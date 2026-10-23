package com.ai.assistance.operit.sync

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import java.util.UUID

/**
 * 本地存储抽象接口——由宿主 App（Operit fork）实现。
 * 隔离 SyncManager 与 Operit 原有 DataStore/Room 的耦合，方便后续升级上游。
 */
interface SyncStorage {
    /** 所有 Provider 配置的序列化 JSON（明文） */
    suspend fun getProvidersJson(): String?
    suspend fun setProvidersJson(json: String)

    /** 全局 Settings 的序列化 JSON（明文） */
    suspend fun getSettingsJson(): String?
    suspend fun setSettingsJson(json: String)

    /** 拉取所有话题：topic_id → 序列化 JSON */
    suspend fun getAllTopics(): Map<String, String>
    /** 更新/插入话题 */
    suspend fun upsertTopic(topicId: String, json: String)
    /** 删除话题 */
    suspend fun deleteTopic(topicId: String)

    /**
     * 把云端公共 Provider 模板灌入本地 ModelConfigManager。
     * 模板不带 API Key，只填 baseUrl / name / 首批 modelName，
     * 用户首次使用时再填自己的 key。已存在同名 provider 跳过。
     */
    suspend fun importProviderTemplates(templates: List<ProviderTemplate>): Int
}

/**
 * 同步管理器单例。
 * 负责：账号登录态、密钥派生缓存、settings/topics/provider/software 的双向增量同步。
 *
 * 使用：
 * ```
 * SyncManager.init(context, storage, baseUrl = "https://ag.linchuanwl.xyz")
 * SyncManager.login(username, password)
 * SyncManager.pullAll()
 * ```
 */
object SyncManager {

    private const val PREFS_NAME = "lc_sync"
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_REFRESH_TOKEN = "refresh_token"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_USERNAME = "username"
    private const val KEY_PBKDF2_SALT = "pbkdf2_salt"
    private const val KEY_BASE_URL = "base_url"
    private const val KEY_DEVICE_HASH = "device_hash"

    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var storage: SyncStorage
    private lateinit var api: SyncApi

    /** 内存中的 AES 密钥缓存（避免每次同步都 PBKDF2 派生） */
    @Volatile private var cachedKey: ByteArray? = null

    private val _state = MutableStateFlow<AuthState>(AuthState.LoggedOut)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    /** 是否已初始化 */
    fun isInitialized(): Boolean = ::prefs.isInitialized

    /** 是否已登录 */
    fun isLoggedIn(): Boolean = _state.value is AuthState.LoggedIn

    /** 当前用户（null = 未登录） */
    fun currentUser(): AuthState.LoggedIn? = _state.value as? AuthState.LoggedIn

    // ============ 初始化 ============

    fun init(
        context: Context,
        storage: SyncStorage,
        baseUrl: String = "https://ag.linchuanwl.xyz",
    ) {
        if (::prefs.isInitialized) return
        this.storage = storage
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        prefs = EncryptedSharedPreferences.create(
            context, PREFS_NAME, masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
        api = SyncApi(
            baseUrl = prefs.getString(KEY_BASE_URL, baseUrl) ?: baseUrl,
            tokenProvider = { prefs.getString(KEY_ACCESS_TOKEN, null) },
            onTokenExpired = ::doRefreshToken,
        )
        restoreLoginState()
    }

    fun updateBaseUrl(newUrl: String) {
        prefs.edit().putString(KEY_BASE_URL, newUrl).apply()
        api = SyncApi(
            baseUrl = newUrl,
            tokenProvider = { prefs.getString(KEY_ACCESS_TOKEN, null) },
            onTokenExpired = ::doRefreshToken,
        )
    }

    private fun restoreLoginState() {
        val uid = prefs.getString(KEY_USER_ID, null)
        val uname = prefs.getString(KEY_USERNAME, null)
        val at = prefs.getString(KEY_ACCESS_TOKEN, null)
        if (uid != null && uname != null && at != null) {
            _state.value = AuthState.LoggedIn(uid, uname)
        } else {
            _state.value = AuthState.LoggedOut
        }
    }

    // ============ Auth ============

    suspend fun register(username: String, email: String, password: String, code: String): Result<AuthState.LoggedIn> {
        val pbkdf2Salt = SyncCrypto.generateSaltHex()
        val resp = api.register(RegisterRequest(username, email, password, pbkdf2Salt, code))
        return when (resp) {
            is ApiResult.Ok -> {
                saveAuth(resp.data, pbkdf2Salt)
                Result.success(_state.value as AuthState.LoggedIn)
            }
            is ApiResult.Error -> Result.failure(resp.ex)
        }
    }

    suspend fun login(username: String, password: String): Result<AuthState.LoggedIn> {
        val resp = api.login(LoginRequest(username, password))
        return when (resp) {
            is ApiResult.Ok -> {
                val pbkdf2Salt = resp.data.salt ?: SyncCrypto.generateSaltHex()
                saveAuth(resp.data, pbkdf2Salt)
                Result.success(_state.value as AuthState.LoggedIn)
            }
            is ApiResult.Error -> Result.failure(resp.ex)
        }
    }

    private fun saveAuth(resp: AuthResponse, pbkdf2Salt: String) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, resp.accessToken)
            .putString(KEY_REFRESH_TOKEN, resp.refreshToken)
            .putString(KEY_USER_ID, resp.user.id)
            .putString(KEY_USERNAME, resp.user.username)
            .putString(KEY_PBKDF2_SALT, pbkdf2Salt)
            .apply()
        _state.value = AuthState.LoggedIn(resp.user.id, resp.user.username)
        cachedKey = null // 清除缓存，下次 pull/push 前需要 re-derive
    }

    fun logout() {
        cachedKey = null
        prefs.edit().clear().apply()
        _state.value = AuthState.LoggedOut
    }

    // ============ 验证码（优雅降级） ============

    /** 发送邮箱验证码。后端未实现该端点（404）时返回 NotSupported，UI 应跳过验证 */
    suspend fun sendCode(email: String, purpose: String = "register"): Result<CodeSendResult> {
        return when (val resp = api.sendCode(SendCodeRequest(email, purpose))) {
            is ApiResult.Ok -> Result.success(CodeSendResult.Sent)
            is ApiResult.Error -> {
                if (resp.ex is ApiException.Http && resp.ex.code == 404) {
                    Result.success(CodeSendResult.NotSupported)
                } else Result.failure(resp.ex)
            }
        }
    }

    /** 校验邮箱验证码。后端未实现该端点（404）时返回 NotSupported，UI 应跳过验证 */
    suspend fun verifyCode(email: String, code: String, purpose: String = "register"): Result<CodeVerifyResult> {
        return when (val resp = api.verifyCode(VerifyCodeRequest(email, code, purpose))) {
            is ApiResult.Ok -> Result.success(CodeVerifyResult.Ok(resp.data.token))
            is ApiResult.Error -> {
                if (resp.ex is ApiException.Http && resp.ex.code == 404) {
                    Result.success(CodeVerifyResult.NotSupported)
                } else Result.failure(resp.ex)
            }
        }
    }

    /** 401 回调：用 refresh_token 换新 access_token */
    private suspend fun doRefreshToken(): String? {
        val refresh = prefs.getString(KEY_REFRESH_TOKEN, null) ?: return null
        val resp = api.refresh(refresh)
        return when (resp) {
            is ApiResult.Ok -> {
                prefs.edit().putString(KEY_ACCESS_TOKEN, resp.data.accessToken).apply()
                resp.data.accessToken
            }
            is ApiResult.Error -> {
                logout()
                null
            }
        }
    }

    // ============ 密钥派生 ============

    /**
     * 用当前密码派生 AES 密钥，缓存到内存。
     * 每次登录后需要调用一次，之后 pull/push 都会复用缓存。
     */
    fun deriveKey(password: String) {
        val salt = prefs.getString(KEY_PBKDF2_SALT, null)
            ?: throw IllegalStateException("not logged in or no salt")
        cachedKey = SyncCrypto.deriveKey(password, salt)
    }

    private fun getOrDeriveKey(): ByteArray {
        return cachedKey ?: throw IllegalStateException("key not derived - call deriveKey(password) after login")
    }

    // ============ Settings 同步 ============

    /** 拉取 settings + providers（从服务端），解密后写回本地 */
    suspend fun pullSettings(): Result<Unit> {
        val resp = api.pullSettings()
        val key = getOrDeriveKey()
        return when (resp) {
            is ApiResult.Ok -> {
                val d = resp.data
                // providers
                if (d.providersEnc != null && d.providersNonce != null) {
                    val json = SyncCrypto.decryptString(d.providersEnc, d.providersNonce, key)
                    storage.setProvidersJson(json)
                }
                // settings
                if (d.settingsEnc != null && d.settingsNonce != null) {
                    val json = SyncCrypto.decryptString(d.settingsEnc, d.settingsNonce, key)
                    storage.setSettingsJson(json)
                }
                Result.success(Unit)
            }
            is ApiResult.Error -> Result.failure(resp.ex)
        }
    }

    /** 把本地 settings + providers 加密后推送到服务端 */
    suspend fun pushSettings(): Result<Unit> {
        val key = getOrDeriveKey()
        val deviceHash = getOrCreateDeviceHash()
        val deviceName = android.os.Build.MODEL

        val (pEnc, pNonce) = storage.getProvidersJson()
            ?.let { SyncCrypto.encryptString(it, key) }
            ?: (null to null)
        val (sEnc, sNonce) = storage.getSettingsJson()
            ?.let { SyncCrypto.encryptString(it, key) }
            ?: (null to null)

        val req = SettingsPushRequest(
            providersEnc = pEnc, providersNonce = pNonce,
            settingsEnc = sEnc, settingsNonce = sNonce,
            deviceName = deviceName,
            deviceHash = deviceHash,
        )
        return when (val resp = api.pushSettings(req)) {
            is ApiResult.Ok -> Result.success(Unit)
            is ApiResult.Error -> Result.failure(resp.ex)
        }
    }

    // ============ Topics 同步（增量） ============

    private var lastTopicsSync: String = "1970-01-01T00:00:00Z"

    /** 拉取增量话题变化，密文解密后合并到本地 */
    suspend fun pullTopics(): Result<Int> {
        val since = lastTopicsSync
        val resp = api.pullTopics(since)
        val key = getOrDeriveKey()
        return when (resp) {
            is ApiResult.Ok -> {
                var applied = 0
                for (change in resp.data.changed) {
                    if (change.deletedAt != null) {
                        storage.deleteTopic(change.topicId)
                    } else if (change.enc != null && change.nonce != null) {
                        val json = SyncCrypto.decryptString(change.enc, change.nonce, key)
                        storage.upsertTopic(change.topicId, json)
                    }
                    applied++
                }
                lastTopicsSync = resp.data.serverTime
                Result.success(applied)
            }
            is ApiResult.Error -> Result.failure(resp.ex)
        }
    }

    /** 把本地话题的变更（全部）加密后推送到服务端 */
    suspend fun pushTopics(clientVersion: String = "1.0"): Result<Unit> {
        val key = getOrDeriveKey()
        val nowIso = java.time.Instant.now().toString()
        val updates = storage.getAllTopics().map { (id, json) ->
            val (enc, nonce) = SyncCrypto.encryptString(json, key)
            TopicUpdate(
                topicId = id, enc = enc, nonce = nonce,
                clientVersion = clientVersion, updatedAt = nowIso,
            )
        }
        // TODO: 收集本地已删除的 topicId 列表并放入 deletes
        val deletes = emptyList<String>()
        return when (val resp = api.pushTopics(TopicsPushRequest(updates, deletes))) {
            is ApiResult.Ok -> {
                lastTopicsSync = resp.data.updatedAt ?: nowIso
                Result.success(Unit)
            }
            is ApiResult.Error -> Result.failure(resp.ex)
        }
    }

    // ============ Provider Templates ============

    /** 拉取云端公共 Provider 模板列表（无鉴权） */
    suspend fun pullProviderTemplates(): Result<ProviderTemplatesResponse> {
        return when (val resp = api.listProviderTemplates()) {
            is ApiResult.Ok -> Result.success(resp.data)
            is ApiResult.Error -> Result.failure(resp.ex)
        }
    }

    /** 拉取云端 Provider 模板并灌入本地 ModelConfigManager，返回新增数量 */
    suspend fun pullProviderTemplatesAndImport(): Result<Int> {
        return when (val resp = api.listProviderTemplates()) {
            is ApiResult.Ok -> {
                val n = storage.importProviderTemplates(resp.data.providers)
                Result.success(n)
            }
            is ApiResult.Error -> Result.failure(resp.ex)
        }
    }

    // ============ Software ============

    suspend fun pushSoftware(inventory: List<String>): Result<Unit> {
        val req = SoftwarePushRequest(
            deviceName = android.os.Build.MODEL,
            deviceHash = getOrCreateDeviceHash(),
            inventory = inventory,
        )
        return when (val resp = api.pushSoftware(req)) {
            is ApiResult.Ok -> Result.success(Unit)
            is ApiResult.Error -> Result.failure(resp.ex)
        }
    }

    // ============ Device Hash ============

    private fun getOrCreateDeviceHash(): String {
        return prefs.getString(KEY_DEVICE_HASH, null) ?: run {
            val raw = "${android.os.Build.MANUFACTURER}|${android.os.Build.MODEL}|${android.os.Build.SERIAL}"
            val hash = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
                .joinToString("") { "%02x".format(it) }.take(32)
            prefs.edit().putString(KEY_DEVICE_HASH, hash).apply()
            hash
        }
    }
}

sealed class AuthState {
    data class LoggedIn(val userId: String, val username: String) : AuthState()
    object LoggedOut : AuthState()
}

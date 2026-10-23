package com.ai.assistance.operit.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.ai.assistance.operit.util.AppLogger
import java.security.KeyStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Credentials shared by all Codex model configurations in this app. */
data class CodexAuthState(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtMillis: Long,
    val accountId: String,
    val residency: String? = null,
    val email: String? = null,
)

class CodexAuthPreferences private constructor(context: Context) {
    private val appContext = context.applicationContext
    private var preferences = SecurePrefsFactory.create(appContext, STORE_NAME)

    private val _authState = MutableStateFlow(readState())
    val authState: StateFlow<CodexAuthState?> = _authState.asStateFlow()

    fun currentState(): CodexAuthState? = _authState.value

    fun save(state: CodexAuthState) {
        require(state.accessToken.isNotBlank()) { "Codex access token is empty" }
        require(state.refreshToken.isNotBlank()) { "Codex refresh token is empty" }
        require(state.accountId.isNotBlank()) { "Codex account ID is empty" }
        require(state.expiresAtMillis > 0L) { "Codex token expiration is invalid" }

        preferences.edit()
            .putString(KEY_ACCESS_TOKEN, state.accessToken)
            .putString(KEY_REFRESH_TOKEN, state.refreshToken)
            .putLong(KEY_EXPIRES_AT, state.expiresAtMillis)
            .putString(KEY_ACCOUNT_ID, state.accountId)
            .apply {
                if (state.residency.isNullOrBlank()) {
                    remove(KEY_RESIDENCY)
                } else {
                    putString(KEY_RESIDENCY, state.residency)
                }
                if (state.email.isNullOrBlank()) {
                    remove(KEY_EMAIL)
                } else {
                    putString(KEY_EMAIL, state.email)
                }
            }
            .apply()
        _authState.value = state
    }

    fun clear() {
        preferences.edit().clear().apply()
        _authState.value = null
    }

    private fun readState(): CodexAuthState? {
        return try {
            readStateFromPreferences()
        } catch (error: SecurityException) {
            // Restored encrypted values can be unreadable when Android Keystore kept the key
            // device-local. Reset only Codex OAuth state so the settings screen can open.
            AppLogger.e(TAG, "Codex OAuth credentials are unreadable; resetting encrypted store", error)
            resetEncryptedStore()
            null
        }
    }

    private fun readStateFromPreferences(): CodexAuthState? {
        val accessToken = preferences.getString(KEY_ACCESS_TOKEN, null)?.trim()
        val refreshToken = preferences.getString(KEY_REFRESH_TOKEN, null)?.trim()
        val accountId = preferences.getString(KEY_ACCOUNT_ID, null)?.trim()
        val expiresAtMillis = preferences.getLong(KEY_EXPIRES_AT, 0L)
        if (accessToken.isNullOrEmpty() ||
            refreshToken.isNullOrEmpty() ||
            accountId.isNullOrEmpty() ||
            expiresAtMillis <= 0L
        ) {
            return null
        }

        return CodexAuthState(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresAtMillis = expiresAtMillis,
            accountId = accountId,
            residency = preferences.getString(KEY_RESIDENCY, null),
            email = preferences.getString(KEY_EMAIL, null),
        )
    }

    private fun resetEncryptedStore() {
        preferences = SecurePrefsFactory.create(appContext, STORE_NAME, forceReset = true)
    }

    companion object {
        private const val TAG = "CodexAuthPreferences"
        private const val STORE_NAME = "codex_oauth_credentials"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_EXPIRES_AT = "expires_at"
        private const val KEY_ACCOUNT_ID = "account_id"
        private const val KEY_RESIDENCY = "residency"
        private const val KEY_EMAIL = "email"

        @Volatile
        private var instance: CodexAuthPreferences? = null

        fun getInstance(context: Context): CodexAuthPreferences {
            return instance ?: synchronized(this) {
                instance ?: CodexAuthPreferences(context.applicationContext).also { instance = it }
            }
        }

    }
}

/**
 * EncryptedSharedPreferences 的安全创建器。
 *
 * 背景：设备恢复备份、系统清理或 Keystore 损坏时，`_androidx_security_master_key_` 别名会
 * 残留但不可用，此时 `EncryptedSharedPreferences.create` 直接抛
 * `KeyStoreException: the master key ... exists but is unusable`。
 * 只删 SharedPreferences 文件并不能解决，必须同时删掉 Keystore 别名再重建，
 * 重建仍失败则回退普通 SharedPreferences，保证界面不崩溃。
 */
internal object SecurePrefsFactory {
    private const val TAG = "SecurePrefsFactory"
    private const val MASTER_KEY_ALIAS = "_androidx_security_master_key_"

    fun create(context: Context, storeName: String, forceReset: Boolean = false): SharedPreferences {
        if (forceReset) resetKeystoreAndStore(context, storeName, null)
        return try {
            createEncrypted(context, storeName)
        } catch (first: Exception) {
            AppLogger.e(TAG, "加密存储 $storeName 无法打开，重建密钥后重试", first)
            resetKeystoreAndStore(context, storeName, first)
            try {
                createEncrypted(context, storeName)
            } catch (second: Exception) {
                AppLogger.e(TAG, "加密存储 $storeName 重建仍失败，回退普通存储", second)
                context.getSharedPreferences("${storeName}_plain", Context.MODE_PRIVATE)
            }
        }
    }

    private fun resetKeystoreAndStore(context: Context, storeName: String, error: Exception?) {
        if (error != null) AppLogger.e(TAG, "重置加密存储 $storeName（Keystore 别名一并删除）", error)
        try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (keyStore.containsAlias(MASTER_KEY_ALIAS)) {
                keyStore.deleteEntry(MASTER_KEY_ALIAS)
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "删除 Keystore 别名失败", e)
        }
        try {
            context.deleteSharedPreferences(storeName)
        } catch (e: Exception) {
            AppLogger.e(TAG, "删除 SharedPreferences 失败", e)
        }
    }

    private fun createEncrypted(context: Context, storeName: String): SharedPreferences {
        return EncryptedSharedPreferences.create(
            context,
            storeName,
            MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }
}

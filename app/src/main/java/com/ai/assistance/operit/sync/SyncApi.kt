package com.ai.assistance.operit.sync

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 同步后端 HTTP API 客户端。
 * 所有请求走 OkHttp + kotlinx.serialization。
 * auth 路由无需 token，sync 路由需要 Bearer access_token。
 */
class SyncApi(
    private val baseUrl: String,
    private val tokenProvider: () -> String? = { null },
    private val onTokenExpired: suspend () -> String? = { null }, // 401 时尝试刷新，返回新 token
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    // ============ Auth ============

    suspend fun register(req: RegisterRequest): ApiResult<AuthResponse> =
        post("/api/auth/register", req, needAuth = false)

    suspend fun login(req: LoginRequest): ApiResult<AuthResponse> =
        post("/api/auth/login", req, needAuth = false)

    suspend fun refresh(refreshToken: String): ApiResult<RefreshResponse> =
        post("/api/auth/refresh", RefreshRequest(refreshToken), needAuth = false)

    // ============ Provider Templates ============

    suspend fun listProviderTemplates(): ApiResult<ProviderTemplatesResponse> =
        get("/api/sync/provider-templates", needAuth = false)

    // ============ Settings ============

    suspend fun pullSettings(): ApiResult<SettingsPullResponse> =
        get("/api/sync/settings", needAuth = true)

    suspend fun pushSettings(req: SettingsPushRequest): ApiResult<OkResponse> =
        post("/api/sync/settings", req, needAuth = true)

    // ============ Topics ============

    suspend fun pullTopics(sinceIso: String): ApiResult<TopicsPullResponse> =
        get("/api/sync/topics?since=${java.net.URLEncoder.encode(sinceIso, "UTF-8")}", needAuth = true)

    suspend fun pushTopics(req: TopicsPushRequest): ApiResult<OkResponse> =
        post("/api/sync/topics", req, needAuth = true)

    // ============ Software ============

    suspend fun pullSoftware(): ApiResult<SoftwarePullResponse> =
        get("/api/sync/software", needAuth = true)

    suspend fun pushSoftware(req: SoftwarePushRequest): ApiResult<OkResponse> =
        post("/api/sync/software", req, needAuth = true)

    // ============ 底层 ============

    private suspend inline fun <reified T> get(
        path: String, needAuth: Boolean,
    ): ApiResult<T> = execute("GET", path, null, needAuth)

    private suspend inline fun <reified T, reified B> post(
        path: String, body: B, needAuth: Boolean,
    ): ApiResult<T> {
        val jsonBody = json.encodeToString(body).toRequestBody("application/json".toMediaType())
        return execute("POST", path, jsonBody, needAuth)
    }

    private suspend inline fun <reified T> execute(
        method: String, path: String, body: okhttp3.RequestBody?, needAuth: Boolean,
    ): ApiResult<T> {
        var token = if (needAuth) tokenProvider() else null
        var attempt = 0

        loop@ while (attempt < 2) {
            attempt++
            val builder = Request.Builder().url("$baseUrl$path").method(method, body)
            if (needAuth && token != null) {
                builder.header("Authorization", "Bearer $token")
            }
            val resp: Response = try { http.newCall(builder.build()).await() } catch (e: Exception) {
                return ApiResult.Error(ApiException.Network(e.message ?: "network error"))
            }

            resp.use { r ->
                val text = r.body?.string().orEmpty()
                if (r.code == 401 && needAuth && attempt == 1) {
                    // token 过期，尝试刷新
                    val newToken = onTokenExpired()
                    if (newToken != null) {
                        token = newToken
                        continue@loop // 重试
                    }
                }
                if (!r.isSuccessful) {
                    val errMsg = runCatching {
                        json.decodeFromString<ErrorResponse>(text).error
                    }.getOrDefault(text.ifBlank { "HTTP ${r.code}" })
                    return ApiResult.Error(ApiException.Http(r.code, errMsg))
                }
                if (T::class == OkResponse::class && text.isBlank()) {
                    @Suppress("UNCHECKED_CAST")
                    return ApiResult.Ok(OkResponse(ok = true) as T)
                }
                return runCatching { json.decodeFromString<T>(text) }
                    .fold(onSuccess = { ApiResult.Ok(it) },
                          onFailure = { ApiResult.Error(ApiException.Parse(it.message ?: "parse error")) })
            }
        }
        return ApiResult.Error(ApiException.Http(401, "token expired after refresh"))
    }
}

// ============ 辅助 ============

sealed class ApiResult<out T> {
    data class Ok<T>(val data: T) : ApiResult<T>()
    data class Error(val ex: ApiException) : ApiResult<Nothing>()
}

sealed class ApiException(message: String) : Exception(message) {
    class Network(msg: String) : ApiException(msg)
    class Http(val code: Int, msg: String) : ApiException(msg)
    class Parse(msg: String) : ApiException(msg)
    object Unauthorized : ApiException("unauthorized")
}

// OkHttp 没有原生 suspend 扩展，这里用 runBlocking 桥接（在 Dispatchers.IO 调用即可）
private suspend fun okhttp3.Call.await(): Response {
    return kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { cancel() }
        enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                cont.resumeWith(Result.failure(e))
            }
            override fun onResponse(call: okhttp3.Call, response: Response) {
                cont.resumeWith(Result.success(response))
            }
        })
    }
}

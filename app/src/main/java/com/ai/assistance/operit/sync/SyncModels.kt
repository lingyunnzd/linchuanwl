package com.ai.assistance.operit.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// ============ Auth ============

@Serializable
data class RegisterRequest(
    val username: String,
    val email: String,
    val password: String,
    val salt: String, // pbkdf2 salt, hex string
    val code: String, // 邮箱验证码（后端 consumeCode 校验）
)

@Serializable
data class LoginRequest(
    val username: String,
    val password: String,
)

@Serializable
data class SendCodeRequest(
    val email: String,
    val purpose: String = "register", // register / login
)

@Serializable
data class VerifyCodeRequest(
    val email: String,
    val code: String,
    val purpose: String = "register",
)

@Serializable
data class VerifyCodeResponse(
    val ok: Boolean,
    val token: String? = null, // 可选：后端返回一次性验证 token，注册时可带上
)

@Serializable
data class RefreshRequest(
    @SerialName("refresh_token")
    val refreshToken: String,
)

@Serializable
data class AuthUser(
    // 后端返回数字 id，旧版本按 String 解析会抛 "Expected quotation mark" 异常
    val id: JsonElement,
    val username: String,
) {
    val idText: String get() = id.toString().trim('"')
}

@Serializable
data class AuthResponse(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("refresh_token")
    val refreshToken: String? = null,
    val salt: String? = null, // login response 带，register 不带
    val user: AuthUser,
)

@Serializable
data class RefreshResponse(
    @SerialName("access_token")
    val accessToken: String,
)

// ============ Settings ============

@Serializable
data class SettingsPullResponse(
    @SerialName("providers_enc")
    val providersEnc: String? = null, // base64
    @SerialName("providers_nonce")
    val providersNonce: String? = null,
    @SerialName("settings_enc")
    val settingsEnc: String? = null, // base64
    @SerialName("settings_nonce")
    val settingsNonce: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null, // ISO-8601
)

@Serializable
data class SettingsPushRequest(
    @SerialName("providers_enc")
    val providersEnc: String? = null,
    @SerialName("providers_nonce")
    val providersNonce: String? = null,
    @SerialName("settings_enc")
    val settingsEnc: String? = null,
    @SerialName("settings_nonce")
    val settingsNonce: String? = null,
    @SerialName("device_name")
    val deviceName: String? = null,
    @SerialName("device_hash")
    val deviceHash: String? = null,
)

@Serializable
data class OkResponse(
    val ok: Boolean,
    @SerialName("updated_at")
    val updatedAt: String? = null,
)

// ============ Topics ============

@Serializable
data class TopicChange(
    @SerialName("topic_id")
    val topicId: String,
    val enc: String? = null, // base64密文
    val nonce: String? = null,
    @SerialName("client_version")
    val clientVersion: String? = null,
    @SerialName("updated_at")
    val updatedAt: String,
    @SerialName("deleted_at")
    val deletedAt: String? = null, // 非null表示已删除
)

@Serializable
data class TopicsPullResponse(
    val changed: List<TopicChange> = emptyList(),
    @SerialName("server_time")
    val serverTime: String,
)

@Serializable
data class TopicUpdate(
    @SerialName("topic_id")
    val topicId: String,
    val enc: String,
    val nonce: String,
    @SerialName("client_version")
    val clientVersion: String? = null,
    @SerialName("updated_at")
    val updatedAt: String,
)

@Serializable
data class TopicsPushRequest(
    val updates: List<TopicUpdate> = emptyList(),
    val deletes: List<String> = emptyList(),
)

// ============ Software ============

@Serializable
data class SoftwareItem(
    @SerialName("device_hash")
    val deviceHash: String,
    val inventory: List<String> = emptyList(),
    @SerialName("updated_at")
    val updatedAt: String? = null,
)

@Serializable
data class SoftwarePullResponse(
    val items: List<SoftwareItem> = emptyList(),
)

@Serializable
data class SoftwarePushRequest(
    @SerialName("device_name")
    val deviceName: String? = null,
    @SerialName("device_hash")
    val deviceHash: String,
    val inventory: List<String>,
)

// ============ Provider Templates ============

@Serializable
data class ProviderTemplate(
    val id: String,
    val name: String,
    val baseUrl: String,
    val description: String? = null,
    val models: List<String> = emptyList(),
    val authType: String = "api_key", // api_key / oauth / none
)

@Serializable
data class ProviderTemplatesResponse(
    val providers: List<ProviderTemplate> = emptyList(),
)

// ============ Error ============

@Serializable
data class ErrorResponse(
    val error: String,
)

// ============ 验证码结果枚举（UI 层识别用） ============

sealed class CodeSendResult {
    object Sent : CodeSendResult()
    object NotSupported : CodeSendResult() // 后端未实现 send-code 端点（404）
}

sealed class CodeVerifyResult {
    data class Ok(val token: String?) : CodeVerifyResult()
    object NotSupported : CodeVerifyResult() // 后端未实现 verify-code 端点（404）
}

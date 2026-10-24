package com.ai.assistance.operit.sync

import com.ai.assistance.operit.data.dao.ChatDao
import com.ai.assistance.operit.data.model.ApiKeyAvailabilityStatus
import com.ai.assistance.operit.data.model.ApiKeyInfo
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.ChatEntity
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.model.ModelConfigData
import com.ai.assistance.operit.data.preferences.FunctionalConfigManager
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * SyncStorage 在 Operit 上的落地：
 * - providers：ModelConfigManager 的 DataStore 配置列表
 * - topics：Room 的 chats 表（经 DTO 序列化，不污染 Room 实体）
 */
class OperitSyncAdapter(
    private val modelConfigManager: ModelConfigManager,
    private val chatDao: ChatDao,
    private val functionalConfigManager: FunctionalConfigManager,
) : SyncStorage {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; isLenient = true }

    /** ChatEntity 的可序列化副本（Room 实体带注解，不宜直接加 @Serializable） */
    @Serializable
    data class ChatEntityDto(
        val id: String,
        val title: String,
        val createdAt: Long,
        val updatedAt: Long,
        val inputTokens: Long = 0L,
        val outputTokens: Long = 0L,
        val currentWindowSize: Long = 0L,
        val group: String? = null,
        val displayOrder: Long = 0L,
        val workspace: String? = null,
        val workspaceEnv: String? = null,
        val parentChatId: String? = null,
        val characterCardName: String? = null,
        val characterGroupId: String? = null,
        val locked: Boolean = false,
        val pinned: Boolean = false,
    )

    private fun ChatEntity.toDto() = ChatEntityDto(
        id = id, title = title,
        createdAt = createdAt, updatedAt = updatedAt,
        inputTokens = inputTokens, outputTokens = outputTokens,
        currentWindowSize = currentWindowSize, group = group,
        displayOrder = displayOrder, workspace = workspace,
        workspaceEnv = workspaceEnv, parentChatId = parentChatId,
        characterCardName = characterCardName, characterGroupId = characterGroupId,
        locked = locked, pinned = pinned,
    )

    private fun ChatEntityDto.toEntity() = ChatEntity(
        id = id, title = title,
        createdAt = createdAt, updatedAt = updatedAt,
        inputTokens = inputTokens, outputTokens = outputTokens,
        currentWindowSize = currentWindowSize, group = group,
        displayOrder = displayOrder, workspace = workspace,
        workspaceEnv = workspaceEnv, parentChatId = parentChatId,
        characterCardName = characterCardName, characterGroupId = characterGroupId,
        locked = locked, pinned = pinned,
    )

    override suspend fun getProvidersJson(): String = modelConfigManager.exportAllConfigs()

    override suspend fun setProvidersJson(json: String) {
        modelConfigManager.importConfigs(json)
    }

    override suspend fun getSettingsJson(): String? = null

    override suspend fun setSettingsJson(json: String) {
        // 全局 settings 暂无对应本地存储，预留
    }

    override suspend fun getAllTopics(): Map<String, String> {
        val chats = chatDao.getAllChatsDirectly()
        return chats.associate { chat -> chat.id to json.encodeToString(chat.toDto()) }
    }

    override suspend fun upsertTopic(topicId: String, data: String) {
        runCatching { chatDao.insertChat(json.decodeFromString<ChatEntityDto>(data).toEntity()) }
    }

    override suspend fun deleteTopic(topicId: String) {
        chatDao.deleteChat(topicId)
    }

    /**
     * 把云端 ProviderTemplate 列表灌入本地 ModelConfigManager。
     * 每个模板映射为一个 ModelConfigData：
     * - id: 以 cloud- 前缀 + template.id，区分于本地自建
     * - apiEndpoint: template.baseUrl
     * - apiKey / apiKeyPool: 云端下发的密钥（不再要求用户手填）
     * - apiProviderType: 统一 OPENAI（通用 Chat Completions 端点）
     * 导入后若当前聊天配置还没有可用密钥，自动把首个带密钥的模板设为默认对话模型。
     */
    override suspend fun importProviderTemplates(templates: List<ProviderTemplate>): Int {
        if (templates.isEmpty()) return 0
        val now = System.currentTimeMillis()
        val configs = templates.map { t ->
            val pool = (listOf(t.apiKey) + t.apiKeys)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
            ModelConfigData(
                id = "cloud-${t.id}",
                name = t.name,
                apiEndpoint = t.baseUrl,
                modelName = t.models.firstOrNull() ?: "",
                apiKey = pool.firstOrNull() ?: "",
                useMultipleApiKeys = pool.size > 1,
                apiKeyPool = pool.mapIndexed { idx, key ->
                    ApiKeyInfo(
                        id = "cloud-${t.id}-$idx",
                        key = key,
                        name = "云端密钥 ${idx + 1}",
                        isEnabled = true,
                        availabilityStatus = ApiKeyAvailabilityStatus.UNTESTED,
                        lastUsed = now,
                    )
                },
                apiProviderType = ApiProviderType.OPENAI,
                apiProviderTypeId = ApiProviderType.OPENAI.name,
            )
        }
        val jsonStr = Json { encodeDefaults = true; prettyPrint = false }
            .encodeToString(configs)
        val (newCount, _updated, _skipped) = modelConfigManager.importConfigs(jsonStr)

        // 自动启用：原配置若没有可用密钥，直接把首个云端模板设为聊天默认模型
        runCatching {
            val currentId = functionalConfigManager.getConfigIdForFunction(FunctionType.CHAT)
            val current = modelConfigManager.getModelConfig(currentId)
            val currentReady = current != null &&
                (current.apiKey.isNotBlank() || current.apiKeyPool.any { it.isEnabled && it.key.isNotBlank() })
            if (!currentReady) {
                val firstReady = configs.firstOrNull { cfg ->
                    cfg.apiKey.isNotBlank() || cfg.apiKeyPool.any { it.key.isNotBlank() }
                }
                if (firstReady != null) {
                    functionalConfigManager.setConfigForFunction(FunctionType.CHAT, firstReady.id)
                }
            }
        }
        return newCount
    }
}

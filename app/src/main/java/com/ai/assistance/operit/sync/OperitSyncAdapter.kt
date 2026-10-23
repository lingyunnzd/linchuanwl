package com.ai.assistance.operit.sync

import com.ai.assistance.operit.data.dao.ChatDao
import com.ai.assistance.operit.data.model.ChatEntity
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * SyncStorage 在 Operit 上的落地：
 * - providers：ModelConfigManager 的 DataStore 配置列表
 * - topics：Room 的 chats 表（经 DTO 序列化，不污染 Room 实体）
 */
class OperitSyncAdapter(
    private val modelConfigManager: ModelConfigManager,
    private val chatDao: ChatDao,
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
}

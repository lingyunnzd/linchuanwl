package com.ai.assistance.operit.data.announcement

import com.ai.assistance.operit.util.AppLogger
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 霖川云端公告
 *
 * 对接后端公开接口 GET /api/announcements，返回已启用公告列表（已按 pinned/sort/时间排序）。
 * 客户端取第一条（置顶优先）作为弹窗展示，用 updated_at 做去重。
 */
@Serializable
private data class AnnouncementListResponse(
    val announcements: List<AnnouncementItem> = emptyList(),
    val version: String = "",
    val server_time: String = ""
)

@Serializable
private data class AnnouncementItem(
    val id: Int = 0,
    val title: String = "",
    val content: String = "",
    val level: String = "info",
    val pinned: Boolean = false,
    val sort: Int = 0,
    val updated_at: String = ""
)

data class RemoteAnnouncementDisplay(
    val id: Int,
    val version: String,
    val title: String,
    val body: String,
    val level: String,
    val acknowledgeText: String = "我知道了"
)

class RemoteAnnouncementRepository(
    private val baseUrl: String = DEFAULT_BASE_URL
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .build()

    suspend fun fetchDisplayableAnnouncement(): RemoteAnnouncementDisplay? {
        return withContext(Dispatchers.IO) {
            runCatching {
                val url = "$baseUrl/api/announcements?ts=${System.currentTimeMillis()}"
                val body = fetchText(url)
                val resp = json.decodeFromString<AnnouncementListResponse>(body)
                val first = resp.announcements.firstOrNull() ?: return@runCatching null
                if (first.title.isBlank() || first.content.isBlank()) return@runCatching null
                RemoteAnnouncementDisplay(
                    id = first.id,
                    version = first.updated_at,
                    title = first.title,
                    body = first.content,
                    level = first.level
                )
            }.onFailure { error ->
                AppLogger.w(TAG, "fetchDisplayableAnnouncement failed", error)
            }.getOrNull()
        }
    }

    private fun fetchText(url: String): String {
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Cache-Control", "no-cache")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Request failed: ${response.code} $url")
            }
            return response.body?.string() ?: throw IOException("Empty response body: $url")
        }
    }

    companion object {
        private const val TAG = "RemoteAnnouncementRepo"
        const val DEFAULT_BASE_URL = "https://ag.linchuanwl.xyz"
    }
}

package com.agnesai.chat.data.announcement

import com.agnesai.chat.data.network.ServerApiService
import java.io.IOException

class AnnouncementRepositoryImpl(
    private val serverApiService: ServerApiService
) : AnnouncementRepository {

    override suspend fun fetchAnnouncements(): Result<List<Announcement>> = runCatching {
        val response = serverApiService.getLatestAnnouncement()
        if (!response.isSuccessful) {
            throw IOException("获取公告失败 (HTTP ${response.code()})")
        }
        val body = response.body() ?: return@runCatching emptyList()
        listOf(
            Announcement(
                id = body.id,
                title = body.title,
                content = body.content,
                priority = if (body.priority.equals("important", ignoreCase = true)) {
                    AnnouncementPriority.IMPORTANT
                } else {
                    AnnouncementPriority.NORMAL
                },
                publishAt = body.publishAt
            )
        )
    }
}

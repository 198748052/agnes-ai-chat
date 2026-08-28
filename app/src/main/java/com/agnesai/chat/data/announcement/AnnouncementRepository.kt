package com.agnesai.chat.data.announcement

/**
 * 公告仓库接口。
 *
 * 后续对接服务器时在 [AnnouncementRepositoryImpl] 中注入
 * [com.agnesai.chat.data.network.ServerApiService] 并替换占位逻辑。
 */
interface AnnouncementRepository {

    /** 拉取公告列表（按发布时间倒序）。 */
    suspend fun fetchAnnouncements(): Result<List<Announcement>>
}

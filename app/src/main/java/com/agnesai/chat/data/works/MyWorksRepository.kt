package com.agnesai.chat.data.works

import com.agnesai.chat.data.generation.GenerationParamsCodec
import com.agnesai.chat.data.local.MessageDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 我的作品数据仓库：聚合本地数据库中已完成的图片/视频生成作品。
 */
class MyWorksRepository(
    private val messageDao: MessageDao
) {

    /** 响应式查询已完成作品，按时间倒序。 */
    fun observeWorks(): Flow<List<MyWork>> =
        messageDao.observeCompletedWorks().map { rows ->
            rows.map { it.toMyWork() }
        }

    /** 删除单条作品（仅删除该消息，不影响同会话其他消息）。 */
    suspend fun deleteWork(messageId: Long) {
        messageDao.delete(messageId)
    }

    private fun MyWorkRow.toMyWork(): MyWork {
        val decoded = GenerationParamsCodec.decode(params)
        // 作品类型优先取生成参数（聊天内联生成的作品位于 chat 会话），回退会话类型
        val workType = decoded?.type ?: sessionType
        return MyWork(
            id = id,
            sessionId = sessionId,
            type = workType,
            url = content,
            prompt = prompt,
            sessionTitle = sessionTitle,
            timestamp = timestamp,
            params = decoded
        )
    }
}

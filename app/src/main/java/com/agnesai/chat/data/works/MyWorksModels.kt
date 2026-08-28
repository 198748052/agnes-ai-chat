package com.agnesai.chat.data.works

import com.agnesai.chat.data.generation.GenerationParams

/**
 * 一条已成功生成的 AI 作品（图片/视频）。
 *
 * @param id            消息 id
 * @param sessionId     所属会话 id
 * @param type          作品类型（image / video，来自会话类型）
 * @param url           资源 URL（消息 content）
 * @param prompt        生成提示词（会话内最近一条用户消息，可为空）
 * @param sessionTitle  会话标题
 * @param timestamp     消息创建时间
 * @param params        解析后的生成参数，可空
 */
data class MyWork(
    val id: Long,
    val sessionId: Long,
    val type: String,
    val url: String,
    val prompt: String?,
    val sessionTitle: String,
    val timestamp: Long,
    val params: GenerationParams?
)

/** Room 映射中间 POJO：JOIN 会话表后的已完成作品消息。 */
data class MyWorkRow(
    val id: Long,
    val sessionId: Long,
    val content: String,
    val params: String?,
    val timestamp: Long,
    val sessionTitle: String,
    val sessionType: String,
    val prompt: String?
)

package com.agnesai.chat.data.generation

/**
 * 从模型回复中解析出的内联生成意图（聊天内对话即生成）。
 */
sealed interface GenerationIntent {
    /** 用户想生成图片，[prompt] 为标记内提炼的生成提示词 */
    data class Image(val prompt: String) : GenerationIntent

    /** 用户想生成视频，[prompt] 为标记内提炼的生成提示词 */
    data class Video(val prompt: String) : GenerationIntent
}

/**
 * 解析模型回复中的生成意图协议标记：
 * - 图片：[GENERATE_IMAGE]提示词[/GENERATE_IMAGE]
 * - 视频：[GENERATE_VIDEO]提示词[/GENERATE_VIDEO]
 *
 * 解析规则：首个有效标记生效（图片优先）；提示词 trim 后为空视为未命中；
 * 标记格式畸形（缺闭合等）按未处理，避免误触发。
 */
object GenerationIntentParser {

    private const val IMAGE_OPEN = "[GENERATE_IMAGE]"
    private const val IMAGE_CLOSE = "[/GENERATE_IMAGE]"
    private const val VIDEO_OPEN = "[GENERATE_VIDEO]"
    private const val VIDEO_CLOSE = "[/GENERATE_VIDEO]"

    // 标记含正则元字符（[ ]），必须 escape 后拼接捕获组
    private val pairedImageRegex =
        Regex(Regex.escape(IMAGE_OPEN) + "(.*?)" + Regex.escape(IMAGE_CLOSE))
    private val pairedVideoRegex =
        Regex(Regex.escape(VIDEO_OPEN) + "(.*?)" + Regex.escape(VIDEO_CLOSE))

    /** 从模型完整回复中解析意图；未命中返回 null。 */
    fun parse(reply: String): GenerationIntent? {
        parseMarked(reply, IMAGE_OPEN, IMAGE_CLOSE)?.let { return GenerationIntent.Image(it) }
        parseMarked(reply, VIDEO_OPEN, VIDEO_CLOSE)?.let { return GenerationIntent.Video(it) }
        return null
    }

    /** 剥离标记后的用户可见文本：移除成对标记（含提示词）与孤立的开/闭合标签残留。 */
    fun displayText(reply: String): String = reply
        .replace(pairedImageRegex, "")
        .replace(pairedVideoRegex, "")
        .replace(IMAGE_OPEN, "")
        .replace(IMAGE_CLOSE, "")
        .replace(VIDEO_OPEN, "")
        .replace(VIDEO_CLOSE, "")
        .trim()

    /** 提取 open 与 close 之间的提示词；格式不完整或内容为空白返回 null。 */
    private fun parseMarked(reply: String, open: String, close: String): String? {
        val startIndex = reply.indexOf(open)
        if (startIndex < 0) return null
        val contentStart = startIndex + open.length
        val endIndex = reply.indexOf(close, contentStart)
        if (endIndex < 0) return null
        return reply.substring(contentStart, endIndex).trim().takeIf(String::isNotEmpty)
    }
}

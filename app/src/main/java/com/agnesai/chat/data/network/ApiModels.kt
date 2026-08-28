package com.agnesai.chat.data.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter

data class ChatCompletionRequest(
    val model: String = MODEL_NAME,
    val messages: List<ChatMessageDto>,
    val stream: Boolean = true,
    val temperature: Double? = null,
    @Json(name = "max_tokens") val maxTokens: Int? = null,
    @Json(name = "top_p") val topP: Double? = null
)

/**
 * 聊天消息。多模态时 [imageUrls] 携带图片 Data URI 列表，序列化为 OpenAI 兼容
 * `content` 数组（`text` + `image_url`）；纯文本消息 [imageUrls] 为 null，保持 `content` 字符串格式。
 */
data class ChatMessageDto(
    val role: String,
    val content: String,
    val imageUrls: List<String>? = null
)

/**
 * [ChatMessageDto] 自定义序列化：图片列表非空时 content 输出数组，
 * 否则输出纯文本字符串，保证纯文本消息与旧版请求格式完全一致。
 */
class ChatMessageDtoAdapter : JsonAdapter<ChatMessageDto>() {

    override fun fromJson(reader: JsonReader): ChatMessageDto? {
        // 仅用于请求序列化；响应解析走 ChatChunkResponse/Delta，不经过此类型
        reader.skipValue()
        return null
    }

    override fun toJson(writer: JsonWriter, value: ChatMessageDto?) {
        if (value == null) {
            writer.nullValue()
            return
        }
        writer.beginObject()
        writer.name("role").value(value.role)
        writer.name("content")
        val images = value.imageUrls
        if (images.isNullOrEmpty()) {
            writer.value(value.content)
        } else {
            writer.beginArray()
            writer.beginObject()
            writer.name("type").value("text")
            writer.name("text").value(value.content)
            writer.endObject()
            images.forEach { url ->
                writer.beginObject()
                writer.name("type").value("image_url")
                writer.name("image_url")
                writer.beginObject()
                writer.name("url").value(url)
                writer.endObject()
                writer.endObject()
            }
            writer.endArray()
        }
        writer.endObject()
    }
}

data class ChatChunkResponse(
    val choices: List<Choice> = emptyList()
)

data class Choice(
    val delta: Delta? = null,
    @Json(name = "finish_reason") val finishReason: String? = null
)

data class Delta(
    val content: String? = null
)

const val MODEL_NAME = "agnes-2.5-flash"
const val API_BASE_URL = "https://api.agnes-ai.cn/"

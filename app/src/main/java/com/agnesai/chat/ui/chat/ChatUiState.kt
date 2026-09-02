package com.agnesai.chat.ui.chat

import com.agnesai.chat.data.generation.GenerationParams
import com.agnesai.chat.data.generation.GenerationParamsCodec
import com.agnesai.chat.data.local.MessageEntity
import com.agnesai.chat.data.local.MessageStatus
import com.agnesai.chat.data.local.Roles
import com.agnesai.chat.data.local.SessionEntity
import com.agnesai.chat.data.repository.parseImagePaths

data class UiMessage(
    val id: Long,
    val role: String,
    val content: String,
    val status: String,
    val isError: Boolean,
    /** 生成参数 JSON（图片/视频生成消息专用，文本消息为 null） */
    val params: String? = null,
    /** 多模态图片相对路径列表（filesDir 下，文本消息为空） */
    val imagePaths: List<String> = emptyList()
) {
    /** 内联/创作生成的参数；文本消息为 null */
    val generationParams: GenerationParams? by lazy { GenerationParamsCodec.decode(params) }
}

fun MessageEntity.toUiMessage(): UiMessage = UiMessage(
    id = id,
    role = role,
    content = content,
    status = status,
    isError = role == Roles.ASSISTANT && status == MessageStatus.ERROR,
    params = params,
    imagePaths = parseImagePaths(imagePaths)
)

data class UiSession(
    val id: Long,
    val title: String,
    val type: String,
    val updatedAt: Long
)

fun SessionEntity.toUiSession(): UiSession = UiSession(
    id = id,
    title = title,
    type = type,
    updatedAt = updatedAt
)

data class ChatUiState(
    val messages: List<UiMessage> = emptyList(),
    val isStreaming: Boolean = false,
    val error: String? = null,
    val apiKeyConfigured: Boolean = false,
    /** 全部会话（含类型），UI 按当前能力过滤后用于抽屉展示 */
    val sessions: List<UiSession> = emptyList(),
    val currentSessionId: Long = 0L,
    val currentSessionTitle: String = "New Chat"
)

package com.agnesai.chat.data.repository

import com.agnesai.chat.data.local.MessageDao
import com.agnesai.chat.data.local.MessageEntity
import com.agnesai.chat.data.local.MessageStatus
import com.agnesai.chat.data.local.Roles
import com.agnesai.chat.data.local.SessionDao
import com.agnesai.chat.data.local.SessionEntity
import com.agnesai.chat.data.local.SessionType
import com.agnesai.chat.data.local.ChatSettings
import com.agnesai.chat.data.network.AgnesApiService
import com.agnesai.chat.data.network.ChatCompletionRequest
import com.agnesai.chat.data.network.ChatMessageDto
import com.agnesai.chat.data.network.StreamParser
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import retrofit2.Response
import java.io.IOException
import java.io.InterruptedIOException

class HttpException(val code: Int, val errorBody: String = "") : IOException("HTTP $code")

/** 发送给模型的单次上下文历史消息上限，防止长对话 token 超限 / 请求体过大。 */
const val MAX_HISTORY_MESSAGES = 20

/**
 * 聊天内联生成意图协议：要求模型在判断用户想生成图片/视频时，
 * 在自然回应后输出约定标记，客户端解析后自动调用生成接口。
 * 纯提示词实现，兼容任意 OpenAI 兼容端点，不依赖 function calling。
 */
val GENERATION_PROTOCOL_PROMPT = """
当用户希望生成图片或视频时，你必须在自然回应（一两句话）之后，单独输出一个协议标记：
- 生成图片：[GENERATE_IMAGE]提示词[/GENERATE_IMAGE]
- 生成视频：[GENERATE_VIDEO]提示词[/GENERATE_VIDEO]
标记内的提示词应是根据用户需求提炼的详细画面描述。仅当用户明确表达生成意图时输出标记；
普通对话、提问、闲聊时严禁输出任何标记。
""".trim()

private data class ErrorDetailBody(val detail: String? = null)

private val errorDetailMoshi = Moshi.Builder()
    .add(KotlinJsonAdapterFactory())
    .build()

/** 从服务器错误响应体中提取 detail 文案（形如 {"detail": "..."}），解析失败返回 null。 */
fun extractServerDetail(errorBody: String): String? {
    if (errorBody.isBlank()) return null
    return runCatching {
        errorDetailMoshi.adapter(ErrorDetailBody::class.java).fromJson(errorBody)?.detail
            ?.takeIf { it.isNotBlank() }
    }.getOrNull()
}

/**
 * 从会话历史组装发送给模型的请求上下文：系统提示词 + 最近最多 [MAX_HISTORY_MESSAGES] 条已完成消息。
 * 排除 STREAMING / SENDING / ERROR 等未完成或错误消息，已删除消息天然不在历史中。
 * 带图片的消息通过 [imageDataUriProvider]（相对路径 → Data URI）把图片编码进 OpenAI 兼容 content 数组；
 * 未提供 provider 或图片文件缺失时回退为纯文本消息。
 */
fun buildContextMessages(
    history: List<MessageEntity>,
    systemPrompt: String,
    imageDataUriProvider: (String) -> String? = { null }
): List<ChatMessageDto> = buildList {
    add(ChatMessageDto(Roles.SYSTEM, systemPrompt))
    history
        .filterNot {
            it.status == MessageStatus.STREAMING ||
                it.status == MessageStatus.SENDING ||
                it.status == MessageStatus.ERROR
        }
        .takeLast(MAX_HISTORY_MESSAGES)
        .forEach {
            val imagePaths = parseImagePaths(it.imagePaths)
            val imageUrls = if (imagePaths.isEmpty()) {
                emptyList()
            } else {
                imagePaths.mapNotNull { relPath -> imageDataUriProvider(relPath) }
            }
            if (imageUrls.isEmpty()) {
                add(ChatMessageDto(it.role, it.content))
            } else {
                add(ChatMessageDto(it.role, it.content, imageUrls))
            }
        }
}

class ChatRepository(
    private val apiService: AgnesApiService,
    private val settingsDataStoreProvider: suspend () -> Pair<String, String>,
    private val sessionDao: SessionDao,
    private val messageDao: MessageDao,
    private val chatSettingsProvider: suspend () -> ChatSettings = { ChatSettings() },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val imageStore: MessageImageStore? = null
) {

    fun observeMessages(sessionId: Long): Flow<List<MessageEntity>> =
        messageDao.observeMessages(sessionId)

    /** 读取会话全部消息（重新生成等场景用于构建上下文与校验历史）。 */
    suspend fun getMessages(sessionId: Long): List<MessageEntity> =
        messageDao.getMessages(sessionId)

    fun observeSessions(): Flow<List<SessionEntity>> =
        sessionDao.observeSessions()

    fun observeSessionsByType(type: String): Flow<List<SessionEntity>> =
        sessionDao.observeSessionsByType(type)

    suspend fun createSession(type: String = SessionType.CHAT): Long {
        val now = System.currentTimeMillis()
        return sessionDao.insert(
            SessionEntity(title = "New Chat", type = type, createdAt = now, updatedAt = now)
        )
    }

    /** 插入图片/视频生成的用户请求消息（附带生成参数 JSON）。 */
    suspend fun insertUserGenerationMessage(
        sessionId: Long,
        content: String,
        params: String?
    ): Long {
        return messageDao.insert(
            MessageEntity(
                sessionId = sessionId,
                role = Roles.USER,
                content = content,
                params = params,
                timestamp = System.currentTimeMillis(),
                status = MessageStatus.DONE
            )
        )
    }

    /** 插入图片/视频生成的助手占位或结果消息（附带生成参数 JSON）。 */
    suspend fun insertAssistantGenerationMessage(
        sessionId: Long,
        content: String,
        params: String?,
        status: String
    ): Long {
        return messageDao.insert(
            MessageEntity(
                sessionId = sessionId,
                role = Roles.ASSISTANT,
                content = content,
                params = params,
                timestamp = System.currentTimeMillis(),
                status = status
            )
        )
    }

    suspend fun updateMessageWithParams(id: Long, content: String, params: String?, status: String) {
        messageDao.updateContentAndParams(id, content, params, status)
    }

    suspend fun insertUserMessage(sessionId: Long, content: String, imagePaths: String? = null) {
        messageDao.insert(
            MessageEntity(
                sessionId = sessionId,
                role = Roles.USER,
                content = content,
                imagePaths = imagePaths,
                timestamp = System.currentTimeMillis(),
                status = MessageStatus.DONE
            )
        )
    }

    /** 把待发送图片持久化到 App 内部存储并压缩；任一失败时返回错误，不落库。 */
    suspend fun persistMessageImages(sessionId: Long, uris: List<android.net.Uri>): PersistImagesResult =
        imageStore?.persistImages(sessionId, uris)
            ?: PersistImagesResult(error = "图片功能不可用")

    suspend fun insertAssistantMessage(sessionId: Long, content: String, status: String = MessageStatus.DONE): Long {
        return messageDao.insert(
            MessageEntity(
                sessionId = sessionId,
                role = Roles.ASSISTANT,
                content = content,
                timestamp = System.currentTimeMillis(),
                status = status
            )
        )
    }

    suspend fun updateMessage(id: Long, content: String, status: String) {
        messageDao.updateContent(id, content, status)
    }

    suspend fun deleteMessage(id: Long) {
        // 删除前先取消息，同步清理其图片文件，避免孤儿文件残留
        val entity = messageDao.getById(id)
        entity?.let {
            val paths = parseImagePaths(it.imagePaths)
            if (paths.isNotEmpty()) {
                imageStore?.deleteMessageImages(it.sessionId, paths)
            }
        }
        messageDao.delete(id)
    }

    suspend fun updateSessionTitle(sessionId: Long, title: String) {
        sessionDao.update(sessionId, System.currentTimeMillis(), title)
    }

    /** 重命名会话标题，同时刷新 updatedAt 以更新排序位置。调用方需保证 title 非空白。 */
    suspend fun renameSession(sessionId: Long, title: String) {
        sessionDao.update(sessionId, System.currentTimeMillis(), title)
    }

    /** 首条消息时更新会话标题（图片/视频生成消息复用）。 */
    suspend fun updateSessionTitleIfFirst(sessionId: Long, title: String) {
        if (messageDao.countUserMessages(sessionId) == 0) {
            sessionDao.update(sessionId, System.currentTimeMillis(), title)
        }
    }

    suspend fun deleteSession(sessionId: Long) {
        // 级联删除前先清理该会话全部消息图片文件
        imageStore?.deleteSessionImages(sessionId)
        sessionDao.delete(sessionId)
    }

    suspend fun clearSession(sessionId: Long) {
        messageDao.clearSession(sessionId)
    }

    /** 统计会话中已成功落库的用户消息数，用于判断是否为会话首条消息。 */
    suspend fun countUserMessages(sessionId: Long): Int =
        messageDao.countUserMessages(sessionId)

    suspend fun streamChat(
        sessionId: Long,
        onDelta: (String) -> Unit
    ): Result<String> = runCatching {
        val (apiKey, systemPrompt) = settingsDataStoreProvider()
        require(apiKey.isNotBlank()) { "API Key 未配置" }

        val settings = chatSettingsProvider()
        val history = messageDao.getMessages(sessionId)
        val imageStore = this.imageStore
        // 预加载历史消息中的图片 Data URI（loadDataUri 为 suspend，先一次性取回再供纯函数使用）
        val imageDataUris = buildMap {
            history.forEach { message ->
                parseImagePaths(message.imagePaths).forEach { path ->
                    put(path, imageStore?.loadDataUri(path))
                }
            }
        }
        // 意图协议追加在用户系统提示词之后，保持用户自定义提示词生效
        val messages = buildContextMessages(history, "$systemPrompt\n\n$GENERATION_PROTOCOL_PROMPT") { relativePath ->
            imageDataUris[relativePath]
        }

        val response: Response<ResponseBody> = apiService.chatCompletionsStream(
            authorization = "Bearer $apiKey",
            request = ChatCompletionRequest(
                model = settings.modelName,
                messages = messages,
                stream = true,
                // 默认值不序列化，保持与旧版本请求行为一致
                temperature = settings.temperature.takeIf { it != 1f }?.toDouble(),
                maxTokens = settings.maxTokens,
                topP = settings.topP.takeIf { it != 1f }?.toDouble()
            )
        )

        if (!response.isSuccessful) {
            val errorBody = response.errorBody()?.string().orEmpty()
            throw HttpException(response.code(), errorBody)
        }

        val body = response.body() ?: throw IOException("Empty response body")
        withContext(ioDispatcher) {
            val sb = StringBuilder()
            try {
                body.byteStream().bufferedReader().useLines { lines ->
                    for (line in lines) {
                        val delta = StreamParser.parseLine(line)
                        if (!delta.isNullOrEmpty()) {
                            sb.append(delta)
                            onDelta(delta)
                        }
                    }
                }
            } finally {
                // 无论正常结束、异常还是协程取消，都关闭响应体以释放底层连接
                body.close()
            }
            sb.toString()
        }
    }
}

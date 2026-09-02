package com.agnesai.chat.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agnesai.chat.data.generation.GenerationIntent
import com.agnesai.chat.data.generation.GenerationIntentParser
import com.agnesai.chat.data.generation.GenerationParams
import com.agnesai.chat.data.generation.GenerationParamsCodec
import com.agnesai.chat.data.generation.GenerationRepository
import com.agnesai.chat.data.local.MessageStatus
import com.agnesai.chat.data.local.Roles
import com.agnesai.chat.data.local.SessionType
import com.agnesai.chat.data.network.IMAGE_MODEL_2_5
import com.agnesai.chat.data.network.VIDEO_MODEL_2_5_FLASH
import com.agnesai.chat.data.repository.ChatRepository
import com.agnesai.chat.data.repository.HttpException
import com.agnesai.chat.data.repository.PersistImagesResult
import com.agnesai.chat.data.repository.encodeImagePaths
import com.agnesai.chat.data.repository.extractServerDetail
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import java.io.IOException
import java.util.concurrent.CancellationException
class ChatViewModel(
    private val repository: ChatRepository,
    private val generationRepository: GenerationRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState = _uiState.asStateFlow()

    /** 流式输出增量独立状态：与消息列表解耦，避免每次 token 增量触发整个 UI 重建。 */
    private val _streamingContent = MutableStateFlow("")
    val streamingContent = _streamingContent.asStateFlow()

    /** 每种能力类型各自的当前会话 id，实现三能力会话独立保持 */
    private val currentSessionIdByType = mutableMapOf<String, Long>()
    private var activeType: String = SessionType.CHAT
    private var sessionId: Long = 0L
    private var streamJob: Job? = null
    private var messagesCollector: Job? = null

    /** 当前内联生成任务（聊天内意图触发的图片/视频生成） */
    private var generationJob: Job? = null

    /** 正在内联生成的助手消息 id；0 表示无进行中任务。用于 UI 区分"生成中"与"生成中断" */
    private val _generatingMessageId = MutableStateFlow(0L)
    val generatingMessageId = _generatingMessageId.asStateFlow()

    init {
        observeSessions()
    }

    private fun observeSessions() {
        viewModelScope.launch {
            repository.observeSessions().collect { sessions ->
                val uiSessions = sessions.map { it.toUiSession() }
                _uiState.update {
                    it.copy(
                        sessions = uiSessions,
                        currentSessionTitle = uiSessions.firstOrNull { s -> s.id == it.currentSessionId }?.title
                            ?: it.currentSessionTitle
                    )
                }
                reconcileActiveSession(uiSessions)
            }
        }
    }

    /**
     * 依据最新会话列表校准各类型的当前会话，并确保活动类型始终有会话在展示：
     * 当前会话被删除/失效时恢复该类型最近会话，无会话时自动新建。
     */
    private fun reconcileActiveSession(uiSessions: List<UiSession>) {
        val currentActiveId = currentSessionIdByType[activeType] ?: 0L
        val activeList = uiSessions.filter { it.type == activeType }

        if (currentActiveId != 0L && activeList.none { it.id == currentActiveId }) {
            currentSessionIdByType[activeType] = 0L
        }
        if ((currentSessionIdByType[activeType] ?: 0L) == 0L) {
            if (activeList.isNotEmpty()) {
                switchSession(activeList.first().id)
            } else {
                createSession(activeType)
            }
        }

        SessionType.ALL.filter { it != activeType }.forEach { type ->
            if ((currentSessionIdByType[type] ?: 0L) == 0L) {
                uiSessions.firstOrNull { it.type == type }?.let { currentSessionIdByType[type] = it.id }
            }
        }
    }

    /** 切换当前能力（文本/图片/视频），保持各类型独立的当前会话。 */
    fun switchFeature(type: String) {
        if (type == activeType) return
        activeType = type
        val id = currentSessionIdByType[type]
        if (id != null && id != 0L) {
            switchSession(id)
        } else {
            val list = _uiState.value.sessions.filter { it.type == type }
            if (list.isNotEmpty()) {
                switchSession(list.first().id)
            } else {
                createSession(type)
            }
        }
    }

    fun newSession(type: String = activeType) {
        createSession(type)
    }

    private fun createSession(type: String) {
        viewModelScope.launch {
            val id = repository.createSession(type)
            switchSession(id)
        }
    }

    fun deleteSession(id: Long) {
        if (id == sessionId) {
            streamJob?.cancel()
            messagesCollector?.cancel()
            sessionId = 0L
            currentSessionIdByType[activeType] = 0L
        }
        viewModelScope.launch {
            repository.deleteSession(id)
            if (sessionId == 0L) {
                _uiState.update {
                    it.copy(
                        messages = emptyList(),
                        isStreaming = false,
                        error = null,
                        currentSessionId = 0L
                    )
                }
                _streamingContent.value = ""
                // 删除当前会话后由 observeSessions 接管：恢复该类型最近会话或新建
            }
        }
    }

    /**
     * 重命名会话标题。
     * @return true 表示标题有效并已提交保存；false 表示标题为空或仅空白，调用方应保留对话框就地提示。
     */
    fun renameSession(id: Long, newTitle: String): Boolean {
        val title = newTitle.trim()
        if (title.isEmpty()) return false
        viewModelScope.launch {
            repository.renameSession(id, title)
        }
        return true
    }

    fun switchSession(id: Long) {
        if (id == sessionId) return
        streamJob?.cancel()
        messagesCollector?.cancel()
        sessionId = id
        currentSessionIdByType[activeType] = id
        observeSessionMessages(id)
        _uiState.update {
            it.copy(
                messages = emptyList(),
                isStreaming = false,
                error = null,
                currentSessionId = id,
                currentSessionTitle = it.sessions.firstOrNull { s -> s.id == id }?.title ?: "New Chat"
            )
        }
        _streamingContent.value = ""
    }

    private fun observeSessionMessages(id: Long) {
        messagesCollector = viewModelScope.launch {
            repository.observeMessages(id).collect { entities ->
                val visible = entities.filterNot { it.status == MessageStatus.STREAMING }
                _uiState.update { state ->
                    state.copy(messages = visible.map { it.toUiMessage() })
                }
            }
        }
    }

    /** @return true 表示消息已受理（进入发送流程），false 表示被拒绝（输入框应保留内容） */
    fun sendMessage(text: String): Boolean {
        val content = text.trim()
        if (content.isEmpty() || _uiState.value.isStreaming || sessionId == 0L) return false
        startStreaming(content, emptyList())
        return true
    }

    /**
     * 发送带图片的消息。图片已通过 [persistMessageImages] 持久化，这里携带其相对路径，
     * 落库后由 [ChatRepository.streamChat] 读回编码为 OpenAI 兼容 content 数组。
     * @return true 表示已受理，false 表示被拒绝。
     */
    fun sendMessageWithImages(text: String, imagePaths: List<String>): Boolean {
        val content = text.trim()
        if (content.isEmpty() || _uiState.value.isStreaming || sessionId == 0L) return false
        if (imagePaths.isEmpty()) return sendMessage(text)
        startStreaming(content, imagePaths)
        return true
    }

    /**
     * 把待发送图片持久化到 App 内部存储并压缩（UI 选图后调用）。
     * 返回相对路径与 Data URI；任一图片失败时 [PersistImagesResult.error] 非空。
     */
    suspend fun persistMessageImages(
        sessionId: Long,
        uris: List<android.net.Uri>
    ): PersistImagesResult = repository.persistMessageImages(sessionId, uris)

    private fun startStreaming(content: String, imagePaths: List<String>) {
        // 捕获当前会话，避免流式回调里切走会话后污染其他会话的界面状态
        val targetSession = sessionId

        streamJob = viewModelScope.launch {
            // 以数据库中已落库的用户消息数判断是否首条：ERROR/STREAMING 残留不阻止后续补设标题
            val isFirstMessage = repository.countUserMessages(targetSession) == 0
            if (isFirstMessage) {
                repository.updateSessionTitle(targetSession, content.take(20))
            }
            repository.insertUserMessage(
                targetSession,
                content,
                encodeImagePaths(imagePaths).takeIf { imagePaths.isNotEmpty() }
            )
            _uiState.update {
                it.copy(isStreaming = true, error = null)
            }
            val assistantId = repository.insertAssistantMessage(
                targetSession, "", MessageStatus.STREAMING
            )

            // 用 StringBuilder 累积增量，避免逐 token 字符串拼接造成 O(n²) 分配
            val buffer = StringBuilder()
            val result = repository.streamChat(
                sessionId = targetSession,
                onDelta = { delta ->
                    // 切走会话后忽略旧会话的增量，避免串台
                    if (targetSession == sessionId) {
                        buffer.append(delta)
                        _streamingContent.value = buffer.toString()
                    }
                }
            )

            finishStreaming(targetSession, assistantId, result)
        }
    }

    /**
     * 重新生成上一条 AI 回复：保留原回复，基于当前会话已完成历史再次请求模型，
     * 在会话末尾追加一条新回复并实时流式渲染。
     * 无历史上下文（尚无已完成用户消息）时提示用户先发送消息。
     */
    fun regenerateReply() {
        val targetSession = sessionId
        if (targetSession == 0L || _uiState.value.isStreaming) return

        streamJob = viewModelScope.launch {
            val history = repository.getMessages(targetSession)
            val hasUserMessage = history.any {
                it.role == Roles.USER && it.status == MessageStatus.DONE
            }
            if (!hasUserMessage) {
                _uiState.update { it.copy(error = "请先发送消息") }
                return@launch
            }

            val assistantId = repository.insertAssistantMessage(
                targetSession, "", MessageStatus.STREAMING
            )
            _uiState.update {
                it.copy(isStreaming = true, error = null)
            }

            val buffer = StringBuilder()
            val result = repository.streamChat(
                sessionId = targetSession,
                onDelta = { delta ->
                    if (targetSession == sessionId) {
                        buffer.append(delta)
                        _streamingContent.value = buffer.toString()
                    }
                }
            )

            finishStreaming(targetSession, assistantId, result)
        }
    }

    /** 删除单条消息，界面随 Room Flow 自动刷新。 */
    fun deleteMessage(messageId: Long) {
        viewModelScope.launch {
            repository.deleteMessage(messageId)
        }
    }

    /** 重新发送：将该消息文本作为新消息发送，遵循与普通发送一致的流式与错误处理流程。 */
    fun resendMessage(text: String): Boolean = sendMessage(text)

    /**
     * 流式请求收尾：协程取消时清理残留占位消息；成功将占位落库 DONE；失败落库 ERROR。
     * 回复命中内联生成意图时转为生成占位并启动生成任务。
     * 仅在目标会话仍为当前会话时刷新界面状态，避免切走后串台。
     */
    private suspend fun finishStreaming(
        targetSession: Long,
        assistantId: Long,
        result: Result<String>
    ) {
        if (coroutineContext[Job]?.isCancelled == true) {
            withContext(NonCancellable) { repository.deleteMessage(assistantId) }
            return
        }

        result.fold(
            onSuccess = { full ->
                // 命中内联生成意图：消息转为生成占位，展示文本剥离协议标记
                val intent = GenerationIntentParser.parse(full)
                if (intent != null && generationRepository != null) {
                    beginInlineGeneration(targetSession, assistantId, GenerationIntentParser.displayText(full), intent)
                    if (targetSession == sessionId) {
                        _streamingContent.value = ""
                    }
                    return@fold
                }
                repository.updateMessage(assistantId, full, MessageStatus.DONE)
                _streamingContent.value = ""
                if (targetSession == sessionId) {
                    _uiState.update {
                        it.copy(isStreaming = false, error = null)
                    }
                }
            },
            onFailure = { e ->
                // 请求被取消（含底层 InterruptedIOException）：清理残留消息，不产生 ERROR 气泡
                if (coroutineContext[Job]?.isCancelled == true || e is CancellationException) {
                    withContext(NonCancellable) { repository.deleteMessage(assistantId) }
                    return@fold
                }
                val message = e.toUserMessage()
                repository.updateMessage(assistantId, message, MessageStatus.ERROR)
                _streamingContent.value = ""
                if (targetSession == sessionId) {
                    _uiState.update {
                        it.copy(isStreaming = false, error = message)
                    }
                }
            }
        )
    }

    /**
     * 启动聊天内联生成：占位消息置 GENERATING，后台调用生成接口。
     * 生成期间 isStreaming 复位（不阻塞继续聊天）；结果写回原会话消息。
     */
    private fun beginInlineGeneration(
        targetSession: Long,
        messageId: Long,
        displayText: String,
        intent: GenerationIntent
    ) {
        val params = when (intent) {
            is GenerationIntent.Image -> GenerationParams(
                type = SessionType.IMAGE,
                model = IMAGE_MODEL_2_5,
                ratio = "1:1"
            )
            is GenerationIntent.Video -> GenerationParams(
                type = SessionType.VIDEO,
                model = VIDEO_MODEL_2_5_FLASH,
                duration = "5s",
                quality = "720P",
                ratio = "16:9"
            )
        }
        val paramsJson = GenerationParamsCodec.encode(params)
        val generationRepo = generationRepository ?: return

        generationJob = viewModelScope.launch {
            repository.updateMessageWithParams(messageId, displayText, paramsJson, MessageStatus.GENERATING)
            _generatingMessageId.value = messageId
            if (targetSession == sessionId) {
                _uiState.update { it.copy(isStreaming = false, error = null) }
            }

            val result = when (intent) {
                is GenerationIntent.Image -> generationRepo.generateImage(
                    prompt = intent.prompt,
                    model = IMAGE_MODEL_2_5,
                    size = "2K",
                    ratio = "1:1",
                    referenceImages = emptyList()
                )
                is GenerationIntent.Video -> generationRepo.generateVideo(
                    prompt = intent.prompt,
                    model = VIDEO_MODEL_2_5_FLASH,
                    firstFrameImage = null,
                    lastFrameImage = null,
                    duration = "5s",
                    quality = "720P",
                    ratio = "16:9"
                )
            }

            _generatingMessageId.value = 0L
            result.fold(
                onSuccess = { url ->
                    repository.updateMessageWithParams(messageId, url, paramsJson, MessageStatus.DONE)
                },
                onFailure = { e ->
                    // 用户取消：由 cancelGeneration 负责删除占位，此处静默退出
                    if (e is CancellationException) return@fold
                    val message = e.message ?: "生成失败，请稍后重试"
                    repository.updateMessageWithParams(messageId, message, paramsJson, MessageStatus.ERROR)
                }
            )
        }
    }

    /** 取消进行中的内联生成：终止任务并删除占位消息。 */
    fun cancelGeneration() {
        val messageId = _generatingMessageId.value
        generationJob?.cancel()
        generationJob = null
        _generatingMessageId.value = 0L
        if (messageId != 0L) {
            viewModelScope.launch { repository.deleteMessage(messageId) }
        }
    }
}

/**
 * 按标题关键字实时过滤会话列表：包含匹配（忽略大小写），空关键字返回完整列表。
 * 仅作用于内存中的列表，不修改数据库。
 */
fun filteredSessions(query: String, sessions: List<UiSession>): List<UiSession> {
    val q = query.trim()
    if (q.isEmpty()) return sessions
    return sessions.filter { it.title.contains(q, ignoreCase = true) }
}

/** 把请求异常转换为用户可读的提示文案。 */
internal fun Throwable.toUserMessage(): String = when (this) {
    is HttpException -> when (code) {
        401 -> "API Key 无效，请检查设置"
        429 -> "请求过于频繁，请稍后重试"
        in 500..599 -> "服务暂时不可用，请稍后重试"
        else -> extractServerDetail(errorBody)?.let { "请求失败：$it" } ?: "请求失败 (HTTP $code)"
    }
    is CancellationException -> "请求已取消"
    is IOException -> "网络连接失败，请检查网络"
    else -> message ?: "发生未知错误"
}

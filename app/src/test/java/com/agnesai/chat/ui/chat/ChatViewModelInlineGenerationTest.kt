package com.agnesai.chat.ui.chat

import com.agnesai.chat.data.generation.GenerationIntent
import com.agnesai.chat.data.generation.GenerationParams
import com.agnesai.chat.data.generation.GenerationParamsCodec
import com.agnesai.chat.data.generation.GenerationRepository
import com.agnesai.chat.data.local.MessageDao
import com.agnesai.chat.data.local.MessageEntity
import com.agnesai.chat.data.local.MessageStatus
import com.agnesai.chat.data.local.Roles
import com.agnesai.chat.data.local.SessionDao
import com.agnesai.chat.data.local.SessionEntity
import com.agnesai.chat.data.local.SessionType
import com.agnesai.chat.data.network.AgnesApiService
import com.agnesai.chat.data.network.ChatCompletionRequest
import com.agnesai.chat.data.repository.ChatRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class ChatViewModelInlineGenerationTest {

    private class InMemoryMessageDao : MessageDao {
        val messages = mutableListOf<MessageEntity>()
        val deletedIds = mutableListOf<Long>()
        private var nextId = 1L
        private val _flow = MutableStateFlow<List<MessageEntity>>(emptyList())

        override fun observeMessages(sessionId: Long): Flow<List<MessageEntity>> =
            _flow.map { list -> list.filter { it.sessionId == sessionId } }

        override suspend fun getMessages(sessionId: Long): List<MessageEntity> =
            messages.filter { it.sessionId == sessionId }

        override suspend fun getById(id: Long): MessageEntity? =
            messages.firstOrNull { it.id == id }

        override suspend fun insert(message: MessageEntity): Long {
            val id = nextId++
            messages += message.copy(id = id)
            _flow.value = messages.toList()
            return id
        }

        override suspend fun updateContent(id: Long, content: String, status: String) {
            val idx = messages.indexOfFirst { it.id == id }
            if (idx >= 0) {
                messages[idx] = messages[idx].copy(content = content, status = status)
                _flow.value = messages.toList()
            }
        }

        override suspend fun updateContentAndParams(id: Long, content: String, params: String?, status: String) {
            val idx = messages.indexOfFirst { it.id == id }
            if (idx >= 0) {
                messages[idx] = messages[idx].copy(content = content, params = params, status = status)
                _flow.value = messages.toList()
            }
        }

        override suspend fun delete(id: Long) {
            deletedIds.add(id)
            messages.removeAll { it.id == id }
            _flow.value = messages.toList()
        }

        override suspend fun clearSession(sessionId: Long) {
            messages.removeAll { it.sessionId == sessionId }
            _flow.value = messages.toList()
        }

        override suspend fun countUserMessages(sessionId: Long): Int =
            messages.count { it.sessionId == sessionId && it.role == Roles.USER && it.status == MessageStatus.DONE }

        override suspend fun countByType(type: String): Long = 0

        override fun observeCompletedWorks(): Flow<List<com.agnesai.chat.data.works.MyWorkRow>> =
            flowOf(emptyList())
    }

    private class RecordingSessionDao : SessionDao {
        private val _sessions = MutableStateFlow<List<SessionEntity>>(emptyList())
        private var nextId = 1L

        override suspend fun insert(session: SessionEntity): Long {
            val id = nextId++
            _sessions.value = _sessions.value + session.copy(id = id)
            return id
        }

        override suspend fun getById(id: Long): SessionEntity? =
            _sessions.value.firstOrNull { it.id == id }

        override fun observeSessions(): Flow<List<SessionEntity>> = _sessions

        override fun observeSessionsByType(type: String): Flow<List<SessionEntity>> =
            flowOf(_sessions.value.filter { it.type == type })

        override suspend fun update(id: Long, updatedAt: Long, title: String) {
            _sessions.value = _sessions.value.map {
                if (it.id == id) it.copy(title = title, updatedAt = updatedAt) else it
            }
        }

        override suspend fun delete(id: Long) {
            _sessions.value = _sessions.value.filterNot { it.id == id }
        }

        override suspend fun countByType(type: String): Long =
            _sessions.value.count { it.type == type }.toLong()

        override suspend fun deleteByType(type: String): Int = 0
    }

    private class StubAgnesApiService(
        private val content: String,
        private val secondContent: String? = null
    ) : AgnesApiService {
        private var callCount = 0

        override suspend fun chatCompletionsStream(
            authorization: String,
            request: ChatCompletionRequest
        ): Response<ResponseBody> {
            callCount++
            val payload = if (callCount == 2 && secondContent != null) secondContent else content
            val body =
                "data: {\"choices\":[{\"delta\":{\"content\":\"${payload.replace("\"", "\\\"")}\"}}]}\n\ndata: [DONE]\n\n"
                    .toResponseBody("text/event-stream".toMediaType())
            return Response.success(body)
        }
    }

    private class FakeGenerationRepository(
        private val imageResult: Result<String>? = Result.success("https://img.example/cat.png"),
        private val videoResult: Result<String>? = Result.success("https://vid.example/cat.mp4"),
        private val hang: Boolean = false
    ) : GenerationRepository {
        var lastImagePrompt: String? = null
        var lastVideoPrompt: String? = null

        override suspend fun generateImage(
            prompt: String,
            model: String,
            size: String?,
            ratio: String?,
            referenceImages: List<String>
        ): Result<String> {
            lastImagePrompt = prompt
            if (hang) awaitCancellation()
            return imageResult!!
        }

        override suspend fun generateVideo(
            prompt: String,
            model: String,
            firstFrameImage: String?,
            lastFrameImage: String?,
            duration: String,
            quality: String,
            ratio: String
        ): Result<String> {
            lastVideoPrompt = prompt
            if (hang) awaitCancellation()
            return videoResult!!
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel(
        replyContent: String,
        generationRepository: GenerationRepository,
        ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO,
        secondReplyContent: String? = null
    ): Triple<ChatViewModel, RecordingSessionDao, InMemoryMessageDao> {
        val sessionDao = RecordingSessionDao()
        val messageDao = InMemoryMessageDao()
        val repo = ChatRepository(
            apiService = StubAgnesApiService(replyContent, secondReplyContent),
            settingsDataStoreProvider = { "test-key" to "test-system" },
            sessionDao = sessionDao,
            messageDao = messageDao,
            ioDispatcher = ioDispatcher
        )
        return Triple(
            ChatViewModel(repo, generationRepository),
            sessionDao,
            messageDao
        )
    }

    private fun lastAssistantMessage(dao: InMemoryMessageDao): MessageEntity =
        dao.messages.last { it.role == Roles.ASSISTANT }

    @Test
    fun `image intent converts reply to done media message`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val genRepo = FakeGenerationRepository()
        val (vm, _, messageDao) = buildViewModel(
            replyContent = "好的，正在为你生成：[GENERATE_IMAGE]一只可爱的橘猫[/GENERATE_IMAGE]",
            generationRepository = genRepo,
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )
        testScheduler.advanceUntilIdle()
        val sessionId = vm.uiState.value.currentSessionId

        assertTrue(vm.sendMessage("画一只猫"))
        testScheduler.advanceUntilIdle()

        val assistant = lastAssistantMessage(messageDao)
        assertEquals(sessionId, assistant.sessionId)
        assertEquals(MessageStatus.DONE, assistant.status)
        assertEquals("https://img.example/cat.png", assistant.content)
        assertNotNull(assistant.params)
        assertEquals(SessionType.IMAGE, GenerationParamsCodec.decode(assistant.params)?.type)
        assertEquals("一只可爱的橘猫", genRepo.lastImagePrompt)
        // 展示文本剥离协议标记
        assertTrue(assistant.params!!.contains("image"))
        assertEquals(0L, vm.generatingMessageId.value)
    }

    @Test
    fun `plain reply stays as text message`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val (vm, _, messageDao) = buildViewModel(
            replyContent = "你好呀！",
            generationRepository = FakeGenerationRepository(),
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )
        testScheduler.advanceUntilIdle()

        assertTrue(vm.sendMessage("你好"))
        testScheduler.advanceUntilIdle()

        val assistant = lastAssistantMessage(messageDao)
        assertEquals(MessageStatus.DONE, assistant.status)
        assertEquals("你好呀！", assistant.content)
        assertNull(assistant.params)
    }

    @Test
    fun `video intent uses generation repository and stores video params`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val genRepo = FakeGenerationRepository()
        val (vm, _, messageDao) = buildViewModel(
            replyContent = "开始生成：[GENERATE_VIDEO]海边日落[/GENERATE_VIDEO]",
            generationRepository = genRepo,
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )
        testScheduler.advanceUntilIdle()

        assertTrue(vm.sendMessage("生成一段海边日落的视频"))
        testScheduler.advanceUntilIdle()

        val assistant = lastAssistantMessage(messageDao)
        assertEquals(MessageStatus.DONE, assistant.status)
        assertEquals("https://vid.example/cat.mp4", assistant.content)
        val params: GenerationParams? = GenerationParamsCodec.decode(assistant.params)
        assertEquals(SessionType.VIDEO, params?.type)
        assertEquals("海边日落", genRepo.lastVideoPrompt)
    }

    @Test
    fun `generation failure marks message as error`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val genRepo = FakeGenerationRepository(
            imageResult = Result.failure(IllegalStateException("图片生成失败，请稍后重试"))
        )
        val (vm, _, messageDao) = buildViewModel(
            replyContent = "[GENERATE_IMAGE]一只猫[/GENERATE_IMAGE]",
            generationRepository = genRepo,
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )
        testScheduler.advanceUntilIdle()

        assertTrue(vm.sendMessage("画一只猫"))
        testScheduler.advanceUntilIdle()

        val assistant = lastAssistantMessage(messageDao)
        assertEquals(MessageStatus.ERROR, assistant.status)
        assertEquals("图片生成失败，请稍后重试", assistant.content)
    }

    @Test
    fun `cancelGeneration deletes generating placeholder`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val genRepo = FakeGenerationRepository(hang = true)
        val (vm, _, messageDao) = buildViewModel(
            replyContent = "[GENERATE_IMAGE]一只猫[/GENERATE_IMAGE]",
            generationRepository = genRepo,
            ioDispatcher = StandardTestDispatcher(testScheduler),
            secondReplyContent = "正在生成中，请稍等"
        )
        testScheduler.advanceUntilIdle()

        assertTrue(vm.sendMessage("画一只猫"))
        testScheduler.advanceUntilIdle()

        val assistant = lastAssistantMessage(messageDao)
        assertEquals(MessageStatus.GENERATING, assistant.status)
        assertEquals(assistant.id, vm.generatingMessageId.value)
        // 生成期间可继续发送普通消息
        assertTrue(vm.sendMessage("生成好了吗"))
        testScheduler.advanceUntilIdle()

        vm.cancelGeneration()
        testScheduler.advanceUntilIdle()

        assertTrue(messageDao.deletedIds.contains(assistant.id))
        assertEquals(0L, vm.generatingMessageId.value)
        assertTrue(messageDao.messages.none { it.id == assistant.id })
    }

    @Test
    fun `display text is stored while generating instead of raw markers`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val genRepo = FakeGenerationRepository(hang = true)
        val (vm, _, messageDao) = buildViewModel(
            replyContent = "好的：[GENERATE_IMAGE]一只猫[/GENERATE_IMAGE]",
            generationRepository = genRepo,
            ioDispatcher = StandardTestDispatcher(testScheduler),
            secondReplyContent = "正在生成中，请稍等"
        )
        testScheduler.advanceUntilIdle()

        assertTrue(vm.sendMessage("画一只猫"))
        testScheduler.advanceUntilIdle()

        val assistant = lastAssistantMessage(messageDao)
        assertEquals(MessageStatus.GENERATING, assistant.status)
        assertEquals("好的：", assistant.content)
    }
}

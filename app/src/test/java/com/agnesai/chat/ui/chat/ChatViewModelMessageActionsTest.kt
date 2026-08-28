package com.agnesai.chat.ui.chat

import com.agnesai.chat.data.local.MessageDao
import com.agnesai.chat.data.local.MessageEntity
import com.agnesai.chat.data.local.MessageStatus
import com.agnesai.chat.data.local.Roles
import com.agnesai.chat.data.local.SessionDao
import com.agnesai.chat.data.local.SessionEntity
import com.agnesai.chat.data.network.AgnesApiService
import com.agnesai.chat.data.network.ChatCompletionRequest
import com.agnesai.chat.data.repository.ChatRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.io.IOException

class ChatViewModelMessageActionsTest {

    /** 记录插入/更新/删除的可变消息存储，驱动 Room Flow 刷新。 */
    private class InMemoryMessageDao : MessageDao {
        val messages = mutableListOf<MessageEntity>()
        val deletedIds = mutableListOf<Long>()
        private var nextId = 1L
        private val _flow = MutableStateFlow<List<MessageEntity>>(emptyList())

        override fun observeMessages(sessionId: Long): Flow<List<MessageEntity>> =
            _flow.map { list -> list.filter { it.sessionId == sessionId } }

        override suspend fun getMessages(sessionId: Long): List<MessageEntity> =
            messages
                .filter { it.sessionId == sessionId }
                .sortedWith(compareBy({ it.timestamp }, { it.id }))

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

    /**
     * 可配置的流式接口桩：默认成功返回 [content]；[failAfter] 指定第几次请求抛 IOException（从 1 计数）；
     * [delayMillis] 使请求挂起指定时长，用于构造「流式进行中」场景。
     */
    private class StubAgnesApiService(
        private val content: String = "新回复",
        private val failAfter: Int = -1,
        private val delayMillis: Long = 0
    ) : AgnesApiService {
        var lastRequest: ChatCompletionRequest? = null
        private var callCount = 0

        override suspend fun chatCompletionsStream(
            authorization: String,
            request: ChatCompletionRequest
        ): Response<ResponseBody> {
            callCount++
            lastRequest = request
            if (delayMillis > 0) delay(delayMillis)
            if (callCount == failAfter) throw IOException("boom")
            val body =
                "data: {\"choices\":[{\"delta\":{\"content\":\"$content\"}}]}\n\ndata: [DONE]\n\n"
                    .toResponseBody("text/event-stream".toMediaType())
            return Response.success(body)
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel(
        api: StubAgnesApiService,
        ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.IO
    ): Triple<ChatViewModel, RecordingSessionDao, InMemoryMessageDao> {
        val sessionDao = RecordingSessionDao()
        val messageDao = InMemoryMessageDao()
        val repo = ChatRepository(
            apiService = api,
            settingsDataStoreProvider = { "test-key" to "test-system" },
            sessionDao = sessionDao,
            messageDao = messageDao,
            ioDispatcher = ioDispatcher
        )
        return Triple(ChatViewModel(repo), sessionDao, messageDao)
    }

    // ---------- regenerateReply ----------

    @Test
    fun `regenerateReply appends new reply and keeps original`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val api = StubAgnesApiService()
        val (vm, _, messageDao) = buildViewModel(api, StandardTestDispatcher(testScheduler))
        testScheduler.advanceUntilIdle()
        val sessionId = vm.uiState.value.currentSessionId

        assertTrue(vm.sendMessage("你好"))
        testScheduler.advanceUntilIdle()
        val before = messageDao.messages
        assertEquals(2, before.size)
        val originalReplyId = before.last().id

        vm.regenerateReply()
        testScheduler.advanceUntilIdle()

        val after = messageDao.messages
        assertEquals(3, after.size)
        assertTrue(after.any { it.id == originalReplyId && it.status == MessageStatus.DONE })
        val newReply = after.last()
        assertEquals(Roles.ASSISTANT, newReply.role)
        assertEquals(MessageStatus.DONE, newReply.status)
        assertEquals("新回复", newReply.content)
        assertFalse(vm.uiState.value.isStreaming)
    }

    @Test
    fun `regenerateReply uses full history as context`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val api = StubAgnesApiService()
        val (vm, _, messageDao) = buildViewModel(api, StandardTestDispatcher(testScheduler))
        testScheduler.advanceUntilIdle()

        assertTrue(vm.sendMessage("你好"))
        testScheduler.advanceUntilIdle()

        vm.regenerateReply()
        testScheduler.advanceUntilIdle()

        val requestMessages = api.lastRequest!!.messages
        assertEquals(3, requestMessages.size)
        assertEquals(Roles.SYSTEM, requestMessages[0].role)
        assertEquals("你好", requestMessages[1].content)
        assertEquals("新回复", requestMessages[2].content)
        assertTrue(requestMessages.none { it.content.isEmpty() })
    }

    @Test
    fun `regenerateReply on failure marks new message error and keeps original`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val api = StubAgnesApiService(failAfter = 2)
        val (vm, _, messageDao) = buildViewModel(api, StandardTestDispatcher(testScheduler))
        testScheduler.advanceUntilIdle()

        assertTrue(vm.sendMessage("你好"))
        testScheduler.advanceUntilIdle()
        val originalReplyId = messageDao.messages.last().id

        vm.regenerateReply()
        testScheduler.advanceUntilIdle()

        val after = messageDao.messages
        assertEquals(3, after.size)
        assertTrue(after.any { it.id == originalReplyId && it.status == MessageStatus.DONE })
        val newReply = after.last()
        assertEquals(Roles.ASSISTANT, newReply.role)
        assertEquals(MessageStatus.ERROR, newReply.status)
        assertFalse(vm.uiState.value.isStreaming)
    }

    @Test
    fun `regenerateReply without user message shows hint and inserts nothing`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val api = StubAgnesApiService()
        val (vm, _, messageDao) = buildViewModel(api, StandardTestDispatcher(testScheduler))
        testScheduler.advanceUntilIdle()

        vm.regenerateReply()
        testScheduler.advanceUntilIdle()

        assertEquals("请先发送消息", vm.uiState.value.error)
        assertTrue(messageDao.messages.isEmpty())
    }

    @Test
    fun `regenerateReply while streaming is ignored`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val api = StubAgnesApiService(delayMillis = 1000)
        val (vm, _, messageDao) = buildViewModel(api, StandardTestDispatcher(testScheduler))
        testScheduler.advanceUntilIdle()

        assertTrue(vm.sendMessage("你好"))
        testScheduler.advanceUntilIdle()
        val assistantCountBefore = messageDao.messages.count { it.role == Roles.ASSISTANT }

        vm.regenerateReply()
        testScheduler.runCurrent()
        assertTrue("流式应处于进行中", vm.uiState.value.isStreaming)

        vm.regenerateReply()
        testScheduler.advanceUntilIdle()

        val assistantCountAfter = messageDao.messages.count { it.role == Roles.ASSISTANT }
        assertEquals(assistantCountBefore + 1, assistantCountAfter)
        assertFalse(vm.uiState.value.isStreaming)
    }

    // ---------- deleteMessage ----------

    @Test
    fun `deleteMessage removes a single message`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val api = StubAgnesApiService()
        val (vm, _, messageDao) = buildViewModel(api, StandardTestDispatcher(testScheduler))
        testScheduler.advanceUntilIdle()

        assertTrue(vm.sendMessage("你好"))
        testScheduler.advanceUntilIdle()
        val target = messageDao.messages.first()

        vm.deleteMessage(target.id)
        testScheduler.advanceUntilIdle()

        assertTrue(messageDao.messages.none { it.id == target.id })
        assertTrue(messageDao.deletedIds.contains(target.id))
    }

    @Test
    fun `deleted message is excluded from regenerate context`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val api = StubAgnesApiService()
        val (vm, _, messageDao) = buildViewModel(api, StandardTestDispatcher(testScheduler))
        testScheduler.advanceUntilIdle()

        assertTrue(vm.sendMessage("你好"))
        testScheduler.advanceUntilIdle()
        val assistant = messageDao.messages.last()

        vm.deleteMessage(assistant.id)
        testScheduler.advanceUntilIdle()

        vm.regenerateReply()
        testScheduler.advanceUntilIdle()

        val requestMessages = api.lastRequest!!.messages
        assertEquals(2, requestMessages.size)
        assertTrue(requestMessages.none { it.content == assistant.content })
    }

    // ---------- resendMessage ----------

    @Test
    fun `resendMessage sends text as a new message`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val api = StubAgnesApiService()
        val (vm, _, messageDao) = buildViewModel(api, StandardTestDispatcher(testScheduler))
        testScheduler.advanceUntilIdle()

        assertTrue(vm.resendMessage("重发的内容"))
        testScheduler.advanceUntilIdle()

        val userMessages = messageDao.messages.filter { it.role == Roles.USER }
        assertEquals(1, userMessages.size)
        assertEquals("重发的内容", userMessages.first().content)
        assertTrue(
            messageDao.messages.any { it.role == Roles.ASSISTANT && it.status == MessageStatus.DONE }
        )
        assertFalse(vm.uiState.value.isStreaming)
    }
}

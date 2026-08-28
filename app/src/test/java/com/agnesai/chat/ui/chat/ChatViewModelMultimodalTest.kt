package com.agnesai.chat.ui.chat

import android.net.Uri
import com.agnesai.chat.data.local.MessageDao
import com.agnesai.chat.data.local.MessageEntity
import com.agnesai.chat.data.local.MessageStatus
import com.agnesai.chat.data.local.Roles
import com.agnesai.chat.data.local.SessionDao
import com.agnesai.chat.data.local.SessionEntity
import com.agnesai.chat.data.network.AgnesApiService
import com.agnesai.chat.data.network.ChatCompletionRequest
import com.agnesai.chat.data.repository.ChatRepository
import com.agnesai.chat.data.repository.MessageImageStore
import com.agnesai.chat.data.repository.PersistImagesResult
import com.agnesai.chat.data.repository.encodeImagePaths
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class ChatViewModelMultimodalTest {

    private class InMemoryMessageDao : MessageDao {
        val messages = mutableListOf<MessageEntity>()
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
        private val content: String = "回复内容",
        private val delayMillis: Long = 0
    ) : AgnesApiService {
        var lastRequest: ChatCompletionRequest? = null

        override suspend fun chatCompletionsStream(
            authorization: String,
            request: ChatCompletionRequest
        ): Response<ResponseBody> {
            lastRequest = request
            if (delayMillis > 0) delay(delayMillis)
            val body =
                "data: {\"choices\":[{\"delta\":{\"content\":\"$content\"}}]}\n\ndata: [DONE]\n\n"
                    .toResponseBody("text/event-stream".toMediaType())
            return Response.success(body)
        }
    }

    private class FakeMessageImageStore(
        private val persistPaths: List<String> = emptyList(),
        private val persistError: String? = null
    ) : MessageImageStore {
        var persistCalls = 0
        override suspend fun persistImages(sessionId: Long, uris: List<Uri>): PersistImagesResult {
            persistCalls++
            if (persistError != null) return PersistImagesResult(error = persistError)
            return PersistImagesResult(
                relativePaths = persistPaths,
                dataUris = persistPaths.map { "data:image/jpeg;base64,AAA" }
            )
        }

        override suspend fun loadDataUri(relativePath: String): String? =
            "data:image/jpeg;base64,AAA"

        override suspend fun deleteMessageImages(sessionId: Long, relativePaths: List<String>) = Unit
        override suspend fun deleteSessionImages(sessionId: Long) = Unit
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel(
        api: StubAgnesApiService = StubAgnesApiService(),
        imageStore: FakeMessageImageStore = FakeMessageImageStore(
            persistPaths = listOf("message_images/1/msg_100_0.jpg")
        ),
        ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.IO
    ): Triple<ChatViewModel, RecordingSessionDao, InMemoryMessageDao> {
        val sessionDao = RecordingSessionDao()
        val messageDao = InMemoryMessageDao()
        val repo = ChatRepository(
            apiService = api,
            settingsDataStoreProvider = { "test-key" to "test-system" },
            sessionDao = sessionDao,
            messageDao = messageDao,
            ioDispatcher = ioDispatcher,
            imageStore = imageStore
        )
        return Triple(ChatViewModel(repo), sessionDao, messageDao)
    }

    private fun imagePath() = "message_images/1/msg_100_0.jpg"

    // ---------- sendMessageWithImages ----------

    @Test
    fun `sendMessageWithImages persists imagePaths on user message`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val (vm, _, messageDao) = buildViewModel(
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )
        testScheduler.advanceUntilIdle()

        assertTrue(vm.sendMessageWithImages("看图", listOf(imagePath())))
        testScheduler.advanceUntilIdle()

        val userMessage = messageDao.messages.first { it.role == Roles.USER }
        assertEquals(encodeImagePaths(listOf(imagePath())), userMessage.imagePaths)
        assertTrue(messageDao.messages.any { it.role == Roles.ASSISTANT && it.status == MessageStatus.DONE })
        assertFalse(vm.uiState.value.isStreaming)
    }

    @Test
    fun `sendMessageWithImages includes image uris in request context`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val api = StubAgnesApiService()
        val (vm, _, _) = buildViewModel(
            api, ioDispatcher = StandardTestDispatcher(testScheduler)
        )
        testScheduler.advanceUntilIdle()

        assertTrue(vm.sendMessageWithImages("看图", listOf(imagePath())))
        testScheduler.advanceUntilIdle()

        val messages = api.lastRequest!!.messages
        assertEquals(2, messages.size)
        assertEquals(listOf("data:image/jpeg;base64,AAA"), messages[1].imageUrls)
        assertEquals("看图", messages[1].content)
    }

    @Test
    fun `sendMessageWithImages with empty paths behaves as text message`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val api = StubAgnesApiService()
        val (vm, _, messageDao) = buildViewModel(
            api, ioDispatcher = StandardTestDispatcher(testScheduler)
        )
        testScheduler.advanceUntilIdle()

        assertTrue(vm.sendMessageWithImages("纯文本", emptyList()))
        testScheduler.advanceUntilIdle()

        val userMessage = messageDao.messages.first { it.role == Roles.USER }
        assertNull(userMessage.imagePaths)
        assertNull(api.lastRequest!!.messages[1].imageUrls)
    }

    @Test
    fun `sendMessageWithImages while streaming is rejected`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val api = StubAgnesApiService(delayMillis = 1000)
        val (vm, _, messageDao) = buildViewModel(
            api, ioDispatcher = StandardTestDispatcher(testScheduler)
        )
        testScheduler.advanceUntilIdle()

        assertTrue(vm.sendMessage("先发一条"))
        testScheduler.runCurrent()
        assertTrue(vm.uiState.value.isStreaming)
        val before = messageDao.messages.filter { it.role == Roles.USER }.size

        assertFalse(vm.sendMessageWithImages("带图", listOf(imagePath())))
        testScheduler.advanceUntilIdle()

        assertEquals(before, messageDao.messages.filter { it.role == Roles.USER }.size)
        assertFalse(vm.uiState.value.isStreaming)
    }

    @Test
    fun `historical image message is re-encoded in later request context`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val api = StubAgnesApiService()
        val (vm, _, _) = buildViewModel(
            api, ioDispatcher = StandardTestDispatcher(testScheduler)
        )
        testScheduler.advanceUntilIdle()

        assertTrue(vm.sendMessageWithImages("第一张图", listOf(imagePath())))
        testScheduler.advanceUntilIdle()

        assertTrue(vm.sendMessage("继续聊"))
        testScheduler.advanceUntilIdle()

        val messages = api.lastRequest!!.messages
        // system + 带图user + assistant回复 + 新user
        assertEquals(4, messages.size)
        assertEquals(listOf("data:image/jpeg;base64,AAA"), messages[1].imageUrls)
        assertEquals(null, messages[3].imageUrls)
        assertEquals("继续聊", messages[3].content)
    }

    // ---------- persistMessageImages ----------

    @Test
    fun `persistMessageImages surfaces persist error`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val api = StubAgnesApiService()
        val store = FakeMessageImageStore(persistError = "图片超过 5MB，请压缩后重试")
        val (vm, _, _) = buildViewModel(
            api, store, StandardTestDispatcher(testScheduler)
        )
        testScheduler.advanceUntilIdle()

        val result = vm.persistMessageImages(1L, emptyList())
        testScheduler.advanceUntilIdle()

        assertEquals("图片超过 5MB，请压缩后重试", result.error)
        assertEquals(1, store.persistCalls)
    }

    @Test
    fun `persistMessageImages returns relative paths on success`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val api = StubAgnesApiService()
        val store = FakeMessageImageStore(persistPaths = listOf("message_images/1/msg_9.jpg"))
        val (vm, _, _) = buildViewModel(
            api, store, StandardTestDispatcher(testScheduler)
        )
        testScheduler.advanceUntilIdle()

        val result = vm.persistMessageImages(1L, emptyList())
        testScheduler.advanceUntilIdle()

        assertNull(result.error)
        assertEquals(listOf("message_images/1/msg_9.jpg"), result.relativePaths)
    }
}

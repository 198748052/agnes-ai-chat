package com.agnesai.chat.data.repository

import com.agnesai.chat.data.local.ChatSettings
import com.agnesai.chat.data.local.MessageDao
import com.agnesai.chat.data.local.MessageEntity
import com.agnesai.chat.data.local.MessageStatus
import com.agnesai.chat.data.local.Roles
import com.agnesai.chat.data.local.SessionDao
import com.agnesai.chat.data.local.SessionEntity
import com.agnesai.chat.data.network.AgnesApiService
import com.agnesai.chat.data.network.ChatCompletionRequest
import com.agnesai.chat.data.network.MODEL_NAME
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class ChatRepositoryTest {

    private class FakeMessageDao(private val messages: List<MessageEntity>) : MessageDao {
        override fun observeMessages(sessionId: Long): Flow<List<MessageEntity>> = flowOf(messages)
        override suspend fun getMessages(sessionId: Long): List<MessageEntity> = messages
        override suspend fun getById(id: Long): MessageEntity? = messages.firstOrNull { it.id == id }
        override suspend fun insert(message: MessageEntity): Long = 0
        override suspend fun updateContent(id: Long, content: String, status: String) = Unit
        override suspend fun updateContentAndParams(id: Long, content: String, params: String?, status: String) = Unit
        override suspend fun delete(id: Long) = Unit
        override suspend fun clearSession(sessionId: Long) = Unit
        override suspend fun countUserMessages(sessionId: Long): Int =
            messages.count { it.role == Roles.USER && it.status == MessageStatus.DONE }
        override suspend fun countByType(type: String): Long = 0
        override fun observeCompletedWorks(): Flow<List<com.agnesai.chat.data.works.MyWorkRow>> =
            flowOf(emptyList())
    }

    private class FakeSessionDao : SessionDao {
        override suspend fun insert(session: SessionEntity): Long = 0
        override suspend fun getById(id: Long): SessionEntity? = null
        override fun observeSessions(): Flow<List<SessionEntity>> = flowOf(emptyList())
        override fun observeSessionsByType(type: String): Flow<List<SessionEntity>> = flowOf(emptyList())
        override suspend fun update(id: Long, updatedAt: Long, title: String) = Unit
        override suspend fun delete(id: Long) = Unit
        override suspend fun countByType(type: String): Long = 0
        override suspend fun deleteByType(type: String): Int = 0
    }

    private class FakeAgnesApiService : AgnesApiService {
        var lastRequest: ChatCompletionRequest? = null
        override suspend fun chatCompletionsStream(
            authorization: String,
            request: ChatCompletionRequest
        ): Response<ResponseBody> {
            lastRequest = request
            val body = "data: {\"choices\":[{\"delta\":{\"content\":\"hi\"}}]}\n\ndata: [DONE]\n\n"
                .toResponseBody("text/event-stream".toMediaType())
            return Response.success(body)
        }
    }

    private class FakeMessageImageStore : MessageImageStore {
        override suspend fun persistImages(sessionId: Long, uris: List<android.net.Uri>): PersistImagesResult =
            PersistImagesResult(error = "not used")
        override suspend fun loadDataUri(relativePath: String): String? =
            if (relativePath.endsWith(".jpg")) "data:image/jpeg;base64,AAA" else null
        override suspend fun deleteMessageImages(sessionId: Long, relativePaths: List<String>) = Unit
        override suspend fun deleteSessionImages(sessionId: Long) = Unit
    }

    private fun buildRepository(
        api: FakeAgnesApiService,
        dao: FakeMessageDao
    ): ChatRepository = ChatRepository(
        apiService = api,
        settingsDataStoreProvider = { "test-key" to "test-system" },
        sessionDao = FakeSessionDao(),
        messageDao = dao
    )

    private fun buildRepositoryWithSettings(
        api: FakeAgnesApiService,
        dao: FakeMessageDao,
        chatSettings: ChatSettings
    ): ChatRepository = ChatRepository(
        apiService = api,
        settingsDataStoreProvider = { "test-key" to "test-system" },
        sessionDao = FakeSessionDao(),
        messageDao = dao,
        chatSettingsProvider = { chatSettings }
    )

    @Test
    fun `context excludes streaming and error messages`() = runTest {
        val now = System.currentTimeMillis()
        val dao = FakeMessageDao(
            listOf(
                MessageEntity(id = 1, sessionId = 1, role = Roles.USER, content = "用户消息", timestamp = now, status = MessageStatus.DONE),
                MessageEntity(id = 2, sessionId = 1, role = Roles.ASSISTANT, content = "正常回复", timestamp = now + 1, status = MessageStatus.DONE),
                MessageEntity(id = 3, sessionId = 1, role = Roles.ASSISTANT, content = "网络连接失败，请检查网络", timestamp = now + 2, status = MessageStatus.ERROR),
                MessageEntity(id = 4, sessionId = 1, role = Roles.ASSISTANT, content = "streaming-placeholder", timestamp = now + 3, status = MessageStatus.STREAMING)
            )
        )
        val api = FakeAgnesApiService()
        val repo = buildRepository(api, dao)

        repo.streamChat(sessionId = 1L, onDelta = {})

        val messages = api.lastRequest!!.messages
        assertEquals(3, messages.size)
        assertEquals(Roles.SYSTEM, messages[0].role)
        assertTrue(messages.none { it.content.contains("网络连接失败") })
        assertTrue(messages.none { it.content.contains("streaming-placeholder") })
    }

    @Test
    fun `context keeps normal history`() = runTest {
        val now = System.currentTimeMillis()
        val dao = FakeMessageDao(
            listOf(
                MessageEntity(id = 1, sessionId = 1, role = Roles.USER, content = "你好", timestamp = now, status = MessageStatus.DONE),
                MessageEntity(id = 2, sessionId = 1, role = Roles.ASSISTANT, content = "你好！", timestamp = now + 1, status = MessageStatus.DONE)
            )
        )
        val api = FakeAgnesApiService()
        val repo = buildRepository(api, dao)

        repo.streamChat(sessionId = 1L, onDelta = {})

        val messages = api.lastRequest!!.messages
        assertEquals(3, messages.size)
        assertEquals("你好", messages[1].content)
        assertEquals("你好！", messages[2].content)
    }

    @Test
    fun `context truncates to most recent history when over limit`() = runTest {
        val now = System.currentTimeMillis()
        val total = MAX_HISTORY_MESSAGES + 10
        val history = (1..total).flatMap { i ->
            listOf(
                MessageEntity(id = i * 2L, sessionId = 1, role = Roles.USER, content = "q$i", timestamp = now + i, status = MessageStatus.DONE),
                MessageEntity(id = i * 2L + 1, sessionId = 1, role = Roles.ASSISTANT, content = "a$i", timestamp = now + i + 1, status = MessageStatus.DONE)
            )
        }
        val dao = FakeMessageDao(history)
        val api = FakeAgnesApiService()
        val repo = buildRepository(api, dao)

        repo.streamChat(sessionId = 1L, onDelta = {})

        val messages = api.lastRequest!!.messages
        assertEquals(MAX_HISTORY_MESSAGES + 1, messages.size)
        assertEquals(Roles.SYSTEM, messages.first().role)
        // 60 条历史消息只保留最近 20 条（q21 起），最旧的 20 轮（q1..q20）应被裁剪
        val truncatedPrefix = (1..20).flatMap { listOf("q$it", "a$it") }
        assertTrue(messages.none { it.content in truncatedPrefix })
        assertEquals("q21", messages[1].content)
        assertEquals("a$total", messages[messages.size - 1].content)
    }

    @Test
    fun `default chat settings keep request behavior unchanged`() = runTest {
        val api = FakeAgnesApiService()
        val repo = buildRepositoryWithSettings(api, FakeMessageDao(emptyList()), ChatSettings())

        repo.streamChat(sessionId = 1L, onDelta = {})

        val req = api.lastRequest!!
        assertEquals(MODEL_NAME, req.model)
        assertEquals(null, req.temperature)
        assertEquals(null, req.topP)
        assertEquals(null, req.maxTokens)
    }

    @Test
    fun `custom chat settings injected into request`() = runTest {
        val api = FakeAgnesApiService()
        val repo = buildRepositoryWithSettings(
            api,
            FakeMessageDao(emptyList()),
            ChatSettings(
                modelName = "agnes-2.5-pro",
                temperature = 0.7f,
                topP = 0.8f,
                maxTokens = 256
            )
        )

        repo.streamChat(sessionId = 1L, onDelta = {})

        val req = api.lastRequest!!
        assertEquals("agnes-2.5-pro", req.model)
        assertEquals(0.7, req.temperature!!, 1e-4)
        assertEquals(0.8, req.topP!!, 1e-4)
        assertEquals(256, req.maxTokens)
    }

    @Test
    fun `default temperature and top p are not serialized`() = runTest {
        val api = FakeAgnesApiService()
        val repo = buildRepositoryWithSettings(
            api,
            FakeMessageDao(emptyList()),
            ChatSettings(temperature = 1.0f, topP = 1.0f, maxTokens = null)
        )

        repo.streamChat(sessionId = 1L, onDelta = {})

        val req = api.lastRequest!!
        assertEquals(null, req.temperature)
        assertEquals(null, req.topP)
        assertEquals(null, req.maxTokens)
    }

    @Test
    fun `boundary temperature and top p are injected`() = runTest {
        val api = FakeAgnesApiService()
        val repo = buildRepositoryWithSettings(
            api,
            FakeMessageDao(emptyList()),
            ChatSettings(temperature = 2.0f, topP = 0.0f, maxTokens = 1)
        )

        repo.streamChat(sessionId = 1L, onDelta = {})

        val req = api.lastRequest!!
        assertEquals(2.0, req.temperature)
        assertEquals(0.0, req.topP)
        assertEquals(1, req.maxTokens)
    }

    // ---------- buildContextMessages ----------

    @Test
    fun `buildContextMessages keeps only completed messages with system prompt`() {
        val now = System.currentTimeMillis()
        val history = listOf(
            MessageEntity(id = 1, sessionId = 1, role = Roles.USER, content = "q", timestamp = now, status = MessageStatus.DONE),
            MessageEntity(id = 2, sessionId = 1, role = Roles.ASSISTANT, content = "a", timestamp = now + 1, status = MessageStatus.DONE),
            MessageEntity(id = 3, sessionId = 1, role = Roles.ASSISTANT, content = "err", timestamp = now + 2, status = MessageStatus.ERROR),
            MessageEntity(id = 4, sessionId = 1, role = Roles.ASSISTANT, content = "stream", timestamp = now + 3, status = MessageStatus.STREAMING),
            MessageEntity(id = 5, sessionId = 1, role = Roles.ASSISTANT, content = "send", timestamp = now + 4, status = MessageStatus.SENDING)
        )

        val messages = buildContextMessages(history, "sys")

        assertEquals(3, messages.size)
        assertEquals(Roles.SYSTEM, messages[0].role)
        assertEquals("sys", messages[0].content)
        assertEquals("q", messages[1].content)
        assertEquals("a", messages[2].content)
    }

    @Test
    fun `buildContextMessages truncates to most recent history over limit`() {
        val now = System.currentTimeMillis()
        val total = MAX_HISTORY_MESSAGES + 5
        val history = (1..total).map { i ->
            MessageEntity(
                id = i.toLong(),
                sessionId = 1,
                role = Roles.USER,
                content = "m$i",
                timestamp = now + i,
                status = MessageStatus.DONE
            )
        }

        val messages = buildContextMessages(history, "sys")

        assertEquals(MAX_HISTORY_MESSAGES + 1, messages.size)
        assertEquals("sys", messages.first().content)
        assertEquals("m${total - MAX_HISTORY_MESSAGES + 1}", messages[1].content)
        assertEquals("m$total", messages.last().content)
    }

    // ---------- buildContextMessages with images ----------

    @Test
    fun `buildContextMessages injects image data uris via provider`() {
        val now = System.currentTimeMillis()
        val history = listOf(
            MessageEntity(
                id = 1, sessionId = 1, role = Roles.USER, content = "看图",
                timestamp = now, status = MessageStatus.DONE,
                imagePaths = encodeImagePaths(listOf("message_images/1/msg_1.jpg"))
            ),
            MessageEntity(
                id = 2, sessionId = 1, role = Roles.ASSISTANT, content = "好的",
                timestamp = now + 1, status = MessageStatus.DONE
            )
        )

        val messages = buildContextMessages(history, "sys") { rel ->
            if (rel == "message_images/1/msg_1.jpg") "data:image/jpeg;base64,AAA" else null
        }

        assertEquals(3, messages.size)
        assertEquals(listOf("data:image/jpeg;base64,AAA"), messages[1].imageUrls)
        assertEquals("看图", messages[1].content)
        assertEquals(null, messages[2].imageUrls)
    }

    @Test
    fun `buildContextMessages falls back to text when image uri missing`() {
        val now = System.currentTimeMillis()
        val history = listOf(
            MessageEntity(
                id = 1, sessionId = 1, role = Roles.USER, content = "看图",
                timestamp = now, status = MessageStatus.DONE,
                imagePaths = encodeImagePaths(listOf("message_images/1/msg_missing.jpg"))
            )
        )

        val messages = buildContextMessages(history, "sys") { null }

        assertEquals(2, messages.size)
        assertEquals(null, messages[1].imageUrls)
        assertEquals("看图", messages[1].content)
    }

    @Test
    fun `streamChat includes persisted image uris in context`() = runTest {
        val now = System.currentTimeMillis()
        val dao = FakeMessageDao(
            listOf(
                MessageEntity(
                    id = 1, sessionId = 1, role = Roles.USER, content = "看图",
                    timestamp = now, status = MessageStatus.DONE,
                    imagePaths = encodeImagePaths(listOf("message_images/1/msg_1.jpg"))
                )
            )
        )
        val api = FakeAgnesApiService()
        val repo = ChatRepository(
            apiService = api,
            settingsDataStoreProvider = { "test-key" to "test-system" },
            sessionDao = FakeSessionDao(),
            messageDao = dao,
            imageStore = FakeMessageImageStore()
        )

        repo.streamChat(sessionId = 1L, onDelta = {})

        val messages = api.lastRequest!!.messages
        assertEquals(2, messages.size)
        assertEquals(listOf("data:image/jpeg;base64,AAA"), messages[1].imageUrls)
    }
}

package com.agnesai.chat.data.repository

import com.agnesai.chat.data.local.MessageDao
import com.agnesai.chat.data.local.MessageEntity
import com.agnesai.chat.data.local.SessionDao
import com.agnesai.chat.data.local.SessionEntity
import com.agnesai.chat.data.local.SessionType
import com.agnesai.chat.data.network.AgnesApiService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.Response

class ChatSessionTypeTest {

    private class RecordingSessionDao : SessionDao {
        val inserted = mutableListOf<SessionEntity>()
        private val sessions = MutableStateFlow<List<SessionEntity>>(emptyList())

        override suspend fun insert(session: SessionEntity): Long {
            val id = session.id.takeIf { it != 0L } ?: (inserted.size + 1).toLong()
            inserted.add(session.copy(id = id))
            sessions.value = inserted.toList()
            return id
        }

        override suspend fun getById(id: Long): SessionEntity? =
            inserted.firstOrNull { it.id == id }

        override fun observeSessions(): Flow<List<SessionEntity>> = sessions

        override fun observeSessionsByType(type: String): Flow<List<SessionEntity>> =
            flowOf(inserted.filter { it.type == type })

        override suspend fun update(id: Long, updatedAt: Long, title: String) = Unit
        override suspend fun delete(id: Long) = Unit
        override suspend fun countByType(type: String): Long =
            inserted.count { it.type == type }.toLong()
        override suspend fun deleteByType(type: String): Int = 0
    }

    private class FakeMessageDao : MessageDao {
        override fun observeMessages(sessionId: Long): Flow<List<MessageEntity>> = flowOf(emptyList())
        override suspend fun getMessages(sessionId: Long): List<MessageEntity> = emptyList()
        override suspend fun getById(id: Long): MessageEntity? = null
        override suspend fun insert(message: MessageEntity): Long = 0
        override suspend fun updateContent(id: Long, content: String, status: String) = Unit
        override suspend fun updateContentAndParams(id: Long, content: String, params: String?, status: String) = Unit
        override suspend fun delete(id: Long) = Unit
        override suspend fun clearSession(sessionId: Long) = Unit
        override suspend fun countUserMessages(sessionId: Long): Int = 0
        override suspend fun countByType(type: String): Long = 0
        override fun observeCompletedWorks(): Flow<List<com.agnesai.chat.data.works.MyWorkRow>> =
            flowOf(emptyList())
    }

    private class StubAgnesApiService : AgnesApiService {
        override suspend fun chatCompletionsStream(
            authorization: String,
            request: com.agnesai.chat.data.network.ChatCompletionRequest
        ): Response<ResponseBody> = Response.success(
            "data: [DONE]\n\n".toResponseBody("text/event-stream".toMediaType())
        )
    }

    private fun buildRepository(
        sessionDao: RecordingSessionDao,
        messageDao: FakeMessageDao = FakeMessageDao()
    ): ChatRepository = ChatRepository(
        apiService = StubAgnesApiService(),
        settingsDataStoreProvider = { "test-key" to "test-system" },
        sessionDao = sessionDao,
        messageDao = messageDao
    )

    @Test
    fun `createSession persists requested type`() = runTest {
        val sessionDao = RecordingSessionDao()
        val repo = buildRepository(sessionDao)

        val chatId = repo.createSession(SessionType.CHAT)
        val imageId = repo.createSession(SessionType.IMAGE)
        val videoId = repo.createSession(SessionType.VIDEO)

        assertEquals(SessionType.CHAT, sessionDao.getById(chatId)?.type)
        assertEquals(SessionType.IMAGE, sessionDao.getById(imageId)?.type)
        assertEquals(SessionType.VIDEO, sessionDao.getById(videoId)?.type)
    }

    @Test
    fun `createSession defaults to chat type`() = runTest {
        val sessionDao = RecordingSessionDao()
        val repo = buildRepository(sessionDao)

        val id = repo.createSession()

        assertEquals(SessionType.CHAT, sessionDao.getById(id)?.type)
    }

    @Test
    fun `observeSessionsByType filters sessions by type`() = runTest {
        val sessionDao = RecordingSessionDao()
        val repo = buildRepository(sessionDao)

        repo.createSession(SessionType.CHAT)
        repo.createSession(SessionType.IMAGE)
        repo.createSession(SessionType.CHAT)
        repo.createSession(SessionType.VIDEO)

        val chatSessions = sessionDao.observeSessionsByType(SessionType.CHAT)
        val imageSessions = sessionDao.observeSessionsByType(SessionType.IMAGE)
        val videoSessions = sessionDao.observeSessionsByType(SessionType.VIDEO)

        chatSessions.collect { assertEquals(2, it.size) }
        imageSessions.collect { assertEquals(1, it.size) }
        videoSessions.collect { assertEquals(1, it.size) }
    }
}

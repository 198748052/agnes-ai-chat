package com.agnesai.chat.ui.chat

import com.agnesai.chat.data.local.MessageDao
import com.agnesai.chat.data.local.MessageEntity
import com.agnesai.chat.data.local.SessionDao
import com.agnesai.chat.data.local.SessionEntity
import com.agnesai.chat.data.local.SessionType
import com.agnesai.chat.data.network.AgnesApiService
import com.agnesai.chat.data.network.ChatCompletionRequest
import com.agnesai.chat.data.repository.ChatRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
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

class ChatViewModelSessionManagementTest {

    private class RecordingSessionDao : SessionDao {
        private val _sessions = MutableStateFlow<List<SessionEntity>>(emptyList())
        val updatedTitles = mutableListOf<Pair<Long, String>>()
        val deletedIds = mutableListOf<Long>()
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
            updatedTitles.add(id to title)
            _sessions.value = _sessions.value.map {
                if (it.id == id) it.copy(title = title, updatedAt = updatedAt) else it
            }
        }

        override suspend fun delete(id: Long) {
            deletedIds.add(id)
            _sessions.value = _sessions.value.filterNot { it.id == id }
        }

        override suspend fun countByType(type: String): Long =
            _sessions.value.count { it.type == type }.toLong()

        override suspend fun deleteByType(type: String): Int = 0
    }

    private class EmptyMessageDao : MessageDao {
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
            request: ChatCompletionRequest
        ): Response<ResponseBody> = Response.success(
            "data: [DONE]\n\n".toResponseBody("text/event-stream".toMediaType())
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel(): Triple<ChatViewModel, RecordingSessionDao, EmptyMessageDao> {
        val sessionDao = RecordingSessionDao()
        val messageDao = EmptyMessageDao()
        val repo = ChatRepository(
            apiService = StubAgnesApiService(),
            settingsDataStoreProvider = { "test-key" to "test-system" },
            sessionDao = sessionDao,
            messageDao = messageDao
        )
        return Triple(ChatViewModel(repo), sessionDao, messageDao)
    }

    // ---------- renameSession ----------

    @Test
    fun `renameSession with blank title returns false and does not update dao`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val (vm, sessionDao, _) = buildViewModel()
        testScheduler.advanceUntilIdle()

        val result = vm.renameSession(1L, "   ")

        assertFalse(result)
        assertTrue(sessionDao.updatedTitles.isEmpty())
    }

    @Test
    fun `renameSession with valid title returns true and updates dao`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val (vm, sessionDao, _) = buildViewModel()
        testScheduler.advanceUntilIdle()

        val result = vm.renameSession(1L, "新的标题")

        assertTrue(result)
        testScheduler.advanceUntilIdle()
        assertEquals(listOf(1L to "新的标题"), sessionDao.updatedTitles)
    }

    @Test
    fun `renameSession trims surrounding whitespace`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val (vm, sessionDao, _) = buildViewModel()
        testScheduler.advanceUntilIdle()

        val result = vm.renameSession(1L, "  新标题  ")

        assertTrue(result)
        testScheduler.advanceUntilIdle()
        assertEquals(listOf(1L to "新标题"), sessionDao.updatedTitles)
    }

    // ---------- filteredSessions ----------

    private fun session(id: Long, title: String) = UiSession(id, title, SessionType.CHAT, 0L)

    @Test
    fun `filteredSessions keeps sessions containing the query`() {
        val sessions = listOf(
            session(1, "产品介绍"),
            session(2, "图片生成"),
            session(3, "视频制作")
        )

        val result = filteredSessions("产品", sessions)

        assertEquals(listOf(1L), result.map { it.id })
    }

    @Test
    fun `filteredSessions returns all sessions when query is blank`() {
        val sessions = listOf(session(1, "a"), session(2, "b"))

        assertEquals(sessions, filteredSessions("", sessions))
        assertEquals(sessions, filteredSessions("   ", sessions))
    }

    @Test
    fun `filteredSessions returns empty when no session matches`() {
        val sessions = listOf(session(1, "a"), session(2, "b"))

        assertTrue(filteredSessions("不存在的关键字", sessions).isEmpty())
    }

    @Test
    fun `filteredSessions matches case insensitively`() {
        val sessions = listOf(session(1, "Hello World"))

        assertEquals(listOf(1L), filteredSessions("hello", sessions).map { it.id })
    }

    // ---------- deleteSession ----------

    @Test
    fun `deleting current session restores to a fresh session`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val (vm, sessionDao, _) = buildViewModel()
        testScheduler.advanceUntilIdle()

        val currentId = vm.uiState.value.currentSessionId
        assertTrue("应已自动创建首个会话", currentId != 0L)

        vm.deleteSession(currentId)
        testScheduler.advanceUntilIdle()

        val newId = vm.uiState.value.currentSessionId
        assertTrue("删除当前会话后应恢复为有效会话", newId != 0L)
        assertTrue("应切换到新会话", newId != currentId)
        assertEquals(listOf(currentId), sessionDao.deletedIds)
    }
}

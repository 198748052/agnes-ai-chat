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
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.ResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class ChatViewModelCancellationTest {

    private class RecordingMessageDao : MessageDao {
        val deleted = mutableListOf<Long>()
        val updated = mutableListOf<Triple<Long, String, String>>()
        private var nextId = 100L

        override fun observeMessages(sessionId: Long): Flow<List<MessageEntity>> = flowOf(emptyList())
        override suspend fun getMessages(sessionId: Long): List<MessageEntity> = emptyList()
        override suspend fun getById(id: Long): MessageEntity? = null
        override suspend fun insert(message: MessageEntity): Long = nextId++
        override suspend fun updateContent(id: Long, content: String, status: String) {
            updated.add(Triple(id, content, status))
        }
        override suspend fun updateContentAndParams(id: Long, content: String, params: String?, status: String) {
            updated.add(Triple(id, content, status))
        }
        override suspend fun delete(id: Long) {
            deleted.add(id)
        }
        override suspend fun clearSession(sessionId: Long) = Unit
        override suspend fun countUserMessages(sessionId: Long): Int = 0
        override suspend fun countByType(type: String): Long = 0
        override fun observeCompletedWorks(): Flow<List<com.agnesai.chat.data.works.MyWorkRow>> =
            flowOf(emptyList())
    }

    private class RecordingSessionDao : SessionDao {
        val deleted = mutableListOf<Long>()
        override suspend fun insert(session: SessionEntity): Long = 1
        override suspend fun getById(id: Long): SessionEntity? = null
        override fun observeSessions(): Flow<List<SessionEntity>> = flowOf(emptyList())
        override fun observeSessionsByType(type: String): Flow<List<SessionEntity>> = flowOf(emptyList())
        override suspend fun update(id: Long, updatedAt: Long, title: String) = Unit
        override suspend fun delete(id: Long) {
            deleted.add(id)
        }
        override suspend fun countByType(type: String): Long = 0
        override suspend fun deleteByType(type: String): Int = 0
    }

    private class HangingAgnesApiService : AgnesApiService {
        override suspend fun chatCompletionsStream(
            authorization: String,
            request: ChatCompletionRequest
        ): Response<ResponseBody> {
            awaitCancellation()
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel(
        messageDao: RecordingMessageDao = RecordingMessageDao(),
        sessionDao: RecordingSessionDao = RecordingSessionDao()
    ): Triple<ChatViewModel, RecordingMessageDao, RecordingSessionDao> {
        val repo = ChatRepository(
            apiService = HangingAgnesApiService(),
            settingsDataStoreProvider = { "test-key" to "test-system" },
            sessionDao = sessionDao,
            messageDao = messageDao
        )
        return Triple(ChatViewModel(repo), messageDao, sessionDao)
    }

    @Test
    fun `switching session cancels stream and cleans leftover streaming message`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val (vm, dao, _) = buildViewModel()

        testScheduler.advanceUntilIdle()
        vm.sendMessage("你好")
        testScheduler.advanceUntilIdle()

        vm.switchSession(2L)
        testScheduler.advanceUntilIdle()

        assertTrue("应删除残留的 STREAMING 消息", dao.deleted.contains(101L))
        assertTrue("不应写入 ERROR 状态消息", dao.updated.none { it.third == MessageStatus.ERROR })
        assertNull("不应产生错误气泡", vm.uiState.value.error)
    }

    @Test
    fun `deleting session cancels stream and cleans leftover streaming message`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val (vm, dao, sessionDao) = buildViewModel()

        testScheduler.advanceUntilIdle()
        vm.sendMessage("你好")
        testScheduler.advanceUntilIdle()

        vm.deleteSession(1L)
        testScheduler.advanceUntilIdle()

        assertTrue("应删除残留的 STREAMING 消息", dao.deleted.contains(101L))
        assertTrue("不应写入 ERROR 状态消息", dao.updated.none { it.third == MessageStatus.ERROR })
        assertNull("不应产生错误气泡", vm.uiState.value.error)
        assertEquals("会话应被删除", listOf(1L), sessionDao.deleted)
    }
}

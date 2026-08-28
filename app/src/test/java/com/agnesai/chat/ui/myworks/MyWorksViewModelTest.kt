package com.agnesai.chat.ui.myworks

import com.agnesai.chat.data.local.MessageDao
import com.agnesai.chat.data.local.MessageEntity
import com.agnesai.chat.data.local.SessionDao
import com.agnesai.chat.data.local.SessionEntity
import com.agnesai.chat.data.works.MyWork
import com.agnesai.chat.data.works.MyWorkRow
import com.agnesai.chat.data.works.MyWorksRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MyWorksViewModelTest {

    private class FakeMessageDao(private val initialRows: List<MyWorkRow>) : MessageDao {
        val rows = MutableStateFlow(initialRows)
        val deleted = mutableListOf<Long>()

        override fun observeMessages(sessionId: Long): Flow<List<MessageEntity>> = flowOf(emptyList())
        override suspend fun getMessages(sessionId: Long): List<MessageEntity> = emptyList()
        override suspend fun getById(id: Long): MessageEntity? = null
        override suspend fun insert(message: MessageEntity): Long = 0
        override suspend fun updateContent(id: Long, content: String, status: String) = Unit
        override suspend fun updateContentAndParams(id: Long, content: String, params: String?, status: String) = Unit
        override suspend fun delete(id: Long) {
            deleted.add(id)
            rows.value = rows.value.filterNot { it.id == id }
        }
        override suspend fun clearSession(sessionId: Long) = Unit
        override suspend fun countUserMessages(sessionId: Long): Int = 0
        override suspend fun countByType(type: String): Long = 0
        override fun observeCompletedWorks(): Flow<List<MyWorkRow>> = rows
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

    private fun row(
        id: Long,
        type: String,
        title: String = "会话",
        prompt: String? = "提示词"
    ) = MyWorkRow(
        id = id,
        sessionId = id * 10,
        content = "https://example.com/$id",
        params = null,
        timestamp = id,
        sessionTitle = title,
        sessionType = type,
        prompt = prompt
    )

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun build(
        rows: List<MyWorkRow>
    ): Pair<MyWorksViewModel, FakeMessageDao> {
        val dao = FakeMessageDao(rows)
        val repo = MyWorksRepository(dao)
        return MyWorksViewModel(repo) to dao
    }

    @Test
    fun `init loads works and sets loading false`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val (vm, _) = build(listOf(row(1, "image"), row(2, "video")))

        testScheduler.advanceUntilIdle()

        assertEquals(2, vm.uiState.value.works.size)
        assertFalse(vm.uiState.value.loading)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `setFilter IMAGE shows only image works`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val (vm, _) = build(listOf(row(1, "image"), row(2, "video"), row(3, "image")))

        testScheduler.advanceUntilIdle()
        vm.setFilter(WorkFilter.IMAGE)

        assertEquals(2, vm.uiState.value.visibleWorks.size)
        assertTrue(vm.uiState.value.visibleWorks.all { it.type == "image" })
    }

    @Test
    fun `setFilter VIDEO shows only video works`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val (vm, _) = build(listOf(row(1, "image"), row(2, "video")))

        testScheduler.advanceUntilIdle()
        vm.setFilter(WorkFilter.VIDEO)

        assertEquals(1, vm.uiState.value.visibleWorks.size)
        assertEquals("video", vm.uiState.value.visibleWorks[0].type)
    }

    @Test
    fun `setFilter ALL shows all works`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val (vm, _) = build(listOf(row(1, "image"), row(2, "video")))

        testScheduler.advanceUntilIdle()
        vm.setFilter(WorkFilter.ALL)

        assertEquals(2, vm.uiState.value.visibleWorks.size)
    }

    @Test
    fun `confirmDelete removes work from list and closes dialog`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val (vm, dao) = build(listOf(row(1, "image"), row(2, "video")))

        testScheduler.advanceUntilIdle()
        val work = vm.uiState.value.works.first { it.id == 1L }
        vm.requestDelete(work)
        assertEquals(1L, vm.uiState.value.pendingDeleteWork?.id)

        vm.confirmDelete()
        testScheduler.advanceUntilIdle()

        assertEquals(listOf(1L), dao.deleted)
        assertNull(vm.uiState.value.pendingDeleteWork)
        assertEquals(1, vm.uiState.value.works.size)
        assertFalse(vm.uiState.value.works.any { it.id == 1L })
    }

    @Test
    fun `cancelDelete keeps work in list and clears dialog`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val (vm, _) = build(listOf(row(1, "image")))

        testScheduler.advanceUntilIdle()
        val work = vm.uiState.value.works.first()
        vm.requestDelete(work)
        assertEquals(1L, vm.uiState.value.pendingDeleteWork?.id)

        vm.cancelDelete()

        assertNull(vm.uiState.value.pendingDeleteWork)
        assertEquals(1, vm.uiState.value.works.size)
    }

    @Test
    fun `openDetail sets detail work and closeDetail clears it`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val (vm, _) = build(listOf(row(1, "image")))

        testScheduler.advanceUntilIdle()
        val work = vm.uiState.value.works.first()
        vm.openDetail(work)

        assertEquals(1L, vm.uiState.value.detailWork?.id)
        vm.closeDetail()
        assertNull(vm.uiState.value.detailWork)
    }

    @Test
    fun `MyWork and MyWorkRow equality holds for detail usage`() {
        val work = MyWork(
            id = 1,
            sessionId = 10,
            type = "image",
            url = "https://example.com/1",
            prompt = "提示词",
            sessionTitle = "会话",
            timestamp = 1,
            params = null
        )
        assertEquals("image", work.type)
        assertEquals("https://example.com/1", work.url)
    }
}

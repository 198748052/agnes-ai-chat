package com.agnesai.chat.data.stats

import com.agnesai.chat.data.local.MessageDao
import com.agnesai.chat.data.local.MessageEntity
import com.agnesai.chat.data.local.SessionDao
import com.agnesai.chat.data.local.SessionEntity
import com.agnesai.chat.data.works.MyWorkRow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class StatsRepositoryTest {

    private class FakeMessageDao(private val rows: List<MyWorkRow> = emptyList()) : MessageDao {
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
        override fun observeCompletedWorks(): Flow<List<MyWorkRow>> = flowOf(rows)
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

    @Test
    fun `aggregateCounts empty list returns zeros`() {
        val result = aggregateCounts(emptyList(), ZoneId.of("UTC"), Instant.parse("2026-08-23T12:00:00Z"))
        assertEquals(PeriodCounts(), result)
    }

    @Test
    fun `aggregateCounts splits today week month total`() {
        val zone = ZoneId.of("UTC")
        val now = Instant.parse("2026-08-23T12:00:00Z")
        val timestamps = listOf(
            Instant.parse("2026-08-23T10:00:00Z").toEpochMilli(),
            Instant.parse("2026-08-20T10:00:00Z").toEpochMilli(),
            Instant.parse("2026-08-02T10:00:00Z").toEpochMilli(),
            Instant.parse("2026-07-27T10:00:00Z").toEpochMilli()
        )
        val result = aggregateCounts(timestamps, zone, now)
        assertEquals(PeriodCounts(today = 1, week = 2, month = 3, total = 4), result)
    }

    @Test
    fun `aggregateCounts counts ts at boundary start as today`() {
        val zone = ZoneId.of("UTC")
        val now = Instant.parse("2026-08-23T12:00:00Z")
        val midnight = Instant.parse("2026-08-23T00:00:00Z").toEpochMilli()
        val result = aggregateCounts(listOf(midnight), zone, now)
        assertEquals(PeriodCounts(today = 1, week = 1, month = 1, total = 1), result)
    }

    @Test
    fun `loadStats aggregates local works by type`() = runTest {
        val now = Instant.parse("2026-08-23T12:00:00Z")
        val rows = listOf(
            MyWorkRow(
                id = 1, sessionId = 10, content = "u1",
                params = null, timestamp = now.toEpochMilli(),
                sessionTitle = "t", sessionType = "image", prompt = null
            ),
            MyWorkRow(
                id = 2, sessionId = 11, content = "u2",
                params = null, timestamp = now.toEpochMilli(),
                sessionTitle = "t", sessionType = "video", prompt = null
            ),
            MyWorkRow(
                id = 3, sessionId = 12, content = "u3",
                params = null, timestamp = now.toEpochMilli(),
                sessionTitle = "t", sessionType = "image", prompt = null
            )
        )
        val repo = StatsRepository(FakeMessageDao(rows))
        val result = repo.loadStats(ZoneId.of("UTC"), now)
        assertEquals(PeriodCounts(today = 2, week = 2, month = 2, total = 2), result.image)
        assertEquals(PeriodCounts(today = 1, week = 1, month = 1, total = 1), result.video)
    }

    @Test
    fun `loadStats ignores chat sessions`() = runTest {
        val rows = listOf(
            MyWorkRow(
                id = 1, sessionId = 10, content = "hi",
                params = null, timestamp = System.currentTimeMillis(),
                sessionTitle = "聊天", sessionType = "chat", prompt = null
            )
        )
        val repo = StatsRepository(FakeMessageDao(rows))
        val result = repo.loadStats()
        assertTrue(result.image.total == 0 && result.video.total == 0)
    }
}

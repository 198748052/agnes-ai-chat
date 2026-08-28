package com.agnesai.chat.data.works

import com.agnesai.chat.data.local.MessageDao
import com.agnesai.chat.data.local.MessageEntity
import com.agnesai.chat.data.local.SessionDao
import com.agnesai.chat.data.local.SessionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MyWorksRepositoryTest {

    private class FakeMessageDao(private val rows: List<MyWorkRow> = emptyList()) : MessageDao {
        val deleted = mutableListOf<Long>()
        override fun observeMessages(sessionId: Long): Flow<List<MessageEntity>> = flowOf(emptyList())
        override suspend fun getMessages(sessionId: Long): List<MessageEntity> = emptyList()
        override suspend fun getById(id: Long): MessageEntity? = null
        override suspend fun insert(message: MessageEntity): Long = 0
        override suspend fun updateContent(id: Long, content: String, status: String) = Unit
        override suspend fun updateContentAndParams(id: Long, content: String, params: String?, status: String) = Unit
        override suspend fun delete(id: Long) {
            deleted.add(id)
        }
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
    fun `observeWorks maps row fields to work`() = runTest {
        val dao = FakeMessageDao(
            listOf(
                MyWorkRow(
                    id = 1,
                    sessionId = 10,
                    content = "https://example.com/img.png",
                    params = """{"type":"image","model":"agnes-image-2.1-flash","ratio":"1:1"}""",
                    timestamp = 1000,
                    sessionTitle = "我的图片",
                    sessionType = "image",
                    prompt = "一只猫"
                )
            )
        )
        val repo = MyWorksRepository(dao)

        val works = repo.observeWorks().first()

        assertEquals(1, works.size)
        val work = works[0]
        assertEquals(1L, work.id)
        assertEquals(10L, work.sessionId)
        assertEquals("image", work.type)
        assertEquals("https://example.com/img.png", work.url)
        assertEquals("我的图片", work.sessionTitle)
        assertEquals(1000L, work.timestamp)
        assertEquals("一只猫", work.prompt)
        assertEquals("agnes-image-2.1-flash", work.params?.model)
        assertEquals("1:1", work.params?.ratio)
    }

    @Test
    fun `observeWorks parses invalid params to null without crash`() = runTest {
        val dao = FakeMessageDao(
            listOf(
                MyWorkRow(
                    id = 1,
                    sessionId = 10,
                    content = "https://example.com/v.mp4",
                    params = "not-valid-json{{{",
                    timestamp = 1000,
                    sessionTitle = "我的视频",
                    sessionType = "video",
                    prompt = null
                )
            )
        )
        val repo = MyWorksRepository(dao)

        val works = repo.observeWorks().first()

        assertEquals(1, works.size)
        assertNull(works[0].params)
        assertNull(works[0].prompt)
    }

    @Test
    fun `observeWorks keeps null params as null`() = runTest {
        val dao = FakeMessageDao(
            listOf(
                MyWorkRow(
                    id = 2,
                    sessionId = 11,
                    content = "https://example.com/img2.png",
                    params = null,
                    timestamp = 2000,
                    sessionTitle = "无参数",
                    sessionType = "image",
                    prompt = "prompt"
                )
            )
        )
        val repo = MyWorksRepository(dao)

        val works = repo.observeWorks().first()

        assertEquals(1, works.size)
        assertNull(works[0].params)
    }

    @Test
    fun `deleteWork forwards message id to dao`() = runTest {
        val dao = FakeMessageDao()
        val repo = MyWorksRepository(dao)

        repo.deleteWork(42)

        assertEquals(listOf(42L), dao.deleted)
    }
}

package com.agnesai.chat.data.storage

import org.junit.Assert.assertEquals
import org.junit.Test

class StorageModelsTest {

    @Test
    fun `estimate distributes db size by message proportion`() {
        val sizes = estimateMessageSizes(
            dbBytes = 1000,
            messageCounts = mapOf(
                StorageType.CHAT to 300L,
                StorageType.IMAGE to 100L,
                StorageType.VIDEO to 100L
            )
        )

        assertEquals(600L, sizes[StorageType.CHAT])
        assertEquals(200L, sizes[StorageType.IMAGE])
        assertEquals(200L, sizes[StorageType.VIDEO])
        assertEquals(1000L, sizes.values.sum())
    }

    @Test
    fun `estimate returns zero when total messages is zero`() {
        val sizes = estimateMessageSizes(
            dbBytes = 1000,
            messageCounts = mapOf(
                StorageType.CHAT to 0L,
                StorageType.IMAGE to 0L,
                StorageType.VIDEO to 0L
            )
        )

        assertEquals(0L, sizes.values.sum())
    }

    @Test
    fun `estimate returns zero when db size is zero`() {
        val sizes = estimateMessageSizes(
            dbBytes = 0,
            messageCounts = mapOf(
                StorageType.CHAT to 100L,
                StorageType.IMAGE to 50L,
                StorageType.VIDEO to 50L
            )
        )

        assertEquals(0L, sizes.values.sum())
    }

    @Test
    fun `estimate assigns whole db to single type with all messages`() {
        val sizes = estimateMessageSizes(
            dbBytes = 777,
            messageCounts = mapOf(
                StorageType.CHAT to 0L,
                StorageType.IMAGE to 10L,
                StorageType.VIDEO to 0L
            )
        )

        assertEquals(0L, sizes[StorageType.CHAT])
        assertEquals(777L, sizes[StorageType.IMAGE])
        assertEquals(0L, sizes[StorageType.VIDEO])
    }

    @Test
    fun `estimate corrects rounding so sum equals db size`() {
        val sizes = estimateMessageSizes(
            dbBytes = 10,
            messageCounts = mapOf(
                StorageType.CHAT to 3L,
                StorageType.IMAGE to 3L,
                StorageType.VIDEO to 3L
            )
        )

        assertEquals(10L, sizes.values.sum())
    }

    @Test
    fun `formatBytes renders B KB MB GB`() {
        assertEquals("512 B", formatBytes(512))
        assertEquals("1.0 KB", formatBytes(1024))
        assertEquals("1.5 KB", formatBytes(1536))
        assertEquals("1.0 MB", formatBytes(1024 * 1024))
        assertEquals("2.00 GB", formatBytes(2L * 1024 * 1024 * 1024))
    }
}

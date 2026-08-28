package com.agnesai.chat.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageImagePathsTest {

    @Test
    fun `encode then parse round trip keeps paths`() {
        val paths = listOf("message_images/1/msg_100_0.jpg", "message_images/1/msg_100_1.jpg")
        val encoded = encodeImagePaths(paths)
        assertEquals("""["message_images/1/msg_100_0.jpg","message_images/1/msg_100_1.jpg"]""", encoded)
        assertEquals(paths, parseImagePaths(encoded))
    }

    @Test
    fun `parse null or blank returns empty list`() {
        assertTrue(parseImagePaths(null).isEmpty())
        assertTrue(parseImagePaths("").isEmpty())
        assertTrue(parseImagePaths("   ").isEmpty())
    }

    @Test
    fun `parse empty array returns empty list`() {
        assertTrue(parseImagePaths("[]").isEmpty())
    }

    @Test
    fun `parse malformed json returns empty list`() {
        assertTrue(parseImagePaths("not json").isEmpty())
        assertTrue(parseImagePaths("""["unclosed""").isEmpty())
    }

    @Test
    fun `parse single element array`() {
        assertEquals(listOf("message_images/3/msg_1_0.jpg"), parseImagePaths("""["message_images/3/msg_1_0.jpg"]"""))
    }

    @Test
    fun `encode empty list yields empty array`() {
        assertEquals("[]", encodeImagePaths(emptyList()))
        assertTrue(parseImagePaths(encodeImagePaths(emptyList())).isEmpty())
    }

    @Test
    fun `bytesToDataUri prefixes mime and base64`() {
        val bytes = byteArrayOf(1, 2, 3, 4)
        val uri = bytesToDataUri("image/jpeg", bytes)
        assertTrue(uri.startsWith("data:image/jpeg;base64,"))
        val payload = uri.removePrefix("data:image/jpeg;base64,")
        assertEquals("AQIDBA==", payload)
    }
}

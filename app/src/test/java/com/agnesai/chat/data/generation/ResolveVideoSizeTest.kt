package com.agnesai.chat.data.generation

import org.junit.Assert.assertEquals
import org.junit.Test

class ResolveVideoSizeTest {

    @Test
    fun `720P 9 to 16 resolves to 720x1280`() {
        assertEquals(720 to 1280, resolveVideoSize("720P", "9:16"))
    }

    @Test
    fun `1080P 9 to 16 resolves to 1080x1920`() {
        assertEquals(1080 to 1920, resolveVideoSize("1080P", "9:16"))
    }

    @Test
    fun `720P 16 to 9 resolves to 1280x720`() {
        assertEquals(1280 to 720, resolveVideoSize("720P", "16:9"))
    }

    @Test
    fun `1080P 16 to 9 resolves to 1920x1080`() {
        assertEquals(1920 to 1080, resolveVideoSize("1080P", "16:9"))
    }

    @Test
    fun `720P 1 to 1 resolves to 720x720`() {
        assertEquals(720 to 720, resolveVideoSize("720P", "1:1"))
    }

    @Test
    fun `1080P 1 to 1 resolves to 1080x1080`() {
        assertEquals(1080 to 1080, resolveVideoSize("1080P", "1:1"))
    }

    @Test
    fun `unknown ratio falls back to 16 to 9`() {
        assertEquals(1280 to 720, resolveVideoSize("720P", "3:2"))
    }
}

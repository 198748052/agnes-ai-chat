package com.agnesai.chat.data.generation

import com.agnesai.chat.data.network.VIDEO_MODEL_2_5_FLASH
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoV25RequestTest {

    // ========== videoV25Seconds ==========

    @Test
    fun `duration 4s maps to seconds 4`() {
        assertEquals("4", videoV25Seconds("4s"))
    }

    @Test
    fun `duration 5s maps to seconds 5`() {
        assertEquals("5", videoV25Seconds("5s"))
    }

    @Test
    fun `duration 8s maps to seconds 8`() {
        assertEquals("8", videoV25Seconds("8s"))
    }

    @Test
    fun `duration 10s maps to seconds 10`() {
        assertEquals("10", videoV25Seconds("10s"))
    }

    @Test
    fun `duration 12s maps to seconds 12`() {
        assertEquals("12", videoV25Seconds("12s"))
    }

    @Test
    fun `unknown duration falls back to seconds 5`() {
        assertEquals("5", videoV25Seconds("7s"))
        assertEquals("5", videoV25Seconds(""))
    }

    // ========== buildVideoV25Request ==========

    @Test
    fun `no frames uses text mode without frame fields`() {
        val request = buildVideoV25Request("一只猫", null, null, "5s", "16:9")

        assertEquals(VIDEO_MODEL_2_5_FLASH, request.model)
        assertEquals("text", request.mode)
        assertEquals("5", request.seconds)
        assertEquals("720P", request.size)
        assertEquals("16:9", request.aspectRatio)
        assertNull(request.firstFrame)
        assertNull(request.lastFrame)
    }

    @Test
    fun `first frame only uses keyframe mode`() {
        val request = buildVideoV25Request("一只猫", "data:image/jpeg;base64,AAA", null, "8s", "9:16")

        assertEquals("keyframe", request.mode)
        assertEquals("data:image/jpeg;base64,AAA", request.firstFrame)
        assertNull(request.lastFrame)
        assertEquals("8", request.seconds)
    }

    @Test
    fun `first and last frames use keyframe mode`() {
        val request = buildVideoV25Request(
            "一只猫",
            "data:image/jpeg;base64,AAA",
            "data:image/jpeg;base64,BBB",
            "12s",
            "1:1"
        )

        assertEquals("keyframe", request.mode)
        assertEquals("data:image/jpeg;base64,AAA", request.firstFrame)
        assertEquals("data:image/jpeg;base64,BBB", request.lastFrame)
        assertEquals("12", request.seconds)
    }

    @Test
    fun `last frame only uses keyframe mode`() {
        val request = buildVideoV25Request("一只猫", null, "data:image/jpeg;base64,BBB", "5s", "16:9")

        assertEquals("keyframe", request.mode)
        assertNull(request.firstFrame)
        assertEquals("data:image/jpeg;base64,BBB", request.lastFrame)
    }

    @Test
    fun `blank frames are treated as absent`() {
        val request = buildVideoV25Request("一只猫", "", "  ", "5s", "16:9")

        assertEquals("text", request.mode)
        assertNull(request.firstFrame)
        assertNull(request.lastFrame)
    }

    @Test
    fun `size is always fixed to 720P`() {
        val text = buildVideoV25Request("p", null, null, "5s", "16:9")
        val keyframe = buildVideoV25Request("p", "data:image/jpeg;base64,A", null, "5s", "16:9")

        assertEquals("720P", text.size)
        assertEquals("720P", keyframe.size)
    }
}

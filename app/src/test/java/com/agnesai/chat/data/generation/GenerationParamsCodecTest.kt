package com.agnesai.chat.data.generation

import com.agnesai.chat.data.local.SessionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerationParamsCodecTest {

    @Test
    fun `image params round-trip`() {
        val original = GenerationParams(
            type = SessionType.IMAGE,
            model = "agnes-image-2.1-flash",
            ratio = "1:1",
            referenceImages = listOf("data:image/png;base64,AAA", "data:image/png;base64,BBB")
        )

        val json = GenerationParamsCodec.encode(original)
        val decoded = GenerationParamsCodec.decode(json)

        assertEquals(SessionType.IMAGE, decoded?.type)
        assertEquals("agnes-image-2.1-flash", decoded?.model)
        assertEquals("1:1", decoded?.ratio)
        assertEquals(listOf("data:image/png;base64,AAA", "data:image/png;base64,BBB"), decoded?.referenceImages)
        assertNull(decoded?.duration)
        assertNull(decoded?.quality)
    }

    @Test
    fun `video params round-trip`() {
        val original = GenerationParams(
            type = SessionType.VIDEO,
            duration = "10s",
            quality = "1080P",
            ratio = "16:9",
            firstFrameImage = "data:image/jpeg;base64,CCC"
        )

        val json = GenerationParamsCodec.encode(original)
        val decoded = GenerationParamsCodec.decode(json)

        assertEquals(SessionType.VIDEO, decoded?.type)
        assertEquals("10s", decoded?.duration)
        assertEquals("1080P", decoded?.quality)
        assertEquals("16:9", decoded?.ratio)
        assertEquals("data:image/jpeg;base64,CCC", decoded?.firstFrameImage)
        assertTrue(decoded?.referenceImages.isNullOrEmpty())
    }

    @Test
    fun `decode null or invalid json returns null`() {
        assertNull(GenerationParamsCodec.decode(null))
        assertNull(GenerationParamsCodec.decode("not-json"))
        assertNull(GenerationParamsCodec.decode(""))
    }
}

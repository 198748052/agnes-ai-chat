package com.agnesai.chat.data.local

import com.agnesai.chat.data.network.MODEL_NAME
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatSettingsTest {

    @Test
    fun `default chat settings use default model and null params`() {
        val settings = ChatSettings()

        assertEquals(MODEL_NAME, settings.modelName)
        assertEquals(1f, settings.temperature)
        assertEquals(1f, settings.topP)
        assertNull(settings.maxTokens)
    }

    @Test
    fun `chat settings preserve explicit values`() {
        val settings = ChatSettings(
            modelName = "agnes-2.5-pro",
            temperature = 0.3f,
            topP = 0.9f,
            maxTokens = 512
        )

        assertEquals("agnes-2.5-pro", settings.modelName)
        assertEquals(0.3f, settings.temperature)
        assertEquals(0.9f, settings.topP)
        assertEquals(512, settings.maxTokens)
    }
}

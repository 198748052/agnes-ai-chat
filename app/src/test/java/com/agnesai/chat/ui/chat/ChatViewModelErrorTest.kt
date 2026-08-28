package com.agnesai.chat.ui.chat

import com.agnesai.chat.data.repository.HttpException
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class ChatViewModelErrorTest {

    private fun errorMessage(t: Throwable): String = t.toUserMessage()

    @Test
    fun `http 5xx maps to service unavailable message`() {
        assertEquals("服务暂时不可用，请稍后重试", errorMessage(HttpException(503)))
    }

    @Test
    fun `http 502 also maps to service unavailable message`() {
        assertEquals("服务暂时不可用，请稍后重试", errorMessage(HttpException(502)))
    }

    @Test
    fun `http 401 maps to api key message`() {
        assertEquals("API Key 无效，请检查设置", errorMessage(HttpException(401)))
    }

    @Test
    fun `http 429 maps to rate limit message`() {
        assertEquals("请求过于频繁，请稍后重试", errorMessage(HttpException(429)))
    }

    @Test
    fun `http error surfaces server detail when available`() {
        assertEquals(
            "请求失败：invalid request",
            errorMessage(HttpException(400, "{\"detail\":\"invalid request\"}"))
        )
    }

    @Test
    fun `http error falls back to code when no detail`() {
        assertEquals("请求失败 (HTTP 400)", errorMessage(HttpException(400, "")))
    }

    @Test
    fun `http error falls back to code when detail is invalid json`() {
        assertEquals("请求失败 (HTTP 400)", errorMessage(HttpException(400, "not-json")))
    }

    @Test
    fun `io exception maps to network message`() {
        assertEquals("网络连接失败，请检查网络", errorMessage(IOException("boom")))
    }
}

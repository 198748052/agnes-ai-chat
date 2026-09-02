package com.agnesai.chat.data.network

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class BaseUrlInterceptorTest {

    private lateinit var placeholderServer: MockWebServer
    private lateinit var targetServer: MockWebServer

    @Before
    fun setUp() {
        placeholderServer = MockWebServer()
        targetServer = MockWebServer()
        placeholderServer.start()
        targetServer.start()
    }

    @After
    fun tearDown() {
        placeholderServer.shutdown()
        targetServer.shutdown()
    }

    private fun newClient(baseUrlProvider: () -> String): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(BaseUrlInterceptor(baseUrlProvider))
            .readTimeout(5, TimeUnit.SECONDS)
            .build()

    @Test
    fun `request is rewritten to configured base url`() {
        targetServer.enqueue(MockResponse().setBody("{}"))
        val client = newClient { targetServer.url("/").toString() }

        val request = Request.Builder()
            .url(placeholderServer.url("/v1/chat/completions"))
            .build()
        client.newCall(request).execute().use { response ->
            assertEquals(200, response.code)
        }

        val recorded = checkNotNull(targetServer.takeRequest(5, TimeUnit.SECONDS))
        assertEquals("/v1/chat/completions", recorded.path)
    }

    @Test
    fun `path prefix of custom base url is preserved`() {
        targetServer.enqueue(MockResponse().setBody("{}"))
        val prefixedBase = targetServer.url("/openai/").toString()
        val client = newClient { prefixedBase }

        val request = Request.Builder()
            .url(placeholderServer.url("/v1/chat/completions"))
            .build()
        client.newCall(request).execute().use { response ->
            assertEquals(200, response.code)
        }

        val recorded = checkNotNull(targetServer.takeRequest(5, TimeUnit.SECONDS))
        assertEquals("/openai/v1/chat/completions", recorded.path)
    }

    @Test
    fun `query parameters are preserved after rewrite`() {
        targetServer.enqueue(MockResponse().setBody("{}"))
        val client = newClient { targetServer.url("/").toString() }

        val request = Request.Builder()
            .url(placeholderServer.url("/v1/models").newBuilder()
                .addQueryParameter("limit", "20").build())
            .build()
        client.newCall(request).execute().use { response ->
            assertEquals(200, response.code)
        }

        val recorded = checkNotNull(targetServer.takeRequest(5, TimeUnit.SECONDS))
        assertEquals("/v1/models?limit=20", recorded.path)
    }

    @Test
    fun `invalid base url leaves request untouched`() {
        placeholderServer.enqueue(MockResponse().setBody("{}"))
        val client = newClient { "not-a-url" }

        val request = Request.Builder()
            .url(placeholderServer.url("/v1/models"))
            .build()
        client.newCall(request).execute().use { response ->
            assertEquals(200, response.code)
        }

        val recorded = checkNotNull(placeholderServer.takeRequest(5, TimeUnit.SECONDS))
        assertEquals("/v1/models", recorded.path)
    }
}

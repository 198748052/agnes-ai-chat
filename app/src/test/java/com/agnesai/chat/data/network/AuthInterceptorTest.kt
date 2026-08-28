package com.agnesai.chat.data.network

import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET

class AuthInterceptorTest {

    private lateinit var server: MockWebServer
    private lateinit var interceptor: AuthInterceptor

    @Before
    fun setUp() {
        server = MockWebServer()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private interface DummyApi {
        @GET("ping")
        suspend fun ping(): retrofit2.Response<Unit>
    }

    private fun buildApi(tokenProvider: () -> String, onUnauthorized: () -> Unit): DummyApi {
        interceptor = AuthInterceptor(tokenProvider, onUnauthorized)
        val client = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .build()
        return Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(DummyApi::class.java)
    }

    @Test
    fun addsAuthorizationHeaderWhenTokenPresent() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val api = buildApi(tokenProvider = { "abc123" }, onUnauthorized = {})

        api.ping()
        val request = server.takeRequest()

        assertEquals("Bearer abc123", request.getHeader("Authorization"))
    }

    @Test
    fun noAuthorizationHeaderWithoutToken() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val api = buildApi(tokenProvider = { "" }, onUnauthorized = {})

        api.ping()
        val request = server.takeRequest()

        assertNull(request.getHeader("Authorization"))
    }

    @Test
    fun triggersOnUnauthorizedOn401() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        var unauthorized = false
        val api = buildApi(tokenProvider = { "abc123" }) { unauthorized = true }

        api.ping()

        assertTrue(unauthorized)
    }

    @Test
    fun doesNotTriggerOnUnauthorizedFor200() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        var unauthorized = false
        val api = buildApi(tokenProvider = { "abc123" }) { unauthorized = true }

        api.ping()

        assertFalse(unauthorized)
    }

    // 确保 Interceptor 可以同步读取 token（runBlocking 在 OkHttp 线程内工作）
    @Test
    fun interceptorCanReadTokenFromProvider() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        var readToken: String? = null
        val interceptor = AuthInterceptor(
            tokenProvider = {
                readToken = "read"
                "token-1"
            },
            onUnauthorized = {}
        )
        val client = OkHttpClient.Builder().addInterceptor(interceptor).build()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(DummyApi::class.java)

        api.ping()
        val request = server.takeRequest()

        assertEquals("Bearer token-1", request.getHeader("Authorization"))
        assertEquals("read", readToken)
    }
}

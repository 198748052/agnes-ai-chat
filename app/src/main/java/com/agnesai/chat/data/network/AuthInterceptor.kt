package com.agnesai.chat.data.network

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/**
 * 为业务服务器请求附加 `Authorization: Bearer <token>`。
 *
 * - 无 token 时（未登录）不附加请求头，避免污染登录/注册接口。
 * - 收到 401 响应时回调 [onUnauthorized]，触发全局登出。
 */
class AuthInterceptor(
    private val tokenProvider: () -> String,
    private val onUnauthorized: () -> Unit
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = runBlocking { tokenProvider() }
        val request = if (token.isBlank()) {
            chain.request()
        } else {
            chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        }
        val response = chain.proceed(request)
        if (response.code == 401) {
            onUnauthorized()
        }
        return response
    }
}

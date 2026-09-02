package com.agnesai.chat.data.network

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response

/** API 地址校验失败时抛出，[reason] 为用户可读的提示文案 */
class BaseUrlException(val reason: String) : Exception(reason)

/** API Base URL 校验与规范化。纯函数，无 Android 依赖。 */
object BaseUrlValidator {

    /**
     * 校验并规范化用户输入的 API 地址。
     *
     * @return 规范化后的地址；输入为空白时返回 null（表示回退默认地址）
     * @throws BaseUrlException 地址格式非法时抛出
     */
    fun normalize(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        val hasScheme = trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        if (!hasScheme) {
            throw BaseUrlException("地址需以 http:// 或 https:// 开头")
        }
        val url = trimmed.toHttpUrlOrNull()
        if (url == null || url.host.isBlank()) {
            throw BaseUrlException("地址格式不正确")
        }
        val normalized = url.toString()
        // 末尾斜杠保证相对路径 resolve 时保留路径前缀（如 https://proxy.example.com/openai/）
        return if (normalized.endsWith("/")) normalized else "$normalized/"
    }
}

/**
 * 每次请求时用 [baseUrlProvider] 返回的最新地址改写请求目标；
 * 保留原请求相对路径与 query，使带路径前缀的自定义地址同样生效。
 */
class BaseUrlInterceptor(private val baseUrlProvider: () -> String) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val base = baseUrlProvider().toHttpUrlOrNull()
        val rewritten = base?.resolve(request.url.encodedPath.removePrefix("/"))
        if (rewritten != null) {
            val newUrl = request.url.newBuilder()
                .scheme(rewritten.scheme)
                .host(rewritten.host)
                .port(rewritten.port)
                .encodedPath(rewritten.encodedPath)
                .build()
            return chain.proceed(request.newBuilder().url(newUrl).build())
        }
        return chain.proceed(request)
    }
}

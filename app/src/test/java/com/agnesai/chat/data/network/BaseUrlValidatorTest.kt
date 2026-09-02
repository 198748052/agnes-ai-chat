package com.agnesai.chat.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class BaseUrlValidatorTest {

    @Test
    fun `blank input returns null to fall back to default`() {
        assertNull(BaseUrlValidator.normalize(""))
        assertNull(BaseUrlValidator.normalize("   "))
    }

    @Test
    fun `missing scheme is rejected`() {
        assertRejected("api.example.com", "地址需以 http:// 或 https:// 开头")
        assertRejected("www.example.com/v1/", "地址需以 http:// 或 https:// 开头")
        assertRejected("ftp://example.com", "地址需以 http:// 或 https:// 开头")
    }

    @Test
    fun `unparsable url is rejected`() {
        assertRejected("https://", "地址格式不正确")
        assertRejected("http:// bad host", "地址格式不正确")
        assertRejected("https://exa mple.com", "地址格式不正确")
    }

    @Test
    fun `valid url without path gets trailing slash`() {
        assertEquals("https://api.example.com/", BaseUrlValidator.normalize("https://api.example.com"))
    }

    @Test
    fun `valid url with path keeps trailing slash`() {
        assertEquals(
            "https://proxy.example.com/openai/",
            BaseUrlValidator.normalize("https://proxy.example.com/openai/")
        )
        assertEquals(
            "https://proxy.example.com/openai/",
            BaseUrlValidator.normalize("https://proxy.example.com/openai")
        )
    }

    @Test
    fun `http scheme is accepted`() {
        assertEquals("http://10.0.0.2:8080/", BaseUrlValidator.normalize("http://10.0.0.2:8080"))
    }

    @Test
    fun `scheme matching is case insensitive`() {
        assertEquals("https://api.example.com/", BaseUrlValidator.normalize("HTTPS://api.example.com"))
        assertEquals("http://api.example.com/", BaseUrlValidator.normalize("Http://api.example.com"))
    }

    @Test
    fun `input is trimmed before validation`() {
        assertEquals("https://api.example.com/", BaseUrlValidator.normalize("  https://api.example.com  "))
    }

    private fun assertRejected(input: String, expectedReason: String) {
        try {
            BaseUrlValidator.normalize(input)
            fail("expected BaseUrlException for input: $input")
        } catch (e: BaseUrlException) {
            assertEquals(expectedReason, e.reason)
        }
    }
}

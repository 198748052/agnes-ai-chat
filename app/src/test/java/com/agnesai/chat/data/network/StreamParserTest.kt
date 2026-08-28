package com.agnesai.chat.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamParserTest {

    @Test
    fun `parses content delta from SSE line`() {
        val line = """data: {"choices":[{"delta":{"content":"Hello"}}]}"""
        assertEquals("Hello", StreamParser.parseLine(line))
    }

    @Test
    fun `returns null for done marker`() {
        assertNull(StreamParser.parseLine("data: [DONE]"))
    }

    @Test
    fun `returns null for non-data line`() {
        assertNull(StreamParser.parseLine("event: message"))
    }

    @Test
    fun `returns null for empty data`() {
        assertNull(StreamParser.parseLine("data: "))
    }

    @Test
    fun `returns null for empty content`() {
        val line = """data: {"choices":[{"delta":{"content":""}}]}"""
        assertNull(StreamParser.parseLine(line))
    }

    @Test
    fun `handles malformed json gracefully`() {
        assertNull(StreamParser.parseLine("data: {not-json"))
    }

    @Test
    fun `parses multi-byte text`() {
        val line = """data: {"choices":[{"delta":{"content":"你好"}}]}"""
        assertEquals("你好", StreamParser.parseLine(line))
    }
}

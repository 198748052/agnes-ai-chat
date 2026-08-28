package com.agnesai.chat.data.network

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

/**
 * Parses Server-Sent-Events (SSE) lines produced by the OpenAI-compatible
 * Chat Completions streaming endpoint. Returns incremental text deltas.
 */
object StreamParser {

    private const val DONE_MARKER = "[DONE]"
    private const val DATA_PREFIX = "data:"

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    private val chunkAdapter = moshi.adapter(ChatChunkResponse::class.java)

    /** Returns the incremental content delta from an SSE line, or null if the line carries no text. */
    fun parseLine(line: String): String? {
        val trimmed = line.trim()
        if (!trimmed.startsWith(DATA_PREFIX)) return null
        val payload = trimmed.removePrefix(DATA_PREFIX).trim()
        if (payload.isEmpty() || payload == DONE_MARKER) return null
        return try {
            val chunk = chunkAdapter.fromJson(payload) ?: return null
            chunk.choices.firstOrNull()?.delta?.content?.takeIf(String::isNotEmpty)
        } catch (_: Exception) {
            null
        }
    }
}

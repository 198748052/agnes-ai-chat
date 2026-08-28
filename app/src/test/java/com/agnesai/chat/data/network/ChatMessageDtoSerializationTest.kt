package com.agnesai.chat.data.network

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatMessageDtoSerializationTest {

    private val moshi = Moshi.Builder()
        .add(ChatMessageDto::class.java, ChatMessageDtoAdapter())
        .add(KotlinJsonAdapterFactory())
        .build()

    private val adapter = moshi.adapter(ChatMessageDto::class.java)

    @Test
    fun `text only message serializes as string content`() {
        val json = adapter.toJson(ChatMessageDto("user", "你好"))
        assertEquals("""{"role":"user","content":"你好"}""", json)
    }

    @Test
    fun `text plus images serializes as content array`() {
        val dto = ChatMessageDto(
            role = "user",
            content = "看图",
            imageUrls = listOf("data:image/jpeg;base64,AAA", "data:image/jpeg;base64,BBB")
        )
        val json = adapter.toJson(dto)
        assertEquals(
            """{"role":"user","content":""" +
                """[{"type":"text","text":"看图"},""" +
                """{"type":"image_url","image_url":{"url":"data:image/jpeg;base64,AAA"}},""" +
                """{"type":"image_url","image_url":{"url":"data:image/jpeg;base64,BBB"}}]}""",
            json
        )
    }

    @Test
    fun `empty image list keeps string content`() {
        val dto = ChatMessageDto("assistant", "回复", imageUrls = emptyList())
        val json = adapter.toJson(dto)
        assertEquals("""{"role":"assistant","content":"回复"}""", json)
    }

    @Test
    fun `messages list serializes with mixed formats`() {
        val request = ChatCompletionRequest(
            model = "agnes-2.5-flash",
            messages = listOf(
                ChatMessageDto("system", "system prompt"),
                ChatMessageDto("user", "带图", listOf("data:image/jpeg;base64,ZZZ"))
            )
        )
        val requestAdapter = moshi.adapter(ChatCompletionRequest::class.java)
        val json = requestAdapter.toJson(request)
        assertEquals(
            """{"model":"agnes-2.5-flash","messages":""" +
                """[{"role":"system","content":"system prompt"},""" +
                """{"role":"user","content":[{"type":"text","text":"带图"},""" +
                """{"type":"image_url","image_url":{"url":"data:image/jpeg;base64,ZZZ"}}]}],""" +
                """"stream":true}""",
            json
        )
    }
}

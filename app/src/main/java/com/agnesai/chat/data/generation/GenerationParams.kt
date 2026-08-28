package com.agnesai.chat.data.generation

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

/** 生成消息携带的参数（持久化到 MessageEntity.params）。 */
data class GenerationParams(
    val type: String,
    val model: String? = null,
    val ratio: String? = null,
    val referenceImages: List<String> = emptyList(),
    val duration: String? = null,
    val quality: String? = null,
    val firstFrameImage: String? = null,
    val lastFrameImage: String? = null
)

object GenerationParamsCodec {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    private val adapter = moshi.adapter(GenerationParams::class.java)

    fun encode(params: GenerationParams): String = adapter.toJson(params)

    fun decode(json: String?): GenerationParams? =
        json?.let { runCatching { adapter.fromJson(it) }.getOrNull() }
}

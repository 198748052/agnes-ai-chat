package com.agnesai.chat.data.network

import com.squareup.moshi.Json

// 图片生成模型常量
const val IMAGE_MODEL_2_0 = "agnes-image-2.0-flash"
const val IMAGE_MODEL_2_1 = "agnes-image-2.1-flash"
const val IMAGE_MODEL_2_5 = "agnes-image-2.5-flash"
const val VIDEO_MODEL = "agnes-video-v2.0"
const val VIDEO_MODEL_2_5_FLASH = "agnes-video-2.5-flash"

// ---------- 图片生成 ----------

data class ImageGenerationRequest(
    val model: String,
    val prompt: String,
    val size: String? = null,
    val ratio: String? = null,
    @Json(name = "return_base64") val returnBase64: Boolean? = null,
    @Json(name = "extra_body") val extraBody: ImageGenerationExtraBody? = null
)

data class ImageGenerationExtraBody(
    val image: List<String>? = null,
    @Json(name = "response_format") val responseFormat: String? = null
)

data class ImageGenerationResponse(
    val created: Long? = null,
    val data: List<ImageGenerationItem> = emptyList()
)

data class ImageGenerationItem(
    val url: String? = null,
    @Json(name = "b64_json") val b64Json: String? = null,
    @Json(name = "revised_prompt") val revisedPrompt: String? = null
)

// ---------- 视频生成（异步任务） ----------

data class VideoCreateRequest(
    val model: String,
    val prompt: String,
    val image: String? = null,
    val height: Int? = null,
    val width: Int? = null,
    @Json(name = "num_frames") val numFrames: Int? = null,
    @Json(name = "frame_rate") val frameRate: Int? = null,
    @Json(name = "negative_prompt") val negativePrompt: String? = null,
    @Json(name = "extra_body") val extraBody: VideoExtraBody? = null
)

/**
 * Agnes Video 2.5 Flash 创建请求：
 * mode=text（纯文生视频）/ keyframe（首尾帧控制）；size 固定 720P。
 */
data class VideoV25CreateRequest(
    val model: String,
    val prompt: String,
    val mode: String,
    val seconds: String? = null,
    val size: String? = null,
    @Json(name = "aspect_ratio") val aspectRatio: String? = null,
    @Json(name = "first_frame") val firstFrame: String? = null,
    @Json(name = "last_frame") val lastFrame: String? = null
)

data class VideoExtraBody(
    val image: List<String>? = null,
    val mode: String? = null
)

data class VideoCreateResponse(
    val id: String? = null,
    @Json(name = "task_id") val taskId: String? = null,
    @Json(name = "video_id") val videoId: String? = null,
    val model: String? = null,
    val status: String? = null,
    val progress: Int? = null
)

data class VideoResultResponse(
    val id: String? = null,
    @Json(name = "task_id") val taskId: String? = null,
    @Json(name = "video_id") val videoId: String? = null,
    val model: String? = null,
    val status: String? = null,
    val progress: Int? = null,
    val url: String? = null,
    val metadata: VideoResultMetadata? = null,
    val error: VideoResultError? = null
)

data class VideoResultError(
    val code: String? = null,
    val message: String? = null,
    val type: String? = null
)

data class VideoResultMetadata(
    val url: String? = null
)

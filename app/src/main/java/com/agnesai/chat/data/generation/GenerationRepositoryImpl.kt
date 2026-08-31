package com.agnesai.chat.data.generation

import com.agnesai.chat.data.network.AgnesGenerationApiService
import com.agnesai.chat.data.network.IMAGE_MODEL_2_1
import com.agnesai.chat.data.network.ImageGenerationExtraBody
import com.agnesai.chat.data.network.ImageGenerationRequest
import com.agnesai.chat.data.network.VIDEO_MODEL
import com.agnesai.chat.data.network.VIDEO_MODEL_2_5_FLASH
import com.agnesai.chat.data.network.VideoCreateRequest
import com.agnesai.chat.data.network.VideoExtraBody
import com.agnesai.chat.data.network.VideoV25CreateRequest
import kotlinx.coroutines.delay
import retrofit2.Response

class GenerationRepositoryImpl(
    private val apiService: AgnesGenerationApiService,
    private val apiKeyProvider: suspend () -> String
) : GenerationRepository {

    override suspend fun generateImage(
        prompt: String,
        model: String,
        size: String?,
        ratio: String?,
        referenceImages: List<String>
    ): Result<String> = runCatching {
        require(prompt.isNotBlank()) { "请输入生成描述" }

        val request = ImageGenerationRequest(
            model = model,
            prompt = prompt,
            size = size,
            ratio = ratio,
            extraBody = ImageGenerationExtraBody(
                image = referenceImages.ifEmpty { null },
                responseFormat = "url"
            )
        )

        val body = requestWithRetry(
            request = { apiService.generateImage("Bearer ${apiKeyProvider()}", request) },
            context = "图片"
        )
        val url = body?.data?.firstOrNull()?.url
        require(!url.isNullOrBlank()) { "图片生成未返回结果，请重试" }
        url
    }

    override suspend fun generateVideo(
        prompt: String,
        model: String,
        firstFrameImage: String?,
        lastFrameImage: String?,
        duration: String,
        quality: String,
        ratio: String
    ): Result<String> = runCatching {
        require(prompt.isNotBlank()) { "请输入生成描述" }

        if (model == VIDEO_MODEL_2_5_FLASH) {
            generateVideoV25(prompt, firstFrameImage, lastFrameImage, duration, ratio)
        } else {
            generateVideoV20(prompt, firstFrameImage, lastFrameImage, duration, quality, ratio)
        }
    }

    /** Agnes Video 2.0：height/width/num_frames 旧参数结构 + transition 模式。 */
    private suspend fun generateVideoV20(
        prompt: String,
        firstFrameImage: String?,
        lastFrameImage: String?,
        duration: String,
        quality: String,
        ratio: String
    ): String {
        val (width, height) = resolveVideoSize(quality, ratio)
        // num_frames 必须满足 8n + 1（官方推荐：5s=121、10s=241，帧率 24）
        val numFrames = when (duration) {
            "10s" -> 241
            else -> 121
        }
        val frameImages = listOfNotNull(firstFrameImage, lastFrameImage)

        val createRequest = VideoCreateRequest(
            model = VIDEO_MODEL,
            prompt = prompt,
            image = frameImages.firstOrNull(),
            height = height,
            width = width,
            numFrames = numFrames,
            frameRate = 24,
            extraBody = VideoExtraBody(
                image = if (frameImages.size > 1) frameImages else null,
                mode = if (frameImages.size > 1) "transition" else null
            )
        )

        val createBody = requestWithRetry(
            request = { apiService.createVideo("Bearer ${apiKeyProvider()}", createRequest) },
            context = "视频"
        )
        val videoId = createBody?.videoId ?: createBody?.taskId
        require(!videoId.isNullOrBlank()) { "视频任务创建未返回任务 ID" }

        return pollVideoResult(videoId, VIDEO_MODEL)
    }

    /** Agnes Video 2.5 Flash：mode/seconds/size/aspect_ratio 新参数结构，size 固定 720P。 */
    private suspend fun generateVideoV25(
        prompt: String,
        firstFrameImage: String?,
        lastFrameImage: String?,
        duration: String,
        ratio: String
    ): String {
        val createRequest = buildVideoV25Request(prompt, firstFrameImage, lastFrameImage, duration, ratio)

        val createBody = requestWithRetry(
            request = { apiService.createVideoV25("Bearer ${apiKeyProvider()}", createRequest) },
            context = "视频"
        )
        val videoId = createBody?.videoId ?: createBody?.taskId
        require(!videoId.isNullOrBlank()) { "视频任务创建未返回任务 ID" }

        return pollVideoResult(videoId, VIDEO_MODEL_2_5_FLASH)
    }

    /**
     * 通用请求重试：对瞬时服务错误（502/503/504）短暂等待后重试，
     * 对限流（429）等待后重试；尝试多次仍失败时给出友好提示。
     */
    private suspend fun <T> requestWithRetry(
        request: suspend () -> Response<T>,
        context: String
    ): T {
        var lastCode = 0
        var lastMessage = ""
        for (attempt in 0 until 3) {
            val response = request()
            if (response.isSuccessful) return response.body() ?: error("${context}生成未返回结果，请重试")
            lastCode = response.code()
            lastMessage = response.message() ?: ""
            val retriable = lastCode in setOf(502, 503, 504)
            val throttled = lastCode == 429
            if (attempt < 2 && (retriable || throttled)) {
                // 限流等待 30s，其余瞬时错误等待 4s 再重试
                delay(if (throttled) 30_000L else 4_000L)
            } else {
                break
            }
        }
        error(describeError(context, lastCode, lastMessage))
    }

    private fun describeError(context: String, code: Int, message: String): String = when (code) {
        502, 503, 504 -> "${context}服务暂时不可用，请稍后重试或切换模型"
        429 -> "请求过于频繁，请等待一分钟后再试"
        else -> "${context}生成失败（$code）：$message"
    }

    /** 轮询视频生成结果，直到完成、失败或超时。keyframe 等模式查询必须携带 model_name。 */
    private suspend fun pollVideoResult(videoId: String, modelName: String): String {
        val maxAttempts = 60
        // 官方建议轮询间隔至少 10 秒（视频推理耗时较长）
        val pollIntervalMs = 10_000L
        var lastError: String? = null

        repeat(maxAttempts) {
            delay(pollIntervalMs)
            val response = apiService.getVideoResult("Bearer ${apiKeyProvider()}", videoId, modelName)
            if (!response.isSuccessful) {
                // 记录最近一次失败原因，避免静默重试到超时
                lastError = "视频查询失败（HTTP ${response.code()}）：${response.message()}"
                return@repeat
            }

            val body = response.body() ?: return@repeat
            when (body.status) {
                "completed" -> {
                    val url = body.url ?: body.metadata?.url
                    require(!url.isNullOrBlank()) { "视频生成完成但未返回地址" }
                    return url
                }
                "failed" -> {
                    val detail = buildList {
                        body.error?.let { e ->
                            e.message?.takeIf(String::isNotBlank)?.let(::add)
                            e.type?.takeIf(String::isNotBlank)?.let { add("type: $it") }
                            e.code?.takeIf(String::isNotBlank)?.let { add("code: $it") }
                        }
                    }.joinToString("；")
                    error(if (detail.isBlank()) "视频生成失败，请重试" else "视频生成失败：$detail")
                }
                else -> Unit // queued / in_progress，继续轮询
            }
        }
        error(lastError ?: "视频生成超时，请稍后重试")
    }
}

/**
 * 根据清晰度与画面比例计算视频分辨率。
 * 720P 基准 720，1080P 基准 1080；横屏/竖屏分别以对应边为基准计算标准分辨率。
 */
internal fun resolveVideoSize(quality: String, ratio: String): Pair<Int, Int> {
    val base = if (quality == "1080P") 1080 else 720
    return when (ratio) {
        "9:16" -> base to base * 16 / 9
        "1:1" -> base to base
        else -> base * 16 / 9 to base
    }
}

/** 将 UI 时长档位（如 "5s"）映射为 2.5 Flash 的 seconds 字符串（"4"–"12"），未知值回退默认 "5"。 */
internal fun videoV25Seconds(duration: String): String = when (duration) {
    "4s" -> "4"
    "8s" -> "8"
    "10s" -> "10"
    "12s" -> "12"
    else -> "5"
}

/**
 * 构建 Agnes Video 2.5 Flash 创建请求：
 * 有首/尾帧时使用 keyframe 模式，否则使用 text 模式；size 固定 720P。
 */
internal fun buildVideoV25Request(
    prompt: String,
    firstFrameImage: String?,
    lastFrameImage: String?,
    duration: String,
    ratio: String
): VideoV25CreateRequest {
    val first = firstFrameImage?.takeIf(String::isNotBlank)
    val last = lastFrameImage?.takeIf(String::isNotBlank)
    return VideoV25CreateRequest(
        model = VIDEO_MODEL_2_5_FLASH,
        prompt = prompt,
        mode = if (first != null || last != null) "keyframe" else "text",
        seconds = videoV25Seconds(duration),
        size = "720P",
        aspectRatio = ratio,
        firstFrame = first,
        lastFrame = last
    )
}

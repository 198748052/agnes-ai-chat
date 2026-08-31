package com.agnesai.chat.ui.generation

import com.agnesai.chat.data.network.IMAGE_MODEL_2_5
import com.agnesai.chat.data.network.VIDEO_MODEL_2_5_FLASH

/** 图片生成面板（表单式）状态 */
data class ImageGenState(
    val prompt: String = "",
    /** 图片模型：agnes-image-2.5-flash / agnes-image-2.1-flash / agnes-image-2.0-flash */
    val imageModel: String = IMAGE_MODEL_2_5,
    /** 图生图/多图合成输入的参考图（Data URI Base64 列表，最多 6 张） */
    val referenceImages: List<String> = emptyList(),
    /** 生成比例：1:1 / 16:9 / 9:16 */
    val ratio: String = "1:1",
    val isGenerating: Boolean = false,
    /** 正在生成的任务所属会话 id 集合（跨会话后台生成时，非当前会话不展示加载态） */
    val generatingSessionIds: Set<Long> = emptySet(),
    /** 最近一次生成结果图片 URL */
    val result: String? = null,
    val error: String? = null
)

/** 视频生成面板（表单式）状态 */
data class VideoGenState(
    val prompt: String = "",
    /** 视频模型：agnes-video-2.5-flash / agnes-video-v2.0 */
    val videoModel: String = VIDEO_MODEL_2_5_FLASH,
    /** 首帧图（Data URI Base64，可选） */
    val firstFrameImage: String? = null,
    /** 尾帧图（Data URI Base64，可选） */
    val lastFrameImage: String? = null,
    /** 视频时长：5s / 10s */
    val duration: String = "5s",
    /** 清晰度：720P / 1080P */
    val quality: String = "720P",
    /** 画面比例：16:9 / 9:16 / 1:1 */
    val ratio: String = "16:9",
    val isGenerating: Boolean = false,
    /** 正在生成的任务所属会话 id 集合（跨会话后台生成时，非当前会话不展示加载态） */
    val generatingSessionIds: Set<Long> = emptySet(),
    /** 最近一次生成结果视频 URL */
    val result: String? = null,
    val error: String? = null
)

data class GenerationUiState(
    val image: ImageGenState = ImageGenState(),
    val video: VideoGenState = VideoGenState(),
    /** 最近一次发送的图片提示词（供再次生成复用） */
    val lastImagePrompt: String = "",
    /** 最近一次发送的视频提示词（供再次生成复用） */
    val lastVideoPrompt: String = ""
)

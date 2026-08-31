package com.agnesai.chat.data.generation

/**
 * AI 图片/视频生成仓库接口。
 */
interface GenerationRepository {

    /**
     * 生成图片（同步返回结果 URL）。
     *
     * @param prompt          提示词
     * @param model           图片模型（agnes-image-2.0-flash / agnes-image-2.1-flash）
     * @param size            输出尺寸档位（如 1K/2K/1024x1024），空则使用默认
     * @param ratio           宽高比（如 1:1、16:9），空则使用默认
     * @param referenceImages 图生图/多图合成的输入图（Data URI Base64 列表），空表示文生图
     * @return 生成图片的 URL
     */
    suspend fun generateImage(
        prompt: String,
        model: String,
        size: String?,
        ratio: String?,
        referenceImages: List<String>
    ): Result<String>

    /**
     * 生成视频（异步任务，内部轮询直到完成或失败）。
     *
     * @param prompt          提示词
     * @param model           视频模型（agnes-video-v2.0 / agnes-video-2.5-flash）
     * @param firstFrameImage 图生视频的首帧图（Data URI Base64），空表示文生视频
     * @param lastFrameImage  尾帧图（Data URI Base64，可选，与首帧共同指定时生成首尾帧过渡）
     * @param duration        视频时长（如 5s / 10s）
     * @param quality         清晰度（如 720P / 1080P）
     * @param ratio           画面比例（如 16:9 / 9:16 / 1:1）
     * @return 生成视频的 URL
     */
    suspend fun generateVideo(
        prompt: String,
        model: String,
        firstFrameImage: String?,
        lastFrameImage: String?,
        duration: String,
        quality: String,
        ratio: String
    ): Result<String>
}

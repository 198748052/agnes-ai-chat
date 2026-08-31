package com.agnesai.chat.data.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Agnes AI 图片/视频生成 API。
 *
 * 图片生成是同步请求：POST /v1/images/generations。
 * 视频生成是异步任务：POST /v1/videos 创建任务，再用 video_id 轮询结果。
 */
interface AgnesGenerationApiService {

    @POST("v1/images/generations")
    suspend fun generateImage(
        @Header("Authorization") authorization: String,
        @Body request: ImageGenerationRequest
    ): Response<ImageGenerationResponse>

    @POST("v1/videos")
    suspend fun createVideo(
        @Header("Authorization") authorization: String,
        @Body request: VideoCreateRequest
    ): Response<VideoCreateResponse>

    // Agnes Video 2.5 Flash 复用同一端点，请求体为新一代参数结构
    @POST("v1/videos")
    suspend fun createVideoV25(
        @Header("Authorization") authorization: String,
        @Body request: VideoV25CreateRequest
    ): Response<VideoCreateResponse>

    // 官方文档推荐方式：GET /agnesapi?video_id=<VIDEO_ID>&model_name=<MODEL>
    // keyframe / reference 模式的任务查询必须携带 model_name
    @GET("agnesapi")
    suspend fun getVideoResult(
        @Header("Authorization") authorization: String,
        @Query("video_id") videoId: String,
        @Query("model_name") modelName: String
    ): Response<VideoResultResponse>
}

package com.agnesai.chat.data.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT

/**
 * 业务服务器接口定义。
 *
 * 后续对接真实服务器时：
 * 1. 将 [ServerApiService] 注册到独立 baseUrl 的 Retrofit 实例（见 AppContainer 中 TODO）
 * 2. 各 Repository 注入该实例并替换占位实现
 */
interface ServerApiService {

    @POST("api/v1/auth/login")
    suspend fun login(@Body request: LoginRequestDto): Response<LoginResponseDto>

    @POST("api/v1/auth/register")
    suspend fun register(@Body request: RegisterRequestDto): Response<UserDto>

    @POST("api/v1/auth/logout")
    suspend fun logout(): Response<Unit>

    @GET("api/v1/announcements/latest")
    suspend fun getLatestAnnouncement(): Response<AnnouncementDto>

    @GET("api/v1/app/version")
    suspend fun getAppVersion(): Response<UpdateInfoDto>

    @POST("api/v1/generation/image")
    suspend fun generateImage(@Body request: GenerationRequestDto): Response<GenerationResponseDto>

    @POST("api/v1/generation/video")
    suspend fun generateVideo(@Body request: GenerationRequestDto): Response<GenerationResponseDto>

    @PUT("api/v1/user/profile")
    suspend fun updateProfile(@Body request: UpdateProfileRequestDto): Response<UserDto>

    @POST("api/v1/user/password")
    suspend fun changePassword(@Body request: ChangePasswordRequestDto): Response<Unit>

    @POST("api/v1/user/avatar")
    suspend fun uploadAvatar(@Body request: UploadAvatarRequestDto): Response<AvatarResponseDto>

    @GET("api/v1/user/stats")
    suspend fun getStats(): Response<UserStatsDto>
}

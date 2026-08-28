package com.agnesai.chat.data.network

import com.squareup.moshi.Json

// 登录相关 DTO
data class LoginRequestDto(
    val username: String,
    val password: String
)

data class RegisterRequestDto(
    val username: String,
    val password: String
)

data class LoginResponseDto(
    val token: String,
    val user: UserDto
)

// 服务端统一错误响应（FastAPI {"detail": "..."} 或校验失败 {"detail": "invalid request"}）
data class ApiErrorDto(
    val detail: String
)

data class UserDto(
    val id: String,
    val username: String,
    val nickname: String,
    @Json(name = "avatar_url") val avatarUrl: String? = null
)

// 个人资料相关 DTO
data class UpdateProfileRequestDto(
    val nickname: String
)

data class ChangePasswordRequestDto(
    @Json(name = "old_password") val oldPassword: String,
    @Json(name = "new_password") val newPassword: String
)

data class UploadAvatarRequestDto(
    @Json(name = "avatar_base64") val avatarBase64: String
)

data class AvatarResponseDto(
    @Json(name = "avatar_url") val avatarUrl: String
)

// 生成统计相关 DTO
data class PeriodCountsDto(
    val today: Int = 0,
    val week: Int = 0,
    val month: Int = 0,
    val total: Int = 0
)

data class UserStatsDto(
    val image: PeriodCountsDto = PeriodCountsDto(),
    val video: PeriodCountsDto = PeriodCountsDto()
)

// 公告相关 DTO
data class AnnouncementDto(
    val id: String,
    val title: String,
    val content: String,
    val priority: String = "normal",
    @Json(name = "publish_at") val publishAt: Long = 0L
)

// 更新相关 DTO
data class UpdateInfoDto(
    @Json(name = "latest_version_code") val latestVersionCode: Int,
    @Json(name = "latest_version_name") val latestVersionName: String,
    @Json(name = "force_update") val forceUpdate: Boolean,
    @Json(name = "update_log") val updateLog: String,
    @Json(name = "download_url") val downloadUrl: String
)

// AI 生成相关 DTO
data class GenerationRequestDto(
    val prompt: String,
    val mode: String
)

data class GenerationResponseDto(
    @Json(name = "task_id") val taskId: String,
    @Json(name = "media_url") val mediaUrl: String? = null
)

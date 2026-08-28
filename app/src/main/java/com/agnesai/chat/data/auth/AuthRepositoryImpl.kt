package com.agnesai.chat.data.auth

import com.agnesai.chat.data.network.ChangePasswordRequestDto
import com.agnesai.chat.data.network.ServerApiService
import com.agnesai.chat.data.network.UpdateProfileRequestDto
import com.agnesai.chat.data.network.UploadAvatarRequestDto
import com.agnesai.chat.data.repository.extractServerDetail
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.io.IOException

/**
 * 认证仓库实现。
 *
 * - 账号为内置超级账号 `admin` 时，完全本地校验（不触发网络请求），断网可用。
 * - 其余账号通过 [ServerApiService] 在云服务器校验，登录成功保存返回的 JWT 与用户信息。
 */
class AuthRepositoryImpl(
    private val authStorage: AuthStorage,
    private val serverApiService: ServerApiService
) : AuthRepository {

    override val authState: Flow<AuthState> = authStorage.authState

    override suspend fun login(username: String, password: String): Result<Unit> = runCatching {
        require(username.isNotBlank()) { "请输入账号" }
        require(password.isNotBlank()) { "请输入密码" }

        if (username == SUPER_ADMIN_USERNAME) {
            require(password == SUPER_ADMIN_PASSWORD) { "超级账号密码错误" }
            authStorage.saveAuth(
                token = "admin-local-token",
                user = UserInfo(
                    id = "admin",
                    username = SUPER_ADMIN_USERNAME,
                    nickname = "管理员"
                )
            )
        } else {
            val response = serverApiService.login(
                com.agnesai.chat.data.network.LoginRequestDto(username, password)
            )
            if (!response.isSuccessful) {
                throw IOException(errorMessageFrom("登录", response.code(), response.errorBody()?.string().orEmpty()))
            }
            val body = response.body() ?: throw IOException("登录失败，请稍后重试")
            authStorage.saveAuth(
                token = body.token,
                user = UserInfo(
                    id = body.user.id,
                    username = body.user.username,
                    nickname = body.user.nickname
                )
            )
        }
    }

    override suspend fun register(username: String, password: String): Result<Unit> = runCatching {
        require(username.isNotBlank()) { "请输入账号" }
        require(password.length >= MIN_PASSWORD_LENGTH) { "密码至少 ${MIN_PASSWORD_LENGTH} 位" }
        require(username != SUPER_ADMIN_USERNAME) { "该账号已存在" }

        val response = serverApiService.register(
            com.agnesai.chat.data.network.RegisterRequestDto(username, password)
        )
        if (!response.isSuccessful) {
            throw IOException(errorMessageFrom("注册", response.code(), response.errorBody()?.string().orEmpty()))
        }
    }

    override suspend fun logout() {
        authStorage.clearAuth()
    }

    override suspend fun updateProfile(nickname: String): Result<Unit> = runCatching {
        require(nickname.isNotBlank()) { "昵称不能为空" }
        require(nickname.length <= MAX_NICKNAME_LENGTH) { "昵称最长 $MAX_NICKNAME_LENGTH 字" }
        if (currentUsername() == SUPER_ADMIN_USERNAME) {
            authStorage.updateProfile(nickname, null)
            return@runCatching
        }
        val response = serverApiService.updateProfile(UpdateProfileRequestDto(nickname))
        if (!response.isSuccessful) {
            throw IOException(errorMessageFrom("修改昵称", response.code(), response.errorBody()?.string().orEmpty()))
        }
        val body = response.body() ?: throw IOException("修改昵称失败，请稍后重试")
        authStorage.updateProfile(body.nickname, body.avatarUrl)
    }

    override suspend fun changePassword(oldPassword: String, newPassword: String): Result<Unit> =
        runCatching {
            require(oldPassword.isNotBlank()) { "请输入旧密码" }
            require(newPassword.length >= MIN_PASSWORD_LENGTH) { "新密码至少 ${MIN_PASSWORD_LENGTH} 位" }
            require(currentUsername() != SUPER_ADMIN_USERNAME) { "超级账号不支持修改密码" }
            val response = serverApiService.changePassword(
                ChangePasswordRequestDto(oldPassword, newPassword)
            )
            if (!response.isSuccessful) {
                throw IOException(errorMessageFrom("修改密码", response.code(), response.errorBody()?.string().orEmpty()))
            }
        }

    override suspend fun uploadAvatar(avatarBase64: String): Result<Unit> = runCatching {
        require(avatarBase64.isNotBlank()) { "图片内容为空" }
        require(currentUsername() != SUPER_ADMIN_USERNAME) { "超级账号不支持设置头像" }
        val response = serverApiService.uploadAvatar(UploadAvatarRequestDto(avatarBase64))
        if (!response.isSuccessful) {
            throw IOException(errorMessageFrom("上传头像", response.code(), response.errorBody()?.string().orEmpty()))
        }
        val body = response.body() ?: throw IOException("上传头像失败，请稍后重试")
        authStorage.updateProfile(currentNickname(), body.avatarUrl)
    }

    private suspend fun currentUsername(): String =
        (authStorage.authState.first() as? AuthState.LoggedIn)?.user?.username.orEmpty()

    private suspend fun currentNickname(): String =
        (authStorage.authState.first() as? AuthState.LoggedIn)?.user?.nickname.orEmpty()

    /**
     * 优先把服务器返回的 detail 映射为友好中文文案；
     * 无 detail 或无法解析时回退到按状态码的通用提示。
     */
    private fun errorMessageFrom(operation: String, code: Int, errorBody: String = ""): String {
        val detailMessage = extractServerDetail(errorBody)?.let { detail ->
            when (detail) {
                "invalid credentials" -> "账号或密码错误"
                "username already exists" -> "账号已被占用"
                "invalid request" -> "请求参数错误"
                "old password incorrect" -> "旧密码不正确"
                "unauthorized" -> "登录已失效，请重新登录"
                else -> detail
            }
        }
        return detailMessage ?: when (code) {
            401 -> "账号或密码错误"
            409 -> "账号已被占用"
            else -> "${operation}失败 (HTTP $code)"
        }
    }

    companion object {
        const val SUPER_ADMIN_USERNAME = "admin"
        const val SUPER_ADMIN_PASSWORD = "admin"
        const val MIN_PASSWORD_LENGTH = 6
        const val MAX_NICKNAME_LENGTH = 20
    }
}

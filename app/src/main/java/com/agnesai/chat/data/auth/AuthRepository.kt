package com.agnesai.chat.data.auth

import kotlinx.coroutines.flow.Flow

/**
 * 账号认证仓库接口。
 *
 * 超级账号 admin 在本地校验（不请求网络），普通账号通过
 * [com.agnesai.chat.data.network.ServerApiService] 在云服务器校验。
 */
interface AuthRepository {

    val authState: Flow<AuthState>

    suspend fun login(username: String, password: String): Result<Unit>

    suspend fun register(username: String, password: String): Result<Unit>

    suspend fun logout()

    /** 修改昵称（1-20 字），成功后同步本地登录态。 */
    suspend fun updateProfile(nickname: String): Result<Unit>

    /** 修改密码：校验旧密码后更新，新密码至少 6 位。 */
    suspend fun changePassword(oldPassword: String, newPassword: String): Result<Unit>

    /** 上传头像（base64），成功后同步本地 avatarUrl。 */
    suspend fun uploadAvatar(avatarBase64: String): Result<Unit>
}

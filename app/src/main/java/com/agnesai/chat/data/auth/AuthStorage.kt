package com.agnesai.chat.data.auth

import kotlinx.coroutines.flow.Flow

/**
 * 登录态持久化抽象，由 [com.agnesai.chat.data.local.SettingsDataStore] 实现，
 * 供 [AuthRepositoryImpl] 依赖，便于单元测试替换为假实现。
 */
interface AuthStorage {

    val authState: Flow<AuthState>

    suspend fun getAuthToken(): String

    suspend fun saveAuth(token: String, user: UserInfo)

    suspend fun updateProfile(nickname: String, avatarUrl: String?)

    suspend fun clearAuth()
}

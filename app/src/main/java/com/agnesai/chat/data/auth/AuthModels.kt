package com.agnesai.chat.data.auth

import androidx.compose.runtime.Stable

/**
 * 登录状态机：
 * - [Checking]  应用启动，正在恢复本地会话
 * - [LoggedOut] 未登录
 * - [LoggedIn]  已登录，携带用户信息
 */
@Stable
sealed interface AuthState {
    data object Checking : AuthState
    data object LoggedOut : AuthState
    data class LoggedIn(val user: UserInfo) : AuthState
}

data class UserInfo(
    val id: String,
    val username: String,
    val nickname: String,
    val avatarUrl: String? = null
)

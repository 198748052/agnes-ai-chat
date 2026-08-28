package com.agnesai.chat.ui.auth

data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val isRegisterMode: Boolean = false,
    val isLoggingIn: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)

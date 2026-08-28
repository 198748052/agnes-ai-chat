package com.agnesai.chat.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agnesai.chat.data.auth.AuthRepository
import com.agnesai.chat.data.auth.AuthState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AuthViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    /** 全局登录状态，AppRoot 据此决定展示登录页还是主界面 */
    val authState: StateFlow<AuthState> = authRepository.authState
        .stateIn(viewModelScope, SharingStarted.Eagerly, AuthState.Checking)

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState = _uiState.asStateFlow()

    fun onUsernameChange(value: String) {
        _uiState.update { it.copy(username = value, error = null, successMessage = null) }
    }

    fun onPasswordChange(value: String) {
        _uiState.update { it.copy(password = value, error = null, successMessage = null) }
    }

    fun switchToRegister() {
        _uiState.update { it.copy(isRegisterMode = true, error = null, successMessage = null) }
    }

    fun switchToLogin() {
        _uiState.update { it.copy(isRegisterMode = false, error = null, successMessage = null) }
    }

    fun submit() {
        if (_uiState.value.isRegisterMode) register() else login()
    }

    fun login() {
        val state = _uiState.value
        if (state.isLoggingIn) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoggingIn = true, error = null) }
            authRepository.login(state.username, state.password)
                .onSuccess {
                    _uiState.update { it.copy(isLoggingIn = false) }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isLoggingIn = false, error = e.message ?: "登录失败，请稍后重试")
                    }
                }
        }
    }

    fun register() {
        val state = _uiState.value
        if (state.isLoggingIn) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoggingIn = true, error = null) }
            authRepository.register(state.username, state.password)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isLoggingIn = false,
                            isRegisterMode = false,
                            password = "",
                            successMessage = "注册成功，请登录"
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isLoggingIn = false, error = e.message ?: "注册失败，请稍后重试")
                    }
                }
        }
    }

    fun logout() {
        viewModelScope.launch { authRepository.logout() }
    }
}

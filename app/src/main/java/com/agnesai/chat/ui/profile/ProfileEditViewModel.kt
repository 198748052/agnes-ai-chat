package com.agnesai.chat.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agnesai.chat.data.auth.AuthRepository
import com.agnesai.chat.data.auth.AuthState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileEditUiState(
    val nickname: String = "",
    val nicknameError: String? = null,
    val oldPassword: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    val passwordError: String? = null,
    val isSavingNickname: Boolean = false,
    val isSavingPassword: Boolean = false,
    val isUploadingAvatar: Boolean = false,
    val message: String? = null,
    val avatarUrl: String? = null
)

class ProfileEditViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileEditUiState())
    val uiState: StateFlow<ProfileEditUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val user = (authRepository.authState.first() as? AuthState.LoggedIn)?.user
            if (user != null) {
                _uiState.update { it.copy(nickname = user.nickname, avatarUrl = user.avatarUrl) }
            }
        }
    }

    fun onNicknameChange(value: String) {
        _uiState.update { it.copy(nickname = value, nicknameError = null, message = null) }
    }

    fun saveNickname() {
        val state = _uiState.value
        val trimmed = state.nickname.trim()
        when {
            trimmed.isEmpty() -> {
                _uiState.update { it.copy(nicknameError = "昵称不能为空") }
                return
            }
            trimmed.length > MAX_NICKNAME_LENGTH -> {
                _uiState.update { it.copy(nicknameError = "昵称最长 $MAX_NICKNAME_LENGTH 字") }
                return
            }
            state.isSavingNickname -> return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingNickname = true, nicknameError = null, message = null) }
            authRepository.updateProfile(trimmed)
                .onSuccess {
                    _uiState.update {
                        it.copy(isSavingNickname = false, nickname = trimmed, message = "昵称已更新")
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isSavingNickname = false, nicknameError = e.message ?: "修改昵称失败")
                    }
                }
        }
    }

    fun onOldPasswordChange(value: String) {
        _uiState.update { it.copy(oldPassword = value, passwordError = null, message = null) }
    }

    fun onNewPasswordChange(value: String) {
        _uiState.update { it.copy(newPassword = value, passwordError = null, message = null) }
    }

    fun onConfirmPasswordChange(value: String) {
        _uiState.update { it.copy(confirmPassword = value, passwordError = null, message = null) }
    }

    fun savePassword() {
        val state = _uiState.value
        when {
            state.newPassword.length < MIN_PASSWORD_LENGTH -> {
                _uiState.update { it.copy(passwordError = "新密码至少 $MIN_PASSWORD_LENGTH 位") }
                return
            }
            state.newPassword != state.confirmPassword -> {
                _uiState.update { it.copy(passwordError = "两次输入的新密码不一致") }
                return
            }
            state.isSavingPassword -> return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingPassword = true, passwordError = null, message = null) }
            authRepository.changePassword(state.oldPassword, state.newPassword)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isSavingPassword = false,
                            oldPassword = "",
                            newPassword = "",
                            confirmPassword = "",
                            message = "密码修改成功，下次登录请使用新密码"
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isSavingPassword = false, passwordError = e.message ?: "修改密码失败")
                    }
                }
        }
    }

    fun uploadAvatar(avatarBase64: String) {
        if (_uiState.value.isUploadingAvatar) return
        viewModelScope.launch {
            _uiState.update { it.copy(isUploadingAvatar = true, message = null) }
            authRepository.uploadAvatar(avatarBase64)
                .onSuccess {
                    val url = (authRepository.authState.first() as? AuthState.LoggedIn)?.user?.avatarUrl
                    _uiState.update { it.copy(isUploadingAvatar = false, avatarUrl = url, message = "头像已更新") }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isUploadingAvatar = false, message = e.message ?: "上传头像失败")
                    }
                }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    companion object {
        const val MAX_NICKNAME_LENGTH = 20
        const val MIN_PASSWORD_LENGTH = 6
    }
}

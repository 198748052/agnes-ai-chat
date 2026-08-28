package com.agnesai.chat.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agnesai.chat.data.local.SettingsDataStore
import com.agnesai.chat.data.network.MODEL_NAME
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val apiKey: String = "",
    val systemPrompt: String = SettingsDataStore.DEFAULT_SYSTEM_PROMPT,
    val modelName: String = MODEL_NAME,
    val temperature: Float = 1f,
    val topP: Float = 1f,
    /** max_tokens 输入文本，可留空 */
    val maxTokensInput: String = "",
    val maxTokensError: String? = null,
    val saved: Boolean = false,
    /** 一次性提示文案（保存失败等），展示后由 UI 调用 [SettingsViewModel.consumeMessage] 清空 */
    val message: String? = null
)

class SettingsViewModel(
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val apiKey = settingsDataStore.apiKey.first()
            val systemPrompt = settingsDataStore.systemPrompt.first()
            val settings = settingsDataStore.chatSettings.first()
            _uiState.update {
                it.copy(
                    apiKey = apiKey,
                    systemPrompt = systemPrompt,
                    modelName = settings.modelName,
                    temperature = settings.temperature,
                    topP = settings.topP,
                    maxTokensInput = settings.maxTokens?.toString().orEmpty()
                )
            }
        }
    }

    fun onApiKeyChange(value: String) {
        _uiState.update { it.copy(apiKey = value, saved = false) }
    }

    fun onSystemPromptChange(value: String) {
        _uiState.update { it.copy(systemPrompt = value, saved = false) }
    }

    fun onModelChange(value: String) {
        _uiState.update { it.copy(modelName = value, saved = false) }
    }

    fun onTemperatureChange(value: Float) {
        _uiState.update { it.copy(temperature = value, saved = false) }
    }

    fun onTopPChange(value: Float) {
        _uiState.update { it.copy(topP = value, saved = false) }
    }

    fun onMaxTokensChange(value: String) {
        _uiState.update {
            it.copy(
                maxTokensInput = value.filter(Char::isDigit),
                maxTokensError = null,
                saved = false
            )
        }
    }

    fun save() {
        val state = _uiState.value
        // max_tokens 必须为正整数；留空表示不限制
        val maxTokens = when {
            state.maxTokensInput.isBlank() -> null
            else -> state.maxTokensInput.toIntOrNull()?.takeIf { it > 0 }
        }
        if (state.maxTokensInput.isNotBlank() && maxTokens == null) {
            _uiState.update {
                it.copy(maxTokensError = "请输入正整数或留空", saved = false)
            }
            return
        }
        viewModelScope.launch {
            runCatching {
                settingsDataStore.setApiKey(state.apiKey)
                settingsDataStore.setSystemPrompt(state.systemPrompt)
                settingsDataStore.saveChatSettings(
                    modelName = state.modelName,
                    temperature = state.temperature,
                    topP = state.topP,
                    maxTokens = maxTokens
                )
            }.onSuccess {
                _uiState.update { it.copy(saved = true, message = null, maxTokensError = null) }
            }.onFailure {
                _uiState.update { it.copy(saved = false, message = "保存失败，请重试") }
            }
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }
}

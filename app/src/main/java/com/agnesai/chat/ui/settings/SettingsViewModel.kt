package com.agnesai.chat.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agnesai.chat.data.local.SettingsDataStore
import com.agnesai.chat.data.network.API_BASE_URL
import com.agnesai.chat.data.network.MODEL_NAME
import com.agnesai.chat.data.network.BaseUrlException
import com.agnesai.chat.data.network.BaseUrlValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val baseUrl: String = API_BASE_URL,
    /** API 地址格式错误提示；null 表示无错误 */
    val baseUrlError: String? = null,
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
            val baseUrl = settingsDataStore.baseUrl.first()
            val apiKey = settingsDataStore.apiKey.first()
            val systemPrompt = settingsDataStore.systemPrompt.first()
            val settings = settingsDataStore.chatSettings.first()
            _uiState.update {
                it.copy(
                    baseUrl = baseUrl,
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

    fun onBaseUrlChange(value: String) {
        _uiState.update { it.copy(baseUrl = value, baseUrlError = null, saved = false) }
    }

    /** 输入框重置为默认地址并立即持久化 */
    fun onResetBaseUrl() {
        _uiState.update { it.copy(baseUrl = API_BASE_URL, baseUrlError = null, saved = false) }
        viewModelScope.launch {
            runCatching { settingsDataStore.setBaseUrl(API_BASE_URL) }
                .onFailure {
                    _uiState.update { state -> state.copy(message = "保存失败，请重试") }
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
        // API 地址校验；空白输入解析为 null，保存时回退默认地址
        val normalizedBaseUrl = try {
            BaseUrlValidator.normalize(state.baseUrl)
        } catch (e: BaseUrlException) {
            _uiState.update { it.copy(baseUrlError = e.reason, saved = false) }
            return
        }
        viewModelScope.launch {
            runCatching {
                settingsDataStore.setBaseUrl(normalizedBaseUrl ?: API_BASE_URL)
                settingsDataStore.setApiKey(state.apiKey)
                settingsDataStore.setSystemPrompt(state.systemPrompt)
                settingsDataStore.saveChatSettings(
                    modelName = state.modelName,
                    temperature = state.temperature,
                    topP = state.topP,
                    maxTokens = maxTokens
                )
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        saved = true,
                        message = null,
                        maxTokensError = null,
                        baseUrlError = null,
                        baseUrl = normalizedBaseUrl ?: API_BASE_URL
                    )
                }
            }.onFailure {
                _uiState.update { it.copy(saved = false, message = "保存失败，请重试") }
            }
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }
}

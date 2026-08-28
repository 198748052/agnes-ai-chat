package com.agnesai.chat.ui.storage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agnesai.chat.data.storage.StorageRepository
import com.agnesai.chat.data.storage.StorageType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class StorageViewModel(
    private val repository: StorageRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StorageUiState())
    val uiState = _uiState.asStateFlow()

    init {
        load()
    }

    /** 重新统计本地存储占用。 */
    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            runCatching { repository.getStorageSummary() }
                .onSuccess { summary ->
                    _uiState.update { it.copy(loading = false, summary = summary) }
                }
                .onFailure {
                    _uiState.update {
                        it.copy(loading = false, summary = null, error = "加载失败，请重试")
                    }
                }
        }
    }

    /** 发起清理：弹出二次确认对话框。 */
    fun requestClear(type: String) {
        if (_uiState.value.clearing) return
        _uiState.update { it.copy(pendingClearType = type) }
    }

    fun cancelClear() {
        _uiState.update { it.copy(pendingClearType = null) }
    }

    /** 确认清理：执行清理并刷新统计。 */
    fun confirmClear() {
        val type = _uiState.value.pendingClearType ?: return
        _uiState.update { it.copy(pendingClearType = null, clearing = true) }
        viewModelScope.launch {
            val result = runCatching {
                when (type) {
                    StorageType.CACHE -> repository.clearCache()
                    StorageType.ALL -> repository.clearAll()
                    else -> repository.clearByType(type)
                }
            }
            result.onSuccess {
                val summary = runCatching { repository.getStorageSummary() }.getOrNull()
                _uiState.update {
                    it.copy(
                        clearing = false,
                        summary = summary ?: it.summary,
                        message = "清理完成"
                    )
                }
            }.onFailure {
                _uiState.update { it.copy(clearing = false, message = "清理失败，请重试") }
            }
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }
}

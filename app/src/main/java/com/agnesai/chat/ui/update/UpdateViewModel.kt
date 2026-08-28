package com.agnesai.chat.ui.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agnesai.chat.BuildConfig
import com.agnesai.chat.data.update.UpdateRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class UpdateViewModel(
    private val repository: UpdateRepository,
    private val currentVersionCode: Int = BuildConfig.VERSION_CODE
) : ViewModel() {

    private val _uiState = MutableStateFlow(UpdateUiState())
    val uiState = _uiState.asStateFlow()

    fun checkForUpdate() {
        viewModelScope.launch {
            _uiState.update { it.copy(checking = true) }
            repository.checkForUpdate(currentVersionCode)
                .onSuccess { info ->
                    _uiState.update { it.copy(updateInfo = info, checking = false) }
                }
                .onFailure {
                    _uiState.update { it.copy(updateInfo = null, checking = false) }
                }
        }
    }

    /** 仅非强制更新可关闭弹窗 */
    fun dismiss() {
        val info = _uiState.value.updateInfo ?: return
        if (info.forceUpdate) return
        _uiState.update { it.copy(updateInfo = null) }
    }

    /** 跳转下载：TODO 实现 APK 下载（DownloadManager）与安装引导（FileProvider） */
    fun download() {
        // TODO: 使用 info.downloadUrl 触发 APK 下载并引导安装。
        _uiState.update { it.copy(downloading = true) }
    }
}

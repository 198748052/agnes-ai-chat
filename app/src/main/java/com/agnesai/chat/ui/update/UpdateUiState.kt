package com.agnesai.chat.ui.update

import com.agnesai.chat.data.update.UpdateInfo

data class UpdateUiState(
    /** 非 null 表示存在可用更新，展示更新弹窗 */
    val updateInfo: UpdateInfo? = null,
    val checking: Boolean = false,
    val downloading: Boolean = false
)

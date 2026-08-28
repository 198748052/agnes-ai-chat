package com.agnesai.chat.ui.storage

import com.agnesai.chat.data.storage.StorageSummary

data class StorageUiState(
    val loading: Boolean = true,
    val summary: StorageSummary? = null,
    val error: String? = null,
    val pendingClearType: String? = null,
    val clearing: Boolean = false,
    val message: String? = null
)

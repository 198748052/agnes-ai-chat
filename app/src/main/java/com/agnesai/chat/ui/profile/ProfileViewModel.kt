package com.agnesai.chat.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agnesai.chat.data.stats.PeriodCounts
import com.agnesai.chat.data.stats.StatsResult
import com.agnesai.chat.data.stats.StatsSource
import com.agnesai.chat.data.storage.StorageSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val loading: Boolean = true,
    val source: StatsSource = StatsSource.LOCAL,
    val image: PeriodCounts = PeriodCounts(),
    val video: PeriodCounts = PeriodCounts(),
    val storage: StorageSummary? = null,
    val error: String? = null
)

class ProfileViewModel(
    private val loadStats: suspend () -> StatsResult?,
    private val loadStorage: suspend () -> StorageSummary?
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    /** 加载创作统计概览与本地存储占用，统计失败时保留空数据并提示。 */
    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            val stats = runCatching { loadStats() }.getOrNull()
            val storage = runCatching { loadStorage() }.getOrNull()
            if (stats == null) {
                _uiState.update { it.copy(loading = false, error = "概览加载失败") }
            } else {
                _uiState.update {
                    it.copy(
                        loading = false,
                        source = stats.source,
                        image = stats.image,
                        video = stats.video,
                        storage = storage
                    )
                }
            }
        }
    }
}

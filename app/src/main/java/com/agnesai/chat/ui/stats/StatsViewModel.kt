package com.agnesai.chat.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agnesai.chat.data.stats.PeriodCounts
import com.agnesai.chat.data.stats.StatsRepository
import com.agnesai.chat.data.stats.StatsSource
import com.agnesai.chat.data.storage.StorageRepository
import com.agnesai.chat.data.storage.StorageSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StatsUiState(
    val loading: Boolean = true,
    val source: StatsSource = StatsSource.LOCAL,
    val image: PeriodCounts = PeriodCounts(),
    val video: PeriodCounts = PeriodCounts(),
    val storage: StorageSummary? = null,
    val error: String? = null
)

class StatsViewModel(
    private val statsRepository: StatsRepository,
    private val storageRepository: StorageRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    /** 重新加载生成统计与本地存储占用。 */
    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            val stats = runCatching { statsRepository.loadStats() }.getOrNull()
            val storage = runCatching { storageRepository.getStorageSummary() }.getOrNull()
            if (stats == null) {
                _uiState.update { it.copy(loading = false, error = "加载失败，请重试") }
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

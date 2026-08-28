package com.agnesai.chat.ui.announcement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agnesai.chat.data.announcement.AnnouncementRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AnnouncementViewModel(
    private val repository: AnnouncementRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnnouncementUiState())
    val uiState = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            repository.fetchAnnouncements()
                .onSuccess { list ->
                    _uiState.update {
                        it.copy(
                            announcements = list.sortedByDescending { a -> a.publishAt },
                            loading = false
                        )
                    }
                }
                .onFailure {
                    _uiState.update { it.copy(announcements = emptyList(), loading = false) }
                }
        }
    }
}

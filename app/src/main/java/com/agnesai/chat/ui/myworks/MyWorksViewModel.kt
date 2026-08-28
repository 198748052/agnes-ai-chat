package com.agnesai.chat.ui.myworks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agnesai.chat.data.works.MyWork
import com.agnesai.chat.data.works.MyWorksRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MyWorksViewModel(
    private val repository: MyWorksRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MyWorksUiState())
    val uiState = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            repository.observeWorks()
                .catch { e ->
                    _uiState.update {
                        it.copy(loading = false, error = e.message ?: "作品加载失败，请重试")
                    }
                }
                .collect { works ->
                    _uiState.update {
                        it.copy(loading = false, works = works, error = null)
                    }
                }
        }
    }

    fun setFilter(filter: WorkFilter) {
        _uiState.update { it.copy(filter = filter) }
    }

    fun openDetail(work: MyWork) {
        _uiState.update { it.copy(detailWork = work) }
    }

    fun closeDetail() {
        _uiState.update { it.copy(detailWork = null) }
    }

    fun requestDelete(work: MyWork) {
        _uiState.update { it.copy(pendingDeleteWork = work) }
    }

    fun cancelDelete() {
        if (!_uiState.value.deleting) {
            _uiState.update { it.copy(pendingDeleteWork = null) }
        }
    }

    fun confirmDelete() {
        val work = _uiState.value.pendingDeleteWork ?: return
        if (_uiState.value.deleting) return
        _uiState.update { it.copy(deleting = true) }
        viewModelScope.launch {
            runCatching { repository.deleteWork(work.id) }
                .onSuccess {
                    // 列表由 Flow 自动刷新
                    _uiState.update {
                        it.copy(deleting = false, pendingDeleteWork = null)
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            deleting = false,
                            pendingDeleteWork = null,
                            error = e.message ?: "删除失败，请重试"
                        )
                    }
                }
        }
    }
}

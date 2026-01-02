package com.example.ai4research.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai4research.domain.model.ItemType
import com.example.ai4research.domain.model.ResearchItem
import com.example.ai4research.domain.repository.ItemRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import javax.inject.Inject

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel @Inject constructor(
    private val itemRepository: ItemRepository
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _selectedType = MutableStateFlow<ItemType?>(null) // null = 全部
    val selectedType: StateFlow<ItemType?> = _selectedType.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    val items: StateFlow<List<ResearchItem>> =
        combine(_selectedType, _query) { type, query -> type to query }
            .flatMapLatest { (type, query) ->
                itemRepository.observeItems(type = type, query = query)
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )

    init {
        refresh()
    }

    fun setQuery(value: String) {
        _query.value = value
    }

    fun setTypeFilter(type: ItemType?) {
        _selectedType.value = type
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            _errorMessage.value = null
            val result = itemRepository.refreshItems()
            _isRefreshing.value = false
            result.exceptionOrNull()?.let { _errorMessage.value = it.message ?: "同步失败" }
        }
    }

    fun createUrlItem(url: String, title: String? = null, note: String? = null) {
        viewModelScope.launch {
            _errorMessage.value = null
            val result = itemRepository.createUrlItem(url = url, title = title, note = note)
            result.exceptionOrNull()?.let { _errorMessage.value = it.message ?: "创建失败" }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}


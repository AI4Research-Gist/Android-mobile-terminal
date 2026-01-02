package com.example.ai4research.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai4research.domain.model.ReadStatus
import com.example.ai4research.domain.model.ResearchItem
import com.example.ai4research.domain.repository.ItemRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DetailUiState(
    val isLoading: Boolean = true,
    val item: ResearchItem? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val itemRepository: ItemRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    fun load(itemId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val item = itemRepository.getItem(itemId)
            _uiState.value = DetailUiState(
                isLoading = false,
                item = item,
                errorMessage = if (item == null) "未找到该条目" else null
            )
        }
    }

    fun markAsRead() {
        val item = _uiState.value.item ?: return
        viewModelScope.launch {
            itemRepository.updateReadStatus(item.id, ReadStatus.READ)
            load(item.id)
        }
    }

    fun delete() {
        val item = _uiState.value.item ?: return
        viewModelScope.launch {
            itemRepository.deleteItem(item.id)
            // 由 UI 负责返回上一页
        }
    }
}



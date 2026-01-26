package com.example.ai4research.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai4research.domain.model.Project
import com.example.ai4research.domain.model.ReadStatus
import com.example.ai4research.domain.model.ResearchItem
import com.example.ai4research.domain.repository.ItemRepository
import com.example.ai4research.domain.repository.ProjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DetailUiState(
    val isLoading: Boolean = true,
    val item: ResearchItem? = null,
    val projects: List<Project> = emptyList(),
    val isProjectSaving: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val itemRepository: ItemRepository,
    private val projectRepository: ProjectRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            projectRepository.observeProjects().collect { projects ->
                _uiState.value = _uiState.value.copy(projects = projects)
            }
        }
    }

    fun load(itemId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val item = itemRepository.getItem(itemId)
            _uiState.value = DetailUiState(
                isLoading = false,
                item = item,
                projects = _uiState.value.projects,
                isProjectSaving = false,
                errorMessage = if (item == null) "未找到该条目" else null
            )
        }
    }

    fun refreshProjects() {
        viewModelScope.launch {
            projectRepository.refreshProjects()
        }
    }

    fun updateProject(projectId: String?) {
        val item = _uiState.value.item ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProjectSaving = true, errorMessage = null)
            val result = itemRepository.updateItemProject(item.id, projectId)
            if (result.isSuccess) {
                load(item.id)
            } else {
                _uiState.value = _uiState.value.copy(
                    isProjectSaving = false,
                    errorMessage = "更新项目失败: ${result.exceptionOrNull()?.message}"
                )
            }
        }
    }

    fun markAsRead() {
        val item = _uiState.value.item ?: return
        viewModelScope.launch {
            itemRepository.updateReadStatus(item.id, ReadStatus.READ)
            load(item.id)
        }
    }
    
    fun toggleStar() {
        val item = _uiState.value.item ?: return
        viewModelScope.launch {
            itemRepository.updateStarred(item.id, !item.isStarred)
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

    fun saveContent(summary: String, content: String) {
        val item = _uiState.value.item ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val result = itemRepository.updateItem(
                id = item.id,
                summary = summary,
                content = content
            )
            
            if (result.isSuccess) {
                load(item.id)
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "保存失败: ${result.exceptionOrNull()?.message}"
                )
            }
        }
    }
}







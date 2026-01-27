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
    val isCreatingProject: Boolean = false,
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
            
            // 自动标记为已读（如果当前是未读状态）
            if (item != null && item.readStatus == ReadStatus.UNREAD) {
                markAsReadInternal(itemId)
            }
        }
    }
    
    /**
     * 内部方法：标记已读，不重新加载（避免循环）
     */
    private fun markAsReadInternal(itemId: String) {
        viewModelScope.launch {
            itemRepository.updateReadStatus(itemId, ReadStatus.READ)
            // 更新当前 item 的状态
            _uiState.value.item?.let { currentItem ->
                _uiState.value = _uiState.value.copy(
                    item = currentItem.copy(readStatus = ReadStatus.READ)
                )
            }
        }
    }

    fun refreshProjects() {
        viewModelScope.launch {
            projectRepository.refreshProjects()
        }
    }
    
    /**
     * 删除项目
     */
    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProjectSaving = true, errorMessage = null)
            val result = projectRepository.deleteProject(projectId)
            if (result.isSuccess) {
                android.util.Log.d("DetailViewModel", "项目删除成功: $projectId")
                // 如果当前 item 属于被删除的项目，更新其项目归属为 null
                val item = _uiState.value.item
                if (item != null && item.projectId == projectId) {
                    itemRepository.updateItemProject(item.id, null)
                    load(item.id)
                }
                _uiState.value = _uiState.value.copy(isProjectSaving = false)
            } else {
                _uiState.value = _uiState.value.copy(
                    isProjectSaving = false,
                    errorMessage = "删除项目失败: ${result.exceptionOrNull()?.message}"
                )
            }
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

    fun saveContent(summary: String, content: String, metaJson: String? = null) {
        val item = _uiState.value.item ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val result = itemRepository.updateItem(
                id = item.id,
                summary = summary,
                content = content,
                metaJson = metaJson
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
    
    /**
     * 创建新项目并自动关联当前条目
     */
    fun createProject(name: String, autoAssign: Boolean = true) {
        if (name.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "项目名称不能为空")
            return
        }
        
        val item = _uiState.value.item
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCreatingProject = true, errorMessage = null)
            
            val result = projectRepository.createProject(name)
            
            result.onSuccess { newProject ->
                // 刷新项目列表
                projectRepository.refreshProjects()
                
                // 如果需要自动关联且有当前条目，则更新条目的项目归属
                if (autoAssign && item != null) {
                    val updateResult = itemRepository.updateItemProject(item.id, newProject.id)
                    if (updateResult.isSuccess) {
                        load(item.id)
                    }
                }
                
                _uiState.value = _uiState.value.copy(isCreatingProject = false)
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isCreatingProject = false,
                    errorMessage = "创建项目失败: ${error.message}"
                )
            }
        }
    }
}







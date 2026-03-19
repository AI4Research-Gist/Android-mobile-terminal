package com.example.ai4research.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai4research.domain.model.Project
import com.example.ai4research.domain.model.ReadStatus
import com.example.ai4research.domain.model.ResearchItem
import com.example.ai4research.domain.repository.ItemRepository
import com.example.ai4research.domain.repository.ProjectRepository
import com.example.ai4research.service.AIService
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
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
    val isRegeneratingSummary: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val itemRepository: ItemRepository,
    private val projectRepository: ProjectRepository,
    private val aiService: AIService
) : ViewModel() {

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()
    private val gson = Gson()

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
                isRegeneratingSummary = false,
                errorMessage = if (item == null) "未找到该条目" else null
            )

            if (item != null && item.readStatus == ReadStatus.UNREAD) {
                markAsReadInternal(itemId)
            }
        }
    }

    private fun markAsReadInternal(itemId: String) {
        viewModelScope.launch {
            itemRepository.updateReadStatus(itemId, ReadStatus.READ)
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

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProjectSaving = true, errorMessage = null)
            val result = projectRepository.deleteProject(projectId)
            if (result.isSuccess) {
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
        }
    }

    fun saveContent(summary: String, note: String?, content: String, metaJson: String? = null) {
        val item = _uiState.value.item ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val result = itemRepository.updateItem(
                id = item.id,
                summary = summary,
                note = note,
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

    fun regeneratePaperBilingualSummary() {
        val item = _uiState.value.item ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRegeneratingSummary = true, errorMessage = null)

            val content = item.contentMarkdown.ifBlank { item.summary }
            val result = aiService.generateBilingualSummary(
                title = item.title,
                sourceContent = content,
                existingSummary = item.summary
            )

            result.onSuccess { summary ->
                val mergedMetaJson = mergeSummaryIntoMetaJson(
                    existingMetaJson = item.rawMetaJson,
                    summaryZh = summary.summaryZh,
                    summaryEn = summary.summaryEn,
                    summaryShort = summary.summaryShort
                )

                val updateResult = itemRepository.updateItem(
                    id = item.id,
                    note = item.note,
                    metaJson = mergedMetaJson
                )

                if (updateResult.isSuccess) {
                    load(item.id)
                } else {
                    _uiState.value = _uiState.value.copy(
                        isRegeneratingSummary = false,
                        errorMessage = "重新生成摘要失败: ${updateResult.exceptionOrNull()?.message}"
                    )
                }
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isRegeneratingSummary = false,
                    errorMessage = "重新生成摘要失败: ${error.message}"
                )
            }
        }
    }

    private fun mergeSummaryIntoMetaJson(
        existingMetaJson: String?,
        summaryZh: String?,
        summaryEn: String?,
        summaryShort: String?
    ): String {
        val type = object : TypeToken<MutableMap<String, Any?>>() {}.type
        val map: MutableMap<String, Any?> = if (existingMetaJson.isNullOrBlank()) {
            mutableMapOf()
        } else {
            runCatching { gson.fromJson(existingMetaJson, type) as MutableMap<String, Any?> }
                .getOrElse { mutableMapOf() }
        }

        summaryZh?.let { map["summary_zh"] = it }
        summaryEn?.let { map["summary_en"] = it }
        summaryShort?.let { map["summary_short"] = it }

        return gson.toJson(map)
    }

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
                projectRepository.refreshProjects()

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

package com.example.ai4research.ui.project

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai4research.domain.model.ProjectAiSummary
import com.example.ai4research.domain.model.ProjectOverview
import com.example.ai4research.domain.repository.ProjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProjectOverviewUiState(
    val isLoading: Boolean = true,
    val overview: ProjectOverview? = null,
    val isSavingContext: Boolean = false,
    val isGeneratingSummary: Boolean = false,
    val generatedSummary: ProjectAiSummary? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class ProjectOverviewViewModel @Inject constructor(
    private val projectRepository: ProjectRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProjectOverviewUiState())
    val uiState: StateFlow<ProjectOverviewUiState> = _uiState.asStateFlow()
    private var currentProjectId: String? = null

    fun load(projectId: String) {
        viewModelScope.launch {
            val previousState = _uiState.value
            val keepSummary = currentProjectId == projectId
            _uiState.value = previousState.copy(
                isLoading = true,
                errorMessage = null,
                generatedSummary = if (keepSummary) previousState.generatedSummary else null
            )
            val overview = projectRepository.getProjectOverview(projectId)
            currentProjectId = projectId
            _uiState.value = if (overview != null) {
                ProjectOverviewUiState(
                    isLoading = false,
                    overview = overview,
                    generatedSummary = if (keepSummary) previousState.generatedSummary else null
                )
            } else {
                ProjectOverviewUiState(
                    isLoading = false,
                    overview = null,
                    generatedSummary = null,
                    errorMessage = "暂时无法加载项目总览"
                )
            }
        }
    }

    fun saveProjectContext(projectId: String, fileName: String, markdownContent: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSavingContext = true,
                generatedSummary = null,
                errorMessage = null
            )
            val result = projectRepository.saveProjectContextDocument(
                projectId = projectId,
                fileName = fileName,
                markdownContent = markdownContent
            )

            if (result.isSuccess) {
                load(projectId)
            } else {
                _uiState.value = _uiState.value.copy(
                    isSavingContext = false,
                    errorMessage = "保存研究背景失败: ${result.exceptionOrNull()?.message}"
                )
            }
        }
    }

    fun generateProjectSummary(projectId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isGeneratingSummary = true, errorMessage = null)
            val result = projectRepository.generateProjectSummary(projectId)
            result.onSuccess { summary ->
                _uiState.value = _uiState.value.copy(
                    isGeneratingSummary = false,
                    generatedSummary = summary
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isGeneratingSummary = false,
                    errorMessage = "生成项目总结失败: ${error.message}"
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}

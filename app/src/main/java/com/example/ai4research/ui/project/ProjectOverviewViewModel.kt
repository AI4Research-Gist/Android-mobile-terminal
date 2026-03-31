package com.example.ai4research.ui.project

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    val errorMessage: String? = null
)

@HiltViewModel
class ProjectOverviewViewModel @Inject constructor(
    private val projectRepository: ProjectRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProjectOverviewUiState())
    val uiState: StateFlow<ProjectOverviewUiState> = _uiState.asStateFlow()

    fun load(projectId: String) {
        viewModelScope.launch {
            _uiState.value = ProjectOverviewUiState(isLoading = true)
            val overview = projectRepository.getProjectOverview(projectId)
            _uiState.value = if (overview != null) {
                ProjectOverviewUiState(
                    isLoading = false,
                    overview = overview
                )
            } else {
                ProjectOverviewUiState(
                    isLoading = false,
                    overview = null,
                    errorMessage = "暂时无法加载项目总览"
                )
            }
        }
    }
}

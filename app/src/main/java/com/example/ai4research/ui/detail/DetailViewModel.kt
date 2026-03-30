package com.example.ai4research.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai4research.domain.model.ItemMetaData
import com.example.ai4research.domain.model.ItemType
import com.example.ai4research.domain.model.Project
import com.example.ai4research.domain.model.ReadStatus
import com.example.ai4research.domain.model.ResearchItem
import com.example.ai4research.domain.model.StructuredReadingCard
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
    val isGeneratingReadingCard: Boolean = false,
    val generatedReadingCard: StructuredReadingCard? = null,
    val isAiSheetVisible: Boolean = false,
    val chatMessages: List<AiChatMessage> = emptyList(),
    val isAiResponding: Boolean = false,
    val errorMessage: String? = null
)

data class AiChatMessage(
    val role: AiChatRole,
    val content: String
)

enum class AiChatRole {
    USER,
    ASSISTANT
}

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
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null,
                generatedReadingCard = null,
                chatMessages = emptyList(),
                isAiResponding = false
            )
            val item = itemRepository.getItem(itemId)
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                item = item,
                isProjectSaving = false,
                isRegeneratingSummary = false,
                isGeneratingReadingCard = false,
                errorMessage = if (item == null) "Item not found" else null
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
                    errorMessage = "Delete project failed: ${result.exceptionOrNull()?.message}"
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
                    errorMessage = "Update project failed: ${result.exceptionOrNull()?.message}"
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
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
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
                    errorMessage = "Save failed: ${result.exceptionOrNull()?.message}"
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
                        errorMessage = "Regenerate summary failed: ${updateResult.exceptionOrNull()?.message}"
                    )
                }
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isRegeneratingSummary = false,
                    errorMessage = "Regenerate summary failed: ${error.message}"
                )
            }
        }
    }

    fun generateReadingCardDraft() {
        val item = _uiState.value.item ?: return
        if (item.type != ItemType.PAPER && item.type != ItemType.ARTICLE) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isGeneratingReadingCard = true, errorMessage = null)

            val existingCard = when (val meta = item.metaData) {
                is ItemMetaData.PaperMeta -> meta.readingCard
                is ItemMetaData.ArticleMeta -> meta.readingCard
                else -> null
            }

            val result = aiService.generateStructuredReadingCard(
                title = item.title,
                sourceContent = item.contentMarkdown.ifBlank { item.summary },
                existingSummary = item.summary,
                existingCard = existingCard,
                itemType = item.type.toServerString()
            )

            result.onSuccess { generated ->
                _uiState.value = _uiState.value.copy(
                    isGeneratingReadingCard = false,
                    generatedReadingCard = generated.card
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isGeneratingReadingCard = false,
                    errorMessage = "Generate reading card failed: ${error.message}"
                )
            }
        }
    }

    fun consumeGeneratedReadingCard() {
        _uiState.value = _uiState.value.copy(generatedReadingCard = null)
    }

    fun openAiAssistant() {
        _uiState.value = _uiState.value.copy(isAiSheetVisible = true, errorMessage = null)
    }

    fun closeAiAssistant() {
        _uiState.value = _uiState.value.copy(isAiSheetVisible = false)
    }

    fun askAboutCurrentItem(question: String) {
        val item = _uiState.value.item ?: return
        val trimmedQuestion = question.trim()
        if (trimmedQuestion.isBlank()) return

        viewModelScope.launch {
            val currentMessages = _uiState.value.chatMessages + AiChatMessage(
                role = AiChatRole.USER,
                content = trimmedQuestion
            )
            _uiState.value = _uiState.value.copy(
                chatMessages = currentMessages,
                isAiResponding = true,
                errorMessage = null
            )

            val result = aiService.answerQuestionAboutItem(
                title = item.title,
                summary = item.summary,
                contentMarkdown = item.contentMarkdown,
                metaJson = item.rawMetaJson,
                question = trimmedQuestion,
                itemType = item.type.toServerString()
            )

            result.onSuccess { answer ->
                _uiState.value = _uiState.value.copy(
                    chatMessages = _uiState.value.chatMessages + AiChatMessage(
                        role = AiChatRole.ASSISTANT,
                        content = answer
                    ),
                    isAiResponding = false
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    chatMessages = _uiState.value.chatMessages + AiChatMessage(
                        role = AiChatRole.ASSISTANT,
                        content = "I could not answer this yet: ${error.message ?: "unknown error"}"
                    ),
                    isAiResponding = false,
                    errorMessage = "AI answer failed: ${error.message}"
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun createProject(name: String, autoAssign: Boolean = true) {
        if (name.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Project name cannot be empty")
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
                    errorMessage = "Create project failed: ${error.message}"
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
}

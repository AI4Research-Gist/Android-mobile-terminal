package com.example.ai4research.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai4research.domain.model.ItemMetaData
import com.example.ai4research.domain.model.ItemConnection
import com.example.ai4research.domain.model.ItemType
import com.example.ai4research.domain.model.Project
import com.example.ai4research.domain.model.ReadStatus
import com.example.ai4research.domain.model.ResearchItem
import com.example.ai4research.domain.model.StructuredReadingCard
import com.example.ai4research.domain.repository.ItemRepository
import com.example.ai4research.domain.repository.KnowledgeConnectionRepository
import com.example.ai4research.domain.repository.ProjectRepository
import com.example.ai4research.service.AIService
import com.example.ai4research.service.ImageScanImportService
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DetailUiState(
    val isLoading: Boolean = true,
    val item: ResearchItem? = null,
    val connections: List<ItemConnection> = emptyList(),
    val availableLinkTargets: List<ResearchItem> = emptyList(),
    val availableComparisonTargets: List<ResearchItem> = emptyList(),
    val projects: List<Project> = emptyList(),
    val isProjectSaving: Boolean = false,
    val isCreatingProject: Boolean = false,
    val isRegeneratingSummary: Boolean = false,
    val isGeneratingReadingCard: Boolean = false,
    val isRetryingOcr: Boolean = false,
    val generatedReadingCard: StructuredReadingCard? = null,
    val isComparisonDialogVisible: Boolean = false,
    val isGeneratingComparison: Boolean = false,
    val comparisonResult: LiteratureComparisonViewData? = null,
    val isInsightLinkEditorVisible: Boolean = false,
    val isSavingInsightLinks: Boolean = false,
    val isLookingUpInsightLinks: Boolean = false,
    val insightRecommendations: List<InsightLookupRecommendation> = emptyList(),
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

data class InsightLookupRecommendation(
    val item: ResearchItem,
    val reason: String,
    val suggestedQuestion: String? = null
)

data class LiteratureComparisonViewData(
    val targetItemId: String,
    val targetTitle: String,
    val commonPoints: List<String>,
    val differences: List<String>,
    val complementarities: List<String>,
    val projectFit: String,
    val recommendation: String
)

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val itemRepository: ItemRepository,
    private val knowledgeConnectionRepository: KnowledgeConnectionRepository,
    private val projectRepository: ProjectRepository,
    private val aiService: AIService,
    private val imageScanImportService: ImageScanImportService
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
                comparisonResult = null,
                isGeneratingComparison = false,
                insightRecommendations = emptyList(),
                isLookingUpInsightLinks = false,
                chatMessages = emptyList(),
                isAiResponding = false
            )
            val item = itemRepository.getItem(itemId)
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                item = item,
                connections = if (item != null) {
                    knowledgeConnectionRepository.getConnectionsForItem(item.id)
                } else {
                    emptyList()
                },
                availableLinkTargets = if (item?.type == ItemType.INSIGHT) {
                    itemRepository.observeItems().first()
                        .filter { candidate -> candidate.id != item.id }
                        .sortedWith(compareByDescending<ResearchItem> { candidate ->
                            calculateInsightLinkScore(item, candidate)
                        }.thenByDescending { candidate ->
                            candidate.createdAt.time
                        })
                } else {
                    emptyList()
                },
                availableComparisonTargets = if (item?.type == ItemType.PAPER || item?.type == ItemType.ARTICLE) {
                    itemRepository.observeItems().first()
                        .filter { candidate ->
                            candidate.id != item.id &&
                                (candidate.type == ItemType.PAPER || candidate.type == ItemType.ARTICLE)
                        }
                        .sortedWith(
                            compareByDescending<ResearchItem> { candidate ->
                                if (!item.projectId.isNullOrBlank() && item.projectId == candidate.projectId) 1 else 0
                            }.thenByDescending { candidate ->
                                candidate.isStarred
                            }.thenByDescending { candidate ->
                                candidate.createdAt.time
                            }
                        )
                } else {
                    emptyList()
                },
                isProjectSaving = false,
                isRegeneratingSummary = false,
                isGeneratingReadingCard = false,
                isRetryingOcr = false,
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
                    errorMessage = "生成阅读卡失败: ${error.message}"
                )
            }
        }
    }

    fun retryOcr() {
        val item = _uiState.value.item ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRetryingOcr = true, errorMessage = null)
            val result = imageScanImportService.retryImportForItem(item)
            if (result.isSuccess) {
                load(item.id)
            } else {
                _uiState.value = _uiState.value.copy(
                    isRetryingOcr = false,
                    errorMessage = "重新解析 OCR 失败: ${result.exceptionOrNull()?.message}"
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

    fun openComparisonDialog() {
        _uiState.value = _uiState.value.copy(
            isComparisonDialogVisible = true,
            comparisonResult = null,
            errorMessage = null
        )
    }

    fun closeComparisonDialog() {
        _uiState.value = _uiState.value.copy(
            isComparisonDialogVisible = false,
            isGeneratingComparison = false
        )
    }

    fun openInsightLinkEditor() {
        _uiState.value = _uiState.value.copy(isInsightLinkEditorVisible = true, errorMessage = null)
    }

    fun closeInsightLinkEditor() {
        _uiState.value = _uiState.value.copy(isInsightLinkEditorVisible = false)
    }

    fun saveInsightLinks(targetItemIds: List<String>) {
        val item = _uiState.value.item ?: return
        if (item.type != ItemType.INSIGHT) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSavingInsightLinks = true, errorMessage = null)
            val result = knowledgeConnectionRepository.replaceInsightConnections(item.id, targetItemIds)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    isSavingInsightLinks = false,
                    isInsightLinkEditorVisible = false
                )
                load(item.id)
            } else {
                _uiState.value = _uiState.value.copy(
                    isSavingInsightLinks = false,
                    errorMessage = "保存关联失败: ${result.exceptionOrNull()?.message}"
                )
            }
        }
    }

    fun lookupInsightReferences() {
        val item = _uiState.value.item ?: return
        if (item.type != ItemType.INSIGHT) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLookingUpInsightLinks = true,
                insightRecommendations = emptyList(),
                errorMessage = null
            )

            val rankedCandidates = itemRepository.observeItems().first()
                .filter { candidate ->
                    candidate.id != item.id &&
                        (candidate.type == ItemType.PAPER || candidate.type == ItemType.ARTICLE)
                }
                .sortedWith(
                    compareByDescending<ResearchItem> { candidate ->
                        calculateInsightLinkScore(item, candidate)
                    }.thenByDescending { candidate ->
                        candidate.createdAt.time
                    }
                )

            if (rankedCandidates.isEmpty()) {
                _uiState.value = _uiState.value.copy(
                    isLookingUpInsightLinks = false,
                    errorMessage = "当前没有可供反查的论文或资料"
                )
                return@launch
            }

            val candidateWindow = rankedCandidates.take(12)
            val contextDocument = item.projectId?.let { projectRepository.getProjectContextDocument(it) }
            val aiResult = aiService.recommendItemsForInsight(
                insight = item,
                candidates = candidateWindow,
                projectContextSummary = contextDocument?.summary,
                projectContextKeywords = contextDocument?.keywords.orEmpty()
            )

            val recommendations = aiResult.getOrNull()
                ?.recommendations
                ?.mapNotNull { recommendation ->
                    candidateWindow.getOrNull(recommendation.candidateIndex)?.let { candidate ->
                        InsightLookupRecommendation(
                            item = candidate,
                            reason = recommendation.reason,
                            suggestedQuestion = recommendation.suggestedQuestion
                        )
                    }
                }
                ?.distinctBy { it.item.id }
                ?.take(5)
                ?.takeIf { it.isNotEmpty() }
                ?: buildFallbackInsightRecommendations(item, candidateWindow)

            _uiState.value = _uiState.value.copy(
                isLookingUpInsightLinks = false,
                insightRecommendations = recommendations,
                errorMessage = if (recommendations.isEmpty()) "暂时没有找到合适的反查结果" else null
            )
        }
    }

    fun acceptInsightRecommendations() {
        val item = _uiState.value.item ?: return
        if (item.type != ItemType.INSIGHT) return

        val recommendedIds = _uiState.value.insightRecommendations.map { it.item.id }
        if (recommendedIds.isEmpty()) return

        val mergedTargetIds = (_uiState.value.connections.map { it.item.id } + recommendedIds).distinct()
        saveInsightLinks(mergedTargetIds)
    }

    fun compareWithItem(targetItemId: String) {
        val sourceItem = _uiState.value.item ?: return
        if (sourceItem.type != ItemType.PAPER && sourceItem.type != ItemType.ARTICLE) return

        val targetItem = _uiState.value.availableComparisonTargets.firstOrNull { it.id == targetItemId }
            ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isGeneratingComparison = true,
                comparisonResult = null,
                errorMessage = null
            )

            val contextDocument = sourceItem.projectId?.let { projectRepository.getProjectContextDocument(it) }
            val result = aiService.compareResearchItems(
                leftItem = sourceItem,
                rightItem = targetItem,
                projectContextSummary = contextDocument?.summary,
                projectContextKeywords = contextDocument?.keywords.orEmpty()
            )

            result.onSuccess { comparison ->
                _uiState.value = _uiState.value.copy(
                    isGeneratingComparison = false,
                    comparisonResult = LiteratureComparisonViewData(
                        targetItemId = targetItem.id,
                        targetTitle = targetItem.title,
                        commonPoints = comparison.commonPoints,
                        differences = comparison.differences,
                        complementarities = comparison.complementarities,
                        projectFit = comparison.projectFit,
                        recommendation = comparison.recommendation
                    )
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isGeneratingComparison = false,
                    errorMessage = "生成文献对比失败: ${error.message}"
                )
            }
        }
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
                        content = "我暂时还回答不了这个问题：${error.message ?: "未知错误"}"
                    ),
                    isAiResponding = false,
                    errorMessage = "AI 回答失败: ${error.message}"
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
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

    private fun calculateInsightLinkScore(
        insight: ResearchItem,
        candidate: ResearchItem
    ): Int {
        var score = 0

        if (!insight.projectId.isNullOrBlank() && insight.projectId == candidate.projectId) {
            score += 6
        }

        if (candidate.type == ItemType.PAPER || candidate.type == ItemType.ARTICLE) {
            score += 3
        }

        val insightText = buildSearchTokens(insight)
        val candidateText = buildSearchTokens(candidate)
        val overlapCount = insightText.intersect(candidateText).size
        score += overlapCount.coerceAtMost(6)

        return score
    }

    private fun buildFallbackInsightRecommendations(
        insight: ResearchItem,
        candidates: List<ResearchItem>
    ): List<InsightLookupRecommendation> {
        return candidates
            .take(3)
            .map { candidate ->
                val reason = when {
                    !insight.projectId.isNullOrBlank() && insight.projectId == candidate.projectId ->
                        "和这条灵感属于同一项目，适合优先确认是否能直接支撑当前思路"

                    candidate.type == ItemType.PAPER ->
                        "关键词与摘要存在重合，适合先从论文视角验证这条灵感"

                    else ->
                        "内容主题与这条灵感较接近，适合补充背景和已有观点"
                }
                InsightLookupRecommendation(
                    item = candidate,
                    reason = reason,
                    suggestedQuestion = "这条资料里有没有能直接支持当前灵感的证据、方法或反例？"
                )
            }
    }

    private fun buildSearchTokens(item: ResearchItem): Set<String> {
        val metaKeywords = when (val meta = item.metaData) {
            is ItemMetaData.PaperMeta -> meta.keywords + meta.domainTags + meta.methodTags + meta.tags
            is ItemMetaData.ArticleMeta -> meta.keywords + meta.topicTags + meta.corePoints
            else -> emptyList()
        }

        return (
            listOf(item.title, item.summary, item.contentMarkdown, item.projectName.orEmpty()) + metaKeywords
            )
            .flatMap { text ->
                text.lowercase()
                    .replace(Regex("[^\\p{L}\\p{N}\\u4e00-\\u9fa5]+"), " ")
                    .split(" ")
                    .map(String::trim)
                    .filter { it.length >= 2 }
            }
            .toSet()
    }
}

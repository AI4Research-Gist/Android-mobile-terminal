package com.example.ai4research.ui.main

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai4research.data.repository.AuthRepository
import com.example.ai4research.domain.model.ItemMetaData
import com.example.ai4research.domain.model.ItemStatus
import com.example.ai4research.domain.model.ItemType
import com.example.ai4research.domain.model.Project
import com.example.ai4research.domain.model.ReadStatus
import com.example.ai4research.domain.model.ResearchItem
import com.example.ai4research.domain.repository.ItemRepository
import com.example.ai4research.domain.repository.ProjectRepository
import com.example.ai4research.service.FloatingWindowManager
import com.example.ai4research.service.ImageScanImportService
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject

enum class FilterType {
    ALL,
    UNREAD,
    STARRED,
    PROJECT
}

data class SyncDiagnostics(
    val currentUserId: String? = null,
    val currentUsername: String? = null,
    val currentEmail: String? = null,
    val localPaperCount: Int = 0,
    val localArticleCount: Int = 0,
    val localCompetitionCount: Int = 0,
    val localInsightCount: Int = 0,
    val localVoiceCount: Int = 0,
    val localProjectCount: Int = 0,
    val localUnsyncedItemCount: Int = 0,
    val localRetryableUnsyncedCount: Int = 0,
    val localProcessingUnsyncedCount: Int = 0,
    val lastSyncStatus: String = "idle",
    val lastSyncError: String? = null,
    val lastSyncAt: Long? = null
)

data class ResearchReviewDigest(
    val weeklyNewItemCount: Int = 0,
    val weeklyNewPaperCount: Int = 0,
    val weeklyNewInsightCount: Int = 0,
    val recentHighlights: List<String> = emptyList(),
    val unreadPaperCount: Int = 0,
    val unreadArticleCount: Int = 0,
    val staleUnreadHighlights: List<String> = emptyList(),
    val urgentCompetitionHighlights: List<String> = emptyList(),
    val mostActiveProjectName: String? = null,
    val generatedAt: Long? = null
)

data class RetryUnsyncedResult(
    val attemptedCount: Int,
    val successCount: Int,
    val failureCount: Int,
    val firstFailureMessage: String? = null
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val itemRepository: ItemRepository,
    private val projectRepository: ProjectRepository,
    private val imageScanImportService: ImageScanImportService,
    val floatingWindowManager: FloatingWindowManager
) : ViewModel() {

    private val _papers = MutableStateFlow<List<ResearchItem>>(emptyList())
    val papers: StateFlow<List<ResearchItem>> = _papers.asStateFlow()

    private val _articles = MutableStateFlow<List<ResearchItem>>(emptyList())
    val articles: StateFlow<List<ResearchItem>> = _articles.asStateFlow()

    private val _competitions = MutableStateFlow<List<ResearchItem>>(emptyList())
    val competitions: StateFlow<List<ResearchItem>> = _competitions.asStateFlow()

    private val _insights = MutableStateFlow<List<ResearchItem>>(emptyList())
    val insights: StateFlow<List<ResearchItem>> = _insights.asStateFlow()

    private val _voiceItems = MutableStateFlow<List<ResearchItem>>(emptyList())
    val voiceItems: StateFlow<List<ResearchItem>> = _voiceItems.asStateFlow()

    private val _projects = MutableStateFlow<List<Project>>(emptyList())
    val projects: StateFlow<List<Project>> = _projects.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _currentFilter = MutableStateFlow(FilterType.ALL)
    val currentFilter: StateFlow<FilterType> = _currentFilter.asStateFlow()

    private val _currentProjectId = MutableStateFlow<String?>(null)
    val currentProjectId: StateFlow<String?> = _currentProjectId.asStateFlow()

    private val _syncDiagnostics = MutableStateFlow(SyncDiagnostics())
    val syncDiagnostics: StateFlow<SyncDiagnostics> = _syncDiagnostics.asStateFlow()

    private val _researchReview = MutableStateFlow(ResearchReviewDigest())
    val researchReview: StateFlow<ResearchReviewDigest> = _researchReview.asStateFlow()

    private val gson = Gson()

    private var papersCollectionJob: Job? = null
    private var articlesCollectionJob: Job? = null
    private var competitionsCollectionJob: Job? = null
    private var insightsCollectionJob: Job? = null
    private var voiceCollectionJob: Job? = null

    init {
        refreshDiagnostics()
        fetchData()
        observeProjects()
        applyFilter(FilterType.ALL, null)
    }

    private fun observeProjects() {
        viewModelScope.launch {
            projectRepository.observeProjects().collect { projectList ->
                android.util.Log.d("MainViewModel", "Projects observed: ${projectList.size}")
                _projects.value = projectList
                refreshDiagnostics()
            }
        }
    }

    fun applyFilter(filterType: FilterType, projectId: String? = null) {
        _currentFilter.value = filterType
        _currentProjectId.value = projectId

        papersCollectionJob?.cancel()
        articlesCollectionJob?.cancel()
        competitionsCollectionJob?.cancel()
        insightsCollectionJob?.cancel()
        voiceCollectionJob?.cancel()

        android.util.Log.d("MainViewModel", "Applying filter: $filterType, projectId=$projectId")

        papersCollectionJob = viewModelScope.launch {
            val flow = when (filterType) {
                FilterType.ALL -> itemRepository.observeItems(type = ItemType.PAPER, query = null)
                FilterType.UNREAD -> itemRepository.observeItemsByReadStatus(ItemType.PAPER, ReadStatus.UNREAD)
                FilterType.STARRED -> itemRepository.observeStarredItems(ItemType.PAPER)
                FilterType.PROJECT -> {
                    if (projectId != null) itemRepository.observeItemsByProject(ItemType.PAPER, projectId)
                    else itemRepository.observeItems(type = ItemType.PAPER, query = null)
                }
            }
            flow.collect { items ->
                android.util.Log.d("MainViewModel", "Papers filtered: ${items.size} items")
                _papers.value = items
                refreshDiagnostics()
            }
        }

        articlesCollectionJob = viewModelScope.launch {
            itemRepository.observeItems(type = ItemType.ARTICLE, query = null).collect { items ->
                android.util.Log.d("MainViewModel", "Articles observed: ${items.size} items")
                _articles.value = items
                refreshDiagnostics()
            }
        }

        competitionsCollectionJob = viewModelScope.launch {
            val flow = when (filterType) {
                FilterType.ALL -> itemRepository.observeItems(type = ItemType.COMPETITION, query = null)
                FilterType.UNREAD -> itemRepository.observeItemsByReadStatus(ItemType.COMPETITION, ReadStatus.UNREAD)
                FilterType.STARRED -> itemRepository.observeStarredItems(ItemType.COMPETITION)
                FilterType.PROJECT -> {
                    if (projectId != null) itemRepository.observeItemsByProject(ItemType.COMPETITION, projectId)
                    else itemRepository.observeItems(type = ItemType.COMPETITION, query = null)
                }
            }
            flow.collect { items ->
                android.util.Log.d("MainViewModel", "Competitions filtered: ${items.size} items")
                _competitions.value = items
                refreshDiagnostics()
            }
        }

        insightsCollectionJob = viewModelScope.launch {
            itemRepository.observeItems(type = ItemType.INSIGHT, query = null).collect { items ->
                android.util.Log.d("MainViewModel", "Insights observed: ${items.size} items")
                _insights.value = items
                refreshDiagnostics()
            }
        }

        voiceCollectionJob = viewModelScope.launch {
            itemRepository.observeItems(type = ItemType.VOICE, query = null).collect { items ->
                android.util.Log.d("MainViewModel", "Voice items observed: ${items.size} items")
                _voiceItems.value = items
                refreshDiagnostics()
            }
        }
    }

    fun fetchData() {
        viewModelScope.launch {
            _isLoading.value = true
            _syncDiagnostics.value = _syncDiagnostics.value.copy(
                lastSyncStatus = "synchronizing",
                lastSyncError = null
            )
            try {
                val result = itemRepository.refreshItems()
                if (result.isSuccess) {
                    android.util.Log.d("MainViewModel", "Data refresh successful")
                    _syncDiagnostics.value = _syncDiagnostics.value.copy(
                        lastSyncStatus = "success",
                        lastSyncError = null,
                        lastSyncAt = System.currentTimeMillis()
                    )
                } else {
                    android.util.Log.e("MainViewModel", "Data refresh failed: ${result.exceptionOrNull()?.message}")
                    _syncDiagnostics.value = _syncDiagnostics.value.copy(
                        lastSyncStatus = "failure",
                        lastSyncError = result.exceptionOrNull()?.message,
                        lastSyncAt = System.currentTimeMillis()
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Data refresh exception: ${e.message}", e)
                _syncDiagnostics.value = _syncDiagnostics.value.copy(
                    lastSyncStatus = "failure",
                    lastSyncError = e.message,
                    lastSyncAt = System.currentTimeMillis()
                )
            } finally {
                _isLoading.value = false
                refreshDiagnostics()
            }
        }
    }

    fun search(query: String) {
        _searchQuery.value = query
        papersCollectionJob?.cancel()
        articlesCollectionJob?.cancel()
        competitionsCollectionJob?.cancel()

        papersCollectionJob = viewModelScope.launch {
            itemRepository.observeItems(type = ItemType.PAPER, query = query).collect { items ->
                _papers.value = items
                refreshDiagnostics()
            }
        }
        competitionsCollectionJob = viewModelScope.launch {
            itemRepository.observeItems(type = ItemType.COMPETITION, query = query).collect { items ->
                _competitions.value = items
                refreshDiagnostics()
            }
        }
        articlesCollectionJob = viewModelScope.launch {
            itemRepository.observeItems(type = ItemType.ARTICLE, query = query).collect { items ->
                _articles.value = items
                refreshDiagnostics()
            }
        }
    }

    fun deleteItem(itemId: String) {
        viewModelScope.launch {
            try {
                val result = itemRepository.deleteItem(itemId)
                if (result.isSuccess) {
                    android.util.Log.d("MainViewModel", "Item deleted successfully: $itemId")
                } else {
                    android.util.Log.e("MainViewModel", "Delete failed: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Delete exception: ${e.message}", e)
            } finally {
                refreshDiagnostics()
            }
        }
    }

    suspend fun createProject(name: String, description: String? = null): Result<Project> {
        val cleanName = name.trim()
        if (cleanName.isBlank()) {
            return Result.failure(IllegalArgumentException("项目名称不能为空"))
        }
        val cleanDescription = description?.trim()?.takeIf { it.isNotBlank() }
        val result = projectRepository.createProject(cleanName, cleanDescription)
        if (result.isSuccess) {
            refreshDiagnostics()
        }
        return result
    }

    suspend fun saveInsight(
        id: String?,
        title: String,
        body: String,
        imageUri: String?,
        audioUri: String?,
        categoryId: String?,
        categoryName: String?,
        readStatus: ReadStatus,
        audioDurationSeconds: Int = 0
    ): Result<ResearchItem> {
        val cleanTitle = title.trim()
        if (cleanTitle.isBlank()) {
            return Result.failure(IllegalArgumentException("Title is required"))
        }

        val cleanBody = body.trim()
        val summary = buildInsightSummary(cleanBody, imageUri, audioUri)
        val metaJson = gson.toJson(
            buildMap<String, Any?> {
                put("source", "鐏垫劅")
                put("body", cleanBody)
                put("category_id", categoryId?.takeIf { it.isNotBlank() })
                put("category_name", categoryName?.takeIf { it.isNotBlank() })
                put("image_uri", imageUri)
                put("audio_uri", audioUri)
                put("audio_duration", audioDurationSeconds)
                put("has_image", !imageUri.isNullOrBlank())
                put("has_audio", !audioUri.isNullOrBlank())
            }
        )

        val saveResult = if (id.isNullOrBlank()) {
            itemRepository.createFullItem(
                title = cleanTitle,
                summary = summary,
                contentMd = cleanBody,
                originUrl = imageUri,
                type = ItemType.INSIGHT,
                status = ItemStatus.DONE,
                metaJson = metaJson,
                tags = emptyList(),
                audioUrl = audioUri
            )
        } else {
            itemRepository.updateItem(
                id = id,
                title = cleanTitle,
                summary = summary,
                content = cleanBody,
                originUrl = imageUri ?: "",
                tags = emptyList(),
                metaJson = metaJson,
                status = ItemStatus.DONE,
                audioUrl = audioUri ?: ""
            ).mapCatching {
                itemRepository.getItem(id) ?: error("Item updated but not found")
            }
        }

        val finalResult = saveResult.fold(
            onSuccess = { saved ->
                if (saved.readStatus == readStatus) {
                    Result.success(saved)
                } else {
                    itemRepository.updateReadStatus(saved.id, readStatus)
                        .mapCatching { itemRepository.getItem(saved.id) ?: saved.copy(readStatus = readStatus) }
                }
            },
            onFailure = { Result.failure(it) }
        )
        refreshDiagnostics()
        return finalResult
    }

    suspend fun updateInsightReadStatus(id: String, readStatus: ReadStatus): Result<Unit> {
        return itemRepository.updateReadStatus(id, readStatus)
    }

    fun importScannedImages(
        imageUris: List<Uri>,
        selectedType: ItemType,
        projectId: String?,
        projectName: String?,
        onQueued: (Result<ResearchItem>) -> Unit = {},
        onFinished: (Result<ResearchItem>) -> Unit = {}
    ) {
        viewModelScope.launch {
            val queuedResult = imageScanImportService.queueImport(
                imageUris = imageUris,
                selectedType = selectedType,
                projectId = projectId,
                projectName = projectName,
                captureMode = "gallery"
            )

            queuedResult.fold(
                onSuccess = { queued ->
                    onQueued(Result.success(queued.item))
                    val finalResult = imageScanImportService.processQueuedImport(queued)
                    onFinished(finalResult)
                },
                onFailure = { error ->
                    val failure = Result.failure<ResearchItem>(error)
                    onQueued(failure)
                    onFinished(failure)
                }
            )
            refreshDiagnostics()
        }
    }

    fun getPapersJson(): String {
        val paperDtos = _papers.value.map { item ->
            mapOf(
                "Id" to item.id,
                "id" to item.id,
                "title" to item.title,
                "type" to item.type.toServerString(),
                "summary" to item.summary,
                "content_md" to item.contentMarkdown,
                "origin_url" to item.originUrl,
                "audio_url" to item.audioUrl,
                "note" to item.note,
                "status" to item.status.toServerString(),
                "read_status" to item.readStatus.toServerString(),
                "project_id" to item.projectId,
                "project_name" to item.projectName,
                "tags" to (item.metaData as? ItemMetaData.PaperMeta)
                    ?.keywords
                    ?.ifEmpty { (item.metaData as? ItemMetaData.PaperMeta)?.tags ?: emptyList() }
                    ?.joinToString(","),
                "meta_json" to (item.rawMetaJson ?: serializeMetaData(item)),
                "CreatedAt" to item.createdAt.toString(),
                "UpdatedAt" to item.createdAt.toString()
            )
        }
        return gson.toJson(paperDtos)
    }

    fun getCompetitionsJson(): String {
        val compDtos = _competitions.value.map { item ->
            val competitionMeta = item.metaData as? ItemMetaData.CompetitionMeta
            val timelineSummary = CompetitionTimelineFormatter.summarize(competitionMeta)
            mapOf(
                "Id" to item.id,
                "id" to item.id,
                "title" to item.title,
                "type" to item.type.toServerString(),
                "summary" to item.summary,
                "content_md" to item.contentMarkdown,
                "origin_url" to item.originUrl,
                "audio_url" to item.audioUrl,
                "note" to item.note,
                "status" to item.status.toServerString(),
                "read_status" to item.readStatus.toServerString(),
                "project_id" to item.projectId,
                "project_name" to item.projectName,
                "organizer" to competitionMeta?.organizer,
                "deadline" to competitionMeta?.deadline,
                "urgency_text" to timelineSummary.displayText,
                "urgency_days" to timelineSummary.daysDelta,
                "urgency_anchor" to timelineSummary.anchorName,
                "is_overdue" to timelineSummary.isOverdue,
                "sort_key" to timelineSummary.sortKey,
                "meta_json" to serializeMetaData(item),
                "CreatedAt" to item.createdAt.toString(),
                "UpdatedAt" to item.createdAt.toString()
            )
        }
        return gson.toJson(compDtos)
    }

    fun getArticlesJson(): String {
        val articleDtos = _articles.value.map { item ->
            val articleMeta = item.metaData as? ItemMetaData.ArticleMeta
            mapOf(
                "Id" to item.id,
                "id" to item.id,
                "title" to item.title,
                "type" to item.type.toServerString(),
                "summary" to item.summary,
                "content_md" to item.contentMarkdown,
                "origin_url" to item.originUrl,
                "note" to item.note,
                "status" to item.status.toServerString(),
                "read_status" to item.readStatus.toServerString(),
                "project_id" to item.projectId,
                "project_name" to item.projectName,
                "meta_json" to serializeMetaData(item),
                "platform" to articleMeta?.platform,
                "account_name" to articleMeta?.accountName,
                "author" to articleMeta?.author,
                "publish_date" to articleMeta?.publishDate,
                "CreatedAt" to item.createdAt.toString(),
                "UpdatedAt" to item.createdAt.toString()
            )
        }
        return gson.toJson(articleDtos)
    }

    fun getInsightsJson(): String {
        val insightDtos = _insights.value.map { item ->
            mapOf(
                "Id" to item.id,
                "id" to item.id,
                "title" to item.title,
                "type" to item.type.toServerString(),
                "summary" to item.summary,
                "content_md" to item.contentMarkdown,
                "origin_url" to item.originUrl,
                "audio_url" to item.audioUrl,
                "note" to item.note,
                "status" to item.status.toServerString(),
                "read_status" to item.readStatus.toServerString(),
                "project_id" to item.projectId,
                "project_name" to item.projectName,
                "meta_json" to (item.rawMetaJson ?: serializeMetaData(item)),
                "CreatedAt" to item.createdAt.toString(),
                "UpdatedAt" to item.createdAt.toString()
            )
        }
        return gson.toJson(insightDtos)
    }

    fun getProjectsJson(): String {
        val projectDtos = _projects.value.map { project ->
            mapOf(
                "id" to project.id,
                "name" to project.name,
                "description" to project.description
            )
        }
        return gson.toJson(projectDtos)
    }

    fun getVoiceItemsJson(): String {
        val voiceDtos = _voiceItems.value.map { item ->
            val voiceMeta = item.metaData as? ItemMetaData.VoiceMeta
            mapOf(
                "Id" to item.id,
                "id" to item.id,
                "title" to item.title,
                "type" to item.type.toServerString(),
                "summary" to item.summary,
                "content_md" to item.contentMarkdown,
                "audio_url" to item.audioUrl,
                "note" to item.note,
                "status" to item.status.toServerString(),
                "read_status" to item.readStatus.toServerString(),
                "project_id" to item.projectId,
                "project_name" to item.projectName,
                "meta_json" to gson.toJson(
                    mapOf(
                        "duration" to (voiceMeta?.duration ?: 0),
                        "transcription" to (voiceMeta?.transcription ?: item.summary),
                        "note" to item.note
                    )
                ),
                "CreatedAt" to item.createdAt.toString(),
                "UpdatedAt" to item.createdAt.toString()
            )
        }
        return gson.toJson(voiceDtos)
    }

    fun getSyncDiagnosticsJson(): String = gson.toJson(_syncDiagnostics.value)

    fun getResearchReviewJson(): String = gson.toJson(_researchReview.value)

    suspend fun retryUnsyncedItems(): Result<RetryUnsyncedResult> {
        val allItems = itemRepository.observeItems().first()
        val retryTargets = allItems.filter { item ->
            item.id.toIntOrNull() == null && (item.status == ItemStatus.DONE || item.status == ItemStatus.FAILED)
        }

        var successCount = 0
        var failureCount = 0
        var firstFailureMessage: String? = null

        retryTargets.forEach { item ->
            val result = itemRepository.syncLocalItemToRemote(item.id)
            if (result.isSuccess) {
                successCount += 1
            } else {
                failureCount += 1
                if (firstFailureMessage == null) {
                    firstFailureMessage = result.exceptionOrNull()?.message
                }
            }
        }

        refreshDiagnostics()
        return Result.success(
            RetryUnsyncedResult(
                attemptedCount = retryTargets.size,
                successCount = successCount,
                failureCount = failureCount,
                firstFailureMessage = firstFailureMessage
            )
        )
    }

    private fun buildInsightSummary(
        body: String,
        imageUri: String?,
        audioUri: String?
    ): String {
        if (body.isNotBlank()) {
            return body.replace('\n', ' ').trim().take(120)
        }

        val parts = mutableListOf<String>()
        if (!imageUri.isNullOrBlank()) parts += "鍚浘鐗?"
        if (!audioUri.isNullOrBlank()) parts += "鍚闊?"
        return if (parts.isEmpty()) "鏆傛棤姝ｆ枃" else parts.joinToString(" 路 ")
    }

    private fun serializeMetaData(item: ResearchItem): String {
        val existingMeta = runCatching {
            gson.fromJson(item.rawMetaJson ?: "{}", MutableMap::class.java) as? MutableMap<String, Any?>
        }.getOrNull() ?: mutableMapOf()

        val mergedMeta = when (val meta = item.metaData) {
            is ItemMetaData.PaperMeta -> existingMeta.apply {
                this["authors"] = meta.authors
                this["conference"] = meta.conference
                this["year"] = meta.year?.toString()
                this["source"] = meta.source
                this["identifier"] = meta.identifier
                this["domain_tags"] = meta.domainTags
                this["keywords"] = meta.keywords
                this["method_tags"] = meta.methodTags
                this["dedup_key"] = meta.dedupKey
                this["summary_short"] = meta.summaryShort
                this["medium_summary"] = meta.mediumSummary
                this["summary_en"] = meta.summaryEn
                this["summary_zh"] = meta.summaryZh
                this["tags"] = meta.tags
                this["reading_card"] = meta.readingCard?.let { card ->
                    mapOf(
                        "research_question" to card.researchQuestion,
                        "method" to card.method,
                        "dataset" to card.dataset,
                        "findings" to card.findings,
                        "limitations" to card.limitations,
                        "reuse_points" to card.reusePoints,
                        "my_notes" to card.myNotes
                    )
                }
                this["note"] = item.note
            }

            is ItemMetaData.CompetitionMeta -> existingMeta.apply {
                this["organizer"] = meta.organizer
                this["prizePool"] = meta.prizePool
                this["deadline"] = meta.deadline ?: meta.timeline?.firstOrNull()?.date?.toString()
                this["theme"] = meta.theme
                this["competitionType"] = meta.competitionType
                this["website"] = meta.website
                this["registrationUrl"] = meta.registrationUrl
                this["timeline"] = meta.timeline?.map { event ->
                    mapOf(
                        "name" to event.name,
                        "date" to event.date.toInstant().toString(),
                        "isPassed" to event.isPassed
                    )
                }
            }

            is ItemMetaData.ArticleMeta -> existingMeta.apply {
                this["platform"] = meta.platform
                this["account_name"] = meta.accountName
                this["author"] = meta.author
                this["publish_date"] = meta.publishDate
                this["identifier"] = meta.identifier
                this["summary_short"] = meta.summaryShort
                this["medium_summary"] = meta.mediumSummary
                this["keywords"] = meta.keywords
                this["topic_tags"] = meta.topicTags
                this["core_points"] = meta.corePoints
                this["referenced_links"] = meta.referencedLinks
                this["paper_candidates"] = meta.paperCandidates.map { candidate ->
                    mapOf(
                        "url" to candidate.url,
                        "label" to candidate.label,
                        "kind" to candidate.kind
                    )
                }
                this["reading_card"] = meta.readingCard?.let { card ->
                    mapOf(
                        "research_question" to card.researchQuestion,
                        "method" to card.method,
                        "dataset" to card.dataset,
                        "findings" to card.findings,
                        "limitations" to card.limitations,
                        "reuse_points" to card.reusePoints,
                        "my_notes" to card.myNotes
                    )
                }
                this["note"] = item.note
            }

            is ItemMetaData.InsightMeta -> existingMeta.apply {
                this["source"] = "鐏垫劅"
                this["tags"] = meta.tags
                this["note"] = item.note
            }

            is ItemMetaData.VoiceMeta -> existingMeta.apply {
                this["duration"] = meta.duration
                this["transcription"] = meta.transcription
                this["note"] = item.note
            }

            else -> existingMeta.ifEmpty { null }
        }

        return if (mergedMeta == null || mergedMeta.isEmpty()) {
            item.rawMetaJson ?: "{}"
        } else {
            gson.toJson(mergedMeta)
        }
    }

    private fun refreshDiagnostics() {
        viewModelScope.launch {
            val currentUser = authRepository.getCurrentUser()
            val allItems = runCatching { itemRepository.observeItems().first() }.getOrDefault(emptyList())
            val unsyncedItems = allItems.filter { it.id.toIntOrNull() == null }
            val retryableUnsynced = unsyncedItems.count { item ->
                item.status == ItemStatus.DONE || item.status == ItemStatus.FAILED
            }
            val processingUnsynced = unsyncedItems.count { item ->
                item.status == ItemStatus.PROCESSING
            }
            _syncDiagnostics.value = _syncDiagnostics.value.copy(
                currentUserId = currentUser?.id,
                currentUsername = currentUser?.username,
                currentEmail = currentUser?.email,
                localPaperCount = _papers.value.size,
                localArticleCount = _articles.value.size,
                localCompetitionCount = _competitions.value.size,
                localInsightCount = _insights.value.size,
                localVoiceCount = _voiceItems.value.size,
                localProjectCount = _projects.value.size,
                localUnsyncedItemCount = unsyncedItems.size,
                localRetryableUnsyncedCount = retryableUnsynced,
                localProcessingUnsyncedCount = processingUnsynced
            )

            _researchReview.value = buildResearchReviewDigest(allItems)
        }
    }

    private fun buildResearchReviewDigest(allItems: List<ResearchItem>): ResearchReviewDigest {
        val now = Instant.now()
        val weekAgo = now.minus(7, ChronoUnit.DAYS)
        val zoneId = ZoneId.systemDefault()

        val weeklyItems = allItems.filter { item ->
            item.createdAt.toInstant().isAfter(weekAgo)
        }
        val recentHighlights = weeklyItems
            .sortedByDescending { it.createdAt.time }
            .take(5)
            .map { "${itemTypeLabel(it.type)}：${it.title}" }

        val unreadPapers = allItems.filter { it.type == ItemType.PAPER && it.readStatus != ReadStatus.READ }
        val unreadArticles = allItems.filter { it.type == ItemType.ARTICLE && it.readStatus != ReadStatus.READ }

        val staleUnreadHighlights = (unreadPapers + unreadArticles)
            .filter { item ->
                item.createdAt.toInstant().isBefore(weekAgo)
            }
            .sortedBy { it.createdAt.time }
            .take(4)
            .map { "${itemTypeLabel(it.type)}：${it.title}" }

        val urgentCompetitionHighlights = allItems
            .filter { it.type == ItemType.COMPETITION }
            .mapNotNull { item ->
                val meta = item.metaData as? ItemMetaData.CompetitionMeta ?: return@mapNotNull null
                val summary = CompetitionTimelineFormatter.summarize(
                    meta = meta,
                    now = now,
                    zoneId = zoneId
                )
                if (summary.displayText.isBlank() || summary.isOverdue || summary.daysDelta > 14) {
                    return@mapNotNull null
                }
                "${item.title} · ${summary.displayText}"
            }
            .take(4)

        val mostActiveProjectName = weeklyItems
            .mapNotNull { it.projectName?.takeIf(String::isNotBlank) }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key

        return ResearchReviewDigest(
            weeklyNewItemCount = weeklyItems.size,
            weeklyNewPaperCount = weeklyItems.count { it.type == ItemType.PAPER },
            weeklyNewInsightCount = weeklyItems.count { it.type == ItemType.INSIGHT },
            recentHighlights = recentHighlights,
            unreadPaperCount = unreadPapers.size,
            unreadArticleCount = unreadArticles.size,
            staleUnreadHighlights = staleUnreadHighlights,
            urgentCompetitionHighlights = urgentCompetitionHighlights,
            mostActiveProjectName = mostActiveProjectName,
            generatedAt = System.currentTimeMillis()
        )
    }

    private fun itemTypeLabel(type: ItemType): String = when (type) {
        ItemType.PAPER -> "论文"
        ItemType.ARTICLE -> "资料"
        ItemType.COMPETITION -> "比赛"
        ItemType.INSIGHT -> "灵感"
        ItemType.VOICE -> "语音"
    }
}

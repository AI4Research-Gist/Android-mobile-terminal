package com.example.ai4research.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai4research.domain.model.ItemType
import com.example.ai4research.domain.model.Project
import com.example.ai4research.domain.model.ReadStatus
import com.example.ai4research.domain.model.ResearchItem
import com.example.ai4research.domain.repository.ItemRepository
import com.example.ai4research.domain.repository.ProjectRepository
import com.example.ai4research.service.FloatingWindowManager
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 筛选类型枚举
 */
enum class FilterType {
    ALL,        // 全部
    UNREAD,     // 未读
    STARRED,    // 标星 (暂时用 read 状态表示)
    PROJECT     // 按项目筛选
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val itemRepository: ItemRepository,
    private val projectRepository: ProjectRepository,
    val floatingWindowManager: FloatingWindowManager
) : ViewModel() {

    private val _papers = MutableStateFlow<List<ResearchItem>>(emptyList())
    val papers: StateFlow<List<ResearchItem>> = _papers.asStateFlow()

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

    private val gson = Gson()
    
    private var papersCollectionJob: Job? = null
    private var competitionsCollectionJob: Job? = null
    private var insightsCollectionJob: Job? = null
    private var voiceCollectionJob: Job? = null

    init {
        fetchData()
        observeProjects()
        applyFilter(FilterType.ALL, null)
    }
    
    private fun observeProjects() {
        viewModelScope.launch {
            projectRepository.observeProjects().collect { projectList ->
                android.util.Log.d("MainViewModel", "Projects observed: ${projectList.size}")
                _projects.value = projectList
            }
        }
    }
    
    /**
     * 应用筛选条件
     */
    fun applyFilter(filterType: FilterType, projectId: String? = null) {
        _currentFilter.value = filterType
        _currentProjectId.value = projectId
        
        // 取消之前的收集任务
        papersCollectionJob?.cancel()
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
                    if (projectId != null) {
                        itemRepository.observeItemsByProject(ItemType.PAPER, projectId)
                    } else {
                        itemRepository.observeItems(type = ItemType.PAPER, query = null)
                    }
                }
            }
            flow.collect { items ->
                android.util.Log.d("MainViewModel", "Papers filtered: ${items.size} items")
                _papers.value = items
            }
        }
        
        competitionsCollectionJob = viewModelScope.launch {
            val flow = when (filterType) {
                FilterType.ALL -> itemRepository.observeItems(type = ItemType.COMPETITION, query = null)
                FilterType.UNREAD -> itemRepository.observeItemsByReadStatus(ItemType.COMPETITION, ReadStatus.UNREAD)
                FilterType.STARRED -> itemRepository.observeStarredItems(ItemType.COMPETITION)
                FilterType.PROJECT -> {
                    if (projectId != null) {
                        itemRepository.observeItemsByProject(ItemType.COMPETITION, projectId)
                    } else {
                        itemRepository.observeItems(type = ItemType.COMPETITION, query = null)
                    }
                }
            }
            flow.collect { items ->
                android.util.Log.d("MainViewModel", "Competitions filtered: ${items.size} items")
                _competitions.value = items
            }
        }
        
        // 同时观察 insights 数据
        insightsCollectionJob = viewModelScope.launch {
            itemRepository.observeItems(type = ItemType.INSIGHT, query = null).collect { items ->
                android.util.Log.d("MainViewModel", "Insights observed: ${items.size} items")
                _insights.value = items
            }
        }
        
        // 同时观察 voice 数据
        voiceCollectionJob = viewModelScope.launch {
            itemRepository.observeItems(type = ItemType.VOICE, query = null).collect { items ->
                android.util.Log.d("MainViewModel", "Voice items observed: ${items.size} items")
                _voiceItems.value = items
            }
        }
    }

    fun fetchData() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // 从远程同步数据到本地数据库
                val result = itemRepository.refreshItems()
                if (result.isSuccess) {
                    android.util.Log.d("MainViewModel", "Data refresh successful")
                } else {
                    android.util.Log.e("MainViewModel", "Data refresh failed: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Data refresh exception: ${e.message}", e)
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun search(query: String) {
        _searchQuery.value = query
        papersCollectionJob?.cancel()
        competitionsCollectionJob?.cancel()
        
        papersCollectionJob = viewModelScope.launch {
            itemRepository.observeItems(type = ItemType.PAPER, query = query).collect { items ->
                _papers.value = items
            }
        }
        competitionsCollectionJob = viewModelScope.launch {
            itemRepository.observeItems(type = ItemType.COMPETITION, query = query).collect { items ->
                _competitions.value = items
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
            }
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
                "status" to item.status.toServerString(),
                "read_status" to item.readStatus.toServerString(),
                "project_id" to item.projectId,
                "project_name" to item.projectName,
                "tags" to (item.metaData as? com.example.ai4research.domain.model.ItemMetaData.PaperMeta)?.tags?.joinToString(","),
                "meta_json" to serializeMetaData(item),
                "CreatedAt" to item.createdAt.toString(),
                "UpdatedAt" to item.createdAt.toString()
            )
        }
        return gson.toJson(paperDtos)
    }

    fun getCompetitionsJson(): String {
        val compDtos = _competitions.value.map { item ->
            mapOf(
                "Id" to item.id,
                "id" to item.id,
                "title" to item.title,
                "type" to item.type.toServerString(),
                "summary" to item.summary,
                "content_md" to item.contentMarkdown,
                "origin_url" to item.originUrl,
                "audio_url" to item.audioUrl,
                "status" to item.status.toServerString(),
                "read_status" to item.readStatus.toServerString(),
                "project_id" to item.projectId,
                "project_name" to item.projectName,
                "tags" to (item.metaData as? com.example.ai4research.domain.model.ItemMetaData.CompetitionMeta)?.organizer,
                "meta_json" to serializeMetaData(item),
                "CreatedAt" to item.createdAt.toString(),
                "UpdatedAt" to item.createdAt.toString()
            )
        }
        return gson.toJson(compDtos)
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
                "status" to item.status.toServerString(),
                "read_status" to item.readStatus.toServerString(),
                "project_id" to item.projectId,
                "project_name" to item.projectName,
                "meta_json" to serializeMetaData(item),
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
            val voiceMeta = item.metaData as? com.example.ai4research.domain.model.ItemMetaData.VoiceMeta
            mapOf(
                "Id" to item.id,
                "id" to item.id,
                "title" to item.title,
                "type" to item.type.toServerString(),
                "summary" to item.summary,
                "content_md" to item.contentMarkdown,
                "audio_url" to item.audioUrl,
                "status" to item.status.toServerString(),
                "read_status" to item.readStatus.toServerString(),
                "project_id" to item.projectId,
                "project_name" to item.projectName,
                "meta_json" to gson.toJson(mapOf(
                    "duration" to (voiceMeta?.duration ?: 0),
                    "transcription" to (voiceMeta?.transcription ?: item.summary)
                )),
                "CreatedAt" to item.createdAt.toString(),
                "UpdatedAt" to item.createdAt.toString()
            )
        }
        return gson.toJson(voiceDtos)
    }

    private fun serializeMetaData(item: ResearchItem): String {
        // 如果有解析后的 metaData，序列化它
        val serialized = when (val meta = item.metaData) {
            is com.example.ai4research.domain.model.ItemMetaData.PaperMeta -> {
                gson.toJson(mapOf(
                    "authors" to meta.authors,
                    "conference" to meta.conference,
                    "year" to meta.year?.toString(),
                    "tags" to meta.tags
                ))
            }
            is com.example.ai4research.domain.model.ItemMetaData.CompetitionMeta -> {
                gson.toJson(mapOf(
                    "organizer" to meta.organizer,
                    "prizePool" to meta.prizePool,
                    "deadline" to (meta.deadline ?: meta.timeline?.firstOrNull()?.date?.toString()),
                    "theme" to meta.theme,
                    "competitionType" to meta.competitionType
                ))
            }
            is com.example.ai4research.domain.model.ItemMetaData.InsightMeta -> {
                gson.toJson(mapOf(
                    "source" to "灵感",
                    "tags" to meta.tags
                ))
            }
            else -> null
        }
        
        // 如果 serialized 为 null 或空，回退到原始 metaJson
        return if (serialized.isNullOrEmpty() || serialized == "{}") {
            item.rawMetaJson ?: "{}"
        } else {
            serialized
        }
    }
}

package com.example.ai4research.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai4research.domain.model.ItemType
import com.example.ai4research.domain.model.ResearchItem
import com.example.ai4research.domain.repository.ItemRepository
import com.example.ai4research.service.FloatingWindowManager
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val itemRepository: ItemRepository,
    val floatingWindowManager: FloatingWindowManager
) : ViewModel() {

    private val _papers = MutableStateFlow<List<ResearchItem>>(emptyList())
    val papers: StateFlow<List<ResearchItem>> = _papers.asStateFlow()

    private val _competitions = MutableStateFlow<List<ResearchItem>>(emptyList())
    val competitions: StateFlow<List<ResearchItem>> = _competitions.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val gson = Gson()

    init {
        fetchData()
        observeData()
    }

    private fun observeData() {
        viewModelScope.launch {
            // 观察论文数据
            itemRepository.observeItems(type = ItemType.PAPER, query = null).collect { items ->
                _papers.value = items
            }
        }
        
        viewModelScope.launch {
            // 观察竞赛数据
            itemRepository.observeItems(type = ItemType.COMPETITION, query = null).collect { items ->
                _competitions.value = items
            }
        }
    }

    fun fetchData() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // 从远程同步数据到本地数据库
                itemRepository.refreshItems()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun search(query: String) {
        _searchQuery.value = query
        viewModelScope.launch {
            // 使用Repository的搜索功能
            itemRepository.observeItems(type = ItemType.PAPER, query = query).collect { items ->
                _papers.value = items
            }
        }
        viewModelScope.launch {
            itemRepository.observeItems(type = ItemType.COMPETITION, query = query).collect { items ->
                _competitions.value = items
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
                "tags" to (item.metaData as? com.example.ai4research.domain.model.ItemMetaData.CompetitionMeta)?.organizer,
                "meta_json" to serializeMetaData(item),
                "CreatedAt" to item.createdAt.toString(),
                "UpdatedAt" to item.createdAt.toString()
            )
        }
        return gson.toJson(compDtos)
    }

    private fun serializeMetaData(item: ResearchItem): String {
        return when (val meta = item.metaData) {
            is com.example.ai4research.domain.model.ItemMetaData.PaperMeta -> {
                gson.toJson(mapOf(
                    "authors" to meta.authors,
                    "conference" to meta.conference,
                    "year" to meta.year
                ))
            }
            is com.example.ai4research.domain.model.ItemMetaData.CompetitionMeta -> {
                gson.toJson(mapOf(
                    "organizer" to meta.organizer,
                    "prizePool" to meta.prizePool,
                    "deadline" to meta.timeline.firstOrNull()?.date?.toString()
                ))
            }
            else -> "{}"
        }
    }
}

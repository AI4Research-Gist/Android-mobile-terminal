package com.example.ai4research.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai4research.data.remote.api.NocoApiService
import com.example.ai4research.data.remote.dto.NocoItemDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val apiService: NocoApiService,
    private val json: Json
) : ViewModel() {

    private val _papers = MutableStateFlow<List<NocoItemDto>>(emptyList())
    val papers: StateFlow<List<NocoItemDto>> = _papers.asStateFlow()

    private val _competitions = MutableStateFlow<List<NocoItemDto>>(emptyList())
    val competitions: StateFlow<List<NocoItemDto>> = _competitions.asStateFlow()

    init {
        fetchData()
    }

    fun fetchData() {
        viewModelScope.launch {
            try {
                // Fetch Papers
                val papersResponse = apiService.getItemsByType("(type,eq,paper)")
                
                // Fetch Competitions
                val competitionsResponse = apiService.getItemsByType("(type,eq,competition)")

                // Check if database is empty (both lists empty), then seed data
                if (papersResponse.list.isEmpty() && competitionsResponse.list.isEmpty()) {
                    seedDatabase()
                } else {
                    _papers.value = papersResponse.list
                    _competitions.value = competitionsResponse.list
                }

            } catch (e: Exception) {
                e.printStackTrace()
                // In a real app, handle error state
            }
        }
    }

    private suspend fun seedDatabase() {
        try {
            // Sample Paper 1
            val paper1 = NocoItemDto(
                title = "Attention Is All You Need",
                type = "paper",
                summary = "The dominant sequence transduction models are based on complex recurrent or convolutional neural networks...",
                status = "finished",
                readStatus = "read",
                originUrl = "https://arxiv.org/abs/1706.03762",
                tags = "NLP,Transformer" // Assuming tags is a string or handle conversion if list
            )
            apiService.createItem(paper1)

            // Sample Paper 2
            val paper2 = NocoItemDto(
                title = "Deep Residual Learning for Image Recognition",
                type = "paper",
                summary = "Deeper neural networks are more difficult to train. We present a residual learning framework...",
                status = "processing",
                readStatus = "unread",
                originUrl = "https://arxiv.org/abs/1512.03385",
                tags = "CV,ResNet"
            )
            apiService.createItem(paper2)

            // Sample Competition
            val comp1 = NocoItemDto(
                title = "Kaggle - Titanic: Machine Learning from Disaster",
                type = "competition",
                summary = "Start here! Predict survival on the Titanic and get familiar with ML basics.",
                status = "registered",
                readStatus = "unread",
                originUrl = "https://www.kaggle.com/c/titanic",
                tags = "Kaggle,Intro"
            )
            apiService.createItem(comp1)

            // Refresh data after seeding
            val newPapers = apiService.getItemsByType("(type,eq,paper)")
            val newCompetitions = apiService.getItemsByType("(type,eq,competition)")
            
            _papers.value = newPapers.list
            _competitions.value = newCompetitions.list

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getPapersJson(): String {
        return json.encodeToString(_papers.value)
    }

    fun getCompetitionsJson(): String {
        return json.encodeToString(_competitions.value)
    }
}

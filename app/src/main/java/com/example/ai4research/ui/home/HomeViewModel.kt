package com.example.ai4research.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai4research.domain.model.ItemMetaData
import com.example.ai4research.domain.model.ItemStatus
import com.example.ai4research.domain.model.ItemType
import com.example.ai4research.domain.model.ReadStatus
import com.example.ai4research.domain.model.ResearchItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor() : ViewModel() {

    private val _items = MutableStateFlow<List<ResearchItem>>(emptyList())
    val items: StateFlow<List<ResearchItem>> = _items.asStateFlow()

    init {
        loadMockData()
    }

    private fun loadMockData() {
        viewModelScope.launch {
            val mockItems = listOf(
                ResearchItem(
                    id = "1",
                    type = ItemType.PAPER,
                    title = "Attention Is All You Need",
                    summary = "The dominant sequence transduction models are based on complex recurrent or convolutional neural networks...",
                    contentMarkdown = "# Attention Is All You Need\n...",
                    originUrl = null,
                    audioUrl = null,
                    status = ItemStatus.DONE,
                    readStatus = ReadStatus.READING,
                    projectId = null,
                    projectName = null,
                    metaData = ItemMetaData.PaperMeta(
                        authors = listOf("Vaswani et al."),
                        conference = "NIPS",
                        year = 2017,
                        tags = listOf("Transformer", "NLP")
                    ),
                    createdAt = Date()
                ),
                ResearchItem(
                    id = "2",
                    type = ItemType.INSIGHT,
                    title = "Idea for new architecture",
                    summary = "Maybe we can combine Mamba with Transformer to get linear complexity...",
                    contentMarkdown = "Just a thought...",
                    originUrl = null,
                    audioUrl = null,
                    status = ItemStatus.PROCESSING,
                    readStatus = ReadStatus.UNREAD,
                    projectId = null,
                    projectName = null,
                    metaData = ItemMetaData.InsightMeta(tags = listOf("Idea", "Architecture")),
                    createdAt = Date()
                ),
                ResearchItem(
                    id = "3",
                    type = ItemType.VOICE,
                    title = "Meeting Notes 2023-10-27",
                    summary = "Discussion about the new dataset collection strategy.",
                    contentMarkdown = "...",
                    originUrl = null,
                    audioUrl = "http://...",
                    status = ItemStatus.DONE,
                    readStatus = ReadStatus.UNREAD,
                    projectId = null,
                    projectName = null,
                    metaData = ItemMetaData.VoiceMeta(duration = 120, transcription = "Hello..."),
                    createdAt = Date()
                ),
                ResearchItem(
                    id = "4",
                    type = ItemType.COMPETITION,
                    title = "Kaggle - LLM Science Exam",
                    summary = "Use LLMs to answer difficult science questions.",
                    contentMarkdown = "...",
                    originUrl = null,
                    audioUrl = null,
                    status = ItemStatus.DONE,
                    readStatus = ReadStatus.READ,
                    projectId = null,
                    projectName = null,
                    metaData = ItemMetaData.CompetitionMeta(
                        timeline = emptyList(),
                        prizePool = "$50,000",
                        organizer = "Kaggle"
                    ),
                    createdAt = Date()
                )
            )
            _items.value = mockItems
        }
    }
}


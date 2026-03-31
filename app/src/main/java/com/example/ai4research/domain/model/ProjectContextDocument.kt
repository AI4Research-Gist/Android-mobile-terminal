package com.example.ai4research.domain.model

import java.util.Date

data class ProjectContextDocument(
    val projectId: String,
    val title: String,
    val markdownPath: String,
    val summary: String,
    val keywords: List<String>,
    val updatedAt: Date
)

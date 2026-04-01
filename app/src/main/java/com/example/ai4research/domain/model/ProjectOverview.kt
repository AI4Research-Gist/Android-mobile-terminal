package com.example.ai4research.domain.model

import java.util.Date

data class ProjectOverview(
    val project: Project,
    val contextDocument: ProjectContextDocument?,
    val recentItems: List<ResearchItem>,
    val keyPapers: List<ResearchItem>,
    val recentInsights: List<ResearchItem>,
    val stats: ProjectOverviewStats
)

data class ProjectOverviewStats(
    val totalItems: Int,
    val paperCount: Int,
    val articleCount: Int,
    val insightCount: Int,
    val duplicateRelationCount: Int,
    val articlePaperRelationCount: Int
)

data class ProjectAiSummary(
    val currentTheme: String,
    val recentProgress: List<String>,
    val keyLiterature: List<String>,
    val insightFocus: List<String>,
    val pendingQuestions: List<String>,
    val nextActions: List<String>,
    val generatedAt: Date
)

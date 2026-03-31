package com.example.ai4research.domain.model

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

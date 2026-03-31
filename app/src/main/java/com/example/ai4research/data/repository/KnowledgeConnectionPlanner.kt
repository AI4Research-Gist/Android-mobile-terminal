package com.example.ai4research.data.repository

import com.example.ai4research.domain.model.ItemMetaData
import com.example.ai4research.domain.model.ItemType
import com.example.ai4research.domain.model.RelationType
import com.example.ai4research.domain.model.ResearchItem

internal data class PlannedRelation(
    val toItemId: String,
    val relationType: RelationType,
    val confidence: Double,
    val source: String
)

internal object KnowledgeConnectionPlanner {
    fun planForItem(item: ResearchItem, allItems: List<ResearchItem>): List<PlannedRelation> {
        return when (item.type) {
            ItemType.PAPER -> planDuplicateRelations(item, allItems)
            ItemType.ARTICLE -> planArticlePaperRelations(item, allItems)
            else -> emptyList()
        }
    }

    private fun planDuplicateRelations(item: ResearchItem, allItems: List<ResearchItem>): List<PlannedRelation> {
        val paperMeta = item.metaData as? ItemMetaData.PaperMeta ?: return emptyList()
        val normalizedIdentifier = normalizeIdentifier(paperMeta.identifier)
        val normalizedDedupKey = normalizeIdentifier(paperMeta.dedupKey)
        if (normalizedIdentifier == null && normalizedDedupKey == null) return emptyList()

        val matchingPapers = allItems
            .filter { it.type == ItemType.PAPER }
            .filter { candidate ->
                val candidateMeta = candidate.metaData as? ItemMetaData.PaperMeta ?: return@filter false
                val candidateIdentifier = normalizeIdentifier(candidateMeta.identifier)
                val candidateDedupKey = normalizeIdentifier(candidateMeta.dedupKey)
                (normalizedIdentifier != null && normalizedIdentifier == candidateIdentifier) ||
                    (normalizedDedupKey != null && normalizedDedupKey == candidateDedupKey)
            }

        val canonical = matchingPapers.minWithOrNull(
            compareBy<ResearchItem>({ it.createdAt.time }, { it.id })
        ) ?: return emptyList()

        return if (canonical.id == item.id) {
            emptyList()
        } else {
            listOf(
                PlannedRelation(
                    toItemId = canonical.id,
                    relationType = RelationType.DUPLICATE_OF,
                    confidence = 1.0,
                    source = "auto_duplicate_match"
                )
            )
        }
    }

    private fun planArticlePaperRelations(item: ResearchItem, allItems: List<ResearchItem>): List<PlannedRelation> {
        val articleMeta = item.metaData as? ItemMetaData.ArticleMeta ?: return emptyList()
        val candidateUrls = articleMeta.paperCandidates.mapNotNull { candidate ->
            candidate.url.takeIf { it.isNotBlank() }
        } + articleMeta.referencedLinks

        val candidateLabels = articleMeta.paperCandidates.mapNotNull { candidate ->
            candidate.label?.takeIf { it.isNotBlank() }
        }

        if (candidateUrls.isEmpty() && candidateLabels.isEmpty()) return emptyList()

        return allItems
            .asSequence()
            .filter { it.type == ItemType.PAPER }
            .mapNotNull { paper ->
                val paperMeta = paper.metaData as? ItemMetaData.PaperMeta ?: return@mapNotNull null
                val match = computePaperCandidateMatch(
                    paper = paper,
                    paperMeta = paperMeta,
                    candidateUrls = candidateUrls,
                    candidateLabels = candidateLabels
                ) ?: return@mapNotNull null

                PlannedRelation(
                    toItemId = paper.id,
                    relationType = RelationType.ARTICLE_MENTIONS_PAPER,
                    confidence = match,
                    source = "auto_article_candidate_match"
                )
            }
            .distinctBy { it.toItemId }
            .sortedByDescending { it.confidence }
            .toList()
    }

    private fun computePaperCandidateMatch(
        paper: ResearchItem,
        paperMeta: ItemMetaData.PaperMeta,
        candidateUrls: List<String>,
        candidateLabels: List<String>
    ): Double? {
        val paperUrl = normalizeUrl(paper.originUrl)
        val paperIdentifier = normalizeIdentifier(paperMeta.identifier)
        val paperTitle = normalizeTitle(paper.title)

        if (candidateUrls.any { normalizeUrl(it) == paperUrl && paperUrl != null }) {
            return 0.98
        }

        if (paperIdentifier != null && candidateUrls.any { candidateUrl ->
                val normalizedCandidate = normalizeUrl(candidateUrl) ?: return@any false
                normalizedCandidate.contains(paperIdentifier)
            }
        ) {
            return 0.95
        }

        if (paperIdentifier != null && candidateLabels.any { normalizeIdentifier(it) == paperIdentifier }) {
            return 0.85
        }

        if (candidateLabels.any { normalizeTitle(it) == paperTitle && paperTitle.isNotBlank() }) {
            return 0.72
        }

        return null
    }

    private fun normalizeIdentifier(value: String?): String? {
        return value
            ?.trim()
            ?.lowercase()
            ?.replace("https://", "")
            ?.replace("http://", "")
            ?.trimEnd('/')
            ?.takeIf { it.isNotBlank() }
    }

    private fun normalizeUrl(value: String?): String? {
        return value
            ?.trim()
            ?.lowercase()
            ?.removePrefix("https://")
            ?.removePrefix("http://")
            ?.trimEnd('/')
            ?.takeIf { it.isNotBlank() }
    }

    private fun normalizeTitle(value: String?): String {
        return value
            ?.trim()
            ?.lowercase()
            ?.replace(Regex("[^a-z0-9\\u4e00-\\u9fa5]+"), " ")
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            .orEmpty()
    }
}

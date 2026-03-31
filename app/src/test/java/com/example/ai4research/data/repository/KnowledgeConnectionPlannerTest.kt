package com.example.ai4research.data.repository

import com.example.ai4research.domain.model.ArticlePaperCandidate
import com.example.ai4research.domain.model.ItemMetaData
import com.example.ai4research.domain.model.ItemStatus
import com.example.ai4research.domain.model.ItemType
import com.example.ai4research.domain.model.ReadStatus
import com.example.ai4research.domain.model.RelationType
import com.example.ai4research.domain.model.ResearchItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class KnowledgeConnectionPlannerTest {

    @Test
    fun `paper duplicate points to oldest canonical paper`() {
        val canonical = paperItem(
            id = "paper-old",
            identifier = "arXiv:2501.00001",
            dedupKey = "retrieval-agents-2025",
            createdAt = 1000L
        )
        val duplicate = paperItem(
            id = "paper-new",
            identifier = "2501.00001",
            dedupKey = "retrieval-agents-2025",
            createdAt = 3000L
        )

        val planned = KnowledgeConnectionPlanner.planForItem(duplicate, listOf(canonical, duplicate))

        assertEquals(1, planned.size)
        assertEquals("paper-old", planned.first().toItemId)
        assertEquals(RelationType.DUPLICATE_OF, planned.first().relationType)
        assertEquals(1.0, planned.first().confidence, 0.0001)
    }

    @Test
    fun `article candidate url creates article to paper relation`() {
        val paper = paperItem(
            id = "paper-1",
            identifier = "10.1000/demo",
            dedupKey = null,
            createdAt = 1000L,
            originUrl = "https://doi.org/10.1000/demo"
        )
        val article = articleItem(
            id = "article-1",
            candidateUrl = "https://doi.org/10.1000/demo",
            candidateLabel = "Demo Paper"
        )

        val planned = KnowledgeConnectionPlanner.planForItem(article, listOf(paper, article))

        assertEquals(1, planned.size)
        assertEquals("paper-1", planned.first().toItemId)
        assertEquals(RelationType.ARTICLE_MENTIONS_PAPER, planned.first().relationType)
        assertTrue(planned.first().confidence >= 0.95)
    }

    private fun paperItem(
        id: String,
        identifier: String?,
        dedupKey: String?,
        createdAt: Long,
        originUrl: String? = null
    ): ResearchItem {
        return ResearchItem(
            id = id,
            type = ItemType.PAPER,
            title = "Demo Paper $id",
            summary = "",
            note = null,
            contentMarkdown = "",
            originUrl = originUrl,
            audioUrl = null,
            status = ItemStatus.DONE,
            readStatus = ReadStatus.UNREAD,
            isStarred = false,
            projectId = null,
            projectName = null,
            metaData = ItemMetaData.PaperMeta(
                authors = listOf("Ada"),
                identifier = identifier,
                dedupKey = dedupKey
            ),
            rawMetaJson = null,
            createdAt = Date(createdAt)
        )
    }

    private fun articleItem(
        id: String,
        candidateUrl: String,
        candidateLabel: String?
    ): ResearchItem {
        return ResearchItem(
            id = id,
            type = ItemType.ARTICLE,
            title = "Demo Article",
            summary = "",
            note = null,
            contentMarkdown = "",
            originUrl = null,
            audioUrl = null,
            status = ItemStatus.DONE,
            readStatus = ReadStatus.UNREAD,
            isStarred = false,
            projectId = null,
            projectName = null,
            metaData = ItemMetaData.ArticleMeta(
                paperCandidates = listOf(
                    ArticlePaperCandidate(
                        url = candidateUrl,
                        label = candidateLabel,
                        kind = "doi"
                    )
                )
            ),
            rawMetaJson = null,
            createdAt = Date(2000L)
        )
    }
}

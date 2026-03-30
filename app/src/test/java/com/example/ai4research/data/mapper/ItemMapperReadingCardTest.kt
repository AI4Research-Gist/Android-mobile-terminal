package com.example.ai4research.data.mapper

import com.example.ai4research.data.local.entity.ItemEntity
import com.example.ai4research.domain.model.ItemMetaData
import com.example.ai4research.domain.model.ItemStatus
import com.example.ai4research.domain.model.ItemType
import com.example.ai4research.domain.model.ReadStatus
import com.example.ai4research.domain.model.ResearchItem
import com.example.ai4research.domain.model.StructuredReadingCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class ItemMapperReadingCardTest {

    @Test
    fun `parses reading card from paper meta json`() {
        val metaJson = """
            {
              "authors": ["Ada Lovelace"],
              "reading_card": {
                "research_question": "How to reduce hallucinations?",
                "method": "retrieval augmented generation",
                "dataset": "internal benchmark",
                "findings": "retrieval improves factual grounding",
                "limitations": "depends on index quality",
                "reuse_points": "adapt the retrieval stage",
                "my_notes": "worth trying for our paper ingestion flow"
              }
            }
        """.trimIndent()

        val entity = ItemEntity(
            id = "p1",
            ownerUserId = "u1",
            type = ItemType.PAPER.toServerString(),
            title = "Demo Paper",
            summary = "summary",
            contentMarkdown = "content",
            originUrl = null,
            audioUrl = null,
            status = ItemStatus.DONE.toServerString(),
            readStatus = ReadStatus.UNREAD.toServerString(),
            projectId = null,
            projectName = null,
            isStarred = false,
            metaJson = metaJson,
            createdAt = 0L,
            syncedAt = 0L
        )

        val item = ItemMapper.entityToDomain(entity)
        val meta = item.metaData as? ItemMetaData.PaperMeta

        assertNotNull(meta)
        assertEquals("How to reduce hallucinations?", meta?.readingCard?.researchQuestion)
        assertEquals("retrieval augmented generation", meta?.readingCard?.method)
        assertEquals("worth trying for our paper ingestion flow", meta?.readingCard?.myNotes)
    }

    @Test
    fun `serializes reading card back into meta json`() {
        val item = ResearchItem(
            id = "a1",
            type = ItemType.ARTICLE,
            title = "Demo Article",
            summary = "summary",
            note = "note",
            contentMarkdown = "content",
            originUrl = "https://example.com",
            audioUrl = null,
            status = ItemStatus.DONE,
            readStatus = ReadStatus.READ,
            projectId = null,
            projectName = null,
            metaData = ItemMetaData.ArticleMeta(
                platform = "WeChat",
                readingCard = StructuredReadingCard(
                    researchQuestion = "What trend matters?",
                    method = "manual synthesis",
                    dataset = "10 newsletters",
                    findings = "tooling is converging",
                    limitations = "small sample",
                    reusePoints = "weekly review template",
                    myNotes = "good input for project sync"
                )
            ),
            rawMetaJson = "{}",
            createdAt = Date(0L)
        )

        val entity = ItemMapper.domainToEntity(item, ownerUserId = "u1")

        assertTrue(entity.metaJson?.contains("\"reading_card\"") == true)
        assertTrue(entity.metaJson?.contains("\"weekly review template\"") == true)
    }
}

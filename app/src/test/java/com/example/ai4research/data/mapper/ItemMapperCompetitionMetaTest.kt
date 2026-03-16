package com.example.ai4research.data.mapper

import com.example.ai4research.data.local.entity.ItemEntity
import com.example.ai4research.domain.model.ItemMetaData
import com.example.ai4research.domain.model.ItemType
import com.example.ai4research.domain.model.ReadStatus
import com.example.ai4research.domain.model.ItemStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ItemMapperCompetitionMetaTest {

    @Test
    fun `parses competition timeline and urls from meta json`() {
        val metaJson = """
            {
              "organizer": "Kaggle",
              "website": "https://kaggle.com/competitions/demo",
              "registrationUrl": "https://kaggle.com/competitions/demo/register",
              "timeline": [
                {
                  "name": "报名截止",
                  "date": "2026-03-18T00:00:00Z",
                  "isPassed": false
                }
              ]
            }
        """.trimIndent()

        val entity = ItemEntity(
            id = "c1",
            ownerUserId = "u1",
            type = ItemType.COMPETITION.toServerString(),
            title = "Demo Competition",
            summary = "",
            contentMarkdown = "",
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
        val meta = item.metaData as? ItemMetaData.CompetitionMeta

        assertNotNull(meta)
        assertEquals("Kaggle", meta?.organizer)
        assertEquals("https://kaggle.com/competitions/demo", meta?.website)
        assertEquals("https://kaggle.com/competitions/demo/register", meta?.registrationUrl)
        assertEquals(1, meta?.timeline?.size)
        assertEquals("报名截止", meta?.timeline?.firstOrNull()?.name)
    }
}

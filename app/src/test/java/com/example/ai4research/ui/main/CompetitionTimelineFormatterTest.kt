package com.example.ai4research.ui.main

import com.example.ai4research.domain.model.ItemMetaData
import com.example.ai4research.domain.model.TimelineEvent
import java.time.Instant
import java.time.ZoneOffset
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompetitionTimelineFormatterTest {

    private val now = Instant.parse("2026-03-15T00:00:00Z")

    @Test
    fun `summarize picks nearest upcoming timeline node and labels remaining days`() {
        val meta = ItemMetaData.CompetitionMeta(
            timeline = listOf(
                TimelineEvent(
                    name = "结果公布",
                    date = Date.from(Instant.parse("2026-03-25T00:00:00Z")),
                    isPassed = false
                ),
                TimelineEvent(
                    name = "提交截止",
                    date = Date.from(Instant.parse("2026-03-18T00:00:00Z")),
                    isPassed = false
                ),
                TimelineEvent(
                    name = "报名截止",
                    date = Date.from(Instant.parse("2026-03-17T00:00:00Z")),
                    isPassed = false
                )
            )
        )

        val summary = CompetitionTimelineFormatter.summarize(
            meta = meta,
            now = now,
            zoneId = ZoneOffset.UTC
        )

        assertEquals("报名截止", summary.anchorName)
        assertEquals(2, summary.daysDelta)
        assertEquals("距报名截止 2 天", summary.displayText)
        assertFalse(summary.isOverdue)
    }

    @Test
    fun `summarize shows overdue days when timeline has already passed`() {
        val meta = ItemMetaData.CompetitionMeta(
            timeline = listOf(
                TimelineEvent(
                    name = "提交截止",
                    date = Date.from(Instant.parse("2026-03-12T00:00:00Z")),
                    isPassed = true
                )
            )
        )

        val summary = CompetitionTimelineFormatter.summarize(
            meta = meta,
            now = now,
            zoneId = ZoneOffset.UTC
        )

        assertEquals("提交截止", summary.anchorName)
        assertEquals(3, summary.daysDelta)
        assertEquals("已过 3 天", summary.displayText)
        assertTrue(summary.isOverdue)
    }

    @Test
    fun `summarize falls back to deadline when timeline is missing`() {
        val meta = ItemMetaData.CompetitionMeta(
            deadline = "2026-03-16T00:00:00Z"
        )

        val summary = CompetitionTimelineFormatter.summarize(
            meta = meta,
            now = now,
            zoneId = ZoneOffset.UTC
        )

        assertEquals("截止时间", summary.anchorName)
        assertEquals(1, summary.daysDelta)
        assertEquals("距截止时间 1 天", summary.displayText)
        assertFalse(summary.isOverdue)
    }

    @Test
    fun `sort key puts upcoming competitions ahead of overdue ones`() {
        val upcoming = CompetitionTimelineFormatter.summarize(
            meta = ItemMetaData.CompetitionMeta(deadline = "2026-03-16T00:00:00Z"),
            now = now,
            zoneId = ZoneOffset.UTC
        )
        val overdue = CompetitionTimelineFormatter.summarize(
            meta = ItemMetaData.CompetitionMeta(deadline = "2026-03-10T00:00:00Z"),
            now = now,
            zoneId = ZoneOffset.UTC
        )

        assertTrue(upcoming.sortKey < overdue.sortKey)
    }
}

package com.example.ai4research.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompetitionImportDeciderTest {

    @Test
    fun `competition without key timeline nodes needs manual fallback`() {
        val result = FullLinkParseResult(
            title = "Demo",
            authors = null,
            summary = "summary",
            contentType = "competition",
            source = "kaggle",
            identifier = null,
            tags = emptyList(),
            originalUrl = "https://kaggle.com/demo",
            conference = null,
            year = null,
            platform = "kaggle",
            timeline = emptyList()
        )

        assertTrue(CompetitionImportDecider.needsManualFallback(result))
    }

    @Test
    fun `competition with submission deadline skips manual fallback`() {
        val result = FullLinkParseResult(
            title = "Demo",
            authors = null,
            summary = "summary",
            contentType = "competition",
            source = "kaggle",
            identifier = null,
            tags = emptyList(),
            originalUrl = "https://kaggle.com/demo",
            conference = null,
            year = null,
            platform = "kaggle",
            timeline = listOf(CompetitionTimelineNode("提交截止", "2026-03-20"))
        )

        assertFalse(CompetitionImportDecider.needsManualFallback(result))
    }
}

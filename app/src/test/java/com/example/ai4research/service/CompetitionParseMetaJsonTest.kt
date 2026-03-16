package com.example.ai4research.service

import org.junit.Assert.assertTrue
import org.junit.Test

class CompetitionParseMetaJsonTest {

    @Test
    fun `competition parse result serializes key competition fields into meta json`() {
        val parseResult = FullLinkParseResult(
            title = "Demo Competition",
            authors = null,
            summary = "summary",
            contentType = "competition",
            source = "kaggle",
            identifier = null,
            tags = listOf("vision"),
            originalUrl = "https://kaggle.com/demo",
            conference = null,
            year = null,
            platform = "kaggle",
            organizer = "Kaggle",
            prizePool = "$10,000",
            theme = "CV",
            competitionType = "数据科学",
            website = "https://kaggle.com/demo",
            registrationUrl = "https://kaggle.com/demo/register",
            timeline = listOf(
                CompetitionTimelineNode(name = "报名截止", date = "2026-03-18"),
                CompetitionTimelineNode(name = "提交截止", date = "2026-03-20")
            )
        )

        val metaJson = parseResult.toMetaJson().orEmpty()

        assertTrue(metaJson.contains("\"organizer\":\"Kaggle\""))
        assertTrue(metaJson.contains("\"registrationUrl\":\"https://kaggle.com/demo/register\""))
        assertTrue(metaJson.contains("\"timeline\""))
        assertTrue(metaJson.contains("报名截止"))
        assertTrue(metaJson.contains("提交截止"))
    }
}

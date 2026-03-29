package com.example.ai4research.ui.main

import com.example.ai4research.domain.model.ItemMetaData
import com.example.ai4research.domain.model.TimelineEvent
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

data class CompetitionTimelineSummary(
    val anchorName: String,
    val daysDelta: Int,
    val displayText: String,
    val isOverdue: Boolean,
    val sortKey: Int
)

object CompetitionTimelineFormatter {
    fun summarize(
        meta: ItemMetaData.CompetitionMeta?,
        now: Instant = Instant.now(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): CompetitionTimelineSummary {
        val nowDate = now.atZone(zoneId).toLocalDate()
        val anchors = buildAnchors(meta, zoneId)

        if (anchors.isEmpty()) {
            return CompetitionTimelineSummary(
                anchorName = "",
                daysDelta = Int.MAX_VALUE,
                displayText = "",
                isOverdue = false,
                sortKey = Int.MAX_VALUE
            )
        }

        val upcomingAnchor = anchors
            .filter { !it.date.isBefore(nowDate) }
            .minWithOrNull(compareBy<Anchor> { it.date }.thenBy { priorityOf(it.name) })

        val anchor = upcomingAnchor
            ?: anchors
                .filter { it.date.isBefore(nowDate) }
                .maxWithOrNull(compareBy<Anchor> { it.date }.thenByDescending { priorityOf(it.name) })
            ?: anchors.first()

        val delta = kotlin.math.abs(daysBetween(nowDate, anchor.date))
        val isOverdue = anchor.date.isBefore(nowDate)
        val displayText = if (isOverdue) {
            "已截止"
        } else {
            "距${anchor.name} $delta 天"
        }
        val sortKey = if (isOverdue) 1_000_000 + delta else delta

        return CompetitionTimelineSummary(
            anchorName = anchor.name,
            daysDelta = delta,
            displayText = displayText,
            isOverdue = isOverdue,
            sortKey = sortKey
        )
    }

    private fun buildAnchors(
        meta: ItemMetaData.CompetitionMeta?,
        zoneId: ZoneId
    ): List<Anchor> {
        val anchors = mutableListOf<Anchor>()

        meta?.timeline
            .orEmpty()
            .sortedWith(compareBy<TimelineEvent> { it.date.time }.thenBy { priorityOf(it.name) })
            .forEach { event ->
                anchors += Anchor(
                    name = event.name,
                    date = toLocalDate(event.date.toInstant(), zoneId)
                )
            }

        meta?.deadline
            ?.takeIf { it.isNotBlank() }
            ?.let { parseDate(it, zoneId) }
            ?.let { anchors += Anchor(name = "截止时间", date = it) }

        return anchors
    }

    private fun priorityOf(name: String): Int = when {
        "提交截止" in name -> 0
        "报名截止" in name -> 1
        "报名开始" in name -> 2
        "结果公布" in name -> 3
        else -> 99
    }

    private fun parseDate(raw: String, zoneId: ZoneId): LocalDate? {
        return runCatching { Instant.parse(raw) }.getOrNull()?.let { toLocalDate(it, zoneId) }
            ?: runCatching { LocalDate.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull()
            ?: runCatching {
                DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss zzz yyyy", Locale.US)
                    .parse(raw, ZonedDateTime::from)
                    .toInstant()
            }.getOrNull()?.let { toLocalDate(it, zoneId) }
    }

    private fun toLocalDate(instant: Instant, zoneId: ZoneId): LocalDate =
        instant.atZone(zoneId).toLocalDate()

    private fun daysBetween(start: LocalDate, end: LocalDate): Int =
        ChronoUnit.DAYS.between(start, end).toInt()

    private data class Anchor(
        val name: String,
        val date: LocalDate
    )
}

package com.example.ai4research.ui.main

import com.example.ai4research.domain.model.ItemMetaData
import com.example.ai4research.domain.model.TimelineEvent
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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

        val upcomingEvent = meta?.timeline
            .orEmpty()
            .sortedWith(compareBy<TimelineEvent> { it.date.time }.thenBy { priorityOf(it.name) })
            .firstOrNull { !toLocalDate(it.date.toInstant(), zoneId).isBefore(nowDate) }

        val overdueEvent = meta?.timeline
            .orEmpty()
            .sortedWith(compareByDescending<TimelineEvent> { it.date.time }.thenBy { priorityOf(it.name) })
            .firstOrNull()

        val anchor = when {
            upcomingEvent != null -> Anchor(upcomingEvent.name, toLocalDate(upcomingEvent.date.toInstant(), zoneId))
            meta?.deadline != null -> parseDate(meta.deadline, zoneId)?.let { Anchor("截止时间", it) }
            overdueEvent != null -> Anchor(overdueEvent.name, toLocalDate(overdueEvent.date.toInstant(), zoneId))
            else -> Anchor("时间待补充", nowDate)
        } ?: Anchor("时间待补充", nowDate)

        val delta = kotlin.math.abs(daysBetween(nowDate, anchor.date))
        val isOverdue = anchor.date.isBefore(nowDate)
        val displayText = when {
            anchor.name == "时间待补充" -> anchor.name
            isOverdue -> "已过 $delta 天"
            else -> "距${anchor.name} $delta 天"
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

    private fun priorityOf(name: String): Int = when {
        "提交截止" in name -> 0
        "报名截止" in name -> 1
        "报名开始" in name -> 2
        "结果公布" in name -> 3
        else -> 99
    }

    private fun parseDate(raw: String, zoneId: ZoneId): LocalDate? {
        return runCatching { Instant.parse(raw) }.getOrNull()?.let { toLocalDate(it, zoneId) }
            ?: runCatching {
                DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss zzz yyyy", Locale.US)
                    .parse(raw, java.time.ZonedDateTime::from)
                    .toInstant()
            }.getOrNull()?.let { toLocalDate(it, zoneId) }
    }

    private fun toLocalDate(instant: Instant, zoneId: ZoneId): LocalDate =
        instant.atZone(zoneId).toLocalDate()

    private fun daysBetween(start: LocalDate, end: LocalDate): Int =
        java.time.temporal.ChronoUnit.DAYS.between(start, end).toInt()

    private data class Anchor(val name: String, val date: LocalDate)
}

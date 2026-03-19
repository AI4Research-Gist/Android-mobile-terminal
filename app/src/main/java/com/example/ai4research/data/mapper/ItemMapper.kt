package com.example.ai4research.data.mapper

import com.example.ai4research.data.local.entity.ItemEntity
import com.example.ai4research.data.local.entity.ProjectEntity
import com.example.ai4research.data.remote.dto.NocoItemDto
import com.example.ai4research.data.remote.dto.NocoProjectDto
import com.example.ai4research.domain.model.ItemMetaData
import com.example.ai4research.domain.model.ItemStatus
import com.example.ai4research.domain.model.ItemType
import com.example.ai4research.domain.model.Project
import com.example.ai4research.domain.model.ReadStatus
import com.example.ai4research.domain.model.ResearchItem
import com.example.ai4research.domain.model.TimelineEvent
import com.google.gson.Gson
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

object ItemMapper {
    private val gson = Gson()
    private val dateFormats = listOf(
        SimpleDateFormat("yyyy-MM-dd HH:mm:ssXXX", Locale.US),
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
    ).onEach { it.timeZone = TimeZone.getTimeZone("UTC") }

    private fun parseDate(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return System.currentTimeMillis()
        for (format in dateFormats) {
            try {
                return format.parse(dateStr)?.time ?: continue
            } catch (_: Exception) {
            }
        }
        android.util.Log.w("ItemMapper", "Unable to parse date: $dateStr")
        return System.currentTimeMillis()
    }

    private fun parseStringList(value: Any?): List<String> {
        return when (value) {
            is List<*> -> value.mapNotNull { it?.toString()?.trim()?.takeIf(String::isNotBlank) }
            is String -> value.split(",", "，")
                .map { it.trim() }
                .filter { it.isNotBlank() }
            else -> emptyList()
        }
    }

    private fun parseInt(value: Any?): Int? {
        return when (value) {
            is Number -> value.toInt()
            is String -> value.trim().toIntOrNull()
            else -> null
        }
    }

    private fun parseMetaMap(metaJson: String?): Map<String, Any?>? {
        if (metaJson.isNullOrBlank() || metaJson == "{}") return null
        return try {
            val normalizedMetaJson = if (metaJson.startsWith("\"") && metaJson.endsWith("\"")) {
                runCatching { gson.fromJson(metaJson, String::class.java) }.getOrNull() ?: metaJson
            } else {
                metaJson
            }
            gson.fromJson(normalizedMetaJson, Map::class.java) as? Map<String, Any?>
        } catch (e: Exception) {
            android.util.Log.w("ItemMapper", "Failed to parse metaJson: $metaJson, error: ${e.message}")
            null
        }
    }

    private fun normalizeMetaJson(element: JsonElement?): String? {
        return when (element) {
            null -> null
            is JsonPrimitive -> {
                if (element.isString) {
                    element.contentOrNull?.takeIf { it.isNotBlank() }
                } else {
                    element.toString()
                }
            }
            else -> element.toString()
        }
    }

    fun dtoToEntity(
        dto: NocoItemDto,
        projectId: String? = null,
        projectName: String? = null,
        ownerUserId: String? = null
    ): ItemEntity {
        val createdAt = parseDate(dto.createdAt)
        val normalizedType = (dto.type ?: "insight").trim().lowercase()

        return ItemEntity(
            id = dto.id?.toString() ?: UUID.randomUUID().toString(),
            ownerUserId = dto.ownerUserId ?: ownerUserId.orEmpty(),
            type = normalizedType,
            title = dto.title ?: "未命名",
            summary = dto.summary ?: "",
            contentMarkdown = dto.contentMd ?: "",
            originUrl = dto.originUrl,
            audioUrl = dto.audioUrl,
            status = dto.status ?: ItemStatus.PROCESSING.toServerString(),
            readStatus = dto.readStatus ?: ReadStatus.UNREAD.toServerString(),
            projectId = dto.projectId?.toString() ?: projectId,
            projectName = projectName,
            isStarred = false,
            metaJson = normalizeMetaJson(dto.metaJson),
            createdAt = createdAt,
            syncedAt = System.currentTimeMillis()
        )
    }

    fun entityToDomain(entity: ItemEntity): ResearchItem {
        return ResearchItem(
            id = entity.id,
            type = ItemType.fromString(entity.type),
            title = entity.title,
            summary = entity.summary,
            note = parseMetaMap(entity.metaJson)?.get("note")?.toString(),
            contentMarkdown = entity.contentMarkdown,
            originUrl = entity.originUrl,
            audioUrl = entity.audioUrl,
            status = ItemStatus.fromString(entity.status),
            readStatus = ReadStatus.fromString(entity.readStatus),
            isStarred = entity.isStarred,
            projectId = entity.projectId,
            projectName = entity.projectName,
            metaData = parseMetaData(entity.metaJson, entity.type),
            rawMetaJson = entity.metaJson,
            createdAt = Date(entity.createdAt)
        )
    }

    fun domainToEntity(item: ResearchItem, ownerUserId: String): ItemEntity {
        return ItemEntity(
            id = item.id,
            ownerUserId = ownerUserId,
            type = item.type.toServerString(),
            title = item.title,
            summary = item.summary,
            contentMarkdown = item.contentMarkdown,
            originUrl = item.originUrl,
            audioUrl = item.audioUrl,
            status = item.status.toServerString(),
            readStatus = item.readStatus.toServerString(),
            projectId = item.projectId,
            projectName = item.projectName,
            isStarred = item.isStarred,
            metaJson = buildMetaJson(item),
            createdAt = item.createdAt.time,
            syncedAt = System.currentTimeMillis()
        )
    }

    private fun buildMetaJson(item: ResearchItem): String? {
        val existing = parseMetaMap(item.rawMetaJson)?.toMutableMap() ?: mutableMapOf()

        when (val meta = item.metaData) {
            is ItemMetaData.PaperMeta -> {
                if (meta.authors.isNotEmpty()) existing["authors"] = meta.authors
                meta.conference?.let { existing["conference"] = it }
                meta.year?.let { existing["year"] = it }
                if (meta.tags.isNotEmpty()) existing["tags"] = meta.tags
                meta.source?.let { existing["source"] = it }
                meta.identifier?.let { existing["identifier"] = it }
                if (meta.domainTags.isNotEmpty()) existing["domain_tags"] = meta.domainTags
                if (meta.keywords.isNotEmpty()) existing["keywords"] = meta.keywords
                if (meta.methodTags.isNotEmpty()) existing["method_tags"] = meta.methodTags
                meta.dedupKey?.let { existing["dedup_key"] = it }
                meta.summaryShort?.let { existing["summary_short"] = it }
            }

            is ItemMetaData.CompetitionMeta -> {
                meta.organizer?.let { existing["organizer"] = it }
                meta.prizePool?.let { existing["prizePool"] = it }
                meta.deadline?.let { existing["deadline"] = it }
                meta.theme?.let { existing["theme"] = it }
                meta.competitionType?.let { existing["competitionType"] = it }
                meta.website?.let { existing["website"] = it }
                meta.registrationUrl?.let { existing["registrationUrl"] = it }
                meta.timeline?.let { timeline ->
                    existing["timeline"] = timeline.map { event ->
                        mapOf(
                            "name" to event.name,
                            "date" to event.date.toInstant().toString(),
                            "isPassed" to event.isPassed
                        )
                    }
                }
            }

            is ItemMetaData.InsightMeta -> {
                if (meta.tags.isNotEmpty()) existing["tags"] = meta.tags
            }

            is ItemMetaData.VoiceMeta -> {
                existing["duration"] = meta.duration
                meta.transcription?.let { existing["transcription"] = it }
            }

            null -> Unit
        }

        item.note?.let { existing["note"] = it }

        return if (existing.isEmpty()) null else gson.toJson(existing)
    }

    private fun parseMetaData(metaJson: String?, type: String): ItemMetaData? {
        val map = parseMetaMap(metaJson) ?: return null

        return try {
            when (type.lowercase()) {
                "paper" -> {
                    val authors = parseStringList(map["authors"])
                    val tags = parseStringList(map["tags"])
                    val domainTags = parseStringList(map["domain_tags"]).ifEmpty { tags }
                    val keywords = parseStringList(map["keywords"]).ifEmpty { tags }
                    val methodTags = parseStringList(map["method_tags"])
                    val year = parseInt(map["year"])

                    ItemMetaData.PaperMeta(
                        authors = authors,
                        conference = map["conference"]?.toString(),
                        year = year,
                        tags = tags,
                        source = map["source"]?.toString(),
                        identifier = map["identifier"]?.toString(),
                        domainTags = domainTags,
                        keywords = keywords,
                        methodTags = methodTags,
                        dedupKey = map["dedup_key"]?.toString(),
                        summaryShort = map["summary_short"]?.toString()
                    )
                }

                "competition" -> {
                    val timeline = (map["timeline"] as? List<*>)?.mapNotNull { raw ->
                        val entry = raw as? Map<*, *> ?: return@mapNotNull null
                        val name = entry["name"]?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                        val dateStr = entry["date"]?.toString()
                        val date = Date(parseDate(dateStr))
                        val isPassed = when (val passedRaw = entry["isPassed"]) {
                            is Boolean -> passedRaw
                            is String -> passedRaw.toBoolean()
                            else -> false
                        }
                        TimelineEvent(name = name, date = date, isPassed = isPassed)
                    }

                    ItemMetaData.CompetitionMeta(
                        organizer = map["organizer"]?.toString(),
                        prizePool = map["prizePool"]?.toString(),
                        deadline = map["deadline"]?.toString(),
                        theme = map["theme"]?.toString(),
                        competitionType = map["competitionType"]?.toString(),
                        website = map["website"]?.toString(),
                        registrationUrl = map["registrationUrl"]?.toString(),
                        timeline = timeline
                    )
                }

                "insight" -> {
                    val tags = parseStringList(map["tags"])
                    ItemMetaData.InsightMeta(tags = tags)
                }

                "voice" -> {
                    val duration = when (val durationRaw = map["duration"]) {
                        is Number -> durationRaw.toInt()
                        is String -> durationRaw.toIntOrNull() ?: 0
                        else -> 0
                    }
                    ItemMetaData.VoiceMeta(
                        duration = duration,
                        transcription = map["transcription"]?.toString()
                    )
                }

                else -> null
            }
        } catch (e: Exception) {
            android.util.Log.w("ItemMapper", "Failed to parse metaJson: $metaJson, error: ${e.message}")
            null
        }
    }

    fun projectDtoToEntity(dto: NocoProjectDto, ownerUserId: String? = null): ProjectEntity {
        val createdAt = parseDate(dto.createdAt)

        return ProjectEntity(
            id = dto.id?.toString() ?: UUID.randomUUID().toString(),
            ownerUserId = dto.ownerUserId ?: ownerUserId.orEmpty(),
            name = dto.name?.takeIf { it.isNotBlank() }
                ?: dto.title?.takeIf { it.isNotBlank() }
                ?: "未命名项目",
            description = dto.description,
            createdAt = createdAt
        )
    }

    fun projectEntityToDomain(entity: ProjectEntity): Project {
        return Project(
            id = entity.id,
            name = entity.name,
            description = entity.description
        )
    }
}

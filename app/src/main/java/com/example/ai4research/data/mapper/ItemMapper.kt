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
import com.google.gson.Gson
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

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
            metaJson = dto.metaJson?.toString(),
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
            metaJson = item.metaData?.let { gson.toJson(it) },
            createdAt = item.createdAt.time,
            syncedAt = System.currentTimeMillis()
        )
    }

    private fun parseMetaData(metaJson: String?, type: String): ItemMetaData? {
        if (metaJson.isNullOrEmpty() || metaJson == "{}") return null

        return try {
            val map = gson.fromJson(metaJson, Map::class.java) as? Map<String, Any?> ?: return null

            when (type.lowercase()) {
                "paper" -> {
                    val authors = when (val authorsRaw = map["authors"]) {
                        is List<*> -> authorsRaw.mapNotNull { it?.toString() }
                        is String -> if (authorsRaw.isNotBlank()) authorsRaw.split(",").map { it.trim() } else emptyList()
                        else -> emptyList()
                    }
                    val tags = when (val tagsRaw = map["tags"]) {
                        is List<*> -> tagsRaw.mapNotNull { it?.toString() }
                        is String -> if (tagsRaw.isNotBlank()) tagsRaw.split(",").map { it.trim() } else emptyList()
                        else -> emptyList()
                    }
                    val year = when (val yearRaw = map["year"]) {
                        is Number -> yearRaw.toInt()
                        is String -> yearRaw.toIntOrNull()
                        else -> null
                    }

                    ItemMetaData.PaperMeta(
                        authors = authors,
                        conference = map["conference"]?.toString(),
                        year = year,
                        tags = tags
                    )
                }

                "competition" -> ItemMetaData.CompetitionMeta(
                    organizer = map["organizer"]?.toString(),
                    prizePool = map["prizePool"]?.toString(),
                    deadline = map["deadline"]?.toString(),
                    theme = map["theme"]?.toString(),
                    competitionType = map["competitionType"]?.toString()
                )

                "insight" -> {
                    val tags = when (val tagsRaw = map["tags"]) {
                        is List<*> -> tagsRaw.mapNotNull { it?.toString() }
                        is String -> if (tagsRaw.isNotBlank()) tagsRaw.split(",").map { it.trim() } else emptyList()
                        else -> emptyList()
                    }
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

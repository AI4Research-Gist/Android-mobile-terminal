package com.example.ai4research.data.mapper

import com.example.ai4research.data.local.entity.ItemEntity
import com.example.ai4research.data.local.entity.ProjectEntity
import com.example.ai4research.data.remote.dto.NocoItemDto
import com.example.ai4research.data.remote.dto.NocoProjectDto
import com.example.ai4research.domain.model.*
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import java.text.SimpleDateFormat
import java.util.*

/**
 * 数据映射器 - 在不同层级的数据类型之间转换
 */
object ItemMapper {
    private val gson = Gson()
    // NocoDB 返回的日期格式: "2026-01-27 04:43:09+00:00"
    private val dateFormats = listOf(
        SimpleDateFormat("yyyy-MM-dd HH:mm:ssXXX", Locale.US),  // 带时区偏移
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US),     // 无时区
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US),  // ISO 8601
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)       // ISO 8601 无毫秒
    ).onEach { it.timeZone = TimeZone.getTimeZone("UTC") }
    
    private fun parseDate(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return System.currentTimeMillis()
        for (format in dateFormats) {
            try {
                return format.parse(dateStr)?.time ?: continue
            } catch (e: Exception) {
                // 尝试下一个格式
            }
        }
        android.util.Log.w("ItemMapper", "无法解析日期: $dateStr，使用当前时间")
        return System.currentTimeMillis()
    }
    
    /**
     * DTO -> Entity (网络数据转本地存储)
     */
    fun dtoToEntity(
        dto: NocoItemDto,
        projectId: String? = null,
        projectName: String? = null
    ): ItemEntity {
        val createdAt = parseDate(dto.createdAt)
        
        // 标准化 type 值为小写，确保与查询匹配
        val normalizedType = (dto.type ?: "insight").trim().lowercase()
        android.util.Log.d("ItemMapper", "Mapping item: id=${dto.id}, originalType=${dto.type}, normalizedType=$normalizedType, projectId=${dto.projectId}")
        
        return ItemEntity(
            id = dto.id?.toString() ?: UUID.randomUUID().toString(),  // 将 Int ID 转为 String
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
            isStarred = false,  // 默认未标星
            metaJson = dto.metaJson?.toString(),
            createdAt = createdAt,
            syncedAt = System.currentTimeMillis()
        )
    }
    
    /**
     * Entity -> Domain Model (本地存储转业务模型)
     */
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
            rawMetaJson = entity.metaJson, // 保留原始JSON
            createdAt = Date(entity.createdAt)
        )
    }
    
    /**
     * Domain Model -> Entity (业务模型转本地存储)
     */
    fun domainToEntity(item: ResearchItem): ItemEntity {
        return ItemEntity(
            id = item.id,
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
    
    /**
     * 解析元数据 JSON - 更健壮的解析，兼容多种格式
     */
    private fun parseMetaData(metaJson: String?, type: String): ItemMetaData? {
        if (metaJson.isNullOrEmpty() || metaJson == "{}") return null
        
        return try {
            // 先尝试解析为通用 Map，然后手动构造对象
            val map = gson.fromJson(metaJson, Map::class.java) as? Map<String, Any?> ?: return null
            
            when (type.lowercase()) {
                "paper" -> {
                    // 处理 authors - 可能是数组或字符串
                    val authors = when (val authorsRaw = map["authors"]) {
                        is List<*> -> authorsRaw.mapNotNull { it?.toString() }
                        is String -> if (authorsRaw.isNotBlank()) authorsRaw.split(",").map { it.trim() } else emptyList()
                        else -> emptyList()
                    }
                    // 处理 tags - 可能是数组或字符串
                    val tags = when (val tagsRaw = map["tags"]) {
                        is List<*> -> tagsRaw.mapNotNull { it?.toString() }
                        is String -> if (tagsRaw.isNotBlank()) tagsRaw.split(",").map { it.trim() } else emptyList()
                        else -> emptyList()
                    }
                    // 处理 year - 可能是 Int、Double 或 String
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
                "competition" -> {
                    ItemMetaData.CompetitionMeta(
                        organizer = map["organizer"]?.toString(),
                        prizePool = map["prizePool"]?.toString(),
                        deadline = map["deadline"]?.toString(),
                        theme = map["theme"]?.toString(),
                        competitionType = map["competitionType"]?.toString()
                    )
                }
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
            android.util.Log.w("ItemMapper", "解析 metaJson 失败: $metaJson, error: ${e.message}")
            null
        }
    }
    
    // ==================== Project Mapper ====================
    
    /**
     * Project DTO -> Entity
     */
    fun projectDtoToEntity(dto: NocoProjectDto): ProjectEntity {
        val createdAt = parseDate(dto.createdAt)
        
        return ProjectEntity(
            id = dto.id?.toString() ?: UUID.randomUUID().toString(),  // 将 Int ID 转为 String
            name = dto.name?.takeIf { it.isNotBlank() }
                ?: dto.title?.takeIf { it.isNotBlank() }
                ?: "未命名项目",
            description = dto.description,
            createdAt = createdAt
        )
    }
    
    /**
     * Project Entity -> Domain
     */
    fun projectEntityToDomain(entity: ProjectEntity): Project {
        return Project(
            id = entity.id,
            name = entity.name,
            description = entity.description
        )
    }
}


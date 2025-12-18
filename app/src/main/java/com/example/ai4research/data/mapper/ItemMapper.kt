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
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    
    /**
     * DTO -> Entity (网络数据转本地存储)
     */
    fun dtoToEntity(dto: NocoItemDto, projectName: String? = null): ItemEntity {
        val createdAt = try {
            dto.createdAt?.let { dateFormat.parse(it)?.time } ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
        
        return ItemEntity(
            id = dto.id ?: UUID.randomUUID().toString(),
            type = dto.type,
            title = dto.title,
            summary = dto.summary ?: "",
            contentMarkdown = dto.contentMd ?: "",
            originUrl = dto.originUrl,
            audioUrl = dto.audioUrl,
            status = dto.status ?: "processing",
            readStatus = dto.readStatus ?: "unread",
            projectId = dto.projectId,
            projectName = projectName,
            metaJson = dto.metaJson,
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
            projectId = entity.projectId,
            projectName = entity.projectName,
            metaData = parseMetaData(entity.metaJson, entity.type),
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
            metaJson = item.metaData?.let { gson.toJson(it) },
            createdAt = item.createdAt.time,
            syncedAt = System.currentTimeMillis()
        )
    }
    
    /**
     * 解析元数据 JSON
     */
    private fun parseMetaData(metaJson: String?, type: String): ItemMetaData? {
        if (metaJson.isNullOrEmpty()) return null
        
        return try {
            when (type.lowercase()) {
                "paper" -> gson.fromJson(metaJson, ItemMetaData.PaperMeta::class.java)
                "competition" -> gson.fromJson(metaJson, ItemMetaData.CompetitionMeta::class.java)
                "insight" -> gson.fromJson(metaJson, ItemMetaData.InsightMeta::class.java)
                "voice" -> gson.fromJson(metaJson, ItemMetaData.VoiceMeta::class.java)
                else -> null
            }
        } catch (e: JsonSyntaxException) {
            null
        }
    }
    
    // ==================== Project Mapper ====================
    
    /**
     * Project DTO -> Entity
     */
    fun projectDtoToEntity(dto: NocoProjectDto): ProjectEntity {
        val createdAt = try {
            dto.createdAt?.let { dateFormat.parse(it)?.time } ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
        
        return ProjectEntity(
            id = dto.id ?: UUID.randomUUID().toString(),
            name = dto.name,
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


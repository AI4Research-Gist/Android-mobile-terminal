package com.example.ai4research.data.repository

import com.example.ai4research.data.local.dao.ItemDao
import com.example.ai4research.data.local.dao.ProjectDao
import com.example.ai4research.data.mapper.ItemMapper
import com.example.ai4research.data.remote.api.NocoApiService
import com.example.ai4research.data.remote.dto.NocoItemDto
import com.example.ai4research.domain.model.ItemStatus
import com.example.ai4research.domain.model.ItemType
import com.example.ai4research.domain.model.ReadStatus
import com.example.ai4research.domain.model.ResearchItem
import com.example.ai4research.domain.repository.ItemRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ItemRepositoryImpl @Inject constructor(
    private val api: NocoApiService,
    private val itemDao: ItemDao,
    private val projectDao: ProjectDao
) : ItemRepository {

    private fun parseMetaJson(metaJson: String?): JsonElement? {
        if (metaJson.isNullOrBlank()) return null
        return runCatching { Json.parseToJsonElement(metaJson) }.getOrNull()
    }

    override fun observeItems(type: ItemType?, query: String?): Flow<List<ResearchItem>> {
        val cleanedQuery = query?.trim().orEmpty()

        val flow = when {
            cleanedQuery.isNotEmpty() && type != null ->
                itemDao.searchItemsByType(type.toServerString(), cleanedQuery)

            cleanedQuery.isNotEmpty() ->
                itemDao.searchItems(cleanedQuery)

            type != null ->
                itemDao.observeItemsByType(type.toServerString())

            else ->
                itemDao.observeAllItems()
        }

        return flow.map { list -> list.map(ItemMapper::entityToDomain) }
    }

    override suspend fun refreshItems(): Result<Unit> {
        return try {
            // 1) Sync projects first for name resolution
            val projects = api.getProjects().list
            val projectEntities = projects.map(ItemMapper::projectDtoToEntity)
            projectDao.insertProjects(projectEntities)

            val projectNameMap = projectEntities.associate { it.id to it.name }

            // 2) Sync items
            val items = api.getItems().list
            val itemEntities = items.map { dto ->
                val projectIdFromItem = dto.projectId?.toString()
                val projectNameFromItem = projectIdFromItem?.let { projectNameMap[it] }
                ItemMapper.dtoToEntity(
                    dto,
                    projectId = projectIdFromItem,
                    projectName = projectNameFromItem
                )
            }
            itemDao.insertItems(itemEntities)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getItem(id: String): ResearchItem? {
        return itemDao.getItemById(id)?.let(ItemMapper::entityToDomain)
    }

    override suspend fun createUrlItem(
        url: String,
        title: String?,
        note: String?,
        type: ItemType
    ): Result<ResearchItem> {
        return try {
            // 创建一个“待解析”条目（云端 AI 流水线后续可更新）
            val dto = NocoItemDto(
                title = title?.takeIf { it.isNotBlank() } ?: "待解析链接",
                type = type.toServerString(),
                summary = note?.orEmpty(),
                contentMd = "",
                originUrl = url,
                audioUrl = null,
                status = ItemStatus.PROCESSING.toServerString(),
                readStatus = ReadStatus.UNREAD.toServerString(),
                metaJson = null
            )

            val created = api.createItem(dto)
            val entity = ItemMapper.dtoToEntity(created)
            itemDao.insertItem(entity)
            Result.success(ItemMapper.entityToDomain(entity))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun createVoiceItem(
        title: String,
        audioUri: String,
        durationSeconds: Int,
        summary: String?
    ): Result<ResearchItem> {
        return try {
            val dto = NocoItemDto(
                title = title.ifBlank { "语音灵感" },
                type = ItemType.VOICE.toServerString(),
                summary = summary ?: "已录制语音（${durationSeconds}s）",
                contentMd = "",
                originUrl = null,
                audioUrl = audioUri,
                status = ItemStatus.DONE.toServerString(),
                readStatus = ReadStatus.UNREAD.toServerString(),
                metaJson = null
            )
            val created = api.createItem(dto)
            val entity = ItemMapper.dtoToEntity(created)
            itemDao.insertItem(entity)
            Result.success(ItemMapper.entityToDomain(entity))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun createImageItem(imageUri: String, summary: String?): Result<ResearchItem> {
        return try {
            val dto = NocoItemDto(
                title = "图片采集",
                type = ItemType.INSIGHT.toServerString(),
                summary = summary ?: "已采集图片（待 OCR/解析）",
                contentMd = "",
                originUrl = imageUri,
                audioUrl = null,
                status = ItemStatus.PROCESSING.toServerString(),
                readStatus = ReadStatus.UNREAD.toServerString(),
                metaJson = null
            )
            val created = api.createItem(dto)
            val entity = ItemMapper.dtoToEntity(created)
            itemDao.insertItem(entity)
            Result.success(ItemMapper.entityToDomain(entity))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateReadStatus(id: String, readStatus: ReadStatus): Result<Unit> {
        return try {
            // Local first (better UX)
            itemDao.updateReadStatus(id, readStatus.toServerString())

            // Best-effort remote sync
            val local = itemDao.getItemById(id)
            if (local != null) {
                val dto = NocoItemDto(
                    id = local.id,
                    title = local.title,
                    type = local.type,
                    summary = local.summary,
                    contentMd = local.contentMarkdown,
                    originUrl = local.originUrl,
                    audioUrl = local.audioUrl,
                    status = local.status,
                    readStatus = readStatus.toServerString(),
                    projectId = local.projectId?.toIntOrNull(),
                    metaJson = parseMetaJson(local.metaJson)
                )
                val updated = api.updateItem(id, dto)
                itemDao.insertItem(
                    ItemMapper.dtoToEntity(
                        updated,
                        projectId = local.projectId,
                        projectName = local.projectName
                    )
                )
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateItem(
        id: String,
        title: String?,
        summary: String?,
        content: String?,
        tags: List<String>?
    ): Result<Unit> {
        return try {
            val local = itemDao.getItemById(id) ?: return Result.failure(Exception("Item not found"))

            // Prepare update DTO
            val dto = NocoItemDto(
                id = local.id,
                title = title ?: local.title,
                type = local.type,
                summary = summary ?: local.summary,
                contentMd = content ?: local.contentMarkdown,
                originUrl = local.originUrl,
                audioUrl = local.audioUrl,
                status = local.status,
                readStatus = local.readStatus,
                projectId = local.projectId?.toIntOrNull(),
                tags = tags?.joinToString(","),
                metaJson = parseMetaJson(local.metaJson)
            )

            // Remote update
            val updated = api.updateItem(id, dto)

            // Local update
            itemDao.insertItem(
                ItemMapper.dtoToEntity(
                    updated,
                    projectId = local.projectId,
                    projectName = local.projectName
                )
            )

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateItemProject(id: String, projectId: String?): Result<Unit> {
        return try {
            val local = itemDao.getItemById(id) ?: return Result.failure(Exception("Item not found"))
            if (local.projectId == projectId) {
                return Result.success(Unit)
            }

            val projectName = projectId?.let { projectDao.getProjectById(it)?.name }

            // 优先按新模型：更新 items.project_id
            val projectIdInt = projectId?.toIntOrNull()
            val projectUpdateResult = runCatching {
                val dto = NocoItemDto(
                    id = local.id,
                    title = local.title,
                    type = local.type,
                    summary = local.summary,
                    contentMd = local.contentMarkdown,
                    originUrl = local.originUrl,
                    audioUrl = local.audioUrl,
                    status = local.status,
                    readStatus = local.readStatus,
                    projectId = projectIdInt,
                    metaJson = parseMetaJson(local.metaJson)
                )
                api.updateItem(id, dto)
            }

            if (projectUpdateResult.isSuccess) {
                val updated = projectUpdateResult.getOrNull()
                itemDao.updateProject(id, projectId, projectName)
                if (updated != null) {
                    itemDao.insertItem(
                        ItemMapper.dtoToEntity(
                            updated,
                            projectId = projectId,
                            projectName = projectName
                        )
                    )
                }
                return Result.success(Unit)
            }

            Result.failure(projectUpdateResult.exceptionOrNull() ?: Exception("更新项目失败"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteItem(id: String): Result<Unit> {
        return try {
            // Local delete first
            itemDao.deleteItemById(id)
            // Best-effort remote delete
            runCatching { api.deleteItem(id) }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

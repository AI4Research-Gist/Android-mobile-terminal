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
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ItemRepositoryImpl @Inject constructor(
    private val api: NocoApiService,
    private val itemDao: ItemDao,
    private val projectDao: ProjectDao
) : ItemRepository {

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
                ItemMapper.dtoToEntity(dto, projectName = dto.projectId?.let { projectNameMap[it] })
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
        note: String?
    ): Result<ResearchItem> {
        return try {
            // 创建一个“待解析”条目（云端 AI 流水线后续可更新）
            val dto = NocoItemDto(
                title = title?.takeIf { it.isNotBlank() } ?: "待解析链接",
                type = ItemType.INSIGHT.toServerString(),
                summary = note?.orEmpty(),
                contentMd = "",
                originUrl = url,
                audioUrl = null,
                status = ItemStatus.PROCESSING.toServerString(),
                readStatus = ReadStatus.UNREAD.toServerString(),
                projectId = null,
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
            val item = ResearchItem(
                id = UUID.randomUUID().toString(),
                type = ItemType.VOICE,
                title = title.ifBlank { "语音灵感" },
                summary = summary ?: "已录制语音（${durationSeconds}s）",
                contentMarkdown = "",
                originUrl = null,
                audioUrl = audioUri,
                status = ItemStatus.DONE,
                readStatus = ReadStatus.UNREAD,
                projectId = null,
                projectName = null,
                metaData = null,
                createdAt = Date()
            )
            val entity = ItemMapper.domainToEntity(item)
            itemDao.insertItem(entity)
            Result.success(ItemMapper.entityToDomain(entity))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun createImageItem(imageUri: String, summary: String?): Result<ResearchItem> {
        return try {
            val item = ResearchItem(
                id = UUID.randomUUID().toString(),
                type = ItemType.INSIGHT,
                title = "图片采集",
                summary = summary ?: "已采集图片（待 OCR/解析）",
                contentMarkdown = "",
                originUrl = imageUri,
                audioUrl = null,
                status = ItemStatus.PROCESSING,
                readStatus = ReadStatus.UNREAD,
                projectId = null,
                projectName = null,
                metaData = null,
                createdAt = Date()
            )
            val entity = ItemMapper.domainToEntity(item)
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
                    projectId = local.projectId,
                    metaJson = local.metaJson
                )
                val updated = api.updateItem(id, dto)
                itemDao.insertItem(ItemMapper.dtoToEntity(updated, projectName = local.projectName))
            }

            Result.success(Unit)
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



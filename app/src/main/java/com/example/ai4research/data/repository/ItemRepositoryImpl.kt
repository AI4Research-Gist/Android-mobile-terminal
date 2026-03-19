package com.example.ai4research.data.repository

import com.example.ai4research.core.security.TokenManager
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
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ItemRepositoryImpl @Inject constructor(
    private val api: NocoApiService,
    private val itemDao: ItemDao,
    private val projectDao: ProjectDao,
    private val tokenManager: TokenManager
) : ItemRepository {

    private val gson = Gson()

    private fun currentUserId(): String? = tokenManager.getCurrentUserId()

    private fun requireCurrentUserId(): Result<String> {
        val userId = currentUserId()
        return if (userId.isNullOrBlank()) {
            Result.failure(IllegalStateException("User must be logged in"))
        } else {
            Result.success(userId)
        }
    }

    private fun parseMetaJson(metaJson: String?): JsonElement? {
        if (metaJson.isNullOrBlank()) return null
        return runCatching { Json.parseToJsonElement(metaJson) }.getOrNull()
    }

    private fun mergeMetaJson(
        existingMetaJson: String?,
        incomingMetaJson: String?,
        note: String?
    ): String? {
        val merged = mutableMapOf<String, Any?>()

        fun merge(json: String?) {
            if (json.isNullOrBlank()) return
            val parsed = runCatching {
                gson.fromJson(json, Map::class.java) as? Map<String, Any?>
            }.getOrNull() ?: return
            merged.putAll(parsed)
        }

        merge(existingMetaJson)
        merge(incomingMetaJson)
        note?.let { merged["note"] = it }

        return if (merged.isEmpty()) null else gson.toJson(merged)
    }

    private fun buildOwnerWhereClause(ownerUserId: String): String {
        return "(ownerId,eq,$ownerUserId)"
    }

    private fun mergeServerItem(remote: NocoItemDto, fallback: NocoItemDto): NocoItemDto {
        return remote.copy(
            ownerUserId = remote.ownerUserId ?: fallback.ownerUserId,
            title = remote.title ?: fallback.title,
            type = remote.type ?: fallback.type,
            summary = remote.summary ?: fallback.summary,
            contentMd = remote.contentMd ?: fallback.contentMd,
            originUrl = remote.originUrl ?: fallback.originUrl,
            audioUrl = remote.audioUrl ?: fallback.audioUrl,
            status = remote.status ?: fallback.status,
            readStatus = remote.readStatus ?: fallback.readStatus,
            tags = remote.tags ?: fallback.tags,
            projectId = remote.projectId ?: fallback.projectId,
            metaJson = remote.metaJson ?: fallback.metaJson
        )
    }

    private fun buildItemDto(local: com.example.ai4research.data.local.entity.ItemEntity): NocoItemDto {
        return NocoItemDto(
            id = local.id.toIntOrNull(),
            ownerUserId = local.ownerUserId,
            title = local.title,
            type = local.type,
            summary = local.summary,
            contentMd = local.contentMarkdown,
            originUrl = local.originUrl,
            audioUrl = local.audioUrl,
            status = local.status,
            readStatus = local.readStatus,
            projectId = local.projectId?.toIntOrNull(),
            metaJson = parseMetaJson(local.metaJson)
        )
    }

    override fun observeItems(type: ItemType?, query: String?): Flow<List<ResearchItem>> {
        val ownerUserId = currentUserId() ?: return flowOf(emptyList())
        val cleanedQuery = query?.trim().orEmpty()

        val flow = when {
            cleanedQuery.isNotEmpty() && type != null ->
                itemDao.searchItemsByType(ownerUserId, type.toServerString(), cleanedQuery)

            cleanedQuery.isNotEmpty() ->
                itemDao.searchItems(ownerUserId, cleanedQuery)

            type != null ->
                itemDao.observeItemsByType(ownerUserId, type.toServerString())

            else ->
                itemDao.observeAllItems(ownerUserId)
        }

        return flow.map { list -> list.map(ItemMapper::entityToDomain) }
    }

    override fun observeItemsByReadStatus(type: ItemType, readStatus: ReadStatus): Flow<List<ResearchItem>> {
        val ownerUserId = currentUserId() ?: return flowOf(emptyList())
        return itemDao.observeItemsByTypeAndReadStatus(
            ownerUserId,
            type.toServerString(),
            readStatus.toServerString().substringBefore(" ")
        ).map { list -> list.map(ItemMapper::entityToDomain) }
    }

    override fun observeItemsByProject(type: ItemType, projectId: String): Flow<List<ResearchItem>> {
        val ownerUserId = currentUserId() ?: return flowOf(emptyList())
        return itemDao.observeItemsByTypeAndProject(
            ownerUserId,
            type.toServerString(),
            projectId
        ).map { list -> list.map(ItemMapper::entityToDomain) }
    }

    override fun observeStarredItems(type: ItemType): Flow<List<ResearchItem>> {
        val ownerUserId = currentUserId() ?: return flowOf(emptyList())
        return itemDao.observeStarredItemsByType(ownerUserId, type.toServerString())
            .map { list -> list.map(ItemMapper::entityToDomain) }
    }

    override suspend fun refreshItems(): Result<Unit> {
        val ownerUserId = requireCurrentUserId().getOrElse { return Result.failure(it) }

        return try {
            val projectWhere = buildOwnerWhereClause(ownerUserId)
            val projects = api.getProjects(where = projectWhere).list
                .filter { !it.name.isNullOrBlank() || !it.title.isNullOrBlank() }

            val projectEntities = projects.map { dto ->
                ItemMapper.projectDtoToEntity(dto, ownerUserId)
            }
            projectDao.deleteAllProjectsByOwner(ownerUserId)
            projectDao.insertProjects(projectEntities)
            val projectNameMap = projectEntities.associate { it.id to it.name }

            val itemWhere = buildOwnerWhereClause(ownerUserId)
            val items = api.getItems(where = itemWhere).list
                .filter { !it.title.isNullOrBlank() && !it.type.isNullOrBlank() }

            val itemEntities = items.map { dto ->
                val projectId = dto.projectId?.toString()
                val projectName = projectId?.let { projectNameMap[it] }
                ItemMapper.dtoToEntity(
                    dto = dto,
                    projectId = projectId,
                    projectName = projectName,
                    ownerUserId = ownerUserId
                )
            }
            itemDao.deleteAllItemsByOwner(ownerUserId)
            itemDao.insertItems(itemEntities)
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("ItemRepository", "Failed to refresh items", e)
            Result.failure(e)
        }
    }

    override suspend fun getItem(id: String): ResearchItem? {
        val ownerUserId = currentUserId() ?: return null
        return itemDao.getItemById(ownerUserId, id)?.let(ItemMapper::entityToDomain)
    }

    override suspend fun createUrlItem(
        url: String,
        title: String?,
        note: String?,
        type: ItemType
    ): Result<ResearchItem> {
        val ownerUserId = requireCurrentUserId().getOrElse { return Result.failure(it) }

        return try {
            val dto = NocoItemDto(
                ownerUserId = ownerUserId,
                title = title?.takeIf { it.isNotBlank() } ?: "待解析链接",
                type = type.toServerString(),
                summary = "",
                contentMd = "",
                originUrl = url,
                audioUrl = null,
                status = ItemStatus.PROCESSING.toServerString(),
                readStatus = ReadStatus.UNREAD.toServerString(),
                metaJson = parseMetaJson(mergeMetaJson(null, null, note))
            )

            val created = mergeServerItem(api.createItem(dto), dto)
            val entity = ItemMapper.dtoToEntity(created, ownerUserId = ownerUserId)
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
        val ownerUserId = requireCurrentUserId().getOrElse { return Result.failure(it) }

        return try {
            val dto = NocoItemDto(
                ownerUserId = ownerUserId,
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
            val created = mergeServerItem(api.createItem(dto), dto)
            val entity = ItemMapper.dtoToEntity(created, ownerUserId = ownerUserId)
            itemDao.insertItem(entity)
            Result.success(ItemMapper.entityToDomain(entity))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun createImageItem(imageUri: String, summary: String?): Result<ResearchItem> {
        val ownerUserId = requireCurrentUserId().getOrElse { return Result.failure(it) }

        return try {
            val dto = NocoItemDto(
                ownerUserId = ownerUserId,
                title = "图片采集",
                type = ItemType.INSIGHT.toServerString(),
                summary = summary ?: "已采集图片（待 OCR / 解析）",
                contentMd = "",
                originUrl = imageUri,
                audioUrl = null,
                status = ItemStatus.PROCESSING.toServerString(),
                readStatus = ReadStatus.UNREAD.toServerString(),
                metaJson = null
            )
            val created = mergeServerItem(api.createItem(dto), dto)
            val entity = ItemMapper.dtoToEntity(created, ownerUserId = ownerUserId)
            itemDao.insertItem(entity)
            Result.success(ItemMapper.entityToDomain(entity))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun createFullItem(
        title: String,
        summary: String,
        contentMd: String,
        originUrl: String?,
        type: ItemType,
        status: ItemStatus,
        metaJson: String?,
        note: String?,
        tags: List<String>?
    ): Result<ResearchItem> {
        val ownerUserId = requireCurrentUserId().getOrElse { return Result.failure(it) }

        return try {
            val dto = NocoItemDto(
                ownerUserId = ownerUserId,
                title = title,
                type = type.toServerString(),
                summary = summary,
                contentMd = contentMd,
                originUrl = originUrl,
                audioUrl = null,
                status = status.toServerString(),
                readStatus = ReadStatus.UNREAD.toServerString(),
                tags = tags?.joinToString(","),
                metaJson = parseMetaJson(mergeMetaJson(null, metaJson, note))
            )

            val created = mergeServerItem(api.createItem(dto), dto)
            val entity = ItemMapper.dtoToEntity(created, ownerUserId = ownerUserId)
            itemDao.insertItem(entity)
            Result.success(ItemMapper.entityToDomain(entity))
        } catch (e: Exception) {
            android.util.Log.e("ItemRepository", "Failed to create full item", e)
            Result.failure(e)
        }
    }

    override suspend fun updateItemType(id: String, type: ItemType): Result<Unit> {
        val ownerUserId = requireCurrentUserId().getOrElse { return Result.failure(it) }

        return try {
            val local = itemDao.getItemById(ownerUserId, id) ?: return Result.failure(Exception("Item not found"))
            val requestDto = buildItemDto(local).copy(type = type.toServerString())
            val updated = mergeServerItem(api.updateItem(
                id,
                requestDto
            ), requestDto)
            itemDao.insertItem(
                ItemMapper.dtoToEntity(
                    updated,
                    projectId = local.projectId,
                    projectName = local.projectName,
                    ownerUserId = ownerUserId
                )
            )
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("ItemRepository", "Failed to update item type", e)
            Result.failure(e)
        }
    }

    override suspend fun updateReadStatus(id: String, readStatus: ReadStatus): Result<Unit> {
        val ownerUserId = requireCurrentUserId().getOrElse { return Result.failure(it) }

        return try {
            itemDao.updateReadStatus(ownerUserId, id, readStatus.toServerString())
            val local = itemDao.getItemById(ownerUserId, id)
            if (local != null) {
                val requestDto = buildItemDto(local).copy(readStatus = readStatus.toServerString())
                val updated = mergeServerItem(api.updateItem(
                    id,
                    requestDto
                ), requestDto)
                itemDao.insertItem(
                    ItemMapper.dtoToEntity(
                        updated,
                        projectId = local.projectId,
                        projectName = local.projectName,
                        ownerUserId = ownerUserId
                    )
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateStarred(id: String, isStarred: Boolean): Result<Unit> {
        val ownerUserId = requireCurrentUserId().getOrElse { return Result.failure(it) }

        return try {
            itemDao.updateStarred(ownerUserId, id, isStarred)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateItem(
        id: String,
        title: String?,
        summary: String?,
        note: String?,
        content: String?,
        tags: List<String>?,
        metaJson: String?
    ): Result<Unit> {
        val ownerUserId = requireCurrentUserId().getOrElse { return Result.failure(it) }

        return try {
            val local = itemDao.getItemById(ownerUserId, id) ?: return Result.failure(Exception("Item not found"))
            val dto = buildItemDto(local).copy(
                title = title ?: local.title,
                summary = summary ?: local.summary,
                contentMd = content ?: local.contentMarkdown,
                tags = tags?.joinToString(","),
                metaJson = parseMetaJson(mergeMetaJson(local.metaJson, metaJson, note))
            )

            val updated = mergeServerItem(api.updateItem(id, dto), dto)
            itemDao.insertItem(
                ItemMapper.dtoToEntity(
                    updated,
                    projectId = local.projectId,
                    projectName = local.projectName,
                    ownerUserId = ownerUserId
                )
            )

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateItemProject(id: String, projectId: String?): Result<Unit> {
        val ownerUserId = requireCurrentUserId().getOrElse { return Result.failure(it) }

        return try {
            val local = itemDao.getItemById(ownerUserId, id) ?: return Result.failure(Exception("Item not found"))
            if (local.projectId == projectId) {
                return Result.success(Unit)
            }

            val projectName = projectId?.let { projectDao.getProjectById(ownerUserId, it)?.name }
            val requestDto = buildItemDto(local).copy(projectId = projectId?.toIntOrNull())
            val updated = mergeServerItem(api.updateItem(
                id,
                requestDto
            ), requestDto)

            itemDao.updateProject(ownerUserId, id, projectId, projectName)
            itemDao.insertItem(
                ItemMapper.dtoToEntity(
                    updated,
                    projectId = projectId,
                    projectName = projectName,
                    ownerUserId = ownerUserId
                )
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteItem(id: String): Result<Unit> {
        val ownerUserId = requireCurrentUserId().getOrElse { return Result.failure(it) }

        return try {
            itemDao.deleteItemById(ownerUserId, id)
            runCatching { api.deleteItem(id) }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

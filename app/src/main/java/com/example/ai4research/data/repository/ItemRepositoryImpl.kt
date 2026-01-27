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
    
    override fun observeItemsByReadStatus(type: ItemType, readStatus: ReadStatus): Flow<List<ResearchItem>> {
        return itemDao.observeItemsByTypeAndReadStatus(
            type.toServerString(),
            readStatus.toServerString().substringBefore(" ")  // "unread (未读)" -> "unread"
        ).map { list -> list.map(ItemMapper::entityToDomain) }
    }
    
    override fun observeItemsByProject(type: ItemType, projectId: String): Flow<List<ResearchItem>> {
        return itemDao.observeItemsByTypeAndProject(
            type.toServerString(),
            projectId
        ).map { list -> list.map(ItemMapper::entityToDomain) }
    }
    
    override fun observeStarredItems(type: ItemType): Flow<List<ResearchItem>> {
        return itemDao.observeStarredItemsByType(type.toServerString())
            .map { list -> list.map(ItemMapper::entityToDomain) }
    }

    override suspend fun refreshItems(): Result<Unit> {
        return try {
            android.util.Log.d("ItemRepository", "Starting data refresh from NocoDB...")
            
            // 1) Sync projects first for name resolution
            val projectsResponse = api.getProjects()
            val projects = projectsResponse.list
            android.util.Log.d("ItemRepository", "Fetched ${projects.size} projects from API (before filter)")
            
            // 过滤无效项目：只保留 name 或 Title 不为空的
            val validProjects = projects.filter { dto ->
                !dto.name.isNullOrBlank() || !dto.title.isNullOrBlank()
            }
            android.util.Log.d("ItemRepository", "Valid projects after filter: ${validProjects.size}")
            
            val projectEntities = validProjects.map(ItemMapper::projectDtoToEntity)
            projectDao.insertProjects(projectEntities)

            val projectNameMap = projectEntities.associate { it.id to it.name }

            // 2) Sync items
            val itemsResponse = api.getItems()
            val items = itemsResponse.list
            android.util.Log.d("ItemRepository", "Fetched ${items.size} items from API (before filter)")
            
            // 过滤无效数据：只保留 title 和 type 都不为 null 的条目
            val validItems = items.filter { dto ->
                !dto.title.isNullOrBlank() && !dto.type.isNullOrBlank()
            }
            android.util.Log.d("ItemRepository", "Valid items after filter: ${validItems.size}")
            
            validItems.forEachIndexed { index, dto ->
                android.util.Log.d("ItemRepository", "Item[$index]: id=${dto.id}, title=${dto.title}, type=${dto.type}, project_id=${dto.projectId}")
            }
            
            val itemEntities = validItems.map { dto ->
                val projectIdFromItem = dto.projectId?.toString()
                val projectNameFromItem = projectIdFromItem?.let { projectNameMap[it] }
                ItemMapper.dtoToEntity(
                    dto,
                    projectId = projectIdFromItem,
                    projectName = projectNameFromItem
                )
            }
            itemDao.insertItems(itemEntities)
            android.util.Log.d("ItemRepository", "Inserted ${itemEntities.size} items to local DB")

            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("ItemRepository", "Error refreshing items: ${e.message}", e)
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
    
    override suspend fun createFullItem(
        title: String,
        summary: String,
        contentMd: String,
        originUrl: String?,
        type: ItemType,
        status: ItemStatus,
        metaJson: String?,
        tags: List<String>?
    ): Result<ResearchItem> {
        return try {
            android.util.Log.d("ItemRepository", "========== createFullItem 开始 ==========")
            android.util.Log.d("ItemRepository", "title=$title")
            android.util.Log.d("ItemRepository", "type=${type.toServerString()}")
            android.util.Log.d("ItemRepository", "summary=${summary.take(50)}...")
            android.util.Log.d("ItemRepository", "originUrl=$originUrl")
            android.util.Log.d("ItemRepository", "metaJson原始: $metaJson")
            
            // 解析 metaJson，如果解析失败则不传递
            val parsedMetaJson = try {
                metaJson?.let { parseMetaJson(it) }
            } catch (e: Exception) {
                android.util.Log.w("ItemRepository", "metaJson 解析失败，跳过: ${e.message}")
                null
            }
            
            val dto = NocoItemDto(
                title = title,
                type = type.toServerString(),
                summary = summary,
                contentMd = contentMd,
                originUrl = originUrl,
                audioUrl = null,
                status = status.toServerString(),
                readStatus = ReadStatus.UNREAD.toServerString(),
                tags = tags?.joinToString(","),
                metaJson = parsedMetaJson
            )
            
            // 打印完整的 DTO 内容
            val json = Json { prettyPrint = false; encodeDefaults = false }
            val dtoJson = json.encodeToString(NocoItemDto.serializer(), dto)
            android.util.Log.d("ItemRepository", "DTO JSON: $dtoJson")
            
            // 创建远端记录
            val created = api.createItem(dto)
            android.util.Log.d("ItemRepository", "✅ API 调用成功! created.id=${created.id}, created.title=${created.title}")
            
            // 本地入库
            val entity = ItemMapper.dtoToEntity(created)
            android.util.Log.d("ItemRepository", "Entity 已映射: entity.id=${entity.id}, entity.type=${entity.type}")
            
            itemDao.insertItem(entity)
            android.util.Log.d("ItemRepository", "✅ 本地数据库插入成功!")
            
            val result = ItemMapper.entityToDomain(entity)
            android.util.Log.d("ItemRepository", "========== createFullItem 完成 ==========")
            
            Result.success(result)
        } catch (e: Exception) {
            android.util.Log.e("ItemRepository", "❌ createFullItem 失败: ${e.message}", e)
            e.printStackTrace()
            Result.failure(e)
        }
    }
    
    override suspend fun updateItemType(id: String, type: ItemType): Result<Unit> {
        return try {
            val local = itemDao.getItemById(id) ?: return Result.failure(Exception("Item not found"))
            
            // 更新远端
            val dto = NocoItemDto(
                id = local.id.toIntOrNull(),
                title = local.title,
                type = type.toServerString(),
                summary = local.summary,
                contentMd = local.contentMarkdown,
                originUrl = local.originUrl,
                audioUrl = local.audioUrl,
                status = local.status,
                readStatus = local.readStatus,
                projectId = local.projectId?.toIntOrNull(),
                metaJson = parseMetaJson(local.metaJson)
            )
            
            val updated = api.updateItem(id, dto)
            
            // 更新本地
            itemDao.insertItem(
                ItemMapper.dtoToEntity(
                    updated,
                    projectId = local.projectId,
                    projectName = local.projectName
                )
            )
            
            android.util.Log.d("ItemRepository", "Item type updated: id=$id, newType=$type")
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("ItemRepository", "Failed to update item type: ${e.message}", e)
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
                    id = local.id.toIntOrNull(),
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
    
    override suspend fun updateStarred(id: String, isStarred: Boolean): Result<Unit> {
        return try {
            // 本地更新（仅本地存储，无需同步远端）
            itemDao.updateStarred(id, isStarred)
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
        tags: List<String>?,
        metaJson: String?
    ): Result<Unit> {
        return try {
            val local = itemDao.getItemById(id) ?: return Result.failure(Exception("Item not found"))

            // 处理 metaJson - 如果传入新值则使用新值，否则保留原值
            val finalMetaJson = if (metaJson != null) {
                parseMetaJson(metaJson)
            } else {
                parseMetaJson(local.metaJson)
            }

            // Prepare update DTO
            val dto = NocoItemDto(
                id = local.id.toIntOrNull(),
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
                metaJson = finalMetaJson
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
                    id = local.id.toIntOrNull(),
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

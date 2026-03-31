package com.example.ai4research.data.repository

import com.example.ai4research.core.security.TokenManager
import com.example.ai4research.data.local.dao.ItemDao
import com.example.ai4research.data.local.dao.ItemRelationDao
import com.example.ai4research.data.local.dao.ProjectDao
import com.example.ai4research.data.mapper.ItemMapper
import com.example.ai4research.data.mapper.ItemRelationMapper
import com.example.ai4research.data.remote.api.NocoApiService
import com.example.ai4research.data.remote.dto.NocoProjectDto
import com.example.ai4research.domain.model.ItemType
import com.example.ai4research.domain.model.Project
import com.example.ai4research.domain.model.ProjectOverview
import com.example.ai4research.domain.model.ProjectOverviewStats
import com.example.ai4research.domain.model.RelationType
import com.example.ai4research.domain.repository.ProjectRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProjectRepositoryImpl @Inject constructor(
    private val api: NocoApiService,
    private val itemDao: ItemDao,
    private val itemRelationDao: ItemRelationDao,
    private val projectDao: ProjectDao,
    private val tokenManager: TokenManager
) : ProjectRepository {

    private fun currentUserId(): String? = tokenManager.getCurrentUserId()

    override fun observeProjects(): Flow<List<Project>> {
        val ownerUserId = currentUserId() ?: return flowOf(emptyList())
        return projectDao.observeAllProjects(ownerUserId)
            .map { list -> list.map(ItemMapper::projectEntityToDomain) }
    }

    override suspend fun refreshProjects(): Result<Unit> {
        val ownerUserId = currentUserId()
            ?: return Result.failure(IllegalStateException("User must be logged in"))

        return try {
            val where = "(ownerId,eq,$ownerUserId)"
            val projects = api.getProjects(where = where).list
            val entities = projects.map { dto -> ItemMapper.projectDtoToEntity(dto, ownerUserId) }
            projectDao.deleteAllProjectsByOwner(ownerUserId)
            projectDao.insertProjects(entities)
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("ProjectRepository", "Failed to refresh projects", e)
            Result.failure(e)
        }
    }

    override suspend fun getProject(id: String): Project? {
        val ownerUserId = currentUserId() ?: return null
        return projectDao.getProjectById(ownerUserId, id)?.let(ItemMapper::projectEntityToDomain)
    }

    override suspend fun createProject(name: String, description: String?): Result<Project> {
        val ownerUserId = currentUserId()
            ?: return Result.failure(IllegalStateException("User must be logged in"))

        return try {
            val createDto = NocoProjectDto(
                ownerUserId = ownerUserId,
                title = name,
                name = name,
                description = description
            )
            val responseDto = api.createProject(createDto)
            val entity = ItemMapper.projectDtoToEntity(responseDto, ownerUserId)
            projectDao.insertProject(entity)
            Result.success(ItemMapper.projectEntityToDomain(entity))
        } catch (e: Exception) {
            android.util.Log.e("ProjectRepository", "Failed to create project", e)
            Result.failure(e)
        }
    }

    override suspend fun getProjectOverview(projectId: String): ProjectOverview? {
        val ownerUserId = currentUserId() ?: return null
        val project = projectDao.getProjectById(ownerUserId, projectId)?.let(ItemMapper::projectEntityToDomain) ?: return null
        val projectItems = itemDao.getItemsByProject(ownerUserId, projectId).map(ItemMapper::entityToDomain)
        val projectItemIds = projectItems.map { it.id }
        val relations = if (projectItemIds.isEmpty()) {
            emptyList()
        } else {
            itemRelationDao.getRelationsForItems(ownerUserId, projectItemIds).map(ItemRelationMapper::entityToDomain)
        }

        val keyPapers = projectItems
            .filter { it.type == ItemType.PAPER }
            .sortedWith(
                compareByDescending<com.example.ai4research.domain.model.ResearchItem> { it.isStarred }
                    .thenByDescending { (it.metaData as? com.example.ai4research.domain.model.ItemMetaData.PaperMeta)?.identifier != null }
                    .thenByDescending { (it.metaData as? com.example.ai4research.domain.model.ItemMetaData.PaperMeta)?.readingCard?.isEmpty() == false }
                    .thenByDescending { it.createdAt.time }
            )
            .take(5)

        val recentInsights = projectItems
            .filter { it.type == ItemType.INSIGHT }
            .sortedByDescending { it.createdAt.time }
            .take(10)

        return ProjectOverview(
            project = project,
            recentItems = projectItems.sortedByDescending { it.createdAt.time }.take(10),
            keyPapers = keyPapers,
            recentInsights = recentInsights,
            stats = ProjectOverviewStats(
                totalItems = projectItems.size,
                paperCount = projectItems.count { it.type == ItemType.PAPER },
                articleCount = projectItems.count { it.type == ItemType.ARTICLE },
                insightCount = projectItems.count { it.type == ItemType.INSIGHT },
                duplicateRelationCount = relations.count { it.relationType == RelationType.DUPLICATE_OF },
                articlePaperRelationCount = relations.count { it.relationType == RelationType.ARTICLE_MENTIONS_PAPER || it.relationType == RelationType.ARTICLE_RELATED_PAPER }
            )
        )
    }

    override suspend fun deleteProject(projectId: String): Result<Unit> {
        val ownerUserId = currentUserId()
            ?: return Result.failure(IllegalStateException("User must be logged in"))

        return try {
            api.deleteProject(projectId)
            projectDao.getProjectById(ownerUserId, projectId)?.let { entity ->
                projectDao.deleteProject(entity)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("ProjectRepository", "Failed to delete project", e)
            Result.failure(e)
        }
    }
}

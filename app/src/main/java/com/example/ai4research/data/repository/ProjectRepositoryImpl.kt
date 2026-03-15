package com.example.ai4research.data.repository

import com.example.ai4research.core.security.TokenManager
import com.example.ai4research.data.local.dao.ProjectDao
import com.example.ai4research.data.mapper.ItemMapper
import com.example.ai4research.data.remote.api.NocoApiService
import com.example.ai4research.data.remote.dto.NocoProjectDto
import com.example.ai4research.domain.model.Project
import com.example.ai4research.domain.repository.ProjectRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProjectRepositoryImpl @Inject constructor(
    private val api: NocoApiService,
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

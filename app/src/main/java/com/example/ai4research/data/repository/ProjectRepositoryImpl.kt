package com.example.ai4research.data.repository

import com.example.ai4research.data.local.dao.ProjectDao
import com.example.ai4research.data.mapper.ItemMapper
import com.example.ai4research.data.remote.api.NocoApiService
import com.example.ai4research.domain.model.Project
import com.example.ai4research.domain.repository.ProjectRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProjectRepositoryImpl @Inject constructor(
    private val api: NocoApiService,
    private val projectDao: ProjectDao
) : ProjectRepository {

    override fun observeProjects(): Flow<List<Project>> {
        return projectDao.observeAllProjects()
            .map { list -> list.map(ItemMapper::projectEntityToDomain) }
    }

    override suspend fun refreshProjects(): Result<Unit> {
        return try {
            val projects = api.getProjects().list
            val entities = projects.map(ItemMapper::projectDtoToEntity)
            projectDao.insertProjects(entities)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getProject(id: String): Project? {
        return projectDao.getProjectById(id)?.let(ItemMapper::projectEntityToDomain)
    }
}

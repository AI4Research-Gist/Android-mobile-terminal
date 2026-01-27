package com.example.ai4research.data.repository

import com.example.ai4research.data.local.dao.ProjectDao
import com.example.ai4research.data.mapper.ItemMapper
import com.example.ai4research.data.remote.api.NocoApiService
import com.example.ai4research.data.remote.dto.NocoProjectDto
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
            // 先清除本地所有项目，再插入最新数据，确保前后端同步
            projectDao.deleteAllProjects()
            projectDao.insertProjects(entities)
            android.util.Log.d("ProjectRepository", "项目同步成功: ${entities.size} 个项目")
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("ProjectRepository", "项目同步失败", e)
            Result.failure(e)
        }
    }

    override suspend fun getProject(id: String): Project? {
        return projectDao.getProjectById(id)?.let(ItemMapper::projectEntityToDomain)
    }
    
    override suspend fun createProject(name: String, description: String?): Result<Project> {
        return try {
            android.util.Log.d("ProjectRepository", "创建项目: name=$name, description=$description")
            
            // 创建 DTO (NocoDB 使用 Title 字段)
            val createDto = NocoProjectDto(
                title = name,
                name = name,
                description = description
            )
            
            // 发送到 NocoDB
            val responseDto = api.createProject(createDto)
            android.util.Log.d("ProjectRepository", "NocoDB 返回: id=${responseDto.id}, title=${responseDto.title}")
            
            // 转换并保存到本地
            val entity = ItemMapper.projectDtoToEntity(responseDto)
            projectDao.insertProject(entity)
            
            // 返回 Domain 模型
            val project = ItemMapper.projectEntityToDomain(entity)
            android.util.Log.d("ProjectRepository", "项目创建成功: ${project.name}")
            
            Result.success(project)
        } catch (e: Exception) {
            android.util.Log.e("ProjectRepository", "创建项目失败", e)
            Result.failure(e)
        }
    }
    
    override suspend fun deleteProject(projectId: String): Result<Unit> {
        return try {
            android.util.Log.d("ProjectRepository", "删除项目: id=$projectId")
            
            // 从远程删除
            api.deleteProject(projectId)
            
            // 从本地删除
            projectDao.getProjectById(projectId)?.let { entity ->
                projectDao.deleteProject(entity)
            }
            
            android.util.Log.d("ProjectRepository", "项目删除成功: $projectId")
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("ProjectRepository", "删除项目失败", e)
            Result.failure(e)
        }
    }
}

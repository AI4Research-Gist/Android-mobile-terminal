package com.example.ai4research.domain.repository

import com.example.ai4research.domain.model.Project
import kotlinx.coroutines.flow.Flow

/**
 * Project Repository (Domain Interface)
 */
interface ProjectRepository {
    fun observeProjects(): Flow<List<Project>>

    suspend fun refreshProjects(): Result<Unit>

    suspend fun getProject(id: String): Project?
    
    /**
     * 创建新项目
     * @param name 项目名称
     * @param description 项目描述（可选）
     * @return 创建的项目
     */
    suspend fun createProject(name: String, description: String? = null): Result<Project>
    
    /**
     * 删除项目
     * @param projectId 项目ID
     * @return 删除结果
     */
    suspend fun deleteProject(projectId: String): Result<Unit>
}

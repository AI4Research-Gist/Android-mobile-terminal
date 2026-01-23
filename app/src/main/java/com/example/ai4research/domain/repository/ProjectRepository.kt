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
}

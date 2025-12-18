package com.example.ai4research.data.local.dao

import androidx.room.*
import com.example.ai4research.data.local.entity.ProjectEntity
import kotlinx.coroutines.flow.Flow

/**
 * Project DAO
 */
@Dao
interface ProjectDao {
    /**
     * 观察所有项目
     */
    @Query("SELECT * FROM projects ORDER BY created_at DESC")
    fun observeAllProjects(): Flow<List<ProjectEntity>>
    
    /**
     * 获取单个项目
     */
    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getProjectById(id: String): ProjectEntity?
    
    /**
     * 插入或替换
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: ProjectEntity)
    
    /**
     * 批量插入
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProjects(projects: List<ProjectEntity>)
    
    /**
     * 更新项目
     */
    @Update
    suspend fun updateProject(project: ProjectEntity)
    
    /**
     * 删除项目
     */
    @Delete
    suspend fun deleteProject(project: ProjectEntity)
}


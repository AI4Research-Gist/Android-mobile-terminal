package com.example.ai4research.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.ai4research.data.local.entity.ProjectEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects WHERE owner_user_id = :ownerUserId ORDER BY created_at DESC")
    fun observeAllProjects(ownerUserId: String): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id AND owner_user_id = :ownerUserId")
    suspend fun getProjectById(ownerUserId: String, id: String): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: ProjectEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProjects(projects: List<ProjectEntity>)

    @Update
    suspend fun updateProject(project: ProjectEntity)

    @Delete
    suspend fun deleteProject(project: ProjectEntity)

    @Query("DELETE FROM projects WHERE owner_user_id = :ownerUserId")
    suspend fun deleteAllProjectsByOwner(ownerUserId: String)
}

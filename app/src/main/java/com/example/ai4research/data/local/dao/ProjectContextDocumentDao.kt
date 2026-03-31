package com.example.ai4research.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.ai4research.data.local.entity.ProjectContextDocumentEntity

@Dao
interface ProjectContextDocumentDao {
    @Query(
        """
        SELECT * FROM project_context_documents
        WHERE owner_user_id = :ownerUserId AND project_id = :projectId
        LIMIT 1
        """
    )
    suspend fun getByProjectId(ownerUserId: String, projectId: String): ProjectContextDocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(document: ProjectContextDocumentEntity)

    @Query("DELETE FROM project_context_documents WHERE owner_user_id = :ownerUserId AND project_id = :projectId")
    suspend fun deleteByProjectId(ownerUserId: String, projectId: String)
}

package com.example.ai4research.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "project_context_documents",
    indices = [
        Index(value = ["owner_user_id", "project_id"], unique = true)
    ]
)
data class ProjectContextDocumentEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "owner_user_id")
    val ownerUserId: String,

    @ColumnInfo(name = "project_id")
    val projectId: String,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "markdown_path")
    val markdownPath: String,

    @ColumnInfo(name = "summary")
    val summary: String,

    @ColumnInfo(name = "keywords")
    val keywords: String,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)

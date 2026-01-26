package com.example.ai4research.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.example.ai4research.data.local.converter.RoomConverters

/**
 * Room Entity - 本地数据库表结构
 * 遵循 Single Source of Truth 原则
 */
@Entity(tableName = "items")
@TypeConverters(RoomConverters::class)
data class ItemEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    
    @ColumnInfo(name = "type")
    val type: String,  // paper, competition, insight, voice
    
    @ColumnInfo(name = "title")
    val title: String,
    
    @ColumnInfo(name = "summary")
    val summary: String,
    
    @ColumnInfo(name = "content_md")
    val contentMarkdown: String,
    
    @ColumnInfo(name = "origin_url")
    val originUrl: String?,
    
    @ColumnInfo(name = "audio_url")
    val audioUrl: String?,
    
    @ColumnInfo(name = "status")
    val status: String,  // processing (解析中) / done (完成) / failed (失败)
    
    @ColumnInfo(name = "read_status")
    val readStatus: String,  // unread (未读) / reading (在读) / read (已读)
    
    @ColumnInfo(name = "project_id")
    val projectId: String?,
    
    @ColumnInfo(name = "project_name")
    val projectName: String?,
    
    @ColumnInfo(name = "is_starred", defaultValue = "0")
    val isStarred: Boolean = false,
    
    @ColumnInfo(name = "meta_json")
    val metaJson: String?,  // JSON 字符串，存储动态结构
    
    @ColumnInfo(name = "created_at")
    val createdAt: Long,  // Unix timestamp
    
    @ColumnInfo(name = "synced_at")
    val syncedAt: Long  // 上次同步时间
)

/**
 * Project Entity
 */
@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    
    @ColumnInfo(name = "name")
    val name: String,
    
    @ColumnInfo(name = "description")
    val description: String?,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Long
)


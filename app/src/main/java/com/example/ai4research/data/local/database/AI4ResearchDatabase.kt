package com.example.ai4research.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.ai4research.data.local.converter.RoomConverters
import com.example.ai4research.data.local.dao.ItemDao
import com.example.ai4research.data.local.dao.ProjectDao
import com.example.ai4research.data.local.dao.UserDao
import com.example.ai4research.data.local.entity.ItemEntity
import com.example.ai4research.data.local.entity.ProjectEntity
import com.example.ai4research.data.local.entity.UserEntity

/**
 * Room Database 定义
 * Version: 2 (添加用户认证)
 */
@Database(
    entities = [
        ItemEntity::class,
        ProjectEntity::class,
        UserEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(RoomConverters::class)
abstract class AI4ResearchDatabase : RoomDatabase() {
    abstract fun itemDao(): ItemDao
    abstract fun projectDao(): ProjectDao
    abstract fun userDao(): UserDao
    
    companion object {
        const val DATABASE_NAME = "ai4research_db"
    }
}


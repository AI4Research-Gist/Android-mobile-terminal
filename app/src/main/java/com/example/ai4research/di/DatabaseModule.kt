package com.example.ai4research.di

import android.content.Context
import androidx.room.Room
import com.example.ai4research.data.local.dao.ItemDao
import com.example.ai4research.data.local.dao.ProjectDao
import com.example.ai4research.data.local.dao.UserDao
import com.example.ai4research.data.local.database.AI4ResearchDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 数据库依赖注入模块
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    /**
     * 提供 Room Database
     */
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): AI4ResearchDatabase {
        return Room.databaseBuilder(
            context,
            AI4ResearchDatabase::class.java,
            AI4ResearchDatabase.DATABASE_NAME
        )
            .fallbackToDestructiveMigration()  // 开发阶段可破坏性迁移
            .build()
    }
    
    /**
     * 提供 ItemDao
     */
    @Provides
    @Singleton
    fun provideItemDao(database: AI4ResearchDatabase): ItemDao {
        return database.itemDao()
    }
    
    /**
     * 提供 ProjectDao
     */
    @Provides
    @Singleton
    fun provideProjectDao(database: AI4ResearchDatabase): ProjectDao {
        return database.projectDao()
    }
    
    /**
     * 提供 UserDao
     */
    @Provides
    @Singleton
    fun provideUserDao(database: AI4ResearchDatabase): UserDao {
        return database.userDao()
    }
}


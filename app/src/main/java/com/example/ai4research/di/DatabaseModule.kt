package com.example.ai4research.di

import android.content.Context
import androidx.room.Room
import com.example.ai4research.data.local.dao.ItemDao
import com.example.ai4research.data.local.dao.ItemRelationDao
import com.example.ai4research.data.local.dao.ProjectContextDocumentDao
import com.example.ai4research.data.local.dao.ProjectDao
import com.example.ai4research.data.local.dao.UserDao
import com.example.ai4research.data.local.database.AI4ResearchDatabase
import com.example.ai4research.data.local.database.DatabaseMigrations
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

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
            .addMigrations(*DatabaseMigrations.ALL)
            .build()
    }

    @Provides
    @Singleton
    fun provideItemDao(database: AI4ResearchDatabase): ItemDao = database.itemDao()

    @Provides
    @Singleton
    fun provideItemRelationDao(database: AI4ResearchDatabase): ItemRelationDao = database.itemRelationDao()

    @Provides
    @Singleton
    fun provideProjectContextDocumentDao(database: AI4ResearchDatabase): ProjectContextDocumentDao =
        database.projectContextDocumentDao()

    @Provides
    @Singleton
    fun provideProjectDao(database: AI4ResearchDatabase): ProjectDao = database.projectDao()

    @Provides
    @Singleton
    fun provideUserDao(database: AI4ResearchDatabase): UserDao = database.userDao()
}

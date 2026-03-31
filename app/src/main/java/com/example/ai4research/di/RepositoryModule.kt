package com.example.ai4research.di

import com.example.ai4research.data.repository.ItemRepositoryImpl
import com.example.ai4research.data.repository.KnowledgeConnectionRepositoryImpl
import com.example.ai4research.data.repository.ProjectRepositoryImpl
import com.example.ai4research.domain.repository.ItemRepository
import com.example.ai4research.domain.repository.KnowledgeConnectionRepository
import com.example.ai4research.domain.repository.ProjectRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Repository 依赖注入绑定
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindItemRepository(impl: ItemRepositoryImpl): ItemRepository

    @Binds
    @Singleton
    abstract fun bindProjectRepository(impl: ProjectRepositoryImpl): ProjectRepository

    @Binds
    @Singleton
    abstract fun bindKnowledgeConnectionRepository(impl: KnowledgeConnectionRepositoryImpl): KnowledgeConnectionRepository
}







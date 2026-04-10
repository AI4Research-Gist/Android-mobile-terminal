package com.example.ai4research.di

import com.example.ai4research.ai.local.LocalLlmEngine
import com.example.ai4research.ai.local.NoOpLocalLlmEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AiModule {
    @Binds
    @Singleton
    abstract fun bindLocalLlmEngine(impl: NoOpLocalLlmEngine): LocalLlmEngine
}

package com.example.ai4research.di

import com.example.ai4research.capture.pipeline.CapturePipeline
import com.example.ai4research.capture.pipeline.DefaultCapturePipeline
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CaptureModule {

    @Binds
    @Singleton
    abstract fun bindCapturePipeline(impl: DefaultCapturePipeline): CapturePipeline
}

package com.example.ai4research.ai.local

import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoOpLocalLlmEngine @Inject constructor() : LocalLlmEngine {
    override suspend fun isRuntimeAvailable(): Boolean = false

    override suspend fun isLoaded(): Boolean = false

    override suspend fun loadModel(modelDirectory: File): Result<Unit> {
        return Result.failure(
            IllegalStateException("On-device runtime is not wired yet. Connect the Android local runtime before loading models.")
        )
    }

    override suspend fun generateText(request: LocalGenerationRequest): Result<String> {
        return Result.failure(
            IllegalStateException("On-device runtime is not wired yet. Connect the Android local runtime before generating text.")
        )
    }

    override suspend fun unload() = Unit
}

package com.example.ai4research.ai.local

import java.io.File

interface LocalLlmEngine {
    suspend fun isRuntimeAvailable(): Boolean
    suspend fun isLoaded(): Boolean
    suspend fun loadModel(modelDirectory: File): Result<Unit>
    suspend fun generateText(request: LocalGenerationRequest): Result<String>
    suspend fun unload()
}

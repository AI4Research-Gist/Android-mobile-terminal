package com.example.ai4research.ai.local

import com.example.ai4research.ai.AiTaskType
import java.io.FileNotFoundException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalAiBackend @Inject constructor(
    private val settingsStore: OnDeviceAiSettingsStore,
    private val directoryProvider: OnDeviceModelDirectoryProvider,
    private val modelManager: OnDeviceModelManager,
    private val engine: LocalLlmEngine
) {
    suspend fun isEnabled(): Boolean = settingsStore.snapshot().enabled

    suspend fun canHandle(taskType: AiTaskType): Boolean {
        if (taskType !in SUPPORTED_TASKS) return false
        val settings = settingsStore.snapshot()
        return settings.enabled && settings.installState == OnDeviceModelInstallState.READY
    }

    suspend fun generateText(taskType: AiTaskType, request: LocalGenerationRequest): Result<String> {
        if (taskType !in SUPPORTED_TASKS) {
            return Result.failure(IllegalArgumentException("Task $taskType is not enabled for on-device execution"))
        }

        val settings = settingsStore.snapshot()
        if (!settings.enabled) {
            return Result.failure(IllegalStateException("On-device AI is disabled"))
        }

        val modelId = settings.installedModelId
            ?: return Result.failure(IllegalStateException("No on-device model is registered"))
        val modelDir = directoryProvider.modelDir(modelId)
        if (!modelDir.exists()) {
            return Result.failure(FileNotFoundException("On-device model directory is missing: ${modelDir.absolutePath}"))
        }

        val loadResult = modelManager.ensureLoaded(
            modelId = modelId,
            modelVersion = settings.installedModelVersion,
            modelDirectory = modelDir
        )
        if (loadResult.isFailure) return loadResult.map { "" }

        return engine.generateText(request).also {
            modelManager.markInferenceActivity()
        }
    }

    companion object {
        val SUPPORTED_TASKS: Set<AiTaskType> = setOf(
            AiTaskType.TRANSCRIPTION_ENHANCE,
            AiTaskType.LINK_PARSE,
            AiTaskType.SHORT_SUMMARY,
            AiTaskType.ITEM_QA_SHORT,
            AiTaskType.OCR_POST_PROCESS
        )
    }
}

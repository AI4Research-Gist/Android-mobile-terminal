package com.example.ai4research.ai.local

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OnDeviceModelBootstrapper @Inject constructor(
    private val directoryProvider: OnDeviceModelDirectoryProvider,
    private val modelManager: OnDeviceModelManager
) {
    suspend fun initialize() {
        val rootDir = directoryProvider.rootDir()
        val bundledModel = BundledOnDeviceModels.defaults.firstOrNull { spec ->
            spec.resolveDirectory(rootDir).exists()
        }

        if (bundledModel != null) {
            val modelDir = bundledModel.resolveDirectory(rootDir)
            modelManager.markBundledModelReady(
                modelId = bundledModel.modelId,
                modelVersion = bundledModel.version,
                modelDirectory = modelDir
            )
            Log.d(TAG, "Detected bundled on-device model: ${bundledModel.displayName} at ${modelDir.absolutePath}")
        } else {
            modelManager.syncFromSettings()
            Log.d(TAG, "No bundled on-device model detected under ${rootDir.absolutePath}")
        }
    }

    companion object {
        private const val TAG = "OnDeviceBootstrapper"
    }
}

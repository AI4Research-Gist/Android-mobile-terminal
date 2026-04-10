package com.example.ai4research.ai.local

import com.example.ai4research.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OnDeviceModelManager @Inject constructor(
    private val settingsStore: OnDeviceAiSettingsStore,
    private val engine: LocalLlmEngine,
    @ApplicationScope private val applicationScope: CoroutineScope
) {
    private val _status = MutableStateFlow(OnDeviceModelStatus())
    val status: StateFlow<OnDeviceModelStatus> = _status.asStateFlow()

    private val modelMutex = Mutex()
    private var releaseJob: Job? = null
    private var lastUseTimestampMs: Long = 0L

    suspend fun syncFromSettings() {
        val settings = settingsStore.snapshot()
        val isLoaded = engine.isLoaded()
        _status.value = OnDeviceModelStatus(
            modelId = settings.installedModelId,
            modelVersion = settings.installedModelVersion,
            installState = settings.installState,
            loadState = if (isLoaded) OnDeviceModelLoadState.LOADED else OnDeviceModelLoadState.UNLOADED,
            modelDirectory = _status.value.modelDirectory,
            lastError = settings.lastInstallError
        )
    }

    suspend fun markBundledModelReady(modelId: String, modelVersion: String?, modelDirectory: File) {
        settingsStore.markModelReady(modelId = modelId, modelVersion = modelVersion)
        _status.value = OnDeviceModelStatus(
            modelId = modelId,
            modelVersion = modelVersion,
            installState = OnDeviceModelInstallState.READY,
            loadState = if (engine.isLoaded()) OnDeviceModelLoadState.LOADED else OnDeviceModelLoadState.UNLOADED,
            modelDirectory = modelDirectory.absolutePath
        )
    }

    suspend fun markInstallFailed(errorMessage: String) {
        settingsStore.markModelFailed(errorMessage)
        _status.update {
            it.copy(
                installState = OnDeviceModelInstallState.FAILED,
                loadState = OnDeviceModelLoadState.FAILED,
                lastError = errorMessage
            )
        }
    }

    suspend fun ensureLoaded(modelId: String, modelVersion: String?, modelDirectory: File): Result<Unit> {
        return modelMutex.withLock {
            releaseJob?.cancel()
            val currentStatus = _status.value
            if (
                currentStatus.loadState == OnDeviceModelLoadState.LOADED &&
                currentStatus.modelDirectory == modelDirectory.absolutePath &&
                engine.isLoaded()
            ) {
                touchUsageLocked()
                return@withLock Result.success(Unit)
            }

            _status.update {
                it.copy(
                    modelId = modelId,
                    modelVersion = modelVersion,
                    installState = OnDeviceModelInstallState.READY,
                    loadState = OnDeviceModelLoadState.LOADING,
                    modelDirectory = modelDirectory.absolutePath,
                    lastError = null
                )
            }

            engine.loadModel(modelDirectory).onSuccess {
                _status.update { current -> current.copy(loadState = OnDeviceModelLoadState.LOADED) }
                touchUsageLocked()
            }.onFailure { error ->
                _status.update {
                    it.copy(
                        loadState = OnDeviceModelLoadState.FAILED,
                        lastError = error.message
                    )
                }
            }
        }
    }

    fun markInferenceActivity() {
        applicationScope.launch {
            modelMutex.withLock {
                touchUsageLocked()
            }
        }
    }

    suspend fun release() {
        modelMutex.withLock {
            releaseJob?.cancel()
            releaseJob = null
            engine.unload()
            _status.update {
                it.copy(loadState = OnDeviceModelLoadState.UNLOADED)
            }
        }
    }

    fun isInstalled(): Boolean = status.value.installState == OnDeviceModelInstallState.READY

    private fun touchUsageLocked() {
        lastUseTimestampMs = System.currentTimeMillis()
        scheduleReleaseLocked()
    }

    private fun scheduleReleaseLocked() {
        releaseJob?.cancel()
        releaseJob = applicationScope.launch {
            val marker = lastUseTimestampMs
            delay(IDLE_UNLOAD_TIMEOUT_MS)
            modelMutex.withLock {
                val stillIdle = marker == lastUseTimestampMs &&
                    _status.value.loadState == OnDeviceModelLoadState.LOADED
                if (stillIdle) {
                    engine.unload()
                    _status.update { it.copy(loadState = OnDeviceModelLoadState.UNLOADED) }
                }
            }
        }
    }

    companion object {
        private const val IDLE_UNLOAD_TIMEOUT_MS = 3 * 60 * 1000L
    }
}

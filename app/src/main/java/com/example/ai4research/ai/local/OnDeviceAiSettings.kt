package com.example.ai4research.ai.local

enum class OnDeviceModelInstallState {
    NOT_INSTALLED,
    INSTALLING,
    READY,
    FAILED
}

enum class OnDeviceModelLoadState {
    UNLOADED,
    LOADING,
    LOADED,
    FAILED
}

data class OnDeviceAiSettings(
    val enabled: Boolean = false,
    val preferLocalFirst: Boolean = true,
    val installedModelId: String? = null,
    val installedModelVersion: String? = null,
    val installState: OnDeviceModelInstallState = OnDeviceModelInstallState.NOT_INSTALLED,
    val lastInstallError: String? = null
)

data class OnDeviceModelStatus(
    val modelId: String? = null,
    val modelVersion: String? = null,
    val installState: OnDeviceModelInstallState = OnDeviceModelInstallState.NOT_INSTALLED,
    val loadState: OnDeviceModelLoadState = OnDeviceModelLoadState.UNLOADED,
    val modelDirectory: String? = null,
    val lastError: String? = null
)

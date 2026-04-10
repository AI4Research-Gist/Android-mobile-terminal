package com.example.ai4research.ai.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OnDeviceAiSettingsStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
        name = "on_device_ai_settings"
    )

    companion object {
        private val ENABLED_KEY = booleanPreferencesKey("enabled")
        private val PREFER_LOCAL_FIRST_KEY = booleanPreferencesKey("prefer_local_first")
        private val MODEL_ID_KEY = stringPreferencesKey("installed_model_id")
        private val MODEL_VERSION_KEY = stringPreferencesKey("installed_model_version")
        private val INSTALL_STATE_KEY = stringPreferencesKey("install_state")
        private val LAST_INSTALL_ERROR_KEY = stringPreferencesKey("last_install_error")
    }

    val settingsFlow: Flow<OnDeviceAiSettings> = context.dataStore.data.map { preferences ->
        OnDeviceAiSettings(
            enabled = preferences[ENABLED_KEY] ?: false,
            preferLocalFirst = preferences[PREFER_LOCAL_FIRST_KEY] ?: true,
            installedModelId = preferences[MODEL_ID_KEY],
            installedModelVersion = preferences[MODEL_VERSION_KEY],
            installState = preferences[INSTALL_STATE_KEY]
                ?.let { runCatching { OnDeviceModelInstallState.valueOf(it) }.getOrNull() }
                ?: OnDeviceModelInstallState.NOT_INSTALLED,
            lastInstallError = preferences[LAST_INSTALL_ERROR_KEY]
        )
    }

    suspend fun snapshot(): OnDeviceAiSettings = settingsFlow.first()

    suspend fun setEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[ENABLED_KEY] = enabled
        }
    }

    suspend fun setPreferLocalFirst(preferLocalFirst: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PREFER_LOCAL_FIRST_KEY] = preferLocalFirst
        }
    }

    suspend fun markModelInstalling(modelId: String, modelVersion: String?) {
        context.dataStore.edit { preferences ->
            preferences[MODEL_ID_KEY] = modelId
            modelVersion?.let { preferences[MODEL_VERSION_KEY] = it }
            preferences[INSTALL_STATE_KEY] = OnDeviceModelInstallState.INSTALLING.name
            preferences.remove(LAST_INSTALL_ERROR_KEY)
        }
    }

    suspend fun markModelReady(modelId: String, modelVersion: String?) {
        context.dataStore.edit { preferences ->
            preferences[MODEL_ID_KEY] = modelId
            modelVersion?.let { preferences[MODEL_VERSION_KEY] = it }
            preferences[INSTALL_STATE_KEY] = OnDeviceModelInstallState.READY.name
            preferences.remove(LAST_INSTALL_ERROR_KEY)
        }
    }

    suspend fun markModelFailed(errorMessage: String) {
        context.dataStore.edit { preferences ->
            preferences[INSTALL_STATE_KEY] = OnDeviceModelInstallState.FAILED.name
            preferences[LAST_INSTALL_ERROR_KEY] = errorMessage
        }
    }

    suspend fun clearInstalledModel() {
        context.dataStore.edit { preferences ->
            preferences.remove(MODEL_ID_KEY)
            preferences.remove(MODEL_VERSION_KEY)
            preferences[INSTALL_STATE_KEY] = OnDeviceModelInstallState.NOT_INSTALLED.name
            preferences.remove(LAST_INSTALL_ERROR_KEY)
        }
    }
}

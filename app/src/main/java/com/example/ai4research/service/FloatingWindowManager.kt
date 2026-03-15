package com.example.ai4research.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.floatingWindowDataStore: DataStore<Preferences> by preferencesDataStore(name = "floating_window_settings")

@Singleton
class FloatingWindowManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val KEY_FLOATING_ENABLED = booleanPreferencesKey("floating_window_enabled")
    }

    fun hasOverlayPermission(): Boolean {
        return Settings.canDrawOverlays(context)
    }

    val isFloatingWindowEnabled: Flow<Boolean> = context.floatingWindowDataStore.data
        .map { preferences ->
            preferences[KEY_FLOATING_ENABLED] ?: false
        }

    suspend fun setFloatingWindowEnabled(enabled: Boolean) {
        context.floatingWindowDataStore.edit { preferences ->
            preferences[KEY_FLOATING_ENABLED] = enabled
        }
        if (!enabled) {
            hideFloatingWindow()
            stopFloatingWindowService()
        }
    }

    fun showFloatingWindow() {
        if (hasOverlayPermission()) {
            val intent = Intent(context, FloatingWindowService::class.java).apply {
                action = FloatingWindowService.ACTION_SHOW
            }
            context.startService(intent)
        }
    }

    fun hideFloatingWindow() {
        val intent = Intent(context, FloatingWindowService::class.java).apply {
            action = FloatingWindowService.ACTION_HIDE
        }
        context.startService(intent)
    }

    fun openQuickLinkCapture() {
        if (hasOverlayPermission()) {
            val intent = Intent(context, FloatingWindowService::class.java).apply {
                action = FloatingWindowService.ACTION_SHOW_LINK_INPUT
            }
            context.startService(intent)
            return
        }

        val permissionIntent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(permissionIntent)
    }

    fun startQuickScanCapture() {
        val intent = Intent(context, FloatingWindowService::class.java).apply {
            action = FloatingWindowService.ACTION_SCREENSHOT
        }
        context.startService(intent)
    }

    fun stopFloatingWindowService() {
        val intent = Intent(context, FloatingWindowService::class.java)
        context.stopService(intent)
    }
}

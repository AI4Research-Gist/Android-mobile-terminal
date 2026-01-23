package com.example.ai4research.service

import android.content.Context
import android.content.Intent
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

// DataStore 扩展
private val Context.floatingWindowDataStore: DataStore<Preferences> by preferencesDataStore(name = "floating_window_settings")

/**
 * 悬浮窗管理器
 * 负责管理悬浮窗的显示/隐藏、权限检查、用户设置等
 */
@Singleton
class FloatingWindowManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val KEY_FLOATING_ENABLED = booleanPreferencesKey("floating_window_enabled")
    }

    /**
     * 检查是否有悬浮窗权限
     */
    fun hasOverlayPermission(): Boolean {
        return Settings.canDrawOverlays(context)
    }

    /**
     * 获取用户悬浮窗开关设置
     */
    val isFloatingWindowEnabled: Flow<Boolean> = context.floatingWindowDataStore.data
        .map { preferences ->
            preferences[KEY_FLOATING_ENABLED] ?: false
        }

    /**
     * 设置用户悬浮窗开关
     */
    suspend fun setFloatingWindowEnabled(enabled: Boolean) {
        context.floatingWindowDataStore.edit { preferences ->
            preferences[KEY_FLOATING_ENABLED] = enabled
        }
        if (!enabled) {
            hideFloatingWindow()
            stopFloatingWindowService()
        }
    }

    /**
     * 显示悬浮窗
     */
    fun showFloatingWindow() {
        if (hasOverlayPermission()) {
            val intent = Intent(context, FloatingWindowService::class.java).apply {
                action = FloatingWindowService.ACTION_SHOW
            }
            context.startService(intent)
        }
    }

    /**
     * 隐藏悬浮窗
     */
    fun hideFloatingWindow() {
        val intent = Intent(context, FloatingWindowService::class.java).apply {
            action = FloatingWindowService.ACTION_HIDE
        }
        context.startService(intent)
    }

    /**
     * 停止悬浮窗服务
     */
    fun stopFloatingWindowService() {
        val intent = Intent(context, FloatingWindowService::class.java)
        context.stopService(intent)
    }
}

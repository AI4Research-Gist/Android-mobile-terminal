package com.example.ai4research

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.provider.Settings
import com.example.ai4research.service.FloatingWindowManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Application 类
 * @HiltAndroidApp 触发 Hilt 的代码生成
 */
@HiltAndroidApp
class AI4ResearchApp : Application() {
    
    @Inject
    lateinit var floatingWindowManager: FloatingWindowManager
    
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    // 活跃的Activity计数
    private var activityCount = 0
    private var isInForeground = false
    
    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(AppLifecycleCallbacks())
    }
    
    /**
     * Activity生命周期回调
     * 用于检测应用进入前台/后台状态
     */
    inner class AppLifecycleCallbacks : ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
        
        override fun onActivityStarted(activity: Activity) {
            activityCount++
            if (!isInForeground) {
                isInForeground = true
                onAppForeground()
            }
        }
        
        override fun onActivityResumed(activity: Activity) {}
        
        override fun onActivityPaused(activity: Activity) {}
        
        override fun onActivityStopped(activity: Activity) {
            activityCount--
            if (activityCount == 0) {
                isInForeground = false
                onAppBackground()
            }
        }
        
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        
        override fun onActivityDestroyed(activity: Activity) {}
    }
    
    /**
     * 应用进入前台时隐藏悬浮窗
     */
    private fun onAppForeground() {
        floatingWindowManager.hideFloatingWindow()
    }
    
    /**
     * 应用进入后台时显示悬浮窗（如果用户已开启）
     */
    private fun onAppBackground() {
        applicationScope.launch {
            val isEnabled = floatingWindowManager.isFloatingWindowEnabled.first()
            val hasPermission = Settings.canDrawOverlays(this@AI4ResearchApp)
            
            if (isEnabled && hasPermission) {
                floatingWindowManager.showFloatingWindow()
            }
        }
    }
}

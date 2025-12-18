package com.example.ai4research

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application 类
 * @HiltAndroidApp 触发 Hilt 的代码生成
 */
@HiltAndroidApp
class AI4ResearchApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // 应用初始化逻辑
    }
}


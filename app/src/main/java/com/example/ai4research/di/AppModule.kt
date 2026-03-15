package com.example.ai4research.di

import android.content.Context
import com.example.ai4research.core.theme.ThemeManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 依赖注入模块：AppModule
 * 使用 Hilt 提供应用级别的依赖项，如 ThemeManager。
 * 所有依赖项都是单例（@Singleton），在整个应用生命周期内共享。
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * 提供 ThemeManager 单例实例，用于管理应用主题（深色/浅色/系统）。
     * 需要 ApplicationContext 来访问 SharedPreferences 存储主题设置。
     */
    @Provides
    @Singleton
    fun provideThemeManager(
        @ApplicationContext context: Context
    ): ThemeManager {
        return ThemeManager(context)
    }
}

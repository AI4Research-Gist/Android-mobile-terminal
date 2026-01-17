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
 * ?????????
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * ?? ThemeManager
     */
    @Provides
    @Singleton
    fun provideThemeManager(
        @ApplicationContext context: Context
    ): ThemeManager {
        return ThemeManager(context)
    }
}

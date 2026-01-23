package com.example.ai4research.core.theme

import android.os.Build
import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * iOS 风格主题 - Material 3 浅色配色方案
 * 注意：禁用 Material 的默认阴影、水波纹、胶囊指示器
 */
private fun getLightColorScheme(iosColors: IOSColors) = lightColorScheme(
    // Primary 色系 - iOS 蓝
    primary = iosColors.systemBlue,
    onPrimary = Color.White,
    primaryContainer = iosColors.systemBlue.copy(alpha = 0.1f),
    onPrimaryContainer = iosColors.systemBlue,
    
    // Secondary 色系 - 系统灰
    secondary = iosColors.secondaryLabel,
    onSecondary = Color.White,
    secondaryContainer = iosColors.secondaryLabel.copy(alpha = 0.1f),
    onSecondaryContainer = iosColors.secondaryLabel,
    
    // 背景色系
    background = iosColors.systemBackground,
    onBackground = iosColors.label,
    
    // Surface 色系 - 纯白卡片
    surface = iosColors.secondarySystemBackground,
    onSurface = iosColors.label,
    surfaceVariant = iosColors.tertiarySystemBackground,
    onSurfaceVariant = iosColors.secondaryLabel,
    
    // 边框（替代阴影）
    outline = iosColors.separator,
    outlineVariant = iosColors.opaqueSeparator,
    
    // Error
    error = iosColors.systemRed,
    onError = Color.White,
    errorContainer = iosColors.systemRed.copy(alpha = 0.1f),
    onErrorContainer = iosColors.systemRed
)

/**
 * iOS 风格主题 - Material 3 深色配色方案
 */
private fun getDarkColorScheme(iosColors: IOSColors) = darkColorScheme(
    // Primary 色系 - iOS 蓝（深色模式更亮）
    primary = iosColors.systemBlue,
    onPrimary = Color.Black,
    primaryContainer = iosColors.systemBlue.copy(alpha = 0.2f),
    onPrimaryContainer = iosColors.systemBlue,
    
    // Secondary 色系 - 系统灰
    secondary = iosColors.secondaryLabel,
    onSecondary = Color.Black,
    secondaryContainer = iosColors.secondaryLabel.copy(alpha = 0.2f),
    onSecondaryContainer = iosColors.secondaryLabel,
    
    // 背景色系
    background = iosColors.systemBackground,
    onBackground = iosColors.label,
    
    // Surface 色系 - 深色卡片
    surface = iosColors.secondarySystemBackground,
    onSurface = iosColors.label,
    surfaceVariant = iosColors.tertiarySystemBackground,
    onSurfaceVariant = iosColors.secondaryLabel,
    
    // 边框
    outline = iosColors.separator,
    outlineVariant = iosColors.opaqueSeparator,
    
    // Error
    error = iosColors.systemRed,
    onError = Color.Black,
    errorContainer = iosColors.systemRed.copy(alpha = 0.2f),
    onErrorContainer = iosColors.systemRed
)

/**
 * CompositionLocal 用于在整个应用中访问 iOS 颜色
 */
val LocalIOSColors = staticCompositionLocalOf { lightIOSColors() }

/**
 * AI4Research 应用主题
 * @param darkTheme 是否使用深色主题
 * @param dynamicColor 是否使用动态颜色（Android 12+）
 * @param content Composable 内容
 */
@Composable
fun AI4ResearchTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    // 选择颜色方案
    val iosColors = if (darkTheme) darkIOSColors() else lightIOSColors()
    
    // Material 3 配色方案（支持 Android 12+ 动态颜色）
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> getDarkColorScheme(iosColors)
        else -> getLightColorScheme(iosColors)
    }
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // window.statusBarColor and navigationBarColor setters are deprecated but still functional
            // Suppress warning or use WindowCompat if possible, but color setting is still done via these properties or standard window APIs
            @Suppress("DEPRECATION")
            window.statusBarColor = colorScheme.background.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalIOSColors provides iosColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = IOSTypography,
            shapes = IOSShapes,
            content = content
        )
    }
}

/**
 * 便捷访问 iOS 颜色
 */
object IOSTheme {
    val colors: IOSColors
        @Composable
        get() = LocalIOSColors.current
}


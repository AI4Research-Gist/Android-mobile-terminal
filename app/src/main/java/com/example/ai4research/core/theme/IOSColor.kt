package com.example.ai4research.core.theme

import androidx.compose.ui.graphics.Color

/**
 * iOS 风格颜色系统 - 浅色模式
 * 严格遵循 Apple Human Interface Guidelines
 */
object IOSLightColors {
    // 背景色系
    val SystemBackground = Color(0xFFF2F2F7)  // iOS 默认浅灰背景
    val SystemGroupedBackground = Color(0xFFF2F2F7)
    val SecondarySystemBackground = Color(0xFFFFFFFF)  // 纯白卡片
    val TertiarySystemBackground = Color(0xFFFFFFFF)
    
    // 主色调
    val SystemBlue = Color(0xFF007AFF)  // iOS 标志性蓝色
    val SystemGreen = Color(0xFF34C759)
    val SystemRed = Color(0xFFFF3B30)
    val SystemOrange = Color(0xFFFF9500)
    val SystemYellow = Color(0xFFFFCC00)
    val SystemPurple = Color(0xFFAF52DE)
    
    // 文本颜色
    val Label = Color(0xFF000000)  // 主要文本（几乎纯黑）
    val SecondaryLabel = Color(0xFF8E8E93)  // 次要文本（系统灰）
    val TertiaryLabel = Color(0xFFC7C7CC)  // 三级文本
    val QuaternaryLabel = Color(0xFFD1D1D6)  // 四级文本
    
    // 分隔线和边框
    val Separator = Color(0xFFE5E5EA)  // 极浅边框（替代阴影）
    val OpaqueSeparator = Color(0xFFC6C6C8)
    
    // 特殊效果
    val PlaceholderText = Color(0xFF3C3C434D)  // 占位符文本
    
    // 卡片相关
    val CardBackground = Color(0xFFFFFFFF)
    val CardBorder = Color(0xFFE5E5EA)
}

/**
 * iOS 风格颜色系统 - 深色模式
 * 严格遵循 Apple Human Interface Guidelines Dark Mode
 */
object IOSDarkColors {
    // 背景色系
    val SystemBackground = Color(0xFF000000)  // 纯黑背景
    val SystemGroupedBackground = Color(0xFF000000)
    val SecondarySystemBackground = Color(0xFF1C1C1E)  // 深灰卡片
    val TertiarySystemBackground = Color(0xFF2C2C2E)
    
    // 主色调（深色模式下更亮）
    val SystemBlue = Color(0xFF0A84FF)  // 更亮的蓝色
    val SystemGreen = Color(0xFF30D158)
    val SystemRed = Color(0xFFFF453A)
    val SystemOrange = Color(0xFFFF9F0A)
    val SystemYellow = Color(0xFFFFD60A)
    val SystemPurple = Color(0xFFBF5AF2)
    
    // 文本颜色
    val Label = Color(0xFFFFFFFF)  // 主要文本（纯白）
    val SecondaryLabel = Color(0xFF8E8E93)  // 次要文本（灰色保持一致）
    val TertiaryLabel = Color(0xFF48484A)  // 三级文本（更暗）
    val QuaternaryLabel = Color(0xFF3A3A3C)  // 四级文本
    
    // 分隔线和边框
    val Separator = Color(0xFF38383A)  // 深色边框
    val OpaqueSeparator = Color(0xFF48484A)
    
    // 特殊效果
    val PlaceholderText = Color(0xFFEBEBF54D)  // 占位符文本
    
    // 卡片相关
    val CardBackground = Color(0xFF1C1C1E)
    val CardBorder = Color(0xFF38383A)
}

/**
 * iOS 颜色数据类
 * 用于在主题切换时提供统一的颜色访问接口
 */
data class IOSColors(
    // 背景色系
    val systemBackground: Color,
    val systemGroupedBackground: Color,
    val secondarySystemBackground: Color,
    val tertiarySystemBackground: Color,
    
    // 主色调
    val systemBlue: Color,
    val systemGreen: Color,
    val systemRed: Color,
    val systemOrange: Color,
    val systemYellow: Color,
    val systemPurple: Color,
    
    // 文本颜色
    val label: Color,
    val secondaryLabel: Color,
    val tertiaryLabel: Color,
    val quaternaryLabel: Color,
    
    // 分隔线和边框
    val separator: Color,
    val opaqueSeparator: Color,
    
    // 特殊效果
    val placeholderText: Color,
    
    // 卡片相关
    val cardBackground: Color,
    val cardBorder: Color
)

/**
 * 创建浅色主题颜色方案
 */
fun lightIOSColors() = IOSColors(
    systemBackground = IOSLightColors.SystemBackground,
    systemGroupedBackground = IOSLightColors.SystemGroupedBackground,
    secondarySystemBackground = IOSLightColors.SecondarySystemBackground,
    tertiarySystemBackground = IOSLightColors.TertiarySystemBackground,
    systemBlue = IOSLightColors.SystemBlue,
    systemGreen = IOSLightColors.SystemGreen,
    systemRed = IOSLightColors.SystemRed,
    systemOrange = IOSLightColors.SystemOrange,
    systemYellow = IOSLightColors.SystemYellow,
    systemPurple = IOSLightColors.SystemPurple,
    label = IOSLightColors.Label,
    secondaryLabel = IOSLightColors.SecondaryLabel,
    tertiaryLabel = IOSLightColors.TertiaryLabel,
    quaternaryLabel = IOSLightColors.QuaternaryLabel,
    separator = IOSLightColors.Separator,
    opaqueSeparator = IOSLightColors.OpaqueSeparator,
    placeholderText = IOSLightColors.PlaceholderText,
    cardBackground = IOSLightColors.CardBackground,
    cardBorder = IOSLightColors.CardBorder
)

/**
 * 创建深色主题颜色方案
 */
fun darkIOSColors() = IOSColors(
    systemBackground = IOSDarkColors.SystemBackground,
    systemGroupedBackground = IOSDarkColors.SystemGroupedBackground,
    secondarySystemBackground = IOSDarkColors.SecondarySystemBackground,
    tertiarySystemBackground = IOSDarkColors.TertiarySystemBackground,
    systemBlue = IOSDarkColors.SystemBlue,
    systemGreen = IOSDarkColors.SystemGreen,
    systemRed = IOSDarkColors.SystemRed,
    systemOrange = IOSDarkColors.SystemOrange,
    systemYellow = IOSDarkColors.SystemYellow,
    systemPurple = IOSDarkColors.SystemPurple,
    label = IOSDarkColors.Label,
    secondaryLabel = IOSDarkColors.SecondaryLabel,
    tertiaryLabel = IOSDarkColors.TertiaryLabel,
    quaternaryLabel = IOSDarkColors.QuaternaryLabel,
    separator = IOSDarkColors.Separator,
    opaqueSeparator = IOSDarkColors.OpaqueSeparator,
    placeholderText = IOSDarkColors.PlaceholderText,
    cardBackground = IOSDarkColors.CardBackground,
    cardBorder = IOSDarkColors.CardBorder
)

/**
 * iOS 颜色常量别名（默认使用浅色模式颜色）
 * 用于非 Composable 上下文或需要静态颜色引用的场景
 */
object IOSColor {
    // 背景色系
    val SystemBackground = IOSLightColors.SystemBackground
    val SystemGroupedBackground = IOSLightColors.SystemGroupedBackground
    val SecondarySystemBackground = IOSLightColors.SecondarySystemBackground
    val TertiarySystemBackground = IOSLightColors.TertiarySystemBackground
    
    // 主色调
    val SystemBlue = IOSLightColors.SystemBlue
    val SystemGreen = IOSLightColors.SystemGreen
    val SystemRed = IOSLightColors.SystemRed
    val SystemOrange = IOSLightColors.SystemOrange
    val SystemYellow = IOSLightColors.SystemYellow
    val SystemPurple = IOSLightColors.SystemPurple
    
    // 文本颜色
    val Label = IOSLightColors.Label
    val SecondaryLabel = IOSLightColors.SecondaryLabel
    val TertiaryLabel = IOSLightColors.TertiaryLabel
    val QuaternaryLabel = IOSLightColors.QuaternaryLabel
    
    // 分隔线和边框
    val Separator = IOSLightColors.Separator
    val OpaqueSeparator = IOSLightColors.OpaqueSeparator
    
    // 特殊效果
    val PlaceholderText = IOSLightColors.PlaceholderText
    
    // 卡片相关
    val CardBackground = IOSLightColors.CardBackground
    val CardBorder = IOSLightColors.CardBorder
}


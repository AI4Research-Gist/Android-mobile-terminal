package com.example.ai4research.core.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * iOS 风格形状系统
 * 统一使用大圆角（Squircle 效果）
 */
val IOSShapes = Shapes(
    // 极小圆角 - 用于按钮
    extraSmall = RoundedCornerShape(8.dp),
    
    // 小圆角 - 用于标签/胶囊
    small = RoundedCornerShape(12.dp),
    
    // 中圆角 - 用于小卡片
    medium = RoundedCornerShape(16.dp),
    
    // 大圆角 - 用于主卡片（核心）
    large = RoundedCornerShape(20.dp),
    
    // 超大圆角 - 用于底部弹窗
    extraLarge = RoundedCornerShape(28.dp)
)

/**
 * iOS 专用尺寸常量
 */
object IOSCornerRadius {
    val Small = 8.dp
    val Medium = 12.dp
    val Standard = 20.dp  // 最常用
    val Large = 28.dp
}


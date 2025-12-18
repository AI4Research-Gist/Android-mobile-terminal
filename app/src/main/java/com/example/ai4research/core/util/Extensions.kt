package com.example.ai4research.core.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Kotlin 扩展函数集合
 */

// ==================== Compose 修饰符扩展 ====================

/**
 * iOS 风格的细边框修饰符（替代阴影）
 * @param width 边框宽度，默认 1dp
 * @param color 边框颜色，默认 #E5E5EA
 */
fun Modifier.iosBorder(
    width: Dp = 1.dp,
    color: Color = Color(0xFFE5E5EA)
): Modifier = this.drawBehind {
    val strokeWidthPx = width.toPx()
    drawRect(
        color = color,
        topLeft = Offset(0f, 0f),
        size = size,
        alpha = 1f,
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidthPx)
    )
}

// ==================== 日期时间扩展 ====================

/**
 * Date 转为友好的时间字符串
 * 示例：2024-12-15 14:30
 */
fun Date.toFormattedString(): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return formatter.format(this)
}

/**
 * Date 转为简短日期
 * 示例：12月15日
 */
fun Date.toShortDateString(): String {
    val formatter = SimpleDateFormat("MM月dd日", Locale.CHINA)
    return formatter.format(this)
}

/**
 * Long 时间戳转 Date
 */
fun Long.toDate(): Date {
    return Date(this)
}

// ==================== 字符串扩展 ====================

/**
 * 判断字符串是否为有效 URL
 */
fun String.isValidUrl(): Boolean {
    return this.startsWith("http://") || this.startsWith("https://")
}

/**
 * 截取摘要（最多 N 个字符）
 */
fun String.truncate(maxLength: Int = 100, ellipsis: String = "..."): String {
    return if (this.length > maxLength) {
        this.substring(0, maxLength) + ellipsis
    } else {
        this
    }
}


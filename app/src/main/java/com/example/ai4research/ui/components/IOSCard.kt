package com.example.ai4research.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ai4research.core.theme.IOSCornerRadius
import com.example.ai4research.core.theme.IOSTheme

/**
 * iOS 风格卡片组件
 * 特点：纯白背景 + 极细边框 + 大圆角 + 无阴影
 */
@Composable
fun IOSCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = IOSCornerRadius.Standard,
    backgroundColor: Color? = null,
    borderColor: Color? = null,
    borderWidth: Dp = 1.dp,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val iosColors = IOSTheme.colors
    val bgColor = backgroundColor ?: iosColors.cardBackground
    val bColor = borderColor ?: iosColors.cardBorder
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(bgColor)
            .border(
                width = borderWidth,
                color = bColor,
                shape = RoundedCornerShape(cornerRadius)
            )
            .padding(contentPadding)
    ) {
        content()
    }
}

/**
 * 简化版卡片（无边框）
 */
@Composable
fun IOSCardNoBorder(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = IOSCornerRadius.Standard,
    backgroundColor: Color? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val iosColors = IOSTheme.colors
    val bgColor = backgroundColor ?: iosColors.cardBackground
    
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(bgColor)
            .padding(contentPadding)
    ) {
        content()
    }
}


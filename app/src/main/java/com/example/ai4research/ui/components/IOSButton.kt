package com.example.ai4research.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ai4research.core.theme.IOSCornerRadius
import com.example.ai4research.core.theme.IOSTheme

/**
 * iOS 风格按钮
 * 特点：点击时透明度变化（无水波纹）
 */
@Composable
fun IOSButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color? = null,
    textColor: Color = Color.White,
    enabled: Boolean = true
) {
    val iosColors = IOSTheme.colors
    val bgColor = backgroundColor ?: iosColors.systemBlue
    var isPressed by remember { mutableStateOf(false) }
    
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(IOSCornerRadius.Medium))
            .background(
                if (enabled) {
                    bgColor.copy(alpha = if (isPressed) 0.7f else 1f)
                } else {
                    bgColor.copy(alpha = 0.3f)
                }
            )
            .clickable(
                enabled = enabled,
                indication = null,  // 禁用水波纹
                interactionSource = remember { MutableInteractionSource() }
            ) {
                onClick()
            }
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = textColor
        )
    }
}

/**
 * iOS 风格次要按钮（边框样式）
 */
@Composable
fun IOSButtonSecondary(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    textColor: Color? = null
) {
    val iosColors = IOSTheme.colors
    val txtColor = textColor ?: iosColors.systemBlue
    var isPressed by remember { mutableStateOf(false) }
    
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(IOSCornerRadius.Medium))
            .background(
                if (isPressed) {
                    iosColors.systemBlue.copy(alpha = 0.1f)
                } else {
                    Color.Transparent
                }
            )
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                onClick()
            }
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Medium
            ),
            color = txtColor
        )
    }
}


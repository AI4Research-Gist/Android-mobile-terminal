package com.example.ai4research.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * High-Tech Glass Card Container
 * Updated: Pure white frosted glass, brighter, cleaner.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .shadow(
                elevation = 16.dp, // Increased elevation for better depth
                shape = RoundedCornerShape(24.dp),
                spotColor = Color.Black.copy(alpha = 0.15f), // Slightly stronger shadow for contrast
                ambientColor = Color.Black.copy(alpha = 0.15f)
            )
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.5f), // Much brighter top-left
                        Color.White.copy(alpha = 0.2f)  // Semi-transparent bottom-right
                    ),
                    start = androidx.compose.ui.geometry.Offset(0f, 0f),
                    end = androidx.compose.ui.geometry.Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                )
            )
            .border(
                width = 1.5.dp, // Slightly thicker border
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.8f), // Crisp white highlight
                        Color.White.copy(alpha = 0.3f),
                        Color.Transparent,
                        Color.White.copy(alpha = 0.5f)
                    )
                ),
                shape = RoundedCornerShape(24.dp)
            ),
        content = content
    )
}

/**
 * High-Tech Glass Input Field
 * Updated: Lighter, cleaner, more visible on dark bg.
 */
@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: () -> Unit = {},
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    var passwordVisible by remember { mutableStateOf(false) }

    // Glow effect on focus - Clean Cyan/Blue
    val borderWidth = if (isFocused) 1.5.dp else 0.8.dp
    val borderColor = if (isFocused) {
        Brush.horizontalGradient(listOf(Color(0xFF00E5FF), Color(0xFF2979FF))) // Cyan to Blue
    } else {
        Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.5f), Color.White.copy(alpha = 0.2f)))
    }
    
    // Animate background color on focus
    val backgroundColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (isFocused) Color.White.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.15f), // Brighter background
        label = "input_bg_anim"
    )

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp) // Slightly taller for better touch target
            .border(
                width = borderWidth,
                brush = borderColor,
                shape = RoundedCornerShape(16.dp)
            )
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor), 
        textStyle = MaterialTheme.typography.bodyLarge.copy( // Larger text
            color = Color.White,
            fontWeight = FontWeight.Medium
        ),
        cursorBrush = SolidColor(Color(0xFF00E5FF)), // Cyan cursor
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = imeAction
        ),
        keyboardActions = KeyboardActions(
            onAny = { onImeAction() }
        ),
        interactionSource = interactionSource,
        visualTransformation = if (isPassword && !passwordVisible) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (leadingIcon != null) {
                    Box(modifier = Modifier.padding(end = 12.dp)) {
                        leadingIcon()
                    }
                }
                
                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.7f) // Brighter placeholder
                        )
                    }
                    innerTextField()
                }
                
                if (isPassword) {
                    IconButton(
                        onClick = { passwordVisible = !passwordVisible },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.8f)
                        )
                    }
                } else if (trailingIcon != null) {
                    trailingIcon()
                }
            }
        }
    )
}

/**
 * High-Tech Neon Gradient Button
 * Updated: Vibrant, clean gradient, no "dirty" shadows.
 */
@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.96f else 1f, label = "button_scale")

    Box(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(
                brush = Brush.horizontalGradient(
                    colors = if (enabled) {
                        // Bright Cyan -> Electric Blue (Clean, bright colors)
                        listOf(Color(0xFF00C6FF), Color(0xFF0072FF)) 
                    } else {
                        listOf(Color.White.copy(alpha = 0.15f), Color.White.copy(alpha = 0.15f))
                    }
                )
            )
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null
            ) { onClick() }
            .padding(vertical = 16.dp), // Comfortable padding
        contentAlignment = Alignment.Center
    ) {
        // Inner Shine (Top Gloss)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp) // Top half only
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.2f),
                            Color.Transparent
                        )
                    )
                )
        )

        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            ),
            color = Color.White // Pure white text
        )
    }
}

/**
 * Glass Segmented Control
 * Updated: Brighter, cleaner active state.
 */
@Composable
fun GlassSegmentedControl(
    items: List<String>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.15f)) // Brighter background
            .padding(4.dp)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            items.forEachIndexed { index, item ->
                val isSelected = selectedIndex == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isSelected) Color.White.copy(alpha = 0.3f) else Color.Transparent // Brighter selection
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onItemSelected(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = item,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
fun GlassSocialButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit = {}
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable { onClick() }
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp) // Larger touch target
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.12f))
                .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (label == "GitHub") Color.White else color, // Ensure GitHub icon is visible
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = Color.White.copy(alpha = 0.7f) // Brighter text
        )
    }
}

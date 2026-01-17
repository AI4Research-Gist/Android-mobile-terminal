package com.example.ai4research.ui.components

import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.ai4research.core.theme.IOSTheme

@Composable
fun IOSSegmentedControl(
    items: List<String>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val iosColors = IOSTheme.colors
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(iosColors.tertiarySystemBackground)
            .padding(2.dp)
    ) {
        // Moving indicator
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
        ) {
            val width = maxWidth / items.size
            val offset by animateDpAsState(
                targetValue = width * selectedIndex,
                label = "segment_indicator"
            )
            
            Box(
                modifier = Modifier
                    .offset(x = offset)
                    .width(width)
                    .fillMaxHeight()
                    .shadow(
                        elevation = 1.dp,
                        shape = RoundedCornerShape(6.dp)
                    )
                    .background(iosColors.secondarySystemBackground, RoundedCornerShape(6.dp))
                    .zIndex(0f)
            )
        }

        // Items
        Row(
            modifier = Modifier.fillMaxSize()
        ) {
            items.forEachIndexed { index, item ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { onItemSelected(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = item,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (selectedIndex == index) FontWeight.SemiBold else FontWeight.Medium
                        ),
                        color = if (selectedIndex == index) iosColors.label else iosColors.secondaryLabel,
                        modifier = Modifier.zIndex(1f)
                    )
                }
            }
        }
    }
}

package com.example.ai4research.ui.home

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.ai4research.domain.model.ItemMetaData
import com.example.ai4research.domain.model.ItemStatus
import com.example.ai4research.domain.model.ItemType
import com.example.ai4research.domain.model.ResearchItem
import com.example.ai4research.ui.components.GistCard
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onLogout: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onItemClick: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val items by viewModel.items.collectAsState()
    
    // Simple custom TopBar state, simplified from LargeTopAppBar to match reference "Library" header better
    // The reference shows a big "Library" title with a "Settings" or profile icon
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "我的研究",
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 32.sp
                        ),
                        modifier = Modifier.padding(top = 16.dp)
                    )
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            Icons.Default.Settings, 
                            contentDescription = "设置",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { /* TODO: Add new item */ },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                shape = CircleShape, // Round FAB
                modifier = Modifier.size(64.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "新建", modifier = Modifier.size(32.dp))
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // "Filter" Pills (Simulated for now, based on reference images showing "SONGS", "ALBUMS")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilterPill("全部", true)
                FilterPill("论文", false)
                FilterPill("灵感", false)
                FilterPill("语音", false)
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = 16.dp,
                    bottom = 100.dp, // Space for FAB and BottomBar
                    start = 16.dp,
                    end = 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(items) { item ->
                    LibraryItemCard(
                        item = item,
                        onClick = { onItemClick(item.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun FilterPill(text: String, selected: Boolean) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primary else Color.White,
        contentColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(50),
        shadowElevation = if (selected) 4.dp else 0.dp,
        border = if (!selected) null else null // Simplified
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
        )
    }
}

@Composable
private fun LibraryItemCard(
    item: ResearchItem,
    onClick: () -> Unit
) {
    GistCard(
        onClick = onClick,
        modifier = Modifier.animateContentSize()
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.Top
        ) {
            // Icon Box - Squared with rounded corners like Album Art
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp)) // iOS squircle-ish
                    .background(getItemColor(item.type).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getItemIcon(item.type),
                    contentDescription = null,
                    tint = getItemColor(item.type),
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Content
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp // Close to iOS default body
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    if (item.status == ItemStatus.PROCESSING) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        // Date label
                         Text(
                            text = SimpleDateFormat("MM月dd日", Locale.CHINA).format(item.createdAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray.copy(alpha = 0.8f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = item.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    maxLines = 2, // Reduced to 2 lines for cleaner look
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Tags / Metadata
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Type Chip (Pill style)
                    AssistChip(
                        onClick = {},
                        label = { Text(getItemTypeName(item.type)) },
                        modifier = Modifier.height(26.dp),
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = Color.White,
                            labelColor = Color.Gray
                        ),
                        border = AssistChipDefaults.assistChipBorder(
                            borderColor = Color.LightGray.copy(alpha = 0.5f),
                            borderWidth = 0.5.dp,
                            enabled = true
                        )
                    )
                    
                    // Specific Meta Chip
                    val metaText = when(val meta = item.metaData) {
                        is ItemMetaData.PaperMeta -> meta.year.toString()
                        is ItemMetaData.VoiceMeta -> "${meta.duration / 60} 分钟"
                        is ItemMetaData.CompetitionMeta -> meta.organizer
                        else -> null
                    }
                    
                    if (metaText != null) {
                        AssistChip(
                            onClick = {},
                            label = { Text(metaText) },
                            modifier = Modifier.height(26.dp),
                             colors = AssistChipDefaults.assistChipColors(
                                containerColor = Color.White,
                                labelColor = Color.Gray
                            ),
                            border = AssistChipDefaults.assistChipBorder(
                                borderColor = Color.LightGray.copy(alpha = 0.5f),
                                borderWidth = 0.5.dp,
                                enabled = true
                            )
                        )
                    }
                }
            }
        }
    }
}

private fun getItemTypeName(type: ItemType): String {
    return when (type) {
        ItemType.PAPER -> "论文"
        ItemType.INSIGHT -> "灵感"
        ItemType.VOICE -> "语音"
        ItemType.COMPETITION -> "竞赛"
    }
}

private fun getItemColor(type: ItemType): Color {
    return when (type) {
        ItemType.PAPER -> Color(0xFF3D7AF0) // Blue
        ItemType.INSIGHT -> Color(0xFFAF52DE) // Purple
        ItemType.VOICE -> Color(0xFFFF9500) // Orange
        ItemType.COMPETITION -> Color(0xFFFF2D55) // Pink
    }
}

private fun getItemIcon(type: ItemType): ImageVector {
    return when (type) {
        ItemType.PAPER -> Icons.AutoMirrored.Filled.Article
        ItemType.INSIGHT -> Icons.Default.Lightbulb
        ItemType.VOICE -> Icons.Default.Mic
        ItemType.COMPETITION -> Icons.Default.EmojiEvents
    }
}

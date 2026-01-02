package com.example.ai4research.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.ai4research.domain.model.ItemStatus
import com.example.ai4research.domain.model.ItemType
import com.example.ai4research.domain.model.ResearchItem
import com.example.ai4research.ui.components.GistCard
import com.example.ai4research.ui.capture.CameraCaptureActivity
import kotlinx.coroutines.launch
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
    val query by viewModel.query.collectAsState()
    val selectedType by viewModel.selectedType.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    var showCreateLinkSheet by remember { mutableStateOf(false) }

    LaunchedEffect(errorMessage) {
        val msg = errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearError()
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = "我的研究",
                        style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold)
                    )
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.96f)
                ),
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            HomeFab(
                onAddLink = { showCreateLinkSheet = true },
                onAddPhoto = {
                    context.startActivity(android.content.Intent(context, CameraCaptureActivity::class.java))
                },
                onAddVoice = {
                    coroutineScope.launch { snackbarHostState.showSnackbar("语音页：请切换到底部「语音」Tab") }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    Brush.verticalGradient(
                        0f to MaterialTheme.colorScheme.background,
                        1f to MaterialTheme.colorScheme.background
                    )
                )
                .nestedScroll(scrollBehavior.nestedScrollConnection)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = 120.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    TechHeaderCard()
                }

                item {
                    IOSSearchBar(
                        value = query,
                        onValueChange = viewModel::setQuery,
                        placeholder = "搜索标题 / 摘要 / 关键词"
                    )
                }

                item {
                    TypeFilterRow(
                        selectedType = selectedType,
                        onSelect = viewModel::setTypeFilter
                    )
                }

                items(items, key = { it.id }) { item ->
                    LibraryItemCard(
                        item = item,
                        onClick = { onItemClick(item.id) }
                    )
                }

                if (items.isEmpty()) {
                    item {
                        EmptyStateCard(isRefreshing = isRefreshing)
                    }
                }
            }

        }
    }

    if (showCreateLinkSheet) {
        CreateLinkSheet(
            onDismiss = { showCreateLinkSheet = false },
            onConfirm = { url, title, note ->
                viewModel.createUrlItem(url = url, title = title, note = note)
                showCreateLinkSheet = false
            }
        )
    }
}

@Composable
private fun TechHeaderCard() {
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary

    GistCard(
        onClick = {},
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(92.dp)
                .clip(MaterialTheme.shapes.large)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            primary.copy(alpha = 0.18f),
                            tertiary.copy(alpha = 0.14f),
                            primary.copy(alpha = 0.08f)
                        )
                    )
                )
                .padding(16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "AI4Research · Library",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Text(
                    text = "碎片化采集 → 结构化卡片",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun IOSSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = {
            Text(
                text = placeholder,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
            )
        },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)) },
        singleLine = true,
        shape = MaterialTheme.shapes.large,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
        )
    )
}

@Composable
private fun TypeFilterRow(
    selectedType: ItemType?,
    onSelect: (ItemType?) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 2.dp)
    ) {
        item { FilterChip("全部", selectedType == null) { onSelect(null) } }
        item { FilterChip("论文", selectedType == ItemType.PAPER) { onSelect(ItemType.PAPER) } }
        item { FilterChip("灵感", selectedType == ItemType.INSIGHT) { onSelect(ItemType.INSIGHT) } }
        item { FilterChip("竞赛", selectedType == ItemType.COMPETITION) { onSelect(ItemType.COMPETITION) } }
        item { FilterChip("语音", selectedType == ItemType.VOICE) { onSelect(ItemType.VOICE) } }
    }
}

@Composable
private fun FilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface
    val border = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)

    Surface(
        onClick = onClick,
        color = bg,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = CircleShape,
        border = BorderStroke(0.5.dp, border),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .height(38.dp)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun EmptyStateCard(isRefreshing: Boolean) {
    GistCard(onClick = {}) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = if (isRefreshing) "正在同步..." else "还没有条目",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold)
            )
            Text(
                text = "你可以通过右下角按钮采集链接/拍照/录音，或开启悬浮球在任何 App 一键采集。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
            )
        }
    }
}

@Composable
private fun LibraryItemCard(
    item: ResearchItem,
    onClick: () -> Unit
) {
    val accent = getItemAccent(item.type)
    val accentBg = Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.5f)))

    GistCard(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(accentBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getItemIcon(item.type),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    if (item.status == ItemStatus.PROCESSING) {
                        Spacer(modifier = Modifier.width(10.dp))
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = item.summary.ifBlank { "AI 正在解析，稍后会生成摘要与结构化卡片…" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TechPill(text = getItemTypeName(item.type), color = accent)
                    Text(
                        text = SimpleDateFormat("MM/dd", Locale.CHINA).format(item.createdAt),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                    )
                }
            }
        }
    }
}

@Composable
private fun TechPill(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = color
        )
    }
}

@Composable
private fun HomeFab(
    onAddLink: () -> Unit,
    onAddPhoto: () -> Unit,
    onAddVoice: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (expanded) 45f else 0f, label = "fab_rotation")

    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AnimatedVisibility(visible = expanded) {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MiniFab(label = "链接采集", icon = Icons.Default.Link, onClick = { expanded = false; onAddLink() })
                MiniFab(label = "拍照识别", icon = Icons.Default.PhotoCamera, onClick = { expanded = false; onAddPhoto() })
                MiniFab(label = "录音灵感", icon = Icons.Default.Mic, onClick = { expanded = false; onAddVoice() })
            }
        }

        FloatingActionButton(
            onClick = { expanded = !expanded },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White,
            shape = CircleShape,
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp,
                hoveredElevation = 0.dp,
                focusedElevation = 0.dp
            )
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "新建",
                modifier = Modifier
                    .size(26.dp)
                    .graphicsLayer { rotationZ = rotation }
            )
        }
    }
}

@Composable
private fun MiniFab(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        FloatingActionButton(
            onClick = onClick,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            shape = CircleShape,
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp,
                hoveredElevation = 0.dp,
                focusedElevation = 0.dp
            ),
            modifier = Modifier.size(44.dp)
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateLinkSheet(
    onDismiss: () -> Unit,
    onConfirm: (url: String, title: String?, note: String?) -> Unit
) {
    var url by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "链接采集",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold)
            )

            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("URL") },
                placeholder = { Text("粘贴公众号/网页/arXiv 链接") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("标题（可选）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("备注（可选）") },
                modifier = Modifier.fillMaxWidth()
            )

            FloatingActionButton(
                onClick = { if (url.isNotBlank()) onConfirm(url.trim(), title.trim().ifBlank { null }, note.trim().ifBlank { null }) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text("确认发送", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold))
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

private fun getItemTypeName(type: ItemType): String = when (type) {
    ItemType.PAPER -> "论文"
    ItemType.INSIGHT -> "灵感"
    ItemType.VOICE -> "语音"
    ItemType.COMPETITION -> "竞赛"
}

private fun getItemAccent(type: ItemType): Color = when (type) {
    ItemType.PAPER -> Color(0xFF2F6DFF) // electric blue
    ItemType.INSIGHT -> Color(0xFFB24DFF) // neon purple
    ItemType.VOICE -> Color(0xFFFF8A00) // vivid orange
    ItemType.COMPETITION -> Color(0xFFFF2D55) // iOS pink
}

private fun getItemIcon(type: ItemType): ImageVector = when (type) {
    ItemType.PAPER -> Icons.AutoMirrored.Filled.Article
    ItemType.INSIGHT -> Icons.Default.Lightbulb
    ItemType.VOICE -> Icons.Default.Mic
    ItemType.COMPETITION -> Icons.Default.EmojiEvents
}

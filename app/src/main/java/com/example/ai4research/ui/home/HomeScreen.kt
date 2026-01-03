package com.example.ai4research.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.ai4research.core.floating.FloatingBallService
import com.example.ai4research.core.floating.OverlayPermission
import com.example.ai4research.domain.model.ItemMetaData
import com.example.ai4research.domain.model.ItemStatus
import com.example.ai4research.domain.model.ItemType
import com.example.ai4research.domain.model.ReadStatus
import com.example.ai4research.domain.model.ResearchItem
import com.example.ai4research.domain.model.TimelineEvent
import com.example.ai4research.ui.components.GistButton
import com.example.ai4research.ui.components.GistCard
import com.example.ai4research.ui.components.IOSTextField
import com.example.ai4research.ui.capture.CameraCaptureActivity
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
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
    val lifecycleOwner = LocalLifecycleOwner.current

    var showCreateLinkSheet by remember { mutableStateOf(false) }
    var hasOverlayPermission by remember { mutableStateOf(OverlayPermission.canDrawOverlays(context)) }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasOverlayPermission = OverlayPermission.canDrawOverlays(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(errorMessage) {
        val msg = errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearError()
    }

    val stats = remember(items) { buildHomeStats(items) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = viewModel::refresh
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            LargeTopAppBar(
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "我的研究",
                            style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "碎片采集 · AI 结构化 · 知识沉淀",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                        )
                    }
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
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("语音入口：请切换到底部「语音」Tab")
                    }
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
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        )
                    )
                )
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .pullRefresh(pullRefreshState)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = 140.dp
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    ResearchHeroCard(
                        stats = stats,
                        isRefreshing = isRefreshing,
                        onRefresh = viewModel::refresh
                    )
                }

                item {
                    QuickActionRow(
                        onAddLink = { showCreateLinkSheet = true },
                        onAddPhoto = {
                            context.startActivity(android.content.Intent(context, CameraCaptureActivity::class.java))
                        },
                        onAddVoice = {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("语音入口：请切换到底部「语音」Tab")
                            }
                        }
                    )
                }

                item {
                    FloatingBallCard(
                        hasOverlayPermission = hasOverlayPermission,
                        onEnable = {
                            if (hasOverlayPermission) {
                                FloatingBallService.start(context)
                            } else {
                                context.startActivity(OverlayPermission.createSettingsIntent(context))
                            }
                        },
                        onDisable = { FloatingBallService.stop(context) }
                    )
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

                item {
                    SectionHeader(
                        title = "最新动态",
                        subtitle = if (items.isEmpty()) "暂无条目" else "共 ${items.size} 条"
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
                        EmptyStateCard(
                            isRefreshing = isRefreshing,
                            onAddLink = { showCreateLinkSheet = true },
                            onAddPhoto = {
                                context.startActivity(android.content.Intent(context, CameraCaptureActivity::class.java))
                            },
                            onAddVoice = {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("语音入口：请切换到底部「语音」Tab")
                                }
                            },
                            onEnableFloating = {
                                if (hasOverlayPermission) {
                                    FloatingBallService.start(context)
                                } else {
                                    context.startActivity(OverlayPermission.createSettingsIntent(context))
                                }
                            }
                        )
                    }
                }
            }

            PullRefreshIndicator(
                refreshing = isRefreshing,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter),
                backgroundColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            )
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
private fun ResearchHeroCard(
    stats: HomeStats,
    isRefreshing: Boolean,
    onRefresh: () -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    val accentSoft = accent.copy(alpha = 0.12f)
    val lastSync = stats.latest?.let { formatTimestamp(it) } ?: "欢迎开始采集科研素材"

    GistCard(
        onClick = {},
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            accent.copy(alpha = 0.18f),
                            accentSoft,
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                        Text(
                            text = "最近更新：$lastSync",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }

                    Surface(
                        onClick = { if (!isRefreshing) onRefresh() },
                        shape = CircleShape,
                        color = accent.copy(alpha = 0.12f),
                        border = BorderStroke(0.5.dp, accent.copy(alpha = 0.45f)),
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (isRefreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = accent
                                )
                                Text(
                                    text = "同步中",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = accent
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = accent,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "同步",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = accent
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                    thickness = 0.5.dp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatChip(label = "全部", value = stats.total.toString(), color = accent)
                    StatChip(label = "处理中", value = stats.processing.toString(), color = MaterialTheme.colorScheme.secondary)
                    StatChip(label = "未读", value = stats.unread.toString(), color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(0.5.dp, color.copy(alpha = 0.4f)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = color
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun QuickActionRow(
    onAddLink: () -> Unit,
    onAddPhoto: () -> Unit,
    onAddVoice: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ActionCard(
            title = "链接采集",
            subtitle = "网页 / 公众号",
            icon = Icons.Default.Link,
            tint = Color(0xFF2F6DFF),
            onClick = onAddLink,
            modifier = Modifier.weight(1f)
        )
        ActionCard(
            title = "拍照识别",
            subtitle = "海报 / 幻灯片",
            icon = Icons.Default.PhotoCamera,
            tint = Color(0xFFFF8A00),
            onClick = onAddPhoto,
            modifier = Modifier.weight(1f)
        )
        ActionCard(
            title = "语音灵感",
            subtitle = "随手记录",
            icon = Icons.Default.Mic,
            tint = Color(0xFF30B0FF),
            onClick = onAddVoice,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun FloatingBallCard(
    hasOverlayPermission: Boolean,
    onEnable: () -> Unit,
    onDisable: () -> Unit
) {
    val statusColor = if (hasOverlayPermission) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    val statusLabel = if (hasOverlayPermission) "已授权" else "未授权"
    val actionLabel = if (hasOverlayPermission) "开启悬浮球" else "去授权"

    GistCard(onClick = {}) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "全局悬浮球",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        text = "在任何 App 一键采集链接和图片",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                TagPill(
                    text = statusLabel,
                    textColor = statusColor,
                    background = statusColor.copy(alpha = 0.12f),
                    borderColor = statusColor.copy(alpha = 0.45f)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionPill(
                    text = actionLabel,
                    color = MaterialTheme.colorScheme.primary,
                    onClick = onEnable,
                    modifier = Modifier.weight(1f)
                )
                ActionPill(
                    text = "关闭悬浮球",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    onClick = onDisable,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ActionPill(
    text: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        color = color.copy(alpha = 0.12f),
        shape = CircleShape,
        border = BorderStroke(0.5.dp, color.copy(alpha = 0.45f)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Text(
            text = text,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = color,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
            )
        }
    }
}

@Composable
private fun IOSSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String
) {
    IOSTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        leadingIcon = {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                modifier = Modifier.size(18.dp)
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceVariant
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
private fun EmptyStateCard(
    isRefreshing: Boolean,
    onAddLink: () -> Unit,
    onAddPhoto: () -> Unit,
    onAddVoice: () -> Unit,
    onEnableFloating: () -> Unit
) {
    GistCard(onClick = {}) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = if (isRefreshing) "正在同步..." else "还没有条目",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold)
            )
            Text(
                text = "从链接、图片或语音开始采集科研素材，AI 会自动生成结构化卡片与摘要。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionPill(
                    text = "采集链接",
                    color = MaterialTheme.colorScheme.primary,
                    onClick = onAddLink,
                    modifier = Modifier.weight(1f)
                )
                ActionPill(
                    text = "拍照识别",
                    color = MaterialTheme.colorScheme.primary,
                    onClick = onAddPhoto,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionPill(
                    text = "语音灵感",
                    color = MaterialTheme.colorScheme.primary,
                    onClick = onAddVoice,
                    modifier = Modifier.weight(1f)
                )
                ActionPill(
                    text = "开启悬浮球",
                    color = MaterialTheme.colorScheme.primary,
                    onClick = onEnableFloating,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun LibraryItemCard(
    item: ResearchItem,
    onClick: () -> Unit
) {
    val accent = getItemAccent(item.type)
    val summaryText = item.summary.ifBlank { "AI 正在解析，稍后生成摘要与结构化卡片。" }
    val metaTags = buildMetaTags(item)

    GistCard(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.6f)))),
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

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    StatusPill(status = item.status)

                    if (item.readStatus == ReadStatus.UNREAD) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }

                if (item.readStatus == ReadStatus.READING) {
                    TagPill(
                        text = "在读",
                        textColor = MaterialTheme.colorScheme.primary,
                        background = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = summaryText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                AnimatedVisibility(visible = item.status == ItemStatus.PROCESSING) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "AI 正在分析中",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                AnimatedVisibility(visible = item.status == ItemStatus.FAILED) {
                    Text(
                        text = "解析失败，可稍后重试",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TagPill(
                            text = getItemTypeName(item.type),
                            textColor = accent,
                            background = accent.copy(alpha = 0.12f),
                            borderColor = accent.copy(alpha = 0.45f)
                        )
                        metaTags.forEach { tag ->
                            TagPill(
                                text = tag,
                                textColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                background = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                                borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                            )
                        }
                    }
                    Text(
                        text = formatShortDate(item.createdAt),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusPill(status: ItemStatus) {
    val (label, color) = when (status) {
        ItemStatus.PROCESSING -> "解析中" to MaterialTheme.colorScheme.primary
        ItemStatus.DONE -> "已完成" to MaterialTheme.colorScheme.secondary
        ItemStatus.FAILED -> "失败" to MaterialTheme.colorScheme.error
    }
    TagPill(
        text = label,
        textColor = color,
        background = color.copy(alpha = 0.12f),
        borderColor = color.copy(alpha = 0.45f)
    )
}

@Composable
private fun TagPill(
    text: String,
    textColor: Color,
    background: Color,
    borderColor: Color
) {
    Surface(
        color = background,
        contentColor = textColor,
        shape = CircleShape,
        border = BorderStroke(0.5.dp, borderColor),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            color = textColor
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
                MiniFab(label = "语音灵感", icon = Icons.Default.Mic, onClick = { expanded = false; onAddVoice() })
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
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
            shadowElevation = 0.dp,
            tonalElevation = 0.dp
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
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
                placeholder = { Text("粘贴公众号网页/arXiv 链接") },
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

            GistButton(
                onClick = {
                    if (url.isNotBlank()) {
                        onConfirm(url.trim(), title.trim().ifBlank { null }, note.trim().ifBlank { null })
                    }
                },
                text = "确认发送",
                enabled = url.isNotBlank()
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

private data class HomeStats(
    val total: Int,
    val processing: Int,
    val unread: Int,
    val latest: Date?
)

private fun buildHomeStats(items: List<ResearchItem>): HomeStats {
    val latest = items.maxByOrNull { it.createdAt.time }?.createdAt
    return HomeStats(
        total = items.size,
        processing = items.count { it.status == ItemStatus.PROCESSING },
        unread = items.count { it.readStatus == ReadStatus.UNREAD },
        latest = latest
    )
}

private fun buildMetaTags(item: ResearchItem): List<String> {
    val tags = mutableListOf<String>()

    item.projectName?.takeIf { it.isNotBlank() }?.let { tags.add(it) }

    when (val meta = item.metaData) {
        is ItemMetaData.PaperMeta -> {
            meta.year?.let { tags.add(it.toString()) }
            meta.conference?.takeIf { it.isNotBlank() }?.let { tags.add(it) }
            if (meta.authors.isNotEmpty()) {
                tags.add(meta.authors.first())
            }
            tags.addAll(meta.tags.take(2))
        }
        is ItemMetaData.CompetitionMeta -> {
            val next = pickNextEvent(meta.timeline)
            next?.let { tags.add(it.name) }
            meta.organizer?.takeIf { it.isNotBlank() }?.let { tags.add(it) }
            meta.prizePool?.takeIf { it.isNotBlank() }?.let { tags.add(it) }
        }
        is ItemMetaData.InsightMeta -> {
            tags.addAll(meta.tags.take(3))
        }
        is ItemMetaData.VoiceMeta -> {
            tags.add("${meta.duration}s")
        }
        null -> Unit
    }

    return tags.take(4)
}

private fun pickNextEvent(timeline: List<TimelineEvent>): TimelineEvent? {
    if (timeline.isEmpty()) return null
    return timeline.firstOrNull { !it.isPassed } ?: timeline.maxByOrNull { it.date.time }
}

private fun formatShortDate(date: Date): String =
    SimpleDateFormat("MM/dd", Locale.CHINA).format(date)

private fun formatTimestamp(date: Date): String =
    SimpleDateFormat("MM/dd HH:mm", Locale.CHINA).format(date)

private fun getItemTypeName(type: ItemType): String = when (type) {
    ItemType.PAPER -> "论文"
    ItemType.INSIGHT -> "灵感"
    ItemType.VOICE -> "语音"
    ItemType.COMPETITION -> "竞赛"
}

private fun getItemAccent(type: ItemType): Color = when (type) {
    ItemType.PAPER -> Color(0xFF2F6DFF)
    ItemType.INSIGHT -> Color(0xFFB24DFF)
    ItemType.VOICE -> Color(0xFFFF8A00)
    ItemType.COMPETITION -> Color(0xFFFF2D55)
}

private fun getItemIcon(type: ItemType): ImageVector = when (type) {
    ItemType.PAPER -> Icons.AutoMirrored.Filled.Article
    ItemType.INSIGHT -> Icons.Default.Lightbulb
    ItemType.VOICE -> Icons.Default.Mic
    ItemType.COMPETITION -> Icons.Default.EmojiEvents
}

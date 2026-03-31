package com.example.ai4research.ui.project

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.ai4research.domain.model.ItemType
import com.example.ai4research.domain.model.ProjectContextDocument
import com.example.ai4research.domain.model.ProjectOverview
import com.example.ai4research.domain.model.ResearchItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectOverviewScreen(
    projectId: String,
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    viewModel: ProjectOverviewViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(projectId) {
        viewModel.load(projectId)
    }

    val markdownPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        readMarkdownFile(context, uri)?.let { (fileName, content) ->
            viewModel.saveProjectContext(
                projectId = projectId,
                fileName = fileName,
                markdownContent = content
            )
        } ?: viewModel.clearError()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = uiState.overview?.project?.name ?: "项目总览",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { markdownPickerLauncher.launch(arrayOf("text/markdown", "text/plain", "text/*")) }
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = "上传研究背景")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            uiState.overview == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = uiState.errorMessage ?: "未找到该项目",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }

            else -> {
                ProjectOverviewContent(
                    overview = uiState.overview!!,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    isSavingContext = uiState.isSavingContext,
                    onUploadContext = {
                        markdownPickerLauncher.launch(arrayOf("text/markdown", "text/plain", "text/*"))
                    },
                    onOpenItem = onNavigateToDetail
                )
            }
        }
    }
}

@Composable
private fun ProjectOverviewContent(
    overview: ProjectOverview,
    modifier: Modifier = Modifier,
    isSavingContext: Boolean,
    onUploadContext: () -> Unit,
    onOpenItem: (String) -> Unit
) {
    LazyColumn(
        modifier = modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            ProjectContextCard(
                contextDocument = overview.contextDocument,
                isSaving = isSavingContext,
                onUploadContext = onUploadContext
            )
        }

        item {
            ProjectStatsCard(overview)
        }

        item {
            SectionTitle("本项目最近新增")
        }
        items(overview.recentItems, key = { it.id }) { item ->
            ProjectItemCard(item = item, onOpenItem = onOpenItem)
        }

        item {
            SectionTitle("本项目重点论文")
        }
        items(overview.keyPapers, key = { "paper_${it.id}" }) { item ->
            ProjectItemCard(item = item, onOpenItem = onOpenItem)
        }

        item {
            SectionTitle("本项目灵感汇总")
        }
        items(overview.recentInsights, key = { "insight_${it.id}" }) { item ->
            ProjectItemCard(item = item, onOpenItem = onOpenItem)
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ProjectContextCard(
    contextDocument: ProjectContextDocument?,
    isSaving: Boolean,
    onUploadContext: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        ),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "研究背景",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                TextButton(onClick = onUploadContext, enabled = !isSaving) {
                    Text(if (isSaving) "上传中..." else if (contextDocument == null) "上传 Markdown" else "替换 Markdown")
                }
            }

            if (contextDocument == null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "上传一份 Markdown 文档，描述当前研究方向、问题、边界与目标，后续项目级 AI 能基于它做联动分析。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                )
            } else {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = contextDocument.title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = contextDocument.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.76f)
                )
                if (contextDocument.keywords.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        contextDocument.keywords.take(4).forEach { keyword ->
                            KeywordChip(keyword)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeywordChip(text: String) {
    Box(
        modifier = Modifier
            .background(Color(0xFF06B6D4).copy(alpha = 0.12f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFF0891B2)
        )
    }
}

@Composable
private fun ProjectStatsCard(overview: ProjectOverview) {
    val stats = overview.stats
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        ),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "项目概况",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(14.dp))
            StatsRow("全部条目", stats.totalItems.toString())
            StatsRow("论文", stats.paperCount.toString())
            StatsRow("资料", stats.articleCount.toString())
            StatsRow("灵感", stats.insightCount.toString())
            StatsRow("重复关系", stats.duplicateRelationCount.toString())
            StatsRow("资料关联论文", stats.articlePaperRelationCount.toString())
        }
    }
}

@Composable
private fun StatsRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun ProjectItemCard(
    item: ResearchItem,
    onOpenItem: (String) -> Unit
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenItem(item.id) }
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = typeLabel(item.type),
                    style = MaterialTheme.typography.labelMedium,
                    color = typeAccent(item.type)
                )
                if (item.isStarred) {
                    Text(
                        text = "已星标",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFF59E0B)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
            )
            if (item.summary.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = item.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    maxLines = 2
                )
            }
        }
    }
}

private fun typeLabel(type: ItemType): String = when (type) {
    ItemType.PAPER -> "论文"
    ItemType.ARTICLE -> "资料"
    ItemType.COMPETITION -> "比赛"
    ItemType.INSIGHT -> "灵感"
    ItemType.VOICE -> "语音"
}

private fun typeAccent(type: ItemType): Color = when (type) {
    ItemType.PAPER -> Color(0xFF2563EB)
    ItemType.ARTICLE -> Color(0xFF6366F1)
    ItemType.COMPETITION -> Color(0xFFEF4444)
    ItemType.INSIGHT -> Color(0xFF10B981)
    ItemType.VOICE -> Color(0xFFF97316)
}

private fun readMarkdownFile(context: Context, uri: Uri): Pair<String, String>? {
    return runCatching {
        val fileName = context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: "research-context.md"

        val content = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            ?: return null

        fileName to content
    }.getOrNull()
}

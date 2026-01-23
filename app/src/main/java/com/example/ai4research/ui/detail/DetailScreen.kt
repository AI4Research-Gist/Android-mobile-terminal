package com.example.ai4research.ui.detail

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.ai4research.domain.model.ItemType

import dev.jeziellago.compose.markdowntext.MarkdownText
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    itemId: String,
    onNavigateBack: () -> Unit
) {
    val viewModel: DetailViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(itemId) {
        viewModel.load(itemId)
        viewModel.refreshProjects()
    }

    val item = uiState.item
    val title = item?.title ?: "详情"
    val type = item?.type
    val typeName = type?.let { getItemTypeName(it) }.orEmpty()
    val markdownContent = item?.contentMarkdown?.takeIf { it.isNotBlank() } ?: item?.summary.orEmpty()
    val projectName = item?.projectName?.takeIf { it.isNotBlank() } ?: "未归属"
    val projects = uiState.projects

    // Edit state
    var isEditing by remember { mutableStateOf(false) }
    var editSummary by remember(item) { mutableStateOf(item?.summary ?: "") }
    var editContent by remember(item) { mutableStateOf(item?.contentMarkdown ?: "") }
    var projectMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    if (isEditing) {
                        IconButton(onClick = {
                            viewModel.saveContent(editSummary, editContent)
                            isEditing = false
                        }) {
                            Icon(Icons.Default.Check, contentDescription = "保存")
                        }
                    } else {
                        IconButton(onClick = { isEditing = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "编辑")
                        }
                    }
                    IconButton(onClick = { /* TODO */ }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "更多")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            // Simulated iOS Bottom Toolbar (Glassy/Clean)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                   IconButton(onClick = { viewModel.markAsRead() }) {
                        Icon(Icons.Default.CheckCircle, contentDescription = "标记已读", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { /* Chat */ }) {
                        Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "AI 对话", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = {
                        viewModel.delete()
                        onNavigateBack()
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            // Hero
            val heroAccent = type?.let { getItemAccent(it) } ?: MaterialTheme.colorScheme.primary
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(168.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(heroAccent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (typeName.isNotBlank()) {
                        Text(
                            text = typeName,
                            style = MaterialTheme.typography.labelLarge,
                            color = heroAccent
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Text(
                        text = "AI 总结",
                        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                        color = heroAccent
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Title & meta
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = item?.originUrl ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Project selector (auto sync)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable { projectMenuExpanded = true }
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Text(
                    text = "归属项目",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = projectName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (uiState.isProjectSaving) "同步中..." else "更改",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            DropdownMenu(
                expanded = projectMenuExpanded,
                onDismissRequest = { projectMenuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("未归属") },
                    onClick = {
                        projectMenuExpanded = false
                        viewModel.updateProject(null)
                    }
                )
                if (projects.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("暂无项目") },
                        onClick = { projectMenuExpanded = false },
                        enabled = false
                    )
                } else {
                    projects.forEach { project ->
                        DropdownMenuItem(
                            text = { Text(project.name) },
                            onClick = {
                                projectMenuExpanded = false
                                viewModel.updateProject(project.id)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            
            // Content Card (Lyrics style)
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    if (isEditing) {
                        OutlinedTextField(
                            value = editSummary,
                            onValueChange = { editSummary = it },
                            label = { Text("摘要") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = editContent,
                            onValueChange = { editContent = it },
                            label = { Text("详细内容 (Markdown)") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 10
                        )
                    } else {
                        MarkdownText(
                            markdown = if (markdownContent.isNotBlank()) markdownContent else "（暂无内容）",
                            style = TextStyle(
                                fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                                lineHeight = 28.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Start
                            )
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(80.dp)) // Bottom padding
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
    ItemType.PAPER -> Color(0xFF2F6DFF)
    ItemType.INSIGHT -> Color(0xFFB24DFF)
    ItemType.VOICE -> Color(0xFFFF8A00)
    ItemType.COMPETITION -> Color(0xFFFF2D55)
}

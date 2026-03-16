package com.example.ai4research.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
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
    
    // 解析竞赛元数据
    val competitionMeta = item?.metaData as? com.example.ai4research.domain.model.ItemMetaData.CompetitionMeta
    
    // 解析语音元数据
    val voiceMeta = item?.metaData as? com.example.ai4research.domain.model.ItemMetaData.VoiceMeta

    // Edit state
    var isEditing by remember { mutableStateOf(false) }
    var editSummary by remember(item) { mutableStateOf(item?.summary ?: "") }
    var editContent by remember(item) { mutableStateOf(item?.contentMarkdown ?: "") }
    var showProjectSheet by remember { mutableStateOf(false) }  // 改用底部弹出面板
    
    // 竞赛特有字段编辑状态
    var editOrganizer by remember(competitionMeta) { mutableStateOf(competitionMeta?.organizer ?: "") }
    var editDeadline by remember(competitionMeta) { mutableStateOf(competitionMeta?.deadline ?: "") }
    var editTheme by remember(competitionMeta) { mutableStateOf(competitionMeta?.theme ?: "") }
    var editCompetitionType by remember(competitionMeta) { mutableStateOf(competitionMeta?.competitionType ?: "") }
    var editPrizePool by remember(competitionMeta) { mutableStateOf(competitionMeta?.prizePool ?: "") }
    var editWebsite by remember(competitionMeta) { mutableStateOf(competitionMeta?.website ?: "") }
    var editRegistrationUrl by remember(competitionMeta) { mutableStateOf(competitionMeta?.registrationUrl ?: "") }
    var editRegistrationDeadline by remember(competitionMeta) {
        mutableStateOf(
            competitionMeta?.timeline
                ?.firstOrNull { it.name.contains("报名截止") }
                ?.date
                ?.let { formatTimelineDate(it) }
                ?: ""
        )
    }
    var editSubmissionDeadline by remember(competitionMeta) {
        mutableStateOf(
            competitionMeta?.timeline
                ?.firstOrNull { it.name.contains("提交截止") }
                ?.date
                ?.let { formatTimelineDate(it) }
                ?: ""
        )
    }
    var editResultDate by remember(competitionMeta) {
        mutableStateOf(
            competitionMeta?.timeline
                ?.firstOrNull { it.name.contains("结果公布") }
                ?.date
                ?.let { formatTimelineDate(it) }
                ?: ""
        )
    }
    
    // 语音特有字段编辑状态
    var editTranscription by remember(voiceMeta) { mutableStateOf(voiceMeta?.transcription ?: item?.summary ?: "") }
    
    // Create project dialog state
    var showCreateProjectDialog by remember { mutableStateOf(false) }
    var newProjectName by remember { mutableStateOf("") }
    
    // 新建项目对话框
    if (showCreateProjectDialog) {
        AlertDialog(
            onDismissRequest = { 
                showCreateProjectDialog = false
                newProjectName = ""
            },
            title = { Text("新建项目") },
            text = {
                Column {
                    Text(
                        text = "创建新项目并自动关联到当前条目",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = newProjectName,
                        onValueChange = { newProjectName = it },
                        label = { Text("项目名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.createProject(newProjectName, autoAssign = true)
                        showCreateProjectDialog = false
                        newProjectName = ""
                    },
                    enabled = newProjectName.isNotBlank() && !uiState.isCreatingProject
                ) {
                    Text(if (uiState.isCreatingProject) "创建中..." else "创建")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showCreateProjectDialog = false
                    newProjectName = ""
                }) {
                    Text("取消")
                }
            }
        )
    }

    // 使用 Box 包裹，确保项目选择面板在最上层
    Box(modifier = Modifier.fillMaxSize()) {
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
                            // 根据类型构建 metaJson
                            val metaJson = when (type) {
                                ItemType.COMPETITION -> buildCompetitionMetaJson(
                                    organizer = editOrganizer,
                                    deadline = editDeadline,
                                    theme = editTheme,
                                    competitionType = editCompetitionType,
                                    prizePool = editPrizePool,
                                    website = editWebsite,
                                    registrationUrl = editRegistrationUrl,
                                    registrationDeadline = editRegistrationDeadline,
                                    submissionDeadline = editSubmissionDeadline,
                                    resultDate = editResultDate
                                )
                                ItemType.VOICE -> buildVoiceMetaJson(
                                    transcription = editTranscription,
                                    duration = voiceMeta?.duration ?: 0
                                )
                                else -> null
                            }
                            // 语音类型使用转写文本作为summary
                            val summaryToSave = if (type == ItemType.VOICE) editTranscription else editSummary
                            viewModel.saveContent(summaryToSave, editContent, metaJson)
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
            // Simulated iOS Bottom Toolbar (Liquid Glass Effect)
            val isStarred = item?.isStarred == true
            val isDark = isSystemInDarkTheme()
            // More transparent liquid glass effect
            val glassBackground = if (isDark) {
                Color.White.copy(alpha = 0.03f)
            } else {
                Color.White.copy(alpha = 0.12f)
            }
            val glassBorder = if (isDark) {
                Color.White.copy(alpha = 0.06f)
            } else {
                Color.White.copy(alpha = 0.3f)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(glassBackground)
                    .border(
                        width = 1.dp,
                        color = glassBorder,
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                    )
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                   IconButton(onClick = { viewModel.markAsRead() }) {
                        Icon(Icons.Default.CheckCircle, contentDescription = "标记已读", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { viewModel.toggleStar() }) {
                        Icon(
                            imageVector = if (isStarred) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = if (isStarred) "取消标星" else "标星",
                            tint = if (isStarred) Color(0xFFFFD700) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
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

            // Project selector (auto sync) - Liquid Glass Effect
            val isDarkTheme = isSystemInDarkTheme()
            val selectorBackground = if (isDarkTheme) {
                Color.White.copy(alpha = 0.03f)
            } else {
                Color.White.copy(alpha = 0.12f)
            }
            val selectorBorder = if (isDarkTheme) {
                Color.White.copy(alpha = 0.06f)
            } else {
                Color.White.copy(alpha = 0.3f)
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(selectorBackground)
                    .border(
                        width = 1.dp,
                        color = selectorBorder,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .clickable { showProjectSheet = true }
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

            Spacer(modifier = Modifier.height(24.dp))
            
            // Content Card (Lyrics style) - Liquid Glass Effect
            val cardBackground = if (isDarkTheme) {
                Color.White.copy(alpha = 0.03f)
            } else {
                Color.White.copy(alpha = 0.12f)
            }
            val cardBorder = if (isDarkTheme) {
                Color.White.copy(alpha = 0.06f)
            } else {
                Color.White.copy(alpha = 0.3f)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(cardBackground)
                    .border(
                        width = 1.dp,
                        color = cardBorder,
                        shape = RoundedCornerShape(24.dp)
                    )
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    if (isEditing) {
                        // 竞赛类型显示额外字段
                        if (type == ItemType.COMPETITION) {
                            Text(
                                text = "竞赛信息",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            OutlinedTextField(
                                value = editOrganizer,
                                onValueChange = { editOrganizer = it },
                                label = { Text("主办方") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            OutlinedTextField(
                                value = editDeadline,
                                onValueChange = { editDeadline = it },
                                label = { Text("截止日期 (如: 2026-03-01)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            OutlinedTextField(
                                value = editTheme,
                                onValueChange = { editTheme = it },
                                label = { Text("竞赛主题") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            OutlinedTextField(
                                value = editCompetitionType,
                                onValueChange = { editCompetitionType = it },
                                label = { Text("竞赛类型 (如: 数据科学/算法/创意)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            OutlinedTextField(
                                value = editPrizePool,
                                onValueChange = { editPrizePool = it },
                                label = { Text("奖金池") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            OutlinedTextField(
                                value = editWebsite,
                                onValueChange = { editWebsite = it },
                                label = { Text("官网链接") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            OutlinedTextField(
                                value = editRegistrationUrl,
                                onValueChange = { editRegistrationUrl = it },
                                label = { Text("报名链接") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            OutlinedTextField(
                                value = editRegistrationDeadline,
                                onValueChange = { editRegistrationDeadline = it },
                                label = { Text("报名截止 (YYYY-MM-DD)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            OutlinedTextField(
                                value = editSubmissionDeadline,
                                onValueChange = { editSubmissionDeadline = it },
                                label = { Text("提交截止 (YYYY-MM-DD)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            OutlinedTextField(
                                value = editResultDate,
                                onValueChange = { editResultDate = it },
                                label = { Text("结果公布 (YYYY-MM-DD)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            
                            Spacer(modifier = Modifier.height(20.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(20.dp))
                            
                            Text(
                                text = "基本信息",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        
                        // 语音类型显示转写文本编辑
                        if (type == ItemType.VOICE) {
                            Text(
                                text = "语音转写",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFFFF8A00)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "时长: ${voiceMeta?.duration?.let { "${it / 60}分${it % 60}秒" } ?: "未知"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            OutlinedTextField(
                                value = editTranscription,
                                onValueChange = { editTranscription = it },
                                label = { Text("转写内容") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 5,
                                placeholder = { Text("编辑语音转写的文本内容...") }
                            )
                            
                            Spacer(modifier = Modifier.height(20.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(20.dp))
                            
                            Text(
                                text = "备注信息",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFFFF8A00)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        
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
                        // 非编辑模式：如果是竞赛，先显示竞赛信息卡片
                        if (type == ItemType.COMPETITION && competitionMeta != null) {
                            CompetitionInfoCard(competitionMeta, isDarkTheme)
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                        
                        // 如果是语音，显示语音信息卡片
                        if (type == ItemType.VOICE && voiceMeta != null) {
                            VoiceInfoCard(voiceMeta, isDarkTheme)
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                        
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
    
        // 项目选择底部弹出面板 (在 Scaffold 之后，确保 z-index 最高)
        if (showProjectSheet) {
            val sheetIsDark = isSystemInDarkTheme()
            val sheetBackground = if (sheetIsDark) Color(0xFF1C1C1E) else Color.White
            val cardBg = if (sheetIsDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.04f)
            val borderColor = if (sheetIsDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.08f)
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable { showProjectSheet = false }
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .background(sheetBackground)
                        .clickable(enabled = false) { } // 阻止点击穿透
                        .padding(20.dp)
                ) {
                    // 拖动指示条
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .width(40.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (sheetIsDark) Color.White.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.1f))
                    )
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    Text(
                        text = "选择项目",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = if (sheetIsDark) Color.White else Color.Black
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 新建项目按钮
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                            .clickable { 
                                showProjectSheet = false
                                showCreateProjectDialog = true
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "新建项目",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // 未归属选项
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(cardBg)
                            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                            .clickable { 
                                showProjectSheet = false
                                viewModel.updateProject(null)
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "未归属",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (sheetIsDark) Color.White.copy(alpha = 0.7f) else Color.Black.copy(alpha = 0.7f)
                        )
                        if (item?.projectId == null) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "已选中",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    
                    if (projects.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // 项目列表
                        projects.forEach { project ->
                            val isSelected = item?.projectId == project.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else cardBg)
                                    .border(1.dp, if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else borderColor, RoundedCornerShape(12.dp))
                                    .clickable { 
                                        showProjectSheet = false
                                        viewModel.updateProject(project.id)
                                    }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = project.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = if (sheetIsDark) Color.White else Color.Black
                                    )
                                    if (isSelected) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = "已选中",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                // 删除按钮
                                IconButton(
                                    onClick = {
                                        viewModel.deleteProject(project.id)
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "删除项目",
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    } // 关闭 Box
}

private fun getItemTypeName(type: ItemType): String = when (type) {
    ItemType.PAPER -> "论文"
    ItemType.INSIGHT -> "动态"
    ItemType.VOICE -> "语音"
    ItemType.COMPETITION -> "竞赛"
}

private fun getItemAccent(type: ItemType): Color = when (type) {
    ItemType.PAPER -> Color(0xFF2F6DFF)
    ItemType.INSIGHT -> Color(0xFFB24DFF)
    ItemType.VOICE -> Color(0xFFFF8A00)
    ItemType.COMPETITION -> Color(0xFFFF2D55)
}

/**
 * 构建竞赛元数据 JSON
 */
private fun buildCompetitionMetaJson(
    organizer: String,
    deadline: String,
    theme: String,
    competitionType: String,
    prizePool: String,
    website: String,
    registrationUrl: String,
    registrationDeadline: String,
    submissionDeadline: String,
    resultDate: String
): String {
    val metaMap = mutableMapOf<String, Any>()
    if (organizer.isNotBlank()) metaMap["organizer"] = organizer
    if (deadline.isNotBlank()) metaMap["deadline"] = deadline
    if (theme.isNotBlank()) metaMap["theme"] = theme
    if (competitionType.isNotBlank()) metaMap["competitionType"] = competitionType
    if (prizePool.isNotBlank()) metaMap["prizePool"] = prizePool
    if (website.isNotBlank()) metaMap["website"] = website
    if (registrationUrl.isNotBlank()) metaMap["registrationUrl"] = registrationUrl

    val timeline = mutableListOf<Map<String, Any>>()
    if (registrationDeadline.isNotBlank()) timeline += mapOf("name" to "报名截止", "date" to registrationDeadline, "isPassed" to false)
    if (submissionDeadline.isNotBlank()) timeline += mapOf("name" to "提交截止", "date" to submissionDeadline, "isPassed" to false)
    if (resultDate.isNotBlank()) timeline += mapOf("name" to "结果公布", "date" to resultDate, "isPassed" to false)
    if (timeline.isNotEmpty()) metaMap["timeline"] = timeline
    
    return try {
        org.json.JSONObject(metaMap as Map<*, *>).toString()
    } catch (e: Exception) {
        "{}"
    }
}

/**
 * 竞赛信息卡片组件
 */
@Composable
private fun CompetitionInfoCard(
    meta: com.example.ai4research.domain.model.ItemMetaData.CompetitionMeta,
    isDark: Boolean
) {
    val cardBg = if (isDark) Color(0xFF1E1E2E) else Color(0xFFF8F9FA)
    val accentColor = Color(0xFFFF2D55)
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .padding(16.dp)
    ) {
        Text(
            text = "📋 竞赛信息",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = accentColor
        )
        Spacer(modifier = Modifier.height(12.dp))
        
        meta.organizer?.takeIf { it.isNotBlank() }?.let {
            CompetitionInfoRow("主办方", it, isDark)
        }
        meta.deadline?.takeIf { it.isNotBlank() }?.let {
            CompetitionInfoRow("截止日期", it, isDark)
        }
        meta.website?.takeIf { it.isNotBlank() }?.let {
            CompetitionInfoRow("官网链接", it, isDark)
        }
        meta.registrationUrl?.takeIf { it.isNotBlank() }?.let {
            CompetitionInfoRow("报名链接", it, isDark)
        }
        meta.theme?.takeIf { it.isNotBlank() }?.let {
            CompetitionInfoRow("竞赛主题", it, isDark)
        }
        meta.competitionType?.takeIf { it.isNotBlank() }?.let {
            CompetitionInfoRow("竞赛类型", it, isDark)
        }
        meta.prizePool?.takeIf { it.isNotBlank() }?.let {
            CompetitionInfoRow("奖金池", it, isDark)
        }
        meta.timeline?.forEach { event ->
            CompetitionInfoRow(event.name, formatTimelineDate(event.date), isDark)
        }
    }
}

private fun formatTimelineDate(date: java.util.Date): String {
    val format = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
    return format.format(date)
}

@Composable
private fun CompetitionInfoRow(label: String, value: String, isDark: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isDark) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.6f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = if (isDark) Color.White else Color.Black
        )
    }
}

/**
 * 构建语音元数据 JSON
 */
private fun buildVoiceMetaJson(
    transcription: String,
    duration: Int
): String {
    val metaMap = mutableMapOf<String, Any>()
    metaMap["transcription"] = transcription
    metaMap["duration"] = duration
    
    return try {
        org.json.JSONObject(metaMap as Map<*, *>).toString()
    } catch (e: Exception) {
        "{}"
    }
}

/**
 * 语音信息卡片组件
 */
@Composable
private fun VoiceInfoCard(
    meta: com.example.ai4research.domain.model.ItemMetaData.VoiceMeta,
    isDark: Boolean
) {
    val cardBg = if (isDark) Color(0xFF1E1E2E) else Color(0xFFF8F9FA)
    val accentColor = Color(0xFFFF8A00)
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .padding(16.dp)
    ) {
        Text(
            text = "🎙️ 语音信息",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = accentColor
        )
        Spacer(modifier = Modifier.height(12.dp))
        
        // 时长
        val duration = meta.duration
        val durationStr = if (duration > 0) {
            val minutes = duration / 60
            val seconds = duration % 60
            if (minutes > 0) "${minutes}分${seconds}秒" else "${seconds}秒"
        } else "未知"
        VoiceInfoRow("录音时长", durationStr, isDark)
        
        // 转写文本预览
        meta.transcription?.takeIf { it.isNotBlank() }?.let {
            VoiceInfoRow("转写预览", if (it.length > 50) it.take(50) + "..." else it, isDark)
        }
    }
}

@Composable
private fun VoiceInfoRow(label: String, value: String, isDark: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isDark) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.6f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = if (isDark) Color.White else Color.Black,
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}

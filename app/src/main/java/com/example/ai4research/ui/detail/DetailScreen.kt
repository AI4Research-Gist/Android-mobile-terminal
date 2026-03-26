package com.example.ai4research.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.ai4research.domain.model.ItemType
import dev.jeziellago.compose.markdowntext.MarkdownText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    itemId: String,
    onNavigateBack: () -> Unit
) {
    val viewModel: DetailViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val summaryPrefs = remember {
        context.getSharedPreferences("paper_summary_prefs", android.content.Context.MODE_PRIVATE)
    }

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
    val paperMeta = item?.metaData as? com.example.ai4research.domain.model.ItemMetaData.PaperMeta
    val articleMeta = item?.metaData as? com.example.ai4research.domain.model.ItemMetaData.ArticleMeta
    val insightMeta = remember(item) { item?.let(::parseInsightDetail) }
    
    // 解析竞赛元数据
    val competitionMeta = item?.metaData as? com.example.ai4research.domain.model.ItemMetaData.CompetitionMeta
    
    // 解析语音元数据
    val voiceMeta = item?.metaData as? com.example.ai4research.domain.model.ItemMetaData.VoiceMeta

    // Edit state
    var isEditing by remember { mutableStateOf(false) }
    var editSummary by remember(item) { mutableStateOf(item?.summary ?: "") }
    var editNote by remember(item) { mutableStateOf(item?.note ?: "") }
    var editContent by remember(item) { mutableStateOf(item?.contentMarkdown ?: "") }
    var showProjectSheet by remember { mutableStateOf(false) }  // 改用底部弹出面板
    var showMoreMenu by remember { mutableStateOf(false) }
    var showInsightImagePreview by remember { mutableStateOf(false) }
    
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
                            viewModel.saveContent(summaryToSave, editNote.ifBlank { null }, editContent, metaJson)
                            isEditing = false
                        }) {
                            Icon(Icons.Default.Check, contentDescription = "保存")
                        }
                    } else {
                        IconButton(onClick = { isEditing = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "编辑")
                        }
                    }
                    Box {
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "更多")
                        }
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false }
                        ) {
                            if (type == ItemType.PAPER) {
                                DropdownMenuItem(
                                    text = { Text(if (uiState.isRegeneratingSummary) "重新生成中..." else "重新生成双语摘要") },
                                    onClick = {
                                        showMoreMenu = false
                                        viewModel.regeneratePaperBilingualSummary()
                                    },
                                    enabled = !uiState.isRegeneratingSummary
                                )
                            }
                        }
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

            if (type == ItemType.PAPER && paperMeta != null && item != null) {
                PaperIndexInfoCard(
                    item = item,
                    meta = paperMeta,
                    isDark = isDarkTheme,
                    summaryPrefs = summaryPrefs
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
            if (type == ItemType.ARTICLE && articleMeta != null && item != null) {
                ArticleInfoCard(
                    item = item,
                    meta = articleMeta,
                    isDark = isDarkTheme
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
            
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
                            value = editNote,
                            onValueChange = { editNote = it },
                            label = { Text("备注") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            placeholder = { Text("记录为什么存它、和哪个项目有关、之后要在电脑端怎么处理") }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        OutlinedTextField(
                            value = editSummary,
                            onValueChange = { editSummary = it },
                            label = { Text(if (type == ItemType.PAPER) "极短摘要" else "摘要") },
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

                        if (type == ItemType.INSIGHT && item != null && insightMeta != null) {
                            InsightMediaCard(
                                item = item,
                                detail = insightMeta,
                                isDark = isDarkTheme,
                                onImageClick = { showInsightImagePreview = true }
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        item?.note?.takeIf { it.isNotBlank() }?.let { note ->
                            NoteCard(note = note, isDark = isDarkTheme)
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
        if (showInsightImagePreview && !item?.originUrl.isNullOrBlank()) {
            FullscreenInsightImage(
                imageUri = item?.originUrl.orEmpty(),
                onDismiss = { showInsightImagePreview = false }
            )
        }
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
    ItemType.ARTICLE -> "资料"
    ItemType.INSIGHT -> "动态"
    ItemType.VOICE -> "语音"
    ItemType.COMPETITION -> "竞赛"
}

private fun getItemAccent(type: ItemType): Color = when (type) {
    ItemType.PAPER -> Color(0xFF2F6DFF)
    ItemType.ARTICLE -> Color(0xFF6366F1)
    ItemType.INSIGHT -> Color(0xFFB24DFF)
    ItemType.VOICE -> Color(0xFFFF8A00)
    ItemType.COMPETITION -> Color(0xFFFF2D55)
}

@Composable
private fun PaperIndexInfoCard(
    item: com.example.ai4research.domain.model.ResearchItem,
    meta: com.example.ai4research.domain.model.ItemMetaData.PaperMeta,
    isDark: Boolean,
    summaryPrefs: android.content.SharedPreferences
) {
    val cardBg = if (isDark) Color(0xFF1E1E2E) else Color(0xFFF8F9FA)
    val accentColor = Color(0xFF2F6DFF)
    val venue = meta.conference ?: meta.source ?: item.originUrl
    val authorText = meta.authors.joinToString(", ").ifBlank { "未知作者" }
    val keywords = meta.keywords.ifEmpty { meta.tags }
    val summaryShort = meta.summaryShort?.takeIf { it.isNotBlank() } ?: item.summary.takeIf { it.isNotBlank() }
    val hasBilingualSummary = !meta.summaryZh.isNullOrBlank() && !meta.summaryEn.isNullOrBlank()
    var showEnglishSummary by rememberSaveable(item.id, meta.summaryZh, meta.summaryEn) {
        mutableStateOf(summaryPrefs.getString("paper_summary_language", "zh") == "en")
    }
    val displayedSummary = when {
        hasBilingualSummary && showEnglishSummary -> meta.summaryEn
        !meta.summaryZh.isNullOrBlank() -> meta.summaryZh
        else -> summaryShort
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .padding(16.dp)
    ) {
        Text(
            text = "论文索引",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = accentColor
        )
        Spacer(modifier = Modifier.height(12.dp))

        PaperIndexRow("作者", authorText, isDark)
        meta.year?.let { PaperIndexRow("年份", it.toString(), isDark) }
        venue?.takeIf { it.isNotBlank() }?.let { PaperIndexRow("来源", it, isDark) }
        meta.identifier?.takeIf { it.isNotBlank() }?.let { PaperIndexRow("标识符", it, isDark) }
        item.originUrl?.takeIf { it.isNotBlank() }?.let { PaperIndexRow("原始链接", it, isDark) }

        displayedSummary?.let {
            Spacer(modifier = Modifier.height(12.dp))
            PaperSummarySection(
                label = if (hasBilingualSummary) {
                    if (showEnglishSummary) "English 摘要" else "中文摘要"
                } else {
                    "Preprint 总结"
                },
                value = it,
                isDark = isDark
            )
        }

        if (hasBilingualSummary) {
            Spacer(modifier = Modifier.height(10.dp))
            SummaryLanguageToggle(
                isDark = isDark,
                showEnglish = showEnglishSummary,
                onSelectChinese = {
                    showEnglishSummary = false
                    summaryPrefs.edit().putString("paper_summary_language", "zh").apply()
                },
                onSelectEnglish = {
                    showEnglishSummary = true
                    summaryPrefs.edit().putString("paper_summary_language", "en").apply()
                }
            )
        }

        if (meta.domainTags.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            PaperTagSection("领域标签", meta.domainTags, isDark)
        }

        if (keywords.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            PaperTagSection("关键词", keywords, isDark)
        }

        if (meta.methodTags.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            PaperTagSection("方法标签", meta.methodTags, isDark)
        }
    }
}

@Composable
private fun SummaryLanguageToggle(
    isDark: Boolean,
    showEnglish: Boolean,
    onSelectChinese: () -> Unit,
    onSelectEnglish: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SummaryLanguageChip(
            label = "中文",
            selected = !showEnglish,
            isDark = isDark,
            onClick = onSelectChinese
        )
        SummaryLanguageChip(
            label = "English",
            selected = showEnglish,
            isDark = isDark,
            onClick = onSelectEnglish
        )
    }
}

@Composable
private fun SummaryLanguageChip(
    label: String,
    selected: Boolean,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val background = if (selected) {
        if (isDark) Color(0xFF2F6DFF).copy(alpha = 0.28f) else Color(0xFF2F6DFF).copy(alpha = 0.12f)
    } else {
        if (isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.04f)
    }
    val textColor = if (selected) {
        if (isDark) Color(0xFF9CC2FF) else Color(0xFF2F6DFF)
    } else {
        if (isDark) Color.White.copy(alpha = 0.7f) else Color.Black.copy(alpha = 0.6f)
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(background)
            .border(
                width = 1.dp,
                color = if (selected) textColor.copy(alpha = 0.5f) else Color.Transparent,
                shape = RoundedCornerShape(999.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = textColor
        )
    }
}

@Composable
private fun PaperSummarySection(label: String, value: String, isDark: Boolean) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (isDark) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isDark) Color.White else Color.Black
        )
    }
}

@Composable
private fun PaperIndexRow(label: String, value: String, isDark: Boolean) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (isDark) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = if (isDark) Color.White else Color.Black
        )
    }
}

@Composable
private fun PaperTagSection(label: String, values: List<String>, isDark: Boolean) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (isDark) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = values.joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = if (isDark) Color.White else Color.Black
        )
    }
}

@Composable
private fun ArticleInfoCard(
    item: com.example.ai4research.domain.model.ResearchItem,
    meta: com.example.ai4research.domain.model.ItemMetaData.ArticleMeta,
    isDark: Boolean
) {
    val cardBg = if (isDark) Color(0xFF1E1E2E) else Color(0xFFF8F9FA)
    val accentColor = Color(0xFF6366F1)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .padding(16.dp)
    ) {
        Text(
            text = "资料索引",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = accentColor
        )
        Spacer(modifier = Modifier.height(12.dp))

        meta.platform?.takeIf { it.isNotBlank() }?.let { PaperIndexRow("平台", it, isDark) }
        meta.accountName?.takeIf { it.isNotBlank() }?.let { PaperIndexRow("账号", it, isDark) }
        meta.author?.takeIf { it.isNotBlank() }?.let { PaperIndexRow("作者", it, isDark) }
        meta.publishDate?.takeIf { it.isNotBlank() }?.let { PaperIndexRow("发布时间", it, isDark) }
        meta.identifier?.takeIf { it.isNotBlank() }?.let { PaperIndexRow("提及编号", it, isDark) }
        item.originUrl?.takeIf { it.isNotBlank() }?.let { PaperIndexRow("原始链接", it, isDark) }
        meta.summaryShort?.takeIf { it.isNotBlank() }?.let {
            Spacer(modifier = Modifier.height(12.dp))
            PaperSummarySection("资料摘要", it, isDark)
        }
        if (meta.corePoints.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            PaperTagSection("核心观点", meta.corePoints, isDark)
        }
        if (meta.keywords.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            PaperTagSection("关键词", meta.keywords, isDark)
        }
        if (meta.topicTags.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            PaperTagSection("主题标签", meta.topicTags, isDark)
        }
        if (meta.referencedLinks.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Column {
                Text(
                    text = "提取到的链接",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isDark) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(6.dp))
                meta.referencedLinks.forEach { link ->
                    Text(
                        text = link,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isDark) Color(0xFFBFE3FF) else Color(0xFF1D4ED8),
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
        if (meta.paperCandidates.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Column {
                Text(
                    text = "论文线索",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isDark) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(6.dp))
                meta.paperCandidates.forEach { candidate ->
                    Text(
                        text = "${candidate.kind}: ${candidate.url}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isDark) Color(0xFFBFE3FF) else Color(0xFF1D4ED8),
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun NoteCard(note: String, isDark: Boolean) {
    val cardBg = if (isDark) Color(0xFF1E1E2E) else Color(0xFFF8F9FA)
    val accentColor = Color(0xFF00A86B)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .padding(16.dp)
    ) {
        Text(
            text = "备注",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = accentColor
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = note,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isDark) Color.White else Color.Black
        )
    }
}

private data class InsightDetailUi(
    val body: String,
    val tags: List<String>,
    val imageUri: String?,
    val audioUri: String?,
    val audioDurationSeconds: Int,
    val createdAtLabel: String
)

private fun parseInsightDetail(item: com.example.ai4research.domain.model.ResearchItem): InsightDetailUi {
    val metaJson = item.rawMetaJson
    val metaObject = runCatching {
        if (metaJson.isNullOrBlank()) null else org.json.JSONObject(metaJson)
    }.getOrNull()

    val body = item.contentMarkdown.takeIf { it.isNotBlank() }
        ?: metaObject?.optString("body")?.takeIf { it.isNotBlank() }
        ?: item.summary

    val tags = mutableListOf<String>()
    val insightTags = (item.metaData as? com.example.ai4research.domain.model.ItemMetaData.InsightMeta)?.tags.orEmpty()
    tags += insightTags
    metaObject?.optJSONArray("tags")?.let { array ->
        for (index in 0 until array.length()) {
            val tag = array.optString(index).trim()
            if (tag.isNotBlank()) tags += tag
        }
    }

    val createdAtLabel = runCatching {
        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(item.createdAt)
    }.getOrDefault("")

    return InsightDetailUi(
        body = body,
        tags = tags.distinct(),
        imageUri = item.originUrl?.takeIf { it.isNotBlank() } ?: metaObject?.optString("image_uri")?.takeIf { it?.isNotBlank() == true },
        audioUri = item.audioUrl?.takeIf { it.isNotBlank() } ?: metaObject?.optString("audio_uri")?.takeIf { it?.isNotBlank() == true },
        audioDurationSeconds = metaObject?.optInt("audio_duration") ?: 0,
        createdAtLabel = createdAtLabel
    )
}

@Composable
private fun InsightMediaCard(
    item: com.example.ai4research.domain.model.ResearchItem,
    detail: InsightDetailUi,
    isDark: Boolean,
    onImageClick: () -> Unit
) {
    val cardBg = if (isDark) Color(0xFF1E1E2E) else Color(0xFFF8F9FA)
    val accentColor = Color(0xFF10B981)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .padding(16.dp)
    ) {
        Text(
            text = "灵感内容",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = accentColor
        )

        if (detail.createdAtLabel.isNotBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            InsightMetaRow("创建时间", detail.createdAtLabel, isDark)
        }

        if (detail.tags.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            InsightMetaRow("标签", detail.tags.joinToString(" · "), isDark)
        }

        detail.imageUri?.let {
            Spacer(modifier = Modifier.height(14.dp))
            InsightImagePreviewCard(
                imageUri = it,
                isDark = isDark,
                onClick = onImageClick
            )
        }

        detail.audioUri?.let {
            Spacer(modifier = Modifier.height(14.dp))
            InsightAudioPlayerCard(
                audioUri = it,
                durationSeconds = detail.audioDurationSeconds,
                isDark = isDark
            )
        }
    }
}

@Composable
private fun InsightMetaRow(label: String, value: String, isDark: Boolean) {
    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (isDark) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = if (isDark) Color.White else Color.Black
        )
    }
}

@Composable
private fun InsightImagePreviewCard(
    imageUri: String,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var bitmap by remember(imageUri) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(imageUri) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(android.net.Uri.parse(imageUri)).use { input ->
                    android.graphics.BitmapFactory.decodeStream(input)?.asImageBitmap()
                }
            }.getOrNull()
        }
    }

    Column {
        Text(
            text = "图片",
            style = MaterialTheme.typography.labelMedium,
            color = if (isDark) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.04f))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!,
                    contentDescription = "灵感图片",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    text = "点击查看图片",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isDark) Color.White.copy(alpha = 0.7f) else Color.Black.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun InsightAudioPlayerCard(
    audioUri: String,
    durationSeconds: Int,
    isDark: Boolean
) {
    val context = LocalContext.current
    var mediaPlayer by remember(audioUri) { mutableStateOf<android.media.MediaPlayer?>(null) }
    var isPlaying by remember(audioUri) { mutableStateOf(false) }
    var currentPosition by remember(audioUri) { mutableStateOf(0) }
    var totalDuration by remember(audioUri) { mutableStateOf(durationSeconds * 1000) }

    DisposableEffect(audioUri) {
        val player = runCatching {
            android.media.MediaPlayer().apply {
                setDataSource(context, android.net.Uri.parse(audioUri))
                prepare()
            }
        }.getOrNull()
        mediaPlayer = player
        totalDuration = player?.duration?.takeIf { it > 0 } ?: (durationSeconds * 1000)
        player?.setOnCompletionListener {
            isPlaying = false
            currentPosition = totalDuration
        }

        onDispose {
            player?.release()
            mediaPlayer = null
        }
    }

    LaunchedEffect(isPlaying, mediaPlayer) {
        while (isPlaying && mediaPlayer != null) {
            currentPosition = mediaPlayer?.currentPosition ?: currentPosition
            delay(300)
        }
    }

    Column {
        Text(
            text = "语音",
            style = MaterialTheme.typography.labelMedium,
            color = if (isDark) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(if (isDark) Color(0x33FF8A00) else Color(0x14FF8A00))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    mediaPlayer?.let { player ->
                        if (player.isPlaying) {
                            player.pause()
                            isPlaying = false
                            currentPosition = player.currentPosition
                        } else {
                            player.start()
                            isPlaying = true
                        }
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFF8A00))
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "暂停" else "播放",
                    tint = Color.White
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "原始语音",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = if (isDark) Color.White else Color.Black
                )
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = {
                        if (totalDuration <= 0) 0f else currentPosition.toFloat() / totalDuration.toFloat()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFFFF8A00),
                    trackColor = if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${formatDuration(currentPosition / 1000)} / ${formatDuration((if (totalDuration > 0) totalDuration else durationSeconds * 1000) / 1000)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDark) Color.White.copy(alpha = 0.7f) else Color.Black.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun FullscreenInsightImage(
    imageUri: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var bitmap by remember(imageUri) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(imageUri) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(android.net.Uri.parse(imageUri)).use { input ->
                    android.graphics.BitmapFactory.decodeStream(input)?.asImageBitmap()
                }
            }.getOrNull()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f))
            .clickable(onClick = onDismiss)
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!,
                contentDescription = "灵感图片预览",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.4f), CircleShape)
        ) {
            Icon(Icons.Default.Close, contentDescription = "关闭预览", tint = Color.White)
        }
    }
}

private fun formatDuration(seconds: Int): String {
    val minutes = seconds / 60
    val remain = seconds % 60
    return if (minutes > 0) "%d:%02d".format(minutes, remain) else "0:%02d".format(remain)
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

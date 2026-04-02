package com.example.ai4research.ui.detail

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import com.example.ai4research.domain.model.ResearchItem
import com.example.ai4research.domain.model.StructuredReadingCard
import dev.jeziellago.compose.markdowntext.MarkdownText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    itemId: String,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToProject: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    val viewModel: DetailViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var pendingExportContent by remember { mutableStateOf<String?>(null) }
    var pendingExportFileName by remember { mutableStateOf("comparison.md") }
    val summaryPrefs = remember {
        context.getSharedPreferences("paper_summary_prefs", android.content.Context.MODE_PRIVATE)
    }

    val markdownExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri ->
        val content = pendingExportContent
        if (uri == null || content == null) return@rememberLauncherForActivityResult

        val result = writeMarkdownToUri(context, uri, content)
        if (result.isSuccess) {
            Toast.makeText(context, "Markdown 已导出", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(
                context,
                "导出失败：${result.exceptionOrNull()?.message ?: "未知错误"}",
                Toast.LENGTH_SHORT
            ).show()
        }
        pendingExportContent = null
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
    val currentReadingCard = remember(item) { item?.let(::extractStructuredReadingCard) }
    val insightMeta = remember(item) { item?.let(::parseInsightDetail) }
    val canRetryOcr = remember(item) { item?.let(::canRetryOcrForItem) == true }
    
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
    var aiPrompt by rememberSaveable(itemId) { mutableStateOf("") }
    var selectedInsightLinkIds by remember(itemId) { mutableStateOf(setOf<String>()) }
    var selectedComparisonTargetId by remember(itemId) { mutableStateOf<String?>(null) }
    
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
    var editResearchQuestion by remember(currentReadingCard) { mutableStateOf(currentReadingCard?.researchQuestion ?: "") }
    var editMethod by remember(currentReadingCard) { mutableStateOf(currentReadingCard?.method ?: "") }
    var editDataset by remember(currentReadingCard) { mutableStateOf(currentReadingCard?.dataset ?: "") }
    var editFindings by remember(currentReadingCard) { mutableStateOf(currentReadingCard?.findings ?: "") }
    var editLimitations by remember(currentReadingCard) { mutableStateOf(currentReadingCard?.limitations ?: "") }
    var editReusePoints by remember(currentReadingCard) { mutableStateOf(currentReadingCard?.reusePoints ?: "") }
    var editMyNotes by remember(currentReadingCard) { mutableStateOf(currentReadingCard?.myNotes ?: "") }

    LaunchedEffect(uiState.generatedReadingCard) {
        uiState.generatedReadingCard?.let { card ->
            editResearchQuestion = card.researchQuestion.orEmpty()
            editMethod = card.method.orEmpty()
            editDataset = card.dataset.orEmpty()
            editFindings = card.findings.orEmpty()
            editLimitations = card.limitations.orEmpty()
            editReusePoints = card.reusePoints.orEmpty()
            editMyNotes = card.myNotes.orEmpty()
            isEditing = true
            viewModel.consumeGeneratedReadingCard()
        }
    }

    LaunchedEffect(uiState.isInsightLinkEditorVisible, uiState.connections) {
        if (uiState.isInsightLinkEditorVisible) {
            selectedInsightLinkIds = uiState.connections.map { it.item.id }.toSet()
        }
    }

    LaunchedEffect(uiState.isComparisonDialogVisible, uiState.availableComparisonTargets) {
        if (uiState.isComparisonDialogVisible) {
            val currentSelectionStillValid = uiState.availableComparisonTargets.any { it.id == selectedComparisonTargetId }
            if (!currentSelectionStillValid) {
                selectedComparisonTargetId = uiState.availableComparisonTargets.firstOrNull()?.id
            }
        }
    }
    
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
                                ItemType.PAPER, ItemType.ARTICLE -> buildReadingCardMetaJson(
                                    researchQuestion = editResearchQuestion,
                                    method = editMethod,
                                    dataset = editDataset,
                                    findings = editFindings,
                                    limitations = editLimitations,
                                    reusePoints = editReusePoints,
                                    myNotes = editMyNotes
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
                            if (type == ItemType.PAPER || type == ItemType.ARTICLE) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (uiState.isGeneratingReadingCard) {
                                                "AI 正在生成阅读卡..."
                                            } else {
                                                "生成阅读卡"
                                            }
                                        )
                                    },
                                    onClick = {
                                        showMoreMenu = false
                                        viewModel.generateReadingCardDraft()
                                    },
                                    enabled = !uiState.isGeneratingReadingCard
                                )
                                DropdownMenuItem(
                                    text = { Text("对比文献") },
                                    onClick = {
                                        showMoreMenu = false
                                        viewModel.openComparisonDialog()
                                    },
                                    enabled = uiState.availableComparisonTargets.isNotEmpty()
                                )
                            }
                            if (canRetryOcr) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (uiState.isRetryingOcr) {
                                                "重新解析 OCR 中..."
                                            } else {
                                                "重新解析 OCR"
                                            }
                                        )
                                    },
                                    onClick = {
                                        showMoreMenu = false
                                        viewModel.retryOcr()
                                    },
                                    enabled = !uiState.isRetryingOcr
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
                    IconButton(onClick = { viewModel.openAiAssistant() }) {
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

            if (item?.projectId != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier.clickable {
                        onNavigateToProject(item.projectId)
                    }
                ) {
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF06B6D4).copy(alpha = 0.10f))
                            .border(1.dp, Color(0xFF06B6D4).copy(alpha = 0.22f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = "查看项目总览",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = Color(0xFF06B6D4)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "进入该项目的最近新增、重点论文和灵感汇总",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
                        )
                    }
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
            if (!isEditing && (type == ItemType.PAPER || type == ItemType.ARTICLE)) {
                StructuredReadingCardView(
                    card = StructuredReadingCard(
                        researchQuestion = editResearchQuestion,
                        method = editMethod,
                        dataset = editDataset,
                        findings = editFindings,
                        limitations = editLimitations,
                        reusePoints = editReusePoints,
                        myNotes = editMyNotes
                    ),
                    isDark = isDarkTheme,
                    isLoading = uiState.isGeneratingReadingCard
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
            if (!isEditing && type != ItemType.INSIGHT && uiState.connections.isNotEmpty()) {
                RelatedItemsCard(
                    connections = uiState.connections,
                    isDark = isDarkTheme,
                    onOpenItem = onNavigateToDetail
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
            if (!isEditing && type == ItemType.INSIGHT) {
                InsightLinksCard(
                    connections = uiState.connections,
                    recommendations = uiState.insightRecommendations,
                    isLookingUp = uiState.isLookingUpInsightLinks,
                    isDark = isDarkTheme,
                    onOpenItem = onNavigateToDetail,
                    onManageLinks = { viewModel.openInsightLinkEditor() },
                    onLookup = { viewModel.lookupInsightReferences() },
                    onAcceptRecommendations = { viewModel.acceptInsightRecommendations() }
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

                        if (type == ItemType.PAPER || type == ItemType.ARTICLE) {
                            StructuredReadingCardEditor(
                                isDark = isDarkTheme,
                                isGenerating = uiState.isGeneratingReadingCard,
                                researchQuestion = editResearchQuestion,
                                onResearchQuestionChange = { editResearchQuestion = it },
                                method = editMethod,
                                onMethodChange = { editMethod = it },
                                dataset = editDataset,
                                onDatasetChange = { editDataset = it },
                                findings = editFindings,
                                onFindingsChange = { editFindings = it },
                                limitations = editLimitations,
                                onLimitationsChange = { editLimitations = it },
                                reusePoints = editReusePoints,
                                onReusePointsChange = { editReusePoints = it },
                                myNotes = editMyNotes,
                                onMyNotesChange = { editMyNotes = it },
                                onGenerateClick = { viewModel.generateReadingCardDraft() }
                            )

                            Spacer(modifier = Modifier.height(20.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(20.dp))
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
        uiState.errorMessage?.let { errorMessage ->
            AlertDialog(
                onDismissRequest = { viewModel.clearError() },
                title = { Text("操作失败") },
                text = { Text(errorMessage) },
                confirmButton = {
                    TextButton(onClick = { viewModel.clearError() }) {
                        Text("确定")
                    }
                }
            )
        }
        if (uiState.isInsightLinkEditorVisible && item?.type == ItemType.INSIGHT) {
            InsightLinkEditorDialog(
                availableItems = uiState.availableLinkTargets,
                selectedItemIds = selectedInsightLinkIds,
                onToggleItem = { targetId ->
                    selectedInsightLinkIds = selectedInsightLinkIds.toMutableSet().also { current ->
                        if (current.contains(targetId)) {
                            current.remove(targetId)
                        } else {
                            current.add(targetId)
                        }
                    }
                },
                isSaving = uiState.isSavingInsightLinks,
                onDismiss = { viewModel.closeInsightLinkEditor() },
                onConfirm = { viewModel.saveInsightLinks(selectedInsightLinkIds.toList()) }
            )
        }
        if (uiState.isAiSheetVisible) {
            AiAssistantDialog(
                itemTitle = title,
                messages = uiState.chatMessages,
                prompt = aiPrompt,
                onPromptChange = { aiPrompt = it },
                isResponding = uiState.isAiResponding,
                onDismiss = { viewModel.closeAiAssistant() },
                onSend = {
                    viewModel.askAboutCurrentItem(aiPrompt)
                    aiPrompt = ""
                }
            )
        }
        if (uiState.isComparisonDialogVisible && (type == ItemType.PAPER || type == ItemType.ARTICLE)) {
            LiteratureComparisonDialog(
                sourceTitle = title,
                candidates = uiState.availableComparisonTargets,
                selectedTargetId = selectedComparisonTargetId,
                onSelectTarget = { selectedComparisonTargetId = it },
                comparisonResult = uiState.comparisonResult,
                isGenerating = uiState.isGeneratingComparison,
                onDismiss = { viewModel.closeComparisonDialog() },
                onCompare = {
                    selectedComparisonTargetId?.let(viewModel::compareWithItem)
                },
                onExport = {
                    val comparisonResult = uiState.comparisonResult
                    val sourceItem = uiState.item
                    if (comparisonResult != null && sourceItem != null) {
                        pendingExportContent = buildComparisonMarkdown(sourceItem, comparisonResult)
                        pendingExportFileName = buildComparisonFileName(
                            sourceTitle = sourceItem.title,
                            targetTitle = comparisonResult.targetTitle
                        )
                        markdownExportLauncher.launch(pendingExportFileName)
                    }
                }
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

private fun extractStructuredReadingCard(
    item: com.example.ai4research.domain.model.ResearchItem
): StructuredReadingCard? {
    return when (val meta = item.metaData) {
        is com.example.ai4research.domain.model.ItemMetaData.PaperMeta -> meta.readingCard
        is com.example.ai4research.domain.model.ItemMetaData.ArticleMeta -> meta.readingCard
        else -> null
    }
}

private fun buildReadingCardMetaJson(
    researchQuestion: String,
    method: String,
    dataset: String,
    findings: String,
    limitations: String,
    reusePoints: String,
    myNotes: String
): String {
    val card = mapOf(
        "research_question" to researchQuestion,
        "method" to method,
        "dataset" to dataset,
        "findings" to findings,
        "limitations" to limitations,
        "reuse_points" to reusePoints,
        "my_notes" to myNotes
    )
    return org.json.JSONObject(mapOf("reading_card" to card)).toString()
}

@Composable
private fun InsightLinksCard(
    connections: List<com.example.ai4research.domain.model.ItemConnection>,
    recommendations: List<InsightLookupRecommendation>,
    isLookingUp: Boolean,
    isDark: Boolean,
    onOpenItem: (String) -> Unit,
    onManageLinks: () -> Unit,
    onLookup: () -> Unit,
    onAcceptRecommendations: () -> Unit
) {
    val cardBg = if (isDark) Color(0xFF1E1E2E) else Color(0xFFF8F9FA)
    val accentColor = Color(0xFF14B8A6)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "关联条目",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = accentColor
            )
            Text(
                text = "管理关联",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = accentColor,
                modifier = Modifier.clickable(onClick = onManageLinks)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (isLookingUp) "反查中..." else "反查相关资料",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = accentColor,
                modifier = Modifier.clickable(enabled = !isLookingUp, onClick = onLookup)
            )
            if (recommendations.isNotEmpty()) {
                Text(
                    text = "采纳推荐",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = Color(0xFF8B5CF6),
                    modifier = Modifier.clickable(onClick = onAcceptRecommendations)
                )
            }
        }

        if (connections.isEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "这条灵感还没有关联到论文或资料，可以手动补充。",
                style = MaterialTheme.typography.bodyMedium,
                color = if (isDark) Color.White.copy(alpha = 0.7f) else Color.Black.copy(alpha = 0.65f)
            )
        } else {
            Spacer(modifier = Modifier.height(10.dp))
            connections.take(5).forEachIndexed { index, connection ->
                RelatedItemRow(
                    connection = connection,
                    isDark = isDark,
                    onOpenItem = onOpenItem
                )
                if (index != connections.take(5).lastIndex) {
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f))
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }

        if (recommendations.isNotEmpty()) {
            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f))
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "AI 反查推荐",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = Color(0xFF8B5CF6)
            )
            Spacer(modifier = Modifier.height(10.dp))
            recommendations.forEachIndexed { index, recommendation ->
                InsightRecommendationRow(
                    recommendation = recommendation,
                    isDark = isDark,
                    onOpenItem = onOpenItem
                )
                if (index != recommendations.lastIndex) {
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f))
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun InsightRecommendationRow(
    recommendation: InsightLookupRecommendation,
    isDark: Boolean,
    onOpenItem: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenItem(recommendation.item.id) }
    ) {
        Text(
            text = recommendation.item.title,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            color = if (isDark) Color.White else Color.Black
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = recommendation.reason,
            style = MaterialTheme.typography.bodySmall,
            color = if (isDark) Color.White.copy(alpha = 0.72f) else Color.Black.copy(alpha = 0.68f)
        )
        recommendation.suggestedQuestion?.takeIf { it.isNotBlank() }?.let { question ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "可追问：$question",
                style = MaterialTheme.typography.labelMedium,
                color = if (isDark) Color(0xFFC4B5FD) else Color(0xFF7C3AED)
            )
        }
    }
}

@Composable
private fun RelatedItemsCard(
    connections: List<com.example.ai4research.domain.model.ItemConnection>,
    isDark: Boolean,
    onOpenItem: (String) -> Unit
) {
    val cardBg = if (isDark) Color(0xFF1E1E2E) else Color(0xFFF8F9FA)
    val accentColor = Color(0xFF06B6D4)
    val groupedConnections = connections.groupBy { connection ->
        relationGroupLabel(connection.relation.relationType)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .padding(16.dp)
    ) {
        Text(
            text = "关联条目",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = accentColor
        )
        groupedConnections.forEach { (groupLabel, groupedItems) ->
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = groupLabel,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = if (isDark) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            groupedItems.forEachIndexed { index, connection ->
                RelatedItemRow(
                    connection = connection,
                    isDark = isDark,
                    onOpenItem = onOpenItem
                )
                if (index != groupedItems.lastIndex) {
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f))
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun RelatedItemRow(
    connection: com.example.ai4research.domain.model.ItemConnection,
    isDark: Boolean,
    onOpenItem: (String) -> Unit
) {
    val relatedItem = connection.item

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenItem(relatedItem.id) }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = getItemTypeName(relatedItem.type),
                style = MaterialTheme.typography.labelMedium,
                color = if (isDark) Color.White.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.55f)
            )
            Text(
                text = relationTypeLabel(connection.relation.relationType),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Color(0xFF06B6D4)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = relatedItem.title,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            color = if (isDark) Color.White else Color.Black
        )
        if (relatedItem.summary.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = relatedItem.summary,
                style = MaterialTheme.typography.bodySmall,
                color = if (isDark) Color.White.copy(alpha = 0.7f) else Color.Black.copy(alpha = 0.7f),
                maxLines = 2
            )
        }
    }
}

private fun relationTypeLabel(type: com.example.ai4research.domain.model.RelationType): String {
    return when (type) {
        com.example.ai4research.domain.model.RelationType.DUPLICATE_OF -> "重复条目"
        com.example.ai4research.domain.model.RelationType.ARTICLE_MENTIONS_PAPER -> "文章提到的论文"
        com.example.ai4research.domain.model.RelationType.ARTICLE_RELATED_PAPER -> "相关论文"
        com.example.ai4research.domain.model.RelationType.INSIGHT_REFERENCES_ITEM -> "灵感关联"
    }
}

private fun relationGroupLabel(type: com.example.ai4research.domain.model.RelationType): String {
    return when (type) {
        com.example.ai4research.domain.model.RelationType.DUPLICATE_OF -> "重复来源"
        com.example.ai4research.domain.model.RelationType.ARTICLE_MENTIONS_PAPER -> "引用与提及"
        com.example.ai4research.domain.model.RelationType.ARTICLE_RELATED_PAPER -> "相关文献"
        com.example.ai4research.domain.model.RelationType.INSIGHT_REFERENCES_ITEM -> "灵感关联"
    }
}

@Composable
private fun StructuredReadingCardView(
    card: StructuredReadingCard,
    isDark: Boolean,
    isLoading: Boolean
) {
    val cardBg = if (isDark) Color(0xFF1E1E2E) else Color(0xFFF8F9FA)
    val accentColor = Color(0xFF2563EB)
    val sections = listOf(
        "研究问题" to card.researchQuestion,
        "方法" to card.method,
        "数据集" to card.dataset,
        "核心发现" to card.findings,
        "局限性" to card.limitations,
        "可复用点" to card.reusePoints,
        "我的笔记" to card.myNotes
    ).filter { !it.second.isNullOrBlank() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "阅读卡",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = accentColor
            )
            if (isLoading) {
                Text(
                    text = "AI 正在生成...",
                    style = MaterialTheme.typography.bodySmall,
                    color = accentColor
                )
            }
        }

        if (sections.isEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = if (isLoading) {
                    "正在根据当前条目生成阅读卡初稿。"
                } else {
                    "还没有阅读卡，可以从右上角菜单生成，或在编辑模式下手动填写。"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (isDark) Color.White.copy(alpha = 0.7f) else Color.Black.copy(alpha = 0.65f)
            )
        } else {
            sections.forEach { (label, value) ->
                Spacer(modifier = Modifier.height(10.dp))
                PaperSummarySection(label, value.orEmpty(), isDark)
            }
        }
    }
}

@Composable
private fun StructuredReadingCardEditor(
    isDark: Boolean,
    isGenerating: Boolean,
    researchQuestion: String,
    onResearchQuestionChange: (String) -> Unit,
    method: String,
    onMethodChange: (String) -> Unit,
    dataset: String,
    onDatasetChange: (String) -> Unit,
    findings: String,
    onFindingsChange: (String) -> Unit,
    limitations: String,
    onLimitationsChange: (String) -> Unit,
    reusePoints: String,
    onReusePointsChange: (String) -> Unit,
    myNotes: String,
    onMyNotesChange: (String) -> Unit,
    onGenerateClick: () -> Unit
) {
    Text(
        text = "阅读卡",
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        color = if (isDark) Color(0xFF9CC2FF) else Color(0xFF2563EB)
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "记录这条资料的研究问题、方法、证据与可复用点。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
    )
    Spacer(modifier = Modifier.height(12.dp))
    TextButton(onClick = onGenerateClick, enabled = !isGenerating) {
        Text(if (isGenerating) "AI 正在生成..." else "用 AI 生成")
    }
    Spacer(modifier = Modifier.height(8.dp))
    ReadingCardField("研究问题", researchQuestion, onResearchQuestionChange)
    Spacer(modifier = Modifier.height(12.dp))
    ReadingCardField("方法", method, onMethodChange)
    Spacer(modifier = Modifier.height(12.dp))
    ReadingCardField("数据集", dataset, onDatasetChange)
    Spacer(modifier = Modifier.height(12.dp))
    ReadingCardField("核心发现", findings, onFindingsChange)
    Spacer(modifier = Modifier.height(12.dp))
    ReadingCardField("局限性", limitations, onLimitationsChange)
    Spacer(modifier = Modifier.height(12.dp))
    ReadingCardField("可复用点", reusePoints, onReusePointsChange)
    Spacer(modifier = Modifier.height(12.dp))
    ReadingCardField("我的笔记", myNotes, onMyNotesChange)
}

@Composable
private fun ReadingCardField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        minLines = 2
    )
}

@Composable
private fun AiAssistantDialog(
    itemTitle: String,
    messages: List<AiChatMessage>,
    prompt: String,
    onPromptChange: (String) -> Unit,
    isResponding: Boolean,
    onDismiss: () -> Unit,
    onSend: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("围绕当前条目提问") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = itemTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (messages.isEmpty()) {
                    Text(
                        text = "可以问研究问题、方法、结论、局限性，或者这条资料能怎样复用。",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    messages.forEach { message ->
                        AiMessageBubble(message = message)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
                if (isResponding) {
                    Text(
                        text = "AI 正在思考...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = prompt,
                    onValueChange = onPromptChange,
                    label = { Text("问题") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onSend,
                enabled = prompt.isNotBlank() && !isResponding
            ) {
                Text("发送")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

@Composable
private fun LiteratureComparisonDialog(
    sourceTitle: String,
    candidates: List<ResearchItem>,
    selectedTargetId: String?,
    onSelectTarget: (String) -> Unit,
    comparisonResult: LiteratureComparisonViewData?,
    isGenerating: Boolean,
    onDismiss: () -> Unit,
    onCompare: () -> Unit,
    onExport: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("双文献对比") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "当前条目：$sourceTitle",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                )
                Spacer(modifier = Modifier.height(12.dp))

                if (candidates.isEmpty()) {
                    Text(
                        text = "当前没有可供对比的论文或资料。",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Text(
                        text = "选择另一条资料",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    candidates.take(8).forEach { candidate ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectTarget(candidate.id) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedTargetId == candidate.id,
                                onClick = { onSelectTarget(candidate.id) }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = candidate.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                                candidate.projectName?.takeIf { it.isNotBlank() }?.let { projectName ->
                                    Text(
                                        text = "项目：$projectName",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }
                }

                if (isGenerating) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "AI 正在生成对比结果...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                comparisonResult?.let { result ->
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "对比对象：${result.targetTitle}",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    ComparisonSection(title = "相同点", items = result.commonPoints)
                    ComparisonSection(title = "差异点", items = result.differences)
                    ComparisonSection(title = "互补点", items = result.complementarities)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "项目适配：${result.projectFit}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "建议切入：${result.recommendation}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = onExport) {
                        Text("导出 Markdown")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onCompare,
                enabled = selectedTargetId != null && !isGenerating && candidates.isNotEmpty()
            ) {
                Text("开始对比")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

@Composable
private fun ComparisonSection(
    title: String,
    items: List<String>
) {
    if (items.isEmpty()) return

    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
    )
    Spacer(modifier = Modifier.height(6.dp))
    items.forEach { item ->
        Text(
            text = "• $item",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
        )
        Spacer(modifier = Modifier.height(4.dp))
    }
}

private fun writeMarkdownToUri(
    context: android.content.Context,
    uri: android.net.Uri,
    content: String
): Result<Unit> {
    return runCatching {
        context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use {
            it.write(content)
        } ?: error("无法打开导出文件")
    }
}

private fun buildComparisonFileName(
    sourceTitle: String,
    targetTitle: String
): String {
    fun sanitize(value: String): String {
        return value.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "item" }
    }

    return "${sanitize(sourceTitle)}_vs_${sanitize(targetTitle)}.md"
}

private fun buildComparisonMarkdown(
    sourceItem: ResearchItem,
    comparison: LiteratureComparisonViewData
): String {
    fun section(title: String, items: List<String>): String {
        if (items.isEmpty()) return ""
        return buildString {
            appendLine("## $title")
            items.forEach { appendLine("- $it") }
            appendLine()
        }
    }

    return buildString {
        appendLine("# 双文献对比")
        appendLine()
        appendLine("- 资料 A：${sourceItem.title}")
        appendLine("- 资料 B：${comparison.targetTitle}")
        appendLine("- 资料 A 项目：${sourceItem.projectName ?: "未归属"}")
        appendLine()
        append(section("相同点", comparison.commonPoints))
        append(section("差异点", comparison.differences))
        append(section("互补点", comparison.complementarities))
        appendLine("## 项目适配")
        appendLine(comparison.projectFit)
        appendLine()
        appendLine("## 建议切入")
        appendLine(comparison.recommendation)
    }
}

@Composable
private fun AiMessageBubble(message: AiChatMessage) {
    val isUser = message.role == AiChatRole.USER
    val background = if (isUser) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .padding(12.dp)
    ) {
        Text(
            text = if (isUser) "我" else "AI",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = message.content,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun InsightLinkEditorDialog(
    availableItems: List<com.example.ai4research.domain.model.ResearchItem>,
    selectedItemIds: Set<String>,
    onToggleItem: (String) -> Unit,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("管理灵感关联") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "选择与这条灵感直接相关的论文、资料或其他条目。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (availableItems.isEmpty()) {
                    Text(
                        text = "当前还没有可关联的条目。",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    availableItems.take(20).forEach { target ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleItem(target.id) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selectedItemIds.contains(target.id),
                                onCheckedChange = { onToggleItem(target.id) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = target.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = getItemTypeName(target.type),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !isSaving) {
                Text(if (isSaving) "保存中..." else "保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text("取消")
            }
        }
    )
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
    val summaryShort = meta.mediumSummary?.takeIf { it.isNotBlank() }
        ?: meta.summaryZh?.takeIf { it.isNotBlank() }
        ?: meta.summaryShort?.takeIf { it.isNotBlank() }
        ?: item.summary.takeIf { it.isNotBlank() }
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
        (meta.mediumSummary?.takeIf { it.isNotBlank() } ?: meta.summaryShort?.takeIf { it.isNotBlank() })?.let {
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
    val categoryName: String?,
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

    val createdAtLabel = runCatching {
        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(item.createdAt)
    }.getOrDefault("")

    return InsightDetailUi(
        body = body,
        categoryName = metaObject?.optString("category_name")?.takeIf { it.isNotBlank() },
        imageUri = item.originUrl?.takeIf { it.isNotBlank() } ?: metaObject?.optString("image_uri")?.takeIf { it?.isNotBlank() == true },
        audioUri = item.audioUrl?.takeIf { it.isNotBlank() } ?: metaObject?.optString("audio_uri")?.takeIf { it?.isNotBlank() == true },
        audioDurationSeconds = metaObject?.optInt("audio_duration") ?: 0,
        createdAtLabel = createdAtLabel
    )
}

private fun canRetryOcrForItem(item: com.example.ai4research.domain.model.ResearchItem): Boolean {
    val metaJson = item.rawMetaJson ?: return false
    val metaObject = runCatching { org.json.JSONObject(metaJson) }.getOrNull() ?: return false

    if (!metaObject.has("local_image_paths")) return false

    val source = metaObject.optString("source")
    val captureMode = metaObject.optString("capture_mode")
    val signalStrength = metaObject.optString("ocr_signal_strength")

    return source == "ocr" ||
        captureMode.isNotBlank() ||
        signalStrength == "weak" ||
        item.status == com.example.ai4research.domain.model.ItemStatus.FAILED
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

        if (!detail.categoryName.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            InsightMetaRow("归位词条", detail.categoryName, isDark)
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

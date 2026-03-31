package com.example.ai4research.ui.main

import android.app.AlertDialog
import android.widget.Toast
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog as ComposeAlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.ai4research.core.util.WebViewCache
import com.example.ai4research.domain.model.ItemType
import com.example.ai4research.domain.model.Project
import com.google.gson.Gson
import com.example.ai4research.service.FloatingWindowManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp

/**
 * Main Screen Refactored with WebView
 * Loads main_ui.html which contains the React+Tailwind UI
 */
@Composable
fun MainScreen(
    onLogout: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToProjectOverview: (String) -> Unit,
    onNavigateToVoiceRecording: () -> Unit = {},
    webViewCache: WebViewCache,
    viewModel: MainViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    
    // 获取 MainActivity 的 targetTab
    val activity = context as? com.example.ai4research.MainActivity

    // Track loading state
    var isLoading by remember { mutableStateOf(!webViewCache.isMainLoaded()) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isPageReady by remember { mutableStateOf(false) }  // 跟踪页面是否已加载完成
    val webView = remember { webViewCache.acquireMain(context) }
    
    val papers by viewModel.papers.collectAsState()
    val articles by viewModel.articles.collectAsState()
    val competitions by viewModel.competitions.collectAsState()
    val insights by viewModel.insights.collectAsState()
    val projectsState by viewModel.projects.collectAsState()
    val currentProjectId by viewModel.currentProjectId.collectAsState()
    
    val coroutineScope = rememberCoroutineScope()
    val gson = remember { Gson() }
    val floatingWindowManager = viewModel.floatingWindowManager
    var pendingScanUris by remember { mutableStateOf<List<android.net.Uri>>(emptyList()) }
    var showScanImportDialog by remember { mutableStateOf(false) }
    var selectedScanType by remember { mutableStateOf(ItemType.ARTICLE) }
    var selectedScanProjectId by remember { mutableStateOf<String?>(null) }

    fun emitWebEvent(eventName: String, payload: Map<String, Any?>) {
        if (!isPageReady) return
        val payloadJson = gson.toJson(payload)
        webViewRef?.post {
            webViewRef?.evaluateJavascript(
                "window.dispatchEvent(new CustomEvent('$eventName', { detail: $payloadJson }));",
                null
            )
        }
    }

    if (showScanImportDialog) {
        ScanImportConfigDialog(
            imageCount = pendingScanUris.size,
            projects = projectsState,
            selectedType = selectedScanType,
            selectedProjectId = selectedScanProjectId,
            onTypeSelected = { selectedScanType = it },
            onProjectSelected = { selectedScanProjectId = it },
            onDismiss = {
                showScanImportDialog = false
                pendingScanUris = emptyList()
            },
            onConfirm = {
                val urisToImport = pendingScanUris
                val selectedTypeForImport = selectedScanType
                val selectedProjectIdForImport = selectedScanProjectId
                val projectName = projectsState.firstOrNull { it.id == selectedProjectIdForImport }?.name
                showScanImportDialog = false
                pendingScanUris = emptyList()
                viewModel.importScannedImages(
                    imageUris = urisToImport,
                    selectedType = selectedTypeForImport,
                    projectId = selectedProjectIdForImport,
                    projectName = projectName,
                    onQueued = { result ->
                        val message = result.fold(
                            onSuccess = { "已加入${getItemTypeLabel(selectedTypeForImport)}，正在后台整理" },
                            onFailure = { error -> "加入失败：${error.message ?: "请重试"}" }
                        )
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        if (result.isSuccess && isPageReady) {
                            webViewRef?.evaluateJavascript(
                                "if(window.setActiveTab) window.setActiveTab('${itemTypeToTabId(selectedTypeForImport)}');",
                                null
                            )
                        }
                    },
                    onFinished = { result ->
                        result.onFailure { error ->
                            Toast.makeText(context, "扫描解析失败：${error.message ?: "请重试"}", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        )
    }
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult

        uris.forEach { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }

        pendingScanUris = uris
        selectedScanType = ItemType.ARTICLE
        selectedScanProjectId = currentProjectId
        showScanImportDialog = true
    }
    val insightImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult

        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }

        emitWebEvent(
            "insight-image-picked",
            mapOf(
                "uri" to uri.toString(),
                "name" to queryDisplayName(context, uri)
            )
        )
    }
    val insightAudioRecorderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult

        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }

        emitWebEvent(
            "insight-audio-recorded",
            mapOf(
                "uri" to uri.toString(),
                "name" to queryDisplayName(context, uri),
                "durationSeconds" to readAudioDurationSeconds(context, uri)
            )
        )
    }
    
    // 用于记录待处理的 targetTab
    var pendingTargetTab by remember { mutableStateOf<String?>(null) }
    // 用于记录是否正在处理 tab 切换，避免数据更新时重复推送
    var isHandlingTabSwitch by remember { mutableStateOf(false) }
    
    // 监听 targetTab 变化，记录待处理的 tab
    LaunchedEffect(activity?.targetTab) {
        activity?.targetTab?.let { tab ->
            android.util.Log.d("MainScreen", "收到 targetTab: $tab, isPageReady=$isPageReady")
            pendingTargetTab = tab
        }
    }
    
    // 当页面准备好且有待处理的 tab 时，执行跳转逻辑
    LaunchedEffect(isPageReady, pendingTargetTab) {
        if (isPageReady && webViewRef != null && pendingTargetTab != null) {
            val tab = pendingTargetTab!!
            android.util.Log.d("MainScreen", "执行Tab切换: $tab")
            isHandlingTabSwitch = true
            
            try {
                // 先使用本地 Room 中刚写入的数据完成回跳展示，避免新条目被立即远端刷新覆盖。
                viewModel.applyFilter(viewModel.currentFilter.value, viewModel.currentProjectId.value)
                
                // 等待 Flow 收到最新本地数据（最多等待3秒）
                var retryCount = 0
                while (viewModel.isLoading.value && retryCount < 30) {
                    kotlinx.coroutines.delay(100)
                    retryCount++
                }
                
                // 额外等待一下确保 Room Flow 已 emit 新数据
                kotlinx.coroutines.delay(300)
                
                // 推送最新数据
                val papersJson = viewModel.getPapersJson()
                val articlesJson = viewModel.getArticlesJson()
                val competitionsJson = viewModel.getCompetitionsJson()
                val insightsJson = viewModel.getInsightsJson()
                val projectsJson = viewModel.getProjectsJson()
                val voiceItemsJson = viewModel.getVoiceItemsJson()
                android.util.Log.d("MainScreen", "推送数据: papers=${viewModel.papers.value.size}, insights=${viewModel.insights.value.size}, voice=${viewModel.voiceItems.value.size}")
                webViewRef?.evaluateJavascript("if(window.receivePapers) window.receivePapers($papersJson)", null)
                webViewRef?.evaluateJavascript("if(window.receiveArticles) window.receiveArticles($articlesJson)", null)
                webViewRef?.evaluateJavascript("if(window.receiveCompetitions) window.receiveCompetitions($competitionsJson)", null)
                webViewRef?.evaluateJavascript("if(window.receiveInsights) window.receiveInsights($insightsJson)", null)
                webViewRef?.evaluateJavascript("if(window.receiveProjects) window.receiveProjects($projectsJson)", null)
                webViewRef?.evaluateJavascript("if(window.receiveVoiceItems) window.receiveVoiceItems($voiceItemsJson)", null)
                
                // 4. 切换到目标Tab
                kotlinx.coroutines.delay(100)
                webViewRef?.evaluateJavascript("if(window.setActiveTab) window.setActiveTab('$tab');", null)
                android.util.Log.d("MainScreen", "Tab切换完成: $tab")
            } finally {
                // 清除待处理的 tab
                pendingTargetTab = null
                activity?.clearTargetTab()
                isHandlingTabSwitch = false
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewCache.releaseMain(webView)
        }
    }

    LaunchedEffect(Unit) {
        if (webViewCache.isMainLoaded()) {
            isLoading = false
        }
    }

    // Interface for Main UI - moved outside composable for better structure
    val mainAppInterface = remember(context, floatingWindowManager, coroutineScope) {
        MainAppInterface(
            context = context,
            floatingWindowManager = floatingWindowManager,
            coroutineScope = coroutineScope,
            viewModel = viewModel,
            onLogout = onLogout,
            onNavigateToDetail = onNavigateToDetail,
            onNavigateToProjectOverview = onNavigateToProjectOverview,
            onNavigateToVoiceRecording = onNavigateToVoiceRecording,
            onOpenLinkCapture = { floatingWindowManager.openQuickLinkCapture() },
            onStartScanCapture = { imagePickerLauncher.launch(arrayOf("image/*")) },
            onPickInsightImage = { insightImagePickerLauncher.launch(arrayOf("image/*")) },
            onRecordInsightAudio = {
                val intent = android.content.Intent(android.provider.MediaStore.Audio.Media.RECORD_SOUND_ACTION)
                insightAudioRecorderLauncher.launch(intent)
            },
            emitJsEvent = { eventName, payloadJson ->
                if (isPageReady) {
                    webViewRef?.post {
                        webViewRef?.evaluateJavascript(
                            "window.dispatchEvent(new CustomEvent('$eventName', { detail: $payloadJson }));",
                            null
                        )
                    }
                }
            }
        )
    }
    
    // Sync data to WebView when it changes - 只在页面准备好且不在处理tab切换时才推送
    LaunchedEffect(papers, webViewRef, isPageReady, isHandlingTabSwitch) {
        if (isPageReady && !isHandlingTabSwitch) {
            webViewRef?.let { wv ->
                val jsonStr = viewModel.getPapersJson()
                android.util.Log.d("MainScreen", "Syncing papers to WebView: ${papers.size} items")
                wv.evaluateJavascript("if(window.receivePapers) window.receivePapers($jsonStr)", null)
            }
        }
    }

    LaunchedEffect(articles, webViewRef, isPageReady, isHandlingTabSwitch) {
        if (isPageReady && !isHandlingTabSwitch) {
            webViewRef?.let { wv ->
                val jsonStr = viewModel.getArticlesJson()
                android.util.Log.d("MainScreen", "Syncing articles to WebView: ${articles.size} items")
                wv.evaluateJavascript("if(window.receiveArticles) window.receiveArticles($jsonStr)", null)
            }
        }
    }

    LaunchedEffect(competitions, webViewRef, isPageReady, isHandlingTabSwitch) {
        if (isPageReady && !isHandlingTabSwitch) {
            webViewRef?.let { wv ->
                val jsonStr = viewModel.getCompetitionsJson()
                android.util.Log.d("MainScreen", "Syncing competitions to WebView: ${competitions.size} items")
                wv.evaluateJavascript("if(window.receiveCompetitions) window.receiveCompetitions($jsonStr)", null)
            }
        }
    }
    
    LaunchedEffect(insights, webViewRef, isPageReady, isHandlingTabSwitch) {
        if (isPageReady && !isHandlingTabSwitch) {
            webViewRef?.let { wv ->
                val jsonStr = viewModel.getInsightsJson()
                android.util.Log.d("MainScreen", "Syncing insights to WebView: ${insights.size} items")
                wv.evaluateJavascript("if(window.receiveInsights) window.receiveInsights($jsonStr)", null)
            }
        }
    }
    
    LaunchedEffect(projectsState, webViewRef, isPageReady, isHandlingTabSwitch) {
        if (isPageReady && !isHandlingTabSwitch) {
            webViewRef?.let { wv ->
                val jsonStr = viewModel.getProjectsJson()
                android.util.Log.d("MainScreen", "Syncing projects to WebView: ${projectsState.size} items")
                wv.evaluateJavascript("if(window.receiveProjects) window.receiveProjects($jsonStr)", null)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                webView.apply {
                    // 先设置 webViewRef，确保后续回调能访问到
                    webViewRef = this
                    
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    
                    // Set background to match theme to avoid black flash
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        cacheMode = WebSettings.LOAD_DEFAULT
                        // Enable hardware acceleration and smooth scrolling
                    }
                    
                    // Allow debugging in development
                    WebView.setWebContentsDebuggingEnabled(true)
                    
                    webViewClient = object : WebViewClient() {
                        override fun onPageCommitVisible(view: WebView?, url: String?) {
                            android.util.Log.d("MainScreen", "onPageCommitVisible called")
                            if (isLoading) {
                                isLoading = false
                            }
                            webViewCache.markMainLoaded()
                            webViewCache.markMainInterfaceInjected()
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            android.util.Log.d("MainScreen", "onPageFinished called, url=$url")
                            if (isLoading) {
                                isLoading = false
                            }
                            webViewCache.markMainLoaded()
                            webViewCache.markMainInterfaceInjected()
                            
                            // 延迟一小段时间确保 React 组件已初始化
                            view?.postDelayed({
                                isPageReady = true
                                
                                // 直接推送当前数据到 WebView
                                val papersJson = viewModel.getPapersJson()
                                val articlesJson = viewModel.getArticlesJson()
                                val competitionsJson = viewModel.getCompetitionsJson()
                                val insightsJson = viewModel.getInsightsJson()
                                val projectsJson = viewModel.getProjectsJson()
                                val voiceItemsJson = viewModel.getVoiceItemsJson()
                                android.util.Log.d("MainScreen", "Pushing initial data to WebView: papers=${viewModel.papers.value.size}, articles=${viewModel.articles.value.size}, competitions=${viewModel.competitions.value.size}, insights=${viewModel.insights.value.size}, voice=${viewModel.voiceItems.value.size}")
                                view.evaluateJavascript("if(window.receivePapers) window.receivePapers($papersJson)", null)
                                view.evaluateJavascript("if(window.receiveArticles) window.receiveArticles($articlesJson)", null)
                                view.evaluateJavascript("if(window.receiveCompetitions) window.receiveCompetitions($competitionsJson)", null)
                                view.evaluateJavascript("if(window.receiveInsights) window.receiveInsights($insightsJson)", null)
                                view.evaluateJavascript("if(window.receiveProjects) window.receiveProjects($projectsJson)", null)
                                view.evaluateJavascript("if(window.receiveVoiceItems) window.receiveVoiceItems($voiceItemsJson)", null)
                                
                                // 同时触发刷新以获取最新数据
                                viewModel.fetchData()
                                
                                // 1秒后再次推送数据，确保数据刷新后同步到 WebView
                                view.postDelayed({
                                    val latestPapersJson = viewModel.getPapersJson()
                                    val latestArticlesJson = viewModel.getArticlesJson()
                                    val latestCompetitionsJson = viewModel.getCompetitionsJson()
                                    val latestInsightsJson = viewModel.getInsightsJson()
                                    val latestProjectsJson = viewModel.getProjectsJson()
                                    val latestVoiceItemsJson = viewModel.getVoiceItemsJson()
                                    android.util.Log.d("MainScreen", "Re-pushing data after refresh: papers=${viewModel.papers.value.size}, articles=${viewModel.articles.value.size}, voice=${viewModel.voiceItems.value.size}")
                                    view.evaluateJavascript("if(window.receivePapers) window.receivePapers($latestPapersJson)", null)
                                    view.evaluateJavascript("if(window.receiveArticles) window.receiveArticles($latestArticlesJson)", null)
                                    view.evaluateJavascript("if(window.receiveCompetitions) window.receiveCompetitions($latestCompetitionsJson)", null)
                                    view.evaluateJavascript("if(window.receiveInsights) window.receiveInsights($latestInsightsJson)", null)
                                    view.evaluateJavascript("if(window.receiveProjects) window.receiveProjects($latestProjectsJson)", null)
                                    view.evaluateJavascript("if(window.receiveVoiceItems) window.receiveVoiceItems($latestVoiceItemsJson)", null)
                                }, 1000)
                            }, 500)
                        }
                    }
                    
                    // 检查接口是否已注入，避免重复加载页面
                    val needsReload = !webViewCache.isMainInterfaceInjected()
                    
                    removeJavascriptInterface("AndroidInterface")
                    addJavascriptInterface(mainAppInterface, "AndroidInterface")
                    
                    if (needsReload) {
                        // 首次加载或接口未注入，需要加载页面
                        android.util.Log.d("MainScreen", "Loading page (interface not injected)")
                        loadUrl("file:///android_asset/main_ui.html")
                    } else {
                        // 页面已加载且接口已注入，直接使用缓存，但需要推送数据
                        android.util.Log.d("MainScreen", "Using cached page, pushing data")
                        isLoading = false
                        isPageReady = true
                        
                        // 立即推送当前数据
                        val papersJson = viewModel.getPapersJson()
                        val articlesJson = viewModel.getArticlesJson()
                        val competitionsJson = viewModel.getCompetitionsJson()
                        val insightsJson = viewModel.getInsightsJson()
                        val projectsJson = viewModel.getProjectsJson()
                        val voiceItemsJson = viewModel.getVoiceItemsJson()
                        evaluateJavascript("if(window.receivePapers) window.receivePapers($papersJson)", null)
                        evaluateJavascript("if(window.receiveArticles) window.receiveArticles($articlesJson)", null)
                        evaluateJavascript("if(window.receiveCompetitions) window.receiveCompetitions($competitionsJson)", null)
                        evaluateJavascript("if(window.receiveInsights) window.receiveInsights($insightsJson)", null)
                        evaluateJavascript("if(window.receiveProjects) window.receiveProjects($projectsJson)", null)
                        evaluateJavascript("if(window.receiveVoiceItems) window.receiveVoiceItems($voiceItemsJson)", null)
                    }
                }
            }
        )
        
        // Show loading indicator while WebView is loading
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * JavaScript Interface for WebView communication
 */
class MainAppInterface(
    private val context: android.content.Context,
    private val floatingWindowManager: FloatingWindowManager,
    private val coroutineScope: CoroutineScope,
    private val viewModel: MainViewModel,
    private val onLogout: () -> Unit,
    private val onNavigateToDetail: (String) -> Unit,
    private val onNavigateToProjectOverview: (String) -> Unit,
    private val onNavigateToVoiceRecording: () -> Unit = {},
    private val onOpenLinkCapture: () -> Unit = {},
    private val onStartScanCapture: () -> Unit = {},
    private val onPickInsightImage: () -> Unit = {},
    private val onRecordInsightAudio: () -> Unit = {},
    private val emitJsEvent: (String, String) -> Unit = { _, _ -> }
) {
    private val gson = Gson()
    private val quickCaptureBridge = QuickCaptureBridge(
        postToMainThread = { block ->
            android.os.Handler(android.os.Looper.getMainLooper()).post(block)
        },
        hasOverlayPermission = { floatingWindowManager.hasOverlayPermission() },
        onOpenLinkCapture = onOpenLinkCapture,
        onShowLinkPermissionPrompt = { showLinkOverlayPermissionPrompt() },
        onStartScanCapture = onStartScanCapture,
        onStartVoiceRecording = onNavigateToVoiceRecording
    )

    @JavascriptInterface
    fun logout() {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            onLogout()
        }
    }
    
    @JavascriptInterface
    fun navigateToDetail(itemId: String) {
        android.util.Log.d("MainAppInterface", "navigateToDetail called with itemId: $itemId")
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            android.util.Log.d("MainAppInterface", "Executing navigation to detail: $itemId")
            onNavigateToDetail(itemId)
        }
    }

    @JavascriptInterface
    fun openProjectOverview(projectId: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            if (projectId.isNotBlank()) {
                onNavigateToProjectOverview(projectId)
            }
        }
    }

    @JavascriptInterface
    fun requestData() {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
             viewModel.fetchData()
        }
    }
    
    @JavascriptInterface
    fun search(query: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            viewModel.search(query)
        }
    }
    
    @JavascriptInterface
    fun applyFilter(filterType: String, projectId: String?) {
        android.util.Log.d("MainAppInterface", "applyFilter called: type=$filterType, projectId=$projectId")
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            android.util.Log.d("MainAppInterface", "Executing filter: type=$filterType, projectId=$projectId")
            val filter = when (filterType.lowercase()) {
                "all" -> FilterType.ALL
                "unread" -> FilterType.UNREAD
                "starred" -> FilterType.STARRED
                "project" -> FilterType.PROJECT
                else -> FilterType.ALL
            }
            viewModel.applyFilter(filter, projectId)
        }
    }
    
    @JavascriptInterface
    fun deleteItem(itemId: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            viewModel.deleteItem(itemId)
        }
    }
    
    @JavascriptInterface
    fun getProjects(): String {
        return viewModel.getProjectsJson()
    }

    @JavascriptInterface
    fun getPapers(): String {
        return viewModel.getPapersJson()
    }

    @JavascriptInterface
    fun getArticles(): String {
        return viewModel.getArticlesJson()
    }

    @JavascriptInterface
    fun getCompetitions(): String {
        return viewModel.getCompetitionsJson()
    }

    @JavascriptInterface
    fun getInsights(): String {
        return viewModel.getInsightsJson()
    }

    @JavascriptInterface
    fun getVoiceItems(): String {
        return viewModel.getVoiceItemsJson()
    }

    @JavascriptInterface
    fun getSyncDiagnostics(): String {
        return viewModel.getSyncDiagnosticsJson()
    }
    
    @JavascriptInterface
    fun checkFloatingWindowStatus(): String {
        val hasPermission = android.provider.Settings.canDrawOverlays(context)
        var isEnabled = false
        // 使用 runBlocking 获取当前设置状态，仅供同步 JavaScript 调用
        kotlinx.coroutines.runBlocking {
            isEnabled = floatingWindowManager.isFloatingWindowEnabled.first()
        }
        return """{"hasPermission": $hasPermission, "enabled": $isEnabled}"""
    }
    
    @JavascriptInterface
    fun requestFloatingWindowPermission() {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            val intent = android.content.Intent(
                android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:${context.packageName}")
            )
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
    
    @JavascriptInterface
    fun setFloatingWindowEnabled(enabled: Boolean) {
        coroutineScope.launch {
            floatingWindowManager.setFloatingWindowEnabled(enabled)
        }
    }
    
    /**
     * 启动语音录制页面
     * 由WebView中的语音按钮调用
     */
    @JavascriptInterface
    fun startVoiceRecording() {
        android.util.Log.d("MainAppInterface", "startVoiceRecording called")
        quickCaptureBridge.startVoiceRecording()
    }

    @JavascriptInterface
    fun openLinkCapture() {
        android.util.Log.d("MainAppInterface", "openLinkCapture called")
        quickCaptureBridge.openLinkCapture()
    }

    @JavascriptInterface
    fun startScanCapture() {
        android.util.Log.d("MainAppInterface", "startScanCapture called")
        quickCaptureBridge.startScanCapture()
    }

    @JavascriptInterface
    fun pickInsightImage() {
        android.util.Log.d("MainAppInterface", "pickInsightImage called")
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            onPickInsightImage()
        }
    }

    @JavascriptInterface
    fun recordInsightAudio() {
        android.util.Log.d("MainAppInterface", "recordInsightAudio called")
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            onRecordInsightAudio()
        }
    }

    @JavascriptInterface
    fun saveInsight(payloadJson: String) {
        coroutineScope.launch {
            val result = runCatching {
                val payload = gson.fromJson(payloadJson, InsightPayload::class.java)
                val readStatus = when (payload.readStatus?.trim()?.lowercase()) {
                    "read" -> com.example.ai4research.domain.model.ReadStatus.READ
                    else -> com.example.ai4research.domain.model.ReadStatus.UNREAD
                }
                viewModel.saveInsight(
                    id = payload.id,
                    title = payload.title.orEmpty(),
                    body = payload.body.orEmpty(),
                    imageUri = payload.imageUri,
                    audioUri = payload.audioUri,
                    categoryId = payload.categoryId,
                    categoryName = payload.categoryName,
                    readStatus = readStatus,
                    audioDurationSeconds = payload.audioDurationSeconds ?: 0
                ).getOrThrow()
            }

            val eventPayload = result.fold(
                onSuccess = { item ->
                    gson.toJson(
                        mapOf(
                            "success" to true,
                            "itemId" to item.id
                        )
                    )
                },
                onFailure = { error ->
                    gson.toJson(
                        mapOf(
                            "success" to false,
                            "message" to (error.message ?: "保存失败")
                        )
                    )
                }
            )
            emitJsEvent("insight-save-result", eventPayload)
        }
    }

    @JavascriptInterface
    fun updateInsightReadStatus(itemId: String, readStatus: String) {
        coroutineScope.launch {
            val normalizedStatus = when (readStatus.trim().lowercase()) {
                "read" -> com.example.ai4research.domain.model.ReadStatus.READ
                else -> com.example.ai4research.domain.model.ReadStatus.UNREAD
            }
            val result = viewModel.updateInsightReadStatus(itemId, normalizedStatus)
            val eventPayload = gson.toJson(
                mapOf(
                    "success" to result.isSuccess,
                    "itemId" to itemId,
                    "readStatus" to normalizedStatus.name.lowercase(),
                    "message" to result.exceptionOrNull()?.message
                )
            )
            emitJsEvent("insight-read-status-updated", eventPayload)
        }
    }

    private fun showLinkOverlayPermissionPrompt() {
        AlertDialog.Builder(context)
            .setTitle("需要悬浮窗权限")
            .setMessage("添加链接需要悬浮窗权限，用来显示链接输入框。")
            .setNegativeButton("取消", null)
            .setPositiveButton("去授权") { _, _ ->
                requestFloatingWindowPermission()
            }
            .show()
    }
}

private data class InsightPayload(
    val id: String? = null,
    val title: String? = null,
    val body: String? = null,
    val imageUri: String? = null,
    val audioUri: String? = null,
    val audioDurationSeconds: Int? = null,
    val categoryId: String? = null,
    val categoryName: String? = null,
    val readStatus: String? = null
)

private fun queryDisplayName(context: android.content.Context, uri: android.net.Uri): String? {
    return runCatching {
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)
                } else {
                    null
                }
            }
    }.getOrNull()
}

private fun readAudioDurationSeconds(context: android.content.Context, uri: android.net.Uri): Int {
    return runCatching {
        val retriever = android.media.MediaMetadataRetriever()
        retriever.setDataSource(context, uri)
        val durationMs = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull()
            ?: 0L
        retriever.release()
        (durationMs / 1000L).toInt()
    }.getOrDefault(0)
}

private fun itemTypeToTabId(type: ItemType): String {
    return when (type) {
        ItemType.PAPER -> "papers"
        ItemType.ARTICLE -> "articles"
        ItemType.COMPETITION -> "competitions"
        ItemType.INSIGHT -> "home"
        ItemType.VOICE -> "voice"
    }
}

private fun getItemTypeLabel(type: ItemType): String {
    return when (type) {
        ItemType.PAPER -> "论文"
        ItemType.ARTICLE -> "资料"
        ItemType.COMPETITION -> "竞赛"
        ItemType.INSIGHT -> "动态"
        ItemType.VOICE -> "语音"
    }
}

@Composable
private fun ScanImportConfigDialog(
    imageCount: Int,
    projects: List<Project>,
    selectedType: ItemType,
    selectedProjectId: String?,
    onTypeSelected: (ItemType) -> Unit,
    onProjectSelected: (String?) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val scrollState = rememberScrollState()
    val typeOptions = listOf(
        ItemType.ARTICLE,
        ItemType.PAPER,
        ItemType.COMPETITION,
        ItemType.INSIGHT
    )

    ComposeAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("导入图片")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
            ) {
                Text("已选择 $imageCount 张图片，请先选择分类和项目。")
                Spacer(modifier = Modifier.height(16.dp))
                Text("分类", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))
                typeOptions.forEach { type ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onTypeSelected(type) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedType == type,
                            onClick = { onTypeSelected(type) }
                        )
                        Text(getItemTypeLabel(type))
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("项目", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onProjectSelected(null) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedProjectId == null,
                        onClick = { onProjectSelected(null) }
                    )
                    Text("未分配")
                }
                projects.forEach { project ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onProjectSelected(project.id) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedProjectId == project.id,
                            onClick = { onProjectSelected(project.id) }
                        )
                        Text(project.name)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("开始处理")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

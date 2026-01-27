package com.example.ai4research.ui.main

import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import com.example.ai4research.core.util.LocalWebViewCache
import com.example.ai4research.service.FloatingWindowManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Main Screen Refactored with WebView
 * Loads main_ui.html which contains the React+Tailwind UI
 */
@Composable
fun MainScreen(
    onLogout: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    viewModel: MainViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val webViewCache = LocalWebViewCache.current
    
    // 获取 MainActivity 的 targetTab
    val activity = context as? com.example.ai4research.MainActivity

    // Track loading state
    var isLoading by remember { mutableStateOf(!webViewCache.isMainLoaded()) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isPageReady by remember { mutableStateOf(false) }  // 跟踪页面是否已加载完成
    val webView = remember { webViewCache.acquireMain(context) }
    
    val papers by viewModel.papers.collectAsState()
    val competitions by viewModel.competitions.collectAsState()
    val insights by viewModel.insights.collectAsState()
    
    val coroutineScope = rememberCoroutineScope()
    val floatingWindowManager = viewModel.floatingWindowManager
    
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
                // 1. 先刷新远程数据到本地
                viewModel.fetchData()
                
                // 2. 重新应用筛选，确保 Flow collect 获取最新数据
                viewModel.applyFilter(viewModel.currentFilter.value, viewModel.currentProjectId.value)
                
                // 3. 等待数据加载完成（最多等待3秒）
                var retryCount = 0
                while (viewModel.isLoading.value && retryCount < 30) {
                    kotlinx.coroutines.delay(100)
                    retryCount++
                }
                
                // 额外等待一下确保 Room Flow 已 emit 新数据
                kotlinx.coroutines.delay(300)
                
                // 推送最新数据
                val papersJson = viewModel.getPapersJson()
                val competitionsJson = viewModel.getCompetitionsJson()
                val insightsJson = viewModel.getInsightsJson()
                val projectsJson = viewModel.getProjectsJson()
                android.util.Log.d("MainScreen", "推送数据: papers=${viewModel.papers.value.size}, insights=${viewModel.insights.value.size}")
                webViewRef?.evaluateJavascript("if(window.receivePapers) window.receivePapers($papersJson)", null)
                webViewRef?.evaluateJavascript("if(window.receiveCompetitions) window.receiveCompetitions($competitionsJson)", null)
                webViewRef?.evaluateJavascript("if(window.receiveInsights) window.receiveInsights($insightsJson)", null)
                webViewRef?.evaluateJavascript("if(window.receiveProjects) window.receiveProjects($projectsJson)", null)
                
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
            onNavigateToDetail = onNavigateToDetail
        )
    }
    
    val projectsState by viewModel.projects.collectAsState()

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
                                val competitionsJson = viewModel.getCompetitionsJson()
                                val insightsJson = viewModel.getInsightsJson()
                                val projectsJson = viewModel.getProjectsJson()
                                android.util.Log.d("MainScreen", "Pushing initial data to WebView: papers=${viewModel.papers.value.size}, competitions=${viewModel.competitions.value.size}, insights=${viewModel.insights.value.size}")
                                view.evaluateJavascript("if(window.receivePapers) window.receivePapers($papersJson)", null)
                                view.evaluateJavascript("if(window.receiveCompetitions) window.receiveCompetitions($competitionsJson)", null)
                                view.evaluateJavascript("if(window.receiveInsights) window.receiveInsights($insightsJson)", null)
                                view.evaluateJavascript("if(window.receiveProjects) window.receiveProjects($projectsJson)", null)
                                
                                // 同时触发刷新以获取最新数据
                                viewModel.fetchData()
                                
                                // 1秒后再次推送数据，确保数据刷新后同步到 WebView
                                view.postDelayed({
                                    val latestPapersJson = viewModel.getPapersJson()
                                    val latestCompetitionsJson = viewModel.getCompetitionsJson()
                                    val latestInsightsJson = viewModel.getInsightsJson()
                                    val latestProjectsJson = viewModel.getProjectsJson()
                                    android.util.Log.d("MainScreen", "Re-pushing data after refresh: papers=${viewModel.papers.value.size}")
                                    view.evaluateJavascript("if(window.receivePapers) window.receivePapers($latestPapersJson)", null)
                                    view.evaluateJavascript("if(window.receiveCompetitions) window.receiveCompetitions($latestCompetitionsJson)", null)
                                    view.evaluateJavascript("if(window.receiveInsights) window.receiveInsights($latestInsightsJson)", null)
                                    view.evaluateJavascript("if(window.receiveProjects) window.receiveProjects($latestProjectsJson)", null)
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
                        val competitionsJson = viewModel.getCompetitionsJson()
                        val insightsJson = viewModel.getInsightsJson()
                        val projectsJson = viewModel.getProjectsJson()
                        evaluateJavascript("if(window.receivePapers) window.receivePapers($papersJson)", null)
                        evaluateJavascript("if(window.receiveCompetitions) window.receiveCompetitions($competitionsJson)", null)
                        evaluateJavascript("if(window.receiveInsights) window.receiveInsights($insightsJson)", null)
                        evaluateJavascript("if(window.receiveProjects) window.receiveProjects($projectsJson)", null)
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
    private val onNavigateToDetail: (String) -> Unit
) {
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
    fun checkFloatingWindowStatus(): String {
        val hasPermission = android.provider.Settings.canDrawOverlays(context)
        var isEnabled = false
        // 浣跨敤 runBlocking 鑾峰彇褰撳墠璁剧疆鐘舵€?(浠呯敤浜庡悓姝avaScript璋冪敤)
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
}

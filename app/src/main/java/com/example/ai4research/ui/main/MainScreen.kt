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

    // Track loading state
    var isLoading by remember { mutableStateOf(!webViewCache.isMainLoaded()) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    val webView = remember { webViewCache.acquireMain(context) }
    
    val papers by viewModel.papers.collectAsState()
    val competitions by viewModel.competitions.collectAsState()
    
    val coroutineScope = rememberCoroutineScope()
    val floatingWindowManager = viewModel.floatingWindowManager

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

    // Sync data to WebView when it changes
    LaunchedEffect(papers) {
        if (papers.isNotEmpty()) {
            val jsonStr = viewModel.getPapersJson()
            webViewRef?.evaluateJavascript("window.receivePapers($jsonStr)", null)
        }
    }

    LaunchedEffect(competitions) {
        if (competitions.isNotEmpty()) {
            val jsonStr = viewModel.getCompetitionsJson()
            webViewRef?.evaluateJavascript("window.receiveCompetitions($jsonStr)", null)
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
                        setRenderPriority(WebSettings.RenderPriority.HIGH)
                    }
                    
                    // Allow debugging in development
                    WebView.setWebContentsDebuggingEnabled(true)
                    
                    webViewClient = object : WebViewClient() {
                        override fun onPageCommitVisible(view: WebView?, url: String?) {
                            if (isLoading) {
                                isLoading = false
                            }
                            webViewCache.markMainLoaded()
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            if (isLoading) {
                                isLoading = false
                            }
                            webViewCache.markMainLoaded()
                            // Initial data sync
                            viewModel.fetchData()
                        }
                    }
                    removeJavascriptInterface("AndroidInterface")
                    addJavascriptInterface(mainAppInterface, "AndroidInterface")
                    
                    if (url.isNullOrBlank() || url == "about:blank") {
                        loadUrl("file:///android_asset/main_ui.html")
                    }
                    webViewRef = this
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
        android.os.Handler(android.os.Looper.getMainLooper()).post {
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

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.ai4research.data.remote.dto.NocoItemDto
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

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
    // Track loading state
    var isLoading by remember { mutableStateOf(true) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    
    val papers by viewModel.papers.collectAsState()
    val competitions by viewModel.competitions.collectAsState()

    // Interface for Main UI
    class MainAppInterface {
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
            factory = { context ->
                WebView(context).apply {
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
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            isLoading = false
                            // Initial data sync
                            viewModel.fetchData()
                        }
                    }
                    addJavascriptInterface(MainAppInterface(), "AndroidInterface")
                    
                    loadUrl("file:///android_asset/main_ui.html")
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

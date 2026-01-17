package com.example.ai4research.ui.main

import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Main Screen Refactored with WebView
 * Loads main_ui.html which contains the React+Tailwind UI
 */
@Composable
fun MainScreen(
    onLogout: () -> Unit,
    onNavigateToDetail: (String) -> Unit
) {
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
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    cacheMode = WebSettings.LOAD_DEFAULT
                    // Enable hardware acceleration and smooth scrolling
                    setRenderPriority(WebSettings.RenderPriority.HIGH)
                }
                
                // Allow debugging in development
                WebView.setWebContentsDebuggingEnabled(true)
                
                webViewClient = WebViewClient()
                addJavascriptInterface(MainAppInterface(), "AndroidInterface")
                
                loadUrl("file:///android_asset/main_ui.html")
            }
        }
    )
}

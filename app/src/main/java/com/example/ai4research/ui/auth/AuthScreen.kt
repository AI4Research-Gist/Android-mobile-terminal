package com.example.ai4research.ui.auth

import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.ai4research.core.util.LocalWebViewCache

@Composable
fun AuthScreen(
    onLoginSuccess: () -> Unit,
    onRegisterSuccess: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val webViewCache = LocalWebViewCache.current
    val loginUrl = "file:///android_asset/login.html"
    
    // WebView reference for calling JavaScript
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    val webView = remember { webViewCache.acquireLogin(context) }

    DisposableEffect(Unit) {
        onDispose {
            webViewCache.releaseLogin(webView)
        }
    }
    
    // WebApp Interface
    val webAppInterface = remember(viewModel) {
        WebAppInterface(viewModel)
    }

    // Fullscreen WebView
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = {
            webView.apply {
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
                
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    cacheMode = WebSettings.LOAD_DEFAULT
                }

                // Transparent background to let Compose background show if needed, 
                // but our HTML has its own background.
                // setBackgroundColor(0) 

                webViewClient = object : WebViewClient() {
                    override fun onPageCommitVisible(view: WebView?, url: String?) {
                        webViewCache.markLoginLoaded()
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        webViewCache.markLoginLoaded()
                    }
                }
                
                // Add JS Interface
                removeJavascriptInterface("AndroidInterface")
                addJavascriptInterface(webAppInterface, "AndroidInterface")

                // Load the asset file
                if (url.isNullOrBlank() || url == "about:blank" || url != loginUrl) {
                    loadUrl(loginUrl)
                } else {
                    // If this WebView was preloaded before the JS interface was attached,
                    // the page won't see window.AndroidInterface. Detect and reload once.
                    evaluateJavascript("typeof window.AndroidInterface !== 'undefined'") { result ->
                        val normalized = result.trim('"')
                        if (normalized != "true") {
                            loadUrl(loginUrl)
                        }
                    }
                }
                
                // Save reference
                webViewRef = this
            }
        },
        update = { view ->
            // You can update WebView state here if needed
            webViewRef = view
        }
    )
    
    // Use LaunchedEffect(Unit) with state check to avoid repeated triggers
    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.uiState.collect { state ->
            when (state) {
                is AuthUiState.LoginSuccess -> {
                    onLoginSuccess()
                    viewModel.resetUiState() // Reset state to prevent repeated triggers
                }
                is AuthUiState.RegisterSuccess -> {
                    onRegisterSuccess()
                    viewModel.resetUiState() // Reset state to prevent repeated triggers
                }
                is AuthUiState.Error -> {
                    // Pass error message to frontend for display
                    val errorMessage = state.message
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        webViewRef?.evaluateJavascript(
                            "if(window.showError) window.showError('$errorMessage');",
                            null
                        )
                    }
                }
                else -> {}
            }
        }
    }
}

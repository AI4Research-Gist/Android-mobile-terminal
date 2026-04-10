package com.example.ai4research.ui.auth

import android.webkit.WebSettings
import android.webkit.ConsoleMessage
import android.webkit.WebView
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
    val tag = "AuthScreen"
    val context = LocalContext.current
    val webViewCache = LocalWebViewCache.current
    val loginUrl = "file:///android_asset/login.html"
    var isLoading by remember { mutableStateOf(!webViewCache.isLoginLoaded()) }
    
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

    Box(modifier = Modifier.fillMaxSize()) {
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

                    WebView.setWebContentsDebuggingEnabled(true)

                    // Transparent background to let Compose background show if needed, 
                    // but our HTML has its own background.
                    // setBackgroundColor(0) 

                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                            android.util.Log.d(
                                tag,
                                "console ${consoleMessage.messageLevel()} @${consoleMessage.sourceId()}:${consoleMessage.lineNumber()} ${consoleMessage.message()}"
                            )
                            return true
                        }

                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            android.util.Log.d(tag, "progress=$newProgress url=${view?.url}")
                        }
                    }

                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                            android.util.Log.d(tag, "onPageStarted url=$url")
                            isLoading = true
                        }

                        override fun onPageCommitVisible(view: WebView?, url: String?) {
                            android.util.Log.d(tag, "onPageCommitVisible url=$url")
                            webViewCache.markLoginLoaded()
                            isLoading = false
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            android.util.Log.d(tag, "onPageFinished url=$url")
                            webViewCache.markLoginLoaded()
                            isLoading = false
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?
                        ) {
                            android.util.Log.e(
                                tag,
                                "onReceivedError url=${request?.url} code=${error?.errorCode} desc=${error?.description}"
                            )
                        }

                        override fun onReceivedHttpError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            errorResponse: android.webkit.WebResourceResponse?
                        ) {
                            android.util.Log.e(
                                tag,
                                "onReceivedHttpError url=${request?.url} status=${errorResponse?.statusCode}"
                            )
                        }
                    }
                    
                    // Add JS Interface
                    removeJavascriptInterface("AndroidInterface")
                    addJavascriptInterface(webAppInterface, "AndroidInterface")

                    // Load the asset file
                    if (url.isNullOrBlank() || url == "about:blank" || url != loginUrl) {
                        android.util.Log.d(tag, "Loading login asset directly, currentUrl=$url")
                        loadUrl(loginUrl)
                    } else {
                        // If this WebView was preloaded before the JS interface was attached,
                        // the page won't see window.AndroidInterface. Detect and reload once.
                        android.util.Log.d(tag, "Reusing cached login WebView, currentUrl=$url")
                        evaluateJavascript("typeof window.AndroidInterface !== 'undefined'") { result ->
                            val normalized = result.trim('"')
                            android.util.Log.d(tag, "AndroidInterface presence result=$normalized")
                            if (normalized != "true") {
                                android.util.Log.d(tag, "AndroidInterface missing in cached page, reloading login asset")
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

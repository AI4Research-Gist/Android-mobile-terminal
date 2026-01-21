package com.example.ai4research.ui.auth

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.ai4research.core.security.BiometricHelper

@Composable
fun AuthScreen(
    initialAuthMode: Int = 0,
    onLoginSuccess: () -> Unit,
    onRegisterSuccess: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
    biometricHelper: BiometricHelper = BiometricHelper()
) {
    val context = LocalContext.current
    
    // WebView 引用，用于调用 JavaScript
    var webViewRef: WebView? = null
    
    // WebApp Interface Class
    class WebAppInterface {
        @JavascriptInterface
        fun login(identifier: String, password: String) {
            // 调用 ViewModel 登录逻辑
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                viewModel.login(identifier, password)
            }
        }

        @JavascriptInterface
        fun register(username: String, email: String, password: String, phone: String?) {
            // 调用 ViewModel 注册逻辑
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                viewModel.register(username, email, password, phone)
            }
        }

        @JavascriptInterface
        fun socialLogin(type: String) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                if (type == "mobile") {
                    // Trigger One-Click Login SDK
                }
                // 其他社交登录逻辑
            }
        }
        
        @JavascriptInterface
        fun checkUsername(username: String): Boolean {
            // 注意：这是同步调用，在 WebView JS 线程执行
            // 对于异步检查，需要使用回调机制
            return false // 默认返回 false，实际检查在注册时进行
        }
    }

    // Fullscreen WebView
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
                
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    cacheMode = WebSettings.LOAD_DEFAULT
                    // Enable hardware acceleration
                    setRenderPriority(WebSettings.RenderPriority.HIGH)
                }

                // Transparent background to let Compose background show if needed, 
                // but our HTML has its own background.
                // setBackgroundColor(0) 

                webViewClient = WebViewClient()
                
                // Add JS Interface
                addJavascriptInterface(WebAppInterface(), "AndroidInterface")

                // Load the asset file
                loadUrl("file:///android_asset/login.html")
                
                // 保存引用
                webViewRef = this
            }
        },
        update = { webView ->
            // You can update WebView state here if needed
            webViewRef = webView
        }
    )
    
    // Observe ViewModel state to trigger navigation or show errors
    val uiState = viewModel.uiState.collectAsState()
    
    // 使用 LaunchedEffect(Unit) 配合状态检查，避免重复触发
    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.uiState.collect { state ->
            when (state) {
                is AuthUiState.LoginSuccess -> {
                    onLoginSuccess()
                    viewModel.resetUiState() // 重置状态防止重复触发
                }
                is AuthUiState.RegisterSuccess -> {
                    onRegisterSuccess()
                    viewModel.resetUiState() // 重置状态防止重复触发
                }
                is AuthUiState.Error -> {
                    // 将错误信息传递给前端显示
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

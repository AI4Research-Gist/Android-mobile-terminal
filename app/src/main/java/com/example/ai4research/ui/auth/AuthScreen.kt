package com.example.ai4research.ui.auth

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
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
    
    // WebApp Interface Class
    class WebAppInterface {
        @JavascriptInterface
        fun login(username: String, password: String) {
            // Bridge to ViewModel or handle logic directly
            // Since this runs on a background thread (WebView thread), we need to post to main for UI or Toast
            // For now, let's assume we call ViewModel (which is thread-safe mostly) or show Toast
            
            // In a real app, you would call viewModel.login(username, password)
            // Here we simulate success for the demo
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(context, "Web Login: $username", Toast.LENGTH_SHORT).show()
                viewModel.login(username, password) // Call actual logic
            }
        }

        @JavascriptInterface
        fun register(username: String, password: String) {
             android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(context, "Web Register: $username", Toast.LENGTH_SHORT).show()
                 // Simulate register success for demo
                 onRegisterSuccess()
            }
        }

        @JavascriptInterface
        fun socialLogin(type: String) {
             android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(context, "Social Login: $type", Toast.LENGTH_SHORT).show()
                if (type == "mobile") {
                    // Trigger One-Click Login SDK
                }
            }
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
            }
        },
        update = { webView ->
            // You can update WebView state here if needed
        }
    )
    
    // Observe ViewModel state to trigger navigation (Optional, if ViewModel logic is hooked up)
    // val uiState by viewModel.uiState.collectAsState()
    // LaunchedEffect(uiState) { ... }
}

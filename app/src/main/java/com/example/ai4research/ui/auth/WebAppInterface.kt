package com.example.ai4research.ui.auth

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import androidx.annotation.Keep

/**
 * 用于 WebView 与 Android 原生交互的接口
 * 使用 @Keep 注解防止被混淆
 */
@Keep
class WebAppInterface(
    private val viewModel: AuthViewModel
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun login(identifier: String, password: String) {
        mainHandler.post {
            viewModel.login(identifier, password)
        }
    }

    @JavascriptInterface
    fun register(username: String, email: String, password: String, phone: String?) {
        mainHandler.post {
            viewModel.register(username, email, password, phone)
        }
    }

    @JavascriptInterface
    fun socialLogin(type: String) {
        mainHandler.post {
            if (type == "mobile") {
                // Trigger One-Click Login SDK
                // 目前暂未实现
            }
            // Other social login logic
        }
    }
    
    @JavascriptInterface
    fun checkUsername(username: String): Boolean {
        // Note: This is a synchronous call, executed in WebView JS thread
        // For async check, need to use callback mechanism
        return false // Default return false, actual check during registration
    }
}

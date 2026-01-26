package com.example.ai4research.core.util

import android.content.Context
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * WebView cache to warm up engine and reuse loaded pages for faster startup.
 */
class WebViewCache {
    private var mainWebView: WebView? = null
    private var loginWebView: WebView? = null
    private var mainLoaded = false
    private var loginLoaded = false
    private var mainInterfaceInjected = false  // 跟踪接口是否已注入

    fun warmUp(context: Context) {
        if (mainWebView != null || loginWebView != null) return
        val webView = createBaseWebView(context)
        webView.loadUrl("about:blank")
        webView.destroy()
    }

    fun preloadMain(context: Context) {
        if (mainWebView != null) return
        mainWebView = createBaseWebView(context).apply {
            webViewClient = object : WebViewClient() {
                override fun onPageCommitVisible(view: WebView?, url: String?) {
                    mainLoaded = true
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    mainLoaded = true
                }
            }
            loadUrl("file:///android_asset/main_ui.html")
        }
    }

    fun preloadLogin(context: Context) {
        if (loginWebView != null) return
        loginWebView = createBaseWebView(context).apply {
            webViewClient = object : WebViewClient() {
                override fun onPageCommitVisible(view: WebView?, url: String?) {
                    loginLoaded = true
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    loginLoaded = true
                }
            }
            loadUrl("file:///android_asset/login.html")
        }
    }

    fun acquireMain(context: Context): WebView {
        val webView = mainWebView ?: createBaseWebView(context).also { mainWebView = it }
        detachFromParent(webView)
        return webView
    }

    fun acquireLogin(context: Context): WebView {
        val webView = loginWebView ?: createBaseWebView(context).also { loginWebView = it }
        detachFromParent(webView)
        return webView
    }

    fun releaseMain(webView: WebView) {
        detachFromParent(webView)
        mainWebView = webView
    }

    fun releaseLogin(webView: WebView) {
        detachFromParent(webView)
        loginWebView = webView
    }

    fun isMainLoaded(): Boolean = mainLoaded

    fun isLoginLoaded(): Boolean = loginLoaded

    fun markMainLoaded() {
        mainLoaded = true
    }

    fun markLoginLoaded() {
        loginLoaded = true
    }
    
    fun isMainInterfaceInjected(): Boolean = mainInterfaceInjected
    
    fun markMainInterfaceInjected() {
        mainInterfaceInjected = true
    }

    fun clear() {
        mainWebView?.destroy()
        loginWebView?.destroy()
        mainWebView = null
        loginWebView = null
        mainLoaded = false
        loginLoaded = false
        mainInterfaceInjected = false
    }

    private fun createBaseWebView(context: Context): WebView {
        return WebView(context).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                cacheMode = WebSettings.LOAD_DEFAULT
            }
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
    }

    private fun detachFromParent(webView: WebView) {
        (webView.parent as? ViewGroup)?.removeView(webView)
    }
}

val LocalWebViewCache = staticCompositionLocalOf<WebViewCache> {
    error("WebViewCache not provided")
}

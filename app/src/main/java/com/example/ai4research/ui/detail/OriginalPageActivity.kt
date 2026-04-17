package com.example.ai4research.ui.detail

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class OriginalPageActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val url = intent.getStringExtra(EXTRA_URL).orEmpty()
        val initialTitle = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "Original Page" }
        val isDark = (resources.configuration.uiMode and 0x30) == 0x20

        val root = FrameLayout(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(if (isDark) Color.parseColor("#0B1220") else Color.WHITE)
        }

        val titleView = TextView(this).apply {
            text = initialTitle
            textSize = 18f
            setPadding(32, 28, 32, 12)
            setTextColor(if (isDark) Color.WHITE else Color.parseColor("#111827"))
        }

        val urlView = TextView(this).apply {
            text = url
            textSize = 12f
            maxLines = 2
            setPadding(32, 0, 32, 20)
            setTextColor(if (isDark) Color.parseColor("#9CA3AF") else Color.parseColor("#6B7280"))
        }

        val loadingOverlay = FrameLayout(this)
        val progress = ProgressBar(this).apply { isIndeterminate = true }
        loadingOverlay.addView(
            progress,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.CENTER
            }
        )

        val webView = WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                cacheMode = WebSettings.LOAD_DEFAULT
                loadsImagesAutomatically = true
                builtInZoomControls = true
                displayZoomControls = false
                setSupportZoom(true)
                useWideViewPort = true
                loadWithOverviewMode = true
                userAgentString =
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                javaScriptCanOpenWindowsAutomatically = true
                mediaPlaybackRequiresUserGesture = false
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            }

            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    loadingOverlay.visibility = View.VISIBLE
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    loadingOverlay.visibility = View.GONE
                    val pageTitle = view?.title.orEmpty().ifBlank { initialTitle }
                    titleView.text = pageTitle
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    if (request?.isForMainFrame == true) {
                        loadingOverlay.visibility = View.GONE
                    }
                }
            }

            loadUrl(normalizeBrowsableUrl(url))
        }

        container.addView(titleView)
        container.addView(urlView)
        container.addView(webView)

        root.addView(
            container,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        root.addView(
            loadingOverlay,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }

        setContentView(root)
    }

    companion object {
        const val EXTRA_URL = "extra_url"
        const val EXTRA_TITLE = "extra_title"
    }
}

private fun normalizeBrowsableUrl(url: String): String {
    val trimmed = url.trim()
    val lower = trimmed.lowercase()
    return if (lower.startsWith("http://")) {
        "https://" + trimmed.removePrefix("http://")
    } else {
        trimmed
    }
}

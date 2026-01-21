package com.example.ai4research.service

import android.app.Service
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.compose.ui.platform.ComposeView
import com.example.ai4research.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@AndroidEntryPoint
class FloatingWindowService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private var clipboardManager: ClipboardManager? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        setupFloatingView()
        setupClipboardListener()
    }

    private fun setupFloatingView() {
        // Use a simple ComposeView or XML layout. Here we use a FrameLayout with an icon for simplicity
        // In a real app, use ComposeView with saved state registry owners set up
        
        floatingView = FrameLayout(this).apply {
            background = getDrawable(R.drawable.ic_launcher_background) // Placeholder
            // In real app, inflate a nice layout or use Compose
        }
        
        // Add a simple button (using ImageView for now as placeholder)
        val icon = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_add)
            setOnClickListener {
                Toast.makeText(this@FloatingWindowService, "Floating Action Clicked", Toast.LENGTH_SHORT).show()
                // Here we would open a dialog or capture screen
            }
        }
        (floatingView as FrameLayout).addView(icon)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 100

        try {
            windowManager.addView(floatingView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupClipboardListener() {
        clipboardManager?.addPrimaryClipChangedListener {
            val clip = clipboardManager?.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).text
                if (text != null && (text.contains("http") || text.contains("www"))) {
                    Toast.makeText(this, "Link Detected: $text", Toast.LENGTH_LONG).show()
                    // Here we would save to DB or open dialog
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::floatingView.isInitialized) {
            windowManager.removeView(floatingView)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}

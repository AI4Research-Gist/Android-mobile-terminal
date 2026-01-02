package com.example.ai4research.core.floating

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import android.app.PendingIntent
import com.example.ai4research.R
import com.example.ai4research.domain.repository.ItemRepository
import com.example.ai4research.ui.capture.CameraCaptureActivity
import com.example.ai4research.ui.capture.CaptureLinkActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class FloatingBallService : Service() {

    @Inject
    lateinit var itemRepository: ItemRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var windowManager: WindowManager
    private var rootView: View? = null
    private var params: WindowManager.LayoutParams? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        startAsForeground()
        showOverlay()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopSelf()
            ACTION_START -> {
                // no-op, created in onCreate
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        hideOverlay()
    }

    private fun showOverlay() {
        if (!OverlayPermission.canDrawOverlays(this)) {
            Toast.makeText(this, "请先授予悬浮窗权限", Toast.LENGTH_SHORT).show()
            stopSelf()
            return
        }

        if (rootView != null) return

        val ctx = this
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
        }

        val ball = createBallView(ctx)
        val menu = createMenuView(ctx).apply { visibility = View.GONE }

        ball.setOnClickListener {
            menu.visibility = if (menu.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        // Drag to move
        ball.setOnTouchListener(object : View.OnTouchListener {
            private var lastX = 0f
            private var lastY = 0f
            private var downX = 0f
            private var downY = 0f
            private var isDragging = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                val p = params ?: return false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        lastX = event.rawX
                        lastY = event.rawY
                        downX = lastX
                        downY = lastY
                        isDragging = false
                        return false // allow click
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - lastX
                        val dy = event.rawY - lastY
                        if (!isDragging) {
                            val dist = kotlin.math.hypot(event.rawX - downX, event.rawY - downY)
                            isDragging = dist > 12f
                            if (isDragging) menu.visibility = View.GONE
                        }
                        if (isDragging) {
                            p.x += dx.toInt()
                            p.y += dy.toInt()
                            windowManager.updateViewLayout(container, p)
                            lastX = event.rawX
                            lastY = event.rawY
                            return true
                        }
                        return false
                    }

                    MotionEvent.ACTION_UP -> {
                        return isDragging
                    }
                }
                return false
            }
        })

        container.addView(ball)
        container.addView(menu)

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 12
            y = 220
        }

        params = lp
        rootView = container
        windowManager.addView(container, lp)
    }

    private fun hideOverlay() {
        rootView?.let {
            runCatching { windowManager.removeView(it) }
        }
        rootView = null
        params = null
    }

    private fun createBallView(context: Context): View {
        val size = dp(54)
        val ball = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }

        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(22).toFloat()
            colors = intArrayOf(0xCC0B1220.toInt(), 0xCC111827.toInt())
            orientation = GradientDrawable.Orientation.TL_BR
            setStroke(dp(1), 0x33FFFFFF)
        }
        ball.background = bg

        val icon = ImageView(context).apply {
            setImageResource(android.R.drawable.ic_input_add)
            setColorFilter(0xFFFFFFFF.toInt())
        }

        ball.addView(icon, LinearLayout.LayoutParams(dp(18), dp(18)))

        return ball.apply {
            layoutParams = LinearLayout.LayoutParams(size, size)
        }
    }

    private fun createMenuView(context: Context): View {
        val menu = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }

        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(18).toFloat()
            setColor(0xEEFFFFFF.toInt())
            setStroke(dp(1), 0x22000000)
        }
        menu.background = bg

        menu.addView(
            createMenuItem(
                context = context,
                title = "识别链接",
                onClick = { handleCaptureLink() }
            )
        )
        menu.addView(
            createMenuItem(
                context = context,
                title = "拍照识别",
                onClick = { launchCamera() }
            )
        )

        return menu
    }

    private fun createMenuItem(context: Context, title: String, onClick: () -> Unit): View {
        return TextView(context).apply {
            text = title
            setTextColor(0xFF111827.toInt())
            textSize = 14f
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setOnClickListener { onClick() }
        }
    }

    private fun handleCaptureLink() {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        val text = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).coerceToText(this).toString() else ""

        val url = text.trim().takeIf { it.startsWith("http://") || it.startsWith("https://") }
        if (url.isNullOrBlank()) {
            // fallback to manual input
            val intent = Intent(this, CaptureLinkActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            Toast.makeText(this, "未检测到链接：已打开手动输入", Toast.LENGTH_SHORT).show()
            return
        }

        serviceScope.launch {
            val result = withContext(Dispatchers.IO) {
                itemRepository.createUrlItem(url = url)
            }
            if (result.isSuccess) {
                Toast.makeText(this@FloatingBallService, "已采集链接", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@FloatingBallService, "采集失败：${result.exceptionOrNull()?.message ?: "未知错误"}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun launchCamera() {
        val intent = Intent(this, CameraCaptureActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    private fun startAsForeground() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "悬浮球",
                NotificationManager.IMPORTANCE_LOW
            )
            nm.createNotificationChannel(channel)
        }

        val stopIntent = Intent(this, FloatingBallService::class.java).apply { action = ACTION_STOP }
        val stopFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val stopPending = PendingIntent.getService(this, 0, stopIntent, stopFlags)

        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("悬浮球运行中")
            .setContentText("点击悬浮球可快速采集链接/图片")
            .setOngoing(true)
            .addAction(0, "关闭", stopPending)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val ACTION_START = "com.example.ai4research.core.floating.action.START"
        const val ACTION_STOP = "com.example.ai4research.core.floating.action.STOP"

        private const val NOTIFICATION_CHANNEL_ID = "floating_ball"
        private const val NOTIFICATION_ID = 10011

        fun start(context: Context) {
            val intent = Intent(context, FloatingBallService::class.java).apply { action = ACTION_START }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, FloatingBallService::class.java).apply { action = ACTION_STOP }
            context.startService(intent)
        }
    }
}



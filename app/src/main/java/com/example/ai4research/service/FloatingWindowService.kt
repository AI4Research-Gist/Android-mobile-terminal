package com.example.ai4research.service

import android.Manifest
import android.app.Service
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.content.BroadcastReceiver
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.TypedValue
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.webkit.MimeTypeMap
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.ai4research.domain.model.ItemStatus
import com.example.ai4research.domain.model.ItemType
import com.example.ai4research.domain.repository.ItemRepository
import com.example.ai4research.R
import com.example.ai4research.ui.floating.FloatingBallView
import com.example.ai4research.ui.floating.FloatingMenuView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.abs
import kotlin.coroutines.resume
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * 高科技悬浮窗服务
 * 提供截图、区域选择、链接识别等功能
 */
@AndroidEntryPoint
class FloatingWindowService : Service() {

    companion object {
        const val ACTION_SHOW = "com.example.ai4research.action.SHOW_FLOATING"
        const val ACTION_HIDE = "com.example.ai4research.action.HIDE_FLOATING"
        const val ACTION_SCREENSHOT = "com.example.ai4research.action.SCREENSHOT"
        const val ACTION_REGION_SELECT = "com.example.ai4research.action.REGION_SELECT"
        const val ACTION_CAPTURE_AFTER_PERMISSION = "com.example.ai4research.action.CAPTURE_AFTER_PERMISSION"
        const val ACTION_SHOW_LINK_INPUT = "com.example.ai4research.action.SHOW_LINK_INPUT"
        const val ACTION_ATTACH_QUICK_INSIGHT_IMAGE = "com.example.ai4research.action.ATTACH_QUICK_INSIGHT_IMAGE"
        const val ACTION_START_QUICK_INSIGHT_RECORDING = "com.example.ai4research.action.START_QUICK_INSIGHT_RECORDING"
        const val ACTION_SHOW_QUICK_INSIGHT = "com.example.ai4research.action.SHOW_QUICK_INSIGHT"
        const val ACTION_RESTORE_QUICK_INSIGHT = "com.example.ai4research.action.RESTORE_QUICK_INSIGHT"
        const val EXTRA_IMAGE_URI = "image_uri"
        const val EXTRA_CAPTURE_MODE = "mode"
        private const val PROJECTION_NOTIFICATION_CHANNEL_ID = "media_projection_capture"
        private const val PROJECTION_NOTIFICATION_ID = 1002
        
        // 截图完成广播
        const val ACTION_CAPTURE_COMPLETED = "com.example.ai4research.action.CAPTURE_COMPLETED"
        const val EXTRA_IMAGE_PATH = "image_path"
        
        // 条目添加成功广播 - 通知主页面刷新
        const val ACTION_ITEM_ADDED = "com.example.ai4research.action.ITEM_ADDED"
        const val EXTRA_ITEM_ID = "item_id"
        const val EXTRA_ITEM_TYPE = "item_type"
        
        // 用于检测链接的正则表达式
        private val URL_PATTERN = Regex(
            "(https?://[\\w\\-]+(\\.[\\w\\-]+)+[\\w\\-.,@?^=%&:/~+#]*)"
        )
        private val DOI_PATTERN = Regex("10\\.\\d{4,}/[^\\s]+")
        private val ARXIV_PATTERN = Regex("arxiv\\.org/abs/\\d+\\.\\d+")
    }

    private lateinit var windowManager: WindowManager
    private var floatingBall: FloatingBallView? = null
    private var floatingMenu: FloatingMenuView? = null
    private var inputView: View? = null
    private var resultView: View? = null
    private var competitionInputView: View? = null
    private var quickInsightView: View? = null
    private var quickInsightTitleInput: EditText? = null
    private var quickInsightBodyInput: EditText? = null
    private var quickInsightAttachmentStatusView: TextView? = null
    private var quickInsightScreenshotButton: Button? = null
    private var quickInsightVoiceButton: Button? = null
    private var quickInsightSaveButton: Button? = null
    
    private var ballLayoutParams: WindowManager.LayoutParams? = null
    private var menuLayoutParams: WindowManager.LayoutParams? = null
    
    private var clipboardManager: ClipboardManager? = null
    private var isMenuExpanded = false
    private var detectedLink: String? = null
    private var isProjectionForegroundActive = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mediaProjectionManager by lazy {
        getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }
    private var isCapturing = false
    private var regionOverlay: View? = null
    private val quickInsightRecorder by lazy { AudioRecorderHelper(this) }
    private var quickInsightScreenshotPath: String? = null
    private var quickInsightAudioPath: String? = null
    private var quickInsightAudioDurationSeconds: Int = 0
    private var isQuickInsightRecording = false
    private var isSavingQuickInsight = false
    private var quickInsightDraftTitle: String = ""
    private var quickInsightDraftBody: String = ""
    private var hasQuickInsightDraft: Boolean = false

    private enum class CaptureResultTarget {
        OCR_IMPORT,
        QUICK_INSIGHT_ATTACHMENT
    }

    private var captureResultTarget = CaptureResultTarget.OCR_IMPORT

    @Inject
    lateinit var floatingWindowManager: FloatingWindowManager
    
    @Inject
    lateinit var aiService: AIService
    
    @Inject
    lateinit var itemRepository: ItemRepository

    @Inject
    lateinit var imageScanImportService: ImageScanImportService

    // 广播接收器
    private val captureReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_CAPTURE_COMPLETED) {
                val path = intent.getStringExtra(EXTRA_IMAGE_PATH)
                if (path != null) {
                    handleCapturedImagePath(path)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        setupClipboardListener()
        
        // 注册广播
        registerReceiver(captureReceiver, IntentFilter(ACTION_CAPTURE_COMPLETED), Context.RECEIVER_NOT_EXPORTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> showFloatingBall()
            ACTION_HIDE -> hideFloatingBall()
            ACTION_SCREENSHOT -> triggerScreenshot()
            ACTION_REGION_SELECT -> triggerRegionSelect()
            ACTION_CAPTURE_AFTER_PERMISSION -> resumePendingCapture(
                intent.getStringExtra(EXTRA_CAPTURE_MODE) ?: "full"
            )
            ACTION_SHOW_LINK_INPUT -> showLinkInputWindow()
            ACTION_SHOW_QUICK_INSIGHT -> showOrRestoreQuickInsightWindow()
            ACTION_RESTORE_QUICK_INSIGHT -> showOrRestoreQuickInsightWindow()
            ACTION_ATTACH_QUICK_INSIGHT_IMAGE -> {
                showOrRestoreQuickInsightWindow()
                intent.getStringExtra(EXTRA_IMAGE_URI)?.let { uriString ->
                    attachImageUriToQuickInsight(uriString)
                }
            }
            ACTION_START_QUICK_INSIGHT_RECORDING -> {
                showOrRestoreQuickInsightWindow()
                startQuickInsightRecordingInternal()
            }
        }
        return START_STICKY
    }

    private fun showFloatingBall() {
        if (floatingBall != null) return

        // 创建悬浮球视图
        floatingBall = FloatingBallView(this).apply {
            onClickListener = { toggleMenu() }
            if (detectedLink != null) {
                showLinkBadge = true
            }
        }

        // 窗口参数
        ballLayoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 300
        }

        // 设置拖拽
        setupDragging()

        try {
            windowManager.addView(floatingBall, ballLayoutParams)
        } catch (e: Exception) {
            android.util.Log.e("FloatingWindow", "Failed to add floating ball", e)
            Toast.makeText(this, "悬浮窗显示失败: ${e.message}", Toast.LENGTH_SHORT).show()
            floatingBall = null
            ballLayoutParams = null
        }
    }

    private fun hideFloatingBall() {
        // 关闭菜单和输入框
        if (isMenuExpanded) hideMenu()
        hideInputWindow()
        hideQuickInsightWindow()
        hideResultOverlay()
        
        // 移除悬浮球
        floatingBall?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        floatingBall = null
        ballLayoutParams = null
    }

    private fun setupDragging() {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false
        val clickThreshold = 10

        floatingBall?.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = ballLayoutParams?.x ?: 0
                    initialY = ballLayoutParams?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - initialTouchX).toInt()
                    val deltaY = (event.rawY - initialTouchY).toInt()
                    
                    if (abs(deltaX) > clickThreshold || abs(deltaY) > clickThreshold) {
                        isDragging = true
                    }
                    
                    if (isDragging) {
                        ballLayoutParams?.x = initialX + deltaX
                        ballLayoutParams?.y = initialY + deltaY
                        windowManager.updateViewLayout(floatingBall, ballLayoutParams)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        toggleMenu()
                    } else {
                        // 边缘吸附动画
                        snapToEdge()
                    }
                    true
                }
                else -> false
            }
        }
    }

    /**
     * 边缘吸附动画 - 将悬浮球平滑移动到屏幕左右边缘
     */
    private fun snapToEdge() {
        val currentX = ballLayoutParams?.x ?: return
        val screenWidth = resources.displayMetrics.widthPixels
        val ballWidth = floatingBall?.width ?: 140
        
        // 计算目标位置：靠近左边则吸附到左边，靠近右边则吸附到右边
        val targetX = if (currentX + ballWidth / 2 < screenWidth / 2) {
            0 // 左边缘
        } else {
            screenWidth - ballWidth // 右边缘
        }
        
        // 使用属性动画平滑过渡
        android.animation.ValueAnimator.ofInt(currentX, targetX).apply {
            duration = 250
            interpolator = android.view.animation.OvershootInterpolator(0.8f)
            addUpdateListener { animator ->
                ballLayoutParams?.x = animator.animatedValue as Int
                try {
                    windowManager.updateViewLayout(floatingBall, ballLayoutParams)
                } catch (e: Exception) {
                    cancel()
                }
            }
            start()
        }
    }

    private fun toggleMenu() {
        if (isMenuExpanded) {
            hideMenu()
        } else {
            showMenu()
        }
    }

    private fun showMenu() {
        if (floatingMenu != null) return

        floatingMenu = FloatingMenuView(this).apply {
            actionListener = object : FloatingMenuView.OnMenuActionListener {
                override fun onScreenshot() {
                    hideMenu()
                    mainHandler.postDelayed({
                        triggerScreenshot()
                    }, 300)
                }

                override fun onRegionSelect() {
                    hideMenu()
                    mainHandler.postDelayed({
                        triggerRegionSelect()
                    }, 300)
                }

                override fun onQuickInsight() {
                    hideMenu()
                    showQuickInsightWindow(resetDraft = true)
                }

                override fun onAddLink() {
                    hideMenu()
                    showLinkInputWindow()
                }

                override fun onClose() {
                    hideMenu()
                    hideFloatingBall()
                    stopSelf()
                }
            }
        }

        menuLayoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        try {
            windowManager.addView(floatingMenu, menuLayoutParams)
            floatingMenu?.showWithAnimation()
            isMenuExpanded = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun hideMenu() {
        floatingMenu?.hideWithAnimation {
            mainHandler.post {
                try {
                    floatingMenu?.let { windowManager.removeView(it) }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                floatingMenu = null
                isMenuExpanded = false
            }
        }
    }

    // ==================== 链接输入相关 ====================

    private fun showLinkInputWindow() {
        if (inputView != null) return

        // 创建布局
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#E61F1F24")) // 半透明深色背景
                cornerRadius = 48f
                setStroke(2, Color.parseColor("#33FFFFFF"))
            }
            layoutParams = FrameLayout.LayoutParams(
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 320f, resources.displayMetrics).toInt(),
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // 标题
        val title = TextView(this).apply {
            text = "添加链接"
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, 32)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        container.addView(title)

        // 输入框
        val editText = EditText(this).apply {
            hint = detectedLink ?: "粘贴论文链接、arXiv ID 或 DOI"
            if (detectedLink != null) setText(detectedLink)
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            textSize = 16f
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#33FFFFFF"))
                cornerRadius = 24f
            }
            setPadding(32, 32, 32, 32)
            imeOptions = EditorInfo.IME_ACTION_DONE
            isSingleLine = true
        }
        val editParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = 48
        }
        container.addView(editText, editParams)

        // 按钮容器
        val buttonContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        // 取消按钮
        val cancelButton = Button(this).apply {
            text = "取消"
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                cornerRadius = 24f
            }
            setOnClickListener { hideInputWindow() }
        }
        
        // 确认按钮
        val confirmButton = Button(this).apply {
            text = "添加"
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#10B981"))
                cornerRadius = 24f
            }
            setOnClickListener {
                val link = editText.text.toString().trim()
                if (link.isNotEmpty()) {
                    hideInputWindow()
                    handleLink(link)
                }
            }
        }

        buttonContainer.addView(cancelButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val spacer = View(this).apply { layoutParams = LinearLayout.LayoutParams(32, 1) }
        buttonContainer.addView(spacer)
        buttonContainer.addView(confirmButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        
        container.addView(buttonContainer)

        // 窗口参数 - 允许输入
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_DIM_BEHIND, // 允许背景变暗
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            dimAmount = 0.5f
            @Suppress("DEPRECATION")
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

        inputView = container
        try {
            windowManager.addView(inputView, params)
            detectedLink = null // 清除检测到的链接
            floatingBall?.showLinkBadge = false
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun hideInputWindow() {
        inputView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        inputView = null
    }

    // ==================== 快速灵感 ====================

    private fun showOrRestoreQuickInsightWindow() {
        if (quickInsightView != null) {
            restoreQuickInsightOverlay()
            return
        }
        showQuickInsightWindow(resetDraft = !hasQuickInsightDraft)
    }

    private fun showQuickInsightWindow(resetDraft: Boolean) {
        if (quickInsightView != null) {
            hideQuickInsightWindow()
        }
        if (resetDraft) {
            resetQuickInsightDraft(deleteFiles = true)
        }

        val scrollView = ScrollView(this).apply {
            isFillViewport = true
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 40, 48, 40)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#E61F1F24"))
                cornerRadius = 48f
                setStroke(2, Color.parseColor("#33FFFFFF"))
            }
            layoutParams = FrameLayout.LayoutParams(
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 340f, resources.displayMetrics).toInt(),
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }
        scrollView.addView(container)

        val title = TextView(this).apply {
            text = "快速灵感"
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, 12)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        container.addView(title)

        val subtitle = TextView(this).apply {
            text = "不跳转，直接记录正文、图片和语音"
            textSize = 12f
            setTextColor(Color.parseColor("#B5C6D1"))
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, 28)
        }
        container.addView(subtitle)

        val titleInput = EditText(this).apply {
            hint = "标题可留空，会自动生成"
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            textSize = 15f
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#33FFFFFF"))
                cornerRadius = 24f
            }
            setPadding(28, 24, 28, 24)
            imeOptions = EditorInfo.IME_ACTION_NEXT
            isSingleLine = true
            setText(quickInsightDraftTitle)
        }
        container.addView(
            titleInput,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 20
            }
        )

        val bodyInput = EditText(this).apply {
            hint = "想到什么就先写下来"
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            textSize = 15f
            gravity = Gravity.TOP
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#33FFFFFF"))
                cornerRadius = 24f
            }
            setPadding(28, 24, 28, 24)
            minLines = 5
            setText(quickInsightDraftBody)
        }
        container.addView(
            bodyInput,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 20
            }
        )

        val attachmentStatus = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.parseColor("#B5C6D1"))
            setPadding(4, 0, 4, 16)
        }
        container.addView(attachmentStatus)

        val attachmentActions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val screenshotButton = Button(this).apply {
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0EA5E9"))
                cornerRadius = 24f
            }
            setOnClickListener { startQuickInsightImagePicker() }
        }
        attachmentActions.addView(
            screenshotButton,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )

        val buttonSpacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(24, 1)
        }
        attachmentActions.addView(buttonSpacer)

        val voiceButton = Button(this).apply {
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#8B5CF6"))
                cornerRadius = 24f
            }
            setOnClickListener {
                if (isQuickInsightRecording) {
                    finalizeQuickInsightRecording()
                } else {
                    startQuickInsightRecording()
                }
            }
        }
        attachmentActions.addView(
            voiceButton,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        container.addView(
            attachmentActions,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 28
            }
        )

        val buttonContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val cancelButton = Button(this).apply {
            text = "取消"
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                cornerRadius = 24f
            }
            setOnClickListener { hideQuickInsightWindow() }
        }
        buttonContainer.addView(
            cancelButton,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )

        val actionSpacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(32, 1)
        }
        buttonContainer.addView(actionSpacer)

        val saveButton = Button(this).apply {
            text = "保存灵感"
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#10B981"))
                cornerRadius = 24f
            }
            setOnClickListener { saveQuickInsightFromOverlay() }
        }
        buttonContainer.addView(
            saveButton,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        container.addView(buttonContainer)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_DIM_BEHIND,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            dimAmount = 0.5f
            @Suppress("DEPRECATION")
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

        quickInsightView = scrollView
        quickInsightTitleInput = titleInput
        quickInsightBodyInput = bodyInput
        quickInsightAttachmentStatusView = attachmentStatus
        quickInsightScreenshotButton = screenshotButton
        quickInsightVoiceButton = voiceButton
        quickInsightSaveButton = saveButton
        updateQuickInsightUi()

        try {
            windowManager.addView(quickInsightView, params)
        } catch (e: Exception) {
            e.printStackTrace()
            hideQuickInsightWindow()
        }
    }

    private fun setQuickInsightOverlayVisible(visible: Boolean) {
        quickInsightView?.visibility = if (visible) View.VISIBLE else View.INVISIBLE
    }

    private fun restoreQuickInsightOverlay() {
        captureQuickInsightDraftFromInputs()
        setQuickInsightOverlayVisible(true)
        quickInsightTitleInput?.setText(quickInsightDraftTitle)
        quickInsightBodyInput?.setText(quickInsightDraftBody)
    }

    private fun hideQuickInsightWindow(deleteDraft: Boolean = true) {
        captureQuickInsightDraftFromInputs()
        quickInsightView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        quickInsightView = null
        quickInsightTitleInput = null
        quickInsightBodyInput = null
        quickInsightAttachmentStatusView = null
        quickInsightScreenshotButton = null
        quickInsightVoiceButton = null
        quickInsightSaveButton = null
        if (deleteDraft) {
            resetQuickInsightDraft(deleteFiles = true)
        } else {
            hasQuickInsightDraft = true
        }
    }

    private fun resetQuickInsightDraft(deleteFiles: Boolean) {
        if (isQuickInsightRecording) {
            if (deleteFiles) {
                quickInsightRecorder.cancelRecording()
            } else {
                finalizeQuickInsightRecording(showToast = false)
            }
        }
        if (deleteFiles) {
            deleteFileIfExists(quickInsightScreenshotPath)
            deleteFileIfExists(quickInsightAudioPath)
        }
        quickInsightScreenshotPath = null
        quickInsightAudioPath = null
        quickInsightAudioDurationSeconds = 0
        isQuickInsightRecording = false
        quickInsightDraftTitle = ""
        quickInsightDraftBody = ""
        hasQuickInsightDraft = false
    }

    private fun clearQuickInsightDraftStatePreservingFiles() {
        quickInsightScreenshotPath = null
        quickInsightAudioPath = null
        quickInsightAudioDurationSeconds = 0
        isQuickInsightRecording = false
        quickInsightDraftTitle = ""
        quickInsightDraftBody = ""
        hasQuickInsightDraft = false
    }

    private fun captureQuickInsightDraftFromInputs() {
        quickInsightDraftTitle = quickInsightTitleInput?.text?.toString().orEmpty()
        quickInsightDraftBody = quickInsightBodyInput?.text?.toString().orEmpty()
        hasQuickInsightDraft = quickInsightDraftTitle.isNotBlank() ||
            quickInsightDraftBody.isNotBlank() ||
            !quickInsightScreenshotPath.isNullOrBlank() ||
            !quickInsightAudioPath.isNullOrBlank()
    }

    private fun updateQuickInsightUi() {
        quickInsightAttachmentStatusView?.text = buildQuickInsightAttachmentText()
        quickInsightScreenshotButton?.text = if (quickInsightScreenshotPath != null) "重选图片" else "选图片"
        quickInsightVoiceButton?.text = when {
            isQuickInsightRecording -> "停止录音"
            quickInsightAudioPath != null -> "重录语音"
            else -> "附语音"
        }
        quickInsightSaveButton?.apply {
            text = if (isSavingQuickInsight) "保存中..." else "保存灵感"
            isEnabled = !isSavingQuickInsight
        }
        quickInsightScreenshotButton?.isEnabled = !isSavingQuickInsight
        quickInsightVoiceButton?.isEnabled = !isSavingQuickInsight
        quickInsightTitleInput?.isEnabled = !isSavingQuickInsight
        quickInsightBodyInput?.isEnabled = !isSavingQuickInsight
    }

    private fun buildQuickInsightAttachmentText(): String {
        val pieces = mutableListOf<String>()
        if (quickInsightScreenshotPath != null) {
            pieces += "已附图片"
        }
        if (isQuickInsightRecording) {
            pieces += "语音录制中"
        } else if (quickInsightAudioPath != null) {
            pieces += "已附语音 ${formatDuration(quickInsightAudioDurationSeconds)}"
        }
        return if (pieces.isEmpty()) {
            "可选附加图片或语音，保存后直接进入灵感页"
        } else {
            pieces.joinToString(" · ")
        }
    }

    private fun startQuickInsightImagePicker() {
        if (isSavingQuickInsight) {
            Toast.makeText(this, "正在保存灵感，请稍后", Toast.LENGTH_SHORT).show()
            return
        }
        setQuickInsightOverlayVisible(false)
        launchQuickInsightAssistActivity(QuickInsightAssistActivity.MODE_PICK_IMAGE)
    }

    private fun attachScreenshotToQuickInsight(path: String) {
        deleteFileIfExists(quickInsightScreenshotPath)
        quickInsightScreenshotPath = path
        updateQuickInsightUi()
        Toast.makeText(this, "已附加图片", Toast.LENGTH_SHORT).show()
    }

    private fun startQuickInsightRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            setQuickInsightOverlayVisible(false)
            launchQuickInsightAssistActivity(QuickInsightAssistActivity.MODE_REQUEST_AUDIO_PERMISSION)
            return
        }
        startQuickInsightRecordingInternal()
    }

    private fun startQuickInsightRecordingInternal() {
        if (isSavingQuickInsight) {
            Toast.makeText(this, "正在保存灵感，请稍后", Toast.LENGTH_SHORT).show()
            return
        }
        deleteFileIfExists(quickInsightAudioPath)
        quickInsightAudioPath = null
        quickInsightAudioDurationSeconds = 0
        val filePath = quickInsightRecorder.startRecording()
        if (filePath == null) {
            Toast.makeText(this, "无法启动录音", Toast.LENGTH_SHORT).show()
            return
        }
        isQuickInsightRecording = true
        updateQuickInsightUi()
        Toast.makeText(this, "开始录音，再点一次结束", Toast.LENGTH_SHORT).show()
    }

    private fun finalizeQuickInsightRecording(showToast: Boolean = true): Boolean {
        val result = quickInsightRecorder.stopRecording()
        isQuickInsightRecording = false
        if (result == null) {
            quickInsightAudioPath = null
            quickInsightAudioDurationSeconds = 0
            updateQuickInsightUi()
            if (showToast) {
                Toast.makeText(this, "录音保存失败", Toast.LENGTH_SHORT).show()
            }
            return false
        }

        val (tempPath, duration) = result
        val persistedPath = persistQuickInsightAudioFile(tempPath).getOrElse {
            deleteFileIfExists(tempPath)
            quickInsightAudioPath = null
            quickInsightAudioDurationSeconds = 0
            updateQuickInsightUi()
            if (showToast) {
                Toast.makeText(this, "保存录音附件失败: ${it.message}", Toast.LENGTH_SHORT).show()
            }
            return false
        }
        quickInsightAudioPath = persistedPath
        quickInsightAudioDurationSeconds = duration
        updateQuickInsightUi()
        if (showToast) {
            Toast.makeText(this, "已附加语音", Toast.LENGTH_SHORT).show()
        }
        return true
    }

    private fun attachImageUriToQuickInsight(uriString: String) {
        serviceScope.launch {
            val result = copyQuickInsightUriToLocalFile(Uri.parse(uriString))
            withContext(Dispatchers.Main) {
                result.fold(
                    onSuccess = { path ->
                        attachScreenshotToQuickInsight(path)
                    },
                    onFailure = { error ->
                        Toast.makeText(
                            this@FloatingWindowService,
                            "附加图片失败: ${error.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            }
        }
    }

    private fun copyQuickInsightUriToLocalFile(uri: Uri): Result<String> = runCatching {
        val targetDir = java.io.File(filesDir, "quick-insight/images").apply { mkdirs() }
        val extension = contentResolver.getType(uri)
            ?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
            ?.takeIf { it.isNotBlank() }
            ?: "jpg"
        val targetFile = java.io.File(targetDir, "insight_${System.currentTimeMillis()}.$extension")

        contentResolver.openInputStream(uri)?.use { input ->
            java.io.FileOutputStream(targetFile).use { output ->
                input.copyTo(output)
            }
        } ?: error("无法读取所选图片")

        targetFile.absolutePath
    }

    private fun launchQuickInsightAssistActivity(mode: String) {
        val intent = Intent(this, QuickInsightAssistActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                    Intent.FLAG_ACTIVITY_NO_HISTORY or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            )
            putExtra(QuickInsightAssistActivity.EXTRA_MODE, mode)
        }
        startActivity(intent)
    }

    private fun persistQuickInsightAudioFile(tempPath: String): Result<String> = runCatching {
        val sourceFile = java.io.File(tempPath)
        check(sourceFile.exists()) { "录音文件不存在" }

        val targetDir = java.io.File(filesDir, "quick-insight/audio").apply { mkdirs() }
        val targetFile = java.io.File(targetDir, "insight_voice_${System.currentTimeMillis()}.m4a")

        sourceFile.inputStream().use { input ->
            targetFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        sourceFile.delete()
        targetFile.absolutePath
    }

    private fun saveQuickInsightFromOverlay() {
        if (isSavingQuickInsight) {
            return
        }

        val titleInput = quickInsightTitleInput?.text?.toString()?.trim().orEmpty()
        val bodyInput = quickInsightBodyInput?.text?.toString()?.trim().orEmpty()

        if (titleInput.isBlank() &&
            bodyInput.isBlank() &&
            quickInsightScreenshotPath.isNullOrBlank() &&
            quickInsightAudioPath.isNullOrBlank()
        ) {
            Toast.makeText(this, "请至少输入正文，或附加截图/语音", Toast.LENGTH_SHORT).show()
            return
        }

        if (isQuickInsightRecording && !finalizeQuickInsightRecording()) {
            return
        }

        val localImagePath = quickInsightScreenshotPath
        val localAudioPath = quickInsightAudioPath
        val localAudioDuration = quickInsightAudioDurationSeconds
        val finalTitle = resolveQuickInsightTitle(titleInput, bodyInput, localImagePath, localAudioPath)
        val summary = buildQuickInsightSummary(finalTitle, bodyInput, localImagePath, localAudioPath)
        val contentMd = buildQuickInsightMarkdown(bodyInput, localImagePath, localAudioPath)
        val metaJson = com.google.gson.Gson().toJson(
            buildMap<String, Any?> {
                put("source", "灵感")
                put("body", bodyInput)
                put("image_uri", localImagePath)
                put("audio_uri", localAudioPath)
                put("audio_duration", localAudioDuration)
                put("has_image", !localImagePath.isNullOrBlank())
                put("has_audio", !localAudioPath.isNullOrBlank())
            }
        )

        isSavingQuickInsight = true
        updateQuickInsightUi()
        hideQuickInsightWindow(deleteDraft = false)

        mainHandler.post {
            floatingBall?.isProcessing = true
            Toast.makeText(this, "正在保存灵感...", Toast.LENGTH_SHORT).show()
        }

        serviceScope.launch {
            val result = itemRepository.createFullItem(
                title = finalTitle,
                summary = summary,
                contentMd = contentMd,
                originUrl = localImagePath,
                type = ItemType.INSIGHT,
                status = ItemStatus.DONE,
                metaJson = metaJson,
                tags = emptyList(),
                audioUrl = localAudioPath
            )

            withContext(Dispatchers.Main) {
                floatingBall?.isProcessing = false
                isSavingQuickInsight = false
                result.fold(
                    onSuccess = { item ->
                        val broadcastIntent = Intent(ACTION_ITEM_ADDED).apply {
                            putExtra(EXTRA_ITEM_ID, item.id)
                            putExtra(EXTRA_ITEM_TYPE, ItemType.INSIGHT.name)
                            setPackage(packageName)
                        }
                        sendBroadcast(broadcastIntent)
                        clearQuickInsightDraftStatePreservingFiles()
                        showResultOverlay("已存入灵感", item.title)
                    },
                    onFailure = { error ->
                        deleteFileIfExists(localImagePath)
                        deleteFileIfExists(localAudioPath)
                        Toast.makeText(
                            this@FloatingWindowService,
                            "保存灵感失败: ${error.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            }
        }
    }

    private fun resolveQuickInsightTitle(
        title: String,
        body: String,
        imagePath: String?,
        audioPath: String?
    ): String {
        if (title.isNotBlank()) return title
        if (body.isNotBlank()) {
            val singleLine = body.replace('\n', ' ').trim()
            return if (singleLine.length > 22) "${singleLine.take(22)}..." else singleLine
        }
        return when {
            !imagePath.isNullOrBlank() && !audioPath.isNullOrBlank() -> "图片语音灵感"
            !imagePath.isNullOrBlank() -> "图片灵感"
            !audioPath.isNullOrBlank() -> "语音灵感"
            else -> "快速灵感"
        }
    }

    private fun buildQuickInsightSummary(
        title: String,
        body: String,
        imagePath: String?,
        audioPath: String?
    ): String {
        if (body.isNotBlank()) {
            return body.replace('\n', ' ').trim().take(120)
        }
        val parts = mutableListOf<String>()
        if (!imagePath.isNullOrBlank()) parts += "含图片"
        if (!audioPath.isNullOrBlank()) parts += "含语音"
        return if (parts.isEmpty()) title else parts.joinToString(" · ")
    }

    private fun buildQuickInsightMarkdown(
        body: String,
        imagePath: String?,
        audioPath: String?
    ): String {
        if (body.isNotBlank()) return body
        return buildString {
            appendLine("（快速灵感未填写正文）")
            if (!imagePath.isNullOrBlank()) {
                appendLine()
                appendLine("- 已附图片")
            }
            if (!audioPath.isNullOrBlank()) {
                appendLine("- 已附语音")
            }
        }.trim()
    }

    private fun formatDuration(seconds: Int): String {
        val safeSeconds = seconds.coerceAtLeast(0)
        val minutes = safeSeconds / 60
        val remain = safeSeconds % 60
        return if (minutes > 0) {
            "${minutes}m ${remain}s"
        } else {
            "${remain}s"
        }
    }

    private fun deleteFileIfExists(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching { java.io.File(path).takeIf { it.exists() }?.delete() }
    }

    // ==================== 结果展示 ====================

    private fun showResultOverlay(title: String, summary: String) {
        if (resultView != null) hideResultOverlay()

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 32)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#CC10B981")) // 半透明绿色
                cornerRadius = 48f
            }
        }

        val titleView = TextView(this).apply {
            text = "解析完成"
            textSize = 14f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        container.addView(titleView)

        val contentView = TextView(this).apply {
            text = title.take(20) + "..."
            textSize = 16f
            setTextColor(Color.WHITE)
            setPadding(0, 8, 0, 0)
        }
        container.addView(contentView)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 200 // 位于顶部下方
            windowAnimations = android.R.style.Animation_Toast
        }

        resultView = container
        try {
            windowManager.addView(resultView, params)
            // 3秒后自动消失
            mainHandler.postDelayed({ hideResultOverlay() }, 3000)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun hideResultOverlay() {
        resultView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        resultView = null
    }

    // ==================== 业务逻辑处理 ====================

    private fun handleCapturedImagePath(path: String) {
        when (captureResultTarget) {
            CaptureResultTarget.QUICK_INSIGHT_ATTACHMENT -> {
                captureResultTarget = CaptureResultTarget.OCR_IMPORT
                attachScreenshotToQuickInsight(path)
            }
            CaptureResultTarget.OCR_IMPORT -> handleQueuedScreenshot(path)
        }
    }

    private fun handleScreenshot(path: String) {
        mainHandler.post {
            floatingBall?.isProcessing = true
            Toast.makeText(this, "正在识别截图内容...", Toast.LENGTH_SHORT).show()
        }

        serviceScope.launch {
            try {
                val bitmapResult = OcrBitmapLoader.loadBitmap(path)
                val ocrResult = bitmapResult.fold(
                    onSuccess = { bitmap ->
                        try {
                            aiService.recognizeImageStructured(bitmap).getOrElse { error ->
                                android.util.Log.e("FloatingWindow", "OCR failed for screenshot: $path", error)
                                null
                            }
                        } catch (oom: OutOfMemoryError) {
                            android.util.Log.e("FloatingWindow", "OCR processing ran out of memory for: $path", oom)
                            null
                        } finally {
                            OcrBitmapLoader.recycle(bitmap)
                        }
                    },
                    onFailure = { error ->
                        android.util.Log.e("FloatingWindow", "Failed to load screenshot for OCR: $path", error)
                        null
                    }
                )

                val title = ocrResult?.title?.takeIf { it.isNotBlank() } ?: "截图已保存"
                val summary = ocrResult?.content?.takeIf { it.isNotBlank() }
                    ?: "已采集图片，OCR 暂未识别出正文"

                val saveResult = itemRepository.createImageItem(path, summary)

                withContext(Dispatchers.Main) {
                    floatingBall?.isProcessing = false
                    if (saveResult.isFailure) {
                        Toast.makeText(
                            this@FloatingWindowService,
                            "图片已采集，但保存失败: ${saveResult.exceptionOrNull()?.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        showResultOverlay(if (ocrResult != null) "识别完成" else "截图已保存", title)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    floatingBall?.isProcessing = false
                    Toast.makeText(this@FloatingWindowService, "识别失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 用于存储解析结果，供后续分类选择使用
    private fun handleScreenshotV2(path: String) {
        mainHandler.post {
            floatingBall?.isProcessing = true
            Toast.makeText(this, "正在识别截图并整理内容...", Toast.LENGTH_SHORT).show()
        }

        serviceScope.launch {
            try {
                val saveResult = imageScanImportService.importFromLocalPaths(
                    imagePaths = listOf(path),
                    captureMode = "screenshot"
                )

                withContext(Dispatchers.Main) {
                    floatingBall?.isProcessing = false
                    saveResult.fold(
                        onSuccess = { item ->
                            showResultOverlay("识别完成", item.title)
                        },
                        onFailure = { error ->
                            itemRepository.createImageItem(path, "已采集图片，OCR 暂未识别出正文")
                            Toast.makeText(
                                this@FloatingWindowService,
                                "截图已保存，但整理失败: ${error.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    floatingBall?.isProcessing = false
                    itemRepository.createImageItem(path, "已采集图片，OCR 暂未识别出正文")
                    Toast.makeText(this@FloatingWindowService, "识别失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun handleQueuedScreenshot(path: String) {
        mainHandler.post {
            floatingBall?.isProcessing = true
            Toast.makeText(this, "正在保存截图并创建条目...", Toast.LENGTH_SHORT).show()
        }

        serviceScope.launch {
            try {
                val queueResult = imageScanImportService.queueLocalPathImport(
                    imagePaths = listOf(path),
                    selectedType = ItemType.ARTICLE,
                    projectId = null,
                    projectName = null,
                    captureMode = "screenshot"
                )

                queueResult.fold(
                    onSuccess = { queuedImport ->
                        withContext(Dispatchers.Main) {
                            floatingBall?.isProcessing = false
                            val broadcastIntent = Intent(ACTION_ITEM_ADDED).apply {
                                putExtra(EXTRA_ITEM_ID, queuedImport.item.id)
                                putExtra(EXTRA_ITEM_TYPE, ItemType.ARTICLE.name)
                                setPackage(packageName)
                            }
                            sendBroadcast(broadcastIntent)
                            showResultOverlay("已加入资料", "后台解析中")
                        }

                        val processResult = imageScanImportService.processQueuedLocalPathImport(queuedImport)
                        withContext(Dispatchers.Main) {
                            processResult.fold(
                                onSuccess = { item ->
                                    showResultOverlay("识别完成", item.title)
                                },
                                onFailure = { error ->
                                    Toast.makeText(
                                        this@FloatingWindowService,
                                        "截图已加入资料，但解析失败: ${error.message}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            )
                        }
                    },
                    onFailure = { error ->
                        withContext(Dispatchers.Main) {
                            floatingBall?.isProcessing = false
                            Toast.makeText(
                                this@FloatingWindowService,
                                "截图保存失败: ${error.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    floatingBall?.isProcessing = false
                    Toast.makeText(this@FloatingWindowService, "识别失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private var pendingParseResult: FullLinkParseResult? = null
    
    /**
     * 处理链接 - 完整工作流
     * 1. 显示处理状态
     * 2. AI 解析链接内容
     * 3. 显示解析结果预览和分类选择
     * 4. 用户选择分类后入库
     * 5. 同步更新数据库
     */
    private fun handleLink(link: String) {
        mainHandler.post {
            floatingBall?.isProcessing = true
            Toast.makeText(this, "请选择分类，链接将进入后台解析", Toast.LENGTH_SHORT).show()
        }

        try {
            val draft = aiService.createQuickLinkDraft(link)
            pendingParseResult = draft
            floatingBall?.isProcessing = false
            showCategorySelectionDialog(draft)
        } catch (e: Exception) {
            floatingBall?.isProcessing = false
            android.util.Log.e("FloatingWindow", "Failed to prepare quick link draft: ${e.message}", e)
            Toast.makeText(this, "无法处理该链接", Toast.LENGTH_SHORT).show()
        }
    }
    
    // 分类选择对话框视图
    private var categoryDialogView: View? = null
    
    /**
     * 显示分类选择对话框
     * 展示解析结果预览，让用户选择/确认分类
     */
    private fun showCategorySelectionDialog(parseResult: FullLinkParseResult) {
        if (categoryDialogView != null) hideCategoryDialog()
        
        val dpToPx = { dp: Float -> 
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics).toInt() 
        }
        
        // 主容器
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(24f), dpToPx(24f), dpToPx(24f), dpToPx(24f))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F01F1F24"))
                cornerRadius = dpToPx(24f).toFloat()
                setStroke(1, Color.parseColor("#33FFFFFF"))
            }
        }
        
        // 标题
        val titleView = TextView(this).apply {
            text = "✨ 解析完成"
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dpToPx(16f))
        }
        container.addView(titleView)
        
        // 内容预览区域
        val previewContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#20FFFFFF"))
                cornerRadius = dpToPx(16f).toFloat()
            }
            setPadding(dpToPx(16f), dpToPx(16f), dpToPx(16f), dpToPx(16f))
        }
        
        // 解析的标题
        val parsedTitle = TextView(this).apply {
            text = parseResult.title
            textSize = 16f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            maxLines = 2
        }
        previewContainer.addView(parsedTitle)

        val indexLines = buildList {
            parseResult.authors?.takeIf { it.isNotBlank() }?.let { add("作者: $it") }
            (parseResult.conference ?: parseResult.platform)
                ?.takeIf { it.isNotBlank() }
                ?.let { add("来源: $it") }
            parseResult.year?.takeIf { it.isNotBlank() }?.let { add("年份: $it") }
            parseResult.identifier?.takeIf { it.isNotBlank() }?.let { add("标识符: $it") }
        }
        if (indexLines.isNotEmpty()) {
            val indexInfo = TextView(this).apply {
                text = indexLines.joinToString("\n")
                textSize = 12f
                setTextColor(Color.parseColor("#E5E7EB"))
                setPadding(0, dpToPx(8f), 0, 0)
                maxLines = 4
            }
            previewContainer.addView(indexInfo)
        }
        
        // 来源信息
        val sourceInfo = TextView(this).apply {
            text = "📍 来源类型: ${parseResult.source.uppercase()} | 默认分类: ${parseResult.toItemType().name}"
            textSize = 12f
            setTextColor(Color.parseColor("#9CA3AF"))
            setPadding(0, dpToPx(8f), 0, 0)
        }
        previewContainer.addView(sourceInfo)
        
        // 摘要预览
        val summaryPreview = TextView(this).apply {
            val shortSummary = parseResult.summaryShort?.takeIf { it.isNotBlank() } ?: parseResult.summary
            text = shortSummary.take(100) + if (shortSummary.length > 100) "..." else ""
            textSize = 13f
            setTextColor(Color.parseColor("#D1D5DB"))
            setPadding(0, dpToPx(8f), 0, 0)
            maxLines = 3
        }
        previewContainer.addView(summaryPreview)

        val tagPreviewValues = (parseResult.domainTags + parseResult.keywords + parseResult.methodTags)
            .distinct()
            .take(6)
        if (tagPreviewValues.isNotEmpty()) {
            val tagPreview = TextView(this).apply {
                text = "关键词: ${tagPreviewValues.joinToString(" · ")}"
                textSize = 12f
                setTextColor(Color.parseColor("#93C5FD"))
                setPadding(0, dpToPx(8f), 0, 0)
                maxLines = 2
            }
            previewContainer.addView(tagPreview)
        }
        
        container.addView(previewContainer, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dpToPx(20f) })
        
        // 分类选择标题
        val categoryLabel = TextView(this).apply {
            text = "选择分类"
            textSize = 14f
            setTextColor(Color.parseColor("#9CA3AF"))
            setPadding(0, 0, 0, dpToPx(12f))
        }
        container.addView(categoryLabel)
        
        // 分类按钮容器
        val categoryContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        
        // 当前选中的分类
        var selectedType = parseResult.toItemType()
        
        // 分类选项数据
        data class CategoryOption(val type: ItemType, val emoji: String, val label: String, val color: String)
        val categories = listOf(
            CategoryOption(ItemType.PAPER, "📄", "论文", "#3B82F6"),
            CategoryOption(ItemType.ARTICLE, "📰", "资料", "#6366F1"),
            CategoryOption(ItemType.COMPETITION, "🏆", "竞赛", "#F59E0B"),
            CategoryOption(ItemType.INSIGHT, "💡", "动态", "#10B981")
        )
        
        val categoryButtons = mutableListOf<LinearLayout>()
        
        categories.forEach { category ->
            val isSelected = category.type == selectedType
            val btn = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dpToPx(16f), dpToPx(12f), dpToPx(16f), dpToPx(12f))
                background = GradientDrawable().apply {
                    setColor(if (isSelected) Color.parseColor(category.color + "40") else Color.parseColor("#20FFFFFF"))
                    cornerRadius = dpToPx(12f).toFloat()
                    if (isSelected) setStroke(2, Color.parseColor(category.color))
                }
                
                addView(TextView(this@FloatingWindowService).apply {
                    text = category.emoji
                    textSize = 24f
                    gravity = Gravity.CENTER
                })
                
                addView(TextView(this@FloatingWindowService).apply {
                    text = category.label
                    textSize = 12f
                    setTextColor(if (isSelected) Color.parseColor(category.color) else Color.WHITE)
                    gravity = Gravity.CENTER
                    setPadding(0, dpToPx(4f), 0, 0)
                })
            }
            
            btn.setOnClickListener {
                selectedType = category.type
                // 更新所有按钮状态
                categoryButtons.forEachIndexed { index, button ->
                    val cat = categories[index]
                    val sel = cat.type == selectedType
                    button.background = GradientDrawable().apply {
                        setColor(if (sel) Color.parseColor(cat.color + "40") else Color.parseColor("#20FFFFFF"))
                        cornerRadius = dpToPx(12f).toFloat()
                        if (sel) setStroke(2, Color.parseColor(cat.color))
                    }
                    (button.getChildAt(1) as? TextView)?.setTextColor(
                        if (sel) Color.parseColor(cat.color) else Color.WHITE
                    )
                }
            }
            
            categoryButtons.add(btn)
            categoryContainer.addView(btn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (categories.indexOf(category) < categories.size - 1) marginEnd = dpToPx(8f)
            })
        }
        
        container.addView(categoryContainer, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dpToPx(16f) })
        
        // ===== 来源选择 =====
        val sourceLabel = TextView(this).apply {
            text = "选择来源"
            textSize = 14f
            setTextColor(Color.parseColor("#9CA3AF"))
            setPadding(0, 0, 0, dpToPx(12f))
        }
        container.addView(sourceLabel)
        
        // 来源选项数据
        data class SourceOption(val id: String, val label: String, val emoji: String)
        val sourceOptions = listOf(
            SourceOption("arxiv", "arXiv", "📚"),
            SourceOption("doi", "DOI", "🔗"),
            SourceOption("wechat", "微信公众号", "📱"),
            SourceOption("zhihu", "知乎", "💬"),
            SourceOption("web", "网页", "🌐"),
            SourceOption("custom", "自定义", "✏️")
        )
        
        // 根据解析结果预选来源
        var selectedSource = when {
            parseResult.source.contains("arxiv", ignoreCase = true) -> "arxiv"
            parseResult.source.contains("doi", ignoreCase = true) -> "doi"
            parseResult.source.contains("wechat", ignoreCase = true) -> "wechat"
            parseResult.source.contains("zhihu", ignoreCase = true) -> "zhihu"
            else -> "web"
        }
        var customSourceText = ""
        
        val sourceContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        
        val sourceButtons = mutableListOf<LinearLayout>()
        
        sourceOptions.forEach { source ->
            val isSelected = source.id == selectedSource
            val btn = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dpToPx(12f), dpToPx(10f), dpToPx(12f), dpToPx(10f))
                background = GradientDrawable().apply {
                    setColor(if (isSelected) Color.parseColor("#6366F140") else Color.parseColor("#20FFFFFF"))
                    cornerRadius = dpToPx(10f).toFloat()
                    if (isSelected) setStroke(2, Color.parseColor("#6366F1"))
                }
                
                addView(TextView(this@FloatingWindowService).apply {
                    text = source.emoji
                    textSize = 18f
                    gravity = Gravity.CENTER
                })
                
                addView(TextView(this@FloatingWindowService).apply {
                    text = source.label
                    textSize = 10f
                    setTextColor(if (isSelected) Color.parseColor("#6366F1") else Color.WHITE)
                    gravity = Gravity.CENTER
                    setPadding(0, dpToPx(2f), 0, 0)
                })
            }
            
            btn.setOnClickListener {
                selectedSource = source.id
                // 更新所有按钮状态
                sourceButtons.forEachIndexed { index, button ->
                    val src = sourceOptions[index]
                    val sel = src.id == selectedSource
                    button.background = GradientDrawable().apply {
                        setColor(if (sel) Color.parseColor("#6366F140") else Color.parseColor("#20FFFFFF"))
                        cornerRadius = dpToPx(10f).toFloat()
                        if (sel) setStroke(2, Color.parseColor("#6366F1"))
                    }
                    (button.getChildAt(1) as? TextView)?.setTextColor(
                        if (sel) Color.parseColor("#6366F1") else Color.WHITE
                    )
                }
                
                // 如果选择自定义，显示输入框
                if (source.id == "custom") {
                    showCustomSourceInput { input ->
                        customSourceText = input
                    }
                }
            }
            
            sourceButtons.add(btn)
            sourceContainer.addView(btn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (sourceOptions.indexOf(source) < sourceOptions.size - 1) marginEnd = dpToPx(6f)
            })
        }
        
        container.addView(sourceContainer, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dpToPx(24f) })
        
        // 操作按钮容器
        val buttonContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        
        // 取消按钮
        val cancelBtn = Button(this).apply {
            text = "取消"
            setTextColor(Color.WHITE)
            textSize = 14f
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#374151"))
                cornerRadius = dpToPx(12f).toFloat()
            }
            setOnClickListener {
                hideCategoryDialog()
                pendingParseResult = null
            }
        }
        
        // 添加按钮
        val confirmBtn = Button(this).apply {
            text = "添加到库"
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#10B981"))
                cornerRadius = dpToPx(12f).toFloat()
            }
            setOnClickListener {
                hideCategoryDialog()
                // 使用选中的分类和来源进行入库
                val finalSource = if (selectedSource == "custom" && customSourceText.isNotEmpty()) {
                    customSourceText
                } else {
                    when (selectedSource) {
                        "wechat" -> "微信公众号"
                        "zhihu" -> "知乎"
                        "web" -> "网页"
                        else -> parseResult.source
                    }
                }
                saveItemToDatabase(parseResult, selectedType, finalSource)
            }
        }
        
        buttonContainer.addView(cancelBtn, LinearLayout.LayoutParams(0, dpToPx(48f), 1f).apply {
            marginEnd = dpToPx(12f)
        })
        buttonContainer.addView(confirmBtn, LinearLayout.LayoutParams(0, dpToPx(48f), 1.5f))
        
        container.addView(buttonContainer)
        
        // 窗口参数
        val params = WindowManager.LayoutParams(
            dpToPx(320f),
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_DIM_BEHIND,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            dimAmount = 0.6f
        }
        
        categoryDialogView = container
        try {
            windowManager.addView(categoryDialogView, params)
        } catch (e: Exception) {
            android.util.Log.e("FloatingWindow", "Failed to show category dialog: ${e.message}", e)
        }
    }
    
    private fun hideCategoryDialog() {
        categoryDialogView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        categoryDialogView = null
    }

    private fun needsCompetitionFallback(parseResult: FullLinkParseResult): Boolean {
        return CompetitionImportDecider.needsManualFallback(parseResult)
    }

    private fun showCompetitionFallbackWindow(
        parseResult: FullLinkParseResult,
        onResult: (FullLinkParseResult) -> Unit,
        onCancel: () -> Unit
    ) {
        if (competitionInputView != null) return

        val dpToPx = { dp: Float ->
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics).toInt()
        }

        val padding16 = dpToPx(16f)
        val padding12 = dpToPx(12f)
        val corner12 = dpToPx(12f).toFloat()

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding16, padding16, padding16, padding16)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1F2937"))
                cornerRadius = corner12
            }
            layoutParams = FrameLayout.LayoutParams(
                dpToPx(320f),
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val titleView = TextView(this).apply {
            text = "补全竞赛关键时间"
            textSize = 18f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, padding12)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        container.addView(titleView)

        val registerDeadlineInput = EditText(this).apply {
            hint = "报名截止 (YYYY-MM-DD)"
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            setPadding(padding12, padding12, padding12, padding12)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#33FFFFFF"))
                cornerRadius = corner12
            }
        }
        container.addView(registerDeadlineInput)

        val submitDeadlineInput = EditText(this).apply {
            hint = "提交截止 (YYYY-MM-DD)"
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            setPadding(padding12, padding12, padding12, padding12)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#33FFFFFF"))
                cornerRadius = corner12
            }
        }
        container.addView(submitDeadlineInput)

        val resultDateInput = EditText(this).apply {
            hint = "结果公布 (YYYY-MM-DD)"
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            setPadding(padding12, padding12, padding12, padding12)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#33FFFFFF"))
                cornerRadius = corner12
            }
        }
        container.addView(resultDateInput)

        val websiteInput = EditText(this).apply {
            hint = "官网链接"
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            setPadding(padding12, padding12, padding12, padding12)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#33FFFFFF"))
                cornerRadius = corner12
            }
        }
        container.addView(websiteInput)

        val registrationInput = EditText(this).apply {
            hint = "报名链接"
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            setPadding(padding12, padding12, padding12, padding12)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#33FFFFFF"))
                cornerRadius = corner12
            }
        }
        container.addView(registrationInput)

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }

        val cancelButton = Button(this).apply {
            text = "取消"
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                cornerRadius = corner12
            }
            setOnClickListener {
                hideCompetitionFallbackWindow()
                onCancel()
            }
        }
        val confirmButton = Button(this).apply {
            text = "保存"
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#10B981"))
                cornerRadius = corner12
            }
            setOnClickListener {
                val timeline = mutableListOf<CompetitionTimelineNode>()
                registerDeadlineInput.text.toString().trim().takeIf { it.isNotBlank() }?.let {
                    timeline += CompetitionTimelineNode("报名截止", it)
                }
                submitDeadlineInput.text.toString().trim().takeIf { it.isNotBlank() }?.let {
                    timeline += CompetitionTimelineNode("提交截止", it)
                }
                resultDateInput.text.toString().trim().takeIf { it.isNotBlank() }?.let {
                    timeline += CompetitionTimelineNode("结果公布", it)
                }

                val updated = parseResult.copy(
                    website = websiteInput.text.toString().trim().ifBlank { parseResult.website },
                    registrationUrl = registrationInput.text.toString().trim().ifBlank { parseResult.registrationUrl },
                    timeline = if (timeline.isEmpty()) parseResult.timeline else timeline
                )
                hideCompetitionFallbackWindow()
                onResult(updated)
            }
        }

        buttonRow.addView(cancelButton)
        buttonRow.addView(confirmButton)
        container.addView(buttonRow)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_DIM_BEHIND,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            dimAmount = 0.6f
        }

        competitionInputView = container
        try {
            windowManager.addView(competitionInputView, params)
        } catch (e: Exception) {
            competitionInputView = null
            android.util.Log.e("FloatingWindow", "Failed to show competition fallback: ${e.message}", e)
        }
    }

    private fun hideCompetitionFallbackWindow() {
        competitionInputView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        competitionInputView = null
    }

    private suspend fun resolveCompetitionFallbackIfNeeded(
        parseResult: FullLinkParseResult,
        selectedType: ItemType
    ): FullLinkParseResult {
        if (selectedType != ItemType.COMPETITION) return parseResult
        if (!needsCompetitionFallback(parseResult)) return parseResult

        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                showCompetitionFallbackWindow(
                    parseResult = parseResult,
                    onResult = { updated ->
                        if (continuation.isActive) continuation.resume(updated)
                    },
                    onCancel = {
                        if (continuation.isActive) continuation.resume(parseResult)
                    }
                )

                continuation.invokeOnCancellation {
                    hideCompetitionFallbackWindow()
                }
            }
        }
    }
    
    /**
     * 构建 metaJson，包含作者和来源信息
     */
    private fun buildMetaJson(parseResult: FullLinkParseResult, source: String): String {
        val normalized = if (source.isNotEmpty() && source != parseResult.source) {
            parseResult.copy(source = source)
        } else {
            parseResult
        }
        return normalized.toMetaJson() ?: "{}"
    }

    /**
     * 显示自定义来源输入对话框
     */
    private fun showCustomSourceInput(onResult: (String) -> Unit) {
        mainHandler.post {
            // 定义 dp 转换函数
            val dpToPx = { dp: Float -> 
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics).toInt() 
            }
            
            // 预计算 dp 值
            val padding24 = dpToPx(24f)
            val padding20 = dpToPx(20f)
            val padding16 = dpToPx(16f)
            val padding12 = dpToPx(12f)
            val padding8 = dpToPx(8f)
            val height40 = dpToPx(40f)
            val width280 = dpToPx(280f)
            val corner16 = dpToPx(16f).toFloat()
            val corner8 = dpToPx(8f).toFloat()
            
            val containerBg = GradientDrawable().apply {
                setColor(Color.parseColor("#1F2937"))
                cornerRadius = corner16
            }
            
            val container = LinearLayout(this@FloatingWindowService).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(padding24, padding20, padding24, padding20)
                background = containerBg
            }
            
            // 标题
            val titleView = TextView(this@FloatingWindowService).apply {
                text = "输入自定义来源"
                textSize = 16f
                setTextColor(Color.WHITE)
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setPadding(0, 0, 0, padding16)
            }
            container.addView(titleView)
            
            // 输入框背景
            val editBg = GradientDrawable().apply {
                setColor(Color.parseColor("#374151"))
                cornerRadius = corner8
            }
            
            val editText = EditText(this@FloatingWindowService).apply {
                hint = "例如: 技术博客、论坛..."
                setHintTextColor(Color.parseColor("#6B7280"))
                setTextColor(Color.WHITE)
                textSize = 14f
                background = editBg
                setPadding(padding12, padding12, padding12, padding12)
            }
            container.addView(editText, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = padding16 })
            
            // 按钮容器
            val btnContainer = LinearLayout(this@FloatingWindowService).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END
            }
            
            val cancelBg = GradientDrawable().apply {
                setColor(Color.parseColor("#374151"))
                cornerRadius = corner8
            }
            
            val cancelBtn = Button(this@FloatingWindowService).apply {
                text = "取消"
                setTextColor(Color.WHITE)
                textSize = 13f
                background = cancelBg
            }
            
            val confirmBg = GradientDrawable().apply {
                setColor(Color.parseColor("#6366F1"))
                cornerRadius = corner8
            }
            
            val confirmBtn = Button(this@FloatingWindowService).apply {
                text = "确定"
                setTextColor(Color.WHITE)
                textSize = 13f
                background = confirmBg
            }
            
            btnContainer.addView(cancelBtn, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                height40
            ).apply { marginEnd = padding8 })
            btnContainer.addView(confirmBtn, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                height40
            ))
            
            container.addView(btnContainer)
            
            // 窗口参数
            val params = WindowManager.LayoutParams(
                width280,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_DIM_BEHIND,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER
                dimAmount = 0.6f
            }
            
            var dialogView: View? = container
            
            cancelBtn.setOnClickListener {
                dialogView?.let { windowManager.removeView(it) }
                dialogView = null
            }
            
            confirmBtn.setOnClickListener {
                val input = editText.text.toString().trim()
                if (input.isNotEmpty()) {
                    onResult(input)
                }
                dialogView?.let { windowManager.removeView(it) }
                dialogView = null
            }
            
            try {
                windowManager.addView(container, params)
            } catch (e: Exception) {
                android.util.Log.e("FloatingWindow", "显示自定义来源对话框失败: ${e.message}", e)
            }
        }
    }
    
    /**
     * 保存条目到数据库
     * 完成完整的入库流程：创建记录 -> 同步到远端 -> 更新本地 -> 跳转主页
     */
    private fun saveItemToDatabase(parseResult: FullLinkParseResult, selectedType: ItemType, selectedSource: String = "") {
        mainHandler.post {
            floatingBall?.isProcessing = true
            Toast.makeText(this, "正在加入${getTypeDisplayName(selectedType)}...", Toast.LENGTH_SHORT).show()
        }
        
        serviceScope.launch {
            try {
                val finalSource = if (selectedSource.isNotEmpty()) selectedSource else parseResult.source
                val parseStartedAt = System.currentTimeMillis()
                val placeholderMetaJson = buildProcessingMetaJson(
                    source = finalSource,
                    parseStartedAt = parseStartedAt,
                    feedback = "已加入${getTypeDisplayName(selectedType)}，正在后台解析"
                )
                val placeholderSummary = buildProcessingSummary(selectedType)
                val placeholderContent = buildProcessingMarkdown(parseResult.title, parseResult.originalUrl, selectedType)

                val createResult = itemRepository.createLocalPendingItem(
                    title = parseResult.title,
                    summary = placeholderSummary,
                    contentMd = placeholderContent,
                    originUrl = parseResult.originalUrl,
                    type = selectedType,
                    status = ItemStatus.PROCESSING,
                    metaJson = placeholderMetaJson,
                    note = null,
                    tags = parseResult.tags
                )
                
                withContext(Dispatchers.Main) {
                    floatingBall?.isProcessing = false
                    pendingParseResult = null
                    
                    createResult.onSuccess { item ->
                        android.util.Log.d("FloatingWindow", "✅ 占位条目已创建: id=${item.id}")
                        
                        val broadcastIntent = Intent(ACTION_ITEM_ADDED).apply {
                            putExtra(EXTRA_ITEM_ID, item.id)
                            putExtra(EXTRA_ITEM_TYPE, selectedType.name)
                            setPackage(packageName)
                        }
                        sendBroadcast(broadcastIntent)
                        showResultOverlay(
                            "已加入${getTypeDisplayName(selectedType)}",
                            "已留在当前页面，链接正在后台解析"
                        )

                        serviceScope.launch {
                            processLinkItemInBackground(
                                itemId = item.id,
                                originalLink = parseResult.originalUrl,
                                selectedType = selectedType,
                                selectedSource = finalSource,
                                parseStartedAt = parseStartedAt,
                                placeholderTitle = parseResult.title
                            )
                        }
                        
                    }.onFailure { error ->
                        android.util.Log.e("FloatingWindow", "❌ 保存失败: ${error.message}", error)
                        Toast.makeText(this@FloatingWindowService, "保存失败: ${error.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("FloatingWindow", "❌ 保存异常: ${e.message}", e)
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    floatingBall?.isProcessing = false
                    Toast.makeText(this@FloatingWindowService, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    /**
     * 打开主页面并跳转到对应的Tab
     */
    private fun openMainActivity(itemType: ItemType) {
        try {
            val intent = Intent(this, com.example.ai4research.MainActivity::class.java).apply {
                // 使用 SINGLE_TOP 避免重新创建 Activity，触发 onNewIntent
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                // 传递要显示的类型，让主页面知道跳转到哪个Tab
                putExtra("target_type", itemType.name)
            }
            startActivity(intent)
            android.util.Log.d("FloatingWindow", "已跳转到主页面，目标类型: ${itemType.name}")
        } catch (e: Exception) {
            android.util.Log.e("FloatingWindow", "跳转主页面失败: ${e.message}", e)
        }
    }
    
    /**
     * 显示成功提示
     */
    private fun showSuccessOverlay(title: String, type: ItemType) {
        val emoji = when (type) {
            ItemType.PAPER -> "📄"
            ItemType.ARTICLE -> "📰"
            ItemType.COMPETITION -> "🏆"
            ItemType.INSIGHT -> "💡"
            ItemType.VOICE -> "🎤"
        }
        val typeName = when (type) {
            ItemType.PAPER -> "论文"
            ItemType.ARTICLE -> "资料"
            ItemType.COMPETITION -> "竞赛"
            ItemType.INSIGHT -> "动态"
            ItemType.VOICE -> "语音"
        }
        showResultOverlay("$emoji 已添加到$typeName", title)
    }

    private fun setupClipboardListener() {
        clipboardManager?.addPrimaryClipChangedListener {
            val clip = clipboardManager?.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).text?.toString() ?: return@addPrimaryClipChangedListener
                
                // 检测链接
                val urlMatch = URL_PATTERN.find(text)
                val doiMatch = DOI_PATTERN.find(text)
                val arxivMatch = ARXIV_PATTERN.find(text)

                when {
                    arxivMatch != null -> {
                        detectedLink = "https://${arxivMatch.value}"
                        showLinkDetectedNotification("arXiv论文")
                    }
                    doiMatch != null -> {
                        detectedLink = "https://doi.org/${doiMatch.value}"
                        showLinkDetectedNotification("DOI文献")
                    }
                    urlMatch != null -> {
                        detectedLink = urlMatch.value
                        showLinkDetectedNotification("链接")
                    }
                }
            }
        }
    }

    private fun showLinkDetectedNotification(type: String) {
        mainHandler.post {
            floatingBall?.showLinkBadge = true
            Toast.makeText(this, "检测到${type}，点击悬浮球添加", Toast.LENGTH_SHORT).show()
        }
    }

    private fun triggerScreenshot() {
        if (MediaProjectionStore.hasPermission()) {
            captureScreenSafely(null)
        } else {
            requestScreenCapturePermission("full")
        }
    }

    private fun triggerRegionSelect() {
        if (MediaProjectionStore.hasPermission()) {
            showRegionSelectionOverlay()
        } else {
            requestScreenCapturePermission("region")
        }
    }

    private fun requestScreenCapturePermission(mode: String) {
        val intent = Intent(this, ScreenCaptureActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                    Intent.FLAG_ACTIVITY_NO_HISTORY or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            )
            putExtra(EXTRA_CAPTURE_MODE, mode)
        }
        startActivity(intent)
    }

    private fun resumePendingCapture(mode: String) {
        mainHandler.postDelayed(
            {
                if (mode == "region") {
                    showRegionSelectionOverlay()
                } else {
                    captureScreenSafely(null)
                }
            },
            250L
        )
    }

    private fun showRegionSelectionOverlay() {
        if (regionOverlay != null) return
        val overlay = RegionSelectionOverlayView(
            context = this,
            onConfirm = { rect ->
                hideRegionSelectionOverlay()
                captureScreenSafely(rect)
            },
            onCancel = {
                hideRegionSelectionOverlay()
                MediaProjectionStore.releaseProjection()
            }
        )
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
        regionOverlay = overlay
        try {
            windowManager.addView(overlay, params)
        } catch (e: Exception) {
            regionOverlay = null
            MediaProjectionStore.releaseProjection()
            e.printStackTrace()
        }
    }

    private fun hideRegionSelectionOverlay() {
        regionOverlay?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        regionOverlay = null
    }

    private fun captureScreen(region: Rect?) {
        if (isCapturing) return
        val captureMode = if (region == null) "full" else "region"
        val projection = MediaProjectionStore.getOrCreateProjection(mediaProjectionManager)
        try {
            startProjectionForeground()
        } catch (e: SecurityException) {
            android.util.Log.w(
                "FloatingWindow",
                "Failed to enter mediaProjection foreground state, requesting consent again",
                e
            )
            MediaProjectionStore.clear()
            requestScreenCapturePermission(if (region == null) "full" else "region")
            return
        }
        isCapturing = true
        mainHandler.post {
            Toast.makeText(this, "正在截图...", Toast.LENGTH_SHORT).show()
        }

        serviceScope.launch {
            var imageReader: ImageReader? = null
            var virtualDisplay: android.hardware.display.VirtualDisplay? = null
            val projectionCallback = object : MediaProjection.Callback() {
                override fun onStop() {
                    mainHandler.post {
                        if (isCapturing) {
                            isCapturing = false
                            Toast.makeText(
                                this@FloatingWindowService,
                                "截图会话已结束，请重新发起截图",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
            try {
                // Use WindowMetrics for API 30+ or fallback to deprecated DisplayMetrics
                val width: Int
                val height: Int
                val density: Int
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val windowMetrics = windowManager.currentWindowMetrics
                    val bounds = windowMetrics.bounds
                    width = bounds.width()
                    height = bounds.height()
                    density = resources.configuration.densityDpi
                } else {
                    val metrics = DisplayMetrics()
                    @Suppress("DEPRECATION")
                    windowManager.defaultDisplay.getRealMetrics(metrics)
                    width = metrics.widthPixels
                    height = metrics.heightPixels
                    density = metrics.densityDpi
                }

                projection?.registerCallback(projectionCallback, mainHandler)
                imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
                virtualDisplay = projection?.createVirtualDisplay(
                    "ScreenCapture",
                    width, height, density,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader.surface,
                    null, null
                )

                kotlinx.coroutines.delay(350)

                val image = imageReader.acquireLatestImage()
                if (image == null) {
                    withContext(Dispatchers.Main) {
                        isCapturing = false
                        Toast.makeText(this@FloatingWindowService, "截图失败", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val planes = image.planes
                val buffer = planes[0].buffer
                val pixelStride = planes[0].pixelStride
                val rowStride = planes[0].rowStride
                val rowPadding = rowStride - pixelStride * width

                val bitmap = Bitmap.createBitmap(
                    width + rowPadding / pixelStride,
                    height,
                    Bitmap.Config.ARGB_8888
                )
                bitmap.copyPixelsFromBuffer(buffer)
                image.close()

                val finalBitmap = if (region != null) {
                    val safeLeft = region.left.coerceIn(0, bitmap.width - 1)
                    val safeTop = region.top.coerceIn(0, bitmap.height - 1)
                    val safeWidth = region.width().coerceIn(1, bitmap.width - safeLeft)
                    val safeHeight = region.height().coerceIn(1, bitmap.height - safeTop)
                    Bitmap.createBitmap(bitmap, safeLeft, safeTop, safeWidth, safeHeight)
                } else {
                    Bitmap.createBitmap(bitmap, 0, 0, width, height)
                }

                val imagePath = saveBitmap(finalBitmap)

                withContext(Dispatchers.Main) {
                    isCapturing = false
                    if (imagePath != null) {
                        handleCapturedImagePath(imagePath)
                    } else {
                        captureResultTarget = CaptureResultTarget.OCR_IMPORT
                        Toast.makeText(this@FloatingWindowService, "保存截图失败", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isCapturing = false
                    captureResultTarget = CaptureResultTarget.OCR_IMPORT
                    Toast.makeText(this@FloatingWindowService, "截图失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } finally {
                projection?.unregisterCallback(projectionCallback)
                virtualDisplay?.release()
                imageReader?.close()
                MediaProjectionStore.releaseProjection()
                stopProjectionForeground()
            }
        }
    }

    private fun captureScreenSafely(region: Rect?) {
        if (isCapturing) return
        isCapturing = true
        val captureMode = if (region == null) "full" else "region"

        serviceScope.launch {
            var projection: MediaProjection? = null
            var imageReader: ImageReader? = null
            var virtualDisplay: android.hardware.display.VirtualDisplay? = null
            var projectionCallbackRegistered = false
            var overlayState: CaptureOverlayState? = null
            val projectionCallback = object : MediaProjection.Callback() {
                override fun onStop() {
                    mainHandler.post {
                        if (isCapturing) {
                            Toast.makeText(
                                this@FloatingWindowService,
                                "截图会话已结束，请重新发起截图",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }

            try {
                overlayState = withContext(Dispatchers.Main) {
                    val state = prepareCaptureUi()
                    Toast.makeText(this@FloatingWindowService, "正在截图...", Toast.LENGTH_SHORT).show()
                    state
                }

                try {
                    startProjectionForeground()
                } catch (e: SecurityException) {
                    android.util.Log.w(
                        "FloatingWindow",
                        "Failed to enter mediaProjection foreground state, requesting consent again",
                        e
                    )
                    MediaProjectionStore.clear()
                    withContext(Dispatchers.Main) {
                        requestScreenCapturePermission(captureMode)
                    }
                    return@launch
                }

                projection = MediaProjectionStore.getOrCreateProjection(mediaProjectionManager)
                if (projection == null) {
                    withContext(Dispatchers.Main) {
                        requestScreenCapturePermission(captureMode)
                    }
                    return@launch
                }

                val width: Int
                val height: Int
                val density: Int

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val windowMetrics = windowManager.maximumWindowMetrics
                    val bounds = windowMetrics.bounds
                    width = bounds.width()
                    height = bounds.height()
                    density = resources.configuration.densityDpi
                } else {
                    val metrics = DisplayMetrics()
                    @Suppress("DEPRECATION")
                    windowManager.defaultDisplay.getRealMetrics(metrics)
                    width = metrics.widthPixels
                    height = metrics.heightPixels
                    density = metrics.densityDpi
                }

                projection.registerCallback(projectionCallback, mainHandler)
                projectionCallbackRegistered = true
                imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
                virtualDisplay = projection.createVirtualDisplay(
                    "ScreenCapture",
                    width, height, density,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader.surface,
                    null, null
                )

                kotlinx.coroutines.delay(350)

                val image = imageReader.acquireLatestImage()
                if (image == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@FloatingWindowService, "截图失败", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val imagePath = try {
                    val planes = image.planes
                    val buffer = planes[0].buffer
                    val pixelStride = planes[0].pixelStride
                    val rowStride = planes[0].rowStride
                    val rowPadding = rowStride - pixelStride * width

                    val bitmap = Bitmap.createBitmap(
                        width + rowPadding / pixelStride,
                        height,
                        Bitmap.Config.ARGB_8888
                    )
                    bitmap.copyPixelsFromBuffer(buffer)

                    val finalBitmap = if (region != null) {
                        val safeLeft = region.left.coerceIn(0, bitmap.width - 1)
                        val safeTop = region.top.coerceIn(0, bitmap.height - 1)
                        val safeWidth = region.width().coerceIn(1, bitmap.width - safeLeft)
                        val safeHeight = region.height().coerceIn(1, bitmap.height - safeTop)
                        Bitmap.createBitmap(bitmap, safeLeft, safeTop, safeWidth, safeHeight)
                    } else {
                        Bitmap.createBitmap(bitmap, 0, 0, width, height)
                    }

                    try {
                        saveBitmap(finalBitmap)
                    } finally {
                        if (finalBitmap !== bitmap) {
                            finalBitmap.recycle()
                        }
                        bitmap.recycle()
                    }
                } finally {
                    image.close()
                }

                withContext(Dispatchers.Main) {
                    if (imagePath != null) {
                        handleCapturedImagePath(imagePath)
                    } else {
                        captureResultTarget = CaptureResultTarget.OCR_IMPORT
                        Toast.makeText(this@FloatingWindowService, "保存截图失败", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    captureResultTarget = CaptureResultTarget.OCR_IMPORT
                    Toast.makeText(this@FloatingWindowService, "截图失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } finally {
                if (projectionCallbackRegistered) {
                    runCatching { projection?.unregisterCallback(projectionCallback) }
                }
                virtualDisplay?.release()
                imageReader?.close()
                MediaProjectionStore.releaseProjection()
                stopProjectionForeground()
                withContext(Dispatchers.Main) {
                    restoreCaptureUi(overlayState)
                    isCapturing = false
                }
            }
        }
    }

    private suspend fun processLinkItemInBackground(
        itemId: String,
        originalLink: String,
        selectedType: ItemType,
        selectedSource: String,
        parseStartedAt: Long,
        placeholderTitle: String
    ) {
        updateProcessingFeedback(
            itemId = itemId,
            status = ItemStatus.PROCESSING,
            parseStartedAt = parseStartedAt,
            feedback = "正在抓取网页正文"
        )

        val progressJob = serviceScope.launch {
            kotlinx.coroutines.delay(4000)
            updateProcessingFeedback(
                itemId = itemId,
                status = ItemStatus.PROCESSING,
                parseStartedAt = parseStartedAt,
                feedback = "网页抓取较慢，正在继续解析"
            )

            kotlinx.coroutines.delay(5000)
            updateProcessingFeedback(
                itemId = itemId,
                status = ItemStatus.PROCESSING,
                parseStartedAt = parseStartedAt,
                feedback = "若正文抓取失败，将自动降级为快速链接解析"
            )
        }

        try {
            val result = aiService.parseFullLink(originalLink)
            val parseFinishedAt = System.currentTimeMillis()
            val parsed = result.getOrElse { error ->
                throw error
            }
            val resolvedParsed = resolveCompetitionFallbackIfNeeded(
                parseResult = parsed,
                selectedType = selectedType
            )

            progressJob.cancel()

            val mergedMetaJson = mergeParseTrackingMeta(
                baseMetaJson = buildMetaJson(resolvedParsed, selectedSource),
                parseStartedAt = parseStartedAt,
                parseFinishedAt = parseFinishedAt,
                feedback = resolvedParsed.feedbackMessage ?: "链接解析完成"
            )

            val updateResult = itemRepository.updateItem(
                id = itemId,
                title = resolvedParsed.title,
                summary = resolvedParsed.summaryShort?.takeIf { it.isNotBlank() } ?: resolvedParsed.summary,
                content = resolvedParsed.toMarkdownContent(),
                tags = resolvedParsed.tags,
                metaJson = mergedMetaJson,
                status = ItemStatus.DONE
            )

            if (updateResult.isSuccess) {
                val syncResult = itemRepository.syncLocalItemToRemote(itemId)
                syncResult.onSuccess { synced ->
                    android.util.Log.d("FloatingWindow", "✅ 本地条目已同步到远端: local=$itemId, remote=${synced.id}")
                }.onFailure { syncError ->
                    android.util.Log.w("FloatingWindow", "⚠️ 条目已本地回填，但远端同步失败: ${syncError.message}")
                }
                android.util.Log.d("FloatingWindow", "✅ 后台解析完成并已回填: id=$itemId, type=$selectedType")
            } else {
                android.util.Log.e("FloatingWindow", "❌ 回填解析结果失败: ${updateResult.exceptionOrNull()?.message}")
            }
        } catch (e: Exception) {
            progressJob.cancel()
            val parseFinishedAt = System.currentTimeMillis()
            android.util.Log.e("FloatingWindow", "❌ 后台解析失败: ${e.message}", e)

            itemRepository.updateItem(
                id = itemId,
                title = placeholderTitle,
                summary = "链接已加入${getTypeDisplayName(selectedType)}，但解析失败，可稍后重试",
                content = buildFailedMarkdown(placeholderTitle, originalLink, e.message),
                metaJson = mergeParseTrackingMeta(
                    baseMetaJson = null,
                    parseStartedAt = parseStartedAt,
                    parseFinishedAt = parseFinishedAt,
                    feedback = e.message ?: "解析失败"
                ),
                status = ItemStatus.FAILED
            )
            itemRepository.syncLocalItemToRemote(itemId)
        }
    }

    private suspend fun updateProcessingFeedback(
        itemId: String,
        status: ItemStatus,
        parseStartedAt: Long,
        feedback: String
    ) {
        itemRepository.updateItem(
            id = itemId,
            metaJson = mergeParseTrackingMeta(
                baseMetaJson = null,
                parseStartedAt = parseStartedAt,
                feedback = feedback
            ),
            status = status
        )
    }

    private fun buildProcessingMetaJson(
        source: String,
        parseStartedAt: Long,
        feedback: String
    ): String {
        return com.google.gson.Gson().toJson(
            mapOf(
                "source" to source,
                "parse_started_at" to parseStartedAt,
                "parse_feedback" to feedback
            )
        )
    }

    private fun mergeParseTrackingMeta(
        baseMetaJson: String?,
        parseStartedAt: Long,
        parseFinishedAt: Long? = null,
        feedback: String? = null
    ): String {
        val gson = com.google.gson.Gson()
        val merged = mutableMapOf<String, Any?>()

        if (!baseMetaJson.isNullOrBlank()) {
            val parsed = runCatching {
                gson.fromJson(baseMetaJson, Map::class.java) as? Map<String, Any?>
            }.getOrNull()
            if (parsed != null) {
                merged.putAll(parsed)
            }
        }

        merged["parse_started_at"] = parseStartedAt
        parseFinishedAt?.let { merged["parse_finished_at"] = it }
        feedback?.let { merged["parse_feedback"] = it }

        return gson.toJson(merged)
    }

    private fun buildProcessingSummary(type: ItemType): String {
        return "已加入${getTypeDisplayName(type)}，正在抓取正文与生成摘要"
    }

    private fun buildProcessingMarkdown(title: String, url: String, type: ItemType): String {
        return buildString {
            appendLine("# $title")
            appendLine()
            appendLine("当前状态：已加入${getTypeDisplayName(type)}，正在后台解析。")
            appendLine()
            appendLine("[原始链接]($url)")
        }
    }

    private fun buildFailedMarkdown(title: String, url: String, errorMessage: String?): String {
        return buildString {
            appendLine("# $title")
            appendLine()
            appendLine("当前状态：链接解析失败。")
            errorMessage?.takeIf { it.isNotBlank() }?.let {
                appendLine()
                appendLine("失败原因：$it")
            }
            appendLine()
            appendLine("[原始链接]($url)")
        }
    }

    private fun getTypeDisplayName(type: ItemType): String {
        return when (type) {
            ItemType.PAPER -> "论文"
            ItemType.ARTICLE -> "资料"
            ItemType.COMPETITION -> "竞赛"
            ItemType.INSIGHT -> "动态"
            ItemType.VOICE -> "语音"
        }
    }

    private fun startProjectionForeground() {
        if (isProjectionForegroundActive) return

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                PROJECTION_NOTIFICATION_CHANNEL_ID,
                "屏幕采集",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "用于保持截图投影会话"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, PROJECTION_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("正在准备截图")
            .setContentText("正在建立屏幕采集会话")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                PROJECTION_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(PROJECTION_NOTIFICATION_ID, notification)
        }
        isProjectionForegroundActive = true
    }

    private fun stopProjectionForeground() {
        if (!isProjectionForegroundActive) return
        stopForeground(STOP_FOREGROUND_REMOVE)
        isProjectionForegroundActive = false
    }

    private fun saveBitmap(bitmap: Bitmap): String? {
        return try {
            val screenshotDir = java.io.File(filesDir, "imports/screenshot")
            if (!screenshotDir.exists()) {
                screenshotDir.mkdirs()
            }
            val file = java.io.File(screenshotDir, "screenshot_${System.currentTimeMillis()}.png")
            java.io.FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun prepareCaptureUi(): CaptureOverlayState {
        return CaptureOverlayState(
            floatingBallVisibility = floatingBall.hideForCapture(),
            inputViewVisibility = inputView.hideForCapture(),
            resultViewVisibility = resultView.hideForCapture(),
            competitionInputViewVisibility = competitionInputView.hideForCapture(),
            categoryDialogVisibility = categoryDialogView.hideForCapture(),
            quickInsightViewVisibility = quickInsightView.hideForCapture()
        )
    }

    private fun restoreCaptureUi(state: CaptureOverlayState?) {
        if (state == null) return
        floatingBall.restoreVisibility(state.floatingBallVisibility)
        inputView.restoreVisibility(state.inputViewVisibility)
        resultView.restoreVisibility(state.resultViewVisibility)
        competitionInputView.restoreVisibility(state.competitionInputViewVisibility)
        categoryDialogView.restoreVisibility(state.categoryDialogVisibility)
        quickInsightView.restoreVisibility(state.quickInsightViewVisibility)
    }

    private fun View?.hideForCapture(): Int? {
        val target = this ?: return null
        val previousVisibility = target.visibility
        target.visibility = View.INVISIBLE
        return previousVisibility
    }

    private fun View?.restoreVisibility(visibility: Int?) {
        val target = this ?: return
        val previousVisibility = visibility ?: return
        target.visibility = previousVisibility
    }

    private data class CaptureOverlayState(
        val floatingBallVisibility: Int?,
        val inputViewVisibility: Int?,
        val resultViewVisibility: Int?,
        val competitionInputViewVisibility: Int?,
        val categoryDialogVisibility: Int?,
        val quickInsightViewVisibility: Int?
    )

    override fun onDestroy() {
        super.onDestroy()
        hideFloatingBall()
        hideRegionSelectionOverlay()
        hideCategoryDialog()
        hideQuickInsightWindow()
        pendingParseResult = null
        unregisterReceiver(captureReceiver)
        stopProjectionForeground()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}

private class RegionSelectionOverlayView(
    context: Context,
    private val onConfirm: (Rect) -> Unit,
    private val onCancel: () -> Unit
) : FrameLayout(context) {

    private val selectionView = SelectionView(context)

    init {
        setBackgroundColor(Color.TRANSPARENT)
        addView(selectionView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        val buttonBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(24, 24, 24, 48)
        }

        val cancelButton = Button(context).apply {
            text = "取消"
            setOnClickListener { onCancel() }
        }

        val confirmButton = Button(context).apply {
            text = "确认截图"
            setOnClickListener {
                val rect = selectionView.getSelectionRect()
                if (rect != null) {
                    onConfirm(rect)
                } else {
                    Toast.makeText(context, "请先选择区域", Toast.LENGTH_SHORT).show()
                }
            }
        }

        buttonBar.addView(cancelButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val spacer = View(context).apply { layoutParams = LinearLayout.LayoutParams(24, 1) }
        buttonBar.addView(spacer)
        buttonBar.addView(confirmButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val buttonParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM)
        addView(buttonBar, buttonParams)
    }

    private class SelectionView(context: Context) : View(context) {
        private var startX = 0f
        private var startY = 0f
        private var endX = 0f
        private var endY = 0f
        private var hasSelection = false

        private val dimPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            alpha = 120
            style = android.graphics.Paint.Style.FILL
        }
        private val borderPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#10B981")
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 4f
        }
        private val cornerPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#10B981")
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 6f
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.x
                    startY = event.y
                    endX = event.x
                    endY = event.y
                    hasSelection = true
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    endX = event.x
                    endY = event.y
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    endX = event.x
                    endY = event.y
                    invalidate()
                    return true
                }
            }
            return super.onTouchEvent(event)
        }

        override fun onDraw(canvas: android.graphics.Canvas) {
            super.onDraw(canvas)
            val width = width.toFloat()
            val height = height.toFloat()

            if (!hasSelection) {
                canvas.drawRect(0f, 0f, width, height, dimPaint)
                return
            }

            val left = minOf(startX, endX)
            val top = minOf(startY, endY)
            val right = maxOf(startX, endX)
            val bottom = maxOf(startY, endY)

            // dim background outside selection
            canvas.drawRect(0f, 0f, width, top, dimPaint)
            canvas.drawRect(0f, bottom, width, height, dimPaint)
            canvas.drawRect(0f, top, left, bottom, dimPaint)
            canvas.drawRect(right, top, width, bottom, dimPaint)

            // border
            canvas.drawRect(left, top, right, bottom, borderPaint)

            // corners
            val cornerSize = 30f
            canvas.drawLine(left, top, left + cornerSize, top, cornerPaint)
            canvas.drawLine(left, top, left, top + cornerSize, cornerPaint)
            canvas.drawLine(right, top, right - cornerSize, top, cornerPaint)
            canvas.drawLine(right, top, right, top + cornerSize, cornerPaint)
            canvas.drawLine(left, bottom, left + cornerSize, bottom, cornerPaint)
            canvas.drawLine(left, bottom, left, bottom - cornerSize, cornerPaint)
            canvas.drawLine(right, bottom, right - cornerSize, bottom, cornerPaint)
            canvas.drawLine(right, bottom, right, bottom - cornerSize, cornerPaint)
        }

        fun getSelectionRect(): Rect? {
            if (!hasSelection) return null
            val left = minOf(startX, endX).toInt()
            val top = minOf(startY, endY).toInt()
            val right = maxOf(startX, endX).toInt()
            val bottom = maxOf(startY, endY).toInt()
            if (right - left <= 5 || bottom - top <= 5) return null
            return Rect(left, top, right, bottom)
        }
    }
}

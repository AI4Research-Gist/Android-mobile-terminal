package com.example.ai4research.service

import android.app.Service
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
import android.media.projection.MediaProjectionManager
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
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.example.ai4research.domain.model.ItemType
import com.example.ai4research.domain.repository.ItemRepository
import com.example.ai4research.ui.floating.FloatingBallView
import com.example.ai4research.ui.floating.FloatingMenuView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.abs

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
        
        // 截图完成广播
        const val ACTION_CAPTURE_COMPLETED = "com.example.ai4research.action.CAPTURE_COMPLETED"
        const val EXTRA_IMAGE_PATH = "image_path"
        
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
    
    private var ballLayoutParams: WindowManager.LayoutParams? = null
    private var menuLayoutParams: WindowManager.LayoutParams? = null
    
    private var clipboardManager: ClipboardManager? = null
    private var isMenuExpanded = false
    private var detectedLink: String? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mediaProjectionManager by lazy {
        getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }
    private var isCapturing = false
    private var regionOverlay: View? = null

    @Inject
    lateinit var floatingWindowManager: FloatingWindowManager
    
    @Inject
    lateinit var aiService: AIService
    
    @Inject
    lateinit var itemRepository: ItemRepository

    // 广播接收器
    private val captureReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_CAPTURE_COMPLETED) {
                val path = intent.getStringExtra(EXTRA_IMAGE_PATH)
                if (path != null) {
                    handleScreenshot(path)
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
            e.printStackTrace()
        }
    }

    private fun hideFloatingBall() {
        // 关闭菜单和输入框
        if (isMenuExpanded) hideMenu()
        hideInputWindow()
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

    private fun handleScreenshot(path: String) {
        mainHandler.post {
            floatingBall?.isProcessing = true
            Toast.makeText(this, "正在识别截图内容...", Toast.LENGTH_SHORT).show()
        }

        serviceScope.launch {
            try {
                // 1. AI 识别
                val result = aiService.recognizeImageFromPath(path)
                
                // 2. 解析结果
                val content = result.getOrNull() ?: "识别失败"
                
                // 3. 保存到数据库 (简化版，直接作为 Image Item 保存)
                // 注意：这里简单将整个内容作为 summary 保存，实际应解析 JSON
                // 由于 createUrlItem/createImageItem 接口限制，这里可能需要调整
                // 暂时使用 createUrlItem 的变体或直接调用 API
                
                // 尝试解析为结构化数据
                val ocrResult = try {
                    aiService.recognizeImageStructured(
                        android.graphics.BitmapFactory.decodeFile(path)
                    ).getOrNull()
                } catch (e: Exception) { null }

                val title = ocrResult?.title ?: "截图识别结果"
                val summary = ocrResult?.content ?: content
                
                // 4. 入库
                // 这里我们假设有一个 createImageItem 方法，或者我们复用 createUrlItem 存入内容
                // 实际项目中应调用 itemRepository.createImageItem(path, summary)
                itemRepository.createImageItem(path, summary)

                withContext(Dispatchers.Main) {
                    floatingBall?.isProcessing = false
                    showResultOverlay("识别完成", title)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    floatingBall?.isProcessing = false
                    Toast.makeText(this@FloatingWindowService, "识别失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun handleLink(link: String) {
        mainHandler.post {
            floatingBall?.isProcessing = true
            Toast.makeText(this, "正在解析链接...", Toast.LENGTH_SHORT).show()
        }

        serviceScope.launch {
            try {
                // 1. AI 解析
                val result = aiService.parseLinkStructured(link)
                val parseResult = result.getOrNull()
                
                val title = parseResult?.title ?: "新链接"
                val id = parseResult?.id
                val linkType = parseResult?.linkType?.lowercase() ?: "unknown"
                val itemType = when {
                    linkType.contains("arxiv") || linkType.contains("doi") -> ItemType.PAPER
                    else -> ItemType.PAPER
                }
                
                // 2. 入库
                val createResult = itemRepository.createUrlItem(link, title, "ID: $id", itemType)

                withContext(Dispatchers.Main) {
                    floatingBall?.isProcessing = false
                    createResult.onSuccess {
                        showResultOverlay("添加成功", title)
                    }.onFailure { error ->
                        Toast.makeText(this@FloatingWindowService, "添加失败: ${error.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    floatingBall?.isProcessing = false
                    Toast.makeText(this@FloatingWindowService, "解析失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
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
            captureScreen(null)
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
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY)
            putExtra("mode", mode)
        }
        startActivity(intent)
    }

    private fun showRegionSelectionOverlay() {
        if (regionOverlay != null) return
        val overlay = RegionSelectionOverlayView(
            context = this,
            onConfirm = { rect ->
                hideRegionSelectionOverlay()
                captureScreen(rect)
            },
            onCancel = {
                hideRegionSelectionOverlay()
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
        val projection = MediaProjectionStore.getOrCreateProjection(mediaProjectionManager)
        if (projection == null) {
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
                        handleScreenshot(imagePath)
                    } else {
                        Toast.makeText(this@FloatingWindowService, "保存截图失败", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isCapturing = false
                    Toast.makeText(this@FloatingWindowService, "截图失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } finally {
                virtualDisplay?.release()
                imageReader?.close()
                MediaProjectionStore.releaseProjection()
            }
        }
    }

    private fun saveBitmap(bitmap: Bitmap): String? {
        return try {
            val screenshotDir = java.io.File(cacheDir, "screenshots")
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

    override fun onDestroy() {
        super.onDestroy()
        hideFloatingBall()
        hideRegionSelectionOverlay()
        unregisterReceiver(captureReceiver)
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

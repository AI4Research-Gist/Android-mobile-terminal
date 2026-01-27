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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.example.ai4research.domain.model.ItemStatus
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

    // 用于存储解析结果，供后续分类选择使用
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
            Toast.makeText(this, "正在解析链接...", Toast.LENGTH_SHORT).show()
        }

        serviceScope.launch {
            try {
                android.util.Log.d("FloatingWindow", "Starting link parsing: $link")
                
                // 1. 使用增强版 AI 解析获取完整结构化数据
                val result = aiService.parseFullLink(link)
                val parseResult = result.getOrNull()
                
                if (parseResult == null) {
                    withContext(Dispatchers.Main) {
                        floatingBall?.isProcessing = false
                        Toast.makeText(this@FloatingWindowService, "链接解析失败", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }
                
                android.util.Log.d("FloatingWindow", "Parse result: title=${parseResult.title}, type=${parseResult.contentType}")
                
                // 2. 保存解析结果，显示分类选择界面
                withContext(Dispatchers.Main) {
                    floatingBall?.isProcessing = false
                    pendingParseResult = parseResult
                    showCategorySelectionDialog(parseResult)
                }
                
            } catch (e: Exception) {
                android.util.Log.e("FloatingWindow", "Link parsing error: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    floatingBall?.isProcessing = false
                    Toast.makeText(this@FloatingWindowService, "解析失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
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
        
        // 来源信息
        val sourceInfo = TextView(this).apply {
            text = "📍 来源: ${parseResult.source.uppercase()}" + 
                   (parseResult.identifier?.let { " | $it" } ?: "")
            textSize = 12f
            setTextColor(Color.parseColor("#9CA3AF"))
            setPadding(0, dpToPx(8f), 0, 0)
        }
        previewContainer.addView(sourceInfo)
        
        // 摘要预览
        val summaryPreview = TextView(this).apply {
            text = parseResult.summary.take(100) + if (parseResult.summary.length > 100) "..." else ""
            textSize = 13f
            setTextColor(Color.parseColor("#D1D5DB"))
            setPadding(0, dpToPx(8f), 0, 0)
            maxLines = 3
        }
        previewContainer.addView(summaryPreview)
        
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
            SourceOption("wechat", "微信公众号", "📱"),
            SourceOption("zhihu", "知乎", "💬"),
            SourceOption("web", "网页", "🌐"),
            SourceOption("custom", "自定义", "✏️")
        )
        
        // 根据解析结果预选来源
        var selectedSource = when {
            parseResult.source.contains("wechat", ignoreCase = true) -> "wechat"
            parseResult.source.contains("arxiv", ignoreCase = true) -> "web"
            parseResult.source.contains("doi", ignoreCase = true) -> "web"
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
    
    /**
     * 构建 metaJson，包含作者和来源信息
     */
    private fun buildMetaJson(parseResult: FullLinkParseResult, source: String): String {
        val metaMap = mutableMapOf<String, Any>()
        
        // 添加作者信息
        parseResult.authors?.let { authors ->
            if (authors.isNotEmpty()) {
                metaMap["authors"] = authors
            }
        }
        
        // 添加来源信息
        if (source.isNotEmpty()) {
            metaMap["source"] = source
        }
        
        // 添加会议/期刊信息
        parseResult.conference?.let { conf ->
            if (conf.isNotEmpty()) {
                metaMap["conference"] = conf
            }
        }
        
        // 添加年份信息
        parseResult.year?.let { year ->
            if (year.isNotEmpty()) {
                metaMap["year"] = year
            }
        }
        
        // 添加平台信息
        parseResult.platform?.let { platform ->
            if (platform.isNotEmpty()) {
                metaMap["platform"] = platform
            }
        }
        
        // 添加标识符信息
        parseResult.identifier?.let { identifier ->
            if (identifier.isNotEmpty()) {
                metaMap["identifier"] = identifier
            }
        }
        
        // 添加双语摘要
        parseResult.summaryEn?.let { en ->
            if (en.isNotEmpty()) {
                metaMap["summary_en"] = en
            }
        }
        parseResult.summaryZh?.let { zh ->
            if (zh.isNotEmpty()) {
                metaMap["summary_zh"] = zh
            }
        }
        
        // 使用 Gson 序列化
        return if (metaMap.isEmpty()) {
            "{}"
        } else {
            try {
                com.google.gson.Gson().toJson(metaMap)
            } catch (e: Exception) {
                android.util.Log.e("FloatingWindow", "序列化 metaJson 失败: ${e.message}")
                "{}"
            }
        }
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
            Toast.makeText(this, "正在保存...", Toast.LENGTH_SHORT).show()
        }
        
        serviceScope.launch {
            try {
                android.util.Log.d("FloatingWindow", "========== 开始保存条目 ==========")
                android.util.Log.d("FloatingWindow", "类型: $selectedType")
                android.util.Log.d("FloatingWindow", "标题: ${parseResult.title}")
                android.util.Log.d("FloatingWindow", "作者: ${parseResult.authors}")
                android.util.Log.d("FloatingWindow", "来源: $selectedSource")
                android.util.Log.d("FloatingWindow", "摘要: ${parseResult.summary}")
                android.util.Log.d("FloatingWindow", "链接: ${parseResult.originalUrl}")
                
                // 判断解析状态：只要标题不是默认值就认为解析成功
                // AI模型无法访问网页，但能根据URL生成有意义的标题和描述
                val isParseComplete = parseResult.title.isNotBlank() && 
                    parseResult.title != "未命名链接" &&
                    !parseResult.title.startsWith("链接已保存")
                val itemStatus = if (isParseComplete) ItemStatus.DONE else ItemStatus.PROCESSING
                
                android.util.Log.d("FloatingWindow", "解析状态: $itemStatus (isParseComplete=$isParseComplete)")
                
                // 构建 metaJson，包含作者和来源信息
                val finalSource = if (selectedSource.isNotEmpty()) selectedSource else parseResult.source
                val metaJson = buildMetaJson(parseResult, finalSource)
                android.util.Log.d("FloatingWindow", "metaJson: $metaJson")
                
                // 使用新的 createFullItem 方法创建完整条目
                val createResult = itemRepository.createFullItem(
                    title = parseResult.title,
                    summary = parseResult.summary,
                    contentMd = parseResult.toMarkdownContent(),
                    originUrl = parseResult.originalUrl,
                    type = selectedType,
                    status = itemStatus, // 根据解析结果设置状态
                    metaJson = metaJson, // 传递作者和来源信息
                    tags = parseResult.tags
                )
                
                withContext(Dispatchers.Main) {
                    floatingBall?.isProcessing = false
                    pendingParseResult = null
                    
                    createResult.onSuccess { item ->
                        android.util.Log.d("FloatingWindow", "✅ 条目保存成功: id=${item.id}")
                        
                        // 1. 发送广播通知主页面刷新数据
                        val broadcastIntent = Intent(ACTION_ITEM_ADDED).apply {
                            putExtra(EXTRA_ITEM_ID, item.id)
                            putExtra(EXTRA_ITEM_TYPE, selectedType.name)
                            setPackage(packageName)
                        }
                        sendBroadcast(broadcastIntent)
                        android.util.Log.d("FloatingWindow", "已发送刷新广播")
                        
                        // 2. 跳转到主页面
                        openMainActivity(selectedType)
                        
                        // 3. 显示成功提示
                        showSuccessOverlay(parseResult.title, selectedType)
                        
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
            ItemType.COMPETITION -> "🏆"
            ItemType.INSIGHT -> "💡"
            ItemType.VOICE -> "🎤"
        }
        val typeName = when (type) {
            ItemType.PAPER -> "论文"
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
        hideCategoryDialog()
        pendingParseResult = null
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

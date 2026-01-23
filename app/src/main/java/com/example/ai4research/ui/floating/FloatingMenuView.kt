package com.example.ai4research.ui.floating

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 悬浮窗展开菜单视图
 * 毛玻璃背景效果，包含截图、区域选择、添加链接、关闭等按钮
 */
class FloatingMenuView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    interface OnMenuActionListener {
        fun onScreenshot()
        fun onRegionSelect()
        fun onAddLink()
        fun onClose()
    }

    var actionListener: OnMenuActionListener? = null

    private val menuItems = mutableListOf<View>()

    init {
        setupUI()
    }

    private fun setupUI() {
        // 设置背景 - 半透明毛玻璃效果
        setBackgroundColor(Color.TRANSPARENT)

        // 创建菜单容器
        val menuContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 64, 48, 64) // Padding 翻倍
            
            // 毛玻璃背景
            background = createGlassBackground()
        }

        // 添加菜单项
        val items = listOf(
            MenuItemData("截图", 0xFF10B981.toInt(), "screenshot") { actionListener?.onScreenshot() },
            MenuItemData("区域选择", 0xFF06B6D4.toInt(), "region") { actionListener?.onRegionSelect() },
            MenuItemData("添加链接", 0xFFF59E0B.toInt(), "link") { actionListener?.onAddLink() },
            MenuItemData("关闭", 0xFFEF4444.toInt(), "close") { actionListener?.onClose() }
        )

        items.forEachIndexed { index, item ->
            val menuItemView = createMenuItem(item)
            menuContainer.addView(menuItemView)
            menuItems.add(menuItemView)
            
            // 添加间距
            if (index < items.size - 1) {
                val spacer = View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        32 // 间距翻倍
                    )
                }
                menuContainer.addView(spacer)
            }
        }

        addView(menuContainer, LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ))
    }

    private fun createGlassBackground(): android.graphics.drawable.Drawable {
        return object : android.graphics.drawable.Drawable() {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(220, 20, 20, 25)
            }
            private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 4f // 描边加粗
                color = Color.argb(60, 16, 185, 129)
            }
            private val cornerRadius = 96f // 圆角翻倍

            override fun draw(canvas: Canvas) {
                val rect = RectF(bounds)
                canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
                canvas.drawRoundRect(rect, cornerRadius, cornerRadius, borderPaint)
            }

            override fun setAlpha(alpha: Int) {
                paint.alpha = alpha
            }

            override fun setColorFilter(colorFilter: ColorFilter?) {
                paint.colorFilter = colorFilter
            }

            override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
        }
    }

    private fun createMenuItem(item: MenuItemData): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(64, 48, 96, 48) // Padding 翻倍
            
            // 创建圆形图标背景
            val iconContainer = FrameLayout(context).apply {
                val size = 192 // 图标容器大小翻倍
                layoutParams = LinearLayout.LayoutParams(size, size)
                
                // 渐变背景
                background = object : android.graphics.drawable.Drawable() {
                    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                    
                    override fun draw(canvas: Canvas) {
                        paint.shader = RadialGradient(
                            bounds.width() / 2f, bounds.height() / 2f,
                            bounds.width() / 2f,
                            intArrayOf(item.color, Color.argb(100, Color.red(item.color), Color.green(item.color), Color.blue(item.color))),
                            null,
                            Shader.TileMode.CLAMP
                        )
                        canvas.drawCircle(bounds.width() / 2f, bounds.height() / 2f, bounds.width() / 2f, paint)
                    }

                    override fun setAlpha(alpha: Int) {}
                    override fun setColorFilter(colorFilter: ColorFilter?) {}
                    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
                }

                // 添加图标
                val iconView = createIconView(item.iconType)
                addView(iconView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.CENTER))
            }
            addView(iconContainer)

            // 间距
            val spacer = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(48, 1) // 间距翻倍
            }
            addView(spacer)

            // 文字标签
            val label = TextView(context).apply {
                text = item.label
                setTextColor(Color.WHITE)
                textSize = 28f // 字体大小增大
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            }
            addView(label)

            // 点击事件
            setOnClickListener { item.action() }
            
            // 点击效果
            isClickable = true
            isFocusable = true
        }
    }

    private fun createIconView(iconType: String): View {
        return object : View(context) {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.STROKE
                strokeWidth = 8f // 线条加粗
                strokeCap = Paint.Cap.ROUND
            }

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val cx = width / 2f
                val cy = height / 2f
                val size = width * 0.3f

                when (iconType) {
                    "screenshot" -> {
                        // 相机/截图图标
                        val rect = RectF(cx - size, cy - size * 0.7f, cx + size, cy + size * 0.7f)
                        canvas.drawRoundRect(rect, 16f, 16f, paint) // 圆角增大
                        canvas.drawCircle(cx, cy, size * 0.4f, paint)
                    }
                    "region" -> {
                        // 区域选择图标 (四角)
                        val cornerSize = size * 0.4f
                        // 左上
                        canvas.drawLine(cx - size, cy - size, cx - size + cornerSize, cy - size, paint)
                        canvas.drawLine(cx - size, cy - size, cx - size, cy - size + cornerSize, paint)
                        // 右上
                        canvas.drawLine(cx + size, cy - size, cx + size - cornerSize, cy - size, paint)
                        canvas.drawLine(cx + size, cy - size, cx + size, cy - size + cornerSize, paint)
                        // 左下
                        canvas.drawLine(cx - size, cy + size, cx - size + cornerSize, cy + size, paint)
                        canvas.drawLine(cx - size, cy + size, cx - size, cy + size - cornerSize, paint)
                        // 右下
                        canvas.drawLine(cx + size, cy + size, cx + size - cornerSize, cy + size, paint)
                        canvas.drawLine(cx + size, cy + size, cx + size, cy + size - cornerSize, paint)
                    }
                    "link" -> {
                        // 链接图标
                        paint.style = Paint.Style.STROKE
                        val linkRect1 = RectF(cx - size, cy - size * 0.3f, cx + size * 0.3f, cy + size * 0.3f)
                        canvas.drawRoundRect(linkRect1, size * 0.3f, size * 0.3f, paint)
                        canvas.drawLine(cx - size * 0.2f, cy, cx + size * 0.5f, cy, paint)
                    }
                    "close" -> {
                        // X 关闭图标
                        canvas.drawLine(cx - size * 0.5f, cy - size * 0.5f, cx + size * 0.5f, cy + size * 0.5f, paint)
                        canvas.drawLine(cx + size * 0.5f, cy - size * 0.5f, cx - size * 0.5f, cy + size * 0.5f, paint)
                    }
                }
            }
        }
    }

    fun showWithAnimation() {
        visibility = View.VISIBLE
        alpha = 0f
        scaleX = 0.8f
        scaleY = 0.8f
        
        animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(300)
            .setInterpolator(OvershootInterpolator(1.2f))
            .start()

        // 菜单项依次出现动画
        menuItems.forEachIndexed { index, view ->
            view.alpha = 0f
            view.translationX = -50f
            view.animate()
            .alpha(1f)
            .translationX(0f)
            .setStartDelay((index * 50).toLong())
            .setDuration(250)
            .setInterpolator(OvershootInterpolator())
            .start()
        }
    }

    fun hideWithAnimation(onComplete: () -> Unit = {}) {
        animate()
            .alpha(0f)
            .scaleX(0.8f)
            .scaleY(0.8f)
            .setDuration(200)
            .withEndAction {
                visibility = View.GONE
                onComplete()
            }
            .start()
    }

    private data class MenuItemData(
        val label: String,
        val color: Int,
        val iconType: String,
        val action: () -> Unit
    )
}

package com.example.ai4research.ui.floating

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * 高科技风格悬浮球视图
 * 特性：渐变色背景、呼吸光效、旋转扫描动画、可拖拽
 */
class FloatingBallView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // 正常状态颜色 - 青色到翠绿
    private val normalColors = intArrayOf(
        Color.parseColor("#00D9FF"),
        Color.parseColor("#10B981"),
        Color.parseColor("#059669")
    )

    // 处理中状态颜色 - 紫色到粉色
    private val processingColors = intArrayOf(
        Color.parseColor("#D946EF"), // Fuchsia 500
        Color.parseColor("#8B5CF6"), // Violet 500
        Color.parseColor("#6366F1")  // Indigo 500
    )

    // 画笔
    private val ballPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    // 呼吸动画
    private var glowRadius = 0f
    private var glowAlpha = 0.6f
    private val breathAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 2000
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
        interpolator = AccelerateDecelerateInterpolator()
        addUpdateListener { animation ->
            val value = animation.animatedValue as Float
            glowRadius = 8f + value * 12f
            glowAlpha = 0.3f + value * 0.4f
            invalidate()
        }
    }

    // 旋转动画 - 扫描效果
    private var rotationAngle = 0f
    private val rotationAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 4000
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { animation ->
            rotationAngle = animation.animatedValue as Float
            invalidate()
        }
    }

    // 徽章脉冲动画
    private var badgePulse = 1f
    private val badgePulseAnimator = ValueAnimator.ofFloat(1f, 1.3f).apply {
        duration = 800
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
        interpolator = AccelerateDecelerateInterpolator()
        addUpdateListener { animation ->
            badgePulse = animation.animatedValue as Float
            invalidate()
        }
    }

    // 点击回调
    var onClickListener: (() -> Unit)? = null

    // 处理中状态
    var isProcessing = false
        set(value) {
            field = value
            if (value) {
                // 加速旋转
                rotationAnimator.duration = 1000
            } else {
                rotationAnimator.duration = 4000
            }
            invalidate()
        }

    // 检测到链接时显示的徽章
    var showLinkBadge = false
        set(value) {
            field = value
            if (value && !badgePulseAnimator.isRunning) {
                badgePulseAnimator.start()
            } else if (!value) {
                badgePulseAnimator.cancel()
                badgePulse = 1f
            }
            invalidate()
        }

    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F59E0B") // 橙色
    }

    private val badgeGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F59E0B")
        maskFilter = BlurMaskFilter(15f, BlurMaskFilter.Blur.NORMAL)
    }

    init {
        // 设置阴影层使光效更明显
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        breathAnimator.start()
        rotationAnimator.start()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        breathAnimator.cancel()
        rotationAnimator.cancel()
        badgePulseAnimator.cancel()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerX = width / 2f
        val centerY = height / 2f
        val radius = min(width, height) / 2f - 20f

        // 1. 绘制外发光效果
        val currentGlowColor = if (isProcessing) Color.argb((glowAlpha * 255).toInt(), 217, 70, 239) else Color.argb((glowAlpha * 255).toInt(), 16, 185, 129)
        
        glowPaint.shader = RadialGradient(
            centerX, centerY, radius + glowRadius + 15f,
            intArrayOf(
                currentGlowColor,
                Color.argb((glowAlpha * 128).toInt(), 0, 217, 255),
                Color.TRANSPARENT
            ),
            floatArrayOf(0.4f, 0.7f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(centerX, centerY, radius + glowRadius + 15f, glowPaint)

        // 2. 绘制渐变球体
        ballPaint.shader = LinearGradient(
            centerX - radius, centerY - radius,
            centerX + radius, centerY + radius,
            if (isProcessing) processingColors else normalColors,
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(centerX, centerY, radius, ballPaint)

        // 3. 绘制内部边框光效
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            shader = SweepGradient(
                centerX, centerY,
                intArrayOf(
                    Color.argb(0, 255, 255, 255),
                    Color.argb(150, 255, 255, 255),
                    Color.argb(0, 255, 255, 255)
                ),
                floatArrayOf(0f, 0.5f, 1f)
            )
        }
        canvas.save()
        canvas.rotate(rotationAngle, centerX, centerY)
        canvas.drawCircle(centerX, centerY, radius - 3f, borderPaint)
        canvas.restore()

        // 4. 绘制高光效果
        val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        highlightPaint.shader = RadialGradient(
            centerX - radius * 0.3f, centerY - radius * 0.3f, radius * 0.6f,
            intArrayOf(
                Color.argb(100, 255, 255, 255),
                Color.TRANSPARENT
            ),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(centerX - radius * 0.2f, centerY - radius * 0.2f, radius * 0.5f, highlightPaint)

        // 5. 绘制科技感图标 (AI/扫描图标) - 带旋转
        canvas.save()
        canvas.rotate(rotationAngle * 0.5f, centerX, centerY)
        drawTechIcon(canvas, centerX, centerY, radius * 0.4f)
        canvas.restore()

        // 6. 如果检测到链接，绘制脉冲徽章
        if (showLinkBadge && !isProcessing) {
            val badgeRadius = radius * 0.22f * badgePulse
            val badgeX = centerX + radius * 0.6f
            val badgeY = centerY - radius * 0.6f
            
            // 徽章发光
            canvas.drawCircle(badgeX, badgeY, badgeRadius + 5f, badgeGlowPaint)
            // 徽章本体
            canvas.drawCircle(badgeX, badgeY, badgeRadius, badgePaint)
            
            // 绘制链接数字或图标
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = badgeRadius * 1.2f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText("!", badgeX, badgeY + badgeRadius * 0.4f, textPaint)
        }
    }

    private fun drawTechIcon(canvas: Canvas, cx: Float, cy: Float, size: Float) {
        iconPaint.strokeWidth = size * 0.12f

        // 中心圆点 - 发光效果
        val centerGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            maskFilter = BlurMaskFilter(size * 0.2f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawCircle(cx, cy, size * 0.12f, centerGlow)
        
        iconPaint.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy, size * 0.1f, iconPaint)

        // 外围扫描弧线
        iconPaint.style = Paint.Style.STROKE
        val rect = RectF(cx - size, cy - size, cx + size, cy + size)
        
        // 四个角的弧线 - 带渐变
        val arcPaint = Paint(iconPaint).apply {
            shader = LinearGradient(
                cx - size, cy - size, cx + size, cy + size,
                Color.WHITE, Color.argb(100, 255, 255, 255),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawArc(rect, -60f, 30f, false, arcPaint)
        canvas.drawArc(rect, 30f, 30f, false, arcPaint)
        canvas.drawArc(rect, 120f, 30f, false, arcPaint)
        canvas.drawArc(rect, 210f, 30f, false, arcPaint)

        // 内层弧线
        val innerRect = RectF(cx - size * 0.6f, cy - size * 0.6f, cx + size * 0.6f, cy + size * 0.6f)
        canvas.drawArc(innerRect, -30f, 60f, false, iconPaint)
        canvas.drawArc(innerRect, 150f, 60f, false, iconPaint)
        
        // 添加小点装饰
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val dotRadius = size * 0.08f
        for (i in 0 until 4) {
            val angle = Math.toRadians((i * 90 + 45).toDouble())
            val x = cx + (size * 0.8f * cos(angle)).toFloat()
            val y = cy + (size * 0.8f * sin(angle)).toFloat()
            canvas.drawCircle(x, y, dotRadius, dotPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_UP -> {
                onClickListener?.invoke()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = 200 // 优化后的大小 200dp (原 140dp 增大)
        val measuredSize = MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY)
        super.onMeasure(measuredSize, measuredSize)
    }
}

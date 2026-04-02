package com.example.ai4research.ui.floating

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * 冰感玻璃风悬浮球
 * 特性：分层光晕、细腻高光、轻量扫描环与更精致的状态徽章
 */
class FloatingBallView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private fun dp(value: Float): Float = value * density

    private val normalColors = intArrayOf(
        Color.parseColor("#8BF7F1"),
        Color.parseColor("#22D3C5"),
        Color.parseColor("#0E7490")
    )

    private val processingColors = intArrayOf(
        Color.parseColor("#F6C7FF"),
        Color.parseColor("#A78BFA"),
        Color.parseColor("#6366F1")
    )

    private val ballPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shellPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private var glowRadius = 0f
    private var glowAlpha = 0.5f
    private val breathAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 2200
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
        interpolator = AccelerateDecelerateInterpolator()
        addUpdateListener { animation ->
            val value = animation.animatedValue as Float
            glowRadius = dp(4f) + value * dp(8f)
            glowAlpha = 0.24f + value * 0.26f
            invalidate()
        }
    }

    private var rotationAngle = 0f
    private val rotationAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 5200
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { animation ->
            rotationAngle = animation.animatedValue as Float
            invalidate()
        }
    }

    private var badgePulse = 1f
    private val badgePulseAnimator = ValueAnimator.ofFloat(1f, 1.3f).apply {
        duration = 900
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
        interpolator = AccelerateDecelerateInterpolator()
        addUpdateListener { animation ->
            badgePulse = animation.animatedValue as Float
            invalidate()
        }
    }

    var onClickListener: (() -> Unit)? = null

    var isProcessing = false
        set(value) {
            field = value
            rotationAnimator.duration = if (value) 1400 else 5200
            invalidate()
        }

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
        color = Color.parseColor("#FFB347")
    }

    private val badgeGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFB347")
        maskFilter = BlurMaskFilter(dp(10f), BlurMaskFilter.Blur.NORMAL)
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        isClickable = true
        isFocusable = true
        setOnClickListener { onClickListener?.invoke() }
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
        val radius = min(width, height) / 2f - dp(7f)

        val currentGlowColor = if (isProcessing) {
            Color.argb((glowAlpha * 255).toInt(), 202, 126, 255)
        } else {
            Color.argb((glowAlpha * 255).toInt(), 46, 220, 208)
        }

        glowPaint.shader = RadialGradient(
            centerX,
            centerY,
            radius + glowRadius + dp(10f),
            intArrayOf(
                currentGlowColor,
                Color.argb((glowAlpha * 110).toInt(), 160, 255, 248),
                Color.TRANSPARENT
            ),
            floatArrayOf(0.35f, 0.68f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(centerX, centerY, radius + glowRadius + dp(10f), glowPaint)

        ballPaint.shader = LinearGradient(
            centerX - radius,
            centerY - radius * 1.2f,
            centerX + radius,
            centerY + radius * 1.2f,
            if (isProcessing) processingColors else normalColors,
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(centerX, centerY, radius, ballPaint)

        shellPaint.shader = RadialGradient(
            centerX - radius * 0.35f,
            centerY - radius * 0.45f,
            radius * 1.2f,
            intArrayOf(
                Color.argb(120, 255, 255, 255),
                Color.argb(40, 255, 255, 255),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(centerX, centerY, radius * 0.95f, shellPaint)

        ringPaint.strokeWidth = dp(1.6f)
        ringPaint.shader = SweepGradient(
            centerX,
            centerY,
            intArrayOf(
                Color.argb(0, 255, 255, 255),
                Color.argb(170, 255, 255, 255),
                Color.argb(0, 255, 255, 255)
            ),
            floatArrayOf(0f, 0.45f, 1f)
        )
        canvas.save()
        canvas.rotate(rotationAngle, centerX, centerY)
        canvas.drawCircle(centerX, centerY, radius - dp(2f), ringPaint)
        ringPaint.strokeWidth = dp(1f)
        ringPaint.shader = null
        ringPaint.color = Color.argb(70, 255, 255, 255)
        canvas.drawArc(
            RectF(centerX - radius * 0.7f, centerY - radius * 0.7f, centerX + radius * 0.7f, centerY + radius * 0.7f),
            -50f,
            70f,
            false,
            ringPaint
        )
        canvas.restore()

        val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        highlightPaint.shader = RadialGradient(
            centerX - radius * 0.38f,
            centerY - radius * 0.45f,
            radius * 0.7f,
            intArrayOf(
                Color.argb(140, 255, 255, 255),
                Color.TRANSPARENT
            ),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(centerX - radius * 0.18f, centerY - radius * 0.18f, radius * 0.56f, highlightPaint)

        canvas.save()
        canvas.rotate(rotationAngle * 0.35f, centerX, centerY)
        drawTechIcon(canvas, centerX, centerY, radius * 0.48f)
        canvas.restore()

        if (showLinkBadge && !isProcessing) {
            val badgeRadius = radius * 0.2f * badgePulse
            val badgeX = centerX + radius * 0.58f
            val badgeY = centerY - radius * 0.58f

            canvas.drawCircle(badgeX, badgeY, badgeRadius + dp(3f), badgeGlowPaint)
            canvas.drawCircle(badgeX, badgeY, badgeRadius, badgePaint)

            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = badgeRadius * 1.15f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText("!", badgeX, badgeY + badgeRadius * 0.35f, textPaint)
        }
    }

    private fun drawTechIcon(canvas: Canvas, cx: Float, cy: Float, size: Float) {
        val orbitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = size * 0.12f
            color = Color.argb(220, 255, 255, 255)
            strokeCap = Paint.Cap.ROUND
        }

        canvas.drawArc(
            RectF(cx - size, cy - size, cx + size, cy + size),
            -38f,
            112f,
            false,
            orbitPaint
        )
        canvas.drawArc(
            RectF(cx - size * 0.72f, cy - size * 0.72f, cx + size * 0.72f, cy + size * 0.72f),
            138f,
            82f,
            false,
            orbitPaint
        )

        val sparkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
            maskFilter = BlurMaskFilter(size * 0.16f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawCircle(cx, cy, size * 0.14f, sparkPaint)

        val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
        }
        canvas.drawCircle(cx, cy, size * 0.08f, corePaint)

        val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.argb(220, 255, 255, 255)
        }
        repeat(3) { index ->
            val angle = Math.toRadians((index * 120 - 30).toDouble())
            val x = cx + (size * 0.86f * cos(angle)).toFloat()
            val y = cy + (size * 0.86f * sin(angle)).toFloat()
            canvas.drawCircle(x, y, size * 0.055f, tickPaint)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = dp(74f).toInt()
        val measuredSize = MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY)
        super.onMeasure(measuredSize, measuredSize)
    }
}

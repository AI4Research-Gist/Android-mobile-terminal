package com.example.ai4research.ui.floating

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
 * 冰感玻璃快捷面板，强调清晰层级与更紧凑的系统级交互观感
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

    private val density = resources.displayMetrics.density
    private fun dp(value: Int): Int = (value * density).toInt()

    private val menuItems = mutableListOf<View>()

    init {
        setupUI()
    }

    private fun setupUI() {
        setBackgroundColor(Color.TRANSPARENT)

        val menuContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = createGlassBackground()
            elevation = dp(12).toFloat()
        }

        val eyebrow = TextView(context).apply {
            text = "Quick Capture"
            textSize = 11f
            setTextColor(Color.parseColor("#9BE7E2"))
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.08f
        }
        menuContainer.addView(eyebrow)

        val title = TextView(context).apply {
            text = "悬浮窗助手"
            textSize = 20f
            setTextColor(Color.WHITE)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setPadding(0, dp(6), 0, dp(4))
        }
        menuContainer.addView(title)

        val subtitle = TextView(context).apply {
            text = "截图、区域选择与链接采集"
            textSize = 12f
            setTextColor(Color.parseColor("#B5C6D1"))
            setPadding(0, 0, 0, dp(14))
        }
        menuContainer.addView(subtitle)

        val items = listOf(
            MenuItemData("截图", "整屏快速识别", Color.parseColor("#22D3C5"), android.R.drawable.ic_menu_camera) {
                actionListener?.onScreenshot()
            },
            MenuItemData("区域选择", "聚焦局部内容", Color.parseColor("#38BDF8"), android.R.drawable.ic_menu_crop) {
                actionListener?.onRegionSelect()
            },
            MenuItemData("添加链接", "抓取当前网页", Color.parseColor("#F59E0B"), android.R.drawable.ic_menu_share) {
                actionListener?.onAddLink()
            },
            MenuItemData("关闭助手", "收起悬浮入口", Color.parseColor("#FB7185"), android.R.drawable.ic_menu_close_clear_cancel) {
                actionListener?.onClose()
            }
        )

        items.forEachIndexed { index, item ->
            val menuItemView = createMenuItem(item)
            menuContainer.addView(menuItemView)
            menuItems.add(menuItemView)

            if (index < items.lastIndex) {
                val spacer = View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(10)
                    )
                }
                menuContainer.addView(spacer)
            }
        }

        addView(
            menuContainer,
            LayoutParams(
                dp(280),
                LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )
    }

    private fun createGlassBackground(): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                Color.parseColor("#F0192230"),
                Color.parseColor("#E8121926")
            )
        ).apply {
            cornerRadius = dp(26).toFloat()
            setStroke(dp(1), Color.parseColor("#3DF4FFF8"))
        }
    }

    private fun createMenuItem(item: MenuItemData): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(12), dp(14), dp(12))
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(
                    Color.parseColor("#24FFFFFF"),
                    Color.parseColor("#10FFFFFF")
                )
            ).apply {
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), Color.parseColor("#22FFFFFF"))
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { item.action() }

            val iconBubble = FrameLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
                background = GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(
                        lightenColor(item.color, 0.18f),
                        item.color
                    )
                ).apply {
                    shape = GradientDrawable.OVAL
                }
            }

            val iconView = ImageView(context).apply {
                setImageResource(item.iconRes)
                setColorFilter(Color.WHITE)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
            }
            iconBubble.addView(
                iconView,
                FrameLayout.LayoutParams(
                    dp(22),
                    dp(22),
                    Gravity.CENTER
                )
            )
            addView(iconBubble)

            val textContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), 0, 0, 0)
                layoutParams = LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
            }

            val label = TextView(context).apply {
                text = item.label
                textSize = 15f
                setTextColor(Color.WHITE)
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            textContainer.addView(label)

            val subLabel = TextView(context).apply {
                text = item.subLabel
                textSize = 11.5f
                setTextColor(Color.parseColor("#B7C3D0"))
                setPadding(0, dp(2), 0, 0)
            }
            textContainer.addView(subLabel)
            addView(textContainer)

            val chevron = TextView(context).apply {
                text = "›"
                textSize = 20f
                setTextColor(lightenColor(item.color, 0.3f))
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            addView(chevron)
        }
    }

    fun showWithAnimation() {
        visibility = View.VISIBLE
        alpha = 0f
        scaleX = 0.92f
        scaleY = 0.92f

        animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(260)
            .setInterpolator(OvershootInterpolator(1.05f))
            .start()

        menuItems.forEachIndexed { index, view ->
            view.alpha = 0f
            view.translationY = dp(12).toFloat()
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay((index * 35L))
                .setDuration(220)
                .setInterpolator(OvershootInterpolator(1.0f))
                .start()
        }
    }

    fun hideWithAnimation(onComplete: () -> Unit = {}) {
        animate()
            .alpha(0f)
            .scaleX(0.92f)
            .scaleY(0.92f)
            .setDuration(180)
            .withEndAction {
                visibility = View.GONE
                onComplete()
            }
            .start()
    }

    private fun lightenColor(color: Int, amount: Float): Int {
        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)
        return Color.rgb(
            (red + (255 - red) * amount).toInt().coerceIn(0, 255),
            (green + (255 - green) * amount).toInt().coerceIn(0, 255),
            (blue + (255 - blue) * amount).toInt().coerceIn(0, 255)
        )
    }

    private data class MenuItemData(
        val label: String,
        val subLabel: String,
        val color: Int,
        val iconRes: Int,
        val action: () -> Unit
    )
}

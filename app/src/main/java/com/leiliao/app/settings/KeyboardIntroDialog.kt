package com.leiliao.app.settings

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * 雷聊输入法介绍弹窗
 * 精美卡片式设计，展示输入法功能亮点和下载入口
 */
object KeyboardIntroDialog {

    /** 输入法下载页面 URL */
    private const val KEYBOARD_DOWNLOAD_URL = "https://leiliao.app/keyboard"

    /** 功能亮点数据 */
    private data class Feature(
        val emoji: String,
        val title: String,
        val description: String
    )

    private val FEATURES = arrayOf(
        Feature("🔤", "智能联想", "基于上下文的智能输入推荐，越用越懂你"),
        Feature("🎨", "海量主题", "上百款精美键盘主题免费使用"),
        Feature("⚡", "极速响应", "毫秒级按键响应，流畅不卡顿"),
        Feature("🔒", "隐私安全", "本地运算，不上传任何输入数据")
    )

    /**
     * 显示输入法介绍弹窗
     * @param activity 当前 Activity
     */
    @JvmStatic
    fun show(activity: Activity) {
        val dp = activity.resources.displayMetrics.density

        // 颜色常量
        val primaryColor = Color.parseColor("#1A73E8")
        val primaryLight = Color.parseColor("#E8F0FE")
        val cardBg = Color.parseColor("#F8F9FA")
        val textPrimary = Color.parseColor("#1F1F1F")
        val textSecondary = Color.parseColor("#5F6368")
        val dividerColor = Color.parseColor("#E8EAED")
        val btnGrayBg = Color.parseColor("#F1F3F4")

        // ── 根布局 ──
        val rootLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                (24 * dp).toInt(), (20 * dp).toInt(),
                (24 * dp).toInt(), (8 * dp).toInt()
            )
            gravity = Gravity.CENTER_HORIZONTAL
        }

        // ── Hero 图标 ──
        val iconSize = (64 * dp).toInt()
        rootLayout.addView(LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                bottomMargin = (16 * dp).toInt()
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(primaryLight)
            }
            addView(TextView(activity).apply {
                text = "⌨️"
                textSize = 32f
                gravity = Gravity.CENTER
            })
        })

        // ── 标题 ──
        rootLayout.addView(TextView(activity).apply {
            text = "雷聊输入法"
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            setTextColor(textPrimary)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (6 * dp).toInt() }
        })

        // ── 副标题 ──
        rootLayout.addView(TextView(activity).apply {
            text = "高效、安全、个性化的新一代智能输入体验"
            textSize = 14f
            setTextColor(textSecondary)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (16 * dp).toInt() }
        })

        // ── 分割线 ──
        rootLayout.addView(View(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt()
            ).apply { bottomMargin = (16 * dp).toInt() }
            background = GradientDrawable().apply { setColor(dividerColor) }
        })

        // ── 功能亮点卡片 ──
        for (feature in FEATURES) {
            rootLayout.addView(createFeatureCard(activity, feature, dp, cardBg, textPrimary, textSecondary))
        }

        // ── 弹窗 ──
        val dialog = android.app.AlertDialog.Builder(activity)
            .setView(rootLayout)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawable(
            GradientDrawable().apply {
                setColor(Color.WHITE)
                setCornerRadius(20 * dp)
            }
        )
        dialog.show()

        // ── 底部按钮 ──
        val btnContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(
                (24 * dp).toInt(), (12 * dp).toInt(),
                (24 * dp).toInt(), (20 * dp).toInt()
            )
            gravity = Gravity.CENTER
        }

        // "了解更多" 按钮（关闭弹窗）
        btnContainer.addView(TextView(activity).apply {
            text = "了解更多"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(textSecondary)
            gravity = Gravity.CENTER
            setPadding((16 * dp).toInt(), (12 * dp).toInt(), (16 * dp).toInt(), (12 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            background = GradientDrawable().apply {
                setColor(btnGrayBg)
                setCornerRadius(12 * dp)
            }
            setOnClickListener { dialog.dismiss() }
        })

        // 间距
        btnContainer.addView(View(activity).apply {
            layoutParams = LinearLayout.LayoutParams((12 * dp).toInt(), LinearLayout.LayoutParams.MATCH_PARENT)
        })

        // "前往下载" 按钮（打开浏览器）
        btnContainer.addView(TextView(activity).apply {
            text = "前往下载"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding((16 * dp).toInt(), (12 * dp).toInt(), (16 * dp).toInt(), (12 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            background = GradientDrawable().apply {
                setColor(primaryColor)
                setCornerRadius(12 * dp)
            }
            setOnClickListener {
                dialog.dismiss()
                // 打开浏览器
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(KEYBOARD_DOWNLOAD_URL))
                    activity.startActivity(intent)
                } catch (_: Exception) {
                    // 无浏览器则忽略
                }
            }
        })

        // 将按钮附加到弹窗底部
        dialog.window?.setContentView(
            LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                addView(rootLayout.parent as View)
                addView(btnContainer)
            }
        )
    }

    /**
     * 创建单个功能卡片
     */
    private fun createFeatureCard(
        activity: Activity,
        feature: Feature,
        dp: Float,
        cardBg: Int,
        textPrimary: Int,
        textSecondary: Int
    ): View {
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding((14 * dp).toInt(), (12 * dp).toInt(), (14 * dp).toInt(), (12 * dp).toInt())
            background = GradientDrawable().apply {
                setColor(cardBg)
                setCornerRadius(12 * dp)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (10 * dp).toInt() }
            gravity = Gravity.CENTER_VERTICAL
        }

        // 左侧 emoji 图标
        card.addView(TextView(activity).apply {
            text = feature.emoji
            textSize = 28f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = (14 * dp).toInt() }
        })

        // 右侧文字区域
        card.addView(LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            )

            // 标题
            addView(TextView(activity).apply {
                text = feature.title
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
                setTextColor(textPrimary)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = (2 * dp).toInt() }
            })

            // 描述
            addView(TextView(activity).apply {
                text = feature.description
                textSize = 13f
                setTextColor(textSecondary)
            })
        })

        return card
    }
}

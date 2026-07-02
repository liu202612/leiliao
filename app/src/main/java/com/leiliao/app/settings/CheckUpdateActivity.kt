package com.leiliao.app.settings

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast

/**
 * 检查更新页面
 * 原版 DEX 逆向还原 —— 手动检查更新、显示版本信息、下载安装
 */
class CheckUpdateActivity : Activity() {

    companion object {
        private const val TAG = "CheckUpdateActivity"
    }

    private lateinit var btnCheck: Button
    private lateinit var tvResult: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnDownload: Button
    private lateinit var tvCurrentVersion: TextView

    private var latestUpdateInfo: AppUpdateHelper.UpdateInfo? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 构建界面
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
        }

        // 标题
        root.addView(TextView(this).apply {
            text = "检查更新"
            textSize = 22f
            setTextColor(resources.getColor(android.R.color.black, theme))
            setPadding(0, 0, 0, 32)
        })

        // 当前版本
        tvCurrentVersion = TextView(this).apply {
            val code = AppUpdateHelper.getLocalVersionCode(this@CheckUpdateActivity)
            val name = AppUpdateHelper.getLocalVersionName(this@CheckUpdateActivity)
            text = "当前版本: v$name ($code)"
            textSize = 14f
            setPadding(0, 0, 0, 24)
        }
        root.addView(tvCurrentVersion)

        // 进度条（初始隐藏）
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            visibility = View.GONE
            setPadding(0, 0, 0, 16)
        }
        root.addView(progressBar)

        // 检查按钮
        btnCheck = Button(this).apply {
            text = "检查更新"
            textSize = 16f
            setOnClickListener { doCheckUpdate() }
        }
        root.addView(btnCheck)

        // 结果区域（初始隐藏）
        tvResult = TextView(this).apply {
            textSize = 14f
            movementMethod = ScrollingMovementMethod()
            visibility = View.GONE
            setPadding(0, 24, 0, 16)
        }
        root.addView(tvResult)

        // 下载按钮（初始隐藏）
        btnDownload = Button(this).apply {
            text = "下载更新"
            textSize = 16f
            visibility = View.GONE
            setOnClickListener { doDownload() }
        }
        root.addView(btnDownload)

        setContentView(root)
    }

    /**
     * 执行检查更新
     */
    private fun doCheckUpdate() {
        btnCheck.isEnabled = false
        btnCheck.text = "检查中..."
        progressBar.visibility = View.VISIBLE
        tvResult.visibility = View.GONE
        btnDownload.visibility = View.GONE

        Thread {
            try {
                val info = AppUpdateHelper.checkUpdate(this)
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    btnCheck.isEnabled = true
                    btnCheck.text = "检查更新"

                    if (info.hasUpdate) {
                        // 发现新版本
                        latestUpdateInfo = info
                        tvResult.text = buildString {
                            append("发现新版本 v${info.versionName}\n\n")
                            append("更新日志：\n")
                            append(info.changelog)
                            if (info.buildId.isNotEmpty()) {
                                append("\n\n构建ID：${AppUpdateHelper.displayBuildId(info.buildId)}")
                            }
                        }
                        tvResult.visibility = View.VISIBLE
                        btnDownload.visibility = View.VISIBLE
                    } else {
                        // 已是最新版本
                        latestUpdateInfo = null
                        tvResult.text = "当前已是最新版本"
                        tvResult.visibility = View.VISIBLE
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    btnCheck.isEnabled = true
                    btnCheck.text = "检查更新"
                    tvResult.text = "检查更新失败：${e.message}"
                    tvResult.visibility = View.VISIBLE
                }
            }
        }.start()
    }

    /**
     * 执行下载
     */
    private fun doDownload() {
        val info = latestUpdateInfo ?: return
        Toast.makeText(this, "正在下载中，请稍候", Toast.LENGTH_SHORT).show()
        AppUpdateHelper.startDownload(this, info)
    }

    override fun onDestroy() {
        super.onDestroy()
        // 标记已查看
        if (latestUpdateInfo != null && latestUpdateInfo!!.hasUpdate) {
            AppUpdateBadgeStore.markSeen(this)
        }
    }
}

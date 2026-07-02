package com.leiliao.app.settings

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView

/**
 * 更新红点角标存储
 * 原版 DEX 逆向还原 —— 使用 SharedPreferences 保存更新状态，控制设置页面红点显示
 */
object AppUpdateBadgeStore {

    private const val TAG = "UpdateBadgeStore"

    /** SharedPreferences 文件名 */
    private const val PREFS_NAME = "update_badge"

    // ── 键名 ──
    private const val KEY_INSTALLED_BUILD = "installed_build"
    private const val KEY_PENDING_BUILD = "pending_build"
    private const val KEY_PENDING_CODE = "pending_code"
    private const val KEY_SEEN_BUILD = "seen_build"
    private const val KEY_SEEN_CODE = "seen_code"

    // ─────────────────────────────────────────────────────────────
    // 读写方法
    // ─────────────────────────────────────────────────────────────

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * 保存更新信息到 SharedPreferences
     * @param context 上下文
     * @param info 更新信息（AppUpdateHelper.UpdateInfo）
     */
    @JvmStatic
    fun applyUpdateInfo(context: Context, info: AppUpdateHelper.UpdateInfo) {
        val prefs = getPrefs(context).edit()
        prefs.putString(KEY_PENDING_BUILD, info.buildId)
        prefs.putInt(KEY_PENDING_CODE, info.versionCode)
        prefs.apply()
    }

    /**
     * 是否应该显示红点
     * 条件：有待处理更新 && 用户未查看
     * @param context 上下文
     * @return true 表示应显示红点
     */
    @JvmStatic
    fun shouldShowBadge(context: Context): Boolean {
        val prefs = getPrefs(context)
        val pendingBuild = prefs.getString(KEY_PENDING_BUILD, "") ?: ""
        val pendingCode = prefs.getInt(KEY_PENDING_CODE, 0)
        val seenBuild = prefs.getString(KEY_SEEN_BUILD, "") ?: ""
        val seenCode = prefs.getInt(KEY_SEEN_CODE, 0)

        // 无待处理更新
        if (pendingBuild.isEmpty() && pendingCode <= 0) return false

        // 已查看过该版本
        if (pendingBuild == seenBuild && pendingCode == seenCode) return false

        // 同步本地安装状态后重新判断
        syncWithLocalInstall(context)

        // 重新读取
        val newPendingBuild = prefs.getString(KEY_PENDING_BUILD, "") ?: ""
        val newPendingCode = prefs.getInt(KEY_PENDING_CODE, 0)
        val newSeenBuild = prefs.getString(KEY_SEEN_BUILD, "") ?: ""
        val newSeenCode = prefs.getInt(KEY_SEEN_CODE, 0)

        if (newPendingBuild.isEmpty() && newPendingCode <= 0) return false
        return newPendingBuild != newSeenBuild || newPendingCode != newSeenCode
    }

    /**
     * 标记用户已查看该更新
     * @param context 上下文
     */
    @JvmStatic
    fun markSeen(context: Context) {
        val prefs = getPrefs(context)
        val pendingBuild = prefs.getString(KEY_PENDING_BUILD, "") ?: ""
        val pendingCode = prefs.getInt(KEY_PENDING_CODE, 0)

        prefs.edit()
            .putString(KEY_SEEN_BUILD, pendingBuild)
            .putInt(KEY_SEEN_CODE, pendingCode)
            .apply()
    }

    /**
     * 同步本地安装状态
     * 如果本地版本号已更新，清除待处理状态
     * @param context 上下文
     */
    @JvmStatic
    fun syncWithLocalInstall(context: Context) {
        val prefs = getPrefs(context)
        val pendingCode = prefs.getInt(KEY_PENDING_CODE, 0)
        val localCode = AppUpdateHelper.getLocalVersionCode(context)

        // 本地版本已达到或超过待处理版本号，清除
        if (pendingCode > 0 && localCode >= pendingCode) {
            clearPending(prefs)
            Log.d(TAG, "本地已安装 v$localCode，清除待处理更新")
        }
    }

    /**
     * 清除待处理状态
     * @param prefs SharedPreferences 实例
     */
    @JvmStatic
    fun clearPending(prefs: SharedPreferences) {
        prefs.edit()
            .remove(KEY_PENDING_BUILD)
            .remove(KEY_PENDING_CODE)
            .apply()
    }

    // ─────────────────────────────────────────────────────────────
    // UI 红点绑定
    // ─────────────────────────────────────────────────────────────

    /**
     * 绑定红点到 View
     * 在目标 View 的右上角添加一个红点 View
     * @param view 目标 View
     * @param context 上下文
     */
    @JvmStatic
    fun bindDot(view: View, context: Context) {
        if (view.parent !is FrameLayout) return

        val parent = view.parent as FrameLayout
        val dot = createDotView(context)
        dot.visibility = if (shouldShowBadge(context)) View.VISIBLE else View.GONE
        parent.addView(dot)
    }

    /**
     * 创建红点 View
     * 红色圆形，8dp 大小，位于右上角
     * @param context 上下文
     * @return 红点 View
     */
    @JvmStatic
    fun createDotView(context: Context): View {
        val dot = View(context)
        dot.setBackgroundColor(Color.RED)
        val size = (8 * context.resources.displayMetrics.density).toInt()
        val params = FrameLayout.LayoutParams(size, size)
        params.gravity = Gravity.TOP or Gravity.END
        dot.layoutParams = params
        return dot
    }

    /**
     * 更新红点显示状态
     * @param view 目标 View 的父 View
     * @param context 上下文
     */
    @JvmStatic
    fun updateDotVisibility(view: View, context: Context) {
        if (view is FrameLayout) {
            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i)
                if (child is TextView && child.text.isNullOrEmpty() && child.visibility == View.GONE) {
                    child.visibility = if (shouldShowBadge(context)) View.VISIBLE else View.GONE
                    break
                }
            }
        }
    }
}

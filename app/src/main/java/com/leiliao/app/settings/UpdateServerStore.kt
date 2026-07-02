package com.leiliao.app.settings

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * LAN 更新源存储
 * 原版 DEX 逆向还原 —— 保存局域网更新服务器地址和自定义镜像 APK 地址
 */
object UpdateServerStore {

    private const val TAG = "UpdateServerStore"

    /** SharedPreferences 文件名 */
    private const val PREFS_NAME = "update_server"

    // ── 键名 ──
    private const val KEY_LAN_BASE = "lan_base"
    private const val KEY_LAN_TIME = "lan_time"
    private const val KEY_MIRROR_APKS = "mirror_apks"

    /** 局域网地址缓存有效时长（30分钟） */
    private const val LAN_CACHE_EXPIRE_MS = 30 * 60 * 1000L

    // ─────────────────────────────────────────────────────────────
    // 读写方法
    // ─────────────────────────────────────────────────────────────

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * 获取局域网更新服务器基础地址
     * @param context 上下文
     * @return 基础地址字符串，未配置返回 ""
     */
    @JvmStatic
    fun getLanBase(context: Context): String {
        val prefs = getPrefs(context)
        val time = prefs.getLong(KEY_LAN_TIME, 0)
        // 缓存过期，返回空
        if (System.currentTimeMillis() - time > LAN_CACHE_EXPIRE_MS) {
            return ""
        }
        return prefs.getString(KEY_LAN_BASE, "") ?: ""
    }

    /**
     * 保存局域网更新服务器基础地址
     * @param context 上下文
     * @param base 基础地址
     */
    @JvmStatic
    fun saveLanBase(context: Context, base: String) {
        val normalized = normalizeBase(base)
        getPrefs(context).edit()
            .putString(KEY_LAN_BASE, normalized)
            .putLong(KEY_LAN_TIME, System.currentTimeMillis())
            .apply()
        Log.d(TAG, "保存局域网地址: $normalized")
    }

    /**
     * 获取局域网更新清单 URL
     * @param context 上下文
     * @return update.json 完整 URL
     */
    @JvmStatic
    fun getLanManifestUrl(context: Context): String {
        val base = getLanBase(context)
        if (base.isNullOrEmpty()) return ""
        return normalizeBase(base) + "update.json"
    }

    /**
     * 获取局域网 APK 下载 URL
     * @param context 上下文
     * @return APK 完整 URL
     */
    @JvmStatic
    fun getLanApkUrl(context: Context): String {
        val base = getLanBase(context)
        if (base.isNullOrEmpty()) return ""
        return normalizeBase(base) + "leiliao.apk"
    }

    /**
     * 获取自定义镜像 APK URL 列表
     * @param context 上下文
     * @return URL 列表
     */
    @JvmStatic
    fun getMirrorApkUrls(context: Context): List<String> {
        val prefs = getPrefs(context)
        val raw = prefs.getString(KEY_MIRROR_APKS, "") ?: ""
        if (raw.isNullOrEmpty()) return emptyList()
        return raw.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
    }

    /**
     * 保存自定义镜像 APK URL 列表
     * @param context 上下文
     * @param arr URL 数组
     */
    @JvmStatic
    fun saveMirrorApkUrls(context: Context, arr: Array<String>) {
        getPrefs(context).edit()
            .putString(KEY_MIRROR_APKS, arr.joinToString("\n"))
            .apply()
    }

    /**
     * 规范化基础地址
     * 确保末尾有 "/"，移除多余斜杠
     * @param base 原始基础地址
     * @return 规范化后的地址
     */
    @JvmStatic
    fun normalizeBase(base: String): String {
        if (base.isNullOrEmpty()) return ""
        var normalized = base.trim()
        if (!normalized.endsWith("/")) {
            normalized += "/"
        }
        return normalized
    }
}

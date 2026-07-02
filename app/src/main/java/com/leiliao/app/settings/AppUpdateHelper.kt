package com.leiliao.app.settings

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject
import java.io.File
import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * 应用更新检查核心逻辑
 * 原版 DEX 逆向还原 —— 包含更新检查、解析、对话框展示、下载、安装等完整流程
 */
object AppUpdateHelper {

    private const val TAG = "AppUpdateHelper"

    /** HTTP 超时设置 */
    private const val CONNECT_TIMEOUT = 10000
    private const val READ_TIMEOUT = 15000

    /** 缓存刷新参数间隔（5秒内不重复添加） */
    private const val CACHE_BUST_MIN_INTERVAL = 5000L
    private var lastCacheBustTime = 0L

    /**
     * 更新信息数据类
     * 原版内部类 UpdateInfo，从 DEX 逆向还原
     */
    class UpdateInfo {
        var hasUpdate: Boolean = false
        var versionCode: Int = 0
        var versionName: String = ""
        var buildId: String = ""
        var publishedAt: Long = 0
        var changelog: String = ""
        var downloadUrl: String = ""
        var cachedApkPath: String = ""

        /** 用于对话框显示的简洁版本号 */
        val displayVersion: String get() = "v$versionName"
    }

    // ─────────────────────────────────────────────────────────────
    // 主入口方法
    // ─────────────────────────────────────────────────────────────

    /**
     * 检查更新 —— 主入口
     * 从远程拉取 update.json 并解析，返回 UpdateInfo
     * @param context 上下文
     * @return UpdateInfo 更新信息（hasUpdate 表示是否有新版本）
     */
    @JvmStatic
    fun checkUpdate(context: Context): UpdateInfo {
        val localCode = getLocalVersionCode(context)
        val json = fetchManifest(context)
        if (json.isNullOrEmpty()) {
            return UpdateInfo()
        }
        return parseUpdate(context, json, localCode)
    }

    /**
     * 从已下载的 APK 检查更新
     * @param context 上下文
     * @param serverCode 服务器版本号
     * @return UpdateInfo
     */
    @JvmStatic
    fun checkUpdateFromApk(context: Context, serverCode: Int): UpdateInfo {
        val localCode = getLocalVersionCode(context)
        val buildId = getLocalApkBuildId(context)
        val info = UpdateInfo()
        info.versionCode = serverCode
        info.hasUpdate = resolveHasUpdate(context, serverCode, localCode, buildId, fromApk = true)
        return info
    }

    /**
     * 检查并弹窗提示（在后台线程执行）
     * @param activity Activity 实例
     */
    @JvmStatic
    fun checkAndPrompt(activity: Activity) {
        Thread {
            try {
                val info = checkUpdate(activity)
                activity.runOnUiThread {
                    if (info.hasUpdate) {
                        showUpdateDialog(activity, info)
                    } else {
                        showLatestDialog(activity, info)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "检查更新异常", e)
                activity.runOnUiThread {
                    showFallbackDialog(activity)
                }
            }
        }.start()
    }

    // ─────────────────────────────────────────────────────────────
    // 网络请求
    // ─────────────────────────────────────────────────────────────

    /**
     * 从远程拉取 update.json 内容
     * 逐个尝试 UPDATE_MANIFEST_URLS 中的镜像地址
     * @param context 上下文
     * @return JSON 字符串，获取失败返回 null
     */
    @JvmStatic
    fun fetchManifest(context: Context): String? {
        for (url in AppUpdateConfig.UPDATE_MANIFEST_URLS) {
            try {
                val body = httpGet(context, url)
                if (!body.isNullOrEmpty()) return body
            } catch (e: Exception) {
                Log.w(TAG, "获取更新清单失败: $url", e)
            }
        }
        return null
    }

    /**
     * 通用 HTTP GET 请求
     * @param context 上下文
     * @param url 请求地址
     * @return 响应体字符串
     */
    @JvmStatic
    fun httpGet(context: Context, url: String): String {
        Log.d(TAG, "HTTP GET: $url")
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = CONNECT_TIMEOUT
        conn.readTimeout = READ_TIMEOUT
        conn.requestMethod = "GET"
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("Cache-Control", "no-cache")
        conn.setRequestProperty("User-Agent", "LeiLiao-UpdateCheck")

        return if (conn.responseCode == HttpURLConnection.HTTP_OK) {
            conn.inputStream.bufferedReader().use { it.readText() }
        } else {
            throw IOException("HTTP ${conn.responseCode}: ${conn.responseMessage}")
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 解析与判断
    // ─────────────────────────────────────────────────────────────

    /**
     * 解析 update.json 内容
     * @param context 上下文
     * @param json JSON 字符串
     * @param localCode 本地版本号
     * @return UpdateInfo
     */
    @JvmStatic
    fun parseUpdate(context: Context, json: String, localCode: Int): UpdateInfo {
        val info = UpdateInfo()
        try {
            val obj = JSONObject(json)
            info.versionCode = obj.optInt("versionCode", 0)
            info.versionName = obj.optString("versionName", "")
            info.buildId = obj.optString("buildId", "")
            info.publishedAt = obj.optLong("publishedAt", 0)
            info.changelog = obj.optString("changelog", "")
            info.downloadUrl = obj.optString("downloadUrl", AppUpdateConfig.APK_DOWNLOAD_URL)

            val hasUpdate = resolveHasUpdate(
                context, info.versionCode, localCode, info.buildId, fromApk = false
            )
            info.hasUpdate = hasUpdate
        } catch (e: Exception) {
            Log.e(TAG, "解析 update.json 失败", e)
        }
        return info
    }

    /**
     * 判断是否有更新
     * @param context 上下文
     * @param serverCode 服务器版本号
     * @param localCode 本地版本号
     * @param buildId 构建 ID
     * @param fromApk 是否从 APK 文件检查
     * @return true 表示有更新
     */
    @JvmStatic
    fun resolveHasUpdate(
        context: Context,
        serverCode: Int,
        localCode: Int,
        buildId: String?,
        fromApk: Boolean
    ): Boolean {
        // 版本号大于本地 → 有大版本更新
        if (serverCode > localCode) return true
        // 版本号相同但 buildId 不同 → 同版本热更新
        if (serverCode == localCode && !buildId.isNullOrEmpty()) {
            val localBuildId = getLocalApkBuildId(context)
            // 本地 buildId 已记录且与远程不同 → 需要热更新
            if (localBuildId.isNotEmpty() && localBuildId != buildId) return true
        }
        return false
    }

    // ─────────────────────────────────────────────────────────────
    // 下载地址收集
    // ─────────────────────────────────────────────────────────────

    /**
     * 收集所有镜像下载地址
     * @param context 上下文
     * @param downloadUrl 主下载地址
     * @return 所有可用下载地址列表
     */
    @JvmStatic
    fun collectDownloadUrls(context: Context, downloadUrl: String): List<String> {
        val urls = mutableListOf(downloadUrl)
        for (mirror in AppUpdateConfig.APK_DOWNLOAD_URLS) {
            if (mirror != downloadUrl && mirror !in urls) {
                urls.add(mirror)
            }
        }
        // 加入局域网镜像
        val lanUrl = UpdateServerStore.getLanApkUrl(context)
        if (!lanUrl.isNullOrEmpty() && lanUrl !in urls) {
            urls.add(lanUrl)
        }
        // 加入自定义镜像
        val mirrors = UpdateServerStore.getMirrorApkUrls(context)
        for (m in mirrors) {
            if (m !in urls) urls.add(m)
        }
        return urls
    }

    /**
     * 收集所有镜像下载地址（带缓存刷新参数）
     * @param context 上下文
     * @param downloadUrl 主下载地址
     * @param requiredBuildId 需要的构建 ID
     * @return 所有可用下载地址列表
     */
    @JvmStatic
    fun collectDownloadUrls(context: Context, downloadUrl: String, requiredBuildId: String): List<String> {
        val urls = collectDownloadUrls(context, downloadUrl)
        return urls.map { withCacheBust(it, requiredBuildId) }
    }

    // ─────────────────────────────────────────────────────────────
    // 对话框展示
    // ─────────────────────────────────────────────────────────────

    /**
     * 显示"发现新版本"对话框
     * @param activity Activity
     * @param info 更新信息
     */
    @JvmStatic
    fun showUpdateDialog(activity: Activity, info: UpdateInfo) {
        // 保存更新信息到角标存储
        AppUpdateBadgeStore.applyUpdateInfo(activity, info)

        val contentView = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 16)

            // 版本信息
            addView(TextView(activity).apply {
                text = "新版本: ${info.displayVersion}"
                textSize = 16f
            })

            // 更新日志
            if (info.changelog.isNotBlank()) {
                addView(TextView(activity).apply {
                    text = info.changelog
                    textSize = 14f
                    setPadding(0, 16, 0, 8)
                    movementMethod = ScrollingMovementMethod()
                    maxLines = 10
                })
            }
        }

        AlertDialog.Builder(activity)
            .setTitle("发现新版本 ${info.displayVersion}")
            .setView(contentView)
            .setCancelable(true)
            .setPositiveButton("立即更新") { _, _ ->
                startDownload(activity, info)
            }
            .setNegativeButton("稍后") { _, _ -> }
            .show()
    }

    /**
     * 显示"已是最新版本"对话框
     * @param activity Activity
     * @param info 更新信息
     */
    @JvmStatic
    fun showLatestDialog(activity: Activity, info: UpdateInfo) {
        val msg = if (info.versionCode > 0) {
            "当前已是最新版本 ${info.displayVersion}"
        } else {
            "当前已是最新版本"
        }
        AlertDialog.Builder(activity)
            .setTitle("已是最新版本")
            .setMessage(msg)
            .setCancelable(true)
            .setPositiveButton("确定", null)
            .show()
    }

    /**
     * 显示"无法获取版本信息"对话框
     * @param activity Activity
     */
    @JvmStatic
    fun showFallbackDialog(activity: Activity) {
        AlertDialog.Builder(activity)
            .setTitle("提示")
            .setMessage("暂时无法获取版本信息。\n同版本热更新只在开发者推送后生效，请稍后重试。")
            .setCancelable(true)
            .setPositiveButton("确定", null)
            .show()
    }

    // ─────────────────────────────────────────────────────────────
    // 下载与安装入口
    // ─────────────────────────────────────────────────────────────

    /**
     * 调用 AppUpdateDownloadHelper 开始下载
     * @param activity Activity
     * @param info 更新信息
     */
    @JvmStatic
    fun startDownload(activity: Activity, info: UpdateInfo) {
        val candidates = collectDownloadUrls(activity, info.downloadUrl, info.buildId)
        AppUpdateDownloadHelper.startDownload(activity, info.downloadUrl, candidates, info.buildId)
    }

    /**
     * 下载 APK 返回文件路径
     * @param context 上下文
     * @param downloadUrl 下载地址
     * @return 下载文件路径，失败返回 null
     */
    @JvmStatic
    fun downloadApk(context: Context, downloadUrl: String): String? {
        return try {
            val candidates = collectDownloadUrls(context, downloadUrl)
            for (url in candidates) {
                try {
                    val file = downloadFromUrlToFile(context, url)
                    if (file != null && isValidApkFile(context, file)) {
                        return file.absolutePath
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "下载 APK 失败: $url", e)
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "下载 APK 异常", e)
            null
        }
    }

    /**
     * 从 URL 下载 APK 到应用私有目录
     * @param context 上下文
     * @param url 下载地址
     * @return 下载的文件
     */
    @JvmStatic
    fun downloadFromUrl(context: Context, url: String) {
        downloadFromUrlToFile(context, url)
    }

    /**
     * 安装 APK
     * @param context 上下文
     * @param path APK 文件路径
     */
    @JvmStatic
    fun installApk(context: Context, path: String) {
        val file = File(path)
        if (file.exists()) {
            AppUpdateInstallHelper.install(context, file)
        } else {
            Log.e(TAG, "APK 文件不存在: $path")
        }
    }

    /**
     * 打开浏览器下载 APK
     * @param context 上下文
     */
    @JvmStatic
    fun openDownloadUrl(context: Context) {
        val url = AppUpdateConfig.APK_DOWNLOAD_URL
        try {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                data = android.net.Uri.parse(url)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "打开浏览器下载失败", e)
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 工具方法
    // ─────────────────────────────────────────────────────────────

    /**
     * 添加缓存刷新参数
     * @param url 原始 URL
     * @param buildId 构建 ID
     * @return 带 _t 参数的 URL
     */
    @JvmStatic
    fun withCacheBust(url: String, buildId: String): String {
        val now = System.currentTimeMillis()
        if (now - lastCacheBustTime < CACHE_BUST_MIN_INTERVAL) {
            return url
        }
        lastCacheBustTime = now
        val sep = if (url.contains("?")) "&" else "?"
        return "$url${sep}_t=${buildId}_${now}"
    }

    /**
     * 获取本地版本号
     * @param context 上下文
     * @return 版本号
     */
    @JvmStatic
    fun getLocalVersionCode(context: Context): Int {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode
            }
        } catch (e: PackageManager.NameNotFoundException) {
            0
        }
    }

    /**
     * 获取本地版本名称
     * @param context 上下文
     * @return 版本名称
     */
    @JvmStatic
    fun getLocalVersionName(context: Context): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
        } catch (e: PackageManager.NameNotFoundException) {
            ""
        }
    }

    /**
     * 获取本地已安装的 buildId
     * 优先从 SharedPreferences 读取，没有记录时从缓存 APK 读取
     * @param context 上下文
     * @return buildId，未找到返回 ""
     */
    @JvmStatic
    fun getLocalApkBuildId(context: Context): String {
        // 优先从 SharedPreferences 读取已安装的 buildId
        val prefs = context.getSharedPreferences("update_badge", Context.MODE_PRIVATE)
        val installedBuild = prefs.getString("installed_build", "") ?: ""
        if (installedBuild.isNotEmpty()) return installedBuild

        // 回退到从已下载的缓存 APK 中读取
        val file = getCachedApkFile(context)
        if (!file.exists()) return ""
        return try {
            val pInfo = readApkPackageInfo(context, file.absolutePath)
            // 尝试从 applicationMetaData 或 versionName 中提取 buildId
            val metaBuildId = pInfo.applicationInfo?.metaData?.getString("buildId")
            if (!metaBuildId.isNullOrEmpty()) return metaBuildId
            // 回退到从 versionName 后缀提取
            pInfo.versionName?.split("-").lastOrNull() ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * 验证 APK 文件有效性
     * @param context 上下文
     * @param file APK 文件
     * @return true 表示有效
     */
    @JvmStatic
    fun isValidApkFile(context: Context, file: File): Boolean {
        if (!file.exists() || file.length() < 1024) return false
        return try {
            val pInfo = readApkPackageInfo(context, file.absolutePath)
            pInfo != null && pInfo.packageName == context.packageName
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 计算文件 MD5
     * @param file 文件
     * @return MD5 十六进制字符串
     */
    @JvmStatic
    fun md5File(file: File): String {
        val digest = MessageDigest.getInstance("MD5")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return BigInteger(1, digest.digest()).toString(16).padStart(32, '0')
    }

    /**
     * 读取 APK 包信息
     * @param context 上下文
     * @param path APK 文件路径
     * @return PackageInfo
     */
    @JvmStatic
    fun readApkPackageInfo(context: Context, path: String): PackageInfo {
        val pm = context.packageManager
        return pm.getPackageArchiveInfo(path, PackageManager.GET_META_DATA)
            ?: throw PackageManager.NameNotFoundException("无法读取 APK 包信息: $path")
    }

    /**
     * 验证已下载 APK 是否可安装
     * @param context 上下文
     * @param file APK 文件
     * @param requiredBuildId 需要的构建 ID
     * @return true 表示可以安装
     */
    @JvmStatic
    fun canInstallDownloadedApk(context: Context, file: File, requiredBuildId: String): Boolean {
        if (!file.exists() || !isValidApkFile(context, file)) return false
        if (requiredBuildId.isNotEmpty()) {
            val apkBuildId = getLocalApkBuildId(context)
            // 如果 buildId 能获取到且不匹配，则不能安装
            if (apkBuildId.isNotEmpty() && apkBuildId != requiredBuildId) return false
        }
        return true
    }

    /**
     * 后台刷新红点
     * @param context 上下文
     */
    @JvmStatic
    fun refreshBadgeInBackground(context: Context) {
        refreshBadgeInBackground(context) { }
    }

    /**
     * 后台刷新红点（带回调）
     * @param context 上下文
     * @param onDone 完成回调
     */
    @JvmStatic
    fun refreshBadgeInBackground(context: Context, onDone: () -> Unit) {
        Thread {
            try {
                val info = checkUpdate(context)
                AppUpdateBadgeStore.applyUpdateInfo(context, info)
            } catch (e: Exception) {
                Log.w(TAG, "后台刷新红点失败", e)
            } finally {
                onDone()
            }
        }.start()
    }

    /**
     * 格式化 buildId 用于显示
     * @param buildId 原始 buildId
     * @return 格式化后的字符串
     */
    @JvmStatic
    fun displayBuildId(buildId: String): String {
        return if (buildId.length > 8) buildId.substring(0, 8) + "..." else buildId
    }

    /**
     * 缩短 buildId
     * @param buildId 原始 buildId
     * @return 缩短后的字符串
     */
    @JvmStatic
    fun shortenBuildId(buildId: String): String {
        return if (buildId.length > 6) buildId.substring(0, 6) else buildId
    }

    /**
     * 解析下载地址
     * @param context 上下文
     * @param url 原始 URL
     * @return 解析后的 URL
     */
    @JvmStatic
    fun resolveDownloadUrl(context: Context, url: String): String {
        // 如果 URL 为空，使用默认 CDN 地址
        if (url.isNullOrEmpty()) return AppUpdateConfig.APK_DOWNLOAD_URL
        return url
    }

    // ─────────────────────────────────────────────────────────────
    // 内部实现
    // ─────────────────────────────────────────────────────────────

    /**
     * 获取缓存的 APK 文件
     */
    private fun getCachedApkFile(context: Context): File {
        val dir = File(context.filesDir, AppUpdateDownloadHelper.UPDATES_DIR)
        if (!dir.exists()) dir.mkdirs()
        return File(dir, AppUpdateDownloadHelper.FILE_NAME)
    }

    /**
     * 从 URL 下载文件到应用私有目录
     */
    private fun downloadFromUrlToFile(context: Context, url: String): File? {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = CONNECT_TIMEOUT
        conn.readTimeout = READ_TIMEOUT
        conn.requestMethod = "GET"
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("Accept", "*/*")

        return if (conn.responseCode == HttpURLConnection.HTTP_OK) {
            val dir = File(context.filesDir, AppUpdateDownloadHelper.UPDATES_DIR)
            if (!dir.exists()) dir.mkdirs()
            val outFile = File(dir, AppUpdateDownloadHelper.FILE_NAME)
            conn.inputStream.buffered().use { input ->
                outFile.outputStream().buffered().use { output ->
                    input.copyTo(output)
                }
            }
            outFile
        } else {
            null
        }
    }
}

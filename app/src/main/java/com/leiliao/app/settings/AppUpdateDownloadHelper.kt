package com.leiliao.app.settings

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * APK 下载管理器
 * 原版 DEX 逆向还原 —— 使用 HttpURLConnection 自行下载（非 DownloadManager）
 * 支持多镜像候选 URL 逐个尝试、自定义进度通知
 */
object AppUpdateDownloadHelper {

    private const val TAG = "AppUpdateDownload"
    private const val NOTIFICATION_ID = 1001

    /** 通知渠道 ID */
    const val CHANNEL_ID = "app_update"

    /** APK 保存目录 */
    const val UPDATES_DIR = "updates"

    /** APK 文件名 */
    const val FILE_NAME = "leiliao_update.apk"

    /** HTTP 超时设置 */
    private const val CONNECT_TIMEOUT = 15000
    private const val READ_TIMEOUT = 30000

    /** 缓冲区大小 */
    private const val BUFFER_SIZE = 8192

    /** 当前下载线程（用于取消） */
    @Volatile
    private var downloadThread: Thread? = null

    /** 是否正在下载 */
    @Volatile
    private var isDownloading = false

    /** 当前下载对应的 buildId（用于安装后记录） */
    @Volatile
    private var currentBuildId: String = ""

    // ─────────────────────────────────────────────────────────────
    // 公共入口
    // ─────────────────────────────────────────────────────────────

    /**
     * 开始下载 APK —— 入口
     * @param context 上下文
     * @param url 主下载地址
     */
    @JvmStatic
    fun startDownload(context: Context, url: String) {
        startDownload(context, url, emptyList(), "")
    }

    /**
     * 开始下载 APK —— 带候选 URL
     * @param context 上下文
     * @param url 主下载地址
     * @param candidates 候选 URL 列表
     */
    @JvmStatic
    fun startDownload(context: Context, url: String, candidates: List<String>) {
        startDownload(context, url, candidates, "")
    }

    /**
     * 开始下载 APK —— 带 buildId 验证
     * @param context 上下文
     * @param url 主下载地址
     * @param candidates 候选 URL 列表
     * @param requiredBuildId 需要的构建 ID
     */
    @JvmStatic
    fun startDownload(
        context: Context,
        url: String,
        candidates: List<String>,
        requiredBuildId: String
    ) {
        if (isDownloading) {
            Log.w(TAG, "正在下载中，请稍候")
            toast(context, "正在下载中，请稍候")
            return
        }

        currentBuildId = requiredBuildId

        val allUrls = if (candidates.isNotEmpty()) {
            candidates.toMutableList().apply {
                if (url !in this) add(0, url)
            }
        } else {
            listOf(url)
        }

        isDownloading = true
        downloadThread = Thread {
            var success = false
            var lastError = ""
            for (downloadUrl in allUrls) {
                try {
                    Log.i(TAG, "尝试下载: $downloadUrl")
                    val file = downloadToAppDir(context, downloadUrl)
                    if (file != null && file.exists() && file.length() > 0) {
                        // 验证 APK 有效性
                        if (AppUpdateHelper.isValidApkFile(context, file)) {
                            onDownloadSuccess(context, file)
                            success = true
                            break
                        } else {
                            Log.w(TAG, "下载的 APK 文件无效: ${file.absolutePath}")
                            file.delete()
                            lastError = "下载的 APK 文件无效"
                        }
                    } else {
                        lastError = "下载文件为空"
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "下载失败: $downloadUrl", e)
                    lastError = e.message ?: "未知错误"
                }
            }
            if (!success) {
                onDownloadFailed(context, "下载失败，请稍后重试或用电脑同步最新包 ($lastError)")
            }
            isDownloading = false
        }
        downloadThread?.start()
    }

    // ─────────────────────────────────────────────────────────────
    // 核心下载逻辑
    // ─────────────────────────────────────────────────────────────

    /**
     * 下载 APK 到应用私有目录
     * @param context 上下文
     * @param url 下载地址
     * @return 下载的文件，失败返回 null
     */
    @JvmStatic
    fun downloadToAppDir(context: Context, url: String): File? {
        val dir = File(context.filesDir, UPDATES_DIR)
        if (!dir.exists()) dir.mkdirs()
        val outFile = File(dir, FILE_NAME)

        // 清理旧文件
        if (outFile.exists()) outFile.delete()

        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = CONNECT_TIMEOUT
        conn.readTimeout = READ_TIMEOUT
        conn.requestMethod = "GET"
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("Accept", "*/*")
        conn.setRequestProperty("User-Agent", "LeiLiao-Updater")

        if (conn.responseCode != HttpURLConnection.HTTP_OK) {
            Log.w(TAG, "HTTP ${conn.responseCode}: ${conn.responseMessage}")
            return null
        }

        val totalSize = conn.contentLength
        showProgressNotification(context, 0, totalSize)

        conn.inputStream.buffered().use { input ->
            FileOutputStream(outFile).buffered().use { output ->
                val buffer = ByteArray(BUFFER_SIZE)
                var bytesRead: Int
                var totalRead = 0L

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    if (!isDownloading) {
                        // 下载被取消
                        Log.i(TAG, "下载已取消")
                        break
                    }
                    output.write(buffer, 0, bytesRead)
                    totalRead += bytesRead
                    updateProgressNotification(context, totalRead.toInt(), totalSize)
                }
            }
        }

        return if (outFile.exists() && outFile.length() > 0) outFile else null
    }

    // ─────────────────────────────────────────────────────────────
    // 通知管理
    // ─────────────────────────────────────────────────────────────

    /**
     * 显示进度通知
     * @param context 上下文
     * @param progress 已下载字节数
     * @param total 总字节数
     */
    @JvmStatic
    fun showProgressNotification(context: Context, progress: Int, total: Int) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(nm)

        val percent = if (total > 0) (progress * 100 / total) else 0
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }
        builder.setSmallIcon(android.R.drawable.stat_sys_download)
        builder.setContentTitle("雷聊更新")
        builder.setContentText("正在下载中 ${percent}%")
        builder.setProgress(100, percent, total <= 0)
        builder.setOngoing(true)

        nm.notify(NOTIFICATION_ID, builder.build())
    }

    /**
     * 更新进度通知
     * @param context 上下文
     * @param progress 已下载字节数
     * @param total 总字节数
     */
    @JvmStatic
    fun updateProgressNotification(context: Context, progress: Int, total: Int) {
        showProgressNotification(context, progress, total)
    }

    /**
     * 显示下载完成通知
     * @param context 上下文
     */
    @JvmStatic
    fun showCompleteNotification(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(nm)

        // 点击通知后打开安装页面
        val intent = Intent().apply {
            setPackage(context.packageName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pi = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }
        builder.setSmallIcon(android.R.drawable.stat_sys_download_done)
        builder.setContentTitle("雷聊更新")
        builder.setContentText("下载完成，点击安装")
        builder.setAutoCancel(true)
        builder.setContentIntent(pi)
        builder.setProgress(0, 0, false)

        nm.notify(NOTIFICATION_ID, builder.build())
    }

    /**
     * 取消通知
     * @param context 上下文
     */
    @JvmStatic
    fun cancelNotification(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIFICATION_ID)
    }

    /**
     * 确保通知渠道已创建
     */
    private fun ensureChannel(nm: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "应用更新",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "应用下载与更新进度通知"
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 回调处理
    // ─────────────────────────────────────────────────────────────

    /**
     * 处理下载完成
     * @param context 上下文
     * @param downloadId 下载 ID（兼容用）
     */
    @JvmStatic
    fun handleDownloadComplete(context: Context, downloadId: Int) {
        val dir = File(context.filesDir, UPDATES_DIR)
        val file = File(dir, FILE_NAME)
        if (file.exists()) {
            onDownloadSuccess(context, file)
        } else {
            onDownloadFailed(context, "下载文件不存在")
        }
    }

    /**
     * 下载成功回调 -> 安装
     * @param context 上下文
     * @param file 下载的 APK 文件
     */
    @JvmStatic
    fun onDownloadSuccess(context: Context, file: File) {
        Log.i(TAG, "下载成功: ${file.absolutePath}, 大小: ${file.length()}")
        cancelNotification(context)
        showCompleteNotification(context)

        // 安装前将 buildId 记录到 SharedPreferences
        if (currentBuildId.isNotEmpty()) {
            context.getSharedPreferences("update_badge", Context.MODE_PRIVATE)
                .edit()
                .putString("installed_build", currentBuildId)
                .apply()
            Log.i(TAG, "已记录安装 buildId: $currentBuildId")
        }

        // 调用安装
        AppUpdateInstallHelper.install(context, file)
    }

    /**
     * 下载失败回调
     * @param context 上下文
     * @param message 错误消息
     */
    @JvmStatic
    fun onDownloadFailed(context: Context, message: String) {
        Log.e(TAG, message)
        cancelNotification(context)
        // 弹出 Toast 提示
        toast(context, "下载失败，请稍后重试或用电脑同步最新包")
    }

    /**
     * 安装权限被拒绝回调
     * @param context 上下文
     */
    @JvmStatic
    fun onDownloadRejected(context: Context) {
        Log.w(TAG, "安装权限被拒绝")
        cancelNotification(context)
        toast(context, "需要授予安装权限")
    }

    // ─────────────────────────────────────────────────────────────
    // 安装与浏览器备用
    // ─────────────────────────────────────────────────────────────

    /**
     * 通过系统 URI 安装
     * @param context 上下文
     * @param uri 文件 URI
     */
    @JvmStatic
    fun installFromSystemUri(context: Context, uri: Uri) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "系统安装失败", e)
            toast(context, "安装失败：${e.message}")
        }
    }

    /**
     * 打开浏览器备用下载
     * @param context 上下文
     * @param url 下载地址
     */
    @JvmStatic
    fun openBrowser(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(url)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "打开浏览器失败", e)
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 工具方法
    // ─────────────────────────────────────────────────────────────

    /**
     * 显示 Toast
     * @param context 上下文
     * @param message 消息
     */
    @JvmStatic
    fun toast(context: Context, message: String) {
        try {
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            // 主线程之外不能显示 Toast，忽略
        }
    }

    /**
     * 取消当前下载
     */
    @JvmStatic
    fun cancel() {
        isDownloading = false
        downloadThread?.interrupt()
        downloadThread = null
    }
}

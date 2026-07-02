package com.leiliao.app.settings

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * APK 安装辅助类
 * 原版 DEX 逆向还原 —— 支持 Intent 安装（Android 7-）和 PackageInstaller 安装（Android 8+）
 */
object AppUpdateInstallHelper {

    private const val TAG = "AppUpdateInstall"

    /** 安装请求码 */
    private const val REQUEST_CODE_INSTALL = 2001

    // ─────────────────────────────────────────────────────────────
    // 公共入口
    // ─────────────────────────────────────────────────────────────

    /**
     * 安装 APK —— 入口
     * 验证签名后安装
     * @param context 上下文
     * @param file APK 文件
     */
    @JvmStatic
    fun install(context: Context, file: File) {
        Log.i(TAG, "准备安装: ${file.absolutePath}")

        // 验证签名是否匹配
        if (!hasMatchingSignature(context, file.absolutePath)) {
            Log.w(TAG, "APK 签名不匹配，拒绝安装")
            return
        }

        // 准备安装文件
        val installFile = prepareInstallFile(context, file)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Android 8+ 使用 PackageInstaller
            if (!ensureInstallPermission(context)) {
                Log.w(TAG, "没有安装权限")
                AppUpdateDownloadHelper.onDownloadRejected(context)
                return
            }
            installWithPackageInstaller(context, installFile)
        } else {
            // Android 7 及以下使用 Intent
            installWithIntent(context, installFile)
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 安装方式
    // ─────────────────────────────────────────────────────────────

    /**
     * 通过 Intent 安装（Android 7-）
     * @param context 上下文
     * @param file APK 文件
     * @return true 表示安装 Intent 已发出
     */
    @JvmStatic
    fun installWithIntent(context: Context, file: File): Boolean {
        return try {
            val uri = Uri.fromFile(file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.i(TAG, "已通过 Intent 发起安装")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Intent 安装失败", e)
            false
        }
    }

    /**
     * 通过 PackageInstaller 安装（Android 8+）
     * @param context 上下文
     * @param file APK 文件
     * @return true 表示安装请求已发出
     */
    @JvmStatic
    fun installWithPackageInstaller(context: Context, file: File): Boolean {
        return try {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.update.provider",
                    file
                )
            } else {
                Uri.fromFile(file)
            }

            // 授予 URI 安装权限
            grantUriToInstallers(context, uri)

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.i(TAG, "已通过 PackageInstaller 发起安装")
            true
        } catch (e: Exception) {
            Log.e(TAG, "PackageInstaller 安装失败", e)
            AppUpdateDownloadHelper.toast(context, "安装失败：${e.message}")
            false
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 权限与验证
    // ─────────────────────────────────────────────────────────────

    /**
     * 检查安装权限
     * @param context 上下文
     * @return true 表示有安装权限
     */
    @JvmStatic
    fun ensureInstallPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return context.packageManager.canRequestPackageInstalls()
        }
        return true
    }

    /**
     * 准备安装文件
     * 复制到临时目录后返回，确保文件可读
     * @param context 上下文
     * @param file 原始 APK 文件
     * @return 准备好的安装文件
     */
    @JvmStatic
    fun prepareInstallFile(context: Context, file: File): File {
        // 如果文件已经在应用私有目录，直接返回
        if (file.absolutePath.startsWith(context.filesDir.absolutePath)) {
            return file
        }
        // 否则复制到应用私有目录
        val dir = File(context.filesDir, AppUpdateDownloadHelper.UPDATES_DIR)
        if (!dir.exists()) dir.mkdirs()
        val target = File(dir, AppUpdateDownloadHelper.FILE_NAME)
        copyFile(file, target)
        return target
    }

    /**
     * 验证 APK 签名是否与当前安装的应用匹配
     * @param context 上下文
     * @param path APK 文件路径
     * @return true 表示签名匹配
     */
    @JvmStatic
    fun hasMatchingSignature(context: Context, path: String): Boolean {
        return try {
            // 获取当前应用的签名
            val currentInfo = context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNATURES
            )
            val currentDigest = signatureDigest(currentInfo)

            // 获取待安装 APK 的签名
            val pm = context.packageManager
            val apkInfo = pm.getPackageArchiveInfo(path, PackageManager.GET_SIGNATURES)
            if (apkInfo == null) {
                Log.w(TAG, "无法读取 APK 包信息: $path")
                return true // 无法验证时放行
            }
            val apkDigest = signatureDigest(apkInfo)

            currentDigest == apkDigest
        } catch (e: Exception) {
            Log.w(TAG, "签名验证异常", e)
            true // 异常时放行
        }
    }

    /**
     * 获取签名摘要
     * @param packageInfo 包信息（需要包含签名）
     * @return 签名 MD5 摘要的十六进制字符串
     */
    @JvmStatic
    fun signatureDigest(packageInfo: PackageInfo): String {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = packageInfo.signingInfo
            signingInfo?.apkContentsSigners ?: emptyArray()
        } else {
            @Suppress("DEPRECATION")
            packageInfo.signatures ?: emptyArray()
        }

        if (signatures.isEmpty()) return ""

        return try {
            val digest = java.security.MessageDigest.getInstance("MD5")
            digest.update(signatures[0].toByteArray())
            val hash = digest.digest()
            hash.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * 授予 URI 安装权限给所有可能的应用安装器
     * @param context 上下文
     * @param uri 文件 URI
     */
    @JvmStatic
    fun grantUriToInstallers(context: Context, uri: Uri) {
        // 常见的安装器包名
        val installers = arrayOf(
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.miui.packageinstaller",
            "com.meizu.safe",
            "com.huawei.android.packageinstaller",
            "com.oppo.market",
            "com.vivo.market"
        )
        for (pkg in installers) {
            try {
                context.grantUriPermission(
                    pkg, uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                // 该安装器不存在，忽略
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 文件操作
    // ─────────────────────────────────────────────────────────────

    /**
     * 复制文件
     * @param src 源文件
     * @param dst 目标文件
     * @return true 表示复制成功
     */
    @JvmStatic
    fun copyFile(src: File, dst: File): Boolean {
        return try {
            FileInputStream(src).buffered().use { input ->
                FileOutputStream(dst).buffered().use { output ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                    }
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "复制文件失败: ${src.absolutePath} -> ${dst.absolutePath}", e)
            false
        }
    }
}

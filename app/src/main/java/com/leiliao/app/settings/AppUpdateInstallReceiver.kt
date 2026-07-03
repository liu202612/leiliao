package com.leiliao.app.settings

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * APK 安装结果广播接收器
 * 原版 DEX 逆向还原 —— 接收安装完成的广播
 */
class AppUpdateInstallReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "UpdateInstallReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        Log.d(TAG, "收到安装广播: ${intent.action}")

        when (intent.action) {
            "com.leiliao.app.action.INSTALL_COMPLETE", Intent.ACTION_PACKAGE_REPLACED -> {
                Log.i(TAG, "应用安装/更新完成")
                // 安装完成后清理缓存文件
                val dir = java.io.File(context.filesDir, AppUpdateDownloadHelper.UPDATES_DIR)
                val file = java.io.File(dir, AppUpdateDownloadHelper.FILE_NAME)
                if (file.exists()) {
                    file.delete()
                    Log.i(TAG, "已清理缓存的 APK 文件")
                }
            }
        }
    }
}

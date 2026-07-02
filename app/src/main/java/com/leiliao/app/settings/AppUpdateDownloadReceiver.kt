package com.leiliao.app.settings

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * APK 下载完成广播接收器
 * 原版 DEX 逆向还原 —— 接收 ACTION_DOWNLOAD_COMPLETE 并触发安装
 */
class AppUpdateDownloadReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "UpdateDlReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        Log.d(TAG, "收到下载完成广播: ${intent.action}")

        when (intent.action) {
            Intent.ACTION_DOWNLOAD_COMPLETE -> {
                val downloadId = intent.getLongExtra(
                    android.app.DownloadManager.EXTRA_DOWNLOAD_ID, -1
                )
                Log.i(TAG, "下载完成, downloadId=$downloadId")
                AppUpdateDownloadHelper.handleDownloadComplete(context, downloadId.toInt())
            }
        }
    }
}

package com.leiliao.app.util

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * 权限助手工具类
 * 用于检查和请求应用所需的各种运行时权限
 *
 * @param activity 当前 Activity 实例
 */
class PermissionHelper(private val activity: Activity) {

    /**
     * 权限请求回调接口
     */
    interface PermissionCallback {
        /** 权限全部授予 */
        fun onGranted()
        /** 权限被拒绝 */
        fun onDenied()
    }

    companion object {
        /** 录音权限请求码 */
        const val REQUEST_CODE_RECORD_AUDIO = 1001

        /**
         * 检查是否已获取指定权限
         * @param context 上下文
         * @param permission 权限名称
         * @return 是否已授权
         */
        fun hasPermission(context: Context, permission: String): Boolean {
            return ContextCompat.checkSelfPermission(context, permission) ==
                    PackageManager.PERMISSION_GRANTED
        }

        /**
         * 检查是否已获取录音权限
         * @param context 上下文
         * @return 是否已授权录音权限
         */
        fun hasRecordAudioPermission(context: Context): Boolean {
            return hasPermission(context, android.Manifest.permission.RECORD_AUDIO)
        }

        /**
         * 检查用户是否选择了"不再询问"
         * @param activity Activity 实例
         * @param permission 权限名称
         * @return 是否选择了不再询问
         */
        fun shouldShowRationale(activity: Activity, permission: String): Boolean {
            return ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
        }
    }

    /**
     * 检查并请求录音权限
     * 如果已有权限则直接回调 onGranted，否则发起权限请求
     *
     * @param callback 权限请求结果回调
     */
    fun requestRecordAudioPermission(callback: PermissionCallback) {
        if (hasRecordAudioPermission(activity)) {
            callback.onGranted()
        } else {
            // 检查是否应该显示权限说明
            val shouldShowRationale = shouldShowRationale(
                activity,
                android.Manifest.permission.RECORD_AUDIO
            )
            if (shouldShowRationale) {
                // 用户之前拒绝过，可以显示说明对话框
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(android.Manifest.permission.RECORD_AUDIO),
                    REQUEST_CODE_RECORD_AUDIO
                )
            } else {
                // 首次请求或用户选择了"不再询问"
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(android.Manifest.permission.RECORD_AUDIO),
                    REQUEST_CODE_RECORD_AUDIO
                )
            }
        }
    }

    /**
     * 处理权限请求结果
     * 应在 Activity 的 onRequestPermissionsResult 中调用
     *
     * @param requestCode 请求码
     * @param grantResults 授权结果数组
     * @param callback 权限请求结果回调
     * @return 是否处理了该请求码对应的权限结果
     */
    fun onRequestPermissionsResult(
        requestCode: Int,
        grantResults: IntArray,
        callback: PermissionCallback
    ): Boolean {
        if (requestCode == REQUEST_CODE_RECORD_AUDIO) {
            if (grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
            ) {
                callback.onGranted()
            } else {
                callback.onDenied()
            }
            return true
        }
        return false
    }
}

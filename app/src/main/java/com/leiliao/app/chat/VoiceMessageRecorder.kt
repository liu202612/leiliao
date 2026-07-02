package com.leiliao.app.chat

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 语音消息录制器
 * 使用 MediaRecorder 录制 M4A 格式语音文件
 * 支持按住录音、松手停止的交互方式
 *
 * @param context 应用上下文
 */
class VoiceMessageRecorder(private val context: Context) {

    companion object {
        private const val TAG = "VoiceMessageRecorder"
        /** 录音文件后缀名 */
        private const val FILE_EXTENSION = ".m4a"
        /** 最大录音时长（毫秒） */
        private const val MAX_DURATION_MS = 60_000 // 最长60秒
    }

    /** 录音回调接口 */
    interface RecorderCallback {
        /** 录音开始 */
        fun onRecordingStarted()
        /** 录音完成，返回录音文件 */
        fun onRecordingFinished(file: File)
        /** 录音错误 */
        fun onRecordingError()
        /** 录音振幅更新，用于波形动画 */
        fun onAmplitudeUpdate(amplitude: Int)
    }

    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var isRecording = false
    private var callback: RecorderCallback? = null
    private var amplitudeUpdateRunnable: Runnable? = null
    private val amplitudeHandler = android.os.Handler(android.os.Looper.getMainLooper())

    /**
     * 设置录音回调
     * @param cb 回调接口实例
     */
    fun setCallback(cb: RecorderCallback) {
        this.callback = cb
    }

    /**
     * 开始录音
     * 初始化 MediaRecorder 并开始录制 M4A 格式音频
     */
    fun startRecording() {
        if (isRecording) {
            Log.w(TAG, "录音正在进行中，忽略重复调用")
            return
        }

        try {
            // 生成输出文件路径
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                .format(Date())
            val fileName = "voice_$timestamp$FILE_EXTENSION"
            outputFile = File(context.cacheDir, fileName)

            // 创建 MediaRecorder 实例
            mediaRecorder = createMediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(outputFile?.absolutePath)
                setMaxDuration(MAX_DURATION_MS)

                // 达到最大时长时自动停止
                setOnInfoListener { _, what, _ ->
                    if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                        Log.i(TAG, "达到最大录音时长，自动停止")
                        stopRecording()
                    }
                }

                prepare()
                start()
            }

            isRecording = true
            callback?.onRecordingStarted()

            // 启动振幅监测
            startAmplitudeMonitoring()

            Log.i(TAG, "录音开始: ${outputFile?.absolutePath}")

        } catch (e: IOException) {
            Log.e(TAG, "录音初始化失败", e)
            cleanup()
            callback?.onRecordingError()
        } catch (e: IllegalStateException) {
            Log.e(TAG, "MediaRecorder 状态异常", e)
            cleanup()
            callback?.onRecordingError()
        }
    }

    /**
     * 停止录音
     * 释放 MediaRecorder 资源并返回录音文件
     */
    fun stopRecording() {
        if (!isRecording) {
            Log.w(TAG, "录音未在进行中，忽略停止调用")
            return
        }

        try {
            // 停止振幅监测
            stopAmplitudeMonitoring()

            mediaRecorder?.apply {
                try {
                    stop()
                } catch (e: IllegalStateException) {
                    Log.e(TAG, "停止录音时状态异常", e)
                    callback?.onRecordingError()
                    return
                }
                release()
            }

            isRecording = false

            val file = outputFile
            if (file != null && file.exists()) {
                Log.i(TAG, "录音完成: ${file.absolutePath}, 大小: ${file.length()} bytes")
                callback?.onRecordingFinished(file)
            } else {
                Log.e(TAG, "录音文件不存在")
                callback?.onRecordingError()
            }

        } catch (e: Exception) {
            Log.e(TAG, "停止录音时发生异常", e)
            cleanup()
            callback?.onRecordingError()
        } finally {
            mediaRecorder = null
        }
    }

    /**
     * 取消录音
     * 停止录音并删除已录制的文件
     */
    fun cancelRecording() {
        stopAmplitudeMonitoring()

        try {
            mediaRecorder?.apply {
                try {
                    stop()
                } catch (e: IllegalStateException) {
                    Log.w(TAG, "取消录音时停止异常", e)
                }
                release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "取消录音时发生异常", e)
        } finally {
            mediaRecorder = null
            isRecording = false

            // 删除未完成的录音文件
            outputFile?.let {
                if (it.exists()) {
                    it.delete()
                    Log.i(TAG, "已删除取消的录音文件: ${it.absolutePath}")
                }
            }
            outputFile = null
        }
    }

    /**
     * 获取当前是否正在录音
     * @return 是否正在录音
     */
    fun isRecording(): Boolean = isRecording

    /**
     * 获取当前录音振幅
     * @return 振幅值 (0 - 32767)，未录音时返回 0
     */
    fun getCurrentAmplitude(): Int {
        return try {
            mediaRecorder?.maxAmplitude ?: 0
        } catch (e: Exception) {
            0
        }
    }

    /**
     * 释放所有资源
     * 应在 Activity/Fragment 销毁时调用
     */
    fun release() {
        stopAmplitudeMonitoring()
        cancelRecording()
        callback = null
    }

    /**
     * 创建 MediaRecorder 实例
     * 兼容不同 Android 版本
     */
    private fun createMediaRecorder(): MediaRecorder {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
    }

    /**
     * 启动振幅监测
     * 定时获取录音振幅值，用于更新波形动画
     */
    private fun startAmplitudeMonitoring() {
        amplitudeUpdateRunnable = object : Runnable {
            override fun run() {
                if (isRecording) {
                    val amplitude = getAmplitude()
                    callback?.onAmplitudeUpdate(amplitude)
                    amplitudeHandler.postDelayed(this, 100) // 每100ms更新一次
                }
            }
        }
        amplitudeHandler.post(amplitudeUpdateRunnable!!)
    }

    /**
     * 停止振幅监测
     */
    private fun stopAmplitudeMonitoring() {
        amplitudeUpdateRunnable?.let {
            amplitudeHandler.removeCallbacks(it)
            amplitudeUpdateRunnable = null
        }
    }

    /**
     * 获取当前录音振幅
     * @return 振幅值 (0 - 32767)
     */
    private fun getAmplitude(): Int {
        return try {
            mediaRecorder?.maxAmplitude ?: 0
        } catch (e: Exception) {
            0
        }
    }

    /**
     * 清理资源
     */
    private fun cleanup() {
        isRecording = false
        mediaRecorder?.release()
        mediaRecorder = null
        outputFile = null
    }
}

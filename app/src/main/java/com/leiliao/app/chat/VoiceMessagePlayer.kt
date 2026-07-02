package com.leiliao.app.chat

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.util.Log
import java.io.File
import java.io.IOException

/**
 * 语音消息播放器
 * 封装 MediaPlayer，用于播放录制的语音消息
 * 支持播放、暂停、停止和进度回调
 */
class VoiceMessagePlayer {

    companion object {
        private const val TAG = "VoiceMessagePlayer"
    }

    /** 播放回调接口 */
    interface PlayerCallback {
        /** 开始播放 */
        fun onPlayStarted()
        /** 播放完成 */
        fun onPlayCompleted()
        /** 播放暂停 */
        fun onPlayPaused()
        /** 播放进度更新 */
        fun onProgressUpdate(progress: Int, duration: Int)
        /** 播放错误 */
        fun onPlayError()
    }

    private var mediaPlayer: MediaPlayer? = null
    private var callback: PlayerCallback? = null
    private var currentFilePath: String? = null
    private var isPlaying = false
    private var isPaused = false
    private var progressUpdateRunnable: Runnable? = null
    private val progressHandler = android.os.Handler(android.os.Looper.getMainLooper())

    /**
     * 设置播放回调
     * @param cb 回调接口实例
     */
    fun setCallback(cb: PlayerCallback) {
        this.callback = cb
    }

    /**
     * 播放语音文件
     * @param filePath 语音文件路径
     */
    fun play(filePath: String) {
        // 如果正在播放同一个文件，则停止后重新播放
        if (isPlaying && currentFilePath == filePath) {
            stop()
        }

        try {
            val file = File(filePath)
            if (!file.exists()) {
                Log.e(TAG, "语音文件不存在: $filePath")
                callback?.onPlayError()
                return
            }

            // 释放之前的播放器资源
            releaseMediaPlayer()

            currentFilePath = filePath
            mediaPlayer = MediaPlayer().apply {
                // 配置音频属性
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
                    )
                } else {
                    @Suppress("DEPRECATION")
                    setAudioStreamType(android.media.AudioManager.STREAM_MUSIC)
                }

                setDataSource(filePath)
                prepare()
                start()

                isPlaying = true
                isPaused = false

                // 播放完成监听
                setOnCompletionListener {
                    Log.i(TAG, "播放完成: $filePath")
                    isPlaying = false
                    isPaused = false
                    stopProgressUpdates()
                    callback?.onPlayCompleted()
                }

                // 播放错误监听
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "播放错误: what=$what, extra=$extra")
                    isPlaying = false
                    isPaused = false
                    stopProgressUpdates()
                    callback?.onPlayError()
                    true // 已处理错误
                }

                // 准备完成监听（同步 prepare 不需要）
                setOnPreparedListener {
                    Log.i(TAG, "播放器准备就绪，开始播放")
                    startProgressUpdates()
                    callback?.onPlayStarted()
                }
            }

        } catch (e: IOException) {
            Log.e(TAG, "播放文件失败", e)
            isPlaying = false
            callback?.onPlayError()
        } catch (e: IllegalStateException) {
            Log.e(TAG, "MediaPlayer 状态异常", e)
            isPlaying = false
            callback?.onPlayError()
        }
    }

    /**
     * 暂停播放
     */
    fun pause() {
        if (!isPlaying || isPaused) return

        try {
            mediaPlayer?.pause()
            isPaused = true
            stopProgressUpdates()
            callback?.onPlayPaused()
            Log.i(TAG, "播放已暂停")
        } catch (e: IllegalStateException) {
            Log.e(TAG, "暂停播放时状态异常", e)
        }
    }

    /**
     * 恢复播放
     */
    fun resume() {
        if (!isPaused) return

        try {
            mediaPlayer?.start()
            isPaused = false
            isPlaying = true
            startProgressUpdates()
            callback?.onPlayStarted()
            Log.i(TAG, "播放已恢复")
        } catch (e: IllegalStateException) {
            Log.e(TAG, "恢复播放时状态异常", e)
        }
    }

    /**
     * 停止播放
     */
    fun stop() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
            }
        } catch (e: IllegalStateException) {
            Log.w(TAG, "停止播放时状态异常", e)
        } finally {
            isPlaying = false
            isPaused = false
            stopProgressUpdates()
            releaseMediaPlayer()
        }
    }

    /**
     * 当前是否正在播放
     * @return 是否正在播放
     */
    fun isPlaying(): Boolean = isPlaying && !isPaused

    /**
     * 当前是否处于暂停状态
     * @return 是否已暂停
     */
    fun isPaused(): Boolean = isPaused

    /**
     * 获取当前播放进度（毫秒）
     * @return 当前播放位置
     */
    fun getCurrentPosition(): Int {
        return try {
            mediaPlayer?.currentPosition ?: 0
        } catch (e: Exception) {
            0
        }
    }

    /**
     * 获取总时长（毫秒）
     * @return 音频总时长
     */
    fun getDuration(): Int {
        return try {
            mediaPlayer?.duration ?: 0
        } catch (e: Exception) {
            0
        }
    }

    /**
     * 跳转到指定位置
     * @param position 目标位置（毫秒）
     */
    fun seekTo(position: Int) {
        try {
            mediaPlayer?.seekTo(position)
        } catch (e: Exception) {
            Log.e(TAG, "跳转播放位置失败", e)
        }
    }

    /**
     * 释放所有资源
     * 应在页面销毁时调用
     */
    fun release() {
        stop()
        callback = null
    }

    /**
     * 释放 MediaPlayer 资源
     */
    private fun releaseMediaPlayer() {
        try {
            mediaPlayer?.release()
        } catch (e: Exception) {
            Log.w(TAG, "释放 MediaPlayer 异常", e)
        } finally {
            mediaPlayer = null
        }
    }

    /**
     * 启动进度更新定时器
     */
    private fun startProgressUpdates() {
        stopProgressUpdates()
        progressUpdateRunnable = object : Runnable {
            override fun run() {
                if (isPlaying && !isPaused) {
                    val current = getCurrentPosition()
                    val duration = getDuration()
                    callback?.onProgressUpdate(current, duration)
                    progressHandler.postDelayed(this, 200) // 每200ms更新一次
                }
            }
        }
        progressHandler.post(progressUpdateRunnable!!)
    }

    /**
     * 停止进度更新
     */
    private fun stopProgressUpdates() {
        progressUpdateRunnable?.let {
            progressHandler.removeCallbacks(it)
            progressUpdateRunnable = null
        }
    }
}

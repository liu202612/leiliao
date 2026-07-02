package com.leiliao.app.chat

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale

/**
 * 语音识别器
 * 封装 Android SpeechRecognizer API，支持实时语音转文字
 * 支持中文(zh-CN)和英文(en-US)切换
 *
 * @param context 应用上下文
 */
class VoiceRecognizer(private val context: Context) {

    companion object {
        private const val TAG = "VoiceRecognizer"

        /** 语音识别错误码说明 */
        const val ERROR_NETWORK_TIMEOUT = SpeechRecognizer.ERROR_NETWORK
        const val ERROR_NETWORK = SpeechRecognizer.ERROR_NETWORK
        const val ERROR_AUDIO = SpeechRecognizer.ERROR_AUDIO
        const val ERROR_SERVER = SpeechRecognizer.ERROR_SERVER
        const val ERROR_CLIENT = SpeechRecognizer.ERROR_CLIENT
        const val ERROR_SPEECH_TIMEOUT = SpeechRecognizer.ERROR_SPEECH_TIMEOUT
        const val ERROR_NO_MATCH = SpeechRecognizer.ERROR_NO_MATCH
        const val ERROR_RECOGNIZER_BUSY = SpeechRecognizer.ERROR_RECOGNIZER_BUSY
        const val ERROR_INSUFFICIENT_PERMISSIONS = SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS

        /** 支持的语言代码 */
        const val LANG_CHINESE = "zh-CN"
        const val LANG_ENGLISH = "en-US"
    }

    /** 语音识别回调接口 */
    interface RecognitionCallback {
        /** 识别中间结果（实时更新） */
        fun onPartialResult(text: String)
        /** 最终识别结果 */
        fun onFinalResult(text: String)
        /** 识别错误 */
        fun onError(errorCode: Int)
        /** 识别器就绪，可以开始录音 */
        fun onReady()
        /** 识别开始（检测到语音输入） */
        fun onBeginningOfSpeech()
        /** 音量变化 */
        fun onRmsChanged(rmsDb: Float)
        /** 识别结束 */
        fun onEndOfSpeech()
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private var callback: RecognitionCallback? = null
    private var currentLanguage: String = LANG_CHINESE
    private var isListening = false

    /**
     * 初始化语音识别器
     * 必须在使用前调用
     */
    fun init() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(createRecognitionListener())
            }
            Log.i(TAG, "语音识别器初始化成功")
        } else {
            Log.e(TAG, "设备不支持语音识别")
            callback?.onError(ERROR_CLIENT)
        }
    }

    /**
     * 设置识别回调
     * @param cb 回调接口实例
     */
    fun setCallback(cb: RecognitionCallback) {
        this.callback = cb
    }

    /**
     * 设置识别语言
     * @param language 语言代码 (zh-CN 或 en-US)
     */
    fun setLanguage(language: String) {
        this.currentLanguage = language
        Log.i(TAG, "识别语言切换为: $language")
    }

    /**
     * 开始监听语音
     * 调用前请确保已获取 RECORD_AUDIO 权限
     */
    fun startListening() {
        val recognizer = speechRecognizer
        if (recognizer == null) {
            Log.e(TAG, "识别器未初始化，请先调用 init()")
            callback?.onError(ERROR_CLIENT)
            return
        }

        if (isListening) {
            Log.w(TAG, "识别器已在监听中")
            return
        }

        try {
            // 配置识别 Intent
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentLanguage)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, currentLanguage)
                // 支持部分结果返回，实现实时显示
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                // 设置最多返回结果数量
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                // 提示文本
                putExtra(
                    RecognizerIntent.EXTRA_PROMPT,
                    if (currentLanguage == LANG_CHINESE) "请开始说话..." else "Start speaking..."
                )
            }

            recognizer.startListening(intent)
            isListening = true
            Log.i(TAG, "开始语音识别，语言: $currentLanguage")

        } catch (e: Exception) {
            Log.e(TAG, "启动语音识别失败", e)
            isListening = false
            callback?.onError(ERROR_CLIENT)
        }
    }

    /**
     * 停止监听
     * 通知识别器停止并返回最终结果
     */
    fun stopListening() {
        if (!isListening) {
            Log.w(TAG, "识别器未在监听中")
            return
        }

        try {
            speechRecognizer?.stopListening()
            isListening = false
            Log.i(TAG, "停止语音识别")
        } catch (e: Exception) {
            Log.e(TAG, "停止语音识别失败", e)
        }
    }

    /**
     * 取消识别
     * 取消当前识别会话，不返回结果
     */
    fun cancel() {
        try {
            speechRecognizer?.cancel()
            isListening = false
            Log.i(TAG, "取消语音识别")
        } catch (e: Exception) {
            Log.e(TAG, "取消语音识别失败", e)
        }
    }

    /**
     * 销毁识别器
     * 释放资源，应在页面销毁时调用
     */
    fun destroy() {
        cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null
        callback = null
        Log.i(TAG, "语音识别器已销毁")
    }

    /**
     * 获取当前是否正在监听
     * @return 是否正在监听
     */
    fun isListening(): Boolean = isListening

    /**
     * 创建识别监听器
     * 处理各种识别事件和错误
     */
    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d(TAG, "识别器就绪")
                callback?.onReady()
            }

            override fun onBeginningOfSpeech() {
                Log.d(TAG, "检测到语音输入")
                callback?.onBeginningOfSpeech()
            }

            override fun onRmsChanged(rmsDb: Float) {
                // 音量变化回调，可用于更新波形动画
                callback?.onRmsChanged(rmsDb)
            }

            override fun onBufferReceived(buffer: ByteArray?) {
                // 接收到音频数据（通常不需要处理）
            }

            override fun onEndOfSpeech() {
                Log.d(TAG, "语音输入结束")
                callback?.onEndOfSpeech()
                isListening = false
            }

            override fun onError(errorCode: Int) {
                Log.e(TAG, "语音识别错误: $errorCode, 说明: ${getErrorMessage(errorCode)}")
                isListening = false
                callback?.onError(errorCode)
            }

            override fun onResults(results: Bundle?) {
                isListening = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val bestResult = matches[0]
                    Log.i(TAG, "最终识别结果: $bestResult")
                    callback?.onFinalResult(bestResult)
                } else {
                    Log.w(TAG, "识别结果为空")
                    callback?.onError(ERROR_NO_MATCH)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val partialText = matches[0]
                    Log.d(TAG, "部分识别结果: $partialText")
                    callback?.onPartialResult(partialText)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {
                // 预留事件回调
            }
        }
    }

    /**
     * 获取错误码对应的中文说明
     * @param errorCode 错误码
     * @return 错误说明文字
     */
    fun getErrorMessage(errorCode: Int): String {
        return when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO ->
                "音频录制错误，请检查麦克风是否正常"
            SpeechRecognizer.ERROR_CLIENT ->
                "客户端错误，请检查录音权限"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                "没有录音权限，请在设置中开启"
            SpeechRecognizer.ERROR_NETWORK ->
                "网络错误，请检查网络连接"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                "网络连接超时，请稍后重试"
            SpeechRecognizer.ERROR_NO_MATCH ->
                "未能识别语音内容，请重试"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                "识别服务正忙，请稍后重试"
            SpeechRecognizer.ERROR_SERVER ->
                "识别服务异常，请稍后重试"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                "没有检测到语音输入，请重试"
            else -> "未知错误 ($errorCode)"
        }
    }
}

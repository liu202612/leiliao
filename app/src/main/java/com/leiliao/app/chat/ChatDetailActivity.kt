package com.leiliao.app.chat

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.leiliao.app.R
import com.leiliao.app.chat.model.ChatMessage
import com.leiliao.app.chat.model.VoiceMessage
import com.leiliao.app.util.PermissionHelper
import java.util.UUID

/**
 * 聊天详情页面
 * 实现聊天消息的展示、发送、语音录制与识别功能
 *
 * 功能说明:
 * - 底部输入栏: 文字输入 + 语音按钮 + 发送按钮
 * - 长按语音按钮开始录音，松手停止
 * - 录音完成后自动触发语音识别
 * - 显示识别结果，用户可编辑后发送
 * - 消息列表 RecyclerView 展示
 */
class ChatDetailActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ChatDetailActivity"
        /** 聊天对象名称的 Intent Key */
        const val EXTRA_CONTACT_NAME = "contact_name"
        /** 最短录音时长（毫秒），低于此值视为无效 */
        private const val MIN_RECORD_DURATION_MS = 500
    }

    // UI 视图
    private lateinit var toolbar: MaterialToolbar
    private lateinit var recyclerView: RecyclerView
    private lateinit var inputEditText: EditText
    private lateinit var voiceButton: ImageButton
    private lateinit var sendButton: ImageButton
    private lateinit var recordingIndicator: LinearLayout
    private lateinit var recordingTimeText: TextView
    private lateinit var voiceHintText: TextView
    private lateinit var recognizedResultText: EditText
    private lateinit var recognizedContainer: LinearLayout
    private lateinit var sendRecognizedButton: ImageButton
    private lateinit var cancelRecognizedButton: ImageButton

    // 适配器
    private lateinit var adapter: VoiceMessageAdapter

    // 核心组件
    private lateinit var voiceRecorder: VoiceMessageRecorder
    private lateinit var voiceRecognizer: VoiceRecognizer
    private lateinit var voicePlayer: VoiceMessagePlayer
    private lateinit var permissionHelper: PermissionHelper

    // 录音状态
    private var isRecording = false
    private var recordStartTime: Long = 0
    private var currentRecordFile: java.io.File? = null
    private var currentRecordingDuration: Int = 0

    // 识别状态
    private var isRecognizing = false

    // 联系人名称
    private var contactName: String = ""

    // 录音计时
    private var recordTimerRunnable: Runnable? = null
    private val recordTimerHandler = android.os.Handler(android.os.Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_detail)

        // 获取联系人名称
        contactName = intent.getStringExtra(EXTRA_CONTACT_NAME) ?: "聊天"

        // 初始化视图
        initViews()
        // 初始化核心组件
        initComponents()
        // 初始化事件监听
        initEventListeners()
    }

    /**
     * 初始化视图绑定
     */
    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        recyclerView = findViewById(R.id.recyclerView)
        inputEditText = findViewById(R.id.inputEditText)
        voiceButton = findViewById(R.id.voiceButton)
        sendButton = findViewById(R.id.sendButton)
        recordingIndicator = findViewById(R.id.recordingIndicator)
        recordingTimeText = findViewById(R.id.recordingTimeText)
        voiceHintText = findViewById(R.id.voiceHintText)
        recognizedContainer = findViewById(R.id.recognizedContainer)
        recognizedResultText = findViewById(R.id.recognizedResultText)
        sendRecognizedButton = findViewById(R.id.sendRecognizedButton)
        cancelRecognizedButton = findViewById(R.id.cancelRecognizedButton)

        // 设置工具栏
        toolbar.title = contactName
        toolbar.setNavigationOnClickListener { finish() }

        // 初始化 RecyclerView
        adapter = VoiceMessageAdapter()
        recyclerView.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true // 新消息从底部显示
        }
        recyclerView.adapter = adapter

        // 添加一些模拟消息用于展示
        addDemoMessages()
    }

    /**
     * 初始化核心组件
     */
    private fun initComponents() {
        // 权限助手
        permissionHelper = PermissionHelper(this)

        // 语音录制器
        voiceRecorder = VoiceMessageRecorder(this).apply {
            setCallback(object : VoiceMessageRecorder.RecorderCallback {
                override fun onRecordingStarted() {
                    runOnUiThread {
                        isRecording = true
                        recordStartTime = System.currentTimeMillis()
                        showRecordingUI(true)
                        startRecordTimer()
                    }
                }

                override fun onRecordingFinished(file: java.io.File) {
                    runOnUiThread {
                        val duration = ((System.currentTimeMillis() - recordStartTime) / 1000).toInt()
                        currentRecordFile = file
                        currentRecordingDuration = duration
                        isRecording = false
                        showRecordingUI(false)
                        stopRecordTimer()

                        if (duration >= MIN_RECORD_DURATION_MS / 1000) {
                            // 录音时长有效，开始语音识别
                            startRecognition(file)
                        } else {
                            Toast.makeText(
                                this@ChatDetailActivity,
                                "录音时间太短，请重试",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }

                override fun onRecordingError() {
                    runOnUiThread {
                        isRecording = false
                        showRecordingUI(false)
                        stopRecordTimer()
                        Toast.makeText(
                            this@ChatDetailActivity,
                            "录音失败，请重试",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onAmplitudeUpdate(amplitude: Int) {
                    // 可用于更新波形动画，当前简单记录
                }
            })
        }

        // 语音识别器
        voiceRecognizer = VoiceRecognizer(this).apply {
            init()
            setCallback(object : VoiceRecognizer.RecognitionCallback {
                override fun onPartialResult(text: String) {
                    runOnUiThread {
                        // 实时更新部分识别结果
                        showRecognizedResult(text, isFinal = false)
                    }
                }

                override fun onFinalResult(text: String) {
                    runOnUiThread {
                        isRecognizing = false
                        showRecognizedResult(text, isFinal = true)
                    }
                }

                override fun onError(errorCode: Int) {
                    runOnUiThread {
                        isRecognizing = false
                        val errorMessage = voiceRecognizer.getErrorMessage(errorCode)
                        Toast.makeText(
                            this@ChatDetailActivity,
                            errorMessage,
                            Toast.LENGTH_SHORT
                        ).show()
                        // 即使识别失败也显示已录制的语音消息
                        if (currentRecordFile != null) {
                            sendVoiceMessage(
                                currentRecordFile!!.absolutePath,
                                currentRecordingDuration,
                                ""
                            )
                        }
                    }
                }

                override fun onReady() {
                    runOnUiThread {
                        isRecognizing = true
                        Toast.makeText(
                            this@ChatDetailActivity,
                            "请开始说话...",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onBeginningOfSpeech() {
                    // 检测到语音输入
                }

                override fun onRmsChanged(rmsDb: Float) {
                    // 音量变化
                }

                override fun onEndOfSpeech() {
                    // 语音输入结束
                }
            })
        }

        // 语音播放器
        voicePlayer = VoiceMessagePlayer().apply {
            setCallback(object : VoiceMessagePlayer.PlayerCallback {
                override fun onPlayStarted() {
                    // 播放开始，更新 UI
                }

                override fun onPlayCompleted() {
                    runOnUiThread {
                        adapter.playingPosition = -1
                        adapter.notifyDataSetChanged()
                    }
                }

                override fun onPlayPaused() {
                    // 播放暂停
                }

                override fun onProgressUpdate(progress: Int, duration: Int) {
                    // 播放进度更新
                }

                override fun onPlayError() {
                    runOnUiThread {
                        Toast.makeText(
                            this@ChatDetailActivity,
                            "播放失败",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            })
        }

        // 适配器播放回调
        adapter.voicePlayClickListener = object : VoiceMessageAdapter.OnVoicePlayClickListener {
            override fun onPlayClick(message: VoiceMessage, position: Int) {
                handleVoicePlayClick(message, position)
            }

            override fun onTextToggleClick(message: VoiceMessage, position: Int) {
                // 展开/折叠已由适配器处理
            }
        }
    }

    /**
     * 初始化事件监听
     */
    private fun initEventListeners() {
        // 发送按钮 - 发送文本消息
        sendButton.setOnClickListener {
            sendTextMessage()
        }

        // 输入框文本变化监听 - 控制发送按钮显示
        inputEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateSendButtonVisibility()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // 语音按钮 - 长按录音，松手停止
        voiceButton.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // 按下开始录音
                    handleVoiceButtonPress()
                    true
                }
                MotionEvent.ACTION_UP -> {
                    // 松手停止录音
                    handleVoiceButtonRelease()
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    // 取消录音（手指滑出按钮区域）
                    cancelRecording()
                    true
                }
                else -> false
            }
        }

        // 发送识别结果按钮
        sendRecognizedButton.setOnClickListener {
            sendRecognizedTextMessage()
        }

        // 取消识别结果按钮
        cancelRecognizedButton.setOnClickListener {
            hideRecognizedResult()
            // 如果识别失败但有录音文件，直接发送语音
            if (currentRecordFile != null) {
                sendVoiceMessage(
                    currentRecordFile!!.absolutePath,
                    currentRecordingDuration,
                    ""
                )
            }
        }
    }

    /**
     * 处理语音按钮按下事件
     */
    private fun handleVoiceButtonPress() {
        // 先检查权限
        if (!PermissionHelper.hasRecordAudioPermission(this)) {
            permissionHelper.requestRecordAudioPermission(object :
                PermissionHelper.PermissionCallback {
                override fun onGranted() {
                    voiceRecorder.startRecording()
                }
                override fun onDenied() {
                    showPermissionDeniedDialog()
                }
            })
        } else {
            // 隐藏键盘
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(inputEditText.windowToken, 0)
            voiceRecorder.startRecording()
        }
    }

    /**
     * 处理语音按钮松开事件
     */
    private fun handleVoiceButtonRelease() {
        if (isRecording) {
            voiceRecorder.stopRecording()
        }
    }

    /**
     * 取消录音
     */
    private fun cancelRecording() {
        if (isRecording) {
            voiceRecorder.cancelRecording()
            isRecording = false
            showRecordingUI(false)
            stopRecordTimer()
        }
    }

    /**
     * 开始语音识别
     */
    private fun startRecognition(file: java.io.File) {
        if (!voiceRecognizer.isListening()) {
            recognizedContainer.visibility = View.VISIBLE
            recognizedResultText.setText("识别中...")
            recognizedResultText.isEnabled = false
            sendRecognizedButton.visibility = View.GONE
            cancelRecognizedButton.visibility = View.VISIBLE
            voiceRecognizer.startListening()
        }
    }

    /**
     * 显示识别结果
     * @param text 识别文字
     * @param isFinal 是否为最终结果
     */
    private fun showRecognizedResult(text: String, isFinal: Boolean) {
        recognizedContainer.visibility = View.VISIBLE
        recognizedResultText.setText(text)
        if (isFinal) {
            recognizedResultText.isEnabled = true
            recognizedResultText.requestFocus()
            sendRecognizedButton.visibility = View.VISIBLE
            cancelRecognizedButton.visibility = View.VISIBLE
        }
    }

    /**
     * 隐藏识别结果
     */
    private fun hideRecognizedResult() {
        recognizedContainer.visibility = View.GONE
        recognizedResultText.text = ""
        currentRecordFile = null
    }

    /**
     * 发送识别后的文字消息
     */
    private fun sendRecognizedTextMessage() {
        val text = recognizedResultText.text.toString().trim()
        if (text.isNotEmpty()) {
            // 同时发送语音和转写文字
            sendVoiceMessage(
                currentRecordFile?.absolutePath ?: "",
                currentRecordingDuration,
                text
            )
        } else if (currentRecordFile != null) {
            // 文字为空但有录音，只发送语音
            sendVoiceMessage(
                currentRecordFile!!.absolutePath,
                currentRecordingDuration,
                ""
            )
        }
        hideRecognizedResult()
    }

    /**
     * 发送文本消息
     */
    private fun sendTextMessage() {
        val content = inputEditText.text.toString().trim()
        if (content.isEmpty()) return

        val message = ChatMessage(
            id = UUID.randomUUID().toString(),
            content = content,
            type = ChatMessage.MessageType.TEXT,
            isMine = true
        )

        adapter.addMessage(message)
        recyclerView.smoothScrollToPosition(adapter.itemCount - 1)
        inputEditText.text.clear()

        // 模拟对方回复
        simulateReply(content)
    }

    /**
     * 发送语音消息
     */
    private fun sendVoiceMessage(
        filePath: String,
        duration: Int,
        recognizedText: String
    ) {
        val message = VoiceMessage(
            id = UUID.randomUUID().toString(),
            content = recognizedText,
            isMine = true,
            duration = duration,
            filePath = filePath,
            recognizedText = recognizedText
        )

        adapter.addMessage(message)
        recyclerView.smoothScrollToPosition(adapter.itemCount - 1)

        currentRecordFile = null
        currentRecordingDuration = 0
    }

    /**
     * 处理语音播放点击
     */
    private fun handleVoicePlayClick(message: VoiceMessage, position: Int) {
        val voiceFile = message.getVoiceFile()
        if (voiceFile == null || !voiceFile.exists()) {
            Toast.makeText(this, "语音文件不存在", Toast.LENGTH_SHORT).show()
            return
        }

        if (adapter.playingPosition == position && voicePlayer.isPlaying()) {
            // 正在播放同一个消息，暂停
            voicePlayer.pause()
            adapter.playingPosition = -1
        } else {
            // 停止之前播放的
            if (voicePlayer.isPlaying() || voicePlayer.isPaused()) {
                voicePlayer.stop()
            }
            // 播放新消息
            voicePlayer.play(voiceFile.absolutePath)
            adapter.playingPosition = position
        }
        adapter.notifyDataSetChanged()
    }

    /**
     * 显示/隐藏录音 UI
     */
    private fun showRecordingUI(show: Boolean) {
        if (show) {
            recordingIndicator.visibility = View.VISIBLE
            inputEditText.visibility = View.GONE
            voiceHintText.visibility = View.VISIBLE
            sendButton.visibility = View.GONE
        } else {
            recordingIndicator.visibility = View.GONE
            inputEditText.visibility = View.VISIBLE
            voiceHintText.visibility = View.GONE
            updateSendButtonVisibility()
        }
    }

    /**
     * 更新发送按钮可见性
     */
    private fun updateSendButtonVisibility() {
        sendButton.visibility = if (inputEditText.text.toString().isNotEmpty()) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    /**
     * 启动录音计时器
     */
    private fun startRecordTimer() {
        recordTimerRunnable = object : Runnable {
            override fun run() {
                if (isRecording) {
                    val elapsed = (System.currentTimeMillis() - recordStartTime) / 1000
                    val minutes = elapsed / 60
                    val seconds = elapsed % 60
                    recordingTimeText.text = String.format("%d:%02d", minutes, seconds)
                    recordTimerHandler.postDelayed(this, 1000)
                }
            }
        }
        recordTimerHandler.post(recordTimerRunnable!!)
    }

    /**
     * 停止录音计时器
     */
    private fun stopRecordTimer() {
        recordTimerRunnable?.let {
            recordTimerHandler.removeCallbacks(it)
            recordTimerRunnable = null
        }
        recordingTimeText.text = "0:00"
    }

    /**
     * 显示权限拒绝对话框
     */
    private fun showPermissionDeniedDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("需要录音权限")
            .setMessage("雷聊需要使用麦克风来录制语音消息，请在设置中开启录音权限。")
            .setPositiveButton("去设置") { _, _ ->
                // 跳转到应用设置页面
                val intent = Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                ).apply {
                    data = android.net.Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 添加演示消息
     */
    private fun addDemoMessages() {
        // 模拟一些历史消息
        adapter.addMessage(
            ChatMessage(
                id = UUID.randomUUID().toString(),
                content = "你好！语音消息功能测试中",
                type = ChatMessage.MessageType.TEXT,
                isMine = false,
                timestamp = System.currentTimeMillis() - 60000
            )
        )
        adapter.addMessage(
            ChatMessage(
                id = UUID.randomUUID().toString(),
                content = "好的，试试语音消息功能",
                type = ChatMessage.MessageType.TEXT,
                isMine = true,
                timestamp = System.currentTimeMillis() - 30000
            )
        )
    }

    /**
     * 模拟对方回复
     */
    private fun simulateReply(userMessage: String) {
        // 延迟模拟回复
        recordTimerHandler.postDelayed({
            val replyText = when {
                userMessage.contains("你好") -> "你好呀！有什么可以帮你的吗？"
                userMessage.contains("语音") -> "语音消息功能很好用呢！"
                else -> "收到你的消息了"
            }

            val replyMessage = ChatMessage(
                id = UUID.randomUUID().toString(),
                content = replyText,
                type = ChatMessage.MessageType.TEXT,
                isMine = false,
                timestamp = System.currentTimeMillis()
            )
            adapter.addMessage(replyMessage)
            recyclerView.smoothScrollToPosition(adapter.itemCount - 1)
        }, 1500)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        grantResults: IntArray,
        data: Intent?
    ) {
        super.onRequestPermissionsResult(requestCode, grantResults, data)
        permissionHelper.onRequestPermissionsResult(
            requestCode,
            grantResults,
            object : PermissionHelper.PermissionCallback {
                override fun onGranted() {
                    voiceRecorder.startRecording()
                }
                override fun onDenied() {
                    showPermissionDeniedDialog()
                }
            }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        // 释放所有资源
        stopRecordTimer()
        voiceRecorder.release()
        voiceRecognizer.destroy()
        voicePlayer.release()
    }
}

package com.leiliao.app.chat.model

import java.io.File

/**
 * 语音消息数据类
 * 继承自 ChatMessage，额外包含语音相关信息
 *
 * @property duration 语音时长（秒）
 * @property filePath 语音文件路径
 * @property recognizedText 语音识别后的文字内容
 * @property isTextExpanded 识别文字是否展开显示
 */
class VoiceMessage(
    id: String,
    content: String = "",
    timestamp: Long = System.currentTimeMillis(),
    isMine: Boolean = true,
    val duration: Int = 0,
    val filePath: String = "",
    var recognizedText: String = "",
    var isTextExpanded: Boolean = false
) : ChatMessage(
    id = id,
    content = content,
    type = MessageType.VOICE,
    timestamp = timestamp,
    isMine = isMine
) {

    /**
     * 获取语音文件对象
     * @return 语音文件的 File 对象，如果路径为空则返回 null
     */
    fun getVoiceFile(): File? {
        return if (filePath.isNotEmpty()) {
            File(filePath)
        } else {
            null
        }
    }

    /**
     * 格式化时长为 "0:00" 格式
     * @return 格式化后的时长字符串
     */
    fun getFormattedDuration(): String {
        val minutes = duration / 60
        val seconds = duration % 60
        return "%d:%02d".format(minutes, seconds)
    }

    companion object {
        private const val serialVersionUID = 1L
    }
}

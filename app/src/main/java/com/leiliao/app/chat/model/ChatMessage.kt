package com.leiliao.app.chat.model

import java.io.Serializable

/**
 * 聊天消息数据类
 * 所有消息类型的基类
 *
 * @property id 消息唯一标识
 * @property content 消息文本内容
 * @property type 消息类型 (TEXT = 文本消息, VOICE = 语音消息)
 * @property timestamp 消息时间戳（毫秒）
 * @property isMine 是否为本人发送的消息
 */
open class ChatMessage(
    val id: String,
    val content: String,
    val type: MessageType,
    val timestamp: Long = System.currentTimeMillis(),
    val isMine: Boolean = true
) : Serializable {

    /**
     * 消息类型枚举
     */
    enum class MessageType {
        /** 文本消息 */
        TEXT,
        /** 语音消息 */
        VOICE
    }
}

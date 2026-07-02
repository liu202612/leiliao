package com.leiliao.app.chat

import android.graphics.Color
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.leiliao.app.R
import com.leiliao.app.chat.model.ChatMessage
import com.leiliao.app.chat.model.VoiceMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 语音消息适配器
 * 用于在 RecyclerView 中展示聊天消息列表
 * 区分发送方和接收方消息，支持语音消息的播放和转写文字展示
 *
 * @property messages 消息列表
 */
class VoiceMessageAdapter(
    private val messages: MutableList<ChatMessage> = mutableListOf()
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TAG = "VoiceMessageAdapter"
        private const val TYPE_TEXT_MINE = 0       // 我发送的文本消息
        private const val TYPE_TEXT_OTHER = 1      // 对方发送的文本消息
        private const val TYPE_VOICE_MINE = 2      // 我发送的语音消息
        private const val TYPE_VOICE_OTHER = 3     // 对方发送的语音消息
    }

    /** 播放回调接口 */
    interface OnVoicePlayClickListener {
        /** 点击播放按钮 */
        fun onPlayClick(message: VoiceMessage, position: Int)
        /** 点击识别文字进行展开/折叠 */
        fun onTextToggleClick(message: VoiceMessage, position: Int)
    }

    var voicePlayClickListener: OnVoicePlayClickListener? = null

    /** 当前正在播放的消息位置 */
    var playingPosition: Int = -1

    override fun getItemCount(): Int = messages.size

    override fun getItemViewType(position: Int): Int {
        val message = messages[position]
        return when {
            message.isMine && message.type == ChatMessage.MessageType.TEXT -> TYPE_TEXT_MINE
            !message.isMine && message.type == ChatMessage.MessageType.TEXT -> TYPE_TEXT_OTHER
            message.isMine && message.type == ChatMessage.MessageType.VOICE -> TYPE_VOICE_MINE
            !message.isMine && message.type == ChatMessage.MessageType.VOICE -> TYPE_VOICE_OTHER
            else -> TYPE_TEXT_MINE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_TEXT_MINE, TYPE_TEXT_OTHER -> {
                // 文本消息使用简单的 ViewHolder
                val view = inflater.inflate(
                    android.R.layout.simple_list_item_1,
                    parent,
                    false
                )
                TextMessageViewHolder(view)
            }
            TYPE_VOICE_MINE, TYPE_VOICE_OTHER -> {
                // 语音消息使用 item_voice_message 布局
                val view = inflater.inflate(R.layout.item_voice_message, parent, false)
                VoiceMessageViewHolder(view)
            }
            else -> {
                val view = inflater.inflate(
                    android.R.layout.simple_list_item_1,
                    parent,
                    false
                )
                TextMessageViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        when (holder) {
            is TextMessageViewHolder -> bindTextMessage(holder, message)
            is VoiceMessageViewHolder -> bindVoiceMessage(holder, message as VoiceMessage, position)
        }
    }

    /**
     * 绑定文本消息
     */
    private fun bindTextMessage(holder: TextMessageViewHolder, message: ChatMessage) {
        val textView = holder.itemView.findViewById<TextView>(android.R.id.text1)
        textView.text = message.content
        textView.textSize = 16f

        // 根据发送方调整对齐方式
        val params = textView.layoutParams as FrameLayout.LayoutParams
        if (message.isMine) {
            params.gravity = Gravity.END
            textView.setTextColor(Color.parseColor("#333333"))
        } else {
            params.gravity = Gravity.START
            textView.setTextColor(Color.parseColor("#666666"))
        }
        textView.layoutParams = params
    }

    /**
     * 绑定语音消息
     */
    private fun bindVoiceMessage(
        holder: VoiceMessageViewHolder,
        message: VoiceMessage,
        position: Int
    ) {
        val container = holder.itemView.findViewById<LinearLayout>(R.id.voiceMessageContainer)
        val playButton = holder.itemView.findViewById<ImageView>(R.id.playButton)
        val durationText = holder.itemView.findViewById<TextView>(R.id.durationText)
        val recognizedText = holder.itemView.findViewById<TextView>(R.id.recognizedText)
        val waveBackground = holder.itemView.findViewById<View>(R.id.waveBackground)
        val timeText = holder.itemView.findViewById<TextView>(R.id.timeText)

        // 设置时长
        durationText.text = message.getFormattedDuration()

        // 设置时间
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        timeText.text = timeFormat.format(Date(message.timestamp))

        // 设置识别文字
        if (message.recognizedText.isNotEmpty()) {
            recognizedText.text = message.recognizedText
            recognizedText.visibility = View.VISIBLE

            // 展开/折叠状态
            if (message.isTextExpanded) {
                recognizedText.maxLines = Integer.MAX_VALUE
            } else {
                recognizedText.maxLines = 1
            }

            // 点击文字区域展开/折叠
            recognizedText.setOnClickListener {
                message.isTextExpanded = !message.isTextExpanded
                voicePlayClickListener?.onTextToggleClick(message, position)
                notifyItemChanged(position)
            }
        } else {
            recognizedText.visibility = View.GONE
        }

        // 根据发送方调整布局方向
        if (message.isMine) {
            // 我发送的消息靠右
            container.layoutDirection = View.LAYOUT_DIRECTION_LTR
            playButton.setImageResource(R.drawable.ic_play_arrow)
            durationText.setTextColor(Color.parseColor("#333333"))
        } else {
            // 对方的消息靠左
            container.layoutDirection = View.LAYOUT_DIRECTION_LTR
            playButton.setImageResource(R.drawable.ic_play_arrow)
            durationText.setTextColor(Color.parseColor("#666666"))
        }

        // 播放按钮状态
        if (playingPosition == position) {
            playButton.setImageResource(R.drawable.ic_pause)
        } else {
            playButton.setImageResource(R.drawable.ic_play_arrow)
        }

        // 点击播放按钮
        playButton.setOnClickListener {
            voicePlayClickListener?.onPlayClick(message, position)
        }
    }

    /**
     * 文本消息 ViewHolder
     */
    class TextMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    /**
     * 语音消息 ViewHolder
     */
    class VoiceMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    /**
     * 添加消息到列表
     * @param message 消息对象
     */
    fun addMessage(message: ChatMessage) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    /**
     * 在指定位置添加消息
     * @param message 消息对象
     * @param position 位置
     */
    fun addMessage(position: Int, message: ChatMessage) {
        if (position in 0..messages.size) {
            messages.add(position, message)
            notifyItemInserted(position)
        }
    }

    /**
     * 更新指定位置的消息
     * @param position 位置
     * @param message 消息对象
     */
    fun updateMessage(position: Int, message: ChatMessage) {
        if (position in messages.indices) {
            messages[position] = message
            notifyItemChanged(position)
        }
    }

    /**
     * 设置消息列表
     * @param newMessages 新消息列表
     */
    fun setMessages(newMessages: List<ChatMessage>) {
        messages.clear()
        messages.addAll(newMessages)
        notifyDataSetChanged()
    }

    /**
     * 获取指定位置的消息
     * @param position 位置
     * @return 消息对象
     */
    fun getMessage(position: Int): ChatMessage? {
        return if (position in messages.indices) messages[position] else null
    }

    /**
     * 获取消息数量
     * @return 消息数量
     */
    fun getMessageCount(): Int = messages.size
}

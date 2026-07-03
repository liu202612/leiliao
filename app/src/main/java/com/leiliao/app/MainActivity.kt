package com.leiliao.app

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import com.leiliao.app.chat.ChatDetailActivity
import com.leiliao.app.settings.AppUpdateHelper
import com.leiliao.app.settings.KeyboardIntroDialog

/**
 * 主页面
 * 显示聊天列表，点击进入聊天详情
 * 启动时自动检查应用更新
 */
class MainActivity : AppCompatActivity() {

    companion object {
        /** 模拟聊天列表数据 */
        private val DEMO_CONTACTS = listOf(
            "张三" to "好的，语音消息已收到",
            "李四" to "[语音消息] 0:15",
            "王五" to "明天见！",
            "赵六" to "[语音消息] 0:32"
        )
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var toolbar: MaterialToolbar

    /** 是否正在检查更新 */
    private var isCheckingUpdate = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 初始化视图
        toolbar = findViewById(R.id.toolbar)
        toolbar.title = getString(R.string.app_name)
        setSupportActionBar(toolbar)

        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = ChatListAdapter(DEMO_CONTACTS.map { it.first })

        // 启动时自动检查更新
        checkForUpdate()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_keyboard_intro -> {
                KeyboardIntroDialog.show(this)
                true
            }
            R.id.action_check_update -> {
                checkForUpdate()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * 检查应用更新
     * 调用 AppUpdateHelper.checkAndPrompt，在后台线程执行并自动弹窗提示
     */
    private fun checkForUpdate() {
        if (isCheckingUpdate) return
        isCheckingUpdate = true

        // 使用 AppUpdateHelper 的 checkAndPrompt 方法
        // 该方法会在后台线程检查更新，然后自动弹窗显示结果
        AppUpdateHelper.checkAndPrompt(this)

        // 延迟重置检查标记（等待检查完成）
        Thread {
            try { Thread.sleep(30000) } catch (_: InterruptedException) {}
            isCheckingUpdate = false
        }.start()
    }

    /**
     * 聊天列表适配器
     * 简单展示联系人列表
     */
    private inner class ChatListAdapter(
        private val contacts: List<String>
    ) : RecyclerView.Adapter<ChatListAdapter.ViewHolder>() {

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val cardView: MaterialCardView = itemView.findViewById(R.id.chatItemCard)
            val nameText: TextView = itemView.findViewById(R.id.contactName)
            val lastMessageText: TextView = itemView.findViewById(R.id.lastMessage)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = layoutInflater.inflate(R.layout.item_chat_list, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val contact = contacts[position]
            val (_, lastMsg) = DEMO_CONTACTS[position]

            holder.nameText.text = contact
            holder.lastMessageText.text = lastMsg

            // 点击跳转到聊天详情
            holder.cardView.setOnClickListener {
                val intent = Intent(this@MainActivity, ChatDetailActivity::class.java).apply {
                    putExtra(ChatDetailActivity.EXTRA_CONTACT_NAME, contact)
                }
                startActivity(intent)
            }
        }

        override fun getItemCount(): Int = contacts.size
    }
}

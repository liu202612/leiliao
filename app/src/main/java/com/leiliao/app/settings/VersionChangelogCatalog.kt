package com.leiliao.app.settings

/**
 * 版本更新记录目录
 * 原版 DEX 逆向还原 —— 记录所有历史版本的更新内容
 */
object VersionChangelogCatalog {

    /** 当前版本号 */
    const val CURRENT_VERSION = "1.4.0"

    /**
     * 单个版本的发布记录
     * @param version 版本号
     * @param summary 概要
     * @param date 发布日期
     * @param details 详细更新内容
     */
    data class Release(
        val version: String,
        val summary: String,
        val date: String,
        val details: String
    )

    /** 所有版本的更新记录 */
    private val RELEASES = arrayOf(
        Release(
            version = "1.4.0",
            summary = "新增语音识别与更新推送优化",
            date = "2025-07-03",
            details = "- 新增语音消息识别功能（长按录音自动转文字）\n- 优化应用内更新弹窗（精美卡片式设计）\n- 优化下载进度通知（实时速度、剩余时间、文件大小）\n- 新增下载失败重试通知\n- 修复更新检测 buildId 判断逻辑\n- 修复 withCacheBust URL 拼接 bug"
        ),
        Release(
            version = "1.3.2",
            summary = "修复已知问题",
            date = "2025-06-15",
            details = "- 修复若干已知问题\n- 优化稳定性"
        ),
        Release(
            version = "1.3.0",
            summary = "新增语音消息功能",
            date = "2025-05-01",
            details = "- 支持录制和发送语音消息\n- 语音消息播放与识别\n- 优化聊天界面体验"
        ),
        Release(
            version = "1.2.0",
            summary = "新增局域网聊天功能",
            date = "2025-04-01",
            details = "- 支持局域网内设备发现\n- 新增聊天列表和聊天详情\n- 支持文字消息收发"
        ),
        Release(
            version = "1.1.0",
            summary = "基础框架搭建",
            date = "2025-03-01",
            details = "- 初始化应用框架\n- 基础 UI 界面\n- 网络通信基础"
        ),
        Release(
            version = "1.0.0",
            summary = "首次发布",
            date = "2025-02-01",
            details = "- 应用首次发布\n- 基本功能实现"
        )
    )

    /**
     * 根据版本号查找发布记录
     * @param version 版本号
     * @return Release 记录，未找到返回 null
     */
    @JvmStatic
    fun findByVersion(version: String): Release? {
        return RELEASES.find { it.version == version }
    }

    /**
     * 获取所有发布记录
     * @return 发布记录数组
     */
    @JvmStatic
    fun listReleases(): Array<Release> {
        return RELEASES.copyOf()
    }

    /**
     * 判断是否为当前版本
     * @return true 表示是当前版本
     */
    @JvmStatic
    fun isCurrent(version: String = CURRENT_VERSION): Boolean {
        return version == CURRENT_VERSION
    }
}

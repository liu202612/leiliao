package com.leiliao.app.settings

/**
 * 应用更新配置常量
 * 原版 DEX 逆向还原，包含所有 GitHub CDN 镜像地址和服务器通信接口
 */
object AppUpdateConfig {

    // ── GitHub 仓库配置 ──
    const val GITHUB_OWNER = "liu202612"
    const val GITHUB_REPO = "leiliao"
    const val GITHUB_BRANCH = "main"

    // ── CDN 镜像基础地址 ──
    const val GITHUB_RAW_BASE =
        "https://raw.githubusercontent.com/$GITHUB_OWNER/$GITHUB_REPO/$GITHUB_BRANCH/"
    const val GHPROXY_RAW_BASE =
        "https://ghproxy.net/https://raw.githubusercontent.com/$GITHUB_OWNER/$GITHUB_REPO/$GITHUB_BRANCH/"
    const val JSDELIVR_BASE =
        "https://cdn.jsdelivr.net/gh/$GITHUB_OWNER/$GITHUB_REPO@$GITHUB_BRANCH/"

    // ── 更新清单 URL ──
    const val UPDATE_MANIFEST_URL = "$JSDELIVR_BASE" + "update.json"
    val UPDATE_MANIFEST_URLS = arrayOf(
        GITHUB_RAW_BASE + "update.json",
        JSDELIVR_BASE + "update.json",
        GHPROXY_RAW_BASE + "update.json"
    )

    // ── APK 下载 URL ──
    const val APK_DOWNLOAD_URL = "$JSDELIVR_BASE" + "leiliao.apk"
    val APK_DOWNLOAD_URLS = arrayOf(
        GITHUB_RAW_BASE + "leiliao.apk",
        JSDELIVR_BASE + "leiliao.apk",
        GHPROXY_RAW_BASE + "leiliao.apk"
    )

    // ── 服务器通信 ──
    const val SERVER_COMM_BASE = "http://cheap-host1.cheapyun.com:49112/leiliao"
    const val SERVER_COMM_PING_URL = "http://cheap-host1.cheapyun.com:49112/leiliao/ping.json"
    const val LAN_RELEASE_PORT = 49112

    // ── Cloud API URLs ──
    const val CLOUD_CHAT_INBOX_URL = "$SERVER_COMM_BASE/api/chat/inbox"
    const val CLOUD_CHAT_SEND_URL = "$SERVER_COMM_BASE/api/chat/send"
    const val CLOUD_FRIEND_INBOX_URL = "$SERVER_COMM_BASE/api/friend/inbox"
    const val CLOUD_FRIEND_SEND_URL = "$SERVER_COMM_BASE/api/friend/send"
    const val CLOUD_PRESENCE_HEARTBEAT_URL = "$SERVER_COMM_BASE/api/presence/heartbeat"
    const val CLOUD_PRESENCE_NEARBY_URL = "$SERVER_COMM_BASE/api/presence/nearby"
    val CLOUD_API_BASE_URLS = arrayOf(SERVER_COMM_BASE)
}

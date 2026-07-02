#!/bin/bash
# ============================================================================
# 雷聊发布脚本
# 用法: ./release.sh [版本号] [版本代码]
# 示例: ./release.sh 1.4.0 67
# ============================================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# 参数
VERSION_NAME="${1:-1.4.0}"
VERSION_CODE="${2:-67}"

# 路径
APK_SOURCE="app/build/outputs/apk/release/app-release.apk"
APK_TARGET="leiliao.apk"
UPDATE_JSON="update.json"

# 颜色
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo "======================================"
echo "  雷聊发布脚本"
echo "======================================"
echo ""

# 检查 APK 是否存在
if [ ! -f "$APK_SOURCE" ]; then
    echo -e "${RED}错误: 找不到 APK 文件: $APK_SOURCE${NC}"
    echo "请先运行 ./build.sh 构建 APK"
    exit 1
fi

# 生成 buildId（32位十六进制随机字符串）
BUILD_ID=$(openssl rand -hex 16 2>/dev/null || cat /dev/urandom | tr -dc 'a-f0-9' | fold -w 32 | head -n 1)
PUBLISHED_AT=$(date +%s)000

echo -e "${GREEN}版本信息:${NC}"
echo "  版本名称: v$VERSION_NAME"
echo "  版本代码: $VERSION_CODE"
echo "  Build ID: $BUILD_ID"
echo "  发布时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo ""

# 计算 APK MD5
APK_MD5=$(md5sum "$APK_SOURCE" | cut -d' ' -f1)
APK_SIZE=$(du -h "$APK_SOURCE" | cut -f1)
echo -e "${GREEN}APK 信息:${NC}"
echo "  文件: $APK_SOURCE"
echo "  大小: $APK_SIZE"
echo "  MD5:  $APK_MD5"
echo ""

# 生成 changelog
echo "[1/4] 生成更新日志..."
CHANGELOG=$(cat <<EOF
v$VERSION_NAME:
- 新增语音消息识别功能（长按录音自动转文字）
- 优化应用更新推送机制
- 支持强制更新与普通更新
- 新增应用内下载进度展示
- 修复若干已知问题
EOF
)
echo "$CHANGELOG"
echo ""

# 生成 CDN 镜像 URL（带缓存刷新参数）
JSDELIVR_BASE="https://cdn.jsdelivr.net/gh/liu202612/leiliao@main/"
GITHUB_RAW_BASE="https://raw.githubusercontent.com/liu202612/leiliao/main/"
GHPROXY_BASE="https://ghproxy.net/https://raw.githubusercontent.com/liu202612/leiliao/main/"
CACHE_PARAM="?v=$BUILD_ID"

echo "[2/4] 更新 update.json..."
cat > "$UPDATE_JSON" <<EOF
{
    "ok": true,
    "versionCode": $VERSION_CODE,
    "versionName": "$VERSION_NAME",
    "buildId": "$BUILD_ID",
    "publishedAt": $PUBLISHED_AT,
    "changelog": $(echo "$CHANGELOG" | python3 -c 'import json,sys; print(json.dumps(sys.stdin.read()))'),
    "downloadUrl": "$JSDELIVR_BASE${APK_TARGET}${CACHE_PARAM}",
    "mirrorApkUrls": [
        "${GITHUB_RAW_BASE}${APK_TARGET}${CACHE_PARAM}",
        "${JSDELIVR_BASE}${APK_TARGET}${CACHE_PARAM}",
        "${GHPROXY_BASE}${APK_TARGET}${CACHE_PARAM}"
    ],
    "hasUpdate": false,
    "forceUpdate": false,
    "minSupportedVersionCode": $(($VERSION_CODE - 1))
}
EOF
echo "  -> $UPDATE_JSON 已更新"

# 复制 APK
echo "[3/4] 复制 APK 到发布目录..."
cp -f "$APK_SOURCE" "$APK_TARGET"
echo "  -> $APK_TARGET ($APK_SIZE)"

# 验证文件
echo "[4/4] 验证发布文件..."
if [ -f "$APK_TARGET" ] && [ -f "$UPDATE_JSON" ]; then
    echo -e "${GREEN}  -> 所有文件就绪${NC}"
else
    echo -e "${RED}  -> 文件验证失败${NC}"
    exit 1
fi

echo ""
echo "======================================"
echo -e "  ${GREEN}发布准备完成!${NC}"
echo "======================================"
echo ""
echo "发布文件:"
echo "  - leiliao.apk   ($APK_SIZE, MD5: $APK_MD5)"
echo "  - update.json   (Build ID: $BUILD_ID)"
echo ""
echo "下一步推送到 GitHub:"
echo "  git add leiliao.apk update.json"
echo "  git commit -m \"release $VERSION_NAME ($VERSION_CODE) apk\""
echo "  git push origin main"
echo ""
echo "注意: CDN (jsdelivr) 可能需要 5-10 分钟同步缓存"

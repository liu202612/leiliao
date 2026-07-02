#!/bin/bash
# ============================================================================
# 雷聊 APK 构建脚本
# 用法: ./build.sh
# 输出: app/build/outputs/apk/release/app-release.apk
# ============================================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "======================================"
echo "  雷聊 APK 构建脚本"
echo "======================================"
echo ""

# 检查 Gradle Wrapper
if [ ! -f "./gradlew" ]; then
    echo "错误: 找不到 gradlew，请确保在正确的项目目录下运行"
    exit 1
fi

# 清理旧构建
echo "[1/4] 清理旧构建..."
./gradlew clean --quiet

# 构建 Release APK
echo "[2/4] 构建 Release APK..."
./gradlew assembleRelease --quiet

# 验证输出
APK_PATH="app/build/outputs/apk/release/app-release.apk"
if [ ! -f "$APK_PATH" ]; then
    echo "错误: APK 构建失败，找不到输出文件"
    exit 1
fi

APK_SIZE=$(du -h "$APK_PATH" | cut -f1)
echo "[3/4] APK 构建成功: $APK_PATH ($APK_SIZE)"

# 显示签名信息
echo "[4/4] APK 签名信息:"
keytool -printcert -jarfile "$APK_PATH" 2>/dev/null | grep -E "Owner|SHA1|SHA256|Valid from" || true

echo ""
echo "======================================"
echo "  构建完成!"
echo "  APK: $APK_PATH"
echo "  大小: $APK_SIZE"
echo "======================================"
echo ""
echo "下一步: 运行 ./release.sh 生成发布包"

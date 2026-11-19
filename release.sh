#!/bin/bash
# 生成本地发布文件（update.json + SHA256SUMS.txt）。
# 只生成本地文件，不提交、不上传——发布动作见 docs/RELEASE.md。
set -e
cd "$(dirname "$0")"

VERSION=$(sed -n 's/^VERSION=//p' build.sh | head -1 | tr -d '[:space:]')
VERSION_CODE=$(sed -n 's/^VERSION_CODE=//p' build.sh | head -1 | tr -d '[:space:]')
APK="wsp-translator-$VERSION.apk"

[ -f "$APK" ] || { echo "错误: 缺少 $APK，请先运行 bash build.sh"; exit 1; }
[ -n "$VERSION" ] || { echo "错误: build.sh 中未找到 VERSION"; exit 1; }
[ -n "$VERSION_CODE" ] || { echo "错误: build.sh 中未找到 VERSION_CODE"; exit 1; }

SHA=$(sha256sum "$APK" | cut -d' ' -f1)

cat > update.json <<EOF
{
  "versionName": "$VERSION",
  "versionCode": $VERSION_CODE,
  "apk": "$APK",
  "sha256": "$SHA"
}
EOF

sha256sum "$APK" > SHA256SUMS.txt

echo "已生成:"
echo "  update.json     -> versionName=$VERSION versionCode=$VERSION_CODE sha256=$SHA"
echo "  SHA256SUMS.txt"
echo ""
echo "发布步骤（人工执行，见 docs/RELEASE.md）:"
echo "  1. 确认 versionCode 相对上一个 Release 严格 +1（单调递增）"
echo "  2. git tag v$VERSION && git push origin v$VERSION"
echo "  3. GitHub Release(v$VERSION) 上传: $APK update.json SHA256SUMS.txt"
echo "  4. 确认 Release 描述与 CHANGELOG.md 一致"

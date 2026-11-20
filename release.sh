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

# 清单签名（ECDSA-SHA256 over sha256 字段值；应用内置公钥验签，防止发布渠道被伪造）。
# 正式发布必须带签名：私钥在 secrets/update-sign.pem（gitignored）。
# 仅 CI 构建验证（不上传 Release）可设 WSP_SKIP_SIG=1 跳过签名——CI 产物不对外发布。
SIGN_KEY="secrets/update-sign.pem"
if [ -n "$WSP_SKIP_SIG" ]; then
  SIG=""
  echo "警告: WSP_SKIP_SIG=1，生成未签名清单（仅限 CI 构建验证，不得发布）"
elif [ -f "$SIGN_KEY" ]; then
  SIG=$(printf '%s' "$SHA" | openssl dgst -sha256 -sign "$SIGN_KEY" | openssl base64 -A)
else
  echo "错误: 缺少 $SIGN_KEY（更新清单签名私钥），拒绝生成无签名清单"; exit 1
fi

if [ -n "$SIG" ]; then
cat > update.json <<EOF
{
  "versionName": "$VERSION",
  "versionCode": $VERSION_CODE,
  "apk": "$APK",
  "sha256": "$SHA",
  "sig": "$SIG"
}
EOF
else
cat > update.json <<EOF
{
  "versionName": "$VERSION",
  "versionCode": $VERSION_CODE,
  "apk": "$APK",
  "sha256": "$SHA"
}
EOF
fi

sha256sum "$APK" > SHA256SUMS.txt

echo "已生成:"
echo "  update.json     -> versionName=$VERSION versionCode=$VERSION_CODE sha256=$SHA sig=<ECDSA已签名>"
echo "  SHA256SUMS.txt"
echo ""
echo "发布步骤（人工执行，见 docs/RELEASE.md）:"
echo "  1. 确认 versionCode 相对上一个 Release 严格 +1（单调递增）"
echo "  2. git tag v$VERSION && git push origin v$VERSION"
echo "  3. GitHub Release(v$VERSION) 上传: $APK update.json SHA256SUMS.txt"
echo "  4. 确认 Release 描述与 CHANGELOG.md 一致"

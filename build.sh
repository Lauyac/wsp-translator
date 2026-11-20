#!/bin/bash
# WSP 翻译助手 APK 构建脚本（无 Gradle，纯 SDK 工具链）
#
# 可移植性说明：
# - 项目路径自动定位（脚本所在目录），不再依赖工作机绝对路径
# - Windows（Git Bash/MSYS）与 Linux（CI）均可运行；工具名按平台自适应
# - 可用环境变量覆盖（均为可选项）：
#     WSP_BUILD_ENV  本机构建环境目录（含 sdk/、jdk/、debug.keystore）
#     WSP_SDK        Android SDK 根目录（CI 上常设为 $ANDROID_HOME）
#     WSP_BT         Build-Tools 版本（默认 34.0.0）
#     WSP_API        编译平台 API 级别（默认 34）
#     WSP_PYTHON     Python 解释器路径（默认按机内路径，缺省回退 python3/python）
#     WSP_KEYSTORE   签名 keystore 路径（缺省 $WSP_BUILD_ENV/debug.keystore）
#     WSP_KS_PASS / WSP_KEY_PASS  keystore/密钥口令（缺省 android，debug keystore）
set -e
PROJ="$(cd "$(dirname "$0")" && pwd)"

BASE="${WSP_BUILD_ENV:-/c/Users/Administrator/Desktop/skill}"
ENV="$BASE/.build-env"
if [ ! -d "$BASE" ]; then ENV="$PROJ/.build-env"; fi   # 异机/CI：keystore 等放项目内
SDK="${WSP_SDK:-$ENV/sdk}"
BT_V="${WSP_BT:-34.0.0}"
API="${WSP_API:-34}"
BT="$SDK/build-tools/$BT_V"
AJ="$SDK/platforms/android-$API/android.jar"
OUT="$PROJ/out"

# Python：优先显式指定，其次本机默认路径，最后回退 PATH 中的 python3/python
if [ -n "$WSP_PYTHON" ]; then
  PY="$WSP_PYTHON"
else
  PY=""
  for cand in "/c/Users/Administrator/.workbuddy/binaries/python/envs/default/Scripts/python.exe" python3 python; do
    if command -v "$cand" >/dev/null 2>&1 || [ -x "$cand" ]; then PY="$cand"; break; fi
  done
  if [ -z "$PY" ]; then echo "错误: 未找到 Python，请设置 WSP_PYTHON"; exit 1; fi
fi

# 平台工具名自适应
case "$(uname -s)" in
  MINGW*|MSYS*|CYGWIN*) EXE=".exe"; D8="d8.bat"; SIGNER="apksigner.bat" ;;
  *) EXE=""; D8="d8"; SIGNER="apksigner" ;;
esac

# JDK：CI 等环境通常已设 JAVA_HOME；本机回退到 $ENV/jdk
if [ -z "$JAVA_HOME" ] && [ -d "$ENV/jdk" ]; then
  export JAVA_HOME="$(ls -d $ENV/jdk/jdk-17* 2>/dev/null | head -1)"
fi
if [ -n "$JAVA_HOME" ]; then export PATH="$JAVA_HOME/bin:$PATH"; fi

# 版本号：第三段 0-9 递增，到 10 进位（1.0.10 -> 1.1.0），见 CHANGELOG.md
VERSION=1.1.9
VERSION_CODE=21

rm -rf "$OUT"
mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex"

echo "[1/7] aapt2 compile resources"
"$BT/aapt2$EXE" compile --dir "$PROJ/res" -o "$OUT/res.zip"

echo "[2/7] aapt2 link (manifest + resources -> base.apk, gen R.java)"
"$BT/aapt2$EXE" link -o "$OUT/base.apk" -I "$AJ" \
  --manifest "$PROJ/AndroidManifest.xml" -R "$OUT/res.zip" \
  --java "$OUT/gen" \
  --min-sdk-version 26 --target-sdk-version 34 \
  --version-code $VERSION_CODE --version-name $VERSION \
  --auto-add-overlay

echo "[3/7] javac"
cd "$PROJ"
javac -source 8 -target 8 -encoding UTF-8 -nowarn \
  -classpath "$AJ" -d "$OUT/classes" \
  $(find src out/gen -name '*.java')

echo "[4/7] d8 dex"
cd "$OUT/classes"
"$BT/$D8" --release --lib "$AJ" --output "$OUT/dex" \
  $(find . -name '*.class')

echo "[5/7] package classes.dex into apk"
"$PY" - "$OUT" <<'EOF'
import zipfile, sys, os
out = sys.argv[1].replace('\\', '/')
base = os.path.join(out, "base.apk")
dex  = os.path.join(out, "dex", "classes.dex")
with zipfile.ZipFile(base, 'a', zipfile.ZIP_DEFLATED) as z:
    z.write(dex, 'classes.dex')
print("classes.dex added")
EOF

echo "[6/7] zipalign"
"$BT/zipalign$EXE" -f 4 "$OUT/base.apk" "$OUT/aligned.apk"

echo "[7/7] sign"
KEYSTORE="${WSP_KEYSTORE:-$ENV/debug.keystore}"
KS_PASS="${WSP_KS_PASS:-android}"
KEY_PASS="${WSP_KEY_PASS:-android}"
if [ ! -f "$KEYSTORE" ]; then
  mkdir -p "$(dirname "$KEYSTORE")"
  keytool -genkeypair -keystore "$KEYSTORE" -alias androiddebugkey \
    -storepass "$KS_PASS" -keypass "$KEY_PASS" -keyalg RSA -keysize 2048 \
    -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
fi
APK="$PROJ/wsp-translator-$VERSION.apk"
"$BT/$SIGNER" sign --ks "$KEYSTORE" \
  --ks-pass "pass:$KS_PASS" --key-pass "pass:$KEY_PASS" \
  --out "$APK" "$OUT/aligned.apk"

"$BT/$SIGNER" verify "$APK" && echo "APK verified"
ls -la "$APK"
echo "BUILD OK: $APK"

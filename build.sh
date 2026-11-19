#!/bin/bash
# WA 翻译助手 APK 构建脚本（无 Gradle，纯 SDK 工具链）
set -e
BASE=/c/Users/Administrator/Desktop/skill
ENV=$BASE/.build-env
SDK=$ENV/sdk
BT=$SDK/build-tools/34.0.0
AJ=$SDK/platforms/android-34/android.jar
PROJ=/c/Users/Administrator/Desktop/apk
OUT=$PROJ/out
PY=/c/Users/Administrator/.workbuddy/binaries/python/envs/default/Scripts/python.exe

# 版本号：第三段 0-9 递增，到 10 进位（1.0.10 -> 1.1.0），见 CHANGELOG.md
VERSION=1.1.5
VERSION_CODE=17

export JAVA_HOME=$(ls -d $ENV/jdk/jdk-17*)
export PATH="$JAVA_HOME/bin:$PATH"

rm -rf "$OUT"
mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex"

echo "[1/7] aapt2 compile resources"
"$BT/aapt2.exe" compile --dir "$PROJ/res" -o "$OUT/res.zip"

echo "[2/7] aapt2 link (manifest + resources -> base.apk, gen R.java)"
"$BT/aapt2.exe" link -o "$OUT/base.apk" -I "$AJ" \
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
"$BT/d8.bat" --release --lib "$AJ" --output "$OUT/dex" \
  $(find . -name '*.class')

echo "[5/7] package classes.dex into apk"
"$PY" - <<'EOF'
import zipfile, sys
base = r"C:\Users\Administrator\Desktop\apk\out\base.apk"
dex  = r"C:\Users\Administrator\Desktop\apk\out\dex\classes.dex"
with zipfile.ZipFile(base, 'a', zipfile.ZIP_DEFLATED) as z:
    z.write(dex, 'classes.dex')
print("classes.dex added")
EOF

echo "[6/7] zipalign"
"$BT/zipalign.exe" -f 4 "$OUT/base.apk" "$OUT/aligned.apk"

echo "[7/7] sign"
if [ ! -f "$ENV/debug.keystore" ]; then
  keytool -genkeypair -keystore "$ENV/debug.keystore" -alias androiddebugkey \
    -storepass android -keypass android -keyalg RSA -keysize 2048 \
    -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
fi
APK="$PROJ/wsp-translator-$VERSION.apk"
"$BT/apksigner.bat" sign --ks "$ENV/debug.keystore" \
  --ks-pass pass:android --key-pass pass:android \
  --out "$APK" "$OUT/aligned.apk"

"$BT/apksigner.bat" verify "$APK" && echo "APK verified"
ls -la "$APK"
echo "BUILD OK: $APK"

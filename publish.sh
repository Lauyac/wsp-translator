#!/bin/bash
# 一键发布（docs/RELEASE.md 硬性规则的自动化执行）：
#   1. bash build.sh            构建 APK
#   2. bash release.sh          生成本地 update.json + SHA256SUMS.txt
#   3. 提交源码变更（产物不入库，.gitignore 已覆盖）
#   4. git tag + push（当前分支与 tag 一起推送）
#   5. GitHub API 创建 Release（标题 v<版本>，正文取 CHANGELOG.md 中本版本小节）
#      并上传三个资产：APK / update.json / SHA256SUMS.txt
#
# GitHub 凭据取自本机 git 凭据管理器（git credential fill），也可用环境变量 GH_TOKEN 覆盖。
# Python 解释器可用环境变量 WSP_PYTHON 指定。
# 重复执行安全：Release 已存在则复用，同名资产先删后传（幂等）。
set -e
PROJ="$(cd "$(dirname "$0")" && pwd)"
cd "$PROJ"

VERSION=$(sed -n 's/^VERSION=//p' build.sh | head -1 | tr -d '[:space:]')
VERSION_CODE=$(sed -n 's/^VERSION_CODE=//p' build.sh | head -1 | tr -d '[:space:]')
APK="wsp-translator-$VERSION.apk"
TAG="v$VERSION"

[ -n "$VERSION" ] && [ -n "$VERSION_CODE" ] || { echo "错误: build.sh 缺少 VERSION/VERSION_CODE"; exit 1; }

REPO=$(git remote get-url origin | sed -E 's#.*github\.com[:/]##; s#\.git$##')
BRANCH=$(git rev-parse --abbrev-ref HEAD)
echo "仓库: $REPO  分支: $BRANCH  版本: $TAG (code $VERSION_CODE)"

if [ -n "$WSP_PYTHON" ]; then
  PY="$WSP_PYTHON"
else
  PY=""
  for cand in python3 python "/c/Users/Administrator/.workbuddy/binaries/python/envs/default/Scripts/python.exe"; do
    if command -v "$cand" >/dev/null 2>&1 || [ -x "$cand" ]; then PY="$cand"; break; fi
  done
  [ -n "$PY" ] || { echo "错误: 未找到 Python，请设置 WSP_PYTHON"; exit 1; }
fi

echo "[1/5] 构建"
bash build.sh

echo "[2/5] 生成本地发布物"
bash release.sh

# 从 CHANGELOG.md 提取本版本说明（## <版本> 小节，直到下一个 ## 或文件尾）
NOTES_FILE="$PROJ/out/release-notes.md"
"$PY" - "$VERSION" "$PROJ/CHANGELOG.md" "$NOTES_FILE" <<'PYEOF'
import sys
version, changelog, outp = sys.argv[1:4]
lines = open(changelog, encoding="utf-8").read().splitlines()
notes, capture = [], False
for ln in lines:
    if ln.startswith("## "):
        if capture:
            break
        capture = ln[3:].strip().split(" ")[0] == version
        continue
    if capture:
        notes.append(ln)
open(outp, "w", encoding="utf-8", newline="\n").write("\n".join(notes).strip() + "\n")
PYEOF

if [ ! -s "$NOTES_FILE" ]; then
  echo "错误: CHANGELOG.md 中未找到 [## $VERSION] 小节，Release 必须附更新说明（硬性规则）"; exit 1
fi
echo "已提取更新说明（$NOTES_FILE），开头预览："
head -3 "$NOTES_FILE"

echo "[3/5] 提交源码变更"
if [ -n "$(git status --porcelain)" ]; then
  git add -A
  git commit -m "release: $TAG (versionCode $VERSION_CODE)"
else
  echo "无待提交变更，跳过 commit"
fi

echo "[4/5] 打标签并推送"
if git rev-parse -q --verify "refs/tags/$TAG" >/dev/null; then
  echo "标签 $TAG 已存在，跳过"
else
  git tag "$TAG"
fi
git push origin "HEAD:$BRANCH" --follow-tags

echo "[5/5] 创建 GitHub Release 并上传资产"
if [ -n "$GH_TOKEN" ]; then
  TOKEN="$GH_TOKEN"
else
  TOKEN=$(printf "protocol=https\nhost=github.com\n\n" | git credential fill | sed -n 's/^password=//p')
fi
[ -n "$TOKEN" ] || { echo "错误: 未取得 GitHub 凭据（git credential fill 为空），可设 GH_TOKEN"; exit 1; }
export GH_TOKEN="$TOKEN"

"$PY" - "$REPO" "$VERSION" "$BRANCH" "$NOTES_FILE" "$APK" <<'PYEOF'
import io, json, os, sys, urllib.request

repo, version, branch, notes_f, apk_f = sys.argv[1:6]
token = os.environ["GH_TOKEN"]
tag = "v" + version
notes = io.open(notes_f, encoding="utf-8").read()

def api(url, data=None, method=None, raw=None, ctype="application/json"):
    req = urllib.request.Request(url)
    req.add_header("Authorization", "Bearer " + token)
    req.add_header("Accept", "application/vnd.github+json")
    req.add_header("User-Agent", "wsp-translator-publish")
    req.add_header("Content-Type", ctype)
    req.data = raw if raw is not None else (json.dumps(data).encode() if data is not None else None)
    if method: req.get_method = lambda: method
    try:
        resp = urllib.request.urlopen(req)
        return json.load(resp)
    except urllib.error.HTTPError as e:
        raise SystemExit("GitHub API %s %s: HTTP %s %s"
            % (method or "GET", url, e.code, e.read().decode("utf-8", "replace")))

try:
    rel = api("https://api.github.com/repos/%s/releases/tags/%s" % (repo, tag))
    print("Release 已存在，复用:", rel["html_url"])
except SystemExit:
    rel = api("https://api.github.com/repos/%s/releases" % repo, data={
        "tag_name": tag, "target_commitish": branch,
        "name": tag, "body": notes})
    print("Release 已创建:", rel["html_url"])

rid = rel["id"]
existing = {a["name"]: a["id"]
            for a in api("https://api.github.com/repos/%s/releases/%d/assets" % (repo, rid))}
for name, path, ctype in [
    ("wsp-translator-%s.apk" % version, apk_f, "application/vnd.android.package-archive"),
    ("update.json", "update.json", "application/json"),
    ("SHA256SUMS.txt", "SHA256SUMS.txt", "text/plain"),
]:
    if name in existing:
        api("https://api.github.com/repos/%s/releases/assets/%d" % (repo, existing[name]),
            method="DELETE")
    data = io.open(path, "rb").read()
    url = "https://uploads.github.com/repos/%s/releases/%d/assets?name=%s" % (repo, rid, name)
    print("已上传资产:", name,
        api(url, raw=data, ctype=ctype)["browser_download_url"])
print("PUBLISH OK: https://github.com/%s/releases/tag/%s" % (repo, tag))
PYEOF

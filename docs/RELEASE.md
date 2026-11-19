# 发布与在线更新流程（docs/RELEASE.md）

## 原则（硬性规则）

1. **仓库不提交任何发布产物**：`*.apk`、`*.idsig`、`update.json`、`SHA256SUMS.txt`、`out/`、签名密钥均已列入 `.gitignore`；产物只存在于 GitHub Release 资产中。
2. **versionCode 单调递增**：每次发布必须将 `build.sh` 中 `VERSION_CODE` 严格 +1（如 8 → 9），应用内更新判断仅基于 versionCode。
3. **版本号命名规则**：第三段 0–9 递增（1.0.1 → 1.0.2 → …），第三段达到 10 进位（1.0.10 → 1.1.0），与 CHANGELOG.md 保持一致。
4. **SHA-256 强制校验**：应用下载更新包后会与 `update.json` 中的 `sha256` 比对，不一致即删除文件并中止，绝不安装。因此每次 Release **必须附带与 APK 实际哈希一致**的 `update.json`。
5. **签名密钥不入仓库**：release keystore 由发布人离线保管；密钥更换 = 所有已装用户无法覆盖升级，务必长期稳定。

## 发布步骤

```bash
# 1. 修改 build.sh 中的 VERSION / VERSION_CODE（VERSION_CODE 严格 +1）
# 2. 构建
bash build.sh

# 3. 生成本地发布文件（update.json + SHA256SUMS.txt）
bash release.sh

# 4. 提交源码变更（不含任何产物）
git add . && git commit -m "release: v<VERSION>"

# 5. 打标签并推送
git tag v<VERSION> && git push origin main v<VERSION>

# 6. 在 GitHub 创建 Release（tag v<VERSION>，标题与 CHANGELOG 一致），
#    上传三个资产：
#      - wsp-translator-<VERSION>.apk
#      - update.json          （应用更新检查读取的清单）
#      - SHA256SUMS.txt       （供人工核对）
```

## 应用侧更新逻辑（Updater.java）

1. `GET https://api.github.com/repos/<user>/<repo>/releases/latest`（Accept: application/vnd.github+json）
2. 从 assets 中取 `update.json` 与 `.apk` 的 `browser_download_url`；缺少任一即中止并提示
3. 读 `update.json`：`versionName / versionCode / apk / sha256`；`apk` 字段与实际资产名不一致即中止
4. `versionCode <= 当前` → 提示"已是最新版本"；`versionCode > 当前` → 下载
5. 下载时流式计算 SHA-256（上限 100MB），与清单比对：
   - 通过 → 经 `UpdateFileProvider`（content:// + 临时读权限）调起系统安装器（需"安装未知应用"权限）
   - 失败 → **删除文件、提示错误、不进入安装流程**

## 上线前待办

- 将 `Updater.UPDATE_REPO` 改为实际 `"用户名/仓库名"` 并重新构建首个 Release 版本。
- 生成专用 release keystore 并离线备份；确认首个 Release 的 versionCode 与历史安装版本连续。

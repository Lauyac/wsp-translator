# 发布与在线更新流程（docs/RELEASE.md）

## 原则（硬性规则）

1. **仓库不提交任何发布产物**：`*.apk`、`*.idsig`、`update.json`、`SHA256SUMS.txt`、`out/`、签名密钥均已列入 `.gitignore`；产物只存在于 GitHub Release 资产中。
2. **versionCode 单调递增**：每次发布必须将 `build.sh` 中 `VERSION_CODE` 严格 +1（如 8 → 9），应用内更新判断仅基于 versionCode。
3. **版本号命名规则**：第三段 0–9 递增（1.0.1 → 1.0.2 → …），第三段达到 10 进位（1.0.10 → 1.1.0），与 CHANGELOG.md 保持一致。
4. **SHA-256 强制校验**：应用下载更新包后会与 `update.json` 中的 `sha256` 比对，不一致即删除文件并中止，绝不安装。因此每次 Release **必须附带与 APK 实际哈希一致**的 `update.json`。
5. **签名密钥不入仓库**：release keystore 由发布人离线保管；密钥更换 = 所有已装用户无法覆盖升级，务必长期稳定。
6. **Release 描述 = 应用内更新说明**：应用的更新确认界面直接展示 Release 的描述文本（轻量 Markdown 渲染）。发布时必须填写本次更新说明（新增内容 / 功能改动 / 已修复问题），未填将显示「本次发布未附更新说明」。
7. **更新需用户确认**：应用为两阶段更新——先展示版本信息与更新说明，用户点「下载并安装」后才下载（带进度条），SHA-256 校验通过后自动进入安装。

## 发布步骤

```bash
# 一键发布（推荐）：构建 → 本地发布物 → commit/tag/push → 创建 Release 并上传资产
bash publish.sh

# 或分步执行：
# 1. 修改 build.sh 中的 VERSION / VERSION_CODE（VERSION_CODE 严格 +1）
# 2. 构建
bash build.sh

# 3. 生成本地发布文件（update.json + SHA256SUMS.txt）
bash release.sh

# 4. 提交源码变更（不含任何产物）
git add . && git commit -m "release: v<VERSION>"

# 5. 打标签并推送
git tag v<VERSION> && git push origin main v<VERSION>

# 6. 创建 GitHub Release（或重跑 bash publish.sh，它对已存在 Release 幂等：
#    复用 Release、同名资产先删后传）
```

## CI（.github/workflows/ci.yml）

- push/PR 时在 ubuntu runner 上用可移植版 build.sh 完整构建（`WSP_SDK=$ANDROID_HOME`），
  随后 release.sh 生成清单并校验 APK 实际 SHA-256 与 update.json 一致，最后上传构件。
- CI 中使用自动生成的临时 keystore，仅验证"能构建"，不影响正式签名。

## 签名密钥

- 当前正式签名沿用历史 debug.keystore（`$WSP_BUILD_ENV/debug.keystore`，本机备份在 `secrets/backup/`），保证已装用户覆盖升级连续。
- 专用 release keystore 已生成于 `secrets/wsp-release.keystore`，**暂未启用**；启用即意味着所有已装用户需一次性卸载重装，切换步骤与口令记录见 `secrets/release-keystore.txt`。
- 密钥文件与口令绝不入库（.gitignore 覆盖 `*.keystore`、`*.jks`、`secrets/`、`.build-env/`）。

## 应用侧更新逻辑（Updater.java）

1. `GET https://api.github.com/repos/<user>/<repo>/releases/latest`（Accept: application/vnd.github+json）
2. 从 assets 中取 `update.json` 与 `.apk` 的 `browser_download_url`；缺少任一即中止并提示
3. 读 `update.json`：`versionName / versionCode / apk / sha256`；`apk` 字段与实际资产名不一致即中止
4. `versionCode <= 当前` → 提示"已是最新版本"；`versionCode > 当前` → 下载
5. 下载时流式计算 SHA-256（上限 100MB），与清单比对：
   - 通过 → 经 `UpdateFileProvider`（content:// + 临时读权限）调起系统安装器（需"安装未知应用"权限）
   - 失败 → **删除文件、提示错误、不进入安装流程**

## 状态（2026-11-19 健康检查后）

- `Updater.UPDATE_REPO` 已为实际值 `"Lauyac/wsp-translator"`，在线更新链路已实测可用。
- 签名密钥备份与专用 release keystore 已落地（见上文「签名密钥」与 secrets/）。
- 剩余唯一合规挂起项：上游浏览器扩展许可证确认（COMPLIANCE_REVIEW.md R1 / THIRD_PARTY_NOTICES.md 第 5 节），需项目所有者确认。

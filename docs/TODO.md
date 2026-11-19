# 待办与决策记录（docs/TODO.md）

健康检查（2026-11-19）后，除本次已完成事项外，剩余项的决策与计划如下。

## 已评估、明确推迟的项

### 1. targetSdk 34 → 35/36【推迟，需真机验证】

- 本应用通过 GitHub Releases 分发，**不涉及 Google Play 的 targetSdk 政策约束**（该政策只约束 Play 商店提审），targetSdk 34 功能上完全够用。
- targetSdk ≥35 的核心影响是**强制 edge-to-edge**：窗口默认延伸到系统栏下，本应用的悬浮面板（WindowManager）与主界面（纯代码布局，无 androidx insets 库）都需要重做边距适配；另外还有部分行为收紧（如后台服务限制变化）。
- 结论：**在具备真机回归测试条件前不升**。升级前置条件：真机验证收信通知、悬浮球/面板、设置界面在 Android 15+ 上无遮挡、无错位。

### 2. networkSecurityConfig（禁明文流量）【有意不启用】

- 用户可把 API 地址配置为局域网网关 / 自建代理（http://），禁明文会直接破坏该合法使用场景。
- 当前所有官方推荐端点均为 HTTPS，风险主要来自用户自行配置的明文地址——这是用户知情选择。
- 结论：保持默认（允许明文）。若未来需要收紧，可加一个 `networkSecurityConfig` 仅对固定域名列表强制 HTTPS，保留用户自定义地址的明文能力。

### 3. 单元测试【低优先级】

- 项目无 Gradle，传统 JUnit 基建缺失；核心逻辑（翻译请求、更新校验、噪音过滤）均与 Android 框架强耦合，可纯 JVM 测试的函数有限。
- 已有的替代保障：CI 每次 push 完整编译 + 打包 + SHA-256 清单一致性校验（.github/workflows/ci.yml）。
- 结论：暂不引入测试框架；后续若抽出纯逻辑模块（如版本比较、Markdown 清理）再补 JVM 单测。

### 4. longVersionCode / versionCodeMajor【不适用】

- versionCode 目前为 18，远低于 Int 上限（21 亿）；aapt2 link 的 `--version-code` 已足够。
- 结论：不设置；等 versionCode 接近上限的极端场景再考虑（实际不可能发生）。

### 5. 上游浏览器扩展许可证确认（COMPLIANCE_REVIEW.md R1）【待项目所有者确认，唯一未关闭项】

- 本仓库代码中的"移植自扩展"逻辑需要所有者确认上游扩展的所有权/许可证状态后，在 THIRD_PARTY_NOTICES.md 第 5 节补全。
- 该项无法由代理代办（涉及权利归属确认），保持挂起状态。

## 已完成项（本次健康检查落地）

| 项 | 落地形式 |
|---|---|
| 签名密钥单点故障 | debug.keystore 已备份至 secrets/backup/；已生成专用 release keystore（secrets/，暂未启用，切换需一次性卸载重装，见 secrets/release-keystore.txt） |
| LICENSE 缺失 | LICENSE（Apache-2.0 官方原文） |
| README 缺失 | README.md |
| 隐私说明缺失 | PRIVACY.md |
| 崩溃无日志 | src/com/wsp/translate/CrashHandler.java |
| API Key 明文存储 | src/com/wsp/translate/Crypto.java（AndroidKeyStore AES-256/GCM，旧明文懒迁移） |
| 构建脚本不可移植 | build.sh 相对路径化 + 平台自适应 + 环境变量覆盖 |
| 发布步骤繁琐 | publish.sh 一键发布（幂等） |
| 无 CI | .github/workflows/ci.yml（编译 + 打包 + 校验 + 产物上传） |

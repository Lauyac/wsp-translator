# 开源合规与在线更新风险评审（wsp-translator）

评审对象：C:\Users\Administrator\Desktop\apk\（源码 src/、资源 res/、构建 build.sh）
评审方式：全量 import 扫描 + 构建/资源文件核查 + 注释溯源
日期：2026-11-19

## 一、依赖清单核实结论

- **APK 运行时第三方代码：仅 2 项**，均为 Apache-2.0，无冲突：
  1. Android Framework API（AOSP）——全部 android.* import；
  2. org.json（Android 平台 libcore 实现，经 android.jar 提供）——仅 Translator.java 使用。
- **未发现**任何捆绑的第三方 jar/AAR、Gradle/Maven 依赖（本项目无 Gradle，纯 SDK 工具链构建）。
- 其余 import 全部为 JDK 标准库（java.*）。
- 构建期工具（Build-Tools 34.0.0、OpenJDK 17、Python 3.x）不随产物分发，无署名义务，已在 THIRD_PARTY_NOTICES.md 第 4 节列明。
- **上游溯源发现**：Translator.java / Langs.java / Settings.java / FloatService.java 注释明确记载提示词、LRU 缓存、错误分类、语言表、中文残留检查"移植自浏览器扩展（core.js/providers.js/langmap.js/checks.js/cacheByContent）"。

## 二、风险清单与修正建议（按严重程度排序）

### R1 · 上游扩展许可证未确认【高风险，阻塞开源发布】
- 现状：移植代码的来源扩展无许可证信息。若上游为 copyleft（GPL/AGPL），当前工程必须以兼容许可证开源；若为 MIT/BSD/Apache，须在 THIRD_PARTY_NOTICES.md 与 NOTICE 中补充其名称、作者、版本、仓库链接与版权声明；若上游为闭源/私有（自研扩展），需确认可公开移植代码的权利。
- 修正：**确认上游扩展的许可证或所有权**，补入 THIRD_PARTY_NOTICES.md 第 5 节。未确认前不建议公开仓库。

### R2 · 项目自身无 LICENSE【高风险】
- 现状：仓库无 LICENSE 文件 → 默认"保留所有权利"，他人不能合法复用/分发/二次开发，与开源目标矛盾，也无法支撑社区贡献。
- 修正：选定并添加 LICENSE。推荐 **Apache-2.0**：与 AOSP 生态一致、含显式专利授权、有 NOTICE 惯例（本仓库已按此惯例生成 NOTICE）；MIT 为更简单的替代。许可证文本必须从官方地址原样复制，不可手写改写。

### R3 · "在线更新"机制合规【中风险】
- 现状与风险：
  1. **签名密钥不稳定风险**：当前使用 build.sh 首次运行自动生成的 debug.keystore（有效期 10000 天，存于构建机 .build-env）。密钥一旦丢失/重建，已安装用户将无法覆盖升级（签名不一致只能卸载重装，本地缓存与设置全部丢失）。
  2. **更新完整性**：应用内检查更新若直接下载 APK 安装，缺少校验环节存在被替换风险。
- 修正：
  1. 将当前 debug.keystore **立即备份至安全位置并纳入密码管理**；正式开源后改用专用 release keystore（仅保留私钥文件 + 强口令，不入仓库）。
  2. 更新检查采用 GitHub Releases API（HTTPS），比对 versionCode 单调递增；下载后校验 **SHA-256**（发布时随 Release 附带校验文件）再触发安装。
  3. 版本号遵循 CHANGELOG.md 已定规则（第三段到 10 进位），versionCode 每次 +1，保证覆盖升级语义正确。

### R4 · 商标使用【中风险】
- 现状：应用名"WhatsApp 翻译助手"、包名 com.wsp.translate。图标为自绘（气泡+对勾），未复制 WhatsApp 官方图标——这点合规。
- 风险：应用名直接含他人商标；Meta 对 WhatsApp 商标使用有明确政策，面向公众分发（尤其未来可能上架商店）存在投诉/下架风险。
- 修正：README 保留免责声明（片段已生成）；建议英文名采用 "WSP Translator" 类中性命名，中文说明性使用"用于 WhatsApp 消息翻译"属描述性使用，风险可控。

### R5 · 隐私说明缺失【中风险】
- 现状：应用将用户聊天文本发送至用户配置的 API 端点，属数据出境到第三方服务，但仓库无隐私说明。
- 修正：新增 PRIVACY.md：明确"本地读取（需用户授权无障碍/通知权限）→ 仅发送至用户自行配置的 API → 译文仅存本地内存缓存（500 条 LRU，退出即清）"；无遥测、无第三方 SDK、无数据收集。这也是 GitHub 用户与未来审核的基础材料。

### R6 · 许可证兼容性逐项核验【低风险，当前无冲突】
- Apache-2.0（AOSP/org.json）+ 项目自身 Apache-2.0/MIT：兼容，无传染问题。
- 唯一红旗预案：若未来有人引入 json.org 原始 Java 实现作为依赖，其"Good, not Evil"条款与 Apache-2.0 不兼容（Apache 基金会明确禁止），已在 THIRD_PARTY_NOTICES.md 第 2 节写入禁用说明。
- OpenJDK 工具链（GPLv2+CE）仅编译使用，Classpath Exception 覆盖标准库调用，无传染。

### R7 · CHANGELOG/版本/署名一致性【低风险】
- 已有 CHANGELOG.md 与版本规则，建议每次 Release：tag = 版本号、附 APK + SHA-256、CHANGELOG 同步；THIRD_PARTY_NOTICES.md/NOTICE 若有变更需同步提交。

## 三、需要补充确认的依赖信息（当前缺失，不得臆造）

1. 上游浏览器扩展（core.js/providers.js/langmap.js/checks.js）的：项目名称、作者/版权人、版本、仓库地址、**许可证类型**。
2. 项目所有者对 **LICENSE 类型**的选择（Apache-2.0 或 MIT，或其他）。
3. 是否计划上架 Google Play（决定商标改名与审核策略的优先级）。
4. release 签名密钥的生成与保管方案（谁持有、是否离线备份）。

## 四、结论

- 代码与产物层面**当前未发现许可证冲突或未署名的已捆绑开源组件**；依赖面极小且全部为 Apache-2.0，开源合规基础良好。
- 发布前必须完成：① 确认上游扩展许可证（R1）；② 添加项目 LICENSE（R2）；③ 固化签名密钥与更新校验流程（R3）；④ 补 PRIVACY.md（R5）。
- 已生成可直接入库的文件：`THIRD_PARTY_NOTICES.md`、`NOTICE`、`README_LICENSE_SNIPPET.md`（README 致谢/许可证/免责声明片段）。

## 五、整改状态更新（2026-11-19 健康检查后）

| 风险项 | 状态 | 落地方式 |
|---|---|---|
| R1 上游扩展许可证未确认 | **仍挂起**（涉及权利归属确认，只能由项目所有者完成） | THIRD_PARTY_NOTICES.md 第 5 节保持占位；见 docs/TODO.md 第 5 条 |
| R2 无 LICENSE | ✅ 已关闭 | LICENSE = Apache-2.0 官方原文（与 AOSP/org.json 生态一致，含专利授权）；README/LICENSE/NOTICE 已联动 |
| R3 签名密钥风险 | ✅ 大部分关闭 | debug.keystore 已备份（secrets/backup/）；专用 release keystore 已生成（secrets/wsp-release.keystore，暂未启用——切换会导致已装用户需一次性卸载重装，启用步骤见 secrets/release-keystore.txt）；SHA-256 更新校验早已上线 |
| R4 商标使用 | ✅ 已缓解 | 应用名已改为"WSP 翻译助手"（避免含商标）；README 免责声明已落地 |
| R5 隐私说明缺失 | ✅ 已关闭 | PRIVACY.md（数据流表、权限用途表、不做什么清单） |
| R6/R7 一致性 | ✅ 维持 | 发布流程硬化为 publish.sh 一键发布（幂等），CHANGELOG/版本/资产一致性由脚本强制 |

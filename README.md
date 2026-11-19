# WSP 翻译助手（WSP Translator）

一款极简、无依赖的 Android 聊天翻译助手：**收信自动翻译 · 悬浮屏上翻译 · 草稿一键翻译**，面向 WhatsApp 等聊天场景的外贸沟通使用。适配 Android 8.0（API 26）及以上。

- 纯 Android SDK 工具链构建，**零第三方运行时依赖**（仅 AOSP Framework + 平台 org.json）
- 不收集任何数据，详见 [PRIVACY.md](PRIVACY.md)
- 通过 GitHub Releases 分发，应用内支持两阶段安全更新（versionCode 单调递增 + SHA-256 强校验）

## 功能

| 功能 | 说明 |
|---|---|
| 收信自动翻译 | 开启通知使用权后，新消息到达时自动发出「译 · 」开头的本地通知，展示原文与译文；翻译失败也会通知并说明原因 |
| 悬浮翻译 | 在聊天界面点悬浮球「译」，自动读取屏幕文字并翻译（自动排除界面噪音、广告横幅、语音条提示等非聊天内容） |
| 草稿翻译 | 在悬浮面板输入中文 → 「翻译并填入」→ 译文写入聊天输入框，**需人工核对后手动发送，应用绝不自动发消息** |
| 模型选择 | 预设 DeepSeek 系列模型，兼容任意 OpenAI 风格 API 网关 / 私有代理 |

## 安装

1. 到 [Releases](https://github.com/Lauyac/wsp-translator/releases) 下载最新 `wsp-translator-<版本>.apk`
2. 安装时允许「安装未知来源应用」
3. 打开应用填写 DeepSeek API Key（或你的 OpenAI 兼容网关地址与 Key）
4. 按引导开启：通知使用权、无障碍服务、悬浮窗权限（Android 13+ 另需通知显示权限）
5. 「测试连接」通过后即可使用

## 在线更新

应用内「应用更新」卡片支持检查更新：

- 通过 GitHub Releases API 检查（HTTPS），基于 `versionCode` 单调递增判断新版本
- 两阶段流程：先展示版本号与完整更新说明，**用户确认后**才开始下载
- 下载时流式计算 SHA-256，与发布清单 `update.json` 比对，**不一致即删除文件并中止，绝不安装**

## 构建

无 Gradle，纯 SDK 工具链（aapt2 → javac → d8 → package → zipalign → apksigner）：

```bash
bash build.sh      # 需 Android SDK Build-Tools 34.0.0、platform android-34、JDK 17
bash release.sh    # 生成本地 update.json + SHA256SUMS.txt（不入库）
bash publish.sh    # 一键发布：构建 → 提交/打标签 → 创建 GitHub Release 并上传资产
```

路径、SDK、Python 均可用环境变量覆盖（`WSP_BUILD_ENV` / `WSP_SDK` / `WSP_PYTHON` / `WSP_KEYSTORE` 等），Windows（Git Bash）与 Linux（CI）均可运行。详见 `build.sh` 头部注释。

签名密钥不入仓库，保管与切换见 `secrets/`（本地）与 [docs/RELEASE.md](docs/RELEASE.md)。

## 项目结构

```
AndroidManifest.xml          权限与四大组件声明
build.sh / release.sh / publish.sh   构建 / 本地发布物 / 一键发布
res/                         图标与主题资源
src/com/wsp/translate/
  MainActivity.java          设置界面（现代蓝 UI）+ 两阶段更新流程
  FloatService.java          无障碍服务：屏幕读取、噪音过滤、悬浮面板
  NLService.java             通知监听：收信自动翻译
  Translator.java            OpenAI 风格 API 调用与错误处理
  Settings.java              本机配置存储（API Key 经 AndroidKeyStore 加密）
  Crypto.java                AES-256/GCM 加密（密钥存 AndroidKeyStore）
  Updater.java               GitHub Releases 在线更新（SHA-256 校验）
  UpdateFileProvider.java    更新包安全共享 Provider
  CrashHandler.java          崩溃日志本地捕获
docs/RELEASE.md              发布流程与硬性规则
PRIVACY.md                   隐私说明
THIRD_PARTY_NOTICES.md / NOTICE   第三方声明
```

## 已知边界

- 应用通过 GitHub Releases 分发，未上架 Google Play；targetSdk 34（原因与后续计划见 `docs/TODO.md`）。
- 聊天内容仅作为翻译请求发送至**你自己配置的 API 服务端**，请知悉所选服务商的隐私政策。

## 致谢

本项目在开发过程中受益于以下开源项目与工具：

- [Android 开源项目（AOSP）](https://source.android.com/)（Apache-2.0）——应用基于 Android Framework API 构建：无障碍服务（屏幕读取翻译）、通知监听（收信翻译）、悬浮窗（悬浮翻译面板）均由系统框架能力实现。
- Android 平台自带的 [org.json](https://developer.android.com/reference/org/json/package-summary) 实现（Apache-2.0）——用于 OpenAI 兼容接口的 JSON 编解码。
- Android SDK Build-Tools、OpenJDK、Python——仅作为构建工具链使用，详见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

完整第三方声明与许可证信息见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) 与 [NOTICE](NOTICE)。

## 许可证

本项目代码以 [Apache License 2.0](LICENSE) 发布。

第三方组件的许可证与署名要求见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

## 免责声明

- "WhatsApp" 是 Meta Platforms, Inc. 的商标。本项目为独立的第三方辅助翻译工具，与 Meta Platforms, Inc. 及 WhatsApp 无任何隶属、合作或背书关系；应用图标为自绘矢量图形，未使用 WhatsApp 官方图标素材。
- 本应用仅在用户设备本地读取用户明确授权的内容（无障碍屏幕文字 / 通知文本），翻译请求仅发送至用户自行配置的 API 服务端；聊天内容不写入任何第三方服务器。
- 本应用不自动发送任何消息；"翻译并填入"仅写入输入框，需用户人工核对后手动发送。
- 本应用通过 GitHub Releases 分发，未上架 Google Play；安装需允许"安装未知来源应用"。
- 使用本应用产生的一切后果由使用者自行承担，请遵守目标服务（如 WhatsApp）的用户协议与当地法律法规。

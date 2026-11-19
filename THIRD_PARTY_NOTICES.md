# THIRD_PARTY_NOTICES（第三方声明）

本文件列出本软件（wsp-translator，"WhatsApp 翻译助手"）所使用、分发或链接的第三方组件及其许可证信息。本仓库的构建产物（APK）在运行时**仅依赖以下组件**，未捆绑任何其他第三方库、jar 或 AAR。

---

## 1. Android 开源项目（AOSP）/ Android SDK 框架 API

- **组件**：Android Framework API（android.jar，compileSdk 34 / targetSdk 34 / minSdk 26），包括但不限于 `android.app.*`、`android.widget.*`、`android.view.*`、`android.accessibilityservice.*`、`android.service.notification.*`、`android.content.*`、`android.graphics.*`
- **用途**：应用界面、无障碍屏幕读取（悬浮翻译）、通知监听（收信翻译）、悬浮窗
- **许可证**：Apache License 2.0
- **上游**：https://source.android.com/
- **署名要求**：Apache-2.0 要求再分发时保留许可证与版权声明。本文件即作为该声明的载体；使用官方 Android SDK 构建产物时，框架 API 以系统镜像形式运行于设备端，不随 APK 复制分发。

## 2. org.json（Android 平台实现）

- **组件**：`org.json.JSONArray` / `org.json.JSONObject`（来自 Android 平台 libcore 实现，经 android.jar 提供）
- **用途**：构造与解析 OpenAI 兼容 API 的 JSON 请求/响应（Translator.java）
- **许可证**：Apache License 2.0（Android 平台实现）
- **合规说明**：Android 的 org.json 是 Google 基于 json.org 原始代码重写后的 Apache-2.0 实现。**注意**：不要改用 json.org 官网原始 Java 实现作为捆绑依赖——其"JSON License"含"shall be used for Good, not Evil"条款，非自由许可证，与开源分发及多数许可证不兼容。本仓库当前未捆绑任何 org.json jar，未来也不应引入。

## 3. Java 标准库（JDK）

- **组件**：`java.net.*`（HttpURLConnection）、`java.security.*`（SHA-256）、`java.util.*`、`java.util.concurrent.*` 等
- **用途**：网络请求、哈希去重、并发队列、集合与文本处理
- **许可证**：随构建所用 OpenJDK 发行（GPL-2.0-only with Classpath Exception）；标准库 API 调用不向应用程序传染许可证义务

## 4. 仅构建期工具（不随 APK 分发，不影响产物许可证）

| 工具 | 版本 | 许可证 | 用途 |
|---|---|---|---|
| Android SDK Build-Tools | 34.0.0（aapt2/d8/zipalign/apksigner） | Apache-2.0 | 资源编译、dex、对齐、签名 |
| OpenJDK | 17（javac，-source/-target 8） | GPL-2.0-only with Classpath Exception | Java 编译 |
| Python | 3.x（PSF License） | 仅用于打包 classes.dex 进 APK 的脚本步骤 | 构建脚本 build.sh |
| Git Bash / MSYS2 | — | 构建脚本执行环境 | 构建 shell |

以上工具仅处理源码生成产物，无任何工具代码被复制进 APK，按其各自许可证均不要求在应用内署名；为完整起见在此列明。

## 5. 待确认项（见 COMPLIANCE_REVIEW.md）

- **上游浏览器扩展移植代码**：源码注释表明提示词构造、LRU 缓存、错误分类、语言表、中文残留检查等逻辑"移植自扩展 core.js / providers.js / langmap.js / checks.js"。该上游扩展的开源许可证**尚未确认**。若其为署名类许可证（如 MIT/BSD/Apache），需在上文补充其名称、版本、仓库地址与版权声明；若为 copyleft（GPL 系列），需评估整体许可证兼容性。**在确认前，请勿删除本条目。**

## 6. 商标声明（非许可证，但属分发合规）

"WhatsApp"是 Meta Platforms, Inc. 的商标。本项目是独立的第三方辅助工具，与 Meta/WhatsApp 无任何隶属或合作关系，未使用 WhatsApp 官方图标素材。项目图标为自绘矢量图形。README 中须保留相应免责声明。

# wsp-translator 更新记录

版本递增规则：第三段 0-9 依次递增（1.0.1 → 1.0.2 → …），第三段达到 10 时向第二段进 1 并归零（1.0.10 → 1.1.0）。每次更新同步修改 `build.sh` 中的 `VERSION` / `VERSION_CODE`，`version-code` 从 1 起每次 +1。

## 1.0.8 (2026-11-19)

### 在线更新链路实测

- 版本号 1.0.7 → 1.0.8（versionCode 8 → 9），用于验证应用内「检查更新」全链路：GitHub Releases API → versionCode 判断 → 下载 → SHA-256 校验 → 安装。
- 功能无变化，与 1.0.7 代码一致（仅版本配置变更）。

## 1.0.7 (2026-11-19)

### 在线更新与开源合规改造

1. **发布产物不入仓库**：新增 `.gitignore`（`*.apk`、`*.idsig`、`update.json`、`SHA256SUMS.txt`、`out/`、`*.keystore` 等），构建与发布产物只存在于 GitHub Release 资产。
2. **更新检查改为 GitHub Releases API**（新增 `Updater.java`）：获取最新 Release → 读取资产中的 `update.json`（versionName/versionCode/apk/sha256）→ 与本地比较。
3. **版本判断仅基于 versionCode**，发布流程保证其单调递增（`docs/RELEASE.md` 硬性规则）。
4. **SHA-256 强制校验**：下载时流式计算哈希并与清单比对；失败即中止更新、删除下载文件并提示错误，绝不进入安装流程；清单缺失哈希、清单与资产名不一致、下载超限（100MB）同样中止。
5. **安全安装**：新增自研只读内容提供器 `UpdateFileProvider`（content:// + 临时读权限，零第三方依赖，未引入 androidx）；`REQUEST_INSTALL_PACKAGES` 权限仅用于安装校验通过的更新包。
6. **应用更名避免品牌混淆**：应用名 "WhatsApp 翻译助手" → "WSP 翻译助手"；无障碍服务描述与界面文案改为中性表述（"聊天应用（如 WhatsApp）"），包名不变。
7. **发布流程工具化**：新增 `release.sh`（生成 update.json + SHA256SUMS.txt，只产本地文件不上传）与 `docs/RELEASE.md`（发布步骤、硬性规则、上线前待办）。

### 修改范围

- 新增：`src/com/wsp/translate/Updater.java`、`src/com/wsp/translate/UpdateFileProvider.java`、`.gitignore`、`release.sh`、`docs/RELEASE.md`。
- 修改：`AndroidManifest.xml`（REQUEST_INSTALL_PACKAGES 权限、provider 注册）、`res/values/strings.xml`（更名）、`MainActivity.java`（④ 应用更新区 + checkUpdate/installUpdate）、`FloatService.java`（文案中性化）、`build.sh`（1.0.7 / versionCode 8）。

## 1.0.6 (2026-11-17)

### 修复：WhatsApp 系统提示条出现在翻译区

截图问题：「此聊天源自 Meta 广告。顾客相关活动数据分享功能已开启。 管理」是 WhatsApp 插入的广告来源声明提示条（系统提示，非聊天消息），因带文本节点而被当作正文送进翻译。

1. **新增 `isSystemNotice()`**：识别广告来源类系统提示——以「此聊天源自 / 此聊天来自 / This chat started from / This chat came from」开头，或包含「顾客相关活动数据分享功能 / customer activity data」，或提到 Meta 广告且带「管理 / 已开启 / 分享功能」的兜底规则，命中即从原文中排除（也不进入译文）。
2. **动作链接过滤**：「管理 / 了解更多 / Manage / Learn more」加入界面文案表，避免提示条末尾的独立链接节点单独成行。
3. **仅作用于翻译区采集**：不改变面板布局、按钮行为与其他翻译内容；被过滤的提示条不计入"跳过非文本"计数。

### 修改范围

- `src/com/wsp/translate/FloatService.java`：isSystemNotice、UI_CHROME 增补。
- `build.sh`：VERSION=1.0.6 / VERSION_CODE=7。

## 1.0.5 (2026-11-17)

### 修复：语音/图片等非文本内容混入原文与译文

截图问题：原文卡出现「语音消息，按钮。双击并按住可录制，向左滑动可取消录制…松开以发送消息。」与「表情符号、动图和贴图」——这是配音按钮 / 表情按钮的无障碍 contentDescription，被当作聊天内容送入翻译，导致译文杂乱。

1. **跳过按钮无障碍说明**：新增 `isA11yHint()`，含"按钮"且带"双击/滑动/长按/松开/点按"的说明文字直接丢弃，不再进入原文。
2. **跳过媒体条目**：新增 `MEDIA_PREFIX` 表（语音消息/图片/照片/视频/贴纸/表情符号/动图/文件/文档/位置等中英文），contentDescription 以这些词开头的条目判定为媒体内容，跳过翻译；文本节点形式（如"语音消息 0:07"）用 `isMediaOnly()` 精确判定，避免误删真实消息（"语音消息我收到了"仍保留）。
3. **跳过输入框草稿**：可编辑节点的内容改由「草稿翻译」通道处理，不再并入屏幕原文重复翻译。
4. **非文本内容不参与合并展示**：上述内容在采集阶段即被排除，原文卡只保留真正的文字消息，界面更整洁。
5. **状态提示更清晰**：全部为非文本内容时提示"当前屏幕只有语音/图片/表情等非文本内容，无需翻译"；正常翻译时状态行追加"· 跳过 N 项非文本"，让用户知道跳过了什么。
6. `screenText()` 重构为 `scanScreen()`，返回文本与被跳过条目数（ScreenScan）。

### 修改范围

- `src/com/wsp/translate/FloatService.java`：scanScreen/ScreenScan、isMediaEntry/isMediaOnly/isA11yHint、状态提示。
- `build.sh`：VERSION=1.0.5 / VERSION_CODE=6。

## 1.0.4 (2026-11-17)

### 译文展示区排版优化（功能不变）

1. **统一字体体系**：原文卡、译文卡、标签、状态行、语言行、标题全部统一为 sans-serif 字体族，字号分层固定（标题 16 / 译文 14 / 原文 12 / 标签 11 / 状态 12），不再依赖系统默认值混排。
2. **层级与视觉分区**：原文卡、译文卡增加浅色底（8% 白）+ 内边距，与面板背景形成层级，减少视觉干扰。
3. **行距与对齐**：统一 `lineSpacing(+2dp, ×1.1)` 行距、START 左对齐、包含字体上下留白，两卡观感一致。
4. **段落与换行规则**：新增 `formatForDisplay()`——空行识别为段落分隔（渲染为空一行），行内制表符/连续空格压缩为单个空格，去除首尾空行；长文本、多段译文层级清晰、间距一致。
5. **特殊符号处理**：译文在展示、复制、填入前统一经 `cleanText()` 清除不可见字符（零宽字符、方向符、控制符等），避免特殊符号破坏排版与 WhatsApp 输入。
6. **译文卡加高** 140dp → 150dp，多段译文一屏可见更多。

### 修改范围

- `src/com/wsp/translate/FloatService.java`：makeTextCard 统一文本卡、formatForDisplay 排版规则、两条翻译链路展示/复制/填入统一走"清洗 → 排版"。
- `build.sh`：VERSION=1.0.4 / VERSION_CODE=5。

## 1.0.3 (2026-11-17)

### 识别结果清洗、分段与排序（核心改进）

1. **按屏幕位置排序**：`screenText()` 由视图树 BFS 顺序改为按节点屏幕坐标排序（自上而下、自左而右），识别顺序与用户视觉看到的聊天顺序一致，消除乱序。
2. **文本清洗**：剔除控制符、零宽字符（U+200B-200F）、双向方向符（U+202A-202C）、BOM、变体选择符等不可见字符，减少乱码来源。
3. **界面噪声过滤**：精确匹配丢弃 WhatsApp 界面固定文案（"输入消息""更多选项""已读/已送达""正在输入"等中英文 60 余项）；丢弃纯时间戳、1-3 位未读徽标数字、纯表情/符号短串，只保留真实聊天内容。
4. **去重压缩**：全局精确去重 + 排序后相邻行去重；仍保留 3500 字符上限（约 80 条文本节点）。

### 译文展示与界面交互

5. **原文/译文分区展示**：面板改为「原文（屏幕识别）」+「译文」双卡片，各自独立滚动（72dp/140dp），行距加大，原文可回看、译文可选中复制。
6. **处理状态行**：新增醒目状态行——灰色=进行中、绿色=完成（注明缓存/网络与目标语言）、红色=失败、黄色=警告（中文残留/未找到输入框）。
7. **按钮忙碌反馈**：翻译期间四个操作按钮统一禁用，防止重复提交。
8. **移动端适配**：面板整体内容包在可滚动容器中并限高屏幕 78%，小屏/横屏不再溢出；草稿框行高与字号调整。
9. **复制逻辑修正**：新增 `copyText()` 直复制通道，中文残留回退场景不再被结果状态拦截，始终能复制译文供人工核对。

### 修改范围

- `src/com/wsp/translate/FloatService.java`：screenText 重写（清洗/排序/过滤）、面板布局重构（双卡片+状态行+限高滚动）、setBusy/setStatus、copyText。
- `build.sh`：VERSION=1.0.3 / VERSION_CODE=4。

## 1.0.2 (2026-11-17)

### 修复

1. **修复应用无法打开的启动闪退**（MainActivity，关键缺陷）：1.0.1 加入"显示 API Key"开关时，`etKey` 被 `root.addView()` 添加了两次，LinearLayout 对已有父容器的子 View 再次 addView 会抛出 `IllegalStateException: The specified child already has a parent`，导致 MainActivity 在 onCreate 中必然崩溃——表现为安装完成后点击图标闪退/无法打开（所有机型，包括小米 17 Pro / 澎湃OS 4.0.6.0）。修复：删除重复的 `root.addView(etKey)`，仅保留第 ① 区顶部一处。

### 修改范围

- `src/com/wsp/translate/MainActivity.java`：删除重复 addView（最小范围修复）。
- `build.sh`：VERSION=1.0.2 / VERSION_CODE=3。

## 1.0.1 (2026-11-17)

易用性优化版本，不改变核心翻译功能与协议行为（仍为 DeepSeek/OpenAI 兼容接口，仍不自动发送消息）。

### 优化点

1. **错误提示可操作化**（Translator）：新增错误分类，把底层报错转为可行动的中文提示——API Key 无效（401/403）、余额不足（402）、频率受限（429）、地址或模型不存在（404）、服务端故障（5xx）、请求超时、网络连接失败等。
2. **修复"复制译文"复制错误文本的缺陷**（FloatService）：结果状态改用标志位（lastResultOk/busy）管理，替代原来按"翻译失败/正在…"字符串前缀判断的脆弱逻辑；翻译失败后复制按钮不再把错误信息写入剪贴板。
3. **重复提交保护**（FloatService）：翻译进行中再次点击读取/草稿翻译会提示"正在翻译中，请稍候"，避免重复请求。
4. **翻译过程反馈更明确**（FloatService）：翻译中文案显示目标语言（如"正在翻译屏幕内容（→ 简体中文）…"）；面板顶部新增语言方向行（收信 → xx / 回复 → xx），打开面板即刷新。
5. **悬浮球贴边吸附**（FloatService）：拖动结束后自动吸附到最近的屏幕左右边缘并限制纵向范围，不再遮挡聊天内容。
6. **面板宽度自适应**（FloatService）：由固定 320dp 改为 min(320dp, 屏宽 90%)，避免小屏溢出。
7. **失败提示附带下一步指引**（FloatService）：读取不到屏幕时区分"无障碍服务未连接"与"不在 WhatsApp 聊天界面"；填入输入框失败时明确提示"请确认当前处于 WhatsApp 聊天界面"，并回退使用最近一次捕获的 WhatsApp 窗口查找输入框（节点操作加异常保护）。
8. **API Key 可见性切换**（MainActivity）：新增"显示 API Key"开关，便于检查输入是否正确。
9. **收信翻译失败可见**（NLService）：翻译失败时发出含原因的失败通知（受原有 30 秒去重保护），不再静默失败。
10. **保存与版本反馈**（MainActivity）：保存时 API Key 为空给出明确提醒；使用说明区显示当前版本号（动态读取 versionName）。

### 修改范围

- `src/com/wsp/translate/Translator.java`：错误分类 friendlyError、空结果提示。
- `src/com/wsp/translate/FloatService.java`：状态标志、忙碌保护、贴边吸附、面板宽度自适应、语言方向行、错误提示与指引、fillEditable 兜底与保护。
- `src/com/wsp/translate/MainActivity.java`：Key 显示切换、版本号显示、空 Key 保存提醒。
- `src/com/wsp/translate/NLService.java`：失败通知。
- `build.sh`：VERSION / VERSION_CODE 参数化，本次 1.0.1 / version-code 2。
- `CHANGELOG.md`：新建本文件。

## 1.0.0

初始版本：悬浮球屏幕翻译、草稿翻译并填入、收信通知翻译、DeepSeek（OpenAI 兼容）API、内存 LRU 缓存、中文残留检查。

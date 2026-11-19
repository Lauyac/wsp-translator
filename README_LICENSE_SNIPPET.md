# README 致谢 / 许可 / 免责声明片段

> 用法：将以下各段复制粘贴到仓库 README.md 的对应位置。

---

## 致谢（粘贴到 README"致谢 / Acknowledgements"段）

```markdown
## 致谢

本项目在开发过程中受益于以下开源项目与工具：

- [Android 开源项目（AOSP）](https://source.android.com/)（Apache-2.0）——
  应用基于 Android Framework API 构建：无障碍服务（屏幕读取翻译）、
  通知监听（收信翻译）、悬浮窗（悬浮翻译面板）均由系统框架能力实现。
- Android 平台自带的 [org.json](https://developer.android.com/reference/org/json/package-summary)
  实现（Apache-2.0）——用于 OpenAI 兼容接口的 JSON 编解码。
- Android SDK Build-Tools、OpenJDK、Python——仅作为构建工具链使用，
  详见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

完整第三方声明与许可证信息见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)
与 [NOTICE](NOTICE)。
```

## 许可证段（粘贴到 README 尾部；确定项目许可证后启用对应行）

```markdown
## 许可证

本项目代码以 [Apache License 2.0](LICENSE) 发布（仓库根目录须放置 LICENSE 文件，
文本请从 https://www.apache.org/licenses/LICENSE-2.0.txt 原样复制，勿修改）。
<!-- 若改用 MIT：替换为 MIT 许可证说明，并将官方文本存入 LICENSE -->

第三方组件的许可证与署名要求见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
```

## 商标与免责声明段（粘贴到 README 尾部，强烈建议保留）

```markdown
## 免责声明

- "WhatsApp" 是 Meta Platforms, Inc. 的商标。本项目为独立的第三方辅助翻译工具，
  与 Meta Platforms, Inc. 及 WhatsApp 无任何隶属、合作或背书关系。
- 本应用仅在用户设备本地读取用户明确授权的内容（无障碍屏幕文字 / 通知文本），
  翻译请求仅发送至用户自行配置的 API 服务端；聊天内容不写入任何第三方服务器。
- 本应用不自动发送任何消息；"翻译并填入"仅写入输入框，需用户人工核对后手动发送。
- 本应用通过 GitHub Releases 分发，未上架 Google Play；安装需允许"安装未知来源应用"。
```

---

## 仓库需要补充的许可证文件清单

| 文件 | 内容来源 | 说明 |
|---|---|---|
| `LICENSE` | 官方文本原样复制：Apache-2.0 取 https://www.apache.org/licenses/LICENSE-2.0.txt ；MIT 取 https://opensource.org/license/mit | **不要手写或改写许可证文本**；许可证类型需项目所有者确认后选定（推荐 Apache-2.0，理由见 COMPLIANCE_REVIEW.md） |
| `NOTICE` | 已生成（本目录） | Apache-2.0 生态惯例；选 MIT 时可保留为致谢文件 |
| `THIRD_PARTY_NOTICES.md` | 已生成（本目录） | 第三方组件完整声明；第 5 节上游扩展许可证待确认 |
| `PRIVACY.md` | 建议新增 | 数据流说明：本地读取 → 用户配置的 API 端点 → 本地缓存；GitHub 开源 + 自动更新场景的隐私说明 |

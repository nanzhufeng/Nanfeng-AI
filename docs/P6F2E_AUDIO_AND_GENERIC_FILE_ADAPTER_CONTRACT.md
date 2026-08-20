# P6-F2-E Audio & Generic File Adapter 合同

状态：完成（2026-08-14）。本合同是 P6-F2-E 的唯一正文；它是一个 Adapter，包含两个不可互相替代的子路径：Audio 与 Generic File。最终事实见 `P6F2E_AUDIO_AND_GENERIC_FILE_ADAPTER_EVIDENCE.md`；此完成不授权 P6-G、Provider、Key/HTTP、OPPO、图标或发布。

## 范围与唯一所有者

- 两端复用既有 Conversation/Message/Attachment private-copy owner。UI 只传 `workspaceId + attachmentId`（Android 仅 `AttachmentId`）给 typed owner；不接收或显示 source path、URI、Key、隐藏 metadata 或媒体字节的持久化副本。
- Audio 仅允许受控、magic-verified 的 MP3、WAV 与 M4A。卡片显示安全文件名、类型、大小、真实时长与“点击播放 · 仅本地”；仅显式点击才开始，关闭/Back/Escape/外点统一保存实际非零未结束位置，重启后按同一 owner 恢复。没有第二层媒体中心、自动播放、扫描、上传或 Prompt 输入。
- Generic File 只接受既有受控 PDF 与文本类型（TXT、Markdown、JSON、CSV）。PDF 保持 P6-F2-C 阅读器；文本只使用有上限的 UTF-8 受控解码及 inert plain-text 视图，不解析 Markdown/JSON/CSV 为指令、不执行 HTML、脚本、工具调用或远端资源。未知、二进制、损坏、超限或校验不一致的文件不伪造封面/摘要/系统打开成功，而是原位保留安全失败说明。
- 因当前 private-copy owner 没有可安全证明的跨端系统打开发布路径，本 Adapter 不展示“系统打开”作为虚假后备；不支持的 generic binary 在 picker/import 时诚实拒绝。未来若新增该动作，必须为 Desktop/Android 分别建立仅 attachment-ID、真实外部结果和失败恢复的独立合同。

## 共同安全与状态

- import 继续受 20 MB 单项、4 项、40 MB 总量、SHA-256、普通文件/非符号链接与真实 magic 限制；只保存安全名、MIME、大小、摘要和 opaque owner reference。
- card metadata 惰性读取；仅用户打开的 audio/text 项读取已校验的 private copy。单项失败隔离，不阻塞 transcript。TEMP、已清理项、路径/URI/Key、原始正文、播放字节与任何 Provider payload 不进入搜索索引、历史、导出或同步。
- 必须覆盖取消、重复、损坏/缺失、超限、跨 workspace/会话拒绝、关闭前保存失败、重启读回与中文可执行提示。P6-F2-E 不读 Key、不发 HTTP、不外发附件、不操作 OPPO、不改图标，也不进入 P6-G。

## 退出门

Desktop 与 Android 都须有 domain/owner/UI 自动契约、lint/build、真实 picker → private-copy → 草稿 → 消息卡 → 显式操作 → 完整退出或 force-stop restart/readback。Android 还须正式签名 Debug/Release、`emulator-5554 install -r --user 0` 和回拉 `base.apk` hash；Desktop 须最新 ad-hoc strict-signed `.app`。模拟器不替代 OPPO，所有证据写入独立 P6F2E evidence 后才能将 P6-F2-E 标为完成。

## 完成记录

2026-08-14 的全部退出门已满足；证据、最终包 hash 和 emulator 限制见 `P6F2E_AUDIO_AND_GENERIC_FILE_ADAPTER_EVIDENCE.md`。

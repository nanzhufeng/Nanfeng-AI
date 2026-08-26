# P6-D2 双端 Composer Attachment Adapter 合同

状态：完成；P6-E 临时聊天的前置已满足，不改变 Conversation role、Provider 或图标边界。

> **当前 Android UI 路由（2026-08-24）：** 本文保留附件 private-copy、类型校验、容量与角色领域语义。Android 会话中的 Assistant/USER 表面、附件预览几何、Composer、来源、字体和图标视觉规则统一以 [Android 当前会话界面合同](ANDROID_CONVERSATION_UI_CURRENT_CONTRACT.md) 为准；本文早期视觉措辞不形成并列 UI 合同。

## 唯一所有者与路径

- Android：`DocumentsUI / Photo Picker -> Android*SelectionReader -> AddConversationImageAttachmentUseCase -> AndroidPrivateAttachmentStore -> RoomPrivateAttachmentRepository -> ConversationDraftRepository`。
- Desktop：`macOS dialog -> import_desktop_conversation_attachment -> DesktopWorkspaceStore -> mutate_desktop_domain`。Rust 复制到 app-private content-addressed `assets/<sha256>`，SQLite 只保存安全 metadata；前端草稿只保存 `id/mime/displayName/byteCount/sha256`，不保存系统 path/URI。
- Draft 和 Message 只拥有安全 Attachment reference。二进制、私有位置、受控读取与 GC 属于平台 attachment owner；UI、Message role、Prompt、RunSpec、Key、HTTP 不得成为第二 owner。Desktop `DesktopWorkspaceStore` 的启动 maintenance 是唯一清理入口：SQLite v6 `desktop_attachment_assets` 保存 `created_at_ms`、`last_referenced_at_ms`、`last_unreferenced_at_ms` 与精确 `reference_count`，`desktop_attachment_staging` 保存私有 staging 的创建时间。正常运行以系统 Clock 调用同一 owner；Rust 合同测试以显式 epoch 调用同一 owner，绝不以文件 mtime 或 shell 删除充当业务 GC。

## 允许输入与失败关闭

- 只接受 JPG/PNG/WebP、PDF、TXT/Markdown/JSON/CSV。声明 MIME、扩展名和文件魔数/非 NUL 文本前缀必须一致；普通未知二进制、symlink、URI/path、空文件和不匹配输入均拒绝。
- 单项最多 20 MB；每条草稿/消息最多 4 项、总计最多 40 MB。按 SHA-256 去重；重复选择不新增引用。
- 每个已接受输入必须先私有复制并回读 metadata，再进入草稿；取消或失败保持既有文字/附件。重启可恢复安全草稿 metadata；Desktop 仅在无有效消息引用且私有副本超过 24h 时回收 orphan。最后一个有效消息引用消失时才写 `last_unreferenced_at_ms`；23h59m 保留、达到 24h 才删除 asset 与其安全 metadata。中断 attachment staging 同样只在其 owner metadata 满 24h 后清理。
- 当前阶段没有附件外发、OCR、正文提取、预览执行或 Provider capability 推断。图片缩略图必须受控有界；非图片只显示安全 chip。

## 角色与视觉合同

- USER message bubble 和 attachment chip 只消费共享 `accent-orange-soft`，正文为深中性色；禁止按页面散落 hex 或改写 `MessageRole` 领域。
- ASSISTANT 为中性 surface；SYSTEM 为中性 system surface；TOOL 为 tool semantic surface；ERROR 保留错误 surface。quote/code 的内层始终中性，不能继承用户橙色。
- Desktop 与 Android 的 role mapping/contrast 合同必须验证每种消息正文对比度至少 4.5:1，并同时回归 Composer orange、发送/新建按钮、12px/12sp 会话行、PushPin/Archive 与 attachment chip。

## 验收门

1. Android JVM/Room/UI contract、Desktop Node/Rust contract、两端构建。
2. macOS `.app` 的原生图片和文件 picker：private copy、重复、取消、发送、完整退出/重开 readback 与 24h GC 的安全证据。已完成：Rust 34 tests 覆盖可注入 Clock、引用/23h59m/24h/staging/幂等/reopen；最新 ad-hoc `.app` 在隔离 HOME 的 app-private fixture 启动 maintenance 后保留 referenced/fresh orphan，删除 expired orphan/expired staging，第二次启动目录计数、SQLite 行数和 asset aggregate SHA-256 不变；metadata/receipt 只含安全 hash/计数/时间，不含 source path/URI。
3. `emulator-5554` 的 Photo Picker 与 DocumentsUI：同一链路、force-stop/readback、同签名安装/hash；不清数据、不装 test APK、不操作 OPPO。
4. 每端的真实可见视觉状态必须独立记录；自动回归、构建或旧 P6-D 截图均不能替代。

## 平台例外与完成证据

- Android：本合同只要求其 private-copy、dedup、发送、force-stop/readback 和可见状态；**Android 24h attachment/staging GC 为 N/A**，没有实现、测试或宣称该能力。
- Desktop：2026-08-13 已在全新 `/tmp/nanfeng-ai-p6d2-app-fixture-20260813` HOME 下完成非敏感、app-private 合成 fixture。启动前为 3 assets/1 staging；最新 `.app` 启动后为 2/0，SQLite 显示 referenced `reference_count=1`、fresh orphan `0`，过期 orphan/staging 均不存在；重启仍为 2/0，asset aggregate SHA-256 `a806aa53a0e4eab2b74fec881bf581cdf95762028c6436316a42f907fb4ea412`。该 fixture 不接触任何长期 workspace、真实用户文件、Key 或网络。

## 明确排除

不读/写 Key，不构造 Authorization、Prompt、RunSpec，不发任何 HTTP，不改图标/role domain，不操作 OPPO、同步、临时聊天或 Provider。

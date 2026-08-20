# P6-J 南枫知识库全部 JSON Adapter 当前证据

日期：2026-08-15  
状态：**CLOSED（非 OPPO 退出门，2026-08-15）**。本文件只记录已观察事实，不将自动测试或打开 picker 写成完整 UI 闭环。

## 已完成的代码与自动验证

- Android 与 Desktop 均只接受严格 `IntelligenceRecord[]`；每条记录必须在 `sourceText` 中提供静态 `chat_messages`。普通知识记录逐项保留为 `NOT_CONVERSATION_RECORD`，不伪造会话。
- 两端明确丢弃 `sender=tool` 的整行消息，并且只保留 `content.type=text` 的可见文本；`thinking` 和 `content.type=tool` 的文本不进入会话。合成 fixture 同时覆盖这两类过滤。
- Android：Schema 28→29 的 P6-J 私有表、私有原始 JSON 副本、任务恢复、单项确认同事务写入 Conversation/provenance/receipt 已接入。P6-J parser 与 Room 定向 JVM 测试通过。
- Desktop：SQLite 14→15 的独立任务表、private copy、read-latest、受限 retry、确认幂等、Skip、原子回滚与严格交换包 readback 已由 `p6j_selected_file_confirm_skip_reopen_export_and_rollback_are_atomic_and_idempotent` 覆盖并通过。该测试以合成数据验证错误 workspace 无 receipt 残留、确认重放返回同一 conversation、Skip 后任务完成、重开读回及 package/semantic hash 一致。

## Android emulator-5554 真实闭环

- 仅向 `emulator-5554` 推送合成 `app/src/test/resources/p6j-nanfeng-knowledge-export-acceptance.json`（SHA-256 `4b2a449efe1987ed9db0626192a2ee6ec6d7b0cadb04d92c11e0980b44e36750`），并通过 Android DocumentsUI 实际选取。UI 显示 `AWAITING_CONFIRMATION` 与两个有效静态候选 `P6J Acceptance Alpha`、`P6J Acceptance Skip`。
- 实际点击 Alpha 的“确认导入”，页面变为 `PARTIALLY_COMPLETED / Alpha CONFIRMED / Skip PENDING_CONFIRMATION`；滚动后实际点击第二条“跳过”，页面变为 `COMPLETED / Alpha CONFIRMED / Skip SKIPPED`。
- 之后 `am force-stop com.nanzhufeng.ai` 并显式启动 `NanfengAiActivity`；任务页仍读回同一 `COMPLETED`、`CONFIRMED`、`SKIPPED`。从 drawer 打开 Alpha 后，UIAutomator 实际读回 `P6J acceptance alpha user` 与 `P6J acceptance alpha assistant`。
- 正式导出路径复用 P6-I：选中 Alpha 后走 **设置 → 对话 → 管理对话 → 导出当前对话**，未新增入口。首次为查找入口误切到“工作”而产生的空会话包被保留为失败诊断，未计入验收；重新选中 Alpha 后才生成成功包 `conversation-1786793006424-784ac5e3-faa6-42a6-8692-32b3902b2172.nfai`。
- 成功包由 `adb exec-out run-as` 严格读回到外部宿主 `/tmp/p6j-android-alpha-current-path.nfai`，文件 SHA-256 为 `126b9b350bec5fecf1c7a6d7e5991638422352ed5b058958455b730b765e5923`。`unzip -t` 通过；manifest 的 `conversation.json` SHA-256 `4f5ce938e8b7a19325bc53e58e9137af97a4267e7e7a0d982fe650387cc03f3e` 与严格解包回读一致。payload 只含标题 `P6J Acceptance Alpha`、USER `P6J acceptance alpha user` 与 ASSISTANT `P6J acceptance alpha assistant`；`P6J Acceptance Skip`、skip 文本、tool directive 和 thinking 均不存在。

## Desktop 原生窗口真实闭环

- 正式开发 bundle 已构建：`desktop/src-tauri/target/release/bundle/macos/南枫 AI Desktop.app`。为避开已有 P6-I 同 bundle-id 窗口，复制出临时 bundle `/tmp/nanfeng-ai-p6j-bundle-20260815/南枫 AI P6-J 验收.app`，仅将副本 identifier 改为 `com.nanzhufeng.ai.desktop.p6jacceptance`，ad-hoc 重签后 `codesign --verify --strict --deep` 通过。它以 `NANFENG_AI_P6J_ACCEPTANCE=1 --p6j-acceptance` 运行，只使用 `/tmp/nanfeng-ai-p6j-acceptance-20260815`；旧 P6-I 进程和用户 app-data 均未改。
- 原生 Open panel 实际选择同一合成 JSON；首次选择后暴露 Tauri capability 漏列 `stage_nanfeng_knowledge_export_selected` 的 ACL 拒绝。补上仅 P6-J 的 task 命令权限、`cargo check`/Desktop lint/正式 bundle 后，原生 panel 重试成功。
- UI 显示两个 `PENDING_CONFIRMATION` 候选；实际确认 Alpha、跳过第二条，终态页面显示 `CONFIRMED` 与 `SKIPPED`。关闭仅该临时验收进程并重开后，普通历史可见 Alpha 的两条可见文本，列表无 Skip。
- Settings 原生 Save panel 导出 `/tmp/p6j-fixtures/p6j-desktop-acceptance.nfai-exchange`，SHA-256 `281be57728dd34016170b511a0b30986a34586f67543fe9e874cc5b3b7338b81`。`unzip -t` 通过；payload 的 P6-J imported conversation 仅为 `P6J Acceptance Alpha` 与两条 alpha 文本，不含 Skip、thinking 或 tool directive。

## 明确未做

未读取真实知识库数据、Key 或 Provider；未发 HTTP、未执行 Agent/tool、未操作 OPPO、未发布。合同列出的 parser/隔离、双端系统 picker、Alpha 确认、第二有效会话 Skip、完整重开、Settings 导出、SHA 与严格 payload readback 门均已满足，故正确关闭 P6-J；这不构成总计划、Provider、同步、OPPO 或发布完成声明。

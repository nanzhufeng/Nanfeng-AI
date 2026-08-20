# 南枫 AI P4-L 版本化 JSON Knowledge Adapter 合同

日期：2026-08-13  
状态：P4 的第十二个本地增量，已完成本地验收；Schema 14→15。仅支持 `nfai.knowledge.json` / `schemaVersion: 1`；不实现 JSONL、网页、PDF、Office、通用附件、语音、视频或 Provider payload 导入。

## 唯一链与版本边界

```text
系统 OpenDocument JSON
→ 有界 UTF-8 读取 → JSON 私有副本
→ JsonKnowledgeAdapter（严格 JSON Schema）
→ json_knowledge_import_tasks/items
→ 用户逐项编辑、确认、跳过、取消、失败重试
→ KnowledgeDomain → ManageKnowledgeUseCase → Room Knowledge
```

- JSON Adapter、私有资产目录、任务/条目表与 Markdown Adapter 完全分开；不把 Markdown parser 扩展成不透明通用 parser。
- 顶层必须且仅可有 `format`、`schemaVersion`、`exportedAt`、`items`。格式为 `nfai.knowledge.json`，版本为 `1`；未知格式/版本、未知字段、缺失字段、类型不符、重复 key 都整体拒绝，绝不猜测兼容。
- 每项必须且仅可有稳定来源 `id`、`title`、`body`、`tags`、`scope`、`projectRef`、`revision`、`hash`、安全 `source.kind/summary`。导入永远新建本地 Knowledge ID，不保留/覆盖原 ID；来源 ID 只作为安全的 intent 指纹成分。

## 不可信数据与正式写入

- 严格 UTF-8；单文件最大 768 KiB、最多 100 项、最大 JSON 深度 24、字符串最大 12,000 code points。数字语法仅允许该合同使用的整数；NaN、Infinity、浮点、重复 key、空 `items`、深度/大小超限均拒绝。
- 外部 URI、绝对路径/路径穿越、Authorization、Key、Provider payload、附件字节与未知字段没有 Schema 位置，安全 summary 命中这些形式也拒绝。JSON 字符串只作为惰性正文，绝不执行或解释为指令。
- 复制后 Room 仅保存稳定任务/条目 ID、受控 storage key、安全显示名、MIME、大小、SHA-256、状态与安全失败码；不保存 `content://`、外部权限 token 或绝对路径。重建从 Room + 私有副本恢复，失败可重试。
- 确认默认关闭，且正式写入只能走 P4-E `KnowledgeDomain → ManageKnowledgeUseCase` 的高敏、标签、scope、duplicate/revision 规则。P4-F 仍只读提示，P4-G 不自动建关系。
- PROJECT 请求若没有用户明确选择的目标 Project 映射，确认时确定性降为 GLOBAL；不读取、写入或猜测其他 Project。当前最小 UI 因而显示该降级，而不是暗中跨项目。

## 导出与保真

- 只从用户明确选择的 `ACTIVE` 正式 Knowledge 导出，规范顺序为 Knowledge ID 升序、UTF-8 canonical JSON。写入临时 `.part`、`fd.sync()`、原子移动、最终文件回读与 SHA-256/Manifest 校验全部成功才报告成功。
- 导出保真范围：title、body、tags、scope 与安全 source metadata；导出 ID/revision/hash 是来源版本事实，导入时 ID 必须重映射。deleted/archived、URI、路径、Key、Prompt、Provider payload、附件、关系、Memory、Context 与临时选择全部排除。
- 同文件回读/Manifest hash 失败或篡改不报告成功；没有外发、Key、Prompt、RunSpec、HTTP、费用或图片外发，`OpenRouterEgressPolicy.Disabled` 保持。

## Schema 与证据

- 14→15 只追加 `json_knowledge_import_tasks`、`json_knowledge_import_items` 和索引；不 wipe、destructive migration 或改写 P1–P4-K/Markdown 队列。P4-I 仅增加版本、严格边界、红队、roundtrip、tamper、ID remap、私有复制重建与 `NO_EGRESS` 机械事实，不声称模型质量或成本。
- 最低验收：严格 parser、重复 key/深度/大小/类型/未知版本、高敏/URI 路径排除、任务重建、确认/跳过/取消/重试、scope 降级、ID 冲突、原子导出/回读/hash/tamper/roundtrip、14→15 迁移及 P4-E–K 回归；再分别跑 Lint、正式签名 Debug/Release/v2-v3 证书及仅 `emulator-5554` DocumentsUI 可见链。不得操作 OPPO。

## 最终验证证据（2026-08-13）

- API 35 `emulator-5554` 的真实 DocumentsUI 选择 `p4l-final-visible-chain.json`（严格 UTF-8、两项 `nfai.knowledge.json/v1` fixture）后，新私有任务显示 `AWAITING_CONFIRMATION`。在任务详情明确确认 `P4L Final Alpha`，再明确跳过 `P4L Final Beta`；界面回读 `COMPLETED / CONFIRMED / SKIPPED`。
- force-stop 后以 `NanfengAiActivity` 冷启动（1.741s），重新打开 JSON 任务仍显示同一 `COMPLETED / Alpha CONFIRMED / Beta SKIPPED` 状态，证明从独立 Room 任务与私有副本恢复，不使用数据库注入或开发后门。
- JSON 导出面中仅勾选刚确认且 ACTIVE 的 `P4L Final Alpha`；其余项（包括旧任务的已确认 Knowledge）均未勾选。界面报告原子写入、同文件回读和 SHA-256：`knowledge-20260812T222057795391Z.json`，`c7e1057b7025c4a9744315fbeeb7dd0048c3f32132c7a7dda0e39d4a3e030b9b`。为同 Adapter 前检，只在 emulator-5554 将此精确私有导出副本传至 Downloads（传输后 hash 相同），再由 DocumentsUI 选择 `p4l-roundtrip-export.json`；同一 Adapter 创建 `AWAITING_CONFIRMATION` 任务并只投影 `P4L Final Alpha · PENDING_CONFIRMATION`。该前检任务随后在 UI 中显式取消，未确认写入任何重复 Knowledge。自动全量回归同时覆盖本 Adapter 的严格解析边界。
- Android Studio JBR 下 `:app:testDebugUnitTest --rerun-tasks`：155 tests、0 failures、0 errors；`:app:lintDebug --rerun-tasks`：0 errors、11 个既有 warnings（SDK/Gradle/依赖、图标与 KTX 建议；图标验收仍暂停）；Debug/Release 均重新组装成功。
- `0.3.0-p4l` / code 31 正式签名产物：Debug SHA-256 `a95865d62255af756c273d1e3c0547c69b971dd107815d6f8a9859327ac9bb14`，Release SHA-256 `d8588cfc1429277a19683f860fdbb4e659b200460ae631ce185aebfcc6ce61d4`。两包 APK Signature Scheme v2/v3 为 true，证书 SHA-256 均为 `889ecf3ff4eeb40486e5122c6dc4eaaa04226ce6493d8a946c65275a803e99d5`。正式 Debug 已同签名 `install -r` 覆盖 `emulator-5554`，回读 version code 31 / `0.3.0-p4l`，从设备拉回 `base.apk` 的 SHA-256 与 Debug 一致。
- 全程未操作 OPPO、未读取/写入 Key、未构造 Prompt/RunSpec、未发 Provider HTTP、未产生费用或外发文本/图片；`OpenRouterEgressPolicy.Disabled` 保持。

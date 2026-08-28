# ChatGPT ZIP 导入附件链路问题开发档案

> 更新时间：2026-08-28
> 项目：南枫 AI Android
> 工作区：`/Users/nanzhufeng/Documents/工具开发/Nanfeng_AI`
> 用途：保留 ChatGPT ZIP 导入附件链路的完整根因、修复和验证证据，供后续开发者（包括 Claude）复查。本档案下方保留的 `852/853` 和诊断包记录是定位过程，不是当前代码结论。

## 一、先看结论

文本对话导入、新旧包去重合并、官方附件映射和普通 Message Tree 挂载在当前代码已达到真实包自动验收闭环：

- 新包 `784` 个可导入对话，旧包→新包合并后仍是 `784` 个，无重复对话。
- 官方明确可归属且 ZIP entry 存在的附件为 `853`，完整 Room 恢复为 `853/853`。
- 最后 `1` 个失败的精确异常是 `IllegalArgumentException: 当前分支必须指向叶消息。`根因不是附件或 Room 丢数，而是官方当前路径可以以不可渲染结构记录收尾；文本 parser 最后保留的可渲染节点仍可以有后代，不能直接当成当前叶节点。
- owner 现在优先保留官方路径下既有合法叶，否则确定性选择最新后代叶；每个会话失败不再被伪装为空成功，完成标记也要求 `failedConversationCount == 0`。
- 临时产品 Log、堆栈回调和 mapper 诊断参数已移除；真实 ZIP XML 为 `tests=1, skipped=0, failures=0, errors=0`。

**当前剩余的关键边界是真机验收，不是代码卡点。** OPPO 仍安装 SHA-256 `d86b...` 的旧诊断包，当时 UI 回读为 `0 个附件已恢复`。最新正式包已构建但本轮未安装；在用户再次明确授权覆盖以前，不能声称 OPPO 已恢复 853 个附件。

## 二、用户最终要求

1. 先导入旧 ChatGPT ZIP，再导入新 ZIP，对话可正常合并去重，不重复创建对话。
2. 导入后对话按原始 `updatedAt` 时间顺序出现在左侧栏。
3. 文本和所有官方可归属附件都必须进入普通消息树，与 App 自带对话完全一致。
4. 图片、视频、音频、文件要出现在搜索页各自分组，可预览、定位到原消息、下载、分享，并保留搜索页返回栈。
5. 在对话内要显示这些附件，不能只在搜索索引里出现。
6. 设置→数据与存储中，JSON 和 ZIP 保持两张独立大卡片；两张卡片都要有自己的“导入结果 / 查看详情”入口。
7. ZIP 详情要如实区分：已导入对话、已恢复附件、缺少官方对话归属而未自动关联的候选文件。
8. 不从文件名、时间、相邻消息或媒体内容猜测归属；只用 ChatGPT 官方导出里的明确 source ID / attachment pointer。

## 三、真实数据包与已知数量

### 3.1 用户指定的两个包

- 旧包：`/Users/nanzhufeng/Downloads/a223d5d9ad25c20a_ChatGPT_20260726.zip`
  - 字节数：`944,961,948`
  - 文本对话项：`517`
  - 有效文本对话：`506`
  - `EMPTY_CONTENT`：`11`
- 新包：`/Users/nanzhufeng/Downloads/ChatGPT_20260827.zip`
  - 字节数：`4,905,893,691`
  - 文本对话项：`796`
  - 有效文本对话：`784`
  - `EMPTY_CONTENT`：`12`
  - attachment 记录：`855`
  - 唯一 attachment ID：`854`
  - ZIP 中实际存在的官方归属 entry：`853`
  - 有 `conversation_asset_file_names.json` 官方显示名：`851`
  - 候选资产总数：`1675`
  - 官方映射之外、不可猜配的候选：`822`

### 3.2 去重合并已有证据

旧包→新包的隔离 Room 验收已证实：

- 最终为 `784` 个本地对话，不重复创建。
- 共享 source conversation 复用原本地 Conversation ID。
- 新增消息通过 append-only merge 追加。
- 对话列表按 `updatedAt DESC`、搜索索引和导入任务回读已通过对应验收。
- 这部分不等于附件链路已通过。

## 四、当前架构和数据流

### 4.1 文本对话导入链

```text
DocumentsUI 选择 ZIP
  → AndroidP6KZipIntakeStore.stage
  → app-private p6k-zip-import/v1/archives/<taskId>.zip
  → ThirdPartyZipInventoryPolicy
  → P6KChatGptZipCandidateMapper
  → RoomP6KZipImportTaskRepository
  → ManageP6KChatGptZipImportUseCase.importAll
  → RoomP6KZipImportCommitStore
  → Conversation / Message Tree + ZIP receipt/provenance
```

关键 owner：

- `app/src/main/java/com/nanzhufeng/ai/data/AndroidP6KZipIntakeStore.kt`
- `app/src/main/java/com/nanzhufeng/ai/domain/P6KThirdPartyZipInventory.kt`
- `app/src/main/java/com/nanzhufeng/ai/data/local/RoomP6KZipImportCommitStore.kt`
- `app/src/main/java/com/nanzhufeng/ai/data/local/RoomRepositories.kt`

### 4.2 附件映射与恢复链

```text
P6KZipImportViewModel.show
  → AndroidP6KZipIntakeStore.list
  → 对 COMPLETED / PARTIALLY_COMPLETED ChatGPT ZIP 任务执行 reconcileMappedAssets
  → P6KChatGptZipAssetMapper 读取官方 attachment ID / asset_pointer
  → 生成 P6KZipAssetMapping（完整 currentPath + 唯一 entry 表）
  → 生成 archive-backed AttachmentReference
  → RoomP6KZipMappedAssetLinkOwner.reconcile
  → 恢复被文本 parser 折叠的仅附件消息节点
  → 将 ContentBlock.Attachment 写回普通 Message Tree
  → 普通附件 catalog / 搜索 / 预览 / 定位 / 下载 / 分享
```

附件本体不复制约 `3.7 GB`数据：

- `AttachmentReference.reference` 使用 `P6KZipArchiveAssetStorage.key(taskId, entryName)`。
- `AndroidPrivateAttachmentStore` 预览或分享时才从原 app-private ZIP 流式读取指定 entry。
- 每次按已存储 `byteCount` / SHA-256 验证，再输出到私有预览缓存或流。
- 删除单个附件不能误删被其他消息共享的原 ZIP。

### 4.3 相关 Room 数据结构

已有表/实体大致分为：

- ZIP 任务、item、消息候选和 asset candidate。
- `p6k_zip_import_provenance`：source conversation → 本地 conversation。
- `p6k_zip_import_message_provenance`：source message → 本地 message node。
- `p6k_zip_import_receipts`：对话包级去重回执。
- `p6k_zip_asset_link_receipts`：当前以 `(taskId, entryName)` 表达附件链接回执。
- `private_attachment_assets`：普通附件 catalog，可按 SHA-256 复用附件引用。
- 普通 `Conversation` / `MessageNode` / `ContentBlock.Attachment`：真正的展示和搜索数据源。

## 五、已实现的价值

### 5.1 导入结果 UI

- JSON 和 ZIP 保持两张独立大卡片。
- JSON 和 ZIP 的“导入结果 / 查看详情”均已改为始终显示；没有任务时如实显示 `0 个导入批次`。
- ZIP 卡片摘要统计已导入对话和 `SOURCE_MAPPED / MANUAL_LINKED` 附件数。
- ZIP 详情分开显示“附件已恢复到原对话”和“缺少官方对话归属，未自动关联”。

相关位置：

- `app/src/main/java/com/nanzhufeng/ai/ui/NanfengAiApp.kt`，导入卡摘要约在 1900 行附近。
- `app/src/main/java/com/nanzhufeng/ai/ui/P6KZipImportUi.kt`，附件恢复/未归属详情约在 150 行附近。

### 5.2 官方归属映射

- 只读 `message.metadata.attachments[].id/name` 和 content part 里的 `asset_pointer / audio_asset_pointer`。
- pointer 映射到 ZIP 里的 `<file-id>.dat`。
- `conversation_asset_file_names.json` 仅用于官方显示名。
- MIME 优先由官方显示名扩展名和安全文件头识别。
- 不从本地文件名相似性、对话时间或文本内容猜测。

### 5.3 原生消息链

- 恢复结果写入普通 `MessageNode` 的 `ContentBlock.Attachment`，不另建一套“导入附件页”。
- 对于原消息只有附件、没有安全文本的节点，恢复 owner 会补建普通消息节点。
- 因此完成后理论上可以直接复用已有对话渲染、搜索分组、预览、定位和返回栈。

## 六、问题定位过程（不要重走）

### 6.1 第一层：旧完成标记会误阻止重试

旧代码使用 `<taskId>.assets-v1.done`。完成判定对空 `mapping.assets` 执行 `.all { ... }`，空集合会返回 true，因此可能在一个附件都没映射时也写下“已完成”标记。

当前工作区已改为：

- 新标记 `<taskId>.assets-v2.done`。
- 只有 `mapping.assets.isNotEmpty()` 且每个映射 entry 都已有 `attachmentId` 才写 v2。
- v2 成功后删除旧 v1 标记。
- 取消批次同时删除 v1/v2 标记。

这个 bug 是真实问题，但不是当前 `0` 的最终根因。安装含 v2 修复的包后，OPPO 仍显示 `0`。

### 6.2 第二层：真实包存在共享附件引用

为了不读私有数据库，在正式同签名包中临时加入了只记录数量和异常类型的 `P6KAssetRecovery` 日志。真机冷启动得到：

```text
mapping exception=java.lang.IllegalArgumentException
java.lang.IllegalArgumentException: 同一 ZIP 附件不能属于多个消息。
mapping rejected ... candidates=1675 archiveBytes=4905893691 expectedBytes=4905893691
```

这证明：

- 设备私有 ZIP 存在，且大小与用户新包完全一致。
- 不是 DocumentsUI 授权丢失、不是 ZIP 被删、不是 UI 缓存。
- 映射器的“一个 entry 只能有一个 source message”假设不符合 ChatGPT 真实导出语义。

当前工作区已将该约束改为：

- 完整 `currentPath` 保留每一个官方消息引用。
- 唯一 asset entry 表只保留第一个确定性 owner 作为元数据 owner。
- 同一附件字节不重复复制，但消息树可在多个精确 source occurrence 中复用它。

### 6.3 第三层：852/853 的最后一个失败（已解决）

使用环境变量确保测试进程真正读到用户新 ZIP：

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export NANFENG_AI_P6K_NEW_ZIP="/Users/nanzhufeng/Downloads/ChatGPT_20260827.zip"
./gradlew :app:testDebugUnitTest \
  --tests com.nanzhufeng.ai.data.P6KUserSelectedChatGptZipAttachmentChainAcceptanceTest
```

首次真正传入环境变量时，XML 为 `expected 853, actual 852`。将 owner 的静默降级改为测试可见失败回调后，捕获到唯一异常：

```text
java.lang.IllegalArgumentException: 当前分支必须指向叶消息。
```

官方当前路径可能以不可渲染的结构记录收尾，而文本 parser 最后保留的消息仍可能有后代。修复后的 `selectLeaf(...)` 先判断已有当前叶是否位于恢复路径下；如果不是，选择确定性的最新后代叶节点。

最终 XML 证据：

```text
tests=1, skipped=0, failures=0, errors=0
time=249.963s
official source-owned assets=853
persisted linked assets=853
failed conversations=0
```

结果文件由下述命令生成：

`app/build/test-results/testDebugUnitTest/TEST-com.nanzhufeng.ai.data.P6KUserSelectedChatGptZipAttachmentChainAcceptanceTest.xml`

### 6.4 已排除的假设

- **不是导入 ZIP 丢失：** 设备 archiveBytes 与新 ZIP 精确相等。
- **不是文本对话未导入：** `784` 个对话在设备上已可见。
- **不是附件所属对话为 EMPTY_CONTENT：** 集合核对显示 `mapping.assets.keys - referencedByImportedConversations = 0`。
- **不是整体映射失败：** 放开官方共享引用后，映射层已能得到全部 `853`。
- **不是附件 catalog 丢失：** 最后的单个失败是 Message Tree 当前叶约束，修复叶节点选择后完整 Room 链路为 `853/853`。
- **不能把自动测试写成真机完成：** 当前 OPPO 仍是旧诊断包。

## 七、根因收口与剩余架构风险

### 7.1 会话事务异常被静默吞掉（已修复）

`RoomP6KZipMappedAssetLinkOwner.kt` 约 37–159 行对每个 source conversation 执行事务，但外层使用：

```kotlin
val outcome = runCatching {
    database.runInTransaction(Callable { ... })
}.getOrDefault(Outcome())
```

这会把下列错误全部变成 `linked=0, createdMessages=0`：

- Room 约束/外键/主键冲突。
- Conversation graph 校验失败。
- 共享附件 receipt/provenance 冲突。
- 同一本地附件被多个 source occurrence 复用时的状态覆盖。
- 任何代码约束 `require/check`。

当前实现已使用 `getOrElse` 显式累计 `failedConversationCount`，测试可通过不泄露内容的失败回调拿到原异常。这一调整找到了最后 1 个 Message Tree 叶节点错误。产品代码不输出对话标题、正文、文件名或堆栈。

### 7.2 “唯一附件”与“多消息引用”的持久化模型（当前已满足实包，仍是后续演进点）

当前模型中：

- `P6KZipAssetCandidate` 只有一组 `sourceConversationId/sourceMessageId/linkedConversationId/linkedMessageId/attachmentId`。
- asset link receipt 主要按 `(taskId, entryName)` 去重。
- 但真实 ChatGPT ZIP 允许同一 entry 出现在多个 source message，甚至可能跨对话。

当前映射层保留官方路径中的多 occurrence，唯一 asset entry 只保留第一个确定性 owner 作元数据 owner；真实包 `853/853` 已证明这一实现可以完整挂载当前官方归属资产。但若后续要对“每个 occurrence 的独立删除／重放／审计”做强语义，仍建议拆成：

1. **Asset catalog（唯一文件）**：`taskId + entryName + sha256 + byteCount + mime + displayName + attachmentId`。
2. **Asset occurrence（官方引用）**：`taskId + entryName + sourceConversationId + sourceMessageId`，四元组唯一。
3. **Occurrence link receipt（本地挂载回执）**：对应本地 `conversationId + messageId + attachmentId`。

这样才能同时满足：

- 附件字节只保留一份。
- 所有官方消息引用都有独立、可重放、可删除、可审计的 receipt。
- 完成标记验证“每个 occurrence 已挂载”，而不是只看每个 entry 有一个 `attachmentId`。

### 7.3 v2 完成标记已增加会话失败门，occurrence 粒度仍可演进

当前 v2 判定是：映射非空、所有 `mapping.assets.keys` 对应 candidate 有 `attachmentId`，且 `failedConversationCount == 0`。这防止了空映射或局部会话失败被标记成功。它仍主要验证每个唯一 entry，尚不是独立 occurrence receipt 模型。

如果引入 occurrence 表，v2 或新 v3 marker 应基于：

- 映射出的 occurrence 集合非空。
- 每个 occurrence 都有匹配的本地 link receipt。
- 唯一 asset 数、occurrence 数、失败 conversation 数分开统计。
- `822` 个没有官方归属的候选不阻止官方映射完成标记。

### 7.4 性能和可恢复性

真实 `4.9 GB / 784 对话 / 853 附件` 的 checkpoint 提交后完整 Room 验收耗时 `249.963` 秒。当前是逐对话事务，并在循环内反复读取整个 task asset 集合。

后续不应为了提速破坏原子性，但可以：

- 事务前一次加载只读 asset/occurrence 映射。
- 按 conversation 保留原子写入，但提交明确的 progress/failure receipt。
- 应用启动不要在无限期的页面 `show/list` 调用中重复扫描 4.9GB ZIP。
- 恢复任务应是可重放、有进度、有失败类型的持久化 job，不应只靠一个 `.done` 文件和 UI 回读隐式触发。

## 八、测试证据与误判记录

### 8.1 曾被误报为“真实包通过”的测试

之前使用：

```bash
./gradlew :app:testDebugUnitTest \
  --tests com.nanzhufeng.ai.data.P6KUserSelectedChatGptZipAttachmentChainAcceptanceTest \
  -Dnanfeng.ai.p6k.newZip=/Users/nanzhufeng/Downloads/ChatGPT_20260827.zip
```

Gradle 并没有把该 `-D` 属性传入 fork 出的测试 JVM。XML 实际为：

```text
tests=1, skipped=1, failures=0
requires an explicitly selected newer ChatGPT ZIP
```

所以这次 BUILD SUCCESSFUL 不是业务通过。后续必须使用 `NANFENG_AI_P6K_NEW_ZIP` 环境变量，并回读 XML 的 `skipped=0`。

### 8.2 当前测试状态

| 验证 | 结果 | 证据/边界 |
|---|---|---|
| 单个官方附件映射 | 通过 | `P6KChatGptZipAssetMapperContractsTest` |
| 同一官方 file ID 被多个消息引用 | 小型契约通过 | 工作区已新增共享引用 fixture |
| 新真实 ZIP 映射 853 个附件 | 映射通过 | 必须用环境变量触发 |
| 映射附件是否都属于已导入对话 | 通过，差集为 0 | `P6KUserSelectedChatGptZipAssetAcceptanceTest`，`skipped=0, failures=0` |
| 新真实 ZIP → 完整 Room 附件链 | **通过** | `expected 853, actual 853`，`skipped=0, failures=0`，`249.963s` |
| 旧 ZIP → 新 ZIP 去重合并 | **通过** | 最终 `784` 个对话，真实包验收 `skipped=0, failures=0`，`320.925s` |
| 完整 JVM | **非全绿** | `815 tests / 60 failures / 3 skipped / 0 errors`；3 个 opt-in 真包测试已分开真实运行通过 |
| Lint | 通过 | `0 errors, 84 warnings, 13 hints` |
| Release 构建 | 通过 | SHA-256 `b0d1008fde0bbbddaaad57d925e09ca067dbbb64407d9783904955a999481929` |
| OPPO 保数据覆盖 | 安装层通过 | 业务层仍为 0 附件，不能当作完成 |

## 九、当前工作区的精确状态

### 9.1 Git

- 上一个 checkpoint：`da20412 checkpoint: consolidate conversation and settings refinements`（2026-08-27 22:52:45 +0800）。
- 本档案、当前交接和下列源码／测试随本轮“当前代码 checkpoint”一起提交；具体 commit 以 `git log -1` 的本地读回为准。
- 本轮不使用 reset/checkout/stash/clean，不删除或覆盖用户已有改动。

附件问题直接相关的文件：

- `M app/src/main/java/com/nanzhufeng/ai/data/AndroidP6KZipIntakeStore.kt`
- `M app/src/main/java/com/nanzhufeng/ai/domain/P6KThirdPartyZipInventory.kt`
- `?? app/src/main/java/com/nanzhufeng/ai/data/local/RoomP6KZipMappedAssetLinkOwner.kt`
- `?? app/src/test/java/com/nanzhufeng/ai/data/P6KUserSelectedChatGptZipAttachmentChainAcceptanceTest.kt`
- `?? app/src/test/java/com/nanzhufeng/ai/domain/P6KChatGptZipAssetMapperContractsTest.kt`
- `M docs/CURRENT_HANDOFF.md`

另有搜索、附件存储、UI 和其他真包验收文件在当前未提交树中；必须先做限定路径的 diff/stat，不要假定都可删。

### 9.2 临时诊断已移除

- `AndroidP6KZipIntakeStore.kt` 不再包含 `P6KAssetRecovery` tag、数量 Log 或 exception stack log。
- `P6KChatGptZipAssetMapper` 不再有临时 `onRejected` 构造参数。
- owner 只将会话失败数量并入结构化 summary；原异常回调仅用于 JVM 测试定位，产品默认为空回调。

### 9.3 最新 Release APK 已与当前源码同步，但未上机

最新本地 Release APK：

```text
b0d1008fde0bbbddaaad57d925e09ca067dbbb64407d9783904955a999481929
```

包名 `com.nanzhufeng.ai`，版本 `66 / 0.3.0-p10j`，证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`，minSdk 26。`lintDebug` 为 `0 errors, 84 warnings, 13 hints`，`assembleRelease` 成功。该 APK 包含当前共享引用、合法叶节点选择和失败门修复，但本轮未安装到 OPPO。

### 9.4 OPPO 当前状态

- 设备 serial：`3B157F009E800000`
- 型号：OPPO PKH120 / Find N5
- 包名：`com.nanzhufeng.ai`
- 版本：`66 / 0.3.0-p10j`
- 调试标志：非 Debug
- 证书 SHA-256：`6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`
- 首次安装时间：`2026-08-20 15:15:31`（多次覆盖后保持不变）
- 当前更新时间：`2026-08-28 12:00:41`
- 当前 APK SHA-256：`d86b670e050da976aa07151928059b49a9e0936fd7422dbac5883ec683c7c390`
- 当前设置回读：`1 个 ZIP 导入批次 · 784 个对话已导入 · 0 个附件已恢复`
- 数据保全：已经使用 `pm install -r --user 0` 同签名保数据覆盖；未卸载、未清数据、未注入私有数据库、未运行任何 `connected*AndroidTest`。

## 十、文档冲突和证据优先级

本轮已将 `docs/CURRENT_HANDOFF.md` 顶部更新为当前真实结果，并明确区分：

- 当前代码／真实 ZIP Room 验收：`853/853`。
- 当前本地 Release：SHA-256 `b0d1008f...481929`。
- OPPO 实际已安装旧诊断包：SHA-256 `d86b670e...c390`，当时 UI 仍是 `0`。
- 本档中的 `852/853`、静默异常和诊断 Log 为历史定位过程，不得覆盖顶部结论。

对本问题应按以下顺序取信：

1. 当前源码。
2. 最新测试 XML，并确认 `skipped=0`。
3. OPPO 实际安装包回拉哈希和 UI 回读。
4. `CURRENT_HANDOFF.md` 顶部和本档案顶部。
5. 最后才是历史阶段文档。

## 十一、后续接手顺序

### 步骤 1：代码根因与自动验收已完成

1. 不要恢复“一个 entry 只能属于一条消息”的旧约束。
2. 不要把最后可渲染官方节点直接当作叶节点；保留 `selectLeaf(...)` 的合法后代叶选择。
3. 不要恢复 `getOrDefault(Outcome())` 式静默降级。
4. 修改真包测试时使用 `NANFENG_AI_P6K_NEW_ZIP` / `NANFENG_AI_P6K_OLD_ZIP`，并回读 XML 确认 `skipped=0`。

### 步骤 2：后续可选的 occurrence 模型演进

1. 当前真实包已恢复 `853/853`，不需要为了追求表结构完美而立即重写。
2. 只在需要每个 occurrence 独立删除、重放或审计时，再引入独立 asset occurrence 模型。
3. 如果修改 Room schema，必须添加显式 migration，不得 destructive migration。
4. 同一附件被多消息、多对话引用时：
   - 私有字节/catalog 只保留一份。
   - 每个消息都要有 `ContentBlock.Attachment`。
   - 每个 occurrence 都要有可重放 receipt。
   - 删除/重试一个批次时不得误删其他引用仍使用的字节。

### 步骤 3：保留真实包门禁

1. 完整真包链必须继续达到：
   - `skipped=0`
   - `failures=0`
   - 唯一官方 asset `853/853`
   - 所有 source occurrence 都有本地挂载 receipt
   - 未归属候选仍为 `822`，不被猜配
3. 保留旧包→新包合并验收，并增加共享 asset occurrence 在累积包更新中的去重。
4. 完整测试后必须回读 XML，不能只看 Gradle `BUILD SUCCESSFUL`。

### 步骤 4：产物已重建，禁止重新引入临时诊断

1. 当前包 SHA-256 为 `b0d1008fde0bbbddaaad57d925e09ca067dbbb64407d9783904955a999481929`。
2. 如果后续再加诊断，只保留结构化、无标题／正文／文件名的本机统计，完成后移除。

### 步骤 5：OPPO 保数据真实回读

只有用户再次明确授权“覆盖安装”后才能执行。必须：

1. 先回拉当前 `base.apk`，核对同包名、同证书、非 Debug。
2. 仅使用 `adb push` 到明确的 `/data/local/tmp/<name>.apk`，再执行 `pm install -r --user 0`。
3. 安装后删除该精确临时 APK。
4. 回拉设备 `base.apk` 验证哈希，核对 `firstInstallTime` 未变。
5. 冷启动，等待恢复 job 完成。
6. 设置页预期为：`1 个 ZIP 导入批次 · 784 个对话已导入 · 853 个附件已恢复`。
7. 详情预期为：`853 个附件已恢复到原对话`，`822 个文件缺少官方对话归属`。
8. 在搜索页分别打开图片、视频、音频、文件分组，确认导入内容实际可见。
9. 从搜索结果打开对话，确认原消息附件、预览和返回搜索栈。
10. 不得卸载、清数据、注入数据库、安装 Debug/仪器包或运行 `connected*AndroidTest`。

## 十二、建议补充的最小测试集

1. **Mapper 共享引用契约**：同一 file ID 被同一对话两个消息引用，两个 occurrence 都保留。
2. **跨对话共享引用契约**：同一 entry 被两个 source conversation 引用。
3. **Room occurrence 回执契约**：一份附件字节、多个消息块、多个可重放 receipt。
4. **事务失败可见契约**：人为制造图约束/receipt 冲突，必须返回结构化失败，不能变成静默 `Outcome()`。
5. **marker 契约**：空映射、部分 entry、部分 occurrence 均不能写完成标记。
6. **真实包资产验收**：`853` 唯一 asset，官方 occurrence 全部可追溯，未归属为 `822`。
7. **真实包完整 Room 验收**：`853/853`，`skipped=0`。
8. **旧→新包合并**：对话 ID、消息 provenance、asset occurrence 和搜索索引均不重复。
9. **原生搜索契约**：图片/视频/音频/文件全部从普通 Message Tree 投影，不另造假索引。
10. **导入结果 UI 契约**：JSON/ZIP 入口分开且始终显示，数字来自真实 task/asset/receipt。

## 十三、不要做的事

- 不要为了让数字变成 853 而从文件名、时间或内容猜测归属。
- 不要把 `822` 个无官方归属候选随意挂到对话。
- 不要只修卡片数字或搜索 UI；真正数据必须进普通 Message Tree。
- 不要复制 3.7GB 附件本体到另一套存储来“修复”。
- 不要把唯一 asset 字节与多消息 occurrence 混为同一种 identity。
- 不要继续吞掉事务异常。
- 不要将 JUnit assumption skip 写成真实包通过。
- 不要用当前 `d86b...` 诊断 APK 声称共享附件修复已上机。
- 不要在 OPPO 上卸载、清数据、运行 Debug/仪器测试或注入数据库。
- 不要为了重放本问题而 reset/checkout/stash/clean 用户现有改动。

## 十四、完成定义

代码与自动验收已满足：

- 每个会话事务失败可精确定位，不静默降级。
- 新真实 ZIP 的完整 Room 链为 `853/853`，XML `skipped=0, failures=0`。
- 旧包→新包依旧只得到 `784` 个去重对话。
- 最终 Release 移除临时诊断，构建、签名和哈希回读通过。

整个用户问题只剩真机业务验收未完成：

- 在用户明确授权后，OPPO 同签名保数据覆盖，首次安装时间和原有对话数据保留。
- 设置页真实回读 `853 个附件已恢复`，JSON/ZIP 导入结果入口独立且始终可见。
- 搜索页图片／视频／音频／文件分组显示真实导入附件。
- 从搜索打开对话后，附件渲染、预览、定位、下载、分享和返回栈与原生附件一致。
- 在用户明确授权后，OPPO 同签名保数据覆盖并完成真实 UI 回读；首次安装时间和原有对话数据保留。

## 十五、当前最小接手信息

如果只有几分钟，请先看这五个点：

1. 当前 OPPO 是 `d86b...` 诊断包，仍显示 0 附件。
2. 当前代码已放开官方共享附件引用，并修复当前分支必须指向叶消息的结构尾部问题。
3. 真包完整 Room 测试是 `853/853`，`skipped=0, failures=0`。
4. 最新 Release 已构建，SHA-256 `b0d1008f...481929`，本轮未安装。
5. 现在卡点是用户授权后的 OPPO 保数据覆盖和真实 UI 链路回读，不是代码根因未知。

# P6-C：Desktop 本地领域写入、可逆动作与模型/成本 metadata 工作台合同

## 状态与目标

P6-C 在 P6-B 的 Rust-owned app-private SQLite 基础上，建立 Desktop 本地领域的明确写入链；它不是 P6 或项目终点。唯一写入入口是 `DesktopWorkspaceStore` 的 typed Tauri command → domain/repository → SQLite transaction。前端只提交结构化 intent，绝不拼 SQL、改 exchange JSON、写本地文件或保留仅内存的业务状态。

范围是 Workspace / Project、Conversation 标题与消息树、Knowledge、Memory、Knowledge relation 的本地 CRUD，及只读或手工录入的 Provider/Model/价格 metadata。无 Provider 调用、Key、Prompt、RunSpec、raw invocation payload/chunk、真实计费、网络 catalog、账号/同步/Hub/Agent。

## 领域事实、版本与导入来源

- 每个新领域对象由 Rust 生成并验证稳定 ID；所有 ID、revision、createdAt/updatedAt、contentHash、scope、status 均以 Desktop domain 为真值，不依赖 Android Room 表名或路径。
- 写入 intent 必含 `intentId`、`workspaceId`、对象 kind、允许字段和 `expectedRevision`。同一 `intentId` 的成功重放返回首次 receipt；失败或冲突不得留下局部写入。
- 正常更新要求 `expectedRevision == currentRevision`，成功生成 `revision + 1` 和新 hash；不匹配返回结构化 `REVISION_CONFLICT`（对象 ID、expected、actual、当前安全摘要），不静默覆盖。
- imported 对象保留 package/semantic hash、原 stable ID 和 imported revision；本地第一次编辑产生可审计的新 Desktop revision，同时保留 `source=IMPORTED` 与 origin 摘要。新建对象标为 `LOCAL`。不保存外部路径、URI、Key 或原始敏感 payload。
- 正文仅当作文本。Markdown、HTML、链接和代码不执行、不渲染为 HTML，也不触发外链或脚本。

## 可写操作与完整性

- Project 可新建、编辑 title/description、archive/restore；Conversation 可新建、编辑 title、显式追加节点到既有消息树；Knowledge 可新建、编辑正文/tags/scope/status；Memory 可新建、编辑正文/scope/status。所有正文、长度、classification、scope 引用和稳定 ID 在 Rust 校验。
- Conversation tree 的新节点必须指向活动 conversation 内的已存在 parent（或显式 root），ordinal 不可冲突；不生成助手内容、Prompt 或 RunSpec。
- Knowledge/Memory 的 `contentHash` 由 Rust 基于允许的规范化领域正文计算。scope 必须与 project/conversation 活动状态匹配；GLOBAL 无 scopeId，PROJECT/CONVERSATION 必有相应活动端点。
- Relation 只允许显式新建/撤销；两端必须存在、活动且在同一 scope。`RELATED` 是对称关系，规范端点顺序且拒绝重复；`DERIVED_FROM` 与 `REFERENCES` 有向且拒绝相同方向的活跃重复。不会自动生成 relation、去重对象或 Memory。

## 软删除、恢复与永久删除

- Project/Conversation 使用 archive 作为保留可见历史的软隐藏；Knowledge/Memory 使用既有 `DELETED`，Relation 使用 `REVOKED`。软删除/撤销会新建 revision，保留历史 ledger；被删除/归档对象不得作为新的 scope 或 relation 端点。
- 回收站只展示可恢复的软删除对象。恢复要求 `expectedRevision`，检查父 scope/端点活动，再生成新 revision；失败返回可见冲突或约束原因。
- P6-C 不开放永久删除。任何 UI/command 都不得清除历史、原始 imported provenance 或 ledger；未来永久删除必须另立强确认、保留期、附件引用与恢复边界合同。

## 持久化 undo / redo

- 每次成功且可证明本地可逆的动作写入 append-only intent/revision ledger 与持久 action stack；重启后仍可见。
- Undo 只针对最近可逆本地动作，使用当时的可验证 before/after 摘要和当前 revision；它生成一个新的 revision/intent，而不改写历史。导入、导出、冲突、不可逆/外部动作不进入 undo。
- Redo 只重放被 undo 的同一安全变更，仍受当前 revision 与 scope 约束；任意新写入截断 redo 分支。重复点击/命令重放依赖 intentId 幂等，不能重复追加树节点或重复创建对象。

## exchange v1 兼容与导出

- P6-C 不改变 `nfai.exchange.v1` schema、packageVersion、Android app version 或 Android import 写库边界。Desktop 的本地状态映射回 v1 已允许的 Project / Conversation / Knowledge / Memory / Relation 字段；软删除使用既有 `archived`、`DELETED`、`REVOKED` 表达。
- provenance、ledger、undo stack、conflict receipt、provider/model metadata 与成本摘要是 Desktop 私有 metadata，绝不作为 v1 未知字段导出。
- 导出仍使用 Rust canonical package 和同文件 strict preflight；产物必须可被 Node runner 与 Android `NfaiExchangeV1Gateway` 的只读 preflight 接受。Android 未获得导入写库能力。

## 模型与成本 metadata

- Provider/Model preset 只保存用户明确配置或本地 fixture 的名称、capability、价格版本、币种、来源、更新时间和“非真实价格/仅 metadata”状态；禁止字段与交换协议相同，不可存 Key/endpoint/HTTP 内容。
- Invocation cost 面板只读本地安全聚合或 fixture metadata：计数、已知 token/cost、币种、价格版本、来源与更新时间。`null` 表示未知，`0` 表示明确零；不读取 raw payload/chunk，也不计算或宣称真实费用。
- 价格/catalog 不联网拉取；未配置时 UI 明确为空或未配置，而非假定默认模型、价格或可用能力。

## UI 与安全

- Expanded 三栏保留高密度 Workspace/Project/Conversation tree、正文画布、可折叠 Inspector；新增明显编辑、保存、取消、undo/redo、冲突、回收站、模型与成本 metadata 入口。Compact 使用可恢复 drawer 与顺序 Inspector，2.0x 缩放不裁切主操作。
- `Cmd/Ctrl+S` 保存、`Cmd/Ctrl+Z` undo、`Cmd/Ctrl+Shift+Z` redo；在文本编辑控件之外由工作台接管，焦点、disabled/冲突说明与状态消息可达。App-owned Dialog/Menu/Picker 的 selection face 为纯白 `#FFFFFFFF`。
- capability 仅增本合同 typed commands；不加 shell/process/http/updater/global filesystem。所有 multi-record write 以 SQLite transaction 成功或 rollback；高敏/恶意/超长/未知字段/不活动 scope 整体拒绝，错误不回显正文、路径或 secret。

## 验收与尚未完成范围

- Rust：migration/reopen、Domain CRUD、revision conflict、idempotency、ledger undo/redo、rollback、scope/relation、soft delete/restore、导出 roundtrip 与 malicious input。
- 前端：state/forms、保存/取消、快捷键、冲突与回收站、metadata、compact/2.0x 和纯白 selection surface；typecheck/lint/test/build。
- 跨端：Node golden 与 Android `P6AExchangeContractsTest` 定向 preflight。没有 Android write import、协议升级或无关 Android 版本变更。
- macOS：在新专用 workspace 完成写入→edit→undo/redo→soft delete/restore→重启→export→Node/Android preflight；生成 ad-hoc 开发包/hash/大小/启动证据。macOS Developer ID/notarization 与全部 Windows 交付继续是债务。

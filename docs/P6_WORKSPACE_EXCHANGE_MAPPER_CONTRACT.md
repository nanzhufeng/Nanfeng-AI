# P6 工作区语义交换 Mapper 合同

## 目标与停止条件

本增量让 Android 可从既有 Project、Conversation、Knowledge、Memory、Knowledge Relationship 与私有附件 owner 生成一个 `nfai.exchange.v1` 安全快照。它是纯领域 mapper：不写 Room、不做 Android 导入、不启动 SAF、不新增界面或常驻按键，也不调用 Provider、Key、HTTP、同步或设备。

停止条件是 mapper 与协议 gateway 的本地合同闭环；Android 正常 Settings/SAF 的完整工作区选择、Android 导入、Desktop/Android 的真实全对象往返、紧凑/展开恢复、Windows 与发布仍是 P6 的独立退出门。

## 唯一所有者与显式选择

- `ExportWorkspaceExchangeUseCase` 是唯一 Android 全对象 v1 语义 mapper；`RepositoryNfaiExchangeWorkspaceSource` 只把既有 repository/private attachment owner 的只读事实适配给它。
- `NfaiExchangeExportSelection` 精确列出 project、conversation、knowledge、memory、relation 与 attachment ID；`NfaiExchangeWorkspaceSelection` 还必须为每个附件给出 `NORMAL` 或 `HIGH_SENSITIVE` classification。mapper 不会从项目、关系、会话或目录自动扩大选择。
- `NfaiExchangeSafeSettings` 只允许 `uiLanguage` 与 `theme`。Provider、凭据、route preference、路径、URI、诊断、运行时与账本不在输入或输出中。

## Scope、关系与附件闭包

- 已选 Project Conversation、Project Knowledge、Project Memory 必须同时显式选择其 Project；Conversation Memory 必须同时选择其 Conversation。
- 已选 Relationship 的两个 Knowledge endpoint 都必须被选择；未选择的关系不会被推断或自动补进。
- 已选 Conversation 的每一个持久 `ContentBlock.Attachment` 必须恰好出现在 `attachmentIds`，且不能选择没有被所选消息引用的附件。
- mapper 重新读取私有附件目录，核对 ID/MIME/字节数/SHA-256，并且仅将通过核验的 bytes 以 `assets/<sha256>` 写入准备快照。相同 hash 的 bytes 只封装一次，但每个消息引用保留自己的安全 attachment ID。

## 映射、失败与恢复

- Project、Conversation message tree、Knowledge lifecycle/scope、Memory scope/status、Relationship endpoint/type/status 均映射到 v1 的对应 payload；revision 使用 owner 的当前最大 revision，稳定顺序按 ID 或消息时间/ID 保留。
- 未保存草稿、`WORK`/已删除 conversation、缺少 leaf、ToolResult、缺少/不匹配/不可读取附件、未选引用依赖、Project 删除和 Knowledge 自带附件都在准备阶段失败关闭；不会构造部分 JSON 或写出文件。Knowledge 附件在 v1 没有关联位置，必须另立版本化协议后才可支持。
- 现有 `NfaiExchangeV1Gateway` 再核验所选 IDs 与所有 `ASSET_REF`、内容寻址 asset entry 的双向一致性，计算唯一 canonical `semanticHash`，并在 export 后 preflight 回读。失败只返回拒绝，不报告成功。
- Desktop 继续先 private staging/preflight，再私有 asset/package 落盘和 SQLite transaction；只有 `workspace`、IR、asset index、provenance 与 `import_journal` 都提交才可见。提交前中断只可能留下不可见 orphan blob；不创建半工作区。receipt 同时保留 package hash 与 semantic hash，重复包拒绝隐式合并。

## 验证与产品入口

- `WorkspaceExchangeExportContractsTest` 覆盖全对象映射→gateway export/preflight、scope/relationship/attachment 三类显式选择缺失与私有附件不可读失败关闭。
- 本增量没有新的用户可见入口，因此不新增 Android/Desktop 设置 → 功能审阅条目；后续把 mapper 接入 Settings/SAF 或导入入口时，必须在同一变更登记两端审阅，并验证真实文件链。

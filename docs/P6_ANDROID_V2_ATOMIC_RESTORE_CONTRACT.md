# P6 Android v2 原子恢复合同

## 目标与边界

本合同只定义 Android 对 `nfai.exchange.v2` 完整工作区包的本地、原子恢复所有者。它是独立质量增量，不改变 P6 的既有 Android 导出 → Desktop 导入/回导退出证据，也不构成同步、备份、Provider、账号或 P0–P11 完成。

唯一生产领域入口为 `WorkspaceExchangeV2AtomicRestoreOwner.restore`。入口只接收调用方已经有界读取的 package bytes 与不透明 `intentId`；它不接受 `Uri`、path、Room/DAO、Context、ViewModel、SAF、网络、Provider 或 Key。未来 OpenDocument adapter 只能在用户明确选择后读取有限 bytes，再调用这个入口；UI 不解析 ZIP/manifest/IR，也不直接写 Room。

## 不覆盖本机真值

恢复只有在 atomic store 明确报告本机业务真值为空时才能进入提交。任一已存在 Project、Conversation、Knowledge、Memory、Relationship、私有附件、v2 provenance 或未完成恢复 journal 都使本次操作以 `LOCAL_TRUTH_PRESENT` 停止；不得 merge、upsert、覆盖、删除、替换或“以导入为准”。重新恢复到非空设备需要另一份、用户可见的方向选择与冲突合同。

同一 `intentId` 只可重放同一 `packageHash + semanticHash` 的既有 receipt；不同包一律 `INTENT_CONFLICT`。重放不重新写对象、附件、receipt 或 provenance。reader 拒绝、baseline 非空或任何 commit 失败时都不得产生成功 receipt。

## 解析、提交与可恢复边界

所有 package 解释必须先由唯一 `NfaiExchangeV2PackageReader` 在内存中完成。严格 preflight 成功前，owner 不读取本机真值、不创建 staging、不写 receipt/provenance/附件，也不调用提交端口。

通过 preflight 后，owner 将 canonical IR、当前调用内的附件 bytes 及 content-free `packageHash/semanticHash/origin/sensitivity/counts/owner-field hashes` 作为一个不可拆分的 `WorkspaceExchangeV2AtomicRestoreCommit` 交给唯一 atomic store。store 必须在同一 Room transaction 或等价可恢复边界内完成：

1. 再次核对“本机为空”和 intent replay/conflict；
2. 写入全部 Project、Conversation/Message、Knowledge、Memory、Relationship 和安全 Settings；
3. 将每个附件先安全置入 app-private staged root，逐字节 hash 回读，再把附件 owner/reference 与业务记录一起提交；
4. 在同一原子边界写入 v2 provenance 和 content-free receipt；
5. 完成 typed readback（引用完整性、semantic hash、asset ledger 与 owner-field hashes）后才发布 committed receipt。

任一步失败必须保留既有本机真值；未发布的 candidate/附件只可保留为恢复 journal，不能被业务 UI 当作已恢复内容。下一次同 intent、同 package 的 owner 调用只可在 database 仍完全空、没有其他 journal，且 journal staged 文件名逐项属于该 receipt 的 asset ledger、final 残留逐文件 hash 匹配该 ledger 时清理该未发布候选并从头重试（staged bytes 可是不完整的写入）。任何未知文件、未知名称或 final hash 不符都保持 `RECOVERY_REQUIRED`，不得猜测、删除或覆盖。恢复成功后若底层采用 database switch，旧 live database 直到 typed readback 成功才可移除；调用方必须重建容器后读取新的真值。

## Receipt 与 provenance 最小化

receipt/provenance 只允许 intent ID、package/semantic hash、origin、sensitivity、owner/asset counts、导入版本、时间和安全 outcome。不得记录 package bytes、IR 正文、附件 bytes、展示名、URI/path、账号、Provider、Key、token 或原始错误。附件 bytes 只能存在于本次调用、私有 staging 或最终 private attachment owner；不进入 receipt、日志或 UI 状态。

## 自动合同

- ZIP/manifest/IR/asset 任何严格 reader 失败：零本机读取、零 staging、零 commit。
- 非空本机：返回 `LOCAL_TRUTH_PRESENT`，零对象/附件/receipt/provenance 写入。
- 空本机且 package 有效：仅一次 atomic commit，commit input 的 receipt/hash 与 reader 完全一致。
- 同 intent 同包重放只回读 receipt；同 intent 异包拒绝，均零新写入。
- atomic store 的失败或中断：不返回成功、零 partial receipt/provenance；已存在本机真值不变。只有精确同包、hash-complete、数据库仍空的 journal 可回收并安全重试；其余 candidate 只按 `RECOVERY_REQUIRED` 保留。
- schema 38 历史实例升级至 39 只追加 v2 receipt/provenance/settings 表，不改写既有事实；完整 migration 链必须连续至 39。

## 尚未接入

本增量已实现 schema 38→39 的专属 content-free receipt/provenance/settings 表与 `AndroidWorkspaceExchangeV2AtomicRestoreStore`。它以私有 journal staging、附件 hash 回读和一次 Room transaction 组成可恢复边界：正常失败回滚已移动附件且不发布 receipt；受控中断留下 journal 后，同包在完整 asset ledger 和空 database 双重证明下可从头安全重试，其余 journal 继续阻断恢复，而不把候选对象当作真值。

本合同之后已接入独立的 `AndroidWorkspaceExchangeV2OpenDocumentRestorePort`、`WorkspaceExchangeV2RestoreViewModel` 与设置二级入口。OpenDocument 只可在用户明确选择一个文件后读取至多 128 MiB bytes；adapter 不解析 ZIP/JSON、不保留 URI/path/name/正文，且只把有限 bytes 与由 package hash 派生的 opaque intent ID 交给本 owner。UI 只投影 content-free 成功、拒绝或 recovery 状态。真实 DocumentsUI 文件链仍需在项目专属空 AVD 独立验收；OPPO 不在本合同范围。

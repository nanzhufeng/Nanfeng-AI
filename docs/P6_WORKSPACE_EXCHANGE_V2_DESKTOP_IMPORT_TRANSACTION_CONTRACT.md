# P6 工作区交换 v2 Desktop 私有暂存、原子导入与回导合同

## 结论、范围与停止门

本合同冻结 `nfai.exchange.v2` 在 Desktop 的**未来内部导入 owner**。它只消费已由 `protocol/nfai.exchange.v2.schema.json`、`protocol/scripts/exchange-v2-lib.mjs` 和 `NfaiExchangeV2Ir` 确认的 exact IR；不重新解释、不补默认值、不把 v2 降级为 v1，也不把 v1 的 `workspaces/workspace_exchange/import_journal` 当作 v2 owner。

已实现 v2 package writer、private archive、SQLite migration 和独立 Tauri command；Desktop 设置的 native picker 只把用户选中的单一 `.nfai-exchange` 交给该 command。该桥接不接受 v1 staging ID、不返回路径/正文/显示名/bytes、不读写 v1 工作区表，失败不生成可见 workspace。Android 没有 v2 SAF/UI。没有网络、Keychain、Provider、设备或数据库注入验收；尚无真实 native picker 人工文件验收、跨端 v2 互通、Windows 或发布结论。

## v2 私有 package 与严格 preflight

v2 package 使用独立 `packageVersion: 2`，且 `exchangeVersion: 2`；v1 预检必须拒绝它。ZIP 仅允许下列 manifest 列出的文件：

| entry | 必须内容 | 校验 |
| --- | --- | --- |
| `manifest.json` | `format: nfai.exchange.package`、`packageVersion: 2`、`exchangeVersion: 2`、exact `export`、files 清单 | 清单没有重复/未知 entry；每个 `path,byteCount,sha256` 与实际 bytes 一致；manifest `export` 与 `exchange.json.export` canonical-equal |
| `exchange.json` | 完整 canonical `nfai.exchange` v2 IR | `validate_exchange_v2_ir` 通过，semantic hash 与 manifest export 一致 |
| `assets/<sha256>` | 每个被引用附件的原始 bytes | name 等于 64 位小写 SHA-256；bytes 长度和 hash 与所有引用它的附件 metadata 一致 |

所有 ZIP 安全限制沿用 v1：128 MiB 包/解压总量、entry 数上限、无绝对路径、`..`、反斜杠、目录、重复项或未列项。v2 preflight 还必须从**会话 `ASSET_REF` 与 Knowledge `attachments` 的并集**建立引用账本：每个 attachment 必有 `id,entry,mimeType,displayName,byteCount,sha256,classification`，`entry == assets/<sha256>`，同 id 的 metadata 必完全相同；每个 manifest asset 必且只能被账本引用。IR 中 `sourceReference`、路径/URI、picker token、私有 reference、凭据、Provider raw/runtime/diagnostic/route 字段仍在 preflight 前拒绝。

preflight 返回不可写的 `V2PreflightReceipt`：`packageHash`、`semanticHash`、origin、sensitivity、各 root owner 数、unique asset 数/字节数、以及下述 `ownerFieldHashes`。receipt 只含 ID、枚举、计数和 hash，不含正文、显示名、路径或附件 bytes。

`ownerFieldHashes` 是本地 receipt/provenance 字段而非 wire 字段：对 canonical JSON 中的每一个完整 root owner 取 SHA-256，键固定为 `project/<id>`、`conversation/<id>`、`knowledge/<id>`、`memory/<id>`、`relation/<id>`、`settings/root`；附件为 `asset/<sha256>`，值就是已核验 bytes SHA-256。它使回导能证明所有 v2 owner 字段和所有附件内容身份均未改变，而不新增或臆造 IR 字段。

## 私有暂存、归档与 SQLite 所有者

v2 使用与 v1 完全隔离的 app-data 根：`exchange-v2/archives/<packageHash>/package.nfai-exchange` 与 `exchange-v2/archives/<packageHash>/assets/<sha256>`。选择文件只读入受限内存 preflight；写入时先建不可猜测 `.prepare-*` 目录，逐个写入、`fsync`、hash 回读后原子 rename 到上述 package-hash 归档目录。归档准备在 SQLite transaction **之前**完成：写入失败时不开始 transaction，并尽力删除 prepare/孤儿目录；即使崩溃留下私有孤儿，也没有 SQLite 引用、UI 可见状态或可 resume 任务。

未来 migration 使用独立表，不能污染 v1 查询或 `list_workspaces`：

| 表 | 原子内容 |
| --- | --- |
| `exchange_v2_imports` | `workspace_id`、`package_hash`（unique）、`semantic_hash`、canonical `exchange_json`、`origin_platform`、`sensitivity`、`created_at` |
| `exchange_v2_assets` | `(workspace_id,sha256)`、`byte_count`；只索引已准备的私有 archive |
| `exchange_v2_owner_provenance` | `(workspace_id,owner_kind,owner_id)`、`origin_semantic_hash`、`field_hash`、`imported_revision`；涵盖 Project/Conversation/Knowledge/Memory/Relation/settings，附件记录为 `asset` |
| `exchange_v2_import_journal` | `package_hash`（primary key）、`workspace_id`、`semantic_hash`、receipt JSON hash、`committed_at` |
| `exchange_v2_import_receipts` | `package_hash`（primary key）、`semantic_hash`、owner-field-hashes JSON、计数、`committed_at`；不含内容或路径 |

`workspace_id` 固定为 `workspace-v2-<packageHash 前 24 位>`；其只在 v2 private owner 中可读，未来原生 Desktop owner migration 必另开合同。所有 INSERT（import、asset 索引、每个 provenance、journal、receipt）与 schema version 写入同一个 `BEGIN IMMEDIATE` transaction。事务提交前，任何 v2 read/list/re-export 都返回“不存在”；提交失败/rollback 后所有五表均为零行。文件归档可能是不可见孤儿，但不能成为可见半工作区，维护任务只能删除没有 journal 引用且完整性可验证的 archive。

## 幂等、崩溃恢复与回导

同一 `packageHash` 的再次导入先严格 preflight，再读取 journal：若 journal 的 semantic hash、receipt hash、canonical IR semantic hash、owner field hashes 和所有 archive asset bytes 均匹配，返回同一 `Committed` receipt，`replayed: true`，绝不再插入；任一不一致是本地完整性错误，拒绝而不覆盖。

v2 **没有中途 resume**。进程在 commit 前终止时，下一次只能从用户再次选择的 package 重新 preflight；无 journal 的 private staging/archive 不是任务，也不能自动提交。进程在 commit 后终止时，重开按 journal 做上述完整性 readback，返回幂等 committed receipt。禁止“根据部分表行补齐”“从 v1 workspace 猜回 v2”“以 package hash 相同忽略 semantic/field hash 不同”。

回导仅从已提交的 canonical `exchange_json` 和对应 private archive bytes 建立 v2 package，写到用户所选 `.nfai-exchange.part` 后原子 rename。回读必须完成 v2 preflight，并比较 input 与 output 的 semantic hash、每一个 `ownerFieldHash`、asset hash/byte count；package hash 可因 ZIP 元数据而不同，不能替代语义或字段保真证明。任一缺 archive、hash 不符、unknown owner 或 v2 verifier 失败时拒绝回导。

## 失败注入合同与最小验证矩阵

| 注入点 | 必须结果 | 重开/重试结果 |
| --- | --- | --- |
| ZIP/manifest/IR/asset preflight | 不创建 archive、DB 行、journal 或 receipt | 修正输入后从头 preflight |
| prepare 写入、hash 回读或 rename | 不开始 SQLite transaction；仅允许无引用的私有孤儿 | 重新选择并 preflight；孤儿不可见且可维护删除 |
| `BEGIN IMMEDIATE`、任一 import/asset/provenance/journal/receipt INSERT | transaction rollback；五表零可见行 | 无 resume，从头 preflight |
| commit 前模拟中断 | 同上，且 journal 不存在 | 无 resume，从头 preflight |
| commit 后 reopen | journal、canonical IR、field hashes、archive bytes 全部一致才读为已提交 | 返回幂等 replay；不重复写入 |
| 回导写入/rename/readback | 不发布目标文件或保留可识别 `.part` 供用户处理；不改已提交 import | 原 committed import 可继续被重新回导 |

最小自动合同必须覆盖：v1 package/export 不能接纳 v2 IR；v2 preflight 的 owner/attachment 引用账本和 hash mismatch 拒绝；每个 SQLite 写入点失败后的零行；commit 后 reopen/replay；回导 semantic 与全量 ownerFieldHash/asset hash 相等。上述自动合同覆盖了 command 所调用的 owner，但不替代真实 native picker 人工文件、跨端、Windows 或发布验收。

## 用户入口与后续实现顺序

已完成的顺序为：独立 v2 preflight/package reader → private archive writer → migration + single transaction + failure injection → private reopen/re-export readback → 最小 Desktop native picker command/UI bridge。每一步保持 v1 隔离。该 bridge 已在 Android/Desktop “设置 → 功能审阅”登记为待判断功能，并只给 Desktop 设置二级入口；下一验证只能在隔离 Desktop 环境经真实 native picker 选择 v2 fixture，读取 committed receipt 并核对 reopen/re-export readback。不可用时必须如实记录，不得用 command、SQLite 或文件注入伪造验收。

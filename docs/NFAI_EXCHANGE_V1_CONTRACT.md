# nfai.exchange.v1 跨端语义交换合同

## 定位

`nfai.exchange.v1` 是 Android、Desktop 与未来端之间的**语义迁移**包；不是 P5-D `.nfai-backup`、不是 Room 复制、不是同步协议，也不允许直接打开 Android DB。唯一权威 schema 是 [`../protocol/nfai.exchange.v1.schema.json`](../protocol/nfai.exchange.v1.schema.json)。包内所有正文、Markdown、HTML、代码、链接和资产均是不可信数据，只可按展示 IR 呈现，绝不执行。

## 包结构与 canonical hash

ZIP 根只允许 `manifest.json`、六个 `payload/*.json` 和由 SHA-256 内容寻址的 `assets/<sha256>`。Manifest 固定 `nfai.exchange.package` / packageVersion 1 / exchangeVersion 1，列出每个 entry 的路径、字节数、SHA-256 和 export 元数据。交换 IR 固定 `nfai.exchange` / version 1，含 projects、conversation message tree、knowledge、memory、relations、safe settings、revision、稳定 ID、UTC 时间、稳定顺序与 asset refs/hash。

Canonical JSON 为 UTF-8、递归字典序 key、无无意义空白、数组保持领域顺序、仅安全整数、只转义 JSON 必须字符；semantic hash 是删除 `export.semanticHash` 后该 canonical IR 的 SHA-256。未知字段、未知 format/version、重复/跨领域冲突 ID、无效父节点/叶子、部分消息非 assistant、非内容寻址资产、hash 不符一律拒绝。v2+ 必须另立 schema/upgrade；v1 consumer 不猜测未知含义。

## 边界与排除

只允许安全领域事实和 `settings.uiLanguage/theme`。严禁 Key/credential、Authorization、Provider 原始请求响应、Prompt、RunSpec、Invocation payload、runtime chunks、诊断、临时任务、route preference、URI、绝对/相对路径、数据库表名、Android storage key、签名与 Keystore。`HIGH_SENSITIVE` 是显式 classification，preflight 必须显示；P6-A 只预览/导出，**不会自动 merge 或写入非空库**。后续导入只可进入空工作区，或在单独合同下允许用户明确选择替换。

## 预检与攻击面

文件先到私有 staging；拒绝 zip-slip、symlink/不安全 entry、重复 entry、未知根、缺 Manifest、entry/hash/size 不符、超过 100,000 entries、128 MiB 解压总量、单项越界、压缩比例超过 200:1、架构/语义错误和高敏规则不满足。资产必须同时通过 Manifest 与 asset ref 双 hash。没有自动解压到业务目录、没有自动执行、没有网络 egress。

## 实现与验收

Android 唯一入口是 `NfaiExchangeV1Gateway`：调用者必须提交显式 `NfaiExchangeExportSelection`（含精确 attachment ID）和已构造安全快照；gateway 只导出，或从 staging 包返回 `NfaiExchangePreflight`，不接 Room 写入。选择的 attachment ID 必须与所有消息 `ASSET_REF` 双向一致，且 `assets/<sha256>` 集合必须与引用集合完全相同。`ExportWorkspaceExchangeUseCase` 的全对象 scope/关系/附件 mapper 规则见 [`P6_WORKSPACE_EXCHANGE_MAPPER_CONTRACT.md`](P6_WORKSPACE_EXCHANGE_MAPPER_CONTRACT.md)。Desktop 使用临时内存 IR 与同一 fixture，不读 Android DB。`protocol/scripts/run-golden.mjs` 覆盖 deterministic desktop byte roundtrip、semantic IR、hash、资产和恶意包；Android `P6AExchangeContractsTest` 覆盖同一 golden 的显式选择→导出→preflight。fixture 与生成物路径见 `protocol/fixtures/` 和 `protocol/artifacts/`。

# P6 Android v2 OpenDocument 受控恢复合同

## 用户路径与唯一所有者

唯一普通入口是 Android `设置 → 数据与导入 → 完整工作区交换（v2）→ 选择 v2 交换包并恢复`。它不出现在聊天主页、Composer、会话详情或工作页。首次启动不再隐式写入“新对话”：用户仍可从抽屉显式创建对话，但在此之前空本机可以到达设置恢复入口。`ActivityResultContracts.OpenDocument` 只返回用户明确选择的单个 transient URI；`AndroidWorkspaceExchangeV2OpenDocumentRestorePort` 是唯一可消费该 URI 的 adapter。

adapter 仅以流式方式读取不超过 128 MiB 的 bytes，不扫描目录、不保留 URI/path/display name/MIME、不解析 ZIP/manifest/IR，也不写 Room 或私有附件。它只将有限 package bytes 和由精确 package SHA-256 派生的 opaque intent ID 传给 `WorkspaceExchangeV2AtomicRestoreOwner`。owner 内部仍是唯一 strict reader 与原子提交所有者。

## 状态与恢复

ViewModel/UI state 只能包含：忙碌状态、脱敏 outcome、semantic hash 的截断前缀、匿名对象数和附件数。不得包含 URI、path、文件名、MIME、package/IR/attachment bytes、原始异常或原始错误消息。

| owner 结果 | 用户显示 | 写入边界 |
| --- | --- | --- |
| `Restored` | 严格恢复及匿名 counts | 仅在 strict reader + 空本机 + atomic typed readback 都通过后 |
| `Replayed` | 已验证相同回执，未重复写入 | 只读既有 content-free receipt |
| `PACKAGE_REJECTED` | 包未通过完整校验 | strict reader 前后均不覆盖本机 |
| `LOCAL_TRUTH_PRESENT` | 本机已有数据或待恢复记录 | 不 merge/upsert/替换/删除 |
| `RECOVERY_REQUIRED` | 无法安全判定，停止 | 保留未知 journal，等待人工处置 |
| `FailedRecoverably` | 可重新选择同一包重试 | 只允许同一 package-derived intent 安全回收未发布 candidate |

本增量不是备份、云同步、目录导入、多文件导入或对已有本机数据的恢复。功能审阅已同步 Android/Desktop，状态均为“待您判断是否保留”，建议只保留设置二级入口。

## 自动与真实验收

- 自动合同：UI 不解析 package；adapter 对单 URI 流限长；只调用 atomic owner；UI state/失败显示不含内容或 locator；strict reader、空本机、replay、冲突和 journal 合同继续复用既有 owner/store tests。
- 真实验收：已在新建项目专属空 AVD `emulator-5588`，经实际 DocumentsUI Download 选择 2,340 B non-sensitive strict v2 fixture；App 显示 content-free success，Room typed owners/asset/receipt/provenance/settings 的只读 count 与 package 一致。篡改包、nonempty-local 拒绝、process interruption 后同包重选仍需独立真实证据。不得运行 `connected*AndroidTest`，不得操作 OPPO。

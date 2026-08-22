# P6 工作区交换 v2 Android 严格读取基础合同

## 目标

为未来 Android v2 导入/恢复建立唯一的、纯内存 `NfaiExchangeV2PackageReader`。它只接收调用方已经限定的 package bytes，严格验证 ZIP、manifest、IR、附件账本与 owner-field hash，返回内容无关 receipt、canonical IR 文本和仅本次调用可用的附件字节。

## 本增量边界

- 不注册 Settings、SAF OpenDocument、ViewModel、Room/文件写入、私有 staging、恢复、合并、覆盖、删除或后台任务。
- 不扫描目录、保留 URI/path/显示名、读取 Provider/Key/网络或把包正文写进 receipt、日志或 UI。
- 任一 ZIP entry、manifest、semantic hash、owner-field hash、附件 metadata/bytes 或未知字段不符时整体拒绝；不忽略坏附件、不降级为 v1、不猜测 owner 默认值。

## 唯一所有者与消费者

`NfaiExchangeV2PackageReader` 是 Android v2 package bytes 的唯一 preflight owner。现有 `NfaiExchangeV2PackageWriter` 在发布前回读自己的 bytes 时必须复用它；后续 SAF/恢复事务也只能使用其成功结果，不能自行解析 ZIP 或 JSON。

## 最小验证与停止

定向合同覆盖 writer 输出可被 reader 严格回读，以及 manifest/附件篡改整体拒绝。此阶段不构成 Android 导入、迁移、真实 DocumentsUI、模拟器、OPPO、Desktop 原生对象恢复或 P6/P0–P11 完成。

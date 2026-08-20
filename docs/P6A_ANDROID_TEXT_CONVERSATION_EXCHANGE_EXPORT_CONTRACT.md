# P6-A Android 文本会话跨端交换导出合同

## 目标与停止条件

- 目标：将一个符合条件的 Android 已保存普通文本会话，经系统 SAF 输出为 `nfai.exchange.v1`，供既有 Desktop workspace exchange owner 严格预检/导入；不改变 Android 的会话、Room 或 Desktop workspace。
- 停止：本增量只完成 Android 导出端。Android→Desktop 导入→再导出真实文件回读、项目/Knowledge/Memory/关系/附件映射、Android 导入、紧凑/展开、异常恢复与 Windows 发布仍是 P6 的独立退出门。

## 唯一所有者与入口

- 语义快照：`ExportConversationExchangeUseCase`。它只读取 `ConversationRepository.findById` 的既有 `ConversationSnapshot`，不访问 DAO、Provider、Key、HTTP、Usage 或 renderer。
- 受控输出：`AndroidConversationExchangeExportPort`。它以私有临时文件调用 `NfaiExchangeV1Gateway.export`，经用户选择的 SAF `Uri` 写出、回读 SHA-256，才报告成功。
- 用户入口：Android 设置 → 对话 → “导出当前文本会话到 Desktop”。Desktop 不新增行为，继续使用现有工作区交换导入。

## 范围与失败关闭

- 仅允许当前活动的 `CHAT` 会话，必须无 Project、已存在 current leaf、无草稿，且每个消息块都是 `TEXT`。附件、工具结果、项目归属、WORK/TEMP、已归档/删除或空会话全部拒绝，不构造部分包。
- 保留稳定会话/消息 ID、父子关系、同级 ordinal、角色、delivery、revision、创建/更新时间与 text block ordinal；不伪造 Attachment、Provider Attempt、Invocation、Usage、路由或本机路径。
- 文本中的既有高敏特征只提升交换包的 `sensitivity=HIGH_SENSITIVE`；不写 Key/URI/path，也不把用户主动 SAF 导出说成云同步或外发。
- `NfaiExchangeV1Gateway.withComputedSemanticHash` 是唯一 canonical semantic hash 计算路径；任何 gateway export、preflight、SAF 写入或 SAF 回读失败都是失败，不留下成功状态。

## 验证

- 领域合同：普通文本会话可封包并通过 Android 严格 preflight；草稿、项目、WORK/TEMP、附件/工具结果在写文件前拒绝。
- 入口合同：设置页只在现有“对话”二级页加入入口；Android/Desktop 均在设置 → 功能审阅登记，不新增聊天主页或 Composer 常驻按键。
- 未验证：真实 Android DocumentsUI 输出、Desktop 实际导入/再导出、OPPO、真实用户内容和 Windows 正式发布。

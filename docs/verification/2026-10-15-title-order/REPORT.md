# 纯标题变更保留对话排序

- 用户要求：修改标题与对话内容更新分开；Android／Mac 手动改名和自动标题写回均不把旧对话顶到最近列表顶部。
- 根因：Android `ConversationManagementDomain.apply`、默认／Room `completeAutomaticTitle`，Mac `apply_domain_mutation` 的 conversation/update 与 `complete_desktop_conversation_title` 都把标题写回时刻覆盖进排序字段 `updatedAt`。双端已在本地生产保存入口复现 timestamp 变化的红灯。
- 修复：仅纯标题写入保留原活动时间；`titleRevision`／对象 `revision`、索引和同步标识仍更新。内容追加仍更新活动时间。现有 titleRevision 处理等活动时间的标题同步，不新增 schema、不重写旧数据、不调整置顶或未读分组。
- 验证：Android 生产 Room／管理用例覆盖手动与自动标题、三条会话排序、关闭重开、等时间戳同步合并和新增消息提升；相关管理／自动标题／同步回归 38 项通过，Debug 构建通过。Mac 原生 SQLite／领域写入覆盖手动与自动标题、版本及语义 hash、关闭重开和新增消息；完整 Rust 回归 449 项通过、36 项忽略，结果见 verification.json。前端实际 activeConversations 列表投影回归 7 项通过。
- 边界：全部使用独立临时数据库与公开合成文本；0 Provider 请求、0 服务费。未运行 connected Android 测试，未安装、未触碰现有应用数据、未 commit／push；原生 UI 手势未实测。其他既有 WIP 和历史真实验收缺口保持。

源码绑定见 [source-hashes.json](source-hashes.json)，结构化验证见 [verification.json](verification.json)。合同治理与一致性门均通过（114 合同／40 领域）；不等于全产品语义或 UI 验收。

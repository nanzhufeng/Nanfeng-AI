# 南枫 AI P4-F 本地 Knowledge 去重候选合同

日期：2026-08-13  
状态：P4 的第六个本地增量；不变更 Schema 11。仅从本机已保存 Knowledge 生成确定性的只读重复候选；不读取 Key、不构造 Prompt/RunSpec、不发 HTTP、不产生费用或图片外发。

## 边界与所有权

```text
Knowledge 详情中的明确用户操作
→ ManageKnowledgeUseCase.duplicateCandidates
→ KnowledgeDeduplicationDomain
→ KnowledgeManagementRepository / Room 的瞬时只读快照
→ 仅内存中的候选列表
```

- 本阶段只提供去重候选，不建立 Knowledge 关系，不创建合并/删除/更新/revision，不产生导入记录，也不改动 Project、Conversation、Memory、Context、Export、Invocation、同步或诊断。
- 用户必须在一条活动 Knowledge 的详情中主动请求；候选默认不加载、不持久化，关闭详情、切换条目或进程重建即丢弃。
- 候选只比较同一 scope：GLOBAL 只对 GLOBAL，PROJECT 只对相同 Project；归档、回收站、其他 Project 均排除。没有跨范围读取或关系暗示。

## 确定性规则与隐私

- 候选的唯一理由是：规范化标题相同，或规范化标题与正文得到的 SHA-256 内容哈希相同。规范化使用 trim、`Locale.ROOT` 小写和连续空白折叠；不使用 embedding、向量库、模糊相似度、模型、网页或附件内容。
- 排序固定为精确内容优先、再规范化标题、再 `updatedAt DESC`、Knowledge ID 升序，最多 20 条。只显示已存在的标题、修订号和理由，不显示正文、附件、来源 URI、hash 或敏感检测细节。
- 锚点不存在或非活动时明确拒绝。锚点或任何已匹配候选命中 `MemoryDomain.sensitiveRejection` 时，整体拒绝且不返回部分列表；不写入、导出、记账或记录被拒正文。

## 后续明确授权前禁止事项

- P4-F 不自动合并、不标记已处理、不建立关系，也没有“确认”入口。任何关系、用户确认归并、导入准备或文件/网页 Adapter 必须另立合同、独立的数据迁移与测试，不得由候选列表暗中触发。
- 本地候选验证不替代真实 Provider、费用、OPPO、图标或发布验收。

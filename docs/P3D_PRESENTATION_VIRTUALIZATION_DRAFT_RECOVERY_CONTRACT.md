# 南枫 AI P3-D 安全展示、长会话虚拟化与草稿恢复合同

日期：2026-08-13  
状态：P3 的下一本地增量；不含真实 Provider、Key、RunSpec、HTTP、图片外发或 P4 能力

## 目标、边界与唯一所有权

```text
已持久化 ConversationSnapshot / ContentBlock
→ MessagePresentationRenderer（版本化安全展示 IR、局部缓存）
→ Compose LazyColumn（只渲染 IR、稳定 key）

Conversation Draft Domain / Repository（草稿及附件引用、提交原子清理、重建恢复）
→ ViewModel（调用用例、持有瞬时输入状态）

AiRuntimeEvent → ConversationRuntimeStateMachine（运行和错误终态）
→ UI（安全错误文案与允许动作）
```

| 概念 | 唯一所有者 | 禁止事项 |
|---|---|---|
| 消息树、当前路径、ContentBlock 真值 | P3-A Conversation Domain / Room | Renderer、UI 或缓存回写消息正文、分支或 Provider 内容 |
| 运行顺序、部分输出、失败/取消终态 | P3-B Runtime State Machine | UI 自判成功/失败、把展示状态伪造成运行事实 |
| continue/retry/change-model 谱系和前置条件 | P3-C Action Orchestrator | Renderer 或草稿流程重写旧 assistant / Invocation |
| Markdown/代码展示 IR | `MessagePresentationRenderer` | UI 直接解析 Provider chunk，持久化 IR/HTML/解析缓存 |
| 当前会话草稿与附件引用 | `ConversationDraftRepository` / `ConversationDraftDomain` | ViewModel 自造第二份持久化真值，发送前清草稿 |
| 可见范围和滚动状态 | Compose `LazyColumn` | `Column` 全量实例化长会话，使用不稳定 index key |

所有正文均是不可信展示输入。生产 OpenRouter egress 固定 `Disabled`；本增量绝不读取或写入 Key、不构造 Authorization/RunSpec、不发 HTTP、不产生费用，也不新增图片读取或外发。

> 当前 Markdown 局部容错以 [P6-F 展示合同](P6F_CONVERSATION_TRANSCRIPT_PRESENTATION_AND_MESSAGE_ACTIONS_CONTRACT.md) 为准；下列整篇降级描述为历史阶段规则。

## 展示 IR 与安全策略

- `MessagePresentationRenderer` 只接收已持久化 `ContentBlock`，生成 `parserVersion=1` 的 `PresentedMessage` / `PresentationBlock`；稳定身份为 `(messageId, contentBlockPosition, parserVersion)`，不是文字内容或列表下标。
- 最小支持：段落、标题、无序/有序列表、引用、行内代码、围栏代码块与受限语言标签。链接只作为可见文本与 URL 的 IR，**没有点击、自动打开、URI handler、HTML、JavaScript 或工具调用**。
- HTML、未知、畸形或未闭合围栏一律以原文 `PlainText` 降级；不得丢字、执行或“修复”成另一段业务正文。表格和公式在 P3-D 作为纯文本降级类型保留，不引入引擎。
- 正文与代码 IR 均由 `SelectionContainer` 提供平台可用的选择/复制能力；链接不是可执行控件。
- Renderer 的内存缓存仅保存输入指纹和 IR。只有同一稳定 block 的正文/`schemaVersion` 或 parser version 改变时才失效；消息消失时移除缓存。缓存不进 Room、Ledger、日志、导出或消息真值。

## 长会话与增量规则

- 会话详情必须使用 `LazyColumn`，每条消息以 `MessageNodeId` 作为 `key`，并声明有限的 `contentType`；分支切换读取当前根→叶路径，其他分支不混入。
- 流式增量由 P3-B 先原子写入 MessageNode，再由 Renderer 仅重投影该 message/block；不得每个 delta 重新解析或重建整段会话。
- 提供数量、内容和 ID 固定的本地长会话 fixture。合同测试验证路径、切换分支、增量变更保持其它 message/block identity，并以 Lazy list 的 key/contentType 合同验证虚拟化结构；不伪造 TTFT、帧率、内存或真实设备性能数值。
- `LazyListState` 由可保存状态持有；切换/重建不改变当前分支事实。真实滚动帧率和内存基线仍是后续真机/真实使用验收债务。

## 草稿、发送与错误恢复

- 草稿文本在保存时去首尾空白，最大长度固定为 12,000 字符；空文本加空附件是合法“清空草稿”，但不可发送。附件引用按稳定 `AttachmentId` 去重，文本与附件分别判定，不能把附件误当文字或清错。
- `ConversationDraftRepository` 是唯一草稿持久化入口。相同规范化草稿幂等回读且不改更新时间；返回、Activity/进程重建和冷启动均从 Room 读取。
- 发送使用单个 Room 事务：先验证保存的草稿与请求快照一致，再创建 user MessageNode，成功后将草稿置空；任一步失败则消息和草稿都不改变。失败或取消绝不清草稿。
- P3-D 不新增附件选择、系统图片分享、拍照或真实图片发送；已有附件引用只被保留和安全显示。
- `ConversationRuntimeState` 的 `FAILED`/`CANCELLED` 是错误终态真值。UI 只映射受限安全错误码为中文说明和允许动作：有部分输出的本地 `FAILED`/`CANCELLED` 可继续；终态 assistant 可重试/换本地 fixture；鉴权、余额、取消、无部分输出均不显示“继续”，且不得盲目重试。

## 数据、迁移与验证

- 复用 Schema 6 的 `message_content_blocks`、`conversation_drafts`、草稿附件、runtime state 和 lineage；不升 Schema，不清库。
- 定向测试覆盖 parser/IR、畸形降级、稳定 identity、缓存失效、HTML/链接不可执行、长 fixture/路径/分支隔离/Lazy key 合同、草稿空白/边界/幂等/附件、提交成功清理与失败保留、Room/重建和安全错误动作。
- P3-A/B/C 回归、Room Schema 迁移数据保留、全量单测、Lint、正式签名 Debug/Release 和 API 35 本地生命周期另行分层报告。真实服务、Token/费用、真机/OPPO、图标视觉和发布不因本地结果而通过。

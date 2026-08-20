# 南枫 AI P3-G 对话附件本地引用与生命周期合同

日期：2026-08-13  
状态：P3 的第七个本地增量；无 Provider、Key、Authorization、RunSpec、HTTP、图片外发或费用

## 目标、所有权与固定能力

```text
Android Photo Picker
→ 私有复制 / PrivateAttachmentRepository（资产、哈希、MIME、大小、私有路径）
→ Conversation Draft（仅 AttachmentId + 发送范围意图）
→ user Message ContentBlock.Attachment（仅安全引用/元数据）
→ Attachment Preview Projection（受控私有读取与有界缩略图）
→ Compose
```

| 概念 | 唯一所有者 | 公开入口 | 禁止的平行规则 |
|---|---|---|---|
| 附件资产、哈希、MIME、大小与私有路径 | Attachment Domain / `PrivateAttachmentRepository` | `PrivateAttachmentStore` → Repository | Conversation/UI/Export/Ledger 保存路径、URI grant、EXIF 或二进制 |
| 当前会话草稿附件引用 | Conversation Draft Domain | `AddConversationImageAttachmentUseCase` / `RemoveConversationAttachmentUseCase` | Picker/UI/DAO 直接写草稿或把 Capture 草稿当 Conversation 草稿 |
| 已发送附件块 | Conversation Domain | `SubmitConversationDraftUseCase` | 外发层、fixture 或展示层改写/补发附件 |
| 缩略图 | `ConversationAttachmentPreviewProjection` | 私有 Repository → 有界解码 | Compose 从 URI/path/full bitmap 读取或持久化预览 |
| 外发范围 | `ConversationAttachmentEgressScope.LOCAL_ONLY_NO_EGRESS` | 固定能力门 | “加入对话”被解释为图片外发授权 |

## 本地语义与边界

- 复用单图 Android Photo Picker；仅 JPEG、PNG、WebP。取消、返回、重复 Intent/结果确定不写入或幂等回读，Activity/进程重建从 Room 回读文字和附件。
- 每会话最多 4 张、单张最大 20 MB、总量最大 40 MB、原图最大 40,000,000 像素；无效 MIME、空输入、超量和解码炸弹均拒绝且保留旧草稿。缩略图最大边 512、最多 2 MB，绝不把全图送入 Compose。
- Capture 图片草稿与 Conversation 草稿是两条独立业务状态；可复用同一 `AttachmentId`，但一方的取消/更新/移除不清理另一方。
- Conversation Draft 与 `ContentBlock.Attachment` 只保存稳定 ID、MIME、显示名、大小、SHA-256；无 storage key、绝对路径、content URI、URI grant、EXIF、字节或缩略图。Export 只输出同一安全元数据，搜索不索引图片内容/路径，Ledger/lineage/runtime 不含附件字段。
- 发送以一个 Room 事务写有序 Text/Attachment user 内容块并清同一 Conversation Draft；失败或冲突保留草稿。草稿移除只移除引用：P3-G 不删除/GC 私有二进制。
- P3-F 保持：含附件 user 消息没有编辑分支入口。P3-C continue/retry/change-model 本地 fixture 不隐式读取或外发附件；展示仅说明“本地附件引用”。

## Schema 7→8 唯一迁移理由

Schema 7 的对话草稿与消息块曾复用 `storageKey` 列。P3-G 新增 `private_attachment_assets`，迁移先从 P2 Capture/Knowledge 与既有 P3 引用回填资产目录，再把 Conversation 行的该列置为稳定 `AttachmentId`。不删除或重建表，不清库；P2/P3 行均保留。私有路径只在资产目录中存在。

## UI、验证与未覆盖门

- 本地对话输入区提供“添加图片（本地）”、紧凑缩略图、移除及限制/local-only 提示；消息显示缩略图与 MIME/大小，不声称 AI 已读图。所有自有浅色 Dialog/Popup 保持 `#FFFFFFFF`。
- 合同测试覆盖选择/取消/重复、MIME/像素/数量/体积、草稿原子保存/移除/发送/重建、消息顺序、Schema 7→8 路径剥离、P3-F 附件编辑隔离和敏感字段排除；全量 P1/P2/P3-A–F 回归必须通过。
- API 35 仅验证本地 Picker、草稿重建、发送、缩略图、分支切换/冷启动和附件消息无编辑入口。真实服务、图片外发、Token/费用、OPPO、图标视觉与发布仍是独立未验证门。

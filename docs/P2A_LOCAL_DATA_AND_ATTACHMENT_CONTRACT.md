# 南枫 AI P2-A 本地数据与附件合同

> 日期：2026-08-12  
> 状态：已完成（本地工程验证）  
> 上位依据：`MASTER_DEVELOPMENT_BLUEPRINT.md` 的 P2、`domain-rules.md` 1/2/7/12/13 节

## 唯一目标

建立 Android 本地真值底座：Capture Draft 与 Knowledge Item 通过 Room 持久化，图片附件经 App 私有目录的可验证副本保存；同一事实可回读为领域对象，并可形成不含绝对路径、密钥或正文诊断的版本化导出快照。

## 本轮概念与唯一所有者

| 概念 | 唯一所有者 | 公开入口 | 真值 |
|---|---|---|---|
| Capture Draft 持久化 | Capture Application | `CaptureDraftRepository` | Room draft/evidence/attachment 关系 |
| 私有附件副本 | Attachment Storage | `PrivateAttachmentStore.import` | `filesDir/attachments/v1` 中的内容寻址副本与附件元数据 |
| Knowledge 持久化 | Knowledge Application | `SaveKnowledgeItemUseCase` → `KnowledgeRepository` | Room knowledge/evidence/attachment 关系 |
| 导出快照 | Portability Domain | `ExportKnowledgeSnapshotUseCase` | 版本化领域快照，不含 Android 路径 |

## 入口矩阵

| 入口/消费者 | P2-A 状态 | 统一入口 | 最小验证 |
|---|---|---|---|
| 手工文本草稿 | 受影响 | `CaptureDraftRepository.save` | 重启后来源与正文回读一致 |
| Android 文本分享 | 合同已覆盖，系统 Intent 未实现 | 与手工文本相同 Repository | 来源包名不丢失 |
| 图片二进制输入 | 受影响，仅内部 `InputStream` 适配 | `PrivateAttachmentStore.import` | 私有副本、大小、SHA-256 与 MIME 回读一致 |
| 相册选择器、系统图片分享、拍照 | 不存在 | 后续输入 Adapter | 不申请权限、不读取真实 URI |
| Candidate 确认保存 | 受影响 | `SaveKnowledgeItemUseCase` | 确认、溯源、来源与附件一起持久化 |
| 列表/详情/导出文件 UI | 不存在 | 后续读取模型/文件写入器 | 先验证领域快照，不虚构 UI 成功 |
| OpenRouter、Key、同步、Hub、Agent | 不受影响 | 不创建入口 | 不联网、不读取凭据 |

## 数据与附件合同

- Room 初始 Schema 为 `1`；后续升级必须提供显式 Migration，禁止以清库替代迁移。
- ID 为领域稳定 UUID，不使用数据库自增 ID 作为协议身份。
- `null` 的正文表示图片草稿；空白正文非法。附件显示名允许 `null`；MIME、私有相对存储键、字节数与 SHA-256 必须存在。
- 外部 URI、调用方路径与绝对文件路径只属于导入瞬间的输入证据；不进入正式附件引用、Room 真值或导出快照。
- 导入使用“写入临时文件 → 计算 SHA-256/大小 → 原子移动至私有目录 → 返回 Ready 引用”的顺序。失败时不创建数据库附件记录；本次创建的临时文件可由操作自身清理。
- P2-A 不自动删除任何已完成附件，不实现垃圾回收、回收站或覆盖更新；这些高风险生命周期动作须在后续单独设计。
- 导出快照包含协议版本、稳定 ID、领域事实、来源、附件的逻辑标识/哈希/大小/MIME 和调用溯源；不包含 API Key、绝对路径、原始 Provider 请求/响应或未脱敏诊断。

## 允许范围

- Room 2.8.4、KSP、数据库实体/DAO/Repository、初始 Schema、Room 测试数据库。
- App 私有附件存储实现，以及仅用临时测试目录与内存 `InputStream` 的定向测试。
- 领域附件元数据、Capture Draft Repository、版本化导出快照和必要结构化错误。

## 禁止项

- 不调用 OpenRouter，不读取、保存或测试 API Key，不发送文本/图片。
- 不接系统相册、系统分享 Intent、拍照、文件选择器、权限申请、云同步或真实设备文件。
- 不清库、不迁移已有用户数据库、不删除既有附件、不导出真实用户数据、不创建 Git 提交或发布。

## 最小验收与停止条件

1. 文本/分享/图片草稿均可由同一 Room Repository 保存并回读，来源与附件顺序不丢失。
2. 二进制输入只在成功完成私有副本与哈希后产生 Ready 附件；读取副本与哈希一致。
3. 已确认 Candidate 保存为 Knowledge 后，来源、附件与 Invocation 溯源一起回读。
4. 导出快照与持久化事实一致，且不出现绝对路径、Key 或网络正文。
5. Room 定向测试、现有领域测试、Lint 与 Debug 构建通过。

达到以上条件即结束 P2-A；之后再评审系统图片入口和最小可见捕获闭环，不自动进入真实 Provider。

## 完成证据

- Room 2.8.4 + KSP 初始 Schema 已生成至 `app/schemas/com.nanzhufeng.ai.data.local.NanfengAiDatabase/1.json`。
- `testDebugUnitTest`：9 项测试、0 失败、0 错误；覆盖私有副本、SHA-256 校验、Room 草稿/知识/附件/溯源回读、导出快照与外部 URI 清洗。
- `lintDebug`、`assembleDebug` 通过。
- KSP 在 AGP Built-in Kotlin 下使用 `android.disallowKotlinSourceSets=false` 兼容开关；这是当前 KSP 生成源码注册的项目级临时兼容项，未来 AGP/KSP 升级时必须重新核验。
- 未运行相册、系统分享、拍照、模拟器、真机、真实文件、真实 Provider 或真实导出文件路径；本地测试目录和内存输入流不构成这些验证证据。

# 南枫 AI P5-C 隐私、数据管理与安全诊断合同

日期：2026-08-13  
状态：P5 的第三个独立增量；不是 P5 或项目终点。Schema 保持 17。

## 目标、事实与不做的事

本阶段提供纯白的“隐私与数据”控制面，且只陈述可由当前实现核验的事实：业务内容优先保存在本机；`OpenRouterEgressPolicy.Disabled` 阻止当前 Provider 推理外发；网页文本 Adapter 仅在用户手动输入公共 HTTPS URL 并勾选确认后才可能联网；真实 Provider 是后置能力。本阶段没有 telemetry、后台诊断、崩溃 SaaS、Firebase、Sentry、analytics、备份、同步或 OPPO 操作。

`INTERNET` 仅为已实现的公开 Models 目录核验和用户确认的 Web Adapter 保留；没有宽泛存储、通知、后台位置、相机或麦克风权限。文件导入/诊断目标均走系统 SAF，Photo Picker 不转换为传统存储权限。

## 数据清单与唯一所有者

数据清单只能给出安全的数量、总字节和状态汇总，不能读取正文以计算摘要。它包含：

- Conversation、message、draft、Invocation/local fixture；Projects；Memory；Knowledge、revision、relation；Markdown/JSON/PDF/Web Adapter 任务与私有资产；Offline Eval；Capture/private attachments。
- 本机导出与 model registry 快照；模型设置、Provider metadata/credential **存在性引用**和 route preference。凭据不解密、不读取、不导出。
- 安装签名、Android Keystore 安装身份不在可删除集合。

`PrivacyDataManager` 是 P5-C 的唯一汇总、删除预览、强确认、文件协调和诊断数据源。UI 不直连 Room/文件，也不得从任何正文/Prompt/URL/URI/Key 组装诊断。

## 删除范围、确认与恢复

| 范围 | 预览 | 执行规则 |
| --- | --- | --- |
| 临时/失败任务资产 | 仅列出 FAILED/CANCELLED adapter task 数、可安全移除文件数/字节 | 只处理失败/取消 task 及同任务私有根，活跃/完成/正式 Knowledge 引用不删；孤儿与 `.part` 仅在受控根、canonical path、无 symlink、无活动引用时删除 |
| Eval 运行 | Eval run、结果、断言、人工评分与 Eval report 数/字节 | 单一 Room transaction 删除 Eval 事实；对应 app-private report 采用暂存后删除；失败须可见且可重试 |
| Knowledge/Memory 回收站 | 软删除 Knowledge/Memory 数 | 物理回收已 `DELETED` 的业务记录及其 revision/intents/relations；不是将其他 ACTIVE/ARCHIVED 项改为删除 |
| 全部本地业务数据 | 每类安全汇总、将删除的偏好/私有目录集合 | 必须在第二个纯白 Dialog 输入 `删除全部本地业务数据`；不自动执行、不清安装/签名身份。业务 Room、受控业务目录、模型设置/credential 引用和 route preference 才在范围内，Android Keystore alias 不读取也不删除 |

每个执行请求有随机 operation ID、固定预览指纹和作用域。执行前重新生成预览并比对；不符即拒绝，不静默扩大范围。数据库删除在一个 transaction 内；文件先移入 app-private 同卷 pending 根，transaction 成功后才删除。移动/删除失败返回 PARTIAL，保留 operation ID、失败计数和可重试事实；不能把部分完成伪装为成功。Activity/进程重建只恢复结果，不会重新开始删除。

## 安全诊断导出

用户先选择“安全诊断”范围，再通过 SAF `CreateDocument` 选择位置。输出为 `nanfeng-ai.security-diagnostic` v1 JSON，先在 app-private staging 以 `.part → fsync → atomic move` 完整生成，再复制到用户 URI 并从同一 URI 回读 SHA-256；只有读取字节与 Manifest hash 相同才成功。

诊断字段采用严格 allowlist：app/version/build、Room schema/migrations、编译能力开关、任务/状态计数、已白名单错误码分布、API/窗口安全摘要和安全导出自身的版本/hash。不得包含正文、标题、Prompt、Key/token/Authorization、URL（含 query/path）、URI、附件/HTML/PDF/图片字节、外部 IP、用户输入、数据库文件、文件系统路径或 Provider 请求/响应。任何疑似高敏模式、URL/URI、路径分隔符或未注册字段使整个导出拒绝；它不后台生成、上传或接入崩溃服务。

## 自动与真实验收

自动合同覆盖安全汇总、删除预览/短语/范围和 stale-preview 拒绝、Room transaction/文件 partial-retry、回收站与 Eval 边界、allowlist/红队高敏拒绝、staging+SAF 回读 hash、无正文/URL/路径/Key、Manifest 最小权限、孤儿根/path traversal/symlink/活动引用边界，以及重建不自动执行。

真实验收只在 `emulator-5554`：查看清单，使用真实系统文档创建器导出并回读 hash；创建隔离非敏感 fixture 后只删除该小范围并 force-stop/冷启动确认没有重复执行。任务删除清单必须按 Adapter、任务安全 ID 摘要、终态、私有文件计数/字节和失败证据后果逐项显示，默认零选择；只有 `FAILED`/`CANCELLED`、无 ACTIVE Knowledge/导出引用且私有目录无 symlink/越根风险的任务可以进入二次预览。文件先进入 app-private quarantine，再在 Room transaction 中删除所选行，最后清 quarantine；清理失败只保留 FAILED 剩余项的显式重试，重建不自动继续。最终恢复 portrait/1.0x/Capture。不得清既有长期验收数据，不得 OPPO。

P5-C 完成后下一候选为 P5-D 备份/恢复合同；真实 Provider、Provider egress、备份、同步、发布和 P6+ 仍保持后置。

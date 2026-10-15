# P6-E 双端临时聊天完整生命周期合同

<!-- contract-governance:start -->
> **合同口径：现行领域合同。** 领域入口：[ 本地临时聊天 ](P6E_TEMPORARY_CONVERSATION_DUAL_PLATFORM_CONTRACT.md)；[全项目口径与维护索引](contracts/CONTRACT_INDEX.md)。本文的独立owner／格式边界不因归类改变；阶段状态、Schema与验收记录只适用于注明的范围，不能覆盖其他领域或推断整机当前能力。
> **适用范围：** 仅本地临时消息与隔离24小时恢复记录；不接Provider/普通同步
<!-- contract-governance:end -->

状态：**正式合同，已授权实施**；替代并废止原 `P6E_TEMPORARY_CONVERSATION_DUAL_PLATFORM_CONTRACT_DRAFT.md`。TEMP的数据与生命周期由本文持有；图标、输入框、侧栏、颜色和字号沿[C16主题合同](C16_THEME_FONT_OVERLAY_PARITY_CONTRACT.md)与当前平台会话合同，不将早期橙色写成不可配置规则。

## 1. 唯一目标、停止条件与禁止项

本合同只交付 Desktop 与 Android 的离线临时聊天完整生命周期：从同义 Ghost 入口直接 NORMAL↔TEMP、在临时会话内编辑本地草稿/发送本地纯文本消息、使用 P6-D2 已完成的图片或文件附件、窗口或进程重建恢复、24 小时到期清理，以及普通功能零泄漏。

- 停止条件：双端均通过领域/入口契约、构建；Desktop 真实 `.app` 与 Android 正式签名 Debug/Release + 当次隔离模拟器 均完成 NORMAL↔TEMP 直切 → 草稿/本地消息/真实图片或文件附件 → NORMAL 不删除 → 重启读回 → `23h59m/24h` owner 清理的真实路径。
- 禁止 Provider、Key、HTTP、Prompt/RunSpec、真实用户数据、OPPO、图标、同步实现、真实缓存、导入导出扩展、普通 Conversation/附件表迁移或任何普通历史兼容捷径。
- P6-E **复用 P6-D2 已完成的真实附件能力**：Desktop native picker 与 Android Photo Picker/DocumentsUI 继续可用，复用既有 allowlist、MIME+magic、大小限制、app-private content-addressed copy、SHA-256 去重和安全 metadata owner；禁止相机、插件、语音与任何外发。新建附件 Adapter 不在本阶段范围内。

## 2. 领域、所有权与数据边界

- 用显式、版本化 `ConversationKind.NORMAL | TEMPORARY` 区分，禁止以标题、空 project 或 UI route 推断。NORMAL 继续由既有 `ConversationRepository` / Desktop workspace owner 管理；TEMPORARY 只能由下述独立 owner 管理。
- TEMPORARY 不进入普通历史、工作区/项目归属、置顶、归档、搜索、Knowledge、Memory、导入导出、同步、历史缓存或训练声明；不得创建普通 Conversation 假记录。
- 仅可使用 app-private、隔离的 ephemeral recovery record 支持窗口/Activity 重建和崩溃恢复；Ghost 返回 NORMAL 只是视图切换，不删除 recovery，24 小时内再次进入恢复同一 session。不得存 URI、path、Key。
- 当前只允许本地 fake/offline 验证。未来若在线，必须清楚说明内容仍发送给所选 Provider，Provider 的保留/训练政策由其决定，南枫 AI 不作“不会训练”保证。
- `TemporaryConversationOwner` 是唯一公开写入链：`enterOrRestore → updateDraft/appendOfflineMessage → readRecovery → leaveToNormal → pruneExpired`。Android 为 `TemporaryConversationDomain → TemporaryConversationRecoveryStore`；Desktop 为 `DesktopTemporaryConversationOwner → app-private SQLite recovery table`。`leaveToNormal` 只切换可见模式，不删除 recovery；仅到期或安全异常清理可删除。两者都不得复用 `ConversationRepository`、`ConversationManagementDomain`、Room `conversations`、Rust workspace exchange 或任何普通 Conversation ID。
- recovery record 只能保存 schema version、temporary ID、创建/更新时间、纯文本草稿、纯文本本地消息、可空 model override 的安全标识和 attachment IDs 的有界稳定引用。每个引用必须在既有 attachment owner 中显式标为 `TEMPORARY_SESSION` 并关联同一 temporary ID；不得保存普通 Conversation/Project ID、URI、path、Key、Prompt、RunSpec、Provider payload、费用、缓存或同步字段。
- 到期/无效 schema、超限文本、未知字段或非法 ID 才删除 recovery：同一维护路径解除同一 temporary ID 的 attachment 引用并清理最后引用 private asset，绝不写普通回收站、action ledger、审计历史或导出。系统 picker 取消不改变 recovery。
- 异常恢复最多至最后有意义 mutation 的 `updatedAt + 24h`；查看、Ghost 切换和启动发现不续期。owner Clock 在 `23h59m` 保留、`24h` 删除；过期 record 与其最后引用 attachment 必须在同一维护路径清除，重复清理幂等，并让 UI 非阻塞回到 NORMAL。

## 3. 隔离与零泄漏矩阵

| 消费者或路径 | NORMAL | TEMPORARY |
| --- | --- | --- |
| 普通历史、会话侧栏、置顶、归档、回收站、搜索 | 既有行为 | 不读取、不投影、不写入 |
| Project / 工作区 / Knowledge / Memory | 既有显式行为 | 无归属、无候选、无写入 |
| 导入、导出、交换协议、备份 | 既有合同 | 不进入 payload、manifest 或 asset index |
| 同步、缓存、训练或 Provider 声明 | 既有独立合同 | 无字段、无任务、无声明；本阶段不实现联网 |
| Draft、消息、model override、附件 | 普通 owner | 同一 temporary recovery record/temporary ID；附件引用带 `TEMPORARY_SESSION` scope |

## 4. 双端入口矩阵

| 功能 | Desktop | Android | 真值与验证 |
| --- | --- | --- | --- |
| 进入临时聊天 | 右上角同义临时图标入口 | 右上角同义临时图标入口 | typed temporary owner；重复点击幂等 |
| 顶栏状态 | 明示“临时聊天” | 明示“临时聊天” | 普通与临时草稿/消息/附件/model override 严格隔离 |
| 返回 NORMAL | 再点右上 Ghost 直接返回 | 再点右上 Ghost 直接返回 | 不删除 TEMP；24h 内恢复同一 session |
| 重建恢复 | `.app` 关闭/重开 | Activity/process 重建 | 隔离 recovery record；24h 过期清理 |
| Ghost | icon-only、无菜单/确认 | icon-only、无菜单/确认 | idle neutral，TEMP active当前主题强调色 |

## 5. 交互、生命周期与视觉

- 两端从普通聊天右上角同义 Ghost 进入；Ghost 直接 NORMAL↔TEMP，无 modal/menu/确认，重复进入只恢复同一 active temporary record。`title`/`aria-label`/`ContentDescription` 均为“临时聊天”。
- temporary 顶栏明确显示“临时聊天”，不得显示普通会话标题或项目范围；Back/Escape 返回 NORMAL，不丢 TEMP 草稿。没有置顶、归档、项目、搜索、导出、同步或回收站动作。
- TEMP复用各平台当前Composer、共享主题token与角色表面，不维护第二份边框、字体、焦点或橙色常量。轮廓和关闭／焦点行为继续遵循当前会话、主题合同；临时状态的可识别性不授权修改皮肤。
- TEMP的添加与模型控件消费当前平台共享图标／命中面，只编辑temporary recovery的安全model override；显示本地标识不代表已配置模型，不读取Key或调用Provider。
- 发送／停止的尺寸、箭头和反馈只遵循当前平台会话合同；TEMP只追加本地消息，没有Provider运行时不得伪造stop或模型生成状态。

## 5.1 共享 app-owned overlay 关闭合同

- Desktop popover/context menu/dialog/drawer 由单一 topmost dismiss owner 管理：外部 pointerdown/click/contextmenu、Escape、resize/scroll 只关闭最上层；新 trigger 先关闭旧层；关闭后还焦 trigger。scrim 只关闭/取消，不得调用删除、移动、导入、分享或临时清理 mutation；内部点击不得误关。
- Android app-owned `Dialog`/`AlertDialog`/`ModalBottomSheet`/drawer 的 `onDismissRequest` 与 system Back 同义：先 topmost overlay、再 drawer、再页面；confirmation 外点等于 Cancel，草稿、选择、附件、滚动不变。系统 picker 由 OS 管理，cancel 不改变 app state。

## 6. 验收与证据

- 自动覆盖 NORMAL/TEMPORARY 明确区分、普通草稿进入隔离、Ghost 直切不删除、重复入口、窗口/Activity/进程重启、23h59m/24h、picker cancel、附件引用/last-reference 到期清理、model override 隔离及 history/search/project/knowledge/memory/export/sync/cache 零泄漏；另覆盖 topmost overlay、confirmation cancel、焦点回归与草稿保持。
- Desktop 真实 `.app` 与 Android 正式签名 Debug/Release、当次隔离模拟器 同签名安装、重启读回分别证明；不读 Key、不发 HTTP、不改图标。
- 另加静态扫描：temporary owner、Android/Desktop recovery store 和导出/搜索/同步/缓存路径不能引用普通 conversation record 或附件 URI/path/Key；UI token 不得绕过 shared orange semantic token。
- 报告必须分开陈述：合同、定向测试、构建、Desktop `.app`、Android 正式签名、模拟器重启/readback；不得把任一层替代另一层。
## 历史2026-08-13补充入口记录

### P6-E 补充入口矩阵（2026-08-13；收口中）

- 临时聊天右上入口使用同一 Lucide Ghost（MIT）几何语义，idle 中性、active accent orange/soft，Desktop `title/aria-label` 与 Android `ContentDescription` 均为“临时聊天”；Ghost 直接 NORMAL↔TEMP，绝不显示退出/清除 modal/menu。
- 常规 sidebar/drawer 不再放 archive/recycle；它们只能由“设置 → 数据与存储 → 会话管理”的真实 archived/soft-delete owner 投影访问。work 的 workspace 管理与本地受控记录也分别进入真实 Settings 二级页，不能只隐藏。

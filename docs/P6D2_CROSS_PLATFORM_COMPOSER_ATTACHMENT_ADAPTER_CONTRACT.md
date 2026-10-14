# P6-D2 双端 Composer Attachment Adapter 合同

<!-- contract-governance:start -->
> **合同口径：现行领域合同。** 领域入口：[ Composer材料与附件桥 ](P6D2_CROSS_PLATFORM_COMPOSER_ATTACHMENT_ADAPTER_CONTRACT.md)；[全项目口径与维护索引](contracts/CONTRACT_INDEX.md)。本文的独立owner／格式边界不因归类改变；阶段状态、Schema与验收记录只适用于注明的范围，不能覆盖其他领域或推断整机当前能力。
> **适用范围：** 选择/本机解析/私有副本到已授权普通发送的材料桥；本模块不自行外发
<!-- contract-governance:end -->

状态：完成；P6-E 临时聊天的前置已满足，不改变 Conversation role、Provider 或图标边界。

> **当前 Android UI 路由（2026-08-24）：** 本文保留附件 private-copy、类型校验、容量与角色领域语义。Android 会话中的 Assistant/USER 表面、附件预览几何、Composer、来源、字体和图标视觉规则统一以 [Android 当前会话界面合同](ANDROID_CONVERSATION_UI_CURRENT_CONTRACT.md) 为准；本文早期视觉措辞不形成并列 UI 合同。

## 当前文档发送与检索补充（2026-10-12）

- 下方“当前阶段没有附件外发”是P6-D2建立私有副本时的历史阶段边界，不能用于禁止已实现的普通会话发送。当前全任务检索范围与逐项来源规则由[C12联网合同](C12_MODEL_NETWORK_PARITY_CONTRACT.md)管理；RelayCore合同拥有该渠道身份/别名，准确接收方授权唯一在P3J，当前会话合同拥有显示投影。
- 受支持的正文文本格式统一由`protocol/fixtures/attachment-document-types-v1.json`约束：TXT、Markdown、JSON、CSV、XML、YAML、HTML和DOCX／XLSX／PPTX。两端实际正文转换后才提取检索线索；选取、预览、Auto兼容判断与发送投影不能各保留一份较窄格式表。markup仍为惰性文本，不执行脚本、宏、外部关系或实体。Office仅提取可读文本层，不宣称解析嵌入媒体、图表或重算公式。
- 发送不能复用128KiB的Office预览截取。Office完整文本最多800000字符；超过上限明确拒绝，不将截断前缀标为完整正文。OOXML仅读取所需XML，单part4MiB、累计12MiB、最多2000条目；无效结构、路径或外部实体失败关闭。
- PDF能本机提取文本层时可用于文本检索；损坏／加密／无文本层不允许返回空成功。支持原生PDF的接口仍可接收原文件；扫描件及媒体可由原选、已声明支持该类型的原生接口读取并提取公开核验事项；不支持时使用既有明确转写流程或拒绝，不能静默交给另一个模型、返回空读取成功或遗漏某份媒体。Android OpenRouter的联网工具附着于同一多模态Chat请求，不强制丢弃模型已声明支持的图片／PDF／音频／视频；其他文本搜索接口保持准确的文本能力边界。
- ZIP发送提取其中受支持文档／PDF的正文，保留逐个成员边界；不以目录清单代替内容。最多2000条目、单成员16MiB、累计展开40MiB，完整正文受800000字符上限约束；不自动递归ZIP，不静默跳过不支持或失败的成员，拒绝重复／越界路径。媒体成员先在现有入口选择或转写。
- 单附件、多附件和混合类型均保留文件边界并完整遍历；“以下是文件”“以下是附件”以及独立附件文本块均为分隔。JSON记录无论换行或单行存储，都按记录提取实体和日期，公开网址单独绑定该记录；已识别私密字段排除。检索线索没有首12项硬截断；编号Markdown、普通及结构化材料的核验项粒度、来源绑定与完成语义只读C12当前规则；不得把旧整章核验写成现行解析结果。附件适配器不定义统一固定字符拆分粒度；每文件材料容量、事项计划、总调度上限、分批、有限补查和未知结果停发规则只由[C12联网合同](C12_MODEL_NETWORK_PARITY_CONTRACT.md)持有，两端与所有模型共用，不能在附件适配器重复设置更小上限。
- 格式和转换回归不替代真实Provider或原生设备验证；“全部格式支持”不表示任意二进制、任意模型或所有媒体已经通过联网实测。

当前整体模型能力与维护层级见[总架构](model-service/MODEL_SERVICE_ARCHITECTURE.md)。当前选取／发送容量按各平台对应类型owner的实际参数验证；初期20MB／40MB数据不是所有后续格式和媒体的统一上限，持久引用硬上限也不等于选取／发送上限。

## 唯一所有者与路径

- Android：`DocumentsUI / Photo Picker -> Android*SelectionReader -> AddConversationImageAttachmentUseCase -> AndroidPrivateAttachmentStore -> RoomPrivateAttachmentRepository -> ConversationDraftRepository`。
- Desktop：`macOS dialog -> import_desktop_conversation_attachment -> DesktopWorkspaceStore -> mutate_desktop_domain`。Rust 复制到 app-private content-addressed `assets/<sha256>`，SQLite 只保存安全 metadata；前端草稿只保存 `id/mime/displayName/byteCount/sha256`，不保存系统 path/URI。
- Draft 和 Message 只拥有安全 Attachment reference。二进制、私有位置、受控读取与 GC 属于平台 attachment owner；UI、Message role、Prompt、RunSpec、Key、HTTP 不得成为第二 owner。Desktop `DesktopWorkspaceStore` 的启动 maintenance 是唯一清理入口：SQLite v6 `desktop_attachment_assets` 保存 `created_at_ms`、`last_referenced_at_ms`、`last_unreferenced_at_ms` 与精确 `reference_count`，`desktop_attachment_staging` 保存私有 staging 的创建时间。正常运行以系统 Clock 调用同一 owner；Rust 合同测试以显式 epoch 调用同一 owner，绝不以文件 mtime 或 shell 删除充当业务 GC。

## 初期P6-D2输入与失败关闭记录（历史范围）

- 只接受 JPG/PNG/WebP、PDF、TXT/Markdown/JSON/CSV。声明 MIME、扩展名和文件魔数/非 NUL 文本前缀必须一致；普通未知二进制、symlink、URI/path、空文件和不匹配输入均拒绝。
- 单项最多 20 MB；每条草稿/消息最多 4 项、总计最多 40 MB。按 SHA-256 去重；重复选择不新增引用。
- 每个已接受输入必须先私有复制并回读 metadata，再进入草稿；取消或失败保持既有文字/附件。重启可恢复安全草稿 metadata；Desktop 仅在无有效消息引用且私有副本超过 24h 时回收 orphan。最后一个有效消息引用消失时才写 `last_unreferenced_at_ms`；23h59m 保留、达到 24h 才删除 asset 与其安全 metadata。中断 attachment staging 同样只在其 owner metadata 满 24h 后清理。
- 当前阶段没有附件外发、OCR、正文提取、预览执行或 Provider capability 推断。图片缩略图必须受控有界；非图片只显示安全 chip。

## 角色与视觉合同

- USER message bubble 和 attachment chip 只消费共享 `accent-orange-soft`，正文为深中性色；禁止按页面散落 hex 或改写 `MessageRole` 领域。
- ASSISTANT 为中性 surface；SYSTEM 为中性 system surface；TOOL 为 tool semantic surface；ERROR 保留错误 surface。quote/code 的内层始终中性，不能继承用户橙色。
- Desktop 与 Android 的 role mapping/contrast 合同必须验证每种消息正文对比度至少 4.5:1，并同时回归 Composer orange、发送/新建按钮、12px/12sp 会话行、PushPin/Archive 与 attachment chip。

## 验收门

1. Android JVM/Room/UI contract、Desktop Node/Rust contract、两端构建。
2. macOS `.app` 的原生图片和文件 picker：private copy、重复、取消、发送、完整退出/重开 readback 与 24h GC 的安全证据。已完成：Rust 34 tests 覆盖可注入 Clock、引用/23h59m/24h/staging/幂等/reopen；最新 ad-hoc `.app` 在隔离 HOME 的 app-private fixture 启动 maintenance 后保留 referenced/fresh orphan，删除 expired orphan/expired staging，第二次启动目录计数、SQLite 行数和 asset aggregate SHA-256 不变；metadata/receipt 只含安全 hash/计数/时间，不含 source path/URI。
3. 当次隔离模拟器 的 Photo Picker 与 DocumentsUI：同一链路、force-stop/readback、同签名安装/hash；不清数据、不装 test APK、不操作 OPPO。
4. 每端的真实可见视觉状态必须独立记录；自动回归、构建或旧 P6-D 截图均不能替代。

## 平台例外与完成证据

- Android：本合同只要求其 private-copy、dedup、发送、force-stop/readback 和可见状态；**Android 24h attachment/staging GC 为 N/A**，没有实现、测试或宣称该能力。
- Desktop：2026-08-13 已在全新 `/tmp/nanfeng-ai-p6d2-app-fixture-20260813` HOME 下完成非敏感、app-private 合成 fixture。启动前为 3 assets/1 staging；最新 `.app` 启动后为 2/0，SQLite 显示 referenced `reference_count=1`、fresh orphan `0`，过期 orphan/staging 均不存在；重启仍为 2/0，asset aggregate SHA-256 `a806aa53a0e4eab2b74fec881bf581cdf95762028c6436316a42f907fb4ea412`。该 fixture 不接触任何长期 workspace、真实用户文件、Key 或网络。

## 附件适配owner的职责边界

选取/校验/本机正文转换/私有副本owner不读Key、不构造Authorization/Prompt/RunSpec、不发HTTP，也不自行改变role或启动同步。这是Adapter自身边界，不禁止普通发送执行器使用已批准的准确材料和Provider；TEMP仍只走独立本地记录。真实设备与外发按对应授权管理。

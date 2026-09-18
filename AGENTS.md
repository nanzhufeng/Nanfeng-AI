# 南枫 AI 长期开发规则

## 事实与范围

- 先确认当前 checkout、工作树和 `docs/CURRENT_HANDOFF.md`；历史资料不证明当前状态。源码／可复现验证优先，冲突记录依据。
- 会话／搜索／文件读取 `docs/ANDROID_CONVERSATION_UI_CURRENT_CONTRACT.md`；设置读取 `docs/ANDROID_SETTINGS_UI_CURRENT_CONTRACT.md`；普通上下文读取 `docs/ANDROID_RUNTIME_CONTEXT_CURRENT_CONTRACT.md`；转写读取 `docs/ANDROID_TRANSCRIPTION_UI_CURRENT_CONTRACT.md`。Desktop、协议与服务读取对应 owner 的合同；入口索引见完整开发档案。
- Android、Desktop、协议、Supabase 与附件网关分别确认实现及证据，各层证据不能互相替代。
- 跨端同步先读 `docs/P7F_SELECTED_CONVERSATION_SYNC_CONTRACT.md`，区分当前 wire 格式与历史安全承诺；不得把认证、hash 或旧加密模块的存在当作当前路径端到端加密证据。
- Google 账号直同步不得依赖恢复码、恢复确认或旧加密密钥；新设备、重装、重登和定期同步都必须能在无恢复材料时工作。旧密文兼容仅逐条处理，不得重新成为整批前置门槛；规则与例外唯一正文见上述同步合同。

## 数据与执行边界

- 扩展既有 owner：Android UI／ViewModel 投影状态，domain 管策略，data 管 Room／私有文件／系统适配，ai 管 Provider，后台 owner 管持久任务；Desktop 前端经受控 Tauri IPC 调用 Rust／SQLite owner，不另造业务真相。
- 本机数据与联网能力并列。选择、编辑、预览不外发；普通发送授权本次准确材料交给已披露接收方，不增加逐消息重复确认，不静默更换 Provider／模型或扩大材料范围。后台任务仅在既有明确授权范围内执行。
- 授权业务正文和附件只进入其业务存储／导出 owner；凭据、恢复秘密、原始 Provider payload、无关正文和私人路径不得进入 Git、诊断日志或无正文审计。凭据 presence 只查状态，实际执行／测试连接／明确显示才读 secret；拒绝不等于未保存，不自动删密钥或后台重试。
- 数据结构变化必须有连续前向迁移、版本化 schema 和定向升级验证；不改结构的持久化变化验证事务、并发、失败和关闭重开，不无故增加 schema。禁止 destructive fallback、删库或新装夹具冒充真实升级。
- 外部动作保留 Attempt／receipt／provenance／幂等和真实归因；UNKNOWN 不静默重发。字节身份与 occurrence 分开，最后真实引用消失前不得删除共享附件。
- 同步只传递有来源的事实，不按模型名／时间或当前设置重写用户历史；字段冲突沿领域合同处理。远端缺失清理须有完整身份清单与账号边界，恢复成功子集和网络失败不能当空清单。
- 导入、备份、语义交换和加密同步使用各自严格格式与 owner；未知／歧义字段、来源或路径显式拒绝，不猜配、不把 Android 数据库当 Desktop 协议。
- 模型变更贯通目录、凭据、路由、材料桥、预算、归因、费用与入口；原生能力、请求意图和实际结果分别记录。

## 交互、验收与交付

- 用户可见增量复用现有入口和共享组件，去留及按键建议按当前产品决定登记；未经判断不新增常驻按钮。控件表面、阴影、反馈与焦点服从同一轮廓，关闭浮层须消费输入，不穿透触发业务动作。
- 设备与正式签名遵守全局契约：永久禁止任何 `connected*AndroidTest`；主设备禁止 Debug／仪器测试、自动部署、卸载和清数据。明确授权后只可验签正式包保数据覆盖，并前后核对身份及数据指纹。Debug 与正式包可能同 ID／同签名，不能据签名相同部署主设备。
- Android 签名只从完整环境变量或用户级 App 专属 Gradle 属性解析，不读取 macOS Keychain、不输出秘密、不生成替代身份。Desktop 隔离验收必须确认独立根及 bundle 身份。
- 验收分开报告代码／静态、行为测试、构建、视觉、隔离原生、主设备和真实服务；列明失败、skip、覆盖缺口及未运行层。测试 LaunchAgent 遵守全局唯一 label、精确清理及残留核验门。

## 文档与可复用流程

- 当前行为写唯一领域合同，动态证据写当前交接，历史交接归档，长期取舍写决策日志；完整开发档案和可迁移经验不再维护第二套“当前状态”。版本、测试数、hash、临时设备状态不写入本文件或 Skill。
- 使用 `.agents/skills/` 下独立开发、排错、测试交付、代码审查和文档同步流程；流程只放重复方法，不修改生成记忆。文档任务结束核对业务文件未变，保护范围外工作。

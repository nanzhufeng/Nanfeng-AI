# 南枫 AI 长期项目规则

## 事实与合同

- 先读本文件、[当前交接](docs/CURRENT_HANDOFF.md) 和相关源码。会话／搜索 UI、设置 UI、普通聊天上下文的唯一正文分别是 [会话合同](docs/ANDROID_CONVERSATION_UI_CURRENT_CONTRACT.md)、[设置合同](docs/ANDROID_SETTINGS_UI_CURRENT_CONTRACT.md) 与 [运行时上下文合同](docs/ANDROID_RUNTIME_CONTEXT_CURRENT_CONTRACT.md)；旧 P 阶段文档只作历史证据。
- 文档、截图和记忆与当前代码或最新验证冲突时，以代码与可复现验证为准，并在当前交接或相应合同记录冲突；不得悄悄沿用旧版本、旧 Schema、旧 APK 或旧验收结论。
- 用户可见功能先按 [入口审计](docs/ANDROID_DESKTOP_USER_ENTRY_AUDIT_20260816.md) 判断 Android／Desktop 去留、入口与理由。不得恢复已删除的“设置 → 功能审阅”页面，也不得未经确认新增聊天主页、Composer 或会话详情的常驻按键。

## 数据、外发与迁移

- 保持本地优先：Key 不进入源码、Git、Room、日志、截图或导出；附件、导入包和调用审计遵守各自 owner 与最小化原则。任何真实 Provider、网页检索、附件外发或云端动作都必须保留实际接收方、用户授权与失败边界，不能用 UI 文案代替。
- 修改 Room 实体、导入／导出、恢复或会话持久化时必须维护可升级迁移链并写定向测试；禁止 destructive migration、删库、私有数据库注入或用样本数据掩盖升级风险。
- 发送、重试、取消和进程恢复必须保留持久化 Attempt 的事实；未知结果不得静默重发，切换 Provider／模型不得把原请求无提示改投其他服务。

## 验证与交付

- 按“定向契约 → Kotlin/构建 → 全量 JVM → 视觉 → 真机/真实服务”分层报告；构建、JVM、安装和截图互不替代。全量失败必须列出数量、测试类与未解决边界。
- 永久禁止 `connected*AndroidTest`。OPPO 主设备仅可在用户授权后以同包名、同证书、非 Debug 的正式 APK 做保数据覆盖；先只读核对身份与签名，禁止卸载、清数据、数据库注入、Debug／仪器包、自动部署或读取私有业务数据。
- 正式签名仅从完整环境变量或用户级 `~/.gradle/gradle.properties` 的项目命名空间读取；缺失即停止，不访问 Keychain、不生成替代签名。发布、覆盖或外部服务动作需单独报告产物、签名、设备读回与未覆盖风险。

## 文档与流程

- 功能／视觉／上下文变更同步更新对应当前合同；涉及当前版本、验收或跨域边界时同步更新 `CURRENT_HANDOFF.md`。长期取舍写决策日志，历史证据不改写为当前事实。
- 复用 `.agents/skills/` 中与任务匹配的流程；Skill 只放可重复方法，项目当前事实放文档，临时状态不写入本文件。

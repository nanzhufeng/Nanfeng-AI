# 南枫 AI 长期开发规则

## 事实来源与任务范围

- 开始先读 [项目状态](docs/PROJECT_STATUS.md) 与 [当前交接](docs/CURRENT_HANDOFF.md)，核对 checkout、工作树及未跟踪实现。HEAD或产品版本号不代表当前WIP、现装包或全部本地可达Git历史；保留范围外 WIP，不以 reset／clean／stash 收口。
- 当前源码与适用验证优先于旧文档；冲突记录来源、裁决和影响。按 [合同索引](docs/contracts/CONTRACT_INDEX.md)、[合同治理](docs/CONTRACT_GOVERNANCE_CURRENT.md) 定位唯一行为正文，修改owner时反查同领域CURRENT和SCOPED子合同；[开发档案](docs/南枫AI完整开发档案.md) 只提供架构、历史与证据导航。
- 模型服务变更先读 [统一架构](docs/model-service/MODEL_SERVICE_ARCHITECTURE.md) 与 [维护流程](docs/model-service/MODEL_SERVICE_MAINTENANCE.md)，同时核对维护目录、双端生产 owner 和离线门禁。历史菜单、阶段方案与外部目录声明不等于当前能力。

## 所有权、数据与外部动作

- Android 通过 Compose／ViewModel → domain／ai／data／background；Desktop 通过 ESM → Tauri IPC → Rust／SQLite。复用既有 owner；IPC 改动同时检查调用、注册、权限、校验和投影，不让 UI 形成第二份业务真相。
- Android、Desktop、协议、Supabase、附件网关分别实现与验收；不直接交换平台数据库。CHAT／WORK 物理分库、分区同步与恢复；无项目 WORK 仍属 WORK，隐藏入口不意味着删除数据。
- 选择、编辑和预览默认本地。发送绑定准确材料与已披露接收方；不得静默换 Provider、模型、Key，扩大外发范围或用 fallback 规避授权。UNKNOWN 不自动重发；跨阶段聚合不得丢失失败确定性而发起后续外部调用，具体停止边界由 C12 持有。
- 凭据、恢复秘密、原始 Provider payload、用户正文和私有路径不进入 Git、普通日志或无正文审计。凭据 presence 与 secret read 分开；秘密仅在已授权实际动作中读取。
- 结构变化要求连续前向迁移、schema 与升级／重开验证；持久化行为覆盖事务、并发、失败和重开。导入／备份／同步校验格式、路径与身份，不猜配或损失性成功。

## 验证与交付边界

- 分开报告源码／静态、行为测试、构建、隔离原生、主设备、真实服务；列明 skip、失败与未运行层。旧链路成功、模拟回执、构建和安装不能替代新链路业务验收。
- 永久禁止任何 `connected*AndroidTest`。主设备禁止 Debug／仪器测试、自动部署、卸载和清数据；正式包仅按授权验签保数据覆盖并读回。模拟器亦遵守当前任务范围，不扩大历史授权。
- Android 正式签名从完整环境变量或用户级 App 专属 Gradle 属性读取，不读取 Keychain 或生成替代身份。Mac 已授权覆盖可复用原证书，仍核对原身份与数据；凭据缺失、身份变化或需用户输入时停止。Desktop 验收使用独立数据根及 bundle 身份。
- 收费测试先回放已有安全回执及本地回归，再做最小必要真实请求；预计一轮超过 ¥3，先说明必要性与费用上限。不无目的比较候选或重复长报告，未知费用不记零。

## 文档与重复流程

- 当前行为 → 领域合同；动态状态与验收 → PROJECT_STATUS／CURRENT_HANDOFF；取舍 → decision-log；历史与架构 → 开发档案；跨项目方法 → 可迁移开发经验。各入口只路由，不维护第二套同义规则。
- 开发、排错、测试交付、代码审查、文档同步分别使用 `.agents/skills/nanfeng-ai-*` 的五个独立 Skill；入口见开发档案。纯文档任务结束比对业务源码、配置、schema、应用测试与 HEAD，保护原始证据。
- README 预览图使用仓库内独立文件及相对路径，不进入 Release 附件或引用 Release 图片地址；双端发布每个平台至少两张正常界面图。Release 只上传安装包与校验文件。

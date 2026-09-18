# 南枫 AI 长期开发规则

## 开始与事实来源

- 每次先核对当前 checkout、工作树和 [当前交接](docs/CURRENT_HANDOFF.md)。当前源码与可复现验证优先于历史档案；冲突必须记录来源、裁决和影响范围。
- 按任务读取一份现行合同：会话与搜索、设置、[运行时上下文](docs/ANDROID_RUNTIME_CONTEXT_CURRENT_CONTRACT.md)、转写、工作区隔离与同步分别由 `docs/` 对应合同定义。完整模块地图见 [开发档案](docs/南枫AI完整开发档案.md)。
- Android、Desktop、协议、Supabase 与附件网关是独立实现层。相同界面或概念不等于共享数据库、数据模型或验证结论。

## 数据、授权与所有权

- 复用现有 owner：Android 由 Compose/ViewModel → domain/ai/data/background；Desktop 由 ESM → Tauri IPC → Rust/SQLite。IPC 变更同时核对前端调用、Rust 注册、权限、参数验证和回传投影。
- CHAT 与 WORK 必须物理分库、分区同步和分区恢复；`projectId=null` 不能把 WORK 当 CHAT。跨区只允许用户显式选择的引用，源记录不移动、不自动同步。
- 选择、编辑和预览默认本地。发送只授权本次已披露接收方取得准确材料；不得静默更换 Provider、扩大材料或以 fallback 规避授权。
- 凭据、恢复秘密、原始 Provider payload、用户正文和私有路径不得进入 Git、普通日志或无正文审计。先检查凭据状态；只在已授权的实际动作中读取 secret。UNKNOWN 不自动重发。
- 结构变更必须有连续前向迁移、schema 与升级／重开验证；持久化行为变更须验证事务、并发、失败和重开。导入、备份和同步拒绝未知／歧义的格式、来源或路径，不猜配、不损失性成功。

## 验收与交付

- 验收分开报告：源码／静态、自动测试、构建、隔离原生、主设备、真实服务。skip、失败和未运行层不可省略；构建或模拟 fixture 不替代真实账号、设备或服务。
- 永久禁止 `connected*AndroidTest`。主设备禁止 Debug／仪器测试、自动部署、卸载或清数据；仅在明确授权后，使用验签正式包保数据覆盖并核对身份和数据指纹。
- Android 签名只从完整环境变量或用户级 App 专属 Gradle 属性读取；不访问 Keychain、不输出秘密、不生成替代身份。Desktop 隔离验收必须使用独立数据根与 bundle 身份。

## 文档与流程

- 当前行为写领域合同；动态测试、设备和交付证据写当前交接；长期取舍写 `docs/decision-log.md`；档案和经验只做索引与可追溯总结，不复制第二套行为规则。
- 开发、排错、测试交付、代码审查和文档同步分别使用 `.agents/skills/nanfeng-ai-*`。文档任务结束必须核对业务源码、配置、schema 和测试未被改动，并保护范围外文件。
- README 预览图必须作为仓库内独立文件以相对路径引用；永不作为 GitHub Release 附件，也不得引用 Release 下载地址中的图片。双端发布时每个平台至少保留两张正常界面图。Release 只上传安装包和校验文件。

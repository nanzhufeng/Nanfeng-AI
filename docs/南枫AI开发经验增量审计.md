# 南枫 AI App 开发经验增量正式审计

本轮在[完整开发档案](南枫AI完整开发档案.md)和[可迁移开发经验](可迁移开发经验.md)之上固化最终回归与Git checkpoint；已完成复盘、模型比较和安装不重做。当前目标是让准确实现与证据可从版本控制集合读取，不增加产品功能或外部动作。

## 1. 审计结论

第一用户任务是向明确选定模型发送材料，获得可回读的回答、真实联网证明与准确计量。完成定义分别验证输入、授权、来源、逐项范围、最终终态、持久正文和物理费用，不能以其中一层代替整个任务。源码checkpoint是可追溯基准，不等于所有真实场景验收完成。

## 2. 最终基准与明确排除

| 证据层 | 本轮基准 |
|---|---|
| 源码提交 | 本地标签checkpoint-search-routing-20261014；准确commit及纳入集合见[checkpoint报告](verification/2026-10-14-code-checkpoint/REPORT.md)，未推送 |
| 自动门禁 | Android1495 PASS/10 skip、Rust443 PASS/34 ignored、Node551 PASS/12 skip；维护43、Supabase静态10、协议golden及Go通过，[本轮回归](verification/2026-10-14-code-checkpoint/regression.json) |
| 正式构建／hash | 复用10月14日10:49前后的原证书1.0.29冻结包证据；本轮只重新JVM/Lint、Rust与Desktop静态build，不重签app/DMG，[原安装报告](verification/2026-10-14-fact-risk-cover-install/REPORT.md) |
| 模拟器／集成 | 复用原Android AppContainer/Room、Mac原生SQLite及原位复制隔离证据；不重新启动应用、系统picker或数据库 |
| 真实设备／服务 | 原双端覆盖、真实Gemini/Muse普通文字和历史Luna矩阵按原执行范围保留；本轮0新增Provider调用，不刷新旧服务时间 |
| 数据保留 | 原安装Android元数据保持、Mac1595文件hash一致；本轮未操作正式数据根 |
| 未验证增量范围 | v5逐事实真实返回/长原件、新Muse多附件/媒体、RC Pro附件404、正式原生入口等仍不进入“全部功能通过”基准 |

明确排除：旧主题回执不能证明新事实范围；旧智谱链不证明Luna；失败实验、早期截图、测试cap截断及旧403不替代最新实际结果；原始Provider正文/回执、私有路径/材料、临时before/日志及重复源码副本不进入checkpoint提交。历史证据保留本地，不删除或重命名冒充新成功。

## 3. 分类口径

A为项目专属事实；B为跨项目方法；C为用户已明确偏好，仅作当次约束引用，不新增记忆；D为平台/设备规则；E为尚未验证判断。自动回归、构建、隔离、真实设备和真实服务是独立验证等级。

## 4. 用户反馈—根因—最终实现—验证证据矩阵

| 维度 | 用户反馈／原问题 | 根因／首个分叉 | 最终实现 | 自动测试 | 模拟器／集成 | 真实设备／服务 | 尚存风险 | 分类 |
|---|---|---|---|---|---|---|---|---|
| 产品 | 部分报告有来源仍有很多未核实 | 搜索完成、单主题覆盖和事实核实混为一谈 | 完整事实句独立ID、来源与scope分离 | 两端9用例25事实fixture及当前全套 | 原件实际planner66项/17批一致，只离线 | 新66项逐事实服务返回未验 | 不能宣称66/66核实 | A/B |
| UI与交互 | 底部信息不能正常长按复制 | 可见metadata被隐藏字段复制限制覆盖 | Android共享原生选区、Desktop metadata选择与菜单 | 页脚及当前Node/JVM回归 | Android系统Copy→未发送输入框一致；Mac浏览器投影通过 | 原生Mac页脚尚未验 | 浏览器不证明WebView系统菜单 | A/D |
| 直接操纵与反馈 | 窄屏金额分行也要复制 | 布局分支丢失同一语义选择区 | 同行/分行读同一metadata owner | 定向布局/选择回归 | Android两尺寸回放并恢复 | 本轮未触摸正式设备 | 设备形态/字体仍独立验证 | A/D |
| 功能与状态 | 有完整DeepSeek回答却显示未完成 | 单次工具耗尽替代真实最终终态 | 正常终态与实际来源联合判定，局部失败仍保留 | Provider/状态/账本回归 | 原生持久重开 | 两型号同题真实结果见既有报告 | 上游截断/无来源仍失败 | B |
| 架构数据 | 统计逐次Luna与页脚不一致 | 物理调用事实与读投影分组混用 | 物理账保留，检索按root分组、回答独立、页脚合计 | Room/SQLite七笔→两行、未知/币种 | 零网络重开原账不变 | 本轮无新收费 | 旧缺关联不猜配 | B |
| 错误可观测性 | 回答有字但金额未知 | 失败终态丢量化回执/尾部usage | 精确请求usage、计量交接与cost-only本地恢复 | Adapter→持久owner失败重开 | 公开本地HTTP→SQLite/Room | 已有RC/两模型实际计量 | 无回执旧费用不能补造 | B |
| 性能与成本 | 克制高价/长报告重复测试 | 协议探索、完整材料和测试cap混合，前置失败仍发请求 | 已有安全回执先复用、前置分步门、最小必要实测 | 现有公开fixture/零网络回放 | 减少新真实请求；本轮0调用 | 既有Gemini同题短答有源，不代表完整长答 | 未测生产时延/长期稳定性 | B/C |
| 账号与外部集成 | Muse年龄已确认仍需复验 | 旧403与后来无源/接口问题被混称同一原因 | 同OpenRouter/同模型Responses可读材料例外 | 请求/来源/终态/计量回归 | 双端普通文字持久重开 | 年龄已确认且普通文字双端通过 | 新附件/媒体及线上同步未全验 | A/D |
| 测试验收 | 双端与所有模型不能只凭本地绿灯 | 目录枚举、结构扫描、构建被扩大成服务验收 | manifest门＋双端owner fixture＋逐模型范围矩阵 | 本轮全套及离线维护43通过 | 原矩阵/14格式代表模型正交证据 | Pro404/旧UNKNOWN/504保留 | 非20×14全部组合 | B |
| 交付 | 保数据覆盖并固化当前代码 | 版本号同名不证明实装字节/源码集合 | 精确包/签名读回，checkpoint包含新增owner及依赖 | 本轮source hash/暂存集合门 | 原独立验收包与生产隔离 | 原正式覆盖及数据证据复用 | 未新安装/发布/Windows | B/D |
| 失败经验 | 多轮修复后仍反复漏合同或文件 | 历史条款和未跟踪核心被误当当前/不存在 | 合同scope/owner门，清单含非ignored未跟踪，缓存版本分域 | 维护27＋模型16、清单8类前向验证 | 旧原件/失败按日期保留 | 文档绿灯不关闭业务缺口 | 43历史引用缺失、7XML原件非标准 | B |

矩阵依据：[事实风险](verification/2026-10-14-attachment-fact-risk-review/REPORT.md)、[页脚复制](verification/2026-10-14-footer-text-copy/REPORT.md)、[DeepSeek](verification/2026-10-13-deepseek-terminal-fix/REPORT.md)、[费用分组](verification/2026-10-13-retrieval-cost-grouping/REPORT.md)、[Gemini/Muse](verification/2026-10-14-gemini-muse-risk-fix/REPORT.md)、[Gemini同题](verification/2026-10-14-gemini-search-repeat/REPORT.md)、[全合同](verification/2026-10-14-contract-implementation-unification/REPORT.md)。没有新实验去填旧空白。

## 5. 跨项目规则

- 外部动作结果不确定 → 保留Attempt/已返回正文/量化事实并停止自动重发 → 验证失败/取消/重开与零网络恢复 → 禁止unknown当零或成功。
- 同概念有多入口/平台 → 经唯一owner及公共fixture验证 → 分别核对持久化与原生路径 → 禁止同家族/同页面推断能力相同。
- 准备checkpoint → 盘点跟踪/未跟踪依赖、格式与秘密，提交准确闭包 → 验证暂存索引及源码hash → 禁止git add全仓混入原始回执或依赖未提交owner。
- 改变完成粒度或缓存身份 → 明确语义版本及旧结果拒绝 → 验证旧缓存不能覆盖新任务 → 禁止参考URL/一主题结果冒充全部事实。

## 6. 项目专属事实与遗留风险

项目专属路由、direct同步安全模型、预算/缓存期限、Android数据区、UI和签名介质不直接迁移；细分边界沿用[可迁移经验](可迁移开发经验.md)。真实逐项原件、Muse新材料、RC Pro404/旧UNKNOWN/504、正式UI/系统picker/生命周期、线上同步/网关、远端Release/Windows及About原生可达性继续待验。永久connected禁令和主设备隔离按[AGENTS](../AGENTS.md)执行。

## 7. 失败经验与防回归资产

本项目未在本轮做并行下载或无损音视频封装，不适用样本首尾/时间轴证明。嵌入运行时打包只复用准确候选证据，版本控制集合与测试源码单独冻结；不把宿主旧dist/target产物当checkpoint源码。

| 被否定做法 | 失败原因 | 今后禁止项 | 机器防回归 |
|---|---|---|---|
| 用章/关键词代替事实覆盖 | 多断言没有独立证据 | 标全部核实或复用旧范围 | [共享事实fixture](../protocol/fixtures/attachment-report-facts-v1.json) |
| 失败后丢已返费用 | 错误状态覆盖计量 | 补造费用/收费重发 | 两端Adapter/账本重开回归 |
| 目录显示等于模型可调用 | 精确接口/账号未通过 | 换渠道绕过或抹去404 | [模型维护门](../protocol/scripts/verify-model-service-contract.py)及实际矩阵 |
| 全跟踪清单代表当前软件 | 新owner未提交 | checkpoint依赖未跟踪核心 | [可含未跟踪清单工具](../.agents/skills/nanfeng-ai-document-sync/scripts/inventory_project.py)与暂存集合核对 |

## 8. 产物与渐进更新

context沿用[完整档案](南枫AI完整开发档案.md)，架构/规则仍由领域合同与owner map拥有；动态状态写PROJECT_STATUS/CURRENT_HANDOFF。本轮只增量审计和checkpoint，不再重写五类已完成Skill；文档同步入口增加[增量模板](../.agents/skills/nanfeng-ai-document-sync/assets/experience-increment-template.md)路由。复用既有机器门和fixture，未修改外部共享Skill或长期记忆。

## 9. 验证等级

| 等级 | 结果 | 不能替代 |
|---|---|---|
| 文档/Skill | 本轮经验审计结构、链接、秘密/占位扫描及合同门；Skill沿用上轮标准库核验，PyYAML缺失限制保留 | 原生/服务验收 |
| 自动测试 | Android1495/10skip、Rust443/34ignored、Node551/12skip及维护/协议/服务静态全过 | opt-in及生产线上 |
| 构建 | AndroidRelease Lint、Node build/typecheck/lint；原正式签名包仅复核源输入，不重打 | 安装/迁移/业务 |
| 模拟器/集成 | 已有隔离证据按原日期复用，本轮未跑 | OPPO/原生Mac新UI |
| 真实设备/服务 | 原覆盖与普通文字真验保留，未新网络/安装 | 原件全量/媒体/线上同步/新发布 |

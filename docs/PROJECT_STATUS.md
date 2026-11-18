# Project Status

## Current

- 2026-11-18 的本地工作树将 Android／Mac 的 OpenRouter、RelayCore Sol 深度入口和 Auto 原位置升级至 GPT-6.2 Sol。旧当前选择使用原渠道的新预设，历史调用按原模型归因。RelayCore GPT 只读目录已确认精确 ID，模型费率与推理仍待实证。正式版 1.0.41 已同签名覆盖 OPPO（code105）与 Mac，双端正常启动。见[安装验证](verification/2026-11-18-gpt-6-2-sol/REPORT.md)。

- 2026-11-14 本地工作树的 Android／Mac OpenRouter 日常 Gemini 已同位替换为 Gemini 4 Hydrogen。当前 3.7／3.8 选择升级，旧消息、Attempt 和账目保留历史身份。RelayCore 目录仅列旧 Flash，沿用独立配置。正式版 1.0.40 已同签名覆盖 OPPO（code104）与 Mac 并正常启动。Hydrogen 真实聊天、附件和联网来源仍待验。见[接入记录](verification/2026-11-14-gemini-hydrogen/REPORT.md)。

- Code baseline：`1837908b57ff445af157b374d2905ddd17d15b8a` + 未提交WORKTREE（main）；未commit／push／publish。1.0.39/code103源码见[本轮清单](verification/2026-11-02-exa-companion/source-manifest.json)。
- 历史记录（2026-11-02）：Mac已原签名保数据覆盖1.0.39并正常启动，CHAT／WORK语义数据与原Attempt数量不变；[Mac回执](verification/2026-11-02-exa-companion/mac-cover.json)。手机已原签名保数据覆盖1.0.39/code103并正常启动，APK哈希读回匹配、首次安装时间／UID／数据目录及两区诊断摘要不变；[手机边界](verification/2026-11-02-exa-companion/phone-cover.json)。不要使用第一版候选或重复安装相同字节，[最终产物](verification/2026-11-02-exa-companion/artifacts.json)。
- Exa Fast协作检索已双端实现，Luna／原Key和原选回答模型保持；每请求至多2搜索、3结果／搜索、6累计结果、1500字符／结果。原普通问题完整scope、最多两物理检索、首笔费用未知或>10000微USD不补查继续有效。Android授权v4、Mac Luna／Exa v2拒绝旧native授权，缓存绑定版本。
- 真实短题已通过原生Mac scoped owner与Android实际wire／流解码／Room重开；Android真实回执暴露的缺口标题解析已修，不升级为已核实。3检索＋2回答、检索回报USD0.029555；¥1账本占用¥0.63644含未知回答费预留，非后台实扣。详见[本轮报告](verification/2026-11-02-exa-companion/REPORT.md)。
- RC六模型名义CNY、别名及旧账修补沿用；OpenRouter独立USD，未知不记零、不二次换汇。Exa限量不是人民币总费用硬上限。
- 历史资料→自动记忆沿用DeepSeek Flash→GLM-5.3 Flash→Qwen3.6 Flash及手动内容保护；旧格式解析和恢复优先级已修，不等于正式入库。

## In Progress / Verification Gaps

- 手机实体业务UI及Mac已装App内实际发送、原复杂问题／完整材料矩阵未验；代表短题不冒充全量验收。原截图精确Attempt仍未定位。
- 两端连续自然周期及Mac原FAILED／RESPONSE_FORMAT／retry1条目恢复入库仍缺证。正常窗口2026-11-03 01:18起，睡眠可延后；ai-2回读PAUSED，不恢复或创建替代定时跟进。原private baseline/events保留，当前私有观察副本只校正Mac版本，不force-run／改时间／写正式库。
- 原Opus EOF/499发起方、既有UNKNOWN与跨端partial费用、后台逐笔扣费、线上Google／Supabase／网关、Windows／公证等历史未验仍有效，原日期／适用版本见历史归档。

## Tests

- 2026-11-18 GPT-6.2 Sol 本地验证。Android JDK 21 单测 1,603 通过、12 跳过，开发 APK 编译和模型档案读回通过。Mac Rust 486 通过、46 忽略，Node 568 通过、12 跳过，前端 lint、typecheck、静态构建与 macOS 原生开发构建通过。协议与领域合同 45 项通过。只读目录查询未发起推理。

- 2026-11-14 12:04 +08:00 Hydrogen 工作树验证。Android JDK 21 单测 1,602 通过／12 跳过，Debug APK 通过。Mac Rust 486 通过／46 忽略，Node 568 通过／12 跳过，ARM64 开发构建通过。模型合同及治理 45 项通过。底层 HEAD 与本机缓存 upstream 一致，新增改动仅在本地。命令、产物哈希和适用边界见[接入记录](verification/2026-11-14-gemini-hydrogen/REPORT.md)。

- 最终1.0.39：Android1602 PASS／12skip、Release／Lint／原签名通过；Rust485 PASS／46ignored；Node567 PASS／12skip，Desktop lint／typecheck／inventory／build／原签名DMG通过。
- 模型维护16测试、23聊天／1专项／7凭据组一致，领域／治理29测试通过；旧真实回执定向离线两端各1通过。[分层回执](verification/2026-11-02-exa-companion/verification.json)。无connected类测试或主设备UI自动化。

## Next

1. 双端 1.0.41 覆盖已完成，旧版本安装证据留作历史；后续真实业务问题沿现有来源／费用回执定向核验，不重复收费或恢复定时跟进。
2. 自然到期时只读核验原失败恢复、真实Memory修订与手动保护、连续第二周期；不可用安装／NO_CANDIDATE冒充成功。
3. 对新的真实业务失败定向复用回执定位，保留UNKNOWN，不擅自扩大模型／材料／付费矩阵。

## History / Safety

完整R01–R20及全部边界已在[本轮报告](verification/2026-11-02-exa-companion/REPORT.md)逐项确认。上轮1.0.38报告仍是其准确版本证据。旧长入口无损保留：项目状态归档（本地）、交接归档（本地）。全部WIP／未跟踪／忽略／独立1.0.37工作树保留，不reset／clean／stash。

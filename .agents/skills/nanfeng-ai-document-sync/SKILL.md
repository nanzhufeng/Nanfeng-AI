---
name: nanfeng-ai-document-sync
description: Synchronize Nanfeng AI contracts, handoff, development archive, decisions, and reusable workflows from repository evidence. Use for retrospectives, documentation conflicts, handoffs, or formal consolidation.
---

# 南枫 AI 文档同步

共同入口：[长期规则](../../../AGENTS.md)、[当前交接](../../../docs/CURRENT_HANDOFF.md)、[架构与证据索引](../../../docs/南枫AI完整开发档案.md)。当前用户授权及安全边界优先；本 Skill 不自动授权部署、安装或真实数据操作。

## 输入与全库复盘

1. 读取 AGENTS、当前交接、目标文档、HEAD／工作树与用户范围；文档任务默认不改业务代码、配置值、schema 或测试逻辑。旧版本由 Git 或快照保全。
2. 完整复盘先运行本 Skill 的 `scripts/inventory_project.py --repo <仓库绝对路径> --output <证据目录>`，生成全部跟踪文件 hash／类型／规模和完整可达 Git 历史统计；输出不复制正文、密钥或 raw payload。
3. 按代码、配置、测试、协议、服务、交付工具、文档／历史证据分组检查。大文件先符号／关键字定位；二进制默认只看身份，视觉验收另立范围。全量机器扫描不等于逐行语义审核，报告必须诚实写覆盖层级。
4. 核查组合根、真实入口、持久化／迁移、发送／导入链、凭据、IPC、后台／部署配置及相关测试。提交主题／日期只证明仓库记录，不补作者动机或实际工作时长。

## 写入唯一位置

5. 当前行为 → 一份领域合同；动态 checkpoint／构建／设备／测试 → 当前交接；重要取舍与依据 → decision-log；项目目标、架构、目录、模块、数据流、过程、坑、验证、部署、问题与路线 → 完整开发档案；可迁移方法 → 可迁移开发经验。
6. AGENTS 只留每次开发必须遵守的长期边界；五类 Skill 各自保存重复流程，同步 agents/openai.yaml。不把版本、测试数、设备事实、hash和下一任务写入规则，不直接编辑生成记忆。
7. 每项结论附代码／测试／文档／commit；分清事实、历史记录、判断、待验证计划。外部模型能力／价格如果未在线核验，只写“仓库声明”，不把旧链接当当前官方事实。
8. 发现冲突写表：旧来源／断言、当前代码或验证、裁决、是否修改源文档。历史正文保留可追溯性，不把过去未完成悄悄写成过去已完成。
9. 经验分为通用原则、项目专属、不可直接照搬；写明适用条件和移植前验证，不复制整个项目规章给其他软件。

## 验证与输出

10. 运行链接／frontmatter／Skill UI 元数据／git diff --check。按受影响文档选择已有合同测试；不为文档改动重打所有应用。借用同源码点先前测试时写明执行阶段并核对 hash，未执行层不能写通过。
11. 回读全部修改文件及差异，核对业务源码／配置／schema／测试未变；确认未跟踪用户资产未纳入或删除。全库 inventory 是生成时快照，不要求它自包含新生成文件。
12. 输出新增／修改文件、证据与冲突摘要、仍未确认项和恢复基线。只有用户要求时才提交／发布；流程审计软警告不得驱动范围外规则修复。

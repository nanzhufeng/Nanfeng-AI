---
name: nanfeng-ai-debugging
description: Diagnose Nanfeng AI Android, Desktop, storage, Provider, import, build, or service failures from reproducible evidence. Use for incorrect behavior, regressions, and ambiguous status.
---

# 南枫 AI 排错

共同入口：[长期规则](../../../AGENTS.md)、[当前交接](../../../docs/CURRENT_HANDOFF.md)、[架构与证据索引](../../../docs/南枫AI完整开发档案.md)。当前用户授权及安全边界优先；本 Skill 不自动授权部署、安装或真实数据操作。

## 输入与分流

1. 记录复现入口、平台／构建、期望、实际和最近变化；读取 AGENTS、交接与对应当前合同。保留用户数据，不以清库／重装作为诊断前提。
2. 分类为产品行为、迁移／数据、IPC／权限、凭据、Provider／流式终态、导入／资产、构建、环境／夹具或陈旧断言。不能只修提示语来掩盖持久状态错误。

## 定位与最小实验

3. 从 UI 到真实 owner 追踪数据和状态：Android Activity／ViewModel／Room／Executor；Desktop ESM／Tauri command／Rust／SQLite。核对当前运行包是否包含正在检查的源码。
4. 每次写一个假设、最小只读或隔离复现、成功／失败判据。先取计数／状态／安全错误码，再读必要源码片段；不输出正文、原始 Provider payload 或秘密。
5. 流式异常沿 Attempt → Provider 事件 → runtime → 完整回复／终态事务 → UI 回读检查；超时与结果未知不能自动重发。费用异常区分目录上限、产品预算、Provider usage 和本机估算。
6. 凭据异常先看 metadata presence、拒绝／不可用状态与签名身份；实际发送／测试连接才读 secret。不删现有密钥来试错，不后台循环弹授权。
7. 导入／存储异常检查 source identity、资产 hash、occurrence、活动引用、物理字节及 journal；迁移用隔离库复现关闭重开／中断，不注入用户库。
8. UI 问题检查状态、布局几何、输入消费、焦点、原生层级与真实包；浏览器样式通过不覆盖原生红灯。多图先联系表，再查疑点。
9. 环境失败核对解释器、JBR、FTS5、fixture、测试 skip、CSP／ACL 和构建资产依赖。禁止为了静态测试变绿恢复已废弃行为。

## 输出与停止条件

10. 只在证据收敛后下根因结论；否则标明假设和缺口。未经修复授权只报告定位，不修改业务代码。
11. 获准修复后做最小变化，在原失败边界复验，再运行关联回归。交付根因、影响、修复证据、未验层和恢复路径；外部授权不足时完成本地分析并说明具体缺口。

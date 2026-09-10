---
name: nanfeng-ai-development
description: Implement Nanfeng AI Android, Desktop, protocol, or service changes using existing owners and explicit data boundaries. Use for feature work, persistence changes, and integration work.
---

# 南枫 AI 开发

共同入口：[长期规则](../../../AGENTS.md)、[当前交接](../../../docs/CURRENT_HANDOFF.md)、[架构与证据索引](../../../docs/南枫AI完整开发档案.md)。当前用户授权及安全边界优先；本 Skill 不自动授权部署、安装或真实数据操作。

## 输入与定位

1. 读取项目 AGENTS、当前工作树和当前交接；按完整开发档案定位本次平台、真实入口、owner 和一份现行合同。先确认用户授权范围、输出及受影响数据。
2. 盘点差异统计和新增文件，不覆盖范围外工作。界面相似不代表两端共享实现；区分 Android、Desktop、协议、Supabase 与独立网关。

## 执行

3. 先定义一个可观察的垂直增量：入口 → 状态 → 业务执行 → 持久化／失败 → 用户回读。复用既有入口和 owner，不因实现方便新增常驻按钮。
4. Android 通过 ViewModel／UseCase 接 data／ai／后台；Desktop 通过 ESM → Tauri command → Rust owner。IPC 同时检查 invoke、注册、permission、参数验证与返回投影，前端不另存第二份业务真相。
5. 结构变化才新增连续迁移和 schema；行为变化覆盖事务、并发和重开。导入／恢复维护严格格式、暂存、资产引用、原子提交与 receipt；禁止静默丢字段或猜配来源。
6. 模型／服务变更贯通目录、凭据状态、实际接收方、选择／材料桥、预算、流终态、归因、费用、错误和 UI。普通发送授权绑定准确材料，不添加重复确认，也不以 fallback 扩张接收方。
7. 保持凭据与无正文审计边界；系统不可用／拒绝与未保存分开。长任务由持久 owner 保存状态，不绑定页面协程；UNKNOWN 不隐式重发。
8. 外部配置、真实数据迁移、账号／服务部署和主设备安装仅在明确授权范围执行。浏览器 fixture 不能冒充原生数据结果。

## 输出与完成门

9. 调用测试交付 Skill 选择最小充分验证；失败先分类，新增失败未闭环不能写完成。记录源码／行为测试／构建／原生／服务的不同结论。
10. 调用文档同步 Skill：行为更新合同，重要取舍写决策，动态证据写交接。交付文件、验证结果、未验项和可回滚基线。

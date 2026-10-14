# 最新事实粒度／风险修复双端保数据覆盖（2026-10-14）

用户再次明确授权覆盖安装，已对OPPO PKH120与Mac安装当前工作树正式1.0.29。包括编号报告逐条事实核验、五类双端遗漏修复，以及Android此前尚未安装的Gemini Flash原生搜索插件修复。模型路由、原选模型回答和费用合并方式沿用当前合同。本次未commit／push／发布Release。

| 平台 | 安装与读回 | 数据保留 |
|---|---|---|
| Android | 2026-10-14 10:49:19 +0800；com.nanzhufeng.ai／1.0.29／93、非Debug、原证书；临时APK推送后pm install -r --user 0返回Success，拉回已装APK与冻结候选字节完全一致；临时APK已清理 | UID10611、首次安装时间、dataDir及CE／DE inode均不变；未卸载／清数据／部署Debug／运行connected测试 |
| Mac | 2026-10-14 10:48:51 +0800；com.nanzhufeng.ai.desktop／1.0.29，原Apple Development证书；严格验签后staged精确替换，安装4个bundle文件与冻结候选完全一致，DMG封装验证通过 | 1595个数据文件／6069511187字节逐项SHA-256均一致；新增、删除、变化均0；覆盖前后未启动正式包 |

旧新版本号相同，已用Android更新时刻及APK字节变化、Mac二进制变化与候选逐文件一致确认实际更新，不以版本号判断。Mac原bundle保留在/Applications/.Nanfeng-AI-before-cover-20261014-22abc24e8a06.app。

## 冻结产物与源码

- 冻结APK：/tmp/nanfeng-fact-cover-20261014-b005575e9f/formal/Nanfeng-AI-1.0.29.apk；SHA-256 c637e365a70de7d631fd107491a8e78d5e301d92f6148872c379add0a2080470；证书SHA-256 6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8。
- 冻结Mac app：/tmp/nanfeng-fact-cover-20261014-b005575e9f/formal/南枫 AI Desktop.app；证书SHA-256 e427918fedfc3842938551595bd6d3cd77666fc3a3a2ccdb67a43bc26684e7f3。
- 冻结DMG：/tmp/nanfeng-fact-cover-20261014-b005575e9f/formal/Nanfeng-AI-macOS-1.0.29.dmg；SHA-256 6c12b1996a5655c75c299197e9cb6bd36560e556fc882a807d0be81280824779。
- 本次Android lintRelease／assembleRelease通过，使用既有正式签名配置、6GB JVM堆／2 workers；Mac lint／typecheck、Release原证书app与DMG构建通过。签名未迁移，未导出私钥或更改Keychain访问控制。
- 复用紧邻风险修复的完整JVM1495通过／10 opt-in跳过、Rust443通过／34 opt-in忽略，8个已验owner hash核对一致；本次没有重复该完整测试。相关Desktop合同21通过／0跳过为前阶段结果；前端94个源码／工具文件相对于此前Node551通过／12skip阶段hash不变，Node全套未重跑。
- 冻结1152个源码／配置／静态资产文件，构建、安装与收尾hash无漂移；HEAD dafc64d9b33661dc4f792fd1434ff1ddca9cdfa3不是当前WIP完整快照。完整冻结清单／数据逐文件hash在私有目录，正文不含用户数据。

## 验收边界

本轮只完成正式签名覆盖及数据核对，0新增Provider请求／0新增服务费。未启动正式软件做UI、迁移或真实联网业务验收；此前[事实粒度验证](../2026-10-14-attachment-fact-granularity/REPORT.md)与[双端风险修复](../2026-10-14-attachment-fact-risk-review/REPORT.md)中的真实Provider逐项返回缺口仍未闭合，不将离线66项计划或覆盖安装变成66项全部核实。

Android仅核对可读的安装／目录元数据，未读取或绕过权限获取正式私有数据库；这些指标不能当完整数据库哈希证明。Mac数据文件不变不能替代启动后的业务验收。此前历史报告的“未安装”仍指各自当时源码点；当前正式Android与Mac均已包含最新优化及风险修复。

[安全回执](receipts.json)／[已验owner hash](verified-owner-hashes.json)。私有before、日志、冻结完整源码hash及数据指纹在/tmp/nanfeng-fact-cover-20261014-b005575e9f；正式数据根未写入测试夹具。

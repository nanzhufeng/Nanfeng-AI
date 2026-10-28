# v1.0.32 GitHub 正式发布

- 构建源码：`7fc45689117302c01fbb73d0e2eddcaf2014d25d`，双端可执行文件均实读到该提交。Android 96／1.0.32，macOS 1.0.32；核心新增 owner、回归、合同及最近脱敏验收材料已纳入该提交。
- 发布状态：[v1.0.32](https://github.com/nanzhufeng/Nanfeng-AI/releases/tag/v1.0.32) 已正式发布，非草稿、非预发布，标签准确指向上述构建源码。两个安装包的名称、大小、GitHub SHA256 与公开匿名下载字节均一致，见[远端回执](remote.json)。源码提交的三个 GitHub Actions 检查全部 SUCCESS。
- [检查汇总](checks.json)与[安装包／截图字节清单](artifacts.json)。本轮 Android 1548 PASS／10 skip；Rust 469 PASS／38 ignored；Node 563 PASS／12 skip；维护45及114合同／40域、23聊天部署／1独立／7凭据组门通过，双端正式构建与验签通过。
- 原 Rust 云同步失败定位到测试 TCP mock 将一次 read 当完整请求。仅修测试读满 Content-Length 的逻辑；生产同步行为不变。历史红灯记录保留，本轮完整默认 Rust 回归已通过；不能将其当作真实云端验收。
- Android 精确正式 APK 在本任务独立 Android 15 模拟器安装、拉回哈希一致；Mac 冻结 DMG 只读挂载后复制，4份 bundle 内容与挂载源逐一匹配，原证书验签后通过 LaunchServices 正常启动。README 双端各两张当前独立页面图，空数据／未配置 Key，不代表真实 Provider 业务。两个截图 manifest 在上述构建提交为 HEAD 时通过校验。
- README／发布回执为后续纯文档与图片提交；不重建、不重签、不移动构建源码。Release 仅提供 APK 和 DMG，内部保留哈希，不上传辅助校验或图片附件。
- 本次无新付费调用、无主设备覆盖、无 `/Applications` 覆盖。先前 Terra 真实业务证据沿用[原报告](../2026-10-28-terra-live-chain/REPORT.md)，不刷新测试日期、不关闭 Desktop 全模型及网页来源等剩余缺口；旧 UNKNOWN 不重发。
- 本地历史原始日志／patch／before／载荷材料未上传；原文件保留。公开归档中的本机用户目录已用 `${USER_HOME}` 脱敏，原始私有副本保留在忽略目录。
- 本任务创建的隔离模拟器与 AVD 已清理，Mac 隔离窗口已关闭；未操作其他模拟器或主设备。

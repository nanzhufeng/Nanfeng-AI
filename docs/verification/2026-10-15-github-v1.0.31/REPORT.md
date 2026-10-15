# v1.0.31 Android／macOS 正式发布

构建源码：`fe504fec14b53b7bd2093d5b9929e99248695705`。在已完成代码checkpoint基础上发布当前联网／协议／设置修复，不重复收费测试或已完成全库复盘。候选包与四张预览均已冻结；[正式Release](https://github.com/nanzhufeng/Nanfeng-AI/releases/tag/v1.0.31)三附件上传并从公开地址重新下载，大小/SHA-256全部一致；准确身份见[结构化回执](release-receipt.json)。

## 本轮增量与验证

- Android版本1.0.31/code95，非Debug，证书SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`；正式APK构建成功。隔离模拟器安装后拉回实际APK，精确hash一致；正常新对话与设置采图，开发与诊断入口隐藏。没有主设备安装、卸载或清数据。
- Mac版本1.0.31，沿用原Apple Development证书 `e427918fedfc3842938551595bd6d3cd77666fc3a3a2ccdb67a43bc26684e7f3`；原生app/DMG构建、严格验签和DMG校验成功，未公证。最终DMG只读挂载并提取app，实际运行可执行文件hash与提取文件一致；独立空数据根冷启及设置实看通过，不以既有正式数据代替首次启动验收。
- 首次空数据验收发现 `entry?.workspaceId === workspaceId` 在两边均undefined时仍进入解引用null分支；修正为先确认entry存在。新增真实refresh函数回归先见TypeError红灯，再验证空工作区完成目录／全局默认读取且不读取不存在会话；已有短时恢复／异步导航用例保留。同步[启动合同](../../CONVERSATION_APP_ENTRY_CONTRACT.md)。新用例初次错误要求会话级selection，已改为核对空数据应读取的catalog/global default，不改变生产路径。
- 修复后重跑Node：567总／555 PASS／12 skip／0失败；typecheck、lint通过。双端从最终源码重新构建，签名/版本/hash重新冻结；早期候选与预览不用于交付。
- 合同114/40、模型21+1/7一致性门通过。与checkpoint的1222输入逐项比较，只有双端版本文件、上述JS空值判断与其测试变化；Android JVM1525/10skip、Release Lint、Rust457/36ignored及维护/协议/Supabase/Go复用[同日最终回归](../2026-10-15-code-checkpoint/REPORT.md)，不刷新原执行时间。CI结果另记，不替代正式签名包或业务验收。

## 截图和冻结

每个平台两张当前正常界面图，全部与最终候选hash、版本、Git源码、采集时间绑定；两份本地manifest经 `verify_release_evidence_manifest.py` 校验通过。公开仓库只纳入已检查无私人内容的四张独立PNG与安全摘要；Mac移除采集器64px顶栏，截图只做等比缩放。原始截图、候选、回拉包和manifest保留本机，不纳Git。README引用仓库相对路径，Release不上传图片。

| 附件 | SHA-256 |
|---|---|
| Nanfeng-AI-Android-1.0.31.apk | `87fe3143668aac53a8f24b8f40582e0007fbc39099f196a44235c17dbd11531d` |
| Nanfeng-AI-macOS-1.0.31.dmg | `7a847f47e1bf823022c00e1253f627b13eade64be8eb2588b0cc870129284177` |

原始323份未跟踪证据逐项hash保留，未删除或纳Git；待推送新增行凭据/私有路径检查无命中。只推main与正式版本标签，不推私有checkpoint标签。

## 发布状态

- 正式发布于 `2026-10-15T11:16:31Z`，非草稿/非Pre-release；标签源码 `1ffacaf36bd5a5b29c6e9f45ceb7933a1ec1e5e4`，与构建源码不同部分只含文档/预览。APK与Mac可执行文件均检出构建源码的既有12字符标记 `fe504fec14b5`。
- 三附件远端名称、字节数、GitHub SHA-256和公开下载hash均精确一致；无截图附件。
- [GitHub源码验证](https://github.com/nanzhufeng/Nanfeng-AI/actions/runs/38725359765)：Android compile-only、Desktop source checks、Protocol/Supabase contracts/gateway三项全部SUCCESS；该run对应正式标签提交。
- 大附件gh客户端停滞后，先停止精确本轮进程并读回草稿状态，再用现有本机代理/HTTP1客户端补传同一冻结文件；未改系统代理/签名/候选包，也未重建草稿。
- 当前回执为纯文档薄提交；应用输入与已发布包保持不变，使用skip-ci避免重复源码构建，已完成的标签CI结果保留。

## 保留的验收边界

本轮没有新增付费Provider请求或主设备覆盖。RC Gemini Pro旧404/中断未知及未知费用不重发；更广全部模型×附件格式、原生picker/生命周期/媒体、Google/Supabase/网关线上部署、Windows与公证等缺口沿原报告保留。首次启动和所示界面通过，不代表所有业务组合通过。

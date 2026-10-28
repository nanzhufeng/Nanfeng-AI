# 2026-10-21 Mercury 图片识读修复

基线：main `dd3c8e89921feee8be5bc0185bf3174e6fed4bae`，保留既有WIP。本轮是截图0.0秒附件被本地拒绝的修复，不重做已完成的Mercury菜单、Argon简称或前次覆盖。不提交、不推送、不修改系统代理或用户消息。当前分层结果见[checks.json](checks.json)。

## 根因和实现

1. Mercury仍是文字模型。Android附件桥在实际网络之前拒绝图片；Mac原投影会向文字模型直接携带image_url，两端缺少显式的读图阶段。
2. 当前JPEG／PNG／WebP／GIF由固定Haiku 5.5 · OpenRouter识读。发送前显示接收方、Mercury最终回答和两段计费；材料指纹绑定当前附件和准确辅助接收方。缺授权不外发，不伪造Mercury原生视觉能力，不运行时换最终模型。
3. 识读使用完整本次图片，读到的文字／表格／图表关系作为不可信材料交给Mercury；原图保留本机，Mercury wire不带原图。取消、超时、截断、空材料、身份缺失／错误和持久化失败停止后续回答。Android重算实际材料预算。Mac历史图片不重新外发，保留已保存文字上下文。
4. 联网分支使用实际识读文字制定事项，识读说明不混入查询。既有RC Luna检索独立保留。识读与回答复用一根Attempt和独立物理调用账本；识读不等于已联网，未知费用不当零。
5. Mac请求库原关闭system-proxy，未读取已启用的系统HTTP／HTTPS代理。直连最小请求返回403／region_restricted；只读Key元数据200，不能归因于Key无效。启用system-proxy后，模型回环接口和显式localhost云验收保持直连；不更改系统网络、代理配置或节点。

生产owner：Android的VisualAttachmentReadingPolicy、NormalChatRoutingSnapshot、UniversalChatAttachmentBridge、NormalChatOpenRouterExecutor、AttachmentSearchInventory；Mac的visual-attachment-reading.mjs、submit／retry普通聊天边界、attachment_web_search_execution_v1.rs、desktop_ordinary_chat_v1.rs及Cargo依赖配置。唯一行为正文见[C12](../../C12_MODEL_NETWORK_PARITY_CONTRACT.md)，模型例外为OR_MERCURY_IMAGE_READER。

## 验证

| 层 | 结果 | 边界 |
| --- | --- | --- |
| 原始红灯 | 新的Mercury图片准备测试在旧附件桥失败 | 不是只改错误文案 |
| Android JVM | 1539通过、10跳过、0失败 | 包含图片准备、实际Executor＋Room、无授权零调用、读图后原模型回答、联网分支、失败停止与既有全量回归 |
| Node | 560通过、12跳过、0失败 | 包含实际Composer渲染的接收方／费用提示和本地模块完整打包 |
| Rust | 464通过、38忽略、0失败 | 包含实际事项coordinator、精确授权／身份、失败停止、回环与既有协议／存储回归；有费用测试为显式opt-in |
| 合同／模型／协议 | 合同治理、冲突检查、模型维护门及三组golden通过 | 离线一致性，不替代真实业务 |
| 真实读图＋回答 | 完成 | 仓库公开合成订单图；Haiku准确返回身份后交给Mercury，实付40.80／优惠26.80均正确，Mercury准确身份，SQLite重开COMPLETED |
| 真实图片＋网页查询 | UNKNOWN / NETWORK | 独立公开Python主题图，未自动重发；不能关闭该层缺口 |
| Android正式候选 | 1.0.31/code95，原正式证书，非Debug | 精确APK hash见checks；未覆盖主设备 |
| Mac正式候选 | 1.0.31原Apple Development证书；codesign strict及DMG校验通过 | 精确hash见checks；未覆盖正式入口 |
| 主设备／原会话 | NOT RUN | 仅检测到模拟器；未点原失败会话重试、未卸载或清数据 |

公开订单实测两次最终计量为Haiku 219、Mercury 218微USD，合计0.000437 USD。账本另有各自被final对账的pending快照：它们不是另外两次收费请求。初版验收脚本误把四个账本快照当四次调用，所有实际金额／身份／重开检查已通过而最后计数断言失败；已修正只计FinalMeasured，并离线回放原回执通过，没有为脚本计数重发付费请求。

保留独立的403诊断、一次显式代理对照NETWORK未知和图片联网NETWORK未知，不将未返回费用补成0。403来自Mac当时的直接出口，不说明该模型在所有设备／出口都不可用，也不把换模型当作网络修复。

## 构建和证据边界

Android曾遇PackageAndroidArtifact增量打包失败；保留失败日志，一次assembleRelease复核成功。未确认初次打包异常的具体内部原因，不改凭据或清除用户数据处理。macOS重新按最终system-proxy输入构建；两端版本号沿用1.0.31，必须按候选hash区分旧安装。

原始工作树patch、基准／最终文件hash、JUnit、构建／签名日志及公开实测回执保存在本机私有证据目录（当前本机指针nfai-mercury-vision-path.txt）。仓库只保存安全汇总，不保存凭据、用户图片、原始Provider payload或私有路径。

## 待确认

- 手机当前未连接；候选包安装、外屏提示实际布局及原图片／原网络完整业务尚未验收。
- 真实图片联网查询本轮NETWORK未知，仍需后续独立明确验证；原未知请求不自动重发。
- 识读长图／复杂图表准确率、扫描PDF和音视频不因本次PNG样本自动验收；本轮辅助范围只含已列图片MIME。
- 无新Git提交、Release或GitHub上传；前次主设备覆盖事实保留原报告，不冒充本次修复已安装。

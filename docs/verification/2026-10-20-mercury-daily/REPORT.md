# Mercury 3 日常入口与 Android Argon 短名

> 2026-10-21复查订正：本报告的完整归因字串测试没有覆盖实际Android回复页脚，Composer短名曾同时缩短该页脚；现已直接复现并修复。缓存精确身份、API ID读名与付费矩阵计数／筛选遗漏一并修复，见[复查报告](../2026-10-21-mercury-risk-review/REPORT.md)。本页原日期／hash／候选保持历史事实。

核验时间：2026-10-20T23:38:16+08:00。基线 `dd3c8e89921feee8be5bc0185bf3174e6fed4bae` 上的 WORKTREE；保留上轮 Argon 诊断补丁和全部 326 个既有未跟踪文件，不提交／推送／发布。

## 实现与依据

- 双端日常档在 Luna 后加入 Mercury 3：稳定 preset `MERCURY_3`，OpenRouter 精确 API ID `inception/mercury-3`。复用原 OpenRouter 配置与 Key、请求适配器、流式／来源解析、费用及历史持久化 owner；Auto 和后台默认不变。对应 Android `ChatModelRouting.kt`、`ModelService.kt`、`RegistryVerification.kt`、`ModelRegistry.kt`、`ResolvedModel.kt`；Desktop `desktop_model_service_v1.rs`、`chat-shell.mjs` 和设置投影；维护 manifest／生成矩阵同步。
- 2026-10-20 的[官方目录](https://openrouter.ai/api/v1/models/inception/mercury-3/endpoints)与[模型页面](https://openrouter.ai/inception/mercury-3/)确认 text→text、1000000 context／131072 输出上限、tools／structured output／reasoning；原始单模型公开 fixture 位于 `protocol/fixtures/mercury-3-20261020.json`。Android bundled profile 与实时 registry 映射均保持精确身份，不映射到 Luna 或近似版本。当前限时公开 token 价格为每百万 USD 0.10 输入／0.35 输出／0.01 缓存读取，三份本机估算按日期版本同步；Provider 结算优先，未来优惠变化须重新核实。
- 文本文件／Markdown／Office 正文／ZIP 文档及可读 PDF 沿用本机提取；本轮新增两文件完整内容准备测试。非原生图片／音视频不冒充 Mercury 能力、不隐式换模型处理。
- Android 共用 Composer 短名 owner 将 Gemini 4 Argon 显示为 `4 Argon`，普通／临时输入框共同消费；设置、菜单、Assistant 历史归因保留完整名字，Desktop 保持完整名称。现有隐藏“功能审阅”记录同步，不恢复正式版开发入口或新增常驻按钮。

## 验证

| 层次 | 结果 | 边界 |
|---|---|---|
| 红→绿 | 新增 Node 两项先失败，Android 两项先失败；最终新增桥接／精确身份／短名／费用与 native stream 来源回归通过 | 初次测试编译中的测试字段拼写已改正，不算业务红信号 |
| Android | 全套 1533 PASS／10 skip，0失败错误；正式构建、Release Lint、原签名通过 | JVM／正式候选，不冒充主设备业务验收 |
| Desktop | Node 557 PASS／12 skip；Rust 459 PASS／36 ignored；lint／typecheck、inventory 通过；原 Apple Development 签名 bundle／DMG 校验通过 | ignored 真实服务不在离线全套中自动外发 |
| 合同 | 模型22／OCR1／凭据7；16协议测试；114合同／40领域治理与一致性通过 | 静态一致性不替代运行 |
| 真实普通回答 | 生产 Rust streaming transport，约 4.826 秒；54可见字符，返回 `inception/mercury-3`，用量及权威费用收到 | 公开短文本，非主设备 |
| 真实双文本联网 | 生产 `execute_cached_core`＋原 streaming transport；Go／Firefox 两项 2/2，结构化来源、原 Mercury 非空最终回答、返回身份匹配 | 文本内容已在调用前形成，非原生文件导入 UI；完整请求体／公开材料保留私有证据 |
| 原生 UI | Mac 锁屏阻止 Cua 操作；已验签正式候选在 emulator-5594 保数据安装 Success | 外屏视觉、点击／重开及新模型主设备真实请求 NOT RUN |

真实来源包括 Go 官方发布页和 Firefox／MDN 官方仓库内容，具体 structured citations、物理检索用量与费用见 [摘要](verification.json)。普通回答完成不作为联网成功依据。前两次独立公开样例分别遇 HTTP429／TLS connect 和 TLS connect／0覆盖；保留未完成与未知费用，没有重发原 Attempt，也不宣称外部限流／TLS 已根治。随后第三个独立 Go／Firefox 样例真实通过。

## 交付与未确认

- Android 候选仍为1.0.31/code95，APK SHA256 `818d77c171dd786de2f226bf3a974a7a9a5efce7832bbd8b5018d00055955f8b`；Mac DMG SHA256 `51347ad7fbc39b8c389b808aee69dafd755ab048d33dc6ed3e2526bdd1621ba0`。与公开 GitHub 同版本资产、主设备现装诊断包分别识别，不冒称已覆盖。
- 已冻结 1001 份源码／配置／测试输入并核对0漂移；当前增量输入见 [source-hashes.json](source-hashes.json)。全部构建日志、完整证书核对、候选与公开服务探针仅在本机私有目录，由 `/tmp/nfai-mercury-task-path.txt` 定位；无密钥或用户原材料入库。
- 本轮没有覆盖 OPPO／Mac 正式入口，没有公开发布，也没有任何 connected Android 仪器测试、主设备 UI dump、清数据或卸载。隔离镜像进程已精确退出。
- 后续仍须在用户解锁后检查原生菜单与外屏文字；主设备安装需按原签名正式包保数据覆盖。历史网络失败和其他模型缺口保持，不扩展本次验证。

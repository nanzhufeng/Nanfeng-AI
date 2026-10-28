# Mercury／Argon 增量遗漏风险复查

核验时间：2026-10-21T00:37:55+08:00。针对上轮模型新增、显示、缓存／目录、真实发送入口、附件／联网／终态／计量和相邻历史读取路径复查；不是全库逐行语义审计。基线仍为 `dd3c8e89921feee8be5bc0185bf3174e6fed4bae` 上的 WORKTREE，保留原Argon诊断与Mercury增量。

## 已证实并修复

| 风险 | 触发及影响 | 修复／依据 |
|---|---|---|
| P1：冷启动缓存覆盖精确身份 | 无OpenRouter registry时，缓存profile的旧／错误ID可覆盖已验证seed；模拟Mercury或Argon缓存为Luna，`UnifiedModelResolver`确实返回Luna ID而保留原选标签 | `AndroidModelProfileDirectory.mergeCachedProfiles`对固定OpenRouter ID不匹配缓存拒绝采用；有效健康／更新时间保留，官方直连显式别名规则不变。`ModelProfileUpgradeTest`从红转绿 |
| P2：Argon简称影响回复页脚 | 实际UI `assistantFooterModelName`调用Composer短名函数，上轮只测归因函数，漏掉此显示副作用 | 页脚保留Gemini 4 Argon，Composer继续4 Argon；其他短名／RC原样。`MercuryModelIntegrationTest`直接测真实页脚helper，从4 Argon红灯转绿；设置／记录不改 |
| P2：旧记录显示API ID | 没有displayName快照的Mercury记录在Android标签或Desktop `transcriptMetadata`显示`inception/mercury-3` | 双端精确显示映射为Mercury 3，保留冻结人工名称与原始ID；Android及Node公开投影回归先失败后通过 |
| P2：全量验收计数／筛选错误 | 原live矩阵只发送chat_selectable，期望数却含退役／OCR；空或部分未知筛选也可零请求／缺项通过 | 共用`public_matrix_presets`合法当前聊天集合驱动外发和计数；未知、空、退役、OCR筛选在Key读取／网络前失败。独立离线测试与维护manifest集合比对；没有为回归触发付费矩阵 |

R1并非上游已换模型的证据，是可重复的本地缓存错误状态；修复的是精确身份边界。R2保留历史数据库内容，纠正的是阅读页脚的显示范围。R3只投影已知精确ID，不改写历史记录。R4只改显式opt-in测试harness，不改变生产联网策略。

## 核查与验证

- 上轮模型新增的双端目录、manual／Auto隔离、Android旧目录未解析时的无正文刷新、Mac实际普通发送选择与禁用／缺Key失败关闭路径已复核；没有新增API ID、接收方、参数或重试fallback。旧目录GET失败保留选择并明确不可用，未用别的模型绕过。
- Android定向红灯3项（错误缓存身份、真实Argon页脚、API ID读名），Node历史投影红灯1项，Rust矩阵选择红灯1项；随后转绿。源码形式检查因helper参数名变化产生的陈旧断言已同步，不算业务根因。
- Android最终1545 tests：1535 PASS／10 opt-in skip，0failure／error；正式构建与Release Lint PASS，原正式证书／非Debug PASS。
- Desktop Node558 PASS／12skip；Rust460 PASS／36ignored；lint、typecheck、inventory PASS；原Apple Development签名bundle与DMG校验PASS。
- 模型门22聊天／1OCR／7凭据、16协议测试、114合同／40领域治理与一致性PASS；源码／测试共9文件增量，1001份输入冻结后0漂移，范围外既有文件0漂移。
- 本轮0新Provider调用，不重发UNKNOWN。上轮Mercury普通短答及Go／Firefox双文本2/2仍只是原日期真实服务证据；本轮协议构造、transport／检索执行owner和维护manifest逐文件hash不变，无需重复收费。UI、缓存修复不被这些旧服务记录当作原生验收。

## 文档冲突与未验项

上轮报告中“完整归因保留”的domain测试未覆盖实际UI页脚，因此不能证明输入框简称没有影响阅读页脚；现已按实际helper红灯修复，并在原报告追加订正，原测试日期、源码hash和候选不改写。本轮计数以实际最终结果为准。

- 当前复查候选仍为1.0.31／Android code95，APK SHA256 `71fa25020f36d70abbb6f3db1a4120026c2334855b1677cef1b059a8237419f9`，Mac DMG SHA256 `e4d26640ebfbe8940bdf84d590eef7b430390992e9961c17da3424426489b9b3`；均为已验签新候选，不等同上轮包、公开GitHub包或主设备现装诊断包。
- 本轮没有主设备／Mac正式入口／隔离模拟器安装，没有发布／commit／push；全部既有用户数据不动。候选、完整回归日志与私有基线由 `/tmp/nfai-mercury-review-path.txt` 定位。
- 原生外屏菜单、点击／重开及新模型Android真实外发仍NOT RUN；上轮Mac锁屏导致的UI缺口保持，不能以本轮纯函数／JVM测试补写为实看通过。历史429／TLS与未知费用未关闭；所选文本模型原生媒体限制不变。

安全摘要：[verification.json](verification.json)；本轮增量输入：[source-hashes.json](source-hashes.json)。

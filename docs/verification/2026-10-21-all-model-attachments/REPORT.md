# 2026-10-21 全模型附件排查与统一修复

结论：当前维护目录的 22 个模型已纳入双端准备／执行检查，已修复已证实的附件拒收和混合材料遗漏。Android最终正式包已原签名保数据覆盖并回读相同哈希。**不能宣称全部模型联网已经打通**：真实服务中仍有未知请求、空结果／流内错误、范围未完成及无关来源；旧失败和未知费用保留。

## 证实的根因与修复

1. Mercury专用图片路径使其他文字模型及切换到文字搜索接口的多模态模型仍被本地拒绝。统一为原生接收、本机完整提取、已披露辅助识读，原选模型最终回答。图片／PDF读取方为OpenRouter Haiku 5.5，音视频为OpenRouter Gemini 4 Argon；按实际调用计量。
2. OpenRouter冷启动种子缺少六个现用部署；已据公开目录补足，动态验证目录仍优先。Mac不再按整个Provider品牌推断图片／PDF能力，与Android种子和实际适配器同时判断。
3. 跨Provider读取不能借用原选Key；Android固定读取OpenRouter配置与密钥，Mac读取方与Luna检索grant分离，精确绑定材料和原选模型，写入独立授权版本。
4. Mac原先未序列化音视频；现保留支持类型的完整原件，其他接口经已披露读取。Qwen视频仅支持画面，完整声音／画面经媒体读取；Qwen Flash混合四附件真实成功。
5. Mac把“图片后紧跟PDF文字层”误认为已有PDF投影，遗漏图片；已限定只跳过同一PDF原件，保留逐文件标签并拒绝空辅助请求。RC多模型修复后四个不同标记均返回。
6. PDF不能因有部分文字层就标成完整：扫描页、XObject、矢量图进入完整读取，坏／加密／超限拒绝。原生PDF显式native解析，避免默认外发到额外解析服务。
7. Mac读取方ID、授权说明、附件标签不再混入检索查询；Desktop证据指纹升级v7，旧污染查询不当作新证据。**模型自行生成的版式描述仍可能形成多余查询，真实来源相关性未全面通过，不能以终态COMPLETED替代验收。**

代码依据：Android `AttachmentInputPolicy.kt`、`UniversalChatAttachmentBridge.kt`、`NormalChatOpenRouterExecutor.kt`；Mac `attachment_input_policy_v1.rs`、`attachment_web_search_execution_v1.rs`、`attachment_document_text_v1.rs`、`lib.rs`及ESM授权／Composer；精确路径和输入哈希见files.json。

## 验证与交付

- Android：1541通过／10跳过，0失败；全部维护部署×四种媒体×联网开关准备矩阵、可手选现用部署的实际Executor图片链；完整JVM、Lint Release和正式构建。准备夹具不代表真实媒体解码／服务验收。
- Node：561通过／12跳过；Rust：467通过／38忽略，均0失败。维护manifest驱动矩阵、密钥归属、原选身份、混合路径、终态与取消；Lint／typecheck／模块闭包／合同及协议门单列日志。
- 真实混合四附件（联网关闭）：综合不同阶段，15/22 个模型有完整内容返回证据；其余不算通过。初版512／2048测试预算导致OUTPUT_LIMIT，保留回执；已知失败修正后做新的有界案例，UNKNOWN不重发。初版直接运行Rust测试时cwd错误的22条均为本地前置失败，0网络，不计服务验收。
- 真实图片联网：当前全部部署均做准备／调用检查；RC GPT初版夹具遗漏“附件必须Luna授权”时在发送前阻止，后修正夹具。存在网络未知、服务空回答／流内错误、查询范围不足，以及版式说明引起无关来源，**不作全模型联网通过声明**。部分返回正确日期并附官网链接也不能替代全部来源相关性。
- 旧真实探针在读取前保存的planned数不代表读取后的完整计划；未来脚本已改从coordinator结果及实际A序列回读，未为计数重发收费请求，旧回执不回填通过。
- OPPO最终覆盖：1.0.31/code95，SHA256 `e1824c75a1c02c038030de05aaf0c81c549e0fcd71ec8989916c1221bba02af5`；原正式证书，首次安装时间／appId／数据目录保持，设备端APK哈希相同，临时APK删除。未卸载、未清数据、未点旧消息重试；无仪器测试／UI自动化。应用内部会话数量和主设备实际业务未自动验证。
- Mac：候选构建及签名单独记录；本轮未覆盖主入口，不能把源码或Android安装说成Mac已更新。
- 费用：FINAL_MEASURED累计已知USD 0.707597、CNY 1.126540，混含Provider返回及本地估算；9条已结束计量金额仍未知，不能补零，pending快照不重复计费。原始公开合成回执只保留本机，不进Git。

## 当前模型结果

状态是服务／持久owner回执；COMPLETED不等于图片全部内容或来源相关性通过。后续失败／UNKNOWN不会被另一成功短题抹去。下表联网列均未形成全部范围通过结论。

| 模型 | 四类混合附件最新回执 | 内容核对 | 图片联网最新回执 |
|---|---|---|---|
| `CLAUDE_OPUS_5_5` | COMPLETED | 内容已核对 | UNKNOWN |
| `CLAUDE_SONNET_5_5` | COMPLETED | 内容已核对 | UNKNOWN |
| `CLAUDE_HAIKU_5_5` | COMPLETED | 内容已核对 | PARTIAL |
| `GPT_6_1_SOL` | COMPLETED | 内容已核对 | COMPLETED |
| `GPT_6_LUNA` | COMPLETED | 内容已核对 | COMPLETED |
| `MERCURY_3` | UNKNOWN | 未通过/未确认 | PARTIAL |
| `GEMINI_3_8_FLASH` | COMPLETED | 内容已核对 | PARTIAL |
| `DEEPSEEK_V4_PRO` | UNKNOWN | 未通过/未确认 | PARTIAL |
| `DEEPSEEK_V4_FLASH` | COMPLETED | 内容已核对 | PARTIAL |
| `MUSE_WONDER` | COMPLETED | 内容已核对 | PARTIAL |
| `GEMINI_4_ARGON` | COMPLETED | 内容已核对 | PARTIAL |
| `GLM_5_3` | COMPLETED | 内容已核对 | PARTIAL |
| `GLM_5_3_FLASH` | UNKNOWN | 未通过/未确认 | PARTIAL |
| `QWEN_3_7_PLUS` | UNKNOWN | 未通过/未确认 | PARTIAL |
| `QWEN_3_8_MAX` | UNKNOWN | 未通过/未确认 | COMPLETED |
| `RELAYCORE_OPUS_5_5` | COMPLETED | 内容已核对 | UNKNOWN |
| `RELAYCORE_SONNET_5_5` | UNKNOWN | 未通过/未确认 | UNKNOWN |
| `RELAYCORE_GPT_6_1_SOL` | UNKNOWN | 未通过/未确认 | FAILED |
| `RELAYCORE_GPT_6_ASTRA` | COMPLETED | 内容已核对 | COMPLETED |
| `RELAYCORE_GEMINI_3_8_FLASH` | COMPLETED | 内容已核对 | FAILED |
| `RELAYCORE_GEMINI_3_1_PRO` | COMPLETED | 内容已核对 | COMPLETED |
| `QWEN_3_6_FLASH` | COMPLETED | 内容已核对 | PARTIAL |

## 合同冲突裁决与边界

旧P6D2／C12把识读限定为Mercury图片；P3J旧搜索grant无法授权跨类型读取；架构把原选Provider能力当整个Provider能力。依据实际owner及红绿回归，统一更新P6D2、P3J、C12、模型架构、维护矩阵和manifest，不改写带日期的旧报告。设置“功能审阅”同步统一处理建议，正式版开发入口继续隐藏。

Office仍为完整可读文本层，ZIP仍为有界受支持文档成员，不自动递归或悄悄跳过媒体；任意二进制、加密文件、超限和不支持格式不在“统一路径”保证内。Mac Compare需要逐回答模型授权辅助读取，目前缺少该授权时明确阻止并提示单模型，不静默扩大外发。

## 仍未确认／后续必要工作

- 全部模型、全部媒体、联网开启的真实端到端验收尚未完成；网络UNKNOWN不可自动重发，服务错误未以客户端成功替代。
- 识读观察应在保留完整材料的同时，可靠区分实际主题和生成的版式说明；当前已去掉应用元数据，但真实相关性仍有红灯，不能再叠加按财经／新闻等主题的白名单来规避。
- 主设备附件新发送／外屏实看、Mac主入口覆盖和Compare跨类型辅助读取未验；不在用户设备自动注入测试会话。
- 工作树保留原WIP；本轮未提交、推送或发布GitHub。

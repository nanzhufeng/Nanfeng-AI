# GPT-6.1 Terra 日常接入记录（2026-10-27）

- 用户要求：日常增加 GPT-6.1 Terra，位于 Luna 上方。双端选择顺序为 Terra → Luna → Mercury 3。
- 身份：`GPT_6_1_TERRA` / `logical:daily:gpt-6.1-terra` / `OPENROUTER` / `openai/gpt-6.1-terra`；固定端点与现有 Chat Completions、SSE、附件读取及搜索 owner 复用，不新增 Provider 或 Key 分组。
- 原 Luna 默认、Auto 与后台计划、旧 Terra → Luna 升级映射及历史记录保持原语义。新增逻辑 ID 避免旧选择被误指向新型号。
- 输入档案按 OpenRouter 公共目录核验：文字、图片、文件输入，文字输出；音视频不声明原生支持，沿既有附件准备与准确接收方路径。上下文 1,050,000，最大输出 128,000；实际聊天输出上限仍由既有预算 owner 裁决。能力声明不等于所有真实材料场景已经验收。
- 保留 Provider 默认 reasoning，不为日常新型号新增 High effort。Rust 请求排除目录未声明的 temperature；Android 既有请求无该字段。
- 本机估算 USD / 百万 token：普通输入 1、输出 5、缓存读取 0.05；输入达到 272,000 时分别 2、7.5、0.1。已有 Provider 实际结算优先；缓存写入／搜索调用费不能由 token-only 本机估算冒充最终结算。

来源：[OpenRouter 型号页面](https://openrouter.ai/openai/gpt-6.1-terra)及[公开目录 API](https://openrouter.ai/api/v1/models)，核验日 2026-10-27；冻结的相关字段见 [provider-metadata.json](provider-metadata.json)。

## 验证范围

- 先用日常选择器行为测试确认缺少 Terra 的红灯；同步双端后验证 Terra 紧接 Luna 前且原默认保持。
- 新增精确注册映射、缓存错误身份拒绝、Android 实际 Adapter 请求、Rust transport 请求及两端阶梯费用回归。
- Android 全量 1,557 项：1,547 PASS、10 skip、0 failure/error；正式 assembleRelease PASS。lintRelease PASS。
- Desktop JS 全套 563 PASS / 12 skip；lint、typecheck、前端 build PASS。Rust 新增 Terra 请求测试 PASS；全套排除 live 后为 465 PASS / 1 FAIL / 20 ignored / 21 filtered，并非全绿。
- Rust 失败为既存 `supabase_empty_read_result_means_document_is_not_yet_in_the_cloud`，定向和串行复查均复现；其 owner 文件与本轮开始时 SHA256 相同。mock server 仅一次 TCP read 就断言完整请求体，实际读取缺少 document 字段。保持该红灯，未修改范围外云同步逻辑或测试。
- 文档整理曾把当前合同读取门移出前三行，导致既有 Android 合同测试失败；本轮更新交接时将同一入口移回顶部后全量通过，无新增行为合同。
- 选择器基线红灯在隔离副本重验：chooser/settings 两文件与修改前哈希一致，用同一正确入口测试因缺少 Terra 失败；当前实现绿灯。初次测试入口参数及 Android JVM JSONObject stub 不适用已修正，不将测试设施错误当产品根因。
- 目录门 PASS（23 chat / 1 special / 7 credential groups），协议反例测试 16 PASS。不复用 10 月 22 日计数当本轮验证。
- 真实 Provider 聊天／附件／联网：NOT RUN（本轮未获得可用测试凭据）；未读主设备密钥、未发送私人附件、未重试旧消息。
- 主设备安装、Mac 主入口覆盖、Git 提交／推送／Release：NOT RUN。

## 产物与范围

正式签名候选：`南枫AI-GPT-6.1-Terra-20261027.apk`，1.0.31 / code95 / 非 Debug；SHA256 `44514c059995537b3df154fda43f334d7fbb50a4d5587ebeda190327d3ce5487`。原签名身份已核对，没有覆盖安装。

结构化结果见 [checks.json](checks.json)，增量路径哈希见 [files.json](files.json)。既有工作树保留；不自动提交或发布。

## 2026-10-28交付更新

上述“未安装”为10月27日边界；此冻结候选现已[同签名保数据覆盖OPPO](../2026-10-28-gpt-terra-cover/REPORT.md)，APK拉回一致。未新增真实服务或Mac安装验收。

# Gemini 4 Argon：双端深度档接入

## 接入与依据

- 用户要求新增 Gemini 4 Argon，置于深度档智谱上方；沿用当前 Gemini 的 OpenRouter 渠道。稳定 preset 为 `GEMINI_4_ARGON`，Android logical ID 为 `logical:deep:gemini-4-argon`，精确实时 API ID 为 `google/gemini-4-argon`；拒绝将 `:batch` 等变体解析成该手动选择。没有新增 RC 或 Google 官方直连部署。
- 两端深度档保持 DeepSeek Pro → Muse Wonder → Gemini 4 Argon → GLM-5.3。Android 目录／在线映射／旧快照精确校验／冷启动精确身份、Desktop Rust 目录／ESM 选模和设置均同步。默认模型、既有 Auto 顺序、其他模型与历史归因不变；没有数据库迁移、版本号、安装或发布改动。
- 2026-10-15 核对 [OpenRouter 模型页](https://openrouter.ai/google/gemini-4-argon) 与公开 `GET /api/v1/models`。目录声明输入 text/image/file/audio/video、上下文 1048576、实时输出上限65536；不套用 batch 或营销文案中的百万输出。公开目录 fixture 只含该型号，不含用户内容／凭据。
- 健康初始 UNKNOWN；能力元数据是供应商目录声明，不等于真实媒体或业务验收。共享 JSON seed 供 Android 维护；OpenRouter 在线 registry 与 Desktop 通用附件适配继续使用各自 owner。
- 自身联网按精确型号加入现有 Gemini native web plugin 白名单：开启且非 grounded 时注入 web/engine=native 与搜索指令，禁用与证据回答不注入；不同时发可选 server tool／required。Flash 原路径及其他 OpenRouter／官方／RC 路由保持，Argon 不走 Luna 代搜。该兼容例外登记 OR_GEMINI_SEARCH_INTENT，后续真实来源仍需验证。
- 三处费用 owner（Android、Rust账本、Desktop显示估算）同步已核对的促销价格快照 `openrouter-gemini-4-argon-2026-10-15-v1`：USD2/M输入、10/M输出、0.1/M缓存读取。供应商结算优先，本地仅估算Token，不猜工具／搜索费或把未知记零；促销后须重新核价。

## 验证

- 新菜单行为测试先失败（智谱上方仍为 Muse），接入后通过。Android 测试覆盖精确实时映射、旧缓存 batch 冒名拒绝、位置和 Auto 不变；Android/Rust 搜索请求测试覆盖开关、grounded、文字、多文件与图片序列化；三端价格测试验证1000输入/100输出/500缓存为2050微USD，显示层保留供应商结算优先。
- Android 完整1497 PASS／10 opt-in skip，322套件，Debug构建PASS；Mac Rust444 PASS／34 ignored，原生开发二进制编译PASS；Desktop Node553 PASS／12 skip，lint/typecheck/build PASS；维护16测试及21聊天＋1独立OCR／7凭据组门PASS。详细结果见[verification.json](verification.json)，输入绑定见[source-hashes.json](source-hashes.json)。
- 首轮还定位到新模型使Rust静态数组长度与UI数量断言过期，以及新增测试的坏快照构造；修正数组长度／批准菜单期望和自洽的反例fixture后重跑完整回归，未隐藏失败、跳过测试或放宽身份检查。
- NOT RUN：Argon实际生成／联网来源／真实多附件媒体、双端原生菜单、Room/SQLite业务重开及正式签名安装。0付费请求，未读取Key或改正式数据，未安装／提交／推送。供应商目录查询免费，不冒称真实调用成功；此前v1.0.29安装与GitHub包不包含此次增量。
- 旧逐事实原件、Muse媒体、RC Pro404／旧UNKNOWN／Sol504等验收缺口保持，不能由本次回归关闭。

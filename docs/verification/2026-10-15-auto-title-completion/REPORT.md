# 自动标题兜底与主回答结束修复

2026-10-15；底层HEAD `d64d71026418b9325931b6cbbb94544b28a81309`，本轮为未提交WORKTREE。[24份输入hash](source-hashes.json)／[结构化验证](verification.json)。

## 根因与改动

Android生产executor＋Room的阻塞标题回归在改前出现TimeoutException：主回答已持久完成，主发送仍等待标题。Mac执行owner也有同一同步等待。双端改独立次级任务，主完成后释放发送／进度；标题成功刷新元数据，Mac不再将迟到标题事件写入岛，Android刷新读取当前执行事实。

用户明确要求自动标题失败必须兜底。短标题独立使用 `Qwen3.6 Flash → DeepSeek V4.1 Flash`，只选择已启用且有凭据的服务；显式非思考、JSON object、256输出Token，Android每次读取15秒／Mac单次请求总15秒。超时、网络失败、HTTP拒绝和无有效标题进入下一候选；每候选最多一次正常调用及一次已返回非空无效标题的格式纠正，不循环。取消／本地写入失败停止。保留失败、安全错误码与未知费用，不造usage／零金额；两家均失败保留pending，下次完成回答可再试。

Android同会话次级任务串行，Mac使用按数据区／工作区／会话隔离的标题lease。Android在Room事务内重新读取pending及最新快照，保护手动标题、新草稿和消息。原正常聊天／Auto／联网搜索／历史资料整理路由不变，无新增模型、Key或schema。主回答及检索UNKNOWN规则不受标题专属兜底影响。

## 实际验证与边界

- Android323套件：1502 PASS／10 opt-in skip，0失败；Debug构建PASS。覆盖慢标题不阻塞主完成、异步标题写回、保留新草稿、手动标题优先、超时转备用、取消停止、全部失败有界结束、同／跨会话任务与失败后续工作。
- Mac Rust446 PASS／34 ignored，cargo check PASS；覆盖标题持久化／重开／账本、候选选择、短标题参数与普通聊天隔离、标题lease。未做原生Tauri进度UI验收。
- Node553 PASS／12 skip、lint PASS；模型维护16项及21聊天＋1OCR／7组门PASS；工作流审计无硬错误，保留既有UI metadata／AGENTS软警告。
- 0新增Provider调用，真实上游标题生成与双端原生UI NOT RUN。不能据此判定当前DeepSeek上游故障或恢复。未安装／提交／推送／发布，现装仍上一OpenRouter Argon正式包。

## 合同裁决

[会话UI合同](../../ANDROID_CONVERSATION_UI_CURRENT_CONTRACT.md)唯一拥有标题候选／兜底及进度行为；[运行时合同](../../ANDROID_RUNTIME_CONTEXT_CURRENT_CONTRACT.md)移除“标题属于主完成链”与共用历史整理顺序的旧断言；[模型架构](../../model-service/MODEL_SERVICE_ARCHITECTURE.md)和[决策记录](../../decision-log.md)记录标题专属边界。强制思考的GLM不再参与短标题，历史整理顺序未变。

参数参考：2026-10-15核对[DeepSeek思考开关](https://api-docs.deepseek.com/guides/thinking_mode/)及[千问混合思考开关](https://help.aliyun.com/zh/model-studio/deep-thinking)。公开文档能力不代表当前账号真实请求成功。

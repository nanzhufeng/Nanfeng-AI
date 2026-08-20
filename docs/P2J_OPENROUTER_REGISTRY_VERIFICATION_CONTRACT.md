# 南枫 AI P2-J OpenRouter Model Registry 只读核验合同

日期：2026-08-12  
状态：已实现并经公开目录/API 35 模拟器核验；本合同是唯一允许的联网范围，不包含任何推理调用

## 1. 唯一目标与唯一链路

```text
OpenRouter 官方 `GET /api/v1/models`（无 Authorization、无请求正文）
→ OpenRouterRegistryCatalogClient（固定 host、只读超时/状态校验）
→ OpenRouterRegistrySnapshotVerifier（字段白名单、哈希、Claude 预设映射）
→ ModelRegistrySnapshotStore（应用私有原子持久化）
→ VersionedModelRegistry（当前稳定版 / 上一稳定版）
→ ModelSettingsViewModel / 设置页验证状态
```

- `Model Registry` 仍是具体模型 ID、能力、价格版本和预设映射的唯一所有者；设置页只显示状态并请求核验，不能写入端点、模型 ID 或快照内容。
- 目录读取只允许固定 HTTPS 地址 `https://openrouter.ai/api/v1/models`，只允许 `GET`、空请求体、无 `Authorization`、无 Cookie、无用户输入、无草稿、无附件、无 Prompt、无图片和无真实 API Key。
- 不新增推理 HTTP、聊天完成、流式、图片外发、搜索/图谱/编辑管理、系统图片分享/拍照、账号/同步/Hub/Agent，也不改图标。

## 2. 来源、字段白名单与版本化

- 核验来源是 OpenRouter 官方 Models API；实现不得跟随跨域重定向，响应必须为 HTTPS 的同一官方 host 与 `200 OK`。
- 仅从每个目录条目读取并保存：`id`、`name`、`context_length`、`architecture.input_modalities`、`architecture.output_modalities`、`supported_parameters`，以及 `pricing.prompt`、`pricing.completion`、`pricing.cache_read`。顶层只保留来源 URL、HTTP `ETag`（若存在）、捕获时间、白名单化载荷的 SHA-256 与由它导出的 catalog version。
- 绝不持久化原始响应、未知字段、请求/响应正文、Cookie、重定向地址、API Key、Prompt、用户文字、图片、附件或其他诊断内容。`null` 价格为未知，不能写成 `0`；目录价格以 `USD`、每 token 的整数微美元表示，不能使用浮点真值。
- 只有非空、模型 ID 不重复、含至少一个可映射 Claude 文本模型、预设映射完整且所有字段满足领域不变量的快照，才能标为 `VERIFIED` 并发布。catalog version 为白名单化规范载荷 SHA-256 的稳定前缀，完整哈希同时作为可追溯证据。
- `FLAGSHIP`、`BALANCED`、`FAST` 仅从本次已验证的 Claude 文本模型按可复现排序映射；绝不把任意模型名文本或未知模型变成可选模型。模型不足三项时允许复用同一个已验证 Claude 模型，但必须显式记录为映射回退，不伪造三个不同模型。

## 3. 失败、回退与用户可见状态

- 网络、TLS、超时、非 200、跨域重定向、过大响应、JSON/白名单/映射/持久化失败均不得覆盖当前稳定快照，也不得触发任何推理请求。
- 成功发布新快照时，原当前稳定版成为上一稳定版；写入采用同目录临时文件、同步、原子移动、回读解析和 SHA-256 复核。回读失败必须删除新文件并继续保留旧稳定版。
- 重启时只加载经过文件回读验证的当前/上一稳定快照。没有稳定快照时显示“未验证，不能选择真实模型”；有稳定快照但本次核验失败时显示“使用上一稳定快照（本次核验失败）”；本次远程核验/回读成功时显示“已验证”，并显示来源、验证时间和 catalog version 的安全短指纹。
- 目录核验没有用户内容外发、不会创建 `AiTask`、`Invocation`、`ProviderAttempt` 或费用记录；因此它不是真实模型调用、可用额度或真实能力验收。

## 4. 验证与停止

1. 定向合同测试：GET/无认证/无正文约束、白名单忽略未知敏感字段、价格未知与零值、规范哈希稳定、Claude 映射、失败保持旧版、原子回读损坏回退、重启加载和用户可见状态。
2. 构建：既有单测、Lint、正式签名 Debug/Release 构建均须通过；只新增最小必要依赖，优先平台 HTTP/JSON 实现。
3. 真实目录核验：仅记录官方来源 URL、核验时间、catalog version/完整 SHA-256、结果状态和选中映射；不得输出或保存原始响应。真实目录成功只证明公开目录核验，不证明真实 Key、推理、费用、Token、图片外发、真机、OPPO 或发布。
4. 停止于已验证的只读目录快照及其稳定回退。真实 OpenRouter 调用必须另有独立合同、用户授权的测试 Key、额度和非敏感资料。

## 5. 完成证据与未覆盖风险

- 定向测试共 52 项通过；P2-J 覆盖固定 GET 策略、字段白名单、规范哈希、Claude 预设映射、负数/无法精确表示价格降级为未知、失败回退、原子回读和篡改拒绝。
- `lintDebug`、正式签名 `assembleDebug` 与 `assembleRelease` 通过，版本为 `10 / 0.2.0-p2j`；Debug/Release 均为正式证书的 v2/v3 验签。
- API 35 模拟器对 `https://openrouter.ai/api/v1/models` 发起一次无认证、无正文 GET，成功将 406 个模型的白名单快照写入应用私有目录；catalog version 为 `d537fc08bb627116`，完整 SHA-256 为 `d537fc08bb6271163a8e32e5f859964c68d9ce370ad30af96ba3265b5ac1f9d2`。设置页显示“已验证”，冷启动后仍显示同一短指纹。
- 此证据只证明公开目录的读取、字段收敛、私有落地与回读。不证明 API Key、聊天/图片推理、费用、Token、真实 Android 设备、OPPO、图标视觉验收或发布。

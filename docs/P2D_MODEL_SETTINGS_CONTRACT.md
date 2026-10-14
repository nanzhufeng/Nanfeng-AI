# 南枫 AI P2-D 模型设置与本机凭据合同

<!-- contract-governance:start -->
> **合同口径：领域／模块限定合同。** 领域入口：[ 模型选择与Auto ](P6G_MODEL_SELECTION_AUTO_ROUTER_CONTRACT.md)；[全项目口径与维护索引](contracts/CONTRACT_INDEX.md)。本文的独立owner／格式边界不因归类改变；阶段状态、Schema与验收记录只适用于注明的范围，不能覆盖其他领域或推断整机当前能力。
> **适用范围：** 南枫 AI P2-D 模型设置与本机凭据合同
<!-- contract-governance:end -->

> **当前 Android UI 路由（2026-08-26）：** 本文保留配置、Keystore、Provider Registry 与错误的领域边界。Android 模型与联网、模型设置、调用／费用／上下文／诊断的页面层级、控件、文案、主题、暗色皮肤、测试反馈和弹层只读取 [Android 当前设置界面合同](ANDROID_SETTINGS_UI_CURRENT_CONTRACT.md)。本文早期 Dialog、预设和只读状态文案不得成为当前 UI 规则。

日期：2026-08-12  
状态：已实现本地配置、Keystore 凭据、最小可见设置 UI 与结构化错误合同；真实目录和 Provider 调用不在本增量

## 当前配置维护入口

本合同保留配置／凭据分离、Keystore及结构化错误的领域边界；下方品牌数量、三档策略、Dialog和“尚无HTTP”的内容属于2026-08-12增量记录。当前模型目录、配置身份、端点、选择与协议维护读取[总架构](model-service/MODEL_SERVICE_ARCHITECTURE.md)、[生成矩阵](model-service/MODEL_SERVICE_MATRIX.md)和对应联网／RelayCore合同。未知／退役的当前会话选择须精确失败关闭；历史测试中的“未知旧预设回退”不授权当前普通发送静默替换模型。目录、授权、恢复和历史数据读取不能按本合同早期范围退回未实现状态。

## 历史P2-D增量记录

## 1. 唯一目标与链路

```text
模型设置 UI
→ SaveModelServiceConfigurationUseCase
├─ ModelServiceSettingsRepository → 非敏感服务商/预设/启用状态
└─ ProviderCredentialStore → Android Keystore AES-GCM → 仅 IV/密文落本机
→ LoadModelServiceConfigurationUseCase
→ 只读配置状态 → UI
```

- Provider Registry 唯一拥有 OpenRouter 品牌与固定官方兼容端点；普通 UI 不允许编辑端点。
- Model Registry 本增量只保存“旗舰 / 均衡 / 快速”策略和 Claude 家族提示，不把未经当前目录验证的具体 Model ID 写成永久真值。
- UI 不读取旧 Key；只显示 `MISSING` / `STORED`。留空保存保持已有 Key，新 Key 只在当前弹窗内存中短暂存在且不进入 SavedState。
- 启用服务前必须已有可解密凭据或同时提交有效替换凭据；缺失、过短、含控制字符或安全存储失败均返回结构化错误。

## 2. 数据与隐私

| 数据 | 唯一所有者 | 存储 | 禁止进入 |
| --- | --- | --- | --- |
| Provider/预设/启用状态 | `ModelServiceSettingsRepository` | 私有 SharedPreferences | Room 业务表、网络请求 |
| API Key | `ProviderCredentialStore` | Android Keystore 密钥 + 私有 SharedPreferences 密文 | Room、备份、日志、截图、调用记录、文档 |
| 实际 Model ID/能力/价格 | 后续版本化 Model Registry 快照 | 本增量不持久化 | UI 任意文本框、业务硬编码 |

- `allowBackup=false`，`backup_rules.xml` 与 `data_extraction_rules.xml` 同时排除全部 SharedPreferences、数据库、文件和设备迁移域。
- AES-GCM 使用每个 Provider 独立别名与 Provider ID 作为 AAD；解密失败只表现为“待配置”，不输出异常或密文。
- 本增量没有 HTTP 客户端、真实 Key 检查、Provider 健康检查或任何文字/图片外发。

## 3. UI 与选择面

- 捕获页只增加一张模型服务状态卡和“模型设置”入口，不提前建设完整设置中心或调用记录页。
- 设置使用单一纯白 Dialog；灰色只属于系统 scrim。
- 三项模型预设使用唯一 `AiSelectionSurface`，锚定在触发框下方；不存在第二个默认菜单或长列表分支。
- Key 默认隐藏，提供显隐按钮；保存中禁止关闭和重复提交。

## 4. 结构化错误

- 配置：无效配置、凭据缺失、凭据格式无效、安全存储失败。
- Provider：鉴权、余额、限流、超时、网络、服务故障、响应格式、Schema、上下文溢出。
- `OpenRouterErrorMapper` 只建立 HTTP/异常到稳定领域错误的映射；本增量不发起请求。

## 5. 最小验证与停止条件

- 预设与实际 Model ID 分离、未知旧预设回退、启用前凭据门禁。
- 加密存储往返且持久层不出现明文；设置与凭据由不同所有者保存。
- OpenRouter HTTP/异常错误映射为稳定领域错误。
- 源码检查 Key 不进入 Room/备份/日志；设置弹窗只有一个纯白选择面。
- 全量单测、Lint、正式签名 Debug/Release 构建；模拟器只验证本机设置、Keystore 重启恢复和 P2-C 待补链路。
- 达到上述本地门即停止 P2-D；不得据此宣称真实 OpenRouter、真实模型目录、真实服务、费用或图片外发已通过。

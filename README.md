# 南枫 AI

南枫 AI 是本地优先、可导出、支持多 Provider 的个人 AI 工作台。这个仓库是产品的唯一源码真相：Android、Desktop、跨端协议和可选云端组件共同演进。

## 仓库边界

当前采用**私有单仓库**，不按 Android、macOS 和 Windows 拆分源码：

```text
app/              Android 应用
desktop/          Tauri Desktop；macOS 与未来 Windows 共用的前端和 Rust 主线
protocol/         跨端版本化格式与 fixture
supabase/         云端迁移与 Edge Functions
upload-gateway/   附件网关
docs/             合同、决策、开发档案与交接
```

平台安装包是独立发布物：Android 发布 APK；macOS 发布已签名的 macOS 包；Windows 在 Windows 原生环境构建并发布 MSI 或 NSIS 包。macOS 包不能替代 Windows 构建、签名、WebView2 或真实验收。

首传、截图溯源、版本和发布规则见 [GitHub 仓库与发布策略](docs/GITHUB_REPOSITORY_AND_RELEASE_POLICY.md)。当前开发状态、已验证层级和未验证边界只以 [当前交接](docs/CURRENT_HANDOFF.md) 为准。

## 开发前必读

1. 阅读 [AGENTS.md](AGENTS.md) 和当前交接。
2. 按任务选择 `docs/` 中唯一的现行领域合同；不要以旧交接或历史包推断当前行为。
3. 凭据只放在本机受保护配置或 CI Secret 中，绝不提交到仓库。

## 自动验证

`.github/workflows/source-verification.yml` 只执行不需要签名、设备或真实服务的源码验证。正式 Android、macOS 与未来 Windows 发布均须走各自的签名、产物、数据保留和真实环境验收门；不能以 CI 绿灯替代这些证据。

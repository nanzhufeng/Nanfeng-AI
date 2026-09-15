# 南枫 AI

本地优先的个人 AI 工作台：Android、macOS，后续支持 Windows。

## 下载

正式版：[v1.0.1](https://github.com/nanzhufeng/Nanfeng-AI/releases/tag/v1.0.1)

- Android：`1.0.1` APK
- macOS：`1.0.1` DMG（Apple Development 签名，未公证）


## 源码

| 目录 | 内容 |
| --- | --- |
| `app/` | Android |
| `desktop/` | macOS 与未来 Windows 共用的桌面端 |
| `protocol/` | 跨端协议 |
| `supabase/`、`upload-gateway/` | 可选云端组件 |

Windows 必须在 Windows 原生环境单独构建、签名和验收；macOS 包不能替代 Windows 包。

开发规则与当前状态见 [AGENTS.md](AGENTS.md) 和 [当前交接](docs/CURRENT_HANDOFF.md)。

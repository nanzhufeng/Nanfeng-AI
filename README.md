# 南枫 AI

本地优先的个人 AI 工作台，支持 Android 与 macOS。

## 下载

当前发布：[v1.0.20](https://github.com/nanzhufeng/Nanfeng-AI/releases/tag/v1.0.20)

- Android：[APK](https://github.com/nanzhufeng/Nanfeng-AI/releases/download/v1.0.20/Nanfeng-AI-Android-1.0.20.apk)
- macOS：[DMG](https://github.com/nanzhufeng/Nanfeng-AI/releases/download/v1.0.20/Nanfeng-AI-macOS-1.0.20.dmg)（开发签名，未公证）

## 预览

| Android | macOS |
| --- | --- |
| ![Android 1.0.20 预览](https://github.com/nanzhufeng/Nanfeng-AI/releases/download/v1.0.20/Nanfeng-AI-Android-1.0.20-preview.png) | ![macOS 1.0.20 预览](https://github.com/nanzhufeng/Nanfeng-AI/releases/download/v1.0.20/Nanfeng-AI-macOS-1.0.20-preview.png) |

预览图与对应安装包一起生成并随 Release 附件校验；Android 来自隔离模拟器，macOS 来自隔离数据目录。

## 源码

| 目录 | 内容 |
| --- | --- |
| `app/` | Android |
| `desktop/` | macOS 与未来 Windows 共用的桌面端 |
| `protocol/` | 跨端协议 |
| `supabase/`、`upload-gateway/` | 可选云端组件 |

Windows 必须在 Windows 原生环境单独构建、签名和验收；macOS 包不能替代 Windows 包。

开发规则与当前状态见 [AGENTS.md](AGENTS.md) 和 [当前交接](docs/CURRENT_HANDOFF.md)。

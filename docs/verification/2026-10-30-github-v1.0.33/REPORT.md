# v1.0.33 Android 与 macOS 发布

- 包含已提交的模型失败边界、计量与回归 checkpoint，以及完整开发档案、AGENTS、五类独立 Skill 和可迁移开发经验。Windows 1.0.32 发布分支、下载和预览保持不变。
- 构建源码：`9886ce5dcc5e57a5372c9ac6331b8063743ef86c`。本次业务代码未额外修改，仅 Android 97／1.0.33 与 macOS 1.0.33 产品版本更新。
- 新跑 Android lintRelease／assembleRelease、macOS bundle 构建／验签／DMG 完整性，以及 114 合同／40 域／23 聊天模型／1 独立模型／7 凭据组静态门均通过。原业务输入回归沿用[checkpoint](../2026-10-30-code-checkpoint/REPORT.md)与[最终回归](../2026-10-30-risk-boundary-audit/REPORT.md)，不刷新旧真实服务证据日期。
- APK 原签名、非 Debug、版本正确；独立 Android 15 模拟器安装后回拉字节一致。Mac 使用原 Apple Development 身份，未公证；DMG 只读挂载后复制，bundle 文件逐一一致，在独立数据根正常启动。四张正常页面截图随冻结包 manifest 校验通过，见[字节清单](artifacts.json)。空数据未配置 Key 的预览不证明真实模型服务。
- 本轮未覆盖主设备、未覆盖 /Applications、未触发收费请求或云端部署。原 499 关闭方和 partial 计量剩余边界继续保留；不宣称全部媒体、联网或所有异常已实测。
- [v1.0.33](https://github.com/nanzhufeng/Nanfeng-AI/releases/tag/v1.0.33) 已正式发布为 Latest，非草稿／非预发布；APK、DMG、SHA256SUMS 均回下载，字节数、本地 SHA256 与 GitHub digest 一致。三个云端源码检查全部 SUCCESS，见[远端回执](remote.json)。仅本任务原生进程与模拟器已停止，保留隔离数据。
- 复用证据核对：784 原索引输入中783不变，Android版本文件变更；另外核对未在原索引内的Tauri产品配置。`2c22021..9886ce5`全部应用目录差异仅这两份版本文件，见[input-reuse.json](input-reuse.json)。本次没有重跑旧收费验收。

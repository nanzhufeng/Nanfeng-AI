# 南枫 AI Windows

Windows x64 1.0.32，包含模型目录对齐、附件阅读、全界面滚动条和模型资料提示优化。

[下载安装包](https://github.com/nanzhufeng/Nanfeng-AI/releases/tag/v1.0.32-windows.1)。安装包和程序均未签名（NotSigned）。

本标签保存与 Windows 发布程序对应的源码快照。Mac/Android 主线保留在 main，本标签不用于构建其发布包。

本地前端 1868 通过、0 失败、12 既有跳过；已完成隔离原生验证。真实模型服务由用户稍后在软件内配置，未执行本次安装包的实际覆盖安装。

开发：在 desktop 运行 npm ci、npm run build，再在 Windows 使用 node scripts/build-windows.mjs。媒体工具需按 desktop/scripts/verify-windows-media-tools.ps1 配置，真实配置和凭据不随源码提供。

# GPT-6.2 Sol 双端覆盖

2026-11-18，Android 与 macOS 从 1.0.40 覆盖至 **1.0.41**。

- Android Release 构建及 Lint 通过。OPPO 安装 code105 原签名正式包，现装 APK 哈希匹配，正常启动并进入前台。首装时间、应用 UID、数据目录及 CE／DE inode 保持，CHAT 诊断摘要一致。WORK 诊断未取得。设备临时 APK 已清理。
- Mac DMG 校验通过，沿用现装 Apple Development 叶证书和 Bundle ID。覆盖后应用窗口正常打开，CHAT 和 WORK 的会话与调用记录指纹一致。原 1.0.40 App 已备份。
- 候选包内核对 OpenRouter `openai/gpt-6.2-sol` 和 RelayCore `gpt-6.2-sol`。真实聊天、联网及 RelayCore 的新模型实扣待验。
- Android APK SHA-256 `702bdbf43b3edd91f63a076fb90e1ef01ceebb4500c942327a343d70b5b2cffa`。Mac DMG SHA-256 `48bc1509a9aa7345aefdff47a400a6600f6ccf879c86d0b09d213bc707371366`。现装 Mac 可执行文件 SHA-256 `7d90a68d74abae0b8689847245b0c8b6bcec8a704d23ec6db897ce0ebba0b205`。

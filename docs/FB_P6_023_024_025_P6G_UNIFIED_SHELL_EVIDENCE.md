# FB-P6-023/024/025 与 P6-G 统一壳层证据

日期：2026-08-14  
状态：本地统一壳层与 P6-G UI 退出已关闭；不是项目终点

## 已关闭的退出门

- Android Release 先前表象为 `mergeReleaseJavaResource` 失败，诊断日志的根因是 JBR `Out of space in CodeCache for method handle intrinsic`，不是资源重复。`gradle.properties` 已在现有 heap 限制上增加 `-XX:ReservedCodeCacheSize=320m`；单 worker 的 Release 随后实际执行 `mergeReleaseJavaResource` 并成功。
- Android：`testDebugUnitTest` 283/0/0、`lintDebug`、Debug assemble 均通过。正式签名 Debug 经 v2/v3 验签，证书 SHA-256 为 `889ecf3ff4eeb40486e5122c6dc4eaaa04226ce6493d8a946c65275a803e99d5`；`install -r` 后回拉的 `base.apk` 与本地产物 SHA-256 同为 `abd505f98c6adc97bcdded7d100af3a0890093da2d399788cdee05e5908e59e8`。没有清数据、未安装 test APK、未操作 OPPO。
- Android Emulator 的 Computer Use 仍不可附着，故改用真实 `adb shell input` 触控/键盘这一安全本机 UI 通道，不将截图或 hierarchy 伪装成操作：drawer 打开 → 搜索输入 `local` 时 `mInputShown=true`；底部 Settings 可达；对话/工作切换实际改为当前会话工作 scope；Settings 新增的明确 `LOCAL` 本地确定性 fixture 写入 app-private P6-G catalog 后，普通会话实际选择 fixture → force-stop/restart 仍显示手动 fixture → 实际选择 Auto → restart 仍显示 Auto。Settings 的 global FAST 已从 owner 读到，再点 Auto、restart 后读回 `全局默认：自动`。
- Android TEMP：Ghost 进入临时面后只见临时恢复字段、没有普通 P6-G fixture；force-stop/restart 回到普通会话且 Composer 为 Auto；再次 Ghost 可恢复同一临时记录，退出后重启仍是普通会话。这是 TEMP 与 ordinary owner 的零泄漏 readback，不读取 app-private 数据、Key 或 URI。
- Desktop：最新唯一 bundle 完成 Node lint/42 tests/static build 与 Tauri release bundle。初包的资源封签缺失已由同一 bundle `codesign --force --deep --sign -` 修复；`codesign --verify --deep --strict` 通过，`Identifier=com.nanzhufeng.ai.desktop`、`Signature=adhoc`、`TeamIdentifier=not set`，最终 executable SHA-256 为 `091c855fc608048f46b075292e30b44a026c99d2bef26a32d034d646d6d5fab2`。它不是 Developer ID、notarized 或发布包。
- Desktop 真实 Computer Use：只运行上述 bundle 的唯一进程。宽窗 Chat→Work 实际显示当前会话 scope（不再露出工程模块目录）；Settings 从诚实空 catalog 点击添加本地 fixture；普通会话的标准可访问模型下拉实际选择 fixture，完整退出/重开仍为手动 fixture，随后选 Auto 并重开仍为 Auto。此前被底部裁切的模型 option 已改为同一 popover 内标准 select，避免把不可达项误报为完成。
- Desktop 窄窗由真实窗口拖拽进入，实际打开/关闭 drawer；离底圆形 `到最新消息` 被实际点击并把焦点回交 Composer；Composer 写入 `P6G_DESKTOP_UI_QA_LOCAL_ONLY` 后实际保存为 `LOCAL_RECORD`，没有模型调用。两端同态聊天面均保持 Assistant 开放列、USER 限宽暖橙 bubble、消息归属 metadata、固定 Composer、drawer 治理入口与对象 scope 的 Chat/Work 分离。

## 退出判断

- FB-P6-023/024/025 的本地 chat-first 壳层 P0–P2 与 P6-G 本地 catalog/global/ordinary-conversation override 的 UI/restart 门已关闭；`design-qa.md` 可标为 `passed`。
- 这只关闭本地 UI/owner 路径，不把 fixture 当作已联网 Provider，不授权 Key/HTTP/Prompt/RunSpec/Agent/tool execution、账号同步、OPPO、图标变更、Developer ID/notarization 或发布。

## 边界

未读 Key、未发 HTTP、未调用 Provider/Agent/tool、未外发图片、未操作 OPPO，且未改 launcher/Dock 图标。

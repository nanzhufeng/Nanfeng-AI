# 2026-08-31 模型与媒体交互 checkpoint

## 本次冻结范围

- Composer 草稿附件与已发送附件使用同一受验证的本地预览投影；仅生成本地缩略图、首页、海报、音频语义或文本摘要，不改变附件 egress。
- 退役 Grok 选择从设置、Composer 和 Auto 候选移除；旧会话保留历史模型归因，但发送／重试在读取凭据、准备附件和外发正文之前以 `MODEL_NOT_FOUND` 失败关闭，绝不静默换模型。
- 图片查看器按图片与视口的真实几何溢出决定最小缩放、双击放大和单指平移：可缩小到完整图片，初始纵图可上下查看，放大后可连续浏览每一个溢出方向，位置只在真实内容边界收束。

## 验证证据

- `:app:testDebugUnitTest`：`1042 tests / 0 failures / 3 skipped`。
- `:app:assembleRelease`（含 `lintVitalRelease`）：通过。
- APK：`app/build/outputs/apk/release/南枫AI.apk`，`28,017,564` bytes，SHA-256 `8279335eef23b7aff39fbf08ff367e2a7d3ec8b0325f04f5d0e3062aabe95593`；签名证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。

## 验收边界

本 checkpoint 未运行任何 `connected*AndroidTest`，未安装或操作 OPPO，未使用用户 Key 请求真实 Provider。既有 OPPO 同签名覆盖和逐字节回读仍有效，但只证明安装与数据保留；真实 Provider 输出稳定性与真机图片手势体感仍需要独立人工验收。

# 南枫 AI P2-B 相册捕获与真实验收合同

日期：2026-08-12  
状态：已实现；自动验证与 API 35 模拟器真实相册链路通过，真机待验收

## 1. 第一用户任务

- 用户目标：从 Android 系统相册选择一张图片，立即看到可识别的预览，并在离开页面或 Activity 重建后仍能恢复草稿。
- 真实输入：Android Photo Picker 返回的单个 `content://` 图片 URI，或用户取消后返回 `null`。
- 最终结果：图片二进制已复制到 App 私有目录，附件元数据与来源写入 Room，页面只从私有副本恢复和预览。
- 不属于本次范围：系统图片分享、拍照、删除/回收站、OpenRouter、图片外发、Candidate、Knowledge UI、真实导出文件。

## 2. 概念与入口

| 概念 | 唯一所有者 | 输入 | 消费者 | 禁止分叉 |
| --- | --- | --- | --- | --- |
| 系统相册选择 | Android Gallery Adapter | Photo Picker URI / 取消 | Capture Application | 页面直接保存 URI 或申请整库权限 |
| 图片捕获事务 | `CaptureGalleryImageUseCase` | 已打开的图片流、MIME、显示名 | Room 草稿、捕获 UI | ContentResolver、私有附件和 Room 分别报告成功 |
| 私有附件 | `PrivateAttachmentStore` | 图片流 | 草稿恢复、预览、未来外发 | 外部 URI 或绝对路径成为业务真值 |
| 当前图片草稿 | `CaptureDraftRepository.findLatest` | Room | Capture ViewModel | 页面局部状态成为唯一副本 |

## 3. 状态与操作

| 状态 | 事实条件 | 中文提示 | 用户操作 | 持久化/恢复 |
| --- | --- | --- | --- | --- |
| 空 | Room 无草稿 | 还没有图片草稿 | 从相册选择 | 不持久化空状态 |
| 恢复中 | 正在 IO 线程读取 Room 与私有副本 | 正在恢复本地草稿 | 等待 | 完成后进入空/完成/失败 |
| 导入中 | 已返回 URI，正在校验、复制、哈希和写库 | 正在复制并保存 | 等待 | 未成功前不替换当前草稿 |
| 完成 | 私有副本、哈希和 Room 草稿均成功 | 已复制并保存为本地草稿 | 重新选择 | Activity 重建从最近草稿恢复 |
| 取消 | Photo Picker 返回 `null` | 已取消选择，当前草稿没有改变 | 再次选择 | 不写库、不覆盖当前草稿 |
| 失败 | 来源不可读、类型不支持、超过 20 MB、完整性或写库失败 | 原因与可执行建议 | 重新选择 | 旧草稿原位保留，不误报成功 |

## 4. 性能与诊断

- 私有复制使用流式写入与 SHA-256，不把原始输入一次性读入内存。
- 单张图片上限 20 MB；超过上限停止写入并清理本次临时文件。
- UI 预览最大边按约 2048 像素采样，减少大图解码内存压力。
- Room、ContentResolver、复制与恢复均在 IO dispatcher 执行，不阻塞主线程。
- 用户只看到中文原因与恢复建议；外部 URI、绝对路径和原始异常不进入 UI 或数据库。

## 5. 权限与系统集成

- 使用 `ActivityResultContracts.PickVisualMedia` 单图模式。
- Manifest 不声明 `READ_MEDIA_IMAGES` 或 `READ_EXTERNAL_STORAGE`；不请求整库读取权限。
- 不持久化 Photo Picker grant：内容在回调后立即复制到 App 私有目录。
- 取消不是权限拒绝，也不是失败；后续系统图片分享与拍照需另建入口合同。

## 6. 真实链路

- [x] API 35 模拟器从系统相册选择非敏感真实 PNG。
- [x] 适配器校验 MIME、可解码尺寸并打开输入流。
- [x] 私有复制、大小与 SHA-256 完整性。
- [x] Room 草稿保存、最近草稿读取与私有副本恢复。
- [x] 取消、格式错误不写库且不覆盖旧草稿。
- [x] Activity 强制停止并冷启动后的系统级实际恢复。
- [ ] 真实 OEM 相册、超大图、HEIC/WebP 与低内存设备表现。

## 7. 验证等级

- [x] 领域契约。
- [x] Room/附件入口契约。
- [x] Kotlin 编译与单元测试。
- [x] Lint、正式签名 Debug/Release APK。
- [x] API 35 模拟器相册、取消、冷启动恢复与截图。
- [ ] OPPO 真实设备（需设备可用与同签名覆盖验收条件）。

## 8. 交付边界

- 代码入口：`CaptureGalleryImageUseCase`、`AndroidGallerySelectionReader`、`CaptureViewModel`、`NanfengAiApp`。
- 平台标识：Android `minSdk 26`、`targetSdk 36`。
- 当前开发验收 APK 为可调试构建，但从本阶段开始与 Release 共用正式证书；本阶段仍不发布、不上传 GitHub。
- 未验证风险：Photo Picker 回退实现、OEM 相册行为、系统进程重建、真实图像格式和目标设备视觉。

## 9. 响应式与连续性

- 紧凑窗口为“标题 → 图片主工作区 → 状态 → 相册操作”的单列结构。
- 840dp 及以上为主预览区 + 操作/状态辅助栏，不把手机卡片同比拉宽。
- 页面内部统一滚动；状态变化不移动相册主操作的语义位置。
- Activity 重建通过 Room 最近草稿恢复，不序列化 URI 或图片正文到 SavedState。

## 10. 反馈—原因—实现—验证

| 反馈 | 首个语义分叉 | 唯一实现 | 自动测试 | 模拟器/真机 | 尚存风险 |
| --- | --- | --- | --- | --- | --- |
| 选择图片后必须长期可用 | 外部 URI 被误当真值 | 先私有复制，再保存 Room 草稿 | 私有回读与来源清洗 | 待验收 | OEM/格式 |
| 取消不能丢原草稿 | `null` 被当失败或空草稿 | `Cancelled` 结构化终态 | 取消不替换草稿 | 待验收 | 系统返回路径 |
| 重建后应恢复 | 页面状态成为唯一副本 | `findLatest` + 私有附件恢复 | 新 Repository 恢复同一草稿 | 待验收 | 真实进程死亡 |

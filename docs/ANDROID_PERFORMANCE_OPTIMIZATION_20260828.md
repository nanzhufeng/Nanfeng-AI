# Android 流畅度优化交付记录（2026-08-28）

> **后续产物边界：** 本文记录的 OPPO 覆盖与系统帧证据对应 APK SHA-256 `9e8d0a6a98c209864d0871c86f472698134df59c5c491827bb2ed9cebf802e7a`。同日后续“本地文本预览 Markdown 排版”已重建同一路径 APK，当前本地文件 SHA-256 为 `3cd3a638f73a2b71f4ccc4068be1fca3960ca3bf6ac58a7f638c603648cb46e5`，尚未安装 OPPO；不得把本文设备结果套用到后续包。

## 范围

- 只优化会话加载、搜索、状态投影、Compose 重组、附件预览和文件读取性能。
- 不改变功能、入口、控件、布局、视觉合同或数据语义。
- 未执行任何 `connected*AndroidTest`，未安装或操作 OPPO Find N5。

## 已落地

### Room 与搜索

- 会话快照使用单次批量内容块查询，消除按消息逐条加载内容块的 N+1。
- 会话列表批量读取记忆来源；超过 SQLite 绑定参数安全阈值时按 900 条分批。
- ChatGPT、Claude、ZIP 导入来源改为批量读取，并使用同一 900 条分批边界。
- 文本和附件搜索使用当前分支的数据库投影，不再为整个目录重建完整 `ConversationSnapshot`。
- 空查询浏览使用轻量会话/索引投影，不加载消息树和附件正文。

### 状态投影

- `MessageTree` 预计算父子索引，并将环检测改为带记忆的线性遍历。
- 当前路径、叶节点和可编辑用户消息通过一次 `ConversationBranchHistory.project` 共同投影。
- `reload()` 复用已载入的选中会话，避免重复 `findById`。

### Compose、媒体与 I/O

- 消息文本块、附件块和高频派生状态使用稳定键缓存，减少滚动和输入期间重复计算。
- 普通会话附件预览改为消息进入可见组合范围后按需请求，不再在整段转录加载时预生成。
- 图片、视频代表帧、PDF 页和原图位图统一在 `Dispatchers.Default` 解码。
- Markdown、JSON、ChatGPT、Claude、南枫知识库、PDF、ZIP 选择器读取移至 `Dispatchers.IO`。

## 自动验证

- 聚焦性能/搜索/附件/ZIP 回归：18 项通过。
- 完整 JVM：850 项，0 失败，0 错误，3 跳过。
- `:app:lintDebug`：通过；0 Error，90 Warning，15 Hint。
- `:app:assembleRelease`：通过。
- `git diff --check`：通过。

新增性能契约覆盖：

- 80 条消息的长会话回读只允许一次批量内容块查询，不允许逐消息查询。
- 30 个会话的列表记忆来源只允许一次批量查询。
- 搜索不得逐会话加载快照或逐消息加载内容块。
- 附件预览保持可见区懒加载，位图解码和选择器读取不得回到 Compose 主线程。

## Release 产物

- 路径：`app/build/outputs/apk/release/南枫AI.apk`
- 包名：`com.nanzhufeng.ai`
- 版本：`0.3.0-p10j`（66）
- 大小：25,889,916 bytes
- DEX：4 个
- 签名：APK Signature Scheme v2/v3 验证通过；证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`
- APK SHA-256：`9e8d0a6a98c209864d0871c86f472698134df59c5c491827bb2ed9cebf802e7a`

## OPPO 保数据覆盖与系统级性能证据

- 设备：OPPO PKH120（Find N5），Android 16 / API 36，物理视口 `1140×2616`，覆盖密度 `442dpi`，字体比例 `1.0`。
- 安装前包：`com.nanzhufeng.ai / 0.3.0-p10j (66)`，非 Debug；证书与新包完全一致。
- 安装路径：推送到 `/data/local/tmp` 后执行 `pm install -r --user 0`，成功后删除临时 APK；没有卸载、清数据或安装测试包。
- 数据保留：`ceDataInode=1459104`、`deDataInode=1433378` 和首次安装时间 `2026-08-20 15:15:31` 在覆盖前后保持不变。
- 设备回读 APK 与本地产物逐字节一致，SHA-256 均为 `9e8d0a6a98c209864d0871c86f472698134df59c5c491827bb2ed9cebf802e7a`。
- 两次正式包冷启动均成功，`TotalTime` 分别为 `229ms`、`186ms`，Activity 保持前台且进程存活。
- 前后台恢复成功，任务回前台 `WaitTime=19ms`。
- 六次屏幕中部合成纵向滑动共产生 291 帧：0 janky frame，50/90/95/99 分位为 `5/5/5/6ms`，慢 UI 线程、慢位图上传、慢绘制命令均为 0。

## 尚未冒充完成的验收

- ADB 合成滑动同时记录了 288 次 high-input-latency，不能替代真实手指触控延迟判断。
- 为避免读取用户私有聊天内容，本轮没有抓取截图、UI 文本或数据库，也没有按内容识别并打开特定长会话、搜索结果或附件。
- 因而正式覆盖、数据保留、冷启动、前后台恢复和当前页面系统帧统计已验证；长会话、搜索输入和具体附件预览的主观手感仍由用户在真实内容上确认。

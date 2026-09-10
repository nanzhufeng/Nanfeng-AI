# C-13 对话生命周期 Android → Desktop 合同

状态：**2026-09-03 按当前 Android 源码、隔离 AVD 同状态和隔离 Tauri SQLite 主链闭环。** 本合同只覆盖设置 → 对话管理 → 收藏／已归档／回收站，不扩展到 C-14 及后续单元。

## 1. Android 当前事实

- 对话管理根页固定为“收藏 → 已归档 → 回收站”，每项保留当前 Android 的标题、说明、语义图标和整行进入。
- 收藏页说明“收藏的会话保存在本机；取消收藏不会删除消息或附件。”，列表显示标题和“更新于”本地时间；取消收藏是唯一行级动作。
- 已归档页说明“归档会话不会出现在日常列表；恢复后会回到普通对话列表。”，列表显示标题和“创建于”本地时间；行级动作是恢复、删除，批量动作是清空已归档。
- 回收站页说明“会话消息树尚未物理删除；恢复后会回到普通对话列表。”，列表显示标题和“创建于”本地时间；行级动作是恢复、删除，批量动作是清空回收站。
- 已归档删除必须先确认“会话标题将移入回收站，可恢复”；回收站删除必须先确认“将永久删除会话标题，无法恢复”。恢复不要求危险确认。
- 收藏、已归档和回收站中的会话行都能打开真实消息树；返回时回到原列表，并由列表自己的滚动位置 owner 恢复位置。

## 2. Desktop 投影与允许适配

- `conversation-lifecycle-view.mjs` 是根页和三类列表的共享渲染 owner；Browser 与 Tauri 不再各自拼装生命周期字段。
- Desktop 保留宽屏双栏设置 IA。Android 左滑操作投影为行尾省略号 disclosure，只有展开后才显示动作；这属于鼠标／键盘适配，不改变动作集合、确认层级或恢复边界。
- 收藏使用 `updatedAt`，归档／回收站使用 `createdAt`，统一显示本地 `YYYY-MM-DD HH:mm`；空态分别为“暂无收藏会话。”、“暂无已归档会话。”和“暂无回收站会话。”。
- Browser C13 fixture 只做固定、只读渲染，不写 Desktop SQLite。原生 C13 fixture 只允许 `NANFENG_AI_DESKTOP_C13_ACCEPTANCE=1`、唯一 `/tmp/nanfeng-ai-desktop-c13-acceptance.*` 根和 `--diagnostic-ui-schema-acceptance` 同时成立；正常启动不能选择该根。

## 3. 行为与数据门

1. 先以当前 Android 源码和隔离 AVD 建立三类本地会话，再采集根页、列表、行级动作和确认弹窗；旧截图不能定义字段。
2. C13 Node 合同必须先对旧 Desktop 形成红灯，再锁定入口文案、时间字段、按需动作层、确认文案和列表返回 owner。
3. Android 只运行相关 JVM 合同和 build，永久禁止 instrumentation／`connected*AndroidTest`。
4. Browser 固定 `1440×900`，验证 DOM、console、横纵溢出、三类列表、动作 disclosure 与删除确认。
5. Rust 必须验证临时根校验、三类固定状态、收藏表、恢复、永久删除、重开不重灌 fixture 和 `PRAGMA integrity_check=ok`。
6. 隔离 Tauri 必须验证真实 AX／截图、SQLite 投影、无网络 socket、恢复后重启仍保持、收藏会话进入后返回原列表。图形界面的永久删除只检查确认并取消，实际不可逆提交由隔离 Rust 测试执行。

## 4. 已确认边界

- 本轮没有操作 OPPO、正式 Desktop 数据根、账号、Provider、Key、网页、通知或真实服务。
- Android 同状态数据由隔离模拟器本地 UI 创建；Provider 未启用的失败消息只用于生成本地会话，不构成真实 Provider 验收。
- 当前开发 `.app` 是 ad-hoc 签名开发包，不是 Developer ID／公证正式包。

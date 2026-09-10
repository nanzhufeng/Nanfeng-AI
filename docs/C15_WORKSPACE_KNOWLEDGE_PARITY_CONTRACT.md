# C-15 工作区／项目／知识／开发跨端合同

> 状态：2026-09-03 已闭环。只裁决 C-15，不扩展 C-16，也不改写最终完成度审计。

## 1. 当前 Android 事实

C-15 只读取当前 Android 源码与隔离 AVD 的同状态结果，不从 Desktop 反推：

- 设置首页“工作区”组只有“项目与知识”“开发与诊断”。
- “项目与知识”说明固定为：`Projects 用于范围整理；开启资料库搜索后，相关本地资料会随当前问题自动检索并加入上下文。`
- 入口为“管理 Projects（n）”“管理知识库”；“开发与诊断”入口为“本次 Context 控制”“离线评测”。
- 工作模式未选择项目时显示“还没有项目工作对话／从左侧项目中创建或打开一条对话。”，没有 Composer。
- 选择项目后仍显示相同空态，同时出现 Composer；在真正发送首条消息前项目仍是 `0 个工作对话`，不得伪造会话。
- 工作抽屉包含工作、项目、知识、记忆和项目列表。设置入口与工作抽屉必须到达同一 Projects／知识 owner。

隔离 Android fixture 为：

- Project：`C15_Project`／`C15_local-only_fixture`
- Knowledge：`C15_Knowledge`／`C15_local-only_knowledge_fixture`／标签 `c15,local`

## 2. Desktop 共享 owner

- `workspace-view.mjs` 统一拥有工作导航、项目筛选和工作空态；`chat-shell.mjs` 只负责布局投影。
- 工作模式只读取带 `projectId` 的会话，普通对话不得泄漏到工作态。
- 未选择项目不渲染 Composer；选择项目后允许新建草稿，但不先创建假会话。
- 首条实际发送由 `sendLocalMessage → submit_desktop_ordinary_chat → Rust mutation` 原子写入 `conversation.projectId`；已有会话不能通过草稿参数改写 owner。
- Projects／知识／记忆页和设置入口复用同一路由与数据 owner；Desktop 只保留宽屏布局差异。

## 3. 固定夹具与红绿合同

- Browser 使用显式只读 `c15WorkspacePreview`，不会写 Desktop SQLite、调用 Provider 或读取正式数据根。
- Tauri fixture 只有同时满足 C-15 marker、唯一 `/tmp/nanfeng-ai-desktop-c15-acceptance.*` 根和诊断启动参数才会建立；同根重开不重新播种。
- C-15 Node 合同首轮 `0/4`，修复后 `4/4`；合同同时锁定普通会话隔离、项目选择前后 Composer、共享导航 owner 和活跃提交链的 `projectId`。
- Rust 合同锁定唯一根、fixture 重开和项目首条消息的 `projectId` 持久化。

## 4. 分层验收

- Android：三个最小 JVM 合同任务与 `:app:assembleDebug` 通过；只使用关网隔离 AVD，没有 instrumentation。
- Browser：精确 `1440×900`，工作空态、选中项目、Projects、知识、记忆、工作区设置和开发设置均实看；页面 `scrollWidth=1440`、`scrollHeight=900`，console warning／error 为 0。
- Desktop Node／Rust：lint、typecheck、static build、Node `225/225`、Rust `194/194` 全通过。
- Tauri：最新 development bundle 复制为 `com.nanzhufeng.ai.desktop.c15.acceptance.7mgJQw`，仅使用 `/tmp/nanfeng-ai-desktop-c15-acceptance.7mgJQw/app-data`。原生实看选择项目前后状态并同根重开；SQLite `integrity_check=ok`，project／knowledge／memory revision 均为 `2`，活动会话和活动 relation 均为 `0`，进程无 TCP／UDP socket。

证据位于 `/Users/nanzhufeng/.codex/visualizations/2026/09/03/01a065e5-85ed-7011-9f89-98d3c56d34d5/nanfeng-ai-c15-20260903/`。

## 5. 边界

- 未操作 OPPO、正式 Desktop 数据根、账号、Provider、Key 或真实服务。
- 未运行 instrumentation 或任何 `connected*AndroidTest`。
- development bundle 为 ad-hoc 签名，不是 Developer ID／公证正式包。
- 本合同不声明 C-16 或最终审计完成，也不把 Browser fixture、自动测试或构建替代真实外部服务验收。

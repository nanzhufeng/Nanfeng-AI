# Android → Desktop 深层原生验收记录（2026-09-01）

状态：**本机深层对齐通过；外部真实服务／系统权限另行验收**

## 1. 构建与隔离

- 源码 Release：`desktop/src-tauri/target/release/bundle/macos/南枫 AI Desktop.app`
- 版本：`0.6.0-p6d-dev`；正式 Bundle ID：`com.nanzhufeng.ai.desktop`
- 主程序：`30,702,352` bytes；SHA-256 `d0934b1a6830044cc0f4bee03a5c98ef040665025a1f4997875497ca383ef9f2`
- 签名：严格 `codesign --verify --deep --strict` 通过；仍为 ad-hoc，`TeamIdentifier=not set`，不是 Developer ID／公证分发包。
- 最终原生 QA 副本：`/tmp/nanfeng-ai-final-acl-qa.66665M/南枫 AI Desktop Final ACL QA.app`
- 最终 QA Bundle ID：`com.nanzhufeng.ai.desktop.deepqa.finalacl20260901`
- 最终数据根：`/tmp/nanfeng-ai-desktop-ordinary-chat-acceptance.final-acl.CdkKBU`
- 根内实际生成独立 `workspace.sqlite3`、agent ledger 与 owner lock；正式 Desktop 数据根未读取。

## 2. 自动门禁

- `npm run lint`：通过。
- `npm run typecheck`：通过。
- `npm test`：`141/141` 通过，0 failed。
- `cargo test --manifest-path src-tauri/Cargo.toml --lib`：`165/165` 通过，0 failed、0 ignored。
- 一次全量 Rust 运行中的 localhost OAuth 回调测试瞬时失败；该单测立即单独重跑通过，随后全量 `165/165` 再跑通过，未把瞬时结果隐藏为通过。
- `npm run build`、`npm run bundle:macos`、严格 codesign：通过。

## 3. 原生 Computer Use 深层证据

- 设置：二／三级页、模型设置、Google 未配置态、本机数据、关于、开发与诊断、导入与工作区均实际打开；未填写凭据。
- 自定义指令：全窗口编辑，输入后取消立即回滚到进入前草稿；曾发现“取消仍显示编辑草稿”，修复后重建复验通过。
- Memory：空态、本机查询、询问／补充显式选择、刷新／删除／关闭确认入口均实际打开；查询明确显示未调用模型／网络。
- 会话生命周期：从设置进入归档列表，回读创建时间，打开无 Composer／无变更动作的只读会话，再精确返回归档列表；恢复后回到活动会话；回收站空态已检查。
- 搜索：全屏六分类、时间／大小／还原、MD/PDF/ZIP/DOCX/TXT/JSON/其他类型面、`golden` TXT 结果、历史、当前会话查找与附件定位均实际操作；系统外部打开未强行触发。
- 定时任务：手工草案、字段填写、确认创建、持久列表、暂停、恢复、删除确认与取消均通过。首轮编辑暴露 `update_desktop_reminder_plan` 未进入 Tauri ACL；补权限并重建后，在最终隔离实例完成“创建 → 编辑 → 改名 → 保存 → 列表回读 `最终 ACL 隔离复验计划`”。
- 南枫转写：图片／PDF 模式、原生文件选择器打开／取消、Qwen 音视频模式、SenseVoice“验证中／保留实验”状态均实际检查；未伪造任务或真实 Provider 结果。
- v2 导入：原生 macOS picker 导入成功并显示内容无关的 hash／asset receipt；v2 owner 按合同保持与 v1 当前工作区隔离，不虚构为当前 v1 会话。

## 4. 代码级闭环

- 设置拥有独立页面滚动快照、精确会话返回目的地和只读生命周期页。
- 已确认提醒拥有事务更新、expected revision 冲突拒绝、下次执行重算、运行历史保留及正式 ACL。
- GLM-OCR 拥有文档详情真实字段投影、安全迁移、32 MiB 响应上限、分段 Base64 JSON 请求、Attempt 前置持久化和读取／解析失败持久化。
- Memory 摘要与自定义指令均为真实全屏子页，不再是跳转壳或内联替代。

## 5. 未冒充完成的边界

- 未调用真实智谱／OpenRouter／DeepSeek／Qwen Provider，未产生真实 Token／账单／长文结果。
- 未登录 Google，未连接 Supabase，未执行真实跨设备同步。
- 未点击 macOS 通知授权；有 Team ID 的热／冷通知点按仍待外部验收。
- 未执行删除最终确认、全量本机清理等破坏性动作，只验收确认面与取消。
- 未操作 OPPO，未运行任何 `connected*AndroidTest`、Debug 或仪器测试。

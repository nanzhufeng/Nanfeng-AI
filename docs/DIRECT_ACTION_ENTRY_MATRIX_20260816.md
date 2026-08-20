# 直接执行入口—Owner 矩阵（2026-08-16）

## 决定

应用内一次明确的“选择导入”“发送”或“Compare”就是产品命令，不再显示产品级二次确认。安全边界不变：严格 parser/输入上限、私有暂存、原子写入、item-level 失败隔离、receipt/幂等、凭据隔离和可见错误仍由各自 owner 执行。

| 平台 | 正常入口 | 唯一执行 owner | 结果 | 保留的非产品确认边界 |
|---|---|---|---|---|
| Android | ChatGPT / Claude / 知识库 JSON picker | 三个 `Manage…ExportImportUseCase.select` | 解析后自动逐 item commit；失败保留 task receipt | parser、private copy、atomic commit、失败隔离 |
| Android | ChatGPT / Claude ZIP picker | `AndroidP6KZipIntakeStore.stage` | 已是选择即私有暂存并直接导入 | strict ZIP inventory、format/profile/media owner |
| Android | Composer 普通发送 | `SubmitConversationDraftUseCase` | 本地原子提交；不接 Provider | 普通外发的 P3-J authority owner 没有 Workspace 调用点，未被当作产品 UI 删除 |
| Android | 模型菜单 / Composer / 长按 Compare | `CompareVisibleExecutionOwner.execute` | 直接申请一次 scoped grant 并提交；移除根 Dialog | attachment fail-closed、draft fingerprint、session/receipt、credential/dispatch guard |
| Desktop | ChatGPT / Claude / 知识库 JSON picker | Tauri stage + item commit endpoint | picker 后顺序直接提交每个 pending item；独立失败保留回执 | app-private copy、typed command、atomic commit |
| Desktop | ChatGPT / Claude ZIP picker | `stage_p6k_zip_import_selected` | 原本即直接导入 | strict inventory、receipt/retry/revoke |
| Desktop | 交换包 picker | preflight + typed workspace import | 严格预检后以中性默认工作区名直接导入 | preflight、独立 workspace、typed mutation |
| Desktop | Compare 三个可见入口 | `executeDesktopCompare` | 直接提交请求；Desktop owner 缺失时真实报错，不弹禁用确认框 | 未读 Key、未发送内容；待未来真实 owner 注册 |

## 审计边界

- P8 Agent external authority 与普通聊天的未接通 external-send authority 属于非当前可见产品确认面；它们没有被本次直接执行规则盲删。
- Markdown、PDF、版本化 Knowledge JSON 和 Web snapshot 是知识编辑/抽取工作流：其每项编辑选择仍是内容变更面，而非本轮 ChatGPT/Claude/知识库会话导入或 normal Composer 入口。Web snapshot 还涉及本轮明确禁止的 HTTP；本轮没有触发或改写这些 owner。
- 既定聊天、抽屉、Composer 布局、颜色及控件关系未改；Compare 仅复用现有入口，未重新设计界面。

## 定向证据

- Android：P6-H/P6-I/P6-J direct-import 合同与 `MMO4HCompareVisibleEntryContractsTest` 通过（`./gradlew --no-daemon :app:testDebugUnitTest …`）；该任务仅编译/测试，不产生可安装签名包。
- Desktop：`npm test -- --runInBand` 80/80；`npm run lint`、`npm run typecheck`、`npm run build` 均通过。
- 未读取 Key、未发送 HTTP、未操作 OPPO/emulator、未注入数据库或使用真实导入数据。

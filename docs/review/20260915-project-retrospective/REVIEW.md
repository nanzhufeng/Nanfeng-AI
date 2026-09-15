# 南枫 AI 全库复盘核查报告（2026-09-15）

## 基线、授权与覆盖

- HEAD `ff9fd332636a5e3136e9b5278c25c14697b0a0ee`，业务 checkpoint `50f4b32`；本地 main，0 remote、0 tag，154 条可达提交。日期从 2026-08-20 至 2026-09-15。提交主题和文件统计不能证明实际投入时长或远端发布。
- 只修改开发档案、AGENTS、项目 Skill 与复盘证据；不改业务代码、配置、schema、测试或用户数据，不签名／安装／部署，不触发真实 Provider 或云端操作，不自动提交。
- [files.json](files.json) 记录全部 1,344 个跟踪路径；读取现存字节共 66,637,806 bytes，1,271 文本、72 二进制、1 个缺失图标源。缺失与原先未跟踪截图、output、Supabase 临时目录保留，不吸收为本轮改动。
- [structure.json](structure.json) 对全部文本作结构路由和适用语法检查；[history.json](history.json) 记录全部可达提交逐文件增删统计。语义检查采用下表的真实入口和高风险边界，不声称每一行已作形式证明、每个历史 patch 已重放或二进制已逐图验收。
- 不读取忽略的私有配置／凭据／运行数据库。新脚本仅处理清单路径、不输出正文；统计标记只帮助定位，不自动升级为缺陷。

## 分组语义核查

| 分组 | 本次核查的关键入口／事实 | 证据与边界 |
| --- | --- | --- |
| Android 构建／组合根 | 单 app，JBR／Gradle，手工装配，Release 正式签名来源和 Debug 共用身份风险 | [构建](../../../app/build.gradle.kts)、[组合根](../../../app/src/main/java/com/nanzhufeng/ai/app/AppContainer.kt)；不读签名值 |
| Android 数据 | Room 69、129 entity；67 展示表、68 云回答事实、69 独立标题版本；组合根注册迁移 | [数据库](../../../app/src/main/java/com/nanzhufeng/ai/data/local/NanfengAiDatabase.kt)、[schema](../../../app/schemas/com.nanzhufeng.ai.data.local.NanfengAiDatabase/69.json)；未执行真实设备升级 |
| 普通发送／费用 | 授权存在和指纹检查先于实际请求；本地 Attempt 与跨端回答事实不可混为一体 | [Executor](../../../app/src/main/java/com/nanzhufeng/ai/ai/NormalChatOpenRouterExecutor.kt)、[CloudResponseModelUsage](../../../app/src/main/java/com/nanzhufeng/ai/domain/CloudResponseModelUsage.kt)；真实结算显示仍须逐端验收，不新增估算 |
| 导入／记忆／恢复 | 严格 reader 先于 restore、receipt 重放与空库门；记忆编辑检查完整修订集合 | [restore](../../../app/src/main/java/com/nanzhufeng/ai/domain/WorkspaceExchangeV2AtomicRestore.kt)、[记忆](../../../app/src/main/java/com/nanzhufeng/ai/data/local/RoomMemoryRepository.kt)；真实 ZIP 输入不在本轮范围 |
| Desktop 入口／持久化 | ESM→Tauri→Rust／SQLite，迁移上限 42；独立只读路径避免部分可变 store 锁，前端缓存仍需明确回读 | [lib.rs](../../../desktop/src-tauri/src/lib.rs)、[存储路径](../../../desktop/src-tauri/src/desktop_storage_location.rs)、[权限](../../../desktop/src-tauri/permissions/default.toml)；静态引用不证明全部 IPC 执行正确 |
| 双端同步 | 标题独立版本、字段冲突共用读取／上传规则；完整清单与恢复子集分离；删除任务持久化和续同步防复活 | [Android](../../../app/src/main/java/com/nanzhufeng/ai/data/P7FManualConversationSync.kt)、[Desktop](../../../desktop/src-tauri/src/desktop_account_sync_v1.rs)、[列表合并](../../../desktop/src/cloud-conversation-list-merge.mjs)、[fixture](../../../protocol/fixtures/title-sync-v1.json) |
| 同步安全格式 | 两端当前发送 direct payload；认证、revision CAS 和 hash 不等于端到端加密 | [sealDirect](../../../app/src/main/java/com/nanzhufeng/ai/domain/NfaiSyncV1.kt)、[seal_direct](../../../desktop/src-tauri/src/sync_v1.rs)、[SQL](../../../supabase/migrations/202609130004_p8_direct_google_sync.sql) |
| Supabase／头像 | 默认拒绝表访问，RPC 内 auth.uid、用户文档键、advisory lock／CAS；删除仅指定文档；头像限域、重定向与流式累计限额 | [初始 SQL](../../../supabase/migrations/202608130001_p7c_secure_sync.sql)、[删除](../../../supabase/migrations/202609130005_p8_cancel_direct_sync.sql)、[头像](../../../supabase/functions/google-avatar/policy.mjs)；未执行 Postgres 或线上 RLS |
| Go 网关 | 单独鉴权、offset、完成 hash、带时限 URL 和清理；nonroot Docker | [实现](../../../upload-gateway/main.go)、[测试](../../../upload-gateway/main_test.go)、[Dockerfile](../../../upload-gateway/Dockerfile)；组件未据此接回 App |
| macOS 打包 | 静态构建后 Tauri app；脚本读取 Android local.properties 的公开云配置，并探测系统签名身份，再 strict codesign | [bundle 脚本](../../../desktop/scripts/bundle-macos.mjs)；环境签名参数不代表私钥脱离钥匙串，也不是公证发行 |
| 文档／流程／历史 | 四份当前 Android 合同、P7-F、开发档案、经验、决策、交接、五个 Skill 分层；全库文档结构扫描及历史阶段核对 | [完整档案](../../南枫AI完整开发档案.md)、[Git 历史](history.json)；历史记录不自动成为当前规则 |

## 本轮验证与复用证据

| 验证层 | 结果 | 证据性质 |
| --- | --- | --- |
| 全文件结构／格式（新跑） | JSON 106、XML/SVG 83、ESM 160、Python 6、bash 5、zsh 1 通过；7 XML 解析失败；903 文本未执行语言语法检查；73 非文本／缺失 | [structure.json](structure.json)；不把 Kotlin／Rust／SQL 文本扫描当编译 |
| 协议（新跑） | v1、v2、旧加密 sync golden 均成功 | `protocol/scripts/run-golden.mjs`、`run-v2-golden.mjs`、`run-sync-golden.mjs`；合成数据，不替代 direct 多端业务验证 |
| Supabase（新跑） | 9 tests / 9 passed | `node --test supabase/tests/*.test.mjs supabase/functions/google-avatar/policy.test.mjs`；静态 SQL 和本地头像策略，不执行生产 RPC |
| Go（新跑） | `go test -count=1 ./...` 成功 | 本地 httptest／临时存储，不是公网部署 |
| Desktop 入口审计（新跑） | 143 invoke、184 Rust commands／注册项；缺 Rust 实现 0、缺注册 0、未处理可见 action 0 | `audit-completion-inventory.mjs` 正则路由；40 invoke、87 action 无直接测试字符串引用，仅为补查线索，不等于无行为测试或确定 bug |
| Desktop typecheck（新跑） | 静态 command／keyboard 检查成功 | 自有 `typecheck.mjs`，不是 TypeScript 编译器 |
| Desktop Rust（本轮前紧邻最终回归复用） | 280 passed / 0 failed / 1 ignored | `/tmp/nanfeng-checkpoint-final-rust.log`，业务点 `50f4b32`，本轮不改源码 |
| Desktop Node（同上复用） | 436 tests / 410 passed / 14 failed / 12 skipped | `/tmp/nanfeng-checkpoint-final-js.log`，不能用 9 月 13 日全绿覆盖 |
| Android JVM（前增量最终结果复用） | 1186 tests / 7 failed / 3 skipped；账号与 P7-F 六套 52 无失败 | `/tmp/nanfeng-cloud-delete-android-full.log` 与当前交接；本轮未重跑、不触碰原报告目录 |
| 构建／安装（历史，未重做） | 前一增量 Release lint／assemble、macOS 签名及双端覆盖记录已固化 | [当前交接](../../CURRENT_HANDOFF.md)，`ff9fd33`；不是本次新安装或真实同步验收 |

临时日志用于本机追溯，可能被系统清理；关键数字及命令在本报告固化，业务归档不复制测试原始输出中的正文。

## 文档／代码冲突裁决

| 旧来源／断言 | 当前证据 | 裁决与处理 |
| --- | --- | --- |
| 完整档案 Room 66／127 entity、Desktop 38，旧目录数 | Room 69／129 entity，Desktop 42，文件清单 | 修正档案当前正文；旧日期统计保留为历史，不改迁移源码 |
| 完整档案及 P7-F 第 16 行笼统称“本机加密后才交云端” | sealDirect/seal_direct 输出对象 payload；direct SQL 持久保存 envelope | 当前 direct 不具备该端到端加密保证。修正档案／经验／长期规则路由；P7-F 现有尾注虽承认差异，首部承诺仍冲突，列为待独立校正的安全合同，不假称安全实现已修复 |
| 旧档案当前测试全绿 | 同一业务源码最终 Node 14、Android 7 失败 | 单列当前失败，旧结果加时点。失败中确有多行标题和旧 store-lock 断言；其余未逐项重新归因，不全部宣称只是旧测试问题 |
| 原先各段交接“未安装” | `ff9fd33` 已记录双端安装、哈希和数据指纹 | 认定历史时点不同，不删除历史或重新覆盖。安装不代表同步闭环 |
| 签名环境变量似乎可避免钥匙串 | bundle 脚本仍无条件 find-identity，codesign 使用身份私钥 | 当前仍有钥匙串依赖；一次无密码签名不代表永久解决。不改 ACL、口令、签名或打包业务脚本 |
| 旧网关 README 的 App 中转路线 | 当前组合根使用官方 transport／材料桥 | 组件历史说明不代表当前调用；报告保留差异，不擅自接线 |
| 历史 UI dump 被当标准 XML | 7 份 docs/evidence/p6f2d-android-*.xml 解析失败 | 证据包装缺陷，保留原件，不当 Android 资源失败 |
| 跟踪图标源应该在工作树 | 清单记录该路径 missing，复盘开始前已删除 | 不恢复、不纳入本轮删除；删除原因未确认 |

## 风险与后续路线（建议，不是新增授权）

1. **安全承诺冲突优先。** 确认用户接受的同步保护目标，再统一 direct／旧加密的现行合同与 UI 披露；当前没有证据可宣称线上端到端加密。SQL 的 hash／byteCount 有格式／范围门，但 direct validator 未重算实际 payload hash，不能把它称为服务端内容真实性证明；尚未做恶意客户端隔离实验。
2. **真实同步与旧数据仍有缺口。** 独立版本防止以后误判，不会恢复已分歧的历史标题；任何真实改名必须有来源和明确选择。千问／智谱等实际结算、双方列表和删除闭环未在本次复盘重新验收。Android owner 的真实 HTTP→Room 注入行为边界、Desktop 全封包大库延迟仍待补。
3. **完整回归未绿。** Android 失败涉及 scrim、稍后看、新会话、三项会话行无障碍／菜单及 PDF；Node 14 项涉及侧栏、标题、定价刷新、复制、菜单与旧锁断言等。先按当前产品要求分类，再做单一增量，不为测试恢复用户反对的多行标题。
4. **维护与可观测性。** lib.rs 28,134 行、ConversationWorkspace.kt 11,583 行、app.mjs 6,485 行；它们是审查成本，不是独立故障证据。按 owner 加行为保护后再拆分；40 invoke 无直接测试引用先补查真实测试链。
5. **部署边界。** macOS 仍为开发签名，永久无密码方案、公证／更新、Windows 凭据与安装器、线上 Supabase 跨用户负向验证、真实 Provider 账单和公网网关均未被本轮确认。无 remote／tag，不宣称远端发布。

## 产物与恢复

更新现有完整档案、AGENTS、五个项目 Skill、可迁移经验；新增本目录 6 份证据和文档同步 Skill 的结构检查脚本。既有 Skill UI 元数据保留（名称／默认提示仍匹配，不另造同义 Skill）。全部旧跟踪文本可由 `ff9fd33` 回读；本轮不自动提交。收尾以清单 hash 校验所有非文档／流程跟踪文件仍与开始一致，缺失图标状态不变；具体检查结果见 [validation.json](validation.json)。

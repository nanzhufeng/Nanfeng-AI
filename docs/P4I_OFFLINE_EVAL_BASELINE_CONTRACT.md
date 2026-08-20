# 南枫 AI P4-I 版本化离线 Eval 基线合同

日期：2026-08-13  
状态：P4 的第九个本地增量；Schema 13→14。它是本地回归与人工审阅基础设施，不是 Provider、Prompt、模型质量、Token、TTFT、费用、缓存收益或用户价值证据。

## 所有权与版本

```text
打包只读 EvalDataset / EvalFixture / EvalCase
→ RunOfflineEvalUseCase（纯本地事实断言）
→ EvalRun / CaseResult / DeterministicAssertion（append-only Room）
→ HumanScore（独立 append-only Room）
→ OFFLINE_LOCAL JSON + Manifest + SHA-256
```

| 概念 | 唯一所有者 | 版本或稳定 ID | 禁止事项 |
|---|---|---|---|
| Dataset、Fixture、Case、Expected/Forbidden Facts、RedTeamCase | `P4IOfflineEvalDataset` | dataset / fixture / domain 版本与 fixture manifest hash | 不写进生产 Conversation、Knowledge、Memory 表；不执行正文 |
| 自动结果与确定性断言 | `RunOfflineEvalUseCase` | EvalRun ID、assertion version | 不比较回答字符串，不生成模型分数、Token、TTFT、cost 或 cache 指标 |
| 人工评分 | `HumanScore` | rubric version、审阅人本地别名、时间 | 不与自动断言混同；可为未评分/不适用；不保存账号或敏感身份 |
| 可导出报告 | `ExportOfflineEvalReportUseCase` | report / manifest v1、SHA-256 | 不含 Key、Prompt、Provider payload、URI/路径、附件字节或生产正文 |

Fixture 是版本化、打包、只读的非生产测试材料；Room 只追加运行、结果、断言和评分事实。生产领域对象与 Eval 对象不得共表、复用 ID 语义或通过 UI 暗门互相写入。

## 覆盖与红队边界

基线至少登记并以机械事实断言覆盖：当前 Conversation 根→叶路径而非兄弟；draft/Attachment/Tool 排除；Project、Memory、Knowledge scope；Knowledge 不自动进入 Context；高敏拒绝；revision/hash 竞态；中文、英文、Markdown/代码和长内容边界的保真/安全渲染；流事件去重/恢复。

Red team fixture 仅作为不可信惰性正文，绝不执行其中的自然语言命令。覆盖 prompt injection、高敏模式、跨 Project/Conversation、URI/路径/附件泄漏、关系/导入自动注入、旧 revision/hash 和畸形流事件。所有 case 还断言 `NO_EGRESS`；`OpenRouterEgressPolicy.Disabled` 不变。

自动断言只允许报告可机械证明的事实覆盖、禁止事实泄漏、范围、顺序、状态、hash 与安全错误边界。它们不评价“回答好坏”，也不能用 Mock 输出、fixture 字符串相等或零费率伪装真实质量/真实价值。

## 运行、评分、比较与报告

- 每次运行固定 app、schema、domain、dataset、fixture manifest、assertion、rubric 与安全摘要版本；运行和 case/assertion 结果只能插入，不更新或覆盖。
- 两次运行只在相同 dataset/version 下比较确定性 case verdict；不可比较时明确拒绝。重复运行不生成真实性能、Provider 或价值声明。
- 人工维度为相关性、事实性、完整性、安全性、可追溯性；score 为 `1..5` 或 `null`（未评分/不适用）。评分只追加，记录本地别名、rubric、时间和受限备注。
- 导出是 app-private 的版本化 JSON 与独立 Manifest：临时文件 `fsync`、原子改名、同文件回读、SHA-256、再读 Manifest。报告明确 `OFFLINE_LOCAL` 与 `realServiceVerified=false`；篡改、路径分隔符或 hash 不符均拒绝。

## Room、UI 与验证

Schema 13→14 仅追加 `offline_eval_runs`、`offline_eval_case_results`、`offline_eval_assertions`、`offline_eval_human_scores` 及索引；不 wipe、不改写 P1–P4-H 表、私有资产或业务正文。内建 dataset 通过明确的“离线 Eval 基线”产品卡进入，没有 build-type 后门。

最小 UI 显示 dataset/run 列表、开始本地回归、运行中/通过/失败/未评分、单例断言、红队 case、人工评分录入、两次运行比较及报告导出。所有 App 自有 Dialog 和选择面内容底色为 `#FFFFFFFF`。

定向测试覆盖 fixture 版本/篡改语义、自动断言、红队惰性隔离、未评分、run immutability/重建、比较、报告 roundtrip/hash/tamper 与 13→14 迁移；再分别运行全量 P1–P4-H 回归、Lint、正式签名 Debug/Release、v2/v3 和 API 35 `emulator-5554` 可见链/进程重建。上述本地证据不替代真实 Provider/费用/模型质量/用户价值、OPPO、图标或发布验收。

达到本地 Eval 基线即停止。摘要、压缩、缓存、JSON/网页/其他 Adapter、同步/账号、Tool/Agent 和真实模型调用仍是各自独立阶段；P4-I 不是 P4 或项目终点。

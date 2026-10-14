# 同次提问的检索费用合并（2026-10-13）

用户最终口径：多次Luna检索在费用统计中合并，原模型回答单列。回答页脚仍是该次提问的检索＋回答总费用。

## 已实现

- Android费用入口先合并归因/审计的唯一Call，再按root attempt＋会话＋检索Provider/请求模型/冻结名称分组。相同提问的六次检索只显示一条费用和Token合计；每个ANSWER继续独立显示。`ledgerCostFacts`供类别和本机总计展开原费用，部分未知不会丢掉已知金额。
- Desktop原生IPC从不可变SQLite账本投影已有`attemptId`，ESM以同一root/会话/请求模型/冻结名称分组；无root的历史记录不猜配。静态打包包含新模块。原币费用、用量、pending/final对账和账本写入规则未改。
- 人民币统计先换算、合计，再取整。修正Desktop原有逐Call取整可能产生的末位微小差额，未改参考汇率或原币金额。单条明细保留六位小数，回答页脚保留四位。
- 明确零、全部未知、部分未知/失败/pending、Token溢出、不同root/模型/渠道及无root旧记录均有行为覆盖。Mac缺少冻结渠道名称的旧metadata也不猜配，已有1/6红灯及最终全量绿灯。补充防护：同一旧消息的不同attempt归因不得因无物理Call而少算。
- 没有修改正常OpenRouter/官方自身联网、Luna搜索请求或原RC回答路径，没有新增提示、按钮、Provider、迁移或Schema。

## 红绿和回归

| 层 | 本轮结果 | 能证明的范围 |
|---|---|---|
| Android初始红灯 | 六次搜索仍有七条明细；2/3失败 | 捕获逐物理调用展示的原始问题 |
| Desktop初始红灯 | 7条而非2条、pending未合并；2/3失败 | 捕获展示分组缺失 |
| Android旧归因补测 | 1/5红灯→修复→最终全量绿 | 不丢同一消息的不同旧attempt费用 |
| Android最终JVM | 1477PASS／9skip，1486总计，319套件，0失败/错误 | 真实Domain、Robolectric/Room与重开；不是Android原生进程/UI或服务验收 |
| Android构建 | assembleDebug PASS | 当前源码编译/APK生成；未安装 |
| Desktop Rust | 424PASS／33ignored，0失败 | 原生SQLite owner重开、七笔原账→IPC根身份；不是Mac WebView/正式UI |
| Desktop Node | 551PASS／12skip，563总计，0失败 | 实际设置渲染函数两条明细、合计/未知事实、静态依赖图 |
| Desktop构建/检查 | 静态build、lint、静态typecheck PASS | 打包模块可达与语法/既有边界；不是签名app构建或正式安装 |
| 维护门 | 25合同测试、114合同/40领域治理与owner审计、20＋1模型/7密钥组门PASS | 离线一致性；不替代真实服务 |

合成固定例：6笔Luna各USD 0.1＋回答CNY 0.018087。两端检索明细¥4.032185、回答明细¥0.018087、合计¥4.050272，页脚按既有四位投影。Android Room重开和原生Mac SQLite读回均仍为7笔原始调用，幂等重放不增加记录。

本轮未调用真实Provider、未操作正式Android/Mac安装或正式库、未运行任何connected Android测试。9项Android opt-in skip、33项Rust ignored、12项Node skip均保留。原周报Mac UNKNOWN、RC Pro404、Muse403及原生picker/生命周期/扫描媒体缺口不由费用改动宣称解决。

## 证据与恢复

`verification-summary.json`保存本轮结构化结果，`source-hashes.json`与`source-delta.patch`绑定本轮源码和独立增量。底层HEAD仍为既有`dafc64d9b33661dc4f792fd1434ff1ddca9cdfa3`，不是本轮WIP快照；没有commit/push/reset/clean/stash。

本轮修改前的限定原件与红绿/全量日志保存在`/tmp/nanfeng-search-cost-group-20261013/before`及同目录日志。不得通过恢复HEAD覆盖既有WIP。18:54正式安装包没有包含这次费用合并；安装与本轮代码验证分开。

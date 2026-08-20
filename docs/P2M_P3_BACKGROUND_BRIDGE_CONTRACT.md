# 南枫 AI P2-M → P3 后台桥接合同

日期：2026-08-15  
状态：仅后台 wiring、默认 fail-closed；不改变既有 P2-M 可见确认或执行结果

## 精确接点

唯一接点是既有 `ModelSettingsViewModel.confirmRealServiceOnce()` 完成可见勾选门后调用的 `P2MRealServiceExecutor.execute(expectedRunSpecFingerprint)`。桥接在 executor 复核同一 RunSpec 指纹后运行；不改 ViewModel、Compose、文案、布局或控件，也不新增入口。

## 边界

- 没有 `P2MExistingVisibleConfirmation` 或其 RunSpec 指纹不一致时，桥接在 preflight 前拒绝。
- Ready 后只把既有 P2-M 合成 text fixture 放入瞬时 P3 preflight；附件列表固定为空，禁止读取或外发文件。
- AppContainer 注入 `RealTextExecutionCoordinator` 的默认 ports：reservation 立即拒绝，所以没有 runtime receipt、Usage Ledger、Provider transport、HTTP、Key bytes 或 Authorization。
- bridge 结果不改变既有 P2-M DryRun、nonce、legacy executor、UI state 或用户可见结果；它没有 schema/Room/SQLite 写入。

## 验证

定向 fake/stub 合同覆盖未确认/配置不完整 fail-closed、已确认 ready→preflight→默认 coordinator 拒绝、credential presence-only 与无附件；静态核查确认 AppContainer 只注入默认端口，P2-M executor 在 legacy DryRun 之前调用桥接且忽略其结果。真实 HTTP、Key、账本、附件 egress、UI 和设备验收均不在本增量内。

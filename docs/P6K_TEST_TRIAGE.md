# P6-K 全量 JVM 失败分类

日期：2026-08-28
状态：P0 当前分类基线；只约束本轮全量 JVM 收口，不替代产品合同或真实 ZIP／真机验收。

## 1. 现场基线

- 仓库：`main@af218e1d45b5`
- 命令：`JAVA_HOME=/Applications/Android Studio.app/Contents/jbr/Contents/Home JAVA_TOOL_OPTIONS=-XX:TieredStopAtLevel=1 ./gradlew --no-daemon --max-workers=1 :app:testDebugUnitTest --continue`
- XML：`815 tests / 59 failures / 0 errors / 3 skipped`
- 失败分布：15 个测试类；其中 `P6DConversationRowAccessibilityContractsTest` 为 40 项。
- 与旧交接的 `60 failures` 不同：本轮必须以当前工作树重新生成的 XML 为准；当前两份入口文档的同步改动会被静态合同读取。

3 个 skipped 均为真实 ZIP opt-in 验收，未向普通全量命令提供 `NANFENG_AI_P6K_NEW_ZIP`／`NANFENG_AI_P6K_OLD_ZIP`。它们不计入通过；P0 收口后仍须以真实包单独运行并确认 `skipped=0`。

## 2. A/B/C/D 分类

| 类别 | 数量 | 结论 |
| --- | ---: | --- |
| A 真实产品回归 | 0 | 当前没有证据证明 59 项中存在产品行为回退；修复后仍需重跑同层测试确认。 |
| B 过时合同或夹具 | 58 | 旧静态源码锚点、旧视觉值、旧函数顺序或已被安全规则拒绝的测试模型夹具。只可依据当前合同和现行 owner 修订，不得为了变绿删除语义断言。 |
| C 环境／测试结构耦合 | 1 | 纯金额格式函数与 Compose 大文件同类加载，普通 JVM 因 `android.graphics.Typeface.create` 未 mock 而在断言前失败。应拆出纯函数边界，不跳过。 |
| D 无法归属的历史失败 | 0 | 所有失败均已定位到明确 owner；不存在可作为背景噪音保留的未归属失败。 |

## 3. 逐类依据与允许修复

| 测试类 | 数量 | 分类 | 当前依据 | 允许修复 |
| --- | ---: | --- | --- | --- |
| `SystemBarsAppearanceContractsTest` | 1 | B | 旧测试要求窗口重新聚焦后强制浅色系统栏；当前皮肤由 Compose 统一驱动，深色模式禁止被改回深色图标。 | 改为断言初始浅色 fallback 与 Compose 按当前皮肤更新，不恢复聚焦时强制浅色。 |
| `DirectExecutionApplicationOwnerContractsTest` | 1 | B | 夹具把 Terra 映射到 `openrouter/test-v1`；现行精确模型路由会 fail-closed 拒绝该别名。 | 夹具改为精确 `openrouter/gpt-5.6-terra`，保留原确认、费用和一次性消费语义。 |
| `RealTextExecutionPreflightContractsTest` | 4 | B | 同上；失败发生在预期费用／附件分支之前。 | 只修精确模型夹具及相应期望，不放宽模型路由。 |
| `AssistantFooterCostDisplayTest` | 1 | C | `assistantFooterCostDisplay` 位于 `ConversationWorkspace.kt`；JVM 加载文件类时先触发未 mock 的 `Typeface.create`。 | 将纯金额格式函数移入无 Android 静态初始化的 Kotlin owner，保持 UI 调用和舍入语义不变。 |
| `ConversationWebSearchComposerContractsTest` | 1 | B | 开关尺寸已由共享 `SettingsSwitch`／`SettingsControlDimensions.kt` 拥有，Composer 不应复制 `SettingsSwitchTrackWidth`。 | 断言共享控件调用与唯一尺寸 owner。 |
| `FBP6041ComposerGlyphContractsTest` | 1 | B | 当前图标合同使用圆端 `Icons.Rounded.Stop`；旧断言仍要求 `Icons.Filled.Stop`。 | 更新为圆端图标，保留大小、命中面和停止语义。 |
| `P5AAdaptiveNavigationContractsTest` | 1 | B | 当前合同规定 Composer 自行消费底部安全区；会话根只消费横向 inset。 | 更新为 `WindowInsetsSides.Horizontal`，保留 Activity 的 `adjustResize` 唯一权。 |
| `P6DConversationRowAccessibilityContractsTest` | 40 | B | 大量旧断言要求静态橙色、纯白弹层、Filled 图标、旧菜单／函数名、旧 dp/sp、已删除重复导出入口及旧合同措辞；与当前会话／设置合同直接冲突。 | 按当前合同分组更新可执行锚点；保留交互、无障碍、隐私和生命周期语义，禁止恢复历史视觉值。 |
| `P6F2BImagePreviewUiContractsTest` | 1 | B | 旧测试全文件禁止 `http(s)` 字符串；当前合同允许有效来源链接由系统浏览器打开。 | 将禁止范围收窄到图片预览实现，不阻止合法来源入口。 |
| `P6F2CPdfPreviewUiContractsTest` | 1 | B | 同上。 | 将禁止范围收窄到 PDF 预览实现。 |
| `P6F2DVideoPreviewUiContractsTest` | 3 | B | 圆端播放图标、共享主题分隔色和预览可用条件已替代旧精确源码字符串。 | 按当前圆端图标和语义 owner 更新断言，保留自动播放、双击、时间线与手势合同。 |
| `P6IClaudeExportImportUiContractsTest` | 1 | B | 数据与存储已改为 JSON／ZIP 分组卡和直接选择器；旧工程化说明文字已被当前设置合同删除。 | 断言系统 JSON 选择、私有有界读取、直接提交与结果入口，不恢复冗余小字。 |
| `SettingsCategoryCardContractsTest` | 1 | B | 当前合同的卡内 canvas 分隔为 `4dp`；旧测试仍要求 `8dp`，且依赖易碎的函数边界字符串。 | 使用共享 `SettingsGroupedCardDividerHeight = 4.dp` owner，并保留四组大卡。 |
| `SettingsUiSimplificationContractsTest` | 1 | B | `ConversationImportSection` 已内联到数据与存储读取模型；旧 substring 锚点不存在。 | 改为从稳定的当前函数边界核对 JSON／ZIP 卡与结果行。 |
| `WorkspaceExchangeV2DocumentsUiContractsTest` | 1 | B | 旧测试以首个 `MaterialTheme(` 作为 picker 结束锚点，代码重排后该锚点位于 picker 之前。 | 收窄到 restore picker 声明本身，保留 UI 不解析、受控 bridge 和内容无关状态断言。 |

## 4. P0 修复门

1. 禁止修改当前产品合同来迎合旧测试。
2. 禁止放宽模型精确路由、ZIP 归属、安全 MIME、私有复制、迁移或设备门禁。
3. 静态合同必须改为稳定 owner／语义断言；不得只删除失败断言。
4. 修复后完整 JVM 必须为 `failures=0, errors=0`；3 个 opt-in skip 逐条保留理由，并以真实 ZIP 分层运行到 `skipped=0`。
5. P0 完成后只提交本轮分类、测试和必要纯函数边界，作为 P1 回滚锚点；不得混入并行任务的文档改动。

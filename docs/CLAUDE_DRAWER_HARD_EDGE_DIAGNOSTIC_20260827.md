# 南枫 AI Android 左侧栏顶部硬边灰底：交给 Claude 的完整诊断材料

更新：2026-08-27。本文只记录已核实的现象、源码与版本事实；其中“候选根因”不是结论。请先定位，再提出最小补丁。不要根据本文中的历史代理回复推断实现已经正确。

## 1. 任务目标

Android 普通对话左侧栏顶部存在一块用户明确指出的、横跨抽屉宽度且底边笔直的错误灰色/灰白承托底。用户要删除它。

保留要求：

- App 图标和“南枫 AI”位于状态栏安全区下，固定在最上层，滚动时不动。
- 标题行不应该有白底、灰卡、托盘或其他实体承托面。
- 顶部和底部允许保留与主界面**相同参数**的软灰渐变加强；它必须是连续渐变，不能变成有明确边界的灰色矩形。
- 会话列表正常从固定标题下方滚过是正常行为；不要把这件事本身当成 bug。
- 不要为了删硬底而删除列表、标题、搜索、已计划、设置、新对话或改变它们的业务行为。

## 2. 用户截图和可见现象

下列文件均在本机，Claude 如可访问本机请逐张查看：

| 文件 | 时间/状态 | 用途 |
|---|---:|---|
| `/var/folders/bn/4pttdwp96xj5mv8rbhdlscn40000gn/T/codex-clipboard-851a5e31-3439-447c-8c5d-05ed32239228.jpg` | 22:45 | 用户首次明确指出软件名上方有硬边灰底。 |
| `/var/folders/bn/4pttdwp96xj5mv8rbhdlscn40000gn/T/codex-clipboard-e9c9fd50-154a-44e3-b52c-f3b03acefa56.png` | 同一问题 | 同一现象的重复截图。 |
| `/var/folders/bn/4pttdwp96xj5mv8rbhdlscn40000gn/T/codex-clipboard-e6f5dd0d-02dc-46a1-862b-8199a9073761.jpg` | 00:19 | 错误的独立渐变覆盖层：搜索/已计划被冲白。该层后来已删除。 |
| `/var/folders/bn/4pttdwp96xj5mv8rbhdlscn40000gn/T/codex-clipboard-d6e703d9-39e7-4593-97fb-91975adbefcb.jpg` | 00:34 | 删除独立覆盖层后，用户仍看到顶部硬边。 |
| `/var/folders/bn/4pttdwp96xj5mv8rbhdlscn40000gn/T/codex-clipboard-658d243c-2cce-4cec-b2ff-20028d9d546c.jpg` | 00:35 | 标题下方仍有明显灰色实体面。 |
| `/var/folders/bn/4pttdwp96xj5mv8rbhdlscn40000gn/T/codex-clipboard-3e401f45-b87c-4ace-a117-6cfe1a021d94.png` | 00:35，放大裁切 | 用户指认的灰底区域：状态栏下方、横跨抽屉宽度、底部一刀切。 |

注意：用户已经明确表示，**不要再把“会话内容在固定标题下方滚动”解释为根因**。那是预期行为。重点是那块实体、硬边、灰色/灰白的承托面本身。

## 3. 当前安装与工作区状态

- 设备：OPPO Find N5，包名 `com.nanzhufeng.ai`。
- 已安装包：`66 / 0.3.0-p10j`，2026-08-27 00:34:01 覆盖，SHA-256 `85acc1ebf294d3b57b1a5dce99ed54157b092fcbf94259ac674ab8082fcaa31f`。
- 该已安装包已删除过一个独立的全屏渐变 `Box`，但用户截图仍显示硬边。
- 工作区之后还有**未安装**变动：普通抽屉中“搜索／已计划”两个 `Surface` 的颜色由 `ConversationDrawerQuickActionSurface` 临时改为 `Color.Transparent`。这不是已验证结果，Claude 不应将其当作已在设备上可见的证据。
- 当前工作区含大量早先未提交的用户/代理改动；禁止用 `git reset --hard`、整文件回退或切换旧版本覆盖当前工作区。

## 4. 当前源码结构（核查对象）

主文件：`app/src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt`。

### 4.1 外层抽屉 Sheet：连续基底的第 1、2 次绘制

`ConversationWorkspace.kt:702-720`：

```kotlin
ModalNavigationDrawer(
    drawerContent = {
        ModalDrawerSheet(
            drawerShape = RectangleShape,
            drawerContainerColor = ConversationDrawerBaseSurface,
            drawerTonalElevation = 0.dp,
            windowInsets = WindowInsets(0, 0, 0, 0),
            modifier = Modifier
                .requiredWidth(drawerWidth)
                .fillMaxHeight()
                .background(ConversationDrawerBaseSurface)
        ) { ... }
    }
)
```

需要判断：`drawerContainerColor` 与额外 `.background(ConversationDrawerBaseSurface)` 是否构成了用户看到的顶端硬边承托面，或仅是相同颜色的冗余绘制。不要仅凭名称判断。

### 4.2 普通抽屉根：连续基底的第 3 次绘制

`ConversationWorkspace.kt:1839`：

```kotlin
Box(modifier = Modifier.fillMaxSize().background(ConversationDrawerBaseSurface)) {
```

该 `Box` 包住：可滚动 `Column`、固定标题 `Row`、底部设置/新对话控件。它与外层 Sheet 共同构成多层同色背景。

### 4.3 实际滚动内容

`ConversationWorkspace.kt:1842-1972`：

```kotlin
Column(
    modifier = Modifier.fillMaxSize()
        .statusBarsPadding()
        .padding(start = 5.dp, end = 5.dp, top = 16.dp)
        .verticalScroll(rememberScrollState())
        .clickable(...),
    verticalArrangement = Arrangement.spacedBy(8.dp)
) {
    Spacer(Modifier.height(drawerIdentityVisualSize))
    // 搜索、已计划、会话分组、会话行
    Spacer(Modifier.height(drawerScrollableEndInset))
}
```

当前普通抽屉的 `Column` **没有**调用 `conversationEdgeGrayFade(...)`。这不是“立即加回独立覆盖层”的授权；需要判断主界面做法应如何安全复用到真实滚动 viewport。

### 4.4 固定标题

`ConversationWorkspace.kt:1973-1995`：

```kotlin
Row(
    modifier = Modifier.align(Alignment.TopStart)
        .zIndex(1f)
        .statusBarsPadding()
        .padding(start = 5.dp, end = 5.dp, top = 16.dp)
) {
    Image(...)
    Text("南枫 AI", ...)
}
```

标题自身没有显式 `background` 或 `Surface`。它是在根 `Box` 内、滚动列之后绘制。

### 4.5 搜索/已计划实体灰卡

已安装版本中，`ConversationWorkspace.kt:1864-1910` 的两个 `Surface` 使用：

```kotlin
color = ConversationDrawerQuickActionSurface
```

浅色令牌原值为 `#EBEEEC`（见 `NanfengAiApp.kt:165-166, 268`）。未安装工作区现在临时写为 `Color.Transparent`。请区分：

1. 该灰卡本身是否是用户裁切图里看到的硬灰底；
2. 它滚到固定标题下方时为何露出；
3. 用户是否要求删除卡片本身，还是要求移除其在标题区域形成的硬边。

不要把这一项与独立全屏灰层混为同一个对象。

### 4.6 共享软渐变实现

文件：`app/src/main/java/com/nanzhufeng/ai/ui/ConversationEdgeGrayFade.kt:12-65`。

```kotlin
internal fun Modifier.conversationEdgeGrayFade(
    topBand: Dp = 128.dp,
    bottomBand: Dp = 112.dp,
    edgeColor: Color = PageBackground,
)
```

顶部停靠色不透明度为 `1.00`，中段为 `0.90 / 0.62 / 0.22`；底部末端为 `0.98`。该函数使用 `drawWithContent`，先画内容、后画渐变。

主界面已验证的调用形态在同一 `ConversationWorkspace.kt:899-908`：

```kotlin
LazyColumn(
    state = listState,
    contentPadding = ConversationTranscriptContentPadding,
    modifier = Modifier.fillMaxSize()
        .background(Color.Transparent)
        .then(transcriptScrollCaptureVisualModifier),
)
```

其中 `transcriptScrollCaptureVisualModifier` 在非长截图时为 `Modifier.conversationEdgeGrayFade()`。若建议将该模式用于抽屉，必须说明为何会固定在 viewport，而不是再次引入独立 sibling `Box` 或让渐变跟随内容移动。

## 5. 当前令牌与现行合同

`NanfengAiApp.kt:154-166`：

```kotlin
PageBackground = #F7F7F7
ConversationDrawerBaseSurface = #FFFFFF
ConversationDrawerCanvas = #F2F2F2
ConversationDrawerRowSurface = ConversationDrawerBaseSurface
ConversationDrawerQuickActionSurface = #EBEEEC
```

当前合同：`docs/ANDROID_CONVERSATION_UI_CURRENT_CONTRACT.md:52-53`。这里存在完全重复的长规则，且同时写了：

- 抽屉必须连续亮白基底；
- 不得有硬边灰底/灰卡；
- 顶/底需要 `#F2F2F2` 软渐变；
- 搜索/已计划又需要更深一阶灰色表面。

请把合同重复和“灰卡是否允许”的歧义视为文档问题，而非代码根因。

## 6. 已执行且确认无效/不可作为最终方案的尝试

1. 曾在普通抽屉根 `Box` 内增加一个独立全屏 sibling `Box`，其上调用 `conversationEdgeGrayFade(edgeColor = ConversationDrawerCanvas)`；用户截图显示它把搜索和“已计划”冲白，并出现错误硬边。该 sibling 已从已安装版本删除。
2. 曾把“会话文字在固定标题下方经过”误判为根因。用户已明确否定；这是正常滚动关系。
3. 曾计划把搜索/已计划也固定到顶部；尚未安装，且用户没有要求改变其滚动语义，不应作为默认方案。
4. 当前未安装工作区把搜索/已计划两项 `Surface` 改透明，只是按最近指认做的局部尝试；它没有真机截图，不应宣称已解决。

## 7. 历史版本/可回退性核查

已盘点：

| 载体 | 状态 | 是否可直接复用 |
|---|---|---|
| `delivery/p5d`、`delivery/p6a` APK | 2026-08-13，版本码 39/40 | 不可：旧抽屉、标题不固定。 |
| `docs/evidence/...` APK | 2026-08-14，版本码 51 | 不可：两周前旧 UI。 |
| Git `a969b8f` / `5bced98` | 2026-08-24 | 不可：`ModalDrawerSheet(PageBackground)`，标题与搜索都在同一滚动 `Column`。 |
| 版本码 66 的历史覆盖 `2fc…`、`7085…`、`2b2…`、`b66…`、`3270…` | 交接记录中只有哈希 | 不能直接复用：原 APK 已被后续构建覆盖，相关 UI 改动没有提交。 |

任务历史中还确认过一个旧错误：曾把 `ConversationDrawerCanvas` 整块绘制到状态栏/抽屉根，产生整片硬灰画布。不要恢复这种结构。

## 8. 给 Claude 的具体输出要求

请按以下顺序答复：

1. 基于截图，明确指出用户所说“硬边灰底”最可能对应哪个具体 Compose 节点；若有两个可见候选，按概率排序并说明截图特征。
2. 审查第 4 节的三个连续背景绘制节点，判断哪些是冗余且可能形成可见边界。
3. 与第 4.6 节主界面 `LazyColumn` 模式逐项对照，给出一个**最小** Kotlin 补丁方案：不得有独立的全屏灰色 sibling `Box`、不得给标题加实体白/灰底、不得改变标题固定行为。
4. 明确搜索/已计划灰色 Surface 应保留、透明、还是仅在标题下方被裁切/渐变；不能模糊回答。
5. 给出最小验证计划：源码合同测试、Debug 编译、目标真机单张截图验证。不要建议 `connectedAndroidTest`、卸载或清数据。
6. 如果无法从证据确定某节点，明确写“不确定”，不要声称已经找到。

## 9. 执行安全边界

- OPPO Find N5 有用户数据；只允许同签名正式 Release 的 `pm install -r --user 0` 覆盖。不得卸载、清数据、降级或安装旧 APK。
- 永久禁止运行 `connected*AndroidTest`。
- 当前工作区有大量无关未提交变更；只允许目标化编辑，禁止 reset/clean/checkout 覆盖。
- 任何构建或安装成功都不能替代该截图状态的真机视觉验收。

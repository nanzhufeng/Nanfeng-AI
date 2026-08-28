package com.nanzhufeng.ai.ui

import java.io.File
import androidx.compose.ui.graphics.Color
import com.nanzhufeng.ai.domain.MessageRole
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class P6DConversationRowAccessibilityContractsTest {
    private val source = File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()
    private val appSource = File("src/main/java/com/nanzhufeng/ai/ui/NanfengAiApp.kt").readText()
    private val dialogDismissSource = File("src/main/java/com/nanzhufeng/ai/ui/P5ADialogDismissBehavior.kt").readText()
    private val currentUiContract = File("../docs/ANDROID_CONVERSATION_UI_CURRENT_CONTRACT.md").readText()

    @Test
    fun `FB-P6-111 current Android conversation UI contract has one precedence route and executable anchors`() {
        for (clause in listOf(
            "Android 会话、抽屉、Composer、搜索与暗色皮肤的唯一视觉／交互正文",
            "普通与批量行分别以 `36dp`／`44dp`",
            "会话右滑带依次为置顶／取消置顶、收藏／取消收藏、重命名、删除",
            "窄屏为 `min(320dp, 可用宽度)`",
            "点击遮罩关闭",
            "禁止遗留橙色常量",
            "会话标题使用 `14sp / 18sp / Normal`",
            "日期分隔线在同一会话内始终使用统一的低对比中性分隔色",
            "新 Assistant 内容到达而该会话未打开时",
            "来源只有有效 http(s) 链接时才显示入口",
        )) assertTrue("missing current UI contract clause $clause", currentUiContract.contains(clause))
        for (legacyContract in listOf(
            "../docs/CHAT_FIRST_INTENT_ORGANIZATION_CONTRACT.md",
            "../docs/P6F_CONVERSATION_TRANSCRIPT_PRESENTATION_AND_MESSAGE_ACTIONS_CONTRACT.md",
            "../docs/P6G_MODEL_SELECTION_AUTO_ROUTER_CONTRACT.md",
            "../docs/P3E_CONVERSATION_MANAGEMENT_SEARCH_EXPORT_CONTRACT.md",
            "../docs/P6F2_UNIFIED_SEARCH_HISTORY_AND_LOCAL_CONTENT_PREVIEW_CONTRACT.md",
            "../docs/P5A_ADAPTIVE_ACCESSIBILITY_BASELINE_CONTRACT.md",
            "../docs/P6D2_CROSS_PLATFORM_COMPOSER_ATTACHMENT_ADAPTER_CONTRACT.md",
        )) {
            assertTrue("legacy contract must route Android UI to the current contract", File(legacyContract).readText().contains("ANDROID_CONVERSATION_UI_CURRENT_CONTRACT.md"))
        }
        for (token in listOf(
            "gesturesEnabled = true",
            "val rowHeight = if (batchEditing) 44.dp else 36.dp",
            "modifier = modifier.width(176.dp).height(rowHeight)",
            "fun dismissRevealedConversation(): Boolean",
            "private val ComposerModelDisplayWidth = 88.dp",
            "onOverlayBack",
        )) assertTrue("current UI implementation drifted from its contract anchor $token", source.contains(token))
    }

    @Test
    fun `conversation drawer keeps full canvas swipe and completes opening quickly`() {
        for (token in listOf(
            "gesturesEnabled = true",
            "Modifier.quickConversationDrawerOpen",
            "QuickDrawerOpenTravel = 36.dp",
            "kotlinx.coroutines.yield()",
            "if (openRequested) onOpen()",
            ".then(quickDrawerOpenModifier)",
        )) assertTrue("quick drawer gesture must retain $token", source.contains(token))
    }

    @Test
    fun `FB-P6-110 every app dialog shares outside dismissal and a guarded inward edge swipe`() {
        for (token in listOf(
            "internal fun AlertDialog(",
            "internal fun Dialog(",
            "P5ACenteredDialogScrimAlpha = 0.12f",
            "private fun P5ALightDialogScrimEffect()",
            "setDimAmount(P5ACenteredDialogScrimAlpha)",
            "WindowManager.LayoutParams.FLAG_DIM_BEHIND",
            "P5ADialogEdgeDismissEffect(onDismissRequest)",
            "activationEdge = with(density) { 56.dp.toPx() }",
            "completionDistance = with(density) { 72.dp.toPx() }",
            "abs(horizontalTravel) >= abs(verticalTravel) * 1.3f",
            "host.setOnTouchListener(listener)",
            "internal fun Modifier.p5aDismissOnInwardEdgeSwipe",
            "detectHorizontalDragGestures(",
            "false",
        )) assertTrue("missing shared dialog dismissal token $token", dialogDismissSource.contains(token))

        val uiSources = File("src/main/java/com/nanzhufeng/ai/ui").walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name != "P5ADialogDismissBehavior.kt" }
            .toList()
        assertTrue(uiSources.none { it.readText().contains("import androidx.compose.material3.AlertDialog") })
        assertTrue(uiSources.none { it.readText().contains("import androidx.compose.ui.window.Dialog\n") })
        assertTrue(uiSources.none { it.readText().contains("onDismissRequest = { if") })
        val composerOverlay = source.substring(source.indexOf("private fun ComposerMenuOverlay"), source.indexOf("private fun ComposerModelPickerHeader"))
        assertTrue(composerOverlay.contains(".p5aDismissOnInwardEdgeSwipe(onOverlayBack)"))
        assertTrue(composerOverlay.contains("BackHandler(onBack = onOverlayBack)"))
    }

    @Test
    fun `FB-P6-079 keeps Android app popup surfaces on the live foreground token`() {
        val selectionDialog = source.substring(source.indexOf("private fun WideTextSelectionDialog"), source.indexOf("private fun MessageContextAction"))
        for (token in listOf("DialogProperties(usePlatformDefaultWidth = false", "padding(horizontal = 16.dp)", "fillMaxWidth()", "heightIn(max = 580.dp)", "verticalScroll(rememberScrollState())", "color = ForegroundSurface", "shape = RoundedCornerShape(24.dp)")) assertTrue("missing selected-text foreground surface token $token", selectionDialog.contains(token))
        for (token in listOf("internal var ForegroundSurface by mutableStateOf(Color.White)", "surfaceTint = Color.Transparent", "surfaceVariant = ForegroundSurface", "surfaceContainerLow = ForegroundSurface", "surfaceContainer = ForegroundSurface", "surfaceContainerHigh = ForegroundSurface", "surfaceContainerHighest = ForegroundSurface")) assertTrue("missing live popup surface token $token", appSource.contains(token))
    }

    @Test
    fun `FB-P6-061 compact conversation row routes long press and its bounds to the root action owner`() {
        assertTrue(source.contains("onLongClick = if (batchEditing) null else"))
        assertTrue(source.contains("{ rowBounds?.let { onRequestConversationActions(conversation, it) } }"))
        assertTrue(source.contains("var conversationActionTarget by remember"))
        assertTrue(source.contains("ConversationActionMenuTarget(conversation.id.value, anchor)"))
        assertTrue(source.contains(".onGloballyPositioned { rowBounds = it.boundsInRoot() }"))
        assertTrue(source.contains("Text(conversationListLocalDate(conversation.updatedAt)"))
        assertTrue(source.contains("softWrap = false"))
        assertTrue(source.contains("Modifier.wrapContentWidth(Alignment.End)"))
        assertTrue(source.contains("TextAlign.End"))
        assertTrue(source.contains("DateTimeFormatter.ofPattern(\"yyyy/MM/dd\")"))
        assertFalse(source.contains("date == today.minusDays(1)"))
        assertFalse(source.contains("longPressMenuVisible"))
        assertFalse(source.contains("TextButton(onClick = { actionsVisible = !actionsVisible }"))
        assertFalse(source.contains("if (actionsVisible) Row"))
    }

    @Test
    fun `FB-P6-104 drawer conversation rows expose a right-swipe shortcut to the existing management owners`() {
        val row = source.substring(source.indexOf("private fun ConversationNavigationRow"), source.indexOf("private data class ConversationActionMenuTarget"))
        val drawer = source.substring(source.indexOf("private fun ConversationNavigationDrawer"), source.indexOf("private fun WorkProjectNavigationDrawer"))
        for (token in listOf(
            "Orientation.Horizontal",
            "Modifier.draggable(",
            "startDragImmediately = revealed",
            "dragOffsetPx >= revealWidthPx * 0.42f",
            "ConversationRowSwipeActions(",
            "ConversationRowSwipeAction.TOGGLE_PIN",
            "ConversationRowSwipeAction.TOGGLE_FAVORITE",
            "ConversationRowSwipeAction.RENAME",
            "ConversationRowSwipeAction.DELETE",
            "contentDescription = label",
            "color = Color(0xFFF2F4F1)",
            "swipeAction != null && translatedPx > 0f",
            "val rowHeight = if (batchEditing) 44.dp else 36.dp",
            "rowHeight = rowHeight",
            "modifier = modifier.width(176.dp).height(rowHeight)",
            "modifier = Modifier.fillMaxSize()",
            "shape = RectangleShape",
            "RoundedCornerShape(topStart = 0.dp, topEnd = 14.dp, bottomEnd = 14.dp, bottomStart = 0.dp)",
            "contentColor = Color(0xFF397A6B)",
            "contentColor = Color(0xFF9B751D)",
            "contentColor = Color(0xFF8B684C)",
            "contentColor = Color(0xFF935651)",
        )) assertTrue("missing right-swipe shortcut token $token", row.contains(token))
        assertTrue(drawer.contains("var revealedConversationId by remember"))
        assertTrue(drawer.contains("fun dismissRevealedConversation(): Boolean"))
        assertTrue(drawer.contains("enabled = revealedConversationId != null"))
        assertTrue(drawer.contains("if (!dismissRevealedConversation()) onSelect(id)"))
        assertTrue(drawer.contains("padding(start = 5.dp, end = 5.dp, top = 16.dp"))
        assertTrue(drawer.contains("verticalArrangement = Arrangement.spacedBy(8.dp)"))
        assertTrue(row.contains("else if (revealed) onRevealChanged(false)"))
        assertTrue(drawer.contains("ConversationManagementAction.PIN else ConversationManagementAction.UNPIN"))
        assertTrue(drawer.contains("ConversationManagementAction.FAVORITE else ConversationManagementAction.UNFAVORITE"))
        assertTrue(drawer.contains("ConversationRowSwipeAction.RENAME -> onRequestRename(conversation)"))
        assertTrue(drawer.contains("ConversationRowSwipeAction.DELETE -> onRequestDelete(conversation)"))
        assertFalse(row.contains("ConversationManagementAction.SOFT_DELETE"))
        assertTrue("the normal row surface must hide the action strip until it is swiped", row.contains("color = if (selected) AccentOrangeSoft else ConversationDrawerRowSurface"))
        assertFalse("a transparent normal row leaks the underlying swipe actions", row.contains("color = if (selected) AccentOrangeSoft else Color.Transparent"))
        assertTrue("the opaque conversation surface must occupy the complete row height", row.contains("modifier = Modifier.fillMaxSize()"))
        val actions = row.substring(row.indexOf("private fun ConversationRowSwipeActions"))
        assertFalse("the compact swipe ribbon must not reserve text labels", actions.contains("Text(label"))
    }

    @Test
    fun `FB-P6-106 drawer and rendered transcript text use the same reduced reading scale`() {
        val drawerHostStart = source.indexOf("ModalNavigationDrawer(")
        val drawerHost = source.substring(
            drawerHostStart,
            source.indexOf("if (state.temporaryRecovery != null)", drawerHostStart),
        )
        val messageBubble = source.substring(source.indexOf("private fun MessageBubble"), source.indexOf("private fun MessageContextAction"))
        val row = source.substring(source.indexOf("private fun ConversationNavigationRow"), source.indexOf("private data class ConversationActionMenuTarget"))
        val presentation = source.substring(source.indexOf("private fun PresentationBlockView"), source.indexOf("private fun inlineText"))

        assertTrue(source.contains("private const val ConversationTextScaleFactor = 0.99f"))
        assertTrue(source.contains("private fun Typography.scaledForConversationText()"))
        assertTrue(drawerHost.contains("ConversationTextScale {"))
        assertTrue(messageBubble.contains("val textContent: @Composable () -> Unit = {\n        ConversationTextScale {"))
        assertTrue(row.contains("fontSize = scaledConversationTextUnit(14.sp)"))
        assertTrue(row.contains("Icon(icon, contentDescription = label, modifier = Modifier.size(17.dp))"))
        for (token in listOf("scaledConversationTextUnit(typography.bodyLineHeight)", "scaledConversationTextUnit(typography.noteLineHeight)", "scaledConversationTextUnit(typography.listLineHeight)")) {
            assertTrue("missing typography-owned scaled message line-height $token", presentation.contains(token))
        }
        assertTrue(source.contains("body = 16.sp, bodyLineHeight = 25.sp"))
        assertTrue(source.contains("list = 15.sp, listLineHeight = 24.sp"))
    }

    @Test
    fun `FB-P6-105 recent header batch editor selects conversations and routes confirmed deletion to the soft-delete owner`() {
        val drawer = source.substring(source.indexOf("private fun ConversationNavigationDrawer"), source.indexOf("private fun ConversationBatchEditControls"))
        val controls = source.substring(source.indexOf("private fun ConversationBatchEditControls"), source.indexOf("/** Work is organized"))
        val row = source.substring(source.indexOf("private fun ConversationNavigationRow"), source.indexOf("private data class ConversationActionMenuTarget"))
        val viewModel = File("src/main/java/com/nanzhufeng/ai/ui/ConversationFoundationViewModel.kt").readText()
        for (token in listOf(
            "contentDescription = if (batchEditing) \"退出批量编辑\" else \"批量编辑对话\"",
            "var selectedBatchConversationIds by remember",
            "var pendingBatchDelete by remember",
            "ConversationBatchEditControls(",
            "label = \"删除 ${'$'}selectedCount\"",
            "label = \"完成\"",
            "Text(\"移入回收站？\")",
            "onBatchSoftDelete(pendingBatchDelete)",
            "不会物理删除消息、附件或调用关联",
        )) assertTrue("missing batch editor token $token", drawer.contains(token) || controls.contains(token))
        for (token in listOf(
            "tonalElevation = 0.dp",
            "shadowElevation = 0.dp",
            "height(50.dp)",
            "iconSize = 16.dp",
            "iconSize: androidx.compose.ui.unit.Dp = 18.dp",
        )) assertTrue("missing compact batch-toolbar token $token", controls.contains(token))
        for (token in listOf("batchEditing: Boolean = false", "CompactConversationSelectionCheckbox(", "onBatchSelectionChanged(!batchSelected)", "selectionDescription = if (batchSelected) \"已选择${'$'}{conversation.title}\" else \"选择${'$'}{conversation.title}\"")) {
            assertTrue("missing selectable row token $token", row.contains(token))
        }
        val compactCheckbox = source.substring(source.indexOf("private fun CompactConversationSelectionCheckbox"), source.indexOf("private data class ConversationActionMenuTarget"))
        for (token in listOf("size(32.dp)", "size(16.dp)", "RoundedCornerShape(4.dp)", "width = 1.5.dp", "Modifier.size(12.dp)", "onCheckedChange(!checked)")) {
            assertTrue("missing compact checkbox token $token", compactCheckbox.contains(token))
        }
        val batchOwner = viewModel.substring(viewModel.indexOf("fun softDeleteConversations"), viewModel.indexOf("fun assignProject"))
        for (token in listOf("distinctBy { it.id.value }", "ConversationManagementAction.SOFT_DELETE", "ConversationManagementIntentId.new()", "withContext(Dispatchers.IO)", "已将 ${'$'}completed 个会话移入回收站", "个未完成")) {
            assertTrue("missing batch soft-delete owner token $token", batchOwner.contains(token))
        }
        assertFalse("drawer must not bypass the view-model soft-delete owner", drawer.contains("ConversationManagementAction.SOFT_DELETE"))
    }

    @Test
    fun `compact conversation menu keeps icon actions and isolates delete without headers`() {
        for (token in listOf("ConversationMenuAction(Icons.Rounded.PushPin", "Icons.Rounded.Archive", "Icons.Rounded.Unarchive", "ConversationMenuAction(Icons.Rounded.DeleteOutline", "Icons.Rounded.ChevronRight", "Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))", "onRequestDelete(conversation)")) {
            assertTrue("missing $token", source.contains(token))
        }
        assertFalse(source.contains("if (workMode) ConversationMenuAction"))
        for (forbidden in listOf("Text(\"会话操作\"", "Text(\"危险操作\"", "Text(conversation.title, style = MaterialTheme.typography.titleMedium")) assertFalse("unexpected $forbidden", source.contains(forbidden))
    }

    @Test
    fun `FB-P6-064 conversation menu stays compact with a soft low-elevation surface`() {
        val actionSheet = source.substring(source.indexOf("private fun ConversationActionSheet"), source.indexOf("private fun ConversationMenuAction"))
        val actionRow = source.substring(source.indexOf("private fun ConversationMenuAction"), source.indexOf("private fun ConversationManagementBar"))
        for (token in listOf("val menuWidth = 224.dp", "(rowCount * 48).dp + 60.dp", "RoundedCornerShape(20.dp)", "shadowElevation = 6.dp", "Column(Modifier.padding(vertical = 6.dp))", "conversation.title")) {
            assertTrue("missing compact menu token $token", actionSheet.contains(token))
        }
        for (token in listOf("height(48.dp)", "Modifier.size(22.dp)", "Spacer(Modifier.width(16.dp))", "fontSize = scaledTransientMenuTextUnit(15.sp)", "Modifier.size(18.dp)")) {
            assertTrue("missing compact row token $token", actionRow.contains(token))
        }
        assertFalse(actionSheet.contains("shadowElevation = 12.dp"))
    }

    @Test
    fun `FB-P6-061 conversation sheet is outside the drawer modal layer`() {
        val drawer = source.substring(source.indexOf("private fun ConversationNavigationDrawer"), source.indexOf("internal fun ConversationManagementSettingsCard"))
        val actionSheet = source.substring(source.indexOf("private fun ConversationActionSheet"), source.indexOf("private fun ConversationMenuAction"))
        assertFalse(drawer.contains("ModalBottomSheet"))
        assertTrue(actionSheet.contains("Popup("))
        assertTrue(actionSheet.contains("PopupProperties(focusable = true, dismissOnBackPress = true, dismissOnClickOutside = true)"))
        assertTrue(actionSheet.contains("offset = IntOffset(x, y)"))
        assertTrue(actionSheet.contains("ConversationMenuAction(Icons.Rounded.DeleteOutline"))
    }

    @Test
    fun `FB-P6-067 rename dialog keeps only its field and actions`() {
        val renameInvocation = source.substring(source.indexOf("if (renameVisible) CompactConversationRenameDialog"), source.indexOf("if (confirmingDelete) AlertDialog"))
        val renameDialog = source.substring(source.indexOf("private fun CompactConversationRenameDialog"), source.indexOf("private fun TranscriptDateDivider"))
        assertTrue(renameInvocation.contains("onDismiss = { renameVisible = false }"))
        assertTrue(renameInvocation.contains("ConversationManagementAction.RENAME"))
        assertTrue(renameDialog.contains("CompactConversationRenameField("))
        assertTrue(renameDialog.contains("focusRequester = focusRequester"))
        assertFalse(renameInvocation.contains("Text(\"重命名会话\")"))
        assertFalse(renameInvocation.contains("1–120 个 Unicode 字符；空白不会保存。"))
    }

    @Test
    fun `FB-P6-069 rename dialog fills the available sidebar width while compressing vertical space`() {
        val renameDialog = source.substring(source.indexOf("private fun CompactConversationRenameDialog"), source.indexOf("private fun TranscriptDateDivider"))
        for (token in listOf("DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true, dismissOnClickOutside = true)", "BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart)", "DismissibleDialogBackdrop(onDismiss)", "val drawerWidth = if (maxWidth >= 600.dp) maxWidth * (2f / 3f) else maxWidth.coerceAtMost(320.dp)", ".width(drawerWidth)", "RoundedCornerShape(20.dp)", "padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 10.dp)", "CompactConversationRenameField(", "focusRequester = focusRequester", "height(34.dp)", "RoundedCornerShape(17.dp)", "height(32.dp)", "RoundedCornerShape(16.dp)", "Arrangement.spacedBy(8.dp, Alignment.End)")) {
            assertTrue("missing compact rename token $token", renameDialog.contains(token))
        }
        val field = source.substring(source.indexOf("private fun CompactConversationRenameField"), source.indexOf("private fun TranscriptDateDivider"))
        for (token in listOf("height(48.dp)", ".border(1.dp, Color(0xFF79747E), shape)", "contentAlignment = Alignment.CenterStart", "offset(x = 12.dp, y = (-8).dp)")) assertTrue("missing full-height field token $token", field.contains(token))
        assertFalse(renameDialog.contains("AlertDialog("))
    }

    @Test
    fun `FB-P6-111 transcript date divider uses the shared neutral divider token on both sides`() {
        val dateDivider = source.substring(source.indexOf("private fun TranscriptDateDivider"), source.indexOf("private data class TranscriptScrollMetrics"))
        assertTrue(Regex("HorizontalDivider\\(modifier = Modifier\\.weight\\(1f\\), color = SubtleDivider\\)").findAll(dateDivider).count() == 2)
        val theme = File("src/main/java/com/nanzhufeng/ai/ui/NanfengAiApp.kt").readText()
        assertTrue(theme.contains("internal var SubtleDivider by mutableStateOf(NeutralBorder)"))
        assertTrue(theme.contains("internal var NeutralBorder by mutableStateOf(Color(0xFFD8DEDA))"))
        assertTrue(theme.contains("SubtleDivider = NeutralBorder"))
    }

    @Test
    fun `FB-P6-068 submitted drafts always return the normal and temporary transcript to latest`() {
        val normalTranscriptStart = source.indexOf("val listState = chatTranscriptListState")
        val normalTranscript = source.substring(normalTranscriptStart, source.indexOf("if (showJumpToLatest", normalTranscriptStart))
        assertTrue(source.contains("var chatSentFromMessageCount by remember(state.selectedConversationId)"))
        assertTrue(normalTranscript.contains("sentCount != null && state.messages.size > sentCount"))
        assertTrue(normalTranscript.contains("listState.scrollToTrueBottom()"))
        assertTrue(source.contains("chatSentFromMessageCount = state.messages.size"))
        assertTrue(source.contains("workSentFromMessageCount = state.messages.size"))

        val temporaryTranscript = source.substring(source.indexOf("private fun TemporaryConversationPane"))
        assertTrue(temporaryTranscript.contains("val listState = rememberLazyListState()"))
        assertTrue(temporaryTranscript.contains("sentCount != null && recovery.messages.size > sentCount"))
        assertTrue(temporaryTranscript.contains("LazyColumn(state = listState"))
        assertTrue(temporaryTranscript.contains("sentFromMessageCount = recovery.messages.size\n                    onSubmit()"))
    }

    @Test
    fun `conversation menu is compact while workspace retains its complete local action set`() {
        val actionSheet = source.substring(source.indexOf("private fun ConversationActionSheet"), source.indexOf("private fun ConversationMenuAction"))
        for (token in listOf(
            "workMode -> 10 + if (includeRename) 1 else 0",
            "else -> 7 + if (includeRename) 1 else 0",
            "if (workMode) {",
            "ConversationMenuAction(Icons.Rounded.PushPin",
            "ConversationMenuAction(Icons.AutoMirrored.Outlined.DriveFileMove",
            "ConversationMenuAction(Icons.Rounded.AttachFile, \"已上传文件\"",
            "ConversationMenuAction(Icons.Rounded.Search, \"在聊天中查找\"",
            "ConversationMenuAction(Icons.Rounded.Home, \"添加到主屏幕\"",
            "ConversationManagementAction.ARCHIVE",
            "ConversationMenuAction(Icons.Rounded.DeleteOutline",
        )) assertTrue("missing action owner $token", actionSheet.contains(token))
        val compact = actionSheet.substringAfter("} else {\n                        ConversationMenuAction(Icons.Rounded.PushPin").substringBefore("\n                    }\n                }")
        for (removedFromChat in listOf("DriveFileMove", "已上传文件", "添加到主屏幕")) assertFalse("chat menu retained $removedFromChat", compact.contains(removedFromChat))
        assertTrue(compact.indexOf("\"重命名\"") < compact.indexOf("\"分享\""))
        for (alwaysAvailable in listOf("ConversationManagementAction.RENAME", "ConversationManagementAction.SOFT_DELETE")) {
            assertTrue("missing $alwaysAvailable", source.contains(alwaysAvailable))
        }
    }

    @Test
    fun `FB-P6-107 content headers replace the empty switch with new and complete overflow actions`() {
        val shell = source.substring(source.indexOf("private fun ConversationShellHeader"), source.indexOf("private fun ConversationHeaderFloatingIconButton"))
        val actionCapsule = source.substring(source.indexOf("private fun ConversationHeaderContentActions"), source.indexOf("private fun ConversationHeaderFloatingIconButton"))
        val activity = File("src/main/java/com/nanzhufeng/ai/NanfengAiActivity.kt").readText()
        val owner = File("src/main/java/com/nanzhufeng/ai/ui/ConversationFoundationViewModel.kt").readText()
        for (token in listOf(
            "val hasConversationContent = state.messages.isNotEmpty()",
            "showContentActions = hasConversationContent",
            "onCreateConversation = onCreate",
            "ConversationActionMenuTarget(conversation.id.value, anchor, includeRename = false)",
            "ConversationHeaderContentActions(",
            "if (!showContentActions) ConversationModeSwitch(",
        )) assertTrue("missing content-header token $token", source.contains(token))
        for (token in listOf("RoundedCornerShape(50)", "painterResource(R.drawable.ic_lucide_file_pen)", "contentDescription = \"新对话\"", "modifier = Modifier.size(22.dp)", "Icon(Icons.Rounded.MoreVert, contentDescription = \"对话更多操作\", tint = ConversationControlGlyph)", "onGloballyPositioned { moreBounds = it.boundsInRoot() }")) {
            assertTrue("missing header capsule token $token", actionCapsule.contains(token))
        }
        assertTrue(shell.contains("ConversationHeaderFloatingIconButton(onClick = onTemporaryAction)"))
        for (token in listOf("ConversationUploadedFilesDialog(", "ConversationFindInChatDialog(", "requestPinConversationShortcut(context, target)", "ShortcutInfo.Builder", "CONVERSATION_SHORTCUT_ID_EXTRA")) {
            assertTrue("missing local content action owner $token", source.contains(token))
        }
        assertTrue(activity.contains("consumeConversationShortcutIntent(intent)"))
        assertTrue(activity.contains("selectP5ARoute(P5ARoute.CONVERSATION)"))
        assertTrue(owner.contains("fun openConversationShortcut"))
        assertTrue(owner.contains("conversation.deletedAt != null"))
    }

    @Test
    fun `pin and archive semantics never fall back to text symbols`() {
        for (forbidden in listOf("\\\"⌁\\\"", "\\\"▤\\\"", "\\\"⚡\\\"")) assertFalse("unexpected $forbidden", source.contains(forbidden))
    }

    @Test
    fun `FB-P6-078 uses a compact black message popup anchored to the long pressed message`() {
        val messagePopup = source.substring(source.indexOf("private fun MessageActionPopup"), source.indexOf("private fun MessageContextAction"))
        val messageAction = source.substring(source.indexOf("private fun MessageContextAction"), source.indexOf("private val transcriptTimeFormatter"))
        for (token in listOf("messageActionTarget", "MessageActionMenuTarget", "MessageActionPopup(", "Icons.Rounded.ContentCopy", "Icons.Rounded.SelectAll", "Icons.Rounded.Share", "\"编辑消息\"", "Intent.ACTION_SEND", "formatTranscriptTimeOrNull")) assertTrue("missing $token", source.contains(token))
        for (token in listOf("val menuWidth = 224.dp", "(rowCount * 46).dp", "RoundedCornerShape(20.dp)", "shadowElevation = 6.dp", "PopupProperties(focusable = true, dismissOnBackPress = true, dismissOnClickOutside = true)", "offset = IntOffset(x, y)")) assertTrue("missing compact popup token $token", messagePopup.contains(token))
        for (token in listOf("height(46.dp)", "Modifier.size(22.dp)", "fontSize = 15.sp", "contentColor = BodyText")) assertTrue("missing black action token $token", messageAction.contains(token))
        assertFalse(messagePopup.contains("ModalBottomSheet"))
        assertFalse(messageAction.contains("AccentOrange"))
        for (forbidden in listOf("\"模型未知\"", "durationLabel", "origin.label", "Text(\"你\"")) assertFalse("unexpected $forbidden", source.contains(forbidden))
    }

    @Test
    fun `FB-P6-034 keeps USER actions long press only while assistant has a visible external action row`() {
        val messageBubble = source.substring(source.indexOf("private fun MessageBubble"), source.indexOf("private fun MessageContextAction"))
        for (token in listOf("AssistantMessageActionRow", "onCopyAssistant", "onShareAssistant", "onBranchAssistant", "Icons.Rounded.ContentCopy", "Icons.Rounded.Share", "Icons.Rounded.MoreVert", "更多操作", "formatTranscriptTimeOrNull")) assertTrue("missing $token", messageBubble.contains(token))
        val assistantBranch = messageBubble.substring(messageBubble.indexOf("MessageRole.ASSISTANT"), messageBubble.indexOf("else -> Surface"))
        assertFalse(assistantBranch.contains("combinedClickable"))
        assertTrue(messageBubble.contains("MessageRole.USER -> Column"))
        assertTrue(messageBubble.contains("onAnchorChanged = { messageBounds = it }"))
        assertTrue(messageBubble.contains("SelectionContainer {"))
        val userBranch = messageBubble.substring(messageBubble.indexOf("MessageRole.USER"), messageBubble.indexOf("MessageRole.ASSISTANT"))
        assertTrue(userBranch.contains("onLongPress = { pressPosition -> messageBounds?.let"))
    }

    @Test
    fun `assistant footer keeps export and branch together in more menu with export first`() {
        val actionRow = source.substring(source.indexOf("private fun AssistantMessageActionRow"), source.indexOf("private fun MessageActionPopup"))
        for (token in listOf("var moreActionsExpanded", "Icons.Rounded.MoreVert", "contentDescription = \"更多操作\"", "DropdownMenu(", "Text(\"导出 Markdown\")", "Text(\"创建分支\")", "onExportMarkdown(transcript)", "onBranch(transcript.message.messageId)")) {
            assertTrue("missing Markdown export action $token", actionRow.contains(token))
        }
        assertTrue(actionRow.indexOf("Text(\"导出 Markdown\")") < actionRow.indexOf("Text(\"创建分支\")"))
        assertFalse(actionRow.contains("contentDescription = \"导出 Markdown\""))
        assertFalse(actionRow.contains("contentDescription = \"从此处创建分支\""))
        for (token in listOf("private suspend fun shareAssistantMarkdown", "presentedMessageMarkdown", "type = \"text/markdown\"", "FileProvider.getUriForFile", "shared_attachments")) {
            assertTrue("missing safe Markdown export owner $token", source.contains(token))
        }
        for (token in listOf(
            "private fun conversationMarkdownExportFileName",
            "conversationMarkdownExportFileName(conversationTitle, messageSequence)",
            "sequence.coerceIn(1, 99).toString().padStart(2, '0')",
        )) assertTrue("missing title-first Markdown filename token $token", source.contains(token))
    }

    @Test
    fun `conversation long press menu exports its current path as a Markdown file`() {
        val conversationMenu = source.substring(source.indexOf("private fun ConversationActionSheet"), source.indexOf("private fun ConversationMenuAction"))
        for (token in listOf("onExportConversationMarkdown", "Icons.Rounded.FileDownload", "\"导出 Markdown\"", "else -> 7 + if (includeRename) 1 else 0")) {
            assertTrue("missing conversation Markdown action $token", conversationMenu.contains(token))
        }
        for (token in listOf("private suspend fun shareConversationMarkdown", "# ", "presentedMessageMarkdown", "type = \"text/markdown\"", "南枫 AI 对话 Markdown")) {
            assertTrue("missing conversation Markdown export owner $token", source.contains(token))
        }
        assertTrue(source.contains("conversationMarkdownExportFileName(conversation.title, sequence = 1)"))
    }

    @Test
    fun `FB-P6-044 keeps an existing assistant run duration above open body and outside action metadata`() {
        val messageBubble = source.substring(source.indexOf("private fun MessageBubble"), source.indexOf("private fun MessageContextAction"))
        val assistantBranch = messageBubble.substring(messageBubble.indexOf("MessageRole.ASSISTANT"), messageBubble.indexOf("else -> Surface"))
        assertTrue(assistantBranch.contains("transcript.metadata.workDurationLabel?.let"))
        assertTrue(assistantBranch.indexOf("workDurationLabel") < assistantBranch.indexOf("textBlocks"))
        assertFalse(source.substring(source.indexOf("private fun AssistantMessageActionRow"), source.indexOf("private fun MessageContextAction")).contains("workDurationLabel"))
    }

    @Test
    fun `assistant generation state is visible while its durable message is partial and footer stays on the assistant side`() {
        val messageBubble = source.substring(source.indexOf("private fun MessageBubble"), source.indexOf("private fun RightAlignedUserBubble"))
        val assistantBranch = messageBubble.substring(messageBubble.indexOf("MessageRole.ASSISTANT"), messageBubble.indexOf("else -> Surface"))
        val actionRow = source.substring(source.indexOf("private fun AssistantMessageActionRow"), source.indexOf("private fun MessageActionPopup"))
        val generation = source.substring(source.indexOf("private fun AssistantGenerationStatus"), source.indexOf("private fun AssistantMessageActionRow"))

        assertTrue(assistantBranch.contains("message.deliveryState == com.nanzhufeng.ai.domain.MessageDeliveryState.PARTIAL"))
        assertTrue(assistantBranch.contains("AssistantGenerationStatus("))
        assertTrue(assistantBranch.contains("hasPartialText = textBlocks.isNotEmpty()"))
        for (token in listOf("CircularProgressIndicator", "南枫 AI 正在继续生成…", "waitingPreview", "rememberInfiniteTransition", "RepeatMode.Reverse")) {
            assertTrue("missing $token", generation.contains(token))
        }
        assertFalse(generation.contains("正式回答生成后将自动替换此提示"))
        for (token in listOf("horizontalAlignment = Alignment.Start", "Arrangement.spacedBy(AssistantFooterActionSpacing)", "assistantFooterModelName", "costLabel", "BoxWithConstraints(Modifier.weight(1f))", "rememberTextMeasurer().measure", "costOnOwnLine", "textAlign = TextAlign.End", "composerModelShortNameForUser(it)")) {
            assertTrue("missing concise left-aligned assistant footer token $token", actionRow.contains(token) || source.contains(token))
        }
        for (token in listOf("AssistantFooterActionSpacing = 4.dp", "AssistantMessageAction(", "modifier = modifier.size(36.dp)", "iconSize = 16.dp", "iconSize: androidx.compose.ui.unit.Dp = 20.dp", "modifier = Modifier.size(iconSize)", "tint = SecondaryText.copy(alpha = 0.72f)")) {
            assertTrue("missing compact unified assistant action token $token", actionRow.contains(token) || source.contains(token))
        }
        assertFalse(actionRow.contains("AssistantFooterLeadingActionVisualOffset"))
        assertFalse(actionRow.contains("tint = BrandGreen"))
    }

    @Test
    fun `FB-P6-045 user text bubble is content driven with only a responsive maximum`() {
        val messageBubble = source.substring(source.indexOf("private fun MessageBubble"), source.indexOf("private fun MessageContextAction"))
        val userBranch = messageBubble.substring(messageBubble.indexOf("MessageRole.USER"), messageBubble.indexOf("MessageRole.ASSISTANT"))
        val sharedBubble = source.substring(source.indexOf("private fun RightAlignedUserBubble"), source.indexOf("private fun AssistantMessageActionRow"))
        assertTrue(userBranch.contains("RightAlignedUserBubble(surfaceColor = roleVisual.surface"))
        assertTrue(sharedBubble.contains("BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopEnd)"))
        assertTrue(sharedBubble.contains("shape = RoundedCornerShape(24.dp)"))
        assertTrue(sharedBubble.contains(".widthIn(max = maxWidth * 0.82f)"))
        assertTrue(sharedBubble.contains(".wrapContentWidth(Alignment.End)"))
        assertFalse(sharedBubble.contains("fillMaxWidth(0.82f)"))
        assertFalse(sharedBubble.contains("minWidth"))
    }

    @Test
    fun `normal model settings do not surface real-service acceptance state`() {
        val modelSettings = File("src/main/java/com/nanzhufeng/ai/ui/ModelSettingsUi.kt").readText()
        for (token in listOf("realServiceAcceptance.label()", "P2MRealServiceStatusCard", "P2MRealServiceConfirmationDialog", "公开模型目录")) {
            assertFalse(token, modelSettings.contains(token))
        }
    }

    @Test
    fun `new normal conversation never injects an acceptance fixture into the user transcript`() {
        val viewModel = File("src/main/java/com/nanzhufeng/ai/ui/ConversationFoundationViewModel.kt").readText()
        val create = viewModel.substring(viewModel.indexOf("fun createDevelopmentConversation"), viewModel.indexOf("fun updateDraft"))
        assertTrue(create.contains("val surface = state.surface"))
        assertTrue(create.contains("createConversation.execute(surface = surface)"))
        assertFalse(create.contains("appendMessage.execute"))
        assertFalse(create.contains("确定性 fixture"))
    }

    @Test
    fun `FB-P6-091 Android drawer search opens a compact full screen search destination`() {
        val drawer = source.substring(source.indexOf("private fun ConversationNavigationDrawer"), source.indexOf("internal fun ConversationManagementSettingsCard"))
        val page = source.substring(source.indexOf("private fun ConversationSearchPage"), source.indexOf("private fun SearchAllOrTextResults"))
        assertTrue(drawer.contains(".height(42.dp)"))
        assertTrue(drawer.contains("onOpenSearchPage"))
        assertFalse(drawer.contains("最近搜索"))
        assertTrue(page.contains("DialogProperties(usePlatformDefaultWidth = false"))
        assertTrue(page.contains("statusBarsPadding()"))
        assertTrue(source.contains("attachmentSearchMonthGroups(hits)"))
        assertTrue(source.contains("SearchAttachmentMonthHeading(group.label, group.hits.size)"))
        assertTrue(page.contains("ConversationSearchCategory.entries"))
        assertTrue(page.contains("val categoryShape = RoundedCornerShape(16.dp)"))
        assertTrue(page.contains("onClick = { onSelectCategory(category) }"))
        assertTrue(page.contains("shape = categoryShape"))
        assertTrue(page.contains("shadowElevation = if (selected) 1.dp else 0.dp"))
        assertTrue(page.contains("maxWidth >= 600.dp") && page.contains("Alignment.Center"))
        assertTrue(page.contains("expandedSearchCategories") && page.contains("Modifier.weight(1f)"))
        assertFalse(page.contains("horizontalScroll(rememberScrollState())"))
        assertTrue(page.contains("Modifier.weight(1f).height(42.dp)"))
        assertTrue(page.contains("BasicTextField("))
        assertTrue(page.contains("Icons.Rounded.Search"))
        assertTrue(page.contains("Modifier.fillMaxSize().padding(start = 10.dp, end = 8.dp)"))
        assertTrue(page.contains("horizontalArrangement = Arrangement.spacedBy(7.dp)"))
        assertTrue(page.contains("Modifier.width(76.dp).height(42.dp).clip(RoundedCornerShape(21.dp)).combinedClickable(onClick = onOpenHistory)"))
        assertTrue(page.contains("BodyText.copy(alpha = 0.80f)"))
        assertTrue(page.contains("shape = RoundedCornerShape(21.dp)"))
        assertTrue(page.contains("ConversationSearchCategory.ALL -> \"搜索全部内容\""))
        assertTrue(page.contains("else -> \"搜索${'$'}{state.searchCategory.label}\""))
        assertTrue(page.contains("searchPlaceholder,"))
        assertFalse(page.contains("搜索本地内容"))
        assertTrue(page.contains("bottomContentPadding = 78.dp"))
        assertTrue(page.contains("detectTapGestures(onTap = { onCloseHistory() })"))
        assertTrue(page.contains("Box(Modifier.weight(1f).fillMaxWidth().imePadding())"))
        assertTrue(page.contains(".imePadding()"))
        assertTrue(page.contains("bottom = if (imeVisible) 8.dp else 14.dp"))
        assertTrue(page.contains("Text(\"历史\""))
        assertTrue(source.contains("onSearchChanged(\"\")\n                                onSearchRequested()"))
    }

    @Test
    fun `Android conversation drawer keeps a hard-edge-free base and gray edge fade`() {
        assertTrue(appSource.contains("PageBackground by mutableStateOf(Color(0xFFF7F7F7))"))
        assertTrue(appSource.contains("ConversationDrawerBaseSurface by mutableStateOf(Color.White)"))
        assertTrue(appSource.contains("ConversationDrawerCanvas by mutableStateOf(Color(0xFFF2F2F2))"))
        assertTrue(appSource.contains("ConversationDrawerRowSurface by mutableStateOf(ConversationDrawerBaseSurface)"))
        assertTrue(appSource.contains("ForegroundSurface by mutableStateOf(Color.White)"))
        assertTrue(source.contains("drawerContainerColor = ConversationDrawerBaseSurface"))
        assertTrue(source.contains(".requiredWidth(drawerWidth)"))
        assertTrue(source.contains(".then(closedDrawerSemanticsModifier)"))
        assertTrue(source.contains("ButtonDefaults.buttonColors(containerColor = AccentOrange, contentColor = Color.White)"))
        assertTrue(source.contains("LaunchedEffect(drawerOpen)"))
        assertTrue(source.contains("snapshotFlow { drawerState.currentValue }"))
        assertTrue(source.contains(".drop(1)"))
        assertTrue(source.contains("if (drawerState.isOpen) BackHandler { drawerScope.launch { drawerState.close() } }"))
    }

    @Test
    fun `FB-P6-051 keeps normal and project-work drawers on separate actionable navigation contracts`() {
        val drawer = source.substring(source.indexOf("private fun ConversationNavigationDrawer"), source.indexOf("private fun WorkProjectNavigationDrawer"))
        val projectDrawer = source.substring(source.indexOf("private fun WorkProjectNavigationDrawer"), source.indexOf("private fun TemporaryConversationNavigationDrawer"))
        assertTrue(drawer.contains("onClick = { if (!dismissRevealedConversation()) onCreate() }"))
        assertTrue(drawer.contains("IconButton(onClick = { if (!dismissRevealedConversation()) onOpenRoute(P5ARoute.SETTINGS) }, modifier = Modifier.size(48.dp))"))
        assertTrue(drawer.contains("modifier = Modifier.align(Alignment.BottomCenter)"))
        assertTrue(drawer.contains(".conversationEdgeGrayFade(edgeColor = ConversationDrawerBaseSurface)"))
        assertTrue(drawer.contains(".verticalScroll(rememberScrollState())\n                    .statusBarsPadding()\n                    .padding("))
        assertFalse(drawer.contains("bottom = if (batchEditing) 150.dp else 84.dp"))
        assertTrue(drawer.contains("horizontalArrangement = Arrangement.SpaceBetween"))
        assertTrue(drawer.contains("ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp)"))
        assertTrue(drawer.contains("shape = CircleShape"))
        assertTrue(drawer.contains("R.drawable.ic_lucide_file_pen"))
        assertTrue(drawer.contains("contentDescription = null, modifier = Modifier.size(18.dp)"))
        assertTrue(drawer.contains("Text(\"新对话\")"))
        assertFalse(drawer.contains("Text(\"新建本地对话\")"))
        assertTrue(projectDrawer.contains("Text(\"项目\","))
        assertTrue(projectDrawer.contains("contentDescription = \"项目列表菜单\""))
        assertTrue(projectDrawer.contains("contentDescription = \"创建项目\""))
        assertTrue(projectDrawer.contains("WorkProjectFolder("))
        assertTrue(projectDrawer.contains("onCreateWorkConversation"))
        assertTrue(projectDrawer.contains("未归入项目或项目已归档"))
        val glyph = File("src/main/res/drawable/ic_lucide_file_pen.xml").readText()
        assertTrue(glyph.contains("18.375,2.625"))
        assertTrue(glyph.contains("strokeLineJoin=\"round\""))
    }

    @Test
    fun `FB-P6-040 Android gives the concrete model label enough room immediately left of send`() {
        val composer = source.substring(source.indexOf("private fun DraftComposer"), source.indexOf("private fun ComposerMenuOverlay"))
        assertTrue(composer.contains("val modelLabel ="))
        assertTrue(composer.contains("val selectedPresets ="))
        assertTrue(composer.contains("val modelLabel = composerModelDisplayLabel(selectedPresets)"))
        assertFalse(composer.contains("Auto · 发送时按能力选择"))
        val modelEntry = source.substring(source.indexOf("private fun ComposerModelEntry"), source.indexOf("private fun ConversationComposerDock"))
        assertTrue(source.contains("private val ComposerModelDisplayWidth = 88.dp"))
        assertTrue(source.contains("private fun composerModelDisplayLabel(presets: List<ModelPresetId>)"))
        assertTrue(source.contains("presets.joinToString(\" / \")"))
        assertTrue(source.contains("composerModelShortNameForUser"))
        val modelService = File("src/main/java/com/nanzhufeng/ai/domain/ModelService.kt").readText()
        assertTrue(modelService.contains("The curated model catalog is the single user-facing naming source"))
        assertTrue(modelService.contains("raw.contains(preset.displayName, ignoreCase = true)"))
        assertFalse(modelService.contains(".removePrefix(\"GPT-\")"))
        assertTrue(modelEntry.contains("modifier = Modifier.width(ComposerModelDisplayWidth).height(48.dp)"))
        assertTrue(modelEntry.contains("modifier = Modifier.fillMaxWidth().height(36.dp)"))
        assertFalse(source.contains("if (presets.size > 1) \"对比\""))
        assertTrue(modelEntry.contains("interactionSource.collectIsPressedAsState()"))
        assertTrue(modelEntry.contains("Text(label, fontSize = 13.sp"))
        assertTrue(composer.contains("ConversationComposerDock("))
        assertTrue(composer.indexOf("ComposerModelEntry(") < composer.indexOf("ComposerSendButton"))
    }

    @Test
    fun `FB-P6-052 Android conversation and work use one selected segmented pill`() {
        assertTrue(source.contains("private val ConversationControlGlyph: Color get() = BodyText"))
        assertTrue(source.contains("ConversationModeSwitch("))
        assertTrue(source.contains("val workMode = state.surface == com.nanzhufeng.ai.domain.ConversationSurface.WORK"))
        assertTrue(source.contains("onModeChanged = { enabled -> onSurfaceChanged"))
        val modeSwitch = source.substring(source.indexOf("private fun ConversationModeSwitch"), source.indexOf("private fun ConversationModeSegment"))
        assertTrue(modeSwitch.contains("color = NeutralSystemSurface"))
        assertTrue(modeSwitch.contains("shape = RoundedCornerShape(50)"))
        assertFalse(modeSwitch.contains("shadowElevation"))
        assertTrue(modeSwitch.contains("Modifier.height(44.dp).padding(3.dp)"))
        assertTrue(source.contains("containerColor = if (selected) ForegroundSurface else Color.Transparent"))
        assertTrue(source.contains("if (ForegroundSurface.red < 0.5f) Color.White else Color(0xFF3F3F3F)"))
        assertTrue(source.contains("ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp)"))
    }

    @Test
    fun `FB-P6-099 keeps all three conversation header controls as independent floating surfaces`() {
        val shell = source.substring(source.indexOf("private fun ConversationShellHeader"), source.indexOf("private fun ConversationModeSwitch"))
        val floatingButton = source.substring(source.indexOf("private fun ConversationHeaderFloatingIconButton"), source.indexOf("private fun ConversationModeSwitch"))
        assertTrue(shell.contains("ConversationHeaderFloatingIconButton(onClick = onLeftAction)"))
        assertTrue(shell.contains("ConversationHeaderFloatingIconButton(onClick = onTemporaryAction)"))
        assertTrue(floatingButton.contains("color = ForegroundSurface"))
        assertTrue(floatingButton.contains("conversationForegroundShadow(CircleShape)"))
        assertTrue(floatingButton.contains("shadowElevation = 0.dp"))
        assertFalse(floatingButton.contains("border = BorderStroke"))
        assertTrue(source.contains("modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(horizontal = 18.dp, vertical = 18.dp)"))
        assertTrue(source.contains("The header remains an overlay, while the list owns a scrollable initial inset"))
        assertTrue(source.contains("private val ConversationTranscriptContentPadding = androidx.compose.foundation.layout.PaddingValues("))
        assertTrue(source.contains("top = 104.dp,"))
        for (token in listOf(
            "ConversationTopFrostHeight",
            "ConversationBottomFrostHeight",
            "ConversationEdgeFrostBlurRadius",
            "conversationEdgeFrostTransition",
            "BlurEffect(",
            "TileMode.Mirror",
        )) assertFalse("conversation transcript must not retain the removed frost effect: $token", source.contains(token))
        assertFalse(source.contains(".conversationEdgeFrost()"))
        assertTrue(source.contains(".conversationEdgeGrayFade()"))
        assertTrue(source.contains("private val ConversationWorkspaceCanvas: Color get() = PageBackground"))
        assertTrue(source.contains("background(ConversationWorkspaceCanvas)"))
    }

    @Test
    fun `conversation reading column keeps symmetric visual whitespace while its scrollbar stays beyond text`() {
        assertTrue(source.contains("private val ConversationTranscriptPageGutter = 4.dp"))
        assertTrue(source.contains("private val ConversationAssistantReadingStartInset = 24.dp"))
        assertTrue(source.contains("private val ConversationAssistantReadingEndInset = 18.dp"))
        assertTrue(source.contains("end = ConversationScrollbarContentEndInset"))
        assertTrue(source.contains("6dp passive-scrollbar lane"))
        assertTrue(source.contains("start = ConversationAssistantReadingStartInset"))
        assertTrue(source.contains("end = ConversationAssistantReadingEndInset"))
        assertTrue(source.contains(".padding(horizontal = ConversationTranscriptPageGutter)"))
        assertTrue(source.contains(".navigationBarsPadding()"))
        assertTrue(source.contains("modifier = Modifier.align(Alignment.CenterEnd).padding(top = 8.dp, bottom = 8.dp)"))
        val indicator = source.substring(source.indexOf("private fun TranscriptScrollIndicator"), source.indexOf("private fun presentedMessagePlainText"))
        assertTrue(indicator.contains("modifier.width(8.dp).fillMaxHeight()"))
    }

    @Test
    fun `floating composer rises above the keyboard while preserving navigation safety`() {
        assertTrue(
            source.contains(
                ".navigationBarsPadding()\n                            .imePadding()",
            ),
        )
        val temporary = source.substring(source.indexOf("private fun TemporaryConversationPane"))
        assertTrue(
            temporary.contains(
                ".navigationBarsPadding()\n            .imePadding()\n            .padding(start = 18.dp, end = 18.dp, bottom = 6.dp)",
            ),
        )
    }

    @Test
    fun `FB-P6-053 removes redundant conversation identity and local provider status from the chat canvas`() {
        val canvas = source.substring(source.indexOf("} else Box("), source.indexOf("if (choosingProject)"))
        assertTrue(canvas.contains("ConversationShellHeader("))
        assertFalse(canvas.contains("Text(\"本地对话\""))
        assertFalse(canvas.contains("Text(\"本机本地 · 不联网\""))
        assertFalse(canvas.contains("state.notice?.let"))
    }

    @Test
    fun `composer native text toolbar is transient across blank taps and app backgrounding`() {
        assertTrue(source.contains(".clearComposerFocusOnBlankTap(focusManager)"))
        assertTrue(source.contains("if (up != null && !up.isConsumed) focusManager.clearFocus(force = true)"))
        assertTrue(source.contains("Lifecycle.Event.ON_STOP"))
        assertTrue(source.contains("LocalLifecycleOwner.current"))
        val overlay = source.substring(source.indexOf("private fun ComposerMenuOverlay"), source.indexOf("private fun ComposerDraftTextField"))
        assertTrue(overlay.contains("color = ForegroundSurface"))
    }

    @Test
    fun `assistant branch creation gives a brief accessible success confirmation without a new permanent control`() {
        val feedback = source.substring(source.indexOf("private fun BranchCreationFeedback"), source.indexOf("private fun AssistantMessageActionRow"))
        for (token in listOf("已创建分支", "已打开新的本地对话", "Icons.Rounded.CheckCircle", "LiveRegionMode.Polite", "delay(2_800)", "onDismiss(branchCreation.branchConversationId)")) {
            assertTrue("missing branch feedback $token", feedback.contains(token))
        }
        assertFalse(feedback.contains("Button("))
    }

    @Test
    fun `normal transcript never exposes fixture or recovery engineering controls`() {
        val transcriptStart = source.indexOf("// The drawer owns navigation and management")
        val transcript = source.substring(transcriptStart, source.indexOf("DraftComposer(", transcriptStart))
        assertFalse(transcript.contains("ConversationAttemptHistory("))
        assertFalse(transcript.contains("ConversationActions("))
    }

    @Test
    fun `mobile composer keeps its model owner between the input and send controls`() {
        val composer = source.substring(source.indexOf("private fun DraftComposer"), source.indexOf("private fun ComposerSendButton"))
        assertTrue(composer.contains("onClick = onToggleModel"))
        assertTrue(composer.contains("ComposerModelEntry("))
    }

    @Test
    fun `transcript keeps only an unobtrusive right scrollbar without a left position rail`() {
        assertFalse(source.contains("showInnerTranscriptRail"))
        assertFalse(source.contains("TranscriptPositionRail"))
        assertFalse(source.contains("跳转到对话位置"))
        assertTrue(source.contains("private fun rememberTranscriptMeasuredItemHeights("))
        assertTrue(source.contains("mutableStateMapOf<Int, Int>()"))
        assertTrue(source.contains("visibleItemsInfo.map { item -> item.index to item.size.coerceAtLeast(1) }"))
        assertTrue(source.contains("private fun TranscriptScrollIndicator("))
        assertTrue(source.contains("measuredItemHeights: Map<Int, Int>"))
        assertTrue(source.contains("firstVisibleItemScrollOffset"))
        assertTrue(source.contains("val knownItemHeights = measuredItemHeights.toMutableMap()"))
        assertTrue(source.contains("Captures the final Compose-measured row heights, including the exact wrapped text line count"))
        assertTrue(source.contains("!listState.canScrollForward -> 1f"))
        assertFalse(source.contains("val nominalViewportItems = minOf(4, totalItems)"))
        assertTrue(source.contains("private const val TranscriptScrollbarIdleHideMillis = 3_000L"))
        assertTrue(source.contains("private fun rememberTranscriptScrollIndicatorVisible("))
        assertTrue(source.contains("listState.isScrollInProgress -> visible = true"))
        assertTrue(source.contains("delay(TranscriptScrollbarIdleHideMillis)"))
        assertTrue(source.contains("if (!metrics.canScroll) return"))
        assertTrue(source.contains("if (!rememberTranscriptScrollIndicatorVisible(listState, metrics.canScroll)) return"))
        assertTrue(source.contains("animateFloatAsState("))
        assertFalse(source.contains("offset(x = 4.dp)"))
        assertTrue(source.contains("end = ConversationScrollbarContentEndInset"))
    }

    @Test
    fun `drawer keeps Material standard right-swipe opening available from the chat canvas`() {
        assertTrue(source.contains("gesturesEnabled = true"))
        assertFalse(source.contains("openConversationDrawerOnStrictEdgeSwipe"))
    }


    @Test
    fun `expanded inner display gives the navigation drawer two thirds of available width`() {
        assertTrue(source.contains("val windowWidth = with(density) { LocalWindowInfo.current.containerSize.width.toDp() }"))
        assertTrue(source.contains("val drawerWidth = if (windowWidth >= 600.dp) windowWidth * (2f / 3f) else 320.dp"))
        assertTrue(source.contains(".requiredWidth(drawerWidth)"))
    }

    @Test
    fun `search history is never retained on the normal conversation canvas`() {
        assertTrue(source.contains("if (!searchPageVisible && state.searchHistoryOpen) onCloseSearchHistory()"))
        assertTrue(source.contains("if (searchPageVisible && state.searchHistoryOpen) BackHandler"))
        assertTrue(source.contains("onDismiss = {\n                searchPageVisible = false\n                returnToSearchAfterSearchOpen = false\n                onDrawerOpenChanged(true)\n                drawerScope.launch { drawerState.open() }"))
        assertFalse(source.contains("transcriptRailPreviewIndex"))
    }

    @Test
    fun `chat find normalizes readable wrapping instead of requiring an exact raw line`() {
        val find = source.substring(source.indexOf("private fun ConversationFindInChatDialog"), source.indexOf("private fun ConversationMenuAction"))
        assertTrue(find.contains("normalizeConversationSearchText(query)"))
        assertTrue(find.contains("conversationFindMessageMatches(messages, query)"))
        assertTrue(find.contains("KeyboardOptions(imeAction = ImeAction.Search)"))
        assertTrue(find.contains("Text(\"查找\")"))
        assertTrue(find.contains("Text(\"上一个\")"))
        assertTrue(find.contains("Text(\"下一个\")"))
        assertTrue(source.contains("activeFindQuery = query"))
        assertTrue(source.contains("activeTranscriptListState.animateScrollToItem(itemIndex)"))
        assertFalse(find.contains("Text(\"完成\")"))
        assertTrue(source.contains("normalizeConversationSearchText(presentedMessagePlainText(transcript.message)).contains(needle)"))
        assertTrue(find.contains("java.text.Normalizer.Form.NFKC"))
    }

    @Test
    fun `FB-P6-049 composer has the exact empty hint without changing its semantic label`() {
        val composer = source.substring(source.indexOf("private fun ComposerDraftTextField"), source.indexOf("private fun AttachmentPreviewChip"))
        assertTrue(composer.contains("inputDescription: String = \"会话草稿\""))
        assertTrue(composer.contains("semantics { contentDescription = inputDescription }"))
        assertTrue(composer.contains("hint = \"回复 南枫AI\""))
        assertFalse(composer.contains("输入内容"))
    }

    @Test
    fun `normal composer keeps safety detail in the attachment flow instead of permanent transcript chrome`() {
        val composer = source.substring(source.indexOf("private fun DraftComposer"), source.indexOf("private fun TemporaryConversationPane"))
        assertFalse(composer.contains("图片和文件仅保存为本地附件引用"))
        assertFalse(composer.contains("草稿会保存到当前会话；发送成功后才清理。"))
    }

    @Test
    fun `FB-P6-027 keeps normal message long press exclusive to the app action sheet`() {
        val messageBubble = source.substring(source.indexOf("private fun MessageBubble"), source.indexOf("private fun MessageContextAction"))
        val blockView = source.substring(source.indexOf("private fun PresentationBlockView"), source.indexOf("private fun inlineText"))
        assertTrue(messageBubble.contains("SelectionContainer {"))
        assertTrue(source.contains("WideTextSelectionDialog("))
        assertTrue(source.contains("SelectionContainer {"))
        assertFalse(blockView.contains("SelectionContainer"))
    }

    @Test
    fun `attachment facts are attachment scoped and appear in the shared anchored popup`() {
        for (token in listOf("var attachmentInfoVisible", "attachmentPressPosition", "detectTapGestures(", "onLongPress = { pressPosition", "AttachmentInfoPopup(", "anchorBounds", "pressPosition", "absoluteY = (pressInRoot.y.toInt() - popupHeightPx - verticalGap)", "PopupProperties(focusable = true, dismissOnBackPress = true, dismissOnClickOutside = true)", "发送于 \$it", "attachmentKindLabel(mimeType)")) assertTrue("missing $token", source.contains(token))
        assertTrue(source.contains("sentAt: java.time.Instant?"))
        assertFalse(source.contains("ModalBottomSheet"))
        assertFalse(source.contains("Text(\"仅本地引用，未外发\""))
        assertFalse(source.contains("Text(\"文本 · 点击安全预览\""))
    }

    @Test
    fun `FB-P6-039 keeps attachment previews outside message text surfaces and aligned by role`() {
        val messageBubble = source.substring(source.indexOf("private fun MessageBubble"), source.indexOf("private fun MessageContextAction"))
        for (token in listOf("val textBlocks = message.blocks.filterNot", "val attachmentBlocks = message.blocks.filterIsInstance", "val attachmentContent: @Composable () -> Unit", "if (textBlocks.isNotEmpty()) RightAlignedUserBubble", "if (attachmentBlocks.isNotEmpty()) Box", "contentAlignment = Alignment.CenterEnd", "contentAlignment = Alignment.CenterStart", "AssistantMessageActionRow")) {
            assertTrue("missing $token", messageBubble.contains(token))
        }
        val userBranch = messageBubble.substring(messageBubble.indexOf("MessageRole.USER"), messageBubble.indexOf("MessageRole.ASSISTANT"))
        assertFalse(userBranch.contains("AttachmentPreviewChip("))
        assertTrue(messageBubble.indexOf("AssistantMessageActionRow") > messageBubble.indexOf("attachmentContent"))
    }

    @Test
    fun `FB-P6-031 routes document image video and audio preview footprints by attachment type`() {
        val attachment = source.substring(source.indexOf("private fun AttachmentPreviewChip"), source.indexOf("private fun PdfPreviewDialog"))
        for (token in listOf("val isImage = mimeType.startsWith(\"image/\")", "val isPdf = mimeType == \"application/pdf\"", "val isVideo = mimeType == \"video/mp4\"", "val isAudio = mimeType in", "val isText = mimeType in", "isVideo -> Modifier.widthIn(min = 144.dp, max = 220.dp).height(128.dp)", "isImage -> originalAspectPreviewModifier(bitmap, maxEdge = 220.dp, fallbackSize = 92.dp)", "isAudio -> Modifier.width(176.dp).height(42.dp)", "isText -> Modifier.widthIn(min = 164.dp, max = 228.dp).heightIn(min = 96.dp, max = 128.dp)", "AttachmentTextSnippet", "AttachmentTextPreviewUnavailable", "else -> Modifier.size(92.dp)", "contentScale = if (isVideo) ContentScale.Crop else ContentScale.Fit")) assertTrue("missing $token", attachment.contains(token))
        assertFalse(attachment.contains("Surface(color = AccentOrangeSoft"))
        assertFalse(attachment.contains("Modifier.size(72.dp)"))
    }

    @Test
    fun `navigation titles use the shared reading scale while section labels remain visually secondary`() {
        for (token in listOf("fontSize = scaledConversationTextUnit(14.sp)", "lineHeight = scaledConversationTextUnit(18.sp)", "fontWeight = FontWeight.Normal", "overflow = TextOverflow.Ellipsis", "Text(\"已置顶\", style = MaterialTheme.typography.labelSmall", "Text(\"最近\", style = MaterialTheme.typography.labelSmall", "conversation.pinnedAt != null", "R.drawable.ic_nanfeng_conversation_bubble", "Modifier.size(18.dp)")) {
            assertTrue("missing $token", source.contains(token))
        }
        assertFalse(source.contains("conversation.title, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyLarge"))
    }

    @Test
    fun `drawer unread marker uses a local read watermark and clears on opening`() {
        val viewModel = File("src/main/java/com/nanzhufeng/ai/ui/ConversationFoundationViewModel.kt").readText()
        val row = source.substring(source.indexOf("private fun ConversationNavigationRow"), source.indexOf("private data class ConversationActionMenuTarget"))
        for (token in listOf(
            "val unreadConversationIds: Set<com.nanzhufeng.ai.domain.ConversationId>",
            "ConversationReadMarkerStore",
            "conversationReadMarkerStore.isInitialized()",
            "conversationReadMarkerStore.markRead(conversation.id, conversation.updatedAt.toEpochMilli())",
            "unreadConversationIds = state.unreadConversationIds - id",
        )) assertTrue("missing unread watermark anchor $token", viewModel.contains(token))
        assertTrue("drawer must consume its state unread projection", source.contains("unread = notificationReminderSettings.unreadConversationIndicatorsEnabled && conversation.id in state.unreadConversationIds"))
        for (token in listOf("if (unread)", "size(7.dp)", "background(AccentOrange)", "contentDescription = \"有未查看的新内容\"")) {
            assertTrue("missing unread marker rendering $token", row.contains(token))
        }
    }

    @Test
    fun `drawer heading uses the packaged app icon at compact title scale`() {
        val drawer = source.substring(source.indexOf("private fun ConversationNavigationDrawer"), source.indexOf("private fun ConversationNavigationRow"))
        assertTrue(drawer.contains("painterResource(R.drawable.nanfeng_ai_icon_foreground_image)"))
        assertTrue(drawer.contains("Modifier.size(drawerIdentityVisualSize).clip(RoundedCornerShape(8.dp))"))
        assertTrue(drawer.contains("\"南枫 AI\","))
    }

    @Test
    fun `composer uses typed local runtime stop only and keeps send icon accessible`() {
        for (token in listOf("val canStopRuntime = state.runtime?.isTerminal == false", "ComposerSendButton(onClick = if (canStopRuntime) onStop else onSubmit", "private val ComposerSendSurfaceSize = 36.dp", "private val ComposerSendGlyphSize = 24.dp", "private val ComposerStopGlyphSize = 22.5.dp", "colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, disabledContainerColor = Color.Transparent)", "Modifier.size(ComposerSendSurfaceSize)", "Icon(Icons.Rounded.Stop, contentDescription = \"停止生成\", tint = glyphColor, modifier = Modifier.size(ComposerStopGlyphSize))", "painterResource(R.drawable.ic_nanfeng_send_rounded)", "CircularProgressIndicator")) {
            assertTrue("missing $token", source.contains(token))
        }
        assertFalse(source.contains("Text(\"本地发送\")"))
    }

    @Test
    fun `FB-P6-055 mobile composer uses one floating pill rather than separate narrow screen controls`() {
        val composerSource = source.substring(source.indexOf("private fun ComposerDraftTextField"), source.indexOf("private fun AttachmentPreviewChip"))
        val composer = source.substring(source.indexOf("private fun DraftComposer"), source.indexOf("private fun ComposerMenuOverlay"))
        val composerDock = source.substring(source.indexOf("private fun ConversationComposerDock"), source.indexOf("private fun ComposerAttachmentPreview"))
        for (token in listOf("Surface(", "shape = RoundedCornerShape(28.dp)", "var inputFocused", "val inputExpanded = inputFocused && imeVisible", "if (inputExpanded)", "expanded = true", "expanded = false", "height(ComposerDockMinimumHeight)", "IconButton(", "ComposerDraftTextField", "ComposerSendButton")) {
            assertTrue("missing $token", composer.contains(token))
        }
        assertTrue(composerDock.contains("conversationForegroundShadow("))
        assertTrue(composerDock.contains("shape = RoundedCornerShape(28.dp)"))
        assertTrue(composerDock.contains("shadow = ConversationComposerForegroundShadow"))
        assertTrue(composerDock.contains("shadowElevation = 0.dp"))
        assertFalse(composerDock.contains("border = BorderStroke"))
        for (token in listOf("private fun ComposerDraftTextField", "AndroidView(", "android.widget.EditText", "setBackgroundColor(android.graphics.Color.TRANSPARENT)", "hint = \"回复 南枫AI\"", "includeFontPadding = false", "Gravity.CENTER_VERTICAL", "IME_ACTION_NONE", "setSingleLine(!expanded)", "editor.maxLines = if (expanded) Int.MAX_VALUE else 1", "heightIn(", "isLongClickable = true", "IMPORTANT_FOR_AUTOFILL_YES", "addTextChangedListener", "ComposerExpandedDraftMinimumHeight = 28.dp", "min = if (expanded) ComposerExpandedDraftMinimumHeight else 36.dp", "ComposerDraftTextMaximumHeight = 208.dp")) {
            assertTrue("missing $token", composerSource.contains(token))
        }
        assertFalse(composerSource.contains("singleLine = true"))
        assertFalse(composerSource.contains("OutlinedTextFieldDefaults.Container("))
        assertTrue(source.contains("Text(label, fontSize = 15.sp"))
        assertFalse(composer.contains("ComposerModelSelectorHitWidth"))
    }

    @Test
    fun `composer native editor mirrors the live theme accent for selection`() {
        val composer = source.substring(source.indexOf("private fun ComposerDraftTextField"), source.indexOf("private fun AttachmentPreviewChip"))
        for (token in listOf(
            "applyComposerNativeSelectionColors(",
            "accent = AccentOrange",
            "selectionBackground = composerSelectionBackground(AccentOrange)",
            "editor.highlightColor = selectionBackground.toArgb()",
            "Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q",
            "editor.textCursorDrawable",
            "editor.setTextSelectHandle(",
            "editor.setTextSelectHandleLeft(",
            "editor.setTextSelectHandleRight(",
            "copyAndTintForComposer",
        )) assertTrue("missing native selection-theme bridge $token", composer.contains(token))
        assertTrue(appSource.contains("CompositionLocalProvider("))
        assertTrue(appSource.contains("LocalTextSelectionColors provides selectionColors"))
        assertTrue(appSource.contains("handleColor = appearanceAccent"))
        assertTrue(appSource.contains("backgroundColor = appearanceAccent.copy"))
    }

    @Test
    fun `light orange user bubbles use the brighter warm apricot companion token`() {
        assertTrue(appSource.contains("AccentColor.ORANGE -> if (dark) Color(0xFF5D4031) else Color(0xFFF9E3D2)"))
        assertFalse(appSource.contains("AccentColor.ORANGE -> if (dark) Color(0xFF5D4031) else Color(0xFFE6D5C8)"))
    }

    @Test
    fun `top controls use controlled visible shadow while composer has one heavier tier`() {
        val headerActions = source.substring(source.indexOf("private fun ConversationHeaderContentActions"), source.indexOf("private fun ConversationHeaderFloatingIconButton"))

        for (token in listOf(
            "import androidx.compose.ui.draw.dropShadow",
            "import androidx.compose.ui.graphics.shadow.Shadow",
            "ConversationHeaderForegroundShadow = Shadow(",
            "radius = 54.dp",
            "Color(0x1B000000)",
            "offset = DpOffset(x = 0.dp, y = 3.dp)",
            "ConversationComposerForegroundShadow = Shadow(",
            "radius = 84.dp",
            "spread = 1.dp",
            "Color(0x22000000)",
            "offset = DpOffset.Zero",
            "private fun Modifier.conversationForegroundShadow(",
            "shadow: Shadow = ConversationHeaderForegroundShadow",
            "dropShadow(shape = shape, shadow = shadow)",
            "shadow = ConversationComposerForegroundShadow",
            "modifier = Modifier.conversationForegroundShadow(shape)",
            "shadowElevation = 0.dp",
        )) assertTrue("missing broad faint foreground-shadow token $token", source.contains(token) || headerActions.contains(token))
        assertFalse(source.contains("ConversationHeaderShadowElevation"))
        assertFalse(source.contains("ConversationComposerShadowElevation"))
        assertTrue(source.contains("import androidx.compose.ui.zIndex"))
        assertTrue(source.contains(".zIndex(ConversationScrollToLatestZIndex)"))
        val drawer = source.substring(source.indexOf("private fun ConversationNavigationDrawer"), source.indexOf("private fun ConversationNavigationRow"))
        assertTrue(drawer.contains("modifier = Modifier.conversationForegroundShadow(shape = CircleShape)"))
        assertTrue(drawer.contains(".conversationForegroundShadow(shape = CircleShape)"))
        assertTrue(source.contains("val activeTranscriptListState = if (state.surface"))
        assertTrue(source.contains("A child's zIndex cannot escape its parent"))
        val jumpOverlay = source.substring(
            source.indexOf("This must be a later sibling of the composer"),
            source.indexOf("// This sibling is outside the transcript and floating composer measurement."),
        )
        assertTrue(jumpOverlay.contains("if (showJumpToLatest && !state.searchPanelOpen) JumpToLatestButton("))
        assertTrue(jumpOverlay.contains("activeTranscriptListState.animateForwardByVisibleViewport"))
        assertTrue(source.contains("activeTranscriptListState.scrollToTrueBottom()"))
    }

    @Test
    fun `FB-P6-093 composer and drawer actions are isolated floating surfaces without a painted bottom dock`() {
        assertTrue(source.contains("val showFloatingComposer = state.draft != null && !state.searchPanelOpen"))
        assertTrue(source.contains("The composer is a true overlay"))
        assertTrue(source.split("contentPadding = ConversationTranscriptContentPadding").size - 1 >= 3)
        assertTrue(source.contains("val jumpToLatestBottomPadding = floatingComposerHeight + 4.dp"))
        assertTrue(source.contains("floatingComposerHeight = with(density) { coordinates.size.height.toDp() }"))
        assertTrue(source.contains(".align(Alignment.BottomCenter)"))
        assertTrue(source.contains("padding(bottom = jumpToLatestBottomPadding + 28.dp)"))
        assertTrue(source.contains("drawerContainerColor = ConversationDrawerBaseSurface"))
        val temporary = source.substring(source.indexOf("private fun TemporaryConversationPane"))
        assertTrue(temporary.contains("Box(Modifier.fillMaxSize())"))
        assertTrue(temporary.contains(".align(Alignment.BottomCenter)"))
    }

    @Test
    fun `FB-P6-060 keeps every pending image preview and its remove action inside the one composer surface`() {
        val draftComposer = source.substring(source.indexOf("private fun DraftComposer"), source.indexOf("private fun ComposerModelEntry"))
        val dock = source.substring(source.indexOf("private fun ConversationComposerDock"), source.indexOf("private fun ComposerMenuOverlay"))
        val draftPreview = source.substring(source.indexOf("private fun ComposerAttachmentPreview"), source.indexOf("private fun ComposerMenuOverlay"))
        assertTrue(draftComposer.contains("attachmentPreview = if (draft.attachments.isNotEmpty())"))
        assertTrue(draftComposer.contains("ComposerAttachmentPreview("))
        assertFalse(draftComposer.contains("TextButton(onClick = { onRemoveAttachment(attachment.id) }"))
        assertTrue(dock.contains("attachmentPreview?.invoke()"))
        assertTrue(dock.contains("Draft previews are intentionally inside this single white surface"))
        for (token in listOf("contentDescription = if (isVideo) \"草稿视频代表帧\" else \"草稿图片预览\"", "isImage -> originalAspectPreviewModifier(bitmap, maxEdge = 92.dp, fallbackSize = 92.dp)", "contentScale = if (isVideo) ContentScale.Crop else ContentScale.Fit", "contentDescription = \"移除草稿附件", "IconButton(\n            onClick = onRemove")) {
            assertTrue("missing $token", draftPreview.contains(token))
        }
    }

    @Test
    fun `temporary and normal conversations share the single composer dock and overlay entry`() {
        val normal = source.substring(source.indexOf("private fun DraftComposer"), source.indexOf("private fun ComposerMenuOverlay"))
        val temporary = source.substring(source.indexOf("private fun TemporaryConversationPane"))
        assertTrue(normal.contains("ConversationComposerDock("))
        for (token in listOf("ConversationComposerDock(", "ComposerMenuOverlay(", "attachmentDescription = \"添加附件\"", "ComposerModelEntry(")) assertTrue("missing $token", temporary.contains(token))
        assertFalse(temporary.contains("OutlinedTextField(draftText"))
        assertFalse(temporary.contains("ModalBottomSheet(onDismissRequest = { adding = false }"))
    }

    @Test
    fun `FB-P6-040 keeps model selection visibly available from the narrow mobile composer`() {
        val composer = source.substring(source.indexOf("private fun DraftComposer"), source.indexOf("private fun ComposerMenuOverlay"))
        for (token in listOf(
            "val modelLabel =",
            "onClick = onToggleModel",
            "ComposerModelEntry(",
        )) assertTrue("missing $token", source.contains(token))
        assertTrue(composer.contains("combinedClickable("))
    }

    @Test
    fun `FB-P6-056 keeps the button anchored menus minimal, stable and visually truthful`() {
        val composer = source.substring(source.indexOf("private fun DraftComposer"), source.indexOf("private fun ComposerMenuOverlay"))
        val overlay = source.substring(source.indexOf("private fun ComposerMenuOverlay"), source.indexOf("private fun ComposerDraftTextField"))
        for (token in listOf(
            "onGloballyPositioned", "onToggleAttachments", "onToggleModel", "boundsInRoot()",
        )) assertTrue("missing $token", composer.contains(token))
        for (token in listOf(
            "Box(Modifier.fillMaxSize().zIndex(ConversationModalOverlayZIndex))", "BackHandler(onBack = onOverlayBack)", "pointerInput(menu, onDismiss)", "detectTapGestures(onTap = { onDismiss() })",
            "anchor.top - heightPx - verticalGap", "attachmentActions.forEach", "ComposerOverlayAction",
            "contentColor = BodyText", "menuWidth = if (menu == ComposerMenu.MODEL) 336.dp else 248.dp",
            "Modifier.background(Color.Black.copy(alpha = 0.16f))", "RoundedCornerShape(if (menu == ComposerMenu.MODEL) 28.dp else 22.dp)",
            "shadowElevation = if (menu == ComposerMenu.MODEL) 14.dp else 10.dp", "ComposerModelPickerHeader(",
            "ComposerModelPickerSectionLabel(", "heightIn(max = modelPickerContentHeight)", "Icons.Rounded.Check", "Icons.Rounded.ChevronRight",
            "viewportWidthPx - widthPx - horizontalPadding", "zIndex(ConversationModalOverlayZIndex)",
        )) assertTrue("missing $token", overlay.contains(token))
        for (forbidden in listOf("Popup(", "PopupProperties", "addSheetVisible", "modelPickerVisible", "当前会话模型", "手动选择只影响当前普通会话", "最近路由")) assertFalse("unexpected $forbidden", overlay.contains(forbidden))
        for (token in listOf("Icons.Rounded.PhotoCamera", "\"相机\"", "Icons.Rounded.AddPhotoAlternate", "\"添加图片和视频\"", "Icons.Rounded.AttachFile", "\"添加文件\"")) assertTrue("missing $token", source.contains(token))
        val attachmentAction = source.substring(source.indexOf("private fun ComposerOverlayAction"), source.indexOf("/** The mobile composer"))
        assertTrue(attachmentAction.contains("ButtonDefaults.textButtonColors(contentColor = BodyText)"))
    }

    @Test
    fun `FB-P6-108 model picker uses a larger layered sheet without changing model owners`() {
        val overlay = source.substring(source.indexOf("private fun ComposerMenuOverlay"), source.indexOf("private fun ComposerOverlayAction"))
        val header = source.substring(source.indexOf("private fun ComposerModelPickerHeader"), source.indexOf("private fun ComposerModelPickerSectionLabel"))
        val row = source.substring(source.indexOf("private fun ComposerModelOverlayRow"), source.indexOf("private fun ComposerOverlayAction"))
        for (token in listOf(
            "val menuWidth = if (menu == ComposerMenu.MODEL) 336.dp else 248.dp",
            "ComposerModelPickerRootContentHeight", "ComposerModelPickerHeaderHeight", "32.dp + 72.dp * currentChoices.size.toFloat()", "Color.Black.copy(alpha = 0.16f)", "选择模型", "自动选择", "按任务选择", "选择具体模型",
            "heightIn(max = modelPickerContentHeight)", "selected = slot == activeModelSlot", "showNext = slot != activeModelSlot", "BackHandler(onBack = onOverlayBack)", "p5aDismissOnInwardEdgeSwipe(onOverlayBack)", "pointerInput(menu, onDismiss)", "detectTapGestures(onTap = { onDismiss() })", "if (menu == ComposerMenu.MODEL && selectedSlot != null) selectedSlot = null else onDismiss()", "onSelectModel?.invoke(null)", "onSelectModel?.invoke(choice.id)",
            "viewportWidthPx - widthPx - horizontalPadding",
        )) assertTrue("missing layered model picker token $token", overlay.contains(token))
        for (token in listOf("width(36.dp)", "height(4.dp)", "ComposerModelPickerHeaderHeight", "关闭模型选择", "返回模型分类", "Icons.AutoMirrored.Outlined.ArrowBack")) assertTrue("missing sheet header token $token", header.contains(token))
        for (token in listOf("AccentOrangeSoft", "NeutralAssistantSurface", "RoundedCornerShape(16.dp)", "heightIn(min = 64.dp)", "Icons.Rounded.ChevronRight", "Icons.Rounded.Check")) assertTrue("missing layered model row token $token", row.contains(token))
        assertTrue(overlay.indexOf("ComposerModelSlot.DAILY") < overlay.indexOf("ComposerModelSlot.DEEP"))
        assertTrue(overlay.indexOf("ComposerModelSlot.DEEP") < overlay.indexOf("ComposerModelSlot.MULTIMODAL"))
        assertTrue(overlay.indexOf("ComposerModelSlot.MULTIMODAL") < overlay.indexOf("ComposerModelSlot.COMPARE"))
        assertFalse(overlay.contains("Popup("))
        assertFalse(overlay.contains("modelPickerVisible"))
    }

    @Test
    fun `temporary Ghost directly changes only the visible mode and preserves the recovery owner`() {
        val viewModel = File("src/main/java/com/nanzhufeng/ai/ui/ConversationFoundationViewModel.kt").readText()
        for (token in listOf("fun leaveTemporaryConversation()", "temporaryRecovery = null", "attachmentPreviews = state.attachmentPreviews - temporaryPreviewIds", "已返回普通聊天；临时内容仍可在 24 小时内继续。", "temporary.readRecovery()")) assertTrue("missing $token", viewModel.contains(token))
        for (forbidden in listOf("fun exitTemporaryConversation()", "clearTemporary.execute()")) assertFalse("unexpected $forbidden", viewModel.contains(forbidden))
        val normalHeaderStart = source.indexOf("} else Box(")
        val normalHeader = source.substring(normalHeaderStart, source.indexOf("ComposerMenuOverlay(", normalHeaderStart))
        val temporaryPane = source.substring(source.indexOf("private fun TemporaryConversationPane"))
        assertTrue(normalHeader.contains("temporaryTint = ConversationControlGlyph"))
        assertTrue(temporaryPane.contains("temporaryTint = AccentOrange"))
        assertTrue(temporaryPane.contains("onLeftAction = onOpenNavigation"))
        assertTrue(temporaryPane.contains("leftIcon = Icons.Rounded.Menu"))
        assertTrue(temporaryPane.contains("leftDescription = \"打开对话导航\""))
        assertTrue(source.contains("onOpenNavigation = { drawerScope.launch { drawerState.open() } }"))
        assertTrue(source.contains("onExit = onExitTemporary"))
        assertFalse(source.contains("confirmingTemporaryExit"))
    }

    @Test
    fun `temporary startup maintenance never reads Room on the main dispatcher`() {
        val viewModel = File("src/main/java/com/nanzhufeng/ai/ui/ConversationFoundationViewModel.kt").readText()
        assertTrue(viewModel.contains("withContext(Dispatchers.IO) {\n                clearTemporary.pruneExpired()\n                temporary.readRecovery()"))
    }

    @Test fun `temporary model override reuses the normal model entry and persists only in temporary owner`() {
        val workspace = source
        val viewModel = File("src/main/java/com/nanzhufeng/ai/ui/ConversationFoundationViewModel.kt").readText()
        val temporary = workspace.substring(workspace.indexOf("private fun TemporaryConversationPane"))
        for (token in listOf("modelOptions: List<Pair<String?, String>>", "ComposerModelEntry(", "ComposerMenuOverlay(", "selectedModelId = recovery.modelOverrideId", "onModelOverrideChanged(modelId)")) assertTrue("missing $token", temporary.contains(token))
        for (forbidden in listOf("临时模型标识", "editingModelOverride", "recovery.modelOverrideId ?: \"临时\"")) assertFalse("unexpected $forbidden", temporary.contains(forbidden))
        for (token in listOf("fun updateTemporaryModelOverride", "temporary.updateModelOverride", "没有选择或调用模型")) assertTrue("missing $token", viewModel.contains(token))
    }

    @Test fun `temporary transcript keeps user ownership and never renders attachment counts or internal model labels`() {
        val temporary = source.substring(source.indexOf("private fun TemporaryConversationPane"))
        for (token in listOf("RightAlignedUserBubble {", "Icons.Rounded.PhotoCamera", "\"相机\"", "Icons.Rounded.AttachFile", "contentDescription = \"附件\"", "modelOptions: List<Pair<String?, String>>")) assertTrue("missing $token", temporary.contains(token))
        for (forbidden in listOf("个临时本地附件", "草稿含", "recovery.modelOverrideId ?: \"临时\"", "开始临时对话", "临时草稿", "发送临时消息")) assertFalse("unexpected $forbidden", temporary.contains(forbidden))
    }

    @Test fun `temporary draft images use the shared in-composer preview and remove owner`() {
        val temporary = source.substring(source.indexOf("private fun TemporaryConversationPane"))
        val viewModel = File("src/main/java/com/nanzhufeng/ai/ui/ConversationFoundationViewModel.kt").readText()
        for (token in listOf("attachmentPreview = if (recovery.draftAttachmentIds.isNotEmpty())", "ComposerAttachmentPreview(")) assertTrue("missing $token", temporary.contains(token))
        assertTrue(source.contains("onRemoveAttachment = onRemoveTemporaryDraftAttachment"))
        for (token in listOf("fun removeTemporaryDraftAttachment", "temporary.removeDraftAttachment(id)", "temporaryAttachmentReferences")) assertTrue("missing $token", viewModel.contains(token))
    }

    @Test fun `FB-P6-106 full-resolution camera output is privately captured before both draft owners import it`() {
        val app = File("src/main/java/com/nanzhufeng/ai/ui/NanfengAiApp.kt").readText()
        val owner = File("src/main/java/com/nanzhufeng/ai/ui/ConversationFoundationViewModel.kt").readText()
        val reader = File("src/main/java/com/nanzhufeng/ai/data/AndroidGallerySelectionReader.kt").readText()
        val fileProviderPaths = File("src/main/res/xml/attachment_share_paths.xml").readText()
        for (token in listOf("ActivityResultContracts.TakePicture()", "createCameraCaptureUri", "conversationCamera.launch(uri)", "temporaryCamera.launch(uri)", "conversationCameraUri", "temporaryCameraUri")) assertTrue("missing full-resolution camera token $token", app.contains(token))
        assertFalse(app.contains("TakePicturePreview"))
        assertTrue(fileProviderPaths.contains("<cache-path name=\"camera_capture\" path=\"camera_capture/\" />"))
        for (token in listOf("fun onConversationCameraResult(uri: Uri?, captured: Boolean)", "fun onTemporaryCameraResult(uri: Uri?, captured: Boolean)", "galleryReader.openConversationVisual(uri)", "galleryReader.discardAppPrivateCapture(uri)", "addAttachment.add(conversationId, opened.selection)", "addTemporaryAttachment.add(opened.selection)", "相机原图已私有复制", "相机原图已加入临时会话")) assertTrue("missing original-camera owner token $token", owner.contains(token))
        assertTrue(reader.contains("fun discardAppPrivateCapture(uri: Uri?)"))
        assertTrue(reader.contains("uri.authority != localCaptureAuthority"))
        val cameraOwner = owner.substring(owner.indexOf("fun onTemporaryCameraResult"), owner.indexOf("fun removeDraftAttachment"))
        for (forbidden in listOf("Bitmap?", "Bitmap.CompressFormat.JPEG", "bitmap.compress", "ByteArrayOutputStream", "ByteArrayInputStream")) assertFalse("preview or recompression must not remain in camera path: $forbidden", cameraOwner.contains(forbidden))
    }

    @Test
    fun `conversation overlays dismiss as cancel before drawer or workspace and preserve drafts`() {
        for (token in listOf(
            "ModalNavigationDrawer(",
            "val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)",
            "PopupProperties(focusable = true, dismissOnBackPress = true, dismissOnClickOutside = true)",
            "onDismiss = { composerMenu = ComposerMenu.NONE }",
            "detectTapGestures(onTap = { onDismiss() })",
            "ConversationComposerDock(",
            "onDismissRequest = { confirmingDelete = false }",
        )) assertTrue("missing $token", source.contains(token))
        val renameBackdrop = source.substring(source.indexOf("private fun DismissibleDialogBackdrop"), source.indexOf("private fun CompactConversationRenameField"))
        assertTrue(renameBackdrop.contains("detectTapGestures(onTap = { onDismiss() })"))
        assertTrue(renameBackdrop.contains(".fillMaxSize()"))
        assertTrue(renameBackdrop.contains(".p5aDismissOnInwardEdgeSwipe(onDismiss)"))
        assertFalse(source.contains("onDismissRequest = {}"))
    }

    @Test
    fun `mobile chat canvas keeps the composer outside navigation and management stacks`() {
        assertTrue(source.contains("The drawer owns navigation and management"))
        assertTrue(source.contains("ConversationNavigationDrawer("))
        assertTrue(source.contains("onExport = { drawerScope.launch { drawerState.close() }; onExport() }"))
        assertTrue(source.contains("Text(\"导出 Markdown\")"))
        assertFalse(source.contains("Text(\"导出当前对话\")"))
    }

    @Test
    fun `archive and recycle are settings lifecycle projections rather than drawer scopes`() {
        val appSource = File("src/main/java/com/nanzhufeng/ai/ui/NanfengAiApp.kt").readText()
        val management = source.substring(source.indexOf("internal fun ConversationManagementSettingsCard"), source.indexOf("private fun ConversationSelector"))
        for (token in listOf("internal fun ConversationManagementSettingsCard", "DataStorageGroupedCard", "ConversationLifecycleEntry(", "title = \"收藏\"", "title = \"已归档\"", "title = \"回收站\"", "ConversationLifecycleListSettingsCard", "ConversationManagementAction.RESTORE_DELETED", "ConversationManagementAction.UNARCHIVE")) {
            assertTrue("missing $token", management.contains(token))
        }
        assertTrue(appSource.contains("ConversationManagementSettingsCard("))
        for (token in listOf("SettingsDestination.ARCHIVED_CONVERSATIONS", "SettingsDestination.RECYCLE_BIN", "ConversationListScope.ARCHIVED", "ConversationListScope.DELETED")) assertTrue("missing separated route: $token", appSource.contains(token))
        assertFalse(management.contains("DropdownMenu("))
        assertFalse(management.contains("查看：\$selectedScope"))
        assertTrue(source.contains("label = { Text(\"搜索\") }"))
    }

    @Test
    fun `favorite archive and recycle rows open their conversation and return to the originating lifecycle list`() {
        val appSource = File("src/main/java/com/nanzhufeng/ai/ui/NanfengAiApp.kt").readText()
        val favorites = source.substring(
            source.indexOf("internal fun FavoriteConversationListSettingsCard"),
            source.indexOf("private fun ConversationLifecycleEntry"),
        )
        val lifecycle = source.substring(
            source.indexOf("internal fun ConversationLifecycleListSettingsCard"),
            source.indexOf("private fun formatLifecycleTime"),
        )
        val workspace = source.substring(
            source.indexOf("internal fun ConversationWorkspaceDialog"),
            source.indexOf("private fun ConversationNavigationDrawer"),
        )
        for (token in listOf(
            "FavoriteConversationSwipeRow(",
            "label = \"取消收藏\"",
            "if (revealed) onRevealChanged(false) else onOpenConversation()",
            "if (revealedConversationId != null) revealedConversationId = null else onOpenConversation(conversation)",
            "onOpenConversation: (com.nanzhufeng.ai.domain.Conversation) -> Unit",
            "ConversationLifecycleSwipeRow(",
        )) assertTrue("missing lifecycle opening token $token", favorites.contains(token) || lifecycle.contains(token))
        assertFalse("favorite cancellation must not remain a permanent row button", favorites.contains("TextButton(onClick = { onUnfavorite(conversation) }"))
        assertTrue(workspace.contains("if (interceptsSystemBack && !drawerState.isOpen) BackHandler(onBack = onDismiss)"))
        for (token in listOf(
            "var lifecycleConversationReturnDestination by rememberSaveable",
            "conversationViewModel.setListScope(lifecycleScope(destination))",
            "onReturnToLifecycleList = returnToLifecycleList",
            "onOpenLifecycleConversation = { destination, conversation ->",
            "conversationViewModel.selectConversation(conversation.id)",
            "onRouteSelected(P5ARoute.CONVERSATION)",
            "onRouteSelected(P5ARoute.SETTINGS)",
            "onOpenConversation = { conversation -> onOpenLifecycleConversation(SettingsDestination.FAVORITE_CONVERSATIONS, conversation) }",
            "onOpenConversation = { conversation -> onOpenLifecycleConversation(SettingsDestination.ARCHIVED_CONVERSATIONS, conversation) }",
            "onOpenConversation = { conversation -> onOpenLifecycleConversation(SettingsDestination.RECYCLE_BIN, conversation) }",
        )) assertTrue("missing lifecycle return token $token", appSource.contains(token))
    }

    @Test
    fun `settings route starts with categories and opens dense controls only in a detail page`() {
        val appSource = File("src/main/java/com/nanzhufeng/ai/ui/NanfengAiApp.kt").readText()
        val hierarchy = appSource.substring(appSource.indexOf("private fun SettingsHierarchy"), appSource.indexOf("private fun WorkbenchRoute"))
        val categoryList = appSource.substring(appSource.indexOf("private fun SettingsCategoryList"), appSource.indexOf("private fun SettingsCategoryRow"))
        for (title in listOf("对话", "外观", "应用与数据", "工作区", "个性化", "模型与联网", "对话管理", "数据与存储", "隐私与安全")) assertTrue("missing current settings category $title", categoryList.contains(title))
        assertTrue(appSource.contains("val returnFromSettings: () -> Unit = {"))
        assertTrue(appSource.contains("conversationViewModel.setListScope(com.nanzhufeng.ai.domain.ConversationListScope.ACTIVE)"))
        assertTrue(appSource.contains("onReturnToConversationDrawer()"))
        assertTrue(appSource.contains("private data class SettingsNavigationEntry("))
        assertTrue(appSource.contains("var settingsNavigationStack by remember"))
        assertTrue(appSource.contains("fun openSettingsLevel(route: P5ARoute, destination: SettingsDestination)"))
        assertTrue(appSource.contains("settingsNavigationStack = settingsNavigationStack.dropLast(1)"))
        assertTrue(appSource.contains("route == P5ARoute.ADAPTERS && chatGptImportState.selected != null"))
        assertTrue(appSource.contains("BackHandler(onBack = returnFromSettings)"))
        assertTrue(appSource.contains("Modifier.systemGestureExclusion()"))
        assertTrue(appSource.contains("Modifier.settingsEdgeExit(returnFromSettings)"))
        assertTrue(appSource.contains("contentDescription = \"返回侧栏\""))
        assertTrue(appSource.contains("contentDescription = \"返回设置\""))
        assertTrue(hierarchy.contains("if (destination == SettingsDestination.HOME)"))
        for (token in listOf("ModelServiceStatusCard", "ConversationManagementSettingsCard", "WorkspaceSettingsCard", "DevelopmentDiagnosticsSettingsCard", "dataStorageImportContent", "PrivacyDataPage")) assertTrue("missing current detail owner $token", hierarchy.contains(token))
        assertFalse(categoryList.contains("ConversationManagementSettingsCard"))
        assertFalse(categoryList.contains("P6GModelSelectionSettingsCard"))
        for (token in listOf("InvocationLedgerCard", "P6GModelSelectionSettingsCard", "P6ETemporaryMaintenanceAcceptanceCard", "installP6GLocalFixtureCatalog")) assertFalse("normal settings must hide $token", hierarchy.contains(token))
    }

    @Test
    fun `conversation surfaces consume the shared live appearance accent token`() {
        val appSource = File("src/main/java/com/nanzhufeng/ai/ui/NanfengAiApp.kt").readText()
        for (token in listOf("internal val DefaultAccentOrange = Color(0xFFE97128)", "internal var AccentOrange by mutableStateOf(DefaultAccentOrange)", "internal var AccentOrangeHover by mutableStateOf", "internal var AccentOrangePressed by mutableStateOf", "internal var AccentOrangeSoft by mutableStateOf", "internal var ComposerFocusBorder by mutableStateOf", "internal var AccentDisabled by mutableStateOf", "primary = appearanceAccent", "onPrimary = AccentOnPrimary", "primaryContainer = appearance.accentColor.userBubbleColor(false)", "internal var BrandGreen by mutableStateOf")) assertTrue("missing $token", appSource.contains(token))
        assertTrue(source.contains("if (selected) AccentOrangeSoft else ConversationDrawerRowSurface"))
        val row = source.substring(source.indexOf("private fun ConversationNavigationRow"), source.indexOf("private data class ConversationActionMenuTarget"))
        assertTrue(row.contains("contentColor = BodyText"))
    }

    @Test
    fun `FB-P6-080 keeps warm user bubbles and identifies quotes without a colored slab`() {
        val appSource = File("src/main/java/com/nanzhufeng/ai/ui/NanfengAiApp.kt").readText()
        val attachment = source.substring(source.indexOf("private fun AttachmentPreviewChip"), source.indexOf("private fun PdfPreviewDialog"))
        val composerAttachment = source.substring(source.indexOf("private fun ComposerAttachmentPreview"), source.indexOf("private fun ComposerMenuOverlay"))
        val presentation = source.substring(source.indexOf("private fun PresentationBlockView"), source.indexOf("private fun inlineText"))
        for (token in listOf("internal fun chatRoleVisual", "MessageRole.USER -> ChatRoleVisual(", "ActiveUserBubbleSurface", "ActiveUserBubbleAccent", "MessageRole.ASSISTANT -> ChatRoleVisual(NeutralAssistantSurface", "MessageRole.SYSTEM -> ChatRoleVisual(NeutralSystemSurface", "MessageRole.TOOL -> ChatRoleVisual(ToolSurface")) assertTrue("missing $token", appSource.contains(token))
        assertFalse(attachment.contains("AccentOrangeSoft"))
        assertFalse(composerAttachment.contains("AccentOrangeSoft"))
        assertTrue(composerAttachment.contains("background(ForegroundSurface)"))
        for (token in listOf("is PresentationBlock.Quote -> Row", "height(IntrinsicSize.Min)", "Box(Modifier.width(3.dp).fillMaxHeight()", "SecondaryText.copy(alpha = 0.42f)")) assertTrue("missing quote format token $token", presentation.contains(token))
        assertFalse(presentation.contains("fontStyle = androidx.compose.ui.text.font.FontStyle.Italic"))
        assertTrue(presentation.contains("is PresentationBlock.Note -> InlinePresentationText("))
        assertTrue(presentation.contains("fontSize = scaledConversationTextUnit(typography.note)"))
        assertTrue(presentation.contains("lineHeight = scaledConversationTextUnit(typography.noteLineHeight)"))
        assertTrue(presentation.contains("suppressEmphasis = true"))
        assertFalse(presentation.contains("background(Color(0xFFE8ECE9))"))
        MessageRole.entries.forEach { role -> assertTrue("${role.name} body contrast", contrast(chatRoleVisual(role).surface, chatRoleVisual(role).body) >= 4.5) }
    }

    @Test
    fun `FB-P6-081 makes links directly clickable with a reinforced color and no underline`() {
        val inline = source.substring(source.indexOf("private fun inlineText"), source.indexOf("private fun ConversationActions"))
        for (token in listOf("withLink(", "LinkAnnotation.Url(", "url = span.url", "TextLinkStyles(style = SpanStyle(color = BrandGreen, fontWeight = FontWeight.SemiBold))")) assertTrue("missing clickable link token $token", inline.contains(token))
        assertFalse(inline.contains("TextDecoration.Underline"))
    }

    @Test
    fun `FB-P6-089 gives assistant information blocks local hierarchy tables and individual copy actions`() {
        val presentation = source.substring(source.indexOf("private fun PresentationBlockView"), source.indexOf("private fun inlineText"))
        for (token in listOf("is PresentationBlock.Table -> MarkdownTable(block)", "CopyableInformationSurface", "MarkdownTable(block)", "Icon(Icons.Rounded.ContentCopy", "contentDescription = \"复制\$label\"", "NeutralAssistantSurface", "horizontalScroll(scrollState)", "adaptiveTableColumnWeights(block)", "cellWidths = columnWeights.map", "trailingHeaderInset = 38.dp", "复制表格（保留 Markdown 格式）", "tableMarkdownText(block)", "VerticalDivider(", "contentAlignment = Alignment.Center", "textAlign = androidx.compose.ui.text.style.TextAlign.Center")) assertTrue("missing formatted assistant content token $token", presentation.contains(token))
        val inline = source.substring(source.indexOf("private fun inlineText"), source.indexOf("private fun ConversationActions"))
        assertTrue(inline.contains("appendConversationFindText(\"\${span.label} ↗\", highlightQuery)"))
    }

    @Test
    fun `FB-P6-112 renders readable Markdown structure without literal syntax noise`() {
        val presentation = source.substring(source.indexOf("private fun PresentationBlockView"), source.indexOf("private fun inlineText"))
        val inline = source.substring(source.indexOf("private fun inlineText"), source.indexOf("private fun ConversationActions"))
        assertTrue(presentation.contains("is PresentationBlock.HorizontalRule -> Box("))
        assertTrue(presentation.contains("Arrangement.spacedBy(if (assistantDocument) 8.dp else 6.dp)"))
        assertTrue(presentation.contains("val typography = if (assistantDocument) AssistantDocumentTypography else UserBubbleTypography"))
        assertTrue(source.contains("headingOne = 26.sp, headingTwo = 22.sp, headingMinor = 18.sp"))
        assertTrue(presentation.contains("Modifier.widthIn(min = 14.dp, max = 22.dp)"))
        assertTrue(presentation.contains("Spacer(Modifier.width(4.dp))"))
        assertTrue(presentation.contains("val listFontSize = scaledConversationTextUnit(typography.list)"))
        assertTrue(presentation.contains("val listLineHeight = scaledConversationTextUnit(typography.listLineHeight)"))
        assertTrue(presentation.contains("Modifier.widthIn(min = 14.dp, max = 22.dp).alignByBaseline()"))
        assertTrue(presentation.contains("Modifier.weight(1f).alignByBaseline()"))
        assertTrue(presentation.contains("val listStartIndent = 12.dp + (depth.coerceIn(0, 6) * 16).dp"))
        assertTrue(presentation.contains("padding(start = listStartIndent)"))
        assertFalse(presentation.contains("Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth())"))
        assertFalse(presentation.contains("modifier = Modifier.width(28.dp)"))
        assertTrue(inline.contains("is InlinePresentation.Code -> {"))
        assertTrue(inline.contains("appendConversationFindText(span.value, highlightQuery)"))
        assertFalse(inline.contains("append(\"`${'$'}{span.value}`\")"))
        assertTrue(source.contains("InlineCodeChipBackground: Color get() = NeutralSystemSurface"))
        assertTrue(inline.contains("pushStringAnnotation(InlineCodeChipAnnotationTag, span.value)"))
        assertTrue(inline.contains("drawRoundRect("))
        assertTrue(inline.contains("cornerRadius = CornerRadius(8.dp.toPx())"))
    }

    @Test
    fun `FB-P6-113 keeps source URLs out of prose and routes the source shortcut to the system browser`() {
        val presentation = source.substring(source.indexOf("private fun PresentationBlockView"), source.indexOf("private fun ConversationActions"))
        val inline = source.substring(source.indexOf("private fun inlineText"), source.indexOf("private fun ConversationActions"))
        assertTrue(presentation.contains("InlinePresentationText("))
        assertTrue(presentation.contains("private fun SourceLinkShortcut"))
        assertTrue(inline.contains("appendInlineContent(SourceShortcutInlineContentId, \"来源\")"))
        assertTrue(source.contains("private fun SourceWebsiteGlyph"))
        assertTrue(source.contains("private fun OverlappingSourceWebsiteGlyphs"))
        assertTrue(source.contains("visibleSources = sources.take(3)"))
        assertTrue(source.contains("glyphOffset = 11.dp"))
        assertTrue(source.contains("if (sources.size > 1) OverlappingSourceWebsiteGlyphs(sources) else SourceWebsiteGlyph(primary)"))
        assertTrue(source.contains("sourceShortcutLabel(sources)"))
        assertTrue(source.contains("Icons.Rounded.Language"))
        assertTrue(source.contains("RoundedCornerShape(16.dp)"))
        assertFalse(source.contains("shape = CircleShape,\n        modifier = Modifier.size(24.dp).semantics { contentDescription = \"打开 ${'$'}{sources.size} 个来源网站\" }"))
        assertTrue(source.contains("private fun SourceLinksDialog"))
        val sourceDialog = source.substring(source.indexOf("private fun SourceLinksDialog"), source.indexOf("private fun ConversationActions"))
        for (token in listOf("DialogProperties(usePlatformDefaultWidth = false", "DismissibleDialogBackdrop(onDismiss)", "padding(horizontal = 16.dp).fillMaxWidth()", "shape = RoundedCornerShape(24.dp)")) {
            assertTrue("source dialog must use the available phone width: $token", sourceDialog.contains(token))
        }
        assertTrue(source.contains("Intent(Intent.ACTION_VIEW, Uri.parse(source.url)).addCategory(Intent.CATEGORY_BROWSABLE)"))
        assertTrue(source.contains("context.startActivity("))
        assertTrue(source.contains("未找到可打开网站的系统浏览器。"))
    }

    @Test
    fun `FB-P6-082 keeps the edit branch dialog to its title editor and actions`() {
        val editDialog = source.substring(source.indexOf("editingMessageId?.let"), source.indexOf("state.imagePreview?.let"))
        for (token in listOf("EditUserMessageDialog(", "value = editingText", "onCreateBranch", "onEditUserMessage(com.nanzhufeng.ai.domain.MessageNodeId(rawId), editingText)")) assertTrue("missing edit branch route token $token", editDialog.contains(token))
        val editSurface = source.substring(source.indexOf("private fun EditUserMessageDialog"), source.indexOf("private fun MessageContextAction"))
        for (token in listOf("DialogProperties(usePlatformDefaultWidth = false", "padding(horizontal = 12.dp)", "fillMaxWidth()", "color = ForegroundSurface", "shape = RoundedCornerShape(24.dp)", "OutlinedTextField(", "minLines = 3", "heightIn(min = 132.dp, max = 580.dp)", "Modifier.width(76.dp).height(32.dp)", "Modifier.width(44.dp).height(30.dp)", "Text(\"创建分支\", fontSize = 13.sp)", "Text(\"取消\", fontSize = 13.sp)")) assertTrue("missing wide edit surface token $token", editSurface.contains(token))
        for (removed in listOf("编辑消息并创建分支", "确认后只在本机创建修订分支", "用户消息", "仅纯文本消息可编辑")) assertFalse("unexpected edit-dialog detail $removed", editDialog.contains(removed))
    }

    @Test
    fun `FB-P6-111 gives every completed conversation copy a shared system haptic`() {
        val copyAction = source.substring(source.indexOf("private fun rememberConversationCopyTextAction"), source.indexOf("private fun MessageContextAction"))
        for (token in listOf(
            "LocalClipboardManager.current",
            "LocalView.current",
            "clipboard.setText",
            "Build.VERSION.SDK_INT >= Build.VERSION_CODES.R",
            "HapticFeedbackConstants.CONFIRM",
            "HapticFeedbackConstants.KEYBOARD_TAP",
            "view.performHapticFeedback(feedback)",
        )) assertTrue("missing copy success haptic token $token", copyAction.contains(token))
        for (token in listOf(
            "val copyText = rememberConversationCopyTextAction()",
            "onCopyAssistant = { copyText(presentedMessagePlainText(it.message)) }",
            "copyText(presentedMessagePlainText(transcript.message))",
            "IconButton(onClick = { copyText(value) })",
        )) assertTrue("missing shared copy entry token $token", source.contains(token))
    }

    @Test
    fun `FB-P6-090 launches the platform share sheet immediately without an in-app confirmation dialog`() {
        assertTrue(source.contains("val shareMessage: (PresentedTranscriptMessage) -> Unit"))
        assertTrue(source.contains("context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND)"))
        assertTrue(source.contains("MessageContextAction(Icons.Rounded.Share, \"分享\")"))
        assertFalse(source.contains("通过系统分享？"))
        assertFalse(source.contains("打开系统分享"))
        assertFalse(source.contains("sharingMessageId"))
    }

    @Test
    fun `file previews and anchored attachment info expose the same download and share actions`() {
        for (token in listOf(
            "private fun FilePreviewTopActions",
            "contentDescription = \"下载文件\"",
            "Text(\"分享\"",
            "AttachmentTransferAction.DOWNLOAD",
            "AttachmentTransferAction.SHARE",
            "private fun AttachmentPopupAction",
            "LocalAttachmentTransferRequest",
            "performAttachmentTransfer",
            "MediaStore.Downloads.EXTERNAL_CONTENT_URI",
            "FileProvider.getUriForFile",
        )) assertTrue("missing file handoff token $token", source.contains(token))
        val attachmentPopup = source.substring(source.indexOf("private fun AttachmentInfoPopup"), source.indexOf("private fun attachmentKindLabel"))
        assertTrue(attachmentPopup.contains("AttachmentPopupAction(Icons.Rounded.FileDownload, \"下载\""))
        assertTrue(attachmentPopup.contains("AttachmentPopupAction(Icons.Rounded.Share, \"分享\""))
    }

    @Test
    fun `one image downloads directly while a multi-image AI result offers current or batch download`() {
        val imagePreview = source.substring(source.indexOf("private fun ImagePreviewDialog"), source.indexOf("private fun ComposerSendButton"))
        for (token in listOf(
            "val hasMultipleGeneratedImages = generatedImageIds.size > 1",
            "if (hasMultipleGeneratedImages) batchDownloadVisible = true",
            "else requestAttachmentTransfer(preview.id, AttachmentTransferAction.DOWNLOAD)",
            "\"下载当前图片\"",
            "\"同时下载 \${generatedImageIds.size} 张\"",
            "onRequestAttachmentTransfers(generatedImageIds, AttachmentTransferAction.DOWNLOAD)",
        )) assertTrue("missing generated-image download token $token", imagePreview.contains(token))
        assertTrue(source.contains("request.batch.forEachIndexed { index, item ->"))
        assertTrue(source.contains("saveAttachmentToUserCollection(context, item, transferStartedAtMillis + index)"))
        assertTrue(source.contains("savedUris.forEach { savedUri -> resolver.delete(savedUri, null, null) }"))
        assertTrue(source.contains("MediaStore.Images.Media.EXTERNAL_CONTENT_URI"))
        assertTrue(source.contains("MediaStore.Video.Media.EXTERNAL_CONTENT_URI"))
        assertTrue(source.contains("MediaStore.Downloads.EXTERNAL_CONTENT_URI"))
        assertTrue(source.contains("\"${'$'}{Environment.DIRECTORY_DCIM}/Camera\""))
        assertTrue(source.contains("stampDownloadedImageTakenAt(resolver, uri, downloadedAtMillis)"))
        assertTrue(source.contains("android.media.ExifInterface.TAG_DATETIME_ORIGINAL"))
        assertTrue(source.contains("android.media.ExifInterface.TAG_OFFSET_TIME_ORIGINAL"))
        assertFalse(source.contains("put(MediaStore.Images.Media.DATE_TAKEN"))
        assertTrue(source.contains("resolver.openOutputStream(uri, \"wt\")"))
        assertTrue(source.contains("if (item.mimeType == \"video/mp4\")"))
        assertTrue(source.contains("remuxDownloadedMp4(context, resolver, uri, item.open)"))
        assertTrue(source.contains("android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4"))
        assertFalse(source.contains("runCatching { stampDownloadedImageTakenAt"))
        assertFalse(source.contains("runCatching { remuxDownloadedMp4"))
        assertTrue(source.contains("无法按本次下载时间保存到图库，文件未保留。"))
    }

    @Test
    fun `FB-P6-083 separates in-place text selection from the message action popup`() {
        val messageBubble = source.substring(source.indexOf("private fun MessageBubble"), source.indexOf("private fun RightAlignedUserBubble"))
        val sharedBubble = source.substring(source.indexOf("private fun RightAlignedUserBubble"), source.indexOf("private fun AssistantMessageActionRow"))
        assertTrue(messageBubble.contains("SelectionContainer {"))
        assertTrue(messageBubble.contains("copy toolbar at the original message position"))
        assertTrue(messageBubble.contains("onLongPress = { pressPosition -> messageBounds?.let"))
        assertTrue(sharedBubble.contains("gesture zones never widen a naturally short message"))
        assertTrue(sharedBubble.contains(".align(Alignment.TopCenter)"))
        assertTrue(sharedBubble.contains(".align(Alignment.BottomCenter)"))
        assertTrue(sharedBubble.contains(".align(Alignment.CenterStart)"))
        assertTrue(sharedBubble.contains(".align(Alignment.CenterEnd)"))
        assertTrue(sharedBubble.contains(".fillMaxWidth()"))
        assertTrue(sharedBubble.contains("BoxWithConstraints(Modifier.matchParentSize())"))
        assertTrue(sharedBubble.contains("val sideMenuZone = maxWidth * 0.25f"))
        assertTrue(sharedBubble.contains(".width(sideMenuZone)"))
        assertTrue(sharedBubble.contains(".height(12.dp)"))
        assertTrue(sharedBubble.contains("val menuLongPress = Modifier.pointerInput(onLongPress)"))
        assertTrue(sharedBubble.contains("detectTapGestures(onLongPress = { pressPosition -> onLongPress(pressPosition) })"))
        assertFalse(sharedBubble.contains("horizontalMenuInset"))
    }

    private fun contrast(a: Color, b: Color): Double {
        fun channel(value: Float): Double = if (value <= 0.04045f) value / 12.92 else Math.pow(((value + 0.055f) / 1.055f).toDouble(), 2.4)
        fun luminance(value: Color) = 0.2126 * channel(value.red) + 0.7152 * channel(value.green) + 0.0722 * channel(value.blue)
        val (lighter, darker) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (lighter + 0.05) / (darker + 0.05)
    }
}

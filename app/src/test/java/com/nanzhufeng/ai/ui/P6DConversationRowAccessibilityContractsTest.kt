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

    @Test
    fun `FB-P6-079 keeps Android app popup surfaces pure white`() {
        val selectionDialog = source.substring(source.indexOf("private fun WideTextSelectionDialog"), source.indexOf("private fun MessageContextAction"))
        for (token in listOf("DialogProperties(usePlatformDefaultWidth = false", "padding(horizontal = 16.dp)", "fillMaxWidth()", "heightIn(max = 580.dp)", "verticalScroll(rememberScrollState())", "color = Color.White", "shape = RoundedCornerShape(24.dp)")) assertTrue("missing selected-text white surface token $token", selectionDialog.contains(token))
        for (token in listOf("surfaceTint = Color.Transparent", "surfaceVariant = Color.White", "surfaceContainerLow = Color.White", "surfaceContainer = Color.White", "surfaceContainerHigh = Color.White", "surfaceContainerHighest = Color.White")) assertTrue("missing global white popup token $token", appSource.contains(token))
    }

    @Test
    fun `FB-P6-061 compact conversation row routes long press and its bounds to the root action owner`() {
        assertTrue(source.contains("onLongClick = { rowBounds?.let { onRequestConversationActions(conversation, it) } }"))
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
    fun `compact conversation menu keeps icon actions and isolates delete without headers`() {
        for (token in listOf("ConversationMenuAction(Icons.Outlined.PushPin", "Icons.Outlined.Archive", "Icons.Outlined.Unarchive", "ConversationMenuAction(Icons.Outlined.DeleteOutline", "Icons.Outlined.ChevronRight", "Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))", "onRequestDelete(conversation)")) {
            assertTrue("missing $token", source.contains(token))
        }
        assertTrue(source.contains("if (workMode) ConversationMenuAction"))
        for (forbidden in listOf("Text(\"会话操作\"", "Text(\"危险操作\"", "Text(conversation.title, style = MaterialTheme.typography.titleMedium")) assertFalse("unexpected $forbidden", source.contains(forbidden))
    }

    @Test
    fun `FB-P6-064 conversation menu stays compact with a soft low-elevation surface`() {
        val actionSheet = source.substring(source.indexOf("private fun ConversationActionSheet"), source.indexOf("private fun ConversationMenuAction"))
        val actionRow = source.substring(source.indexOf("private fun ConversationMenuAction"), source.indexOf("private fun ConversationManagementBar"))
        for (token in listOf("val menuWidth = 224.dp", "(rowCount * 48).dp + 12.dp", "RoundedCornerShape(20.dp)", "shadowElevation = 6.dp", "Column(Modifier.padding(vertical = 6.dp))")) {
            assertTrue("missing compact menu token $token", actionSheet.contains(token))
        }
        for (token in listOf("height(48.dp)", "Modifier.size(22.dp)", "Spacer(Modifier.width(16.dp))", "fontSize = 15.sp", "Modifier.size(18.dp)")) {
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
        assertTrue(actionSheet.contains("ConversationMenuAction(Icons.Outlined.DeleteOutline"))
    }

    @Test
    fun `FB-P6-067 rename dialog keeps only its field and actions`() {
        val renameInvocation = source.substring(source.indexOf("if (renameVisible) CompactConversationRenameDialog"), source.indexOf("if (confirmingDelete) AlertDialog"))
        val renameDialog = source.substring(source.indexOf("private fun CompactConversationRenameDialog"), source.indexOf("private fun TranscriptDateDivider"))
        assertTrue(renameInvocation.contains("onDismiss = { renameVisible = false }"))
        assertTrue(renameInvocation.contains("ConversationManagementAction.RENAME"))
        assertTrue(renameDialog.contains("CompactConversationRenameField(value = value, onValueChange = onValueChange)"))
        assertFalse(renameInvocation.contains("Text(\"重命名会话\")"))
        assertFalse(renameInvocation.contains("1–120 个 Unicode 字符；空白不会保存。"))
    }

    @Test
    fun `FB-P6-069 rename dialog fills the available sidebar width while compressing vertical space`() {
        val renameDialog = source.substring(source.indexOf("private fun CompactConversationRenameDialog"), source.indexOf("private fun TranscriptDateDivider"))
        for (token in listOf("DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true, dismissOnClickOutside = true)", "Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart)", "DismissibleDialogBackdrop(onDismiss)", "padding(horizontal = 12.dp)", "widthIn(max = 336.dp)", "RoundedCornerShape(20.dp)", "padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 10.dp)", "CompactConversationRenameField(value = value, onValueChange = onValueChange)", "height(34.dp)", "RoundedCornerShape(17.dp)", "height(32.dp)", "RoundedCornerShape(16.dp)", "Arrangement.spacedBy(8.dp, Alignment.End)")) {
            assertTrue("missing compact rename token $token", renameDialog.contains(token))
        }
        val field = source.substring(source.indexOf("private fun CompactConversationRenameField"), source.indexOf("private fun TranscriptDateDivider"))
        for (token in listOf("height(48.dp)", ".border(1.dp, Color(0xFF79747E), shape)", "contentAlignment = Alignment.CenterStart", "offset(x = 12.dp, y = (-8).dp)")) assertTrue("missing full-height field token $token", field.contains(token))
        assertFalse(renameDialog.contains("AlertDialog("))
    }

    @Test
    fun `FB-P6-068 submitted drafts always return the normal and temporary transcript to latest`() {
        val normalTranscript = source.substring(source.indexOf("val listState = chatTranscriptListState"), source.indexOf("val showJumpToLatest"))
        assertTrue(source.contains("var chatSentFromMessageCount by remember(state.selectedConversationId)"))
        assertTrue(normalTranscript.contains("sentCount != null && state.messages.size > sentCount"))
        assertTrue(normalTranscript.contains("listState.scrollToItem(listState.layoutInfo.totalItemsCount - 1)"))
        assertTrue(source.contains("chatSentFromMessageCount = state.messages.size"))
        assertTrue(source.contains("workSentFromMessageCount = state.messages.size"))

        val temporaryTranscript = source.substring(source.indexOf("private fun TemporaryConversationPane"))
        assertTrue(temporaryTranscript.contains("val listState = rememberLazyListState()"))
        assertTrue(temporaryTranscript.contains("sentCount != null && recovery.messages.size > sentCount"))
        assertTrue(temporaryTranscript.contains("LazyColumn(state = listState"))
        assertTrue(temporaryTranscript.contains("sentFromMessageCount = recovery.messages.size\n                    onSubmit()"))
    }

    @Test
    fun `conversation and work menus keep their archive boundary`() {
        val archiveAction = "if (workMode) ConversationMenuAction(if (conversation.archivedAt == null) Icons.Outlined.Archive"
        assertTrue(source.contains(archiveAction))
        for (alwaysAvailable in listOf("Icons.Outlined.PushPin", "ConversationManagementAction.RENAME", "ConversationManagementAction.SOFT_DELETE")) {
            assertTrue("missing $alwaysAvailable", source.contains(alwaysAvailable))
        }
    }

    @Test
    fun `pin and archive semantics never fall back to text symbols`() {
        for (forbidden in listOf("\\\"⌁\\\"", "\\\"▤\\\"", "\\\"⚡\\\"")) assertFalse("unexpected $forbidden", source.contains(forbidden))
    }

    @Test
    fun `FB-P6-078 uses a compact black message popup anchored to the long pressed message`() {
        val messagePopup = source.substring(source.indexOf("private fun MessageActionPopup"), source.indexOf("private fun MessageContextAction"))
        val messageAction = source.substring(source.indexOf("private fun MessageContextAction"), source.indexOf("private val transcriptTimeFormatter"))
        for (token in listOf("messageActionTarget", "MessageActionMenuTarget", "MessageActionPopup(", "Icons.Outlined.ContentCopy", "Icons.Outlined.SelectAll", "Icons.Outlined.Share", "\"编辑消息\"", "Intent.ACTION_SEND", "formatTranscriptTimeOrNull")) assertTrue("missing $token", source.contains(token))
        for (token in listOf("val menuWidth = 224.dp", "(rowCount * 46).dp", "RoundedCornerShape(20.dp)", "shadowElevation = 6.dp", "PopupProperties(focusable = true, dismissOnBackPress = true, dismissOnClickOutside = true)", "offset = IntOffset(x, y)")) assertTrue("missing compact popup token $token", messagePopup.contains(token))
        for (token in listOf("height(46.dp)", "Modifier.size(22.dp)", "fontSize = 15.sp", "contentColor = BodyText")) assertTrue("missing black action token $token", messageAction.contains(token))
        assertFalse(messagePopup.contains("ModalBottomSheet"))
        assertFalse(messageAction.contains("AccentOrange"))
        for (forbidden in listOf("\"模型未知\"", "durationLabel", "origin.label", "Text(\"你\"")) assertFalse("unexpected $forbidden", source.contains(forbidden))
    }

    @Test
    fun `FB-P6-034 keeps USER actions long press only while assistant has a visible external action row`() {
        val messageBubble = source.substring(source.indexOf("private fun MessageBubble"), source.indexOf("private fun MessageContextAction"))
        for (token in listOf("AssistantMessageActionRow", "onCopyAssistant", "onShareAssistant", "onBranchAssistant", "Icons.Outlined.ContentCopy", "Icons.Outlined.Share", "Icons.AutoMirrored.Outlined.DriveFileMove", "formatTranscriptTimeOrNull")) assertTrue("missing $token", messageBubble.contains(token))
        val assistantBranch = messageBubble.substring(messageBubble.indexOf("MessageRole.ASSISTANT"), messageBubble.indexOf("else -> Surface"))
        assertFalse(assistantBranch.contains("combinedClickable"))
        assertTrue(messageBubble.contains("MessageRole.USER -> Column"))
        assertTrue(messageBubble.contains("onAnchorChanged = { messageBounds = it }"))
        assertTrue(messageBubble.contains("SelectionContainer {"))
        val userBranch = messageBubble.substring(messageBubble.indexOf("MessageRole.USER"), messageBubble.indexOf("MessageRole.ASSISTANT"))
        assertTrue(userBranch.contains("onLongPress = { pressPosition -> messageBounds?.let"))
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
    fun `FB-P6-045 user text bubble is content driven with only a responsive maximum`() {
        val messageBubble = source.substring(source.indexOf("private fun MessageBubble"), source.indexOf("private fun MessageContextAction"))
        val userBranch = messageBubble.substring(messageBubble.indexOf("MessageRole.USER"), messageBubble.indexOf("MessageRole.ASSISTANT"))
        val sharedBubble = source.substring(source.indexOf("private fun RightAlignedUserBubble"), source.indexOf("private fun AssistantMessageActionRow"))
        assertTrue(userBranch.contains("RightAlignedUserBubble(surfaceColor = roleVisual.surface"))
        assertTrue(sharedBubble.contains("BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopEnd)"))
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
        assertTrue(create.contains("createConversation.execute(surface = state.surface)"))
        assertFalse(create.contains("appendMessage.execute"))
        assertFalse(create.contains("确定性 fixture"))
    }

    @Test
    fun `FB-P6-091 Android drawer search opens a compact full screen search destination`() {
        val drawer = source.substring(source.indexOf("private fun ConversationNavigationDrawer"), source.indexOf("internal fun ConversationManagementSettingsCard"))
        val page = source.substring(source.indexOf("private fun ConversationSearchPage"), source.indexOf("private fun SearchAllOrTextResults"))
        assertTrue(drawer.contains(".height(38.dp)"))
        assertTrue(drawer.contains("onOpenSearchPage"))
        assertFalse(drawer.contains("最近搜索"))
        assertTrue(page.contains("DialogProperties(usePlatformDefaultWidth = false"))
        assertTrue(page.contains("ConversationSearchCategory.entries"))
        assertTrue(page.contains("maxWidth >= 600.dp") && page.contains("Alignment.Center"))
        assertTrue(page.contains("expandedSearchCategories") && page.contains("Modifier.weight(1f)"))
        assertFalse(page.contains("horizontalScroll(rememberScrollState())"))
        assertTrue(page.contains("Modifier.weight(1f).height(42.dp)"))
        assertTrue(page.contains("BasicTextField("))
        assertTrue(page.contains("Icons.Outlined.Search"))
        assertTrue(page.contains("Modifier.fillMaxSize().padding(start = 10.dp, end = 8.dp)"))
        assertTrue(page.contains("horizontalArrangement = Arrangement.spacedBy(7.dp)"))
        assertTrue(page.contains("Modifier.width(76.dp).height(42.dp).combinedClickable(onClick = onOpenHistory)"))
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
    }

    @Test
    fun `Android conversation drawer is a continuous pure white surface`() {
        assertTrue(source.contains("ModalDrawerSheet(drawerContainerColor = Color.White, modifier = Modifier.requiredWidth(drawerWidth))"))
        assertTrue(source.contains("LaunchedEffect(drawerOpen)"))
        assertTrue(source.contains("snapshotFlow { drawerState.currentValue }"))
        assertTrue(source.contains(".drop(1)"))
        assertTrue(source.contains("if (drawerState.isOpen) BackHandler { drawerScope.launch { drawerState.close() } }"))
    }

    @Test
    fun `FB-P6-051 drawer keeps independent left settings and right new conversation actions`() {
        val drawer = source.substring(source.indexOf("private fun ConversationNavigationDrawer"), source.indexOf("internal fun ConversationManagementSettingsCard"))
        assertTrue(drawer.contains("onClick = onCreate"))
        assertTrue(drawer.contains("IconButton(onClick = { onOpenRoute(P5ARoute.SETTINGS) }, modifier = Modifier.size(48.dp))"))
        assertTrue(drawer.contains("modifier = Modifier.align(Alignment.BottomCenter)"))
        assertTrue(drawer.contains("horizontalArrangement = Arrangement.SpaceBetween"))
        assertTrue(drawer.contains("ButtonDefaults.buttonElevation(defaultElevation = 6.dp, pressedElevation = 2.dp)"))
        assertTrue(drawer.contains("Button(\n            onClick = onCreate,\n            shape = CircleShape"))
        assertTrue(drawer.contains("shape = CircleShape"))
        assertTrue(drawer.contains("R.drawable.ic_lucide_file_pen"))
        assertTrue(drawer.contains("contentDescription = null, modifier = Modifier.size(18.dp)"))
        assertTrue(drawer.contains("Text(\"新对话\")"))
        assertFalse(drawer.contains("Text(\"新建本地对话\")"))
        assertFalse(drawer.contains("Icons.Outlined.Add"))
        val glyph = File("src/main/res/drawable/ic_lucide_file_pen.xml").readText()
        assertTrue(glyph.contains("18.375,2.625"))
        assertTrue(glyph.contains("strokeLineJoin=\"round\""))
    }

    @Test
    fun `FB-P6-040 Android keeps the desktop-like model entry immediately left of send`() {
        val composer = source.substring(source.indexOf("private fun DraftComposer"), source.indexOf("private fun ComposerMenuOverlay"))
        assertTrue(composer.contains("val modelLabel ="))
        val modelEntry = source.substring(source.indexOf("private fun ComposerModelEntry"), source.indexOf("private fun ConversationComposerDock"))
        assertTrue(modelEntry.contains("modifier = Modifier.width(64.dp).height(48.dp)"))
        assertTrue(modelEntry.contains("interactionSource.collectIsPressedAsState()"))
        assertTrue(modelEntry.contains("Text(label, fontSize = 15.sp"))
        assertTrue(composer.contains("ConversationComposerDock("))
        assertTrue(composer.indexOf("ComposerModelEntry(") < composer.indexOf("ComposerSendButton"))
    }

    @Test
    fun `FB-P6-052 Android conversation and work use one selected segmented pill`() {
        assertTrue(source.contains("ConversationModeSwitch("))
        assertTrue(source.contains("val workMode = state.surface == com.nanzhufeng.ai.domain.ConversationSurface.WORK"))
        assertTrue(source.contains("onModeChanged = { enabled -> onSurfaceChanged"))
        val modeSwitch = source.substring(source.indexOf("private fun ConversationModeSwitch"), source.indexOf("private fun ConversationModeSegment"))
        assertTrue(modeSwitch.contains("color = Color(0xFFF1F3F1)"))
        assertTrue(modeSwitch.contains("shape = RoundedCornerShape(50)"))
        assertTrue(modeSwitch.contains("shadowElevation = 3.dp"))
        assertTrue(modeSwitch.contains("Modifier.height(44.dp).padding(3.dp)"))
        assertTrue(source.contains("containerColor = if (selected) Color.White else Color.Transparent"))
        assertTrue(source.contains("contentColor = if (selected) BodyText else SecondaryText"))
        assertTrue(source.contains("ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp)"))
    }

    @Test
    fun `FB-P6-099 keeps all three conversation header controls as independent floating surfaces`() {
        val shell = source.substring(source.indexOf("private fun ConversationShellHeader"), source.indexOf("private fun ConversationModeSwitch"))
        val floatingButton = source.substring(source.indexOf("private fun ConversationHeaderFloatingIconButton"), source.indexOf("private fun ConversationModeSwitch"))
        assertTrue(shell.contains("ConversationHeaderFloatingIconButton(onClick = onLeftAction)"))
        assertTrue(shell.contains("ConversationHeaderFloatingIconButton(onClick = onTemporaryAction)"))
        assertTrue(floatingButton.contains("color = Color.White"))
        assertTrue(floatingButton.contains("shadowElevation = 3.dp"))
        assertTrue(source.contains("modifier = Modifier.align(Alignment.TopCenter).padding(horizontal = 18.dp, vertical = 18.dp)"))
        assertTrue(source.contains("The header occupies no layout row or reserved top band"))
    }

    @Test
    fun `FB-P6-053 removes redundant conversation identity and local provider status from the chat canvas`() {
        val canvas = source.substring(source.indexOf("else Box(Modifier.fillMaxSize())"), source.indexOf("if (choosingProject)"))
        assertTrue(canvas.contains("ConversationShellHeader("))
        assertFalse(canvas.contains("Text(\"本地对话\""))
        assertFalse(canvas.contains("Text(\"本机本地 · 不联网\""))
        assertFalse(canvas.contains("state.notice?.let"))
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
    fun `transcript position rail and scrollbar share the transcript scroll position`() {
        assertTrue(source.contains("val showInnerTranscriptRail = windowWidth >= 600.dp"))
        val rail = source.substring(source.indexOf("private fun TranscriptPositionRail"), source.indexOf("private fun presentedMessagePlainText"))
        assertTrue(rail.contains("presentedMessagePlainText(it.message)"))
        assertTrue(rail.contains("listState: LazyListState"))
        assertTrue(rail.contains("transcriptScrollMetrics(listState)"))
        assertTrue(rail.contains("previewSlot ?: activeSlot"))
        assertTrue(source.contains("private fun TranscriptScrollIndicator(listState: LazyListState"))
        assertTrue(source.contains("firstVisibleItemScrollOffset"))
        assertTrue(source.contains("val nominalViewportItems = minOf(4, totalItems)"))
        assertTrue(source.contains("A thumb that changes length with every unusually tall attachment"))
        assertTrue(source.contains("animateFloatAsState("))
        assertTrue(source.contains("offset(x = 12.dp)"))
        assertTrue(source.contains("padding(end = 14.dp)"))
        assertTrue(rail.contains("collectIsHoveredAsState()"))
        assertTrue(rail.contains("collectIsFocusedAsState()"))
        assertTrue(rail.contains("val positionLabel = \"跳转到对话位置 \${slot + 1}/\$slots\""))
        assertTrue(rail.contains("contentDescription = positionLabel"))
        assertTrue(rail.contains(".combinedClickable("))
        assertFalse(rail.contains("SemanticsProperties.Role]"))
        assertTrue(rail.contains("modifier = modifier.width(if (previewSlot == null) 28.dp else 364.dp)"))
        assertTrue(rail.contains("modifier = Modifier.padding(start = 4.dp)"))
        assertTrue(rail.contains("0 -> 20.dp"))
        assertTrue(rail.contains("1 -> 15.dp"))
        assertTrue(rail.contains("onSelect(index)"))
        assertFalse(rail.contains("effectivePreviewIndex"))
        assertFalse(rail.contains("SharedPreferences"))
        assertFalse(rail.contains("DataStore"))
    }


    @Test
    fun `expanded inner display gives the navigation drawer two thirds of available width`() {
        assertTrue(source.contains("val windowWidth = with(density) { LocalWindowInfo.current.containerSize.width.toDp() }"))
        assertTrue(source.contains("val drawerWidth = if (windowWidth >= 600.dp) windowWidth * (2f / 3f) else 320.dp"))
        assertTrue(source.contains("Modifier.requiredWidth(drawerWidth)"))
    }

    @Test
    fun `search history is never retained on the normal conversation canvas`() {
        assertTrue(source.contains("if (!searchPageVisible && state.searchHistoryOpen) onCloseSearchHistory()"))
        assertTrue(source.contains("if (searchPageVisible && state.searchHistoryOpen) BackHandler"))
        assertTrue(source.contains("onDismiss = {\n                searchPageVisible = false\n                onDrawerOpenChanged(true)\n                drawerScope.launch { drawerState.open() }"))
        assertFalse(source.contains("transcriptRailPreviewIndex"))
    }

    @Test
    fun `FB-P6-049 composer has the exact empty hint without changing its semantic label`() {
        val composer = source.substring(source.indexOf("private fun ComposerDraftTextField"), source.indexOf("private fun AttachmentPreviewChip"))
        assertTrue(composer.contains("inputDescription: String = \"会话草稿\""))
        assertTrue(composer.contains("semantics { contentDescription = inputDescription }"))
        assertTrue(composer.contains("Text(\"回复 南枫AI\""))
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
        for (token in listOf("val isImage = mimeType.startsWith(\"image/\")", "val isPdf = mimeType == \"application/pdf\"", "val isVideo = mimeType == \"video/mp4\"", "val isAudio = mimeType in", "val isText = mimeType in", "isVideo -> Modifier.widthIn(min = 144.dp, max = 220.dp).height(128.dp)", "isImage -> originalAspectPreviewModifier(bitmap, maxEdge = 220.dp, fallbackSize = 92.dp)", "isAudio -> Modifier.size(width = 176.dp, height = 42.dp)", "else -> Modifier.size(92.dp)", "contentScale = if (isVideo) ContentScale.Crop else ContentScale.Fit")) assertTrue("missing $token", attachment.contains(token))
        assertFalse(attachment.contains("Surface(color = AccentOrangeSoft"))
        assertFalse(attachment.contains("Modifier.size(72.dp)"))
    }

    @Test
    fun `navigation titles are compact while section labels remain visually secondary`() {
        for (token in listOf("fontSize = 12.sp", "lineHeight = 16.sp", "fontWeight = FontWeight.Medium", "overflow = TextOverflow.Ellipsis", "Text(\"置顶\", style = MaterialTheme.typography.labelSmall", "Text(\"最近\", style = MaterialTheme.typography.labelSmall")) {
            assertTrue("missing $token", source.contains(token))
        }
        assertFalse(source.contains("conversation.title, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyLarge"))
    }

    @Test
    fun `drawer heading uses the packaged app icon at compact title scale`() {
        val drawer = source.substring(source.indexOf("private fun ConversationNavigationDrawer"), source.indexOf("private fun ConversationNavigationRow"))
        assertTrue(drawer.contains("painterResource(R.drawable.nanfeng_ai_icon_foreground_image)"))
        assertTrue(drawer.contains("Modifier.size(28.dp).clip(RoundedCornerShape(8.dp))"))
        assertTrue(drawer.contains("Text(\"南枫 AI\""))
    }

    @Test
    fun `composer uses typed local runtime stop only and keeps send icon accessible`() {
        for (token in listOf("val canStopRuntime = state.runtime?.isTerminal == false", "ComposerSendButton(onClick = if (canStopRuntime) onStop else onSubmit", "private val ComposerSendSurfaceSize = 36.dp", "private val ComposerSendGlyphSize = 18.dp", "private val ComposerStopGlyphSize = 22.5.dp", "colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, disabledContainerColor = Color.Transparent)", "Modifier.size(ComposerSendSurfaceSize)", "Icon(Icons.Outlined.Stop, contentDescription = \"停止生成\", tint = glyphColor, modifier = Modifier.size(ComposerStopGlyphSize))", "Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = contentDescription, tint = glyphColor, modifier = Modifier.size(ComposerSendGlyphSize).graphicsLayer(rotationZ = -90f))", "CircularProgressIndicator")) {
            assertTrue("missing $token", source.contains(token))
        }
        assertFalse(source.contains("Text(\"本地发送\")"))
    }

    @Test
    fun `FB-P6-055 mobile composer uses one floating pill rather than separate narrow screen controls`() {
        val composerSource = source.substring(source.indexOf("private fun ComposerDraftTextField"), source.indexOf("private fun AttachmentPreviewChip"))
        val composer = source.substring(source.indexOf("private fun DraftComposer"), source.indexOf("private fun ComposerMenuOverlay"))
        for (token in listOf("Surface(", "shape = RoundedCornerShape(28.dp)", "shadowElevation = 10.dp", "Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 6.dp, vertical = 4.dp)", "IconButton(", "ComposerDraftTextField", "ComposerSendButton")) {
            assertTrue("missing $token", composer.contains(token))
        }
        for (token in listOf("private fun ComposerDraftTextField", "BasicTextField(", "minLines = 1", "maxLines = 5", "Text(\"回复 南枫AI\"")) {
            assertTrue("missing $token", composerSource.contains(token))
        }
        assertFalse(composerSource.contains("OutlinedTextFieldDefaults.Container("))
        assertTrue(source.contains("Text(label, fontSize = 15.sp"))
        assertFalse(composer.contains("ComposerModelSelectorHitWidth"))
    }

    @Test
    fun `FB-P6-093 composer and drawer actions are isolated floating surfaces without a painted bottom dock`() {
        assertTrue(source.contains("val showFloatingComposer = state.draft != null && !state.searchPanelOpen"))
        assertTrue(source.contains("The composer is a true overlay"))
        assertTrue(source.contains("contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 86.dp)"))
        assertTrue(source.contains("val jumpToLatestBottomPadding = floatingComposerHeight + 4.dp"))
        assertTrue(source.contains("floatingComposerHeight = with(density) { coordinates.size.height.toDp() }"))
        assertTrue(source.contains("padding(bottom = jumpToLatestBottomPadding)"))
        assertTrue(source.contains("ModalDrawerSheet(drawerContainerColor = Color.White"))
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
        assertTrue(composer.contains("TextButton("))
    }

    @Test
    fun `FB-P6-056 keeps the button anchored menus minimal, stable and visually truthful`() {
        val composer = source.substring(source.indexOf("private fun DraftComposer"), source.indexOf("private fun ComposerMenuOverlay"))
        val overlay = source.substring(source.indexOf("private fun ComposerMenuOverlay"), source.indexOf("private fun ComposerDraftTextField"))
        for (token in listOf(
            "onGloballyPositioned", "onToggleAttachments", "onToggleModel", "boundsInRoot()",
        )) assertTrue("missing $token", composer.contains(token))
        for (token in listOf(
            "Box(Modifier.fillMaxSize())", "pointerInput(menu)", "detectTapGestures(onTap = { onDismiss() })",
            "anchor.top - heightPx - verticalGap", "attachmentActions.forEach", "ComposerOverlayAction",
            "contentColor = SecondaryText", "contentColor = if (id == selectedModelId) AccentOrange else SecondaryText",
            "Icons.Outlined.Check", "TextAlign.Center", "Modifier.align(Alignment.Center)", "Modifier.align(Alignment.CenterEnd)", "shadowElevation = 10.dp",
        )) assertTrue("missing $token", overlay.contains(token))
        for (forbidden in listOf("Popup(", "PopupProperties", "addSheetVisible", "modelPickerVisible", "当前会话模型", "手动选择只影响当前普通会话", "最近路由")) assertFalse("unexpected $forbidden", overlay.contains(forbidden))
        for (token in listOf("Icons.Outlined.PhotoCamera", "\"相机\"", "Icons.Outlined.AddPhotoAlternate", "\"添加图片\"", "Icons.Outlined.AttachFile", "\"添加文件\"")) assertTrue("missing $token", source.contains(token))
    }

    @Test
    fun `temporary Ghost directly changes only the visible mode and preserves the recovery owner`() {
        val viewModel = File("src/main/java/com/nanzhufeng/ai/ui/ConversationFoundationViewModel.kt").readText()
        for (token in listOf("fun leaveTemporaryConversation()", "temporaryRecovery = null", "attachmentPreviews = state.attachmentPreviews - temporaryPreviewIds", "已返回普通聊天；临时内容仍可在 24 小时内继续。", "temporary.readRecovery()")) assertTrue("missing $token", viewModel.contains(token))
        for (forbidden in listOf("fun exitTemporaryConversation()", "clearTemporary.execute()")) assertFalse("unexpected $forbidden", viewModel.contains(forbidden))
        val normalHeaderStart = source.indexOf("else Box(Modifier.fillMaxSize())")
        val normalHeader = source.substring(normalHeaderStart, source.indexOf("ComposerMenuOverlay(", normalHeaderStart))
        val temporaryPane = source.substring(source.indexOf("private fun TemporaryConversationPane"))
        assertTrue(normalHeader.contains("temporaryTint = SecondaryText"))
        assertTrue(temporaryPane.contains("temporaryTint = AccentOrange"))
        assertTrue(temporaryPane.contains("onLeftAction = onOpenNavigation"))
        assertTrue(temporaryPane.contains("leftIcon = Icons.Outlined.Menu"))
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
        for (token in listOf("RightAlignedUserBubble {", "Icons.Outlined.PhotoCamera", "\"相机\"", "Icons.Outlined.AttachFile", "contentDescription = \"附件\"", "modelOptions: List<Pair<String?, String>>")) assertTrue("missing $token", temporary.contains(token))
        for (forbidden in listOf("个临时本地附件", "草稿含", "recovery.modelOverrideId ?: \"临时\"", "开始临时对话", "临时草稿", "发送临时消息")) assertFalse("unexpected $forbidden", temporary.contains(forbidden))
    }

    @Test fun `temporary draft images use the shared in-composer preview and remove owner`() {
        val temporary = source.substring(source.indexOf("private fun TemporaryConversationPane"))
        val viewModel = File("src/main/java/com/nanzhufeng/ai/ui/ConversationFoundationViewModel.kt").readText()
        for (token in listOf("attachmentPreview = if (recovery.draftAttachmentIds.isNotEmpty())", "ComposerAttachmentPreview(")) assertTrue("missing $token", temporary.contains(token))
        assertTrue(source.contains("onRemoveAttachment = onRemoveTemporaryDraftAttachment"))
        for (token in listOf("fun removeTemporaryDraftAttachment", "temporary.removeDraftAttachment(id)", "temporaryAttachmentReferences")) assertTrue("missing $token", viewModel.contains(token))
    }

    @Test fun `temporary camera dispatches through the temporary private attachment owner`() {
        val app = File("src/main/java/com/nanzhufeng/ai/ui/NanfengAiApp.kt").readText()
        val owner = File("src/main/java/com/nanzhufeng/ai/ui/ConversationFoundationViewModel.kt").readText()
        for (token in listOf("temporaryCamera", "onAddTemporaryCamera", "temporaryCamera.launch(null)", "onTemporaryCameraResult")) assertTrue("missing $token", "$source\n$app\n$owner".contains(token))
        for (token in listOf("addTemporaryAttachment.add(", "mimeType = \"image/jpeg\"", "相机图片已加入临时会话")) assertTrue("missing $token", owner.contains(token))
        assertFalse(owner.substring(owner.indexOf("fun onTemporaryCameraResult"), owner.indexOf("fun onTemporaryDocumentPickerResult")).contains("addAttachment.execute("))
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
        assertTrue(renameBackdrop.contains("Modifier.fillMaxSize().pointerInput(onDismiss)"))
        assertFalse(source.contains("onDismissRequest = {}"))
    }

    @Test
    fun `mobile chat canvas keeps the composer outside navigation and management stacks`() {
        assertTrue(source.contains("The drawer owns navigation and management"))
        assertTrue(source.contains("ConversationNavigationDrawer("))
        assertTrue(source.contains("onExport = { drawerScope.launch { drawerState.close() }; onExport() }"))
        assertTrue(source.contains("Text(\"导出当前对话\")"))
    }

    @Test
    fun `archive and recycle are settings lifecycle projections rather than drawer scopes`() {
        val appSource = File("src/main/java/com/nanzhufeng/ai/ui/NanfengAiApp.kt").readText()
        for (token in listOf("internal fun ConversationManagementSettingsCard", "Text(\"管理对话\"", "DropdownMenu(", "Text(\"查看：\$selectedScope\"", "ConversationListScope.ARCHIVED", "ConversationListScope.DELETED", "ConversationManagementAction.RESTORE_DELETED", "ConversationManagementAction.UNARCHIVE")) {
            assertTrue("missing $token", source.contains(token))
        }
        assertTrue(appSource.contains("ConversationManagementSettingsCard("))
        assertFalse(source.contains("Text(\"归档\") }\n            OutlinedButton(onClick = { onScope(ConversationListScope.DELETED)"))
        assertTrue(source.contains("label = { Text(\"搜索\") }"))
    }

    @Test
    fun `settings route starts with categories and opens dense controls only in a detail page`() {
        val appSource = File("src/main/java/com/nanzhufeng/ai/ui/NanfengAiApp.kt").readText()
        val hierarchy = appSource.substring(appSource.indexOf("private fun SettingsHierarchy"), appSource.indexOf("private fun WorkbenchRoute"))
        val categoryList = appSource.substring(appSource.indexOf("private fun SettingsCategoryList"), appSource.indexOf("private fun SettingsCategoryRow"))
        for (title in listOf("AI 模型", "对话", "数据与导入", "隐私")) assertTrue("missing category $title", categoryList.contains(title))
        assertTrue(appSource.contains("val returnFromSettings = {"))
        assertTrue(appSource.contains("conversationViewModel.setListScope(com.nanzhufeng.ai.domain.ConversationListScope.ACTIVE)"))
        assertTrue(appSource.contains("onReturnToConversationDrawer()"))
        assertTrue(appSource.contains("else settingsDestination = SettingsDestination.HOME"))
        assertTrue(appSource.contains("BackHandler(onBack = returnFromSettings)"))
        assertTrue(appSource.contains("Modifier.systemGestureExclusion()"))
        assertTrue(appSource.contains("Modifier.settingsEdgeExit(returnFromSettings)"))
        assertTrue(appSource.contains("contentDescription = if (destination == SettingsDestination.HOME) \"返回侧栏\" else \"返回设置\""))
        assertTrue(hierarchy.contains("if (destination == SettingsDestination.HOME)"))
        for (token in listOf("ModelServiceStatusCard", "ConversationManagementSettingsCard", "PrivacyDataCard", "onOpenImport")) assertTrue("missing detail owner $token", hierarchy.contains(token))
        assertFalse(categoryList.contains("ConversationManagementSettingsCard"))
        assertFalse(categoryList.contains("P6GModelSelectionSettingsCard"))
        for (token in listOf("InvocationLedgerCard", "P6GModelSelectionSettingsCard", "P6ETemporaryMaintenanceAcceptanceCard", "installP6GLocalFixtureCatalog")) assertFalse("normal settings must hide $token", hierarchy.contains(token))
    }

    @Test
    fun `conversation surfaces consume the shared orange accent token`() {
        val appSource = File("src/main/java/com/nanzhufeng/ai/ui/NanfengAiApp.kt").readText()
        for (token in listOf("internal val AccentOrange = Color(0xFFE97128)", "internal val AccentOrangeHover = Color(0xFFD86520)", "internal val AccentOrangePressed = Color(0xFFC2581A)", "internal val AccentOrangeSoft = Color(0xFFFFF1E5)", "internal val ComposerFocusBorder = Color(0xFFEFBD94)", "internal val AccentDisabled = Color(0xFFF4C8AA)", "primary = AccentOrange", "onPrimary = AccentOnPrimary", "primaryContainer = AccentOrangeSoft", "internal val BrandGreen = Color(0xFF167A61)")) assertTrue("missing $token", appSource.contains(token))
        assertTrue(source.contains("if (selected) AccentOrangeSoft else Color.Transparent"))
        val row = source.substring(source.indexOf("private fun ConversationNavigationRow"), source.indexOf("private data class ConversationActionMenuTarget"))
        assertTrue(row.contains("contentColor = BodyText"))
    }

    @Test
    fun `FB-P6-080 keeps warm user bubbles and identifies quotes without a colored slab`() {
        val appSource = File("src/main/java/com/nanzhufeng/ai/ui/NanfengAiApp.kt").readText()
        val attachment = source.substring(source.indexOf("private fun AttachmentPreviewChip"), source.indexOf("private fun PdfPreviewDialog"))
        val composerAttachment = source.substring(source.indexOf("private fun ComposerAttachmentPreview"), source.indexOf("private fun ComposerMenuOverlay"))
        val presentation = source.substring(source.indexOf("private fun PresentationBlockView"), source.indexOf("private fun inlineText"))
        for (token in listOf("internal fun chatRoleVisual", "MessageRole.USER -> ChatRoleVisual(AccentOrangeSoft", "MessageRole.ASSISTANT -> ChatRoleVisual(NeutralAssistantSurface", "MessageRole.SYSTEM -> ChatRoleVisual(NeutralSystemSurface", "MessageRole.TOOL -> ChatRoleVisual(ToolSurface")) assertTrue("missing $token", appSource.contains(token))
        assertFalse(attachment.contains("AccentOrangeSoft"))
        assertFalse(composerAttachment.contains("AccentOrangeSoft"))
        assertTrue(composerAttachment.contains("background(Color.White)"))
        for (token in listOf("is PresentationBlock.Quote -> Row", "height(IntrinsicSize.Min)", "Box(Modifier.width(2.dp).fillMaxHeight()", "fontStyle = androidx.compose.ui.text.font.FontStyle.Italic")) assertTrue("missing quote format token $token", presentation.contains(token))
        assertFalse(presentation.contains("background(Color(0xFFE8ECE9))"))
        MessageRole.entries.forEach { role -> assertTrue("${role.name} body contrast", contrast(chatRoleVisual(role).surface, chatRoleVisual(role).body) >= 4.5) }
    }

    @Test
    fun `FB-P6-081 makes links directly clickable with a reinforced color and no underline`() {
        val inline = source.substring(source.indexOf("private fun inlineText"), source.indexOf("private fun ConversationActions"))
        for (token in listOf("withLink(", "LinkAnnotation.Url(", "url = span.url", "TextLinkStyles(style = SpanStyle(color = BrandGreen, fontWeight = FontWeight.Medium))")) assertTrue("missing clickable link token $token", inline.contains(token))
        assertFalse(inline.contains("TextDecoration.Underline"))
    }

    @Test
    fun `FB-P6-089 gives assistant information blocks local hierarchy tables and individual copy actions`() {
        val presentation = source.substring(source.indexOf("private fun PresentationBlockView"), source.indexOf("private fun inlineText"))
        for (token in listOf("is PresentationBlock.Table ->", "CopyableInformationSurface", "MarkdownTable(block)", "Icon(Icons.Outlined.ContentCopy", "contentDescription = \"复制\$label\"", "Color(0xFFF5F6F5)", "horizontalScroll(scrollState)")) assertTrue("missing formatted assistant content token $token", presentation.contains(token))
        val inline = source.substring(source.indexOf("private fun inlineText"), source.indexOf("private fun ConversationActions"))
        assertTrue(inline.contains("append(\"\${span.label} ↗\")"))
    }

    @Test
    fun `FB-P6-082 keeps the edit branch dialog to its title editor and actions`() {
        val editDialog = source.substring(source.indexOf("editingMessageId?.let"), source.indexOf("state.imagePreview?.let"))
        for (token in listOf("title = { Text(\"编辑\") }", "OutlinedTextField(editingText", "minLines = 3", "Modifier.width(76.dp).height(32.dp)", "Modifier.width(44.dp).height(30.dp)", "Text(\"创建分支\", fontSize = 13.sp)", "Text(\"取消\", fontSize = 13.sp)")) assertTrue("missing compact edit token $token", editDialog.contains(token))
        for (removed in listOf("编辑消息并创建分支", "确认后只在本机创建修订分支", "用户消息", "仅纯文本消息可编辑")) assertFalse("unexpected edit-dialog detail $removed", editDialog.contains(removed))
    }

    @Test
    fun `FB-P6-090 launches the platform share sheet immediately without an in-app confirmation dialog`() {
        assertTrue(source.contains("val shareMessage: (PresentedTranscriptMessage) -> Unit"))
        assertTrue(source.contains("context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND)"))
        assertTrue(source.contains("MessageContextAction(Icons.Outlined.Share, \"分享\")"))
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
        assertTrue(attachmentPopup.contains("AttachmentPopupAction(Icons.Outlined.FileDownload, \"下载\""))
        assertTrue(attachmentPopup.contains("AttachmentPopupAction(Icons.Outlined.Share, \"分享\""))
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
        assertTrue(source.contains("remuxDownloadedMp4(context, resolver, uri, item.bytes)"))
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

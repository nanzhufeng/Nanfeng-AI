package com.nanzhufeng.ai.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class P6GUnifiedChatFirstUiContractsTest {
    private val workspace = File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()

    @Test
    fun `work projects the selected conversation rather than a module dashboard`() {
        for (token in listOf("if (state.surface == com.nanzhufeng.ai.domain.ConversationSurface.WORK)", "ConversationWorkScope(", "state.messages", "MessageBubble(", "right alignment and responsive reading width", "sentFromMessageCount = workSentFromMessageCount", "workSentFromMessageCount = state.messages.size")) {
            assertTrue("missing $token", workspace.contains(token))
        }
        val workScope = workspace.substring(workspace.indexOf("private fun ConversationWorkScope"), workspace.indexOf("private fun ConversationAttemptHistory"))
        for (token in listOf("listState: LazyListState", "followLatest: Boolean", "onSentToLatestConsumed()")) assertTrue("missing $token", workScope.contains(token))
        for (token in listOf("showJumpToLatest", "contentDescription = \"到最新消息\"")) assertTrue("missing $token", workspace.contains(token))
        assertFalse(workScope.contains("widthIn(max = 680.dp)"))
        assertFalse(workScope.contains("work-project-context"))
        assertFalse(workspace.contains("Text(\"项目\")\n                    Text(\"知识\")\n                    Text(\"记忆\")"))
    }

    @Test
    fun `normal work and temporary conversations share the same right aligned user bubble geometry`() {
        val sharedBubble = workspace.substring(workspace.indexOf("private fun RightAlignedUserBubble"), workspace.indexOf("private fun AssistantMessageActionRow"))
        assertTrue(sharedBubble.contains(".widthIn(max = maxWidth * 0.82f)"))
        assertTrue(sharedBubble.contains(".wrapContentWidth(Alignment.End)"))
        assertTrue(workspace.contains("RightAlignedUserBubble(surfaceColor = roleVisual.surface"))
        val temporaryPane = workspace.substring(workspace.indexOf("private fun TemporaryConversationPane"))
        assertTrue(temporaryPane.contains("RightAlignedUserBubble {"))
        assertFalse(temporaryPane.contains("widthIn(max = 680.dp)"))
    }

    @Test
    fun `public logical model slots keep only daily and deep while selection is persisted per conversation`() {
        for (token in listOf("ComposerModelSlot.DAILY", "ComposerModelSlot.DEEP", "onSelectP6GModel(modelId)")) {
            assertTrue("missing $token", workspace.contains(token))
        }
        val publicSlotsStart = workspace.indexOf("listOf(\n                        com.nanzhufeng.ai.domain.ComposerModelSlot.DAILY")
        val publicSlots = workspace.substring(publicSlotsStart, workspace.indexOf(").forEach { slot ->", publicSlotsStart))
        assertFalse(publicSlots.contains("ComposerModelSlot.COMPARE"))
        assertFalse(publicSlots.contains("ComposerModelSlot.MULTIMODAL"))
        val owner = File("src/main/java/com/nanzhufeng/ai/ui/ConversationFoundationViewModel.kt").readText()
        assertTrue(owner.contains("installP6GLocalFixtureCatalog"))
        assertTrue(owner.contains("P6GProviderFamily.LOCAL"))
        assertFalse(workspace.contains("ComposerCompareEntry("))
    }

    @Test
    fun `normal work and temporary panes retain the shared composer while first launch remains empty for controlled restore`() {
        for (token in listOf("TemporaryConversationPane(", "ConversationWorkScope(", "ConversationComposerDock(", "A first launch must remain an actually empty local truth", "“新对话” from the drawer")) {
            assertTrue("missing $token", workspace.contains(token))
        }
        assertFalse(workspace.contains("LaunchedEffect(state.conversations.isEmpty(), state.isCreating) { onCreate() }"))
        assertFalse(workspace.contains("还没有本地对话。创建后可验证草稿、Markdown 和安全错误恢复。"))
    }

    @Test
    fun `chat and work keep independent transcript positions across a surface switch`() {
        for (token in listOf(
            "val chatTranscriptListState = rememberLazyListState()",
            "val workTranscriptListState = rememberLazyListState()",
            "var chatFollowLatest by rememberSaveable",
            "var workFollowLatest by rememberSaveable",
            "listState = workTranscriptListState",
            "followLatest = workFollowLatest",
            "onFollowLatestChanged = { workFollowLatest = it }",
            "val listState = chatTranscriptListState",
            "if (atLatest) chatFollowLatest = true",
        )) assertTrue("missing $token", workspace.contains(token))
        val workScope = workspace.substring(workspace.indexOf("private fun ConversationWorkScope"), workspace.indexOf("private fun ConversationAttemptHistory"))
        assertFalse(workScope.contains("rememberLazyListState()"))
    }

    @Test
    fun `system long screenshot owns transcript scrolling until capture completes`() {
        assertTrue(workspace.contains("LocalScrollCaptureInProgress.current"))
        assertTrue(workspace.contains("val closedDrawerSemanticsModifier = if (drawerOpen)"))
        assertTrue(workspace.contains("hideFromAccessibility()"))
        assertTrue(workspace.contains("val transcriptScrollCaptureVisualModifier = if (systemScrollCaptureInProgress)"))
        assertTrue(workspace.contains(".then(transcriptScrollCaptureVisualModifier)"))
        assertTrue(workspace.contains("systemScrollCaptureInProgress = systemScrollCaptureInProgress"))
        val chatTranscript = workspace.substring(
            workspace.indexOf("val listState = chatTranscriptListState"),
            workspace.indexOf("TranscriptScrollIndicator("),
        )
        assertTrue(chatTranscript.contains("if (systemScrollCaptureInProgress) return@LaunchedEffect"))
        val workScope = workspace.substring(workspace.indexOf("private fun ConversationWorkScope"), workspace.indexOf("private fun ConversationAttemptHistory"))
        assertTrue(workScope.contains("systemScrollCaptureInProgress: Boolean"))
        assertTrue(workScope.contains("if (systemScrollCaptureInProgress) return@LaunchedEffect"))
    }

    @Test
    fun `surface swap commits the matching transcript atomically without a local mode mirror`() {
        assertTrue(workspace.contains("val workMode = state.surface == com.nanzhufeng.ai.domain.ConversationSurface.WORK"))
        assertFalse(workspace.contains("var workMode by rememberSaveable"))
        val owner = File("src/main/java/com/nanzhufeng/ai/ui/ConversationFoundationViewModel.kt").readText()
        val reload = owner.substring(owner.indexOf("fun reload("), owner.indexOf("fun openImagePreview"))
        val switch = owner.substring(owner.indexOf("fun selectSurface"), owner.indexOf("fun setListScope"))
        for (token in listOf(
            "targetSurface: ConversationSurface = state.surface",
            "requestedSurfaceGeneration: Long? = null",
            "if (reloadRequest != projectionGeneration",
            "surface = surface,",
            "reload(targetSurface = surface, selectedBefore = selectedBefore, requestedSurfaceGeneration = request)",
        )) assertTrue("missing $token", "$reload\n$switch".contains(token))
        assertFalse(switch.contains("state = state.copy(surface = surface"))
    }

    @Test
    fun `chat work and temporary navigation stay on distinct persisted owners`() {
        val owner = File("src/main/java/com/nanzhufeng/ai/ui/ConversationFoundationViewModel.kt").readText()
        val reload = owner.substring(owner.indexOf("fun reload("), owner.indexOf("fun openImagePreview"))
        val select = owner.substring(owner.indexOf("fun selectConversation"), owner.indexOf("fun selectSurface"))
        val createWork = owner.substring(owner.indexOf("fun createWorkConversation"), owner.indexOf("fun updateDraft"))
        assertTrue(reload.contains("LoadedConversation(surfaceConversations"))
        assertTrue(select.contains("ConversationSurface.WORK -> selectedWorkConversationId = id"))
        assertFalse(select.contains("state = state.copy(surface = ConversationSurface.CHAT"))
        assertTrue(createWork.contains("createConversation.resumeOrCreateDraft(projectId = projectId.value, surface = ConversationSurface.WORK)"))

        assertTrue(workspace.contains("WorkProjectNavigationDrawer("))
        assertTrue(workspace.contains("TemporaryConversationNavigationDrawer("))
        assertTrue(workspace.contains("if (state.temporaryRecovery != null)"))
        assertTrue(workspace.contains("与普通对话、项目工作区独立"))

        val projectViewModel = File("src/main/java/com/nanzhufeng/ai/ui/ProjectViewModel.kt").readText()
        val projectWorkspace = File("src/main/java/com/nanzhufeng/ai/ui/ProjectWorkspace.kt").readText()
        assertTrue(projectViewModel.contains("fun showCreateDialog()"))
        assertTrue(projectViewModel.contains("createDialogVisible = true"))
        assertTrue(projectWorkspace.contains("if (state.createDialogVisible) AlertDialog"))
    }
}

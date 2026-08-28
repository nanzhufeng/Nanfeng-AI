package com.nanzhufeng.ai.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationSearchAttachmentPreviewUiContractsTest {
    @Test
    fun `search file tap previews locally and quick locate targets the exact attachment`() {
        val workspace = File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()
        val viewModel = File("src/main/java/com/nanzhufeng/ai/ui/ConversationFoundationViewModel.kt").readText()

        assertTrue(workspace.contains("onOpenAttachmentHit = { hit -> onOpenSearchAttachment(hit.attachment) }"))
        assertTrue(workspace.contains("query = state.searchQuery"))
        assertTrue(workspace.contains("private fun searchHighlightedText("))
        assertTrue(workspace.contains("background = highlightColor.copy(alpha = 0.18f)"))
        assertTrue(workspace.contains("fontWeight = FontWeight.Bold"))
        assertTrue(workspace.contains("hit.matchSnippet ?: textPreview?.text?.replace"))
        assertTrue(viewModel.contains("fun fillSearchHistory(query: String) {"))
        assertTrue(viewModel.contains("state = state.copy(searchQuery = query, searchHistoryOpen = false)\n        submitSearch()"))
        assertTrue(workspace.contains("SearchAttachmentActionMenuTarget(hit, anchorBounds)"))
        assertTrue(workspace.contains("Text(\"快速定位到对应对话\", style"))
        assertTrue(workspace.contains("private fun SearchAttachmentActionPopup("))
        assertTrue(workspace.contains("displayName = target.hit.attachment.displayName ?: \"本地附件\""))
        assertTrue(workspace.contains("onLocateSearchAttachment(hit)"))
        assertTrue(workspace.contains("DismissibleDialogBackdrop(onDismiss)"))
        assertTrue(workspace.contains("RoundedCornerShape(28.dp)"))
        assertTrue(workspace.contains(".widthIn(max = 360.dp)"))
        assertTrue(workspace.contains("shadowElevation = 8.dp"))
        assertTrue(workspace.contains("onLongClick = { anchorBounds?.let { onLocate(hit, it) } }"))
        assertTrue(workspace.contains(".onGloballyPositioned { anchorBounds = it.boundsInRoot() }"))
        assertTrue(workspace.contains("var returnToSearchAfterSearchOpen by rememberSaveable"))
        assertTrue(workspace.contains("returnToSearchAfterSearchOpen = true"))
        assertTrue(workspace.contains("Modifier.searchAttachmentReturnSwipe(onReturn = ::returnToSearchAfterSearchOpen)"))
        assertTrue(workspace.contains("onOpenTextHit = { hit ->\n                returnToSearchAfterSearchOpen = true"))
        assertTrue(workspace.contains("awaitEachGesture {"))
        assertTrue(workspace.contains("awaitFirstDown(requireUnconsumed = false)"))
        assertTrue(workspace.contains("val ScreenEdgeGestureWidth = 24.dp"))
        assertTrue(workspace.contains("down.position.x <= edgeWidthPx -> 1"))
        assertTrue(workspace.contains("down.position.x >= size.width - edgeWidthPx -> -1"))
        assertTrue(workspace.contains("horizontalDistancePx * direction >= returnThresholdPx"))
        assertTrue(workspace.contains("BackHandler(onBack = ::returnToSearchAfterSearchOpen)"))
        assertTrue(workspace.contains("searchPageVisible = true"))
        assertTrue(viewModel.contains("fun openSearchAttachment(reference: ConversationAttachmentReference)"))
        for (token in listOf(
            "val searchAnchorAttachmentId: AttachmentId? = null",
            "val searchAnchorRequestId: Long = 0L",
            "fun locateSearchAttachment(hit: ConversationAttachmentSearchHit)",
            "searchAnchorAttachmentId = hit.attachment.id",
            "searchAnchorRequestId = state.searchAnchorRequestId + 1L",
        )) assertTrue("missing exact search attachment anchor: $token", viewModel.contains(token))
        for (token in listOf(
            "private fun Modifier.searchAttachmentAnchorHighlight",
            "BringIntoViewRequester()",
            "requester.bringIntoView()",
            "Brush.linearGradient",
            "intensity.animateTo(0f",
            "searchAnchorAttachmentId?.takeIf(imageIds::contains)?.let { selectedIdValue = it.value }",
        )) assertTrue("missing transient themed attachment location cue: $token", workspace.contains(token))
        for (token in listOf("attachmentPreview.original(reference)", "loadPdfPage(reference", "attachmentPreview.video(reference)", "attachmentPreview.audio(reference)", "attachmentPreview.text(reference)")) {
            assertTrue("missing local preview route: $token", viewModel.contains(token))
        }
    }
}

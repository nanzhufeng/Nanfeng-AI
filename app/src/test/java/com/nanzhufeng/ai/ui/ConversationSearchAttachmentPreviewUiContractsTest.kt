package com.nanzhufeng.ai.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationSearchAttachmentPreviewUiContractsTest {
    @Test
    fun `search file tap previews locally and quick locate targets the exact attachment`() {
        val workspace = File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()
        val viewModel = File("src/main/java/com/nanzhufeng/ai/ui/ConversationFoundationViewModel.kt").readText()

        assertTrue(workspace.contains("onOpenAttachmentHit = { hit ->"))
        val quickLocate = workspace.substringAfter("onOpenConversation = {\n                val hit = target.hit").substringBefore("onRequestDelete = {")
        assertTrue(quickLocate.contains("searchReturnAttachmentId = null"))
        assertTrue(quickLocate.contains("onLocateSearchAttachment(hit)"))
        assertFalse(quickLocate.contains("searchReturnAttachmentId = hit.attachment.id.value"))
        assertTrue(workspace.contains("onOpenSearchAttachment(hit.attachment)"))
        assertTrue(workspace.contains("query = state.searchQuery"))
        assertTrue(workspace.contains("private fun searchHighlightedText("))
        assertTrue(workspace.contains("background = highlightColor.copy(alpha = 0.18f)"))
        assertTrue(workspace.contains("fontWeight = FontWeight.Bold"))
        assertTrue(workspace.contains("hit.matchSnippet ?: textPreview?.text?.replace"))
        assertTrue(viewModel.contains("fun fillSearchHistory(query: String) {"))
        assertTrue(viewModel.contains("state = state.copy(searchQuery = query, searchHistoryOpen = false, searchHistoryHighlightedQuery = null, searchHistoryManuallyOpened = false)"))
        assertTrue(viewModel.contains("executeSearch(recordHistory = true, closeHistoryOnComplete = true)"))
        assertTrue(workspace.contains("SearchAttachmentActionMenuTarget(hit, anchorBounds)"))
        assertTrue(workspace.contains("Text(\"快速定位\", style"))
        assertTrue(workspace.contains("title = { Text(\"删除\", style = MaterialTheme.typography.titleMedium"))
        assertTrue(workspace.contains("仅移除这条消息中的附件。"))
        assertFalse(workspace.contains("共享文件会保留到最后一个引用消失后再清理"))
        assertTrue(workspace.contains("onDeleteSearchAttachment(hit)"))
        assertTrue(workspace.contains("formatAttachmentBytes(hit.attachment.byteCount)"))
        assertTrue(workspace.countOccurrences("formatAttachmentBytes(hit.attachment.byteCount)") >= 2)
        assertTrue(workspace.countOccurrences("formatSearchAttachmentTimestamp(hit.timestampEpochMs)") >= 2)
        assertTrue(workspace.contains("private fun formatSearchAttachmentTimestamp(timestampEpochMs: Long)"))
        assertTrue(workspace.contains("formatTranscriptTimeOrNull(java.time.Instant.ofEpochMilli(timestampEpochMs)).orEmpty()"))
        val searchAttachmentCard = workspace.substring(
            workspace.indexOf("private fun SearchAttachmentCard("),
            workspace.indexOf("private fun SearchAttachmentRow("),
        )
        assertTrue(searchAttachmentCard.contains("Text(formatAttachmentBytes(hit.attachment.byteCount)"))
        assertTrue(searchAttachmentCard.contains("Spacer(Modifier.weight(1f))"))
        assertTrue(searchAttachmentCard.contains("Text(formatSearchAttachmentTimestamp(hit.timestampEpochMs)"))
        assertFalse(searchAttachmentCard.contains("hit.matchSnippet != null -> Text"))
        assertTrue(workspace.contains("private fun SearchAttachmentActionPopup("))
        assertTrue(workspace.contains("displayName = target.hit.attachment.displayName ?: \"本地附件\""))
        assertTrue(workspace.contains("onLocateSearchAttachment(hit)"))
        assertTrue(workspace.contains("DismissibleDialogBackdrop(onDismiss)"))
        assertTrue(workspace.contains("RoundedCornerShape(28.dp)"))
        assertTrue(workspace.contains(".widthIn(max = 360.dp)"))
        assertTrue(workspace.contains("shadowElevation = 8.dp"))
        val actionPopup = workspace.substring(
            workspace.indexOf("private fun SearchAttachmentActionPopup("),
            workspace.indexOf("private fun ConversationActionSheet("),
        )
        assertTrue(actionPopup.contains("TransientMenuTextScale"))
        assertTrue(actionPopup.contains("style = MaterialTheme.typography.titleMedium"))
        assertTrue(actionPopup.countOccurrences("style = MaterialTheme.typography.bodyLarge") == 2)
        assertTrue(actionPopup.contains("Text(\"删除\", style = MaterialTheme.typography.bodyLarge"))
        assertTrue(actionPopup.contains("Modifier.padding(24.dp)"))
        assertTrue(actionPopup.contains("Modifier.padding(horizontal = 18.dp, vertical = 19.dp)"))
        assertTrue(actionPopup.countOccurrences("Modifier.size(20.dp)") == 2)
        val deleteConfirmation = workspace.substring(
            workspace.indexOf("confirmingSearchAttachmentDelete?.let"),
            workspace.indexOf("// Register after ModalNavigationDrawer"),
        )
        assertTrue(deleteConfirmation.contains("containerColor = MaterialTheme.colorScheme.primary"))
        assertTrue(deleteConfirmation.contains("contentColor = MaterialTheme.colorScheme.onPrimary"))
        assertTrue(deleteConfirmation.contains("shape = P5AInteractiveShape"))
        assertTrue(deleteConfirmation.contains("Text(\"确认删除\", style = MaterialTheme.typography.labelLarge"))
        assertFalse(deleteConfirmation.contains("containerColor = ErrorRed"))
        assertTrue(workspace.contains("onLongClick = { anchorBounds?.let { onLocate(hit, it) } }"))
        assertTrue(workspace.contains(".onGloballyPositioned { anchorBounds = it.boundsInRoot() }"))
        assertTrue(workspace.contains("var returnToSearchAfterSearchOpen by rememberSaveable"))
        assertTrue(workspace.contains("returnToSearchAfterSearchOpen = true"))
        assertTrue(workspace.contains("Modifier.searchAttachmentReturnSwipe(onReturn = ::returnToSearchAfterSearchOpen)"))
        assertTrue(workspace.contains("onOpenTextHit = { hit ->\n                searchReturnAttachmentId = null\n                searchLocateConversationId = null\n                searchLocateMessageId = null\n                searchLocateAttachmentId = null\n                returnToSearchAfterSearchOpen = true"))
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
        val transcriptLocate = workspace.substringAfter("LaunchedEffect(state.searchAnchorRequestId, state.messages) {")
            .substringBefore("LaunchedEffect(\n                        state.messages.size,")
        assertTrue(transcriptLocate.contains("chatFollowLatest = false"))
        assertTrue(transcriptLocate.contains("index + normalTranscriptLeadingItems"))
        assertTrue(transcriptLocate.contains("listState.scrollToItem(index + normalTranscriptLeadingItems)"))
        for (token in listOf("attachmentPreview.original(reference)", "loadPdfPage(reference", "attachmentPreview.video(reference)", "attachmentPreview.audio(reference)", "attachmentPreview.text(reference)")) {
            assertTrue("missing local preview route: $token", viewModel.contains(token))
        }
    }

    private fun String.countOccurrences(token: String): Int = windowed(token.length, 1).count { it == token }
}

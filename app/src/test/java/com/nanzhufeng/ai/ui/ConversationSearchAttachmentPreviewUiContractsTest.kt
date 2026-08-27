package com.nanzhufeng.ai.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationSearchAttachmentPreviewUiContractsTest {
    @Test
    fun `search file tap previews locally and long press alone locates its conversation`() {
        val workspace = File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()
        val viewModel = File("src/main/java/com/nanzhufeng/ai/ui/ConversationFoundationViewModel.kt").readText()

        assertTrue(workspace.contains("onOpenAttachmentHit = { hit -> onOpenSearchAttachment(hit.attachment) }"))
        assertTrue(workspace.contains("SearchAttachmentActionMenuTarget(hit, anchorBounds)"))
        assertTrue(workspace.contains("Text(\"快速定位到对应对话\", style"))
        assertTrue(workspace.contains("private fun SearchAttachmentActionPopup("))
        assertTrue(workspace.contains("displayName = target.hit.attachment.displayName ?: \"本地附件\""))
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
        assertTrue(workspace.contains("abs(horizontalDistancePx) >= returnThresholdPx"))
        assertTrue(workspace.contains("abs(horizontalDistancePx) > abs(verticalDistancePx) * 1.3f"))
        assertTrue(workspace.contains("searchPageVisible = true"))
        assertTrue(viewModel.contains("fun openSearchAttachment(reference: ConversationAttachmentReference)"))
        for (token in listOf("attachmentPreview.original(reference)", "loadPdfPage(reference", "attachmentPreview.video(reference)", "attachmentPreview.audio(reference)", "attachmentPreview.text(reference)")) {
            assertTrue("missing local preview route: $token", viewModel.contains(token))
        }
    }
}

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
        assertTrue(workspace.contains("Text(\"跳转到对应对话\", style"))
        assertTrue(workspace.contains("private fun SearchAttachmentActionPopup("))
        assertTrue(workspace.contains("val popupWidth = 184.dp"))
        assertTrue(workspace.contains("PopupProperties(focusable = true, dismissOnBackPress = true, dismissOnClickOutside = true)"))
        assertTrue(workspace.contains("onLongClick = { anchorBounds?.let { onLocate(hit, it) } }"))
        assertTrue(workspace.contains(".onGloballyPositioned { anchorBounds = it.boundsInRoot() }"))
        assertTrue(viewModel.contains("fun openSearchAttachment(reference: ConversationAttachmentReference)"))
        for (token in listOf("attachmentPreview.original(reference)", "loadPdfPage(reference", "attachmentPreview.video(reference)", "attachmentPreview.audio(reference)", "attachmentPreview.text(reference)")) {
            assertTrue("missing local preview route: $token", viewModel.contains(token))
        }
    }
}

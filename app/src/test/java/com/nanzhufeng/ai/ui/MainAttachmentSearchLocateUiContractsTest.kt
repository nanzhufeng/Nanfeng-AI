package com.nanzhufeng.ai.ui

import com.nanzhufeng.ai.domain.ConversationSearchCategory
import com.nanzhufeng.ai.domain.conversationAttachmentSearchCategory
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainAttachmentSearchLocateUiContractsTest {
    @Test
    fun `sent main attachment popup opens its exact search category and stable attachment id`() {
        val workspace = File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()
        val provider = workspace.substring(
            workspace.indexOf("CompositionLocalProvider("),
            workspace.indexOf("LocalConversationFindQuery provides"),
        )
        val popup = workspace.substring(
            workspace.indexOf("private fun AttachmentInfoPopup("),
            workspace.indexOf("private fun AttachmentPopupAction("),
        )

        assertTrue(provider.contains("LocalAttachmentSearchLocateRequest provides { messageNodeId, attachmentId, mimeType ->"))
        assertTrue(provider.contains("searchLocateConversationId = state.selectedConversationId?.value"))
        assertTrue(provider.contains("searchLocateMessageId = messageNodeId.value"))
        assertTrue(provider.contains("searchReturnAttachmentId = attachmentId.value"))
        assertTrue(provider.contains("searchLocateAttachmentId = attachmentId.value"))
        assertTrue(provider.contains("searchLocateRequestId += 1L"))
        assertTrue(provider.contains("onSearchCategoryChanged(conversationAttachmentSearchCategory(mimeType))"))
        assertTrue(provider.contains("searchPageVisible = true"))

        assertTrue(popup.contains("val locateAttachmentInSearch = LocalAttachmentSearchLocateRequest.current"))
        assertTrue(popup.contains("if (!isDraft && messageNodeId != null)"))
        assertTrue(popup.contains("AttachmentPopupAction(Icons.Rounded.Search, \"搜索定位\""))
        assertTrue(popup.contains("locateAttachmentInSearch(messageNodeId, attachmentId, mimeType)"))
        assertFalse(popup.contains("Text(kind, color"))
    }

    @Test
    fun `main attachment popup keeps the app visual system and search target gets the existing themed cue`() {
        val workspace = File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()
        val popup = workspace.substring(
            workspace.indexOf("private fun AttachmentInfoPopup("),
            workspace.indexOf("private fun attachmentKindLabel("),
        )
        val searchPage = workspace.substring(
            workspace.indexOf("private fun ConversationSearchPage("),
            workspace.indexOf("private fun audioFormatLabel("),
        )

        assertTrue(popup.contains("shape = RoundedCornerShape(24.dp)"))
        assertTrue(popup.contains("TransientMenuTextScale"))
        assertTrue(popup.contains("containerColor = NeutralSystemSurface"))
        assertTrue(popup.contains("shape = RoundedCornerShape(19.dp)"))
        assertTrue(popup.contains("Modifier.size(16.dp)"))
        assertTrue(popup.contains("MaterialTheme.colorScheme.primary"))
        assertTrue(searchPage.contains("targeted = hit.conversationId.value == locateConversationId"))
        assertTrue(searchPage.contains("hit.messageNodeId.value == locateMessageId"))
        assertTrue(searchPage.contains("hit.attachment.id.value == locateAttachmentId"))
        assertTrue(searchPage.contains("requestId = locateRequestId"))
        assertTrue(searchPage.contains(".searchAttachmentAnchorHighlight("))
    }

    @Test
    fun `exact main attachment search route shares the index category mapping`() {
        assertEquals(ConversationSearchCategory.IMAGE, conversationAttachmentSearchCategory("image/png"))
        assertEquals(ConversationSearchCategory.VIDEO, conversationAttachmentSearchCategory("video/mp4"))
        assertEquals(ConversationSearchCategory.AUDIO, conversationAttachmentSearchCategory("audio/mpeg"))
        assertEquals(ConversationSearchCategory.FILE, conversationAttachmentSearchCategory("application/pdf"))
        assertEquals(ConversationSearchCategory.FILE, conversationAttachmentSearchCategory("text/markdown"))
    }
}

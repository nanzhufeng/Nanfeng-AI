package com.nanzhufeng.ai.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportedConversationSearchUiContractsTest {
    @Test fun `正文 catalogue labels imported sources and opens the matching branch before locating`() {
        val viewModel = File("src/main/java/com/nanzhufeng/ai/ui/ConversationFoundationViewModel.kt").readText()
        val openHit = viewModel.substringAfter("fun openSearchHit(hit: ConversationSearchHit)").substringBefore("fun locateSearchAttachment")
        val workspace = File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()
        val results = workspace.substringAfter("private fun SearchAllOrTextResults").substringBefore("private fun SearchAttachmentRow")

        assertTrue(openHit.contains("conversationSearchLeafForMessage(snapshot, messageId)"))
        assertTrue(openHit.contains("switchBranch.execute(snapshot, leafId)"))
        assertTrue(openHit.indexOf("switchBranch.execute(snapshot, leafId)") < openHit.indexOf("searchAnchorMessageId = hit.messageNodeId"))
        assertTrue(results.contains("hit.importSource?.let"))
        assertTrue(results.contains("source.searchLabel"))
        assertFalse(results.contains("state.searchQuery.isBlank() || state.searchCategory == ConversationSearchCategory.ALL"))
        assertFalse(results.contains("\"最近对话\""))
    }
}

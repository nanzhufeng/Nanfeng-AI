package com.nanzhufeng.ai.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NewConversationRecentListContractsTest {
    private val source = File("src/main/java/com/nanzhufeng/ai/ui/ConversationFoundationViewModel.kt").readText()
    private val workspace = File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()

    @Test
    fun `explicit new conversation still creates a fresh selected workspace`() {
        val create = source.substring(
            source.indexOf("fun createDevelopmentConversation"),
            source.indexOf("/** A work conversation is born"),
        )

        assertTrue(create.contains("listScope = ConversationListScope.ACTIVE"))
        assertTrue(create.contains("createConversation.execute(surface = surface)"))
        assertTrue(create.contains("selectedBefore = result.snapshot.conversation.id"))
        assertFalse(create.contains("reusableEmpty"))
    }

    @Test
    fun `empty new workspace stays out of the drawer until its first message exists`() {
        val drawer = workspace.substring(
            workspace.indexOf("private fun ConversationNavigationDrawer"),
            workspace.indexOf("private fun WorkProjectNavigationDrawer"),
        )

        assertTrue(drawer.contains("val drawerConversations = state.conversations.filter { it.currentLeafMessageId != null }"))
        assertTrue(drawer.contains("conversations = drawerConversations"))
        assertTrue(drawer.contains("val pinned = drawerConversations.filter"))
        assertTrue(drawer.contains("val content = drawerConversations.filter"))
        assertFalse(drawer.contains("val content = state.conversations.filter"))
    }
}

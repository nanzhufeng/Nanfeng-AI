package com.nanzhufeng.ai.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NewConversationRecentListContractsTest {
    private val source = File("src/main/java/com/nanzhufeng/ai/ui/ConversationFoundationViewModel.kt").readText()

    @Test
    fun `explicit new conversation creates a fresh active item for the first recent row`() {
        val create = source.substring(
            source.indexOf("fun createDevelopmentConversation"),
            source.indexOf("/** A work conversation is born"),
        )

        assertTrue(create.contains("listScope = ConversationListScope.ACTIVE"))
        assertTrue(create.contains("createConversation.execute(surface = surface)"))
        assertTrue(create.contains("selectedBefore = result.snapshot.conversation.id"))
        assertFalse(create.contains("reusableEmpty"))
    }
}

package com.nanzhufeng.ai.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantFooterActionAlignmentContractsTest {
    private val source = File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()

    @Test
    fun `first assistant footer action visually aligns with reading text without shrinking its hit target`() {
        val actionRow = source.substring(
            source.indexOf("private fun AssistantMessageActionRow"),
            source.indexOf("private fun MessageActionPopup"),
        )
        val firstActionStart = actionRow.indexOf("AssistantMessageAction(")
        val secondActionStart = actionRow.indexOf("AssistantMessageAction(", firstActionStart + 1)
        val firstAction = actionRow.substring(firstActionStart, secondActionStart)

        assertTrue(source.contains("private val ConversationAssistantReadingStartInset = 24.dp"))
        assertTrue(source.contains("private val AssistantFooterLeadingActionVisualOffset = 10.dp"))
        assertTrue(firstAction.contains("modifier = Modifier.offset(x = -AssistantFooterLeadingActionVisualOffset)"))
        assertTrue(source.contains("modifier = modifier.size(36.dp)"))
    }
}

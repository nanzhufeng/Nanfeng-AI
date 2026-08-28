package com.nanzhufeng.ai.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantFooterActionAlignmentContractsTest {
    private val source = File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()

    @Test
    fun `assistant footer actions share one left aligned hit box and equal spacing`() {
        val actionRow = source.substring(
            source.indexOf("private fun AssistantMessageActionRow"),
            source.indexOf("private fun MessageActionPopup"),
        )
        val firstActionStart = actionRow.indexOf("AssistantMessageAction(")
        val secondActionStart = actionRow.indexOf("AssistantMessageAction(", firstActionStart + 1)
        val firstAction = actionRow.substring(firstActionStart, secondActionStart)

        assertTrue(source.contains("private val ConversationAssistantReadingStartInset = 24.dp"))
        assertTrue(source.contains("private val AssistantFooterActionSpacing = 4.dp"))
        assertTrue(actionRow.contains("start = 0.dp"))
        assertTrue(actionRow.contains("horizontalArrangement = Arrangement.spacedBy(AssistantFooterActionSpacing)"))
        assertTrue(actionRow.contains("BoxWithConstraints(Modifier.weight(1f))"))
        assertFalse(firstAction.contains("offset("))
        assertTrue(source.contains("modifier = modifier.size(36.dp)"))
    }
}

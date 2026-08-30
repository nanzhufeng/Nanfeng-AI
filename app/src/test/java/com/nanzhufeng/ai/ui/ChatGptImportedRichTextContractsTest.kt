package com.nanzhufeng.ai.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatGptImportedRichTextContractsTest {
    @Test
    fun `imported automation marker exposes only its readable reminder label`() {
        val raw = "\uE200genui\uE202{\"suggest_automation\":{\"label\":\"持续监控 AI CapEx 融资质量与循环融资风险\"}}\uE201"

        assertEquals("持续监控 AI CapEx 融资质量与循环融资风险", chatGptImportedAutomationLabel(raw))
    }

    @Test
    fun `imported cite marker counts references and maps unknown marker families safely`() {
        val citation = chatGptImportedMarkers("\uE200cite\uE202turn540057search0\uE202turn540057search18\uE201").single()
        val navigation = chatGptImportedMarkers("\uE200navlist\uE202turn9news0\uE201").single()

        assertEquals("来源 +1", citation.label)
        assertEquals(2, citation.itemCount)
        assertEquals("相关链接", navigation.label)
    }

    @Test
    fun `conversation renderer replaces ChatGPT private markers with stable native components`() {
        val source = File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()

        for (token in listOf(
            "ImportedChatGptAutomationSuggestion",
            "ImportedChatGptMarkerChip",
            "appendChatGptImportedText",
            "appendInlineContent(marker.inlineContentId, marker.label)",
            "Icons.Rounded.History",
            "Icons.Rounded.Language",
        )) assertTrue("missing imported ChatGPT rich marker owner: $token", source.contains(token))
    }

    @Test
    fun `source shortcuts consume the shared appearance palette instead of a fixed light chip`() {
        val source = File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()
        val importedChip = source.substringAfter("private fun ImportedChatGptMarkerChip").substringBefore("private fun InlinePresentation.Link.sourceHost")
        val sourceChip = source.substringAfter("private fun SourceLinkShortcut").substringBefore("private fun SourceLinksDialog")

        for (chip in listOf(importedChip, sourceChip)) {
            assertTrue(chip.contains("color = NeutralSystemSurface"))
            assertTrue(chip.contains("border = BorderStroke(1.dp, NeutralBorder.copy(alpha = 0.58f))"))
            assertFalse(chip.contains("Color(0xFFEDEDED)"))
        }
    }
}

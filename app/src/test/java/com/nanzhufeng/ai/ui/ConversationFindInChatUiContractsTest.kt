package com.nanzhufeng.ai.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationFindInChatUiContractsTest {
    @Test fun `chat find keeps a white input on a gray local surface without redundant blank-state copy`() {
        val source = File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()
        val dialog = source.substringAfter("private fun ConversationFindInChatDialog").substringBefore("private fun ConversationFindNavigationBar")

        assertTrue(dialog.contains("Text(\"输入关键词\", color = BodyText"))
        assertTrue(dialog.contains("containerColor = NeutralSystemSurface"))
        assertTrue(dialog.contains("Surface(color = NeutralSystemSurface, shape = RoundedCornerShape(16.dp)"))
        assertTrue(dialog.contains("focusedContainerColor = ForegroundSurface"))
        assertTrue(dialog.contains("unfocusedContainerColor = ForegroundSurface"))
        assertFalse(dialog.contains("label = { Text(\"输入关键词\") }"))
        assertFalse(dialog.contains("仅在当前本地对话中查找，不会搜索其它会话或发送内容。"))
        assertTrue(dialog.contains("if (query.isNotBlank())"))
        assertTrue(dialog.contains("当前对话没有匹配内容。"))
        assertTrue(dialog.contains("找到 \${matches.size} 处匹配"))
    }

    @Test fun `active chat find query is rendered as themed text on neutral rounded pills without mutating messages`() {
        val source = File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()
        val inlineText = source.substringAfter("private fun inlineText(").substringBefore("private fun InlinePresentationText")
        val highlight = source.substringAfter("private fun Modifier.conversationFindHighlightBackgrounds").substringBefore("private fun sourceShortcutWidth")

        for (token in listOf("LocalConversationFindQuery provides activeFindQuery", "LocalConversationFindTarget provides", "highlightQuery: String? = null", "conversationFindOccurrenceStarts", "ConversationFindHighlightAnnotationTag", "SpanStyle(color = AccentOrange, fontWeight = FontWeight.Bold)")) {
            assertTrue("missing find-highlight projection $token", source.contains(token) || inlineText.contains(token))
        }
        for (token in listOf("drawRoundRect", "SecondaryText.copy(alpha = 0.26f)", "Brush.horizontalGradient", "AccentOrange.copy", "BrandGreen.copy", "CornerRadius(9.dp.toPx())", "getStringAnnotations(ConversationFindHighlightAnnotationTag")) {
            assertTrue("missing rounded neutral find highlight $token", highlight.contains(token))
        }
        assertTrue(source.contains("bringIntoViewRequester.bringIntoView(matchRect)"))
        assertTrue(source.contains("activeTranscriptListState.scrollToItem(itemIndex)"))
        assertFalse(highlight.contains("state.messages ="))
    }

    @Test fun `find occurrence counter keeps repeated words in one long message distinct`() {
        val value = "值得盯的几个具体信号，这也是重要信号。"

        val starts = conversationFindOccurrenceStarts(value, "信号")

        assertTrue(starts.size == 2)
        assertTrue(starts.zipWithNext().all { (first, second) -> second > first })
    }
}

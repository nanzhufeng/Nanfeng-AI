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
        assertTrue(dialog.contains("找到 \${matches.size} 条匹配消息"))
    }

    @Test fun `active chat find query is rendered as themed text on neutral rounded pills without mutating messages`() {
        val source = File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()
        val inlineText = source.substringAfter("private fun inlineText(").substringBefore("private fun InlinePresentationText")
        val highlight = source.substringAfter("private fun Modifier.conversationFindHighlightBackgrounds").substringBefore("private fun sourceShortcutWidth")

        for (token in listOf("LocalConversationFindQuery provides activeFindQuery", "highlightQuery: String? = null", "appendConversationFindText", "ConversationFindHighlightAnnotationTag", "SpanStyle(color = AccentOrange")) {
            assertTrue("missing find-highlight projection $token", source.contains(token) || inlineText.contains(token))
        }
        for (token in listOf("drawRoundRect", "color = NeutralSystemSurface", "CornerRadius(9.dp.toPx())", "getStringAnnotations(ConversationFindHighlightAnnotationTag")) {
            assertTrue("missing rounded neutral find highlight $token", highlight.contains(token))
        }
        assertFalse(highlight.contains("state.messages ="))
    }
}

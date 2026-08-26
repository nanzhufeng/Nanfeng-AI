package com.nanzhufeng.ai.ui

import org.junit.Assert.assertTrue
import org.junit.Test

class P6DarkSkinSurfaceContractsTest {
    @Test fun `Markdown table gives dark skin a readable semantic surface`() {
        val source = java.io.File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()
        val table = source.substring(source.indexOf("private fun MarkdownTable"), source.indexOf("private fun adaptiveTableColumnWeights"))
        for (token in listOf(
            "val darkTable = ForegroundSurface.red < 0.5f",
            "Color(0xFF34383A)",
            "Color(0xFF42484A)",
            "Color(0xFF5A6264)",
            "Color(0xFF4A5153)",
            "headerBackground = tableHeaderSurface",
            "dividerColor = tableDivider",
        )) assertTrue("missing dark Markdown table treatment: $token", table.contains(token))
    }
}

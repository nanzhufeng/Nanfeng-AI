package com.nanzhufeng.ai.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchAudioCatalogPreviewContractsTest {
    @Test
    fun `search audio cards keep white player controls on a readable semantic surface`() {
        val source = File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()
        val card = source.substring(
            source.indexOf("private fun SearchAttachmentCard"),
            source.indexOf("private fun SearchAttachmentRow("),
        )
        val row = source.substring(
            source.indexOf("private fun SearchAttachmentRow"),
            source.indexOf("private fun audioFormatLabel"),
        )

        assertTrue(source.contains("private fun catalogAudioPreviewSurface(dark: Boolean): Color"))
        assertTrue(source.contains("if (dark) Color(0xFF2A3438) else Color(0xFF183551)"))
        assertTrue(card.contains(".background(if (isAudio) audioCatalogSurface else NeutralSystemSurface)"))
        assertTrue(row.contains("Surface(color = if (isAudio) audioCatalogSurface else NeutralSystemSurface"))
    }
}

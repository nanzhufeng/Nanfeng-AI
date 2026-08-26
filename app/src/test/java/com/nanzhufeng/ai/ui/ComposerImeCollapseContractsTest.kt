package com.nanzhufeng.ai.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposerImeCollapseContractsTest {
    @Test
    fun `composer returns to its compact single-line presentation when the IME closes`() {
        val source = File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()
        val dock = source.substring(
            source.indexOf("private fun ConversationComposerDock"),
            source.indexOf("private fun ComposerAttachmentPreview"),
        )

        assertTrue(dock.contains("val imeVisible = WindowInsets.isImeVisible"))
        assertTrue(dock.contains("val inputExpanded = inputFocused && imeVisible"))
        assertTrue(dock.contains("LaunchedEffect(imeVisible)"))
        assertTrue(dock.contains("if (!imeVisible) inputFocused = false"))
        assertTrue(dock.contains("if (inputExpanded)"))
        assertTrue(dock.contains("expanded = false"))
    }
}

package com.nanzhufeng.ai.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationRenameDialogFocusContractsTest {
    @Test fun `mobile rename uses a normal input dialog and requests the keyboard`() {
        val source = File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()
        val inline = source.substringAfter("internal fun InlineConversationTitle").substringBefore("private fun CompactConversationSelectionCheckbox")
        assertTrue(inline.contains("remember { FocusRequester() }"))
        assertTrue(inline.contains("TextFieldValue(title, TextRange(0, title.length))"))
        assertTrue(inline.contains("focus.requestFocus()"))
        assertTrue(inline.contains("singleLine = true"))
        assertTrue(inline.contains("AlertDialog("))
        assertTrue(inline.contains("OutlinedTextField("))
        assertTrue(inline.contains("keyboard?.show()"))
        assertTrue(inline.contains("Key.Escape"))
    }
}

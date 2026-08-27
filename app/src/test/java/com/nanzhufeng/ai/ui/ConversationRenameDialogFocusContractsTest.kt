package com.nanzhufeng.ai.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationRenameDialogFocusContractsTest {
    @Test fun `rename opens with keyboard focus selects the existing title and spans the drawer`() {
        val source = File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()
        val dialog = source.substringAfter("private fun CompactConversationRenameDialog").substringBefore("private fun DismissibleDialogBackdrop")
        val field = source.substringAfter("private fun CompactConversationRenameField").substringBefore("private fun TranscriptDateDivider")

        assertTrue(dialog.contains("remember { FocusRequester() }"))
        assertTrue(dialog.contains("LocalSoftwareKeyboardController.current"))
        assertTrue(dialog.contains("TextFieldValue(value, selection = TextRange(0, value.length))"))
        assertTrue(dialog.contains("LaunchedEffect(Unit)"))
        assertTrue(dialog.contains("focusRequester.requestFocus()"))
        assertTrue(dialog.contains("keyboardController?.show()"))
        assertTrue(dialog.contains("onValueChange(updated.text)"))
        assertTrue(dialog.contains("BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart)"))
        assertTrue(dialog.contains("val drawerWidth = if (maxWidth >= 600.dp) maxWidth * (2f / 3f) else maxWidth.coerceAtMost(320.dp)"))
        assertTrue(dialog.contains(".width(drawerWidth)"))
        assertTrue(field.contains("value: TextFieldValue"))
        assertTrue(field.contains(".focusRequester(focusRequester)"))
    }
}

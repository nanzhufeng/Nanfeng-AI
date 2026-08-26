package com.nanzhufeng.ai.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationPopupLayerContractsTest {
    @Test
    fun `all composer popup kinds outrank the scroll to latest control`() {
        val source = File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()
        val overlay = source.substring(
            source.indexOf("private fun ComposerMenuOverlay"),
            source.indexOf("private fun ComposerModelPickerHeader"),
        )

        assertTrue(source.contains("private const val ConversationScrollToLatestZIndex = 1f"))
        assertTrue(source.contains("private const val ConversationModalOverlayZIndex = 2f"))
        assertTrue(source.contains(".zIndex(ConversationScrollToLatestZIndex)"))
        assertTrue(overlay.contains("Box(Modifier.fillMaxSize().zIndex(ConversationModalOverlayZIndex))"))
        assertTrue(overlay.contains("ComposerMenu.ATTACHMENTS -> addAnchor"))
        assertTrue(overlay.contains("ComposerMenu.MODEL -> modelAnchor"))
        assertTrue(source.contains("private const val TransientMenuTextScaleFactor = 0.90f"))
        assertTrue(source.contains("private fun TransientMenuTextScale(content: @Composable () -> Unit)"))
        assertTrue(overlay.contains("} else TransientMenuTextScale {"))
        val actionSheet = source.substring(source.indexOf("private fun ConversationActionSheet"), source.indexOf("private fun ConversationMenuAction"))
        assertTrue(actionSheet.contains("TransientMenuTextScale {"))
        assertTrue(source.contains("fontSize = scaledTransientMenuTextUnit(15.sp)"))
        assertTrue(source.contains("fontSize = scaledConversationTextUnit(14.sp)"))
    }
}

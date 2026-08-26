package com.nanzhufeng.ai.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FBP6042TopBarOwnershipContractsTest {
    private val source = File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()

    @Test
    fun `FB-P6-042 keeps Android Settings in the drawer and reserves its header for navigation mode and Ghost`() {
        val drawer = source.substring(source.indexOf("private fun ConversationNavigationDrawer"), source.indexOf("/**\n * Settings owns"))
        // The shell header is now a sibling overlay rather than a fixed layout row, so the
        // transcript remains the canvas underneath all three independent controls.
        assertTrue(source.contains("ConversationShellHeader("))
        assertTrue(source.contains("modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(horizontal = 18.dp, vertical = 18.dp)"))
        val shell = source.substring(source.indexOf("private fun ConversationShellHeader"), source.indexOf("private fun ConversationModeSwitch"))
        for (token in listOf("ConversationModeSwitch(", "contentDescription = \"临时聊天\"")) assertTrue("missing $token", shell.contains(token))
        val modeSwitch = source.substring(source.indexOf("private fun ConversationModeSwitch"), source.indexOf("private fun ConversationWorkScope"))
        for (token in listOf("ConversationModeSegment(label = \"对话\"", "ConversationModeSegment(label = \"工作\"", "Text(label")) assertTrue("missing $token", modeSwitch.contains(token))
        assertFalse(shell.contains("P5ARoute.SETTINGS"))
        assertFalse(shell.contains("background(Color.White)"))
        assertTrue(drawer.contains("onOpenRoute(P5ARoute.SETTINGS)"))
    }
}

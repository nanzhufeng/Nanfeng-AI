package com.nanzhufeng.ai.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationDrawerFloatingHeaderContractsTest {
    @Test
    fun `normal drawer keeps the app identity fixed above its scrolling conversation list`() {
        val source = File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()
        val drawer = source.substring(
            source.indexOf("private fun ConversationNavigationDrawer"),
            source.indexOf("private fun ConversationBatchEditControls"),
        )
        val headerStart = drawer.indexOf(".align(Alignment.TopStart)")
        val header = drawer.substring(headerStart, drawer.lastIndexOf("if (batchEditing)"))

        assertTrue(drawer.contains("val drawerIdentityVisualSize = scaledAppIconSize(36.dp)"))
        assertTrue(drawer.contains("val drawerIdentityTitleLineHeight = scaledAppTextUnit(28.sp)"))
        assertTrue(drawer.contains("val drawerIdentityHeaderReservedHeight = maxOf("))
        assertTrue(drawer.contains("Spacer(Modifier.height(drawerIdentityHeaderReservedHeight))"))
        assertTrue(header.contains(".zIndex(1f)"))
        assertTrue(header.contains(".statusBarsPadding()"))
        assertTrue(header.contains("R.drawable.nanfeng_ai_icon_foreground_image"))
        assertTrue(header.contains("Text(\n                    \"南枫 AI\","))
        assertTrue(header.contains("fontSize = scaledAppTextUnit(22.sp)"))
        assertTrue(header.contains("fontFamily = DrawerIdentityRoundedFontFamily"))
        assertTrue(header.contains("fontWeight = FontWeight.Bold"))
    }
}

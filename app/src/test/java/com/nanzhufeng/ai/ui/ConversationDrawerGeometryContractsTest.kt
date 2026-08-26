package com.nanzhufeng.ai.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationDrawerGeometryContractsTest {
    @Test
    fun `drawer reaches both system edges and tightens text rows without reducing card gaps`() {
        val source = File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()
        val drawer = source.substring(
            source.indexOf("ModalDrawerSheet("),
            source.indexOf("private fun ConversationNavigationDrawer"),
        )
        val row = source.substring(
            source.indexOf("private fun ConversationNavigationRow"),
            source.indexOf("private data class ConversationActionMenuTarget"),
        )

        assertTrue(drawer.contains("drawerShape = RectangleShape"))
        assertTrue(drawer.contains("windowInsets = WindowInsets(0, 0, 0, 0)"))
        assertTrue(source.contains("Modifier.fillMaxSize().statusBarsPadding().padding("))
        assertTrue(row.contains("val rowHeight = if (batchEditing) 44.dp else 36.dp"))
        assertTrue(source.contains("verticalArrangement = Arrangement.spacedBy(8.dp)"))
    }
}

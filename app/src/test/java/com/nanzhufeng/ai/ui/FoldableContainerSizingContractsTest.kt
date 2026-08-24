package com.nanzhufeng.ai.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FoldableContainerSizingContractsTest {
    private val source = File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()

    @Test fun `drawer and anchored menus use the current compose container rather than display configuration`() {
        assertTrue(source.contains("LocalWindowInfo.current.containerSize"))
        assertTrue(source.contains("val windowWidth = with(density) { LocalWindowInfo.current.containerSize.width.toDp() }"))
        assertTrue(source.contains("val drawerWidth = if (windowWidth >= 600.dp) windowWidth * (2f / 3f) else 320.dp"))
        assertFalse(source.contains("TranscriptPositionRail"))
        assertFalse(source.contains("screenWidthDp"))
        assertFalse(source.contains("screenHeightDp"))
    }
}

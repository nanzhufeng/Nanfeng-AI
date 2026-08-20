package com.nanzhufeng.ai.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FBP6043ScrollToLatestContractsTest {
    private val source = File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()

    @Test
    fun `FB-P6-043 anchors the latest control above the adaptive composer and hides it at the end`() {
        for (token in listOf(
            "val showJumpToLatest by remember",
            "lastVisible < layout.totalItemsCount - 1",
            "val jumpToLatestBottomPadding = floatingComposerHeight + 4.dp",
            "Modifier.align(Alignment.BottomCenter).padding(bottom = jumpToLatestBottomPadding)",
            "listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)",
            "JumpToLatestButton(",
            "Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = \"到最新消息\")",
            "DraftComposer(",
        )) assertTrue("missing $token", source.contains(token))
        assertFalse(source.contains("Modifier.align(Alignment.BottomEnd).padding(12.dp)"))
    }
}

package com.nanzhufeng.ai.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptScrollIndicatorVisibilityContractsTest {
    private val source = File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()

    @Test
    fun `scroll indicator appears on scrolling and hides after three seconds of idle reading`() {
        val visibilityOwner = source.substring(
            source.indexOf("private const val TranscriptScrollbarIdleHideMillis"),
            source.indexOf("private fun TranscriptScrollIndicator"),
        )
        val indicator = source.substring(
            source.indexOf("private fun TranscriptScrollIndicator"),
            source.indexOf("private fun presentedMessagePlainText"),
        )

        assertTrue(visibilityOwner.contains("TranscriptScrollbarIdleHideMillis = 3_000L"))
        assertTrue(visibilityOwner.contains("var visible by remember(listState) { mutableStateOf(true) }"))
        assertTrue(visibilityOwner.contains("LaunchedEffect(canScroll, listState.isScrollInProgress)"))
        assertTrue(visibilityOwner.contains("listState.isScrollInProgress -> visible = true"))
        assertTrue(visibilityOwner.contains("delay(TranscriptScrollbarIdleHideMillis)"))
        assertTrue(visibilityOwner.contains("visible = false"))
        assertTrue(indicator.contains("if (!rememberTranscriptScrollIndicatorVisible(listState, metrics.canScroll)) return"))
    }
}

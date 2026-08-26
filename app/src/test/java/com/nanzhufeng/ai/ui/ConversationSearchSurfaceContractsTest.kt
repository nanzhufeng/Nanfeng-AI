package com.nanzhufeng.ai.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationSearchSurfaceContractsTest {
    @Test
    fun `search canvas and controls keep two deeper gray steps while results remain white cards`() {
        val app = File("src/main/java/com/nanzhufeng/ai/ui/NanfengAiApp.kt").readText()
        val source = File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()
        val page = source.substring(source.indexOf("private fun ConversationSearchPage"), source.indexOf("private fun SearchAllOrTextResults"))

        assertTrue(app.contains("SearchPageCanvas by mutableStateOf(Color(0xFFEDEEEE))"))
        assertTrue(app.contains("SearchControlSurface by mutableStateOf(Color(0xFFE1E4E2))"))
        assertTrue(page.contains("Surface(color = SearchPageCanvas"))
        assertTrue(page.split("color = SearchControlSurface").size - 1 == 2)
        assertTrue(page.contains("conversationEdgeGrayFade(\n                                topBand = 0.dp,\n                                bottomBand = 112.dp,\n                                edgeColor = SearchPageCanvas,"))
        assertTrue(page.contains("modifier = Modifier.align(Alignment.Center).semantics { heading() }"))
        assertTrue(source.contains("onOpenHit(hit) },\n                    color = ForegroundSurface"))
    }
}

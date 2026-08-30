package com.nanzhufeng.ai.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchHistorySuggestionContractsTest {
    @Test fun `history matching prefers exact then recent prefix then contained text`() {
        val history = listOf("南枫 ai 安卓", "android pdf 清晰度", "zip 文件查看")

        assertEquals("南枫 ai 安卓", bestSearchHistoryMatch("  南枫   AI 安卓 ", history))
        assertEquals("android pdf 清晰度", bestSearchHistoryMatch("and", history))
        assertEquals("zip 文件查看", bestSearchHistoryMatch("文件", history))
        assertNull(bestSearchHistoryMatch("", history))
        assertNull(bestSearchHistoryMatch("不存在", history))
    }

    @Test fun `typing opens and locates persisted history without recording debounce queries`() {
        val viewModel = File("src/main/java/com/nanzhufeng/ai/ui/ConversationFoundationViewModel.kt").readText()
        val workspace = File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()

        for (token in listOf(
            "bestSearchHistoryMatch(query, recentHistory)",
            "searchHistoryOpen = state.searchHistoryManuallyOpened || matchedHistory != null",
            "searchHistoryHighlightedQuery = matchedHistory",
            "executeSearch(recordHistory = false, closeHistoryOnComplete = false)",
            "if (recordHistory && !browsing) searchHistory.record(query, scope)",
        )) assertTrue(token, viewModel.contains(token))
        for (token in listOf(
            "highlightedQuery = state.searchHistoryHighlightedQuery",
            "historyListState.scrollToItem(index)",
            "query == highlightedQuery",
            "MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)",
            "LazyColumn(",
        )) assertTrue(token, workspace.contains(token))
    }
}

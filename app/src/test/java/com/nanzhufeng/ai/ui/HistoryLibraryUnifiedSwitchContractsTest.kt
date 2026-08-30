package com.nanzhufeng.ai.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryLibraryUnifiedSwitchContractsTest {
    private val settingsUi = File("src/main/java/com/nanzhufeng/ai/ui/NanfengAiApp.kt").readText()
    private val router = File("src/main/java/com/nanzhufeng/ai/ai/NormalChatOpenRouterExecutor.kt").readText()
    private val curation = File("src/main/java/com/nanzhufeng/ai/domain/HistoryKnowledgeCuration.kt").readText()
    private val container = File("src/main/java/com/nanzhufeng/ai/app/AppContainer.kt").readText()

    @Test fun `history library has one visible switch and one effective permission for write and read`() {
        assertTrue(settingsUi.contains("title = \"历史资料库\""))
        assertTrue(settingsUi.contains("checked = settings.historyLibraryEnabled"))
        assertTrue(settingsUi.contains("librarySearchEnabled = enabled"))
        assertTrue(settingsUi.contains("autoHistoryKnowledgeEnabled = enabled"))
        assertFalse(settingsUi.contains("title = \"自动沉淀历史资料\""))
        assertTrue(router.contains("includeRelevantKnowledge = experience.historyLibraryEnabled"))
        assertTrue(curation.contains("if (!settings().historyLibraryEnabled)"))
        assertTrue(container.contains("historyKnowledgeAutoCurationScheduler.onSettingChanged(loadAssistantExperienceSettings.execute().historyLibraryEnabled)"))
    }
}

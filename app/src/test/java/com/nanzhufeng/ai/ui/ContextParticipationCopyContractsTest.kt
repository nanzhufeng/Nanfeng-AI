package com.nanzhufeng.ai.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextParticipationCopyContractsTest {
    @Test
    fun `memory and knowledge UI copy matches ordinary chat automatic relevant retrieval`() {
        val app = File("src/main/java/com/nanzhufeng/ai/ui/NanfengAiApp.kt").readText()
        val memory = File("src/main/java/com/nanzhufeng/ai/ui/MemoryWorkspace.kt").readText()
        val viewModel = File("src/main/java/com/nanzhufeng/ai/ui/MemoryViewModel.kt").readText()
        val knowledge = File("src/main/java/com/nanzhufeng/ai/ui/KnowledgeLibraryUi.kt").readText()

        for (source in listOf(app, memory, viewModel, knowledge)) {
            assertFalse(source.contains("不会自动加入对话上下文"))
        }
        assertTrue(app.contains("启用记忆后会按当前问题自动检索"))
        assertTrue(memory.contains("启用记忆后，会按当前问题自动检索相关内容加入对话上下文。"))
        assertTrue(knowledge.contains("开启资料库搜索后，相关内容会按当前问题自动检索并加入对话上下文。"))
    }
}

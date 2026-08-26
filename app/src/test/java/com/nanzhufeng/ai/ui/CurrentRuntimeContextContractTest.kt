package com.nanzhufeng.ai.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrentRuntimeContextContractTest {
    private fun projectFile(path: String) = File("..", path)

    @Test
    fun `current ordinary chat context contract overrides historical P4 wording without expanding egress`() {
        val current = projectFile("docs/ANDROID_RUNTIME_CONTEXT_CURRENT_CONTRACT.md").readText()
        val settings = projectFile("docs/ANDROID_SETTINGS_UI_CURRENT_CONTRACT.md").readText()
        val conversation = projectFile("docs/ANDROID_CONVERSATION_UI_CURRENT_CONTRACT.md").readText()
        val agents = projectFile("AGENTS.md").readText()
        val router = File("src/main/java/com/nanzhufeng/ai/ai/NormalChatOpenRouterExecutor.kt").readText()
        val broker = File("src/main/java/com/nanzhufeng/ai/domain/LocalContextBroker.kt").readText()
        val experience = File("src/main/java/com/nanzhufeng/ai/domain/AssistantExperienceSettings.kt").readText()

        assertTrue(current.contains("NormalChatOpenRouterExecutor → LocalContextBroker → ChatAdapter"))
        assertTrue(current.contains("每次普通调用"))
        assertTrue(current.contains("至多 6 条"))
        assertTrue(current.contains("至多 8 条"))
        assertTrue(current.contains("不会把整个本地索引"))
        assertTrue(settings.contains("ANDROID_RUNTIME_CONTEXT_CURRENT_CONTRACT.md"))
        assertTrue(conversation.contains("ANDROID_RUNTIME_CONTEXT_CURRENT_CONTRACT.md"))
        assertTrue(agents.contains("ANDROID_RUNTIME_CONTEXT_CURRENT_CONTRACT.md"))
        assertTrue(router.contains("includeRelevantMemory = experience.memoryEnabled"))
        assertTrue(router.contains("includeRelevantKnowledge = experience.librarySearchEnabled"))
        assertTrue(broker.contains("const val MEMORY_LIMIT = 6"))
        assertTrue(broker.contains("const val KNOWLEDGE_LIMIT = 8"))
        assertTrue(experience.contains("职业或角色："))
        assertTrue(experience.contains("回答偏好："))
        assertFalse(current.contains("全部本地索引无差别外发"))
    }

    @Test
    fun `historical P4 contracts are explicitly prevented from overriding ordinary chat`() {
        for (path in listOf(
            "docs/P4C_MEMORY_GOVERNANCE_CONTRACT.md",
            "docs/P4D_EXPLICIT_CONTEXT_BODY_SELECTION_CONTRACT.md",
            "docs/domain-rules.md",
            "docs/implementation-plan.md",
            "docs/architecture-governance.md",
        )) {
            val source = projectFile(path).readText()
            assertTrue("$path must point to the current runtime contract", source.contains("ANDROID_RUNTIME_CONTEXT_CURRENT_CONTRACT.md"))
        }
    }

    @Test
    fun `master plan and handoff lead with the same current contract sources`() {
        val masterGate = projectFile("docs/MASTER_PLAN_COMPLETION_AUDIT_20260816.md")
            .readLines().take(45).joinToString("\n")
        val handoffGate = projectFile("docs/CURRENT_HANDOFF.md")
            .readLines().take(32).joinToString("\n")

        for (source in listOf(masterGate, handoffGate)) {
            assertTrue(source.contains("ANDROID_CONVERSATION_UI_CURRENT_CONTRACT.md"))
            assertTrue(source.contains("ANDROID_SETTINGS_UI_CURRENT_CONTRACT.md"))
            assertTrue(source.contains("ANDROID_RUNTIME_CONTEXT_CURRENT_CONTRACT.md"))
        }
        assertTrue(masterGate.contains("顶部“最新有效交接”"))
        assertTrue(handoffGate.contains("最新有效交接"))
        assertFalse(masterGate.contains("顶部 2026-08-24 记录"))
    }

}

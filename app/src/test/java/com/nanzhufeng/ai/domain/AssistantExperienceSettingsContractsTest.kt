package com.nanzhufeng.ai.domain

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantExperienceSettingsContractsTest {
    @Test
    fun `custom instructions preserve the complete eight-thousand-character user budget`() {
        val fullInstruction = "个".repeat(AssistantExperienceSettings.CUSTOM_INSTRUCTIONS_MAX_LENGTH)
        val emitted = AssistantExperienceSettings(
            personalizationEnabled = true,
            customInstructions = fullInstruction,
        ).modelInstruction().orEmpty()

        assertTrue(emitted.endsWith(fullInstruction))
        assertFalse(runCatching {
            AssistantExperienceSettings(customInstructions = fullInstruction + "个")
        }.isSuccess)
    }

    @Test
    fun `profile personalization is absent until the user explicitly enables it`() {
        val draft = AssistantExperienceSettings(
            displayName = "南烛枫",
            occupation = "创作者",
            customInstructions = "先给结论。",
        )
        val withoutPersonalization = draft.modelInstruction().orEmpty()
        assertFalse(withoutPersonalization.contains("南烛枫"))
        assertFalse(withoutPersonalization.contains("创作者"))
        assertTrue(withoutPersonalization.contains("先给结论"))

        val enabled = draft.copy(personalizationEnabled = true).modelInstruction().orEmpty()
        assertTrue(enabled.contains("南烛枫"))
        assertTrue(enabled.contains("先给结论"))
    }

    @Test
    fun `saved custom instructions apply to every ordinary chat even when memory is paused`() {
        val instruction = AssistantExperienceSettings(
            personalizationEnabled = false,
            memoryRetrievalEnabled = false,
            customInstructions = "先给结论。",
        ).modelInstruction().orEmpty()

        assertTrue(instruction.contains("回答偏好：先给结论。"))
    }

    @Test
    fun `conversation style affects the next reply without exposing profile data`() {
        val instruction = AssistantExperienceSettings(
            displayName = "南烛枫",
            conversationStyle = ConversationStyle.DIRECT,
        ).modelInstruction().orEmpty()

        assertTrue(instruction.contains("直言不讳"))
        assertFalse(instruction.contains("南烛枫"))
    }

    @Test
    fun `humorous conversation style stays light without weakening important facts`() {
        val instruction = AssistantExperienceSettings(
            conversationStyle = ConversationStyle.HUMOROUS,
        ).modelInstruction().orEmpty()

        assertTrue(instruction.contains("风趣搞笑"))
        assertTrue(instruction.contains("不得牺牲准确性"))
        assertTrue(instruction.contains("高风险"))
    }

    @Test
    fun `library search is enabled by default to preserve existing local retrieval behavior`() {
        assertTrue(AssistantExperienceSettings().librarySearchEnabled)
    }

    @Test
    fun `web search is enabled by default and remains separately controllable`() {
        assertTrue(AssistantExperienceSettings().webSearchEnabled)
        assertFalse(AssistantExperienceSettings(webSearchEnabled = false).webSearchEnabled)
    }

    @Test
    fun `new conversation first reply uses the enabled user nickname even without memory retrieval`() {
        val settings = AssistantExperienceSettings(
            personalizationEnabled = true,
            memoryRetrievalEnabled = false,
            displayName = "南烛枫",
            occupation = "开发者",
            customInstructions = "先给结论。",
        )

        val firstReply = settings.modelInstruction(isFirstAssistantReply = true).orEmpty()
        val laterReply = settings.modelInstruction(isFirstAssistantReply = false).orEmpty()

        assertTrue(firstReply.contains("称呼：南烛枫"))
        assertTrue(firstReply.contains("首个助理回复"))
        assertTrue(firstReply.contains("以“南烛枫，”"))
        assertTrue(firstReply.contains("职业或角色：开发者"))
        assertTrue(firstReply.contains("回答偏好：先给结论。"))
        assertFalse(laterReply.contains("首个助理回复"))
    }

    @Test
    fun `disabling memory summary retrieval preserves directly authored personalization`() {
        val settings = AssistantExperienceSettings(
            personalizationEnabled = true,
            memoryRetrievalEnabled = false,
            displayName = "南烛枫",
            customInstructions = "先给结论。",
        )

        assertFalse(settings.memoryEnabled)
        assertTrue(settings.modelInstruction().orEmpty().contains("称呼：南烛枫"))
        assertTrue(settings.modelInstruction().orEmpty().contains("回答偏好：先给结论。"))
    }

    @Test
    fun `memory policy removes relevant memories without suppressing the current user message`() {
        val tree = ConversationTreeService(Clock.fixed(Instant.parse("2026-08-24T00:00:00Z"), ZoneOffset.UTC))
        val current = tree.append(tree.create("当前"), AppendMessageRequest(MessageRole.USER, listOf(ContentBlock.Text("请继续迁移"))))
        val broker = LocalContextBroker(object : LocalContextIndex {
            override fun searchActiveMemories(queryTerms: Set<String>, scope: ContextRetrievalScope, limit: Int) = listOf(LocalContextIndexHit("m", "记忆", "迁移偏好", "先完整备份", 1L, 0.0))
            override fun searchActiveKnowledge(queryTerms: Set<String>, scope: ContextRetrievalScope, limit: Int) = emptyList<LocalContextIndexHit>()
            override fun searchActiveHistory(queryTerms: Set<String>, scope: ContextRetrievalScope, limit: Int) = emptyList<LocalContextIndexHit>()
            override fun activeKnowledgeCount() = 0
            override fun status() = LocalContextIndexStatus.AVAILABLE
        })

        val result = broker.assemble(current, "请继续迁移", policy = LocalContextBroker.RetrievalPolicy(includeRelevantMemory = false))
        val outbound = result.messages.joinToString("\n") { it.text }
        assertFalse(outbound.contains("先完整备份"))
        assertTrue(outbound.contains("请继续迁移"))
    }
}

package com.nanzhufeng.ai.ai

import com.nanzhufeng.ai.domain.AssistantExperienceSettings
import com.nanzhufeng.ai.domain.ConversationStyle
import com.nanzhufeng.ai.domain.ModelCapabilities
import com.nanzhufeng.ai.domain.ModelHealth
import com.nanzhufeng.ai.domain.ProviderId
import com.nanzhufeng.ai.domain.ResolvedModel
import com.nanzhufeng.ai.domain.definition
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationStyleRequestContractsTest {
    @Test
    fun `every direct provider serializes the selected style as a system instruction`() {
        val adapters: List<Pair<ProviderId, ChatProviderAdapter>> = listOf(
            ProviderId.OPENROUTER to OpenRouterChatAdapter(),
            ProviderId.QWEN to QwenChatAdapter(),
            ProviderId.DEEPSEEK to DeepSeekChatAdapter(),
            ProviderId.ZHIPU to ZhipuChatAdapter(),
        )

        ConversationStyle.selectable.forEach { style ->
            val instruction = AssistantExperienceSettings(conversationStyle = style).modelInstruction().orEmpty()
            adapters.forEach { (providerId, adapter) ->
                val prepared = adapter.prepare(
                    model(providerId),
                    listOf("system" to instruction, "user" to "继续"),
                    emptyList(),
                    stream = false,
                ) as ChatAdapterPrepareResult.Ready

                assertTrue("$providerId lost ${style.persistedId}", prepared.jsonBody.contains(style.definition().label))
                assertEquals(1, prepared.jsonBody.split("对话方式：").size - 1)
            }
        }
    }

    @Test
    fun `ordinary retry attachment bridge and web routes share the current style projection`() {
        val source = File("src/main/java/com/nanzhufeng/ai/ai/NormalChatOpenRouterExecutor.kt").readText()
        val requestOne = source.substringAfter("private fun requestOne(").substringBefore("private fun completedMemoryCommandReply")
        val retry = source.substringAfter("fun retryLatestAttempt(").substringBefore("private fun completedResult")

        assertTrue(requestOne.contains("val globalExperience = loadAssistantExperienceSettings()"))
        assertTrue(requestOne.contains("resolveConversationStyle(conversationId, globalExperience.conversationStyle)"))
        assertTrue(requestOne.contains("AutomaticWebSearchPolicy.requestOptions("))
        assertTrue(requestOne.contains("attachmentBridge.resolve("))
        assertTrue(requestOne.contains("systemFactForRequest(requestOptions, experience"))
        assertTrue(requestOne.contains("listOf(ChatHistoryMessage(\"system\", systemFact))"))
        assertTrue(requestOne.contains("adapter.prepareContinuation(resolvedModel, protocolMessages"))
        assertTrue(requestOne.indexOf("val globalExperience = loadAssistantExperienceSettings()") < requestOne.indexOf("attachmentBridge.resolve("))
        assertTrue(requestOne.indexOf("attachmentBridge.resolve(") < requestOne.indexOf("systemFactForRequest(requestOptions, experience"))
        assertTrue(retry.contains("requestOne("))
        assertEquals(1, source.split("experience.modelInstruction(isFirstAssistantReply)").size - 1)
        assertTrue(source.contains("val effectiveStyle = experience.conversationStyle.effective()"))
        assertFalse(source.contains("experience.conversationStyle != com.nanzhufeng.ai.domain.ConversationStyle.DEFAULT"))
    }

    private fun model(providerId: ProviderId) = ResolvedModel(
        providerId = providerId,
        modelId = when (providerId) {
            ProviderId.OPENROUTER -> "openai/gpt-test"
            ProviderId.QWEN -> "qwen-test"
            ProviderId.DEEPSEEK -> "deepseek-test"
            ProviderId.ZHIPU -> "glm-test"
            ProviderId.MOCK -> "mock"
        },
        displayName = "test",
        capabilities = ModelCapabilities(true, false, false, false),
        contextWindowTokens = 8_192,
        health = ModelHealth.AVAILABLE,
        metadataUpdatedAt = null,
        healthCheckedAt = null,
    )
}

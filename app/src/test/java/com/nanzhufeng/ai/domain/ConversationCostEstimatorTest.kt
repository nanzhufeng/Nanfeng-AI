package com.nanzhufeng.ai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ConversationCostEstimatorTest {
    @Test fun `every current product model has a token-only local estimate`() {
        val usage = ProviderUsage(inputTokens = 1_443, outputTokens = 2_405)
        val modelIds = listOf(
            "anthropic/claude-fable-5",
            "anthropic/claude-opus-5",
            "anthropic/claude-sonnet-5",
            "anthropic/claude-haiku-4.5",
            "openai/gpt-5.6-sol",
            "openai/gpt-5.6-terra",
            "openai/gpt-5.6-luna",
            "google/gemini-3.7-flash",
            "qwen3.7-plus",
            "qwen3.8-max",
            "qwen3.6-flash",
            "deepseek-v4-pro",
        )

        modelIds.forEach { modelId ->
            assertNotNull("missing local estimate for $modelId", ConversationCostEstimator.estimate(modelId, usage))
        }
    }

    @Test fun `qwen plus uses the published lower input tier for the reported screenshot tokens`() {
        val estimate = requireNotNull(ConversationCostEstimator.estimate("qwen3.7-plus", ProviderUsage(1_443, 2_405)))

        assertEquals(ConversationCostEstimator.QWEN_PRICE_VERSION, estimate.priceVersion)
        assertEquals(3_046L, estimate.totalMicros)
    }

    @Test fun `deepseek estimate uses cache tokens when the provider reports them`() {
        val estimate = requireNotNull(ConversationCostEstimator.estimate(
            "deepseek-v4-pro",
            ProviderUsage(inputTokens = 1_000_000, outputTokens = 0, cachedInputTokens = 400_000),
        ))

        assertEquals(ConversationCostEstimator.DEEPSEEK_PRICE_VERSION, estimate.priceVersion)
        assertEquals(262_450L, estimate.totalMicros)
    }

    @Test fun `a historical token-only attribution is projected as a clearly labelled estimate`() {
        val projected = AssistantResponseModelAttribution(
            assistantMessageId = MessageNodeId("historical-qwen"),
            attemptId = NormalChatSendAttemptId("historical-qwen-attempt"),
            providerId = ProviderId.QWEN,
            receiverProviderId = ProviderId.QWEN,
            modelId = "qwen3.7-plus",
            modelDisplayName = "Qwen3.7-Plus",
            recordedAt = java.time.Instant.EPOCH,
            usage = ProviderUsage(1_443, 2_405),
        ).withAvailableLocalCostEstimate()

        assertEquals(ConversationCostSource.LOCAL_ESTIMATE, projected.costSource)
        assertEquals(3_046L, projected.cost.totalMicros)
        assertEquals("≈ \$0.003046（估算）", projected.footerCostLabel())
    }
}

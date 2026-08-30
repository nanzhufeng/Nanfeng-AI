package com.nanzhufeng.ai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class ConversationCostEstimatorTest {
    @Test fun `every model with a verified local price has a token-only local estimate`() {
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
            "deepseek-v4-flash",
            "glm-5.3-flash",
        )

        modelIds.forEach { modelId ->
            assertNotNull("missing local estimate for $modelId", ConversationCostEstimator.estimate(modelId, usage))
        }
    }

    @Test fun `GLM Flash uses the official time-bounded promotion then published standard rate`() {
        val usage = ProviderUsage(inputTokens = 1_000_000, outputTokens = 0, cachedInputTokens = 400_000)
        val promotion = requireNotNull(ConversationCostEstimator.estimate("glm-5.3-flash", usage, Instant.parse("2026-08-29T00:00:00Z")))
        val standard = requireNotNull(ConversationCostEstimator.estimate("glm-5.3-flash", usage, Instant.parse("2026-09-01T00:00:00Z")))

        assertEquals(ConversationCostEstimator.ZHIPU_GLM_5_3_FLASH_PROMOTION_PRICE_VERSION, promotion.priceVersion)
        assertEquals("CNY", promotion.currencyCode)
        assertEquals(286_000L, promotion.totalMicros)
        assertEquals(ConversationCostEstimator.ZHIPU_GLM_5_3_FLASH_STANDARD_PRICE_VERSION, standard.priceVersion)
        assertEquals(572_000L, standard.totalMicros)
    }

    @Test fun `GLM flagship does not fabricate a China direct price while token attribution remains available`() {
        assertNull(ConversationCostEstimator.estimate("glm-5.3", ProviderUsage(inputTokens = 1_443, outputTokens = 2_405)))
    }

    @Test fun `qwen plus uses the published lower input tier for the reported screenshot tokens`() {
        val estimate = requireNotNull(ConversationCostEstimator.estimate("qwen3.7-plus", ProviderUsage(1_443, 2_405)))

        assertEquals(ConversationCostEstimator.QWEN_PRICE_VERSION, estimate.priceVersion)
        assertEquals(3_046L, estimate.totalMicros)
    }

    @Test fun `qwen max uses Beijing CNY rates for the reported screenshot tokens`() {
        val estimate = requireNotNull(ConversationCostEstimator.estimate(
            "qwen3.8-max",
            ProviderUsage(inputTokens = 83_273, outputTokens = 17_242, cachedInputTokens = 0),
        ))

        assertEquals(ConversationCostEstimator.QWEN_PRICE_VERSION, estimate.priceVersion)
        assertEquals("CNY", estimate.currencyCode)
        assertEquals(1_619_988L, estimate.totalMicros)
    }

    @Test fun `deepseek estimate uses official off peak rates and cache tokens`() {
        val offPeak = requireNotNull(ConversationCostEstimator.estimate(
            "deepseek-v4-pro",
            ProviderUsage(inputTokens = 1_000_000, outputTokens = 0, cachedInputTokens = 400_000),
            Instant.parse("2026-08-30T10:00:00Z"),
        ))

        assertEquals(ConversationCostEstimator.DEEPSEEK_OFF_PEAK_PRICE_VERSION, offPeak.priceVersion)
        assertEquals(404_800L, offPeak.totalMicros)
    }

    @Test fun `deepseek estimate uses official peak rates at the same UI boundary`() {
        val peak = requireNotNull(ConversationCostEstimator.estimate(
            "deepseek-v4-pro",
            ProviderUsage(inputTokens = 1_000_000, outputTokens = 0, cachedInputTokens = 400_000),
            Instant.parse("2026-08-30T06:00:00Z"),
        ))

        assertEquals(ConversationCostEstimator.DEEPSEEK_PEAK_PRICE_VERSION, peak.priceVersion)
        assertEquals(809_600L, peak.totalMicros)
    }

    @Test fun `deepseek flash estimate uses published off peak cache and token rates`() {
        val offPeak = requireNotNull(ConversationCostEstimator.estimate(
            "deepseek-v4-flash",
            ProviderUsage(inputTokens = 1_000_000, outputTokens = 0, cachedInputTokens = 400_000),
            Instant.parse("2026-08-30T10:00:00Z"),
        ))

        assertEquals(ConversationCostEstimator.DEEPSEEK_OFF_PEAK_PRICE_VERSION, offPeak.priceVersion)
        assertEquals(134_800L, offPeak.totalMicros)
    }

    @Test fun `historical DeepSeek estimates keep the pre peak pricing version`() {
        val historical = requireNotNull(ConversationCostEstimator.estimate(
            "deepseek-v4-pro",
            ProviderUsage(inputTokens = 1_000_000, outputTokens = 0, cachedInputTokens = 400_000),
            Instant.parse("2026-08-16T15:59:59Z"),
        ))

        assertEquals(ConversationCostEstimator.DEEPSEEK_LEGACY_PRICE_VERSION, historical.priceVersion)
        assertEquals(262_450L, historical.totalMicros)
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
        assertEquals("≈ ¥0.02047（估算）", projected.footerCostLabel())
    }

    @Test fun `an existing GLM reply is backfilled with the rate from its recorded time`() {
        val projected = AssistantResponseModelAttribution(
            assistantMessageId = MessageNodeId("historical-glm"),
            attemptId = NormalChatSendAttemptId("historical-glm-attempt"),
            providerId = ProviderId.ZHIPU,
            receiverProviderId = ProviderId.ZHIPU,
            modelId = "glm-5.3-flash",
            modelDisplayName = "GLM-5.3 Flash",
            recordedAt = Instant.parse("2026-08-29T00:00:00Z"),
            usage = ProviderUsage(inputTokens = 1_443, outputTokens = 2_405),
        ).withAvailableLocalCostEstimate()

        assertEquals(ConversationCostEstimator.ZHIPU_GLM_5_3_FLASH_PROMOTION_PRICE_VERSION, projected.cost.priceVersion)
        assertEquals("CNY", projected.cost.currencyCode)
        assertEquals(3_944L, projected.cost.totalMicros)
        assertEquals("≈ ¥0.003944（估算）", projected.footerCostLabel())
    }
}

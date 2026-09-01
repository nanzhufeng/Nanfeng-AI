package com.nanzhufeng.ai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenRouterColdStartResolverContractsTest {
    @Test fun `cold start retains exact standard Terra text egress when the public catalog is temporarily unavailable`() {
        val result = resolver().resolve(ModelPresetId.GPT_5_6_TERRA) as? ResolvedModelResult.Resolved

        assertEquals("openai/gpt-5.6-terra", result?.model?.modelId)
        assertEquals("GPT-5.6 Terra", result?.model?.displayName)
        assertTrue(result?.model?.capabilities?.supportsText == true)
        assertTrue(result?.model?.capabilities?.supportsVision == false)
    }

    @Test fun `cold start fallback never invents a direct provider model`() {
        val result = resolver().resolve(ModelPresetId.DEEPSEEK_V4_PRO)

        assertTrue(result is ResolvedModelResult.Unavailable)
    }

    @Test fun `cold start prefers exact bundled Grok 4 point 6 capabilities over a text only emergency identity`() {
        val bundled = ResolvedModel(
            providerId = ProviderId.OPENROUTER,
            modelId = "x-ai/grok-4.6",
            displayName = "Grok 4.6 High",
            capabilities = ModelCapabilities(supportsText = true, supportsVision = true, supportsPdf = true, supportsStreaming = true),
            contextWindowTokens = 500_000,
            health = ModelHealth.UNKNOWN,
            metadataUpdatedAt = null,
            healthCheckedAt = null,
        )
        val result = UnifiedModelResolver(
            registry = InMemoryVersionedModelRegistry(),
            profileDirectory = object : ModelProfileDirectory {
                override fun profile(presetId: ModelPresetId) = bundled.takeIf { presetId == ModelPresetId.GROK_4_6_HIGH }
                override fun profiles() = mapOf(ModelPresetId.GROK_4_6_HIGH to bundled)
                override fun replaceIfNewer(profiles: Map<ModelPresetId, ResolvedModel>) = false
            },
            healthStore = object : ModelHealthStore {
                override fun observation(providerId: ProviderId, presetId: ModelPresetId) = null
                override fun record(providerId: ProviderId, presetId: ModelPresetId, observation: ModelHealthObservation) = Unit
            },
        ).resolve(ModelPresetId.GROK_4_6_HIGH) as ResolvedModelResult.Resolved

        assertEquals("x-ai/grok-4.6", result.model.modelId)
        assertTrue(result.model.capabilities.supportsVision)
        assertTrue(result.model.capabilities.supportsPdf)
    }

    private fun resolver() = UnifiedModelResolver(
        registry = InMemoryVersionedModelRegistry(),
        profileDirectory = object : ModelProfileDirectory {
            override fun profile(presetId: ModelPresetId): ResolvedModel? = null
            override fun profiles(): Map<ModelPresetId, ResolvedModel> = emptyMap()
            override fun replaceIfNewer(profiles: Map<ModelPresetId, ResolvedModel>): Boolean = false
        },
        healthStore = object : ModelHealthStore {
            override fun observation(providerId: ProviderId, presetId: ModelPresetId): ModelHealthObservation? = null
            override fun record(providerId: ProviderId, presetId: ModelPresetId, observation: ModelHealthObservation) = Unit
        },
    )
}

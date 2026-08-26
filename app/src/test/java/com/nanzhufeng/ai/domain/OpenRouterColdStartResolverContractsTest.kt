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

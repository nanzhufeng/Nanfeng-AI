package com.nanzhufeng.ai.data

import com.nanzhufeng.ai.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class ModelProfileUpgradeTest {
    @Test fun `fixed OpenRouter identity survives cached route corruption and current health still survives reopen`() {
        for ((preset, id, name) in listOf(
            Triple(ModelPresetId.GPT_6_1_TERRA, "openai/gpt-6.1-terra", "GPT-6.1 Terra"),
            Triple(ModelPresetId.MERCURY_3, "inception/mercury-3", "Mercury 3"),
            Triple(ModelPresetId.GEMINI_4_ARGON, "google/gemini-4-argon", "Gemini 4 Argon"),
        )) {
            val seed=ResolvedModel(ProviderId.OPENROUTER,id,name,ModelCapabilities(true,false,true),1000000,ModelHealth.UNKNOWN,Instant.parse("2026-10-20T00:00:00Z"),null,8192)
            val invalid=seed.copy(modelId="openai/gpt-6-luna",health=ModelHealth.UNAVAILABLE,metadataUpdatedAt=Instant.parse("2026-10-21T00:00:00Z"))
            val merged=mergeCachedProfiles(mapOf(preset to seed),mapOf(preset to invalid))
            val directory=object:ModelProfileDirectory {
                override fun profile(presetId:ModelPresetId)=merged[presetId]
                override fun profiles()=merged
                override fun replaceIfNewer(profiles:Map<ModelPresetId,ResolvedModel>)=false
            }
            val health=object:ModelHealthStore {
                override fun observation(providerId:ProviderId,presetId:ModelPresetId):ModelHealthObservation?=null
                override fun record(providerId:ProviderId,presetId:ModelPresetId,observation:ModelHealthObservation)=Unit
            }
            val resolved=UnifiedModelResolver(InMemoryVersionedModelRegistry(),directory,health).resolve(preset) as ResolvedModelResult.Resolved
            assertEquals(id,resolved.model.modelId)
            assertEquals(ModelHealth.UNKNOWN,resolved.model.health)
            val current=seed.copy(health=ModelHealth.DEGRADED,metadataUpdatedAt=Instant.parse("2026-10-21T00:00:00Z"))
            assertEquals(current,mergeCachedProfiles(mapOf(preset to seed),mapOf(preset to current))[preset])
        }
    }

    @Test fun `cached Opus legacy alias cannot undo the verified bundled route`() {
        val seed=ResolvedModel(ProviderId.RELAYCORE_CLAUDE,"claude-opus-5.5","Opus 5.5 · Kiro正价",ModelCapabilities(true,false,true),200000,ModelHealth.UNKNOWN,Instant.parse("2026-10-11T00:00:00Z"),null,8192)
        val cached=seed.copy(modelId="claude-opus-5-5",metadataUpdatedAt=Instant.parse("2026-10-12T00:00:00Z"),health=ModelHealth.DEGRADED)
        assertEquals(seed,mergeCachedProfiles(mapOf(ModelPresetId.RELAYCORE_OPUS_5_5 to seed),mapOf(ModelPresetId.RELAYCORE_OPUS_5_5 to cached))[ModelPresetId.RELAYCORE_OPUS_5_5])
    }

    @Test fun `retired cached Flash ID and health cannot mask upgraded seed while current metadata survives reopen merge`() {
        val seed = ResolvedModel(ProviderId.DEEPSEEK, "deepseek-flash", "DeepSeek V4.1 Flash",
            ModelCapabilities(true, true, true), 1_000_000, ModelHealth.UNKNOWN,
            Instant.parse("2026-09-10T00:00:00Z"), null, 384_000)
        val preset = ModelPresetId.DEEPSEEK_V4_FLASH
        val stale = seed.copy(modelId = "deepseek-v4-flash", displayName = "DeepSeek V4 Flash", health = ModelHealth.UNAVAILABLE,
            metadataUpdatedAt = Instant.parse("2026-09-11T00:00:00Z"))
        assertEquals(seed, mergeCachedProfiles(mapOf(preset to seed), mapOf(preset to stale))[preset])
        val fresh = seed.copy(metadataUpdatedAt = Instant.parse("2026-09-11T00:00:00Z"))
        assertEquals(fresh, mergeCachedProfiles(mapOf(preset to seed), mapOf(preset to fresh))[preset])
    }
}

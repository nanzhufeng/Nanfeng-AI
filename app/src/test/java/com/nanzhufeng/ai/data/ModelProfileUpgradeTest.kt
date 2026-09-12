package com.nanzhufeng.ai.data

import com.nanzhufeng.ai.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class ModelProfileUpgradeTest {
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

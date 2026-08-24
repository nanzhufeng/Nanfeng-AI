package com.nanzhufeng.ai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class CapabilityAwareAutoModelRouterTest {
    @Test fun `auto priorities are read from the central model catalog rather than router model IDs`() {
        val source = java.io.File("src/main/java/com/nanzhufeng/ai/domain/ChatModelRouting.kt").readText()
        val autoRouter = source.substringAfter("object AutoModelRouter {").substringBefore("class CapabilityAwareAutoModelRouter")
        assertTrue(autoRouter.contains("NanfengModelServiceCatalog.autoRoutingCandidates"))
        assertEquals(0, Regex("ModelPresetId\\.[A-Z0-9_]+").findAll(autoRouter).count())
    }

    @Test fun `auto skips configured model that cannot accept the actual PDF`() {
        val resolver = fixtureResolver(
            ModelPresetId.DEEPSEEK_V4_PRO to profile(ModelPresetId.DEEPSEEK_V4_PRO, ProviderId.DEEPSEEK, pdf = false),
            ModelPresetId.QWEN_3_7_PLUS to profile(ModelPresetId.QWEN_3_7_PLUS, ProviderId.QWEN, pdf = true),
        )

        val result = CapabilityAwareAutoModelRouter(resolver).resolve(
            AutoRoutingFacts(hasImageVideoOrPdf = true, requiresPdf = true),
        ) { it == ModelPresetId.DEEPSEEK_V4_PRO || it == ModelPresetId.QWEN_3_7_PLUS }

        assertEquals(ModelPresetId.QWEN_3_7_PLUS, result)
    }

    @Test fun `unavailable model is not selected even when it advertises the required capability`() {
        val resolver = fixtureResolver(
            ModelPresetId.GEMINI_3_7_FLASH to profile(ModelPresetId.GEMINI_3_7_FLASH, ProviderId.OPENROUTER, video = true, health = ModelHealth.UNAVAILABLE),
            ModelPresetId.QWEN_3_7_PLUS to profile(ModelPresetId.QWEN_3_7_PLUS, ProviderId.QWEN, video = true),
        )

        val result = CapabilityAwareAutoModelRouter(resolver).resolve(
            AutoRoutingFacts(hasImageVideoOrPdf = true, requiresVideo = true),
        ) { it == ModelPresetId.GEMINI_3_7_FLASH || it == ModelPresetId.QWEN_3_7_PLUS }

        assertEquals(ModelPresetId.QWEN_3_7_PLUS, result)
    }

    private fun fixtureResolver(vararg entries: Pair<ModelPresetId, ResolvedModel>) = ModelResolver { preset ->
        entries.toMap()[preset]?.let(ResolvedModelResult::Resolved)
            ?: ResolvedModelResult.Unavailable("fixture missing")
    }

    private fun profile(
        preset: ModelPresetId,
        provider: ProviderId,
        pdf: Boolean = false,
        video: Boolean = false,
        health: ModelHealth = ModelHealth.AVAILABLE,
    ) = ResolvedModel(
        providerId = provider, modelId = preset.name.lowercase(), displayName = preset.name,
        capabilities = ModelCapabilities(true, true, true, supportsPdf = pdf, supportsVideo = video),
        contextWindowTokens = null, health = health, metadataUpdatedAt = Instant.EPOCH, healthCheckedAt = Instant.EPOCH,
    )
}

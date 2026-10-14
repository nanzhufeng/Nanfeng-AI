package com.nanzhufeng.ai.ai

import com.nanzhufeng.ai.domain.*
import org.junit.Assert.*
import org.junit.Test

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
class OctoberModelUpgradeTest {
    @Test fun `current standard selections reject alternate cached API identities`() {
        val catalog = com.nanzhufeng.ai.data.OpenRouterCatalogJsonParser.parse(java.io.File("../protocol/fixtures/model-upgrade-20261009.json").readBytes())
        val snapshot = OpenRouterRegistrySnapshotVerifier().verify(OpenRouterCatalogResponse(catalog, null), java.time.Instant.parse("2026-10-09T00:00:00Z"))!!
        for ((preset, original, alternate) in listOf(
            Triple(ModelPresetId.GPT_6_1_SOL, "openai/gpt-6.1-sol", "openai/gpt-6.1-sol-pro"),
            Triple(ModelPresetId.GEMINI_3_8_FLASH, "google/gemini-3.8-flash", "google/gemini-3.8-flash:priority"),
        )) {
            val corrupted = snapshot.copy(
                models = snapshot.models.map { if (it.id == original) it.copy(id = alternate) else it },
                presetMappings = snapshot.presetMappings.map { if (it.presetId == preset) it.copy(modelId = alternate) else it },
            )
            assertTrue(InMemoryVersionedModelRegistry(listOf(corrupted)).resolve(ProviderId.OPENROUTER, preset) is ModelRegistryResolution.Rejected)
        }
    }
    @Test fun `live October catalog resolves exact new IDs without requiring retired models`() {
        val bytes = java.io.File("../protocol/fixtures/model-upgrade-20261009.json").readBytes()
        val parsed = com.nanzhufeng.ai.data.OpenRouterCatalogJsonParser.parse(bytes)
        val gemini = parsed.single { it.id == "google/gemini-3.8-flash" }
        val catalog = parsed + gemini.copy(id = "google/gemini-3.8-flash:priority")
        val snapshot = OpenRouterRegistrySnapshotVerifier().verify(OpenRouterCatalogResponse(catalog, null), java.time.Instant.parse("2026-10-09T00:00:00Z"))!!
        for ((preset, apiId) in listOf(
            "CLAUDE_OPUS_5_5" to "anthropic/claude-opus-5.5",
            "CLAUDE_SONNET_5_5" to "anthropic/claude-sonnet-5.5",
            "CLAUDE_HAIKU_5_5" to "anthropic/claude-haiku-5.5",
            "GPT_6_1_SOL" to "openai/gpt-6.1-sol",
            "GPT_6_LUNA" to "openai/gpt-6-luna",
            "GEMINI_3_8_FLASH" to "google/gemini-3.8-flash",
        )) assertEquals(apiId, snapshot.modelFor(ModelPresetId.valueOf(preset))?.id)
        assertNull(snapshot.modelFor(ModelPresetId.CLAUDE_OPUS_5))
        assertEquals("Opus 5", composerModelShortNameForUser("Claude Opus 5"))
    }
    @Test fun `current menus contain new daily models and Sol immediately before DeepSeek Pro`() {
        val daily = ComposerModelRoutingCatalog.daily.flatMap { it.routes }.map { it.name }
        assertTrue(daily.containsAll(listOf("CLAUDE_SONNET_5_5", "CLAUDE_HAIKU_5_5", "GPT_6_LUNA", "GEMINI_3_8_FLASH")))
        assertFalse(daily.contains("GPT_5_6_TERRA"))
        val deep = ComposerModelRoutingCatalog.deep.flatMap { it.routes }.map { it.name }
        assertEquals(deep.indexOf("DEEPSEEK_V4_PRO") - 1, deep.indexOf("GPT_6_1_SOL"))
        assertFalse(deep.contains("GPT_5_6_SOL"))
        assertTrue(deep.contains("CLAUDE_OPUS_5_5"))
    }

    @Test fun `new Claude efforts apply to explicit choices and Auto presets without losing web search`() {
        for ((id, effort) in listOf("CLAUDE_SONNET_5_5" to "high", "CLAUDE_OPUS_5_5" to "medium")) {
            val preset = ModelPresetId.valueOf(id)
            val choice = ComposerModelRoutingCatalog.choices.single { preset in it.routes }
            assertEquals(effort, OpenRouterDeepReasoningPolicy.forChoice(choice).reasoningEffort?.wireValue)
            val options = OpenRouterDeepReasoningPolicy.forPreset(preset, ChatRequestOptions(OfficialWebSearchRoute.OPENROUTER_SERVER_TOOL))
            assertEquals(effort, options.reasoningEffort?.wireValue)
            assertTrue(options.liveWebSearch)
        }
    }

    @Test fun `removed presets remain historical identities but cannot be selected or used by Auto`() {
        val removed = setOf("CLAUDE_OPUS_5", "CLAUDE_SONNET_5", "CLAUDE_HAIKU_4_5", "GPT_5_6_SOL", "GPT_5_6_TERRA", "GPT_5_6_LUNA")
        assertTrue(NanfengModelServiceCatalog.chatPresets.none { it.id.name in removed })
        for (facts in listOf(AutoRoutingFacts(), AutoRoutingFacts(requiresComplexReasoning = true), AutoRoutingFacts(hasAttachment = true))) {
            assertTrue(AutoModelRouter.candidates(facts).none { it.name in removed })
        }
    }
}

package com.nanzhufeng.ai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
class CapabilityAwareAutoModelRouterTest {
    @Test fun `Auto plans prioritize Relay and domestic before OpenRouter without changing manual routes`() {
        val fixture = org.json.JSONObject(java.io.File("../protocol/fixtures/auto-routing-rc-domestic-20261010.json").readText())
        for ((task, facts) in listOf("DEFAULT_TEXT" to AutoRoutingFacts(), "COMPLEX_REASONING" to AutoRoutingFacts(requiresComplexReasoning=true), "ATTACHMENT" to AutoRoutingFacts(hasAttachment=true))) {
            val expected=fixture.getJSONObject("plans").getJSONArray(task)
            assertEquals((0 until expected.length()).map { ModelPresetId.valueOf(expected.getString(it)) },AutoModelRouter.candidates(facts))
            var foundOpenRouter=false
            for (preset in AutoModelRouter.candidates(facts)) {
                if(NanfengModelServiceCatalog.providerFor(preset)==ProviderId.OPENROUTER) foundOpenRouter=true
                else assertTrue("non-OpenRouter route must precede fallback",!foundOpenRouter)
            }
        }
        val snapshot=NormalChatRoutingSnapshot(emptyMap(),emptySet(),true)
        assertEquals(listOf(ModelPresetId.CLAUDE_OPUS_5_5),snapshot.presets(ConversationDraft(updatedAt=Instant.EPOCH),"logical:deep:claude-opus"))
    }
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
            AutoRoutingFacts(hasAttachment = true, requiresPdf = true),
        ) { it == ModelPresetId.DEEPSEEK_V4_PRO || it == ModelPresetId.QWEN_3_7_PLUS }

        assertEquals(ModelPresetId.QWEN_3_7_PLUS, result)
    }

    @Test fun `unavailable model is not selected even when it advertises the required capability`() {
        val resolver = fixtureResolver(
            ModelPresetId.GEMINI_3_8_FLASH to profile(ModelPresetId.GEMINI_3_8_FLASH, ProviderId.OPENROUTER, video = true, health = ModelHealth.UNAVAILABLE),
            ModelPresetId.QWEN_3_7_PLUS to profile(ModelPresetId.QWEN_3_7_PLUS, ProviderId.QWEN, video = true),
        )

        val result = CapabilityAwareAutoModelRouter(resolver).resolve(
            AutoRoutingFacts(hasAttachment = true, requiresVideo = true),
        ) { it == ModelPresetId.GEMINI_3_8_FLASH || it == ModelPresetId.QWEN_3_7_PLUS }

        assertEquals(ModelPresetId.QWEN_3_7_PLUS, result)
    }

    @Test fun `auto prefers a known audio capable model but does not locally reject when no profile knows audio`() {
        val resolver = fixtureResolver(
            ModelPresetId.GPT_6_LUNA to profile(ModelPresetId.GPT_6_LUNA, ProviderId.OPENROUTER, audio = false),
            ModelPresetId.QWEN_3_7_PLUS to profile(ModelPresetId.QWEN_3_7_PLUS, ProviderId.QWEN, audio = true),
        )

        val routed = CapabilityAwareAutoModelRouter(resolver).resolve(
            AutoRoutingFacts(hasAttachment = true, requiresAudio = true),
        ) { it == ModelPresetId.GPT_6_LUNA || it == ModelPresetId.QWEN_3_7_PLUS }
        assertEquals(ModelPresetId.QWEN_3_7_PLUS, routed)
    }

    @Test fun `degraded primary is demoted behind a healthy compatible fallback`() {
        val resolver = fixtureResolver(
            ModelPresetId.DEEPSEEK_V4_FLASH to profile(
                ModelPresetId.DEEPSEEK_V4_FLASH,
                ProviderId.DEEPSEEK,
                health = ModelHealth.DEGRADED,
            ),
            ModelPresetId.GPT_6_LUNA to profile(ModelPresetId.GPT_6_LUNA, ProviderId.OPENROUTER),
        )

        val routed = CapabilityAwareAutoModelRouter(resolver).resolve(AutoRoutingFacts()) {
            it == ModelPresetId.DEEPSEEK_V4_FLASH || it == ModelPresetId.GPT_6_LUNA
        }

        assertEquals(ModelPresetId.GPT_6_LUNA, routed)
    }

    @Test fun `Auto filters missing search companion but keeps a manually chosen model`() {
        val flash=ModelPresetId.RELAYCORE_GEMINI_3_8_FLASH
        val domestic=ModelPresetId.DEEPSEEK_V4_FLASH
        val models=mapOf(flash to profile(flash,ProviderId.RELAYCORE_GEMINI),domestic to profile(domestic,ProviderId.DEEPSEEK))
        val draft=ConversationDraft(updatedAt=Instant.EPOCH)
        val snapshot=NormalChatRoutingSnapshot(models,models.keys,true,true)
        assertEquals(listOf(domestic),snapshot.presets(draft,null))
        assertEquals("Auto · DeepSeek V4.1 Flash",snapshot.autoLabel(draft))
        assertEquals(listOf(flash),snapshot.copy(webSearchEnabled=false).presets(draft,null))
        assertEquals(listOf(flash),snapshot.presets(draft,"logical:relaycore:RELAYCORE_GEMINI_3_8_FLASH"))
    }
    @Test fun `recorded Auto does not inherit another conversation manual selection`() {
        val global=P6GGlobalDefault(2,null,"logical:relaycore:RELAYCORE_GPT_6_ASTRA")
        assertEquals(null,effectiveComposerModelId(P6GConversationOverride(ConversationId("kept-auto"),1,null),global))
        assertEquals(global.modelId,effectiveComposerModelId(P6GConversationOverride(ConversationId("new"),0,null),global))
    }

    @Test fun `cached profile without usable provider cannot authorize send`() {
        val preset=ModelPresetId.RELAYCORE_GPT_6_1_SOL
        val snapshot=NormalChatRoutingSnapshot(mapOf(preset to profile(preset,ProviderId.RELAYCORE_GPT)),emptySet(),true)
        assertEquals(false,snapshot.canAuthorize(ConversationDraft(updatedAt=Instant.EPOCH),null))
        assertEquals("Auto · 等待可用模型",snapshot.autoLabel(ConversationDraft(updatedAt=Instant.EPOCH)))
        assertEquals(false,snapshot.canAuthorize(ConversationDraft(updatedAt=Instant.EPOCH),"logical:relaycore:RELAYCORE_GPT_6_1_SOL"))
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
        audio: Boolean = false,
        health: ModelHealth = ModelHealth.AVAILABLE,
    ) = ResolvedModel(
        providerId = provider, modelId = preset.name.lowercase(), displayName = preset.name,
        capabilities = ModelCapabilities(true, true, true, supportsPdf = pdf, supportsVideo = video, supportsAudio = audio),
        contextWindowTokens = null, health = health, metadataUpdatedAt = Instant.EPOCH, healthCheckedAt = Instant.EPOCH,
    )
}

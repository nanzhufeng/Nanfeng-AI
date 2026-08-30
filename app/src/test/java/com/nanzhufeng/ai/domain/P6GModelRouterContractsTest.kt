package com.nanzhufeng.ai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class P6GModelRouterContractsTest {
    private val router = P6GModelRouter()
    private fun candidate(family: P6GProviderFamily, id: String, cost: Long?, available: Boolean = true, capabilities: Set<P6GCapability> = setOf(P6GCapability.TEXT)) =
        P6GCatalogCandidate(family, family.name.lowercase(), id, "fixture $id", setOf(P6GModelTier.BALANCED), capabilities, available, cost, 1)

    @Test fun `exact cache and local safety both stop before catalog selection`() {
        val catalog = listOf(candidate(P6GProviderFamily.ANTHROPIC, "anthropic.fixture", 3))
        assertEquals(P6GRouteReason.EXACT_CACHE_HIT, router.route(P6GRouteRequest(P6GModelTier.BALANCED, exactHistoricalCacheHit = true), catalog).reason)
        assertEquals(P6GRouteReason.LOCAL_SAFETY_GATE, router.route(P6GRouteRequest(P6GModelTier.BALANCED, localSafeRequired = true), catalog).reason)
    }

    @Test fun `manual override never falls through to auto`() {
        val decision = router.route(P6GRouteRequest(P6GModelTier.BALANCED, manualModelId = "missing"), listOf(candidate(P6GProviderFamily.ANTHROPIC, "anthropic.fixture", 3)))
        assertEquals(P6GRouteSource.REJECTED, decision.source)
        assertNull(decision.candidate)
    }

    @Test fun `auto prefers eligible anthropic and unknown cost fails closed`() {
        val catalog = listOf(candidate(P6GProviderFamily.OPENAI, "openai.fixture", 1), candidate(P6GProviderFamily.ANTHROPIC, "anthropic.fixture", 9))
        assertEquals("anthropic.fixture", router.route(P6GRouteRequest(P6GModelTier.BALANCED), catalog).candidate?.modelId)
        assertEquals(P6GRouteReason.UNKNOWN_COST_REQUIRES_CONFIRMATION, router.route(P6GRouteRequest(P6GModelTier.BALANCED), listOf(candidate(P6GProviderFamily.ANTHROPIC, "unknown.fixture", null))).reason)
    }

    @Test fun `typed owner persists global revision and conversation override before auto`() {
        val catalog = listOf(candidate(P6GProviderFamily.OPENAI, "openai.fixture", 1), candidate(P6GProviderFamily.ANTHROPIC, "anthropic.fixture", 9))
        val store = object : P6GModelSelectionStore {
            var global = P6GGlobalDefault(0, null); val overrides = mutableMapOf<String, P6GConversationOverride>(); val metadata = mutableListOf<P6GRouteMetadata>()
            override fun readCatalog() = P6GLocalCatalogSnapshot("fixture-v1", 1, catalog)
            override fun saveCatalog(value: P6GLocalCatalogSnapshot) = true
            override fun readGlobalDefault() = global
            override fun readConversationOverride(conversationId: ConversationId) = overrides[conversationId.value] ?: P6GConversationOverride(conversationId, 0, null)
            override fun saveGlobalDefault(value: P6GGlobalDefault) = true.also { global = value }
            override fun saveConversationOverride(value: P6GConversationOverride) = true.also { overrides[value.conversationId.value] = value }
            override fun saveComposerModelSelection(conversation: P6GConversationOverride, global: P6GGlobalDefault) = true.also {
                overrides[conversation.conversationId.value] = conversation
                this.global = global
            }
            override fun appendRouteMetadata(value: P6GRouteMetadata) = true.also { metadata += value }
        }
        val owner = P6GModelSelectionOwner(store, router); val conversation = ConversationId("p6g-fixture")
        assertEquals(P6GSelectionMutationResult.Applied(1), owner.setGlobalDefault(P6GModelTier.BALANCED, 0))
        assertEquals(P6GSelectionMutationResult.Applied(1), owner.setConversationManualOverride(conversation, "openai.fixture", 0))
        assertEquals("openai.fixture", owner.evaluate(conversation, P6GRouteRequest(P6GModelTier.FAST)).candidate?.modelId)
        assertEquals(P6GRouteReason.MANUAL_OVERRIDE, store.metadata.single().reason)
        assertTrue(store.metadata.single().rejectedCandidates.isEmpty())
    }

    @Test fun `composer selection persists manual choice for the next conversation while auto remains content-routed`() {
        val catalog = listOf(candidate(P6GProviderFamily.OPENAI, "openai.fixture", 1), candidate(P6GProviderFamily.ANTHROPIC, "anthropic.fixture", 9))
        val store = object : P6GModelSelectionStore {
            var global = P6GGlobalDefault(0, null); val overrides = mutableMapOf<String, P6GConversationOverride>()
            override fun readCatalog() = P6GLocalCatalogSnapshot("fixture-v1", 1, catalog)
            override fun saveCatalog(value: P6GLocalCatalogSnapshot) = true
            override fun readGlobalDefault() = global
            override fun readConversationOverride(conversationId: ConversationId) = overrides[conversationId.value] ?: P6GConversationOverride(conversationId, 0, null)
            override fun saveGlobalDefault(value: P6GGlobalDefault) = true.also { global = value }
            override fun saveConversationOverride(value: P6GConversationOverride) = true.also { overrides[value.conversationId.value] = value }
            override fun saveComposerModelSelection(conversation: P6GConversationOverride, global: P6GGlobalDefault) = true.also {
                overrides[conversation.conversationId.value] = conversation
                this.global = global
            }
            override fun appendRouteMetadata(value: P6GRouteMetadata) = true
        }
        val owner = P6GModelSelectionOwner(store, router)
        val first = ConversationId("p6g-first")
        val next = ConversationId("p6g-next")

        assertEquals(P6GSelectionMutationResult.Applied(1), owner.setComposerModelSelection(first, "openai.fixture", 0, 0))
        assertEquals("openai.fixture", store.global.modelId)
        assertEquals("openai.fixture", owner.evaluate(next, P6GRouteRequest(P6GModelTier.BALANCED)).candidate?.modelId)
        assertEquals(P6GRouteSource.MANUAL_OVERRIDE, store.global.let { owner.evaluate(next, P6GRouteRequest(P6GModelTier.BALANCED)).source })

        assertEquals(P6GSelectionMutationResult.Applied(1), owner.setComposerModelSelection(next, null, 0, 1))
        assertNull(store.global.modelId)
        assertEquals(P6GRouteSource.AUTO, owner.evaluate(next, P6GRouteRequest(P6GModelTier.BALANCED)).source)
    }

    @Test fun `conversation picker persists only the approved logical model choices`() {
        assertEquals(
            ComposerModelRoutingCatalog.choices.map { it.label },
            P6GCuratedModelCatalog.snapshot().candidates.map { it.displayName },
        )
        assertEquals(
            setOf(ComposerModelSlot.AUTO, ComposerModelSlot.DAILY, ComposerModelSlot.DEEP),
            ComposerModelRoutingCatalog.choices.map { it.slot }.toSet(),
        )
        assertEquals(ComposerModelRoutingCatalog.auto, ComposerModelRoutingCatalog.choice("logical:compare:gpt-claude"))
        assertEquals(ComposerModelRoutingCatalog.auto, ComposerModelRoutingCatalog.choice("logical:media:gemini-flash"))
    }

    @Test fun `composer picker preserves the approved daily and deep ordering`() {
        assertEquals(
            listOf("Claude Sonnet 5", "DeepSeek V4 Flash", "GPT-5.6 Terra", "GLM-5.3 Flash", "Qwen3.7-Plus", "Gemini 3.7 Flash"),
            ComposerModelRoutingCatalog.daily.map { it.label },
        )
        assertEquals(
            listOf("Claude Fable 5", "Claude Opus 5", "DeepSeek V4 Pro", "GPT-5.6 Sol", "GLM-5.3", "Qwen3.8-Max"),
            ComposerModelRoutingCatalog.deep.map { it.label },
        )
    }

    @Test fun `composer uses compact labels while picker retains complete catalog names`() {
        assertEquals("Claude Fable 5", ComposerModelRoutingCatalog.deep[0].label)
        assertEquals("DeepSeek V4 Pro", ComposerModelRoutingCatalog.deep[2].label)
        assertEquals("GPT-5.6 Sol", ComposerModelRoutingCatalog.deep[3].label)
        assertEquals("Claude Sonnet 5", ComposerModelRoutingCatalog.daily[0].label)
        assertEquals("Fable 5", composerModelShortNameForUser(ComposerModelRoutingCatalog.deep[0].label))
        assertEquals("V4 Pro", composerModelShortNameForUser(ComposerModelRoutingCatalog.deep[2].label))
        assertEquals("5.6 Sol", composerModelShortNameForUser(ComposerModelRoutingCatalog.deep[3].label))
        assertEquals("Sonnet 5", composerModelShortNameForUser(ComposerModelRoutingCatalog.daily[0].label))
        assertEquals("3.7 Flash", composerModelShortNameForUser("Gemini 3.7 Flash"))
        assertEquals("GLM 5.3", composerModelShortNameForUser("GLM-5.3"))
        assertEquals("5.3 Flash", composerModelShortNameForUser("GLM-5.3 Flash"))
        assertEquals("V4 Pro", composerModelShortNameForUser("DeepSeek V4 Pro"))
        assertEquals("V4 Flash", composerModelShortNameForUser("DeepSeek V4 Flash"))
    }

    @Test fun `legacy provider and route decorations normalize to the curated model name`() {
        assertEquals("GPT-5.6 Terra", modelDisplayNameForUser("模型：GPT-5.6 Terra · OpenRouter"))
        assertEquals("Gemini 3.7 Flash", modelDisplayNameForUser("Google: Gemini 3.7 Flash · OpenRouter"))
        assertEquals("DeepSeek V4 Pro", modelDisplayNameForUser("模型：DeepSeek V4 Pro · 通义千问官方实时检索"))
    }

    @Test fun `automatic routing follows explicit default attachment and complex priorities`() {
        assertEquals(ModelPresetId.DEEPSEEK_V4_FLASH, AutoModelRouter.resolve(AutoRoutingFacts()))
        assertEquals(ModelPresetId.QWEN_3_7_PLUS, AutoModelRouter.resolve(AutoRoutingFacts(hasAttachment = true, requiresComplexReasoning = true)))
        assertEquals(ModelPresetId.GPT_5_6_SOL, AutoModelRouter.resolve(AutoRoutingFacts(requiresComplexReasoning = true)))
        assertEquals(
            listOf(ModelPresetId.DEEPSEEK_V4_FLASH, ModelPresetId.GPT_5_6_TERRA, ModelPresetId.CLAUDE_SONNET_5),
            AutoModelRouter.candidates(AutoRoutingFacts()).take(3),
        )
        listOf(
            AutoRoutingFacts(),
            AutoRoutingFacts(requiresComplexReasoning = true),
            AutoRoutingFacts(hasAttachment = true),
        ).forEach { facts ->
            val candidates = AutoModelRouter.candidates(facts)
            assertEquals(NanfengModelServiceCatalog.chatPresets.map { it.id }.toSet(), candidates.toSet())
            assertTrue(candidates.contains(ModelPresetId.GLM_5_3))
            assertTrue(candidates.indexOf(ModelPresetId.GLM_5_3) < candidates.indexOf(ModelPresetId.QWEN_3_8_MAX))
        }
        assertEquals(
            ModelPresetId.GEMINI_3_7_FLASH,
            AutoModelRouter.candidates(AutoRoutingFacts(hasAttachment = true)).last(),
        )
    }

    @Test fun `automatic routing classifier escalates only explicit deep work or very long prompts`() {
        assertFalse(AutoRoutingTaskClassifier.requiresComplexReasoning("帮我概括这段话"))
        assertFalse(AutoRoutingTaskClassifier.requiresComplexReasoning("分析一下这家公司"))
        assertTrue(AutoRoutingTaskClassifier.requiresComplexReasoning("请做完整方案，并说明架构设计与权衡"))
        assertTrue(AutoRoutingTaskClassifier.requiresComplexReasoning("a".repeat(2_400)))
    }
}

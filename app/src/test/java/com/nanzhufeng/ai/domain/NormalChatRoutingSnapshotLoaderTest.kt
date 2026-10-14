package com.nanzhufeng.ai.domain

import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class NormalChatRoutingSnapshotLoaderTest {
    @Test fun `Auto respects the active search interface rather than offline image capability`() {
        val ids=setOf(ModelPresetId.QWEN_3_7_PLUS,ModelPresetId.DEEPSEEK_V4_FLASH,ModelPresetId.GEMINI_3_8_FLASH)
        val models=ids.associateWith {model(it).copy(capabilities=model(it).capabilities.copy(supportsVision=true))}
        val image=ConversationAttachmentReference(AttachmentId("public-image"),"image/png","public.png",1,"a".repeat(64))
        val draft=ConversationDraft(text="分析报告",attachments=listOf(image),updatedAt=Instant.EPOCH)
        val search=NormalChatRoutingSnapshot(models,ids,true,webSearchEnabled=true)
        assertEquals(listOf(ModelPresetId.GEMINI_3_8_FLASH),search.presets(draft,null))
        assertEquals(listOf(ModelPresetId.QWEN_3_7_PLUS),search.copy(webSearchEnabled=false).presets(draft,null))
    }
    private fun model(preset: ModelPresetId) = ResolvedModel(
        NanfengModelServiceCatalog.providerFor(preset),
        if (preset == ModelPresetId.GPT_6_LUNA) "openai/gpt-6-luna" else "fixture-${preset.name}",
        preset.name, ModelCapabilities(true, false, true), null, ModelHealth.AVAILABLE, Instant.EPOCH, null,
    )
    @Test fun `upgraded cached catalog is refreshed before the rendered send receipt needs a new model`() {
        val models = ModelPresetId.entries.filter { it != ModelPresetId.GPT_6_LUNA }.associateWith(::model).toMutableMap()
        var refreshes = 0
        val loader = NormalChatRoutingSnapshotLoader(
            ModelResolver { models[it]?.let(ResolvedModelResult::Resolved) ?: ResolvedModelResult.Unavailable("old catalog") },
            { true }, { false }, { refreshes++; models[ModelPresetId.GPT_6_LUNA] = model(ModelPresetId.GPT_6_LUNA) },
        )
        val draft = ConversationDraft(text = "hello", updatedAt = Instant.EPOCH)
        val snapshot = loader.load()
        assertTrue(snapshot.canAuthorize(draft, "logical:daily:gpt-luna"))
        val approval = NormalChatEgressAuthorization.forUserSend(draft, 1, "logical:daily:gpt-luna", snapshot)
        assertTrue(approval.matches(draft))
        assertEquals("openai/gpt-6-luna", approval.recipientModelIds[ModelPresetId.GPT_6_LUNA])
        assertEquals(1, refreshes)
        loader.load(); assertEquals(1, refreshes)
    }
    @Test fun `failed public refresh is bounded and does not manufacture a model or recipient`() {
        var clock = 100L
        var refreshes = 0
        val loader = NormalChatRoutingSnapshotLoader(ModelResolver { ResolvedModelResult.Unavailable("missing") }, { true }, { false }, { refreshes++ }, { clock })
        assertTrue(loader.load().models.isEmpty())
        loader.load(); assertEquals(1, refreshes)
        clock += 60_001; loader.load(); assertEquals(2, refreshes)
    }
    @Test fun `unconfigured OpenRouter never fetches a catalog merely to inspect the picker`() {
        var refreshes = 0
        val loader = NormalChatRoutingSnapshotLoader(ModelResolver { ResolvedModelResult.Unavailable("missing") }, { false }, { false }, { refreshes++ })
        loader.load(); assertEquals(0, refreshes)
    }
}

package com.nanzhufeng.ai.ai

import com.nanzhufeng.ai.domain.*
import org.junit.Assert.*
import org.junit.Test

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
class MercuryModelIntegrationTest {
    @Test fun `Mercury is an exact manual daily OpenRouter model immediately below Luna`() {
        val preset = ModelPresetId.valueOf("MERCURY_3")
        val daily = ComposerModelRoutingCatalog.daily.flatMap { it.routes }
        assertEquals(preset, daily[daily.indexOf(ModelPresetId.GPT_6_LUNA) + 1])
        assertEquals(ProviderId.OPENROUTER, NanfengModelServiceCatalog.providerFor(preset))
        val existing = com.nanzhufeng.ai.data.OpenRouterCatalogJsonParser.parse(java.io.File("../protocol/fixtures/model-upgrade-20261009.json").readBytes())
        val mercury = com.nanzhufeng.ai.data.OpenRouterCatalogJsonParser.parse(java.io.File("../protocol/fixtures/mercury-3-20261020.json").readBytes())
        val snapshot = OpenRouterRegistrySnapshotVerifier().verify(OpenRouterCatalogResponse(existing + mercury, null), java.time.Instant.parse("2026-10-20T00:00:00Z"))!!
        val result = InMemoryVersionedModelRegistry(listOf(snapshot)).resolve(ProviderId.OPENROUTER, preset) as ModelRegistryResolution.Resolved
        assertEquals("inception/mercury-3", result.model.id)
        assertFalse(result.model.capabilities.supportsVision)
        assertEquals(1000000L, result.model.contextWindowTokens)
        assertTrue(AutoModelRouter.candidates(AutoRoutingFacts()).none { it == preset })
        val corrupted = snapshot.copy(presetMappings = snapshot.presetMappings.map { if (it.presetId == preset) it.copy(modelId = "openai/gpt-6-luna") else it })
        assertTrue(InMemoryVersionedModelRegistry(listOf(corrupted)).resolve(ProviderId.OPENROUTER, preset) is ModelRegistryResolution.Rejected)
    }

    @Test fun `Mercury text files keep full contents through bridge and both search request states`() {
        val model = requireNotNull(com.nanzhufeng.ai.data.AndroidModelProfileDirectory(androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()).profile(ModelPresetId.valueOf("MERCURY_3")))
        val files = listOf(
            ChatAttachment(ChatAttachmentKind.FILE, "text/plain", "python.txt", "Python 3.14.0 release report".toByteArray()),
            ChatAttachment(ChatAttachmentKind.FILE, "text/markdown", "rust.md", "Rust 1.90.0 release report".toByteArray()),
        )
        val bridge = UniversalChatAttachmentBridge().resolve(ConversationId("mercury-fixture"), model, "核验两份报告", files, false) as ChatAttachmentBridgeResult.Ready
        assertTrue(bridge.contextText.contains("Python 3.14.0"))
        assertTrue(bridge.contextText.contains("Rust 1.90.0"))
        assertTrue(bridge.providerAttachments.isEmpty())
        val adapter = OpenRouterChatAdapter()
        for (enabled in listOf(false, true)) {
            val options = AutomaticWebSearchPolicy.requestOptions(ProviderId.OPENROUTER, ChatRequestOptions.Standard, enabled, "核验两份报告", emptyList(), model.modelId)
            val prepared = adapter.prepare(model, listOf("user" to bridge.contextText), bridge.providerAttachments, true, options) as ChatAdapterPrepareResult.Ready
            val body = org.json.JSONObject(prepared.jsonBody)
            assertEquals("inception/mercury-3", body.getString("model"))
            assertTrue(body.getBoolean("stream"))
            assertEquals(enabled, body.has("tools"))
            assertTrue(body.toString().contains("Rust 1.90.0"))
        }
        assertTrue(UniversalChatAttachmentBridge().resolve(ConversationId("mercury-fixture"), model, "读取图片", listOf(ChatAttachment(ChatAttachmentKind.IMAGE, "image/png", "reader.png", byteArrayOf(1,2,3))), false) is ChatAttachmentBridgeResult.Ready)
    }

    @Test fun `Mercury API identity renders a readable label without changing stored identity`() {
        assertEquals("Mercury 3", modelDisplayNameForUser("inception/mercury-3"))
        assertEquals("Mercury 3", com.nanzhufeng.ai.ui.assistantFooterModelName("inception/mercury-3"))
    }

    @Test fun `Argon composer name fits outer screen while full attribution stays intact`() {
        assertEquals("4 Argon", composerModelShortNameForUser("Gemini 4 Argon"))
        assertEquals("Gemini 4 Argon", NanfengModelServiceCatalog.preset(ModelPresetId.GEMINI_4_ARGON).displayName)
        assertEquals("Gemini 4 Argon", assistantResponseFooterNameForUser("Gemini 4 Argon"))
        assertEquals("Gemini 4 Argon", com.nanzhufeng.ai.ui.assistantFooterModelName("Gemini 4 Argon"))
        assertEquals("Haiku 5.5", com.nanzhufeng.ai.ui.assistantFooterModelName("Claude Haiku 5.5"))
    }
}

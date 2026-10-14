package com.nanzhufeng.ai.ai

import com.nanzhufeng.ai.domain.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RelayCoreIntegrationTest {
    @Test fun `Relay completion streams request the trailing usage frame`() {
        for(spec in RelayCoreModels.specs.filter {it.provider!=ProviderId.RELAYCORE_CLAUDE}) {
            val adapter=RelayCoreChatAdapter(spec.provider)
            val model=ResolvedModel(spec.provider,spec.apiId,spec.compactName,ModelCapabilities(true,false,true),null,ModelHealth.UNKNOWN,null,null)
            for(options in listOf(ChatRequestOptions(),ChatRequestOptions(groundedAnswer=true))) {
                val ready=adapter.prepare(model,listOf("user" to "public minimal input"),emptyList(),true,options) as ChatAdapterPrepareResult.Ready
                val body=org.json.JSONObject(ready.jsonBody)
                // A gateway is entitled to omit the final usage chunk unless requested.
                val usage=if(body.optJSONObject("stream_options")?.optBoolean("include_usage")==true) "data: {\"choices\":[],\"usage\":{\"prompt_tokens\":10000,\"completion_tokens\":1000,\"prompt_tokens_details\":{\"cached_tokens\":2000},\"completion_tokens_details\":{\"reasoning_tokens\":250}}}\n\n" else ""
                val wire="data: {\"model\":\"${spec.apiId}\",\"choices\":[{\"delta\":{\"content\":\"public answer\"},\"finish_reason\":\"stop\"}]}\n\n"+usage+"data: [DONE]\n\n"
                val result=ProviderSseDecoder.read(wire.byteInputStream(),{},{adapter.decodeStreamingEvent(it,options)},adapter.streamTextMode(options))
                assertEquals("COMPLETED",result.finishReason);assertEquals(10000L,result.inputTokens);assertEquals(1000L,result.outputTokens)
                assertEquals(2000L,result.cachedInputTokens);assertEquals(250L,result.reasoningTokens);assertEquals(spec.apiId,result.returnedModelId)
                assertFalse(body.has("tools"));assertFalse(body.has("tool_choice"));assertEquals(spec.apiId,body.getString("model"))
                val json=adapter.prepare(model,listOf("user" to "public minimal input"),emptyList(),false,options) as ChatAdapterPrepareResult.Ready
                assertFalse(org.json.JSONObject(json.jsonBody).has("stream_options"))
            }
        }
    }
    @Test fun `Relay Claude preserves native Messages and complete attachment`() {
        for (preset in listOf(ModelPresetId.RELAYCORE_OPUS_5_5, ModelPresetId.RELAYCORE_SONNET_5_5)) {
            val spec = RelayCoreModels.spec(preset)!!
            val adapter = RelayCoreChatAdapter(ProviderId.RELAYCORE_CLAUDE)
            val model = ResolvedModel(spec.provider, spec.apiId, spec.compactName, ModelCapabilities(true, false, true), null, ModelHealth.UNKNOWN, null, null, maxOutputTokens = 16384)
            val baseOptions = adapter.requestOptions(model, ComposerModelRoutingCatalog.choices.single { preset in it.routes })
            for (options in listOf(baseOptions, baseOptions.copy(webSearchRoute = OfficialWebSearchRoute.RELAYCORE_COMPANION))) {
                assertEquals("https://api.relaycore.cn/v1/messages", adapter.endpoint("https://api.relaycore.cn/v1", options))
                val ready = adapter.prepare(model, listOf("system" to "fixed system", "user" to "分析报告", "assistant" to "保留断点", "user" to "继续"),
                    listOf(ChatAttachment(ChatAttachmentKind.FILE, "text/markdown", "report.md", "# 完整报告\n保留末行".toByteArray())), adapter.supportsStreaming(model,options), options) as ChatAdapterPrepareResult.Ready
                val body = org.json.JSONObject(ready.jsonBody)
                assertEquals(spec.apiId, body.getString("model")); assertTrue(body.getBoolean("stream"))
                assertEquals(16384, body.getInt("max_tokens"))
                assertEquals("fixed system", body.getString("system"))
                assertEquals(if (preset == ModelPresetId.RELAYCORE_OPUS_5_5) "medium" else "high", body.getJSONObject("output_config").getString("effort"))
                assertFalse(body.has("tools")); assertFalse(body.has("tool_choice")); assertFalse(body.has("stream_options"))
                assertTrue(body.getJSONArray("messages").toString().contains("保留末行")); assertTrue(body.getJSONArray("messages").toString().contains("保留断点"))
                assertEquals(ProviderStreamTextMode.MESSAGES_API, adapter.streamTextMode(options))
                val payload = """{"type":"content_block_delta","delta":{"type":"text_delta","text":"第一段"}}"""
                val event = adapter.decodeStreamingEvent(payload, options)
                assertEquals("第一段", event?.text)
            }
        }
    }

    @Test fun `published Opus aliases share estimate but keep original response identity`() {
        val at=java.time.Instant.parse("2026-10-11T00:00:00Z")
        val old=ConversationCostEstimator.estimate("claude-opus-5-5",ProviderUsage(inputTokens=100,outputTokens=20),at,"Opus 5.5 · Kiro正价")
        val current=ConversationCostEstimator.estimate("claude-opus-5.5",ProviderUsage(inputTokens=100,outputTokens=20),at,"Opus 5.5 · Kiro正价")
        assertNotNull(current);assertEquals(old,current)
        assertTrue(RelayCoreModels.responseName(ModelPresetId.RELAYCORE_OPUS_5_5,"claude-opus-5-5").startsWith("Opus 5.5"))
        assertFalse(RelayCoreModels.sameApiModel(ProviderId.OPENROUTER,"claude-opus-5-5","claude-opus-5.5"))
        assertFalse(RelayCoreModels.sameApiModel(ProviderId.RELAYCORE_CLAUDE,"claude-opus-5-5","claude-sonnet-5-5"))
    }

    @Test fun `Relay clean EOF without terminal keeps partial text incomplete`() {
        val adapter=RelayCoreChatAdapter(ProviderId.RELAYCORE_CLAUDE)
        val payload="data: {\"model\":\"claude-opus-5-5\",\"choices\":[{\"delta\":{\"content\":\"partial\"}}]}\n\n"
        val chunks=mutableListOf<String>()
        val reply=ProviderSseDecoder.read(java.io.ByteArrayInputStream(payload.toByteArray()),chunks::add,adapter::decodeStreamingEvent,adapter.streamTextMode(ChatRequestOptions.Standard))
        assertEquals("partial",reply.text);assertEquals(listOf("partial"),chunks);assertEquals("MISSING_COMPLETION",reply.finishReason)
    }

    @Test fun `six explicit platform entries preserve names and independent receivers`() {
        val fixture = org.json.JSONObject(java.io.File("../protocol/fixtures/relaycore-models-20261011.json").readText()).getJSONArray("models")
        assertEquals(6, RelayCoreModels.specs.size)
        assertEquals(3, RelayCoreModels.providers.size)
        for (i in 0 until fixture.length()) {
            val row = fixture.getJSONObject(i)
            val spec = RelayCoreModels.spec(ModelPresetId.valueOf(row.getString("presetId")))!!
            assertEquals(row.getString("modelId"), spec.apiId)
            assertEquals(row.getString("providerId"), NanfengModelServiceCatalog.providerFor(spec.preset).name)
            val display = "${spec.uiName} · ${row.getString("multiplier")}×"
            assertEquals(display, spec.pickerName)
            val prefix = ""
            assertEquals("${prefix}未联网", spec.pickerDetail(false))
            val search = if (RelayCoreModels.needsCompanion(spec.preset, true)) "Luna · OpenRouter 检索" else "实时联网"
            assertEquals("$prefix$search", spec.pickerDetail(true))
            assertEquals(row.getString("channel"), spec.channel)
            assertEquals(spec,RelayCoreModels.specByName("${spec.modelName} · ${spec.channel}"))
            assertEquals("${spec.composerName} · RC", composerModelShortNameForUser(spec.pickerName))
            assertFalse(spec.compactName.contains("×"))
            assertFalse(spec.compactName.contains("RelayCore"))
            val choice = ComposerModelRoutingCatalog.choices.single { spec.preset in it.routes }
            assertEquals(spec.pickerName, choice.label)
            assertEquals("${spec.uiName} · RC", ComposerModelRoutingCatalog.label(choice.id))
            assertEquals(spec.slot, choice.slot)
        }
    }

    @Test fun `outer composer follows ordinary compact names without rate or channel`() {
        val names = mapOf(
            ModelPresetId.RELAYCORE_OPUS_5_5 to "Opus 5.5",
            ModelPresetId.RELAYCORE_SONNET_5_5 to "Sonnet 5.5",
            ModelPresetId.RELAYCORE_GPT_6_1_SOL to "6.1 Sol",
            ModelPresetId.RELAYCORE_GPT_6_ASTRA to "6 Astra",
            ModelPresetId.RELAYCORE_GEMINI_3_8_FLASH to "Gemini 3.8",
            ModelPresetId.RELAYCORE_GEMINI_3_1_PRO to "Gemini 3.1",
        )
        for ((preset, name) in names) {
            val spec = RelayCoreModels.spec(preset)!!
            assertEquals(name, spec.composerName)
            assertEquals("${spec.uiName} · ${spec.multiplier}×", spec.pickerName)
        }
    }

    @Test fun `Relay menu moves entries without changing persisted choice identity or capability tier`() {
        val ordinary = ComposerModelRoutingCatalog.daily + ComposerModelRoutingCatalog.deep
        assertTrue(ordinary.none { choice -> choice.routes.any { RelayCoreModels.spec(it) != null } })
        assertEquals(6, ComposerModelRoutingCatalog.relayCore.size)
        for (spec in RelayCoreModels.specs) {
            val id = "logical:relaycore:${spec.preset.name}"
            val choice = ComposerModelRoutingCatalog.choice(id)
            assertEquals(id, choice.id)
            assertEquals(listOf(spec.preset), choice.routes)
            assertEquals(spec.slot, choice.slot)
            assertEquals(1, ComposerModelRoutingCatalog.choices.count { it.id == id })
        }
    }

    @Test fun `adapter calls exact selected API identity without provider fallback`() {
        for (spec in RelayCoreModels.specs) {
            val adapter = ChatProviderAdapters().adapter(spec.provider)!!
            val model = ResolvedModel(spec.provider, spec.apiId, spec.compactName, ModelCapabilities(true, false, true), null, ModelHealth.UNKNOWN, null, null)
            val options = adapter.requestOptions(model, ComposerModelRoutingCatalog.choices.single { spec.preset in it.routes })
            val request = adapter.prepare(model, listOf("user" to "synthetic request"), emptyList(), true, options) as ChatAdapterPrepareResult.Ready
            assertEquals(spec.apiId, org.json.JSONObject(request.jsonBody).getString("model"))
            assertTrue(adapter.supportsStreaming(model,options))
            assertTrue(org.json.JSONObject(request.jsonBody).getBoolean("stream"))
            if (spec.provider == ProviderId.RELAYCORE_CLAUDE) {
                val body = org.json.JSONObject(request.jsonBody)
                assertEquals(if (spec.apiId in setOf("claude-opus-5-5","claude-opus-5.5")) "medium" else "high",body.getJSONObject("output_config").getString("effort"))
            }
            assertEquals(if (spec.provider == ProviderId.RELAYCORE_CLAUDE) "https://api.relaycore.cn/v1/messages" else "https://api.relaycore.cn/v1/chat/completions", adapter.endpoint(NanfengModelServiceCatalog.provider(spec.provider)!!.fixedEndpoint, options))
            assertTrue(adapter.prepare(model.copy(providerId = ProviderId.OPENROUTER), emptyList(), emptyList(), false) is ChatAdapterPrepareResult.AttachmentUnsupported)
        }
    }

    @Test fun `Relay rejects truncated completion while preserving partial usage and identity`() {
        val body = """{"model":"claude-opus-5-5","usage":{"prompt_tokens":7,"completion_tokens":3},"choices":[{"finish_reason":"length","message":{"content":"partial"}}]}"""
        val result = RelayCoreChatAdapter(ProviderId.RELAYCORE_CLAUDE).decodeNonStreaming(body) as ChatAdapterDecodedResult.Incomplete
        assertEquals("OUTPUT_LIMIT", result.safeCode)
        assertEquals("partial", result.partial.text)
        assertEquals(7L, result.partial.inputTokens)
        assertEquals(3L, result.partial.outputTokens)
        assertEquals("claude-opus-5-5", result.partial.returnedModelId)
    }

    @Test fun `returned identity survives response parsing and streaming completion`() {
        val adapter = RelayCoreChatAdapter(ProviderId.RELAYCORE_CLAUDE)
        val body = """{"model":"claude-sonnet-5-5","choices":[{"message":{"content":"OK"}}]}"""
        val reply = adapter.decodeNonStreaming(body) as ChatAdapterDecodedResult.Text
        assertEquals("claude-sonnet-5-5", reply.returnedModelId)
        for (invalid in listOf("null", "42", "{}")) {
            val malformed = adapter.decodeNonStreaming("{\"model\":$invalid,\"choices\":[{\"message\":{\"content\":\"OK\"}}]}") as ChatAdapterDecodedResult.Text
            assertNull(malformed.returnedModelId)
        }
        assertEquals("Sonnet 5.5 · Kiro正价", RelayCoreModels.responseName(ModelPresetId.RELAYCORE_OPUS_5_5, reply.returnedModelId))
        val bytes = "data: {\"model\":\"claude-opus-5-5\",\"choices\":[{\"delta\":{\"content\":\"OK\"},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n".toByteArray()
        val result = ProviderSseDecoder.read(java.io.ByteArrayInputStream(bytes), {}, adapter::decodeStreamingEvent)
        assertEquals("claude-opus-5-5", result.returnedModelId)
        assertEquals("OK", result.text)
        assertEquals("返回模型未验证 · Kiro正价", RelayCoreModels.responseName(ModelPresetId.RELAYCORE_OPUS_5_5, null))
        val identityOnly = "data: {\"model\":\"claude-opus-5-5\",\"choices\":[{\"delta\":{\"role\":\"assistant\"}}]}\n\ndata: {\"choices\":[{\"delta\":{\"content\":\"OK\"},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n".toByteArray()
        val identityResult = ProviderSseDecoder.read(java.io.ByteArrayInputStream(identityOnly), {}, adapter::decodeStreamingEvent)
        assertEquals("claude-opus-5-5", identityResult.returnedModelId)
        val conflicting = "data: {\"model\":\"claude-opus-5-5\"}\n\ndata: {\"model\":\"claude-sonnet-5-5\",\"choices\":[{\"delta\":{\"content\":\"OK\"},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n".toByteArray()
        assertEquals("inconsistent-response-model", ProviderSseDecoder.read(java.io.ByteArrayInputStream(conflicting), {}, adapter::decodeStreamingEvent).returnedModelId)
    }

    @Test fun `RC labels survive repeated display projection while route channels remain frozen`() {
        for (spec in RelayCoreModels.specs) {
            val source="${spec.modelName} · ${spec.channel}"
            val name=modelDisplayNameForUser(source)
            assertEquals(name,modelDisplayNameForUser(name))
            val short=assistantResponseFooterNameForUser(source,spec.provider)
            assertEquals(short,composerModelShortNameForUser(short))
            assertEquals(short,assistantResponseFooterNameForUser(short))
            assertFalse(name.contains("Pro3") || name.contains("Kiro"))
        }
    }

    @Test fun `historical Claude channel is frozen after current key changes`() {
        assertEquals("Opus 5.5 · Kiro", RelayCoreModels.specByName("Opus 5.5 · Kiro · 0.08×")!!.compactName)
        assertEquals("0.08", RelayCoreModels.specByName("Opus 5.5 · Kiro")!!.multiplier)
        assertEquals("Opus 5.5 · Kiro正价", RelayCoreModels.spec(ModelPresetId.RELAYCORE_OPUS_5_5)!!.compactName)
    }

    @Test fun `Relay identity remains compact in persisted response footer`() {
        assertEquals("6.1 Sol · RC", composerModelShortNameForUser("GPT-6.1 Sol · Pro1"))
        assertEquals("6.1 Sol · RC", composerModelShortNameForUser("GPT-6.1 Sol · Pro1 · 0.12×"))
        assertEquals("返回模型未验证 · RC", composerModelShortNameForUser("返回模型未验证 · Kiro"))
        assertEquals("Opus 5.5 · RC", modelDisplayNameForUser("Opus 5.5 · Kiro"))
        assertEquals("Opus 5.5", composerModelShortNameForUser("Claude Opus 5.5"))
    }

    @Test fun `retired OpenRouter routes stay excluded while Relay Astra can enter text Auto`() {
        val hidden = setOf(ModelPresetId.CLAUDE_FABLE_5_1, ModelPresetId.GPT_6_ASTRA)
        assertTrue(NanfengModelServiceCatalog.chatPresets.none { it.id in hidden })
        for (facts in listOf(AutoRoutingFacts(), AutoRoutingFacts(requiresComplexReasoning = true), AutoRoutingFacts(hasAttachment = true))) {
            assertTrue(AutoModelRouter.candidates(facts).none { it in hidden })
        }
        assertTrue(ModelPresetId.RELAYCORE_GPT_6_ASTRA in AutoModelRouter.candidates(AutoRoutingFacts(requiresComplexReasoning = true)))
        assertTrue(ComposerModelRoutingCatalog.relayCore.any { ModelPresetId.RELAYCORE_GPT_6_ASTRA in it.routes })
        assertTrue(ComposerModelRoutingCatalog.isRetired("logical:deep:gpt-astra"))
    }

    @Test fun `Relay web capability requires the approved companion route`() {
        val options = AutomaticWebSearchPolicy.requestOptions(ProviderId.RELAYCORE_GEMINI, ChatRequestOptions.Standard, true, "news", emptyList())
        assertTrue(options.liveWebSearch)
    }
}

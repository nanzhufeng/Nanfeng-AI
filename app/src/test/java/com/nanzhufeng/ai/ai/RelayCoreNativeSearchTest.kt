package com.nanzhufeng.ai.ai

import com.nanzhufeng.ai.domain.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RelayCoreNativeSearchTest {
    private fun model() = ResolvedModel(ProviderId.RELAYCORE_GPT, "gpt-6.1-sol", "GPT-6.1 Sol · Pro3", ModelCapabilities(true, false, true), 131072, ModelHealth.AVAILABLE, null, null, maxOutputTokens = 512)
    private fun opus() = model().copy(providerId=ProviderId.RELAYCORE_CLAUDE, modelId="claude-opus-5-5", displayName="Opus 5.5 · Kiro正价", maxOutputTokens=1024)
    @Test fun `native protocol support does not enable unreliable ordinary Opus or hide its Luna recipient`() {
        val m=opus();val options=ChatRequestOptions.Standard.copy(webSearchRoute=OfficialWebSearchRoute.RELAYCORE_CLAUDE_MESSAGES)
        val adapter=RelayCoreChatAdapter(m.providerId)
        assertEquals("RELAYCORE_CLAUDE_MESSAGES",options.webSearchRoute.name)
        assertEquals("https://api.relaycore.cn/v1/messages",adapter.endpoint("https://api.relaycore.cn/v1",options))
        val body=JSONObject((adapter.prepare(m,listOf("system" to "synthetic instruction","user" to "public question"),emptyList(),false,options) as ChatAdapterPrepareResult.Ready).jsonBody)
        assertEquals("auto",body.getJSONObject("tool_choice").getString("type"))
        assertEquals("web_search_20250305",body.getJSONArray("tools").getJSONObject(0).getString("type"))
        assertEquals(m.modelId,body.getString("model"));assertFalse(body.getBoolean("stream"))
        val routing=NormalChatRoutingSnapshot(mapOf(ModelPresetId.RELAYCORE_OPUS_5_5 to m),setOf(ModelPresetId.RELAYCORE_OPUS_5_5),false,true)
        val draft=ConversationDraft(text="public question",updatedAt=java.time.Instant.EPOCH)
        val selectedId=ComposerModelRoutingCatalog.choices.single {it.routes==listOf(ModelPresetId.RELAYCORE_OPUS_5_5)}.id
        assertEquals(listOf(ModelPresetId.GPT_6_LUNA),routing.auxiliaryPresets(draft,selectedId))
        assertFalse(routing.canAuthorize(draft,selectedId))
        assertEquals(OfficialWebSearchRoute.RELAYCORE_COMPANION,AutomaticWebSearchPolicy.requestOptions(m.providerId,ChatRequestOptions.Standard,true,"public question",emptyList(),m.modelId).webSearchRoute)
    }
    @Test fun `native Claude requires final real sources rather than an empty server tool result`() {
        val adapter=RelayCoreChatAdapter(ProviderId.RELAYCORE_CLAUDE)
        val good="""{"model":"claude-opus-5.5","stop_reason":"end_turn","content":[{"type":"server_tool_use","name":"web_search"},{"type":"web_search_tool_result","content":[{"type":"web_search_result","url":"https://www.python.org/downloads/","title":"Python"}]},{"type":"text","text":"Public grounded answer"}],"usage":{"input_tokens":200,"output_tokens":30}}"""
        val parsed=adapter.decodeNonStreaming(good) as ChatAdapterDecodedResult.Text
        assertTrue(parsed.webSearchPerformed);assertEquals(1,parsed.webSources.size);assertEquals("claude-opus-5.5",parsed.returnedModelId)
        val empty=adapter.decodeNonStreaming(good.replace("[{\"type\":\"web_search_result\",\"url\":\"https://www.python.org/downloads/\",\"title\":\"Python\"}]","[]")) as ChatAdapterDecodedResult.Text
        assertFalse(empty.webSearchPerformed);assertTrue(empty.webSources.isEmpty())
    }
    @Test fun `authorized production adapter verifies Opus own live search`() {
        org.junit.Assume.assumeTrue(System.getenv("NFAI_RELAY_OPUS_NATIVE_AUTH")=="public-only-yuan-0.25")
        val m=opus();val adapter=RelayCoreChatAdapter(m.providerId)
        val options=ChatRequestOptions.Standard.copy(webSearchRoute=OfficialWebSearchRoute.RELAYCORE_CLAUDE_MESSAGES)
        val body=(adapter.prepare(m,listOf("user" to "Search the live web now for the latest Python release on python.org. Report one release version and its release date with an official search citation. Do not answer from memory. Keep the answer under 50 words."),emptyList(),false,options) as ChatAdapterPrepareResult.Ready).jsonBody
        val secret=java.io.File(requireNotNull(System.getenv("NFAI_RELAY_OPUS_NATIVE_KEY_PATH"))).readText().trim().toCharArray()
        val outcome=try {OfficialProviderChatTransport().execute(ProviderChatRequest(adapter.endpoint("https://api.relaycore.cn/v1",options),body,readTimeoutMillis=180_000),secret)} finally {secret.fill('\u0000')}
        assertTrue(outcome is ProviderChatOutcome.HttpResponse)
        outcome as ProviderChatOutcome.HttpResponse
        assertEquals(200,outcome.statusCode)
        val reply=adapter.decodeNonStreaming(outcome.responseBody) as ChatAdapterDecodedResult.Text
        assertTrue(reply.webSearchPerformed && reply.webSources.isNotEmpty())
        println("LIVE_OPUS_NATIVE sources=${reply.webSources.size} returnedModel=${reply.returnedModelId} inputTokens=${reply.inputTokens} outputTokens=${reply.outputTokens}")
    }
    @Test fun `GPT native search streams before the final response and preserves verified terminal evidence`() {
        val adapter = RelayCoreChatAdapter(ProviderId.RELAYCORE_GPT)
        for (id in listOf("gpt-6.1-sol", "gpt-6-astra")) {
            val m = model().copy(modelId = id)
            val options = ChatRequestOptions.Standard.copy(webSearchRoute = OfficialWebSearchRoute.RELAYCORE_GPT_RESPONSES)
            assertTrue(adapter.supportsStreaming(m, options))
            val prepared = adapter.prepare(m, listOf("user" to "public synthetic query"), emptyList(), true, options) as ChatAdapterPrepareResult.Ready
            assertTrue(JSONObject(prepared.jsonBody).getBoolean("stream"))
            assertTrue(JSONObject(prepared.jsonBody).getJSONArray("tools").getJSONObject(0).getBoolean("external_web_access"))
            assertEquals("required", JSONObject(prepared.jsonBody).getString("tool_choice"))
            val terminal = """{"type":"response.completed","response":{"status":"completed","model":"$id","output":[{"type":"web_search_call","status":"completed"},{"type":"message","content":[{"type":"output_text","text":"answer","annotations":[{"type":"url_citation","url":"https://docs.python.org/3/library/asyncio.html","title":"Python"}]}]}],"usage":{"input_tokens":20,"output_tokens":5}}}"""
            val event = """{"type":"response.output_text.delta","delta":"answer"}"""
            val bytes = "data: $event\n\ndata: $terminal\n\n".toByteArray()
            val deltas = mutableListOf<String>()
            val result = ProviderSseDecoder.read(java.io.ByteArrayInputStream(bytes), deltas::add, { adapter.decodeStreamingEvent(it, options) }, ProviderStreamTextMode.RESPONSES_API)
            assertEquals(listOf("answer"), deltas)
            assertEquals("answer", result.text)
            assertEquals(id, result.returnedModelId)
            assertTrue(result.webSearchPerformed)
            assertEquals(1, result.webSources.size)
            assertEquals(20L, result.inputTokens)
            assertEquals(5L, result.outputTokens)
        }
    }

    @Test fun `Relay stream requires explicit completed response and completed search facts`() {
        val adapter = RelayCoreChatAdapter(ProviderId.RELAYCORE_GPT)
        val options = ChatRequestOptions.Standard.copy(webSearchRoute=OfficialWebSearchRoute.RELAYCORE_GPT_RESPONSES)
        for (status in listOf("incomplete", "failed", "")) {
            val event = adapter.decodeStreamingEvent("""{"type":"response.completed","response":{"status":"$status","model":"gpt-6-astra"}}""", options)!!
            assertEquals(ProviderStreamTerminal.INCOMPLETE, event.terminal)
            assertFalse(event.webSearchPerformed)
        }
        val unfinishedSearch = adapter.decodeStreamingEvent("""{"type":"response.completed","response":{"status":"completed","model":"gpt-6-astra","output":[{"type":"web_search_call"}]}}""", options)!!
        assertFalse(unfinishedSearch.webSearchPerformed)
        assertEquals(ProviderStreamTerminal.INCOMPLETE, unfinishedSearch.terminal)
        val noSources = adapter.decodeStreamingEvent("""{"type":"response.completed","response":{"status":"completed","model":"gpt-6-astra","output":[{"type":"web_search_call","status":"completed"}]}}""", options)!!
        assertEquals(ProviderStreamTerminal.INCOMPLETE, noSources.terminal)
    }

    @Test fun `attachment batches retain an opaque task routing key on the same receiver`() {
        assertEquals("ad50d2c77702ce0a07c697d3e87535031e5cda5d50255809db3c8f0b65c11526",relayTaskRoutingKey(ProviderId.RELAYCORE_GPT,"gpt-6.1-sol","fixture-task-01"))
        assertNotEquals(relayTaskRoutingKey(ProviderId.RELAYCORE_GPT,"gpt-6.1-sol","task-a"),relayTaskRoutingKey(ProviderId.RELAYCORE_GPT,"gpt-6.1-sol","task-b"))
        val adapter=RelayCoreChatAdapter(ProviderId.RELAYCORE_GPT)
        val options=ChatRequestOptions.Standard.copy(webSearchRoute=OfficialWebSearchRoute.RELAYCORE_GPT_RESPONSES,relayTaskRoutingKey="a".repeat(64))
        val first=JSONObject((adapter.prepare(model(),listOf("user" to "公开批次甲"),emptyList(),true,options) as ChatAdapterPrepareResult.Ready).jsonBody)
        val second=JSONObject((adapter.prepare(model(),listOf("user" to "公开批次乙"),emptyList(),true,options) as ChatAdapterPrepareResult.Ready).jsonBody)
        assertEquals("a".repeat(64),first.getString("prompt_cache_key"));assertEquals(first.getString("prompt_cache_key"),second.getString("prompt_cache_key"));assertEquals("required",first.getString("tool_choice"))
        val grounded=JSONObject((adapter.prepare(model(),listOf("user" to "原选回答"),emptyList(),true,options.copy(groundedAnswer=true)) as ChatAdapterPrepareResult.Ready).jsonBody)
        assertFalse(grounded.has("tools"));assertEquals(first.getString("prompt_cache_key"),grounded.getString("prompt_cache_key"))
    }

    @Test fun `enabled GPT search sends native Responses to the same Relay receiver`() {
        val model = model()
        val options = AutomaticWebSearchPolicy.requestOptions(model.providerId, ChatRequestOptions.Standard, true, "search for Svelte releases", emptyList(), model.modelId)
        assertEquals("RELAYCORE_GPT_RESPONSES", options.webSearchRoute.name)
        val adapter = RelayCoreChatAdapter(model.providerId)
        assertEquals("https://api.relaycore.cn/v1/responses", adapter.endpoint("https://api.relaycore.cn/v1", options))
        assertTrue(adapter.supportsStreaming(model, options))
        val prepared = adapter.prepare(model, listOf("user" to "public synthetic query"), emptyList(), false, options) as ChatAdapterPrepareResult.Ready
        val body = JSONObject(prepared.jsonBody)
        assertEquals(model.modelId, body.getString("model"))
        assertEquals("web_search", body.getJSONArray("tools").getJSONObject(0).getString("type"))
        assertEquals("required",body.getString("tool_choice"))
        assertFalse(body.getBoolean("store"))
        assertFalse(body.getBoolean("stream"))
        assertFalse(body.has("max_tool_calls"))
        assertFalse(body.has("messages"))
    }
    @Test fun `Responses retains final identity search execution and structured citations`() {
        val adapter = RelayCoreChatAdapter(ProviderId.RELAYCORE_GPT)
        val raw = """{"status":"completed","model":"gpt-6.1-sol","output":[{"type":"web_search_call","status":"completed"},{"type":"message","content":[{"type":"output_text","text":"Public answer","annotations":[{"type":"url_citation","url":"https://svelte.dev/blog","title":"Svelte"}]}]}],"usage":{"input_tokens":23,"output_tokens":9}}"""
        val result = adapter.decodeNonStreaming(raw) as ChatAdapterDecodedResult.Text
        assertEquals("Public answer", result.text)
        assertEquals("gpt-6.1-sol", result.returnedModelId)
        assertTrue(result.webSearchPerformed)
        assertEquals(listOf(ProviderWebSource("https://svelte.dev/blog", "Svelte")), result.webSources)
        assertEquals(23L, result.inputTokens)
        assertEquals(9L, result.outputTokens)
        assertEquals(ChatAdapterDecodedResult.EmptyOrMalformed, adapter.decodeNonStreaming(raw.replace("\"status\":\"completed\",\"model\"", "\"status\":\"incomplete\",\"model\"")))
    }
    @Test fun `authorized live Android adapter and transport return real Relay search evidence`() {
        org.junit.Assume.assumeTrue(System.getenv("NFAI_RELAY_LIVE_AUTH") == "public-only-budget-0.10")
        val keyPath = requireNotNull(System.getenv("NFAI_RELAY_LIVE_KEY_PATH"))
        val adapter = RelayCoreChatAdapter(ProviderId.RELAYCORE_GPT)
        val model = model()
        val options = AutomaticWebSearchPolicy.requestOptions(model.providerId, ChatRequestOptions.Standard, true, "public release query", emptyList(), model.modelId)
        val prepared = adapter.prepare(model, listOf("user" to "Search the web for the latest stable Svelte release. Return its version and one official release source in one short sentence."), emptyList(), false, options) as ChatAdapterPrepareResult.Ready
        val secret = java.io.File(keyPath).readText().trim().toCharArray()
        val outcome = try {
            OfficialProviderChatTransport().execute(ProviderChatRequest(adapter.endpoint("https://api.relaycore.cn/v1", options), prepared.jsonBody, readTimeoutMillis = 180_000), secret)
        } finally { secret.fill('\u0000') }
        assertTrue("Expected a completed HTTP response", outcome is ProviderChatOutcome.HttpResponse)
        outcome as ProviderChatOutcome.HttpResponse
        assertEquals(200, outcome.statusCode)
        val reply = adapter.decodeNonStreaming(outcome.responseBody) as ChatAdapterDecodedResult.Text
        assertTrue(reply.webSearchPerformed && reply.webSources.isNotEmpty())
        assertEquals(model.modelId, reply.returnedModelId)
        assertTrue(reply.text.isNotBlank())
        println("LIVE_RELAY_ANDROID searchVerified=${reply.webSearchPerformed} sources=${reply.webSources.size} visibleChars=${reply.text.length} inputTokens=${reply.inputTokens} outputTokens=${reply.outputTokens}")
    }

}

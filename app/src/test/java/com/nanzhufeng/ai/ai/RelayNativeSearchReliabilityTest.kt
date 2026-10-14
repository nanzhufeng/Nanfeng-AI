package com.nanzhufeng.ai.ai

import com.nanzhufeng.ai.domain.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RelayNativeSearchReliabilityTest {
    private fun model(id: String) = ResolvedModel(ProviderId.RELAYCORE_GPT, id, id,
        ModelCapabilities(true, false, false, false), 8192, ModelHealth.AVAILABLE, null, null)

    @Test fun `Sol and Astra native search use the same bounded reasoning and search context policy`() {
        for (id in listOf("gpt-6.1-sol", "gpt-6-astra")) {
            for ((question, effort) in listOf("查询Python最近发布版本" to "low", "请深入分析这项技术的长期风险" to "medium")) {
                val prepared = RelayCoreChatAdapter(ProviderId.RELAYCORE_GPT).prepare(model(id), listOf("user" to question), emptyList(), true,
                    ChatRequestOptions(OfficialWebSearchRoute.RELAYCORE_GPT_RESPONSES)) as ChatAdapterPrepareResult.Ready
                val body = JSONObject(prepared.jsonBody)
                assertEquals(id, body.getString("model"))
                assertEquals(effort, body.getJSONObject("reasoning").getString("effort"))
                assertEquals(effort, body.getJSONArray("tools").getJSONObject(0).getString("search_context_size"))
                assertEquals("required", body.getString("tool_choice"))
            }
        }
    }

    @Test fun `invalid completed status retains returned identity usage and sources while remaining incomplete`() {
        for (id in listOf("gpt-6.1-sol", "gpt-6-astra")) {
            val event = RelayCoreChatAdapter(ProviderId.RELAYCORE_GPT).decodeStreamingEvent(
                """{"type":"response.completed","response":{"status":"incomplete","model":"$id","usage":{"input_tokens":100,"output_tokens":30,"input_tokens_details":{"cached_tokens":20},"output_tokens_details":{"reasoning_tokens":10}},"output":[{"type":"web_search_call","status":"completed","action":{"sources":[{"url":"https://www.python.org/","title":"Python"}]}}]}}""",
                ChatRequestOptions(OfficialWebSearchRoute.RELAYCORE_GPT_RESPONSES))!!
            assertEquals(ProviderStreamTerminal.INCOMPLETE, event.terminal)
            assertEquals(100L, event.inputTokens)
            assertEquals(30L, event.outputTokens)
            assertEquals(20L, event.cachedInputTokens)
            assertEquals(10L, event.reasoningTokens)
            assertEquals(id, event.returnedModelId)
            assertFalse(event.webSearchPerformed)
            assertEquals(1, event.webSources.size)
        }
    }
}

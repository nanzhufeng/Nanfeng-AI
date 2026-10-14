package com.nanzhufeng.ai.ai

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MessagesSearchResultLifecycleTest {
    private val options = ChatRequestOptions(OfficialWebSearchRoute.DEEPSEEK_MESSAGES)
    private val empty = """{"type":"content_block_start","content_block":{"type":"web_search_tool_result","content":[]}}"""
    private val toolError = """{"type":"content_block_start","content_block":{"type":"web_search_tool_result","content":{"type":"web_search_tool_result_error","error_code":"too_many_requests"}}}"""
    private val exhausted = """{"type":"content_block_start","content_block":{"type":"web_search_tool_result","content":[{"type":"web_search_tool_result_error","error_code":"max_uses_exceeded"}]}}"""
    private val found = """{"type":"content_block_start","content_block":{"type":"web_search_tool_result","content":[{"type":"web_search_result","url":"https://www.python.org/","title":"Python"}]}}"""
    private fun read(results: List<String>, reason: String = "end_turn", providerError: Boolean = false, useDeepSeekAdapter: Boolean = true): ProviderSseDecoder.Result {
        val frames = listOf("""{"type":"message_start","message":{"model":"deepseek-flash","usage":{"input_tokens":100,"cache_read_input_tokens":20}}}""") + results +
            listOf("""{"type":"content_block_delta","delta":{"type":"text_delta","text":"完成的公开回答"}}""") +
            (if (providerError) listOf("""{"type":"error","error":{"type":"api_error"}}""") else emptyList()) +
            listOf("""{"type":"message_delta","delta":{"stop_reason":"$reason"},"usage":{"output_tokens":30}}""", """{"type":"message_stop"}""")
        val adapter = DeepSeekChatAdapter()
        return ProviderSseDecoder.read(frames.joinToString("") { "data: $it\n\n" }.byteInputStream(), {},
            { if (useDeepSeekAdapter) adapter.decodeStreamingEvent(it, options) else MessagesProviderSseCodec.decode(it) }, adapter.streamTextMode(options))
    }

    @Test fun `one unsuccessful query cannot poison another successful query and complete answer`() {
        for (results in listOf(listOf(empty, found), listOf(toolError, found), listOf(found, empty), listOf(found, toolError), listOf(exhausted, found), listOf(found, exhausted))) {
            val reply = read(results)
            assertEquals("COMPLETED", reply.finishReason)
            assertEquals("完成的公开回答", reply.text)
            assertEquals("deepseek-flash", reply.returnedModelId)
            assertEquals(120L, reply.inputTokens)
            assertEquals(30L, reply.outputTokens)
            assertEquals(20L, reply.cachedInputTokens)
            assertTrue(reply.webSearchPerformed)
            assertEquals("https://www.python.org/", reply.webSources.single().url)
        }
    }

    @Test fun `all unsuccessful queries do not prove search even if message ends normally`() {
        val reply = read(listOf(empty, toolError))
        assertFalse(reply.webSearchPerformed)
        assertTrue(reply.webSources.isEmpty())
        assertEquals("FAILED", reply.finishReason)
        assertEquals(120L, reply.inputTokens)
        assertEquals(30L, reply.outputTokens)
        assertEquals("WEB_SEARCH_ANSWER_UNVERIFIED", WebSearchGroundingPolicy.completedAuditStatus(options, reply.webSources, true, reply.webSearchPerformed))
    }

    @Test fun `response truncation pause or provider error still remain unfinished`() {
        assertEquals("INCOMPLETE", read(listOf(found), "max_tokens").finishReason)
        assertEquals("INCOMPLETE", read(listOf(found), "pause_turn").finishReason)
        assertEquals("FAILED", read(listOf(found), providerError = true).finishReason)
    }
    private fun jsonReply(results: List<String>): ChatAdapterDecodedResult {
        val blocks = org.json.JSONArray()
        results.forEach { blocks.put(org.json.JSONObject(it).getJSONObject("content_block")) }
        blocks.put(org.json.JSONObject().put("type", "text").put("text", "完成的公开回答"))
        val body = org.json.JSONObject().put("model", "deepseek-flash").put("content", blocks)
            .put("stop_reason", "end_turn").put("usage", org.json.JSONObject().put("input_tokens", 100).put("cache_read_input_tokens", 20).put("output_tokens", 30))
        return DeepSeekChatAdapter().decodeNonStreaming(body.toString())
    }

    @Test fun `JSON replies preserve successful sources despite an unsuccessful sibling query`() {
        for (results in listOf(listOf(empty, found), listOf(toolError, found), listOf(found, toolError), listOf(found, exhausted))) {
            val reply = jsonReply(results) as ChatAdapterDecodedResult.Text
            assertTrue("$results", reply.webSearchPerformed)
            assertEquals("https://www.python.org/", reply.webSources.single().url)
            assertEquals("完成的公开回答", reply.text)
            assertEquals("deepseek-flash", reply.returnedModelId)
            assertEquals(120L, reply.inputTokens)
            assertEquals(30L, reply.outputTokens)
        }
    }

    @Test fun `JSON empty and exhausted queries cannot invent search proof`() {
        val decoded = jsonReply(listOf(empty, exhausted))
        assertTrue(decoded is ChatAdapterDecodedResult.Incomplete)
        val failed = decoded as ChatAdapterDecodedResult.Incomplete
        assertEquals("WEB_SEARCH_NO_SOURCES", failed.safeCode)
        val reply = failed.partial
        assertEquals(120L, reply.inputTokens)
        assertEquals(30L, reply.outputTokens)
        assertFalse(reply.webSearchPerformed)
        assertTrue(reply.webSources.isEmpty())
        assertEquals("WEB_SEARCH_ANSWER_UNVERIFIED", WebSearchGroundingPolicy.completedAuditStatus(options, reply.webSources, false, reply.webSearchPerformed))
    }

    @Test fun `ordinary Messages without required search are not rejected for absent sources`() {
        val reply = read(listOf(empty), useDeepSeekAdapter = false)
        assertEquals("COMPLETED", reply.finishReason)
        assertFalse(reply.webSearchPerformed)
    }

}

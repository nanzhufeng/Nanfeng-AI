package com.nanzhufeng.ai.ai

import java.io.ByteArrayInputStream
import java.net.SocketTimeoutException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderSseDecoderContractsTest {
    @Test fun `SSE framing retains Adapter decoded text and final usage`() {
        val payload = """
            : keepalive

            data: {"choices":[{"delta":{"reasoning_content":"thinking"}}]}

            data: {"choices":[{"delta":{"content":"你好"}}]}

            data: {"choices":[{"delta":{"content":"，世界"}}],"usage":{"prompt_tokens":3,"completion_tokens":4}}

            data: [DONE]

        """.trimIndent()

        val result = ProviderSseDecoder.read(ByteArrayInputStream(payload.toByteArray()), { }, OpenRouterChatAdapter()::decodeStreamingEvent)

        assertEquals("你好，世界", result.text)
        assertEquals("thinking", result.reasoning)
        assertEquals(3L, result.inputTokens)
        assertEquals(4L, result.outputTokens)
    }

    @Test fun `Qwen web search XML is buffered and never emitted as visible stream text`() {
        val payload = """
            data: {"choices":[{"delta":{"content":"我先梳理条件。<tool_"}}]}

            data: {"choices":[{"delta":{"content":"use>query</tool_use><tool_result>结果</tool_result>"}}]}

            data: [DONE]

        """.trimIndent()
        val visibleDeltas = mutableListOf<String>()

        val result = ProviderSseDecoder.read(
            ByteArrayInputStream(payload.toByteArray()),
            visibleDeltas::add,
            QwenChatAdapter()::decodeStreamingEvent,
            ProviderStreamTextMode.BUFFER_QWEN_WEB_SEARCH,
        )

        assertEquals("", result.text)
        assertTrue(visibleDeltas.isEmpty())
    }

    @Test fun `Qwen web search buffer keeps only a final answer after tool traffic`() {
        val payload = """
            data: {"choices":[{"delta":{"content":"准备检索。<tool_use>{}</tool_use>"}}]}

            data: {"choices":[{"delta":{"content":"<tool_result>{}</tool_result>南烛枫，最终结论。"}}]}

            data: [DONE]

        """.trimIndent()
        val visibleDeltas = mutableListOf<String>()

        val result = ProviderSseDecoder.read(
            ByteArrayInputStream(payload.toByteArray()),
            visibleDeltas::add,
            QwenChatAdapter()::decodeStreamingEvent,
            ProviderStreamTextMode.BUFFER_QWEN_WEB_SEARCH,
        )

        assertEquals("准备检索。南烛枫，最终结论。", result.text)
        assertEquals(listOf("准备检索。南烛枫，最终结论。"), visibleDeltas)
        assertTrue(result.text.contains("tool_").not())
    }

    @Test fun `Qwen Responses stream ignores search protocol and completes with answer sources and usage`() {
        val payload = """
            event: response.created
            data: {"type":"response.created","response":{"status":"queued"}}

            event: response.web_search_call.searching
            data: {"type":"response.web_search_call.searching","sequence_number":2}

            event: response.output_text.delta
            data: {"type":"response.output_text.delta","delta":"南烛枫，"}

            event: response.output_text.delta
            data: {"type":"response.output_text.delta","delta":"最终结论。"}

            event: response.completed
            data: {"type":"response.completed","response":{"output":[{"type":"web_search_call","action":{"sources":[{"url":"https://example.test/official","title":"官方公告"}]}}],"usage":{"input_tokens":12,"output_tokens":6,"output_tokens_details":{"reasoning_tokens":4}}}}

        """.trimIndent()
        val options = ChatRequestOptions(OfficialWebSearchRoute.QWEN_RESPONSES)
        val visibleDeltas = mutableListOf<String>()

        val result = ProviderSseDecoder.read(
            ByteArrayInputStream(payload.toByteArray()),
            visibleDeltas::add,
            { QwenChatAdapter().decodeStreamingEvent(it, options) },
            ProviderStreamTextMode.RESPONSES_API,
        )

        assertEquals("南烛枫，最终结论。", result.text)
        assertEquals(listOf("南烛枫，", "最终结论。"), visibleDeltas)
        assertEquals("COMPLETED", result.finishReason)
        assertEquals(12L, result.inputTokens)
        assertEquals(6L, result.outputTokens)
        assertEquals(4L, result.reasoningTokens)
        assertEquals(listOf(ProviderWebSource("https://example.test/official", "官方公告")), result.webSources)
    }

    @Test fun `Qwen Responses socket EOF without completed event is not accepted as a complete answer`() {
        val payload = """
            data: {"type":"response.output_text.delta","delta":"未完整"}

        """.trimIndent()
        val options = ChatRequestOptions(OfficialWebSearchRoute.QWEN_RESPONSES)

        val result = ProviderSseDecoder.read(
            ByteArrayInputStream(payload.toByteArray()),
            { },
            { QwenChatAdapter().decodeStreamingEvent(it, options) },
            ProviderStreamTextMode.RESPONSES_API,
        )

        assertEquals("未完整", result.text)
        assertEquals("MISSING_COMPLETION", result.finishReason)
    }

    @Test fun `active SSE output cannot extend a request beyond its total stream deadline`() {
        val payload = "data: {\"choices\":[{\"delta\":{\"content\":\"仍在输出\"}}]}\n\n"
        var now = 0L

        assertThrows(SocketTimeoutException::class.java) {
            ProviderSseDecoder.read(
                ByteArrayInputStream(payload.toByteArray()),
                { },
                QwenChatAdapter()::decodeStreamingEvent,
                maxDurationMillis = 1,
                nanoTime = { now.also { now += 2_000_000L } },
            )
        }
    }
}

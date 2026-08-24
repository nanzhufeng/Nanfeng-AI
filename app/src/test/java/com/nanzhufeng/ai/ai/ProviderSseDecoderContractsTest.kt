package com.nanzhufeng.ai.ai

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
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
}

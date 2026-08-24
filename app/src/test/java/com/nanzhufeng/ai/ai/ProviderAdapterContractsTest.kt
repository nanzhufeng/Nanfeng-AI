package com.nanzhufeng.ai.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ProviderAdapterContractsTest {
    @Test fun `non streaming OpenAI compatible response keeps text reasoning tool call and usage`() {
        val decoded = OpenAiCompatibleProbe().decodeNonStreaming(
            """{"choices":[{"message":{"content":[{"type":"text","text":"完成"}],"reasoning_content":"本地保留","tool_calls":[{"id":"call_1","function":{"name":"lookup","arguments":"{}"}}]}}],"usage":{"prompt_tokens":12,"completion_tokens":4}}""",
        ) as? ChatAdapterDecodedResult.Text

        assertNotNull(decoded)
        assertEquals("完成", decoded?.text)
        assertEquals("本地保留", decoded?.reasoning)
        assertEquals(1, decoded?.toolCalls?.size)
        assertEquals("lookup", decoded?.toolCalls?.single()?.name)
        assertEquals(12L, decoded?.inputTokens)
        assertEquals(4L, decoded?.outputTokens)
    }

    @Test fun `accepted long reply is not silently clipped by the adapter`() {
        val content = "答".repeat(12_001)
        val decoded = OpenAiCompatibleProbe().decodeNonStreaming(
            """{"choices":[{"message":{"content":"$content"}}]}""",
        ) as? ChatAdapterDecodedResult.Text

        assertEquals(content.length, decoded?.text?.length)
    }

    @Test fun `qwen adapter owns its serialization instead of inheriting OpenRouter adapter`() {
        val source = File("src/main/java/com/nanzhufeng/ai/ai/ChatProviderAdapters.kt").readText()
        val declaration = source.substringAfter("class QwenChatAdapter").substringBefore("class DeepSeekChatAdapter")
        assertFalse(declaration.contains(": OpenRouterChatAdapter"))
        assertTrue(declaration.contains("override fun prepare"))
        val qwenSerializer = source.substringAfter("private fun qwenMultimodalBody").substringBefore("/** OpenAI-compatible JSON projection")
        assertTrue(qwenSerializer.contains("file_data"))
        assertTrue(qwenSerializer.contains("video_url"))
        assertTrue(qwenSerializer.contains("Base64File"))
    }

    @Test fun `Qwen PDF request streams base64 bytes into valid full file data`() {
        val attachment = ChatAttachment(ChatAttachmentKind.PDF, "application/pdf", "report.pdf", "%PDF-1.7".toByteArray())
        val model = model().copy(
            providerId = com.nanzhufeng.ai.domain.ProviderId.QWEN,
            capabilities = com.nanzhufeng.ai.domain.ModelCapabilities(
                supportsText = true,
                supportsVision = false,
                supportsStreaming = true,
                supportsPdf = true,
            ),
        )
        val ready = QwenChatAdapter().prepare(model, emptyList(), listOf(attachment), stream = false) as ChatAdapterPrepareResult.Ready
        val output = java.io.ByteArrayOutputStream()
        ready.body.writeTo(output)
        val body = output.toString(Charsets.UTF_8)

        assertEquals(ready.body.contentLength, body.toByteArray().size.toLong())
        assertTrue(body.contains("\"file_data\":\"data:application/pdf;base64,JVBERi0xLjc=\""))
        assertTrue(body.contains("\"filename\":\"report.pdf\""))
    }

    @Test fun `OpenRouter image request streams source bytes instead of materializing a base64 string`() {
        val attachment = ChatAttachment(ChatAttachmentKind.IMAGE, "image/png", "image.png", byteArrayOf(1, 2, 3))
        val model = model().copy(
            providerId = com.nanzhufeng.ai.domain.ProviderId.OPENROUTER,
            capabilities = com.nanzhufeng.ai.domain.ModelCapabilities(true, true, true, false),
        )
        val ready = OpenRouterChatAdapter().prepare(model, listOf("user" to "看图"), listOf(attachment), stream = true) as ChatAdapterPrepareResult.Ready
        val output = java.io.ByteArrayOutputStream()
        ready.body.writeTo(output)
        val body = output.toString(Charsets.UTF_8)

        assertEquals(ready.body.contentLength, body.toByteArray().size.toLong())
        assertTrue(body.contains("\"image_url\":{\"url\":\"data:image/png;base64,AQID\"}"))
        assertTrue(body.contains("\"stream_options\":{\"include_usage\":true}"))
    }

    @Test fun `OpenRouter gateway reference stays a URL and is never reencoded as base64`() {
        val attachment = ChatAttachment(ChatAttachmentKind.PDF, "application/pdf", "report.pdf", byteArrayOf(1, 2, 3))
            .withRemoteUrl("https://gateway.example.test/v1/attachments/a/content?expires=1&signature=x")
        val model = model().copy(
            providerId = com.nanzhufeng.ai.domain.ProviderId.OPENROUTER,
            capabilities = com.nanzhufeng.ai.domain.ModelCapabilities(true, false, true, false, supportsPdf = true),
        )
        val ready = OpenRouterChatAdapter().prepare(model, emptyList(), listOf(attachment), stream = false) as ChatAdapterPrepareResult.Ready
        val output = java.io.ByteArrayOutputStream()
        ready.body.writeTo(output)
        val body = output.toString(Charsets.UTF_8)

        assertTrue(body.contains("\"file_data\":\"https://gateway.example.test/v1/attachments/a/content?expires=1&signature=x\""))
        assertFalse(body.contains("data:application/pdf;base64"))
        assertTrue(QwenChatAdapter().prepare(model.copy(providerId = com.nanzhufeng.ai.domain.ProviderId.QWEN), emptyList(), listOf(attachment), stream = false) is ChatAdapterPrepareResult.AttachmentUnsupported)
    }

    @Test fun `tool only non streaming response is explicit instead of a fake empty answer`() {
        val decoded = OpenAiCompatibleProbe().decodeNonStreaming(
            """{"choices":[{"message":{"tool_calls":[{"function":{"name":"lookup","arguments":"{}"}}]}}]}""",
        )
        assertTrue(decoded is ChatAdapterDecodedResult.ToolCalls)
        assertEquals("lookup", (decoded as ChatAdapterDecodedResult.ToolCalls).toolCalls.single().name)
    }

    @Test fun `malformed provider tool call alongside text is still never a completed chat answer`() {
        val decoded = OpenAiCompatibleProbe().decodeNonStreaming(
            """{"choices":[{"message":{"content":"正在查询","tool_calls":[{"index":0,"function":{"arguments":"{"}}]}}]}""",
        ) as? ChatAdapterDecodedResult.Text

        assertNotNull(decoded)
        assertTrue(decoded?.toolCallEncountered == true)
        assertTrue(decoded?.toolCalls.orEmpty().isEmpty())
    }

    @Test fun `adapter owns OpenAI compatible SSE JSON decoding including tool calls`() {
        val event = OpenRouterChatAdapter().decodeStreamingEvent(
            """{"choices":[{"delta":{"content":"片段","reasoning_content":"推理片段","tool_calls":[{"function":{"name":"lookup","arguments":"{}"}}]}}],"usage":{"prompt_tokens":7,"completion_tokens":2}}""",
        )
        assertEquals("片段", event?.text)
        assertEquals("推理片段", event?.reasoning)
        assertTrue(event?.toolCallEncountered == true)
        assertEquals(7L, event?.inputTokens)
        assertEquals(2L, event?.outputTokens)
    }

    @Test fun `non streaming request omits SSE options`() {
        val ready = DeepSeekChatAdapter().prepare(model(), listOf("user" to "你好"), emptyList(), stream = false) as ChatAdapterPrepareResult.Ready
        assertTrue(ready.jsonBody.contains("\"stream\":false"))
        assertFalse(ready.jsonBody.contains("stream_options"))
    }

    @Test fun `each deep route uses its own documented official web-search protocol`() {
        val openRouter = model().copy(providerId = com.nanzhufeng.ai.domain.ProviderId.OPENROUTER)
        val deepChoice = com.nanzhufeng.ai.domain.ComposerModelRoutingCatalog.deep.first { it.label == "Claude Opus 5" }
        val options = OpenRouterChatAdapter().requestOptions(openRouter, deepChoice)
        val grounded = OpenRouterChatAdapter().prepare(openRouter, listOf("user" to "查一下最新财报"), emptyList(), stream = false, options = options) as ChatAdapterPrepareResult.Ready

        assertTrue(options.liveWebSearch)
        assertEquals(OfficialWebSearchRoute.OPENROUTER_SERVER_TOOL, options.webSearchRoute)
        assertTrue(grounded.jsonBody.contains("\"type\":\"openrouter:web_search\""))
        val qwen = QwenChatAdapter()
        val qwenOptions = qwen.requestOptions(model().copy(providerId = com.nanzhufeng.ai.domain.ProviderId.QWEN), deepChoice)
        val qwenGrounded = qwen.prepare(model().copy(providerId = com.nanzhufeng.ai.domain.ProviderId.QWEN), listOf("user" to "查一下最新财报"), emptyList(), stream = false, options = qwenOptions) as ChatAdapterPrepareResult.Ready
        assertEquals(OfficialWebSearchRoute.QWEN_CHAT_COMPLETIONS, qwenOptions.webSearchRoute)
        assertTrue(qwenGrounded.jsonBody.contains("\"enable_search\":true"))

        val deepSeek = DeepSeekChatAdapter()
        val deepSeekChoice = com.nanzhufeng.ai.domain.ComposerModelRoutingCatalog.deep.first { it.label == "DeepSeek V4 Pro" }
        val deepSeekOptions = deepSeek.requestOptions(model(), deepSeekChoice)
        val deepSeekGrounded = deepSeek.prepare(model(), listOf("user" to "查一下最新财报"), emptyList(), stream = false, options = deepSeekOptions) as ChatAdapterPrepareResult.Ready
        assertEquals(OfficialWebSearchRoute.QWEN_RESPONSES, deepSeekOptions.webSearchRoute)
        assertEquals(com.nanzhufeng.ai.domain.ProviderId.QWEN, deepSeek.executionProviderId(deepSeekOptions))
        assertEquals("/responses", deepSeek.endpointPath(deepSeekOptions))
        assertFalse(deepSeek.supportsStreaming(model(), deepSeekOptions))
        assertTrue(deepSeekGrounded.jsonBody.contains("\"tools\":[{\"type\":\"web_search\"}]"))
    }

    @Test fun `Qwen Responses deepseek result has a dedicated non chat-completions projection`() {
        val decoded = DeepSeekChatAdapter().decodeNonStreaming(
            """{"output_text":"已根据实时来源完成检索。","usage":{"input_tokens":9,"output_tokens":3}}""",
        ) as? ChatAdapterDecodedResult.Text
        assertEquals("已根据实时来源完成检索。", decoded?.text)
        assertEquals(9L, decoded?.inputTokens)
        assertEquals(3L, decoded?.outputTokens)
    }

    @Test fun `attachment budget comes from model profile metadata rather than adapter constants`() {
        val profile = com.nanzhufeng.ai.domain.AttachmentInputTokenEstimate(10, 1, 20, 2, 30, 3)
        assertEquals(10, ChatAttachment(ChatAttachmentKind.IMAGE, "image/png", "a.png", ByteArray(1)).estimatedInputTokens(profile))
        assertEquals(20, ChatAttachment(ChatAttachmentKind.PDF, "application/pdf", "a.pdf", ByteArray(1)).estimatedInputTokens(profile))
        assertEquals(30, ChatAttachment(ChatAttachmentKind.VIDEO, "video/mp4", "a.mp4", ByteArray(1)).estimatedInputTokens(profile))
    }

    @Test fun `Qwen native PDF keeps the official long first token deadline without slowing other requests`() {
        val qwen = QwenChatAdapter()
        assertEquals(300_000, qwen.readTimeoutMillis(model(), listOf(ChatAttachment(ChatAttachmentKind.PDF, "application/pdf", "report.pdf", ByteArray(1))), stream = true))
        assertEquals(90_000, qwen.readTimeoutMillis(model(), emptyList(), stream = true))
    }

    @Test fun `response byte ceiling scales from the resolved model output capacity`() {
        assertEquals(1 * 1024 * 1024, ProviderResponseByteBudget.forMaxOutputTokens(8_192))
        assertEquals(6_209_536, ProviderResponseByteBudget.forMaxOutputTokens(384_000))
        assertEquals(8 * 1024 * 1024, ProviderResponseByteBudget.forMaxOutputTokens(1_000_000))
    }

    private class OpenAiCompatibleProbe : OpenAiCompatibleChatAdapter() {
        override val providerId = com.nanzhufeng.ai.domain.ProviderId.MOCK
        override fun prepare(
            model: com.nanzhufeng.ai.domain.ResolvedModel,
            messages: List<Pair<String, String>>,
            attachments: List<ChatAttachment>,
            stream: Boolean,
            options: ChatRequestOptions,
        ) = ChatAdapterPrepareResult.AttachmentUnsupported
        override fun decodeStreamingEvent(body: String) = null
    }

    private fun model() = com.nanzhufeng.ai.domain.ResolvedModel(
        providerId = com.nanzhufeng.ai.domain.ProviderId.DEEPSEEK,
        modelId = "test", displayName = "test",
        capabilities = com.nanzhufeng.ai.domain.ModelCapabilities(true, false, false, false),
        contextWindowTokens = 8_192, health = com.nanzhufeng.ai.domain.ModelHealth.AVAILABLE,
        metadataUpdatedAt = null, healthCheckedAt = null,
    )
}

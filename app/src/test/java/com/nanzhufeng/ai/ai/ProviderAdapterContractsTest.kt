package com.nanzhufeng.ai.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

class ProviderAdapterContractsTest {
    @Test fun `Flash native images retain original bytes in chat and web search while Pro and PDF fail closed`() {
        val flash = model().copy(providerId = com.nanzhufeng.ai.domain.ProviderId.DEEPSEEK,
            modelId = "deepseek-flash", capabilities = model().capabilities.copy(supportsVision = true))
        val image = ChatAttachment(ChatAttachmentKind.IMAGE, "image/png", "chart.png", byteArrayOf(1, 2, 3))
        for (route in listOf(OfficialWebSearchRoute.NONE, OfficialWebSearchRoute.DEEPSEEK_RESPONSES)) {
            val ready = DeepSeekChatAdapter().prepare(flash, listOf("user" to "看图"), listOf(image), false, ChatRequestOptions(route)) as ChatAdapterPrepareResult.Ready
            val output = java.io.ByteArrayOutputStream()
            ready.body.writeTo(output)
            val body = output.toString(Charsets.UTF_8)
            assertTrue(body.contains("data:image/png;base64,AQID"))
            assertTrue(body.contains(if (route == OfficialWebSearchRoute.NONE) "\"type\":\"image_url\"" else "\"type\":\"input_image\""))
            assertEquals(ready.body.contentLength, output.size().toLong())
        }
        assertTrue(DeepSeekChatAdapter().prepare(flash.copy(modelId = "deepseek-v4-pro"), emptyList(), listOf(image), false) is ChatAdapterPrepareResult.AttachmentUnsupported)
        val pdf = ChatAttachment(ChatAttachmentKind.PDF, "application/pdf", "file.pdf", byteArrayOf(1))
        assertTrue(DeepSeekChatAdapter().prepare(flash, emptyList(), listOf(pdf), false) is ChatAdapterPrepareResult.AttachmentUnsupported)
    }

    @Test fun `transport network diagnostics retain only a safe failure category`() {
        assertEquals(ProviderNetworkFailureKind.DNS, classifyProviderNetworkFailure(UnknownHostException("openrouter.ai")))
        assertEquals(ProviderNetworkFailureKind.TLS, classifyProviderNetworkFailure(SSLHandshakeException("certificate details stay out of diagnostics")))
    }

    @Test fun `explicit K3 and legacy Grok requests require their exact fresh public catalog mapping before Key access`() {
        val source = File("src/main/java/com/nanzhufeng/ai/ai/NormalChatOpenRouterExecutor.kt").readText()
        assertTrue(source.contains("requiresFreshOpenRouterVerification"))
        assertTrue(source.contains("ModelPresetId.KIMI_K3, ModelPresetId.GROK_4_1_FAST"))
        assertTrue(source.contains("ComposerModelRoutingCatalog.isRetired(selectedId)"))
        assertTrue(source.indexOf("verifyOpenRouterRegistry.execute()") < source.indexOf("credentials.loadCredential(executionProviderId)"))
        assertTrue(source.contains("verified !is VerifyOpenRouterRegistryResult.Verified"))
        assertTrue(source.contains("Result.Blocked(Code.MODEL_NOT_FOUND)"))
        assertTrue(source.contains("ModelPresetUsage.CHAT"))
    }

    @Test fun `Grok 4 point 6 High owns a bounded high reasoning request`() {
        val adapter = OpenRouterChatAdapter()
        val high = adapter.prepare(
            model().copy(providerId = com.nanzhufeng.ai.domain.ProviderId.OPENROUTER, modelId = GROK_4_6_MODEL_ID, maxOutputTokens = 450_000),
            listOf("user" to "深入分析"), emptyList(), stream = true,
        ) as ChatAdapterPrepareResult.Ready

        assertTrue(high.jsonBody.contains("\"reasoning\":{\"effort\":\"high\"}"))
        assertTrue(high.jsonBody.contains("\"max_tokens\":65536"))
        assertFalse(high.jsonBody.contains("\"max_tokens\":450000"))
    }

    @Test fun `Grok multimodal request retains product reasoning and OpenRouter X aware search plugin`() {
        val attachment = ChatAttachment(ChatAttachmentKind.IMAGE, "image/png", "chart.png", byteArrayOf(1, 2, 3))
        val options = ChatRequestOptions(OfficialWebSearchRoute.OPENROUTER_SERVER_TOOL)
        val ready = OpenRouterChatAdapter().prepare(
            model().copy(providerId = com.nanzhufeng.ai.domain.ProviderId.OPENROUTER, modelId = GROK_4_6_MODEL_ID),
            listOf("user" to "结合实时信息分析"), listOf(attachment), stream = false, options = options,
        ) as ChatAdapterPrepareResult.Ready
        val output = java.io.ByteArrayOutputStream()
        ready.body.writeTo(output)
        val body = output.toString(Charsets.UTF_8)

        assertTrue(body.contains("\"reasoning\":{\"effort\":\"high\"}"))
        assertTrue(body.contains("\"type\":\"openrouter:web_search\""))
        assertTrue(body.contains("\"image_url\":{\"url\":\"data:image/png;base64,AQID\"}"))
    }

    @Test fun `K3 continuation preserves reasoning content and tool calls instead of flattening assistant content`() {
        val k3 = model().copy(
            providerId = com.nanzhufeng.ai.domain.ProviderId.OPENROUTER,
            modelId = KIMI_K3_MODEL_ID,
            maxOutputTokens = 131_072,
        )
        val ready = OpenRouterChatAdapter().prepareContinuation(
            k3,
            listOf(
                ChatHistoryMessage("user", "继续分析"),
                ChatHistoryMessage("assistant", "阶段结论", "内部推理", listOf(ChatToolCall("call_1", "lookup", "{\"q\":\"K3\"}"))),
            ),
            emptyList(),
            stream = true,
        ) as ChatAdapterPrepareResult.Ready

        assertTrue(ready.jsonBody.contains("\"reasoning_content\":\"内部推理\""))
        assertTrue(ready.jsonBody.contains("\"tool_calls\":["))
        assertTrue(ready.jsonBody.contains("\"id\":\"call_1\""))
        assertTrue(ready.jsonBody.contains("\\\"q\\\":\\\"K3\\\""))
    }

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

    @Test fun `OpenRouter usage cost is converted to persisted USD micro units without losing a valid zero`() {
        val charged = OpenRouterChatAdapter().decodeNonStreaming(
            """{"choices":[{"message":{"content":"完成"}}],"usage":{"prompt_tokens":12,"completion_tokens":4,"cost":0.00521}}""",
        ) as? ChatAdapterDecodedResult.Text
        val free = OpenRouterChatAdapter().decodeNonStreaming(
            """{"choices":[{"message":{"content":"免费"}}],"usage":{"prompt_tokens":1,"completion_tokens":1,"cost":0}}""",
        ) as? ChatAdapterDecodedResult.Text

        assertEquals(5_210L, charged?.reportedCostUsdMicros)
        assertEquals(0L, free?.reportedCostUsdMicros)
    }

    @Test fun `OpenRouter citations are retained as provider sources and become source chips later`() {
        val decoded = OpenAiCompatibleProbe().decodeNonStreaming(
            """{"choices":[{"message":{"content":"已核验","annotations":[{"type":"url_citation","url_citation":{"url":"https://example.com/news","title":"官方公告"}}]}}]}""",
        ) as? ChatAdapterDecodedResult.Text

        assertEquals(listOf(ProviderWebSource("https://example.com/news", "官方公告")), decoded?.webSources)
        assertTrue(appendProviderWebSources(decoded!!.text, decoded.webSources).contains("[官方公告](https://example.com/news)"))
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
        assertTrue(qwenSerializer.contains("inlineUtf8TextFiles"))
        assertTrue(qwenSerializer.contains("video_url"))
        assertTrue(qwenSerializer.contains("Base64File"))
    }

    @Test fun `Qwen Max ordinary chat explicitly uses low bounded reasoning instead of provider xhigh default`() {
        val qwenMax = model().copy(
            providerId = com.nanzhufeng.ai.domain.ProviderId.QWEN,
            modelId = "qwen3.8-max",
            maxOutputTokens = 131_072,
        )
        val ready = QwenChatAdapter().prepare(
            qwenMax,
            listOf("user" to "分析这项投资"),
            emptyList(),
            stream = true,
        ) as ChatAdapterPrepareResult.Ready

        assertTrue(ready.jsonBody.contains("\"reasoning_effort\":\"low\""))
        assertTrue(ready.jsonBody.contains("\"preserve_thinking\":false"))
        assertTrue(ready.jsonBody.contains("\"max_completion_tokens\":16384"))
        assertFalse(ready.jsonBody.contains("\"max_tokens\":131072"))
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

    @Test fun `Qwen standard model can combine image analysis with its official chat-completions search`() {
        val attachment = ChatAttachment(ChatAttachmentKind.IMAGE, "image/png", "chart.png", byteArrayOf(1, 2, 3))
        val model = model().copy(
            providerId = com.nanzhufeng.ai.domain.ProviderId.QWEN,
            capabilities = com.nanzhufeng.ai.domain.ModelCapabilities(true, true, true, false),
        )
        val options = ChatRequestOptions(OfficialWebSearchRoute.QWEN_CHAT_COMPLETIONS)
        val ready = QwenChatAdapter().prepare(model, listOf("user" to "分析这张图"), listOf(attachment), stream = true, options = options) as ChatAdapterPrepareResult.Ready
        val output = java.io.ByteArrayOutputStream()
        ready.body.writeTo(output)
        val body = output.toString(Charsets.UTF_8)

        assertTrue(body.contains("\"enable_search\":true"))
        assertTrue(body.contains("\"forced_search\":true"))
        assertTrue(body.contains("\"image_url\":{\"url\":\"data:image/png;base64,AQID\"}"))
    }

    @Test fun `Qwen textual attachment uses a string message instead of an unsupported file part`() {
        val attachment = ChatAttachment(ChatAttachmentKind.FILE, "text/markdown", "notes.md", "# 标题\n正文".toByteArray())
        val model = model().copy(providerId = com.nanzhufeng.ai.domain.ProviderId.QWEN)
        val ready = QwenChatAdapter().prepare(model, listOf("user" to "整理这份笔记"), listOf(attachment), stream = false) as ChatAdapterPrepareResult.Ready

        assertTrue(ready.jsonBody.contains("以下是文件 notes.md 的完整 UTF-8 文本"))
        assertTrue(ready.jsonBody.contains("# 标题\\n正文"))
        assertFalse(ready.jsonBody.contains("\"type\":\"file\""))
        assertFalse(ready.jsonBody.contains("\"content\":["))
    }

    @Test fun `Markdown is a complete local text input for DeepSeek and Zhipu`() {
        val markdown = ChatAttachment(ChatAttachmentKind.FILE, "text/markdown", "notes.md", "# 标题\n正文".toByteArray())
        val deepSeek = DeepSeekChatAdapter().prepare(
            model().copy(providerId = com.nanzhufeng.ai.domain.ProviderId.DEEPSEEK, modelId = "deepseek-flash"),
            listOf("user" to "整理文件"), listOf(markdown), stream = true,
        ) as ChatAdapterPrepareResult.Ready
        val zhipu = ZhipuChatAdapter().prepare(
            model().copy(providerId = com.nanzhufeng.ai.domain.ProviderId.ZHIPU, modelId = "glm-5.3-flash"),
            listOf("user" to "整理文件"), listOf(markdown), stream = true,
        ) as ChatAdapterPrepareResult.Ready

        listOf(deepSeek.jsonBody, zhipu.jsonBody).forEach { body ->
            assertTrue(body.contains("以下是文件 notes.md 的完整 UTF-8 文本"))
            assertTrue(body.contains("# 标题\\n正文"))
            assertFalse(body.contains("\"type\":\"file\""))
        }
    }

    @Test fun `safe markup files are local text for every direct text adapter`() {
        val yaml = ChatAttachment(ChatAttachmentKind.FILE, "application/x-yaml", "config.yaml", "name: 南枫".toByteArray())
        val deepSeek = DeepSeekChatAdapter().prepare(model(), emptyList(), listOf(yaml), stream = false)
        val zhipu = ZhipuChatAdapter().prepare(
            model().copy(providerId = com.nanzhufeng.ai.domain.ProviderId.ZHIPU, modelId = "glm-5.3-flash"),
            emptyList(), listOf(yaml), stream = false,
        )

        assertTrue(deepSeek is ChatAdapterPrepareResult.Ready)
        assertTrue(zhipu is ChatAdapterPrepareResult.Ready)
        assertTrue((deepSeek as ChatAdapterPrepareResult.Ready).jsonBody.contains("name: 南枫"))
        assertTrue((zhipu as ChatAdapterPrepareResult.Ready).jsonBody.contains("name: 南枫"))
    }

    @Test fun `OpenRouter image and markdown request keeps both materials model visible`() {
        val image = ChatAttachment(ChatAttachmentKind.IMAGE, "image/png", "marked.png", byteArrayOf(1, 2, 3))
        val markdown = ChatAttachment(ChatAttachmentKind.FILE, "text/markdown", "design.md", "# 顶栏\n去掉硬边".toByteArray())
        val ready = OpenRouterChatAdapter().prepare(
            model().copy(providerId = com.nanzhufeng.ai.domain.ProviderId.OPENROUTER),
            listOf("user" to "请同时检查图片和开发文档"), listOf(image, markdown), stream = true,
        ) as ChatAdapterPrepareResult.Ready
        val output = java.io.ByteArrayOutputStream()
        ready.body.writeTo(output)
        val body = output.toString(Charsets.UTF_8)

        assertEquals(ready.body.contentLength, body.toByteArray().size.toLong())
        assertTrue(body.contains("以下是文件 design.md 的完整 UTF-8 文本"))
        assertTrue(body.contains("# 顶栏\\n去掉硬边"))
        assertTrue(body.contains("\"image_url\":{\"url\":\"data:image/png;base64,AQID\"}"))
        assertFalse(body.contains("\"filename\":\"design.md\""))
        assertFalse(body.contains("\"file_data\":\"data:text/markdown"))
    }

    @Test fun `OpenRouter rejects unknown binary file instead of pretending it was sent`() {
        val archive = ChatAttachment(ChatAttachmentKind.FILE, "application/zip", "source.zip", byteArrayOf(1, 2, 3))

        assertTrue(
            OpenRouterChatAdapter().prepare(
                model().copy(providerId = com.nanzhufeng.ai.domain.ProviderId.OPENROUTER),
                emptyList(), listOf(archive), stream = true,
            ) is ChatAdapterPrepareResult.AttachmentUnsupported,
        )
    }

    @Test fun `OpenRouter keeps every supported attachment modality in one request`() {
        val attachments = listOf(
            ChatAttachment(ChatAttachmentKind.IMAGE, "image/png", "screen.png", byteArrayOf(1, 2, 3)),
            ChatAttachment(ChatAttachmentKind.PDF, "application/pdf", "brief.pdf", "%PDF".toByteArray()),
            ChatAttachment(ChatAttachmentKind.VIDEO, "video/mp4", "clip.mp4", byteArrayOf(4, 5, 6)),
            ChatAttachment(ChatAttachmentKind.AUDIO, "audio/mpeg", "memo.mp3", byteArrayOf(7, 8, 9)),
            ChatAttachment(ChatAttachmentKind.FILE, "text/markdown", "notes.md", "# 说明".toByteArray()),
        )
        val ready = OpenRouterChatAdapter().prepare(
            model().copy(providerId = com.nanzhufeng.ai.domain.ProviderId.OPENROUTER),
            listOf("user" to "逐项检查这些材料"), attachments, stream = true,
        ) as ChatAdapterPrepareResult.Ready
        val output = java.io.ByteArrayOutputStream()
        ready.body.writeTo(output)
        val body = output.toString(Charsets.UTF_8)

        assertEquals(ready.body.contentLength, body.toByteArray().size.toLong())
        assertTrue(body.contains("\"image_url\":{\"url\":\"data:image/png;base64,AQID\"}"))
        assertTrue(body.contains("\"file_data\":\"data:application/pdf;base64,JVBERg==\""))
        assertTrue(body.contains("\"video_url\":{\"url\":\"data:video/mp4;base64,BAUG\"}"))
        assertTrue(body.contains("\"input_audio\":{\"data\":\"BwgJ\",\"format\":\"mp3\"}"))
        assertTrue(body.contains("以下是文件 notes.md 的完整 UTF-8 文本"))
        assertFalse(body.contains("\"file_data\":\"data:text/markdown"))
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
        assertFalse(body.contains("stream_options"))
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

    @Test fun `OpenRouter audio request uses input audio with raw base64 and format`() {
        val attachment = ChatAttachment(ChatAttachmentKind.AUDIO, "audio/mpeg", "memo.mp3", byteArrayOf(1, 2, 3))
        val ready = OpenRouterChatAdapter().prepare(
            model().copy(providerId = com.nanzhufeng.ai.domain.ProviderId.OPENROUTER),
            listOf("user" to "请转写"), listOf(attachment), stream = false,
        ) as ChatAdapterPrepareResult.Ready
        val output = java.io.ByteArrayOutputStream()
        ready.body.writeTo(output)
        val body = output.toString(Charsets.UTF_8)

        assertTrue(body.contains("\"type\":\"input_audio\""))
        assertTrue(body.contains("\"data\":\"AQID\",\"format\":\"mp3\""))
        assertFalse(body.contains("data:audio/mpeg;base64"))
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

    @Test fun `DeepSeek V4 point 1 Flash uses the existing official direct chat transport`() {
        val flash = model().copy(
            providerId = com.nanzhufeng.ai.domain.ProviderId.DEEPSEEK,
            modelId = "deepseek-flash",
        )
        val ready = DeepSeekChatAdapter().prepare(flash, listOf("user" to "你好"), emptyList(), stream = true) as ChatAdapterPrepareResult.Ready

        assertTrue(ready.jsonBody.contains("\"model\":\"deepseek-flash\""))
        assertTrue(ready.jsonBody.contains("\"stream\":true"))
        assertEquals(com.nanzhufeng.ai.domain.ProviderId.DEEPSEEK, ChatProviderAdapters().adapter(com.nanzhufeng.ai.domain.ProviderId.DEEPSEEK)?.providerId)
    }

    @Test fun `Zhipu GLM models explicitly lock max reasoning without pretending attachments were sent`() {
        val flagship = model().copy(providerId = com.nanzhufeng.ai.domain.ProviderId.ZHIPU, modelId = "glm-5.3")
        val flash = flagship.copy(modelId = "glm-5.3-flash")
        val text = ZhipuChatAdapter().prepare(flagship, listOf("user" to "你好"), emptyList(), stream = false) as ChatAdapterPrepareResult.Ready
        val flashText = ZhipuChatAdapter().prepare(flash, listOf("user" to "你好"), emptyList(), stream = false) as ChatAdapterPrepareResult.Ready
        val image = ChatAttachment(ChatAttachmentKind.IMAGE, "image/png", "chart.png", byteArrayOf(1, 2, 3))

        assertTrue(text.jsonBody.contains("\"model\":\"glm-5.3\""))
        assertTrue(text.jsonBody.contains("\"thinking\":{\"type\":\"enabled\"}"))
        assertTrue(text.jsonBody.contains("\"reasoning_effort\":\"max\""))
        assertTrue(flashText.jsonBody.contains("\"model\":\"glm-5.3-flash\""))
        assertTrue(flashText.jsonBody.contains("\"reasoning_effort\":\"max\""))
        assertTrue(text.jsonBody.contains("\"stream\":false"))
        assertTrue(ZhipuChatAdapter().prepare(flagship, emptyList(), listOf(image), stream = false) is ChatAdapterPrepareResult.AttachmentUnsupported)
        assertEquals(com.nanzhufeng.ai.domain.ProviderId.ZHIPU, ChatProviderAdapters().adapter(com.nanzhufeng.ai.domain.ProviderId.ZHIPU)?.providerId)

        val deepSeek = model().copy(providerId = com.nanzhufeng.ai.domain.ProviderId.DEEPSEEK, modelId = "deepseek-flash")
        val unrelated = DeepSeekChatAdapter().prepare(deepSeek, listOf("user" to "你好"), emptyList(), stream = false) as ChatAdapterPrepareResult.Ready
        assertFalse(unrelated.jsonBody.contains("\"reasoning_effort\""))
    }

    @Test fun `each explicit web route uses its own documented protocol without relaying a model`() {
        val openRouter = model().copy(providerId = com.nanzhufeng.ai.domain.ProviderId.OPENROUTER)
        val options = ChatRequestOptions(OfficialWebSearchRoute.OPENROUTER_SERVER_TOOL)
        val grounded = OpenRouterChatAdapter().prepare(openRouter, listOf("user" to "查一下最新财报"), emptyList(), stream = false, options = options) as ChatAdapterPrepareResult.Ready

        assertTrue(options.liveWebSearch)
        assertEquals(OfficialWebSearchRoute.OPENROUTER_SERVER_TOOL, options.webSearchRoute)
        assertTrue(grounded.jsonBody.contains("\"type\":\"openrouter:web_search\""))
        assertFalse(OpenRouterChatAdapter().supportsStreaming(openRouter, options))
        val qwen = QwenChatAdapter()
        val qwenOptions = ChatRequestOptions(OfficialWebSearchRoute.QWEN_RESPONSES)
        val qwenModel = model().copy(
            providerId = com.nanzhufeng.ai.domain.ProviderId.QWEN,
            modelId = "qwen3.8-max",
            capabilities = com.nanzhufeng.ai.domain.ModelCapabilities(true, false, true, false),
            maxOutputTokens = 131_072,
        )
        val qwenGrounded = qwen.prepare(qwenModel, listOf("user" to "查一下最新财报"), emptyList(), stream = true, options = qwenOptions) as ChatAdapterPrepareResult.Ready
        assertEquals(OfficialWebSearchRoute.QWEN_RESPONSES, qwenOptions.webSearchRoute)
        assertEquals("/responses", qwen.endpointPath(qwenOptions))
        assertTrue(qwen.supportsStreaming(qwenModel, qwenOptions))
        assertTrue(qwenGrounded.jsonBody.contains("\"tools\":[{\"type\":\"web_search\"}]"))
        assertTrue(qwenGrounded.jsonBody.contains("\"store\":false"))
        assertTrue(qwenGrounded.jsonBody.contains("\"stream\":true"))
        assertTrue(qwenGrounded.jsonBody.contains("\"reasoning\":{\"effort\":\"low\"}"))
        assertTrue(qwenGrounded.jsonBody.contains("\"max_output_tokens\":16384"))
        assertFalse(qwenGrounded.jsonBody.contains("\"tool_choice\""))
        assertEquals(ProviderStreamTextMode.RESPONSES_API, qwen.streamTextMode(qwenOptions))
        assertEquals(180_000, qwen.readTimeoutMillis(qwenModel, emptyList(), stream = true, options = qwenOptions))
        assertEquals(300_000, qwen.maxStreamDurationMillis(qwenModel, emptyList(), qwenOptions))
        val qwenChatSearch = ChatRequestOptions(OfficialWebSearchRoute.QWEN_CHAT_COMPLETIONS)
        assertEquals(ProviderStreamTextMode.BUFFER_QWEN_WEB_SEARCH, qwen.streamTextMode(qwenChatSearch))
        assertEquals(180_000, qwen.maxStreamDurationMillis(openRouter, emptyList(), qwenChatSearch))
        val qwen38ChatSearch = qwen.prepare(qwenModel, listOf("user" to "查一下最新财报"), emptyList(), stream = true, options = qwenChatSearch) as ChatAdapterPrepareResult.Ready
        assertTrue(qwen38ChatSearch.jsonBody.contains("\"enable_search\":true"))
        assertTrue(qwen38ChatSearch.jsonBody.contains("\"forced_search\":true"))
        assertTrue(qwen38ChatSearch.jsonBody.contains("\"reasoning_effort\":\"low\""))
        assertTrue(qwen38ChatSearch.jsonBody.contains("\"max_completion_tokens\":16384"))

        val deepSeek = DeepSeekChatAdapter()
        val deepSeekOptions = ChatRequestOptions(OfficialWebSearchRoute.DEEPSEEK_RESPONSES)
        val deepSeekDirect = deepSeek.prepare(model(), listOf("user" to "复杂推理"), emptyList(), stream = false, options = deepSeekOptions) as ChatAdapterPrepareResult.Ready
        assertEquals(OfficialWebSearchRoute.DEEPSEEK_RESPONSES, deepSeekOptions.webSearchRoute)
        assertEquals(com.nanzhufeng.ai.domain.ProviderId.DEEPSEEK, deepSeek.executionProviderId(deepSeekOptions))
        assertEquals("/responses", deepSeek.endpointPath(deepSeekOptions))
        assertFalse(deepSeek.supportsStreaming(openRouter, deepSeekOptions))
        assertTrue(deepSeekDirect.jsonBody.contains("\"tools\":[{\"type\":\"web_search\"}]"))
        assertTrue(deepSeekDirect.jsonBody.contains("\"tool_choice\":{\"type\":\"web_search\"}"))
        assertTrue(deepSeekDirect.jsonBody.contains("\"input\":[{\"role\":\"user\""))
        assertFalse(deepSeekDirect.jsonBody.contains("\"messages\":"))

        val zhipu = ZhipuChatAdapter()
        val zhipuOptions = ChatRequestOptions(OfficialWebSearchRoute.ZHIPU_CHAT_COMPLETIONS)
        val zhipuGrounded = zhipu.prepare(
            model().copy(providerId = com.nanzhufeng.ai.domain.ProviderId.ZHIPU, modelId = "glm-5.3"),
            listOf("user" to "查一下最新财报"), emptyList(), stream = false, options = zhipuOptions,
        ) as ChatAdapterPrepareResult.Ready
        assertEquals(OfficialWebSearchRoute.ZHIPU_CHAT_COMPLETIONS, zhipuOptions.webSearchRoute)
        assertTrue(zhipuGrounded.jsonBody.contains("\"type\":\"web_search\""))
        assertTrue(zhipuGrounded.jsonBody.contains("\"search_engine\":\"search_std\""))
        assertTrue(zhipuGrounded.jsonBody.contains("\"search_result\":true"))
        assertTrue(zhipuGrounded.jsonBody.contains("\"tool_choice\":\"auto\""))
        assertTrue(zhipuGrounded.jsonBody.contains("\"thinking\":{\"type\":\"enabled\"}"))
        assertTrue(zhipuGrounded.jsonBody.contains("\"reasoning_effort\":\"max\""))
    }

    @Test fun `Claude and ChatGPT deep selections request High without adding a web tool`() {
        val adapter = OpenRouterChatAdapter()
        val deepChoices = com.nanzhufeng.ai.domain.ComposerModelRoutingCatalog.deep.filter { choice ->
            choice.label in setOf("Claude Fable 5.1", "Claude Opus 5", "GPT-6 Astra", "GPT-5.6 Sol")
        }

        assertEquals(4, deepChoices.size)
        deepChoices.forEach { choice ->
            val options = adapter.requestOptions(model().copy(providerId = com.nanzhufeng.ai.domain.ProviderId.OPENROUTER), choice)
            assertEquals(ReasoningEffort.HIGH, options.reasoningEffort)
            assertFalse(options.liveWebSearch)
        }
        val text = adapter.prepare(
            model().copy(providerId = com.nanzhufeng.ai.domain.ProviderId.OPENROUTER),
            listOf("user" to "深入分析"), emptyList(), stream = false,
            options = ChatRequestOptions(reasoningEffort = ReasoningEffort.HIGH),
        ) as ChatAdapterPrepareResult.Ready
        assertTrue(text.jsonBody.contains("\"reasoning\":{\"effort\":\"high\"}"))
        assertEquals(ChatRequestOptions.Standard, QwenChatAdapter().requestOptions(model(), deepChoices.first()))
        assertEquals(ChatRequestOptions.Standard, DeepSeekChatAdapter().requestOptions(model(), deepChoices.first()))
    }

    @Test fun `DeepSeek Responses result keeps final text and structured public search sources`() {
        val decoded = DeepSeekChatAdapter().decodeNonStreaming(
            """{"output_text":"已完成检索。","output":[{"type":"web_search_call","action":{"sources":[{"url":"https://example.test/notice","title":"官方公告"}]}},{"type":"message","content":[{"type":"output_text","text":"已完成检索。"}]}],"usage":{"input_tokens":8,"input_tokens_details":{"cached_tokens":3},"output_tokens":5}}""",
        ) as? ChatAdapterDecodedResult.Text

        assertEquals("已完成检索。", decoded?.text)
        assertEquals(8L, decoded?.inputTokens)
        assertEquals(3L, decoded?.cachedInputTokens)
        assertEquals(5L, decoded?.outputTokens)
        assertEquals(listOf(ProviderWebSource("https://example.test/notice", "官方公告")), decoded?.webSources)
    }

    @Test fun `DeepSeek Responses keeps a completed server search without inventing public URLs`() {
        val decoded = DeepSeekChatAdapter().decodeNonStreaming(
            """{"output":[{"type":"web_search_call","action":{"type":"search","query":"最新汇率"}},{"type":"message","content":[{"type":"output_text","text":"南烛枫，美元汇率请以银行实时报价为准。"}]}],"usage":{"input_tokens":8,"output_tokens":5}}""",
        ) as? ChatAdapterDecodedResult.Text

        assertEquals("南烛枫，美元汇率请以银行实时报价为准。", decoded?.text)
        assertTrue(decoded?.webSources.isNullOrEmpty())
        assertTrue(
            WebSearchGroundingPolicy.hasRequiredSources(
                ChatRequestOptions(OfficialWebSearchRoute.DEEPSEEK_RESPONSES),
                decoded?.webSources.orEmpty(),
            ),
        )
    }

    @Test fun `Responses reasoning is retained separately and never concatenated into final reply`() {
        val decoded = DeepSeekChatAdapter().decodeNonStreaming(
            """{"output":[{"type":"reasoning","content":[{"type":"reasoning_text","text":"Let me search the latest index data first."}]},{"type":"message","content":[{"type":"output_text","text":"南烛枫，结论如下。"}]}],"usage":{"input_tokens":8,"output_tokens":5}}""",
        ) as? ChatAdapterDecodedResult.Text

        assertEquals("南烛枫，结论如下。", decoded?.text)
        assertEquals("Let me search the latest index data first.", decoded?.reasoning)
        assertFalse(decoded?.text.orEmpty().contains("Let me search"))
    }

    @Test fun `Qwen Responses result keeps its public web-search source separately from model prose`() {
        val decoded = QwenChatAdapter().decodeNonStreaming(
            """{"output_text":"已根据实时来源完成检索。","output":[{"type":"web_search_call","action":{"sources":[{"url":"https://news.example.test/item","title":"公告"}]}}],"usage":{"input_tokens":9,"output_tokens":3}}""",
        ) as? ChatAdapterDecodedResult.Text
        assertEquals("已根据实时来源完成检索。", decoded?.text)
        assertEquals(9L, decoded?.inputTokens)
        assertEquals(3L, decoded?.outputTokens)
        assertEquals(listOf(ProviderWebSource("https://news.example.test/item", "公告")), decoded?.webSources)
    }

    @Test fun `Qwen Responses rejects a tool-trace-only payload instead of persisting it as an answer`() {
        val decoded = QwenChatAdapter().decodeNonStreaming(
            """{"output_text":"准备检索。<tool_use>{}</tool_use><tool_result>{}</tool_result>","output":[],"usage":{"input_tokens":9,"output_tokens":30}}""",
        )

        assertEquals(ChatAdapterDecodedResult.EmptyOrMalformed, decoded)
    }

    @Test fun `Qwen Responses stream exposes only answer deltas and terminal metadata`() {
        val qwen = QwenChatAdapter()
        val options = ChatRequestOptions(OfficialWebSearchRoute.QWEN_RESPONSES)
        val delta = qwen.decodeStreamingEvent(
            """{"type":"response.output_text.delta","delta":"南烛枫，结论如下。"}""",
            options,
        )
        val tool = qwen.decodeStreamingEvent(
            """{"type":"response.web_search_call.searching","sequence_number":3}""",
            options,
        )
        val completed = qwen.decodeStreamingEvent(
            """{"type":"response.completed","response":{"output":[{"type":"web_search_call","action":{"sources":[{"url":"https://example.test/source","title":"官方来源"}]}}],"usage":{"input_tokens":9,"output_tokens":5,"output_tokens_details":{"reasoning_tokens":2}}}}""",
            options,
        )

        assertEquals("南烛枫，结论如下。", delta?.text)
        assertEquals(null, tool)
        assertEquals(ProviderStreamTerminal.COMPLETED, completed?.terminal)
        assertEquals(9L, completed?.inputTokens)
        assertEquals(5L, completed?.outputTokens)
        assertEquals(2L, completed?.reasoningTokens)
        assertEquals(listOf(ProviderWebSource("https://example.test/source", "官方来源")), completed?.webSources)
    }

    @Test fun `Zhipu chat web search returns official sources without putting them in answer prose`() {
        val adapter = ZhipuChatAdapter()
        val decoded = adapter.decodeNonStreaming(
            """{"choices":[{"message":{"content":"已核验。"}}],"web_search":[{"search_result":[{"title":"官方公告","link":"https://example.test/official"}]}],"usage":{"prompt_tokens":6,"completion_tokens":2}}""",
        ) as? ChatAdapterDecodedResult.Text

        assertEquals("已核验。", decoded?.text)
        assertEquals(listOf(ProviderWebSource("https://example.test/official", "官方公告")), decoded?.webSources)

        val streamedSources = adapter.decodeStreamingEvent(
            """{"choices":[{"delta":{}}],"web_search":[{"search_result":[{"title":"政策原文","link":"https://example.test/policy"}]}]}""",
        )
        assertEquals(listOf(ProviderWebSource("https://example.test/policy", "政策原文")), streamedSources?.webSources)
    }

    @Test fun `attachment budget comes from model profile metadata rather than adapter constants`() {
        val profile = com.nanzhufeng.ai.domain.AttachmentInputTokenEstimate(10, 1, 20, 2, 30, 3)
        assertEquals(10, ChatAttachment(ChatAttachmentKind.IMAGE, "image/png", "a.png", ByteArray(1)).estimatedInputTokens(profile))
        assertEquals(20, ChatAttachment(ChatAttachmentKind.PDF, "application/pdf", "a.pdf", ByteArray(1)).estimatedInputTokens(profile))
        assertEquals(30, ChatAttachment(ChatAttachmentKind.VIDEO, "video/mp4", "a.mp4", ByteArray(1)).estimatedInputTokens(profile))
        assertEquals(30, ChatAttachment(ChatAttachmentKind.AUDIO, "audio/mpeg", "a.mp3", ByteArray(1)).estimatedInputTokens(profile))
        assertEquals(20, ChatAttachment(ChatAttachmentKind.FILE, "text/plain", "a.txt", ByteArray(1)).estimatedInputTokens(profile))
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

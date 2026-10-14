package com.nanzhufeng.ai.ai

import java.io.ByteArrayInputStream
import java.net.SocketTimeoutException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProviderSseDecoderContractsTest {
    @Test fun `typed Responses error cannot become unknown missing completion`() {
        val adapter=QwenChatAdapter();val options=ChatRequestOptions(OfficialWebSearchRoute.QWEN_RESPONSES)
        val stream="data: {\"type\":\"error\",\"code\":\"rate_limit_exceeded\"}\n\n".byteInputStream()
        val result=ProviderSseDecoder.read(stream,{}, {adapter.decodeStreamingEvent(it,options)},ProviderStreamTextMode.RESPONSES_API)
        assertEquals("STREAM_PROVIDER_ERROR",result.finishReason)
    }
    @Test fun `DeepSeek search stream exposes only text following the last native result`() {
        val wire="""data: {"type":"content_block_delta","delta":{"type":"text_delta","text":"intermediate"}}

data: {"type":"content_block_start","content_block":{"type":"web_search_tool_result","content":[{"type":"web_search_result","url":"https://example.org/a","title":"Official"}]}}

data: {"type":"content_block_delta","delta":{"type":"text_delta","text":"final"}}

data: {"type":"message_delta","delta":{"stop_reason":"end_turn"}}

data: {"type":"message_stop"}

"""
        val options=ChatRequestOptions(OfficialWebSearchRoute.DEEPSEEK_MESSAGES,explicitSearchQuery="public query");val adapter=DeepSeekChatAdapter();val visible=StringBuilder()
        val result=ProviderSseDecoder.read(wire.byteInputStream(),{visible.append(it)},{adapter.decodeStreamingEvent(it,options)},adapter.streamTextMode(options))
        assertEquals("final",result.text);assertEquals("final",visible.toString());assertEquals(1,result.webSources.size)
    }
    @Test fun `sensitive finish retains content filter reason through the production stream decoder`() {
        val input=ByteArrayInputStream("data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"sensitive\"}]}\n\ndata: [DONE]\n\n".toByteArray())
        val reply=ProviderSseDecoder.read(input,{}, {ZhipuChatAdapter().decodeStreamingEvent(it)},ProviderStreamTextMode.COMPLETIONS_API)
        assertEquals("CONTENT_FILTER",reply.finishReason)
        assertEquals("",reply.text)
    }
    @Test fun `fully received native terminal does not wait for missing blank separator`() {
        val terminal="""{"type":"response.completed","response":{"status":"completed","model":"gpt-6.1-sol","usage":{"input_tokens":5,"output_tokens":2},"output":[{"type":"web_search_call","status":"completed"},{"type":"message","content":[{"type":"output_text","text":"完成正文","annotations":[{"type":"url_citation","url":"https://example.test/source"}]}]}]}}"""
        val bytes="data: $terminal\n".toByteArray()
        val input=object:java.io.InputStream(){var offset=0
            override fun read():Int=if(offset<bytes.size) bytes[offset++].toInt() and 255 else throw SocketTimeoutException("no trailing separator")
            override fun read(target:ByteArray,start:Int,count:Int):Int {if(offset==bytes.size) throw SocketTimeoutException("no trailing separator");val size=minOf(count,bytes.size-offset);bytes.copyInto(target,start,offset,offset+size);offset+=size;return size}
        }
        val adapter=RelayCoreChatAdapter(com.nanzhufeng.ai.domain.ProviderId.RELAYCORE_GPT);val options=ChatRequestOptions(OfficialWebSearchRoute.RELAYCORE_GPT_RESPONSES)
        val reply=ProviderSseDecoder.read(input,{}, {adapter.decodeStreamingEvent(it,options)},ProviderStreamTextMode.RESPONSES_API)
        assertEquals("COMPLETED",reply.finishReason);assertTrue(reply.webSearchPerformed);assertEquals("gpt-6.1-sol",reply.returnedModelId)
    }

    @Test fun `Messages DONE cannot replace both end turn and message stop`() {
        for (endTurn in listOf("", "data: end-turn\n\n")) {
            val input=ByteArrayInputStream("data: answer\n\n${endTurn}data: [DONE]\n\n".toByteArray())
            val reply=ProviderSseDecoder.read(input,{}, { data -> when(data) {
                "answer" -> ProviderSseEvent(text="部分正文")
                "end-turn" -> ProviderSseEvent(messageEndTurn=true)
                else -> null
            } },ProviderStreamTextMode.MESSAGES_API)
            assertEquals("部分正文",reply.text);assertEquals("MISSING_COMPLETION",reply.finishReason)
        }
    }

    @Test fun `native final event completes without waiting for connection EOF`() {
        for (mode in listOf(ProviderStreamTextMode.MESSAGES_API, ProviderStreamTextMode.RESPONSES_API)) {
            val bytes = "data: answer\n\ndata: end-turn\n\ndata: final\n\n".toByteArray()
            val input = object : java.io.InputStream() {
                var offset = 0
                override fun read(): Int = if (offset < bytes.size) bytes[offset++].toInt() and 255 else throw SocketTimeoutException("socket still open")
                override fun read(target: ByteArray, start: Int, count: Int): Int {
                    if (offset == bytes.size) throw SocketTimeoutException("socket still open")
                    val size = minOf(count, bytes.size-offset); bytes.copyInto(target,start,offset,offset+size);offset+=size;return size
                }
            }
            val reply = ProviderSseDecoder.read(input, {}, { data -> when(data) {
                "answer" -> ProviderSseEvent(text="完成正文")
                "end-turn" -> ProviderSseEvent(messageEndTurn=true,outputTokens=8)
                "final" -> ProviderSseEvent(terminal=ProviderStreamTerminal.COMPLETED)
                else -> null
            } },mode)
            assertEquals("完成正文",reply.text);assertEquals("COMPLETED",reply.finishReason);assertEquals(8L,reply.outputTokens)
        }
    }

    @Test fun `provider DONE completes without waiting for connection EOF`() {
        val bytes = "data: answer\n\ndata: usage\n\ndata: [DONE]\n\n".toByteArray()
        val input = object : java.io.InputStream() {
            var offset = 0
            override fun read(): Int = if (offset < bytes.size) bytes[offset++].toInt() and 255
                else throw SocketTimeoutException("completed provider kept socket open")
            override fun read(target: ByteArray, start: Int, count: Int): Int {
                if (offset == bytes.size) throw SocketTimeoutException("completed provider kept socket open")
                val size = minOf(count, bytes.size - offset)
                bytes.copyInto(target, start, offset, offset + size); offset += size; return size
            }
        }
        val visible = mutableListOf<String>()
        val reply = ProviderSseDecoder.read(input, visible::add, { data -> when (data) {
            "answer" -> ProviderSseEvent(text = "已完成回答", terminal = ProviderStreamTerminal.COMPLETED)
            "usage" -> ProviderSseEvent(inputTokens = 10, outputTokens = 5)
            else -> null
        } }, ProviderStreamTextMode.COMPLETIONS_API)
        assertEquals(listOf("已完成回答"), visible)
        assertEquals("COMPLETED", reply.finishReason)
        assertEquals(10L, reply.inputTokens); assertEquals(5L, reply.outputTokens)
    }

    private fun frames(vararg values: String, beforeFrame: (Int) -> Unit): java.io.InputStream = object : java.io.InputStream() {
        var index = 0; var offset = 0
        override fun read(): Int { val one=ByteArray(1);return if(read(one,0,1)<0) -1 else one[0].toInt() and 255 }
        override fun read(bytes: ByteArray, start: Int, length: Int): Int {
            if(index>=values.size) return -1
            if(offset==0) beforeFrame(index)
            val current=values[index].toByteArray();val count=minOf(length,current.size-offset)
            current.copyInto(bytes,start,offset,offset+count);offset+=count
            if(offset==current.size){index++;offset=0};return count
        }
    }

    @Test fun `Qwen ordinary answer appears before the next input frame without tool XML`() {
        val visible=mutableListOf<String>()
        val input=frames(
            "data: {\"choices\":[{\"delta\":{\"content\":\"第一段<tool_\"}}]}\n\n",
            "data: {\"choices\":[{\"delta\":{\"content\":\"use>private</tool_use>第二段\"}}]}\n\n",
            "data: [DONE]\n\n",
            beforeFrame={if(it==1) assertEquals(listOf("第一段"),visible);if(it==2) assertEquals(listOf("第一段","第二段"),visible)},
        )
        val result=ProviderSseDecoder.read(input,visible::add,QwenChatAdapter()::decodeStreamingEvent,ProviderStreamTextMode.FILTER_QWEN_WEB_SEARCH)
        assertEquals("第一段第二段",result.text)
    }

    @Test fun `DeepSeek Messages streams final prose and retains grounding identity usage and terminal`() {
        val adapter=DeepSeekChatAdapter();val options=ChatRequestOptions(OfficialWebSearchRoute.DEEPSEEK_MESSAGES)
        val model=com.nanzhufeng.ai.domain.ResolvedModel(com.nanzhufeng.ai.domain.ProviderId.DEEPSEEK,"deepseek-flash","Flash",com.nanzhufeng.ai.domain.ModelCapabilities(true,false,true),null,com.nanzhufeng.ai.domain.ModelHealth.UNKNOWN,null,null)
        assertTrue(adapter.supportsStreaming(model,options))
        assertTrue((adapter.prepare(model,listOf("user" to "Public question"),emptyList(),true,options) as ChatAdapterPrepareResult.Ready).jsonBody.contains("\"stream\":true"))
        val prefix="""data: {"type":"message_start","message":{"model":"deepseek-flash","usage":{"input_tokens":8,"cache_read_input_tokens":2}}}

data: {"type":"content_block_start","index":1,"content_block":{"type":"web_search_tool_result","content":[{"type":"web_search_result","url":"https://example.test/a","title":"Official"}]}}

data: {"type":"content_block_delta","index":2,"delta":{"type":"text_delta","text":"第一段"}}

"""
        val tail="""data: {"type":"content_block_delta","index":2,"delta":{"type":"text_delta","text":"第二段"}}

"""
        val terminal="""data: {"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"output_tokens":5}}

data: {"type":"message_stop"}

"""
        for(finish in listOf(terminal,"",terminal.replace("end_turn","max_tokens"),"data: {\"type\":\"error\",\"error\":{\"message\":\"private\"}}\n\n"+terminal)) {
            val visible=mutableListOf<String>();val input=frames(prefix,tail,finish.ifEmpty {": EOF\n\n"},beforeFrame={if(it==1)assertEquals(listOf("第一段"),visible);if(it==2)assertEquals(listOf("第一段","第二段"),visible)})
            val reply=ProviderSseDecoder.read(input,visible::add,{adapter.decodeStreamingEvent(it,options)},adapter.streamTextMode(options))
            assertEquals("第一段第二段",reply.text);assertEquals("deepseek-flash",reply.returnedModelId)
            assertEquals(10L,reply.inputTokens);assertEquals("https://example.test/a",reply.webSources.single().url);assertTrue(reply.webSearchPerformed)
            assertEquals(when {finish.isEmpty()->"MISSING_COMPLETION";finish.contains("max_tokens")->"INCOMPLETE";finish.contains("private")->"FAILED";else->"COMPLETED"},reply.finishReason)
        }
    }

    @Test fun `OpenRouter web answer uses real stream with citations and terminal failure`() {
        val adapter = OpenRouterChatAdapter()
        val model = com.nanzhufeng.ai.domain.ResolvedModel(com.nanzhufeng.ai.domain.ProviderId.OPENROUTER,"fixture-model","Fixture",com.nanzhufeng.ai.domain.ModelCapabilities(true,false,true),null,com.nanzhufeng.ai.domain.ModelHealth.UNKNOWN,null,null)
        val options = ChatRequestOptions(OfficialWebSearchRoute.OPENROUTER_SERVER_TOOL)
        assertTrue(adapter.supportsStreaming(model,options))
        val sources = adapter.decodeStreamingEvent("""{"choices":[{"delta":{"annotations":[{"type":"url_citation","url_citation":{"url":"https://example.test/a","title":"Official"}}]}}]}""")!!
        assertEquals("https://example.test/a",sources.webSources.single().url)
        assertEquals(ProviderStreamTerminal.FAILED,adapter.decodeStreamingEvent("""{"error":{"code":"server_error","message":"private body"}}""")!!.terminal)
    }

    @Test fun `completed provider search is retained without URLs while pending and failed search are not confirmed`() {
        val options = ChatRequestOptions(OfficialWebSearchRoute.QWEN_RESPONSES)
        for ((status, expected) in listOf("completed" to true, "failed" to false, "in_progress" to false)) {
            val payload = "data: {\"type\":\"response.output_text.delta\",\"delta\":\"Answer with https://example.test in prose\"}\n\n" +
                "data: {\"type\":\"response.completed\",\"response\":{\"output\":[{\"type\":\"web_search_call\",\"status\":\"$status\"}]}}\n\n"
            val result = ProviderSseDecoder.read(ByteArrayInputStream(payload.toByteArray()), {},
                { QwenChatAdapter().decodeStreamingEvent(it, options) }, ProviderStreamTextMode.RESPONSES_API)
            assertEquals(expected, result.webSearchPerformed)
            assertTrue(result.webSources.isEmpty())
            assertEquals("COMPLETED", result.finishReason)
        }
    }

    @Test fun `all direct adapters preserve explicit truncation instead of completing partial text`() {
        for (adapter in listOf(OpenRouterChatAdapter(), QwenChatAdapter(), DeepSeekChatAdapter(), ZhipuChatAdapter())) {
            val payload = "data: {\"choices\":[{\"delta\":{\"content\":\"partial\"},\"finish_reason\":\"length\"}]}\n\ndata: [DONE]\n\n"
            val result = ProviderSseDecoder.read(ByteArrayInputStream(payload.toByteArray()), {}, adapter::decodeStreamingEvent)
            assertEquals("partial", result.text)
            assertEquals("INCOMPLETE", result.finishReason)
        }
    }

    @Test fun `fragmented OpenRouter tool calls are reconstructed with reasoning for K3 continuation`() {
        val payload = """
            data: {"choices":[{"delta":{"reasoning_content":"先查资料","tool_calls":[{"index":0,"id":"call_1","function":{"name":"lookup","arguments":"{\"q\":"}}]}}]}

            data: {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"\"K3\"}"}}]}}]}

            data: {"choices":[{"delta":{"content":"已完成"}}]}

            data: [DONE]
        """.trimIndent()

        val result = ProviderSseDecoder.read(ByteArrayInputStream(payload.toByteArray()), { }, OpenRouterChatAdapter()::decodeStreamingEvent)

        assertEquals("先查资料", result.reasoning)
        assertEquals("已完成", result.text)
        assertEquals(ChatToolCall("call_1", "lookup", "{\"q\":\"K3\"}"), result.toolCalls.single())
    }

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

    @Test fun `Qwen web search XML is filtered while prose remains incremental`() {
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
            ProviderStreamTextMode.FILTER_QWEN_WEB_SEARCH,
        )

        assertEquals("我先梳理条件。", result.text)
        assertEquals(listOf("我先梳理条件。"),visibleDeltas)
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
            ProviderStreamTextMode.FILTER_QWEN_WEB_SEARCH,
        )

        assertEquals("准备检索。南烛枫，最终结论。", result.text)
        assertEquals(listOf("准备检索。","南烛枫，最终结论。"), visibleDeltas)
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

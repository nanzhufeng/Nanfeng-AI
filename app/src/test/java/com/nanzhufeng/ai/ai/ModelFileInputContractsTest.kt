package com.nanzhufeng.ai.ai

import com.nanzhufeng.ai.domain.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ModelFileInputContractsTest {
    @Test fun `every provider receives complete unicode file once beyond preview length`() {
        val body = "# 完整文件🙂\r\n" + "完整行🙂\n".repeat(1000) + "RC-FILE-TAIL-816"
        val file = ChatAttachment(ChatAttachmentKind.FILE,"text/markdown","report.md",body.toByteArray())
        val cases=listOf(ProviderId.OPENROUTER to "anthropic/claude-opus-5.5", ProviderId.DEEPSEEK to "deepseek-flash",
            ProviderId.QWEN to "qwen3.8-max", ProviderId.ZHIPU to "glm-5.3-flash") + RelayCoreModels.specs.map {it.provider to it.apiId}
        for ((provider,id) in cases) {
            val model=ResolvedModel(provider,id,id,ModelCapabilities(true,false,true),200000,ModelHealth.AVAILABLE,null,null,maxOutputTokens=8192)
            val bridged=UniversalChatAttachmentBridge().resolve(ConversationId("fixture"),model,"分析报告",listOf(file),false) as ChatAttachmentBridgeResult.Ready
            assertTrue(bridged.providerAttachments.isEmpty());assertTrue(bridged.receivers.isEmpty())
            val adapter=ChatProviderAdapters().adapter(provider)!!
            val ready=adapter.prepare(model,listOf("system" to "fixed-system","user" to "分析报告","user" to bridged.contextText),bridged.providerAttachments,true) as ChatAdapterPrepareResult.Ready
            val values=mutableListOf<String>()
            fun collect(value:Any?) {
                when(value) {
                    is String -> values+=value
                    is org.json.JSONArray -> (0 until value.length()).forEach {collect(value.get(it))}
                    is org.json.JSONObject -> value.keys().forEach {collect(value.get(it))}
                }
            }
            collect(org.json.JSONObject(ready.jsonBody))
            assertEquals("$provider/$id full file sent once",1,values.count {it.contains(body)})
            assertEquals("$provider/$id tail sent once",1,values.count {it.contains("RC-FILE-TAIL-816")})
            assertTrue(values.contains("fixed-system"))
        }
    }

    @Test fun `invalid UTF8 fails locally before every provider can receive a replacement text`() {
        val file=ChatAttachment(ChatAttachmentKind.FILE,"text/markdown","broken.md",byteArrayOf(0xC3.toByte(),0x28))
        for(provider in listOf(ProviderId.OPENROUTER,ProviderId.DEEPSEEK,ProviderId.QWEN,ProviderId.ZHIPU)+RelayCoreModels.providers) {
            val model=ResolvedModel(provider,"fixture","fixture",ModelCapabilities(true,false,true),200000,ModelHealth.AVAILABLE,null,null)
            assertEquals(ChatAttachmentBridgeResult.Failed(ChatAttachmentBridgeResult.Failure.FORMAT),
                UniversalChatAttachmentBridge().resolve(ConversationId("fixture"),model,"分析报告",listOf(file),false))
        }
    }
}

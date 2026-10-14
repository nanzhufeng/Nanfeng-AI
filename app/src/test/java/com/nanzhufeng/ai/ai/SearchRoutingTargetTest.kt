package com.nanzhufeng.ai.ai

import com.nanzhufeng.ai.domain.*
import org.junit.Assert.*
import org.junit.Test

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
class SearchRoutingTargetTest {
    @Test fun `RC attachments use companion while Sol and Astra text keep native`() {
        val file=ChatAttachment(ChatAttachmentKind.FILE,"text/plain","public.txt","Python 3.14.0".toByteArray())
        RelayCoreModels.specs.forEach { spec ->
            assertEquals(spec.apiId,OfficialWebSearchRoute.RELAYCORE_COMPANION,AutomaticWebSearchPolicy.requestOptions(spec.provider,ChatRequestOptions.Standard,true,"核验",listOf(file),spec.apiId).webSearchRoute)
            val textRoute=AutomaticWebSearchPolicy.requestOptions(spec.provider,ChatRequestOptions.Standard,true,"核验",emptyList(),spec.apiId).webSearchRoute
            assertEquals(if(spec.provider==ProviderId.RELAYCORE_GPT) OfficialWebSearchRoute.RELAYCORE_GPT_RESPONSES else OfficialWebSearchRoute.RELAYCORE_COMPANION,textRoute)
        }
        assertEquals(ModelPresetId.GPT_6_LUNA,RelayCoreWebSearchCompanion.preset)
    }
    @Test fun `normal channels retain their own search for attachments`() {
        val file=ChatAttachment(ChatAttachmentKind.FILE,"text/plain","public.txt","Python 3.14.0".toByteArray())
        mapOf(ProviderId.OPENROUTER to OfficialWebSearchRoute.OPENROUTER_SERVER_TOOL,ProviderId.QWEN to OfficialWebSearchRoute.QWEN_RESPONSES,ProviderId.ZHIPU to OfficialWebSearchRoute.ZHIPU_CHAT_COMPLETIONS,ProviderId.DEEPSEEK to OfficialWebSearchRoute.DEEPSEEK_MESSAGES).forEach {(provider,route)->
            assertEquals(route,AutomaticWebSearchPolicy.requestOptions(provider,ChatRequestOptions.Standard,true,"核验",listOf(file)).webSearchRoute)
        }
    }

    @Test fun `actual Luna annotations bind all four items and prose URLs cannot replace missing citations`() {
        val fixture=org.json.JSONObject(java.io.File("../protocol/fixtures/openrouter-luna-sources-20261013.json").readText())
        val queries=fixture.getJSONArray("queries")
        val plan=AttachmentWebSearchPlan.Plan((0 until queries.length()).map {AttachmentWebSearchPlan.Item("A${it+1}",queries.getString(it),emptyList())})
        val results=fixture.getJSONArray("results")
        for(i in 0 until results.length()) {
            val response=results.getJSONObject(i).getJSONObject("response")
            val reply=OpenRouterChatAdapter().decodeNonStreaming(response.toString()) as ChatAdapterDecodedResult.Text
            assertEquals("openai/gpt-6-luna",reply.returnedModelId)
            assertEquals(4,reply.webSources.size)
            val entries=linkedMapOf<String,String>()
            assertNotNull(AttachmentWebSearchPlan.evidence(plan,reply.text,reply.webSources.map {it.url},onItem={id,evidence->entries[id]=evidence}))
            assertEquals(listOf("A1","A2","A3","A4"),entries.keys.toList())
            assertTrue(entries["A1"]!!.contains("ec.europa.eu"));assertTrue(entries["A2"]!!.contains("python.org"))
            assertTrue(entries["A3"]!!.contains("microsoft.com"));assertTrue(entries["A4"]!!.contains("apple.com"))
            assertNull(AttachmentWebSearchPlan.evidence(plan,reply.text,emptyList()))
        }
    }
}

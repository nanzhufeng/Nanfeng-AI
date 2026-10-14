package com.nanzhufeng.ai.ai

import com.nanzhufeng.ai.domain.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GeminiNativeSearchContractsTest {
    private fun model() = ResolvedModel(ProviderId.OPENROUTER,"google/gemini-3.8-flash","Gemini",ModelCapabilities(true,true,false,false),8192,ModelHealth.AVAILABLE,null,null)
    @Test fun `Gemini search toggle selects native grounding for text files and media without optional server tool`() {
        val selected=model().copy(providerId=com.nanzhufeng.ai.domain.ProviderId.OPENROUTER,modelId="google/gemini-3.8-flash",capabilities=model().capabilities.copy(supportsVision=true))
        val files=listOf(ChatAttachment(ChatAttachmentKind.FILE,"text/markdown","one.md","first fact".toByteArray()),ChatAttachment(ChatAttachmentKind.FILE,"application/json","two.json","{\"second\":true}".toByteArray()))
        val image=ChatAttachment(ChatAttachmentKind.IMAGE,"image/png","public.png",byteArrayOf(1,2,3))
        for(attachments in listOf(emptyList(),files,files+image)) for(stream in listOf(false,true)) for(search in listOf(false,true)) {
            val options=if(search) ChatRequestOptions(OfficialWebSearchRoute.OPENROUTER_SERVER_TOOL) else ChatRequestOptions.Standard
            val ready=OpenRouterChatAdapter().prepare(selected,listOf("user" to "力帆口碑很差吗？和理想有什么关系？"),attachments,stream,options) as ChatAdapterPrepareResult.Ready
            val output=java.io.ByteArrayOutputStream();ready.body.writeTo(output)
            val body=org.json.JSONObject(output.toString(Charsets.UTF_8))
            assertEquals(selected.modelId,body.getString("model"));assertEquals(stream,body.getBoolean("stream"))
            assertEquals(search,body.has("plugins"));assertFalse(body.has("tools"));assertFalse(body.has("tool_choice"))
            if(search) {val plugin=body.getJSONArray("plugins");assertEquals(1,plugin.length());assertEquals("web",plugin.getJSONObject(0).getString("id"));assertEquals("native",plugin.getJSONObject(0).getString("engine"))}
            assertTrue(body.toString().contains("力帆口碑很差吗？和理想有什么关系？"))
            if(attachments.isNotEmpty()){assertTrue(body.toString().contains("first fact"));assertTrue(body.toString().contains("second"))}
            if(image in attachments) assertTrue(body.toString().contains("AQID"))
        }
        val grounded=(OpenRouterChatAdapter().prepare(selected,listOf("user" to "已有证据回答"),emptyList(),false,ChatRequestOptions(OfficialWebSearchRoute.OPENROUTER_SERVER_TOOL,groundedAnswer=true)) as ChatAdapterPrepareResult.Ready).jsonBody
        assertFalse(org.json.JSONObject(grounded).has("plugins"));assertFalse(org.json.JSONObject(grounded).has("tools"))
    }

}

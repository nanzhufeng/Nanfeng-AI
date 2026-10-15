package com.nanzhufeng.ai.ai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nanzhufeng.ai.data.AndroidModelProfileDirectory
import com.nanzhufeng.ai.domain.*
import java.io.ByteArrayOutputStream
import java.io.File
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Production resolver, bundled profiles, route policy and adapters; no credentials or network. */
@RunWith(RobolectricTestRunner::class)
class CurrentModelNetworkReadinessTest {
    @Test fun `attachment reading accepts only the verified RelayCore Opus alias pair`() {
        val selected=requireNotNull(AndroidModelProfileDirectory(ApplicationProvider.getApplicationContext<Context>()).profile(ModelPresetId.RELAYCORE_OPUS_5_5))
        for(returned in listOf("claude-opus-5-5","claude-sonnet-5-5")) {
            val transport=object:ProviderChatTransport {
                override fun execute(request:ProviderChatRequest,credential:CharArray):ProviderChatOutcome =
                    ProviderChatOutcome.StreamedResponse(200,"印象派",null,100,20,null,false,finishReason="COMPLETED",returnedModelId=returned)
            }
            val result=AttachmentSearchInventory.read(selected,RelayCoreChatAdapter(selected.providerId),"https://api.relaycore.cn/v1",emptyList(),transport,
                {"fixture-only".toCharArray()},null,{},materialText="个人材料中的印象派主题")
            assertEquals(returned,returned=="claude-opus-5-5",result is AttachmentSearchInventory.Result.Ready)
        }
    }
    @Test fun `every current model preserves identity and streaming with search on and off`() {
        val manifest=JSONObject(File("../protocol/model-service/model-service-v1.json").readText()).getJSONArray("models")
        val context=ApplicationProvider.getApplicationContext<Context>()
        val resolver=UnifiedModelResolver(InMemoryVersionedModelRegistry(),AndroidModelProfileDirectory(context),object:ModelHealthStore {
            override fun observation(providerId:ProviderId,presetId:ModelPresetId):ModelHealthObservation?=null
            override fun record(providerId:ProviderId,presetId:ModelPresetId,observation:ModelHealthObservation)=Unit
        })
        val seen=mutableSetOf<ModelPresetId>()
        for(i in 0 until manifest.length()) {
            val row=manifest.getJSONObject(i);val preset=ModelPresetId.valueOf(row.getString("presetId"));seen+=preset
            val resolved=resolver.resolve(preset)
            assertTrue("$preset must resolve",resolved is ResolvedModelResult.Resolved)
            val model=(resolved as ResolvedModelResult.Resolved).model
            assertEquals(preset.name,row.getString("modelId"),model.modelId)
            val adapter=requireNotNull(ChatProviderAdapters().adapter(model.providerId))
            val choice=ComposerModelRoutingCatalog.choices.firstOrNull {it.routes==listOf(preset)}
                ?: ComposerModelChoice("audit-auto",ComposerModelSlot.DAILY,model.displayName,listOf(preset))
            for(enabled in listOf(false,true)) {
                val options=AutomaticWebSearchPolicy.requestOptions(model.providerId,adapter.requestOptions(model,choice),enabled,"Python与印象派",emptyList(),model.modelId)
                assertEquals(preset.name,enabled,options.liveWebSearch)
                assertTrue("$preset ordinary route should stream",adapter.supportsStreaming(model,options))
                val prepared=adapter.prepare(model,listOf("user" to "Python与印象派"),emptyList(),true,options)
                assertTrue("$preset request preparation",prepared is ChatAdapterPrepareResult.Ready)
                prepared as ChatAdapterPrepareResult.Ready
                val output=ByteArrayOutputStream();(prepared.body ?: ProviderChatRequestBody.Utf8Json(prepared.jsonBody)).writeTo(output)
                val body=JSONObject(output.toString("UTF-8"))
                assertEquals(preset.name,model.modelId,body.getString("model"));assertTrue(preset.name,body.getBoolean("stream"))
                assertTrue(adapter.endpoint(requireNotNull(NanfengModelServiceCatalog.provider(model.providerId)).fixedEndpoint,options).startsWith("https://"))
            }
        }
        assertEquals(NanfengModelServiceCatalog.chatPresets.map {it.id}.toSet(),seen)
    }
}

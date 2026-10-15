package com.nanzhufeng.ai.ai

import android.content.Context
import androidx.room.Room
import com.nanzhufeng.ai.data.*
import com.nanzhufeng.ai.data.local.*
import com.nanzhufeng.ai.domain.*
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.net.Socket
import java.time.Clock
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Explicit public title acceptance in the independent application; no instrumentation or main data. */
object TitleNativeAcceptanceOwner {
    fun run(context: Context, config: JSONObject) {
        check(context.packageName=="com.nanzhufeng.ai.publicmodelacceptance")
        val run=config.getString("runId");val output=File(context.filesDir,"public-title-$run.json")
        check(!output.exists());val keys=linkedMapOf<ProviderId,CharArray>();val rows=JSONArray();var sent=0
        fun save(){output.writeText(JSONObject().put("rows",rows).put("paidCalls",sent).toString(2))}
        try {
            Socket("127.0.0.1",config.getInt("port")).use {socket->
                socket.soTimeout=30000;socket.getOutputStream().write((config.getString("token")+"\n").toByteArray())
                val bytes=socket.getInputStream().readBytes()
                try {val payload=JSONObject(String(bytes,Charsets.UTF_8));for(id in listOf(ProviderId.QWEN,ProviderId.DEEPSEEK)){keys[id]=payload.getString(id.name).toCharArray();payload.remove(id.name)}}finally{bytes.fill(0)}
            }
            val clock=Clock.systemUTC()
            val resolver=UnifiedModelResolver(InMemoryVersionedModelRegistry(initialSnapshots=listOf(P3CLocalFixtureRegistry.snapshot)),AndroidModelProfileDirectory(context),AndroidModelHealthStore(context))
            for(case in listOf("qwen-primary","deepseek-only","qwen-timeout-backup")) {
                val name="title-$run-$case.db";val db=Room.databaseBuilder(context,NanfengAiDatabase::class.java,name).allowMainThreadQueries().build()
                val repository=RoomConversationRepository(db);val records=RoomConversationTitleGenerationRecordStore(db)
                val tree=ConversationTreeService(clock)
                val created=CreateConversationUseCase(tree,repository).execute() as ConversationMutationResult.Saved
                var snapshot=created.snapshot
                snapshot=(AppendConversationMessageUseCase(tree,repository).execute(snapshot,AppendMessageRequest(MessageRole.USER,listOf(ContentBlock.Text("请帮我整理应用设置页面的层级")))) as ConversationMutationResult.Saved).snapshot
                snapshot=(AppendConversationMessageUseCase(tree,repository).execute(snapshot,AppendMessageRequest(MessageRole.ASSISTANT,listOf(ContentBlock.Text("可以按功能分类和使用频率整理设置页面。")))) as ConversationMutationResult.Saved).snapshot
                val id=snapshot.conversation.id;val physical=JSONArray()
                val enabled=when(case){"qwen-primary"->setOf(ProviderId.QWEN);"deepseek-only"->setOf(ProviderId.DEEPSEEK);else->setOf(ProviderId.QWEN,ProviderId.DEEPSEEK)}
                val credentials=object:ProviderCredentialStore {
                    override fun hasCredential(providerId:ProviderId)=providerId in keys
                    override fun loadCredential(providerId:ProviderId)=keys[providerId]?.copyOf()
                    override fun saveCredential(providerId:ProviderId,credential:CharArray)=false
                }
                val settings=object:ModelServiceSettingsRepository {
                    override fun load(providerId:ProviderId)=ProviderSettings(providerId,providerId in enabled,NanfengModelServiceCatalog.defaultPreset(providerId))
                    override fun save(settings:ProviderSettings)=settings
                }
                val actual=OfficialProviderChatTransport()
                val transport=object:ProviderChatTransport {
                    override fun execute(request:ProviderChatRequest,credential:CharArray):ProviderChatOutcome {
                        val wire=JSONObject(request.jsonBody);val model=wire.getString("model")
                        if(case=="qwen-timeout-backup" && model.startsWith("qwen")) {
                            physical.put(JSONObject().put("model",model).put("faultInjected",true).put("sent",false).put("error","TIMEOUT"));return ProviderChatOutcome.TimedOut
                        }
                        check(++sent<=6);check(wire.getInt("max_tokens")==256);val start=System.nanoTime()
                        val result=actual.execute(request,credential)
                        physical.put(JSONObject().put("model",model).put("faultInjected",false).put("sent",true).put("outcome",result.javaClass.simpleName).put("elapsedMs",(System.nanoTime()-start)/1000000).apply {
                            if(result is ProviderChatOutcome.HttpResponse){put("http",result.statusCode);if(result.statusCode in 200..299){val decoded=ChatProviderAdapters().adapter(if(model.startsWith("qwen"))ProviderId.QWEN else ProviderId.DEEPSEEK)!!.decodeNonStreaming(result.responseBody) as? ChatAdapterDecodedResult.Text;put("returnedModel",decoded?.returnedModelId);put("titleContractValid",decoded?.let{parseConversationTitleResponse(it.text)!=null})}}
                        });return result
                    }
                }
                val refiner=ConfiguredConversationTitleRefiner(records,LoadModelServiceConfigurationUseCase(settings,credentials),credentials,resolver,transport,clock)
                val done=CountDownLatch(1);var result:ConversationTitleRefinementResult?=null
                com.nanzhufeng.ai.background.ConversationCompletionWork.enqueue(context,ConversationSurface.CHAT,id) {
                    try {result=refiner.refine(id,repository.findById(id)!!.openingTitleSource()!!);val title=result as? ConversationTitleRefinementResult.Title;if(title!=null)repository.completeAutomaticTitle(id,title.value,clock.instant())}finally{done.countDown()}
                }
                check(done.await(90,TimeUnit.SECONDS));val receipt=records.listNewestFirst().map{JSONObject().put("provider",it.providerId?.name).put("model",it.modelId).put("status",it.status.name).put("error",it.safeErrorCode).put("inputTokens",it.usage.inputTokens).put("outputTokens",it.usage.outputTokens).put("chargeMicros",it.cost.totalMicros).put("currency",it.cost.currencyCode).put("costSource",it.costSource?.name)}
                db.close();val reopened=Room.databaseBuilder(context,NanfengAiDatabase::class.java,name).allowMainThreadQueries().build()
                val saved=RoomConversationRepository(reopened).findById(id)!!
                rows.put(JSONObject().put("platform","Android application process title owner").put("case",case).put("mainAnswerSynthetic",true).put("uid",android.os.Process.myUid()).put("result",result.toString()).put("physical",physical).put("reopenedTitle",saved.conversation.title).put("pending",saved.conversation.autoTitlePending).put("titleValid",!saved.conversation.autoTitlePending && parseConversationTitleResponse(JSONObject().put("title",saved.conversation.title).toString())!=null).put("records",JSONArray(receipt)))
                reopened.close();save()
            }
        }catch(error:Throwable){rows.put(JSONObject().put("runnerError",error.javaClass.simpleName));save()}finally{keys.values.forEach{it.fill('\u0000')};keys.clear()}
        File(context.filesDir,"public-title-$run.done").writeText("done")
    }
}

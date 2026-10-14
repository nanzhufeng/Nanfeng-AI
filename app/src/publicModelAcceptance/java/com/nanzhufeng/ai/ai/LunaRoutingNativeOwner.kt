package com.nanzhufeng.ai.ai
import android.content.Context
import androidx.room.Room
import com.nanzhufeng.ai.data.*
import com.nanzhufeng.ai.data.local.*
import com.nanzhufeng.ai.domain.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
    class NativeFixture(val context:Context, private val keys:Map<ProviderId,CharArray>, val physical:org.json.JSONArray, databaseName:String, presetId:ModelPresetId) : AutoCloseable {
        private val webSearch=true
        private val independentSearch=false
        private val customInstructions=""
        var preparedAttachments:List<ConversationAttachmentReference>?=null
        val db = (if(databaseName==null) Room.inMemoryDatabaseBuilder(context,NanfengAiDatabase::class.java) else Room.databaseBuilder(context,NanfengAiDatabase::class.java,databaseName)).allowMainThreadQueries().build()
        val clock=Clock.systemUTC()
        val tree = ConversationTreeService(clock)
        val conversations = RoomConversationRepository(db)
        val attempts = RoomNormalChatSendAttemptStore(db)
        val attribution = RoomAssistantResponseModelAttributionStore(db)
        val durableAudit = AndroidDirectChatCallAuditStore(context, db)
        val preset = presetId
        private val snapshots=AndroidModelRegistrySnapshotStore(context)
        private val registry = InMemoryVersionedModelRegistry(initialSnapshots=listOf(P3CLocalFixtureRegistry.snapshot)+listOfNotNull(snapshots.load().current))
        private val verification = VerifyOpenRouterRegistryUseCase(AndroidOpenRouterRegistryCatalogClient(),OpenRouterRegistrySnapshotVerifier(),registry,snapshots,clock)
        private val resolver=UnifiedModelResolver(registry,AndroidModelProfileDirectory(context),AndroidModelHealthStore(context))
        val model=(resolver.resolve(preset) as? ResolvedModelResult.Resolved)?.model ?: run {verification.execute();(resolver.resolve(preset) as ResolvedModelResult.Resolved).model}
        var extraAttachmentTexts: List<String> = emptyList()
        var attachmentText = "公开附件测试：系统处理100个请求，出现5次超时。"
        var retrievalFactory:((ProviderChatRequest)->ProviderChatOutcome)?=null
        var retrievalOutcome: ProviderChatOutcome = ProviderChatOutcome.HttpResponse(504,"synthetic private upstream body")
        var requestCount = 0
        val requests = mutableListOf<ProviderChatRequest>()
        var partialText = "已收到的部分内容"
        var replyText = "完整回答"
        var returnsCancelled = false
        var streamChunks: List<String>? = null
        var credential: CharArray? = null
        var auditFailure: (DirectChatCallAuditRecord) -> Boolean = { false }
        var providerFailure: Exception? = null
        var answerOutcome: ProviderChatOutcome? = null
        var streamed = false
        var afterPartial: () -> Unit = {}
        var afterChunk: () -> Unit = {}
        var completedCallbackFails = false
        var lastConversationId: ConversationId? = null
        private val credentials = object : ProviderCredentialStore {
            override fun hasCredential(providerId: ProviderId) = keys[providerId]?.isNotEmpty()==true
            override fun saveCredential(providerId: ProviderId, credential: CharArray) = false
            override fun loadCredential(providerId: ProviderId) = keys[providerId]?.copyOf()
        }
        private val settings = object : ModelServiceSettingsRepository {
            override fun load(providerId: ProviderId) = ProviderSettings(providerId,keys[providerId]?.isNotEmpty()==true,NanfengModelServiceCatalog.defaultPreset(providerId))
            override fun save(settings: ProviderSettings) = settings
        }
        private val index=RoomLocalContextIndex(db)
        private val machine=ConversationRuntimeStateMachine(clock)
        val executor = NormalChatOpenRouterExecutor(
            configuration=LoadModelServiceConfigurationUseCase(settings,credentials),conversations=conversations,
            registry=registry,credentials=credentials,submitDraft=SubmitConversationDraftUseCase(conversations,conversations,tree),
            appendMessage=AppendConversationMessageUseCase(tree,conversations),contextBroker=LocalContextBroker(index),
            transport=object:ProviderChatTransport {
                private val native=OfficialProviderChatTransport()
                override fun execute(request:ProviderChatRequest,credential:CharArray):ProviderChatOutcome {
                    requestCount++;requests+=request
                    val result=native.execute(request,credential);val wire=org.json.JSONObject(request.jsonBody)
                    physical.put(org.json.JSONObject().apply {put("requested",wire.optString("model"));put("path",java.net.URI(request.endpoint).path);put("toolCount",wire.optJSONArray("tools")?.length()?:0);put("nativeOnly",wire.optJSONObject("provider")?.optBoolean("allow_fallbacks")==false);put("outcome",result.javaClass.simpleName)
                        if(result is ProviderChatOutcome.StreamedResponse){put("returned",result.returnedModelId);put("publicAnswer",result.text);put("nativeSources",org.json.JSONArray(result.webSources.map {it.url}));put("finish",result.finishReason);put("reportedMicros",result.reportedProviderCost?.totalMicros)}
                        if(result is ProviderChatOutcome.HttpResponse){put("http",result.statusCode)}
                    });return result
                }
            },audit=durableAudit,
            diagnostics=RoomProviderDiagnosticStore(db),sendAttempts=attempts,responseModelAttributions=attribution,
            attachmentBridge=UniversalChatAttachmentBridge(),adapters=ChatProviderAdapters(),modelResolver=resolver,
            modelHealthReporter=object : ModelHealthReporter {
                override fun recordSuccess(presetId: ModelPresetId) = Unit
                override fun recordFailure(presetId: ModelPresetId,error: ProviderDiagnosticErrorClass) = Unit
            },contextSelectionAudits=AndroidContextSelectionAuditStore(context,db),clock=clock,
            verifyOpenRouterRegistry=verification,loadRoutingPolicy=LoadChatRoutingPolicyUseCase(object : ChatRoutingPolicyRepository {
                override fun load() = ChatRoutingPolicy(autoRoutingEnabled=false)
                override fun save(policy: ChatRoutingPolicy) = policy
            }),attachmentRepository=RoomPrivateAttachmentRepository(db),attachmentStore=AndroidPrivateAttachmentStore(context),
            submitDraftAndStartProviderRuntime=SubmitConversationDraftAndStartProviderRuntimeUseCase(conversations,conversations,conversations,machine,tree,clock),
            startProviderRuntimeForExistingUser=StartProviderRuntimeForExistingUserUseCase(conversations,conversations,machine,clock),
            applyRuntimeEvent=ApplyConversationRuntimeEventUseCase(conversations,conversations,machine),runtimeRepository=conversations,
            conversationTitleRefiner=object : ConversationTitleRefiner {
                override fun refine(sourceConversationId: ConversationId,source: ConversationTitleSource) = ConversationTitleRefinementResult.Failed("SYNTHETIC_DISABLED")
            },loadAssistantExperienceSettings={AssistantExperienceSettings(webSearchEnabled=webSearch,customInstructions=customInstructions)},
            onConversationCompleted={if(completedCallbackFails) throw IllegalStateException("synthetic completion callback")},
        )
        fun send(withAttachment: Boolean = false): Pair<ConversationId,NormalChatOpenRouterExecutor.Result> {
            val snapshot=conversations.save(tree.create("稳定性测试"));val id=snapshot.conversation.id;lastConversationId=id
            val refs=preparedAttachments ?: if(withAttachment) {
                (listOf(attachmentText)+extraAttachmentTexts).mapIndexed {index,text ->
                    val imported=AndroidPrivateAttachmentStore(context).import(AttachmentImportRequest(text.byteInputStream(),"text/markdown","public-fixture-$index.md")) as AttachmentImportResult.Imported
                    RoomPrivateAttachmentRepository(db).save(imported.attachment).toConversationReference()
                }
            } else emptyList()
            val draft=conversations.saveDraft(id,ConversationDraft(text=if(withAttachment) "分析报告" else "合成测试问题",attachments=refs,updatedAt=clock.instant()))
            val choice=ComposerModelRoutingCatalog.choices.firstOrNull {it.routes==listOf(preset)} ?: ComposerModelRoutingCatalog.auto
            val approval=NormalChatEgressAuthorization.forUserSend(draft,clock.millis(),choice.id,executor.routingSnapshot().copy(webSearchEnabled=webSearch).let {if(preset==ModelPresetId.QWEN_3_6_FLASH) it.copy(usablePresets=setOf(preset)) else it})
            return id to executor.execute(id,approval)
        }
        override fun close() = db.close()
    }


/** Real HTTP and production Room/executor owners; no AppContainer, UI or background acceptance claim. */
object LunaRoutingNativeOwner {
    @JvmStatic fun run(context:Context,config:org.json.JSONObject) {
        check(context.packageName=="com.nanzhufeng.ai.publicmodelacceptance")
        val run=config.getString("runId");val rows=org.json.JSONArray();val physical=org.json.JSONArray()
        val keys=linkedMapOf<ProviderId,CharArray>()
        fun save(){java.io.File(context.filesDir,"public-results-$run.json").writeText(rows.toString(2));java.io.File(context.filesDir,"public-physical-$run.json").writeText(physical.toString(2))}
        try {
            com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context)
            java.net.Socket("127.0.0.1",config.getInt("port")).use {socket ->
                socket.soTimeout=30000;socket.getOutputStream().write((config.getString("token")+"\n").toByteArray())
                val payload=socket.getInputStream().bufferedReader().use {it.readText()};val parsed=org.json.JSONObject(payload)
                parsed.keys().asSequence().toList().forEach {id->keys[ProviderId.valueOf(id)]=parsed.getString(id).toCharArray();parsed.remove(id)}
            }
            val filter=config.optString("filter").split(',').filter(String::isNotBlank).toSet()
            for(preset in NanfengModelServiceCatalog.chatPresets.map {it.id}.filter {filter.isEmpty() || it.name in filter}) {
                val dbName="luna-native-$run-${preset.name}.db";val started=System.currentTimeMillis()
                try {
                    val fixture=NativeFixture(context,keys,physical,dbName,preset)
                    fixture.use {f ->
                        val groups=if(config.optString("fixture")=="formats") {
                            val manifest=org.json.JSONObject(context.assets.open("public-search-fixtures/manifest.json").bufferedReader().use {it.readText()});val files=manifest.getJSONArray("fixtures");val a=manifest.getJSONArray("groups")
                            (0 until a.length()).map {i->val g=a.getJSONArray(i);(0 until g.length()).map {files.getJSONObject(g.getInt(it))}}
                        } else listOf(listOf(org.json.JSONObject().put("file","public-02.md").put("mime","text/markdown"),org.json.JSONObject().put("file","public-10.docx").put("mime","application/vnd.openxmlformats-officedocument.wordprocessingml.document")))
                        for((group,files) in groups.withIndex()) {
                            f.preparedAttachments=files.map {file ->
                                val name=file.getString("file");val imported=AndroidPrivateAttachmentStore(context).import(AttachmentImportRequest(context.assets.open("public-search-fixtures/$name"),file.getString("mime"),name)) as AttachmentImportResult.Imported
                                RoomPrivateAttachmentRepository(f.db).save(imported.attachment).toConversationReference()
                            }
                            val before=physical.length();val (id,result)=f.send(true);val snapshot=f.conversations.findById(id)!!;val answer=snapshot.nodes.lastOrNull {it.role==MessageRole.ASSISTANT}
                            val fact=answer?.let {f.attribution.forMessages(listOf(it.id))[it.id]?.lastOrNull()};val text=answer?.content?.filterIsInstance<ContentBlock.Text>()?.joinToString("\n") {it.text}.orEmpty()
                            val coverage=AndroidContextSelectionAuditStore(context,f.db).recent(100).filter {it.conversationId==id.value}.flatMap {it.selectedSources}.firstOrNull {it.kind=="联网核验范围"}?.title
                            rows.put(org.json.JSONObject().apply {put("preset",preset.name);put("uid",android.os.Process.myUid());put("group",group);put("files",org.json.JSONArray(files.map {it.getString("file")}));put("result",result.toString());put("conversationId",id.value);put("coverage",coverage);put("requestedModel",fact?.modelId);put("webSearchUsed",fact?.webSearchUsed);put("assistantPresent",answer!=null);put("publicAnswer",text);put("answerChars",text.length);put("elapsedMs",System.currentTimeMillis()-started);put("physicalCount",physical.length()-before)
                                put("accountingCalls",org.json.JSONArray(fact?.accountingCalls.orEmpty().map {c->org.json.JSONObject().apply {put("role",c.role);put("provider",c.providerId.name);put("requested",c.requestedModelId);put("returned",c.actualModelId);put("status",c.status);put("micros",c.cost.totalMicros);put("currency",c.cost.currencyCode);put("input",c.usage.inputTokens);put("output",c.usage.outputTokens)}}))
                            });save()
                        }
                    }
                    NativeFixture(context,keys,org.json.JSONArray(),dbName,preset).use {f ->
                        for(i in 0 until rows.length()){val row=rows.getJSONObject(i);if(row.optString("preset")!=preset.name || !row.has("conversationId"))continue
                            val restored=f.conversations.findById(ConversationId(row.getString("conversationId")))!!;val text=restored.nodes.lastOrNull {it.role==MessageRole.ASSISTANT}?.content?.filterIsInstance<ContentBlock.Text>()?.joinToString("\n") {it.text}.orEmpty();row.put("reopenedAnswerExact",text==row.getString("publicAnswer"));row.put("reopenNetworkCalls",f.requestCount)
                        }
                    };save()
                } catch(e:Throwable) {rows.put(org.json.JSONObject().put("preset",preset.name).put("bootstrapError",true).put("errorClass",e.javaClass.simpleName).put("frames",org.json.JSONArray(e.stackTrace.take(6).map {it.className+"."+it.methodName})));save()}
            }
        } catch(e:Throwable){rows.put(org.json.JSONObject().put("bootstrapError",true).put("errorClass",e.javaClass.simpleName).put("frames",org.json.JSONArray(e.stackTrace.take(6).map {it.className+"."+it.methodName})));save()}
        finally {keys.values.forEach {it.fill('\u0000')};keys.clear();java.io.File(context.filesDir,"public-cleanup-$run.json").writeText("{\"memoryCredentialsCleared\":true,\"capabilityConsumed\":true}")}
    }
}

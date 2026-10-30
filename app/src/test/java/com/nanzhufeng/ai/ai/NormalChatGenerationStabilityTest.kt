package com.nanzhufeng.ai.ai

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nanzhufeng.ai.data.*
import com.nanzhufeng.ai.data.local.*
import com.nanzhufeng.ai.domain.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CancellationException

/** Production executor + Room owners; only provider I/O and credentials are synthetic. */
@RunWith(RobolectricTestRunner::class)
class NormalChatGenerationStabilityTest {
    @Test fun `local accepted or text callback IO errors are not reported as network outages`() {
        for (phase in listOf("accepted", "text")) Fixture(presetId=ModelPresetId.CLAUDE_OPUS_5_5,currentProfile=true).use { f ->
            var opened=0
            val connection=InterruptedStreamConnection("""data: {"model":"${f.model.modelId}","choices":[{"delta":{"content":"已收到的部分内容"},"finish_reason":"stop"}]}

data: [DONE]

""")
            f.providerFactory={ request -> OfficialProviderChatTransport {opened++;connection}.execute(request.copy(
                onAccepted={if(phase=="accepted") throw java.io.IOException("synthetic local callback error")},
                onTextDelta={delta -> request.onTextDelta(delta);if(phase=="text") throw java.io.IOException("synthetic local callback error")},
            ),"synthetic".toCharArray()) }
            val (id,result)=f.send()
            assertEquals(NormalChatOpenRouterExecutor.Result.Failed(NormalChatOpenRouterExecutor.Code.LOCAL_EXECUTION_INTERRUPTED),result)
            val attempt=f.attempts.findLatestForConversation(id)!!
            assertEquals(NormalChatSendAttemptStatus.UNKNOWN,attempt.status)
            assertEquals("LOCAL_EXECUTION_INTERRUPTED",attempt.safeErrorCode)
            assertEquals(ConversationRuntimeStatus.FAILED,f.conversations.stateFor(id)!!.status)
            if(phase=="text") assertTrue(f.conversations.findById(id)!!.nodes.single {it.role==MessageRole.ASSISTANT}.content
                .filterIsInstance<ContentBlock.Text>().any {it.text.contains("已收到的部分内容")})
            assertEquals(1,opened);assertEquals(1,f.requestCount)
            assertTrue(f.credential!!.all {it=='\u0000'})
        }
    }

    @Test fun `socket EOF retains received settlement through Room reopen without repeating request`() {
        val name="interrupted-settlement-${java.util.UUID.randomUUID()}"
        val f=Fixture(databaseName=name,presetId=ModelPresetId.CLAUDE_OPUS_5_5,currentProfile=true)
        try {
            var opened=0
            val connection=InterruptedStreamConnection("""data: {"model":"${f.model.modelId}","choices":[{"delta":{"content":"部分回答","reasoning":"部分思考"},"finish_reason":null}],"usage":{"prompt_tokens":100,"completion_tokens":20,"cost":0.009001}}

""",failRead=true)
            f.providerFactory={ request -> OfficialProviderChatTransport { opened++;connection }
                .execute(request.copy(onAccepted={}),"synthetic".toCharArray()) }
            val (id,result)=f.send()
            assertTrue(result is NormalChatOpenRouterExecutor.Result.Failed)
            assertEquals(1,opened)
            assertEquals(1,f.requestCount)
            assertEquals(NormalChatSendAttemptStatus.UNKNOWN,f.attempts.findLatestForConversation(id)!!.status)
            val answer=f.conversations.findById(id)!!.nodes.single {it.role==MessageRole.ASSISTANT}
            assertEquals(MessageDeliveryState.FAILED,answer.deliveryState)
            assertTrue(answer.content.filterIsInstance<ContentBlock.Text>().any {it.text.contains("部分回答")})
            val call=f.durableAudit.listNewestFirst().mapNotNull {it.accounting}.single {it.conversationId==id.value}
            assertEquals(9001L,call.cost.totalMicros)
            assertEquals(ConversationCostSource.PROVIDER_RESPONSE,call.costSource)
            assertEquals(100L,call.usage.inputTokens)
            assertEquals(20L,call.usage.outputTokens)
            val diagnostic=RoomProviderDiagnosticStore(f.db).recent(8).single {it.conversationId==id.value}
            assertEquals(200,diagnostic.httpStatus)
            assertEquals("TRANSPORT_IO_EOFException_AT_RESPONSE_BODY",diagnostic.redactedBody)
            f.close()
            val reopened=Room.databaseBuilder(f.context,NanfengAiDatabase::class.java,name).allowMainThreadQueries().build()
            try {
                assertEquals(call,AndroidDirectChatCallAuditStore(f.context,reopened).listNewestFirst().mapNotNull {it.accounting}.single {it.conversationId==id.value})
                assertEquals(answer,RoomConversationRepository(reopened).findById(id)!!.nodes.single {it.role==MessageRole.ASSISTANT})
                assertEquals(diagnostic,RoomProviderDiagnosticStore(reopened).recent(8).single {it.conversationId==id.value})
            } finally {reopened.close()}
        } finally {runCatching {f.close()};f.context.deleteDatabase(name)}
    }

    @Test fun `leading whitespace stream frames do not fail response persistence`() {
        Fixture(presetId=ModelPresetId.RELAYCORE_GPT_6_ASTRA,currentProfile=true).use {f ->
            f.streamed=true
            f.streamChunks=listOf(" ","\n", "图片：63.27", "\nPDF：29.14")
            f.replyText=" \n图片：63.27\nPDF：29.14"
            val (id,result)=f.send()
            assertEquals(NormalChatOpenRouterExecutor.Result.Sent,result)
            assertEquals(NormalChatSendAttemptStatus.COMPLETED,f.attempts.findLatestForConversation(id)!!.status)
            assertEquals(ConversationRuntimeStatus.COMPLETED,f.conversations.stateFor(id)!!.status)
            val answer=f.conversations.findById(id)!!.nodes.last {it.role==MessageRole.ASSISTANT}
            assertEquals(MessageDeliveryState.COMPLETE,answer.deliveryState)
            assertEquals("图片：63.27\nPDF：29.14",answer.content.filterIsInstance<ContentBlock.Text>().joinToString("\n"){it.text})
        }
    }

    @Test fun `completed provider reasoning is retained through the Room owner and reopen`() {
        for(stream in listOf(false,true)) {
            val name="reasoning-retention-$stream-${System.nanoTime()}"
            val f=Fixture(databaseName=name,presetId=ModelPresetId.CLAUDE_HAIKU_5_5)
            try {
                f.answerOutcome=if(stream) ProviderChatOutcome.StreamedResponse(200,"公开回答","合成思考内容",100,20,null,false,finishReason="COMPLETED",returnedModelId=f.model.modelId)
                else ProviderChatOutcome.HttpResponse(200,"""{"model":"${f.model.modelId}","choices":[{"message":{"content":"公开回答","reasoning_content":"合成思考内容"},"finish_reason":"stop"}],"usage":{"prompt_tokens":100,"completion_tokens":20}}""")
                val (id,result)=f.send();assertEquals(NormalChatOpenRouterExecutor.Result.Sent,result)
                val before=f.conversations.findById(id)!!;val answer=before.nodes.single {it.role==MessageRole.ASSISTANT}
                assertEquals("合成思考内容",answer.content.filterIsInstance<ContentBlock.Reasoning>().single().text)
                assertEquals(NormalChatSendAttemptStatus.COMPLETED,f.attempts.findLatestForConversation(id)!!.status)
                val state=f.conversations.stateFor(id)!!
                assertTrue(f.conversations.retainCompletedProviderContinuation(state,"合成思考内容",emptyList()))
                assertFalse(f.conversations.retainCompletedProviderContinuation(state,"不得覆盖原思考",emptyList()))
                assertFalse(f.conversations.retainCompletedProviderContinuation(state.copy(messageId=before.nodes.first {it.role==MessageRole.USER}.id),"不得修改用户消息",emptyList()))
                assertEquals(before,f.conversations.findById(id))
                f.close()
                val reopened=Room.databaseBuilder(f.context,NanfengAiDatabase::class.java,name).allowMainThreadQueries().build()
                try {assertEquals(before,RoomConversationRepository(reopened).findById(id))} finally {reopened.close()}
            } finally {runCatching {f.close()};f.context.deleteDatabase(name)}
        }
    }
    @Test fun `verified Opus aliases do not report a model change while unrelated models still do`() {
        for(stream in listOf(false,true)) for(returned in listOf("claude-opus-5-5","claude-sonnet-5-5")) {
            Fixture(presetId=ModelPresetId.RELAYCORE_OPUS_5_5).use {f ->
                f.answerOutcome=if(stream) ProviderChatOutcome.StreamedResponse(200,"公开回答",null,100,20,null,false,finishReason="COMPLETED",returnedModelId=returned)
                else ProviderChatOutcome.HttpResponse(200,"""{"model":"$returned","content":[{"type":"text","text":"公开回答"}],"stop_reason":"end_turn","usage":{"input_tokens":100,"output_tokens":20}}""")
                val (_,result)=f.send()
                if(returned=="claude-opus-5-5") assertEquals(NormalChatOpenRouterExecutor.Result.Sent,result)
                else assertEquals(NormalChatOpenRouterExecutor.Result.SentWithNotice(NormalChatOpenRouterExecutor.CompletionNotice.MODEL_IDENTITY_MISMATCH),result)
            }
        }
    }
    private class Fixture(databaseName: String? = null, presetId: ModelPresetId = ModelPresetId.RELAYCORE_SONNET_5_5, private val webSearch: Boolean = false, private val customInstructions: String = "", modelContextWindow: Long? = 200000,private val independentSearch:Boolean=false,clockOverride:Clock?=null, asynchronousCompletion:Boolean=false, private val imageOnlyInput:Boolean=false,private val currentProfile:Boolean=false) : AutoCloseable {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = (if(databaseName==null) Room.inMemoryDatabaseBuilder(context,NanfengAiDatabase::class.java) else Room.databaseBuilder(context,NanfengAiDatabase::class.java,databaseName)).allowMainThreadQueries().build()
        val clock = clockOverride ?: Clock.fixed(Instant.parse("2026-10-10T07:38:57.123456789Z"), ZoneOffset.UTC)
        val tree = ConversationTreeService(clock)
        val conversations = RoomConversationRepository(db, draftClock=clock)
        val attempts = RoomNormalChatSendAttemptStore(db)
        val attribution = RoomAssistantResponseModelAttributionStore(db)
        val durableAudit = AndroidDirectChatCallAuditStore(context, db)
        val preset = presetId
        private val spec = RelayCoreModels.spec(preset)
        private val profiles=com.nanzhufeng.ai.data.AndroidModelProfileDirectory(context)
        val model = if(currentProfile) requireNotNull(profiles.profile(preset)).copy(health=ModelHealth.AVAILABLE,maxOutputTokens=8192) else ResolvedModel(spec?.provider ?: ProviderId.OPENROUTER,spec?.apiId ?: if(preset==ModelPresetId.MERCURY_3) "inception/mercury-3" else "anthropic/claude-haiku-5.5",spec?.compactName ?: "Haiku 5.5",
            ModelCapabilities(true,imageOnlyInput && preset!=ModelPresetId.MERCURY_3,true),modelContextWindow,ModelHealth.AVAILABLE,clock.instant(),null,8192)
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
        var providerFactory: ((ProviderChatRequest) -> ProviderChatOutcome)? = null
        var providerAssertion: Throwable? = null
        var streamed = false
        var afterPartial: () -> Unit = {}
        var afterChunk: () -> Unit = {}
        var completedCallbackFails = false
        var titlePending = false
        var titleRefine: () -> ConversationTitleRefinementResult = { ConversationTitleRefinementResult.Failed("SYNTHETIC_DISABLED") }
        var lastConversationId: ConversationId? = null
        private val credentials = object : ProviderCredentialStore {
            override fun hasCredential(providerId: ProviderId) = providerId == model.providerId || ((webSearch || currentProfile) && providerId == ProviderId.OPENROUTER) || (independentSearch && providerId==ProviderId.ZHIPU)
            override fun saveCredential(providerId: ProviderId, credential: CharArray) = false
            override fun loadCredential(providerId: ProviderId) = if(hasCredential(providerId)) (if(currentProfile) "synthetic-${providerId.name}-key" else "synthetic-test-key").toCharArray().also { credential=it } else null
        }
        private val settings = object : ModelServiceSettingsRepository {
            override fun load(providerId: ProviderId) = ProviderSettings(providerId,providerId==model.providerId || ((webSearch || currentProfile) && providerId == ProviderId.OPENROUTER),NanfengModelServiceCatalog.defaultPreset(providerId))
            override fun save(settings: ProviderSettings) = settings
        }
        private val registry = InMemoryVersionedModelRegistry()
        private val verification = VerifyOpenRouterRegistryUseCase(object : OpenRouterRegistryCatalogClient {
            override fun fetchCatalog(): OpenRouterCatalogFetchResult = OpenRouterCatalogFetchResult.Failed(OpenRouterCatalogFailure.NETWORK)
        },OpenRouterRegistrySnapshotVerifier(),registry,object : ModelRegistrySnapshotStore {
            override fun load() = StoredModelRegistrySnapshots(null,null)
            override fun persist(snapshots: StoredModelRegistrySnapshots) = false
        },clock)
        private val index = object : LocalContextIndex {
            override fun searchActiveMemories(queryTerms: Set<String>,scope: ContextRetrievalScope,limit: Int) = emptyList<LocalContextIndexHit>()
            override fun searchActiveKnowledge(queryTerms: Set<String>,scope: ContextRetrievalScope,limit: Int) = emptyList<LocalContextIndexHit>()
            override fun searchActiveHistory(queryTerms: Set<String>,scope: ContextRetrievalScope,limit: Int) = emptyList<LocalContextIndexHit>()
            override fun activeKnowledgeCount() = 0
            override fun status() = LocalContextIndexStatus.AVAILABLE
        }
        private val machine = ConversationRuntimeStateMachine(clock)
        private val completionExecutor = if(asynchronousCompletion) java.util.concurrent.Executors.newSingleThreadExecutor() else null
        private val completionQueue = completionExecutor?.let(::ConversationCompletionWorkQueue)
        val executor = NormalChatOpenRouterExecutor(
            configuration=LoadModelServiceConfigurationUseCase(settings,credentials),conversations=conversations,
            registry=registry,credentials=credentials,submitDraft=SubmitConversationDraftUseCase(conversations,conversations,tree),
            appendMessage=AppendConversationMessageUseCase(tree,conversations),contextBroker=LocalContextBroker(index),
            transport=object : ProviderChatTransport {
                override fun execute(request: ProviderChatRequest, credential: CharArray): ProviderChatOutcome {
                    requestCount++;requests+=request;request.onAccepted()
                    if(currentProfile) assertEquals("synthetic-${if(request.endpoint.startsWith("https://openrouter.ai/")) ProviderId.OPENROUTER.name else model.providerId.name}-key",String(credential))
                    providerFactory?.let { factory -> return try {factory(request)} catch(e:Throwable) {providerAssertion=e;throw e} }
                    if(request.endpoint==IndependentSearchServiceGrant.ENDPOINT) return retrievalFactory?.invoke(request) ?: retrievalOutcome
                    if (webSearch && model.providerId != ProviderId.OPENROUTER && request.endpoint.startsWith("https://openrouter.ai/")) return retrievalFactory?.invoke(request) ?: retrievalOutcome
                    if (streamed || providerFailure!=null || returnsCancelled) { (streamChunks ?: listOf(partialText)).forEach {request.onTextDelta(it);afterChunk()} }
                    afterPartial()
                    providerFailure?.let { throw it }
                    if(returnsCancelled) return ProviderChatOutcome.Cancelled
                    answerOutcome?.let { return it }
                    return if(streamed) ProviderChatOutcome.StreamedResponse(200,replyText,null,100,20,null,false,returnedModelId=model.modelId)
                    else ProviderChatOutcome.HttpResponse(200,"""{"id":"synthetic","model":"${model.modelId}","choices":[{"message":{"role":"assistant","content":"$replyText"},"finish_reason":"stop"}],"usage":{"prompt_tokens":100,"completion_tokens":20,"total_tokens":120}}""")
                }
            },audit=object : DirectChatCallAuditStore by durableAudit {
                override fun append(record: DirectChatCallAuditRecord) {
                    if(auditFailure(record)) throw IllegalStateException("synthetic disk failure")
                    durableAudit.append(record)
                }
            },diagnostics=RoomProviderDiagnosticStore(db),sendAttempts=attempts,responseModelAttributions=attribution,
            attachmentBridge=UniversalChatAttachmentBridge(),adapters=ChatProviderAdapters(),modelResolver=ModelResolver { if(currentProfile) profiles.profile(it)?.let {m->ResolvedModelResult.Resolved(m.copy(health=ModelHealth.AVAILABLE,maxOutputTokens=8192))} ?: ResolvedModelResult.Unavailable("fixture") else if(it==preset) ResolvedModelResult.Resolved(model) else if(preset==ModelPresetId.MERCURY_3 && it==ModelPresetId.CLAUDE_HAIKU_5_5) ResolvedModelResult.Resolved(model.copy(providerId=ProviderId.OPENROUTER,modelId="anthropic/claude-haiku-5.5",capabilities=model.capabilities.copy(supportsVision=true))) else if(webSearch && it==ModelPresetId.GPT_6_LUNA) ResolvedModelResult.Resolved(model.copy(providerId=ProviderId.OPENROUTER,modelId="openai/gpt-6-luna",capabilities=model.capabilities.copy(supportsVision=true))) else ResolvedModelResult.Unavailable("fixture") },
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
            scheduleCompletionWork={ id, work -> if(completionQueue != null) completionQueue.enqueue(id.value, work) else work() },
            conversationTitleRefiner=object : ConversationTitleRefiner {
                override fun refine(sourceConversationId: ConversationId,source: ConversationTitleSource) = titleRefine()
            },loadAssistantExperienceSettings={AssistantExperienceSettings(webSearchEnabled=webSearch,customInstructions=customInstructions)},
            onConversationCompleted={if(completedCallbackFails) throw IllegalStateException("synthetic completion callback")},
        )
        fun send(withAttachment: Boolean = false, approveReader:Boolean=true): Pair<ConversationId,NormalChatOpenRouterExecutor.Result> {
            val initial=tree.create(if(titlePending) ConversationAutoTitle.NEW_CONVERSATION_TITLE else "稳定性测试", autoTitlePending=titlePending); val snapshot=conversations.save(initial);val id=snapshot.conversation.id;lastConversationId=id
            val refs=if(imageOnlyInput) {
                val bytes=java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aemkAAAAASUVORK5CYII=")
                val imported=AndroidPrivateAttachmentStore(context).import(AttachmentImportRequest(bytes.inputStream(),"image/png","public-pixel.png")) as AttachmentImportResult.Imported
                listOf(RoomPrivateAttachmentRepository(db).save(imported.attachment).toConversationReference())
            } else if(withAttachment) {
                (listOf(attachmentText)+extraAttachmentTexts).mapIndexed {index,text ->
                    val imported=AndroidPrivateAttachmentStore(context).import(AttachmentImportRequest(text.byteInputStream(),"text/markdown","public-fixture-$index.md")) as AttachmentImportResult.Imported
                    RoomPrivateAttachmentRepository(db).save(imported.attachment).toConversationReference()
                }
            } else emptyList()
            val draft=conversations.saveDraft(id,ConversationDraft(text=if(imageOnlyInput) "" else if(withAttachment) "分析报告" else "合成测试问题",attachments=refs,updatedAt=clock.instant()))
            val choice=ComposerModelRoutingCatalog.choices.first {it!=ComposerModelRoutingCatalog.auto && it.routes==listOf(preset)}
            val approval=NormalChatEgressAuthorization.forUserSend(draft,clock.millis(),choice.id,executor.routingSnapshot().copy(webSearchEnabled=webSearch))
            return id to executor.execute(id,if(approveReader) approval else approval.copy(auxiliaryRecipientModelIds=emptyMap()))
        }
        fun awaitCompletion(id: ConversationId) {
            val done = java.util.concurrent.CountDownLatch(1)
            checkNotNull(completionQueue).enqueue(id.value) { done.countDown() }
            assertTrue(done.await(10, java.util.concurrent.TimeUnit.SECONDS))
        }
        override fun close() { completionExecutor?.shutdown(); completionExecutor?.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS); db.close() }
    }

    @Test fun `every selected model retains received fee for interrupted image reading retrieval or answer`() {
        val presets=NanfengModelServiceCatalog.chatPresets.map {it.id}.filter {preset ->
            ComposerModelRoutingCatalog.choices.any {it!=ComposerModelRoutingCatalog.auto && it.routes==listOf(preset)}
        }
        for(preset in presets) for(web in listOf(false,true)) Fixture(presetId=preset,webSearch=web,imageOnlyInput=true,currentProfile=true).use {f ->
            f.providerFactory={request ->
                val wire=java.io.ByteArrayOutputStream().also {request.effectiveBody().writeTo(it)}.toString(Charsets.UTF_8.name())
                val model=org.json.JSONObject(wire).getString("model")
                ProviderChatOutcome.NetworkFailure(ProviderNetworkFailureKind.IO,"EOFException",partialResponse=
                    ProviderChatOutcome.StreamedResponse(200,"partial fixture",null,100,20,ProviderReportedCost(9001,"USD","synthetic-v1"),false,
                        cachedInputTokens=40,reasoningTokens=10,finishReason="MISSING_COMPLETION",returnedModelId=model))
            }
            val (id,result)=f.send();f.providerAssertion?.let {throw it}
            assertTrue("$preset/web=$web",result is NormalChatOpenRouterExecutor.Result.Failed)
            assertEquals("$preset/web=$web must not send after uncertain reading or retrieval",1,f.requestCount)
            assertFalse("$preset/web=$web",f.attempts.findLatestForConversation(id)!!.status==NormalChatSendAttemptStatus.COMPLETED)
            val calls=f.durableAudit.listNewestFirst().mapNotNull {it.accounting}.filter {it.conversationId==id.value}
            assertEquals("$preset/web=$web",f.requestCount,calls.size)
            for(call in calls) {
                assertEquals("$preset/web=$web/${call.role}",9001L,call.cost.totalMicros)
                assertEquals(ConversationCostSource.PROVIDER_RESPONSE,call.costSource)
                assertEquals(100L,call.usage.inputTokens)
            }
        }
    }

    @Test fun `every selectable current model uses authorized image preparation and its original final identity`() {
        val manifest=org.json.JSONObject(java.io.File("../protocol/model-service/model-service-v1.json").readText()).getJSONArray("models")
        for(i in 0 until manifest.length()) {
            val preset=ModelPresetId.valueOf(manifest.getJSONObject(i).getString("presetId"))
            if(ComposerModelRoutingCatalog.choices.none {it!=ComposerModelRoutingCatalog.auto && it.routes==listOf(preset)}) continue // Auto-only routes are covered by the manifest preparation matrix.
            for(web in listOf(false,true)) Fixture(presetId=preset,webSearch=web,imageOnlyInput=true,currentProfile=true).use {f->
                f.providerFactory={request->
                    val wire=java.io.ByteArrayOutputStream().also {request.effectiveBody().writeTo(it)}.toString(Charsets.UTF_8.name())
                    val model=org.json.JSONObject(wire).getString("model")
                    val reading=wire.contains("忠实读取") || wire.contains("忠实识读")
                    ProviderChatOutcome.StreamedResponse(200,if(reading) "### 附件\nPython 3.13于2024年10月7日发布。" else "[A1] 已核实\nPython 3.13于2024年10月7日发布。\n来源：https://www.python.org/downloads/release/python-3130/",null,100,20,null,false,finishReason="stop",returnedModelId=model,webSearchPerformed=web && !reading,webSources=if(web && !reading) listOf(ProviderWebSource("https://www.python.org/downloads/release/python-3130/")) else emptyList())
                }
                val (id,result)=f.send();f.providerAssertion?.let {throw it}
                assertEquals("$preset/web=$web",NormalChatOpenRouterExecutor.Result.Sent,result)
                val attempt=requireNotNull(f.attempts.findLatestForConversation(id))
                assertEquals(f.model.modelId,attempt.modelId);assertEquals(NormalChatSendAttemptStatus.COMPLETED,attempt.status)
                val final=f.requests.last();val finalWire=java.io.ByteArrayOutputStream().also {final.effectiveBody().writeTo(it)}.toString(Charsets.UTF_8.name())
                assertEquals(f.model.modelId,org.json.JSONObject(finalWire).getString("model"))
                if(!AttachmentInputPolicy.acceptsNative(f.model,"image/png",web && f.model.providerId!=ProviderId.OPENROUTER)) assertFalse("$preset raw image leaked to text route",finalWire.contains("image_url"))
                assertTrue(f.durableAudit.listNewestFirst().mapNotNull {it.accounting}.any {it.rootAttemptId==attempt.attemptId.value && it.requestedModelId==f.model.modelId})
            }
        }
    }

    @Test fun `Mercury reads images with disclosed Haiku then answers as Mercury without raw image`() {
        for(search in listOf(false,true)) Fixture(presetId=ModelPresetId.MERCURY_3,webSearch=search,imageOnlyInput=true).use { f ->
            f.providerFactory={ request ->
                val wire=java.io.ByteArrayOutputStream().also {request.effectiveBody().writeTo(it)}.toString(Charsets.UTF_8.name())
                val body=org.json.JSONObject(wire)
                val reader=f.requestCount==1
                assertEquals(if(reader) "anthropic/claude-haiku-5.5" else "inception/mercury-3",body.getString("model"))
                assertEquals(reader,wire.contains("image_url"))
                if(!reader) {
                    assertTrue(wire.contains("台积电"))
                }
                ProviderChatOutcome.StreamedResponse(200,if(reader) "### 附件\n台积电2026年第三季度营收467.2亿美元。" else if(search && f.requestCount==2) "[A1] 已核实\n台积电公开了2026年第三季度财报。\n来源：https://investor.tsmc.com/quarterly-results" else "根据材料分析台积电营收。",null,100,20,null,false,
                    finishReason="stop",returnedModelId=body.getString("model"),webSearchPerformed=search && !reader,
                    webSources=if(search && !reader) listOf(ProviderWebSource("https://investor.tsmc.com/quarterly-results")) else emptyList())
            }
            val (id,result)=f.send();f.providerAssertion?.let {throw it}
            assertEquals(NormalChatOpenRouterExecutor.Result.Sent,result)
            assertEquals(if(search) 3 else 2,f.requestCount)
            val finalBody=java.io.ByteArrayOutputStream().also {f.requests.last().effectiveBody().writeTo(it)}.toString(Charsets.UTF_8.name())
            val messages=org.json.JSONObject(finalBody).getJSONArray("messages")
            assertTrue((0 until messages.length()).any {i->messages.getJSONObject(i).let {it.optString("role")=="system" && it.optString("content").contains(AttachmentInputPolicy.ANSWER_MATERIAL_INSTRUCTION)}})
            val attempt=f.attempts.findLatestForConversation(id)!!
            assertEquals("inception/mercury-3",attempt.modelId)
            assertEquals(NormalChatSendAttemptStatus.COMPLETED,attempt.status)
            val facts=f.durableAudit.listNewestFirst().mapNotNull {it.accounting}.filter {it.rootAttemptId==attempt.attemptId.value}.distinctBy {it.callId}
            assertEquals(if(search) 3 else 2,facts.size)
            assertEquals(setOf("anthropic/claude-haiku-5.5","inception/mercury-3"),facts.map {it.requestedModelId}.toSet())
        }
    }

    @Test fun `Mercury image cannot egress without exact auxiliary recipient approval`() {
        Fixture(presetId=ModelPresetId.MERCURY_3,imageOnlyInput=true).use {f ->
            val (_,result)=f.send(approveReader=false)
            assertTrue(result is NormalChatOpenRouterExecutor.Result.Blocked)
            assertEquals(0,f.requestCount)
        }
    }

    @Test fun `Mercury reader failure missing identity truncation and cancellation never reaches answer`() {
        val outcomes=listOf(ProviderChatOutcome.TimedOut,ProviderChatOutcome.Cancelled,
            ProviderChatOutcome.StreamedResponse(200,"观察材料",null,100,20,null,false,finishReason="length",returnedModelId="anthropic/claude-haiku-5.5"),
            ProviderChatOutcome.StreamedResponse(200,"观察材料",null,100,20,null,false,finishReason="stop",returnedModelId=null))
        for(outcome in outcomes) Fixture(presetId=ModelPresetId.MERCURY_3,imageOnlyInput=true).use { f ->
            f.providerFactory={outcome}
            val (id,result)=f.send()
            if(outcome!=ProviderChatOutcome.Cancelled) assertFalse(result==NormalChatOpenRouterExecutor.Result.Sent)
            assertEquals(1,f.requestCount)
            assertFalse(f.attempts.findLatestForConversation(id)!!.status==NormalChatSendAttemptStatus.COMPLETED)
        }
    }

    @Test fun `Haiku image facts reach search before final answer with original image retained`() {
        Fixture(presetId=ModelPresetId.CLAUDE_HAIKU_5_5,webSearch=true,imageOnlyInput=true).use { f ->
            f.providerFactory = { request ->
                val wire=java.io.ByteArrayOutputStream().also {request.effectiveBody().writeTo(it)}.toString(Charsets.UTF_8.name())
                val body=org.json.JSONObject(wire)
                assertTrue("Haiku reading, retrieval and final answer must stream even with old catalog metadata",request.expectsStream)
                assertTrue(body.getBoolean("stream"))
                val text=when(f.requestCount) {
                    1 -> {assertFalse(body.has("plugins")); "### 附件\n台积电2026年第三季度营收467.2亿美元。"}
                    2 -> {assertTrue(wire.contains("台积电"));assertTrue(wire.contains("467.2"));assertTrue(body.has("plugins")); "[A1] 已核实\n台积电公开了2026年第三季度财报。\n来源：https://investor.tsmc.com/quarterly-results"}
                    else -> {assertTrue(wire.contains("image_url"));assertFalse(body.has("plugins")); "根据台积电官方财报核对图片。"}
                }
                ProviderChatOutcome.StreamedResponse(200,text,null,100,20,null,false,finishReason="stop",
                    webSources=if(f.requestCount==2) listOf(ProviderWebSource("https://investor.tsmc.com/quarterly-results")) else emptyList(),
                    webSearchPerformed=f.requestCount==2,returnedModelId="anthropic/claude-haiku-5.5")
            }
            val (id,result)=f.send()
            f.providerAssertion?.let {throw it}
            assertEquals(NormalChatOpenRouterExecutor.Result.Sent,result)
            assertEquals(3,f.requestCount)
            assertEquals(NormalChatSendAttemptStatus.COMPLETED,f.attempts.findLatestForConversation(id)!!.status)
        }
    }

    @Test fun `Haiku opening image without public facts keeps enabled search and original image`() {
        for (search in listOf(true,false)) Fixture(presetId=ModelPresetId.CLAUDE_HAIKU_5_5,webSearch=search,imageOnlyInput=true).use { f ->
            f.streamed=true
            f.answerOutcome=ProviderChatOutcome.StreamedResponse(200,"图片解读与查询结果",null,100,20,null,false,finishReason="stop",
                webSources=if(search) listOf(ProviderWebSource("https://example.org/public-result")) else emptyList(),
                webSearchPerformed=search,returnedModelId="anthropic/claude-haiku-5.5")
            if(search) f.providerFactory={ request ->
                if(f.requestCount==1) ProviderChatOutcome.StreamedResponse(200,"无公开事实",null,100,20,null,false,finishReason="stop",returnedModelId="anthropic/claude-haiku-5.5")
                else requireNotNull(f.answerOutcome)
            }
            val (id,result)=f.send()
            assertEquals(NormalChatOpenRouterExecutor.Result.Sent,result)
            assertEquals(ConversationRuntimeStatus.COMPLETED,f.conversations.stateFor(id)!!.status)
            assertEquals(NormalChatSendAttemptStatus.COMPLETED,f.attempts.findLatestForConversation(id)!!.status)
            assertEquals(if(search) 2 else 1,f.requestCount)
            val bytes=java.io.ByteArrayOutputStream().also { f.requests.last().effectiveBody().writeTo(it) }
            val body=org.json.JSONObject(bytes.toString(Charsets.UTF_8.name()))
            assertTrue(body.toString().contains("image_url"))
            assertEquals(search,body.has("plugins"))
            assertFalse(body.has("tools"))
            if(search) assertEquals("exa",body.getJSONArray("plugins").getJSONObject(0).getString("engine"))
            assertTrue(f.conversations.findById(id)!!.nodes.any {it.role==MessageRole.ASSISTANT && it.deliveryState==MessageDeliveryState.COMPLETE})
        }
    }

    @Test fun `Haiku enabled search without execution proof retains answer and fee as incomplete`() = Fixture(
        presetId=ModelPresetId.CLAUDE_HAIKU_5_5,webSearch=true,imageOnlyInput=true,
    ).use { f ->
        f.streamed=true; f.partialText="已收到的图片分析"
        f.answerOutcome=ProviderChatOutcome.StreamedResponse(200,"已收到的图片分析",null,100,20,ProviderReportedCost(120,"USD","test-receipt"),false,finishReason="stop",
            returnedModelId="anthropic/claude-haiku-5.5")
        f.providerFactory={ request ->
            if(f.requestCount==1) ProviderChatOutcome.StreamedResponse(200,"无公开事实",null,100,20,null,false,finishReason="stop",returnedModelId="anthropic/claude-haiku-5.5")
            else requireNotNull(f.answerOutcome)
        }
        val (id,result)=f.send()
        assertEquals(NormalChatOpenRouterExecutor.Result.Failed(NormalChatOpenRouterExecutor.Code.WEB_SEARCH_NO_SOURCES),result)
        assertEquals(2,f.requestCount)
        assertEquals(NormalChatSendAttemptStatus.FAILED,f.attempts.findLatestForConversation(id)!!.status)
        assertTrue(f.conversations.findById(id)!!.nodes.last {it.role==MessageRole.ASSISTANT}.content.filterIsInstance<ContentBlock.Text>().any {it.text.contains("已收到的图片分析")})
        assertTrue(f.durableAudit.listNewestFirst().any {it.accounting?.cost?.totalMicros==120L})
    }

    @Test fun `Luna retrieval keeps provider settlement for both grounded and rejected results`() {
        for (verified in listOf(true, false)) {
            val databaseName="luna-billing-risk-$verified-${java.util.UUID.randomUUID()}.db"
            try { Fixture(databaseName=databaseName,webSearch=true).use { f ->
            f.retrievalOutcome=ProviderChatOutcome.StreamedResponse(statusCode=200,text="公开事实摘要",reasoning=null,inputTokens=100,outputTokens=20,toolCallEncountered=false,
                webSources=if(verified) listOf(ProviderWebSource("https://example.org/release")) else emptyList(),
                webSearchPerformed=verified,returnedModelId="openai/gpt-6-luna",
                reportedProviderCost=ProviderReportedCost(9001,"USD","synthetic-settlement-v1"),
                cachedInputTokens=40,reasoningTokens=5)
            val (id,result)=f.send()
            if(verified) assertEquals(NormalChatOpenRouterExecutor.Result.Sent,result)
            else assertTrue(result is NormalChatOpenRouterExecutor.Result.Failed)
            val call=f.durableAudit.listNewestFirst().mapNotNull {it.accounting}
                .single {it.conversationId==id.value && it.role=="RETRIEVAL"}
            assertEquals("provider amount must survive verified=$verified",9001L,call.cost.totalMicros)
            assertEquals("USD",call.cost.currencyCode)
            assertEquals("synthetic-settlement-v1",call.cost.priceVersion)
            assertEquals(ConversationCostSource.PROVIDER_RESPONSE,call.costSource)
            assertEquals(40L,call.usage.cachedInputTokens)
            assertEquals(5L,call.usage.reasoningTokens)
            assertEquals("openai/gpt-6-luna",call.actualModelId)
            f.db.close()
            val reopened=Room.databaseBuilder(f.context,NanfengAiDatabase::class.java,databaseName).allowMainThreadQueries().build()
            try {
                val saved=AndroidDirectChatCallAuditStore(f.context,reopened).listNewestFirst().mapNotNull {it.accounting}
                    .single {it.conversationId==id.value && it.role=="RETRIEVAL"}
                assertEquals(call,saved)
            } finally { reopened.close() }
            } } finally { ApplicationProvider.getApplicationContext<Context>().deleteDatabase(databaseName) }
        }
    }

    @Test fun `Luna retrieval timestamps survive real Room persistence without a global search override`() {
        val movingClock=object:Clock() {
            var time=1_800_000_000_000L
            override fun getZone()=ZoneOffset.UTC
            override fun withZone(zone:java.time.ZoneId)=this
            override fun instant()=Instant.ofEpochMilli(++time)
        }
        Fixture(webSearch=true,independentSearch=true,clockOverride=movingClock).use {f->
            f.attachmentText="- Python 3.14.0正式发布，核验官方发布日期。"
            f.extraAttachmentTexts=listOf("- Rust 1.90.0正式发布，核验官方发布日期。")
            f.retrievalFactory={ProviderChatOutcome.StreamedResponse(200,(1..2).joinToString("\n\n") {"[A$it] 已核实\n公开发布事项的日期已确认。\n来源：https://example.org/release/$it"},null,100,20,null,false,webSources=(1..2).map {ProviderWebSource("https://example.org/release/$it","官方来源")},webSearchPerformed=true,returnedModelId="openai/gpt-6-luna")}
            f.answerOutcome=ProviderChatOutcome.StreamedResponse(200,"Python 3.14.0于2025年10月7日发布，Rust 1.90.0于2025年9月18日发布。",null,100,20,null,false,finishReason="end_turn",returnedModelId=f.model.modelId)
            val (id,result)=f.send(withAttachment=true)
            assertEquals(NormalChatOpenRouterExecutor.Result.Sent,result)
            val calls=f.durableAudit.listNewestFirst().mapNotNull {it.accounting}.filter {it.conversationId==id.value}
            val service=calls.filter {it.role=="RETRIEVAL"}
            assertEquals(1,service.size);assertTrue(service.all {it.finishedAt!=null && it.requestedModelId=="openai/gpt-6-luna" && it.actualModelId=="openai/gpt-6-luna" && it.usage.inputTokens==100L})
            assertEquals(1,calls.count {it.role=="ANSWER"});assertEquals(2,f.requestCount)
            assertTrue(f.executor.routingSnapshot().independentSearchAvailable)
            assertFalse(ProviderId.ZHIPU in f.executor.routingSnapshot().enabledProviders)
        }
    }

    @Test fun `Luna failed owner reopens quietly and explicit retry reuses original evidence and materials`() {
        val name="service-owner-retry-${java.util.UUID.randomUUID()}.db"
        var conversationId:ConversationId?=null
        Fixture(databaseName=name,webSearch=true,independentSearch=true).use {f->
            f.attachmentText="- Python 3.14.0正式发布，核验官方日期。";f.extraAttachmentTexts=listOf("- Rust 1.90.0正式发布，核验官方日期。")
            f.retrievalFactory={ProviderChatOutcome.StreamedResponse(200,(1..2).joinToString("\n\n") {"[A$it] 已核实\n公开发布事项的日期已确认。\n来源：https://example.org/release/$it"},null,100,20,null,false,webSources=(1..2).map {ProviderWebSource("https://example.org/release/$it","官方来源")},webSearchPerformed=true,returnedModelId="openai/gpt-6-luna")}
            f.answerOutcome=ProviderChatOutcome.NetworkFailure(ProviderNetworkFailureKind.IO)
            val (id,result)=f.send(withAttachment=true);conversationId=id
            assertEquals(NormalChatOpenRouterExecutor.Result.Failed(NormalChatOpenRouterExecutor.Code.NETWORK),result)
            assertEquals(2,f.requestCount);assertEquals(NormalChatSendAttemptStatus.UNKNOWN,f.attempts.findLatestForConversation(id)?.status)
            f.conversations.saveDraft(id,ConversationDraft(text="草稿修改不能进入旧重试",updatedAt=f.clock.instant()))
        }
        Fixture(databaseName=name,webSearch=true,independentSearch=true).use {f->
            val id=requireNotNull(conversationId);assertEquals(0,f.requestCount)
            assertEquals(NormalChatSendAttemptStatus.UNKNOWN,f.attempts.findLatestForConversation(id)?.status)
            f.retrievalFactory={error("completed service evidence must not be resent")}
            f.answerOutcome=ProviderChatOutcome.StreamedResponse(200,"基于原始两份公开材料继续回答。",null,100,20,null,false,finishReason="end_turn",returnedModelId=f.model.modelId)
            assertEquals(NormalChatOpenRouterExecutor.Result.Sent,f.executor.retryLatestAttempt(id))
            assertEquals(1,f.requestCount);val body=f.requests.single().jsonBody
            assertTrue(body.contains("Python 3.14.0"));assertTrue(body.contains("Rust 1.90.0"));assertFalse(body.contains("草稿修改不能进入旧重试"))
            val calls=f.durableAudit.listNewestFirst().mapNotNull {it.accounting}.filter {it.conversationId==id.value && it.role=="RETRIEVAL"}
            assertEquals(1,calls.size);assertTrue(calls.all {it.finishedAt!=null && it.actualModelId=="openai/gpt-6-luna"})
        }
        ApplicationProvider.getApplicationContext<Context>().deleteDatabase(name)
    }

    @Test fun `Luna search final RC GPT uses the ordinary answer endpoint and its decoder`() {
        Fixture(presetId=ModelPresetId.RELAYCORE_GPT_6_1_SOL,webSearch=true,independentSearch=true).use {f->
            f.attachmentText="- Go 1.24.0正式发布，核验官方发布日期。"
            f.retrievalFactory={ProviderChatOutcome.StreamedResponse(200,(1..1).joinToString("\n\n") {"[A$it] 已核实\n公开发布事项的日期已确认。\n来源：https://example.org/release/$it"},null,100,20,null,false,webSources=(1..1).map {ProviderWebSource("https://example.org/release/$it","官方来源")},webSearchPerformed=true,returnedModelId="openai/gpt-6-luna")}
            f.answerOutcome=ProviderChatOutcome.StreamedResponse(200,"Go 1.24.0于2025年2月11日发布。",null,100,20,null,false,finishReason="stop",returnedModelId=f.model.modelId)
            val (_,result)=f.send(withAttachment=true)
            assertEquals(NormalChatOpenRouterExecutor.Result.Sent,result)
            val final=f.requests.last();assertEquals("https://api.relaycore.cn/v1/chat/completions",final.endpoint)
            assertEquals(ProviderStreamTextMode.COMPLETIONS_API,final.streamTextMode)
            assertFalse(org.json.JSONObject(final.jsonBody).has("tools"))
        }
    }

    @Test fun `predicted attachment evidence cannot reject fitting material with the production fallback capacity`() {
        Fixture(webSearch=true,modelContextWindow=null).use { f ->
            f.retrievalOutcome=ProviderChatOutcome.HttpResponse(503,"synthetic explicit rejection")
            f.attachmentText=(1..60).joinToString("\n") { "- 公开发布事项$it：Python版本在2025年10月7日发布，请核验官方公告。" }
            val (_,result)=f.send(withAttachment=true)
            assertTrue("input fits the real 32768 fallback; predicted future evidence must not reject it: $result",result != NormalChatOpenRouterExecutor.Result.Blocked(NormalChatOpenRouterExecutor.Code.CONTEXT_LIMIT))
            assertEquals("retrieval and original material analysis must actually reach the transport",2,f.requestCount)
            assertTrue(f.requests.last().jsonBody.contains("公开发布事项60"))
        }
    }

    @Test fun `stream timeout retains received fee without changing timeout or unknown state`() {
        Fixture().use {f ->
            f.answerOutcome=ProviderChatOutcome.NetworkFailure(ProviderNetworkFailureKind.TIMEOUT,"SocketTimeoutException",partialResponse=
                ProviderChatOutcome.StreamedResponse(200,"partial",null,100,20,ProviderReportedCost(9001,"USD","synthetic-v1"),false,
                    finishReason="MISSING_COMPLETION",returnedModelId=f.model.modelId))
            val(id,result)=f.send()
            assertEquals(NormalChatOpenRouterExecutor.Result.Failed(NormalChatOpenRouterExecutor.Code.TIMEOUT),result)
            assertEquals(1,f.requestCount)
            assertEquals(NormalChatSendAttemptStatus.UNKNOWN,f.attempts.findLatestForConversation(id)!!.status)
            val call=f.durableAudit.listNewestFirst().mapNotNull {it.accounting}.single {it.conversationId==id.value}
            assertEquals(9001L,call.cost.totalMicros);assertEquals(100L,call.usage.inputTokens)
        }
    }

    @Test fun `unknown attachment retrieval stops before final answer and preserves received accounting`() {
        for(outcome in listOf(ProviderChatOutcome.TimedOut,ProviderChatOutcome.ResponseTooLarge,
            ProviderChatOutcome.HttpResponse(408,"{}"),ProviderChatOutcome.HttpResponse(504,"{}"),
            ProviderChatOutcome.NetworkFailure(ProviderNetworkFailureKind.IO,"EOFException",partialResponse=
                ProviderChatOutcome.StreamedResponse(200,"unverified",null,100,20,ProviderReportedCost(9001,"USD","synthetic-v1"),false,
                    finishReason="MISSING_COMPLETION",returnedModelId="openai/gpt-6-luna")))) {
            Fixture(webSearch=true).use { f ->
                f.attachmentText="Python 3.14在2025年10月7日发布。"
                f.retrievalOutcome=outcome
                val(id,result)=f.send(withAttachment=true)
                assertTrue(result is NormalChatOpenRouterExecutor.Result.Failed)
                assertEquals("no final model request after unknown search",1,f.requestCount)
                assertEquals(NormalChatSendAttemptStatus.UNKNOWN,f.attempts.findLatestForConversation(id)!!.status)
                if(outcome is ProviderChatOutcome.NetworkFailure) {
                    val calls=f.durableAudit.listNewestFirst().mapNotNull {it.accounting}.filter {it.conversationId==id.value}
                    assertEquals(1,calls.size);assertEquals(9001L,calls.single().cost.totalMicros)
                }
            }
        }
    }

    @Test fun `unknown retrieval terminal never triggers a targeted paid resend`() {
        Fixture(webSearch=true,modelContextWindow=null).use { f ->
            f.attachmentText="- Python 3.14.0在2025年10月7日正式发布。"
            f.retrievalOutcome=ProviderChatOutcome.StreamedResponse(200,"检索未确认的部分内容",null,100,20,null,false,finishReason="MISSING_COMPLETION")
            f.send(withAttachment=true)
            assertEquals("unknown retrieval must stop before any further paid call",1,f.requestCount)
        }
    }
    @Test fun `explicit content rejection stops all remaining searches without paid supplements`() {
        Fixture(webSearch=true,modelContextWindow=null).use { f ->
            f.attachmentText=(1..8).joinToString("\n") {"- Python 3.14.${it}在2025年发布，请核验官方公告。"}
            f.retrievalOutcome=ProviderChatOutcome.StreamedResponse(200,"",null,100,20,null,false,finishReason="CONTENT_FILTER")
            f.send(withAttachment=true)
            assertEquals("one rejected retrieval plus independent material answer; no supplemental searches",2,f.requestCount)
        }
    }

    @Test fun `actual evidence overflow after search reports the external search separately`() {
        Fixture(webSearch=true,modelContextWindow=8192).use { f ->
            f.attachmentText="- Python 3.14.0在2025年10月7日发布。\n我的持仓："+"测试".repeat(1400)
            val url="https://www.python.org/releases/"
            f.retrievalOutcome=ProviderChatOutcome.StreamedResponse(200,"[A1] 已核实\nPython "+"公开事实".repeat(105)+"\n来源：$url",null,100,20,null,false,
                webSources=listOf(ProviderWebSource(url,"Python")),webSearchPerformed=true,returnedModelId="openai/gpt-6-luna")
            val(_,result)=f.send(withAttachment=true)
            assertEquals(NormalChatOpenRouterExecutor.Result.Failed(NormalChatOpenRouterExecutor.Code.ANSWER_CONTEXT_LIMIT),result)
            assertEquals("completed search is preserved; final answer must not be sent",1,f.requestCount)
        }
    }

    @Test fun `actual final assembly uses the selected large capacity rather than a universal evidence cap`() {
        Fixture(webSearch=true,modelContextWindow=200000).use { f ->
            f.attachmentText=(1..90).joinToString("\n") { "- 公开发布事项$it：Python在2025年发布，请核验官方公告。" }
            f.retrievalFactory={ request ->
                val ids=Regex("\\[(A\\d+)\\]").findAll(request.jsonBody.substringAfter("附件待核验要点")).map {it.groupValues[1]}.distinct().toList()
                val urls=ids.map {"https://www.python.org/releases/$it"}
                val evidence=ids.zip(urls).joinToString("\n\n") {(id,url)-> "[$id] 已核实\nPython "+"公开事实".repeat(100)+"\n来源：$url" }
                ProviderChatOutcome.StreamedResponse(200,evidence,null,100,20,null,false,
                    webSources=urls.map {ProviderWebSource(it,"Python")},webSearchPerformed=true,returnedModelId="openai/gpt-6-luna")
            }
            val(_,result)=f.send(withAttachment=true)
            assertEquals(NormalChatOpenRouterExecutor.Result.Sent,result)
            assertTrue(f.requests.last().jsonBody.contains("公开发布事项90"))
            assertTrue(f.requests.last().jsonBody.length>32768)
        }
    }

    @Test fun `actual mandatory attachment overflow still rejects before any external call`() {
        Fixture(webSearch=true,modelContextWindow=null).use { f ->
            f.attachmentText="公开资料".repeat(10000)
            val (_,result)=f.send(withAttachment=true)
            assertEquals(NormalChatOpenRouterExecutor.Result.Blocked(NormalChatOpenRouterExecutor.Code.CONTEXT_LIMIT),result)
            assertEquals(0,f.requestCount)
        }
    }

    @Test fun `companion timeout records a safe retrieval diagnostic without calling the answer model`() {
        Fixture(webSearch=true).use { f ->
            val (_,result)=f.send()
            assertEquals(NormalChatOpenRouterExecutor.Result.Failed(NormalChatOpenRouterExecutor.Code.WEB_SEARCH_COMPANION_FAILED),result)
            assertEquals(1,f.requestCount)
            val rows=RoomProviderDiagnosticStore(f.db).recent()
            assertEquals(1,rows.size)
            val record=rows.single()
            assertEquals(ProviderId.OPENROUTER,record.providerId)
            assertEquals("openai/gpt-6-luna",record.apiModelId)
            assertEquals(504,record.httpStatus)
            assertEquals(ProviderDiagnosticErrorClass.TIMEOUT,record.errorClass)
            assertEquals("RETRIEVAL_HTTP_504",record.redactedBody)
            assertFalse(record.toString().contains("synthetic private upstream body"))
        }
    }

    @Test fun `ordinary generation carries a language contract independently of personalization and source language`() {
        Fixture().use { f ->
            f.send()
            val body=f.requests.single().jsonBody
            assertTrue(body.contains("默认使用简体中文"))
            assertTrue(body.contains("用户明确指定其他语言"))
            assertTrue(body.contains("先用回答语言译述"))
            assertTrue(body.contains("不得逐段夹带外语原文"))
        }
    }

    @Test fun `an explicit user language preference remains in the request alongside the default contract`() {
        Fixture(customInstructions="Please answer in English.").use { f ->
            f.send()
            val body=f.requests.single().jsonBody
            assertTrue(body.contains("Please answer in English."))
            assertTrue(body.contains("用户明确指定其他语言"))
            assertTrue(body.contains("当前用户明确要求优先"))
        }
    }

    @Test fun `Opus sends verified platform alias and old failure retry keeps its historical identity`() {
        Fixture(presetId=ModelPresetId.RELAYCORE_OPUS_5_5).use {f ->
            f.providerFailure=IllegalStateException("synthetic failure")
            val (id,_)=f.send(withAttachment=true)
            assertEquals("claude-opus-5.5",org.json.JSONObject(f.requests.first().jsonBody).getString("model"))
            val attempt=f.attempts.findLatestForConversation(id)!!
            f.db.openHelper.writableDatabase.execSQL("UPDATE normal_chat_send_attempts SET modelId='claude-opus-5-5' WHERE attemptId=?",arrayOf(attempt.attemptId.value))
            f.providerFailure=null;f.replyText="附件分析成功"
            assertEquals(NormalChatOpenRouterExecutor.Result.Sent,f.executor.retryLatestAttempt(id))
            assertEquals("claude-opus-5.5",org.json.JSONObject(f.requests.last().jsonBody).getString("model"))
            assertTrue(f.requests.last().jsonBody.contains("系统处理100个请求"))
            assertEquals("claude-opus-5-5",f.attempts.findLatestForConversation(id)!!.modelId)
            assertEquals(2,f.requestCount)
        }
    }

    @Test fun `attachment retrieval searches document claims instead of generic report methods`() {
        Fixture(webSearch=true).use {f ->
            f.attachmentText="# 公开报告\n(1) Python 3.14.0在2025年10月7日发布。\n相关链接：https://www.python.org/downloads/release/python-3140/\n(2) Rust 1.90.0在2025年9月18日发布。"
            f.send(withAttachment=true)
            assertTrue(f.requests.first().jsonBody.contains("Python 3.14.0"))
            assertTrue(f.requests.first().jsonBody.contains("Rust 1.90.0"))
            assertFalse(f.requests.first().jsonBody.contains("public-fixture-"))
        }
    }

    @Test fun `each companion model receives covered attachment facts and completes original answer`() {
        for(preset in listOf(ModelPresetId.RELAYCORE_OPUS_5_5,ModelPresetId.RELAYCORE_SONNET_5_5,ModelPresetId.RELAYCORE_GEMINI_3_8_FLASH,ModelPresetId.RELAYCORE_GEMINI_3_1_PRO)) Fixture(presetId=preset,webSearch=true).use {f ->
            f.attachmentText="(1) Python 3.14.0在2025年10月8日发布。"
            f.extraAttachmentTexts=listOf("(1) Rust 1.90.0在2025年9月18日发布。")
            val evidence="[A1] 与材料矛盾\nPython官方发布日期为2025年10月7日。\n来源：https://www.python.org/releases/\n[A2] 已核实\nRust官方发布日期为2025年9月18日。\n来源：https://blog.rust-lang.org/releases/"
            f.retrievalOutcome=ProviderChatOutcome.StreamedResponse(200,evidence,null,100,20,null,false,webSources=listOf(ProviderWebSource("https://www.python.org/releases/","来源网站 https://publisher.example.test/"),ProviderWebSource("https://blog.rust-lang.org/releases/","Rust")),webSearchPerformed=true,returnedModelId="openai/gpt-6-luna")
            val(id,result)=f.send(withAttachment=true)
            assertEquals(NormalChatOpenRouterExecutor.Result.Sent,result)
            assertEquals(2,f.requestCount)
            assertTrue(f.requests.first().jsonBody.contains("Python 3.14.0"))
            assertTrue(f.requests.first().jsonBody.contains("Rust 1.90.0"))
            assertFalse(f.requests.first().jsonBody.contains("public-fixture-"))
            assertTrue(f.requests.last().jsonBody.contains("2025年10月7日"))
            assertTrue(f.requests.last().jsonBody.contains("与材料矛盾"))
            assertTrue(f.requests.last().jsonBody.contains("不得只搜索分析报告的方法"))
            assertTrue(f.requests.last().jsonBody.contains(f.model.modelId))
            assertEquals(MessageDeliveryState.COMPLETE,f.conversations.findById(id)!!.nodes.single {it.role==MessageRole.ASSISTANT}.deliveryState)
        }
    }

    @Test fun `completed attachment retrieval survives failed final answer with message bound coverage and original usage`() {
        val databaseName="attachment-failed-answer-${java.util.UUID.randomUUID()}.db"
        val fixture=Fixture(databaseName=databaseName,webSearch=true)
        var answerId: MessageNodeId? = null
        var conversationId: ConversationId? = null
        fixture.use {f ->
            f.attachmentText="(1) Python 3.14.0在2025年10月7日发布。"
            f.retrievalOutcome=ProviderChatOutcome.StreamedResponse(200,"[A1] 已核实\nPython官方日期为2025年10月7日。\n来源：https://www.python.org/releases/",null,100,20,null,false,
                webSources=listOf(ProviderWebSource("https://www.python.org/releases/","Python")),webSearchPerformed=true,returnedModelId="openai/gpt-6-luna")
            f.answerOutcome=ProviderChatOutcome.NetworkFailure(ProviderNetworkFailureKind.IO)
            val(id,result)=f.send(withAttachment=true)
            assertEquals(NormalChatOpenRouterExecutor.Result.Failed(NormalChatOpenRouterExecutor.Code.NETWORK),result)
            assertEquals(2,f.requestCount)
            val attempt=f.attempts.findLatestForConversation(id)!!
            assertEquals(NormalChatSendAttemptStatus.UNKNOWN,attempt.status)
            val answer=f.conversations.findById(id)!!.nodes.single {it.role==MessageRole.ASSISTANT}
            answerId=answer.id;conversationId=id
            val records=AndroidContextSelectionAuditStore(f.context,f.db).forAssistantMessages(listOf(answer.id))[answer.id].orEmpty()
            assertTrue(records.flatMap {it.selectedSources}.any {it.title=="1/1项检索结果" && it.kind=="联网核验范围"})
            assertTrue(records.all {it.webSearchUsed==null})
            val calls=f.durableAudit.listNewestFirst().mapNotNull {it.accounting}.filter {it.rootAttemptId==attempt.attemptId.value}
            assertEquals(2,calls.size)
            assertEquals(100L,calls.single {it.role=="RETRIEVAL"}.usage.inputTokens)
            assertNull(calls.single {it.role=="ANSWER"}.usage.inputTokens)
            assertNull(calls.single {it.role=="ANSWER"}.cost.totalMicros)
        }
        val reopened=Room.databaseBuilder(fixture.context,NanfengAiDatabase::class.java,databaseName).allowMainThreadQueries().build()
        try {
            assertEquals(NormalChatSendAttemptStatus.UNKNOWN,RoomNormalChatSendAttemptStore(reopened).findLatestForConversation(requireNotNull(conversationId))!!.status)
            val records=AndroidContextSelectionAuditStore(fixture.context,reopened).forAssistantMessages(listOf(requireNotNull(answerId)))[answerId].orEmpty()
            assertTrue(records.flatMap {it.selectedSources}.any {it.title=="1/1项检索结果"})
            val calls=AndroidDirectChatCallAuditStore(fixture.context,reopened).listNewestFirst().mapNotNull {it.accounting}.filter {it.conversationId==requireNotNull(conversationId).value}
            assertEquals(2,calls.size)
            assertEquals(100L,calls.single {it.role=="RETRIEVAL"}.usage.inputTokens)
            assertNull(calls.single {it.role=="ANSWER"}.cost.totalMicros)
        } finally {reopened.close();fixture.context.deleteDatabase(databaseName)}
    }
    @Test fun `missing final stream completion retains text and unknown attempt without becoming a format error`() {
        Fixture().use {f ->
            f.streamed=true;f.streamChunks=listOf("已收到的内容")
            f.answerOutcome=ProviderChatOutcome.StreamedResponse(200,"已收到的内容",null,100,20,null,false,finishReason="MISSING_COMPLETION")
            val(id,result)=f.send()
            assertEquals(NormalChatOpenRouterExecutor.Result.Failed(NormalChatOpenRouterExecutor.Code.RESPONSE_INCOMPLETE),result)
            assertEquals(NormalChatSendAttemptStatus.UNKNOWN,f.attempts.findLatestForConversation(id)!!.status)
            assertEquals(1,f.requestCount)
            val answer=f.conversations.findById(id)!!.nodes.single {it.role==MessageRole.ASSISTANT}
            assertEquals("已收到的内容",answer.content.filterIsInstance<ContentBlock.Text>().joinToString(""){it.text})
            assertEquals(MessageDeliveryState.FAILED,answer.deliveryState)
        }
    }
    @Test fun `personal attachment derives safe subject and searches without a public event gate`() {
        Fixture(webSearch=true).use {f ->
            f.attachmentText="我的持仓预算是98765元，想理解风险分散。"
            f.providerFactory={ request ->
                val wire=java.io.ByteArrayOutputStream().also {request.effectiveBody().writeTo(it)}.toString(Charsets.UTF_8.name())
                val text=when(f.requestCount) {
                    1 -> "风险分散与资产配置"
                    2 -> {
                        assertTrue(wire.contains("风险分散"));assertFalse(wire.contains("98765"))
                        "[A1] 未核实\n风险分散与资产配置属于投资风险管理主题，不能直接核实个人持仓。\n来源：https://example.org/diversification"
                    }
                    else -> "结合材料与检索资料解释风险分散。"
                }
                ProviderChatOutcome.StreamedResponse(200,text,null,100,20,null,false,finishReason="stop",
                    webSources=if(f.requestCount==2) listOf(ProviderWebSource("https://example.org/diversification")) else emptyList(),
                    webSearchPerformed=f.requestCount==2,returnedModelId=if(f.requestCount==2) "openai/gpt-6-luna" else f.model.modelId)
            }
            val(id,result)=f.send(withAttachment=true)
            f.providerAssertion?.let {throw it}
            assertEquals(3,f.requestCount)
            assertEquals(NormalChatOpenRouterExecutor.Result.Sent,result)
            assertEquals(MessageDeliveryState.COMPLETE,f.conversations.findById(id)!!.nodes.single {it.role==MessageRole.ASSISTANT}.deliveryState)
        }
    }

    @Test fun `unrelated verified webpage cannot complete document fact checking`() {
        Fixture(webSearch=true).use {f ->
            f.attachmentText="(1) Python 3.14.0在2025年10月7日发布。"
            f.retrievalOutcome=ProviderChatOutcome.StreamedResponse(200,"分析报告应该严谨，要引用证据。",null,100,20,null,false,webSources=listOf(ProviderWebSource("https://example.test/report-method","报告方法")),webSearchPerformed=true,returnedModelId="openai/gpt-6-luna")
            val(id,result)=f.send(withAttachment=true)
            assertTrue(result is NormalChatOpenRouterExecutor.Result.Failed)
            assertEquals(MessageDeliveryState.FAILED,f.conversations.findById(id)!!.nodes.single {it.role==MessageRole.ASSISTANT}.deliveryState)
        }
    }

    @Test fun `failed web retrieval does not suppress an authorized attachment analysis`() {
        for (preset in listOf(ModelPresetId.RELAYCORE_OPUS_5_5, ModelPresetId.RELAYCORE_SONNET_5_5, ModelPresetId.RELAYCORE_GEMINI_3_8_FLASH, ModelPresetId.RELAYCORE_GEMINI_3_1_PRO)) Fixture(presetId=preset,webSearch=true).use {f ->
            f.retrievalOutcome=ProviderChatOutcome.HttpResponse(503,"synthetic explicit rejection")
            val (id,result)=f.send(withAttachment=true)
            assertEquals(2,f.requestCount)
            assertTrue(f.requests.last().jsonBody.contains("\"model\":\"${f.model.modelId}\""))
            assertEquals("https://api.relaycore.cn/v1/" + if (preset in listOf(ModelPresetId.RELAYCORE_OPUS_5_5,ModelPresetId.RELAYCORE_SONNET_5_5)) "messages" else "chat/completions", f.requests.last().endpoint)
            val answer=f.conversations.findById(id)!!.nodes.single {it.role==MessageRole.ASSISTANT}
            assertTrue(answer.content.filterIsInstance<ContentBlock.Text>().joinToString(""){it.text}.contains(f.replyText))
            assertEquals(MessageDeliveryState.FAILED,answer.deliveryState)
            assertTrue(result is NormalChatOpenRouterExecutor.Result.Failed)
            assertTrue(f.requests.last().jsonBody.contains("系统处理100个请求"))
            assertTrue(f.requests.last().jsonBody.contains("外部检索未完成"))
            assertTrue(f.requests.first().jsonBody.contains("系统处理100个请求"))
            assertFalse(f.requests.first().jsonBody.contains("public-fixture-"))
        }
    }

    @Test fun `attachment analysis retains the streamed final suffix when retrieval failed`() {
        Fixture(webSearch=true).use {f ->
            f.retrievalOutcome=ProviderChatOutcome.HttpResponse(503,"synthetic explicit rejection")
            f.streamed=true;f.streamChunks=listOf("完整");f.replyText="完整回答"
            val(id,result)=f.send(withAttachment=true)
            assertTrue(result is NormalChatOpenRouterExecutor.Result.Failed)
            val answer=f.conversations.findById(id)!!.nodes.single {it.role==MessageRole.ASSISTANT}
            assertEquals("完整回答",answer.content.filterIsInstance<ContentBlock.Text>().joinToString(""){it.text})
            assertEquals(MessageDeliveryState.FAILED,answer.deliveryState)
            assertEquals(2,f.requestCount)
        }
    }

    @Test fun `stopping retrieval prevents any subsequent attachment generation`() {
        Fixture(webSearch=true).use {f ->
            f.retrievalOutcome=ProviderChatOutcome.Cancelled
            val(id,_)=f.send(withAttachment=true)
            assertEquals(1,f.requestCount)
            assertEquals(MessageDeliveryState.CANCELLED,f.conversations.findById(id)!!.nodes.single {it.role==MessageRole.ASSISTANT}.deliveryState)
        }
    }

    @Test fun `background metadata observes durable unicode checkpoints without retaining body`() {
        Fixture().use {f ->
            f.streamed=true;f.streamChunks=listOf("甲🙂\n", "乙 ");f.replyText="甲🙂\n乙 "
            val ledger=GenerationIslandLedger();var checkpoints=0
            f.afterChunk={
                val id=f.lastConversationId!!;val snapshot=f.conversations.findById(id)!!
                val answer=snapshot.nodes.first {it.id==snapshot.conversation.currentLeafMessageId}
                val count=f.conversations.savedTextCharacters(answer.id)
                if(checkpoints++==0) assertEquals(3,count)
                ledger.begin(ConversationSurface.CHAT,id,0);ledger.checkpoint(ConversationSurface.CHAT,id,count,1000,"Sonnet 5.5 · RC")
            }
            val (id,result)=f.send()
            assertEquals(NormalChatOpenRouterExecutor.Result.Sent,result)
            val answer=f.conversations.findById(id)!!.nodes.single {it.role==MessageRole.ASSISTANT}
            assertEquals(4,f.conversations.savedTextCharacters(answer.id))
            assertEquals(1,f.requestCount)
            assertTrue(ledger.snapshot().single().savedCharacters in 3..5)
            assertEquals("Sonnet 5.5 · RC",ledger.snapshot().single().modelLabel)
        }
    }
    @Test fun `all Relay presets persist visible text before completion and retain selected identity`() {
        for(spec in RelayCoreModels.specs) Fixture(presetId=spec.preset).use {f ->
            f.streamed=true;f.streamChunks=listOf("第一段","第二段");f.replyText="第一段第二段"
            var observed=false
            f.afterPartial={
                val id=f.lastConversationId!!
                val state=f.conversations.stateFor(id)!!
                assertFalse(state.isTerminal)
                val answer=f.conversations.findById(id)!!.nodes.single {it.role==MessageRole.ASSISTANT}
                assertTrue(answer.content.filterIsInstance<ContentBlock.Text>().joinToString("") {it.text}.contains("第一段"))
                observed=true
            }
            val (id,result)=f.send();assertTrue(observed);assertEquals(NormalChatOpenRouterExecutor.Result.Sent,result)
            val request=f.requests.single();assertTrue(request.expectsStream);assertTrue(request.jsonBody.contains("\"stream\":true"));assertTrue(request.jsonBody.contains(spec.apiId))
            assertEquals(ConversationRuntimeStatus.COMPLETED,f.conversations.stateFor(id)!!.status)
            val answer=f.conversations.findById(id)!!.nodes.single {it.role==MessageRole.ASSISTANT}
            assertEquals("第一段第二段",answer.content.filterIsInstance<ContentBlock.Text>().joinToString("") {it.text})
        }
    }

    @Test fun `real precision non streaming and streaming finish without crashing`() {
        for(stream in listOf(false,true)) Fixture().use {f ->
            f.streamed=stream;val (id,result)=f.send()
            assertEquals(NormalChatOpenRouterExecutor.Result.Sent,result)
            assertEquals(ConversationRuntimeStatus.COMPLETED,f.conversations.stateFor(id)!!.status)
            assertEquals(1,f.requestCount);assertTrue(f.credential!!.all {it=='\u0000'})
            assertEquals(1,f.durableAudit.summary().recordCount)
            val answer=f.conversations.findById(id)!!.nodes.single {it.role==MessageRole.ASSISTANT}
            assertEquals("完整回答",answer.content.filterIsInstance<ContentBlock.Text>().joinToString("") {it.text})
            assertEquals(100L,f.attribution.forMessages(listOf(answer.id)).getValue(answer.id).single().accountingCalls.single().usage.inputTokens)
        }
    }
    @Test fun `pending audit failure blocks provider without leaving an active placeholder`() = Fixture().use {f ->
        f.auditFailure={true};val (id,result)=f.send()
        assertTrue(result is NormalChatOpenRouterExecutor.Result.Failed)
        assertEquals(0,f.requestCount);assertTrue(f.credential!!.all {it=='\u0000'})
        assertTrue(f.conversations.stateFor(id)!!.isTerminal)
        assertEquals(NormalChatSendAttemptStatus.FAILED,f.attempts.findLatestForConversation(id)!!.status)
    }
    @Test fun `final audit failure preserves complete text and measured attribution`() = Fixture().use {f ->
        f.auditFailure={!it.status.endsWith("PENDING")};val (id,result)=f.send()
        assertEquals(NormalChatOpenRouterExecutor.Result.Failed(NormalChatOpenRouterExecutor.Code.LOCAL_ACCOUNTING_PERSISTENCE),result)
        assertEquals(ConversationRuntimeStatus.COMPLETED,f.conversations.stateFor(id)!!.status)
        val answer=f.conversations.findById(id)!!.nodes.single {it.role==MessageRole.ASSISTANT}
        assertEquals("完整回答",answer.content.filterIsInstance<ContentBlock.Text>().joinToString("") {it.text})
        assertEquals(20L,f.attribution.forMessages(listOf(answer.id)).getValue(answer.id).single().usage.outputTokens)
        assertEquals(1,f.requestCount)
    }
    @Test fun `unexpected provider exception preserves partial marks unknown and allows another conversation`() = Fixture().use {f ->
        f.providerFailure=IllegalStateException("synthetic failure");val (id,result)=f.send()
        assertTrue(result is NormalChatOpenRouterExecutor.Result.Failed)
        assertEquals(NormalChatSendAttemptStatus.UNKNOWN,f.attempts.findLatestForConversation(id)!!.status)
        assertEquals(ConversationRuntimeStatus.FAILED,f.conversations.stateFor(id)!!.status)
        assertEquals("已收到的部分内容",f.conversations.findById(id)!!.nodes.single {it.role==MessageRole.ASSISTANT}.content.filterIsInstance<ContentBlock.Text>().joinToString("") {it.text})
        assertTrue(f.credential!!.all {it=='\u0000'});assertEquals(1,f.requestCount)
        f.providerFailure=null;assertEquals(NormalChatOpenRouterExecutor.Result.Sent,f.send().second)
        assertEquals(2,f.requestCount)
    }
    @Test fun `interrupted stream preserves standalone whitespace in durable checkpoint`() = Fixture().use { f ->
        f.streamChunks = listOf("第一段", "\n\n", "第二段", " ", "保留")
        f.providerFailure = IllegalStateException("synthetic interruption")
        val (id, _) = f.send()
        val checkpoint = f.conversations.findById(id)!!.nodes.single { it.role == MessageRole.ASSISTANT }.content.filterIsInstance<ContentBlock.Text>().joinToString("") { it.text }
        assertEquals("第一段\n\n第二段 保留", checkpoint)
        assertEquals(1, f.requestCount)
    }
    @Test fun `retry retains durable prefix at submission and sends it to the original model for continuation`() = Fixture().use {f ->
        f.partialText="这是一段已生成的回答，必须完整保留在当前可见回复里。"
        f.providerFailure=IllegalStateException("synthetic failure");val (id,_)=f.send()
        val original=f.conversations.stateFor(id)!!.messageId
        f.providerFailure=null;f.replyText="这是紧接断点的后续内容。"
        var visibleAtSubmission=""
        val result=f.executor.retryLatestAttempt(id,onLocalSubmission={
            val state=f.conversations.stateFor(id)!!
            visibleAtSubmission=f.conversations.findById(id)!!.nodes.single {it.id==state.messageId}.content.filterIsInstance<ContentBlock.Text>().joinToString("") {it.text}
        })
        assertEquals(NormalChatOpenRouterExecutor.Result.Sent,result)
        assertEquals(f.partialText,visibleAtSubmission)
        assertTrue(f.requests.last().jsonBody.contains("默认使用简体中文"))
        val state=f.conversations.stateFor(id)!!
        val snapshot=f.conversations.findById(id)!!
        assertEquals(f.partialText+f.replyText,snapshot.nodes.single {it.id==state.messageId}.content.filterIsInstance<ContentBlock.Text>().joinToString("") {it.text})
        assertEquals(f.partialText,snapshot.nodes.single {it.id==original}.content.filterIsInstance<ContentBlock.Text>().joinToString("") {it.text})
        val body=org.json.JSONObject(f.requests.last().jsonBody)
        assertTrue(body.toString().contains(f.partialText))
        assertTrue(body.toString().contains("继续"))
        assertEquals("claude-sonnet-5-5",body.getString("model"))
        assertNotEquals(f.requests.first().idempotencyKey,f.requests.last().idempotencyKey)
        assertEquals(2,f.requestCount)
    }
    @Test fun `retry repeating checkpoint text keeps exactly one prefix in final answer`() = Fixture().use {f ->
        f.partialText="这是断点之前已经生成的一整段回答，需要完整保存而且不能重复。"
        f.providerFailure=IllegalStateException("synthetic failure");val (id,_)=f.send()
        f.providerFailure=null;f.replyText=f.partialText+"新的结尾。"
        assertEquals(NormalChatOpenRouterExecutor.Result.Sent,f.executor.retryLatestAttempt(id))
        val message=f.conversations.stateFor(id)!!.messageId
        assertEquals(f.replyText,f.conversations.findById(id)!!.nodes.single {it.id==message}.content.filterIsInstance<ContentBlock.Text>().joinToString("") {it.text})
    }
    @Test fun `echo only continuation stays incomplete and retains the original checkpoint`() {
        for (stream in listOf(false,true)) Fixture().use {f ->
            f.partialText="这是中断之前已经生成的长段落，需要保留，不能把重复回显当作补全。"
            f.providerFailure=IllegalStateException("synthetic failure");val (id,_)=f.send()
            f.providerFailure=null;f.streamed=stream;f.replyText=f.partialText;f.streamChunks=listOf(f.partialText)
            assertEquals(NormalChatOpenRouterExecutor.Result.Failed(NormalChatOpenRouterExecutor.Code.RESPONSE_INCOMPLETE),f.executor.retryLatestAttempt(id))
            val state=f.conversations.stateFor(id)!!
            assertEquals(ConversationRuntimeStatus.FAILED,state.status)
            assertTrue(f.executor.recoveryForConversation(id)!!.canRetry)
            assertEquals(f.partialText,f.conversations.findById(id)!!.nodes.single {it.id==state.messageId}.content.filterIsInstance<ContentBlock.Text>().joinToString("") {it.text})
        }
    }
    @Test fun `explicit stop retains prefix and can be retried without an active spinner`() = Fixture().use {f ->
        f.returnsCancelled=true;f.partialText="用户停止前已生成的内容。";val (id,_)=f.send()
        assertEquals(ConversationRuntimeStatus.CANCELLED,f.conversations.stateFor(id)!!.status)
        assertTrue(f.executor.recoveryForConversation(id)!!.canRetry)
        f.returnsCancelled=false;f.replyText="继续完成。"
        assertEquals(NormalChatOpenRouterExecutor.Result.Sent,f.executor.retryLatestAttempt(id))
        val message=f.conversations.stateFor(id)!!.messageId
        assertEquals(f.partialText+f.replyText,f.conversations.findById(id)!!.nodes.single {it.id==message}.content.filterIsInstance<ContentBlock.Text>().joinToString("") {it.text})
    }
    @Test fun `repeated interruptions resume the latest persisted prefix without a second automatic call`() = Fixture().use {f ->
        val first="第一段在第一次中断前保存。";val second="第二段在第二次中断前保存。"
        f.partialText=first;f.providerFailure=IllegalStateException("synthetic failure");val (id,_)=f.send()
        f.partialText=second
        assertTrue(f.executor.retryLatestAttempt(id) is NormalChatOpenRouterExecutor.Result.Failed)
        var state=f.conversations.stateFor(id)!!
        assertEquals(first+second,f.conversations.findById(id)!!.nodes.single {it.id==state.messageId}.content.filterIsInstance<ContentBlock.Text>().joinToString("") {it.text})
        assertEquals(2,f.requestCount)
        f.providerFailure=null;f.replyText="第三段最后完成。"
        assertEquals(NormalChatOpenRouterExecutor.Result.Sent,f.executor.retryLatestAttempt(id))
        state=f.conversations.stateFor(id)!!
        assertEquals(first+second+f.replyText,f.conversations.findById(id)!!.nodes.single {it.id==state.messageId}.content.filterIsInstance<ContentBlock.Text>().joinToString("") {it.text})
        assertEquals(3,f.requestCount);assertEquals(3,f.requests.map {it.idempotencyKey}.distinct().size)
    }
    @Test fun `streamed continuation echo is merged with the checkpoint exactly once`() = Fixture().use {f ->
        val prefix="这是先前生成的完整断点段落，包含足够长的文字用于精确识别重复。"
        f.partialText=prefix;f.providerFailure=IllegalStateException("synthetic failure");val (id,_)=f.send()
        f.providerFailure=null;f.streamed=true;f.replyText=prefix+"新的结尾。";f.streamChunks=listOf(prefix.take(7),prefix.drop(7),"新的结尾。")
        assertEquals(NormalChatOpenRouterExecutor.Result.Sent,f.executor.retryLatestAttempt(id))
        val message=f.conversations.stateFor(id)!!.messageId
        assertEquals(f.replyText,f.conversations.findById(id)!!.nodes.single {it.id==message}.content.filterIsInstance<ContentBlock.Text>().joinToString("") {it.text})
    }
    @Test fun `reopened database exposes manual recovery and retains its exact checkpoint`() {
        val name="resume-reopen-${java.util.UUID.randomUUID()}.db";val prefix="退出进程之前已持久保存的断点文字。";var id: ConversationId?=null
        try {
            Fixture(name).use {f ->f.partialText=prefix;f.providerFailure=IllegalStateException("synthetic failure");id=f.send().first}
            Fixture(name).use {f ->
                assertEquals(0,f.requestCount);assertTrue(f.executor.recoveryForConversation(id!!)!!.canRetry)
                f.replyText="重开后接着完成。";assertEquals(NormalChatOpenRouterExecutor.Result.Sent,f.executor.retryLatestAttempt(id!!))
                val message=f.conversations.stateFor(id!!)!!.messageId
                assertEquals(prefix+f.replyText,f.conversations.findById(id!!)!!.nodes.single {it.id==message}.content.filterIsInstance<ContentBlock.Text>().joinToString("") {it.text})
                assertEquals(1,f.requestCount)
            }
        } finally {ApplicationProvider.getApplicationContext<Context>().deleteDatabase(name)}
    }
    @Test fun `duplicate retry while invocation is active does not create a second request`() = Fixture().use {f ->
        f.providerFailure=IllegalStateException("synthetic failure");val (id,_)=f.send();f.providerFailure=null
        var duplicate: NormalChatOpenRouterExecutor.Result?=null
        assertEquals(NormalChatOpenRouterExecutor.Result.Sent,f.executor.retryLatestAttempt(id,onLocalSubmission={duplicate=f.executor.retryLatestAttempt(id)}))
        assertTrue(duplicate is NormalChatOpenRouterExecutor.Result.Failed || duplicate is NormalChatOpenRouterExecutor.Result.Blocked)
        assertEquals(2,f.requestCount);assertEquals(ConversationRuntimeStatus.COMPLETED,f.conversations.stateFor(id)!!.status)
    }
    @Test fun `completed answer returns while automatic title provider is still blocked`() = Fixture(asynchronousCompletion=true).use { f ->
        val titleEntered = java.util.concurrent.CountDownLatch(1)
        val titleRelease = java.util.concurrent.CountDownLatch(1)
        f.titlePending = true
        f.titleRefine = { titleEntered.countDown(); titleRelease.await(); ConversationTitleRefinementResult.Title("公开问题分析结果") }
        val sender = java.util.concurrent.Executors.newSingleThreadExecutor()
        val sent = sender.submit<Pair<ConversationId, NormalChatOpenRouterExecutor.Result>> { f.send() }
        try {
            val entered = titleEntered.await(10, java.util.concurrent.TimeUnit.SECONDS)
            assertTrue("title not reached: result=${if(sent.isDone) runCatching {sent.get().second}.toString() else "still sending"}; runtime=${f.lastConversationId?.let { f.conversations.stateFor(it)?.status }}", entered)
            val id = f.lastConversationId!!
            assertEquals(ConversationRuntimeStatus.COMPLETED, f.conversations.stateFor(id)!!.status)
            assertEquals(NormalChatSendAttemptStatus.COMPLETED, f.attempts.findLatestForConversation(id)!!.status)
            assertEquals("completed answer must release send ownership before a slow title", NormalChatOpenRouterExecutor.Result.Sent, sent.get(1, java.util.concurrent.TimeUnit.SECONDS).second)
            assertEquals(1, f.requestCount)
            f.conversations.saveDraft(id, ConversationDraft(text="下一轮正在编辑的问题", updatedAt=f.clock.instant()))
        } finally {
            titleRelease.countDown()
            sender.shutdown(); assertTrue(sender.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS))
        }
        val id = f.lastConversationId!!
        f.awaitCompletion(id)
        val titled = f.conversations.findById(id)!!
        assertEquals("公开问题分析结果", titled.conversation.title)
        assertEquals(false, titled.conversation.autoTitlePending)
        assertEquals("下一轮正在编辑的问题", titled.draft.text)
    }

    @Test fun `unexpected failure seals physical call instead of leaving pending forever`() = Fixture().use {f ->
        f.providerFailure=IllegalStateException("synthetic failure");f.send()
        val record=f.durableAudit.listNewestFirst().single()
        assertEquals("LOCAL_EXECUTION_INTERRUPTED",record.status)
        assertNotNull(record.accounting!!.finishedAt)
        assertNull(record.inputTokens);assertNull(record.outputTokens);assertNull(record.cost.totalMicros)
        assertEquals(1,f.requestCount)
    }
    @Test fun `manual retry after exception completes original attempt with a separate physical call`() = Fixture().use {f ->
        f.providerFailure=IllegalStateException("synthetic failure");val (id,_)=f.send()
        val before=f.attempts.findLatestForConversation(id)!!
        assertEquals(1,f.requestCount)
        f.providerFailure=null
        assertEquals(NormalChatOpenRouterExecutor.Result.Sent,f.executor.retryLatestAttempt(id))
        val after=f.attempts.findLatestForConversation(id)!!
        assertEquals(before.attemptId,after.attemptId);assertEquals(before.idempotencyKey,after.idempotencyKey)
        assertEquals(NormalChatSendAttemptStatus.COMPLETED,after.status)
        assertEquals(ConversationRuntimeStatus.COMPLETED,f.conversations.stateFor(id)!!.status)
        assertEquals(2,f.requestCount);assertEquals(2,f.durableAudit.summary().recordCount)
    }
    @Test fun `exception after committed answer cannot revoke text usage or completion`() = Fixture().use {f ->
        f.completedCallbackFails=true;val (id,result)=f.send()
        assertEquals(NormalChatOpenRouterExecutor.Result.Sent,result)
        assertEquals(ConversationRuntimeStatus.COMPLETED,f.conversations.stateFor(id)!!.status)
        assertEquals(NormalChatSendAttemptStatus.COMPLETED,f.attempts.findLatestForConversation(id)!!.status)
        assertEquals(1,f.durableAudit.summary().recordCount)
        assertEquals(20L,f.durableAudit.listNewestFirst().single().outputTokens)
        assertEquals(1,f.requestCount)
    }
    @Test fun `coroutine cancellation remains cancellation and cleans local execution state`() = Fixture().use {f ->
        f.providerFailure=CancellationException("synthetic cancel")
        val thrown=runCatching {f.send()}.exceptionOrNull();assertTrue(thrown is CancellationException)
        assertTrue(f.credential!!.all {it=='\u0000'});assertEquals(1,f.requestCount)
        assertTrue(f.conversations.stateFor(f.lastConversationId!!)!!.isTerminal)
        assertEquals(NormalChatSendAttemptStatus.UNKNOWN,f.attempts.findLatestForConversation(f.lastConversationId!!)!!.status)
    }
    @Test fun `native and companion full attachment scopes keep each physical call and finish the original model`() {
        for(preset in listOf(ModelPresetId.RELAYCORE_SONNET_5_5,ModelPresetId.RELAYCORE_GPT_6_1_SOL)) Fixture(presetId=preset,webSearch=true).use {f ->
            f.attachmentText=(1..20).joinToString("\n") {"- 公开事项${it}：公开机构在2026年发布第${it}项数据。"}
            f.retrievalFactory={request ->
                val body=org.json.JSONObject(request.jsonBody)
                if(!body.has("tools")) ProviderChatOutcome.StreamedResponse(200,"完整回答",null,100,20,null,false,returnedModelId=f.model.modelId)
                else {
                    val text=body.toString();val ids=Regex("\\[(A[1-9][0-9]*)\\]").findAll(text.substringAfter("本次问题")).map {it.groupValues[1]}.distinct().toList()
                    ProviderChatOutcome.StreamedResponse(200,ids.joinToString("\n\n") {"[$it] 已核实\n公开机构在2026年发布该项数据。\n来源：https://example.org/$it"},null,100,20,null,false,
                        webSources=ids.map {ProviderWebSource("https://example.org/$it","公开来源")},webSearchPerformed=true,returnedModelId="openai/gpt-6-luna")
                }
            }
            val (id,result)=f.send(withAttachment=true)
            assertTrue("$preset:$result",result is NormalChatOpenRouterExecutor.Result.Sent || result is NormalChatOpenRouterExecutor.Result.SentWithNotice)
            assertEquals(6,f.requestCount);assertEquals(NormalChatSendAttemptStatus.COMPLETED,f.attempts.findLatestForConversation(id)!!.status)
            val answer=f.conversations.findById(id)!!.nodes.last {it.role==MessageRole.ASSISTANT}
            val calls=f.attribution.forMessages(listOf(answer.id)).getValue(answer.id).single().accountingCalls
            assertEquals(6,calls.size);assertEquals(6,calls.map {it.callId}.distinct().size);assertEquals(5,calls.count {it.role=="RETRIEVAL"});assertEquals(f.model.modelId,calls.single {it.role=="ANSWER"}.actualModelId)
        }
    }

}

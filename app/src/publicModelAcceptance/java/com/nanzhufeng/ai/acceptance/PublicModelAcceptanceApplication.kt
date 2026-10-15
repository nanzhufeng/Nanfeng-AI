package com.nanzhufeng.ai.acceptance

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.widget.TextView
import androidx.work.Configuration
import com.nanzhufeng.ai.app.AppContainer
import com.nanzhufeng.ai.ai.*
import com.nanzhufeng.ai.domain.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.ByteArrayOutputStream
import java.net.Socket
import java.time.Clock
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Explicit one-shot paid acceptance, independent application/data identity. No instrumented tests. */
class PublicModelAcceptanceApplication : Application(), Configuration.Provider {
    override val workManagerConfiguration get() = Configuration.Builder().build()
    override fun onCreate() {
        super.onCreate()
        check(packageName == "com.nanzhufeng.ai.publicmodelacceptance")
        val bootstrap = File(filesDir, "public-bootstrap.json")
        if (!bootstrap.isFile) return
        // Consume capability before constructing any owners. Restart never resends a paid task.
        val config = JSONObject(bootstrap.readText())
        check(bootstrap.delete())
        if(config.optString("runner")=="automatic-title-native") {
            Thread({TitleNativeAcceptanceOwner.run(this,config)},"automatic-title-native").start()
            return
        }
        if(config.optString("runner")=="reopen-existing") {
            Thread({
                var calls=0
                val container=AppContainer(this,normalChatTransport=object:ProviderChatTransport {
                    override fun execute(request:ProviderChatRequest,credential:CharArray):ProviderChatOutcome {
                        calls++;error("readback must never send")
                    }
                })
                val rows=JSONArray();val ids=config.getJSONArray("conversationIds")
                for(index in 0 until ids.length()) {
                    val id=ConversationId(ids.getString(index));val conversation=container.conversationRepository.findById(id)
                    val assistant=conversation?.nodes?.lastOrNull {it.role==MessageRole.ASSISTANT}
                    val attribution=assistant?.let {container.assistantResponseModelAttributions.forMessages(listOf(it.id))[it.id]?.lastOrNull()}
                    rows.put(JSONObject().apply {
                        put("conversationId",id.value);put("assistantPresent",assistant!=null)
                        put("publicAnswer",assistant?.content?.filterIsInstance<ContentBlock.Text>()?.joinToString("\n") {it.text}.orEmpty())
                        put("requestedModel",attribution?.modelId);put("webSearchUsed",attribution?.webSearchUsed)
                        put("footerCostLabel",attribution?.footerCostLabel())
                        put("accountingCalls",JSONArray(attribution?.accountingCalls.orEmpty().map {call->JSONObject().apply {
                            put("role",call.role);put("requested",call.requestedModelId);put("returned",call.actualModelId)
                            put("currency",call.cost.currencyCode);put("chargeMicros",call.cost.totalMicros)
                        }}))
                    })
                }
                File(filesDir,"public-reopen-${config.getString("runId")}.json").writeText(JSONObject().put("rows",rows).put("networkCalls",calls).toString(2))
            },"public-reopen-acceptance").start()
            return
        }
        if(config.optString("runner")=="luna-routing-ports") {
            Thread({com.nanzhufeng.ai.ai.LunaRoutingNativeOwner.run(this,config)},"luna-routing-native-acceptance").start()
            return
        }
        Thread({
            val rows = JSONArray()
            val evidence = File(filesDir, "public-results-${config.getString("runId")}.json")
            fun save() {val temporary=File(filesDir,evidence.name+".tmp");temporary.writeText(rows.toString(2));check(temporary.renameTo(evidence))}
            try {
                val physical=JSONArray()
                val traceFile=File(filesDir,"public-physical-${config.getString("runId")}.json")
                val actualTransport=OfficialProviderChatTransport()
                val container = AppContainer(this,normalChatTransport=object:ProviderChatTransport {
                    override fun execute(request:ProviderChatRequest,credential:CharArray):ProviderChatOutcome {
                        val started=System.nanoTime();var frames=0;var reasoningChars=0L;var firstFrameMillis:Long?=null
                        val terminalFrames=JSONArray()
                        val observed=request.copy(streamEventDecoder={data->
                            runCatching {val frame=JSONObject(data);val type=frame.optString("type")
                                if(type in setOf("response.completed","response.incomplete","response.failed","error")) {
                                    val response=frame.optJSONObject("response") ?: frame
                                    val error=response.optJSONObject("error") ?: frame.optJSONObject("error")
                                    terminalFrames.put(JSONObject().apply {
                                        put("type",type);put("status",response.optString("status").takeIf {it in setOf("completed","incomplete","failed","cancelled","in_progress")})
                                        fun safe(value:String)=value.takeIf {it.length<=100 && it.matches(Regex("[A-Za-z0-9_.-]+"))}
                                        put("errorCode",safe(error?.optString("code").orEmpty()));put("errorType",safe(error?.optString("type").orEmpty()))
                                        put("incompleteReason",safe(response.optJSONObject("incomplete_details")?.optString("reason").orEmpty()))
                                        put("errorMessageChars",error?.optString("message")?.length ?: 0)
                                        val message=error?.optString("message").orEmpty().lowercase();put("errorTerms",JSONArray(listOf("token","limit","context","search","quota","upstream","internal","reasoning","tool","timeout","unsupported").filter {it in message}))
                                        put("input",response.optJSONObject("usage")?.opt("input_tokens"));put("output",response.optJSONObject("usage")?.opt("output_tokens"))
                                    })
                                }
                            }
                            runCatching {val frame=JSONObject(data);val type=frame.optString("type");val block=frame.optJSONObject("content_block")
                                if(type=="content_block_start" && block?.optString("type")=="web_search_tool_result") {
                                    val content=block.opt("content");val items=content as? JSONArray
                                    terminalFrames.put(JSONObject().apply {put("type","search_result");put("resultKind",if(items!=null) "array" else if(content is JSONObject) "tool_error" else "unknown");put("items",items?.length() ?: 0)
                                        put("itemTypes",JSONArray((0 until (items?.length()?:0)).mapNotNull {items?.optJSONObject(it)?.optString("type")?.takeIf {value->value.length<=100 && value.matches(Regex("[A-Za-z0-9_.-]+"))}}.distinct()))
                                        put("itemErrorCodes",JSONArray((0 until (items?.length()?:0)).mapNotNull {items?.optJSONObject(it)?.optString("error_code")?.takeIf {value->value.length<=100 && value.matches(Regex("[A-Za-z0-9_.-]+"))}}.distinct()))
                                        put("errorCode",(content as? JSONObject)?.optString("error_code")?.takeIf {it.length<=100 && it.matches(Regex("[A-Za-z0-9_.-]+"))})
                                    })
                                }
                                if(type=="message_delta" || type=="message_stop") terminalFrames.put(JSONObject().apply {put("type",type);put("stopReason",frame.optJSONObject("delta")?.optString("stop_reason")?.takeIf {it in setOf("end_turn","pause_turn","max_tokens","tool_use","stop_sequence","refusal")})})
                            }
                            request.streamEventDecoder(data)?.also {event->frames++;reasoningChars+=event.reasoning?.length ?: 0;if(firstFrameMillis==null) firstFrameMillis=(System.nanoTime()-started)/1_000_000}
                        })
                        val outbound=if(config.has("outputLimit")) {
                            check(config.optString("fixture")=="rc-text" && (request.body==null || request.body is ProviderChatRequestBody.Utf8Json))
                            val cap=config.getInt("outputLimit");check(cap in 32..2048)
                            val limited=JSONObject(request.jsonBody);check(limited.optString("model") in setOf("meta/muse-wonder","google/gemini-3.8-flash"))
                            limited.put(if(request.endpoint.endsWith("/responses")) "max_output_tokens" else "max_tokens",cap)
                            observed.copy(jsonBody=limited.toString(),body=null)
                        } else observed
                        val outcome=actualTransport.execute(outbound,credential)
                        val wire=if(outbound.body!=null) {
                            val bytes=ByteArrayOutputStream();outbound.effectiveBody().writeTo(bytes)
                            JSONObject(bytes.toString(Charsets.UTF_8.name()))
                        } else JSONObject(outbound.jsonBody)
                        val model=wire.optString("model","unknown")
                        physical.put(JSONObject().apply {
                            put("terminalFrames",terminalFrames);put("requestedModel",model);put("outcome",outcome.javaClass.simpleName)
                            put("validFrames",frames);put("reasoningChars",reasoningChars);put("firstFrameMillis",firstFrameMillis)
                            put("readTimeoutMillis",request.readTimeoutMillis);put("maxStreamDurationMillis",request.maxStreamDurationMillis);put("streamTextMode",request.streamTextMode.name)
                            put("endpointPath",java.net.URI(request.endpoint).path)
                            put("outputBudget",wire.optLong("max_output_tokens",wire.optLong("max_completion_tokens",wire.optLong("max_tokens",-1))))
                            put("reasoningEffort",wire.optString("reasoning_effort").takeIf {it in setOf("max","high","medium","low")})
                            if(outcome is ProviderChatOutcome.HttpResponse) {
                                put("httpStatus",outcome.statusCode)
                                if(outcome.statusCode>=400) put("responseErrorHints",JSONArray(listOf("gateway","timeout","cloudflare","nginx").filter {outcome.responseBody.lowercase(java.util.Locale.ROOT).contains(it)}))
                                runCatching {JSONObject(outcome.responseBody)}.getOrNull()?.let {body ->
                                    put("responseKnownFields",JSONArray(listOf("choices","usage","error","model","output","candidates","type","status").filter {body.has(it)}))
                                    put("hasErrorObject",body.optJSONObject("error")!=null)
                                    body.optJSONObject("error")?.let {error ->
                                        put("providerErrorCode",error.opt("code")?.toString()?.takeIf {it.length<=64 && it.all {c->c.isLetterOrDigit() || c in "_-."}})
                                        val errorWords=(error.optString("message")+" "+error.optJSONObject("metadata")?.optString("raw").orEmpty()).lowercase(java.util.Locale.ROOT)
                                        if(errorWords.contains("18+") && errorWords.contains("age confirmation")) put("providerAccessRequirement","AGE_CONFIRMATION_18_PLUS")
                                        put("providerErrorWordFlags",JSONArray(listOf("model","provider","access","permission","denied","region","country","location","restricted","blocked","credits","insufficient","quota","limit","byok","account","plan","enabled","available","gateway","timeout","privacy","settings","data","policy","terms","disabled","not allowed","not found","invalid","forbidden","train","training","paid","beta","rate","organization","guardrail").filter {errorWords.contains(it)}))
                                    }
                                    val choice=body.optJSONArray("choices")?.optJSONObject(0);val message=choice?.optJSONObject("message")
                                    if(request.endpoint.startsWith("https://openrouter.ai/")) {
                                        val decoded=OpenRouterChatAdapter().decodeNonStreaming(outcome.responseBody)
                                        val reply=(decoded as? ChatAdapterDecodedResult.Text) ?: (decoded as? ChatAdapterDecodedResult.Incomplete)?.partial
                                        if(reply!=null) {
                                            put("decodedNativeSourceCount",reply.webSources.size);put("decodedSearchPerformed",reply.webSearchPerformed)
                                            put("reportedSearchRequestCount",body.optJSONObject("usage")?.optJSONObject("server_tool_use")?.opt("web_search_requests"))
                                            val ids=Regex("\\[?(A[0-9]+)\\]?").findAll(reply.text).map {it.groupValues[1]}.distinct().take(48).toList();put("replyScopeIds",JSONArray(ids))
                                            put("nativeURLsPresentInReply",reply.webSources.count {reply.text.contains(it.url)})
                                            if(config.optString("fixture")=="user-report") {
                                                val plan=AttachmentWebSearchPlan.build(File(filesDir,"actual-report.md").readText())
                                                val batch=plan.items.filter {request.jsonBody.contains("[${it.id}]")}
                                                if(batch.isNotEmpty()) {
                                                    put("requestedScopeIds",JSONArray(batch.map {it.id}))
                                                    put("scopeEvidenceAccepted",AttachmentWebSearchPlan.evidence(AttachmentWebSearchPlan.Plan(batch),reply.text,reply.webSources.map {it.url},allowUnverified=batch.size==1 && (reply.webSearchPerformed || reply.webSources.isNotEmpty()))!=null)
                                                }
                                            }
                                        }
                                    }
                                    put("choiceFinishReason",choice?.optString("finish_reason")?.takeIf {it in setOf("stop","length","max_tokens","content_filter","end_turn")})
                                    put("contentChars",message?.optString("content")?.length ?: 0);put("thinkingChars",message?.optString("reasoning_content")?.length ?: 0)
                                }
                            }
                            put("wire",JSONObject().apply {put("effort",wire.optJSONObject("reasoning")?.opt("effort") ?: wire.opt("reasoning_effort"));put("maxTokens",wire.opt("max_tokens"));put("searchEngine",wire.optJSONArray("tools")?.optJSONObject(0)?.optJSONObject("web_search")?.opt("search_engine"));put("toolsPresent",wire.has("tools"));put("toolsCount",wire.optJSONArray("tools")?.length()?:0)
                                put("tools",JSONArray().apply {val tools=wire.optJSONArray("tools");for(i in 0 until (tools?.length()?:0)){val t=tools!!.optJSONObject(i)?:continue;put(JSONObject().apply {put("type",t.optString("type"));put("keys",JSONArray(t.keys().asSequence().toList()));put("externalWebAccess",t.opt("external_web_access"));put("searchContextSize",t.opt("search_context_size"))})}})
                                val choice=wire.opt("tool_choice");put("choiceKind",if(choice is JSONObject) "object" else if(choice is String) "string" else "absent");if(choice is String)put("choiceValue",choice);if(choice is JSONObject){put("choiceType",choice.opt("type"));put("choiceMode",choice.opt("mode"))}
                                put("input",JSONArray().apply {val input=wire.optJSONArray("input");for(i in 0 until (input?.length()?:0)){val item=input!!.optJSONObject(i)?:continue;put(JSONObject().apply {put("role",item.opt("role"));put("type",item.opt("type"));put("partTypes",JSONArray().apply {val parts=item.optJSONArray("content");for(j in 0 until (parts?.length()?:0))put(parts!!.optJSONObject(j)?.opt("type"))})})}})
                                put("searchPlugins",wire.optJSONArray("plugins"));put("routingKeyPresent",wire.has("prompt_cache_key"));wire.optString("prompt_cache_key").takeIf {it.isNotEmpty()}?.let {key->put("routingKeyFingerprint",java.security.MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString(""){"%02x".format(it.toInt() and 255)})};put("maxOutputTokens",wire.opt("max_output_tokens"));put("stream",wire.opt("stream"));put("store",wire.opt("store"))})
                            if(outcome is ProviderChatOutcome.StreamedResponse) {
                                put("httpStatus",outcome.statusCode);put("finish",outcome.finishReason);put("returnedModel",outcome.returnedModelId)
                                put("nativeSources",JSONArray(outcome.webSources.map {it.url}));put("webSearchPerformed",outcome.webSearchPerformed)
                                put("publicFixtureReply",outcome.text);put("input",outcome.inputTokens);put("output",outcome.outputTokens)
                            }
                            if(outcome is ProviderChatOutcome.NetworkFailure) {put("kind",outcome.kind.name);put("exceptionClass",outcome.exceptionClass)}
                        })
                        val temp=File(filesDir,traceFile.name+".tmp");temp.writeText(physical.toString(2));check(temp.renameTo(traceFile))
                        return outcome
                    }
                })
                val keys = receive(config)
                for (provider in ProviderId.entries.mapNotNull(NanfengModelServiceCatalog::provider)) {
                    val key = keys.optString(provider.id.name)
                    if (key.isEmpty()) continue
                    check(container.saveModelServiceConfiguration.execute(provider.id, true,
                        NanfengModelServiceCatalog.defaultPreset(provider.id), key) is SaveModelServiceConfigurationResult.Saved)
                    keys.remove(provider.id.name)
                }
                check(keys.length() == 0)
                container.saveAssistantExperienceSettings.execute(AssistantExperienceSettings(
                    webSearchEnabled=config.optString("fixture")!="rc-usage", librarySearchEnabled=false, memoryRetrievalEnabled=false))
                val count=config.optInt("claimCountPerFile",2).also {check(it in 1..8)}
                val fresh=config.optString("fixture")=="go-firefox"
                val python=if(fresh) listOf("1.24.0","1.23.0").take(count).joinToString("\n") {"- Go $it 正式发布，核验go.dev官方发布日期。"} else listOf("3.14.0","3.13.0","3.12.0","3.11.0","3.10.0","3.9.0","3.8.0","3.7.0").take(count).joinToString("\n") {"- Python $it 正式发布，核验python.org官方发布日期。"}
                val rust=if(fresh) listOf("128.0","127.0").take(count).map {"- Firefox $it 正式发布，核验mozilla.org官方发布日期。"} else listOf("1.90.0","1.89.0","1.88.0","1.87.0","1.86.0","1.85.0","1.84.0","1.83.0").take(count).map {"- Rust $it 正式发布，核验blog.rust-lang.org官方发布日期。"}
                val fixture=config.optString("fixture")
                val suites=when(fixture) {
                    "public-topic-mixed" -> listOf(listOf(
                        import(container,"image/png","public-finance.png",File(filesDir,"public-finance.png").readBytes()),
                        import(container,"text/markdown","public-art.md",File(filesDir,"public-art.md").readBytes()),
                    ))
                    "haiku-image" -> {
                        check(config.optString("filter")=="CLAUDE_HAIKU_5_5")
                        listOf(listOf(import(container,"image/png","public-sample-order.png",assets.open("public-search-fixtures/public-sample-order.png").use {it.readBytes()})))
                    }
                    "rc-text", "rc-usage" -> listOf(emptyList())
                    "user-report" -> listOf(listOf(import(container,"text/markdown","20261011-OCR.md",File(filesDir,"actual-report.md").readBytes())))
                    "formats" -> {
                        val manifest=JSONObject(assets.open("public-search-fixtures/manifest.json").bufferedReader().use {it.readText()})
                        val files=manifest.getJSONArray("fixtures");val groups=manifest.getJSONArray("groups")
                        (0 until groups.length()).map {g -> val group=groups.getJSONArray(g)
                            (0 until group.length()).map {j -> val f=files.getJSONObject(group.getInt(j));val name=f.getString("file")
                                import(container,f.getString("mime"),name,assets.open("public-search-fixtures/$name").use {it.readBytes()}) }
                        }
                    }
                    "chinese" -> listOf(listOf("chinese-first.md","chinese-tail.md").map {name->import(container,"text/markdown",name,assets.open("public-search-fixtures/$name").use {it.readBytes()})})
                    else -> listOf(listOf(import(container,"text/markdown","公开Python发布报告.md",python.toByteArray()),import(container,"application/vnd.openxmlformats-officedocument.wordprocessingml.document","公开Rust发布报告.docx",docx(rust))))
                }
                val filter = config.optString("filter").split(',').filter(String::isNotBlank).toSet()
                if(fixture=="rc-usage") check(filter.isNotEmpty() && filter.all {it in setOf("RELAYCORE_GEMINI_3_1_PRO","RELAYCORE_GEMINI_3_8_FLASH")})
                val catalogCases=ComposerModelRoutingCatalog.choices.filter {it.routes.size==1 && it.id!=ComposerModelRoutingCatalog.auto.id}.map {it to it.routes.single()} + (ComposerModelRoutingCatalog.auto to ModelPresetId.QWEN_3_6_FLASH)
                check(catalogCases.map {it.second}.toSet()==NanfengModelServiceCatalog.chatPresets.map {it.id}.toSet())
                val cases=if(fixture in setOf("formats","chinese")) catalogCases.filter {it.second.name==config.optString("formatPreset","RELAYCORE_SONNET_5_5")} else catalogCases
                for ((group,references) in suites.withIndex()) for ((choice,preset) in cases) {
                    val groupFilter=config.optString("groupFilter").split(',').mapNotNull(String::toIntOrNull)
                    if(groupFilter.isNotEmpty() && group !in groupFilter) continue
                    if (filter.isNotEmpty() && preset.name !in filter) continue
                    val snapshot = container.conversationRepository.save(ConversationTreeService(Clock.systemUTC()).create("公开双文件-${preset.name}"))
                    val id = snapshot.conversation.id
                    val draft = container.conversationRepository.saveDraft(id, ConversationDraft(
                        text=if(fixture=="haiku-image") "分析这张订单图，用100字以内回答。" else if(fixture=="public-topic-mixed") "分析这两份附件，给出简洁结论和相关来源。" else if(fixture=="rc-usage") "只回答算式结果：1+1。不要解释。" else if(fixture=="user-report") config.optString("requestText","分析报告") else if(fixture=="rc-text") config.optString("requestText","请实际联网核验 Go 1.23.0 与 Firefox 138.0 的官方发布日期，中文简短列出两项日期与官方来源，并比较两者发布节奏。") else "分析本次全部公开附件，所有事项都实际联网核验，中文简短回答并引用来源。",
                        attachments=references, updatedAt=java.time.Instant.now()))
                    val executor = container.normalChatOpenRouterExecutor
                    val routing=executor.routingSnapshot().copy(webSearchEnabled=fixture!="rc-usage").let { if(preset==ModelPresetId.QWEN_3_6_FLASH) it.copy(usablePresets=setOf(preset)) else it }
                    val approval = NormalChatEgressAuthorization.forUserSend(draft, System.currentTimeMillis(), choice.id,routing)
                    check(approval.recipientPresets==listOf(preset))
                    val started = System.currentTimeMillis()
                    val result = executor.execute(id, approval)
                    val after = container.conversationRepository.findById(id)!!
                    val assistant = after.nodes.lastOrNull { it.role == MessageRole.ASSISTANT }
                    val context = container.contextSelectionAudits.recent(100).filter { it.conversationId == id.value }
                    val coverage = context.flatMap { it.selectedSources }.firstOrNull { it.kind=="联网核验范围" }?.title
                    val attribution = assistant?.let { container.assistantResponseModelAttributions.forMessages(listOf(it.id))[it.id]?.lastOrNull() }
                    rows.put(JSONObject().apply {
                        put("preset",preset.name);put("result",result.toString());put("conversationId",id.value)
                        put("elapsedMs",System.currentTimeMillis()-started);put("coverage",coverage)
                        put("attachmentCount",references.size);put("mimes",JSONArray(references.map { it.mimeType }))
                        put("fixture",fixture);put("group",group);put("fileCount",references.size);put("assistantPresent",assistant!=null);put("webSearchUsed",attribution?.webSearchUsed)
                        put("diagnostics",JSONArray(container.providerDiagnostics.recent(100).filter {it.conversationId==id.value}.map {record -> JSONObject().apply {
                            put("errorClass",record.errorClass.name);put("httpStatus",record.httpStatus);put("safeDetail",record.redactedBody);put("model",record.apiModelId);put("elapsedMs",record.latencyMs)
                        }}))
                        put("requestedModel",attribution?.modelId)
                        put("physicalCalls",JSONArray(attribution?.accountingCalls.orEmpty().map { call -> JSONObject().apply {
                            put("role",call.role);put("status",call.status);put("requested",call.requestedModelId);put("returned",call.actualModelId)
                            put("serviceId",call.serviceId);put("serviceEngineId",call.serviceEngineId)
                            put("input",call.usage.inputTokens);put("output",call.usage.outputTokens)
                        }}))
                        val text=assistant?.content?.filterIsInstance<ContentBlock.Text>()?.joinToString("\n") {it.text}.orEmpty()
                        put("answerChars",text.length);put("publicAnswer",text);put("footerCostLabel",attribution?.footerCostLabel());put("costSource",attribution?.costSource?.name)
                    }); save()
                }
            } catch (error: Throwable) {
                rows.put(JSONObject().put("bootstrapError",true).put("errorClass",error.javaClass.simpleName));save()
            } finally {
                // Only this disposable package's encrypted copies are removed, never the credential owner.
                check(getSharedPreferences("provider_credentials_v2", MODE_PRIVATE).edit().clear().commit())
                File(filesDir,"public-cleanup-${config.getString("runId")}.json").writeText("{\"encryptedCredentialsCleared\":true,\"capabilityConsumed\":true}")
            }
        }, "public-model-acceptance").start()
    }
    private fun receive(config: JSONObject): JSONObject = Socket("127.0.0.1",config.getInt("port")).use { socket ->
        socket.soTimeout=30000
        socket.getOutputStream().write((config.getString("token")+"\n").toByteArray())
        val bytes=socket.getInputStream().readBytes()
        try { JSONObject(String(bytes,Charsets.UTF_8)) } finally { bytes.fill(0) }
    }
    private fun import(container:AppContainer,mime:String,name:String,bytes:ByteArray):ConversationAttachmentReference {
        val imported=container.privateAttachmentStore.import(AttachmentImportRequest(bytes.inputStream(),mime,name)) as AttachmentImportResult.Imported
        return container.privateAttachmentRepository.save(imported.attachment).toConversationReference()
    }
    private fun docx(lines:List<String>):ByteArray {
        val bytes=ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            val xml="<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>"+lines.joinToString("") { "<w:p><w:r><w:t>$it</w:t></w:r></w:p>" }+"</w:body></w:document>"
            for ((name,text) in mapOf("[Content_Types].xml" to "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"xml\" ContentType=\"application/xml\"/></Types>","word/document.xml" to xml)) {
                zip.putNextEntry(ZipEntry(name));zip.write(text.toByteArray());zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }
}
class PublicModelAcceptanceActivity:Activity() {
    override fun onCreate(state:Bundle?) {super.onCreate(state);setContentView(TextView(this).apply {text="独立公开双文件联网验收\n结果写入本包私有目录，不自动重试。"})}
}

package com.nanzhufeng.ai.ai

/** One complete task, not a prefix sample. HTTP/SSE and recipient rules remain in adapters. */
internal object AttachmentWebSearchCoordinator {
    const val BATCH_SIZE = 4
    fun batchSize(route:OfficialWebSearchRoute)=when(route) {
        OfficialWebSearchRoute.ZHIPU_CHAT_COMPLETIONS->1
        else->BATCH_SIZE
    }
    const val MAX_CALLS = 48
    const val MAX_DURATION_MILLIS = 600_000L
    fun run(plan:AttachmentWebSearchPlan.Plan, cancelled:()->Boolean, execute:(AttachmentWebSearchPlan.Plan,Int)->RelayCoreWebSearchCompanion.Result,
            elapsedMillis:()->Long = run {val start=System.nanoTime(); { (System.nanoTime()-start)/1_000_000 } },cache:AttachmentSearchCheckpointCache=AttachmentSearchCheckpointCache.None,batchSize:Int=BATCH_SIZE):RelayCoreWebSearchCompanion.Result {
        val parts=linkedMapOf<String,String>();val sources=mutableListOf<ProviderWebSource>();var calls=0;var stop:String?=null
        val failedItems=mutableSetOf<String>()
        var template:ChatAdapterDecodedResult.Text?=null;var label=RelayCoreWebSearchCompanion.label
        fun invoke(items:List<AttachmentWebSearchPlan.Item>):RelayCoreWebSearchCompanion.Result {
            if(cancelled()) return RelayCoreWebSearchCompanion.Result.Failed("USER_CANCELLED")
            if(calls>=MAX_CALLS || elapsedMillis()>=MAX_DURATION_MILLIS) return RelayCoreWebSearchCompanion.Result.Failed("WEB_SEARCH_BUDGET_EXHAUSTED")
            return execute(AttachmentWebSearchPlan.Plan(items),++calls)
        }
        fun retain(result:RelayCoreWebSearchCompanion.Result.Ready,items:List<AttachmentWebSearchPlan.Item>) {
            template=result.reply;label=result.sourceLabel;sources+=result.reply.webSources
            items.forEach {item ->
                result.itemEvidence[item.id]?.let {part ->
                    parts[item.id]=part;failedItems.remove(item.id)
                    if(items.size==1 || !part.contains("证据不足，仍未核实")) cache.save(item,part,result)
                }
            }
        }

        try {
        var reused=0
        for(item in plan.items) cache.load(item)?.let {saved ->
            template=saved.reply;label=saved.sourceLabel;sources+=saved.reply.webSources;parts[item.id]=saved.reply.text;reused++
        }
        for(batch in plan.items.filter {it.id !in parts}.chunked(batchSize)) {
            val result=invoke(batch)
            if(result is RelayCoreWebSearchCompanion.Result.Failed && result.code=="USER_CANCELLED") return result
            if(result is RelayCoreWebSearchCompanion.Result.Ready) retain(result,batch)
            else if(result is RelayCoreWebSearchCompanion.Result.Failed && result.code!="WEB_SEARCH_BUDGET_EXHAUSTED") failedItems+=batch.map {it.id}
            val retry=when(result) {
                is RelayCoreWebSearchCompanion.Result.Ready -> if(batch.size>1) batch.filter {parts[it.id]?.contains("证据不足，仍未核实")==true} else emptyList()
                is RelayCoreWebSearchCompanion.Result.Failed -> if(!result.unknown && result.code in setOf("WEB_SEARCH_SCOPE_MISMATCH","WEB_SEARCH_NO_SOURCES","RESPONSE_FORMAT")) batch else emptyList()
            }
            if(result is RelayCoreWebSearchCompanion.Result.Failed && retry.isEmpty()) {if(!result.unknown && result.code in setOf("WEB_SEARCH_SCOPE_MISMATCH","WEB_SEARCH_NO_SOURCES","RESPONSE_FORMAT")) continue else {stop=result.code;break}}
            // A known completed but missing/insufficient result gets one targeted query, never an UNKNOWN resend.
            for(item in retry) {
                val targeted=invoke(listOf(item))
                if(targeted is RelayCoreWebSearchCompanion.Result.Ready) retain(targeted,listOf(item))
                else {
                    val failed=targeted as RelayCoreWebSearchCompanion.Result.Failed
                    if(failed.code=="USER_CANCELLED") return failed
                    // A failed supplement cannot erase a prior, actually searched
                    // unverified result. UNKNOWN still stops the task below.
                    if(item.id !in parts && failed.code!="WEB_SEARCH_BUDGET_EXHAUSTED") failedItems+=item.id
                    if(failed.unknown || failed.code !in setOf("WEB_SEARCH_SCOPE_MISMATCH","WEB_SEARCH_NO_SOURCES","RESPONSE_FORMAT")) {stop=failed.code;break}
                }
            }
            if(stop!=null) break
        }
        if(cancelled()) return RelayCoreWebSearchCompanion.Result.Failed("USER_CANCELLED")
        val complete=!plan.limited && parts.size==plan.items.size && stop==null
        val body=buildString {
            append("逐项检索结果：\n")
            if(reused>0) append("其中${reused}项复用了本任务10分钟内已完成查询的证据，并未再次发送这些查询；不称为本次新搜索。\n")
            plan.items.forEach {append(parts[it.id] ?: "${it.id} ${it.query}\n未完成查询：${if(it.id in failedItems) "查询失败或结果未确认，没有取得完整检索证据。" else "尚未发起该项查询。"}").append("\n\n")}
            if(!complete) append("本轮核验未完成：仍有未处理或被材料长度限制的事项。原因：${if(stop=="WEB_SEARCH_BUDGET_EXHAUSTED") "达到本轮检索时限或次数上限" else "服务未返回完整的逐项检索结果"}。不得把未完成查询称为搜过没找到。")
        }
        val reply=template ?: return RelayCoreWebSearchCompanion.Result.Failed(stop ?: "WEB_SEARCH_SCOPE_MISMATCH")
        return RelayCoreWebSearchCompanion.Result.Ready(reply.copy(text=body,inputTokens=null,outputTokens=null,
            webSources=sources.distinctBy {it.url}.filter {body.contains(it.url)},webSearchPerformed=true),complete,plan.items.size,parts.size,label)
        } catch(cancelled:java.util.concurrent.CancellationException) {throw cancelled} catch(_:Exception) {return RelayCoreWebSearchCompanion.Result.Failed("LOCAL_ATTEMPT_PERSISTENCE")}
    }
}

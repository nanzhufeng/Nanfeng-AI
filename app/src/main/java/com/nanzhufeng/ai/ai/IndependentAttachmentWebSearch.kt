package com.nanzhufeng.ai.ai

import com.nanzhufeng.ai.domain.*
import java.time.Clock

/** Shared attachment plan, explicit service authorization, separate physical-call accounting. */
internal object IndependentAttachmentWebSearch {
    fun run(plan:AttachmentWebSearchPlan.Plan,grant:IndependentSearchServiceGrant,fingerprint:String,
        client:IndependentWebSearchClient,readCredential:()->CharArray,cancellation:ProviderChatCancellation?,
        clock:Clock,onAccepted:()->Unit,onCall:(String,Boolean,String)->Unit,onCoverage:(Int,Int)->Unit,cache:AttemptIndependentSearchCache?=null):RelayCoreWebSearchCompanion.Result {
        if(!grant.allows(fingerprint)) return RelayCoreWebSearchCompanion.Result.Failed("EGRESS_AUTHORIZATION_REQUIRED")
        val parts=linkedMapOf<String,String>();val sources=mutableListOf<ProviderWebSource>();var calls=0
        val queries=plan.items.associate {it.id to IndependentWebSearchClient.queries(it.query)}
        if(queries.values.any {it==null}) return RelayCoreWebSearchCompanion.Result.Failed("SEARCH_QUERY_UNPLANNABLE")
        if(queries.values.sumOf {requireNotNull(it).size}>AttachmentWebSearchCoordinator.MAX_CALLS) return RelayCoreWebSearchCompanion.Result.Failed("WEB_SEARCH_BUDGET_EXHAUSTED")
        val started=clock.millis();var stop:String?=null
        for(item in plan.items) {
            if(cancellation?.isCancelled()==true) return RelayCoreWebSearchCompanion.Result.Failed("USER_CANCELLED")
            val cached=try {cache?.load(item)} catch(_:Exception) {return RelayCoreWebSearchCompanion.Result.Failed("LOCAL_ATTEMPT_PERSISTENCE")}
            if(cached!=null) {
                parts[item.id]=cached.evidenceText;sources+=cached.sources.map {ProviderWebSource(it.url,it.title)}
                try {onCoverage(plan.items.size,parts.size)} catch(_:Exception) {return RelayCoreWebSearchCompanion.Result.Failed("LOCAL_ATTEMPT_PERSISTENCE")}
                continue
            }
            val itemCalls=mutableListOf<String>()
            val itemSources=mutableListOf<IndependentWebSearchClient.Source>()
            var itemComplete=true
            for(query in requireNotNull(queries[item.id])) {
                if(calls>=AttachmentWebSearchCoordinator.MAX_CALLS || clock.millis()-started>=AttachmentWebSearchCoordinator.MAX_DURATION_MILLIS) {stop="WEB_SEARCH_BUDGET_EXHAUSTED";itemComplete=false;break}
                val callId="service-${java.util.UUID.randomUUID()}"
                try {onCall(callId,false,"RELAY_WEB_RETRIEVAL_PENDING")} catch(_:Exception) {return RelayCoreWebSearchCompanion.Result.Failed("LOCAL_ATTEMPT_PERSISTENCE")}
                calls++
                val result=client.search(query,callId,grant,fingerprint,readCredential,cancellation,onAccepted)
                val status=when(result) {is IndependentWebSearchClient.Result.Completed->"RELAY_WEB_RETRIEVAL_SUCCEEDED";is IndependentWebSearchClient.Result.Failed->"RELAY_WEB_RETRIEVAL_${if(result.unknown) "UNKNOWN_" else ""}${result.code}"}
                try {onCall(callId,true,status)} catch(_:Exception) {return RelayCoreWebSearchCompanion.Result.Failed("LOCAL_ATTEMPT_PERSISTENCE")}
                when(result) {
                    is IndependentWebSearchClient.Result.Completed -> {itemSources+=result.sources;itemCalls+=result.callId}
                    is IndependentWebSearchClient.Result.Failed -> {if(result.code=="USER_CANCELLED") return RelayCoreWebSearchCompanion.Result.Failed("USER_CANCELLED");stop=result.code;itemComplete=false;break}
                }
            }
            if(itemComplete) {
                val chosen=itemSources.distinctBy {it.url}.take(3)
                parts[item.id]=buildString {
                    append("${item.id} ${item.query}\n材料定位：附件${item.fileOrdinal}，事项${item.ordinalWithinFile}。\n查询已执行；以下原生来源尚需判断能否支持该事项，不能仅据检索完成标为已核实。\n")
                    if(chosen.isEmpty()) append("查询完成但没有可用来源：未核实。\n")
                    chosen.forEach {s-> append("来源：${s.url}\n原生摘要：${excerpt(s.content,item.query,500/chosen.size.coerceAtLeast(1))}\n")}
                }
                val itemWebSources=chosen.map {ProviderWebSource(it.url,it.title)}
                try {cache?.save(item,requireNotNull(parts[item.id]),itemWebSources,itemCalls)} catch(_:Exception) {return RelayCoreWebSearchCompanion.Result.Failed("LOCAL_ATTEMPT_PERSISTENCE")}
                sources+=itemWebSources
                try {onCoverage(plan.items.size,parts.size)} catch(_:Exception) {return RelayCoreWebSearchCompanion.Result.Failed("LOCAL_ATTEMPT_PERSISTENCE")}
            }
            if(stop!=null) break
        }
        if(cancellation?.isCancelled()==true) return RelayCoreWebSearchCompanion.Result.Failed("USER_CANCELLED")
        val complete=!plan.limited && parts.size==plan.items.size && stop==null
        val body=plan.items.joinToString("\n\n") {parts[it.id] ?: "${it.id} ${it.query}\n查询未完成：${stop ?: "材料处理受限"}，未核实。"}
        val reply=ChatAdapterDecodedResult.Text(body,null,null,webSources=sources.distinctBy {it.url},webSearchPerformed=parts.isNotEmpty(),returnedModelId=null)
        return RelayCoreWebSearchCompanion.Result.Ready(reply,complete,plan.items.size,parts.size,IndependentSearchServiceGrant.LABEL,itemEvidence=parts)
    }
    internal fun excerpt(content:String,query:String,limit:Int):String {
        val points=content.codePoints().toArray();val text=String(points,0,points.size)
        val tokens=Regex("[A-Za-z][A-Za-z0-9._-]{2,}|[0-9]+(?:[年月日./-][0-9]+)*|[\\p{IsHan}]{2,12}").findAll(query).map {it.value}.distinct().take(12).toList()
        val locations=tokens.mapNotNull {token -> text.indexOf(token,ignoreCase=true).takeIf {it>=0}?.let {text.codePointCount(0,it)}}.distinct().sorted()
        val snippets=mutableListOf<String>();var end=0
        for(index in locations) {
            val start=(index-40).coerceAtLeast(0);if(start<end && snippets.isNotEmpty()) continue
            end=(index+100).coerceAtMost(points.size);snippets+=String(points,start,end-start)
        }
        val value=if(snippets.isEmpty()) text else snippets.joinToString(" … ")
        return value.substring(0,value.offsetByCodePoints(0,minOf(limit,value.codePointCount(0,value.length))))
    }
}

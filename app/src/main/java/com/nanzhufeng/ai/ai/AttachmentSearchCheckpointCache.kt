package com.nanzhufeng.ai.ai

import com.nanzhufeng.ai.domain.*
import java.time.Clock
import java.security.MessageDigest

internal interface AttachmentSearchCheckpointCache {
    fun load(item:AttachmentWebSearchPlan.Item):RelayCoreWebSearchCompanion.Result.Ready?
    fun save(item:AttachmentWebSearchPlan.Item,evidence:String,result:RelayCoreWebSearchCompanion.Result.Ready)
    object None:AttachmentSearchCheckpointCache {
        override fun load(item:AttachmentWebSearchPlan.Item)=null
        override fun save(item:AttachmentWebSearchPlan.Item,evidence:String,result:RelayCoreWebSearchCompanion.Result.Ready) {}
    }
}
internal class AttemptAttachmentSearchCache(private val store:NormalChatSendAttemptStore,private val attempt:NormalChatSendAttemptId,
    private val fingerprint:String,private val model:ResolvedModel,private val clock:Clock,private val knownCalls:Map<String,ChatCallAccounting>):AttachmentSearchCheckpointCache {
    override fun load(item:AttachmentWebSearchPlan.Item):RelayCoreWebSearchCompanion.Result.Ready? {
        if(Regex("当前|最新|实时|今日|今天|股价|汇率|收益率|天气|气温|价格").containsMatchIn(item.query)) return null
        val row=store.loadSearchCheckpoint(attempt,fingerprint,item.id) ?: return null
        val age=clock.millis()-row.createdAt.toEpochMilli()
        val call=knownCalls[row.originCallId] ?: return null
        if(age !in 0..600_000 || !RelayCoreModels.sameApiModel(model.providerId,model.modelId,row.actualModelId) || call.finishedAt==null || call.role!="RETRIEVAL" || call.providerId!=model.providerId || !RelayCoreModels.sameApiModel(model.providerId,call.requestedModelId,model.modelId) || !call.status.endsWith("SUCCEEDED") || !row.evidenceText.startsWith("${item.id} ${item.query}\n")) return null
        val sources=row.sources.mapNotNull {source ->AttachmentWebSearchPlan.publicUrl(source.url)?.let {ProviderWebSource(it,source.title)}}
        if(sources.size!=row.sources.size) return null
        return RelayCoreWebSearchCompanion.Result.Ready(ChatAdapterDecodedResult.Text(row.evidenceText,null,null,webSources=sources,webSearchPerformed=true,returnedModelId=row.actualModelId),sourceLabel=row.sourceLabel,originCallId=row.originCallId,originAt=row.createdAt,itemEvidence=mapOf(item.id to row.evidenceText))
    }
    override fun save(item:AttachmentWebSearchPlan.Item,evidence:String,result:RelayCoreWebSearchCompanion.Result.Ready) {
        val id=result.reply.returnedModelId ?: return
        val call=result.originCallId ?: return
        if(!RelayCoreModels.sameApiModel(model.providerId,model.modelId,id)) return
        store.saveSearchCheckpoint(attempt,NormalChatSearchCheckpoint(fingerprint,item.id,evidence,result.reply.webSources.filter {evidence.contains(it.url)}.map {NormalChatSearchSource(it.url,it.title)},id,result.sourceLabel,result.originAt ?: clock.instant(),call))
    }
    companion object {
        fun fingerprint(value:String)=MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") {"%02x".format(it)}
    }
}

/** Service checkpoints keep a service identity and original physical-call facts, never a model. */
internal class AttemptIndependentSearchCache(private val store:NormalChatSendAttemptStore,private val attempt:NormalChatSendAttemptId,
    private val scope:String,private val clock:Clock,private val knownCalls:Map<String,ChatCallAccounting>) {
    fun load(item:AttachmentWebSearchPlan.Item):NormalChatSearchCheckpoint? {
        if(Regex("当前|最新|实时|今日|今天|股价|汇率|收益率|天气|气温|价格").containsMatchIn(item.query)) return null
        val row=store.loadSearchCheckpoint(attempt,scope,item.id) ?: return null
        if(row.actualModelId.isNotEmpty() || row.serviceId!=IndependentSearchServiceGrant.SERVICE_ID || row.serviceEngineId!=IndependentSearchServiceGrant.ENGINE_ID || row.planVersion!=PLAN_VERSION || clock.millis()-row.createdAt.toEpochMilli() !in 0..600_000 || !row.evidenceText.startsWith("${item.id} ${item.query}\n") || row.sources.size>3 || row.originCallIds.isEmpty()) return null
        if(row.originCallIds.any {id->val c=knownCalls[id]; c==null || c.finishedAt==null || c.serviceId!=row.serviceId || c.serviceEngineId!=row.serviceEngineId || c.requestedModelId.isNotEmpty() || c.actualModelId!=null || !c.status.endsWith("SUCCEEDED")}) return null
        if(row.sources.any {AttachmentWebSearchPlan.publicUrl(it.url)!=it.url}) return null
        return row
    }
    fun save(item:AttachmentWebSearchPlan.Item,text:String,sources:List<ProviderWebSource>,calls:List<String>) {
        store.saveSearchCheckpoint(attempt,NormalChatSearchCheckpoint(scope,item.id,text,sources.map {NormalChatSearchSource(it.url,it.title)},"",IndependentSearchServiceGrant.LABEL,clock.instant(),calls.last(),IndependentSearchServiceGrant.SERVICE_ID,IndependentSearchServiceGrant.ENGINE_ID,PLAN_VERSION,calls))
    }
    companion object {const val PLAN_VERSION="independent-semantic-v2"}
}

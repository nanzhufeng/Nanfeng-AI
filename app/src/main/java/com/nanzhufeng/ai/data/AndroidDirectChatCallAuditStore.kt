package com.nanzhufeng.ai.data

import android.content.Context
import com.nanzhufeng.ai.domain.*
import com.nanzhufeng.ai.data.local.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Callable

/** Room is the production owner. Existing bounded preferences are imported once, without bodies. */
class AndroidDirectChatCallAuditStore(context: Context, private val database:NanfengAiDatabase? = null) : DirectChatCallAuditStore {
    private val prefs=context.applicationContext.getSharedPreferences(FILE,Context.MODE_PRIVATE)
    private val lock=Any()
    init {
        if(database != null) {
            database.runInTransaction(Callable {
                legacyRead().forEachIndexed { index,json ->
                    val record=decode(json)
                    val id=record.accounting?.callId ?: "legacy-direct-$index-${record.requestedAt.toEpochMilli()}"
                    val dao=database.directChatCallAuditDao()
                    if(dao.find(id)==null) dao.insert(DirectChatCallAuditEntity(id,record.requestedAt.toEpochMilli(),encode(enrich(record,id)).toString(),record.accounting?.conversationId))
                }
            })
        }
    }
    override fun append(record:DirectChatCallAuditRecord) = synchronized(lock) {
        val id=record.accounting?.callId ?: UUID.randomUUID().toString()
        val enriched=enrich(record,id)
        val value=enriched.copy(accounting=enriched.accounting?.let(ChatAccountingJsonCodec::canonical))
        if(database==null) {
            val rows=legacyRead().also { it+=encode(value);while(it.size>MAX_LEGACY_RECORDS) it.removeAt(0) }
            check(prefs.edit().putString(RECORDS,JSONArray(rows).toString()).commit())
        } else database.runInTransaction(Callable {
            val dao=database.directChatCallAuditDao();val existing=dao.find(id)
            if(existing==null) dao.insert(DirectChatCallAuditEntity(id,value.requestedAt.toEpochMilli(),encode(value).toString(),value.accounting?.conversationId))
            else {
                val old=decode(JSONObject(existing.recordJson))
                val oldCall=requireNotNull(old.accounting);val next=requireNotNull(value.accounting)
                require(oldCall.callId==next.callId && oldCall.role==next.role && oldCall.rootAttemptId==next.rootAttemptId && oldCall.conversationId==next.conversationId && oldCall.providerId==next.providerId && oldCall.requestedModelId==next.requestedModelId && oldCall.requestedAt==next.requestedAt) { "调用用量归属冲突。" }
                if(oldCall.status.endsWith("PENDING") && next.finishedAt!=null) check(dao.enrich(id,encode(value).toString())==1)
                else require(oldCall.copy(finishedAt=null)==next.copy(finishedAt=null)) { "已完成调用的计量事实不可改写。" }
            }
        })
        Unit
    }
    override fun summary():DirectChatCallAuditSummary = database?.directChatCallAuditDao()?.let { DirectChatCallAuditSummary(it.count(),it.storageBytes()) }
        ?: DirectChatCallAuditSummary(legacyRead().size,(prefs.getString(RECORDS,"[]") ?: "[]").toByteArray().size.toLong())
    override fun listNewestFirst():List<DirectChatCallAuditRecord> = database?.directChatCallAuditDao()?.newestFirst()?.map { decode(JSONObject(it.recordJson)) }
        ?: legacyRead().asReversed().map(::decode)
    private fun enrich(record:DirectChatCallAuditRecord,id:String):DirectChatCallAuditRecord {
        if(record.accounting!=null) return record
        val usage=ProviderUsage(record.inputTokens,record.outputTokens,if(record.inputTokens!=null && record.outputTokens!=null) Math.addExact(record.inputTokens,record.outputTokens) else null,record.cachedInputTokens,record.reasoningTokens)
        val estimated=if(record.cost.totalMicros==null) ConversationCostEstimator.estimate(accountingPricingModelId(record.providerId,record.modelId,record.actualModelId),usage,record.requestedAt,record.modelAlias) else null
        val cost=estimated ?: record.cost;val source=if(estimated!=null) ConversationCostSource.LOCAL_ESTIMATE else record.costSource
        return record.copy(accounting=ChatCallAccounting(id,null,null,"BACKGROUND",record.providerId,record.modelId,record.actualModelId,record.modelAlias,record.requestedAt,null,record.status.uppercase(),usage,cost,source))
    }
    private fun encode(record:DirectChatCallAuditRecord)=JSONObject().apply {
        put("provider",record.providerId.name);put("endpoint",record.endpoint);put("modelId",record.modelId);put("alias",record.modelAlias)
        put("reasoning",record.reasoningLevel);put("requestedAt",record.requestedAt.toEpochMilli());putOpt("inputTokens",record.inputTokens);putOpt("outputTokens",record.outputTokens);put("status",record.status)
        putOpt("actualModelId",record.actualModelId);putOpt("cachedInputTokens",record.cachedInputTokens);putOpt("reasoningTokens",record.reasoningTokens)
        record.accounting?.let { put("accounting",ChatAccountingJsonCodec.encode(it)) }
    }
    private fun decode(raw:JSONObject):DirectChatCallAuditRecord {
        fun long(key:String)=if(raw.has(key) && !raw.isNull(key)) raw.getLong(key) else null
        val call=raw.optJSONObject("accounting")?.let(ChatAccountingJsonCodec::decode)
        return DirectChatCallAuditRecord(ProviderId.valueOf(raw.getString("provider")),raw.getString("endpoint"),raw.getString("modelId"),raw.getString("alias"),raw.getString("reasoning"),Instant.ofEpochMilli(raw.getLong("requestedAt")),long("inputTokens"),long("outputTokens"),raw.getString("status"),raw.optString("actualModelId").takeIf { it.isNotBlank() },long("cachedInputTokens"),long("reasoningTokens"),call?.cost ?: ProviderCost(),call?.costSource,call)
    }
    private fun legacyRead():MutableList<JSONObject> = runCatching { JSONArray(prefs.getString(RECORDS,"[]") ?: "[]").let { array -> MutableList(array.length()) { array.getJSONObject(it) } } }.getOrDefault(mutableListOf())
    private companion object { const val FILE="direct_chat_call_audit_v1";const val RECORDS="records";const val MAX_LEGACY_RECORDS=500 }
}

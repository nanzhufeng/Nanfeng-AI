package com.nanzhufeng.ai.data

import com.nanzhufeng.ai.domain.*
import org.json.JSONObject
import org.json.JSONArray
import java.time.Instant

/** A metadata whitelist. No request, response, credential or attachment is serializable here. */
object ChatAccountingJsonCodec {
    private fun JSONObject.longOrNull(key:String):Long? = if(has(key) && !isNull(key)) getLong(key) else null
    private fun JSONObject.stringOrNull(key:String):String? = if(has(key) && !isNull(key)) getString(key) else null
    /** Both persisted facts and incoming replays use the ledger's millisecond time unit. */
    fun canonical(call:ChatCallAccounting):ChatCallAccounting = call.copy(
        requestedAt=Instant.ofEpochMilli(call.requestedAt.toEpochMilli()),
        finishedAt=call.finishedAt?.let { Instant.ofEpochMilli(it.toEpochMilli()) },
    )
    fun encode(call:ChatCallAccounting):JSONObject = JSONObject().apply {
        put("callId",call.callId);putOpt("rootAttemptId",call.rootAttemptId);putOpt("conversationId",call.conversationId)
        put("role",call.role);put("providerId",call.providerId.name);put("requestedModelId",call.requestedModelId)
        putOpt("actualModelId",call.actualModelId);put("modelDisplayName",call.modelDisplayName)
        put("requestedAt",call.requestedAt.toEpochMilli());putOpt("finishedAt",call.finishedAt?.toEpochMilli());put("status",call.status)
        putOpt("inputTokens",call.usage.inputTokens);putOpt("outputTokens",call.usage.outputTokens);putOpt("totalTokens",call.usage.totalTokens)
        putOpt("cachedInputTokens",call.usage.cachedInputTokens);putOpt("reasoningTokens",call.usage.reasoningTokens)
        putOpt("priceVersion",call.cost.priceVersion);putOpt("currencyCode",call.cost.currencyCode);putOpt("chargeMicros",call.cost.totalMicros)
        putOpt("costSource",call.costSource?.name);putOpt("contextWindowTokens",call.contextWindowTokens);putOpt("contextSourceCount",call.contextSourceCount)
        putOpt("serviceId",call.serviceId);putOpt("serviceEngineId",call.serviceEngineId)
    }
    fun decode(value:JSONObject)=ChatCallAccounting(
        value.getString("callId"),value.stringOrNull("rootAttemptId"),value.stringOrNull("conversationId"),value.getString("role"),
        ProviderId.valueOf(value.getString("providerId")),value.getString("requestedModelId"),value.stringOrNull("actualModelId"),value.getString("modelDisplayName"),
        Instant.ofEpochMilli(value.getLong("requestedAt")),value.longOrNull("finishedAt")?.let(Instant::ofEpochMilli),value.getString("status"),
        ProviderUsage(value.longOrNull("inputTokens"),value.longOrNull("outputTokens"),value.longOrNull("totalTokens"),value.longOrNull("cachedInputTokens"),value.longOrNull("reasoningTokens")),
        ProviderCost(value.stringOrNull("priceVersion"),value.stringOrNull("currencyCode"),value.longOrNull("chargeMicros")),value.stringOrNull("costSource")?.let(ConversationCostSource::valueOf),
        value.longOrNull("contextWindowTokens"),value.longOrNull("contextSourceCount")?.toInt(),value.stringOrNull("serviceId"),value.stringOrNull("serviceEngineId"),
    )
    fun encodeList(calls:List<ChatCallAccounting>):String? = calls.takeIf { it.isNotEmpty() }?.let { JSONArray(it.map(::encode)).toString() }
    fun decodeList(raw:String?):List<ChatCallAccounting> = raw?.let { JSONArray(it).let { array -> List(array.length()) { index -> decode(array.getJSONObject(index)) } } }.orEmpty()
}

package com.nanzhufeng.ai.data

import android.content.Context
import com.nanzhufeng.ai.domain.ConversationId
import com.nanzhufeng.ai.domain.P6GCandidateRejection
import com.nanzhufeng.ai.domain.P6GCandidateRejectionReason
import com.nanzhufeng.ai.domain.P6GCatalogCandidate
import com.nanzhufeng.ai.domain.P6GCapability
import com.nanzhufeng.ai.domain.P6GConversationOverride
import com.nanzhufeng.ai.domain.P6GCuratedModelCatalog
import com.nanzhufeng.ai.domain.P6GGlobalDefault
import com.nanzhufeng.ai.domain.P6GLocalCatalogSnapshot
import com.nanzhufeng.ai.domain.P6GModelSelectionStore
import com.nanzhufeng.ai.domain.P6GModelTier
import com.nanzhufeng.ai.domain.P6GProviderFamily
import com.nanzhufeng.ai.domain.P6GRouteMetadata
import com.nanzhufeng.ai.domain.P6GRouteReason
import com.nanzhufeng.ai.domain.P6GRouteSource
import org.json.JSONArray
import org.json.JSONObject

/** App-private, Key-free P6-G state. The reviewed picker catalog is always available offline. */
class AndroidP6GModelSelectionStore(context: Context) : P6GModelSelectionStore {
    private val prefs = context.applicationContext.getSharedPreferences("p6g-model-selection-v1", Context.MODE_PRIVATE)
    override fun readCatalog(): P6GLocalCatalogSnapshot = P6GCuratedModelCatalog.merge(runCatching {
        val root = JSONObject(prefs.getString("catalog", "") ?: "")
        P6GLocalCatalogSnapshot(root.getString("catalogVersion"), root.getInt("policyVersion"), root.getJSONArray("candidates").map { value ->
            val item = value as JSONObject
            P6GCatalogCandidate(P6GProviderFamily.valueOf(item.getString("providerFamily")), item.getString("providerId"), item.getString("modelId"), item.getString("displayName"), item.getJSONArray("tiers").map { P6GModelTier.valueOf(it as String) }.toSet(), item.getJSONArray("capabilities").map { P6GCapability.valueOf(it as String) }.toSet(), item.getBoolean("available"), item.optLong("knownCostMicros").takeIf { item.has("knownCostMicros") }, item.getInt("latencyRank"), item.optLong("contextWindowTokens").takeIf { item.has("contextWindowTokens") })
        })
    }.getOrNull())

    override fun saveCatalog(value: P6GLocalCatalogSnapshot): Boolean = prefs.edit().putString("catalog", JSONObject().apply {
        put("catalogVersion", value.catalogVersion); put("policyVersion", value.policyVersion); put("candidates", JSONArray(value.candidates.map { candidate -> JSONObject().apply {
            put("providerFamily", candidate.providerFamily.name); put("providerId", candidate.providerId); put("modelId", candidate.modelId); put("displayName", candidate.displayName); put("tiers", JSONArray(candidate.tiers.map { it.name })); put("capabilities", JSONArray(candidate.capabilities.map { it.name })); put("available", candidate.available); candidate.knownCostMicros?.let { put("knownCostMicros", it) }; put("latencyRank", candidate.latencyRank); candidate.contextWindowTokens?.let { put("contextWindowTokens", it) }
        } }))
    }.toString()).commit()

    override fun readGlobalDefault() = P6GGlobalDefault(prefs.getLong("global.revision", 0), prefs.getString("global.tier", null)?.let(P6GModelTier::valueOf))
    override fun saveGlobalDefault(value: P6GGlobalDefault) = prefs.edit().putLong("global.revision", value.revision).putString("global.tier", value.tier?.name).commit()
    override fun readConversationOverride(conversationId: ConversationId): P6GConversationOverride = P6GConversationOverride(conversationId, prefs.getLong("conversation.${conversationId.value}.revision", 0), prefs.getString("conversation.${conversationId.value}.model", null))
    override fun saveConversationOverride(value: P6GConversationOverride) = prefs.edit().putLong("conversation.${value.conversationId.value}.revision", value.revision).putString("conversation.${value.conversationId.value}.model", value.modelId).commit()
    override fun appendRouteMetadata(value: P6GRouteMetadata): Boolean = prefs.edit().putString("last-route.${value.conversationId.value}", JSONObject().apply {
        put("policyVersion", value.policyVersion); put("catalogVersion", value.catalogVersion); put("tier", value.tier.name); put("source", value.source.name); put("reason", value.reason.name); value.modelId?.let { put("modelId", it) }; value.displayName?.let { put("displayName", it) }; put("rejected", JSONArray(value.rejectedCandidates.map { JSONObject().put("modelId", it.modelId).put("reason", it.reason.name) }))
    }.toString()).commit()
}

private inline fun <T> JSONArray.map(transform: (Any) -> T): List<T> = List(length()) { transform(get(it)) }

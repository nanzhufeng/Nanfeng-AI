package com.nanzhufeng.ai.domain

import org.json.JSONArray
import org.json.JSONObject

/**
 * P7-E's common, encrypted business-state mapping.  This is deliberately a record payload,
 * not a P5-D backup database and not a P6 exchange package.  Platform owners must map their
 * own truth into this value before calling P7-A; no Room/SQLite handle is accepted here.
 */
const val NFAI_SYNC_SEMANTIC_RECORD_FORMAT_V1 = "nfai.sync.semantic-record.v1"

private val P7E_SEMANTIC_KINDS = setOf(
    "project", "conversation", "knowledge", "memory", "relation", "safe_settings",
)

data class P7ESemanticState(
    val kind: String,
    val id: String,
    val revision: Long,
    val classification: String = "NORMAL",
    /** Canonical JSON object containing only this one stable business object's semantic state. */
    val valueJson: String,
) {
    init {
        require(kind in P7E_SEMANTIC_KINDS) { "P7E unsupported semantic kind" }
        require(id.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}"))) { "P7E semantic id invalid" }
        require(revision > 0) { "P7E semantic revision invalid" }
        require(classification == "NORMAL") { "P7E high sensitive state is never mappable" }
    }
}

data class P7ESemanticSnapshot(
    val appId: String,
    val documentId: String,
    val revision: Long,
    val states: List<P7ESemanticState>,
) {
    init {
        require(revision > 0 && states.isNotEmpty() && states.size <= 10_000)
        require(states.map { Triple(it.kind, it.id, it.revision) }.distinct().size == states.size) {
            "P7E duplicate semantic record"
        }
    }
}

/** The only common mapper on either platform. It does no persistence and returns no partial data. */
object P7ESemanticSnapshotMapper {
    fun toPreparedSnapshot(snapshot: P7ESemanticSnapshot): NfaiSyncPreparedSnapshot {
        val records = snapshot.states.map { state ->
            val value = JSONObject(state.valueJson)
            require(value.keys().asSequence().toList().none { forbiddenKey(it) }) { "P7E forbidden semantic field" }
            requireSafe(value)
            val content = JSONObject().apply {
                put("format", NFAI_SYNC_SEMANTIC_RECORD_FORMAT_V1)
                put("semanticVersion", 1)
                put("kind", state.kind)
                put("id", state.id)
                put("revision", state.revision)
                put("value", value)
            }
            NfaiSyncRecord(state.kind, state.id, state.revision, state.classification, canonical(content))
        }.sortedWith(compareBy<NfaiSyncRecord> { it.kind }.thenBy { it.id }.thenBy { it.revision })
        return NfaiSyncPreparedSnapshot(snapshot.appId, snapshot.documentId, snapshot.revision, records)
    }

    fun fromOpened(snapshot: NfaiSyncPreparedSnapshot): P7ESemanticSnapshot {
        val states = snapshot.records.map { record ->
            require(record.kind in P7E_SEMANTIC_KINDS && record.classification == "NORMAL" && record.revision > 0)
            val content = JSONObject(record.contentJson)
            require(content.keys().asSequence().toSet() == setOf("format", "semanticVersion", "kind", "id", "revision", "value"))
            require(content.getString("format") == NFAI_SYNC_SEMANTIC_RECORD_FORMAT_V1 && content.getInt("semanticVersion") == 1)
            require(content.getString("kind") == record.kind && content.getString("id") == record.id && content.getLong("revision") == record.revision)
            val value = content.getJSONObject("value")
            require(value.keys().asSequence().toList().none { forbiddenKey(it) })
            requireSafe(value)
            P7ESemanticState(record.kind, record.id, record.revision, record.classification, canonical(value))
        }
        return P7ESemanticSnapshot(snapshot.appId, snapshot.documentId, snapshot.revision, states)
    }

    private fun forbiddenKey(key: String) = Regex("(?i)(credential|api[_-]?key|authorization|token|provider[_-]?(raw|payload)|prompt|runspec|uri|path|storagekey|attachment|diagnostic|runtime)").containsMatchIn(key)
    private fun requireSafe(value: Any?) {
        when (value) {
            is JSONObject -> value.keys().asSequence().toList().forEach { requireSafe(value.get(it)) }
            is JSONArray -> repeat(value.length()) { requireSafe(value.get(it)) }
            is String -> require(MemoryDomain.sensitiveRejection(value) == null) { "P7E sensitive semantic text" }
        }
    }
    private fun canonical(value: Any?): String = when (value) {
        null, JSONObject.NULL -> "null"
        is String -> JSONObject.quote(value)
        is Boolean -> value.toString()
        is Number -> value.toLong().toString()
        is JSONObject -> value.keys().asSequence().toList().sorted().joinToString(",", "{", "}") { "${JSONObject.quote(it)}:${canonical(value.get(it))}" }
        is JSONArray -> (0 until value.length()).joinToString(",", "[", "]") { canonical(value.get(it)) }
        else -> error("P7E unsupported semantic JSON")
    }
}

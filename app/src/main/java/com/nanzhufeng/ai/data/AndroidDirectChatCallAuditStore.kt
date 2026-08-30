package com.nanzhufeng.ai.data

import android.content.Context
import com.nanzhufeng.ai.domain.DirectChatCallAuditRecord
import com.nanzhufeng.ai.domain.DirectChatCallAuditStore
import com.nanzhufeng.ai.domain.DirectChatCallAuditSummary
import com.nanzhufeng.ai.domain.ProviderId
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/** A bounded, content-free local audit. Prompts, replies, attachments and credentials never enter it. */
class AndroidDirectChatCallAuditStore(context: Context) : DirectChatCallAuditStore {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun append(record: DirectChatCallAuditRecord) {
        val entries = read().also { list ->
            list += JSONObject().apply {
                put("provider", record.providerId.name); put("endpoint", record.endpoint); put("modelId", record.modelId)
                put("alias", record.modelAlias); put("reasoning", record.reasoningLevel); put("requestedAt", record.requestedAt.toEpochMilli())
                record.inputTokens?.let { put("inputTokens", it) }; record.outputTokens?.let { put("outputTokens", it) }; put("status", record.status)
            }
            while (list.size > MAX_RECORDS) list.removeAt(0)
        }
        prefs.edit().putString(RECORDS, JSONArray(entries).toString()).commit()
    }

    override fun summary(): DirectChatCallAuditSummary {
        val raw = prefs.getString(RECORDS, "[]") ?: "[]"
        return DirectChatCallAuditSummary(read().size, raw.toByteArray(Charsets.UTF_8).size.toLong())
    }

    override fun listNewestFirst(): List<DirectChatCallAuditRecord> = read().asReversed().mapNotNull { raw ->
        runCatching {
            DirectChatCallAuditRecord(
                providerId = ProviderId.valueOf(raw.getString("provider")),
                endpoint = raw.getString("endpoint"),
                modelId = raw.getString("modelId"),
                modelAlias = raw.getString("alias"),
                reasoningLevel = raw.getString("reasoning"),
                requestedAt = Instant.ofEpochMilli(raw.getLong("requestedAt")),
                inputTokens = raw.takeIf { it.has("inputTokens") }?.getLong("inputTokens"),
                outputTokens = raw.takeIf { it.has("outputTokens") }?.getLong("outputTokens"),
                status = raw.getString("status"),
            )
        }.getOrNull()
    }

    private fun read(): MutableList<JSONObject> = runCatching {
        val array = JSONArray(prefs.getString(RECORDS, "[]") ?: "[]")
        MutableList(array.length()) { array.getJSONObject(it) }
    }.getOrDefault(mutableListOf())

    private companion object {
        const val FILE = "direct_chat_call_audit_v1"
        const val RECORDS = "records"
        const val MAX_RECORDS = 500
    }
}

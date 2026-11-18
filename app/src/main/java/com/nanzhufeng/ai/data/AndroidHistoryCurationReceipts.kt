package com.nanzhufeng.ai.data

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import com.nanzhufeng.ai.app.AppContainer
import com.nanzhufeng.ai.domain.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant

/** Bounded, content-free receipts for observing real OS background execution without instrumentation. */
class AndroidHistoryCurationReceipts(context: Context, area: ConversationSurface) {
    private val file = context.getExternalFilesDir("diagnostics")?.let {
        AtomicFile(File(it, "history-curation-${area.name.lowercase()}-v1.json"))
    }

    fun append(record: JSONObject) = synchronized(lock) {
        // All fields are assembled below from counts, enums, hashes and provider usage, never user text.
        Log.i("NfAiHistory", record.toString())
        runCatching {
            val target = file ?: return@runCatching
            val previous = runCatching { JSONArray(target.openRead().bufferedReader().use { it.readText() }) }.getOrDefault(JSONArray())
            val next = JSONArray()
            for (i in (previous.length() - 63).coerceAtLeast(0) until previous.length()) next.put(previous.getJSONObject(i))
            next.put(record)
            val stream = target.startWrite()
            try { stream.write(next.toString().toByteArray(Charsets.UTF_8)); target.finishWrite(stream) }
            catch (error: Exception) { target.failWrite(stream); throw error }
        }
        Unit
    }

    companion object {
        private val lock = Any()

        internal fun memoryWitness(memories: List<MemorySnapshot>): JSONObject {
            val automatic = memories.filter { snapshot ->
                snapshot.memory.sourceStableId.startsWith("auto-history-v1:") && snapshot.revisions.all {
                    it.sourceStableId == snapshot.memory.sourceStableId && it.status == MemoryStatus.ACTIVE
                }
            }
            val manual = memories - automatic.toSet()
            fun digest(items: List<MemorySnapshot>) = MemoryDomain.sha256(items.sortedBy { it.memory.id.value }.joinToString("\n") {
                "${it.memory.id.value}|${it.memory.status}|${it.memory.contentHash}|${it.revisions.maxOfOrNull { revision -> revision.revision } ?: 0}"
            })
            return JSONObject().apply {
                put("memoryCount", memories.size); put("automaticMemoryCount", automatic.size)
                put("automaticRevisionTotal", automatic.sumOf { it.revisions.maxOfOrNull { r -> r.revision } ?: 0 })
                put("automaticDigest", digest(automatic)); put("protectedMemoryCount", manual.size); put("protectedDigest", digest(manual))
            }
        }

        internal fun witness(container: AppContainer): JSONObject = runCatching {
            memoryWitness(container.memoryRepository.list(null, null, "")).apply {
                put("available", true); put("knowledgeCount", container.readKnowledgeLibrary.list().size)
            }
        }.getOrElse { JSONObject().put("available", false) }

        internal fun calls(container: AppContainer, since: Instant): JSONArray = runCatching { JSONArray().apply {
            container.directChatCallAudit.listNewestFirst().filter {
                it.modelAlias == HISTORY_CURATION_AUDIT_ALIAS && !it.requestedAt.isBefore(since)
            }.forEach { call -> put(JSONObject().apply {
                put("provider", call.providerId.name)
                put("requestedModel", call.modelId.takeIf { it.matches(Regex("[A-Za-z0-9/_.:-]{1,160}")) } ?: "UNVERIFIED")
                put("actualModel", call.actualModelId?.takeIf { it.matches(Regex("[A-Za-z0-9/_.:-]{1,160}")) } ?: JSONObject.NULL)
                put("requestedAt", call.requestedAt.toEpochMilli()); put("inputTokens", call.inputTokens ?: JSONObject.NULL)
                put("outputTokens", call.outputTokens ?: JSONObject.NULL)
            }) }
        } }.getOrElse { JSONArray().put(JSONObject().put("metadataAvailable", false)) }
    }
}

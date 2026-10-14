package com.nanzhufeng.ai.data

import android.content.Context
import com.nanzhufeng.ai.domain.ContextBudget
import com.nanzhufeng.ai.domain.ContextSelectionAuditRecord
import com.nanzhufeng.ai.domain.ContextSelectionAuditStore
import com.nanzhufeng.ai.domain.ContextSelectionSource
import com.nanzhufeng.ai.domain.ContextRetrievalAudit
import com.nanzhufeng.ai.domain.MessageNodeId
import com.nanzhufeng.ai.domain.NormalChatSendAttemptId
import com.nanzhufeng.ai.domain.ProviderId
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant

/** Bounded local-only context diagnostics; deliberately excludes all selected source bodies. */
class AndroidContextSelectionAuditStore(context: Context, private val database:com.nanzhufeng.ai.data.local.NanfengAiDatabase?=null) : ContextSelectionAuditStore {
    private val file = File(context.filesDir, "context-selection-audit-v1.json")
    private val lock = Any()

    init {
        database?.runInTransaction(java.util.concurrent.Callable {
            legacyRecent(MAX_ENTRIES).forEachIndexed { index,record ->
                database.contextSelectionAuditDao().insert(entity("legacy-context-$index-${record.createdAt.toEpochMilli()}",record))
            }
        })
    }
    private fun entity(id:String,record:ContextSelectionAuditRecord)=com.nanzhufeng.ai.data.local.ContextSelectionAuditEntity(id,record.attemptId?.value,record.assistantMessageId?.value,record.createdAt.toEpochMilli(),toJson(record).toString(),record.conversationId)
    override fun append(record: ContextSelectionAuditRecord) = synchronized(lock) {
        if(database!=null) {
            check(database.contextSelectionAuditDao().insert(entity(java.util.UUID.randomUUID().toString(),record))!=-1L)
            return@synchronized Unit
        }
        val entries = recent(MAX_ENTRIES - 1).toMutableList().apply { add(0, record) }
        check(runCatching { file.writeText(JSONArray(entries.map(::toJson)).toString()) }.isSuccess)
        Unit
    }

    override fun bindAnswer(
        attemptId: NormalChatSendAttemptId,
        assistantMessageId: MessageNodeId,
        webSearchUsed: Boolean?,
    ) = synchronized(lock) {
        if(database!=null) {
            database.runInTransaction(java.util.concurrent.Callable {
                val dao=database.contextSelectionAuditDao()
                dao.unbound(attemptId.value).forEach { row ->
                    val record=fromJson(JSONObject(row.recordJson)).copy(assistantMessageId=assistantMessageId,webSearchUsed=webSearchUsed)
                    check(dao.bind(row.recordId,assistantMessageId.value,toJson(record).toString())==1)
                }
            })
            return@synchronized Unit
        }
        val entries = recent(MAX_ENTRIES).toMutableList()
        var changed=false
        entries.indices.forEach { index ->
            if(entries[index].attemptId==attemptId && entries[index].assistantMessageId==null) {
                entries[index]=entries[index].copy(assistantMessageId=assistantMessageId,webSearchUsed=webSearchUsed)
                changed=true
            }
        }
        if(changed) check(runCatching { file.writeText(JSONArray(entries.map(::toJson)).toString()) }.isSuccess)
        Unit
    }

    override fun forAssistantMessages(messageIds: Collection<MessageNodeId>): Map<MessageNodeId, List<ContextSelectionAuditRecord>> {
        if (messageIds.isEmpty()) return emptyMap()
        if(database!=null) return database.contextSelectionAuditDao().forMessages(messageIds.map { it.value }).map { fromJson(JSONObject(it.recordJson)) }.groupBy { requireNotNull(it.assistantMessageId) }
        val ids = messageIds.toSet()
        return recent(MAX_ENTRIES)
            .mapNotNull { record -> record.assistantMessageId?.takeIf(ids::contains)?.let { it to record } }
            .groupBy({ it.first }, { it.second })
    }

    override fun recent(limit:Int):List<ContextSelectionAuditRecord> = synchronized(lock) {
        database?.contextSelectionAuditDao()?.recent(limit.coerceAtLeast(1))?.map { fromJson(JSONObject(it.recordJson)) } ?: legacyRecent(limit)
    }
    private fun legacyRecent(limit: Int): List<ContextSelectionAuditRecord> = synchronized(lock) {
        runCatching {
            val array = JSONArray(file.takeIf(File::isFile)?.readText() ?: "[]")
            buildList { for (index in 0 until minOf(array.length(), limit.coerceIn(1, MAX_ENTRIES))) add(fromJson(array.getJSONObject(index))) }
        }.getOrDefault(emptyList())
    }

    private fun toJson(record: ContextSelectionAuditRecord) = JSONObject().apply {
        put("createdAt", record.createdAt.toString()); putOpt("conversationId", record.conversationId); putOpt("attemptId", record.attemptId?.value); putOpt("assistantMessageId", record.assistantMessageId?.value); put("providerId", record.providerId.name); put("modelId", record.modelId); put("tokenizerId", record.tokenizerId)
        put("budget", JSONObject().apply { put("context", record.budget.contextWindowTokens); put("output", record.budget.reservedOutputTokens); put("prompt", record.budget.promptTokens); put("history", record.budget.historyTokens); put("retrieval", record.budget.retrievalTokens); put("fixedInput", record.budget.fixedInputTokens); put("tokenizerId", record.budget.tokenizerId) })
        put("indexStatus", record.indexStatus.name)
        put("participationAuditAvailable", record.participationAuditAvailable)
        record.webSearchUsed?.let { put("webSearchUsed", it) }
        record.retrievalAudit?.let { retrieval ->
            put("retrievalAudit", JSONObject().apply {
                put("topic", retrieval.topic)
                put("memorySearched", retrieval.memorySearched)
                put("selectedMemoryCount", retrieval.selectedMemoryCount)
                put("knowledgeSearched", retrieval.knowledgeSearched)
                put("selectedKnowledgeCount", retrieval.selectedKnowledgeCount)
            })
        }
        put("sources", JSONArray(record.selectedSources.map { source -> JSONObject().apply {
            put("kind", source.kind); put("id", source.stableId); put("title", source.title.take(120)); put("tokens", source.estimatedTokens)
            put("selectedByModel", source.selectedByModel)
        } }))
    }

    private fun fromJson(value: JSONObject): ContextSelectionAuditRecord {
        val budget = value.getJSONObject("budget")
        val sources = value.getJSONArray("sources")
        val retrieval = value.optJSONObject("retrievalAudit")?.let {
            ContextRetrievalAudit(
                topic = it.getString("topic"),
                memorySearched = it.getBoolean("memorySearched"),
                selectedMemoryCount = it.getInt("selectedMemoryCount"),
                knowledgeSearched = it.getBoolean("knowledgeSearched"),
                selectedKnowledgeCount = it.getInt("selectedKnowledgeCount"),
            )
        }
        return ContextSelectionAuditRecord(
            createdAt = Instant.parse(value.getString("createdAt")), conversationId = value.optString("conversationId").takeIf { it.isNotBlank() }, attemptId = value.optString("attemptId").takeIf { it.isNotBlank() }?.let(::NormalChatSendAttemptId), assistantMessageId = value.optString("assistantMessageId").takeIf { it.isNotBlank() }?.let(::MessageNodeId), providerId = ProviderId.valueOf(value.getString("providerId")), modelId = value.getString("modelId"), tokenizerId = value.getString("tokenizerId"),
            budget = ContextBudget(budget.getInt("context"), budget.getInt("output"), budget.getInt("prompt"), budget.getInt("history"), budget.getInt("retrieval"), budget.optInt("fixedInput", 0), budget.optString("tokenizerId", value.getString("tokenizerId"))),
            selectedSources = buildList { for (index in 0 until sources.length()) { val source = sources.getJSONObject(index); add(ContextSelectionSource(source.getString("kind"), source.getString("id"), source.getString("title"), source.getInt("tokens"), source.optBoolean("selectedByModel", false))) } },
            indexStatus = value.optString("indexStatus", com.nanzhufeng.ai.domain.LocalContextBroker.AssemblyStatus.READY.name).let { runCatching { com.nanzhufeng.ai.domain.LocalContextBroker.AssemblyStatus.valueOf(it) }.getOrDefault(com.nanzhufeng.ai.domain.LocalContextBroker.AssemblyStatus.READY) },
            participationAuditAvailable = value.optBoolean("participationAuditAvailable", false),
            retrievalAudit = retrieval,
            webSearchUsed = value.takeIf { it.has("webSearchUsed") }?.getBoolean("webSearchUsed"),
        )
    }

    private companion object { const val MAX_ENTRIES = 240 }
}

package com.nanzhufeng.ai.data.local

import com.nanzhufeng.ai.domain.ConversationId
import com.nanzhufeng.ai.domain.MessageNodeId
import com.nanzhufeng.ai.domain.NormalChatSendAttempt
import com.nanzhufeng.ai.domain.NormalChatSendAttemptId
import com.nanzhufeng.ai.domain.NormalChatSendAttemptStatus
import com.nanzhufeng.ai.domain.NormalChatSendAttemptStore
import com.nanzhufeng.ai.domain.ProviderId
import com.nanzhufeng.ai.domain.NormalChatSearchCheckpoint
import com.nanzhufeng.ai.domain.NormalChatSearchSource
import org.json.JSONObject
import org.json.JSONArray
import java.time.Instant
import java.util.concurrent.Callable

class RoomNormalChatSendAttemptStore(private val database: NanfengAiDatabase) : NormalChatSendAttemptStore {
    override fun create(attempt: NormalChatSendAttempt): NormalChatSendAttempt = database.runInTransaction(Callable {
        val dao = database.normalChatSendAttemptDao()
        dao.insert(attempt.toEntity())
        requireNotNull(dao.find(attempt.attemptId.value)).toDomain()
    })

    override fun transition(id: NormalChatSendAttemptId, expected: Set<NormalChatSendAttemptStatus>, next: NormalChatSendAttemptStatus, updatedAt: Instant, safeErrorCode: String?): NormalChatSendAttempt? = database.runInTransaction(Callable {
        val dao = database.normalChatSendAttemptDao()
        if (dao.transition(id.value, expected.map { it.name }, next.name, updatedAt.toEpochMilli(), safeErrorCode) != 1) return@Callable null
        dao.find(id.value)?.toDomain()
    })

    override fun findLatestForConversation(conversationId: ConversationId): NormalChatSendAttempt? =
        database.normalChatSendAttemptDao().latestForConversation(conversationId.value)?.toDomain()

    override fun markFailed(id: NormalChatSendAttemptId, updatedAt: Instant, safeErrorCode: String): NormalChatSendAttempt? =
        transition(
            id,
            setOf(
                NormalChatSendAttemptStatus.PENDING,
                NormalChatSendAttemptStatus.SENDING,
                NormalChatSendAttemptStatus.ACCEPTED,
                NormalChatSendAttemptStatus.STREAMING,
                NormalChatSendAttemptStatus.UNKNOWN,
                NormalChatSendAttemptStatus.FAILED,
            ),
            NormalChatSendAttemptStatus.FAILED,
            updatedAt,
            safeErrorCode,
        )

    override fun markInterruptedAsUnknown(updatedAt: Instant): Int =
        database.normalChatSendAttemptDao().markInterruptedAsUnknown(updatedAt.toEpochMilli())

    override fun loadSearchCheckpoint(id:NormalChatSendAttemptId,fingerprint:String,itemId:String):NormalChatSearchCheckpoint? {
        val row=database.normalChatSearchCheckpointDao().get(id.value,fingerprint,itemId) ?: return null
        return runCatching {
            val envelope=if(row.sourcesJson.trim().startsWith("{")) JSONObject(row.sourcesJson) else null
            val array=envelope?.getJSONArray("sources") ?: JSONArray(row.sourcesJson)
            NormalChatSearchCheckpoint(row.scopeFingerprint,row.itemId,row.evidenceText,(0 until array.length()).map {index ->array.getJSONObject(index).let {NormalChatSearchSource(it.getString("url"),it.optString("title").takeIf(String::isNotBlank))}},row.actualModelId,row.sourceLabel,Instant.ofEpochMilli(row.createdAtEpochMs),row.originCallId,envelope?.getString("serviceId"),envelope?.getString("engine"),envelope?.getString("plan"),envelope?.getJSONArray("calls")?.let {a->(0 until a.length()).map {a.getString(it)}}.orEmpty())
        }.getOrNull()
    }
    override fun saveSearchCheckpoint(id:NormalChatSendAttemptId,value:NormalChatSearchCheckpoint) {
        val json=JSONArray();value.sources.forEach {json.put(JSONObject().put("url",it.url).put("title",it.title.orEmpty()))}
        val encoded=if(value.serviceId!=null) JSONObject().put("sources",json).put("serviceId",value.serviceId).put("engine",value.serviceEngineId).put("plan",value.planVersion).put("calls",JSONArray(value.originCallIds)).toString() else json.toString()
        database.normalChatSearchCheckpointDao().put(NormalChatSearchCheckpointEntity(id.value,value.scopeFingerprint,value.itemId,value.evidenceText,encoded,value.actualModelId,value.sourceLabel,value.createdAt.toEpochMilli(),value.originCallId))
    }

}

private fun NormalChatSendAttempt.toEntity() = NormalChatSendAttemptEntity(
    attemptId.value, messageId.value, conversationId.value, providerId.name, modelId, idempotencyKey,
        // Schema-53 gateway columns are intentionally inert. Keep placeholders only to preserve
        // the user's already-installed Room schema without a destructive downgrade.
        status.name, createdAt.toEpochMilli(), updatedAt.toEpochMilli(), safeErrorCode, egressProviderId?.name,
        egressAuthorizedAt?.toEpochMilli(), egressDisclosureVersion, "LEGACY_UNUSED",
    null, null, null,
)

private fun NormalChatSendAttemptEntity.toDomain() = NormalChatSendAttempt(
    NormalChatSendAttemptId(attemptId), MessageNodeId(messageId), ConversationId(conversationId),
    ProviderId.valueOf(providerId), modelId, idempotencyKey, NormalChatSendAttemptStatus.valueOf(status),
    Instant.ofEpochMilli(createdAtEpochMs), Instant.ofEpochMilli(updatedAtEpochMs), safeErrorCode, egressProviderId?.let(ProviderId::valueOf),
    egressAuthorizedAtEpochMs?.let(Instant::ofEpochMilli), egressDisclosureVersion,
)

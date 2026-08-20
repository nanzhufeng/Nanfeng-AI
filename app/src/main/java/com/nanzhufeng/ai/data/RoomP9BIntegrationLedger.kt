package com.nanzhufeng.ai.data

import com.nanzhufeng.ai.data.local.NanfengAiDatabase
import com.nanzhufeng.ai.data.local.P9BIntegrationEventEntity
import com.nanzhufeng.ai.data.local.P9BIntegrationReceiptEntity
import com.nanzhufeng.ai.data.local.P9BIntegrationSessionEntity
import com.nanzhufeng.ai.domain.*

/** Secret-free P9-B audit persistence. This is not a target-app adapter and is never bound to release DI. */
class RoomP9BIntegrationLedger(private val database: NanfengAiDatabase) : P9BIntegrationLedger {
    private val dao get() = database.p9bIntegrationLedgerDao()
    override fun byRequest(requestId: String) = dao.session(requestId)?.let(::snapshot)
    override fun byIdempotency(key: String) = dao.sessionByIdempotency(key)?.let(::snapshot)
    override fun create(session: P9BSession, event: P9BEvent): P9BSnapshot = database.runInTransaction<P9BSnapshot> {
        dao.insertSession(entity(session)); dao.insertEvent(eventEntity(session.request.requestId, event)); snapshot(entity(session))
    }
    override fun append(snapshot: P9BSnapshot, session: P9BSession, event: P9BEvent, receipt: P9BReceipt?): P9BSnapshot = database.runInTransaction<P9BSnapshot> {
        dao.updateSession(entity(session)); dao.insertEvent(eventEntity(session.request.requestId, event)); receipt?.let { dao.insertReceipt(P9BIntegrationReceiptEntity(it.idempotencyKey, session.request.requestId, it.resultHash)) }; snapshot(entity(session))
    }
    private fun snapshot(session: P9BIntegrationSessionEntity): P9BSnapshot {
        val request = P9BRequest(session.requestId, session.idempotencyKey, session.appHandle, session.subjectHandle, P9BCapability.valueOf(session.capability), P9BPermission.valueOf(session.permission), P9BClassification.valueOf(session.classification), P9BProvenance(session.provenanceSource, session.sourceRevision, session.sourceHash), session.pageLimit, session.pageCursor, session.expiresAtEpochMs)
        val preview = session.previewRevision?.let { P9BPreview(it, requireNotNull(session.previewHash), session.previewItemCount ?: 0, session.previewNextCursor) }
        val events = dao.events(session.requestId).map { P9BEvent(it.sequence, it.kind, it.fingerprint) }
        return P9BSnapshot(P9BSession(request, P9BState.valueOf(session.state), preview, session.resultHash, session.errorCode), events)
    }
    private fun entity(session: P9BSession) = P9BIntegrationSessionEntity(session.request.requestId, session.request.idempotencyKey, session.request.appHandle, session.request.subjectHandle, session.state.name, session.request.capability.name, session.request.permission.name, session.request.classification.name, session.request.provenance.source, session.request.provenance.revision, session.request.provenance.contentHash, session.request.pageLimit, session.request.pageCursor, session.request.expiresAtEpochMs, session.preview?.revision, session.preview?.contentHash, session.preview?.itemCount, session.preview?.nextCursor, session.resultHash, session.errorCode)
    private fun eventEntity(requestId: String, event: P9BEvent) = P9BIntegrationEventEntity(requestId, event.sequence, event.kind, event.fingerprint)
}

package com.nanzhufeng.ai.domain

/** P7-D contains no auth/session secrets and never owns Room business objects. */
enum class P7DSyncStage { IDLE, SNAPSHOT, REMOTE_CHECK, ENCRYPTING, COMMITTING, VERIFYING, COMPLETED, CONFLICT, FAILED, INTERRUPTED, DISABLED }
data class P7DSyncJob(
    val accountRef: String,
    val generation: Long,
    val completedGeneration: Long,
    val stage: P7DSyncStage,
    val lastLocalRevision: Long?,
    val lastLocalHash: String?,
    val lastRemoteRevision: Long?,
    val lastRemoteHash: String?,
    val stagingRef: String?,
    val stagingHash: String?,
    val stagingBytes: Long?,
    val lastErrorCode: String?,
)
data class P7DSyncReceipt(val intentId: String, val accountRef: String, val generation: Long, val stage: P7DSyncStage)
data class P7DStagedEnvelope(val documentId: String, val revision: Long, val payloadHash: String, val canonicalEnvelope: String, val opaqueStagingRef: String, val byteCount: Long)

interface P7DStateStore {
    fun job(accountRef: String): P7DSyncJob?
    fun receipt(intentId: String): P7DSyncReceipt?
    fun save(job: P7DSyncJob, receipt: P7DSyncReceipt)
}
interface P7DWorkScheduler {
    fun enqueueDelayed(accountRef: String, generation: Long)
    fun ensurePeriodic(accountRef: String)
    fun cancel(accountRef: String)
}
fun interface P7DEnvelopeProducer {
    /** Implementations stream a bounded explicit allowlist then P7-A seal; no database-wide serialization. */
    fun produce(expectedRemoteRevision: Long): P7DStagedEnvelope
}
data class P7DGate(val configured: Boolean, val verifiedSession: Boolean, val ready: Boolean, val recoveryConfirmed: Boolean, val directionConfirmed: Boolean) {
    val allowed: Boolean get() = configured && verifiedSession && ready && recoveryConfirmed && directionConfirmed
}

sealed interface P7DResult {
    data class Applied(val generation: Long) : P7DResult
    data object Disabled : P7DResult
    data object Conflict : P7DResult
    data class Rejected(val code: String) : P7DResult
}

/**
 * Single state owner for a future authenticated worker. It deliberately starts inert in the App:
 * P7-C configuration and a verified transient auth session are both absent today.
 */
class P7DSyncCoordinator(
    private val store: P7DStateStore,
    private val scheduler: P7DWorkScheduler,
    private val gateway: P7CCloudGateway,
    private val gate: () -> P7DGate,
) {
    fun onBusinessMutation(intentId: String, accountRef: String): P7DResult {
        store.receipt(intentId)?.let { return P7DResult.Applied(it.generation) }
        if (!gate().allowed) return P7DResult.Disabled
        val current = store.job(accountRef) ?: blank(accountRef)
        val next = current.copy(generation = current.generation + 1, stage = P7DSyncStage.IDLE, lastErrorCode = null)
        save(next, intentId); scheduler.enqueueDelayed(accountRef, next.generation)
        return P7DResult.Applied(next.generation)
    }

    /** Cold start/account-page/avatar paths call this at most; it schedules no immediate work. */
    fun restoreSessionScheduling(accountRef: String): P7DResult {
        if (!gate().allowed) return P7DResult.Disabled
        scheduler.ensurePeriodic(accountRef); return P7DResult.Applied(store.job(accountRef)?.generation ?: 0)
    }

    fun cancelForSignOutOrSwitch(accountRef: String) { scheduler.cancel(accountRef) }

    fun run(intentId: String, accountRef: String, producer: P7DEnvelopeProducer): P7DResult {
        store.receipt(intentId)?.let { return if (it.stage == P7DSyncStage.CONFLICT) P7DResult.Conflict else P7DResult.Applied(it.generation) }
        if (!gate().allowed) return P7DResult.Disabled
        val current = store.job(accountRef) ?: return P7DResult.Rejected("NO_PENDING_GENERATION")
        if (current.generation <= current.completedGeneration) return P7DResult.Rejected("NO_PENDING_GENERATION")
        val remote = gateway.read(DEFAULT_DOCUMENT_ID, 0)
        val remoteValue = when (remote) {
            is P7CCloudResult.Value -> remote.value
            is P7CCloudResult.Rejected -> if (remote.code == "REMOTE_MISSING") null else return fail(current, intentId, "REMOTE_READ_REJECTED")
            P7CCloudResult.Disabled -> return P7DResult.Disabled
        }
        // A changed cloud head while unsynced local work exists is never silently merged/overwritten.
        if (remoteValue != null && current.lastRemoteRevision != null &&
            (remoteValue.revision != current.lastRemoteRevision || remoteValue.payloadHash != current.lastRemoteHash)) {
            val conflict = current.copy(stage = P7DSyncStage.CONFLICT, lastRemoteRevision = remoteValue.revision, lastRemoteHash = remoteValue.payloadHash)
            save(conflict, intentId); return P7DResult.Conflict
        }
        val expected = remoteValue?.revision ?: 0L
        val snapshotting = current.copy(stage = P7DSyncStage.SNAPSHOT)
        save(snapshotting, "$intentId:snapshot")
        val staged = try { producer.produce(expected) } catch (cancelled: kotlinx.coroutines.CancellationException) {
            return interrupted(snapshotting, intentId)
        } catch (_: Throwable) { return fail(snapshotting, intentId, "SNAPSHOT_REJECTED") }
        if (staged.revision != expected + 1 || staged.byteCount !in 1..(2L * 1024 * 1024)) return fail(snapshotting, intentId, "STAGING_REJECTED")
        val encrypting = snapshotting.copy(stage = P7DSyncStage.ENCRYPTING, stagingRef = staged.opaqueStagingRef, stagingHash = staged.payloadHash, stagingBytes = staged.byteCount)
        save(encrypting, "$intentId:encrypt")
        val committing = encrypting.copy(stage = P7DSyncStage.COMMITTING)
        save(committing, "$intentId:commit")
        val committed = gateway.commit(expected, staged.canonicalEnvelope)
        val receipt = (committed as? P7CCloudResult.Value)?.value ?: return fail(committing, intentId, "COMMIT_REJECTED")
        if (receipt.revision != staged.revision || receipt.payloadHash != staged.payloadHash) return fail(committing, intentId, "COMMIT_RECEIPT_REJECTED")
        val verifying = committing.copy(stage = P7DSyncStage.VERIFYING)
        save(verifying, "$intentId:verify")
        val readBack = gateway.read(staged.documentId, staged.revision)
        val returned = (readBack as? P7CCloudResult.Value)?.value
            ?: return fail(verifying, intentId, "READBACK_REJECTED")
        if (returned.revision != staged.revision || returned.payloadHash != staged.payloadHash) return fail(verifying, intentId, "READBACK_HASH_REJECTED")
        // The producer may run while another real mutation reserves a newer generation. Preserve it.
        val latestGeneration = maxOf(store.job(accountRef)?.generation ?: current.generation, current.generation)
        val next = verifying.copy(generation = latestGeneration, stage = P7DSyncStage.COMPLETED, completedGeneration = current.generation, lastLocalRevision = staged.revision, lastLocalHash = staged.payloadHash, lastRemoteRevision = returned.revision, lastRemoteHash = returned.payloadHash, lastErrorCode = null)
        save(next, intentId)
        if (next.generation > next.completedGeneration) scheduler.enqueueDelayed(accountRef, next.generation)
        return P7DResult.Applied(next.completedGeneration)
    }

    private fun fail(job: P7DSyncJob, intentId: String, code: String): P7DResult { save(job.copy(stage = P7DSyncStage.FAILED, lastErrorCode = code), intentId); return P7DResult.Rejected(code) }
    private fun interrupted(job: P7DSyncJob, intentId: String): P7DResult { save(job.copy(stage = P7DSyncStage.INTERRUPTED, lastErrorCode = "INTERRUPTED"), intentId); return P7DResult.Rejected("INTERRUPTED") }
    /** A worker cannot overwrite a generation reserved by a concurrent real business mutation. */
    private fun save(job: P7DSyncJob, intentId: String) {
        val persisted = store.job(job.accountRef)
        val protected = if (persisted != null && persisted.generation > job.generation) job.copy(generation = persisted.generation) else job
        store.save(protected, P7DSyncReceipt(intentId, protected.accountRef, protected.generation, protected.stage))
    }
    private fun blank(accountRef: String) = P7DSyncJob(accountRef, 0, 0, P7DSyncStage.IDLE, null, null, null, null, null, null, null, null)
    private companion object { const val DEFAULT_DOCUMENT_ID = "primary-sync-v1" }
}

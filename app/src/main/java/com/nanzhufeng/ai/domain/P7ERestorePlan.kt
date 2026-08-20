package com.nanzhufeng.ai.domain

/**
 * P7-E's restore boundary is deliberately separate from P5-D SQLite backup restore.
 * It never accepts Room/SQLite handles: a platform owner must provide a checkpointed,
 * atomic allowlist writer. This prevents a sync envelope from becoming a clear-and-copy path.
 */
const val NFAI_SYNC_RESTORE_PLAN_V1 = "nfai.sync.restore-plan.v1"

enum class P7ERestoreMode { EMPTY_LOCAL, REPLACE_LOCAL_CONFIRMED }
enum class P7ERestoreState { PLANNED, CHECKPOINTED, STAGED, COMMITTED, ROLLED_BACK, INTERRUPTED }

data class P7ERestorePlan(
    val format: String,
    val documentId: String,
    val remoteRevision: Long,
    val payloadHash: String,
    val records: List<NfaiSyncRecord>,
    val mode: P7ERestoreMode,
)

sealed interface P7ERestoreResult {
    data class Planned(val plan: P7ERestorePlan) : P7ERestoreResult
    data class Restored(val state: P7ERestoreState, val readbackHash: String) : P7ERestoreResult
    data class Rejected(val code: String) : P7ERestoreResult
}

/** Platform data owner: checkpoint before any non-empty replacement and atomically expose staged allowlist data. */
interface P7EAtomicAllowlistRestoreWriter {
    fun isLocalBusinessEmpty(): Boolean
    fun createCheckpoint(): String
    fun stage(plan: P7ERestorePlan): String
    fun commitAtomically(stagingRef: String): String
    fun rollback(checkpointRef: String)

    /** Successful replacement must release its private checkpoint; legacy/test writers may no-op. */
    fun discardCheckpoint(checkpointRef: String) {}
}

/**
 * A sync restore plan is opened and fully allowlisted before any platform writer is called.
 * The platform implementation is responsible for Room transaction/file-switch plus process restart.
 */
class P7ERestorePlanCoordinator(private val writer: P7EAtomicAllowlistRestoreWriter) {
    fun plan(
        canonicalEnvelope: String,
        recoveryCode: CharArray,
        expectedAppId: String,
        documentId: String,
        minimumRevision: Long,
        mode: P7ERestoreMode,
    ): P7ERestoreResult {
        val opened = NfaiSyncV1Gateway.open(canonicalEnvelope, recoveryCode, expectedAppId, documentId, minimumRevision)
        val value = (opened as? NfaiSyncResult.Opened)?.value ?: return P7ERestoreResult.Rejected("SYNC_OPEN_REJECTED")
        // P7-A validates its generic record envelope. P7-E must additionally validate the one
        // shared semantic-record mapping before it creates a restore plan for a platform writer.
        val semantic = runCatching { P7ESemanticSnapshotMapper.fromOpened(value.snapshot) }
            .getOrElse { return P7ERestoreResult.Rejected("SEMANTIC_RECORD_REJECTED") }
        val snapshot = P7ESemanticSnapshotMapper.toPreparedSnapshot(semantic)
        val supportedKinds = setOf("project", "conversation", "knowledge", "memory", "relation", "safe_settings")
        if (snapshot.records.isEmpty() || snapshot.records.size > 10_000 || snapshot.records.any { it.classification != "NORMAL" || it.kind !in supportedKinds }) {
            return P7ERestoreResult.Rejected("ALLOWLIST_REJECTED")
        }
        if (mode == P7ERestoreMode.EMPTY_LOCAL && !writer.isLocalBusinessEmpty()) return P7ERestoreResult.Rejected("LOCAL_REPLACE_CONFIRMATION_REQUIRED")
        if (mode == P7ERestoreMode.REPLACE_LOCAL_CONFIRMED && writer.isLocalBusinessEmpty()) return P7ERestoreResult.Rejected("REPLACE_MODE_NOT_NEEDED")
        return P7ERestoreResult.Planned(P7ERestorePlan(NFAI_SYNC_RESTORE_PLAN_V1, documentId, snapshot.revision, value.canonicalPayload.sha256(), snapshot.records.sortedWith(compareBy<NfaiSyncRecord> { it.kind }.thenBy { it.id }.thenBy { it.revision }), mode))
    }

    /** Any stage/commit failure rolls back the checkpoint. Cancellation is deliberately not retried. */
    fun restore(plan: P7ERestorePlan, interrupted: () -> Boolean = { false }): P7ERestoreResult {
        if (plan.format != NFAI_SYNC_RESTORE_PLAN_V1) return P7ERestoreResult.Rejected("RESTORE_PLAN_REJECTED")
        if (interrupted()) return P7ERestoreResult.Rejected("INTERRUPTED")
        val checkpoint = if (plan.mode == P7ERestoreMode.REPLACE_LOCAL_CONFIRMED) writer.createCheckpoint() else null
        return try {
            val staging = writer.stage(plan)
            if (interrupted()) throw InterruptedException()
            val readbackHash = writer.commitAtomically(staging)
            if (readbackHash != plan.payloadHash) throw IllegalStateException("readback hash mismatch")
            checkpoint?.let(writer::discardCheckpoint)
            P7ERestoreResult.Restored(P7ERestoreState.COMMITTED, readbackHash)
        } catch (_: InterruptedException) {
            checkpoint?.let(writer::rollback); P7ERestoreResult.Rejected("INTERRUPTED")
        } catch (_: Throwable) {
            checkpoint?.let(writer::rollback); P7ERestoreResult.Rejected("RESTORE_ROLLED_BACK")
        }
    }
}

private fun String.sha256(): String = java.security.MessageDigest.getInstance("SHA-256").digest(toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

package com.nanzhufeng.ai.domain

import java.time.Clock
import java.time.Instant

/**
 * The only Android domain owner allowed to turn an already user-selected v2 package into a
 * local restore command.  It deliberately has no URI, Room, Context, UI, or network surface.
 */
data class WorkspaceExchangeV2RestoreRequest(
    val intentId: String,
    val packageBytes: ByteArray,
) {
    init {
        require(intentId.matches(Regex("[A-Za-z0-9._-]{8,128}"))) { "恢复 intent 无效。" }
    }
}

/** Content-free durable proof; package/IR/asset bytes and locators never enter this value. */
data class WorkspaceExchangeV2RestoreReceipt(
    val intentId: String,
    val packageHash: String,
    val semanticHash: String,
    val origin: String,
    val sensitivity: String,
    val rootCounts: Map<String, Int>,
    val assetCount: Int,
    val assetBytes: Long,
    val ownerFieldHashes: Map<String, String>,
    val importedAt: Instant,
    val importerVersion: Int = 1,
)

/**
 * This is intentionally the only write-facing port. Implementations must atomically write the
 * complete typed workspace, private attachment owners, provenance and this receipt, or preserve
 * the prior local truth/recoverable candidate. They must re-check both guards inside the commit.
 */
data class WorkspaceExchangeV2AtomicRestoreCommit(
    val receipt: WorkspaceExchangeV2RestoreReceipt,
    val packageRead: NfaiExchangeV2PackageRead,
)

enum class WorkspaceExchangeV2LocalTruth { EMPTY, PRESENT, RECOVERY_REQUIRED }

sealed interface WorkspaceExchangeV2AtomicRestoreStoreResult {
    data class Committed(val receipt: WorkspaceExchangeV2RestoreReceipt) : WorkspaceExchangeV2AtomicRestoreStoreResult
    data class Replayed(val receipt: WorkspaceExchangeV2RestoreReceipt) : WorkspaceExchangeV2AtomicRestoreStoreResult
    data object LocalTruthPresent : WorkspaceExchangeV2AtomicRestoreStoreResult
    data object IntentConflict : WorkspaceExchangeV2AtomicRestoreStoreResult
    data object FailedRecoverably : WorkspaceExchangeV2AtomicRestoreStoreResult
}

interface WorkspaceExchangeV2AtomicRestoreStore {
    /** Existing receipt lookup is content-free and never reconstructs a workspace. */
    fun receipt(intentId: String): WorkspaceExchangeV2RestoreReceipt?
    /**
     * Reclaims only a demonstrably unpublished journal for this exact receipt before the empty
     * local-truth guard.  Implementations must leave any ambiguous candidate recoverable.
     */
    fun recoverInterruptedForRetry(receipt: WorkspaceExchangeV2RestoreReceipt): WorkspaceExchangeV2LocalTruth = localTruth()
    fun localTruth(): WorkspaceExchangeV2LocalTruth
    fun commitEmptyLocal(commit: WorkspaceExchangeV2AtomicRestoreCommit): WorkspaceExchangeV2AtomicRestoreStoreResult
}

sealed interface WorkspaceExchangeV2AtomicRestoreResult {
    data class Restored(val receipt: WorkspaceExchangeV2RestoreReceipt) : WorkspaceExchangeV2AtomicRestoreResult
    data class Replayed(val receipt: WorkspaceExchangeV2RestoreReceipt) : WorkspaceExchangeV2AtomicRestoreResult
    data class Rejected(val code: String) : WorkspaceExchangeV2AtomicRestoreResult
    data object FailedRecoverably : WorkspaceExchangeV2AtomicRestoreResult
}

class WorkspaceExchangeV2AtomicRestoreOwner(
    private val store: WorkspaceExchangeV2AtomicRestoreStore,
    private val clock: Clock,
) {
    fun restore(request: WorkspaceExchangeV2RestoreRequest): WorkspaceExchangeV2AtomicRestoreResult {
        // The strict reader is always ahead of every local observation or write.
        val read = runCatching { NfaiExchangeV2PackageReader.read(request.packageBytes) }
            .getOrElse { return WorkspaceExchangeV2AtomicRestoreResult.Rejected("PACKAGE_REJECTED") }
        val candidateReceipt = read.receipt.toRestoreReceipt(request.intentId, clock.instant())
        store.receipt(request.intentId)?.let { existing ->
            return if (existing.matches(candidateReceipt)) WorkspaceExchangeV2AtomicRestoreResult.Replayed(existing)
            else WorkspaceExchangeV2AtomicRestoreResult.Rejected("INTENT_CONFLICT")
        }
        if (store.recoverInterruptedForRetry(candidateReceipt) != WorkspaceExchangeV2LocalTruth.EMPTY) {
            return WorkspaceExchangeV2AtomicRestoreResult.Rejected("LOCAL_TRUTH_PRESENT")
        }
        return when (val committed = store.commitEmptyLocal(WorkspaceExchangeV2AtomicRestoreCommit(candidateReceipt, read))) {
            is WorkspaceExchangeV2AtomicRestoreStoreResult.Committed ->
                if (committed.receipt.matches(candidateReceipt)) WorkspaceExchangeV2AtomicRestoreResult.Restored(committed.receipt)
                else WorkspaceExchangeV2AtomicRestoreResult.FailedRecoverably
            is WorkspaceExchangeV2AtomicRestoreStoreResult.Replayed ->
                if (committed.receipt.matches(candidateReceipt)) WorkspaceExchangeV2AtomicRestoreResult.Replayed(committed.receipt)
                else WorkspaceExchangeV2AtomicRestoreResult.Rejected("INTENT_CONFLICT")
            WorkspaceExchangeV2AtomicRestoreStoreResult.LocalTruthPresent -> WorkspaceExchangeV2AtomicRestoreResult.Rejected("LOCAL_TRUTH_PRESENT")
            WorkspaceExchangeV2AtomicRestoreStoreResult.IntentConflict -> WorkspaceExchangeV2AtomicRestoreResult.Rejected("INTENT_CONFLICT")
            WorkspaceExchangeV2AtomicRestoreStoreResult.FailedRecoverably -> WorkspaceExchangeV2AtomicRestoreResult.FailedRecoverably
        }
    }

    private fun NfaiExchangeV2PackageReceipt.toRestoreReceipt(intentId: String, now: Instant) = WorkspaceExchangeV2RestoreReceipt(
        intentId = intentId,
        packageHash = packageHash,
        semanticHash = semanticHash,
        origin = origin,
        sensitivity = sensitivity,
        rootCounts = rootCounts.toSortedMap(),
        assetCount = assetCount,
        assetBytes = assetBytes,
        ownerFieldHashes = ownerFieldHashes.toSortedMap(),
        importedAt = now,
    )

    private fun WorkspaceExchangeV2RestoreReceipt.matches(other: WorkspaceExchangeV2RestoreReceipt) =
        intentId == other.intentId && packageHash == other.packageHash && semanticHash == other.semanticHash
}

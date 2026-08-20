package com.nanzhufeng.ai.domain

private const val P7E_DEFAULT_DOCUMENT_ID = "primary-sync-v1"

/**
 * The only Android production-facing P7-E restore entry.  Authentication is deliberately not
 * implemented here: a future trusted auth boundary must supply [VerifiedAccountHandle].  This
 * owner never accepts a Room handle and it does not know about HTTP, UI, or workers.
 */
data class P7EVerifiedRestoreRequest(
    val intentId: String,
    val verifiedAccount: VerifiedAccountHandle,
    val documentId: String,
    val expectedRemoteRevision: Long,
    val canonicalEnvelope: String,
    val recoveryCode: CharArray,
) {
    init {
        require(intentId.matches(Regex("[A-Za-z0-9._-]{8,128}")))
        require(documentId == P7E_DEFAULT_DOCUMENT_ID)
        require(expectedRemoteRevision > 0)
    }
}

data class P7ERestoreReceipt(
    val intentId: String,
    val accountRef: String,
    val documentId: String,
    val remoteRevision: Long,
    val payloadHash: String?,
    val outcome: String,
)

interface P7ERestoreReceiptStore {
    fun receipt(intentId: String): P7ERestoreReceipt?
    fun save(receipt: P7ERestoreReceipt)
}

/** The source is typed semantic data only; it has no Room, worker, UI, or transport API. */
fun interface P7ESemanticSnapshotSource {
    fun snapshot(documentId: String, revision: Long): NfaiSyncPreparedSnapshot
}

sealed interface P7EGuardedSealResult {
    data class Sealed(val canonicalEnvelope: String, val payloadHash: String, val revision: Long) : P7EGuardedSealResult
    data class Rejected(val code: String) : P7EGuardedSealResult
}

sealed interface P7EGuardedRestoreResult {
    data class Restored(val receipt: P7ERestoreReceipt, val replayed: Boolean) : P7EGuardedRestoreResult
    data class Rejected(val code: String) : P7EGuardedRestoreResult
}

/**
 * P7-E's account/recovery/direction/revision gate.  It deliberately permits only the currently
 * safe "empty local <- verified remote" direction.  A non-empty replacement remains a future
 * explicit UI contract; it cannot be selected by a worker or this API.
 */
class P7EGuardedRestoreOwner(
    private val accounts: P7BAccountStateMachine,
    private val accountMetadata: P7BMetadataStore,
    private val syncState: P7DStateStore,
    private val restore: P7ERestorePlanCoordinator,
    private val receipts: P7ERestoreReceiptStore,
    private val semanticSource: P7ESemanticSnapshotSource,
) {
    /**
     * Future authenticated orchestration may obtain an envelope only from the typed source.  It
     * cannot serialize Room, use a raw SQLite backup, or enqueue/perform a network request.
     */
    fun sealLocalSnapshot(
        verifiedAccount: VerifiedAccountHandle,
        documentId: String,
        expectedRemoteRevision: Long,
        recoveryCode: CharArray,
    ): P7EGuardedSealResult {
        if (documentId != P7E_DEFAULT_DOCUMENT_ID || expectedRemoteRevision < 0) return P7EGuardedSealResult.Rejected("SYNC_DOCUMENT_REJECTED")
        val accountRef = P7BAccountStateMachine.accountRef(verifiedAccount.opaqueId)
        val metadata = accountMetadata.account(accountRef) ?: return P7EGuardedSealResult.Rejected("VERIFIED_ACCOUNT_REQUIRED")
        if (metadata.state != P7BSyncState.READY || metadata.directionFact != P7BDirectionFact.LOCAL_PRESENT_EMPTY_REMOTE) {
            return P7EGuardedSealResult.Rejected("UPLOAD_DIRECTION_NOT_CONFIRMED")
        }
        val prepared = runCatching { semanticSource.snapshot(documentId, expectedRemoteRevision + 1) }
            .getOrElse { return P7EGuardedSealResult.Rejected("SEMANTIC_SNAPSHOT_REJECTED") }
        val sealed = runCatching { accounts.withReadyDataKey(accountRef) { key -> NfaiSyncV1Gateway.seal(prepared, recoveryCode, key) } }
            .getOrElse { return P7EGuardedSealResult.Rejected("KEY_MATERIAL_UNAVAILABLE") }
        return when (sealed) {
            is NfaiSyncResult.Sealed -> {
                val preflight = NfaiSyncV1Gateway.preflight(sealed.canonicalEnvelope) as? NfaiSyncResult.Preflighted
                    ?: return P7EGuardedSealResult.Rejected("SYNC_SEAL_REJECTED")
                P7EGuardedSealResult.Sealed(sealed.canonicalEnvelope, preflight.value.payloadHash, prepared.revision)
            }
            is NfaiSyncResult.Rejected -> P7EGuardedSealResult.Rejected(sealed.code)
            else -> P7EGuardedSealResult.Rejected("SYNC_SEAL_REJECTED")
        }
    }

    fun restore(request: P7EVerifiedRestoreRequest, interrupted: () -> Boolean = { false }): P7EGuardedRestoreResult {
        receipts.receipt(request.intentId)?.let { return P7EGuardedRestoreResult.Restored(it, replayed = true) }
        val accountRef = P7BAccountStateMachine.accountRef(request.verifiedAccount.opaqueId)
        val metadata = accountMetadata.account(accountRef) ?: return P7EGuardedRestoreResult.Rejected("VERIFIED_ACCOUNT_REQUIRED")
        if (metadata.state != P7BSyncState.READY) return P7EGuardedRestoreResult.Rejected(metadata.state.name)
        if (metadata.directionFact != P7BDirectionFact.EMPTY_LOCAL_REMOTE_PRESENT) return P7EGuardedRestoreResult.Rejected("RESTORE_DIRECTION_NOT_CONFIRMED")
        val remote = syncState.job(accountRef) ?: return P7EGuardedRestoreResult.Rejected("REMOTE_REVISION_UNVERIFIED")
        if (remote.stage == P7DSyncStage.CONFLICT || remote.lastRemoteRevision != request.expectedRemoteRevision || remote.lastRemoteHash.isNullOrBlank()) {
            return P7EGuardedRestoreResult.Rejected("REMOTE_REVISION_UNVERIFIED")
        }
        // This proves the P7-B vault remains usable without returning or retaining the data key.
        if (runCatching { accounts.withReadyDataKey(accountRef) { Unit } }.isFailure) return P7EGuardedRestoreResult.Rejected("KEY_MATERIAL_UNAVAILABLE")
        val planned = restore.plan(
            request.canonicalEnvelope,
            request.recoveryCode,
            APP_ID,
            request.documentId,
            request.expectedRemoteRevision,
            P7ERestoreMode.EMPTY_LOCAL,
        )
        val plan = (planned as? P7ERestoreResult.Planned)?.plan
            ?: return P7EGuardedRestoreResult.Rejected((planned as P7ERestoreResult.Rejected).code)
        val outcome = restore.restore(plan, interrupted)
        val receipt = when (outcome) {
            is P7ERestoreResult.Restored -> P7ERestoreReceipt(request.intentId, accountRef, request.documentId, request.expectedRemoteRevision, outcome.readbackHash, "COMMITTED")
            is P7ERestoreResult.Rejected -> P7ERestoreReceipt(request.intentId, accountRef, request.documentId, request.expectedRemoteRevision, null, outcome.code)
            is P7ERestoreResult.Planned -> error("P7E restore cannot return a plan")
        }
        receipts.save(receipt)
        return if (outcome is P7ERestoreResult.Restored) P7EGuardedRestoreResult.Restored(receipt, replayed = false)
        else P7EGuardedRestoreResult.Rejected(receipt.outcome)
    }

    private companion object {
        const val APP_ID = "com.nanzhufeng.ai"
    }
}

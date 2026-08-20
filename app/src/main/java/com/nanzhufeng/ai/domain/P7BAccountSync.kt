package com.nanzhufeng.ai.domain

import java.security.MessageDigest

/** Opaque proof issued only by a future authentication boundary; it deliberately carries no profile or token. */
class VerifiedAccountHandle internal constructor(internal val opaqueId: String) {
    init { require(opaqueId.matches(Regex("[A-Za-z0-9._-]{8,128}"))) }
    companion object { internal fun fromVerifiedAuthentication(opaqueId: String) = VerifiedAccountHandle(opaqueId) }
}

enum class P7BSyncState {
    SIGNED_OUT, AUTHENTICATED_NEEDS_RECOVERY_CONFIRMATION, DIRECTION_REQUIRED, READY, SYNCING, CONFLICT, FAILED, SIGNED_OUT_KEEP_LOCAL,
}
enum class P7BDirectionFact { EMPTY_LOCAL_EMPTY_REMOTE, EMPTY_LOCAL_REMOTE_PRESENT, LOCAL_PRESENT_EMPTY_REMOTE, LOCAL_PRESENT_REMOTE_PRESENT }
data class P7BAccountMetadata(
    val accountRef: String,
    val state: P7BSyncState,
    val revision: Long,
    val keyAliasRef: String?,
    val wrappedKeyRef: String?,
    val wrappedKeySha256: String?,
    val directionFact: P7BDirectionFact?,
    val lastError: String?,
)
data class P7BIntentReceipt(val intentId: String, val accountRef: String, val expectedRevision: Long?, val resultingRevision: Long, val state: P7BSyncState)

interface P7BMetadataStore {
    fun account(accountRef: String): P7BAccountMetadata?
    fun receipt(intentId: String): P7BIntentReceipt?
    fun transaction(block: () -> P7BIntentReceipt): P7BIntentReceipt
    fun save(metadata: P7BAccountMetadata, receipt: P7BIntentReceipt)
}
interface P7BKeyVault {
    data class Created(val aliasRef: String, val wrappedKeyRef: String, val wrappedKeySha256: String)
    fun createAccountKey(accountRef: String): Created
    fun <T> withUnsealedDataKey(accountRef: String, block: (ByteArray) -> T): T
    fun isUsable(metadata: P7BAccountMetadata): Boolean
}

/** P7-B has no scheduler or cloud gateway: every transition is local and deliberately guarded. */
class P7BAccountStateMachine(private val store: P7BMetadataStore, private val keyVault: P7BKeyVault) {
    fun authenticate(intentId: String, expectedRevision: Long?, verified: VerifiedAccountHandle): P7BIntentReceipt = store.transaction {
        store.receipt(intentId)?.let { return@transaction it }
        val ref = accountRef(verified.opaqueId); val prior = store.account(ref)
        if (prior != null && expectedRevision != null && prior.revision != expectedRevision) error("REVISION_CONFLICT")
        val next = if (prior == null) {
            val key = keyVault.createAccountKey(ref)
            P7BAccountMetadata(ref, P7BSyncState.AUTHENTICATED_NEEDS_RECOVERY_CONFIRMATION, 1, key.aliasRef, key.wrappedKeyRef, key.wrappedKeySha256, null, null)
        } else if (!keyVault.isUsable(prior)) {
            prior.copy(state = P7BSyncState.FAILED, revision = prior.revision + 1, lastError = "KEY_MATERIAL_UNAVAILABLE")
        } else if (prior.state == P7BSyncState.SIGNED_OUT || prior.state == P7BSyncState.SIGNED_OUT_KEEP_LOCAL) {
            prior.copy(state = P7BSyncState.DIRECTION_REQUIRED, revision = prior.revision + 1, lastError = null)
        } else prior
        val receipt = P7BIntentReceipt(intentId, ref, expectedRevision, next.revision, next.state); store.save(next, receipt); receipt
    }

    fun confirmRecoverySaved(intentId: String, expectedRevision: Long, accountRef: String): P7BIntentReceipt = transition(intentId, expectedRevision, accountRef) { current ->
        require(current.state == P7BSyncState.AUTHENTICATED_NEEDS_RECOVERY_CONFIRMATION) { "RECOVERY_CONFIRMATION_REQUIRED" }
        current.copy(state = P7BSyncState.DIRECTION_REQUIRED, revision = current.revision + 1)
    }

    fun chooseDirection(intentId: String, expectedRevision: Long, accountRef: String, fact: P7BDirectionFact): P7BIntentReceipt = transition(intentId, expectedRevision, accountRef) { current ->
        require(current.state == P7BSyncState.DIRECTION_REQUIRED) { "DIRECTION_REQUIRED" }
        val state = if (fact == P7BDirectionFact.LOCAL_PRESENT_REMOTE_PRESENT) P7BSyncState.CONFLICT else P7BSyncState.READY
        current.copy(state = state, revision = current.revision + 1, directionFact = fact)
    }

    fun markConflict(intentId: String, expectedRevision: Long, accountRef: String): P7BIntentReceipt = transition(intentId, expectedRevision, accountRef) { current ->
        require(current.state == P7BSyncState.READY || current.state == P7BSyncState.SYNCING) { "SYNC_NOT_READY" }
        current.copy(state = P7BSyncState.CONFLICT, revision = current.revision + 1)
    }

    fun signOutKeepLocal(intentId: String, expectedRevision: Long, accountRef: String): P7BIntentReceipt = transition(intentId, expectedRevision, accountRef) { current ->
        current.copy(state = P7BSyncState.SIGNED_OUT_KEEP_LOCAL, revision = current.revision + 1)
    }

    fun <T> withReadyDataKey(accountRef: String, block: (ByteArray) -> T): T {
        val metadata = store.account(accountRef) ?: error("SIGNED_OUT")
        require(metadata.state == P7BSyncState.READY) { metadata.state.name }
        require(keyVault.isUsable(metadata)) { "KEY_MATERIAL_UNAVAILABLE" }
        return keyVault.withUnsealedDataKey(accountRef, block)
    }

    /** Cold start reads metadata only; it never creates a vault, unlocks a key, or schedules network work. */
    fun restoreAtColdStart(accountRef: String): P7BAccountMetadata? = store.account(accountRef)?.let { current ->
        if (current.state !in setOf(P7BSyncState.SIGNED_OUT, P7BSyncState.SIGNED_OUT_KEEP_LOCAL) && !keyVault.isUsable(current)) current.copy(state = P7BSyncState.FAILED, lastError = "KEY_MATERIAL_UNAVAILABLE") else current
    }

    private fun transition(intentId: String, expectedRevision: Long, accountRef: String, mutate: (P7BAccountMetadata) -> P7BAccountMetadata): P7BIntentReceipt = store.transaction {
        store.receipt(intentId)?.let { return@transaction it }; val current = store.account(accountRef) ?: error("SIGNED_OUT")
        require(current.revision == expectedRevision) { "REVISION_CONFLICT" }; val next = mutate(current)
        val receipt = P7BIntentReceipt(intentId, accountRef, expectedRevision, next.revision, next.state); store.save(next, receipt); receipt
    }
    companion object { fun accountRef(opaqueId: String) = MessageDigest.getInstance("SHA-256").digest(opaqueId.toByteArray()).joinToString("") { "%02x".format(it) }.take(32) }
}

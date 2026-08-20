package com.nanzhufeng.ai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class P7EGuardedRestoreOwnerContractsTest {
    private val recovery = "fixture recovery only".toCharArray()

    @Test fun `typed source seals only after verified ready upload direction`() {
        val fixture = Fixture(P7BDirectionFact.LOCAL_PRESENT_EMPTY_REMOTE)
        val result = fixture.owner.sealLocalSnapshot(fixture.verified, "primary-sync-v1", 4, recovery)
        assertTrue(result is P7EGuardedSealResult.Sealed)
        result as P7EGuardedSealResult.Sealed
        assertEquals(5, result.revision)
        assertTrue(NfaiSyncV1Gateway.open(result.canonicalEnvelope, recovery, "com.nanzhufeng.ai", "primary-sync-v1", 5) is NfaiSyncResult.Opened)
        assertEquals(5, fixture.sourceRevision)
    }

    @Test fun `restore rejects wrong account direction and stale remote before writer stage`() {
        val fixture = Fixture(P7BDirectionFact.EMPTY_LOCAL_REMOTE_PRESENT)
        val envelope = fixture.envelope(7)
        val wrong = VerifiedAccountHandle.fromVerifiedAuthentication("verified-account-other")
        val wrongAccount = P7EVerifiedRestoreRequest("restore-wrong-account", wrong, "primary-sync-v1", 7, envelope, recovery)
        assertEquals("VERIFIED_ACCOUNT_REQUIRED", (fixture.owner.restore(wrongAccount) as P7EGuardedRestoreResult.Rejected).code)
        val stale = P7EVerifiedRestoreRequest("restore-stale-revision", fixture.verified, "primary-sync-v1", 6, envelope, recovery)
        assertEquals("REMOTE_REVISION_UNVERIFIED", (fixture.owner.restore(stale) as P7EGuardedRestoreResult.Rejected).code)
        assertEquals(0, fixture.writer.stages)
    }

    @Test fun `restore commits once and durable receipt makes duplicate intent inert`() {
        val fixture = Fixture(P7BDirectionFact.EMPTY_LOCAL_REMOTE_PRESENT)
        val request = P7EVerifiedRestoreRequest("restore-receipt-01", fixture.verified, "primary-sync-v1", 7, fixture.envelope(7), recovery)
        val first = fixture.owner.restore(request) as P7EGuardedRestoreResult.Restored
        val replay = fixture.owner.restore(request) as P7EGuardedRestoreResult.Restored
        assertTrue(!first.replayed && replay.replayed)
        assertEquals(1, fixture.writer.stages)
        assertEquals(first.receipt, replay.receipt)
    }

    private class Fixture(direction: P7BDirectionFact) {
        val verified = VerifiedAccountHandle.fromVerifiedAuthentication("verified-account-p7e")
        private val accountRef = P7BAccountStateMachine.accountRef(verified.opaqueId)
        private val accountStore = AccountStore().apply {
            accounts[accountRef] = P7BAccountMetadata(accountRef, P7BSyncState.READY, 3, "alias", "ref", "hash", direction, null)
        }
        private val vault = Vault(accountRef)
        private val state = StateStore().apply {
            jobs[accountRef] = P7DSyncJob(accountRef, 0, 0, P7DSyncStage.COMPLETED, null, null, 7, "a".repeat(64), null, null, null, null)
        }
        val writer = Writer()
        var sourceRevision = -1L
        private val source = P7ESemanticSnapshotSource { document, revision ->
            sourceRevision = revision
            P7ESemanticSnapshotMapper.toPreparedSnapshot(P7ESemanticSnapshot("com.nanzhufeng.ai", document, revision, listOf(
                P7ESemanticState("project", "project-p7e", revision, valueJson = "{\"title\":\"fixture\"}"),
            )))
        }
        val owner = P7EGuardedRestoreOwner(P7BAccountStateMachine(accountStore, vault), accountStore, state, P7ERestorePlanCoordinator(writer), ReceiptStore(), source)

        fun envelope(revision: Long): String = (NfaiSyncV1Gateway.seal(
            P7ESemanticSnapshotMapper.toPreparedSnapshot(P7ESemanticSnapshot("com.nanzhufeng.ai", "primary-sync-v1", revision, listOf(
                P7ESemanticState("project", "project-p7e", revision, valueJson = "{\"title\":\"fixture\"}"),
            ))), "fixture recovery only".toCharArray(), ByteArray(32) { 7 },
        ) as NfaiSyncResult.Sealed).canonicalEnvelope
    }

    private class AccountStore : P7BMetadataStore {
        val accounts = mutableMapOf<String, P7BAccountMetadata>(); private val receipts = mutableMapOf<String, P7BIntentReceipt>()
        override fun account(accountRef: String) = accounts[accountRef]
        override fun receipt(intentId: String) = receipts[intentId]
        override fun transaction(block: () -> P7BIntentReceipt) = block()
        override fun save(metadata: P7BAccountMetadata, receipt: P7BIntentReceipt) { accounts[metadata.accountRef] = metadata; receipts[receipt.intentId] = receipt }
    }
    private class Vault(private val account: String) : P7BKeyVault {
        override fun createAccountKey(accountRef: String) = error("not used")
        override fun <T> withUnsealedDataKey(accountRef: String, block: (ByteArray) -> T): T { require(accountRef == account); return block(ByteArray(32) { 8 }) }
        override fun isUsable(metadata: P7BAccountMetadata) = metadata.accountRef == account
    }
    private class StateStore : P7DStateStore {
        val jobs = mutableMapOf<String, P7DSyncJob>(); private val receipts = mutableMapOf<String, P7DSyncReceipt>()
        override fun job(accountRef: String) = jobs[accountRef]
        override fun receipt(intentId: String) = receipts[intentId]
        override fun save(job: P7DSyncJob, receipt: P7DSyncReceipt) { jobs[job.accountRef] = job; receipts[receipt.intentId] = receipt }
    }
    private class ReceiptStore : P7ERestoreReceiptStore {
        val values = mutableMapOf<String, P7ERestoreReceipt>()
        override fun receipt(intentId: String) = values[intentId]
        override fun save(receipt: P7ERestoreReceipt) { values[receipt.intentId] = receipt }
    }
    private class Writer : P7EAtomicAllowlistRestoreWriter {
        var stages = 0; private var records = emptyList<NfaiSyncRecord>(); private var payloadHash = ""
        override fun isLocalBusinessEmpty() = true
        override fun createCheckpoint() = error("empty restore must not checkpoint")
        override fun stage(plan: P7ERestorePlan): String { stages++; records = plan.records; payloadHash = plan.payloadHash; return "stage" }
        override fun commitAtomically(stagingRef: String) = payloadHash
        override fun rollback(checkpointRef: String) = Unit
    }
}

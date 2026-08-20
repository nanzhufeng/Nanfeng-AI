package com.nanzhufeng.ai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * LOCAL_TEST_ONLY: an opaque in-memory equivalent of P7-C read/commit RPC semantics.
 * It is test-source only and has no production DI, UI, network, account, or filesystem binding.
 */
@RunWith(RobolectricTestRunner::class)
class P7ELocalTestOnlyCrossDeviceContractsTest {
    private val recovery = "fixture recovery code only".toCharArray()
    private val key = ByteArray(32) { it.toByte() }

    @Test fun `Android A seal fake RPC Desktop-open shape B commit and A atomic restore readback`() {
        val cloud = LocalTestOnlyCloud(); val scope = LocalTestOnlyScope("account-a", "primary-sync-v1")
        val a1 = seal(scope.documentId, 1, "A-first")
        assertEquals(1, cloud.commit(scope, 0, a1, "a-upload").revision)
        // Desktop consumes exactly the same P7-A envelope; Rust has its independent golden/open gate.
        assertTrue(NfaiSyncV1Gateway.open(cloud.read(scope)!!.envelope, recovery, APP, scope.documentId, 1) is NfaiSyncResult.Opened)
        val b2 = seal(scope.documentId, 2, "B-edit")
        assertEquals(2, cloud.commit(scope, 1, b2, "b-edit").revision)
        val target = TestAtomicAllowlistWriter(empty = true)
        val coordinator = P7ERestorePlanCoordinator(target)
        val plan = coordinator.plan(cloud.read(scope)!!.envelope, recovery, APP, scope.documentId, 2, P7ERestoreMode.EMPTY_LOCAL) as P7ERestoreResult.Planned
        target.expectedHash = plan.plan.payloadHash
        val restored = coordinator.restore(plan.plan) as P7ERestoreResult.Restored
        assertEquals(plan.plan.payloadHash, restored.readbackHash)
        assertEquals("B-edit", target.visible.first { it.kind == "project" }.contentJson.substringAfter("\"title\":\"").substringBefore('"'))
    }

    @Test fun `direction stale duplicate conflict interruption and account document isolation stop safely`() {
        val cloud = LocalTestOnlyCloud(); val a = LocalTestOnlyScope("account-a", "sync-fixture-v1"); val b = LocalTestOnlyScope("account-b", "document-b")
        val first = seal(a.documentId, 1, "first"); val receipt = cloud.commit(a, 0, first, "intent-1")
        assertEquals(receipt, cloud.commit(a, 0, first, "intent-1")) // worker/intent replay never increments.
        assertTrue(cloud.commitResult(a, 0, seal(a.documentId, 1, "stale"), "intent-2") is LocalTestOnlyCommitResult.Stale)
        assertEquals(null, cloud.read(b)); assertFalse(cloud.scopes().contains(b))
        val target = TestAtomicAllowlistWriter(empty = false); val coordinator = P7ERestorePlanCoordinator(target)
        assertEquals("LOCAL_REPLACE_CONFIRMATION_REQUIRED", (coordinator.plan(first, recovery, APP, a.documentId, 1, P7ERestoreMode.EMPTY_LOCAL) as P7ERestoreResult.Rejected).code)
        val replace = coordinator.plan(first, recovery, APP, a.documentId, 1, P7ERestoreMode.REPLACE_LOCAL_CONFIRMED) as P7ERestoreResult.Planned
        target.expectedHash = replace.plan.payloadHash
        assertEquals("INTERRUPTED", (coordinator.restore(replace.plan) { true } as P7ERestoreResult.Rejected).code)
        assertTrue(target.checkpoints.isEmpty()) // interruption before checkpoint leaves existing local truth untouched.
        assertTrue(cloud.hasLocalDirtyRemoteUpdate(a, localExpectedRevision = 0))
    }

    @Test fun `wrong code tamper cross document high sensitivity and failed replace preserve local truth`() {
        val cloud = LocalTestOnlyCloud(); val scope = LocalTestOnlyScope("account-a", "sync-fixture-v1"); val envelope = seal(scope.documentId, 1, "safe")
        cloud.commit(scope, 0, envelope, "safe-intent")
        val target = TestAtomicAllowlistWriter(empty = false, failCommit = true); val coordinator = P7ERestorePlanCoordinator(target)
        assertTrue(coordinator.plan(envelope, "wrong".toCharArray(), APP, scope.documentId, 1, P7ERestoreMode.REPLACE_LOCAL_CONFIRMED) is P7ERestoreResult.Rejected)
        assertTrue(coordinator.plan(envelope, recovery, APP, "document-other", 1, P7ERestoreMode.REPLACE_LOCAL_CONFIRMED) is P7ERestoreResult.Rejected)
        assertTrue(coordinator.plan(envelope.replace("payloadHash", "payloadHasy"), recovery, APP, scope.documentId, 1, P7ERestoreMode.REPLACE_LOCAL_CONFIRMED) is P7ERestoreResult.Rejected)
        val replace = coordinator.plan(envelope, recovery, APP, scope.documentId, 1, P7ERestoreMode.REPLACE_LOCAL_CONFIRMED) as P7ERestoreResult.Planned
        target.expectedHash = replace.plan.payloadHash
        assertEquals("RESTORE_ROLLED_BACK", (coordinator.restore(replace.plan) as P7ERestoreResult.Rejected).code)
        assertTrue(target.rolledBack); assertEquals("existing", target.visible.single().id)
        val sensitive = NfaiSyncPreparedSnapshot(APP, scope.documentId, 2, listOf(NfaiSyncRecord("knowledge", "sensitive", 1, "HIGH_SENSITIVE", "{\"title\":\"no\"}")))
        assertTrue(NfaiSyncV1Gateway.seal(sensitive, recovery, key) is NfaiSyncResult.Rejected)
    }

    private fun seal(documentId: String, revision: Long, title: String): String {
        val snapshot = P7ESemanticSnapshotMapper.toPreparedSnapshot(P7ESemanticSnapshot(APP, documentId, revision, listOf(
            P7ESemanticState("project", "project-p7a-01", revision, valueJson = "{\"archived\":false,\"title\":\"$title\"}"),
            P7ESemanticState("knowledge", "knowledge-p7a-01", revision, valueJson = "{\"body\":\"Non-sensitive local test fixture\",\"title\":\"Encrypted only\"}"),
        )))
        val material = NfaiSyncKnownAnswerMaterial(
            key.copyOf(), ByteArray(16) { (revision + it).toByte() },
            ByteArray(12) { (revision + it + 32).toByte() }, ByteArray(12) { (revision + it + 48).toByte() },
        )
        return when (val sealed = NfaiSyncV1Gateway.sealKnownAnswer(snapshot, recovery, material)) {
            is NfaiSyncResult.Sealed -> sealed.canonicalEnvelope
            is NfaiSyncResult.Rejected -> error(sealed.code)
            else -> error("unexpected sync result")
        }
    }

    private data class LocalTestOnlyScope(val accountRef: String, val documentId: String)
    private data class LocalTestOnlyReceipt(val revision: Long, val payloadHash: String)
    private data class LocalTestOnlyDocument(val envelope: String, val revision: Long, val payloadHash: String)
    private sealed interface LocalTestOnlyCommitResult { data class Value(val receipt: LocalTestOnlyReceipt) : LocalTestOnlyCommitResult; data object Stale : LocalTestOnlyCommitResult }
    private class LocalTestOnlyCloud {
        private val documents = mutableMapOf<LocalTestOnlyScope, LocalTestOnlyDocument>(); private val receipts = mutableMapOf<Pair<LocalTestOnlyScope, String>, LocalTestOnlyReceipt>()
        fun read(scope: LocalTestOnlyScope) = documents[scope]
        fun scopes() = documents.keys
        fun commit(scope: LocalTestOnlyScope, expected: Long, envelope: String, intent: String): LocalTestOnlyReceipt = (commitResult(scope, expected, envelope, intent) as LocalTestOnlyCommitResult.Value).receipt
        fun commitResult(scope: LocalTestOnlyScope, expected: Long, envelope: String, intent: String): LocalTestOnlyCommitResult {
            receipts[scope to intent]?.let { return LocalTestOnlyCommitResult.Value(it) }
            val p = (NfaiSyncV1Gateway.preflight(envelope) as? NfaiSyncResult.Preflighted)?.value ?: error("LOCAL_TEST_ONLY envelope rejected")
            val current = documents[scope]?.revision ?: 0; if (current != expected || p.documentId != scope.documentId || p.revision != expected + 1) return LocalTestOnlyCommitResult.Stale
            val receipt = LocalTestOnlyReceipt(p.revision, p.payloadHash); documents[scope] = LocalTestOnlyDocument(envelope, p.revision, p.payloadHash); receipts[scope to intent] = receipt; return LocalTestOnlyCommitResult.Value(receipt)
        }
        fun hasLocalDirtyRemoteUpdate(scope: LocalTestOnlyScope, localExpectedRevision: Long) = (documents[scope]?.revision ?: 0) != localExpectedRevision
    }
    private class TestAtomicAllowlistWriter(private val empty: Boolean, private val failCommit: Boolean = false) : P7EAtomicAllowlistRestoreWriter {
        var expectedHash = ""; var rolledBack = false; val checkpoints = mutableListOf<String>(); var visible = listOf(NfaiSyncRecord("knowledge", "existing", 1, "NORMAL", "{\"title\":\"old\"}")); private var staged: List<NfaiSyncRecord> = emptyList()
        override fun isLocalBusinessEmpty() = empty
        override fun createCheckpoint(): String = "checkpoint".also(checkpoints::add)
        override fun stage(plan: P7ERestorePlan): String { staged = plan.records; return "stage" }
        override fun commitAtomically(stagingRef: String): String { check(stagingRef == "stage" && !failCommit); visible = staged; return expectedHash }
        override fun rollback(checkpointRef: String) { rolledBack = true }
    }
    private companion object { const val APP = "com.nanzhufeng.ai" }
}

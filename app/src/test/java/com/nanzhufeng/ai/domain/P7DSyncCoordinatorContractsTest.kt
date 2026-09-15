package com.nanzhufeng.ai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class P7DSyncCoordinatorContractsTest {
    private class Store : P7DStateStore {
        val jobs = mutableMapOf<String, P7DSyncJob>(); val receipts = mutableMapOf<String, P7DSyncReceipt>()
        override fun job(accountRef: String) = jobs[accountRef]
        override fun receipt(intentId: String) = receipts[intentId]
        override fun save(job: P7DSyncJob, receipt: P7DSyncReceipt) { jobs[job.accountRef] = job; receipts[receipt.intentId] = receipt }
    }
    private class Scheduler : P7DWorkScheduler { var delayed = 0; var periodic = 0; var cancelled = 0; override fun enqueueDelayed(accountRef: String, generation: Long) { delayed++ }; override fun ensurePeriodic(accountRef: String) { periodic++ }; override fun cancel(accountRef: String) { cancelled++ } }
    private class Gateway(var remote: P7CRemoteEnvelope? = null) : P7CCloudGateway {
        override fun read(documentId: String, minimumRevision: Long) = remote?.let { P7CCloudResult.Value(it) } ?: P7CCloudResult.Rejected("REMOTE_MISSING")
        override fun commit(expectedRevision: Long, canonicalEnvelope: String): P7CCloudResult<P7CCommitReceipt> { val pre = NfaiSyncV1Gateway.preflight(canonicalEnvelope) as NfaiSyncResult.Preflighted; remote = P7CRemoteEnvelope(pre.value.documentId, pre.value.revision, pre.value.payloadHash, canonicalEnvelope); return P7CCloudResult.Value(P7CCommitReceipt(pre.value.revision, pre.value.payloadHash)) }
        override fun delete(documentId: String, expectedRevision: Long) = P7CCloudResult.Value(remote?.takeIf { it.documentId == documentId && it.revision == expectedRevision }?.let { remote = null; true } ?: false)
    }
    private val enabled = P7DGate(true, true, true, true, true)

    @Test fun `disabled app startup account page and periodic path enqueue nothing`() {
        val store = Store(); val scheduler = Scheduler(); val coordinator = P7DSyncCoordinator(store, scheduler, P7CDisabledCloudGateway) { P7DGate(false, false, false, false, false) }
        assertEquals(P7DResult.Disabled, coordinator.onBusinessMutation("write", "account-safe"))
        assertEquals(P7DResult.Disabled, coordinator.restoreSessionScheduling("account-safe"))
        assertEquals(0, scheduler.delayed + scheduler.periodic)
    }

    @Test fun `real mutation delays once and newer generation survives a completed run`() {
        val store = Store(); val scheduler = Scheduler(); val gateway = Gateway(P7CRemoteEnvelope("primary-sync-v1", 6, "a".repeat(64), "{}")); val coordinator = P7DSyncCoordinator(store, scheduler, gateway) { enabled }
        assertEquals(P7DResult.Applied(1), coordinator.onBusinessMutation("write-1", "account-safe")); assertEquals(1, scheduler.delayed)
        val envelope = fixtureEnvelope()
        val producer = P7DEnvelopeProducer { expected ->
            // New business data arriving while a worker is in progress reserves generation 2.
            coordinator.onBusinessMutation("write-2", "account-safe"); staged(envelope, expected, "stage-a")
        }
        assertTrue(coordinator.run("worker-1", "account-safe", producer) is P7DResult.Applied)
        assertEquals(2, store.job("account-safe")!!.generation)
        assertEquals(1, store.job("account-safe")!!.completedGeneration)
        assertTrue(scheduler.delayed >= 2)
    }

    @Test fun `changed remote head stops local write as conflict and signout cancels`() {
        val store = Store(); val scheduler = Scheduler(); val gateway = Gateway(P7CRemoteEnvelope("primary-sync-v1", 2, "a".repeat(64), "{}")); val coordinator = P7DSyncCoordinator(store, scheduler, gateway) { enabled }
        coordinator.onBusinessMutation("write", "account-safe")
        store.jobs["account-safe"] = store.jobs["account-safe"]!!.copy(lastRemoteRevision = 1, lastRemoteHash = "b".repeat(64))
        assertEquals(P7DResult.Conflict, coordinator.run("worker", "account-safe", P7DEnvelopeProducer { error("must not snapshot") }))
        assertEquals(P7DSyncStage.CONFLICT, store.job("account-safe")!!.stage)
        coordinator.cancelForSignOutOrSwitch("account-safe"); assertEquals(1, scheduler.cancelled)
    }

    private fun fixtureEnvelope(): String = java.io.File(sequenceOf(java.io.File("../protocol"), java.io.File("protocol"), java.io.File("../../protocol")).first { it.isDirectory }, "fixtures/nfai.sync.v1.golden.json").readText().let { org.json.JSONObject(it).getJSONObject("envelope").toString() }
    private fun staged(envelope: String, expected: Long, ref: String): P7DStagedEnvelope { val pre = NfaiSyncV1Gateway.preflight(envelope) as NfaiSyncResult.Preflighted; return P7DStagedEnvelope(pre.value.documentId, expected + 1, pre.value.payloadHash, envelope, ref, envelope.toByteArray().size.toLong()) }
}

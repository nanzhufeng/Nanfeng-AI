package com.nanzhufeng.ai.domain

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class P9BIntegrationContractTest {
    private val hash = "a".repeat(64)
    private fun raw(app: String = "app_fixture", classification: String = "NON_SENSITIVE", limit: Int = 1, expiry: String = "null", extra: String = "") = """{"format":"nfai.integration-contract","version":1,"mode":"LOCAL_TEST_ONLY","requestId":"request_one","idempotencyKey":"idem_one","appHandle":"$app","subjectHandle":"subject_one","capability":"READ_ONLY_PREVIEW","permission":"READ_ONLY","classification":"$classification","provenance":{"source":"LOCAL_TEST_ONLY","revision":1,"contentHash":"$hash"},"page":{"limit":$limit,"cursor":null},"expiresAtEpochMs":$expiry$extra}"""
    private val target = object : P9BLocalTestOnlyTarget {
        override val appHandle = "app_fixture"
        var current = P9BPreview(1, hash, 1, null)
        override fun preview(subjectHandle: String, limit: Int, cursor: String?) = current
        override fun readback(subjectHandle: String) = current
    }
    private fun harness(ledger: MemoryLedger = MemoryLedger()) = P9BLocalTestOnlyHarness(ledger, target, Clock.fixed(Instant.ofEpochMilli(1000), ZoneOffset.UTC))

    @Test fun `local test only path is explicit read only confirmed readback revocable and receipt replayable`() {
        val ledger = MemoryLedger(); val harness = harness(ledger)
        assertTrue(harness.request(raw()) is P9BResult.Accepted)
        assertTrue(harness.request(raw()) is P9BResult.Replayed)
        assertTrue(harness.authorize("request_one") is P9BResult.Accepted)
        assertTrue(harness.preview("request_one") is P9BResult.Accepted)
        assertTrue(harness.confirm("request_one") is P9BResult.Accepted)
        val result = harness.result("request_one") as P9BResult.Accepted
        assertNotNull(result.snapshot.session.resultHash)
        assertTrue(harness.result("request_one") is P9BResult.Replayed)
        assertTrue(harness.readback("request_one") is P9BResult.Accepted)
        val revoked = harness.revoke("request_one") as P9BResult.Accepted
        assertEquals(P9BState.REVOKED, revoked.snapshot.session.state)
        assertEquals(listOf("REQUESTED", "AUTHORIZED", "PREVIEWED", "CONFIRMED", "RESULT_READY", "READBACK_VERIFIED", "REVOKED"), revoked.snapshot.events.map { it.kind })
    }

    @Test fun `unknown high sensitive pagination scope expiry and cancelled states fail closed`() {
        assertEquals("UNKNOWN_OR_INVALID_SCHEMA", (P9BContractParser.parse(raw(extra = ",\"uri\":\"content://forbidden\"")) as P9BResult.Rejected).code)
        assertEquals("HIGH_SENSITIVE_OR_UNKNOWN_DENIED", (P9BContractParser.parse(raw(classification = "HIGH_SENSITIVE")) as P9BResult.Rejected).code)
        assertEquals("PAGINATION_LIMIT_DENIED", (P9BContractParser.parse(raw(limit = 26)) as P9BResult.Rejected).code)
        val harness = harness(); harness.request(raw(app = "app_other")); assertEquals("APP_SCOPE_DENIED", (harness.authorize("request_one") as P9BResult.Rejected).code)
        val expired = harness(); expired.request(raw(expiry = "0")); assertEquals("PERMISSION_EXPIRED", (expired.authorize("request_one") as P9BResult.Rejected).code)
        val cancelled = harness(); cancelled.request(raw()); cancelled.cancel("request_one"); assertEquals("INVALID_STATE_TRANSITION", (cancelled.authorize("request_one") as P9BResult.Rejected).code)
    }

    @Test fun `target revision update after preview prevents result readback success`() {
        val harness = harness(); harness.request(raw()); harness.authorize("request_one"); harness.preview("request_one"); harness.confirm("request_one"); harness.result("request_one")
        target.current = P9BPreview(2, "b".repeat(64), 1, null)
        assertEquals("TARGET_UPDATED", (harness.readback("request_one") as P9BResult.Rejected).code)
        target.current = P9BPreview(1, hash, 1, null)
    }

    private class MemoryLedger : P9BIntegrationLedger {
        private val snapshots = linkedMapOf<String, P9BSnapshot>(); private val keys = mutableMapOf<String, String>()
        override fun byRequest(requestId: String) = snapshots[requestId]
        override fun byIdempotency(key: String) = keys[key]?.let(snapshots::get)
        override fun create(session: P9BSession, event: P9BEvent): P9BSnapshot = P9BSnapshot(session, listOf(event)).also { snapshots[session.request.requestId] = it; keys[session.request.idempotencyKey] = session.request.requestId }
        override fun append(snapshot: P9BSnapshot, session: P9BSession, event: P9BEvent, receipt: P9BReceipt?) = P9BSnapshot(session, snapshot.events + event).also { snapshots[session.request.requestId] = it }
    }
}

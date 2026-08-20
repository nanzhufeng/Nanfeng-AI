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
        var selectedAppHandle = "app_fixture"
        override val appHandle get() = selectedAppHandle
        var current = P9BPreview(1, hash, 1, null)
        var previewCalls = 0
        var readbackCalls = 0
        override fun preview(subjectHandle: String, limit: Int, cursor: String?): P9BPreview { previewCalls++; return current }
        override fun readback(subjectHandle: String): P9BPreview { readbackCalls++; return current }
    }
    private class MutableClock(var now: Long) : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId) = this
        override fun instant() = Instant.ofEpochMilli(now)
    }
    private fun harness(ledger: MemoryLedger = MemoryLedger(), clock: Clock = Clock.fixed(Instant.ofEpochMilli(1000), ZoneOffset.UTC)) = P9BLocalTestOnlyHarness(ledger, target, clock)

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

    @Test fun `expired permission prevents later preview confirmation receipt and readback without target calls`() {
        val clock = MutableClock(1000)
        val previewExpired = harness(clock = clock)
        previewExpired.request(raw(expiry = "1000")); previewExpired.authorize("request_one")
        clock.now = 1001
        assertEquals("PERMISSION_EXPIRED", (previewExpired.preview("request_one") as P9BResult.Rejected).code)
        assertEquals(0, target.previewCalls)

        val confirmationClock = MutableClock(999)
        val confirmationExpired = harness(clock = confirmationClock)
        confirmationExpired.request(raw(expiry = "1000")); confirmationExpired.authorize("request_one"); confirmationExpired.preview("request_one")
        confirmationClock.now = 1001
        assertEquals("PERMISSION_EXPIRED", (confirmationExpired.confirm("request_one") as P9BResult.Rejected).code)

        val receiptClock = MutableClock(999)
        val receiptExpired = harness(clock = receiptClock)
        receiptExpired.request(raw(expiry = "1000")); receiptExpired.authorize("request_one"); receiptExpired.preview("request_one"); receiptExpired.confirm("request_one")
        receiptClock.now = 1001
        val blocked = receiptExpired.result("request_one") as P9BResult.Rejected
        assertEquals("PERMISSION_EXPIRED", blocked.code)
        assertNull(blocked.snapshot!!.session.resultHash)

        val readbackClock = MutableClock(999)
        val readbackExpired = harness(clock = readbackClock)
        readbackExpired.request(raw(expiry = "1000")); readbackExpired.authorize("request_one"); readbackExpired.preview("request_one"); readbackExpired.confirm("request_one"); readbackExpired.result("request_one")
        readbackClock.now = 1001
        assertEquals("PERMISSION_EXPIRED", (readbackExpired.readback("request_one") as P9BResult.Rejected).code)
        assertEquals(0, target.readbackCalls)
    }

    @Test fun `target selection change prevents continuation and leaves no synthetic receipt`() {
        val ledger = MemoryLedger(); val harness = harness(ledger)
        harness.request(raw()); harness.authorize("request_one")
        target.selectedAppHandle = "app_reselected"
        val blocked = harness.preview("request_one") as P9BResult.Rejected
        assertEquals("APP_SCOPE_DENIED", blocked.code)
        assertEquals(0, target.previewCalls)
        assertNull(blocked.snapshot!!.session.resultHash)
        target.selectedAppHandle = "app_fixture"
    }

    private class MemoryLedger : P9BIntegrationLedger {
        private val snapshots = linkedMapOf<String, P9BSnapshot>(); private val keys = mutableMapOf<String, String>()
        override fun byRequest(requestId: String) = snapshots[requestId]
        override fun byIdempotency(key: String) = keys[key]?.let(snapshots::get)
        override fun create(session: P9BSession, event: P9BEvent): P9BSnapshot = P9BSnapshot(session, listOf(event)).also { snapshots[session.request.requestId] = it; keys[session.request.idempotencyKey] = session.request.requestId }
        override fun append(snapshot: P9BSnapshot, session: P9BSession, event: P9BEvent, receipt: P9BReceipt?) = P9BSnapshot(session, snapshot.events + event).also { snapshots[session.request.requestId] = it }
    }
}

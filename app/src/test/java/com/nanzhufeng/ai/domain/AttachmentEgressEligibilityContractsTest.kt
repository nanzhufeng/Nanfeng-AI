package com.nanzhufeng.ai.domain

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AttachmentEgressEligibilityContractsTest {
    private val now = Instant.parse("2026-08-15T15:00:00Z")
    private val owner = AttachmentEgressAuthorizationOwner(Clock.fixed(now, ZoneOffset.UTC))

    @Test fun `explicit consent fee capability and safe conversation execution binding issue an active one time grant`() {
        val intent = intent()
        val result = owner.authorize(request(intent))

        val summary = (result as AttachmentEgressAuthorizationResult.Authorized).summary
        assertEquals(AttachmentEgressAuthorizationState.ACTIVE, summary.state)
        assertEquals("provider/vision", summary.modelId)
        assertEquals(AttachmentId("attachment"), summary.attachmentId)
        assertEquals(ConversationRealTextExecutionId("execution"), summary.executionId)
        assertTrue(owner.consume(summary.authorizationId) is AttachmentEgressAuthorizationResult.Authorized)
        assertTrue(owner.consume(summary.authorizationId) is AttachmentEgressAuthorizationResult.Rejected)
    }

    @Test fun `capability MIME size content boundaries and missing fee confirmation fail closed`() {
        val base = intent()
        val unsupported = base.copy(capability = base.capability.copy(allowedMimeTypes = setOf("application/pdf")))
        assertEquals(AttachmentEgressRejection.MIME_DENIED, owner.project(unsupported))
        val oversized = base.copy(capability = base.capability.copy(maxByteCount = 5))
        assertEquals(AttachmentEgressRejection.SIZE_DENIED, owner.project(oversized))
        val noFee = request(base).copy(consent = request(base).consent?.copy(feeConfirmation = null))
        assertEquals(AttachmentEgressRejection.FEE_CONFIRMATION_REQUIRED, (owner.authorize(noFee) as AttachmentEgressAuthorizationResult.Rejected).reason)
    }

    @Test fun `revoked expired and changed replay cannot authorize again`() {
        val intent = intent()
        val active = (owner.authorize(request(intent)) as AttachmentEgressAuthorizationResult.Authorized).summary
        assertTrue(owner.revoke(active.authorizationId) is AttachmentEgressAuthorizationResult.Authorized)
        assertEquals(AttachmentEgressRejection.REVOKED, (owner.authorize(request(intent)) as AttachmentEgressAuthorizationResult.Rejected).reason)
        val changed = request(intent).copy(authorizationId = AttachmentEgressAuthorizationId("different"))
        assertEquals(AttachmentEgressRejection.REPLAY_CONFLICT, (owner.authorize(changed) as AttachmentEgressAuthorizationResult.Rejected).reason)
        val expiredOwner = AttachmentEgressAuthorizationOwner(Clock.fixed(now, ZoneOffset.UTC))
        assertEquals(AttachmentEgressRejection.EXPIRED, (expiredOwner.authorize(request(intent, now)) as AttachmentEgressAuthorizationResult.Rejected).reason)
    }

    @Test fun `safe summary and grant request carry no filenames locations bytes content credentials or transport`() {
        val prohibited = setOf("displayname", "uri", "path", "bytearray", "rawcontent", "prompt", "response", "apikey", "bearer", "credential", "secret", "http", "transport")
        val fields = (AttachmentEgressSafeSummary::class.java.declaredFields + AttachmentEgressAuthorizationRequest::class.java.declaredFields).map { it.name.lowercase() }
        assertFalse(fields.any { field -> prohibited.any(field::contains) })
        assertEquals("attachment-egress-eligibility-v1", ATTACHMENT_EGRESS_ELIGIBILITY_PROTOCOL)
    }

    private fun intent() = AttachmentEgressIntent(
        attachmentId = AttachmentId("attachment"), attachmentSha256 = "a".repeat(64), mimeType = "image/png", byteCount = 10,
        conversationId = ConversationId("conversation"), executionId = ConversationRealTextExecutionId("execution"),
        capability = AttachmentEgressCapability("provider", "provider/vision", 1, setOf("image/png"), setOf(AttachmentEgressContentType.IMAGE), 20),
    )

    private fun request(intent: AttachmentEgressIntent, expiresAt: Instant = now.plusSeconds(60)): AttachmentEgressAuthorizationRequest {
        val fee = AttachmentEgressFeeConfirmation("fee", now, "catalog-1", "USD", 20)
        val consent = ExplicitAttachmentEgressConsent("consent", now, intent.fingerprint(), fee)
        return AttachmentEgressAuthorizationRequest(AttachmentEgressAuthorizationId("authorization"), "replay", intent, consent, expiresAt)
    }
}

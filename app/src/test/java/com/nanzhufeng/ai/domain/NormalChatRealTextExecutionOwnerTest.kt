package com.nanzhufeng.ai.domain

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NormalChatRealTextExecutionOwnerTest {
    private val now = Instant.parse("2026-08-15T00:00:00Z")
    private val owner = NormalChatRealTextExecutionOwner(Clock.fixed(now, ZoneOffset.UTC))

    @Test fun `default owner discloses an unregistered text only route with unchecked confirmation`() {
        val confirmation = owner.requestConfirmation(NormalChatExternalSendIntent(draftCharacterCount = 12, attachmentCount = 0))

        assertEquals(NormalChatExternalSendBlocker.EGRESS_UNREGISTERED, confirmation.blocker)
        assertFalse(confirmation.acknowledgementChecked)
        assertFalse(confirmation.isConfirmable)
        assertEquals("未知（不可确认）", confirmation.maximumFee)
        assertEquals(now.plusSeconds(300), confirmation.expiresAt)
    }

    @Test fun `attachments are rejected without reading them and expiry revokes acknowledgement`() {
        val confirmation = owner.requestConfirmation(NormalChatExternalSendIntent(draftCharacterCount = 12, attachmentCount = 1))
        val checked = owner.setAcknowledgement(confirmation, checked = true)
        val expired = owner.expire(checked)

        assertEquals(NormalChatExternalSendBlocker.ATTACHMENTS_NOT_SUPPORTED, confirmation.blocker)
        assertTrue(checked.acknowledgementChecked)
        assertEquals(NormalChatExternalSendBlocker.CONFIRMATION_EXPIRED, expired.blocker)
        assertFalse(expired.acknowledgementChecked)
    }
}

package com.nanzhufeng.ai.domain

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class P4CMemoryContractsTest {
    private val domain = MemoryDomain(Clock.fixed(Instant.parse("2026-08-13T12:30:00Z"), ZoneOffset.UTC))

    @Test fun `scope is stable mutually exclusive and content hashes normalize whitespace`() {
        val global = MemoryScope(MemoryScopeKind.GLOBAL)
        assertEquals("GLOBAL||", global.stableKey())
        assertEquals(domain.contentHash(" 偏好 ", "使用  中文"), domain.contentHash("偏好", "使用 中文"))
        assertTrue(runCatching { MemoryScope(MemoryScopeKind.PROJECT, ProjectId("p"), ConversationId("c")) }.isFailure)
    }

    @Test fun `high sensitivity is rejected before a memory action can be persisted`() {
        val rejected = domain.validate(MemoryIntent(MemoryIntentId("i"), MemoryIntentAction.CREATE, MemoryId("m"), "凭据", "Authorization: Bearer abcdefghijklmnop", MemoryScope(MemoryScopeKind.GLOBAL)))
        assertEquals(MemoryRejectionCode.HIGH_SENSITIVITY_AUTHORIZATION, rejected)
        val card = domain.validate(MemoryIntent(MemoryIntentId("card"), MemoryIntentAction.CREATE, MemoryId("m2"), "卡", "4111 1111 1111 1111", MemoryScope(MemoryScopeKind.GLOBAL)))
        assertEquals(MemoryRejectionCode.HIGH_SENSITIVITY_PAYMENT_CARD, card)
    }
}

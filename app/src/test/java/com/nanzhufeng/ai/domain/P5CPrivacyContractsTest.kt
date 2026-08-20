package com.nanzhufeng.ai.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class P5CPrivacyContractsTest {
    @Test fun `diagnostic allowlist accepts aggregate-only document`() {
        assertTrue(SecurityDiagnosticAllowlist.accepts("{\"format\":\"nanfeng-ai.security-diagnostic\",\"counts\":{\"knowledge\":2}}"))
    }

    @Test fun `diagnostic rejects body url path and credential-shaped values as whole document`() {
        listOf(
            "{\"body\":\"private note\"}",
            "{\"endpoint\":\"https://example.test/a?x=1\"}",
            "{\"path\":\"a/b\"}",
            "{\"authorization\":\"Bearer abcdefghijklmnop\"}",
            "{\"api key\":\"value\"}",
        ).forEach { assertFalse(SecurityDiagnosticAllowlist.accepts(it)) }
    }

    @Test fun `full deletion is the only strong-confirmation scope`() {
        assertTrue(PrivacyDeleteScope.ALL_LOCAL_BUSINESS_DATA.requiresPhrase)
        assertFalse(PrivacyDeleteScope.KNOWLEDGE_MEMORY_TRASH.requiresPhrase)
    }

    @Test fun `precise task deletion is terminal-only and never crosses formal references`() {
        listOf("RUNNING", "QUEUED", "FETCHING", "EXTRACTING", "AWAITING_CONFIRMATION", "COMPLETED", "PARTIALLY_COMPLETED").forEach { status ->
            assertFalse(PrivacyTaskDeletionPolicy.canSelect(status, false, false, true))
        }
        assertTrue(PrivacyTaskDeletionPolicy.canSelect("FAILED", false, false, true))
        assertTrue(PrivacyTaskDeletionPolicy.canSelect("CANCELLED", false, false, true))
        assertFalse(PrivacyTaskDeletionPolicy.canSelect("FAILED", true, false, true))
        assertFalse(PrivacyTaskDeletionPolicy.canSelect("FAILED", false, true, true))
        assertFalse(PrivacyTaskDeletionPolicy.canSelect("FAILED", false, false, false))
    }

    @Test fun `only cleanup failures are retryable and no lifecycle invokes retry`() {
        assertTrue(PrivacyTaskDeletionPolicy.isRetryableRemaining("FAILED_CLEANUP"))
        assertFalse(PrivacyTaskDeletionPolicy.isRetryableRemaining("FAILED"))
        assertFalse(PrivacyTaskDeletionPolicy.isRetryableRemaining("COMPLETED"))
    }
}

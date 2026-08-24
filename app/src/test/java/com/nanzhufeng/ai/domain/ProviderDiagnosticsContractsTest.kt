package com.nanzhufeng.ai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderDiagnosticsContractsTest {
    @Test fun `redactor retains a bounded actionable message without an api key`() {
        val raw = """{\"error\":{\"message\":\"model not found\"},\"token\":\"sk-super-secret-key\",\"Authorization\":\"Bearer abcdefghijklmnopqrstuvwxyz\"}"""

        val redacted = ErrorBodyRedactor.redact(raw)

        assertTrue(redacted.contains("model not found"))
        assertTrue(redacted.contains("***REDACTED***"))
        assertFalse(redacted.contains("super-secret"))
        assertFalse(redacted.contains("abcdefghijklmnopqrstuvwxyz"))
    }

    @Test fun `failure classifier distinguishes model stream and authentication causes`() {
        assertEquals(ProviderDiagnosticErrorClass.MODEL_NOT_FOUND, classifyProviderFailure(400, "model not found"))
        assertEquals(ProviderDiagnosticErrorClass.STREAM_REQUIRED, classifyProviderFailure(400, "only support stream required"))
        assertEquals(ProviderDiagnosticErrorClass.AUTHENTICATION, classifyProviderFailure(401, "invalid api key"))
        assertEquals(ProviderDiagnosticErrorClass.RATE_LIMIT, classifyProviderFailure(429, null))
    }
}

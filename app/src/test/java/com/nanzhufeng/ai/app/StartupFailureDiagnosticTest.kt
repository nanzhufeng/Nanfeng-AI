package com.nanzhufeng.ai.app

import org.junit.Assert.*
import org.junit.Test

class StartupFailureDiagnosticTest {
    @Test fun `startup failure metadata excludes exception messages and private content`() {
        val error = IllegalStateException("private body and key", IllegalArgumentException("private path"))
        val diagnostic = startupFailureDiagnostic("container", error)
        assertTrue(diagnostic.contains("stage=container"))
        assertTrue(diagnostic.contains("java.lang.IllegalArgumentException"))
        assertFalse(diagnostic.contains("private body"))
        assertFalse(diagnostic.contains("private path"))
    }
}

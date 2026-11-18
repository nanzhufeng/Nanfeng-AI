package com.nanzhufeng.ai.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryKnowledgeAutoCurationRetryPolicyTest {
    @Test fun `only transient transport failures retry`() {
        listOf("CONNECT_FAILED", "HTTP_429", "HTTP_502", "HTTP_503").forEach { code ->
            assertTrue(code, code.isTransientAutoCurationFailure())
        }
        listOf("TIMEOUT", "NETWORK", "HTTP_408", "HTTP_504", "UNKNOWN_TIMEOUT", "HTTP_500", "HTTP_599", "SERVICE_DISABLED", "CREDENTIAL_MISSING", "MODEL_UNAVAILABLE", "HTTP_400", "HTTP_401", "HTTP_404", "RESPONSE_FORMAT", "CANCELLED").forEach { code ->
            assertFalse(code, code.isTransientAutoCurationFailure())
        }
    }
}

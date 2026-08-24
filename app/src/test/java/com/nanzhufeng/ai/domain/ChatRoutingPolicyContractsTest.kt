package com.nanzhufeng.ai.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatRoutingPolicyContractsTest {
    @Test fun `default auto routing keeps fallback inside the selected provider`() {
        val policy = ChatRoutingPolicy()
        assertTrue(policy.autoRoutingEnabled)
        assertTrue(policy.automaticFallbackEnabled)
        assertFalse(policy.crossProviderFallbackEnabled)
        assertTrue(policy.qualityEscalationEnabled)
    }

    @Test fun `review policy is explicit and defaults to important tasks`() {
        assertTrue(ChatRoutingPolicy().crossModelReviewPolicy == CrossModelReviewPolicy.IMPORTANT_ONLY)
    }
}

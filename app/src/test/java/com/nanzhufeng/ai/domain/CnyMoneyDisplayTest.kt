package com.nanzhufeng.ai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CnyMoneyDisplayTest {
    @Test fun `USD micros are converted with the versioned current reference rate`() {
        assertEquals("约 ¥0.035013", CnyMoneyDisplay.label(5_210L, "USD", estimated = false))
        assertEquals("≈ ¥0.035886（估算）", CnyMoneyDisplay.label(5_340L, "USD", estimated = true))
    }

    @Test fun `CNY micros keep their original value without a conversion qualifier`() {
        assertEquals("¥1.25", CnyMoneyDisplay.label(1_250_000L, "CNY", estimated = false))
        assertEquals("≈ ¥1.25（估算）", CnyMoneyDisplay.label(1_250_000L, "CNY", estimated = true))
    }

    @Test fun `mixed supported currencies are converted before aggregation`() {
        val total = CnyMoneyDisplay.totalLabel(
            listOf(
                ProviderCost("usd", "USD", 1_000_000L),
                ProviderCost("cny", "CNY", 1_000_000L),
            ),
            estimated = false,
        )
        assertEquals("约 ¥7.720309", total)
    }

    @Test fun `unsupported currency is never mislabeled as RMB`() {
        assertNull(CnyMoneyDisplay.label(1_000_000L, "EUR", estimated = false))
    }
}

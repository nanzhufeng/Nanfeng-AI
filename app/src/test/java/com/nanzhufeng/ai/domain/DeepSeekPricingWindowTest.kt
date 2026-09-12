package com.nanzhufeng.ai.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class DeepSeekPricingWindowTest {
    @Test fun `weekends stay off peak and Friday boundary wakes on Monday`() {
        assertEquals(DeepSeekPricingPeriod.OFF_PEAK, DeepSeekPricingWindow.periodAt(Instant.parse("2026-09-12T01:00:00Z")))
        assertEquals(63 * 60 * 60 * 1_000L, DeepSeekPricingWindow.millisUntilNextTransition(Instant.parse("2026-09-11T10:00:00Z")))
    }

    @Test fun `official UTC boundaries switch peak and off peak exactly`() {
        val cases = mapOf(
            "2026-09-10T00:59:59Z" to DeepSeekPricingPeriod.OFF_PEAK,
            "2026-09-10T01:00:00Z" to DeepSeekPricingPeriod.PEAK,
            "2026-09-10T03:59:59Z" to DeepSeekPricingPeriod.PEAK,
            "2026-09-10T04:00:00Z" to DeepSeekPricingPeriod.OFF_PEAK,
            "2026-09-10T06:00:00Z" to DeepSeekPricingPeriod.PEAK,
            "2026-09-10T09:59:59Z" to DeepSeekPricingPeriod.PEAK,
            "2026-09-10T10:00:00Z" to DeepSeekPricingPeriod.OFF_PEAK,
        )

        cases.forEach { (instant, expected) ->
            assertEquals(instant, expected, DeepSeekPricingWindow.periodAt(Instant.parse(instant)))
        }
    }

    @Test fun `picker labels are the requested concise Chinese status`() {
        assertEquals("当前低谷", DeepSeekPricingPeriod.OFF_PEAK.pickerLabel)
        assertEquals("当前高峰", DeepSeekPricingPeriod.PEAK.pickerLabel)
    }

    @Test fun `live picker wakes at the next boundary including the following UTC day`() {
        assertEquals(30 * 60 * 1_000L, DeepSeekPricingWindow.millisUntilNextTransition(Instant.parse("2026-09-10T00:30:00Z")))
        assertEquals(15 * 60 * 60 * 1_000L, DeepSeekPricingWindow.millisUntilNextTransition(Instant.parse("2026-09-10T10:00:00Z")))
    }
}

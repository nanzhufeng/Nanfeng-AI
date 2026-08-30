package com.nanzhufeng.ai.domain

import java.time.Instant
import java.time.ZoneOffset

/**
 * The single owner for DeepSeek's official peak/off-peak billing window.
 *
 * DeepSeek publishes the schedule in UTC: peak is 01:00-04:00 and 06:00-10:00
 * every day. Using the request instant in UTC keeps the result correct even if the device
 * timezone is changed while the model picker is open.
 */
enum class DeepSeekPricingPeriod(val pickerLabel: String) {
    OFF_PEAK("当前低谷"),
    PEAK("当前高峰"),
}

object DeepSeekPricingWindow {
    private const val SecondsPerDay = 24 * 60 * 60
    private val transitionSecondsUtc = intArrayOf(
        1 * 60 * 60,
        4 * 60 * 60,
        6 * 60 * 60,
        10 * 60 * 60,
    )

    fun periodAt(instant: Instant): DeepSeekPricingPeriod {
        val secondOfDay = instant.atOffset(ZoneOffset.UTC).toLocalTime().toSecondOfDay()
        val peak = secondOfDay in transitionSecondsUtc[0] until transitionSecondsUtc[1] ||
            secondOfDay in transitionSecondsUtc[2] until transitionSecondsUtc[3]
        return if (peak) DeepSeekPricingPeriod.PEAK else DeepSeekPricingPeriod.OFF_PEAK
    }

    /** Milliseconds until the next exact schedule boundary; useful for a live picker. */
    fun millisUntilNextTransition(instant: Instant): Long {
        val utc = instant.atOffset(ZoneOffset.UTC)
        val secondOfDay = utc.toLocalTime().toSecondOfDay()
        val nanoOfSecond = utc.nano
        val nextSecond = transitionSecondsUtc.firstOrNull { it > secondOfDay } ?: (SecondsPerDay + transitionSecondsUtc.first())
        val wholeSeconds = nextSecond - secondOfDay
        return wholeSeconds * 1_000L - nanoOfSecond / 1_000_000L
    }
}

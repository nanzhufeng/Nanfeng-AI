package com.nanzhufeng.ai.domain

/** Finite leases cover active long requests without keeping the device awake indefinitely. */
class GenerationWakeLease {
    private var startedAtMs: Long? = null
    fun begin(nowMs: Long) { if (startedAtMs == null) startedAtMs = nowMs }
    fun timeoutMs(nowMs: Long): Long = startedAtMs?.let { start ->
        (MAX_ACTIVE_MS - (nowMs - start).coerceAtLeast(0)).coerceIn(0, LEASE_MS)
    } ?: 0
    fun finish() { startedAtMs = null }
    companion object { const val LEASE_MS = 120_000L; const val MAX_ACTIVE_MS = 1_800_000L }
}

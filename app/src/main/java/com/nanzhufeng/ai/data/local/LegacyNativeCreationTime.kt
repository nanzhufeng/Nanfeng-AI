package com.nanzhufeng.ai.data.local

/** Content-free repair candidate; no date is inferred from a title or current clock. */
data class LegacyNativeCreationTime(
    val id: String,
    val createdAtEpochMs: Long,
    val revision: Long,
    val firstSentAtEpochMs: Long,
)

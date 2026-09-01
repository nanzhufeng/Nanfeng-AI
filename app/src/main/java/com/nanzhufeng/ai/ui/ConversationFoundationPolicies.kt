package com.nanzhufeng.ai.ui

import java.util.Locale

internal data class PdfPageCacheKey(
    val attachmentId: String,
    val sourceKey: String,
    val pageNumber: Int,
)

internal data class ArchivePdfContext(
    val containerPath: List<String>,
    val entryPath: String,
)

/** Exact history wins, then the most recent prefix, then the most recent contained match. */
internal fun bestSearchHistoryMatch(input: String, history: List<String>): String? {
    val normalized = input.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)
    if (normalized.isBlank()) return null
    fun canonical(value: String) = value.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)
    return history.firstOrNull { canonical(it) == normalized }
        ?: history.firstOrNull { canonical(it).startsWith(normalized) }
        ?: history.firstOrNull { canonical(it).contains(normalized) }
}

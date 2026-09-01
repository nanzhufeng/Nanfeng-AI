package com.nanzhufeng.ai.ui

import com.nanzhufeng.ai.domain.ConversationAttachmentSearchHit
import com.nanzhufeng.ai.domain.ConversationSearchHit
import com.nanzhufeng.ai.domain.GlmOcrDocumentSearchHit
import com.nanzhufeng.ai.domain.normalizedAttachmentExtension
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

internal data class SearchAttachmentMonthGroup(
    val key: String,
    val label: String,
    val hits: List<ConversationAttachmentSearchHit>,
)

/** Default preserves the established month sections; every explicit mode is one global order. */
internal enum class ConversationAttachmentSortMode(val label: String) {
    DEFAULT("默认排序"),
    TIME_DESCENDING("时间从新到旧"),
    TIME_ASCENDING("时间从旧到新"),
    SIZE_DESCENDING("大小从大到小"),
    SIZE_ASCENDING("大小从小到大"),
}

internal enum class ConversationAttachmentSortColumn { TIME, SIZE }

internal fun nextAttachmentSortMode(
    current: ConversationAttachmentSortMode,
    column: ConversationAttachmentSortColumn,
): ConversationAttachmentSortMode = when (column) {
    ConversationAttachmentSortColumn.TIME -> if (current == ConversationAttachmentSortMode.TIME_DESCENDING) {
        ConversationAttachmentSortMode.TIME_ASCENDING
    } else {
        ConversationAttachmentSortMode.TIME_DESCENDING
    }
    ConversationAttachmentSortColumn.SIZE -> if (current == ConversationAttachmentSortMode.SIZE_DESCENDING) {
        ConversationAttachmentSortMode.SIZE_ASCENDING
    } else {
        ConversationAttachmentSortMode.SIZE_DESCENDING
    }
}

/** Text hits and attachment hits share the same visible time/size controls.  The default keeps
 * the repository's relevance or browsing order; an explicit choice is a stable global order. */
internal fun sortedConversationSearchHits(
    hits: List<ConversationSearchHit>,
    sortMode: ConversationAttachmentSortMode,
): List<ConversationSearchHit> = when (sortMode) {
    ConversationAttachmentSortMode.DEFAULT -> hits
    ConversationAttachmentSortMode.TIME_DESCENDING -> hits.sortedWith(
        compareByDescending<ConversationSearchHit> { it.timestampEpochMs }.thenBySearchTextIdentity(),
    )
    ConversationAttachmentSortMode.TIME_ASCENDING -> hits.sortedWith(
        compareBy<ConversationSearchHit> { it.timestampEpochMs }.thenBySearchTextIdentity(),
    )
    ConversationAttachmentSortMode.SIZE_DESCENDING -> hits.sortedWith(
        compareByDescending<ConversationSearchHit> { it.byteCount }
            .thenByDescending { it.timestampEpochMs }
            .thenBySearchTextIdentity(),
    )
    ConversationAttachmentSortMode.SIZE_ASCENDING -> hits.sortedWith(
        compareBy<ConversationSearchHit> { it.byteCount }
            .thenByDescending { it.timestampEpochMs }
            .thenBySearchTextIdentity(),
    )
}

/** File-tab refinement only. Other attachment categories retain their existing exact owners. */
internal enum class ConversationAttachmentFileType(val label: String) {
    ALL("全部类型"),
    MARKDOWN("MD"),
    PDF("PDF"),
    ZIP("ZIP"),
    DOCX("DOCX"),
    TXT("TXT"),
    JSON("JSON"),
    OTHER("其他"),
}

internal fun filterSearchFileType(
    hits: List<ConversationAttachmentSearchHit>,
    type: ConversationAttachmentFileType,
): List<ConversationAttachmentSearchHit> = if (type == ConversationAttachmentFileType.ALL) hits else hits.filter {
    it.searchFileType() == type
}

internal fun filterGlmOcrSearchFileType(
    hits: List<GlmOcrDocumentSearchHit>,
    type: ConversationAttachmentFileType,
): List<GlmOcrDocumentSearchHit> = if (type == ConversationAttachmentFileType.ALL) hits else hits.filter {
    searchFileType(it.attachment.mimeType, it.attachment.displayName) == type
}

private fun ConversationAttachmentSearchHit.searchFileType(): ConversationAttachmentFileType =
    searchFileType(attachment.mimeType, attachment.displayName)

private fun searchFileType(mimeType: String, displayName: String?): ConversationAttachmentFileType {
    val extension = normalizedAttachmentExtension(displayName)
    return when {
        mimeType == "text/markdown" || extension in setOf("md", "markdown") -> ConversationAttachmentFileType.MARKDOWN
        mimeType == "application/pdf" || extension == "pdf" -> ConversationAttachmentFileType.PDF
        mimeType in setOf("application/zip", "application/x-zip-compressed") || extension == "zip" -> ConversationAttachmentFileType.ZIP
        mimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" || extension == "docx" -> ConversationAttachmentFileType.DOCX
        mimeType == "application/json" || extension == "json" -> ConversationAttachmentFileType.JSON
        mimeType == "text/plain" || extension == "txt" -> ConversationAttachmentFileType.TXT
        else -> ConversationAttachmentFileType.OTHER
    }
}

internal fun attachmentSearchMonthGroups(
    hits: List<ConversationAttachmentSearchHit>,
): List<SearchAttachmentMonthGroup> = hits
    .sortedByDescending { it.timestampEpochMs }
    .groupBy { hit ->
        val date = Instant.ofEpochMilli(hit.timestampEpochMs).atZone(ZoneId.systemDefault())
        "%04d-%02d".format(Locale.ROOT, date.year, date.monthValue)
    }
    .entries
    .sortedByDescending { it.key }
    .map { (key, groupedHits) ->
        val year = key.substringBefore('-')
        val month = key.substringAfter('-').toInt()
        SearchAttachmentMonthGroup(key, "${year}年${month}月", groupedHits)
    }

internal fun sortedSearchAttachmentHits(
    hits: List<ConversationAttachmentSearchHit>,
    sortMode: ConversationAttachmentSortMode,
): List<ConversationAttachmentSearchHit> = when (sortMode) {
    ConversationAttachmentSortMode.DEFAULT -> attachmentSearchMonthGroups(hits).flatMap(SearchAttachmentMonthGroup::hits)
    ConversationAttachmentSortMode.TIME_DESCENDING -> hits.sortedWith(
        compareByDescending<ConversationAttachmentSearchHit> { it.timestampEpochMs }.thenBySearchAttachmentIdentity(),
    )
    ConversationAttachmentSortMode.TIME_ASCENDING -> hits.sortedWith(
        compareBy<ConversationAttachmentSearchHit> { it.timestampEpochMs }.thenBySearchAttachmentIdentity(),
    )
    ConversationAttachmentSortMode.SIZE_DESCENDING -> hits.sortedWith(
        compareByDescending<ConversationAttachmentSearchHit> { it.attachment.byteCount }
            .thenByDescending { it.timestampEpochMs }
            .thenBySearchAttachmentIdentity(),
    )
    ConversationAttachmentSortMode.SIZE_ASCENDING -> hits.sortedWith(
        compareBy<ConversationAttachmentSearchHit> { it.attachment.byteCount }
            .thenByDescending { it.timestampEpochMs }
            .thenBySearchAttachmentIdentity(),
    )
}

/** OCR documents and conversation attachments share the same explicit global order.  Keeping
 * the owner-specific location metadata in this sealed entry avoids inventing fake conversations. */
internal sealed interface UnifiedSearchAttachmentEntry {
    val attachment: com.nanzhufeng.ai.domain.ConversationAttachmentReference
    val timestampEpochMs: Long
    val stableKey: String

    data class Conversation(val hit: ConversationAttachmentSearchHit) : UnifiedSearchAttachmentEntry {
        override val attachment = hit.attachment
        override val timestampEpochMs = hit.timestampEpochMs
        override val stableKey = "attachment:${hit.conversationId.value}:${hit.messageNodeId.value}:${hit.attachment.id.value}"
    }

    data class GlmOcr(val hit: GlmOcrDocumentSearchHit) : UnifiedSearchAttachmentEntry {
        override val attachment = hit.attachment
        override val timestampEpochMs = hit.timestampEpochMs
        override val stableKey = "ocr:${hit.taskId.value}:${hit.kind}:${hit.attachment.id.value}"
    }
}

internal data class UnifiedSearchAttachmentMonthGroup(
    val key: String,
    val label: String,
    val entries: List<UnifiedSearchAttachmentEntry>,
)

/** Default timeline ownership is based only on each file's real timestamp. OCR lineage changes
 * actions and navigation, never the visible time section. */
internal fun unifiedSearchAttachmentMonthGroups(
    conversationHits: List<ConversationAttachmentSearchHit>,
    glmOcrHits: List<GlmOcrDocumentSearchHit>,
): List<UnifiedSearchAttachmentMonthGroup> =
    sortedUnifiedSearchAttachmentEntries(conversationHits, glmOcrHits, ConversationAttachmentSortMode.DEFAULT)
        .groupBy { entry ->
            val date = Instant.ofEpochMilli(entry.timestampEpochMs).atZone(ZoneId.systemDefault())
            "%04d-%02d".format(Locale.ROOT, date.year, date.monthValue)
        }
        .entries
        .sortedByDescending { it.key }
        .map { (key, entries) ->
            val year = key.substringBefore('-')
            val month = key.substringAfter('-').toInt()
            UnifiedSearchAttachmentMonthGroup(key, "${year}年${month}月", entries)
        }

internal fun sortedUnifiedSearchAttachmentEntries(
    conversationHits: List<ConversationAttachmentSearchHit>,
    glmOcrHits: List<GlmOcrDocumentSearchHit>,
    sortMode: ConversationAttachmentSortMode,
): List<UnifiedSearchAttachmentEntry> {
    val entries = conversationHits.map { UnifiedSearchAttachmentEntry.Conversation(it) } + glmOcrHits.map { UnifiedSearchAttachmentEntry.GlmOcr(it) }
    return when (sortMode) {
        ConversationAttachmentSortMode.DEFAULT,
        ConversationAttachmentSortMode.TIME_DESCENDING -> entries.sortedWith(compareByDescending<UnifiedSearchAttachmentEntry> { it.timestampEpochMs }.thenBy { it.stableKey })
        ConversationAttachmentSortMode.TIME_ASCENDING -> entries.sortedWith(compareBy<UnifiedSearchAttachmentEntry> { it.timestampEpochMs }.thenBy { it.stableKey })
        ConversationAttachmentSortMode.SIZE_DESCENDING -> entries.sortedWith(compareByDescending<UnifiedSearchAttachmentEntry> { it.attachment.byteCount }.thenByDescending { it.timestampEpochMs }.thenBy { it.stableKey })
        ConversationAttachmentSortMode.SIZE_ASCENDING -> entries.sortedWith(compareBy<UnifiedSearchAttachmentEntry> { it.attachment.byteCount }.thenByDescending { it.timestampEpochMs }.thenBy { it.stableKey })
    }
}

private fun Comparator<ConversationAttachmentSearchHit>.thenBySearchAttachmentIdentity(): Comparator<ConversationAttachmentSearchHit> =
    thenBy { it.conversationId.value }
        .thenBy { it.messageNodeId.value }
        .thenBy { it.attachment.id.value }

private fun Comparator<ConversationSearchHit>.thenBySearchTextIdentity(): Comparator<ConversationSearchHit> =
    thenBy { it.conversationId.value }
        .thenBy { it.messageNodeId?.value.orEmpty() }

/** Lazy item indices include section headings; these resolvers keep an attachment as the
 * fallback anchor if the result set changes while its owner conversation is open. */
internal fun searchAllAttachmentItemIndex(
    state: ConversationFoundationUiState,
    attachmentId: String,
    conversationId: String? = null,
    messageNodeId: String? = null,
    sortMode: ConversationAttachmentSortMode = ConversationAttachmentSortMode.DEFAULT,
): Int? {
    var itemIndex = 0
    if (state.searchResults.isNotEmpty()) itemIndex += 1 + state.searchResults.size
    if (sortMode != ConversationAttachmentSortMode.DEFAULT) {
        if (state.attachmentSearchResults.isNotEmpty() || state.glmOcrSearchResults.isNotEmpty()) itemIndex += 1
        val sortedIndex = sortedUnifiedSearchAttachmentEntries(state.attachmentSearchResults, state.glmOcrSearchResults, sortMode)
            .indexOfFirst { entry ->
                entry is UnifiedSearchAttachmentEntry.Conversation && entry.hit.matchesSearchAttachmentTarget(attachmentId, conversationId, messageNodeId)
            }
        return sortedIndex.takeIf { it >= 0 }?.let(itemIndex::plus)
    }
    if (state.attachmentSearchResults.isNotEmpty() || state.glmOcrSearchResults.isNotEmpty()) itemIndex += 1
    unifiedSearchAttachmentMonthGroups(state.attachmentSearchResults, state.glmOcrSearchResults).forEach { group ->
        itemIndex += 1
        group.entries.forEach { entry ->
            if (entry is UnifiedSearchAttachmentEntry.Conversation && entry.hit.matchesSearchAttachmentTarget(attachmentId, conversationId, messageNodeId)) return itemIndex
            itemIndex += 1
        }
    }
    return null
}

internal fun searchGridAttachmentItemIndex(
    hits: List<ConversationAttachmentSearchHit>,
    attachmentId: String,
    conversationId: String? = null,
    messageNodeId: String? = null,
    sortMode: ConversationAttachmentSortMode = ConversationAttachmentSortMode.DEFAULT,
    glmOcrHits: List<GlmOcrDocumentSearchHit> = emptyList(),
): Int? {
    var itemIndex = 1 // attachment-total
    if (sortMode != ConversationAttachmentSortMode.DEFAULT) {
        val sortedIndex = sortedUnifiedSearchAttachmentEntries(hits, glmOcrHits, sortMode)
            .indexOfFirst { entry ->
                entry is UnifiedSearchAttachmentEntry.Conversation && entry.hit.matchesSearchAttachmentTarget(attachmentId, conversationId, messageNodeId)
            }
        return sortedIndex.takeIf { it >= 0 }?.let(itemIndex::plus)
    }
    unifiedSearchAttachmentMonthGroups(hits, glmOcrHits).forEach { group ->
        itemIndex += 1
        group.entries.forEach { entry ->
            if (entry is UnifiedSearchAttachmentEntry.Conversation && entry.hit.matchesSearchAttachmentTarget(attachmentId, conversationId, messageNodeId)) return itemIndex
            itemIndex += 1
        }
    }
    return null
}

private fun ConversationAttachmentSearchHit.matchesSearchAttachmentTarget(
    attachmentId: String,
    conversationId: String?,
    messageNodeId: String?,
): Boolean = attachment.id.value == attachmentId &&
    (conversationId == null || this.conversationId.value == conversationId) &&
    (messageNodeId == null || this.messageNodeId.value == messageNodeId)

package com.nanzhufeng.ai.data.local

/** Lightweight read models keep search and attachment catalogues out of full snapshot rebuilds. */
data class ConversationSearchBrowseRow(
    val conversationId: String,
    val title: String,
    val snippet: String,
    val timestampEpochMs: Long,
)

data class ConversationPathTextSearchRow(
    val conversationId: String,
    val messageNodeId: String,
    val title: String,
    val text: String,
    val timestampEpochMs: Long,
)

data class ConversationPathAttachmentRow(
    val conversationId: String,
    val messageNodeId: String,
    val title: String,
    val createdAtEpochMs: Long,
    val attachmentId: String?,
    val storageKey: String?,
    val mimeType: String?,
    val displayName: String?,
    val byteCount: Long?,
    val sha256: String?,
    val messageText: String,
)

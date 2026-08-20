package com.nanzhufeng.ai.domain

import android.net.Uri

sealed interface ConversationExchangeExportResult {
    data class Exported(val sha256: String, val byteCount: Long, val semanticHash: String) : ConversationExchangeExportResult
    data class Rejected(val reason: String) : ConversationExchangeExportResult
    data class Failed(val reason: String) : ConversationExchangeExportResult
}

/** Android UI reaches the semantic owner only through this explicit SAF output port. */
interface ConversationExchangeExportPort {
    fun export(conversationId: ConversationId, destination: Uri): ConversationExchangeExportResult
}

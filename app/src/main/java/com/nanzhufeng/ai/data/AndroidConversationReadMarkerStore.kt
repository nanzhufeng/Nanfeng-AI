package com.nanzhufeng.ai.data

import android.content.Context
import com.nanzhufeng.ai.domain.ConversationId
import com.nanzhufeng.ai.domain.ConversationReadMarkerStore

/** Stores only opaque conversation IDs and update watermarks; no title, message body or Provider data. */
class AndroidConversationReadMarkerStore(context: Context) : ConversationReadMarkerStore {
    private val preferences = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun isInitialized(): Boolean = preferences.getBoolean(INITIALIZED, false)

    override fun markInitialized() {
        preferences.edit().putBoolean(INITIALIZED, true).apply()
    }

    override fun lastReadAtEpochMs(conversationId: ConversationId): Long? =
        preferences.getLong(readKey(conversationId), MISSING).takeIf { it != MISSING }

    override fun markRead(conversationId: ConversationId, updatedAtEpochMs: Long) {
        preferences.edit().putLong(readKey(conversationId), updatedAtEpochMs).apply()
    }

    override fun watchLaterAtEpochMs(conversationId: ConversationId): Long? =
        preferences.getLong(watchLaterKey(conversationId), MISSING).takeIf { it != MISSING }

    override fun markWatchLater(conversationId: ConversationId, markedAtEpochMs: Long) {
        preferences.edit().putLong(watchLaterKey(conversationId), markedAtEpochMs).apply()
    }

    override fun clearWatchLater(conversationId: ConversationId) {
        preferences.edit().remove(watchLaterKey(conversationId)).apply()
    }

    private fun readKey(conversationId: ConversationId) = "read_at_${conversationId.value}"
    private fun watchLaterKey(conversationId: ConversationId) = "watch_later_at_${conversationId.value}"

    private companion object {
        const val FILE = "conversation_read_markers_v1"
        const val INITIALIZED = "initialized"
        const val MISSING = Long.MIN_VALUE
    }
}

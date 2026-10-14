package com.nanzhufeng.ai.data

import android.content.SharedPreferences
import com.nanzhufeng.ai.domain.ConversationAppEntryPolicy
import com.nanzhufeng.ai.domain.ConversationId

/** Navigation metadata only. Call from IO before a manual choice is shown or generation starts. */
class AndroidConversationEntryCheckpoint(private val preferences: SharedPreferences) {
    fun remember(conversationId: ConversationId, hadRunningGeneration: Boolean, nowMillis: Long = System.currentTimeMillis()): Boolean =
        preferences.edit()
            .putString(LAST_CONVERSATION_ID, conversationId.value)
            .putBoolean(HAD_RUNNING_GENERATION, hadRunningGeneration)
            .putLong(LAST_BACKGROUND_AT_MILLIS, nowMillis)
            .commit()

    fun retainedId(nowMillis: Long): ConversationId? = ConversationAppEntryPolicy.retainedId(
        preferences.getString(LAST_CONVERSATION_ID, null),
        preferences.getLong(LAST_BACKGROUND_AT_MILLIS, 0L), nowMillis,
        preferences.getBoolean(HAD_RUNNING_GENERATION, false),
    )

    companion object {
        const val LAST_CONVERSATION_ID = "last_conversation_id"
        const val HAD_RUNNING_GENERATION = "had_running_generation"
        const val LAST_BACKGROUND_AT_MILLIS = "last_background_at_millis"
    }
}

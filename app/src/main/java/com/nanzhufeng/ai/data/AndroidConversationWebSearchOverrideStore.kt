package com.nanzhufeng.ai.data

import android.content.Context
import com.nanzhufeng.ai.domain.ConversationId
import com.nanzhufeng.ai.domain.ConversationWebSearchOverride
import com.nanzhufeng.ai.domain.ConversationWebSearchOverrideStore

/** App-private, content-free per-conversation preference; it never stores prompts or sources. */
class AndroidConversationWebSearchOverrideStore(context: Context) : ConversationWebSearchOverrideStore {
    private val prefs = context.applicationContext.getSharedPreferences(
        "conversation-web-search-overrides-v1",
        Context.MODE_PRIVATE,
    )

    override fun read(conversationId: ConversationId): ConversationWebSearchOverride {
        val prefix = "conversation.${conversationId.value}"
        return ConversationWebSearchOverride(
            conversationId = conversationId,
            revision = prefs.getLong("$prefix.revision", 0),
            enabled = prefs.takeIf { it.contains("$prefix.enabled") }?.getBoolean("$prefix.enabled", false),
        )
    }

    override fun save(value: ConversationWebSearchOverride): Boolean {
        val prefix = "conversation.${value.conversationId.value}"
        return prefs.edit()
            .putLong("$prefix.revision", value.revision)
            .apply {
                if (value.enabled == null) remove("$prefix.enabled") else putBoolean("$prefix.enabled", value.enabled)
            }
            .commit()
    }
}

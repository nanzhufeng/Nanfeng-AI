package com.nanzhufeng.ai.data

import android.annotation.SuppressLint
import android.content.Context
import com.nanzhufeng.ai.domain.ConversationId
import com.nanzhufeng.ai.domain.ConversationStyle
import com.nanzhufeng.ai.domain.ConversationStyleOverride
import com.nanzhufeng.ai.domain.ConversationStyleOverrideStore

/** App-private per-conversation style state; it stores no prompt or answer content. */
class AndroidConversationStyleOverrideStore(context: Context) : ConversationStyleOverrideStore {
    private val prefs = context.applicationContext.getSharedPreferences(
        "conversation-style-overrides-v1",
        Context.MODE_PRIVATE,
    )

    override fun read(conversationId: ConversationId): ConversationStyleOverride {
        val prefix = "conversation.${conversationId.value}"
        return ConversationStyleOverride(
            conversationId = conversationId,
            revision = prefs.getLong("$prefix.revision", 0),
            style = ConversationStyle.fromPersistedIdOrNull(prefs.getString("$prefix.style", null)),
        )
    }

    @SuppressLint("UseKtx") // The KTX helper discards commit(), but this contract must report persistence failure.
    override fun save(value: ConversationStyleOverride): Boolean {
        val prefix = "conversation.${value.conversationId.value}"
        return prefs.edit()
            .putLong("$prefix.revision", value.revision)
            .apply {
                if (value.style == null) remove("$prefix.style") else putString("$prefix.style", value.style.persistedId)
            }
            .commit()
    }
}

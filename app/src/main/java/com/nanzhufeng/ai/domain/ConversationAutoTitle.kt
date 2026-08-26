package com.nanzhufeng.ai.domain

/**
 * A freshly created chat remains "新对话" until the dedicated, constrained title refinement
 * finishes.  Local sentence/heading extraction was deliberately removed: response sections are
 * not a reliable summary of the conversation, and must never overwrite the drawer title.
 */
object ConversationAutoTitle {
    const val NEW_CONVERSATION_TITLE = "新对话"

    /** Kept only as a compatibility seam for the conversation tree and runtime state machine. */
    fun titleForFirstCompletedAssistantReply(snapshot: ConversationSnapshot, assistantMessageId: MessageNodeId): String? = null

}

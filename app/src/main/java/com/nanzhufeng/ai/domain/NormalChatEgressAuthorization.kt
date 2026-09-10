package com.nanzhufeng.ai.domain

import java.security.MessageDigest

/**
 * The send affordance is the sole approval action for an ordinary hosted-model request.
 * It binds that explicit action to the exact local draft without retaining its text or bytes.
 */
data class NormalChatEgressAuthorization(
    val approvedAtEpochMs: Long,
    val draftFingerprint: String,
    val disclosureVersion: String = DISCLOSURE_VERSION,
) {
    init {
        require(approvedAtEpochMs > 0L)
        require(draftFingerprint.matches(Regex("[0-9a-f]{64}")))
        require(disclosureVersion == DISCLOSURE_VERSION)
    }

    fun matches(message: MessageNode): Boolean = draftFingerprint == fingerprint(
        message.content.filterIsInstance<ContentBlock.Text>().joinToString("") { it.text },
        message.content.filterIsInstance<ContentBlock.Attachment>().map { it.attachment },
    )

    companion object {
        const val DISCLOSURE_VERSION = "normal-chat-egress-v1"

        fun forUserSend(draft: ConversationDraft, approvedAtEpochMs: Long): NormalChatEgressAuthorization =
            NormalChatEgressAuthorization(approvedAtEpochMs, fingerprint(draft.text, draft.attachments))

        private fun fingerprint(text: String, attachments: List<ConversationAttachmentReference>): String {
            val safeAttachments = attachments.sortedBy { it.id.value }.joinToString("|") {
                "${it.id.value}:${it.sha256}:${it.mimeType}:${it.byteCount}"
            }
            return MessageDigest.getInstance("SHA-256")
                .digest("$DISCLOSURE_VERSION\\n$text\\n$safeAttachments".toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        }
    }
}

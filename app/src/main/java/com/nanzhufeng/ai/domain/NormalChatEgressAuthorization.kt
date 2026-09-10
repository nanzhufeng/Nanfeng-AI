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
    val recipientChoiceId: String,
    val recipientPresets: List<ModelPresetId>,
    val recipientModelIds: Map<ModelPresetId, String>,
) {
    init {
        require(approvedAtEpochMs > 0L)
        require(draftFingerprint.matches(Regex("[0-9a-f]{64}")))
        require(disclosureVersion == DISCLOSURE_VERSION)
        require(recipientChoiceId.isNotBlank() && recipientPresets.isNotEmpty())
        require(recipientModelIds.keys == recipientPresets.toSet() && recipientModelIds.values.all { it.isNotBlank() })
    }

    fun matches(draft: ConversationDraft): Boolean = draftFingerprint == fingerprint(
        draft.text, draft.attachments, recipientChoiceId, recipientPresets, recipientModelIds,
    )

    fun matches(message: MessageNode): Boolean = draftFingerprint == fingerprint(
        message.content.filterIsInstance<ContentBlock.Text>().joinToString("") { it.text },
        message.content.filterIsInstance<ContentBlock.Attachment>().map { it.attachment },
        recipientChoiceId, recipientPresets, recipientModelIds,
    )

    fun allowsRecipient(preset: ModelPresetId, providerId: ProviderId, modelId: String): Boolean =
        preset in recipientPresets && NanfengModelServiceCatalog.providerFor(preset) == providerId && recipientModelIds[preset] == modelId

    companion object {
        const val DISCLOSURE_VERSION = "normal-chat-egress-v2"
        const val LEGACY_DISCLOSURE_VERSION = "normal-chat-egress-v1"

        fun forUserSend(draft: ConversationDraft, approvedAtEpochMs: Long, selectedModelId: String?, routing: NormalChatRoutingSnapshot): NormalChatEgressAuthorization {
            val choiceId = selectedModelId ?: ComposerModelRoutingCatalog.auto.id
            val presets = routing.presets(draft, selectedModelId)
            val modelIds = presets.associateWith { requireNotNull(routing.models[it]).modelId }
            return NormalChatEgressAuthorization(approvedAtEpochMs,
                fingerprint(draft.text, draft.attachments, choiceId, presets, modelIds),
                recipientChoiceId = choiceId, recipientPresets = presets, recipientModelIds = modelIds)
        }

        private fun fingerprint(text: String, attachments: List<ConversationAttachmentReference>, choiceId: String, presets: List<ModelPresetId>, modelIds: Map<ModelPresetId, String>): String {
            val safeAttachments = attachments.sortedBy { it.id.value }.joinToString("|") {
                "${it.id.value}:${it.sha256}:${it.mimeType}:${it.byteCount}"
            }
            return MessageDigest.getInstance("SHA-256")
                .digest("$DISCLOSURE_VERSION\\n$choiceId\\n${presets.joinToString { NanfengModelServiceCatalog.providerFor(it).name + ":" + it.name + ":" + modelIds[it] }}\\n$text\\n$safeAttachments".toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        }
    }
}

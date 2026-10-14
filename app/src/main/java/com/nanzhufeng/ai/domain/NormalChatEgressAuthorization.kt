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
    val auxiliaryRecipientModelIds: Map<ModelPresetId, String> = emptyMap(),
    val auxiliaryServiceEngines: Map<String,String> = emptyMap(),
) {
    init {
        require(approvedAtEpochMs > 0L)
        require(draftFingerprint.matches(Regex("[0-9a-f]{64}")))
        require(disclosureVersion == DISCLOSURE_VERSION)
        require(recipientChoiceId.isNotBlank() && recipientPresets.isNotEmpty())
        require(auxiliaryRecipientModelIds.keys.none { it in recipientPresets } && auxiliaryRecipientModelIds.values.all(String::isNotBlank))
        require(recipientModelIds.keys == recipientPresets.toSet() && recipientModelIds.values.all { it.isNotBlank() })
        require(auxiliaryServiceEngines.isEmpty() || auxiliaryServiceEngines == mapOf(IndependentSearchServiceGrant.SERVICE_ID to IndependentSearchServiceGrant.ENGINE_ID))
    }

    fun matches(draft: ConversationDraft): Boolean = draftFingerprint == fingerprint(
        draft.text, draft.attachments, recipientChoiceId, recipientPresets, recipientModelIds, auxiliaryRecipientModelIds,auxiliaryServiceEngines,
    )

    fun matches(message: MessageNode): Boolean = draftFingerprint == fingerprint(
        message.content.filterIsInstance<ContentBlock.Text>().joinToString("") { it.text },
        message.content.filterIsInstance<ContentBlock.Attachment>().map { it.attachment },
        recipientChoiceId, recipientPresets, recipientModelIds, auxiliaryRecipientModelIds,auxiliaryServiceEngines,
    )

    fun allowsRecipient(preset: ModelPresetId, providerId: ProviderId, modelId: String): Boolean =
        NanfengModelServiceCatalog.providerFor(preset) == providerId && (recipientModelIds[preset] ?: auxiliaryRecipientModelIds[preset]) == modelId

    fun independentSearchGrant():IndependentSearchServiceGrant? = auxiliaryServiceEngines[IndependentSearchServiceGrant.SERVICE_ID]?.let {
        IndependentSearchServiceGrant(disclosureVersion,IndependentSearchServiceGrant.SERVICE_ID,it,draftFingerprint)
    }

    companion object {
        const val DISCLOSURE_VERSION = "normal-chat-egress-v3"
        const val LEGACY_DISCLOSURE_VERSION = "normal-chat-egress-v1"

        fun forUserSend(draft: ConversationDraft, approvedAtEpochMs: Long, selectedModelId: String?, routing: NormalChatRoutingSnapshot): NormalChatEgressAuthorization {
            val choiceId = ComposerModelRoutingCatalog.currentChoiceId(selectedModelId) ?: ComposerModelRoutingCatalog.auto.id
            val presets = routing.presets(draft, selectedModelId)
            val auxiliary = routing.auxiliaryPresets(draft, selectedModelId).associateWith { requireNotNull(routing.models[it]).modelId }
            val modelIds = presets.associateWith { requireNotNull(routing.models[it]).modelId }
            val services=if(routing.usesIndependentSearch(draft)) mapOf(IndependentSearchServiceGrant.SERVICE_ID to IndependentSearchServiceGrant.ENGINE_ID) else emptyMap()
            return NormalChatEgressAuthorization(approvedAtEpochMs,
                fingerprint(draft.text, draft.attachments, choiceId, presets, modelIds, auxiliary,services),
                recipientChoiceId = choiceId, recipientPresets = presets, recipientModelIds = modelIds, auxiliaryRecipientModelIds = auxiliary,auxiliaryServiceEngines=services)
        }

        private fun fingerprint(text: String, attachments: List<ConversationAttachmentReference>, choiceId: String, presets: List<ModelPresetId>, modelIds: Map<ModelPresetId, String>, auxiliary: Map<ModelPresetId, String> = emptyMap(),services:Map<String,String> = emptyMap()): String {
            val safeAttachments = attachments.sortedBy { it.id.value }.joinToString("|") {
                "${it.id.value}:${it.sha256}:${it.mimeType}:${it.byteCount}"
            }
            val auxiliaryFact = (if (auxiliary.isEmpty()) "" else "|aux:" + auxiliary.entries.sortedBy { it.key.name }.joinToString { it.key.name + ":" + it.value }) +
                (if(services.isEmpty()) "" else "|services:"+services.entries.sortedBy {it.key}.joinToString {it.key+":"+it.value})
            return MessageDigest.getInstance("SHA-256")
                .digest("$DISCLOSURE_VERSION\\n$choiceId\\n${presets.joinToString { NanfengModelServiceCatalog.providerFor(it).name + ":" + it.name + ":" + modelIds[it] }}$auxiliaryFact\\n$text\\n$safeAttachments".toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        }
    }
}

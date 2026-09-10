package com.nanzhufeng.ai.domain

/** Local metadata only. Load off the UI thread, then share this exact view between disclosure and click. */
data class NormalChatRoutingSnapshot(
    val models: Map<ModelPresetId, ResolvedModel>,
    val usablePresets: Set<ModelPresetId>,
    val autoRoutingEnabled: Boolean,
) {
    fun presets(draft: ConversationDraft, selectedModelId: String?): List<ModelPresetId> {
        val choice = ComposerModelRoutingCatalog.choice(selectedModelId)
        if (choice != ComposerModelRoutingCatalog.auto || !autoRoutingEnabled) return choice.routes.toList()
        val mimeTypes = draft.attachments.map { it.mimeType }
        val facts = AutoRoutingFacts(
            hasAttachment = mimeTypes.isNotEmpty(),
            requiresImage = mimeTypes.any { it.startsWith("image/") },
            requiresPdf = "application/pdf" in mimeTypes,
            requiresVideo = mimeTypes.any { it.startsWith("video/") },
            requiresAudio = mimeTypes.any { it.startsWith("audio/") },
            requiresFile = mimeTypes.any { it != "application/pdf" && !it.startsWith("image/") && !it.startsWith("video/") && !it.startsWith("audio/") },
            requiresComplexReasoning = AutoRoutingTaskClassifier.requiresComplexReasoning(draft.text),
        )
        val resolver = ModelResolver { preset -> models[preset]?.let(ResolvedModelResult::Resolved)
            ?: ResolvedModelResult.Unavailable("No local profile") }
        return listOf(CapabilityAwareAutoModelRouter(resolver).resolve(facts) { it in usablePresets })
    }

    fun canAuthorize(draft: ConversationDraft, selectedModelId: String?): Boolean =
        presets(draft, selectedModelId).all { models[it]?.modelId?.isNotBlank() == true }
}

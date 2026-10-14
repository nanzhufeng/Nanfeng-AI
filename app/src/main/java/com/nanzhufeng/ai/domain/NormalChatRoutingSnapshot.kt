package com.nanzhufeng.ai.domain

/** Local metadata only. Load off the UI thread, then share this exact view between disclosure and click. */
data class NormalChatRoutingSnapshot(
    val models: Map<ModelPresetId, ResolvedModel>,
    val usablePresets: Set<ModelPresetId>,
    val autoRoutingEnabled: Boolean,
    val webSearchEnabled: Boolean = false,
    val enabledProviders: Set<ProviderId> = ProviderId.entries.toSet(),
    val independentSearchAvailable:Boolean = false,
) {
    fun usesIndependentSearch(draft:ConversationDraft):Boolean=false // Legacy service history remains readable; new sends use deployment routes.
    fun isVisible(choice: ComposerModelChoice): Boolean = choice.routes.all { NanfengModelServiceCatalog.providerFor(it) in enabledProviders }
    fun presets(draft: ConversationDraft, selectedModelId: String?): List<ModelPresetId> {
        val choice = ComposerModelRoutingCatalog.choice(selectedModelId)
        if (choice != ComposerModelRoutingCatalog.auto) return choice.routes.toList()
        val mimeTypes = draft.attachments.map { it.mimeType }
        val facts = AutoRoutingFacts(
            hasAttachment = autoRoutingEnabled && mimeTypes.isNotEmpty(),
            requiresImage = mimeTypes.any { it.startsWith("image/") },
            requiresPdf = "application/pdf" in mimeTypes,
            requiresVideo = mimeTypes.any { it.startsWith("video/") },
            requiresAudio = mimeTypes.any { it.startsWith("audio/") },
            requiresFile = mimeTypes.any { it != "application/pdf" && !it.startsWith("image/") && !it.startsWith("video/") && !it.startsWith("audio/") },
            requiresComplexReasoning = autoRoutingEnabled && AutoRoutingTaskClassifier.requiresComplexReasoning(draft.text),
        )
        val resolver = ModelResolver { preset -> models[preset]?.let(ResolvedModelResult::Resolved)
            ?: ResolvedModelResult.Unavailable("No local profile") }
        return listOf(CapabilityAwareAutoModelRouter(resolver).resolve(facts) { preset ->
            preset in usablePresets && (!webSearchEnabled || models[preset]?.providerId == ProviderId.OPENROUTER ||
                mimeTypes.none { it.startsWith("image/") || it.startsWith("audio/") || it.startsWith("video/") }) && (!RelayCoreModels.needsCompanion(preset, webSearchEnabled, draft.attachments.isNotEmpty()) ||
                (usesIndependentSearch(draft) || ModelPresetId.GPT_6_LUNA in usablePresets && models[ModelPresetId.GPT_6_LUNA]?.health != ModelHealth.UNAVAILABLE))
        })
    }

    fun autoLabel(draft: ConversationDraft): String {
        if (!canAuthorize(draft, null)) return "Auto · 等待可用模型"
        val preset = presets(draft, null).single()
        val name = RelayCoreModels.spec(preset)?.let { "${it.uiName} · RC" }
            ?: NanfengModelServiceCatalog.preset(preset).displayName
        return "Auto · $name"
    }

    fun auxiliaryPresets(draft: ConversationDraft, selectedModelId: String?): List<ModelPresetId> =
        if (!usesIndependentSearch(draft) && webSearchEnabled && presets(draft, selectedModelId).any { RelayCoreModels.needsCompanion(it, true, draft.attachments.isNotEmpty()) }) listOf(ModelPresetId.GPT_6_LUNA) else emptyList()

    fun canAuthorize(draft: ConversationDraft, selectedModelId: String?): Boolean =
        (presets(draft, selectedModelId) + auxiliaryPresets(draft, selectedModelId)).all { it in usablePresets && models[it]?.modelId?.isNotBlank() == true && models[it]?.health != ModelHealth.UNAVAILABLE }
}

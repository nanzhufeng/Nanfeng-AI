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
        val resolver = ModelResolver { preset -> models[preset]?.let { model ->
            // Auto preference reflects the active transport, not the offline model's marketing capability.
            val projected=model.copy(capabilities=model.capabilities.copy(
                supportsVision=AttachmentInputPolicy.acceptsNative(model,"image/png",webSearchEnabled && model.providerId!=ProviderId.OPENROUTER),
                supportsPdf=AttachmentInputPolicy.acceptsNative(model,"application/pdf",webSearchEnabled && model.providerId!=ProviderId.OPENROUTER),
                supportsVideo=AttachmentInputPolicy.acceptsNative(model,"video/mp4",webSearchEnabled && model.providerId!=ProviderId.OPENROUTER),
                supportsAudio=AttachmentInputPolicy.acceptsNative(model,"audio/wav",webSearchEnabled && model.providerId!=ProviderId.OPENROUTER)))
            ResolvedModelResult.Resolved(projected)
        } ?: ResolvedModelResult.Unavailable("No local profile") }
        return listOf(CapabilityAwareAutoModelRouter(resolver).resolve(facts) { preset ->
            preset in usablePresets && AttachmentInputPolicy.readers(models[preset],mimeTypes,webSearchEnabled).all {it.preset in usablePresets && models[it.preset]?.health!=ModelHealth.UNAVAILABLE} &&
                (!RelayCoreModels.needsCompanion(preset,webSearchEnabled,draft.attachments.isNotEmpty()) || ModelPresetId.GPT_6_LUNA in usablePresets && models[ModelPresetId.GPT_6_LUNA]?.health!=ModelHealth.UNAVAILABLE)
        })
    }

    fun autoLabel(draft: ConversationDraft): String {
        if (!canAuthorize(draft, null)) return "Auto · 等待可用模型"
        val preset = presets(draft, null).single()
        val name = RelayCoreModels.spec(preset)?.let { "${it.uiName} · RC" }
            ?: NanfengModelServiceCatalog.preset(preset).displayName
        return "Auto · $name"
    }

    fun auxiliaryPresets(draft: ConversationDraft, selectedModelId: String?): List<ModelPresetId> {
        val selected = presets(draft, selectedModelId)
        val mimes=draft.attachments.map {it.mimeType}
        val readers=selected.flatMap {AttachmentInputPolicy.readers(models[it],mimes,webSearchEnabled)}.distinct()
        val search = !usesIndependentSearch(draft) && webSearchEnabled && selected.any { RelayCoreModels.needsCompanion(it, true, draft.attachments.isNotEmpty()) }
        return buildList {
            readers.filter { reader -> mimes.any {it!="application/pdf" && AttachmentInputPolicy.readerFor(it)==reader} || reader.preset in usablePresets }.forEach {add(it.preset)}
            if(search) add(ModelPresetId.GPT_6_LUNA)
        }.distinct().filter {it !in selected}
    }

    fun canAuthorize(draft: ConversationDraft, selectedModelId: String?): Boolean =
        (presets(draft, selectedModelId) + auxiliaryPresets(draft, selectedModelId)).all { it in usablePresets && models[it]?.modelId?.isNotBlank() == true && models[it]?.health != ModelHealth.UNAVAILABLE }
}

package com.nanzhufeng.ai.domain

/** Public platform listing, not proof of the upstream identity or a successful request. */
data class RelayCoreModelSpec(
    val preset: ModelPresetId, val provider: ProviderId, val apiId: String,
    val modelName: String, val channel: String, val multiplier: String, val slot: ComposerModelSlot,
) {
    val compactName get() = if (channel == "Gemini" && modelName.startsWith("Gemini ")) modelName else "$modelName · $channel"
    val uiName get() = if (modelName == "GPT-6 Astra") "6 Astra" else modelName
    val pickerName get() = "$uiName · ${multiplier}×"
    val composerName get() = when (modelName) {
        "GPT-6.1 Sol" -> "6.1 Sol"
        "GPT-6 Astra" -> "6 Astra"
        "Gemini 3.8 Flash" -> "Gemini 3.8"
        "Gemini 3.1 Pro" -> "Gemini 3.1"
        else -> modelName
    }
    fun pickerDetail(webSearchEnabled: Boolean): String {
        val search = when {
            !webSearchEnabled -> "未联网"
            RelayCoreModels.needsCompanion(preset, true) -> "Luna · OpenRouter 检索"
            else -> "实时联网"
        }
        return search
    }
}

object RelayCoreModels {
    val specs = listOf(
        RelayCoreModelSpec(ModelPresetId.RELAYCORE_OPUS_5_5, ProviderId.RELAYCORE_CLAUDE, "claude-opus-5.5", "Opus 5.5", "Kiro正价", "0.35", ComposerModelSlot.DEEP),
        RelayCoreModelSpec(ModelPresetId.RELAYCORE_SONNET_5_5, ProviderId.RELAYCORE_CLAUDE, "claude-sonnet-5-5", "Sonnet 5.5", "Kiro正价", "0.35", ComposerModelSlot.DAILY),
        RelayCoreModelSpec(ModelPresetId.RELAYCORE_GPT_6_1_SOL, ProviderId.RELAYCORE_GPT, "gpt-6.1-sol", "GPT-6.1 Sol", "Pro3", "0.25", ComposerModelSlot.DEEP),
        RelayCoreModelSpec(ModelPresetId.RELAYCORE_GPT_6_ASTRA, ProviderId.RELAYCORE_GPT, "gpt-6-astra", "GPT-6 Astra", "Pro3", "0.25", ComposerModelSlot.DEEP),
        RelayCoreModelSpec(ModelPresetId.RELAYCORE_GEMINI_3_8_FLASH, ProviderId.RELAYCORE_GEMINI, "gemini-3.8-flash", "Gemini 3.8 Flash", "Gemini", "0.28", ComposerModelSlot.DAILY),
        RelayCoreModelSpec(ModelPresetId.RELAYCORE_GEMINI_3_1_PRO, ProviderId.RELAYCORE_GEMINI, "gemini-3.1-pro", "Gemini 3.1 Pro", "Gemini", "0.28", ComposerModelSlot.DEEP),
    )
    /** Exact published Claude alias pairs only. Sonnet's configured request ID stays unchanged. */
    fun canonicalApiId(provider: ProviderId, modelId: String): String =
        if (provider != ProviderId.RELAYCORE_CLAUDE) modelId else when (modelId) {
            "claude-opus-5-5" -> "claude-opus-5.5"
            "claude-sonnet-5.5" -> "claude-sonnet-5-5"
            else -> modelId
        }
    fun sameApiModel(provider: ProviderId, left: String, right: String) =
        canonicalApiId(provider,left) == canonicalApiId(provider,right)
    val providers = specs.map { it.provider }.distinct()
    // Ordinary native search remains unverified: Opus probes returned missing sources and 504.
    // Keep protocol support separate from permission to replace the reliable ordinary route.
    private val verifiedNativeClaudeChatModels = emptySet<String>()
    fun hasNativeClaudeSearch(modelId: String?) = modelId in verifiedNativeClaudeChatModels
    fun needsCompanion(preset: ModelPresetId?, enabled: Boolean, hasAttachments:Boolean=false): Boolean = enabled &&
        specOrNull(preset)?.let { hasAttachments || it.provider in setOf(ProviderId.RELAYCORE_CLAUDE,ProviderId.RELAYCORE_GEMINI) && !hasNativeClaudeSearch(it.apiId) } == true
    private fun specOrNull(preset: ModelPresetId?) = specs.firstOrNull { it.preset == preset }
    fun spec(preset: ModelPresetId) = specs.firstOrNull { it.preset == preset }
    fun responseName(preset: ModelPresetId, returnedId: String?): String {
        val requested = requireNotNull(spec(preset))
        val actual = specs.firstOrNull { (returnedId != null && sameApiModel(requested.provider,it.apiId,returnedId)) || it.apiId.replace("-5-5", "-5.5") == returnedId }
        val name = actual?.modelName ?: returnedId?.takeIf { it.length <= 200 && it.none(Char::isISOControl) } ?: "返回模型未验证"
        return "$name · ${requested.channel}"
    }
    fun specByName(name: String): RelayCoreModelSpec? {
        val value = name.trim()
        specs.firstOrNull { value == it.compactName || value == it.pickerName || value == "${it.modelName} · ${it.multiplier}×" || value == "${it.modelName} · ${it.channel}" || value == "${it.modelName} · ${it.channel} · ${it.multiplier}×" }?.let { return it }
        val parts = value.split(" · ")
        if (parts.size in 2..3 && parts[1] in setOf("Pro1", "Pro2", "Pro3")) {
            return specs.firstOrNull { it.provider == ProviderId.RELAYCORE_GPT && it.modelName == parts[0] }
                ?.copy(channel = parts[1], multiplier = parts.getOrNull(2)?.removeSuffix("×") ?: "0.12")
        }
        if (parts.size in 2..3 && parts[1] in setOf("Kiro", "Kiro正价")) {
            return specs.firstOrNull { it.provider == ProviderId.RELAYCORE_CLAUDE && it.modelName == parts[0] }
                ?.copy(channel = parts[1], multiplier = parts.getOrNull(2)?.removeSuffix("×") ?: if (parts[1] == "Kiro") "0.08" else "0.35")
        }
        return null
    }
}

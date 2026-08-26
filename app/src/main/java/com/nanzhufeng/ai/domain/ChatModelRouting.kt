package com.nanzhufeng.ai.domain

/**
 * The five composer entries are product-level logical slots.  They are the only IDs the
 * conversation UI persists; concrete provider model IDs stay behind [NanfengModelServiceCatalog].
 */
enum class ComposerModelSlot(val label: String) {
    AUTO("自动"),
    COMPARE("对比"),
    DAILY("日常"),
    DEEP("深度"),
    MULTIMODAL("图像 / 视频 / PDF"),
}

data class ComposerModelChoice(
    val id: String,
    val slot: ComposerModelSlot,
    val label: String,
    val routes: List<ModelPresetId>,
) {
    init { require(id.isNotBlank() && label.isNotBlank() && routes.isNotEmpty()) }
    val isCompare: Boolean get() = slot == ComposerModelSlot.COMPARE
}

object ComposerModelRoutingCatalog {
    private const val PREFIX = "logical:"
    val auto = ComposerModelChoice("${PREFIX}auto", ComposerModelSlot.AUTO, "自动", listOf(ModelPresetId.GPT_5_6_TERRA))
    val compareGptClaude = ComposerModelChoice("${PREFIX}compare:gpt-claude", ComposerModelSlot.COMPARE, "GPT-5.6 Sol / Claude Opus 5", listOf(ModelPresetId.GPT_5_6_SOL, ModelPresetId.CLAUDE_OPUS_5))
    val compareClaudeQwen = ComposerModelChoice("${PREFIX}compare:claude-qwen", ComposerModelSlot.COMPARE, "Claude Opus 5 / Qwen3.8-Max", listOf(ModelPresetId.CLAUDE_OPUS_5, ModelPresetId.QWEN_3_8_MAX))
    val daily = listOf(
        ComposerModelChoice("${PREFIX}daily:claude-sonnet", ComposerModelSlot.DAILY, "Claude Sonnet 5", listOf(ModelPresetId.CLAUDE_SONNET_5)),
        ComposerModelChoice("${PREFIX}daily:gpt-terra", ComposerModelSlot.DAILY, "GPT-5.6 Terra", listOf(ModelPresetId.GPT_5_6_TERRA)),
        ComposerModelChoice("${PREFIX}daily:qwen-plus", ComposerModelSlot.DAILY, "Qwen3.7-Plus", listOf(ModelPresetId.QWEN_3_7_PLUS)),
        ComposerModelChoice("${PREFIX}daily:gemini-flash", ComposerModelSlot.DAILY, "Gemini 3.7 Flash", listOf(ModelPresetId.GEMINI_3_7_FLASH)),
    )
    val deep = listOf(
        ComposerModelChoice("${PREFIX}deep:claude-opus", ComposerModelSlot.DEEP, "Claude Opus 5", listOf(ModelPresetId.CLAUDE_OPUS_5)),
        ComposerModelChoice("${PREFIX}deep:gpt-sol", ComposerModelSlot.DEEP, "GPT-5.6 Sol", listOf(ModelPresetId.GPT_5_6_SOL)),
        ComposerModelChoice("${PREFIX}deep:qwen-max", ComposerModelSlot.DEEP, "Qwen3.8-Max", listOf(ModelPresetId.QWEN_3_8_MAX)),
        ComposerModelChoice("${PREFIX}deep:deepseek-pro", ComposerModelSlot.DEEP, "DeepSeek V4 Pro", listOf(ModelPresetId.DEEPSEEK_V4_PRO)),
    )
    val multimodal = listOf(
        ComposerModelChoice("${PREFIX}media:gemini-flash", ComposerModelSlot.MULTIMODAL, "Gemini 3.7 Flash", listOf(ModelPresetId.GEMINI_3_7_FLASH)),
        ComposerModelChoice("${PREFIX}media:qwen-plus", ComposerModelSlot.MULTIMODAL, "Qwen3.7-Plus", listOf(ModelPresetId.QWEN_3_7_PLUS)),
        ComposerModelChoice("${PREFIX}media:gpt-terra", ComposerModelSlot.MULTIMODAL, "GPT-5.6 Terra", listOf(ModelPresetId.GPT_5_6_TERRA)),
    )
    val groups: Map<ComposerModelSlot, List<ComposerModelChoice>> = mapOf(
        ComposerModelSlot.COMPARE to listOf(compareGptClaude, compareClaudeQwen),
        ComposerModelSlot.DAILY to daily,
        ComposerModelSlot.DEEP to deep,
        ComposerModelSlot.MULTIMODAL to multimodal,
    )
    val choices: List<ComposerModelChoice> = listOf(auto) + groups.values.flatten()

    fun choice(id: String?): ComposerModelChoice = choices.firstOrNull { it.id == id } ?: auto
    fun label(id: String?): String = if (id == null || id == auto.id) auto.label else choice(id).label
}

/**
 * This is deliberately pure: a caller supplies only local, already-known task facts.  It never
 * reads text, credentials or remote state in order to choose an automatic model.
 */
data class AutoRoutingFacts(
    val hasImageVideoOrPdf: Boolean = false,
    val requiresImage: Boolean = false,
    val requiresPdf: Boolean = false,
    val requiresVideo: Boolean = false,
    val requiresAudio: Boolean = false,
    /** Generic files use the provider's file/PDF capability family for preference only. */
    val requiresFile: Boolean = false,
    val knowledgeItemCount: Int = 0,
    val isComplexProjectDebug: Boolean = false,
)

object AutoModelRouter {
    fun candidates(facts: AutoRoutingFacts): List<ModelPresetId> = NanfengModelServiceCatalog.autoRoutingCandidates(facts)

    fun resolve(facts: AutoRoutingFacts): ModelPresetId = candidates(facts).first()

    fun label(facts: AutoRoutingFacts): String = "Auto · ${NanfengModelServiceCatalog.preset(resolve(facts)).displayName}"
}

/**
 * Keeps the product priority order in [AutoModelRouter], but never treats a category label as a
 * capability.  The selected route must have a resolved, currently usable model profile.
 */
class CapabilityAwareAutoModelRouter(private val resolver: ModelResolver) {
    fun resolve(facts: AutoRoutingFacts, isUsable: (ModelPresetId) -> Boolean): ModelPresetId {
        val candidates = AutoModelRouter.candidates(facts)
        val preferred = candidates.first()
        val usable = candidates.filter { preset ->
            isUsable(preset) && (resolver.resolve(preset) as? ResolvedModelResult.Resolved)?.model?.let { model ->
                model.health != ModelHealth.UNAVAILABLE && model.capabilities.supportsText
            } == true
        }
        // Prefer verified attachment capabilities, but never turn an unknown provider capability
        // into a local rejection. The selected model still receives the complete original file and
        // any provider-level incompatibility remains an explicit, visible send result.
        return usable.firstOrNull { preset ->
            (resolver.resolve(preset) as? ResolvedModelResult.Resolved)?.model?.capabilities?.let { capabilities ->
                (!facts.requiresImage || capabilities.supportsVision) &&
                    (!facts.requiresPdf || capabilities.supportsPdf) &&
                    (!facts.requiresVideo || capabilities.supportsVideo) &&
                    (!facts.requiresAudio || capabilities.supportsAudio) &&
                    (!facts.requiresFile || capabilities.supportsPdf)
            } == true
        } ?: usable.firstOrNull() ?: preferred
    }
}

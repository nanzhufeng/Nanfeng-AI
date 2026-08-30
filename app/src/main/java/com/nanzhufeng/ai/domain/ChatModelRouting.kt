package com.nanzhufeng.ai.domain

/**
 * The five composer entries are product-level logical slots.  They are the only IDs the
 * conversation UI persists; concrete provider model IDs stay behind [NanfengModelServiceCatalog].
 */
enum class ComposerModelSlot(val label: String) {
    AUTO("自动"),
    /** Legacy persisted slot; no longer exposed by the model picker. */
    COMPARE("对比"),
    DAILY("日常"),
    DEEP("深度"),
    /** Legacy persisted slot; automatic capability routing still handles attachments. */
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
    val auto = ComposerModelChoice("${PREFIX}auto", ComposerModelSlot.AUTO, "自动", listOf(ModelPresetId.DEEPSEEK_V4_FLASH))
    val daily = listOf(
        ComposerModelChoice("${PREFIX}daily:claude-sonnet", ComposerModelSlot.DAILY, "Claude Sonnet 5", listOf(ModelPresetId.CLAUDE_SONNET_5)),
        ComposerModelChoice("${PREFIX}daily:deepseek-flash", ComposerModelSlot.DAILY, "DeepSeek V4 Flash", listOf(ModelPresetId.DEEPSEEK_V4_FLASH)),
        ComposerModelChoice("${PREFIX}daily:gpt-terra", ComposerModelSlot.DAILY, "GPT-5.6 Terra", listOf(ModelPresetId.GPT_5_6_TERRA)),
        ComposerModelChoice("${PREFIX}daily:glm-flash", ComposerModelSlot.DAILY, "GLM-5.3 Flash", listOf(ModelPresetId.GLM_5_3_FLASH)),
        ComposerModelChoice("${PREFIX}daily:qwen-plus", ComposerModelSlot.DAILY, "Qwen3.7-Plus", listOf(ModelPresetId.QWEN_3_7_PLUS)),
        ComposerModelChoice("${PREFIX}daily:gemini-flash", ComposerModelSlot.DAILY, "Gemini 3.7 Flash", listOf(ModelPresetId.GEMINI_3_7_FLASH)),
    )
    val deep = listOf(
        ComposerModelChoice("${PREFIX}deep:claude-fable", ComposerModelSlot.DEEP, "Claude Fable 5", listOf(ModelPresetId.CLAUDE_FABLE_5)),
        ComposerModelChoice("${PREFIX}deep:claude-opus", ComposerModelSlot.DEEP, "Claude Opus 5", listOf(ModelPresetId.CLAUDE_OPUS_5)),
        ComposerModelChoice("${PREFIX}deep:deepseek-pro", ComposerModelSlot.DEEP, "DeepSeek V4 Pro", listOf(ModelPresetId.DEEPSEEK_V4_PRO)),
        ComposerModelChoice("${PREFIX}deep:gpt-sol", ComposerModelSlot.DEEP, "GPT-5.6 Sol", listOf(ModelPresetId.GPT_5_6_SOL)),
        ComposerModelChoice("${PREFIX}deep:glm-5.3", ComposerModelSlot.DEEP, "GLM-5.3", listOf(ModelPresetId.GLM_5_3)),
        ComposerModelChoice("${PREFIX}deep:qwen-max", ComposerModelSlot.DEEP, "Qwen3.8-Max", listOf(ModelPresetId.QWEN_3_8_MAX)),
    )
    val groups: Map<ComposerModelSlot, List<ComposerModelChoice>> = mapOf(
        ComposerModelSlot.DAILY to daily,
        ComposerModelSlot.DEEP to deep,
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
    val hasAttachment: Boolean = false,
    val requiresImage: Boolean = false,
    val requiresPdf: Boolean = false,
    val requiresVideo: Boolean = false,
    val requiresAudio: Boolean = false,
    /** Generic files use the provider's file/PDF capability family for preference only. */
    val requiresFile: Boolean = false,
    val requiresComplexReasoning: Boolean = false,
)

/**
 * A conservative, testable task classifier. Ordinary requests stay on the economical route;
 * escalation requires either a long prompt or an explicit deep-work signal.
 */
object AutoRoutingTaskClassifier {
    private const val LONG_PROMPT_THRESHOLD = 2_400
    private val complexSignal = Regex(
        "(?i)深入分析|深度分析|系统分析|完整方案|方案设计|架构设计|重构|代码审计|根因|权衡|" +
            "情景分析|风险传导|投资分析|估值分析|长期规划|多步骤推理|复杂推理|调试|复杂项目|报错|" +
            "\\b(?:debug|bug|stacktrace|exception|compile|architecture|root[ -]?cause|audit|" +
            "trade[ -]?offs?|migration|refactor|deep analysis|multi[ -]?step reasoning)\\b",
    )

    fun requiresComplexReasoning(text: String): Boolean =
        text.length >= LONG_PROMPT_THRESHOLD || complexSignal.containsMatchIn(text)
}

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
        val usable = candidates.mapNotNull { preset ->
            if (!isUsable(preset)) return@mapNotNull null
            val model = (resolver.resolve(preset) as? ResolvedModelResult.Resolved)?.model
                ?: return@mapNotNull null
            if (model.health == ModelHealth.UNAVAILABLE || !model.capabilities.supportsText) null
            else preset to model
        }
        // Prefer verified attachment capabilities, but never turn an unknown provider capability
        // into a local rejection. The selected model still receives the complete original file and
        // any provider-level incompatibility remains an explicit, visible send result.
        val capabilityMatches = usable.filter { (_, model) ->
            model.capabilities.let { capabilities ->
                (!facts.requiresImage || capabilities.supportsVision) &&
                    (!facts.requiresPdf || capabilities.supportsPdf) &&
                    (!facts.requiresVideo || capabilities.supportsVideo) &&
                    (!facts.requiresAudio || capabilities.supportsAudio) &&
                    (!facts.requiresFile || capabilities.supportsPdf)
            }
        }
        val pool = capabilityMatches.ifEmpty { usable }
        return pool.firstOrNull { (_, model) -> model.health != ModelHealth.DEGRADED }?.first
            ?: pool.firstOrNull()?.first
            ?: preferred
    }
}

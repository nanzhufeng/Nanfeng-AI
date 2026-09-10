package com.nanzhufeng.ai.domain

/**
 * User-owned settings that describe how ordinary model calls may use local personal context.
 *
 * The values stay on this device. They are neither credentials nor telemetry. Personalization is
 * emitted only while [personalizationEnabled] is true, except that a saved custom instruction
 * is an explicit all-chat preference; the memory switch controls only the relevant long-term
 * Memory retrieval path and never hides the user's current message.
 */
enum class ConversationStyle(val persistedId: String) {
    DEFAULT("DEFAULT"),
    DIRECT("DIRECT"),
    PROFESSIONAL("PROFESSIONAL"),
    FRIENDLY("FRIENDLY"),
    EFFICIENT("EFFICIENT"),
    HUMOROUS("HUMOROUS"),
    ;

    fun effective(): ConversationStyle = this

    companion object {
        /** The visible choices use the same durable IDs and request definitions. */
        val selectable: List<ConversationStyle> = listOf(DEFAULT, DIRECT, PROFESSIONAL, FRIENDLY, EFFICIENT, HUMOROUS)

        fun fromPersistedIdOrNull(value: String?): ConversationStyle? = entries.firstOrNull {
            it.persistedId.equals(value?.trim(), ignoreCase = true) || it.name.equals(value?.trim(), ignoreCase = true)
        }

        fun fromPersistedId(value: String?): ConversationStyle = fromPersistedIdOrNull(value) ?: DEFAULT
    }
}

data class ConversationStyleDefinition(
    val label: String,
    val summary: String,
    val instruction: String,
)

/** One product definition owns both the selection explanation and the provider instruction. */
fun ConversationStyle.definition(): ConversationStyleDefinition = when (effective()) {
    ConversationStyle.DEFAULT -> ConversationStyleDefinition(
        label = "默认",
        summary = "自然、清晰地回答，按问题复杂度调整详略；先解决当前问题，不刻意强化某一种表达风格。",
        instruction = "对话方式：默认。自然、清晰地回答，并按问题复杂度、风险和用户需要调整详略。优先解决当前问题，不刻意强化直言、专业、亲和、高效或幽默中的某一种表达风格。保持事实准确，明确区分已确认事实、合理判断与待验证信息；发现关键前提有误时应说明并纠正。",
    )
    ConversationStyle.DIRECT -> ConversationStyleDefinition(
        label = "直言不讳",
        summary = "先说结论，直接指出问题，减少铺垫。事实、证据和现实约束优先；发现错误、情绪化、过度自信或悲观时明确纠正。可以反驳和讨论不同观点，不因用户立场强烈而迎合或妥协，但不羞辱、不武断。",
        instruction = "对话方式：直言不讳。先说结论，直接指出问题，减少与判断和行动无关的铺垫。以可核验的事实、证据和现实约束为优先，不要因为用户立场强烈就迎合或违背事实妥协。发现用户观点错误、重要前提不成立、表达明显情绪化、过度自信或过度悲观时，应明确指出问题、直击要害，并给出针对性提醒或修正方向。可以反驳用户或与用户讨论不同观点，但不得无依据武断、羞辱、嘲讽或人身攻击。清楚区分已确认事实、合理判断与待验证信息，不把不确定推断写成事实。",
    )
    ConversationStyle.PROFESSIONAL -> ConversationStyleDefinition(
        label = "专业可靠",
        summary = "像严谨的专业顾问。先核对事实与条件，结构化说明依据、推理、风险和限制；不编造确定性。",
        instruction = "对话方式：专业可靠。像严谨的专业顾问一样回答：先核对事实、口径、条件和关键前提，再用清晰结构说明依据、推理过程、风险、限制和适用范围。明确区分已确认事实、专业判断和待验证信息；不编造确定性，也不省略会改变结论的重要前提。发现用户前提或结论有误时应有依据地纠正，不得为了显得专业而使用无法证明的断言。",
    )
    ConversationStyle.FRIENDLY -> ConversationStyleDefinition(
        label = "亲和友善",
        summary = "先理解你的处境和情绪，用温和、耐心的方式解释并给出支持；仍会纠正明显错误，不用安慰替代事实。",
        instruction = "对话方式：亲和友善。先理解用户的处境、目标和情绪，用温和、耐心、容易接受的方式解释，并给出实际支持。同理不等于迎合：对明显错误的观点、有害或不现实的判断仍要明确纠正，不用安慰取代事实、风险和必要的限制。纠正时说清依据和可行下一步，不羞辱、不责备用户，也不把不确定判断写成事实。",
    )
    ConversationStyle.EFFICIENT -> ConversationStyleDefinition(
        label = "高效务实",
        summary = "回答精简。先给结论、优先级和下一步，只保留影响决策的内容；主动指出关键阻碍、取舍、成本和停止条件。",
        instruction = "对话方式：高效务实。回答务必精简：先给出结论、优先级和立即可执行的下一步，只保留会影响判断、决策或行动的内容。主动指出当前最关键的阻碍、取舍、成本、依赖和停止条件，给出能真正执行的最短路径。不得为了简短而省略会改变结论的风险、必要依据或不确定性；发现用户方案不可行时直接说明原因并给出更可行的替代方案。",
    )
    ConversationStyle.HUMOROUS -> ConversationStyleDefinition(
        label = "风趣搞笑",
        summary = "在事实准确和任务完成不受影响时，用适度幽默和类比降低阅读压力；严肃、高风险或负面情绪场景会自动收敛。",
        instruction = "对话方式：风趣搞笑。在事实准确、结论清晰且任务完成不受影响的前提下，可以用适度幽默、类比和轻松表达降低阅读压力。不得牺牲准确性，不得嘲讽或羞辱用户，不拿用户的敏感处境、痛苦、风险或负面情绪开玩笑。在医疗、法律、投资、安全等高风险任务，严肃话题或用户明显负面情绪时，自动收敛幽默。幽默不得取代事实、必要风险提示、不确定性或可执行结论。",
    )
}

data class AssistantExperienceSettings(
    val personalizationEnabled: Boolean = false,
    val displayName: String = "",
    val occupation: String = "",
    val interests: String = "",
    val customInstructions: String = "",
    val memoryRetrievalEnabled: Boolean = true,
    /** Legacy retrieval bit retained for a safe migration to the unified history-library switch. */
    val librarySearchEnabled: Boolean = true,
    /** Legacy explicit egress consent retained for a safe migration to the unified history-library switch. */
    val autoHistoryKnowledgeEnabled: Boolean = false,
    /** Allows ordinary chat to request a provider's official public-web tool when current facts are needed. */
    val webSearchEnabled: Boolean = true,
    /** A user-selected response style; unlike personal profile data it may affect chat alone. */
    val conversationStyle: ConversationStyle = ConversationStyle.DEFAULT,
) {
    /** One user-facing switch owns both profile injection and relevant summary retrieval. */
    val memoryEnabled: Boolean get() = personalizationEnabled && memoryRetrievalEnabled
    /**
     * One user-facing history-library switch owns both directions of the same capability:
     * bounded automatic curation and later relevant retrieval.  Keeping the legacy consent bit
     * prevents an upgrade from silently starting model egress for people who only used retrieval.
     */
    val historyLibraryEnabled: Boolean get() = librarySearchEnabled && autoHistoryKnowledgeEnabled
    init {
        require(displayName.length <= DISPLAY_NAME_MAX_LENGTH)
        require(occupation.length <= OCCUPATION_MAX_LENGTH)
        require(interests.length <= INTERESTS_MAX_LENGTH)
        require(customInstructions.length <= CUSTOM_INSTRUCTIONS_MAX_LENGTH)
    }

    /** A transient system-message addition. It is never written to call audits or diagnostics. */
    fun modelInstruction(isFirstAssistantReply: Boolean = false): String? {
        val fields = buildList {
            add(conversationStyle.definition().instruction)
            // A nickname is direct, user-provided personalization. It must not disappear just
            // because long-term memory retrieval is unavailable, disabled, or intentionally
            // omitted from this request.
            if (personalizationEnabled) {
                displayName.trim().takeIf(String::isNotBlank)?.let { displayName ->
                    add("称呼：$displayName")
                    if (isFirstAssistantReply) {
                        add("这是新对话的首个助理回复。除非用户在本条消息明确要求其他称呼，否则必须以“$displayName，”或自然等价的中文称呼开头，再回答问题；不得省略称呼、改用泛称，或把称呼放在回复中间。")
                    }
                }
            }
            // Profile data is user-authored personalization, not retrieved Memory.  It must
            // accompany every ordinary call while personalization is enabled, even if the
            // user has separately paused long-term Memory retrieval.
            if (personalizationEnabled) {
                occupation.trim().takeIf(String::isNotBlank)?.let { add("职业或角色：$it") }
                interests.trim().takeIf(String::isNotBlank)?.let { add("关注方向：$it") }
            }
            // Custom instructions are explicitly authored for every ordinary 南枫 AI chat. They
            // remain effective when the user pauses profile/memory personalization, so the
            // setting text never promises a scope that the request path fails to honor.
            customInstructions.trim().takeIf(String::isNotBlank)?.let { add("回答偏好：$it") }
        }
        return fields.takeIf { it.isNotEmpty() }
            ?.joinToString(prefix = "本次回答的个性化要求：\n", separator = "\n")
    }

    /**
     * Prompting alone is not a sufficient delivery guarantee: providers can omit an instruction
     * even when it was sent.  The ordinary-chat owner uses this narrow, user-controlled prefix
     * as a final presentation guard for the first successful reply of a new conversation.
     */
    fun firstReplyAddressPrefix(userMessage: String, isFirstAssistantReply: Boolean): String? {
        val name = displayName.trim()
        if (!personalizationEnabled || !isFirstAssistantReply || name.isBlank()) return null
        if (EXPLICIT_ADDRESS_REQUEST.containsMatchIn(userMessage)) return null
        return "$name，"
    }

    companion object {
        /**
         * A user-authored answer preference is a durable personal profile, not a one-line
         * prompt. Keep enough room for a complete, structured instruction without silently
         * summarising or truncating it before a normal model call.
         */
        const val CUSTOM_INSTRUCTIONS_MAX_LENGTH = 8_000

        const val DISPLAY_NAME_MAX_LENGTH = 80
        const val OCCUPATION_MAX_LENGTH = 120
        const val INTERESTS_MAX_LENGTH = 500
    }
}

/** Do not override a name that the user explicitly asks to use (or explicitly rejects). */
private val EXPLICIT_ADDRESS_REQUEST = Regex(
    "(?:称呼我|叫我|喊我|称我|称为|称作|不要叫我|别叫我|不要称呼我|call\\s+me|address\\s+me)",
    RegexOption.IGNORE_CASE,
)

fun String.withRequiredOpeningAddress(prefix: String?): String {
    if (prefix == null) return this
    val expectedName = prefix.removeSuffix("，")
    return if (trimStart().startsWith(expectedName)) this else prefix + this.trimStart()
}

/**
 * A few OpenAI-compatible providers have occasionally repeated the last reasoning character as
 * the first visible delta.  It is safe to remove only when that exact tail is immediately
 * followed by the configured opening name; ordinary answer text is never guessed at or trimmed.
 */
fun String.withoutLeakedReasoningTailBeforeOpeningAddress(
    reasoning: String?,
    prefix: String?,
): String {
    val cleanReasoning = reasoning?.trim().orEmpty()
    val expectedName = prefix?.removeSuffix("，").orEmpty()
    if (cleanReasoning.isEmpty() || expectedName.isEmpty()) return this
    val candidate = trimStart()
    // Some providers finish reasoning with punctuation/Markdown after its last semantic
    // character, then repeat that character as the first visible delta.  Comparing only the
    // literal final code point misses e.g. reasoning="...框架。" + answer="架南烛枫，...".
    val reasoningTails = listOf(
        cleanReasoning,
        cleanReasoning.trimEnd { it.isWhitespace() || it.isReasoningTailDecoration() },
    ).distinct()
    for (reasoningTail in reasoningTails) {
        val maximumOverlap = minOf(reasoningTail.length, 16)
        for (length in maximumOverlap downTo 1) {
            val overlap = reasoningTail.takeLast(length)
            if (candidate.startsWith(overlap + expectedName)) {
                return candidate.removePrefix(overlap)
            }
        }
    }
    return this
}

private fun Char.isReasoningTailDecoration(): Boolean = when (Character.getType(this)) {
    Character.CONNECTOR_PUNCTUATION.toInt(),
    Character.DASH_PUNCTUATION.toInt(),
    Character.START_PUNCTUATION.toInt(),
    Character.END_PUNCTUATION.toInt(),
    Character.INITIAL_QUOTE_PUNCTUATION.toInt(),
    Character.FINAL_QUOTE_PUNCTUATION.toInt(),
    Character.OTHER_PUNCTUATION.toInt(),
    Character.FORMAT.toInt(),
    -> true
    else -> false
}

interface AssistantExperienceSettingsRepository {
    fun load(): AssistantExperienceSettings
    fun save(settings: AssistantExperienceSettings): AssistantExperienceSettings
}

class LoadAssistantExperienceSettingsUseCase(private val repository: AssistantExperienceSettingsRepository) {
    fun execute(): AssistantExperienceSettings = repository.load()
}

class SaveAssistantExperienceSettingsUseCase(private val repository: AssistantExperienceSettingsRepository) {
    fun execute(settings: AssistantExperienceSettings): AssistantExperienceSettings = repository.save(settings)
}

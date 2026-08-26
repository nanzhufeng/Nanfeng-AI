package com.nanzhufeng.ai.domain

/**
 * User-owned settings that describe how ordinary model calls may use local personal context.
 *
 * The values stay on this device. They are neither credentials nor telemetry. Personalization is
 * emitted only while [personalizationEnabled] is true, except that a saved custom instruction
 * is an explicit all-chat preference; the memory switch controls only the relevant long-term
 * Memory retrieval path and never hides the user's current message.
 */
enum class ConversationStyle {
    DEFAULT,
    DIRECT,
    PROFESSIONAL,
    FRIENDLY,
    EFFICIENT,
    HUMOROUS,
}

data class AssistantExperienceSettings(
    val personalizationEnabled: Boolean = false,
    val displayName: String = "",
    val occupation: String = "",
    val interests: String = "",
    val customInstructions: String = "",
    val memoryRetrievalEnabled: Boolean = true,
    /** Controls only local Knowledge-library retrieval for ordinary chat. Defaults on for upgrade parity. */
    val librarySearchEnabled: Boolean = true,
    /** Allows ordinary chat to request a provider's official public-web tool when current facts are needed. */
    val webSearchEnabled: Boolean = true,
    /** A user-selected response style; unlike personal profile data it may affect chat alone. */
    val conversationStyle: ConversationStyle = ConversationStyle.DEFAULT,
) {
    /** One user-facing switch owns both profile injection and relevant summary retrieval. */
    val memoryEnabled: Boolean get() = personalizationEnabled && memoryRetrievalEnabled
    init {
        require(displayName.length <= DISPLAY_NAME_MAX_LENGTH)
        require(occupation.length <= OCCUPATION_MAX_LENGTH)
        require(interests.length <= INTERESTS_MAX_LENGTH)
        require(customInstructions.length <= CUSTOM_INSTRUCTIONS_MAX_LENGTH)
    }

    /** A transient system-message addition. It is never written to call audits or diagnostics. */
    fun modelInstruction(isFirstAssistantReply: Boolean = false): String? {
        val fields = buildList {
            conversationStyle.instruction()?.let(::add)
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

private fun ConversationStyle.instruction(): String? = when (this) {
    ConversationStyle.DEFAULT -> null
    ConversationStyle.DIRECT -> "对话方式：直言不讳。先给结论，清楚区分事实、判断与待验证信息；直接指出风险和关键分歧，不要空泛安慰。"
    ConversationStyle.PROFESSIONAL -> "对话方式：专业可靠。以准确、严谨、可追溯为优先；用清晰结构解释依据，并明确不确定性。"
    ConversationStyle.FRIENDLY -> "对话方式：亲和友善。语气温和自然，但结论与建议仍须明确、具体，避免过度迎合。"
    ConversationStyle.EFFICIENT -> "对话方式：高效务实。优先给可执行结论和下一步；只保留解决问题必要的背景与分析。"
    ConversationStyle.HUMOROUS -> "对话方式：风趣搞笑。可以自然使用轻松、聪明的幽默来增进可读性，但不得牺牲准确性、清晰度、风险提示或任务结论；严肃、高风险或用户明确要求正式时保持克制。"
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

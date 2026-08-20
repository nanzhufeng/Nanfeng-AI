package com.nanzhufeng.ai.domain

/**
 * P3-I test-only normalized text boundary. It accepts no credential, provider configuration,
 * request body or network client; text exists only long enough to become a normal Message event.
 */
sealed interface ConversationRealTextNormalizedEvent {
    data class TextDelta(val text: String) : ConversationRealTextNormalizedEvent {
        init { require(text.isNotEmpty()) { "规范化文本增量不能为空。" } }
    }

    data object Completed : ConversationRealTextNormalizedEvent

    data class Failed(val safeErrorCode: String) : ConversationRealTextNormalizedEvent {
        init { require(safeErrorCode.matches(Regex("[A-Z][A-Z0-9_]*"))) { "错误码必须是安全大写代码。" } }
    }

    data class Cancelled(val safeErrorCode: String = "TEST_TRANSPORT_CANCELLED") : ConversationRealTextNormalizedEvent {
        init { require(safeErrorCode.matches(Regex("[A-Z][A-Z0-9_]*"))) { "错误码必须是安全大写代码。" } }
    }
}

/** A deterministic script is the only P0 transport. It has no HTTP, Key or Provider dependency. */
interface ConversationRealTextTestTransport {
    fun normalizedEvents(handle: ConversationRealTextExecutionTransportHandle): List<ConversationRealTextNormalizedEvent>
}

class ScriptedConversationRealTextTestTransport(
    private val script: List<ConversationRealTextNormalizedEvent>,
) : ConversationRealTextTestTransport {
    override fun normalizedEvents(handle: ConversationRealTextExecutionTransportHandle): List<ConversationRealTextNormalizedEvent> = script
}

sealed interface ConversationRealTextTestExecutionResult {
    data class Started(val record: ConversationRealTextExecutionRecord) : ConversationRealTextTestExecutionResult
    data class Replayed(val record: ConversationRealTextExecutionRecord) : ConversationRealTextTestExecutionResult
    data class Updated(val record: ConversationRealTextExecutionRecord) : ConversationRealTextTestExecutionResult
    data class Rejected(val reason: String) : ConversationRealTextTestExecutionResult
}

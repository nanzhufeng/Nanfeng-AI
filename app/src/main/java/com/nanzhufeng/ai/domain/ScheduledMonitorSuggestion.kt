package com.nanzhufeng.ai.domain

/** The only content eligible for the user-initiated reminder-draft request. */
data class ScheduledMonitorDraftSource(
    val userText: String,
    val assistantText: String,
) {
    init {
        require(userText.isNotBlank() && assistantText.isNotBlank())
    }
}

data class ScheduledMonitorSuggestion(
    val title: String,
    val instruction: String,
    val cadence: ScheduledMonitorCadence = ScheduledMonitorCadence.DAILY,
)

/**
 * This is deliberately an eligibility gate, not a title generator. General terms such as
 * “价格”、“动态” or a model answer mentioning a product must never turn an ordinary discussion
 * into a recurring task. Meaningful wording is produced only by the user-initiated Qwen
 * refiner from [ScheduledMonitorDraftSource].
 */
object ScheduledMonitorSuggestionPolicy {
    fun shouldOffer(userText: String): Boolean {
        val text = userText.normalized()
        return explicitFutureIntent.any(text::contains) && text.meaningfulCharacters() >= 6
    }

    private fun String.normalized() = lowercase().replace(Regex("\\s+"), "")
    private fun String.meaningfulCharacters() = count { it.isLetterOrDigit() || it.code in 0x4E00..0x9FFF }

    private val explicitFutureIntent = listOf(
        "提醒我", "提醒一下", "给我提醒", "帮我监控", "持续监控", "持续跟踪", "帮我跟踪",
        "持续关注", "有变化告诉我", "有消息告诉我", "定期告诉我", "每天告诉我", "每周告诉我", "每小时告诉我",
    )
}

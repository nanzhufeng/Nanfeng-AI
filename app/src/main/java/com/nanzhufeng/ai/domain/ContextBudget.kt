package com.nanzhufeng.ai.domain

/** Provider tokenizer-independent upper estimate used until a provider-specific tokenizer is registered. */
object LocalTokenEstimator {
    fun estimate(text: String): Int {
        if (text.isBlank()) return 0
        var units = 0
        var asciiRun = 0
        text.forEach { char ->
            if (char.code <= 0x7f && !char.isWhitespace()) asciiRun++ else {
                units += (asciiRun + 3) / 4
                asciiRun = 0
                if (!char.isWhitespace()) units++
            }
        }
        return (units + (asciiRun + 3) / 4).coerceAtLeast(1)
    }
}

/**
 * One explicit selection point for token accounting.  Unknown tokenizer IDs never pretend to
 * be exact: they use the documented conservative local estimator until a verified tokenizer is
 * registered for that ID.
 */
object ModelTokenEstimators {
    fun estimate(tokenizerId: String, text: String): Int = when (tokenizerId) {
        "heuristic-v1", "openrouter-catalog-v1" -> LocalTokenEstimator.estimate(text)
        else -> LocalTokenEstimator.estimate(text)
    }
}

data class ContextBudget(
    val contextWindowTokens: Int,
    val reservedOutputTokens: Int,
    val promptTokens: Int,
    val historyTokens: Int,
    val retrievalTokens: Int,
    /** Tokens reserved for the current user turn, attachment interpretation and fixed instructions. */
    val fixedInputTokens: Int = 0,
    /** Kept with the budget so every selected context item uses the selected model's estimator. */
    val tokenizerId: String = "heuristic-v1",
) {
    init {
        require(contextWindowTokens > 0 && reservedOutputTokens > 0 && promptTokens >= 0)
        require(historyTokens >= 0 && retrievalTokens >= 0 && historyTokens + retrievalTokens <= promptTokens)
        require(fixedInputTokens >= 0 && fixedInputTokens <= promptTokens)
    }

    val availableContextTokens: Int get() = promptTokens - fixedInputTokens

    /**
     * Reserves the non-negotiable input before any history or retrieval is selected.  A request
     * that cannot carry its current user turn is rejected locally; it is never sent incomplete.
     */
    fun reserveFixedInput(tokens: Int): ContextBudget? {
        if (tokens !in 0..promptTokens) return null
        val remaining = promptTokens - tokens
        val history = (remaining * 40) / 100
        return copy(historyTokens = history, retrievalTokens = remaining - history, fixedInputTokens = tokens)
    }

    companion object {
        fun forModel(model: ResolvedModel): ContextBudget {
            val window = (model.contextWindowTokens ?: SAFE_FALLBACK_CONTEXT).coerceIn(MIN_CONTEXT, MAX_CONTEXT).toInt()
            val output = (model.maxOutputTokens ?: SAFE_FALLBACK_OUTPUT).coerceIn(1, (window / 2).toLong()).toInt()
            val prompt = (window - output).coerceAtLeast(0)
            val history = (prompt * 40) / 100
            return ContextBudget(window, output, prompt, history, prompt - history, tokenizerId = model.tokenizerId)
        }
        fun safeDefault() = ContextBudget(SAFE_FALLBACK_CONTEXT.toInt(), SAFE_FALLBACK_OUTPUT.toInt(), 24_576, 9_830, 14_746)
        private const val SAFE_FALLBACK_CONTEXT = 32_768L
        private const val SAFE_FALLBACK_OUTPUT = 8_192L
        private const val MIN_CONTEXT = 4_096L
        private const val MAX_CONTEXT = 2_000_000L
    }
}

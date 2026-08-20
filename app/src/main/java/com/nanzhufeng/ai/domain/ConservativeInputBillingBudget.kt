package com.nanzhufeng.ai.domain

/**
 * A local conservative reservation bound, not a claim about Provider-reported token usage.
 *
 * UTF-8 byte count is used as the ceiling: a Provider tokenizer may fall back to byte-level
 * pieces, so multi-byte CJK text, whitespace, emoji, and malformed surrogate input never get
 * the optimistic four-characters-per-token discount. Actual Provider usage remains an external
 * reconciliation fact and must not be inferred from this bound.
 */
object ConservativeInputBillingBudget {
    fun inputTokenUpperBound(text: String): Long =
        text.toByteArray(Charsets.UTF_8).size.toLong().coerceAtLeast(1L)

    fun maximumBudgetMicros(
        text: String,
        outputTokenLimit: Long,
        inputMicrosPerToken: Long,
        outputMicrosPerToken: Long,
    ): Long? {
        val inputCost = safeMultiply(inputTokenUpperBound(text), inputMicrosPerToken) ?: return null
        val outputCost = safeMultiply(outputTokenLimit, outputMicrosPerToken) ?: return null
        return safeAdd(inputCost, outputCost)
    }

    private fun safeMultiply(left: Long, right: Long): Long? =
        runCatching { Math.multiplyExact(left, right) }.getOrNull()

    private fun safeAdd(left: Long, right: Long): Long? =
        runCatching { Math.addExact(left, right) }.getOrNull()
}

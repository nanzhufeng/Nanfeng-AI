package com.nanzhufeng.ai.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

/**
 * Versioned, token-only fallback for models whose current direct or hosted price is verified.
 *
 * A provider settlement always wins.  These rates deliberately exclude metered provider tools,
 * promotions and cache writes because a response without those accounting details cannot support
 * a truthful exact charge. Prices are stored in each provider's billing currency per million
 * tokens, so decimal prices remain exact until the final micro-currency rounding boundary.
 */
object ConversationCostEstimator {
    const val OPENROUTER_PRICE_VERSION = "openrouter-public-prices-2026-08-v1"
    const val GEMINI_3_8_OPENROUTER_PRICE_VERSION = "openrouter-gemini-3.8-flash-intro-2026-09-v1"
    const val QWEN_PRICE_VERSION = "qwen-cn-beijing-standard-2026-08-v2"
    const val DEEPSEEK_LEGACY_PRICE_VERSION = "deepseek-public-prices-before-2026-08-17-v1"
    const val DEEPSEEK_OFF_PEAK_PRICE_VERSION = "deepseek-off-peak-2026-08-v2"
    const val DEEPSEEK_PEAK_PRICE_VERSION = "deepseek-peak-2026-08-v2"
    const val ZHIPU_GLM_5_3_FLASH_PROMOTION_PRICE_VERSION = "zhipu-glm-5.3-flash-promo-2026-08-v1"
    const val ZHIPU_GLM_5_3_FLASH_STANDARD_PRICE_VERSION = "zhipu-glm-5.3-flash-standard-2026-08-v1"

    private data class Price(
        val version: String,
        val inputPerMillion: BigDecimal,
        val outputPerMillion: BigDecimal,
        val cachedInputPerMillion: BigDecimal? = null,
        val currencyCode: String = "USD",
    )

    private data class InputTier(val upperInclusiveInputTokens: Long, val price: Price)
    private data class ScheduledPrice(val untilExclusive: Instant?, val price: Price)

    private fun price(
        version: String,
        inputPerMillion: String,
        outputPerMillion: String,
        cachedInputPerMillion: String? = null,
        currencyCode: String = "USD",
    ) = Price(
        version = version,
        inputPerMillion = BigDecimal(inputPerMillion),
        outputPerMillion = BigDecimal(outputPerMillion),
        cachedInputPerMillion = cachedInputPerMillion?.let(::BigDecimal),
        currencyCode = currencyCode,
    )

    private val prices = mapOf(
        "anthropic/claude-fable-5" to price(OPENROUTER_PRICE_VERSION, "10", "50"),
        "anthropic/claude-opus-5" to price(OPENROUTER_PRICE_VERSION, "5", "25"),
        "anthropic/claude-opus-5:fast" to price(OPENROUTER_PRICE_VERSION, "10", "50"),
        "anthropic/claude-sonnet-5" to price(OPENROUTER_PRICE_VERSION, "2", "10"),
        "anthropic/claude-haiku-4.5" to price(OPENROUTER_PRICE_VERSION, "1", "5"),
        "google/gemini-3.7-flash" to price(OPENROUTER_PRICE_VERSION, "0.375", "1.875"),
        "google/gemini-3.8-flash" to price(GEMINI_3_8_OPENROUTER_PRICE_VERSION, "0.75", "3.75", "0.075"),
        "moonshotai/kimi-k3" to price(OPENROUTER_PRICE_VERSION, "2.55", "12.75", "0.256"),
        "qwen3.8-max" to price(QWEN_PRICE_VERSION, "12", "36", "1.5", "CNY"),
    )

    private val deepSeekPeakPricingEffectiveAt = Instant.parse("2026-08-16T16:00:00Z")

    private fun deepSeekPrice(modelId: String, at: Instant): Price? {
        if (modelId == "deepseek-flash") {
            return if (DeepSeekPricingWindow.periodAt(at) == DeepSeekPricingPeriod.PEAK)
                price("deepseek-v4.1-flash-peak-2026-09-10-v1", "0.3", "1.2", "0.006")
            else price("deepseek-v4.1-flash-off-peak-2026-09-10-v1", "0.15", "0.6", "0.003")
        }
        // The official legacy Flash alias now serves V4.1, but its exact cutover instant is unpublished.
        // Keep past estimates and refuse to apply the retired tariff to new alias responses.
        if (modelId == "deepseek-v4-flash" && !at.isBefore(Instant.parse("2026-09-10T00:00:00Z"))) return null
        if (modelId !in setOf("deepseek-v4-pro", "deepseek-v4-flash")) return null
        if (at.isBefore(deepSeekPeakPricingEffectiveAt)) {
            return when (modelId) {
                "deepseek-v4-pro" -> price(DEEPSEEK_LEGACY_PRICE_VERSION, "0.435", "0.87", "0.003625")
                else -> price(DEEPSEEK_LEGACY_PRICE_VERSION, "0.14", "0.28", "0.0028")
            }
        }
        // Preserve the previously recorded schedule for historical IDs; new Flash has its own version.
        val hour = at.atOffset(java.time.ZoneOffset.UTC).hour
        val peak = hour in 1 until 4 || hour in 6 until 10
        return when (modelId) {
            "deepseek-v4-pro" -> if (peak) {
                price(DEEPSEEK_PEAK_PRICE_VERSION, "1.32", "3.96", "0.044")
            } else {
                price(DEEPSEEK_OFF_PEAK_PRICE_VERSION, "0.66", "1.98", "0.022")
            }
            else -> if (peak) {
                price(DEEPSEEK_PEAK_PRICE_VERSION, "0.44", "1.32", "0.014")
            } else {
                price(DEEPSEEK_OFF_PEAK_PRICE_VERSION, "0.22", "0.66", "0.007")
            }
        }
    }

    private val inputTieredPrices = mapOf(
        "openai/gpt-5.6-sol" to listOf(
            InputTier(271_999, price(OPENROUTER_PRICE_VERSION, "2", "10")),
            InputTier(Long.MAX_VALUE, price(OPENROUTER_PRICE_VERSION, "4", "15")),
        ),
        "openai/gpt-5.6-terra" to listOf(
            InputTier(271_999, price(OPENROUTER_PRICE_VERSION, "2", "12")),
            InputTier(Long.MAX_VALUE, price(OPENROUTER_PRICE_VERSION, "4", "18")),
        ),
        "openai/gpt-5.6-luna" to listOf(
            InputTier(271_999, price(OPENROUTER_PRICE_VERSION, "0.2", "1.2")),
            InputTier(Long.MAX_VALUE, price(OPENROUTER_PRICE_VERSION, "0.4", "1.8")),
        ),
        "x-ai/grok-4.6" to listOf(
            InputTier(199_999, price(OPENROUTER_PRICE_VERSION, "2", "6", "0.5")),
            InputTier(Long.MAX_VALUE, price(OPENROUTER_PRICE_VERSION, "4", "12", "1")),
        ),
        "qwen3.7-plus" to listOf(
            InputTier(256_000, price(QWEN_PRICE_VERSION, "0.276", "1.101")),
            InputTier(1_000_000, price(QWEN_PRICE_VERSION, "0.826", "3.301")),
        ),
        "qwen3.6-flash" to listOf(
            InputTier(256_000, price(QWEN_PRICE_VERSION, "0.165", "0.99")),
            InputTier(1_000_000, price(QWEN_PRICE_VERSION, "0.66", "3.961")),
        ),
    )

    /** Official GLM-5.3 Flash promotion is valid through 2026-08-31 in China; past records
     * retain the rate that applied at their recorded time, while later records fall back to the
     * published standard price instead of keeping a stale promotion forever. */
    private val scheduledPrices = mapOf(
        "glm-5.3-flash" to listOf(
            ScheduledPrice(
                untilExclusive = Instant.parse("2026-08-31T16:00:00Z"),
                price(ZHIPU_GLM_5_3_FLASH_PROMOTION_PRICE_VERSION, "0.4", "1.4", "0.115", "CNY"),
            ),
            ScheduledPrice(
                untilExclusive = null,
                price(ZHIPU_GLM_5_3_FLASH_STANDARD_PRICE_VERSION, "0.8", "2.8", "0.23", "CNY"),
            ),
        ),
    )

    fun estimate(modelId: String, usage: ProviderUsage, at: Instant = Instant.now()): ProviderCost? {
        val input = usage.inputTokens ?: return null
        val output = usage.outputTokens ?: return null
        val normalizedModelId = modelId.lowercase()
        val price = deepSeekPrice(normalizedModelId, at)
            ?: prices[normalizedModelId]
            ?: inputTieredPrices[normalizedModelId]?.firstOrNull { input <= it.upperInclusiveInputTokens }?.price
            ?: scheduledPrices[normalizedModelId]?.firstOrNull { it.untilExclusive == null || at.isBefore(it.untilExclusive) }?.price
            ?: return null
        val cachedInput = usage.cachedInputTokens?.coerceAtMost(input) ?: 0L
        val uncachedInput = input - cachedInput
        val cachedInputPrice = price.cachedInputPerMillion ?: price.inputPerMillion
        // Currency/M token × token = micro-currency, because one currency unit is one million micros.
        val micros = BigDecimal.valueOf(uncachedInput).multiply(price.inputPerMillion)
            .add(BigDecimal.valueOf(cachedInput).multiply(cachedInputPrice))
            .add(BigDecimal.valueOf(output).multiply(price.outputPerMillion))
            .setScale(0, RoundingMode.HALF_UP)
            .longValueExact()
        return ProviderCost(price.version, price.currencyCode, micros)
    }
}

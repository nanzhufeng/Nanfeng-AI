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
        "inception/mercury-3" to price("openrouter-mercury-3-2026-10-20-v1", "0.1", "0.35", "0.01"),
        "google/gemini-4-argon" to price("openrouter-gemini-4-argon-2026-10-15-v1", "2", "10", "0.1"),
        "meta/muse-wonder" to price("openrouter-muse-wonder-2026-10-13-v1", "4", "20", "0.2"),
        "anthropic/claude-opus-5.5" to price("openrouter-public-prices-2026-10-09-v1", "4", "20", "0.2"),
        "anthropic/claude-sonnet-5.5" to price("openrouter-public-prices-2026-10-09-v1", "2", "10", "0.1"),
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
        val weekday = at.atOffset(java.time.ZoneOffset.UTC).dayOfWeek.value <= 5
        val peak = weekday && (hour in 1 until 4 || hour in 6 until 10)
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
        "anthropic/claude-haiku-5.5" to listOf(
            InputTier(99_999, price("openrouter-public-prices-2026-10-09-v1", "0.1", "0.5", "0.01")),
            InputTier(Long.MAX_VALUE, price("openrouter-public-prices-2026-10-09-v1", "0.5", "2.5", "0.05")),
        ),
        "openai/gpt-6.1-sol" to listOf(
            InputTier(271_999, price("openrouter-public-prices-2026-10-09-v1", "2", "10", "0.1")),
            InputTier(Long.MAX_VALUE, price("openrouter-public-prices-2026-10-09-v1", "4", "15", "0.2")),
        ),
        "openai/gpt-6.1-terra" to listOf(
            InputTier(271_999, price("openrouter-public-prices-2026-10-27-v1", "1", "5", "0.05")),
            InputTier(Long.MAX_VALUE, price("openrouter-public-prices-2026-10-27-v1", "2", "7.5", "0.1")),
        ),
        "openai/gpt-6-luna" to listOf(
            InputTier(271_999, price("openrouter-public-prices-2026-10-09-v1", "0.1", "0.5", "0.01")),
            InputTier(Long.MAX_VALUE, price("openrouter-public-prices-2026-10-09-v1", "0.2", "0.75", "0.02")),
        ),
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

    private fun relayPrice(modelId: String, displayName: String?, at: Instant): Price? {
        val model = RelayCoreModels.specs.firstOrNull { RelayCoreModels.sameApiModel(it.provider,it.apiId,modelId) } ?: return null
        if (displayName == null && at.isBefore(Instant.parse("2026-10-10T00:00:00Z"))) return null
        val channel = displayName?.split(" · ")?.getOrNull(1) ?: model.channel
        val multiplier = when (channel) { "Kiro" -> "0.08"; "Kiro正价" -> "0.35"; "Pro1" -> "0.12"; "Pro2" -> "0.18"; "Pro3" -> "0.25"; "Gemini" -> "0.28"; else -> return null }.toBigDecimal()
        val base = when(modelId) {
            "claude-opus-5-5", "claude-opus-5.5" -> listOf("4","20","0.2")
            "claude-sonnet-5-5" -> listOf("2","10","0.2")
            "gpt-6.1-sol" -> listOf("2","10","0.1")
            "gpt-6-astra" -> listOf("10","50","1")
            "gemini-3.8-flash" -> listOf("0.75","3.75","0.075")
            "gemini-3.1-pro" -> listOf("2","12","0.2")
            else -> return null
        }
        // User-confirmed account denomination: Relay dollar-style numbers are nominal CNY.
        return Price("relaycore-cny-nominal-user-confirmed-2026-10-10-$channel",base[0].toBigDecimal()*multiplier,base[1].toBigDecimal()*multiplier,base[2].toBigDecimal()*multiplier,"CNY")
    }

    private fun currentQwenPrice(modelId: String, input: Long, at: Instant): Price? {
        if (at.isBefore(Instant.parse("2026-10-10T00:00:00Z")) || input > 1_000_000) return null
        val longInput=input > 256_000
        return when(modelId) {
            "qwen3.7-plus" -> price("qwen-cn-beijing-published-standard-2026-10-10",if(longInput) "6" else "2",if(longInput) "24" else "8",if(longInput) "1.2" else "0.4",currencyCode="CNY")
            "qwen3.6-flash" -> price("qwen-cn-beijing-published-standard-2026-10-10",if(longInput) "4.8" else "1.2",if(longInput) "28.8" else "7.2",if(longInput) "0.96" else "0.24",currencyCode="CNY")
            "qwen3.8-max" -> price("qwen-cn-beijing-published-standard-2026-10-10","12","36","2.4",currencyCode="CNY")
            else -> null
        }
    }

    fun estimate(modelId: String, usage: ProviderUsage, at: Instant = Instant.now(), modelDisplayName: String? = null): ProviderCost? {
        val input = usage.inputTokens ?: return null
        val output = usage.outputTokens ?: return null
        val normalizedModelId = modelId.lowercase()
        val price = relayPrice(normalizedModelId,modelDisplayName,at)
            ?: currentQwenPrice(normalizedModelId,input,at)
            ?: (if (normalizedModelId == "glm-5.3" && !at.isBefore(Instant.ofEpochMilli(1793351790000L)))
                price("zhipu-glm-5.3-observed-2026-10-30-v1", "8", "28", "2", "CNY") else null)
            ?: deepSeekPrice(normalizedModelId, at)
            // Observed public catalog update, not a claimed upstream historical cutover.
            ?: (if (normalizedModelId == "google/gemini-3.8-flash" && !at.isBefore(Instant.ofEpochMilli(1793350322077L)))
                price("openrouter-gemini-3.8-flash-observed-2026-10-30-v1", "0.825", "4.125", "0.0825") else null)
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

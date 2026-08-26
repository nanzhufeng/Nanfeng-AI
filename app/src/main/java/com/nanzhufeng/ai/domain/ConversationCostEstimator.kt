package com.nanzhufeng.ai.domain

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Versioned, token-only fallback for every model currently exposed by the product.
 *
 * A provider settlement always wins.  These rates deliberately exclude metered provider tools,
 * promotions and cache writes because a response without those accounting details cannot support
 * a truthful exact charge.  Pricing is USD per million tokens so decimal prices remain exact
 * until the final micro-USD rounding boundary.
 */
object ConversationCostEstimator {
    const val OPENROUTER_PRICE_VERSION = "openrouter-public-prices-2026-08-v1"
    const val QWEN_PRICE_VERSION = "qwen-cn-beijing-standard-2026-08-v1"
    const val DEEPSEEK_PRICE_VERSION = "deepseek-public-prices-2026-08-v1"

    private data class Price(
        val version: String,
        val inputPerMillionUsd: BigDecimal,
        val outputPerMillionUsd: BigDecimal,
        val cachedInputPerMillionUsd: BigDecimal? = null,
    )

    private data class InputTier(val upperInclusiveInputTokens: Long, val price: Price)

    private fun price(
        version: String,
        inputPerMillionUsd: String,
        outputPerMillionUsd: String,
        cachedInputPerMillionUsd: String? = null,
    ) = Price(
        version = version,
        inputPerMillionUsd = BigDecimal(inputPerMillionUsd),
        outputPerMillionUsd = BigDecimal(outputPerMillionUsd),
        cachedInputPerMillionUsd = cachedInputPerMillionUsd?.let(::BigDecimal),
    )

    private val prices = mapOf(
        "anthropic/claude-fable-5" to price(OPENROUTER_PRICE_VERSION, "10", "50"),
        "anthropic/claude-opus-5" to price(OPENROUTER_PRICE_VERSION, "5", "25"),
        "anthropic/claude-opus-5:fast" to price(OPENROUTER_PRICE_VERSION, "10", "50"),
        "anthropic/claude-sonnet-5" to price(OPENROUTER_PRICE_VERSION, "2", "10"),
        "anthropic/claude-haiku-4.5" to price(OPENROUTER_PRICE_VERSION, "1", "5"),
        "google/gemini-3.7-flash" to price(OPENROUTER_PRICE_VERSION, "0.375", "1.875"),
        "qwen3.8-max" to price(QWEN_PRICE_VERSION, "1.65", "4.951"),
        "deepseek-v4-pro" to price(DEEPSEEK_PRICE_VERSION, "0.435", "0.87", "0.003625"),
    )

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
        "qwen3.7-plus" to listOf(
            InputTier(256_000, price(QWEN_PRICE_VERSION, "0.276", "1.101")),
            InputTier(1_000_000, price(QWEN_PRICE_VERSION, "0.826", "3.301")),
        ),
        "qwen3.6-flash" to listOf(
            InputTier(256_000, price(QWEN_PRICE_VERSION, "0.165", "0.99")),
            InputTier(1_000_000, price(QWEN_PRICE_VERSION, "0.66", "3.961")),
        ),
    )

    fun estimate(modelId: String, usage: ProviderUsage): ProviderCost? {
        val input = usage.inputTokens ?: return null
        val output = usage.outputTokens ?: return null
        val price = prices[modelId.lowercase()]
            ?: inputTieredPrices[modelId.lowercase()]?.firstOrNull { input <= it.upperInclusiveInputTokens }?.price
            ?: return null
        val cachedInput = usage.cachedInputTokens?.coerceAtMost(input) ?: 0L
        val uncachedInput = input - cachedInput
        val cachedInputPrice = price.cachedInputPerMillionUsd ?: price.inputPerMillionUsd
        // USD/M token × token = microUSD, because one USD is one million microUSD.
        val micros = BigDecimal.valueOf(uncachedInput).multiply(price.inputPerMillionUsd)
            .add(BigDecimal.valueOf(cachedInput).multiply(cachedInputPrice))
            .add(BigDecimal.valueOf(output).multiply(price.outputPerMillionUsd))
            .setScale(0, RoundingMode.HALF_UP)
            .longValueExact()
        return ProviderCost(price.version, "USD", micros)
    }
}

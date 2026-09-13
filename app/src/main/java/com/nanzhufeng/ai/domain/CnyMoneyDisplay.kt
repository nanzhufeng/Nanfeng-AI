package com.nanzhufeng.ai.domain

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * User-facing RMB projection for immutable provider accounting facts.
 *
 * The stored amount and ISO currency remain untouched. USD is converted with the latest ECB
 * reference rates available when this version was built: on 2026-08-27, 1 EUR = 1.1645 USD and
 * 1 EUR = 7.8258 CNY, therefore 1 USD = 6.7203091455560326320 CNY.
 */
object CnyMoneyDisplay {
    const val REFERENCE_DATE = "2026-08-27"
    private val microsPerUnit = BigDecimal("1000000")
    private val usdToCny = BigDecimal("6.7203091455560326320")

    fun amountText(
        totalMicros: Long,
        currencyCode: String?,
        maximumFractionDigits: Int = 6,
    ): String? = amountInYuan(totalMicros, currencyCode)
        ?.setScale(maximumFractionDigits, RoundingMode.HALF_UP)
        ?.stripTrailingZeros()
        ?.toPlainString()

    fun label(
        totalMicros: Long,
        currencyCode: String?,
        estimated: Boolean,
        maximumFractionDigits: Int = 6,
    ): String? {
        val amount = amountText(totalMicros, currencyCode, maximumFractionDigits) ?: return null
        return "¥$amount"
    }

    fun totalLabel(
        costs: List<ProviderCost>,
        estimated: Boolean,
        maximumFractionDigits: Int = 6,
    ): String? {
        val known = costs.filter { it.totalMicros != null }
        if (known.isEmpty()) return null
        val amounts = known.map { cost ->
            amountInYuan(requireNotNull(cost.totalMicros), cost.currencyCode) ?: return null
        }
        val amount = amounts.fold(BigDecimal.ZERO, BigDecimal::add)
            .setScale(maximumFractionDigits, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()
        return "¥$amount"
    }

    /** Compact summary cards show one blended RMB total without extra accounting annotations. */
    fun summaryLabel(
        costs: List<ProviderCost>,
        maximumFractionDigits: Int = 6,
    ): String? {
        val known = costs.filter { it.totalMicros != null }
        if (known.isEmpty()) return null
        val amounts = known.map { cost ->
            amountInYuan(requireNotNull(cost.totalMicros), cost.currencyCode) ?: return null
        }
        val amount = amounts.fold(BigDecimal.ZERO, BigDecimal::add)
            .setScale(maximumFractionDigits, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()
        return "¥$amount"
    }

    private fun amountInYuan(totalMicros: Long, currencyCode: String?): BigDecimal? {
        val sourceAmount = BigDecimal.valueOf(totalMicros).divide(microsPerUnit)
        return when (currencyCode) {
            "CNY" -> sourceAmount
            "USD" -> sourceAmount.multiply(usdToCny)
            else -> null
        }
    }
}

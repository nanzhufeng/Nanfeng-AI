package com.nanzhufeng.ai.ui

import java.math.RoundingMode

/** Pure footer projection kept outside Compose class initialization for ordinary JVM coverage. */
internal fun assistantFooterCostDisplay(label: String?): String? = label?.split(" / ")
    ?.joinToString(" / ") { entry ->
        val amount = Regex("[¥￥]([+-]?\\d+(?:\\.\\d+)?)").find(entry)?.groupValues?.getOrNull(1)
            ?.toBigDecimalOrNull()
            ?.setScale(4, RoundingMode.HALF_UP)
            ?.toPlainString()
        amount?.let { "¥$it" } ?: entry
    }

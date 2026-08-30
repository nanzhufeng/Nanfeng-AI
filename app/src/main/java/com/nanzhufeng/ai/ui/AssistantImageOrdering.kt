package com.nanzhufeng.ai.ui

/** Assistant multi-image blocks arrive highest/newest first; every visual consumer uses low-to-high order. */
internal fun <T> ascendingAssistantImageOrder(highToLow: List<T>): List<T> = highToLow.asReversed()

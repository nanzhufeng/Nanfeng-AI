package com.nanzhufeng.ai.ui

private const val ComposerDraftTextBaseSizeSp = 16f

internal fun composerNativeTextSizeSp(appTextScale: Float): Float =
    ComposerDraftTextBaseSizeSp * appTextScale

package com.nanzhufeng.ai.ui

internal enum class RootCanvasKind { CONVERSATION, SETTINGS, DOCUMENT }

/** Keeps route ownership independent from Compose colors so every level follows one policy. */
internal fun rootCanvasKind(route: P5ARoute): RootCanvasKind = when (route) {
    P5ARoute.OCR -> RootCanvasKind.DOCUMENT
    P5ARoute.CAPTURE, P5ARoute.CONVERSATION -> RootCanvasKind.CONVERSATION
    else -> RootCanvasKind.SETTINGS
}

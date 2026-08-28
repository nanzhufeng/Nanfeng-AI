package com.nanzhufeng.ai.ui

/** A tall original opens width-filled and top-aligned, like a document, while ordinary images
 * remain wholly visible. Both branches preserve the bitmap's aspect ratio. */
internal fun initialOriginalImageScale(
    sourceWidthPx: Float,
    sourceHeightPx: Float,
    viewportWidthPx: Float,
    viewportHeightPx: Float,
): Float {
    val safeSourceWidth = sourceWidthPx.coerceAtLeast(1f)
    val safeSourceHeight = sourceHeightPx.coerceAtLeast(1f)
    val safeViewportWidth = viewportWidthPx.coerceAtLeast(1f)
    val safeViewportHeight = viewportHeightPx.coerceAtLeast(1f)
    val widthScale = safeViewportWidth / safeSourceWidth
    val heightScale = safeViewportHeight / safeSourceHeight
    val isTallerThanViewport = safeSourceHeight / safeSourceWidth > safeViewportHeight / safeViewportWidth
    return if (isTallerThanViewport) widthScale else minOf(widthScale, heightScale)
}

/** Six gesture steps remain available for ordinary images; very large originals are never capped
 * before the bitmap reaches one source pixel per rendered pixel. */
internal fun maximumOriginalImageZoom(initialScale: Float): Float =
    maxOf(6f, 1f / initialScale.coerceAtLeast(0.000_001f))

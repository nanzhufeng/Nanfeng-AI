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

internal const val ImagePreviewDoubleTapZoom = 2.5f

internal data class ImagePreviewTransform(
    val zoom: Float,
    val offsetX: Float,
    val offsetY: Float,
)

/**
 * One stable viewport owns pinch and pan. The rendered image may be remeasured after every event,
 * so gesture math uses viewport coordinates and the latest placed rectangle rather than a moving
 * Image node's local coordinates.
 */
internal fun imagePreviewGestureTransform(
    currentZoom: Float,
    zoomChange: Float,
    panX: Float,
    panY: Float,
    focusX: Float,
    focusY: Float,
    placedX: Float,
    placedY: Float,
    fittedWidthPx: Float,
    fittedHeightPx: Float,
    viewportWidthPx: Float,
    viewportHeightPx: Float,
    maximumZoom: Float,
): ImagePreviewTransform {
    val safeCurrentZoom = currentZoom.coerceIn(1f, maximumZoom.coerceAtLeast(1f))
    val nextZoom = (safeCurrentZoom * zoomChange.coerceAtLeast(0.01f))
        .coerceIn(1f, maximumZoom.coerceAtLeast(1f))
    val ratio = nextZoom / safeCurrentZoom
    val targetWidthPx = fittedWidthPx * nextZoom
    val targetHeightPx = fittedHeightPx * nextZoom
    val targetX = focusX - (focusX - placedX) * ratio + panX
    val targetY = focusY - (focusY - placedY) * ratio + panY
    return ImagePreviewTransform(
        zoom = nextZoom,
        offsetX = if (targetWidthPx <= viewportWidthPx) 0f else targetX.coerceIn(viewportWidthPx - targetWidthPx, 0f),
        offsetY = if (targetHeightPx <= viewportHeightPx) 0f else targetY.coerceIn(viewportHeightPx - targetHeightPx, 0f),
    )
}

/** Double tap is a binary image-only action: enlarge around the touched source point, then reset. */
internal fun imagePreviewDoubleTapTransform(
    currentZoom: Float,
    focusX: Float,
    focusY: Float,
    placedX: Float,
    placedY: Float,
    fittedWidthPx: Float,
    fittedHeightPx: Float,
    viewportWidthPx: Float,
    viewportHeightPx: Float,
    maximumZoom: Float,
): ImagePreviewTransform {
    if (currentZoom > 1.001f) return ImagePreviewTransform(zoom = 1f, offsetX = 0f, offsetY = 0f)

    val targetZoom = ImagePreviewDoubleTapZoom.coerceAtMost(maximumZoom.coerceAtLeast(1f))
    return imagePreviewGestureTransform(
        currentZoom = currentZoom,
        zoomChange = targetZoom / currentZoom.coerceAtLeast(0.000_001f),
        panX = 0f,
        panY = 0f,
        focusX = focusX,
        focusY = focusY,
        placedX = placedX,
        placedY = placedY,
        fittedWidthPx = fittedWidthPx,
        fittedHeightPx = fittedHeightPx,
        viewportWidthPx = viewportWidthPx,
        viewportHeightPx = viewportHeightPx,
        maximumZoom = maximumZoom,
    )
}

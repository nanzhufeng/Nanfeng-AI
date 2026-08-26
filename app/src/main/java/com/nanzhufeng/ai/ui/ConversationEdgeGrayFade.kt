package com.nanzhufeng.ai.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private const val TopEdgeFadeOpaqueAlpha = 1f
private const val BottomEdgeFadeOpaqueAlpha = 0.98f

/**
 * Uses only the conversation canvas colour. The top curve is deliberately stronger than the
 * bottom curve while both retain their approved ranges; it paints no blur, alpha-mixed text,
 * RenderEffect, or side treatment.
 */
@Composable
internal fun Modifier.conversationEdgeGrayFade(
    topBand: Dp = 128.dp,
    bottomBand: Dp = 112.dp,
    edgeColor: Color = PageBackground,
): Modifier {
    val density = LocalDensity.current
    val topBandPx = with(density) { topBand.toPx() }
    val bottomBandPx = with(density) { bottomBand.toPx() }
    val topOpaqueEdge = edgeColor.copy(alpha = TopEdgeFadeOpaqueAlpha)
    val bottomOpaqueEdge = edgeColor.copy(alpha = BottomEdgeFadeOpaqueAlpha)
    return drawWithContent {
        drawContent()
        val topEnd = topBandPx.coerceAtMost(size.height * 0.4f)
        val bottomStart = (size.height - bottomBandPx.coerceAtMost(size.height * 0.4f)).coerceAtLeast(0f)
        if (topEnd > 0f) {
            drawRect(
                brush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to topOpaqueEdge,
                        0.25f to edgeColor.copy(alpha = 0.90f),
                        0.5f to edgeColor.copy(alpha = 0.62f),
                        0.75f to edgeColor.copy(alpha = 0.22f),
                        1f to Color.Transparent,
                    ),
                    startY = 0f,
                    endY = topEnd,
                ),
            )
        }
        if (bottomStart < size.height) {
            drawRect(
                brush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to Color.Transparent,
                        0.25f to edgeColor.copy(alpha = 0.153f),
                        0.5f to edgeColor.copy(alpha = 0.49f),
                        0.75f to edgeColor.copy(alpha = 0.827f),
                        1f to bottomOpaqueEdge,
                    ),
                    startY = bottomStart,
                    endY = size.height,
                ),
            )
        }
    }
}

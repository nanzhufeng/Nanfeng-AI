package com.nanzhufeng.ai.ui

import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

/** Compact labels must not inherit a paragraph line height or platform font padding. */
@Composable
internal fun centeredControlTextStyle(fontSize: TextUnit, base: TextStyle = LocalTextStyle.current): TextStyle =
    base.copy(fontSize = fontSize, lineHeight = fontSize * 1.2f,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None))

/** Measures fallback CJK and Latin glyphs; nominal SP alone can undercount their actual height. */
@Composable
internal fun measuredControlTextHeight(style: TextStyle): Dp {
    val layout = androidx.compose.ui.text.rememberTextMeasurer().measure("国Ag", style = style, maxLines = 1)
    val pixels = kotlin.math.ceil(maxOf(layout.size.height.toFloat(), layout.multiParagraph.height))
    return with(LocalDensity.current) { pixels.toDp() }
}

@Composable
internal fun singleLineControlHeight(minimum: Dp, fontSize: TextUnit, verticalPadding: Dp = 8.dp): Dp =
    (measuredControlTextHeight(centeredControlTextStyle(fontSize)) + verticalPadding).coerceAtLeast(minimum)

@Composable
internal fun singleLineControlWidth(minimum: Dp, label: String, fontSize: TextUnit): Dp {
    val pixels = androidx.compose.ui.text.rememberTextMeasurer().measure(label, style = centeredControlTextStyle(fontSize)).size.width
    return with(LocalDensity.current) { (pixels.toDp() + 8.dp).coerceAtLeast(minimum) }
}

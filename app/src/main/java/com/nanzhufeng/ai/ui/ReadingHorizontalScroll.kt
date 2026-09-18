package com.nanzhufeng.ai.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

internal val LocalReadingDrawerOpen = staticCompositionLocalOf<(() -> Unit)?> { null }

/** Only user movement left over at the content's start can become a drawer gesture. */
internal class ReadingDrawerEdgeHandoff(private val threshold: Float) {
    private var distance = 0f

    fun reset() { distance = 0f }

    fun scroll(consumedX: Float, remainingX: Float, atStart: Boolean, userInput: Boolean) {
        if (!userInput) return
        if (consumedX != 0f || remainingX <= 0f || !atStart) reset()
        if (atStart && remainingX > 0f) distance += remainingX
    }

    fun finish(): Boolean {
        val open = distance >= threshold
        reset()
        return open
    }
}

/** Child scroll wins; only its unconsumed rightward distance may open the drawer on release. */
@Composable
internal fun Modifier.readingHorizontalScroll(state: ScrollState): Modifier {
    val openDrawer = LocalReadingDrawerOpen.current
    if (openDrawer == null) return horizontalScroll(state)
    val currentOpenDrawer = rememberUpdatedState(openDrawer)
    val threshold = with(LocalDensity.current) { 36.dp.toPx() }
    val handoff = remember(state, threshold) { ReadingDrawerEdgeHandoff(threshold) }
    // Pre-fling also runs after drag cancellation. Only a real DragInteraction.Stop may open.
    LaunchedEffect(state, handoff) {
        state.interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start, is DragInteraction.Cancel -> handoff.reset()
                is DragInteraction.Stop -> if (handoff.finish()) currentOpenDrawer.value()
            }
        }
    }
    val connection = remember(state, handoff) {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                val userInput = source == NestedScrollSource.UserInput
                val atStart = !state.canScrollBackward
                handoff.scroll(consumed.x, available.x, atStart, userInput)
                return if (userInput && atStart && available.x > 0f) Offset(available.x, 0f) else Offset.Zero
            }
        }
    }
    return this
        .nestedScroll(connection)
        .horizontalScroll(state)
}

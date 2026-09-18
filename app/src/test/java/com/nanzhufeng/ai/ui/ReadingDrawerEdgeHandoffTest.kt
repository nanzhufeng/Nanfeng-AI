package com.nanzhufeng.ai.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingDrawerEdgeHandoffTest {
    @Test fun contentMovementNeverCountsAsDrawerTravel() {
        val drag = ReadingDrawerEdgeHandoff(36f)
        drag.scroll(200f, 0f, atStart = false, userInput = true)
        assertFalse(drag.finish())
    }
    @Test fun reachingStartCountsOnlyTheRemainingDistance() {
        val drag = ReadingDrawerEdgeHandoff(36f)
        drag.scroll(180f, 12f, atStart = true, userInput = true)
        assertFalse(drag.finish())
        drag.scroll(180f, 12f, atStart = true, userInput = true)
        drag.scroll(0f, 25f, atStart = true, userInput = true)
        assertTrue(drag.finish())
    }
    @Test fun rightSwipeAtStartOpensOnce() {
        val drag = ReadingDrawerEdgeHandoff(36f)
        drag.scroll(0f, 40f, atStart = true, userInput = true)
        assertTrue(drag.finish())
        assertFalse(drag.finish())
    }
    @Test fun oppositeDirectionResetsIntent() {
        val drag = ReadingDrawerEdgeHandoff(36f)
        drag.scroll(0f, 30f, atStart = true, userInput = true)
        drag.scroll(-10f, 0f, atStart = false, userInput = true)
        drag.scroll(10f, 12f, atStart = true, userInput = true)
        assertFalse(drag.finish())
    }
    @Test fun flingAndProgrammaticMovementCannotOpenDrawer() {
        val drag = ReadingDrawerEdgeHandoff(36f)
        drag.scroll(0f, 500f, atStart = true, userInput = false)
        assertFalse(drag.finish())
    }
    @Test fun separateShortGesturesNeverAccumulate() {
        val drag = ReadingDrawerEdgeHandoff(36f)
        repeat(4) {
            drag.reset()
            drag.scroll(0f, 20f, atStart = true, userInput = true)
            assertFalse(drag.finish())
        }
    }
    @Test fun remainderAwayFromStartCannotOpenDrawer() {
        val drag = ReadingDrawerEdgeHandoff(36f)
        drag.scroll(0f, 300f, atStart = false, userInput = true)
        assertFalse(drag.finish())
    }
}

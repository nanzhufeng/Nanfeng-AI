package com.nanzhufeng.ai.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoTimelineSeekPolicyTest {
    private val track = VideoTimelineTrackBounds(left = 28f, top = 700f, right = 372f, bottom = 718f)

    @Test fun `tap and drag use the measured track rather than full screen width`() {
        assertEquals(0L, track.positionAt(28f, 60_000L))
        assertEquals(30_000L, track.positionAt(200f, 60_000L))
        assertEquals(60_000L, track.positionAt(372f, 60_000L))
        assertEquals(0L, track.positionAt(-100f, 60_000L))
        assertEquals(60_000L, track.positionAt(900f, 60_000L))
    }

    @Test fun `expanded hit area is forgiving without stealing distant controls`() {
        assertTrue(track.contains(200f, 682f, horizontalSlop = 24f, verticalSlop = 20f))
        assertTrue(track.contains(5f, 710f, horizontalSlop = 24f, verticalSlop = 20f))
        assertFalse(track.contains(200f, 670f, horizontalSlop = 24f, verticalSlop = 20f))
        assertFalse(track.contains(200f, 750f, horizontalSlop = 24f, verticalSlop = 20f))
    }

    @Test fun `invalid duration or unmeasured width cannot seek beyond zero`() {
        assertEquals(0L, track.positionAt(200f, 0L))
        assertEquals(0L, VideoTimelineTrackBounds(10f, 0f, 10f, 20f).positionAt(10f, 60_000L))
    }
}

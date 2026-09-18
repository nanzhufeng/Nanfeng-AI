package com.nanzhufeng.ai.ui

import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.time.Duration

/** Host-only real Android touch dispatch through the production Compose modifiers. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class, qualifiers = "mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
class ReadingHorizontalTouchTest {
    @get:Rule val compose = createEmptyComposeRule()
    private fun exercise(reading: Boolean = true, contentWidth: Int = 1000, block: Fixture.() -> Unit) {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        try {
            val fixture = Fixture(controller.get(), reading, contentWidth)
            fixture.mount()
            fixture.block()
        } finally { controller.pause().stop().destroy() }
    }

    private inner class Fixture(val activity: ComponentActivity, val reading: Boolean, val contentWidth: Int) {
        val scroll = ScrollState(0)
        val drawer = DrawerState(DrawerValue.Closed)
        var opens = 0
        var bounds = Rect.Zero
        val deltas = mutableListOf<String>()
        val trace = object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                deltas += "${consumed.x}/${available.x}"
                return Offset.Zero
            }
        }
        private var downTime = 0L
        private val decor get() = activity.window.decorView
        private val canvas = android.graphics.Canvas(android.graphics.Bitmap.createBitmap(300, 400, android.graphics.Bitmap.Config.ARGB_8888))
        fun pump() {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))
            decor.measure(View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY))
            decor.layout(0, 0, 300, 400)
            decor.draw(canvas)
            compose.waitForIdle()
        }
        fun mount() {
            activity.setContent {
                MaterialTheme {
                    val scope = rememberCoroutineScope()
                    val open: () -> Unit = { opens++; scope.launch { drawer.open() }; Unit }
                    CompositionLocalProvider(LocalReadingDrawerOpen provides open) {
                        ModalNavigationDrawer(drawerState = drawer, gesturesEnabled = true,
                            drawerContent = { ModalDrawerSheet { Box(Modifier.size(240.dp, 300.dp)) } }) {
                            Box(Modifier.size(300.dp).nestedScroll(trace).quickConversationDrawerOpen(open)) {
                                val scrollModifier = if (reading) Modifier.readingHorizontalScroll(scroll) else Modifier.horizontalScroll(scroll)
                                Box(Modifier.size(300.dp, 150.dp).onGloballyPositioned { bounds = it.boundsInWindow() }.then(scrollModifier)) {
                                    Box(Modifier.size(contentWidth.dp, 150.dp))
                                }
                            }
                        }
                    }
                }
            }
            repeat(4) {
                decor.measure(View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY))
                decor.layout(0, 0, 300, 400)
                pump()
            }
            assertEquals("Fixture overflow must match content width", (contentWidth - 300).coerceAtLeast(0), scroll.maxValue)
        }
        fun touch(action: Int, x: Float, yOffset: Float = 0f) {
            if (action == MotionEvent.ACTION_DOWN) downTime = SystemClock.uptimeMillis()
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, 1,
                arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_FINGER }),
                arrayOf(MotionEvent.PointerCoords().apply { this.x = bounds.left + x; y = bounds.center.y + yOffset; pressure = 1f; size = 1f }),
                0, 0, 1f, 1f, 0, 0, android.view.InputDevice.SOURCE_TOUCHSCREEN, 0)
            try { decor.dispatchTouchEvent(event) } finally { event.recycle() }
            pump()
        }
        fun rightDrag(end: Int) {
            touch(MotionEvent.ACTION_DOWN, 40f)
            for (x in 60..160 step 20) touch(MotionEvent.ACTION_MOVE, x.toFloat())
            touch(end, 160f)
            repeat(8) { pump() }
        }
    }

    @Test fun cancelledEdgeDragDoesNotOpenDrawer() = exercise {
        rightDrag(MotionEvent.ACTION_CANCEL)
        assertEquals(0, opens)
        assertTrue(drawer.isClosed)
    }

    @Test fun releasedEdgeDragOpensExactlyOnce() = exercise {
        rightDrag(MotionEvent.ACTION_UP)
        assertEquals(1, opens)
        assertTrue(drawer.isOpen)
    }

    @Test fun scrollableContentWinsOverCanvasShortcut() = exercise {
        scroll.dispatchRawDelta(400f)
        pump()
        assertEquals(400, scroll.value)
        rightDrag(MotionEvent.ACTION_UP)
        assertTrue("Touch must actually scroll the child: value=${scroll.value}, opens=$opens, bounds=$bounds, deltas=$deltas", scroll.value < 400)
        assertTrue("Child must still be away from start", scroll.value > 0)
        assertEquals(0, opens)
        assertTrue(drawer.isClosed)
    }

    @Test fun leftDragScrollsContent() = exercise {
        touch(MotionEvent.ACTION_DOWN, 240f)
        for (x in 220 downTo 120 step 20) touch(MotionEvent.ACTION_MOVE, x.toFloat())
        touch(MotionEvent.ACTION_UP, 120f)
        repeat(8) { pump() }
        assertTrue("Left swipe must scroll: value=${scroll.value}, max=${scroll.maxValue}, deltas=$deltas", scroll.value > 0)
        assertEquals(0, opens)
        assertTrue(drawer.isClosed)
    }

    @Test fun fixtureCanScrollPlainCompose() = exercise(reading = false) {
        touch(MotionEvent.ACTION_DOWN, 240f)
        for (x in 220 downTo 120 step 20) touch(MotionEvent.ACTION_MOVE, x.toFloat())
        touch(MotionEvent.ACTION_UP, 120f)
        repeat(8) { pump() }
        assertTrue("Plain scroll fixture: $deltas", scroll.value > 0)
    }

    @Test fun sameDragReachesStartThenHandsOffOnlyExcess() = exercise {
        scroll.dispatchRawDelta(30f)
        pump()
        rightDrag(MotionEvent.ACTION_UP)
        assertEquals(0, scroll.value)
        assertEquals(1, opens)
        assertTrue(drawer.isOpen)
    }

    @Test fun separateShortDragsDoNotAccumulate() = exercise {
        repeat(3) {
            touch(MotionEvent.ACTION_DOWN, 40f)
            touch(MotionEvent.ACTION_MOVE, 50f)
            touch(MotionEvent.ACTION_MOVE, 65f)
            touch(MotionEvent.ACTION_UP, 65f)
        }
        assertEquals(0, opens)
        assertTrue(drawer.isClosed)
    }

    @Test fun reversingBeforeReleaseDoesNotOpenDrawer() = exercise {
        touch(MotionEvent.ACTION_DOWN, 40f)
        for (x in 60..140 step 20) touch(MotionEvent.ACTION_MOVE, x.toFloat())
        for (x in 120 downTo 60 step 20) touch(MotionEvent.ACTION_MOVE, x.toFloat())
        touch(MotionEvent.ACTION_UP, 60f)
        assertEquals(0, opens)
        assertTrue(drawer.isClosed)
    }

    @Test fun verticalDragDoesNotOpenDrawer() = exercise {
        touch(MotionEvent.ACTION_DOWN, 100f, -40f)
        for (y in -20..40 step 20) touch(MotionEvent.ACTION_MOVE, 100f, y.toFloat())
        touch(MotionEvent.ACTION_UP, 100f, 40f)
        assertEquals(0, opens)
        assertTrue(drawer.isClosed)
    }

    @Test fun cancelledDragDoesNotContaminateNextShortGesture() = exercise {
        rightDrag(MotionEvent.ACTION_CANCEL)
        touch(MotionEvent.ACTION_DOWN, 40f)
        touch(MotionEvent.ACTION_MOVE, 60f)
        touch(MotionEvent.ACTION_UP, 60f)
        assertEquals(0, opens)
        assertTrue(drawer.isClosed)
    }

    @Test fun contentWithoutOverflowStillHandsOffAtStart() = exercise(contentWidth = 200) {
        rightDrag(MotionEvent.ACTION_UP)
        assertEquals(1, opens)
        assertTrue(drawer.isOpen)
    }

    @Test fun ordinaryCanvasRightSwipeStillOpensDrawer() = exercise {
        touch(MotionEvent.ACTION_DOWN, 40f, 110f)
        for (x in 60..160 step 20) touch(MotionEvent.ACTION_MOVE, x.toFloat(), 110f)
        touch(MotionEvent.ACTION_UP, 160f, 110f)
        assertEquals(1, opens)
        assertTrue(drawer.isOpen)
    }
}

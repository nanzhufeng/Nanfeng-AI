package com.nanzhufeng.ai.ui

import android.app.Activity
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MainContentDrawGateTest {
    @Test fun `only actual content is allowed to become the first application frame`() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val root = controller.get().window.decorView
            val gate = MainContentDrawGate(root)
            assertTrue("No blank or loading frame before content", root.viewTreeObserver.dispatchOnPreDraw())
            controller.get().setContentView(TextView(controller.get()).apply { text = "Conversation content" })
            gate.release()
            assertFalse("Main content draws immediately, without a timer", root.viewTreeObserver.dispatchOnPreDraw())
            assertFalse(root.viewTreeObserver.dispatchOnPreDraw())
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun `destroying during initialization removes the drawing gate`() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val root = controller.get().window.decorView
            val gate = MainContentDrawGate(root)
            gate.dispose()
            assertFalse(root.viewTreeObserver.dispatchOnPreDraw())
        } finally { controller.pause().stop().destroy() }
    }
}

package com.nanzhufeng.ai.ui

import com.nanzhufeng.ai.NanfengAiActivity
import com.nanzhufeng.ai.NanfengWorkActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class ChatOnlyEntryActivityTest {
    @Test fun `retained work activity redirects to chat and safely destroys without initializing work`() {
        val controller = Robolectric.buildActivity(NanfengWorkActivity::class.java).create()
        val activity = controller.get()
        assertTrue(activity.isFinishing)
        assertEquals(NanfengAiActivity::class.java.name, shadowOf(activity).nextStartedActivity.component?.className)
        controller.destroy()
    }
}

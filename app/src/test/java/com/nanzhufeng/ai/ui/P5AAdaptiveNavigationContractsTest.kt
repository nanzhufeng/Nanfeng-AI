package com.nanzhufeng.ai.ui

import androidx.lifecycle.SavedStateHandle
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class P5AAdaptiveNavigationContractsTest {
    @Test
    fun `wide window uses expanded workbench unless large font requires compact`() {
        assertEquals(P5AWindowLayout.COMPACT, classifyP5AWindow(839, 1.0f))
        assertEquals(P5AWindowLayout.EXPANDED, classifyP5AWindow(840, 1.0f))
        assertEquals(P5AWindowLayout.COMPACT, classifyP5AWindow(1200, 1.5f))
        assertEquals(P5AWindowLayout.COMPACT, classifyP5AWindow(1200, 2.0f))
    }

    @Test
    fun `route survives recreated navigation owner and explicit intent route wins`() {
        val handle = SavedStateHandle()
        val first = P5ANavigationViewModel(handle)
        first.select(P5ARoute.CONTEXT)

        val recreated = P5ANavigationViewModel(handle)
        assertEquals(P5ARoute.CONTEXT, recreated.state.route)

        recreated.restoreFromIntent(null, P5ARoute.CONVERSATION.wireValue)
        assertEquals(P5ARoute.CONVERSATION, recreated.state.route)
    }

    @Test
    fun `unknown route safely falls back without replacing restored route`() {
        assertEquals(P5ARoute.CAPTURE, resolveP5ARoute(null, "not-a-route"))
        assertEquals(P5ARoute.KNOWLEDGE, resolveP5ARoute("android.intent.action.VIEW", null, "knowledge"))

        val handle = SavedStateHandle(mapOf("p5a_route" to P5ARoute.MEMORY.wireValue))
        val viewModel = P5ANavigationViewModel(handle)
        viewModel.restoreFromIntent(null, "not-a-route")
        assertEquals(P5ARoute.MEMORY, viewModel.state.route)
    }

    @Test
    fun `persisted route restores after process recreation without an explicit intent`() {
        val viewModel = P5ANavigationViewModel(SavedStateHandle())
        viewModel.restoreFromIntent(null, null, persistedRoute = P5ARoute.KNOWLEDGE.wireValue)
        assertEquals(P5ARoute.KNOWLEDGE, viewModel.state.route)
    }

    @Test
    fun `side exiting settings persists the conversation drawer snapshot`() {
        val handle = SavedStateHandle()
        val viewModel = P5ANavigationViewModel(handle)

        viewModel.select(P5ARoute.SETTINGS)
        viewModel.exitSettingsToConversationDrawer()

        assertEquals(P5ARoute.CONVERSATION, viewModel.state.route)
        assertTrue(viewModel.state.conversationDrawerOpen)
        assertEquals(viewModel.state, P5ANavigationViewModel(handle).state)
    }

    @Test
    fun `launcher restores the exact settings or conversation drawer snapshot`() {
        val settings = P5ANavigationViewModel(SavedStateHandle())
        settings.restoreFromIntent(null, null, persistedRoute = P5ARoute.SETTINGS.wireValue, persistedConversationDrawerOpen = true)
        assertEquals(P5ARoute.SETTINGS, settings.state.route)
        assertFalse(settings.state.conversationDrawerOpen)

        val drawer = P5ANavigationViewModel(SavedStateHandle())
        drawer.restoreFromIntent(null, null, persistedRoute = P5ARoute.CONVERSATION.wireValue, persistedConversationDrawerOpen = true)
        assertEquals(P5ARoute.CONVERSATION, drawer.state.route)
        assertTrue(drawer.state.conversationDrawerOpen)
    }

    @Test
    fun `all primary routes have stable nonempty labels for semantic navigation`() {
        assertTrue(P5ARoute.values().all { it.label.isNotBlank() && it.wireValue.isNotBlank() })
        assertEquals("capture", P5ARoute.CAPTURE.wireValue)
        assertEquals("conversation", P5ARoute.CONVERSATION.wireValue)
    }

    @Test
    fun `adaptive navigation selection surface stays pure white`() {
        assertEquals(Color.White, P5ASelectionSurfaceColor)
        assertEquals(P5AInteractiveShape, P5AInteractiveShape)
    }

    @Test
    fun `compact more remains selected for every overflow control plane`() {
        assertTrue(P5ARoute.PROJECTS.isCompactOverflowRoute())
        assertTrue(P5ARoute.ADAPTERS.isCompactOverflowRoute())
        assertTrue(!P5ARoute.CONVERSATION.isCompactOverflowRoute())
    }

    @Test
    fun `tab traversal always leaves multiline editors in visual reading order`() {
        assertEquals(FocusDirection.Next, p5aTabFocusDirection(shiftPressed = false))
        assertEquals(FocusDirection.Previous, p5aTabFocusDirection(shiftPressed = true))
    }

    @Test
    fun `activity is the single ime resize owner so composer tracks the real keyboard top`() {
        val scaffold = File("src/main/java/com/nanzhufeng/ai/ui/P5AAdaptiveUi.kt").readText()
        val manifest = File("src/main/AndroidManifest.xml").readText()

        assertTrue(manifest.contains("android:windowSoftInputMode=\"adjustResize|stateAlwaysHidden\""))
        assertFalse(scaffold.contains(".imePadding()"))
        assertTrue(scaffold.contains("WindowInsets.safeDrawing.only"))
        assertTrue(scaffold.contains("route == P5ARoute.CONVERSATION"))
        assertTrue(scaffold.contains("WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom"))
    }

    @Test
    fun `folding between outer and inner displays preserves the live conversation viewport owner`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val workspace = File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()

        assertTrue(manifest.contains("android:configChanges=\"orientation|screenSize|screenLayout|smallestScreenSize\""))
        assertTrue(workspace.contains("val chatTranscriptListState = rememberLazyListState()"))
        assertTrue(workspace.contains("val workTranscriptListState = rememberLazyListState()"))
        assertTrue(workspace.contains("val activeTranscriptListState = if (workMode) workTranscriptListState else chatTranscriptListState"))
    }
}

package com.nanzhufeng.ai.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel

internal const val P5A_ROUTE_EXTRA = "com.nanzhufeng.ai.extra.P5A_ROUTE"
internal const val P5A_ROUTE_PREFERENCE_KEY = "last_route"
internal const val P5A_CONVERSATION_DRAWER_PREFERENCE_KEY = "conversation_drawer_open"
private const val P5A_ROUTE_STATE = "p5a_route"
private const val P5A_CONVERSATION_DRAWER_STATE = "p5a_conversation_drawer_open"
internal const val P5A_EXPANDED_MIN_WIDTH_DP = 840
internal const val P5A_COMPACT_FONT_SCALE = 1.5f
internal val P5ASelectionSurfaceColor = Color(0xFFFFFFFF)
/** The shared component owns visible shape, pressed indication, focus and hover contour together. */
/** Single-line foreground controls use one pill contour for surface, press and focus feedback. */
internal val P5AInteractiveShape = RoundedCornerShape(999.dp)

/** Stable UI routes only; business facts stay in their existing ViewModels and Room owners. */
internal enum class P5ARoute(val wireValue: String, val label: String) {
    CAPTURE("capture", "捕获"),
    CONVERSATION("conversation", "对话"),
    KNOWLEDGE("knowledge", "知识"),
    PROJECTS("projects", "项目"),
    MEMORY("memory", "记忆"),
    CONTEXT("context", "Context"),
    EVAL("eval", "评测"),
    SETTINGS("settings", "设置"),
    ADAPTERS("adapters", "导入与适配"),
    CONTROL("control", "工作区");

    companion object {
        fun fromWireValue(value: String?): P5ARoute? = entries.firstOrNull { it.wireValue == value }
    }
}

internal enum class P5AWindowLayout { COMPACT, EXPANDED }

internal fun P5ARoute.isCompactOverflowRoute(): Boolean = this in setOf(
    P5ARoute.PROJECTS,
    P5ARoute.MEMORY,
    P5ARoute.CONTEXT,
    P5ARoute.EVAL,
    P5ARoute.ADAPTERS,
    P5ARoute.CONTROL,
)

internal fun classifyP5AWindow(widthDp: Int, fontScale: Float): P5AWindowLayout =
    if (widthDp >= P5A_EXPANDED_MIN_WIDTH_DP && fontScale < P5A_COMPACT_FONT_SCALE) P5AWindowLayout.EXPANDED
    else P5AWindowLayout.COMPACT

/**
 * Multiline text inputs accept a literal tab by default. At the workbench boundary, Tab is a
 * navigation command instead so keyboard users can always leave an editor.
 */
internal fun p5aTabFocusDirection(shiftPressed: Boolean): FocusDirection =
    if (shiftPressed) FocusDirection.Previous else FocusDirection.Next

internal fun FocusManager.handleP5AKeyboardFocus(event: androidx.compose.ui.input.key.KeyEvent): Boolean =
    event.type == KeyEventType.KeyDown && event.key == Key.Tab && moveFocus(p5aTabFocusDirection(event.isShiftPressed))

/** Apply inside Dialog fields as well: Dialog content uses a separate Compose window. */
@Suppress("DEPRECATION")
internal fun Modifier.p5aKeyboardTraversal(): Modifier = composed {
    val focusManager = LocalFocusManager.current
    onPreviewKeyEvent(focusManager::handleP5AKeyboardFocus)
}

internal fun resolveP5ARoute(action: String?, routeExtra: String?, deepLinkRoute: String? = null): P5ARoute =
    P5ARoute.fromWireValue(routeExtra)
        ?: P5ARoute.fromWireValue(deepLinkRoute)
        ?: if (action == android.content.Intent.ACTION_SEND) P5ARoute.CAPTURE else P5ARoute.CAPTURE

/** The only restorable navigation snapshot: page plus the conversation drawer's visible state. */
internal data class P5AUiState(
    val route: P5ARoute = P5ARoute.CAPTURE,
    val conversationDrawerOpen: Boolean = false,
)

internal class P5ANavigationViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {
    var state by mutableStateOf(
        P5AUiState(
            route = P5ARoute.fromWireValue(savedStateHandle[P5A_ROUTE_STATE]) ?: P5ARoute.CAPTURE,
            conversationDrawerOpen = savedStateHandle[P5A_CONVERSATION_DRAWER_STATE] ?: false,
        ).normalized(),
    )
        private set

    fun select(route: P5ARoute) {
        setSnapshot(P5AUiState(route))
    }

    /** Side-exiting settings changes the complete snapshot in one operation. */
    fun exitSettingsToConversationDrawer() {
        setSnapshot(P5AUiState(route = P5ARoute.CONVERSATION, conversationDrawerOpen = true))
    }

    fun setConversationDrawerOpen(open: Boolean) {
        setSnapshot(P5AUiState(route = P5ARoute.CONVERSATION, conversationDrawerOpen = open))
    }

    fun restoreFromIntent(
        action: String?,
        routeExtra: String?,
        deepLinkRoute: String? = null,
        persistedRoute: String? = null,
        persistedConversationDrawerOpen: Boolean = false,
    ) {
        // A plain launcher/restart must preserve the exact saved snapshot; only an explicit route changes it.
        val explicitRoute = P5ARoute.fromWireValue(routeExtra) ?: P5ARoute.fromWireValue(deepLinkRoute)
        if (explicitRoute != null) select(explicitRoute)
        else if (action == android.content.Intent.ACTION_SEND) select(P5ARoute.CAPTURE)
        else P5ARoute.fromWireValue(persistedRoute)?.let { route ->
            setSnapshot(P5AUiState(route, route == P5ARoute.CONVERSATION && persistedConversationDrawerOpen))
        }
    }

    private fun setSnapshot(next: P5AUiState) {
        state = next.normalized()
        savedStateHandle[P5A_ROUTE_STATE] = state.route.wireValue
        savedStateHandle[P5A_CONVERSATION_DRAWER_STATE] = state.conversationDrawerOpen
    }
}

private fun P5AUiState.normalized(): P5AUiState =
    if (route == P5ARoute.CONVERSATION) this else copy(conversationDrawerOpen = false)

@Composable
internal fun P5AAdaptiveScaffold(
    route: P5ARoute,
    onRouteSelected: (P5ARoute) -> Unit,
    content: @Composable (P5AWindowLayout) -> Unit,
) {
    val fontScale = LocalConfiguration.current.fontScale
    val focusManager = LocalFocusManager.current
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent(focusManager::handleP5AKeyboardFocus),
    ) {
        val layout = classifyP5AWindow(maxWidth.value.toInt(), fontScale)
        // Conversation is edge-to-edge under both system bars. Its own floating header and
        // composer consume their real insets, so the transcript has no duplicate grey/white
        // bands at either edge and can scroll fully behind the transient controls.
        val contentInsets = if (route == P5ARoute.CONVERSATION) {
            WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
        } else {
            WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top + WindowInsetsSides.Bottom)
        }
        // FB-P6-023: one chat-first root; drawer/settings own navigation at every width.
        Box(
            modifier = Modifier
                .fillMaxSize()
                // The activity owns IME geometry through adjustResize. Applying imePadding here
                // would subtract the same keyboard height a second time and lift the composer far
                // above the real IME top.
                .windowInsetsPadding(contentInsets),
        ) { content(layout) }
        Text(
            text = "当前工作区：${route.label}",
            modifier = Modifier
                .padding(0.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            // The string is intentionally visually absent from layout; navigation items remain the
            // visible label, while this emits a concise state change to assistive services.
            color = Color.Transparent,
        )
    }
}

@Composable
private fun P5ARouteIcon(route: P5ARoute) {
    val image = when (route) {
        P5ARoute.CAPTURE -> Icons.Rounded.AutoAwesome
        P5ARoute.CONVERSATION -> Icons.Rounded.ChatBubbleOutline
        P5ARoute.KNOWLEDGE -> Icons.Rounded.Book
        P5ARoute.PROJECTS -> Icons.Rounded.FolderOpen
        P5ARoute.MEMORY -> Icons.Rounded.Memory
        P5ARoute.CONTEXT -> Icons.Rounded.Tune
        P5ARoute.EVAL -> Icons.Rounded.AutoAwesome
        P5ARoute.SETTINGS, P5ARoute.ADAPTERS -> Icons.Rounded.Settings
        P5ARoute.CONTROL -> Icons.Rounded.Tune
    }
    Icon(imageVector = image, contentDescription = null, modifier = Modifier.size(24.dp))
}

package com.nanzhufeng.ai.ui

import com.nanzhufeng.ai.ai.NormalChatOpenRouterExecutor
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.HapticFeedbackConstants
import android.widget.Toast
import android.widget.VideoView
import android.media.MediaPlayer
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.automirrored.rounded.CallSplit
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.res.painterResource
import com.nanzhufeng.ai.CONVERSATION_SHORTCUT_ID_EXTRA
import com.nanzhufeng.ai.NanfengAiActivity
import com.nanzhufeng.ai.R
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalScrollCaptureInProgress
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.onFocusChanged
import androidx.activity.compose.BackHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.core.view.WindowCompat
import com.nanzhufeng.ai.domain.ConversationActionKind
import com.nanzhufeng.ai.domain.ConversationListVirtualizationContract
import com.nanzhufeng.ai.domain.InlinePresentation
import com.nanzhufeng.ai.domain.ModelPresetId
import com.nanzhufeng.ai.domain.NanfengModelServiceCatalog
import com.nanzhufeng.ai.domain.MessageNodeId
import com.nanzhufeng.ai.domain.MessagePresentationRenderer
import com.nanzhufeng.ai.domain.PresentationBlock
import com.nanzhufeng.ai.domain.PresentationBlockIdentity
import com.nanzhufeng.ai.domain.PresentedMessage
import com.nanzhufeng.ai.domain.PresentedTranscriptMessage
import com.nanzhufeng.ai.domain.ContextSelectionAuditRecord
import com.nanzhufeng.ai.domain.AssistantResponseModelAttribution
import com.nanzhufeng.ai.domain.definition
import com.nanzhufeng.ai.domain.AnswerContextSourceDisclosure
import com.nanzhufeng.ai.domain.ConversationListScope
import com.nanzhufeng.ai.domain.ConversationManagementAction
import com.nanzhufeng.ai.domain.AttachmentId
import com.nanzhufeng.ai.domain.ConversationAttachmentReference
import com.nanzhufeng.ai.domain.ConversationAttachmentPreview
import com.nanzhufeng.ai.domain.ConversationAttachmentOriginalPreview
import com.nanzhufeng.ai.domain.ConversationAttachmentPdfPreview
import com.nanzhufeng.ai.domain.ConversationAttachmentVideoPreview
import com.nanzhufeng.ai.domain.ConversationAttachmentAudioPreview
import com.nanzhufeng.ai.domain.ConversationAttachmentTextPreview
import com.nanzhufeng.ai.domain.ConversationSearchCategory
import com.nanzhufeng.ai.domain.ConversationAttachmentSearchHit
import com.nanzhufeng.ai.domain.GlmOcrDocumentSearchHit
import com.nanzhufeng.ai.domain.ProjectId
import com.nanzhufeng.ai.domain.ProjectSnapshot
import com.nanzhufeng.ai.domain.TemporaryConversationRecovery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlin.math.abs
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

private val ComposerControlGlyphSize = 24.dp
// The attachment add mark deliberately has twice the previous arm length, while the rounded
// vector keeps both stroke ends soft inside the unchanged 48dp circular touch target.
private val ComposerAddGlyphSize = 30.dp
private val ComposerSendSurfaceSize = 36.dp
private val ComposerSendGlyphSize = 24.dp
private val ComposerStopGlyphSize = 22.5.dp
private val ComposerModelPickerHeaderHeight = 62.dp
private val ComposerModelPickerRootContentHeight = 280.dp
// Conversation overlays have an explicit stacking contract. The scroll-to-latest control stays
// above the composer, but every in-canvas transient surface must cover that control.
private const val ConversationScrollToLatestZIndex = 1f
private const val TranscriptJumpControlIdleHideMillis = 3_000L

private enum class TranscriptJumpDirection {
    TOP,
    BOTTOM,
}

private data class TranscriptScrollObservation(
    val itemIndex: Int,
    val itemOffset: Int,
    val userDragging: Boolean,
    val scrollInProgress: Boolean,
)
private const val ConversationModalOverlayZIndex = 2f
// Show the full concrete model number in the Composer.  The fixed width admits
// "5.6 Terra" without ellipsis while keeping the input lane usable on phones.
private val ComposerModelCompactDisplayWidth = 88.dp
private val ComposerModelExpandedDisplayWidth = 200.dp
private val ComposerModelExpandedBreakpoint = 600.dp
// Conversation chrome is intentionally neutral: the canvas recedes while white controls remain
// bright, and their boundaries never rely on harsh black outlines.
private val ConversationControlBorder: Color get() = NeutralBorder
// Chat, work, and temporary chat are one workspace.  Keep their canvas visibly below the
// white foreground controls, but neutral enough that no surface reads as a tinted card.
private val ConversationWorkspaceCanvas: Color get() = PageBackground
// Header controls stay clearly readable over the pale neutral canvas without
// reintroducing an outline or a narrow, dark edge shadow around their surfaces.
private val ConversationControlGlyph: Color get() = BodyText
// Settings owns a fixed capsule width. "新对话" remains content-measured so changing this
// icon-only control can never compress, wrap, or reposition the independent primary action.
private val ConversationDrawerSettingsPillWidth = 108.dp
// These are drawn by Compose itself, rather than as platform elevation shadows: elevation
// spreads a shadow but cannot make it denser on the very pale chat canvas.  Short, downward
// shadows give both surfaces a clearly visible lift without a black outline or backing tray.
private val ConversationHeaderForegroundShadow = Shadow(
    radius = 54.dp,
    color = Color(0x1B000000),
    offset = DpOffset(x = 0.dp, y = 3.dp),
)
private val ConversationComposerForegroundShadow = Shadow(
    radius = 84.dp,
    spread = 1.dp,
    color = Color(0x22000000),
    // The composer is a floating white surface; its ambient shadow must be evenly distributed
    // around the pill instead of visually weighting the transcript side or the navigation side.
    offset = DpOffset.Zero,
)
private val InlineCodeChipBackground: Color get() = NeutralSystemSurface
private const val InlineCodeChipAnnotationTag = "inline-code-chip"
/** Reading text reaches the edge without touching it; header and composer keep their own spacing. */
private val ConversationTranscriptPageGutter = 4.dp
// Assistant prose must keep a 24dp visual gutter on both sides. The right reading inset is
// 6dp smaller because the LazyColumn owns a separate 6dp passive-scrollbar lane.
private val ConversationAssistantReadingStartInset = 24.dp
private val ConversationAssistantReadingEndInset = 18.dp
// Every footer command owns the same hit box and the same gap. The row uses one shared optical
// inset so copy/share/more stay related without per-icon nudges.
private val AssistantFooterActionSpacing = 4.dp
// The first 16dp glyph sits optically 12dp inside its 36dp hit target. Moving the complete row
// by the same amount makes the visible copy glyph—not the invisible hit box—share the 24dp
// reading line with assistant prose while preserving every internal relationship in the row.
private val AssistantFooterGroupStartInset = 12.dp
/** Reserved only for the passive scroll position marker, never for a second blank column. */
private val ConversationScrollbarContentEndInset = 6.dp
// This is scrollable LazyColumn content, not a painted header backing. Short transcripts begin
// below the floating header; once there is enough content, the inset scrolls away naturally.
private val ConversationTranscriptContentPadding = androidx.compose.foundation.layout.PaddingValues(
    top = 104.dp,
    end = ConversationScrollbarContentEndInset,
    // This is scrollable content clearance for the true floating composer and navigation
    // gesture area, never a fixed page-bottom bar.
    bottom = 96.dp,
)
// The previous 1.10 enlargement made the reading column feel crowded on phones.  Applying
// 90% to that rendered size gives a true 10% reduction without shrinking chrome or hit areas.
private const val ConversationTextScaleFactor = 0.99f
private const val TransientMenuTextScaleFactor = 0.90f

private fun Modifier.conversationForegroundShadow(
    shape: Shape,
    shadow: Shadow = ConversationHeaderForegroundShadow,
): Modifier = dropShadow(shape = shape, shadow = shadow)

/**
 * Keeps the reading surfaces legible without globally scaling Composer controls, icons, or
 * touch targets. Both the drawer and rendered message body opt in to this same typography map.
 */
private fun TextStyle.scaledForConversationText(): TextStyle = copy(
    fontSize = fontSize * ConversationTextScaleFactor,
    lineHeight = lineHeight * ConversationTextScaleFactor,
)

private fun Typography.scaledForConversationText(): Typography = copy(
    displayLarge = displayLarge.scaledForConversationText(),
    displayMedium = displayMedium.scaledForConversationText(),
    displaySmall = displaySmall.scaledForConversationText(),
    headlineLarge = headlineLarge.scaledForConversationText(),
    headlineMedium = headlineMedium.scaledForConversationText(),
    headlineSmall = headlineSmall.scaledForConversationText(),
    titleLarge = titleLarge.scaledForConversationText(),
    titleMedium = titleMedium.scaledForConversationText(),
    titleSmall = titleSmall.scaledForConversationText(),
    bodyLarge = bodyLarge.scaledForConversationText(),
    bodyMedium = bodyMedium.scaledForConversationText(),
    bodySmall = bodySmall.scaledForConversationText(),
    labelLarge = labelLarge.scaledForConversationText(),
    labelMedium = labelMedium.scaledForConversationText(),
    labelSmall = labelSmall.scaledForConversationText(),
)

private fun TextStyle.scaledForTransientMenuText(): TextStyle = copy(
    fontSize = fontSize * TransientMenuTextScaleFactor,
    lineHeight = lineHeight * TransientMenuTextScaleFactor,
)

private fun Typography.scaledForTransientMenuText(): Typography = copy(
    displayLarge = displayLarge.scaledForTransientMenuText(),
    displayMedium = displayMedium.scaledForTransientMenuText(),
    displaySmall = displaySmall.scaledForTransientMenuText(),
    headlineLarge = headlineLarge.scaledForTransientMenuText(),
    headlineMedium = headlineMedium.scaledForTransientMenuText(),
    headlineSmall = headlineSmall.scaledForTransientMenuText(),
    titleLarge = titleLarge.scaledForTransientMenuText(),
    titleMedium = titleMedium.scaledForTransientMenuText(),
    titleSmall = titleSmall.scaledForTransientMenuText(),
    bodyLarge = bodyLarge.scaledForTransientMenuText(),
    bodyMedium = bodyMedium.scaledForTransientMenuText(),
    bodySmall = bodySmall.scaledForTransientMenuText(),
    labelLarge = labelLarge.scaledForTransientMenuText(),
    labelMedium = labelMedium.scaledForTransientMenuText(),
    labelSmall = labelSmall.scaledForTransientMenuText(),
)

@Composable
private fun scaledConversationTextUnit(value: androidx.compose.ui.unit.TextUnit) =
    scaledAppTextUnit(value * ConversationTextScaleFactor)

@Composable
private fun scaledTransientMenuTextUnit(value: androidx.compose.ui.unit.TextUnit) =
    scaledConversationTextUnit(value) * TransientMenuTextScaleFactor

/** Android's bundled rounded sans keeps the drawer identity friendly without shipping a font file. */
private val DrawerIdentityRoundedFontFamily = FontFamily(
    android.graphics.Typeface.create("sans-serif-rounded", android.graphics.Typeface.NORMAL),
)

/** A normal-only platform Typeface ignores the requested Compose weight on some devices.
 * Bind the Composer label to the real bold rounded face so the visible model name is bold. */
private val ComposerModelRoundedBoldFontFamily = FontFamily(
    android.graphics.Typeface.create("sans-serif-rounded", android.graphics.Typeface.BOLD),
)

/** Compact outer-display labels keep the phone composer usable; the picker and inner display use catalog names. */
private fun composerModelDisplayLabel(presets: List<ModelPresetId>): String =
    presets.joinToString(" / ") { preset ->
        com.nanzhufeng.ai.domain.NanfengModelServiceCatalog.preset(preset).displayName
            .let { com.nanzhufeng.ai.domain.composerModelShortNameForUser(it) }
    }

/** Wide inner displays have room to disclose the selected catalog name without abbreviation. */
private fun composerModelFullDisplayLabel(presets: List<ModelPresetId>): String =
    presets.joinToString(" / ") { preset ->
        com.nanzhufeng.ai.domain.NanfengModelServiceCatalog.preset(preset).displayName
    }

@Composable
private fun ConversationTextScale(content: @Composable () -> Unit) {
    val baseTypography = MaterialTheme.typography
    val scaledTypography = remember(baseTypography) { baseTypography.scaledForConversationText() }
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme,
        shapes = MaterialTheme.shapes,
        typography = scaledTypography,
        content = content,
    )
}

@Composable
private fun TransientMenuTextScale(content: @Composable () -> Unit) {
    val baseTypography = MaterialTheme.typography
    val compactTypography = remember(baseTypography) { baseTypography.scaledForTransientMenuText() }
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme,
        shapes = MaterialTheme.shapes,
        typography = compactTypography,
        content = content,
    )
}

/** A root overlay is deliberately outside the composer measurement tree. */
private enum class ComposerMenu { NONE, ATTACHMENTS, MODEL }

/**
 * Android's native paste/AI-writing toolbar belongs to the currently focused text field.
 * Clear only when a tap was not consumed by message content or another control, so an
 * intentional blank-canvas tap dismisses that transient toolbar without changing child actions.
 */
private fun Modifier.clearComposerFocusOnBlankTap(focusManager: FocusManager): Modifier = pointerInput(focusManager) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        val up = waitForUpOrCancellation()
        if (up != null && !up.isConsumed) focusManager.clearFocus(force = true)
    }
}

private data class ComposerAttachmentAction(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
)

internal val LocalAttachmentTransferRequest = staticCompositionLocalOf<(AttachmentId, AttachmentTransferAction) -> Unit> {
    { _, _ -> }
}

/** Sent attachment cards can reverse-locate their exact stable ID in the existing search page. */
private val LocalAttachmentSearchLocateRequest = staticCompositionLocalOf<(MessageNodeId, AttachmentId) -> Unit> {
    { _, _ -> }
}

/** Find is a visual projection over the current transcript, never a message mutation. */
private val LocalConversationFindQuery = staticCompositionLocalOf<String?> { null }

private data class ConversationFindMatch(
    val messageId: MessageNodeId,
    val segmentKey: String,
    val occurrenceIndex: Int,
)

private data class ConversationFindTarget(
    val match: ConversationFindMatch,
    val requestId: Long,
)

/** The selected occurrence is separate from the query so one long message can expose every hit. */
private val LocalConversationFindTarget = staticCompositionLocalOf<ConversationFindTarget?> { null }

/** Real local Room conversations only. This is intentionally a workspace, not a permanent QA card. */
@OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
)
@Composable
internal fun ConversationWorkspaceDialog(
    state: ConversationFoundationUiState,
    notificationReminderSettings: com.nanzhufeng.ai.domain.NotificationReminderSettings,
    onDismiss: () -> Unit,
    interceptsSystemBack: Boolean = false,
    searchDismissesToParent: Boolean = false,
    onCreate: () -> Unit,
    onSelect: (com.nanzhufeng.ai.domain.ConversationId) -> Unit,
    onClearWatchLater: (com.nanzhufeng.ai.domain.ConversationId) -> Unit,
    onSurfaceChanged: (com.nanzhufeng.ai.domain.ConversationSurface) -> Unit,
    onDraftChanged: (String) -> Unit,
    onSubmitDraft: (com.nanzhufeng.ai.domain.ConversationId?, com.nanzhufeng.ai.domain.NormalChatEgressAuthorization?) -> Unit,
    onRetryNormalSend: () -> Unit,
    onMarkNormalSendFailed: () -> Unit,
    onStartFixture: () -> Unit,
    onStartFailureFixture: () -> Unit,
    onStop: () -> Unit,
    onAction: (ConversationActionKind, ModelPresetId?) -> Unit,
    onSwitchBranch: (com.nanzhufeng.ai.domain.MessageNodeId) -> Unit,
    onBranchFromMessage: (com.nanzhufeng.ai.domain.MessageNodeId) -> Unit,
    onDismissBranchCreation: (com.nanzhufeng.ai.domain.ConversationId) -> Unit,
    onEditUserMessage: (com.nanzhufeng.ai.domain.MessageNodeId, String) -> Unit,
    onListScope: (ConversationListScope) -> Unit,
    onSearchChanged: (String) -> Unit,
    onSearchCategoryChanged: (ConversationSearchCategory) -> Unit,
    onSearchRequested: () -> Unit,
    onSearchFocus: () -> Unit,
    onCloseSearchHistory: () -> Unit,
    onFillSearchHistory: (String) -> Unit,
    onClearSearchHistory: () -> Unit,
    onCloseSearch: () -> Unit,
    onOpenSearchHit: (com.nanzhufeng.ai.domain.ConversationSearchHit) -> Unit,
    onOpenSearchAttachment: (com.nanzhufeng.ai.domain.ConversationAttachmentReference) -> Unit,
    onOpenGlmOcrSearchHit: (GlmOcrDocumentSearchHit) -> Unit,
    onDeleteGlmOcrSearchHit: (GlmOcrDocumentSearchHit) -> Unit,
    onLocateSearchAttachment: (ConversationAttachmentSearchHit) -> Unit,
    onDeleteSearchAttachment: (ConversationAttachmentSearchHit) -> Unit,
    onEnsureSearchAttachmentPreview: (com.nanzhufeng.ai.domain.ConversationAttachmentReference) -> Unit,
    onEnsureAttachmentPreview: (com.nanzhufeng.ai.domain.ConversationAttachmentReference) -> Unit,
    onManage: (com.nanzhufeng.ai.domain.Conversation, ConversationManagementAction, String?) -> Unit,
    onMarkWatchLater: (com.nanzhufeng.ai.domain.Conversation) -> Unit,
    onBatchSoftDelete: (List<com.nanzhufeng.ai.domain.Conversation>) -> Unit,
    onExport: () -> Unit,
    onSyncConversation: (com.nanzhufeng.ai.domain.Conversation) -> Unit,
    onCancelConversationSync: (com.nanzhufeng.ai.domain.Conversation) -> Unit,
    onReadCloudConversations: () -> Unit,
    syncNotice: String?,
    syncOperation: P7DAccountSyncOperation?,
    syncCompletedFeedback: P7DAccountSyncFeedback?,
    syncedConversationIds: Set<String>,
    cloudConversations: List<com.nanzhufeng.ai.domain.Conversation>,
    cloudPinnedConversationIds: Set<String>,
    onToggleCloudConversationPinned: (com.nanzhufeng.ai.domain.Conversation, Boolean) -> Unit,
    onAddCamera: () -> Unit,
    onAddImage: () -> Unit,
    onAddFile: () -> Unit,
    onRemoveAttachment: (AttachmentId) -> Unit,
    onOpenImagePreview: (AttachmentId) -> Unit,
    onCloseImagePreview: () -> Unit,
    onOpenPdfPreview: (AttachmentId) -> Unit,
    onOpenPdfPage: (Int) -> Unit,
    onClosePdfPreview: () -> Unit,
    onOpenVideoPreview: (AttachmentId) -> Unit,
    onCloseVideoPreview: (Long) -> Unit,
    onOpenAudioPreview: (AttachmentId) -> Unit,
    onCloseAudioPreview: (Long) -> Unit,
    onOpenTextPreview: (AttachmentId) -> Unit,
    onCloseTextPreview: () -> Unit,
    onOpenArchiveEntry: (com.nanzhufeng.ai.domain.AttachmentArchiveEntry) -> Unit,
    onRequestAttachmentTransfer: (AttachmentId, AttachmentTransferAction) -> Unit,
    onRequestAttachmentTransfers: (List<AttachmentId>, AttachmentTransferAction) -> Unit,
    onConsumeAttachmentTransfer: (AttachmentId) -> Unit,
    onEnterTemporary: () -> Unit,
    onUpdateTemporaryDraft: (String) -> Unit,
    onUpdateTemporaryModelOverride: (String?) -> Unit,
    onSubmitTemporaryDraft: () -> Unit,
    onExitTemporary: () -> Unit,
    onSelectP6GModel: (String?) -> Unit,
    onSetCurrentConversationWebSearchEnabled: (Boolean) -> Unit,
    onSetCurrentConversationStyle: (com.nanzhufeng.ai.domain.ConversationStyle) -> Unit,
    onAddTemporaryCamera: () -> Unit,
    onAddTemporaryImage: () -> Unit,
    onAddTemporaryFile: () -> Unit,
    onRemoveTemporaryDraftAttachment: (AttachmentId) -> Unit,
    projects: List<ProjectSnapshot>,
    currentProjectId: ProjectId?,
    onAssignProject: (com.nanzhufeng.ai.domain.Conversation, ProjectId?) -> Unit,
    onCreateProject: () -> Unit,
    onManageProjects: () -> Unit,
    onManageProject: (ProjectId) -> Unit,
    onPinProject: (ProjectId, Boolean) -> Unit,
    onArchiveProject: (ProjectId, Boolean) -> Unit,
    onCreateWorkConversation: (ProjectId) -> Unit,
    onOpenRoute: (P5ARoute) -> Unit,
    onOpenScheduledMonitors: () -> Unit,
    onCreateScheduledMonitor: (com.nanzhufeng.ai.domain.ConversationId?, com.nanzhufeng.ai.domain.ScheduledMonitorDraftSource) -> Unit,
    onCreateMemorySummary: (com.nanzhufeng.ai.domain.ConversationId?, com.nanzhufeng.ai.domain.MemorySummaryDraft) -> Unit,
    memorySummaryGenerationEnabled: Boolean,
    drawerOpen: Boolean,
    onDrawerOpenChanged: (Boolean) -> Unit,
    initialSearchCategory: ConversationSearchCategory? = null,
    onInitialSearchCategoryConsumed: () -> Unit = {},
) {
    val focusManager = LocalFocusManager.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // System long screenshots drive LazyColumn through its scroll semantics.  While that
    // capture is active, the app must not issue its own "follow latest" scroll, otherwise it
    // fights the platform callback and can make the assembled screenshot stop mid-transcript.
    val systemScrollCaptureInProgress = LocalScrollCaptureInProgress.current
    // Compose discovers one system scroll-capture target from the semantics tree. A closed
    // ModalNavigationDrawer is translated off-canvas but its vertical drawer list still has
    // scroll semantics; on ColorOS it can otherwise win over the visible transcript. Keep that
    // physically closed subtree out of both accessibility and scroll-capture discovery.
    val closedDrawerSemanticsModifier = if (drawerOpen) Modifier else Modifier.semantics {
        hideFromAccessibility()
    }
    // Scroll capture redraws the complete root view for every segment. The visual edge fade is
    // deliberately a screen-only treatment, so do not paint its near-opaque bands into each
    // captured segment.
    val transcriptScrollCaptureVisualModifier = if (systemScrollCaptureInProgress) {
        Modifier
    } else {
        Modifier.conversationEdgeGrayFade()
    }
    var choosingModel by rememberSaveable { mutableStateOf(false) }
    var renameVisible by rememberSaveable { mutableStateOf(false) }
    var renameText by rememberSaveable { mutableStateOf("") }
    var editingMessageId by rememberSaveable { mutableStateOf<String?>(null) }
    var editingText by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    LaunchedEffect(syncNotice, syncCompletedFeedback) {
        if (syncCompletedFeedback == null) {
            syncNotice?.takeIf { it.isNotBlank() }?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
        }
    }
    P7DAccountSyncProgressDialog(syncOperation, syncCompletedFeedback)
    val actionScope = rememberCoroutineScope()
    val shareMessage: (PresentedTranscriptMessage) -> Unit = { transcript ->
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, presentedMessagePlainText(transcript.message))
        }, "分享本地消息"))
    }
    val exportAssistantMarkdown: (PresentedTranscriptMessage) -> Unit = { transcript ->
        val conversationTitle = state.conversations
            .firstOrNull { it.id == state.selectedConversationId }
            ?.title
            ?: "对话"
        val messageSequence = state.messages.indexOfFirst { it.message.messageId == transcript.message.messageId }
            .let { index -> if (index < 0) 1 else index + 1 }
        actionScope.launch {
            runCatching { shareAssistantMarkdown(context, transcript, conversationTitle, messageSequence) }
                .onSuccess { Toast.makeText(context, "已准备分享 Markdown", Toast.LENGTH_SHORT).show() }
                .onFailure { error -> Toast.makeText(context, error.message ?: "Markdown 分享失败，请重试", Toast.LENGTH_SHORT).show() }
        }
    }
    val exportConversationMarkdown: (com.nanzhufeng.ai.domain.Conversation) -> Unit = { conversation ->
        // A drawer row can be long-pressed before its transcript becomes the visible path.
        // Never export the previously selected path under this conversation's title.
        if (conversation.id != state.selectedConversationId || state.isLoading) {
            onSelect(conversation.id)
            Toast.makeText(context, "已打开该对话，请再次选择分享。", Toast.LENGTH_SHORT).show()
        } else {
            actionScope.launch {
                runCatching { shareConversationMarkdown(context, conversation, state.messages) }
                    .onSuccess { Toast.makeText(context, "已准备分享对话 Markdown", Toast.LENGTH_SHORT).show() }
                    .onFailure { error -> Toast.makeText(context, error.message ?: "Markdown 分享失败，请重试", Toast.LENGTH_SHORT).show() }
            }
        }
    }
    // A message menu is transient and is anchored to the exact long-pressed message.
    // Keeping it out of saveable state also prevents a stale popup after recreation.
    var messageActionTarget by remember { mutableStateOf<MessageActionMenuTarget?>(null) }
    // A conversation action menu must be a sibling of the drawer. Its anchor is
    // intentionally transient: reopening from process state should dismiss a menu.
    var conversationActionTarget by remember { mutableStateOf<ConversationActionMenuTarget?>(null) }
    var selectingTextMessageId by rememberSaveable { mutableStateOf<String?>(null) }
    var choosingProject by rememberSaveable { mutableStateOf(false) }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    var uploadedFilesVisible by rememberSaveable { mutableStateOf(false) }
    var findInConversationVisible by rememberSaveable { mutableStateOf(false) }
    var searchAttachmentActionTarget by remember { mutableStateOf<SearchAttachmentActionMenuTarget?>(null) }
    var confirmingSearchAttachmentDelete by remember { mutableStateOf<ConversationAttachmentSearchHit?>(null) }
    var searchGlmOcrActionTarget by remember { mutableStateOf<SearchGlmOcrActionMenuTarget?>(null) }
    var confirmingSearchGlmOcrDelete by remember { mutableStateOf<GlmOcrDocumentSearchHit?>(null) }
    var searchReturnAttachmentId by rememberSaveable { mutableStateOf<String?>(null) }
    var searchLocateConversationId by rememberSaveable { mutableStateOf<String?>(null) }
    var searchLocateMessageId by rememberSaveable { mutableStateOf<String?>(null) }
    var searchLocateAttachmentId by rememberSaveable { mutableStateOf<String?>(null) }
    var searchLocateRequestId by rememberSaveable { mutableStateOf(0L) }
    var activeFindQuery by rememberSaveable { mutableStateOf<String?>(null) }
    var activeFindMatchIndex by rememberSaveable { mutableStateOf(0) }
    var activeFindRequestId by rememberSaveable { mutableStateOf(0L) }
    var activeFindSessionId by rememberSaveable { mutableStateOf(0L) }
    // The committed domain surface owns both the segmented selection and the canvas. Keeping
    // no local mirror prevents the header from moving before the matching transcript is ready.
    val workMode = state.surface == com.nanzhufeng.ai.domain.ConversationSurface.WORK
    val selectedConversation = remember(state.conversations, state.selectedConversationId) {
        state.conversations.firstOrNull { it.id == state.selectedConversationId }
    }
    // Header mode is defined by the visible transcript, not by the drawer projection.  A
    // transiently filtered or still-loading drawer row must never make a non-empty chat wear
    // the empty conversation/work switch.
    val hasConversationContent = state.messages.isNotEmpty()
    var searchPageVisible by rememberSaveable { mutableStateOf(false) }
    var searchAttachmentSortMode by rememberSaveable { mutableStateOf(ConversationAttachmentSortMode.DEFAULT) }
    var searchAttachmentFileType by rememberSaveable { mutableStateOf(ConversationAttachmentFileType.ALL) }
    // Search facts remain in the ViewModel while any search result briefly opens its owner chat.
    var returnToSearchAfterSearchOpen by rememberSaveable { mutableStateOf(false) }
    // Chat and Work have different persisted content owners, so they also keep distinct
    // viewport owners.  Hoisting both states keeps a mode swap from recreating a list at
    // its default (latest) position, while rememberLazyListState retains them on recreation.
    val chatTranscriptListState = rememberLazyListState()
    val workTranscriptListState = rememberLazyListState()
    val activeTranscriptListState = if (workMode) workTranscriptListState else chatTranscriptListState
    val normalTranscriptLeadingItems = listOf(state.importedFromChatGptExport, state.importedFromClaudeExport, state.importedFromChatGptZip).count { it }
    var chatFollowLatest by rememberSaveable { mutableStateOf(true) }
    var workFollowLatest by rememberSaveable { mutableStateOf(true) }
    var composerMenu by remember { mutableStateOf(ComposerMenu.NONE) }
    var composerAddAnchor by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var composerModelAnchor by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    val density = LocalDensity.current
    var copiedAssistantMessageId by remember { mutableStateOf<String?>(null) }
    var copiedContextMessageId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(copiedAssistantMessageId) {
        val copiedMessageId = copiedAssistantMessageId ?: return@LaunchedEffect
        delay(1_200)
        if (copiedAssistantMessageId == copiedMessageId) copiedAssistantMessageId = null
    }
    LaunchedEffect(copiedContextMessageId) {
        val copiedMessageId = copiedContextMessageId ?: return@LaunchedEffect
        delay(1_200)
        if (copiedContextMessageId == copiedMessageId) {
            copiedContextMessageId = null
            messageActionTarget = null
        }
    }
    val copyText = rememberConversationCopyTextAction()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val drawerScope = rememberCoroutineScope()
    // Search can briefly leave the full-screen destination to preview or locate one result.
    // Keep one viewport owner per category above searchPageVisible so returning restores the
    // exact result neighbourhood instead of recreating a list at item zero.
    val searchScrollStates = ConversationSearchScrollStates(
        all = rememberLazyListState(),
        text = rememberLazyListState(),
        image = rememberLazyGridState(),
        video = rememberLazyGridState(),
        audio = rememberLazyGridState(),
        file = rememberLazyGridState(),
    )
    LaunchedEffect(initialSearchCategory) {
        val category = initialSearchCategory ?: return@LaunchedEffect
        searchReturnAttachmentId = null
        searchLocateConversationId = null
        searchLocateMessageId = null
        searchLocateAttachmentId = null
        searchAttachmentFileType = ConversationAttachmentFileType.ALL
        onSearchChanged("")
        onSearchCategoryChanged(category)
        searchPageVisible = true
        onDrawerOpenChanged(false)
        drawerState.close()
        onInitialSearchCategoryConsumed()
    }
    LaunchedEffect(
        searchPageVisible,
        searchReturnAttachmentId,
        searchLocateConversationId,
        searchLocateMessageId,
        searchAttachmentSortMode,
        searchAttachmentFileType,
        state.searchCategory,
        state.searchResults,
        state.attachmentSearchResults,
        state.glmOcrSearchResults,
    ) {
        if (!searchPageVisible) return@LaunchedEffect
        val attachmentId = searchReturnAttachmentId ?: return@LaunchedEffect
        val targetKeySuffix = listOfNotNull(
            searchLocateConversationId,
            searchLocateMessageId,
            attachmentId,
        ).joinToString(":")
        when (state.searchCategory) {
            ConversationSearchCategory.ALL -> {
                val listState = searchScrollStates.all
                val isVisible = listState.layoutInfo.visibleItemsInfo.any { item ->
                    item.key.toString().endsWith(":$targetKeySuffix")
                }
                if (!isVisible) searchAllAttachmentItemIndex(
                    state,
                    attachmentId,
                    searchLocateConversationId,
                    searchLocateMessageId,
                    searchAttachmentSortMode,
                )?.let { listState.scrollToItem(it) }
            }
            ConversationSearchCategory.IMAGE,
            ConversationSearchCategory.VIDEO,
            ConversationSearchCategory.AUDIO,
            ConversationSearchCategory.FILE -> {
                val attachmentHits = if (state.searchCategory == ConversationSearchCategory.FILE) {
                    filterSearchFileType(state.attachmentSearchResults, searchAttachmentFileType)
                } else {
                    state.attachmentSearchResults
                }
                val glmOcrHits = if (state.searchCategory == ConversationSearchCategory.FILE) {
                    filterGlmOcrSearchFileType(state.glmOcrSearchResults, searchAttachmentFileType)
                } else {
                    state.glmOcrSearchResults
                }
                val gridState = searchScrollStates.grid(state.searchCategory)
                val isVisible = gridState.layoutInfo.visibleItemsInfo.any { item ->
                    item.key.toString().endsWith(":$targetKeySuffix")
                }
                if (!isVisible) searchGridAttachmentItemIndex(
                    hits = attachmentHits,
                    glmOcrHits = glmOcrHits,
                    attachmentId = attachmentId,
                    conversationId = searchLocateConversationId,
                    messageNodeId = searchLocateMessageId,
                    sortMode = searchAttachmentSortMode,
                )?.let { gridState.scrollToItem(it) }
            }
            ConversationSearchCategory.TEXT -> Unit
        }
    }
    fun returnToSearchAfterSearchOpen() {
        if (!returnToSearchAfterSearchOpen) return
        returnToSearchAfterSearchOpen = false
        searchPageVisible = true
        onDrawerOpenChanged(false)
        drawerScope.launch { drawerState.close() }
    }
    val searchReturnSwipeModifier = if (returnToSearchAfterSearchOpen && !searchPageVisible) {
        Modifier.searchAttachmentReturnSwipe(onReturn = ::returnToSearchAfterSearchOpen)
    } else {
        Modifier
    }
    val quickDrawerOpenModifier = if (!returnToSearchAfterSearchOpen && !searchPageVisible) {
        Modifier.quickConversationDrawerOpen {
            drawerScope.launch {
                // Let ModalNavigationDrawer finish dispatching the same pointer-up first, then
                // win the settle decision with the requested open state.
                kotlinx.coroutines.yield()
                drawerState.open()
            }
        }
    } else {
        Modifier
    }
    val activeFindMatches = remember(activeFindQuery, state.messages) {
        activeFindQuery?.let { query -> conversationFindMatches(state.messages, query) }.orEmpty()
    }
    val editableById = remember(state.editableUserMessages) {
        state.editableUserMessages.associateBy { it.messageId }
    }
    val monitorDraftSource = remember(
        notificationReminderSettings.conversationReminderSuggestionsEnabled,
        state.messages,
    ) {
        if (notificationReminderSettings.conversationReminderSuggestionsEnabled) {
            scheduledMonitorDraftSourceFor(state.messages)
        } else null
    }
    val memorySuggestion = remember(memorySummaryGenerationEnabled, state.messages) {
        if (memorySummaryGenerationEnabled) memorySummarySuggestionFor(state.messages) else null
    }
    fun navigateToFindMatch(requestedIndex: Int) {
        if (activeFindMatches.isEmpty()) return
        val resolvedIndex = ((requestedIndex % activeFindMatches.size) + activeFindMatches.size) % activeFindMatches.size
        val previousTarget = activeFindMatches.getOrNull(activeFindMatchIndex)
        val target = activeFindMatches[resolvedIndex]
        val needsCoarseMessageScroll = activeFindRequestId == 0L || previousTarget?.messageId != target.messageId
        val messageIndex = state.messages.indexOfFirst { it.message.messageId == target.messageId }
        if (messageIndex >= 0 && needsCoarseMessageScroll) {
            val itemIndex = messageIndex + if (workMode) 0 else normalTranscriptLeadingItems
            // Compose the target message first. Its selected text range then performs the exact
            // glyph-level relocation, instead of leaving a long reply parked at its first line.
            drawerScope.launch {
                activeTranscriptListState.scrollToItem(itemIndex)
                activeFindMatchIndex = resolvedIndex
                activeFindRequestId += 1L
            }
        } else {
            activeFindMatchIndex = resolvedIndex
            activeFindRequestId += 1L
        }
    }
    // A find session belongs to the currently visible local transcript.  It is deliberately
    // not a repository search or a global search result: each navigation command scrolls the
    // one list the user is already reading.
    LaunchedEffect(activeFindQuery, activeFindSessionId, activeFindMatches, workMode) {
        when {
            activeFindQuery == null -> Unit
            activeFindMatches.isEmpty() -> activeFindQuery = null
            else -> navigateToFindMatch(0)
        }
    }
    val windowWidth = with(density) { LocalWindowInfo.current.containerSize.width.toDp() }
    // The inner display is a separate expanded contract: navigation can use two thirds of
    // the wide canvas, while the outer display keeps its compact, one-hand drawer width.
    val drawerWidth = if (windowWidth >= 600.dp) windowWidth * (2f / 3f) else 320.dp
    // The platform text-classifier toolbar is not application state. Releasing the text focus
    // while the activity is backgrounded prevents a stale paste/AI-writing action mode from
    // resurfacing when the user returns to the conversation.
    DisposableEffect(lifecycleOwner, focusManager) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) focusManager.clearFocus(force = true)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(state.selectedConversationId, state.surface, state.temporaryRecovery?.id) {
        focusManager.clearFocus(force = true)
    }
    LaunchedEffect(drawerOpen) {
        if (drawerOpen && !drawerState.isOpen) drawerState.open()
        else if (!drawerOpen && drawerState.isOpen) drawerState.close()
    }
    // The first DrawerState value is its local construction value. Do not let it overwrite the
    // restored owner snapshot; only report changes that happen after hydration/user interaction.
    LaunchedEffect(drawerOpen) {
        snapshotFlow { drawerState.currentValue }
            .drop(1)
            .collect { onDrawerOpenChanged(drawerState.isOpen) }
    }
    // Search history is owned by the search page alone.  A restored/stale history
    // state must never put a search card on top of the normal conversation canvas.
    LaunchedEffect(searchPageVisible, state.searchHistoryOpen) {
        if (!searchPageVisible && state.searchHistoryOpen) onCloseSearchHistory()
    }
    if (searchPageVisible && state.searchHistoryOpen) BackHandler(onBack = onCloseSearchHistory)
    CompositionLocalProvider(
        LocalAttachmentTransferRequest provides onRequestAttachmentTransfer,
        LocalAttachmentSearchLocateRequest provides { messageNodeId, attachmentId ->
            searchLocateConversationId = state.selectedConversationId?.value
            searchLocateMessageId = messageNodeId.value
            searchReturnAttachmentId = attachmentId.value
            searchLocateAttachmentId = attachmentId.value
            searchLocateRequestId += 1L
            // This is an exact jump inside the complete catalogue, not an implicit
            // filename/type filter that hides the surrounding search results.
            onSearchChanged("")
            onSearchCategoryChanged(ConversationSearchCategory.ALL)
            searchPageVisible = true
            onDrawerOpenChanged(false)
            drawerScope.launch { drawerState.close() }
        },
        LocalConversationFindQuery provides activeFindQuery,
        LocalConversationFindTarget provides activeFindMatches.getOrNull(activeFindMatchIndex)?.takeIf { activeFindRequestId > 0L }?.let { match ->
            ConversationFindTarget(match = match, requestId = activeFindRequestId)
        },
    ) {
    state.attachmentTransfer?.let { transfer -> AttachmentTransferEffect(transfer, onConsumeAttachmentTransfer) }
    ModalNavigationDrawer(
            drawerState = drawerState,
            // Keep the established Material drawer gesture: a deliberate right swipe on the
            // conversation canvas opens the left drawer. Search-result return is a separate,
            // edge-only observer and must not weaken this normal navigation gesture.
            gesturesEnabled = true,
            drawerContent = {
                ModalDrawerSheet(
                    drawerShape = RectangleShape,
                    drawerContainerColor = ConversationDrawerBaseSurface,
                    drawerTonalElevation = 0.dp,
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    modifier = Modifier
                        .requiredWidth(drawerWidth)
                        // The transparent system bar exposes this sheet. Keep the same bright
                        // base through that area; only the dedicated edge fade may add gray.
                        .fillMaxHeight()
                        .background(ConversationDrawerBaseSurface)
                        .then(closedDrawerSemanticsModifier),
                ) {
                    ConversationTextScale {
                    if (state.temporaryRecovery != null) {
                        TemporaryConversationNavigationDrawer(
                            onExit = { onExitTemporary(); drawerScope.launch { drawerState.close() } },
                            onOpenSettings = { onDismiss(); onOpenRoute(P5ARoute.SETTINGS) },
                        )
                    } else {
                        ConversationNavigationDrawer(
                            state = state,
                            notificationReminderSettings = notificationReminderSettings,
                            projects = projects,
                            onCreate = { onCreate(); drawerScope.launch { drawerState.close() } },
                            onSelect = { id -> onSelect(id); drawerScope.launch { drawerState.close() } },
                            onClearWatchLater = onClearWatchLater,
                            onScope = onListScope,
                            onOpenSearchPage = {
                                searchReturnAttachmentId = null
                                searchLocateConversationId = null
                                searchLocateMessageId = null
                                searchLocateAttachmentId = null
                                onSearchChanged("")
                                searchPageVisible = true
                                drawerScope.launch { drawerState.close() }
                            },
                            onManage = onManage,
                            onBatchSoftDelete = onBatchSoftDelete,
                            workMode = workMode,
                            onModeChanged = { enabled -> onSurfaceChanged(if (enabled) com.nanzhufeng.ai.domain.ConversationSurface.WORK else com.nanzhufeng.ai.domain.ConversationSurface.CHAT) },
                            onRequestRename = { conversation -> onSelect(conversation.id); renameText = conversation.title; renameVisible = true },
                            onRequestProject = { conversation -> onSelect(conversation.id); choosingProject = true },
                            onRequestDelete = { conversation -> onSelect(conversation.id); confirmingDelete = true },
                            onRequestConversationActions = { conversation, anchor, cloudList ->
                                conversationActionTarget = ConversationActionMenuTarget(conversation.id.value, anchor, cloudList = cloudList)
                            },
                            onCreateProject = { onCreateProject(); drawerScope.launch { drawerState.close() } },
                            onManageProjects = { onManageProjects(); drawerScope.launch { drawerState.close() } },
                            onManageProject = { projectId -> onManageProject(projectId); drawerScope.launch { drawerState.close() } },
                            onPinProject = onPinProject,
                            onArchiveProject = onArchiveProject,
                            onCreateWorkConversation = { projectId -> onCreateWorkConversation(projectId); drawerScope.launch { drawerState.close() } },
                            onOpenRoute = { route -> onDismiss(); onOpenRoute(route) },
                            onOpenScheduledMonitors = { onOpenScheduledMonitors(); drawerScope.launch { drawerState.close() } },
                            onExport = { drawerScope.launch { drawerState.close() }; onExport() },
                            syncedConversationIds = syncedConversationIds,
                            cloudConversations = cloudConversations,
                            cloudPinnedConversationIds = cloudPinnedConversationIds,
                            onToggleCloudConversationPinned = onToggleCloudConversationPinned,
                            onReadCloudConversations = onReadCloudConversations,
                        )
                    }
                    }
                }
            },
        ) {
            Surface(
                color = ConversationWorkspaceCanvas,
                modifier = Modifier
                    .fillMaxSize()
                    .clearComposerFocusOnBlankTap(focusManager),
            ) {
            if (state.temporaryRecovery != null) {
                TemporaryConversationPane(
                    recovery = state.temporaryRecovery,
                    modelOptions = listOf(null to "Auto · DeepSeek V4.1 Flash"),
                    onDraftChanged = onUpdateTemporaryDraft,
                    onModelOverrideChanged = onUpdateTemporaryModelOverride,
                    onSubmit = onSubmitTemporaryDraft,
                    onAddCamera = onAddTemporaryCamera,
                    onAddImage = onAddTemporaryImage,
                    onAddFile = onAddTemporaryFile,
                    attachmentPreviews = state.attachmentPreviews,
                    onRemoveAttachment = onRemoveTemporaryDraftAttachment,
                    onOpenImagePreview = onOpenImagePreview,
                    onOpenPdfPreview = onOpenPdfPreview,
                    onOpenVideoPreview = onOpenVideoPreview,
                    onOpenAudioPreview = onOpenAudioPreview,
                    onOpenTextPreview = onOpenTextPreview,
                    onExit = onExitTemporary,
                    onOpenNavigation = { drawerScope.launch { drawerState.open() } },
                    onEnterWork = { onExitTemporary(); onSurfaceChanged(com.nanzhufeng.ai.domain.ConversationSurface.WORK) },
                )
            } else Box(
                Modifier
                    .fillMaxSize()
                    .background(ConversationWorkspaceCanvas)
                    .then(quickDrawerOpenModifier)
                    .then(searchReturnSwipeModifier),
            ) {
                // A first launch must remain an actually empty local truth until the user chooses
                // “新对话” from the drawer. In particular, OpenDocument v2 restore may safely run
                // before any business owner exists; the normal drawer action still creates the
                // same chat-first composer on explicit user intent.
                var workSentFromMessageCount by remember(state.selectedConversationId) { mutableStateOf<Int?>(null) }
                var chatSentFromMessageCount by remember(state.selectedConversationId) { mutableStateOf<Int?>(null) }
                var floatingComposerHeight by remember { mutableStateOf(60.dp) }
                val jumpToLatestBottomPadding = floatingComposerHeight + 4.dp
                val showFloatingComposer = state.draft != null && !state.searchPanelOpen
                val activeTranscriptListState = if (state.surface == com.nanzhufeng.ai.domain.ConversationSurface.WORK) {
                    workTranscriptListState
                } else {
                    chatTranscriptListState
                }
                var revealedTranscriptJumpDirection by remember(activeTranscriptListState) {
                    mutableStateOf<TranscriptJumpDirection?>(null)
                }
                var transcriptJumpActivityGeneration by remember(activeTranscriptListState) { mutableStateOf(0) }
                val activeTranscriptUserDragging by activeTranscriptListState.interactionSource.collectIsDraggedAsState()
                LaunchedEffect(activeTranscriptListState) {
                    var previousObservation: TranscriptScrollObservation? = null
                    var manualScrollInProgress = false
                    snapshotFlow {
                        TranscriptScrollObservation(
                            itemIndex = activeTranscriptListState.firstVisibleItemIndex,
                            itemOffset = activeTranscriptListState.firstVisibleItemScrollOffset,
                            userDragging = activeTranscriptUserDragging,
                            scrollInProgress = activeTranscriptListState.isScrollInProgress,
                        )
                    }.collect { observation ->
                        if (observation.userDragging) manualScrollInProgress = true
                        val direction = previousObservation?.let { previous ->
                            when {
                                observation.itemIndex > previous.itemIndex || (
                                    observation.itemIndex == previous.itemIndex && observation.itemOffset > previous.itemOffset
                                ) -> TranscriptJumpDirection.BOTTOM
                                observation.itemIndex < previous.itemIndex || (
                                    observation.itemIndex == previous.itemIndex && observation.itemOffset < previous.itemOffset
                                ) -> TranscriptJumpDirection.TOP
                                else -> null
                            }
                        }
                        if (manualScrollInProgress && direction != null) {
                            revealedTranscriptJumpDirection = direction
                            transcriptJumpActivityGeneration += 1
                        }
                        if (!observation.scrollInProgress) manualScrollInProgress = false
                        previousObservation = observation
                    }
                }
                LaunchedEffect(transcriptJumpActivityGeneration) {
                    if (revealedTranscriptJumpDirection == null) return@LaunchedEffect
                    delay(TranscriptJumpControlIdleHideMillis)
                    revealedTranscriptJumpDirection = null
                }
                val showJumpToLatest by remember(activeTranscriptListState, revealedTranscriptJumpDirection) {
                    derivedStateOf {
                        // A full Assistant reply is one LazyColumn item. Seeing its top does
                        // not mean its lower paragraphs are on screen, so item-index equality
                        // must never make this control disappear before the actual list end.
                        revealedTranscriptJumpDirection == TranscriptJumpDirection.BOTTOM &&
                            activeTranscriptListState.canScrollForward
                    }
                }
                val showJumpToTop by remember(activeTranscriptListState, revealedTranscriptJumpDirection) {
                    derivedStateOf {
                        revealedTranscriptJumpDirection == TranscriptJumpDirection.TOP &&
                            activeTranscriptListState.canScrollBackward
                    }
                }
                // The header remains an overlay, while the list owns a scrollable initial inset:
                // short transcripts start below it; overflowing content can scroll underneath.
                Box(Modifier.fillMaxSize()) {
                // The drawer owns navigation and management. Keeping them out of the chat canvas
                // leaves one scroll owner for messages and a permanently reachable composer.
                // The persisted surface is the content owner. `workMode` only gives the
                // segmented control immediate visual feedback while the ViewModel reloads.
                // Never let that transient UI value attach the other surface's messages to
                // a saved list state during a mode switch.
                if (state.surface == com.nanzhufeng.ai.domain.ConversationSurface.WORK) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = ConversationTranscriptPageGutter),
                    ) {
                        ConversationWorkScope(
                            state = state,
                            attachmentPreviews = state.attachmentPreviews,
                            listState = workTranscriptListState,
                            followLatest = workFollowLatest,
                            systemScrollCaptureInProgress = systemScrollCaptureInProgress,
                            onFollowLatestChanged = { workFollowLatest = it },
                            sentFromMessageCount = workSentFromMessageCount,
                            onSentToLatestConsumed = { workSentFromMessageCount = null },
                            onLongPress = { messageId, anchorBounds, pressPosition -> messageActionTarget = MessageActionMenuTarget(messageId.value, anchorBounds, pressPosition) },
                            copiedAssistantMessageId = copiedAssistantMessageId,
                            onCopyAssistant = { transcript -> copyText(presentedMessagePlainText(transcript.message)) { copiedAssistantMessageId = transcript.message.messageId.value } },
                            onShareAssistant = exportAssistantMarkdown,
                            onBranchAssistant = onBranchFromMessage,
                            onEnsureAttachmentPreview = onEnsureAttachmentPreview,
                            onOpenImagePreview = onOpenImagePreview,
                            onOpenPdfPreview = onOpenPdfPreview,
                            onOpenVideoPreview = onOpenVideoPreview,
                            onOpenAudioPreview = onOpenAudioPreview,
                            onOpenTextPreview = onOpenTextPreview,
                        )
                    }
                } else if (state.searchPanelOpen) {
                    SearchResultsMainPanel(state, onOpenSearchHit, onCloseSearch)
                } else if (state.conversations.isEmpty()) {
                    Spacer(Modifier.fillMaxSize())
                } else {
                    val listState = chatTranscriptListState
                    // A direct touch drag is an explicit reading decision. It must win over
                    // stream updates and any earlier follow-latest state immediately.
                    val chatUserDragging by listState.interactionSource.collectIsDraggedAsState()
                    LaunchedEffect(chatUserDragging) {
                        if (chatUserDragging) chatFollowLatest = false
                    }
                    // LazyColumn only lays out rows around the viewport.  Retain each row's
                    // real measured height while it is visible, so a multi-line bubble moves
                    // the edge thumb by its own pixels instead of by a single item index.
                    val measuredTranscriptItemHeights = rememberTranscriptMeasuredItemHeights(
                        listState = listState,
                        transcriptIdentity = state.selectedConversationId,
                    )
                    // Sending is an explicit user request to resume at the newest message,
                    // even when they were reading older history immediately beforehand.
                    // Keep the request local to this conversation and wait for the persisted
                    // message append before moving the only transcript scroll owner.
                    LaunchedEffect(listState) {
                        snapshotFlow {
                            listState.layoutInfo.totalItemsCount == 0 || !listState.canScrollForward
                        }.collect { atLatest ->
                            // Reaching the real end may resume following. A streamed row making
                            // canScrollForward true is not a user reading decision and must not
                            // disable it or trigger any item-top alignment.
                            if (atLatest) chatFollowLatest = true
                        }
                    }
                    LaunchedEffect(state.searchAnchorRequestId, state.messages) {
                        state.searchAnchorMessageId?.let { anchor ->
                            state.messages.indexOfFirst { it.message.messageId == anchor }.takeIf { it >= 0 }?.let { index ->
                                // A search locate is an explicit reading destination. It must
                                // not be overwritten by the old conversation's follow-latest
                                // state, and imported-conversation provenance rows precede the
                                // message list in this LazyColumn.
                                chatFollowLatest = false
                                listState.scrollToItem(index + normalTranscriptLeadingItems)
                            }
                        }
                    }
                    LaunchedEffect(
                        state.messages.size,
                        chatSentFromMessageCount,
                        chatFollowLatest,
                        systemScrollCaptureInProgress,
                    ) {
                        if (systemScrollCaptureInProgress) return@LaunchedEffect
                        val sentCount = chatSentFromMessageCount
                        if (sentCount != null && state.messages.size > sentCount && listState.layoutInfo.totalItemsCount > 0) {
                            listState.scrollToTrueBottom()
                            chatSentFromMessageCount = null
                        } else if (chatFollowLatest && listState.layoutInfo.totalItemsCount > 0) {
                            listState.scrollToTrueBottom()
                        }
                    }
                    Box(Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = listState,
                            contentPadding = ConversationTranscriptContentPadding,
                            // Reserve a dedicated edge lane so the visual scrollbar never
                            // overlaps readable messages, media, or their action targets.
                            // The approved curve uses only a grey canvas fade; it does not blur text.
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Transparent)
                                .then(transcriptScrollCaptureVisualModifier),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                        if (state.importedFromChatGptExport) item(key = "chatgpt-imported-provenance", contentType = "chatgpt-imported-provenance") {
                            ImportedConversationProvenance("从 ChatGPT 导入")
                        }
                        if (state.importedFromClaudeExport) item(key = "claude-imported-provenance", contentType = "claude-imported-provenance") {
                            ImportedConversationProvenance("从 Claude 导入")
                        }
                        if (state.importedFromChatGptZip) item(key = "chatgpt-zip-imported-provenance", contentType = "chatgpt-zip-imported-provenance") {
                            ImportedConversationProvenance("从 ChatGPT ZIP 导入")
                        }
                        itemsIndexed(
                            items = state.messages,
                            key = { _, transcript -> transcript.message.messageId.value },
                            contentType = { _, transcript -> transcript.message.role.name },
                        ) { index, message ->
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                val previous = state.messages.getOrNull(index - 1)
                                if (previous == null || transcriptLocalDateKey(previous.metadata.createdAt) != transcriptLocalDateKey(message.metadata.createdAt)) {
                                    TranscriptDateDivider(transcriptLocalDateKey(message.metadata.createdAt))
                                }
                                MessageBubble(
                                    transcript = message,
                                    attachmentPreviews = state.attachmentPreviews,
                                    contextSelections = state.answerContextSelections[message.message.messageId].orEmpty(),
                                    responseAttributions = state.answerResponseAttributions[message.message.messageId].orEmpty(),
                                    assistantGenerationPhaseOverride = if (
                                        state.normalSendRetryInProgress && message.message.messageId == state.currentLeafId
                                    ) "正在重新生成…" else null,
                                    assistantGenerationIsRetry = state.normalSendRetryInProgress && message.message.messageId == state.currentLeafId,
                                    searchAnchorAttachmentId = state.searchAnchorAttachmentId,
                                    searchAnchorRequestId = state.searchAnchorRequestId,
                                    onLongPress = { messageId, anchorBounds, pressPosition -> messageActionTarget = MessageActionMenuTarget(messageId.value, anchorBounds, pressPosition) },
                                    copiedAssistantMessageId = copiedAssistantMessageId,
                                    onCopyAssistant = { transcript -> copyText(presentedMessagePlainText(transcript.message)) { copiedAssistantMessageId = transcript.message.messageId.value } },
                                    onShareAssistant = exportAssistantMarkdown,
                                    onBranchAssistant = onBranchFromMessage,
                                    onEnsureAttachmentPreview = onEnsureAttachmentPreview,
                                    onOpenImagePreview = onOpenImagePreview,
                                    onOpenPdfPreview = onOpenPdfPreview,
                                    onOpenVideoPreview = onOpenVideoPreview,
                                    onOpenAudioPreview = onOpenAudioPreview,
                                    onOpenTextPreview = onOpenTextPreview,
                                )
                            }
                        }
                        if (monitorDraftSource != null) {
                            item(key = "conversation-monitor-tail", contentType = "conversation-monitor-tail") {
                                ConversationTailMonitorAction(
                                    onClick = { onCreateScheduledMonitor(selectedConversation?.id, monitorDraftSource) },
                                )
                            }
                        }
                        if (memorySuggestion != null) {
                            item(key = "conversation-memory-tail", contentType = "conversation-memory-tail") {
                                ConversationTailMemoryAction(onClick = { onCreateMemorySummary(selectedConversation?.id, memorySuggestion) })
                            }
                        }
                        }
                        TranscriptScrollIndicator(
                            listState = listState,
                            measuredItemHeights = measuredTranscriptItemHeights,
                            // Keep the indicator inside LazyColumn's dedicated 6dp edge lane.
                            // A positive x offset puts half of its thin thumb outside the
                            // clipped conversation canvas on some system-inset configurations.
                            modifier = Modifier.align(Alignment.CenterEnd).padding(top = 8.dp, bottom = 8.dp),
                        )
                    }
                }
                }
                ConversationShellHeader(
                    workMode = workMode,
                    onModeChanged = { enabled -> onSurfaceChanged(if (enabled) com.nanzhufeng.ai.domain.ConversationSurface.WORK else com.nanzhufeng.ai.domain.ConversationSurface.CHAT) },
                    onLeftAction = { drawerScope.launch { drawerState.open() } },
                    leftIcon = Icons.Rounded.Menu,
                    leftDescription = "打开对话导航",
                    onTemporaryAction = onEnterTemporary,
                    temporaryTint = ConversationControlGlyph,
                    showContentActions = hasConversationContent,
                    onCreateConversation = onCreate,
                    onConversationActionsRequested = { anchor ->
                        selectedConversation?.let { conversation ->
                            conversationActionTarget = ConversationActionMenuTarget(conversation.id.value, anchor)
                        }
                    },
                    modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(horizontal = 18.dp, vertical = 18.dp),
                )
                activeFindQuery?.takeIf { activeFindMatches.isNotEmpty() }?.let { query ->
                    ConversationFindNavigationBar(
                        query = query,
                        currentMatch = activeFindMatchIndex + 1,
                        matchCount = activeFindMatches.size,
                        onPrevious = { navigateToFindMatch(activeFindMatchIndex - 1) },
                        onNext = { navigateToFindMatch(activeFindMatchIndex + 1) },
                        onClose = { activeFindQuery = null },
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(start = 18.dp, top = 84.dp, end = 18.dp),
                    )
                }
                state.branchCreation?.let { branchCreation ->
                    BranchCreationFeedback(
                        branchCreation = branchCreation,
                        onDismiss = onDismissBranchCreation,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(start = 28.dp, end = 28.dp, bottom = floatingComposerHeight + 14.dp),
                    )
                }
                // The composer is a true overlay: only its own rounded surface obscures the
                // transcript. The page canvas stays visible beneath it rather than becoming a
                // full-width white bottom bar.
                if (showFloatingComposer) {
                    // This appears only for an actual send failure. It is deliberately not a
                    // welcome tip, confirmation dialog, or toast: the user can see why the
                    // already-committed message has no model reply and can continue typing to
                    // dismiss it.
                    state.sendError?.takeIf {
                        it.isNotBlank() && state.normalSendRecovery == null && state.sendErrorConversationId == state.selectedConversationId
                    }?.let { notice ->
                        Surface(
                            color = Color(0xFFFFF3F1),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(start = 28.dp, end = 28.dp, bottom = floatingComposerHeight + 14.dp),
                        ) {
                            Text(
                                notice,
                                color = ErrorRed,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                        }
                    }
                    state.normalSendRecovery?.takeIf { !state.isSending }?.let { recovery ->
                        Surface(
                            color = ForegroundSurface,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(start = 28.dp, end = 28.dp, bottom = floatingComposerHeight + 84.dp),
                        ) {
                            Column(
                                Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    "发送未完成",
                                    color = ErrorRed,
                                    style = MaterialTheme.typography.labelLarge,
                                    textAlign = TextAlign.Center,
                                )
                                Text(
                                    recovery.failureReason,
                                    color = SecondaryText,
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center,
                                )
                                Button(onClick = onRetryNormalSend, enabled = recovery.canRetry && !state.isSending) { Text("重试") }
                            }
                        }
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .imePadding()
                            .padding(start = 18.dp, end = 18.dp, bottom = 6.dp),
                    ) {
                        Box(
                            modifier = Modifier.onGloballyPositioned { coordinates ->
                                floatingComposerHeight = with(density) { coordinates.size.height.toDp() }
                            },
                        ) {
                        DraftComposer(
                            state = state,
                            onEnsureAttachmentPreview = onEnsureAttachmentPreview,
                            onDraftChanged = onDraftChanged,
                            onSubmit = {
                                if (state.surface == com.nanzhufeng.ai.domain.ConversationSurface.WORK) {
                                    workSentFromMessageCount = state.messages.size
                                } else {
                                    chatSentFromMessageCount = state.messages.size
                                }
                                // Bind the actual rendered disclosure, not a later ViewModel state.
                                val shownDraft = state.draft
                                val shownRouting = state.normalChatRouting
                                val shownModelId = state.p6gConversationOverride?.modelId ?: state.p6gGlobalDefault.modelId
                                val authorization = if (shownRouting != null &&
                                    shownRouting.canAuthorize(shownDraft, shownModelId) &&
                                    state.surface == com.nanzhufeng.ai.domain.ConversationSurface.CHAT
                                ) com.nanzhufeng.ai.domain.NormalChatEgressAuthorization.forUserSend(
                                    shownDraft, System.currentTimeMillis(), shownModelId, shownRouting,
                                ) else null
                                onSubmitDraft(state.selectedConversationId, authorization)
                            },
                            onStop = onStop,
                            onRemoveAttachment = onRemoveAttachment,
                            onOpenImagePreview = onOpenImagePreview,
                            onOpenPdfPreview = onOpenPdfPreview,
                            onOpenVideoPreview = onOpenVideoPreview,
                            onOpenAudioPreview = onOpenAudioPreview,
                            onOpenTextPreview = onOpenTextPreview,
                            onToggleAttachments = { composerMenu = if (composerMenu == ComposerMenu.ATTACHMENTS) ComposerMenu.NONE else ComposerMenu.ATTACHMENTS },
                            onToggleModel = { composerMenu = if (composerMenu == ComposerMenu.MODEL) ComposerMenu.NONE else ComposerMenu.MODEL },
                            onAddAnchorChanged = { composerAddAnchor = it },
                            onModelAnchorChanged = { composerModelAnchor = it },
                        )
                        }
                    }
                }
                // This must be a later sibling of the composer, rather than a child of the
                // transcript. A child's zIndex cannot escape its parent, so the composer's
                // deliberately wide dropShadow would otherwise darken the white jump control.
                if (showJumpToTop && !state.searchPanelOpen) JumpToTopButton(
                    onClick = {
                        // This is a reading rewind, not a request to resume auto-follow.
                        if (workMode) workFollowLatest = false else chatFollowLatest = false
                        revealedTranscriptJumpDirection = TranscriptJumpDirection.TOP
                        transcriptJumpActivityGeneration += 1
                        drawerScope.launch {
                            activeTranscriptListState.animateBackwardByVisibleViewport(
                                with(density) { floatingComposerHeight.toPx() },
                            )
                        }
                    },
                    onLongClick = {
                        drawerScope.launch {
                            activeTranscriptListState.scrollToTrueTop()
                            if (workMode) workFollowLatest = false else chatFollowLatest = false
                        }
                    },
                    modifier = Modifier
                        .zIndex(ConversationScrollToLatestZIndex)
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        // Header round buttons are 44dp at top 18dp; this is 48dp at top 16dp,
                        // so their visual centres share exactly the same header row.
                        .padding(top = 16.dp),
                )
                if (showJumpToLatest && !state.searchPanelOpen) JumpToLatestButton(
                    onClick = {
                        // This is a reading advance, not a request to resume auto-follow.
                        if (workMode) workFollowLatest = false else chatFollowLatest = false
                        revealedTranscriptJumpDirection = TranscriptJumpDirection.BOTTOM
                        transcriptJumpActivityGeneration += 1
                        drawerScope.launch {
                            activeTranscriptListState.animateForwardByVisibleViewport(
                                with(density) { floatingComposerHeight.toPx() },
                            )
                        }
                    },
                    onLongClick = {
                        // A deliberate long press is the explicit request to rejoin live content.
                        drawerScope.launch {
                            activeTranscriptListState.scrollToTrueBottom()
                            if (workMode) workFollowLatest = true else chatFollowLatest = true
                        }
                    },
                    modifier = Modifier
                        .zIndex(ConversationScrollToLatestZIndex)
                        .align(Alignment.BottomCenter)
                        .padding(bottom = jumpToLatestBottomPadding + 28.dp),
                )
                // This sibling is outside the transcript and floating composer measurement.
                ComposerMenuOverlay(
                    menu = composerMenu,
                    addAnchor = composerAddAnchor,
                    modelAnchor = composerModelAnchor,
                    attachmentActions = listOf(
                        ComposerAttachmentAction(Icons.Rounded.PhotoCamera, "相机") { composerMenu = ComposerMenu.NONE; onAddCamera() },
                        ComposerAttachmentAction(Icons.Rounded.AddPhotoAlternate, "图片") { composerMenu = ComposerMenu.NONE; onAddImage() },
                        ComposerAttachmentAction(Icons.Rounded.AttachFile, "文件") { composerMenu = ComposerMenu.NONE; onAddFile() },
                    ),
                    conversationWebSearchEnabled = state.conversationWebSearchOverride?.enabled ?: state.globalWebSearchEnabled,
                    onToggleConversationWebSearch = { enabled -> onSetCurrentConversationWebSearchEnabled(enabled) },
                    conversationStyle = state.conversationStyleOverride?.style ?: state.globalConversationStyle,
                    onSelectConversationStyle = onSetCurrentConversationStyle,
                    modelOptions = listOf(null to com.nanzhufeng.ai.domain.AutoModelRouter.label(
                        com.nanzhufeng.ai.domain.AutoRoutingFacts(
                            hasAttachment = state.draft?.attachments?.isNotEmpty() == true,
                            requiresComplexReasoning = com.nanzhufeng.ai.domain.AutoRoutingTaskClassifier.requiresComplexReasoning(state.draft?.text.orEmpty()),
                        ),
                    )) + com.nanzhufeng.ai.domain.ComposerModelRoutingCatalog.choices
                        .filterNot { it == com.nanzhufeng.ai.domain.ComposerModelRoutingCatalog.auto }
                        .map { it.id to it.label },
                    selectedModelId = (state.p6gConversationOverride?.modelId ?: state.p6gGlobalDefault.modelId)
                        ?.let(com.nanzhufeng.ai.domain.ComposerModelRoutingCatalog::choice)?.id,
                    onDismiss = { composerMenu = ComposerMenu.NONE },
                    onSelectModel = { modelId -> composerMenu = ComposerMenu.NONE; onSelectP6GModel(modelId) },
                )
            }
        }
    }
    if (searchPageVisible) {
        ConversationSearchPage(
            state = state,
            scrollStates = searchScrollStates,
            attachmentSortMode = searchAttachmentSortMode,
            attachmentFileType = searchAttachmentFileType,
            locateConversationId = searchLocateConversationId,
            locateMessageId = searchLocateMessageId,
            locateAttachmentId = searchLocateAttachmentId,
            locateRequestId = searchLocateRequestId,
            onSelectAttachmentSortMode = { sortMode ->
                if (sortMode != searchAttachmentSortMode) {
                    searchReturnAttachmentId = null
                    searchLocateConversationId = null
                    searchLocateMessageId = null
                    searchLocateAttachmentId = null
                    searchAttachmentSortMode = sortMode
                    drawerScope.launch {
                        when (state.searchCategory) {
                            ConversationSearchCategory.ALL -> searchScrollStates.all.scrollToItem(0)
                            ConversationSearchCategory.IMAGE,
                            ConversationSearchCategory.VIDEO,
                            ConversationSearchCategory.AUDIO,
                            ConversationSearchCategory.FILE -> searchScrollStates.grid(state.searchCategory).scrollToItem(0)
                            ConversationSearchCategory.TEXT -> Unit
                        }
                    }
                }
            },
            onSelectAttachmentFileType = { type ->
                if (type != searchAttachmentFileType) {
                    searchReturnAttachmentId = null
                    searchLocateConversationId = null
                    searchLocateMessageId = null
                    searchLocateAttachmentId = null
                    searchAttachmentFileType = type
                    drawerScope.launch { searchScrollStates.file.scrollToItem(0) }
                }
            },
            onDismiss = {
                searchPageVisible = false
                returnToSearchAfterSearchOpen = false
                if (searchDismissesToParent) {
                    onDismiss()
                } else {
                    onDrawerOpenChanged(true)
                    drawerScope.launch { drawerState.open() }
                }
            },
            onQueryChanged = { query ->
                searchReturnAttachmentId = null
                searchLocateConversationId = null
                searchLocateMessageId = null
                searchLocateAttachmentId = null
                onSearchChanged(query)
            },
            onSubmit = onSearchRequested,
            onSelectCategory = { category ->
                // The search owner re-runs an existing query after a category switch.
                searchReturnAttachmentId = null
                searchLocateConversationId = null
                searchLocateMessageId = null
                searchLocateAttachmentId = null
                onSearchCategoryChanged(category)
            },
            onOpenHistory = onSearchFocus,
            onCloseHistory = onCloseSearchHistory,
            onFillHistory = { query ->
                searchReturnAttachmentId = null
                searchLocateConversationId = null
                searchLocateMessageId = null
                searchLocateAttachmentId = null
                onFillSearchHistory(query)
            },
            onClearHistory = onClearSearchHistory,
            onOpenTextHit = { hit ->
                searchReturnAttachmentId = null
                searchLocateConversationId = null
                searchLocateMessageId = null
                searchLocateAttachmentId = null
                returnToSearchAfterSearchOpen = true
                onOpenSearchHit(hit)
                searchPageVisible = false
            },
            onOpenAttachmentHit = { hit ->
                onOpenSearchAttachment(hit.attachment)
            },
            onOpenGlmOcrHit = { hit ->
                onOpenSearchAttachment(hit.attachment)
            },
            onEnsureAttachmentPreview = onEnsureSearchAttachmentPreview,
            onLocateAttachment = { hit, anchorBounds ->
                searchAttachmentActionTarget = SearchAttachmentActionMenuTarget(hit, anchorBounds)
            },
            onLocateGlmOcr = { hit, anchorBounds ->
                searchGlmOcrActionTarget = SearchGlmOcrActionMenuTarget(hit, anchorBounds)
            },
        )
    }
    searchAttachmentActionTarget?.let { target ->
        SearchAttachmentActionPopup(
            displayName = target.hit.attachment.displayName ?: "本地附件",
            onDismiss = { searchAttachmentActionTarget = null },
            onOpenConversation = {
                val hit = target.hit
                // A quick locate leaves the search canvas only to reveal this attachment in
                // its owner conversation. The existing category Lazy state is already the
                // return viewport; carrying the search-target anchor back would force that
                // grid/list to scroll to this file and lose the user's original position.
                searchReturnAttachmentId = null
                searchLocateConversationId = null
                searchLocateMessageId = null
                searchLocateAttachmentId = null
                onLocateSearchAttachment(hit)
                searchAttachmentActionTarget = null
                returnToSearchAfterSearchOpen = true
                searchPageVisible = false
            },
            onRequestDelete = {
                confirmingSearchAttachmentDelete = target.hit
                searchAttachmentActionTarget = null
            },
        )
    }
    confirmingSearchAttachmentDelete?.let { hit ->
        TransientMenuTextScale {
            AlertDialog(
                onDismissRequest = { confirmingSearchAttachmentDelete = null },
                containerColor = ForegroundSurface,
                shape = RoundedCornerShape(24.dp),
                title = { Text("删除", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) },
                text = { Text("仅移除这条消息中的附件。", style = MaterialTheme.typography.bodyMedium) },
                confirmButton = {
                    Button(
                        onClick = {
                            confirmingSearchAttachmentDelete = null
                            onDeleteSearchAttachment(hit)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        shape = P5AInteractiveShape,
                    ) { Text("确认删除", style = MaterialTheme.typography.labelLarge) }
                },
                dismissButton = {
                    TextButton(
                        onClick = { confirmingSearchAttachmentDelete = null },
                        shape = RoundedCornerShape(14.dp),
                    ) { Text("取消", style = MaterialTheme.typography.labelLarge) }
                },
            )
        }
    }
    searchGlmOcrActionTarget?.let { target ->
        SearchAttachmentActionPopup(
            displayName = target.hit.attachment.displayName ?: "南枫转写文件",
            onDismiss = { searchGlmOcrActionTarget = null },
            onOpenConversation = {
                searchGlmOcrActionTarget = null
                returnToSearchAfterSearchOpen = true
                searchPageVisible = false
                onOpenGlmOcrSearchHit(target.hit)
            },
            onRequestDelete = {
                confirmingSearchGlmOcrDelete = target.hit
                searchGlmOcrActionTarget = null
            },
        )
    }
    confirmingSearchGlmOcrDelete?.let { hit ->
        TransientMenuTextScale {
            AlertDialog(
                onDismissRequest = { confirmingSearchGlmOcrDelete = null },
                containerColor = ForegroundSurface,
                shape = RoundedCornerShape(24.dp),
                title = { Text("删除南枫转写", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) },
                text = { Text("将删除这条转写记录、原始文件和生成的 Markdown；仍被其他位置引用的共享文件会保留。", style = MaterialTheme.typography.bodyMedium) },
                confirmButton = {
                    Button(
                        onClick = {
                            confirmingSearchGlmOcrDelete = null
                            onDeleteGlmOcrSearchHit(hit)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                        shape = P5AInteractiveShape,
                    ) { Text("确认删除", style = MaterialTheme.typography.labelLarge) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmingSearchGlmOcrDelete = null }, shape = RoundedCornerShape(14.dp)) {
                        Text("取消", style = MaterialTheme.typography.labelLarge)
                    }
                },
            )
        }
    }
    // Register after ModalNavigationDrawer so this handler wins over the drawer's internal
    // callback: physical left/right Back closes into the current chat canvas, never Activity.
    if (drawerState.isOpen) BackHandler { drawerScope.launch { drawerState.close() } }
    if (interceptsSystemBack && !drawerState.isOpen) BackHandler(onBack = onDismiss)
    if (returnToSearchAfterSearchOpen && !drawerState.isOpen) {
        BackHandler(onBack = ::returnToSearchAfterSearchOpen)
    }
    conversationActionTarget?.let { target ->
        val conversation = state.conversations.firstOrNull { it.id.value == target.conversationId }
        if (conversation == null) conversationActionTarget = null else ConversationActionSheet(
            conversation = conversation,
            anchor = target.anchor,
            workMode = workMode,
            cloudList = target.cloudList,
            cloudPinned = conversation.id.value in cloudPinnedConversationIds,
            onDismiss = { conversationActionTarget = null },
            onManage = onManage,
            onToggleCloudPinned = { pinned -> onToggleCloudConversationPinned(conversation, pinned) },
            onMarkWatchLater = onMarkWatchLater,
            onRequestRename = { target ->
                conversationActionTarget = null
                onSelect(target.id)
                renameText = target.title
                renameVisible = true
            },
            onRequestProject = { target ->
                conversationActionTarget = null
                onSelect(target.id)
                choosingProject = true
            },
            onRequestDelete = { target ->
                conversationActionTarget = null
                onSelect(target.id)
                confirmingDelete = true
            },
            onShareConversation = { target ->
                conversationActionTarget = null
                exportConversationMarkdown(target)
            },
            onSyncConversation = { target ->
                conversationActionTarget = null
                onSyncConversation(target)
            },
            onCancelConversationSync = { target ->
                conversationActionTarget = null
                onCancelConversationSync(target)
            },
            isSyncedToCloud = conversation.id.value in syncedConversationIds,
            onShowUploadedFiles = { target ->
                conversationActionTarget = null
                if (target.id == state.selectedConversationId) {
                    uploadedFilesVisible = true
                } else {
                    onSelect(target.id)
                    Toast.makeText(context, "已打开该对话，请再次查看已上传文件。", Toast.LENGTH_SHORT).show()
                }
            },
            onFindInConversation = { target ->
                conversationActionTarget = null
                if (target.id == state.selectedConversationId) {
                    findInConversationVisible = true
                } else {
                    onSelect(target.id)
                    Toast.makeText(context, "已打开该对话，请再次选择聊天内查找。", Toast.LENGTH_SHORT).show()
                }
            },
            onAddToHomeScreen = { target ->
                conversationActionTarget = null
                requestPinConversationShortcut(context, target)
            },
        )
    }
    if (uploadedFilesVisible) ConversationUploadedFilesDialog(
        attachments = state.messages.flatMap { transcript ->
            transcript.message.blocks.filterIsInstance<PresentationBlock.AttachmentReference>().map { it.attachment }
        }.distinctBy { it.id },
        previews = state.attachmentPreviews,
        onDismiss = { uploadedFilesVisible = false },
        onOpen = { attachment ->
            uploadedFilesVisible = false
            when {
                attachment.mimeType.startsWith("image/") -> onOpenImagePreview(attachment.id)
                attachment.mimeType == "application/pdf" -> onOpenPdfPreview(attachment.id)
                attachment.mimeType == "video/mp4" -> onOpenVideoPreview(attachment.id)
                attachment.mimeType in com.nanzhufeng.ai.domain.CONVERSATION_ALLOWED_AUDIO_MIME_TYPES -> onOpenAudioPreview(attachment.id)
                else -> onOpenTextPreview(attachment.id)
            }
        },
    )
    if (findInConversationVisible) ConversationFindInChatDialog(
        messages = state.messages,
        onDismiss = { findInConversationVisible = false },
        onFind = { query ->
            activeFindQuery = query
            activeFindMatchIndex = 0
            activeFindRequestId = 0L
            activeFindSessionId += 1L
            findInConversationVisible = false
        },
    )
    if (choosingProject) ProjectAssignmentDialog(projects, currentProjectId, onAssign = { projectId, assigned ->
        state.conversations.firstOrNull { it.id == state.selectedConversationId }?.let { conversation ->
            onAssignProject(conversation, if (assigned) projectId else null)
        }
    }) { choosingProject = false }
    if (choosingModel) {
        AlertDialog(
            onDismissRequest = { choosingModel = false }, containerColor = ForegroundSurface, shape = RoundedCornerShape(24.dp),
            title = { Text("换模型重答") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    NanfengModelServiceCatalog.presets.forEach { preset ->
                        OutlinedButton(onClick = { choosingModel = false; onAction(ConversationActionKind.CHANGE_MODEL, preset.id) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text(preset.displayName, fontWeight = FontWeight.Bold) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choosingModel = false }, shape = RoundedCornerShape(14.dp)) { Text("取消") } },
        )
    }
    messageActionTarget?.let { target ->
        val rawId = target.messageId
        val transcript = state.messages.firstOrNull { it.message.messageId.value == rawId }
        val editableForMenu = state.editableUserMessages.associateBy { it.messageId }
        if (transcript == null) messageActionTarget = null else MessageActionPopup(
            anchorBounds = target.anchorBounds,
            pressPosition = target.pressPosition,
            headline = transcript.metadata.modelSnapshotLabel?.takeIf { it.isNotBlank() }?.let { modelName ->
                modelNameAnnotatedText(
                    prefix = formatTranscriptTimeOrNull(transcript.metadata.createdAt)?.let { "$it · " }.orEmpty(),
                    modelName = modelName,
                )
            } ?: AnnotatedString(formatTranscriptTimeOrNull(transcript.metadata.createdAt).orEmpty()),
            editable = transcript.message.role == com.nanzhufeng.ai.domain.MessageRole.USER && editableForMenu[transcript.message.messageId] != null,
            onDismiss = { messageActionTarget = null },
        ) {
            val copied = copiedContextMessageId == rawId
            MessageContextAction(
                if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                if (copied) "已复制" else "复制",
                iconOnly = true,
            ) {
                copyText(presentedMessagePlainText(transcript.message)) { copiedContextMessageId = rawId }
            }
            MessageContextAction(Icons.Rounded.SelectAll, "选择文本") {
                selectingTextMessageId = rawId
                messageActionTarget = null
            }
            if (transcript.message.role == com.nanzhufeng.ai.domain.MessageRole.USER && editableForMenu[transcript.message.messageId] != null) {
                MessageContextAction(Icons.Rounded.Edit, "编辑消息") {
                    editingMessageId = rawId
                    editingText = editableForMenu.getValue(transcript.message.messageId).text
                    messageActionTarget = null
                }
            }
            MessageContextAction(Icons.Rounded.Share, "分享") {
                shareMessage(transcript)
                messageActionTarget = null
            }
        }
    }
    selectingTextMessageId?.let { rawId ->
        val transcript = state.messages.firstOrNull { it.message.messageId.value == rawId }
        if (transcript == null) selectingTextMessageId = null else WideTextSelectionDialog(
            text = presentedMessagePlainText(transcript.message),
            onDismiss = { selectingTextMessageId = null },
        )
    }
    if (renameVisible) CompactConversationRenameDialog(
        value = renameText,
        onValueChange = { renameText = it },
        onDismiss = { renameVisible = false },
        onConfirm = {
            state.conversations.firstOrNull { it.id == state.selectedConversationId }?.let { conversation ->
                onManage(conversation, ConversationManagementAction.RENAME, renameText)
            }
            renameVisible = false
        },
    )
    if (confirmingDelete) AlertDialog(
        onDismissRequest = { confirmingDelete = false }, containerColor = ForegroundSurface, shape = RoundedCornerShape(24.dp),
        title = { Text("移入回收站？") },
        text = { Text("可从回收站恢复。") },
        confirmButton = { Button(onClick = {
            state.conversations.firstOrNull { it.id == state.selectedConversationId }?.let { onManage(it, ConversationManagementAction.SOFT_DELETE, null) }
            confirmingDelete = false
        }, shape = RoundedCornerShape(14.dp)) { Text("移入回收站") } },
        dismissButton = { TextButton(onClick = { confirmingDelete = false }, shape = RoundedCornerShape(14.dp)) { Text("取消") } },
    )
    editingMessageId?.let { rawId ->
        EditUserMessageDialog(
            value = editingText,
            onValueChange = { editingText = it },
            onDismiss = { editingMessageId = null },
            onCreateBranch = {
                editingMessageId = null
                onEditUserMessage(com.nanzhufeng.ai.domain.MessageNodeId(rawId), editingText)
            },
        )
    }
    state.imagePreview?.let { preview ->
        val relatedImageMessage = state.messages.firstOrNull { message ->
            message.message.blocks.filterIsInstance<PresentationBlock.AttachmentReference>().any { it.attachment.id == preview.id }
        }
        val generatedImageIds = relatedImageMessage?.message?.blocks
            ?.filterIsInstance<PresentationBlock.AttachmentReference>()
            ?.filter { it.attachment.mimeType.startsWith("image/") }
            ?.let { imageBlocks ->
                if (relatedImageMessage.message.role == com.nanzhufeng.ai.domain.MessageRole.USER) imageBlocks
                else ascendingAssistantImageOrder(imageBlocks)
            }
            ?.map { it.attachment }
            ?.map { it.id }
            .orEmpty()
        ImagePreviewDialog(preview, generatedImageIds, onOpenImagePreview, onCloseImagePreview, onRequestAttachmentTransfers)
    }
    state.pdfPreview?.let { preview -> PdfPreviewDialog(preview, state.pdfPreviewLoading, onOpenPdfPage, onClosePdfPreview) }
    state.videoPreview?.let { preview -> VideoPreviewDialog(preview, onCloseVideoPreview) }
    state.audioPreview?.let { preview -> AudioPreviewDialog(preview, onCloseAudioPreview) }
    state.textPreview?.let { preview -> TextPreviewDialog(preview, onCloseTextPreview) }
    state.archivePreview?.let { preview -> ArchivePreviewDialog(preview, onOpenArchiveEntry, onCloseTextPreview) }
    // P3-J is a root sibling of the workspace.  It is never measured by the transcript,
    // drawer, or composer and therefore cannot move any frozen chat-first geometry.
    }
}

/** Shared visual shell; routes may differ, but normal, work and temporary panes never invent a second header. */
@Composable
private fun ConversationShellHeader(
    workMode: Boolean,
    onModeChanged: (Boolean) -> Unit,
    onLeftAction: () -> Unit,
    leftIcon: ImageVector,
    leftDescription: String,
    onTemporaryAction: () -> Unit,
    temporaryTint: Color,
    showContentActions: Boolean,
    onCreateConversation: () -> Unit,
    onConversationActionsRequested: (androidx.compose.ui.geometry.Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            ConversationHeaderFloatingIconButton(onClick = onLeftAction) {
                Icon(leftIcon, contentDescription = leftDescription, tint = ConversationControlGlyph)
            }
            Spacer(Modifier.weight(1f))
            if (showContentActions) {
                ConversationHeaderContentActions(
                    onCreateConversation = onCreateConversation,
                    onMoreActionsRequested = onConversationActionsRequested,
                )
            } else {
                ConversationHeaderFloatingIconButton(onClick = onTemporaryAction) {
                    Icon(painterResource(R.drawable.ic_lucide_ghost), contentDescription = "临时聊天", tint = temporaryTint)
                }
            }
        }
        if (!showContentActions) ConversationModeSwitch(
            workMode = workMode,
            onModeChanged = onModeChanged,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

/** The content header mirrors the reference's compact new-chat and overflow action capsule. */
@Composable
private fun ConversationHeaderContentActions(
    onCreateConversation: () -> Unit,
    onMoreActionsRequested: (androidx.compose.ui.geometry.Rect) -> Unit,
) {
    var moreBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    val shape = RoundedCornerShape(50)
    Surface(
        modifier = Modifier.conversationForegroundShadow(shape),
        color = ForegroundSurface,
        shape = shape,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(modifier = Modifier.height(44.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                onClick = onCreateConversation,
                color = Color.Transparent,
                shape = RoundedCornerShape(50),
                modifier = Modifier.size(44.dp),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.ic_lucide_file_pen),
                        contentDescription = "新对话",
                        tint = ConversationControlGlyph,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Spacer(Modifier.width(1.dp).height(22.dp).background(ConversationControlBorder))
            Surface(
                onClick = { moreBounds?.let(onMoreActionsRequested) },
                color = Color.Transparent,
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .size(44.dp)
                    .onGloballyPositioned { moreBounds = it.boundsInRoot() },
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "对话更多操作", tint = ConversationControlGlyph)
                }
            }
        }
    }
}

/** Each header action is its own floating surface; the header never paints a backing strip. */
@Composable
private fun ConversationHeaderFloatingIconButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(44.dp).conversationForegroundShadow(CircleShape),
        color = ForegroundSurface,
        shape = CircleShape,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
private fun ConversationModeSwitch(workMode: Boolean, onModeChanged: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = NeutralSystemSurface,
        shape = RoundedCornerShape(50),
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.height(44.dp).padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ConversationModeSegment(label = "对话", selected = !workMode, onClick = { onModeChanged(false) })
            ConversationModeSegment(label = "工作", selected = workMode, onClick = { onModeChanged(true) })
        }
    }
}

@Composable
private fun ConversationModeSegment(label: String, selected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.height(38.dp).width(66.dp),
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) ForegroundSurface else Color.Transparent,
            // The selected pill is a dark foreground surface in dark mode, so its label must
            // be pure white rather than a muted gray. Light mode keeps the dark label on white.
            contentColor = if (selected) {
                if (ForegroundSurface.red < 0.5f) Color.White else Color(0xFF3F3F3F)
            } else {
                ConversationControlGlyph
            },
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
    ) { Text(label, fontWeight = FontWeight.SemiBold) }
}

/** Shown only for a local high-value suggestion; saving remains an explicit egress decision. */
@Composable
private fun ConversationTailMonitorAction(onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = BodyText),
        border = BorderStroke(1.dp, Color(0xFFD8D8D8)),
    ) {
        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("添加提醒 / 监控")
    }
}

@Composable
private fun ConversationTailMemoryAction(onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 2.dp),
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = BodyText),
        border = BorderStroke(1.dp, Color(0xFFD8D8D8)),
    ) {
        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("加入记忆摘要")
    }
}

private fun memorySummarySuggestionFor(messages: List<PresentedTranscriptMessage>): com.nanzhufeng.ai.domain.MemorySummaryDraft? {
    val assistantIndex = messages.indexOfLast { it.message.role == com.nanzhufeng.ai.domain.MessageRole.ASSISTANT && it.message.deliveryState == com.nanzhufeng.ai.domain.MessageDeliveryState.COMPLETE }
    if (assistantIndex < 0 || assistantIndex != messages.lastIndex) return null
    val user = messages.subList(0, assistantIndex).lastOrNull { it.message.role == com.nanzhufeng.ai.domain.MessageRole.USER } ?: return null
    return com.nanzhufeng.ai.domain.MemorySummaryPolicy.suggestionFor(presentedMessagePlainText(user.message), presentedMessagePlainText(messages[assistantIndex].message))
}

private fun scheduledMonitorDraftSourceFor(messages: List<PresentedTranscriptMessage>): com.nanzhufeng.ai.domain.ScheduledMonitorDraftSource? {
    // The draft must be grounded in one stable, explainable source pair: the opening user
    // request and the first completed 南枫AI reply after it. A later answer cannot inject an
    // unrelated product category into the reminder.
    val userIndex = messages.indexOfFirst { it.message.role == com.nanzhufeng.ai.domain.MessageRole.USER }
    if (userIndex < 0 || messages.lastOrNull()?.message?.deliveryState != com.nanzhufeng.ai.domain.MessageDeliveryState.COMPLETE) return null
    val user = messages[userIndex]
    val assistant = messages.drop(userIndex + 1).firstOrNull {
        it.message.role == com.nanzhufeng.ai.domain.MessageRole.ASSISTANT &&
            it.message.deliveryState == com.nanzhufeng.ai.domain.MessageDeliveryState.COMPLETE
    } ?: return null
    val userText = presentedMessagePlainText(user.message)
    if (!com.nanzhufeng.ai.domain.ScheduledMonitorSuggestionPolicy.shouldOffer(userText)) return null
    return com.nanzhufeng.ai.domain.ScheduledMonitorDraftSource(userText, presentedMessagePlainText(assistant.message))
}

/** Work projects the current conversation's local scope; it is not a second module dashboard. */
@Composable
private fun ConversationWorkScope(
    state: ConversationFoundationUiState,
    attachmentPreviews: Map<AttachmentId, ConversationAttachmentPreview>,
    listState: LazyListState,
    followLatest: Boolean,
    systemScrollCaptureInProgress: Boolean,
    onFollowLatestChanged: (Boolean) -> Unit,
    sentFromMessageCount: Int?,
    onSentToLatestConsumed: () -> Unit,
    onLongPress: (MessageNodeId, androidx.compose.ui.geometry.Rect, androidx.compose.ui.geometry.Offset) -> Unit,
    copiedAssistantMessageId: String?,
    onCopyAssistant: (PresentedTranscriptMessage) -> Unit,
    onShareAssistant: (PresentedTranscriptMessage) -> Unit,
    onBranchAssistant: (MessageNodeId) -> Unit,
    onEnsureAttachmentPreview: (ConversationAttachmentReference) -> Unit,
    onOpenImagePreview: (AttachmentId) -> Unit,
    onOpenPdfPreview: (AttachmentId) -> Unit,
    onOpenVideoPreview: (AttachmentId) -> Unit,
    onOpenAudioPreview: (AttachmentId) -> Unit,
    onOpenTextPreview: (AttachmentId) -> Unit,
) {
    // Work changes the conversation scope only. It deliberately reuses normal chat rows,
    // including their right alignment and responsive reading width, rather than maintaining
    // a second transcript projection with a different proportion.
    LaunchedEffect(listState) {
        snapshotFlow {
            listState.layoutInfo.totalItemsCount == 0 || !listState.canScrollForward
        }.collect { atLatest -> if (atLatest) onFollowLatestChanged(true) }
    }
    // Manual reading takes precedence over automatic updates. Programmatic scrolls do not
    // emit this interaction, so an explicit send and a true-bottom state retain their meaning.
    val workUserDragging by listState.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(workUserDragging) {
        if (workUserDragging) onFollowLatestChanged(false)
    }
    LaunchedEffect(
        state.messages.size,
        sentFromMessageCount,
        followLatest,
        systemScrollCaptureInProgress,
    ) {
        if (systemScrollCaptureInProgress) return@LaunchedEffect
        val sentCount = sentFromMessageCount
        if (sentCount != null && state.messages.size > sentCount && listState.layoutInfo.totalItemsCount > 0) {
            listState.scrollToTrueBottom()
            onSentToLatestConsumed()
        } else if (followLatest && listState.layoutInfo.totalItemsCount > 0) {
            listState.scrollToTrueBottom()
        }
    }
    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        state = listState,
        contentPadding = ConversationTranscriptContentPadding,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.messages.isEmpty()) item(key = "work-empty") {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "还没有项目工作对话\n从左侧项目中创建或打开一条对话。",
                    color = SecondaryText,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.semantics { contentDescription = "工作内容为空；请从左侧项目创建或打开工作对话" },
                )
            }
        }
        itemsIndexed(state.messages, key = { _, transcript -> "work-${transcript.message.messageId.value}" }) { index, transcript ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val previous = state.messages.getOrNull(index - 1)
                if (previous == null || transcriptLocalDateKey(previous.metadata.createdAt) != transcriptLocalDateKey(transcript.metadata.createdAt)) {
                    TranscriptDateDivider(transcriptLocalDateKey(transcript.metadata.createdAt))
                }
                MessageBubble(
                    transcript = transcript,
                    attachmentPreviews = attachmentPreviews,
                    contextSelections = state.answerContextSelections[transcript.message.messageId].orEmpty(),
                    responseAttributions = state.answerResponseAttributions[transcript.message.messageId].orEmpty(),
                    assistantGenerationPhaseOverride = if (
                        state.normalSendRetryInProgress && transcript.message.messageId == state.currentLeafId
                    ) "正在重新生成…" else null,
                    assistantGenerationIsRetry = state.normalSendRetryInProgress && transcript.message.messageId == state.currentLeafId,
                    onLongPress = onLongPress,
                    copiedAssistantMessageId = copiedAssistantMessageId,
                    onCopyAssistant = onCopyAssistant,
                    onShareAssistant = onShareAssistant,
                    onBranchAssistant = onBranchAssistant,
                    onEnsureAttachmentPreview = onEnsureAttachmentPreview,
                    onOpenImagePreview = onOpenImagePreview,
                    onOpenPdfPreview = onOpenPdfPreview,
                    onOpenVideoPreview = onOpenVideoPreview,
                    onOpenAudioPreview = onOpenAudioPreview,
                    onOpenTextPreview = onOpenTextPreview,
                )
            }
        }
    }
    }
}

/** Advances exactly one currently visible reading page without targeting any transcript item. */
private suspend fun LazyListState.animateForwardByVisibleViewport(bottomOverlayHeightPx: Float) {
    val viewportHeightPx = layoutInfo.viewportSize.height.toFloat()
    val visibleReadingHeightPx = (viewportHeightPx - bottomOverlayHeightPx).coerceAtLeast(0f)
    if (!canScrollForward || visibleReadingHeightPx <= 0f) return
    animateScrollBy(visibleReadingHeightPx)
}

/** Rewinds exactly one currently visible reading page without targeting any transcript item. */
private suspend fun LazyListState.animateBackwardByVisibleViewport(bottomOverlayHeightPx: Float) {
    val viewportHeightPx = layoutInfo.viewportSize.height.toFloat()
    val visibleReadingHeightPx = (viewportHeightPx - bottomOverlayHeightPx).coerceAtLeast(0f)
    if (!canScrollBackward || visibleReadingHeightPx <= 0f) return
    animateScrollBy(-visibleReadingHeightPx)
}

/** Reaches the true physical end; it must never leave a long final row aligned at its top. */
private suspend fun LazyListState.scrollToTrueBottom() {
    if (layoutInfo.totalItemsCount == 0 || !canScrollForward) return
    scrollToItem(layoutInfo.totalItemsCount - 1)
    val viewportHeightPx = layoutInfo.viewportSize.height.toFloat().coerceAtLeast(1f)
    while (canScrollForward) {
        if (scrollBy(viewportHeightPx) <= 0f) return
    }
}

/** Reaches the true physical start, including any scrollable transcript inset. */
private suspend fun LazyListState.scrollToTrueTop() {
    if (layoutInfo.totalItemsCount == 0 || !canScrollBackward) return
    scrollToItem(0)
    val viewportHeightPx = layoutInfo.viewportSize.height.toFloat().coerceAtLeast(1f)
    while (canScrollBackward) {
        if (scrollBy(-viewportHeightPx) >= 0f) return
    }
}

private val ScreenEdgeGestureWidth = 24.dp
private val ScreenEdgeGestureTravel = 48.dp
private const val ScreenEdgeGestureHorizontalRatio = 1.3f
private val QuickDrawerOpenTravel = 36.dp
private const val QuickDrawerOpenHorizontalRatio = 1.35f

/**
 * Keeps Material's full-canvas drawer gesture, then finishes a clearly horizontal right swipe
 * promptly instead of making the user drag the sheet across most of a wide screen.
 */
private fun Modifier.quickConversationDrawerOpen(onOpen: () -> Unit): Modifier = pointerInput(onOpen) {
    val openThresholdPx = QuickDrawerOpenTravel.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var previousPosition = down.position
        var horizontalDistancePx = 0f
        var verticalDistancePx = 0f
        var openRequested = false
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            val delta = change.position - previousPosition
            previousPosition = change.position
            horizontalDistancePx += delta.x
            verticalDistancePx += delta.y
            if (!openRequested &&
                horizontalDistancePx >= openThresholdPx &&
                horizontalDistancePx > abs(verticalDistancePx) * QuickDrawerOpenHorizontalRatio
            ) {
                openRequested = true
            }
            if (openRequested) {
                // After intent is established, own the remainder of this swipe so Material does
                // not settle the same partial drag closed. Taps, vertical scroll and left swipes
                // never reach this branch and remain free.
                change.consume()
            }
            if (change.changedToUpIgnoreConsumed()) {
                if (openRequested) onOpen()
                break
            }
        }
    }
}

/** A search result may temporarily open its owner conversation; only an inward edge swipe returns. */
private fun Modifier.searchAttachmentReturnSwipe(onReturn: () -> Unit): Modifier = pointerInput(onReturn) {
    val edgeWidthPx = ScreenEdgeGestureWidth.toPx()
    val returnThresholdPx = ScreenEdgeGestureTravel.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val direction = when {
            down.position.x <= edgeWidthPx -> 1
            down.position.x >= size.width - edgeWidthPx -> -1
            else -> 0
        }
        if (direction == 0) return@awaitEachGesture
        var previousPosition = down.position
        var horizontalDistancePx = 0f
        var verticalDistancePx = 0f
        var returned = false
        while (!returned) {
            // Read raw coordinates rather than participating in drag consumption: nested
            // transcript rows and the drawer may own their own gesture, but this one-shot
            // source return must still observe either horizontal direction.
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            val delta = change.position - previousPosition
            previousPosition = change.position
            horizontalDistancePx += delta.x
            verticalDistancePx += delta.y
            if (
                horizontalDistancePx * direction >= returnThresholdPx &&
                abs(horizontalDistancePx) > abs(verticalDistancePx) * ScreenEdgeGestureHorizontalRatio
            ) {
                returned = true
                onReturn()
            }
            if (change.changedToUpIgnoreConsumed()) break
        }
    }
}

@Composable
private fun JumpToTopButton(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) = TranscriptJumpButton(
    icon = Icons.Rounded.KeyboardArrowUp,
    contentDescription = "回到对话开头",
    onClick = onClick,
    onLongClick = onLongClick,
    modifier = modifier,
)

@Composable
private fun JumpToLatestButton(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) = TranscriptJumpButton(
    icon = Icons.Rounded.KeyboardArrowDown,
    contentDescription = "到最新消息",
    onClick = onClick,
    onLongClick = onLongClick,
    modifier = modifier,
)

/** A non-tonal pure-white control; Material FAB's elevation tint is intentionally avoided. */
@Composable
private fun TranscriptJumpButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        color = ForegroundSurface,
        contentColor = BodyText,
        shape = CircleShape,
        tonalElevation = 0.dp,
        shadowElevation = 3.dp,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(30.dp),
            )
        }
    }
}

@Composable
private fun ConversationAttemptHistory(items: List<com.nanzhufeng.ai.domain.ConversationAttemptHistoryItem>) {
    Column(
        verticalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFF5F8F6)).padding(10.dp),
    ) {
        Text("本地尝试历史", style = MaterialTheme.typography.labelLarge, color = SecondaryText)
        Text("只显示继续、重试或换模型产生的本地 fixture 谱系；不代表真实 Provider、Token 或费用。", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
        if (items.isEmpty()) {
            Text("尚无由继续、重试或换模型产生的本地尝试。", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
        } else items.forEach { item ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("${attemptActionLabel(item.actionKind)} · ${attemptStateLabel(item.deliveryState)}", style = MaterialTheme.typography.bodyMedium)
                Text("${com.nanzhufeng.ai.domain.modelDisplayNameForUser(item.modelDisplayName)} · ${item.modelId}", style = MaterialTheme.typography.bodySmall, color = SecondaryText, fontWeight = FontWeight.Bold)
                Text("${item.harnessLabel} · ${item.registrySnapshotId.value}", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
                Text("本地 fixture · 不产生 Provider 费用 · ${attemptTokenLabel(item)}", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
            }
        }
    }
}

private fun attemptActionLabel(kind: ConversationActionKind) = when (kind) {
    ConversationActionKind.CONTINUE -> "继续"
    ConversationActionKind.RETRY -> "重试"
    ConversationActionKind.CHANGE_MODEL -> "换模型重答"
}

private fun attemptStateLabel(state: com.nanzhufeng.ai.domain.MessageDeliveryState) = when (state) {
    com.nanzhufeng.ai.domain.MessageDeliveryState.COMPLETE -> "本地完成"
    com.nanzhufeng.ai.domain.MessageDeliveryState.PARTIAL -> "本地进行中"
    com.nanzhufeng.ai.domain.MessageDeliveryState.FAILED -> "本地失败"
    com.nanzhufeng.ai.domain.MessageDeliveryState.CANCELLED -> "已本地停止"
}

private fun attemptTokenLabel(item: com.nanzhufeng.ai.domain.ConversationAttemptHistoryItem): String =
    if (item.inputTokens == null && item.outputTokens == null) "Token 未知" else
        "本地事件 Token：输入 ${item.inputTokens ?: "未知"} · 输出 ${item.outputTokens ?: "未知"}"

@Composable
private fun ConversationNavigationDrawer(
    state: ConversationFoundationUiState,
    notificationReminderSettings: com.nanzhufeng.ai.domain.NotificationReminderSettings,
    projects: List<ProjectSnapshot>,
    onCreate: () -> Unit,
    onSelect: (com.nanzhufeng.ai.domain.ConversationId) -> Unit,
    onClearWatchLater: (com.nanzhufeng.ai.domain.ConversationId) -> Unit,
    onScope: (ConversationListScope) -> Unit,
    onOpenSearchPage: () -> Unit,
    onManage: (com.nanzhufeng.ai.domain.Conversation, ConversationManagementAction, String?) -> Unit,
    onBatchSoftDelete: (List<com.nanzhufeng.ai.domain.Conversation>) -> Unit,
    workMode: Boolean,
    onModeChanged: (Boolean) -> Unit,
    onRequestRename: (com.nanzhufeng.ai.domain.Conversation) -> Unit,
    onRequestProject: (com.nanzhufeng.ai.domain.Conversation) -> Unit,
    onRequestDelete: (com.nanzhufeng.ai.domain.Conversation) -> Unit,
    onRequestConversationActions: (com.nanzhufeng.ai.domain.Conversation, androidx.compose.ui.geometry.Rect, Boolean) -> Unit,
    onCreateProject: () -> Unit,
    onManageProjects: () -> Unit,
    onManageProject: (ProjectId) -> Unit,
    onPinProject: (ProjectId, Boolean) -> Unit,
    onArchiveProject: (ProjectId, Boolean) -> Unit,
    onCreateWorkConversation: (ProjectId) -> Unit,
    onOpenRoute: (P5ARoute) -> Unit,
    onOpenScheduledMonitors: () -> Unit,
    onExport: () -> Unit,
    syncedConversationIds: Set<String>,
    cloudConversations: List<com.nanzhufeng.ai.domain.Conversation>,
    cloudPinnedConversationIds: Set<String>,
    onToggleCloudConversationPinned: (com.nanzhufeng.ai.domain.Conversation, Boolean) -> Unit,
    onReadCloudConversations: () -> Unit,
) {
    // Creating a chat opens an empty workspace immediately, but the drawer is a history of
    // conversations that actually contain content. The first persisted message supplies the
    // leaf and makes the row appear without changing draft or first-send ownership.
    val allDrawerConversations = state.conversations.filter { it.currentLeafMessageId != null }
    var cloudListVisible by rememberSaveable { mutableStateOf(false) }
    // Cloud rows are local projections with a durable cloud receipt. Their presentation pin is
    // deliberately separate from Conversation.pinnedAt, exactly like Desktop's cloud list.
    val drawerConversations = if (cloudListVisible) {
        // The normal Room query deliberately puts *local* pins first.  A cloud
        // projection must not inherit that ordering: its only grouping is the
        // cloud-list presentation pin, then the common conversation update time.
        // Without this explicit order a phone could show a locally pinned row
        // ahead of Desktop's cloud order even after both had read the same data.
        cloudConversations
            .filter { it.id.value in syncedConversationIds }
            .sortedWith(compareByDescending<com.nanzhufeng.ai.domain.Conversation> { it.updatedAt }.thenBy { it.id.value })
    } else {
        allDrawerConversations
    }
    // A row may expose its quick actions, but the drawer owns which one is open so
    // a deliberate swipe never leaves multiple rows half-open after scrolling.
    var revealedConversationId by remember { mutableStateOf<String?>(null) }
    val drawerDismissInteractionSource = remember { MutableInteractionSource() }
    fun dismissRevealedConversation(): Boolean {
        if (revealedConversationId == null) return false
        revealedConversationId = null
        return true
    }
    val onConversationSwipeAction: (com.nanzhufeng.ai.domain.Conversation, ConversationRowSwipeAction) -> Unit = { conversation, action ->
        revealedConversationId = null
        when (action) {
            ConversationRowSwipeAction.TOGGLE_PIN -> if (cloudListVisible) {
                onToggleCloudConversationPinned(conversation, conversation.id.value !in cloudPinnedConversationIds)
            } else onManage(
                conversation,
                if (conversation.pinnedAt == null) ConversationManagementAction.PIN else ConversationManagementAction.UNPIN,
                null,
            )
            ConversationRowSwipeAction.TOGGLE_FAVORITE -> onManage(
                conversation,
                if (conversation.favoritedAt == null) ConversationManagementAction.FAVORITE else ConversationManagementAction.UNFAVORITE,
                null,
            )
            ConversationRowSwipeAction.RENAME -> onRequestRename(conversation)
            ConversationRowSwipeAction.DELETE -> onRequestDelete(conversation)
        }
    }
    if (workMode) {
        WorkProjectNavigationDrawer(
            projects = projects,
            conversations = drawerConversations,
            selectedConversationId = state.selectedConversationId,
            watchLaterAtEpochMs = state.watchLaterAtEpochMs,
            onSelect = { id ->
                // Match the normal drawer: with a row ribbon exposed, the first tap
                // on any other conversation is a safe dismissal, never a surprise
                // navigation away from the currently open conversation.
                if (revealedConversationId != null) {
                    revealedConversationId = null
                } else {
                    onClearWatchLater(id)
                    onSelect(id)
                }
            },
            onConversationActions = { conversation, anchor ->
                if (revealedConversationId != null) revealedConversationId = null else onRequestConversationActions(conversation, anchor, false)
            },
            onConversationSwipeAction = onConversationSwipeAction,
            revealedConversationId = revealedConversationId,
            onRevealedConversationIdChange = { revealedConversationId = it },
            onCreateProject = { if (revealedConversationId != null) revealedConversationId = null else onCreateProject() },
            onManageProjects = { if (revealedConversationId != null) revealedConversationId = null else onManageProjects() },
            onManageProject = { projectId -> if (revealedConversationId != null) revealedConversationId = null else onManageProject(projectId) },
            onPinProject = { projectId, pinned -> if (revealedConversationId != null) revealedConversationId = null else onPinProject(projectId, pinned) },
            onArchiveProject = { projectId, archived -> if (revealedConversationId != null) revealedConversationId = null else onArchiveProject(projectId, archived) },
            onCreateWorkConversation = { projectId -> if (revealedConversationId != null) revealedConversationId = null else onCreateWorkConversation(projectId) },
            onOpenSettings = { if (revealedConversationId != null) revealedConversationId = null else onOpenRoute(P5ARoute.SETTINGS) },
        )
    } else {
        fun prioritizeWatchLater(items: List<com.nanzhufeng.ai.domain.Conversation>) = items.sortedWith(
            compareByDescending<com.nanzhufeng.ai.domain.Conversation> { it.id in state.watchLaterAtEpochMs }
                .thenByDescending { state.watchLaterAtEpochMs[it.id] ?: Long.MIN_VALUE },
        )
        val pinned = remember(drawerConversations, state.watchLaterAtEpochMs, cloudListVisible, cloudPinnedConversationIds) {
            prioritizeWatchLater(drawerConversations.filter { conversation ->
                if (cloudListVisible) conversation.id.value in cloudPinnedConversationIds else conversation.pinnedAt != null
            })
        }
        val content = remember(drawerConversations, state.watchLaterAtEpochMs, cloudListVisible, cloudPinnedConversationIds) {
            prioritizeWatchLater(drawerConversations.filter { conversation ->
                if (cloudListVisible) conversation.id.value !in cloudPinnedConversationIds else conversation.pinnedAt == null
            })
        }
        val batchCandidates = remember(pinned, content) { pinned + content }
        val batchCandidateIds = remember(batchCandidates) {
            batchCandidates.mapTo(linkedSetOf()) { it.id.value }
        }
        var batchEditing by remember { mutableStateOf(false) }
        var selectedBatchConversationIds by remember { mutableStateOf<Set<String>>(emptySet()) }
        var pendingBatchDelete by remember { mutableStateOf<List<com.nanzhufeng.ai.domain.Conversation>>(emptyList()) }
        LaunchedEffect(drawerConversations) {
            val currentIds = drawerConversations.map { it.id.value }.toSet()
            selectedBatchConversationIds = selectedBatchConversationIds.intersect(currentIds)
        }
        val allBatchSelected = batchCandidateIds.isNotEmpty() &&
            selectedBatchConversationIds.size == batchCandidateIds.size &&
            selectedBatchConversationIds.containsAll(batchCandidateIds)
        val drawerIdentityVisualSize = scaledAppIconSize(36.dp)
        val drawerIdentityTitleLineHeight = scaledAppTextUnit(28.sp)
        val drawerContentTopInset = with(LocalDensity.current) { WindowInsets.statusBars.getTop(this).toDp() } + 16.dp
        val drawerIdentityHeaderReservedHeight = maxOf(
            drawerIdentityVisualSize,
            with(LocalDensity.current) { drawerIdentityTitleLineHeight.toDp() },
        )
        // The settings and new-conversation controls are true overlays, but the final
        // conversation must still be able to scroll completely above them.  This is
        // scrollable end space only: it never creates a fixed drawer tray or a gap in
        // the list while the user is reading earlier conversations.
        val drawerScrollableEndInset = if (batchEditing) 156.dp else 96.dp
        Box(modifier = Modifier.fillMaxSize().background(ConversationDrawerBaseSurface)) {
            // Empty drawer space is a neutral dismissal target for the currently revealed row.
            // Interactive children remain above this transparent layer and retain their action.
            LazyColumn(
                // The two bottom actions are independent floating controls.  Keep the
                // conversation canvas continuous beneath them instead of reserving a
                // bottom tray (which visually reads as a white strip behind the actions).
                // The sheet itself reaches the status-bar edge to keep one continuous drawer
                // canvas. The list starts below the fixed app identity at rest, while the
                // identity itself is a later, non-scrolling overlay below the status bar.
                modifier = Modifier
                    .fillMaxSize()
                    // This is the scroll viewport treatment: it must wrap the scroll node,
                    // not be a sibling layer or be clipped by the content safe-area inset.
                    .conversationEdgeGrayFade(edgeColor = ConversationDrawerBaseSurface)
                    .padding(horizontal = 5.dp)
                    .clickable(
                        enabled = revealedConversationId != null,
                        interactionSource = drawerDismissInteractionSource,
                        indication = null,
                        onClick = { dismissRevealedConversation() },
                ),
                contentPadding = PaddingValues(top = drawerContentTopInset),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // This is scrollable top space for the fixed identity header. It must match the
                // larger icon/title line so the first quick action never sits underneath it.
                item(key = "drawer-identity-space", contentType = "spacer") {
                    Spacer(Modifier.height(drawerIdentityHeaderReservedHeight))
                }
                item(key = "drawer-search", contentType = "quick-action") {
                    Surface(
                        color = ConversationDrawerQuickActionSurface,
                        shape = P5AInteractiveShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .clip(P5AInteractiveShape)
                            .combinedClickable(onClick = {
                                if (!dismissRevealedConversation()) onOpenSearchPage()
                            }),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(scaledAppIconSize(17.dp)), tint = BodyText)
                            Text(
                                "搜索",
                                color = BodyText,
                                fontSize = scaledConversationTextUnit(16.sp),
                                lineHeight = scaledConversationTextUnit(20.sp),
                            )
                        }
                    }
                }
                item(key = "drawer-scheduled", contentType = "quick-action") {
                    Surface(
                        onClick = {
                            if (!dismissRevealedConversation()) onOpenScheduledMonitors()
                        },
                        color = Color.Transparent,
                        contentColor = BodyText,
                        shape = P5AInteractiveShape,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.Schedule, contentDescription = null, modifier = Modifier.size(scaledAppIconSize(24.dp)), tint = BodyText)
                            Spacer(Modifier.width(16.dp))
                            Text(
                                "定时任务",
                                color = BodyText,
                                fontSize = scaledConversationTextUnit(16.sp),
                                lineHeight = scaledConversationTextUnit(20.sp),
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
                item(key = "drawer-document-markdown", contentType = "quick-action") {
                    Surface(
                        onClick = {
                            if (!dismissRevealedConversation()) onOpenRoute(P5ARoute.OCR)
                        },
                        color = Color.Transparent,
                        contentColor = BodyText,
                        shape = P5AInteractiveShape,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.Description, contentDescription = null, modifier = Modifier.size(scaledAppIconSize(24.dp)), tint = BodyText)
                            Spacer(Modifier.width(16.dp))
                            Text(
                                GLM_OCR_WORKSPACE_TITLE,
                                color = BodyText,
                                fontSize = scaledConversationTextUnit(16.sp),
                                lineHeight = scaledConversationTextUnit(20.sp),
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
                item(key = "drawer-list-mode", contentType = "section-header") {
                    DrawerNavigationListControls(
                        cloudListVisible = cloudListVisible,
                        batchEditing = batchEditing,
                        batchEnabled = batchCandidates.isNotEmpty(),
                        onShowLocal = {
                            if (!dismissRevealedConversation()) {
                                cloudListVisible = false
                                batchEditing = false
                                selectedBatchConversationIds = emptySet()
                            }
                        },
                        onShowCloud = {
                            if (!dismissRevealedConversation()) {
                                cloudListVisible = true
                                batchEditing = false
                                selectedBatchConversationIds = emptySet()
                            }
                        },
                        onToggleBatch = {
                            if (!dismissRevealedConversation()) {
                                batchEditing = !batchEditing
                                selectedBatchConversationIds = emptySet()
                            }
                        },
                        onReadCloud = {
                            // Reading is a list operation: once its durable receipts arrive,
                            // leave the user in the cloud projection where those rows appear.
                            cloudListVisible = true
                            onReadCloudConversations()
                        },
                    )
                }
                if (pinned.isNotEmpty() && state.listScope == ConversationListScope.ACTIVE) {
                    item(key = "drawer-pinned-header", contentType = "section-header") {
                        Text("已置顶", style = MaterialTheme.typography.labelSmall, color = SecondaryText)
                    }
                    items(
                        items = pinned,
                        key = { "pinned:${it.id.value}" },
                        contentType = { "conversation-row" },
                    ) { conversation ->
                        ConversationNavigationRow(
                            conversation = conversation,
                            selected = conversation.id == state.selectedConversationId,
                            presentationPinned = if (cloudListVisible) conversation.id.value in cloudPinnedConversationIds else conversation.pinnedAt != null,
                            syncedToCloud = !cloudListVisible && conversation.id.value in syncedConversationIds,
                            generating = conversation.id in state.runningConversationIds,
                            unread = notificationReminderSettings.unreadConversationIndicatorsEnabled && conversation.id in state.unreadConversationIds,
                            watchLater = conversation.id in state.watchLaterAtEpochMs,
                            onSelect = { id -> if (!dismissRevealedConversation()) { onClearWatchLater(id); onSelect(id) } },
                            onRequestConversationActions = { target, anchor ->
                                if (!dismissRevealedConversation()) onRequestConversationActions(target, anchor, cloudListVisible)
                            },
                            onSwipeAction = onConversationSwipeAction,
                            revealed = revealedConversationId == conversation.id.value,
                            onRevealChanged = { opened -> revealedConversationId = conversation.id.value.takeIf { opened } },
                            batchEditing = batchEditing,
                            batchSelected = conversation.id.value in selectedBatchConversationIds,
                            onBatchSelectionChanged = { checked ->
                                selectedBatchConversationIds = selectedBatchConversationIds.toMutableSet().apply {
                                    if (checked) add(conversation.id.value) else remove(conversation.id.value)
                                }
                            },
                        )
                    }
                }
                item(key = "drawer-recent-header", contentType = "section-header") {
                    Text("最近", style = MaterialTheme.typography.labelSmall, color = SecondaryText)
                }
                items(
                    items = content,
                    key = { "recent:${it.id.value}" },
                    contentType = { "conversation-row" },
                ) { conversation ->
                    ConversationNavigationRow(
                        conversation = conversation,
                        selected = conversation.id == state.selectedConversationId,
                        presentationPinned = if (cloudListVisible) conversation.id.value in cloudPinnedConversationIds else conversation.pinnedAt != null,
                        syncedToCloud = !cloudListVisible && conversation.id.value in syncedConversationIds,
                        generating = conversation.id in state.runningConversationIds,
                        unread = notificationReminderSettings.unreadConversationIndicatorsEnabled && conversation.id in state.unreadConversationIds,
                        watchLater = conversation.id in state.watchLaterAtEpochMs,
                        onSelect = { id -> if (!dismissRevealedConversation()) { onClearWatchLater(id); onSelect(id) } },
                        onRequestConversationActions = { target, anchor ->
                            if (!dismissRevealedConversation()) onRequestConversationActions(target, anchor, cloudListVisible)
                        },
                        onSwipeAction = onConversationSwipeAction,
                        revealed = revealedConversationId == conversation.id.value,
                        onRevealChanged = { opened -> revealedConversationId = conversation.id.value.takeIf { opened } },
                        batchEditing = batchEditing,
                        batchSelected = conversation.id.value in selectedBatchConversationIds,
                        onBatchSelectionChanged = { checked ->
                            selectedBatchConversationIds = selectedBatchConversationIds.toMutableSet().apply {
                                if (checked) add(conversation.id.value) else remove(conversation.id.value)
                            }
                        },
                    )
                }
                item(key = "drawer-end-space", contentType = "spacer") {
                    Spacer(Modifier.height(drawerScrollableEndInset))
                }
            }
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .zIndex(1f)
                    .statusBarsPadding()
                    .padding(start = 5.dp, end = 5.dp, top = 16.dp)
                    .clickable(
                        enabled = revealedConversationId != null,
                        interactionSource = drawerDismissInteractionSource,
                        indication = null,
                        onClick = { dismissRevealedConversation() },
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier.size(drawerIdentityVisualSize).clip(RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    // Android's adaptive launcher mask crops the outer safe zone. Apply the
                    // same optical crop here so the 85% master has one visible subject size
                    // in the drawer and on the launcher, without changing either hit target.
                    Image(
                        painter = painterResource(R.drawable.nanfeng_ai_icon_foreground_image),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = 1.24f, scaleY = 1.24f),
                    )
                }
                Text(
                    "南枫 AI",
                    fontSize = scaledAppTextUnit(22.sp),
                    lineHeight = drawerIdentityTitleLineHeight,
                    fontFamily = DrawerIdentityRoundedFontFamily,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (batchEditing) {
                ConversationBatchEditControls(
                    selectedCount = selectedBatchConversationIds.size,
                    allSelected = allBatchSelected,
                    onSelectAll = {
                        selectedBatchConversationIds = if (allBatchSelected) emptySet() else batchCandidateIds
                    },
                    onDelete = {
                        pendingBatchDelete = batchCandidates.filter { it.id.value in selectedBatchConversationIds }
                    },
                    onDone = {
                        batchEditing = false
                        selectedBatchConversationIds = emptySet()
                    },
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 72.dp),
                )
            }
            Row(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    color = ForegroundSurface,
                    shape = P5AInteractiveShape,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    modifier = Modifier
                        .width(ConversationDrawerSettingsPillWidth)
                        .height(48.dp)
                        .conversationForegroundShadow(shape = P5AInteractiveShape),
                ) {
                    IconButton(onClick = { if (!dismissRevealedConversation()) onOpenRoute(P5ARoute.SETTINGS) }, modifier = Modifier.fillMaxSize()) {
                        Icon(Icons.Rounded.Settings, contentDescription = "设置", tint = BodyText)
                    }
                }
                Button(
                    onClick = { if (!dismissRevealedConversation()) onCreate() },
                    shape = CircleShape,
                    modifier = Modifier
                        .height(44.dp)
                        .conversationForegroundShadow(shape = CircleShape),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentOrange, contentColor = Color.White),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_lucide_file_pen), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("新对话")
                }
            }
        }
        if (pendingBatchDelete.isNotEmpty()) {
            val batchCount = pendingBatchDelete.size
            AlertDialog(
                onDismissRequest = { pendingBatchDelete = emptyList() },
                containerColor = ForegroundSurface,
                shape = RoundedCornerShape(24.dp),
                title = { Text("移入回收站？") },
                text = { Text("$batchCount 个会话将移入回收站，可恢复。") },
                confirmButton = {
                    Button(onClick = {
                        onBatchSoftDelete(pendingBatchDelete)
                        pendingBatchDelete = emptyList()
                        batchEditing = false
                        selectedBatchConversationIds = emptySet()
                    }, shape = RoundedCornerShape(14.dp)) { Text("移入回收站") }
                },
                dismissButton = { TextButton(onClick = { pendingBatchDelete = emptyList() }, shape = RoundedCornerShape(14.dp)) { Text("取消") } },
            )
        }
    }
}

@Composable
private fun DrawerListModeButton(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(50)
    Surface(
        onClick = onClick,
        color = if (selected) AccentOrange else Color.Transparent,
        contentColor = if (selected) ForegroundSurface else SecondaryText,
        shape = shape,
        modifier = modifier.fillMaxHeight().clip(shape),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(label, fontSize = scaledConversationTextUnit(12.sp), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DrawerNavigationListControls(
    cloudListVisible: Boolean,
    batchEditing: Boolean,
    batchEnabled: Boolean,
    onShowLocal: () -> Unit,
    onShowCloud: () -> Unit,
    onToggleBatch: () -> Unit,
    onReadCloud: () -> Unit,
) {
    // The whole control strip is compact. The central mode pill takes one third of
    // the drawer; the two circular utilities share that same compact scale instead
    // of leaving the pen/cloud actions visually oversized beside it.
    val modeShape = RoundedCornerShape(50)
    Box(Modifier.fillMaxWidth().height(30.dp)) {
        Surface(
            color = ForegroundSurface,
            shape = modeShape,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = Modifier.align(Alignment.Center).fillMaxWidth(0.3333f).height(26.dp).conversationForegroundShadow(modeShape),
        ) {
            Row(Modifier.fillMaxSize().padding(2.dp), verticalAlignment = Alignment.CenterVertically) {
                DrawerListModeButton("本地", selected = !cloudListVisible, modifier = Modifier.weight(1f), onClick = onShowLocal)
                DrawerListModeButton("云端", selected = cloudListVisible, modifier = Modifier.weight(1f), onClick = onShowCloud)
            }
        }
        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DrawerRoundIconButton(
                icon = null,
                contentDescription = if (batchEditing) "退出批量编辑" else "批量编辑对话",
                // Keep this utility visually identical to cloud-read: both use
                // the same white circle and a full-strength dark glyph. Only
                // the active editing state becomes orange.
                tint = if (batchEditing) AccentOrange else BodyText,
                enabled = batchEnabled,
                onClick = onToggleBatch,
            )
            DrawerRoundIconButton(
                icon = Icons.Rounded.CloudDownload,
                contentDescription = "读取云端列表",
                tint = BodyText,
                onClick = onReadCloud,
            )
        }
    }
}

@Composable
private fun DrawerRoundIconButton(
    icon: ImageVector?,
    contentDescription: String,
    tint: Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier.size(36.dp),
        contentAlignment = Alignment.Center,
    ) {
    Surface(
        color = ForegroundSurface,
        contentColor = tint,
        shape = CircleShape,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        // Material's clickable Surface applies a disabled alpha to its whole
        // container, which made the batch control look like a different gray
        // circle. Keep the shared surface opaque; disable only interaction.
        modifier = Modifier
            .size(26.dp)
            .conversationForegroundShadow(CircleShape)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (icon == null) {
                NanfengBatchEditIcon(
                    tint = tint,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(scaledAppIconSize(13.dp)),
                )
            } else {
                Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(scaledAppIconSize(13.dp)))
            }
        }
    }
    }
}

@Composable
private fun ConversationBatchEditControls(
    selectedCount: Int,
    allSelected: Boolean,
    onSelectAll: () -> Unit,
    onDelete: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = modifier.conversationForegroundShadow(shape = RoundedCornerShape(18.dp)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(50.dp).padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ConversationBatchEditAction(
                icon = Icons.Rounded.SelectAll,
                label = if (allSelected) "取消全选" else "全选",
                color = BodyText,
                onClick = onSelectAll,
                iconSize = 16.dp,
                modifier = Modifier.weight(1f),
            )
            ConversationBatchEditAction(
                icon = Icons.Rounded.DeleteOutline,
                label = "删除 $selectedCount",
                color = ErrorRed,
                enabled = selectedCount > 0,
                onClick = onDelete,
                modifier = Modifier.weight(1f),
            )
            ConversationBatchEditAction(
                icon = Icons.Rounded.Check,
                label = "完成",
                color = BrandGreen,
                onClick = onDone,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ConversationBatchEditAction(
    icon: ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    iconSize: androidx.compose.ui.unit.Dp = 18.dp,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        color = Color.Transparent,
        contentColor = if (enabled) color else SecondaryText,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxHeight(),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(iconSize))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Work is organized by persisted local Project membership, never by the normal-chat list. */
@Composable
private fun WorkProjectNavigationDrawer(
    projects: List<ProjectSnapshot>,
    conversations: List<com.nanzhufeng.ai.domain.Conversation>,
    selectedConversationId: com.nanzhufeng.ai.domain.ConversationId?,
    watchLaterAtEpochMs: Map<com.nanzhufeng.ai.domain.ConversationId, Long>,
    onSelect: (com.nanzhufeng.ai.domain.ConversationId) -> Unit,
    onConversationActions: (com.nanzhufeng.ai.domain.Conversation, androidx.compose.ui.geometry.Rect) -> Unit,
    onConversationSwipeAction: (com.nanzhufeng.ai.domain.Conversation, ConversationRowSwipeAction) -> Unit,
    revealedConversationId: String?,
    onRevealedConversationIdChange: (String?) -> Unit,
    onCreateProject: () -> Unit,
    onManageProjects: () -> Unit,
    onManageProject: (ProjectId) -> Unit,
    onPinProject: (ProjectId, Boolean) -> Unit,
    onArchiveProject: (ProjectId, Boolean) -> Unit,
    onCreateWorkConversation: (ProjectId) -> Unit,
    onOpenSettings: () -> Unit,
) {
    var sidebarMenuVisible by remember { mutableStateOf(false) }
    val activeProjectIds = projects.map { it.project.id.value }.toSet()
    fun prioritizeWatchLater(items: List<com.nanzhufeng.ai.domain.Conversation>) = items.sortedWith(
        compareByDescending<com.nanzhufeng.ai.domain.Conversation> { it.id in watchLaterAtEpochMs }
            .thenByDescending { watchLaterAtEpochMs[it.id] ?: Long.MIN_VALUE },
    )
    val ungrouped = prioritizeWatchLater(conversations.filter { it.projectId !in activeProjectIds })
    Box(Modifier.fillMaxSize().background(ConversationDrawerBaseSurface)) {
        Column(
            // Match the normal-chat drawer: the last work conversation must be able
            // to scroll above the floating settings action instead of stopping under it.
            modifier = Modifier.fillMaxSize().statusBarsPadding().padding(start = 12.dp, end = 12.dp, top = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("项目", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Box {
                    IconButton(onClick = { sidebarMenuVisible = true }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "项目列表菜单")
                    }
                    DropdownMenu(
                        expanded = sidebarMenuVisible,
                        onDismissRequest = { sidebarMenuVisible = false },
                        containerColor = ForegroundSurface,
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        DropdownMenuItem(
                            text = { Text("管理项目") },
                            onClick = { sidebarMenuVisible = false; onManageProjects() },
                        )
                    }
                }
                IconButton(onClick = onCreateProject, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Rounded.Add, contentDescription = "创建项目")
                }
            }
            if (projects.isEmpty()) {
                Text("还没有项目。创建项目后，可在项目内新建独立工作对话。", color = SecondaryText, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp))
            }
            projects.forEach { project ->
                val projectConversations = prioritizeWatchLater(conversations.filter { it.projectId == project.project.id.value })
                WorkProjectFolder(
                    project = project,
                    conversations = projectConversations,
                    selectedConversationId = selectedConversationId,
                    watchLaterAtEpochMs = watchLaterAtEpochMs,
                    onSelect = onSelect,
                    onConversationActions = onConversationActions,
                    onConversationSwipeAction = onConversationSwipeAction,
                    revealedConversationId = revealedConversationId,
                    onRevealedConversationIdChange = onRevealedConversationIdChange,
                    onManage = onManageProject,
                    onPin = onPinProject,
                    onArchive = onArchiveProject,
                    onCreateWorkConversation = onCreateWorkConversation,
                )
            }
            if (ungrouped.isNotEmpty()) {
                Text("未归入项目或项目已归档", modifier = Modifier.padding(start = 4.dp, top = 10.dp), style = MaterialTheme.typography.labelSmall, color = SecondaryText)
                ungrouped.forEach { conversation ->
                    ConversationNavigationRow(
                        conversation = conversation,
                        selected = conversation.id == selectedConversationId,
                        watchLater = conversation.id in watchLaterAtEpochMs,
                        onSelect = onSelect,
                        onRequestConversationActions = onConversationActions,
                        onSwipeAction = onConversationSwipeAction,
                        revealed = revealedConversationId == conversation.id.value,
                        onRevealChanged = { opened -> onRevealedConversationIdChange(conversation.id.value.takeIf { opened }) },
                    )
                }
            }
            Spacer(Modifier.height(96.dp))
        }
        Surface(
            color = ForegroundSurface,
            shape = P5AInteractiveShape,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 24.dp, bottom = 12.dp)
                .width(ConversationDrawerSettingsPillWidth)
                .height(48.dp)
                .conversationForegroundShadow(shape = P5AInteractiveShape),
        ) {
            IconButton(onClick = onOpenSettings, modifier = Modifier.fillMaxSize()) {
                Icon(Icons.Rounded.Settings, contentDescription = "设置", tint = BodyText)
            }
        }
    }
}

@Composable
private fun WorkProjectFolder(
    project: ProjectSnapshot,
    conversations: List<com.nanzhufeng.ai.domain.Conversation>,
    selectedConversationId: com.nanzhufeng.ai.domain.ConversationId?,
    watchLaterAtEpochMs: Map<com.nanzhufeng.ai.domain.ConversationId, Long>,
    onSelect: (com.nanzhufeng.ai.domain.ConversationId) -> Unit,
    onConversationActions: (com.nanzhufeng.ai.domain.Conversation, androidx.compose.ui.geometry.Rect) -> Unit,
    onConversationSwipeAction: (com.nanzhufeng.ai.domain.Conversation, ConversationRowSwipeAction) -> Unit,
    revealedConversationId: String?,
    onRevealedConversationIdChange: (String?) -> Unit,
    onManage: (ProjectId) -> Unit,
    onPin: (ProjectId, Boolean) -> Unit,
    onArchive: (ProjectId, Boolean) -> Unit,
    onCreateWorkConversation: (ProjectId) -> Unit,
) {
    var menuVisible by remember(project.project.id) { mutableStateOf(false) }
    val isPinned = project.project.pinnedAt != null
    Surface(
        color = if (conversations.any { it.id == selectedConversationId }) AccentOrangeSoft else Color.Transparent,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(vertical = 2.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(start = 8.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Folder, contentDescription = null, tint = SecondaryText, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(project.project.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text("${conversations.size} 个工作对话", style = MaterialTheme.typography.labelSmall, color = SecondaryText)
                }
                IconButton(onClick = { onCreateWorkConversation(project.project.id) }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Rounded.Add, contentDescription = "在${project.project.title}中新建工作对话", modifier = Modifier.size(19.dp))
                }
                Box {
                    IconButton(onClick = { menuVisible = true }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "${project.project.title}菜单", modifier = Modifier.size(19.dp))
                    }
                    DropdownMenu(
                        expanded = menuVisible,
                        onDismissRequest = { menuVisible = false },
                        containerColor = ForegroundSurface,
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        DropdownMenuItem(text = { Text(if (isPinned) "取消置顶项目" else "置顶项目") }, onClick = {
                            menuVisible = false
                            onPin(project.project.id, !isPinned)
                        })
                        DropdownMenuItem(text = { Text("编辑项目") }, onClick = {
                            menuVisible = false
                            onManage(project.project.id)
                        })
                        DropdownMenuItem(text = { Text("归档项目") }, onClick = {
                            menuVisible = false
                            onArchive(project.project.id, true)
                        })
                    }
                }
            }
            if (conversations.isEmpty()) {
                Text("在此项目中创建第一条工作对话", modifier = Modifier.padding(start = 34.dp, bottom = 6.dp), style = MaterialTheme.typography.labelSmall, color = SecondaryText)
            } else conversations.forEach { conversation ->
                ConversationNavigationRow(
                    conversation = conversation,
                    selected = conversation.id == selectedConversationId,
                    watchLater = conversation.id in watchLaterAtEpochMs,
                    onSelect = onSelect,
                    onRequestConversationActions = onConversationActions,
                    onSwipeAction = onConversationSwipeAction,
                    revealed = revealedConversationId == conversation.id.value,
                    onRevealChanged = { opened -> onRevealedConversationIdChange(conversation.id.value.takeIf { opened }) },
                )
            }
        }
    }
}

/** TEMP is one isolated recovery record, so it must never render normal or work histories. */
@Composable
private fun TemporaryConversationNavigationDrawer(onExit: () -> Unit, onOpenSettings: () -> Unit) {
    Box(Modifier.fillMaxSize().background(ConversationDrawerBaseSurface)) {
        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("临时对话", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Surface(color = NeutralSystemSurface, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("当前临时会话", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text("与普通对话、项目工作区独立；只在本机短暂恢复。", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
                }
            }
            OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text("返回原来的对话区") }
            Spacer(Modifier.height(84.dp))
        }
        Surface(
            color = ForegroundSurface,
            shape = P5AInteractiveShape,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 24.dp, bottom = 12.dp)
                .width(ConversationDrawerSettingsPillWidth)
                .height(48.dp)
                .conversationForegroundShadow(shape = P5AInteractiveShape),
        ) {
            IconButton(onClick = onOpenSettings, modifier = Modifier.fillMaxSize()) {
                Icon(Icons.Rounded.Settings, contentDescription = "设置", tint = BodyText)
            }
        }
    }
}

/**
 * Settings owns the archived/deleted projections. The drawer deliberately has
 * no alternate lifecycle scopes, while every mutation still goes through the
 * existing [ConversationManagementAction] owner supplied by the view model.
 */
@Composable
internal fun ConversationManagementSettingsCard(
    state: ConversationFoundationUiState,
    onOpenFavorites: () -> Unit,
    onOpenArchived: () -> Unit,
    onOpenRecycleBin: () -> Unit,
) {
    DataStorageGroupedCard {
        ConversationLifecycleEntry(
            icon = Icons.Rounded.Bookmark,
            title = "收藏",
            summary = "查看并管理已收藏的本地对话。",
            onClick = onOpenFavorites,
        )
        DataStorageGroupedDivider()
        ConversationLifecycleEntry(
            icon = Icons.Rounded.Archive,
            title = "已归档",
            summary = "查看并恢复暂时收起的会话；归档不会删除消息或附件。",
            onClick = onOpenArchived,
        )
        DataStorageGroupedDivider()
        ConversationLifecycleEntry(
            icon = Icons.Rounded.RestoreFromTrash,
            title = "回收站",
            summary = "查看并恢复已移入回收站的会话；消息树仍保留在本机。",
            onClick = onOpenRecycleBin,
        )
    }
}

@Composable
internal fun FavoriteConversationListSettingsCard(
    state: ConversationFoundationUiState,
    onUnfavorite: (com.nanzhufeng.ai.domain.Conversation) -> Unit,
    onOpenConversation: (com.nanzhufeng.ai.domain.Conversation) -> Unit,
) {
    var revealedConversationId by remember { mutableStateOf<String?>(null) }
    val favoriteDismissInteractionSource = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 640.dp)
            .combinedClickable(
                enabled = revealedConversationId != null,
                interactionSource = favoriteDismissInteractionSource,
                indication = null,
                onClick = { revealedConversationId = null },
            ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("收藏的会话保存在本机；取消收藏不会删除消息或附件。", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
        if (state.listScope != ConversationListScope.FAVORITES) {
            Text("正在读取收藏…", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
        } else if (state.conversations.isEmpty()) {
            Text("暂无收藏会话。", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
        } else {
            state.conversations.forEach { conversation ->
                FavoriteConversationSwipeRow(
                    conversation = conversation,
                    revealed = revealedConversationId == conversation.id.value,
                    onRevealChanged = { opened -> revealedConversationId = conversation.id.value.takeIf { opened } },
                    onOpenConversation = {
                        if (revealedConversationId != null) revealedConversationId = null else onOpenConversation(conversation)
                    },
                    onUnfavorite = {
                        revealedConversationId = null
                        onUnfavorite(conversation)
                    },
                )
            }
        }
    }
}

@Composable
private fun FavoriteConversationSwipeRow(
    conversation: com.nanzhufeng.ai.domain.Conversation,
    revealed: Boolean,
    onRevealChanged: (Boolean) -> Unit,
    onOpenConversation: () -> Unit,
    onUnfavorite: () -> Unit,
) {
    val rowHeight = 58.dp
    val revealWidth = 72.dp
    val density = LocalDensity.current
    val revealWidthPx = with(density) { revealWidth.toPx() }
    var dragOffsetPx by remember(conversation.id) { mutableStateOf(0f) }
    var dragging by remember(conversation.id) { mutableStateOf(false) }
    val rowInteractionSource = remember { MutableInteractionSource() }
    LaunchedEffect(revealed, revealWidthPx, dragging) {
        if (!dragging) dragOffsetPx = if (revealed) -revealWidthPx else 0f
    }
    val translatedPx by animateFloatAsState(
        targetValue = if (dragging) dragOffsetPx else if (revealed) -revealWidthPx else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "favoriteConversationSwipeOffset",
    )
    val rowSurfaceShape = if (translatedPx < 0f) {
        RoundedCornerShape(topStart = 18.dp, topEnd = 0.dp, bottomEnd = 0.dp, bottomStart = 18.dp)
    } else {
        RoundedCornerShape(18.dp)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(rowHeight)
            .clip(RoundedCornerShape(18.dp))
            .background(ForegroundSurface),
    ) {
        if (translatedPx < 0f) {
            ConversationLifecycleSwipeAction(
                icon = Icons.Rounded.Bookmark,
                label = "取消收藏",
                color = Color(0xFFFFF3E8),
                contentColor = AccentOrange,
                onClick = onUnfavorite,
                modifier = Modifier.align(Alignment.CenterEnd).width(revealWidth),
            )
        }
        Surface(
            color = ForegroundSurface,
            contentColor = BodyText,
            shape = rowSurfaceShape,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationX = translatedPx }
                .clip(rowSurfaceShape)
                .draggable(
                    state = rememberDraggableState { delta ->
                        dragging = true
                        dragOffsetPx = (dragOffsetPx + delta).coerceIn(-revealWidthPx, 0f)
                    },
                    orientation = Orientation.Horizontal,
                    startDragImmediately = revealed,
                    onDragStarted = {
                        dragging = true
                        dragOffsetPx = if (revealed) -revealWidthPx else 0f
                    },
                    onDragStopped = {
                        dragging = false
                        onRevealChanged(dragOffsetPx <= -revealWidthPx * 0.42f)
                    },
                )
                .combinedClickable(
                    interactionSource = rowInteractionSource,
                    indication = null,
                    onClick = {
                        if (revealed) onRevealChanged(false) else onOpenConversation()
                    },
                ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Rounded.Bookmark, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(20.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(conversation.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("更新于 ${formatLifecycleTime(conversation.updatedAt)}", style = MaterialTheme.typography.labelSmall, color = SecondaryText)
                }
            }
        }
    }
}

@Composable
private fun ConversationLifecycleEntry(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    summary: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = ForegroundSurface,
        shape = RectangleShape,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(color = AccentOrangeSoft, shape = RoundedCornerShape(14.dp), modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(22.dp))
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = SecondaryText)
            }
        }
    }
}

@Composable
internal fun ConversationLifecycleListSettingsCard(
    state: ConversationFoundationUiState,
    scope: ConversationListScope,
    onManage: (com.nanzhufeng.ai.domain.Conversation, ConversationManagementAction) -> Unit,
    onPermanentlyDelete: (com.nanzhufeng.ai.domain.Conversation) -> Unit,
    onClearArchived: (List<com.nanzhufeng.ai.domain.Conversation>) -> Unit,
    onClearRecycleBin: (List<com.nanzhufeng.ai.domain.Conversation>) -> Unit,
    onOpenConversation: (com.nanzhufeng.ai.domain.Conversation) -> Unit,
) {
    val isRecycleBin = scope == ConversationListScope.DELETED
    val title = if (isRecycleBin) "回收站" else "已归档"
    var pendingDelete by remember { mutableStateOf<com.nanzhufeng.ai.domain.Conversation?>(null) }
    var pendingPermanentDelete by remember { mutableStateOf<com.nanzhufeng.ai.domain.Conversation?>(null) }
    var pendingBulkCleanup by remember { mutableStateOf(false) }
    var revealedConversationId by remember { mutableStateOf<String?>(null) }
    val lifecycleDismissInteractionSource = remember { MutableInteractionSource() }
    val summary = if (isRecycleBin) {
        "会话消息树尚未物理删除；恢复后会回到普通对话列表。"
    } else {
        "归档会话不会出现在日常列表；恢复后会回到普通对话列表。"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 640.dp)
            // A revealed lifecycle ribbon is contextual only.  Tapping any list gap
            // or another non-action row closes it; only the visible action ribbon
            // itself is allowed to restore or delete.
            .combinedClickable(
                enabled = revealedConversationId != null,
                interactionSource = lifecycleDismissInteractionSource,
                indication = null,
                onClick = { revealedConversationId = null },
            ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(summary, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = SecondaryText)
            if (state.listScope == scope && state.conversations.isNotEmpty()) {
                Spacer(Modifier.width(10.dp))
                OutlinedButton(
                    onClick = { pendingBulkCleanup = true },
                    modifier = Modifier.height(36.dp),
                    shape = P5AInteractiveShape,
                    border = null,
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = ForegroundSurface, contentColor = if (isRecycleBin) ErrorRed else BodyText),
                ) { Text(if (isRecycleBin) "清空回收站" else "清空已归档", style = MaterialTheme.typography.labelSmall) }
            }
        }
        if (state.listScope != scope) {
            Text("正在读取$title…", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
        } else if (state.conversations.isEmpty()) {
            Text("暂无${title}会话。", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
        } else {
            state.conversations.forEach { conversation ->
                ConversationLifecycleSwipeRow(
                    conversation = conversation,
                    revealed = revealedConversationId == conversation.id.value,
                    onRevealChanged = { opened -> revealedConversationId = conversation.id.value.takeIf { opened } },
                    onRestore = {
                        revealedConversationId = null
                        onManage(conversation, if (isRecycleBin) ConversationManagementAction.RESTORE_DELETED else ConversationManagementAction.UNARCHIVE)
                    },
                    onDelete = {
                        revealedConversationId = null
                        if (isRecycleBin) pendingPermanentDelete = conversation else pendingDelete = conversation
                    },
                    onOpenConversation = {
                        if (revealedConversationId != null) revealedConversationId = null else onOpenConversation(conversation)
                    },
                )
            }
        }
    }
    pendingDelete?.let { conversation ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = ForegroundSurface,
            shape = RoundedCornerShape(24.dp),
            title = { Text("移入回收站？") },
            text = { Text("“${conversation.title}”将移入回收站，可恢复。") },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } },
            confirmButton = {
                Button(onClick = {
                    onManage(conversation, ConversationManagementAction.SOFT_DELETE)
                    pendingDelete = null
                }) { Text("移入回收站") }
            },
        )
    }
    pendingPermanentDelete?.let { conversation ->
        AlertDialog(
            onDismissRequest = { pendingPermanentDelete = null },
            containerColor = ForegroundSurface,
            shape = RoundedCornerShape(24.dp),
            title = { Text("永久删除？") },
            text = { Text("将永久删除“${conversation.title}”，无法恢复。") },
            dismissButton = { TextButton(onClick = { pendingPermanentDelete = null }) { Text("取消") } },
            confirmButton = {
                Button(
                    onClick = {
                        onPermanentlyDelete(conversation)
                        pendingPermanentDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                ) { Text("永久删除") }
            },
        )
    }
    if (pendingBulkCleanup) {
        AlertDialog(
            onDismissRequest = { pendingBulkCleanup = false },
            containerColor = ForegroundSurface,
            shape = RoundedCornerShape(24.dp),
            title = { Text(if (isRecycleBin) "清空回收站？" else "清空已归档？") },
            text = {
                if (isRecycleBin) {
                    Text("将永久删除 ${state.conversations.size} 个会话，无法恢复。")
                } else {
                    Text("${state.conversations.size} 个会话将移入回收站，可恢复。")
                }
            },
            dismissButton = { TextButton(onClick = { pendingBulkCleanup = false }) { Text("取消") } },
            confirmButton = {
                Button(
                    onClick = {
                        if (isRecycleBin) onClearRecycleBin(state.conversations) else onClearArchived(state.conversations)
                        pendingBulkCleanup = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = if (isRecycleBin) ErrorRed else ForegroundSurface, contentColor = if (isRecycleBin) Color.White else BodyText),
                    shape = P5AInteractiveShape,
                ) { Text(if (isRecycleBin) "永久删除" else "移入回收站") }
            },
        )
    }
}

@Composable
private fun ConversationLifecycleSwipeRow(
    conversation: com.nanzhufeng.ai.domain.Conversation,
    revealed: Boolean,
    onRevealChanged: (Boolean) -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    onOpenConversation: () -> Unit,
) {
    val rowHeight = 58.dp
    val revealWidth = 124.dp
    val density = LocalDensity.current
    val revealWidthPx = with(density) { revealWidth.toPx() }
    var dragOffsetPx by remember(conversation.id) { mutableStateOf(0f) }
    var dragging by remember(conversation.id) { mutableStateOf(false) }
    val closeActionsInteractionSource = remember { MutableInteractionSource() }
    LaunchedEffect(revealed, revealWidthPx, dragging) {
        if (!dragging) dragOffsetPx = if (revealed) -revealWidthPx else 0f
    }
    val translatedPx by animateFloatAsState(
        targetValue = if (dragging) dragOffsetPx else if (revealed) -revealWidthPx else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "conversationLifecycleSwipeOffset",
    )
    val rowSurfaceShape = if (translatedPx < 0f) {
        RoundedCornerShape(topStart = 18.dp, topEnd = 0.dp, bottomEnd = 0.dp, bottomStart = 18.dp)
    } else {
        RoundedCornerShape(18.dp)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(rowHeight)
            .clip(RoundedCornerShape(18.dp))
            .background(ForegroundSurface),
    ) {
        if (translatedPx < 0f) {
            ConversationLifecycleSwipeActions(
                onRestore = onRestore,
                onDelete = onDelete,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
        Surface(
            color = ForegroundSurface,
            contentColor = BodyText,
            shape = rowSurfaceShape,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationX = translatedPx }
                .clip(rowSurfaceShape)
                .draggable(
                    state = rememberDraggableState { delta ->
                        dragging = true
                        dragOffsetPx = (dragOffsetPx + delta).coerceIn(-revealWidthPx, 0f)
                    },
                    orientation = Orientation.Horizontal,
                    startDragImmediately = revealed,
                    onDragStarted = {
                        dragging = true
                        dragOffsetPx = if (revealed) -revealWidthPx else 0f
                    },
                    onDragStopped = {
                        dragging = false
                        onRevealChanged(dragOffsetPx <= -revealWidthPx * 0.42f)
                    },
                )
                .combinedClickable(
                    interactionSource = closeActionsInteractionSource,
                    indication = null,
                    onClick = {
                        if (revealed) onRevealChanged(false) else onOpenConversation()
                    },
                ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
            ) {
                Text(
                    conversation.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "创建于 ${formatLifecycleTime(conversation.createdAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = SecondaryText,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ConversationLifecycleSwipeActions(
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.width(124.dp).fillMaxHeight()) {
        ConversationLifecycleSwipeAction(
            icon = Icons.Rounded.RestoreFromTrash,
            label = "恢复",
            color = Color(0xFFF0F4F1),
            contentColor = BrandGreen,
            onClick = onRestore,
            modifier = Modifier.weight(1f),
        )
        ConversationLifecycleSwipeAction(
            icon = Icons.Rounded.DeleteOutline,
            label = "删除",
            color = Color(0xFFFFEEEE),
            contentColor = ErrorRed,
            onClick = onDelete,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ConversationLifecycleSwipeAction(
    icon: ImageVector,
    label: String,
    color: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        color = color,
        contentColor = contentColor,
        shape = RectangleShape,
        modifier = modifier.fillMaxHeight(),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(16.dp))
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun formatLifecycleTime(value: java.time.Instant): String =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault()).format(value)

@Composable
private fun ConversationSelector(
    state: ConversationFoundationUiState, onSelect: (com.nanzhufeng.ai.domain.ConversationId) -> Unit, onCreate: () -> Unit,
    onScope: (ConversationListScope) -> Unit, onSearch: (String) -> Unit, onSearchRequested: () -> Unit, onOpenHit: (com.nanzhufeng.ai.domain.ConversationSearchHit) -> Unit,
    onManage: (com.nanzhufeng.ai.domain.Conversation, ConversationManagementAction, String?) -> Unit,
    workMode: Boolean,
    onRequestRename: (com.nanzhufeng.ai.domain.Conversation) -> Unit,
    onRequestProject: (com.nanzhufeng.ai.domain.Conversation) -> Unit,
    onRequestDelete: (com.nanzhufeng.ai.domain.Conversation) -> Unit,
    onRequestConversationActions: (com.nanzhufeng.ai.domain.Conversation, androidx.compose.ui.geometry.Rect) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton(onClick = { onScope(ConversationListScope.ACTIVE) }, shape = RoundedCornerShape(14.dp)) { Text("最近") }
        OutlinedTextField(value = state.searchQuery, onValueChange = onSearch, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("搜索") }, supportingText = { Text("仅本机索引；TEMP、路径、URI、Key 与隐藏过程不会进入。") })
        OutlinedButton(onClick = onSearchRequested, enabled = state.searchQuery.isNotBlank(), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text("在主面板显示结果") }
        val pinned = state.conversations.filter { it.pinnedAt != null }
        val listed = state.conversations.filter { it.pinnedAt == null }
        if (pinned.isNotEmpty() && state.listScope == ConversationListScope.ACTIVE) {
            Text("已置顶", style = MaterialTheme.typography.labelSmall, color = SecondaryText)
            pinned.forEach { conversation ->
                ConversationNavigationRow(
                    conversation = conversation,
                    selected = conversation.id == state.selectedConversationId,
                    onSelect = onSelect,
                    onRequestConversationActions = onRequestConversationActions,
                    generating = conversation.id in state.runningConversationIds,
                )
            }
        }
        Text("最近", style = MaterialTheme.typography.labelSmall, color = SecondaryText)
        listed.forEach { conversation ->
            ConversationNavigationRow(
                conversation = conversation,
                selected = conversation.id == state.selectedConversationId,
                onSelect = onSelect,
                onRequestConversationActions = onRequestConversationActions,
                generating = conversation.id in state.runningConversationIds,
            )
        }
    }
}

@Composable
private fun SearchResultsMainPanel(
    state: ConversationFoundationUiState,
    onOpenHit: (com.nanzhufeng.ai.domain.ConversationSearchHit) -> Unit,
    onClose: () -> Unit,
) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("搜索结果", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold); Text("“${state.searchQuery}” · 仅本地安全索引", style = MaterialTheme.typography.bodySmall, color = SecondaryText) }
            TextButton(onClick = onClose) { Text("返回对话") }
        }
        if (state.searchResults.isEmpty()) Text("没有匹配的本地内容。", color = SecondaryText)
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.searchResults, key = { "${it.conversationId.value}:${it.messageNodeId?.value ?: "title"}" }) { hit ->
                Surface(
                    onClick = { onOpenHit(hit) },
                    color = ForegroundSurface,
                    contentColor = BodyText,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(14.dp)) { Text(hit.title, maxLines = 1); Text(hit.snippet, style = MaterialTheme.typography.bodySmall, color = SecondaryText, maxLines = 3) }
                }
            }
        }
    }
}

/**
 * A separate destination, not a drawer expansion: the source workspace remains intact beneath
 * it and the six categories all consume the same local-only search owner.
 */
private data class ConversationSearchScrollStates(
    val all: LazyListState,
    val text: LazyListState,
    val image: LazyGridState,
    val video: LazyGridState,
    val audio: LazyGridState,
    val file: LazyGridState,
) {
    fun list(category: ConversationSearchCategory): LazyListState = when (category) {
        ConversationSearchCategory.ALL -> all
        ConversationSearchCategory.TEXT -> text
        else -> error("$category does not use a search list")
    }

    fun grid(category: ConversationSearchCategory): LazyGridState = when (category) {
        ConversationSearchCategory.IMAGE -> image
        ConversationSearchCategory.VIDEO -> video
        ConversationSearchCategory.AUDIO -> audio
        ConversationSearchCategory.FILE -> file
        else -> error("$category does not use a search grid")
    }
}

@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
private fun ConversationSearchPage(
    state: ConversationFoundationUiState,
    scrollStates: ConversationSearchScrollStates,
    attachmentSortMode: ConversationAttachmentSortMode,
    attachmentFileType: ConversationAttachmentFileType,
    locateConversationId: String?,
    locateMessageId: String?,
    locateAttachmentId: String?,
    locateRequestId: Long,
    onSelectAttachmentSortMode: (ConversationAttachmentSortMode) -> Unit,
    onSelectAttachmentFileType: (ConversationAttachmentFileType) -> Unit,
    onDismiss: () -> Unit,
    onQueryChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onSelectCategory: (ConversationSearchCategory) -> Unit,
    onOpenHistory: () -> Unit,
    onCloseHistory: () -> Unit,
    onFillHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
    onOpenTextHit: (com.nanzhufeng.ai.domain.ConversationSearchHit) -> Unit,
    onOpenAttachmentHit: (ConversationAttachmentSearchHit) -> Unit,
    onOpenGlmOcrHit: (GlmOcrDocumentSearchHit) -> Unit,
    onEnsureAttachmentPreview: (ConversationAttachmentReference) -> Unit,
    onLocateAttachment: (ConversationAttachmentSearchHit, androidx.compose.ui.geometry.Rect) -> Unit,
    onLocateGlmOcr: (GlmOcrDocumentSearchHit, androidx.compose.ui.geometry.Rect) -> Unit,
) {
    val searchPlaceholder = when (state.searchCategory) {
        ConversationSearchCategory.ALL -> "搜索全部内容"
        else -> "搜索${state.searchCategory.label}"
    }
    val searchControlShape = RoundedCornerShape(50)
    val imeVisible = WindowInsets.isImeVisible
    val attachmentGridHits = remember(state.searchCategory, state.attachmentSearchResults, attachmentFileType) {
        if (state.searchCategory == ConversationSearchCategory.FILE) {
            filterSearchFileType(state.attachmentSearchResults, attachmentFileType)
        } else {
            state.attachmentSearchResults
        }
    }
    val glmOcrGridHits = remember(state.searchCategory, state.glmOcrSearchResults, attachmentFileType) {
        if (state.searchCategory == ConversationSearchCategory.FILE) {
            filterGlmOcrSearchFileType(state.glmOcrSearchResults, attachmentFileType)
        } else {
            state.glmOcrSearchResults
        }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        BackHandler(onBack = onDismiss)
        // Search owns a deliberately deeper canvas: results and file previews remain bright
        // foreground cards, while the page and its compact controls keep separate gray steps.
        Surface(color = SearchPageCanvas, contentColor = BodyText, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(start = 12.dp, end = 18.dp, top = 10.dp),
                ) {
                    IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterStart)) {
                        Icon(Icons.Rounded.Close, contentDescription = "退出搜索")
                    }
                    Text(
                        "搜索",
                        modifier = Modifier.align(Alignment.Center).semantics { heading() },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    val expandedSearchCategories = maxWidth >= 600.dp
                    Row(
                        modifier = Modifier
                            .align(if (expandedSearchCategories) Alignment.Center else Alignment.CenterStart)
                            .then(if (expandedSearchCategories) Modifier else Modifier.fillMaxWidth()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ConversationSearchCategory.entries.forEach { category ->
                            val selected = category == state.searchCategory
                            val categoryShape = RoundedCornerShape(50)
                            Surface(
                                onClick = { onSelectCategory(category) },
                                color = if (selected) ForegroundSurface else Color.Transparent,
                                shape = categoryShape,
                                tonalElevation = 0.dp,
                                shadowElevation = if (selected) 1.dp else 0.dp,
                                modifier = Modifier
                                    .then(if (expandedSearchCategories) Modifier else Modifier.weight(1f))
                            ) {
                                Text(
                                    category.label,
                                    modifier = if (expandedSearchCategories) Modifier.padding(horizontal = 14.dp, vertical = 9.dp) else Modifier.fillMaxWidth().padding(vertical = 9.dp),
                                    color = if (selected) MaterialTheme.colorScheme.primary else SecondaryText,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                )
                            }
                        }
                    }
                }
                Box(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 6.dp).height(36.dp),
                ) {
                    if (state.searchCategory == ConversationSearchCategory.FILE) {
                        SearchAttachmentFileTypeControl(
                            selected = attachmentFileType,
                            onSelect = onSelectAttachmentFileType,
                            modifier = Modifier.align(Alignment.CenterStart),
                        )
                    }
                    SearchAttachmentSortControl(
                        selected = attachmentSortMode,
                        onSelect = onSelectAttachmentSortMode,
                        modifier = Modifier.align(Alignment.CenterEnd),
                    )
                }
                HorizontalDivider(color = SubtleDivider)
                // Keep the whole usable search canvas above the IME. This makes empty-state
                // guidance stay centered in what remains visible, while the bottom controls
                // and history panel share the same coordinate space.
                Box(Modifier.weight(1f).fillMaxWidth().imePadding()) {
                    when (state.searchCategory) {
                        ConversationSearchCategory.IMAGE,
                        ConversationSearchCategory.VIDEO,
                        ConversationSearchCategory.AUDIO,
                        ConversationSearchCategory.FILE -> SearchAttachmentGrid(
                            hits = attachmentGridHits,
                            glmOcrHits = glmOcrGridHits,
                            gridState = scrollStates.grid(state.searchCategory),
                            sortMode = attachmentSortMode,
                            locateConversationId = locateConversationId,
                            locateMessageId = locateMessageId,
                            locateAttachmentId = locateAttachmentId,
                            locateRequestId = locateRequestId,
                            query = state.searchQuery,
                            previews = state.searchAttachmentPreviews,
                            textPreviews = state.searchAttachmentTextPreviews,
                            onOpen = onOpenAttachmentHit,
                            onOpenGlmOcr = onOpenGlmOcrHit,
                            onEnsurePreview = onEnsureAttachmentPreview,
                            onLocate = onLocateAttachment,
                            onLocateGlmOcr = onLocateGlmOcr,
                            bottomContentPadding = 78.dp,
                        )
                        ConversationSearchCategory.ALL, ConversationSearchCategory.TEXT -> SearchAllOrTextResults(
                            state = state,
                            listState = scrollStates.list(state.searchCategory),
                            sortMode = attachmentSortMode,
                            locateConversationId = locateConversationId,
                            locateMessageId = locateMessageId,
                            locateAttachmentId = locateAttachmentId,
                            locateRequestId = locateRequestId,
                            onOpenText = onOpenTextHit,
                            onOpenAttachment = onOpenAttachmentHit,
                            onOpenGlmOcr = onOpenGlmOcrHit,
                            onEnsureAttachmentPreview = onEnsureAttachmentPreview,
                            onLocateAttachment = onLocateAttachment,
                            onLocateGlmOcr = onLocateGlmOcr,
                            bottomContentPadding = 78.dp,
                        )
                    }
                    // Match the conversation canvas: results fade naturally beneath the
                    // bottom search and history controls. The controls remain independent
                    // surfaces above this edge-only treatment, never on a hard backing tray.
                    Box(
                        Modifier
                            .fillMaxSize()
                            .conversationEdgeGrayFade(
                                topBand = 0.dp,
                                bottomBand = 112.dp,
                                edgeColor = SearchPageCanvas,
                            ),
                    )
                    if (state.searchHistoryOpen) {
                        Box(
                            modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                                detectTapGestures(onTap = { onCloseHistory() })
                            },
                        )
                        SearchHistoryPanel(
                            history = state.searchHistory,
                            highlightedQuery = state.searchHistoryHighlightedQuery,
                            onFill = onFillHistory,
                            onClear = onClearHistory,
                            onDismiss = onCloseHistory,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(start = 16.dp, end = 16.dp, bottom = 68.dp),
                        )
                    }
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = if (imeVisible) 8.dp else 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Material TextField reserves a fixed leading-icon lane and extra
                        // padding. This compact search shell owns that geometry directly: the
                        // icon is only 18dp and the text receives the full remaining width.
                        Surface(
                            color = SearchControlSurface,
                            shape = searchControlShape,
                            modifier = Modifier.weight(1f).height(42.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize().padding(start = 10.dp, end = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(7.dp),
                            ) {
                                Icon(
                                    Icons.Rounded.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = BodyText.copy(alpha = 0.80f),
                                )
                                BasicTextField(
                                    value = state.searchQuery,
                                    onValueChange = onQueryChanged,
                                    modifier = Modifier.weight(1f).semantics { contentDescription = "搜索${state.searchCategory.label}" },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.labelLarge.copy(color = BodyText.copy(alpha = 0.80f)),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                                    decorationBox = { innerTextField ->
                                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                                            if (state.searchQuery.isBlank()) {
                                                Text(
                                                    searchPlaceholder,
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = BodyText.copy(alpha = 0.80f),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                            innerTextField()
                                        }
                                    },
                                )
                            }
                        }
                        Surface(
                            color = SearchControlSurface, shape = searchControlShape,
                            modifier = Modifier.width(152.dp).height(42.dp).clip(searchControlShape).combinedClickable(onClick = onOpenHistory),
                        ) {
                            Row(Modifier.fillMaxSize().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                                Icon(Icons.Rounded.History, contentDescription = null, modifier = Modifier.size(16.dp), tint = SecondaryText)
                                Spacer(Modifier.width(4.dp))
                                Text("历史", style = MaterialTheme.typography.labelLarge, color = BodyText)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchAttachmentSortControl(
    selected: ConversationAttachmentSortMode,
    onSelect: (ConversationAttachmentSortMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.height(36.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        SearchAttachmentSortColumn(
            label = "时间",
            selected = selected,
            column = ConversationAttachmentSortColumn.TIME,
            onSelect = onSelect,
        )
        SearchAttachmentSortColumn(
            label = "大小",
            selected = selected,
            column = ConversationAttachmentSortColumn.SIZE,
            onSelect = onSelect,
        )
        val restored = selected == ConversationAttachmentSortMode.DEFAULT
        val restoreShape = RoundedCornerShape(50)
        Surface(
            onClick = { onSelect(ConversationAttachmentSortMode.DEFAULT) },
            color = SearchControlSurface,
            contentColor = if (restored) SecondaryText else MaterialTheme.colorScheme.primary,
            shape = restoreShape,
            modifier = Modifier.width(72.dp).fillMaxHeight().semantics {
                contentDescription = if (restored) "还原，当前已按月默认排序" else "还原默认按月排序"
            },
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "还原",
                    modifier = Modifier.padding(horizontal = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun SearchAttachmentSortColumn(
    label: String,
    selected: ConversationAttachmentSortMode,
    column: ConversationAttachmentSortColumn,
    onSelect: (ConversationAttachmentSortMode) -> Unit,
) {
    val active = when (column) {
        ConversationAttachmentSortColumn.TIME -> selected in setOf(
            ConversationAttachmentSortMode.TIME_DESCENDING,
            ConversationAttachmentSortMode.TIME_ASCENDING,
        )
        ConversationAttachmentSortColumn.SIZE -> selected in setOf(
            ConversationAttachmentSortMode.SIZE_DESCENDING,
            ConversationAttachmentSortMode.SIZE_ASCENDING,
        )
    }
    val descending = selected == when (column) {
        ConversationAttachmentSortColumn.TIME -> ConversationAttachmentSortMode.TIME_DESCENDING
        ConversationAttachmentSortColumn.SIZE -> ConversationAttachmentSortMode.SIZE_DESCENDING
    }
    val direction = if (descending) "倒序" else "正序"
    val shape = RoundedCornerShape(50)
    Surface(
        onClick = { onSelect(nextAttachmentSortMode(selected, column)) },
        color = SearchControlSurface,
        contentColor = if (active) MaterialTheme.colorScheme.primary else SecondaryText,
        shape = shape,
        modifier = Modifier.width(72.dp).fillMaxHeight().semantics {
            contentDescription = if (active) "$label，当前$direction，点击切换" else "$label，点击按倒序排序"
        },
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Icon(
                imageVector = when {
                    !active -> Icons.Rounded.SwapVert
                    descending -> Icons.Rounded.KeyboardArrowDown
                    else -> Icons.Rounded.KeyboardArrowUp
                },
                contentDescription = null,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

@Composable
private fun SearchAttachmentFileTypeControl(
    selected: ConversationAttachmentFileType,
    onSelect: (ConversationAttachmentFileType) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        val shape = RoundedCornerShape(50)
        Surface(
            onClick = { expanded = true },
            color = SearchControlSurface,
            contentColor = BodyText,
            shape = shape,
            // This is a left-side pill, not a flexible row background.  Without an
            // explicit width the fillMaxSize child can consume the whole sort row,
            // merging its gray surface with the three right-side sort controls and
            // leaving the category label effectively invisible.
            modifier = Modifier.width(128.dp).height(36.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(selected.label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(14.dp))
            }
        }
        TransientMenuTextScale {
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                containerColor = ForegroundSurface,
                shape = RoundedCornerShape(18.dp),
                tonalElevation = 0.dp,
                shadowElevation = 6.dp,
                modifier = Modifier.widthIn(min = 148.dp),
            ) {
                ConversationAttachmentFileType.entries.forEach { type ->
                    val isSelected = type == selected
                    DropdownMenuItem(
                        text = {
                            Text(
                                type.label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else BodyText,
                            )
                        },
                        trailingIcon = {
                            if (isSelected) Icon(
                                Icons.Rounded.Check,
                                contentDescription = "当前类型",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        onClick = {
                            expanded = false
                            onSelect(type)
                        },
                    )
                }
            }
        }
    }
}

/** Search results share the same theme-colored, bold occurrence treatment as in-chat find. */
private fun searchHighlightedText(value: String, query: String, highlightColor: Color): AnnotatedString = buildAnnotatedString {
    append(value)
    conversationFindOccurrenceStarts(value, query).forEach { matchStart ->
        val matchEnd = (matchStart + query.trim().length).coerceAtMost(value.length)
        addStyle(
            SpanStyle(
                color = highlightColor,
                background = highlightColor.copy(alpha = 0.18f),
                fontWeight = FontWeight.Bold,
            ),
            matchStart,
            matchEnd,
        )
    }
}

@Composable
private fun SearchAllOrTextResults(
    state: ConversationFoundationUiState,
    listState: LazyListState,
    sortMode: ConversationAttachmentSortMode,
    locateConversationId: String?,
    locateMessageId: String?,
    locateAttachmentId: String?,
    locateRequestId: Long,
    onOpenText: (com.nanzhufeng.ai.domain.ConversationSearchHit) -> Unit,
    onOpenAttachment: (ConversationAttachmentSearchHit) -> Unit,
    onOpenGlmOcr: (GlmOcrDocumentSearchHit) -> Unit,
    onEnsureAttachmentPreview: (ConversationAttachmentReference) -> Unit,
    onLocateAttachment: (ConversationAttachmentSearchHit, androidx.compose.ui.geometry.Rect) -> Unit,
    onLocateGlmOcr: (GlmOcrDocumentSearchHit, androidx.compose.ui.geometry.Rect) -> Unit,
    bottomContentPadding: androidx.compose.ui.unit.Dp = 0.dp,
) {
    val textResults = remember(state.searchResults, sortMode) {
        sortedConversationSearchHits(state.searchResults, sortMode)
    }
    val attachmentMonthGroups = remember(state.attachmentSearchResults, state.glmOcrSearchResults) {
        unifiedSearchAttachmentMonthGroups(state.attachmentSearchResults, state.glmOcrSearchResults)
    }
    val sortedAttachmentEntries = remember(state.attachmentSearchResults, state.glmOcrSearchResults, sortMode) {
        sortedUnifiedSearchAttachmentEntries(state.attachmentSearchResults, state.glmOcrSearchResults, sortMode)
    }
    if (state.searchResults.isEmpty() && state.attachmentSearchResults.isEmpty() && state.glmOcrSearchResults.isEmpty()) {
        SearchHint(if (state.searchQuery.isBlank()) "暂无可浏览的本地内容" else "没有匹配的本地内容")
        return
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp + bottomContentPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (textResults.isNotEmpty()) item {
            Text(
                "正文 · ${textResults.size} 条",
                style = MaterialTheme.typography.labelLarge,
                color = SecondaryText,
            )
        }
        items(textResults, key = { "text:${it.conversationId.value}:${it.messageNodeId?.value.orEmpty()}" }) { hit ->
            val resultShape = RoundedCornerShape(14.dp)
            Surface(color = ForegroundSurface, shape = resultShape, modifier = Modifier.fillMaxWidth().clip(resultShape).combinedClickable(onClick = { onOpenText(hit) })) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(searchHighlightedText(hit.title, state.searchQuery, MaterialTheme.colorScheme.primary), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    hit.importSource?.let { source -> Text(source.searchLabel, color = AccentOrange, style = MaterialTheme.typography.labelSmall) }
                    Text(searchHighlightedText(hit.snippet, state.searchQuery, MaterialTheme.colorScheme.primary), color = SecondaryText, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(formatAttachmentBytes(hit.byteCount), style = MaterialTheme.typography.labelSmall, color = SecondaryText)
                        Spacer(Modifier.weight(1f))
                        Text(formatSearchAttachmentTimestamp(hit.timestampEpochMs), style = MaterialTheme.typography.labelSmall, color = SecondaryText)
                    }
                }
            }
        }
        if (sortMode == ConversationAttachmentSortMode.DEFAULT) {
            val totalAttachmentCount = state.attachmentSearchResults.size + state.glmOcrSearchResults.size
            if (totalAttachmentCount > 0) item { Text("附件 · $totalAttachmentCount 项", modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelLarge, color = SecondaryText) }
            attachmentMonthGroups.forEach { group ->
                item(key = "all-month:${group.key}") { SearchAttachmentMonthHeading(group.label, group.entries.size) }
                items(group.entries, key = { it.stableKey }) { entry ->
                    when (entry) {
                        is UnifiedSearchAttachmentEntry.Conversation -> SearchAttachmentRow(
                            hit = entry.hit,
                            query = state.searchQuery,
                            preview = state.searchAttachmentPreviews[entry.attachment.id],
                            textPreview = state.searchAttachmentTextPreviews[entry.attachment.id],
                            locateConversationId = locateConversationId,
                            locateMessageId = locateMessageId,
                            locateAttachmentId = locateAttachmentId,
                            locateRequestId = locateRequestId,
                            onOpen = onOpenAttachment,
                            onEnsurePreview = onEnsureAttachmentPreview,
                            onLocate = onLocateAttachment,
                        )
                        is UnifiedSearchAttachmentEntry.GlmOcr -> GlmOcrSearchAttachmentRow(
                            hit = entry.hit,
                            query = state.searchQuery,
                            preview = state.searchAttachmentPreviews[entry.attachment.id],
                            textPreview = state.searchAttachmentTextPreviews[entry.attachment.id],
                            onOpen = onOpenGlmOcr,
                            onEnsurePreview = onEnsureAttachmentPreview,
                            onLocate = onLocateGlmOcr,
                        )
                    }
                }
            }
        } else {
            item {
                Text("附件 · ${state.attachmentSearchResults.size + state.glmOcrSearchResults.size} 项", modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelLarge, color = SecondaryText)
            }
            items(
                sortedAttachmentEntries,
                key = { it.stableKey },
            ) { entry ->
                when (entry) {
                    is UnifiedSearchAttachmentEntry.Conversation -> SearchAttachmentRow(
                        hit = entry.hit,
                        query = state.searchQuery,
                        preview = state.searchAttachmentPreviews[entry.attachment.id],
                        textPreview = state.searchAttachmentTextPreviews[entry.attachment.id],
                        locateConversationId = locateConversationId,
                        locateMessageId = locateMessageId,
                        locateAttachmentId = locateAttachmentId,
                        locateRequestId = locateRequestId,
                        onOpen = onOpenAttachment,
                        onEnsurePreview = onEnsureAttachmentPreview,
                        onLocate = onLocateAttachment,
                    )
                    is UnifiedSearchAttachmentEntry.GlmOcr -> GlmOcrSearchAttachmentRow(
                        hit = entry.hit,
                        query = state.searchQuery,
                        preview = state.searchAttachmentPreviews[entry.attachment.id],
                        textPreview = state.searchAttachmentTextPreviews[entry.attachment.id],
                        onOpen = onOpenGlmOcr,
                        onEnsurePreview = onEnsureAttachmentPreview,
                        onLocate = onLocateGlmOcr,
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchAttachmentGrid(
    hits: List<ConversationAttachmentSearchHit>,
    glmOcrHits: List<GlmOcrDocumentSearchHit>,
    gridState: LazyGridState,
    sortMode: ConversationAttachmentSortMode,
    locateConversationId: String?,
    locateMessageId: String?,
    locateAttachmentId: String?,
    locateRequestId: Long,
    query: String,
    previews: Map<AttachmentId, ConversationAttachmentPreview>,
    textPreviews: Map<AttachmentId, ConversationAttachmentTextPreview>,
    onOpen: (ConversationAttachmentSearchHit) -> Unit,
    onOpenGlmOcr: (GlmOcrDocumentSearchHit) -> Unit,
    onEnsurePreview: (ConversationAttachmentReference) -> Unit,
    onLocate: (ConversationAttachmentSearchHit, androidx.compose.ui.geometry.Rect) -> Unit,
    onLocateGlmOcr: (GlmOcrDocumentSearchHit, androidx.compose.ui.geometry.Rect) -> Unit,
    bottomContentPadding: androidx.compose.ui.unit.Dp = 0.dp,
) {
    val attachmentMonthGroups = remember(hits, glmOcrHits) {
        unifiedSearchAttachmentMonthGroups(hits, glmOcrHits)
    }
    val sortedAttachmentEntries = remember(hits, glmOcrHits, sortMode) {
        sortedUnifiedSearchAttachmentEntries(hits, glmOcrHits, sortMode)
    }
    if (hits.isEmpty() && glmOcrHits.isEmpty()) { SearchHint("没有匹配的本地附件"); return }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 152.dp),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp + bottomContentPadding),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(
            key = "attachment-total",
            span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) },
        ) { Text("共 ${hits.size + glmOcrHits.size} 项", style = MaterialTheme.typography.labelLarge, color = SecondaryText) }
        if (sortMode == ConversationAttachmentSortMode.DEFAULT) {
            attachmentMonthGroups.forEach { group ->
                item(
                    key = "month:${group.key}",
                    span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) },
                ) { SearchAttachmentMonthHeading(group.label, group.entries.size) }
                gridItems(group.entries, key = { it.stableKey }) { entry ->
                    when (entry) {
                        is UnifiedSearchAttachmentEntry.Conversation -> SearchAttachmentCard(
                            hit = entry.hit,
                            query = query,
                            preview = previews[entry.attachment.id],
                            textPreview = textPreviews[entry.attachment.id],
                            locateConversationId = locateConversationId,
                            locateMessageId = locateMessageId,
                            locateAttachmentId = locateAttachmentId,
                            locateRequestId = locateRequestId,
                            onOpen = onOpen,
                            onEnsurePreview = onEnsurePreview,
                            onLocate = onLocate,
                        )
                        is UnifiedSearchAttachmentEntry.GlmOcr -> GlmOcrSearchAttachmentCard(
                            hit = entry.hit,
                            query = query,
                            preview = previews[entry.attachment.id],
                            textPreview = textPreviews[entry.attachment.id],
                            onOpen = onOpenGlmOcr,
                            onEnsurePreview = onEnsurePreview,
                            onLocate = onLocateGlmOcr,
                        )
                    }
                }
            }
        } else {
            gridItems(
                sortedAttachmentEntries,
                key = { it.stableKey },
            ) { entry ->
                when (entry) {
                    is UnifiedSearchAttachmentEntry.Conversation -> SearchAttachmentCard(
                        hit = entry.hit,
                        query = query,
                        preview = previews[entry.attachment.id],
                        textPreview = textPreviews[entry.attachment.id],
                        locateConversationId = locateConversationId,
                        locateMessageId = locateMessageId,
                        locateAttachmentId = locateAttachmentId,
                        locateRequestId = locateRequestId,
                        onOpen = onOpen,
                        onEnsurePreview = onEnsurePreview,
                        onLocate = onLocate,
                    )
                    is UnifiedSearchAttachmentEntry.GlmOcr -> GlmOcrSearchAttachmentCard(
                        hit = entry.hit,
                        query = query,
                        preview = previews[entry.attachment.id],
                        textPreview = textPreviews[entry.attachment.id],
                        onOpen = onOpenGlmOcr,
                        onEnsurePreview = onEnsurePreview,
                        onLocate = onLocateGlmOcr,
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchAttachmentMonthHeading(label: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = BodyText, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.weight(1f))
        Text(count.toString(), style = MaterialTheme.typography.labelSmall, color = SecondaryText)
    }
}

@Composable
private fun SearchAttachmentRows(
    hits: List<ConversationAttachmentSearchHit>,
    query: String,
    previews: Map<AttachmentId, ConversationAttachmentPreview>,
    textPreviews: Map<AttachmentId, ConversationAttachmentTextPreview>,
    onOpen: (ConversationAttachmentSearchHit) -> Unit,
    onEnsurePreview: (ConversationAttachmentReference) -> Unit,
    onLocate: (ConversationAttachmentSearchHit, androidx.compose.ui.geometry.Rect) -> Unit,
    bottomContentPadding: androidx.compose.ui.unit.Dp = 0.dp,
) {
    if (hits.isEmpty()) { SearchHint("没有匹配的本地附件"); return }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp + bottomContentPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(hits, key = { "${it.conversationId.value}:${it.messageNodeId.value}:${it.attachment.id.value}" }) { hit ->
            SearchAttachmentRow(
                hit = hit,
                query = query,
                preview = previews[hit.attachment.id],
                textPreview = textPreviews[hit.attachment.id],
                onOpen = onOpen,
                onEnsurePreview = onEnsurePreview,
                onLocate = onLocate,
            )
        }
    }
}

/** Bitmap decoding is CPU-heavy and must not run during a Compose measure/recomposition frame. */
@Composable
private fun rememberDecodedBitmap(bytes: ByteArray?, cacheKey: Any?): android.graphics.Bitmap? {
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, cacheKey, bytes) {
        value = withContext(Dispatchers.Default) {
            bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        }
    }
    return bitmap
}

@Composable
private fun GlmOcrSearchAttachmentCard(
    hit: GlmOcrDocumentSearchHit,
    query: String,
    preview: ConversationAttachmentPreview?,
    textPreview: ConversationAttachmentTextPreview?,
    onOpen: (GlmOcrDocumentSearchHit) -> Unit,
    onEnsurePreview: (ConversationAttachmentReference) -> Unit,
    onLocate: (GlmOcrDocumentSearchHit, androidx.compose.ui.geometry.Rect) -> Unit,
) {
    LaunchedEffect(hit.attachment.id) { onEnsurePreview(hit.attachment) }
    val bitmap = rememberDecodedBitmap(preview?.thumbnail?.bytes, preview?.id)
    val cardShape = RoundedCornerShape(14.dp)
    var cardBounds by remember(hit.attachment.id) { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    Surface(
        color = ForegroundSurface,
        shape = cardShape,
        modifier = Modifier.fillMaxWidth().height(204.dp).onGloballyPositioned { cardBounds = it.boundsInRoot() }.clip(cardShape).combinedClickable(onClick = { onOpen(hit) }, onLongClick = { onLocate(hit, cardBounds) }),
    ) {
        Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                Modifier.fillMaxWidth().height(104.dp).clip(RoundedCornerShape(10.dp))
                    .background(NeutralSystemSurface).border(1.dp, SubtleDivider.copy(alpha = 0.45f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    bitmap != null -> Image(bitmap.asImageBitmap(), contentDescription = "南枫转写文件缩略图", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                    textPreview?.text != null -> AttachmentTextSnippet(textPreview.text, hit.attachment.mimeType, Modifier.fillMaxSize())
                    else -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Icon(Icons.Rounded.Description, contentDescription = null, tint = SecondaryText, modifier = Modifier.size(30.dp))
                        Text(if (hit.attachment.mimeType == "application/pdf") "PDF" else if (hit.attachment.mimeType == "text/markdown") "Markdown" else "图片", style = MaterialTheme.typography.labelSmall, color = SecondaryText, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Text(searchHighlightedText(hit.attachment.displayName ?: "南枫转写文件", query, MaterialTheme.colorScheme.primary), minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            Text(hit.title, minLines = 1, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = SecondaryText)
            Row(Modifier.fillMaxWidth().height(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(formatAttachmentBytes(hit.attachment.byteCount), style = MaterialTheme.typography.labelSmall, color = SecondaryText)
                Spacer(Modifier.weight(1f))
                Text(formatSearchAttachmentTimestamp(hit.timestampEpochMs), style = MaterialTheme.typography.labelSmall, color = SecondaryText)
            }
        }
    }
}

@Composable
private fun GlmOcrSearchAttachmentRow(
    hit: GlmOcrDocumentSearchHit,
    query: String,
    preview: ConversationAttachmentPreview?,
    textPreview: ConversationAttachmentTextPreview?,
    onOpen: (GlmOcrDocumentSearchHit) -> Unit,
    onEnsurePreview: (ConversationAttachmentReference) -> Unit,
    onLocate: (GlmOcrDocumentSearchHit, androidx.compose.ui.geometry.Rect) -> Unit,
) {
    LaunchedEffect(hit.attachment.id) { onEnsurePreview(hit.attachment) }
    val bitmap = rememberDecodedBitmap(preview?.thumbnail?.bytes, preview?.id)
    val rowShape = RoundedCornerShape(14.dp)
    var rowBounds by remember(hit.attachment.id) { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    Surface(
        color = ForegroundSurface,
        shape = rowShape,
        modifier = Modifier.fillMaxWidth().onGloballyPositioned { rowBounds = it.boundsInRoot() }.clip(rowShape).combinedClickable(onClick = { onOpen(hit) }, onLongClick = { onLocate(hit, rowBounds) }),
    ) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = NeutralSystemSurface, shape = RoundedCornerShape(10.dp), modifier = Modifier.size(58.dp, 48.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    when {
                        bitmap != null -> Image(bitmap.asImageBitmap(), contentDescription = "南枫转写文件缩略图", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                        textPreview?.text != null -> AttachmentTextSnippet(textPreview.text, hit.attachment.mimeType, Modifier.fillMaxSize())
                        else -> Icon(Icons.Rounded.Description, contentDescription = null, tint = SecondaryText)
                    }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(searchHighlightedText(hit.attachment.displayName ?: "南枫转写文件", query, MaterialTheme.colorScheme.primary), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(hit.title, style = MaterialTheme.typography.bodySmall, color = SecondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                (hit.matchSnippet ?: textPreview?.text?.replace('\n', ' '))?.let { text ->
                    Text(searchHighlightedText(text, query, MaterialTheme.colorScheme.primary), style = MaterialTheme.typography.labelSmall, color = SecondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(formatAttachmentBytes(hit.attachment.byteCount), style = MaterialTheme.typography.labelSmall, color = SecondaryText)
                    Spacer(Modifier.weight(1f))
                    Text(formatSearchAttachmentTimestamp(hit.timestampEpochMs), style = MaterialTheme.typography.labelSmall, color = SecondaryText)
                }
            }
        }
    }
}

@Composable
private fun SearchAttachmentCard(
    hit: ConversationAttachmentSearchHit,
    query: String,
    preview: ConversationAttachmentPreview?,
    textPreview: ConversationAttachmentTextPreview?,
    locateConversationId: String?,
    locateMessageId: String?,
    locateAttachmentId: String?,
    locateRequestId: Long,
    onOpen: (ConversationAttachmentSearchHit) -> Unit,
    onEnsurePreview: (ConversationAttachmentReference) -> Unit,
    onLocate: (ConversationAttachmentSearchHit, androidx.compose.ui.geometry.Rect) -> Unit,
) {
    LaunchedEffect(hit.attachment.id) { onEnsurePreview(hit.attachment) }
    val bitmap = rememberDecodedBitmap(preview?.thumbnail?.bytes, preview?.id)
    val isVideo = hit.attachment.mimeType in com.nanzhufeng.ai.domain.CONVERSATION_ALLOWED_VIDEO_MIME_TYPES
    val isAudio = hit.attachment.mimeType in com.nanzhufeng.ai.domain.CONVERSATION_ALLOWED_AUDIO_MIME_TYPES
    val isArchive = com.nanzhufeng.ai.domain.isSafeArchiveAttachment(hit.attachment.mimeType, hit.attachment.displayName)
    val darkCatalogSurface = ForegroundSurface.red < 0.5f
    val audioCatalogSurface = catalogAudioPreviewSurface(dark = darkCatalogSurface)
    val videoPlayContainer = if (darkCatalogSurface) Color.Black.copy(alpha = 0.62f) else Color.White.copy(alpha = 0.88f)
    val videoPlayContent = if (darkCatalogSurface) Color.White.copy(alpha = 0.94f) else BodyText
    val visualLabel = when {
        isVideo -> "视频"
        isAudio -> "音频"
        isArchive -> "ZIP"
        hit.attachment.mimeType == "application/pdf" -> "PDF"
        bitmap != null -> "图片"
        else -> "文件"
    }
    // Image corners are intentionally half of video corners: visible enough to separate a
    // thumbnail from surrounding text, but quieter than the distinct video-card treatment.
    val thumbnailShape = RoundedCornerShape(if (isVideo) 20.dp else 10.dp)
    val cardShape = RoundedCornerShape(14.dp)
    var anchorBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    Surface(
        color = ForegroundSurface,
        shape = cardShape,
        modifier = Modifier
            .fillMaxWidth()
            .height(204.dp)
            .onGloballyPositioned { anchorBounds = it.boundsInRoot() }
            .searchAttachmentAnchorHighlight(
                targeted = hit.conversationId.value == locateConversationId &&
                    hit.messageNodeId.value == locateMessageId &&
                    hit.attachment.id.value == locateAttachmentId,
                requestId = locateRequestId,
                cornerRadius = 14.dp,
            )
            .clip(cardShape)
            .combinedClickable(onClick = { onOpen(hit) }, onLongClick = { anchorBounds?.let { onLocate(hit, it) } }),
    ) {
        Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                Modifier.fillMaxWidth().height(104.dp).clip(thumbnailShape)
                    .background(if (isAudio) audioCatalogSurface else NeutralSystemSurface)
                    .border(1.dp, SubtleDivider.copy(alpha = if (isVideo) 0.72f else 0.45f), thumbnailShape),
                contentAlignment = Alignment.Center,
            ) {
                if (isAudio) {
                    AudioCatalogPlayer(
                        mimeType = hit.attachment.mimeType,
                        durationMillis = preview?.audioDurationMillis,
                        onPlay = { onOpen(hit) },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else if (bitmap != null) {
                    Image(bitmap.asImageBitmap(), contentDescription = "本地附件缩略图", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                } else if (textPreview?.text != null) {
                    AttachmentTextSnippet(
                        text = textPreview.text,
                        mimeType = hit.attachment.mimeType,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(Icons.Rounded.AttachFile, contentDescription = null, tint = if (isAudio) Color.White else SecondaryText, modifier = Modifier.size(30.dp))
                    Text(visualLabel, style = MaterialTheme.typography.labelSmall, color = if (isAudio) Color.White else SecondaryText, fontWeight = FontWeight.SemiBold)
                    if (!isAudio) Text(preview?.unavailableReason ?: if (isArchive) "点击查看压缩包内容" else "暂无可读预览", style = MaterialTheme.typography.labelSmall, color = SecondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (isVideo) {
                    Surface(
                        color = videoPlayContainer,
                        contentColor = videoPlayContent,
                        shape = CircleShape,
                        modifier = Modifier.align(Alignment.Center).size(40.dp),
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = "视频", modifier = Modifier.padding(8.dp))
                    }
                }
            }
            Text(searchHighlightedText(hit.attachment.displayName ?: "本地附件", query, MaterialTheme.colorScheme.primary), minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            Text(searchHighlightedText(hit.title, query, MaterialTheme.colorScheme.primary), minLines = 1, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = SecondaryText)
            Row(Modifier.fillMaxWidth().height(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(formatAttachmentBytes(hit.attachment.byteCount), style = MaterialTheme.typography.labelSmall, color = SecondaryText)
                Spacer(Modifier.weight(1f))
                Text(formatSearchAttachmentTimestamp(hit.timestampEpochMs), style = MaterialTheme.typography.labelSmall, color = SecondaryText)
            }
        }
    }
}

@Composable
private fun SearchAttachmentRow(
    hit: ConversationAttachmentSearchHit,
    query: String,
    preview: ConversationAttachmentPreview?,
    textPreview: ConversationAttachmentTextPreview?,
    locateConversationId: String? = null,
    locateMessageId: String? = null,
    locateAttachmentId: String? = null,
    locateRequestId: Long = 0L,
    onOpen: (ConversationAttachmentSearchHit) -> Unit,
    onEnsurePreview: (ConversationAttachmentReference) -> Unit,
    onLocate: (ConversationAttachmentSearchHit, androidx.compose.ui.geometry.Rect) -> Unit,
) {
    LaunchedEffect(hit.attachment.id) { onEnsurePreview(hit.attachment) }
    val bitmap = rememberDecodedBitmap(preview?.thumbnail?.bytes, preview?.id)
    val isAudio = hit.attachment.mimeType in com.nanzhufeng.ai.domain.CONVERSATION_ALLOWED_AUDIO_MIME_TYPES
    val isArchive = com.nanzhufeng.ai.domain.isSafeArchiveAttachment(hit.attachment.mimeType, hit.attachment.displayName)
    val audioCatalogSurface = catalogAudioPreviewSurface(dark = ForegroundSurface.red < 0.5f)
    val rowShape = RoundedCornerShape(14.dp)
    var anchorBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    Surface(
        color = ForegroundSurface,
        shape = rowShape,
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { anchorBounds = it.boundsInRoot() }
            .searchAttachmentAnchorHighlight(
                targeted = hit.conversationId.value == locateConversationId &&
                    hit.messageNodeId.value == locateMessageId &&
                    hit.attachment.id.value == locateAttachmentId,
                requestId = locateRequestId,
                cornerRadius = 14.dp,
            )
            .clip(rowShape)
            .combinedClickable(onClick = { onOpen(hit) }, onLongClick = { anchorBounds?.let { onLocate(hit, it) } }),
    ) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = if (isAudio) audioCatalogSurface else NeutralSystemSurface, shape = RoundedCornerShape(10.dp), modifier = Modifier.size(58.dp, 48.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    when {
                        isAudio -> AudioCatalogBadge(
                            mimeType = hit.attachment.mimeType,
                            onPlay = { onOpen(hit) },
                            modifier = Modifier.fillMaxSize(),
                        )
                        bitmap != null -> Image(bitmap.asImageBitmap(), contentDescription = "本地附件缩略图", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                        textPreview?.text != null -> AttachmentTextSnippet(textPreview.text, hit.attachment.mimeType, Modifier.fillMaxSize())
                        else -> Icon(Icons.Rounded.AttachFile, contentDescription = null, tint = if (isAudio) Color.White else SecondaryText)
                    }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(searchHighlightedText(hit.attachment.displayName ?: "本地附件", query, MaterialTheme.colorScheme.primary), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(searchHighlightedText(hit.title, query, MaterialTheme.colorScheme.primary), style = MaterialTheme.typography.bodySmall, color = SecondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                (hit.matchSnippet ?: textPreview?.text?.replace('\n', ' '))?.let { text -> Text(searchHighlightedText(text, query, MaterialTheme.colorScheme.primary), style = MaterialTheme.typography.labelSmall, color = SecondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                if (bitmap == null && textPreview?.text == null && !isAudio) Text(preview?.unavailableReason ?: if (isArchive) "点击查看压缩包内容" else "暂无可读预览", style = MaterialTheme.typography.labelSmall, color = SecondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (isAudio) AudioCatalogTimeline(preview?.audioDurationMillis, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(formatAttachmentBytes(hit.attachment.byteCount), style = MaterialTheme.typography.labelSmall, color = SecondaryText)
                    Spacer(Modifier.weight(1f))
                    Text(formatSearchAttachmentTimestamp(hit.timestampEpochMs), style = MaterialTheme.typography.labelSmall, color = SecondaryText)
                }
            }
            if (isAudio) Text(preview?.audioDurationMillis?.let(::formatAudioDuration) ?: "--:--", style = MaterialTheme.typography.labelSmall, color = SecondaryText)
        }
    }
}

private fun audioFormatLabel(mimeType: String): String = when (mimeType) {
    "audio/mpeg" -> "MP3"
    "audio/mp4", "audio/x-m4a" -> "M4A"
    "audio/wav", "audio/x-wav" -> "WAV"
    "audio/ogg" -> "OGG"
    "audio/aac" -> "AAC"
    else -> "音频"
}

/** Audio controls use white glyphs, so catalog previews require a dark semantic player surface. */
private fun catalogAudioPreviewSurface(dark: Boolean): Color =
    if (dark) Color(0xFF2A3438) else Color(0xFF183551)

private fun formatAudioDuration(durationMillis: Long): String {
    val totalSeconds = (durationMillis.coerceAtLeast(0L) / 1_000L)
    return "%d:%02d".format(java.util.Locale.ROOT, totalSeconds / 60, totalSeconds % 60)
}

private fun formatSearchAttachmentTimestamp(timestampEpochMs: Long): String =
    formatTranscriptTimeOrNull(java.time.Instant.ofEpochMilli(timestampEpochMs)).orEmpty()

@Composable
private fun AudioCatalogPlayer(mimeType: String, durationMillis: Long?, onPlay: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(
                onClick = onPlay,
                color = Color.White.copy(alpha = 0.18f),
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.size(30.dp).semantics { contentDescription = "播放${audioFormatLabel(mimeType)}音频" },
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.padding(6.dp))
            }
            Text(audioFormatLabel(mimeType), color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(durationMillis?.let(::formatAudioDuration) ?: "--:--", color = Color.White.copy(alpha = 0.80f), style = MaterialTheme.typography.labelSmall)
        }
        AudioCatalogTimeline(durationMillis, trackColor = Color.White.copy(alpha = 0.35f), progressColor = Color.White, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun AudioCatalogBadge(mimeType: String, onPlay: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onPlay,
        color = Color.Transparent,
        contentColor = Color.White,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier.semantics { contentDescription = "播放${audioFormatLabel(mimeType)}音频" },
    ) {
        Column(Modifier.padding(horizontal = 7.dp, vertical = 5.dp), verticalArrangement = Arrangement.spacedBy(3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
            Box(Modifier.fillMaxWidth().height(2.dp).background(Color.White.copy(alpha = 0.48f), CircleShape))
        }
    }
}

@Composable
private fun AudioCatalogTimeline(
    durationMillis: Long?,
    modifier: Modifier = Modifier,
    trackColor: Color = Color(0xFFD4D9D6),
    progressColor: Color = BrandGreen,
    progressFraction: Float = 0f,
) {
    // A catalog card has not started playback yet, so its visible timeline truthfully
    // begins at zero rather than imitating progress.
    Box(modifier.height(3.dp).clip(CircleShape).background(trackColor)) {
        if ((durationMillis ?: 0L) > 0L) {
            val barModifier = if (progressFraction <= 0f) {
                Modifier.width(3.dp)
            } else {
                Modifier.fillMaxWidth(progressFraction.coerceIn(0f, 1f))
            }
            Box(barModifier.fillMaxHeight().background(progressColor, CircleShape))
        }
    }
}

/** Audio playback only seeks once after a drag ends; intermediate positions are visual previews. */
@Composable
private fun AudioScrubbableTimeline(
    durationMillis: Long?,
    positionMillis: Long,
    onPreviewPosition: (Long) -> Unit,
    onCommitPosition: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var widthPx by remember { mutableStateOf(1f) }
    fun positionFor(x: Float): Long {
        val duration = durationMillis?.coerceAtLeast(0L) ?: 0L
        return (x / widthPx.coerceAtLeast(1f) * duration).toLong().coerceIn(0L, duration)
    }
    val enabled = (durationMillis ?: 0L) > 0L
    Box(
        modifier = modifier
            .height(22.dp)
            .onGloballyPositioned { widthPx = it.size.width.toFloat().coerceAtLeast(1f) }
            .semantics { contentDescription = "音频播放进度，可左右拖动调整位置" }
            // One owner covers both tap-to-seek and dragging; stacked recognizers can race on
            // a short drag and make a centered dialog appear to jump.
            .pointerInput(durationMillis, widthPx, enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var committed = false
                    try {
                        onPreviewPosition(positionFor(down.position.x))
                        while (!committed) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            when {
                                change.changedToUpIgnoreConsumed() -> {
                                    onPreviewPosition(positionFor(change.position.x))
                                    onCommitPosition()
                                    committed = true
                                }
                                change.positionChanged() -> {
                                    change.consume()
                                    onPreviewPosition(positionFor(change.position.x))
                                }
                            }
                        }
                    } finally {
                        if (!committed) onCancel()
                    }
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        AudioCatalogTimeline(
            durationMillis = durationMillis,
            trackColor = Color.White.copy(alpha = 0.32f),
            progressColor = Color.White,
            progressFraction = durationMillis?.takeIf { it > 0L }
                ?.let { positionMillis.toFloat() / it } ?: 0f,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** A compact, inert document cover. It shows actual stored characters and never interprets them. */
@Composable
private fun AttachmentTextSnippet(text: String, mimeType: String, modifier: Modifier = Modifier) {
    val lines = text.lineSequence().map(String::trim).filter(String::isNotEmpty).take(4).toList()
    Column(
        modifier = modifier.padding(horizontal = 8.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            textAttachmentFormatLabel(mimeType),
            style = MaterialTheme.typography.labelSmall,
            color = SecondaryText,
            fontWeight = FontWeight.SemiBold,
        )
        if (lines.isEmpty()) {
            Text("文本为空", style = MaterialTheme.typography.labelSmall, color = SecondaryText)
        } else lines.forEachIndexed { index, line ->
            val markdownHeading = mimeType == "text/markdown" && line.startsWith("#")
            Text(
                text = if (markdownHeading) line.trimStart('#', ' ') else line,
                style = if (markdownHeading && index == 0) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelSmall,
                color = BodyText,
                fontWeight = if (markdownHeading && index == 0) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AttachmentTextPreviewUnavailable(mimeType: String, reason: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 8.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            textAttachmentFormatLabel(mimeType),
            style = MaterialTheme.typography.labelSmall,
            color = SecondaryText,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            reason,
            style = MaterialTheme.typography.labelSmall,
            color = SecondaryText,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun textAttachmentFormatLabel(mimeType: String): String = when (mimeType) {
    "text/markdown" -> "MD"
    "application/json" -> "JSON"
    "text/csv" -> "CSV"
    else -> "TXT"
}

@Composable
private fun SearchHistoryPanel(
    history: List<String>,
    highlightedQuery: String?,
    onFill: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val historyListState = rememberLazyListState()
    LaunchedEffect(highlightedQuery, history) {
        history.indexOf(highlightedQuery).takeIf { it >= 0 }?.let { index ->
            historyListState.scrollToItem(index)
        }
    }
    Surface(color = ForegroundSurface, shape = RoundedCornerShape(18.dp), shadowElevation = 12.dp, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("搜索历史", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = onClear, enabled = history.isNotEmpty()) { Text("清空") }
                IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) { Icon(Icons.Rounded.Close, contentDescription = "关闭历史") }
            }
            if (history.isEmpty()) Text("暂无已提交的本地搜索。", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
            else LazyColumn(
                state = historyListState,
                modifier = Modifier.fillMaxWidth().height((history.size.coerceAtMost(6) * 48).dp),
            ) {
                items(history, key = { it }) { query ->
                    val highlighted = query == highlightedQuery
                    Surface(
                        onClick = { onFill(query) },
                        color = if (highlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent,
                        contentColor = if (highlighted) MaterialTheme.colorScheme.primary else BodyText,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), contentAlignment = Alignment.CenterStart) {
                            Text(
                                query,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (highlighted) MaterialTheme.colorScheme.primary else BodyText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchHint(text: String) = Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { Text(text, color = SecondaryText, style = MaterialTheme.typography.bodyMedium) }

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
private fun ConversationNavigationRow(
    conversation: com.nanzhufeng.ai.domain.Conversation,
    selected: Boolean,
    // In the cloud projection this is deliberately independent from the local
    // conversation record, so a cloud-only pin never leaks into the local list.
    presentationPinned: Boolean = conversation.pinnedAt != null,
    syncedToCloud: Boolean = false,
    onSelect: (com.nanzhufeng.ai.domain.ConversationId) -> Unit,
    onRequestConversationActions: (com.nanzhufeng.ai.domain.Conversation, androidx.compose.ui.geometry.Rect) -> Unit,
    generating: Boolean = false,
    unread: Boolean = false,
    watchLater: Boolean = false,
    onSwipeAction: ((com.nanzhufeng.ai.domain.Conversation, ConversationRowSwipeAction) -> Unit)? = null,
    revealed: Boolean = false,
    onRevealChanged: (Boolean) -> Unit = {},
    batchEditing: Boolean = false,
    batchSelected: Boolean = false,
    onBatchSelectionChanged: (Boolean) -> Unit = {},
) {
    var rowBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    val swipeAction = if (batchEditing) null else onSwipeAction
    // Batch selection owns a Material checkbox and never exposes row shortcuts.  Keep that
    // separate from the normal one-line conversation geometry.
    // Batch selection only needs room for its compact square control; it remains
    // visually aligned with the ordinary one-line conversation row.
    // Preserve the 8dp gap between cards.  The visual density requested for the drawer comes
    // from reducing each row's vertical text breathing room, not by making card gaps merge.
    val rowHeight = if (batchEditing) 44.dp else 36.dp
    val density = LocalDensity.current
    // The four shortcuts are one compact part of the row: they only exist visually while
    // the row moves right, and remain at the exact row height when the gesture settles open.
    val revealWidth = 176.dp
    val revealWidthPx = with(density) { revealWidth.toPx() }
    var dragOffsetPx by remember(conversation.id) { mutableStateOf(0f) }
    var dragging by remember(conversation.id) { mutableStateOf(false) }
    LaunchedEffect(revealed, revealWidthPx, dragging) {
        if (!dragging) dragOffsetPx = if (revealed) revealWidthPx else 0f
    }
    val translatedPx by animateFloatAsState(
        targetValue = if (dragging) dragOffsetPx else if (revealed) revealWidthPx else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "conversationRowSwipeOffset",
    )
    val rowSurfaceShape = if (translatedPx > 0f) {
        RoundedCornerShape(topStart = 0.dp, topEnd = 14.dp, bottomEnd = 14.dp, bottomStart = 0.dp)
    } else {
        RoundedCornerShape(14.dp)
    }
    val dragModifier = if (swipeAction == null) {
        Modifier
    } else {
        Modifier.draggable(
            state = rememberDraggableState { delta ->
                dragging = true
                dragOffsetPx = (dragOffsetPx + delta).coerceIn(0f, revealWidthPx)
            },
            orientation = Orientation.Horizontal,
            startDragImmediately = revealed,
            onDragStarted = {
                dragging = true
                dragOffsetPx = if (revealed) revealWidthPx else 0f
            },
            onDragStopped = {
                dragging = false
                onRevealChanged(dragOffsetPx >= revealWidthPx * 0.42f)
            },
        )
    }
    Box(
        modifier = Modifier.fillMaxWidth().height(rowHeight)
            .clip(RoundedCornerShape(14.dp))
            .background(ConversationDrawerRowSurface),
    ) {
        if (swipeAction != null && translatedPx > 0f) {
            ConversationRowSwipeActions(
                pinned = presentationPinned,
                favorited = conversation.favoritedAt != null,
                rowHeight = rowHeight,
                onAction = { action ->
                    onRevealChanged(false)
                    swipeAction(conversation, action)
                },
                modifier = Modifier.align(Alignment.CenterStart),
            )
        }
        // The swipe actions are deliberately drawn beneath this surface. Keep the normal
        // row opaque, otherwise the buttons bleed through every unselected conversation.
        Surface(
            color = if (selected) AccentOrangeSoft else ConversationDrawerRowSurface,
            contentColor = BodyText,
            shape = rowSurfaceShape,
            modifier = Modifier.fillMaxSize()
                .graphicsLayer { translationX = translatedPx }
                .onGloballyPositioned { rowBounds = it.boundsInRoot() }
                .then(dragModifier)
                .clip(rowSurfaceShape)
                .combinedClickable(
                    onClick = {
                        if (batchEditing) onBatchSelectionChanged(!batchSelected)
                        else if (revealed) onRevealChanged(false)
                        else onSelect(conversation.id)
                    },
                    onLongClick = if (batchEditing) null else {
                        { rowBounds?.let { onRequestConversationActions(conversation, it) } }
                    },
                ),
        ) {
            Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (batchEditing) {
                    CompactConversationSelectionCheckbox(
                        checked = batchSelected,
                        onCheckedChange = onBatchSelectionChanged,
                        selectionDescription = if (batchSelected) "已选择${conversation.title}" else "选择${conversation.title}",
                    )
                }
                if (presentationPinned) {
                    Icon(
                        painter = painterResource(R.drawable.ic_nanfeng_conversation_bubble),
                        contentDescription = null,
                        modifier = Modifier.size(scaledAppIconSize(18.dp)),
                        tint = if (selected) AccentOrange else SecondaryText,
                    )
                }
                if (syncedToCloud) {
                    NanfengCloudDoneIcon(
                        contentDescription = "已同步到云端",
                        modifier = Modifier.size(scaledAppIconSize(15.dp)),
                        tint = if (selected) AccentOrange else SecondaryText,
                    )
                }
                if (unread || watchLater) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(AccentOrange)
                            .semantics { contentDescription = if (watchLater) "未读对话" else "有未查看的新内容" },
                    )
                }
                if (generating) {
                    CircularProgressIndicator(
                        color = AccentOrange,
                        strokeWidth = 1.8.dp,
                        modifier = Modifier
                            .size(12.dp)
                            .semantics { contentDescription = "正在生成回复" },
                    )
                }
                Text(conversation.title, modifier = Modifier.weight(1f), color = if (selected) AccentOrange else BodyText, fontSize = scaledConversationTextUnit(14.sp), lineHeight = scaledConversationTextUnit(18.sp), fontWeight = FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(conversationListLocalDate(conversation.createdAt), modifier = Modifier.wrapContentWidth(Alignment.End), style = MaterialTheme.typography.labelSmall, color = SecondaryText, maxLines = 1, softWrap = false, textAlign = androidx.compose.ui.text.style.TextAlign.End)
            }
        }
    }
}

/** A compact visual checkbox; the full conversation row remains the primary batch-selection hit area. */
@Composable
private fun CompactConversationSelectionCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    selectionDescription: String,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val shape = RoundedCornerShape(4.dp)
    Box(
        modifier = Modifier
            .size(32.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
            ) { onCheckedChange(!checked) }
            .semantics { contentDescription = selectionDescription },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(shape)
                .background(
                    when {
                        checked -> AccentOrange
                        pressed -> AccentOrange.copy(alpha = 0.12f)
                        else -> Color.Transparent
                    },
                )
                .border(
                    width = 1.5.dp,
                    color = if (checked) AccentOrange else BodyText.copy(alpha = 0.68f),
                    shape = shape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = Color.White,
            )
        }
    }
}

private enum class ConversationRowSwipeAction { TOGGLE_PIN, TOGGLE_FAVORITE, RENAME, DELETE }

@Composable
private fun ConversationRowSwipeActions(
    pinned: Boolean,
    favorited: Boolean,
    rowHeight: androidx.compose.ui.unit.Dp,
    onAction: (ConversationRowSwipeAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    // This is one contextual action ribbon belonging to the revealed conversation row,
    // not four competing primary buttons. The shared surface keeps the actions visually
    // grouped while low-chroma icon colors retain their distinct meanings.
    Surface(
        color = Color(0xFFF2F4F1),
        shape = RectangleShape,
        modifier = modifier.width(176.dp).height(rowHeight),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
                ConversationRowSwipeActionButton(
                    icon = Icons.Rounded.PushPin,
                    label = if (pinned) "取消置顶" else "置顶",
                    contentColor = Color(0xFF397A6B),
                    shape = RectangleShape,
                    onClick = { onAction(ConversationRowSwipeAction.TOGGLE_PIN) },
                    modifier = Modifier.weight(1f),
                )
                ConversationRowSwipeActionButton(
                    icon = if (favorited) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                    label = if (favorited) "取消收藏" else "收藏",
                    contentColor = Color(0xFF9B751D),
                    shape = RectangleShape,
                    onClick = { onAction(ConversationRowSwipeAction.TOGGLE_FAVORITE) },
                    modifier = Modifier.weight(1f),
                )
                ConversationRowSwipeActionButton(
                    icon = Icons.Rounded.Edit,
                    label = "重命名",
                    contentColor = Color(0xFF8B684C),
                    shape = RectangleShape,
                    onClick = { onAction(ConversationRowSwipeAction.RENAME) },
                    modifier = Modifier.weight(1f),
                )
                ConversationRowSwipeActionButton(
                    icon = Icons.Rounded.DeleteOutline,
                    label = "删除",
                    contentColor = Color(0xFF935651),
                    shape = RectangleShape,
                    onClick = { onAction(ConversationRowSwipeAction.DELETE) },
                    modifier = Modifier.weight(1f),
                )
        }
    }
}

@Composable
private fun ConversationRowSwipeActionButton(
    icon: ImageVector,
    label: String,
    contentColor: Color,
    shape: androidx.compose.ui.graphics.Shape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        contentColor = contentColor,
        shape = shape,
        modifier = modifier.fillMaxHeight(),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(17.dp))
        }
    }
}

private data class ConversationActionMenuTarget(
    val conversationId: String,
    val anchor: androidx.compose.ui.geometry.Rect,
    val cloudList: Boolean = false,
)

private data class MessageActionMenuTarget(
    val messageId: String,
    val anchorBounds: androidx.compose.ui.geometry.Rect,
    val pressPosition: androidx.compose.ui.geometry.Offset,
)

/** Long-press actions are transient; the actual file identity stays in the search hit. */
private data class SearchAttachmentActionMenuTarget(
    val hit: ConversationAttachmentSearchHit,
    val anchorBounds: androidx.compose.ui.geometry.Rect,
)

private data class SearchGlmOcrActionMenuTarget(
    val hit: GlmOcrDocumentSearchHit,
    val anchorBounds: androidx.compose.ui.geometry.Rect,
)

@Composable
private fun SearchAttachmentActionPopup(
    displayName: String,
    onOpenConversation: () -> Unit,
    onRequestDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true, dismissOnClickOutside = true),
    ) {
        TransientMenuTextScale {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            DismissibleDialogBackdrop(onDismiss)
            Surface(
                color = ForegroundSurface,
                contentColor = BodyText,
                shape = RoundedCornerShape(28.dp),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .padding(horizontal = 22.dp)
                    .widthIn(max = 360.dp)
                    .fillMaxWidth(),
            ) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Text(
                        displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val actionShape = RoundedCornerShape(18.dp)
                    Surface(
                        onClick = { onDismiss(); onOpenConversation() },
                        color = NeutralSystemSurface,
                        contentColor = BodyText,
                        shape = actionShape,
                        modifier = Modifier.fillMaxWidth().clip(actionShape),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 19.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.AutoMirrored.Outlined.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = BodyText,
                            )
                            Text("快速定位", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        }
                    }
                    Surface(
                        onClick = onRequestDelete,
                        color = NeutralSystemSurface,
                        contentColor = ErrorRed,
                        shape = actionShape,
                        modifier = Modifier.fillMaxWidth().clip(actionShape),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 19.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(20.dp), tint = ErrorRed)
                            Text("删除", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = ErrorRed)
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun ConversationActionSheet(
    conversation: com.nanzhufeng.ai.domain.Conversation,
    anchor: androidx.compose.ui.geometry.Rect,
    onManage: (com.nanzhufeng.ai.domain.Conversation, ConversationManagementAction, String?) -> Unit,
    onToggleCloudPinned: (Boolean) -> Unit,
    onMarkWatchLater: (com.nanzhufeng.ai.domain.Conversation) -> Unit,
    workMode: Boolean,
    cloudList: Boolean,
    cloudPinned: Boolean,
    onRequestRename: (com.nanzhufeng.ai.domain.Conversation) -> Unit,
    onRequestProject: (com.nanzhufeng.ai.domain.Conversation) -> Unit,
    onRequestDelete: (com.nanzhufeng.ai.domain.Conversation) -> Unit,
    onShareConversation: (com.nanzhufeng.ai.domain.Conversation) -> Unit,
    onSyncConversation: (com.nanzhufeng.ai.domain.Conversation) -> Unit,
    onCancelConversationSync: (com.nanzhufeng.ai.domain.Conversation) -> Unit,
    isSyncedToCloud: Boolean,
    onShowUploadedFiles: (com.nanzhufeng.ai.domain.Conversation) -> Unit,
    onFindInConversation: (com.nanzhufeng.ai.domain.Conversation) -> Unit,
    onAddToHomeScreen: (com.nanzhufeng.ai.domain.Conversation) -> Unit,
    onDismiss: () -> Unit,
) {
    val density = LocalDensity.current
    val containerSize = LocalWindowInfo.current.containerSize
    val menuWidth = 224.dp
    val rowCount = when {
        conversation.deletedAt != null -> 1
        // The ordinary conversation menu stays focused on reading and managing the chat.
        // Workspace keeps its existing project, attachment and launcher actions.
        workMode -> 12
        else -> 9
    }
    val horizontalMargin = with(density) { 16.dp.roundToPx() }
    val verticalGap = with(density) { 6.dp.roundToPx() }
    val menuWidthPx = with(density) { menuWidth.roundToPx() }
    val menuHeightPx = with(density) { ((rowCount * 48).dp + 60.dp).roundToPx() }
    val screenWidthPx = containerSize.width
    val screenHeightPx = containerSize.height
    val maxMenuHeight = with(density) { (screenHeightPx - horizontalMargin * 2).coerceAtLeast(48).toDp() }
    val x = (anchor.right.toInt() - menuWidthPx).coerceIn(horizontalMargin, (screenWidthPx - menuWidthPx - horizontalMargin).coerceAtLeast(horizontalMargin))
    val below = anchor.bottom.toInt() + verticalGap
    val y = (if (below + menuHeightPx <= screenHeightPx - horizontalMargin) below else anchor.top.toInt() - menuHeightPx - verticalGap)
        .coerceIn(horizontalMargin, (screenHeightPx - menuHeightPx - horizontalMargin).coerceAtLeast(horizontalMargin))
    Popup(
        alignment = Alignment.TopStart,
        offset = IntOffset(x, y),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true, dismissOnBackPress = true, dismissOnClickOutside = true),
    ) {
        Surface(
            color = ForegroundSurface,
            shape = RoundedCornerShape(20.dp),
            shadowElevation = 6.dp,
            modifier = Modifier.width(menuWidth).heightIn(max = maxMenuHeight),
        ) {
            TransientMenuTextScale {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 6.dp)) {
                Text(
                    conversation.title,
                    modifier = Modifier.padding(start = 18.dp, top = 6.dp, end = 18.dp, bottom = 8.dp),
                    color = SecondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                )
                if (conversation.deletedAt != null) {
                    ConversationMenuAction(Icons.Rounded.RestoreFromTrash, "从回收站恢复", onClick = { onDismiss(); onManage(conversation, ConversationManagementAction.RESTORE_DELETED, null) })
                } else {
                    if (workMode) {
                        ConversationMenuAction(Icons.Rounded.PushPin, if (cloudList) if (cloudPinned) "取消置顶" else "置顶" else if (conversation.pinnedAt == null) "置顶" else "取消置顶", onClick = { onDismiss(); if (cloudList) onToggleCloudPinned(!cloudPinned) else onManage(conversation, if (conversation.pinnedAt == null) ConversationManagementAction.PIN else ConversationManagementAction.UNPIN, null) })
                        ConversationMenuAction(Icons.Rounded.Visibility, "未读", onClick = { onDismiss(); onMarkWatchLater(conversation) })
                        ConversationMenuAction(if (conversation.favoritedAt == null) Icons.Rounded.BookmarkBorder else Icons.Rounded.Bookmark, if (conversation.favoritedAt == null) "收藏" else "取消收藏", onClick = { onDismiss(); onManage(conversation, if (conversation.favoritedAt == null) ConversationManagementAction.FAVORITE else ConversationManagementAction.UNFAVORITE, null) })
                        ConversationMenuAction(Icons.Rounded.Edit, "重命名", onClick = { onRequestRename(conversation) })
                        ConversationMenuAction(Icons.Rounded.Share, "分享", onClick = { onShareConversation(conversation) })
                        ConversationMenuAction(
                            Icons.Rounded.CloudUpload,
                            if (isSyncedToCloud) "取消同步" else "同步到南枫云",
                            onClick = { if (isSyncedToCloud) onCancelConversationSync(conversation) else onSyncConversation(conversation) },
                        )
                        ConversationMenuAction(Icons.AutoMirrored.Outlined.DriveFileMove, if (conversation.projectId == null) "添加到项目" else "移动或移出项目", trailing = Icons.Rounded.ChevronRight, onClick = { onRequestProject(conversation) })
                        ConversationMenuAction(Icons.Rounded.AttachFile, "已上传文件", onClick = { onShowUploadedFiles(conversation) })
                        ConversationMenuAction(Icons.Rounded.Search, "在聊天中查找", onClick = { onFindInConversation(conversation) })
                        ConversationMenuAction(Icons.Rounded.Home, "添加到主屏幕", onClick = { onAddToHomeScreen(conversation) })
                        ConversationMenuAction(if (conversation.archivedAt == null) Icons.Rounded.Archive else Icons.Rounded.Unarchive, if (conversation.archivedAt == null) "归档" else "恢复", onClick = { onDismiss(); onManage(conversation, if (conversation.archivedAt == null) ConversationManagementAction.ARCHIVE else ConversationManagementAction.UNARCHIVE, null) })
                        ConversationMenuAction(Icons.Rounded.DeleteOutline, "删除", danger = true, onClick = { onRequestDelete(conversation) })
                    } else {
                        ConversationMenuAction(Icons.Rounded.PushPin, if (cloudList) if (cloudPinned) "取消置顶" else "置顶" else if (conversation.pinnedAt == null) "置顶" else "取消置顶", onClick = { onDismiss(); if (cloudList) onToggleCloudPinned(!cloudPinned) else onManage(conversation, if (conversation.pinnedAt == null) ConversationManagementAction.PIN else ConversationManagementAction.UNPIN, null) })
                        ConversationMenuAction(Icons.Rounded.Visibility, "未读", onClick = { onDismiss(); onMarkWatchLater(conversation) })
                        ConversationMenuAction(if (conversation.favoritedAt == null) Icons.Rounded.BookmarkBorder else Icons.Rounded.Bookmark, if (conversation.favoritedAt == null) "收藏" else "取消收藏", onClick = { onDismiss(); onManage(conversation, if (conversation.favoritedAt == null) ConversationManagementAction.FAVORITE else ConversationManagementAction.UNFAVORITE, null) })
                        ConversationMenuAction(Icons.Rounded.Edit, "重命名", onClick = { onRequestRename(conversation) })
                        ConversationMenuAction(Icons.Rounded.Share, "分享", onClick = { onShareConversation(conversation) })
                        ConversationMenuAction(
                            Icons.Rounded.CloudUpload,
                            if (isSyncedToCloud) "取消同步" else "同步到南枫云",
                            onClick = { if (isSyncedToCloud) onCancelConversationSync(conversation) else onSyncConversation(conversation) },
                        )
                        ConversationMenuAction(Icons.Rounded.Search, "在聊天中查找", onClick = { onFindInConversation(conversation) })
                        ConversationMenuAction(if (conversation.archivedAt == null) Icons.Rounded.Archive else Icons.Rounded.Unarchive, if (conversation.archivedAt == null) "归档" else "恢复", onClick = { onDismiss(); onManage(conversation, if (conversation.archivedAt == null) ConversationManagementAction.ARCHIVE else ConversationManagementAction.UNARCHIVE, null) })
                        ConversationMenuAction(Icons.Rounded.DeleteOutline, "删除", danger = true, onClick = { onRequestDelete(conversation) })
                    }
                }
            }
            }
        }
    }
}

/** Android owns the final launcher confirmation; the shortcut itself contains only a local ID. */
private fun requestPinConversationShortcut(
    context: android.content.Context,
    conversation: com.nanzhufeng.ai.domain.Conversation,
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
        Toast.makeText(context, "当前 Android 系统不支持添加主屏快捷方式。", Toast.LENGTH_SHORT).show()
        return
    }
    val accepted = runCatching {
        val shortcut = android.content.pm.ShortcutInfo.Builder(context, "conversation-${conversation.id.value}")
            .setShortLabel(conversation.title.take(20))
            .setLongLabel("打开对话：${conversation.title.take(48)}")
            .setIcon(android.graphics.drawable.Icon.createWithResource(context, R.mipmap.ic_launcher))
            .setIntent(Intent(context, NanfengAiActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra(CONVERSATION_SHORTCUT_ID_EXTRA, conversation.id.value)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            })
            .build()
        context.getSystemService(android.content.pm.ShortcutManager::class.java)
            .requestPinShortcut(shortcut, null)
    }.getOrDefault(false)
    Toast.makeText(
        context,
        if (accepted) "已请求添加“${conversation.title.take(20)}”到主屏幕。" else "当前桌面不支持添加主屏快捷方式。",
        Toast.LENGTH_SHORT,
    ).show()
}

@Composable
private fun ConversationUploadedFilesDialog(
    attachments: List<ConversationAttachmentReference>,
    previews: Map<AttachmentId, ConversationAttachmentPreview>,
    onDismiss: () -> Unit,
    onOpen: (ConversationAttachmentReference) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ForegroundSurface,
        shape = RoundedCornerShape(24.dp),
        title = { Text("已上传文件") },
        text = {
            if (attachments.isEmpty()) {
                Text("当前对话没有已发送的本地附件。", color = SecondaryText)
            } else Column(
                modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                attachments.forEach { attachment ->
                    Surface(
                        onClick = { onOpen(attachment) },
                        color = Color(0xFFF7F8F7),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(Icons.Rounded.AttachFile, contentDescription = null, tint = SecondaryText)
                            Column(Modifier.weight(1f)) {
                                Text(attachment.displayName ?: "本地附件", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    previews[attachment.id]?.mimeType ?: attachment.mimeType,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SecondaryText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
    )
}

@Composable
private fun ConversationFindInChatDialog(
    messages: List<PresentedTranscriptMessage>,
    onDismiss: () -> Unit,
    onFind: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val matches = conversationFindMatches(messages, query)
    val submitFind = {
        if (matches.isNotEmpty()) onFind(query)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        // The find dialog is a neutral gray task card; only the editable keyword surface is foreground-white.
        containerColor = NeutralSystemSurface,
        shape = RoundedCornerShape(24.dp),
        title = { Text("在聊天中查找") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("输入关键词", color = BodyText, style = MaterialTheme.typography.bodyMedium)
                Surface(color = NeutralSystemSurface, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth().padding(6.dp),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = ForegroundSurface,
                            unfocusedContainerColor = ForegroundSurface,
                            disabledContainerColor = ForegroundSurface,
                            focusedTextColor = BodyText,
                            unfocusedTextColor = BodyText,
                            cursorColor = AccentOrange,
                            focusedBorderColor = AccentOrange,
                            unfocusedBorderColor = Color.Transparent,
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { submitFind() }),
                    )
                }
                if (query.isNotBlank()) {
                    when {
                        matches.isEmpty() -> Text("当前对话没有匹配内容。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                        else -> Text("找到 ${matches.size} 处", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        confirmButton = { TextButton(onClick = submitFind, enabled = matches.isNotEmpty()) { Text("查找") } },
    )
}

@Composable
private fun ConversationFindNavigationBar(
    query: String,
    currentMatch: Int,
    matchCount: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = ForegroundSurface,
        contentColor = BodyText,
        shape = RoundedCornerShape(18.dp),
        shadowElevation = 4.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 5.dp, end = 4.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Icon(Icons.Rounded.Search, contentDescription = null, tint = SecondaryText, modifier = Modifier.size(18.dp))
            Text(query, modifier = Modifier.padding(start = 6.dp).weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
            Text("$currentMatch / $matchCount", color = SecondaryText, style = MaterialTheme.typography.labelSmall)
            TextButton(onClick = onPrevious, shape = RoundedCornerShape(14.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp)) { Text("上一个") }
            TextButton(onClick = onNext, shape = RoundedCornerShape(14.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp)) { Text("下一个") }
            IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Rounded.Close, contentDescription = "关闭查找", modifier = Modifier.size(18.dp), tint = SecondaryText)
            }
        }
    }
}

private data class ConversationFindTextSegment(
    val key: String,
    val text: String,
)

private fun conversationFindMatches(
    messages: List<PresentedTranscriptMessage>,
    query: String,
): List<ConversationFindMatch> = messages.flatMap { transcript ->
    conversationFindTextSegments(transcript.message).flatMap { segment ->
        conversationFindOccurrenceStarts(segment.text, query).mapIndexed { occurrenceIndex, _ ->
            ConversationFindMatch(
                messageId = transcript.message.messageId,
                segmentKey = segment.key,
                occurrenceIndex = occurrenceIndex,
            )
        }
    }
}

private fun conversationFindTextSegments(message: PresentedMessage): List<ConversationFindTextSegment> = buildList {
    message.blocks.filterNot { it is PresentationBlock.AttachmentReference }.forEachIndexed { blockIndex, block ->
        when (block) {
            is PresentationBlock.Heading -> add(ConversationFindTextSegment(conversationFindBlockKey(blockIndex), conversationVisibleInlineText(block.spans)))
            is PresentationBlock.Paragraph -> add(ConversationFindTextSegment(conversationFindBlockKey(blockIndex), conversationVisibleInlineText(block.spans)))
            is PresentationBlock.Note -> add(ConversationFindTextSegment(conversationFindBlockKey(blockIndex), conversationVisibleInlineText(block.spans)))
            is PresentationBlock.Quote -> add(ConversationFindTextSegment(conversationFindBlockKey(blockIndex), conversationVisibleInlineText(block.spans)))
            is PresentationBlock.UnorderedList -> block.items.forEachIndexed { itemIndex, item ->
                add(ConversationFindTextSegment(conversationFindListItemKey(blockIndex, itemIndex), conversationVisibleInlineText(item.spans)))
            }
            is PresentationBlock.OrderedList -> block.items.forEachIndexed { itemIndex, item ->
                add(ConversationFindTextSegment(conversationFindListItemKey(blockIndex, itemIndex), conversationVisibleInlineText(item.spans)))
            }
            is PresentationBlock.CodeFence -> add(ConversationFindTextSegment(conversationFindBlockKey(blockIndex), block.code))
            is PresentationBlock.Table -> {
                block.headers.forEachIndexed { cellIndex, cell ->
                    add(ConversationFindTextSegment(conversationFindTableHeaderKey(blockIndex, cellIndex), conversationVisibleInlineText(cell)))
                }
                block.rows.forEachIndexed { rowIndex, row ->
                    row.forEachIndexed { cellIndex, cell ->
                        add(ConversationFindTextSegment(conversationFindTableCellKey(blockIndex, rowIndex, cellIndex), conversationVisibleInlineText(cell)))
                    }
                }
            }
            is PresentationBlock.PlainText -> add(ConversationFindTextSegment(conversationFindBlockKey(blockIndex), block.raw))
            is PresentationBlock.SafeToolSummary -> add(ConversationFindTextSegment(conversationFindBlockKey(blockIndex), conversationSafeToolSummaryText(block)))
            is PresentationBlock.HorizontalRule,
            is PresentationBlock.Reasoning,
            is PresentationBlock.AttachmentReference,
            -> Unit
        }
    }
}

private fun conversationVisibleInlineText(spans: List<InlinePresentation>): String =
    chatGptImportedAutomationLabel(conversationRawInlineText(spans)) ?: inlineText(
        spans = spans,
        appendSourceShortcut = spans.any { it is InlinePresentation.Link },
    ).text

private fun conversationRawInlineText(spans: List<InlinePresentation>): String = spans.joinToString("") { span ->
    when (span) {
        is InlinePresentation.Text -> span.value
        is InlinePresentation.Strong -> span.value
        is InlinePresentation.Emphasis -> span.value
        is InlinePresentation.Code -> span.value
        is InlinePresentation.Link -> span.label
    }
}

private fun conversationSafeToolSummaryText(block: PresentationBlock.SafeToolSummary): String =
    "工具结果（安全摘要）：${block.toolName} · ${block.summary}"

private fun conversationFindBlockKey(blockIndex: Int): String = "block:$blockIndex"
private fun conversationFindListItemKey(blockIndex: Int, itemIndex: Int): String = "block:$blockIndex:item:$itemIndex"
private fun conversationFindTableHeaderKey(blockIndex: Int, cellIndex: Int): String = "block:$blockIndex:header:$cellIndex"
private fun conversationFindTableCellKey(blockIndex: Int, rowIndex: Int, cellIndex: Int): String =
    "block:$blockIndex:row:$rowIndex:cell:$cellIndex"

@Composable
private fun ConversationMenuAction(icon: ImageVector, label: String, danger: Boolean = false, trailing: ImageVector? = null, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 6.dp),
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent,
        contentColor = if (danger) MaterialTheme.colorScheme.error else BodyText,
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(16.dp))
            Text(label, modifier = Modifier.weight(1f), fontSize = scaledTransientMenuTextUnit(15.sp), lineHeight = scaledTransientMenuTextUnit(20.sp), fontWeight = FontWeight.Medium)
            trailing?.let { Icon(it, contentDescription = null, modifier = Modifier.size(18.dp)) }
        }
    }
}

@Composable
private fun ConversationManagementBar(
    state: ConversationFoundationUiState,
    onManage: (com.nanzhufeng.ai.domain.Conversation, ConversationManagementAction, String?) -> Unit,
    onExport: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val selected = state.selectedConversationId?.let { id -> state.conversations.firstOrNull { it.id == id } } ?: return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("会话管理", style = MaterialTheme.typography.labelLarge, color = SecondaryText)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = onRename, shape = RoundedCornerShape(14.dp)) { Text("重命名") }
            if (selected.deletedAt != null) OutlinedButton(onClick = { onManage(selected, ConversationManagementAction.RESTORE_DELETED, null) }, shape = RoundedCornerShape(14.dp)) { Text("恢复") }
            else {
                OutlinedButton(onClick = { onManage(selected, if (selected.pinnedAt == null) ConversationManagementAction.PIN else ConversationManagementAction.UNPIN, null) }, shape = RoundedCornerShape(14.dp)) { Text(if (selected.pinnedAt == null) "置顶" else "取消置顶") }
                OutlinedButton(onClick = { onManage(selected, if (selected.archivedAt == null) ConversationManagementAction.ARCHIVE else ConversationManagementAction.UNARCHIVE, null) }, shape = RoundedCornerShape(14.dp)) { Text(if (selected.archivedAt == null) "归档" else "恢复") }
                OutlinedButton(onClick = onDelete, shape = RoundedCornerShape(14.dp)) { Text("删除") }
            }
        }
        Button(onClick = onExport, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) { Text("导出当前路径（本机文件）") }
    }
}

@Composable
private fun MessageBubble(
    transcript: PresentedTranscriptMessage,
    attachmentPreviews: Map<AttachmentId, ConversationAttachmentPreview>,
    contextSelections: List<ContextSelectionAuditRecord>,
    responseAttributions: List<AssistantResponseModelAttribution>,
    assistantGenerationPhaseOverride: String? = null,
    assistantGenerationIsRetry: Boolean = false,
    searchAnchorAttachmentId: AttachmentId? = null,
    searchAnchorRequestId: Long = 0L,
    onLongPress: (MessageNodeId, androidx.compose.ui.geometry.Rect, androidx.compose.ui.geometry.Offset) -> Unit,
    copiedAssistantMessageId: String?,
    onCopyAssistant: (PresentedTranscriptMessage) -> Unit,
    onShareAssistant: (PresentedTranscriptMessage) -> Unit,
    onBranchAssistant: (MessageNodeId) -> Unit,
    onEnsureAttachmentPreview: (ConversationAttachmentReference) -> Unit,
    onOpenImagePreview: (AttachmentId) -> Unit,
    onOpenPdfPreview: (AttachmentId) -> Unit,
    onOpenVideoPreview: (AttachmentId) -> Unit,
    onOpenAudioPreview: (AttachmentId) -> Unit,
    onOpenTextPreview: (AttachmentId) -> Unit,
) {
    val message = transcript.message
    val roleVisual = chatRoleVisual(message.role)
    var messageBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    val textBlocks = remember(message.blocks) {
        message.blocks.filterNot { it is PresentationBlock.AttachmentReference }
    }
    val attachmentBlocks = remember(message.blocks) {
        message.blocks.filterIsInstance<PresentationBlock.AttachmentReference>()
    }
    val assistantImageBlocks = remember(attachmentBlocks) {
        ascendingAssistantImageOrder(attachmentBlocks.filter { it.attachment.mimeType.startsWith("image/") })
    }
    val assistantOtherAttachmentBlocks = remember(attachmentBlocks) {
        attachmentBlocks.filterNot { it.attachment.mimeType.startsWith("image/") }
    }
    LaunchedEffect(attachmentBlocks) {
        attachmentBlocks.forEach { block -> onEnsureAttachmentPreview(block.attachment) }
    }
    // USER is the only bubble role. The remaining transcript roles may use their own surface,
    // but share the document-sized text metrics instead of the bubble's compact inset.
    val isAssistantDocument = message.role != com.nanzhufeng.ai.domain.MessageRole.USER
    val textContent: @Composable () -> Unit = {
        ConversationTextScale {
            // Text selection belongs to the rendered transcript, so Android keeps the handles and
            // copy toolbar at the original message position instead of moving to a separate dialog.
            SelectionContainer {
                // Assistant replies are an open reading document. User prose keeps the compact
                // padding of its own right-aligned bubble, so the two roles never share a fake
                // card geometry merely because they use the same Markdown renderer.
                Column(
                    modifier = if (isAssistantDocument) {
                        Modifier.padding(
                            start = ConversationAssistantReadingStartInset,
                            top = 8.dp,
                            end = ConversationAssistantReadingEndInset,
                            bottom = 8.dp,
                        )
                    } else Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(if (isAssistantDocument) 16.dp else 7.dp),
                ) {
                    if (message.blocks.isEmpty()) Text("正在等待本地输出…", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                    textBlocks.forEachIndexed { blockIndex, block ->
                        PresentationBlockView(
                            block, attachmentPreviews, roleVisual.body, transcript.metadata.createdAt, isAssistantDocument,
                            onOpenImagePreview, onOpenPdfPreview, onOpenVideoPreview, onOpenAudioPreview, onOpenTextPreview,
                            findBlockIndex = blockIndex,
                        )
                    }
                }
            }
        }
    }
    val attachmentContent: @Composable () -> Unit = {
        if (attachmentBlocks.isNotEmpty()) DisableSelection {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                attachmentBlocks.forEach { block -> PresentationBlockView(
                    block, attachmentPreviews, roleVisual.body, transcript.metadata.createdAt, isAssistantDocument,
                    onOpenImagePreview, onOpenPdfPreview, onOpenVideoPreview, onOpenAudioPreview, onOpenTextPreview,
                    searchAnchorAttachmentId, searchAnchorRequestId,
                ) }
            }
        }
    }
    val assistantAttachmentContent: @Composable () -> Unit = {
        if (attachmentBlocks.isNotEmpty()) DisableSelection {
            Column(
                modifier = Modifier.fillMaxWidth().padding(
                    start = ConversationAssistantReadingStartInset,
                    end = ConversationAssistantReadingEndInset,
                ),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (assistantImageBlocks.isNotEmpty()) {
                    AssistantGeneratedImageGroup(
                        messageId = message.messageId,
                        images = assistantImageBlocks,
                        attachmentPreviews = attachmentPreviews,
                        onOpenImagePreview = onOpenImagePreview,
                        searchAnchorAttachmentId = searchAnchorAttachmentId,
                        searchAnchorRequestId = searchAnchorRequestId,
                    )
                }
                assistantOtherAttachmentBlocks.forEach { block ->
                    PresentationBlockView(
                        block, attachmentPreviews, roleVisual.body, transcript.metadata.createdAt, true,
                        onOpenImagePreview, onOpenPdfPreview, onOpenVideoPreview, onOpenAudioPreview, onOpenTextPreview,
                        searchAnchorAttachmentId, searchAnchorRequestId,
                    )
                }
            }
        }
    }
    when (message.role) {
        com.nanzhufeng.ai.domain.MessageRole.USER -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // A user bubble measures from its content.  Only the available transcript width
            // limits long text; there is deliberately no visual minimum width for short text.
            if (textBlocks.isNotEmpty()) RightAlignedUserBubble(surfaceColor = roleVisual.surface,
                onAnchorChanged = { messageBounds = it },
                // Long-pressing readable center text selects it in place. The bubble's
                // four-sided edge zone remains a separate target for the action popup.
                onLongPress = { pressPosition -> messageBounds?.let { anchorBounds -> onLongPress(message.messageId, anchorBounds, pressPosition) } },
            ) { textContent() }
            // The group is deliberately a sibling of the user text surface.  Its own preview
            // controls keep their long-press metadata overlay; attachment-only messages still
            // expose the message action sheet from the group boundary.
            if (attachmentBlocks.isNotEmpty()) Box(
                modifier = Modifier
                    .onGloballyPositioned { messageBounds = it.boundsInRoot() }
                    .pointerInput(message.messageId) {
                        detectTapGestures(onLongPress = { pressPosition ->
                            messageBounds?.let { anchorBounds -> onLongPress(message.messageId, anchorBounds, pressPosition) }
                        })
                    },
                contentAlignment = Alignment.CenterEnd,
            ) { attachmentContent() }
        }
        // Assistant prose belongs to the open transcript column, not a functional card/bubble.
        com.nanzhufeng.ai.domain.MessageRole.ASSISTANT -> Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            transcript.metadata.workDurationLabel?.let { duration ->
                Text(
                    duration,
                    color = SecondaryText,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(
                        start = ConversationAssistantReadingStartInset,
                        end = ConversationAssistantReadingEndInset,
                    ),
                )
            }
            // A PARTIAL assistant message is the durable in-progress state created before the
            // first chunk arrives. Render it explicitly instead of leaving a blank transcript
            // gap, and keep the small indicator while streamed text continues to arrive.
            if (message.deliveryState == com.nanzhufeng.ai.domain.MessageDeliveryState.PARTIAL) {
                AssistantGenerationStatus(
                    transcript.metadata.waitingPreview?.let { preview ->
                        assistantGenerationPhaseOverride?.let { preview.copy(phase = it) } ?: preview
                    },
                    hasPartialText = textBlocks.isNotEmpty(),
                    isRetrying = assistantGenerationIsRetry,
                )
            }
            if (message.deliveryState == com.nanzhufeng.ai.domain.MessageDeliveryState.FAILED) {
                AssistantGenerationFailure(transcript.metadata.safeErrorCode)
            }
            if (textBlocks.isNotEmpty()) Box(Modifier.fillMaxWidth()) { textContent() }
            if (attachmentBlocks.isNotEmpty()) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) { assistantAttachmentContent() }
            AssistantMessageActionRow(transcript, copiedAssistantMessageId, contextSelections, responseAttributions, onCopyAssistant, onShareAssistant, onBranchAssistant)
        }
        else -> Surface(color = roleVisual.surface, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { textContent(); attachmentContent() }
        }
    }
}

/** Shared user-message geometry for normal, work and temporary conversations. */
@Composable
private fun RightAlignedUserBubble(
    surfaceColor: Color = AccentOrangeSoft,
    onAnchorChanged: (androidx.compose.ui.geometry.Rect) -> Unit = {},
    onLongPress: ((androidx.compose.ui.geometry.Offset) -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
            Surface(
                color = surfaceColor,
                // Keep short replies compact while giving multi-line prose a stable card edge;
                // percentage corners turn tall messages into distracting ovals.
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .widthIn(max = maxWidth * 0.82f)
                    .wrapContentWidth(Alignment.End)
                    .onGloballyPositioned { onAnchorChanged(it.boundsInRoot()) },
            ) {
                Box {
                    content()
                    // This overlay is matched to the bubble after its content has been measured,
                    // so its gesture zones never widen a naturally short message. The existing
                    // top/bottom padding plus outer quarters are action zones; the center text
                    // stays available for Android's native in-place selection and copy toolbar.
                    if (onLongPress != null) {
                        val menuLongPress = Modifier.pointerInput(onLongPress) {
                            detectTapGestures(onLongPress = { pressPosition -> onLongPress(pressPosition) })
                        }
                        BoxWithConstraints(Modifier.matchParentSize()) {
                            val sideMenuZone = maxWidth * 0.25f
                            Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(12.dp).then(menuLongPress))
                            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(12.dp).then(menuLongPress))
                            Box(Modifier.align(Alignment.CenterStart).width(sideMenuZone).fillMaxHeight().then(menuLongPress))
                            Box(Modifier.align(Alignment.CenterEnd).width(sideMenuZone).fillMaxHeight().then(menuLongPress))
                        }
                    }
                }
            }
        }
    }
}

/** Honest, transient feedback for the persisted assistant message that is still receiving output. */
@Composable
private fun AssistantGenerationStatus(
    waitingPreview: com.nanzhufeng.ai.domain.AssistantWaitingPreview?,
    hasPartialText: Boolean,
    isRetrying: Boolean,
) {
    val pulse by rememberInfiniteTransition(label = "assistant-generation").animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(780), repeatMode = RepeatMode.Reverse),
        label = "assistant-generation-pulse",
    )
    if (!hasPartialText) {
        val preview = waitingPreview ?: com.nanzhufeng.ai.domain.AssistantWaitingPreview(
            preface = "我先梳理你的问题和重点，再给你清晰答复。",
            phase = "正在准备回答",
        )
        Column(
            modifier = Modifier.padding(
                start = ConversationAssistantReadingStartInset,
                top = 10.dp,
                end = ConversationAssistantReadingEndInset,
                bottom = 10.dp,
            ).semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (isRetrying) {
                Text(
                    "正在重新生成",
                    color = AccentOrange,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                preview.preface,
                color = BodyText,
                style = MaterialTheme.typography.bodyMedium,
                fontSize = scaledConversationTextUnit(16.sp),
                lineHeight = scaledConversationTextUnit(26.sp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    color = AccentOrange,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(16.dp).graphicsLayer(alpha = pulse),
                )
                Text(preview.phase, color = SecondaryText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }
        }
    } else Row(
        modifier = Modifier.padding(
            start = ConversationAssistantReadingStartInset,
            top = 2.dp,
            end = ConversationAssistantReadingEndInset,
            bottom = 2.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(color = AccentOrange, strokeWidth = 2.dp, modifier = Modifier.size(14.dp).graphicsLayer(alpha = pulse))
        Text(
            if (isRetrying) "正在重新生成…" else "南枫 AI 正在继续生成…",
            color = SecondaryText.copy(alpha = pulse),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** Durable in-transcript failure feedback; it remains visible after broadcasts, reloads and restarts. */
@Composable
private fun AssistantGenerationFailure(safeErrorCode: String?) {
    val detail = safeErrorCode
        ?.let { raw -> runCatching { NormalChatOpenRouterExecutor.Code.valueOf(raw) }.getOrNull() }
        ?.let { code -> normalChatResultLabel(code, sent = true) }
        ?: "本次回答未完成；没有保存为空白的完整回答。你可以检查模型与联网设置后重试。"
    Surface(
        color = Color(0xFFFFF3F1),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = ConversationAssistantReadingStartInset,
                top = 8.dp,
                end = ConversationAssistantReadingEndInset,
                bottom = 8.dp,
            )
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text("回答未完成", color = ErrorRed, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text(detail, color = BodyText, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun BranchCreationFeedback(
    branchCreation: ConversationBranchCreationUi,
    onDismiss: (com.nanzhufeng.ai.domain.ConversationId) -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(branchCreation.branchConversationId) {
        delay(2_800)
        onDismiss(branchCreation.branchConversationId)
    }
    Surface(
        color = Color(0xFFF1F8F4),
        contentColor = BrandGreen,
        shape = RoundedCornerShape(18.dp),
        shadowElevation = 5.dp,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text("已创建分支", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text("已打开新的本地对话", color = SecondaryText, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/** Always-visible assistant row; USER actions intentionally remain long-press-only. */
@Composable
private fun AssistantMessageActionRow(
    transcript: PresentedTranscriptMessage,
    copiedAssistantMessageId: String?,
    contextSelections: List<ContextSelectionAuditRecord>,
    responseAttributions: List<AssistantResponseModelAttribution>,
    onCopy: (PresentedTranscriptMessage) -> Unit,
    onShare: (PresentedTranscriptMessage) -> Unit,
    onBranch: (MessageNodeId) -> Unit,
) {
    val copied = copiedAssistantMessageId == transcript.message.messageId.value
    val time = formatTranscriptTimeOrNull(transcript.metadata.createdAt)
    val model = assistantFooterModelName(transcript.metadata.modelSnapshotLabel)
    var moreActionsExpanded by remember(transcript.message.messageId) { mutableStateOf(false) }
    // The conversation footer is a compact reading surface. It projects the persisted cost
    // label without estimate provenance and rounds only this rendered amount; settings and the
    // cost ledger keep their full source-qualified accounting label.
    val cost = assistantFooterCostDisplay(transcript.metadata.costLabel)
    val answerInformation = contextSelections.answerInformationDisclosure(responseAttributions)
    var answerInformationVisible by remember(transcript.message.messageId) { mutableStateOf(false) }
    val metadataStyle = MaterialTheme.typography.labelSmall
    Column(
        modifier = Modifier.fillMaxWidth().padding(
            // Keep the established internal order and spacing; only align the complete footer
            // group's first visible glyph with the assistant prose above it.
            start = AssistantFooterGroupStartInset,
            end = ConversationAssistantReadingEndInset,
        ),
        // Assistant provenance belongs to the assistant side. Keep concise facts inline when
        // possible, but give a real amount its own right-aligned line before it is squeezed into
        // a character-by-character vertical stack on a narrow screen.
        horizontalAlignment = Alignment.Start,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AssistantFooterActionSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AssistantMessageAction(
                icon = if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                contentDescription = if (copied) "已复制" else "复制",
                onClick = { onCopy(transcript) },
                iconSize = 16.dp,
            )
            AssistantMessageAction(
                icon = Icons.Rounded.Share,
                contentDescription = "分享",
                onClick = { onShare(transcript) },
                iconSize = 16.dp,
            )
            Box {
                AssistantMessageAction(
                    icon = Icons.Rounded.MoreVert,
                    contentDescription = "更多操作",
                    onClick = { moreActionsExpanded = true },
                )
                DropdownMenu(
                    expanded = moreActionsExpanded,
                    onDismissRequest = { moreActionsExpanded = false },
                    containerColor = ForegroundSurface,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    DropdownMenuItem(
                        text = { Text("本次回答信息") },
                        leadingIcon = { Icon(Icons.Rounded.AccountTree, contentDescription = null, modifier = Modifier.size(16.dp), tint = SecondaryText.copy(alpha = 0.72f)) },
                        onClick = {
                            moreActionsExpanded = false
                            answerInformationVisible = true
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("创建分支") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.CallSplit,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = BodyText,
                            )
                        },
                        onClick = {
                            moreActionsExpanded = false
                            onBranch(transcript.message.messageId)
                        },
                    )
                }
            }
            BoxWithConstraints(Modifier.weight(1f)) {
                val inlineMetadata = assistantFooterMetadataText(time, model, cost)
                val primaryMetadata = assistantFooterMetadataText(time, model, null)
                val inlineWidth = rememberTextMeasurer().measure(
                    text = inlineMetadata,
                    style = metadataStyle,
                    softWrap = false,
                    maxLines = 1,
                ).size.width
                val costOnOwnLine = cost != null && inlineWidth > with(LocalDensity.current) { maxWidth.roundToPx() }

                Column(Modifier.fillMaxWidth()) {
                    Text(
                        text = if (costOnOwnLine) primaryMetadata else inlineMetadata,
                        color = SecondaryText,
                        style = metadataStyle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (costOnOwnLine) {
                        Text(
                            text = cost.orEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                            color = SecondaryText,
                            style = metadataStyle,
                            textAlign = TextAlign.End,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
    if (answerInformationVisible) {
        AnswerInformationDialog(answerInformation) { answerInformationVisible = false }
    }
}

@Composable
private fun AnswerInformationDialog(
    disclosure: AnswerInformationDisclosure,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("本次回答信息") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                AnswerInformationSection(title = "回答设置") {
                    AnswerInformationFact(
                        label = "基础风格和语气",
                        value = disclosure.styleLabel ?: "未记录（旧回答）",
                    )
                    HorizontalDivider(color = SecondaryText.copy(alpha = 0.12f))
                    AnswerInformationFact(
                        label = "实时网络",
                        value = when (disclosure.webSearchUsed) {
                            true -> "已实际使用"
                            false -> "本次未使用"
                            null -> "未记录（旧回答）"
                        },
                        emphasized = disclosure.webSearchUsed == true,
                    )
                }
                if (disclosure.sources.isNotEmpty()) {
                    AnswerInformationSection(title = "本次上下文来源") {
                        disclosure.sources.forEach { source -> AnswerContextSourceRow(source) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("知道了") } },
        containerColor = ForegroundSurface,
    )
}

@Composable
private fun AnswerInformationSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            color = SecondaryText,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
        content()
    }
}

@Composable
private fun AnswerInformationFact(label: String, value: String, emphasized: Boolean = false) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, color = SecondaryText, style = MaterialTheme.typography.bodySmall)
        if (value != label) {
            Text(
                value,
                color = if (emphasized) AccentOrange else BodyText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun AnswerContextSourceRow(source: AnswerContextSourceDisclosure) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text("•", color = AccentOrange, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        Text(
            text = answerContextSourceTitle(source),
            modifier = Modifier.weight(1f),
            color = BodyText,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun answerContextSourceTitle(source: AnswerContextSourceDisclosure): String = when (source.kind) {
    "个性化资料" -> source.title
    "自定义指令" -> source.kind
    else -> "${source.kind} · ${source.title}"
}

/** The footer is a reading aid, not a technical route ledger. */
private fun assistantFooterModelName(label: String?): String? = label
    ?.let { com.nanzhufeng.ai.domain.composerModelShortNameForUser(it) }
    ?.trim()
    ?.takeIf { it.isNotBlank() }

private fun assistantFooterMetadataText(time: String?, model: String?, cost: String?): AnnotatedString =
    if (model == null) {
        AnnotatedString(listOfNotNull(time, cost).joinToString(" · "))
    } else {
        modelNameAnnotatedText(
            prefix = time?.let { "$it · " }.orEmpty(),
            modelName = model,
            suffix = cost?.let { " · $it" }.orEmpty(),
        )
    }

/** A visually quiet but still comfortably tappable action used by every assistant footer command. */
@Composable
private fun AssistantMessageAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    iconSize: androidx.compose.ui.unit.Dp = 20.dp,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(36.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(iconSize),
            tint = SecondaryText.copy(alpha = 0.72f),
        )
    }
}

@Composable
private fun MessageActionPopup(
    anchorBounds: androidx.compose.ui.geometry.Rect,
    pressPosition: androidx.compose.ui.geometry.Offset,
    headline: AnnotatedString,
    editable: Boolean,
    onDismiss: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    val containerSize = LocalWindowInfo.current.containerSize
    val menuWidth = 224.dp
    val rowCount = 3 + if (editable) 1 else 0
    val horizontalMargin = with(density) { 16.dp.roundToPx() }
    val verticalGap = with(density) { 6.dp.roundToPx() }
    val menuWidthPx = with(density) { menuWidth.roundToPx() }
    val headlineHeightPx = with(density) { if (headline.isBlank()) 0 else 36.dp.roundToPx() }
    val menuHeightPx = with(density) { (rowCount * 46).dp.roundToPx() } + headlineHeightPx + with(density) { 12.dp.roundToPx() }
    val screenWidthPx = containerSize.width
    val screenHeightPx = containerSize.height
    val pressInRoot = androidx.compose.ui.geometry.Offset(anchorBounds.left + pressPosition.x, anchorBounds.top + pressPosition.y)
    val x = (pressInRoot.x.toInt() - menuWidthPx / 2)
        .coerceIn(horizontalMargin, (screenWidthPx - menuWidthPx - horizontalMargin).coerceAtLeast(horizontalMargin))
    val y = (pressInRoot.y.toInt() - menuHeightPx - verticalGap)
        .coerceIn(horizontalMargin, (screenHeightPx - menuHeightPx - horizontalMargin).coerceAtLeast(horizontalMargin))
    Popup(
        alignment = Alignment.TopStart,
        offset = IntOffset(x, y),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true, dismissOnBackPress = true, dismissOnClickOutside = true),
    ) {
        Surface(
            color = ForegroundSurface,
            contentColor = BodyText,
            shape = RoundedCornerShape(20.dp),
            shadowElevation = 6.dp,
            modifier = Modifier.width(menuWidth),
        ) {
            Column(Modifier.padding(vertical = 6.dp)) {
                if (headline.isNotBlank()) Text(
                    headline,
                    modifier = Modifier.padding(start = 20.dp, end = 16.dp, bottom = 2.dp),
                    color = SecondaryText,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                content()
            }
        }
    }
}

/** A reading surface intentionally uses the available phone width instead of dialog defaults. */
@Composable
private fun WideTextSelectionDialog(text: String, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true, dismissOnClickOutside = true),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            DismissibleDialogBackdrop(onDismiss)
            Surface(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth(),
                color = ForegroundSurface,
                contentColor = BodyText,
                shape = RoundedCornerShape(24.dp),
                shadowElevation = 8.dp,
            ) {
                Column(Modifier.padding(start = 24.dp, top = 22.dp, end = 24.dp, bottom = 12.dp)) {
                    Text("选择文本", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium)
                    Box(Modifier.fillMaxWidth().heightIn(max = 580.dp).padding(top = 18.dp, bottom = 8.dp)) {
                        SelectionContainer {
                            Text(
                                text,
                                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                            )
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDismiss, shape = RoundedCornerShape(16.dp)) { Text("完成") }
                    }
                }
            }
        }
    }
}

/** The editable message needs a wide reading and editing surface on phone-sized windows. */
@Composable
private fun EditUserMessageDialog(
    value: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onCreateBranch: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true, dismissOnClickOutside = true),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            DismissibleDialogBackdrop(onDismiss)
            Surface(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .fillMaxWidth(),
                color = ForegroundSurface,
                contentColor = BodyText,
                shape = RoundedCornerShape(24.dp),
                shadowElevation = 8.dp,
            ) {
                Column(Modifier.padding(start = 24.dp, top = 22.dp, end = 24.dp, bottom = 12.dp)) {
                    Text("编辑", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium)
                    OutlinedTextField(
                        value = value,
                        onValueChange = onValueChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 132.dp, max = 580.dp)
                            .padding(top = 18.dp)
                            .p5aKeyboardTraversal(),
                        minLines = 3,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.width(44.dp).height(30.dp),
                            shape = RoundedCornerShape(15.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                        ) { Text("取消", fontSize = 13.sp) }
                        Button(
                            onClick = onCreateBranch,
                            enabled = value.isNotBlank(),
                            modifier = Modifier.width(76.dp).height(32.dp),
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                        ) { Text("创建分支", fontSize = 13.sp) }
                    }
                }
            }
        }
    }
}

/** Clipboard writes are synchronous here; only a completed write receives the system confirmation haptic. */
@Composable
private fun rememberConversationCopyTextAction(): (String, () -> Unit) -> Unit {
    val clipboard = LocalClipboardManager.current
    val view = LocalView.current
    return remember(clipboard, view) {
        { text, onCopied ->
            runCatching {
                clipboard.setText(androidx.compose.ui.text.AnnotatedString(text))
            }.onSuccess {
                val feedback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    HapticFeedbackConstants.CONFIRM
                } else {
                    HapticFeedbackConstants.KEYBOARD_TAP
                }
                view.performHapticFeedback(feedback)
                onCopied()
            }
        }
    }
}

@Composable
private fun MessageContextAction(
    icon: ImageVector,
    label: String,
    iconOnly: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = if (iconOnly) Modifier.size(46.dp) else Modifier.fillMaxWidth().height(46.dp).padding(horizontal = 6.dp),
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent,
        contentColor = BodyText,
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = if (iconOnly) 0.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (iconOnly) Arrangement.Center else Arrangement.Start,
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(22.dp))
            if (!iconOnly) {
                Spacer(Modifier.width(16.dp))
                Text(label, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

private val transcriptTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日 HH:mm")
private fun formatTranscriptTimeOrNull(value: java.time.Instant?): String? = value?.let { transcriptTimeFormatter.withZone(ZoneId.systemDefault()).format(it) }
private val transcriptDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日")
private fun transcriptLocalDateKey(value: java.time.Instant): String = transcriptDateFormatter.withZone(ZoneId.systemDefault()).format(value)
private fun conversationListLocalDate(value: java.time.Instant): String {
    return DateTimeFormatter.ofPattern("yyyy/MM/dd").withZone(ZoneId.systemDefault()).format(value)
}

/**
 * Rename is a single compact form, not a generic confirmation dialog.  The
 * custom width is constrained to the drawer-side canvas instead of the whole
 * dialog window, while the 48dp field plus 44dp action row removes AlertDialog's
 * otherwise unused title/body space.
 */
@Composable
private fun CompactConversationRenameDialog(
    value: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var fieldValue by remember { mutableStateOf(TextFieldValue(value, selection = TextRange(0, value.length))) }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true, dismissOnClickOutside = true),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
            val drawerWidth = if (maxWidth >= 600.dp) maxWidth * (2f / 3f) else maxWidth.coerceAtMost(320.dp)
            // A full-width Compose Dialog owns the entire window, so its dimmed area is
            // not technically "outside" the Dialog. Keep one shared backdrop target above
            // the window but below the card so every background tap remains a cancel action.
            DismissibleDialogBackdrop(onDismiss)
            Surface(
                modifier = Modifier
                    .width(drawerWidth),
                color = ForegroundSurface,
                shape = RoundedCornerShape(20.dp),
                shadowElevation = 8.dp,
            ) {
                Column(
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    CompactConversationRenameField(
                        value = fieldValue,
                        onValueChange = { updated ->
                            fieldValue = updated
                            onValueChange(updated.text)
                        },
                        focusRequester = focusRequester,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = onDismiss, modifier = Modifier.height(34.dp), shape = RoundedCornerShape(17.dp)) { Text("取消") }
                        Button(onClick = onConfirm, modifier = Modifier.height(32.dp), shape = RoundedCornerShape(16.dp)) { Text("保存") }
                    }
                }
            }
        }
    }
}

/** Shared rule for full-window dialog shells: tapping the dimmed canvas cancels the dialog. */
@Composable
private fun DismissibleDialogBackdrop(onDismiss: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .p5aDismissOnInwardEdgeSwipe(onDismiss)
            .pointerInput(onDismiss) { detectTapGestures(onTap = { onDismiss() }) },
    )
}

/** A full-height editable line; the label decorates the border without consuming input space. */
@Composable
private fun CompactConversationRenameField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    focusRequester: FocusRequester,
) {
    val shape = RoundedCornerShape(12.dp)
    Box(Modifier.fillMaxWidth().height(48.dp)) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                .clip(shape)
                .border(1.dp, Color(0xFF79747E), shape)
                .padding(horizontal = 12.dp),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = scaledConversationTextUnit(16.sp), color = BodyText),
            decorationBox = { innerTextField ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) { innerTextField() }
            },
        )
        Text(
            "会话标题",
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 12.dp, y = (-8).dp)
                .background(ForegroundSurface)
                .padding(horizontal = 4.dp),
            color = SecondaryText,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun TranscriptDateDivider(label: String) = Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
    HorizontalDivider(modifier = Modifier.weight(1f), color = SubtleDivider)
    Text(label, modifier = Modifier.padding(horizontal = 10.dp), style = MaterialTheme.typography.labelSmall, color = SecondaryText)
    HorizontalDivider(modifier = Modifier.weight(1f), color = SubtleDivider)
}

/** Import provenance is only a source label. It must never imply that the live conversation is locked. */
@Composable
private fun ImportedConversationProvenance(label: String) {
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        contentColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.78f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            label,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
    }
}

private data class TranscriptScrollMetrics(
    val progress: Float,
    val thumbFraction: Float,
    val canScroll: Boolean,
)

/**
 * Captures the final Compose-measured row heights, including the exact wrapped text line count,
 * date dividers, media cards and action rows. The cache is scoped to one conversation so an old
 * transcript can never affect the position range of the newly opened one.
 */
@Composable
private fun rememberTranscriptMeasuredItemHeights(
    listState: LazyListState,
    transcriptIdentity: Any?,
): Map<Int, Int> {
    val measuredItemHeights = remember(transcriptIdentity) { mutableStateMapOf<Int, Int>() }
    LaunchedEffect(listState, transcriptIdentity) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.map { item -> item.index to item.size.coerceAtLeast(1) }
        }.collect { visibleItemHeights ->
            visibleItemHeights.forEach { (index, height) ->
                if (measuredItemHeights[index] != height) measuredItemHeights[index] = height
            }
        }
    }
    return measuredItemHeights
}

/** One source of truth for the right-side transcript scrollbar. */
private fun transcriptScrollMetrics(
    listState: LazyListState,
    measuredItemHeights: Map<Int, Int>,
): TranscriptScrollMetrics {
    val layout = listState.layoutInfo
    val totalItems = layout.totalItemsCount
    val visibleItems = layout.visibleItemsInfo
    if (totalItems == 0 || visibleItems.isEmpty()) {
        return TranscriptScrollMetrics(progress = 0f, thumbFraction = 1f, canScroll = false)
    }
    val knownItemHeights = measuredItemHeights.toMutableMap().apply {
        visibleItems.forEach { item -> put(item.index, item.size.coerceAtLeast(1)) }
    }
    val knownRows = knownItemHeights.filterKeys { it in 0 until totalItems }
    val viewportHeight = (layout.viewportEndOffset - layout.viewportStartOffset).coerceAtLeast(1)
    // Unseen LazyColumn rows are not guessed from a message count. They use the median of rows
    // that Compose has already laid out, capped at one third of the viewport. This prevents one
    // currently visible long bubble from inflating every unseen row while preserving that long
    // bubble's exact measured pixels in both the range and progress.
    val fallbackRowHeight = knownRows.values.sorted().let { heights ->
        heights[heights.size / 2].coerceIn(1, (viewportHeight / 3).coerceAtLeast(1))
    }
    val estimatedRowSpacing = visibleItems.zipWithNext()
        .map { (first, second) -> second.offset - first.offset - first.size }
        .filter { it >= 0 }
        .sorted()
        .let { gaps -> gaps.getOrElse(gaps.size / 2) { 0 } }
    val knownHeightTotal = knownRows.values.sum()
    val estimatedContentHeight = knownHeightTotal +
        (totalItems - knownRows.size) * fallbackRowHeight +
        (totalItems - 1).coerceAtLeast(0) * estimatedRowSpacing +
        layout.beforeContentPadding + layout.afterContentPadding
    val canScroll = listState.canScrollBackward || listState.canScrollForward || estimatedContentHeight > viewportHeight
    if (!canScroll) return TranscriptScrollMetrics(progress = 0f, thumbFraction = 1f, canScroll = false)

    val firstVisibleIndex = listState.firstVisibleItemIndex
    val knownBefore = knownRows.filterKeys { it < firstVisibleIndex }
    val scrollOffset = knownBefore.values.sum() +
        (firstVisibleIndex - knownBefore.size) * fallbackRowHeight +
        firstVisibleIndex * estimatedRowSpacing +
        listState.firstVisibleItemScrollOffset
    val maximumScrollOffset = (estimatedContentHeight - viewportHeight).coerceAtLeast(1)
    val progress = when {
        !listState.canScrollBackward -> 0f
        !listState.canScrollForward -> 1f
        else -> (scrollOffset.toFloat() / maximumScrollOffset).coerceIn(0f, 1f)
    }
    val thumbFraction = (viewportHeight.toFloat() / estimatedContentHeight).coerceIn(0.08f, 1f)
    return TranscriptScrollMetrics(progress, thumbFraction, canScroll = true)
}

private const val TranscriptScrollbarIdleHideMillis = 3_000L

/** Keeps the passive reading-position cue visible only while the reader needs it. */
@Composable
private fun rememberTranscriptScrollIndicatorVisible(
    listState: LazyListState,
    canScroll: Boolean,
): Boolean {
    var visible by remember(listState) { mutableStateOf(true) }
    LaunchedEffect(canScroll, listState.isScrollInProgress) {
        when {
            !canScroll -> visible = false
            listState.isScrollInProgress -> visible = true
            else -> {
                delay(TranscriptScrollbarIdleHideMillis)
                visible = false
            }
        }
    }
    return visible
}

/** A passive scroll thumb appears while reading, then clears from a static transcript. */
@Composable
private fun TranscriptScrollIndicator(
    listState: LazyListState,
    measuredItemHeights: Map<Int, Int>,
    modifier: Modifier = Modifier,
) {
    val metrics = transcriptScrollMetrics(listState, measuredItemHeights)
    if (!metrics.canScroll) return
    if (!rememberTranscriptScrollIndicatorVisible(listState, metrics.canScroll)) return
    val animatedProgress by animateFloatAsState(
        targetValue = metrics.progress,
        animationSpec = tween(durationMillis = 110, easing = LinearOutSlowInEasing),
        label = "transcript-scroll-progress",
    )
    val animatedThumbFraction by animateFloatAsState(
        targetValue = metrics.thumbFraction,
        animationSpec = tween(durationMillis = 110, easing = LinearOutSlowInEasing),
        label = "transcript-scroll-thumb-size",
    )
    BoxWithConstraints(modifier = modifier.width(8.dp).fillMaxHeight()) {
        val thumbHeight = maxHeight * animatedThumbFraction
        val thumbOffset = (maxHeight - thumbHeight) * animatedProgress
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .width(1.dp)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.08f)),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = thumbOffset)
                .width(3.dp)
                .height(thumbHeight)
                .clip(CircleShape)
                .background(BodyText.copy(alpha = 0.38f))
                .semantics { contentDescription = "对话滚动位置" },
        )
    }
}

private fun presentedMessagePlainText(message: PresentedMessage): String = message.blocks.joinToString("\n") { block -> when (block) {
    is PresentationBlock.Heading -> inlineText(block.spans).text
    is PresentationBlock.Paragraph -> inlineText(block.spans).text
    is PresentationBlock.Note -> inlineText(block.spans, suppressEmphasis = true).text
    is PresentationBlock.Quote -> inlineText(block.spans).text
    is PresentationBlock.UnorderedList -> block.items.joinToString("\n") { "• ${inlineText(it.spans).text}" }
    is PresentationBlock.OrderedList -> block.items.joinToString("\n") { "${it.ordinal}. ${inlineText(it.spans).text}" }
    is PresentationBlock.HorizontalRule -> ""
    is PresentationBlock.CodeFence -> block.code
    is PresentationBlock.Table -> tablePlainText(block)
    is PresentationBlock.PlainText -> block.raw
    is PresentationBlock.AttachmentReference -> "${block.attachment.displayName ?: "本地附件"} · ${block.attachment.mimeType}"
    is PresentationBlock.Reasoning -> ""
    is PresentationBlock.SafeToolSummary -> "工具结果（安全摘要）：${block.toolName} · ${block.summary}"
} }

/**
 * A title-first name stays recognizable in Android's file picker; the short sequence
 * differentiates a single reply from a full conversation without exposing timestamps.
 */
private fun conversationMarkdownExportFileName(title: String, sequence: Int): String {
    val safeTitle = title
        .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(48)
        .ifBlank { "对话" }
    return safeTitle + "-" + sequence.coerceIn(1, 99).toString().padStart(2, '0') + ".md"
}

/** Exports the already-visible assistant response as portable Markdown, never a Provider payload. */
private suspend fun shareAssistantMarkdown(
    context: android.content.Context,
    transcript: PresentedTranscriptMessage,
    conversationTitle: String,
    messageSequence: Int,
) {
    val file = withContext(Dispatchers.IO) {
        val directory = java.io.File(context.cacheDir, "shared_attachments")
        if (!directory.exists() && !directory.mkdirs()) error("无法创建 Markdown 导出文件。")
        val target = java.io.File(directory, conversationMarkdownExportFileName(conversationTitle, messageSequence))
        target.writeText(presentedMessageMarkdown(transcript.message), Charsets.UTF_8)
        target
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.attachment-share", file)
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
        type = "text/markdown"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TITLE, file.name)
        clipData = android.content.ClipData.newRawUri("南枫 AI Markdown", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }, "分享"))
}

/** Exports one current-path conversation as readable Markdown, never as a raw provider archive. */
private suspend fun shareConversationMarkdown(
    context: android.content.Context,
    conversation: com.nanzhufeng.ai.domain.Conversation,
    messages: List<PresentedTranscriptMessage>,
) {
    val file = withContext(Dispatchers.IO) {
        val directory = java.io.File(context.cacheDir, "shared_attachments")
        if (!directory.exists() && !directory.mkdirs()) error("无法创建 Markdown 导出文件。")
        val target = java.io.File(directory, conversationMarkdownExportFileName(conversation.title, sequence = 1))
        target.writeText(buildString {
            append("# ")
            append(conversation.title.replace('\n', ' ').trim())
            messages.forEach { transcript ->
                append("\n\n## ")
                append(if (transcript.message.role == com.nanzhufeng.ai.domain.MessageRole.USER) "我" else "南枫 AI")
                formatTranscriptTimeOrNull(transcript.metadata.createdAt)?.let { time -> append(" · ").append(time) }
                val body = presentedMessageMarkdown(transcript.message)
                if (body.isNotBlank()) append("\n\n").append(body)
            }
            append('\n')
        }, Charsets.UTF_8)
        target
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.attachment-share", file)
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
        type = "text/markdown"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TITLE, file.name)
        clipData = android.content.ClipData.newRawUri("南枫 AI 对话 Markdown", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }, "分享"))
}

/** The user sees a semantic Markdown reconstruction; raw Provider bodies never enter this path. */
private fun presentedMessageMarkdown(message: PresentedMessage): String = message.blocks.joinToString("\n\n") { block -> when (block) {
    is PresentationBlock.Heading -> "${"#".repeat(block.level)} ${markdownInlineText(block.spans)}"
    is PresentationBlock.Paragraph -> markdownInlineText(block.spans)
    is PresentationBlock.Note -> markdownInlineText(block.spans)
    is PresentationBlock.Quote -> markdownInlineText(block.spans).lineSequence().joinToString("\n") { line -> "> $line" }
    is PresentationBlock.UnorderedList -> block.items.joinToString("\n") { item -> "${"  ".repeat(item.depth)}- ${markdownInlineText(item.spans)}" }
    is PresentationBlock.OrderedList -> block.items.joinToString("\n") { item -> "${"  ".repeat(item.depth)}${item.ordinal}. ${markdownInlineText(item.spans)}" }
    is PresentationBlock.HorizontalRule -> "---"
    is PresentationBlock.CodeFence -> "```" + (block.language ?: "") + "\n${block.code}\n```"
    is PresentationBlock.Table -> tableMarkdownText(block)
    is PresentationBlock.PlainText -> block.raw
    is PresentationBlock.AttachmentReference -> "<${attachmentKindLabel(block.attachment.mimeType)}>"
    is PresentationBlock.Reasoning -> ""
    is PresentationBlock.SafeToolSummary -> "工具结果（安全摘要）：${block.toolName} · ${block.summary}"
} }.trim()

/**
 * USER keeps authored Markdown semantics, but its visual hierarchy intentionally stays tighter
 * than the open, document-like 南枫AI reading column. This prevents one pasted Markdown heading
 * from turning a compact user bubble into a competing page headline.
 */
private data class ConversationMessageTypography(
    val headingOne: androidx.compose.ui.unit.TextUnit,
    val headingTwo: androidx.compose.ui.unit.TextUnit,
    val headingMinor: androidx.compose.ui.unit.TextUnit,
    val headingOneLineHeight: androidx.compose.ui.unit.TextUnit,
    val headingTwoLineHeight: androidx.compose.ui.unit.TextUnit,
    val headingMinorLineHeight: androidx.compose.ui.unit.TextUnit,
    val headingOneWeight: FontWeight,
    val headingTwoWeight: FontWeight,
    val headingMinorWeight: FontWeight,
    val body: androidx.compose.ui.unit.TextUnit,
    val bodyLineHeight: androidx.compose.ui.unit.TextUnit,
    val note: androidx.compose.ui.unit.TextUnit,
    val noteLineHeight: androidx.compose.ui.unit.TextUnit,
    val list: androidx.compose.ui.unit.TextUnit,
    val listLineHeight: androidx.compose.ui.unit.TextUnit,
)

private val AssistantDocumentTypography = ConversationMessageTypography(
    headingOne = 26.sp, headingTwo = 22.sp, headingMinor = 18.sp,
    headingOneLineHeight = 35.sp, headingTwoLineHeight = 31.sp, headingMinorLineHeight = 26.sp,
    headingOneWeight = FontWeight.ExtraBold, headingTwoWeight = FontWeight.Bold, headingMinorWeight = FontWeight.Bold,
    body = 16.sp, bodyLineHeight = 25.sp, note = 13.sp, noteLineHeight = 21.sp, list = 15.sp, listLineHeight = 24.sp,
)

/** User bubble scale: 15sp body, only 1–3sp between heading levels, and never ExtraBold. */
private val UserBubbleTypography = ConversationMessageTypography(
    headingOne = 18.sp, headingTwo = 17.sp, headingMinor = 16.sp,
    headingOneLineHeight = 26.sp, headingTwoLineHeight = 24.sp, headingMinorLineHeight = 23.sp,
    headingOneWeight = FontWeight.SemiBold, headingTwoWeight = FontWeight.Medium, headingMinorWeight = FontWeight.Medium,
    body = 15.sp, bodyLineHeight = 23.sp, note = 13.sp, noteLineHeight = 19.sp, list = 15.sp, listLineHeight = 23.sp,
)

/** File preview hierarchy: Markdown remains legible without page-sized heading jumps. */
private val TextPreviewTypography = ConversationMessageTypography(
    headingOne = 19.sp, headingTwo = 18.sp, headingMinor = 17.sp,
    headingOneLineHeight = 27.sp, headingTwoLineHeight = 26.sp, headingMinorLineHeight = 25.sp,
    headingOneWeight = FontWeight.SemiBold, headingTwoWeight = FontWeight.SemiBold, headingMinorWeight = FontWeight.Medium,
    body = 15.sp, bodyLineHeight = 23.sp, note = 13.sp, noteLineHeight = 20.sp, list = 15.sp, listLineHeight = 23.sp,
)

private fun markdownInlineText(spans: List<InlinePresentation>): String = spans.joinToString("") { span -> when (span) {
    is InlinePresentation.Text -> span.value
    is InlinePresentation.Strong -> "**${span.value}**"
    is InlinePresentation.Emphasis -> "*${span.value}*"
    is InlinePresentation.Code -> "`${span.value}`"
    is InlinePresentation.Link -> "[${span.label}](${span.url})"
} }

@Composable
private fun PresentationBlockView(block: PresentationBlock, attachmentPreviews: Map<AttachmentId, ConversationAttachmentPreview>, bodyColor: Color, sentAt: java.time.Instant?, assistantDocument: Boolean, onOpenImagePreview: (AttachmentId) -> Unit, onOpenPdfPreview: (AttachmentId) -> Unit, onOpenVideoPreview: (AttachmentId) -> Unit, onOpenAudioPreview: (AttachmentId) -> Unit, onOpenTextPreview: (AttachmentId) -> Unit, searchAnchorAttachmentId: AttachmentId? = null, searchAnchorRequestId: Long = 0L, findBlockIndex: Int? = null, typographyOverride: ConversationMessageTypography? = null) {
    val typography = typographyOverride ?: if (assistantDocument) AssistantDocumentTypography else UserBubbleTypography
    val findMessageId = findBlockIndex?.let { block.identity.messageId }
    val blockFindKey = findBlockIndex?.let(::conversationFindBlockKey)
    when (block) {
        is PresentationBlock.Heading -> {
            val (fontSize, lineHeight, weight) = when (block.level) {
                1 -> Triple(typography.headingOne, typography.headingOneLineHeight, typography.headingOneWeight)
                2 -> Triple(typography.headingTwo, typography.headingTwoLineHeight, typography.headingTwoWeight)
                else -> Triple(typography.headingMinor, typography.headingMinorLineHeight, typography.headingMinorWeight)
            }
            InlinePresentationText(
                spans = block.spans,
                modifier = Modifier.padding(top = if (block.level == 1) 10.dp else 6.dp, bottom = 2.dp),
                color = bodyColor,
                style = if (block.level <= 2) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                fontSize = scaledConversationTextUnit(fontSize),
                lineHeight = scaledConversationTextUnit(lineHeight),
                fontWeight = weight,
                findMessageId = findMessageId,
                findSegmentKey = blockFindKey,
            )
        }
        is PresentationBlock.Paragraph -> {
            val automationLabel = chatGptImportedAutomationLabel(conversationRawInlineText(block.spans))
            if (automationLabel != null) {
                ImportedChatGptAutomationSuggestion(automationLabel)
            } else {
                InlinePresentationText(
                    spans = block.spans,
                    color = bodyColor,
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = scaledConversationTextUnit(typography.body),
                    lineHeight = scaledConversationTextUnit(typography.bodyLineHeight),
                    findMessageId = findMessageId,
                    findSegmentKey = blockFindKey,
                )
            }
        }
        is PresentationBlock.Note -> InlinePresentationText(
            spans = block.spans,
            modifier = Modifier.padding(vertical = 2.dp),
            color = SecondaryText,
            style = MaterialTheme.typography.bodySmall,
            fontSize = scaledConversationTextUnit(typography.note),
            lineHeight = scaledConversationTextUnit(typography.noteLineHeight),
            suppressEmphasis = true,
            findMessageId = findMessageId,
            findSegmentKey = blockFindKey,
        )
        is PresentationBlock.Quote -> Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(vertical = 4.dp)) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(SecondaryText.copy(alpha = 0.42f)))
            InlinePresentationText(
                spans = block.spans,
                modifier = Modifier.padding(start = 12.dp),
                color = bodyColor,
                style = MaterialTheme.typography.bodyMedium,
                fontSize = scaledConversationTextUnit(typography.body),
                lineHeight = scaledConversationTextUnit(typography.bodyLineHeight),
                findMessageId = findMessageId,
                findSegmentKey = blockFindKey,
            )
        }
        is PresentationBlock.UnorderedList -> Column(verticalArrangement = Arrangement.spacedBy(if (assistantDocument) 8.dp else 6.dp)) {
            block.items.forEachIndexed { itemIndex, item ->
                ListItem("•", item.spans, bodyColor, item.depth, typography, findMessageId, findBlockIndex?.let { conversationFindListItemKey(it, itemIndex) })
            }
        }
        is PresentationBlock.OrderedList -> Column(verticalArrangement = Arrangement.spacedBy(if (assistantDocument) 8.dp else 6.dp)) {
            block.items.forEachIndexed { itemIndex, item ->
                ListItem("${item.ordinal}.", item.spans, bodyColor, item.depth, typography, findMessageId, findBlockIndex?.let { conversationFindListItemKey(it, itemIndex) })
            }
        }
        is PresentationBlock.HorizontalRule -> Box(
            Modifier.fillMaxWidth().padding(vertical = 12.dp).height(1.dp).background(SecondaryText.copy(alpha = 0.18f)),
        )
        is PresentationBlock.CodeFence -> CopyableInformationSurface(label = block.language ?: "可复制内容", value = block.code) {
            InlinePresentationText(
                spans = listOf(InlinePresentation.Text(block.code)),
                color = BodyText,
                fontStyle = null,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                lineHeight = scaledConversationTextUnit(20.sp),
                findMessageId = findMessageId,
                findSegmentKey = blockFindKey,
            )
        }
        is PresentationBlock.Table -> MarkdownTable(block, findBlockIndex)
        is PresentationBlock.PlainText -> {
            val automationLabel = chatGptImportedAutomationLabel(block.raw)
            if (automationLabel != null) {
                ImportedChatGptAutomationSuggestion(automationLabel)
            } else {
                InlinePresentationText(
                    spans = listOf(InlinePresentation.Text(block.raw)),
                    color = bodyColor,
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = scaledConversationTextUnit(typography.body),
                    lineHeight = scaledConversationTextUnit(typography.bodyLineHeight),
                    findMessageId = findMessageId,
                    findSegmentKey = blockFindKey,
                )
            }
        }
        is PresentationBlock.AttachmentReference -> AttachmentPreviewChip(
            preview = attachmentPreviews[block.attachment.id],
            messageNodeId = block.identity.messageId,
            displayName = block.attachment.displayName,
            mimeType = block.attachment.mimeType,
            byteCount = block.attachment.byteCount,
            sentAt = sentAt,
            onOpenImagePreview = onOpenImagePreview,
            onOpenPdfPreview = onOpenPdfPreview,
            onOpenVideoPreview = onOpenVideoPreview,
            onOpenAudioPreview = onOpenAudioPreview,
            onOpenTextPreview = onOpenTextPreview,
            searchAnchorAttachmentId = searchAnchorAttachmentId,
            searchAnchorRequestId = searchAnchorRequestId,
        )
        is PresentationBlock.Reasoning -> AssistantReasoningDisclosure(block)
        is PresentationBlock.SafeToolSummary -> InlinePresentationText(
            spans = listOf(InlinePresentation.Text(conversationSafeToolSummaryText(block))),
            color = SecondaryText,
            style = MaterialTheme.typography.bodySmall,
            findMessageId = findMessageId,
            findSegmentKey = blockFindKey,
        )
    }
}

/** A multi-line disclosure uses a rounded rectangle, never a pill-shaped card. */
@Composable
private fun AssistantReasoningDisclosure(block: PresentationBlock.Reasoning) {
    var expanded by rememberSaveable(block.identity.messageId.value, block.identity.contentBlockPosition) { mutableStateOf(false) }
    Surface(
        onClick = { expanded = !expanded },
        color = NeutralSystemSurface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().semantics {
            contentDescription = if (expanded) "收起思考过程" else "展开思考过程"
        },
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "思考过程",
                    color = SecondaryText,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    tint = SecondaryText,
                )
            }
            if (expanded) {
                Text(
                    text = block.text,
                    color = SecondaryText,
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = scaledConversationTextUnit(20.sp),
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun ListItem(marker: String, spans: List<InlinePresentation>, bodyColor: Color, depth: Int, typography: ConversationMessageTypography, findMessageId: MessageNodeId? = null, findSegmentKey: String? = null) {
    val listFontSize = scaledConversationTextUnit(typography.list)
    val listLineHeight = scaledConversationTextUnit(typography.listLineHeight)
    val listStartIndent = 12.dp + (depth.coerceIn(0, 6) * 16).dp
    Row(modifier = Modifier.fillMaxWidth().padding(start = listStartIndent)) {
        // Markers belong to their own list item, not to the paragraph/title alignment grid.
        // A narrow bounded slot keeps bullets and ordinals immediately before the first line,
        // while wrapped prose still has a small, stable hanging indent. Both sides share the
        // first text baseline: aligning their boxes to the top makes bullets and ordinals float.
        Text(
            marker,
            color = bodyColor,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            fontSize = listFontSize,
            lineHeight = listLineHeight,
            modifier = Modifier.widthIn(min = 14.dp, max = 22.dp).alignByBaseline(),
        )
        Spacer(Modifier.width(4.dp))
        InlinePresentationText(
            spans = spans,
            color = bodyColor,
            style = MaterialTheme.typography.bodyMedium,
            fontSize = listFontSize,
            lineHeight = listLineHeight,
            modifier = Modifier.weight(1f).alignByBaseline(),
            findMessageId = findMessageId,
            findSegmentKey = findSegmentKey,
        )
    }
}

/** Imported ChatGPT automation suggestions are inert historical UI, not executable raw JSON. */
@Composable
private fun ImportedChatGptAutomationSuggestion(label: String) {
    Surface(
        color = Color.Transparent,
        contentColor = BodyText,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, SecondaryText.copy(alpha = 0.34f)),
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Rounded.History, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(22.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun CopyableInformationSurface(label: String, value: String, content: @Composable () -> Unit) {
    val copyText = rememberConversationCopyTextAction()
    var copied by remember(value) { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1_200)
            copied = false
        }
    }
    Surface(
            color = NeutralAssistantSurface,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Color(0xFFD8E0E8)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 14.dp, top = 10.dp, end = 6.dp, bottom = 12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = SecondaryText, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = { copyText(value) { copied = true } }) {
                    Icon(
                        if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                        contentDescription = if (copied) "已复制" else "复制$label",
                        tint = if (copied) BrandGreen else BodyText,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            content()
        }
    }
}

@Composable
private fun MarkdownTable(block: PresentationBlock.Table, findBlockIndex: Int? = null) {
    val scrollState = rememberScrollState()
    val copyTable = rememberConversationCopyTextAction()
    var copied by remember(block.identity) { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1_200)
            copied = false
        }
    }
    val darkTable = ForegroundSurface.red < 0.5f
    val tableSurface = if (darkTable) Color(0xFF34383A) else Color(0xFFFBFCFD)
    val tableHeaderSurface = if (darkTable) Color(0xFF42484A) else Color(0xFFF0F3F6)
    val tableBorder = if (darkTable) Color(0xFF5A6264) else Color(0xFFD8E0E8)
    val tableDivider = if (darkTable) Color(0xFF555D60) else Color(0xFFDDE4EB)
    val tableCopySurface = if (darkTable) Color(0xFF4A5153) else Color.White.copy(alpha = 0.94f)
    val tableCopyContent = if (darkTable) Color.White else BodyText
    // A data table is an information structure, not a paragraph with tabs. Keep the muted
    // header, content-weighted columns and restrained chrome used by the knowledge-base reading view.
    val columnCount = block.headers.size.coerceAtLeast(1)
    val columnWeights = adaptiveTableColumnWeights(block)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val dividerWidth = 1.dp
        val tableWidth = maxWidth.coerceAtLeast(112.dp * columnCount)
        val usableWidth = tableWidth - dividerWidth * (columnCount - 1)
        val cellWidths = columnWeights.map { weight -> usableWidth * weight }
        Box(Modifier.fillMaxWidth()) {
            Surface(
                color = tableSurface,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, tableBorder),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.horizontalScroll(scrollState)) {
                    Column(Modifier.width(tableWidth)) {
                        MarkdownTableRow(
                            block.headers,
                            header = true,
                            cellWidths = cellWidths,
                            headerBackground = tableHeaderSurface,
                            dividerColor = tableDivider,
                            trailingHeaderInset = 38.dp,
                            findMessageId = findBlockIndex?.let { block.identity.messageId },
                            findSegmentKey = { cellIndex -> findBlockIndex?.let { conversationFindTableHeaderKey(it, cellIndex) } },
                        )
                        HorizontalDivider(color = tableDivider)
                        block.rows.forEachIndexed { index, row ->
                            MarkdownTableRow(
                                row,
                                header = false,
                                cellWidths = cellWidths,
                                headerBackground = tableHeaderSurface,
                                dividerColor = tableDivider,
                                findMessageId = findBlockIndex?.let { block.identity.messageId },
                                findSegmentKey = { cellIndex -> findBlockIndex?.let { conversationFindTableCellKey(it, index, cellIndex) } },
                            )
                            if (index < block.rows.lastIndex) {
                                HorizontalDivider(color = tableDivider)
                            }
                        }
                    }
                }
            }
            Surface(
                onClick = { copyTable(tableMarkdownText(block)) { copied = true } },
                color = tableCopySurface,
                contentColor = if (copied) BrandGreen else tableCopyContent,
                shape = CircleShape,
                shadowElevation = 1.dp,
                modifier = Modifier.align(Alignment.TopEnd).padding(3.dp).size(30.dp),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                        contentDescription = if (copied) "已复制" else "复制表格（保留 Markdown 格式）",
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

private fun adaptiveTableColumnWeights(block: PresentationBlock.Table): List<Float> {
    val columnCount = block.headers.size.coerceAtLeast(1)
    if (columnCount == 1) return listOf(1f)
    val allRows = listOf(block.headers) + block.rows
    val scores = (0 until columnCount).map { column ->
        allRows.maxOfOrNull { row -> row.getOrNull(column)?.tableVisualUnits() ?: 0f }?.coerceAtLeast(4f) ?: 4f
    }
    val total = scores.sum().coerceAtLeast(1f)
    if (columnCount == 2) {
        val first = (scores.first() / total).coerceIn(0.30f, 0.55f)
        return listOf(first, 1f - first)
    }
    val minimum = (0.16f).coerceAtMost(0.8f / columnCount)
    val distributable = 1f - minimum * columnCount
    return scores.map { score -> minimum + score / total * distributable }
}

private fun List<InlinePresentation>.tableVisualUnits(): Float = inlineText(this).text.sumOf { character ->
    when {
        character.isWhitespace() -> 0.2
        character.code in 0x2E80..0x9FFF -> 1.0
        else -> 0.58
    }
}.toFloat()

@Composable
private fun MarkdownTableRow(
    cells: List<List<InlinePresentation>>,
    header: Boolean,
    cellWidths: List<androidx.compose.ui.unit.Dp>,
    headerBackground: Color,
    dividerColor: Color,
    trailingHeaderInset: androidx.compose.ui.unit.Dp = 0.dp,
    findMessageId: MessageNodeId? = null,
    findSegmentKey: (Int) -> String? = { null },
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(if (header) headerBackground else Color.Transparent),
    ) {
        cells.forEachIndexed { index, cell ->
            val endPadding = 10.dp + if (header && index == cells.lastIndex) trailingHeaderInset else 0.dp
            Box(
                modifier = Modifier
                    .width(cellWidths.getOrElse(index) { cellWidths.last() })
                    .fillMaxHeight()
                    .padding(start = 10.dp, top = 10.dp, end = endPadding, bottom = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                InlinePresentationText(
                    spans = cell,
                    color = BodyText,
                    style = if (header) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium,
                    fontWeight = if (header) FontWeight.Bold else FontWeight.Normal,
                    fontSize = scaledConversationTextUnit(if (header) 14.sp else 15.sp),
                    lineHeight = scaledConversationTextUnit(23.sp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                    findMessageId = findMessageId,
                    findSegmentKey = findSegmentKey(index),
                )
            }
            if (index < cells.lastIndex) {
                VerticalDivider(
                    modifier = Modifier.fillMaxHeight(),
                    color = dividerColor,
                )
            }
        }
    }
}

private fun tablePlainText(block: PresentationBlock.Table): String = buildList {
    add(block.headers.joinToString("\t") { inlineText(it).text })
    block.rows.forEach { row -> add(row.joinToString("\t") { inlineText(it).text }) }
}.joinToString("\n")

private fun tableMarkdownText(block: PresentationBlock.Table): String = buildString {
    fun appendRow(cells: List<List<InlinePresentation>>) {
        append("| ")
        append(cells.joinToString(" | ") { spans -> spans.tableMarkdownCell() })
        append(" |\n")
    }
    appendRow(block.headers)
    append("| ")
    append(block.headers.joinToString(" | ") { "---" })
    append(" |\n")
    block.rows.forEach(::appendRow)
}.trimEnd()

private fun List<InlinePresentation>.tableMarkdownCell(): String = joinToString("") { span -> when (span) {
    is InlinePresentation.Text -> span.value
    is InlinePresentation.Strong -> "**${span.value}**"
    is InlinePresentation.Emphasis -> "*${span.value}*"
    is InlinePresentation.Code -> "`${span.value}`"
    is InlinePresentation.Link -> "[${span.label}](${span.url})"
}.replace("|", "\\|").replace("\n", " ") }

private const val SourceShortcutInlineContentId = "message-source-shortcut"
private const val ChatGptImportedMarkerAnnotationTag = "chatgpt-imported-marker"

private fun AnnotatedString.Builder.appendChatGptImportedText(value: String) {
    val markers = chatGptImportedMarkers(value)
    if (markers.isEmpty()) {
        append(value)
        return
    }
    var cursor = 0
    markers.forEach { marker ->
        if (marker.start > cursor) append(value.substring(cursor, marker.start))
        pushStringAnnotation(ChatGptImportedMarkerAnnotationTag, marker.inlineContentId)
        appendInlineContent(marker.inlineContentId, marker.label)
        pop()
        cursor = marker.endExclusive
    }
    if (cursor < value.length) append(value.substring(cursor))
}

private fun inlineText(
    spans: List<InlinePresentation>,
    appendSourceShortcut: Boolean = false,
    suppressEmphasis: Boolean = false,
    highlightQuery: String? = null,
) = buildAnnotatedString {
    spans.forEach { span -> when (span) {
        is InlinePresentation.Text -> appendChatGptImportedText(span.value)
        is InlinePresentation.Strong -> withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) { appendChatGptImportedText(span.value) }
        is InlinePresentation.Emphasis -> if (suppressEmphasis) appendChatGptImportedText(span.value) else withStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) { appendChatGptImportedText(span.value) }
        is InlinePresentation.Code -> {
            pushStringAnnotation(InlineCodeChipAnnotationTag, span.value)
            withStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)) { appendChatGptImportedText(span.value) }
            pop()
        }
        is InlinePresentation.Link -> if (!appendSourceShortcut) withLink(
            LinkAnnotation.Url(
                url = span.url,
            styles = TextLinkStyles(style = SpanStyle(color = BrandGreen, fontWeight = FontWeight.SemiBold)),
            ),
        ) { appendChatGptImportedText("${span.label} ↗") }
    } }
    if (appendSourceShortcut && spans.any { it is InlinePresentation.Link }) {
        append(" ")
        appendInlineContent(SourceShortcutInlineContentId, "来源")
    }
    conversationFindOccurrenceStarts(toAnnotatedString().text, highlightQuery.orEmpty()).forEach { matchStart ->
        val matchEnd = matchStart + highlightQuery.orEmpty().trim().length
        addStringAnnotation(ConversationFindHighlightAnnotationTag, highlightQuery.orEmpty().trim(), matchStart, matchEnd)
        addStyle(SpanStyle(color = AccentOrange, fontWeight = FontWeight.Bold), matchStart, matchEnd)
    }
}

private const val ConversationFindHighlightAnnotationTag = "conversation-find-highlight"

@Composable
private fun InlinePresentationText(
    spans: List<InlinePresentation>,
    color: Color,
    style: TextStyle,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null,
    fontStyle: androidx.compose.ui.text.font.FontStyle? = null,
    fontSize: androidx.compose.ui.unit.TextUnit = style.fontSize,
    lineHeight: androidx.compose.ui.unit.TextUnit = style.lineHeight,
    textAlign: androidx.compose.ui.text.style.TextAlign? = null,
    suppressEmphasis: Boolean = false,
    findMessageId: MessageNodeId? = null,
    findSegmentKey: String? = null,
) {
    val sources = spans.filterIsInstance<InlinePresentation.Link>().distinctBy { it.url }
    val importedMarkers = spans.flatMap { span ->
        val value = when (span) {
            is InlinePresentation.Text -> span.value
            is InlinePresentation.Strong -> span.value
            is InlinePresentation.Emphasis -> span.value
            is InlinePresentation.Code -> span.value
            is InlinePresentation.Link -> span.label
        }
        chatGptImportedMarkers(value)
    }.distinctBy { it.inlineContentId }
    val findQuery = LocalConversationFindQuery.current
    val text = inlineText(
        spans,
        appendSourceShortcut = sources.isNotEmpty(),
        suppressEmphasis = suppressEmphasis,
        highlightQuery = findQuery,
    )
    var textLayoutResult by remember(text) { mutableStateOf<TextLayoutResult?>(null) }
    val findTarget = LocalConversationFindTarget.current
    val selectedOccurrenceIndex = findTarget?.match?.takeIf { match ->
        match.messageId == findMessageId && match.segmentKey == findSegmentKey
    }?.occurrenceIndex
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val selectedHighlightStrength = remember { Animatable(0f) }
    val density = LocalDensity.current
    LaunchedEffect(findTarget?.requestId, selectedOccurrenceIndex, textLayoutResult) {
        val selectedIndex = selectedOccurrenceIndex ?: return@LaunchedEffect
        val layout = textLayoutResult ?: return@LaunchedEffect
        val matchRange = text.getStringAnnotations(ConversationFindHighlightAnnotationTag, 0, text.length)
            .getOrNull(selectedIndex)
            ?: return@LaunchedEffect
        if (matchRange.start >= matchRange.end) return@LaunchedEffect
        val firstLine = layout.getLineForOffset(matchRange.start)
        val lastLine = layout.getLineForOffset(matchRange.end - 1)
        val firstGlyph = layout.getBoundingBox(matchRange.start)
        val lastGlyph = layout.getBoundingBox(matchRange.end - 1)
        val matchRect = androidx.compose.ui.geometry.Rect(
            left = minOf(firstGlyph.left, lastGlyph.left),
            top = layout.getLineTop(firstLine) - with(density) { 104.dp.toPx() },
            right = maxOf(firstGlyph.right, lastGlyph.right),
            bottom = layout.getLineBottom(lastLine) + with(density) { 144.dp.toPx() },
        )
        bringIntoViewRequester.bringIntoView(matchRect)
        selectedHighlightStrength.snapTo(1f)
        selectedHighlightStrength.animateTo(0f, animationSpec = tween(durationMillis = 1100, easing = LinearOutSlowInEasing))
    }
    val inlineContent = buildMap {
        if (sources.isNotEmpty()) put(
            SourceShortcutInlineContentId,
            InlineTextContent(
                Placeholder(
                    width = sourceShortcutWidth(sources),
                    height = 28.sp,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
                ),
            ) { SourceLinkShortcut(sources) },
        )
        importedMarkers.forEach { marker ->
            put(
                marker.inlineContentId,
                InlineTextContent(
                    Placeholder(
                        width = ((marker.label.length * 7 + 38).coerceIn(68, 150)).sp,
                        height = 28.sp,
                        placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
                    ),
                ) { ImportedChatGptMarkerChip(marker.label) },
            )
        }
    }
    Text(
        text = text,
        modifier = modifier
            .bringIntoViewRequester(bringIntoViewRequester)
            .inlineCodeChipBackgrounds(text, textLayoutResult)
            .conversationFindHighlightBackgrounds(text, textLayoutResult, selectedOccurrenceIndex, selectedHighlightStrength.value),
        color = color,
        style = style,
        fontWeight = fontWeight,
        fontStyle = fontStyle,
        fontSize = fontSize,
        lineHeight = lineHeight,
        textAlign = textAlign,
        inlineContent = inlineContent,
        onTextLayout = { textLayoutResult = it },
    )
}

private fun Modifier.inlineCodeChipBackgrounds(
    text: androidx.compose.ui.text.AnnotatedString,
    layoutResult: TextLayoutResult?,
): Modifier = drawBehind {
    val layout = layoutResult ?: return@drawBehind
    text.getStringAnnotations(InlineCodeChipAnnotationTag, 0, text.length).forEach { codeRange ->
        if (codeRange.start >= codeRange.end) return@forEach
        val firstLine = layout.getLineForOffset(codeRange.start)
        val lastLine = layout.getLineForOffset(codeRange.end - 1)
        for (line in firstLine..lastLine) {
            val segmentStart = maxOf(codeRange.start, layout.getLineStart(line))
            val segmentEnd = minOf(codeRange.end, layout.getLineEnd(line))
            if (segmentStart >= segmentEnd) continue
            val firstGlyph = layout.getBoundingBox(segmentStart)
            val lastGlyph = layout.getBoundingBox(segmentEnd - 1)
            val horizontalInset = 3.dp.toPx()
            val verticalInset = 2.dp.toPx()
            val left = minOf(firstGlyph.left, lastGlyph.left) - horizontalInset
            val right = maxOf(firstGlyph.right, lastGlyph.right) + horizontalInset
            val top = layout.getLineTop(line) + verticalInset
            val bottom = layout.getLineBottom(line) - verticalInset
            drawRoundRect(
                color = InlineCodeChipBackground,
                topLeft = Offset(left, top),
                size = Size((right - left).coerceAtLeast(0f), (bottom - top).coerceAtLeast(0f)),
                cornerRadius = CornerRadius(8.dp.toPx()),
            )
        }
    }
}

/** Search text gets a small neutral pill behind each rendered line segment, not a rectangular band. */
private fun Modifier.conversationFindHighlightBackgrounds(
    text: androidx.compose.ui.text.AnnotatedString,
    layoutResult: TextLayoutResult?,
    selectedOccurrenceIndex: Int?,
    selectedHighlightStrength: Float,
): Modifier = drawBehind {
    val layout = layoutResult ?: return@drawBehind
    text.getStringAnnotations(ConversationFindHighlightAnnotationTag, 0, text.length).forEachIndexed { index, matchRange ->
        if (matchRange.start >= matchRange.end) return@forEachIndexed
        val firstLine = layout.getLineForOffset(matchRange.start)
        val lastLine = layout.getLineForOffset(matchRange.end - 1)
        for (line in firstLine..lastLine) {
            val segmentStart = maxOf(matchRange.start, layout.getLineStart(line))
            val segmentEnd = minOf(matchRange.end, layout.getLineEnd(line))
            if (segmentStart >= segmentEnd) continue
            val firstGlyph = layout.getBoundingBox(segmentStart)
            val lastGlyph = layout.getBoundingBox(segmentEnd - 1)
            val horizontalInset = 4.dp.toPx()
            val verticalInset = 2.dp.toPx()
            val left = minOf(firstGlyph.left, lastGlyph.left) - horizontalInset
            val right = maxOf(firstGlyph.right, lastGlyph.right) + horizontalInset
            val top = layout.getLineTop(line) + verticalInset
            val bottom = layout.getLineBottom(line) - verticalInset
            val isSelected = index == selectedOccurrenceIndex
            drawRoundRect(
                brush = if (isSelected) {
                    Brush.horizontalGradient(
                        listOf(
                            AccentOrange.copy(alpha = 0.20f + selectedHighlightStrength * 0.20f),
                            BrandGreen.copy(alpha = 0.14f + selectedHighlightStrength * 0.18f),
                        ),
                    )
                } else {
                    Brush.linearGradient(listOf(SecondaryText.copy(alpha = 0.26f), SecondaryText.copy(alpha = 0.26f)))
                },
                topLeft = Offset(left, top),
                size = Size((right - left).coerceAtLeast(0f), (bottom - top).coerceAtLeast(0f)),
                cornerRadius = CornerRadius(9.dp.toPx()),
            )
        }
    }
}

private fun sourceShortcutWidth(sources: List<InlinePresentation.Link>): androidx.compose.ui.unit.TextUnit =
    if (sources.size > 1) 84.sp else ((sourceShortcutLabel(sources).length * 7 + 34).coerceIn(72, 156)).sp

private fun sourceShortcutLabel(sources: List<InlinePresentation.Link>): String {
    val primary = sources.firstOrNull()?.sourceSiteName().orEmpty().ifBlank { "网站" }
    return if (sources.size > 1) "来源" else primary
}

/** Historical ChatGPT references may lack exported URLs; keep their source shape without faking a link. */
@Composable
private fun ImportedChatGptMarkerChip(label: String) {
    Surface(
        color = NeutralSystemSurface,
        contentColor = SecondaryText,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, NeutralBorder.copy(alpha = 0.58f)),
        modifier = Modifier.height(28.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(Icons.Rounded.Language, contentDescription = null, tint = BrandGreen, modifier = Modifier.size(15.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium, maxLines = 1)
        }
    }
}

private fun InlinePresentation.Link.sourceHost(): String =
    Uri.parse(url).host?.removePrefix("www.").orEmpty()

private fun InlinePresentation.Link.sourceSiteName(): String {
    val host = sourceHost()
    return when {
        host.endsWith("eastmoney.com") -> "东方财富"
        host == "etf.run" -> "ETF 数据"
        host.endsWith("whatsid.me") -> "ETF 估值"
        host.endsWith("lixinger.com") -> "理杏仁"
        host.endsWith("goodmoney.club") -> "好买基金"
        host.endsWith("hangyan.co") -> "行研社"
        else -> when (host.substringBefore('.')) {
        "openai" -> "OpenAI"
        "anthropic" -> "Anthropic"
        "github" -> "GitHub"
        "google" -> "Google"
            else -> "外部网站"
        }
    }
}

private fun InlinePresentation.Link.sourceDisplayTitle(): String {
    val candidate = label.trim()
    return if (candidate.isNotBlank() && !candidate.isWebsiteAddressLabel()) candidate else sourceSiteName()
}

/** A URL or bare domain is never a useful first-line source title. */
private fun String.isWebsiteAddressLabel(): Boolean =
    startsWith("http://", ignoreCase = true) ||
        startsWith("https://", ignoreCase = true) ||
        matches(Regex("^[a-z0-9-]+(?:\\.[a-z0-9-]+)+(?:/.*)?$", RegexOption.IGNORE_CASE))

private fun InlinePresentation.Link.sourceSiteColor(): Color = when (sourceHost().substringBefore('.')) {
    "openai" -> Color(0xFF10A37F)
    "anthropic" -> Color(0xFFB86649)
    "github" -> Color(0xFF24292F)
    "google" -> Color(0xFF4285F4)
    else -> Color(0xFF64748B)
}

@Composable
private fun SourceWebsiteGlyph(source: InlinePresentation.Link, size: androidx.compose.ui.unit.Dp = 18.dp) {
    Surface(color = source.sourceSiteColor(), shape = CircleShape, modifier = Modifier.size(size)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Language, contentDescription = null, modifier = Modifier.size(size * 0.62f), tint = Color.White)
        }
    }
}

@Composable
private fun OverlappingSourceWebsiteGlyphs(sources: List<InlinePresentation.Link>) {
    val visibleSources = sources.take(3)
    val glyphOffset = 11.dp
    val glyphSize = 18.dp
    val railWidth = glyphSize + glyphOffset * (visibleSources.size - 1)
    Box(modifier = Modifier.width(railWidth).height(glyphSize)) {
        visibleSources.forEachIndexed { index, source ->
            Box(Modifier.offset(x = glyphOffset * index)) {
                SourceWebsiteGlyph(source, size = glyphSize)
            }
        }
    }
}

@Composable
private fun SourceLinkShortcut(sources: List<InlinePresentation.Link>) {
    var visible by remember { mutableStateOf(false) }
    val primary = sources.first()
    Surface(
        onClick = { visible = true },
        color = NeutralSystemSurface,
        contentColor = BodyText,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, NeutralBorder.copy(alpha = 0.58f)),
        modifier = Modifier.height(28.dp).semantics { contentDescription = "打开 ${sources.size} 个来源网站" },
    ) {
        Row(
            modifier = Modifier.padding(start = 5.dp, end = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (sources.size > 1) OverlappingSourceWebsiteGlyphs(sources) else SourceWebsiteGlyph(primary)
            Text(sourceShortcutLabel(sources), fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    if (visible) SourceLinksDialog(sources = sources, onDismiss = { visible = false })
}

@Composable
private fun SourceLinksDialog(sources: List<InlinePresentation.Link>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true, dismissOnClickOutside = true),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            DismissibleDialogBackdrop(onDismiss)
            Surface(
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                color = ForegroundSurface,
                contentColor = BodyText,
                shape = RoundedCornerShape(24.dp),
                shadowElevation = 8.dp,
            ) {
                Column(Modifier.padding(start = 24.dp, top = 22.dp, end = 24.dp, bottom = 12.dp)) {
                    Text("来源网站", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Column(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).padding(top = 18.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        sources.distinctBy { it.url }.forEach { source ->
                            Surface(
                                onClick = {
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(source.url)).addCategory(Intent.CATEGORY_BROWSABLE))
                                    }.onSuccess { onDismiss() }.onFailure {
                                        Toast.makeText(context, "未找到可打开网站的系统浏览器。", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                color = NeutralAssistantSurface,
                                contentColor = BodyText,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    SourceWebsiteGlyph(source, size = 24.dp)
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            source.sourceDisplayTitle(),
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(source.url, style = MaterialTheme.typography.bodySmall, color = SecondaryText, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = "在系统浏览器打开${source.sourceDisplayTitle()}", modifier = Modifier.size(18.dp), tint = SecondaryText)
                                }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDismiss) { Text("关闭") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationActions(
    state: ConversationFoundationUiState,
    onStartFixture: () -> Unit,
    onStartFailureFixture: () -> Unit,
    onAction: (ConversationActionKind, ModelPresetId?) -> Unit,
    onSwitchBranch: (com.nanzhufeng.ai.domain.MessageNodeId) -> Unit,
    onChooseModel: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
        state.recovery?.let { recovery ->
            Text(recovery.title, style = MaterialTheme.typography.labelLarge)
            Text(recovery.detail, style = MaterialTheme.typography.bodySmall, color = SecondaryText)
            if (recovery.canContinue) OutlinedButton(onClick = { onAction(ConversationActionKind.CONTINUE, null) }, shape = RoundedCornerShape(14.dp)) { Text("继续部分输出") }
            if (recovery.canRetry) {
                OutlinedButton(onClick = { onAction(ConversationActionKind.RETRY, null) }, shape = RoundedCornerShape(14.dp)) { Text("重试（新回答版本）") }
                Button(onClick = onChooseModel, shape = RoundedCornerShape(14.dp)) { Text("换模型重答") }
            }
        }
        if (state.runtime == null || state.runtime.isTerminal) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onStartFixture, shape = RoundedCornerShape(14.dp)) { Text("运行本地 fixture") }
                OutlinedButton(onClick = onStartFailureFixture, shape = RoundedCornerShape(14.dp)) { Text("本地失败示例") }
            }
        }
        if (state.branchLeaves.size > 1) {
            Text("分支历史", style = MaterialTheme.typography.labelLarge, color = SecondaryText)
            state.branchLeaves.forEach { branch ->
                val role = if (branch.role == com.nanzhufeng.ai.domain.MessageRole.USER) "用户" else "助手"
                OutlinedButton(onClick = { onSwitchBranch(branch.leafId) }, enabled = !branch.isCurrent && state.runtime?.isTerminal != false, shape = RoundedCornerShape(14.dp)) {
                    Text(if (branch.isCurrent) "当前：$role · ${branch.label}" else "切回：$role · ${branch.label}")
                }
            }
        }
    }
}

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
private fun DraftComposer(
    state: ConversationFoundationUiState,
    onEnsureAttachmentPreview: (com.nanzhufeng.ai.domain.ConversationAttachmentReference) -> Unit,
    onDraftChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onStop: () -> Unit,
    onRemoveAttachment: (AttachmentId) -> Unit,
    onOpenImagePreview: (AttachmentId) -> Unit,
    onOpenPdfPreview: (AttachmentId) -> Unit,
    onOpenVideoPreview: (AttachmentId) -> Unit,
    onOpenAudioPreview: (AttachmentId) -> Unit,
    onOpenTextPreview: (AttachmentId) -> Unit,
    onToggleAttachments: () -> Unit,
    onToggleModel: () -> Unit,
    onAddAnchorChanged: (androidx.compose.ui.geometry.Rect) -> Unit,
    onModelAnchorChanged: (androidx.compose.ui.geometry.Rect) -> Unit,
) {
    val draft = state.draft ?: return
    // A draft attachment is already a private, verified local asset. Request the same bounded
    // projection used by transcript rows as soon as it enters the composer; never wait until
    // after sending, and never replace an unavailable local cover with a fake image.
    LaunchedEffect(draft.attachments) {
        draft.attachments.forEach(onEnsureAttachmentPreview)
    }
    val canStopRuntime = state.runtime?.isTerminal == false
    val canSubmit = !state.isSending && (state.surface != com.nanzhufeng.ai.domain.ConversationSurface.CHAT || state.normalChatRouting != null) && (draft.text.isNotBlank() || draft.attachments.isNotEmpty())
    val manualId = state.p6gConversationOverride?.modelId ?: state.p6gGlobalDefault.modelId
    val selectedPresets = state.normalChatRouting?.presets(draft, manualId)
        ?: com.nanzhufeng.ai.domain.ComposerModelRoutingCatalog.choice(manualId).routes
    val windowWidth = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
    val modelLabel = if (windowWidth >= ComposerModelExpandedBreakpoint) {
        composerModelFullDisplayLabel(selectedPresets)
    } else {
        composerModelDisplayLabel(selectedPresets)
    }
    ConversationComposerDock(
        value = draft.text,
        onValueChange = onDraftChanged,
        onOpenAttachments = onToggleAttachments,
        attachmentsEnabled = !state.isSending && !canStopRuntime && draft.attachments.size < 4,
        attachmentDescription = "添加到草稿",
        onAttachmentAnchorChanged = onAddAnchorChanged,
        attachmentPreview = if (draft.attachments.isNotEmpty()) {
            {
                Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        draft.attachments.forEach { attachment ->
                            ComposerAttachmentPreview(
                                preview = state.attachmentPreviews[attachment.id],
                                displayName = attachment.displayName,
                                mimeType = attachment.mimeType,
                                byteCount = attachment.byteCount,
                                onRemove = { onRemoveAttachment(attachment.id) },
                                onOpenImagePreview = onOpenImagePreview,
                                onOpenPdfPreview = onOpenPdfPreview,
                                onOpenVideoPreview = onOpenVideoPreview,
                                onOpenAudioPreview = onOpenAudioPreview,
                                onOpenTextPreview = onOpenTextPreview,
                            )
                        }
                    }
            }
        } else null,
    ) {
        ComposerModelEntry(
            label = modelLabel,
            enabled = !state.isSending,
            onClick = onToggleModel,
            onAnchorChanged = onModelAnchorChanged,
        )
        ComposerSendButton(onClick = if (canStopRuntime) onStop else onSubmit, enabled = canStopRuntime || canSubmit, isSending = state.isSending, showStop = canStopRuntime, contentDescription = "发送消息")
    }
}

/** One model affordance for normal and temporary conversations; only their persistence owner differs. */
@Composable
private fun ComposerModelEntry(
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
    onAnchorChanged: (androidx.compose.ui.geometry.Rect) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val shape = RoundedCornerShape(50)
    val windowWidth = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
    val modelWidth = if (windowWidth >= ComposerModelExpandedBreakpoint) {
        ComposerModelExpandedDisplayWidth
    } else {
        ComposerModelCompactDisplayWidth
    }
    Box(
        modifier = Modifier.width(modelWidth).height(48.dp)
            .onGloballyPositioned { onAnchorChanged(it.boundsInRoot()) }
            .clip(shape)
            .combinedClickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = null,
            )
            .semantics { contentDescription = "选择模型：$label" },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().height(36.dp),
            shape = shape,
            color = if (pressed) NeutralSystemSurface else Color.Transparent,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    label,
                    fontSize = 13.sp,
                    fontFamily = ComposerModelRoundedBoldFontFamily,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** The one measured Composer implementation shared by normal and temporary conversations. */
@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
private fun ConversationComposerDock(
    value: String,
    onValueChange: (String) -> Unit,
    onOpenAttachments: () -> Unit,
    attachmentsEnabled: Boolean,
    attachmentDescription: String,
    onAttachmentAnchorChanged: (androidx.compose.ui.geometry.Rect) -> Unit,
    inputDescription: String = "会话草稿",
    attachmentPreview: (@Composable () -> Unit)? = null,
    trailingContent: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    var inputFocused by remember { mutableStateOf(false) }
    val imeVisible = WindowInsets.isImeVisible
    // The visible branch follows the insets directly.  `LaunchedEffect` is only state cleanup;
    // it must not be allowed to leave one frame of the expanded editor after Back hides the IME.
    val inputExpanded = inputFocused && imeVisible
    // Android keeps an EditText focused after the user dismisses the keyboard with Back.  This
    // composer deliberately treats visible IME as its expanded-editing state: once the keyboard
    // is gone, replace the expanded native editor with the compact single-line field while
    // keeping the draft text intact.
    LaunchedEffect(imeVisible) {
        if (!imeVisible) inputFocused = false
    }
    // Draft previews are intentionally inside this single white surface. The root overlay menu
    // remains a sibling, so opening it never contributes to the dock's measurement or IME path.
    Surface(
        modifier = Modifier.fillMaxWidth().conversationForegroundShadow(
            shape = RoundedCornerShape(28.dp),
            shadow = ConversationComposerForegroundShadow,
        ),
        color = ForegroundSurface,
        shape = RoundedCornerShape(28.dp),
        shadowElevation = 0.dp,
    ) {
        Column {
            attachmentPreview?.invoke()
            if (inputExpanded) {
                // Editing gets its own full-width lane. The bottom row remains a stable action
                // strip, so a long question never has to compete with attachment/model/send.
                ComposerDraftTextField(
                    value = value,
                    onValueChange = onValueChange,
                    inputDescription = inputDescription,
                    expanded = true,
                    onFocusChanged = { inputFocused = it },
                    modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 10.dp),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ComposerDockMinimumHeight)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onOpenAttachments,
                    enabled = attachmentsEnabled,
                    modifier = Modifier.size(48.dp).onGloballyPositioned { onAttachmentAnchorChanged(it.boundsInRoot()) },
                    shape = CircleShape,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_nanfeng_composer_add_rounded),
                        contentDescription = attachmentDescription,
                        modifier = Modifier.size(ComposerAddGlyphSize),
                        tint = ConversationControlGlyph,
                    )
                }
                if (!inputExpanded) {
                    ComposerDraftTextField(
                        value = value,
                        onValueChange = onValueChange,
                        inputDescription = inputDescription,
                    expanded = false,
                        // This field is removed as the full-width editor appears; its inevitable
                        // focus-loss callback must not immediately collapse that new editor.
                        onFocusChanged = { focused -> if (focused) inputFocused = true },
                        modifier = Modifier.weight(1f).p5aKeyboardTraversal(),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                trailingContent()
            }
        }
    }
}

/** Draft attachments reuse the transcript's local preview geometry; only removal is draft-only. */
@Composable
private fun ComposerAttachmentPreview(
    preview: ConversationAttachmentPreview?,
    displayName: String?,
    mimeType: String,
    byteCount: Long,
    onRemove: () -> Unit,
    onOpenImagePreview: (AttachmentId) -> Unit,
    onOpenPdfPreview: (AttachmentId) -> Unit,
    onOpenVideoPreview: (AttachmentId) -> Unit,
    onOpenAudioPreview: (AttachmentId) -> Unit,
    onOpenTextPreview: (AttachmentId) -> Unit,
) {
    val isImage = mimeType.startsWith("image/")
    val isPdf = mimeType == "application/pdf"
    val isVideo = mimeType == "video/mp4"
    val isAudio = mimeType in com.nanzhufeng.ai.domain.CONVERSATION_ALLOWED_AUDIO_MIME_TYPES
    // Draft previews and persisted conversation attachments must make the same
    // capability decision as search/open: MIME first, then a safe filename fallback.
    val isText = com.nanzhufeng.ai.domain.isSafeTextAttachment(mimeType, displayName)
    val previewCorner = if (isVideo) 20.dp else 10.dp
    val bitmap = rememberDecodedBitmap(preview?.thumbnail?.bytes, preview?.id)
    // Keep the exact same content bounds as a sent attachment. This makes a PDF first page,
    // text excerpt, audio cue or video poster readable before send instead of turning all
    // formats into one generic attachment icon.
    val previewModifier = when {
        isVideo -> Modifier.widthIn(min = 144.dp, max = 220.dp).height(128.dp)
        isImage -> originalAspectPreviewModifier(bitmap, maxEdge = 220.dp, fallbackSize = 92.dp)
        isAudio -> Modifier.width(176.dp).height(42.dp)
        isText -> Modifier.widthIn(min = 164.dp, max = 228.dp).heightIn(min = 96.dp, max = 128.dp)
        else -> Modifier.size(92.dp)
    }.clip(RoundedCornerShape(previewCorner))
    var attachmentInfoVisible by rememberSaveable("composer-attachment-info-${preview?.id?.value ?: displayName}") { mutableStateOf(false) }
    var attachmentBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var attachmentPressPosition by remember { mutableStateOf<androidx.compose.ui.geometry.Offset?>(null) }
    val openAttachment = {
        preview?.id?.let { id ->
            when {
                isImage -> onOpenImagePreview(id)
                isPdf -> onOpenPdfPreview(id)
                isVideo -> onOpenVideoPreview(id)
                isAudio -> onOpenAudioPreview(id)
                isText -> onOpenTextPreview(id)
            }
        }
    }
    Box(
        modifier = previewModifier
            .onGloballyPositioned { attachmentBounds = it.boundsInRoot() }
            .pointerInput(preview?.id, mimeType) {
                detectTapGestures(
                    onTap = { openAttachment() },
                    onLongPress = { pressPosition ->
                        attachmentPressPosition = pressPosition
                        attachmentInfoVisible = true
                    },
                )
            }
            .semantics { contentDescription = "${attachmentKindLabel(mimeType)}附件，点击预览，长按查看信息" },
    ) {
        if (isAudio) {
            AudioMessageAttachmentPlayer(
                mimeType = mimeType,
                durationMillis = preview?.audioDurationMillis,
                modifier = Modifier.fillMaxSize(),
            )
        } else Box(
            Modifier.fillMaxSize().then(
                if (isVideo) Modifier.border(1.dp, Color.Black.copy(alpha = 0.18f), RoundedCornerShape(previewCorner)) else Modifier,
            ),
        ) {
            if (bitmap != null) Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = if (isVideo) "草稿视频代表帧" else "草稿图片预览",
                contentScale = if (isVideo) ContentScale.Crop else ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            ) else Box(
                Modifier.fillMaxSize()
                    .background(if (isPdf || isText) ForegroundSurface else NeutralAssistantSurface)
                    .border(if (isPdf || isText) 1.dp else 0.dp, Color(0x141E2925), RoundedCornerShape(10.dp)),
            ) {
                val textPreview = preview?.textPreview
                when {
                    isText && textPreview?.text != null -> AttachmentTextSnippet(textPreview.text, mimeType, Modifier.fillMaxSize())
                    isText -> AttachmentTextPreviewUnavailable(
                        mimeType = mimeType,
                        reason = textPreview?.unavailableReason ?: preview?.unavailableReason ?: "文本预览不可用",
                        modifier = Modifier.fillMaxSize(),
                    )
                    else -> Icon(
                        imageVector = if (isImage) Icons.Rounded.AddPhotoAlternate else Icons.Rounded.AttachFile,
                        contentDescription = if (isImage) "草稿图片预览不可用" else "草稿附件",
                        tint = SecondaryText,
                        modifier = Modifier.align(Alignment.Center).size(24.dp),
                    )
                }
            }
            if (isVideo) VideoAttachmentOverlay(preview?.videoDurationMillis)
        }
        IconButton(
            onClick = onRemove,
            modifier = Modifier.align(Alignment.TopEnd).padding(3.dp).size(28.dp),
        ) {
            Surface(color = ForegroundSurface, shape = CircleShape, shadowElevation = 2.dp) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "移除草稿附件${displayName?.let { "：$it" }.orEmpty()}",
                    tint = BodyText,
                    modifier = Modifier.padding(5.dp).size(18.dp),
                )
            }
        }
        attachmentBounds?.let { bounds ->
            attachmentPressPosition?.takeIf { attachmentInfoVisible }?.let { pressPosition ->
                AttachmentInfoPopup(
                    attachmentId = preview?.id ?: return@let,
                    messageNodeId = null,
                    anchorBounds = bounds,
                    pressPosition = pressPosition,
                    displayName = displayName,
                    mimeType = mimeType,
                    byteCount = byteCount,
                    durationMillis = preview?.videoDurationMillis,
                    sentAt = null,
                    isDraft = true,
                    onDismiss = { attachmentInfoVisible = false },
                )
            }
        }
    }
}

/**
 * This is intentionally a sibling of the whole conversation Column, not a [Popup] and not a
 * child of [DraftComposer]. A [Popup] creates a separate focus/window path on Android; that was
 * the source of the visible dock jump. This layer has a fixed parent size and uses placement-only
 * offsets, therefore it cannot participate in the composer's measurement or IME negotiation.
 */
@Composable
private fun ComposerMenuOverlay(
    menu: ComposerMenu,
    addAnchor: androidx.compose.ui.geometry.Rect?,
    modelAnchor: androidx.compose.ui.geometry.Rect?,
    attachmentActions: List<ComposerAttachmentAction>,
    conversationWebSearchEnabled: Boolean? = null,
    onToggleConversationWebSearch: ((Boolean) -> Unit)? = null,
    conversationStyle: com.nanzhufeng.ai.domain.ConversationStyle? = null,
    onSelectConversationStyle: ((com.nanzhufeng.ai.domain.ConversationStyle) -> Unit)? = null,
    modelOptions: List<Pair<String?, String>>,
    selectedModelId: String?,
    onDismiss: () -> Unit,
    onSelectModel: ((String?) -> Unit)? = null,
) {
    if (menu == ComposerMenu.NONE) return
    var selectedSlot by remember(menu) { mutableStateOf<com.nanzhufeng.ai.domain.ComposerModelSlot?>(null) }
    var styleMenuVisible by remember(menu) { mutableStateOf(false) }
    val onOverlayBack: () -> Unit = {
        when {
            menu == ComposerMenu.MODEL && selectedSlot != null -> selectedSlot = null
            menu == ComposerMenu.ATTACHMENTS && styleMenuVisible -> styleMenuVisible = false
            else -> onDismiss()
        }
    }
    // An overlay is always the first Back consumer. A model child page returns to its root
    // before the overlay itself closes, so Back never falls through to the conversation/App.
    BackHandler(onBack = onOverlayBack)
    val anchor = when (menu) {
        ComposerMenu.ATTACHMENTS -> addAnchor
        ComposerMenu.MODEL -> modelAnchor
        ComposerMenu.NONE -> null
    } ?: return
    val density = LocalDensity.current
    val currentChoices = selectedSlot?.let { com.nanzhufeng.ai.domain.ComposerModelRoutingCatalog.groups[it].orEmpty() }.orEmpty()
    val normalizedSelectedChoice = com.nanzhufeng.ai.domain.ComposerModelRoutingCatalog.choice(selectedModelId)
    val activeModelSlot = normalizedSelectedChoice.slot
    val deepSeekPricingPeriod by produceState(
        initialValue = com.nanzhufeng.ai.domain.DeepSeekPricingWindow.periodAt(java.time.Instant.now()),
        key1 = menu,
    ) {
        while (true) {
            val now = java.time.Instant.now()
            value = com.nanzhufeng.ai.domain.DeepSeekPricingWindow.periodAt(now)
            delay(
                (com.nanzhufeng.ai.domain.DeepSeekPricingWindow.millisUntilNextTransition(now) + 50L)
                    .coerceAtLeast(250L),
            )
        }
    }
    val menuWidth = if (menu == ComposerMenu.MODEL) 336.dp else 280.dp
    // The root needs room for one automatic row and the Daily/Deep task rows. A child list uses the same
    // header plus only the rows it actually owns, so it never has a blank lower tray.
    val modelPickerContentHeight = if (selectedSlot == null) {
        ComposerModelPickerRootContentHeight
    } else {
        32.dp + 72.dp * currentChoices.size.toFloat()
    }
    val menuHeight = if (menu == ComposerMenu.ATTACHMENTS) {
        if (styleMenuVisible) {
            ComposerModelPickerHeaderHeight + 52.dp * com.nanzhufeng.ai.domain.ConversationStyle.selectable.size + 16.dp
        } else {
            52.dp * attachmentActions.size +
                (if (conversationStyle != null && onSelectConversationStyle != null) 56.dp else 0.dp) +
                (if (conversationWebSearchEnabled != null && onToggleConversationWebSearch != null) 72.dp else 16.dp)
        }
    } else {
        ComposerModelPickerHeaderHeight + modelPickerContentHeight
    }
    val horizontalPadding = with(density) { 12.dp.toPx() }
    val verticalGap = with(density) { 8.dp.toPx() }
    val widthPx = with(density) { menuWidth.toPx() }
    val heightPx = with(density) { menuHeight.toPx() }
    val viewportWidthPx = LocalWindowInfo.current.containerSize.width
    val x = when (menu) {
        ComposerMenu.ATTACHMENTS -> maxOf(horizontalPadding, anchor.left)
        // The model affordance lives on the composer's trailing side.  Its layered root and
        // child menus use the same trailing edge as the composer instead of stretching left
        // merely because the picker is wider than the compact model label.
        ComposerMenu.MODEL -> (viewportWidthPx - widthPx - horizontalPadding)
            .coerceAtLeast(horizontalPadding)
        ComposerMenu.NONE -> horizontalPadding
    }
    val y = maxOf(horizontalPadding, anchor.top - heightPx - verticalGap)
    Box(Modifier.fillMaxSize().zIndex(ConversationModalOverlayZIndex)) {
        // This transparent sibling owns only outside-tap dismissal. Unlike Back and inward edge
        // swipe, tapping the scrim always closes the whole overlay instead of navigating a model
        // child page back to its root. It has no focus request and no size dependency on the menu,
        // keeping the IME and composer where they already are.
        Box(
            Modifier
                .fillMaxSize()
                .then(if (menu == ComposerMenu.MODEL) Modifier.background(Color.Black.copy(alpha = 0.16f)) else Modifier)
                .p5aDismissOnInwardEdgeSwipe(onOverlayBack)
                .pointerInput(menu, onDismiss) { detectTapGestures(onTap = { onDismiss() }) },
        )
        Surface(
            color = ForegroundSurface,
            shape = RoundedCornerShape(if (menu == ComposerMenu.MODEL) 28.dp else 22.dp),
            shadowElevation = if (menu == ComposerMenu.MODEL) 14.dp else 10.dp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset { IntOffset(x.toInt(), y.toInt()) }
                .width(menuWidth)
                .p5aDismissOnInwardEdgeSwipe(onOverlayBack),
        ) {
            if (menu == ComposerMenu.ATTACHMENTS) {
                if (styleMenuVisible && conversationStyle != null && onSelectConversationStyle != null) {
                    Column {
                        ComposerStylePickerHeader(onBack = { styleMenuVisible = false })
                        Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp)) {
                            com.nanzhufeng.ai.domain.ConversationStyle.selectable.forEach { style ->
                                ComposerStyleOptionRow(
                                    label = style.definition().label,
                                    selected = style == conversationStyle,
                                    onClick = {
                                        onSelectConversationStyle(style)
                                        onDismiss()
                                    },
                                )
                            }
                        }
                    }
                } else Column(Modifier.padding(vertical = 8.dp)) {
                    attachmentActions.forEach { action -> ComposerOverlayAction(action.icon, action.label, action.onClick) }
                    if (conversationStyle != null && onSelectConversationStyle != null) {
                        ComposerConversationStyleAction(
                            selectedLabel = conversationStyle.definition().label,
                            onClick = { styleMenuVisible = true },
                        )
                    }
                    if (conversationWebSearchEnabled != null && onToggleConversationWebSearch != null) {
                        ComposerConversationWebSearchAction(
                            enabled = conversationWebSearchEnabled,
                            onEnabledChange = onToggleConversationWebSearch,
                        )
                    }
                }
            } else TransientMenuTextScale {
                Column {
                    ComposerModelPickerHeader(
                    title = selectedSlot?.label ?: "选择模型",
                    onBackOrClose = onOverlayBack,
                    isRoot = selectedSlot == null,
                )
                    Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = modelPickerContentHeight)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                if (selectedSlot == null) {
                    val autoDetail = modelOptions.firstOrNull()?.second ?: "Auto · DeepSeek V4.1 Flash"
                    ComposerModelPickerSectionLabel("自动选择")
                    ComposerModelOverlayRow(
                        label = "自动",
                        detail = autoDetail,
                        detailModelName = autoDetail.substringAfter(" · ", missingDelimiterValue = "").takeIf { it.isNotBlank() },
                        selected = normalizedSelectedChoice.id == com.nanzhufeng.ai.domain.ComposerModelRoutingCatalog.auto.id,
                        onClick = { onSelectModel?.invoke(null) ?: onDismiss() },
                    )
                    ComposerModelPickerSectionLabel("按任务选择")
                    listOf(
                        com.nanzhufeng.ai.domain.ComposerModelSlot.DAILY,
                        com.nanzhufeng.ai.domain.ComposerModelSlot.DEEP,
                    ).forEach { slot ->
                            ComposerModelOverlayRow(
                                label = slot.label,
                            detail = when (slot) {
                                com.nanzhufeng.ai.domain.ComposerModelSlot.COMPARE -> "GPT / Claude 或 Claude / Qwen 双模型对照"
                                com.nanzhufeng.ai.domain.ComposerModelSlot.DAILY -> "日常问答与轻量任务"
                                com.nanzhufeng.ai.domain.ComposerModelSlot.DEEP -> "复杂推理与专业分析"
                                com.nanzhufeng.ai.domain.ComposerModelSlot.MULTIMODAL -> "图像、视频与 PDF"
                                com.nanzhufeng.ai.domain.ComposerModelSlot.AUTO -> ""
                            },
                            selected = slot == activeModelSlot,
                            showNext = slot != activeModelSlot,
                            onClick = { selectedSlot = slot },
                        )
                    }
                } else {
                    ComposerModelPickerSectionLabel("选择具体模型")
                    val webSearchStateLabel = conversationWebSearchEnabled?.let { enabled ->
                        if (enabled) "实时联网" else "未联网"
                    }
                    currentChoices.forEach { choice ->
                        val providerId = com.nanzhufeng.ai.domain.NanfengModelServiceCatalog.providerFor(choice.routes.single())
                        val isDeepSeek = providerId == com.nanzhufeng.ai.domain.ProviderId.DEEPSEEK
                        val webSearchDetail = webSearchStateLabel?.let {
                            when (providerId) {
                                com.nanzhufeng.ai.domain.ProviderId.OPENROUTER -> "OpenRouter · $webSearchStateLabel"
                                com.nanzhufeng.ai.domain.ProviderId.QWEN -> "千问 · $webSearchStateLabel"
                                com.nanzhufeng.ai.domain.ProviderId.DEEPSEEK -> null
                                com.nanzhufeng.ai.domain.ProviderId.ZHIPU -> "智谱 · $webSearchStateLabel"
                                com.nanzhufeng.ai.domain.ProviderId.MOCK -> null
                            }
                        }
                        ComposerModelOverlayRow(
                            label = choice.label,
                            detail = if (isDeepSeek && webSearchStateLabel != null) {
                                "${deepSeekPricingPeriod.pickerLabel} · $webSearchStateLabel"
                            } else if (isDeepSeek) {
                                deepSeekPricingPeriod.pickerLabel
                            } else webSearchDetail ?: when (choice.slot) {
                                com.nanzhufeng.ai.domain.ComposerModelSlot.COMPARE -> "两个模型并行回答，便于对照"
                                com.nanzhufeng.ai.domain.ComposerModelSlot.DAILY -> "适合日常问答与轻量任务"
                                com.nanzhufeng.ai.domain.ComposerModelSlot.DEEP -> "适合复杂推理与专业分析"
                                com.nanzhufeng.ai.domain.ComposerModelSlot.MULTIMODAL -> "支持图像、视频与 PDF"
                                com.nanzhufeng.ai.domain.ComposerModelSlot.AUTO -> ""
                            },
                            emphasizedDetailPrefix = deepSeekPricingPeriod.pickerLabel.takeIf { isDeepSeek },
                            selected = choice.id == normalizedSelectedChoice.id,
                            onClick = { onSelectModel?.invoke(choice.id) ?: onDismiss() },
                        )
                    }
                }
                    }
                }
            }
        }
    }
}

@Composable
private fun ComposerModelPickerHeader(title: String, onBackOrClose: () -> Unit, isRoot: Boolean) {
    Box(Modifier.fillMaxWidth().height(ComposerModelPickerHeaderHeight)) {
        Spacer(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 8.dp)
                .width(36.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(50))
                .background(Color(0xFFC7CBC8)),
        )
        IconButton(
            onClick = onBackOrClose,
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp).size(44.dp),
        ) {
            Icon(if (isRoot) Icons.Rounded.Close else Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = if (isRoot) "关闭模型选择" else "返回模型分类")
        }
        Text(
            title,
            modifier = Modifier.align(Alignment.Center),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ComposerModelPickerSectionLabel(label: String) {
    Text(
        label,
        color = SecondaryText,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
    )
}

@Composable
private fun ComposerModelOverlayRow(
    label: String,
    detail: String,
    selected: Boolean = false,
    showNext: Boolean = false,
    detailModelName: String? = null,
    emphasizedDetailPrefix: String? = null,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = if (selected) AccentOrangeSoft else NeutralAssistantSurface,
        contentColor = if (selected) AccentOrange else BodyText,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(label, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (detail.isNotBlank()) Text(
                    detailModelName?.let { modelNameAnnotatedTextIn(detail, it) }
                        ?: emphasizedDetailPrefix
                            ?.takeIf(detail::startsWith)
                            ?.let { prefix ->
                                buildAnnotatedString {
                                    withStyle(SpanStyle(color = AccentOrange, fontWeight = FontWeight.Bold)) {
                                        append(prefix)
                                    }
                                    append(detail.removePrefix(prefix))
                                }
                            }
                        ?: AnnotatedString(detail),
                    style = MaterialTheme.typography.bodySmall,
                    color = SecondaryText,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            when {
                selected -> Icon(Icons.Rounded.Check, contentDescription = "当前模型", modifier = Modifier.size(22.dp))
                showNext -> Icon(Icons.Rounded.ChevronRight, contentDescription = "展开$label", modifier = Modifier.size(20.dp), tint = SecondaryText)
            }
        }
    }
}

@Composable
private fun ComposerOverlayAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(contentColor = BodyText),
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(0.dp),
    ) {
        ComposerMenuIconSurface(icon = icon, tint = BodyText)
        Spacer(Modifier.width(12.dp))
        Text(label, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Start)
    }
}

@Composable
private fun ComposerConversationStyleAction(selectedLabel: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ComposerMenuIconSurface(icon = Icons.Rounded.AutoAwesome, tint = AccentOrange)
            Spacer(Modifier.width(12.dp))
            Text(
                "基础风格和语气",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            Text(
                selectedLabel,
                color = AccentOrange,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Rounded.ChevronRight, contentDescription = "切换基础风格和语气", modifier = Modifier.size(18.dp), tint = SecondaryText)
        }
    }
}

@Composable
private fun ComposerStylePickerHeader(onBack: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(ComposerModelPickerHeaderHeight)) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp).size(44.dp),
        ) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回附件菜单")
        }
        Text(
            "基础风格和语气",
            modifier = Modifier.align(Alignment.Center),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ComposerStyleOptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (selected) AccentOrangeSoft else Color.Transparent,
        contentColor = if (selected) AccentOrange else BodyText,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            if (selected) Icon(Icons.Rounded.Check, contentDescription = "当前风格", modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun ComposerMenuIconSurface(icon: ImageVector, tint: Color) {
    Surface(color = NeutralSystemSurface, shape = CircleShape, modifier = Modifier.size(32.dp)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = tint)
        }
    }
}

@Composable
private fun ComposerConversationWebSearchAction(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    Surface(
        onClick = { onEnabledChange(!enabled) },
        color = Color.Transparent,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ComposerMenuIconSurface(icon = Icons.Rounded.Public, tint = if (enabled) AccentOrange else SecondaryText)
            Spacer(Modifier.width(12.dp))
            Text("实时网页搜索", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            SettingsSwitch(
                checked = enabled,
                onCheckedChange = onEnabledChange,
            )
        }
    }
}

/** The mobile composer has one floating outer pill; the text field deliberately draws no second box. */
@Composable
private fun ComposerDraftTextField(
    value: String,
    onValueChange: (String) -> Unit,
    inputDescription: String = "会话草稿",
    expanded: Boolean,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val composerTextSizeSp = composerNativeTextSizeSp(LocalAppTextScale.current)
    // The native editable view keeps Android's real text ActionMode.  In particular, a long
    // press must expose the white system toolbar's Paste / Select all / Autofill actions instead
    // of reducing the user to the keyboard's Autofill bubble alone.
    AndroidView(
        factory = { androidContext ->
            android.widget.EditText(androidContext).apply {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                setPadding(0, 0, 0, 0)
                setTextColor(BodyText.toArgb())
                setHintTextColor(Color(0xFF9A9E9B).toArgb())
                hint = "回复 南枫AI"
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, composerTextSizeSp)
                includeFontPadding = false
                gravity = android.view.Gravity.CENTER_VERTICAL or android.view.Gravity.START
                inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_NONE
                setMinHeight(0)
                isLongClickable = true
                importantForAutofill = android.view.View.IMPORTANT_FOR_AUTOFILL_YES
                contentDescription = inputDescription
                // This field deliberately uses Android's native editor so long press keeps the
                // system text toolbar.  Compose's LocalTextSelectionColors does not cross that
                // AndroidView boundary, therefore the native highlight, cursor and handles must
                // explicitly consume the same live theme accent.
                applyComposerNativeSelectionColors(
                    editor = this,
                    accent = AccentOrange,
                    selectionBackground = composerSelectionBackground(AccentOrange),
                )
                addTextChangedListener(object : android.text.TextWatcher {
                    override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) = Unit
                    override fun afterTextChanged(text: android.text.Editable?) {
                        onValueChange(text?.toString().orEmpty())
                    }
                })
            }
        },
        update = { editor ->
            // AndroidView does not inherit Compose Typography. Re-apply the same live app font
            // scale so typed text and the hint change immediately with Settings → 字体大小.
            editor.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, composerTextSizeSp)
            // Re-apply on every theme recomposition: changing the appearance accent must update
            // an already-created EditText instead of leaving its system teal selection behind.
            applyComposerNativeSelectionColors(
                editor = editor,
                accent = AccentOrange,
                selectionBackground = composerSelectionBackground(AccentOrange),
            )
            editor.setSingleLine(!expanded)
            editor.minLines = 1
            // A finite maxLines makes EditText measure itself as that many empty lines.  That
            // created one spare line above and below a real draft. Let Compose cap the actual
            // content height instead; EditText scrolls normally once it reaches that cap.
            editor.maxLines = if (expanded) Int.MAX_VALUE else 1
            editor.ellipsize = if (expanded) null else android.text.TextUtils.TruncateAt.END
            editor.gravity = if (expanded) {
                android.view.Gravity.TOP or android.view.Gravity.START
            } else {
                android.view.Gravity.CENTER_VERTICAL or android.view.Gravity.START
            }
            editor.onFocusChangeListener = android.view.View.OnFocusChangeListener { _, focused -> onFocusChanged(focused) }
            if (expanded && !editor.hasFocus()) editor.post { editor.requestFocus() }
            if (editor.text.toString() != value) {
                editor.setText(value)
                editor.setSelection(value.length)
            }
        },
        modifier = modifier
            .padding(horizontal = 6.dp)
            .heightIn(
                // In focused mode the action row is below this editor. Keep one clear text line
                // but do not reserve an extra half-line beneath it; the send control keeps its
                // independent 60dp touch row and therefore never competes with typed text.
                min = if (expanded) ComposerExpandedDraftMinimumHeight else 36.dp,
                max = if (expanded) ComposerDraftTextMaximumHeight else 36.dp,
            )
            .semantics { contentDescription = inputDescription },
    )
}

/** Native AndroidView selection needs its own bridge; Compose locals do not reach EditText. */
private fun applyComposerNativeSelectionColors(
    editor: android.widget.EditText,
    accent: Color,
    selectionBackground: Color,
) {
    editor.highlightColor = selectionBackground.toArgb()
    // Android Q exposes the editor cursor and every selection handle as public drawables.  The
    // product's actual device supports this API; earlier Android versions still receive the
    // accent highlight instead of the default teal fill.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val accentArgb = accent.toArgb()
        editor.textCursorDrawable = editor.textCursorDrawable.copyAndTintForComposer(accentArgb)
        editor.textSelectHandle.copyAndTintForComposer(accentArgb)?.let { editor.setTextSelectHandle(it) }
        editor.textSelectHandleLeft.copyAndTintForComposer(accentArgb)?.let { editor.setTextSelectHandleLeft(it) }
        editor.textSelectHandleRight.copyAndTintForComposer(accentArgb)?.let { editor.setTextSelectHandleRight(it) }
    }
}

private fun composerSelectionBackground(accent: Color): Color =
    accent.copy(alpha = if (BodyText.red > 0.5f) 0.46f else 0.34f)

private fun android.graphics.drawable.Drawable?.copyAndTintForComposer(
    accentArgb: Int,
): android.graphics.drawable.Drawable? {
    val drawable = this ?: return null
    return (drawable.constantState?.newDrawable()?.mutate() ?: drawable.mutate()).apply {
        setTint(accentArgb)
    }
}

private val ComposerDockMinimumHeight = 60.dp
private val ComposerExpandedDraftMinimumHeight = 28.dp
private val ComposerDockMaximumHeight = 224.dp
private val ComposerDraftTextMaximumHeight = 208.dp

/** Images keep their intrinsic aspect ratio; only an upper edge limit keeps a chat row readable. */
@Composable
private fun originalAspectPreviewModifier(
    bitmap: android.graphics.Bitmap?,
    maxEdge: androidx.compose.ui.unit.Dp,
    fallbackSize: androidx.compose.ui.unit.Dp,
): Modifier {
    if (bitmap == null) return Modifier.size(fallbackSize)
    val density = LocalDensity.current
    val naturalWidth = with(density) { bitmap.width.toDp() }
    val naturalHeight = with(density) { bitmap.height.toDp() }
    val scale = (maxEdge.value / maxOf(naturalWidth.value, naturalHeight.value)).coerceAtMost(1f)
    return Modifier.size(naturalWidth * scale, naturalHeight * scale)
}

/** Exact search navigation briefly washes the real attachment with the active theme color. */
@Composable
private fun Modifier.searchAttachmentAnchorHighlight(
    targeted: Boolean,
    requestId: Long,
    cornerRadius: androidx.compose.ui.unit.Dp,
): Modifier {
    val requester = remember { BringIntoViewRequester() }
    val intensity = remember { Animatable(0f) }
    val accent = MaterialTheme.colorScheme.primary
    LaunchedEffect(targeted, requestId) {
        if (targeted && requestId > 0L) {
            delay(90)
            requester.bringIntoView()
            intensity.snapTo(0f)
            intensity.animateTo(1f, tween(durationMillis = 160, easing = LinearOutSlowInEasing))
            intensity.animateTo(0f, tween(durationMillis = 1_100, easing = LinearOutSlowInEasing))
        }
    }
    return this.bringIntoViewRequester(requester).drawWithContent {
        drawContent()
        if (intensity.value > 0f) {
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        accent.copy(alpha = 0.30f * intensity.value),
                        accent.copy(alpha = 0.10f * intensity.value),
                        Color.Transparent,
                    ),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                ),
                cornerRadius = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx()),
            )
        }
    }
}

/**
 * One assistant-owned image result, whether imported or generated locally, uses the same gallery.
 * The selected image is the only large surface; fixed thumbnail slots switch it without changing
 * the row geometry, and opening the large surface always routes to the verified original viewer.
 */
@Composable
private fun AssistantGeneratedImageGroup(
    messageId: MessageNodeId,
    images: List<PresentationBlock.AttachmentReference>,
    attachmentPreviews: Map<AttachmentId, ConversationAttachmentPreview>,
    onOpenImagePreview: (AttachmentId) -> Unit,
    searchAnchorAttachmentId: AttachmentId?,
    searchAnchorRequestId: Long,
) {
    val imageIds = images.map { it.attachment.id }
    var selectedIdValue by rememberSaveable(messageId.value, imageIds.map { it.value }) {
        mutableStateOf(imageIds.first().value)
    }
    LaunchedEffect(imageIds) {
        if (imageIds.none { it.value == selectedIdValue }) selectedIdValue = imageIds.first().value
    }
    LaunchedEffect(searchAnchorAttachmentId, searchAnchorRequestId, imageIds) {
        searchAnchorAttachmentId?.takeIf(imageIds::contains)?.let { selectedIdValue = it.value }
    }
    val selected = images.firstOrNull { it.attachment.id.value == selectedIdValue } ?: images.first()
    val selectedPreview = attachmentPreviews[selected.attachment.id]
    val selectedBitmap = rememberDecodedBitmap(selectedPreview?.thumbnail?.bytes, selectedPreview?.id)
    Column(
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .searchAttachmentAnchorHighlight(
                targeted = searchAnchorAttachmentId in imageIds,
                requestId = searchAnchorRequestId,
                cornerRadius = 18.dp,
            ),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val maximumWidth = maxWidth.coerceAtMost(340.dp)
            val maximumHeight = 260.dp
            val aspectRatio = selectedBitmap
                ?.let { bitmap -> bitmap.width.toFloat() / bitmap.height.toFloat().coerceAtLeast(1f) }
                ?.coerceIn(0.12f, 8f)
                ?: (4f / 3f)
            val previewWidth: androidx.compose.ui.unit.Dp
            val previewHeight: androidx.compose.ui.unit.Dp
            if (aspectRatio >= maximumWidth.value / maximumHeight.value) {
                previewWidth = maximumWidth
                previewHeight = (maximumWidth / aspectRatio).coerceAtLeast(72.dp)
            } else {
                previewHeight = maximumHeight
                previewWidth = (maximumHeight * aspectRatio).coerceAtLeast(72.dp)
            }
            Surface(
                onClick = { onOpenImagePreview(selected.attachment.id) },
                color = ForegroundSurface,
                contentColor = BodyText,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, NeutralBorder.copy(alpha = 0.72f)),
                modifier = Modifier
                    .size(previewWidth, previewHeight)
                    .semantics { contentDescription = "AI 生成图片，点击全屏查看" },
            ) {
                if (selectedBitmap != null) {
                    Image(
                        bitmap = selectedBitmap.asImageBitmap(),
                        contentDescription = selected.attachment.displayName ?: "AI 生成图片",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            selectedPreview?.unavailableReason ?: "图片预览暂不可用",
                            color = SecondaryText,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
        if (images.size > 1) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                images.forEachIndexed { index, image ->
                    val preview = attachmentPreviews[image.attachment.id]
                    val bitmap = rememberDecodedBitmap(preview?.thumbnail?.bytes, preview?.id)
                    val selectedThumbnail = image.attachment.id.value == selectedIdValue
                    Surface(
                        onClick = { selectedIdValue = image.attachment.id.value },
                        color = ForegroundSurface,
                        contentColor = BodyText,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(
                            if (selectedThumbnail) 2.dp else 1.dp,
                            if (selectedThumbnail) MaterialTheme.colorScheme.primary else NeutralBorder.copy(alpha = 0.72f),
                        ),
                        modifier = Modifier
                            .size(48.dp)
                            .semantics {
                                contentDescription = "切换到第 ${index + 1} 张 AI 生成图片"
                            },
                    ) {
                        if (bitmap != null) Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentPreviewChip(
    preview: ConversationAttachmentPreview?,
    messageNodeId: MessageNodeId,
    displayName: String?,
    mimeType: String,
    byteCount: Long,
    sentAt: java.time.Instant?,
    onOpenImagePreview: (AttachmentId) -> Unit,
    onOpenPdfPreview: (AttachmentId) -> Unit,
    onOpenVideoPreview: (AttachmentId) -> Unit,
    onOpenAudioPreview: (AttachmentId) -> Unit,
    onOpenTextPreview: (AttachmentId) -> Unit,
    searchAnchorAttachmentId: AttachmentId? = null,
    searchAnchorRequestId: Long = 0L,
) {
    val isImage = mimeType.startsWith("image/")
    val isPdf = mimeType == "application/pdf"
    val isVideo = mimeType == "video/mp4"
    val isAudio = mimeType in com.nanzhufeng.ai.domain.CONVERSATION_ALLOWED_AUDIO_MIME_TYPES
    // Do not maintain a second, narrower format list here.  The preview surface and
    // tap action intentionally share the domain safety gate used by search and the
    // extractor, including DOCX/XLSX/PPTX and safe octet-stream filename fallbacks.
    val isText = com.nanzhufeng.ai.domain.isSafeTextAttachment(mimeType, displayName)
    val previewCorner = if (isVideo) 20.dp else if (isImage) 10.dp else 10.dp
    val bitmap = rememberDecodedBitmap(preview?.thumbnail?.bytes, preview?.id)
    // Attachments are content, not message bubbles: a photo is its own rounded pixel surface;
    // only video/document-like content receives a deliberate card treatment.
    val previewModifier = when {
        // A deliberately short, uniform video card makes a representative frame read as
        // video at a glance, while photos retain their natural aspect ratio.
        isVideo -> Modifier.widthIn(min = 144.dp, max = 220.dp).height(128.dp)
        isImage -> originalAspectPreviewModifier(bitmap, maxEdge = 220.dp, fallbackSize = 92.dp)
        // The in-transcript audio preview is a compact attachment cue.  Only the dedicated
        // player expands; keeping this short avoids turning a message attachment into a row.
        isAudio -> Modifier.width(176.dp).height(42.dp)
        isText -> Modifier.widthIn(min = 164.dp, max = 228.dp).heightIn(min = 96.dp, max = 128.dp)
        else -> Modifier.size(92.dp)
    }.clip(RoundedCornerShape(previewCorner))
    var attachmentInfoVisible by rememberSaveable("attachment-info-${preview?.id?.value ?: displayName}") { mutableStateOf(false) }
    var attachmentBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var attachmentPressPosition by remember { mutableStateOf<androidx.compose.ui.geometry.Offset?>(null) }
    val openAttachment = {
        preview?.id?.let { id ->
            when {
                isImage -> onOpenImagePreview(id)
                isPdf -> onOpenPdfPreview(id)
                isVideo -> onOpenVideoPreview(id)
                isAudio -> onOpenAudioPreview(id)
                isText -> onOpenTextPreview(id)
            }
        }
    }
    Box(
        modifier = Modifier
            .onGloballyPositioned { attachmentBounds = it.boundsInRoot() }
            .searchAttachmentAnchorHighlight(
                targeted = preview?.id == searchAnchorAttachmentId,
                requestId = searchAnchorRequestId,
                cornerRadius = previewCorner,
            )
            .pointerInput(preview?.id, mimeType) {
                detectTapGestures(
                    onTap = { openAttachment() },
                    onLongPress = { pressPosition ->
                        attachmentPressPosition = pressPosition
                        attachmentInfoVisible = true
                    },
                )
            }
            .semantics { contentDescription = "${attachmentKindLabel(mimeType)}附件，点击预览，长按查看信息" },
    ) {
        if (isAudio) {
            AudioMessageAttachmentPlayer(
                mimeType = mimeType,
                durationMillis = preview?.audioDurationMillis,
                modifier = previewModifier,
            )
        } else Box(previewModifier.then(when {
            isVideo -> Modifier.border(1.dp, Color.Black.copy(alpha = 0.18f), RoundedCornerShape(previewCorner))
            else -> Modifier
        })) {
            if (bitmap != null) Image(bitmap = bitmap.asImageBitmap(), contentDescription = if (isVideo) "本地视频代表帧" else "本地图片缩略图", contentScale = if (isVideo) ContentScale.Crop else ContentScale.Fit, modifier = Modifier.fillMaxSize())
            else Box(
                Modifier.fillMaxSize()
                    .background(if (isPdf || isText) ForegroundSurface else NeutralAssistantSurface)
                    .border(if (isPdf || isText) 1.dp else 0.dp, Color(0x141E2925), RoundedCornerShape(10.dp)),
            ) {
                val textPreview = preview?.textPreview
                when {
                    isText && textPreview?.text != null -> AttachmentTextSnippet(
                        text = textPreview.text,
                        mimeType = mimeType,
                        modifier = Modifier.fillMaxSize(),
                    )
                    isText -> AttachmentTextPreviewUnavailable(
                        mimeType = mimeType,
                        reason = textPreview?.unavailableReason ?: preview?.unavailableReason ?: "文本预览不可用",
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            if (isVideo) VideoAttachmentOverlay(preview?.videoDurationMillis)
        }
        attachmentBounds?.let { bounds ->
            attachmentPressPosition?.takeIf { attachmentInfoVisible }?.let { pressPosition ->
                AttachmentInfoPopup(
                    attachmentId = preview?.id ?: return@let,
                    messageNodeId = messageNodeId,
                    anchorBounds = bounds,
                    pressPosition = pressPosition,
                    displayName = displayName,
                    mimeType = mimeType,
                    byteCount = byteCount,
                    durationMillis = preview?.videoDurationMillis,
                    sentAt = sentAt,
                    onDismiss = { attachmentInfoVisible = false },
                )
            }
        }
    }
}

@Composable
private fun AudioMessageAttachmentPlayer(mimeType: String, durationMillis: Long?, modifier: Modifier = Modifier) {
    Surface(color = Color(0xFF183551), contentColor = Color.White, shape = RoundedCornerShape(14.dp), modifier = modifier) {
        Row(Modifier.padding(horizontal = 11.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Surface(color = Color.White.copy(alpha = 0.16f), contentColor = Color.White, shape = CircleShape, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.padding(6.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(audioFormatLabel(mimeType), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
                AudioCatalogTimeline(durationMillis, trackColor = Color.White.copy(alpha = 0.34f), progressColor = Color.White, modifier = Modifier.fillMaxWidth())
            }
            Text(durationMillis?.let(::formatAudioDuration) ?: "--:--", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.82f))
        }
    }
}

/** Shared attachment facts: compact, anchored, and readable without exposing implementation MIME jargon. */
@Composable
private fun AttachmentInfoPopup(
    attachmentId: AttachmentId,
    messageNodeId: MessageNodeId?,
    anchorBounds: androidx.compose.ui.geometry.Rect,
    pressPosition: androidx.compose.ui.geometry.Offset,
    displayName: String?,
    mimeType: String,
    byteCount: Long,
    durationMillis: Long?,
    sentAt: java.time.Instant?,
    isDraft: Boolean = false,
    onDismiss: () -> Unit,
) {
    val requestAttachmentTransfer = LocalAttachmentTransferRequest.current
    val locateAttachmentInSearch = LocalAttachmentSearchLocateRequest.current
    val density = LocalDensity.current
    val containerSize = LocalWindowInfo.current.containerSize
    val popupWidth = minOf(
        300.dp,
        with(density) { (containerSize.width - 32.dp.roundToPx()).coerceAtLeast(240.dp.roundToPx()).toDp() },
    )
    val popupHeight = if ((displayName?.length ?: 0) > 34) 208.dp else 188.dp
    val horizontalMargin = with(density) { 16.dp.roundToPx() }
    val verticalGap = with(density) { 6.dp.roundToPx() }
    val popupWidthPx = with(density) { popupWidth.roundToPx() }
    val popupHeightPx = with(density) { popupHeight.roundToPx() }
    val screenWidthPx = containerSize.width
    val screenHeightPx = containerSize.height
    val pressInRoot = androidx.compose.ui.geometry.Offset(anchorBounds.left + pressPosition.x, anchorBounds.top + pressPosition.y)
    val absoluteX = (pressInRoot.x.toInt() - popupWidthPx / 2)
        .coerceIn(horizontalMargin, (screenWidthPx - popupWidthPx - horizontalMargin).coerceAtLeast(horizontalMargin))
    // Long-press popups always point to their own press position from above. The only
    // exception is the physical top safe margin, where the same popup is clamped in place.
    val absoluteY = (pressInRoot.y.toInt() - popupHeightPx - verticalGap)
        .coerceIn(horizontalMargin, (screenHeightPx - popupHeightPx - horizontalMargin).coerceAtLeast(horizontalMargin))
    // This Popup is emitted inside the pressed attachment Box: the offset is therefore local,
    // while the clamping above still uses root coordinates. It cannot measure or move siblings.
    val x = absoluteX - anchorBounds.left.toInt()
    val y = absoluteY - anchorBounds.top.toInt()
    val kind = attachmentKindLabel(mimeType)
    val detail = listOfNotNull(
        kind,
        durationMillis?.takeIf { it > 0L }?.let(::formatVideoDuration),
        formatAttachmentBytes(byteCount),
    ).joinToString(" · ")
    val time = sentAt?.let(::formatTranscriptTimeOrNull) ?: if (isDraft) "待发送" else null
    Popup(
        alignment = Alignment.TopStart,
        offset = IntOffset(x, y),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true, dismissOnBackPress = true, dismissOnClickOutside = true),
    ) {
        Surface(
            color = ForegroundSurface,
            contentColor = BodyText,
            shape = RoundedCornerShape(24.dp),
            shadowElevation = 6.dp,
            modifier = Modifier.width(popupWidth),
        ) {
            TransientMenuTextScale {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        color = NeutralSystemSurface,
                        contentColor = SecondaryText,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = if (kind == "图片") Icons.Rounded.AddPhotoAlternate else Icons.Rounded.AttachFile,
                            contentDescription = null,
                            modifier = Modifier.padding(10.dp).size(16.dp),
                        )
                    }
                    Column(Modifier.padding(start = 10.dp, top = 2.dp).weight(1f)) {
                        Text(
                            displayName ?: "未命名附件",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = "关闭附件信息", modifier = Modifier.size(16.dp))
                    }
                }
                Text(detail, color = SecondaryText, style = MaterialTheme.typography.labelSmall)
                time?.let { Text(if (isDraft) it else "发送于 $it", color = SecondaryText, style = MaterialTheme.typography.labelSmall) }
                HorizontalDivider(color = Color(0x141E2925))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (!isDraft && messageNodeId != null) {
                        AttachmentPopupAction(Icons.Rounded.Search, "搜索定位", Modifier.weight(1f), emphasized = true) {
                            locateAttachmentInSearch(messageNodeId, attachmentId)
                            onDismiss()
                        }
                    }
                    AttachmentPopupAction(Icons.Rounded.FileDownload, "下载", Modifier.weight(1f)) {
                        requestAttachmentTransfer(attachmentId, AttachmentTransferAction.DOWNLOAD)
                        onDismiss()
                    }
                    AttachmentPopupAction(Icons.Rounded.Share, "分享", Modifier.weight(1f)) {
                        requestAttachmentTransfer(attachmentId, AttachmentTransferAction.SHARE)
                        onDismiss()
                    }
                }
                }
            }
        }
    }
}

@Composable
private fun AttachmentPopupAction(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.height(38.dp),
        shape = RoundedCornerShape(19.dp),
        colors = ButtonDefaults.textButtonColors(
            containerColor = NeutralSystemSurface,
            contentColor = if (emphasized) MaterialTheme.colorScheme.primary else BodyText,
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 5.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

private fun attachmentKindLabel(mimeType: String): String = when {
    mimeType.startsWith("video/") -> "视频"
    mimeType.startsWith("image/") -> "图片"
    mimeType == "application/pdf" -> "PDF"
    mimeType in com.nanzhufeng.ai.domain.CONVERSATION_ALLOWED_AUDIO_MIME_TYPES -> "音频"
    mimeType.startsWith("text/") || mimeType in setOf("application/json", "text/csv") -> "文本"
    else -> "文件"
}

/** A video remains visually distinct before playback without turning its frame into a chat bubble. */
@Composable
private fun VideoAttachmentOverlay(durationMillis: Long?) {
    Box(Modifier.fillMaxSize()) {
        Surface(
            color = Color.Black.copy(alpha = 0.56f),
            shape = CircleShape,
            modifier = Modifier.align(Alignment.Center).size(40.dp),
        ) {
            Icon(
                Icons.Rounded.PlayArrow,
                contentDescription = "视频预览，可播放",
                tint = Color.White,
                modifier = Modifier.padding(8.dp).size(24.dp),
            )
        }
        durationMillis?.takeIf { it > 0L }?.let { duration ->
            Text(
                formatVideoDuration(duration),
                color = ForegroundSurface,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.62f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            )
        }
    }
}

private const val FilePreviewChromeAutoHideMillis = 12_000L

private data class FilePreviewChromeState(
    val visible: Boolean,
    val isVisible: () -> Boolean,
    val show: () -> Unit,
    val toggle: () -> Unit,
)

/**
 * Every local-file viewer uses the same temporary top chrome: tap its content once to reveal
 * actions and once more to hide them. Re-showing restarts the same timeout used by video.
 */
@Composable
private fun rememberFilePreviewChromeState(previewId: String, autoHide: Boolean = true): FilePreviewChromeState {
    // The audio dialog's persistent controls must not inherit a previously auto-hidden
    // transient state if this viewer changes configuration or is restored.
    var visible by rememberSaveable(previewId, autoHide) { mutableStateOf(true) }
    var visibleRevision by rememberSaveable(previewId, autoHide) { mutableStateOf(0) }
    fun show() {
        visible = true
        visibleRevision += 1
    }
    LaunchedEffect(autoHide, visible, visibleRevision) {
        if (autoHide && visible) {
            delay(FilePreviewChromeAutoHideMillis)
            if (visible) visible = false
        }
    }
    return FilePreviewChromeState(
        visible = visible,
        isVisible = { visible },
        show = ::show,
        toggle = { if (visible) visible = false else show() },
    )
}

private fun Modifier.toggleFilePreviewChrome(previewId: String, onToggle: () -> Unit): Modifier =
    pointerInput(previewId, onToggle) { detectTapGestures(onTap = { onToggle() }) }

/** File viewer chrome must follow its host surface instead of inheriting a light-preview default. */
private fun isDarkFilePreviewSurface(surface: Color = ForegroundSurface): Boolean = surface.red < 0.5f

/** A document canvas is neutral in light mode and a distinct readable charcoal in dark mode. */
private fun localFilePreviewCanvas(dark: Boolean): Color =
    if (dark) Color(0xFF343837) else Color(0xFFF1F4F2)

/** Preview actions share one compact top-right treatment across every local file format. */
@Composable
private fun FilePreviewTopActions(
    dark: Boolean,
    onDownload: () -> Unit,
    onShare: () -> Unit,
    showTransferActions: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val container = if (dark) Color.White.copy(alpha = 0.16f) else Color(0xFFF1F4F2)
    val content = if (dark) Color.White else BodyText
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (showTransferActions) {
            IconButton(onClick = onDownload, modifier = Modifier.size(48.dp)) {
                Surface(color = container, shape = CircleShape, modifier = Modifier.fillMaxSize()) {
                    Icon(Icons.Rounded.FileDownload, contentDescription = "下载文件", tint = content, modifier = Modifier.padding(12.dp).size(24.dp))
                }
            }
            Button(
                onClick = onShare,
                modifier = Modifier.width(78.dp).height(48.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            ) { Text("分享", style = MaterialTheme.typography.labelLarge) }
        }
    }
}

@Composable
private fun PreviewCloseButton(dark: Boolean, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val container = if (dark) Color.White.copy(alpha = 0.16f) else Color(0xFFF1F4F2)
    val content = if (dark) Color.White else BodyText
    IconButton(onClick = onClose, modifier = modifier.size(48.dp)) {
        Surface(color = container, shape = CircleShape, modifier = Modifier.fillMaxSize()) {
            Icon(Icons.Rounded.Close, contentDescription = "关闭文件预览", tint = content, modifier = Modifier.padding(12.dp).size(24.dp))
        }
    }
}

/** One prominent circular control owns all preview-only previous/next navigation. */
@Composable
private fun PreviewPagerButton(
    previous: Boolean,
    enabled: Boolean,
    dark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = if (dark) Color.White.copy(alpha = 0.20f) else Color(0xFFF1F4F2)
    val content = if (dark) Color.White else BodyText
    IconButton(onClick = onClick, enabled = enabled, modifier = modifier.size(52.dp)) {
        Surface(
            color = container,
            contentColor = content,
            shape = CircleShape,
            border = BorderStroke(1.dp, if (dark) Color.White.copy(alpha = 0.28f) else NeutralBorder.copy(alpha = 0.72f)),
            shadowElevation = 6.dp,
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    if (previous) Icons.Rounded.ChevronLeft else Icons.Rounded.ChevronRight,
                    contentDescription = if (previous) "上一项" else "下一项",
                    tint = content,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

/** Audio keeps all three header actions in one fixed row, leaving the title one stable slot. */
@Composable
private fun AudioPreviewTopActions(dark: Boolean, onDownload: () -> Unit, onShare: () -> Unit, onClose: () -> Unit, showTransferActions: Boolean = true) {
    val container = if (dark) Color.White.copy(alpha = 0.16f) else Color(0xFFF1F4F2)
    val content = if (dark) Color.White else BodyText
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (showTransferActions) {
            IconButton(onClick = onDownload, modifier = Modifier.size(44.dp)) {
                Surface(color = container, shape = CircleShape, modifier = Modifier.fillMaxSize()) {
                    Icon(Icons.Rounded.FileDownload, contentDescription = "下载文件", tint = content, modifier = Modifier.padding(11.dp).size(22.dp))
                }
            }
            Button(
                onClick = onShare,
                modifier = Modifier.width(68.dp).height(44.dp),
                shape = RoundedCornerShape(22.dp),
                colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            ) { Text("分享", style = MaterialTheme.typography.labelLarge) }
        }
        IconButton(onClick = onClose, modifier = Modifier.size(44.dp)) {
            Surface(color = container, shape = CircleShape, modifier = Modifier.fillMaxSize()) {
                Icon(Icons.Rounded.Close, contentDescription = "关闭文件预览", tint = content, modifier = Modifier.padding(11.dp).size(22.dp))
            }
        }
    }
}

/** Text reading keeps a smaller persistent action group so the full-screen title still breathes. */
@Composable
private fun TextPreviewTopActions(dark: Boolean, onDownload: () -> Unit, onShare: () -> Unit, onCopy: () -> Boolean, onClose: () -> Unit, showTransferActions: Boolean = true, showCopy: Boolean = true) {
    val container = if (dark) Color.White.copy(alpha = 0.16f) else Color(0xFFF1F4F2)
    val content = if (dark) Color.White else BodyText
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1_200)
            copied = false
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (showTransferActions) {
            IconButton(onClick = onDownload, modifier = Modifier.size(40.dp)) {
                Surface(color = container, shape = CircleShape, modifier = Modifier.fillMaxSize()) {
                    Icon(Icons.Rounded.FileDownload, contentDescription = "下载文件", tint = content, modifier = Modifier.padding(10.dp).size(20.dp))
                }
            }
            Button(
                onClick = onShare,
                modifier = Modifier.width(62.dp).height(40.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            ) { Text("分享", style = MaterialTheme.typography.labelMedium) }
        }
        if (showCopy) {
            IconButton(onClick = { if (onCopy()) copied = true }, modifier = Modifier.size(40.dp)) {
                Surface(color = container, shape = CircleShape, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                        contentDescription = if (copied) "已复制" else "复制全文",
                        tint = if (copied) BrandGreen else content,
                        modifier = Modifier.padding(10.dp).size(20.dp),
                    )
                }
            }
        }
        IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
            Surface(color = container, shape = CircleShape, modifier = Modifier.fillMaxSize()) {
                Icon(Icons.Rounded.Close, contentDescription = "关闭文件预览", tint = content, modifier = Modifier.padding(10.dp).size(20.dp))
            }
        }
    }
}

@Composable
internal fun SharedPdfPreviewDialog(
    preview: ConversationAttachmentPdfPreview,
    loading: Boolean,
    onOpenPage: (Int) -> Unit,
    onClose: () -> Unit,
) = PdfPreviewDialog(preview, loading, onOpenPage, onClose)

@Composable
private fun PdfPreviewDialog(
    preview: ConversationAttachmentPdfPreview,
    loading: Boolean,
    onOpenPage: (Int) -> Unit,
    onClose: () -> Unit,
) {
    val requestAttachmentTransfer = LocalAttachmentTransferRequest.current
    val chrome = rememberFilePreviewChromeState(preview.id.value)
    val darkFilePreview = isDarkFilePreviewSurface()
    val page = preview.page
    val bitmap = rememberDecodedBitmap(page?.image?.bytes, "${preview.id.value}:${page?.pageNumber}")
    DisposableEffect(bitmap) { onDispose { bitmap?.recycle() } }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = localFilePreviewCanvas(darkFilePreview), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        // Page gestures end above navigation controls, so a PDF pan can never
                        // steal the Previous/Next hit area.
                        .padding(bottom = 92.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        page == null && loading -> Unit
                        page == null -> Text(preview.unavailableReason ?: "本地 PDF 已损坏，无法阅读。", color = SecondaryText)
                        bitmap == null -> Text("本地 PDF 页渲染失败，无法阅读。", color = SecondaryText)
                        else -> PdfPageCanvas(
                            bitmap = bitmap,
                            displayName = preview.displayName,
                            pageNumber = page.pageNumber,
                            pageCount = page.pageCount,
                            loading = loading,
                            onToggleChrome = chrome.toggle,
                            onOpenPage = onOpenPage,
                        )
                    }
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp).zIndex(2f),
                            color = AccentOrange,
                            strokeWidth = 2.5.dp,
                        )
                    }
                }
                if (chrome.visible) {
                    PreviewCloseButton(dark = darkFilePreview, onClose = onClose, modifier = Modifier.align(Alignment.TopStart).padding(12.dp))
                    FilePreviewTopActions(
                        dark = darkFilePreview,
                        onDownload = { requestAttachmentTransfer(preview.id, AttachmentTransferAction.DOWNLOAD) },
                        onShare = { requestAttachmentTransfer(preview.id, AttachmentTransferAction.SHARE) },
                        showTransferActions = preview.canTransfer,
                        modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                    )
                }
                page?.let {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(12.dp)
                            .zIndex(3f),
                    ) {
                        PreviewPagerButton(
                            previous = true,
                            enabled = it.pageNumber > 1 && !loading,
                            dark = darkFilePreview,
                            onClick = { onOpenPage(it.pageNumber - 1) },
                        )
                        Text("第 ${it.pageNumber} / ${it.pageCount} 页", style = MaterialTheme.typography.labelSmall, color = SecondaryText, modifier = Modifier.padding(horizontal = 10.dp))
                        PreviewPagerButton(
                            previous = false,
                            enabled = it.pageNumber < it.pageCount && !loading,
                            dark = darkFilePreview,
                            onClick = { onOpenPage(it.pageNumber + 1) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * PDF pages support deliberate document gestures without borrowing the image-only double-tap
 * contract: pinch zoom and pan work at any page, while a single horizontal swipe changes pages
 * only at fitted zoom so reading movement cannot accidentally navigate.
 */
@Composable
private fun PdfPageCanvas(
    bitmap: android.graphics.Bitmap,
    displayName: String?,
    pageNumber: Int,
    pageCount: Int,
    loading: Boolean,
    onToggleChrome: () -> Unit,
    onOpenPage: (Int) -> Unit,
) {
    var zoom by rememberSaveable(pageNumber) { mutableStateOf(1f) }
    var offsetX by rememberSaveable(pageNumber) { mutableStateOf(0f) }
    var offsetY by rememberSaveable(pageNumber) { mutableStateOf(0f) }
    val currentZoom = rememberUpdatedState(zoom)
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopStart) {
        val viewportWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val viewportHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val sourceWidthPx = bitmap.width.toFloat().coerceAtLeast(1f)
        val sourceHeightPx = bitmap.height.toFloat().coerceAtLeast(1f)
        val initialScale = minOf(viewportWidthPx / sourceWidthPx, viewportHeightPx / sourceHeightPx)
        val fittedWidthPx = sourceWidthPx * initialScale
        val fittedHeightPx = sourceHeightPx * initialScale
        val maximumZoom = maximumOriginalImageZoom(initialScale)
        val renderedWidthPx = fittedWidthPx * zoom
        val renderedHeightPx = fittedHeightPx * zoom
        val placedX = if (renderedWidthPx <= viewportWidthPx) (viewportWidthPx - renderedWidthPx) / 2f else offsetX.coerceIn(viewportWidthPx - renderedWidthPx, 0f)
        val placedY = if (renderedHeightPx <= viewportHeightPx) (viewportHeightPx - renderedHeightPx) / 2f else offsetY.coerceIn(viewportHeightPx - renderedHeightPx, 0f)
        val density = LocalDensity.current
        val renderedWidth = with(density) { renderedWidthPx.toDp() }
        val renderedHeight = with(density) { renderedHeightPx.toDp() }
        val swipeThresholdPx = with(density) { 56.dp.toPx() }
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(pageNumber, pageCount, loading) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val fittedZoomAtStart = currentZoom.value <= 1.001f
                        var previousPosition = down.position
                        var horizontalDistancePx = 0f
                        var verticalDistancePx = 0f
                        var usedMultiplePointers = false
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.changes.count { it.pressed } > 1) usedMultiplePointers = true
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            val delta = change.position - previousPosition
                            previousPosition = change.position
                            horizontalDistancePx += delta.x
                            verticalDistancePx += delta.y
                            if (change.changedToUpIgnoreConsumed()) {
                                if (
                                    !loading &&
                                    fittedZoomAtStart &&
                                    currentZoom.value <= 1.001f &&
                                    !usedMultiplePointers &&
                                    abs(horizontalDistancePx) > abs(verticalDistancePx) * 1.25f
                                ) {
                                    when {
                                        horizontalDistancePx <= -swipeThresholdPx && pageNumber < pageCount -> onOpenPage(pageNumber + 1)
                                        horizontalDistancePx >= swipeThresholdPx && pageNumber > 1 -> onOpenPage(pageNumber - 1)
                                    }
                                }
                                break
                            }
                        }
                    }
                },
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "${displayName ?: "本地 PDF"} 第 $pageNumber 页",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(renderedWidth, renderedHeight)
                    .offset { IntOffset(placedX.toInt(), placedY.toInt()) }
                    .pointerInput(pageNumber, viewportWidthPx, viewportHeightPx) {
                        detectTransformGestures { centroid, pan, zoomChange, _ ->
                            val oldZoom = zoom
                            val nextZoom = (oldZoom * zoomChange).coerceIn(1f, maximumZoom)
                            val ratio = nextZoom / oldZoom
                            val nextWidth = fittedWidthPx * nextZoom
                            val nextHeight = fittedHeightPx * nextZoom
                            val focusX = placedX + centroid.x
                            val focusY = placedY + centroid.y
                            val nextX = focusX - (focusX - placedX) * ratio + pan.x
                            val nextY = focusY - (focusY - placedY) * ratio + pan.y
                            zoom = nextZoom
                            offsetX = if (nextWidth <= viewportWidthPx) 0f else nextX.coerceIn(viewportWidthPx - nextWidth, 0f)
                            offsetY = if (nextHeight <= viewportHeightPx) 0f else nextY.coerceIn(viewportHeightPx - nextHeight, 0f)
                        }
                    }
                    // PDF deliberately has no onDoubleTap; that gesture belongs to image files.
                    .pointerInput(pageNumber, onToggleChrome) {
                        detectTapGestures(onTap = { onToggleChrome() })
                    },
            )
        }
    }
}

/** The cache file is created from verified display bytes keyed by the owner ID; no source URI/path enters UI state. */
@Composable
private fun rememberLocalPlaybackFile(
    id: AttachmentId,
    extension: String,
    bytes: ByteArray?,
    open: (() -> java.io.InputStream)?,
): java.io.File? {
    val context = LocalContext.current
    var localFile by remember(id.value) { mutableStateOf<java.io.File?>(null) }
    LaunchedEffect(id.value, bytes?.size, open != null) {
        localFile = withContext(Dispatchers.IO) {
            runCatching {
                java.io.File(context.cacheDir, "local-playback-${id.value}$extension").apply {
                    outputStream().use { output ->
                        when {
                            bytes != null -> output.write(bytes)
                            open != null -> open().use { input -> input.copyTo(output) }
                            else -> error("missing local playback source")
                        }
                    }
                }
            }.getOrNull()
        }
    }
    DisposableEffect(localFile) {
        // Capture the file owned by this exact effect. Reading delegated localFile from
        // onDispose observes the newly assigned file when the key changes and deletes it
        // before MediaPlayer/VideoView can open it.
        val ownedPlaybackFile = localFile
        onDispose { ownedPlaybackFile?.delete() }
    }
    return localFile
}

@Composable
private fun VideoPreviewDialog(preview: ConversationAttachmentVideoPreview, onClose: (Long) -> Unit) {
    val context = LocalContext.current
    val requestAttachmentTransfer = LocalAttachmentTransferRequest.current
    var videoView by remember(preview.id.value) { mutableStateOf<VideoView?>(null) }
    var isPlaying by rememberSaveable(preview.id.value) { mutableStateOf(false) }
    val chrome = rememberFilePreviewChromeState(preview.id.value)
    val controlsVisible = chrome.visible
    var positionMillis by rememberSaveable(preview.id.value) { mutableStateOf(preview.positionMillis) }
    var durationMillis by rememberSaveable(preview.id.value) { mutableStateOf(preview.durationMillis ?: 0L) }
    // Dragging the timeline previews a transient position. The actual VideoView is sought only
    // on release, so a finger move never turns into dozens of decoder seeks and dropped frames.
    var scrubPositionMillis by rememberSaveable(preview.id.value) { mutableStateOf<Long?>(null) }
    val localFile = rememberLocalPlaybackFile(preview.id, ".mp4", preview.bytes, preview.open)
    fun showControls() {
        chrome.show()
    }
    fun startPlayback() {
        videoView?.start()
        isPlaying = true
        showControls()
    }
    fun togglePlayback() {
        videoView?.let { view ->
            if (view.isPlaying) {
                view.pause()
                isPlaying = false
            } else startPlayback()
            showControls()
        }
    }
    fun seekTo(position: Long) {
        val safePosition = position.coerceIn(0L, durationMillis.coerceAtLeast(0L))
        videoView?.seekTo(safePosition.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        positionMillis = safePosition
        showControls()
    }
    fun previewTimelineSeek(position: Long) {
        scrubPositionMillis = position.coerceIn(0L, durationMillis.coerceAtLeast(0L))
        showControls()
    }
    fun commitTimelineSeek() {
        scrubPositionMillis?.let(::seekTo)
        scrubPositionMillis = null
    }
    fun closeVideo() = onClose(videoView?.currentPosition?.toLong() ?: preview.positionMillis)
    LaunchedEffect(videoView) {
        while (videoView != null) {
            videoView?.let { view ->
                isPlaying = view.isPlaying
                if (scrubPositionMillis == null) positionMillis = view.currentPosition.toLong().coerceAtLeast(0L)
                durationMillis = view.duration.toLong().coerceAtLeast(0L)
            }
            delay(250)
        }
    }
    Dialog(onDismissRequest = ::closeVideo, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = Color.Black, modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (preview.bytes == null && preview.open == null) Text(preview.unavailableReason ?: "本地视频已损坏，无法播放。", color = Color.White)
                else if (localFile == null) Text("正在准备本地视频…", color = Color.White)
                else AndroidView(
                    factory = { androidContext ->
                        VideoView(androidContext).also { view ->
                            view.setVideoURI(Uri.fromFile(localFile))
                            view.setOnPreparedListener { ready ->
                                ready.seekTo(preview.positionMillis.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                                durationMillis = ready.duration.toLong().coerceAtLeast(0L)
                                positionMillis = preview.positionMillis.coerceAtMost(durationMillis)
                                // Opening a local video is the explicit play action.  Start only
                                // after the verified local source is prepared, so a video never
                                // needs a second tap before it begins (including when resuming).
                                ready.start()
                                isPlaying = true
                                showControls()
                            }
                            view.setOnCompletionListener { isPlaying = false; showControls() }
                            videoView = view
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                if (controlsVisible && localFile != null) {
                    VideoControlOverlay(
                        isPlaying = isPlaying,
                        positionMillis = scrubPositionMillis ?: positionMillis,
                        durationMillis = durationMillis,
                        isScrubbing = scrubPositionMillis != null,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
                // The central control is part of the same temporary chrome as the timeline in
                // both playback states: one tap opens/closes both, while this control toggles.
                if (controlsVisible && localFile != null) {
                    VideoPlaybackOverlay(
                        isPlaying = isPlaying,
                        onToggle = ::togglePlayback,
                    )
                }
                if (controlsVisible) {
                    PreviewCloseButton(dark = true, onClose = ::closeVideo, modifier = Modifier.align(Alignment.TopStart).padding(12.dp))
                    FilePreviewTopActions(
                        dark = true,
                        onDownload = { requestAttachmentTransfer(preview.id, AttachmentTransferAction.DOWNLOAD) },
                        onShare = { requestAttachmentTransfer(preview.id, AttachmentTransferAction.SHARE) },
                        showTransferActions = preview.canTransfer,
                        modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                    )
                }
                if (localFile != null) {
                    AndroidView(
                        factory = { androidContext ->
                            val density = androidContext.resources.displayMetrics.density
                            val edgeWidthPx = 28f * density
                            val minimumEdgeSwipePx = 72f * density
                            val centralRadiusPx = 42f * density
                            val bottomButtonRadiusPx = 36f * density
                            android.view.View(androidContext).apply {
                                isClickable = true
                                setOnClickListener {
                                    chrome.toggle()
                                }
                                var edgeStartX: Float? = null
                                var edgeExitHandled = false
                                var timelineScrubbing = false
                                fun timelinePosition(event: android.view.MotionEvent): Long =
                                    (event.x / width.coerceAtLeast(1) * durationMillis).toLong()
                                val gestures = android.view.GestureDetector(androidContext, object : android.view.GestureDetector.SimpleOnGestureListener() {
                                    override fun onDown(event: android.view.MotionEvent): Boolean = true
                                    override fun onSingleTapConfirmed(event: android.view.MotionEvent): Boolean {
                                        val centerX = width / 2f
                                        val centerY = height / 2f
                                        val onCloseButton = event.x <= 76f * density && event.y <= 76f * density
                                        val onDownloadButton = event.x in (width - 160f * density)..(width - 98f * density) && event.y <= 76f * density
                                        val onShareButton = event.x >= width - 98f * density && event.y <= 76f * density
                                        val onCentralButton = kotlin.math.hypot(event.x - centerX, event.y - centerY) <= centralRadiusPx
                                        val onBottomButton = kotlin.math.hypot(event.x - centerX, event.y - (height - 58f * density)) <= bottomButtonRadiusPx
                                        val onTimeline = event.y in (height - 158f * density)..(height - 108f * density)
                                        when {
                                            onCloseButton && chrome.isVisible() -> closeVideo()
                                            onDownloadButton && chrome.isVisible() -> requestAttachmentTransfer(preview.id, AttachmentTransferAction.DOWNLOAD)
                                            onShareButton && chrome.isVisible() -> requestAttachmentTransfer(preview.id, AttachmentTransferAction.SHARE)
                                            onCentralButton || onBottomButton -> togglePlayback()
                                            onTimeline && durationMillis > 0L -> seekTo((event.x / width.coerceAtLeast(1) * durationMillis).toLong())
                                            else -> performClick()
                                        }
                                        return true
                                    }
                                    override fun onDoubleTap(event: android.view.MotionEvent): Boolean {
                                        togglePlayback()
                                        return true
                                    }
                                    override fun onFling(
                                        down: android.view.MotionEvent?,
                                        up: android.view.MotionEvent,
                                        velocityX: Float,
                                        velocityY: Float,
                                    ): Boolean {
                                        val start = down ?: return false
                                        val moved = up.x - start.x
                                        val fromLeftEdge = start.x <= edgeWidthPx && moved >= minimumEdgeSwipePx
                                        val fromRightEdge = start.x >= width - edgeWidthPx && moved <= -minimumEdgeSwipePx
                                        if (fromLeftEdge || fromRightEdge) {
                                            closeVideo()
                                            return true
                                        }
                                        return false
                                    }
                                })
                                // GestureDetector dispatches the delayed single tap to this
                                // View's real performClick path.  Lint cannot follow that
                                // nested callback, so keep the suppression local.
                                @SuppressLint("ClickableViewAccessibility")
                                setOnTouchListener { _, event ->
                                    when (event.actionMasked) {
                                        android.view.MotionEvent.ACTION_DOWN -> {
                                            edgeStartX = event.x
                                            edgeExitHandled = false
                                            timelineScrubbing = chrome.isVisible() && durationMillis > 0L &&
                                                event.y in (height - 158f * density)..(height - 108f * density)
                                        }
                                        android.view.MotionEvent.ACTION_MOVE -> {
                                            if (timelineScrubbing) {
                                                previewTimelineSeek(timelinePosition(event))
                                                return@setOnTouchListener true
                                            }
                                            val startX = edgeStartX
                                            val moved = event.x - (startX ?: event.x)
                                            val fromLeftEdge = startX != null && startX <= edgeWidthPx && moved >= minimumEdgeSwipePx
                                            val fromRightEdge = startX != null && startX >= width - edgeWidthPx && moved <= -minimumEdgeSwipePx
                                            if (!edgeExitHandled && (fromLeftEdge || fromRightEdge)) {
                                                edgeExitHandled = true
                                                closeVideo()
                                                return@setOnTouchListener true
                                            }
                                        }
                                        android.view.MotionEvent.ACTION_UP -> {
                                            if (timelineScrubbing) {
                                                previewTimelineSeek(timelinePosition(event))
                                                commitTimelineSeek()
                                                timelineScrubbing = false
                                                edgeStartX = null
                                                return@setOnTouchListener true
                                            }
                                            edgeStartX = null
                                        }
                                        android.view.MotionEvent.ACTION_CANCEL -> {
                                            scrubPositionMillis = null
                                            timelineScrubbing = false
                                            edgeStartX = null
                                        }
                                    }
                                    if (!edgeExitHandled) gestures.onTouchEvent(event)
                                    true
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

/** Drawn beneath the single gesture owner, so edge swipes always leave the viewer first. */
@Composable
private fun VideoControlOverlay(isPlaying: Boolean, positionMillis: Long, durationMillis: Long, isScrubbing: Boolean, modifier: Modifier = Modifier) {
    val progress = if (durationMillis > 0L) (positionMillis.toFloat() / durationMillis).coerceIn(0f, 1f) else 0f
    Surface(
        color = Color.Black.copy(alpha = 0.44f),
        contentColor = Color.White,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 28.dp, top = 14.dp, end = 28.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatVideoDuration(positionMillis), color = Color.White, style = MaterialTheme.typography.labelSmall)
                Text(if (isScrubbing) "松手跳转" else formatVideoDuration(durationMillis), color = Color.White.copy(alpha = 0.82f), style = MaterialTheme.typography.labelSmall)
            }
            Box(Modifier.fillMaxWidth().padding(vertical = 14.dp).height(18.dp).semantics { contentDescription = "视频播放进度，可左右拖动调整位置" }, contentAlignment = Alignment.CenterStart) {
                Box(Modifier.fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = 0.38f), CircleShape)) {
                Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(Color.White, CircleShape))
                }
            }
            Surface(
                color = Color.Black.copy(alpha = 0.56f),
                shape = CircleShape,
                modifier = Modifier.size(56.dp).semantics { contentDescription = if (isPlaying) "暂停视频" else "播放视频" },
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.padding(14.dp).size(28.dp),
                )
            }
        }
    }
}

/** The central playback toggle shares one visible state with the bottom timeline. */
@Composable
private fun VideoPlaybackOverlay(isPlaying: Boolean, onToggle: () -> Unit) {
    Surface(
        onClick = onToggle,
        color = Color.Black.copy(alpha = 0.62f),
        shape = CircleShape,
        modifier = Modifier.size(72.dp),
    ) {
            Icon(
                if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (isPlaying) "暂停视频" else "播放视频",
                tint = Color.White,
                modifier = Modifier.padding(14.dp).size(44.dp),
            )
    }
}

@Composable
private fun AudioPreviewDialog(preview: ConversationAttachmentAudioPreview, onClose: (Long) -> Unit) {
    val context = LocalContext.current
    val requestAttachmentTransfer = LocalAttachmentTransferRequest.current
    val darkAudioPreview = ForegroundSurface.red < 0.5f
    // This compact dialog has no overlay chrome to reveal: its file actions stay fixed so
    // dragging the timeline never makes the header move or disappear.
    val chrome = rememberFilePreviewChromeState(preview.id.value, autoHide = false)
    var player by remember(preview.id.value) { mutableStateOf<MediaPlayer?>(null) }
    var prepared by remember(preview.id.value) { mutableStateOf(false) }
    var isPlaying by remember(preview.id.value) { mutableStateOf(false) }
    var durationMillis by remember(preview.id.value) { mutableStateOf(preview.durationMillis) }
    var positionMillis by remember(preview.id.value) { mutableStateOf(preview.positionMillis) }
    var scrubPositionMillis by remember(preview.id.value) { mutableStateOf<Long?>(null) }
    var playbackError by remember(preview.id.value) { mutableStateOf<String?>(null) }
    val localFile = rememberLocalPlaybackFile(preview.id, ".bin", preview.bytes, preview.open)
    fun position(): Long = scrubPositionMillis ?: player?.currentPosition?.toLong()?.takeIf { it >= 0L } ?: positionMillis
    fun togglePlayback() {
        val active = player ?: return
        if (!prepared) return
        if (isPlaying) {
            active.pause()
            positionMillis = active.currentPosition.toLong()
            isPlaying = false
        } else {
            active.start()
            isPlaying = true
        }
    }
    fun previewTimelineSeek(position: Long) {
        scrubPositionMillis = position.coerceIn(0L, durationMillis?.coerceAtLeast(0L) ?: 0L)
    }
    fun commitTimelineSeek() {
        scrubPositionMillis?.let { target ->
            player?.takeIf { prepared }?.seekTo(target.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            positionMillis = target
        }
        scrubPositionMillis = null
    }
    DisposableEffect(preview.id) {
        onDispose {
            player?.release()
        }
    }
    LaunchedEffect(localFile) {
        if (localFile == null) return@LaunchedEffect
        runCatching {
            MediaPlayer().also { media ->
                player = media
                media.setDataSource(localFile.absolutePath)
                media.setOnPreparedListener { ready ->
                    val restored = preview.positionMillis.coerceIn(0L, ready.duration.toLong().coerceAtLeast(0L))
                    ready.seekTo(restored.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                    durationMillis = ready.duration.toLong().takeIf { value -> value > 0L } ?: preview.durationMillis
                    positionMillis = restored
                    prepared = true
                    // Tapping an audio attachment is the explicit playback action; merely
                    // browsing its catalog card never starts audio.
                    ready.start()
                    isPlaying = true
                }
                media.setOnCompletionListener {
                    positionMillis = durationMillis ?: 0L
                    isPlaying = false
                }
                media.prepareAsync()
            }
        }.onFailure { error -> playbackError = error.message ?: "本地音频无法播放" }
    }
    LaunchedEffect(player, prepared, isPlaying) {
        while (prepared && isPlaying) {
            if (scrubPositionMillis == null) {
                positionMillis = player?.currentPosition?.toLong()?.coerceAtLeast(0L) ?: positionMillis
            }
            delay(200)
        }
    }
    Dialog(onDismissRequest = { onClose(position()) }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            DismissibleDialogBackdrop { onClose(position()) }
            Surface(color = ForegroundSurface, shape = RoundedCornerShape(24.dp), modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f).padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(preview.displayName ?: "未命名音频", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${audioFormatLabel(preview.mimeType)} · ${formatAttachmentBytes(preview.byteCount)}", style = MaterialTheme.typography.labelSmall, color = SecondaryText)
                    }
                    if (chrome.visible) {
                        AudioPreviewTopActions(
                            dark = darkAudioPreview,
                            onDownload = { requestAttachmentTransfer(preview.id, AttachmentTransferAction.DOWNLOAD) },
                            onShare = { requestAttachmentTransfer(preview.id, AttachmentTransferAction.SHARE) },
                            onClose = { onClose(position()) },
                            showTransferActions = preview.canTransfer,
                        )
                    }
                }
                if (preview.bytes == null && preview.open == null) {
                    Text(preview.unavailableReason ?: "本地音频无法播放。", color = SecondaryText)
                } else if (localFile == null) {
                    Text("正在准备本地音频…", color = SecondaryText)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(color = if (darkAudioPreview) Color(0xFF2A3438) else Color(0xFF183551), contentColor = Color.White, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Surface(
                                    onClick = ::togglePlayback,
                                    enabled = prepared,
                                    color = Color.White.copy(alpha = 0.16f),
                                    contentColor = Color.White,
                                    shape = CircleShape,
                                    modifier = Modifier.size(56.dp).semantics { contentDescription = if (isPlaying) "暂停音频" else "播放音频" },
                                ) {
                                    Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.padding(12.dp).size(32.dp))
                                }
                                AudioScrubbableTimeline(
                                    durationMillis = durationMillis,
                                    positionMillis = scrubPositionMillis ?: positionMillis,
                                    onPreviewPosition = ::previewTimelineSeek,
                                    onCommitPosition = ::commitTimelineSeek,
                                    onCancel = { scrubPositionMillis = null },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(formatAudioDuration(scrubPositionMillis ?: positionMillis), color = Color.White, style = MaterialTheme.typography.labelSmall)
                                    Text(durationMillis?.let(::formatAudioDuration) ?: "--:--", color = Color.White.copy(alpha = 0.78f), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                        playbackError?.let { Text(it, color = SecondaryText, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
    }
}
}

@Composable
private fun TextPreviewDialog(preview: ConversationAttachmentTextPreview, onClose: () -> Unit) {
    val requestAttachmentTransfer = LocalAttachmentTransferRequest.current
    SharedTextPreviewDialog(preview, onClose, requestAttachmentTransfer)
}

@Composable
internal fun SharedTextPreviewDialog(
    preview: ConversationAttachmentTextPreview,
    onClose: () -> Unit,
    onRequestAttachmentTransfer: (AttachmentId, AttachmentTransferAction) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    // A text preview is a reading surface, so its compact top-right actions stay visible.
    rememberFilePreviewChromeState(preview.id.value, autoHide = false)
    val darkTextPreview = isDarkFilePreviewSurface()
    val previewSurface = if (darkTextPreview) ForegroundSurface else Color.White
    val markdownBlocks = remember(preview.id, preview.text) {
        preview.text?.let { text ->
            MessagePresentationRenderer().renderText(
                PresentationBlockIdentity(MessageNodeId("local-text-preview:${preview.id.value}"), 0),
                text,
            ).map(PresentationBlock::inertTextPreviewBlock)
        }.orEmpty()
    }
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        TextPreviewSystemBarsEffect(darkTextPreview)
        Surface(color = previewSurface, shape = RectangleShape, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(start = 18.dp, top = 12.dp, end = 14.dp, bottom = 10.dp),
                ) {
                    Text("本地安全文本预览", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(end = 8.dp))
                    TextPreviewTopActions(
                        dark = darkTextPreview,
                        onDownload = { onRequestAttachmentTransfer(preview.id, AttachmentTransferAction.DOWNLOAD) },
                        onShare = { onRequestAttachmentTransfer(preview.id, AttachmentTransferAction.SHARE) },
                        onCopy = {
                            preview.text?.let { text ->
                                clipboard.setText(AnnotatedString(text))
                                true
                            } ?: false
                        },
                        onClose = onClose,
                        showTransferActions = preview.canTransfer,
                        showCopy = preview.text != null,
                    )
                }
                if (preview.text == null) {
                    Box(Modifier.fillMaxSize().padding(18.dp), contentAlignment = Alignment.Center) {
                        Text(preview.unavailableReason ?: "本地文本不可用。", color = SecondaryText)
                    }
                } else {
                    Column(
                        Modifier
                            .weight(1f)
                            .navigationBarsPadding()
                            .padding(start = 18.dp, end = 18.dp, bottom = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("${preview.displayName ?: "未命名文件"} · ${preview.mimeType} · ${formatAttachmentBytes(preview.byteCount)}", style = MaterialTheme.typography.labelSmall, color = SecondaryText)
                        if (preview.truncated) Text("仅显示前 128 KiB；原文件未执行或外发。", style = MaterialTheme.typography.labelSmall, color = SecondaryText)
                        SelectionContainer(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(localFilePreviewCanvas(darkTextPreview)),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                markdownBlocks.forEach { block ->
                                    PresentationBlockView(
                                        block = block,
                                        attachmentPreviews = emptyMap(),
                                        bodyColor = BodyText,
                                        sentAt = null,
                                        assistantDocument = false,
                                        onOpenImagePreview = {},
                                        onOpenPdfPreview = {},
                                        onOpenVideoPreview = {},
                                        onOpenAudioPreview = {},
                                        onOpenTextPreview = {},
                                        typographyOverride = TextPreviewTypography,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArchivePreviewDialog(
    preview: com.nanzhufeng.ai.domain.ConversationAttachmentArchivePreview,
    onOpenEntry: (com.nanzhufeng.ai.domain.AttachmentArchiveEntry) -> Unit,
    onClose: () -> Unit,
) {
    val requestAttachmentTransfer = LocalAttachmentTransferRequest.current
    val darkPreview = isDarkFilePreviewSurface()
    val previewSurface = if (darkPreview) ForegroundSurface else Color.White
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        TextPreviewSystemBarsEffect(darkPreview)
        Surface(color = previewSurface, shape = RectangleShape, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(start = 18.dp, top = 12.dp, end = 14.dp, bottom = 10.dp),
                ) {
                    Column(Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            when {
                                preview.directoryPath.isNotEmpty() -> preview.directoryPath.last()
                                preview.containerPath.isNotEmpty() -> preview.containerPath.last().substringAfterLast('/')
                                else -> "压缩包内容"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            preview.displayName ?: "未命名压缩文件",
                            style = MaterialTheme.typography.labelSmall,
                            color = SecondaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    TextPreviewTopActions(
                        dark = darkPreview,
                        onDownload = { requestAttachmentTransfer(preview.id, AttachmentTransferAction.DOWNLOAD) },
                        onShare = { requestAttachmentTransfer(preview.id, AttachmentTransferAction.SHARE) },
                        onCopy = { false },
                        onClose = onClose,
                        showCopy = false,
                    )
                }
                if (preview.unavailableReason != null) {
                    Box(Modifier.fillMaxSize().padding(18.dp), contentAlignment = Alignment.Center) {
                        Text(preview.unavailableReason, color = SecondaryText)
                    }
                } else {
                    Column(
                        Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "${preview.totalEntryCount} 项 · ${formatAttachmentBytes(preview.byteCount)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = SecondaryText,
                        )
                        if (preview.truncated) {
                            Text("内容较多，仅显示前 2000 项。", style = MaterialTheme.typography.labelSmall, color = SecondaryText)
                        }
                        if (preview.entries.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("压缩包为空。", color = SecondaryText)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                contentPadding = PaddingValues(bottom = 10.dp),
                            ) {
                                itemsIndexed(preview.entries, key = { index, entry -> "$index:${entry.path}:${entry.isDirectory}:${entry.byteCount}" }) { _, entry ->
                                    Surface(
                                        onClick = { onOpenEntry(entry) },
                                        enabled = entry.isDirectory || entry.mimeType != null,
                                        color = localFilePreviewCanvas(darkPreview),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            Icon(
                                                if (entry.isDirectory) Icons.Rounded.Folder else Icons.Rounded.AttachFile,
                                                contentDescription = null,
                                                tint = SecondaryText,
                                                modifier = Modifier.size(20.dp),
                                            )
                                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                Text(entry.path.substringAfterLast('/'), style = MaterialTheme.typography.bodySmall, color = BodyText)
                                                if (!entry.isDirectory) {
                                                    Text(
                                                        listOfNotNull(archiveEntryTypeLabel(entry.mimeType), entry.byteCount?.let(::formatAttachmentBytes)).joinToString(" · "),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = SecondaryText,
                                                    )
                                                }
                                            }
                                            if (entry.isDirectory) {
                                                Icon(
                                                    Icons.Rounded.ChevronRight,
                                                    contentDescription = "进入${entry.path.substringAfterLast('/')}",
                                                    tint = SecondaryText,
                                                    modifier = Modifier.size(20.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun archiveEntryTypeLabel(mimeType: String?): String? = when (mimeType) {
    "image/jpeg", "image/png", "image/webp" -> "图片"
    "video/mp4" -> "视频"
    "audio/mpeg", "audio/wav", "audio/mp4" -> "音频"
    "application/pdf" -> "PDF"
    "text/markdown" -> "Markdown"
    "text/plain" -> "文本"
    "application/json" -> "JSON"
    "text/csv" -> "CSV"
    "application/xml", "text/xml" -> "XML"
    "application/x-yaml", "text/yaml" -> "YAML"
    "text/html" -> "HTML"
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "Word"
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> "Excel"
    "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> "PowerPoint"
    "application/zip" -> "ZIP"
    null -> null
    else -> "文件"
}

@Composable
private fun TextPreviewSystemBarsEffect(dark: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, dark) {
        val window = (view.parent as? DialogWindowProvider)?.window
        if (window == null) return@DisposableEffect onDispose { }
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        val previousLightStatusBars = controller.isAppearanceLightStatusBars
        controller.isAppearanceLightStatusBars = !dark
        onDispose {
            controller.isAppearanceLightStatusBars = previousLightStatusBars
        }
    }
}

private fun PresentationBlock.inertTextPreviewBlock(): PresentationBlock = when (this) {
    is PresentationBlock.Paragraph -> copy(spans = spans.inertTextPreviewSpans())
    is PresentationBlock.Note -> copy(spans = spans.inertTextPreviewSpans())
    is PresentationBlock.Heading -> copy(spans = spans.inertTextPreviewSpans())
    is PresentationBlock.UnorderedList -> copy(items = items.map { item -> item.copy(spans = item.spans.inertTextPreviewSpans()) })
    is PresentationBlock.OrderedList -> copy(items = items.map { item -> item.copy(spans = item.spans.inertTextPreviewSpans()) })
    is PresentationBlock.Quote -> copy(spans = spans.inertTextPreviewSpans())
    is PresentationBlock.Table -> copy(
        headers = headers.map(List<InlinePresentation>::inertTextPreviewSpans),
        rows = rows.map { row -> row.map(List<InlinePresentation>::inertTextPreviewSpans) },
    )
    else -> this
}

private fun List<InlinePresentation>.inertTextPreviewSpans(): List<InlinePresentation> = map { span ->
    if (span is InlinePresentation.Link) InlinePresentation.Text(span.label) else span
}

private fun formatVideoDuration(milliseconds: Long): String {
    val seconds = (milliseconds / 1000).coerceAtLeast(0)
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

@Composable
internal fun SharedImagePreviewDialog(
    preview: ConversationAttachmentOriginalPreview,
    relatedImageIds: List<AttachmentId>,
    onOpenImagePreview: (AttachmentId) -> Unit,
    onClose: () -> Unit,
    onRequestAttachmentTransfers: (List<AttachmentId>, AttachmentTransferAction) -> Unit,
) = ImagePreviewDialog(preview, relatedImageIds, onOpenImagePreview, onClose, onRequestAttachmentTransfers)

@Composable
private fun ImagePreviewDialog(
    preview: ConversationAttachmentOriginalPreview,
    relatedImageIds: List<AttachmentId>,
    onOpenImagePreview: (AttachmentId) -> Unit,
    onClose: () -> Unit,
    onRequestAttachmentTransfers: (List<AttachmentId>, AttachmentTransferAction) -> Unit,
) {
    val requestAttachmentTransfer = LocalAttachmentTransferRequest.current
    // Decode the verified original once per opened attachment. Zoom recompositions must never
    // repeatedly decode a long screenshot or silently fall back to its catalogue thumbnail.
    val bitmap = rememberDecodedBitmap(preview.bytes, preview.id)
    DisposableEffect(bitmap) { onDispose { bitmap?.recycle() } }
    val generatedImageIds = relatedImageIds.distinct().ifEmpty { listOf(preview.id) }
    val hasMultipleGeneratedImages = generatedImageIds.size > 1
    val currentGeneratedImageIndex = generatedImageIds.indexOf(preview.id).coerceAtLeast(0)
    val chrome = rememberFilePreviewChromeState(preview.id.value)
    var batchDownloadVisible by rememberSaveable(preview.id.value) { mutableStateOf(false) }
    var zoom by rememberSaveable(preview.id.value) { mutableStateOf(1f) }
    var offsetX by rememberSaveable(preview.id.value) { mutableStateOf(0f) }
    var offsetY by rememberSaveable(preview.id.value) { mutableStateOf(0f) }
    val currentPreviewZoom = rememberUpdatedState(zoom)
    val imageGestureScope = rememberCoroutineScope()
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = Color.Black, modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (bitmap == null) Text(preview.unavailableReason ?: "本地原图已损坏，无法预览。", color = Color.White)
                    else BoxWithConstraints(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.TopStart,
                    ) {
                        val viewportWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
                        val viewportHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
                        val sourceWidthPx = bitmap.width.toFloat().coerceAtLeast(1f)
                        val sourceHeightPx = bitmap.height.toFloat().coerceAtLeast(1f)
                        val initialScale = initialOriginalImageScale(sourceWidthPx, sourceHeightPx, viewportWidthPx, viewportHeightPx)
                        val fittedWidthPx = sourceWidthPx * initialScale
                        val fittedHeightPx = sourceHeightPx * initialScale
                        val maximumZoom = maximumOriginalImageZoom(initialScale)
                        val renderedWidthPx = fittedWidthPx * zoom
                        val renderedHeightPx = fittedHeightPx * zoom
                        val placedX = if (renderedWidthPx <= viewportWidthPx) (viewportWidthPx - renderedWidthPx) / 2f else offsetX.coerceIn(viewportWidthPx - renderedWidthPx, 0f)
                        val placedY = if (renderedHeightPx <= viewportHeightPx) (viewportHeightPx - renderedHeightPx) / 2f else offsetY.coerceIn(viewportHeightPx - renderedHeightPx, 0f)
                        val density = LocalDensity.current
                        val swipeThresholdPx = with(density) { 56.dp.toPx() }
                        val currentPlacedX = rememberUpdatedState(placedX)
                        val currentPlacedY = rememberUpdatedState(placedY)
                        // A touchpad can keep the same horizontal contact alive while the next
                        // image is composed. Keep the viewport recognizer alive too; each new
                        // gesture snapshots these current values instead of restarting midway
                        // through a swipe and treating its remaining movement as another swipe.
                        val currentPreviewId = rememberUpdatedState(preview.id)
                        val currentGeneratedImageIds = rememberUpdatedState(generatedImageIds)
                        val currentFittedWidthPx = rememberUpdatedState(fittedWidthPx)
                        val currentFittedHeightPx = rememberUpdatedState(fittedHeightPx)
                        val currentMaximumZoom = rememberUpdatedState(maximumZoom)
                        val currentSwipeThresholdPx = rememberUpdatedState(swipeThresholdPx)
                        Box(
                            Modifier
                                .fillMaxSize()
                                // The stable viewport is the sole image-gesture owner. The Image
                                // below is remeasured and moved after every zoom step, so attaching
                                // recognizers to it can invalidate the hit target mid-gesture and
                                // makes independent tap/transform detectors race each other.
                                .pointerInput(
                                    viewportWidthPx,
                                    viewportHeightPx,
                                ) {
                                    var lastImageTapUptime = 0L
                                    var lastImageTapPosition = Offset.Unspecified
                                    var pendingSingleTap: Job? = null
                                    val doubleTapTimeoutMillis = viewConfiguration.doubleTapTimeoutMillis
                                    val doubleTapMinTimeMillis = viewConfiguration.doubleTapMinTimeMillis
                                    val touchSlopPx = viewConfiguration.touchSlop
                                    val doubleTapSlopPx = touchSlopPx * 2f
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        val gesturePreviewId = currentPreviewId.value
                                        val gestureGeneratedImageIds = currentGeneratedImageIds.value
                                        val gestureFittedWidthPx = currentFittedWidthPx.value
                                        val gestureFittedHeightPx = currentFittedHeightPx.value
                                        val gestureMaximumZoom = currentMaximumZoom.value
                                        val gestureSwipeThresholdPx = currentSwipeThresholdPx.value
                                        val currentImageIndex = gestureGeneratedImageIds.indexOf(gesturePreviewId)
                                        val nativeZoomAtStart = currentPreviewZoom.value <= 1.001f
                                        val startZoom = currentPreviewZoom.value
                                        val startPlacedX = currentPlacedX.value
                                        val startPlacedY = currentPlacedY.value
                                        var gestureZoom = startZoom
                                        var gesturePlacedX = startPlacedX
                                        var gesturePlacedY = startPlacedY
                                        val startRenderedWidth = gestureFittedWidthPx * startZoom
                                        val startRenderedHeight = gestureFittedHeightPx * startZoom
                                        val startedInsideImage =
                                            down.position.x >= startPlacedX &&
                                            down.position.x <= startPlacedX + startRenderedWidth &&
                                            down.position.y >= startPlacedY &&
                                            down.position.y <= startPlacedY + startRenderedHeight
                                        var previousPosition = down.position
                                        var horizontalDistancePx = 0f
                                        var verticalDistancePx = 0f
                                        var usedMultiplePointers = false
                                        var transformed = false
                                        var upPosition = down.position
                                        var upUptime = down.uptimeMillis
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val pressedCount = event.changes.count { it.pressed }
                                            if (pressedCount > 1) usedMultiplePointers = true
                                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                            val delta = change.position - previousPosition
                                            previousPosition = change.position
                                            horizontalDistancePx += delta.x
                                            verticalDistancePx += delta.y

                                            if (startedInsideImage && usedMultiplePointers && pressedCount > 0) {
                                                val activeChanges = event.changes.filter { it.pressed && it.previousPressed }
                                                val previousCentroid = if (activeChanges.isEmpty()) down.position else Offset(
                                                    x = activeChanges.sumOf { it.previousPosition.x.toDouble() }.toFloat() / activeChanges.size,
                                                    y = activeChanges.sumOf { it.previousPosition.y.toDouble() }.toFloat() / activeChanges.size,
                                                )
                                                val centroid = if (activeChanges.isEmpty()) previousCentroid else Offset(
                                                    x = activeChanges.sumOf { it.position.x.toDouble() }.toFloat() / activeChanges.size,
                                                    y = activeChanges.sumOf { it.position.y.toDouble() }.toFloat() / activeChanges.size,
                                                )
                                                val previousSpread = activeChanges
                                                    .map { (it.previousPosition - previousCentroid).getDistance() }
                                                    .average().toFloat()
                                                val currentSpread = activeChanges
                                                    .map { (it.position - centroid).getDistance() }
                                                    .average().toFloat()
                                                val zoomChange = if (previousSpread > 0.001f) currentSpread / previousSpread else 1f
                                                val pan = centroid - previousCentroid
                                                if (zoomChange != 1f || pan != Offset.Zero) {
                                                    val next = imagePreviewGestureTransform(
                                                        currentZoom = gestureZoom,
                                                        zoomChange = zoomChange,
                                                        panX = pan.x,
                                                        panY = pan.y,
                                                        focusX = centroid.x,
                                                        focusY = centroid.y,
                                                        placedX = gesturePlacedX,
                                                        placedY = gesturePlacedY,
                                                        fittedWidthPx = gestureFittedWidthPx,
                                                        fittedHeightPx = gestureFittedHeightPx,
                                                        viewportWidthPx = viewportWidthPx,
                                                        viewportHeightPx = viewportHeightPx,
                                                        maximumZoom = gestureMaximumZoom,
                                                    )
                                                    zoom = next.zoom
                                                    offsetX = next.offsetX
                                                    offsetY = next.offsetY
                                                    gestureZoom = next.zoom
                                                    val nextRenderedWidth = gestureFittedWidthPx * next.zoom
                                                    val nextRenderedHeight = gestureFittedHeightPx * next.zoom
                                                    gesturePlacedX = if (nextRenderedWidth <= viewportWidthPx) {
                                                        (viewportWidthPx - nextRenderedWidth) / 2f
                                                    } else next.offsetX
                                                    gesturePlacedY = if (nextRenderedHeight <= viewportHeightPx) {
                                                        (viewportHeightPx - nextRenderedHeight) / 2f
                                                    } else next.offsetY
                                                    event.changes.filter { it.positionChanged() }.forEach { it.consume() }
                                                    transformed = true
                                                }
                                            } else if (
                                                startedInsideImage &&
                                                change.pressed &&
                                                change.positionChanged() &&
                                                (gestureFittedWidthPx * gestureZoom > viewportWidthPx ||
                                                    gestureFittedHeightPx * gestureZoom > viewportHeightPx)
                                            ) {
                                                val next = imagePreviewGestureTransform(
                                                    currentZoom = gestureZoom,
                                                    zoomChange = 1f,
                                                    panX = delta.x,
                                                    panY = delta.y,
                                                    focusX = change.position.x,
                                                    focusY = change.position.y,
                                                    placedX = gesturePlacedX,
                                                    placedY = gesturePlacedY,
                                                    fittedWidthPx = gestureFittedWidthPx,
                                                    fittedHeightPx = gestureFittedHeightPx,
                                                    viewportWidthPx = viewportWidthPx,
                                                    viewportHeightPx = viewportHeightPx,
                                                    maximumZoom = gestureMaximumZoom,
                                                )
                                                zoom = next.zoom
                                                offsetX = next.offsetX
                                                offsetY = next.offsetY
                                                gestureZoom = next.zoom
                                                val nextRenderedWidth = gestureFittedWidthPx * next.zoom
                                                val nextRenderedHeight = gestureFittedHeightPx * next.zoom
                                                gesturePlacedX = if (nextRenderedWidth <= viewportWidthPx) {
                                                    (viewportWidthPx - nextRenderedWidth) / 2f
                                                } else next.offsetX
                                                gesturePlacedY = if (nextRenderedHeight <= viewportHeightPx) {
                                                    (viewportHeightPx - nextRenderedHeight) / 2f
                                                } else next.offsetY
                                                change.consume()
                                                transformed = true
                                            }
                                            if (change.changedToUpIgnoreConsumed()) {
                                                upPosition = change.position
                                                upUptime = change.uptimeMillis
                                                break
                                            }
                                        }

                                        if (usedMultiplePointers || transformed) return@awaitEachGesture
                                        val travelled = maxOf(abs(horizontalDistancePx), abs(verticalDistancePx))
                                        if (travelled > touchSlopPx) {
                                            if (
                                                startedInsideImage &&
                                                gestureGeneratedImageIds.size > 1 &&
                                                nativeZoomAtStart &&
                                                gestureZoom <= 1.001f &&
                                                abs(horizontalDistancePx) > abs(verticalDistancePx) * 1.25f
                                            ) {
                                                val targetIndex = when {
                                                    horizontalDistancePx <= -gestureSwipeThresholdPx -> currentImageIndex + 1
                                                    horizontalDistancePx >= gestureSwipeThresholdPx -> currentImageIndex - 1
                                                    else -> currentImageIndex
                                                }
                                                gestureGeneratedImageIds.getOrNull(targetIndex)
                                                    ?.takeIf { target -> target != gesturePreviewId }
                                                    ?.let(onOpenImagePreview)
                                            }
                                            return@awaitEachGesture
                                        }

                                        if (!startedInsideImage) {
                                            chrome.toggle()
                                            return@awaitEachGesture
                                        }

                                        val sinceLastTap = upUptime - lastImageTapUptime
                                        val closeToLastTap = lastImageTapPosition != Offset.Unspecified &&
                                            (upPosition - lastImageTapPosition).getDistance() <= doubleTapSlopPx
                                        if (
                                            sinceLastTap in doubleTapMinTimeMillis..doubleTapTimeoutMillis &&
                                            closeToLastTap
                                        ) {
                                            pendingSingleTap?.cancel()
                                            pendingSingleTap = null
                                            lastImageTapUptime = 0L
                                            lastImageTapPosition = Offset.Unspecified
                                            val next = imagePreviewDoubleTapTransform(
                                                currentZoom = currentPreviewZoom.value,
                                                focusX = upPosition.x,
                                                focusY = upPosition.y,
                                                placedX = currentPlacedX.value,
                                                placedY = currentPlacedY.value,
                                                fittedWidthPx = fittedWidthPx,
                                                fittedHeightPx = fittedHeightPx,
                                                viewportWidthPx = viewportWidthPx,
                                                viewportHeightPx = viewportHeightPx,
                                                maximumZoom = maximumZoom,
                                            )
                                            zoom = next.zoom
                                            offsetX = next.offsetX
                                            offsetY = next.offsetY
                                        } else {
                                            lastImageTapUptime = upUptime
                                            lastImageTapPosition = upPosition
                                            pendingSingleTap?.cancel()
                                            pendingSingleTap = imageGestureScope.launch {
                                                delay(doubleTapTimeoutMillis)
                                                chrome.toggle()
                                            }
                                        }
                                    }
                                }
                        ) {
                            OriginalImageZoomLayer(
                                image = bitmap.asImageBitmap(),
                                contentDescription = "${preview.displayName ?: "本地图片"} 原图预览",
                                renderedWidthPx = renderedWidthPx,
                                renderedHeightPx = renderedHeightPx,
                                placedX = placedX,
                                placedY = placedY,
                            )
                        }
                    }
                }
                if (chrome.visible) {
                    PreviewCloseButton(dark = true, onClose = onClose, modifier = Modifier.align(Alignment.TopStart).padding(12.dp))
                    FilePreviewTopActions(
                        dark = true,
                        onDownload = {
                            if (hasMultipleGeneratedImages) batchDownloadVisible = true
                            else requestAttachmentTransfer(preview.id, AttachmentTransferAction.DOWNLOAD)
                        },
                        onShare = { requestAttachmentTransfer(preview.id, AttachmentTransferAction.SHARE) },
                        showTransferActions = preview.canTransfer,
                        modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                    )
                }
                if (hasMultipleGeneratedImages) {
                    PreviewPagerButton(
                        previous = true,
                        enabled = currentGeneratedImageIndex > 0,
                        dark = true,
                        onClick = {
                            generatedImageIds.getOrNull(currentGeneratedImageIndex - 1)?.let { target ->
                                zoom = 1f
                                offsetX = 0f
                                offsetY = 0f
                                onOpenImagePreview(target)
                            }
                        },
                        modifier = Modifier.align(Alignment.CenterStart).padding(12.dp).zIndex(3f),
                    )
                    PreviewPagerButton(
                        previous = false,
                        enabled = currentGeneratedImageIndex < generatedImageIds.lastIndex,
                        dark = true,
                        onClick = {
                            generatedImageIds.getOrNull(currentGeneratedImageIndex + 1)?.let { target ->
                                zoom = 1f
                                offsetX = 0f
                                offsetY = 0f
                                onOpenImagePreview(target)
                            }
                        },
                        modifier = Modifier.align(Alignment.CenterEnd).padding(12.dp).zIndex(3f),
                    )
                    Surface(
                        color = Color.Black.copy(alpha = 0.56f),
                        contentColor = Color.White,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp).zIndex(3f),
                    ) {
                        Text(
                            "${currentGeneratedImageIndex + 1} / ${generatedImageIds.size}",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        )
                    }
                }
                if (batchDownloadVisible) {
                    val outsideDismissInteraction = remember { MutableInteractionSource() }
                    val popupSurfaceInteraction = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = outsideDismissInteraction,
                                indication = null,
                                onClick = { batchDownloadVisible = false },
                            ),
                    ) {
                        Surface(
                            color = ForegroundSurface,
                            contentColor = BodyText,
                            shape = RoundedCornerShape(18.dp),
                            shadowElevation = 10.dp,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 70.dp, end = 12.dp)
                                .width(220.dp)
                                .clickable(
                                    interactionSource = popupSurfaceInteraction,
                                    indication = null,
                                    onClick = {},
                                ),
                        ) {
                            Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("下载图片", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                                AttachmentPopupAction(Icons.Rounded.FileDownload, "下载当前图片", Modifier.fillMaxWidth()) {
                                    requestAttachmentTransfer(preview.id, AttachmentTransferAction.DOWNLOAD)
                                    batchDownloadVisible = false
                                }
                                AttachmentPopupAction(Icons.Rounded.FileDownload, "同时下载 ${generatedImageIds.size} 张", Modifier.fillMaxWidth()) {
                                    onRequestAttachmentTransfers(generatedImageIds, AttachmentTransferAction.DOWNLOAD)
                                    batchDownloadVisible = false
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Measure the original bitmap at its real zoomed size while the surrounding viewport remains
 * stable. `Modifier.size` obeys the viewport's maximum constraints, so using it here would keep
 * a 2.5x image at 1x pixels and only apply the enlarged offset — the exact failure seen on device.
 */
@Composable
private fun OriginalImageZoomLayer(
    image: androidx.compose.ui.graphics.ImageBitmap,
    contentDescription: String,
    renderedWidthPx: Float,
    renderedHeightPx: Float,
    placedX: Float,
    placedY: Float,
) {
    val measuredWidthPx = renderedWidthPx.toInt().coerceAtLeast(1)
    val measuredHeightPx = renderedHeightPx.toInt().coerceAtLeast(1)
    Layout(
        content = {
            Image(
                bitmap = image,
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        },
        modifier = Modifier.fillMaxSize(),
    ) { measurables, viewportConstraints ->
        val imagePlaceable = measurables.single().measure(
            Constraints.fixed(measuredWidthPx, measuredHeightPx),
        )
        layout(viewportConstraints.maxWidth, viewportConstraints.maxHeight) {
            imagePlaceable.place(placedX.toInt(), placedY.toInt())
        }
    }
}

@Composable
private fun ComposerSendButton(
    onClick: () -> Unit,
    enabled: Boolean,
    isSending: Boolean = false,
    showStop: Boolean = false,
    contentDescription: String,
) {
    val surfaceColor = if (enabled) AccentOrange else AccentOrangeSoft
    val glyphColor = if (enabled) Color.White else SecondaryText
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(48.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, disabledContainerColor = Color.Transparent),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
    ) {
        Surface(modifier = Modifier.size(ComposerSendSurfaceSize), shape = CircleShape, color = surfaceColor) {
            Box(contentAlignment = Alignment.Center) {
                when {
                    showStop -> Icon(Icons.Rounded.Stop, contentDescription = "停止生成", tint = glyphColor, modifier = Modifier.size(ComposerStopGlyphSize))
                    isSending -> CircularProgressIndicator(color = glyphColor, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                    else -> Icon(
                        painter = painterResource(R.drawable.ic_nanfeng_send_rounded),
                        contentDescription = contentDescription,
                        tint = glyphColor,
                        modifier = Modifier.size(ComposerSendGlyphSize),
                    )
                }
            }
        }
    }
}

private fun formatAttachmentBytes(bytes: Long): String = when {
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0)
    else -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
}

/** Writes only after the explicit preview action; no private path crosses the UI state boundary. */
@Composable
internal fun AttachmentTransferEffect(
    transfer: AttachmentTransferRequest,
    onConsumeAttachmentTransfer: (AttachmentId) -> Unit,
) {
    val context = LocalContext.current
    LaunchedEffect(transfer.id, transfer.action, transfer.batch.map { it.id }) {
        runCatching { performAttachmentTransfer(context, transfer) }
            .onSuccess {
                val message = when {
                    transfer.action == AttachmentTransferAction.DOWNLOAD && transfer.batch.size > 1 -> "已同时保存 ${transfer.batch.size} 张到图库"
                    transfer.action == AttachmentTransferAction.DOWNLOAD && isGalleryMediaMime(transfer.mimeType) -> "已保存到图库"
                    transfer.action == AttachmentTransferAction.DOWNLOAD -> "已保存到下载"
                    else -> "已准备分享"
                }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
            .onFailure { error -> Toast.makeText(context, error.message ?: "文件操作失败，请重试", Toast.LENGTH_SHORT).show() }
        onConsumeAttachmentTransfer(transfer.id)
    }
}

private suspend fun performAttachmentTransfer(context: android.content.Context, request: AttachmentTransferRequest) {
    when (request.action) {
        AttachmentTransferAction.DOWNLOAD -> withContext(Dispatchers.IO) {
            check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { "当前系统不支持安全下载目录" }
            val transferStartedAtMillis = System.currentTimeMillis()
            val resolver = context.contentResolver
            val savedUris = mutableListOf<Uri>()
            try {
                request.batch.forEachIndexed { index, item ->
                    savedUris += saveAttachmentToUserCollection(context, item, transferStartedAtMillis + index)
                }
            } catch (error: Throwable) {
                savedUris.forEach { savedUri -> resolver.delete(savedUri, null, null) }
                throw error
            }
        }
        AttachmentTransferAction.SHARE -> {
            val uri = withContext(Dispatchers.IO) {
                val directory = java.io.File(context.cacheDir, "shared_attachments").apply { mkdirs() }
                val file = java.io.File(directory, "${request.id.value}-${attachmentTransferFileName(request)}")
                request.open().use { input -> file.outputStream().use(input::copyTo) }
                FileProvider.getUriForFile(context, "${context.packageName}.attachment-share", file)
            }
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = request.mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TITLE, attachmentTransferFileName(request))
                clipData = android.content.ClipData.newRawUri("南枫 AI 文件", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, "分享文件"))
        }
    }
}

private fun saveAttachmentToUserCollection(context: android.content.Context, item: AttachmentTransferItem, downloadedAtMillis: Long): Uri {
    // This flow relies on scoped storage's pending rows and relative paths.  On Android 8–9
    // neither is available without legacy broad-storage permission, so fail visibly instead of
    // writing to an unpredictable public path or reporting a false success.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        throw AttachmentTransferException(
            "当前 Android 系统不支持安全保存到图库，请升级系统后重试。",
            UnsupportedOperationException("Scoped MediaStore requires Android 10 or later."),
        )
    }
    val (collection, relativePath) = when {
        // ColorOS' “照片” timeline gives the standard camera roll precedence. Keep generated
        // images and videos together there instead of creating a separate Pictures/Movies album.
        item.mimeType.startsWith("image/") -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI to "${Environment.DIRECTORY_DCIM}/Camera"
        item.mimeType.startsWith("video/") -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI to "${Environment.DIRECTORY_DCIM}/Camera"
        else -> MediaStore.Downloads.EXTERNAL_CONTENT_URI to "${Environment.DIRECTORY_DOWNLOADS}/南枫 AI"
    }
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, attachmentTransferFileName(item.displayName, item.id))
        put(MediaStore.MediaColumns.MIME_TYPE, item.mimeType)
        put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(collection, values) ?: error("无法创建保存文件")
    try {
        val written = resolver.openOutputStream(uri, "wt")?.use { output -> item.open().use { input -> input.copyTo(output) } }
            ?: error("无法写入下载文件")
        check(written == item.byteCount) { "下载文件长度校验失败" }
        // DATE_TAKEN is read-only: MediaStore extracts it from the downloaded image's EXIF.
        // Stamp the new copy before publishing so gallery timelines sort by this download,
        // not by the original upload/capture date embedded in its bytes.
        if (item.mimeType.startsWith("image/")) {
            stampDownloadedImageTakenAt(resolver, uri, downloadedAtMillis)
        }
        if (item.mimeType == "video/mp4") {
            remuxDownloadedMp4(context, resolver, uri, item.open)
        }
        resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        return uri
    } catch (error: Throwable) {
        resolver.delete(uri, null, null)
        throw AttachmentTransferException(
            if (isGalleryMediaMime(item.mimeType)) "无法按本次下载时间保存到图库，文件未保留。" else "文件未能保存到下载目录。",
            error,
        )
    }
}

private class AttachmentTransferException(message: String, cause: Throwable) : IllegalStateException(message, cause)

private val downloadedImageExifTimeFormatter = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")
private val downloadedImageExifOffsetFormatter = DateTimeFormatter.ofPattern("XXX")

/** Rewrites only temporal EXIF on the new gallery copy; pixels and all other metadata stay intact. */
@androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
private fun stampDownloadedImageTakenAt(resolver: android.content.ContentResolver, uri: Uri, downloadedAtMillis: Long) {
    val downloadedAt = java.time.Instant.ofEpochMilli(downloadedAtMillis).atZone(ZoneId.systemDefault())
    val exifTime = downloadedImageExifTimeFormatter.format(downloadedAt)
    val offset = downloadedImageExifOffsetFormatter.format(downloadedAt)
    resolver.openFileDescriptor(uri, "rw")?.use { descriptor ->
        val exif = ExifInterface(descriptor.fileDescriptor)
        exif.setAttribute(ExifInterface.TAG_DATETIME, exifTime)
        exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, exifTime)
        exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, exifTime)
        exif.setAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL, offset)
        exif.setAttribute(ExifInterface.TAG_OFFSET_TIME_DIGITIZED, offset)
        exif.saveAttributes()
    }
}

/** MP4 creation metadata belongs to the source file, so create a new local container for this download. */
@androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
private fun remuxDownloadedMp4(
    context: android.content.Context,
    resolver: android.content.ContentResolver,
    destination: Uri,
    sourceOpen: () -> java.io.InputStream,
) {
    val source = java.io.File.createTempFile("nanfeng-ai-download-", ".mp4", context.cacheDir)
    try {
        sourceOpen().use { input -> source.outputStream().use(input::copyTo) }
        resolver.openFileDescriptor(destination, "rw")?.use { output ->
            val extractor = android.media.MediaExtractor()
            val muxer = android.media.MediaMuxer(
                output.fileDescriptor,
                android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4,
            )
            var muxerStarted = false
            try {
                extractor.setDataSource(source.absolutePath)
                val outputTracks = IntArray(extractor.trackCount) { -1 }
                repeat(extractor.trackCount) { inputTrack ->
                    outputTracks[inputTrack] = muxer.addTrack(extractor.getTrackFormat(inputTrack))
                    extractor.selectTrack(inputTrack)
                }
                check(outputTracks.isNotEmpty()) { "视频没有可写入的轨道" }
                muxer.start()
                muxerStarted = true
                var buffer = java.nio.ByteBuffer.allocate(1024 * 1024)
                val info = android.media.MediaCodec.BufferInfo()
                while (true) {
                    val inputTrack = extractor.sampleTrackIndex
                    if (inputTrack < 0) break
                    val sampleSize = extractor.sampleSize.toInt()
                    if (sampleSize > buffer.capacity()) buffer = java.nio.ByteBuffer.allocate(sampleSize)
                    buffer.clear()
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) break
                    val flags = if (extractor.sampleFlags and android.media.MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                        android.media.MediaCodec.BUFFER_FLAG_KEY_FRAME
                    } else 0
                    info.set(0, size, extractor.sampleTime, flags)
                    muxer.writeSampleData(outputTracks[inputTrack], buffer, info)
                    extractor.advance()
                }
            } finally {
                extractor.release()
                if (muxerStarted) muxer.stop()
                muxer.release()
            }
        } ?: error("无法创建视频下载文件")
    } finally {
        source.delete()
    }
}

private fun isGalleryMediaMime(mimeType: String): Boolean = mimeType.startsWith("image/") || mimeType.startsWith("video/")

private fun attachmentTransferFileName(request: AttachmentTransferRequest): String = attachmentTransferFileName(request.displayName, request.id)

private fun attachmentTransferFileName(displayName: String?, id: AttachmentId): String = displayName
    ?.substringAfterLast('/')
    ?.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
    ?.takeIf { it.isNotBlank() }
    ?: "南枫AI附件-${id.value}"

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun TemporaryConversationPane(
    recovery: TemporaryConversationRecovery,
    modelOptions: List<Pair<String?, String>>,
    onDraftChanged: (String) -> Unit,
    onModelOverrideChanged: (String?) -> Unit,
    onSubmit: () -> Unit,
    onAddCamera: () -> Unit,
    onAddImage: () -> Unit,
    onAddFile: () -> Unit,
    attachmentPreviews: Map<AttachmentId, ConversationAttachmentPreview>,
    onRemoveAttachment: (AttachmentId) -> Unit,
    onOpenImagePreview: (AttachmentId) -> Unit,
    onOpenPdfPreview: (AttachmentId) -> Unit,
    onOpenVideoPreview: (AttachmentId) -> Unit,
    onOpenAudioPreview: (AttachmentId) -> Unit,
    onOpenTextPreview: (AttachmentId) -> Unit,
    onExit: () -> Unit,
    onOpenNavigation: () -> Unit,
    onEnterWork: () -> Unit,
) {
    var composerMenu by remember { mutableStateOf(ComposerMenu.NONE) }
    var composerAddAnchor by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var composerModelAnchor by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var draftText by rememberSaveable(recovery.id.value) { mutableStateOf(recovery.draftText) }
    val listState = rememberLazyListState()
    var sentFromMessageCount by remember(recovery.id) { mutableStateOf<Int?>(null) }
    LaunchedEffect(recovery.updatedAt) {
        draftText = recovery.draftText
    }
    LaunchedEffect(recovery.messages.size, sentFromMessageCount) {
        val sentCount = sentFromMessageCount
        if (sentCount != null && recovery.messages.size > sentCount && listState.layoutInfo.totalItemsCount > 0) {
            listState.scrollToItem(listState.layoutInfo.totalItemsCount - 1)
            sentFromMessageCount = null
        }
    }
    val temporaryAutoFacts = com.nanzhufeng.ai.domain.AutoRoutingFacts(
        hasAttachment = recovery.draftAttachmentIds.isNotEmpty(),
        requiresComplexReasoning = com.nanzhufeng.ai.domain.AutoRoutingTaskClassifier.requiresComplexReasoning(draftText),
    )
    val selectedPresets = recovery.modelOverrideId
        ?.let { id -> com.nanzhufeng.ai.domain.ComposerModelRoutingCatalog.choice(id).routes }
        ?: listOf(com.nanzhufeng.ai.domain.AutoModelRouter.resolve(temporaryAutoFacts))
    val windowWidth = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
    val modelLabel = if (windowWidth >= ComposerModelExpandedBreakpoint) {
        composerModelFullDisplayLabel(selectedPresets)
    } else {
        composerModelDisplayLabel(selectedPresets)
    }
    Box(Modifier.fillMaxSize()) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = ConversationTranscriptPageGutter, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = ConversationTranscriptContentPadding,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(recovery.messages, key = { it.id }) { message ->
                RightAlignedUserBubble {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (message.text.isNotBlank()) Text(message.text, color = BodyText)
                        if (message.attachmentIds.isNotEmpty()) Icon(
                            Icons.Rounded.AttachFile,
                            contentDescription = "附件",
                            tint = SecondaryText,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
    ConversationShellHeader(
        workMode = false,
        onModeChanged = { selectedWork -> if (selectedWork) onEnterWork() },
        onLeftAction = onOpenNavigation,
        leftIcon = Icons.Rounded.Menu,
        leftDescription = "打开对话导航",
        onTemporaryAction = onExit,
        temporaryTint = AccentOrange,
        showContentActions = false,
        onCreateConversation = {},
        onConversationActionsRequested = {},
        modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(horizontal = 18.dp, vertical = 18.dp),
    )
    Box(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .imePadding()
            .padding(start = 18.dp, end = 18.dp, bottom = 6.dp),
    ) {
        ConversationComposerDock(
            value = draftText,
            onValueChange = { value -> draftText = value; onDraftChanged(value) },
            onOpenAttachments = { composerMenu = if (composerMenu == ComposerMenu.ATTACHMENTS) ComposerMenu.NONE else ComposerMenu.ATTACHMENTS },
            attachmentsEnabled = true,
            attachmentDescription = "添加附件",
            onAttachmentAnchorChanged = { composerAddAnchor = it },
            attachmentPreview = if (recovery.draftAttachmentIds.isNotEmpty()) {
                {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        recovery.draftAttachmentIds.forEach { id ->
                            attachmentPreviews[id]?.let { preview ->
                                ComposerAttachmentPreview(
                                    preview = preview,
                                    displayName = preview.displayName,
                                    mimeType = preview.mimeType,
                                    byteCount = preview.byteCount,
                                    onRemove = { onRemoveAttachment(id) },
                                    onOpenImagePreview = onOpenImagePreview,
                                    onOpenPdfPreview = onOpenPdfPreview,
                                    onOpenVideoPreview = onOpenVideoPreview,
                                    onOpenAudioPreview = onOpenAudioPreview,
                                    onOpenTextPreview = onOpenTextPreview,
                                )
                            }
                        }
                    }
                }
            } else null,
        ) {
            ComposerModelEntry(
                label = modelLabel,
                onClick = { composerMenu = if (composerMenu == ComposerMenu.MODEL) ComposerMenu.NONE else ComposerMenu.MODEL },
                onAnchorChanged = { composerModelAnchor = it },
            )
            ComposerSendButton(
                onClick = {
                    sentFromMessageCount = recovery.messages.size
                    onSubmit()
                },
                enabled = draftText.isNotBlank() || recovery.draftAttachmentIds.isNotEmpty(),
                contentDescription = "发送消息",
            )
        }
    }
    ComposerMenuOverlay(
        menu = composerMenu,
        addAnchor = composerAddAnchor,
        modelAnchor = composerModelAnchor,
        attachmentActions = listOf(
            ComposerAttachmentAction(Icons.Rounded.PhotoCamera, "相机") { composerMenu = ComposerMenu.NONE; onAddCamera() },
            ComposerAttachmentAction(Icons.Rounded.AddPhotoAlternate, "图片") { composerMenu = ComposerMenu.NONE; onAddImage() },
            ComposerAttachmentAction(Icons.Rounded.AttachFile, "文件") { composerMenu = ComposerMenu.NONE; onAddFile() },
        ),
        modelOptions = modelOptions,
        selectedModelId = recovery.modelOverrideId,
        onDismiss = { composerMenu = ComposerMenu.NONE },
        onSelectModel = { modelId -> composerMenu = ComposerMenu.NONE; onModelOverrideChanged(modelId) },
    )
    }
}

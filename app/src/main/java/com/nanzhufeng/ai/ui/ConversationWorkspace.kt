package com.nanzhufeng.ai.ui

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import android.widget.VideoView
import android.media.MediaPlayer
import androidx.core.content.FileProvider
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.res.painterResource
import com.nanzhufeng.ai.R
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.focus.onFocusChanged
import androidx.activity.compose.BackHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.nanzhufeng.ai.domain.ConversationActionKind
import com.nanzhufeng.ai.domain.ConversationListVirtualizationContract
import com.nanzhufeng.ai.domain.InlinePresentation
import com.nanzhufeng.ai.domain.ModelPresetId
import com.nanzhufeng.ai.domain.NanfengModelServiceCatalog
import com.nanzhufeng.ai.domain.MessageNodeId
import com.nanzhufeng.ai.domain.PresentationBlock
import com.nanzhufeng.ai.domain.PresentedMessage
import com.nanzhufeng.ai.domain.PresentedTranscriptMessage
import com.nanzhufeng.ai.domain.ConversationListScope
import com.nanzhufeng.ai.domain.ConversationManagementAction
import com.nanzhufeng.ai.domain.AttachmentId
import com.nanzhufeng.ai.domain.ConversationAttachmentPreview
import com.nanzhufeng.ai.domain.ConversationAttachmentOriginalPreview
import com.nanzhufeng.ai.domain.ConversationAttachmentPdfPreview
import com.nanzhufeng.ai.domain.ConversationAttachmentVideoPreview
import com.nanzhufeng.ai.domain.ConversationAttachmentAudioPreview
import com.nanzhufeng.ai.domain.ConversationAttachmentTextPreview
import com.nanzhufeng.ai.domain.ConversationSearchCategory
import com.nanzhufeng.ai.domain.ConversationAttachmentSearchHit
import com.nanzhufeng.ai.domain.ProjectId
import com.nanzhufeng.ai.domain.ProjectSnapshot
import com.nanzhufeng.ai.domain.TemporaryConversationRecovery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

private val ComposerControlGlyphSize = 24.dp
private val ComposerAddGlyphSize = 19.2.dp
private val ComposerSendSurfaceSize = 36.dp
private val ComposerSendGlyphSize = 18.dp
private val ComposerStopGlyphSize = 22.5.dp

/** A root overlay is deliberately outside the composer measurement tree. */
private enum class ComposerMenu { NONE, ATTACHMENTS, MODEL }

private data class ComposerAttachmentAction(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
)

private val LocalAttachmentTransferRequest = staticCompositionLocalOf<(AttachmentId, AttachmentTransferAction) -> Unit> {
    { _, _ -> }
}

/** Real local Room conversations only. This is intentionally a workspace, not a permanent QA card. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun ConversationWorkspaceDialog(
    state: ConversationFoundationUiState,
    onDismiss: () -> Unit,
    onCreate: () -> Unit,
    onSelect: (com.nanzhufeng.ai.domain.ConversationId) -> Unit,
    onSurfaceChanged: (com.nanzhufeng.ai.domain.ConversationSurface) -> Unit,
    onDraftChanged: (String) -> Unit,
    onSubmitDraft: () -> Unit,
    onRequestNormalChatExternalSendConfirmation: () -> Unit,
    onSetNormalChatExternalSendAcknowledgement: (Boolean) -> Unit,
    onConfirmNormalChatExternalSend: () -> Unit,
    onExpireNormalChatExternalSendConfirmation: () -> Unit,
    onDismissNormalChatExternalSendConfirmation: () -> Unit,
    onRequestCompare: () -> Unit,
    onStartFixture: () -> Unit,
    onStartFailureFixture: () -> Unit,
    onStop: () -> Unit,
    onAction: (ConversationActionKind, ModelPresetId?) -> Unit,
    onSwitchBranch: (com.nanzhufeng.ai.domain.MessageNodeId) -> Unit,
    onBranchFromMessage: (com.nanzhufeng.ai.domain.MessageNodeId) -> Unit,
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
    onManage: (com.nanzhufeng.ai.domain.Conversation, ConversationManagementAction, String?) -> Unit,
    onExport: () -> Unit,
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
    onRequestAttachmentTransfer: (AttachmentId, AttachmentTransferAction) -> Unit,
    onRequestAttachmentTransfers: (List<AttachmentId>, AttachmentTransferAction) -> Unit,
    onConsumeAttachmentTransfer: (AttachmentId) -> Unit,
    onEnterTemporary: () -> Unit,
    onUpdateTemporaryDraft: (String) -> Unit,
    onUpdateTemporaryModelOverride: (String?) -> Unit,
    onSubmitTemporaryDraft: () -> Unit,
    onExitTemporary: () -> Unit,
    onSelectP6GModel: (String?) -> Unit,
    onAddTemporaryCamera: () -> Unit,
    onAddTemporaryImage: () -> Unit,
    onAddTemporaryFile: () -> Unit,
    onRemoveTemporaryDraftAttachment: (AttachmentId) -> Unit,
    projects: List<ProjectSnapshot>,
    currentProjectId: ProjectId?,
    onAssignProject: (com.nanzhufeng.ai.domain.Conversation, ProjectId?) -> Unit,
    onOpenRoute: (P5ARoute) -> Unit,
    drawerOpen: Boolean,
    onDrawerOpenChanged: (Boolean) -> Unit,
) {
    var choosingModel by rememberSaveable { mutableStateOf(false) }
    var renameVisible by rememberSaveable { mutableStateOf(false) }
    var renameText by rememberSaveable { mutableStateOf("") }
    var editingMessageId by rememberSaveable { mutableStateOf<String?>(null) }
    var editingText by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    val shareMessage: (PresentedTranscriptMessage) -> Unit = { transcript ->
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, presentedMessagePlainText(transcript.message))
        }, "分享本地消息"))
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
    // The committed domain surface owns both the segmented selection and the canvas. Keeping
    // no local mirror prevents the header from moving before the matching transcript is ready.
    val workMode = state.surface == com.nanzhufeng.ai.domain.ConversationSurface.WORK
    var searchPageVisible by rememberSaveable { mutableStateOf(false) }
    // Chat and Work have different persisted content owners, so they also keep distinct
    // viewport owners.  Hoisting both states keeps a mode swap from recreating a list at
    // its default (latest) position, while rememberLazyListState retains them on recreation.
    val chatTranscriptListState = rememberLazyListState()
    val workTranscriptListState = rememberLazyListState()
    var chatFollowLatest by rememberSaveable { mutableStateOf(true) }
    var workFollowLatest by rememberSaveable { mutableStateOf(true) }
    var composerMenu by remember { mutableStateOf(ComposerMenu.NONE) }
    var composerAddAnchor by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var composerModelAnchor by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    val density = LocalDensity.current
    val clipboard = LocalClipboardManager.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val drawerScope = rememberCoroutineScope()
    val windowWidth = with(density) { LocalWindowInfo.current.containerSize.width.toDp() }
    // The inner display is a separate expanded contract: navigation can use two thirds of
    // the wide canvas, while the outer display keeps its compact, one-hand drawer width.
    val drawerWidth = if (windowWidth >= 600.dp) windowWidth * (2f / 3f) else 320.dp
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
    CompositionLocalProvider(LocalAttachmentTransferRequest provides onRequestAttachmentTransfer) {
    state.attachmentTransfer?.let { transfer ->
        LaunchedEffect(transfer.id, transfer.action, transfer.bytes) {
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
                .onFailure { error ->
                    Toast.makeText(context, error.message ?: "文件操作失败，请重试", Toast.LENGTH_SHORT).show()
                }
            onConsumeAttachmentTransfer(transfer.id)
        }
    }
    ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(drawerContainerColor = Color.White, modifier = Modifier.requiredWidth(drawerWidth)) {
                    ConversationNavigationDrawer(
                        state = state,
                        onCreate = { onCreate(); drawerScope.launch { drawerState.close() } },
                        onSelect = { id -> onSelect(id); drawerScope.launch { drawerState.close() } },
                        onScope = onListScope,
                        onOpenSearchPage = { searchPageVisible = true; drawerScope.launch { drawerState.close() } },
                        onManage = onManage,
                        workMode = workMode,
                        onModeChanged = { enabled -> onSurfaceChanged(if (enabled) com.nanzhufeng.ai.domain.ConversationSurface.WORK else com.nanzhufeng.ai.domain.ConversationSurface.CHAT) },
                        onRequestRename = { conversation -> onSelect(conversation.id); renameText = conversation.title; renameVisible = true },
                        onRequestProject = { conversation -> onSelect(conversation.id); choosingProject = true },
                        onRequestDelete = { conversation -> onSelect(conversation.id); confirmingDelete = true },
                        onRequestConversationActions = { conversation, anchor ->
                            conversationActionTarget = ConversationActionMenuTarget(conversation.id.value, anchor)
                        },
                        onOpenRoute = { route -> onDismiss(); onOpenRoute(route) },
                        onExport = { drawerScope.launch { drawerState.close() }; onExport() },
                    )
                }
            },
        ) {
            Surface(color = Color.White, modifier = Modifier.fillMaxSize()) {
            if (state.temporaryRecovery != null) {
                TemporaryConversationPane(
                    recovery = state.temporaryRecovery,
                    modelOptions = listOf(null to "自动") + state.p6gCatalog?.candidates.orEmpty().map { it.modelId to it.displayName },
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
            } else Box(Modifier.fillMaxSize()) {
                // A first launch must remain an actually empty local truth until the user chooses
                // “新对话” from the drawer. In particular, OpenDocument v2 restore may safely run
                // before any business owner exists; the normal drawer action still creates the
                // same chat-first composer on explicit user intent.
                var workSentFromMessageCount by remember(state.selectedConversationId) { mutableStateOf<Int?>(null) }
                var chatSentFromMessageCount by remember(state.selectedConversationId) { mutableStateOf<Int?>(null) }
                var floatingComposerHeight by remember { mutableStateOf(60.dp) }
                val jumpToLatestBottomPadding = floatingComposerHeight + 4.dp
                val showFloatingComposer = state.draft != null && !state.searchPanelOpen
                // The header occupies no layout row or reserved top band: transcript content
                // continues behind the three floating controls, just like the bottom composer.
                Column(
                    modifier = Modifier.fillMaxSize().padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                // The drawer owns navigation and management. Keeping them out of the chat canvas
                // leaves one scroll owner for messages and a permanently reachable composer.
                // The persisted surface is the content owner. `workMode` only gives the
                // segmented control immediate visual feedback while the ViewModel reloads.
                // Never let that transient UI value attach the other surface's messages to
                // a saved list state during a mode switch.
                if (state.surface == com.nanzhufeng.ai.domain.ConversationSurface.WORK) {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        ConversationWorkScope(
                            state = state,
                            attachmentPreviews = state.attachmentPreviews,
                            listState = workTranscriptListState,
                            followLatest = workFollowLatest,
                            jumpToLatestBottomPadding = jumpToLatestBottomPadding,
                            onFollowLatestChanged = { workFollowLatest = it },
                            sentFromMessageCount = workSentFromMessageCount,
                            onSentToLatestConsumed = { workSentFromMessageCount = null },
                            onLongPress = { messageId, anchorBounds, pressPosition -> messageActionTarget = MessageActionMenuTarget(messageId.value, anchorBounds, pressPosition) },
                            onCopyAssistant = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(presentedMessagePlainText(it.message))) },
                            onShareAssistant = shareMessage,
                            onBranchAssistant = onBranchFromMessage,
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
                    Spacer(Modifier.weight(1f))
                } else {
                    val listState = chatTranscriptListState
                    // Sending is an explicit user request to resume at the newest message,
                    // even when they were reading older history immediately beforehand.
                    // Keep the request local to this conversation and wait for the persisted
                    // message append before moving the only transcript scroll owner.
                    LaunchedEffect(listState) {
                        snapshotFlow {
                            val layout = listState.layoutInfo
                            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
                            layout.totalItemsCount == 0 || lastVisible >= layout.totalItemsCount - 1
                        }.collect { atLatest -> chatFollowLatest = atLatest }
                    }
                    LaunchedEffect(state.searchAnchorMessageId, state.messages) {
                        state.searchAnchorMessageId?.let { anchor ->
                            state.messages.indexOfFirst { it.message.messageId == anchor }.takeIf { it >= 0 }?.let { index -> listState.scrollToItem(index) }
                        }
                    }
                    LaunchedEffect(state.messages.size, chatSentFromMessageCount, chatFollowLatest) {
                        val sentCount = chatSentFromMessageCount
                        if (sentCount != null && state.messages.size > sentCount && listState.layoutInfo.totalItemsCount > 0) {
                            listState.scrollToItem(listState.layoutInfo.totalItemsCount - 1)
                            chatSentFromMessageCount = null
                        } else if (chatFollowLatest && listState.layoutInfo.totalItemsCount > 0) {
                            listState.scrollToItem(listState.layoutInfo.totalItemsCount - 1)
                        }
                    }
                    val showJumpToLatest by remember {
                        derivedStateOf {
                            val layout = listState.layoutInfo
                            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
                            layout.totalItemsCount > 0 && lastVisible < layout.totalItemsCount - 1
                        }
                    }
                    val showInnerTranscriptRail = windowWidth >= 600.dp && state.messages.size > 1
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        LazyColumn(
                            state = listState,
                            // This is scroll content space, not a painted bottom bar: it lets
                            // the last message rise clear of the floating composer.
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 86.dp),
                            // Reserve a dedicated edge lane so the visual scrollbar never
                            // overlaps readable messages, media, or their action targets.
                            modifier = Modifier.fillMaxSize().padding(end = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                        if (state.importedFromChatGptExport) item(key = "chatgpt-imported-provenance", contentType = "chatgpt-imported-provenance") {
                            Surface(
                                color = Color(0xFFF1F8F4),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    "从 ChatGPT 导入 · 本地静态文本，不关联模型、Provider、费用或调用记录。",
                                    modifier = Modifier.padding(12.dp),
                                    color = SecondaryText,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        if (state.importedFromClaudeExport) item(key = "claude-imported-provenance", contentType = "claude-imported-provenance") {
                            Surface(color = Color(0xFFF1F8F4), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                                Text("从 Claude 导入 · 本地静态文本，不关联模型、Provider、费用或调用记录。", modifier = Modifier.padding(12.dp), color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        val editableById = state.editableUserMessages.associateBy { it.messageId }
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
                                    onLongPress = { messageId, anchorBounds, pressPosition -> messageActionTarget = MessageActionMenuTarget(messageId.value, anchorBounds, pressPosition) },
                                    onCopyAssistant = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(presentedMessagePlainText(it.message))) },
                                    onShareAssistant = shareMessage,
                                    onBranchAssistant = onBranchFromMessage,
                                    onOpenImagePreview = onOpenImagePreview,
                                    onOpenPdfPreview = onOpenPdfPreview,
                                    onOpenVideoPreview = onOpenVideoPreview,
                                    onOpenAudioPreview = onOpenAudioPreview,
                                    onOpenTextPreview = onOpenTextPreview,
                                )
                            }
                        }
                        }
                        if (showInnerTranscriptRail) {
                            TranscriptPositionRail(
                                messages = state.messages,
                                listState = listState,
                                onSelect = { index ->
                                    drawerScope.launch { listState.animateScrollToItem(index) }
                                },
                                modifier = Modifier.align(Alignment.CenterStart).padding(start = 2.dp),
                            )
                        }
                        TranscriptScrollIndicator(
                            listState = listState,
                            modifier = Modifier.align(Alignment.CenterEnd).offset(x = 12.dp).padding(top = 8.dp, bottom = 8.dp),
                        )
                        if (showJumpToLatest) JumpToLatestButton(
                            onClick = { drawerScope.launch { listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1) } },
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = jumpToLatestBottomPadding),
                        )
                    }
                }
                }
                ConversationShellHeader(
                    workMode = workMode,
                    onModeChanged = { enabled -> onSurfaceChanged(if (enabled) com.nanzhufeng.ai.domain.ConversationSurface.WORK else com.nanzhufeng.ai.domain.ConversationSurface.CHAT) },
                    onLeftAction = { drawerScope.launch { drawerState.open() } },
                    leftIcon = Icons.Outlined.Menu,
                    leftDescription = "打开对话导航",
                    onTemporaryAction = onEnterTemporary,
                    temporaryTint = SecondaryText,
                    modifier = Modifier.align(Alignment.TopCenter).padding(horizontal = 18.dp, vertical = 18.dp),
                )
                // The composer is a true overlay: only its own rounded surface obscures the
                // transcript. The page canvas stays visible beneath it rather than becoming a
                // full-width white bottom bar.
                if (showFloatingComposer) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(start = 18.dp, end = 18.dp, bottom = 6.dp),
                    ) {
                        Box(
                            modifier = Modifier.onGloballyPositioned { coordinates ->
                                floatingComposerHeight = with(density) { coordinates.size.height.toDp() }
                            },
                        ) {
                        DraftComposer(
                            state = state,
                            onDraftChanged = onDraftChanged,
                            onSubmit = {
                                if (state.surface == com.nanzhufeng.ai.domain.ConversationSurface.WORK) {
                                    workSentFromMessageCount = state.messages.size
                                } else {
                                    chatSentFromMessageCount = state.messages.size
                                }
                                onSubmitDraft()
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
                            onRequestCompare = onRequestCompare,
                            onAddAnchorChanged = { composerAddAnchor = it },
                            onModelAnchorChanged = { composerModelAnchor = it },
                        )
                        }
                    }
                }
                // This sibling is outside the transcript and floating composer measurement.
                ComposerMenuOverlay(
                    menu = composerMenu,
                    addAnchor = composerAddAnchor,
                    modelAnchor = composerModelAnchor,
                    attachmentActions = listOf(
                        ComposerAttachmentAction(Icons.Outlined.PhotoCamera, "相机") { composerMenu = ComposerMenu.NONE; onAddCamera() },
                        ComposerAttachmentAction(Icons.Outlined.AddPhotoAlternate, "添加图片") { composerMenu = ComposerMenu.NONE; onAddImage() },
                        ComposerAttachmentAction(Icons.Outlined.AttachFile, "添加文件") { composerMenu = ComposerMenu.NONE; onAddFile() },
                    ),
                    modelOptions = listOf(null to "自动") + state.p6gCatalog?.candidates.orEmpty().map { it.modelId to it.displayName },
                    selectedModelId = state.p6gConversationOverride?.modelId,
                    onDismiss = { composerMenu = ComposerMenu.NONE },
                    onSelectModel = { modelId -> composerMenu = ComposerMenu.NONE; onSelectP6GModel(modelId) },
                    onSelectCompare = { composerMenu = ComposerMenu.NONE; onRequestCompare() },
                )
            }
        }
    }
    if (searchPageVisible) {
        ConversationSearchPage(
            state = state,
            onDismiss = {
                searchPageVisible = false
                onDrawerOpenChanged(true)
                drawerScope.launch { drawerState.open() }
            },
            onQueryChanged = onSearchChanged,
            onSubmit = onSearchRequested,
            onSelectCategory = { category ->
                // The search owner re-runs an existing query after a category switch.
                onSearchCategoryChanged(category)
            },
            onOpenHistory = onSearchFocus,
            onCloseHistory = onCloseSearchHistory,
            onFillHistory = onFillSearchHistory,
            onClearHistory = onClearSearchHistory,
            onOpenTextHit = { hit -> onOpenSearchHit(hit); searchPageVisible = false },
            onOpenAttachmentHit = { hit ->
                onOpenSearchHit(com.nanzhufeng.ai.domain.ConversationSearchHit(hit.conversationId, hit.messageNodeId, hit.title, hit.attachment.displayName ?: "本地附件", false))
                searchPageVisible = false
            },
        )
    }
    // Register after ModalNavigationDrawer so this handler wins over the drawer's internal
    // callback: physical left/right Back closes into the current chat canvas, never Activity.
    if (drawerState.isOpen) BackHandler { drawerScope.launch { drawerState.close() } }
    conversationActionTarget?.let { target ->
        val conversation = state.conversations.firstOrNull { it.id.value == target.conversationId }
        if (conversation == null) conversationActionTarget = null else ConversationActionSheet(
            conversation = conversation,
            anchor = target.anchor,
            workMode = workMode,
            onDismiss = { conversationActionTarget = null },
            onManage = onManage,
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
        )
    }
    if (choosingProject) ProjectAssignmentDialog(projects, currentProjectId, onAssign = { projectId, assigned ->
        state.conversations.firstOrNull { it.id == state.selectedConversationId }?.let { conversation ->
            onAssignProject(conversation, if (assigned) projectId else null)
        }
    }) { choosingProject = false }
    if (choosingModel) {
        AlertDialog(
            onDismissRequest = { choosingModel = false }, containerColor = Color.White, shape = RoundedCornerShape(24.dp),
            title = { Text("换模型重答") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("仅切换本机模型偏好，不会读取 Key 或联网。", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
                    NanfengModelServiceCatalog.presets.forEach { preset ->
                        OutlinedButton(onClick = { choosingModel = false; onAction(ConversationActionKind.CHANGE_MODEL, preset.id) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text(preset.displayName) }
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
            headline = listOfNotNull(
                formatTranscriptTimeOrNull(transcript.metadata.createdAt),
                transcript.metadata.modelSnapshotLabel?.takeIf { it.isNotBlank() },
            ).joinToString(" · "),
            editable = transcript.message.role == com.nanzhufeng.ai.domain.MessageRole.USER && editableForMenu[transcript.message.messageId] != null,
            onDismiss = { messageActionTarget = null },
        ) {
            MessageContextAction(Icons.Outlined.ContentCopy, "复制") {
                clipboard.setText(androidx.compose.ui.text.AnnotatedString(presentedMessagePlainText(transcript.message)))
                messageActionTarget = null
            }
            MessageContextAction(Icons.Outlined.SelectAll, "选择文本") {
                selectingTextMessageId = rawId
                messageActionTarget = null
            }
            if (transcript.message.role == com.nanzhufeng.ai.domain.MessageRole.USER && editableForMenu[transcript.message.messageId] != null) {
                MessageContextAction(Icons.Outlined.Edit, "编辑消息") {
                    editingMessageId = rawId
                    editingText = editableForMenu.getValue(transcript.message.messageId).text
                    messageActionTarget = null
                }
            }
            MessageContextAction(Icons.Outlined.Share, "分享") {
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
        onDismissRequest = { confirmingDelete = false }, containerColor = Color.White, shape = RoundedCornerShape(24.dp),
        title = { Text("移入回收站？") },
        text = { Text("删除与归档不同：会话消息树不会物理删除，可从回收站恢复。") },
        confirmButton = { Button(onClick = {
            state.conversations.firstOrNull { it.id == state.selectedConversationId }?.let { onManage(it, ConversationManagementAction.SOFT_DELETE, null) }
            confirmingDelete = false
        }, shape = RoundedCornerShape(14.dp)) { Text("移入回收站") } },
        dismissButton = { TextButton(onClick = { confirmingDelete = false }, shape = RoundedCornerShape(14.dp)) { Text("取消") } },
    )
    editingMessageId?.let { rawId ->
        AlertDialog(
            onDismissRequest = { editingMessageId = null }, containerColor = Color.White, shape = RoundedCornerShape(24.dp),
            title = { Text("编辑") },
            text = { OutlinedTextField(editingText, { editingText = it }, modifier = Modifier.fillMaxWidth().p5aKeyboardTraversal(), minLines = 3) },
            confirmButton = {
                Button(onClick = {
                    editingMessageId = null
                    onEditUserMessage(com.nanzhufeng.ai.domain.MessageNodeId(rawId), editingText)
                }, enabled = editingText.isNotBlank(), modifier = Modifier.width(76.dp).height(32.dp), shape = RoundedCornerShape(16.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) { Text("创建分支", fontSize = 13.sp) }
            },
            dismissButton = { TextButton(onClick = { editingMessageId = null }, modifier = Modifier.width(44.dp).height(30.dp), shape = RoundedCornerShape(15.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) { Text("取消", fontSize = 13.sp) } },
        )
    }
    state.imagePreview?.let { preview ->
        val generatedImageIds = state.messages.firstOrNull { message ->
            message.message.blocks.filterIsInstance<PresentationBlock.AttachmentReference>().any { it.attachment.id == preview.id }
        }?.message?.blocks
            ?.filterIsInstance<PresentationBlock.AttachmentReference>()
            ?.map { it.attachment }
            ?.filter { it.mimeType.startsWith("image/") }
            ?.map { it.id }
            .orEmpty()
        ImagePreviewDialog(preview, generatedImageIds, onCloseImagePreview, onRequestAttachmentTransfers)
    }
    state.pdfPreview?.let { preview -> PdfPreviewDialog(preview, onOpenPdfPage, onClosePdfPreview) }
    state.videoPreview?.let { preview -> VideoPreviewDialog(preview, onCloseVideoPreview) }
    state.audioPreview?.let { preview -> AudioPreviewDialog(preview, onCloseAudioPreview) }
    state.textPreview?.let { preview -> TextPreviewDialog(preview, onCloseTextPreview) }
    // P3-J is a root sibling of the workspace.  It is never measured by the transcript,
    // drawer, or composer and therefore cannot move any frozen chat-first geometry.
    state.externalSendConfirmation?.let { confirmation ->
        NormalChatExplicitEgressConfirmationDialog(
            confirmation = confirmation,
            onAcknowledgementChanged = onSetNormalChatExternalSendAcknowledgement,
            onConfirm = onConfirmNormalChatExternalSend,
            onExpired = onExpireNormalChatExternalSendConfirmation,
            onDismiss = onDismissNormalChatExternalSendConfirmation,
        )
    }
    }
}

/**
 * P3-J disclosure-only confirmation.  The default owner always reports an unregistered
 * route, so this surface cannot create egress, read credentials, or claim a charge.
 */
@Composable
private fun NormalChatExplicitEgressConfirmationDialog(
    confirmation: com.nanzhufeng.ai.domain.NormalChatExternalSendConfirmation,
    onAcknowledgementChanged: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onExpired: () -> Unit,
    onDismiss: () -> Unit,
) {
    LaunchedEffect(confirmation.createdAt, confirmation.expiresAt) {
        delay(com.nanzhufeng.ai.domain.NormalChatRealTextExecutionOwner.EXTERNAL_SEND_CONFIRMATION_TTL_MINUTES * 60_000L)
        onExpired()
    }
    val expired = confirmation.blocker == com.nanzhufeng.ai.domain.NormalChatExternalSendBlocker.CONFIRMATION_EXPIRED
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true, dismissOnClickOutside = true),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            DismissibleDialogBackdrop(onDismiss)
            Surface(
                modifier = Modifier.padding(horizontal = 20.dp).widthIn(max = 480.dp).fillMaxWidth(),
                color = Color.White,
                contentColor = BodyText,
                shape = RoundedCornerShape(24.dp),
                shadowElevation = 8.dp,
            ) {
                Column(
                    modifier = Modifier.padding(start = 24.dp, top = 22.dp, end = 24.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("确认发送给第三方服务", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text("服务商：${confirmation.providerDisplayName}（${confirmation.providerHandle}）", style = MaterialTheme.typography.bodyMedium)
                    Text("模型：${confirmation.modelDisplayName}（${confirmation.modelId}）\n预设：${confirmation.preset} · 注册表：${confirmation.registrySnapshot}", style = MaterialTheme.typography.bodyMedium)
                    Text("发送内容：仅当前这条用户草稿的文本。不会发送其他会话历史、系统隐藏内容、URI/path、设备或账号资料、Key、调用记录或诊断。", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
                    Text("附件不会发送。${if (confirmation.attachmentCount > 0) "当前草稿含附件，请移除附件或继续当前本地草稿流。" else "本次不读取、预览或上传附件。"}", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
                    Text("费用：价格版本 ${confirmation.priceVersion} · 币种 ${confirmation.currency}\n预估输入/输出上限：未知 · 本次最多预留/可能产生：${confirmation.maximumFee}", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
                    Text(
                        if (expired) "此确认已过期，勾选已撤销；请重新发起请求。" else "确认默认未勾选，并将在 5 分钟后过期。确认后只发送当前这条文字；服务、模型和凭据会在发送前再次检查。",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (expired) MaterialTheme.colorScheme.error else SecondaryText,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = confirmation.acknowledgementChecked,
                            onCheckedChange = { onAcknowledgementChanged(it) },
                            enabled = !expired,
                        )
                        Text("我理解上述仅文本外发范围与费用状态", style = MaterialTheme.typography.bodySmall)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                        TextButton(onClick = onDismiss, shape = RoundedCornerShape(16.dp)) { Text("取消") }
                        Button(
                            onClick = onConfirm,
                            enabled = confirmation.acknowledgementChecked && confirmation.isConfirmable && !expired,
                            shape = RoundedCornerShape(16.dp),
                        ) { Text("确认外发") }
                    }
                }
            }
        }
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
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            ConversationHeaderFloatingIconButton(onClick = onLeftAction) {
                Icon(leftIcon, contentDescription = leftDescription)
            }
            Spacer(Modifier.weight(1f))
            ConversationHeaderFloatingIconButton(onClick = onTemporaryAction) {
                Icon(painterResource(R.drawable.ic_lucide_ghost), contentDescription = "临时聊天", tint = temporaryTint)
            }
        }
        ConversationModeSwitch(
            workMode = workMode,
            onModeChanged = onModeChanged,
            modifier = Modifier.align(Alignment.Center),
        )
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
        modifier = Modifier.size(44.dp),
        color = Color.White,
        shape = CircleShape,
        tonalElevation = 0.dp,
        shadowElevation = 3.dp,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
private fun ConversationModeSwitch(workMode: Boolean, onModeChanged: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = Color(0xFFF1F3F1),
        shape = RoundedCornerShape(50),
        tonalElevation = 0.dp,
        shadowElevation = 3.dp,
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
            containerColor = if (selected) Color.White else Color.Transparent,
            contentColor = if (selected) BodyText else SecondaryText,
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
    ) { Text(label, fontWeight = FontWeight.SemiBold) }
}

/** Work projects the current conversation's local scope; it is not a second module dashboard. */
@Composable
private fun ConversationWorkScope(
    state: ConversationFoundationUiState,
    attachmentPreviews: Map<AttachmentId, ConversationAttachmentPreview>,
    listState: LazyListState,
    followLatest: Boolean,
    jumpToLatestBottomPadding: androidx.compose.ui.unit.Dp,
    onFollowLatestChanged: (Boolean) -> Unit,
    sentFromMessageCount: Int?,
    onSentToLatestConsumed: () -> Unit,
    onLongPress: (MessageNodeId, androidx.compose.ui.geometry.Rect, androidx.compose.ui.geometry.Offset) -> Unit,
    onCopyAssistant: (PresentedTranscriptMessage) -> Unit,
    onShareAssistant: (PresentedTranscriptMessage) -> Unit,
    onBranchAssistant: (MessageNodeId) -> Unit,
    onOpenImagePreview: (AttachmentId) -> Unit,
    onOpenPdfPreview: (AttachmentId) -> Unit,
    onOpenVideoPreview: (AttachmentId) -> Unit,
    onOpenAudioPreview: (AttachmentId) -> Unit,
    onOpenTextPreview: (AttachmentId) -> Unit,
) {
    // Work changes the conversation scope only. It deliberately reuses normal chat rows,
    // including their right alignment and responsive reading width, rather than maintaining
    // a second transcript projection with a different proportion.
    val scope = rememberCoroutineScope()
    LaunchedEffect(listState) {
        snapshotFlow {
            val layout = listState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
            layout.totalItemsCount == 0 || lastVisible >= layout.totalItemsCount - 1
        }.collect(onFollowLatestChanged)
    }
    LaunchedEffect(state.messages.size, sentFromMessageCount, followLatest) {
        val sentCount = sentFromMessageCount
        if (sentCount != null && state.messages.size > sentCount && listState.layoutInfo.totalItemsCount > 0) {
            listState.scrollToItem(listState.layoutInfo.totalItemsCount - 1)
            onSentToLatestConsumed()
        } else if (followLatest && listState.layoutInfo.totalItemsCount > 0) {
            listState.scrollToItem(listState.layoutInfo.totalItemsCount - 1)
        }
    }
    val showJumpToLatest by remember {
        derivedStateOf {
            val layout = listState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
            layout.totalItemsCount > 0 && lastVisible < layout.totalItemsCount - 1
        }
    }
    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        state = listState,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 86.dp),
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.messages.isEmpty()) item(key = "work-empty") {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("", modifier = Modifier.semantics { contentDescription = "工作内容为空" })
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
                    onLongPress = onLongPress,
                    onCopyAssistant = onCopyAssistant,
                    onShareAssistant = onShareAssistant,
                    onBranchAssistant = onBranchAssistant,
                    onOpenImagePreview = onOpenImagePreview,
                    onOpenPdfPreview = onOpenPdfPreview,
                    onOpenVideoPreview = onOpenVideoPreview,
                    onOpenAudioPreview = onOpenAudioPreview,
                    onOpenTextPreview = onOpenTextPreview,
                )
            }
        }
    }
    if (showJumpToLatest) JumpToLatestButton(
        onClick = { scope.launch { listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1) } },
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = jumpToLatestBottomPadding),
    )
    }
}

/** A non-tonal pure-white control; Material FAB's elevation tint is intentionally avoided. */
@Composable
private fun JumpToLatestButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(56.dp),
        color = Color.White,
        contentColor = BodyText,
        shape = CircleShape,
        tonalElevation = 0.dp,
        shadowElevation = 10.dp,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "到最新消息")
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
                Text("${item.modelDisplayName} · ${item.modelId}", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
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
    onCreate: () -> Unit,
    onSelect: (com.nanzhufeng.ai.domain.ConversationId) -> Unit,
    onScope: (ConversationListScope) -> Unit,
    onOpenSearchPage: () -> Unit,
    onManage: (com.nanzhufeng.ai.domain.Conversation, ConversationManagementAction, String?) -> Unit,
    workMode: Boolean,
    onModeChanged: (Boolean) -> Unit,
    onRequestRename: (com.nanzhufeng.ai.domain.Conversation) -> Unit,
    onRequestProject: (com.nanzhufeng.ai.domain.Conversation) -> Unit,
    onRequestDelete: (com.nanzhufeng.ai.domain.Conversation) -> Unit,
    onRequestConversationActions: (com.nanzhufeng.ai.domain.Conversation, androidx.compose.ui.geometry.Rect) -> Unit,
    onOpenRoute: (P5ARoute) -> Unit,
    onExport: () -> Unit,
) {
    val pinned = state.conversations.filter { it.pinnedAt != null }
    val content = state.conversations.filter { it.pinnedAt == null }
    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 84.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Image(
                painter = painterResource(R.drawable.nanfeng_ai_icon_foreground_image),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)),
            )
            Text("南枫 AI", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        Surface(
            color = Color(0xFFF1F2F0),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .combinedClickable(onClick = onOpenSearchPage),
        ) {
            Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(17.dp), tint = SecondaryText)
                Text("搜索", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (pinned.isNotEmpty() && state.listScope == ConversationListScope.ACTIVE) {
            Text("置顶", style = MaterialTheme.typography.labelSmall, color = SecondaryText)
            pinned.forEach { conversation -> ConversationNavigationRow(conversation, conversation.id == state.selectedConversationId, onSelect, onRequestConversationActions) }
        }
        Text("最近", style = MaterialTheme.typography.labelSmall, color = SecondaryText)
        content.forEach { conversation -> ConversationNavigationRow(conversation, conversation.id == state.selectedConversationId, onSelect, onRequestConversationActions) }
    }
    Row(
        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            color = Color.White,
            shape = CircleShape,
            shadowElevation = 6.dp,
        ) {
            IconButton(onClick = { onOpenRoute(P5ARoute.SETTINGS) }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Outlined.Settings, contentDescription = "设置")
            }
        }
        Button(
            onClick = onCreate,
            shape = CircleShape,
            modifier = Modifier.height(44.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp, pressedElevation = 2.dp),
        ) {
            Icon(painterResource(R.drawable.ic_lucide_file_pen), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("新对话")
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
    onScope: (ConversationListScope) -> Unit,
    onManage: (com.nanzhufeng.ai.domain.Conversation, ConversationManagementAction, String?) -> Unit,
    onExport: () -> Unit,
    exchangeExportState: ConversationExchangeExportUiState,
    onExchangeExport: (com.nanzhufeng.ai.domain.ConversationId) -> Unit,
) {
    Surface(color = Color(0xFFF8FAF8), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("管理对话", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            var scopeExpanded by remember { mutableStateOf(false) }
            val selectedScope = if (state.listScope == ConversationListScope.DELETED) "回收站" else "已归档"
            Box {
                OutlinedButton(
                    onClick = { scopeExpanded = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text("查看：$selectedScope", modifier = Modifier.weight(1f))
                    Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "选择对话分类")
                }
                DropdownMenu(
                    expanded = scopeExpanded,
                    onDismissRequest = { scopeExpanded = false },
                    containerColor = Color.White,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    DropdownMenuItem(
                        text = { Text("已归档") },
                        onClick = { onScope(ConversationListScope.ARCHIVED); scopeExpanded = false },
                    )
                    DropdownMenuItem(
                        text = { Text("回收站") },
                        onClick = { onScope(ConversationListScope.DELETED); scopeExpanded = false },
                    )
                }
            }
            val heading = if (state.listScope == ConversationListScope.DELETED) "回收站" else "已归档"
            if (state.listScope == ConversationListScope.ACTIVE) {
                Text("选择一个分类查看对话。", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
            } else if (state.conversations.isEmpty()) {
                Text("暂无${heading}会话。", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
            } else {
                state.conversations.forEach { conversation ->
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(conversation.title, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        OutlinedButton(onClick = {
                            onManage(
                                conversation,
                                if (state.listScope == ConversationListScope.DELETED) ConversationManagementAction.RESTORE_DELETED else ConversationManagementAction.UNARCHIVE,
                                null,
                            )
                        }, shape = RoundedCornerShape(14.dp)) { Text("恢复") }
                    }
                }
            }
            OutlinedButton(onClick = onExport, enabled = state.selectedConversationId != null, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text("导出当前对话") }
            OutlinedButton(
                onClick = { state.selectedConversationId?.let(onExchangeExport) },
                enabled = state.selectedConversationId != null && !exchangeExportState.working,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            ) { Text("导出当前文本会话到 Desktop") }
            Text("只导出当前活动、未归属项目、无草稿且无附件/工具结果的文本会话为 .nfai-exchange；通过系统文件选择器写入并回读校验，不是备份或云同步。", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
            exchangeExportState.notice?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = BrandGreen) }
            exchangeExportState.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = ErrorRed) }
        }
    }
}

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
            Text("置顶", style = MaterialTheme.typography.labelSmall, color = SecondaryText)
            pinned.forEach { conversation -> ConversationNavigationRow(conversation, conversation.id == state.selectedConversationId, onSelect, onRequestConversationActions) }
        }
        Text("最近", style = MaterialTheme.typography.labelSmall, color = SecondaryText)
        listed.forEach { conversation -> ConversationNavigationRow(conversation, conversation.id == state.selectedConversationId, onSelect, onRequestConversationActions) }
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
                OutlinedButton(onClick = { onOpenHit(hit) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.fillMaxWidth()) { Text(hit.title, maxLines = 1); Text(hit.snippet, style = MaterialTheme.typography.bodySmall, color = SecondaryText, maxLines = 3) }
                }
            }
        }
    }
}

/**
 * A separate destination, not a drawer expansion: the source workspace remains intact beneath
 * it and the six categories all consume the same local-only search owner.
 */
@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
private fun ConversationSearchPage(
    state: ConversationFoundationUiState,
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
) {
    val searchPlaceholder = when (state.searchCategory) {
        ConversationSearchCategory.ALL -> "搜索全部内容"
        else -> "搜索${state.searchCategory.label}"
    }
    val imeVisible = WindowInsets.isImeVisible
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        BackHandler(onBack = onDismiss)
        Surface(color = Color.White, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 18.dp, top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, contentDescription = "退出搜索") }
                    Text("搜索", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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
                            Surface(
                                color = if (selected) Color(0xFFF0F1EF) else Color.Transparent,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .then(if (expandedSearchCategories) Modifier else Modifier.weight(1f))
                                    .combinedClickable(onClick = { onSelectCategory(category) }),
                            ) {
                                Text(
                                    category.label,
                                    modifier = if (expandedSearchCategories) Modifier.padding(horizontal = 14.dp, vertical = 9.dp) else Modifier.fillMaxWidth().padding(vertical = 9.dp),
                                    color = if (selected) BodyText else SecondaryText,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(color = Color(0xFFEDEEEB))
                // Keep the whole usable search canvas above the IME. This makes empty-state
                // guidance stay centered in what remains visible, while the bottom controls
                // and history panel share the same coordinate space.
                Box(Modifier.weight(1f).fillMaxWidth().imePadding()) {
                    when (state.searchCategory) {
                        ConversationSearchCategory.IMAGE, ConversationSearchCategory.VIDEO -> SearchAttachmentGrid(
                            hits = state.attachmentSearchResults,
                            previews = state.searchAttachmentPreviews,
                            onOpen = onOpenAttachmentHit,
                            bottomContentPadding = 78.dp,
                        )
                        ConversationSearchCategory.AUDIO, ConversationSearchCategory.FILE -> SearchAttachmentRows(
                            hits = state.attachmentSearchResults,
                            previews = state.searchAttachmentPreviews,
                            onOpen = onOpenAttachmentHit,
                            bottomContentPadding = 78.dp,
                        )
                        ConversationSearchCategory.ALL, ConversationSearchCategory.TEXT -> SearchAllOrTextResults(
                            state = state,
                            onOpenText = onOpenTextHit,
                            onOpenAttachment = onOpenAttachmentHit,
                            bottomContentPadding = 78.dp,
                        )
                    }
                    if (state.searchHistoryOpen) {
                        Box(
                            modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                                detectTapGestures(onTap = { onCloseHistory() })
                            },
                        )
                        SearchHistoryPanel(
                            history = state.searchHistory,
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
                            color = Color(0xFFF1F2F0),
                            shape = RoundedCornerShape(21.dp),
                            modifier = Modifier.weight(1f).height(42.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize().padding(start = 10.dp, end = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(7.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.Search,
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
                            color = Color(0xFFF1F2F0), shape = RoundedCornerShape(21.dp),
                            modifier = Modifier.width(76.dp).height(42.dp).combinedClickable(onClick = onOpenHistory),
                        ) {
                            Row(Modifier.fillMaxSize().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                                Icon(Icons.Outlined.History, contentDescription = null, modifier = Modifier.size(16.dp), tint = SecondaryText)
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
private fun SearchAllOrTextResults(
    state: ConversationFoundationUiState,
    onOpenText: (com.nanzhufeng.ai.domain.ConversationSearchHit) -> Unit,
    onOpenAttachment: (ConversationAttachmentSearchHit) -> Unit,
    bottomContentPadding: androidx.compose.ui.unit.Dp = 0.dp,
) {
    if (state.searchQuery.isBlank()) {
        SearchHint("输入关键词搜索")
        return
    }
    if (state.searchResults.isEmpty() && state.attachmentSearchResults.isEmpty()) {
        SearchHint("没有匹配的本地内容")
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp + bottomContentPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.searchResults.isNotEmpty()) item { Text(if (state.searchCategory == ConversationSearchCategory.ALL) "正文" else "搜索结果", style = MaterialTheme.typography.labelLarge, color = SecondaryText) }
        items(state.searchResults, key = { "text:${it.conversationId.value}:${it.messageNodeId?.value.orEmpty()}" }) { hit ->
            Surface(color = Color(0xFFF9FAF8), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { onOpenText(hit) })) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(hit.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(hit.snippet, color = SecondaryText, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (state.attachmentSearchResults.isNotEmpty()) item { Text("附件", modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelLarge, color = SecondaryText) }
        items(state.attachmentSearchResults, key = { "attachment:${it.conversationId.value}:${it.messageNodeId.value}:${it.attachment.id.value}" }) { hit ->
            SearchAttachmentRow(hit, state.searchAttachmentPreviews[hit.attachment.id], onOpenAttachment)
        }
    }
}

@Composable
private fun SearchAttachmentGrid(
    hits: List<ConversationAttachmentSearchHit>,
    previews: Map<AttachmentId, ConversationAttachmentPreview>,
    onOpen: (ConversationAttachmentSearchHit) -> Unit,
    bottomContentPadding: androidx.compose.ui.unit.Dp = 0.dp,
) {
    if (hits.isEmpty()) { SearchHint("没有匹配的本地附件"); return }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp + bottomContentPadding),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        gridItems(hits, key = { "${it.conversationId.value}:${it.messageNodeId.value}:${it.attachment.id.value}" }) { hit ->
            SearchAttachmentCard(hit, previews[hit.attachment.id], onOpen)
        }
    }
}

@Composable
private fun SearchAttachmentRows(
    hits: List<ConversationAttachmentSearchHit>,
    previews: Map<AttachmentId, ConversationAttachmentPreview>,
    onOpen: (ConversationAttachmentSearchHit) -> Unit,
    bottomContentPadding: androidx.compose.ui.unit.Dp = 0.dp,
) {
    if (hits.isEmpty()) { SearchHint("没有匹配的本地附件"); return }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp + bottomContentPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(hits, key = { "${it.conversationId.value}:${it.messageNodeId.value}:${it.attachment.id.value}" }) { hit ->
            SearchAttachmentRow(hit, previews[hit.attachment.id], onOpen)
        }
    }
}

@Composable
private fun SearchAttachmentCard(hit: ConversationAttachmentSearchHit, preview: ConversationAttachmentPreview?, onOpen: (ConversationAttachmentSearchHit) -> Unit) {
    val bitmap = preview?.thumbnail?.bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
    val isVideo = hit.attachment.mimeType in com.nanzhufeng.ai.domain.CONVERSATION_ALLOWED_VIDEO_MIME_TYPES
    // Image corners are intentionally half of video corners: visible enough to separate a
    // thumbnail from surrounding text, but quieter than the distinct video-card treatment.
    val thumbnailShape = RoundedCornerShape(if (isVideo) 20.dp else 10.dp)
    Surface(color = Color(0xFFF8F9F7), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { onOpen(hit) })) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Box(Modifier.fillMaxWidth().height(104.dp).clip(thumbnailShape).background(Color(0xFFECEFEB)).border(1.dp, Color.Black.copy(alpha = if (isVideo) 0.18f else 0.08f), thumbnailShape), contentAlignment = Alignment.Center) {
                if (bitmap != null) Image(bitmap.asImageBitmap(), contentDescription = "本地附件缩略图", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                else Icon(Icons.Outlined.AttachFile, contentDescription = null, tint = SecondaryText, modifier = Modifier.size(30.dp))
                if (isVideo) Icon(Icons.Outlined.ArrowUpward, contentDescription = "视频", tint = Color.White, modifier = Modifier.align(Alignment.Center).background(BodyText, CircleShape).padding(5.dp))
            }
            Text(hit.attachment.displayName ?: "本地附件", maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            Text(hit.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = SecondaryText)
        }
    }
}

@Composable
private fun SearchAttachmentRow(hit: ConversationAttachmentSearchHit, preview: ConversationAttachmentPreview?, onOpen: (ConversationAttachmentSearchHit) -> Unit) {
    Surface(color = Color(0xFFF8F9F7), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { onOpen(hit) })) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = Color(0xFFECEFEB), shape = RoundedCornerShape(10.dp), modifier = Modifier.size(44.dp)) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.AttachFile, contentDescription = null, tint = SecondaryText) } }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(hit.attachment.displayName ?: "本地附件", fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(hit.title, style = MaterialTheme.typography.bodySmall, color = SecondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            preview?.audioDurationMillis?.let { Text("${it / 1_000}s", style = MaterialTheme.typography.labelSmall, color = SecondaryText) }
        }
    }
}

@Composable
private fun SearchHistoryPanel(history: List<String>, onFill: (String) -> Unit, onClear: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Surface(color = Color.White, shape = RoundedCornerShape(18.dp), shadowElevation = 12.dp, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("搜索历史", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = onClear, enabled = history.isNotEmpty()) { Text("清空") }
                IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) { Icon(Icons.Outlined.Close, contentDescription = "关闭历史") }
            }
            if (history.isEmpty()) Text("暂无已提交的本地搜索。", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
            else history.forEach { query -> TextButton(onClick = { onFill(query) }, modifier = Modifier.fillMaxWidth()) { Text(query, modifier = Modifier.fillMaxWidth(), color = BodyText) } }
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
    onSelect: (com.nanzhufeng.ai.domain.ConversationId) -> Unit,
    onRequestConversationActions: (com.nanzhufeng.ai.domain.Conversation, androidx.compose.ui.geometry.Rect) -> Unit,
) {
    var rowBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    Surface(
        color = if (selected) AccentOrangeSoft else Color.Transparent,
        contentColor = BodyText,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
            .onGloballyPositioned { rowBounds = it.boundsInRoot() }
            .combinedClickable(
                onClick = { onSelect(conversation.id) },
                onLongClick = { rowBounds?.let { onRequestConversationActions(conversation, it) } },
            ),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(conversation.title, modifier = Modifier.weight(1f), fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(conversationListLocalDate(conversation.updatedAt), modifier = Modifier.wrapContentWidth(Alignment.End), style = MaterialTheme.typography.labelSmall, color = SecondaryText, maxLines = 1, softWrap = false, textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }
    }
}

private data class ConversationActionMenuTarget(
    val conversationId: String,
    val anchor: androidx.compose.ui.geometry.Rect,
)

private data class MessageActionMenuTarget(
    val messageId: String,
    val anchorBounds: androidx.compose.ui.geometry.Rect,
    val pressPosition: androidx.compose.ui.geometry.Offset,
)

@Composable
private fun ConversationActionSheet(
    conversation: com.nanzhufeng.ai.domain.Conversation,
    anchor: androidx.compose.ui.geometry.Rect,
    onManage: (com.nanzhufeng.ai.domain.Conversation, ConversationManagementAction, String?) -> Unit,
    workMode: Boolean,
    onRequestRename: (com.nanzhufeng.ai.domain.Conversation) -> Unit,
    onRequestProject: (com.nanzhufeng.ai.domain.Conversation) -> Unit,
    onRequestDelete: (com.nanzhufeng.ai.domain.Conversation) -> Unit,
    onDismiss: () -> Unit,
) {
    val density = LocalDensity.current
    val containerSize = LocalWindowInfo.current.containerSize
    val menuWidth = 224.dp
    val rowCount = if (conversation.deletedAt != null) 1 else 4 + if (workMode) 1 else 0
    val horizontalMargin = with(density) { 16.dp.roundToPx() }
    val verticalGap = with(density) { 6.dp.roundToPx() }
    val menuWidthPx = with(density) { menuWidth.roundToPx() }
    val menuHeightPx = with(density) { ((rowCount * 48).dp + 12.dp).roundToPx() }
    val screenWidthPx = containerSize.width
    val screenHeightPx = containerSize.height
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
            color = Color.White,
            shape = RoundedCornerShape(20.dp),
            shadowElevation = 6.dp,
            modifier = Modifier.width(menuWidth),
        ) {
            Column(Modifier.padding(vertical = 6.dp)) {
                if (conversation.deletedAt != null) {
                    ConversationMenuAction(Icons.Outlined.RestoreFromTrash, "从回收站恢复", onClick = { onDismiss(); onManage(conversation, ConversationManagementAction.RESTORE_DELETED, null) })
                } else {
                    ConversationMenuAction(Icons.Outlined.PushPin, if (conversation.pinnedAt == null) "置顶" else "取消置顶", onClick = { onDismiss(); onManage(conversation, if (conversation.pinnedAt == null) ConversationManagementAction.PIN else ConversationManagementAction.UNPIN, null) })
                    ConversationMenuAction(Icons.Outlined.Edit, "重命名", onClick = { onRequestRename(conversation) })
                    ConversationMenuAction(Icons.AutoMirrored.Outlined.DriveFileMove, if (conversation.projectId == null) "添加到项目" else "移动或移出项目", trailing = Icons.Outlined.ChevronRight, onClick = { onRequestProject(conversation) })
                    if (workMode) ConversationMenuAction(if (conversation.archivedAt == null) Icons.Outlined.Archive else Icons.Outlined.Unarchive, if (conversation.archivedAt == null) "归档" else "恢复", onClick = { onDismiss(); onManage(conversation, if (conversation.archivedAt == null) ConversationManagementAction.ARCHIVE else ConversationManagementAction.UNARCHIVE, null) })
                    ConversationMenuAction(Icons.Outlined.DeleteOutline, "删除", danger = true, onClick = { onRequestDelete(conversation) })
                }
            }
        }
    }
}

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
            Text(label, modifier = Modifier.weight(1f), fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
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
    onLongPress: (MessageNodeId, androidx.compose.ui.geometry.Rect, androidx.compose.ui.geometry.Offset) -> Unit,
    onCopyAssistant: (PresentedTranscriptMessage) -> Unit,
    onShareAssistant: (PresentedTranscriptMessage) -> Unit,
    onBranchAssistant: (MessageNodeId) -> Unit,
    onOpenImagePreview: (AttachmentId) -> Unit,
    onOpenPdfPreview: (AttachmentId) -> Unit,
    onOpenVideoPreview: (AttachmentId) -> Unit,
    onOpenAudioPreview: (AttachmentId) -> Unit,
    onOpenTextPreview: (AttachmentId) -> Unit,
) {
    val message = transcript.message
    val roleVisual = chatRoleVisual(message.role)
    var messageBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    val textBlocks = message.blocks.filterNot { it is PresentationBlock.AttachmentReference }
    val attachmentBlocks = message.blocks.filterIsInstance<PresentationBlock.AttachmentReference>()
    val textContent: @Composable () -> Unit = {
        // Text selection belongs to the rendered transcript, so Android keeps the handles and
        // copy toolbar at the original message position instead of moving to a separate dialog.
        SelectionContainer {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                if (message.blocks.isEmpty()) Text("正在等待本地输出…", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                textBlocks.forEach { block -> PresentationBlockView(block, attachmentPreviews, roleVisual.body, transcript.metadata.createdAt, onOpenImagePreview, onOpenPdfPreview, onOpenVideoPreview, onOpenAudioPreview, onOpenTextPreview) }
            }
        }
    }
    val attachmentContent: @Composable () -> Unit = {
        if (attachmentBlocks.isNotEmpty()) DisableSelection {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                attachmentBlocks.forEach { block -> PresentationBlockView(block, attachmentPreviews, roleVisual.body, transcript.metadata.createdAt, onOpenImagePreview, onOpenPdfPreview, onOpenVideoPreview, onOpenAudioPreview, onOpenTextPreview) }
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
                Text(duration, color = SecondaryText, style = MaterialTheme.typography.labelSmall)
            }
            if (textBlocks.isNotEmpty()) Box(Modifier.fillMaxWidth()) { textContent() }
            if (attachmentBlocks.isNotEmpty()) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) { attachmentContent() }
            AssistantMessageActionRow(transcript, onCopyAssistant, onShareAssistant, onBranchAssistant)
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
                shape = RoundedCornerShape(18.dp),
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

/** Always-visible assistant row; USER actions intentionally remain long-press-only. */
@Composable
private fun AssistantMessageActionRow(
    transcript: PresentedTranscriptMessage,
    onCopy: (PresentedTranscriptMessage) -> Unit,
    onShare: (PresentedTranscriptMessage) -> Unit,
    onBranch: (MessageNodeId) -> Unit,
) {
    val metadata = listOfNotNull(
        formatTranscriptTimeOrNull(transcript.metadata.createdAt),
        transcript.metadata.modelSnapshotLabel?.takeIf { it.isNotBlank() },
    ).joinToString(" · ")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onCopy(transcript) }) { Icon(Icons.Outlined.ContentCopy, contentDescription = "复制") }
        IconButton(onClick = { onShare(transcript) }) { Icon(Icons.Outlined.Share, contentDescription = "分享") }
        IconButton(onClick = { onBranch(transcript.message.messageId) }) { Icon(Icons.AutoMirrored.Outlined.DriveFileMove, contentDescription = "从此处分支") }
        if (metadata.isNotBlank()) Text(metadata, color = SecondaryText, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun MessageActionPopup(
    anchorBounds: androidx.compose.ui.geometry.Rect,
    pressPosition: androidx.compose.ui.geometry.Offset,
    headline: String,
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
            color = Color.White,
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
                color = Color.White,
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

@Composable
private fun MessageContextAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(46.dp).padding(horizontal = 6.dp),
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent,
        contentColor = BodyText,
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(16.dp))
            Text(label, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
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
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true, dismissOnClickOutside = true),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
            // A full-width Compose Dialog owns the entire window, so its dimmed area is
            // not technically "outside" the Dialog. Keep one shared backdrop target above
            // the window but below the card so every background tap remains a cancel action.
            DismissibleDialogBackdrop(onDismiss)
            Surface(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .widthIn(max = 336.dp)
                    .fillMaxWidth(),
                color = Color.White,
                shape = RoundedCornerShape(20.dp),
                shadowElevation = 8.dp,
            ) {
                Column(
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    CompactConversationRenameField(value = value, onValueChange = onValueChange)
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
    Box(Modifier.fillMaxSize().pointerInput(onDismiss) { detectTapGestures(onTap = { onDismiss() }) })
}

/** A full-height editable line; the label decorates the border without consuming input space. */
@Composable
private fun CompactConversationRenameField(value: String, onValueChange: (String) -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(Modifier.fillMaxWidth().height(48.dp)) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .border(1.dp, Color(0xFF79747E), shape)
                .padding(horizontal = 12.dp),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, color = BodyText),
            decorationBox = { innerTextField ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) { innerTextField() }
            },
        )
        Text(
            "会话标题",
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 12.dp, y = (-8).dp)
                .background(Color.White)
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

/**
 * A transient index over the single [LazyListState] owner. It exists only on a
 * wide inner display, never persists message text, and always navigates through
 * the same list used by the transcript itself.
 */
@Composable
private fun TranscriptPositionRail(
    messages: List<PresentedTranscriptMessage>,
    listState: LazyListState,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val slots = minOf(12, messages.size)
    var previewSlot by remember { mutableStateOf<Int?>(null) }
    val scrollMetrics = transcriptScrollMetrics(listState)
    val activeSlot = ((slots - 1) * scrollMetrics.progress).roundToInt().coerceIn(0, (slots - 1).coerceAtLeast(0))
    // The scroll position supplies the resting envelope. Pointer hover or keyboard focus
    // temporarily replaces it with the desktop local preview; tapping is navigation only.
    Box(
        modifier = modifier.width(if (previewSlot == null) 28.dp else 364.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.padding(start = 4.dp),
        ) {
            repeat(slots) { slot ->
                val index = if (slots == 1) 0 else ((messages.lastIndex.toFloat() * slot) / (slots - 1)).roundToInt()
                val positionLabel = "跳转到对话位置 ${slot + 1}/$slots"
                val interactionSource = remember(slot) { MutableInteractionSource() }
                val hovered by interactionSource.collectIsHoveredAsState()
                val focused by interactionSource.collectIsFocusedAsState()
                LaunchedEffect(hovered, focused, slot) {
                    if (hovered || focused) previewSlot = slot
                    else if (previewSlot == slot) previewSlot = null
                }
                val distance = abs(slot - (previewSlot ?: activeSlot))
                val lineWidth = when (distance) {
                    0 -> 20.dp
                    1 -> 15.dp
                    2 -> 11.dp
                    3 -> 8.dp
                    4 -> 6.dp
                    else -> 4.dp
                }
                val lineColor = when (distance) {
                    0 -> BodyText
                    1 -> Color(0xFF7A817D)
                    2 -> Color(0xFF979D99)
                    3 -> Color(0xFFAFB4B0)
                    4 -> Color(0xFFC2C6C3)
                    else -> Color(0xFFD0D3D1)
                }
                Box(
                    modifier = Modifier
                        .width(22.dp)
                        .height(9.dp)
                        .semantics { contentDescription = positionLabel }
                        .hoverable(interactionSource)
                        .combinedClickable(
                            interactionSource = interactionSource,
                            onClick = { onSelect(index) },
                        ),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Box(
                        modifier = Modifier
                            .width(lineWidth)
                            .height(if (distance == 0) 2.dp else 1.dp)
                            .clip(CircleShape)
                            .background(lineColor),
                    )
                }
            }
        }
        previewSlot?.let { slot ->
            val index = if (slots == 1) 0 else ((messages.lastIndex.toFloat() * slot) / (slots - 1)).roundToInt()
            val message = messages.getOrNull(index)
            val preview = message?.let { presentedMessagePlainText(it.message).replace('\n', ' ').trim().take(48) }.orEmpty()
            if (preview.isNotBlank()) Surface(
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 44.dp).width(320.dp),
                color = Color.White,
                shape = RoundedCornerShape(16.dp),
                shadowElevation = 10.dp,
            ) {
                Text(
                    preview,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private data class TranscriptScrollMetrics(
    val progress: Float,
    val thumbFraction: Float,
    val canScroll: Boolean,
)

/** One source of truth for the inner rail and the right scrollbar. */
private fun transcriptScrollMetrics(listState: LazyListState): TranscriptScrollMetrics {
    val layout = listState.layoutInfo
    val totalItems = layout.totalItemsCount
    val visibleItems = layout.visibleItemsInfo
    val visibleCount = visibleItems.size.coerceAtLeast(1)
    val canScroll = totalItems > visibleCount
    if (!canScroll) return TranscriptScrollMetrics(progress = 0f, thumbFraction = 1f, canScroll = false)
    // A thumb that changes length with every unusually tall attachment looks like it is
    // jumping. Keep a stable, conversation-level visual fraction while scrolling.
    val nominalViewportItems = minOf(4, totalItems)
    val firstItemSize = visibleItems.firstOrNull()?.size?.coerceAtLeast(1) ?: 1
    val itemFraction = (listState.firstVisibleItemScrollOffset.toFloat() / firstItemSize).coerceIn(0f, 1f)
    val maximumStart = (totalItems - nominalViewportItems).coerceAtLeast(1)
    val hasReachedEnd = visibleItems.lastOrNull()?.index == totalItems - 1
    val progress = if (hasReachedEnd) 1f else ((listState.firstVisibleItemIndex + itemFraction) / maximumStart).coerceIn(0f, 1f)
    val thumbFraction = (nominalViewportItems.toFloat() / totalItems).coerceIn(0.08f, 1f)
    return TranscriptScrollMetrics(progress, thumbFraction, canScroll = true)
}

/** A continuous scroll thumb mirrors the same transcript position represented by the left rail. */
@Composable
private fun TranscriptScrollIndicator(listState: LazyListState, modifier: Modifier = Modifier) {
    val metrics = transcriptScrollMetrics(listState)
    if (!metrics.canScroll) return
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
    BoxWithConstraints(modifier = modifier.width(12.dp).fillMaxHeight()) {
        val thumbHeight = maxHeight * animatedThumbFraction
        val thumbOffset = (maxHeight - thumbHeight) * animatedProgress
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .width(1.dp)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.05f)),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = thumbOffset)
                .width(3.dp)
                .height(thumbHeight)
                .clip(CircleShape)
                .background(BodyText.copy(alpha = 0.28f))
                .semantics { contentDescription = "对话滚动位置" },
        )
    }
}

private fun presentedMessagePlainText(message: PresentedMessage): String = message.blocks.joinToString("\n") { block -> when (block) {
    is PresentationBlock.Heading -> inlineText(block.spans).text
    is PresentationBlock.Paragraph -> inlineText(block.spans).text
    is PresentationBlock.Quote -> inlineText(block.spans).text
    is PresentationBlock.UnorderedList -> block.items.joinToString("\n") { "• ${inlineText(it).text}" }
    is PresentationBlock.OrderedList -> block.items.joinToString("\n") { "${it.ordinal}. ${inlineText(it.spans).text}" }
    is PresentationBlock.CodeFence -> block.code
    is PresentationBlock.Table -> tablePlainText(block)
    is PresentationBlock.PlainText -> block.raw
    is PresentationBlock.AttachmentReference -> "${block.attachment.displayName ?: "本地附件"} · ${block.attachment.mimeType}"
    is PresentationBlock.SafeToolSummary -> "工具结果（安全摘要）：${block.toolName} · ${block.summary}"
} }

@Composable
private fun PresentationBlockView(block: PresentationBlock, attachmentPreviews: Map<AttachmentId, ConversationAttachmentPreview>, bodyColor: Color, sentAt: java.time.Instant?, onOpenImagePreview: (AttachmentId) -> Unit, onOpenPdfPreview: (AttachmentId) -> Unit, onOpenVideoPreview: (AttachmentId) -> Unit, onOpenAudioPreview: (AttachmentId) -> Unit, onOpenTextPreview: (AttachmentId) -> Unit) {
    when (block) {
        is PresentationBlock.Heading -> Text(inlineText(block.spans), color = bodyColor, style = if (block.level <= 2) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        is PresentationBlock.Paragraph -> Text(inlineText(block.spans), color = bodyColor, style = MaterialTheme.typography.bodyMedium, lineHeight = 25.sp)
        is PresentationBlock.Quote -> Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(vertical = 2.dp)) {
            Box(Modifier.width(2.dp).fillMaxHeight().background(BodyText))
            Text(
                inlineText(block.spans),
                modifier = Modifier.padding(start = 10.dp),
                color = BodyText,
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
            )
        }
        is PresentationBlock.UnorderedList -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { block.items.forEach { ListItem("•", inlineText(it), bodyColor) } }
        is PresentationBlock.OrderedList -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { block.items.forEach { ListItem("${it.ordinal}.", inlineText(it.spans), bodyColor) } }
        is PresentationBlock.CodeFence -> CopyableInformationSurface(label = block.language ?: "可复制内容", value = block.code) {
            Text(block.code, color = BodyText, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, lineHeight = 20.sp)
        }
        is PresentationBlock.Table -> CopyableInformationSurface(label = "数据对照", value = tablePlainText(block)) { MarkdownTable(block) }
        is PresentationBlock.PlainText -> Text(block.raw, style = MaterialTheme.typography.bodyMedium)
        is PresentationBlock.AttachmentReference -> AttachmentPreviewChip(attachmentPreviews[block.attachment.id], block.attachment.displayName, block.attachment.mimeType, block.attachment.byteCount, sentAt, onOpenImagePreview, onOpenPdfPreview, onOpenVideoPreview, onOpenAudioPreview, onOpenTextPreview)
        is PresentationBlock.SafeToolSummary -> Text("工具结果（安全摘要）：${block.toolName} · ${block.summary}", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ListItem(marker: String, text: androidx.compose.ui.text.AnnotatedString, bodyColor: Color) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
        Text(marker, color = bodyColor, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(28.dp))
        Text(text, color = bodyColor, style = MaterialTheme.typography.bodyMedium, lineHeight = 25.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun CopyableInformationSurface(label: String, value: String, content: @Composable () -> Unit) {
    val clipboard = LocalClipboardManager.current
    Surface(
        color = Color(0xFFF5F6F5),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 14.dp, top = 10.dp, end = 6.dp, bottom = 12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = SecondaryText, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(value)) }) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = "复制$label", tint = BodyText, modifier = Modifier.size(20.dp))
                }
            }
            content()
        }
    }
}

@Composable
private fun MarkdownTable(block: PresentationBlock.Table) {
    val scrollState = rememberScrollState()
    Column(Modifier.horizontalScroll(scrollState)) {
        MarkdownTableRow(block.headers, header = true)
        HorizontalDivider(color = Color(0x1A1E2925))
        block.rows.forEach { row ->
            MarkdownTableRow(row, header = false)
            HorizontalDivider(color = Color(0x121E2925))
        }
    }
}

@Composable
private fun MarkdownTableRow(cells: List<List<InlinePresentation>>, header: Boolean) {
    Row {
        cells.forEach { cell ->
            Text(
                inlineText(cell),
                color = BodyText,
                style = if (header) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium,
                fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal,
                lineHeight = 22.sp,
                modifier = Modifier.widthIn(min = 132.dp).padding(horizontal = 10.dp, vertical = 9.dp),
            )
        }
    }
}

private fun tablePlainText(block: PresentationBlock.Table): String = buildList {
    add(block.headers.joinToString("\t") { inlineText(it).text })
    block.rows.forEach { row -> add(row.joinToString("\t") { inlineText(it).text }) }
}.joinToString("\n")

private fun inlineText(spans: List<InlinePresentation>) = buildAnnotatedString {
    spans.forEach { span -> when (span) {
        is InlinePresentation.Text -> append(span.value)
        is InlinePresentation.Code -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)) { append("`${span.value}`") }
        is InlinePresentation.Link -> withLink(
            LinkAnnotation.Url(
                url = span.url,
                styles = TextLinkStyles(style = SpanStyle(color = BrandGreen, fontWeight = FontWeight.Medium)),
            ),
        ) { append("${span.label} ↗") }
    } }
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
    onRequestCompare: () -> Unit,
    onAddAnchorChanged: (androidx.compose.ui.geometry.Rect) -> Unit,
    onModelAnchorChanged: (androidx.compose.ui.geometry.Rect) -> Unit,
) {
    val draft = state.draft ?: return
    val canStopRuntime = state.runtime?.isTerminal == false
    val canSubmit = !state.isSending && (draft.text.isNotBlank() || draft.attachments.isNotEmpty())
    val manualId = state.p6gConversationOverride?.modelId
    val modelLabel = manualId?.let { id -> state.p6gCatalog?.candidates?.firstOrNull { it.modelId == id }?.displayName ?: id } ?: "自动"
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
            onClick = onToggleModel,
            onLongClick = onRequestCompare,
            onAnchorChanged = onModelAnchorChanged,
        )
        ComposerCompareEntry(onClick = onRequestCompare)
        ComposerSendButton(onClick = if (canStopRuntime) onStop else onSubmit, enabled = canStopRuntime || canSubmit, isSending = state.isSending, showStop = canStopRuntime, contentDescription = "发送消息")
    }
}

/** One model affordance for normal and temporary conversations; only their persistence owner differs. */
@Composable
private fun ComposerModelEntry(
    label: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onAnchorChanged: (androidx.compose.ui.geometry.Rect) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier.width(64.dp).height(48.dp)
            .onGloballyPositioned { onAnchorChanged(it.boundsInRoot()) }
            .clip(shape)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .semantics { contentDescription = if (onLongClick == null) "选择模型：$label" else "选择模型：$label；长按对比 ChatGPT + Claude" },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().height(36.dp),
            shape = shape,
            color = if (pressed) Color(0xFFF1F3F1) else Color.Transparent,
        ) { Box(contentAlignment = Alignment.Center) { Text(label, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) } }
    }
}

/** A compact explicit Compare action; it shares the model affordance's surface and hit geometry. */
@Composable
private fun ComposerCompareEntry(onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.width(52.dp).height(48.dp),
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.textButtonColors(contentColor = SecondaryText),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().height(36.dp),
            shape = RoundedCornerShape(50),
            color = Color.Transparent,
        ) { Box(contentAlignment = Alignment.Center) { Text("对比", fontSize = 14.sp) } }
    }
}

/** The one measured Composer implementation shared by normal and temporary conversations. */
@Composable
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
    // Draft previews are intentionally inside this single white surface. The root overlay menu
    // remains a sibling, so opening it never contributes to the dock's measurement or IME path.
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(28.dp),
        shadowElevation = 10.dp,
    ) {
        Column {
            attachmentPreview?.invoke()
            Row(
                modifier = Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onOpenAttachments,
                    enabled = attachmentsEnabled,
                    modifier = Modifier.size(48.dp).onGloballyPositioned { onAttachmentAnchorChanged(it.boundsInRoot()) },
                    shape = CircleShape,
                ) { Icon(Icons.Outlined.Add, contentDescription = attachmentDescription, modifier = Modifier.size(ComposerAddGlyphSize)) }
                ComposerDraftTextField(value = value, onValueChange = onValueChange, inputDescription = inputDescription, modifier = Modifier.weight(1f).p5aKeyboardTraversal())
                trailingContent()
            }
        }
    }
}

/** One draft-only presentation: image thumbnails and their remove action stay inside the composer. */
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
    val isText = mimeType in setOf("text/plain", "text/markdown", "application/json", "text/csv")
    val previewCorner = if (isVideo) 20.dp else if (isImage) 10.dp else 14.dp
    val bitmap = preview?.thumbnail?.bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
    val previewModifier = when {
        isImage -> originalAspectPreviewModifier(bitmap, maxEdge = 92.dp, fallbackSize = 92.dp)
        isVideo -> Modifier.size(92.dp)
        else -> Modifier.size(76.dp)
    }
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
            .clip(RoundedCornerShape(previewCorner))
            .then(if (isImage) Modifier else Modifier.background(Color.White).border(1.dp, Color(0x141E2925), RoundedCornerShape(previewCorner)))
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
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = if (isVideo) "草稿视频代表帧" else "草稿图片预览",
                contentScale = if (isVideo) ContentScale.Crop else ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = if (isImage) Icons.Outlined.AddPhotoAlternate else Icons.Outlined.AttachFile,
                contentDescription = if (isImage) "草稿图片预览不可用" else "草稿附件",
                tint = SecondaryText,
                modifier = Modifier.align(Alignment.Center).size(24.dp),
            )
        }
        if (isVideo) VideoAttachmentOverlay(preview?.videoDurationMillis)
        IconButton(
            onClick = onRemove,
            modifier = Modifier.align(Alignment.TopEnd).padding(3.dp).size(28.dp),
        ) {
            Surface(color = Color.White, shape = CircleShape, shadowElevation = 2.dp) {
                Icon(
                    imageVector = Icons.Outlined.Close,
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
    modelOptions: List<Pair<String?, String>>,
    selectedModelId: String?,
    onDismiss: () -> Unit,
    onSelectModel: ((String?) -> Unit)? = null,
    onSelectCompare: (() -> Unit)? = null,
) {
    if (menu == ComposerMenu.NONE) return
    val anchor = when (menu) {
        ComposerMenu.ATTACHMENTS -> addAnchor
        ComposerMenu.MODEL -> modelAnchor
        ComposerMenu.NONE -> null
    } ?: return
    val density = LocalDensity.current
    val menuWidth = 248.dp
    val menuHeight = if (menu == ComposerMenu.ATTACHMENTS) 52.dp * attachmentActions.size + 16.dp else 52.dp * (modelOptions.size + if (onSelectCompare == null) 0 else 1) + 16.dp
    val horizontalPadding = with(density) { 12.dp.toPx() }
    val verticalGap = with(density) { 8.dp.toPx() }
    val widthPx = with(density) { menuWidth.toPx() }
    val heightPx = with(density) { menuHeight.toPx() }
    val x = when (menu) {
        ComposerMenu.ATTACHMENTS -> maxOf(horizontalPadding, anchor.left)
        ComposerMenu.MODEL -> maxOf(horizontalPadding, anchor.right - widthPx)
        ComposerMenu.NONE -> horizontalPadding
    }
    val y = maxOf(horizontalPadding, anchor.top - heightPx - verticalGap)
    Box(Modifier.fillMaxSize()) {
        // This transparent sibling owns only outside-tap dismissal. It has no focus request and
        // no size dependency on the menu, keeping the IME and composer where they already are.
        Box(Modifier.fillMaxSize().pointerInput(menu) { detectTapGestures(onTap = { onDismiss() }) })
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(22.dp),
            shadowElevation = 10.dp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset { IntOffset(x.toInt(), y.toInt()) }
                .width(menuWidth),
        ) {
            Column(Modifier.padding(vertical = 8.dp)) {
                if (menu == ComposerMenu.ATTACHMENTS) {
                    attachmentActions.forEach { action -> ComposerOverlayAction(action.icon, action.label, action.onClick) }
                } else {
                    modelOptions.forEach { (id, label) ->
                        TextButton(
                            onClick = { onSelectModel?.invoke(id) ?: onDismiss() },
                            colors = ButtonDefaults.textButtonColors(contentColor = if (id == selectedModelId) AccentOrange else SecondaryText),
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(0.dp),
                        ) {
                            Box(Modifier.fillMaxWidth()) {
                                Text(
                                    label,
                                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 40.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (id == selectedModelId) Icon(
                                    Icons.Outlined.Check,
                                    contentDescription = "当前模型",
                                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp).size(20.dp),
                                )
                            }
                        }
                    }
                    if (onSelectCompare != null) {
                        TextButton(
                            onClick = onSelectCompare,
                            colors = ButtonDefaults.textButtonColors(contentColor = SecondaryText),
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(0.dp),
                        ) {
                            Text(
                                "对比 ChatGPT + Claude",
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
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
private fun ComposerOverlayAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(contentColor = SecondaryText),
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(0.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Start)
    }
}

/** The mobile composer has one floating outer pill; the text field deliberately draws no second box. */
@Composable
private fun ComposerDraftTextField(value: String, onValueChange: (String) -> Unit, inputDescription: String = "会话草稿", modifier: Modifier = Modifier) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.padding(horizontal = 6.dp, vertical = 8.dp).semantics { contentDescription = inputDescription },
        minLines = 1,
        maxLines = 5,
    ) { innerTextField ->
        Box(contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text("回复 南枫AI", color = Color(0xFF9A9E9B))
            innerTextField()
        }
    }
}

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

@Composable
private fun AttachmentPreviewChip(preview: ConversationAttachmentPreview?, displayName: String?, mimeType: String, byteCount: Long, sentAt: java.time.Instant?, onOpenImagePreview: (AttachmentId) -> Unit, onOpenPdfPreview: (AttachmentId) -> Unit, onOpenVideoPreview: (AttachmentId) -> Unit, onOpenAudioPreview: (AttachmentId) -> Unit, onOpenTextPreview: (AttachmentId) -> Unit) {
    val isImage = mimeType.startsWith("image/")
    val isPdf = mimeType == "application/pdf"
    val isVideo = mimeType == "video/mp4"
    val isAudio = mimeType in com.nanzhufeng.ai.domain.CONVERSATION_ALLOWED_AUDIO_MIME_TYPES
    val isText = mimeType in setOf("text/plain", "text/markdown", "application/json", "text/csv")
    val previewCorner = if (isVideo) 20.dp else if (isImage) 10.dp else 10.dp
    val bitmap = preview?.thumbnail?.bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
    // Attachments are content, not message bubbles: a photo is its own rounded pixel surface;
    // only video/document-like content receives a deliberate card treatment.
    val previewModifier = when {
        // A deliberately short, uniform video card makes a representative frame read as
        // video at a glance, while photos retain their natural aspect ratio.
        isVideo -> Modifier.widthIn(min = 144.dp, max = 220.dp).height(128.dp)
        isImage -> originalAspectPreviewModifier(bitmap, maxEdge = 220.dp, fallbackSize = 92.dp)
        isAudio -> Modifier.size(width = 176.dp, height = 42.dp)
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
        Box(previewModifier.then(when {
            isVideo -> Modifier.border(1.dp, Color.Black.copy(alpha = 0.18f), RoundedCornerShape(previewCorner))
            else -> Modifier
        })) {
            if (bitmap != null) Image(bitmap = bitmap.asImageBitmap(), contentDescription = if (isVideo) "本地视频代表帧" else "本地图片缩略图", contentScale = if (isVideo) ContentScale.Crop else ContentScale.Fit, modifier = Modifier.fillMaxSize())
            else Box(
                Modifier.fillMaxSize()
                    .background(if (isPdf) Color.White else NeutralAssistantSurface)
                    .border(if (isPdf) 1.dp else 0.dp, Color(0x141E2925), RoundedCornerShape(10.dp)),
            )
            if (isVideo) VideoAttachmentOverlay(preview?.videoDurationMillis)
        }
        attachmentBounds?.let { bounds ->
            attachmentPressPosition?.takeIf { attachmentInfoVisible }?.let { pressPosition ->
                AttachmentInfoPopup(
                    attachmentId = preview?.id ?: return@let,
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

/** Shared attachment facts: compact, anchored, and readable without exposing implementation MIME jargon. */
@Composable
private fun AttachmentInfoPopup(
    attachmentId: AttachmentId,
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
    val density = LocalDensity.current
    val containerSize = LocalWindowInfo.current.containerSize
    val popupWidth = 252.dp
    val popupHeight = if ((displayName?.length ?: 0) > 34) 220.dp else 198.dp
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
            color = Color.White,
            contentColor = BodyText,
            shape = RoundedCornerShape(20.dp),
            shadowElevation = 6.dp,
            modifier = Modifier.width(popupWidth),
        ) {
            Column(Modifier.padding(start = 16.dp, top = 14.dp, end = 10.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                    Icon(
                        imageVector = if (kind == "图片") Icons.Outlined.AddPhotoAlternate else Icons.Outlined.AttachFile,
                        contentDescription = null,
                        tint = SecondaryText,
                        modifier = Modifier.padding(top = 2.dp).size(20.dp),
                    )
                    Column(Modifier.padding(start = 10.dp).weight(1f)) {
                        Text(
                            displayName ?: "未命名附件",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(kind, color = SecondaryText, style = MaterialTheme.typography.labelSmall)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Outlined.Close, contentDescription = "关闭附件信息", modifier = Modifier.size(18.dp))
                    }
                }
                Text(detail, color = SecondaryText, style = MaterialTheme.typography.labelSmall)
                time?.let { Text(if (isDraft) it else "发送于 $it", color = SecondaryText, style = MaterialTheme.typography.labelSmall) }
                HorizontalDivider(color = Color(0x141E2925))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AttachmentPopupAction(Icons.Outlined.FileDownload, "下载", Modifier.weight(1f)) {
                        requestAttachmentTransfer(attachmentId, AttachmentTransferAction.DOWNLOAD)
                        onDismiss()
                    }
                    AttachmentPopupAction(Icons.Outlined.Share, "分享", Modifier.weight(1f)) {
                        requestAttachmentTransfer(attachmentId, AttachmentTransferAction.SHARE)
                        onDismiss()
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentPopupAction(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = modifier.height(36.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = BodyText),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
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
                Icons.Filled.PlayArrow,
                contentDescription = "视频预览，可播放",
                tint = Color.White,
                modifier = Modifier.padding(8.dp).size(24.dp),
            )
        }
        durationMillis?.takeIf { it > 0L }?.let { duration ->
            Text(
                formatVideoDuration(duration),
                color = Color.White,
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

/** Preview actions share one compact top-right treatment across every local file format. */
@Composable
private fun FilePreviewTopActions(
    dark: Boolean,
    onDownload: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = if (dark) Color.White.copy(alpha = 0.16f) else Color(0xFFF1F4F2)
    val content = if (dark) Color.White else BodyText
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onDownload, modifier = Modifier.size(48.dp)) {
            Surface(color = container, shape = CircleShape, modifier = Modifier.fillMaxSize()) {
                Icon(Icons.Outlined.FileDownload, contentDescription = "下载文件", tint = content, modifier = Modifier.padding(12.dp).size(24.dp))
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

@Composable
private fun PreviewCloseButton(dark: Boolean, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val container = if (dark) Color.White.copy(alpha = 0.16f) else Color(0xFFF1F4F2)
    val content = if (dark) Color.White else BodyText
    IconButton(onClick = onClose, modifier = modifier.size(48.dp)) {
        Surface(color = container, shape = CircleShape, modifier = Modifier.fillMaxSize()) {
            Icon(Icons.Outlined.Close, contentDescription = "关闭文件预览", tint = content, modifier = Modifier.padding(12.dp).size(24.dp))
        }
    }
}

@Composable
private fun PdfPreviewDialog(preview: ConversationAttachmentPdfPreview, onOpenPage: (Int) -> Unit, onClose: () -> Unit) {
    val requestAttachmentTransfer = LocalAttachmentTransferRequest.current
    val page = preview.page
    val bitmap = page?.image?.bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = Color(0xFFF1F4F2), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                when {
                    page == null -> Text(preview.unavailableReason ?: "本地 PDF 已损坏，无法阅读。", color = SecondaryText)
                    bitmap == null -> Text("本地 PDF 页渲染失败，无法阅读。", color = SecondaryText)
                    else -> Image(bitmap = bitmap.asImageBitmap(), contentDescription = "${preview.displayName ?: "本地 PDF"} 第 ${page.pageNumber} 页", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                }
                PreviewCloseButton(dark = false, onClose = onClose, modifier = Modifier.align(Alignment.TopStart).padding(12.dp))
                FilePreviewTopActions(
                    dark = false,
                    onDownload = { requestAttachmentTransfer(preview.id, AttachmentTransferAction.DOWNLOAD) },
                    onShare = { requestAttachmentTransfer(preview.id, AttachmentTransferAction.SHARE) },
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                )
                page?.let {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp)) {
                        OutlinedButton(onClick = { onOpenPage(it.pageNumber - 1) }, enabled = it.pageNumber > 1, shape = RoundedCornerShape(14.dp)) { Text("上一页") }
                        Text("第 ${it.pageNumber} / ${it.pageCount} 页", style = MaterialTheme.typography.labelSmall, color = SecondaryText, modifier = Modifier.padding(horizontal = 10.dp))
                        OutlinedButton(onClick = { onOpenPage(it.pageNumber + 1) }, enabled = it.pageNumber < it.pageCount, shape = RoundedCornerShape(14.dp)) { Text("下一页") }
                    }
                }
            }
        }
    }
}

/** The cache file is created from verified display bytes keyed by the owner ID; no source URI/path enters UI state. */
@Composable
private fun VideoPreviewDialog(preview: ConversationAttachmentVideoPreview, onClose: (Long) -> Unit) {
    val context = LocalContext.current
    val requestAttachmentTransfer = LocalAttachmentTransferRequest.current
    var videoView by remember(preview.id.value) { mutableStateOf<VideoView?>(null) }
    var isPlaying by rememberSaveable(preview.id.value) { mutableStateOf(false) }
    var controlsVisible by rememberSaveable(preview.id.value) { mutableStateOf(true) }
    var controlsRevision by rememberSaveable(preview.id.value) { mutableStateOf(0) }
    var positionMillis by rememberSaveable(preview.id.value) { mutableStateOf(preview.positionMillis) }
    var durationMillis by rememberSaveable(preview.id.value) { mutableStateOf(preview.durationMillis ?: 0L) }
    val localFile = remember(preview.id.value, preview.bytes?.size) {
        preview.bytes?.let { bytes -> java.io.File(context.cacheDir, "local-video-preview-${preview.id.value}.mp4").apply { outputStream().use { it.write(bytes) } } }
    }
    fun showControls() {
        controlsVisible = true
        controlsRevision += 1
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
    fun closeVideo() = onClose(videoView?.currentPosition?.toLong() ?: preview.positionMillis)
    LaunchedEffect(controlsVisible, controlsRevision) {
        if (controlsVisible) {
            delay(12_000)
            if (controlsVisible) controlsVisible = false
        }
    }
    LaunchedEffect(videoView) {
        while (videoView != null) {
            videoView?.let { view ->
                isPlaying = view.isPlaying
                positionMillis = view.currentPosition.toLong().coerceAtLeast(0L)
                durationMillis = view.duration.toLong().coerceAtLeast(0L)
            }
            delay(250)
        }
    }
    Dialog(onDismissRequest = ::closeVideo, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = Color.Black, modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (preview.bytes == null || localFile == null) Text(preview.unavailableReason ?: "本地视频已损坏，无法播放。", color = Color.White)
                else AndroidView(
                    factory = { androidContext ->
                        VideoView(androidContext).also { view ->
                            view.setVideoURI(Uri.fromFile(localFile))
                            view.setOnPreparedListener { ready ->
                                ready.seekTo(preview.positionMillis.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                                durationMillis = ready.duration.toLong().coerceAtLeast(0L)
                                positionMillis = preview.positionMillis.coerceAtMost(durationMillis)
                                showControls()
                            }
                            view.setOnCompletionListener { isPlaying = false; showControls() }
                            videoView = view
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                if (controlsVisible && preview.bytes != null && localFile != null) {
                    VideoControlOverlay(
                        isPlaying = isPlaying,
                        positionMillis = positionMillis,
                        durationMillis = durationMillis,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
                // The central control is part of the same temporary chrome as the timeline in
                // both playback states: one tap opens/closes both, while this control toggles.
                if (controlsVisible && preview.bytes != null && localFile != null) {
                    VideoPlaybackOverlay(
                        isPlaying = isPlaying,
                        onToggle = ::togglePlayback,
                    )
                }
                PreviewCloseButton(dark = true, onClose = ::closeVideo, modifier = Modifier.align(Alignment.TopStart).padding(12.dp))
                FilePreviewTopActions(
                    dark = true,
                    onDownload = { requestAttachmentTransfer(preview.id, AttachmentTransferAction.DOWNLOAD) },
                    onShare = { requestAttachmentTransfer(preview.id, AttachmentTransferAction.SHARE) },
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                )
                if (preview.bytes != null && localFile != null) {
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
                                    if (controlsVisible) controlsVisible = false else showControls()
                                }
                                var edgeStartX: Float? = null
                                var edgeExitHandled = false
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
                                            onCloseButton -> closeVideo()
                                            onDownloadButton -> requestAttachmentTransfer(preview.id, AttachmentTransferAction.DOWNLOAD)
                                            onShareButton -> requestAttachmentTransfer(preview.id, AttachmentTransferAction.SHARE)
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
                                        }
                                        android.view.MotionEvent.ACTION_MOVE -> {
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
                                        android.view.MotionEvent.ACTION_UP,
                                        android.view.MotionEvent.ACTION_CANCEL -> edgeStartX = null
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
private fun VideoControlOverlay(isPlaying: Boolean, positionMillis: Long, durationMillis: Long, modifier: Modifier = Modifier) {
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
                Text(formatVideoDuration(durationMillis), color = Color.White.copy(alpha = 0.82f), style = MaterialTheme.typography.labelSmall)
            }
            Box(Modifier.fillMaxWidth().padding(vertical = 14.dp).height(3.dp).background(Color.White.copy(alpha = 0.38f), CircleShape)) {
                Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(Color.White, CircleShape))
            }
            Surface(
                color = Color.Black.copy(alpha = 0.56f),
                shape = CircleShape,
                modifier = Modifier.size(56.dp).semantics { contentDescription = if (isPlaying) "暂停视频" else "播放视频" },
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
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
                if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
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
    var player by remember(preview.id.value) { mutableStateOf<MediaPlayer?>(null) }
    var prepared by remember(preview.id.value) { mutableStateOf(false) }
    var durationMillis by remember(preview.id.value) { mutableStateOf<Long?>(null) }
    val localFile = remember(preview.id.value, preview.bytes?.size) {
        preview.bytes?.let { bytes -> java.io.File(context.cacheDir, "local-audio-preview-${preview.id.value}.bin").apply { outputStream().use { it.write(bytes) } } }
    }
    fun position(): Long = player?.currentPosition?.toLong()?.takeIf { it > 0L } ?: preview.positionMillis
    Dialog(onDismissRequest = { onClose(position()) }) {
        Surface(color = Color.White, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().padding(18.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("本地音频播放", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    FilePreviewTopActions(
                        dark = false,
                        onDownload = { requestAttachmentTransfer(preview.id, AttachmentTransferAction.DOWNLOAD) },
                        onShare = { requestAttachmentTransfer(preview.id, AttachmentTransferAction.SHARE) },
                    )
                    PreviewCloseButton(dark = false, onClose = { onClose(position()) })
                }
                if (preview.bytes == null || localFile == null) Text(preview.unavailableReason ?: "本地音频已损坏，无法播放。", color = SecondaryText)
                else {
                    LaunchedEffect(localFile) {
                        runCatching {
                            MediaPlayer().also { media ->
                                media.setDataSource(localFile.absolutePath)
                                media.setOnPreparedListener { ready ->
                                    ready.seekTo(preview.positionMillis.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                                    durationMillis = ready.duration.toLong().takeIf { value -> value > 0L }
                                    prepared = true
                                }
                                media.prepareAsync(); player = media
                            }
                        }
                    }
                    Button(onClick = { player?.takeIf { prepared }?.start() }, enabled = prepared, shape = RoundedCornerShape(14.dp)) { Text("开始本地播放") }
                    Text("${preview.displayName ?: "未命名音频"} · ${preview.mimeType} · ${formatAttachmentBytes(preview.byteCount)}${(durationMillis ?: preview.durationMillis)?.let { " · ${formatVideoDuration(it)}" }.orEmpty()}", style = MaterialTheme.typography.labelSmall, color = SecondaryText)
                    if (preview.positionMillis > 0L) Text("将从 ${formatVideoDuration(preview.positionMillis)} 继续播放", style = MaterialTheme.typography.labelSmall, color = SecondaryText)
                    Text("仅从本机私有副本播放；点击播放前不会自动播放、上传或外发。关闭后会恢复到当前位置。", style = MaterialTheme.typography.labelSmall, color = SecondaryText)
                }
            }
        }
    }
}

@Composable
private fun TextPreviewDialog(preview: ConversationAttachmentTextPreview, onClose: () -> Unit) {
    val requestAttachmentTransfer = LocalAttachmentTransferRequest.current
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = Color.White, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxSize().padding(18.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("本地安全文本预览", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    FilePreviewTopActions(
                        dark = false,
                        onDownload = { requestAttachmentTransfer(preview.id, AttachmentTransferAction.DOWNLOAD) },
                        onShare = { requestAttachmentTransfer(preview.id, AttachmentTransferAction.SHARE) },
                    )
                    PreviewCloseButton(dark = false, onClose = onClose)
                }
                if (preview.text == null) Text(preview.unavailableReason ?: "本地文本不可用。", color = SecondaryText)
                else {
                    Text("${preview.displayName ?: "未命名文件"} · ${preview.mimeType} · ${formatAttachmentBytes(preview.byteCount)}", style = MaterialTheme.typography.labelSmall, color = SecondaryText)
                    if (preview.truncated) Text("仅显示前 128 KiB；原文件未执行或外发。", style = MaterialTheme.typography.labelSmall, color = SecondaryText)
                    SelectionContainer { Text(preview.text, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).clip(RoundedCornerShape(12.dp)).background(Color(0xFFF1F4F2)).padding(12.dp)) }
                    Text("内容按 inert UTF-8 纯文本显示；不会渲染 HTML、执行链接、脚本或 Markdown 指令。", style = MaterialTheme.typography.labelSmall, color = SecondaryText)
                }
            }
        }
    }
}

private fun formatVideoDuration(milliseconds: Long): String {
    val seconds = (milliseconds / 1000).coerceAtLeast(0)
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

@Composable
private fun ImagePreviewDialog(
    preview: ConversationAttachmentOriginalPreview,
    relatedImageIds: List<AttachmentId>,
    onClose: () -> Unit,
    onRequestAttachmentTransfers: (List<AttachmentId>, AttachmentTransferAction) -> Unit,
) {
    val requestAttachmentTransfer = LocalAttachmentTransferRequest.current
    val bitmap = preview.bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
    val generatedImageIds = relatedImageIds.distinct().ifEmpty { listOf(preview.id) }
    val hasMultipleGeneratedImages = generatedImageIds.size > 1
    var batchDownloadVisible by rememberSaveable(preview.id.value) { mutableStateOf(false) }
    var zoom by rememberSaveable(preview.id.value) { mutableStateOf(1f) }
    var offsetX by rememberSaveable(preview.id.value) { mutableStateOf(0f) }
    var offsetY by rememberSaveable(preview.id.value) { mutableStateOf(0f) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = Color.Black, modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (bitmap == null) Text(preview.unavailableReason ?: "本地原图已损坏，无法预览。", color = Color.White)
                else Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize().pointerInput(preview.id) {
                            detectTransformGestures { _, pan, zoomChange, _ ->
                                zoom = (zoom * zoomChange).coerceIn(1f, 4f)
                                if (zoom <= 1f) { offsetX = 0f; offsetY = 0f } else { offsetX += pan.x; offsetY += pan.y }
                            }
                        },
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "${preview.displayName ?: "本地图片"} 原图预览",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = zoom, scaleY = zoom, translationX = offsetX, translationY = offsetY),
                    )
                }
                PreviewCloseButton(dark = true, onClose = onClose, modifier = Modifier.align(Alignment.TopStart).padding(12.dp))
                FilePreviewTopActions(
                    dark = true,
                    onDownload = {
                        if (hasMultipleGeneratedImages) batchDownloadVisible = true
                        else requestAttachmentTransfer(preview.id, AttachmentTransferAction.DOWNLOAD)
                    },
                    onShare = { requestAttachmentTransfer(preview.id, AttachmentTransferAction.SHARE) },
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                )
                if (batchDownloadVisible) {
                    Surface(
                        color = Color.White,
                        contentColor = BodyText,
                        shape = RoundedCornerShape(18.dp),
                        shadowElevation = 10.dp,
                        modifier = Modifier.align(Alignment.TopEnd).padding(top = 70.dp, end = 12.dp).width(220.dp),
                    ) {
                        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("下载图片", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                            AttachmentPopupAction(Icons.Outlined.FileDownload, "下载当前图片", Modifier.fillMaxWidth()) {
                                requestAttachmentTransfer(preview.id, AttachmentTransferAction.DOWNLOAD)
                                batchDownloadVisible = false
                            }
                            AttachmentPopupAction(Icons.Outlined.FileDownload, "同时下载 ${generatedImageIds.size} 张", Modifier.fillMaxWidth()) {
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

@Composable
private fun ComposerSendButton(
    onClick: () -> Unit,
    enabled: Boolean,
    isSending: Boolean = false,
    showStop: Boolean = false,
    contentDescription: String,
) {
    val surfaceColor = if (enabled) AccentOrange else Color(0xFFF2E4D6)
    val glyphColor = if (enabled) Color.White else Color(0xFF875B35)
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
                    showStop -> Icon(Icons.Outlined.Stop, contentDescription = "停止生成", tint = glyphColor, modifier = Modifier.size(ComposerStopGlyphSize))
                    isSending -> CircularProgressIndicator(color = glyphColor, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                    else -> Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = contentDescription, tint = glyphColor, modifier = Modifier.size(ComposerSendGlyphSize).graphicsLayer(rotationZ = -90f))
                }
            }
        }
    }
}

private fun formatAttachmentBytes(bytes: Long): String = if (bytes < 1024 * 1024) "${bytes / 1024} KB" else "%.1f MB".format(bytes / 1024f / 1024f)

/** Writes only after the explicit preview action; no private path crosses the UI state boundary. */
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
                file.outputStream().use { it.write(request.bytes) }
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
        resolver.openOutputStream(uri, "wt")?.use { it.write(item.bytes) } ?: error("无法写入下载文件")
        // DATE_TAKEN is read-only: MediaStore extracts it from the downloaded image's EXIF.
        // Stamp the new copy before publishing so gallery timelines sort by this download,
        // not by the original upload/capture date embedded in its bytes.
        if (item.mimeType.startsWith("image/")) {
            stampDownloadedImageTakenAt(resolver, uri, downloadedAtMillis)
        }
        if (item.mimeType == "video/mp4") {
            remuxDownloadedMp4(context, resolver, uri, item.bytes)
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
        val exif = android.media.ExifInterface(descriptor.fileDescriptor)
        exif.setAttribute(android.media.ExifInterface.TAG_DATETIME, exifTime)
        exif.setAttribute(android.media.ExifInterface.TAG_DATETIME_ORIGINAL, exifTime)
        exif.setAttribute(android.media.ExifInterface.TAG_DATETIME_DIGITIZED, exifTime)
        exif.setAttribute(android.media.ExifInterface.TAG_OFFSET_TIME_ORIGINAL, offset)
        exif.setAttribute(android.media.ExifInterface.TAG_OFFSET_TIME_DIGITIZED, offset)
        exif.saveAttributes()
    }
}

/** MP4 creation metadata belongs to the source file, so create a new local container for this download. */
@androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
private fun remuxDownloadedMp4(
    context: android.content.Context,
    resolver: android.content.ContentResolver,
    destination: Uri,
    sourceBytes: ByteArray,
) {
    val source = java.io.File.createTempFile("nanfeng-ai-download-", ".mp4", context.cacheDir)
    try {
        source.outputStream().use { it.write(sourceBytes) }
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
    val modelLabel = modelOptions.firstOrNull { it.first == recovery.modelOverrideId }?.second ?: "自动"
    Box(Modifier.fillMaxSize()) {
    Column(
        Modifier.fillMaxSize().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 86.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(recovery.messages, key = { it.id }) { message ->
                RightAlignedUserBubble {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (message.text.isNotBlank()) Text(message.text, color = BodyText)
                        if (message.attachmentIds.isNotEmpty()) Icon(
                            Icons.Outlined.AttachFile,
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
        leftIcon = Icons.Outlined.Menu,
        leftDescription = "打开对话导航",
        onTemporaryAction = onExit,
        temporaryTint = AccentOrange,
        modifier = Modifier.align(Alignment.TopCenter).padding(horizontal = 18.dp, vertical = 18.dp),
    )
    Box(
        modifier = Modifier
            .align(Alignment.BottomCenter)
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
            ComposerAttachmentAction(Icons.Outlined.PhotoCamera, "相机") { composerMenu = ComposerMenu.NONE; onAddCamera() },
            ComposerAttachmentAction(Icons.Outlined.AddPhotoAlternate, "添加图片") { composerMenu = ComposerMenu.NONE; onAddImage() },
            ComposerAttachmentAction(Icons.Outlined.AttachFile, "添加文件") { composerMenu = ComposerMenu.NONE; onAddFile() },
        ),
        modelOptions = modelOptions,
        selectedModelId = recovery.modelOverrideId,
        onDismiss = { composerMenu = ComposerMenu.NONE },
        onSelectModel = { modelId -> composerMenu = ComposerMenu.NONE; onModelOverrideChanged(modelId) },
    )
    }
}

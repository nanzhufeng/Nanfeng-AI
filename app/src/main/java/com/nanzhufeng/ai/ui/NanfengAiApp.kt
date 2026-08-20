package com.nanzhufeng.ai.ui

import android.graphics.BitmapFactory
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.graphics.ImageDecoder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import com.nanzhufeng.ai.domain.P6KZipManualLinkTarget
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.nio.ByteBuffer
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.nanzhufeng.ai.domain.ChatGptImportItemId
import com.nanzhufeng.ai.domain.ChatGptImportTask
import com.nanzhufeng.ai.domain.ChatGptImportTaskId
import com.nanzhufeng.ai.domain.ClaudeImportItemId
import com.nanzhufeng.ai.domain.ClaudeImportTask
import com.nanzhufeng.ai.domain.ClaudeImportTaskId
import com.nanzhufeng.ai.domain.NanfengKnowledgeImportItemId
import com.nanzhufeng.ai.domain.NanfengKnowledgeImportTask
import com.nanzhufeng.ai.domain.NanfengKnowledgeImportTaskId

internal val PageBackground = Color(0xFFE6EAE7)
/** Semantic success only; it must not be reused as the interactive brand accent. */
internal val BrandGreen = Color(0xFF167A61)
/** Shared with the already-updated Desktop chat shell: bright, warm CTA orange. */
internal val AccentOrange = Color(0xFFE97128)
internal val AccentOrangeHover = Color(0xFFD86520)
internal val AccentOrangePressed = Color(0xFFC2581A)
internal val AccentOrangeSoft = Color(0xFFFFF1E5)
/** Low-emphasis input focus only; primary orange remains reserved for CTAs. */
internal val ComposerFocusBorder = Color(0xFFEFBD94)
internal val ComposerIdleBorder = Color(0xFFDFE2DE)
internal val AccentOnPrimary = Color.White
internal val AccentDisabled = Color(0xFFF4C8AA)
internal val ActionOrange = Color(0xFFE86E28)
internal val NeutralBorder = Color(0xFFD8DEDA)
internal val SubtleDivider = Color(0xFFFAFBFA)
internal val BodyText = Color(0xFF1E2925)
internal val NeutralAssistantSurface = Color(0xFFF7F8F7)
internal val NeutralSystemSurface = Color(0xFFF2F4F3)
internal val ToolSurface = Color(0xFFF1F5F8)
internal val SemanticErrorSurface = Color(0xFFFFECEB)
internal val ToolText = Color(0xFF31566C)
internal val ErrorText = Color(0xFFB3261E)

internal data class ChatRoleVisual(val surface: Color, val label: Color, val body: Color)

/** Shared role mapping: user messages retain their warm bubble; direct actions use solid orange. */
internal fun chatRoleVisual(role: com.nanzhufeng.ai.domain.MessageRole): ChatRoleVisual = when (role) {
    com.nanzhufeng.ai.domain.MessageRole.USER -> ChatRoleVisual(AccentOrangeSoft, AccentOrange, BodyText)
    com.nanzhufeng.ai.domain.MessageRole.ASSISTANT -> ChatRoleVisual(NeutralAssistantSurface, SecondaryText, BodyText)
    com.nanzhufeng.ai.domain.MessageRole.SYSTEM -> ChatRoleVisual(NeutralSystemSurface, SecondaryText, BodyText)
    com.nanzhufeng.ai.domain.MessageRole.TOOL -> ChatRoleVisual(ToolSurface, ToolText, BodyText)
}
internal val SecondaryText = Color(0xFF64706B)
internal val ErrorRed = Color(0xFFB3261E)
internal val CardShape = RoundedCornerShape(20.dp)

private enum class SettingsDestination(val label: String) {
    HOME("设置"),
    MODEL("模型服务"),
    CONVERSATIONS("对话与存储"),
    IMPORT("数据与导入"),
    FEATURE_REVIEW("功能审阅"),
    PRIVACY("隐私与安全"),
}

/**
 * Leaves the physical screen edge to Android system back. A drag which starts just inside either
 * app edge and travels inward returns to the still-available conversation drawer.
 */
private fun Modifier.settingsEdgeExit(onExit: () -> Unit): Modifier = pointerInput(onExit) {
    // Compose reports drag start after touch slop; keep the near-edge zone wide enough that the
    // original app-side edge touch is not reclassified as an interior drag.
    val activationEdge = 80.dp.toPx()
    val completionDistance = 72.dp.toPx()
    var direction = 0
    var travel = 0f
    detectHorizontalDragGestures(
        onDragStart = { offset ->
            direction = when {
                offset.x <= activationEdge -> 1
                offset.x >= size.width - activationEdge -> -1
                else -> 0
            }
            travel = 0f
        },
        onHorizontalDrag = { _, dragAmount -> if (direction != 0) travel += dragAmount },
        onDragCancel = { direction = 0 },
        onDragEnd = {
            if ((direction == 1 && travel >= completionDistance) ||
                (direction == -1 && travel <= -completionDistance)
            ) onExit()
            direction = 0
        },
    )
}

private fun java.io.InputStream.readMarkdownBounded(limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream(limit.coerceAtMost(64 * 1024)); val buffer = ByteArray(8 * 1024)
    while (output.size() < limit) { val count = read(buffer, 0, minOf(buffer.size, limit - output.size())); if (count < 0) break; output.write(buffer, 0, count) }
    return output.toByteArray()
}

/** DocumentsUI may expose an opaque URI segment; use its Openable display name for ZIP routing. */
private fun pickerDisplayName(context: Context, uri: Uri, fallback: String): String = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            .takeIf { it >= 0 }
            ?.let(cursor::getString)
    }
}.getOrNull()?.takeIf { it.isNotBlank() && !it.startsWith("msf:") } ?: fallback

@Composable
internal fun NanfengAiApp(
    viewModel: CaptureViewModel,
    modelSettingsViewModel: ModelSettingsViewModel,
    invocationLedgerViewModel: InvocationLedgerViewModel,
    knowledgeLibraryViewModel: KnowledgeLibraryViewModel,
    knowledgeExportViewModel: KnowledgeExportViewModel,
    conversationFoundationViewModel: ConversationFoundationViewModel,
    projectViewModel: ProjectViewModel,
    memoryViewModel: MemoryViewModel,
    contextBodySelectionViewModel: ContextBodySelectionViewModel,
    markdownImportViewModel: MarkdownKnowledgeImportViewModel,
    markdownExportViewModel: MarkdownKnowledgeExportViewModel,
    jsonKnowledgeImportViewModel: JsonKnowledgeImportViewModel,
    jsonKnowledgeExportViewModel: JsonKnowledgeExportViewModel,
    chatGptExportImportViewModel: ChatGptExportImportViewModel,
    claudeExportImportViewModel: ClaudeExportImportViewModel,
    p6kZipImportViewModel: P6KZipImportViewModel,
    nanfengKnowledgeExportImportViewModel: NanfengKnowledgeExportImportViewModel,
    pdfTextImportViewModel: PdfTextImportViewModel,
    webTextSnapshotViewModel: WebTextSnapshotViewModel,
    offlineEvalViewModel: OfflineEvalViewModel,
    privacyDataViewModel: PrivacyDataViewModel,
    localBackupRestoreViewModel: LocalBackupRestoreViewModel,
    conversationExchangeExportViewModel: ConversationExchangeExportViewModel,
    workspaceExchangeV2ExportViewModel: WorkspaceExchangeV2ExportViewModel,
    accountSyncViewModel: P7DAccountSyncViewModel,
    dualPathConnectionViewModel: DualPathConnectionViewModel,
    p8ControlledAgentViewModel: P8ControlledAgentViewModel,
    navigationViewModel: P5ANavigationViewModel,
    onRouteSelected: (P5ARoute) -> Unit,
    onConversationDrawerChanged: (Boolean) -> Unit,
    onExitSettingsToConversationDrawer: () -> Unit,
) {
    val context = LocalContext.current
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        viewModel.onPhotoPickerResult(uri)
    }
    val markdownPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val name = uri.lastPathSegment?.substringAfterLast('/') ?: "selected.md"
        val mime = context.contentResolver.getType(uri) ?: "text/markdown"
        context.contentResolver.openInputStream(uri)?.use { input ->
            // Bound the untrusted provider stream before any parse or Room write.
            markdownImportViewModel.selectedFile(name, mime, input.readMarkdownBounded((com.nanzhufeng.ai.domain.MARKDOWN_TASK_MAX_BYTES + 1).toInt()))
        }
    }
    val jsonKnowledgePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val name = uri.lastPathSegment?.substringAfterLast('/') ?: "knowledge.json"; val mime = context.contentResolver.getType(uri) ?: "application/json"
        context.contentResolver.openInputStream(uri)?.use { input -> jsonKnowledgeImportViewModel.selectedFile(name, mime, input.readMarkdownBounded((com.nanzhufeng.ai.domain.JSON_KNOWLEDGE_MAX_BYTES + 1).toInt())) }
    }
    val chatGptExportPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri ?: return@rememberLauncherForActivityResult; val name = uri.lastPathSegment?.substringAfterLast('/') ?: "conversations.json"; val mime = context.contentResolver.getType(uri) ?: "application/json"; context.contentResolver.openInputStream(uri)?.use { chatGptExportImportViewModel.selectedFile(name, mime, it.readMarkdownBounded((com.nanzhufeng.ai.domain.CHATGPT_EXPORT_MAX_BYTES + 1).toInt())) } }
    val claudeExportPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri ?: return@rememberLauncherForActivityResult; val name = uri.lastPathSegment?.substringAfterLast('/') ?: "conversations.json"; val mime = context.contentResolver.getType(uri) ?: "application/json"; context.contentResolver.openInputStream(uri)?.use { claudeExportImportViewModel.selectedFile(name, mime, it.readMarkdownBounded((com.nanzhufeng.ai.domain.CLAUDE_EXPORT_MAX_BYTES + 1).toInt())) } }
    val p6kChatGptZipPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri ?: return@rememberLauncherForActivityResult; val name = pickerDisplayName(context, uri, "chatgpt-export.zip"); val mime = context.contentResolver.getType(uri) ?: "application/zip"; context.contentResolver.openInputStream(uri)?.let { p6kZipImportViewModel.selected(com.nanzhufeng.ai.domain.ThirdPartyZipProvider.CHATGPT, name, mime, it) } }
    val p6kClaudeZipPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri ?: return@rememberLauncherForActivityResult; val name = pickerDisplayName(context, uri, "claude-export.zip"); val mime = context.contentResolver.getType(uri) ?: "application/zip"; context.contentResolver.openInputStream(uri)?.let { p6kZipImportViewModel.selected(com.nanzhufeng.ai.domain.ThirdPartyZipProvider.CLAUDE, name, mime, it) } }
    val nanfengKnowledgeExportPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri ?: return@rememberLauncherForActivityResult; val name = uri.lastPathSegment?.substringAfterLast('/') ?: "knowledge-export.json"; val mime = context.contentResolver.getType(uri) ?: "application/json"; context.contentResolver.openInputStream(uri)?.use { nanfengKnowledgeExportImportViewModel.selectedFile(name, mime, it.readMarkdownBounded((com.nanzhufeng.ai.domain.NANFENG_KNOWLEDGE_EXPORT_MAX_BYTES + 1).toInt())) } }
    val pdfTextPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val name = uri.lastPathSegment?.substringAfterLast('/') ?: "document.pdf"; val mime = context.contentResolver.getType(uri) ?: "application/pdf"
        context.contentResolver.openInputStream(uri)?.use { input -> pdfTextImportViewModel.selectedFile(name, mime, input.readMarkdownBounded((com.nanzhufeng.ai.domain.PDF_TEXT_MAX_BYTES + 1).toInt())) }
    }
    val localBackupExportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri -> uri?.let(localBackupRestoreViewModel::exported) }
    val localBackupImportPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(localBackupRestoreViewModel::selected) }
    var pendingConversationExchangeId by remember { mutableStateOf<com.nanzhufeng.ai.domain.ConversationId?>(null) }
    val conversationExchangeExportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val conversationId = pendingConversationExchangeId
        pendingConversationExchangeId = null
        if (uri != null && conversationId != null) conversationExchangeExportViewModel.export(conversationId, uri)
    }
    var pendingWorkspaceExchangeV2Scope by remember { mutableStateOf<com.nanzhufeng.ai.domain.WorkspaceExchangeV2ScopeSummary?>(null) }
    val workspaceExchangeV2ExportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val scope = pendingWorkspaceExchangeV2Scope
        pendingWorkspaceExchangeV2Scope = null
        if (uri != null && scope != null) workspaceExchangeV2ExportViewModel.export(uri) else workspaceExchangeV2ExportViewModel.clearScope()
    }
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = AccentOrange,
            onPrimary = AccentOnPrimary,
            primaryContainer = AccentOrangeSoft,
            secondary = ActionOrange,
            background = PageBackground,
            surface = Color.White,
            // Material dialogs and popups consume elevated surface-container tokens by
            // default. Keep every app-owned foreground surface pure white; the neutral
            // scrim, not a themed tint, provides modal separation from the transcript.
            surfaceTint = Color.Transparent,
            surfaceVariant = Color.White,
            surfaceContainerLow = Color.White,
            surfaceContainer = Color.White,
            surfaceContainerHigh = Color.White,
            surfaceContainerHighest = Color.White,
            error = ErrorRed,
            onBackground = BodyText,
            onSurface = BodyText,
        ),
    ) {
        Surface(color = PageBackground, modifier = Modifier.fillMaxSize()) {
            if (accountSyncViewModel.state.detailVisible) {
                P7DAccountSyncScreen(accountSyncViewModel.state, accountSyncViewModel::close)
                return@Surface
            }
            if (dualPathConnectionViewModel.state.detailVisible) {
                DualPathConnectionDialog(
                    dualPathConnectionViewModel.state,
                    dualPathConnectionViewModel::close,
                    dualPathConnectionViewModel::selectLocal,
                    dualPathConnectionViewModel::requestOnlineGuidance,
                )
            }
            P5AAdaptiveScaffold(
                route = navigationViewModel.state.route,
                onRouteSelected = onRouteSelected,
            ) { windowLayout ->
                CaptureScreen(
                state = viewModel.state,
                onChooseImage = {
                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onSaveText = { text -> viewModel.onTextInput(com.nanzhufeng.ai.domain.CaptureTextInput(
                    text = text,
                    sourceType = com.nanzhufeng.ai.domain.CaptureSourceType.MANUAL_TEXT,
                )) },
                onDismissMessage = viewModel::clearMessage,
                onRequestAi = viewModel::requestAiConfirmation,
                modelSettingsState = modelSettingsViewModel.state,
                onOpenModelSettings = modelSettingsViewModel::showDialog,
                privacyDataState = privacyDataViewModel.state,
                onOpenPrivacyData = privacyDataViewModel::show,
                onOpenLocalBackup = localBackupRestoreViewModel::show,
                conversationExchangeExportState = conversationExchangeExportViewModel.state,
                onExportConversationExchange = { conversationId ->
                    pendingConversationExchangeId = conversationId
                    conversationExchangeExportPicker.launch("${conversationId.value}.nfai-exchange")
                },
                workspaceExchangeV2ExportState = workspaceExchangeV2ExportViewModel.state,
                onSelectWorkspaceExchangeV2Scope = workspaceExchangeV2ExportViewModel::selectCompleteWorkspaceScope,
                accountSyncState = accountSyncViewModel.state,
                onOpenAccountSync = accountSyncViewModel::open,
                dualPathState = dualPathConnectionViewModel.state,
                onOpenDualPath = dualPathConnectionViewModel::open,
                onOpenP8ControlledAgent = p8ControlledAgentViewModel::show,
                knowledgeLibraryState = knowledgeLibraryViewModel.state,
                onOpenKnowledgeLibrary = knowledgeLibraryViewModel::showDialog,
                onOpenKnowledgeExport = knowledgeExportViewModel::showDialog,
                onOpenMarkdownImport = { markdownImportViewModel.showDialog(); markdownPicker.launch(arrayOf("text/markdown", "text/plain", "application/octet-stream")) },
                onOpenMarkdownExport = markdownExportViewModel::show,
                onOpenJsonKnowledgeImport = { jsonKnowledgeImportViewModel.show(); jsonKnowledgePicker.launch(arrayOf("application/json", "text/json", "application/octet-stream")) },
                onOpenJsonKnowledgeExport = jsonKnowledgeExportViewModel::show,
                onOpenChatGptExportImport = { chatGptExportImportViewModel.show(); chatGptExportPicker.launch(arrayOf("application/json", "text/json")) },
                chatGptImportState = chatGptExportImportViewModel.state,
                onOpenChatGptImportTask = chatGptExportImportViewModel::open,
                onBackChatGptImportTask = chatGptExportImportViewModel::back,
                onConfirmChatGptImportItem = chatGptExportImportViewModel::confirm,
                onSkipChatGptImportItem = chatGptExportImportViewModel::skip,
                onRetryChatGptImportTask = chatGptExportImportViewModel::retry,
                onCancelChatGptImport = chatGptExportImportViewModel::cancel,
                onOpenClaudeExportImport = { claudeExportImportViewModel.show(); claudeExportPicker.launch(arrayOf("application/json", "text/json")) },
                claudeImportState = claudeExportImportViewModel.state,
                onOpenClaudeImportTask = claudeExportImportViewModel::open,
                onBackClaudeImportTask = claudeExportImportViewModel::back,
                onConfirmClaudeImportItem = claudeExportImportViewModel::confirm,
                onSkipClaudeImportItem = claudeExportImportViewModel::skip,
                onRetryClaudeImportTask = claudeExportImportViewModel::retry,
                onCancelClaudeImport = claudeExportImportViewModel::cancel,
                p6kZipImportState = p6kZipImportViewModel.state,
                // DocumentsUI often classifies a valid ZIP as generic binary.  Let the picker show it,
                // then enforce the filename, app-private copy and strict central-directory checks in P6-K.
                onOpenP6KChatGptZip = { p6kZipImportViewModel.show(); p6kChatGptZipPicker.launch(arrayOf("*/*")) },
                onOpenP6KClaudeZip = { p6kZipImportViewModel.show(); p6kClaudeZipPicker.launch(arrayOf("*/*")) },
                onClearP6KZip = p6kZipImportViewModel::clear,
                onViewP6KZip = p6kZipImportViewModel::view,
                onSelectP6KAsset = p6kZipImportViewModel::selectAsset,
                onSelectP6KTarget = p6kZipImportViewModel::selectTarget,
                onLinkP6KAsset = p6kZipImportViewModel::link,
                onOpenNanfengKnowledgeExportImport = { nanfengKnowledgeExportImportViewModel.show(); nanfengKnowledgeExportPicker.launch(arrayOf("application/json", "text/json")) },
                nanfengKnowledgeImportState = nanfengKnowledgeExportImportViewModel.state,
                onOpenNanfengKnowledgeImportTask = nanfengKnowledgeExportImportViewModel::open,
                onBackNanfengKnowledgeImportTask = nanfengKnowledgeExportImportViewModel::back,
                onConfirmNanfengKnowledgeImportItem = nanfengKnowledgeExportImportViewModel::confirm,
                onSkipNanfengKnowledgeImportItem = nanfengKnowledgeExportImportViewModel::skip,
                onRetryNanfengKnowledgeImportTask = nanfengKnowledgeExportImportViewModel::retry,
                onCancelNanfengKnowledgeImport = nanfengKnowledgeExportImportViewModel::cancel,
                onOpenPdfTextImport = { pdfTextImportViewModel.show(); pdfTextPicker.launch(arrayOf("application/pdf")) },
                onOpenWebTextSnapshot = webTextSnapshotViewModel::show,
                onOpenOfflineEval = offlineEvalViewModel::show,
                conversationState = conversationFoundationViewModel.state,
                conversationViewModel = conversationFoundationViewModel,
                projectState = projectViewModel.state,
                    projectViewModel = projectViewModel,
                    memoryViewModel = memoryViewModel,
                    contextBodySelectionViewModel = contextBodySelectionViewModel,
                onCreateDevelopmentConversation = conversationFoundationViewModel::createDevelopmentConversation,
                onStartDeterministicLocalStream = conversationFoundationViewModel::startDeterministicLocalStream,
                onStopLocalStream = conversationFoundationViewModel::stopLocalStream,
                onContinueLocalAnswer = { conversationFoundationViewModel.performAction(com.nanzhufeng.ai.domain.ConversationActionKind.CONTINUE) },
                onRetryLocalAnswer = { conversationFoundationViewModel.performAction(com.nanzhufeng.ai.domain.ConversationActionKind.RETRY) },
                onChangeLocalModel = { preset -> conversationFoundationViewModel.performAction(com.nanzhufeng.ai.domain.ConversationActionKind.CHANGE_MODEL, preset) },
                onSwitchConversationBranch = conversationFoundationViewModel::switchToBranch,
                route = navigationViewModel.state.route,
                windowLayout = windowLayout,
                onRouteSelected = onRouteSelected,
                conversationDrawerOpen = navigationViewModel.state.conversationDrawerOpen,
                onConversationDrawerChanged = onConversationDrawerChanged,
                onReturnToConversationDrawer = onExitSettingsToConversationDrawer,
            )
            }
            viewModel.state.requestPreview?.let { preview ->
                AiEgressConfirmationDialog(
                    preview = preview,
                    consentChecked = viewModel.state.isConsentChecked,
                    onConsentChecked = viewModel::setEgressConsentChecked,
                    onDismiss = viewModel::dismissAiConfirmation,
                    onConfirm = viewModel::confirmAndRunAi,
                )
            }
            viewModel.state.pendingCandidate?.let { candidate ->
                CandidateReviewDialog(
                    candidate = candidate,
                    isSaving = viewModel.state.isSavingCandidate,
                    onDismiss = { /* A candidate remains pending until the user saves or cancels it. */ },
                    onDiscard = viewModel::discardCandidate,
                    onSave = viewModel::saveCandidate,
                )
            }
            if (modelSettingsViewModel.state.dialogVisible) {
                ModelSettingsDialog(
                    state = modelSettingsViewModel.state,
                    onDismiss = modelSettingsViewModel::dismissDialog,
                    onSave = modelSettingsViewModel::save,
                    onRevealStoredCredential = modelSettingsViewModel::revealStoredCredential,
                )
            }
            workspaceExchangeV2ExportViewModel.state.scope?.let { scope ->
                WorkspaceExchangeV2ScopeDialog(
                    scope = scope,
                    onDismiss = workspaceExchangeV2ExportViewModel::clearScope,
                    onExport = { onScope ->
                        pendingWorkspaceExchangeV2Scope = onScope
                        workspaceExchangeV2ExportPicker.launch("nanfeng-ai-workspace-v2.nfai-exchange")
                    },
                )
            }
            if (privacyDataViewModel.state.visible) {
                PrivacyDataDialog(
                    state = privacyDataViewModel.state,
                    onDismiss = privacyDataViewModel::dismiss,
                    onPreview = privacyDataViewModel::preview,
                    onToggleTask = privacyDataViewModel::toggleTask,
                    onPreviewSelectedTasks = privacyDataViewModel::previewSelectedTasks,
                    onConfirmation = privacyDataViewModel::confirmation,
                    onDelete = privacyDataViewModel::delete,
                    onRetryFailedTaskDeletion = privacyDataViewModel::retryFailedTaskDeletion,
                    onOpenBackup = localBackupRestoreViewModel::show,
                )
            }
            if (localBackupRestoreViewModel.state.visible) LocalBackupRestoreDialog(
                state = localBackupRestoreViewModel.state,
                onDismiss = localBackupRestoreViewModel::dismiss,
                onExport = { localBackupExportPicker.launch("nanfeng-ai-local-backup.nfai-backup") },
                onImport = { localBackupImportPicker.launch(arrayOf("application/zip", "application/octet-stream")) },
                onReplace = localBackupRestoreViewModel::replace,
                onRestore = localBackupRestoreViewModel::restore,
                onCancel = localBackupRestoreViewModel::cancel,
            )
            if (invocationLedgerViewModel.state.dialogVisible) {
                InvocationLedgerDialog(
                    state = invocationLedgerViewModel.state,
                    onDismiss = invocationLedgerViewModel::dismissDialog,
                )
            }
            if (knowledgeLibraryViewModel.state.dialogVisible) {
                KnowledgeLibraryDialog(
                    state = knowledgeLibraryViewModel.state,
                    onDismiss = knowledgeLibraryViewModel::dismissDialog,
                    onOpenDetail = knowledgeLibraryViewModel::openDetail,
                    onBackToList = knowledgeLibraryViewModel::backToList,
                    onSearch = knowledgeLibraryViewModel::updateSearch,
                    onStatus = knowledgeLibraryViewModel::setStatus,
                    onSource = knowledgeLibraryViewModel::setSourceType,
                    onArchiveRestore = knowledgeLibraryViewModel::archiveOrRestore,
                    onDeleteRestore = knowledgeLibraryViewModel::deleteOrRestoreTrash,
                    onStartCreate = knowledgeLibraryViewModel::startCreate,
                    onStartEdit = knowledgeLibraryViewModel::startEdit,
                    onEditTitle = { knowledgeLibraryViewModel.updateEdit(title = it) },
                    onEditBody = { knowledgeLibraryViewModel.updateEdit(body = it) },
                    onEditTags = { knowledgeLibraryViewModel.updateEdit(tags = it) },
                    onCancelEdit = knowledgeLibraryViewModel::cancelEdit,
                    onSaveEdit = knowledgeLibraryViewModel::saveEdit,
                    onFindDuplicateCandidates = knowledgeLibraryViewModel::findDuplicateCandidates,
                    onStartRelationshipBuilder = knowledgeLibraryViewModel::startRelationshipBuilder,
                    onShowRelationshipList = knowledgeLibraryViewModel::showRelationshipList,
                )
            }
            if (knowledgeLibraryViewModel.state.relationshipUi.builderVisible) {
                KnowledgeRelationshipBuilderDialog(
                    state = knowledgeLibraryViewModel.state.relationshipUi,
                    onDismiss = knowledgeLibraryViewModel::dismissRelationshipBuilder,
                    onType = knowledgeLibraryViewModel::selectRelationshipType,
                    onTarget = knowledgeLibraryViewModel::selectRelationshipTarget,
                    onConfirm = knowledgeLibraryViewModel::confirmRelationship,
                )
            }
            if (knowledgeLibraryViewModel.state.relationshipUi.listVisible) {
                KnowledgeRelationshipListDialog(
                    state = knowledgeLibraryViewModel.state.relationshipUi,
                    onDismiss = knowledgeLibraryViewModel::dismissRelationshipList,
                    onStatus = knowledgeLibraryViewModel::showRelationshipList,
                    onRevoke = knowledgeLibraryViewModel::revokeRelationship,
                )
            }
            if (knowledgeExportViewModel.state.dialogVisible) {
                KnowledgeExportDialog(
                    state = knowledgeExportViewModel.state,
                    onDismiss = knowledgeExportViewModel::dismissDialog,
                    onExport = knowledgeExportViewModel::export,
                )
            }
            if (markdownImportViewModel.state.dialogVisible) {
                MarkdownKnowledgeImportDialog(
                    state = markdownImportViewModel.state, onDismiss = markdownImportViewModel::dismiss, onBack = markdownImportViewModel::back,
                    onOpen = markdownImportViewModel::open, onRetry = markdownImportViewModel::retry, onEdit = markdownImportViewModel::edit,
                    onTitle = { markdownImportViewModel.update(title = it) }, onBody = { markdownImportViewModel.update(body = it) }, onTags = { markdownImportViewModel.update(tags = it) },
                    onConfirm = markdownImportViewModel::confirm, onSkip = markdownImportViewModel::skip, onCancel = markdownImportViewModel::cancel,
                )
            }
            if (markdownExportViewModel.state.visible) MarkdownKnowledgeExportDialog(markdownExportViewModel.state, markdownExportViewModel::dismiss, markdownExportViewModel::toggle, markdownExportViewModel::export)
            if (jsonKnowledgeImportViewModel.state.visible) JsonKnowledgeImportDialog(jsonKnowledgeImportViewModel.state, jsonKnowledgeImportViewModel::dismiss, jsonKnowledgeImportViewModel::back, jsonKnowledgeImportViewModel::open, jsonKnowledgeImportViewModel::retry, jsonKnowledgeImportViewModel::edit, { a, b, c -> jsonKnowledgeImportViewModel.update(a, b, c) }, jsonKnowledgeImportViewModel::confirm, jsonKnowledgeImportViewModel::skip, jsonKnowledgeImportViewModel::cancel)
            if (jsonKnowledgeExportViewModel.state.visible) JsonKnowledgeExportDialog(jsonKnowledgeExportViewModel.state, jsonKnowledgeExportViewModel::dismiss, jsonKnowledgeExportViewModel::toggle, jsonKnowledgeExportViewModel::export)
            if (pdfTextImportViewModel.state.visible) PdfTextImportDialog(pdfTextImportViewModel.state, pdfTextImportViewModel::dismiss, pdfTextImportViewModel::back, pdfTextImportViewModel::open, pdfTextImportViewModel::retry, pdfTextImportViewModel::edit, { a, b, c -> pdfTextImportViewModel.update(a, b, c) }, pdfTextImportViewModel::confirm, pdfTextImportViewModel::skip, pdfTextImportViewModel::cancel)
            if (webTextSnapshotViewModel.state.visible) WebTextSnapshotDialog(webTextSnapshotViewModel.state, webTextSnapshotViewModel::dismiss, webTextSnapshotViewModel::back, webTextSnapshotViewModel::open, webTextSnapshotViewModel::updateUrl, webTextSnapshotViewModel::setConfirmed, webTextSnapshotViewModel::start, webTextSnapshotViewModel::retry, webTextSnapshotViewModel::confirm, webTextSnapshotViewModel::skip, webTextSnapshotViewModel::cancel)
            if (offlineEvalViewModel.state.visible) OfflineEvalDialog(offlineEvalViewModel.state, offlineEvalViewModel::dismiss, offlineEvalViewModel::start, offlineEvalViewModel::select, offlineEvalViewModel::export, offlineEvalViewModel::alias, offlineEvalViewModel::selectCase, offlineEvalViewModel::score, offlineEvalViewModel::rotateDimension, offlineEvalViewModel::saveScore, offlineEvalViewModel.comparison())
            if (projectViewModel.state.dialogVisible) ProjectWorkspaceDialog(projectViewModel.state, projectViewModel)
            if (memoryViewModel.state.dialogVisible) MemoryWorkspaceDialog(memoryViewModel.state, memoryViewModel, projectViewModel.state.activeProjects, conversationFoundationViewModel.state.conversations)
            if (contextBodySelectionViewModel.state.dialogVisible) ContextBodySelectionDialog(contextBodySelectionViewModel.state, contextBodySelectionViewModel)
            if (p8ControlledAgentViewModel.state.visible) P8ControlledAgentDialog(p8ControlledAgentViewModel.state, p8ControlledAgentViewModel::dismiss, p8ControlledAgentViewModel::begin, p8ControlledAgentViewModel::confirm, p8ControlledAgentViewModel::pause, p8ControlledAgentViewModel::resume, p8ControlledAgentViewModel::cancel)
        }
    }
}

@Composable
private fun CaptureScreen(
    state: CaptureScreenState,
    onChooseImage: () -> Unit,
    onSaveText: (String) -> Unit,
    onDismissMessage: () -> Unit,
    onRequestAi: () -> Unit,
    modelSettingsState: ModelSettingsUiState,
    onOpenModelSettings: () -> Unit,
    privacyDataState: PrivacyDataUiState,
    onOpenPrivacyData: () -> Unit,
    onOpenLocalBackup: () -> Unit,
    conversationExchangeExportState: ConversationExchangeExportUiState,
    onExportConversationExchange: (com.nanzhufeng.ai.domain.ConversationId) -> Unit,
    workspaceExchangeV2ExportState: WorkspaceExchangeV2ExportUiState,
    onSelectWorkspaceExchangeV2Scope: () -> Unit,
    accountSyncState: P7DAccountSyncUiState,
    onOpenAccountSync: () -> Unit,
    dualPathState: DualPathConnectionUiState,
    onOpenDualPath: () -> Unit,
    onOpenP8ControlledAgent: () -> Unit,
    knowledgeLibraryState: KnowledgeLibraryUiState,
    onOpenKnowledgeLibrary: () -> Unit,
    onOpenKnowledgeExport: () -> Unit,
    onOpenMarkdownImport: () -> Unit,
    onOpenMarkdownExport: () -> Unit,
    onOpenJsonKnowledgeImport: () -> Unit,
    onOpenJsonKnowledgeExport: () -> Unit,
    onOpenChatGptExportImport: () -> Unit,
    chatGptImportState: ChatGptImportUiState,
    onOpenChatGptImportTask: (ChatGptImportTask) -> Unit,
    onBackChatGptImportTask: () -> Unit,
    onConfirmChatGptImportItem: (ChatGptImportItemId) -> Unit,
    onSkipChatGptImportItem: (ChatGptImportItemId) -> Unit,
    onRetryChatGptImportTask: (ChatGptImportTaskId) -> Unit,
    onCancelChatGptImport: () -> Unit,
    onOpenClaudeExportImport: () -> Unit,
    claudeImportState: ClaudeImportUiState,
    onOpenClaudeImportTask: (ClaudeImportTask) -> Unit,
    onBackClaudeImportTask: () -> Unit,
    onConfirmClaudeImportItem: (ClaudeImportItemId) -> Unit,
    onSkipClaudeImportItem: (ClaudeImportItemId) -> Unit,
    onRetryClaudeImportTask: (ClaudeImportTaskId) -> Unit,
    onCancelClaudeImport: () -> Unit,
    p6kZipImportState: P6KZipImportUiState,
    onOpenP6KChatGptZip: () -> Unit,
    onOpenP6KClaudeZip: () -> Unit,
    onClearP6KZip: (String) -> Unit,
    onViewP6KZip: (String) -> Unit,
    onSelectP6KAsset: (String) -> Unit,
    onSelectP6KTarget: (P6KZipManualLinkTarget) -> Unit,
    onLinkP6KAsset: () -> Unit,
    onOpenNanfengKnowledgeExportImport: () -> Unit,
    nanfengKnowledgeImportState: NanfengKnowledgeImportUiState,
    onOpenNanfengKnowledgeImportTask: (NanfengKnowledgeImportTask) -> Unit,
    onBackNanfengKnowledgeImportTask: () -> Unit,
    onConfirmNanfengKnowledgeImportItem: (NanfengKnowledgeImportItemId) -> Unit,
    onSkipNanfengKnowledgeImportItem: (NanfengKnowledgeImportItemId) -> Unit,
    onRetryNanfengKnowledgeImportTask: (NanfengKnowledgeImportTaskId) -> Unit,
    onCancelNanfengKnowledgeImport: () -> Unit,
    onOpenPdfTextImport: () -> Unit,
    onOpenWebTextSnapshot: () -> Unit,
    onOpenOfflineEval: () -> Unit,
    conversationState: ConversationFoundationUiState,
    conversationViewModel: ConversationFoundationViewModel,
    projectState: ProjectUiState,
    projectViewModel: ProjectViewModel,
    memoryViewModel: MemoryViewModel,
    contextBodySelectionViewModel: ContextBodySelectionViewModel,
    onCreateDevelopmentConversation: () -> Unit,
    onStartDeterministicLocalStream: () -> Unit,
    onStopLocalStream: () -> Unit,
    onContinueLocalAnswer: () -> Unit,
    onRetryLocalAnswer: () -> Unit,
    onChangeLocalModel: (com.nanzhufeng.ai.domain.ModelPresetId) -> Unit,
    onSwitchConversationBranch: (com.nanzhufeng.ai.domain.MessageNodeId) -> Unit,
    route: P5ARoute,
    windowLayout: P5AWindowLayout,
    onRouteSelected: (P5ARoute) -> Unit,
    conversationDrawerOpen: Boolean,
    onConversationDrawerChanged: (Boolean) -> Unit,
    onReturnToConversationDrawer: () -> Unit,
) {
    val expanded = windowLayout == P5AWindowLayout.EXPANDED
    var settingsDestination by remember { mutableStateOf(SettingsDestination.HOME) }
    val returnFromSettings = {
        if (settingsDestination == SettingsDestination.HOME) {
            // Management's archived/recycle-bin projection is settings-only. Never let it
            // leak back into the drawer and make ordinary conversations look missing.
            conversationViewModel.setListScope(com.nanzhufeng.ai.domain.ConversationListScope.ACTIVE)
            onReturnToConversationDrawer()
        }
        else settingsDestination = SettingsDestination.HOME
    }
    // The chat root owns its own LazyColumn. It must not inherit the settings/workspace
    // verticalScroll container, otherwise Compose measures the transcript at infinity.
    if (route == P5ARoute.CAPTURE || route == P5ARoute.CONVERSATION) {
        ConversationFoundationCard(
            conversationState,
            conversationViewModel,
            projectState,
            projectViewModel,
            contextBodySelectionViewModel,
            onRouteSelected,
            conversationDrawerOpen,
            onConversationDrawerChanged,
        )
        return
    }
    Column(
        modifier = Modifier
            .widthIn(max = 1280.dp)
            .fillMaxSize()
            .then(if (route == P5ARoute.SETTINGS) Modifier.systemGestureExclusion() else Modifier)
            .then(if (route == P5ARoute.SETTINGS) Modifier.settingsEdgeExit(returnFromSettings) else Modifier)
            .verticalScroll(rememberScrollState())
            .padding(start = if (expanded) 32.dp else 20.dp, end = if (expanded) 32.dp else 20.dp, top = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (route == P5ARoute.SETTINGS) {
            // Every settings return shares the same hierarchy: detail → settings home → drawer.
            // The bottom system gesture remains untouched because no bottom exclusion is set.
            BackHandler(onBack = returnFromSettings)
            SettingsPageHeader(
                destination = settingsDestination,
                onBack = returnFromSettings,
            )
        } else if (route != P5ARoute.CAPTURE && route != P5ARoute.CONVERSATION) {
            TextButton(onClick = { onRouteSelected(P5ARoute.CONVERSATION) }, shape = RoundedCornerShape(12.dp)) { Text("返回对话") }
            Header(route)
        }
        when (route) {
            P5ARoute.CAPTURE, P5ARoute.CONVERSATION -> error("chat routes return before the settings scroll container")
            P5ARoute.KNOWLEDGE -> WorkbenchRoute(expanded) {
                KnowledgeLibraryCard(knowledgeLibraryState, onOpenKnowledgeLibrary)
                KnowledgeExportCard(onOpenKnowledgeExport)
            }
            P5ARoute.PROJECTS -> WorkbenchRoute(expanded) { ProjectCard(projectState, projectViewModel) }
            P5ARoute.MEMORY -> WorkbenchRoute(expanded) { MemoryCard(memoryViewModel.state, memoryViewModel) }
            P5ARoute.CONTEXT -> WorkbenchRoute(expanded) { ContextControlCard(conversationState, contextBodySelectionViewModel) }
            P5ARoute.EVAL -> WorkbenchRoute(expanded) { OfflineEvalCard(onOpenOfflineEval) }
            P5ARoute.SETTINGS -> SettingsHierarchy(
                destination = settingsDestination,
                expanded = expanded,
                modelSettingsState = modelSettingsState,
                onOpenModelSettings = onOpenModelSettings,
                conversationState = conversationState,
                conversationViewModel = conversationViewModel,
                privacyDataState = privacyDataState,
                onOpenPrivacyData = onOpenPrivacyData,
                onOpenImport = { onRouteSelected(P5ARoute.ADAPTERS) },
                onOpenBackup = onOpenLocalBackup,
                conversationExchangeExportState = conversationExchangeExportState,
                onExportConversationExchange = onExportConversationExchange,
                onSelect = { settingsDestination = it },
            )
            P5ARoute.ADAPTERS -> WorkbenchRoute(expanded) {
                Text("数据导入", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Text("设置 · 数据 · 导入、同步与存储", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                LocalKnowledgeImportEntryCard(
                    onOpenMarkdownImport = onOpenMarkdownImport,
                    onOpenMarkdownExport = onOpenMarkdownExport,
                    onOpenJsonKnowledgeImport = onOpenJsonKnowledgeImport,
                    onOpenJsonKnowledgeExport = onOpenJsonKnowledgeExport,
                    onOpenPdfTextImport = onOpenPdfTextImport,
                    onOpenWebTextSnapshot = onOpenWebTextSnapshot,
                )
                ChatGptExportImportSettingsPage(
                    state = chatGptImportState,
                    onChoose = onOpenChatGptExportImport,
                    onOpen = onOpenChatGptImportTask,
                    onBack = onBackChatGptImportTask,
                    onRetry = onRetryChatGptImportTask,
                    onCancel = onCancelChatGptImport,
                )
                ClaudeExportImportSettingsPage(
                    state = claudeImportState,
                    onChoose = onOpenClaudeExportImport,
                    onOpen = onOpenClaudeImportTask,
                    onBack = onBackClaudeImportTask,
                    onRetry = onRetryClaudeImportTask,
                    onCancel = onCancelClaudeImport,
                )
                P6KZipImportSettingsCard(
                    state = p6kZipImportState,
                    onChatGpt = onOpenP6KChatGptZip,
                    onClaude = onOpenP6KClaudeZip,
                    onClear = onClearP6KZip,
                    onView = onViewP6KZip,
                    onSelectAsset = onSelectP6KAsset,
                    onSelectTarget = onSelectP6KTarget,
                    onLink = onLinkP6KAsset,
                )
                WorkspaceExchangeV2ExportCard(
                    state = workspaceExchangeV2ExportState,
                    onSelectScope = onSelectWorkspaceExchangeV2Scope,
                )
                NanfengKnowledgeExportImportSettingsPage(
                    state = nanfengKnowledgeImportState,
                    onChoose = onOpenNanfengKnowledgeExportImport,
                    onOpen = onOpenNanfengKnowledgeImportTask,
                    onBack = onBackNanfengKnowledgeImportTask,
                    onRetry = onRetryNanfengKnowledgeImportTask,
                    onCancel = onCancelNanfengKnowledgeImport,
                )
            }
            P5ARoute.CONTROL -> ControlHub(onRouteSelected)
        }
    }
}

@Composable
private fun WorkspaceExchangeV2ExportCard(
    state: WorkspaceExchangeV2ExportUiState,
    onSelectScope: () -> Unit,
) {
    WhiteCard {
        Text("完整工作区交换（v2）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text("先选择完整工作区范围，再由系统选择保存位置。导出前严格校验；只在写入后回读哈希一致时显示成功。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(6.dp))
        Text("附件一律按高敏感内容处理；它不是备份、云同步或对象恢复。", color = SecondaryText, style = MaterialTheme.typography.labelSmall)
        state.notice?.let { notice -> Spacer(Modifier.height(8.dp)); Text(notice, color = SecondaryText, style = MaterialTheme.typography.bodySmall) }
        state.error?.let { error -> Spacer(Modifier.height(8.dp)); Text(error, color = ErrorRed, style = MaterialTheme.typography.bodySmall) }
        Spacer(Modifier.height(10.dp))
        Button(onClick = onSelectScope, enabled = !state.working, modifier = Modifier.fillMaxWidth().height(48.dp), shape = P5AInteractiveShape) {
            if (state.working) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = AccentOnPrimary)
            else Text("选择完整工作区范围")
        }
    }
}

@Composable
private fun WorkspaceExchangeV2ScopeDialog(
    scope: com.nanzhufeng.ai.domain.WorkspaceExchangeV2ScopeSummary,
    onDismiss: () -> Unit,
    onExport: (com.nanzhufeng.ai.domain.WorkspaceExchangeV2ScopeSummary) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("完整工作区范围") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("将严格导出当前范围内的全部对象；任一对象不完整、不可读取或不符合 v2 合同，均不会写入文件。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                Text("项目 ${scope.projectCount} · 对话 ${scope.conversationCount} · 知识 ${scope.knowledgeCount}", style = MaterialTheme.typography.bodyMedium)
                Text("记忆 ${scope.memoryCount} · 关系 ${scope.relationCount} · 附件 ${scope.attachmentCount}", style = MaterialTheme.typography.bodyMedium)
                Text("附件按高敏感级别处理；选择保存位置后即开始生成与回读，不再显示第二次产品确认。", color = SecondaryText, style = MaterialTheme.typography.labelSmall)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        confirmButton = { Button(onClick = { onExport(scope) }, shape = P5AInteractiveShape) { Text("选择保存位置") } },
    )
}

/** Existing local import owners are exposed here only; this card owns no parsing or network work. */
@Composable
private fun LocalKnowledgeImportEntryCard(
    onOpenMarkdownImport: () -> Unit,
    onOpenMarkdownExport: () -> Unit,
    onOpenJsonKnowledgeImport: () -> Unit,
    onOpenJsonKnowledgeExport: () -> Unit,
    onOpenPdfTextImport: () -> Unit,
    onOpenWebTextSnapshot: () -> Unit,
) = WhiteCard {
    Text("知识文件与网页文本", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
    Text("每种来源都在既有独立任务中预检、确认和保存；不会读取模型 Key。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(12.dp))
    Button(onClick = onOpenMarkdownImport, modifier = Modifier.fillMaxWidth().height(48.dp), shape = P5AInteractiveShape) { Text("导入 Markdown 文件") }
    Spacer(Modifier.height(8.dp))
    OutlinedButton(onClick = onOpenMarkdownExport, modifier = Modifier.fillMaxWidth().height(48.dp), shape = P5AInteractiveShape) { Text("导出 Markdown 知识") }
    Spacer(Modifier.height(8.dp))
    Button(onClick = onOpenJsonKnowledgeImport, modifier = Modifier.fillMaxWidth().height(48.dp), shape = P5AInteractiveShape) { Text("导入知识 JSON") }
    Spacer(Modifier.height(8.dp))
    OutlinedButton(onClick = onOpenJsonKnowledgeExport, modifier = Modifier.fillMaxWidth().height(48.dp), shape = P5AInteractiveShape) { Text("导出知识 JSON") }
    Spacer(Modifier.height(8.dp))
    Button(onClick = onOpenPdfTextImport, modifier = Modifier.fillMaxWidth().height(48.dp), shape = P5AInteractiveShape) { Text("导入 PDF 文本") }
    Spacer(Modifier.height(8.dp))
    OutlinedButton(onClick = onOpenWebTextSnapshot, modifier = Modifier.fillMaxWidth().height(48.dp), shape = P5AInteractiveShape) { Text("保存网页文本快照") }
}

@Composable
private fun SettingsPageHeader(destination: SettingsDestination, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = if (destination == SettingsDestination.HOME) "返回侧栏" else "返回设置",
            )
        }
        Text(destination.label, modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SettingsHierarchy(
    destination: SettingsDestination,
    expanded: Boolean,
    modelSettingsState: ModelSettingsUiState,
    onOpenModelSettings: () -> Unit,
    conversationState: ConversationFoundationUiState,
    conversationViewModel: ConversationFoundationViewModel,
    privacyDataState: PrivacyDataUiState,
    onOpenPrivacyData: () -> Unit,
    onOpenImport: () -> Unit,
    onOpenBackup: () -> Unit,
    conversationExchangeExportState: ConversationExchangeExportUiState,
    onExportConversationExchange: (com.nanzhufeng.ai.domain.ConversationId) -> Unit,
    onSelect: (SettingsDestination) -> Unit,
) {
    if (destination == SettingsDestination.HOME) {
        SettingsCategoryList(onSelect)
        return
    }
    WorkbenchRoute(expanded) {
        when (destination) {
            SettingsDestination.HOME -> Unit
            SettingsDestination.MODEL -> {
                ModelServiceStatusCard(modelSettingsState, onOpenModelSettings)
            }
            SettingsDestination.CONVERSATIONS -> ConversationManagementSettingsCard(
                state = conversationState,
                onScope = conversationViewModel::setListScope,
                onManage = { conversation, action, title -> conversationViewModel.manage(conversation, action, title) },
                onExport = conversationViewModel::exportCurrentConversation,
                exchangeExportState = conversationExchangeExportState,
                onExchangeExport = onExportConversationExchange,
            )
            SettingsDestination.IMPORT -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                WhiteCard {
                    Text("导入中心", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text("选择 ChatGPT、Claude、ZIP、知识文件或本机备份的导入方式。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = onOpenImport, modifier = Modifier.fillMaxWidth().height(48.dp), shape = P5AInteractiveShape) { Text("打开导入中心") }
                }
                WhiteCard {
                    Text("备份与恢复", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text("手动备份或恢复本机数据。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = onOpenBackup, modifier = Modifier.fillMaxWidth().height(48.dp), shape = P5AInteractiveShape) { Text("打开备份与恢复") }
                }
            }
            SettingsDestination.FEATURE_REVIEW -> FeatureReviewSettingsCard()
            SettingsDestination.PRIVACY -> PrivacyDataCard(privacyDataState, onOpenPrivacyData)
        }
    }
}

@Composable
private fun SettingsCategoryList(onSelect: (SettingsDestination) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().widthIn(max = 640.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SettingsCategoryRow(Icons.Outlined.Settings, "AI 模型") { onSelect(SettingsDestination.MODEL) }
        SettingsCategoryRow(Icons.Outlined.ChatBubbleOutline, "对话") { onSelect(SettingsDestination.CONVERSATIONS) }
        SettingsCategoryRow(Icons.Outlined.Save, "数据与导入") { onSelect(SettingsDestination.IMPORT) }
        SettingsCategoryRow(Icons.Outlined.Settings, "功能审阅") { onSelect(SettingsDestination.FEATURE_REVIEW) }
        SettingsCategoryRow(Icons.Outlined.Lock, "隐私") { onSelect(SettingsDestination.PRIVACY) }
    }
}

@Composable
private fun FeatureReviewSettingsCard() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        WhiteCard {
            Text("新增功能审阅", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text("新增能力在进入常用界面前，先在这里列出用途、现有入口与待您判断项。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
        }
        WhiteCard {
            Text("ChatGPT / Claude ZIP 导入", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text("当前：待您判断保留或删减 · 入口：设置 → 数据与导入 → 导入中心。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
            Text("建议：保留设置入口；暂不在对话主页添加快捷按钮，避免高敏感导入被误触。", color = SecondaryText, style = MaterialTheme.typography.labelSmall)
        }
        WhiteCard {
            Text("未关联媒体人工关联", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text("当前：待外部归属证据后再判断是否启用 · 入口：ZIP 导入批次详情。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
            Text("建议：仅在存在未关联媒体时提供二级操作；不在聊天主界面常驻功能按钮。", color = SecondaryText, style = MaterialTheme.typography.labelSmall)
        }
        WhiteCard {
            Text("Desktop Compare 联网执行", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text("当前：待您判断是否保留；需先完成服务商、模型、费用与凭据安全方案。入口：既有对话 Composer 的“对比”操作。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
            Text("阶段 1/2 已有 fail-closed owner 与 Security.framework 边界；建议：复用现有“对比”操作，不新增 Composer 常驻按钮；固定预设与状态只放在设置 → AI 模型服务。", color = SecondaryText, style = MaterialTheme.typography.labelSmall)
        }
        WhiteCard {
            Text("本地精确复用", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text("当前：待您判断是否保留；仅完成离线精确键与既有消息引用的安全索引，尚未接入普通聊天执行。入口：设置 → 功能审阅。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
            Text("建议：暂不增加聊天或 Composer 按键；只有将来真实复用、用量和清理能力完整后，再在设置提供独立开关与清理入口，避免误解为联网缓存或省费承诺。", color = SecondaryText, style = MaterialTheme.typography.labelSmall)
        }
        WhiteCard {
            Text("跨端文本会话交换", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text("当前：待您判断是否保留；Android 现只可从设置 → 对话导出当前活动、未归属项目且无附件/工具结果的文本会话为 .nfai-exchange，Desktop 可按既有工作区导入。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
            Text("建议：只保留设置二级入口，不在聊天主页或 Composer 增加按键；它不是本机备份、云同步，也不代表完整工作区跨端保真。", color = SecondaryText, style = MaterialTheme.typography.labelSmall)
        }
        WhiteCard {
            Text("完整工作区交换（v2）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text("当前：待您判断是否保留；Android 可从设置 → 数据与导入显式选择完整范围并经系统保存位置导出 v2 包，Desktop 仅可从设置选择 v2 包后私有导入。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
            Text("建议：只保留双端设置二级入口，不在聊天主页、Composer 或工作页增加按键；它不是备份、云同步，也不表示已恢复为原生对象。", color = SecondaryText, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun SettingsCategoryRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(58.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF3F4F3)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, pressedElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(icon, contentDescription = null, tint = BodyText, modifier = Modifier.size(20.dp))
            Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Icon(Icons.Outlined.ChevronRight, contentDescription = "打开$title", tint = SecondaryText, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun WorkbenchRoute(expanded: Boolean, content: @Composable ColumnScope.() -> Unit) {
    if (expanded) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1.8f), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
            WhiteCard(modifier = Modifier.weight(1f)) {
                Text("当前工作区", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text("展开态只增加导航与辅助阅读区。操作、选择和本地事实仍由原有 ViewModel 与 Room 唯一持有。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }
}

@Composable
private fun ContextControlCard(state: ConversationFoundationUiState, viewModel: ContextBodySelectionViewModel) = WhiteCard {
    Text("本次 Context", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
    Text("所有来源默认关闭；选择只在当前控制面内存中，不构造 Prompt 或发送内容。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(12.dp))
    OutlinedButton(
        onClick = { viewModel.showDialog(state.selectedConversationId) },
        enabled = state.selectedConversationId != null,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape = P5AInteractiveShape,
    ) { Text("打开 Context 控制面") }
}

@Composable
private fun ControlHub(onRouteSelected: (P5ARoute) -> Unit) = WhiteCard {
    Text("更多本地控制面", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
    Text("入口只切换工作区，不复制 Projects、Memory、Context 或离线 Eval 的业务状态。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(12.dp))
    listOf(P5ARoute.PROJECTS, P5ARoute.MEMORY, P5ARoute.CONTEXT, P5ARoute.EVAL, P5ARoute.ADAPTERS).forEach { item ->
        OutlinedButton(onClick = { onRouteSelected(item) }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = P5AInteractiveShape) { Text(item.label) }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ConversationFoundationCard(
    state: ConversationFoundationUiState,
    viewModel: ConversationFoundationViewModel,
    projectState: ProjectUiState,
    projectViewModel: ProjectViewModel,
    contextBodySelectionViewModel: ContextBodySelectionViewModel,
    onRouteSelected: (P5ARoute) -> Unit,
    drawerOpen: Boolean,
    onDrawerOpenChanged: (Boolean) -> Unit,
) {
    var workspaceVisible by rememberSaveable { mutableStateOf(true) }
    val conversationPhotoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        viewModel.onConversationPhotoPickerResult(uri)
    }
    val conversationCamera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        viewModel.onConversationCameraResult(bitmap)
    }
    val conversationDocumentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        viewModel.onConversationDocumentPickerResult(uri)
    }
    val temporaryPhotoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        viewModel.onTemporaryPhotoPickerResult(uri)
    }
    val temporaryCamera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        viewModel.onTemporaryCameraResult(bitmap)
    }
    val temporaryDocumentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        viewModel.onTemporaryDocumentPickerResult(uri)
    }
    WhiteCard {
        Text("对话", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            if (state.conversations.isEmpty()) "还没有本地对话。" else "${state.conversations.size} 个本地对话，当前路径与草稿均可恢复。",
            color = SecondaryText, style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(10.dp))
        Button(onClick = { workspaceVisible = true }, modifier = Modifier.fillMaxWidth().height(46.dp), shape = RoundedCornerShape(16.dp)) { Text("进入本地对话") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { contextBodySelectionViewModel.showDialog(state.selectedConversationId) }, enabled = state.selectedConversationId != null, modifier = Modifier.fillMaxWidth().height(46.dp), shape = RoundedCornerShape(16.dp)) { Text("选择本次 Context 正文") }
    }
    if (workspaceVisible) ConversationWorkspaceDialog(
        state = state, onDismiss = { /* Root chat has no dismiss-to-workbench escape hatch. */ }, onCreate = viewModel::createDevelopmentConversation,
        onSelect = viewModel::selectConversation, onSurfaceChanged = viewModel::selectSurface, onDraftChanged = viewModel::updateDraft, onSubmitDraft = viewModel::submitCurrentDraft,
        onRequestNormalChatExternalSendConfirmation = viewModel::requestNormalChatExternalSendConfirmation,
        onSetNormalChatExternalSendAcknowledgement = viewModel::setNormalChatExternalSendAcknowledgement,
        onExpireNormalChatExternalSendConfirmation = viewModel::expireNormalChatExternalSendConfirmation,
        onDismissNormalChatExternalSendConfirmation = viewModel::dismissNormalChatExternalSendConfirmation,
        onRequestCompare = viewModel::requestCompareChatGptAndClaude,
        onStartFixture = { viewModel.startDeterministicLocalStream() }, onStartFailureFixture = { viewModel.startDeterministicLocalStream(fail = true) },
        onStop = viewModel::stopLocalStream, onAction = viewModel::performAction, onSwitchBranch = viewModel::switchToBranch, onBranchFromMessage = viewModel::branchFromMessage,
        onEditUserMessage = viewModel::editCurrentPathUserMessage,
        onListScope = viewModel::setListScope, onSearchChanged = viewModel::updateSearchQuery, onSearchCategoryChanged = viewModel::selectSearchCategory, onSearchRequested = viewModel::submitSearch, onSearchFocus = viewModel::openSearchHistory, onCloseSearchHistory = viewModel::closeSearchHistory, onFillSearchHistory = viewModel::fillSearchHistory, onClearSearchHistory = viewModel::clearSearchHistory, onCloseSearch = viewModel::closeSearchPanel, onOpenSearchHit = viewModel::openSearchHit,
        onManage = viewModel::manage, onExport = viewModel::exportCurrentConversation,
        onAddCamera = { conversationCamera.launch(null) },
        onAddImage = { conversationPhotoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        onAddFile = { conversationDocumentPicker.launch(arrayOf("video/mp4", "audio/*", "application/pdf", "text/plain", "text/markdown", "application/json", "text/csv")) },
        onRemoveAttachment = viewModel::removeDraftAttachment,
        onOpenImagePreview = viewModel::openImagePreview,
        onCloseImagePreview = viewModel::closeImagePreview,
        onOpenPdfPreview = viewModel::openPdfPreview,
        onOpenPdfPage = viewModel::openPdfPage,
        onClosePdfPreview = viewModel::closePdfPreview,
        onOpenVideoPreview = viewModel::openVideoPreview,
        onCloseVideoPreview = viewModel::closeVideoPreview,
        onOpenAudioPreview = viewModel::openAudioPreview,
        onCloseAudioPreview = viewModel::closeAudioPreview,
        onOpenTextPreview = viewModel::openTextPreview,
        onCloseTextPreview = viewModel::closeTextPreview,
        onRequestAttachmentTransfer = viewModel::requestAttachmentTransfer,
        onRequestAttachmentTransfers = viewModel::requestAttachmentTransfers,
        onConsumeAttachmentTransfer = viewModel::consumeAttachmentTransfer,
        onEnterTemporary = viewModel::enterTemporaryConversation,
        onUpdateTemporaryDraft = viewModel::updateTemporaryDraft,
        onUpdateTemporaryModelOverride = viewModel::updateTemporaryModelOverride,
        onSubmitTemporaryDraft = viewModel::submitTemporaryDraft,
        onExitTemporary = viewModel::leaveTemporaryConversation,
        onSelectP6GModel = viewModel::selectP6GModel,
        onAddTemporaryCamera = { temporaryCamera.launch(null) },
        onAddTemporaryImage = { temporaryPhotoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        onAddTemporaryFile = { temporaryDocumentPicker.launch(arrayOf("application/pdf", "text/plain", "text/markdown", "application/json", "text/csv")) },
        onRemoveTemporaryDraftAttachment = viewModel::removeTemporaryDraftAttachment,
        projects = projectState.activeProjects,
        currentProjectId = state.currentProjectId?.let { com.nanzhufeng.ai.domain.ProjectId(it) },
        onAssignProject = viewModel::assignProject,
        onOpenRoute = onRouteSelected,
        drawerOpen = drawerOpen,
        onDrawerOpenChanged = onDrawerOpenChanged,
    )
}

@Composable
private fun ProjectCard(state: ProjectUiState, viewModel: ProjectViewModel) = WhiteCard {
    Text("Projects", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
    Text(if (state.projects.isEmpty()) "项目用于本地指令、会话与知识范围隔离。" else "${state.projects.size} 个本地项目；系统与安全规则始终优先。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(10.dp))
    Button(onClick = viewModel::showDialog, modifier = Modifier.fillMaxWidth().height(46.dp), shape = RoundedCornerShape(16.dp)) { Text("管理 Projects") }
}

@Composable
private fun MemoryCard(state: MemoryUiState, viewModel: MemoryViewModel) = WhiteCard {
    Text("长期 Memory", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
    Text(if (state.memories.isEmpty()) "显式创建、可暂停、可删除；不会自动加入对话上下文。" else "${state.memories.size} 条可见本地记忆；不会自动加入对话上下文。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(10.dp))
    Button(onClick = viewModel::showDialog, modifier = Modifier.fillMaxWidth().height(46.dp), shape = RoundedCornerShape(16.dp)) { Text("管理长期 Memory") }
}

@Composable
private fun PrivacyDataCard(state: PrivacyDataUiState, onOpen: () -> Unit) = WhiteCard {
    Text("隐私与数据", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
    Text("本地优先；当前 Provider 推理外发已禁用。查看清单、范围删除或手动导出不上传的安全诊断。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(10.dp))
    Button(onClick = onOpen, modifier = Modifier.fillMaxWidth().height(46.dp), shape = P5AInteractiveShape) { Text("打开隐私与数据") }
    state.notice?.let { Text(it, color = BrandGreen, style = MaterialTheme.typography.bodySmall) }
}

@Composable
private fun Header(route: P5ARoute) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(route.label, modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text(
            when (route) {
                P5ARoute.CAPTURE -> "先把文字或图片安全保存到本机，再进入整理与知识沉淀。"
                P5ARoute.CONVERSATION -> "本地对话、草稿与当前路径由同一份持久化事实恢复。"
                P5ARoute.KNOWLEDGE -> "知识管理与导出保持明确确认和本地优先边界。"
                P5ARoute.PROJECTS -> "项目指令、会话与知识范围隔离由 Projects 统一管理。"
                P5ARoute.MEMORY -> "长期 Memory 必须显式创建，且不会自动加入上下文。"
                P5ARoute.CONTEXT -> "仅为当前会话显式选择本地 Context；不会构造 Prompt。"
                P5ARoute.EVAL -> "离线 Eval 只验证本地合同，不代表真实服务结果。"
                P5ARoute.SETTINGS -> "模型配置与调用账本保持本地、可审计的产品边界。"
                P5ARoute.ADAPTERS -> "每种导入/网页适配器独立确认，外部数据始终不可信。"
                P5ARoute.CONTROL -> "在紧凑屏中进入其余可恢复的本地工作区。"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = SecondaryText,
        )
    }
}

@Composable
private fun PreviewCard(state: CaptureScreenState, modifier: Modifier) {
    WhiteCard(modifier = modifier.heightIn(min = 320.dp)) {
        Text("当前草稿", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = BrandGreen)
        Spacer(Modifier.height(12.dp))
        when {
            state.isRestoring -> LoadingWorkspace("正在恢复本地草稿…")
            state.capturedImage != null -> CapturedPreview(state.capturedImage)
            state.capturedText != null -> CapturedTextPreview(state.capturedText)
            else -> EmptyWorkspace()
        }
    }
}

@Composable
private fun EmptyWorkspace() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 240.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFF5F7F6)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Outlined.Image, contentDescription = null, tint = BrandGreen, modifier = Modifier.size(42.dp))
            Text("还没有本地草稿", fontWeight = FontWeight.Medium)
            Text("可输入文字，或从相册选择图片", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun CapturedTextPreview(draft: com.nanzhufeng.ai.domain.CaptureDraft) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            draft.text.orEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 160.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFFF5F7F6))
                .padding(16.dp),
            color = BodyText,
        )
        Text(
            if (draft.sourceEvidence.single().sourceType == com.nanzhufeng.ai.domain.CaptureSourceType.ANDROID_TEXT_SHARE) {
                "来自系统文本分享，已保存到本机。"
            } else {
                "手工输入，已保存到本机。"
            },
            color = SecondaryText,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun LoadingWorkspace(label: String) {
    Box(modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(color = BrandGreen, modifier = Modifier.size(32.dp))
            Text(label, color = SecondaryText)
        }
    }
}

@Composable
private fun CapturedPreview(captured: CapturedImageState) {
    val preview = remember(captured.previewBytes) { decodePreview(captured.previewBytes) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 240.dp, max = 460.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFFF5F7F6)),
            contentAlignment = Alignment.Center,
        ) {
            if (preview == null) {
                Text("私有副本已保存，但此设备暂时无法生成预览。", color = ErrorRed, modifier = Modifier.padding(20.dp))
            } else {
                Image(
                    bitmap = preview,
                    contentDescription = "已选择图片预览",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp, max = 460.dp),
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Lock, contentDescription = null, tint = BrandGreen, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Column {
                Text(captured.draft.attachments.single().displayName ?: "相册图片", fontWeight = FontWeight.Medium)
                Text(
                    "${captured.draft.attachments.single().mimeType} · ${formatBytes(captured.draft.attachments.single().byteCount)}",
                    color = SecondaryText,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ActionCard(
    state: CaptureScreenState,
    onChooseImage: () -> Unit,
    onSaveText: (String) -> Unit,
    onRequestAi: () -> Unit,
) {
    val saveActionRequester = remember { BringIntoViewRequester() }
    var textInputFocused by remember { mutableStateOf(false) }
    val imeVisible = WindowInsets.isImeVisible
    LaunchedEffect(textInputFocused, imeVisible) {
        if (textInputFocused && imeVisible) saveActionRequester.bringIntoView()
    }
    WhiteCard {
        Text("手工文本", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            "输入后保存为本地草稿；空白内容不会覆盖已有草稿。",
            color = SecondaryText,
            style = MaterialTheme.typography.bodyMedium,
        )
        var text by rememberSaveable(state.capturedText?.id?.value) { mutableStateOf(state.capturedText?.text.orEmpty()) }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focus ->
                    textInputFocused = focus.isFocused
                }
                .p5aKeyboardTraversal(),
            minLines = 4,
            label = { Text("输入要捕获的文字") },
            enabled = !state.isRestoring && !state.isSavingText && !state.isImporting && state.pendingCandidate == null,
            shape = RoundedCornerShape(16.dp),
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { onSaveText(text) },
            enabled = !state.isRestoring && !state.isSavingText && !state.isImporting && state.pendingCandidate == null,
            modifier = Modifier.fillMaxWidth().height(52.dp).bringIntoViewRequester(saveActionRequester),
            shape = RoundedCornerShape(16.dp),
        ) {
            if (state.isSavingText) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("正在保存草稿…")
            } else {
                Icon(Icons.Outlined.Save, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text("保存文本草稿")
            }
        }
        Spacer(Modifier.height(24.dp))
        Text("相册输入", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text("使用系统图片选择器，不申请整库读取权限。当前不会联网，也不会发送图片。", color = SecondaryText, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onChooseImage,
            enabled = !state.isImporting && !state.isRestoring && !state.isSavingText && state.pendingCandidate == null,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ActionOrange),
        ) {
            if (state.isImporting) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("正在复制并保存…")
            } else {
                Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text(if (state.capturedImage == null) "从相册选择" else "重新选择图片")
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("本地 Mock 整理", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text("先查看本次内容范围并单独确认；Mock 不连接真实服务，结果只会成为待核对候选。", color = SecondaryText, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onRequestAi,
            enabled = !state.isRestoring && !state.isImporting && !state.isSavingText && !state.isRunningAi &&
                state.currentDraft() != null && state.pendingCandidate == null,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            if (state.isRunningAi) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("本地 Mock 正在运行…")
            } else {
                Text(if (state.pendingCandidate == null) "查看并确认本地整理" else "请先核对当前候选")
            }
        }
    }
}

@Composable
private fun AiEgressConfirmationDialog(
    preview: com.nanzhufeng.ai.domain.AiRequestPreview,
    consentChecked: Boolean,
    onConsentChecked: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(24.dp),
        title = { Text("确认本次本地整理", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("服务：${preview.providerLabel}", fontWeight = FontWeight.Medium)
                Text("预设：${preview.presetLabel} · 实际模型：${preview.model.id}", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                Text("发送范围：${if (preview.text.isNullOrBlank()) "不发送文字" else "当前草稿文字"}；图片 ${preview.imageCount} 张。", color = BodyText)
                preview.text?.let { text ->
                    Text(text.take(500), modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFF5F7F6)).padding(12.dp), style = MaterialTheme.typography.bodySmall)
                }
                Text("隐私：本次由本地 Mock 在设备内生成，不会连接 OpenRouter 或其他真实服务，也不会读取密钥或外发文字、图片。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                Text("费用：本地 Mock 的已验证本地成本为 0；这不代表任何真实服务免费。Token 未估算时会显示为未知。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = consentChecked, onCheckedChange = onConsentChecked)
                    Text("我已确认以上本次内容范围与本地 Mock 语义", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("返回") } },
        confirmButton = { Button(onClick = onConfirm, enabled = consentChecked) { Text("运行本地 Mock") } },
    )
}

@Composable
private fun CandidateReviewDialog(
    candidate: com.nanzhufeng.ai.domain.StoredGeneratedCandidate,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onDiscard: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var title by rememberSaveable(candidate.candidate.id.value) { mutableStateOf(candidate.candidate.title) }
    var body by rememberSaveable(candidate.candidate.id.value) { mutableStateOf(candidate.candidate.body) }
    var saveChecked by rememberSaveable(candidate.candidate.id.value) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(24.dp),
        title = { Text("核对生成候选", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(modifier = Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "${if (candidate.providerId == com.nanzhufeng.ai.domain.ProviderId.OPENROUTER) "OpenRouter（真实服务）" else "本地 Mock（非真实服务）"} · ${candidate.modelId}",
                    color = SecondaryText,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text("这是候选，尚未保存为知识。可编辑后确认保存，或取消候选；两者都不会删除本地草稿和调用记录。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = title, onValueChange = { title = it }, modifier = Modifier.fillMaxWidth(), label = { Text("标题") }, enabled = !isSaving, shape = RoundedCornerShape(14.dp))
                OutlinedTextField(value = body, onValueChange = { body = it }, modifier = Modifier.fillMaxWidth().p5aKeyboardTraversal(), minLines = 5, label = { Text("候选内容") }, enabled = !isSaving, shape = RoundedCornerShape(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = saveChecked, onCheckedChange = { saveChecked = it }, enabled = !isSaving)
                    Text("确认将编辑后的候选保存为本地知识", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        dismissButton = { TextButton(onClick = onDiscard, enabled = !isSaving) { Text("取消候选") } },
        confirmButton = {
            Button(onClick = { onSave(title, body) }, enabled = !isSaving && saveChecked && (title.isNotBlank() || body.isNotBlank())) {
                if (isSaving) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp)) else Text("确认保存")
            }
        },
    )
}

@Composable
private fun StatusCard(state: CaptureScreenState, onDismiss: () -> Unit) {
    val error = state.error
    val notice = state.notice
    if (error == null && notice == null) return
    WhiteCard(modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = if (error == null) Icons.Outlined.CheckCircle else Icons.Outlined.Refresh,
                contentDescription = null,
                tint = if (error == null) BrandGreen else ErrorRed,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(error?.title ?: notice.orEmpty(), fontWeight = FontWeight.SemiBold)
                if (error != null) Text(error.suggestion, color = SecondaryText, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Outlined.Close, contentDescription = "关闭提示")
            }
        }
    }
}

@Composable
internal fun WhiteCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier,
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, NeutralBorder),
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

private fun decodePreview(bytes: ByteArray): ImageBitmap? = runCatching {
    val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val source = ImageDecoder.createSource(ByteBuffer.wrap(bytes))
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val largestSide = max(info.size.width, info.size.height)
            if (largestSide > 2048) {
                val scale = 2048f / largestSide
                decoder.setTargetSize(
                    (info.size.width * scale).toInt().coerceAtLeast(1),
                    (info.size.height * scale).toInt().coerceAtLeast(1),
                )
            }
        }
    } else {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (max(bounds.outWidth / sample, bounds.outHeight / sample) > 2048) sample *= 2
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }
    requireNotNull(bitmap).asImageBitmap()
}.getOrNull()

private fun formatBytes(byteCount: Long?): String = when {
    byteCount == null -> "大小未知"
    byteCount < 1024 -> "$byteCount B"
    byteCount < 1024 * 1024 -> "%.1f KB".format(byteCount / 1024.0)
    else -> "%.1f MB".format(byteCount / (1024.0 * 1024.0))
}

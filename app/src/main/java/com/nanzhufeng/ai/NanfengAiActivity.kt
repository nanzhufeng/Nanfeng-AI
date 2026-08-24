package com.nanzhufeng.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.nanzhufeng.ai.app.AppContainer
import com.nanzhufeng.ai.BuildConfig
import com.nanzhufeng.ai.data.AndroidTextShareIntentGate
import com.nanzhufeng.ai.ui.CaptureViewModel
import com.nanzhufeng.ai.ui.NanfengAiApp
import com.nanzhufeng.ai.ui.ModelSettingsViewModel
import com.nanzhufeng.ai.ui.InvocationLedgerViewModel
import com.nanzhufeng.ai.ui.KnowledgeLibraryViewModel
import com.nanzhufeng.ai.ui.KnowledgeExportViewModel
import com.nanzhufeng.ai.ui.ConversationFoundationViewModel
import com.nanzhufeng.ai.ui.ProjectViewModel
import com.nanzhufeng.ai.ui.MemoryViewModel
import com.nanzhufeng.ai.ui.ContextBodySelectionViewModel
import com.nanzhufeng.ai.ui.MarkdownKnowledgeImportViewModel
import com.nanzhufeng.ai.ui.MarkdownKnowledgeExportViewModel
import com.nanzhufeng.ai.ui.OfflineEvalViewModel
import com.nanzhufeng.ai.ui.JsonKnowledgeImportViewModel
import com.nanzhufeng.ai.ui.JsonKnowledgeExportViewModel
import com.nanzhufeng.ai.ui.ChatGptExportImportViewModel
import com.nanzhufeng.ai.ui.ClaudeExportImportViewModel
import com.nanzhufeng.ai.ui.P6KZipImportViewModel
import com.nanzhufeng.ai.ui.NanfengKnowledgeExportImportViewModel
import com.nanzhufeng.ai.ui.PdfTextImportViewModel
import com.nanzhufeng.ai.ui.WebTextSnapshotViewModel
import com.nanzhufeng.ai.ui.P5ANavigationViewModel
import com.nanzhufeng.ai.ui.P5A_ROUTE_EXTRA
import com.nanzhufeng.ai.ui.P5A_ROUTE_PREFERENCE_KEY
import com.nanzhufeng.ai.ui.P5A_CONVERSATION_DRAWER_PREFERENCE_KEY
import com.nanzhufeng.ai.ui.P5ARoute
import com.nanzhufeng.ai.ui.PrivacyDataViewModel
import com.nanzhufeng.ai.ui.LocalBackupRestoreViewModel
import com.nanzhufeng.ai.ui.ConversationExchangeExportViewModel
import com.nanzhufeng.ai.ui.WorkspaceExchangeV2ExportViewModel
import com.nanzhufeng.ai.ui.WorkspaceExchangeV2RestoreViewModel
import com.nanzhufeng.ai.ui.P7DAccountSyncViewModel
import com.nanzhufeng.ai.ui.DualPathConnectionViewModel
import com.nanzhufeng.ai.ui.P8ControlledAgentViewModel
import com.nanzhufeng.ai.domain.ConversationId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Pinned shortcuts carry only an opaque local conversation ID, never message or provider data. */
const val CONVERSATION_SHORTCUT_ID_EXTRA = "com.nanzhufeng.ai.extra.CONVERSATION_SHORTCUT_ID"

/** Root activity: all user-visible actions remain rooted in the local app container. */
class NanfengAiActivity : ComponentActivity() {
    private lateinit var container: AppContainer
    private lateinit var textShareGate: AndroidTextShareIntentGate
    private lateinit var captureViewModel: CaptureViewModel
    private lateinit var modelSettingsViewModel: ModelSettingsViewModel
    private lateinit var invocationLedgerViewModel: InvocationLedgerViewModel
    private lateinit var knowledgeLibraryViewModel: KnowledgeLibraryViewModel
    private lateinit var knowledgeExportViewModel: KnowledgeExportViewModel
    private lateinit var conversationFoundationViewModel: ConversationFoundationViewModel
    private lateinit var projectViewModel: ProjectViewModel
    private lateinit var memoryViewModel: MemoryViewModel
    private lateinit var contextBodySelectionViewModel: ContextBodySelectionViewModel
    private lateinit var markdownImportViewModel: MarkdownKnowledgeImportViewModel
    private lateinit var markdownExportViewModel: MarkdownKnowledgeExportViewModel
    private lateinit var offlineEvalViewModel: OfflineEvalViewModel
    private lateinit var jsonKnowledgeImportViewModel: JsonKnowledgeImportViewModel
    private lateinit var jsonKnowledgeExportViewModel: JsonKnowledgeExportViewModel
    private lateinit var chatGptExportImportViewModel: ChatGptExportImportViewModel
    private lateinit var claudeExportImportViewModel: ClaudeExportImportViewModel
    private lateinit var p6kZipImportViewModel: P6KZipImportViewModel
    private lateinit var nanfengKnowledgeExportImportViewModel: NanfengKnowledgeExportImportViewModel
    private lateinit var pdfTextImportViewModel: PdfTextImportViewModel
    private lateinit var webTextSnapshotViewModel: WebTextSnapshotViewModel
    private lateinit var navigationViewModel: P5ANavigationViewModel
    private lateinit var privacyDataViewModel: PrivacyDataViewModel
    private lateinit var localBackupRestoreViewModel: LocalBackupRestoreViewModel
    private lateinit var conversationExchangeExportViewModel: ConversationExchangeExportViewModel
    private lateinit var workspaceExchangeV2ExportViewModel: WorkspaceExchangeV2ExportViewModel
    private lateinit var workspaceExchangeV2RestoreViewModel: WorkspaceExchangeV2RestoreViewModel
    private lateinit var accountSyncViewModel: P7DAccountSyncViewModel
    private lateinit var dualPathConnectionViewModel: DualPathConnectionViewModel
    private lateinit var p8ControlledAgentViewModel: P8ControlledAgentViewModel
    private lateinit var routePreferences: android.content.SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        container = AppContainer(applicationContext)
        textShareGate = AndroidTextShareIntentGate(savedInstanceState?.getString(TEXT_SHARE_FINGERPRINT))
        captureViewModel = ViewModelProvider(
            this,
            CaptureViewModel.Factory(
                container.gallerySelectionReader,
                container.captureGalleryImage,
                container.captureTextDraft,
                container.restoreLatestCaptureDraft,
                container.captureDraftRepository,
                container.confirmAiRequest,
                container.runAiTask,
                container.persistGeneratedCandidate,
                container.generatedCandidateRepository,
                container.invocationRepository,
                container.saveCandidateReview,
            ),
        )[CaptureViewModel::class.java]
        modelSettingsViewModel = ViewModelProvider(
            this,
            ModelSettingsViewModel.Factory(
                container.loadModelServiceConfiguration,
                container.saveModelServiceConfiguration,
                container.loadRegistryVerificationStatus,
                container.verifyOpenRouterRegistry,
                container.loadRealServiceAcceptanceStatus,
                container.p2mRealServiceReadiness,
                container.p2mRealServiceExecutor,
                container.directChatCallAudit,
                container.providerDiagnostics,
                container.contextSelectionAudits,
                container.loadChatRoutingPolicy,
                container.saveChatRoutingPolicy,
                container.providerConnectionProbe,
            ),
        )[ModelSettingsViewModel::class.java]
        invocationLedgerViewModel = ViewModelProvider(
            this,
            InvocationLedgerViewModel.Factory(container.invocationRepository),
        )[InvocationLedgerViewModel::class.java]
        knowledgeLibraryViewModel = ViewModelProvider(
            this,
            KnowledgeLibraryViewModel.Factory(container.readKnowledgeLibrary, container.manageKnowledge, container.manageKnowledgeRelationships),
        )[KnowledgeLibraryViewModel::class.java]
        knowledgeExportViewModel = ViewModelProvider(
            this,
            KnowledgeExportViewModel.Factory(container.exportKnowledgePackage),
        )[KnowledgeExportViewModel::class.java]
        conversationFoundationViewModel = ViewModelProvider(
            this,
            ConversationFoundationViewModel.Factory(
                container.conversationRepository,
                container.createConversation,
                container.appendConversationMessage,
                container.editConversationUserMessage,
                container.saveConversationDraft,
                container.submitConversationDraft,
                container.messagePresentationRenderer,
                container.startLocalConversationRuntime,
                container.applyConversationRuntimeEvent,
                container.deterministicFixtureStreamingAdapter,
                container.conversationActionOrchestrator,
                container.switchConversationBranch,
                container.manageConversation,
                container.searchConversations,
                container.searchConversationAttachments,
                container.localSearchHistory,
                container.exportConversationPackage,
                container.gallerySelectionReader,
                container.documentSelectionReader,
                container.addConversationImageAttachment,
                container.removeConversationAttachment,
                container.conversationAttachmentPreviewProjection,
                container.pdfPreviewPositionStore,
                container.videoPreviewPositionStore,
                container.audioPreviewPositionStore,
                container.readConversationAttemptHistory,
                container.temporaryConversationDomain,
                container.addTemporaryConversationAttachment,
                container.clearTemporaryConversation,
                container.p6gModelSelection,
                container.invocationRepository,
                container.assistantResponseModelAttributions,
                container.normalChatOpenRouterExecutor,
            ),
        )[ConversationFoundationViewModel::class.java]
        // P5-B maps only stale persisted work to stable failure. It deliberately cannot resume
        // it.  This runs after the conversation owner exists, then reloads it on the main
        // dispatcher so an Attempt changed to UNKNOWN cannot be missed by the first UI load.
        lifecycleScope.launch {
            val recoveredAttempts = withContext(Dispatchers.IO) {
                container.recoverInterruptedNormalChatAttemptsAfterProcessStart()
            }
            withContext(Dispatchers.IO) {
                container.taskRecoveryAudit.recoverAfterProcessStart()
            }
            if (recoveredAttempts > 0) conversationFoundationViewModel.reload()
        }
        projectViewModel = ViewModelProvider(this, ProjectViewModel.Factory(container.projectRepository, container.manageProject))[ProjectViewModel::class.java]
        memoryViewModel = ViewModelProvider(this, MemoryViewModel.Factory(container.manageMemory))[MemoryViewModel::class.java]
        contextBodySelectionViewModel = ViewModelProvider(this, ContextBodySelectionViewModel.Factory(container.readExplicitContextBody, container.readLocalContextPreview, container.readExplicitLocalActionTrace))[ContextBodySelectionViewModel::class.java]
        markdownImportViewModel = ViewModelProvider(this, MarkdownKnowledgeImportViewModel.Factory(container.manageMarkdownImport))[MarkdownKnowledgeImportViewModel::class.java]
        markdownExportViewModel = ViewModelProvider(this, MarkdownKnowledgeExportViewModel.Factory(container.manageKnowledge, container.exportMarkdownKnowledge))[MarkdownKnowledgeExportViewModel::class.java]
        offlineEvalViewModel = ViewModelProvider(this, OfflineEvalViewModel.Factory(container.runOfflineEval, container.offlineEvalRepository, container.exportOfflineEvalReport))[OfflineEvalViewModel::class.java]
        jsonKnowledgeImportViewModel = ViewModelProvider(this, JsonKnowledgeImportViewModel.Factory(container.manageJsonKnowledgeImport))[JsonKnowledgeImportViewModel::class.java]
        jsonKnowledgeExportViewModel = ViewModelProvider(this, JsonKnowledgeExportViewModel.Factory(container.manageKnowledge, container.exportJsonKnowledge))[JsonKnowledgeExportViewModel::class.java]
        chatGptExportImportViewModel = ViewModelProvider(this, ChatGptExportImportViewModel.Factory(container.manageChatGptExportImport))[ChatGptExportImportViewModel::class.java]
        claudeExportImportViewModel = ViewModelProvider(this, ClaudeExportImportViewModel.Factory(container.manageClaudeExportImport))[ClaudeExportImportViewModel::class.java]
        p6kZipImportViewModel = ViewModelProvider(this, P6KZipImportViewModel.Factory(container.p6kZipIntakeStore))[P6KZipImportViewModel::class.java]
        nanfengKnowledgeExportImportViewModel = ViewModelProvider(this, NanfengKnowledgeExportImportViewModel.Factory(container.manageNanfengKnowledgeExportImport))[NanfengKnowledgeExportImportViewModel::class.java]
        pdfTextImportViewModel = ViewModelProvider(this, PdfTextImportViewModel.Factory(container.managePdfTextKnowledgeImport))[PdfTextImportViewModel::class.java]
        webTextSnapshotViewModel = ViewModelProvider(this, WebTextSnapshotViewModel.Factory(container.manageWebTextSnapshot))[WebTextSnapshotViewModel::class.java]
        privacyDataViewModel = ViewModelProvider(this, PrivacyDataViewModel.Factory(container.privacyDataManager))[PrivacyDataViewModel::class.java]
        localBackupRestoreViewModel = ViewModelProvider(this, LocalBackupRestoreViewModel.Factory(container.localBackupRestoreManager))[LocalBackupRestoreViewModel::class.java]
        conversationExchangeExportViewModel = ViewModelProvider(this, ConversationExchangeExportViewModel.Factory(container.conversationExchangeExportPort))[ConversationExchangeExportViewModel::class.java]
        workspaceExchangeV2ExportViewModel = ViewModelProvider(this, WorkspaceExchangeV2ExportViewModel.Factory(container.workspaceExchangeV2ExportPort))[WorkspaceExchangeV2ExportViewModel::class.java]
        workspaceExchangeV2RestoreViewModel = ViewModelProvider(this, WorkspaceExchangeV2RestoreViewModel.Factory(container.workspaceExchangeV2OpenDocumentRestorePort))[WorkspaceExchangeV2RestoreViewModel::class.java]
        accountSyncViewModel = ViewModelProvider(this)[P7DAccountSyncViewModel::class.java]
        dualPathConnectionViewModel = ViewModelProvider(this, DualPathConnectionViewModel.Factory(container.readConnectionCapability))[DualPathConnectionViewModel::class.java]
        p8ControlledAgentViewModel = ViewModelProvider(this, P8ControlledAgentViewModel.Factory(container.p8CProductionLocalAgent))[P8ControlledAgentViewModel::class.java]
        routePreferences = getSharedPreferences("p5a_ui", MODE_PRIVATE)
        navigationViewModel = ViewModelProvider(this)[P5ANavigationViewModel::class.java]
        restoreP5ARoute(intent)
        setContent {
            NanfengAiApp(
                captureViewModel,
                modelSettingsViewModel,
                invocationLedgerViewModel,
                knowledgeLibraryViewModel,
                knowledgeExportViewModel,
                conversationFoundationViewModel,
                projectViewModel,
                memoryViewModel,
                contextBodySelectionViewModel,
                markdownImportViewModel,
                markdownExportViewModel,
                jsonKnowledgeImportViewModel,
                jsonKnowledgeExportViewModel,
                chatGptExportImportViewModel,
                claudeExportImportViewModel,
                p6kZipImportViewModel,
                nanfengKnowledgeExportImportViewModel,
                pdfTextImportViewModel,
                webTextSnapshotViewModel,
                offlineEvalViewModel,
                privacyDataViewModel,
                localBackupRestoreViewModel,
                conversationExchangeExportViewModel,
                workspaceExchangeV2ExportViewModel,
                workspaceExchangeV2RestoreViewModel,
                accountSyncViewModel,
                dualPathConnectionViewModel,
                p8ControlledAgentViewModel,
                navigationViewModel,
                ::selectP5ARoute,
                ::setConversationDrawerOpen,
                ::exitSettingsToConversationDrawer,
            )
        }
        consumeTextShareIntent(intent)
        consumeConversationShortcutIntent(intent)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        restoreP5ARoute(intent)
        consumeTextShareIntent(intent)
        consumeConversationShortcutIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(TEXT_SHARE_FINGERPRINT, textShareGate.savedFingerprint())
        super.onSaveInstanceState(outState)
    }

    private fun consumeTextShareIntent(intent: android.content.Intent?) {
        textShareGate.consume(container.androidTextShareAdapter.read(intent))?.let(captureViewModel::onTextInput)
    }

    /** Opens the exact local conversation behind a user-approved pinned launcher shortcut. */
    private fun consumeConversationShortcutIntent(intent: android.content.Intent?) {
        val rawId = intent?.getStringExtra(CONVERSATION_SHORTCUT_ID_EXTRA)?.takeIf { it.isNotBlank() } ?: return
        selectP5ARoute(P5ARoute.CONVERSATION)
        conversationFoundationViewModel.openConversationShortcut(ConversationId(rawId))
    }

    private fun restoreP5ARoute(intent: android.content.Intent?) {
        navigationViewModel.restoreFromIntent(
            action = intent?.action,
            routeExtra = intent?.getStringExtra(P5A_ROUTE_EXTRA),
            deepLinkRoute = intent?.data?.lastPathSegment,
            persistedRoute = routePreferences.getString(P5A_ROUTE_PREFERENCE_KEY, null),
            persistedConversationDrawerOpen = routePreferences.getBoolean(P5A_CONVERSATION_DRAWER_PREFERENCE_KEY, false),
        )
        persistP5ANavigationState()
    }

    @Suppress("UseKtx") // Keep this UI-only preference write dependency-free in the single Android module.
    private fun selectP5ARoute(route: P5ARoute) {
        navigationViewModel.select(route)
        persistP5ANavigationState()
    }

    private fun setConversationDrawerOpen(open: Boolean) {
        navigationViewModel.setConversationDrawerOpen(open)
        persistP5ANavigationState()
    }

    private fun exitSettingsToConversationDrawer() {
        navigationViewModel.exitSettingsToConversationDrawer()
        persistP5ANavigationState()
    }

    private fun persistP5ANavigationState() {
        routePreferences.edit()
            .putString(P5A_ROUTE_PREFERENCE_KEY, navigationViewModel.state.route.wireValue)
            .putBoolean(P5A_CONVERSATION_DRAWER_PREFERENCE_KEY, navigationViewModel.state.conversationDrawerOpen)
            .apply()
    }

    private companion object {
        const val TEXT_SHARE_FINGERPRINT = "handled_text_share_fingerprint"
    }
}

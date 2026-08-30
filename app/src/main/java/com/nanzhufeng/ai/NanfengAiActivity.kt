package com.nanzhufeng.ai

import android.os.Bundle
import android.os.SystemClock
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.nanzhufeng.ai.app.AppContainer
import com.nanzhufeng.ai.background.NormalChatGenerationForegroundService
import com.nanzhufeng.ai.BuildConfig
import com.nanzhufeng.ai.data.AndroidTextShareIntentGate
import com.nanzhufeng.ai.data.AndroidScheduledMonitorScheduler
import com.nanzhufeng.ai.ui.CaptureViewModel
import com.nanzhufeng.ai.ui.NanfengAiApp
import com.nanzhufeng.ai.ui.ModelSettingsViewModel
import com.nanzhufeng.ai.ui.GlmOcrWorkspaceViewModel
import com.nanzhufeng.ai.ui.InvocationLedgerViewModel
import com.nanzhufeng.ai.ui.ConversationCostLedgerViewModel
import com.nanzhufeng.ai.ui.KnowledgeLibraryViewModel
import com.nanzhufeng.ai.ui.KnowledgeExportViewModel
import com.nanzhufeng.ai.ui.ConversationFoundationViewModel
import com.nanzhufeng.ai.ui.ProjectViewModel
import com.nanzhufeng.ai.ui.MemoryViewModel
import com.nanzhufeng.ai.ui.ContextBodySelectionViewModel
import com.nanzhufeng.ai.ui.AssistantExperienceSettingsViewModel
import com.nanzhufeng.ai.ui.NotificationReminderSettingsViewModel
import com.nanzhufeng.ai.ui.AppearanceSettingsViewModel
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
import com.nanzhufeng.ai.ui.ScheduledMonitorViewModel
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
    private lateinit var glmOcrWorkspaceViewModel: GlmOcrWorkspaceViewModel
    private lateinit var invocationLedgerViewModel: InvocationLedgerViewModel
    private lateinit var conversationCostLedgerViewModel: ConversationCostLedgerViewModel
    private lateinit var knowledgeLibraryViewModel: KnowledgeLibraryViewModel
    private lateinit var knowledgeExportViewModel: KnowledgeExportViewModel
    private lateinit var conversationFoundationViewModel: ConversationFoundationViewModel
    private lateinit var projectViewModel: ProjectViewModel
    private lateinit var memoryViewModel: MemoryViewModel
    private lateinit var contextBodySelectionViewModel: ContextBodySelectionViewModel
    private lateinit var assistantExperienceSettingsViewModel: AssistantExperienceSettingsViewModel
    private lateinit var notificationReminderSettingsViewModel: NotificationReminderSettingsViewModel
    private lateinit var appearanceSettingsViewModel: AppearanceSettingsViewModel
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
    private lateinit var scheduledMonitorViewModel: ScheduledMonitorViewModel
    private var normalChatExecutionReceiverRegistered = false
    private val normalChatExecutionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != NormalChatGenerationForegroundService.ACTION_EXECUTION_STATE_CHANGED) return
            val conversationId = intent.getStringExtra(NormalChatGenerationForegroundService.EXTRA_CONVERSATION_ID)
                ?.takeIf(String::isNotBlank)
                ?.let(::ConversationId)
                ?: return
            conversationFoundationViewModel.onNormalChatBackgroundExecutionStateChanged(
                conversationId,
                intent.getBooleanExtra(NormalChatGenerationForegroundService.EXTRA_RUNNING, false),
                intent.getStringExtra(NormalChatGenerationForegroundService.EXTRA_SAFE_RESULT),
            )
        }
    }
    private lateinit var routePreferences: android.content.SharedPreferences
    private lateinit var appEntryPreferences: android.content.SharedPreferences
    private var hasStartedOnce = false
    private var startWithFreshChat = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        applyLightSystemBars()
        appEntryPreferences = getSharedPreferences(APP_ENTRY_PREFERENCES, MODE_PRIVATE)
        startWithFreshChat = shouldStartWithFreshChat(intent)
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
        glmOcrWorkspaceViewModel = ViewModelProvider(
            this,
            GlmOcrWorkspaceViewModel.Factory(
                container.glmOcrTaskOwner,
                container.glmOcrScheduler,
                container.documentSelectionReader,
                container.conversationAttachmentPreviewProjection,
                container.pdfPreviewPositionStore,
            ),
        )[GlmOcrWorkspaceViewModel::class.java]
        invocationLedgerViewModel = ViewModelProvider(
            this,
            InvocationLedgerViewModel.Factory(container.invocationRepository, container.glmOcrTaskOwner),
        )[InvocationLedgerViewModel::class.java]
        conversationCostLedgerViewModel = ViewModelProvider(
            this,
            ConversationCostLedgerViewModel.Factory(container.assistantResponseModelAttributions, container.reminderDraftGenerationRecords, container.conversationTitleGenerationRecords, container.scheduledMonitorRepository, container.directChatCallAudit, container.invocationRepository),
        )[ConversationCostLedgerViewModel::class.java]
        knowledgeLibraryViewModel = ViewModelProvider(
            this,
            KnowledgeLibraryViewModel.Factory(container.readKnowledgeLibrary, container.manageKnowledge, container.manageKnowledgeRelationships, container.readHistoryKnowledgeCurationSource, container.qwenHistoryKnowledgeRefiner),
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
                container.glmOcrTaskOwner,
                container.localSearchHistory,
                container.conversationReadMarkerStore,
                container.exportConversationPackage,
                container.gallerySelectionReader,
                container.documentSelectionReader,
                container.addConversationImageAttachment,
                container.removeConversationAttachment,
                container.deletePersistedConversationAttachment,
                container.conversationAttachmentPreviewProjection,
                container.pdfPreviewPositionStore,
                container.videoPreviewPositionStore,
                container.audioPreviewPositionStore,
                container.readConversationAttemptHistory,
                container.temporaryConversationDomain,
                container.addTemporaryConversationAttachment,
                container.clearTemporaryConversation,
                container.p6gModelSelection,
                container.conversationWebSearchOverrides,
                container.loadAssistantExperienceSettings,
                container.invocationRepository,
                container.assistantResponseModelAttributions,
                container.contextSelectionAudits,
                container.normalChatOpenRouterExecutor,
                container.normalChatBackgroundExecution,
                startWithFreshChat,
            ),
        )[ConversationFoundationViewModel::class.java]
        // Normal generation recovery belongs to GenerationForegroundService.  This Activity
        // observes the persisted conversation state only; it never changes an in-flight task.
        lifecycleScope.launch { withContext(Dispatchers.IO) { container.taskRecoveryAudit.recoverAfterProcessStart() } }
        projectViewModel = ViewModelProvider(this, ProjectViewModel.Factory(container.projectRepository, container.manageProject))[ProjectViewModel::class.java]
        memoryViewModel = ViewModelProvider(this, MemoryViewModel.Factory(container.manageMemory))[MemoryViewModel::class.java]
        lifecycleScope.launch {
            val seeded = withContext(Dispatchers.IO) { container.ensureInitialMemorySummary() }
            if (seeded > 0) memoryViewModel.reload("已加入你的记忆摘要初始版本。")
        }
        contextBodySelectionViewModel = ViewModelProvider(this, ContextBodySelectionViewModel.Factory(container.readExplicitContextBody, container.readLocalContextPreview, container.readExplicitLocalActionTrace))[ContextBodySelectionViewModel::class.java]
        assistantExperienceSettingsViewModel = ViewModelProvider(
            this,
            AssistantExperienceSettingsViewModel.Factory(
                container.loadAssistantExperienceSettings,
                container.saveAssistantExperienceSettings,
                container.historyKnowledgeAutoCurationScheduler::onSettingChanged,
            ),
        )[AssistantExperienceSettingsViewModel::class.java]
        notificationReminderSettingsViewModel = ViewModelProvider(
            this,
            NotificationReminderSettingsViewModel.Factory(
                container.loadNotificationReminderSettings,
                container.saveNotificationReminderSettings,
            ),
        )[NotificationReminderSettingsViewModel::class.java]
        appearanceSettingsViewModel = ViewModelProvider(
            this,
            AppearanceSettingsViewModel.Factory(
                container.loadAppearanceSettings,
                container.saveAppearanceSettings,
            ),
        )[AppearanceSettingsViewModel::class.java]
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
        accountSyncViewModel = ViewModelProvider(
            this,
            P7DAccountSyncViewModel.Factory(
                container.p7fGoogleAccountOwner,
                container.p7fManualConversationSyncOwner,
                container.p7fSelectedConversationSyncScheduler,
            ),
        )[P7DAccountSyncViewModel::class.java]
        dualPathConnectionViewModel = ViewModelProvider(this, DualPathConnectionViewModel.Factory(container.readConnectionCapability))[DualPathConnectionViewModel::class.java]
        p8ControlledAgentViewModel = ViewModelProvider(this, P8ControlledAgentViewModel.Factory(container.p8CProductionLocalAgent))[P8ControlledAgentViewModel::class.java]
        scheduledMonitorViewModel = ViewModelProvider(
            this,
            ScheduledMonitorViewModel.Factory(
                container.scheduledMonitorRepository,
                AndroidScheduledMonitorScheduler(applicationContext),
                container.qwenReminderDraftRefiner,
                java.time.Clock.systemUTC(),
            ),
        )[ScheduledMonitorViewModel::class.java]
        routePreferences = getSharedPreferences("p5a_ui", MODE_PRIVATE)
        navigationViewModel = ViewModelProvider(this)[P5ANavigationViewModel::class.java]
        restoreP5ARoute(intent)
        if (startWithFreshChat) selectP5ARoute(P5ARoute.CONVERSATION)
        setContent {
            NanfengAiApp(
                captureViewModel,
                modelSettingsViewModel,
                glmOcrWorkspaceViewModel,
                invocationLedgerViewModel,
                conversationCostLedgerViewModel,
                knowledgeLibraryViewModel,
                knowledgeExportViewModel,
                conversationFoundationViewModel,
                projectViewModel,
                memoryViewModel,
                contextBodySelectionViewModel,
                assistantExperienceSettingsViewModel,
                notificationReminderSettingsViewModel,
                appearanceSettingsViewModel,
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
                scheduledMonitorViewModel,
                navigationViewModel,
                ::selectP5ARoute,
                ::setConversationDrawerOpen,
                ::exitSettingsToConversationDrawer,
            )
        }
        consumeTextShareIntent(intent)
        consumeConversationShortcutIntent(intent)
        consumeScheduledMonitorIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this,
            normalChatExecutionReceiver,
            IntentFilter(NormalChatGenerationForegroundService.ACTION_EXECUTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        normalChatExecutionReceiverRegistered = true
        if (hasStartedOnce && conversationFoundationViewModel.onAppForeground(SystemClock.elapsedRealtime())) {
            selectP5ARoute(P5ARoute.CONVERSATION)
        }
        hasStartedOnce = true
    }

    override fun onStop() {
        if (normalChatExecutionReceiverRegistered) {
            unregisterReceiver(normalChatExecutionReceiver)
            normalChatExecutionReceiverRegistered = false
        }
        conversationFoundationViewModel.onAppBackground(SystemClock.elapsedRealtime())
        appEntryPreferences.edit().putLong(APP_ENTRY_LAST_BACKGROUND_AT_MILLIS, System.currentTimeMillis()).apply()
        super.onStop()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        restoreP5ARoute(intent)
        consumeTextShareIntent(intent)
        consumeConversationShortcutIntent(intent)
        consumeScheduledMonitorIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(TEXT_SHARE_FINGERPRINT, textShareGate.savedFingerprint())
        super.onSaveInstanceState(outState)
    }

    private fun consumeTextShareIntent(intent: android.content.Intent?) {
        textShareGate.consume(container.androidTextShareAdapter.read(intent))?.let(captureViewModel::onTextInput)
    }

    private fun shouldStartWithFreshChat(intent: android.content.Intent?): Boolean {
        if (isExplicitAppLaunch(intent)) return false
        val lastBackgroundAt = appEntryPreferences.getLong(APP_ENTRY_LAST_BACKGROUND_AT_MILLIS, 0L)
        val elapsed = System.currentTimeMillis() - lastBackgroundAt
        return lastBackgroundAt <= 0L || elapsed !in 0 until FRESH_CHAT_AFTER_BACKGROUND_MS
    }

    private fun isExplicitAppLaunch(intent: android.content.Intent?): Boolean = when {
        intent == null -> false
        !intent.getStringExtra(CONVERSATION_SHORTCUT_ID_EXTRA).isNullOrBlank() -> true
        !intent.getStringExtra(P5A_ROUTE_EXTRA).isNullOrBlank() -> true
        intent.data != null -> true
        intent.action == Intent.ACTION_SEND || intent.action == Intent.ACTION_SEND_MULTIPLE -> true
        intent.action == ACTION_OPEN_SCHEDULED_MONITOR -> true
        else -> false
    }

    private fun consumeScheduledMonitorIntent(intent: Intent?) {
        if (intent?.action != ACTION_OPEN_SCHEDULED_MONITOR) return
        scheduledMonitorViewModel.open(intent.getStringExtra(EXTRA_SCHEDULED_MONITOR_TASK_ID)?.let { com.nanzhufeng.ai.domain.ScheduledMonitorTaskId(it) })
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

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Appearance is owned by the Compose skin. Reapplying a light-only system-bar mode here
        // would turn the status and navigation glyphs dark again after returning to a deep skin.
        // The initial light request in onCreate remains a safe fallback until Compose is drawn.
    }

    private fun applyLightSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
    }

    companion object {
        const val TEXT_SHARE_FINGERPRINT = "handled_text_share_fingerprint"
        const val APP_ENTRY_PREFERENCES = "conversation_app_entry"
        const val APP_ENTRY_LAST_BACKGROUND_AT_MILLIS = "last_background_at_millis"
        const val FRESH_CHAT_AFTER_BACKGROUND_MS = 15L * 60L * 1_000L
        const val ACTION_OPEN_SCHEDULED_MONITOR = "com.nanzhufeng.ai.action.OPEN_SCHEDULED_MONITOR"
        const val EXTRA_SCHEDULED_MONITOR_TASK_ID = "com.nanzhufeng.ai.extra.SCHEDULED_MONITOR_TASK_ID"
    }
}

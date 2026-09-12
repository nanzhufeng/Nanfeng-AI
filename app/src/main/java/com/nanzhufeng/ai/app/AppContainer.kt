package com.nanzhufeng.ai.app

import android.content.Context
import androidx.room.Room
import com.nanzhufeng.ai.BuildConfig
import com.nanzhufeng.ai.ai.MockAiTaskRunner
import com.nanzhufeng.ai.ai.OpenRouterOfflineAdapterContract
import com.nanzhufeng.ai.ai.OfficialOpenRouterInferenceTransport
import com.nanzhufeng.ai.ai.NormalChatOpenRouterExecutor
import com.nanzhufeng.ai.ai.ScheduledMonitorExecutor
import com.nanzhufeng.ai.ai.QwenReminderDraftRefiner
import com.nanzhufeng.ai.ai.ConfiguredConversationTitleRefiner
import com.nanzhufeng.ai.background.AndroidNormalChatBackgroundExecution
import com.nanzhufeng.ai.ai.ChatProviderAdapters
import com.nanzhufeng.ai.domain.UnifiedModelResolver
import com.nanzhufeng.ai.domain.toConversationReference
import com.nanzhufeng.ai.ai.OfficialProviderChatTransport
import com.nanzhufeng.ai.ai.QwenHistoryKnowledgeRefiner
import com.nanzhufeng.ai.ai.ProviderConnectionProbe
import com.nanzhufeng.ai.data.AndroidDirectChatCallAuditStore
import com.nanzhufeng.ai.data.local.RoomProviderDiagnosticStore
import com.nanzhufeng.ai.data.local.RoomNormalChatSendAttemptStore
import com.nanzhufeng.ai.data.local.RoomScheduledMonitorRepository
import com.nanzhufeng.ai.data.local.RoomReminderDraftGenerationRecordStore
import com.nanzhufeng.ai.data.local.RoomConversationTitleGenerationRecordStore
import com.nanzhufeng.ai.data.local.RoomAssistantResponseModelAttributionStore
import com.nanzhufeng.ai.ai.OpenRouterEgressPolicy
import com.nanzhufeng.ai.ai.OpenRouterInferenceAdapter
import com.nanzhufeng.ai.ai.LoadRealServiceAcceptanceUiStatusUseCase
import com.nanzhufeng.ai.ai.FileRealServiceAcceptanceTokenStore
import com.nanzhufeng.ai.ai.P2MRealServiceExecutor
import com.nanzhufeng.ai.ai.P2MRealServiceReadinessUseCase
import com.nanzhufeng.ai.ai.P2MRealTextExecutionBridge
import com.nanzhufeng.ai.ai.P2MTranscriptOwner
import com.nanzhufeng.ai.ai.RealServiceAcceptanceTokenService
import com.nanzhufeng.ai.data.AndroidPrivateAttachmentStore
import com.nanzhufeng.ai.data.AndroidPdfPreviewPositionStore
import com.nanzhufeng.ai.data.AndroidVideoPreviewPositionStore
import com.nanzhufeng.ai.data.AndroidAudioPreviewPositionStore
import com.nanzhufeng.ai.data.AndroidKnowledgeExportStore
import com.nanzhufeng.ai.data.AndroidMarkdownPrivateAssetStore
import com.nanzhufeng.ai.data.AndroidMarkdownKnowledgeExportStore
import com.nanzhufeng.ai.data.AndroidJsonKnowledgePrivateAssetStore
import com.nanzhufeng.ai.data.AndroidChatGptExportPrivateAssetStore
import com.nanzhufeng.ai.data.AndroidClaudeExportPrivateAssetStore
import com.nanzhufeng.ai.data.AndroidNanfengKnowledgeExportPrivateAssetStore
import com.nanzhufeng.ai.data.AndroidP6KZipIntakeStore
import com.nanzhufeng.ai.data.AndroidP6KZipAssetRecoveryScheduler
import com.nanzhufeng.ai.data.AndroidPdfTextKnowledgePrivateAssetStore
import com.nanzhufeng.ai.data.AndroidPublicWebFetcher
import com.nanzhufeng.ai.data.AndroidWebTextSnapshotPrivateAssetStore
import com.nanzhufeng.ai.data.AndroidJsonKnowledgeExportStore
import com.nanzhufeng.ai.data.AndroidOfflineEvalReportStore
import com.nanzhufeng.ai.data.AndroidConversationExportStore
import com.nanzhufeng.ai.data.AndroidConversationReadMarkerStore
import com.nanzhufeng.ai.data.AndroidConversationExchangeExportPort
import com.nanzhufeng.ai.data.AndroidWorkspaceExchangeV2ExportPort
import com.nanzhufeng.ai.data.AndroidWorkspaceExchangeV2AtomicRestoreStore
import com.nanzhufeng.ai.data.AndroidWorkspaceExchangeV2OpenDocumentRestorePort
import com.nanzhufeng.ai.data.P6V2JournalInterruptAcceptance
import com.nanzhufeng.ai.data.P6V2Schema38UpgradeAcceptance
import com.nanzhufeng.ai.data.AndroidTextShareAdapter
import com.nanzhufeng.ai.data.AndroidP7BAccountVault
import com.nanzhufeng.ai.data.AndroidP7ERestoreReceiptStore
import com.nanzhufeng.ai.data.AndroidP7ESemanticAtomicRestoreWriter
import com.nanzhufeng.ai.data.AndroidP7ESemanticSnapshotSourceAdapter
import com.nanzhufeng.ai.data.RoomP7BMetadataStore
import com.nanzhufeng.ai.data.RoomP7DStateStore
import com.nanzhufeng.ai.data.RoomAgentLedger
import com.nanzhufeng.ai.data.AndroidPrivacyDataManager
import com.nanzhufeng.ai.data.AndroidLocalBackupRestoreManager
import com.nanzhufeng.ai.data.AndroidModelServiceSettingsRepository
import com.nanzhufeng.ai.data.AndroidChatRoutingPolicyRepository
import com.nanzhufeng.ai.data.AndroidAssistantExperienceSettingsRepository
import com.nanzhufeng.ai.data.AndroidHistoryKnowledgeCurationCheckpointStore
import com.nanzhufeng.ai.data.AndroidHistoryKnowledgeAutoCurationScheduler
import com.nanzhufeng.ai.data.AndroidHistoryKnowledgeAutoCurationRunStore
import com.nanzhufeng.ai.data.AndroidNotificationReminderSettingsRepository
import com.nanzhufeng.ai.data.AndroidAppearanceSettingsRepository
import com.nanzhufeng.ai.data.AndroidModelRegistrySnapshotStore
import com.nanzhufeng.ai.data.AndroidModelProfileDirectory
import com.nanzhufeng.ai.data.AndroidModelHealthStore
import com.nanzhufeng.ai.data.AndroidProviderModelListClient
import com.nanzhufeng.ai.data.AndroidContextSelectionAuditStore
import com.nanzhufeng.ai.data.AndroidP6GModelSelectionStore
import com.nanzhufeng.ai.data.AndroidConversationWebSearchOverrideStore
import com.nanzhufeng.ai.data.AndroidConversationStyleOverrideStore
import com.nanzhufeng.ai.data.local.RoomCompareBranchExecutionPorts
import com.nanzhufeng.ai.data.local.RoomCompareConversationSessionStore
import com.nanzhufeng.ai.data.AndroidOpenRouterRegistryCatalogClient
import com.nanzhufeng.ai.data.P7CAndroidCloudGateway
import com.nanzhufeng.ai.data.createAndroidProviderCredentialStore
import com.nanzhufeng.ai.data.AndroidGallerySelectionReader
import com.nanzhufeng.ai.data.AndroidDocumentSelectionReader
import com.nanzhufeng.ai.data.local.NanfengAiDatabase
import com.nanzhufeng.ai.data.local.RoomP6KZipImportTaskRepository
import com.nanzhufeng.ai.data.local.RoomP6KZipAssetRecoveryJobRepository
import com.nanzhufeng.ai.data.local.RoomP6KZipImportCommitStore
import com.nanzhufeng.ai.data.local.RoomP6KZipManualAssetLinkOwner
import com.nanzhufeng.ai.data.local.RoomP6KZipMappedAssetLinkOwner
import com.nanzhufeng.ai.data.local.RoomP6KImportIdentityLedger
import com.nanzhufeng.ai.data.local.AndroidP6KImportIdentityEncoder
import com.nanzhufeng.ai.data.local.RoomP6KProfilePersonalizationSettingsOwner
import com.nanzhufeng.ai.data.local.RoomCaptureDraftRepository
import com.nanzhufeng.ai.data.local.RoomKnowledgeRepository
import com.nanzhufeng.ai.data.local.RoomInvocationRepository
import com.nanzhufeng.ai.data.local.RoomGeneratedCandidateRepository
import com.nanzhufeng.ai.data.local.RoomConversationRepository
import com.nanzhufeng.ai.data.local.AndroidLocalSearchHistoryStore
import com.nanzhufeng.ai.data.local.RoomPrivateAttachmentRepository
import com.nanzhufeng.ai.data.local.RoomTemporaryConversationRecoveryStore
import com.nanzhufeng.ai.data.local.RoomProjectRepository
import com.nanzhufeng.ai.data.local.RoomMemoryRepository
import com.nanzhufeng.ai.data.local.RoomLocalContextIndex
import com.nanzhufeng.ai.data.local.RoomKnowledgeRelationshipRepository
import com.nanzhufeng.ai.data.local.RoomMarkdownImportTaskRepository
import com.nanzhufeng.ai.data.local.RoomOfflineEvalRepository
import com.nanzhufeng.ai.data.local.RoomJsonKnowledgeTaskRepository
import com.nanzhufeng.ai.data.local.RoomChatGptImportTaskRepository
import com.nanzhufeng.ai.data.local.RoomChatGptImportCommitStore
import com.nanzhufeng.ai.data.local.RoomClaudeImportTaskRepository
import com.nanzhufeng.ai.data.local.RoomClaudeImportCommitStore
import com.nanzhufeng.ai.data.local.RoomNanfengKnowledgeImportTaskRepository
import com.nanzhufeng.ai.data.local.RoomNanfengKnowledgeImportCommitStore
import com.nanzhufeng.ai.data.local.RoomPdfTextImportTaskRepository
import com.nanzhufeng.ai.data.local.RoomWebTextSnapshotTaskRepository
import com.nanzhufeng.ai.domain.CaptureDraftFactory
import com.nanzhufeng.ai.domain.InitialMemorySummary
import com.nanzhufeng.ai.domain.CaptureGalleryImageUseCase
import com.nanzhufeng.ai.domain.CaptureTextDraftUseCase
import com.nanzhufeng.ai.domain.ExportKnowledgeSnapshotUseCase
import com.nanzhufeng.ai.domain.ExportKnowledgePackageUseCase
import com.nanzhufeng.ai.domain.InMemoryVersionedModelRegistry
import com.nanzhufeng.ai.domain.RunAiTaskUseCase
import com.nanzhufeng.ai.domain.RestoreLatestCaptureDraftUseCase
import com.nanzhufeng.ai.domain.LoadModelServiceConfigurationUseCase
import com.nanzhufeng.ai.domain.LoadChatRoutingPolicyUseCase
import com.nanzhufeng.ai.domain.SaveChatRoutingPolicyUseCase
import com.nanzhufeng.ai.domain.LoadAssistantExperienceSettingsUseCase
import com.nanzhufeng.ai.domain.SaveAssistantExperienceSettingsUseCase
import com.nanzhufeng.ai.domain.LocalContextBroker
import com.nanzhufeng.ai.domain.ReadConnectionCapabilityUseCase
import com.nanzhufeng.ai.domain.SaveModelServiceConfigurationUseCase
import com.nanzhufeng.ai.domain.SaveKnowledgeItemUseCase
import com.nanzhufeng.ai.domain.ConfirmAiRequest
import com.nanzhufeng.ai.domain.RealTextExecutionCoordinator
import com.nanzhufeng.ai.domain.DirectExecutionApplicationOwner
import com.nanzhufeng.ai.domain.DirectExecutionProductionComposition
import com.nanzhufeng.ai.domain.RealTextExecutionPreflightOrchestrator
import com.nanzhufeng.ai.domain.PersistGeneratedCandidateUseCase
import com.nanzhufeng.ai.domain.SaveCandidateReviewUseCase
import com.nanzhufeng.ai.domain.ReadKnowledgeLibraryUseCase
import com.nanzhufeng.ai.domain.VersionedModelRegistry
import com.nanzhufeng.ai.domain.LoadRegistryVerificationStatusUseCase
import com.nanzhufeng.ai.domain.OpenRouterRegistrySnapshotVerifier
import com.nanzhufeng.ai.domain.P6GModelRouter
import com.nanzhufeng.ai.domain.P6GModelSelectionOwner
import com.nanzhufeng.ai.domain.ConversationWebSearchOverrideOwner
import com.nanzhufeng.ai.domain.ConversationStyleOverrideOwner
import com.nanzhufeng.ai.domain.ConversationTreeService
import com.nanzhufeng.ai.domain.ConversationManagementDomain
import com.nanzhufeng.ai.domain.ConversationSearchProjection
import com.nanzhufeng.ai.domain.ManageConversationUseCase
import com.nanzhufeng.ai.domain.SearchConversationsUseCase
import com.nanzhufeng.ai.domain.SearchConversationAttachmentsUseCase
import com.nanzhufeng.ai.domain.ExportConversationPackageUseCase
import com.nanzhufeng.ai.domain.CreateConversationUseCase
import com.nanzhufeng.ai.domain.AppendConversationMessageUseCase
import com.nanzhufeng.ai.domain.EditConversationUserMessageUseCase
import com.nanzhufeng.ai.domain.SwitchConversationBranchUseCase
import com.nanzhufeng.ai.domain.SaveConversationDraftUseCase
import com.nanzhufeng.ai.domain.SubmitConversationDraftUseCase
import com.nanzhufeng.ai.domain.MessagePresentationRenderer
import com.nanzhufeng.ai.domain.ApplyConversationRuntimeEventUseCase
import com.nanzhufeng.ai.domain.ConversationRuntimeStateMachine
import com.nanzhufeng.ai.domain.DeterministicFixtureStreamingAdapter
import com.nanzhufeng.ai.domain.StartLocalConversationRuntimeUseCase
import com.nanzhufeng.ai.domain.StartProviderRuntimeForExistingUserUseCase
import com.nanzhufeng.ai.domain.SubmitConversationDraftAndStartProviderRuntimeUseCase
import com.nanzhufeng.ai.domain.CompareConversationSessionOwner
import com.nanzhufeng.ai.domain.CompareExecutionApplicationOwner
import com.nanzhufeng.ai.domain.MultiModelOrchestrator
import com.nanzhufeng.ai.domain.OpenRouterVerifiedMultiProviderRegistryProjection
import com.nanzhufeng.ai.domain.ConversationActionOrchestrator
import com.nanzhufeng.ai.domain.P3CLocalFixtureRegistry
import com.nanzhufeng.ai.domain.AddConversationImageAttachmentUseCase
import com.nanzhufeng.ai.domain.RemoveConversationAttachmentUseCase
import com.nanzhufeng.ai.domain.DeletePersistedConversationAttachmentUseCase
import com.nanzhufeng.ai.domain.ConversationAttachmentPreviewProjection
import com.nanzhufeng.ai.domain.ReadConversationAttemptHistoryUseCase
import com.nanzhufeng.ai.domain.P6ETemporaryMaintenanceAcceptanceHarness
import com.nanzhufeng.ai.domain.VerifyOpenRouterRegistryUseCase
import com.nanzhufeng.ai.domain.RefreshModelProfilesUseCase
import com.nanzhufeng.ai.domain.ProjectDomain
import com.nanzhufeng.ai.domain.ManageProjectUseCase
import com.nanzhufeng.ai.domain.ReadContextSelectionUseCase
import com.nanzhufeng.ai.domain.ReadExplicitContextBodyUseCase
import com.nanzhufeng.ai.domain.ReadLocalContextCompressionUseCase
import com.nanzhufeng.ai.domain.ReadLocalContextPreviewUseCase
import com.nanzhufeng.ai.domain.ReadExplicitLocalActionTraceUseCase
import com.nanzhufeng.ai.domain.MemoryDomain
import com.nanzhufeng.ai.domain.ManageMemoryUseCase
import com.nanzhufeng.ai.domain.MemoryId
import com.nanzhufeng.ai.domain.MemoryIntent
import com.nanzhufeng.ai.domain.MemoryIntentAction
import com.nanzhufeng.ai.domain.MemoryIntentId
import com.nanzhufeng.ai.domain.MemoryScope
import com.nanzhufeng.ai.domain.MemoryScopeKind
import com.nanzhufeng.ai.domain.MemorySource
import com.nanzhufeng.ai.domain.KnowledgeDomain
import com.nanzhufeng.ai.domain.ManageKnowledgeUseCase
import com.nanzhufeng.ai.domain.ChatGptExportJsonAdapter
import com.nanzhufeng.ai.domain.ManageChatGptExportImportUseCase
import com.nanzhufeng.ai.domain.ClaudeExportJsonAdapter
import com.nanzhufeng.ai.domain.ManageClaudeExportImportUseCase
import com.nanzhufeng.ai.domain.NanfengKnowledgeExportJsonAdapter
import com.nanzhufeng.ai.domain.ManageNanfengKnowledgeExportImportUseCase
import com.nanzhufeng.ai.domain.KnowledgeRelationshipDomain
import com.nanzhufeng.ai.domain.ManageKnowledgeRelationshipsUseCase
import com.nanzhufeng.ai.domain.MarkdownKnowledgeAdapter
import com.nanzhufeng.ai.domain.ManageMarkdownImportUseCase
import com.nanzhufeng.ai.domain.ExportMarkdownKnowledgeUseCase
import com.nanzhufeng.ai.domain.ExportOfflineEvalReportUseCase
import com.nanzhufeng.ai.domain.JsonKnowledgeAdapter
import com.nanzhufeng.ai.domain.ManageJsonKnowledgeImportUseCase
import com.nanzhufeng.ai.domain.ExportJsonKnowledgeUseCase
import com.nanzhufeng.ai.domain.ExportConversationExchangeUseCase
import com.nanzhufeng.ai.domain.NfaiExchangeV2PackageWriter
import com.nanzhufeng.ai.domain.NfaiExchangeV2OwnerMapper
import com.nanzhufeng.ai.domain.RepositoryNfaiExchangeWorkspaceSource
import com.nanzhufeng.ai.domain.WorkspaceExchangeV2ScopePlanner
import com.nanzhufeng.ai.domain.WorkspaceExchangeV2AtomicRestoreOwner
import com.nanzhufeng.ai.domain.PdfTextKnowledgeAdapter
import com.nanzhufeng.ai.domain.ManagePdfTextKnowledgeImportUseCase
import com.nanzhufeng.ai.domain.ManageWebTextSnapshotUseCase
import com.nanzhufeng.ai.domain.RunOfflineEvalUseCase
import com.nanzhufeng.ai.domain.TaskRecoveryAudit
import com.nanzhufeng.ai.domain.LoadNotificationReminderSettingsUseCase
import com.nanzhufeng.ai.domain.SaveNotificationReminderSettingsUseCase
import com.nanzhufeng.ai.domain.LoadAppearanceSettingsUseCase
import com.nanzhufeng.ai.domain.SaveAppearanceSettingsUseCase
import com.nanzhufeng.ai.domain.P7BAccountStateMachine
import com.nanzhufeng.ai.data.P7FGoogleAccountOwner
import com.nanzhufeng.ai.data.P7FManualConversationSyncOwner
import com.nanzhufeng.ai.data.P7FSelectedConversationSyncScheduler
import com.nanzhufeng.ai.data.AndroidDataStorageLocationOwner
import com.nanzhufeng.ai.data.AndroidDataStorageLocationManager
import com.nanzhufeng.ai.domain.P7EGuardedRestoreOwner
import com.nanzhufeng.ai.domain.P7ERestorePlanCoordinator
import com.nanzhufeng.ai.domain.P8BProductionReadOnlyAgentLedgerStatus
import com.nanzhufeng.ai.domain.P8CProductionLocalAgentController
import java.time.Clock
import java.io.File
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class AppContainer(baseContext: Context, private val clock: Clock = Clock.systemUTC()) {
    private val dataStorageLocationOwner = AndroidDataStorageLocationOwner(baseContext.applicationContext)
    // Android framework and third-party initializers must always receive the real application
    // context.  The movable wrapper is deliberately limited to Room's database boundary.
    // Passing it through the whole container made normal app bootstrap depend on a custom
    // Context implementation and caused launch crashes on real devices.
    private val context: Context = baseContext.applicationContext
    private val databaseContext: Context = dataStorageLocationOwner.storageContext()
    init { PDFBoxResourceLoader.init(context.applicationContext) }
    // The established direct-provider path keeps its Android request owner.  It does not create
    // a gateway task or select a second execution mode.
    val normalChatBackgroundExecution = AndroidNormalChatBackgroundExecution(context.applicationContext)
    val captureDraftFactory = CaptureDraftFactory(clock)
    private val mockAiTaskRunner = MockAiTaskRunner(clock)
    private val database = Room.databaseBuilder(
        databaseContext,
        NanfengAiDatabase::class.java,
        "nanfeng-ai.db",
    ).addMigrations(
        NanfengAiDatabase.MIGRATION_1_2,
        NanfengAiDatabase.MIGRATION_2_3,
        NanfengAiDatabase.MIGRATION_3_4,
        NanfengAiDatabase.MIGRATION_4_5,
        NanfengAiDatabase.MIGRATION_5_6,
        NanfengAiDatabase.MIGRATION_6_7,
        NanfengAiDatabase.MIGRATION_7_8,
        NanfengAiDatabase.MIGRATION_8_9,
        NanfengAiDatabase.MIGRATION_9_10,
        NanfengAiDatabase.MIGRATION_10_11,
        NanfengAiDatabase.MIGRATION_11_12,
        NanfengAiDatabase.MIGRATION_12_13,
        NanfengAiDatabase.MIGRATION_13_14,
        NanfengAiDatabase.MIGRATION_14_15,
        NanfengAiDatabase.MIGRATION_15_16,
        NanfengAiDatabase.MIGRATION_16_17,
        NanfengAiDatabase.MIGRATION_17_18,
        NanfengAiDatabase.MIGRATION_18_19,
        NanfengAiDatabase.MIGRATION_19_20,
        NanfengAiDatabase.MIGRATION_20_21,
        NanfengAiDatabase.MIGRATION_21_22,
        NanfengAiDatabase.MIGRATION_22_23,
        NanfengAiDatabase.MIGRATION_23_24,
        NanfengAiDatabase.MIGRATION_24_25,
        NanfengAiDatabase.MIGRATION_25_26,
        NanfengAiDatabase.MIGRATION_26_27,
        NanfengAiDatabase.MIGRATION_27_28,
        NanfengAiDatabase.MIGRATION_28_29,
        NanfengAiDatabase.MIGRATION_29_30,
        NanfengAiDatabase.MIGRATION_30_31,
        NanfengAiDatabase.MIGRATION_31_32,
        NanfengAiDatabase.MIGRATION_32_33,
        NanfengAiDatabase.MIGRATION_33_34,
        NanfengAiDatabase.MIGRATION_34_35,
        NanfengAiDatabase.MIGRATION_35_36,
        NanfengAiDatabase.MIGRATION_36_37,
        NanfengAiDatabase.MIGRATION_37_38,
        NanfengAiDatabase.MIGRATION_38_39,
        NanfengAiDatabase.MIGRATION_39_40,
        NanfengAiDatabase.MIGRATION_40_41,
        NanfengAiDatabase.MIGRATION_41_42,
        NanfengAiDatabase.MIGRATION_42_43,
        NanfengAiDatabase.MIGRATION_43_44,
        NanfengAiDatabase.MIGRATION_44_45,
        NanfengAiDatabase.MIGRATION_45_46,
        NanfengAiDatabase.MIGRATION_46_47,
        NanfengAiDatabase.MIGRATION_47_48,
        NanfengAiDatabase.MIGRATION_48_49,
        NanfengAiDatabase.MIGRATION_49_50,
        NanfengAiDatabase.MIGRATION_50_51,
        NanfengAiDatabase.MIGRATION_51_52,
        NanfengAiDatabase.MIGRATION_52_53,
        NanfengAiDatabase.MIGRATION_53_54,
        NanfengAiDatabase.MIGRATION_54_55,
        NanfengAiDatabase.MIGRATION_55_56,
        NanfengAiDatabase.MIGRATION_56_57,
        NanfengAiDatabase.MIGRATION_57_58,
        NanfengAiDatabase.MIGRATION_58_59,
        NanfengAiDatabase.MIGRATION_59_60,
        NanfengAiDatabase.MIGRATION_60_61,
        NanfengAiDatabase.MIGRATION_61_62,
        NanfengAiDatabase.MIGRATION_62_63,
        NanfengAiDatabase.MIGRATION_63_64,
        NanfengAiDatabase.MIGRATION_64_65,
        NanfengAiDatabase.MIGRATION_65_66,
    ).build()
    val dataStorageLocationManager = AndroidDataStorageLocationManager(dataStorageLocationOwner) { database.close() }
    val captureDraftRepository = RoomCaptureDraftRepository(database)
    val privateAttachmentStore = AndroidPrivateAttachmentStore(context)
    val privateAttachmentRepository = RoomPrivateAttachmentRepository(database)
    /** Module-internal so the separately installed, offline acceptance source set can seed typed states. */
    internal val glmOcrTasks = com.nanzhufeng.ai.data.local.RoomGlmOcrTaskRepository(database)
    val glmOcrScheduler = com.nanzhufeng.ai.data.AndroidGlmOcrScheduler(context)
    private val temporaryConversationRecoveryStore = RoomTemporaryConversationRecoveryStore(database)
    val temporaryConversationDomain = com.nanzhufeng.ai.domain.TemporaryConversationDomain(temporaryConversationRecoveryStore, clock)
    val addTemporaryConversationAttachment = com.nanzhufeng.ai.domain.AddTemporaryConversationAttachmentUseCase(temporaryConversationDomain, privateAttachmentStore, privateAttachmentRepository)
    val clearTemporaryConversation = com.nanzhufeng.ai.domain.ClearTemporaryConversationUseCase(temporaryConversationDomain, privateAttachmentRepository, privateAttachmentStore)
    /** Only the separately installed P6-E acceptance build presents this fixed, no-input harness. */
    val p6eTemporaryMaintenanceAcceptance = P6ETemporaryMaintenanceAcceptanceHarness(
        temporaryConversationRecoveryStore,
        privateAttachmentStore,
        privateAttachmentRepository,
    )
    val gallerySelectionReader = AndroidGallerySelectionReader(context)
    val documentSelectionReader = AndroidDocumentSelectionReader(context)
    val androidTextShareAdapter = AndroidTextShareAdapter()
    private val knowledgeRepository = RoomKnowledgeRepository(database, clock)
    private val knowledgeRelationshipRepository = RoomKnowledgeRelationshipRepository(database, clock)
    private val markdownImportTasks = RoomMarkdownImportTaskRepository(database)
    private val markdownPrivateAssets = AndroidMarkdownPrivateAssetStore(context)
    private val jsonKnowledgeTasks = RoomJsonKnowledgeTaskRepository(database)
    private val jsonKnowledgePrivateAssets = AndroidJsonKnowledgePrivateAssetStore(context)
    private val chatGptExportTasks = RoomChatGptImportTaskRepository(database)
    private val chatGptExportPrivateAssets = AndroidChatGptExportPrivateAssetStore(context)
    private val claudeExportTasks = RoomClaudeImportTaskRepository(database)
    private val claudeExportPrivateAssets = AndroidClaudeExportPrivateAssetStore(context)
    private val nanfengKnowledgeExportTasks = RoomNanfengKnowledgeImportTaskRepository(database)
    private val nanfengKnowledgeExportPrivateAssets = AndroidNanfengKnowledgeExportPrivateAssetStore(context)
    private val p6kZipImportTasks = RoomP6KZipImportTaskRepository(database)
    private val p6kZipAssetRecoveryJobs = RoomP6KZipAssetRecoveryJobRepository(database)
    private val p6kProfilePersonalizationSettings = RoomP6KProfilePersonalizationSettingsOwner(database)
    private val pdfTextImportTasks = RoomPdfTextImportTaskRepository(database)
    private val pdfTextPrivateAssets = AndroidPdfTextKnowledgePrivateAssetStore(context)
    private val webTextSnapshotTasks = RoomWebTextSnapshotTaskRepository(database)
    val invocationRepository = RoomInvocationRepository(database)
    val generatedCandidateRepository = RoomGeneratedCandidateRepository(database)
    private val p6kImportIdentityLedger = RoomP6KImportIdentityLedger(database, AndroidP6KImportIdentityEncoder(context))
    val conversationRepository = RoomConversationRepository(database, privateAttachmentStore, p6kImportIdentityLedger)
    /** P6-A: one active, standalone, text-only conversation can reach the shared exchange gateway via SAF. */
    val conversationExchangeExportPort = AndroidConversationExchangeExportPort(
        context,
        ExportConversationExchangeUseCase(conversationRepository, BuildConfig.VERSION_NAME, clock),
    )
    private val p6kZipCommitStore = RoomP6KZipImportCommitStore(database, conversationRepository, p6kImportIdentityLedger)
    private val p6kZipManualAssetLinkOwner = RoomP6KZipManualAssetLinkOwner(database, conversationRepository)
    private val p6kZipMappedAssetLinkOwner = RoomP6KZipMappedAssetLinkOwner(database, conversationRepository, identityLedger = p6kImportIdentityLedger)
    private val manageP6KChatGptZipImport = com.nanzhufeng.ai.domain.ManageP6KChatGptZipImportUseCase(p6kZipImportTasks, p6kZipCommitStore, clock)
    val p6kZipAssetRecoveryScheduler = AndroidP6KZipAssetRecoveryScheduler(context, p6kZipAssetRecoveryJobs, p6kZipImportTasks)
    val p6kZipIntakeStore = AndroidP6KZipIntakeStore(
        context, p6kZipImportTasks, manageP6KChatGptZipImport, p6kProfilePersonalizationSettings,
        p6kZipManualAssetLinkOwner, p6kZipMappedAssetLinkOwner, privateAttachmentStore,
        conversationRepository, clock, assetRecoveryJobs = p6kZipAssetRecoveryJobs,
        assetRecoveryScheduler = p6kZipAssetRecoveryScheduler,
    )
    init {
        kotlin.concurrent.thread(isDaemon = true, name = "p6k-import-ledger-recovery") {
            runCatching {
                p6kImportIdentityLedger.backfillExactLegacyEvidence(clock.instant())
                p6kImportIdentityLedger.markInterruptedAsUnknown(clock.instant())
            }
        }
        p6kZipAssetRecoveryScheduler.resumePending()
    }
    private val chatGptConversationCommitStore = RoomChatGptImportCommitStore(database, conversationRepository)
    private val claudeConversationCommitStore = RoomClaudeImportCommitStore(database, conversationRepository)
    private val nanfengKnowledgeConversationCommitStore = RoomNanfengKnowledgeImportCommitStore(database, conversationRepository)
    val projectRepository = RoomProjectRepository(database)
    val memoryRepository = RoomMemoryRepository(database, clock)
    private val workspaceExchangeV2Source = RepositoryNfaiExchangeWorkspaceSource(
        projects = projectRepository,
        conversations = conversationRepository,
        knowledge = knowledgeRepository,
        memories = memoryRepository,
        relationships = knowledgeRelationshipRepository,
        attachments = privateAttachmentRepository,
        privateStore = privateAttachmentStore,
    )
    private val workspaceExchangeV2Planner = WorkspaceExchangeV2ScopePlanner(
        source = workspaceExchangeV2Source,
        projects = projectRepository,
        conversations = conversationRepository,
        knowledge = knowledgeRepository,
        memories = memoryRepository,
        relationships = knowledgeRelationshipRepository,
    )
    val workspaceExchangeV2ExportPort = AndroidWorkspaceExchangeV2ExportPort(
        context = context,
        planner = workspaceExchangeV2Planner,
        writer = NfaiExchangeV2PackageWriter(
            mapper = NfaiExchangeV2OwnerMapper(workspaceExchangeV2Source, BuildConfig.VERSION_NAME, clock),
            source = workspaceExchangeV2Source,
        ),
    )
    private val p6V2JournalInterruptAcceptance = P6V2JournalInterruptAcceptance(context, database)
    private val p6V2Schema38UpgradeAcceptance = P6V2Schema38UpgradeAcceptance(context, database)
    private val workspaceExchangeV2AtomicRestoreStore = AndroidWorkspaceExchangeV2AtomicRestoreStore(
        context,
        database,
        p6V2JournalInterruptAcceptance::afterAttachmentPromotion,
    )
    init {
        if (BuildConfig.P6_V2_JOURNAL_INTERRUPT_ACCEPTANCE) {
            Thread {
                p6V2JournalInterruptAcceptance.recordStartupAudit(workspaceExchangeV2AtomicRestoreStore)
            }.start()
        }
        if (BuildConfig.P6_V2_SCHEMA38_UPGRADE_ACCEPTANCE) {
            Thread { p6V2Schema38UpgradeAcceptance.recordStartupAudit() }.start()
        }
    }
    val workspaceExchangeV2AtomicRestoreOwner = WorkspaceExchangeV2AtomicRestoreOwner(workspaceExchangeV2AtomicRestoreStore, clock)
    val workspaceExchangeV2OpenDocumentRestorePort = AndroidWorkspaceExchangeV2OpenDocumentRestorePort(
        context,
        workspaceExchangeV2AtomicRestoreOwner,
    )
    val offlineEvalRepository = RoomOfflineEvalRepository(database)
    // P8-B is an internal read-only status bridge. It has no runtime, fixture registry, UI or worker.
    private val p8AgentLedger = RoomAgentLedger(database)
    val p8BReadOnlyAgentLedgerStatus = P8BProductionReadOnlyAgentLedgerStatus(p8AgentLedger)
    /** P8-C's explicit, no-input, local-only action. No fixture registry is reachable here. */
    val p8CProductionLocalAgent = P8CProductionLocalAgentController(p8AgentLedger, clock)
    // P7-E is deliberately present in the production container, but no UI/worker can reach it
    // without a future verified-auth handle. It is separate from P5-D's local backup writer.
    private val p7bMetadataStore = RoomP7BMetadataStore(database)
    private val p7bAccountStateMachine = P7BAccountStateMachine(p7bMetadataStore, AndroidP7BAccountVault(context.applicationContext))
    val p7fGoogleAccountOwner = P7FGoogleAccountOwner(context.applicationContext)
    val p7fManualConversationSyncOwner = P7FManualConversationSyncOwner(
        conversations = conversationRepository,
        database = database,
        accounts = p7bAccountStateMachine,
        accountOwner = p7fGoogleAccountOwner,
    )
    val p7fSelectedConversationSyncScheduler = P7FSelectedConversationSyncScheduler(context.applicationContext)
    private val p7dStateStore = RoomP7DStateStore(database)
    private val p7eRestoreWriter = AndroidP7ESemanticAtomicRestoreWriter(context.applicationContext, database)
    val p7eGuardedRestoreOwner = P7EGuardedRestoreOwner(
        accounts = p7bAccountStateMachine,
        accountMetadata = p7bMetadataStore,
        syncState = p7dStateStore,
        restore = P7ERestorePlanCoordinator(p7eRestoreWriter),
        receipts = AndroidP7ERestoreReceiptStore(context.applicationContext),
        semanticSource = AndroidP7ESemanticSnapshotSourceAdapter(database),
    )
    val runOfflineEval = RunOfflineEvalUseCase(offlineEvalRepository, clock, BuildConfig.VERSION_NAME)
    val exportOfflineEvalReport = ExportOfflineEvalReportUseCase(offlineEvalRepository, AndroidOfflineEvalReportStore(context))
    val manageProject = ManageProjectUseCase(ProjectDomain(clock), projectRepository)
    val manageMemory = ManageMemoryUseCase(MemoryDomain(clock), memoryRepository)
    val manageKnowledge = ManageKnowledgeUseCase(KnowledgeDomain(clock), knowledgeRepository)
    val manageKnowledgeRelationships = ManageKnowledgeRelationshipsUseCase(KnowledgeRelationshipDomain(clock), knowledgeRelationshipRepository)
    val manageMarkdownImport = ManageMarkdownImportUseCase(markdownImportTasks, markdownPrivateAssets, MarkdownKnowledgeAdapter(KnowledgeDomain(clock)), manageKnowledge, clock)
    val exportMarkdownKnowledge = ExportMarkdownKnowledgeUseCase(knowledgeRepository, AndroidMarkdownKnowledgeExportStore(context), clock)
    val manageJsonKnowledgeImport = ManageJsonKnowledgeImportUseCase(jsonKnowledgeTasks, jsonKnowledgePrivateAssets, JsonKnowledgeAdapter(KnowledgeDomain(clock)), manageKnowledge, clock)
    /** P6-H is local-only: parser, private copy and atomic Room commit; no Provider or Key surface. */
    val manageChatGptExportImport = ManageChatGptExportImportUseCase(chatGptExportTasks, chatGptExportPrivateAssets, ChatGptExportJsonAdapter(), chatGptConversationCommitStore, clock)
    /** P6-I is local-only and has no visible picker until the persisted task chain is verified. */
    val manageClaudeExportImport = ManageClaudeExportImportUseCase(claudeExportTasks, claudeExportPrivateAssets, ClaudeExportJsonAdapter(), claudeConversationCommitStore, clock)
    /** P6-J handles an exported static JSON copy only; it has no knowledge database or Provider path. */
    val manageNanfengKnowledgeExportImport = ManageNanfengKnowledgeExportImportUseCase(nanfengKnowledgeExportTasks, nanfengKnowledgeExportPrivateAssets, NanfengKnowledgeExportJsonAdapter(), nanfengKnowledgeConversationCommitStore, clock)
    val exportJsonKnowledge = ExportJsonKnowledgeUseCase(knowledgeRepository, AndroidJsonKnowledgeExportStore(context), clock)
    val managePdfTextKnowledgeImport = ManagePdfTextKnowledgeImportUseCase(pdfTextImportTasks, pdfTextPrivateAssets, PdfTextKnowledgeAdapter(KnowledgeDomain(clock)), manageKnowledge, clock)
    val manageWebTextSnapshot = ManageWebTextSnapshotUseCase(webTextSnapshotTasks, AndroidWebTextSnapshotPrivateAssetStore(context), AndroidPublicWebFetcher(), manageKnowledge, clock)
    // P4-B is local metadata-only source selection. It cannot assemble a Prompt or enable egress.
    val readContextSelection = ReadContextSelectionUseCase(conversationRepository, projectRepository)
    // P4-D only builds a transient, explicitly controlled local body IR. It cannot create a Prompt or egress.
    val readExplicitContextBody = ReadExplicitContextBodyUseCase(conversationRepository, projectRepository, memoryRepository, knowledgeRepository, readContextSelection)
    // P4-K composes the P4-J local extractor with metadata-only prefix evidence; neither has a write or egress path.
    val readLocalContextPreview = ReadLocalContextPreviewUseCase(ReadLocalContextCompressionUseCase(readExplicitContextBody))
    // P4-M reads only terminal P3-C lineage metadata and remains separate from body/compression paths.
    val readExplicitLocalActionTrace = ReadExplicitLocalActionTraceUseCase(conversationRepository, projectRepository, conversationRepository, readContextSelection)
    private val conversationTreeService = ConversationTreeService(clock)
    private val conversationManagementDomain = ConversationManagementDomain(clock)
    val manageConversation = ManageConversationUseCase(conversationManagementDomain, conversationRepository)
    val searchConversations = SearchConversationsUseCase(conversationRepository, ConversationSearchProjection(conversationManagementDomain))
    val searchConversationAttachments = SearchConversationAttachmentsUseCase(conversationRepository)
    val localSearchHistory = AndroidLocalSearchHistoryStore(context)
    val conversationReadMarkerStore = AndroidConversationReadMarkerStore(context)
    private val conversationExportStore = AndroidConversationExportStore(context)
    val exportConversationPackage = ExportConversationPackageUseCase(conversationRepository, conversationExportStore, clock)
    val createConversation = CreateConversationUseCase(conversationTreeService, conversationRepository)
    val appendConversationMessage = AppendConversationMessageUseCase(conversationTreeService, conversationRepository)
    val editConversationUserMessage = EditConversationUserMessageUseCase(conversationTreeService, conversationRepository)
    val switchConversationBranch = SwitchConversationBranchUseCase(conversationTreeService, conversationRepository)
    val saveConversationDraft = SaveConversationDraftUseCase(conversationRepository, clock)
    val submitConversationDraft = SubmitConversationDraftUseCase(conversationRepository, conversationRepository, conversationTreeService)
    val addConversationImageAttachment = AddConversationImageAttachmentUseCase(
        privateAttachmentStore, privateAttachmentRepository, conversationRepository, clock,
    )
    val removeConversationAttachment = RemoveConversationAttachmentUseCase(conversationRepository, clock)
    val deletePersistedConversationAttachment = DeletePersistedConversationAttachmentUseCase(
        conversationRepository,
        privateAttachmentRepository,
        privateAttachmentStore,
        clock,
    )
    val conversationAttachmentPreviewProjection = ConversationAttachmentPreviewProjection(privateAttachmentRepository, privateAttachmentStore)
    val pdfPreviewPositionStore = AndroidPdfPreviewPositionStore(context)
    val videoPreviewPositionStore = AndroidVideoPreviewPositionStore(context)
    val audioPreviewPositionStore = AndroidAudioPreviewPositionStore(context)
    /** P6-G local-only owner; its store contains no Key, endpoint, prompt or Provider invocation. */
    val p6gModelSelection = P6GModelSelectionOwner(AndroidP6GModelSelectionStore(context), P6GModelRouter())
    /** Per-conversation web-search overrides are local, content-free, and do not alter the global default. */
    val conversationWebSearchOverrides = ConversationWebSearchOverrideOwner(AndroidConversationWebSearchOverrideStore(context))
    /** Per-conversation answer style is independent from the global personalization setting. */
    val conversationStyleOverrides = ConversationStyleOverrideOwner(AndroidConversationStyleOverrideStore(context))
    val readConversationAttemptHistory = ReadConversationAttemptHistoryUseCase(conversationRepository, conversationRepository)
    val messagePresentationRenderer = MessagePresentationRenderer()
    private val conversationRuntimeStateMachine = ConversationRuntimeStateMachine(clock)
    val applyConversationRuntimeEvent = ApplyConversationRuntimeEventUseCase(
        conversationRepository, conversationRepository, conversationRuntimeStateMachine,
    )
    val startLocalConversationRuntime = StartLocalConversationRuntimeUseCase(
        conversationRepository, conversationRepository, conversationRuntimeStateMachine, clock,
    )
    val submitDraftAndStartProviderConversationRuntime = SubmitConversationDraftAndStartProviderRuntimeUseCase(
        conversationRepository, conversationRepository, conversationRepository,
        conversationRuntimeStateMachine, conversationTreeService, clock,
    )
    val startProviderRuntimeForExistingUser = StartProviderRuntimeForExistingUserUseCase(
        conversationRepository, conversationRepository, conversationRuntimeStateMachine, clock,
    )
    val deterministicFixtureStreamingAdapter = DeterministicFixtureStreamingAdapter(clock)
    /** The confirmation owner remains content-free; the executor receives text only after confirmation. */
    /** P5-B only finalizes interrupted facts at process start; it never schedules or resumes work. */
    val taskRecoveryAudit = TaskRecoveryAudit(
        markdown = manageMarkdownImport,
        json = manageJsonKnowledgeImport,
        pdf = managePdfTextKnowledgeImport,
        web = manageWebTextSnapshot,
        conversations = conversationRepository,
        conversationList = conversationRepository,
        runtime = conversationRepository,
        applyRuntimeEvent = applyConversationRuntimeEvent,
        clock = clock,
    )
    /** P5-C owns aggregate-only privacy inventory, scoped deletion and user-initiated diagnostics. */
    val privacyDataManager = AndroidPrivacyDataManager(context, database, BuildConfig.VERSION_NAME)
    /** P5-D is manual local portability only; it never participates in lifecycle recovery or cloud backup. */
    val localBackupRestoreManager = AndroidLocalBackupRestoreManager(context, database, BuildConfig.VERSION_NAME)
    private val modelServiceSettingsRepository = AndroidModelServiceSettingsRepository(context)
    private val chatRoutingPolicyRepository = AndroidChatRoutingPolicyRepository(context)
    private val assistantExperienceSettingsRepository = AndroidAssistantExperienceSettingsRepository(context)
    val historyKnowledgeAutoCurationScheduler = AndroidHistoryKnowledgeAutoCurationScheduler(context)
    private val historyKnowledgeAutoCurationCheckpointStore = AndroidHistoryKnowledgeCurationCheckpointStore(context)
    val historyKnowledgeAutoCurationRunStore = AndroidHistoryKnowledgeAutoCurationRunStore(context)
    private val notificationReminderSettingsRepository = AndroidNotificationReminderSettingsRepository(context)
    private val appearanceSettingsRepository = AndroidAppearanceSettingsRepository(context)
    private val providerCredentialStore = createAndroidProviderCredentialStore(context)
    val directChatCallAudit = AndroidDirectChatCallAuditStore(context)
    val contextSelectionAudits = AndroidContextSelectionAuditStore(context)
    val providerDiagnostics = RoomProviderDiagnosticStore(database)
    private val normalChatSendAttempts = RoomNormalChatSendAttemptStore(database)
    val scheduledMonitorRepository = RoomScheduledMonitorRepository(database)
    val reminderDraftGenerationRecords = RoomReminderDraftGenerationRecordStore(database)
    val conversationTitleGenerationRecords = RoomConversationTitleGenerationRecordStore(database)
    val assistantResponseModelAttributions = RoomAssistantResponseModelAttributionStore(database)
    private val localContextBroker = LocalContextBroker(RoomLocalContextIndex(database))
    private val modelRegistrySnapshotStore = AndroidModelRegistrySnapshotStore(context)
    private val modelProfileDirectory = AndroidModelProfileDirectory(context)
    private val modelHealthStore = AndroidModelHealthStore(context)
    private val modelProfileRefresher = RefreshModelProfilesUseCase(
        modelProfileDirectory, providerCredentialStore, AndroidProviderModelListClient(), clock,
    )
    private val storedModelRegistrySnapshots = modelRegistrySnapshotStore.load()
    private val modelRegistry: VersionedModelRegistry = InMemoryVersionedModelRegistry(
        initialSnapshots = listOf(P3CLocalFixtureRegistry.snapshot) + listOfNotNull(storedModelRegistrySnapshots.current),
        initialPreviousStableSnapshots = listOfNotNull(storedModelRegistrySnapshots.previousStable),
    )
    private val modelResolver = UnifiedModelResolver(modelRegistry, modelProfileDirectory, modelHealthStore)
    /** MM-O4-H is reachable only from the explicit Compare model-menu item. Construction is inert. */
    private val compareSessionStore = RoomCompareConversationSessionStore(database, clock)
    private val compareBranchExecutionPorts = RoomCompareBranchExecutionPorts(database)
    private val compareExecutionApplicationOwner = CompareExecutionApplicationOwner(
        registry = OpenRouterVerifiedMultiProviderRegistryProjection(modelRegistry),
        // Compare never delegates to Auto; this fail-closed port exists only to satisfy the
        // orchestrator's Auto constructor contract.
        orchestrator = MultiModelOrchestrator { _, _ -> null },
        clock = clock,
    )
    val compareVisibleExecutionOwner = CompareVisibleExecutionOwner(
        conversations = conversationRepository,
        submitDraft = submitConversationDraft,
        registry = OpenRouterVerifiedMultiProviderRegistryProjection(modelRegistry),
        compare = compareExecutionApplicationOwner,
        sessionOwner = CompareConversationSessionOwner(clock),
        sessionStore = compareSessionStore,
        credentials = providerCredentialStore,
        branchPorts = compareBranchExecutionPorts,
        clock = clock,
    )
    val conversationActionOrchestrator = ConversationActionOrchestrator(
        conversations = conversationRepository,
        runtime = conversationRepository,
        actions = conversationRepository,
        registry = modelRegistry,
        stateMachine = conversationRuntimeStateMachine,
        clock = clock,
    )

    val runAiTask = RunAiTaskUseCase(mockAiTaskRunner, invocationRepository, clock)
    val confirmAiRequest = ConfirmAiRequest(clock, modelRegistry)
    val persistGeneratedCandidate = PersistGeneratedCandidateUseCase(generatedCandidateRepository, clock)
    val saveKnowledgeItem = SaveKnowledgeItemUseCase(knowledgeRepository, clock)
    val saveCandidateReview = SaveCandidateReviewUseCase(saveKnowledgeItem, generatedCandidateRepository, clock)
    val readKnowledgeLibrary = ReadKnowledgeLibraryUseCase(knowledgeRepository, generatedCandidateRepository)
    /** User-confirmed historical conversation -> reviewable local-knowledge candidate; it never writes by itself. */
    val readHistoryKnowledgeCurationSource = com.nanzhufeng.ai.domain.ReadHistoryKnowledgeCurationSourceUseCase(conversationRepository)
    val exportKnowledgeSnapshot = ExportKnowledgeSnapshotUseCase(knowledgeRepository, clock)
    private val knowledgeExportStore = AndroidKnowledgeExportStore(context)
    val exportKnowledgePackage = ExportKnowledgePackageUseCase(knowledgeRepository, knowledgeExportStore, clock)
    val captureGalleryImage = CaptureGalleryImageUseCase(
        privateAttachmentStore,
        captureDraftFactory,
        captureDraftRepository,
        privateAttachmentRepository,
    )
    val captureTextDraft = CaptureTextDraftUseCase(captureDraftFactory, captureDraftRepository)
    val restoreLatestCaptureDraft = RestoreLatestCaptureDraftUseCase(
        privateAttachmentStore,
        captureDraftRepository,
    )
    val loadModelServiceConfiguration = LoadModelServiceConfigurationUseCase(
        modelServiceSettingsRepository,
        providerCredentialStore,
    )
    val saveModelServiceConfiguration = SaveModelServiceConfigurationUseCase(
        modelServiceSettingsRepository,
        providerCredentialStore,
        loadModelServiceConfiguration,
    )
    val glmOcrTaskOwner = com.nanzhufeng.ai.domain.GlmOcrTaskOwner(
        tasks = glmOcrTasks,
        privateStore = privateAttachmentStore,
        assets = privateAttachmentRepository,
        loadConfiguration = loadModelServiceConfiguration,
        credentials = providerCredentialStore,
        transport = com.nanzhufeng.ai.ai.OfficialGlmOcrTransport(),
        conversationDraftCreator = com.nanzhufeng.ai.domain.GlmOcrConversationDraftCreator { markdown ->
            val safe = runCatching { markdown.toConversationReference() }.getOrNull()
                ?: return@GlmOcrConversationDraftCreator com.nanzhufeng.ai.domain.GlmOcrContinueResult.Rejected("Markdown 文件元数据不完整。")
            val created = createConversation.execute() as? com.nanzhufeng.ai.domain.ConversationMutationResult.Saved
                ?: return@GlmOcrConversationDraftCreator com.nanzhufeng.ai.domain.GlmOcrContinueResult.Rejected("新对话创建失败，原结果保持不变。")
            when (saveConversationDraft.execute(created.snapshot.conversation.id, "", listOf(safe))) {
                is com.nanzhufeng.ai.domain.ConversationDraftResult.Saved -> com.nanzhufeng.ai.domain.GlmOcrContinueResult.Created(created.snapshot.conversation.id)
                is com.nanzhufeng.ai.domain.ConversationDraftResult.Rejected -> com.nanzhufeng.ai.domain.GlmOcrContinueResult.Rejected("新对话已创建，但 Markdown 没有加入草稿；原结果保持不变。")
            }
        },
        clock = clock,
        invocations = invocationRepository,
    )
    val loadChatRoutingPolicy = LoadChatRoutingPolicyUseCase(chatRoutingPolicyRepository)
    val saveChatRoutingPolicy = SaveChatRoutingPolicyUseCase(chatRoutingPolicyRepository)
    val loadAssistantExperienceSettings = LoadAssistantExperienceSettingsUseCase(assistantExperienceSettingsRepository)
    val saveAssistantExperienceSettings = SaveAssistantExperienceSettingsUseCase(assistantExperienceSettingsRepository)
    val loadNotificationReminderSettings = LoadNotificationReminderSettingsUseCase(notificationReminderSettingsRepository)
    val saveNotificationReminderSettings = SaveNotificationReminderSettingsUseCase(notificationReminderSettingsRepository)
    val loadAppearanceSettings = LoadAppearanceSettingsUseCase(appearanceSettingsRepository)
    val saveAppearanceSettings = SaveAppearanceSettingsUseCase(appearanceSettingsRepository)
    val loadRegistryVerificationStatus = LoadRegistryVerificationStatusUseCase(modelRegistry)
    /** Status-only dual-path owner. It sees encrypted-entry presence, never the Key value. */
    val readConnectionCapability = ReadConnectionCapabilityUseCase(
        loadModelConfiguration = loadModelServiceConfiguration,
        loadRegistryStatus = loadRegistryVerificationStatus,
        encryptedSyncConfigured = { P7CAndroidCloudGateway.availability() is com.nanzhufeng.ai.domain.P7CServiceAvailability.Configured },
    )
    // P2-L reads Registry metadata only. It never probes a Key or enables egress.
    val loadRealServiceAcceptanceStatus = LoadRealServiceAcceptanceUiStatusUseCase(modelRegistry)
    val verifyOpenRouterRegistry = VerifyOpenRouterRegistryUseCase(
        client = AndroidOpenRouterRegistryCatalogClient(),
        verifier = OpenRouterRegistrySnapshotVerifier(),
        registry = modelRegistry,
        store = modelRegistrySnapshotStore,
        clock = clock,
    )
    /** Only an explicit Settings action invokes this fixed, content-free connection probe. */
    val providerConnectionProbe = ProviderConnectionProbe(
        configuration = loadModelServiceConfiguration,
        modelResolver = modelResolver,
        credentials = providerCredentialStore,
        transport = OfficialProviderChatTransport(),
        diagnostics = providerDiagnostics,
        clock = clock,
    )
    // P2-E's offline codec remains isolated from P2-J's public, no-auth registry GET.
    val openRouterOfflineAdapter = OpenRouterOfflineAdapterContract(modelRegistry)
    // P2-K is transport-ready but disabled: this object cannot read a Key or send content.
    val openRouterInferenceAdapter = OpenRouterInferenceAdapter(
        registry = modelRegistry,
        credentialStore = providerCredentialStore,
        transport = OfficialOpenRouterInferenceTransport(),
        egressPolicy = OpenRouterEgressPolicy.Disabled,
        clock = clock,
    )
    val normalChatOpenRouterExecutor = NormalChatOpenRouterExecutor(
        configuration = loadModelServiceConfiguration,
        conversations = conversationRepository,
        registry = modelRegistry,
        credentials = providerCredentialStore,
        submitDraft = submitConversationDraft,
        appendMessage = appendConversationMessage,
        contextBroker = localContextBroker,
        transport = OfficialProviderChatTransport(),
        audit = directChatCallAudit,
        diagnostics = providerDiagnostics,
        sendAttempts = normalChatSendAttempts,
        responseModelAttributions = assistantResponseModelAttributions,
        adapters = ChatProviderAdapters(),
        modelResolver = modelResolver,
        modelHealthReporter = modelResolver,
        contextSelectionAudits = contextSelectionAudits,
        modelProfileRefresher = modelProfileRefresher,
        clock = clock,
        verifyOpenRouterRegistry = verifyOpenRouterRegistry,
        loadRoutingPolicy = loadChatRoutingPolicy,
        attachmentRepository = privateAttachmentRepository,
        attachmentStore = privateAttachmentStore,
        submitDraftAndStartProviderRuntime = submitDraftAndStartProviderConversationRuntime,
        startProviderRuntimeForExistingUser = startProviderRuntimeForExistingUser,
        loadAssistantExperienceSettings = loadAssistantExperienceSettings::execute,
        resolveConversationWebSearchEnabled = conversationWebSearchOverrides::effectiveEnabled,
        resolveConversationStyle = conversationStyleOverrides::effectiveStyle,
        applyRuntimeEvent = applyConversationRuntimeEvent,
        runtimeRepository = conversationRepository,
        attachmentBridge = com.nanzhufeng.ai.ai.UniversalChatAttachmentBridge(
            configuration = loadModelServiceConfiguration,
            credentials = providerCredentialStore,
            modelResolver = modelResolver,
            providerTransport = com.nanzhufeng.ai.ai.OfficialProviderChatTransport(),
            glmOcrTransport = com.nanzhufeng.ai.ai.OfficialGlmOcrTransport(),
            invocations = invocationRepository,
            clock = clock,
        ),
        saveMemorySummary = { conversationId, draft ->
            manageMemory.execute(
                MemoryIntent(
                    id = MemoryIntentId.new(),
                    action = MemoryIntentAction.CREATE,
                    memoryId = MemoryId.new(),
                    title = draft.title,
                    body = draft.body,
                    scope = MemoryScope(MemoryScopeKind.GLOBAL),
                    source = MemorySource.USER_CONFIRMED,
                    sourceStableId = "conversation:${conversationId.value}",
                    sourceSummary = "用户在对话中明确要求记住后生成的本机记忆摘要",
                ),
            )
        },
        onConversationCompleted = { conversationId ->
            if (loadAssistantExperienceSettings.execute().historyLibraryEnabled) {
                historyKnowledgeAutoCurationScheduler.enqueueConversation(conversationId)
            }
        },
        conversationTitleRefiner = ConfiguredConversationTitleRefiner(
            records = conversationTitleGenerationRecords,
            configuration = loadModelServiceConfiguration,
            credentials = providerCredentialStore,
            modelResolver = modelResolver,
            transport = OfficialProviderChatTransport(),
            clock = clock,
        ),
    )
    val scheduledMonitorExecutor = ScheduledMonitorExecutor(
        tasks = scheduledMonitorRepository,
        configuration = loadModelServiceConfiguration,
        credentials = providerCredentialStore,
        modelResolver = modelResolver,
        transport = OfficialProviderChatTransport(),
        clock = clock,
    )
    val qwenReminderDraftRefiner = QwenReminderDraftRefiner(
        records = reminderDraftGenerationRecords,
        configuration = loadModelServiceConfiguration,
        credentials = providerCredentialStore,
        modelResolver = modelResolver,
        transport = OfficialProviderChatTransport(),
        clock = clock,
    )
    val qwenHistoryKnowledgeRefiner = QwenHistoryKnowledgeRefiner(
        configuration = loadModelServiceConfiguration,
        credentials = providerCredentialStore,
        modelResolver = modelResolver,
        transport = OfficialProviderChatTransport(),
        clock = clock,
        audit = directChatCallAudit,
    )
    val automaticHistoryKnowledgeCurationOwner = com.nanzhufeng.ai.domain.AutomaticHistoryKnowledgeCurationOwner(
        settings = loadAssistantExperienceSettings::execute,
        conversations = conversationRepository,
        readSource = readHistoryKnowledgeCurationSource,
        refiner = qwenHistoryKnowledgeRefiner,
        checkpoint = historyKnowledgeAutoCurationCheckpointStore,
        readKnowledge = readKnowledgeLibrary,
        manageKnowledge = manageKnowledge,
    )

    /** Reconcile any legacy queued work with the single effective history-library permission. */
    init { historyKnowledgeAutoCurationScheduler.onSettingChanged(loadAssistantExperienceSettings.execute().historyLibraryEnabled) }

    /** Seeds the user-confirmed baseline only before any global summary has ever existed. */
    fun ensureInitialMemorySummary(): Int {
        // A soft-deleted summary is an explicit user decision. It must prevent a cold start from
        // silently recreating the baseline after the user chose "删除记忆".
        if (!InitialMemorySummary.shouldSeed(
                manageMemory.list(com.nanzhufeng.ai.domain.MemoryScopeKind.GLOBAL, null, "").size,
            )) return 0
        return InitialMemorySummary.entries.count { draft ->
            when (manageMemory.execute(
                com.nanzhufeng.ai.domain.MemoryIntent(
                    id = com.nanzhufeng.ai.domain.MemoryIntentId.new(),
                    action = com.nanzhufeng.ai.domain.MemoryIntentAction.CREATE,
                    memoryId = com.nanzhufeng.ai.domain.MemoryId.new(),
                    title = draft.title,
                    body = draft.body,
                    scope = com.nanzhufeng.ai.domain.MemoryScope(com.nanzhufeng.ai.domain.MemoryScopeKind.GLOBAL),
                    source = com.nanzhufeng.ai.domain.MemorySource.USER_CONFIRMED,
                    sourceStableId = "initial-memory-summary-v1:${draft.title}",
                    sourceSummary = "南烛枫确认的记忆摘要初始版本",
                ),
            )) {
                is com.nanzhufeng.ai.domain.MemoryMutationResult.Applied,
                is com.nanzhufeng.ai.domain.MemoryMutationResult.Replayed,
                is com.nanzhufeng.ai.domain.MemoryMutationResult.Duplicate -> true
                else -> false
            }
        }
    }
    // P2-M is reachable only from the Android Model Settings confirmation owner. It binds one
    // fixed synthetic text fixture to the currently saved provider/preset and persists one nonce.
    val p2mRealServiceReadiness = P2MRealServiceReadinessUseCase(loadModelServiceConfiguration, modelRegistry)
    val p2mTranscriptOwner = P2MTranscriptOwner(conversationRepository)
    // This special bridge remains outside all normal chat paths. Defaults reject before transport,
    // runtime receipt, or Usage Ledger writes and do not load credential bytes.
    private val p2mRealTextPreflight = RealTextExecutionPreflightOrchestrator(
        modelServiceSettingsRepository, providerCredentialStore, modelRegistry, clock,
    )
    private val p2mRealTextCoordinator = RealTextExecutionCoordinator(now = clock::instant)
    private val p2mRealTextExecutionBridge = P2MRealTextExecutionBridge(
        p2mRealTextPreflight, p2mRealTextCoordinator, clock,
    )
    /** MM-O3-B composition only: unreferenced by UI and inert until an explicit Direct request. */
    val directExecutionApplicationOwner: DirectExecutionApplicationOwner = DirectExecutionProductionComposition.create(
        registry = modelRegistry,
        settings = modelServiceSettingsRepository,
        credentials = providerCredentialStore,
        clock = clock,
    )
    val p2mRealServiceExecutor = P2MRealServiceExecutor(
        readiness = p2mRealServiceReadiness,
        registry = modelRegistry,
        credentialStore = providerCredentialStore,
        invocationRepository = invocationRepository,
        persistCandidate = persistGeneratedCandidate,
        transcriptOwner = p2mTranscriptOwner,
        tokenService = RealServiceAcceptanceTokenService(
            FileRealServiceAcceptanceTokenStore(File(context.filesDir, "p2m-real-service-tokens")), clock,
        ),
        transport = OfficialOpenRouterInferenceTransport(),
        clock = clock,
        evidenceDirectory = File(context.filesDir, "p2m-real-service-evidence"),
        realTextExecutionBridge = p2mRealTextExecutionBridge,
    )
}

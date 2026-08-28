package com.nanzhufeng.ai.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nanzhufeng.ai.domain.AiRuntimeEventId
import com.nanzhufeng.ai.domain.AiRuntimeSecurityMetadata
import com.nanzhufeng.ai.domain.AppendConversationMessageUseCase
import com.nanzhufeng.ai.domain.AppendMessageRequest
import com.nanzhufeng.ai.domain.ApplyConversationRuntimeEventUseCase
import com.nanzhufeng.ai.domain.ContentBlock
import com.nanzhufeng.ai.domain.Conversation
import com.nanzhufeng.ai.domain.ConversationSnapshot
import com.nanzhufeng.ai.domain.ConversationActionKind
import com.nanzhufeng.ai.domain.ConversationActionOrchestrator
import com.nanzhufeng.ai.domain.ConversationActionRequest
import com.nanzhufeng.ai.domain.ConversationActionIntentId
import com.nanzhufeng.ai.domain.ConversationActionResult
import com.nanzhufeng.ai.domain.ConversationAttemptLineage
import com.nanzhufeng.ai.domain.ConversationAttemptHistoryItem
import com.nanzhufeng.ai.domain.ConversationDraft
import com.nanzhufeng.ai.domain.ConversationDraftResult
import com.nanzhufeng.ai.domain.ConversationDraftSubmissionResult
import com.nanzhufeng.ai.domain.ConversationMutationResult
import com.nanzhufeng.ai.domain.ConversationRecoveryPresentation
import com.nanzhufeng.ai.domain.ConversationRecoveryPresenter
import com.nanzhufeng.ai.domain.ConversationRepository
import com.nanzhufeng.ai.domain.ConversationListRepository
import com.nanzhufeng.ai.domain.ConversationListScope
import com.nanzhufeng.ai.domain.ConversationSurface
import com.nanzhufeng.ai.domain.ConversationSurfaceRepository
import com.nanzhufeng.ai.domain.ImportedConversationProvenanceReader
import com.nanzhufeng.ai.domain.InvocationRepository
import com.nanzhufeng.ai.domain.AssistantResponseModelAttributionStore
import com.nanzhufeng.ai.domain.ContextSelectionAuditRecord
import com.nanzhufeng.ai.domain.ContextSelectionAuditStore
import com.nanzhufeng.ai.domain.ConversationManagementAction
import com.nanzhufeng.ai.domain.ConversationPurgeResult
import com.nanzhufeng.ai.domain.ConversationManagementIntent
import com.nanzhufeng.ai.domain.ConversationManagementIntentId
import com.nanzhufeng.ai.domain.ConversationManagementResult
import com.nanzhufeng.ai.domain.ManageConversationUseCase
import com.nanzhufeng.ai.domain.SearchConversationsUseCase
import com.nanzhufeng.ai.domain.SearchConversationAttachmentsUseCase
import com.nanzhufeng.ai.domain.ConversationSearchHit
import com.nanzhufeng.ai.domain.ConversationAttachmentSearchHit
import com.nanzhufeng.ai.domain.ConversationSearchCategory
import com.nanzhufeng.ai.domain.LocalSearchHistoryStore
import com.nanzhufeng.ai.domain.ConversationReadMarkerStore
import com.nanzhufeng.ai.domain.ExportConversationPackageUseCase
import com.nanzhufeng.ai.domain.ConversationExportResult
import com.nanzhufeng.ai.domain.ConversationRuntimePersistenceResult
import com.nanzhufeng.ai.domain.ConversationRuntimeRepository
import com.nanzhufeng.ai.domain.ConversationRuntimeState
import com.nanzhufeng.ai.domain.ConversationActionRepository
import com.nanzhufeng.ai.domain.ConversationBranchHistory
import com.nanzhufeng.ai.domain.CreateConversationUseCase
import com.nanzhufeng.ai.domain.EditConversationUserMessageUseCase
import com.nanzhufeng.ai.domain.EditableConversationUserMessage
import com.nanzhufeng.ai.domain.DeterministicFixtureStreamingAdapter
import com.nanzhufeng.ai.domain.MessageNodeId
import com.nanzhufeng.ai.domain.MessageNode
import com.nanzhufeng.ai.domain.MessagePresentationRenderer
import com.nanzhufeng.ai.domain.MessageRole
import com.nanzhufeng.ai.domain.MessageTree
import com.nanzhufeng.ai.domain.ModelPresetId
import com.nanzhufeng.ai.domain.PresentedTranscriptMessage
import com.nanzhufeng.ai.domain.ConversationTranscriptPresentation
import com.nanzhufeng.ai.domain.RuntimeCancelled
import com.nanzhufeng.ai.domain.SaveConversationDraftUseCase
import com.nanzhufeng.ai.domain.StartLocalConversationRuntimeUseCase
import com.nanzhufeng.ai.domain.SubmitConversationDraftUseCase
import com.nanzhufeng.ai.domain.AddConversationImageAttachmentUseCase
import com.nanzhufeng.ai.domain.AddConversationAttachmentResult
import com.nanzhufeng.ai.domain.RemoveConversationAttachmentUseCase
import com.nanzhufeng.ai.domain.RemoveConversationAttachmentResult
import com.nanzhufeng.ai.domain.ConversationAttachmentPreviewProjection
import com.nanzhufeng.ai.domain.ReadConversationAttemptHistoryUseCase
import com.nanzhufeng.ai.domain.ConversationAttachmentPreview
import com.nanzhufeng.ai.domain.ConversationAttachmentOriginalPreview
import com.nanzhufeng.ai.domain.ConversationAttachmentPdfPreview
import com.nanzhufeng.ai.domain.ConversationAttachmentVideoPreview
import com.nanzhufeng.ai.domain.ConversationAttachmentAudioPreview
import com.nanzhufeng.ai.domain.ConversationAttachmentTextPreview
import com.nanzhufeng.ai.domain.PdfPreviewPositionStore
import com.nanzhufeng.ai.domain.VideoPreviewPositionStore
import com.nanzhufeng.ai.domain.AudioPreviewPositionStore
import com.nanzhufeng.ai.domain.ConversationAttachmentReference
import com.nanzhufeng.ai.domain.ConversationAttachmentSelection
import com.nanzhufeng.ai.domain.AttachmentId
import com.nanzhufeng.ai.domain.AttachmentOpenResult
import com.nanzhufeng.ai.domain.TemporaryConversationDomain
import com.nanzhufeng.ai.domain.TemporaryConversationRecovery
import com.nanzhufeng.ai.domain.TemporaryConversationIsolation
import com.nanzhufeng.ai.domain.AddTemporaryConversationAttachmentUseCase
import com.nanzhufeng.ai.domain.ClearTemporaryConversationUseCase
import com.nanzhufeng.ai.domain.P6GConversationOverride
import com.nanzhufeng.ai.domain.P6GGlobalDefault
import com.nanzhufeng.ai.domain.P6GLocalCatalogSnapshot
import com.nanzhufeng.ai.domain.P6GCatalogCandidate
import com.nanzhufeng.ai.domain.P6GCapability
import com.nanzhufeng.ai.domain.P6GModelSelectionOwner
import com.nanzhufeng.ai.domain.P6GModelTier
import com.nanzhufeng.ai.domain.ConversationWebSearchOverride
import com.nanzhufeng.ai.domain.ConversationWebSearchOverrideOwner
import com.nanzhufeng.ai.domain.ConversationWebSearchOverrideMutationResult
import com.nanzhufeng.ai.domain.LoadAssistantExperienceSettingsUseCase
import com.nanzhufeng.ai.domain.P6GProviderFamily
import com.nanzhufeng.ai.domain.P6GRouteRequest
import com.nanzhufeng.ai.domain.P6GSelectionMutationResult
import com.nanzhufeng.ai.ai.NormalChatOpenRouterExecutor
import com.nanzhufeng.ai.background.NoopNormalChatBackgroundExecution
import com.nanzhufeng.ai.background.NormalChatBackgroundExecution
import com.nanzhufeng.ai.background.NormalChatBackgroundOperation
import com.nanzhufeng.ai.domain.TemporaryAttachmentResult
import com.nanzhufeng.ai.data.AndroidGalleryOpenResult
import com.nanzhufeng.ai.data.AndroidVisualAttachmentOpenResult
import com.nanzhufeng.ai.data.AndroidGallerySelectionReader
import com.nanzhufeng.ai.data.AndroidDocumentSelectionReader
import com.nanzhufeng.ai.data.AndroidDocumentOpenResult
import android.net.Uri
import com.nanzhufeng.ai.domain.SwitchConversationBranchUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Returning after this gap is treated like a fresh chat entry, never a resume of old prose. */
private const val FRESH_CHAT_AFTER_BACKGROUND_MS = 15L * 60L * 1_000L

private sealed interface PickedConversationAttachment {
    data class Opened(val selection: ConversationAttachmentSelection) : PickedConversationAttachment
    data class Rejected(val reason: String) : PickedConversationAttachment
}

private data class AttachmentBatchOutcome(
    val addedCount: Int,
    val duplicateCount: Int,
    val rejectionReasons: List<String>,
    val temporaryRecovery: TemporaryConversationRecovery? = null,
)

data class ConversationFoundationUiState(
    val isLoading: Boolean = true,
    val isCreating: Boolean = false,
    val isSending: Boolean = false,
    val conversations: List<Conversation> = emptyList(),
    val unreadConversationIds: Set<com.nanzhufeng.ai.domain.ConversationId> = emptySet(),
    val listScope: ConversationListScope = ConversationListScope.ACTIVE,
    val searchQuery: String = "",
    val searchCategory: ConversationSearchCategory = ConversationSearchCategory.ALL,
    val searchResults: List<ConversationSearchHit> = emptyList(),
    val attachmentSearchResults: List<ConversationAttachmentSearchHit> = emptyList(),
    val searchAttachmentPreviews: Map<AttachmentId, ConversationAttachmentPreview> = emptyMap(),
    /** Inert, bounded excerpts make text/PDF catalog cards informative before opening. */
    val searchAttachmentTextPreviews: Map<AttachmentId, ConversationAttachmentTextPreview> = emptyMap(),
    val searchPanelOpen: Boolean = false,
    val searchHistory: List<String> = emptyList(),
    val searchHistoryOpen: Boolean = false,
    val searchAnchorMessageId: MessageNodeId? = null,
    val selectedConversationId: com.nanzhufeng.ai.domain.ConversationId? = null,
    val surface: ConversationSurface = ConversationSurface.CHAT,
    val currentProjectId: String? = null,
    val runtime: ConversationRuntimeState? = null,
    val messages: List<PresentedTranscriptMessage> = emptyList(),
    /** Local, content-free source disclosures bound to individual visible assistant answers. */
    val answerContextSelections: Map<MessageNodeId, List<ContextSelectionAuditRecord>> = emptyMap(),
    val draft: ConversationDraft? = null,
    val recovery: ConversationRecoveryPresentation? = null,
    val currentLeafId: MessageNodeId? = null,
    val branchLeaves: List<ConversationBranchUi> = emptyList(),
    /** Ephemeral screen feedback after a newly created local branch has been opened. */
    val branchCreation: ConversationBranchCreationUi? = null,
    val editableUserMessages: List<EditableConversationUserMessage> = emptyList(),
    val lineage: ConversationAttemptLineage? = null,
    val attemptHistory: List<ConversationAttemptHistoryItem> = emptyList(),
    val attachmentPreviews: Map<AttachmentId, ConversationAttachmentPreview> = emptyMap(),
    val attachmentTransfer: AttachmentTransferRequest? = null,
    val imagePreview: ConversationAttachmentOriginalPreview? = null,
    val pdfPreview: ConversationAttachmentPdfPreview? = null,
    val videoPreview: ConversationAttachmentVideoPreview? = null,
    val audioPreview: ConversationAttachmentAudioPreview? = null,
    val textPreview: ConversationAttachmentTextPreview? = null,
    val temporaryRecovery: TemporaryConversationRecovery? = null,
    val p6gCatalog: P6GLocalCatalogSnapshot? = null,
    val p6gGlobalDefault: P6GGlobalDefault = P6GGlobalDefault(0, null),
    val p6gConversationOverride: P6GConversationOverride? = null,
    val globalWebSearchEnabled: Boolean = true,
    val conversationWebSearchOverride: ConversationWebSearchOverride? = null,
    val importedFromChatGptExport: Boolean = false,
    val importedFromClaudeExport: Boolean = false,
    val importedFromChatGptZip: Boolean = false,
    /** Inline feedback only for a submitted chat message that did not get a reply. */
    val sendError: String? = null,
    /** Durable, content-free decision point for a prior ordinary Provider attempt. */
    val normalSendRecovery: NormalChatOpenRouterExecutor.Recovery? = null,
    /** True only while an explicit retry owns a new assistant placeholder. */
    val normalSendRetryInProgress: Boolean = false,
    val notice: String? = null,
)

enum class AttachmentTransferAction { DOWNLOAD, SHARE }

/** An explicit user download/share receives a verified one-shot stream, never a giant UI byte array. */
data class AttachmentTransferItem(
    val id: AttachmentId,
    val displayName: String?,
    val mimeType: String,
    val byteCount: Long,
    val open: () -> java.io.InputStream,
)

data class AttachmentTransferRequest(
    val id: AttachmentId,
    val action: AttachmentTransferAction,
    val displayName: String?,
    val mimeType: String,
    val byteCount: Long,
    val open: () -> java.io.InputStream,
    val batch: List<AttachmentTransferItem> = listOf(AttachmentTransferItem(id, displayName, mimeType, byteCount, open)),
)

data class ConversationBranchUi(
    val leafId: MessageNodeId,
    val role: MessageRole,
    val label: String,
    val revision: Int,
    val isCurrent: Boolean,
)

data class ConversationBranchCreationUi(
    val branchConversationId: com.nanzhufeng.ai.domain.ConversationId,
)

private data class LoadedConversation(
    val conversations: List<Conversation>,
    val snapshot: com.nanzhufeng.ai.domain.ConversationSnapshot?,
    val runtime: ConversationRuntimeState?,
    val lineage: ConversationAttemptLineage?,
    val lineages: List<ConversationAttemptLineage>,
    val attemptHistory: List<ConversationAttemptHistoryItem>,
    val importedFromChatGptExport: Boolean,
    val importedFromClaudeExport: Boolean,
    val importedFromChatGptZip: Boolean,
    val unreadConversationIds: Set<com.nanzhufeng.ai.domain.ConversationId>,
)

/** P3-D observes persisted snapshots; it neither parses chunks nor owns drafts or runtime truth. */
class ConversationFoundationViewModel(
    private val repository: ConversationRepository,
    private val createConversation: CreateConversationUseCase,
    private val appendMessage: AppendConversationMessageUseCase,
    private val editUserMessage: EditConversationUserMessageUseCase,
    private val saveDraft: SaveConversationDraftUseCase,
    private val submitDraft: SubmitConversationDraftUseCase,
    private val renderer: MessagePresentationRenderer,
    private val startLocalRuntime: StartLocalConversationRuntimeUseCase,
    private val applyRuntimeEvent: ApplyConversationRuntimeEventUseCase,
    private val fixture: DeterministicFixtureStreamingAdapter,
    private val actions: ConversationActionOrchestrator,
    private val switchBranch: SwitchConversationBranchUseCase,
    private val manageConversation: ManageConversationUseCase,
    private val searchConversations: SearchConversationsUseCase,
    private val searchConversationAttachments: SearchConversationAttachmentsUseCase,
    private val searchHistory: LocalSearchHistoryStore,
    private val conversationReadMarkerStore: ConversationReadMarkerStore,
    private val exportConversation: ExportConversationPackageUseCase,
    private val galleryReader: AndroidGallerySelectionReader,
    private val documentReader: AndroidDocumentSelectionReader,
    private val addAttachment: AddConversationImageAttachmentUseCase,
    private val removeAttachment: RemoveConversationAttachmentUseCase,
    private val attachmentPreview: ConversationAttachmentPreviewProjection,
    private val pdfPreviewPosition: PdfPreviewPositionStore,
    private val videoPreviewPosition: VideoPreviewPositionStore,
    private val audioPreviewPosition: AudioPreviewPositionStore,
    private val readAttemptHistory: ReadConversationAttemptHistoryUseCase,
    private val temporary: TemporaryConversationDomain,
    private val addTemporaryAttachment: AddTemporaryConversationAttachmentUseCase,
    private val clearTemporary: ClearTemporaryConversationUseCase,
    private val p6gModelSelection: P6GModelSelectionOwner,
    private val conversationWebSearchOverrides: ConversationWebSearchOverrideOwner,
    private val loadAssistantExperienceSettings: LoadAssistantExperienceSettingsUseCase,
    private val invocations: InvocationRepository,
    private val responseModelAttributions: AssistantResponseModelAttributionStore,
    private val contextSelectionAudits: ContextSelectionAuditStore,
    private val normalChatOpenRouterExecutor: NormalChatOpenRouterExecutor,
    private val normalChatBackgroundExecution: NormalChatBackgroundExecution = NoopNormalChatBackgroundExecution,
    private val startWithFreshChat: Boolean = false,
) : ViewModel() {
    private var streamJob: Job? = null
    private var searchInputGeneration = 0L
    /** Search keeps the full lightweight catalogue; only composed rows request local bytes. */
    private val searchPreviewRequests = mutableSetOf<AttachmentId>()
    private var currentAttachmentReferences: Map<AttachmentId, ConversationAttachmentReference> = emptyMap()
    private var temporaryAttachmentReferences: Map<AttachmentId, ConversationAttachmentReference> = emptyMap()
    private var draftSaveGeneration = 0L
    /** Serializes draft writes and send so the text under the send button is the text committed. */
    private val draftMutationMutex = Mutex()
    private var reloadGeneration = 0L
    private var surfaceRequestGeneration = 0L
    private var selectedChatConversationId: com.nanzhufeng.ai.domain.ConversationId? = null
    private var selectedWorkConversationId: com.nanzhufeng.ai.domain.ConversationId? = null
    private var appBackgroundElapsedRealtime: Long? = null
    var state by mutableStateOf(ConversationFoundationUiState())
        private set

    init {
        // P6-E maintenance is owner-clock based and runs on process entry; it never scans normal conversations.
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                clearTemporary.pruneExpired()
                temporary.readRecovery()
            }
        }
        if (startWithFreshChat) openFreshChatForAppEntry() else reload()
    }

    /** Process start always lands on a reusable blank chat or creates one; history stays in the drawer. */
    private fun openFreshChatForAppEntry() {
        if (state.isCreating) return
        state = state.copy(
            isCreating = true,
            surface = ConversationSurface.CHAT,
            listScope = ConversationListScope.ACTIVE,
            notice = null,
        )
        viewModelScope.launch {
            val reusableEmpty = withContext(Dispatchers.IO) {
                val chats = (repository as? ConversationListRepository)?.list(ConversationListScope.ACTIVE)
                    ?: repository.listActive()
                chats
                    .asSequence()
                    .filter { it.surface == ConversationSurface.CHAT }
                    .sortedByDescending { it.updatedAt }
                    .firstOrNull { chat -> repository.findById(chat.id)?.nodes?.isEmpty() == true }
            }
            val selected = reusableEmpty?.id ?: when (val result = withContext(Dispatchers.IO) {
                createConversation.execute(surface = ConversationSurface.CHAT)
            }) {
                is ConversationMutationResult.Saved -> result.snapshot.conversation.id
                is ConversationMutationResult.Rejected -> null
            }
            if (selected == null) {
                state = state.copy(isCreating = false, notice = "新对话未能在本机创建。")
                reload(targetSurface = ConversationSurface.CHAT, selectedBefore = null)
                return@launch
            }
            selectedChatConversationId = selected
            state = state.copy(selectedConversationId = selected, isCreating = false)
            reload(targetSurface = ConversationSurface.CHAT, selectedBefore = selected)
        }
    }

    fun onAppBackground(nowElapsedRealtime: Long) {
        appBackgroundElapsedRealtime = nowElapsedRealtime
    }

    /** Returns true only when the background gap requires the Activity to reveal a fresh chat. */
    fun onAppForeground(nowElapsedRealtime: Long): Boolean {
        val backgroundAt = appBackgroundElapsedRealtime ?: return false
        appBackgroundElapsedRealtime = null
        if (state.isSending || state.selectedConversationId?.let(normalChatBackgroundExecution::isRunning) == true) {
            reload(keepSending = true)
            return false
        }
        if (nowElapsedRealtime - backgroundAt < FRESH_CHAT_AFTER_BACKGROUND_MS) return false
        openFreshChatForAppEntry()
        return true
    }

    fun reload(
        notice: String? = null,
        keepSending: Boolean = false,
        targetSurface: ConversationSurface = state.surface,
        selectedBefore: com.nanzhufeng.ai.domain.ConversationId? = when (targetSurface) {
            ConversationSurface.CHAT -> selectedChatConversationId ?: state.selectedConversationId
            ConversationSurface.WORK -> selectedWorkConversationId ?: state.selectedConversationId
        },
        requestedSurfaceGeneration: Long? = null,
    ) {
        val reloadRequest = ++reloadGeneration
        val surface = targetSurface
        val selectedIdBeforeLoad = selectedBefore
        state = state.copy(isLoading = true, notice = notice ?: state.notice)
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                val conversations = (repository as? ConversationListRepository)?.list(state.listScope) ?: repository.listActive()
                val surfaceConversations = when (surface) {
                    ConversationSurface.CHAT -> conversations
                    ConversationSurface.WORK -> (repository as? ConversationSurfaceRepository)?.listActive(ConversationSurface.WORK).orEmpty()
                }
                val selected = selectedIdBeforeLoad?.let { repository.findById(it)?.conversation }
                    ?.takeIf { it.surface == surface }
                    ?: surfaceConversations.firstOrNull { it.id == selectedIdBeforeLoad } ?: surfaceConversations.firstOrNull()
                val snapshot = selected?.let { repository.findById(it.id) }
                val persistedRuntime = snapshot?.let { (repository as? ConversationRuntimeRepository)?.stateFor(it.conversation.id) }
                // A completed sibling is historical runtime evidence, not an action surface for a
                // newer current user leaf. Only the current-path leaf may expose P3-B/C actions.
                val runtime = persistedRuntime?.takeIf { active -> snapshot?.conversation?.currentLeafMessageId == active.messageId }
                val lineage = runtime?.let { (repository as? ConversationActionRepository)?.lineageForInvocation(it.invocationId) }
                val lineages = snapshot?.conversation?.id?.let { id ->
                    (repository as? ConversationActionRepository)?.lineagesForConversation(id)
                }.orEmpty()
                val attemptHistory = snapshot?.let(readAttemptHistory::execute).orEmpty()
                val provenance = repository as? ImportedConversationProvenanceReader
                val chatGptImported = snapshot?.conversation?.id?.let { id -> provenance?.isChatGptExportImported(id) ?: false } ?: false
                val claudeImported = snapshot?.conversation?.id?.let { id -> provenance?.isClaudeExportImported(id) ?: false } ?: false
                val chatGptZipImported = snapshot?.conversation?.id?.let { id -> provenance?.isP6KZipImported(id) ?: false } ?: false
                // The drawer, canvas and selection must consume the same surface projection.
                // Returning the broad CHAT list here used to make the WORK drawer display normal
                // conversations even though the selected transcript itself was correctly WORK.
                val selectedId = snapshot?.conversation?.id
                val unreadConversationIds = if (!conversationReadMarkerStore.isInitialized()) {
                    surfaceConversations.forEach { conversation ->
                        conversationReadMarkerStore.markRead(conversation.id, conversation.updatedAt.toEpochMilli())
                    }
                    conversationReadMarkerStore.markInitialized()
                    emptySet()
                } else {
                    snapshot?.conversation?.let { conversation ->
                        conversationReadMarkerStore.markRead(conversation.id, conversation.updatedAt.toEpochMilli())
                    }
                    surfaceConversations.asSequence()
                        .filter { conversation -> conversation.id != selectedId }
                        .filter { conversation ->
                            (conversationReadMarkerStore.lastReadAtEpochMs(conversation.id) ?: Long.MAX_VALUE) < conversation.updatedAt.toEpochMilli()
                        }
                        .map(Conversation::id)
                        .toSet()
                }
                LoadedConversation(surfaceConversations, snapshot, runtime, lineage, lineages, attemptHistory, chatGptImported, claudeImported, chatGptZipImported, unreadConversationIds)
            }
            val conversations = loaded.conversations
            val snapshot = loaded.snapshot
            val runtime = loaded.runtime
            val lineage = loaded.lineage
            val lineages = loaded.lineages
            val attemptHistory = loaded.attemptHistory
            val unreadConversationIds = loaded.unreadConversationIds
            val path = snapshot?.let { MessageTree(it.conversation, it.nodes).contextPath() }.orEmpty()
            val invocationById = withContext(Dispatchers.IO) {
                path.mapNotNull { node ->
                    node.invocation?.invocationId?.let { invocationId ->
                        invocations.findById(invocationId)?.let { invocationId to it }
                    }
                }.toMap()
            }
            val responseAttributions = withContext(Dispatchers.IO) {
                responseModelAttributions.forMessages(path.map(MessageNode::id))
            }
            val answerContextSelections = withContext(Dispatchers.IO) {
                contextSelectionAudits.forAssistantMessages(path.map(MessageNode::id))
            }
            val runtimeByMessage = runtime
                ?.let { persisted -> mapOf(persisted.messageId to persisted) }
                .orEmpty()
            val messages = ConversationTranscriptPresentation(renderer).render(
                path,
                lineages,
                invocationById,
                runtimeByMessage,
                responseAttributions,
            )
            val attachmentReferences = snapshot?.draft?.attachments.orEmpty() + path.flatMap { node ->
                node.content.filterIsInstance<ContentBlock.Attachment>().map { it.attachment }
            }
            val attachmentPreviews = withContext(Dispatchers.IO) {
                attachmentReferences.distinctBy { it.id }.associate { it.id to attachmentPreview.project(it) }
            }
            if (reloadRequest != reloadGeneration || (requestedSurfaceGeneration != null && requestedSurfaceGeneration != surfaceRequestGeneration)) return@launch
            currentAttachmentReferences = attachmentReferences.distinctBy { it.id }.associateBy { it.id }
            val currentLeaf = snapshot?.conversation?.currentLeafMessageId
            val leaves = snapshot?.let(ConversationBranchHistory::leaves)
                ?.map { leaf -> ConversationBranchUi(leaf.leafId, leaf.role, leaf.preview.take(22), leaf.revision, leaf.isCurrent) }
                .orEmpty()
            val editable = snapshot?.let(ConversationBranchHistory::editableUserMessages).orEmpty()
            val runtimeMessage = snapshot?.nodes?.firstOrNull { it.id == runtime?.messageId }
            val p6gCatalog = withContext(Dispatchers.IO) { p6gModelSelection.readCatalog() }
            val p6gGlobalDefault = withContext(Dispatchers.IO) { p6gModelSelection.readGlobalDefault() }
            val p6gConversationOverride = snapshot?.conversation?.id?.let { id ->
                withContext(Dispatchers.IO) { p6gModelSelection.readConversationOverride(id) }
            }
            val globalWebSearchEnabled = withContext(Dispatchers.IO) {
                loadAssistantExperienceSettings.execute().webSearchEnabled
            }
            val conversationWebSearchOverride = snapshot?.conversation?.id?.let { id ->
                withContext(Dispatchers.IO) { conversationWebSearchOverrides.read(id) }
            }
            val normalSendRecovery = snapshot?.conversation?.id?.let { id ->
                withContext(Dispatchers.IO) { normalChatOpenRouterExecutor.recoveryForConversation(id) }
            }
            when (surface) {
                ConversationSurface.CHAT -> selectedChatConversationId = snapshot?.conversation?.id
                ConversationSurface.WORK -> selectedWorkConversationId = snapshot?.conversation?.id
            }
            state = state.copy(
                surface = surface,
                isLoading = false, isCreating = false, isSending = keepSending,
                conversations = conversations, unreadConversationIds = unreadConversationIds, selectedConversationId = snapshot?.conversation?.id,
                currentProjectId = snapshot?.conversation?.projectId,
                runtime = runtime, messages = messages, draft = snapshot?.draft,
                answerContextSelections = answerContextSelections,
                recovery = ConversationRecoveryPresenter.present(runtime, runtimeMessage),
                currentLeafId = currentLeaf, branchLeaves = leaves, editableUserMessages = editable, lineage = lineage,
                attemptHistory = attemptHistory,
                attachmentPreviews = attachmentPreviews,
                imagePreview = null,
                pdfPreview = null,
                videoPreview = null,
                p6gCatalog = p6gCatalog,
                p6gGlobalDefault = p6gGlobalDefault,
                globalWebSearchEnabled = globalWebSearchEnabled,
                conversationWebSearchOverride = conversationWebSearchOverride,
                p6gConversationOverride = p6gConversationOverride,
                importedFromChatGptExport = loaded.importedFromChatGptExport,
                importedFromClaudeExport = loaded.importedFromClaudeExport,
                importedFromChatGptZip = loaded.importedFromChatGptZip,
                normalSendRecovery = normalSendRecovery,
                sendError = if (normalSendRecovery == null) state.sendError else null,
            )
        }
    }

    /** Service broadcasts contain only an opaque local conversation id and terminal/running state. */
    fun onNormalChatBackgroundExecutionStateChanged(
        conversationId: com.nanzhufeng.ai.domain.ConversationId,
        running: Boolean,
        safeResult: String? = null,
    ) {
        if (conversationId != state.selectedConversationId) return
        val error = safeResult?.toNormalChatBackgroundErrorLabel()
        state = state.copy(
            isSending = running,
            sendError = error ?: state.sendError,
            normalSendRetryInProgress = if (running) state.normalSendRetryInProgress else false,
        )
        reload(keepSending = running)
    }

    fun openImagePreview(id: AttachmentId) {
        val reference = (currentAttachmentReferences + temporaryAttachmentReferences)[id] ?: run {
            state = state.copy(notice = "该本地图片附件不可用。")
            return
        }
        if (!reference.mimeType.startsWith("image/")) {
            state = state.copy(notice = "该本地附件不是图片。")
            return
        }
        viewModelScope.launch {
            val preview = withContext(Dispatchers.IO) { attachmentPreview.original(reference) }
            state = state.copy(imagePreview = preview, notice = if (preview.bytes == null) preview.unavailableReason else "正在查看本地原图；不会外发。")
        }
    }

    fun closeImagePreview() { state = state.copy(imagePreview = null) }

    /** Search owns no conversation navigation for files: a normal tap opens the local file. */
    fun openSearchAttachment(reference: ConversationAttachmentReference) {
        when {
            reference.mimeType.startsWith("image/") -> viewModelScope.launch {
                val preview = withContext(Dispatchers.IO) { attachmentPreview.original(reference) }
                state = state.copy(imagePreview = preview, notice = if (preview.bytes == null) preview.unavailableReason else "正在查看本地原图；不会外发。")
            }
            reference.mimeType == "application/pdf" -> loadPdfPage(reference, pdfPreviewPosition.pageFor(reference.id))
            reference.mimeType == "video/mp4" -> viewModelScope.launch {
                val preview = withContext(Dispatchers.IO) { attachmentPreview.video(reference) }
                state = state.copy(videoPreview = preview.copy(positionMillis = videoPreviewPosition.positionFor(reference.id)), notice = if (preview.bytes == null && preview.open == null) preview.unavailableReason else "正在打开本地视频并自动播放。")
            }
            reference.mimeType in com.nanzhufeng.ai.domain.CONVERSATION_ALLOWED_AUDIO_MIME_TYPES -> viewModelScope.launch {
                val preview = withContext(Dispatchers.IO) { attachmentPreview.audio(reference) }
                state = state.copy(audioPreview = preview.copy(positionMillis = audioPreviewPosition.positionFor(reference.id)), notice = if (preview.bytes == null && preview.open == null) preview.unavailableReason else "正在打开本地音频并自动播放。")
            }
            else -> viewModelScope.launch {
                val preview = withContext(Dispatchers.IO) { attachmentPreview.text(reference) }
                state = state.copy(textPreview = preview, notice = if (preview.text == null) preview.unavailableReason else "正在安全读取本地文本；不会执行文件内容。")
            }
        }
    }

    fun requestAttachmentTransfer(id: AttachmentId, action: AttachmentTransferAction) {
        requestAttachmentTransfers(listOf(id), action)
    }

    /** Multi-image download is an explicit action from one AI result; sharing remains singular. */
    fun requestAttachmentTransfers(ids: List<AttachmentId>, action: AttachmentTransferAction) {
        val references = (currentAttachmentReferences + temporaryAttachmentReferences)
        val requestedIds = ids.distinct()
        if (requestedIds.isEmpty()) return
        viewModelScope.launch {
            val items = withContext(Dispatchers.IO) {
                requestedIds.mapNotNull { id ->
                    val reference = references[id] ?: return@mapNotNull null
                    when (val opened = attachmentPreview.openVerified(reference)) {
                        is AttachmentOpenResult.Opened -> AttachmentTransferItem(id, reference.displayName, reference.mimeType, opened.byteCount, opened.open)
                        is AttachmentOpenResult.Rejected -> null
                    }
                }
            }
            state = if (items.isEmpty()) {
                state.copy(notice = "所选本地附件不可用，无法${if (action == AttachmentTransferAction.DOWNLOAD) "下载" else "分享"}。")
            } else {
                val first = items.first()
                state.copy(
                    attachmentTransfer = AttachmentTransferRequest(first.id, action, first.displayName, first.mimeType, first.byteCount, first.open, items),
                    notice = null,
                )
            }
        }
    }

    fun consumeAttachmentTransfer(id: AttachmentId) {
        if (state.attachmentTransfer?.id == id) state = state.copy(attachmentTransfer = null)
    }

    /** One picker action fixes this conversation and persists the next-conversation default. */
    fun selectP6GModel(modelId: String?) {
        val conversationId = state.selectedConversationId ?: return
        val currentConversation = state.p6gConversationOverride ?: return
        val currentGlobal = state.p6gGlobalDefault
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                p6gModelSelection.setComposerModelSelection(
                    conversationId = conversationId,
                    modelId = modelId,
                    expectedConversationRevision = currentConversation.revision,
                    expectedGlobalRevision = currentGlobal.revision,
                ).also { applied ->
                    if (applied is P6GSelectionMutationResult.Applied) p6gModelSelection.evaluate(
                        conversationId,
                        P6GRouteRequest(P6GModelTier.BALANCED, exactHistoricalCacheHit = false, localSafeRequired = false, unknownCostConfirmed = false, contextTokens = null, budgetMicros = null),
                    )
                }
            }
            when (result) {
                is P6GSelectionMutationResult.Applied -> reload(
                    if (modelId == null) "已选择自动；当前内容可参与路由，后续新会话也沿用自动。"
                    else "已固定当前模型；后续新会话也沿用此选择，未读取 Key、未调用 Provider。",
                )
                P6GSelectionMutationResult.Conflict -> reload("模型选择已更新；请按最新本地版本重试。")
                P6GSelectionMutationResult.InvalidModel -> reload("该模型不在本地 catalog 中，未保存。")
                P6GSelectionMutationResult.PersistenceFailed -> reload("模型选择未保存；本地数据保持不变。")
            }
        }
    }

    fun setP6GGlobalDefault(tier: P6GModelTier?) {
        val current = state.p6gGlobalDefault
        viewModelScope.launch {
            when (withContext(Dispatchers.IO) { p6gModelSelection.setGlobalDefault(tier, current.revision) }) {
                is P6GSelectionMutationResult.Applied -> reload(if (tier == null) "已清除本地全局默认；自动策略使用请求档位。" else "已保存本地全局默认：${tier.name}。")
                P6GSelectionMutationResult.Conflict -> reload("全局默认已更新；请按最新本地版本重试。")
                P6GSelectionMutationResult.InvalidModel -> reload("无效的本地模型选择。")
                P6GSelectionMutationResult.PersistenceFailed -> reload("全局默认未保存；本地数据保持不变。")
            }
        }
    }

    /** The Composer action intentionally fixes only this conversation; global settings stay untouched. */
    fun setCurrentConversationWebSearchEnabled(enabled: Boolean) {
        val conversationId = state.selectedConversationId ?: return
        val current = state.conversationWebSearchOverride ?: return
        viewModelScope.launch {
            when (withContext(Dispatchers.IO) {
                conversationWebSearchOverrides.setEnabled(conversationId, enabled, current.revision)
            }) {
                is ConversationWebSearchOverrideMutationResult.Applied -> {
                    state = state.copy(notice = null)
                    reload()
                }
                ConversationWebSearchOverrideMutationResult.Conflict -> reload("当前对话联网状态已更新；请按最新状态重试。")
                ConversationWebSearchOverrideMutationResult.PersistenceFailed -> reload("当前对话联网状态未保存；本机设置保持不变。")
            }
        }
    }

    /** Adds one clearly-labelled LOCAL-only fixture so the selection owner can be accepted without a Provider. */
    fun installP6GLocalFixtureCatalog() {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val current = p6gModelSelection.readCatalog()
                val fixture = P6GCatalogCandidate(
                    providerFamily = P6GProviderFamily.LOCAL,
                    providerId = "local-fixture",
                    modelId = "local-p6g-fixture-balanced-v1",
                    displayName = "本地确定性 fixture（仅验收）",
                    tiers = setOf(P6GModelTier.BALANCED),
                    capabilities = setOf(P6GCapability.TEXT),
                    available = true,
                    knownCostMicros = 0,
                    latencyRank = 0,
                    contextWindowTokens = 32_768,
                )
                p6gModelSelection.replaceCatalog(
                    current.copy(
                        catalogVersion = "local-fixture-catalog-v1",
                        policyVersion = current.policyVersion + 1,
                        candidates = current.candidates.filterNot { it.modelId == fixture.modelId } + fixture,
                    ),
                )
            }
            when (result) {
                is P6GSelectionMutationResult.Applied -> reload("已保存本地确定性 fixture catalog；仅用于验收，不读取 Key、不配置 Provider、不会调用网络。")
                P6GSelectionMutationResult.Conflict -> reload("本地 catalog 已更新；请按最新版本重试。")
                P6GSelectionMutationResult.InvalidModel -> reload("本地 fixture catalog 无效，未保存。")
                P6GSelectionMutationResult.PersistenceFailed -> reload("本地 fixture catalog 未保存；数据保持不变。")
            }
        }
    }

    fun openPdfPreview(id: AttachmentId) {
        val reference = (currentAttachmentReferences + temporaryAttachmentReferences)[id] ?: run {
            state = state.copy(notice = "该本地 PDF 附件不可用。")
            return
        }
        if (reference.mimeType != "application/pdf") {
            state = state.copy(notice = "该本地附件不是 PDF。")
            return
        }
        loadPdfPage(reference, pdfPreviewPosition.pageFor(id))
    }

    fun openPdfPage(pageNumber: Int) {
        val preview = state.pdfPreview ?: return
        val reference = (currentAttachmentReferences + temporaryAttachmentReferences)[preview.id] ?: return
        loadPdfPage(reference, pageNumber)
    }

    private fun loadPdfPage(reference: ConversationAttachmentReference, pageNumber: Int) {
        viewModelScope.launch {
            val preview = withContext(Dispatchers.IO) { attachmentPreview.pdfPage(reference, pageNumber) }
            preview.page?.let { pdfPreviewPosition.savePage(reference.id, it.pageNumber) }
            state = state.copy(pdfPreview = preview, notice = if (preview.page == null) preview.unavailableReason else "正在阅读本地 PDF；不会外发。")
        }
    }

    fun closePdfPreview() { state = state.copy(pdfPreview = null) }

    fun openVideoPreview(id: AttachmentId) {
        val reference = (currentAttachmentReferences + temporaryAttachmentReferences)[id] ?: run { state = state.copy(notice = "该本地视频附件不可用。"); return }
        if (reference.mimeType != "video/mp4") { state = state.copy(notice = "该本地附件不是受支持的视频。"); return }
        viewModelScope.launch {
            val preview = withContext(Dispatchers.IO) { attachmentPreview.video(reference) }
            state = state.copy(videoPreview = preview.copy(positionMillis = videoPreviewPosition.positionFor(id)), notice = if (preview.bytes == null && preview.open == null) preview.unavailableReason else "正在打开本地视频并自动播放。")
        }
    }

    fun closeVideoPreview(positionMillis: Long) {
        state.videoPreview?.id?.let { videoPreviewPosition.savePosition(it, positionMillis) }
        state = state.copy(videoPreview = null)
    }

    fun openAudioPreview(id: AttachmentId) {
        val reference = (currentAttachmentReferences + temporaryAttachmentReferences)[id] ?: run { state = state.copy(notice = "该本地音频附件不可用。"); return }
        if (reference.mimeType !in com.nanzhufeng.ai.domain.CONVERSATION_ALLOWED_AUDIO_MIME_TYPES) { state = state.copy(notice = "该本地附件不是受支持的音频。"); return }
        viewModelScope.launch {
            val preview = withContext(Dispatchers.IO) { attachmentPreview.audio(reference) }
            state = state.copy(audioPreview = preview.copy(positionMillis = audioPreviewPosition.positionFor(id)), notice = if (preview.bytes == null && preview.open == null) preview.unavailableReason else "正在打开本地音频并自动播放。")
        }
    }

    fun closeAudioPreview(positionMillis: Long) {
        state.audioPreview?.id?.let { audioPreviewPosition.savePosition(it, positionMillis) }
        state = state.copy(audioPreview = null)
    }

    fun openTextPreview(id: AttachmentId) {
        val reference = (currentAttachmentReferences + temporaryAttachmentReferences)[id] ?: run { state = state.copy(notice = "该本地文本附件不可用。"); return }
        viewModelScope.launch {
            val preview = withContext(Dispatchers.IO) { attachmentPreview.text(reference) }
            state = state.copy(textPreview = preview, notice = if (preview.text == null) preview.unavailableReason else "正在安全读取本地文本；不会执行文件内容。")
        }
    }

    fun closeTextPreview() { state = state.copy(textPreview = null) }

    fun savedVideoPosition(id: AttachmentId): Long = videoPreviewPosition.positionFor(id)

    private suspend fun applyTemporaryRecovery(recovery: TemporaryConversationRecovery, notice: String?) {
        val previousIds = temporaryAttachmentReferences.keys
        val references = withContext(Dispatchers.IO) {
            recovery.draftAttachmentIds.mapNotNull { id -> attachmentPreview.referenceFor(id) }.associateBy { it.id }
        }
        val previews = withContext(Dispatchers.IO) {
            references.values.associate { reference -> reference.id to attachmentPreview.project(reference) }
        }
        temporaryAttachmentReferences = references
        state = state.copy(
            temporaryRecovery = recovery,
            attachmentPreviews = (state.attachmentPreviews - previousIds) + previews,
            notice = notice,
        )
    }

    fun enterTemporaryConversation() {
        viewModelScope.launch {
            val recovery = withContext(Dispatchers.IO) { temporary.enterOrRestore() }
            applyTemporaryRecovery(recovery, TemporaryConversationIsolation.entryNotice())
        }
    }

    fun updateTemporaryDraft(text: String) {
        val current = state.temporaryRecovery ?: return
        viewModelScope.launch {
            val recovery = withContext(Dispatchers.IO) { temporary.updateDraft(text, current.draftAttachmentIds) }
            applyTemporaryRecovery(recovery, null)
        }
    }

    fun removeTemporaryDraftAttachment(id: AttachmentId) {
        if (state.temporaryRecovery == null) return
        viewModelScope.launch {
            val recovery = withContext(Dispatchers.IO) { temporary.removeDraftAttachment(id) }
            applyTemporaryRecovery(recovery, null)
        }
    }

    fun submitTemporaryDraft() {
        viewModelScope.launch {
            val recovery = withContext(Dispatchers.IO) { temporary.appendOfflineMessage() }
            applyTemporaryRecovery(recovery, TemporaryConversationIsolation.sentNotice())
        }
    }

    /** This opaque local field is not P6-G model selection and never calls a Provider. */
    fun updateTemporaryModelOverride(modelId: String?) {
        viewModelScope.launch {
            val recovery = withContext(Dispatchers.IO) {
                temporary.updateModelOverride(modelId?.trim()?.takeIf { it.isNotEmpty() })
            }
            applyTemporaryRecovery(recovery, "临时模型标识仅保存在本次临时会话；没有选择或调用模型。")
        }
    }

    /** Returning to NORMAL is a view-mode change only: the isolated record remains until expiry. */
    fun leaveTemporaryConversation() {
        if (state.temporaryRecovery == null) return
        val temporaryPreviewIds = temporaryAttachmentReferences.keys
        temporaryAttachmentReferences = emptyMap()
        state = state.copy(
            temporaryRecovery = null,
            attachmentPreviews = state.attachmentPreviews - temporaryPreviewIds,
            notice = "已返回普通聊天；临时内容仍可在 24 小时内继续。",
        )
    }

    fun onTemporaryVisualPickerResults(uris: List<Uri>) {
        onTemporaryAttachmentPickerResults(uris) { uri ->
            when (val opened = galleryReader.openConversationVisual(uri)) {
                is AndroidVisualAttachmentOpenResult.Opened -> PickedConversationAttachment.Opened(opened.selection)
                is AndroidVisualAttachmentOpenResult.Rejected -> PickedConversationAttachment.Rejected("图片或视频不可读取，临时草稿未改变。")
            }
        }
    }

    fun onTemporaryDocumentPickerResults(uris: List<Uri>) {
        onTemporaryAttachmentPickerResults(uris) { uri ->
            when (val opened = documentReader.open(uri)) {
                is AndroidDocumentOpenResult.Opened -> PickedConversationAttachment.Opened(opened.selection)
                AndroidDocumentOpenResult.Cancelled -> PickedConversationAttachment.Rejected("文件选择已取消，临时草稿未改变。")
                is AndroidDocumentOpenResult.Rejected -> PickedConversationAttachment.Rejected(opened.reason)
            }
        }
    }

    private fun onTemporaryAttachmentPickerResults(uris: List<Uri>, open: (Uri) -> PickedConversationAttachment) {
        viewModelScope.launch {
            if (uris.isEmpty()) {
                state = state.copy(notice = "已取消选择，临时草稿未改变。")
                return@launch
            }
            val outcome = withContext(Dispatchers.IO) {
                var added = 0
                var duplicates = 0
                var recovery: TemporaryConversationRecovery? = null
                val rejected = mutableListOf<String>()
                uris.forEach { uri ->
                    when (val picked = open(uri)) {
                        is PickedConversationAttachment.Rejected -> rejected += picked.reason
                        is PickedConversationAttachment.Opened -> when (val result = addTemporaryAttachment.add(picked.selection)) {
                            is TemporaryAttachmentResult.Added -> {
                                recovery = result.recovery
                                if (result.wasAlreadyAttached) duplicates += 1 else added += 1
                            }
                            TemporaryAttachmentResult.Cancelled -> Unit
                            is TemporaryAttachmentResult.Rejected -> rejected += result.reason
                        }
                    }
                }
                AttachmentBatchOutcome(added, duplicates, rejected, recovery)
            }
            val notice = attachmentBatchNotice(outcome, "临时会话")
            outcome.temporaryRecovery?.let { applyTemporaryRecovery(it, notice) } ?: run { state = state.copy(notice = notice) }
        }
    }

    private fun attachmentBatchNotice(outcome: AttachmentBatchOutcome, destination: String): String = when {
        outcome.addedCount > 0 -> buildString {
            append("已加入 ${outcome.addedCount} 项附件到${destination}，仅本地保存。")
            if (outcome.duplicateCount > 0) append(" ${outcome.duplicateCount} 项已存在。")
            if (outcome.rejectionReasons.isNotEmpty()) append(" 另有 ${outcome.rejectionReasons.size} 项未加入：${outcome.rejectionReasons.first()}")
        }
        outcome.duplicateCount > 0 -> "所选附件已在${destination}草稿中，未重复添加。"
        else -> outcome.rejectionReasons.firstOrNull() ?: "已取消选择，草稿未改变。"
    }

    fun onConversationVisualPickerResults(uris: List<Uri>) {
        onConversationAttachmentPickerResults(uris) { uri ->
            when (val opened = galleryReader.openConversationVisual(uri)) {
                is AndroidVisualAttachmentOpenResult.Opened -> PickedConversationAttachment.Opened(opened.selection)
                is AndroidVisualAttachmentOpenResult.Rejected -> PickedConversationAttachment.Rejected("图片或视频不可读取；当前会话草稿未改变。")
            }
        }
    }

    fun onConversationDocumentPickerResults(uris: List<Uri>) {
        onConversationAttachmentPickerResults(uris) { uri ->
            when (val opened = documentReader.open(uri)) {
                is AndroidDocumentOpenResult.Opened -> PickedConversationAttachment.Opened(opened.selection)
                AndroidDocumentOpenResult.Cancelled -> PickedConversationAttachment.Rejected("文件选择已取消，当前会话草稿未改变。")
                is AndroidDocumentOpenResult.Rejected -> PickedConversationAttachment.Rejected(opened.reason)
            }
        }
    }

    /** TEMP camera imports the original TakePicture bytes before deleting the app-private capture source. */
    fun onTemporaryCameraResult(uri: Uri?, captured: Boolean) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                if (!captured || uri == null) {
                    galleryReader.discardAppPrivateCapture(uri)
                    return@withContext TemporaryAttachmentResult.Cancelled
                }
                try {
                    when (val opened = galleryReader.openConversationVisual(uri)) {
                        is AndroidVisualAttachmentOpenResult.Opened -> addTemporaryAttachment.add(opened.selection)
                        is AndroidVisualAttachmentOpenResult.Rejected -> TemporaryAttachmentResult.Rejected("相机原图不可读取，临时草稿未改变。")
                    }
                } finally {
                    galleryReader.discardAppPrivateCapture(uri)
                }
            }
            when (result) {
                is TemporaryAttachmentResult.Added -> applyTemporaryRecovery(result.recovery, "相机原图已加入临时会话，仅本地引用。")
                TemporaryAttachmentResult.Cancelled -> state = state.copy(notice = "已取消拍照，临时草稿没有改变。")
                is TemporaryAttachmentResult.Rejected -> state = state.copy(notice = result.reason)
            }
        }
    }

    fun reportCameraCaptureUnavailable(temporary: Boolean) {
        state = state.copy(
            notice = if (temporary) {
                "无法创建本机相机临时文件，临时草稿没有改变。"
            } else {
                "无法创建本机相机临时文件，当前会话草稿没有改变。"
            },
        )
    }

    fun onTemporaryDocumentPickerResult(uri: Uri?) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                when (val opened = documentReader.open(uri)) {
                    AndroidDocumentOpenResult.Cancelled -> TemporaryAttachmentResult.Cancelled
                    is AndroidDocumentOpenResult.Rejected -> TemporaryAttachmentResult.Rejected(opened.reason)
                    is AndroidDocumentOpenResult.Opened -> addTemporaryAttachment.add(opened.selection)
                }
            }
            when (result) {
                is TemporaryAttachmentResult.Added -> applyTemporaryRecovery(result.recovery, "文件已加入临时会话，仅本地引用。")
                TemporaryAttachmentResult.Cancelled -> state = state.copy(notice = "已取消选择，临时草稿未改变。")
                is TemporaryAttachmentResult.Rejected -> state = state.copy(notice = result.reason)
            }
        }
    }

    fun selectConversation(id: com.nanzhufeng.ai.domain.ConversationId) {
        if (id == state.selectedConversationId) return
        when (state.surface) {
            ConversationSurface.CHAT -> selectedChatConversationId = id
            ConversationSurface.WORK -> selectedWorkConversationId = id
        }
        state = state.copy(selectedConversationId = id, unreadConversationIds = state.unreadConversationIds - id)
        reload(targetSurface = state.surface, selectedBefore = id)
    }

    /** A launcher shortcut may reopen only an existing, active local conversation. */
    fun openConversationShortcut(id: com.nanzhufeng.ai.domain.ConversationId) {
        viewModelScope.launch {
            val conversation = withContext(Dispatchers.IO) { repository.findById(id)?.conversation }
            when {
                conversation == null -> state = state.copy(notice = "该对话已不存在，未打开。")
                conversation.deletedAt != null -> state = state.copy(notice = "该对话已在回收站，未从桌面快捷方式打开。")
                conversation.archivedAt != null -> state = state.copy(notice = "该对话已归档，请先在对话管理中恢复。")
                else -> {
                    when (conversation.surface) {
                        ConversationSurface.CHAT -> selectedChatConversationId = conversation.id
                        ConversationSurface.WORK -> selectedWorkConversationId = conversation.id
                    }
                    state = state.copy(
                        surface = conversation.surface,
                        selectedConversationId = conversation.id,
                        listScope = ConversationListScope.ACTIVE,
                    )
                    reload(targetSurface = conversation.surface, selectedBefore = conversation.id)
                }
            }
        }
    }

    /**
     * The visual transcript is shared, while this switch changes its persisted content owner.
     * Work is a dedicated local conversation surface, never a projection of the selected chat.
     */
    fun selectSurface(surface: ConversationSurface) {
        val request = ++surfaceRequestGeneration
        val selectedBefore = when (surface) {
            ConversationSurface.CHAT -> selectedChatConversationId
            ConversationSurface.WORK -> selectedWorkConversationId
        }
        // A second tap on the surface that is still visible cancels an in-flight swap instead
        // of allowing its stale background read to replace the canvas afterwards.
        if (state.surface == surface && !state.isLoading) {
            state = state.copy(isCreating = false, notice = null)
            return
        }
        if (surface == ConversationSurface.WORK && selectedBefore == null) {
            state = state.copy(isCreating = true)
            viewModelScope.launch {
                val existing = withContext(Dispatchers.IO) {
                    (repository as? ConversationSurfaceRepository)
                        ?.listActive(ConversationSurface.WORK)
                        ?.firstOrNull()
                }
                if (request != surfaceRequestGeneration) return@launch
                if (existing != null) {
                    selectedWorkConversationId = existing.id
                    state = state.copy(isCreating = false)
                    reload(targetSurface = ConversationSurface.WORK, selectedBefore = existing.id, requestedSurfaceGeneration = request)
                } else {
                    // WORK belongs to a user-created Project. Do not silently create an
                    // unassigned generic "工作" conversation merely by opening the surface.
                    state = state.copy(isCreating = false)
                    reload(targetSurface = ConversationSurface.WORK, selectedBefore = null, requestedSurfaceGeneration = request)
                }
            }
        } else {
            reload(targetSurface = surface, selectedBefore = selectedBefore, requestedSurfaceGeneration = request)
        }
    }

    fun setListScope(scope: ConversationListScope) {
        if (state.listScope == scope) return
        state = state.copy(listScope = scope, searchResults = emptyList(), attachmentSearchResults = emptyList(), searchAttachmentPreviews = emptyMap(), searchAttachmentTextPreviews = emptyMap(), searchQuery = "", searchPanelOpen = false)
        reload()
    }

    fun updateSearchQuery(query: String) {
        val generation = ++searchInputGeneration
        searchPreviewRequests.clear()
        state = state.copy(
            searchQuery = query,
            searchResults = emptyList(),
            attachmentSearchResults = emptyList(),
            searchAttachmentPreviews = emptyMap(),
            searchAttachmentTextPreviews = emptyMap(),
            searchPanelOpen = false,
        )
        viewModelScope.launch {
            if (query.isNotBlank()) delay(180)
            if (generation == searchInputGeneration && state.searchQuery == query) submitSearch()
        }
    }

    fun selectSearchCategory(category: ConversationSearchCategory) {
        if (state.searchCategory == category) return
        searchInputGeneration += 1
        searchPreviewRequests.clear()
        state = state.copy(searchCategory = category, searchResults = emptyList(), attachmentSearchResults = emptyList(), searchAttachmentPreviews = emptyMap(), searchAttachmentTextPreviews = emptyMap(), searchPanelOpen = false)
        submitSearch()
    }

    fun openSearchHistory() { state = state.copy(searchHistory = searchHistory.recent(state.listScope), searchHistoryOpen = true) }
    fun closeSearchHistory() { state = state.copy(searchHistoryOpen = false) }
    fun fillSearchHistory(query: String) { state = state.copy(searchQuery = query, searchHistoryOpen = false) }
    fun clearSearchHistory() { searchHistory.clear(state.listScope); state = state.copy(searchHistory = emptyList()) }

    /** Opening and category changes browse local records; typing debounces the same local filter. */
    fun submitSearch() {
        val query = state.searchQuery
        val browsing = query.isBlank()
        val category = state.searchCategory
        viewModelScope.launch {
            val scope = state.listScope
            val results = withContext(Dispatchers.IO) {
                if (category == ConversationSearchCategory.TEXT || category == ConversationSearchCategory.ALL) {
                    if (browsing) searchConversations.browse(scope) else searchConversations.execute(query, scope)
                } else emptyList()
            }
            val attachments = withContext(Dispatchers.IO) {
                if (browsing) searchConversationAttachments.browse(category, scope)
                else searchConversationAttachments.execute(query, category, scope)
            }
            if (state.searchQuery == query && state.searchCategory == category) {
                if (!browsing) searchHistory.record(query, scope)
                searchPreviewRequests.clear()
                state = state.copy(
                    searchResults = results,
                    attachmentSearchResults = attachments,
                    searchAttachmentPreviews = emptyMap(),
                    searchAttachmentTextPreviews = emptyMap(),
                    searchPanelOpen = false,
                    searchHistoryOpen = false,
                    searchHistory = searchHistory.recent(scope),
                )
            }
        }
    }

    /** A Lazy list/grid calls this only for composed attachment rows. Full catalogue discovery
     * remains metadata-only, while visible images, video posters and file excerpts become useful. */
    fun ensureSearchAttachmentPreview(reference: ConversationAttachmentReference) {
        if (state.searchAttachmentPreviews.containsKey(reference.id) || !searchPreviewRequests.add(reference.id)) return
        viewModelScope.launch {
            try {
                val preview = withContext(Dispatchers.IO) { attachmentPreview.project(reference) }
                if (state.attachmentSearchResults.any { it.attachment.id == reference.id }) {
                    state = state.copy(
                        searchAttachmentPreviews = state.searchAttachmentPreviews + (reference.id to preview),
                        searchAttachmentTextPreviews = preview.textPreview?.let { text ->
                            state.searchAttachmentTextPreviews + (reference.id to text)
                        } ?: state.searchAttachmentTextPreviews,
                    )
                }
            } finally {
                searchPreviewRequests.remove(reference.id)
            }
        }
    }

    fun closeSearchPanel() { state = state.copy(searchPanelOpen = false) }

    fun openSearchHit(hit: ConversationSearchHit) {
        selectedChatConversationId = hit.conversationId
        state = state.copy(surface = ConversationSurface.CHAT, selectedConversationId = hit.conversationId, searchPanelOpen = false, searchAnchorMessageId = hit.messageNodeId)
        reload()
    }

    fun manageCurrent(action: ConversationManagementAction, title: String? = null) {
        val conversation = state.conversations.firstOrNull { it.id == state.selectedConversationId } ?: return
        manage(conversation, action, title)
    }

    fun manage(conversation: Conversation, action: ConversationManagementAction, title: String? = null, projectId: String? = null) {
        viewModelScope.launch {
            when (val result = withContext(Dispatchers.IO) {
                manageConversation.execute(ConversationManagementIntent(ConversationManagementIntentId.new(), conversation.id, action, conversation.revision, title, projectId))
            }) {
                is ConversationManagementResult.Applied -> reload(managementNotice(action, result.snapshot.conversation.title))
                is ConversationManagementResult.Replayed -> reload("已回读相同本地管理操作。")
                is ConversationManagementResult.Rejected -> state = state.copy(notice = result.reason)
            }
        }
    }

    fun permanentlyDelete(conversation: Conversation) {
        viewModelScope.launch {
            when (val result = withContext(Dispatchers.IO) { repository.permanentlyDelete(conversation.id, conversation.revision) }) {
                ConversationPurgeResult.Deleted -> reload("已永久删除会话。")
                is ConversationPurgeResult.Rejected -> state = state.copy(notice = result.reason)
            }
        }
    }

    /** Bulk recycle-bin cleanup reuses the same irreversible owner as a single permanent delete. */
    fun permanentlyDeleteConversations(conversations: List<Conversation>) {
        val targets = conversations.distinctBy { it.id.value }
        if (targets.isEmpty()) return
        viewModelScope.launch {
            val outcomes = withContext(Dispatchers.IO) {
                targets.map { conversation -> repository.permanentlyDelete(conversation.id, conversation.revision) }
            }
            val completed = outcomes.count { it is ConversationPurgeResult.Deleted }
            val rejected = outcomes.filterIsInstance<ConversationPurgeResult.Rejected>()
            if (completed > 0) {
                val notice = if (rejected.isEmpty()) {
                    "已永久删除 $completed 个回收站会话。"
                } else {
                    "已永久删除 $completed 个回收站会话；${rejected.size} 个未完成：${rejected.first().reason}"
                }
                reload(notice)
            } else {
                state = state.copy(notice = "回收站未清空：${rejected.firstOrNull()?.reason ?: "本机操作未完成。"}")
            }
        }
    }

    /**
     * The drawer's batch editor never bypasses the existing soft-delete owner.  Every
     * selected local conversation gets its own idempotent management intent so a
     * concurrent revision can be reported as a partial result instead of being hidden.
     */
    fun softDeleteConversations(conversations: List<Conversation>) {
        val targets = conversations.distinctBy { it.id.value }
        if (targets.isEmpty()) return
        viewModelScope.launch {
            val outcomes = withContext(Dispatchers.IO) {
                targets.map { conversation ->
                    manageConversation.execute(
                        ConversationManagementIntent(
                            id = ConversationManagementIntentId.new(),
                            conversationId = conversation.id,
                            action = ConversationManagementAction.SOFT_DELETE,
                            expectedRevision = conversation.revision,
                        ),
                    )
                }
            }
            val completed = outcomes.count {
                it is ConversationManagementResult.Applied || it is ConversationManagementResult.Replayed
            }
            val rejected = outcomes.filterIsInstance<ConversationManagementResult.Rejected>()
            if (completed > 0) {
                val notice = if (rejected.isEmpty()) {
                    "已将 $completed 个会话移入回收站。"
                } else {
                    "已将 $completed 个会话移入回收站；${rejected.size} 个未完成：${rejected.first().reason}"
                }
                reload(notice)
            } else {
                state = state.copy(notice = "所选会话未移入回收站：${rejected.firstOrNull()?.reason ?: "本机操作未完成。"}")
            }
        }
    }

    fun assignProject(conversation: Conversation, projectId: com.nanzhufeng.ai.domain.ProjectId?) =
        manage(conversation, if (projectId == null) ConversationManagementAction.REMOVE_PROJECT else ConversationManagementAction.ASSIGN_PROJECT, projectId = projectId?.value)

    fun exportCurrentConversation() {
        val id = state.selectedConversationId ?: return
        viewModelScope.launch {
            when (val result = withContext(Dispatchers.IO) { exportConversation.execute(id) }) {
                is ConversationExportResult.Success -> state = state.copy(notice = "已导出 ${result.export.fileName} · ${result.export.sha256.take(12)} · 当前路径")
                ConversationExportResult.MissingConversation -> state = state.copy(notice = "会话未能从本机回读，未导出。")
                ConversationExportResult.Cancelled -> state = state.copy(notice = "导出已取消，未留下成功文件。")
                is ConversationExportResult.Failed -> state = state.copy(notice = "导出未完成，未报告为成功。")
            }
        }
    }

    fun createDevelopmentConversation() {
        if (state.isCreating) return
        val surface = state.surface
        // “新对话” is an explicit user request for a fresh conversation, not a request to
        // reopen an older empty draft.  Reset to the active list before loading so its new
        // updatedAt is projected as the first row under “最近”.
        state = state.copy(isCreating = true, notice = null, listScope = ConversationListScope.ACTIVE)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                // A normal conversation starts empty.  Deterministic fixtures remain explicit
                // task actions and are never silently written into a user-visible transcript.
                createConversation.execute(surface = surface)
            }
            when (result) {
                is ConversationMutationResult.Saved -> {
                    when (surface) {
                        ConversationSurface.CHAT -> selectedChatConversationId = result.snapshot.conversation.id
                        ConversationSurface.WORK -> selectedWorkConversationId = result.snapshot.conversation.id
                    }
                    state = state.copy(selectedConversationId = result.snapshot.conversation.id, isCreating = false)
                    reload(
                        notice = if (surface == ConversationSurface.CHAT) "已创建新对话；首条消息会在本机生成标题。" else "已创建独立工作内容。",
                        targetSurface = surface,
                        selectedBefore = result.snapshot.conversation.id,
                    )
                }
                is ConversationMutationResult.Rejected -> state = state.copy(isCreating = false, notice = result.reason)
            }
        }
    }

    /** A work conversation is born inside the explicitly selected local Project. */
    fun createWorkConversation(projectId: com.nanzhufeng.ai.domain.ProjectId) {
        if (state.isCreating) return
        state = state.copy(isCreating = true, notice = null)
        viewModelScope.launch {
            when (val result = withContext(Dispatchers.IO) {
                createConversation.execute(projectId = projectId.value, surface = ConversationSurface.WORK)
            }) {
                is ConversationMutationResult.Saved -> {
                    selectedWorkConversationId = result.snapshot.conversation.id
                    state = state.copy(isCreating = false, selectedConversationId = result.snapshot.conversation.id)
                    reload(
                        notice = "已在当前项目创建工作对话。",
                        targetSurface = ConversationSurface.WORK,
                        selectedBefore = result.snapshot.conversation.id,
                    )
                }
                is ConversationMutationResult.Rejected -> state = state.copy(isCreating = false, notice = result.reason)
            }
        }
    }

    fun updateDraft(text: String) {
        val id = state.selectedConversationId ?: return
        val current = state.draft ?: return
        if (state.isSending) return
        state = state.copy(
            draft = current.copy(text = text),
            sendError = null,
            notice = null,
        )
        val generation = ++draftSaveGeneration
        viewModelScope.launch {
            val result = draftMutationMutex.withLock {
                withContext(Dispatchers.IO) { saveDraft.execute(id, text, current.attachments) }
            }
            if (generation != draftSaveGeneration) return@launch
            when (result) {
                is ConversationDraftResult.Saved -> state = state.copy(draft = result.draft)
                is ConversationDraftResult.Rejected -> state = state.copy(notice = result.reason)
            }
        }
    }

    fun submitCurrentDraft() {
        val id = state.selectedConversationId ?: return
        val draft = state.draft ?: return
        if (state.isSending) return
        // Older text-save jobs may already be running. The mutex makes them finish before this
        // exact visible draft is persisted, so clicking send cannot submit an older empty draft.
        ++draftSaveGeneration
        state = state.copy(
            isSending = true,
            sendError = null,
            notice = null,
            normalSendRecovery = null,
            normalSendRetryInProgress = true,
        )
        viewModelScope.launch {
            if (state.surface == ConversationSurface.CHAT) {
                val saved = draftMutationMutex.withLock {
                    val saved = withContext(Dispatchers.IO) { saveDraft.execute(id, draft.text, draft.attachments) }
                    saved
                }
                if (saved !is ConversationDraftResult.Saved) {
                    state = state.copy(isSending = false, sendError = normalChatResultLabel(NormalChatOpenRouterExecutor.Code.DRAFT_UNAVAILABLE, sent = false))
                    return@launch
                }
                // Android production transfers the entire actual request to the foreground
                // service. The ViewModel never owns its socket or stream callbacks.
                if (normalChatBackgroundExecution.ownsExecution()) {
                    if (!normalChatBackgroundExecution.begin(id, NormalChatBackgroundOperation.SEND)) {
                        state = state.copy(isSending = false, sendError = "系统未能启动后台生成；本次没有向服务商发送内容。")
                    } else {
                        reload(keepSending = true)
                    }
                    return@launch
                }
                val result = withContext(Dispatchers.IO) {
                            normalChatOpenRouterExecutor.execute(
                                id,
                                onLocalSubmission = {
                                    // User and assistant placeholder are durable before the first
                                    // SSE chunk, so a failed send has a stable in-place target.
                                    viewModelScope.launch {
                                        if (state.isSending && state.selectedConversationId == id) reload(keepSending = true)
                                    }
                                },
                                onStreamProgress = {
                                    viewModelScope.launch {
                                        if (state.isSending && state.selectedConversationId == id) reload(keepSending = true)
                                    }
                                },
                            )
                }
                when (result) {
                    NormalChatOpenRouterExecutor.Result.Sent -> reload()
                    is NormalChatOpenRouterExecutor.Result.Blocked -> {
                        state = state.copy(isSending = false, sendError = normalChatResultLabel(result.code, sent = false))
                        reload()
                    }
                    is NormalChatOpenRouterExecutor.Result.Failed -> {
                        // Keep the durable recovery card, but also leave a direct error under
                        // the composer when a reload cannot project that card yet.
                        state = state.copy(isSending = false, sendError = normalChatResultLabel(result.code, sent = true))
                        reload()
                    }
                }
                return@launch
            }
            when (val result = draftMutationMutex.withLock {
                val saved = withContext(Dispatchers.IO) { saveDraft.execute(id, draft.text, draft.attachments) }
                if (saved !is ConversationDraftResult.Saved) ConversationDraftSubmissionResult.Rejected("草稿未能安全保存，本次没有发送。")
                else withContext(Dispatchers.IO) { submitDraft.execute(id) }
            }) {
                is ConversationDraftSubmissionResult.Submitted -> reload()
                is ConversationDraftSubmissionResult.Rejected -> state = state.copy(isSending = false, sendError = result.reason)
            }
        }
    }

    /** Explicitly replays the latest durable ordinary-chat attempt with its original key. */
    fun retryLatestNormalSend() {
        val id = state.selectedConversationId ?: return
        if (state.isSending || state.normalSendRecovery?.canRetry != true) return
        state = state.copy(isSending = true, sendError = null, notice = null)
        if (normalChatBackgroundExecution.ownsExecution()) {
            if (!normalChatBackgroundExecution.begin(id, NormalChatBackgroundOperation.RETRY)) {
                state = state.copy(
                    isSending = false,
                    sendError = "系统未能启动后台重试；本次没有向服务商发送内容。",
                    normalSendRetryInProgress = false,
                )
            } else {
                reload(keepSending = true)
            }
            return
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { normalChatOpenRouterExecutor.retryLatestAttempt(id) }
            when (result) {
                NormalChatOpenRouterExecutor.Result.Sent -> {
                    state = state.copy(normalSendRetryInProgress = false)
                    reload("已按原发送编号重试；未更换服务商或模型。")
                }
                is NormalChatOpenRouterExecutor.Result.Blocked -> {
                    state = state.copy(
                        isSending = false,
                        sendError = normalChatResultLabel(result.code, sent = false),
                        normalSendRetryInProgress = false,
                    )
                    reload()
                }
                is NormalChatOpenRouterExecutor.Result.Failed -> {
                    state = state.copy(
                        isSending = false,
                        sendError = normalChatResultLabel(result.code, sent = true),
                        normalSendRetryInProgress = false,
                    )
                    reload()
                }
            }
        }
    }

    fun markLatestNormalSendFailed() {
        val id = state.selectedConversationId ?: return
        viewModelScope.launch {
            val marked = withContext(Dispatchers.IO) { normalChatOpenRouterExecutor.markLatestAttemptFailed(id) }
            reload(if (marked) "已标记该次发送失败；历史记录和幂等编号仍保留在本机。" else "没有可标记的发送记录。")
        }
    }

    private fun onConversationAttachmentPickerResults(uris: List<Uri>, open: (Uri) -> PickedConversationAttachment) {
        val conversationId = state.selectedConversationId ?: return
        viewModelScope.launch {
            if (uris.isEmpty()) {
                state = state.copy(notice = "已取消选择，当前会话草稿没有改变。")
                return@launch
            }
            val outcome = withContext(Dispatchers.IO) {
                var added = 0
                var duplicates = 0
                val rejected = mutableListOf<String>()
                uris.forEach { uri ->
                    when (val picked = open(uri)) {
                        is PickedConversationAttachment.Rejected -> rejected += picked.reason
                        is PickedConversationAttachment.Opened -> when (val result = addAttachment.add(conversationId, picked.selection)) {
                            is AddConversationAttachmentResult.Added -> if (result.wasAlreadyAttached) duplicates += 1 else added += 1
                            AddConversationAttachmentResult.Cancelled -> Unit
                            is AddConversationAttachmentResult.Rejected -> rejected += result.reason
                        }
                    }
                }
                AttachmentBatchOutcome(added, duplicates, rejected)
            }
            if (outcome.addedCount > 0 || outcome.duplicateCount > 0) reload(attachmentBatchNotice(outcome, "当前会话"))
            else state = state.copy(notice = attachmentBatchNotice(outcome, "当前会话"))
        }
    }

    /** Camera imports the full TakePicture output; the preview-bitmap path is deliberately never used. */
    fun onConversationCameraResult(uri: Uri?, captured: Boolean) {
        val conversationId = state.selectedConversationId ?: run {
            galleryReader.discardAppPrivateCapture(uri)
            state = state.copy(notice = "当前会话不可用，相机原图未加入草稿。")
            return
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                if (!captured || uri == null) {
                    galleryReader.discardAppPrivateCapture(uri)
                    return@withContext AddConversationAttachmentResult.Cancelled
                }
                try {
                    when (val opened = galleryReader.openConversationVisual(uri)) {
                        is AndroidVisualAttachmentOpenResult.Opened -> addAttachment.add(conversationId, opened.selection)
                        is AndroidVisualAttachmentOpenResult.Rejected -> AddConversationAttachmentResult.Rejected("相机原图不可读取，当前会话草稿未改变。")
                    }
                } finally {
                    galleryReader.discardAppPrivateCapture(uri)
                }
            }
            when (result) {
                is AddConversationAttachmentResult.Added -> reload("相机原图已私有复制到本地草稿；不会发送给 AI 或第三方。")
                AddConversationAttachmentResult.Cancelled -> state = state.copy(notice = "已取消拍照，当前会话草稿没有改变。")
                is AddConversationAttachmentResult.Rejected -> state = state.copy(notice = result.reason)
            }
        }
    }

    fun removeDraftAttachment(id: AttachmentId) {
        val conversationId = state.selectedConversationId ?: return
        viewModelScope.launch {
            when (val result = withContext(Dispatchers.IO) { removeAttachment.execute(conversationId, id) }) {
                is RemoveConversationAttachmentResult.Removed -> reload("已从当前会话草稿移除图片；私有资产未删除。")
                is RemoveConversationAttachmentResult.Rejected -> state = state.copy(notice = result.reason)
            }
        }
    }

    fun startDeterministicLocalStream(fail: Boolean = false) {
        val conversationId = state.selectedConversationId ?: run { state = state.copy(notice = "请先创建本地会话。") ; return }
        streamJob?.cancel()
        viewModelScope.launch {
            when (val started = withContext(Dispatchers.IO) { startLocalRuntime.execute(conversationId) }) {
                is ConversationRuntimePersistenceResult.Applied -> startFixture(started.projection, "本地确定性流已启动；不会连接 Provider。", fail)
                is ConversationRuntimePersistenceResult.Rejected -> state = state.copy(notice = started.reason)
                is ConversationRuntimePersistenceResult.Replayed -> Unit
            }
        }
    }

    fun stopLocalStream() {
        val visibleRuntime = state.runtime ?: return
        if (visibleRuntime.isTerminal) return
        if (normalChatBackgroundExecution.ownsExecution()) normalChatBackgroundExecution.cancel(visibleRuntime.conversationId)
        else normalChatOpenRouterExecutor.cancelActive(visibleRuntime.conversationId)
        streamJob?.cancel()
        viewModelScope.launch {
            val runtime = withContext(Dispatchers.IO) { (repository as? ConversationRuntimeRepository)?.stateFor(visibleRuntime.conversationId) ?: visibleRuntime }
            if (runtime.isTerminal) { reload(); return@launch }
            val event = RuntimeCancelled(AiRuntimeEventId.new(), runtime.invocationId, runtime.conversationId, runtime.messageId,
                runtime.nextExpectedSequence, java.time.Instant.now(), AiRuntimeSecurityMetadata("LOCAL_USER_STOP"))
            when (val result = withContext(Dispatchers.IO) { applyRuntimeEvent.execute(event) }) {
                is ConversationRuntimePersistenceResult.Applied, is ConversationRuntimePersistenceResult.Replayed -> reload("已停止本地流；部分输出已保留。")
                is ConversationRuntimePersistenceResult.Rejected -> state = state.copy(notice = result.reason)
            }
        }
    }

    fun performAction(kind: ConversationActionKind, preset: ModelPresetId? = null) {
        val runtime = state.runtime ?: run { state = state.copy(notice = "当前没有可操作的本地 assistant。") ; return }
        if (!runtime.isTerminal) { state = state.copy(notice = "请先停止或等待当前本地流结束。") ; return }
        val source = state.currentLeafId ?: runtime.messageId
        viewModelScope.launch {
            when (val result = withContext(Dispatchers.IO) {
                actions.execute(ConversationActionRequest(ConversationActionIntentId.new(), runtime.conversationId, source, kind, preset))
            }) {
                is ConversationActionResult.Started -> startFixture(result.projection, "${result.lineage.actionKind.name} 已创建新的本地 Invocation；不会连接 Provider。")
                is ConversationActionResult.Replayed -> startFixture(result.projection, "已回读相同本地 intent；不会连接 Provider。")
                is ConversationActionResult.Rejected -> state = state.copy(notice = result.reason)
            }
        }
    }

    fun switchToBranch(leafId: MessageNodeId) {
        val conversation = state.selectedConversationId ?: return
        viewModelScope.launch {
            when (val result = withContext(Dispatchers.IO) { switchBranch.execute(repository.findById(conversation) ?: return@withContext null, leafId) }) {
                is ConversationMutationResult.Saved -> reload("已切换到保留的回答版本。")
                is ConversationMutationResult.Rejected -> state = state.copy(notice = result.reason)
                null -> state = state.copy(notice = "会话未能从本机回读。")
            }
        }
    }

    /** FB-P6-034: an assistant-owned branch action creates a new local conversation prefix. */
    fun branchFromMessage(messageId: MessageNodeId) {
        val conversationId = state.selectedConversationId ?: return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val source = repository.findById(conversationId) ?: return@withContext null
                val byId = source.nodes.associateBy { it.id }
                val target = byId[messageId] ?: return@withContext null
                if (target.role != MessageRole.ASSISTANT) return@withContext source
                val path = generateSequence(target) { node -> node.parentMessageId?.let(byId::get) }.toList().asReversed()
                val branchId = com.nanzhufeng.ai.domain.ConversationId.new()
                val ids = path.associate { it.id to MessageNodeId.new() }
                val now = java.time.Instant.now()
                val conversation = source.conversation.copy(
                    id = branchId,
                    title = "${source.conversation.title} · 分支",
                    currentLeafMessageId = ids.getValue(target.id),
                    createdAt = now,
                    updatedAt = now,
                    pinnedAt = null,
                    archivedAt = null,
                    deletedAt = null,
                    revision = 1L,
                )
                val nodes = path.map { node ->
                    node.copy(
                        id = ids.getValue(node.id),
                        conversationId = branchId,
                        parentMessageId = node.parentMessageId?.let(ids::get),
                        siblingPosition = 0,
                    )
                }
                repository.save(ConversationSnapshot(conversation, nodes, ConversationDraft(updatedAt = now)))
            }
            when {
                result == null -> state = state.copy(notice = "消息未能从本机回读，未创建分支。")
                result.conversation.id == conversationId -> state = state.copy(notice = "只有 assistant 消息可以创建分支。")
                else -> {
                    selectConversation(result.conversation.id)
                    state = state.copy(branchCreation = ConversationBranchCreationUi(result.conversation.id))
                }
            }
        }
    }

    fun dismissBranchCreation(branchConversationId: com.nanzhufeng.ai.domain.ConversationId) {
        if (state.branchCreation?.branchConversationId == branchConversationId) {
            state = state.copy(branchCreation = null)
        }
    }

    fun editCurrentPathUserMessage(messageId: MessageNodeId, text: String) {
        val conversationId = state.selectedConversationId ?: return
        if (text.isBlank()) { state = state.copy(notice = "编辑内容不能为空；原消息未改变。") ; return }
        if (state.editableUserMessages.none { it.messageId == messageId }) {
            state = state.copy(notice = "该消息不在当前可编辑路径，原分支未改变。")
            return
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val snapshot = repository.findById(conversationId) ?: return@withContext null
                if (ConversationBranchHistory.editableUserMessages(snapshot).none { it.messageId == messageId }) return@withContext null
                editUserMessage.execute(snapshot, messageId, listOf(ContentBlock.Text(text)))
            }
            when (result) {
                is ConversationMutationResult.Saved -> reload("已在本机创建修订分支；原消息保持不变，未连接 Provider。")
                is ConversationMutationResult.Rejected -> state = state.copy(notice = result.reason)
                null -> state = state.copy(notice = "消息未能从当前本地路径回读，未创建分支。")
            }
        }
    }

    private fun startFixture(
        projection: com.nanzhufeng.ai.domain.ConversationRuntimeProjection,
        notice: String,
        fail: Boolean = false,
    ) {
        state = state.copy(runtime = projection.state, notice = notice)
        streamJob?.cancel()
        streamJob = viewModelScope.launch {
            fixture.events(projection.state, fail).forEach { event ->
                delay(350)
                when (val result = withContext(Dispatchers.IO) { applyRuntimeEvent.execute(event) }) {
                    is ConversationRuntimePersistenceResult.Applied, is ConversationRuntimePersistenceResult.Replayed -> reload()
                    is ConversationRuntimePersistenceResult.Rejected -> { state = state.copy(notice = result.reason); return@launch }
                }
            }
        }
    }

    private fun managementNotice(action: ConversationManagementAction, title: String) = when (action) {
        ConversationManagementAction.RENAME -> "已重命名为“$title”。"
        ConversationManagementAction.PIN -> "已置顶本地会话。"
        ConversationManagementAction.UNPIN -> "已取消置顶本地会话。"
        ConversationManagementAction.FAVORITE -> "已收藏本地会话。"
        ConversationManagementAction.UNFAVORITE -> "已取消收藏本地会话。"
        ConversationManagementAction.ARCHIVE -> "已归档；消息、附件和调用关联未改变。"
        ConversationManagementAction.UNARCHIVE -> "已恢复到本地会话列表。"
        ConversationManagementAction.ASSIGN_PROJECT -> "已移动到所选项目。"
        ConversationManagementAction.REMOVE_PROJECT -> "已移出当前项目。"
        ConversationManagementAction.SOFT_DELETE -> "已移入回收站；消息树未物理删除。"
        ConversationManagementAction.RESTORE_DELETED -> "已从回收站恢复。"
    }

    class Factory(
        private val repository: ConversationRepository,
        private val createConversation: CreateConversationUseCase,
        private val appendMessage: AppendConversationMessageUseCase,
        private val editUserMessage: EditConversationUserMessageUseCase,
        private val saveDraft: SaveConversationDraftUseCase,
        private val submitDraft: SubmitConversationDraftUseCase,
        private val renderer: MessagePresentationRenderer,
        private val startLocalRuntime: StartLocalConversationRuntimeUseCase,
        private val applyRuntimeEvent: ApplyConversationRuntimeEventUseCase,
        private val fixture: DeterministicFixtureStreamingAdapter,
        private val actions: ConversationActionOrchestrator,
        private val switchBranch: SwitchConversationBranchUseCase,
        private val manageConversation: ManageConversationUseCase,
        private val searchConversations: SearchConversationsUseCase,
        private val searchConversationAttachments: SearchConversationAttachmentsUseCase,
        private val searchHistory: LocalSearchHistoryStore,
        private val conversationReadMarkerStore: ConversationReadMarkerStore,
        private val exportConversation: ExportConversationPackageUseCase,
        private val galleryReader: AndroidGallerySelectionReader,
        private val documentReader: AndroidDocumentSelectionReader,
        private val addAttachment: AddConversationImageAttachmentUseCase,
        private val removeAttachment: RemoveConversationAttachmentUseCase,
        private val attachmentPreview: ConversationAttachmentPreviewProjection,
        private val pdfPreviewPosition: PdfPreviewPositionStore,
        private val videoPreviewPosition: VideoPreviewPositionStore,
        private val audioPreviewPosition: AudioPreviewPositionStore,
        private val readAttemptHistory: ReadConversationAttemptHistoryUseCase,
        private val temporary: TemporaryConversationDomain,
        private val addTemporaryAttachment: AddTemporaryConversationAttachmentUseCase,
        private val clearTemporary: ClearTemporaryConversationUseCase,
        private val p6gModelSelection: P6GModelSelectionOwner,
        private val conversationWebSearchOverrides: ConversationWebSearchOverrideOwner,
        private val loadAssistantExperienceSettings: LoadAssistantExperienceSettingsUseCase,
        private val invocations: InvocationRepository,
        private val responseModelAttributions: AssistantResponseModelAttributionStore,
        private val contextSelectionAudits: ContextSelectionAuditStore,
        private val normalChatOpenRouterExecutor: NormalChatOpenRouterExecutor,
        private val normalChatBackgroundExecution: NormalChatBackgroundExecution,
        private val startWithFreshChat: Boolean = false,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ConversationFoundationViewModel::class.java))
            return ConversationFoundationViewModel(repository, createConversation, appendMessage, editUserMessage, saveDraft, submitDraft, renderer, startLocalRuntime, applyRuntimeEvent, fixture, actions, switchBranch, manageConversation, searchConversations, searchConversationAttachments, searchHistory, conversationReadMarkerStore, exportConversation, galleryReader, documentReader, addAttachment, removeAttachment, attachmentPreview, pdfPreviewPosition, videoPreviewPosition, audioPreviewPosition, readAttemptHistory, temporary, addTemporaryAttachment, clearTemporary, p6gModelSelection, conversationWebSearchOverrides, loadAssistantExperienceSettings, invocations, responseModelAttributions, contextSelectionAudits, normalChatOpenRouterExecutor, normalChatBackgroundExecution, startWithFreshChat) as T
        }
    }
}

private fun normalChatResultLabel(code: NormalChatOpenRouterExecutor.Code, sent: Boolean): String = when (code) {
    NormalChatOpenRouterExecutor.Code.SERVICE_DISABLED -> "本次实际接收服务商未启用：请在设置中启用后再发送。"
    NormalChatOpenRouterExecutor.Code.CREDENTIAL_MISSING -> "本次实际接收服务商未保存 API Key：请在设置中保存后再发送。"
    NormalChatOpenRouterExecutor.Code.REGISTRY_UNVERIFIED -> "模型目录尚未核验：请在设置中先核验公开目录。"
    NormalChatOpenRouterExecutor.Code.MODEL_UNAVAILABLE -> "当前预设模型不可用：请在设置中重新选择并核验。"
    NormalChatOpenRouterExecutor.Code.ATTACHMENTS_UNSUPPORTED -> "本次附件未能安全读取或超过数量／大小上限，未发送任何内容。"
    NormalChatOpenRouterExecutor.Code.ATTACHMENT_MODEL_UNSUPPORTED -> "当前服务商或模型不能完整解析本次附件，未发送任何封面或首页；请换用支持该类型的模型。"
    NormalChatOpenRouterExecutor.Code.CONTEXT_LIMIT -> "当前消息与完整附件超过所选模型的上下文容量，本次没有外发；请改用更大上下文模型或减少本次附件。"
    NormalChatOpenRouterExecutor.Code.DRAFT_UNAVAILABLE -> "草稿未能安全提交，本次没有外发。"
    NormalChatOpenRouterExecutor.Code.AUTHENTICATION -> "服务商拒绝鉴权：请检查本机保存的 API Key。"
    NormalChatOpenRouterExecutor.Code.BALANCE -> "服务商余额或额度不足，未自动重试。"
    NormalChatOpenRouterExecutor.Code.RATE_LIMIT -> "服务商限流，未自动重试。"
    NormalChatOpenRouterExecutor.Code.TIMEOUT -> "服务响应超时，未自动重试。"
    NormalChatOpenRouterExecutor.Code.NETWORK -> "网络连接已中断，未收到服务返回结果。"
    NormalChatOpenRouterExecutor.Code.SERVICE -> "服务商未完成本次请求，未自动重试。"
    NormalChatOpenRouterExecutor.Code.MODEL_NOT_FOUND -> "服务商未找到该模型：请在模型设置中刷新目录或改选模型。"
    NormalChatOpenRouterExecutor.Code.STREAM_REQUIRED -> "该模型要求流式输出；流式发送升级正在启用，请稍后重试。"
    NormalChatOpenRouterExecutor.Code.INVALID_REQUEST -> "服务商拒绝了本次请求格式；可在模型设置的本机诊断中查看脱敏原因。"
    NormalChatOpenRouterExecutor.Code.RESPONSE_FORMAT -> "服务返回内容无法安全读取，未自动重试。"
    NormalChatOpenRouterExecutor.Code.TOOL_CALL_UNSUPPORTED -> "服务要求执行工具调用；普通聊天未执行该工具，也没有伪造回答。"
    NormalChatOpenRouterExecutor.Code.LOCAL_SAVE -> if (sent) "服务已返回，但本机未能保存回复；请先不要重复发送。" else "本机保存失败。"
    NormalChatOpenRouterExecutor.Code.RECOVERY_UNAVAILABLE -> "这次发送无法从本机恢复；不会擅自新建请求。"
    NormalChatOpenRouterExecutor.Code.RECOVERY_MODEL_CHANGED -> "原模型档案已变化，不能安全地把旧请求改发给新模型。"
}

private fun String.toNormalChatBackgroundErrorLabel(): String? {
    val separator = indexOf(':')
    if (separator <= 0 || separator == lastIndex) return null
    val sent = substring(0, separator) == "FAILED"
    if (!sent && substring(0, separator) != "BLOCKED") return null
    val code = runCatching { NormalChatOpenRouterExecutor.Code.valueOf(substring(separator + 1)) }.getOrNull() ?: return null
    return normalChatResultLabel(code, sent)
}

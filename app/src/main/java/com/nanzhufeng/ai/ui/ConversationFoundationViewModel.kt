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
import com.nanzhufeng.ai.domain.ConversationManagementAction
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
import com.nanzhufeng.ai.domain.ExportConversationPackageUseCase
import com.nanzhufeng.ai.domain.GalleryImageSelection
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
import com.nanzhufeng.ai.domain.AttachmentId
import com.nanzhufeng.ai.domain.TemporaryConversationDomain
import com.nanzhufeng.ai.domain.TemporaryConversationRecovery
import com.nanzhufeng.ai.domain.AddTemporaryConversationAttachmentUseCase
import com.nanzhufeng.ai.domain.ClearTemporaryConversationUseCase
import com.nanzhufeng.ai.domain.P6GConversationOverride
import com.nanzhufeng.ai.domain.P6GGlobalDefault
import com.nanzhufeng.ai.domain.P6GLocalCatalogSnapshot
import com.nanzhufeng.ai.domain.P6GCatalogCandidate
import com.nanzhufeng.ai.domain.P6GCapability
import com.nanzhufeng.ai.domain.P6GModelSelectionOwner
import com.nanzhufeng.ai.domain.P6GModelTier
import com.nanzhufeng.ai.domain.P6GProviderFamily
import com.nanzhufeng.ai.domain.P6GRouteRequest
import com.nanzhufeng.ai.domain.P6GSelectionMutationResult
import com.nanzhufeng.ai.domain.NormalChatExternalSendConfirmation
import com.nanzhufeng.ai.domain.NormalChatExternalSendIntent
import com.nanzhufeng.ai.domain.NormalChatRealTextExecutionOwner
import com.nanzhufeng.ai.app.CompareVisibleExecutionOwner
import com.nanzhufeng.ai.app.CompareVisibleExecutionResult
import com.nanzhufeng.ai.domain.TemporaryAttachmentResult
import com.nanzhufeng.ai.data.AndroidGalleryOpenResult
import com.nanzhufeng.ai.data.AndroidGallerySelectionReader
import com.nanzhufeng.ai.data.AndroidDocumentSelectionReader
import com.nanzhufeng.ai.data.AndroidDocumentOpenResult
import android.net.Uri
import android.graphics.Bitmap
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import com.nanzhufeng.ai.domain.SwitchConversationBranchUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ConversationFoundationUiState(
    val isLoading: Boolean = true,
    val isCreating: Boolean = false,
    val isSending: Boolean = false,
    val isCompareDispatching: Boolean = false,
    val conversations: List<Conversation> = emptyList(),
    val listScope: ConversationListScope = ConversationListScope.ACTIVE,
    val searchQuery: String = "",
    val searchCategory: ConversationSearchCategory = ConversationSearchCategory.ALL,
    val searchResults: List<ConversationSearchHit> = emptyList(),
    val attachmentSearchResults: List<ConversationAttachmentSearchHit> = emptyList(),
    val searchAttachmentPreviews: Map<AttachmentId, ConversationAttachmentPreview> = emptyMap(),
    val searchPanelOpen: Boolean = false,
    val searchHistory: List<String> = emptyList(),
    val searchHistoryOpen: Boolean = false,
    val searchAnchorMessageId: MessageNodeId? = null,
    val selectedConversationId: com.nanzhufeng.ai.domain.ConversationId? = null,
    val surface: ConversationSurface = ConversationSurface.CHAT,
    val currentProjectId: String? = null,
    val runtime: ConversationRuntimeState? = null,
    val messages: List<PresentedTranscriptMessage> = emptyList(),
    val draft: ConversationDraft? = null,
    val recovery: ConversationRecoveryPresentation? = null,
    val currentLeafId: MessageNodeId? = null,
    val branchLeaves: List<ConversationBranchUi> = emptyList(),
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
    val externalSendConfirmation: NormalChatExternalSendConfirmation? = null,
    val importedFromChatGptExport: Boolean = false,
    val importedFromClaudeExport: Boolean = false,
    val notice: String? = null,
)

enum class AttachmentTransferAction { DOWNLOAD, SHARE }

/** Full bytes exist only for one explicit user-initiated download or system-share handoff. */
data class AttachmentTransferItem(
    val id: AttachmentId,
    val displayName: String?,
    val mimeType: String,
    val bytes: ByteArray,
)

data class AttachmentTransferRequest(
    val id: AttachmentId,
    val action: AttachmentTransferAction,
    val displayName: String?,
    val mimeType: String,
    val bytes: ByteArray,
    val batch: List<AttachmentTransferItem> = listOf(AttachmentTransferItem(id, displayName, mimeType, bytes)),
)

data class ConversationBranchUi(
    val leafId: MessageNodeId,
    val role: MessageRole,
    val label: String,
    val revision: Int,
    val isCurrent: Boolean,
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
    private val invocations: InvocationRepository,
    private val normalChatRealTextExecutionOwner: NormalChatRealTextExecutionOwner,
    private val compareVisibleExecutionOwner: CompareVisibleExecutionOwner,
) : ViewModel() {
    private var streamJob: Job? = null
    private var currentAttachmentReferences: Map<AttachmentId, ConversationAttachmentReference> = emptyMap()
    private var temporaryAttachmentReferences: Map<AttachmentId, ConversationAttachmentReference> = emptyMap()
    private var draftSaveGeneration = 0L
    private var reloadGeneration = 0L
    private var surfaceRequestGeneration = 0L
    private var selectedChatConversationId: com.nanzhufeng.ai.domain.ConversationId? = null
    private var selectedWorkConversationId: com.nanzhufeng.ai.domain.ConversationId? = null
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
        reload()
    }

    fun reload(
        notice: String? = null,
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
                LoadedConversation(conversations, snapshot, runtime, lineage, lineages, attemptHistory, chatGptImported, claudeImported)
            }
            val conversations = loaded.conversations
            val snapshot = loaded.snapshot
            val runtime = loaded.runtime
            val lineage = loaded.lineage
            val lineages = loaded.lineages
            val attemptHistory = loaded.attemptHistory
            val path = snapshot?.let { MessageTree(it.conversation, it.nodes).contextPath() }.orEmpty()
            val invocationById = withContext(Dispatchers.IO) {
                path.mapNotNull { node ->
                    node.invocation?.invocationId?.let { invocationId ->
                        invocations.findById(invocationId)?.let { invocationId to it }
                    }
                }.toMap()
            }
            val runtimeByMessage = runtime
                ?.let { persisted -> mapOf(persisted.messageId to persisted) }
                .orEmpty()
            val messages = ConversationTranscriptPresentation(renderer).render(
                path,
                lineages,
                invocationById,
                runtimeByMessage,
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
            when (surface) {
                ConversationSurface.CHAT -> selectedChatConversationId = snapshot?.conversation?.id
                ConversationSurface.WORK -> selectedWorkConversationId = snapshot?.conversation?.id
            }
            state = state.copy(
                surface = surface,
                isLoading = false, isCreating = false, isSending = false,
                conversations = conversations, selectedConversationId = snapshot?.conversation?.id,
                currentProjectId = snapshot?.conversation?.projectId,
                runtime = runtime, messages = messages, draft = snapshot?.draft,
                recovery = ConversationRecoveryPresenter.present(runtime, runtimeMessage),
                currentLeafId = currentLeaf, branchLeaves = leaves, editableUserMessages = editable, lineage = lineage,
                attemptHistory = attemptHistory,
                attachmentPreviews = attachmentPreviews,
                imagePreview = null,
                pdfPreview = null,
                videoPreview = null,
                p6gCatalog = p6gCatalog,
                p6gGlobalDefault = p6gGlobalDefault,
                p6gConversationOverride = p6gConversationOverride,
                importedFromChatGptExport = loaded.importedFromChatGptExport,
                importedFromClaudeExport = loaded.importedFromClaudeExport,
            )
        }
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
                    attachmentPreview.original(reference).bytes?.let { bytes ->
                        AttachmentTransferItem(id, reference.displayName, reference.mimeType, bytes)
                    }
                }
            }
            state = if (items.isEmpty()) {
                state.copy(notice = "所选本地附件不可用，无法${if (action == AttachmentTransferAction.DOWNLOAD) "下载" else "分享"}。")
            } else {
                val first = items.first()
                state.copy(
                    attachmentTransfer = AttachmentTransferRequest(first.id, action, first.displayName, first.mimeType, first.bytes, items),
                    notice = null,
                )
            }
        }
    }

    fun consumeAttachmentTransfer(id: AttachmentId) {
        if (state.attachmentTransfer?.id == id) state = state.copy(attachmentTransfer = null)
    }

    /** P6-G only persists a current normal-conversation override and safe route metadata. */
    fun selectP6GModel(modelId: String?) {
        val conversationId = state.selectedConversationId ?: return
        val current = state.p6gConversationOverride ?: return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                p6gModelSelection.setConversationManualOverride(conversationId, modelId, current.revision).also { applied ->
                    if (applied is P6GSelectionMutationResult.Applied) p6gModelSelection.evaluate(
                        conversationId,
                        P6GRouteRequest(P6GModelTier.BALANCED, exactHistoricalCacheHit = false, localSafeRequired = false, unknownCostConfirmed = false, contextTokens = null, budgetMicros = null),
                    )
                }
            }
            when (result) {
                is P6GSelectionMutationResult.Applied -> reload(if (modelId == null) "已切回自动；当前会话恢复本地策略。" else "已保存当前会话的本地手动模型选择；未读取 Key、未调用 Provider。")
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
            state = state.copy(videoPreview = preview.copy(positionMillis = videoPreviewPosition.positionFor(id)), notice = if (preview.bytes == null) preview.unavailableReason else "已准备本地视频；点击播放才会开始。")
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
            state = state.copy(audioPreview = preview.copy(positionMillis = audioPreviewPosition.positionFor(id)), notice = if (preview.bytes == null) preview.unavailableReason else "已准备本地音频；点击播放才会开始。")
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
            applyTemporaryRecovery(recovery, "临时聊天仅在本机恢复，最多保留 24 小时。")
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
            applyTemporaryRecovery(recovery, "已在本机临时聊天发送；未连接 Provider。")
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

    fun onTemporaryPhotoPickerResult(uri: Uri?) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val selection = when (val opened = uri?.let(galleryReader::open)) {
                    null -> null
                    is AndroidGalleryOpenResult.Opened -> opened.selection
                    is AndroidGalleryOpenResult.Rejected -> return@withContext TemporaryAttachmentResult.Rejected("图片不可读取，临时草稿未改变。")
                }
                addTemporaryAttachment.add(selection?.let { com.nanzhufeng.ai.domain.ConversationAttachmentSelection(it.input, it.mimeType, it.displayName, it.width, it.height) })
            }
            when (result) {
                is TemporaryAttachmentResult.Added -> applyTemporaryRecovery(result.recovery, "附件已加入临时会话，仅本地引用。")
                TemporaryAttachmentResult.Cancelled -> state = state.copy(notice = "已取消选择，临时草稿未改变。")
                is TemporaryAttachmentResult.Rejected -> state = state.copy(notice = result.reason)
            }
        }
    }

    /** TEMP camera follows the same bounded private-copy owner as TEMP picker attachments. */
    fun onTemporaryCameraResult(bitmap: Bitmap?) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                if (bitmap == null) return@withContext TemporaryAttachmentResult.Cancelled
                val bytes = ByteArrayOutputStream().use { output ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output)
                    output.toByteArray()
                }
                addTemporaryAttachment.add(
                    com.nanzhufeng.ai.domain.ConversationAttachmentSelection(
                        input = ByteArrayInputStream(bytes),
                        mimeType = "image/jpeg",
                        displayName = "camera-${System.currentTimeMillis()}.jpg",
                        width = bitmap.width,
                        height = bitmap.height,
                    ),
                )
            }
            when (result) {
                is TemporaryAttachmentResult.Added -> applyTemporaryRecovery(result.recovery, "相机图片已加入临时会话，仅本地引用。")
                TemporaryAttachmentResult.Cancelled -> state = state.copy(notice = "已取消拍照，临时草稿没有改变。")
                is TemporaryAttachmentResult.Rejected -> state = state.copy(notice = result.reason)
            }
        }
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
        if (state.surface == ConversationSurface.CHAT && id == state.selectedConversationId) return
        selectedChatConversationId = id
        state = state.copy(surface = ConversationSurface.CHAT, selectedConversationId = id)
        reload()
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
                    when (val result = withContext(Dispatchers.IO) {
                        createConversation.execute(title = "工作", surface = ConversationSurface.WORK)
                    }) {
                        is ConversationMutationResult.Saved -> {
                            if (request != surfaceRequestGeneration) return@launch
                            selectedWorkConversationId = result.snapshot.conversation.id
                            state = state.copy(isCreating = false)
                            reload(targetSurface = ConversationSurface.WORK, selectedBefore = selectedWorkConversationId, requestedSurfaceGeneration = request)
                        }
                        is ConversationMutationResult.Rejected -> if (request == surfaceRequestGeneration) {
                            state = state.copy(isCreating = false, notice = result.reason)
                        }
                    }
                }
            }
        } else {
            reload(targetSurface = surface, selectedBefore = selectedBefore, requestedSurfaceGeneration = request)
        }
    }

    fun setListScope(scope: ConversationListScope) {
        if (state.listScope == scope) return
        state = state.copy(listScope = scope, searchResults = emptyList(), attachmentSearchResults = emptyList(), searchAttachmentPreviews = emptyMap(), searchQuery = "", searchPanelOpen = false)
        reload()
    }

    fun updateSearchQuery(query: String) {
        state = state.copy(searchQuery = query, searchPanelOpen = false)
    }

    fun selectSearchCategory(category: ConversationSearchCategory) {
        if (state.searchCategory == category) return
        state = state.copy(searchCategory = category, searchResults = emptyList(), attachmentSearchResults = emptyList(), searchAttachmentPreviews = emptyMap(), searchPanelOpen = false)
        if (state.searchQuery.isNotBlank()) submitSearch()
    }

    fun openSearchHistory() { state = state.copy(searchHistory = searchHistory.recent(state.listScope), searchHistoryOpen = true) }
    fun closeSearchHistory() { state = state.copy(searchHistoryOpen = false) }
    fun fillSearchHistory(query: String) { state = state.copy(searchQuery = query, searchHistoryOpen = false) }
    fun clearSearchHistory() { searchHistory.clear(state.listScope); state = state.copy(searchHistory = emptyList()) }

    /** Only an explicit submit reads the local index; focus/type never scans conversation bodies. */
    fun submitSearch() {
        val query = state.searchQuery
        if (query.isBlank()) return
        val category = state.searchCategory
        viewModelScope.launch {
            val scope = state.listScope
            val results = withContext(Dispatchers.IO) {
                if (category == ConversationSearchCategory.TEXT || category == ConversationSearchCategory.ALL) searchConversations.execute(query, scope) else emptyList()
            }
            val attachments = withContext(Dispatchers.IO) { searchConversationAttachments.execute(query, category, scope) }
            val previews = withContext(Dispatchers.IO) {
                attachments.map { it.attachment }.distinctBy { it.id }.associate { reference -> reference.id to attachmentPreview.project(reference) }
            }
            if (state.searchQuery == query && state.searchCategory == category) {
                searchHistory.record(query, scope)
                state = state.copy(
                    searchResults = results,
                    attachmentSearchResults = attachments,
                    searchAttachmentPreviews = previews,
                    searchPanelOpen = false,
                    searchHistoryOpen = false,
                    searchHistory = searchHistory.recent(scope),
                )
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
        state = state.copy(isCreating = true, notice = null)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                // A normal conversation starts empty.  Deterministic fixtures remain explicit
                // task actions and are never silently written into a user-visible transcript.
                createConversation.execute(surface = state.surface)
            }
            when (result) {
                is ConversationMutationResult.Saved -> {
                    when (state.surface) {
                        ConversationSurface.CHAT -> selectedChatConversationId = result.snapshot.conversation.id
                        ConversationSurface.WORK -> selectedWorkConversationId = result.snapshot.conversation.id
                    }
                    state = state.copy(selectedConversationId = result.snapshot.conversation.id, isCreating = false)
                    reload(if (state.surface == ConversationSurface.CHAT) "已创建新对话；首条消息会在本机生成标题。" else "已创建独立工作内容。")
                }
                is ConversationMutationResult.Rejected -> state = state.copy(isCreating = false, notice = result.reason)
            }
        }
    }

    fun updateDraft(text: String) {
        val id = state.selectedConversationId ?: return
        val current = state.draft ?: return
        state = state.copy(
            draft = current.copy(text = text),
            notice = null,
            externalSendConfirmation = state.externalSendConfirmation?.let(normalChatRealTextExecutionOwner::expire),
        )
        val generation = ++draftSaveGeneration
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { saveDraft.execute(id, text, current.attachments) }
            if (generation != draftSaveGeneration) return@launch
            when (result) {
                is ConversationDraftResult.Saved -> state = state.copy(draft = result.draft)
                is ConversationDraftResult.Rejected -> state = state.copy(notice = result.reason)
            }
        }
    }

    fun submitCurrentDraft() {
        val id = state.selectedConversationId ?: return
        if (state.isSending) return
        state = state.copy(isSending = true, notice = null)
        viewModelScope.launch {
            when (val result = withContext(Dispatchers.IO) { submitDraft.execute(id) }) {
                is ConversationDraftSubmissionResult.Submitted -> reload("已本地发送；草稿已在同一事务清理，未连接 Provider。")
                is ConversationDraftSubmissionResult.Rejected -> state = state.copy(isSending = false, notice = result.reason)
            }
        }
    }

    /**
     * This is a UI-only, fail-closed intent.  The existing local submit path is
     * deliberately independent and remains the only action performed by the composer.
     */
    fun requestNormalChatExternalSendConfirmation() {
        val draft = state.draft ?: return
        if (state.surface != ConversationSurface.CHAT || draft.text.isBlank()) return
        state = state.copy(
            externalSendConfirmation = normalChatRealTextExecutionOwner.requestConfirmation(
                NormalChatExternalSendIntent(
                    draftCharacterCount = draft.text.length,
                    attachmentCount = draft.attachments.size,
                ),
            ),
        )
    }

    fun setNormalChatExternalSendAcknowledgement(checked: Boolean) {
        val confirmation = state.externalSendConfirmation ?: return
        state = state.copy(
            externalSendConfirmation = normalChatRealTextExecutionOwner.setAcknowledgement(confirmation, checked),
        )
    }

    fun expireNormalChatExternalSendConfirmation() {
        val confirmation = state.externalSendConfirmation ?: return
        state = state.copy(externalSendConfirmation = normalChatRealTextExecutionOwner.expire(confirmation))
    }

    fun dismissNormalChatExternalSendConfirmation() {
        state = state.copy(externalSendConfirmation = null)
    }

    /** Explicit Compare is separate from the ordinary local-only submit action and executes directly. */
    fun requestCompareChatGptAndClaude() {
        val conversationId = state.selectedConversationId ?: return
        val draft = state.draft ?: return
        if (state.surface != ConversationSurface.CHAT || draft.text.isBlank()) return
        if (state.isCompareDispatching) return
        state = state.copy(isCompareDispatching = true, notice = null)
        viewModelScope.launch {
            when (val result = withContext(Dispatchers.IO) { compareVisibleExecutionOwner.execute(conversationId, draft) }) {
                is CompareVisibleExecutionResult.Accepted -> {
                    state = state.copy(isCompareDispatching = false)
                    reload("已直接提交一次 ChatGPT + Claude Compare 批次；两支独立记录安全 receipt 与用量。")
                }
                is CompareVisibleExecutionResult.Blocked -> state = state.copy(
                    isCompareDispatching = false,
                    notice = "Compare 未发送或未完整接收：${result.blocker.name}。",
                )
            }
        }
    }

    fun onConversationPhotoPickerResult(uri: Uri?) {
        val conversationId = state.selectedConversationId ?: return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val selection = when (val opened = uri?.let(galleryReader::open)) {
                    null -> null
                    is AndroidGalleryOpenResult.Opened -> opened.selection
                    is AndroidGalleryOpenResult.Rejected -> return@withContext AddConversationAttachmentResult.Rejected("图片不可读取；当前会话草稿未改变。")
                }
                addAttachment.execute(conversationId, selection)
            }
            when (result) {
                is AddConversationAttachmentResult.Added -> reload("图片已加入本地会话草稿；不会发送给 AI 或第三方。")
                AddConversationAttachmentResult.Cancelled -> state = state.copy(notice = "已取消选择，当前会话草稿没有改变。")
                is AddConversationAttachmentResult.Rejected -> state = state.copy(notice = result.reason)
            }
        }
    }

    /** Camera receives only a system-provided preview Bitmap, then follows the same bounded private-copy path as gallery images. */
    fun onConversationCameraResult(bitmap: Bitmap?) {
        val conversationId = state.selectedConversationId ?: return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                if (bitmap == null) return@withContext AddConversationAttachmentResult.Cancelled
                val bytes = ByteArrayOutputStream().use { output ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output)
                    output.toByteArray()
                }
                addAttachment.execute(
                    conversationId,
                    GalleryImageSelection(
                        input = ByteArrayInputStream(bytes),
                        mimeType = "image/jpeg",
                        displayName = "camera-${System.currentTimeMillis()}.jpg",
                        width = bitmap.width,
                        height = bitmap.height,
                    ),
                )
            }
            when (result) {
                is AddConversationAttachmentResult.Added -> reload("相机图片已私有复制到本地草稿；不会发送给 AI 或第三方。")
                AddConversationAttachmentResult.Cancelled -> state = state.copy(notice = "已取消拍照，当前会话草稿没有改变。")
                is AddConversationAttachmentResult.Rejected -> state = state.copy(notice = result.reason)
            }
        }
    }

    fun onConversationDocumentPickerResult(uri: Uri?) {
        val conversationId = state.selectedConversationId ?: return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                when (val opened = documentReader.open(uri)) {
                    AndroidDocumentOpenResult.Cancelled -> return@withContext AddConversationAttachmentResult.Cancelled
                    is AndroidDocumentOpenResult.Rejected -> return@withContext AddConversationAttachmentResult.Rejected(opened.reason)
                    is AndroidDocumentOpenResult.Opened -> addAttachment.add(conversationId, opened.selection)
                }
            }
            when (result) {
                is AddConversationAttachmentResult.Added -> reload("文件已私有复制到本地草稿；不会发送给 AI 或第三方。")
                AddConversationAttachmentResult.Cancelled -> state = state.copy(notice = "已取消选择，当前会话草稿没有改变。")
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
                else -> { selectConversation(result.conversation.id); state = state.copy(notice = "已从该 assistant 消息创建本地分支；未连接 Provider。") }
            }
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
        private val invocations: InvocationRepository,
        private val normalChatRealTextExecutionOwner: NormalChatRealTextExecutionOwner,
        private val compareVisibleExecutionOwner: CompareVisibleExecutionOwner,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ConversationFoundationViewModel::class.java))
            return ConversationFoundationViewModel(repository, createConversation, appendMessage, editUserMessage, saveDraft, submitDraft, renderer, startLocalRuntime, applyRuntimeEvent, fixture, actions, switchBranch, manageConversation, searchConversations, searchConversationAttachments, searchHistory, exportConversation, galleryReader, documentReader, addAttachment, removeAttachment, attachmentPreview, pdfPreviewPosition, videoPreviewPosition, audioPreviewPosition, readAttemptHistory, temporary, addTemporaryAttachment, clearTemporary, p6gModelSelection, invocations, normalChatRealTextExecutionOwner, compareVisibleExecutionOwner) as T
        }
    }
}

package com.nanzhufeng.ai.domain

import java.io.InputStream

interface AiTaskRunner {
    /**
     * Must perform all request-independent gates before `run`. A rejected preflight becomes a
     * BLOCKED Task Run, so no ProviderAttempt can be fabricated for an unsent request.
     */
    fun preflight(task: AiTask, draft: CaptureDraft): AiTaskError? = null
    fun run(task: AiTask, draft: CaptureDraft): AiTaskRunResult
}

interface KnowledgeRepository {
    fun save(item: KnowledgeItem): KnowledgeItem
    fun findById(id: KnowledgeItemId): KnowledgeItem?
    fun listAll(): List<KnowledgeItem>
}

/** P4-E keeps Knowledge lifecycle/search separate from the immutable P2 save/read port. */
interface KnowledgeManagementRepository {
    fun mutate(intent: KnowledgeIntent, fingerprint: String): KnowledgeMutationResult
    fun findSnapshot(id: KnowledgeItemId): KnowledgeSnapshot?
    fun listSnapshots(filter: KnowledgeSearchFilter = KnowledgeSearchFilter()): List<KnowledgeSnapshot>
}

/** P4-G relation facts stay in the Knowledge Domain; this port has no body, URI or attachment field. */
interface KnowledgeRelationshipRepository {
    fun apply(
        intent: KnowledgeRelationshipIntent,
        fingerprint: String,
        decide: (snapshots: List<KnowledgeSnapshot>, existing: List<KnowledgeRelationshipSnapshot>) -> RelationshipDecision,
    ): KnowledgeRelationshipMutationResult
    fun list(filter: KnowledgeRelationshipListFilter = KnowledgeRelationshipListFilter()): List<KnowledgeRelationshipSnapshot>
}

interface CaptureDraftRepository {
    fun save(draft: CaptureDraft): CaptureDraft
    fun findById(id: CaptureDraftId): CaptureDraft?
    fun findLatest(): CaptureDraft?
}

/**
 * The repository owns Room transactions and process-rebuild reads. Conversation Domain owns
 * tree/branch semantics; UI never reaches a DAO.
 */
interface ConversationRepository {
    fun save(snapshot: ConversationSnapshot): ConversationSnapshot
    fun findById(id: ConversationId): ConversationSnapshot?
    fun listActive(): List<Conversation>
    /** A recycle-bin purge is explicit and irreversible; implementations must remove the local conversation tree atomically. */
    fun permanentlyDelete(conversationId: ConversationId, expectedRevision: Long): ConversationPurgeResult =
        ConversationPurgeResult.Rejected("当前会话存储不支持永久删除。")
}

sealed interface ConversationPurgeResult {
    data object Deleted : ConversationPurgeResult
    data class Rejected(val reason: String) : ConversationPurgeResult
}

interface ConversationManagementRepository {
    fun applyManagement(
        intent: ConversationManagementIntent,
        requestFingerprint: String,
        mutate: (ConversationSnapshot) -> ConversationSnapshot,
    ): ConversationManagementResult
}

interface ConversationSearchRepository {
    fun snapshotsForSearch(): List<ConversationSnapshot>
}

/** P6-F2-A's read port. Implementations return only already-sanitized index rows. */
interface LocalSearchIndexRepository {
    fun searchLocalIndex(normalizedQuery: String, scope: ConversationListScope): List<LocalSearchIndexRecord>
}

interface LocalSearchHistoryStore {
    fun recent(scope: ConversationListScope): List<String>
    fun record(query: String, scope: ConversationListScope)
    fun remove(query: String, scope: ConversationListScope)
    fun clear(scope: ConversationListScope)
}

/** Content-free local watermark for conversation rows that have new content since last opening. */
interface ConversationReadMarkerStore {
    fun isInitialized(): Boolean
    fun markInitialized()
    fun lastReadAtEpochMs(conversationId: ConversationId): Long?
    fun markRead(conversationId: ConversationId, updatedAtEpochMs: Long)
}

interface ConversationListRepository {
    fun list(scope: ConversationListScope): List<Conversation>
}

/** Keeps Chat and Work content streams independently queryable while sharing the message contract. */
interface ConversationSurfaceRepository {
    fun listActive(surface: ConversationSurface): List<Conversation>
}

/** Read-only source badge for ordinary conversations; it is never a Provider or invocation fact. */
interface ImportedConversationProvenanceReader {
    fun isChatGptExportImported(conversationId: ConversationId): Boolean
    fun isClaudeExportImported(conversationId: ConversationId): Boolean
}

/** P3-D owns only saved drafts and atomic draft-to-user-message submission. */
interface ConversationDraftRepository {
    fun loadDraft(conversationId: ConversationId): ConversationDraft?
    fun saveDraft(conversationId: ConversationId, draft: ConversationDraft): ConversationDraft
    fun submitDraft(snapshotWithClearedDraft: ConversationSnapshot, expectedDraft: ConversationDraft): ConversationDraftSubmissionResult
}

/** P3-B owns atomic runtime-event fact, message projection and recovery checkpoint persistence. */
interface ConversationRuntimeRepository {
    fun apply(projection: ConversationRuntimeProjection, event: AiRuntimeEvent): ConversationRuntimePersistenceResult
    /** Atomically commits the user submission, cleared draft, and the initial assistant placeholder. */
    fun submitDraftAndStart(
        snapshotWithClearedDraft: ConversationSnapshot,
        expectedDraft: ConversationDraft,
        projection: ConversationRuntimeProjection,
        event: RuntimeRunStarted,
    ): ConversationRuntimePersistenceResult
    fun stateFor(conversationId: ConversationId): ConversationRuntimeState?
}

/** P3-C persists action lineage and its RUN_STARTED projection atomically. */
interface ConversationActionRepository {
    fun applyAction(
        projection: ConversationRuntimeProjection,
        started: RuntimeRunStarted,
        lineage: ConversationAttemptLineage,
    ): ConversationActionPersistenceResult
    fun lineageForIntent(intentId: ConversationActionIntentId): ConversationAttemptLineage?
    fun lineageForInvocation(invocationId: InvocationId): ConversationAttemptLineage?
    fun lineagesForConversation(conversationId: ConversationId): List<ConversationAttemptLineage>
}

/**
 * Append-only local ledger for safe AI runtime metadata. It deliberately has no
 * prompt, response body, credential, image, or attachment-content fields.
 */
interface InvocationRepository {
    fun save(record: InvocationRecord): InvocationRecord
    fun findById(id: InvocationId): InvocationRecord?
    fun listNewestFirst(): List<InvocationRecord>
}

/** Separate from the invocation ledger: it contains local, user-reviewable candidate content. */
interface GeneratedCandidateRepository {
    fun save(candidate: StoredGeneratedCandidate): StoredGeneratedCandidate
    fun findById(id: CandidateId): StoredGeneratedCandidate?
    fun findLatestPendingReview(): StoredGeneratedCandidate?
    fun updateStatus(id: CandidateId, status: CandidateReviewStatus, updatedAt: java.time.Instant): StoredGeneratedCandidate
}

interface ModelServiceSettingsRepository {
    fun load(providerId: ProviderId): ProviderSettings
    fun save(settings: ProviderSettings): ProviderSettings
}

interface ProviderCredentialStore {
    /**
     * Presence is intentionally weaker than loading a credential. Offline acceptance checks may
     * ask only this question; they must never obtain credential bytes or construct Authorization.
     */
    fun credentialPresence(providerId: ProviderId): CredentialPresence =
        if (hasCredential(providerId)) CredentialPresence.PRESENT else CredentialPresence.MISSING
    fun hasCredential(providerId: ProviderId): Boolean
    fun saveCredential(providerId: ProviderId, credential: CharArray): Boolean
    fun loadCredential(providerId: ProviderId): CharArray?
}

enum class CredentialPresence { PRESENT, MISSING }

interface VersionedModelRegistry {
    fun currentSnapshot(providerId: ProviderId): ModelRegistrySnapshot?
    fun previousStableSnapshot(providerId: ProviderId): ModelRegistrySnapshot?
    fun publishVerified(snapshot: ModelRegistrySnapshot): ModelRegistryPublicationResult
    fun resolve(providerId: ProviderId, presetId: ModelPresetId): ModelRegistryResolution
}

/** Read-only catalog data contains only the P2-J field whitelist, never the raw provider body. */
data class OpenRouterCatalogModel(
    val id: String,
    val displayName: String,
    val contextWindowTokens: Long?,
    val maxOutputTokens: Long? = null,
    val inputModalities: Set<String>,
    val outputModalities: Set<String>,
    val supportedParameters: Set<String>,
    val promptUsdPerToken: String?,
    val completionUsdPerToken: String?,
    val cacheReadUsdPerToken: String?,
)

data class OpenRouterCatalogResponse(
    val models: List<OpenRouterCatalogModel>,
    val sourceEtag: String?,
)

sealed interface OpenRouterCatalogFetchResult {
    data class Fetched(val response: OpenRouterCatalogResponse) : OpenRouterCatalogFetchResult
    data class Failed(val reason: OpenRouterCatalogFailure) : OpenRouterCatalogFetchResult
}

enum class OpenRouterCatalogFailure {
    NETWORK, TIMEOUT, HTTP_STATUS, REDIRECT, RESPONSE_TOO_LARGE, MALFORMED_RESPONSE,
}

/** P2-J's only network boundary. Implementations must be a no-auth, bodyless GET. */
interface OpenRouterRegistryCatalogClient {
    fun fetchCatalog(): OpenRouterCatalogFetchResult
}

data class StoredModelRegistrySnapshots(
    val current: ModelRegistrySnapshot?,
    val previousStable: ModelRegistrySnapshot?,
)

interface ModelRegistrySnapshotStore {
    fun load(): StoredModelRegistrySnapshots
    fun persist(snapshots: StoredModelRegistrySnapshots): Boolean
}

sealed interface ModelRegistryPublicationResult {
    data class Published(val current: ModelRegistrySnapshot, val previousStable: ModelRegistrySnapshot?) :
        ModelRegistryPublicationResult
    data class Rejected(val error: AiTaskError) : ModelRegistryPublicationResult
}

sealed interface ModelRegistryResolution {
    data class Resolved(val snapshot: ModelRegistrySnapshot, val model: ModelDescriptor) : ModelRegistryResolution
    data class Rejected(val error: AiTaskError) : ModelRegistryResolution
}

data class AttachmentImportRequest(
    val input: InputStream,
    val mimeType: String,
    val displayName: String? = null,
)

sealed interface AttachmentImportResult {
    data class Imported(val attachment: AttachmentReference) : AttachmentImportResult
    data class Rejected(val error: AiTaskError) : AttachmentImportResult
}

sealed interface AttachmentReadResult {
    data class Content(val bytes: ByteArray) : AttachmentReadResult
    data class Rejected(val error: AiTaskError) : AttachmentReadResult
}

/**
 * Opaque verified byte source for outbound transport.  It deliberately exposes no path or URI;
 * callers can only open a fresh stream after the private store has rechecked size and hash.
 */
sealed interface AttachmentOpenResult {
    data class Opened(val byteCount: Long, val open: () -> InputStream) : AttachmentOpenResult
    data class Rejected(val error: AiTaskError) : AttachmentOpenResult
}

interface PrivateAttachmentStore {
    fun import(request: AttachmentImportRequest): AttachmentImportResult
    fun read(attachment: AttachmentReference): AttachmentReadResult
    /** Default keeps legacy/test stores fail-closed until they implement verified streaming. */
    fun openVerified(attachment: AttachmentReference): AttachmentOpenResult = AttachmentOpenResult.Rejected(AiTaskError.AttachmentNotReady)
    fun thumbnail(attachment: AttachmentReference): AttachmentThumbnailResult
    /** Renders one inert PDF page from the verified private copy; no path ever reaches UI. */
    fun pdfPage(attachment: AttachmentReference, pageNumber: Int): AttachmentPdfPageResult = AttachmentPdfPageResult.Rejected(AiTaskError.AttachmentUnsupportedType)
    /** Card metadata is bounded and local-only; media bytes remain behind the explicit player tap. */
    fun videoMetadata(attachment: AttachmentReference): AttachmentVideoMetadataResult = AttachmentVideoMetadataResult.Rejected(AiTaskError.AttachmentUnsupportedType)
    /** Explicit local-only request; the UI receives bounded poster/display bytes, never a path or URI. */
    fun videoPreview(attachment: AttachmentReference): AttachmentVideoPreviewResult = AttachmentVideoPreviewResult.Rejected(AiTaskError.AttachmentUnsupportedType)
    /** Metadata-only duration for an explicitly allowed local audio attachment. */
    fun audioDurationMillis(attachment: AttachmentReference): Long? = null
    fun deletePrivateCopy(attachment: AttachmentReference): Boolean = false
}

/** Asset metadata and the private storage key remain outside Conversation/Export/Ledger. */
interface PrivateAttachmentRepository {
    fun save(asset: AttachmentReference): AttachmentReference
    fun findById(id: AttachmentId): AttachmentReference?
    fun findBySha256(sha256: String): AttachmentReference?
    /** P6-E calls this only after its isolated reference record has been removed. */
    fun removeIfUnreferenced(id: AttachmentId): AttachmentReference? = null
}

data class AttachmentThumbnail(val bytes: ByteArray, val width: Int, val height: Int)

sealed interface AttachmentThumbnailResult {
    data class Ready(val thumbnail: AttachmentThumbnail) : AttachmentThumbnailResult
    data class Rejected(val error: AiTaskError) : AttachmentThumbnailResult
}

data class AttachmentPdfPage(val image: AttachmentThumbnail, val pageNumber: Int, val pageCount: Int)

sealed interface AttachmentPdfPageResult {
    data class Ready(val page: AttachmentPdfPage) : AttachmentPdfPageResult
    data class Rejected(val error: AiTaskError) : AttachmentPdfPageResult
}

data class AttachmentVideoPreview(
    val poster: AttachmentThumbnail,
    val durationMillis: Long,
    /** Present only while the explicit player is open; it is not persisted or indexed. */
    val bytes: ByteArray,
)

sealed interface AttachmentVideoPreviewResult {
    data class Ready(val preview: AttachmentVideoPreview) : AttachmentVideoPreviewResult
    data class Rejected(val error: AiTaskError) : AttachmentVideoPreviewResult
}

/** Bounded metadata for a visible video card. It deliberately excludes playable media bytes. */
data class AttachmentVideoMetadata(
    val poster: AttachmentThumbnail,
    val durationMillis: Long,
)

sealed interface AttachmentVideoMetadataResult {
    data class Ready(val metadata: AttachmentVideoMetadata) : AttachmentVideoMetadataResult
    data class Rejected(val error: AiTaskError) : AttachmentVideoMetadataResult
}

/** Only attachment ID and 1-based page position persist; no source path, URI or document bytes. */
interface PdfPreviewPositionStore {
    fun pageFor(attachmentId: AttachmentId): Int
    fun savePage(attachmentId: AttachmentId, pageNumber: Int)
}

/** App-private position only; it never stores a source URI, path, media bytes, or provider data. */
interface VideoPreviewPositionStore {
    fun positionFor(attachmentId: AttachmentId): Long
    fun savePosition(attachmentId: AttachmentId, positionMillis: Long)
}

/** Same narrow persistence boundary as video: attachment ID plus a local resume offset only. */
interface AudioPreviewPositionStore {
    fun positionFor(attachmentId: AttachmentId): Long
    fun savePosition(attachmentId: AttachmentId, positionMillis: Long)
}

sealed interface AiTaskRunResult {
    data class Success(val candidate: GeneratedCandidate, val invocation: InvocationRecord) : AiTaskRunResult
    data class Failure(val invocation: InvocationRecord, val error: AiTaskError) : AiTaskRunResult
}

sealed interface SaveKnowledgeResult {
    data class Saved(val item: KnowledgeItem) : SaveKnowledgeResult
    data class Rejected(val error: AiTaskError) : SaveKnowledgeResult
}

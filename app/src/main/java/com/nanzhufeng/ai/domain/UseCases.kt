package com.nanzhufeng.ai.domain

import java.io.InputStream
import java.time.Clock
import java.time.Instant

data class CaptureTextInput(
    val text: String,
    val sourceType: CaptureSourceType,
    val sourceReference: String? = null,
) {
    init {
        require(sourceType == CaptureSourceType.MANUAL_TEXT || sourceType == CaptureSourceType.ANDROID_TEXT_SHARE)
    }
}

sealed interface CaptureTextDraftResult {
    data class Saved(val draft: CaptureDraft) : CaptureTextDraftResult
    data class Rejected(val error: AiTaskError) : CaptureTextDraftResult
}

class CaptureTextDraftUseCase(
    private val draftFactory: CaptureDraftFactory,
    private val draftRepository: CaptureDraftRepository,
) {
    fun execute(input: CaptureTextInput): CaptureTextDraftResult {
        if (input.text.isBlank()) return CaptureTextDraftResult.Rejected(AiTaskError.CaptureTextBlank)
        val draft = when (input.sourceType) {
            CaptureSourceType.MANUAL_TEXT -> draftFactory.fromManualText(input.text)
            CaptureSourceType.ANDROID_TEXT_SHARE -> draftFactory.fromAndroidTextShare(input.text, input.sourceReference)
            CaptureSourceType.IMAGE -> error("图片必须通过图片捕获用例进入草稿。")
            CaptureSourceType.HISTORY_CONVERSATION -> error("历史对话整理不能通过文本捕获入口创建草稿。")
        }
        return runCatching { CaptureTextDraftResult.Saved(draftRepository.save(draft)) }
            .getOrElse { CaptureTextDraftResult.Rejected(AiTaskError.PersistenceConflict) }
    }
}

data class GalleryImageSelection(
    val input: InputStream,
    val mimeType: String,
    val displayName: String?,
    val width: Int? = null,
    val height: Int? = null,
)

sealed interface CaptureGalleryImageResult {
    data object Cancelled : CaptureGalleryImageResult
    data class Saved(val draft: CaptureDraft, val previewBytes: ByteArray) : CaptureGalleryImageResult
    data class Rejected(val error: AiTaskError) : CaptureGalleryImageResult
}

sealed interface RestoreCaptureDraftResult {
    data object Empty : RestoreCaptureDraftResult
    data class RestoredText(val draft: CaptureDraft) : RestoreCaptureDraftResult
    data class RestoredImage(val draft: CaptureDraft, val previewBytes: ByteArray) : RestoreCaptureDraftResult
    data class Rejected(val error: AiTaskError) : RestoreCaptureDraftResult
}

class CaptureGalleryImageUseCase(
    private val attachmentStore: PrivateAttachmentStore,
    private val draftFactory: CaptureDraftFactory,
    private val draftRepository: CaptureDraftRepository,
    private val attachmentRepository: PrivateAttachmentRepository? = null,
) {
    fun execute(selection: GalleryImageSelection?): CaptureGalleryImageResult {
        if (selection == null) return CaptureGalleryImageResult.Cancelled
        if (selection.mimeType !in CONVERSATION_ALLOWED_IMAGE_MIME_TYPES) {
            selection.input.close()
            return CaptureGalleryImageResult.Rejected(AiTaskError.AttachmentUnsupportedType)
        }
        val imported = attachmentStore.import(AttachmentImportRequest(
            input = selection.input,
            mimeType = selection.mimeType,
            displayName = selection.displayName,
        ))
        if (imported is AttachmentImportResult.Rejected) {
            return CaptureGalleryImageResult.Rejected(imported.error)
        }
        val attachment = runCatching {
            attachmentRepository?.save((imported as AttachmentImportResult.Imported).attachment)
                ?: (imported as AttachmentImportResult.Imported).attachment
        }.getOrElse { return CaptureGalleryImageResult.Rejected(AiTaskError.PersistenceConflict) }
        val draft = runCatching {
            draftRepository.save(draftFactory.fromImage(attachment, PHOTO_PICKER_SOURCE))
        }.getOrElse {
            return CaptureGalleryImageResult.Rejected(AiTaskError.PersistenceConflict)
        }
        return when (val preview = attachmentStore.thumbnail(attachment)) {
            is AttachmentThumbnailResult.Ready -> CaptureGalleryImageResult.Saved(draft, preview.thumbnail.bytes)
            is AttachmentThumbnailResult.Rejected -> CaptureGalleryImageResult.Rejected(preview.error)
        }
    }

    private companion object {
        const val PHOTO_PICKER_SOURCE = "android-photo-picker"
    }
}

class RestoreLatestCaptureDraftUseCase(
    private val attachmentStore: PrivateAttachmentStore,
    private val draftRepository: CaptureDraftRepository,
) {
    fun execute(): RestoreCaptureDraftResult {
        val draft = draftRepository.findLatest() ?: return RestoreCaptureDraftResult.Empty
        val attachment = draft.attachments.firstOrNull()
            ?: return RestoreCaptureDraftResult.RestoredText(draft)
        return when (val preview = attachmentStore.thumbnail(attachment)) {
            is AttachmentThumbnailResult.Ready -> RestoreCaptureDraftResult.RestoredImage(draft, preview.thumbnail.bytes)
            is AttachmentThumbnailResult.Rejected -> RestoreCaptureDraftResult.Rejected(preview.error)
        }
    }
}

class RunAiTaskUseCase(
    private val runner: AiTaskRunner,
    private val invocationRepository: InvocationRepository,
    private val clock: Clock,
) {
    fun execute(task: AiTask, draft: CaptureDraft): AiTaskRunResult {
        val error = when {
            task.draftId != draft.id -> AiTaskError.TaskDraftMismatch
            task.consent == null -> AiTaskError.ConsentRequired
            task.consent.providerId != task.providerId ||
                task.consent.modelId != task.model.id ||
                task.consent.contentFingerprint != draft.egressFingerprint() -> AiTaskError.ConsentRequired
            !draft.text.isNullOrBlank() && !task.model.capabilities.supportsText -> AiTaskError.TextNotSupported
            draft.attachments.isNotEmpty() && !task.model.capabilities.supportsVision -> AiTaskError.VisionNotSupported
            else -> runner.preflight(task, draft)
        }
        val result = error?.let { blocked(task, it) } ?: runner.run(task, draft)
        return when (result) {
            is AiTaskRunResult.Success -> result.copy(invocation = invocationRepository.save(result.invocation))
            is AiTaskRunResult.Failure -> result.copy(invocation = invocationRepository.save(result.invocation))
        }
    }

    private fun blocked(task: AiTask, error: AiTaskError): AiTaskRunResult.Failure = AiTaskRunResult.Failure(
        invocation = InvocationRecord(
            id = InvocationId.new(), taskId = task.id, providerId = task.providerId, modelId = task.model.id,
            harnessVersion = task.harness.version, completedAt = clock.instant(), status = InvocationStatus.BLOCKED, error = error,
        ),
        error = error,
    )
}

/**
 * Builds one explicit, single-use local consent. The P2-G profile is intentionally Mock-only:
 * it never resolves an external registry, reads a key, or constructs an HTTP request.
 */
class ConfirmAiRequest(
    private val clock: Clock,
    private val registry: VersionedModelRegistry? = null,
) {
    fun preview(draft: CaptureDraft): AiRequestPreview = AiRequestPreview(
        providerId = ProviderId.MOCK,
        providerLabel = "本地 Mock（非真实服务）",
        presetLabel = "本地均衡预设",
        model = P2GMockProfile.model,
        text = draft.text,
        imageCount = draft.attachments.size,
    )

    fun approve(draft: CaptureDraft): AiTask {
        val approvedAt = clock.instant()
        return AiTask(
            id = AiTaskId.new(),
            draftId = draft.id,
            providerId = ProviderId.MOCK,
            model = P2GMockProfile.model,
            harness = P2GMockProfile.harness,
            consent = EgressConsent(
                approvedAt = approvedAt,
                providerId = ProviderId.MOCK,
                modelId = P2GMockProfile.model.id,
                contentFingerprint = draft.egressFingerprint(),
                costDisclosure = CostDisclosure(
                    acknowledgedAt = approvedAt,
                    priceVersion = "mock-local-v1",
                    currencyCode = "CNY",
                    estimatedCostMicros = 0,
                ),
            ),
            createdAt = approvedAt,
        )
    }

    /** P2-K preview only. It creates no request, reads no Key, and cannot enable production egress. */
    fun previewOpenRouter(draft: CaptureDraft, presetId: ModelPresetId): OpenRouterConsentPreviewResult {
        val resolved = registry?.resolve(ProviderId.OPENROUTER, presetId)
            ?: return OpenRouterConsentPreviewResult.Rejected(AiTaskError.ModelRegistrySnapshotUnverified)
        if (resolved !is ModelRegistryResolution.Resolved) {
            return OpenRouterConsentPreviewResult.Rejected((resolved as ModelRegistryResolution.Rejected).error)
        }
        return OpenRouterConsentPreviewResult.Preview(
            AiRequestPreview(
                providerId = ProviderId.OPENROUTER,
                providerLabel = "OpenRouter（真实服务尚未授权）",
                presetLabel = NanfengModelServiceCatalog.preset(presetId).displayName,
                model = resolved.model,
                text = draft.text,
                imageCount = draft.attachments.size,
            ),
            CostDisclosure(
                acknowledgedAt = clock.instant(),
                priceVersion = resolved.model.pricing.priceVersion,
                currencyCode = resolved.model.pricing.currencyCode,
                estimatedCostMicros = null,
            ),
        )
    }

    /**
     * Future UI must collect an unchecked consent box before invoking this method. This creates
     * a task only; the P2-K production Adapter remains disabled in AppContainer.
     */
    fun approveOpenRouter(
        draft: CaptureDraft,
        presetId: ModelPresetId,
        disclosedCost: CostDisclosure,
    ): OpenRouterApprovalResult {
        val resolved = registry?.resolve(ProviderId.OPENROUTER, presetId)
            ?: return OpenRouterApprovalResult.Rejected(AiTaskError.ModelRegistrySnapshotUnverified)
        if (resolved !is ModelRegistryResolution.Resolved) {
            return OpenRouterApprovalResult.Rejected((resolved as ModelRegistryResolution.Rejected).error)
        }
        if (disclosedCost.priceVersion != resolved.model.pricing.priceVersion ||
            disclosedCost.currencyCode != resolved.model.pricing.currencyCode
        ) return OpenRouterApprovalResult.Rejected(AiTaskError.ProviderBudgetDisclosureRequired)
        val approvedAt = clock.instant()
        return OpenRouterApprovalResult.Approved(
            AiTask(
                id = AiTaskId.new(), draftId = draft.id, providerId = ProviderId.OPENROUTER,
                model = resolved.model, harness = HarnessProfile(id = "capture-organize-openrouter", version = 1),
                consent = EgressConsent(
                    approvedAt = approvedAt, providerId = ProviderId.OPENROUTER, modelId = resolved.model.id,
                    contentFingerprint = draft.egressFingerprint(), costDisclosure = disclosedCost,
                ),
                createdAt = approvedAt,
            ),
        )
    }
}

sealed interface OpenRouterConsentPreviewResult {
    data class Preview(val preview: AiRequestPreview, val costDisclosure: CostDisclosure) : OpenRouterConsentPreviewResult
    data class Rejected(val error: AiTaskError) : OpenRouterConsentPreviewResult
}

sealed interface OpenRouterApprovalResult {
    data class Approved(val task: AiTask) : OpenRouterApprovalResult
    data class Rejected(val error: AiTaskError) : OpenRouterApprovalResult
}

data class AiRequestPreview(
    val providerId: ProviderId,
    val providerLabel: String,
    val presetLabel: String,
    val model: ModelDescriptor,
    val text: String?,
    val imageCount: Int,
)

object P2GMockProfile {
    val model = ModelDescriptor(
        id = "mock-local-balanced-v1",
        displayName = "Mock Local Balanced v1",
        capabilities = ModelCapabilities(supportsText = true, supportsVision = true, supportsStreaming = false, supportsStructuredOutput = true),
        pricing = ModelPricing(priceVersion = "mock-local-v1", currencyCode = "CNY", inputMicrosPerToken = 0, outputMicrosPerToken = 0),
    )
    val harness = HarnessProfile(id = "capture-organize-mock", version = 1)
}

class PersistGeneratedCandidateUseCase(
    private val repository: GeneratedCandidateRepository,
    private val clock: Clock,
) {
    fun execute(candidate: GeneratedCandidate, invocation: InvocationRecord, task: AiTask, draft: CaptureDraft): StoredGeneratedCandidate {
        require(invocation.status == InvocationStatus.SUCCEEDED) { "只有成功调用可以创建候选。" }
        require(candidate.taskId == task.id && invocation.taskId == task.id && task.draftId == draft.id) { "候选溯源不匹配。" }
        return repository.save(StoredGeneratedCandidate(
            candidate = candidate,
            draftId = draft.id,
            invocationId = invocation.id,
            providerId = task.providerId,
            modelId = task.model.id,
            harnessVersion = task.harness.version,
            status = CandidateReviewStatus.PENDING_REVIEW,
            updatedAt = clock.instant(),
        ))
    }
}

class SaveKnowledgeItemUseCase(private val repository: KnowledgeRepository, private val clock: Clock) {
    fun execute(
        candidate: GeneratedCandidate,
        invocation: InvocationRecord,
        task: AiTask,
        draft: CaptureDraft,
        userConfirmed: Boolean,
        title: String = candidate.title,
        body: String = candidate.body,
    ): SaveKnowledgeResult {
        if (!userConfirmed) return SaveKnowledgeResult.Rejected(AiTaskError.CandidateNotConfirmed)
        val provenanceMatches = candidate.taskId == task.id &&
            invocation.taskId == task.id &&
            task.draftId == draft.id &&
            invocation.providerId == task.providerId &&
            invocation.modelId == task.model.id
        if (!provenanceMatches || invocation.status != InvocationStatus.SUCCEEDED) {
            return SaveKnowledgeResult.Rejected(AiTaskError.ProvenanceMismatch)
        }
        if (draft.attachments.any { !it.isReadyPrivateCopy() }) {
            return SaveKnowledgeResult.Rejected(AiTaskError.AttachmentNotReady)
        }
        return SaveKnowledgeResult.Saved(repository.save(KnowledgeItem(
            // A stable candidate-derived ID makes save retry/recovery idempotent.
            id = KnowledgeItemId("knowledge:${candidate.id.value}"), title = title, body = body, sourceEvidence = draft.sourceEvidence,
            provenance = CandidateProvenance(candidate.id, invocation.id, task.providerId, task.model.id, task.harness.version),
            createdAt = clock.instant(), attachments = draft.attachments,
        )))
    }
}

class SaveCandidateReviewUseCase(
    private val saveKnowledge: SaveKnowledgeItemUseCase,
    private val candidates: GeneratedCandidateRepository,
    private val clock: Clock,
) {
    fun execute(
        stored: StoredGeneratedCandidate,
        invocation: InvocationRecord,
        draft: CaptureDraft,
        title: String,
        body: String,
        userConfirmed: Boolean,
    ): SaveKnowledgeResult {
        val task = AiTask(
            id = stored.candidate.taskId,
            draftId = stored.draftId,
            providerId = stored.providerId,
            model = ModelDescriptor(stored.modelId, stored.modelId, ModelCapabilities(true, true, false, true)),
            harness = HarnessProfile("capture-organize-mock", stored.harnessVersion),
            consent = null,
            createdAt = stored.candidate.generatedAt,
        )
        val result = saveKnowledge.execute(stored.candidate, invocation, task, draft, userConfirmed, title, body)
        if (result is SaveKnowledgeResult.Saved) {
            candidates.updateStatus(stored.candidate.id, CandidateReviewStatus.SAVED, clock.instant())
        }
        return result
    }

    fun discard(stored: StoredGeneratedCandidate) {
        candidates.updateStatus(stored.candidate.id, CandidateReviewStatus.DISCARDED, clock.instant())
    }
}

/**
 * The only application-facing read projection for the P2 knowledge library.
 * It deliberately returns persisted Knowledge only: generated candidates remain
 * review work until their confirmed save has produced a KnowledgeItem.
 */
data class KnowledgeListEntry(
    val id: KnowledgeItemId,
    val title: String,
    val summary: String,
    val sourceEvidence: List<SourceEvidence>,
    val savedAt: Instant,
)

data class KnowledgeDetail(
    val item: KnowledgeItem,
    val candidateStatus: CandidateReviewStatus?,
)

class ReadKnowledgeLibraryUseCase(
    private val knowledge: KnowledgeRepository,
    private val candidates: GeneratedCandidateRepository,
) {
    fun list(): List<KnowledgeListEntry> = knowledge.listAll().map { item ->
        KnowledgeListEntry(
            id = item.id,
            title = item.title,
            summary = item.body,
            sourceEvidence = item.sourceEvidence,
            savedAt = item.createdAt,
        )
    }

    fun detail(id: KnowledgeItemId): KnowledgeDetail? = knowledge.findById(id)?.let { item ->
        KnowledgeDetail(
            item = item,
            candidateStatus = candidates.findById(item.provenance.candidateId)?.status,
        )
    }
}

class ExportKnowledgeSnapshotUseCase(private val repository: KnowledgeRepository, private val clock: Clock) {
    fun execute(): KnowledgeExportSnapshot = KnowledgeExportSnapshot(
        protocolVersion = 1,
        createdAt = clock.instant(),
        items = repository.listAll(),
    )
}

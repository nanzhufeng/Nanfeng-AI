package com.nanzhufeng.ai.domain

import java.time.Instant
import java.security.MessageDigest
import java.util.UUID

@JvmInline
value class CaptureDraftId(val value: String) {
    companion object { fun new(): CaptureDraftId = CaptureDraftId(UUID.randomUUID().toString()) }
}

@JvmInline
value class CandidateId(val value: String) {
    companion object { fun new(): CandidateId = CandidateId(UUID.randomUUID().toString()) }
}

@JvmInline
value class KnowledgeItemId(val value: String) {
    companion object { fun new(): KnowledgeItemId = KnowledgeItemId(UUID.randomUUID().toString()) }
}

@JvmInline
value class InvocationId(val value: String) {
    companion object { fun new(): InvocationId = InvocationId(UUID.randomUUID().toString()) }
}

@JvmInline
value class ModelRegistrySnapshotId(val value: String) {
    companion object { fun new(): ModelRegistrySnapshotId = ModelRegistrySnapshotId(UUID.randomUUID().toString()) }
}

@JvmInline
value class TaskRunId(val value: String) {
    companion object { fun new(): TaskRunId = TaskRunId(UUID.randomUUID().toString()) }
}

@JvmInline
value class ProviderAttemptId(val value: String) {
    companion object { fun new(): ProviderAttemptId = ProviderAttemptId(UUID.randomUUID().toString()) }
}

@JvmInline
value class GenerationId(val value: String) {
    companion object { fun new(): GenerationId = GenerationId(UUID.randomUUID().toString()) }
}

@JvmInline
value class ValidationId(val value: String) {
    companion object { fun new(): ValidationId = ValidationId(UUID.randomUUID().toString()) }
}

@JvmInline
value class AiTaskId(val value: String) {
    companion object { fun new(): AiTaskId = AiTaskId(UUID.randomUUID().toString()) }
}

@JvmInline
value class AttachmentId(val value: String) {
    companion object { fun new(): AttachmentId = AttachmentId(UUID.randomUUID().toString()) }
}

enum class CaptureSourceType { MANUAL_TEXT, ANDROID_TEXT_SHARE, IMAGE, HISTORY_CONVERSATION }

data class SourceEvidence(
    val sourceType: CaptureSourceType,
    val receivedAt: Instant,
    val sourceReference: String? = null,
    val contributedFields: Set<String>,
)

data class AttachmentReference(
    val reference: String,
    val mimeType: String,
    val displayName: String? = null,
    val id: AttachmentId = AttachmentId.new(),
    val byteCount: Long? = null,
    val sha256: String? = null,
)

fun AttachmentReference.isReadyPrivateCopy(): Boolean =
    (reference.startsWith("attachments/v1/") || reference.startsWith("p6k-zip-assets/v1/")) &&
        byteCount != null && byteCount >= 0 && !sha256.isNullOrBlank()

data class CaptureDraft(
    val id: CaptureDraftId,
    val text: String?,
    val attachments: List<AttachmentReference>,
    val sourceEvidence: List<SourceEvidence>,
    val createdAt: Instant,
    val schemaVersion: Int = 1,
) {
    init {
        require(!text.isNullOrBlank() || attachments.isNotEmpty()) { "草稿必须包含文字或附件。" }
        require(sourceEvidence.isNotEmpty()) { "草稿必须保留来源证据。" }
    }

    fun egressFingerprint(): String {
        val canonical = buildString {
            append(schemaVersion)
            append('|')
            append(text.orEmpty())
            attachments.forEach { attachment ->
                append('|')
                append(attachment.reference)
                append('|')
                append(attachment.mimeType)
            }
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}

/**
 * Provider identities are transport boundaries, not the names shown in the composer.
 * OpenRouter is intentionally shared by the ChatGPT, Claude and Gemini logical models;
 * Qwen, DeepSeek and Zhipu always have their own official endpoints and credentials.
 */
enum class ProviderId { MOCK, OPENROUTER, QWEN, DEEPSEEK, ZHIPU }

enum class ModelPresetId {
    CLAUDE_FABLE_5,
    CLAUDE_OPUS_5,
    CLAUDE_SONNET_5,
    CLAUDE_HAIKU_4_5,
    GPT_5_6_SOL,
    GPT_5_6_TERRA,
    GPT_5_6_LUNA,
    GEMINI_3_7_FLASH,
    QWEN_3_7_PLUS,
    QWEN_3_8_MAX,
    QWEN_3_6_FLASH,
    DEEPSEEK_V4_PRO,
    DEEPSEEK_V4_FLASH,
    GLM_5_3,
    GLM_5_3_FLASH,
    GLM_OCR,
}

/** Chat presets may be routed into the composer; utility presets own a dedicated workflow. */
enum class ModelPresetUsage { CHAT, DOCUMENT_OCR }

data class ProviderDescriptor(
    val id: ProviderId,
    val displayName: String,
    val fixedEndpoint: String,
)

data class ModelPresetDescriptor(
    val id: ModelPresetId,
    val displayName: String,
    val description: String,
    val modelFamilyHint: String,
    val usage: ModelPresetUsage = ModelPresetUsage.CHAT,
)

data class ProviderSettings(
    val providerId: ProviderId,
    val enabled: Boolean,
    val presetId: ModelPresetId,
)

enum class CredentialState { MISSING, STORED }

data class ModelServiceConfiguration(
    val provider: ProviderDescriptor,
    val settings: ProviderSettings,
    val preset: ModelPresetDescriptor,
    val credentialState: CredentialState,
)

data class ModelCapabilities(
    val supportsText: Boolean,
    val supportsVision: Boolean,
    val supportsStreaming: Boolean,
    val supportsStructuredOutput: Boolean = false,
    val supportsPdf: Boolean = false,
    val supportsVideo: Boolean = false,
    val supportsAudio: Boolean = false,
    val supportsTools: Boolean = false,
    val supportsReasoning: Boolean = false,
)

/**
 * `null` is unknown; `0` is a verified free/local rate. Values are integer micro-currency
 * units per token so pricing and invocation evidence never need floating-point rounding.
 */
data class ModelPricing(
    val priceVersion: String? = null,
    val currencyCode: String? = null,
    val inputMicrosPerToken: Long? = null,
    val outputMicrosPerToken: Long? = null,
    val cachedInputMicrosPerToken: Long? = null,
) {
    init {
        listOf(inputMicrosPerToken, outputMicrosPerToken, cachedInputMicrosPerToken).forEach { value ->
            require(value == null || value >= 0) { "模型价格不能为负数。" }
        }
        require(currencyCode == null || currencyCode.matches(Regex("[A-Z]{3}"))) { "货币必须是 ISO 三位大写代码。" }
        require(
            (inputMicrosPerToken == null && outputMicrosPerToken == null && cachedInputMicrosPerToken == null) ||
                (!priceVersion.isNullOrBlank() && currencyCode != null),
        ) { "已知价格必须携带价格版本和货币。" }
    }
}

data class ModelDescriptor(
    val id: String,
    val displayName: String,
    val capabilities: ModelCapabilities,
    val contextWindowTokens: Long? = null,
    val maxOutputTokens: Long? = null,
    val pricing: ModelPricing = ModelPricing(),
    val inputModalities: Set<String> = emptySet(),
    val outputModalities: Set<String> = emptySet(),
    val supportedParameters: Set<String> = emptySet(),
) {
    init {
        require(id.isNotBlank()) { "模型 ID 不能为空。" }
        require(contextWindowTokens == null || contextWindowTokens > 0) { "上下文窗口必须为正数。" }
        require(maxOutputTokens == null || maxOutputTokens > 0) { "最大输出必须为正数。" }
    }
}

enum class RegistrySnapshotSource { LOCAL_FIXTURE, OPENROUTER_CATALOG }

enum class RegistryVerificationStatus { UNVERIFIED, VERIFIED, SUPERSEDED }

data class ModelPresetMapping(val presetId: ModelPresetId, val modelId: String)

/**
 * A registry snapshot is the only source of a selectable concrete model ID. P2-E ships no
 * populated OpenRouter snapshot: a real catalog must be fetched, reviewed and verified later.
 */
data class ModelRegistrySnapshot(
    val id: ModelRegistrySnapshotId,
    val schemaVersion: Int,
    val providerId: ProviderId,
    val catalogVersion: String,
    val source: RegistrySnapshotSource,
    val capturedAt: Instant,
    val verificationStatus: RegistryVerificationStatus,
    val lastVerifiedAt: Instant? = null,
    val models: List<ModelDescriptor>,
    val presetMappings: List<ModelPresetMapping>,
    val sourceUrl: String? = null,
    val sourceEtag: String? = null,
    val catalogSha256: String? = null,
    val mappingUsesFallback: Boolean = false,
) {
    init {
        require(schemaVersion > 0) { "目录快照版本必须为正数。" }
        require(catalogVersion.isNotBlank()) { "目录版本不能为空。" }
        require(models.map(ModelDescriptor::id).distinct().size == models.size) { "目录快照不能包含重复模型 ID。" }
        require(presetMappings.map(ModelPresetMapping::presetId).distinct().size == presetMappings.size) {
            "每个预设在同一目录快照中只能映射一个模型。"
        }
        require(presetMappings.all { mapping -> models.any { it.id == mapping.modelId } }) {
            "预设映射只能引用同一快照中的模型。"
        }
        require(verificationStatus != RegistryVerificationStatus.VERIFIED || lastVerifiedAt != null) {
            "已验证目录必须保留验证时间。"
        }
        require(catalogSha256 == null || catalogSha256.matches(Regex("[0-9a-f]{64}"))) {
            "目录哈希必须是小写 SHA-256。"
        }
    }

    fun modelFor(presetId: ModelPresetId): ModelDescriptor? = presetMappings
        .firstOrNull { it.presetId == presetId }
        ?.let { mapping -> models.firstOrNull { it.id == mapping.modelId } }
}

data class HarnessProfile(val id: String, val version: Int)

data class EgressConsent(
    val approvedAt: Instant,
    val providerId: ProviderId,
    val modelId: String,
    val contentFingerprint: String,
    /** Explicit cost disclosure for one request. It is never persisted in the Invocation ledger. */
    val costDisclosure: CostDisclosure? = null,
)

/**
 * The user acknowledges an estimate/price-version before one egress. `null` cost means that
 * the provider did not publish a usable estimate; it never means free.
 */
data class CostDisclosure(
    val acknowledgedAt: Instant,
    val priceVersion: String?,
    val currencyCode: String?,
    val estimatedCostMicros: Long?,
) {
    init {
        require(estimatedCostMicros == null || estimatedCostMicros >= 0) { "预估费用不能为负数。" }
        require(currencyCode == null || currencyCode.matches(Regex("[A-Z]{3}"))) { "货币必须是 ISO 三位大写代码。" }
        require(estimatedCostMicros == null || (!priceVersion.isNullOrBlank() && currencyCode != null)) {
            "已知预估费用必须携带价格版本和货币。"
        }
    }
}

data class AiTask(
    val id: AiTaskId,
    val draftId: CaptureDraftId,
    val providerId: ProviderId,
    val model: ModelDescriptor,
    val harness: HarnessProfile,
    val consent: EgressConsent?,
    val createdAt: Instant,
)

data class GeneratedCandidate(
    val id: CandidateId,
    val taskId: AiTaskId,
    val title: String,
    val body: String,
    val generatedAt: Instant,
    val schemaVersion: Int = 1,
)

/** Candidate content is local, reviewable work and never a Knowledge Item by itself. */
enum class CandidateReviewStatus { PENDING_REVIEW, SAVED, DISCARDED }

data class StoredGeneratedCandidate(
    val candidate: GeneratedCandidate,
    val draftId: CaptureDraftId,
    val invocationId: InvocationId,
    val providerId: ProviderId,
    val modelId: String,
    val harnessVersion: Int,
    val status: CandidateReviewStatus,
    val updatedAt: Instant,
) {
    init {
        require(candidate.title.isNotBlank() || candidate.body.isNotBlank()) { "候选内容不能为空。" }
    }
}

data class ProviderUsage(
    val inputTokens: Long? = null,
    val outputTokens: Long? = null,
    val totalTokens: Long? = null,
    val cachedInputTokens: Long? = null,
    /** Provider-reported subset of [outputTokens] spent on hidden reasoning. */
    val reasoningTokens: Long? = null,
) {
    init {
        listOf(inputTokens, outputTokens, totalTokens, cachedInputTokens, reasoningTokens).forEach { value ->
            require(value == null || value >= 0) { "Token 用量不能为负数。" }
        }
        require(reasoningTokens == null || outputTokens == null || reasoningTokens <= outputTokens) {
            "推理 Token 不能超过总输出 Token。"
        }
    }
}

/** `totalMicros == null` is unknown; `totalMicros == 0L` is verified free/local cost. */
data class ProviderCost(
    val priceVersion: String? = null,
    val currencyCode: String? = null,
    val totalMicros: Long? = null,
) {
    init {
        require(totalMicros == null || totalMicros >= 0) { "费用不能为负数。" }
        require(currencyCode == null || currencyCode.matches(Regex("[A-Z]{3}"))) { "货币必须是 ISO 三位大写代码。" }
        require(totalMicros == null || (!priceVersion.isNullOrBlank() && currencyCode != null)) {
            "已知费用必须携带价格版本和货币。"
        }
    }

    val isUnknown: Boolean get() = totalMicros == null
    val isVerifiedFreeOrLocal: Boolean get() = totalMicros == 0L
}

enum class TaskRunStatus { SUCCEEDED, FAILED, CANCELLED, BLOCKED }

enum class ProviderAttemptStatus { SUCCEEDED, FAILED, CANCELLED }

enum class GenerationStatus { COMPLETED, REJECTED }

enum class ValidationStatus { PASSED, FAILED, SKIPPED }

data class GenerationValidation(
    val id: ValidationId,
    val completedAt: Instant,
    val status: ValidationStatus,
    val outputContractVersion: Int,
    val error: AiTaskError? = null,
) {
    init {
        require(outputContractVersion > 0) { "输出合同版本必须为正数。" }
        require(
            when (status) {
                ValidationStatus.PASSED, ValidationStatus.SKIPPED -> error == null
                ValidationStatus.FAILED -> error != null
            },
        ) { "校验状态与错误必须一致。" }
    }
}

data class GenerationRecord(
    val id: GenerationId,
    val createdAt: Instant,
    val status: GenerationStatus,
    val validation: GenerationValidation,
) {
    init {
        require(status != GenerationStatus.COMPLETED || validation.status == ValidationStatus.PASSED) {
            "已完成生成必须通过确定性校验。"
        }
    }
}

data class ProviderAttempt(
    val id: ProviderAttemptId,
    val providerId: ProviderId,
    val modelId: String,
    val registrySnapshotId: ModelRegistrySnapshotId?,
    val startedAt: Instant,
    val completedAt: Instant,
    val status: ProviderAttemptStatus,
    val error: AiTaskError? = null,
    val usage: ProviderUsage = ProviderUsage(),
    val cost: ProviderCost = ProviderCost(),
    val generation: GenerationRecord? = null,
) {
    init {
        require(!completedAt.isBefore(startedAt)) { "Provider Attempt 完成时间不能早于开始时间。" }
        require((status == ProviderAttemptStatus.SUCCEEDED) == (error == null)) { "Attempt 状态与错误必须一致。" }
        require(status != ProviderAttemptStatus.SUCCEEDED || generation != null) { "成功 Attempt 必须包含 Generation。" }
    }
}

data class TaskRun(
    val id: TaskRunId,
    val taskId: AiTaskId,
    val startedAt: Instant,
    val completedAt: Instant,
    val status: TaskRunStatus,
    val attempts: List<ProviderAttempt>,
) {
    init {
        require(!completedAt.isBefore(startedAt)) { "Task Run 完成时间不能早于开始时间。" }
        when (status) {
            TaskRunStatus.SUCCEEDED -> require(attempts.any { it.status == ProviderAttemptStatus.SUCCEEDED }) {
                "成功 Task Run 必须包含成功 Attempt。"
            }
            TaskRunStatus.FAILED -> require(attempts.isNotEmpty()) { "失败 Task Run 必须包含已发生的 Attempt。" }
            TaskRunStatus.CANCELLED -> require(attempts.isNotEmpty()) { "取消 Task Run 必须包含已发生的 Attempt。" }
            TaskRunStatus.BLOCKED -> require(attempts.isEmpty()) { "被门禁阻止的 Task Run 不得伪造 Provider Attempt。" }
        }
    }
}

data class InvocationRecord(
    val id: InvocationId,
    val taskId: AiTaskId,
    val providerId: ProviderId,
    val modelId: String,
    val harnessVersion: Int,
    val completedAt: Instant,
    val status: InvocationStatus,
    val error: AiTaskError? = null,
    val registrySnapshotId: ModelRegistrySnapshotId? = null,
    val pricingVersion: String? = null,
    val usage: ProviderUsage = ProviderUsage(),
    val cost: ProviderCost = ProviderCost(),
    val taskRun: TaskRun = legacyTaskRun(
        invocationId = id,
        taskId = taskId,
        providerId = providerId,
        modelId = modelId,
        completedAt = completedAt,
        status = status,
        error = error,
        registrySnapshotId = registrySnapshotId,
        usage = usage,
        cost = cost,
    ),
) {
    init {
        require(taskRun.taskId == taskId) { "Invocation 与 Task Run 必须指向同一任务。" }
        require(taskRun.completedAt == completedAt) { "Invocation 与 Task Run 必须使用同一完成时间。" }
        require(taskRun.status == status.toTaskRunStatus()) { "Invocation 与 Task Run 状态必须一致。" }
        require(
            taskRun.attempts.all { it.providerId == providerId && it.modelId == modelId && it.registrySnapshotId == registrySnapshotId },
        ) { "Invocation Attempt 不得改写 Provider、Model 或 Registry 快照。" }
    }
}

enum class InvocationStatus { SUCCEEDED, FAILED, CANCELLED, BLOCKED }

private fun InvocationStatus.toTaskRunStatus(): TaskRunStatus = when (this) {
    InvocationStatus.SUCCEEDED -> TaskRunStatus.SUCCEEDED
    InvocationStatus.FAILED -> TaskRunStatus.FAILED
    InvocationStatus.CANCELLED -> TaskRunStatus.CANCELLED
    InvocationStatus.BLOCKED -> TaskRunStatus.BLOCKED
}

private fun legacyTaskRun(
    invocationId: InvocationId,
    taskId: AiTaskId,
    providerId: ProviderId,
    modelId: String,
    completedAt: Instant,
    status: InvocationStatus,
    error: AiTaskError?,
    registrySnapshotId: ModelRegistrySnapshotId?,
    usage: ProviderUsage,
    cost: ProviderCost,
): TaskRun {
    val attempt = when (status) {
        InvocationStatus.BLOCKED -> null
        InvocationStatus.SUCCEEDED -> ProviderAttempt(
            id = ProviderAttemptId("${invocationId.value}:attempt:1"),
            providerId = providerId,
            modelId = modelId,
            registrySnapshotId = registrySnapshotId,
            startedAt = completedAt,
            completedAt = completedAt,
            status = ProviderAttemptStatus.SUCCEEDED,
            usage = usage,
            cost = cost,
            generation = GenerationRecord(
                id = GenerationId("${invocationId.value}:generation:1"),
                createdAt = completedAt,
                status = GenerationStatus.COMPLETED,
                validation = GenerationValidation(
                    id = ValidationId("${invocationId.value}:validation:1"),
                    completedAt = completedAt,
                    status = ValidationStatus.PASSED,
                    outputContractVersion = 1,
                ),
            ),
        )
        InvocationStatus.FAILED -> ProviderAttempt(
            id = ProviderAttemptId("${invocationId.value}:attempt:1"),
            providerId = providerId,
            modelId = modelId,
            registrySnapshotId = registrySnapshotId,
            startedAt = completedAt,
            completedAt = completedAt,
            status = ProviderAttemptStatus.FAILED,
            error = error ?: AiTaskError.ProviderFailure,
            usage = usage,
            cost = cost,
        )
        InvocationStatus.CANCELLED -> ProviderAttempt(
            id = ProviderAttemptId("${invocationId.value}:attempt:1"),
            providerId = providerId,
            modelId = modelId,
            registrySnapshotId = registrySnapshotId,
            startedAt = completedAt,
            completedAt = completedAt,
            status = ProviderAttemptStatus.CANCELLED,
            error = error ?: AiTaskError.ProviderTimedOut,
            usage = usage,
            cost = cost,
        )
    }
    return TaskRun(
        id = TaskRunId(invocationId.value),
        taskId = taskId,
        startedAt = completedAt,
        completedAt = completedAt,
        status = status.toTaskRunStatus(),
        attempts = listOfNotNull(attempt),
    )
}

data class CandidateProvenance(
    val candidateId: CandidateId,
    val invocationId: InvocationId,
    val providerId: ProviderId,
    val modelId: String,
    val harnessVersion: Int,
)

data class KnowledgeItem(
    val id: KnowledgeItemId,
    val title: String,
    val body: String,
    val sourceEvidence: List<SourceEvidence>,
    val provenance: CandidateProvenance,
    val createdAt: Instant,
    val attachments: List<AttachmentReference> = emptyList(),
    val schemaVersion: Int = 1,
)

data class KnowledgeExportSnapshot(
    val protocolVersion: Int,
    val createdAt: Instant,
    val items: List<KnowledgeItem>,
)

sealed interface AiTaskError {
    data object ConsentRequired : AiTaskError
    data object TaskDraftMismatch : AiTaskError
    data object TextNotSupported : AiTaskError
    data object VisionNotSupported : AiTaskError
    data object ProviderFailure : AiTaskError
    data object CandidateNotConfirmed : AiTaskError
    data object ProvenanceMismatch : AiTaskError
    data object AttachmentNotReady : AiTaskError
    data object AttachmentImportFailed : AiTaskError
    data object AttachmentSourceUnavailable : AiTaskError
    data object AttachmentUnsupportedType : AiTaskError
    data object AttachmentTooLarge : AiTaskError
    data object AttachmentIntegrityMismatch : AiTaskError
    data object PersistenceConflict : AiTaskError
    data object CaptureTextBlank : AiTaskError
    data object ProviderConfigurationInvalid : AiTaskError
    data object ProviderCredentialMissing : AiTaskError
    data object ProviderCredentialInvalid : AiTaskError
    data object ProviderCredentialStorageFailed : AiTaskError
    data object ProviderAuthenticationFailed : AiTaskError
    data object ProviderBalanceInsufficient : AiTaskError
    data object ProviderRateLimited : AiTaskError
    data object ProviderTimedOut : AiTaskError
    data object ProviderNetworkUnavailable : AiTaskError
    data object ProviderUnavailable : AiTaskError
    data object ProviderResponseFormatInvalid : AiTaskError
    data object ProviderSchemaValidationFailed : AiTaskError
    data object ProviderContextOverflow : AiTaskError
    data object ProviderRequestCancelled : AiTaskError
    data object ProviderEgressNotAuthorized : AiTaskError
    data object ProviderBudgetDisclosureRequired : AiTaskError
    data object ProviderRequestContentUnsafe : AiTaskError
    data object ModelRegistrySnapshotInvalid : AiTaskError
    data object ModelRegistrySnapshotUnverified : AiTaskError
    data object ModelRegistryModelUnavailable : AiTaskError
}

package com.nanzhufeng.ai.domain

import java.io.ByteArrayInputStream
import java.time.Clock
import java.time.Instant
import java.util.Locale
import java.util.UUID

@JvmInline
value class GlmOcrTaskId(val value: String) {
    companion object { fun new(): GlmOcrTaskId = GlmOcrTaskId(UUID.randomUUID().toString()) }
}

enum class GlmOcrTaskStatus { READY, QUEUED, PROCESSING, COMPLETED, FAILED }

/**
 * Durable source/result lineage for one explicit document-conversion request. Provider bodies,
 * credentials and OCR text never enter this metadata row; the two attachment IDs point at the
 * verified private attachment catalog.
 */
data class GlmOcrTask(
    val id: GlmOcrTaskId,
    val sourceAttachmentId: AttachmentId,
    val resultAttachmentId: AttachmentId? = null,
    val sourceDisplayName: String,
    val sourceMimeType: String,
    val sourceByteCount: Long,
    val sourceSha256: String,
    val status: GlmOcrTaskStatus,
    val requestId: String? = null,
    val pageCount: Int? = null,
    val inputTokens: Long? = null,
    val outputTokens: Long? = null,
    val costCnyMicros: Long? = null,
    val safeErrorCode: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    init {
        require(sourceMimeType in GLM_OCR_ALLOWED_MIME_TYPES)
        require(sourceByteCount in 1..GLM_OCR_PDF_MAX_BYTES)
        require(sourceSha256.matches(Regex("[0-9a-f]{64}")))
        require(status != GlmOcrTaskStatus.COMPLETED || resultAttachmentId != null)
    }
}

val GLM_OCR_ALLOWED_MIME_TYPES = setOf("image/jpeg", "image/png", "application/pdf")
const val GLM_OCR_IMAGE_MAX_BYTES = 10L * 1024L * 1024L
const val GLM_OCR_PDF_MAX_BYTES = 50L * 1024L * 1024L

interface GlmOcrTaskRepository {
    fun save(task: GlmOcrTask): GlmOcrTask
    fun find(id: GlmOcrTaskId): GlmOcrTask?
    fun listNewestFirst(): List<GlmOcrTask>
    fun delete(id: GlmOcrTaskId): Boolean
}

data class GlmOcrTransportRequest(
    val source: AttachmentOpenResult.Opened,
    val mimeType: String,
)

sealed interface GlmOcrTransportResult {
    data class Completed(
        val markdown: String,
        val requestId: String?,
        val pageCount: Int?,
        val inputTokens: Long?,
        val outputTokens: Long?,
    ) : GlmOcrTransportResult

    /** Safe code only. Raw provider bodies must not cross the transport boundary. */
    data class Failed(val safeCode: String) : GlmOcrTransportResult
}

fun interface GlmOcrTransport {
    fun execute(request: GlmOcrTransportRequest, credential: CharArray): GlmOcrTransportResult
}

sealed interface GlmOcrImportResult {
    data class Imported(val task: GlmOcrTask) : GlmOcrImportResult
    data class Rejected(val reason: String) : GlmOcrImportResult
}

sealed interface GlmOcrProcessResult {
    data class Completed(val task: GlmOcrTask) : GlmOcrProcessResult
    data class Failed(val task: GlmOcrTask) : GlmOcrProcessResult
    data object Missing : GlmOcrProcessResult
}

sealed interface GlmOcrContinueResult {
    data class Created(val conversationId: ConversationId) : GlmOcrContinueResult
    data class Rejected(val reason: String) : GlmOcrContinueResult
}

sealed interface GlmOcrDeleteResult {
    data class Deleted(val removedAttachmentCount: Int, val retainedSharedAttachmentCount: Int) : GlmOcrDeleteResult
    data class Rejected(val reason: String) : GlmOcrDeleteResult
}

data class GlmOcrSourceDocument(
    val taskId: GlmOcrTaskId,
    val displayName: String,
    val mimeType: String,
    val byteCount: Long,
    val opened: AttachmentOpenResult.Opened,
)

enum class GlmOcrSearchDocumentKind { SOURCE, MARKDOWN }

/** Search-safe projection for the existing local search surface. */
data class GlmOcrDocumentSearchHit(
    val taskId: GlmOcrTaskId,
    val kind: GlmOcrSearchDocumentKind,
    val title: String,
    val attachment: ConversationAttachmentReference,
    val timestampEpochMs: Long,
    val matchSnippet: String? = null,
)

fun interface GlmOcrConversationDraftCreator {
    fun create(markdown: AttachmentReference): GlmOcrContinueResult
}

/** The only owner allowed to turn a private source into a GLM-OCR request and private Markdown. */
class GlmOcrTaskOwner(
    private val tasks: GlmOcrTaskRepository,
    private val privateStore: PrivateAttachmentStore,
    private val assets: PrivateAttachmentRepository,
    private val loadConfiguration: LoadModelServiceConfigurationUseCase,
    private val credentials: ProviderCredentialStore,
    private val transport: GlmOcrTransport,
    private val conversationDraftCreator: GlmOcrConversationDraftCreator,
    private val clock: Clock,
    private val invocations: InvocationRepository? = null,
) {
    fun import(selection: ConversationAttachmentSelection?): GlmOcrImportResult {
        if (selection == null) return GlmOcrImportResult.Rejected("没有选择文件，现有任务保持不变。")
        if (selection.mimeType !in GLM_OCR_ALLOWED_MIME_TYPES) {
            selection.input.close()
            return GlmOcrImportResult.Rejected("GLM-OCR 只支持 JPG、PNG 和 PDF。")
        }
        val imported = privateStore.import(AttachmentImportRequest(selection.input, selection.mimeType, selection.displayName))
        val importedSource = when (imported) {
            is AttachmentImportResult.Imported -> imported.attachment
            is AttachmentImportResult.Rejected -> return GlmOcrImportResult.Rejected(imported.error.importReason())
        }
        val bytes = requireNotNull(importedSource.byteCount)
        val max = if (importedSource.mimeType == "application/pdf") GLM_OCR_PDF_MAX_BYTES else GLM_OCR_IMAGE_MAX_BYTES
        if (bytes > max) {
            privateStore.deletePrivateCopy(importedSource)
            return GlmOcrImportResult.Rejected(
                if (importedSource.mimeType == "application/pdf") "PDF 不能超过 50 MB。" else "图片不能超过 10 MB。",
            )
        }
        val source = runCatching { assets.save(importedSource) }.getOrNull()
        if (source == null) {
            privateStore.deletePrivateCopy(importedSource)
            return GlmOcrImportResult.Rejected("文件已复制，但本机附件目录写入失败。")
        }
        val now = clock.instant()
        val task = GlmOcrTask(
            id = GlmOcrTaskId.new(),
            sourceAttachmentId = source.id,
            sourceDisplayName = source.displayName?.takeIf(String::isNotBlank) ?: if (source.mimeType == "application/pdf") "未命名.pdf" else "未命名图片",
            sourceMimeType = source.mimeType,
            sourceByteCount = bytes,
            sourceSha256 = requireNotNull(source.sha256),
            status = GlmOcrTaskStatus.READY,
            createdAt = now,
            updatedAt = now,
        )
        return runCatching { GlmOcrImportResult.Imported(tasks.save(task)) }.getOrElse {
            assets.deleteIfUnreferenced(source.id, privateStore::deletePrivateCopy)
            GlmOcrImportResult.Rejected("来源关系未能保存，文件没有被报告为可转换。")
        }
    }

    fun queue(id: GlmOcrTaskId): GlmOcrTask? {
        val task = tasks.find(id) ?: return null
        if (task.status !in setOf(GlmOcrTaskStatus.READY, GlmOcrTaskStatus.FAILED)) return task
        return tasks.save(task.copy(status = GlmOcrTaskStatus.QUEUED, safeErrorCode = null, updatedAt = clock.instant()))
    }

    fun failScheduling(id: GlmOcrTaskId): GlmOcrTask? {
        val task = tasks.find(id) ?: return null
        if (task.status != GlmOcrTaskStatus.QUEUED) return task
        return tasks.save(task.copy(status = GlmOcrTaskStatus.FAILED, safeErrorCode = "SCHEDULER_UNAVAILABLE", updatedAt = clock.instant()))
    }

    fun failUnexpectedExecution(id: GlmOcrTaskId): GlmOcrTask? {
        val task = tasks.find(id) ?: return null
        if (task.status == GlmOcrTaskStatus.COMPLETED) return task
        return tasks.save(task.copy(status = GlmOcrTaskStatus.FAILED, safeErrorCode = "INTERNAL_FAILURE", updatedAt = clock.instant()))
    }

    fun process(id: GlmOcrTaskId): GlmOcrProcessResult {
        val original = tasks.find(id) ?: return GlmOcrProcessResult.Missing
        if (original.status == GlmOcrTaskStatus.COMPLETED) return GlmOcrProcessResult.Completed(original)
        if (original.status !in setOf(GlmOcrTaskStatus.QUEUED, GlmOcrTaskStatus.PROCESSING)) {
            return GlmOcrProcessResult.Failed(original)
        }
        val startedAt = clock.instant()
        val invocationId = InvocationId.new()
        val processing = tasks.save(original.copy(status = GlmOcrTaskStatus.PROCESSING, updatedAt = startedAt))
        val configuration = loadConfiguration.execute(ProviderId.ZHIPU)
        if (configuration?.settings?.enabled != true) {
            return failAndAudit(processing, "ZHIPU_NOT_ENABLED", startedAt, invocationId, providerAttempted = false)
        }
        val credential = credentials.loadCredential(ProviderId.ZHIPU)
            ?: return failAndAudit(processing, "CREDENTIAL_MISSING", startedAt, invocationId, providerAttempted = false)
        try {
            val source = assets.findById(processing.sourceAttachmentId)
                ?: return failAndAudit(processing, "SOURCE_MISSING", startedAt, invocationId, providerAttempted = false)
            val opened = privateStore.openVerified(source) as? AttachmentOpenResult.Opened
                ?: return failAndAudit(processing, "SOURCE_INTEGRITY", startedAt, invocationId, providerAttempted = false)
            return when (val outcome = transport.execute(GlmOcrTransportRequest(opened, processing.sourceMimeType), credential)) {
                is GlmOcrTransportResult.Failed -> failAndAudit(
                    processing,
                    outcome.safeCode,
                    startedAt,
                    invocationId,
                    providerAttempted = true,
                )
                is GlmOcrTransportResult.Completed -> {
                    val result = persistCompleted(processing, outcome)
                    val auditedTask = when (result) {
                        is GlmOcrProcessResult.Completed -> result.task
                        is GlmOcrProcessResult.Failed -> result.task
                        GlmOcrProcessResult.Missing -> processing
                    }
                    saveInvocation(
                        task = auditedTask,
                        startedAt = startedAt,
                        invocationId = invocationId,
                        status = if (result is GlmOcrProcessResult.Completed) InvocationStatus.SUCCEEDED else InvocationStatus.FAILED,
                        safeCode = auditedTask.safeErrorCode,
                        providerAttempted = true,
                        usage = ProviderUsage(
                            inputTokens = outcome.inputTokens,
                            outputTokens = outcome.outputTokens,
                            totalTokens = listOfNotNull(outcome.inputTokens, outcome.outputTokens).sum().takeIf { it > 0 },
                        ),
                        costMicros = glmOcrCostMicros(outcome.inputTokens, outcome.outputTokens),
                    )
                    result
                }
            }
        } finally {
            credential.fill('\u0000')
        }
    }

    fun tasks(): List<GlmOcrTask> = tasks.listNewestFirst()

    /** Original sources and generated Markdown join the existing attachment search catalogue. */
    fun searchDocuments(query: String, category: ConversationSearchCategory): List<GlmOcrDocumentSearchHit> {
        if (category == ConversationSearchCategory.TEXT || category in setOf(ConversationSearchCategory.VIDEO, ConversationSearchCategory.AUDIO)) {
            return emptyList()
        }
        val normalized = query.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)
        return tasks.listNewestFirst().flatMap { task ->
            buildList {
                assets.findById(task.sourceAttachmentId)?.toConversationReferenceOrNull()?.let { source ->
                    if (category == ConversationSearchCategory.ALL || conversationAttachmentSearchCategory(source.mimeType) == category) {
                        val nameMatches = normalized.isBlank() || task.sourceDisplayName.lowercase(Locale.ROOT).contains(normalized)
                        if (nameMatches) add(
                            GlmOcrDocumentSearchHit(
                                taskId = task.id,
                                kind = GlmOcrSearchDocumentKind.SOURCE,
                                title = "南枫转写 · 原始文件",
                                attachment = source,
                                timestampEpochMs = task.createdAt.toEpochMilli(),
                            ),
                        )
                    }
                }
                task.resultAttachmentId?.let(assets::findById)?.toConversationReferenceOrNull()?.let { result ->
                    if (category == ConversationSearchCategory.ALL || conversationAttachmentSearchCategory(result.mimeType) == category) {
                        val displayName = result.displayName.orEmpty()
                        val nameMatches = normalized.isBlank() || displayName.lowercase(Locale.ROOT).contains(normalized) ||
                            task.sourceDisplayName.lowercase(Locale.ROOT).contains(normalized)
                        val contentSnippet = if (!nameMatches && normalized.isNotBlank()) searchMarkdownSnippet(result, normalized) else null
                        if (nameMatches || contentSnippet != null) add(
                            GlmOcrDocumentSearchHit(
                                taskId = task.id,
                                kind = GlmOcrSearchDocumentKind.MARKDOWN,
                                title = "南枫转写 · Markdown",
                                attachment = result,
                                timestampEpochMs = task.updatedAt.toEpochMilli(),
                                matchSnippet = contentSnippet,
                            ),
                        )
                    }
                }
            }
        }
    }

    fun markdown(id: GlmOcrTaskId): String? {
        val task = tasks.find(id)?.takeIf { it.status == GlmOcrTaskStatus.COMPLETED } ?: return null
        val result = task.resultAttachmentId?.let(assets::findById) ?: return null
        val bytes = (privateStore.read(result) as? AttachmentReadResult.Content)?.bytes ?: return null
        return bytes.toString(Charsets.UTF_8)
    }

    /** Returns only a freshly verified source stream; private paths never leave the owner. */
    fun sourceDocument(id: GlmOcrTaskId): GlmOcrSourceDocument? {
        val task = tasks.find(id) ?: return null
        val source = assets.findById(task.sourceAttachmentId) ?: return null
        val opened = privateStore.openVerified(source) as? AttachmentOpenResult.Opened ?: return null
        return GlmOcrSourceDocument(
            taskId = task.id,
            displayName = task.sourceDisplayName,
            mimeType = task.sourceMimeType,
            byteCount = task.sourceByteCount,
            opened = opened,
        )
    }

    /** In-app viewers and search resolve the exact same durable source reference. */
    fun sourceReference(id: GlmOcrTaskId): ConversationAttachmentReference? {
        val task = tasks.find(id) ?: return null
        return assets.findById(task.sourceAttachmentId)?.toConversationReferenceOrNull()
    }

    /** Generated Markdown is a normal durable attachment. Every viewer, search action and
     * transfer must resolve this reference instead of receiving OCR text through a side path. */
    fun resultReference(id: GlmOcrTaskId): ConversationAttachmentReference? {
        val task = tasks.find(id)?.takeIf { it.status == GlmOcrTaskStatus.COMPLETED } ?: return null
        return task.resultAttachmentId?.let(assets::findById)?.toConversationReferenceOrNull()
    }

    /** One explicit delete removes the OCR lineage first, then applies the normal shared-file
     * reference policy to both source and result. Active jobs cannot be deleted mid-execution. */
    fun delete(id: GlmOcrTaskId): GlmOcrDeleteResult {
        val task = tasks.find(id) ?: return GlmOcrDeleteResult.Rejected("这条南枫转写记录已不存在。")
        if (task.status == GlmOcrTaskStatus.QUEUED || task.status == GlmOcrTaskStatus.PROCESSING) {
            return GlmOcrDeleteResult.Rejected("正在处理的转写不能删除，请等待本轮结束。")
        }
        if (!tasks.delete(id)) return GlmOcrDeleteResult.Rejected("转写记录未能删除，本机文件保持不变。")
        val cleanup = listOfNotNull(task.sourceAttachmentId, task.resultAttachmentId).distinct().map { attachmentId ->
            assets.deleteIfUnreferenced(attachmentId, privateStore::deletePrivateCopy)
        }
        val failures = cleanup.count { it == PrivateAttachmentCleanupResult.DeleteFailed }
        if (failures > 0) {
            return GlmOcrDeleteResult.Rejected("转写记录已删除，但有 $failures 个本机文件清理失败；目录记录已保留，可稍后重试清理。")
        }
        return GlmOcrDeleteResult.Deleted(
            removedAttachmentCount = cleanup.count { it == PrivateAttachmentCleanupResult.Deleted || it == PrivateAttachmentCleanupResult.Missing },
            retainedSharedAttachmentCount = cleanup.count { it is PrivateAttachmentCleanupResult.Retained },
        )
    }

    fun continueInNewConversation(id: GlmOcrTaskId): GlmOcrContinueResult {
        val task = tasks.find(id)?.takeIf { it.status == GlmOcrTaskStatus.COMPLETED }
            ?: return GlmOcrContinueResult.Rejected("Markdown 结果尚未生成。")
        val result = task.resultAttachmentId?.let(assets::findById)
            ?: return GlmOcrContinueResult.Rejected("Markdown 文件未能从本机回读。")
        return conversationDraftCreator.create(result)
    }

    private fun persistCompleted(task: GlmOcrTask, outcome: GlmOcrTransportResult.Completed): GlmOcrProcessResult {
        if (outcome.markdown.isBlank()) return fail(task, "EMPTY_MARKDOWN")
        val bytes = outcome.markdown.toByteArray(Charsets.UTF_8)
        if (bytes.size > CONVERSATION_ATTACHMENT_MAX_BYTES) return fail(task, "MARKDOWN_TOO_LARGE")
        val resultName = task.sourceDisplayName.substringBeforeLast('.', task.sourceDisplayName).take(120).ifBlank { "GLM-OCR" } + "-OCR.md"
        val imported = privateStore.import(AttachmentImportRequest(ByteArrayInputStream(bytes), "text/markdown", resultName))
        val importedResult = when (imported) {
            is AttachmentImportResult.Imported -> imported.attachment
            is AttachmentImportResult.Rejected -> null
        } ?: return fail(task, "RESULT_PERSISTENCE")
        val result = runCatching { assets.save(importedResult) }.getOrNull()
        if (result == null) {
            privateStore.deletePrivateCopy(importedResult)
            return fail(task, "RESULT_PERSISTENCE")
        }
        val costMicros = glmOcrCostMicros(outcome.inputTokens, outcome.outputTokens)
        val completed = runCatching { tasks.save(task.copy(
            resultAttachmentId = result.id,
            status = GlmOcrTaskStatus.COMPLETED,
            requestId = outcome.requestId?.take(160),
            pageCount = outcome.pageCount,
            inputTokens = outcome.inputTokens,
            outputTokens = outcome.outputTokens,
            costCnyMicros = costMicros,
            safeErrorCode = null,
            updatedAt = clock.instant(),
        )) }.getOrElse {
            assets.deleteIfUnreferenced(result.id, privateStore::deletePrivateCopy)
            return fail(task, "RESULT_METADATA")
        }
        return GlmOcrProcessResult.Completed(completed)
    }

    private fun fail(task: GlmOcrTask, code: String): GlmOcrProcessResult.Failed = GlmOcrProcessResult.Failed(
        tasks.save(task.copy(status = GlmOcrTaskStatus.FAILED, safeErrorCode = code.take(80), updatedAt = clock.instant())),
    )

    private fun failAndAudit(
        task: GlmOcrTask,
        code: String,
        startedAt: Instant,
        invocationId: InvocationId,
        providerAttempted: Boolean,
    ): GlmOcrProcessResult.Failed {
        val failed = fail(task, code)
        saveInvocation(
            task = failed.task,
            startedAt = startedAt,
            invocationId = invocationId,
            status = when {
                !providerAttempted -> InvocationStatus.BLOCKED
                code == "CANCELLED" -> InvocationStatus.CANCELLED
                else -> InvocationStatus.FAILED
            },
            safeCode = code,
            providerAttempted = providerAttempted,
        )
        return failed
    }

    private fun saveInvocation(
        task: GlmOcrTask,
        startedAt: Instant,
        invocationId: InvocationId,
        status: InvocationStatus,
        safeCode: String?,
        providerAttempted: Boolean,
        usage: ProviderUsage = ProviderUsage(),
        costMicros: Long? = null,
    ) {
        val repository = invocations ?: return
        val completedAt = task.updatedAt.coerceAtLeast(startedAt)
        val taskId = task.toInvocationTaskId()
        val error = safeCode?.glmOcrAiTaskError()
        val cost = ProviderCost(
            priceVersion = costMicros?.let { GLM_OCR_PRICE_VERSION },
            currencyCode = costMicros?.let { "CNY" },
            totalMicros = costMicros,
        )
        val attempt = if (providerAttempted) ProviderAttempt(
            id = ProviderAttemptId("${invocationId.value}:attempt:1"),
            providerId = ProviderId.ZHIPU,
            modelId = GLM_OCR_MODEL_ID,
            registrySnapshotId = null,
            startedAt = startedAt,
            completedAt = completedAt,
            status = when (status) {
                InvocationStatus.SUCCEEDED -> ProviderAttemptStatus.SUCCEEDED
                InvocationStatus.CANCELLED -> ProviderAttemptStatus.CANCELLED
                else -> ProviderAttemptStatus.FAILED
            },
            error = if (status == InvocationStatus.SUCCEEDED) null else error ?: AiTaskError.ProviderFailure,
            usage = usage,
            cost = cost,
            generation = if (status == InvocationStatus.SUCCEEDED) GenerationRecord(
                id = GenerationId("${invocationId.value}:generation:1"),
                createdAt = completedAt,
                status = GenerationStatus.COMPLETED,
                validation = GenerationValidation(
                    id = ValidationId("${invocationId.value}:validation:1"),
                    completedAt = completedAt,
                    status = ValidationStatus.PASSED,
                    outputContractVersion = 1,
                ),
            ) else null,
        ) else null
        repository.save(
            InvocationRecord(
                id = invocationId,
                taskId = taskId,
                providerId = ProviderId.ZHIPU,
                modelId = GLM_OCR_MODEL_ID,
                harnessVersion = 1,
                completedAt = completedAt,
                status = status,
                error = error,
                pricingVersion = cost.priceVersion,
                usage = usage,
                cost = cost,
                taskRun = TaskRun(
                    id = TaskRunId("${invocationId.value}:run"),
                    taskId = taskId,
                    startedAt = startedAt,
                    completedAt = completedAt,
                    status = when (status) {
                        InvocationStatus.SUCCEEDED -> TaskRunStatus.SUCCEEDED
                        InvocationStatus.FAILED -> TaskRunStatus.FAILED
                        InvocationStatus.CANCELLED -> TaskRunStatus.CANCELLED
                        InvocationStatus.BLOCKED -> TaskRunStatus.BLOCKED
                    },
                    attempts = listOfNotNull(attempt),
                ),
            ),
        )
    }

    private fun searchMarkdownSnippet(reference: ConversationAttachmentReference, normalizedQuery: String): String? {
        val asset = assets.findById(reference.id) ?: return null
        val opened = privateStore.openVerified(asset) as? AttachmentOpenResult.Opened ?: return null
        return runCatching {
            opened.open().bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.firstNotNullOfOrNull { line ->
                    val normalizedLine = line.lowercase(Locale.ROOT)
                    val index = normalizedLine.indexOf(normalizedQuery)
                    if (index < 0) null else {
                        val start = (index - 36).coerceAtLeast(0)
                        val end = (index + normalizedQuery.length + 72).coerceAtMost(line.length)
                        line.substring(start, end).trim().takeIf(String::isNotBlank)
                    }
                }
            }
        }.getOrNull()
    }
}

fun GlmOcrTask.toInvocationTaskId(): AiTaskId = AiTaskId("glm-ocr:${id.value}")

private const val GLM_OCR_MODEL_ID = "glm-ocr"
private const val GLM_OCR_PRICE_VERSION = "glm-ocr-cny-0.2-per-million-v1"

private fun glmOcrCostMicros(inputTokens: Long?, outputTokens: Long?): Long? {
    val totalTokens = listOfNotNull(inputTokens, outputTokens).sum().takeIf { it > 0 } ?: return null
    // Official GLM-OCR price represented by the existing product contract: CNY 0.2 / million tokens.
    return (totalTokens * 200_000L + 999_999L) / 1_000_000L
}

private fun AttachmentReference.toConversationReferenceOrNull(): ConversationAttachmentReference? =
    runCatching { toConversationReference() }.getOrNull()

private fun String.glmOcrAiTaskError(): AiTaskError = when (this) {
    "ZHIPU_NOT_ENABLED" -> AiTaskError.ProviderConfigurationInvalid
    "CREDENTIAL_MISSING" -> AiTaskError.ProviderCredentialMissing
    "SOURCE_MISSING" -> AiTaskError.AttachmentSourceUnavailable
    "SOURCE_INTEGRITY" -> AiTaskError.AttachmentIntegrityMismatch
    "AUTHENTICATION" -> AiTaskError.ProviderAuthenticationFailed
    "BALANCE" -> AiTaskError.ProviderBalanceInsufficient
    "RATE_LIMIT" -> AiTaskError.ProviderRateLimited
    "TIMEOUT_UNKNOWN", "HTTP_408_UNKNOWN" -> AiTaskError.ProviderTimedOut
    "NETWORK_UNKNOWN" -> AiTaskError.ProviderNetworkUnavailable
    "CANCELLED" -> AiTaskError.ProviderRequestCancelled
    "SOURCE_TOO_LARGE", "MARKDOWN_TOO_LARGE" -> AiTaskError.AttachmentTooLarge
    "RESPONSE_FORMAT", "EMPTY_MARKDOWN", "RESPONSE_TOO_LARGE", "UNEXPECTED_STREAM" -> AiTaskError.ProviderResponseFormatInvalid
    "RESULT_PERSISTENCE", "RESULT_METADATA" -> AiTaskError.PersistenceConflict
    else -> if (startsWith("HTTP_5")) AiTaskError.ProviderUnavailable else AiTaskError.ProviderFailure
}

private fun AiTaskError.importReason(): String = when (this) {
    AiTaskError.AttachmentTooLarge -> "文件超过本机安全处理上限。"
    AiTaskError.AttachmentUnsupportedType -> "文件类型或真实内容不受支持。"
    AiTaskError.AttachmentIntegrityMismatch -> "文件完整性校验失败。"
    else -> "文件未能安全复制到本机。"
}

package com.nanzhufeng.ai.domain

import java.time.Instant
import java.util.UUID

const val KNOWLEDGE_EXPORT_FORMAT = "nanfeng-ai.knowledge-export"
const val KNOWLEDGE_EXPORT_PROTOCOL_VERSION = 1
const val KNOWLEDGE_EXPORT_PAYLOAD_SCHEMA = "nanfeng-ai.knowledge-payload"
const val KNOWLEDGE_EXPORT_PAYLOAD_SCHEMA_VERSION = 1
const val KNOWLEDGE_EXPORT_ATTACHMENT_POLICY = "metadata-references-only; no attachment bytes, display names, local paths, or external uris"

data class KnowledgeExportSource(
    val sourceType: CaptureSourceType,
    val receivedAt: Instant,
    val sourceReference: String?,
    val contributedFields: List<String>,
)

/** Deliberately has no local storage key, display name, or attachment bytes. */
data class KnowledgeExportAttachment(
    val id: String,
    val mimeType: String,
    val byteCount: Long,
    val sha256: String,
)

data class KnowledgeExportItem(
    val id: String,
    val schemaVersion: Int,
    val createdAt: Instant,
    val title: String,
    val body: String,
    val sources: List<KnowledgeExportSource>,
    val candidateId: String,
    val invocationId: String,
    val providerId: String,
    val modelId: String,
    val harnessVersion: Int,
    val attachments: List<KnowledgeExportAttachment>,
)

data class KnowledgeExportPayload(
    val schema: String = KNOWLEDGE_EXPORT_PAYLOAD_SCHEMA,
    val schemaVersion: Int = KNOWLEDGE_EXPORT_PAYLOAD_SCHEMA_VERSION,
    val items: List<KnowledgeExportItem>,
)

data class KnowledgeExportFileEntry(
    val path: String,
    val byteCount: Long,
    val sha256: String,
)

data class KnowledgeExportManifest(
    val format: String = KNOWLEDGE_EXPORT_FORMAT,
    val protocolVersion: Int = KNOWLEDGE_EXPORT_PROTOCOL_VERSION,
    val exportId: String,
    val exportedAt: Instant,
    val knowledgeCount: Int,
    val knowledgeSchemaVersions: List<Int>,
    val attachmentPolicy: String = KNOWLEDGE_EXPORT_ATTACHMENT_POLICY,
    val excludedFields: List<String>,
    val files: List<KnowledgeExportFileEntry>,
)

data class VerifiedKnowledgeExport(
    val manifest: KnowledgeExportManifest,
    val payload: KnowledgeExportPayload,
    val fileName: String,
    val relativeLocation: String,
    val byteCount: Long,
    val sha256: String,
)

sealed interface KnowledgeExportResult {
    data class Success(val export: VerifiedKnowledgeExport) : KnowledgeExportResult
    data object EmptyKnowledge : KnowledgeExportResult
    data object Cancelled : KnowledgeExportResult
    data class Failed(val reason: KnowledgeExportFailure) : KnowledgeExportResult
}

enum class KnowledgeExportFailure {
    OUTPUT_UNAVAILABLE,
    WRITE_FAILED,
    READ_BACK_FAILED,
    INTEGRITY_MISMATCH,
    INVALID_PACKAGE,
}

interface KnowledgeExportStore {
    fun write(payload: KnowledgeExportPayload, exportedAt: Instant, cancelled: () -> Boolean = { false }): KnowledgeExportResult
    fun latestVerified(): KnowledgeExportResult?
}

/**
 * The export projection is intentionally separate from [KnowledgeItem]: it keeps only
 * portable Knowledge facts and safe provenance, so Candidate/ledger objects cannot
 * become an accidental second export source.
 */
class ExportKnowledgePackageUseCase(
    private val repository: KnowledgeRepository,
    private val store: KnowledgeExportStore,
    private val clock: java.time.Clock,
) {
    fun execute(cancelled: () -> Boolean = { false }): KnowledgeExportResult {
        if (cancelled()) return KnowledgeExportResult.Cancelled
        val items = repository.listAll()
        if (items.isEmpty()) return KnowledgeExportResult.EmptyKnowledge
        val payload = KnowledgeExportPayload(items = items
            .sortedWith(compareByDescending<KnowledgeItem> { it.createdAt }.thenByDescending { it.id.value })
            .map(KnowledgeExportMapper::item))
        return store.write(payload, clock.instant(), cancelled)
    }

    fun latestVerified(): KnowledgeExportResult? = store.latestVerified()
}

object KnowledgeExportMapper {
    val excludedFields = listOf(
        "apiKey",
        "prompt",
        "providerRawResponse",
        "invocationLedgerFields",
        "candidateDraftFields",
        "attachmentBytes",
        "attachmentDisplayName",
        "attachmentLocalPath",
        "attachmentExternalUri",
    )

    fun item(item: KnowledgeItem): KnowledgeExportItem {
        require(item.attachments.all { it.isReadyPrivateCopy() }) { "知识附件尚未准备好，不能导出。" }
        return KnowledgeExportItem(
            id = item.id.value,
            schemaVersion = item.schemaVersion,
            createdAt = item.createdAt,
            title = item.title,
            body = item.body,
            sources = item.sourceEvidence.map { source ->
                KnowledgeExportSource(
                    sourceType = source.sourceType,
                    receivedAt = source.receivedAt,
                    sourceReference = source.sourceReference?.takeIf(::isSafeSourceReference),
                    contributedFields = source.contributedFields.filter(::isSafeFieldName).sorted(),
                )
            },
            candidateId = item.provenance.candidateId.value,
            invocationId = item.provenance.invocationId.value,
            providerId = item.provenance.providerId.name,
            modelId = item.provenance.modelId,
            harnessVersion = item.provenance.harnessVersion,
            attachments = item.attachments.map { attachment ->
                KnowledgeExportAttachment(
                    id = attachment.id.value,
                    mimeType = attachment.mimeType,
                    byteCount = requireNotNull(attachment.byteCount),
                    sha256 = requireNotNull(attachment.sha256),
                )
            },
        )
    }

    fun newExportId(): String = "export:${UUID.randomUUID()}"

    private fun isSafeSourceReference(value: String): Boolean =
        value.length in 1..128 && value.all { it.isAsciiLetterOrDigit() || it in ".:_-" }

    private fun isSafeFieldName(value: String): Boolean =
        value.length in 1..64 && value.all { it.isAsciiLetterOrDigit() || it in "._-" }

    private fun Char.isAsciiLetterOrDigit(): Boolean =
        this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9'
}

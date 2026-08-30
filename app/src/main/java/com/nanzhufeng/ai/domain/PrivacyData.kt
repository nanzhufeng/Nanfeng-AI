package com.nanzhufeng.ai.domain

/** P5-C contains only aggregate facts; no business body is valid diagnostic input. */
enum class PrivacyDeleteScope(val wireValue: String, val requiresPhrase: Boolean) {
    TEMPORARY_FAILED_TASK_ASSETS("temporary_failed_task_assets", false),
    ORPHANED_ATTACHMENT_FILES("orphaned_attachment_files", false),
    OFFLINE_EVAL_RUNS("offline_eval_runs", false),
    KNOWLEDGE_MEMORY_TRASH("knowledge_memory_trash", false),
    ALL_LOCAL_BUSINESS_DATA("all_local_business_data", true),
}

data class PrivacyAggregate(val key: String, val count: Long, val byteCount: Long = 0)
data class PrivacyInventory(
    val aggregates: List<PrivacyAggregate>,
    val credentialReferencePresent: Boolean,
    val internetPermissionPresent: Boolean,
    val importedZipCleanup: ImportedZipCleanupStatus = ImportedZipCleanupStatus(),
)

data class ImportedZipCleanupStatus(
    /** Original packages still occupying app-private storage. Their byte size is deliberately not user-facing. */
    val originalPackageCount: Long = 0,
    /** Attachments already linked into normal app data, including archive-backed attachments. */
    val importedAttachmentCount: Long = 0,
    val importedAttachmentByteCount: Long = 0,
    /** Linked attachment records that have not yet become independently managed local files. */
    val sourceDependentAttachmentCount: Long = 0,
    /** Packages whose conversation/attachment recovery has not reached a safe terminal state. */
    val blockedPackageCount: Long = 0,
    /** Packages already staged for deletion but whose final filesystem cleanup must be retried. */
    val pendingDeletionCount: Long = 0,
) {
    val canDeleteOriginalPackages: Boolean
        get() = originalPackageCount > 0 && blockedPackageCount == 0L

    /** Safe for a direct source-package deletion because no linked data still reads from ZIP. */
    val allImportedAttachmentsManaged: Boolean
        get() = sourceDependentAttachmentCount == 0L
}

sealed interface ImportedZipCleanupResult {
    data class Completed(
        val deletedPackageCount: Long,
        val materializedAttachmentCount: Long,
    ) : ImportedZipCleanupResult
    data class Partial(
        val deletedPackageCount: Long,
        val remainingPackageCount: Long,
        val reason: String,
    ) : ImportedZipCleanupResult
    data class Rejected(val reason: String) : ImportedZipCleanupResult
}
data class PrivacyDeletionPreview(
    val scope: PrivacyDeleteScope,
    val aggregates: List<PrivacyAggregate>,
    val fingerprint: String,
    /** Only populated for the explicit task-asset deletion flow. Never contains a URI, path or body. */
    val taskCandidates: List<PrivacyTaskDeletionCandidate> = emptyList(),
)
enum class PrivacyTaskAdapter(val wireValue: String) { MARKDOWN("markdown"), JSON("json"), PDF("pdf"), WEB("web") }
data class PrivacyTaskDeletionCandidate(
    /** Opaque app-local selection token. The UI displays only [safeIdSummary]. */
    val selectionId: String,
    val adapter: PrivacyTaskAdapter,
    val safeIdSummary: String,
    val status: String,
    val privateAssetCount: Long,
    val privateAssetByteCount: Long,
    /** Deleting a failed task removes its Room failure fact; this is deliberately visible before confirmation. */
    val failureEvidenceWillBeRemoved: Boolean,
)

/** P5-C's narrow task-asset rule; it is not a general-purpose cleanup policy. */
object PrivacyTaskDeletionPolicy {
    private val terminalDeletable = setOf("FAILED", "CANCELLED")
    fun canSelect(status: String, hasActiveKnowledgeReference: Boolean, hasActiveExportReference: Boolean, privateBoundarySafe: Boolean): Boolean =
        status in terminalDeletable && !hasActiveKnowledgeReference && !hasActiveExportReference && privateBoundarySafe
    fun isRetryableRemaining(status: String) = status == "FAILED_CLEANUP"
}
data class PrivacyDeletionRequest(
    val scope: PrivacyDeleteScope,
    val previewFingerprint: String,
    val confirmationPhrase: String = "",
    val selectedTaskIds: Set<String> = emptySet(),
)
sealed interface PrivacyDeletionResult {
    data class Completed(val deleted: List<PrivacyAggregate>) : PrivacyDeletionResult
    data class Rejected(val reason: String) : PrivacyDeletionResult
    data class Partial(val deleted: List<PrivacyAggregate>, val retryableFailureCount: Int) : PrivacyDeletionResult
}

data class SecurityDiagnosticArtifact(val fileName: String, val sha256: String, val byteCount: Long)
sealed interface SecurityDiagnosticResult {
    data class Completed(val artifact: SecurityDiagnosticArtifact) : SecurityDiagnosticResult
    data class Rejected(val reason: String) : SecurityDiagnosticResult
    data class Failed(val reason: String) : SecurityDiagnosticResult
}

interface PrivacyDataManager {
    fun inventory(): PrivacyInventory
    fun preview(scope: PrivacyDeleteScope, selectedTaskIds: Set<String> = emptySet()): PrivacyDeletionPreview
    fun delete(request: PrivacyDeletionRequest): PrivacyDeletionResult
    /** Explicit only: process recreation must never call this automatically. */
    fun retryFailedTaskDeletion(): PrivacyDeletionResult
    /** Explicit only: first materialize every linked imported attachment, then remove safe original packages. */
    fun cleanupImportedZipPackages(): ImportedZipCleanupResult
    fun exportSecurityDiagnostic(destination: android.net.Uri): SecurityDiagnosticResult
}

object SecurityDiagnosticAllowlist {
    const val FORMAT = "nanfeng-ai.security-diagnostic"
    const val VERSION = 1
    private val forbidden = Regex("(?i)(https?://|content:|file:|/|\\\\|\\\"(?:body|title|prompt|endpoint|url|uri|path|key|token|authorization|attachment)\\\"\\s*:|\\\"[^\\\"]*(?:api[ _-]?key|password|token|authorization)[^\\\"]*\\\"\\s*:|authorization\\s*[:=]|bearer\\s+[A-Za-z0-9._~+/-]{8,}|api[ _-]?key\\s*[:=]|password\\s*[:=]|token\\s*[:=])")

    /** Reject the entire artifact: redaction is not safe enough for a diagnostic protocol. */
    fun accepts(json: String): Boolean = forbidden.containsMatchIn(json).not() && MemoryDomain.sensitiveRejection(json) == null
    fun errorCode(value: String?): String? = value
        ?.takeIf { it.matches(Regex("[A-Z][A-Z0-9_]{0,63}")) }
}

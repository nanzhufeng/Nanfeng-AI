package com.nanzhufeng.ai.domain

import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.util.UUID

/** P4-C identities are protocol values, never Room row ids or text labels. */
@JvmInline value class MemoryId(val value: String) { companion object { fun new() = MemoryId(UUID.randomUUID().toString()) } }
@JvmInline value class MemoryRevisionId(val value: String) { companion object { fun new() = MemoryRevisionId(UUID.randomUUID().toString()) } }
@JvmInline value class MemoryIntentId(val value: String) { companion object { fun new() = MemoryIntentId(UUID.randomUUID().toString()) } }
@JvmInline value class MemoryConflictId(val value: String) { companion object { fun new() = MemoryConflictId(UUID.randomUUID().toString()) } }

enum class MemoryScopeKind { GLOBAL, PROJECT, CONVERSATION }
enum class MemorySource { USER_CONFIRMED, MANUAL_IMPORT }
enum class MemoryStatus { ACTIVE, PAUSED, DELETED }
enum class MemoryIntentAction { CREATE, UPDATE, PAUSE, RESTORE, DELETE, BULK_DELETE, RESOLVE_CONFLICT }
enum class MemoryConflictResolution { KEEP_EXISTING, CREATE_PARALLEL, REVISE_EXISTING }
enum class MemoryConflictStatus { PENDING, RESOLVED }

/** Project and Conversation relations are intentionally exclusive and must be validated by the repository. */
data class MemoryScope(
    val kind: MemoryScopeKind,
    val projectId: ProjectId? = null,
    val conversationId: ConversationId? = null,
) {
    init {
        require((kind == MemoryScopeKind.PROJECT) == (projectId != null)) { "Project 范围必须且只能关联一个 Project。" }
        require((kind == MemoryScopeKind.CONVERSATION) == (conversationId != null)) { "Conversation 范围必须且只能关联一个 Conversation。" }
        require(projectId == null || conversationId == null) { "Memory 不能同时关联 Project 与 Conversation。" }
    }
    fun stableKey(): String = "$kind|${projectId?.value.orEmpty()}|${conversationId?.value.orEmpty()}"
}

data class MemoryItem(
    val id: MemoryId,
    val title: String,
    val body: String,
    val scope: MemoryScope,
    val source: MemorySource,
    val sourceStableId: String,
    val sourceSummary: String,
    val status: MemoryStatus,
    val contentHash: String,
    val conceptHash: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val lastConfirmedAt: Instant,
    val deletedAt: Instant? = null,
    val schemaVersion: Int = 1,
)

/** Every material content or state change appends a snapshot; history is never overwritten. */
data class MemoryRevision(
    val id: MemoryRevisionId,
    val memoryId: MemoryId,
    val revision: Int,
    val title: String,
    val body: String,
    val scope: MemoryScope,
    val source: MemorySource,
    val sourceStableId: String,
    val sourceSummary: String,
    val status: MemoryStatus,
    val contentHash: String,
    val createdAt: Instant,
    val schemaVersion: Int = 1,
)

data class MemorySnapshot(val memory: MemoryItem, val revisions: List<MemoryRevision>) {
    init { require(revisions.map { it.revision }.distinct().size == revisions.size) }
}

data class MemoryConflict(
    val id: MemoryConflictId,
    val intentId: MemoryIntentId,
    val existingMemoryId: MemoryId,
    val candidateTitle: String,
    val candidateBody: String,
    val candidateScope: MemoryScope,
    val candidateSource: MemorySource,
    val candidateSourceStableId: String,
    val candidateSourceSummary: String,
    val candidateContentHash: String,
    val status: MemoryConflictStatus,
    val createdAt: Instant,
    val resolvedAt: Instant? = null,
)

data class MemoryIntent(
    val id: MemoryIntentId,
    val action: MemoryIntentAction,
    val memoryId: MemoryId? = null,
    val title: String? = null,
    val body: String? = null,
    val scope: MemoryScope? = null,
    val source: MemorySource = MemorySource.USER_CONFIRMED,
    val sourceStableId: String = "user-action",
    val sourceSummary: String = "用户在本机确认的手工记忆操作",
    val memoryIds: List<MemoryId> = emptyList(),
    val conflictId: MemoryConflictId? = null,
    val conflictResolution: MemoryConflictResolution? = null,
)

sealed interface MemoryMutationResult {
    data class Applied(val snapshots: List<MemorySnapshot>) : MemoryMutationResult
    data class Replayed(val snapshots: List<MemorySnapshot>) : MemoryMutationResult
    data class Duplicate(val existing: MemorySnapshot) : MemoryMutationResult
    data class Conflict(val conflict: MemoryConflict) : MemoryMutationResult
    data class Rejected(val code: MemoryRejectionCode) : MemoryMutationResult
}

enum class MemoryRejectionCode {
    EMPTY_TITLE, TITLE_TOO_LONG, BODY_EMPTY, BODY_TOO_LONG, CONTROL_CHARACTER,
    HIGH_SENSITIVITY_PASSWORD, HIGH_SENSITIVITY_API_KEY, HIGH_SENSITIVITY_AUTHORIZATION,
    HIGH_SENSITIVITY_RECOVERY_CODE, HIGH_SENSITIVITY_PAYMENT_CARD,
    INVALID_SCOPE_REFERENCE, MISSING_MEMORY, INVALID_ACTION, INTENT_MISMATCH, MISSING_CONFLICT,
}

interface MemoryRepository {
    fun mutate(intent: MemoryIntent, fingerprint: String): MemoryMutationResult
    fun findById(id: MemoryId): MemorySnapshot?
    fun list(scope: MemoryScopeKind?, status: MemoryStatus?, search: String): List<MemorySnapshot>
    fun conflict(id: MemoryConflictId): MemoryConflict?
}

/** P4-C semantic owner. It rejects unsafe content before any Room mutation or conflict persistence. */
class MemoryDomain(private val clock: Clock) {
    fun validate(intent: MemoryIntent): MemoryRejectionCode? = runCatching {
        when (intent.action) {
            MemoryIntentAction.CREATE, MemoryIntentAction.UPDATE -> validateContent(requireNotNull(intent.title), requireNotNull(intent.body), requireNotNull(intent.scope))
            MemoryIntentAction.RESOLVE_CONFLICT -> require(intent.conflictId != null && intent.conflictResolution != null)
            MemoryIntentAction.PAUSE, MemoryIntentAction.RESTORE, MemoryIntentAction.DELETE -> requireNotNull(intent.memoryId)
            MemoryIntentAction.BULK_DELETE -> require(intent.memoryIds.isNotEmpty())
        }
    }.exceptionOrNull()?.let { mapFailure(it) }

    fun normalizedTitle(raw: String): String = raw.trim().also {
        require(it.isNotEmpty()) { "title" }; require(it.codePointCount(0, it.length) <= MAX_TITLE_CODE_POINTS) { "title length" }
        require(it.none { c -> c.isISOControl() }) { "control" }
    }
    fun normalizedBody(raw: String): String = raw.trim().also {
        require(it.isNotEmpty()) { "body" }; require(it.codePointCount(0, it.length) <= MAX_BODY_CODE_POINTS) { "body length" }
        require(it.none { c -> c.isISOControl() && c != '\n' && c != '\t' }) { "control" }
        sensitiveRejection(it)?.let { code -> throw MemoryRejectedException(code) }
    }
    fun contentHash(title: String, body: String): String = sha256("${canonical(title)}\n${canonical(body)}")
    fun conceptHash(title: String): String = sha256(canonical(title))
    fun now(): Instant = clock.instant()

    private fun validateContent(title: String, body: String, scope: MemoryScope) {
        val safeTitle = normalizedTitle(title); val safeBody = normalizedBody(body)
        sensitiveRejection("$safeTitle\n$safeBody")?.let { throw MemoryRejectedException(it) }
        scope.stableKey()
    }
    private fun canonical(value: String): String = value.trim().lowercase().replace(Regex("\\s+"), " ")
    private fun mapFailure(error: Throwable): MemoryRejectionCode = when (error) {
        is MemoryRejectedException -> error.code
        else -> when {
            error.message == "title" -> MemoryRejectionCode.EMPTY_TITLE
            error.message == "title length" -> MemoryRejectionCode.TITLE_TOO_LONG
            error.message == "body" -> MemoryRejectionCode.BODY_EMPTY
            error.message == "body length" -> MemoryRejectionCode.BODY_TOO_LONG
            error.message == "control" -> MemoryRejectionCode.CONTROL_CHARACTER
            else -> MemoryRejectionCode.INVALID_ACTION
        }
    }
    private class MemoryRejectedException(val code: MemoryRejectionCode) : IllegalArgumentException()
    companion object {
        const val MAX_TITLE_CODE_POINTS = 120
        const val MAX_BODY_CODE_POINTS = 12_000
        fun sha256(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        /** Shared P4-C/P4-D high-sensitivity gate; callers must reject before exposing or persisting a body. */
        fun sensitiveRejection(value: String): MemoryRejectionCode? = when {
            Regex("(?i)\\b(?:password|passcode|pwd)\\s*[:=]").containsMatchIn(value) || Regex("密码\\s*[:：=]").containsMatchIn(value) -> MemoryRejectionCode.HIGH_SENSITIVITY_PASSWORD
            Regex("(?i)\\bapi[ _-]?key\\s*[:=]").containsMatchIn(value) -> MemoryRejectionCode.HIGH_SENSITIVITY_API_KEY
            Regex("(?i)\\bauthorization\\s*[:=]").containsMatchIn(value) || Regex("(?i)\\bbearer\\s+[A-Za-z0-9._~+/-]{12,}").containsMatchIn(value) -> MemoryRejectionCode.HIGH_SENSITIVITY_AUTHORIZATION
            Regex("恢复码\\s*[:：=]").containsMatchIn(value) || Regex("(?i)\\brecovery[ _-]?code\\s*[:=]").containsMatchIn(value) -> MemoryRejectionCode.HIGH_SENSITIVITY_RECOVERY_CODE
            Regex("(?<!\\d)(?:\\d[ -]?){13,19}(?!\\d)").containsMatchIn(value) -> MemoryRejectionCode.HIGH_SENSITIVITY_PAYMENT_CARD
            else -> null
        }
    }
}

class ManageMemoryUseCase(private val domain: MemoryDomain, private val repository: MemoryRepository) {
    fun execute(intent: MemoryIntent): MemoryMutationResult {
        domain.validate(intent)?.let { return MemoryMutationResult.Rejected(it) }
        return repository.mutate(intent, fingerprint(intent))
    }
    fun list(scope: MemoryScopeKind?, status: MemoryStatus?, search: String) = repository.list(scope, status, search)
    private fun fingerprint(intent: MemoryIntent): String = MemoryDomain.sha256(listOf(
        intent.id.value, intent.action.name, intent.memoryId?.value.orEmpty(), intent.title.orEmpty(), intent.body.orEmpty(), intent.scope?.stableKey().orEmpty(),
        intent.source.name, intent.sourceStableId, intent.sourceSummary, intent.memoryIds.map { it.value }.sorted().joinToString(","), intent.conflictId?.value.orEmpty(), intent.conflictResolution?.name.orEmpty(),
    ).joinToString("|"))
}

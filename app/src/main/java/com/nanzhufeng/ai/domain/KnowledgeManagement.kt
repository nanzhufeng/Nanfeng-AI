package com.nanzhufeng.ai.domain

import java.time.Clock
import java.time.Instant
import java.util.UUID

@JvmInline value class KnowledgeRevisionId(val value: String) { companion object { fun new() = KnowledgeRevisionId(UUID.randomUUID().toString()) } }
@JvmInline value class KnowledgeIntentId(val value: String) { companion object { fun new() = KnowledgeIntentId(UUID.randomUUID().toString()) } }

enum class KnowledgeStatus { ACTIVE, ARCHIVED, DELETED }
enum class KnowledgeScope { GLOBAL, PROJECT }
enum class KnowledgeIntentAction { CREATE_MANUAL, CREATE_HISTORY_CONVERSATION, CREATE_MARKDOWN_IMPORT, CREATE_JSON_IMPORT, CREATE_PDF_TEXT_IMPORT, CREATE_WEB_TEXT_SNAPSHOT, UPDATE, ARCHIVE, RESTORE, DELETE, RESTORE_FROM_TRASH }

data class KnowledgeLifecycle(
    val status: KnowledgeStatus = KnowledgeStatus.ACTIVE,
    val scope: KnowledgeScope = KnowledgeScope.GLOBAL,
    val projectId: ProjectId? = null,
    val tags: Set<String> = emptySet(),
    val contentHash: String,
    val updatedAt: Instant,
    val archivedAt: Instant? = null,
    val deletedAt: Instant? = null,
) {
    init { require((scope == KnowledgeScope.PROJECT) == (projectId != null)) }
}

data class KnowledgeRevision(
    val id: KnowledgeRevisionId,
    val knowledgeId: KnowledgeItemId,
    val revision: Int,
    val title: String,
    val body: String,
    val status: KnowledgeStatus,
    val scope: KnowledgeScope,
    val projectId: ProjectId?,
    val tags: Set<String>,
    val contentHash: String,
    val createdAt: Instant,
)

data class KnowledgeSnapshot(
    val item: KnowledgeItem,
    val lifecycle: KnowledgeLifecycle,
    val revisions: List<KnowledgeRevision>,
) {
    init { require(revisions.map { it.revision }.distinct().size == revisions.size) }
}

data class KnowledgeIntent(
    val id: KnowledgeIntentId,
    val action: KnowledgeIntentAction,
    val knowledgeId: KnowledgeItemId,
    val title: String? = null,
    val body: String? = null,
    val tags: Set<String> = emptySet(),
    val scope: KnowledgeScope = KnowledgeScope.GLOBAL,
    val projectId: ProjectId? = null,
    /** Safe, stable task/item reference only; never an external URI or a filesystem path. */
    val importReference: String? = null,
    /** Present only for a user-confirmed model curation of one local history conversation. */
    val generatedProviderId: ProviderId? = null,
    val generatedModelId: String? = null,
    /** The user has explicitly enabled background history curation for this generated item. */
    val generatedAutomatically: Boolean = false,
)

data class KnowledgeSearchFilter(
    val query: String = "",
    val scope: KnowledgeScope? = null,
    val projectId: ProjectId? = null,
    val sourceType: CaptureSourceType? = null,
    val status: KnowledgeStatus = KnowledgeStatus.ACTIVE,
    val limit: Int = 50,
)

data class KnowledgeSearchResult(
    val id: KnowledgeItemId,
    val title: String,
    val snippet: String,
    val tags: Set<String>,
    val scope: KnowledgeScope,
    val projectId: ProjectId?,
    val sourceTypes: Set<CaptureSourceType>,
    val contentHash: String,
    val revision: Int,
)

sealed interface KnowledgeMutationResult {
    data class Applied(val snapshot: KnowledgeSnapshot) : KnowledgeMutationResult
    data class Replayed(val snapshot: KnowledgeSnapshot) : KnowledgeMutationResult
    data class Rejected(val code: KnowledgeRejectionCode) : KnowledgeMutationResult
}
enum class KnowledgeRejectionCode { EMPTY_TITLE, EMPTY_BODY, TITLE_TOO_LONG, BODY_TOO_LONG, INVALID_TAG, INVALID_SCOPE, MISSING_KNOWLEDGE, INVALID_ACTION, HIGH_SENSITIVITY }

/** The single owner for P4-E edits, lifecycle, tags, scope and deterministic retrieval. */
class KnowledgeDomain(private val clock: Clock) {
    fun mutate(snapshot: KnowledgeSnapshot?, intent: KnowledgeIntent): KnowledgeMutationResult.Rejected? = runCatching {
        when (intent.action) {
            KnowledgeIntentAction.CREATE_MANUAL, KnowledgeIntentAction.CREATE_HISTORY_CONVERSATION, KnowledgeIntentAction.CREATE_MARKDOWN_IMPORT, KnowledgeIntentAction.CREATE_JSON_IMPORT, KnowledgeIntentAction.CREATE_PDF_TEXT_IMPORT, KnowledgeIntentAction.CREATE_WEB_TEXT_SNAPSHOT, KnowledgeIntentAction.UPDATE -> {
                val title = normalizedTitle(requireNotNull(intent.title)); val body = normalizedBody(requireNotNull(intent.body)); require(MemoryDomain.sensitiveRejection("$title\n$body") == null) { "sensitive" }; normalizedTags(intent.tags); validateScope(intent.scope, intent.projectId)
                if (intent.action == KnowledgeIntentAction.CREATE_HISTORY_CONVERSATION) require(intent.importReference?.startsWith("conversation:") == true && intent.generatedProviderId != null && !intent.generatedModelId.isNullOrBlank()) { "historysource" }
            }
            else -> requireNotNull(snapshot)
        }
    }.exceptionOrNull()?.let { KnowledgeMutationResult.Rejected(map(it)) }
    fun normalizedTitle(value: String): String = value.trim().also { require(it.isNotEmpty()) { "title" }; require(it.codePointCount(0, it.length) <= 120) { "titlelong" } }
    fun normalizedBody(value: String): String = value.trim().also { require(it.isNotEmpty()) { "body" }; require(it.codePointCount(0, it.length) <= 12_000) { "bodylong" }; require(MemoryDomain.sensitiveRejection(it) == null) { "sensitive" } }
    fun normalizedTags(tags: Set<String>): Set<String> = tags.map { it.trim().lowercase() }.onEach { require(it.matches(Regex("[\\p{L}\\p{N}][\\p{L}\\p{N} _.-]{0,31}"))) { "tag" } }.toSortedSet()
    fun contentHash(title: String, body: String): String = MemoryDomain.sha256("${canonical(title)}\n${canonical(body)}")
    fun now(): Instant = clock.instant()
    fun search(snapshots: List<KnowledgeSnapshot>, filter: KnowledgeSearchFilter): List<KnowledgeSearchResult> {
        val needle = filter.query.trim().lowercase(java.util.Locale.ROOT)
        return snapshots.asSequence().filter { it.lifecycle.status == filter.status }
            .filter { filter.scope == null || it.lifecycle.scope == filter.scope }
            .filter { filter.projectId == null || it.lifecycle.projectId == filter.projectId }
            .filter { filter.sourceType == null || filter.sourceType in it.item.sourceEvidence.map(SourceEvidence::sourceType) }
            .filter { snapshot -> needle.isBlank() || searchable(snapshot).contains(needle) }
            .sortedWith(compareByDescending<KnowledgeSnapshot> { it.lifecycle.updatedAt }.thenBy { it.item.id.value })
            .take(filter.limit.coerceIn(1, 50)).map { snapshot ->
                val revision = snapshot.revisions.maxOfOrNull { it.revision } ?: 1
                KnowledgeSearchResult(snapshot.item.id, snapshot.item.title, snippet(snapshot.item.title, snapshot.item.body, snapshot.lifecycle.tags, needle), snapshot.lifecycle.tags, snapshot.lifecycle.scope, snapshot.lifecycle.projectId, snapshot.item.sourceEvidence.map(SourceEvidence::sourceType).toSet(), snapshot.lifecycle.contentHash, revision)
            }.toList()
    }
    private fun searchable(snapshot: KnowledgeSnapshot) = listOf(snapshot.item.title, snapshot.item.body, snapshot.lifecycle.tags.joinToString(" "), snapshot.item.sourceEvidence.joinToString(" ") { it.sourceType.name }).joinToString(" ").lowercase(java.util.Locale.ROOT)
    private fun snippet(title: String, body: String, tags: Set<String>, needle: String): String {
        val source = "$title\n$body\n${tags.joinToString(" ")}".replace(Regex("\\s+"), " ").trim(); if (needle.isBlank()) return source.take(240)
        val at = source.lowercase(java.util.Locale.ROOT).indexOf(needle); return if (at < 0) source.take(240) else source.substring((at - 80).coerceAtLeast(0), (at + needle.length + 160).coerceAtMost(source.length))
    }
    private fun validateScope(scope: KnowledgeScope, projectId: ProjectId?) { require((scope == KnowledgeScope.PROJECT) == (projectId != null)) { "scope" } }
    private fun canonical(value: String) = value.trim().lowercase(java.util.Locale.ROOT).replace(Regex("\\s+"), " ")
    private fun map(error: Throwable) = when (error.message) { "title" -> KnowledgeRejectionCode.EMPTY_TITLE; "body" -> KnowledgeRejectionCode.EMPTY_BODY; "titlelong" -> KnowledgeRejectionCode.TITLE_TOO_LONG; "bodylong" -> KnowledgeRejectionCode.BODY_TOO_LONG; "tag" -> KnowledgeRejectionCode.INVALID_TAG; "scope" -> KnowledgeRejectionCode.INVALID_SCOPE; "sensitive" -> KnowledgeRejectionCode.HIGH_SENSITIVITY; else -> KnowledgeRejectionCode.INVALID_ACTION }
}

class ManageKnowledgeUseCase(private val domain: KnowledgeDomain, private val repository: KnowledgeManagementRepository) {
    fun execute(intent: KnowledgeIntent): KnowledgeMutationResult {
        domain.mutate(repository.findSnapshot(intent.knowledgeId), intent)?.let { return it }
        return repository.mutate(intent, MemoryDomain.sha256(listOf(intent.id.value, intent.action.name, intent.knowledgeId.value, intent.title.orEmpty(), intent.body.orEmpty(), intent.tags.sorted().joinToString(","), intent.scope.name, intent.projectId?.value.orEmpty(), intent.importReference.orEmpty(), intent.generatedProviderId?.name.orEmpty(), intent.generatedModelId.orEmpty(), intent.generatedAutomatically).joinToString("|")))
    }
    fun search(filter: KnowledgeSearchFilter): List<KnowledgeSearchResult> = domain.search(repository.listSnapshots(filter), filter)
    fun detail(id: KnowledgeItemId): KnowledgeSnapshot? = repository.findSnapshot(id)
    fun duplicateCandidates(id: KnowledgeItemId): KnowledgeDuplicateCandidatesResult =
        KnowledgeDeduplicationDomain().candidates(id, repository.listSnapshots())
}

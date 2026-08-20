package com.nanzhufeng.ai.domain

import java.time.Clock
import java.time.Instant
import java.util.UUID

@JvmInline value class KnowledgeRelationshipId(val value: String) { companion object { fun new() = KnowledgeRelationshipId(UUID.randomUUID().toString()) } }
@JvmInline value class KnowledgeRelationshipRevisionId(val value: String) { companion object { fun new() = KnowledgeRelationshipRevisionId(UUID.randomUUID().toString()) } }
@JvmInline value class KnowledgeRelationshipIntentId(val value: String) { companion object { fun new() = KnowledgeRelationshipIntentId(UUID.randomUUID().toString()) } }

/** RELATED, duplicate suggestions and contradictions are unordered facts; SUPPORTS is directional. */
enum class KnowledgeRelationshipType(val isSymmetric: Boolean) {
    RELATED(true), DUPLICATE_CANDIDATE(true), SUPPORTS(false), CONTRADICTS(true),
}
enum class KnowledgeRelationshipStatus { ACTIVE, REVOKED }
enum class KnowledgeRelationshipAction { CONFIRM, REVOKE }
enum class KnowledgeRelationshipSuggestionSource { MANUAL, P4F_DEDUPLICATION_CANDIDATE }

data class KnowledgeRelationshipEndpointExpectation(
    val knowledgeId: KnowledgeItemId,
    val revision: Int,
    val contentHash: String,
)

data class KnowledgeRelationshipIntent(
    val id: KnowledgeRelationshipIntentId,
    val action: KnowledgeRelationshipAction,
    val relationshipId: KnowledgeRelationshipId? = null,
    val type: KnowledgeRelationshipType? = null,
    val first: KnowledgeRelationshipEndpointExpectation? = null,
    val second: KnowledgeRelationshipEndpointExpectation? = null,
    val suggestionSource: KnowledgeRelationshipSuggestionSource = KnowledgeRelationshipSuggestionSource.MANUAL,
)

data class KnowledgeRelationship(
    val id: KnowledgeRelationshipId,
    val type: KnowledgeRelationshipType,
    val fromKnowledgeId: KnowledgeItemId,
    val toKnowledgeId: KnowledgeItemId,
    val scope: KnowledgeScope,
    val projectId: ProjectId?,
    val status: KnowledgeRelationshipStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
    val createdByIntentId: KnowledgeRelationshipIntentId,
    val latestIntentId: KnowledgeRelationshipIntentId,
    val suggestionSource: KnowledgeRelationshipSuggestionSource,
)

data class KnowledgeRelationshipRevision(
    val id: KnowledgeRelationshipRevisionId,
    val relationshipId: KnowledgeRelationshipId,
    val revision: Int,
    val action: KnowledgeRelationshipAction,
    val status: KnowledgeRelationshipStatus,
    val intentId: KnowledgeRelationshipIntentId,
    val createdAt: Instant,
)

data class KnowledgeRelationshipSnapshot(
    val relationship: KnowledgeRelationship,
    val revisions: List<KnowledgeRelationshipRevision>,
) {
    init { require(revisions.map { it.revision }.distinct().size == revisions.size) }
}

sealed interface KnowledgeRelationshipMutationResult {
    data class Applied(val snapshot: KnowledgeRelationshipSnapshot) : KnowledgeRelationshipMutationResult
    data class Replayed(val snapshot: KnowledgeRelationshipSnapshot) : KnowledgeRelationshipMutationResult
    data class Rejected(val code: KnowledgeRelationshipRejection) : KnowledgeRelationshipMutationResult
}

enum class KnowledgeRelationshipRejection {
    MISSING_RELATIONSHIP, MISSING_KNOWLEDGE, SELF_REFERENCE, INELIGIBLE_KNOWLEDGE,
    CROSS_SCOPE, STALE_ENDPOINT, HIGH_SENSITIVITY, DUPLICATE_ACTIVE_EDGE, INVALID_INTENT,
}

data class KnowledgeRelationshipListFilter(
    val status: KnowledgeRelationshipStatus? = KnowledgeRelationshipStatus.ACTIVE,
    val endpointId: KnowledgeItemId? = null,
    val type: KnowledgeRelationshipType? = null,
)

/**
 * P4-G's single semantic owner. The UI and DAO receive only the canonical relationship returned
 * here; neither may independently sort symmetric endpoints or reinterpret direction.
 */
class KnowledgeRelationshipDomain(private val clock: Clock) {
    fun confirm(
        intent: KnowledgeRelationshipIntent,
        snapshots: List<KnowledgeSnapshot>,
        existing: List<KnowledgeRelationshipSnapshot>,
    ): RelationshipDecision {
        val type = intent.type ?: return RelationshipDecision.Rejected(KnowledgeRelationshipRejection.INVALID_INTENT)
        val firstExpected = intent.first ?: return RelationshipDecision.Rejected(KnowledgeRelationshipRejection.INVALID_INTENT)
        val secondExpected = intent.second ?: return RelationshipDecision.Rejected(KnowledgeRelationshipRejection.INVALID_INTENT)
        if (intent.action != KnowledgeRelationshipAction.CONFIRM || intent.relationshipId != null || firstExpected.knowledgeId == secondExpected.knowledgeId) {
            return RelationshipDecision.Rejected(if (firstExpected.knowledgeId == secondExpected.knowledgeId) KnowledgeRelationshipRejection.SELF_REFERENCE else KnowledgeRelationshipRejection.INVALID_INTENT)
        }
        val first = snapshots.firstOrNull { it.item.id == firstExpected.knowledgeId }
            ?: return RelationshipDecision.Rejected(KnowledgeRelationshipRejection.MISSING_KNOWLEDGE)
        val second = snapshots.firstOrNull { it.item.id == secondExpected.knowledgeId }
            ?: return RelationshipDecision.Rejected(KnowledgeRelationshipRejection.MISSING_KNOWLEDGE)
        if (!first.isActive() || !second.isActive()) return RelationshipDecision.Rejected(KnowledgeRelationshipRejection.INELIGIBLE_KNOWLEDGE)
        if (!sameScope(first, second)) return RelationshipDecision.Rejected(KnowledgeRelationshipRejection.CROSS_SCOPE)
        if (!matches(first, firstExpected) || !matches(second, secondExpected)) return RelationshipDecision.Rejected(KnowledgeRelationshipRejection.STALE_ENDPOINT)
        if (MemoryDomain.sensitiveRejection("${first.item.title}\n${first.item.body}") != null || MemoryDomain.sensitiveRejection("${second.item.title}\n${second.item.body}") != null) {
            return RelationshipDecision.Rejected(KnowledgeRelationshipRejection.HIGH_SENSITIVITY)
        }
        val (from, to) = canonical(type, first.item.id, second.item.id)
        val activeDuplicate = existing.any { snapshot ->
            snapshot.relationship.status == KnowledgeRelationshipStatus.ACTIVE && snapshot.relationship.type == type &&
                snapshot.relationship.fromKnowledgeId == from && snapshot.relationship.toKnowledgeId == to
        }
        if (activeDuplicate) return RelationshipDecision.Rejected(KnowledgeRelationshipRejection.DUPLICATE_ACTIVE_EDGE)
        return RelationshipDecision.Confirm(type, from, to, first.lifecycle.scope, first.lifecycle.projectId, now())
    }

    fun revoke(intent: KnowledgeRelationshipIntent, existing: KnowledgeRelationshipSnapshot?): RelationshipDecision {
        if (intent.action != KnowledgeRelationshipAction.REVOKE || intent.relationshipId == null || intent.type != null || intent.first != null || intent.second != null) {
            return RelationshipDecision.Rejected(KnowledgeRelationshipRejection.INVALID_INTENT)
        }
        val snapshot = existing ?: return RelationshipDecision.Rejected(KnowledgeRelationshipRejection.MISSING_RELATIONSHIP)
        return RelationshipDecision.Revoke(snapshot, now())
    }

    fun canonical(type: KnowledgeRelationshipType, first: KnowledgeItemId, second: KnowledgeItemId): Pair<KnowledgeItemId, KnowledgeItemId> =
        if (type.isSymmetric && first.value > second.value) second to first else first to second

    private fun KnowledgeSnapshot.isActive() = lifecycle.status == KnowledgeStatus.ACTIVE
    private fun sameScope(first: KnowledgeSnapshot, second: KnowledgeSnapshot) = first.lifecycle.scope == second.lifecycle.scope && first.lifecycle.projectId == second.lifecycle.projectId
    private fun matches(snapshot: KnowledgeSnapshot, expected: KnowledgeRelationshipEndpointExpectation) =
        snapshot.revisions.maxOfOrNull { it.revision } == expected.revision && snapshot.lifecycle.contentHash == expected.contentHash
    private fun now(): Instant = clock.instant()
}

sealed interface RelationshipDecision {
    data class Confirm(val type: KnowledgeRelationshipType, val from: KnowledgeItemId, val to: KnowledgeItemId, val scope: KnowledgeScope, val projectId: ProjectId?, val at: Instant) : RelationshipDecision
    data class Revoke(val existing: KnowledgeRelationshipSnapshot, val at: Instant) : RelationshipDecision
    data class Rejected(val code: KnowledgeRelationshipRejection) : RelationshipDecision
}

class ManageKnowledgeRelationshipsUseCase(private val domain: KnowledgeRelationshipDomain, private val repository: KnowledgeRelationshipRepository) {
    fun confirm(intent: KnowledgeRelationshipIntent): KnowledgeRelationshipMutationResult = repository.apply(
        intent = intent,
        fingerprint = fingerprint(intent),
        decide = { snapshots, existing -> domain.confirm(intent, snapshots, existing) },
    )
    fun revoke(intent: KnowledgeRelationshipIntent): KnowledgeRelationshipMutationResult = repository.apply(
        intent = intent,
        fingerprint = fingerprint(intent),
        decide = { _, existing -> domain.revoke(intent, existing.firstOrNull { it.relationship.id == intent.relationshipId }) },
    )
    fun list(filter: KnowledgeRelationshipListFilter = KnowledgeRelationshipListFilter()): List<KnowledgeRelationshipSnapshot> = repository.list(filter)
    private fun fingerprint(intent: KnowledgeRelationshipIntent) = MemoryDomain.sha256(listOf(
        intent.id.value, intent.action.name, intent.relationshipId?.value.orEmpty(), intent.type?.name.orEmpty(),
        intent.first?.let { "${it.knowledgeId.value}:${it.revision}:${it.contentHash}" }.orEmpty(),
        intent.second?.let { "${it.knowledgeId.value}:${it.revision}:${it.contentHash}" }.orEmpty(), intent.suggestionSource.name,
    ).joinToString("|"))
}

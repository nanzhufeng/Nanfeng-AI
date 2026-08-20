package com.nanzhufeng.ai.domain

import java.util.Locale

/**
 * P4-F is a read-only local candidate projection. It never creates a relationship, merge,
 * revision, import record, Context selection, Prompt, or Provider request.
 */
enum class KnowledgeDuplicateReason { EXACT_CONTENT, SAME_NORMALIZED_TITLE }

data class KnowledgeDuplicateCandidate(
    val knowledgeId: KnowledgeItemId,
    val title: String,
    val revision: Int,
    val reasons: Set<KnowledgeDuplicateReason>,
)

sealed interface KnowledgeDuplicateCandidatesResult {
    data class Available(
        val anchorId: KnowledgeItemId,
        val candidates: List<KnowledgeDuplicateCandidate>,
    ) : KnowledgeDuplicateCandidatesResult

    data class Rejected(val code: KnowledgeDuplicateCandidatesRejection) : KnowledgeDuplicateCandidatesResult
}

enum class KnowledgeDuplicateCandidatesRejection { MISSING_KNOWLEDGE, INELIGIBLE_KNOWLEDGE, HIGH_SENSITIVITY }

class KnowledgeDeduplicationDomain {
    fun candidates(
        anchorId: KnowledgeItemId,
        snapshots: List<KnowledgeSnapshot>,
    ): KnowledgeDuplicateCandidatesResult {
        val anchor = snapshots.firstOrNull { it.item.id == anchorId }
            ?: return KnowledgeDuplicateCandidatesResult.Rejected(KnowledgeDuplicateCandidatesRejection.MISSING_KNOWLEDGE)
        if (!anchor.isEligible()) return KnowledgeDuplicateCandidatesResult.Rejected(KnowledgeDuplicateCandidatesRejection.INELIGIBLE_KNOWLEDGE)
        if (anchor.isSensitive()) return KnowledgeDuplicateCandidatesResult.Rejected(KnowledgeDuplicateCandidatesRejection.HIGH_SENSITIVITY)

        val candidates = snapshots.asSequence()
            .filter { it.item.id != anchorId && it.isEligible() && sameScope(anchor, it) }
            .mapNotNull { candidate ->
                val reasons = buildSet {
                    if (candidate.contentHash() == anchor.contentHash()) add(KnowledgeDuplicateReason.EXACT_CONTENT)
                    if (canonicalTitle(candidate.item.title) == canonicalTitle(anchor.item.title)) add(KnowledgeDuplicateReason.SAME_NORMALIZED_TITLE)
                }
                candidate.takeIf { reasons.isNotEmpty() }?.let { it to reasons }
            }
            .toList()
        if (candidates.any { it.first.isSensitive() }) {
            return KnowledgeDuplicateCandidatesResult.Rejected(KnowledgeDuplicateCandidatesRejection.HIGH_SENSITIVITY)
        }
        return KnowledgeDuplicateCandidatesResult.Available(
            anchorId,
            candidates.sortedWith(
                compareByDescending<Pair<KnowledgeSnapshot, Set<KnowledgeDuplicateReason>>> { KnowledgeDuplicateReason.EXACT_CONTENT in it.second }
                    .thenByDescending { KnowledgeDuplicateReason.SAME_NORMALIZED_TITLE in it.second }
                    .thenByDescending { it.first.lifecycle.updatedAt }
                    .thenBy { it.first.item.id.value },
            ).take(MAX_CANDIDATES).map { (candidate, reasons) ->
                KnowledgeDuplicateCandidate(
                    knowledgeId = candidate.item.id,
                    title = candidate.item.title,
                    revision = candidate.revisions.maxOfOrNull { it.revision } ?: 1,
                    reasons = reasons,
                )
            },
        )
    }

    private fun KnowledgeSnapshot.isEligible() = lifecycle.status == KnowledgeStatus.ACTIVE
    private fun KnowledgeSnapshot.isSensitive() = MemoryDomain.sensitiveRejection("${item.title}\n${item.body}") != null
    private fun KnowledgeSnapshot.contentHash() = MemoryDomain.sha256("${canonicalTitle(item.title)}\n${canonicalBody(item.body)}")
    private fun sameScope(anchor: KnowledgeSnapshot, candidate: KnowledgeSnapshot) =
        anchor.lifecycle.scope == candidate.lifecycle.scope && anchor.lifecycle.projectId == candidate.lifecycle.projectId

    private fun canonicalTitle(value: String) = canonical(value)
    private fun canonicalBody(value: String) = canonical(value)
    private fun canonical(value: String) = value.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")

    private companion object { const val MAX_CANDIDATES = 20 }
}

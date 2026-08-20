package com.nanzhufeng.ai.domain

/**
 * P4-J local-only extractive compression. This is deliberately not a semantic/model summary,
 * a prompt builder, a cache, or a persistence format. It consumes only P4-D's explicit body IR.
 */
const val LOCAL_CONTEXT_COMPRESSION_POLICY_ID = "p4j-extractive-v1"
const val LOCAL_CONTEXT_COMPRESSION_POLICY_VERSION = 1

data class LocalContextCompressionPolicy(
    val id: String = LOCAL_CONTEXT_COMPRESSION_POLICY_ID,
    val version: Int = LOCAL_CONTEXT_COMPRESSION_POLICY_VERSION,
    val maxEntryCodePoints: Int = 480,
    val maxTotalCodePoints: Int = 1_800,
) {
    init {
        require(id == LOCAL_CONTEXT_COMPRESSION_POLICY_ID && version == LOCAL_CONTEXT_COMPRESSION_POLICY_VERSION) { "不支持的本地压缩策略。" }
        require(maxEntryCodePoints in 1..12_000) { "单条压缩预算无效。" }
        require(maxTotalCodePoints in 1..24_000) { "总压缩预算无效。" }
    }
}

enum class LocalContextCompressionBudgetPreset { COMPACT, BALANCED, EXPANDED }

/** UI may choose only this bounded preset; all numeric policy interpretation stays in P4-J Domain. */
fun LocalContextCompressionBudgetPreset.toPolicy(): LocalContextCompressionPolicy = when (this) {
    LocalContextCompressionBudgetPreset.COMPACT -> LocalContextCompressionPolicy(maxEntryCodePoints = 160, maxTotalCodePoints = 480)
    LocalContextCompressionBudgetPreset.BALANCED -> LocalContextCompressionPolicy(maxEntryCodePoints = 480, maxTotalCodePoints = 1_800)
    LocalContextCompressionBudgetPreset.EXPANDED -> LocalContextCompressionPolicy(maxEntryCodePoints = 960, maxTotalCodePoints = 3_600)
}

data class LocalContextCompressionRequest(
    val bodySelection: ExplicitContextBodyRequest,
    val policy: LocalContextCompressionPolicy = LocalContextCompressionPolicy(),
)

data class LocalContextCompressionEntry(
    val layer: ContextBodyLayer,
    val kind: ContextBodySourceKind,
    val sourceId: String,
    val role: MessageRole?,
    val revision: Int?,
    val sourceContentHash: String,
    val compressedBody: String,
    val compressedContentHash: String,
    val originalCodePointCount: Int,
    val retainedCodePointCount: Int,
    val omittedCodePointCount: Int,
    val policyVersion: Int,
) {
    init {
        require(sourceId.isNotBlank()) { "压缩来源必须有稳定 ID。" }
        require(originalCodePointCount >= retainedCodePointCount && retainedCodePointCount >= 0) { "压缩长度证据无效。" }
        require(omittedCodePointCount == originalCodePointCount - retainedCodePointCount) { "压缩省略计数不一致。" }
        require(compressedBody.codePointCount() == retainedCodePointCount) { "压缩正文与 code point 计数不一致。" }
    }
}

/** A transient, traceable local IR. It is never a Prompt, provider payload, RunSpec, or cache. */
data class LocalContextCompressionSnapshot(
    val conversationId: ConversationId,
    val sourceMetadata: ContextSelectionSnapshot,
    val entries: List<LocalContextCompressionEntry>,
    val policy: LocalContextCompressionPolicy,
) {
    init {
        require(entries.map { it.kind to it.sourceId }.distinct().size == entries.size) { "压缩结果不能重复来源。" }
        require(entries.sumOf(LocalContextCompressionEntry::retainedCodePointCount) <= policy.maxTotalCodePoints) { "压缩结果超过总预算。" }
    }
}

enum class LocalContextCompressionRejection {
    MISSING_CONVERSATION,
    INCONSISTENT_PROJECT_REFERENCE,
    STALE_CONTEXT,
    INELIGIBLE_MEMORY,
    INELIGIBLE_KNOWLEDGE,
    SENSITIVE_CONTENT,
    INVALID_COMPRESSION_REQUEST,
}

sealed interface LocalContextCompressionResult {
    data class Compressed(val snapshot: LocalContextCompressionSnapshot) : LocalContextCompressionResult
    data class Rejected(val reason: LocalContextCompressionRejection) : LocalContextCompressionResult
}

/** The only owner of P4-J policy, deterministic ordering, extraction and length evidence. */
class LocalContextCompressionDomain {
    fun compress(
        conversationId: ConversationId,
        metadata: ContextSelectionSnapshot,
        entries: List<ExplicitContextBodyEntry>,
        policy: LocalContextCompressionPolicy,
    ): LocalContextCompressionResult {
        if (entries.map { it.kind to it.sourceId }.distinct().size != entries.size) {
            return LocalContextCompressionResult.Rejected(LocalContextCompressionRejection.INVALID_COMPRESSION_REQUEST)
        }
        if (entries.any { MemoryDomain.sensitiveRejection(it.body) != null }) {
            return LocalContextCompressionResult.Rejected(LocalContextCompressionRejection.SENSITIVE_CONTENT)
        }
        var remaining = policy.maxTotalCodePoints
        val compressed = entries.sortedWith(compareBy<ExplicitContextBodyEntry>({ it.layer.ordinal }, { it.kind.ordinal }, { it.sourceId })).map { entry ->
            val originalCount = entry.body.codePointCount()
            val retainedCount = minOf(originalCount, policy.maxEntryCodePoints, remaining)
            val body = entry.body.extractCodePoints(retainedCount)
            remaining -= retainedCount
            LocalContextCompressionEntry(
                layer = entry.layer,
                kind = entry.kind,
                sourceId = entry.sourceId,
                role = entry.role,
                revision = entry.revision,
                sourceContentHash = entry.contentHash,
                compressedBody = body,
                compressedContentHash = MemoryDomain.sha256(body),
                originalCodePointCount = originalCount,
                retainedCodePointCount = retainedCount,
                omittedCodePointCount = originalCount - retainedCount,
                policyVersion = policy.version,
            )
        }
        return LocalContextCompressionResult.Compressed(LocalContextCompressionSnapshot(conversationId, metadata, compressed, policy))
    }
}

/** P4-J may only delegate to P4-D; it never reads repositories or legacy memorySources directly. */
class ReadLocalContextCompressionUseCase(
    private val explicitBodies: ReadExplicitContextBodyUseCase,
    private val domain: LocalContextCompressionDomain = LocalContextCompressionDomain(),
) {
    fun execute(request: LocalContextCompressionRequest): LocalContextCompressionResult = when (val body = explicitBodies.execute(request.bodySelection)) {
        is ExplicitContextBodyResult.Selected -> domain.compress(
            conversationId = body.snapshot.conversationId,
            metadata = body.snapshot.metadata,
            entries = body.snapshot.entries,
            policy = request.policy,
        )
        is ExplicitContextBodyResult.Rejected -> LocalContextCompressionResult.Rejected(body.reason.toCompressionRejection())
    }
}

private fun ExplicitContextBodyRejection.toCompressionRejection(): LocalContextCompressionRejection = when (this) {
    ExplicitContextBodyRejection.MISSING_CONVERSATION -> LocalContextCompressionRejection.MISSING_CONVERSATION
    ExplicitContextBodyRejection.INCONSISTENT_PROJECT_REFERENCE -> LocalContextCompressionRejection.INCONSISTENT_PROJECT_REFERENCE
    ExplicitContextBodyRejection.STALE_CONTEXT -> LocalContextCompressionRejection.STALE_CONTEXT
    ExplicitContextBodyRejection.INELIGIBLE_MEMORY -> LocalContextCompressionRejection.INELIGIBLE_MEMORY
    ExplicitContextBodyRejection.INELIGIBLE_KNOWLEDGE -> LocalContextCompressionRejection.INELIGIBLE_KNOWLEDGE
    ExplicitContextBodyRejection.SENSITIVE_CONTENT -> LocalContextCompressionRejection.SENSITIVE_CONTENT
}

private fun String.codePointCount(): Int = codePointCount(0, length)

/** Keeps exact head/tail source fragments; omitted text is represented only by the evidence fields. */
private fun String.extractCodePoints(limit: Int): String {
    if (limit <= 0) return ""
    val count = codePointCount()
    if (count <= limit) return this
    val headCount = (limit + 1) / 2
    val tailCount = limit - headCount
    val headEnd = offsetByCodePoints(0, headCount)
    val tailStart = offsetByCodePoints(0, count - tailCount)
    return substring(0, headEnd) + substring(tailStart)
}

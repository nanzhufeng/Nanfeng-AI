package com.nanzhufeng.ai.domain

/**
 * P4-K is metadata for a possible future stable prefix only. It is not a cache key for a
 * provider request, never contains text, and is intentionally retained only by its caller.
 */
const val STABLE_CONTEXT_PREFIX_METADATA_FORMAT = "p4k-stable-prefix-metadata-v1"

data class StableContextPrefixSourceMetadata(
    val layer: ContextBodyLayer,
    val kind: ContextBodySourceKind,
    val sourceId: String,
    val revision: Int?,
    val sourceContentHash: String,
)

data class StableContextPrefixMetadata(
    val format: String = STABLE_CONTEXT_PREFIX_METADATA_FORMAT,
    val fingerprint: String,
    val sources: List<StableContextPrefixSourceMetadata>,
) {
    init {
        require(sources == sources.sortedWith(prefixSourceOrder)) { "稳定前缀元数据顺序不稳定。" }
        require(sources.all { it.layer in setOf(ContextBodyLayer.L0_STABLE, ContextBodyLayer.L1_PROJECT) }) { "稳定前缀不能包含会话正文。" }
    }
}

enum class StablePrefixInvalidationReason {
    SOURCE_SET_CHANGED,
    SOURCE_REVISION_OR_HASH_CHANGED,
    FORMAT_CHANGED,
}

/** The sole P4-K interpretation of which explicit P4-D sources can describe a stable prefix. */
class StableContextPrefixMetadataDomain {
    fun create(snapshot: LocalContextCompressionSnapshot): StableContextPrefixMetadata {
        val sources = snapshot.entries
            .asSequence()
            .filter { it.layer in setOf(ContextBodyLayer.L0_STABLE, ContextBodyLayer.L1_PROJECT) }
            .filter { it.kind in stableKinds }
            .map { StableContextPrefixSourceMetadata(it.layer, it.kind, it.sourceId, it.revision, it.sourceContentHash) }
            .sortedWith(prefixSourceOrder)
            .toList()
        val canonical = sources.joinToString("|") { "${it.layer.name}:${it.kind.name}:${it.sourceId}:${it.revision ?: "-"}:${it.sourceContentHash}" }
        return StableContextPrefixMetadata(fingerprint = MemoryDomain.sha256("$STABLE_CONTEXT_PREFIX_METADATA_FORMAT|$canonical"), sources = sources)
    }

    fun invalidationReasons(previous: StableContextPrefixMetadata, current: StableContextPrefixMetadata): Set<StablePrefixInvalidationReason> = buildSet {
        if (previous.format != current.format) add(StablePrefixInvalidationReason.FORMAT_CHANGED)
        val before = previous.sources.associateBy { Triple(it.layer, it.kind, it.sourceId) }
        val after = current.sources.associateBy { Triple(it.layer, it.kind, it.sourceId) }
        if (before.keys != after.keys) add(StablePrefixInvalidationReason.SOURCE_SET_CHANGED)
        if (before.keys.intersect(after.keys).any { key ->
                val left = requireNotNull(before[key]); val right = requireNotNull(after[key])
                left.revision != right.revision || left.sourceContentHash != right.sourceContentHash
            }
        ) add(StablePrefixInvalidationReason.SOURCE_REVISION_OR_HASH_CHANGED)
    }

    private companion object {
        val stableKinds = setOf(
            ContextBodySourceKind.GLOBAL_MEMORY,
            ContextBodySourceKind.GLOBAL_KNOWLEDGE,
            ContextBodySourceKind.PROJECT_INSTRUCTION,
            ContextBodySourceKind.PROJECT_MEMORY,
            ContextBodySourceKind.PROJECT_KNOWLEDGE,
        )
    }
}

private val prefixSourceOrder = compareBy<StableContextPrefixSourceMetadata>({ it.layer.ordinal }, { it.kind.ordinal }, { it.sourceId })

/** P4-K composition boundary: P4-J remains the only body compression owner. */
class ReadLocalContextPreviewUseCase(
    private val compression: ReadLocalContextCompressionUseCase,
    private val stablePrefix: StableContextPrefixMetadataDomain = StableContextPrefixMetadataDomain(),
) {
    fun execute(request: LocalContextCompressionRequest): LocalContextPreviewResult = when (val result = compression.execute(request)) {
        is LocalContextCompressionResult.Compressed -> LocalContextPreviewResult.Available(
            compression = result.snapshot,
            stablePrefix = stablePrefix.create(result.snapshot),
        )
        is LocalContextCompressionResult.Rejected -> LocalContextPreviewResult.Rejected(result.reason)
    }

    fun invalidationReasons(previous: StableContextPrefixMetadata, current: StableContextPrefixMetadata): Set<StablePrefixInvalidationReason> =
        stablePrefix.invalidationReasons(previous, current)
}

sealed interface LocalContextPreviewResult {
    data class Available(
        val compression: LocalContextCompressionSnapshot,
        val stablePrefix: StableContextPrefixMetadata,
    ) : LocalContextPreviewResult
    data class Rejected(val reason: LocalContextCompressionRejection) : LocalContextPreviewResult
}

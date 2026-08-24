package com.nanzhufeng.ai.domain

/** Stable local state only. Concrete provider IDs remain catalog data, never app configuration. */
data class P6GLocalCatalogSnapshot(
    val catalogVersion: String,
    val policyVersion: Int,
    val candidates: List<P6GCatalogCandidate>,
) { init { require(catalogVersion.isNotBlank() && policyVersion > 0) } }

data class P6GGlobalDefault(
    val revision: Long,
    val tier: P6GModelTier?,
    /** The last explicit Composer selection; null means the user explicitly chose Auto. */
    val modelId: String? = null,
) { init { require(revision >= 0) } }
data class P6GConversationOverride(val conversationId: ConversationId, val revision: Long, val modelId: String?) { init { require(revision >= 0) } }

data class P6GRouteMetadata(
    val conversationId: ConversationId,
    val policyVersion: Int,
    val catalogVersion: String,
    val tier: P6GModelTier,
    val source: P6GRouteSource,
    val reason: P6GRouteReason,
    val modelId: String?,
    val displayName: String?,
    val rejectedCandidates: List<P6GCandidateRejection>,
)

/**
 * The conversation picker and settings use this same reviewed, local-only display catalog.
 * These IDs are selection identities, not Provider request IDs; invocation remains disabled
 * until a separately verified registry is explicitly authorized.
 */
object P6GCuratedModelCatalog {
    const val VERSION = "official-model-picker-v1"

    fun snapshot(): P6GLocalCatalogSnapshot = P6GLocalCatalogSnapshot(
        catalogVersion = VERSION,
        policyVersion = 1,
        candidates = ComposerModelRoutingCatalog.choices.map { choice ->
            val preset = NanfengModelServiceCatalog.preset(choice.routes.first())
            val family = if (preset.modelFamilyHint.startsWith("Anthropic")) P6GProviderFamily.ANTHROPIC else P6GProviderFamily.OPENAI
            P6GCatalogCandidate(
                providerFamily = family,
                providerId = NanfengModelServiceCatalog.providerFor(preset.id).name.lowercase(),
                modelId = choice.id,
                displayName = choice.label,
                tiers = setOf(P6GModelTier.BALANCED),
                capabilities = setOf(P6GCapability.TEXT, P6GCapability.VISION, P6GCapability.CODE, P6GCapability.TOOL, P6GCapability.STRUCTURED_OUTPUT, P6GCapability.LONG_CONTEXT),
                available = true,
                knownCostMicros = null,
                latencyRank = 2,
                contextWindowTokens = null,
            )
        },
    )

    fun merge(saved: P6GLocalCatalogSnapshot?): P6GLocalCatalogSnapshot {
        val curated = snapshot()
        val extras = saved?.candidates.orEmpty().filterNot { it.modelId.startsWith("logical:") || it.modelId.startsWith("curated:") }
        return curated.copy(
            policyVersion = maxOf(curated.policyVersion, saved?.policyVersion ?: curated.policyVersion),
            candidates = curated.candidates + extras,
        )
    }
}

interface P6GModelSelectionStore {
    fun readCatalog(): P6GLocalCatalogSnapshot
    fun saveCatalog(value: P6GLocalCatalogSnapshot): Boolean
    fun readGlobalDefault(): P6GGlobalDefault
    fun readConversationOverride(conversationId: ConversationId): P6GConversationOverride
    fun saveGlobalDefault(value: P6GGlobalDefault): Boolean
    fun saveConversationOverride(value: P6GConversationOverride): Boolean
    fun saveComposerModelSelection(conversation: P6GConversationOverride, global: P6GGlobalDefault): Boolean
    fun appendRouteMetadata(value: P6GRouteMetadata): Boolean
}

sealed interface P6GSelectionMutationResult {
    data class Applied(val revision: Long) : P6GSelectionMutationResult
    data object Conflict : P6GSelectionMutationResult
    data object InvalidModel : P6GSelectionMutationResult
    data object PersistenceFailed : P6GSelectionMutationResult
}

class P6GModelSelectionOwner(private val store: P6GModelSelectionStore, private val router: P6GModelRouter) {
    /** Read-only UI projections stay behind the same local owner as mutations. */
    fun readCatalog(): P6GLocalCatalogSnapshot = store.readCatalog()
    fun readGlobalDefault(): P6GGlobalDefault = store.readGlobalDefault()
    fun readConversationOverride(conversationId: ConversationId): P6GConversationOverride = store.readConversationOverride(conversationId)

    fun replaceCatalog(value: P6GLocalCatalogSnapshot): P6GSelectionMutationResult =
        if (store.saveCatalog(value)) P6GSelectionMutationResult.Applied(0) else P6GSelectionMutationResult.PersistenceFailed

    fun setGlobalDefault(tier: P6GModelTier?, expectedRevision: Long): P6GSelectionMutationResult {
        val current = store.readGlobalDefault()
        if (current.revision != expectedRevision) return P6GSelectionMutationResult.Conflict
        val next = current.copy(revision = current.revision + 1, tier = tier)
        return if (store.saveGlobalDefault(next)) P6GSelectionMutationResult.Applied(next.revision) else P6GSelectionMutationResult.PersistenceFailed
    }

    fun setConversationManualOverride(conversationId: ConversationId, modelId: String?, expectedRevision: Long): P6GSelectionMutationResult {
        val current = store.readConversationOverride(conversationId)
        if (current.revision != expectedRevision) return P6GSelectionMutationResult.Conflict
        if (modelId != null && store.readCatalog().candidates.none { it.modelId == modelId }) return P6GSelectionMutationResult.InvalidModel
        val next = current.copy(revision = current.revision + 1, modelId = modelId)
        return if (store.saveConversationOverride(next)) P6GSelectionMutationResult.Applied(next.revision) else P6GSelectionMutationResult.PersistenceFailed
    }

    /**
     * One user pick has two durable meanings: it fixes the current conversation and becomes the
     * default for the next one. Android persists both values in one preferences commit.
     */
    fun setComposerModelSelection(
        conversationId: ConversationId,
        modelId: String?,
        expectedConversationRevision: Long,
        expectedGlobalRevision: Long,
    ): P6GSelectionMutationResult {
        val conversation = store.readConversationOverride(conversationId)
        val global = store.readGlobalDefault()
        if (conversation.revision != expectedConversationRevision || global.revision != expectedGlobalRevision) {
            return P6GSelectionMutationResult.Conflict
        }
        if (modelId != null && store.readCatalog().candidates.none { it.modelId == modelId }) {
            return P6GSelectionMutationResult.InvalidModel
        }
        val nextConversation = conversation.copy(revision = conversation.revision + 1, modelId = modelId)
        val nextGlobal = global.copy(revision = global.revision + 1, modelId = modelId)
        return if (store.saveComposerModelSelection(nextConversation, nextGlobal)) {
            P6GSelectionMutationResult.Applied(nextConversation.revision)
        } else {
            P6GSelectionMutationResult.PersistenceFailed
        }
    }

    fun evaluate(conversationId: ConversationId, request: P6GRouteRequest): P6GRouteDecision {
        val catalog = store.readCatalog()
        val override = store.readConversationOverride(conversationId)
        val global = store.readGlobalDefault()
        val effective = request.copy(
            tier = global.tier ?: request.tier,
            manualModelId = override.modelId ?: global.modelId ?: request.manualModelId,
        )
        val decision = router.route(effective, catalog.candidates)
        store.appendRouteMetadata(P6GRouteMetadata(conversationId, catalog.policyVersion, catalog.catalogVersion, effective.tier, decision.source, decision.reason, decision.candidate?.modelId, decision.candidate?.displayName, decision.rejectedCandidates))
        return decision
    }
}

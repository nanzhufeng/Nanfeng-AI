package com.nanzhufeng.ai.domain

/** Shared disclosure/click metadata owner. Public catalog refresh receives no user material. */
class NormalChatRoutingSnapshotLoader(
    private val resolver: ModelResolver,
    private val providerUsable: (ProviderId) -> Boolean,
    private val autoRoutingEnabled: () -> Boolean,
    private val refreshPublicCatalog: () -> Unit,
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000L },
    private val providerEnabled: (ProviderId) -> Boolean = providerUsable,
    private val independentSearchCredentialPresent:()->Boolean={false},
) {
    private var lastRefreshAt: Long? = null
    @Synchronized fun load(): NormalChatRoutingSnapshot {
        val selectable = ComposerModelRoutingCatalog.choices.flatMap { it.routes }.toSet()
        val missingOpenRouter = selectable.any { preset ->
            NanfengModelServiceCatalog.providerFor(preset) == ProviderId.OPENROUTER && resolver.resolve(preset) !is ResolvedModelResult.Resolved
        }
        val now = nowMillis()
        if (missingOpenRouter && providerUsable(ProviderId.OPENROUTER) &&
            (lastRefreshAt == null || now - requireNotNull(lastRefreshAt) >= 60_000L)) {
            lastRefreshAt = now
            try { refreshPublicCatalog() } catch (_: Exception) { /* Keep usable cached profiles; never invent a recipient. */ }
        }
        val models = ModelPresetId.entries.mapNotNull { preset ->
            (resolver.resolve(preset) as? ResolvedModelResult.Resolved)?.model?.let { preset to it }
        }.toMap()
        val availableProviders = models.keys.map(NanfengModelServiceCatalog::providerFor).toSet().filterTo(linkedSetOf(), providerUsable)
        val usable = models.keys.filterTo(linkedSetOf()) { NanfengModelServiceCatalog.providerFor(it) in availableProviders }
        return NormalChatRoutingSnapshot(models, usable, autoRoutingEnabled(), enabledProviders = ProviderId.entries.filterTo(linkedSetOf(), providerEnabled),independentSearchAvailable=independentSearchCredentialPresent())
    }
}

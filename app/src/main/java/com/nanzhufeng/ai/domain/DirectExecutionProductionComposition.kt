package com.nanzhufeng.ai.domain

import java.time.Clock

/** AppContainer-only construction seam. Creating it is inert: no credential, HTTP, Room, or Usage access occurs. */
object DirectExecutionProductionComposition {
    fun create(
        registry: VersionedModelRegistry,
        settings: ModelServiceSettingsRepository,
        credentials: ProviderCredentialStore,
        clock: Clock,
    ): DirectExecutionApplicationOwner = DirectExecutionApplicationOwner(
        registry = OpenRouterVerifiedMultiProviderRegistryProjection(registry),
        orchestrator = MultiModelOrchestrator(AutoRoutingPort { _, _ ->
            error("Direct composition must never invoke Auto routing.")
        }),
        preflight = RealTextExecutionPreflightOrchestrator(settings, credentials, registry, clock),
        coordinator = RealTextExecutionCoordinator(now = clock::instant),
        clock = clock,
    )
}

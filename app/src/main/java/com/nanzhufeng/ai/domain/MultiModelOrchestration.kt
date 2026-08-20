package com.nanzhufeng.ai.domain

/**
 * Pure orchestration contracts for multi-provider model execution.
 *
 * This layer owns no UI, credential, transport, persistence, or provider health-check behavior.
 * It only produces an auditable execution plan. Application owners must still obtain explicit
 * egress and budget confirmation before handing any target to a transport adapter.
 */
enum class MultiModelExecutionMode { DIRECT, COMPARE, AUTO }

enum class AutoRoutingStrategy { QUALITY, BALANCED, COST }

enum class CompareSynthesisPolicy { NOT_REQUESTED }

enum class CompareSharedContextPolicy { EXPLICIT_ADOPTION_ONLY }

enum class ProviderFallbackPolicy { DISABLED_UNTIL_EXPLICIT_AUTHORIZATION }

@JvmInline
value class OrchestrationRequestId(val value: String) {
    init { require(value.isNotBlank() && value == value.trim()) }
}

@JvmInline
value class LogicalModelId(val value: String) {
    init { require(value.isNotBlank() && value == value.trim()) }
}

@JvmInline
value class ModelDeploymentId(val value: String) {
    init { require(value.isNotBlank() && value == value.trim()) }
}

@JvmInline
value class ProviderHandle(val value: String) {
    init { require(value.isNotBlank() && value == value.trim()) }
}

@JvmInline
value class CanonicalContextSnapshotId(val value: String) {
    init { require(value.isNotBlank() && value == value.trim()) }
}

@JvmInline
value class CompareBranchId(val value: String) {
    init { require(value.isNotBlank() && value == value.trim()) }
}

data class CanonicalContextSnapshotRef(
    val id: CanonicalContextSnapshotId,
    val contentHash: String,
    val revision: Long,
) {
    init {
        require(contentHash.matches(Regex("[a-f0-9]{64}")))
        require(revision >= 0)
    }
}

data class ModelDeploymentSelection(
    val logicalModelId: LogicalModelId,
    val deploymentId: ModelDeploymentId,
    val provider: ProviderHandle,
)

sealed interface MultiModelOrchestrationRequest {
    val requestId: OrchestrationRequestId
    val context: CanonicalContextSnapshotRef

    data class Direct(
        override val requestId: OrchestrationRequestId,
        override val context: CanonicalContextSnapshotRef,
        val target: ModelDeploymentSelection,
    ) : MultiModelOrchestrationRequest

    data class Compare(
        override val requestId: OrchestrationRequestId,
        override val context: CanonicalContextSnapshotRef,
        val targets: List<ModelDeploymentSelection>,
        val maxParallelTargets: Int = DEFAULT_COMPARE_MAX_TARGETS,
    ) : MultiModelOrchestrationRequest

    data class Auto(
        override val requestId: OrchestrationRequestId,
        override val context: CanonicalContextSnapshotRef,
        val strategy: AutoRoutingStrategy = AutoRoutingStrategy.BALANCED,
    ) : MultiModelOrchestrationRequest

    companion object {
        /** Confirmed Compare MVP ceiling. UI and execution wiring remain intentionally absent. */
        const val DEFAULT_COMPARE_MAX_TARGETS = 2
    }
}

data class PlannedExecutionTarget(
    val selection: ModelDeploymentSelection,
    val context: CanonicalContextSnapshotRef,
    val fallbackPolicy: ProviderFallbackPolicy = ProviderFallbackPolicy.DISABLED_UNTIL_EXPLICIT_AUTHORIZATION,
    val requiresExplicitEgressConfirmation: Boolean = true,
)

data class CompareBranchPlan(
    val branchId: CompareBranchId,
    val target: PlannedExecutionTarget,
)

sealed interface MultiModelExecutionPlan {
    val requestId: OrchestrationRequestId
    val mode: MultiModelExecutionMode

    data class Direct(
        override val requestId: OrchestrationRequestId,
        val target: PlannedExecutionTarget,
    ) : MultiModelExecutionPlan {
        override val mode = MultiModelExecutionMode.DIRECT
    }

    data class Compare(
        override val requestId: OrchestrationRequestId,
        val branches: List<CompareBranchPlan>,
        val synthesisPolicy: CompareSynthesisPolicy = CompareSynthesisPolicy.NOT_REQUESTED,
        val sharedContextPolicy: CompareSharedContextPolicy = CompareSharedContextPolicy.EXPLICIT_ADOPTION_ONLY,
    ) : MultiModelExecutionPlan {
        override val mode = MultiModelExecutionMode.COMPARE
    }

    data class Auto(
        override val requestId: OrchestrationRequestId,
        val strategy: AutoRoutingStrategy,
        val target: PlannedExecutionTarget,
        val safeReasonCode: String,
    ) : MultiModelExecutionPlan {
        override val mode = MultiModelExecutionMode.AUTO
    }
}

enum class MultiModelOrchestrationRejection {
    COMPARE_REQUIRES_TWO_TARGETS,
    COMPARE_TARGET_LIMIT_EXCEEDED,
    DUPLICATE_COMPARE_DEPLOYMENT,
    INVALID_COMPARE_LIMIT,
    AUTO_ROUTE_UNAVAILABLE,
}

sealed interface MultiModelOrchestrationResult {
    data class Planned(val plan: MultiModelExecutionPlan) : MultiModelOrchestrationResult
    data class Rejected(val reason: MultiModelOrchestrationRejection) : MultiModelOrchestrationResult
}

data class AutoRouteSelection(
    val target: ModelDeploymentSelection,
    /** Stable safe metadata only; never raw prompt, response, key, endpoint, or provider error. */
    val safeReasonCode: String,
) {
    init { require(safeReasonCode.matches(Regex("[A-Z0-9_]{1,64}"))) }
}

fun interface AutoRoutingPort {
    fun route(context: CanonicalContextSnapshotRef, strategy: AutoRoutingStrategy): AutoRouteSelection?
}

class MultiModelOrchestrator(private val autoRoutingPort: AutoRoutingPort) {
    fun plan(request: MultiModelOrchestrationRequest): MultiModelOrchestrationResult = when (request) {
        is MultiModelOrchestrationRequest.Direct -> MultiModelOrchestrationResult.Planned(
            MultiModelExecutionPlan.Direct(
                requestId = request.requestId,
                target = request.target.plannedWith(request.context),
            ),
        )

        is MultiModelOrchestrationRequest.Compare -> planCompare(request)
        is MultiModelOrchestrationRequest.Auto -> planAuto(request)
    }

    private fun planCompare(request: MultiModelOrchestrationRequest.Compare): MultiModelOrchestrationResult {
        if (request.maxParallelTargets < 2) {
            return MultiModelOrchestrationResult.Rejected(MultiModelOrchestrationRejection.INVALID_COMPARE_LIMIT)
        }
        if (request.targets.size < 2) {
            return MultiModelOrchestrationResult.Rejected(MultiModelOrchestrationRejection.COMPARE_REQUIRES_TWO_TARGETS)
        }
        if (request.targets.size > request.maxParallelTargets) {
            return MultiModelOrchestrationResult.Rejected(MultiModelOrchestrationRejection.COMPARE_TARGET_LIMIT_EXCEEDED)
        }
        if (request.targets.map(ModelDeploymentSelection::deploymentId).toSet().size != request.targets.size) {
            return MultiModelOrchestrationResult.Rejected(MultiModelOrchestrationRejection.DUPLICATE_COMPARE_DEPLOYMENT)
        }
        val branches = request.targets.mapIndexed { index, target ->
            CompareBranchPlan(
                branchId = CompareBranchId("${request.requestId.value}:branch:${index + 1}"),
                target = target.plannedWith(request.context),
            )
        }
        return MultiModelOrchestrationResult.Planned(
            MultiModelExecutionPlan.Compare(requestId = request.requestId, branches = branches),
        )
    }

    private fun planAuto(request: MultiModelOrchestrationRequest.Auto): MultiModelOrchestrationResult {
        val selected = autoRoutingPort.route(request.context, request.strategy)
            ?: return MultiModelOrchestrationResult.Rejected(MultiModelOrchestrationRejection.AUTO_ROUTE_UNAVAILABLE)
        return MultiModelOrchestrationResult.Planned(
            MultiModelExecutionPlan.Auto(
                requestId = request.requestId,
                strategy = request.strategy,
                target = selected.target.plannedWith(request.context),
                safeReasonCode = selected.safeReasonCode,
            ),
        )
    }

    private fun ModelDeploymentSelection.plannedWith(context: CanonicalContextSnapshotRef) =
        PlannedExecutionTarget(selection = this, context = context)
}

package com.nanzhufeng.ai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiModelOrchestrationContractsTest {
    private val context = CanonicalContextSnapshotRef(
        id = CanonicalContextSnapshotId("context-fixture-1"),
        contentHash = "a".repeat(64),
        revision = 7,
    )

    private fun target(provider: String, deployment: String, logicalModel: String = "logical.fixture") =
        ModelDeploymentSelection(LogicalModelId(logicalModel), ModelDeploymentId(deployment), ProviderHandle(provider))

    @Test fun `direct preserves exact target and never calls auto router`() {
        var autoCalls = 0
        val orchestrator = MultiModelOrchestrator { _, _ -> autoCalls += 1; null }
        val selected = target("openrouter", "openrouter.claude.fixture")

        val result = orchestrator.plan(
            MultiModelOrchestrationRequest.Direct(OrchestrationRequestId("direct-1"), context, selected),
        ) as MultiModelOrchestrationResult.Planned
        val plan = result.plan as MultiModelExecutionPlan.Direct

        assertEquals(0, autoCalls)
        assertEquals(selected, plan.target.selection)
        assertEquals(ProviderFallbackPolicy.DISABLED_UNTIL_EXPLICIT_AUTHORIZATION, plan.target.fallbackPolicy)
        assertTrue(plan.target.requiresExplicitEgressConfirmation)
    }

    @Test fun `compare creates independent stable branches over the same canonical snapshot without auto routing`() {
        var autoCalls = 0
        val orchestrator = MultiModelOrchestrator { _, _ -> autoCalls += 1; null }
        val result = orchestrator.plan(
            MultiModelOrchestrationRequest.Compare(
                requestId = OrchestrationRequestId("compare-1"),
                context = context,
                targets = listOf(
                    target("openrouter", "openrouter.claude.fixture", "logical.claude"),
                    target("openrouter", "openrouter.gpt.fixture", "logical.gpt"),
                ),
            ),
        ) as MultiModelOrchestrationResult.Planned
        val plan = result.plan as MultiModelExecutionPlan.Compare

        assertEquals(0, autoCalls)
        assertEquals(listOf("compare-1:branch:1", "compare-1:branch:2"), plan.branches.map { it.branchId.value })
        assertTrue(plan.branches.all { it.target.context == context })
        assertEquals(2, plan.branches.map { it.target.selection.deploymentId }.toSet().size)
        assertEquals(CompareSynthesisPolicy.NOT_REQUESTED, plan.synthesisPolicy)
        assertEquals(CompareSharedContextPolicy.EXPLICIT_ADOPTION_ONLY, plan.sharedContextPolicy)
    }

    @Test fun `compare rejects duplicate deployments and a third target beyond the confirmed two-target default`() {
        val orchestrator = MultiModelOrchestrator { _, _ -> error("auto must not run") }
        val duplicate = target("openrouter", "same-deployment")
        assertEquals(
            MultiModelOrchestrationRejection.DUPLICATE_COMPARE_DEPLOYMENT,
            (orchestrator.plan(MultiModelOrchestrationRequest.Compare(OrchestrationRequestId("compare-duplicate"), context, listOf(duplicate, duplicate))) as MultiModelOrchestrationResult.Rejected).reason,
        )
        assertEquals(
            MultiModelOrchestrationRejection.COMPARE_TARGET_LIMIT_EXCEEDED,
            (orchestrator.plan(
                MultiModelOrchestrationRequest.Compare(
                    OrchestrationRequestId("compare-third-target"),
                    context,
                    listOf(target("p1", "d1"), target("p2", "d2"), target("p3", "d3")),
                ),
            ) as MultiModelOrchestrationResult.Rejected).reason,
        )
    }

    @Test fun `auto is the only mode allowed to invoke the routing port`() {
        var autoCalls = 0
        val selected = target("openrouter", "auto-deployment", "logical.auto")
        val orchestrator = MultiModelOrchestrator { snapshot, strategy ->
            autoCalls += 1
            assertEquals(context, snapshot)
            assertEquals(AutoRoutingStrategy.QUALITY, strategy)
            AutoRouteSelection(selected, "QUALITY_MATCH")
        }

        val result = orchestrator.plan(
            MultiModelOrchestrationRequest.Auto(OrchestrationRequestId("auto-1"), context, AutoRoutingStrategy.QUALITY),
        ) as MultiModelOrchestrationResult.Planned
        val plan = result.plan as MultiModelExecutionPlan.Auto

        assertEquals(1, autoCalls)
        assertEquals(selected, plan.target.selection)
        assertEquals("QUALITY_MATCH", plan.safeReasonCode)
    }

    @Test fun `auto fails closed when the routing port has no eligible deployment`() {
        val result = MultiModelOrchestrator { _, _ -> null }.plan(
            MultiModelOrchestrationRequest.Auto(OrchestrationRequestId("auto-none"), context),
        ) as MultiModelOrchestrationResult.Rejected
        assertEquals(MultiModelOrchestrationRejection.AUTO_ROUTE_UNAVAILABLE, result.reason)
    }
}

package com.nanzhufeng.ai.domain

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.util.UUID

/** P8-A's sole execution owner. It never builds a Prompt, reads credentials or performs egress. */
const val P8_AGENT_TOOL_SCHEMA_VERSION = 1

enum class AgentRiskLevel { UNKNOWN, READ_ONLY, LOCAL_REVERSIBLE, EXTERNAL_HIGH }
enum class AgentSideEffectClass { UNKNOWN, NONE, LOCAL_REVERSIBLE, EXTERNAL }
enum class AgentPermission { UNKNOWN, LOCAL_READ, LOCAL_DRAFT, LOCAL_REVERSIBLE_FIXTURE, EXTERNAL }
enum class AgentRunStatus { PENDING, RUNNING, PAUSED, CANCELLED, COMPLETED, FAILED, ROLLED_BACK }
enum class AgentStepStatus { SUCCEEDED, FAILED, CANCELLED, REPLAYED, ROLLED_BACK }

data class AgentBudget(val maxSteps: Long?, val maxToolCalls: Long?, val maxSideEffects: Long?) {
    fun isKnown() = listOf(maxSteps, maxToolCalls, maxSideEffects).all { it != null && it >= 0 }
}

data class AgentRunRequest(
    val id: String = "agent-run-${UUID.randomUUID()}",
    val idempotencyKey: String,
    val budget: AgentBudget,
    val riskCeiling: AgentRiskLevel,
    val permissionGrant: AgentPermission,
    val toolSchemaVersion: Int = P8_AGENT_TOOL_SCHEMA_VERSION,
)

data class AgentToolSchema(
    val id: String,
    val version: Int = P8_AGENT_TOOL_SCHEMA_VERSION,
    val riskLevel: AgentRiskLevel,
    val sideEffectClass: AgentSideEffectClass,
    val requiredPermission: AgentPermission,
    val localTestOnly: Boolean,
    val rollbackSupported: Boolean,
)

data class AgentToolInvocation(val idempotencyKey: String, val toolId: String, val untrustedInput: String)
data class AgentToolResult(val safeSummary: String, val resultHash: String, val rollbackToken: String? = null)
class AgentToolCancelledException : RuntimeException()
class AgentToolTimeoutException : RuntimeException()
data class AgentPlanStep(val idempotencyKey: String, val toolId: String, val inputHash: String)
data class AgentExecutionPlan(val runId: String, val planId: String, val steps: List<AgentPlanStep>)
data class AgentApprovalToken(val runId: String, val planHash: String)
data class AgentReceipt(val idempotencyKey: String, val runId: String, val stepSequence: Long, val toolId: String, val sideEffectClass: AgentSideEffectClass, val outcome: AgentStepStatus, val resultHash: String, val rollbackAvailable: Boolean, val createdAt: Instant)
data class AgentStep(val runId: String, val sequence: Long, val idempotencyKey: String, val toolId: String, val inputHash: String, val status: AgentStepStatus, val resultHash: String?, val safeErrorCode: String?, val sideEffectClass: AgentSideEffectClass, val riskLevel: AgentRiskLevel, val createdAt: Instant)
data class AgentEvent(val runId: String, val sequence: Long, val eventId: String, val kind: String, val fingerprint: String, val createdAt: Instant)
data class AgentCheckpoint(val runId: String, val sequence: Long, val status: AgentRunStatus, val nextStepSequence: Long, val safeStateHash: String, val createdAt: Instant)
data class AgentRun(
    val id: String, val idempotencyKey: String, val toolSchemaVersion: Int, val status: AgentRunStatus,
    val riskCeiling: AgentRiskLevel, val permissionGrant: AgentPermission, val budget: AgentBudget,
    val usedSteps: Long = 0, val usedToolCalls: Long = 0, val usedSideEffects: Long = 0,
    val lastCheckpointSequence: Long? = null, val safeErrorCode: String? = null,
    val createdAt: Instant, val updatedAt: Instant,
)
data class AgentRunSnapshot(val run: AgentRun, val steps: List<AgentStep>, val events: List<AgentEvent>, val checkpoints: List<AgentCheckpoint>)
/** P8-B deliberately exposes only aggregate durable facts; null is unknown, while zero is known-empty. */
data class AgentLedgerStatus(
    val toolSchemaVersion: Int?,
    val runCount: Long?,
    val stepCount: Long?,
    val eventCount: Long?,
    val checkpointCount: Long?,
    val receiptCount: Long?,
)

/** Durable implementations must atomically append a step/event/checkpoint/receipt with the run update. */
interface AgentLedger {
    fun findRun(id: String): AgentRunSnapshot?
    fun findRunByIdempotencyKey(key: String): AgentRunSnapshot?
    fun findReceipt(key: String): AgentReceipt?
    fun findEvent(runId: String, eventId: String): AgentEvent?
    fun status(): AgentLedgerStatus
    /** Ordered durable facts only. Implementations must not synthesize UI state. */
    fun allRuns(): List<AgentRunSnapshot> = emptyList()
    fun create(run: AgentRun): AgentRunSnapshot
    fun append(snapshot: AgentRunSnapshot, step: AgentStep?, event: AgentEvent, checkpoint: AgentCheckpoint?, receipt: AgentReceipt?): AgentRunSnapshot
}

enum class P8BProductionToolAvailability { NO_PRODUCTION_EXECUTOR }

data class P8BReadOnlyAgentLedgerStatus(
    val schema: AgentToolSchema,
    val declaredBudget: AgentBudget,
    val availability: P8BProductionToolAvailability,
    val visibleInUi: Boolean,
    val ledger: AgentLedgerStatus,
)

/**
 * P8-B's sole production bridge. It neither accepts untrusted input nor invokes P8-A runtime:
 * it cannot create a run, consume budget, or append durable facts.
 */
class P8BProductionReadOnlyAgentLedgerStatus(private val ledger: AgentLedger) {
    fun read(): P8BReadOnlyAgentLedgerStatus = P8BReadOnlyAgentLedgerStatus(
        schema = schema,
        declaredBudget = AgentBudget(maxSteps = 1, maxToolCalls = 1, maxSideEffects = 0),
        availability = P8BProductionToolAvailability.NO_PRODUCTION_EXECUTOR,
        visibleInUi = false,
        ledger = ledger.status(),
    )

    companion object {
        val schema = AgentToolSchema(
            id = "p8_local_ledger_status",
            riskLevel = AgentRiskLevel.READ_ONLY,
            sideEffectClass = AgentSideEffectClass.NONE,
            requiredPermission = AgentPermission.LOCAL_READ,
            localTestOnly = false,
            rollbackSupported = false,
        )
    }
}

fun interface AgentTool { fun execute(invocation: AgentToolInvocation): AgentToolResult }

interface AgentToolRegistry { fun tool(id: String): Pair<AgentToolSchema, AgentTool>? }

/** Only tests may instantiate this registry. Production DI/UI must not register it. */
class LocalTestOnlyAgentToolRegistry(private val tools: Map<String, Pair<AgentToolSchema, AgentTool>>) : AgentToolRegistry {
    override fun tool(id: String) = tools[id]
    companion object {
        fun fixture(): LocalTestOnlyAgentToolRegistry = LocalTestOnlyAgentToolRegistry(
            listOf(
                AgentToolSchema("fixture_research", riskLevel = AgentRiskLevel.READ_ONLY, sideEffectClass = AgentSideEffectClass.NONE, requiredPermission = AgentPermission.LOCAL_READ, localTestOnly = true, rollbackSupported = false) to AgentTool { AgentToolResult("LOCAL_TEST_ONLY fixture research; untrusted text was data only", sha("fixture-research")) },
                AgentToolSchema("fixture_draft", riskLevel = AgentRiskLevel.READ_ONLY, sideEffectClass = AgentSideEffectClass.NONE, requiredPermission = AgentPermission.LOCAL_DRAFT, localTestOnly = true, rollbackSupported = false) to AgentTool { AgentToolResult("LOCAL_TEST_ONLY draft candidate", sha("fixture-draft")) },
                AgentToolSchema("fixture_reversible_action", riskLevel = AgentRiskLevel.LOCAL_REVERSIBLE, sideEffectClass = AgentSideEffectClass.LOCAL_REVERSIBLE, requiredPermission = AgentPermission.LOCAL_REVERSIBLE_FIXTURE, localTestOnly = true, rollbackSupported = true) to AgentTool { AgentToolResult("LOCAL_TEST_ONLY reversible fixture action", sha("fixture-action"), "fixture-rollback") },
                AgentToolSchema("fixture_failure", riskLevel = AgentRiskLevel.READ_ONLY, sideEffectClass = AgentSideEffectClass.NONE, requiredPermission = AgentPermission.LOCAL_READ, localTestOnly = true, rollbackSupported = false) to AgentTool { throw IllegalStateException("LOCAL_TEST_ONLY failure injection") },
                AgentToolSchema("fixture_cancel", riskLevel = AgentRiskLevel.READ_ONLY, sideEffectClass = AgentSideEffectClass.NONE, requiredPermission = AgentPermission.LOCAL_READ, localTestOnly = true, rollbackSupported = false) to AgentTool { throw AgentToolCancelledException() },
                AgentToolSchema("fixture_timeout", riskLevel = AgentRiskLevel.READ_ONLY, sideEffectClass = AgentSideEffectClass.NONE, requiredPermission = AgentPermission.LOCAL_READ, localTestOnly = true, rollbackSupported = false) to AgentTool { throw AgentToolTimeoutException() },
            ).associateBy { it.first.id },
        )
    }
}

sealed interface AgentExecutionResult {
    data class Accepted(val snapshot: AgentRunSnapshot, val summary: String) : AgentExecutionResult
    data class Approved(val snapshot: AgentRunSnapshot, val token: AgentApprovalToken) : AgentExecutionResult
    data class Replayed(val receipt: AgentReceipt) : AgentExecutionResult
    data class Rejected(val code: String, val snapshot: AgentRunSnapshot? = null) : AgentExecutionResult
}

class ControlledAgentRuntime(
    private val ledger: AgentLedger,
    private val tools: AgentToolRegistry?,
    private val clock: Clock,
    private val allowProductionBuiltInTool: Boolean = false,
) {
    fun start(request: AgentRunRequest): AgentExecutionResult {
        ledger.findRunByIdempotencyKey(request.idempotencyKey)?.let { return AgentExecutionResult.Accepted(it, "replayed run") }
        if (request.toolSchemaVersion != P8_AGENT_TOOL_SCHEMA_VERSION || !request.budget.isKnown() || request.riskCeiling == AgentRiskLevel.UNKNOWN || request.permissionGrant == AgentPermission.UNKNOWN) return AgentExecutionResult.Rejected("UNKNOWN_OR_INVALID_RUN_CONTRACT")
        val now = clock.instant()
        return AgentExecutionResult.Accepted(ledger.create(AgentRun(request.id, request.idempotencyKey, request.toolSchemaVersion, AgentRunStatus.PENDING, request.riskCeiling, request.permissionGrant, request.budget, createdAt = now, updatedAt = now)), "local run created")
    }

    fun execute(runId: String, invocation: AgentToolInvocation): AgentExecutionResult {
        ledger.findReceipt(invocation.idempotencyKey)?.let { return AgentExecutionResult.Replayed(it) }
        val snapshot = ledger.findRun(runId) ?: return AgentExecutionResult.Rejected("RUN_NOT_FOUND")
        if (snapshot.run.status !in setOf(AgentRunStatus.PENDING, AgentRunStatus.RUNNING)) return AgentExecutionResult.Rejected("RUN_NOT_EXECUTABLE", snapshot)
        val pair = tools?.tool(invocation.toolId) ?: return fail(snapshot, "TOOL_NOT_REGISTERED")
        val schema = pair.first
        if (!validSchema(schema)) return fail(snapshot, "UNKNOWN_TOOL_CONTRACT")
        if (!allows(snapshot.run.permissionGrant, schema.requiredPermission) || schema.riskLevel.ordinal > snapshot.run.riskCeiling.ordinal || schema.sideEffectClass == AgentSideEffectClass.EXTERNAL) return fail(snapshot, "PERMISSION_OR_RISK_DENIED")
        val sideEffectCost = if (schema.sideEffectClass == AgentSideEffectClass.NONE) 0 else 1
        val budget = snapshot.run.budget
        if (snapshot.run.usedSteps >= budget.maxSteps!! || snapshot.run.usedToolCalls >= budget.maxToolCalls!! || snapshot.run.usedSideEffects + sideEffectCost > budget.maxSideEffects!!) return fail(snapshot, "BUDGET_EXHAUSTED")
        val sequence = snapshot.steps.size.toLong()
        val now = clock.instant()
        val result = try { pair.second.execute(invocation) } catch (_: AgentToolCancelledException) { return cancelFromTool(snapshot) } catch (_: AgentToolTimeoutException) { return fail(snapshot, "TOOL_TIMEOUT") } catch (_: Exception) { return fail(snapshot, "TOOL_ERROR") }
        val step = AgentStep(runId, sequence, invocation.idempotencyKey, schema.id, sha(invocation.untrustedInput), AgentStepStatus.SUCCEEDED, result.resultHash, null, schema.sideEffectClass, schema.riskLevel, now)
        val updated = snapshot.run.copy(status = AgentRunStatus.RUNNING, usedSteps = snapshot.run.usedSteps + 1, usedToolCalls = snapshot.run.usedToolCalls + 1, usedSideEffects = snapshot.run.usedSideEffects + sideEffectCost, updatedAt = now)
        val event = event(updated, snapshot.events.size.toLong(), "STEP_SUCCEEDED", invocation.idempotencyKey, now)
        val checkpoint = checkpoint(updated, snapshot.checkpoints.size.toLong(), sequence + 1, now)
        val receipt = AgentReceipt(invocation.idempotencyKey, runId, sequence, schema.id, schema.sideEffectClass, AgentStepStatus.SUCCEEDED, result.resultHash, schema.rollbackSupported && result.rollbackToken != null, now)
        return AgentExecutionResult.Accepted(ledger.append(AgentRunSnapshot(updated, snapshot.steps, snapshot.events, snapshot.checkpoints), step, event, checkpoint, receipt), result.safeSummary)
    }

    /** P8-B LOCAL_TEST_ONLY harness: a plan contains only stable tool/idempotency/input hashes. */
    fun plan(plan: AgentExecutionPlan): AgentExecutionResult {
        val snapshot = ledger.findRun(plan.runId) ?: return AgentExecutionResult.Rejected("RUN_NOT_FOUND")
        if (snapshot.run.status !in setOf(AgentRunStatus.PENDING, AgentRunStatus.RUNNING) || plan.steps.isEmpty()) return fail(snapshot, "INVALID_PLAN")
        if (plan.steps.size > snapshot.run.budget.maxSteps!!) return fail(snapshot, "PLAN_BUDGET_DENIED")
        for (step in plan.steps) {
            val schema = tools?.tool(step.toolId)?.first ?: return fail(snapshot, "PLAN_TOOL_NOT_REGISTERED")
            if (!validSchema(schema) || !allows(snapshot.run.permissionGrant, schema.requiredPermission) || schema.riskLevel.ordinal > snapshot.run.riskCeiling.ordinal || schema.sideEffectClass == AgentSideEffectClass.EXTERNAL) return fail(snapshot, "PLAN_PERMISSION_OR_RISK_DENIED")
        }
        val now = clock.instant(); val planHash = planHash(plan)
        val event = event(snapshot.run, snapshot.events.size.toLong(), "PLAN_ACCEPTED", planHash, now)
        return AgentExecutionResult.Accepted(ledger.append(snapshot, null, event, checkpoint(snapshot.run, snapshot.checkpoints.size.toLong(), snapshot.steps.size.toLong(), now), null), "local test plan accepted")
    }

    /** The token is explicit caller material; only its hash-equivalent plan binding is durably audited. */
    fun approve(plan: AgentExecutionPlan): AgentExecutionResult {
        val snapshot = ledger.findRun(plan.runId) ?: return AgentExecutionResult.Rejected("RUN_NOT_FOUND")
        val planHash = planHash(plan)
        if (snapshot.events.none { it.kind == "PLAN_ACCEPTED" && it.fingerprint == sha("${snapshot.run.id}|${snapshot.events.indexOf(it)}|PLAN_ACCEPTED|$planHash") }) return AgentExecutionResult.Rejected("APPROVAL_DENIED", snapshot)
        val token = AgentApprovalToken(plan.runId, planHash)
        val now = clock.instant(); val event = event(snapshot.run, snapshot.events.size.toLong(), "PLAN_APPROVED", token.planHash, now)
        return AgentExecutionResult.Approved(ledger.append(snapshot, null, event, checkpoint(snapshot.run, snapshot.checkpoints.size.toLong(), snapshot.steps.size.toLong(), now), null), token)
    }

    fun executeApproved(plan: AgentExecutionPlan, approval: AgentApprovalToken, invocation: AgentToolInvocation): AgentExecutionResult {
        val snapshot = ledger.findRun(plan.runId) ?: return AgentExecutionResult.Rejected("RUN_NOT_FOUND")
        val planHash = planHash(plan)
        if (approval.runId != plan.runId || approval.planHash != planHash || plan.steps.none { it.idempotencyKey == invocation.idempotencyKey && it.toolId == invocation.toolId && it.inputHash == sha(invocation.untrustedInput) }) return AgentExecutionResult.Rejected("APPROVAL_DENIED", snapshot)
        val approvalFingerprint = sha("${snapshot.run.id}|${snapshot.events.indexOfLast { it.kind == "PLAN_APPROVED" }}|PLAN_APPROVED|$planHash")
        if (snapshot.events.none { it.kind == "PLAN_APPROVED" && it.fingerprint == approvalFingerprint }) return AgentExecutionResult.Rejected("APPROVAL_DENIED", snapshot)
        return execute(plan.runId, invocation)
    }

    /** Duplicate event IDs with the same fingerprint only read back; conflicting replays fail closed. */
    fun replayHarnessEvent(runId: String, eventId: String, fingerprint: String): AgentExecutionResult {
        val snapshot = ledger.findRun(runId) ?: return AgentExecutionResult.Rejected("RUN_NOT_FOUND")
        val existing = ledger.findEvent(runId, eventId)
        if (existing != null) return if (existing.fingerprint == fingerprint) AgentExecutionResult.Accepted(snapshot, "replayed event") else AgentExecutionResult.Rejected("EVENT_REPLAY_CONFLICT", snapshot)
        val now = clock.instant(); val event = AgentEvent(runId, snapshot.events.size.toLong(), eventId, "HARNESS_EVENT", fingerprint, now)
        return AgentExecutionResult.Accepted(ledger.append(snapshot, null, event, checkpoint(snapshot.run, snapshot.checkpoints.size.toLong(), snapshot.steps.size.toLong(), now), null), "harness event accepted")
    }

    fun pause(runId: String): AgentExecutionResult = transition(runId, setOf(AgentRunStatus.PENDING, AgentRunStatus.RUNNING), AgentRunStatus.PAUSED, "PAUSED")
    fun resume(runId: String): AgentExecutionResult = transition(runId, setOf(AgentRunStatus.PAUSED), AgentRunStatus.RUNNING, "RESUMED")
    fun cancel(runId: String): AgentExecutionResult = transition(runId, setOf(AgentRunStatus.PENDING, AgentRunStatus.RUNNING, AgentRunStatus.PAUSED), AgentRunStatus.CANCELLED, "CANCELLED")
    fun rollback(runId: String, receiptKey: String): AgentExecutionResult {
        val snapshot = ledger.findRun(runId) ?: return AgentExecutionResult.Rejected("RUN_NOT_FOUND")
        val receipt = ledger.findReceipt(receiptKey) ?: return AgentExecutionResult.Rejected("RECEIPT_NOT_FOUND", snapshot)
        if (receipt.runId != runId || !receipt.rollbackAvailable || receipt.sideEffectClass != AgentSideEffectClass.LOCAL_REVERSIBLE) return AgentExecutionResult.Rejected("ROLLBACK_DENIED", snapshot)
        val now = clock.instant(); val updated = snapshot.run.copy(status = AgentRunStatus.ROLLED_BACK, updatedAt = now)
        val event = event(updated, snapshot.events.size.toLong(), "ROLLBACK_COMPLETED", receiptKey, now)
        return AgentExecutionResult.Accepted(ledger.append(AgentRunSnapshot(updated, snapshot.steps, snapshot.events, snapshot.checkpoints), null, event, checkpoint(updated, snapshot.checkpoints.size.toLong(), snapshot.steps.size.toLong(), now), null), "local fixture rollback recorded")
    }

    private fun transition(runId: String, allowed: Set<AgentRunStatus>, target: AgentRunStatus, kind: String): AgentExecutionResult {
        val snapshot = ledger.findRun(runId) ?: return AgentExecutionResult.Rejected("RUN_NOT_FOUND")
        if (snapshot.run.status !in allowed) return AgentExecutionResult.Rejected("INVALID_STATE_TRANSITION", snapshot)
        val now = clock.instant(); val updated = snapshot.run.copy(status = target, updatedAt = now)
        return AgentExecutionResult.Accepted(ledger.append(AgentRunSnapshot(updated, snapshot.steps, snapshot.events, snapshot.checkpoints), null, event(updated, snapshot.events.size.toLong(), kind, runId, now), checkpoint(updated, snapshot.checkpoints.size.toLong(), snapshot.steps.size.toLong(), now), null), kind)
    }

    private fun fail(snapshot: AgentRunSnapshot, code: String): AgentExecutionResult {
        val now = clock.instant(); val updated = snapshot.run.copy(status = AgentRunStatus.FAILED, safeErrorCode = code, updatedAt = now)
        return AgentExecutionResult.Rejected(code, ledger.append(AgentRunSnapshot(updated, snapshot.steps, snapshot.events, snapshot.checkpoints), null, event(updated, snapshot.events.size.toLong(), code, code, now), checkpoint(updated, snapshot.checkpoints.size.toLong(), snapshot.steps.size.toLong(), now), null))
    }
    private fun cancelFromTool(snapshot: AgentRunSnapshot): AgentExecutionResult {
        val now = clock.instant(); val updated = snapshot.run.copy(status = AgentRunStatus.CANCELLED, safeErrorCode = "TOOL_CANCELLED", updatedAt = now)
        return AgentExecutionResult.Rejected("TOOL_CANCELLED", ledger.append(AgentRunSnapshot(updated, snapshot.steps, snapshot.events, snapshot.checkpoints), null, event(updated, snapshot.events.size.toLong(), "TOOL_CANCELLED", updated.id, now), checkpoint(updated, snapshot.checkpoints.size.toLong(), snapshot.steps.size.toLong(), now), null))
    }
    private fun validSchema(schema: AgentToolSchema) =
        (schema.localTestOnly || (allowProductionBuiltInTool && schema == P8CProductionLocalAgentController.schema)) &&
            schema.version == P8_AGENT_TOOL_SCHEMA_VERSION && schema.riskLevel != AgentRiskLevel.UNKNOWN &&
            schema.sideEffectClass != AgentSideEffectClass.UNKNOWN && schema.requiredPermission != AgentPermission.UNKNOWN
    private fun planHash(plan: AgentExecutionPlan) = sha("${plan.runId}|${plan.planId}|" + plan.steps.joinToString("|") { "${it.idempotencyKey}:${it.toolId}:${it.inputHash}" })
    private fun event(run: AgentRun, sequence: Long, kind: String, source: String, now: Instant) = AgentEvent(run.id, sequence, "${run.id}:$sequence:$kind", kind, sha("${run.id}|$sequence|$kind|$source"), now)
    private fun checkpoint(run: AgentRun, sequence: Long, next: Long, now: Instant) = AgentCheckpoint(run.id, sequence, run.status, next, sha("${run.id}|${run.status}|$next|${run.usedSteps}|${run.usedToolCalls}|${run.usedSideEffects}"), now)
}

/**
 * P8-C's sole release executor. The action has no user/model input and can only inspect this
 * secret-free Agent ledger; it cannot reach app files, other apps, a provider, or the network.
 * Approval material is memory-only, expires quickly, and is consumed before execution.
 */
class P8CProductionLocalAgentController(private val ledger: AgentLedger, private val clock: Clock) {
    data class PendingApproval(val plan: AgentExecutionPlan, val expiresAt: Instant, internal var core: AgentApprovalToken? = null, var consumed: Boolean = false)
    data class InspectState(val runs: List<AgentRunSnapshot>, val ledgerStatus: AgentLedgerStatus, val pending: PendingApproval?)

    private val registry = object : AgentToolRegistry {
        override fun tool(id: String): Pair<AgentToolSchema, AgentTool>? = if (id == schema.id) schema to AgentTool {
            val status = ledger.status()
            AgentToolResult(
                "已完成本地账本自检：未连接模型与外部工具。运行 ${status.runCount ?: "未知"}，步骤 ${status.stepCount ?: "未知"}。",
                sha("p8c-local-ledger-inspect|${status.runCount}|${status.stepCount}|${status.eventCount}|${status.receiptCount}"),
            )
        } else null
    }
    private val runtime = ControlledAgentRuntime(ledger, registry, clock, allowProductionBuiltInTool = true)
    private var pending: PendingApproval? = null

    fun inspect(): InspectState = InspectState(ledger.allRuns(), ledger.status(), pending?.takeIf { !it.consumed && clock.instant().isBefore(it.expiresAt) })

    fun begin(): AgentExecutionResult {
        val nonce = UUID.randomUUID().toString()
        val request = AgentRunRequest("p8c-local-$nonce", "p8c-run-$nonce", AgentBudget(1, 1, 0), AgentRiskLevel.READ_ONLY, AgentPermission.LOCAL_READ)
        val accepted = runtime.start(request)
        if (accepted !is AgentExecutionResult.Accepted) return accepted
        val plan = AgentExecutionPlan(request.id, "p8c-plan-$nonce", listOf(AgentPlanStep("p8c-step-$nonce", schema.id, sha(P8C_INPUT_SENTINEL))))
        val planned = runtime.plan(plan)
        if (planned !is AgentExecutionResult.Accepted) return planned
        pending = PendingApproval(plan, clock.instant().plusSeconds(90))
        return planned
    }

    fun confirm(): AgentExecutionResult {
        val token = pending ?: return AgentExecutionResult.Rejected("APPROVAL_REQUIRED")
        if (token.consumed || !clock.instant().isBefore(token.expiresAt)) { pending = null; return AgentExecutionResult.Rejected("APPROVAL_EXPIRED") }
        val approved = runtime.approve(token.plan)
        if (approved !is AgentExecutionResult.Approved) return approved
        token.core = approved.token
        token.consumed = true
        pending = null
        return runtime.executeApproved(token.plan, requireNotNull(token.core), AgentToolInvocation(token.plan.steps.single().idempotencyKey, schema.id, P8C_INPUT_SENTINEL))
    }
    fun pause(runId: String) = runtime.pause(runId)
    fun resume(runId: String) = runtime.resume(runId)
    /** A cancelled Run can never consume a previously displayed in-memory approval. */
    fun cancel(runId: String): AgentExecutionResult {
        val result = runtime.cancel(runId)
        if (result is AgentExecutionResult.Accepted && pending?.plan?.runId == runId) pending = null
        return result
    }

    companion object {
        const val P8C_INPUT_SENTINEL = "p8c-local-ledger-inspect-v1"
        val schema = AgentToolSchema("p8c_local_ledger_inspect", riskLevel = AgentRiskLevel.READ_ONLY, sideEffectClass = AgentSideEffectClass.NONE, requiredPermission = AgentPermission.LOCAL_READ, localTestOnly = false, rollbackSupported = false)
    }
}

private fun allows(grant: AgentPermission, required: AgentPermission) = grant == required || (grant == AgentPermission.LOCAL_REVERSIBLE_FIXTURE && required in setOf(AgentPermission.LOCAL_READ, AgentPermission.LOCAL_DRAFT))
fun sha(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }

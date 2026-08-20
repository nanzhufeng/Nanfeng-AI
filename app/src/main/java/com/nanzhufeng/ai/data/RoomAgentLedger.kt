package com.nanzhufeng.ai.data

import androidx.room.RoomDatabase
import com.nanzhufeng.ai.data.local.*
import com.nanzhufeng.ai.domain.*
import java.time.Instant
import java.util.concurrent.Callable

/** Android P8-A persistence owner. Every append uses one Room transaction. */
class RoomAgentLedger(private val database: NanfengAiDatabase) : AgentLedger {
    private val dao = database.agentLedgerDao()
    override fun findRun(id: String) = dao.run(id)?.snapshot()
    override fun findRunByIdempotencyKey(key: String) = dao.runByKey(key)?.snapshot()
    override fun findReceipt(key: String) = dao.receipt(key)?.domain()
    override fun findEvent(runId: String, eventId: String) = dao.event(runId, eventId)?.domain()
    override fun status() = AgentLedgerStatus(
        toolSchemaVersion = P8_AGENT_TOOL_SCHEMA_VERSION,
        runCount = dao.runCount(),
        stepCount = dao.stepCount(),
        eventCount = dao.eventCount(),
        checkpointCount = dao.checkpointCount(),
        receiptCount = dao.receiptCount(),
    )
    override fun allRuns() = dao.runs().map { it.snapshot() }
    override fun create(run: AgentRun): AgentRunSnapshot = database.transaction {
        dao.runByKey(run.idempotencyKey)?.snapshot() ?: run.let { dao.insertRun(it.entity()); AgentRunSnapshot(it, emptyList(), emptyList(), emptyList()) }
    }
    override fun append(snapshot: AgentRunSnapshot, step: AgentStep?, event: AgentEvent, checkpoint: AgentCheckpoint?, receipt: AgentReceipt?): AgentRunSnapshot = database.transaction {
        val current = dao.run(snapshot.run.id) ?: error("agent run missing")
        require(current.usedSteps <= snapshot.run.usedSteps && current.usedToolCalls <= snapshot.run.usedToolCalls) { "agent ledger counters regressed" }
        step?.let { dao.insertStep(it.entity()) }; dao.insertEvent(event.entity()); checkpoint?.let { dao.insertCheckpoint(it.entity()) }; receipt?.let { dao.insertReceipt(it.entity()) }
        dao.updateRun(snapshot.run.id, snapshot.run.status.name, snapshot.run.usedSteps, snapshot.run.usedToolCalls, snapshot.run.usedSideEffects, checkpoint?.sequence ?: snapshot.run.lastCheckpointSequence, snapshot.run.safeErrorCode, snapshot.run.updatedAt.toEpochMilli())
        snapshot.run.copy(lastCheckpointSequence = checkpoint?.sequence ?: snapshot.run.lastCheckpointSequence).let { AgentRunSnapshot(it, dao.steps(it.id).map { value -> value.domain() }, dao.events(it.id).map { value -> value.domain() }, dao.checkpoints(it.id).map { value -> value.domain() }) }
    }
    private fun AgentRunEntity.snapshot() = AgentRunSnapshot(domain(), dao.steps(id).map { it.domain() }, dao.events(id).map { it.domain() }, dao.checkpoints(id).map { it.domain() })
}

private fun AgentRun.entity() = AgentRunEntity(id, idempotencyKey, toolSchemaVersion, status.name, riskCeiling.name, permissionGrant.name, budget.maxSteps!!, budget.maxToolCalls!!, budget.maxSideEffects!!, usedSteps, usedToolCalls, usedSideEffects, lastCheckpointSequence, safeErrorCode, createdAt.toEpochMilli(), updatedAt.toEpochMilli())
private fun AgentRunEntity.domain() = AgentRun(id, idempotencyKey, toolSchemaVersion, AgentRunStatus.valueOf(status), AgentRiskLevel.valueOf(riskCeiling), AgentPermission.valueOf(permissionGrant), AgentBudget(maxSteps, maxToolCalls, maxSideEffects), usedSteps, usedToolCalls, usedSideEffects, lastCheckpointSequence, safeErrorCode, Instant.ofEpochMilli(createdAtEpochMs), Instant.ofEpochMilli(updatedAtEpochMs))
private fun AgentStep.entity() = AgentStepEntity(runId, sequence, idempotencyKey, toolId, inputHash, status.name, resultHash, safeErrorCode, sideEffectClass.name, riskLevel.name, createdAt.toEpochMilli())
private fun AgentStepEntity.domain() = AgentStep(runId, sequence, idempotencyKey, toolId, inputHash, AgentStepStatus.valueOf(status), resultHash, safeErrorCode, AgentSideEffectClass.valueOf(sideEffectClass), AgentRiskLevel.valueOf(riskLevel), Instant.ofEpochMilli(createdAtEpochMs))
private fun AgentEvent.entity() = AgentEventEntity(runId, sequence, eventId, kind, fingerprint, createdAt.toEpochMilli())
private fun AgentEventEntity.domain() = AgentEvent(runId, sequence, eventId, kind, fingerprint, Instant.ofEpochMilli(createdAtEpochMs))
private fun AgentCheckpoint.entity() = AgentCheckpointEntity(runId, sequence, status.name, nextStepSequence, safeStateHash, createdAt.toEpochMilli())
private fun AgentCheckpointEntity.domain() = AgentCheckpoint(runId, sequence, AgentRunStatus.valueOf(status), nextStepSequence, safeStateHash, Instant.ofEpochMilli(createdAtEpochMs))
private fun AgentReceipt.entity() = AgentSideEffectReceiptEntity(idempotencyKey, runId, stepSequence, toolId, sideEffectClass.name, outcome.name, resultHash, rollbackAvailable, createdAt.toEpochMilli())
private fun AgentSideEffectReceiptEntity.domain() = AgentReceipt(idempotencyKey, runId, stepSequence, toolId, AgentSideEffectClass.valueOf(sideEffectClass), AgentStepStatus.valueOf(outcome), resultHash, rollbackAvailable, Instant.ofEpochMilli(createdAtEpochMs))
private fun <T> RoomDatabase.transaction(block: () -> T): T = runInTransaction(Callable { block() })

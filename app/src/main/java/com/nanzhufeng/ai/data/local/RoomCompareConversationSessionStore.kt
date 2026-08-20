package com.nanzhufeng.ai.data.local

import com.nanzhufeng.ai.domain.CanonicalContextSnapshotId
import com.nanzhufeng.ai.domain.CanonicalContextSnapshotRef
import com.nanzhufeng.ai.domain.CompareBranchAdoptionIntent
import com.nanzhufeng.ai.domain.CompareBranchAdoptionPlan
import com.nanzhufeng.ai.domain.CompareBranchCancellationId
import com.nanzhufeng.ai.domain.CompareBranchExecutionGrant
import com.nanzhufeng.ai.domain.CompareBranchFollowUpIntent
import com.nanzhufeng.ai.domain.CompareBranchFollowUpPlan
import com.nanzhufeng.ai.domain.CompareBranchId
import com.nanzhufeng.ai.domain.CompareBranchReservationState
import com.nanzhufeng.ai.domain.CompareBranchRuntimeReference
import com.nanzhufeng.ai.domain.CompareBranchTerminalIntent
import com.nanzhufeng.ai.domain.CompareConversationBranchPlan
import com.nanzhufeng.ai.domain.CompareConversationSessionId
import com.nanzhufeng.ai.domain.CompareConversationSessionPlan
import com.nanzhufeng.ai.domain.CompareConversationSessionRead
import com.nanzhufeng.ai.domain.CompareConversationSessionRejection
import com.nanzhufeng.ai.domain.CompareConversationSessionStore
import com.nanzhufeng.ai.domain.CompareDispatchIntent
import com.nanzhufeng.ai.domain.CompareDispatchRecoveryState
import com.nanzhufeng.ai.domain.CompareSessionStoreResult
import com.nanzhufeng.ai.domain.CompareSharedContextPolicy
import com.nanzhufeng.ai.domain.CompareSynthesisConsentBudgetGate
import com.nanzhufeng.ai.domain.CompareSynthesisIntent
import com.nanzhufeng.ai.domain.CompareSynthesisPlan
import com.nanzhufeng.ai.domain.CompareSynthesisPolicy
import com.nanzhufeng.ai.domain.ConversationId
import com.nanzhufeng.ai.domain.ConversationRealTextExecutionId
import com.nanzhufeng.ai.domain.InvocationId
import com.nanzhufeng.ai.domain.LogicalModelId
import com.nanzhufeng.ai.domain.MessageNodeId
import com.nanzhufeng.ai.domain.ModelDeploymentId
import com.nanzhufeng.ai.domain.ProviderAttemptId
import com.nanzhufeng.ai.domain.ProviderHandle
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.util.concurrent.Callable

/**
 * Room owner for MM-O4-C. It never inserts into message_nodes or changes conversations.currentLeaf.
 * Compare runtime rows therefore coexist with the P3 per-conversation runtime without changing it.
 */
class RoomCompareConversationSessionStore(
    private val database: NanfengAiDatabase,
    private val clock: Clock = Clock.systemUTC(),
) : CompareConversationSessionStore {
    override fun persist(plan: CompareConversationSessionPlan): CompareSessionStoreResult<CompareConversationSessionRead> = transaction {
        val dao = database.compareConversationDao()
        dao.sessionForIntent(plan.intentId.value)?.let { existing ->
            val read = readExisting(dao, existing) ?: return@transaction rejected(CompareConversationSessionRejection.SESSION_INTENT_CONFLICT)
            return@transaction if (read.plan == plan) CompareSessionStoreResult.Replayed(read)
            else rejected(CompareConversationSessionRejection.SESSION_INTENT_CONFLICT)
        }
        if (dao.sessionForConfirmation(plan.confirmationId) != null) {
            return@transaction rejected(CompareConversationSessionRejection.GRANT_ALREADY_CONSUMED)
        }
        val conversation = database.conversationDao().findConversation(plan.conversationId.value)
            ?: return@transaction rejected(CompareConversationSessionRejection.CONVERSATION_MISMATCH)
        if (conversation.currentLeafMessageId != plan.sharedCurrentLeafAtPlanning.value ||
            plan.parentUserMessageId != plan.sharedCurrentLeafAtPlanning
        ) return@transaction rejected(CompareConversationSessionRejection.PARENT_NOT_CURRENT_USER_LEAF)

        dao.insertSession(plan.toEntity())
        dao.insertBranches(plan.branches.map { it.toEntity(plan.sessionId) })
        dao.insertRuntimeStates(plan.branches.map { branch ->
            CompareBranchRuntimeStateEntity(
                sessionId = plan.sessionId.value, branchId = branch.branchId.value,
                invocationId = branch.invocationId.value, assistantMessageId = branch.assistantMessageId.value,
                state = branch.state.name, nextExpectedSequence = 1, lastCheckpointSequence = 0,
                safeErrorCode = null, updatedAtEpochMs = plan.createdAt.toEpochMilli(),
            )
        })
        plan.branches.forEach { branch ->
            dao.insertRuntimeEvent(CompareBranchRuntimeEventEntity(
                eventId = "compare:${plan.sessionId.value}:${branch.branchId.value}:reserved",
                sessionId = plan.sessionId.value, branchId = branch.branchId.value, invocationId = branch.invocationId.value,
                sequence = 0, kind = "RESERVED", payloadFingerprint = sha256("reserved|${plan.sessionId.value}|${branch.branchId.value}|${branch.invocationId.value}"),
                emittedAtEpochMs = plan.createdAt.toEpochMilli(),
            ))
        }
        CompareSessionStoreResult.Stored(requireNotNull(readExisting(dao, plan.sessionId.value)))
    }

    override fun read(sessionId: CompareConversationSessionId): CompareConversationSessionRead? =
        database.runInTransaction(Callable { readExisting(database.compareConversationDao(), sessionId.value) })

    override fun recordTerminal(intent: CompareBranchTerminalIntent, at: Instant): CompareSessionStoreResult<CompareConversationSessionRead> = transaction {
        val dao = database.compareConversationDao()
        val session = dao.session(intent.sessionId.value) ?: return@transaction rejected(CompareConversationSessionRejection.SESSION_UNKNOWN)
        val fingerprint = terminalFingerprint(intent)
        dao.terminalIntent(intent.id.value)?.let { existing ->
            val read = readExisting(dao, session) ?: return@transaction rejected(CompareConversationSessionRejection.SESSION_UNKNOWN)
            return@transaction if (existing.fingerprint == fingerprint) CompareSessionStoreResult.Replayed(read)
            else rejected(CompareConversationSessionRejection.BRANCH_TERMINAL_INTENT_CONFLICT)
        }
        val branch = dao.branches(session.sessionId).firstOrNull { it.branchId == intent.branchId.value }
            ?: return@transaction rejected(CompareConversationSessionRejection.BRANCH_UNKNOWN)
        if (intent.nextState == CompareBranchReservationState.CANCELLED && intent.cancellationId?.value != branch.cancellationId) {
            return@transaction rejected(CompareConversationSessionRejection.CANCELLATION_ID_MISMATCH)
        }
        val runtime = dao.runtimeState(session.sessionId, branch.branchId)
            ?: return@transaction rejected(CompareConversationSessionRejection.BRANCH_UNKNOWN)
        if (runtime.state != CompareBranchReservationState.RESERVED.name) {
            return@transaction rejected(CompareConversationSessionRejection.BRANCH_ALREADY_TERMINAL)
        }
        val error = when (intent.nextState) {
            CompareBranchReservationState.FAILED -> "COMPARE_BRANCH_FAILED"
            CompareBranchReservationState.CANCELLED -> "COMPARE_BRANCH_CANCELLED"
            CompareBranchReservationState.SUCCEEDED -> null
            CompareBranchReservationState.RESERVED -> error("terminal state required")
        }
        dao.updateRuntimeState(session.sessionId, branch.branchId, intent.nextState.name, runtime.nextExpectedSequence + 1,
            runtime.nextExpectedSequence, error, at.toEpochMilli())
        dao.insertRuntimeEvent(CompareBranchRuntimeEventEntity(
            eventId = "compare:${session.sessionId}:${branch.branchId}:terminal:${intent.id.value}", sessionId = session.sessionId,
            branchId = branch.branchId, invocationId = branch.invocationId, sequence = runtime.nextExpectedSequence,
            kind = intent.nextState.name, payloadFingerprint = sha256("$fingerprint|${runtime.nextExpectedSequence}"), emittedAtEpochMs = at.toEpochMilli(),
        ))
        dao.insertTerminalIntent(CompareBranchTerminalIntentEntity(intent.id.value, session.sessionId, branch.branchId, fingerprint,
            intent.nextState.name, intent.cancellationId?.value, at.toEpochMilli()))
        CompareSessionStoreResult.Stored(requireNotNull(readExisting(dao, session)))
    }

    /**
     * Schema 32 already has append-only per-branch runtime events.  Dispatch facts deliberately
     * reuse them: no text, receipt, Usage, Provider attempt, or new schema is introduced here.
     */
    override fun recordDispatchIntent(intent: CompareDispatchIntent, at: Instant): CompareSessionStoreResult<CompareConversationSessionRead> = transaction {
        val dao = database.compareConversationDao()
        val session = dao.session(intent.sessionId.value) ?: return@transaction rejected(CompareConversationSessionRejection.SESSION_UNKNOWN)
        if (!matchesDispatch(session, dao, intent)) return@transaction rejected(CompareConversationSessionRejection.DISPATCH_SCOPE_MISMATCH)
        val fingerprint = dispatchFingerprint(intent)
        val events = dao.runtimeEvents(session.sessionId)
        val existing = events.filter { it.kind == DISPATCH_INTENT_RECORDED }
        if (existing.isNotEmpty()) {
            val expected = dao.branches(session.sessionId).map { branch -> dispatchEventId(session.sessionId, branch.branchId, intent, "intent") }.toSet()
            return@transaction if (existing.map { it.eventId }.toSet() == expected && existing.all { it.payloadFingerprint == fingerprint }) {
                CompareSessionStoreResult.Replayed(requireNotNull(readExisting(dao, session)))
            } else rejected(CompareConversationSessionRejection.DISPATCH_INTENT_CONFLICT)
        }
        dao.branches(session.sessionId).forEach { branch ->
            dao.insertRuntimeEvent(CompareBranchRuntimeEventEntity(
                eventId = dispatchEventId(session.sessionId, branch.branchId, intent, "intent"), sessionId = session.sessionId,
                branchId = branch.branchId, invocationId = branch.invocationId, sequence = -2,
                kind = DISPATCH_INTENT_RECORDED, payloadFingerprint = fingerprint, emittedAtEpochMs = at.toEpochMilli(),
            ))
        }
        CompareSessionStoreResult.Stored(requireNotNull(readExisting(dao, session)))
    }

    override fun recordDispatchAccepted(intent: CompareDispatchIntent, at: Instant): CompareSessionStoreResult<CompareConversationSessionRead> =
        recordDispatchOutcome(intent, at, DISPATCH_ACCEPTED)

    override fun recordDispatchRejected(intent: CompareDispatchIntent, at: Instant): CompareSessionStoreResult<CompareConversationSessionRead> =
        recordDispatchOutcome(intent, at, DISPATCH_REJECTED_RETRY_REQUIRED)

    override fun storeFollowUp(intent: CompareBranchFollowUpIntent): CompareSessionStoreResult<CompareBranchFollowUpPlan> = transaction {
        val dao = database.compareConversationDao()
        val session = dao.session(intent.sessionId.value) ?: return@transaction rejected(CompareConversationSessionRejection.SESSION_UNKNOWN)
        val fingerprint = "${intent.sessionId.value}|${intent.branchId.value}|${intent.newCanonicalContext.id.value}|${intent.newCanonicalContext.contentHash}|${intent.newCanonicalContext.revision}"
        dao.followUpIntent(intent.id.value)?.let { existing ->
            val plan = existing.toFollowUpPlan(dao, session) ?: return@transaction rejected(CompareConversationSessionRejection.BRANCH_UNKNOWN)
            return@transaction if (existing.fingerprint == fingerprint) CompareSessionStoreResult.Replayed(plan)
            else rejected(CompareConversationSessionRejection.FOLLOW_UP_INTENT_CONFLICT)
        }
        val branch = persistedBranch(dao, session, intent.branchId) ?: return@transaction rejected(CompareConversationSessionRejection.BRANCH_UNKNOWN)
        if (branch.state != CompareBranchReservationState.SUCCEEDED) return@transaction rejected(CompareConversationSessionRejection.FOLLOW_UP_REQUIRES_SUCCESS)
        if (intent.newCanonicalContext.id.value == session.canonicalContextId) return@transaction rejected(CompareConversationSessionRejection.FOLLOW_UP_CONTEXT_NOT_NEW)
        dao.insertFollowUpIntent(CompareBranchFollowUpIntentEntity(intent.id.value, session.sessionId, branch.branchId.value, fingerprint,
            intent.newCanonicalContext.id.value, intent.newCanonicalContext.contentHash, intent.newCanonicalContext.revision))
        CompareSessionStoreResult.Stored(requireNotNull(dao.followUpIntent(intent.id.value)).toFollowUpPlan(dao, session)!!)
    }

    override fun storeAdoption(intent: CompareBranchAdoptionIntent): CompareSessionStoreResult<CompareBranchAdoptionPlan> = transaction {
        val dao = database.compareConversationDao()
        val session = dao.session(intent.sessionId.value) ?: return@transaction rejected(CompareConversationSessionRejection.SESSION_UNKNOWN)
        val fingerprint = "${intent.sessionId.value}|${intent.branchId.value}"
        dao.adoptionIntent(intent.id.value)?.let { existing ->
            val plan = existing.toAdoptionPlan(dao, session) ?: return@transaction rejected(CompareConversationSessionRejection.BRANCH_UNKNOWN)
            return@transaction if (existing.fingerprint == fingerprint) CompareSessionStoreResult.Replayed(plan)
            else rejected(CompareConversationSessionRejection.ADOPTION_INTENT_CONFLICT)
        }
        val branch = persistedBranch(dao, session, intent.branchId) ?: return@transaction rejected(CompareConversationSessionRejection.BRANCH_UNKNOWN)
        if (branch.state != CompareBranchReservationState.SUCCEEDED) return@transaction rejected(CompareConversationSessionRejection.ADOPTION_REQUIRES_SUCCESS)
        if (dao.anyAdoption(session.sessionId) != null) return@transaction rejected(CompareConversationSessionRejection.ADOPTION_ALREADY_PLANNED)
        dao.insertAdoptionIntent(CompareBranchAdoptionIntentEntity(intent.id.value, session.sessionId, branch.branchId.value, fingerprint))
        CompareSessionStoreResult.Stored(requireNotNull(dao.adoptionIntent(intent.id.value)).toAdoptionPlan(dao, session)!!)
    }

    override fun storeSynthesis(intent: CompareSynthesisIntent): CompareSessionStoreResult<CompareSynthesisPlan> = transaction {
        val dao = database.compareConversationDao()
        val session = dao.session(intent.sessionId.value) ?: return@transaction rejected(CompareConversationSessionRejection.SESSION_UNKNOWN)
        val fingerprint = listOf(intent.sessionId.value, intent.sourceBranchIds.joinToString { it.value }, intent.newCanonicalContext.id.value,
            intent.newCanonicalContext.contentHash, intent.freshConsentBudgetGate.id, intent.freshConsentBudgetGate.scopeFingerprint).joinToString("|")
        dao.synthesisIntent(intent.id.value)?.let { existing ->
            val plan = existing.toSynthesisPlan(dao, session) ?: return@transaction rejected(CompareConversationSessionRejection.SESSION_UNKNOWN)
            return@transaction if (existing.fingerprint == fingerprint) CompareSessionStoreResult.Replayed(plan)
            else rejected(CompareConversationSessionRejection.SYNTHESIS_INTENT_CONFLICT)
        }
        if (dao.anySynthesis(session.sessionId) != null) return@transaction rejected(CompareConversationSessionRejection.SYNTHESIS_ALREADY_PLANNED)
        if (!clock.instant().isBefore(intent.freshConsentBudgetGate.expiresAt)) return@transaction rejected(CompareConversationSessionRejection.SYNTHESIS_GATE_EXPIRED)
        val branches = dao.branches(session.sessionId).map { persistedBranch(dao, session, CompareBranchId(it.branchId))!! }
        if (intent.sourceBranchIds.toSet() != branches.map { it.branchId }.toSet() || branches.any { it.state != CompareBranchReservationState.SUCCEEDED }) {
            return@transaction rejected(CompareConversationSessionRejection.SYNTHESIS_REQUIRES_TWO_SUCCESSFUL_BRANCHES)
        }
        if (intent.newCanonicalContext.id.value == session.canonicalContextId) return@transaction rejected(CompareConversationSessionRejection.SYNTHESIS_CONTEXT_NOT_NEW)
        dao.insertSynthesisIntent(CompareSynthesisIntentEntity(intent.id.value, session.sessionId, fingerprint, intent.sourceBranchIds.joinToString(",") { it.value },
            intent.newCanonicalContext.id.value, intent.newCanonicalContext.contentHash, intent.newCanonicalContext.revision,
            intent.freshConsentBudgetGate.id, intent.freshConsentBudgetGate.scopeFingerprint, intent.freshConsentBudgetGate.currencyCode,
            intent.freshConsentBudgetGate.maximumBudgetMicros, intent.freshConsentBudgetGate.expiresAt.toEpochMilli()))
        CompareSessionStoreResult.Stored(requireNotNull(dao.synthesisIntent(intent.id.value)).toSynthesisPlan(dao, session)!!)
    }

    private fun readExisting(dao: CompareConversationDao, sessionId: String): CompareConversationSessionRead? =
        dao.session(sessionId)?.let { readExisting(dao, it) }

    private fun readExisting(dao: CompareConversationDao, session: CompareConversationSessionEntity): CompareConversationSessionRead? {
        val branches = dao.branches(session.sessionId)
        val runtime = dao.runtimeStates(session.sessionId)
        if (branches.size != 2 || runtime.size != 2 || branches.map { it.branchId }.toSet() != runtime.map { it.branchId }.toSet()) return null
        val plan = session.toPlan(branches, runtime)
        return CompareConversationSessionRead(plan, runtime.map { state ->
            val branch = branches.first { it.branchId == state.branchId }
            CompareBranchRuntimeReference(CompareBranchId(branch.branchId), ConversationRealTextExecutionId(branch.executionId),
                branch.usageReservationReplayToken, state.nextExpectedSequence, state.lastCheckpointSequence,
                CompareBranchReservationState.valueOf(state.state), state.safeErrorCode, Instant.ofEpochMilli(state.updatedAtEpochMs))
        }, dispatchRecoveryState(dao.runtimeEvents(session.sessionId), branches))
    }

    private fun recordDispatchOutcome(intent: CompareDispatchIntent, at: Instant, kind: String): CompareSessionStoreResult<CompareConversationSessionRead> = transaction {
        val dao = database.compareConversationDao()
        val session = dao.session(intent.sessionId.value) ?: return@transaction rejected(CompareConversationSessionRejection.SESSION_UNKNOWN)
        if (!matchesDispatch(session, dao, intent)) return@transaction rejected(CompareConversationSessionRejection.DISPATCH_SCOPE_MISMATCH)
        val fingerprint = dispatchFingerprint(intent)
        val events = dao.runtimeEvents(session.sessionId)
        val branches = dao.branches(session.sessionId)
        val expectedIntentEvents = branches.map { dispatchEventId(session.sessionId, it.branchId, intent, "intent") }.toSet()
        val recordedIntentEvents = events.filter { it.kind == DISPATCH_INTENT_RECORDED }
        if (recordedIntentEvents.map { it.eventId }.toSet() != expectedIntentEvents || recordedIntentEvents.any { it.payloadFingerprint != fingerprint }) {
            return@transaction rejected(CompareConversationSessionRejection.DISPATCH_INTENT_NOT_RECORDED)
        }
        val terminalEvents = events.filter { it.kind == DISPATCH_ACCEPTED || it.kind == DISPATCH_REJECTED_RETRY_REQUIRED }
        if (terminalEvents.isNotEmpty()) {
            val expected = branches.map { dispatchEventId(session.sessionId, it.branchId, intent, if (kind == DISPATCH_ACCEPTED) "accepted" else "rejected") }.toSet()
            return@transaction if (terminalEvents.map { it.eventId }.toSet() == expected && terminalEvents.all { it.kind == kind && it.payloadFingerprint == fingerprint }) {
                CompareSessionStoreResult.Replayed(requireNotNull(readExisting(dao, session)))
            } else if (terminalEvents.all { it.kind == DISPATCH_ACCEPTED }) {
                rejected(CompareConversationSessionRejection.DISPATCH_ALREADY_ACCEPTED)
            } else rejected(CompareConversationSessionRejection.DISPATCH_INTENT_CONFLICT)
        }
        branches.forEach { branch ->
            dao.insertRuntimeEvent(CompareBranchRuntimeEventEntity(
                eventId = dispatchEventId(session.sessionId, branch.branchId, intent, if (kind == DISPATCH_ACCEPTED) "accepted" else "rejected"), sessionId = session.sessionId,
                branchId = branch.branchId, invocationId = branch.invocationId, sequence = -1,
                kind = kind, payloadFingerprint = fingerprint, emittedAtEpochMs = at.toEpochMilli(),
            ))
        }
        CompareSessionStoreResult.Stored(requireNotNull(readExisting(dao, session)))
    }

    private fun matchesDispatch(session: CompareConversationSessionEntity, dao: CompareConversationDao, intent: CompareDispatchIntent): Boolean {
        val branches = dao.branches(session.sessionId)
        return session.confirmationId == intent.confirmationId && session.requestFingerprint == intent.requestFingerprint &&
            session.canonicalContextId == intent.context.id.value && session.canonicalContextHash == intent.context.contentHash &&
            session.canonicalContextRevision == intent.context.revision && branches.size == 2 &&
            branches.map { it.grantId }.toSet() == intent.branchGrantIds.toSet()
    }

    private fun dispatchRecoveryState(events: List<CompareBranchRuntimeEventEntity>, branches: List<CompareConversationBranchEntity>): CompareDispatchRecoveryState {
        val branchIds = branches.map { it.branchId }.toSet()
        fun isComplete(kind: String) = events.filter { it.kind == kind }.map { it.branchId }.toSet() == branchIds
        return when {
            isComplete(DISPATCH_ACCEPTED) -> CompareDispatchRecoveryState.ACCEPTED
            isComplete(DISPATCH_REJECTED_RETRY_REQUIRED) -> CompareDispatchRecoveryState.REJECTED_RETRY_REQUIRED
            isComplete(DISPATCH_INTENT_RECORDED) -> CompareDispatchRecoveryState.PENDING_ACCEPTANCE_RETRY_REQUIRED
            else -> CompareDispatchRecoveryState.RETRY_REQUIRED
        }
    }

    private fun dispatchFingerprint(intent: CompareDispatchIntent): String = sha256(listOf(
        intent.id.value, intent.sessionId.value, intent.confirmationId, intent.requestFingerprint,
        intent.context.id.value, intent.context.contentHash, intent.context.revision.toString(),
        intent.branchGrantIds.sorted().joinToString(","),
    ).joinToString("|"))

    private fun dispatchEventId(sessionId: String, branchId: String, intent: CompareDispatchIntent, phase: String): String =
        "compare:$sessionId:$branchId:dispatch:${intent.id.value}:$phase"

    private data class PersistedBranch(val entity: CompareConversationBranchEntity, val state: CompareBranchReservationState) {
        val branchId get() = CompareBranchId(entity.branchId)
    }
    private fun persistedBranch(dao: CompareConversationDao, session: CompareConversationSessionEntity, id: CompareBranchId): PersistedBranch? {
        val entity = dao.branches(session.sessionId).firstOrNull { it.branchId == id.value } ?: return null
        val runtime = dao.runtimeState(session.sessionId, id.value) ?: return null
        return PersistedBranch(entity, CompareBranchReservationState.valueOf(runtime.state))
    }

    private fun CompareConversationSessionEntity.toPlan(branches: List<CompareConversationBranchEntity>, runtime: List<CompareBranchRuntimeStateEntity>) = CompareConversationSessionPlan(
        CompareConversationSessionId(sessionId), com.nanzhufeng.ai.domain.CompareConversationSessionIntentId(intentId), confirmationId, requestFingerprint,
        ConversationId(conversationId), MessageNodeId(parentUserMessageId), CanonicalContextSnapshotRef(CanonicalContextSnapshotId(canonicalContextId), canonicalContextHash, canonicalContextRevision),
        MessageNodeId(sharedCurrentLeafAtPlanning), branches.map { branch -> branch.toPlan(runtime.first { it.branchId == branch.branchId }) },
        CompareSynthesisPolicy.valueOf(synthesisPolicy), CompareSharedContextPolicy.valueOf(sharedContextPolicy), Instant.ofEpochMilli(createdAtEpochMs),
    )

    private fun CompareConversationBranchEntity.toPlan(runtime: CompareBranchRuntimeStateEntity) = CompareConversationBranchPlan(
        CompareBranchId(branchId), CompareBranchExecutionGrant(grantId, CompareBranchId(branchId), requestFingerprint, contextHash, textSha256,
            LogicalModelId(logicalModelId), ModelDeploymentId(deploymentId), ProviderHandle(providerHandle), providerModelId, catalogVersion,
            priceVersion, currencyCode, maximumBudgetMicros, confirmationScopeFingerprint, Instant.ofEpochMilli(grantExpiresAtEpochMs)),
        MessageNodeId(assistantMessageId), InvocationId(invocationId), ProviderAttemptId(attemptId), CompareBranchCancellationId(cancellationId),
        CompareBranchReservationState.valueOf(runtime.state),
    )

    private fun CompareConversationSessionPlan.toEntity() = CompareConversationSessionEntity(sessionId.value, intentId.value, requestFingerprint,
        confirmationId, conversationId.value, parentUserMessageId.value, canonicalContext.id.value, canonicalContext.contentHash,
        canonicalContext.revision, sharedCurrentLeafAtPlanning.value, synthesisPolicy.name, sharedContextPolicy.name, createdAt.toEpochMilli())
    private fun CompareConversationBranchPlan.toEntity(sessionId: CompareConversationSessionId) = CompareConversationBranchEntity(sessionId.value, branchId.value,
        grant.grantId, grant.requestFingerprint, grant.contextHash, grant.textSha256, grant.logicalModelId.value, grant.deploymentId.value,
        grant.provider.value, grant.providerModelId, grant.catalogVersion, grant.priceVersion, grant.currencyCode, grant.maximumBudgetMicros,
        grant.confirmationScopeFingerprint, grant.expiresAt.toEpochMilli(), assistantMessageId.value, invocationId.value, attemptId.value,
        "compare:${sessionId.value}:${branchId.value}:execution", cancellationId.value, "compare:${sessionId.value}:${branchId.value}:reservation")
    private fun terminalFingerprint(intent: CompareBranchTerminalIntent) = listOf(intent.sessionId.value, intent.branchId.value, intent.nextState.name, intent.cancellationId?.value).joinToString("|")
    private fun CompareBranchFollowUpIntentEntity.toFollowUpPlan(dao: CompareConversationDao, session: CompareConversationSessionEntity): CompareBranchFollowUpPlan? {
        val branch = dao.branches(session.sessionId).firstOrNull { it.branchId == branchId } ?: return null
        return CompareBranchFollowUpPlan(com.nanzhufeng.ai.domain.CompareBranchFollowUpIntentId(intentId), CompareConversationSessionId(sessionId), ConversationId(session.conversationId),
            CompareBranchId(branchId), MessageNodeId(branch.assistantMessageId), dao.branches(session.sessionId).filter { it.branchId != branchId }.map { MessageNodeId(it.assistantMessageId) }.toSet(),
            CanonicalContextSnapshotRef(CanonicalContextSnapshotId(canonicalContextId), canonicalContextHash, canonicalContextRevision))
    }
    private fun CompareBranchAdoptionIntentEntity.toAdoptionPlan(dao: CompareConversationDao, session: CompareConversationSessionEntity): CompareBranchAdoptionPlan? {
        val branch = dao.branches(session.sessionId).firstOrNull { it.branchId == branchId } ?: return null
        return CompareBranchAdoptionPlan(com.nanzhufeng.ai.domain.CompareBranchAdoptionIntentId(intentId), CompareConversationSessionId(sessionId), ConversationId(session.conversationId),
            CompareBranchId(branchId), MessageNodeId(branch.assistantMessageId), dao.branches(session.sessionId).filter { it.branchId != branchId }.map { MessageNodeId(it.assistantMessageId) }.toSet())
    }
    private fun CompareSynthesisIntentEntity.toSynthesisPlan(dao: CompareConversationDao, session: CompareConversationSessionEntity): CompareSynthesisPlan? = runCatching {
        val sources = sourceBranchIds.split(',').filter(String::isNotBlank).map(::CompareBranchId)
        val branches = dao.branches(session.sessionId)
        val stem = "compare:${session.intentId}:synthesis:$intentId"
        CompareSynthesisPlan(com.nanzhufeng.ai.domain.CompareSynthesisIntentId(intentId), CompareConversationSessionId(sessionId), ConversationId(session.conversationId),
            MessageNodeId(session.parentUserMessageId), sources, sources.map { source -> MessageNodeId(branches.first { it.branchId == source.value }.assistantMessageId) }, CanonicalContextSnapshotRef(CanonicalContextSnapshotId(canonicalContextId), canonicalContextHash, canonicalContextRevision),
            MessageNodeId("$stem:assistant"), InvocationId("$stem:invocation"), ProviderAttemptId("$stem:attempt"),
            CompareSynthesisConsentBudgetGate(gateId, gateScopeFingerprint, gateCurrencyCode, gateMaximumBudgetMicros, Instant.ofEpochMilli(gateExpiresAtEpochMs)))
    }.getOrNull()
    private fun sha256(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    private companion object {
        const val DISPATCH_INTENT_RECORDED = "DISPATCH_INTENT_RECORDED"
        const val DISPATCH_ACCEPTED = "DISPATCH_ACCEPTED"
        const val DISPATCH_REJECTED_RETRY_REQUIRED = "DISPATCH_REJECTED_RETRY_REQUIRED"
    }
    private fun rejected(reason: CompareConversationSessionRejection) = CompareSessionStoreResult.Rejected(reason)
    private fun <T> transaction(block: () -> CompareSessionStoreResult<T>): CompareSessionStoreResult<T> =
        runCatching { database.runInTransaction(Callable { block() }) }.getOrElse { rejected(CompareConversationSessionRejection.RESERVED_ID_COLLISION) }
}

package com.nanzhufeng.ai.domain

import java.security.MessageDigest
import java.time.Clock
import java.time.Instant

/** MM-O4-B pure IR. It references the existing Conversation tree; it never stores a second tree. */
@JvmInline
value class CompareConversationSessionId(val value: String) { init { require(value.isNotBlank()) } }

@JvmInline
value class CompareConversationSessionIntentId(val value: String) { init { require(value.isNotBlank()) } }

@JvmInline
value class CompareBranchTerminalIntentId(val value: String) { init { require(value.isNotBlank()) } }

@JvmInline
value class CompareBranchFollowUpIntentId(val value: String) { init { require(value.isNotBlank()) } }

@JvmInline
value class CompareBranchAdoptionIntentId(val value: String) { init { require(value.isNotBlank()) } }

@JvmInline
value class CompareSynthesisIntentId(val value: String) { init { require(value.isNotBlank()) } }

@JvmInline
value class CompareBranchCancellationId(val value: String) { init { require(value.isNotBlank()) } }

enum class CompareBranchReservationState { RESERVED, SUCCEEDED, FAILED, CANCELLED }

/** Content-free instruction to attach two future assistant siblings to one existing user parent. */
data class CompareConversationSessionIntent(
    val id: CompareConversationSessionIntentId,
    val grantedPlan: CompareExecutionGrantedPlan,
    val conversationId: ConversationId,
    val parentUserMessageId: MessageNodeId,
    val canonicalContext: CanonicalContextSnapshotRef,
) {
    init {
        require(grantedPlan.contextHash == canonicalContext.contentHash) { "Compare grant 与 Canonical Context 不一致。" }
    }
}

data class CompareConversationBranchPlan(
    val branchId: CompareBranchId,
    val grant: CompareBranchExecutionGrant,
    val assistantMessageId: MessageNodeId,
    val invocationId: InvocationId,
    val attemptId: ProviderAttemptId,
    val cancellationId: CompareBranchCancellationId,
    val state: CompareBranchReservationState = CompareBranchReservationState.RESERVED,
) {
    init {
        require(branchId == grant.branchId)
        require(listOf(assistantMessageId.value, invocationId.value, attemptId.value, cancellationId.value).distinct().size == 4)
    }
}

/** A plan only: it leaves Conversation.currentLeafMessageId and every existing MessageNode unchanged. */
data class CompareConversationSessionPlan(
    val sessionId: CompareConversationSessionId,
    val intentId: CompareConversationSessionIntentId,
    /** One-shot MM-O4-A confirmation identity; persisted to prevent a second session consuming it. */
    val confirmationId: String,
    val requestFingerprint: String,
    val conversationId: ConversationId,
    val parentUserMessageId: MessageNodeId,
    val canonicalContext: CanonicalContextSnapshotRef,
    val sharedCurrentLeafAtPlanning: MessageNodeId,
    val branches: List<CompareConversationBranchPlan>,
    val synthesisPolicy: CompareSynthesisPolicy = CompareSynthesisPolicy.NOT_REQUESTED,
    val sharedContextPolicy: CompareSharedContextPolicy = CompareSharedContextPolicy.EXPLICIT_ADOPTION_ONLY,
    val createdAt: Instant,
) {
    init {
        require(confirmationId.isNotBlank())
        require(requestFingerprint.matches(Regex("[0-9a-f]{64}")))
        require(parentUserMessageId == sharedCurrentLeafAtPlanning)
        require(branches.size == CompareExecutionSummaryConfirmation.COMPARE_MVP_TARGET_COUNT)
        require(branches.map(CompareConversationBranchPlan::branchId).toSet().size == branches.size)
        require(branches.map { it.grant.logicalModelId }.toSet().size == branches.size)
        require(branches.map { it.grant.deploymentId }.toSet().size == branches.size)
        require(branches.all {
            it.grant.requestFingerprint == requestFingerprint &&
                it.grant.contextHash == canonicalContext.contentHash
        })
    }
}

enum class CompareConversationSessionRejection {
    SESSION_INTENT_CONFLICT,
    GRANT_ALREADY_CONSUMED,
    GRANT_EXPIRED,
    GRANT_INVALID,
    DISPATCH_SCOPE_MISMATCH,
    DISPATCH_INTENT_CONFLICT,
    DISPATCH_INTENT_NOT_RECORDED,
    DISPATCH_ALREADY_ACCEPTED,
    CONVERSATION_MISMATCH,
    TREE_INVALID,
    PARENT_NOT_CURRENT_USER_LEAF,
    RESERVED_ID_COLLISION,
    SESSION_UNKNOWN,
    BRANCH_UNKNOWN,
    BRANCH_TERMINAL_INTENT_CONFLICT,
    BRANCH_ALREADY_TERMINAL,
    CANCELLATION_ID_MISMATCH,
    FOLLOW_UP_INTENT_CONFLICT,
    FOLLOW_UP_REQUIRES_SUCCESS,
    FOLLOW_UP_CONTEXT_NOT_NEW,
    ADOPTION_INTENT_CONFLICT,
    ADOPTION_REQUIRES_SUCCESS,
    ADOPTION_ALREADY_PLANNED,
    SYNTHESIS_INTENT_CONFLICT,
    SYNTHESIS_REQUIRES_TWO_SUCCESSFUL_BRANCHES,
    SYNTHESIS_CONTEXT_NOT_NEW,
    SYNTHESIS_GATE_EXPIRED,
    SYNTHESIS_ALREADY_PLANNED,
}

sealed interface CompareConversationSessionResult {
    data class Planned(val plan: CompareConversationSessionPlan) : CompareConversationSessionResult
    data class Replayed(val plan: CompareConversationSessionPlan) : CompareConversationSessionResult
    data class Rejected(val reason: CompareConversationSessionRejection) : CompareConversationSessionResult
}

data class CompareBranchTerminalIntent(
    val id: CompareBranchTerminalIntentId,
    val sessionId: CompareConversationSessionId,
    val branchId: CompareBranchId,
    val nextState: CompareBranchReservationState,
    val cancellationId: CompareBranchCancellationId? = null,
) {
    init {
        require(nextState != CompareBranchReservationState.RESERVED)
        require((nextState == CompareBranchReservationState.CANCELLED) == (cancellationId != null))
    }
}

sealed interface CompareBranchTerminalResult {
    data class Updated(val plan: CompareConversationSessionPlan) : CompareBranchTerminalResult
    data class Replayed(val plan: CompareConversationSessionPlan) : CompareBranchTerminalResult
    data class Rejected(val reason: CompareConversationSessionRejection) : CompareBranchTerminalResult
}

/** The future context builder must use this branch tail and explicitly exclude every sibling answer. */
data class CompareBranchFollowUpIntent(
    val id: CompareBranchFollowUpIntentId,
    val sessionId: CompareConversationSessionId,
    val branchId: CompareBranchId,
    val newCanonicalContext: CanonicalContextSnapshotRef,
)

data class CompareBranchFollowUpPlan(
    val id: CompareBranchFollowUpIntentId,
    val sessionId: CompareConversationSessionId,
    val conversationId: ConversationId,
    val branchId: CompareBranchId,
    val branchTailAssistantMessageId: MessageNodeId,
    val excludedSiblingAssistantMessageIds: Set<MessageNodeId>,
    val canonicalContext: CanonicalContextSnapshotRef,
)

sealed interface CompareBranchFollowUpResult {
    data class Planned(val plan: CompareBranchFollowUpPlan) : CompareBranchFollowUpResult
    data class Replayed(val plan: CompareBranchFollowUpPlan) : CompareBranchFollowUpResult
    data class Rejected(val reason: CompareConversationSessionRejection) : CompareBranchFollowUpResult
}

data class CompareBranchAdoptionIntent(
    val id: CompareBranchAdoptionIntentId,
    val sessionId: CompareConversationSessionId,
    val branchId: CompareBranchId,
)

/** This is an auditable future mutation intent, not a mutation of Conversation.currentLeafMessageId. */
data class CompareBranchAdoptionPlan(
    val id: CompareBranchAdoptionIntentId,
    val sessionId: CompareConversationSessionId,
    val conversationId: ConversationId,
    val adoptedBranchId: CompareBranchId,
    val adoptedAssistantMessageId: MessageNodeId,
    val preservedSiblingAssistantMessageIds: Set<MessageNodeId>,
    val requiresExplicitUserAdoption: Boolean = true,
)

sealed interface CompareBranchAdoptionResult {
    data class Planned(val plan: CompareBranchAdoptionPlan) : CompareBranchAdoptionResult
    data class Replayed(val plan: CompareBranchAdoptionPlan) : CompareBranchAdoptionResult
    data class Rejected(val reason: CompareConversationSessionRejection) : CompareBranchAdoptionResult
}

/** A synthesis is a new invocation with its own consent/budget gate, never a concatenated Compare answer. */
data class CompareSynthesisConsentBudgetGate(
    val id: String,
    val scopeFingerprint: String,
    val currencyCode: String,
    val maximumBudgetMicros: Long,
    val expiresAt: Instant,
    val acknowledged: Boolean = false,
) {
    init {
        require(id.isNotBlank())
        require(scopeFingerprint.matches(Regex("[0-9a-f]{64}")))
        require(currencyCode.matches(Regex("[A-Z]{3}")))
        require(maximumBudgetMicros >= 0)
        require(!acknowledged) { "MM-O4-B 只能生成新的未确认 synthesis gate。" }
    }
}

data class CompareSynthesisIntent(
    val id: CompareSynthesisIntentId,
    val sessionId: CompareConversationSessionId,
    val sourceBranchIds: List<CompareBranchId>,
    val newCanonicalContext: CanonicalContextSnapshotRef,
    val freshConsentBudgetGate: CompareSynthesisConsentBudgetGate,
) {
    init { require(sourceBranchIds.size == CompareExecutionSummaryConfirmation.COMPARE_MVP_TARGET_COUNT) }
}

data class CompareSynthesisPlan(
    val id: CompareSynthesisIntentId,
    val sessionId: CompareConversationSessionId,
    val conversationId: ConversationId,
    val parentUserMessageId: MessageNodeId,
    val sourceBranchIds: List<CompareBranchId>,
    val sourceAssistantMessageIds: List<MessageNodeId>,
    val canonicalContext: CanonicalContextSnapshotRef,
    val assistantMessageId: MessageNodeId,
    val invocationId: InvocationId,
    val attemptId: ProviderAttemptId,
    val freshConsentBudgetGate: CompareSynthesisConsentBudgetGate,
    val isIndependentInvocation: Boolean = true,
    val mayNotBeTreatedAsOriginalCompareResponse: Boolean = true,
)

sealed interface CompareSynthesisResult {
    data class Planned(val plan: CompareSynthesisPlan) : CompareSynthesisResult
    data class Replayed(val plan: CompareSynthesisPlan) : CompareSynthesisResult
    data class Rejected(val reason: CompareConversationSessionRejection) : CompareSynthesisResult
}

/**
 * The unique MM-O4-B Compare Session/Branch owner. All state is process-local IR for contract
 * planning only. It never calls ConversationTreeService mutation, repositories, Runtime, Usage,
 * Direct, transport, credential, UI, or persistence adapters.
 */
class CompareConversationSessionOwner(private val clock: Clock) {
    private data class StoredSession(
        var plan: CompareConversationSessionPlan,
        val fingerprint: String,
        val terminalIntentFingerprints: MutableMap<CompareBranchTerminalIntentId, String> = linkedMapOf(),
        val followUps: MutableMap<CompareBranchFollowUpIntentId, Pair<String, CompareBranchFollowUpPlan>> = linkedMapOf(),
        val adoptions: MutableMap<CompareBranchAdoptionIntentId, Pair<String, CompareBranchAdoptionPlan>> = linkedMapOf(),
        val syntheses: MutableMap<CompareSynthesisIntentId, Pair<String, CompareSynthesisPlan>> = linkedMapOf(),
        var adoptedBranchId: CompareBranchId? = null,
    )

    private val sessionsByIntent = linkedMapOf<CompareConversationSessionIntentId, StoredSession>()
    private val sessionsById = linkedMapOf<CompareConversationSessionId, StoredSession>()
    private val sessionIdByConfirmation = linkedMapOf<String, CompareConversationSessionId>()

    @Synchronized
    fun plan(intent: CompareConversationSessionIntent, snapshot: ConversationSnapshot): CompareConversationSessionResult {
        val fingerprint = fingerprint(intent)
        sessionsByIntent[intent.id]?.let { existing ->
            return if (existing.fingerprint == fingerprint) CompareConversationSessionResult.Replayed(existing.plan)
            else CompareConversationSessionResult.Rejected(CompareConversationSessionRejection.SESSION_INTENT_CONFLICT)
        }
        if (sessionIdByConfirmation.containsKey(intent.grantedPlan.confirmationId)) {
            return CompareConversationSessionResult.Rejected(CompareConversationSessionRejection.GRANT_ALREADY_CONSUMED)
        }
        if (!clock.instant().isBefore(intent.grantedPlan.expiresAt)) {
            return CompareConversationSessionResult.Rejected(CompareConversationSessionRejection.GRANT_EXPIRED)
        }
        if (!validGrants(intent.grantedPlan, intent.canonicalContext)) {
            return CompareConversationSessionResult.Rejected(CompareConversationSessionRejection.GRANT_INVALID)
        }
        if (snapshot.conversation.id != intent.conversationId) {
            return CompareConversationSessionResult.Rejected(CompareConversationSessionRejection.CONVERSATION_MISMATCH)
        }
        val tree = runCatching { MessageTree(snapshot.conversation, snapshot.nodes) }.getOrElse {
            return CompareConversationSessionResult.Rejected(CompareConversationSessionRejection.TREE_INVALID)
        }
        if (snapshot.conversation.currentLeafMessageId != intent.parentUserMessageId ||
            tree.node(intent.parentUserMessageId).role != MessageRole.USER
        ) return CompareConversationSessionResult.Rejected(CompareConversationSessionRejection.PARENT_NOT_CURRENT_USER_LEAF)

        val sessionId = CompareConversationSessionId("compare-session:${intent.id.value}")
        val branches = intent.grantedPlan.branchGrants.mapIndexed { index, grant ->
            val stem = "compare:${intent.id.value}:branch:${index + 1}"
            CompareConversationBranchPlan(
                branchId = grant.branchId,
                grant = grant,
                assistantMessageId = MessageNodeId("$stem:assistant"),
                invocationId = InvocationId("$stem:invocation"),
                attemptId = ProviderAttemptId("$stem:attempt"),
                cancellationId = CompareBranchCancellationId("$stem:cancel"),
            )
        }
        if (branches.map(CompareConversationBranchPlan::assistantMessageId).any { planned -> snapshot.nodes.any { it.id == planned } }) {
            return CompareConversationSessionResult.Rejected(CompareConversationSessionRejection.RESERVED_ID_COLLISION)
        }
        val planned = CompareConversationSessionPlan(
            sessionId = sessionId,
            intentId = intent.id,
            confirmationId = intent.grantedPlan.confirmationId,
            requestFingerprint = intent.grantedPlan.requestFingerprint,
            conversationId = intent.conversationId,
            parentUserMessageId = intent.parentUserMessageId,
            canonicalContext = intent.canonicalContext,
            sharedCurrentLeafAtPlanning = snapshot.conversation.currentLeafMessageId!!,
            branches = branches,
            createdAt = clock.instant(),
        )
        val stored = StoredSession(planned, fingerprint)
        sessionsByIntent[intent.id] = stored
        sessionsById[sessionId] = stored
        sessionIdByConfirmation[intent.grantedPlan.confirmationId] = sessionId
        return CompareConversationSessionResult.Planned(planned)
    }

    @Synchronized
    fun recordTerminal(intent: CompareBranchTerminalIntent): CompareBranchTerminalResult {
        val stored = sessionsById[intent.sessionId]
            ?: return CompareBranchTerminalResult.Rejected(CompareConversationSessionRejection.SESSION_UNKNOWN)
        val fingerprint = listOf(intent.sessionId.value, intent.branchId.value, intent.nextState.name, intent.cancellationId?.value).joinToString("|")
        stored.terminalIntentFingerprints[intent.id]?.let { previous ->
            return if (previous == fingerprint) CompareBranchTerminalResult.Replayed(stored.plan)
            else CompareBranchTerminalResult.Rejected(CompareConversationSessionRejection.BRANCH_TERMINAL_INTENT_CONFLICT)
        }
        val branch = stored.plan.branches.firstOrNull { it.branchId == intent.branchId }
            ?: return CompareBranchTerminalResult.Rejected(CompareConversationSessionRejection.BRANCH_UNKNOWN)
        if (intent.nextState == CompareBranchReservationState.CANCELLED && intent.cancellationId != branch.cancellationId) {
            return CompareBranchTerminalResult.Rejected(CompareConversationSessionRejection.CANCELLATION_ID_MISMATCH)
        }
        if (branch.state != CompareBranchReservationState.RESERVED) {
            return CompareBranchTerminalResult.Rejected(CompareConversationSessionRejection.BRANCH_ALREADY_TERMINAL)
        }
        stored.plan = stored.plan.copy(branches = stored.plan.branches.map {
            if (it.branchId == branch.branchId) it.copy(state = intent.nextState) else it
        })
        stored.terminalIntentFingerprints[intent.id] = fingerprint
        return CompareBranchTerminalResult.Updated(stored.plan)
    }

    @Synchronized
    fun planFollowUp(intent: CompareBranchFollowUpIntent): CompareBranchFollowUpResult {
        val stored = sessionsById[intent.sessionId]
            ?: return CompareBranchFollowUpResult.Rejected(CompareConversationSessionRejection.SESSION_UNKNOWN)
        val fingerprint = listOf(intent.sessionId.value, intent.branchId.value, intent.newCanonicalContext.id.value, intent.newCanonicalContext.contentHash, intent.newCanonicalContext.revision).joinToString("|")
        stored.followUps[intent.id]?.let { (previous, plan) ->
            return if (previous == fingerprint) CompareBranchFollowUpResult.Replayed(plan)
            else CompareBranchFollowUpResult.Rejected(CompareConversationSessionRejection.FOLLOW_UP_INTENT_CONFLICT)
        }
        val branch = stored.plan.branches.firstOrNull { it.branchId == intent.branchId }
            ?: return CompareBranchFollowUpResult.Rejected(CompareConversationSessionRejection.BRANCH_UNKNOWN)
        if (branch.state != CompareBranchReservationState.SUCCEEDED) {
            return CompareBranchFollowUpResult.Rejected(CompareConversationSessionRejection.FOLLOW_UP_REQUIRES_SUCCESS)
        }
        if (intent.newCanonicalContext.id == stored.plan.canonicalContext.id) {
            return CompareBranchFollowUpResult.Rejected(CompareConversationSessionRejection.FOLLOW_UP_CONTEXT_NOT_NEW)
        }
        val plan = CompareBranchFollowUpPlan(
            id = intent.id,
            sessionId = intent.sessionId,
            conversationId = stored.plan.conversationId,
            branchId = branch.branchId,
            branchTailAssistantMessageId = branch.assistantMessageId,
            excludedSiblingAssistantMessageIds = stored.plan.branches.filter { it.branchId != branch.branchId }.map { it.assistantMessageId }.toSet(),
            canonicalContext = intent.newCanonicalContext,
        )
        stored.followUps[intent.id] = fingerprint to plan
        return CompareBranchFollowUpResult.Planned(plan)
    }

    @Synchronized
    fun planAdoption(intent: CompareBranchAdoptionIntent): CompareBranchAdoptionResult {
        val stored = sessionsById[intent.sessionId]
            ?: return CompareBranchAdoptionResult.Rejected(CompareConversationSessionRejection.SESSION_UNKNOWN)
        val fingerprint = "${intent.sessionId.value}|${intent.branchId.value}"
        stored.adoptions[intent.id]?.let { (previous, plan) ->
            return if (previous == fingerprint) CompareBranchAdoptionResult.Replayed(plan)
            else CompareBranchAdoptionResult.Rejected(CompareConversationSessionRejection.ADOPTION_INTENT_CONFLICT)
        }
        val branch = stored.plan.branches.firstOrNull { it.branchId == intent.branchId }
            ?: return CompareBranchAdoptionResult.Rejected(CompareConversationSessionRejection.BRANCH_UNKNOWN)
        if (branch.state != CompareBranchReservationState.SUCCEEDED) {
            return CompareBranchAdoptionResult.Rejected(CompareConversationSessionRejection.ADOPTION_REQUIRES_SUCCESS)
        }
        if (stored.adoptedBranchId != null) {
            return CompareBranchAdoptionResult.Rejected(CompareConversationSessionRejection.ADOPTION_ALREADY_PLANNED)
        }
        val plan = CompareBranchAdoptionPlan(
            id = intent.id,
            sessionId = intent.sessionId,
            conversationId = stored.plan.conversationId,
            adoptedBranchId = branch.branchId,
            adoptedAssistantMessageId = branch.assistantMessageId,
            preservedSiblingAssistantMessageIds = stored.plan.branches.filter { it.branchId != branch.branchId }.map { it.assistantMessageId }.toSet(),
        )
        stored.adoptedBranchId = branch.branchId
        stored.adoptions[intent.id] = fingerprint to plan
        return CompareBranchAdoptionResult.Planned(plan)
    }

    @Synchronized
    fun planSynthesis(intent: CompareSynthesisIntent): CompareSynthesisResult {
        val stored = sessionsById[intent.sessionId]
            ?: return CompareSynthesisResult.Rejected(CompareConversationSessionRejection.SESSION_UNKNOWN)
        val fingerprint = listOf(intent.sessionId.value, intent.sourceBranchIds.joinToString { it.value }, intent.newCanonicalContext.id.value, intent.newCanonicalContext.contentHash, intent.freshConsentBudgetGate.id, intent.freshConsentBudgetGate.scopeFingerprint).joinToString("|")
        stored.syntheses[intent.id]?.let { (previous, plan) ->
            return if (previous == fingerprint) CompareSynthesisResult.Replayed(plan)
            else CompareSynthesisResult.Rejected(CompareConversationSessionRejection.SYNTHESIS_INTENT_CONFLICT)
        }
        if (stored.syntheses.isNotEmpty()) return CompareSynthesisResult.Rejected(CompareConversationSessionRejection.SYNTHESIS_ALREADY_PLANNED)
        if (!clock.instant().isBefore(intent.freshConsentBudgetGate.expiresAt)) {
            return CompareSynthesisResult.Rejected(CompareConversationSessionRejection.SYNTHESIS_GATE_EXPIRED)
        }
        if (intent.sourceBranchIds.toSet().size != CompareExecutionSummaryConfirmation.COMPARE_MVP_TARGET_COUNT ||
            intent.sourceBranchIds.toSet() != stored.plan.branches.map(CompareConversationBranchPlan::branchId).toSet() ||
            stored.plan.branches.any { it.state != CompareBranchReservationState.SUCCEEDED }
        ) return CompareSynthesisResult.Rejected(CompareConversationSessionRejection.SYNTHESIS_REQUIRES_TWO_SUCCESSFUL_BRANCHES)
        if (intent.newCanonicalContext.id == stored.plan.canonicalContext.id) {
            return CompareSynthesisResult.Rejected(CompareConversationSessionRejection.SYNTHESIS_CONTEXT_NOT_NEW)
        }
        val stem = "compare:${stored.plan.intentId.value}:synthesis:${intent.id.value}"
        val plan = CompareSynthesisPlan(
            id = intent.id,
            sessionId = intent.sessionId,
            conversationId = stored.plan.conversationId,
            parentUserMessageId = stored.plan.parentUserMessageId,
            sourceBranchIds = intent.sourceBranchIds,
            sourceAssistantMessageIds = intent.sourceBranchIds.map { source -> stored.plan.branches.first { it.branchId == source }.assistantMessageId },
            canonicalContext = intent.newCanonicalContext,
            assistantMessageId = MessageNodeId("$stem:assistant"),
            invocationId = InvocationId("$stem:invocation"),
            attemptId = ProviderAttemptId("$stem:attempt"),
            freshConsentBudgetGate = intent.freshConsentBudgetGate,
        )
        stored.syntheses[intent.id] = fingerprint to plan
        return CompareSynthesisResult.Planned(plan)
    }

    private fun validGrants(plan: CompareExecutionGrantedPlan, context: CanonicalContextSnapshotRef): Boolean =
        plan.contextHash == context.contentHash && plan.branchGrants.size == CompareExecutionSummaryConfirmation.COMPARE_MVP_TARGET_COUNT &&
            plan.branchGrants.all { grant ->
                grant.requestFingerprint == plan.requestFingerprint && grant.contextHash == plan.contextHash &&
                    grant.textSha256 == plan.textSha256 && grant.expiresAt == plan.expiresAt
            } &&
            plan.branchGrants.map(CompareBranchExecutionGrant::branchId).toSet().size == plan.branchGrants.size &&
            plan.branchGrants.map(CompareBranchExecutionGrant::logicalModelId).toSet().size == plan.branchGrants.size &&
            plan.branchGrants.map(CompareBranchExecutionGrant::deploymentId).toSet().size == plan.branchGrants.size

    private fun fingerprint(intent: CompareConversationSessionIntent): String = sha256(
        buildList {
            add("intent=${intent.id.value}")
            add("confirmation=${intent.grantedPlan.confirmationId}")
            add("request=${intent.grantedPlan.requestFingerprint}")
            add("conversation=${intent.conversationId.value}")
            add("parent=${intent.parentUserMessageId.value}")
            add("context=${intent.canonicalContext.id.value}:${intent.canonicalContext.contentHash}:${intent.canonicalContext.revision}")
            intent.grantedPlan.branchGrants.forEach { add("grant=${it.grantId}:${it.branchId.value}:${it.confirmationScopeFingerprint}:${it.expiresAt}") }
        }.joinToString("\n"),
    )

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    internal fun plannedSessionCountForContractTest(): Int = sessionsById.size
}

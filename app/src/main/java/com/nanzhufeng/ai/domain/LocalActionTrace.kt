package com.nanzhufeng.ai.domain

/**
 * P4-M L3 is deliberately a separate, metadata-only IR. It never contributes bodies to P4-D,
 * P4-J/P4-K, a Prompt, RunSpec, export, eval conclusion, or any egress path.
 */
const val LOCAL_ACTION_TRACE_METADATA_FORMAT = "local-l3-metadata-v1"
private const val LOCAL_ACTION_TRACE_MAX_ENTRIES = 12

private class LocalActionTraceDomainFailure(val reason: LocalActionTraceRejection) : IllegalStateException()

data class LocalActionTraceCandidate(
    /** SHA-256 safe selector, not an intent, invocation, message, or conversation ID. */
    val selectionId: String,
    val actionKind: ConversationActionKind,
    val terminalState: MessageDeliveryState,
    val occurredAt: java.time.Instant,
    val sourceVersion: String,
)

data class LocalActionTracePlan(
    val format: String = LOCAL_ACTION_TRACE_METADATA_FORMAT,
    val conversationIdSafeSummary: String,
    val candidates: List<LocalActionTraceCandidate>,
    val exclusions: List<String>,
) {
    init {
        require(candidates.size <= LOCAL_ACTION_TRACE_MAX_ENTRIES)
        require(candidates.map(LocalActionTraceCandidate::selectionId).distinct().size == candidates.size)
    }
}

data class LocalActionTraceRequest(
    val conversationId: ConversationId,
    val selectedActionIds: Set<String>,
)

data class LocalActionTraceEntry(
    val actionKind: ConversationActionKind,
    val stableIdSafeSummary: String,
    val terminalState: MessageDeliveryState,
    val occurredAt: java.time.Instant,
    val sourceVersion: String,
)

data class LocalActionTraceSnapshot(
    val format: String = LOCAL_ACTION_TRACE_METADATA_FORMAT,
    val conversationIdSafeSummary: String,
    val entries: List<LocalActionTraceEntry>,
    val exclusions: List<String>,
) {
    init {
        require(entries.size <= LOCAL_ACTION_TRACE_MAX_ENTRIES)
        require(entries.map(LocalActionTraceEntry::stableIdSafeSummary).distinct().size == entries.size)
    }
}

enum class LocalActionTraceRejection {
    MISSING_CONVERSATION,
    INCONSISTENT_PROJECT_REFERENCE,
    STALE_CONTEXT,
    INELIGIBLE_ACTION,
}

sealed interface LocalActionTracePlanResult {
    data class Available(val plan: LocalActionTracePlan) : LocalActionTracePlanResult
    data class Rejected(val reason: LocalActionTraceRejection) : LocalActionTracePlanResult
}

sealed interface LocalActionTraceResult {
    data class Available(val snapshot: LocalActionTraceSnapshot) : LocalActionTraceResult
    data class Rejected(val reason: LocalActionTraceRejection) : LocalActionTraceResult
}

/**
 * The only L3 interpretation of existing P3-C lineage. It accepts terminal, verified local
 * fixture continue/retry/change-model facts only. User edits and branch switches are excluded:
 * their existing snapshot mutations have no append-only user-action lineage to prove origin.
 */
class LocalActionTraceDomain {
    fun plan(metadata: ContextSelectionSnapshot, snapshot: ConversationSnapshot, lineages: List<ConversationAttemptLineage>): LocalActionTracePlan {
        require(metadata.conversationId == snapshot.conversation.id) { "L3 会话元数据不一致。" }
        val candidates = verifiedTerminalLineages(snapshot, lineages)
            .sortedWith(compareByDescending<ConversationAttemptLineage> { it.createdAt }.thenBy { safeId(it) })
            .take(LOCAL_ACTION_TRACE_MAX_ENTRIES)
            .map { lineage ->
                val node = requireNotNull(snapshot.nodes.firstOrNull { it.id == lineage.createdMessageId })
                LocalActionTraceCandidate(
                    selectionId = safeId(lineage),
                    actionKind = lineage.actionKind,
                    terminalState = node.deliveryState,
                    occurredAt = lineage.createdAt,
                    sourceVersion = "p3c-conversation-action-lineage-v${lineage.schemaVersion}",
                )
            }
        return LocalActionTracePlan(
            conversationIdSafeSummary = safeConversationId(snapshot.conversation.id),
            candidates = candidates,
            exclusions = exclusions,
        )
    }

    fun preview(request: LocalActionTraceRequest, metadata: ContextSelectionSnapshot, snapshot: ConversationSnapshot, lineages: List<ConversationAttemptLineage>): LocalActionTraceResult {
        val plan = plan(metadata, snapshot, lineages)
        if (plan.conversationIdSafeSummary != safeConversationId(request.conversationId)) return LocalActionTraceResult.Rejected(LocalActionTraceRejection.STALE_CONTEXT)
        val candidateIds = plan.candidates.map(LocalActionTraceCandidate::selectionId).toSet()
        if (request.selectedActionIds.any { it !in candidateIds }) return LocalActionTraceResult.Rejected(LocalActionTraceRejection.INELIGIBLE_ACTION)
        return LocalActionTraceResult.Available(
            LocalActionTraceSnapshot(
                conversationIdSafeSummary = plan.conversationIdSafeSummary,
                entries = plan.candidates.filter { it.selectionId in request.selectedActionIds }.map {
                    LocalActionTraceEntry(it.actionKind, it.selectionId, it.terminalState, it.occurredAt, it.sourceVersion)
                },
                exclusions = plan.exclusions,
            ),
        )
    }

    private fun verifiedTerminalLineages(snapshot: ConversationSnapshot, lineages: List<ConversationAttemptLineage>): List<ConversationAttemptLineage> {
        val models = P3CLocalFixtureRegistry.snapshot.models.associateBy { it.id }
        if (lineages.any { it.conversationId != snapshot.conversation.id }) throw LocalActionTraceDomainFailure(LocalActionTraceRejection.STALE_CONTEXT)
        return lineages.filter { lineage ->
            val node = snapshot.nodes.firstOrNull { it.id == lineage.createdMessageId }
            val origin = snapshot.nodes.firstOrNull { it.id == lineage.originMessageId }
            node?.conversationId == snapshot.conversation.id &&
                origin?.conversationId == snapshot.conversation.id &&
                node.role == MessageRole.ASSISTANT &&
                origin.role == MessageRole.ASSISTANT &&
                node.invocation?.invocationId == lineage.invocationId &&
                node.deliveryState in terminalStates &&
                lineage.actionKind in supportedActions &&
                lineage.selection.providerId == ProviderId.MOCK &&
                lineage.selection.registrySnapshotId == P3CLocalFixtureRegistry.snapshot.id &&
                models.containsKey(lineage.selection.modelId) &&
                lineage.selection.harnessId.startsWith("p3c-local-") &&
                lineage.selection.harnessId.endsWith("-fixture")
        }
    }

    private fun safeId(lineage: ConversationAttemptLineage): String =
        MemoryDomain.sha256("$LOCAL_ACTION_TRACE_METADATA_FORMAT|${lineage.intentId.value}|${lineage.invocationId.value}")

    private fun safeConversationId(id: ConversationId): String = "conversation#${MemoryDomain.sha256(id.value).take(12)}"

    private companion object {
        val supportedActions = setOf(ConversationActionKind.CONTINUE, ConversationActionKind.RETRY, ConversationActionKind.CHANGE_MODEL)
        val terminalStates = setOf(MessageDeliveryState.COMPLETE, MessageDeliveryState.FAILED, MessageDeliveryState.CANCELLED)
        val exclusions = listOf(
            "仅纳入可证明用户发起的 P3-C 终态本地 fixture 谱系。",
            "编辑用户消息和切分支没有 append-only 动作谱系，故排除。",
            "不含 assistant/user 正文、Provider/model 原始 payload、runtime event/chunk、Token/cost、附件、URI、路径、命令、日志、诊断、网页或缓存前缀。",
        )
    }
}

/** P4-M's sole public, read-only entry. It rechecks P4-B before and after every lineage read. */
class ReadExplicitLocalActionTraceUseCase(
    private val conversations: ConversationRepository,
    private val projects: ProjectRepository,
    private val actions: ConversationActionRepository,
    private val metadataSelection: ReadContextSelectionUseCase = ReadContextSelectionUseCase(conversations, projects),
    private val domain: LocalActionTraceDomain = LocalActionTraceDomain(),
) {
    fun plan(conversationId: ConversationId): LocalActionTracePlanResult = resolve(conversationId).fold(
        onSuccess = { LocalActionTracePlanResult.Available(domain.plan(it.metadata, it.snapshot, it.lineages)) },
        onFailure = { LocalActionTracePlanResult.Rejected(it.asRejection()) },
    )

    fun execute(request: LocalActionTraceRequest): LocalActionTraceResult = resolve(request.conversationId).fold(
        onSuccess = { domain.preview(request, it.metadata, it.snapshot, it.lineages) },
        onFailure = { LocalActionTraceResult.Rejected(it.asRejection()) },
    )

    private fun resolve(conversationId: ConversationId): Result<Resolved> = runCatching {
        val first = metadataSelection.execute(conversationId).orThrow()
        val snapshot = requireNotNull(conversations.findById(conversationId)) { "missing" }
        val project = snapshot.conversation.projectId?.let { projects.findById(ProjectId(it)) }
        if (snapshot.conversation.projectId != null && project == null) throw ResolutionFailure(ContextSelectionRejection.INCONSISTENT_PROJECT_REFERENCE)
        val firstLineages = actions.lineagesForConversation(conversationId)
        val second = metadataSelection.execute(conversationId).orThrow()
        val secondLineages = actions.lineagesForConversation(conversationId)
        if (first != second || firstLineages != secondLineages) throw StaleActionTraceFailure
        Resolved(second, snapshot, secondLineages)
    }

    private data class Resolved(val metadata: ContextSelectionSnapshot, val snapshot: ConversationSnapshot, val lineages: List<ConversationAttemptLineage>)
    private class ResolutionFailure(val reason: ContextSelectionRejection) : IllegalStateException()
    private data object StaleActionTraceFailure : IllegalStateException()
    private fun ContextSelectionResult.orThrow(): ContextSelectionSnapshot = when (this) {
        is ContextSelectionResult.Selected -> snapshot
        is ContextSelectionResult.Rejected -> throw ResolutionFailure(reason)
    }
    private fun Throwable.asRejection(): LocalActionTraceRejection = when (this) {
        is ResolutionFailure -> when (reason) {
            ContextSelectionRejection.MISSING_CONVERSATION -> LocalActionTraceRejection.MISSING_CONVERSATION
            ContextSelectionRejection.INCONSISTENT_PROJECT_REFERENCE -> LocalActionTraceRejection.INCONSISTENT_PROJECT_REFERENCE
        }
        is LocalActionTraceDomainFailure -> reason
        StaleActionTraceFailure -> LocalActionTraceRejection.STALE_CONTEXT
        else -> LocalActionTraceRejection.MISSING_CONVERSATION
    }
}

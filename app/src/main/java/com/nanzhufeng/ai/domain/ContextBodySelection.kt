package com.nanzhufeng.ai.domain

/**
 * P4-D's local-only body-selection IR. It deliberately stops before Prompt, token, RunSpec,
 * Provider, or egress concerns. Every inclusion is supplied by an explicit transient request.
 */
enum class ContextBodyLayer { L0_STABLE, L1_PROJECT, L2_CONVERSATION, L3_RECENT_ACTIONS }
enum class ContextBodySourceKind { GLOBAL_MEMORY, GLOBAL_KNOWLEDGE, PROJECT_INSTRUCTION, PROJECT_MEMORY, PROJECT_KNOWLEDGE, CONVERSATION_PATH, CONVERSATION_MEMORY, DRAFT, SIBLING_BRANCH, ATTACHMENT, TOOL_RESULT, KNOWLEDGE, RETRIEVAL, SUMMARY_COMPRESSION, CACHE_PREFIX, RECENT_ACTIONS }
enum class ContextBodyDisposition { EXPLICITLY_SELECTABLE, EXCLUDED, NOT_IMPLEMENTED }

data class ContextBodyBoundary(
    val layer: ContextBodyLayer,
    val kind: ContextBodySourceKind,
    val disposition: ContextBodyDisposition,
    val reason: String,
)

/** Metadata for a control only; Memory, project, and conversation bodies are absent here. */
data class ContextBodyCandidate(
    val id: String,
    val layer: ContextBodyLayer,
    val kind: ContextBodySourceKind,
    val title: String,
    val contentHash: String,
    val revision: Int? = null,
)

data class ExplicitContextBodyPlan(
    val conversationId: ConversationId,
    val candidates: List<ContextBodyCandidate>,
    val boundaries: List<ContextBodyBoundary>,
    val policyVersion: Int = 1,
) {
    init {
        require(candidates.map(ContextBodyCandidate::id).distinct().size == candidates.size)
        require(boundaries.map { it.layer to it.kind }.distinct().size == boundaries.size)
    }
}

/** All controls intentionally default off. This value is never persisted. */
data class ExplicitContextBodyRequest(
    val conversationId: ConversationId,
    val includeProjectInstruction: Boolean = false,
    val includeConversationPath: Boolean = false,
    val selectedMemoryIds: Set<MemoryId> = emptySet(),
    val selectedKnowledgeIds: Set<KnowledgeItemId> = emptySet(),
    val selectedKnowledgeVersions: Map<KnowledgeItemId, KnowledgeContextVersion> = emptyMap(),
)
data class KnowledgeContextVersion(val revision: Int, val contentHash: String)

data class ExplicitContextBodyEntry(
    val layer: ContextBodyLayer,
    val kind: ContextBodySourceKind,
    val sourceId: String,
    val title: String,
    val body: String,
    val contentHash: String,
    val role: MessageRole? = null,
    val revision: Int? = null,
)

/** A previewable local IR, never a Prompt or provider-ready representation. */
data class ExplicitContextBodySnapshot(
    val conversationId: ConversationId,
    val metadata: ContextSelectionSnapshot,
    val entries: List<ExplicitContextBodyEntry>,
    val boundaries: List<ContextBodyBoundary>,
    val policyVersion: Int = 1,
) {
    init { require(entries.map { it.kind to it.sourceId }.distinct().size == entries.size) }
}

enum class ExplicitContextBodyRejection {
    MISSING_CONVERSATION, INCONSISTENT_PROJECT_REFERENCE, STALE_CONTEXT, INELIGIBLE_MEMORY, INELIGIBLE_KNOWLEDGE, SENSITIVE_CONTENT,
}

sealed interface ExplicitContextBodyPlanResult {
    data class Available(val plan: ExplicitContextBodyPlan) : ExplicitContextBodyPlanResult
    data class Rejected(val reason: ExplicitContextBodyRejection) : ExplicitContextBodyPlanResult
}

sealed interface ExplicitContextBodyResult {
    data class Selected(val snapshot: ExplicitContextBodySnapshot) : ExplicitContextBodyResult
    data class Rejected(val reason: ExplicitContextBodyRejection) : ExplicitContextBodyResult
}
sealed interface ContextKnowledgeSearchResult {
    data class Available(val results: List<KnowledgeSearchResult>) : ContextKnowledgeSearchResult
    data class Rejected(val reason: ExplicitContextBodyRejection) : ContextKnowledgeSearchResult
}

/** The only P4-D selector. It has no write path and never consults ConversationSettings.memorySources. */
class ContextBodySelectionDomain {
    fun plan(metadata: ContextSelectionSnapshot, conversation: ConversationSnapshot, project: ProjectSnapshot?, memories: List<MemorySnapshot>): ExplicitContextBodyPlan {
        val eligible = eligibleMemories(conversation.conversation, project, memories)
        val candidates = buildList {
            project?.activeInstruction?.takeIf { it.content.isNotBlank() }?.let { revision ->
                add(ContextBodyCandidate(revision.id.value, ContextBodyLayer.L1_PROJECT, ContextBodySourceKind.PROJECT_INSTRUCTION, "当前项目指令", revision.contentHash, revision.revision))
            }
            if (MessageTree(conversation.conversation, conversation.nodes).contextPath().any { node -> node.content.any { it is ContentBlock.Text } }) {
                add(ContextBodyCandidate(conversation.conversation.id.value, ContextBodyLayer.L2_CONVERSATION, ContextBodySourceKind.CONVERSATION_PATH, "当前根到叶对话路径", MemoryDomain.sha256(metadata.messages.joinToString("|") { it.contentHash })))
            }
            eligible.forEach { memory ->
                val (layer, kind) = when (memory.memory.scope.kind) {
                    MemoryScopeKind.GLOBAL -> ContextBodyLayer.L0_STABLE to ContextBodySourceKind.GLOBAL_MEMORY
                    MemoryScopeKind.PROJECT -> ContextBodyLayer.L1_PROJECT to ContextBodySourceKind.PROJECT_MEMORY
                    MemoryScopeKind.CONVERSATION -> ContextBodyLayer.L2_CONVERSATION to ContextBodySourceKind.CONVERSATION_MEMORY
                }
                add(ContextBodyCandidate(memory.memory.id.value, layer, kind, memory.memory.title, memory.memory.contentHash, memory.revisions.maxOfOrNull { it.revision }))
            }
        }
        return ExplicitContextBodyPlan(conversation.conversation.id, candidates, boundaries())
    }

    fun select(
        request: ExplicitContextBodyRequest,
        metadata: ContextSelectionSnapshot,
        conversation: ConversationSnapshot,
        project: ProjectSnapshot?,
        memories: List<MemorySnapshot>,
        knowledge: List<KnowledgeSnapshot>,
    ): ExplicitContextBodyResult {
        val plan = plan(metadata, conversation, project, memories)
        val candidates = plan.candidates.associateBy(ContextBodyCandidate::id)
        val selectedMemoryIds = request.selectedMemoryIds.map(MemoryId::value).toSet()
        if (selectedMemoryIds.any { id -> candidates[id]?.kind !in setOf(ContextBodySourceKind.GLOBAL_MEMORY, ContextBodySourceKind.PROJECT_MEMORY, ContextBodySourceKind.CONVERSATION_MEMORY) }) {
            return ExplicitContextBodyResult.Rejected(ExplicitContextBodyRejection.INELIGIBLE_MEMORY)
        }
        if (request.includeProjectInstruction && candidates.values.none { it.kind == ContextBodySourceKind.PROJECT_INSTRUCTION }) return ExplicitContextBodyResult.Rejected(ExplicitContextBodyRejection.INELIGIBLE_MEMORY)
        if (request.includeConversationPath && candidates.values.none { it.kind == ContextBodySourceKind.CONVERSATION_PATH }) return ExplicitContextBodyResult.Rejected(ExplicitContextBodyRejection.INELIGIBLE_MEMORY)
        val eligibleKnowledge = eligibleKnowledge(project, knowledge).associateBy { it.item.id }
        if (request.selectedKnowledgeIds.any { it !in eligibleKnowledge }) return ExplicitContextBodyResult.Rejected(ExplicitContextBodyRejection.INELIGIBLE_KNOWLEDGE)
        if (request.selectedKnowledgeVersions.keys != request.selectedKnowledgeIds || request.selectedKnowledgeVersions.any { (id, expected) ->
                val actual = eligibleKnowledge[id] ?: return@any true
                expected.revision != (actual.revisions.maxOfOrNull { it.revision } ?: 1) || expected.contentHash != actual.lifecycle.contentHash
            }) return ExplicitContextBodyResult.Rejected(ExplicitContextBodyRejection.STALE_CONTEXT)

        val entries = buildList {
            if (request.includeProjectInstruction) project?.activeInstruction?.takeIf { it.content.isNotBlank() }?.let { revision ->
                add(ExplicitContextBodyEntry(ContextBodyLayer.L1_PROJECT, ContextBodySourceKind.PROJECT_INSTRUCTION, revision.id.value, "当前项目指令", revision.content, revision.contentHash, revision = revision.revision))
            }
            if (request.includeConversationPath) MessageTree(conversation.conversation, conversation.nodes).contextPath().forEach { node ->
                val text = node.content.filterIsInstance<ContentBlock.Text>().joinToString("\n") { it.text }
                if (text.isNotBlank()) add(ExplicitContextBodyEntry(ContextBodyLayer.L2_CONVERSATION, ContextBodySourceKind.CONVERSATION_PATH, node.id.value, "当前对话消息", text, MemoryDomain.sha256(text), node.role))
            }
            eligibleMemories(conversation.conversation, project, memories)
                .filter { it.memory.id in request.selectedMemoryIds }
                .forEach { memory ->
                    val (layer, kind) = when (memory.memory.scope.kind) {
                        MemoryScopeKind.GLOBAL -> ContextBodyLayer.L0_STABLE to ContextBodySourceKind.GLOBAL_MEMORY
                        MemoryScopeKind.PROJECT -> ContextBodyLayer.L1_PROJECT to ContextBodySourceKind.PROJECT_MEMORY
                        MemoryScopeKind.CONVERSATION -> ContextBodyLayer.L2_CONVERSATION to ContextBodySourceKind.CONVERSATION_MEMORY
                    }
                    add(ExplicitContextBodyEntry(layer, kind, memory.memory.id.value, memory.memory.title, memory.memory.body, memory.memory.contentHash, revision = memory.revisions.maxOfOrNull { it.revision }))
                }
            request.selectedKnowledgeIds.sortedBy { it.value }.forEach { id ->
                val item = requireNotNull(eligibleKnowledge[id]); val kind = if (item.lifecycle.scope == KnowledgeScope.GLOBAL) ContextBodySourceKind.GLOBAL_KNOWLEDGE else ContextBodySourceKind.PROJECT_KNOWLEDGE
                val layer = if (item.lifecycle.scope == KnowledgeScope.GLOBAL) ContextBodyLayer.L0_STABLE else ContextBodyLayer.L1_PROJECT
                add(ExplicitContextBodyEntry(layer, kind, item.item.id.value, item.item.title, item.item.body, item.lifecycle.contentHash, revision = item.revisions.maxOfOrNull { it.revision }))
            }
        }
        if (entries.any { MemoryDomain.sensitiveRejection(it.body) != null }) return ExplicitContextBodyResult.Rejected(ExplicitContextBodyRejection.SENSITIVE_CONTENT)
        return ExplicitContextBodyResult.Selected(ExplicitContextBodySnapshot(request.conversationId, metadata, entries, plan.boundaries))
    }

    private fun eligibleMemories(conversation: Conversation, project: ProjectSnapshot?, memories: List<MemorySnapshot>): List<MemorySnapshot> = memories
        .filter { it.memory.status == MemoryStatus.ACTIVE }
        .filter { memory -> when (memory.memory.scope.kind) {
            MemoryScopeKind.GLOBAL -> true
            MemoryScopeKind.PROJECT -> memory.memory.scope.projectId == project?.project?.id
            MemoryScopeKind.CONVERSATION -> memory.memory.scope.conversationId == conversation.id
        } }
        .sortedWith(compareBy<MemorySnapshot>({ layerRank(it.memory.scope.kind) }, { it.memory.id.value }))

    private fun eligibleKnowledge(project: ProjectSnapshot?, knowledge: List<KnowledgeSnapshot>): List<KnowledgeSnapshot> = knowledge
        .filter { it.lifecycle.status == KnowledgeStatus.ACTIVE }
        .filter { it.lifecycle.scope == KnowledgeScope.GLOBAL || it.lifecycle.projectId == project?.project?.id }
        .sortedWith(compareBy<KnowledgeSnapshot>({ if (it.lifecycle.scope == KnowledgeScope.GLOBAL) 0 else 1 }, { it.item.id.value }))

    private fun boundaries(): List<ContextBodyBoundary> = listOf(
        ContextBodyBoundary(ContextBodyLayer.L0_STABLE, ContextBodySourceKind.GLOBAL_MEMORY, ContextBodyDisposition.EXPLICITLY_SELECTABLE, "仅可逐项选择 ACTIVE GLOBAL Memory。"),
        ContextBodyBoundary(ContextBodyLayer.L1_PROJECT, ContextBodySourceKind.PROJECT_INSTRUCTION, ContextBodyDisposition.EXPLICITLY_SELECTABLE, "仅可明确选择当前 Project 的最新非空指令。"),
        ContextBodyBoundary(ContextBodyLayer.L1_PROJECT, ContextBodySourceKind.PROJECT_MEMORY, ContextBodyDisposition.EXPLICITLY_SELECTABLE, "仅可逐项选择当前 Project 的 ACTIVE Memory。"),
        ContextBodyBoundary(ContextBodyLayer.L2_CONVERSATION, ContextBodySourceKind.CONVERSATION_PATH, ContextBodyDisposition.EXPLICITLY_SELECTABLE, "仅可明确选择当前根到叶路径中的 Text。"),
        ContextBodyBoundary(ContextBodyLayer.L2_CONVERSATION, ContextBodySourceKind.CONVERSATION_MEMORY, ContextBodyDisposition.EXPLICITLY_SELECTABLE, "仅可逐项选择当前 Conversation 的 ACTIVE Memory。"),
        ContextBodyBoundary(ContextBodyLayer.L2_CONVERSATION, ContextBodySourceKind.DRAFT, ContextBodyDisposition.EXCLUDED, "草稿不是已提交事实。"),
        ContextBodyBoundary(ContextBodyLayer.L2_CONVERSATION, ContextBodySourceKind.SIBLING_BRANCH, ContextBodyDisposition.EXCLUDED, "非当前分支不得混入。"),
        ContextBodyBoundary(ContextBodyLayer.L2_CONVERSATION, ContextBodySourceKind.ATTACHMENT, ContextBodyDisposition.EXCLUDED, "不读取附件内容、URI 或字节。"),
        ContextBodyBoundary(ContextBodyLayer.L2_CONVERSATION, ContextBodySourceKind.TOOL_RESULT, ContextBodyDisposition.EXCLUDED, "Tool 结果不属于本阶段正文。"),
        ContextBodyBoundary(ContextBodyLayer.L0_STABLE, ContextBodySourceKind.GLOBAL_KNOWLEDGE, ContextBodyDisposition.EXPLICITLY_SELECTABLE, "仅搜索后逐项选择 ACTIVE GLOBAL Knowledge。"),
        ContextBodyBoundary(ContextBodyLayer.L1_PROJECT, ContextBodySourceKind.PROJECT_KNOWLEDGE, ContextBodyDisposition.EXPLICITLY_SELECTABLE, "仅搜索后逐项选择当前 Project 的 ACTIVE Knowledge。"),
        ContextBodyBoundary(ContextBodyLayer.L1_PROJECT, ContextBodySourceKind.RETRIEVAL, ContextBodyDisposition.EXPLICITLY_SELECTABLE, "检索只排序、过滤和截取 snippet；不会自动加入正文。"),
        ContextBodyBoundary(ContextBodyLayer.L1_PROJECT, ContextBodySourceKind.SUMMARY_COMPRESSION, ContextBodyDisposition.NOT_IMPLEMENTED, "摘要和压缩尚无合同。"),
        ContextBodyBoundary(ContextBodyLayer.L0_STABLE, ContextBodySourceKind.CACHE_PREFIX, ContextBodyDisposition.NOT_IMPLEMENTED, "缓存稳定前缀尚无合同。"),
        ContextBodyBoundary(ContextBodyLayer.L3_RECENT_ACTIONS, ContextBodySourceKind.RECENT_ACTIONS, ContextBodyDisposition.EXCLUDED, "P4-M L3 是独立安全元数据，不是正文；不读取运行日志、命令或 Provider 事件。"),
    )

    private fun layerRank(scope: MemoryScopeKind): Int = when (scope) { MemoryScopeKind.GLOBAL -> 0; MemoryScopeKind.PROJECT -> 1; MemoryScopeKind.CONVERSATION -> 2 }
}

class ReadExplicitContextBodyUseCase(
    private val conversations: ConversationRepository,
    private val projects: ProjectRepository,
    private val memories: MemoryRepository,
    private val knowledge: KnowledgeManagementRepository = EmptyKnowledgeManagementRepository,
    private val metadataSelection: ReadContextSelectionUseCase = ReadContextSelectionUseCase(conversations, projects),
    private val domain: ContextBodySelectionDomain = ContextBodySelectionDomain(),
) {
    fun plan(conversationId: ConversationId): ExplicitContextBodyPlanResult = resolve(conversationId).fold(
        onSuccess = { resolved -> ExplicitContextBodyPlanResult.Available(domain.plan(resolved.metadata, resolved.conversation, resolved.project, resolved.memories)) },
        onFailure = { ExplicitContextBodyPlanResult.Rejected(it.asRejection()) },
    )

    fun execute(request: ExplicitContextBodyRequest): ExplicitContextBodyResult = resolve(request.conversationId).fold(
        onSuccess = { resolved -> domain.select(request, resolved.metadata, resolved.conversation, resolved.project, resolved.memories, resolved.knowledge) },
        onFailure = { ExplicitContextBodyResult.Rejected(it.asRejection()) },
    )

    /** Search exposes bounded metadata/snippets only; bodies are re-read only after explicit IDs are chosen. */
    fun searchKnowledge(conversationId: ConversationId, query: String): ContextKnowledgeSearchResult = resolve(conversationId).fold(
        onSuccess = { resolved ->
            val allowed = resolved.knowledge.filter { it.lifecycle.status == KnowledgeStatus.ACTIVE && (it.lifecycle.scope == KnowledgeScope.GLOBAL || it.lifecycle.projectId == resolved.project?.project?.id) }
            ContextKnowledgeSearchResult.Available(KnowledgeDomain(java.time.Clock.systemUTC()).search(allowed, KnowledgeSearchFilter(query = query, status = KnowledgeStatus.ACTIVE)))
        },
        onFailure = { ContextKnowledgeSearchResult.Rejected(it.asRejection()) },
    )

    private fun resolve(conversationId: ConversationId): Result<Resolved> = runCatching {
        val first = when (val metadata = metadataSelection.execute(conversationId)) {
            is ContextSelectionResult.Selected -> metadata.snapshot
            is ContextSelectionResult.Rejected -> throw ResolutionFailure(metadata.reason)
        }
        val conversation = requireNotNull(conversations.findById(conversationId)) { "missing" }
        val project = conversation.conversation.projectId?.let { projects.findById(ProjectId(it)) }
        if (conversation.conversation.projectId != null && project == null) throw ResolutionFailure(ContextSelectionRejection.INCONSISTENT_PROJECT_REFERENCE)
        val second = ContextSelectionDomain().select(conversation, project)
        if (first != second) throw StaleContextFailure
        Resolved(first, conversation, project, memories.list(null, MemoryStatus.ACTIVE, ""), knowledge.listSnapshots(KnowledgeSearchFilter(status = KnowledgeStatus.ACTIVE)))
    }

    private data class Resolved(val metadata: ContextSelectionSnapshot, val conversation: ConversationSnapshot, val project: ProjectSnapshot?, val memories: List<MemorySnapshot>, val knowledge: List<KnowledgeSnapshot>)
    private class ResolutionFailure(val reason: ContextSelectionRejection) : IllegalStateException()
    private data object StaleContextFailure : IllegalStateException()
    private fun Throwable.asRejection(): ExplicitContextBodyRejection = when (this) {
        is ResolutionFailure -> when (reason) {
            ContextSelectionRejection.MISSING_CONVERSATION -> ExplicitContextBodyRejection.MISSING_CONVERSATION
            ContextSelectionRejection.INCONSISTENT_PROJECT_REFERENCE -> ExplicitContextBodyRejection.INCONSISTENT_PROJECT_REFERENCE
        }
        StaleContextFailure -> ExplicitContextBodyRejection.STALE_CONTEXT
        else -> ExplicitContextBodyRejection.MISSING_CONVERSATION
    }
}

private object EmptyKnowledgeManagementRepository : KnowledgeManagementRepository {
    override fun mutate(intent: KnowledgeIntent, fingerprint: String): KnowledgeMutationResult = KnowledgeMutationResult.Rejected(KnowledgeRejectionCode.INVALID_ACTION)
    override fun findSnapshot(id: KnowledgeItemId): KnowledgeSnapshot? = null
    override fun listSnapshots(filter: KnowledgeSearchFilter): List<KnowledgeSnapshot> = emptyList()
}

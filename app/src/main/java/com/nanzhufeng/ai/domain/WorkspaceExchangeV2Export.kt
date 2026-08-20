package com.nanzhufeng.ai.domain

/**
 * Produces the one explicit "complete workspace" selection used by the Android v2 export
 * surface.  It deliberately reads only owner metadata: content and attachment bytes are read
 * later by [NfaiExchangeV2PackageWriter], after the user has selected a SAF destination.
 */
data class WorkspaceExchangeV2ScopeSummary(
    val selection: NfaiExchangeWorkspaceSelection,
    val projectCount: Int,
    val conversationCount: Int,
    val knowledgeCount: Int,
    val memoryCount: Int,
    val relationCount: Int,
    val attachmentCount: Int,
) {
    val objectCount: Int get() = projectCount + conversationCount + knowledgeCount + memoryCount + relationCount
}

sealed interface WorkspaceExchangeV2ScopePreparation {
    data class Prepared(val value: WorkspaceExchangeV2ScopeSummary) : WorkspaceExchangeV2ScopePreparation
    data class Rejected(val reason: String) : WorkspaceExchangeV2ScopePreparation
}

/**
 * There is intentionally no "best effort" filter here.  The exact all-owner scope is assembled
 * before SAF is opened; any unsupported, incomplete, or unsafe owner is then rejected by the
 * v2 mapper and no destination write is attempted.
 */
class WorkspaceExchangeV2ScopePlanner(
    private val source: NfaiExchangeWorkspaceSource,
    private val projects: ProjectRepository,
    private val conversations: ConversationListRepository,
    private val knowledge: KnowledgeManagementRepository,
    private val memories: MemoryRepository,
    private val relationships: KnowledgeRelationshipRepository,
) {
    fun prepareCompleteWorkspace(): WorkspaceExchangeV2ScopePreparation = runCatching {
        val projectIds = (projects.list(ProjectListScope.ACTIVE) + projects.list(ProjectListScope.ARCHIVED))
            .map { it.project.id.value }.toSortedSet()
        val conversationIds = conversations.list(ConversationListScope.ALL).map { it.id.value }.toSortedSet()
        val knowledgeIds = knowledge.listSnapshots().map { it.item.id.value }.toSortedSet()
        val memoryIds = memories.list(scope = null, status = null, search = "").map { it.memory.id.value }.toSortedSet()
        val relationIds = relationships.list().map { it.relationship.id.value }.toSortedSet()
        val attachmentIds = buildSet {
            conversationIds.forEach { id ->
                val snapshot = source.conversation(ConversationId(id)) ?: error("所选对话无法读取，未创建交换包。")
                snapshot.nodes.flatMap { node -> node.content.filterIsInstance<ContentBlock.Attachment>() }
                    .forEach { add(it.attachment.id.value) }
            }
            knowledgeIds.forEach { id ->
                val snapshot = source.knowledge(KnowledgeItemId(id)) ?: error("所选知识无法读取，未创建交换包。")
                snapshot.item.attachments.forEach { add(it.id.value) }
            }
        }.toSortedSet()
        require(projectIds.isNotEmpty() || conversationIds.isNotEmpty() || knowledgeIds.isNotEmpty() || memoryIds.isNotEmpty() || relationIds.isNotEmpty()) {
            "当前没有可选择的完整工作区对象。"
        }
        val selection = NfaiExchangeWorkspaceSelection(
            objects = NfaiExchangeExportSelection(projectIds, conversationIds, knowledgeIds, memoryIds, relationIds, attachmentIds),
            // Binary content has no safe automatic classifier.  The complete-workspace scope
            // therefore treats every included attachment as high-sensitive rather than guessing.
            attachmentClassifications = attachmentIds.associateWith { "HIGH_SENSITIVE" },
        )
        WorkspaceExchangeV2ScopeSummary(
            selection = selection,
            projectCount = projectIds.size,
            conversationCount = conversationIds.size,
            knowledgeCount = knowledgeIds.size,
            memoryCount = memoryIds.size,
            relationCount = relationIds.size,
            attachmentCount = attachmentIds.size,
        )
    }.fold(
        onSuccess = WorkspaceExchangeV2ScopePreparation::Prepared,
        onFailure = { WorkspaceExchangeV2ScopePreparation.Rejected(it.message ?: "无法形成完整工作区范围。") },
    )
}

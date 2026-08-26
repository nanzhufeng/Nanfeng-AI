package com.nanzhufeng.ai.data.local

import androidx.room.RoomDatabase
import com.nanzhufeng.ai.domain.AttachmentId
import com.nanzhufeng.ai.domain.ConversationAttachmentReference
import com.nanzhufeng.ai.domain.CONVERSATION_ATTACHMENT_MAX_COUNT
import com.nanzhufeng.ai.domain.ContentBlock
import com.nanzhufeng.ai.domain.Conversation
import com.nanzhufeng.ai.domain.ConversationDraft
import com.nanzhufeng.ai.domain.ConversationId
import com.nanzhufeng.ai.domain.ConversationRepository
import com.nanzhufeng.ai.domain.ConversationSettings
import com.nanzhufeng.ai.domain.ConversationSnapshot
import com.nanzhufeng.ai.domain.MemorySourceReference
import com.nanzhufeng.ai.domain.MessageCheckpoint
import com.nanzhufeng.ai.domain.MessageDeliveryState
import com.nanzhufeng.ai.domain.MessageInvocationReference
import com.nanzhufeng.ai.domain.MessageNode
import com.nanzhufeng.ai.domain.MessageNodeId
import com.nanzhufeng.ai.domain.MessageRevision
import com.nanzhufeng.ai.domain.MessageRole
import com.nanzhufeng.ai.domain.MessageTree
import com.nanzhufeng.ai.domain.ProviderId
import com.nanzhufeng.ai.domain.InvocationId
import com.nanzhufeng.ai.domain.AiRuntimeEvent
import com.nanzhufeng.ai.domain.ConversationRuntimePersistenceResult
import com.nanzhufeng.ai.domain.ConversationRuntimeProjection
import com.nanzhufeng.ai.domain.ConversationRuntimeRepository
import com.nanzhufeng.ai.domain.ConversationRuntimeState
import com.nanzhufeng.ai.domain.ConversationRuntimeStatus
import com.nanzhufeng.ai.domain.ConversationActionPersistenceResult
import com.nanzhufeng.ai.domain.ConversationActionRepository
import com.nanzhufeng.ai.domain.ConversationAttemptLineage
import com.nanzhufeng.ai.domain.ConversationActionIntentId
import com.nanzhufeng.ai.domain.ConversationActionKind
import com.nanzhufeng.ai.domain.ConversationModelSelection
import com.nanzhufeng.ai.domain.ConversationDraftRepository
import com.nanzhufeng.ai.domain.ConversationDraftSubmissionResult
import com.nanzhufeng.ai.domain.ConversationManagementIntent
import com.nanzhufeng.ai.domain.ConversationManagementRepository
import com.nanzhufeng.ai.domain.ConversationManagementResult
import com.nanzhufeng.ai.domain.ConversationPurgeResult
import com.nanzhufeng.ai.domain.ConversationSearchRepository
import com.nanzhufeng.ai.domain.LocalSearchIndexRepository
import com.nanzhufeng.ai.domain.LocalSearchIndexRecord
import com.nanzhufeng.ai.domain.ConversationListRepository
import com.nanzhufeng.ai.domain.ConversationListScope
import com.nanzhufeng.ai.domain.ConversationSurface
import com.nanzhufeng.ai.domain.ConversationSurfaceRepository
import com.nanzhufeng.ai.domain.ImportedConversationProvenanceReader
import com.nanzhufeng.ai.domain.ModelPricing
import com.nanzhufeng.ai.domain.ModelRegistrySnapshotId
import com.nanzhufeng.ai.domain.RuntimeRunStarted
import com.nanzhufeng.ai.domain.payloadFingerprint
import java.time.Instant
import java.util.concurrent.Callable

/** Room owns only atomic persistence/rebuild. It never chooses a branch or rewrites a message. */
class RoomConversationRepository(private val database: NanfengAiDatabase) : ConversationRepository, ConversationDraftRepository, ConversationRuntimeRepository, ConversationActionRepository, ConversationManagementRepository, ConversationSearchRepository, LocalSearchIndexRepository, ConversationListRepository, ConversationSurfaceRepository, ImportedConversationProvenanceReader {
    private companion object {
        const val RECENT_MESSAGES_EXCLUDED_FROM_SUMMARY = 8
        const val ROLLING_SUMMARY_MAX_CHARS = 1_800
    }
    override fun save(snapshot: ConversationSnapshot): ConversationSnapshot = database.inConversationTransaction {
        persistSnapshot(database.conversationDao(), snapshot)
    }

    override fun permanentlyDelete(conversationId: ConversationId, expectedRevision: Long): ConversationPurgeResult =
        database.inConversationTransaction {
            val dao = database.conversationDao()
            val stored = dao.loadSnapshot(conversationId) ?: return@inConversationTransaction ConversationPurgeResult.Rejected("会话不存在，未执行删除。")
            if (stored.conversation.revision != expectedRevision) {
                return@inConversationTransaction ConversationPurgeResult.Rejected("会话已更新，请返回列表后重试。")
            }
            if (stored.conversation.deletedAt == null) {
                return@inConversationTransaction ConversationPurgeResult.Rejected("只能永久删除回收站中的会话。")
            }
            val id = conversationId.value
            dao.deleteBlocksForConversation(id)
            dao.deleteNodesForConversation(id)
            dao.deleteDraftAttachments(id)
            dao.deleteDraft(id)
            dao.deleteMemorySources(id)
            dao.deleteSearchIndexForConversation(id)
            dao.deleteRuntimeStatesForConversation(id)
            dao.deleteRuntimeEventsForConversation(id)
            dao.deleteAttemptLineagesForConversation(id)
            dao.deleteNormalChatAttemptsForConversation(id)
            dao.deleteManagementIntentsForConversation(id)
            // Saved memories are independent user data. Keep them, but detach their deleted source conversation.
            dao.detachMemoriesForConversation(id)
            if (dao.deleteConversation(id) != 1) return@inConversationTransaction ConversationPurgeResult.Rejected("会话删除未完成。")
            ConversationPurgeResult.Deleted
        }

    /** Adapter-owned commit stores call this only inside the same Room transaction as their receipt. */
    internal fun persistInExistingTransaction(snapshot: ConversationSnapshot): ConversationSnapshot =
        persistSnapshot(database.conversationDao(), snapshot)

    /** K8 mutates an existing imported message only through the normal Message Tree owner. */
    internal fun attachExistingMessageInTransaction(conversationId: ConversationId, messageId: MessageNodeId, attachment: ConversationAttachmentReference, at: Instant): ExistingMessageAttachmentResult {
        val dao = database.conversationDao(); val snapshot = dao.loadSnapshot(conversationId) ?: return ExistingMessageAttachmentResult.Failed
        val target = snapshot.nodes.firstOrNull { it.id == messageId } ?: return ExistingMessageAttachmentResult.Failed
        if (target.content.filterIsInstance<ContentBlock.Attachment>().any { it.attachment.sha256 == attachment.sha256 }) return ExistingMessageAttachmentResult.Replayed
        if (target.content.count { it is ContentBlock.Attachment } >= CONVERSATION_ATTACHMENT_MAX_COUNT) return ExistingMessageAttachmentResult.Failed
        val nextNode = target.copy(content = target.content + ContentBlock.Attachment(attachment)); val nextConversation = snapshot.conversation.copy(updatedAt = at, revision = snapshot.conversation.revision + 1)
        dao.update(nextConversation.toEntity()); dao.deleteBlocks(messageId.value); dao.insertBlocks(nextNode.content.mapIndexed { position, block -> block.toEntity(messageId, position) })
        replaceSafeSearchIndex(dao, snapshot.copy(conversation = nextConversation, nodes = snapshot.nodes.map { if (it.id == messageId) nextNode else it }))
        return ExistingMessageAttachmentResult.Linked
    }

    override fun isChatGptExportImported(conversationId: ConversationId): Boolean =
        database.chatGptExportImportTaskDao().hasProvenanceForConversation(conversationId.value)

    override fun isClaudeExportImported(conversationId: ConversationId): Boolean =
        database.claudeExportImportTaskDao().hasProvenanceForConversation(conversationId.value)

    override fun loadDraft(conversationId: ConversationId): ConversationDraft? =
        database.conversationDao().loadSnapshot(conversationId)?.draft

    override fun saveDraft(conversationId: ConversationId, draft: ConversationDraft): ConversationDraft = database.inConversationTransaction {
        val dao = database.conversationDao()
        val stored = dao.loadSnapshot(conversationId) ?: error("会话不存在，不能保存草稿。")
        if (stored.draft.text == draft.text && stored.draft.attachments == draft.attachments) return@inConversationTransaction stored.draft
        dao.upsertDraft(draft.toEntity(conversationId))
        dao.deleteDraftAttachments(conversationId.value)
        dao.insertDraftAttachments(draft.attachments.mapIndexed { position, attachment -> attachment.toDraftEntity(conversationId, position) })
        dao.loadSnapshot(conversationId)?.draft ?: error("草稿写入后无法回读。")
    }

    /** Message append and draft clearing share one transaction; a stale/failed submit keeps the draft. */
    override fun submitDraft(snapshotWithClearedDraft: ConversationSnapshot, expectedDraft: ConversationDraft): ConversationDraftSubmissionResult =
        database.inConversationTransaction {
            val dao = database.conversationDao()
            val stored = dao.loadSnapshot(snapshotWithClearedDraft.conversation.id)
                ?: return@inConversationTransaction ConversationDraftSubmissionResult.Rejected("会话不存在，草稿仍保留。")
            if (stored.draft != expectedDraft) {
                return@inConversationTransaction ConversationDraftSubmissionResult.Rejected("草稿已变化，未发送且当前草稿已保留。")
            }
            if (snapshotWithClearedDraft.draft.text.isNotEmpty() || snapshotWithClearedDraft.draft.attachments.isNotEmpty()) {
                return@inConversationTransaction ConversationDraftSubmissionResult.Rejected("提交快照未清空草稿，未发送。")
            }
            ConversationDraftSubmissionResult.Submitted(persistSnapshot(dao, snapshotWithClearedDraft))
        }

    private fun persistSnapshot(dao: ConversationDao, snapshot: ConversationSnapshot): ConversationSnapshot {
        val existing = dao.findConversation(snapshot.conversation.id.value)
        if (existing == null) dao.insertConversation(snapshot.conversation.toEntity())
        else dao.update(snapshot.conversation.toEntity())

        snapshot.nodes.forEach { node ->
            val stored = dao.findNode(node.id.value)
            if (stored == null) {
                dao.insertNode(node.toEntity())
                dao.insertBlocks(node.content.mapIndexed { position, block -> block.toEntity(node.id, position) })
            } else {
                val restored = dao.loadNode(stored)
                require(restored == node) { "消息 ID 冲突且内容不同：${node.id.value}" }
            }
        }

        dao.upsertDraft(snapshot.draft.toEntity(snapshot.conversation.id))
        dao.deleteDraftAttachments(snapshot.conversation.id.value)
        dao.insertDraftAttachments(snapshot.draft.attachments.mapIndexed { position, attachment ->
            attachment.toDraftEntity(snapshot.conversation.id, position)
        })
        dao.deleteMemorySources(snapshot.conversation.id.value)
        dao.insertMemorySources(snapshot.conversation.settings.memorySources.mapIndexed { position, source ->
            ConversationMemorySourceEntity(snapshot.conversation.id.value, position, source.memoryId, source.sourceKind, source.sourceVersion)
        })
        replaceSafeSearchIndex(dao, snapshot)

        return dao.loadSnapshot(snapshot.conversation.id) ?: error("会话写入后无法回读：${snapshot.conversation.id.value}")
    }

    private fun replaceSafeSearchIndex(dao: ConversationDao, snapshot: ConversationSnapshot) {
        val conversation = snapshot.conversation
        val rows = mutableListOf<LocalSearchIndexEntity>()
        fun add(id: String, messageId: String?, kind: String, raw: String, timestamp: Long, snippetLimit: Int = 240) {
            val normalized = raw.trim().replace(Regex("\\s+"), " ").lowercase(java.util.Locale.ROOT)
            if (normalized.isNotBlank()) rows += LocalSearchIndexEntity(id, conversation.id.value, messageId, kind, conversation.title, normalized, raw.trim().replace(Regex("\\s+"), " ").take(snippetLimit), timestamp, conversation.archivedAt?.toEpochMilli(), conversation.deletedAt?.toEpochMilli())
        }
        add("${conversation.id.value}:title", null, "TEXT", conversation.title, conversation.updatedAt.toEpochMilli())
        val contextPath = MessageTree(conversation, snapshot.nodes).contextPath()
        contextPath.forEach { node ->
            if (node.role == MessageRole.USER || node.role == MessageRole.ASSISTANT) {
                node.content.filterIsInstance<ContentBlock.Text>().forEachIndexed { index, text -> add("${conversation.id.value}:${node.id.value}:text:$index", node.id.value, "TEXT", text.text, node.createdAt.toEpochMilli()) }
                node.content.filterIsInstance<ContentBlock.Attachment>().forEachIndexed { index, attachment ->
                    val safe = listOfNotNull(attachment.attachment.displayName, attachment.attachment.mimeType).joinToString(" · ")
                    add("${conversation.id.value}:${node.id.value}:attachment:$index", node.id.value, "ATTACHMENT", safe, node.createdAt.toEpochMilli())
                }
            }
        }
        val rollingSummary = contextPath
            .filter { it.role == MessageRole.USER || it.role == MessageRole.ASSISTANT }
            .dropLast(RECENT_MESSAGES_EXCLUDED_FROM_SUMMARY)
            .flatMap { node ->
                node.content.filterIsInstance<ContentBlock.Text>().map { text ->
                    "${if (node.role == MessageRole.USER) "用户" else "助手"}：${text.text.trim().replace(Regex("\\s+"), " ")}"
                }
            }
            .takeCompleteFragments(ROLLING_SUMMARY_MAX_CHARS)
            .takeIf { it.isNotEmpty() }
            ?.joinToString("\n", prefix = "历史摘要（本地抽取）：\n")
        rollingSummary?.let { summary ->
            add("${conversation.id.value}:rolling-summary", null, "HISTORY_SUMMARY", summary, conversation.updatedAt.toEpochMilli(), ROLLING_SUMMARY_MAX_CHARS)
        }
        dao.deleteSearchIndexForConversation(conversation.id.value)
        if (rows.isNotEmpty()) dao.upsertSearchIndex(rows)
    }

    /** Keeps old turns searchable as a bounded, local extractive digest without sending them all. */
    private fun List<String>.takeCompleteFragments(limit: Int): List<String> {
        var used = 0
        return mapNotNull { fragment ->
            val normalized = fragment.trim()
            if (normalized.isBlank() || used + normalized.length > limit) null else normalized.also { used += it.length }
        }
    }

    override fun findById(id: ConversationId): ConversationSnapshot? = database.conversationDao().loadSnapshot(id)

    override fun listActive(): List<Conversation> = database.conversationDao().listVisibleActiveConversations().map { entity ->
        entity.toDomain(database.conversationDao().memorySourcesFor(entity.id))
    }

    override fun list(scope: ConversationListScope): List<Conversation> = when (scope) {
        ConversationListScope.ACTIVE -> database.conversationDao().listVisibleActiveConversations()
        ConversationListScope.ARCHIVED -> database.conversationDao().listArchivedConversations()
        ConversationListScope.DELETED -> database.conversationDao().listDeletedConversations()
        ConversationListScope.ALL -> database.conversationDao().listAllNonDeletedConversations()
    }.map { entity -> entity.toDomain(database.conversationDao().memorySourcesFor(entity.id)) }

    override fun listActive(surface: ConversationSurface): List<Conversation> = when (surface) {
        ConversationSurface.CHAT -> database.conversationDao().listVisibleActiveConversations()
        ConversationSurface.WORK -> database.conversationDao().listActiveWorkConversations()
    }.map { entity -> entity.toDomain(database.conversationDao().memorySourcesFor(entity.id)) }

    override fun snapshotsForSearch(): List<ConversationSnapshot> = database.conversationDao().listAllNonDeletedConversations()
        .mapNotNull { database.conversationDao().loadSnapshot(ConversationId(it.id)) }

    override fun searchLocalIndex(normalizedQuery: String, scope: ConversationListScope): List<LocalSearchIndexRecord> = database.inConversationTransaction {
        val dao = database.conversationDao()
        if (dao.searchIndexCount() == 0) dao.listAllNonDeletedConversations().forEach { entity ->
            dao.loadSnapshot(ConversationId(entity.id))?.let { replaceSafeSearchIndex(dao, it) }
        }
        dao.searchLocalIndex(normalizedQuery, scope.name).map { row ->
            LocalSearchIndexRecord(ConversationId(row.conversationId), row.messageNodeId?.let(::MessageNodeId), row.title, row.snippet, row.contentKind, row.timestampEpochMs, row.contentKind == "TEXT" && row.messageNodeId == null)
        }
    }

    override fun applyManagement(
        intent: ConversationManagementIntent,
        requestFingerprint: String,
        mutate: (ConversationSnapshot) -> ConversationSnapshot,
    ): ConversationManagementResult = database.inConversationTransaction {
        val dao = database.conversationDao()
        val previous = dao.managementIntent(intent.id.value)
        if (previous != null) {
            if (previous.requestFingerprint != requestFingerprint) return@inConversationTransaction ConversationManagementResult.Rejected("相同管理 intent 的请求内容不一致。")
            val snapshot = dao.loadSnapshot(ConversationId(previous.conversationId))
                ?: return@inConversationTransaction ConversationManagementResult.Rejected("重复管理 intent 缺少会话回读。")
            return@inConversationTransaction ConversationManagementResult.Replayed(snapshot)
        }
        val stored = dao.loadSnapshot(intent.conversationId)
            ?: return@inConversationTransaction ConversationManagementResult.Rejected("会话不存在，未执行管理操作。")
        if (intent.action == com.nanzhufeng.ai.domain.ConversationManagementAction.ASSIGN_PROJECT) {
            val projectId = intent.projectId ?: return@inConversationTransaction ConversationManagementResult.Rejected("项目选择缺失，未执行归属变更。")
            val project = database.projectDao().findProject(projectId)
                ?: return@inConversationTransaction ConversationManagementResult.Rejected("项目不存在，未执行归属变更。")
            if (project.deletedAtEpochMs != null || project.archivedAtEpochMs != null) {
                return@inConversationTransaction ConversationManagementResult.Rejected("项目不可用，未执行归属变更。")
            }
        }
        val updated = runCatching { mutate(stored) }.getOrElse { return@inConversationTransaction ConversationManagementResult.Rejected(it.message ?: "管理操作被拒绝。") }
        persistSnapshot(dao, updated)
        dao.insertManagementIntent(ConversationManagementIntentEntity(
            intentId = intent.id.value, conversationId = intent.conversationId.value, action = intent.action.name,
            requestFingerprint = requestFingerprint, expectedRevision = intent.expectedRevision,
            resultRevision = updated.conversation.revision, createdAtEpochMs = updated.conversation.updatedAt.toEpochMilli(),
        ))
        ConversationManagementResult.Applied(dao.loadSnapshot(intent.conversationId) ?: error("管理写入后无法回读。"))
    }

    override fun stateFor(conversationId: ConversationId): ConversationRuntimeState? =
        database.conversationDao().runtimeStateForConversation(conversationId.value)?.toDomain()

    override fun lineageForInvocation(invocationId: InvocationId): ConversationAttemptLineage? =
        database.conversationDao().attemptLineageForInvocation(invocationId.value)?.toDomain()

    override fun lineagesForConversation(conversationId: ConversationId): List<ConversationAttemptLineage> =
        database.conversationDao().attemptLineagesForConversation(conversationId.value).map { it.toDomain() }

    override fun lineageForIntent(intentId: ConversationActionIntentId): ConversationAttemptLineage? =
        database.conversationDao().attemptLineageForIntent(intentId.value)?.toDomain()

    /** P3-C commits the intent lineage and its started projection as one recoverable local fact. */
    override fun applyAction(
        projection: ConversationRuntimeProjection,
        started: RuntimeRunStarted,
        lineage: ConversationAttemptLineage,
    ): ConversationActionPersistenceResult = database.inConversationTransaction {
        val dao = database.conversationDao()
        val existing = dao.attemptLineageForIntent(lineage.intentId.value)
        if (existing != null) {
            if (existing.requestFingerprint != lineage.fingerprint()) {
                return@inConversationTransaction ConversationActionPersistenceResult.Rejected("重复 intent 的动作内容不一致。")
            }
            val snapshot = dao.loadSnapshot(lineage.conversationId)
                ?: return@inConversationTransaction ConversationActionPersistenceResult.Rejected("重放 intent 缺少会话投影。")
            val state = dao.runtimeStateForConversation(lineage.conversationId.value)?.toDomain()
                ?: return@inConversationTransaction ConversationActionPersistenceResult.Rejected("重放 intent 缺少运行状态。")
            return@inConversationTransaction ConversationActionPersistenceResult.Replayed(
                ConversationRuntimeProjection(snapshot, state), existing.toDomain(),
            )
        }
        if (dao.attemptLineageForInvocation(lineage.invocationId.value) != null) {
            return@inConversationTransaction ConversationActionPersistenceResult.Rejected("Invocation 已有谱系，不能复用。")
        }
        val stored = dao.loadSnapshot(started.conversationId)
            ?: return@inConversationTransaction ConversationActionPersistenceResult.Rejected("会话不存在，无法写入动作。")
        if (stored.conversation.currentLeafMessageId != lineage.originMessageId ||
            projection.snapshot.conversation.currentLeafMessageId != lineage.createdMessageId ||
            projection.state.invocationId != lineage.invocationId ||
            projection.state.messageId != lineage.createdMessageId
        ) return@inConversationTransaction ConversationActionPersistenceResult.Rejected("动作谱系与运行投影不一致。")
        val node = projection.snapshot.nodes.firstOrNull { it.id == lineage.createdMessageId }
            ?: return@inConversationTransaction ConversationActionPersistenceResult.Rejected("动作投影缺少新 assistant。")
        dao.update(projection.snapshot.conversation.toEntity())
        dao.insertNode(node.toEntity())
        dao.insertRuntimeEvent(started.toEntity(started.payloadFingerprint()))
        dao.upsertRuntimeState(projection.state.toEntity())
        dao.insertAttemptLineage(lineage.toEntity())
        ConversationActionPersistenceResult.Applied(
            ConversationRuntimeProjection(
                dao.loadSnapshot(lineage.conversationId) ?: error("动作写入后无法回读会话。"),
                dao.runtimeStateForConversation(lineage.conversationId.value)?.toDomain() ?: error("动作写入后无法回读运行状态。"),
            ),
            dao.attemptLineageForInvocation(lineage.invocationId.value)?.toDomain() ?: error("动作写入后无法回读谱系。"),
        )
    }

    /** Event fact, state projection and MessageNode checkpoint commit in one Room transaction. */
    override fun apply(projection: ConversationRuntimeProjection, event: AiRuntimeEvent): ConversationRuntimePersistenceResult = database.inConversationTransaction {
        applyRuntimeProjection(database.conversationDao(), projection, event)
    }

    /** A normal send may never leave a committed user message without its durable assistant run. */
    override fun submitDraftAndStart(
        snapshotWithClearedDraft: ConversationSnapshot,
        expectedDraft: ConversationDraft,
        projection: ConversationRuntimeProjection,
        event: RuntimeRunStarted,
    ): ConversationRuntimePersistenceResult = database.inConversationTransaction {
        val dao = database.conversationDao()
        val stored = dao.loadSnapshot(snapshotWithClearedDraft.conversation.id)
            ?: return@inConversationTransaction ConversationRuntimePersistenceResult.Rejected("会话不存在，草稿仍保留。")
        if (stored.draft != expectedDraft) {
            return@inConversationTransaction ConversationRuntimePersistenceResult.Rejected("草稿已变化，未发送且当前草稿已保留。")
        }
        if (snapshotWithClearedDraft.draft.text.isNotEmpty() || snapshotWithClearedDraft.draft.attachments.isNotEmpty()) {
            return@inConversationTransaction ConversationRuntimePersistenceResult.Rejected("提交快照未清空草稿，未发送。")
        }
        if (projection.snapshot.conversation.id != snapshotWithClearedDraft.conversation.id || event.conversationId != snapshotWithClearedDraft.conversation.id) {
            return@inConversationTransaction ConversationRuntimePersistenceResult.Rejected("助手运行投影与已提交会话不一致。")
        }
        persistSnapshot(dao, snapshotWithClearedDraft)
        applyRuntimeProjection(dao, projection, event)
    }

    private fun applyRuntimeProjection(
        dao: ConversationDao,
        projection: ConversationRuntimeProjection,
        event: AiRuntimeEvent,
    ): ConversationRuntimePersistenceResult {
        val fingerprint = event.payloadFingerprint()
        val byId = dao.runtimeEventById(event.eventId.value)
        val bySequence = dao.runtimeEventAt(event.invocationId.value, event.sequence)
        val replay = byId ?: bySequence
        if (replay != null) {
            if (replay.payloadFingerprint != fingerprint) return ConversationRuntimePersistenceResult.Rejected("重复序号或事件 ID 的内容不一致。")
            val snapshot = dao.loadSnapshot(event.conversationId)
                ?: return ConversationRuntimePersistenceResult.Rejected("重放事件缺少会话投影。")
            val state = dao.runtimeStateForConversation(event.conversationId.value)?.toDomain()
                ?: return ConversationRuntimePersistenceResult.Rejected("重放事件缺少运行状态。")
            return ConversationRuntimePersistenceResult.Replayed(ConversationRuntimeProjection(snapshot, state))
        }
        val stored = dao.loadSnapshot(event.conversationId)
            ?: return ConversationRuntimePersistenceResult.Rejected("会话不存在，无法写入运行事件。")
        if (stored.conversation.id != projection.snapshot.conversation.id || projection.state.conversationId != event.conversationId) {
            return ConversationRuntimePersistenceResult.Rejected("运行投影与会话不一致。")
        }
        dao.update(projection.snapshot.conversation.toEntity())
        val node = projection.snapshot.nodes.firstOrNull { it.id == projection.state.messageId }
            ?: return ConversationRuntimePersistenceResult.Rejected("运行投影缺少 assistant 消息。")
        val existingNode = dao.findNode(node.id.value)
        if (existingNode == null) {
            dao.insertNode(node.toEntity())
        } else {
            dao.updateRuntimeNode(node.id.value, node.deliveryState.name, node.checkpoint?.lastPersistedSequence, node.checkpoint?.resumableFromSequence)
            dao.deleteBlocks(node.id.value)
        }
        if (node.content.isNotEmpty()) dao.insertBlocks(node.content.mapIndexed { position, block -> block.toEntity(node.id, position) })
        dao.insertRuntimeEvent(event.toEntity(fingerprint))
        dao.upsertRuntimeState(projection.state.toEntity())
        return ConversationRuntimePersistenceResult.Applied(ConversationRuntimeProjection(
            dao.loadSnapshot(event.conversationId) ?: error("运行投影写入后无法回读。"),
            dao.runtimeStateForConversation(event.conversationId.value)?.toDomain() ?: error("运行状态写入后无法回读。"),
        ))
    }
}

internal enum class ExistingMessageAttachmentResult { Linked, Replayed, Failed }

private fun AiRuntimeEvent.toEntity(fingerprint: String) = AiRuntimeEventEntity(
    eventId.value, invocationId.value, conversationId.value, messageId.value, sequence, kind.name,
    emittedAt.toEpochMilli(), fingerprint, security.source, schemaVersion,
)

private fun ConversationRuntimeState.toEntity() = ConversationRuntimeStateEntity(
    invocationId.value, conversationId.value, messageId.value, nextExpectedSequence, status.name,
    startedAt.toEpochMilli(), updatedAt.toEpochMilli(), lastCheckpointSequence, resumableFromSequence,
    inputTokens, outputTokens, safeErrorCode, schemaVersion,
)

private fun ConversationRuntimeStateEntity.toDomain() = ConversationRuntimeState(
    InvocationId(invocationId), ConversationId(conversationId), MessageNodeId(messageId), nextExpectedSequence,
    ConversationRuntimeStatus.valueOf(status), Instant.ofEpochMilli(startedAtEpochMs), Instant.ofEpochMilli(updatedAtEpochMs),
    lastCheckpointSequence, resumableFromSequence, inputTokens, outputTokens, safeErrorCode, schemaVersion,
)

private fun ConversationAttemptLineage.toEntity() = ConversationAttemptLineageEntity(
    invocationId = invocationId.value,
    intentId = intentId.value,
    conversationId = conversationId.value,
    actionKind = actionKind.name,
    originMessageId = originMessageId.value,
    createdMessageId = createdMessageId.value,
    previousInvocationId = previousInvocationId.value,
    providerId = selection.providerId.name,
    modelId = selection.modelId,
    harnessId = selection.harnessId,
    harnessVersion = selection.harnessVersion,
    registrySnapshotId = selection.registrySnapshotId.value,
    pricingVersion = selection.pricing.priceVersion,
    pricingCurrencyCode = selection.pricing.currencyCode,
    inputMicrosPerToken = selection.pricing.inputMicrosPerToken,
    outputMicrosPerToken = selection.pricing.outputMicrosPerToken,
    cachedInputMicrosPerToken = selection.pricing.cachedInputMicrosPerToken,
    createdAtEpochMs = createdAt.toEpochMilli(),
    requestFingerprint = fingerprint(),
    schemaVersion = schemaVersion,
)

private fun ConversationAttemptLineageEntity.toDomain() = ConversationAttemptLineage(
    invocationId = InvocationId(invocationId),
    intentId = ConversationActionIntentId(intentId),
    conversationId = ConversationId(conversationId),
    actionKind = ConversationActionKind.valueOf(actionKind),
    originMessageId = MessageNodeId(originMessageId),
    createdMessageId = MessageNodeId(createdMessageId),
    previousInvocationId = InvocationId(previousInvocationId),
    selection = ConversationModelSelection(
        providerId = ProviderId.valueOf(providerId),
        modelId = modelId,
        harnessId = harnessId,
        harnessVersion = harnessVersion,
        registrySnapshotId = ModelRegistrySnapshotId(registrySnapshotId),
        pricing = ModelPricing(pricingVersion, pricingCurrencyCode, inputMicrosPerToken, outputMicrosPerToken, cachedInputMicrosPerToken),
    ),
    createdAt = Instant.ofEpochMilli(createdAtEpochMs),
    schemaVersion = schemaVersion,
)

private fun ConversationDao.update(entity: ConversationEntity): Int = updateConversation(
    id = entity.id,
    title = entity.title,
    surface = entity.surface,
    projectId = entity.projectId,
    currentLeafMessageId = entity.currentLeafMessageId,
    updatedAtEpochMs = entity.updatedAtEpochMs,
    defaultProviderId = entity.defaultProviderId,
    defaultModelId = entity.defaultModelId,
    harnessId = entity.harnessId,
    harnessVersion = entity.harnessVersion,
    contextPolicyVersion = entity.contextPolicyVersion,
    archivedAtEpochMs = entity.archivedAtEpochMs,
    pinnedAtEpochMs = entity.pinnedAtEpochMs,
    deletedAtEpochMs = entity.deletedAtEpochMs,
    revision = entity.revision,
    autoTitlePending = entity.autoTitlePending,
    schemaVersion = entity.schemaVersion,
)

private fun ConversationDao.loadSnapshot(id: ConversationId): ConversationSnapshot? = findConversation(id.value)?.let { entity ->
    val nodes = nodesFor(entity.id).map(::loadNode)
    ConversationSnapshot(
        conversation = entity.toDomain(memorySourcesFor(entity.id)),
        nodes = nodes,
        draft = draftFor(entity.id)?.let { draft ->
            ConversationDraft(
                text = draft.text,
                attachments = draftAttachmentsFor(entity.id).map(ConversationDraftAttachmentEntity::toAttachment),
                updatedAt = Instant.ofEpochMilli(draft.updatedAtEpochMs),
                schemaVersion = draft.schemaVersion,
            )
        } ?: error("会话缺少草稿记录：${entity.id}"),
    )
}

private fun ConversationDao.loadNode(entity: MessageNodeEntity): MessageNode = MessageNode(
    id = MessageNodeId(entity.id),
    conversationId = ConversationId(entity.conversationId),
    parentMessageId = entity.parentMessageId?.let(::MessageNodeId),
    siblingPosition = entity.siblingPosition,
    role = MessageRole.valueOf(entity.role),
    content = blocksFor(entity.id).map(MessageContentBlockEntity::toDomain),
    createdAt = Instant.ofEpochMilli(entity.createdAtEpochMs),
    deliveryState = MessageDeliveryState.valueOf(entity.deliveryState),
    revision = MessageRevision(entity.revision, entity.revisesMessageId?.let(::MessageNodeId)),
    invocation = entity.invocationId?.let { MessageInvocationReference(InvocationId(it)) },
    checkpoint = if (entity.lastPersistedSequence == null && entity.resumableFromSequence == null) null else {
        MessageCheckpoint(entity.lastPersistedSequence, entity.resumableFromSequence)
    },
    schemaVersion = entity.schemaVersion,
)

private fun Conversation.toEntity() = ConversationEntity(
    id = id.value,
    title = title,
    surface = surface.name,
    projectId = projectId,
    currentLeafMessageId = currentLeafMessageId?.value,
    createdAtEpochMs = createdAt.toEpochMilli(),
    updatedAtEpochMs = updatedAt.toEpochMilli(),
    defaultProviderId = settings.defaultProviderId?.name,
    defaultModelId = settings.defaultModelId,
    harnessId = settings.harnessId,
    harnessVersion = settings.harnessVersion,
    contextPolicyVersion = settings.contextPolicyVersion,
    archivedAtEpochMs = archivedAt?.toEpochMilli(),
    pinnedAtEpochMs = pinnedAt?.toEpochMilli(),
    deletedAtEpochMs = deletedAt?.toEpochMilli(),
    revision = revision,
    autoTitlePending = autoTitlePending,
    schemaVersion = schemaVersion,
)

private fun ConversationEntity.toDomain(memorySources: List<ConversationMemorySourceEntity>) = Conversation(
    id = ConversationId(id),
    title = title,
    surface = ConversationSurface.valueOf(surface),
    projectId = projectId,
    currentLeafMessageId = currentLeafMessageId?.let(::MessageNodeId),
    createdAt = Instant.ofEpochMilli(createdAtEpochMs),
    updatedAt = Instant.ofEpochMilli(updatedAtEpochMs),
    settings = ConversationSettings(
        defaultProviderId = defaultProviderId?.let(ProviderId::valueOf),
        defaultModelId = defaultModelId,
        harnessId = harnessId,
        harnessVersion = harnessVersion,
        memorySources = memorySources.map { MemorySourceReference(it.memoryId, it.sourceKind, it.sourceVersion) },
        contextPolicyVersion = contextPolicyVersion,
    ),
    archivedAt = archivedAtEpochMs?.let(Instant::ofEpochMilli),
    pinnedAt = pinnedAtEpochMs?.let(Instant::ofEpochMilli),
    deletedAt = deletedAtEpochMs?.let(Instant::ofEpochMilli),
    revision = revision,
    autoTitlePending = autoTitlePending,
    schemaVersion = schemaVersion,
)

private fun MessageNode.toEntity() = MessageNodeEntity(
    id = id.value,
    conversationId = conversationId.value,
    parentMessageId = parentMessageId?.value,
    siblingPosition = siblingPosition,
    role = role.name,
    createdAtEpochMs = createdAt.toEpochMilli(),
    deliveryState = deliveryState.name,
    revision = revision.revision,
    revisesMessageId = revision.revisesMessageId?.value,
    invocationId = invocation?.invocationId?.value,
    lastPersistedSequence = checkpoint?.lastPersistedSequence,
    resumableFromSequence = checkpoint?.resumableFromSequence,
    schemaVersion = schemaVersion,
)

private fun ContentBlock.toEntity(messageId: MessageNodeId, position: Int): MessageContentBlockEntity = when (this) {
    is ContentBlock.Text -> MessageContentBlockEntity(messageId.value, position, "TEXT", text, null, null, null, null, null, null, null, null, schemaVersion)
    is ContentBlock.Attachment -> MessageContentBlockEntity(
        // Schema 7 retains this column for compatibility. P3-G stores only the stable ID here,
        // never an app-private storage key; Attachment Domain resolves the asset separately.
        messageId.value, position, "ATTACHMENT", null, attachment.id.value, attachment.id.value, attachment.mimeType,
        attachment.displayName, attachment.byteCount, attachment.sha256, null, null, schemaVersion,
    )
    is ContentBlock.ToolResult -> MessageContentBlockEntity(
        messageId.value, position, "TOOL_RESULT", null, null, null, null, null, null, null, toolName, safeSummary, schemaVersion,
    )
}

private fun MessageContentBlockEntity.toDomain(): ContentBlock = when (kind) {
    "TEXT" -> ContentBlock.Text(requireNotNull(textContent) { "文本内容块缺少正文。" }, schemaVersion)
    "ATTACHMENT" -> ContentBlock.Attachment(
        ConversationAttachmentReference(
            id = AttachmentId(requireNotNull(attachmentId) { "附件内容块缺少 ID。" }),
            mimeType = requireNotNull(mimeType) { "附件内容块缺少 MIME。" },
            displayName = displayName,
            byteCount = requireNotNull(byteCount) { "附件内容块缺少大小。" },
            sha256 = requireNotNull(sha256) { "附件内容块缺少摘要。" },
        ),
        schemaVersion,
    )
    "TOOL_RESULT" -> ContentBlock.ToolResult(
        requireNotNull(toolName) { "Tool 内容块缺少名称。" },
        requireNotNull(toolSafeSummary) { "Tool 内容块缺少安全摘要。" },
        schemaVersion,
    )
    else -> error("不支持的内容块类型：$kind")
}

private fun ConversationDraft.toEntity(conversationId: ConversationId) = ConversationDraftEntity(
    conversationId = conversationId.value,
    text = text,
    updatedAtEpochMs = updatedAt.toEpochMilli(),
    schemaVersion = schemaVersion,
)

private fun ConversationAttachmentReference.toDraftEntity(conversationId: ConversationId, position: Int) = ConversationDraftAttachmentEntity(
    conversationId = conversationId.value,
    position = position,
    attachmentId = id.value,
    storageKey = id.value,
    mimeType = mimeType,
    displayName = displayName,
    byteCount = byteCount,
    sha256 = sha256,
)

private fun ConversationDraftAttachmentEntity.toAttachment() = ConversationAttachmentReference(
    id = AttachmentId(attachmentId),
    mimeType = mimeType,
    displayName = displayName,
    byteCount = requireNotNull(byteCount) { "对话草稿附件缺少大小。" },
    sha256 = requireNotNull(sha256) { "对话草稿附件缺少摘要。" },
)

private fun <T> RoomDatabase.inConversationTransaction(action: () -> T): T = runInTransaction(Callable { action() })

package com.nanzhufeng.ai.data.local

import androidx.room.RoomDatabase
import com.nanzhufeng.ai.domain.AiTaskError
import com.nanzhufeng.ai.domain.AttachmentId
import com.nanzhufeng.ai.domain.AttachmentReference
import com.nanzhufeng.ai.domain.CandidateId
import com.nanzhufeng.ai.domain.CandidateProvenance
import com.nanzhufeng.ai.domain.CaptureDraft
import com.nanzhufeng.ai.domain.CaptureDraftId
import com.nanzhufeng.ai.domain.CaptureDraftRepository
import com.nanzhufeng.ai.domain.CaptureSourceType
import com.nanzhufeng.ai.domain.InvocationId
import com.nanzhufeng.ai.domain.KnowledgeItem
import com.nanzhufeng.ai.domain.KnowledgeItemId
import com.nanzhufeng.ai.domain.KnowledgeRepository
import com.nanzhufeng.ai.domain.KnowledgeManagementRepository
import com.nanzhufeng.ai.domain.KnowledgeDomain
import com.nanzhufeng.ai.domain.KnowledgeIntent
import com.nanzhufeng.ai.domain.KnowledgeIntentAction
import com.nanzhufeng.ai.domain.KnowledgeLifecycle
import com.nanzhufeng.ai.domain.KnowledgeMutationResult
import com.nanzhufeng.ai.domain.KnowledgeRejectionCode
import com.nanzhufeng.ai.domain.KnowledgeRevision
import com.nanzhufeng.ai.domain.KnowledgeRevisionId
import com.nanzhufeng.ai.domain.KnowledgeScope
import com.nanzhufeng.ai.domain.KnowledgeSearchFilter
import com.nanzhufeng.ai.domain.KnowledgeSnapshot
import com.nanzhufeng.ai.domain.KnowledgeStatus
import com.nanzhufeng.ai.domain.WebTextSnapshotTaskRepository
import com.nanzhufeng.ai.domain.WebTextSnapshotTask
import com.nanzhufeng.ai.domain.WebTextSnapshotTaskId
import com.nanzhufeng.ai.domain.WebTextSnapshotStatus
import com.nanzhufeng.ai.domain.WebTextSnapshotAsset
import com.nanzhufeng.ai.domain.WebTextSnapshotItem
import com.nanzhufeng.ai.domain.WebTextSnapshotItemId
import com.nanzhufeng.ai.domain.WebTextSnapshotItemStatus
import com.nanzhufeng.ai.domain.WebTextSnapshotFailure
import com.nanzhufeng.ai.domain.WEB_TEXT_SNAPSHOT_VERSION
import com.nanzhufeng.ai.domain.KnowledgeRelationshipAction
import com.nanzhufeng.ai.domain.KnowledgeRelationshipIntent
import com.nanzhufeng.ai.domain.KnowledgeRelationshipIntentId
import com.nanzhufeng.ai.domain.KnowledgeRelationshipListFilter
import com.nanzhufeng.ai.domain.KnowledgeRelationshipMutationResult
import com.nanzhufeng.ai.domain.KnowledgeRelationshipRepository
import com.nanzhufeng.ai.domain.KnowledgeRelationshipRevision
import com.nanzhufeng.ai.domain.KnowledgeRelationshipRevisionId
import com.nanzhufeng.ai.domain.KnowledgeRelationshipSnapshot
import com.nanzhufeng.ai.domain.KnowledgeRelationshipStatus
import com.nanzhufeng.ai.domain.KnowledgeRelationshipSuggestionSource
import com.nanzhufeng.ai.domain.KnowledgeRelationshipType
import com.nanzhufeng.ai.domain.KnowledgeRelationship
import com.nanzhufeng.ai.domain.KnowledgeRelationshipId
import com.nanzhufeng.ai.domain.KnowledgeRelationshipRejection
import com.nanzhufeng.ai.domain.RelationshipDecision
import com.nanzhufeng.ai.domain.MemoryDomain
import com.nanzhufeng.ai.domain.ImportTaskId
import com.nanzhufeng.ai.domain.ImportItemId
import com.nanzhufeng.ai.domain.MarkdownImportAsset
import com.nanzhufeng.ai.domain.MarkdownImportFailure
import com.nanzhufeng.ai.domain.MarkdownImportItem
import com.nanzhufeng.ai.domain.MarkdownImportItemStatus
import com.nanzhufeng.ai.domain.MarkdownImportTask
import com.nanzhufeng.ai.domain.MarkdownImportTaskRepository
import com.nanzhufeng.ai.domain.MarkdownImportTaskStatus
import com.nanzhufeng.ai.domain.JsonKnowledgeAsset
import com.nanzhufeng.ai.domain.JsonKnowledgeFailure
import com.nanzhufeng.ai.domain.JsonKnowledgeImportItem
import com.nanzhufeng.ai.domain.JsonKnowledgeImportItemId
import com.nanzhufeng.ai.domain.JsonKnowledgeItemStatus
import com.nanzhufeng.ai.domain.JsonKnowledgeTask
import com.nanzhufeng.ai.domain.JsonKnowledgeTaskId
import com.nanzhufeng.ai.domain.JsonKnowledgeTaskRepository
import com.nanzhufeng.ai.domain.JsonKnowledgeTaskStatus
import com.nanzhufeng.ai.domain.PdfTextImportAsset
import com.nanzhufeng.ai.domain.PdfTextImportFailure
import com.nanzhufeng.ai.domain.PdfTextImportItem
import com.nanzhufeng.ai.domain.PdfTextImportItemId
import com.nanzhufeng.ai.domain.PdfTextImportItemStatus
import com.nanzhufeng.ai.domain.PdfTextImportTask
import com.nanzhufeng.ai.domain.PdfTextImportTaskId
import com.nanzhufeng.ai.domain.PdfTextImportTaskRepository
import com.nanzhufeng.ai.domain.PdfTextImportTaskStatus
import com.nanzhufeng.ai.domain.PdfTextPageArtifact
import com.nanzhufeng.ai.domain.ProjectId
import com.nanzhufeng.ai.domain.ProviderId
import com.nanzhufeng.ai.domain.SourceEvidence
import com.nanzhufeng.ai.domain.isReadyPrivateCopy
import java.time.Instant
import java.time.Clock
import java.util.concurrent.Callable

private const val FIELD_SEPARATOR = "\u001F"

class RoomCaptureDraftRepository(private val database: NanfengAiDatabase) : CaptureDraftRepository {
    override fun save(draft: CaptureDraft): CaptureDraft = database.inTransaction {
        val dao = database.captureDraftDao()
        val existing = dao.findDraft(draft.id.value)
        if (existing == null) {
            dao.insertBundle(
                draft = draft.toEntity(),
                evidence = draft.sourceEvidence.mapIndexed { position, source -> source.toDraftEntity(draft.id, position) },
                attachments = draft.attachments.mapIndexed { position, attachment -> attachment.toDraftEntity(draft.id, position) },
            )
            draft
        } else {
            dao.load(draft.id) ?: throw IllegalStateException(AiTaskError.PersistenceConflict.toString())
        }
    }

    override fun findById(id: CaptureDraftId): CaptureDraft? = database.captureDraftDao().load(id)

    override fun findLatest(): CaptureDraft? = database.captureDraftDao().loadLatest()
}

class RoomKnowledgeRepository(private val database: NanfengAiDatabase, private val clock: Clock = Clock.systemUTC()) : KnowledgeRepository, KnowledgeManagementRepository {
    private val managementDomain = KnowledgeDomain(clock)
    override fun save(item: KnowledgeItem): KnowledgeItem = database.inTransaction {
        val dao = database.knowledgeDao()
        val existing = dao.findKnowledge(item.id.value)
        if (existing == null) {
            require(item.attachments.all { it.isReadyPrivateCopy() }) { AiTaskError.AttachmentNotReady.toString() }
            dao.insertBundle(
                item = item.toEntity(),
                evidence = item.sourceEvidence.mapIndexed { position, source -> source.toKnowledgeEntity(item.id, position) },
                attachments = item.attachments.mapIndexed { position, attachment -> attachment.toKnowledgeEntity(item.id, position) },
            )
            val hash = MemoryDomain.sha256("${item.title.trim().lowercase()}\n${item.body.trim().lowercase()}")
            dao.insertRevision(KnowledgeRevisionEntity("${item.id.value}:revision:1", item.id.value, 1, item.title, item.body, KnowledgeStatus.ACTIVE.name, null, hash, item.createdAt.toEpochMilli()))
            item
        } else {
            dao.load(item.id) ?: throw IllegalStateException(AiTaskError.PersistenceConflict.toString())
        }
    }

    override fun findById(id: KnowledgeItemId): KnowledgeItem? = database.knowledgeDao().load(id)

    override fun listAll(): List<KnowledgeItem> = database.knowledgeDao().listKnowledge().mapNotNull { item ->
        database.knowledgeDao().load(KnowledgeItemId(item.id))
    }

    override fun findSnapshot(id: KnowledgeItemId): KnowledgeSnapshot? = database.knowledgeDao().loadSnapshot(id)

    override fun listSnapshots(filter: KnowledgeSearchFilter): List<KnowledgeSnapshot> = database.knowledgeDao().listKnowledgeIncludingHidden()
        .mapNotNull { database.knowledgeDao().loadSnapshot(KnowledgeItemId(it.id)) }
        .filter { it.lifecycle.status == filter.status }
        .filter { filter.scope == null || it.lifecycle.scope == filter.scope }
        .filter { filter.projectId == null || it.lifecycle.projectId == filter.projectId }
        .filter { filter.sourceType == null || filter.sourceType in it.item.sourceEvidence.map(SourceEvidence::sourceType) }

    override fun mutate(intent: KnowledgeIntent, fingerprint: String): KnowledgeMutationResult = database.inTransaction {
        val dao = database.knowledgeDao(); val current = dao.loadSnapshot(intent.knowledgeId)
        managementDomain.mutate(current, intent)?.let { return@inTransaction it }
        when (intent.action) {
            KnowledgeIntentAction.CREATE_MANUAL, KnowledgeIntentAction.CREATE_MARKDOWN_IMPORT, KnowledgeIntentAction.CREATE_JSON_IMPORT, KnowledgeIntentAction.CREATE_PDF_TEXT_IMPORT, KnowledgeIntentAction.CREATE_WEB_TEXT_SNAPSHOT -> {
                if (current != null) return@inTransaction KnowledgeMutationResult.Rejected(KnowledgeRejectionCode.INVALID_ACTION)
                if (intent.scope == KnowledgeScope.PROJECT && database.projectDao().findProject(requireNotNull(intent.projectId).value) == null) return@inTransaction KnowledgeMutationResult.Rejected(KnowledgeRejectionCode.INVALID_SCOPE)
                val title = managementDomain.normalizedTitle(requireNotNull(intent.title)); val body = managementDomain.normalizedBody(requireNotNull(intent.body)); val tags = managementDomain.normalizedTags(intent.tags); val now = managementDomain.now()
                val item = KnowledgeItem(intent.knowledgeId, title, body, listOf(SourceEvidence(CaptureSourceType.MANUAL_TEXT, now, null, setOf("knowledge"))), CandidateProvenance(CandidateId("manual:${intent.knowledgeId.value}"), InvocationId("manual:${intent.knowledgeId.value}"), ProviderId.MOCK, "local-manual", 0), now, schemaVersion = 2)
                val snapshot = KnowledgeSnapshot(item, KnowledgeLifecycle(KnowledgeStatus.ACTIVE, intent.scope, intent.projectId, tags, managementDomain.contentHash(title, body), now), emptyList()).appendRevision(now)
                persistManaged(dao, snapshot); KnowledgeMutationResult.Applied(requireNotNull(dao.loadSnapshot(intent.knowledgeId)))
            }
            KnowledgeIntentAction.UPDATE -> {
                val existing = current ?: return@inTransaction KnowledgeMutationResult.Rejected(KnowledgeRejectionCode.MISSING_KNOWLEDGE)
                if (existing.lifecycle.status == KnowledgeStatus.DELETED) return@inTransaction KnowledgeMutationResult.Rejected(KnowledgeRejectionCode.MISSING_KNOWLEDGE)
                if (intent.scope == KnowledgeScope.PROJECT && database.projectDao().findProject(requireNotNull(intent.projectId).value) == null) return@inTransaction KnowledgeMutationResult.Rejected(KnowledgeRejectionCode.INVALID_SCOPE)
                val title = managementDomain.normalizedTitle(requireNotNull(intent.title)); val body = managementDomain.normalizedBody(requireNotNull(intent.body)); val tags = managementDomain.normalizedTags(intent.tags); val now = managementDomain.now()
                val next = existing.copy(item = existing.item.copy(title = title, body = body, schemaVersion = 2), lifecycle = existing.lifecycle.copy(scope = intent.scope, projectId = intent.projectId, tags = tags, contentHash = managementDomain.contentHash(title, body), updatedAt = now)).appendRevision(now)
                persistManaged(dao, next); KnowledgeMutationResult.Applied(requireNotNull(dao.loadSnapshot(intent.knowledgeId)))
            }
            KnowledgeIntentAction.ARCHIVE, KnowledgeIntentAction.DELETE, KnowledgeIntentAction.RESTORE, KnowledgeIntentAction.RESTORE_FROM_TRASH -> {
                val existing = current ?: return@inTransaction KnowledgeMutationResult.Rejected(KnowledgeRejectionCode.MISSING_KNOWLEDGE); val now = managementDomain.now()
                val target = when (intent.action) { KnowledgeIntentAction.ARCHIVE -> KnowledgeStatus.ARCHIVED; KnowledgeIntentAction.DELETE -> KnowledgeStatus.DELETED; else -> KnowledgeStatus.ACTIVE }
                if (intent.action == KnowledgeIntentAction.RESTORE && existing.lifecycle.status != KnowledgeStatus.ARCHIVED) return@inTransaction KnowledgeMutationResult.Rejected(KnowledgeRejectionCode.INVALID_ACTION)
                if (intent.action == KnowledgeIntentAction.RESTORE_FROM_TRASH && existing.lifecycle.status != KnowledgeStatus.DELETED) return@inTransaction KnowledgeMutationResult.Rejected(KnowledgeRejectionCode.INVALID_ACTION)
                val next = existing.copy(lifecycle = existing.lifecycle.copy(status = target, updatedAt = now, archivedAt = if (target == KnowledgeStatus.ARCHIVED) now else null, deletedAt = if (target == KnowledgeStatus.DELETED) now else null)).appendRevision(now)
                persistManaged(dao, next); KnowledgeMutationResult.Applied(requireNotNull(dao.loadSnapshot(intent.knowledgeId)))
            }
        }
    }
}

/** Keeps P4-H queue truth in Room. Bodies are parsed, inert content; asset bytes stay outside Room. */
class RoomMarkdownImportTaskRepository(private val database: NanfengAiDatabase) : MarkdownImportTaskRepository {
    override fun save(task: MarkdownImportTask): MarkdownImportTask = database.inTransaction {
        val dao = database.markdownImportTaskDao()
        dao.upsertTask(MarkdownImportTaskEntity(
            id = task.id.value, status = task.status.name, storageKey = task.asset?.storageKey,
            mimeType = task.asset?.mimeType, displayName = task.asset?.displayName, byteCount = task.asset?.byteCount,
            sha256 = task.asset?.sha256, adapterVersion = task.asset?.adapterVersion ?: 1, failure = task.failure?.name,
            retryCount = task.retryCount, createdAtEpochMs = task.createdAt.toEpochMilli(), updatedAtEpochMs = task.updatedAt.toEpochMilli(),
        ))
        dao.clearItems(task.id.value)
        dao.upsertItems(task.items.map { item -> MarkdownImportItemEntity(task.id.value, item.id.value, item.ordinal, item.title, item.body, item.tags.sorted().joinToString("\u001F"), item.status.name, item.failure?.name, item.knowledgeId?.value) })
        task
    }
    override fun find(id: ImportTaskId): MarkdownImportTask? = database.markdownImportTaskDao().task(id.value)?.toDomain(database.markdownImportTaskDao().items(id.value))
    override fun list(): List<MarkdownImportTask> = database.markdownImportTaskDao().all().map { task -> task.toDomain(database.markdownImportTaskDao().items(task.id)) }
}

private fun MarkdownImportTaskEntity.toDomain(items: List<MarkdownImportItemEntity>): MarkdownImportTask = MarkdownImportTask(
    id = ImportTaskId(id), status = MarkdownImportTaskStatus.valueOf(status),
    asset = storageKey?.let { MarkdownImportAsset(it, requireNotNull(mimeType), requireNotNull(displayName), requireNotNull(byteCount), requireNotNull(sha256), adapterVersion) },
    failure = failure?.let(MarkdownImportFailure::valueOf), retryCount = retryCount,
    createdAt = Instant.ofEpochMilli(createdAtEpochMs), updatedAt = Instant.ofEpochMilli(updatedAtEpochMs),
    items = items.map { MarkdownImportItem(ImportItemId(it.id), ImportTaskId(it.taskId), it.ordinal, it.title, it.body, it.tags.split("\u001F").filter(String::isNotEmpty).toSet(), MarkdownImportItemStatus.valueOf(it.status), it.failure?.let(MarkdownImportFailure::valueOf), it.knowledgeId?.let(::KnowledgeItemId)) },
)

/** JSON queue persistence is deliberately separate from P4-H's Markdown queue. */
class RoomJsonKnowledgeTaskRepository(private val database: NanfengAiDatabase) : JsonKnowledgeTaskRepository {
    override fun save(task: JsonKnowledgeTask): JsonKnowledgeTask = database.inTransaction {
        val dao = database.jsonKnowledgeImportTaskDao()
        dao.upsertTask(JsonKnowledgeImportTaskEntity(task.id.value, task.status.name, task.asset?.storageKey, task.asset?.mimeType, task.asset?.displayName, task.asset?.byteCount, task.asset?.sha256, task.asset?.adapterVersion ?: 1, task.failure?.name, task.retryCount, task.createdAt.toEpochMilli(), task.updatedAt.toEpochMilli()))
        dao.clearItems(task.id.value)
        dao.upsertItems(task.items.map { JsonKnowledgeImportItemEntity(task.id.value, it.id.value, it.ordinal, it.sourceStableId, it.title, it.body, it.tags.sorted().joinToString(FIELD_SEPARATOR), it.sourceSummary, it.requestedScope.name, it.status.name, it.failure?.name, it.knowledgeId?.value) })
        task
    }
    override fun find(id: JsonKnowledgeTaskId): JsonKnowledgeTask? = database.jsonKnowledgeImportTaskDao().task(id.value)?.toDomain(database.jsonKnowledgeImportTaskDao().items(id.value))
    override fun list(): List<JsonKnowledgeTask> = database.jsonKnowledgeImportTaskDao().all().map { it.toDomain(database.jsonKnowledgeImportTaskDao().items(it.id)) }
}
private fun JsonKnowledgeImportTaskEntity.toDomain(items: List<JsonKnowledgeImportItemEntity>) = JsonKnowledgeTask(
    JsonKnowledgeTaskId(id), JsonKnowledgeTaskStatus.valueOf(status), storageKey?.let { JsonKnowledgeAsset(it, requireNotNull(mimeType), requireNotNull(displayName), requireNotNull(byteCount), requireNotNull(sha256), adapterVersion) }, failure?.let(JsonKnowledgeFailure::valueOf), retryCount, Instant.ofEpochMilli(createdAtEpochMs), Instant.ofEpochMilli(updatedAtEpochMs),
    items.map { JsonKnowledgeImportItem(JsonKnowledgeImportItemId(it.id), JsonKnowledgeTaskId(it.taskId), it.ordinal, it.sourceStableId, it.title, it.body, it.tags.split(FIELD_SEPARATOR).filter(String::isNotEmpty).toSet(), it.sourceSummary, KnowledgeScope.valueOf(it.requestedScope), JsonKnowledgeItemStatus.valueOf(it.status), it.failure?.let(JsonKnowledgeFailure::valueOf), it.knowledgeId?.let(::KnowledgeItemId)) },
)

/** P6-H persists only the bounded, inert ChatGPT candidate tree. Source paths and grants are absent. */
class RoomChatGptImportTaskRepository(private val database: NanfengAiDatabase) : com.nanzhufeng.ai.domain.ChatGptImportTaskRepository {
    override fun save(task: com.nanzhufeng.ai.domain.ChatGptImportTask): com.nanzhufeng.ai.domain.ChatGptImportTask = database.inTransaction {
        val dao = database.chatGptExportImportTaskDao(); val asset = task.asset
        dao.upsertTask(ChatGptExportImportTaskEntity(task.id.value, task.status.name, asset?.storageKey, asset?.mimeType, asset?.displayName, asset?.byteCount, asset?.packageHash, task.failure?.name, task.retryCount, task.createdAt.toEpochMilli(), task.updatedAt.toEpochMilli()))
        dao.clearMessages(task.id.value); dao.clearItems(task.id.value)
        dao.upsertItems(task.items.map { item -> val c = item.candidate; ChatGptExportImportItemEntity(task.id.value, item.id.value, item.ordinal, c?.sourceConversationId, c?.title, c?.createdAt?.toEpochMilli(), c?.updatedAt?.toEpochMilli(), c?.contentHash, item.status.name, item.failure?.name, item.conversationId?.value) })
        dao.upsertMessages(task.items.flatMap { item -> item.candidate?.messages.orEmpty().map { message -> ChatGptExportImportMessageEntity(task.id.value, item.id.value, message.sourceId, message.parentSourceId, message.siblingPosition, message.role.name, message.text, message.createdAt.toEpochMilli(), message.importedModel) } })
        task
    }
    override fun find(id: com.nanzhufeng.ai.domain.ChatGptImportTaskId): com.nanzhufeng.ai.domain.ChatGptImportTask? = database.chatGptExportImportTaskDao().task(id.value)?.toChatGptDomain(database.chatGptExportImportTaskDao())
    override fun list(): List<com.nanzhufeng.ai.domain.ChatGptImportTask> = database.chatGptExportImportTaskDao().all().map { it.toChatGptDomain(database.chatGptExportImportTaskDao()) }
}
private fun ChatGptExportImportTaskEntity.toChatGptDomain(dao: ChatGptExportImportTaskDao): com.nanzhufeng.ai.domain.ChatGptImportTask {
    val taskId = com.nanzhufeng.ai.domain.ChatGptImportTaskId(id)
    return com.nanzhufeng.ai.domain.ChatGptImportTask(taskId, com.nanzhufeng.ai.domain.ChatGptImportTaskStatus.valueOf(status), storageKey?.let { com.nanzhufeng.ai.domain.ChatGptImportAsset(it, requireNotNull(mimeType), requireNotNull(displayName), requireNotNull(byteCount), requireNotNull(packageHash)) }, failure?.let(com.nanzhufeng.ai.domain.ChatGptImportFailure::valueOf), retryCount, Instant.ofEpochMilli(createdAtEpochMs), Instant.ofEpochMilli(updatedAtEpochMs), dao.items(id).map { item ->
        val itemId = com.nanzhufeng.ai.domain.ChatGptImportItemId(item.id)
        val candidate = item.sourceConversationId?.let { source -> com.nanzhufeng.ai.domain.ChatGptImportCandidate(source, requireNotNull(item.title), Instant.ofEpochMilli(requireNotNull(item.createdAtEpochMs)), Instant.ofEpochMilli(requireNotNull(item.updatedAtEpochMs)), dao.messages(id, item.id).map { m -> com.nanzhufeng.ai.domain.ChatGptImportMessage(m.sourceMessageId, m.parentSourceMessageId, m.siblingPosition, com.nanzhufeng.ai.domain.MessageRole.valueOf(m.role), m.text, Instant.ofEpochMilli(m.createdAtEpochMs), m.importedModel) }, requireNotNull(item.contentHash)) }
        com.nanzhufeng.ai.domain.ChatGptImportItem(itemId, taskId, item.ordinal, candidate, com.nanzhufeng.ai.domain.ChatGptImportItemStatus.valueOf(item.status), item.failure?.let(com.nanzhufeng.ai.domain.ChatGptImportFailure::valueOf), item.conversationId?.let { com.nanzhufeng.ai.domain.ConversationId(it) })
    })
}

/** P6-K persists only private-archive metadata and inert candidates. It has no Conversation commit port. */
class RoomP6KZipImportTaskRepository(private val database: NanfengAiDatabase) : com.nanzhufeng.ai.domain.P6KZipImportTaskRepository {
    override fun save(task: com.nanzhufeng.ai.domain.P6KZipImportTask): com.nanzhufeng.ai.domain.P6KZipImportTask = database.inTransaction {
        val dao = database.p6kZipImportTaskDao()
        dao.upsertTask(P6KZipImportTaskEntity(task.id.value, task.provider.name, task.displayName, task.byteCount, task.packageHash, task.status.name, task.failure?.name, task.formatVersion, task.createdAt.toEpochMilli(), task.updatedAt.toEpochMilli()))
        dao.clearMessages(task.id.value); dao.clearItems(task.id.value); dao.clearAssets(task.id.value); dao.clearProfile(task.id.value)
        dao.upsertItems(task.items.map { item -> item.candidate.let { c -> P6KZipImportItemEntity(task.id.value, item.id.value, item.ordinal, c?.sourceConversationId, c?.title, c?.createdAt?.toEpochMilli(), c?.updatedAt?.toEpochMilli(), c?.contentHash, item.status.name, item.failure, item.conversationId?.value) } })
        dao.upsertMessages(task.items.flatMap { item -> item.candidate?.messages.orEmpty().map { message -> P6KZipImportMessageEntity(task.id.value, item.id.value, message.sourceId, message.parentSourceId, message.siblingPosition, message.role.name, message.text, message.createdAt.toEpochMilli(), message.importedModel) } })
        dao.upsertAssets(task.assets.map { asset -> P6KZipAssetCandidateEntity(task.id.value, asset.entryName, asset.sha256, asset.byteCount, asset.mimeType, asset.role.name, asset.sourceConversationId, asset.sourceMessageId, asset.linkedConversationId, asset.linkedMessageId, asset.attachmentId, asset.failure) })
        dao.upsertProfile(P6KZipProfileCandidateEntity(task.id.value, task.profile.status, task.profile.mappedFieldCount))
        task
    }
    override fun find(id: com.nanzhufeng.ai.domain.P6KZipTaskId): com.nanzhufeng.ai.domain.P6KZipImportTask? = database.p6kZipImportTaskDao().task(id.value)?.toP6KZipDomain(database.p6kZipImportTaskDao())
    override fun list(): List<com.nanzhufeng.ai.domain.P6KZipImportTask> = database.p6kZipImportTaskDao().all().map { it.toP6KZipDomain(database.p6kZipImportTaskDao()) }
    override fun delete(id: com.nanzhufeng.ai.domain.P6KZipTaskId) = database.inTransaction {
        val dao = database.p6kZipImportTaskDao(); dao.deleteAssetRecoveryJob(id.value); dao.clearMessages(id.value); dao.clearItems(id.value); dao.clearAssets(id.value); dao.clearProfile(id.value); dao.deleteTask(id.value)
    }
}
private fun P6KZipImportTaskEntity.toP6KZipDomain(dao: P6KZipImportTaskDao): com.nanzhufeng.ai.domain.P6KZipImportTask {
    val taskId = com.nanzhufeng.ai.domain.P6KZipTaskId(id)
    val items = dao.items(id).map { item ->
        val candidate = item.sourceConversationId?.let { source -> com.nanzhufeng.ai.domain.ChatGptImportCandidate(source, requireNotNull(item.title), Instant.ofEpochMilli(requireNotNull(item.createdAtEpochMs)), Instant.ofEpochMilli(requireNotNull(item.updatedAtEpochMs)), dao.messages(id, item.id).map { message -> com.nanzhufeng.ai.domain.ChatGptImportMessage(message.sourceMessageId, message.parentSourceMessageId, message.siblingPosition, com.nanzhufeng.ai.domain.MessageRole.valueOf(message.role), message.text, Instant.ofEpochMilli(message.createdAtEpochMs), message.importedModel) }, requireNotNull(item.contentHash)) }
        com.nanzhufeng.ai.domain.P6KZipImportItem(com.nanzhufeng.ai.domain.P6KZipItemId(item.id), item.ordinal, candidate, com.nanzhufeng.ai.domain.P6KZipItemStatus.valueOf(item.status), item.failure, item.conversationId?.let { com.nanzhufeng.ai.domain.ConversationId(it) })
    }
    return com.nanzhufeng.ai.domain.P6KZipImportTask(taskId, com.nanzhufeng.ai.domain.ThirdPartyZipProvider.valueOf(provider), displayName, byteCount, packageHash, com.nanzhufeng.ai.domain.P6KZipTaskStatus.valueOf(status), failure?.let(com.nanzhufeng.ai.domain.P6KZipCandidateFailure::valueOf), formatVersion, Instant.ofEpochMilli(createdAtEpochMs), Instant.ofEpochMilli(updatedAtEpochMs), items, dao.assets(id).map { asset -> com.nanzhufeng.ai.domain.P6KZipAssetCandidate(asset.entryName, asset.sha256, asset.byteCount, asset.mimeType, com.nanzhufeng.ai.domain.P6KZipAssetRole.valueOf(asset.role), asset.sourceConversationId, asset.sourceMessageId, asset.linkedConversationId, asset.linkedMessageId, asset.attachmentId, asset.failure) }, dao.profile(id)?.let { com.nanzhufeng.ai.domain.P6KImportedProfileCandidate(it.status, it.mappedFieldCount) } ?: com.nanzhufeng.ai.domain.P6KImportedProfileCandidate())
}

class RoomP6KZipAssetRecoveryJobRepository(
    private val database: NanfengAiDatabase,
) : com.nanzhufeng.ai.domain.P6KZipAssetRecoveryJobRepository {
    private val dao get() = database.p6kZipImportTaskDao()
    override fun save(job: com.nanzhufeng.ai.domain.P6KZipAssetRecoveryJob): com.nanzhufeng.ai.domain.P6KZipAssetRecoveryJob {
        dao.upsertAssetRecoveryJob(job.toEntity())
        return job
    }
    override fun find(taskId: com.nanzhufeng.ai.domain.P6KZipTaskId) = dao.assetRecoveryJob(taskId.value)?.toDomain()
    override fun list() = dao.assetRecoveryJobs().map(P6KZipAssetRecoveryJobEntity::toDomain)
    override fun resumable() = dao.resumableAssetRecoveryJobs().map(P6KZipAssetRecoveryJobEntity::toDomain)
    override fun delete(taskId: com.nanzhufeng.ai.domain.P6KZipTaskId) { dao.deleteAssetRecoveryJob(taskId.value) }
}

internal fun com.nanzhufeng.ai.domain.P6KZipAssetRecoveryJob.toEntity() = P6KZipAssetRecoveryJobEntity(
    taskId.value, state.name, totalOccurrences, linkedOccurrences, totalConversations,
    processedConversations, failedConversations, uniqueAssets, missingEntries,
    unattributedCandidates, sourceReferenceRecords, fallbackNamedAssets,
    lastFailureKind?.name, lastFailureAtMs, indexVersion, updatedAtMs,
)

internal fun P6KZipAssetRecoveryJobEntity.toDomain() = com.nanzhufeng.ai.domain.P6KZipAssetRecoveryJob(
    com.nanzhufeng.ai.domain.P6KZipTaskId(taskId),
    com.nanzhufeng.ai.domain.P6KZipAssetRecoveryState.valueOf(state),
    totalOccurrences, linkedOccurrences, totalConversations, processedConversations,
    failedConversations, uniqueAssets, missingEntries, unattributedCandidates,
    sourceReferenceRecords, fallbackNamedAssets,
    lastFailureKind?.let(com.nanzhufeng.ai.domain.P6KZipAssetRecoveryFailureKind::valueOf),
    lastFailureAtMs, indexVersion, updatedAtMs,
)

/**
 * The only Android owner for K6's safe personalization projection.  It deliberately stores no
 * provider account or raw profile document: only canonical allowlisted values plus enough local
 * receipt/provenance to replay, reject conflicts and revoke one import batch.
 */
class RoomP6KProfilePersonalizationSettingsOwner(
    private val database: NanfengAiDatabase,
) : com.nanzhufeng.ai.domain.P6KProfilePersonalizationSettingsOwner {
    override fun commit(
        task: com.nanzhufeng.ai.domain.P6KZipImportTask,
        value: com.nanzhufeng.ai.domain.ThirdPartyProfilePersonalization,
        at: Instant,
    ): com.nanzhufeng.ai.domain.P6KProfileCommitResult = runCatching {
        database.inTransaction {
            val owner = database.p6kProfilePersonalizationSettingsDao()
            val taskDao = database.p6kZipImportTaskDao()
            val hash = value.canonicalHash()
            owner.receipt(task.provider.name, task.packageHash)?.let { receipt ->
                if (receipt.canonicalHash != hash) {
                    taskDao.updateProfileStatus(task.id.value, "CONFLICT_PROFILE_REIMPORT")
                    return@inTransaction com.nanzhufeng.ai.domain.P6KProfileCommitResult.Conflict
                }
                taskDao.updateProfileStatus(task.id.value, "OWNER_COMMITTED")
                return@inTransaction com.nanzhufeng.ai.domain.P6KProfileCommitResult.Replayed
            }
            val current = owner.current()
            if (current != null && current.sourceTaskId != task.id.value) {
                taskDao.updateProfileStatus(task.id.value, "CONFLICT_PROFILE_OWNER")
                return@inTransaction com.nanzhufeng.ai.domain.P6KProfileCommitResult.Conflict
            }
            val revision = (current?.revision ?: 0L) + 1L
            owner.save(ThirdPartyProfilePersonalizationSettingsEntity(
                displayName = value.displayName, language = value.language, timezone = value.timezone,
                publicBio = value.publicBio, customInstructions = value.customInstructions, theme = value.theme,
                notificationsEnabled = value.notificationsEnabled, sourceTaskId = task.id.value,
                revision = revision, updatedAtEpochMs = at.toEpochMilli(),
            ))
            owner.insertProvenance(P6KProfileImportProvenanceEntity(task.id.value, task.provider.name, task.packageHash, hash, at.toEpochMilli()))
            owner.insertReceipt(P6KProfileImportReceiptEntity(task.provider.name, task.packageHash, task.id.value, hash, at.toEpochMilli()))
            check(taskDao.updateProfileStatus(task.id.value, "OWNER_COMMITTED") == 1)
            com.nanzhufeng.ai.domain.P6KProfileCommitResult.Committed
        }
    }.getOrDefault(com.nanzhufeng.ai.domain.P6KProfileCommitResult.Failed)

    override fun revokeBatch(task: com.nanzhufeng.ai.domain.P6KZipImportTask, at: Instant): Boolean = runCatching {
        database.inTransaction {
            val owner = database.p6kProfilePersonalizationSettingsDao()
            owner.revokeCurrentForTask(task.id.value)
            owner.deleteReceiptsForTask(task.id.value)
            owner.deleteProvenanceForTask(task.id.value)
            true
        }
    }.getOrDefault(false)

    override fun read(): com.nanzhufeng.ai.domain.ThirdPartyProfilePersonalization? =
        database.p6kProfilePersonalizationSettingsDao().current()?.let {
            com.nanzhufeng.ai.domain.ThirdPartyProfilePersonalization(
                it.displayName, it.language, it.timezone, it.publicBio, it.customInstructions, it.theme, it.notificationsEnabled,
            )
        }
}

/** P6-I persists only inert, already-private Claude candidates. */
class RoomClaudeImportTaskRepository(private val database: NanfengAiDatabase) : com.nanzhufeng.ai.domain.ClaudeImportTaskRepository {
    override fun save(task: com.nanzhufeng.ai.domain.ClaudeImportTask): com.nanzhufeng.ai.domain.ClaudeImportTask = database.inTransaction {
        val dao = database.claudeExportImportTaskDao(); val asset = task.asset
        dao.upsertTask(ClaudeExportImportTaskEntity(task.id.value, task.status.name, asset?.storageKey, asset?.mimeType, asset?.displayName, asset?.byteCount, asset?.packageHash, task.failure?.name, task.retryCount, task.createdAt.toEpochMilli(), task.updatedAt.toEpochMilli()))
        dao.clearMessages(task.id.value); dao.clearItems(task.id.value)
        dao.upsertItems(task.items.map { item -> val c = item.candidate; ClaudeExportImportItemEntity(task.id.value, item.id.value, item.ordinal, c?.sourceConversationId, c?.title, c?.createdAt?.toEpochMilli(), c?.updatedAt?.toEpochMilli(), c?.contentHash, item.status.name, item.failure?.name, item.conversationId?.value) })
        dao.upsertMessages(task.items.flatMap { item -> item.candidate?.messages.orEmpty().map { m -> ClaudeExportImportMessageEntity(task.id.value, item.id.value, m.sourceId, m.parentSourceId, m.siblingPosition, m.role.name, m.text, m.createdAt.toEpochMilli()) } })
        task
    }
    override fun find(id: com.nanzhufeng.ai.domain.ClaudeImportTaskId): com.nanzhufeng.ai.domain.ClaudeImportTask? = database.claudeExportImportTaskDao().task(id.value)?.toClaudeDomain(database.claudeExportImportTaskDao())
    override fun list(): List<com.nanzhufeng.ai.domain.ClaudeImportTask> = database.claudeExportImportTaskDao().all().map { it.toClaudeDomain(database.claudeExportImportTaskDao()) }
}
private fun ClaudeExportImportTaskEntity.toClaudeDomain(dao: ClaudeExportImportTaskDao): com.nanzhufeng.ai.domain.ClaudeImportTask {
    val taskId = com.nanzhufeng.ai.domain.ClaudeImportTaskId(id)
    return com.nanzhufeng.ai.domain.ClaudeImportTask(taskId, com.nanzhufeng.ai.domain.ClaudeImportTaskStatus.valueOf(status), storageKey?.let { com.nanzhufeng.ai.domain.ClaudeImportAsset(it, requireNotNull(mimeType), requireNotNull(displayName), requireNotNull(byteCount), requireNotNull(packageHash)) }, failure?.let(com.nanzhufeng.ai.domain.ClaudeExportParseFailure::valueOf), retryCount, Instant.ofEpochMilli(createdAtEpochMs), Instant.ofEpochMilli(updatedAtEpochMs), dao.items(id).map { item ->
        val itemId = com.nanzhufeng.ai.domain.ClaudeImportItemId(item.id)
        val candidate = item.sourceConversationId?.let { source -> com.nanzhufeng.ai.domain.ClaudeExportCandidate(source, requireNotNull(item.title), Instant.ofEpochMilli(requireNotNull(item.createdAtEpochMs)), Instant.ofEpochMilli(requireNotNull(item.updatedAtEpochMs)), dao.messages(id, item.id).map { m -> com.nanzhufeng.ai.domain.ClaudeExportMessage(m.sourceMessageId, m.parentSourceMessageId, m.siblingPosition, com.nanzhufeng.ai.domain.MessageRole.valueOf(m.role), m.text, Instant.ofEpochMilli(m.createdAtEpochMs)) }, requireNotNull(item.contentHash)) }
        com.nanzhufeng.ai.domain.ClaudeImportItem(itemId, taskId, item.ordinal, candidate, com.nanzhufeng.ai.domain.ClaudeImportItemStatus.valueOf(item.status), item.failure?.let(com.nanzhufeng.ai.domain.ClaudeExportParseFailure::valueOf), item.conversationId?.let { com.nanzhufeng.ai.domain.ConversationId(it) })
    })
}

/** P4-N persists source asset, page extraction evidence and candidates in separate PDF-only tables. */
class RoomPdfTextImportTaskRepository(private val database: NanfengAiDatabase) : PdfTextImportTaskRepository {
    override fun save(task: PdfTextImportTask): PdfTextImportTask = database.inTransaction {
        val dao = database.pdfTextImportTaskDao()
        dao.upsertTask(PdfTextImportTaskEntity(task.id.value, task.status.name, task.asset?.storageKey, task.asset?.mimeType, task.asset?.displayName, task.asset?.byteCount, task.asset?.sourceSha256, task.asset?.adapterVersion ?: 1, task.pageCount, task.extractedPageCount, task.extractionSha256, task.failure?.name, task.retryCount, task.createdAt.toEpochMilli(), task.updatedAt.toEpochMilli()))
        dao.clearPages(task.id.value); dao.clearItems(task.id.value)
        dao.upsertPages(task.pages.map { PdfTextImportPageEntity(task.id.value, it.pageNumber, it.textSha256, it.codePointCount, it.extractionVersion) })
        dao.upsertItems(task.items.map { PdfTextImportItemEntity(task.id.value, it.id.value, it.ordinal, it.pageNumber, it.title, it.body, it.tags.sorted().joinToString(FIELD_SEPARATOR), it.candidateSha256, it.status.name, it.failure?.name, it.knowledgeId?.value) })
        task
    }
    override fun find(id: PdfTextImportTaskId): PdfTextImportTask? = database.pdfTextImportTaskDao().task(id.value)?.toDomain(database.pdfTextImportTaskDao().pages(id.value), database.pdfTextImportTaskDao().items(id.value))
    override fun list(): List<PdfTextImportTask> = database.pdfTextImportTaskDao().all().map { it.toDomain(database.pdfTextImportTaskDao().pages(it.id), database.pdfTextImportTaskDao().items(it.id)) }
}
private fun PdfTextImportTaskEntity.toDomain(pages: List<PdfTextImportPageEntity>, items: List<PdfTextImportItemEntity>) = PdfTextImportTask(
    PdfTextImportTaskId(id), PdfTextImportTaskStatus.valueOf(status), storageKey?.let { PdfTextImportAsset(it, requireNotNull(mimeType), requireNotNull(displayName), requireNotNull(byteCount), requireNotNull(sourceSha256), adapterVersion) }, pageCount, extractedPageCount, extractionSha256, failure?.let(PdfTextImportFailure::valueOf), retryCount, Instant.ofEpochMilli(createdAtEpochMs), Instant.ofEpochMilli(updatedAtEpochMs),
    pages.map { PdfTextPageArtifact(it.pageNumber, it.textSha256, it.codePointCount, it.extractionVersion) },
    items.map { PdfTextImportItem(PdfTextImportItemId(it.id), PdfTextImportTaskId(it.taskId), it.ordinal, it.pageNumber, it.title, it.body, it.tags.split(FIELD_SEPARATOR).filter(String::isNotEmpty).toSet(), it.candidateSha256, PdfTextImportItemStatus.valueOf(it.status), it.failure?.let(PdfTextImportFailure::valueOf), it.knowledgeId?.let(::KnowledgeItemId)) },
)

/** P4-O persistence deliberately has no URI query, DNS/IP, header or credential columns. */
class RoomWebTextSnapshotTaskRepository(private val database: NanfengAiDatabase) : WebTextSnapshotTaskRepository {
    override fun save(task: WebTextSnapshotTask): WebTextSnapshotTask = database.inTransaction {
        val dao = database.webTextSnapshotTaskDao(); val a = task.asset
        dao.upsertTask(WebTextSnapshotTaskEntity(task.id.value, task.status.name, task.requestedUrl, a?.storageKey, a?.displayUrl, a?.host, a?.rawHtmlSha256, a?.byteCount, a?.adapterVersion ?: WEB_TEXT_SNAPSHOT_VERSION, task.extractedSha256, task.failure?.name, task.retryCount, task.createdAt.toEpochMilli(), task.updatedAt.toEpochMilli()))
        dao.clearItems(task.id.value); dao.upsertItems(task.items.map { WebTextSnapshotItemEntity(task.id.value, it.id.value, it.ordinal, it.title, it.body, it.candidateSha256, it.status.name, it.failure?.name, it.knowledgeId?.value) }); task
    }
    override fun find(id: WebTextSnapshotTaskId): WebTextSnapshotTask? = database.webTextSnapshotTaskDao().task(id.value)?.toWebDomain(database.webTextSnapshotTaskDao().items(id.value))
    override fun list(): List<WebTextSnapshotTask> = database.webTextSnapshotTaskDao().all().map { it.toWebDomain(database.webTextSnapshotTaskDao().items(it.id)) }
}
private fun WebTextSnapshotTaskEntity.toWebDomain(items: List<WebTextSnapshotItemEntity>) = WebTextSnapshotTask(
    WebTextSnapshotTaskId(id), WebTextSnapshotStatus.valueOf(status), requestedUrl,
    storageKey?.let { WebTextSnapshotAsset(it, requireNotNull(displayUrl), requireNotNull(host), requireNotNull(rawHtmlSha256), requireNotNull(byteCount), adapterVersion) }, extractedSha256, failure?.let(WebTextSnapshotFailure::valueOf), retryCount, Instant.ofEpochMilli(createdAtEpochMs), Instant.ofEpochMilli(updatedAtEpochMs),
    items.map { WebTextSnapshotItem(WebTextSnapshotItemId(it.id), WebTextSnapshotTaskId(it.taskId), it.ordinal, it.title, it.body, it.candidateSha256, WebTextSnapshotItemStatus.valueOf(it.status), it.failure?.let(WebTextSnapshotFailure::valueOf), it.knowledgeId?.let(::KnowledgeItemId)) },
)

/** Room owns atomic relationship writes; semantic validation and endpoint canonicalization remain in KnowledgeRelationshipDomain. */
class RoomKnowledgeRelationshipRepository(private val database: NanfengAiDatabase, private val clock: Clock = Clock.systemUTC()) : KnowledgeRelationshipRepository {
    override fun apply(
        intent: KnowledgeRelationshipIntent,
        fingerprint: String,
        decide: (snapshots: List<KnowledgeSnapshot>, existing: List<KnowledgeRelationshipSnapshot>) -> RelationshipDecision,
    ): KnowledgeRelationshipMutationResult = database.inTransaction {
        val dao = database.knowledgeRelationshipDao()
        dao.intent(intent.id.value)?.let { previous ->
            if (previous.requestFingerprint != fingerprint) return@inTransaction KnowledgeRelationshipMutationResult.Rejected(KnowledgeRelationshipRejection.INVALID_INTENT)
            val relationship = previous.relationshipId?.let(dao::find)?.let(dao::loadSnapshot)
                ?: return@inTransaction KnowledgeRelationshipMutationResult.Rejected(KnowledgeRelationshipRejection.MISSING_RELATIONSHIP)
            return@inTransaction KnowledgeRelationshipMutationResult.Replayed(relationship)
        }
        val snapshots = database.knowledgeDao().listKnowledgeIncludingHidden().mapNotNull { database.knowledgeDao().loadSnapshot(KnowledgeItemId(it.id)) }
        val existing = dao.all().map(dao::loadSnapshot)
        when (val decision = decide(snapshots, existing)) {
            is RelationshipDecision.Rejected -> KnowledgeRelationshipMutationResult.Rejected(decision.code)
            is RelationshipDecision.Confirm -> {
                val key = "${decision.type.name}|${decision.from.value}|${decision.to.value}"
                val prior = dao.findByKey(key)
                val relationshipId = prior?.id ?: KnowledgeRelationshipId.new().value
                val createdAt = prior?.createdAtEpochMs ?: decision.at.toEpochMilli()
                if (prior == null) {
                    dao.insert(KnowledgeRelationshipEntity(
                        id = relationshipId, relationshipKey = key, type = decision.type.name,
                        fromKnowledgeId = decision.from.value, toKnowledgeId = decision.to.value,
                        scopeKind = decision.scope.name, projectId = decision.projectId?.value,
                        status = KnowledgeRelationshipStatus.ACTIVE.name, createdAtEpochMs = createdAt,
                        updatedAtEpochMs = decision.at.toEpochMilli(), createdByIntentId = intent.id.value,
                        latestIntentId = intent.id.value, suggestionSource = intent.suggestionSource.name,
                    ))
                } else {
                    dao.update(relationshipId, KnowledgeRelationshipStatus.ACTIVE.name, decision.at.toEpochMilli(), intent.id.value, intent.suggestionSource.name)
                }
                dao.insertRevision(KnowledgeRelationshipRevisionEntity(
                    id = KnowledgeRelationshipRevisionId.new().value, relationshipId = relationshipId,
                    revision = dao.revisions(relationshipId).size + 1, action = KnowledgeRelationshipAction.CONFIRM.name,
                    status = KnowledgeRelationshipStatus.ACTIVE.name, intentId = intent.id.value, createdAtEpochMs = decision.at.toEpochMilli(),
                ))
                dao.insertIntent(KnowledgeRelationshipIntentEntity(intent.id.value, relationshipId, intent.action.name, fingerprint, decision.at.toEpochMilli()))
                KnowledgeRelationshipMutationResult.Applied(requireNotNull(dao.find(relationshipId)).let(dao::loadSnapshot))
            }
            is RelationshipDecision.Revoke -> {
                val relationship = decision.existing.relationship
                if (relationship.status == KnowledgeRelationshipStatus.REVOKED) return@inTransaction KnowledgeRelationshipMutationResult.Rejected(KnowledgeRelationshipRejection.INVALID_INTENT)
                dao.update(relationship.id.value, KnowledgeRelationshipStatus.REVOKED.name, decision.at.toEpochMilli(), intent.id.value, relationship.suggestionSource.name)
                dao.insertRevision(KnowledgeRelationshipRevisionEntity(
                    id = KnowledgeRelationshipRevisionId.new().value, relationshipId = relationship.id.value,
                    revision = decision.existing.revisions.size + 1, action = KnowledgeRelationshipAction.REVOKE.name,
                    status = KnowledgeRelationshipStatus.REVOKED.name, intentId = intent.id.value, createdAtEpochMs = decision.at.toEpochMilli(),
                ))
                dao.insertIntent(KnowledgeRelationshipIntentEntity(intent.id.value, relationship.id.value, intent.action.name, fingerprint, decision.at.toEpochMilli()))
                KnowledgeRelationshipMutationResult.Applied(requireNotNull(dao.find(relationship.id.value)).let(dao::loadSnapshot))
            }
        }
    }

    override fun list(filter: KnowledgeRelationshipListFilter): List<KnowledgeRelationshipSnapshot> = database.knowledgeRelationshipDao().all()
        .asSequence().map(database.knowledgeRelationshipDao()::loadSnapshot)
        .filter { filter.status == null || it.relationship.status == filter.status }
        .filter { filter.type == null || it.relationship.type == filter.type }
        .filter { filter.endpointId == null || it.relationship.fromKnowledgeId == filter.endpointId || it.relationship.toKnowledgeId == filter.endpointId }
        .toList()
}

private fun CaptureDraft.toEntity() = CaptureDraftEntity(
    id = id.value,
    text = text,
    createdAtEpochMs = createdAt.toEpochMilli(),
    schemaVersion = schemaVersion,
)

private fun SourceEvidence.toDraftEntity(draftId: CaptureDraftId, position: Int) = CaptureDraftEvidenceEntity(
    draftId = draftId.value,
    position = position,
    sourceType = sourceType.name,
    receivedAtEpochMs = receivedAt.toEpochMilli(),
    sourceReference = sourceReference.cleanSourceReference(),
    contributedFields = contributedFields.encodeFields(),
)

private fun AttachmentReference.toDraftEntity(draftId: CaptureDraftId, position: Int) = CaptureDraftAttachmentEntity(
    draftId = draftId.value,
    position = position,
    attachmentId = id.value,
    storageKey = reference,
    mimeType = mimeType,
    displayName = displayName,
    byteCount = byteCount,
    sha256 = sha256,
)

private fun KnowledgeItem.toEntity() = KnowledgeItemEntity(
    id = id.value,
    title = title,
    body = body,
    candidateId = provenance.candidateId.value,
    invocationId = provenance.invocationId.value,
    providerId = provenance.providerId.name,
    modelId = provenance.modelId,
    harnessVersion = provenance.harnessVersion,
    createdAtEpochMs = createdAt.toEpochMilli(),
    schemaVersion = schemaVersion,
    status = "ACTIVE", updatedAtEpochMs = createdAt.toEpochMilli(), contentHash = MemoryDomain.sha256("${title.trim().lowercase()}\n${body.trim().lowercase()}"),
)

private fun SourceEvidence.toKnowledgeEntity(knowledgeId: KnowledgeItemId, position: Int) = KnowledgeEvidenceEntity(
    knowledgeId = knowledgeId.value,
    position = position,
    sourceType = sourceType.name,
    receivedAtEpochMs = receivedAt.toEpochMilli(),
    sourceReference = sourceReference.cleanSourceReference(),
    contributedFields = contributedFields.encodeFields(),
)

private fun AttachmentReference.toKnowledgeEntity(knowledgeId: KnowledgeItemId, position: Int): KnowledgeAttachmentEntity {
    require(isReadyPrivateCopy()) { AiTaskError.AttachmentNotReady.toString() }
    return KnowledgeAttachmentEntity(
        knowledgeId = knowledgeId.value,
        position = position,
        attachmentId = id.value,
        storageKey = reference,
        mimeType = mimeType,
        displayName = displayName,
        byteCount = requireNotNull(byteCount),
        sha256 = requireNotNull(sha256),
    )
}

private fun CaptureDraftDao.load(id: CaptureDraftId): CaptureDraft? = findDraft(id.value)?.let { draft ->
    CaptureDraft(
        id = CaptureDraftId(draft.id),
        text = draft.text,
        attachments = attachmentsFor(draft.id).map { attachment -> attachment.toDomain() },
        sourceEvidence = evidenceFor(draft.id).map { evidence -> evidence.toDomain() },
        createdAt = Instant.ofEpochMilli(draft.createdAtEpochMs),
        schemaVersion = draft.schemaVersion,
    )
}

private fun CaptureDraftDao.loadLatest(): CaptureDraft? = findLatestDraft()?.let { draft ->
    load(CaptureDraftId(draft.id))
}

private fun KnowledgeDao.load(id: KnowledgeItemId): KnowledgeItem? = findKnowledge(id.value)?.let { item ->
    KnowledgeItem(
        id = KnowledgeItemId(item.id),
        title = item.title,
        body = item.body,
        sourceEvidence = evidenceFor(item.id).map { evidence -> evidence.toDomain() },
        provenance = CandidateProvenance(
            candidateId = CandidateId(item.candidateId),
            invocationId = InvocationId(item.invocationId),
            providerId = ProviderId.valueOf(item.providerId),
            modelId = item.modelId,
            harnessVersion = item.harnessVersion,
        ),
        createdAt = Instant.ofEpochMilli(item.createdAtEpochMs),
        attachments = attachmentsFor(item.id).map { attachment -> attachment.toDomain() },
        schemaVersion = item.schemaVersion,
    )
}

private fun KnowledgeDao.loadSnapshot(id: KnowledgeItemId): KnowledgeSnapshot? = findKnowledge(id.value)?.let { entity ->
    val item = requireNotNull(load(id)); val tags = tagsFor(entity.id).toSet(); val hash = entity.contentHash.ifBlank { MemoryDomain.sha256("${item.title.trim().lowercase()}\n${item.body.trim().lowercase()}") }
    val scopeProject = projectScopeFor(entity.id)?.let(::ProjectId); val lifecycle = KnowledgeLifecycle(KnowledgeStatus.valueOf(entity.status), if (scopeProject == null) KnowledgeScope.GLOBAL else KnowledgeScope.PROJECT, scopeProject, tags, hash, Instant.ofEpochMilli(entity.updatedAtEpochMs), entity.archivedAtEpochMs?.let(Instant::ofEpochMilli), entity.deletedAtEpochMs?.let(Instant::ofEpochMilli))
    val revisions = revisionsFor(entity.id).map { revision -> KnowledgeRevision(KnowledgeRevisionId(revision.id), id, revision.revision, revision.title, revision.body, KnowledgeStatus.valueOf(revision.status), if (revision.projectId == null) KnowledgeScope.GLOBAL else KnowledgeScope.PROJECT, revision.projectId?.let(::ProjectId), tagsForRevision(revision.id).toSet(), revision.contentHash.ifBlank { MemoryDomain.sha256("${revision.title.trim().lowercase()}\n${revision.body.trim().lowercase()}") }, Instant.ofEpochMilli(revision.createdAtEpochMs)) }
    KnowledgeSnapshot(item, lifecycle, revisions.ifEmpty { listOf(KnowledgeRevision(KnowledgeRevisionId("${id.value}:revision:legacy"), id, 1, item.title, item.body, lifecycle.status, lifecycle.scope, lifecycle.projectId, tags, hash, item.createdAt)) })
}

private fun KnowledgeSnapshot.appendRevision(at: Instant): KnowledgeSnapshot {
    val revision = KnowledgeRevision(KnowledgeRevisionId.new(), item.id, (revisions.maxOfOrNull { it.revision } ?: 0) + 1, item.title, item.body, lifecycle.status, lifecycle.scope, lifecycle.projectId, lifecycle.tags, lifecycle.contentHash, at)
    return copy(revisions = revisions + revision)
}

private fun KnowledgeRelationshipDao.loadSnapshot(entity: KnowledgeRelationshipEntity): KnowledgeRelationshipSnapshot {
    val relationship = KnowledgeRelationship(
        id = KnowledgeRelationshipId(entity.id), type = KnowledgeRelationshipType.valueOf(entity.type),
        fromKnowledgeId = KnowledgeItemId(entity.fromKnowledgeId), toKnowledgeId = KnowledgeItemId(entity.toKnowledgeId),
        scope = KnowledgeScope.valueOf(entity.scopeKind), projectId = entity.projectId?.let(::ProjectId),
        status = KnowledgeRelationshipStatus.valueOf(entity.status), createdAt = Instant.ofEpochMilli(entity.createdAtEpochMs),
        updatedAt = Instant.ofEpochMilli(entity.updatedAtEpochMs), createdByIntentId = KnowledgeRelationshipIntentId(entity.createdByIntentId),
        latestIntentId = KnowledgeRelationshipIntentId(entity.latestIntentId), suggestionSource = KnowledgeRelationshipSuggestionSource.valueOf(entity.suggestionSource),
    )
    val revisions = revisions(entity.id).map { revision -> KnowledgeRelationshipRevision(
        id = KnowledgeRelationshipRevisionId(revision.id), relationshipId = relationship.id, revision = revision.revision,
        action = KnowledgeRelationshipAction.valueOf(revision.action), status = KnowledgeRelationshipStatus.valueOf(revision.status),
        intentId = KnowledgeRelationshipIntentId(revision.intentId), createdAt = Instant.ofEpochMilli(revision.createdAtEpochMs),
    ) }
    return KnowledgeRelationshipSnapshot(relationship, revisions)
}

private fun persistManaged(dao: KnowledgeDao, snapshot: KnowledgeSnapshot) {
    val item = snapshot.item; val life = snapshot.lifecycle
    if (dao.findKnowledge(item.id.value) == null) {
        dao.insertBundle(item.toEntity().copy(status = life.status.name, updatedAtEpochMs = life.updatedAt.toEpochMilli(), archivedAtEpochMs = life.archivedAt?.toEpochMilli(), deletedAtEpochMs = life.deletedAt?.toEpochMilli(), contentHash = life.contentHash), item.sourceEvidence.mapIndexed { p, e -> e.toKnowledgeEntity(item.id, p) }, item.attachments.mapIndexed { p, a -> a.toKnowledgeEntity(item.id, p) })
    } else dao.updateKnowledge(item.id.value, item.title, item.body, life.status.name, life.updatedAt.toEpochMilli(), life.archivedAt?.toEpochMilli(), life.deletedAt?.toEpochMilli(), life.contentHash)
    dao.upsertProjectScope(KnowledgeProjectScopeEntity(item.id.value, life.projectId?.value, life.updatedAt.toEpochMilli(), 2)); dao.clearItemTags(item.id.value); dao.insertTags(life.tags.map(::KnowledgeTagEntity)); dao.insertItemTags(life.tags.map { KnowledgeItemTagEntity(item.id.value, it) })
    val existing = dao.revisionsFor(item.id.value).map { it.id }.toSet(); snapshot.revisions.filter { it.id.value !in existing }.forEach { revision -> dao.insertRevision(KnowledgeRevisionEntity(revision.id.value, revision.knowledgeId.value, revision.revision, revision.title, revision.body, revision.status.name, revision.projectId?.value, revision.contentHash, revision.createdAt.toEpochMilli())); dao.insertTags(revision.tags.map(::KnowledgeTagEntity)); dao.insertRevisionTags(revision.tags.map { KnowledgeRevisionTagEntity(revision.id.value, it) }) }
}

private fun CaptureDraftEvidenceEntity.toDomain() = SourceEvidence(
    sourceType = CaptureSourceType.valueOf(sourceType),
    receivedAt = Instant.ofEpochMilli(receivedAtEpochMs),
    sourceReference = sourceReference,
    contributedFields = contributedFields.decodeFields(),
)

private fun KnowledgeEvidenceEntity.toDomain() = SourceEvidence(
    sourceType = CaptureSourceType.valueOf(sourceType),
    receivedAt = Instant.ofEpochMilli(receivedAtEpochMs),
    sourceReference = sourceReference,
    contributedFields = contributedFields.decodeFields(),
)

private fun CaptureDraftAttachmentEntity.toDomain() = AttachmentReference(
    reference = storageKey,
    mimeType = mimeType,
    displayName = displayName,
    id = AttachmentId(attachmentId),
    byteCount = byteCount,
    sha256 = sha256,
)

private fun KnowledgeAttachmentEntity.toDomain() = AttachmentReference(
    reference = storageKey,
    mimeType = mimeType,
    displayName = displayName,
    id = AttachmentId(attachmentId),
    byteCount = byteCount,
    sha256 = sha256,
)

private fun Set<String>.encodeFields(): String {
    require(all { it.isNotBlank() && !it.contains(FIELD_SEPARATOR) }) { "来源字段包含不支持的分隔符。" }
    return sorted().joinToString(FIELD_SEPARATOR)
}

private fun String.decodeFields(): Set<String> =
    if (isEmpty()) emptySet() else split(FIELD_SEPARATOR).toSet()

private fun String?.cleanSourceReference(): String? = this?.takeUnless {
    it.startsWith("content://") || it.startsWith("file://") || it.startsWith('/')
}

private fun <T> RoomDatabase.inTransaction(action: () -> T): T = runInTransaction(Callable { action() })

package com.nanzhufeng.ai.data.local

import androidx.room.RoomDatabase
import com.nanzhufeng.ai.domain.*
import java.time.Clock
import java.time.Instant
import java.util.concurrent.Callable

/** P4-C's only Room gateway. It validates relational scope before persisting explicit user actions. */
class RoomMemoryRepository(private val database: NanfengAiDatabase, private val clock: Clock = Clock.systemUTC()) : MemoryRepository {
    private val domain = MemoryDomain(clock)

    override fun findById(id: MemoryId): MemorySnapshot? = database.memoryDao().loadSnapshot(id)

    override fun list(scope: MemoryScopeKind?, status: MemoryStatus?, search: String): List<MemorySnapshot> {
        val needle = search.trim().lowercase()
        return database.memoryDao().listMemories(scope?.name, status?.name).mapNotNull { entity ->
            database.memoryDao().loadSnapshot(MemoryId(entity.id))
        }.filter { snapshot -> needle.isBlank() || snapshot.memory.title.lowercase().contains(needle) || snapshot.memory.body.lowercase().contains(needle) }
    }

    override fun conflict(id: MemoryConflictId): MemoryConflict? = database.memoryDao().conflict(id.value)?.toDomain()

    override fun mutate(intent: MemoryIntent, fingerprint: String): MemoryMutationResult = database.inMemoryTransaction {
        val dao = database.memoryDao()
        val previous = dao.intent(intent.id.value)
        if (previous != null) {
            if (previous.requestFingerprint != fingerprint) return@inMemoryTransaction MemoryMutationResult.Rejected(MemoryRejectionCode.INTENT_MISMATCH)
            previous.conflictId?.let { conflictId -> dao.conflict(conflictId)?.let { return@inMemoryTransaction MemoryMutationResult.Conflict(it.toDomain()) } }
            val replayed = previous.memoryId?.let { dao.loadSnapshot(MemoryId(it)) }?.let(::listOf).orEmpty()
            return@inMemoryTransaction MemoryMutationResult.Replayed(replayed)
        }
        when (intent.action) {
            MemoryIntentAction.CREATE -> create(dao, intent, fingerprint)
            MemoryIntentAction.UPDATE -> update(dao, intent, fingerprint)
            MemoryIntentAction.PAUSE -> changeStatus(dao, intent, fingerprint, MemoryStatus.PAUSED)
            MemoryIntentAction.RESTORE -> changeStatus(dao, intent, fingerprint, MemoryStatus.ACTIVE)
            MemoryIntentAction.DELETE -> changeStatus(dao, intent, fingerprint, MemoryStatus.DELETED)
            MemoryIntentAction.BULK_DELETE -> bulkDelete(dao, intent, fingerprint)
            MemoryIntentAction.RESOLVE_CONFLICT -> resolveConflict(dao, intent, fingerprint)
        }
    }

    private fun create(dao: MemoryDao, intent: MemoryIntent, fingerprint: String): MemoryMutationResult {
        val scope = requireNotNull(intent.scope)
        if (!scopeExists(scope)) return MemoryMutationResult.Rejected(MemoryRejectionCode.INVALID_SCOPE_REFERENCE)
        val title = domain.normalizedTitle(requireNotNull(intent.title)); val body = domain.normalizedBody(requireNotNull(intent.body))
        val contentHash = domain.contentHash(title, body); val conceptHash = domain.conceptHash(title)
        dao.findLiveByContent(scope.stableKey(), contentHash)?.let { existing ->
            dao.insertIntent(MemoryIntentEntity(intent.id.value, existing.id, intent.action.name, fingerprint, null, now().toEpochMilli()))
            return MemoryMutationResult.Duplicate(requireNotNull(dao.loadSnapshot(MemoryId(existing.id))))
        }
        dao.findLiveByConcept(scope.stableKey(), conceptHash)?.let { existing ->
            val conflict = MemoryConflict(MemoryConflictId.new(), intent.id, MemoryId(existing.id), title, body, scope, intent.source, intent.sourceStableId, intent.sourceSummary, contentHash, MemoryConflictStatus.PENDING, now())
            dao.insertConflict(conflict.toEntity())
            dao.insertIntent(MemoryIntentEntity(intent.id.value, null, intent.action.name, fingerprint, conflict.id.value, conflict.createdAt.toEpochMilli()))
            return MemoryMutationResult.Conflict(conflict)
        }
        val id = requireNotNull(intent.memoryId)
        val item = newItem(id, title, body, scope, intent.source, intent.sourceStableId, intent.sourceSummary, contentHash, conceptHash)
        persist(dao, MemorySnapshot(item, listOf(item.initialRevision())))
        dao.insertIntent(MemoryIntentEntity(intent.id.value, id.value, intent.action.name, fingerprint, null, item.updatedAt.toEpochMilli()))
        return MemoryMutationResult.Applied(listOf(requireNotNull(dao.loadSnapshot(id))))
    }

    private fun update(dao: MemoryDao, intent: MemoryIntent, fingerprint: String): MemoryMutationResult {
        val current = requireNotNull(intent.memoryId).let { dao.loadSnapshot(it) } ?: return MemoryMutationResult.Rejected(MemoryRejectionCode.MISSING_MEMORY)
        if (current.memory.status == MemoryStatus.DELETED) return MemoryMutationResult.Rejected(MemoryRejectionCode.MISSING_MEMORY)
        val scope = requireNotNull(intent.scope)
        if (!scopeExists(scope)) return MemoryMutationResult.Rejected(MemoryRejectionCode.INVALID_SCOPE_REFERENCE)
        val title = domain.normalizedTitle(requireNotNull(intent.title)); val body = domain.normalizedBody(requireNotNull(intent.body))
        val contentHash = domain.contentHash(title, body)
        dao.findLiveByContent(scope.stableKey(), contentHash)?.takeIf { it.id != current.memory.id.value }?.let { return MemoryMutationResult.Duplicate(requireNotNull(dao.loadSnapshot(MemoryId(it.id)))) }
        val next = current.withContent(title, body, scope, intent.source, intent.sourceStableId, intent.sourceSummary, contentHash, domain.conceptHash(title), now())
        persist(dao, next)
        dao.insertIntent(MemoryIntentEntity(intent.id.value, next.memory.id.value, intent.action.name, fingerprint, null, next.memory.updatedAt.toEpochMilli()))
        return MemoryMutationResult.Applied(listOf(requireNotNull(dao.loadSnapshot(next.memory.id))))
    }

    private fun changeStatus(dao: MemoryDao, intent: MemoryIntent, fingerprint: String, status: MemoryStatus): MemoryMutationResult {
        val current = requireNotNull(intent.memoryId).let { dao.loadSnapshot(it) } ?: return MemoryMutationResult.Rejected(MemoryRejectionCode.MISSING_MEMORY)
        if (current.memory.status == status) {
            dao.insertIntent(MemoryIntentEntity(intent.id.value, current.memory.id.value, intent.action.name, fingerprint, null, now().toEpochMilli()))
            return MemoryMutationResult.Applied(listOf(current))
        }
        if (status != MemoryStatus.DELETED && current.memory.status == MemoryStatus.DELETED) return MemoryMutationResult.Rejected(MemoryRejectionCode.MISSING_MEMORY)
        val next = current.withStatus(status, now())
        persist(dao, next)
        dao.insertIntent(MemoryIntentEntity(intent.id.value, next.memory.id.value, intent.action.name, fingerprint, null, next.memory.updatedAt.toEpochMilli()))
        return MemoryMutationResult.Applied(listOf(requireNotNull(dao.loadSnapshot(next.memory.id))))
    }

    private fun bulkDelete(dao: MemoryDao, intent: MemoryIntent, fingerprint: String): MemoryMutationResult {
        val current = intent.memoryIds.distinct().map { id -> dao.loadSnapshot(id) ?: return MemoryMutationResult.Rejected(MemoryRejectionCode.MISSING_MEMORY) }
        val changed = current.map { snapshot -> if (snapshot.memory.status == MemoryStatus.DELETED) snapshot else snapshot.withStatus(MemoryStatus.DELETED, now()).also { persist(dao, it) } }
        dao.insertIntent(MemoryIntentEntity(intent.id.value, null, intent.action.name, fingerprint, null, now().toEpochMilli()))
        return MemoryMutationResult.Applied(changed.map { requireNotNull(dao.loadSnapshot(it.memory.id)) })
    }

    private fun resolveConflict(dao: MemoryDao, intent: MemoryIntent, fingerprint: String): MemoryMutationResult {
        val conflict = requireNotNull(intent.conflictId).let { dao.conflict(it.value)?.toDomain() } ?: return MemoryMutationResult.Rejected(MemoryRejectionCode.MISSING_CONFLICT)
        if (conflict.status != MemoryConflictStatus.PENDING) return MemoryMutationResult.Rejected(MemoryRejectionCode.MISSING_CONFLICT)
        val resolution = requireNotNull(intent.conflictResolution); val resolvedAt = now()
        val snapshots = when (resolution) {
            MemoryConflictResolution.KEEP_EXISTING -> emptyList()
            MemoryConflictResolution.CREATE_PARALLEL -> {
                if (!scopeExists(conflict.candidateScope)) return MemoryMutationResult.Rejected(MemoryRejectionCode.INVALID_SCOPE_REFERENCE)
                val id = MemoryId.new()
                val item = newItem(id, conflict.candidateTitle, conflict.candidateBody, conflict.candidateScope, conflict.candidateSource, conflict.candidateSourceStableId, conflict.candidateSourceSummary, conflict.candidateContentHash, domain.conceptHash(conflict.candidateTitle))
                persist(dao, MemorySnapshot(item, listOf(item.initialRevision())))
                listOf(requireNotNull(dao.loadSnapshot(id)))
            }
            MemoryConflictResolution.REVISE_EXISTING -> {
                val current = dao.loadSnapshot(conflict.existingMemoryId) ?: return MemoryMutationResult.Rejected(MemoryRejectionCode.MISSING_MEMORY)
                val next = current.withContent(conflict.candidateTitle, conflict.candidateBody, conflict.candidateScope, conflict.candidateSource, conflict.candidateSourceStableId, conflict.candidateSourceSummary, conflict.candidateContentHash, domain.conceptHash(conflict.candidateTitle), resolvedAt)
                persist(dao, next); listOf(requireNotNull(dao.loadSnapshot(next.memory.id)))
            }
        }
        dao.resolveConflict(conflict.id.value, MemoryConflictStatus.RESOLVED.name, resolvedAt.toEpochMilli())
        dao.insertIntent(MemoryIntentEntity(intent.id.value, snapshots.singleOrNull()?.memory?.id?.value, intent.action.name, fingerprint, conflict.id.value, resolvedAt.toEpochMilli()))
        return MemoryMutationResult.Applied(snapshots)
    }

    private fun scopeExists(scope: MemoryScope): Boolean = when (scope.kind) {
        MemoryScopeKind.GLOBAL -> true
        MemoryScopeKind.PROJECT -> database.projectDao().findProject(requireNotNull(scope.projectId).value) != null
        MemoryScopeKind.CONVERSATION -> database.conversationDao().findConversation(requireNotNull(scope.conversationId).value) != null
    }
    private fun newItem(id: MemoryId, title: String, body: String, scope: MemoryScope, source: MemorySource, sourceId: String, sourceSummary: String, contentHash: String, conceptHash: String): MemoryItem {
        val time = now(); return MemoryItem(id, title, body, scope, source, sourceId, sourceSummary, MemoryStatus.ACTIVE, contentHash, conceptHash, time, time, time)
    }
    private fun persist(dao: MemoryDao, snapshot: MemorySnapshot) {
        val item = snapshot.memory; if (dao.findMemory(item.id.value) == null) dao.insertMemory(item.toEntity()) else dao.update(item.toEntity())
        val existing = dao.revisionsFor(item.id.value).map { it.id }.toSet(); snapshot.revisions.filter { it.id.value !in existing }.forEach { dao.insertRevision(it.toEntity()) }
    }
    private fun now(): Instant = clock.instant()
}

private fun MemoryItem.initialRevision() = MemoryRevision(MemoryRevisionId.new(), id, 1, title, body, scope, source, sourceStableId, sourceSummary, status, contentHash, createdAt)
private fun MemorySnapshot.withContent(title: String, body: String, scope: MemoryScope, source: MemorySource, sourceId: String, sourceSummary: String, contentHash: String, conceptHash: String, at: Instant): MemorySnapshot {
    val next = memory.copy(title = title, body = body, scope = scope, source = source, sourceStableId = sourceId, sourceSummary = sourceSummary, contentHash = contentHash, conceptHash = conceptHash, updatedAt = at, lastConfirmedAt = at)
    return copy(memory = next, revisions = revisions + MemoryRevision(MemoryRevisionId.new(), next.id, (revisions.maxOfOrNull { it.revision } ?: 0) + 1, title, body, scope, source, sourceId, sourceSummary, next.status, contentHash, at))
}
private fun MemorySnapshot.withStatus(status: MemoryStatus, at: Instant): MemorySnapshot {
    val next = memory.copy(status = status, updatedAt = at, lastConfirmedAt = at, deletedAt = if (status == MemoryStatus.DELETED) at else memory.deletedAt)
    return copy(memory = next, revisions = revisions + MemoryRevision(MemoryRevisionId.new(), next.id, (revisions.maxOfOrNull { it.revision } ?: 0) + 1, next.title, next.body, next.scope, next.source, next.sourceStableId, next.sourceSummary, status, next.contentHash, at))
}
private fun MemoryDao.loadSnapshot(id: MemoryId): MemorySnapshot? = findMemory(id.value)?.let { entity -> MemorySnapshot(entity.toDomain(), revisionsFor(entity.id).map { it.toDomain() }) }
private fun MemoryItem.toEntity() = MemoryEntity(id.value, title, body, scope.kind.name, scope.projectId?.value, scope.conversationId?.value, scope.stableKey(), source.name, sourceStableId, sourceSummary, status.name, contentHash, conceptHash, createdAt.toEpochMilli(), updatedAt.toEpochMilli(), lastConfirmedAt.toEpochMilli(), deletedAt?.toEpochMilli(), schemaVersion)
private fun MemoryEntity.toDomain() = MemoryItem(MemoryId(id), title, body, MemoryScope(MemoryScopeKind.valueOf(scopeKind), projectId?.let(::ProjectId), conversationId?.let(::ConversationId)), MemorySource.valueOf(source), sourceStableId, sourceSummary, MemoryStatus.valueOf(status), contentHash, conceptHash, Instant.ofEpochMilli(createdAtEpochMs), Instant.ofEpochMilli(updatedAtEpochMs), Instant.ofEpochMilli(lastConfirmedAtEpochMs), deletedAtEpochMs?.let(Instant::ofEpochMilli), schemaVersion)
private fun MemoryRevision.toEntity() = MemoryRevisionEntity(id.value, memoryId.value, revision, title, body, scope.kind.name, scope.projectId?.value, scope.conversationId?.value, source.name, sourceStableId, sourceSummary, status.name, contentHash, createdAt.toEpochMilli(), schemaVersion)
private fun MemoryRevisionEntity.toDomain() = MemoryRevision(MemoryRevisionId(id), MemoryId(memoryId), revision, title, body, MemoryScope(MemoryScopeKind.valueOf(scopeKind), projectId?.let(::ProjectId), conversationId?.let(::ConversationId)), MemorySource.valueOf(source), sourceStableId, sourceSummary, MemoryStatus.valueOf(status), contentHash, Instant.ofEpochMilli(createdAtEpochMs), schemaVersion)
private fun MemoryConflict.toEntity() = MemoryConflictEntity(id.value, intentId.value, existingMemoryId.value, candidateTitle, candidateBody, candidateScope.kind.name, candidateScope.projectId?.value, candidateScope.conversationId?.value, candidateSource.name, candidateSourceStableId, candidateSourceSummary, candidateContentHash, status.name, createdAt.toEpochMilli(), resolvedAt?.toEpochMilli())
private fun MemoryConflictEntity.toDomain() = MemoryConflict(MemoryConflictId(id), MemoryIntentId(intentId), MemoryId(existingMemoryId), candidateTitle, candidateBody, MemoryScope(MemoryScopeKind.valueOf(scopeKind), projectId?.let(::ProjectId), conversationId?.let(::ConversationId)), MemorySource.valueOf(source), sourceStableId, sourceSummary, candidateContentHash, MemoryConflictStatus.valueOf(status), Instant.ofEpochMilli(createdAtEpochMs), resolvedAtEpochMs?.let(Instant::ofEpochMilli))
private fun MemoryDao.update(entity: MemoryEntity): Int = updateMemory(entity.id, entity.title, entity.body, entity.scopeKind, entity.projectId, entity.conversationId, entity.scopeKey, entity.source, entity.sourceStableId, entity.sourceSummary, entity.status, entity.contentHash, entity.conceptHash, entity.updatedAtEpochMs, entity.lastConfirmedAtEpochMs, entity.deletedAtEpochMs, entity.schemaVersion)
private fun <T> RoomDatabase.inMemoryTransaction(block: () -> T): T = runInTransaction(Callable { block() })

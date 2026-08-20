package com.nanzhufeng.ai.data.local

import androidx.room.RoomDatabase
import com.nanzhufeng.ai.domain.ConversationId
import com.nanzhufeng.ai.domain.KnowledgeItemId
import com.nanzhufeng.ai.domain.Project
import com.nanzhufeng.ai.domain.ProjectColorSemantic
import com.nanzhufeng.ai.domain.ProjectId
import com.nanzhufeng.ai.domain.ProjectIconSemantic
import com.nanzhufeng.ai.domain.ProjectInstructionRevision
import com.nanzhufeng.ai.domain.ProjectInstructionRevisionId
import com.nanzhufeng.ai.domain.ProjectInstructionSource
import com.nanzhufeng.ai.domain.ProjectIntent
import com.nanzhufeng.ai.domain.ProjectIntentAction
import com.nanzhufeng.ai.domain.ProjectListScope
import com.nanzhufeng.ai.domain.ProjectMutationResult
import com.nanzhufeng.ai.domain.ProjectRepository
import com.nanzhufeng.ai.domain.ProjectSnapshot
import java.time.Instant
import java.util.concurrent.Callable

/** P4-A's only Room gateway for Project facts and scope changes. */
class RoomProjectRepository(private val database: NanfengAiDatabase) : ProjectRepository {
    override fun findById(id: ProjectId): ProjectSnapshot? = database.projectDao().loadSnapshot(id)

    override fun list(scope: ProjectListScope): List<ProjectSnapshot> = when (scope) {
        ProjectListScope.ACTIVE -> database.projectDao().listActive()
        ProjectListScope.ARCHIVED -> database.projectDao().listArchived()
    }.mapNotNull { database.projectDao().loadSnapshot(ProjectId(it.id)) }

    override fun projectForConversation(conversationId: ConversationId): ProjectId? =
        database.projectDao().projectForConversation(conversationId.value)?.let(::ProjectId)

    override fun knowledgeScope(knowledgeId: KnowledgeItemId): ProjectId? =
        database.projectDao().scopeForKnowledge(knowledgeId.value)?.let(::ProjectId)

    override fun apply(intent: ProjectIntent, fingerprint: String, mutate: () -> ProjectSnapshot): ProjectMutationResult = database.inProjectTransaction {
        val dao = database.projectDao()
        val previous = dao.intent(intent.id.value)
        if (previous != null) {
            if (previous.requestFingerprint != fingerprint) return@inProjectTransaction ProjectMutationResult.Rejected("相同项目 intent 的请求内容不一致。")
            return@inProjectTransaction dao.loadSnapshot(ProjectId(previous.projectId))?.let(ProjectMutationResult::Replayed)
                ?: ProjectMutationResult.Rejected("重复项目 intent 缺少项目回读。")
        }
        val next = runCatching(mutate).getOrElse { return@inProjectTransaction ProjectMutationResult.Rejected(it.message ?: "项目操作被拒绝。") }
        if (next.project.id != intent.projectId) return@inProjectTransaction ProjectMutationResult.Rejected("项目 intent 与项目快照不一致。")
        persist(dao, next)
        dao.insertIntent(ProjectIntentEntity(intent.id.value, intent.projectId.value, intent.action.name, fingerprint, intent.conversationId?.value, intent.knowledgeId?.value, next.project.updatedAt.toEpochMilli()))
        ProjectMutationResult.Applied(requireNotNull(dao.loadSnapshot(intent.projectId)))
    }

    override fun assignConversation(intent: ProjectIntent, fingerprint: String): ProjectMutationResult = database.inProjectTransaction {
        val dao = database.projectDao()
        val previous = dao.intent(intent.id.value)
        if (previous != null) {
            if (previous.requestFingerprint != fingerprint) return@inProjectTransaction ProjectMutationResult.Rejected("相同项目 intent 的请求内容不一致。")
            return@inProjectTransaction dao.loadSnapshot(ProjectId(previous.projectId))?.let(ProjectMutationResult::Replayed)
                ?: ProjectMutationResult.Rejected("重复项目 intent 缺少项目回读。")
        }
        val snapshot = dao.loadSnapshot(intent.projectId) ?: return@inProjectTransaction ProjectMutationResult.Rejected("项目不存在。")
        val conversationId = requireNotNull(intent.conversationId)
        if (database.conversationDao().findConversation(conversationId.value) == null) return@inProjectTransaction ProjectMutationResult.Rejected("会话不存在。")
        val projectId = if (intent.action == ProjectIntentAction.ASSIGN_CONVERSATION) intent.projectId.value else null
        if (intent.action != ProjectIntentAction.ASSIGN_CONVERSATION && intent.action != ProjectIntentAction.REMOVE_CONVERSATION) return@inProjectTransaction ProjectMutationResult.Rejected("不是会话归属操作。")
        dao.setConversationProject(conversationId.value, projectId, Instant.now().toEpochMilli())
        dao.insertIntent(ProjectIntentEntity(intent.id.value, intent.projectId.value, intent.action.name, fingerprint, conversationId.value, null, Instant.now().toEpochMilli()))
        ProjectMutationResult.Applied(snapshot)
    }

    override fun assignKnowledge(intent: ProjectIntent, fingerprint: String): ProjectMutationResult = database.inProjectTransaction {
        val dao = database.projectDao()
        val previous = dao.intent(intent.id.value)
        if (previous != null) {
            if (previous.requestFingerprint != fingerprint) return@inProjectTransaction ProjectMutationResult.Rejected("相同项目 intent 的请求内容不一致。")
            return@inProjectTransaction dao.loadSnapshot(ProjectId(previous.projectId))?.let(ProjectMutationResult::Replayed)
                ?: ProjectMutationResult.Rejected("重复项目 intent 缺少项目回读。")
        }
        val snapshot = dao.loadSnapshot(intent.projectId) ?: return@inProjectTransaction ProjectMutationResult.Rejected("项目不存在。")
        val knowledgeId = requireNotNull(intent.knowledgeId)
        if (intent.action != ProjectIntentAction.ASSIGN_KNOWLEDGE && intent.action != ProjectIntentAction.REMOVE_KNOWLEDGE) return@inProjectTransaction ProjectMutationResult.Rejected("不是知识范围操作。")
        // Knowledge may be introduced by a later import; P4-A stores only its stable ID relation.
        val scope = if (intent.action == ProjectIntentAction.ASSIGN_KNOWLEDGE) intent.projectId.value else null
        dao.upsertKnowledgeScope(KnowledgeProjectScopeEntity(knowledgeId.value, scope, Instant.now().toEpochMilli(), 1))
        dao.insertIntent(ProjectIntentEntity(intent.id.value, intent.projectId.value, intent.action.name, fingerprint, null, knowledgeId.value, Instant.now().toEpochMilli()))
        ProjectMutationResult.Applied(snapshot)
    }

    private fun persist(dao: ProjectDao, snapshot: ProjectSnapshot) {
        val existing = dao.findProject(snapshot.project.id.value)
        if (existing == null) dao.insertProject(snapshot.project.toEntity()) else dao.update(snapshot.project.toEntity())
        val known = dao.revisionsFor(snapshot.project.id.value).map { it.id }.toSet()
        snapshot.instructionRevisions.filter { it.id.value !in known }.forEach { dao.insertRevision(it.toEntity()) }
    }
}

private fun ProjectDao.loadSnapshot(id: ProjectId): ProjectSnapshot? = findProject(id.value)?.let { project ->
    ProjectSnapshot(project.toDomain(), revisionsFor(project.id).map { it.toDomain() })
}

private fun Project.toEntity() = ProjectEntity(id.value, title, description, color?.name, icon?.name, createdAt.toEpochMilli(), updatedAt.toEpochMilli(), archivedAt?.toEpochMilli(), pinnedAt?.toEpochMilli(), deletedAt?.toEpochMilli(), schemaVersion)
private fun ProjectEntity.toDomain() = Project(ProjectId(id), title, description, colorSemantic?.let(ProjectColorSemantic::valueOf), iconSemantic?.let(ProjectIconSemantic::valueOf), Instant.ofEpochMilli(createdAtEpochMs), Instant.ofEpochMilli(updatedAtEpochMs), archivedAtEpochMs?.let(Instant::ofEpochMilli), pinnedAtEpochMs?.let(Instant::ofEpochMilli), deletedAtEpochMs?.let(Instant::ofEpochMilli), schemaVersion)
private fun ProjectInstructionRevision.toEntity() = ProjectInstructionRevisionEntity(id.value, projectId.value, revision, content, source.name, contentHash, createdAt.toEpochMilli(), schemaVersion)
private fun ProjectInstructionRevisionEntity.toDomain() = ProjectInstructionRevision(ProjectInstructionRevisionId(id), ProjectId(projectId), revision, content, ProjectInstructionSource.valueOf(source), contentHash, Instant.ofEpochMilli(createdAtEpochMs), schemaVersion)
private fun ProjectDao.update(entity: ProjectEntity): Int = updateProject(entity.id, entity.title, entity.description, entity.colorSemantic, entity.iconSemantic, entity.updatedAtEpochMs, entity.archivedAtEpochMs, entity.pinnedAtEpochMs, entity.deletedAtEpochMs, entity.schemaVersion)
private fun <T> RoomDatabase.inProjectTransaction(block: () -> T): T = runInTransaction(Callable { block() })

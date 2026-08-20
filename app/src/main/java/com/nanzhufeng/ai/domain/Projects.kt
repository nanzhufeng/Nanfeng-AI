package com.nanzhufeng.ai.domain

import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.util.UUID

/** P4-A stable local identity. It is never a database row id or a display value. */
@JvmInline value class ProjectId(val value: String) { companion object { fun new() = ProjectId(UUID.randomUUID().toString()) } }
@JvmInline value class ProjectInstructionRevisionId(val value: String) { companion object { fun new() = ProjectInstructionRevisionId(UUID.randomUUID().toString()) } }
@JvmInline value class ProjectIntentId(val value: String) { companion object { fun new() = ProjectIntentId(UUID.randomUUID().toString()) } }

enum class ProjectInstructionSource { USER }
enum class ProjectIntentAction { CREATE, UPDATE_METADATA, PIN, UNPIN, ARCHIVE, RESTORE, UPDATE_INSTRUCTION, ASSIGN_CONVERSATION, REMOVE_CONVERSATION, ASSIGN_KNOWLEDGE, REMOVE_KNOWLEDGE }
enum class ProjectListScope { ACTIVE, ARCHIVED }
enum class KnowledgeScopeKind { GLOBAL, PROJECT }

/** Decorative semantics only. These are explicitly unrelated to the launcher icon. */
enum class ProjectColorSemantic { FOREST, OCEAN, AMBER, PLUM, SLATE }
enum class ProjectIconSemantic { FOLDER, BOOKMARK, COMPASS, SPARK, GRID }

data class Project(
    val id: ProjectId,
    val title: String,
    val description: String = "",
    val color: ProjectColorSemantic? = null,
    val icon: ProjectIconSemantic? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val archivedAt: Instant? = null,
    val pinnedAt: Instant? = null,
    val deletedAt: Instant? = null,
    val schemaVersion: Int = 1,
)

/** Empty content is an explicit, auditable removal of the active project instruction. */
data class ProjectInstructionRevision(
    val id: ProjectInstructionRevisionId,
    val projectId: ProjectId,
    val revision: Int,
    val content: String,
    val source: ProjectInstructionSource,
    val contentHash: String,
    val createdAt: Instant,
    val schemaVersion: Int = 1,
) {
    init {
        require(revision > 0)
        require(contentHash.matches(Regex("[0-9a-f]{64}")))
    }
    val isEmptyInstruction: Boolean get() = content.isEmpty()
}

data class ProjectSnapshot(val project: Project, val instructionRevisions: List<ProjectInstructionRevision>) {
    init { require(instructionRevisions.map { it.revision }.distinct().size == instructionRevisions.size) }
    val activeInstruction: ProjectInstructionRevision? get() = instructionRevisions.maxByOrNull { it.revision }
}

data class ProjectIntent(
    val id: ProjectIntentId,
    val action: ProjectIntentAction,
    val projectId: ProjectId,
    val title: String? = null,
    val description: String? = null,
    val color: ProjectColorSemantic? = null,
    val icon: ProjectIconSemantic? = null,
    val instruction: String? = null,
    val conversationId: ConversationId? = null,
    val knowledgeId: KnowledgeItemId? = null,
)

sealed interface ProjectMutationResult {
    data class Applied(val snapshot: ProjectSnapshot) : ProjectMutationResult
    data class Replayed(val snapshot: ProjectSnapshot) : ProjectMutationResult
    data class Rejected(val reason: String) : ProjectMutationResult
}

interface ProjectRepository {
    fun apply(intent: ProjectIntent, fingerprint: String, mutate: () -> ProjectSnapshot): ProjectMutationResult
    fun findById(id: ProjectId): ProjectSnapshot?
    fun list(scope: ProjectListScope): List<ProjectSnapshot>
    fun projectForConversation(conversationId: ConversationId): ProjectId?
    fun knowledgeScope(knowledgeId: KnowledgeItemId): ProjectId?
    fun assignConversation(intent: ProjectIntent, fingerprint: String): ProjectMutationResult
    fun assignKnowledge(intent: ProjectIntent, fingerprint: String): ProjectMutationResult
}

/** P4-A's sole semantic owner: lifecycle, instruction revisions and local scope relationships. */
class ProjectDomain(private val clock: Clock) {
    fun create(intent: ProjectIntent): ProjectSnapshot {
        val now = clock.instant()
        val project = Project(intent.projectId, normalizeTitle(requireNotNull(intent.title)), normalizeDescription(intent.description.orEmpty()), intent.color, intent.icon, now, now)
        return ProjectSnapshot(project, emptyList())
    }

    fun updateMetadata(current: ProjectSnapshot, intent: ProjectIntent): ProjectSnapshot {
        val project = current.project
        val next = project.copy(
            title = intent.title?.let(::normalizeTitle) ?: project.title,
            description = intent.description?.let(::normalizeDescription) ?: project.description,
            color = intent.color ?: project.color,
            icon = intent.icon ?: project.icon,
        )
        return current.copy(project = if (next == project) project else next.copy(updatedAt = clock.instant()))
    }

    fun pin(current: ProjectSnapshot, pinned: Boolean): ProjectSnapshot {
        val project = current.project
        val next = if (pinned) project.copy(pinnedAt = project.pinnedAt ?: clock.instant()) else project.copy(pinnedAt = null)
        return current.copy(project = if (next == project) project else next.copy(updatedAt = clock.instant()))
    }

    fun archive(current: ProjectSnapshot, archived: Boolean): ProjectSnapshot {
        val project = current.project
        val next = if (archived) project.copy(archivedAt = project.archivedAt ?: clock.instant()) else project.copy(archivedAt = null)
        return current.copy(project = if (next == project) project else next.copy(updatedAt = clock.instant()))
    }

    fun reviseInstruction(current: ProjectSnapshot, raw: String): ProjectSnapshot {
        val content = normalizeInstruction(raw)
        val previous = current.activeInstruction
        if (previous?.content == content) return current
        val revision = ProjectInstructionRevision(ProjectInstructionRevisionId.new(), current.project.id, (previous?.revision ?: 0) + 1, content, ProjectInstructionSource.USER, sha256(content), clock.instant())
        return current.copy(project = current.project.copy(updatedAt = revision.createdAt), instructionRevisions = current.instructionRevisions + revision)
    }

    fun normalizeTitle(raw: String): String {
        val value = raw.trim()
        require(value.isNotEmpty()) { "项目标题不能为空或全为空白。" }
        require(value.codePointCount(0, value.length) <= MAX_TITLE_CODE_POINTS) { "项目标题不能超过 $MAX_TITLE_CODE_POINTS 个 Unicode 字符。" }
        require(value.none { it.isISOControl() }) { "项目标题不能包含控制字符。" }
        return value
    }
    fun normalizeDescription(raw: String): String {
        require(raw.codePointCount(0, raw.length) <= MAX_DESCRIPTION_CODE_POINTS) { "项目说明不能超过 $MAX_DESCRIPTION_CODE_POINTS 个 Unicode 字符。" }
        require(raw.none { it.isISOControl() && it != '\n' && it != '\t' }) { "项目说明不能包含控制字符。" }
        return raw.trim()
    }
    fun normalizeInstruction(raw: String): String {
        val value = raw.trim()
        require(value.codePointCount(0, value.length) <= MAX_INSTRUCTION_CODE_POINTS) { "项目指令不能超过 $MAX_INSTRUCTION_CODE_POINTS 个 Unicode 字符。" }
        require(value.none { it.isISOControl() && it != '\n' && it != '\t' }) { "项目指令不能包含控制字符。" }
        return value
    }
    companion object {
        const val MAX_TITLE_CODE_POINTS = 120
        const val MAX_DESCRIPTION_CODE_POINTS = 2_000
        const val MAX_INSTRUCTION_CODE_POINTS = 12_000
        fun sha256(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}

/** Local-only audit IR. It defines precedence but never creates a Prompt or a network request. */
enum class InstructionLayerPriority { SYSTEM, SAFETY, PROJECT, CONVERSATION, CURRENT_USER }
data class ResolvedInstructionLayer(val priority: InstructionLayerPriority, val source: String, val revisionId: String?, val contentHash: String, val userText: String?)
data class ProjectContextSnapshot(val projectId: ProjectId?, val instructionRevisionId: ProjectInstructionRevisionId?, val instructionRevision: Int?, val instructionHash: String?, val schemaVersion: Int = 1)
data class InstructionResolution(val layers: List<ResolvedInstructionLayer>, val projectContext: ProjectContextSnapshot) {
    init { require(layers.map { it.priority }.distinct().size == layers.size) }
}

object ProjectInstructionResolution {
    fun resolve(project: ProjectSnapshot?): InstructionResolution {
        val revision = project?.activeInstruction
        val context = ProjectContextSnapshot(project?.project?.id, revision?.id, revision?.revision, revision?.contentHash)
        return InstructionResolution(
            listOf(
                ResolvedInstructionLayer(InstructionLayerPriority.SYSTEM, "SYSTEM", null, ProjectDomain.sha256("SYSTEM"), null),
                ResolvedInstructionLayer(InstructionLayerPriority.SAFETY, "SAFETY", null, ProjectDomain.sha256("SAFETY"), null),
                ResolvedInstructionLayer(InstructionLayerPriority.PROJECT, "USER_PROJECT_INSTRUCTION", revision?.id?.value, revision?.contentHash ?: ProjectDomain.sha256(""), revision?.content),
                ResolvedInstructionLayer(InstructionLayerPriority.CONVERSATION, "RESERVED", null, ProjectDomain.sha256(""), null),
                ResolvedInstructionLayer(InstructionLayerPriority.CURRENT_USER, "RESERVED", null, ProjectDomain.sha256(""), null),
            ), context,
        )
    }
}

class ManageProjectUseCase(private val domain: ProjectDomain, private val repository: ProjectRepository) {
    fun execute(intent: ProjectIntent): ProjectMutationResult = repository.apply(intent, intent.fingerprint()) {
        when (intent.action) {
            ProjectIntentAction.CREATE -> domain.create(intent)
            else -> {
                val current = requireNotNull(repository.findById(intent.projectId)) { "项目不存在。" }
                when (intent.action) {
                    ProjectIntentAction.UPDATE_METADATA -> domain.updateMetadata(current, intent)
                    ProjectIntentAction.PIN -> domain.pin(current, true)
                    ProjectIntentAction.UNPIN -> domain.pin(current, false)
                    ProjectIntentAction.ARCHIVE -> domain.archive(current, true)
                    ProjectIntentAction.RESTORE -> domain.archive(current, false)
                    ProjectIntentAction.UPDATE_INSTRUCTION -> domain.reviseInstruction(current, requireNotNull(intent.instruction))
                    else -> current
                }
            }
        }
    }

    fun assignConversation(intent: ProjectIntent): ProjectMutationResult {
        require(intent.action == ProjectIntentAction.ASSIGN_CONVERSATION || intent.action == ProjectIntentAction.REMOVE_CONVERSATION)
        requireNotNull(intent.conversationId)
        return repository.assignConversation(intent, intent.fingerprint())
    }

    fun assignKnowledge(intent: ProjectIntent): ProjectMutationResult {
        require(intent.action == ProjectIntentAction.ASSIGN_KNOWLEDGE || intent.action == ProjectIntentAction.REMOVE_KNOWLEDGE)
        requireNotNull(intent.knowledgeId)
        return repository.assignKnowledge(intent, intent.fingerprint())
    }
}

private fun ProjectIntent.fingerprint(): String = ProjectDomain.sha256(listOf(id.value, action.name, projectId.value, title ?: "", description ?: "", color?.name ?: "", icon?.name ?: "", instruction ?: "", conversationId?.value ?: "", knowledgeId?.value ?: "").joinToString("|"))

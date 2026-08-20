package com.nanzhufeng.ai.data

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.nanzhufeng.ai.data.local.ConversationEntity
import com.nanzhufeng.ai.data.local.KnowledgeItemEntity
import com.nanzhufeng.ai.data.local.KnowledgeItemTagEntity
import com.nanzhufeng.ai.data.local.KnowledgeProjectScopeEntity
import com.nanzhufeng.ai.data.local.KnowledgeRelationshipEntity
import com.nanzhufeng.ai.data.local.KnowledgeRelationshipRevisionEntity
import com.nanzhufeng.ai.data.local.KnowledgeRevisionEntity
import com.nanzhufeng.ai.data.local.KnowledgeRevisionTagEntity
import com.nanzhufeng.ai.data.local.KnowledgeTagEntity
import com.nanzhufeng.ai.data.local.MemoryEntity
import com.nanzhufeng.ai.data.local.MemoryRevisionEntity
import com.nanzhufeng.ai.data.local.MessageContentBlockEntity
import com.nanzhufeng.ai.data.local.MessageNodeEntity
import com.nanzhufeng.ai.data.local.NanfengAiDatabase
import com.nanzhufeng.ai.data.local.ProjectEntity
import com.nanzhufeng.ai.data.local.ProjectInstructionRevisionEntity
import com.nanzhufeng.ai.domain.NfaiSyncPreparedSnapshot
import com.nanzhufeng.ai.domain.P7EAtomicAllowlistRestoreWriter
import com.nanzhufeng.ai.domain.P7ERestorePlan
import com.nanzhufeng.ai.domain.P7ESemanticSnapshotMapper
import java.io.File
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/**
 * Android's production P7-E allowlist writer.
 *
 * It is intentionally separate from P5-D's `.nfai-backup` writer and from the deprecated raw
 * Room-table prototype. A P7-E plan is decoded only through the shared semantic mapper, written
 * through typed DAOs to a fresh private Room candidate, read back through the typed source, then
 * atomically exposed as a complete database. The currently injected Room instance is closed at
 * the switch boundary; the caller must recreate its container/process before using business data.
 */
class AndroidP7ESemanticAtomicRestoreWriter(
    context: Context,
    private val liveDatabase: NanfengAiDatabase,
    private val appId: String = APP_ID,
) : P7EAtomicAllowlistRestoreWriter {
    private val app = context.applicationContext
    private val liveName = "nanfeng-ai.db"
    private val liveFile get() = app.getDatabasePath(liveName)
    private val staged = mutableMapOf<String, Candidate>()
    private val checkpoints = mutableMapOf<String, String>()

    override fun isLocalBusinessEmpty(): Boolean =
        liveDatabase.projectDao().listAllForP7ESemanticSnapshot().isEmpty() &&
            liveDatabase.conversationDao().listAllForP7ESemanticSnapshot().isEmpty() &&
            liveDatabase.knowledgeDao().listKnowledgeIncludingHidden().isEmpty() &&
            liveDatabase.memoryDao().listMemories(null, null).isEmpty() &&
            liveDatabase.knowledgeRelationshipDao().all().isEmpty()

    /** P5-D-compatible consistent checkpoint; it never copies a live WAL/SHM pair. */
    override fun createCheckpoint(): String {
        val name = uniqueDatabaseName("checkpoint")
        val target = app.getDatabasePath(name)
        target.parentFile?.mkdirs()
        app.deleteDatabase(name)
        val escaped = target.absolutePath.replace("'", "''")
        liveDatabase.openHelper.writableDatabase.execSQL("VACUUM INTO '$escaped'")
        require(target.isFile) { "P7E checkpoint missing" }
        checkpoints[name] = name
        return name
    }

    override fun stage(plan: P7ERestorePlan): String {
        val decoded = decode(plan)
        verifyReferences(decoded)
        val candidateName = uniqueDatabaseName("stage")
        app.deleteDatabase(candidateName)
        return try {
            val candidate = openDatabase(candidateName)
            try {
                candidate.runInTransaction {
                    write(candidate, decoded)
                }
                verifyReadback(candidate, plan)
            } finally {
                candidate.close()
            }
            val candidateFile = app.getDatabasePath(candidateName)
            require(candidateFile.isFile) { "P7E candidate database missing" }
            val ref = "p7e-stage-${UUID.randomUUID()}"
            staged[ref] = Candidate(candidateName, plan.payloadHash, plan)
            ref
        } catch (error: Throwable) {
            app.deleteDatabase(candidateName)
            throw error
        }
    }

    /**
     * Swap only after candidate semantic readback. The old file stays available until a separate
     * Room open/readback reproduces every P7-E record, so a failed open restores old truth.
     */
    override fun commitAtomically(stagingRef: String): String {
        val candidate = staged.remove(stagingRef) ?: error("P7E staged candidate missing")
        val candidateFile = app.getDatabasePath(candidate.name)
        require(candidateFile.isFile) { "P7E staged candidate unavailable" }
        val displacedName = uniqueDatabaseName("displaced")
        val displaced = app.getDatabasePath(displacedName)
        liveDatabase.close()
        try {
            if (liveFile.exists() && !moveDatabaseSet(liveName, displacedName)) error("P7E live database checkpoint switch failed")
            if (!moveDatabaseSet(candidate.name, liveName)) {
                if (displaced.exists()) moveDatabaseSet(displacedName, liveName)
                error("P7E candidate switch failed")
            }
            val reopened = openDatabase(liveName)
            try {
                verifyReadback(reopened, candidate.plan)
            } finally {
                reopened.close()
            }
            app.deleteDatabase(displacedName)
            return candidate.payloadHash
        } catch (error: Throwable) {
            if (liveFile.exists()) {
                val failedName = uniqueDatabaseName("failed")
                if (moveDatabaseSet(liveName, failedName)) app.deleteDatabase(failedName)
            }
            if (displaced.exists() && !moveDatabaseSet(displacedName, liveName)) {
                error("P7E rollback switch failed")
            }
            throw error
        } finally {
            app.deleteDatabase(candidate.name)
        }
    }

    override fun rollback(checkpointRef: String) {
        val name = checkpoints.remove(checkpointRef) ?: return
        val checkpoint = app.getDatabasePath(name)
        if (!checkpoint.isFile) return
        liveDatabase.close()
        val displacedName = uniqueDatabaseName("rollback-displaced")
        val displaced = app.getDatabasePath(displacedName)
        if (liveFile.exists() && !moveDatabaseSet(liveName, displacedName)) return
        if (!moveDatabaseSet(name, liveName)) {
            if (displaced.exists()) moveDatabaseSet(displacedName, liveName)
            return
        }
        app.deleteDatabase(displacedName)
    }

    override fun discardCheckpoint(checkpointRef: String) {
        checkpoints.remove(checkpointRef)?.let(app::deleteDatabase)
    }

    private fun openDatabase(name: String): NanfengAiDatabase =
        Room.databaseBuilder(app, NanfengAiDatabase::class.java, name)
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .build()

    private fun verifyReadback(database: NanfengAiDatabase, plan: P7ERestorePlan) {
        val readback = P7ESemanticSnapshotMapper.toPreparedSnapshot(
            AndroidP7ESemanticSnapshotSource(database, appId).snapshot(plan.documentId, plan.remoteRevision),
        )
        require(readback.records == plan.records) { "P7E semantic candidate readback mismatch" }
    }

    private fun write(database: NanfengAiDatabase, decoded: DecodedSnapshot) {
        decoded.projects.forEach { project ->
            database.projectDao().insertProject(project.entity)
            project.instructions.forEach(database.projectDao()::insertRevision)
        }
        decoded.conversations.forEach { conversation ->
            database.conversationDao().insertConversation(conversation.entity)
            conversation.nodes.forEach(database.conversationDao()::insertNode)
            conversation.blocksByNode.values.forEach(database.conversationDao()::insertBlocks)
        }
        decoded.knowledge.forEach { knowledge ->
            database.knowledgeDao().insertKnowledge(knowledge.entity)
            database.knowledgeDao().insertRevision(knowledge.revisions.first())
            knowledge.revisions.drop(1).forEach(database.knowledgeDao()::insertRevision)
            database.knowledgeDao().insertTags(knowledge.tags.map(::KnowledgeTagEntity))
            database.knowledgeDao().insertItemTags(knowledge.tags.map { KnowledgeItemTagEntity(knowledge.entity.id, it) })
            knowledge.revisionTags.forEach { (revisionId, tags) ->
                database.knowledgeDao().insertRevisionTags(tags.map { KnowledgeRevisionTagEntity(revisionId, it) })
            }
            database.knowledgeDao().upsertProjectScope(knowledge.scope)
        }
        decoded.memories.forEach { memory ->
            database.memoryDao().insertMemory(memory.entity)
            memory.revisions.forEach(database.memoryDao()::insertRevision)
        }
        decoded.relations.forEach { relation ->
            database.knowledgeRelationshipDao().insert(relation.entity)
            relation.revisions.forEach(database.knowledgeRelationshipDao()::insertRevision)
        }
    }

    private fun decode(plan: P7ERestorePlan): DecodedSnapshot {
        require(plan.format == "nfai.sync.restore-plan.v1" && plan.remoteRevision > 0)
        val semantic = P7ESemanticSnapshotMapper.fromOpened(
            NfaiSyncPreparedSnapshot(appId, plan.documentId, plan.remoteRevision, plan.records),
        )
        val byKind = semantic.states.groupBy { it.kind }
        require(byKind.values.flatten().size == semantic.states.size)
        require(byKind.values.all { states -> states.map { it.id }.distinct().size == states.size }) { "P7E duplicate business object" }
        require(byKind.keys.all { it in ALLOWED_KINDS }) { "P7E unsupported semantic kind" }
        val safeSettings = byKind["safe_settings"].orEmpty()
        require(safeSettings.size == 1 && safeSettings.single().id == SAFE_SETTINGS_ID) { "P7E safe settings missing" }
        require(JSONObject(safeSettings.single().valueJson).also { it.requireKeys("values") }.getJSONObject("values").length() == 0) {
            "P7E Android has no mutable safe-settings owner"
        }
        return DecodedSnapshot(
            projects = byKind["project"].orEmpty().map(::decodeProject),
            conversations = byKind["conversation"].orEmpty().map(::decodeConversation),
            knowledge = byKind["knowledge"].orEmpty().map(::decodeKnowledge),
            memories = byKind["memory"].orEmpty().map(::decodeMemory),
            relations = byKind["relation"].orEmpty().map(::decodeRelation),
        )
    }

    private fun decodeProject(state: com.nanzhufeng.ai.domain.P7ESemanticState): ProjectPayload {
        val value = JSONObject(state.valueJson).also {
            it.requireKeys("title", "description", "colorSemantic", "iconSemantic", "createdAtEpochMs", "updatedAtEpochMs", "archivedAtEpochMs", "pinnedAtEpochMs", "deletedAtEpochMs", "instructionRevisions")
        }
        val instructions = value.getJSONArray("instructionRevisions").objects().map { item ->
            item.requireKeys("id", "revision", "content", "source", "contentHash", "createdAtEpochMs")
            ProjectInstructionRevisionEntity(
                id = item.string("id"), projectId = state.id, revision = item.int("revision"), content = item.string("content"),
                source = item.string("source"), contentHash = item.string("contentHash"), createdAtEpochMs = item.long("createdAtEpochMs"), schemaVersion = 1,
            )
        }
        require(instructions.map { it.id }.distinct().size == instructions.size)
        return ProjectPayload(
            ProjectEntity(state.id, value.string("title"), value.string("description"), value.nullableString("colorSemantic"), value.nullableString("iconSemantic"), value.long("createdAtEpochMs"), value.long("updatedAtEpochMs"), value.nullableLong("archivedAtEpochMs"), value.nullableLong("pinnedAtEpochMs"), value.nullableLong("deletedAtEpochMs"), 1),
            instructions,
        )
    }

    private fun decodeConversation(state: com.nanzhufeng.ai.domain.P7ESemanticState): ConversationPayload {
        val value = JSONObject(state.valueJson).also {
            it.requireKeys("title", "projectId", "currentLeafMessageId", "createdAtEpochMs", "updatedAtEpochMs", "archivedAtEpochMs", "pinnedAtEpochMs", "deletedAtEpochMs", "nodes")
        }
        val entity = ConversationEntity(
            id = state.id, title = value.string("title"), projectId = value.nullableString("projectId"), currentLeafMessageId = value.nullableString("currentLeafMessageId"),
            createdAtEpochMs = value.long("createdAtEpochMs"), updatedAtEpochMs = value.long("updatedAtEpochMs"), defaultProviderId = null, defaultModelId = null,
            harnessId = null, harnessVersion = null, contextPolicyVersion = 1, archivedAtEpochMs = value.nullableLong("archivedAtEpochMs"),
            pinnedAtEpochMs = value.nullableLong("pinnedAtEpochMs"), deletedAtEpochMs = value.nullableLong("deletedAtEpochMs"), schemaVersion = 1,
        )
        val blocks = linkedMapOf<String, List<MessageContentBlockEntity>>()
        val nodes = value.getJSONArray("nodes").objects().map { item ->
            item.requireKeys("id", "parentMessageId", "siblingPosition", "role", "createdAtEpochMs", "deliveryState", "revision", "revisesMessageId", "content")
            val id = item.string("id")
            blocks[id] = item.getJSONArray("content").strings().mapIndexed { position, text ->
                MessageContentBlockEntity(id, position, "TEXT", text, null, null, null, null, null, null, null, null, 1)
            }
            MessageNodeEntity(id, state.id, item.nullableString("parentMessageId"), item.int("siblingPosition"), item.string("role"), item.long("createdAtEpochMs"), item.string("deliveryState"), item.int("revision"), item.nullableString("revisesMessageId"), null, null, null, 1)
        }
        require(nodes.map { it.id }.distinct().size == nodes.size)
        return ConversationPayload(entity, nodes, blocks)
    }

    private fun decodeKnowledge(state: com.nanzhufeng.ai.domain.P7ESemanticState): KnowledgePayload {
        val value = JSONObject(state.valueJson).also {
            it.requireKeys("title", "body", "status", "createdAtEpochMs", "updatedAtEpochMs", "archivedAtEpochMs", "deletedAtEpochMs", "contentHash", "projectId", "tags", "revisions")
        }
        val entity = KnowledgeItemEntity(state.id, value.string("title"), value.string("body"), "p7e-semantic", "p7e-semantic", "MOCK", "p7e-restored-local", 1, value.long("createdAtEpochMs"), 1, value.string("status"), value.long("updatedAtEpochMs"), value.nullableLong("archivedAtEpochMs"), value.nullableLong("deletedAtEpochMs"), value.string("contentHash"))
        val revisionTags = linkedMapOf<String, List<String>>()
        val revisions = value.getJSONArray("revisions").objects().map { item ->
            item.requireKeys("id", "revision", "title", "body", "status", "projectId", "contentHash", "createdAtEpochMs", "tags")
            val id = item.string("id")
            revisionTags[id] = item.getJSONArray("tags").strings()
            KnowledgeRevisionEntity(id, state.id, item.int("revision"), item.string("title"), item.string("body"), item.string("status"), item.nullableString("projectId"), item.string("contentHash"), item.long("createdAtEpochMs"))
        }
        require(revisions.isNotEmpty() && revisions.map { it.id }.distinct().size == revisions.size)
        return KnowledgePayload(entity, revisions, value.getJSONArray("tags").strings(), revisionTags, KnowledgeProjectScopeEntity(state.id, value.nullableString("projectId"), value.long("updatedAtEpochMs"), 1))
    }

    private fun decodeMemory(state: com.nanzhufeng.ai.domain.P7ESemanticState): MemoryPayload {
        val value = JSONObject(state.valueJson).also {
            it.requireKeys("title", "body", "scopeKind", "projectId", "conversationId", "source", "sourceStableId", "sourceSummary", "status", "contentHash", "conceptHash", "createdAtEpochMs", "updatedAtEpochMs", "lastConfirmedAtEpochMs", "deletedAtEpochMs", "revisions")
        }
        val scope = value.string("scopeKind")
        val projectId = value.nullableString("projectId")
        val conversationId = value.nullableString("conversationId")
        requireScope(scope, projectId, conversationId)
        val entity = MemoryEntity(state.id, value.string("title"), value.string("body"), scope, projectId, conversationId, "$scope|${projectId.orEmpty()}|${conversationId.orEmpty()}", value.string("source"), value.string("sourceStableId"), value.string("sourceSummary"), value.string("status"), value.string("contentHash"), value.string("conceptHash"), value.long("createdAtEpochMs"), value.long("updatedAtEpochMs"), value.long("lastConfirmedAtEpochMs"), value.nullableLong("deletedAtEpochMs"), 1)
        val revisions = value.getJSONArray("revisions").objects().map { item ->
            item.requireKeys("id", "revision", "title", "body", "scopeKind", "projectId", "conversationId", "source", "sourceStableId", "sourceSummary", "status", "contentHash", "createdAtEpochMs")
            val revisionScope = item.string("scopeKind")
            val revisionProject = item.nullableString("projectId")
            val revisionConversation = item.nullableString("conversationId")
            requireScope(revisionScope, revisionProject, revisionConversation)
            MemoryRevisionEntity(item.string("id"), state.id, item.int("revision"), item.string("title"), item.string("body"), revisionScope, revisionProject, revisionConversation, item.string("source"), item.string("sourceStableId"), item.string("sourceSummary"), item.string("status"), item.string("contentHash"), item.long("createdAtEpochMs"), 1)
        }
        require(revisions.isNotEmpty() && revisions.map { it.id }.distinct().size == revisions.size)
        return MemoryPayload(entity, revisions)
    }

    private fun decodeRelation(state: com.nanzhufeng.ai.domain.P7ESemanticState): RelationPayload {
        val value = JSONObject(state.valueJson).also {
            it.requireKeys("relationshipKey", "type", "fromKnowledgeId", "toKnowledgeId", "scopeKind", "projectId", "status", "createdAtEpochMs", "updatedAtEpochMs", "revisions")
        }
        val entity = KnowledgeRelationshipEntity(state.id, value.string("relationshipKey"), value.string("type"), value.string("fromKnowledgeId"), value.string("toKnowledgeId"), value.string("scopeKind"), value.nullableString("projectId"), value.string("status"), value.long("createdAtEpochMs"), value.long("updatedAtEpochMs"), "p7e-restored-${state.id}-created", "p7e-restored-${state.id}-latest", "USER")
        val revisions = value.getJSONArray("revisions").objects().map { item ->
            item.requireKeys("id", "revision", "action", "status", "createdAtEpochMs")
            KnowledgeRelationshipRevisionEntity(item.string("id"), state.id, item.int("revision"), item.string("action"), item.string("status"), "p7e-restored-${state.id}-${item.int("revision")}", item.long("createdAtEpochMs"))
        }
        require(revisions.map { it.id }.distinct().size == revisions.size)
        return RelationPayload(entity, revisions)
    }

    private fun verifyReferences(decoded: DecodedSnapshot) {
        val projectIds = decoded.projects.map { it.entity.id }.toSet()
        val conversationIds = decoded.conversations.map { it.entity.id }.toSet()
        val knowledgeIds = decoded.knowledge.map { it.entity.id }.toSet()
        decoded.conversations.forEach { conversation ->
            require(conversation.entity.projectId == null || conversation.entity.projectId in projectIds) { "P7E conversation project reference missing" }
            val nodeIds = conversation.nodes.map { it.id }.toSet()
            require(conversation.entity.currentLeafMessageId == null || conversation.entity.currentLeafMessageId in nodeIds) { "P7E conversation leaf reference missing" }
            require(conversation.nodes.all { it.parentMessageId == null || it.parentMessageId in nodeIds }) { "P7E conversation parent reference missing" }
            require(conversation.nodes.all { it.revisesMessageId == null || it.revisesMessageId in nodeIds }) { "P7E conversation revision reference missing" }
        }
        decoded.knowledge.forEach { require(it.scope.projectId == null || it.scope.projectId in projectIds) { "P7E knowledge project reference missing" } }
        decoded.memories.forEach { memory ->
            require(memory.entity.projectId == null || memory.entity.projectId in projectIds) { "P7E memory project reference missing" }
            require(memory.entity.conversationId == null || memory.entity.conversationId in conversationIds) { "P7E memory conversation reference missing" }
        }
        decoded.relations.forEach { relation ->
            require(relation.entity.fromKnowledgeId in knowledgeIds && relation.entity.toKnowledgeId in knowledgeIds) { "P7E relation endpoint reference missing" }
            require(relation.entity.projectId == null || relation.entity.projectId in projectIds) { "P7E relation project reference missing" }
        }
    }

    private fun requireScope(scope: String, projectId: String?, conversationId: String?) {
        require(
            (scope == "GLOBAL" && projectId == null && conversationId == null) ||
                (scope == "PROJECT" && projectId != null && conversationId == null) ||
                (scope == "CONVERSATION" && projectId == null && conversationId != null),
        ) { "P7E memory scope invalid" }
    }

    private data class Candidate(val name: String, val payloadHash: String, val plan: P7ERestorePlan)
    private data class DecodedSnapshot(val projects: List<ProjectPayload>, val conversations: List<ConversationPayload>, val knowledge: List<KnowledgePayload>, val memories: List<MemoryPayload>, val relations: List<RelationPayload>)
    private data class ProjectPayload(val entity: ProjectEntity, val instructions: List<ProjectInstructionRevisionEntity>)
    private data class ConversationPayload(val entity: ConversationEntity, val nodes: List<MessageNodeEntity>, val blocksByNode: Map<String, List<MessageContentBlockEntity>>)
    private data class KnowledgePayload(val entity: KnowledgeItemEntity, val revisions: List<KnowledgeRevisionEntity>, val tags: List<String>, val revisionTags: Map<String, List<String>>, val scope: KnowledgeProjectScopeEntity)
    private data class MemoryPayload(val entity: MemoryEntity, val revisions: List<MemoryRevisionEntity>)
    private data class RelationPayload(val entity: KnowledgeRelationshipEntity, val revisions: List<KnowledgeRelationshipRevisionEntity>)

    private fun uniqueDatabaseName(kind: String) = ".nanfeng-ai-p7e-$kind-${UUID.randomUUID()}.db"

    /** Move SQLite's main file and its journal sidecars as one closed-database set. */
    private fun moveDatabaseSet(fromName: String, toName: String): Boolean {
        val from = app.getDatabasePath(fromName)
        val to = app.getDatabasePath(toName)
        if (!from.isFile || to.exists() || SQLITE_SIDECARS.any { File("${to.path}$it").exists() }) return false
        if (!from.renameTo(to)) return false
        val moved = mutableListOf<String>()
        for (suffix in SQLITE_SIDECARS) {
            val fromSidecar = File("${from.path}$suffix")
            if (!fromSidecar.exists()) continue
            if (!fromSidecar.renameTo(File("${to.path}$suffix"))) {
                moved.asReversed().forEach { movedSuffix ->
                    val targetSidecar = File("${to.path}$movedSuffix")
                    if (targetSidecar.exists()) targetSidecar.renameTo(File("${from.path}$movedSuffix"))
                }
                to.renameTo(from)
                return false
            }
            moved += suffix
        }
        return true
    }

    private companion object {
        const val APP_ID = "com.nanzhufeng.ai"
        const val SAFE_SETTINGS_ID = "safe-settings"
        val ALLOWED_KINDS = setOf("project", "conversation", "knowledge", "memory", "relation", "safe_settings")
        val SQLITE_SIDECARS = listOf("-wal", "-shm", "-journal")
    }
}

private fun JSONObject.requireKeys(vararg expected: String) {
    val actual = keys().asSequence().toSet()
    require(actual == expected.toSet()) { "P7E semantic value fields invalid: $actual" }
}
private fun JSONObject.string(name: String): String = get(name).also { require(it is String) { "P7E semantic string invalid: $name" } } as String
private fun JSONObject.int(name: String): Int = long(name).also { require(it in 0..Int.MAX_VALUE) { "P7E semantic integer invalid: $name" } }.toInt()
private fun JSONObject.long(name: String): Long = get(name).let { value -> require(value is Number && value.toDouble().isFinite() && value.toLong().toDouble() == value.toDouble()) { "P7E semantic number invalid: $name" }; value.toLong() }
private fun JSONObject.nullableString(name: String): String? = get(name).let { value -> if (value == JSONObject.NULL) null else (value as? String ?: error("P7E semantic nullable string invalid: $name")) }
private fun JSONObject.nullableLong(name: String): Long? = get(name).let { value -> if (value == JSONObject.NULL) null else { require(value is Number && value.toDouble().isFinite() && value.toLong().toDouble() == value.toDouble()) { "P7E semantic nullable number invalid: $name" }; value.toLong() } }
private fun JSONArray.objects(): List<JSONObject> = List(length()) { index -> get(index).also { require(it is JSONObject) { "P7E semantic array object invalid" } } as JSONObject }
private fun JSONArray.strings(): List<String> = List(length()) { index -> get(index).also { require(it is String) { "P7E semantic array string invalid" } } as String }

package com.nanzhufeng.ai.data

import android.content.Context
import com.nanzhufeng.ai.data.local.ConversationEntity
import com.nanzhufeng.ai.data.local.ConversationMemorySourceEntity
import com.nanzhufeng.ai.data.local.KnowledgeAttachmentEntity
import com.nanzhufeng.ai.data.local.KnowledgeEvidenceEntity
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
import com.nanzhufeng.ai.data.local.PrivateAttachmentAssetEntity
import com.nanzhufeng.ai.data.local.ProjectEntity
import com.nanzhufeng.ai.data.local.ProjectInstructionRevisionEntity
import com.nanzhufeng.ai.data.local.WorkspaceExchangeV2RestoreProvenanceEntity
import com.nanzhufeng.ai.data.local.WorkspaceExchangeV2RestoreReceiptEntity
import com.nanzhufeng.ai.data.local.WorkspaceExchangeV2RestoreSettingsEntity
import com.nanzhufeng.ai.domain.NfaiExchangeV2Ir
import com.nanzhufeng.ai.domain.WorkspaceExchangeV2AtomicRestoreCommit
import com.nanzhufeng.ai.domain.WorkspaceExchangeV2AtomicRestoreStore
import com.nanzhufeng.ai.domain.WorkspaceExchangeV2AtomicRestoreStoreResult
import com.nanzhufeng.ai.domain.WorkspaceExchangeV2LocalTruth
import com.nanzhufeng.ai.domain.WorkspaceExchangeV2RestoreReceipt
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject

/**
 * The only concrete Android v2 restore store. A journaled private-file staging root plus one
 * Room transaction is the recoverable boundary: crashes leave the journal and block all further
 * restores; ordinary failures roll the moved files back and publish no receipt.
 */
class AndroidWorkspaceExchangeV2AtomicRestoreStore(
    context: Context,
    private val database: NanfengAiDatabase,
) : WorkspaceExchangeV2AtomicRestoreStore {
    private val app = context.applicationContext
    private val stagingRoot = File(app.filesDir, ".nfai-v2-restore")
    private val attachmentRoot = File(app.filesDir, "attachments/v1")

    override fun receipt(intentId: String): WorkspaceExchangeV2RestoreReceipt? =
        database.workspaceExchangeV2RestoreDao().receipt(intentId)?.toDomain(database.workspaceExchangeV2RestoreDao().provenance())

    override fun localTruth(): WorkspaceExchangeV2LocalTruth = when {
        hasJournal() -> WorkspaceExchangeV2LocalTruth.RECOVERY_REQUIRED
        !databaseTruthEmpty() || attachmentRoot.listFiles()?.isNotEmpty() == true -> WorkspaceExchangeV2LocalTruth.PRESENT
        else -> WorkspaceExchangeV2LocalTruth.EMPTY
    }

    override fun commitEmptyLocal(commit: WorkspaceExchangeV2AtomicRestoreCommit): WorkspaceExchangeV2AtomicRestoreStoreResult {
        val decoded = runCatching { DecodedWorkspace.decode(commit) }.getOrElse { return WorkspaceExchangeV2AtomicRestoreStoreResult.FailedRecoverably }
        val existing = receipt(commit.receipt.intentId)
        if (existing != null) return if (existing.matches(commit.receipt)) WorkspaceExchangeV2AtomicRestoreStoreResult.Replayed(existing) else WorkspaceExchangeV2AtomicRestoreStoreResult.IntentConflict
        if (localTruth() != WorkspaceExchangeV2LocalTruth.EMPTY) return WorkspaceExchangeV2AtomicRestoreStoreResult.LocalTruthPresent
        val journal = File(stagingRoot, commit.receipt.intentId)
        val staged = runCatching { stageAssets(journal, decoded.assets, commit.packageRead.assets) }.getOrElse { return WorkspaceExchangeV2AtomicRestoreStoreResult.FailedRecoverably }
        val moved = mutableListOf<Pair<File, File>>()
        return try {
            val result = database.runInTransaction<WorkspaceExchangeV2AtomicRestoreStoreResult> {
                val replay = receipt(commit.receipt.intentId)
                when {
                    replay != null -> if (replay.matches(commit.receipt)) WorkspaceExchangeV2AtomicRestoreStoreResult.Replayed(replay) else WorkspaceExchangeV2AtomicRestoreStoreResult.IntentConflict
                    !databaseTruthEmpty() || hasOtherJournal(journal) || attachmentRoot.listFiles()?.isNotEmpty() == true -> WorkspaceExchangeV2AtomicRestoreStoreResult.LocalTruthPresent
                    else -> {
                        attachmentRoot.mkdirs()
                        staged.forEach { (stage, final) ->
                            if (!stage.renameTo(final)) error("v2 attachment promotion failed")
                            moved += stage to final
                        }
                        write(decoded, commit.receipt)
                        verifyReadback(decoded, commit.receipt)
                        WorkspaceExchangeV2AtomicRestoreStoreResult.Committed(commit.receipt)
                    }
                }
            }
            if (result !is WorkspaceExchangeV2AtomicRestoreStoreResult.FailedRecoverably) journal.deleteRecursively()
            result
        } catch (_: Throwable) {
            moved.asReversed().forEach { (stage, final) -> if (final.exists()) final.renameTo(stage) }
            WorkspaceExchangeV2AtomicRestoreStoreResult.FailedRecoverably
        }
    }

    private fun stageAssets(journal: File, assets: List<Asset>, bytes: Map<String, ByteArray>): List<Pair<File, File>> {
        val parent = requireNotNull(journal.parentFile)
        require(!journal.exists() && (parent.exists() || parent.mkdirs()))
        val stageRoot = File(journal, "assets").also { require(it.mkdirs()) }
        return assets.map { asset ->
            val value = requireNotNull(bytes[asset.entry]) { "v2 asset bytes missing" }
            require(value.size.toLong() == asset.byteCount && sha256(value) == asset.sha256)
            val staged = File(stageRoot, "${asset.sha256}${extensionFor(asset.mimeType)}")
            FileOutputStream(staged).use { out -> out.write(value); out.fd.sync() }
            require(staged.length() == asset.byteCount && sha256(staged.readBytes()) == asset.sha256)
            staged to File(attachmentRoot, staged.name)
        }
    }

    private fun write(decoded: DecodedWorkspace, receipt: WorkspaceExchangeV2RestoreReceipt) {
        decoded.projects.forEach { (entity, revisions) -> database.projectDao().insertProject(entity); revisions.forEach(database.projectDao()::insertRevision) }
        decoded.assets.forEach { asset -> database.privateAttachmentAssetDao().insert(PrivateAttachmentAssetEntity(asset.id, "attachments/v1/${asset.sha256}${extensionFor(asset.mimeType)}", asset.mimeType, asset.displayName, asset.byteCount, asset.sha256)) }
        decoded.conversations.forEach { value ->
            database.conversationDao().insertConversation(value.entity); value.nodes.forEach(database.conversationDao()::insertNode); value.blocks.values.forEach(database.conversationDao()::insertBlocks); database.conversationDao().insertMemorySources(value.memorySources)
        }
        decoded.knowledge.forEach { value ->
            database.knowledgeDao().insertKnowledge(value.entity); database.knowledgeDao().insertEvidence(value.evidence); database.knowledgeDao().insertAttachments(value.attachments); value.revisions.forEach(database.knowledgeDao()::insertRevision)
            database.knowledgeDao().insertTags(value.tags.map(::KnowledgeTagEntity)); database.knowledgeDao().insertItemTags(value.tags.map { KnowledgeItemTagEntity(value.entity.id, it) }); value.revisionTags.forEach { (id, tags) -> database.knowledgeDao().insertRevisionTags(tags.map { KnowledgeRevisionTagEntity(id, it) }) }; database.knowledgeDao().upsertProjectScope(value.scope)
        }
        decoded.memories.forEach { (entity, revisions) -> database.memoryDao().insertMemory(entity); revisions.forEach(database.memoryDao()::insertRevision) }
        decoded.relations.forEach { (entity, revisions) -> database.knowledgeRelationshipDao().insert(entity); revisions.forEach(database.knowledgeRelationshipDao()::insertRevision) }
        database.workspaceExchangeV2RestoreDao().insertSettings(decoded.settings)
        database.workspaceExchangeV2RestoreDao().insertProvenance(receipt.ownerFieldHashes.map { (key, hash) -> val (kind, id) = key.split('/', limit = 2); WorkspaceExchangeV2RestoreProvenanceEntity(kind, id, receipt.packageHash, receipt.semanticHash, hash, receipt.importedAt.toEpochMilli()) })
        database.workspaceExchangeV2RestoreDao().insertReceipt(receipt.toEntity())
    }

    private fun verifyReadback(decoded: DecodedWorkspace, receipt: WorkspaceExchangeV2RestoreReceipt) {
        require(database.workspaceExchangeV2RestoreDao().receipt(receipt.intentId)?.toDomain(database.workspaceExchangeV2RestoreDao().provenance())?.matches(receipt) == true)
        require(database.workspaceExchangeV2RestoreDao().provenanceCount() == receipt.ownerFieldHashes.size)
        require(database.projectDao().listAllForP7ESemanticSnapshot().size == decoded.projects.size)
        require(database.conversationDao().listAllForP7ESemanticSnapshot().size == decoded.conversations.size)
        require(database.knowledgeDao().listKnowledgeIncludingHidden().size == decoded.knowledge.size)
        require(database.memoryDao().listMemories(null, null).size == decoded.memories.size)
        require(database.knowledgeRelationshipDao().all().size == decoded.relations.size)
        require(database.privateAttachmentAssetDao().assetCount() == decoded.assets.size)
    }

    private fun databaseTruthEmpty() = database.projectDao().listAllForP7ESemanticSnapshot().isEmpty() && database.conversationDao().listAllForP7ESemanticSnapshot().isEmpty() && database.knowledgeDao().listKnowledgeIncludingHidden().isEmpty() && database.memoryDao().listMemories(null, null).isEmpty() && database.knowledgeRelationshipDao().all().isEmpty() && database.privateAttachmentAssetDao().assetCount() == 0 && database.workspaceExchangeV2RestoreDao().receiptCount() == 0 && database.workspaceExchangeV2RestoreDao().provenanceCount() == 0 && database.workspaceExchangeV2RestoreDao().settingsCount() == 0
    private fun hasJournal() = stagingRoot.listFiles()?.isNotEmpty() == true
    private fun hasOtherJournal(own: File) = stagingRoot.listFiles()?.any { it != own } == true
    private fun extensionFor(mime: String) = mapOf("image/jpeg" to ".jpg", "image/png" to ".png", "image/webp" to ".webp", "video/mp4" to ".mp4", "audio/mpeg" to ".mp3", "audio/wav" to ".wav", "audio/mp4" to ".m4a", "application/pdf" to ".pdf", "text/markdown" to ".md", "application/json" to ".json", "text/csv" to ".csv", "text/plain" to ".txt").getValue(mime)
    private fun sha256(value: ByteArray) = java.security.MessageDigest.getInstance("SHA-256").digest(value).joinToString("") { "%02x".format(it) }

    private data class Asset(val id: String, val entry: String, val mimeType: String, val displayName: String, val byteCount: Long, val sha256: String)
    private data class ConversationPayload(val entity: ConversationEntity, val nodes: List<MessageNodeEntity>, val blocks: Map<String, List<MessageContentBlockEntity>>, val memorySources: List<ConversationMemorySourceEntity>)
    private data class KnowledgePayload(val entity: KnowledgeItemEntity, val evidence: List<KnowledgeEvidenceEntity>, val attachments: List<KnowledgeAttachmentEntity>, val revisions: List<KnowledgeRevisionEntity>, val tags: List<String>, val revisionTags: Map<String, List<String>>, val scope: KnowledgeProjectScopeEntity)
    private data class DecodedWorkspace(val projects: List<Pair<ProjectEntity, List<ProjectInstructionRevisionEntity>>>, val conversations: List<ConversationPayload>, val knowledge: List<KnowledgePayload>, val memories: List<Pair<MemoryEntity, List<MemoryRevisionEntity>>>, val relations: List<Pair<KnowledgeRelationshipEntity, List<KnowledgeRelationshipRevisionEntity>>>, val assets: List<Asset>, val settings: WorkspaceExchangeV2RestoreSettingsEntity) {
        companion object {
            fun decode(commit: WorkspaceExchangeV2AtomicRestoreCommit): DecodedWorkspace {
                val exchange = JSONObject(commit.packageRead.exchangeJson); NfaiExchangeV2Ir.validate(exchange)
                val assets = linkedMapOf<String, Asset>()
                fun asset(value: JSONObject): Asset { val result = Asset(value.getString("id"), value.getString("entry"), value.getString("mimeType"), value.getString("displayName"), value.getLong("byteCount"), value.getString("sha256")); assets.putIfAbsent(result.id, result)?.let { require(it == result) }; return result }
                val projects = exchange.array("projects").map { p ->
                    val updated = p.instant("updatedAt"); ProjectEntity(p.getString("id"), p.getString("title"), p.getString("description"), p.getJSONObject("appearance").nullable("color"), p.getJSONObject("appearance").nullable("icon"), p.instant("createdAt"), updated, if (p.getBoolean("archived")) updated else null, if (p.getBoolean("pinned")) updated else null, null, p.getInt("schemaVersion")) to p.array("instructionHistory").map { r -> ProjectInstructionRevisionEntity(r.getString("id"), p.getString("id"), r.getInt("revision"), r.getString("content"), r.getString("source"), r.getString("contentHash"), r.instant("createdAt"), r.getInt("schemaVersion")) }
                }
                val conversations = exchange.array("conversations").map { c ->
                    val updated = c.instant("updatedAt"); val entity = ConversationEntity(c.getString("id"), c.getString("title"), c.nullable("projectId"), c.getString("currentLeafId"), c.instant("createdAt"), updated, c.getJSONObject("settings").nullable("defaultProviderId"), c.getJSONObject("settings").nullable("defaultModelId"), c.getJSONObject("settings").nullable("harnessId"), c.getJSONObject("settings").nullableInt("harnessVersion"), c.getJSONObject("settings").getInt("contextPolicyVersion"), if (c.getBoolean("archived")) updated else null, if (c.getBoolean("pinned")) updated else null, null, c.getInt("schemaVersion"), c.getLong("revision"), c.getBoolean("autoTitlePending"), c.getString("surface"))
                    val blocks = linkedMapOf<String, List<MessageContentBlockEntity>>(); val nodes = c.array("messages").map { n -> val id = n.getString("id"); blocks[id] = n.array("blocks").map { b -> when (b.getString("kind")) { "TEXT" -> MessageContentBlockEntity(id, b.getInt("ordinal"), "TEXT", b.getString("text"), null, null, null, null, null, null, null, null, 1); "ASSET_REF" -> asset(b.getJSONObject("asset")).let { a -> MessageContentBlockEntity(id, b.getInt("ordinal"), "ATTACHMENT", null, a.id, "attachments/v1/${a.sha256}${extension(a.mimeType)}", a.mimeType, a.displayName, a.byteCount, a.sha256, null, null, 1) }; else -> error("unsupported block") } }; MessageNodeEntity(id, c.getString("id"), n.nullable("parentId"), n.getInt("ordinal"), n.getString("role").uppercase(), n.instant("createdAt"), n.getString("delivery"), n.getInt("revision"), null, null, null, null, 1) }
                    ConversationPayload(entity, nodes, blocks, c.getJSONObject("settings").array("memorySources").mapIndexed { index, source -> ConversationMemorySourceEntity(c.getString("id"), index, source.getString("memoryId"), source.getString("sourceKind"), source.getInt("sourceVersion")) })
                }
                val knowledge = exchange.array("knowledge").map { k ->
                    val updated = k.instant("updatedAt"); val attachments = k.array("attachments").mapIndexed { index, value -> asset(value).let { a -> KnowledgeAttachmentEntity(k.getString("id"), index, a.id, "attachments/v1/${a.sha256}${extension(a.mimeType)}", a.mimeType, a.displayName, a.byteCount, a.sha256) } }; val history = k.array("history").map { h -> KnowledgeRevisionEntity(h.getString("id"), k.getString("id"), h.getInt("revision"), h.getString("title"), h.getString("body"), h.getString("status"), h.nullable("projectId"), h.getString("contentHash"), h.instant("createdAt")) }; val tags = k.getJSONArray("tags").let { List(it.length()) { index -> it.getString(index) } }; KnowledgePayload(KnowledgeItemEntity(k.getString("id"), k.getString("title"), k.getString("body"), k.getJSONObject("provenance").getString("candidateId"), k.getJSONObject("provenance").getString("invocationId"), k.getJSONObject("provenance").getString("providerId"), k.getJSONObject("provenance").getString("modelId"), k.getJSONObject("provenance").getInt("harnessVersion"), k.instant("createdAt"), k.getInt("schemaVersion"), k.getString("status"), updated, if (k.getString("status") == "ARCHIVED") updated else null, if (k.getString("status") == "DELETED") updated else null, k.getString("contentHash")), k.array("sourceEvidence").mapIndexed { index, e -> KnowledgeEvidenceEntity(k.getString("id"), index, e.getString("sourceType"), e.instant("receivedAt"), e.optString("sourceReference").ifBlank { null }, e.getJSONArray("contributedFields").let { values -> List(values.length()) { position -> values.getString(position) }.joinToString(",") }) }, attachments, history, tags, history.associate { it.id to tags }, KnowledgeProjectScopeEntity(k.getString("id"), k.nullable("projectId"), updated, k.getInt("schemaVersion")))
                }
                val memories = exchange.array("memory").map { m -> val scope = m.getString("scope"); val sid = m.nullable("scopeId"); val project = if (scope == "PROJECT") sid else null; val conversation = if (scope == "CONVERSATION") sid else null; val revisions = m.array("history").map { h -> val hs = h.getString("scope"); val hid = h.nullable("scopeId"); MemoryRevisionEntity(h.getString("id"), m.getString("id"), h.getInt("revision"), h.getString("title"), h.getString("body"), hs, if (hs == "PROJECT") hid else null, if (hs == "CONVERSATION") hid else null, h.getString("source"), h.getString("sourceStableId"), h.getString("sourceSummary"), h.getString("status"), h.getString("contentHash"), h.instant("createdAt"), h.getInt("schemaVersion")) }; MemoryEntity(m.getString("id"), m.getString("title"), m.getString("body"), scope, project, conversation, "$scope|${project.orEmpty()}|${conversation.orEmpty()}", m.getString("source"), m.getString("sourceStableId"), m.getString("sourceSummary"), m.getString("status"), m.getString("contentHash"), m.getString("conceptHash"), m.instant("createdAt"), m.instant("updatedAt"), m.instant("lastConfirmedAt"), m.nullableInstant("deletedAt"), m.getInt("schemaVersion")) to revisions }
                val relations = exchange.array("relations").map { r -> val revisions = r.array("history").map { h -> KnowledgeRelationshipRevisionEntity(h.getString("id"), r.getString("id"), h.getInt("revision"), h.getString("action"), h.getString("status"), h.getString("intentId"), h.instant("createdAt")) }; KnowledgeRelationshipEntity(r.getString("id"), "v2/${r.getString("type")}/${r.getString("fromId")}/${r.getString("toId")}/${r.getString("scope")}/${r.nullable("projectId").orEmpty()}", r.getString("type"), r.getString("fromId"), r.getString("toId"), r.getString("scope"), r.nullable("projectId"), r.getString("status"), r.instant("createdAt"), r.instant("updatedAt"), r.getString("createdByIntentId"), r.getString("latestIntentId"), r.getString("suggestionSource")) to revisions }
                return DecodedWorkspace(projects, conversations, knowledge, memories, relations, assets.values.toList(), WorkspaceExchangeV2RestoreSettingsEntity(uiLanguage = exchange.getJSONObject("settings").getString("uiLanguage"), theme = exchange.getJSONObject("settings").getString("theme"), packageHash = commit.receipt.packageHash, updatedAtEpochMs = commit.receipt.importedAt.toEpochMilli()))
            }
            private fun extension(mime: String) = mapOf("image/jpeg" to ".jpg", "image/png" to ".png", "image/webp" to ".webp", "video/mp4" to ".mp4", "audio/mpeg" to ".mp3", "audio/wav" to ".wav", "audio/mp4" to ".m4a", "application/pdf" to ".pdf", "text/markdown" to ".md", "application/json" to ".json", "text/csv" to ".csv", "text/plain" to ".txt").getValue(mime)
            private fun JSONObject.array(name: String) = getJSONArray(name).let { List(it.length()) { index -> it.get(index) as JSONObject } }
            private fun <T> JSONArray.map(block: (JSONObject) -> T): List<T> = List(length()) { index -> block(getJSONObject(index)) }
            private fun <T> JSONArray.mapIndexed(block: (Int, JSONObject) -> T) = List(length()) { index -> block(index, getJSONObject(index)) }
            private fun JSONObject.nullable(name: String): String? = get(name).let { if (it == JSONObject.NULL) null else it as String }
            private fun JSONObject.nullableInt(name: String): Int? = get(name).let { if (it == JSONObject.NULL) null else (it as Number).toInt() }
            private fun JSONObject.instant(name: String) = Instant.parse(getString(name)).toEpochMilli()
            private fun JSONObject.nullableInstant(name: String): Long? = nullable(name)?.let { Instant.parse(it).toEpochMilli() }
        }
    }
}

private fun WorkspaceExchangeV2RestoreReceipt.matches(other: WorkspaceExchangeV2RestoreReceipt) = intentId == other.intentId && packageHash == other.packageHash && semanticHash == other.semanticHash
private fun WorkspaceExchangeV2RestoreReceipt.toEntity() = WorkspaceExchangeV2RestoreReceiptEntity(intentId, packageHash, semanticHash, origin, sensitivity, rootCounts.getValue("projects"), rootCounts.getValue("conversations"), rootCounts.getValue("knowledge"), rootCounts.getValue("memory"), rootCounts.getValue("relations"), assetCount, assetBytes, importedAt.toEpochMilli(), importerVersion)
private fun WorkspaceExchangeV2RestoreReceiptEntity.toDomain(provenance: List<WorkspaceExchangeV2RestoreProvenanceEntity>) = WorkspaceExchangeV2RestoreReceipt(intentId, packageHash, semanticHash, origin, sensitivity, mapOf("projects" to projectCount, "conversations" to conversationCount, "knowledge" to knowledgeCount, "memory" to memoryCount, "relations" to relationCount), assetCount, assetBytes, provenance.associate { "${it.ownerKind}/${it.ownerId}" to it.ownerFieldHash }, Instant.ofEpochMilli(importedAtEpochMs), importerVersion)

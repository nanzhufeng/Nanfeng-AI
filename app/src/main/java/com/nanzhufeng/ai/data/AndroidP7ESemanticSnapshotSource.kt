package com.nanzhufeng.ai.data

import com.nanzhufeng.ai.data.local.NanfengAiDatabase
import com.nanzhufeng.ai.domain.P7ESemanticSnapshot
import com.nanzhufeng.ai.domain.P7ESemanticSnapshotMapper
import com.nanzhufeng.ai.domain.P7ESemanticState
import org.json.JSONArray
import org.json.JSONObject

/**
 * Android's P7-E source boundary.  It reads typed domain facts through the Room DAOs and maps
 * them one stable object at a time into `nfai.sync.semantic-record.v1`; it never serializes a
 * Room table, database handle, asset, provider/runtime field, URI, path or credential.
 *
 * Objects that cannot be represented completely by this allowlist reject the entire plan.  In
 * particular attachments, Tool blocks, unsent drafts and Knowledge evidence are not silently
 * stripped.  The companion restore writer must consume only the prepared output of this source.
 */
class AndroidP7ESemanticSnapshotSource(
    private val database: NanfengAiDatabase,
    private val appId: String = "com.nanzhufeng.ai",
) {
    fun snapshot(documentId: String, revision: Long): P7ESemanticSnapshot {
        require(documentId.isNotBlank() && revision > 0)
        val states = buildList {
            database.projectDao().listAllForP7ESemanticSnapshot().forEach { project ->
                val instructions = JSONArray().also { array ->
                    database.projectDao().revisionsFor(project.id).forEach { item -> array.put(json {
                        put("id", item.id); put("revision", item.revision); put("content", item.content)
                        put("source", item.source); put("contentHash", item.contentHash); put("createdAtEpochMs", item.createdAtEpochMs)
                    }) }
                }
                add(state("project", project.id, instructions.length().coerceAtLeast(1).toLong(), json {
                    put("title", project.title); put("description", project.description); putNullable("colorSemantic", project.colorSemantic)
                    putNullable("iconSemantic", project.iconSemantic); put("createdAtEpochMs", project.createdAtEpochMs); put("updatedAtEpochMs", project.updatedAtEpochMs)
                    putNullable("archivedAtEpochMs", project.archivedAtEpochMs); putNullable("pinnedAtEpochMs", project.pinnedAtEpochMs); putNullable("deletedAtEpochMs", project.deletedAtEpochMs)
                    put("instructionRevisions", instructions)
                }))
            }
            database.conversationDao().listAllForP7ESemanticSnapshot().forEach { conversation ->
                val draft = database.conversationDao().draftFor(conversation.id)
                require(draft == null || draft.text.isEmpty()) { "P7E unsent conversation draft is outside the sync allowlist" }
                require(database.conversationDao().draftAttachmentsFor(conversation.id).isEmpty()) { "P7E draft attachment is outside the sync allowlist" }
                val nodes = JSONArray().also { array ->
                    database.conversationDao().nodesFor(conversation.id).forEach { node ->
                        val blocks = database.conversationDao().blocksFor(node.id)
                        require(blocks.all { it.kind == "TEXT" && it.attachmentId == null && it.storageKey == null && it.toolName == null }) {
                            "P7E conversation attachment or Tool block is outside the sync allowlist"
                        }
                        array.put(json {
                            put("id", node.id); putNullable("parentMessageId", node.parentMessageId); put("siblingPosition", node.siblingPosition)
                            put("role", node.role); put("createdAtEpochMs", node.createdAtEpochMs); put("deliveryState", node.deliveryState)
                            put("revision", node.revision); putNullable("revisesMessageId", node.revisesMessageId)
                            put("content", JSONArray().also { texts -> blocks.forEach { texts.put(it.textContent ?: "") } })
                        })
                    }
                }
                add(state("conversation", conversation.id, nodes.length().coerceAtLeast(1).toLong(), json {
                    put("title", conversation.title); putNullable("projectId", conversation.projectId); putNullable("currentLeafMessageId", conversation.currentLeafMessageId)
                    put("createdAtEpochMs", conversation.createdAtEpochMs); put("updatedAtEpochMs", conversation.updatedAtEpochMs)
                    putNullable("archivedAtEpochMs", conversation.archivedAtEpochMs); putNullable("pinnedAtEpochMs", conversation.pinnedAtEpochMs); putNullable("deletedAtEpochMs", conversation.deletedAtEpochMs)
                    put("nodes", nodes)
                }))
            }
            database.knowledgeDao().listKnowledgeIncludingHidden().forEach { knowledge ->
                require(database.knowledgeDao().attachmentsFor(knowledge.id).isEmpty()) { "P7E Knowledge attachment is outside the sync allowlist" }
                require(database.knowledgeDao().evidenceFor(knowledge.id).isEmpty()) { "P7E Knowledge evidence is outside the sync allowlist" }
                val revisions = database.knowledgeDao().revisionsFor(knowledge.id)
                require(revisions.isNotEmpty()) { "P7E Knowledge without an append-only revision cannot be restored faithfully" }
                val revisionValues = JSONArray().also { array -> revisions.forEach { item -> array.put(json {
                    put("id", item.id); put("revision", item.revision); put("title", item.title); put("body", item.body); put("status", item.status)
                    putNullable("projectId", item.projectId); put("contentHash", item.contentHash); put("createdAtEpochMs", item.createdAtEpochMs)
                    put("tags", JSONArray(database.knowledgeDao().tagsForRevision(item.id)))
                }) } }
                add(state("knowledge", knowledge.id, revisions.maxOfOrNull { it.revision }?.toLong() ?: 1, json {
                    put("title", knowledge.title); put("body", knowledge.body); put("status", knowledge.status); put("createdAtEpochMs", knowledge.createdAtEpochMs)
                    put("updatedAtEpochMs", knowledge.updatedAtEpochMs); putNullable("archivedAtEpochMs", knowledge.archivedAtEpochMs); putNullable("deletedAtEpochMs", knowledge.deletedAtEpochMs)
                    put("contentHash", knowledge.contentHash); putNullable("projectId", database.knowledgeDao().projectScopeFor(knowledge.id))
                    put("tags", JSONArray(database.knowledgeDao().tagsFor(knowledge.id))); put("revisions", revisionValues)
                }))
            }
            database.memoryDao().listMemories(null, null).forEach { memory ->
                val revisions = database.memoryDao().revisionsFor(memory.id)
                require(revisions.isNotEmpty()) { "P7E Memory without an append-only revision cannot be restored faithfully" }
                val revisionValues = JSONArray().also { array -> revisions.forEach { item -> array.put(json {
                    put("id", item.id); put("revision", item.revision); put("title", item.title); put("body", item.body); put("scopeKind", item.scopeKind)
                    putNullable("projectId", item.projectId); putNullable("conversationId", item.conversationId); put("source", item.source); put("sourceStableId", item.sourceStableId)
                    put("sourceSummary", item.sourceSummary); put("status", item.status); put("contentHash", item.contentHash); put("createdAtEpochMs", item.createdAtEpochMs)
                }) } }
                add(state("memory", memory.id, revisions.maxOfOrNull { it.revision }?.toLong() ?: 1, json {
                    put("title", memory.title); put("body", memory.body); put("scopeKind", memory.scopeKind); putNullable("projectId", memory.projectId); putNullable("conversationId", memory.conversationId)
                    put("source", memory.source); put("sourceStableId", memory.sourceStableId); put("sourceSummary", memory.sourceSummary); put("status", memory.status)
                    put("contentHash", memory.contentHash); put("conceptHash", memory.conceptHash); put("createdAtEpochMs", memory.createdAtEpochMs); put("updatedAtEpochMs", memory.updatedAtEpochMs)
                    put("lastConfirmedAtEpochMs", memory.lastConfirmedAtEpochMs); putNullable("deletedAtEpochMs", memory.deletedAtEpochMs); put("revisions", revisionValues)
                }))
            }
            database.knowledgeRelationshipDao().all().forEach { relation ->
                val revisions = database.knowledgeRelationshipDao().revisions(relation.id)
                require(revisions.isNotEmpty()) { "P7E relationship without an append-only revision cannot be restored faithfully" }
                add(state("relation", relation.id, revisions.maxOfOrNull { it.revision }?.toLong() ?: 1, json {
                    put("relationshipKey", relation.relationshipKey); put("type", relation.type); put("fromKnowledgeId", relation.fromKnowledgeId); put("toKnowledgeId", relation.toKnowledgeId)
                    put("scopeKind", relation.scopeKind); putNullable("projectId", relation.projectId); put("status", relation.status); put("createdAtEpochMs", relation.createdAtEpochMs); put("updatedAtEpochMs", relation.updatedAtEpochMs)
                    put("revisions", JSONArray().also { array -> revisions.forEach { item -> array.put(json {
                        put("id", item.id); put("revision", item.revision); put("action", item.action); put("status", item.status); put("createdAtEpochMs", item.createdAtEpochMs)
                    }) } })
                }))
            }
            // Android has no persistent appearance/account preference in the P7-E allowlist yet.
            // A fixed empty safe-settings object preserves the explicit record without inventing a
            // path to provider/model/account preferences.
            add(state("safe_settings", "safe-settings", 1, json { put("values", JSONObject()) }))
        }
        val snapshot = P7ESemanticSnapshot(appId, documentId, revision, states)
        P7ESemanticSnapshotMapper.toPreparedSnapshot(snapshot) // strict field/high-sensitivity gate; no partial output.
        return snapshot
    }

    private fun state(kind: String, id: String, revision: Long, value: JSONObject) =
        P7ESemanticState(kind, id, revision, valueJson = value.toString())

    private fun json(block: JSONObject.() -> Unit) = JSONObject().apply(block)
    private fun JSONObject.putNullable(name: String, value: Any?) = put(name, value ?: JSONObject.NULL)
}

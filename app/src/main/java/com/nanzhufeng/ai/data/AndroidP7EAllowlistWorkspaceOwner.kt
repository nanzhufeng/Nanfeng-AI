package com.nanzhufeng.ai.data

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.nanzhufeng.ai.data.local.NanfengAiDatabase
import com.nanzhufeng.ai.domain.MemoryDomain
import com.nanzhufeng.ai.domain.NfaiSyncPreparedSnapshot
import com.nanzhufeng.ai.domain.NfaiSyncRecord
import com.nanzhufeng.ai.domain.P7EAtomicAllowlistRestoreWriter
import com.nanzhufeng.ai.domain.P7ERestorePlan
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/**
 * Provisional raw Room staging kept only to regression-test the old P7-E checkpoint mechanics.
 * It is not the P7-E common semantic mapper and must not be bound as a production restore path.
 * `P7ESemanticSnapshotMapper` is the only permitted cross-platform payload boundary.
 *
 * P5-D remains the owner of `.nfai-backup`; this class never accepts that format or a backup
 * database.  No cloud, key, recovery-code, account or UI dependency is present here.
 */
@Deprecated("P7-E raw Room table staging is not a common semantic restore writer.", level = DeprecationLevel.WARNING)
class AndroidP7EAllowlistWorkspaceOwner(
    context: Context,
    private val database: NanfengAiDatabase,
    private val appId: String = APP_ID,
) : P7EAtomicAllowlistRestoreWriter {
    private val app = context.applicationContext
    private val dbFile = app.getDatabasePath(DB_NAME)
    private val root = File(app.filesDir, "p7e-allowlist-restore/v1")
    private val staged = mutableMapOf<String, StagedCandidate>()
    private val checkpoints = mutableMapOf<String, File>()

    /** Reads the live Room database but returns only P7-A safe, canonical allowlist records. */
    fun snapshot(documentId: String, revision: Long): NfaiSyncPreparedSnapshot {
        require(revision > 0)
        requireNoUnmappedAssets()
        val source = database.openHelper.readableDatabase
        val records = GROUPS.map { group ->
            val tables = JSONObject()
            group.tables.forEach { table -> tables.put(table, rows(source, table)) }
            val content = JSONObject().put("format", FORMAT).put("schema", SCHEMA).put("tables", tables)
            requireSafe(content)
            NfaiSyncRecord(group.kind, group.recordId, revision, "NORMAL", canonical(content))
        }
        return NfaiSyncPreparedSnapshot(appId, documentId, revision, records)
    }

    override fun isLocalBusinessEmpty(): Boolean = GROUPS.all { group ->
        group.tables.all { table ->
            database.openHelper.readableDatabase.query("SELECT EXISTS(SELECT 1 FROM `$table`)").use { cursor ->
                cursor.moveToFirst() && cursor.getLong(0) == 0L
            }
        }
    }

    /** A VACUUM checkpoint is used instead of copying WAL/SHM, exactly as P5-D requires. */
    override fun createCheckpoint(): String {
        val id = "checkpoint-${UUID.randomUUID()}"
        val dir = File(root, "checkpoints/$id").also { it.mkdirs() }
        val file = File(dir, "room.sqlite")
        snapshotDatabase(file)
        checkpoints[id] = file
        return id
    }

    /**
     * Builds a full candidate from a consistent local snapshot, then replaces only P7-E tables.
     * The live Room handle is never written.  A missing/unknown field fails the whole plan.
     */
    override fun stage(plan: P7ERestorePlan): String {
        require(plan.records.map { it.kind }.toSet() == GROUPS.map { it.kind }.toSet()) { "P7E allowlist kinds incomplete" }
        val id = "stage-${UUID.randomUUID()}"
        val dir = File(root, "staging/$id").also { it.mkdirs() }
        val candidate = File(dir, "room.sqlite")
        snapshotDatabase(candidate)
        try {
            val incoming = decode(plan)
            requireNoUnmappedAssets(incoming)
            SQLiteDatabase.openDatabase(candidate.path, null, SQLiteDatabase.OPEN_READWRITE).use { sqlite ->
                sqlite.beginTransaction()
                try {
                    sqlite.execSQL("PRAGMA foreign_keys=OFF")
                    DELETE_ORDER.forEach { sqlite.delete(it, null, null) }
                    INSERT_ORDER.forEach { table -> incoming[table]?.forEach { row -> insert(sqlite, table, row) } }
                    sqlite.execSQL("PRAGMA foreign_keys=ON")
                    sqlite.setTransactionSuccessful()
                } finally { sqlite.endTransaction() }
            }
            require(sqliteHealthy(candidate)) { "P7E staged SQLite integrity failed" }
            val readback = recordsFrom(candidate, plan.remoteRevision)
            require(readback.sortedBy { it.kind }.map { it.kind to it.contentJson } == plan.records.sortedBy { it.kind }.map { it.kind to it.contentJson }) { "P7E staged readback mismatch" }
            staged[id] = StagedCandidate(candidate, plan.payloadHash)
            return id
        } catch (error: Throwable) {
            dir.deleteRecursively()
            throw error
        }
    }

    /** The caller must restart after success; raw readback is deliberately performed before it. */
    override fun commitAtomically(stagingRef: String): String {
        val stagedCandidate = staged.remove(stagingRef) ?: error("P7E staging missing")
        val candidate = stagedCandidate.file
        require(candidate.isFile && sqliteHealthy(candidate)) { "P7E candidate invalid" }
        val old = File(dbFile.parentFile, ".${dbFile.name}.p7e-old")
        database.close()
        try {
            if (old.exists()) old.delete()
            if (dbFile.exists() && !dbFile.renameTo(old)) error("P7E cannot move live database")
            if (!candidate.renameTo(dbFile)) error("P7E cannot expose staged database")
            old.delete()
            return stagedCandidate.payloadHash
        } catch (error: Throwable) {
            if (dbFile.exists()) dbFile.delete()
            if (old.exists()) old.renameTo(dbFile)
            throw error
        } finally { candidate.parentFile?.deleteRecursively() }
    }

    override fun rollback(checkpointRef: String) {
        val checkpoint = checkpoints.remove(checkpointRef) ?: return
        if (!checkpoint.isFile || !sqliteHealthy(checkpoint)) return
        database.close()
        val old = File(dbFile.parentFile, ".${dbFile.name}.p7e-rollback-old")
        runCatching {
            if (old.exists()) old.delete()
            if (dbFile.exists()) dbFile.renameTo(old)
            if (!checkpoint.renameTo(dbFile)) checkpoint.copyTo(dbFile, overwrite = true)
            old.delete()
        }
        checkpoint.parentFile?.deleteRecursively()
    }

    private fun decode(plan: P7ERestorePlan): Map<String, List<JSONObject>> {
        val byKind = plan.records.associateBy { it.kind }
        return buildMap {
            GROUPS.forEach { group ->
                val content = JSONObject(byKind.getValue(group.kind).contentJson)
                require(content.optString("format") == FORMAT && content.optInt("schema") == SCHEMA)
                val tables = content.getJSONObject("tables")
                require(tables.keys().asSequence().toSet() == group.tables.toSet())
                group.tables.forEach { table ->
                    val rows = tables.getJSONArray(table)
                    put(table, List(rows.length()) { index -> rows.getJSONObject(index) })
                }
            }
        }
    }

    private fun rows(db: SupportSQLiteDatabase, table: String): JSONArray = JSONArray().also { out ->
        db.query("SELECT * FROM `$table` ORDER BY rowid").use { cursor ->
            val columns = cursor.columnNames
            while (cursor.moveToNext()) {
                val row = JSONObject()
                columns.forEachIndexed { index, column -> row.put(alias(column), cursorValue(cursor, index) ?: JSONObject.NULL) }
                out.put(row)
            }
        }
    }

    private fun insert(db: SQLiteDatabase, table: String, encoded: JSONObject) {
        val allowed = columnsFor(table)
        require(encoded.keys().asSequence().map(::unalias).toSet() == allowed.toSet()) { "P7E row fields do not match $table: encoded=${encoded.keys().asSequence().map(::unalias).toSet()} allowed=${allowed.toSet()}" }
        val values = android.content.ContentValues()
        allowed.forEach { column ->
            val value = encoded.opt( alias(column) )
            when (value) {
                null, JSONObject.NULL -> values.putNull(column)
                is String -> values.put(column, value)
                is Boolean -> values.put(column, if (value) 1 else 0)
                is Int -> values.put(column, value)
                is Long -> values.put(column, value)
                is Number -> values.put(column, value.toLong())
                else -> error("P7E unsupported SQLite value")
            }
        }
        require(db.insertOrThrow(table, null, values) >= 0) { "P7E insert failed" }
    }

    private fun columnsFor(table: String): List<String> = SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
        db.rawQuery("PRAGMA table_info(`$table`)", null).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(1)) } }
    }

    private fun recordsFrom(file: File, revision: Long): List<NfaiSyncRecord> {
        val db = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
        return try { GROUPS.map { group ->
                val tables = JSONObject()
                group.tables.forEach { table ->
                    val rows = JSONArray()
                    db.rawQuery("SELECT * FROM `$table` ORDER BY rowid", null).use { cursor ->
                        val columns = cursor.columnNames
                        while (cursor.moveToNext()) {
                            val row = JSONObject()
                            columns.forEachIndexed { index, column -> row.put(alias(column), cursorValue(cursor, index) ?: JSONObject.NULL) }
                            rows.put(row)
                        }
                    }
                    tables.put(table, rows)
                }
                NfaiSyncRecord(group.kind, group.recordId, revision, "NORMAL", canonical(JSONObject().put("format", FORMAT).put("schema", SCHEMA).put("tables", tables)))
            }
        } finally { db.close() }
    }

    private fun requireNoUnmappedAssets() {
        val db = database.openHelper.readableDatabase
        val hasAssets = listOf(
            "SELECT EXISTS(SELECT 1 FROM knowledge_attachments)",
            "SELECT EXISTS(SELECT 1 FROM conversation_draft_attachments)",
            "SELECT EXISTS(SELECT 1 FROM message_content_blocks WHERE attachmentId IS NOT NULL)",
        ).any { sql -> db.query(sql).use { it.moveToFirst() && it.getLong(0) != 0L } }
        require(!hasAssets) { "P7E asset bytes are not in the sync allowlist" }
    }

    private fun requireNoUnmappedAssets(incoming: Map<String, List<JSONObject>>) {
        val attachments = listOf("knowledge_attachments", "conversation_draft_attachments").any { incoming[it]?.isNotEmpty() == true }
        val messageAsset = incoming["message_content_blocks"].orEmpty().any { !it.isNull("attachmentId") || !it.isNull("storageKey") }
        require(!attachments && !messageAsset) { "P7E asset bytes are not in the sync allowlist" }
    }

    private fun snapshotDatabase(target: File) {
        target.parentFile?.mkdirs(); target.delete()
        val escaped = target.absolutePath.replace("'", "''")
        database.openHelper.writableDatabase.execSQL("VACUUM INTO '$escaped'")
        require(target.isFile && sqliteHealthy(target))
    }

    private fun sqliteHealthy(file: File): Boolean = runCatching {
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("PRAGMA integrity_check", null).use { it.moveToFirst() && it.getString(0) == "ok" }
        }
    }.getOrDefault(false)

    private fun requireSafe(value: Any?) {
        when (value) {
            is JSONObject -> value.keys().asSequence().toList().forEach { key -> requireSafe(value.get(key)) }
            is JSONArray -> repeat(value.length()) { requireSafe(value.get(it)) }
            is String -> require(MemoryDomain.sensitiveRejection(value) == null) { "P7E sensitive text" }
        }
    }

    private fun cursorValue(cursor: Cursor, index: Int): Any? = when (cursor.getType(index)) {
        Cursor.FIELD_TYPE_NULL -> null
        Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index)
        Cursor.FIELD_TYPE_FLOAT -> error("P7E floating values are not supported")
        Cursor.FIELD_TYPE_STRING -> cursor.getString(index)
        else -> error("P7E blob values are not allowed")
    }

    private fun alias(column: String) = when (column) {
        "providerId" -> "serviceId"
        "defaultProviderId" -> "defaultServiceId"
        "lastPersistedSequence" -> "lastSequence"
        "resumableFromSequence" -> "resumeSequence"
        else -> column
    }
    private fun unalias(column: String) = when (column) {
        "serviceId" -> "providerId"
        "defaultServiceId" -> "defaultProviderId"
        "lastSequence" -> "lastPersistedSequence"
        "resumeSequence" -> "resumableFromSequence"
        else -> column
    }
    private fun canonical(value: Any?): String = when (value) {
        null, JSONObject.NULL -> "null"
        is String -> JSONObject.quote(value)
        is Boolean -> value.toString()
        is Number -> value.toLong().toString()
        is JSONObject -> value.keys().asSequence().toList().sorted().joinToString(",", "{", "}") { "${JSONObject.quote(it)}:${canonical(value.get(it))}" }
        is JSONArray -> (0 until value.length()).joinToString(",", "[", "]") { canonical(value.get(it)) }
        else -> error("P7E unsupported JSON")
    }
    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private data class Group(val kind: String, val recordId: String, val tables: List<String>)
    private data class StagedCandidate(val file: File, val payloadHash: String)
    private companion object {
        const val APP_ID = "com.nanzhufeng.ai"
        const val DB_NAME = "nanfeng-ai.db"
        const val FORMAT = "nfai.sync.android.room.allowlist.v1"
        const val SCHEMA = 19
        val GROUPS = listOf(
            Group("project", "p7e-projects", listOf("projects", "project_instruction_revisions", "project_intents")),
            Group("conversation", "p7e-conversations", listOf("conversations", "message_nodes", "message_content_blocks", "conversation_drafts", "conversation_draft_attachments", "conversation_memory_sources", "conversation_management_intents")),
            Group("knowledge", "p7e-knowledge", listOf("knowledge_items", "knowledge_revisions", "knowledge_tags", "knowledge_item_tags", "knowledge_revision_tags", "knowledge_evidence", "knowledge_attachments", "knowledge_project_scopes")),
            Group("memory", "p7e-memory", listOf("memories", "memory_revisions", "memory_intents", "memory_conflicts")),
            Group("relation", "p7e-relations", listOf("knowledge_relationships", "knowledge_relationship_revisions", "knowledge_relationship_intents")),
            Group("safe_settings", "p7e-safe-settings", emptyList()),
        )
        val INSERT_ORDER = GROUPS.flatMap { it.tables }
        val DELETE_ORDER = INSERT_ORDER.reversed()
    }
}

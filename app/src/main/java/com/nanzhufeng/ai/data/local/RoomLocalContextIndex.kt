package com.nanzhufeng.ai.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import com.nanzhufeng.ai.domain.ConversationId
import com.nanzhufeng.ai.domain.ContextRetrievalScope
import com.nanzhufeng.ai.domain.LocalContextIndex
import com.nanzhufeng.ai.domain.LocalContextIndexHit
import com.nanzhufeng.ai.domain.LocalContextIndexStatus

/** SQLite FTS5 index for send-time retrieval. It contains no credential, attachment or runtime data. */
class RoomLocalContextIndex(private val database: NanfengAiDatabase) : LocalContextIndex {
    @Volatile private var available = ContextIndexSchema.ensure(database.openHelper.writableDatabase)

    override fun searchActiveMemories(queryTerms: Set<String>, scope: ContextRetrievalScope, limit: Int) = search(
        table = "memory_context_fts", from = "memory_context_fts f JOIN memories m ON m.id=f.id", queryTerms = queryTerms, limit = limit,
        where = "f.status='ACTIVE' AND (m.scopeKind='GLOBAL' OR m.conversationId=? OR (? IS NOT NULL AND m.projectId=?))",
        bind = arrayOf(scope.conversationId.value, scope.projectId, scope.projectId), kind = "记忆",
    )

    override fun searchActiveKnowledge(queryTerms: Set<String>, scope: ContextRetrievalScope, limit: Int) = search(
        table = "knowledge_context_fts", from = "knowledge_context_fts f LEFT JOIN knowledge_project_scopes s ON s.knowledgeId=f.id", queryTerms = queryTerms, limit = limit,
        where = "f.status='ACTIVE' AND (s.projectId IS NULL OR (? IS NOT NULL AND s.projectId=?))",
        bind = arrayOf(scope.projectId, scope.projectId), kind = "知识库",
    )

    override fun searchActiveHistory(queryTerms: Set<String>, scope: ContextRetrievalScope, limit: Int) = search(
        table = "conversation_context_fts", from = "conversation_context_fts f JOIN conversations c ON c.id=f.conversationId", queryTerms = queryTerms, limit = limit,
        where = "f.deletedAtEpochMs IS NULL AND f.archivedAtEpochMs IS NULL AND f.conversationId != ? AND (? IS NULL OR c.projectId=?)",
        bind = arrayOf(scope.conversationId.value, scope.projectId, scope.projectId), kind = "历史对话",
    )

    override fun activeKnowledgeCount(): Int = if (!available) 0 else runCatching {
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM knowledge_context_fts WHERE status='ACTIVE'").use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }.getOrElse { available = false; 0 }

    override fun status(): LocalContextIndexStatus = if (available) LocalContextIndexStatus.AVAILABLE else LocalContextIndexStatus.UNAVAILABLE

    private fun search(table: String, from: String, queryTerms: Set<String>, limit: Int, where: String, bind: Array<String?>, kind: String): List<LocalContextIndexHit> {
        if (!available) return emptyList()
        val match = queryTerms.toFtsMatch() ?: return emptyList()
        val sql = "SELECT f.id, f.title, f.body, f.updatedAtEpochMs, bm25($table) FROM $from WHERE $table MATCH ? AND $where ORDER BY bm25($table), f.updatedAtEpochMs DESC, f.id ASC LIMIT ?"
        return runCatching {
            database.openHelper.readableDatabase.query(sql, arrayOf(match, *bind, limit.coerceIn(1, 30).toString())).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) add(LocalContextIndexHit(cursor.getString(0), kind, cursor.getString(1), cursor.getString(2), cursor.getLong(3), cursor.getDouble(4)))
                }
            }
        }.getOrElse { available = false; emptyList() }
    }

    private fun Set<String>.toFtsMatch(): String? = asSequence()
        .map { it.filter { char -> char.isLetterOrDigit() || char == '_' || char == '-' } }
        .filter { it.length >= 2 }.take(24).toList().takeIf(List<String>::isNotEmpty)
        ?.joinToString(" OR ") { term ->
            if (term.all { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN }) {
                term.windowed(2).joinToString(" AND ") { "\"$it\"*" }
            } else "\"$term\"*"
        }
}

/** Shared by the Room migration and clean installations; trigger-maintained after the one-time backfill. */
internal object ContextIndexSchema {
    fun ensure(db: SupportSQLiteDatabase): Boolean = runCatching { statements().forEach(db::execSQL); true }.getOrDefault(false)

    /** Used by the host-SQLite contract test so FTS5 SQL is actually executed, not skipped. */
    internal fun statements(): List<String> {
        val memoryTerms = searchableTerms("new.title || ' ' || new.body")
        val knowledgeTerms = searchableTerms("new.title || ' ' || new.body")
        val historyTerms = searchableTerms("new.title || ' ' || new.snippet")
        val memoryBackfillTerms = searchableTerms("title || ' ' || body")
        val knowledgeBackfillTerms = searchableTerms("title || ' ' || body")
        val historyBackfillTerms = searchableTerms("title || ' ' || snippet")
        return listOf(
        "CREATE VIRTUAL TABLE IF NOT EXISTS memory_context_fts USING fts5(id UNINDEXED, title, body, searchTerms, status UNINDEXED, updatedAtEpochMs UNINDEXED, tokenize='unicode61')",
        "CREATE VIRTUAL TABLE IF NOT EXISTS knowledge_context_fts USING fts5(id UNINDEXED, title, body, searchTerms, status UNINDEXED, updatedAtEpochMs UNINDEXED, tokenize='unicode61')",
        "CREATE VIRTUAL TABLE IF NOT EXISTS conversation_context_fts USING fts5(id UNINDEXED, conversationId UNINDEXED, title, body, searchTerms, updatedAtEpochMs UNINDEXED, archivedAtEpochMs UNINDEXED, deletedAtEpochMs UNINDEXED, tokenize='unicode61')",
        "CREATE TRIGGER IF NOT EXISTS context_index_memory_insert AFTER INSERT ON memories BEGIN INSERT INTO memory_context_fts(rowid,id,title,body,searchTerms,status,updatedAtEpochMs) VALUES (new.rowid,new.id,new.title,new.body,$memoryTerms,new.status,new.updatedAtEpochMs); END",
        "CREATE TRIGGER IF NOT EXISTS context_index_memory_update AFTER UPDATE ON memories BEGIN DELETE FROM memory_context_fts WHERE rowid=old.rowid; INSERT INTO memory_context_fts(rowid,id,title,body,searchTerms,status,updatedAtEpochMs) VALUES (new.rowid,new.id,new.title,new.body,$memoryTerms,new.status,new.updatedAtEpochMs); END",
        "CREATE TRIGGER IF NOT EXISTS context_index_memory_delete AFTER DELETE ON memories BEGIN DELETE FROM memory_context_fts WHERE rowid=old.rowid; END",
        "CREATE TRIGGER IF NOT EXISTS context_index_knowledge_insert AFTER INSERT ON knowledge_items BEGIN INSERT INTO knowledge_context_fts(rowid,id,title,body,searchTerms,status,updatedAtEpochMs) VALUES (new.rowid,new.id,new.title,new.body,$knowledgeTerms,new.status,new.updatedAtEpochMs); END",
        "CREATE TRIGGER IF NOT EXISTS context_index_knowledge_update AFTER UPDATE ON knowledge_items BEGIN DELETE FROM knowledge_context_fts WHERE rowid=old.rowid; INSERT INTO knowledge_context_fts(rowid,id,title,body,searchTerms,status,updatedAtEpochMs) VALUES (new.rowid,new.id,new.title,new.body,$knowledgeTerms,new.status,new.updatedAtEpochMs); END",
        "CREATE TRIGGER IF NOT EXISTS context_index_knowledge_delete AFTER DELETE ON knowledge_items BEGIN DELETE FROM knowledge_context_fts WHERE rowid=old.rowid; END",
        "CREATE TRIGGER IF NOT EXISTS context_index_conversation_insert AFTER INSERT ON local_search_index BEGIN INSERT INTO conversation_context_fts(rowid,id,conversationId,title,body,searchTerms,updatedAtEpochMs,archivedAtEpochMs,deletedAtEpochMs) VALUES (new.rowid,new.id,new.conversationId,new.title,new.snippet,$historyTerms,new.timestampEpochMs,new.archivedAtEpochMs,new.deletedAtEpochMs); END",
        "CREATE TRIGGER IF NOT EXISTS context_index_conversation_update AFTER UPDATE ON local_search_index BEGIN DELETE FROM conversation_context_fts WHERE rowid=old.rowid; INSERT INTO conversation_context_fts(rowid,id,conversationId,title,body,searchTerms,updatedAtEpochMs,archivedAtEpochMs,deletedAtEpochMs) VALUES (new.rowid,new.id,new.conversationId,new.title,new.snippet,$historyTerms,new.timestampEpochMs,new.archivedAtEpochMs,new.deletedAtEpochMs); END",
        "CREATE TRIGGER IF NOT EXISTS context_index_conversation_delete AFTER DELETE ON local_search_index BEGIN DELETE FROM conversation_context_fts WHERE rowid=old.rowid; END",
        "INSERT INTO memory_context_fts(rowid,id,title,body,searchTerms,status,updatedAtEpochMs) SELECT rowid,id,title,body,$memoryBackfillTerms,status,updatedAtEpochMs FROM memories WHERE rowid NOT IN (SELECT rowid FROM memory_context_fts)",
        "INSERT INTO knowledge_context_fts(rowid,id,title,body,searchTerms,status,updatedAtEpochMs) SELECT rowid,id,title,body,$knowledgeBackfillTerms,status,updatedAtEpochMs FROM knowledge_items WHERE rowid NOT IN (SELECT rowid FROM knowledge_context_fts)",
        "INSERT INTO conversation_context_fts(rowid,id,conversationId,title,body,searchTerms,updatedAtEpochMs,archivedAtEpochMs,deletedAtEpochMs) SELECT rowid,id,conversationId,title,snippet,$historyBackfillTerms,timestampEpochMs,archivedAtEpochMs,deletedAtEpochMs FROM local_search_index WHERE rowid NOT IN (SELECT rowid FROM conversation_context_fts)",
    )
    }

    /** Unicode61 does not segment Chinese; append searchable two-character terms without changing the returned body. */
    private fun searchableTerms(expression: String): String = "($expression || ' ' || COALESCE((WITH RECURSIVE positions(pos) AS (SELECT 1 UNION ALL SELECT pos + 1 FROM positions WHERE pos < length($expression) - 1) SELECT group_concat(substr($expression, pos, 2), ' ') FROM positions), ''))"

    /** Rebuild only when upgrading the original 42 schema that had no Chinese token terms. */
    fun rebuild(db: SupportSQLiteDatabase): Boolean = runCatching {
        listOf("context_index_memory_insert", "context_index_memory_update", "context_index_memory_delete", "context_index_knowledge_insert", "context_index_knowledge_update", "context_index_knowledge_delete", "context_index_conversation_insert", "context_index_conversation_update", "context_index_conversation_delete").forEach { db.execSQL("DROP TRIGGER IF EXISTS $it") }
        listOf("memory_context_fts", "knowledge_context_fts", "conversation_context_fts").forEach { db.execSQL("DROP TABLE IF EXISTS $it") }
        ensure(db)
    }.getOrDefault(false)
}

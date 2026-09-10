package com.nanzhufeng.ai.data

import com.nanzhufeng.ai.data.local.NanfengAiDatabase
import com.nanzhufeng.ai.data.local.ContextIndexSchema
import java.sql.DriverManager
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalContextFtsMigrationContractsTest {
    @Test fun `FTS5 schema backfills and keeps triggers synchronized on a real SQLite engine`() {
        DriverManager.registerDriver(org.sqlite.JDBC())
        val file = java.io.File.createTempFile("context-fts-${UUID.randomUUID()}", ".db")
        try {
            DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use { connection ->
                connection.createStatement().use { statement ->
                    statement.execute("CREATE TABLE memories (id TEXT PRIMARY KEY, title TEXT, body TEXT, status TEXT, updatedAtEpochMs INTEGER)")
                    statement.execute("CREATE TABLE knowledge_items (id TEXT PRIMARY KEY, title TEXT, body TEXT, status TEXT, updatedAtEpochMs INTEGER)")
                    statement.execute("CREATE TABLE local_search_index (id TEXT PRIMARY KEY, conversationId TEXT, title TEXT, snippet TEXT, timestampEpochMs INTEGER, archivedAtEpochMs INTEGER, deletedAtEpochMs INTEGER)")
                    statement.execute("INSERT INTO memories VALUES ('m1','迁移偏好','迁移前先备份','ACTIVE',10)")
                    ContextIndexSchema.statements().forEach(statement::execute)
                    statement.executeQuery("SELECT id FROM memory_context_fts WHERE memory_context_fts MATCH '迁移*'").use { result -> assertTrue(result.next()); assertEquals("m1", result.getString(1)) }
                    statement.execute("INSERT INTO knowledge_items VALUES ('k1','下载排障','Cookie 与并发导致失败','ACTIVE',11)")
                    statement.executeQuery("SELECT id FROM knowledge_context_fts WHERE knowledge_context_fts MATCH 'Cookie*'").use { result -> assertTrue(result.next()); assertEquals("k1", result.getString(1)) }
                    statement.execute("UPDATE memories SET body='已经完成完整性校验' WHERE id='m1'")
                    statement.executeQuery("SELECT id FROM memory_context_fts WHERE memory_context_fts MATCH '完整* AND 整性*'").use { result -> assertTrue(result.next()); assertEquals("m1", result.getString(1)) }
                    statement.execute("INSERT INTO memories VALUES ('replacement','概览','Entirely rewritten summary','ACTIVE',12)")
                    statement.execute("UPDATE memories SET status='DELETED' WHERE id='m1'")
                    statement.executeQuery("SELECT COUNT(*) FROM memory_context_fts WHERE memory_context_fts MATCH '完整*' AND status='ACTIVE'").use { result -> assertTrue(result.next()); assertEquals(0, result.getInt(1)) }
                    statement.executeQuery("SELECT id FROM memory_context_fts WHERE memory_context_fts MATCH 'rewritten*' AND status='ACTIVE'").use { result -> assertTrue(result.next()); assertEquals("replacement", result.getString(1)) }

                }
            }
        } finally {
            file.delete()
        }
    }
}

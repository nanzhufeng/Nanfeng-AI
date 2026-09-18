package com.nanzhufeng.ai.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.nanzhufeng.ai.domain.ConversationDataArea
import com.nanzhufeng.ai.domain.ConversationSurface
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest

/** Runs before any repository/job can open either area. The two database writes use SQLite's
 * rollback-journal super-journal; WAL is re-enabled by Room only after the atomic split commits. */
object ConversationAreaDatabaseOwner {
    @Synchronized
    fun open(context: Context, area: ConversationSurface, factory: (String) -> NanfengAiDatabase): NanfengAiDatabase {
        val chat = context.getDatabasePath(ConversationDataArea.databaseName(ConversationSurface.CHAT))
        val work = context.getDatabasePath(ConversationDataArea.databaseName(ConversationSurface.WORK))
        check(chat.parentFile!!.isDirectory || chat.parentFile!!.mkdirs())
        RandomAccessFile(File(chat.parentFile,".conversation-area-split.lock"),"rw").use { lockFile ->
            lockFile.channel.lock().use {
                if (!chat.exists()) { val fresh=factory(chat.name);try { fresh.openHelper.writableDatabase } finally { fresh.close() } }
                // SQLite must recover a hot rollback journal before the first identity read.
                // A read-only handle rejects this with SQLITE_READONLY_ROLLBACK after a crash.
                SQLiteDatabase.openDatabase(chat.path,null,SQLiteDatabase.OPEN_READWRITE).use { current ->
                    if (identity(current) == ConversationAreaIdentity.CHAT) return owned(area,factory)
                    require(identity(current) == 0) { "对话区数据库身份不一致。" }
                }
                // Upgrade the legacy schema using the existing, continuous Room migrations.
                val legacy=factory(chat.name)
                try { legacy.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").close() } finally { legacy.close() }
                split(context,chat,work)
            }
        }
        return owned(area,factory)
    }

    private fun owned(area: ConversationSurface, factory: (String) -> NanfengAiDatabase): NanfengAiDatabase {
        val database=factory(ConversationDataArea.databaseName(area))
        try {
            val db=database.openHelper.writableDatabase
            val expected=ConversationAreaIdentity.id(area)
            val current=db.query("PRAGMA application_id").use { it.moveToFirst();it.getInt(0) }
            if(current==0) {
                val count=db.query("SELECT (SELECT COUNT(*) FROM conversations)+(SELECT COUNT(*) FROM projects)").use { it.moveToFirst();it.getLong(0) }
                require(count==0L) { "区域数据库尚未完成迁移。" }
                db.execSQL("PRAGMA application_id=$expected")
            } else require(current==expected) { "区域数据库身份不一致。" }
            for(operation in listOf("INSERT","UPDATE OF surface")) {
                val suffix=if(operation=="INSERT") "insert" else "update"
                db.execSQL("CREATE TRIGGER IF NOT EXISTS area_surface_${suffix}_v1 AFTER $operation ON conversations WHEN NEW.surface != '${area.name}' BEGIN UPDATE conversations SET surface='${area.name}' WHERE id=NEW.id; END")
            }
            return database
        } catch(error: Throwable) { database.close();throw error }
    }

    internal fun split(context: Context, chat: File, work: File, beforeCommit: () -> Unit = {}) {
        val recovery = File(chat.parentFile,"data-area-migration-v1").also { check(it.isDirectory || it.mkdirs()) }
        val backup = File(recovery,"before-split.sqlite")
        SQLiteDatabase.openDatabase(chat.path,null,SQLiteDatabase.OPEN_READWRITE).use { db ->
            require(identity(db) == 0) { "数据库已经完成区域迁移。" }
            val workRoots=db.rawQuery("SELECT (SELECT COUNT(*) FROM projects)+(SELECT COUNT(*) FROM conversations WHERE surface='WORK' OR projectId IS NOT NULL)",null).use { it.moveToFirst();it.getLong(0) }
            if(workRoots==0L) { db.execSQL("PRAGMA application_id=${ConversationAreaIdentity.CHAT}");return }
            db.disableWriteAheadLogging()
            db.rawQuery("PRAGMA journal_mode=DELETE",null).use { require(it.moveToFirst() && it.getString(0).equals("delete",true)) }
            if (!backup.exists()) copyVerified(chat,backup)
            val pending = File(recovery,"pending")
            require(!work.exists() || pending.exists()) { "工作区已有独立数据库，旧区域迁移未覆盖任何记录。" }
            pending.outputStream().use { it.write("1".toByteArray());it.fd.sync() }
            // A previous interrupted, uncommitted candidate is disposable; source + backup remain intact.
            listOf(work,File(work.path+"-wal"),File(work.path+"-shm"),File(work.path+"-journal")).forEach { if(it.exists()) check(it.delete()) }
            copyVerified(chat,work)
            db.execSQL("ATTACH DATABASE ? AS work",arrayOf(work.path))
            try {
                db.execSQL("PRAGMA foreign_keys=OFF")
                db.rawQuery("PRAGMA work.journal_mode=DELETE",null).use { require(it.moveToFirst() && it.getString(0).equals("delete",true)) }
                db.beginTransaction()
                try {
                    LegacyConversationAreaPartition.prepare(db)
                    copySelectedAssets(context,db)
                    copyImportSources(context,db)
                    LegacyConversationAreaPartition.prune(db)
                    beforeCommit()
                    db.execSQL("PRAGMA main.application_id=${ConversationAreaIdentity.CHAT}")
                    db.execSQL("PRAGMA work.application_id=${ConversationAreaIdentity.WORK}")
                    db.setTransactionSuccessful()
                } finally { db.endTransaction() }
                check(pending.delete())
            } finally { db.execSQL("DETACH DATABASE work") }
        }
    }
    private fun copySelectedAssets(context: Context, db: SQLiteDatabase) {
        val queries = listOf(
            "SELECT storageKey,sha256 FROM message_content_blocks WHERE rowid IN (SELECT id FROM _work_message_content_blocks) AND storageKey IS NOT NULL",
            "SELECT storageKey,sha256 FROM conversation_draft_attachments WHERE rowid IN (SELECT id FROM _work_conversation_draft_attachments)",
            "SELECT storageKey,sha256 FROM knowledge_attachments WHERE rowid IN (SELECT id FROM _work_knowledge_attachments)",
            "SELECT storageKey,sha256 FROM capture_draft_attachments WHERE rowid IN (SELECT id FROM _work_capture_draft_attachments)",
            "SELECT storageKey,sha256 FROM private_attachment_assets WHERE rowid IN (SELECT id FROM _work_private_attachment_assets)"
        )
        val sourceRoot=context.filesDir.canonicalFile
        val targetRoot=File(sourceRoot,"work-area").canonicalFile
        for(query in queries) db.rawQuery(query,null).use { c -> while(c.moveToNext()) {
            val key=c.getString(0)
            require(key.startsWith("attachments/") && !File(key).isAbsolute) { "旧附件位置无效，未迁移。" }
            val source=File(sourceRoot,key).canonicalFile
            val target=File(targetRoot,key).canonicalFile
            require(source.path.startsWith(sourceRoot.path+File.separator) && target.path.startsWith(targetRoot.path+File.separator))
            if (source.isFile) { require(hash(source)==c.getString(1)) { "旧附件校验失败，原文件未改动。" }; copyVerified(source,target) }
        } }
    }
    private fun copyImportSources(context: Context, db: SQLiteDatabase) {
        val sources=listOf(
            Triple("chatgpt_export_import_tasks","packageHash","chatgpt-export-import-assets/v1/"),
            Triple("claude_export_import_tasks","packageHash","claude-export-import-assets/v1/"),
            Triple("nanfeng_knowledge_export_import_tasks","packageHash","nanfeng-knowledge-export-import-assets/v1/"),
            Triple("markdown_import_tasks","sha256","markdown-import-assets/v1/"),
            Triple("json_knowledge_import_tasks","sha256","json-knowledge-import-assets/v1/"),
            Triple("pdf_text_import_tasks","sourceSha256","pdf-text-import-assets/v1/"),
            Triple("web_text_snapshot_tasks","rawHtmlSha256","web-text-snapshots/v1/")
        )
        val root=context.filesDir.canonicalFile
        val target=File(root,"work-area").canonicalFile
        fun copy(key: String, expected: String?) {
            val from=File(root,key).canonicalFile;val to=File(target,key).canonicalFile
            require(from.path.startsWith(root.path+File.separator) && to.path.startsWith(target.path+File.separator))
            if(from.isFile) { if(expected!=null) require(hash(from)==expected) { "旧导入来源校验失败，原数据未改动。" };copyVerified(from,to) }
        }
        for((table,column,prefix) in sources) db.rawQuery("SELECT storageKey,$column FROM $table WHERE rowid IN (SELECT id FROM _work_$table)",null).use { c -> while(c.moveToNext()) {
            val key=c.getString(0);require(key.startsWith(prefix)) { "旧导入来源路径无效。" };copy(key,c.getString(1))
        } }
        db.rawQuery("SELECT id,packageHash FROM p6k_zip_import_tasks WHERE rowid IN (SELECT id FROM _work_p6k_zip_import_tasks)",null).use { c -> while(c.moveToNext()) {
            val id=c.getString(0);require(id.matches(Regex("[a-zA-Z0-9_-]+")))
            copy("p6k-zip-import/v1/archives/$id.zip",c.getString(1))
            for(version in 1..2) copy("p6k-zip-import/v1/$id.assets-v$version.done",null)
        } }
    }
    private fun identity(db: SQLiteDatabase) = db.rawQuery("PRAGMA application_id",null).use { it.moveToFirst();it.getInt(0) }
    private fun hash(file: File): String {
        val digest=MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val buffer=ByteArray(64*1024); while(true) { val n=input.read(buffer);if(n<0)break;digest.update(buffer,0,n) } }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    private fun copyVerified(source: File,target: File) {
        check(target.parentFile!!.isDirectory || target.parentFile!!.mkdirs())
        val pending=File(target.parentFile,target.name+".copying")
        source.inputStream().use { input -> pending.outputStream().use { output -> input.copyTo(output);output.fd.sync() } }
        require(hash(source)==hash(pending)) { "迁移副本校验失败。" }
        java.nio.file.Files.move(pending.toPath(),target.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING,java.nio.file.StandardCopyOption.ATOMIC_MOVE)
    }
}

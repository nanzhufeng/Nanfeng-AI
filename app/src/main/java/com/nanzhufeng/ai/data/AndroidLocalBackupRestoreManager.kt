package com.nanzhufeng.ai.data

import android.content.Context
import android.net.Uri
import com.nanzhufeng.ai.data.local.NanfengAiDatabase
import com.nanzhufeng.ai.domain.LocalBackupArtifact
import com.nanzhufeng.ai.domain.LocalBackupFormat
import com.nanzhufeng.ai.domain.LocalBackupPreflight
import com.nanzhufeng.ai.domain.LocalBackupRestoreManager
import com.nanzhufeng.ai.domain.LocalBackupResult
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.json.JSONArray
import org.json.JSONObject

/**
 * P5-D Android owner. It creates a SQLite-consistent snapshot with VACUUM INTO, never a WAL/SHM copy.
 * Restore is intentionally replacement-only and ends in an explicit process restart boundary.
 */
class AndroidLocalBackupRestoreManager(
    private val context: Context,
    private val database: NanfengAiDatabase,
    private val appVersion: String,
) : LocalBackupRestoreManager {
    private val dataArea = (context as? ConversationAreaFileContext)?.area?.name ?: "CHAT"
    private val app = context.applicationContext
    private val files = app.filesDir.canonicalFile
    private val dbFile get() = app.getDatabasePath(DB_NAME)
    private val work get() = File(files, "p5d-local-backup/v1")
    private val inbox get() = File(work, "inbox/pending.nfai-backup")
    private val state get() = app.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)

    override fun export(destination: Uri): LocalBackupResult = runCatching {
        val stage = File(work, "export-${UUID.randomUUID()}").also { it.mkdirs() }
        try {
            val snapshot = File(stage, "nanfeng-ai.snapshot")
            snapshotDatabase(snapshot)
            val assets = snapshotAssets(stage)
            val counts = tableCounts(snapshot)
            val packageFile = File(stage, "backup.nfai-backup")
            writePackage(packageFile, snapshot, assets, counts)
            val hash = sha256(packageFile)
            verifyPackage(packageFile).getOrElse { failure ->
                return LocalBackupResult.Rejected("备份包本机校验失败：${safePackageFailure(failure)}；未写入所选位置。")
            }
            app.contentResolver.openOutputStream(destination, "w")?.use { output -> packageFile.inputStream().use { it.copyTo(output) }; output.flush() }
                ?: return LocalBackupResult.Failed("无法写入所选位置。", false)
            val written = app.contentResolver.openInputStream(destination)?.use(::sha256) ?: return LocalBackupResult.Failed("无法回读所选位置。", false)
            if (written != hash) return LocalBackupResult.Failed("所选位置回读哈希不一致。", false)
            LocalBackupResult.Exported(LocalBackupArtifact("nanfeng-ai-local-backup.nfai-backup", hash, packageFile.length(), currentSchema, counts))
        } finally { stage.deleteRecursively() }
    }.getOrElse { LocalBackupResult.Failed("备份失败：${it.javaClass.simpleName}", false) }

    override fun preflight(source: Uri): LocalBackupResult = runCatching {
        inbox.parentFile?.mkdirs()
        app.contentResolver.openInputStream(source)?.use { input -> FileOutputStream(inbox).use { output -> copyBounded(input, output, LocalBackupFormat.MAX_ARCHIVE_BYTES) } }
            ?: return LocalBackupResult.Rejected("无法读取所选备份包。")
        val parsed = verifyPackage(inbox).getOrNull() ?: return LocalBackupResult.Rejected("备份包不受支持、已损坏或包含不安全内容。")
        val nonEmpty = tableCounts(dbFile).values.any { it > 0 } || allowedRoots().any { root -> File(files, root).exists() }
        val p = parsed.copy(conflicts = if (nonEmpty) listOf("本地已有业务数据；只能明确选择替换本地或取消，不支持合并。") else emptyList())
        state.edit().putString(PREF_FINGERPRINT, p.fingerprint).apply()
        LocalBackupResult.Preflighted(p)
    }.getOrElse { LocalBackupResult.Rejected("预检失败：${it.javaClass.simpleName}") }

    override fun restore(preflightFingerprint: String, replaceLocal: Boolean): LocalBackupResult = runCatching {
        val current = verifyPackage(inbox).getOrNull() ?: return LocalBackupResult.Rejected("没有已通过预检的隔离备份包。")
        if (state.getString(PREF_FINGERPRINT, null) != preflightFingerprint || current.fingerprint != preflightFingerprint) return LocalBackupResult.Rejected("备份包或本地预检已变化，请重新选择并预检。")
        val nonEmpty = tableCounts(dbFile).values.any { it > 0 } || allowedRoots().any { File(files, it).exists() }
        if (nonEmpty && !replaceLocal) return LocalBackupResult.Rejected("本地已有数据：请明确选择替换本地或取消。")
        val root = File(work, "restore-${UUID.randomUUID()}").also { it.mkdirs() }
        val checkpoint = File(work, "recovery/${UUID.randomUUID()}").also { it.mkdirs() }
        state.edit().putString(PREF_OPERATION, "INTERRUPTED").putString(PREF_CHECKPOINT, checkpoint.name).apply()
        var switched = false
        try {
            extractVerified(inbox, root)
            val stagedDb = File(root, LocalBackupFormat.DATABASE_ENTRY)
            validateDatabaseArea(stagedDb)
            if (!sqliteHealthy(stagedDb) || schemaVersion(stagedDb) != currentSchema || tableCounts(stagedDb) != current.tableCounts) return LocalBackupResult.Rejected("候选数据库完整性、Schema 或计数不匹配。")
            checkpointDatabase(File(checkpoint, "nanfeng-ai.snapshot"))
            checkpointAssets(checkpoint)
            // After close, no repository/container may keep using this process. The caller must restart.
            database.close()
            // From the first filesystem mutation onward, any failure must restore both owners.
            switched = true
            replaceFile(stagedDb, dbFile)
            replaceAssets(root)
            state.edit().putString(PREF_OPERATION, "RESTORED_RESTART_REQUIRED").remove(PREF_FINGERPRINT).apply()
            LocalBackupResult.RestoredRestartRequired(checkpoint.name)
        } catch (t: Throwable) {
            if (switched) runCatching { restoreCheckpoint(checkpoint) }
            state.edit().putString(PREF_OPERATION, "FAILED").apply()
            LocalBackupResult.Failed("替换失败，已尝试回滚：${t.javaClass.simpleName}", switched)
        } finally { root.deleteRecursively() }
    }.getOrElse { LocalBackupResult.Failed("恢复失败：${it.javaClass.simpleName}", true) }

    override fun cancelPendingRestore() { inbox.delete(); state.edit().remove(PREF_FINGERPRINT).putString(PREF_OPERATION, "CANCELLED").apply() }

    private fun snapshotDatabase(target: File) {
        target.parentFile?.mkdirs(); target.delete()
        val escaped = target.absolutePath.replace("'", "''")
        database.openHelper.writableDatabase.execSQL("VACUUM INTO '$escaped'")
        require(target.isFile && sqliteHealthy(target)) { "snapshot unavailable" }
    }
    private fun checkpointDatabase(target: File) { if (dbFile.exists()) snapshotDatabase(target) else FileOutputStream(target).close() }
    private fun snapshotAssets(stage: File): List<File> = allowedRoots().flatMap { root ->
        val source = File(files, root); if (!source.exists()) emptyList() else source.walkTopDown().filter { it.isFile }.map { file ->
            require(safeChild(source, file)) { "unsafe asset" }
            val out = File(stage, "assets/$root/${file.relativeTo(source).invariantSeparatorsPath}"); out.parentFile?.mkdirs(); file.inputStream().use { input -> FileOutputStream(out).use(input::copyTo) }; out
        }.toList()
    }
    private fun checkpointAssets(checkpoint: File) { allowedRoots().forEach { root ->
        val source = File(files, root); if (source.exists()) source.copyRecursively(File(checkpoint, "assets/$root"), overwrite = true)
    } }
    private fun writePackage(out: File, snapshot: File, assets: List<File>, counts: Map<String, Long>) {
        val entries = mutableListOf<Pair<String, File>>(LocalBackupFormat.DATABASE_ENTRY to snapshot)
        assets.forEach { entries += it.relativeTo(requireNotNull(snapshot.parentFile)).invariantSeparatorsPath to it }
        val list = JSONArray(entries.sortedBy { it.first }.map { (path, file) -> JSONObject().put("path", path).put("bytes", file.length()).put("sha256", sha256(file)) })
        val manifest = JSONObject().put("format", LocalBackupFormat.FORMAT).put("dataArea", dataArea).put("version", LocalBackupFormat.VERSION).put("appVersion", appVersion).put("schemaVersion", currentSchema).put("scope", "room_and_private_assets").put("excluded", JSONArray(EXCLUDED)).put("tableCounts", JSONObject(counts)).put("entries", list)
        manifest.put("manifestSha256", sha256(manifest.toString().toByteArray()))
        ZipOutputStream(FileOutputStream(out)).use { zip ->
            zip.putNextEntry(ZipEntry(LocalBackupFormat.MANIFEST_ENTRY)); zip.write(manifest.toString().toByteArray()); zip.closeEntry()
            entries.sortedBy { it.first }.forEach { (path, file) -> zip.putNextEntry(ZipEntry(path)); file.inputStream().use { it.copyTo(zip) }; zip.closeEntry() }
        }
    }
    private fun verifyPackage(file: File): Result<LocalBackupPreflight> = runCatching {
        ZipFile(file).use { zip ->
            val seen = mutableSetOf<String>(); var total = 0L; var entries = 0
            val all = zip.entries().toList()
            all.forEach { entry ->
                require(++entries <= LocalBackupFormat.MAX_ENTRY_COUNT && safeEntry(entry.name) && seen.add(entry.name) && !entry.isDirectory) { "unsafe zip" }
                require(entry.size >= 0); total += entry.size; require(total <= LocalBackupFormat.MAX_ARCHIVE_BYTES) { "zip bomb" }
            }
            val manifestEntry = zip.getEntry(LocalBackupFormat.MANIFEST_ENTRY) ?: error("missing manifest")
            val manifest = JSONObject(zip.getInputStream(manifestEntry).bufferedReader().readText())
            require(manifest.getString("format") == LocalBackupFormat.FORMAT && manifest.getInt("version") == LocalBackupFormat.VERSION) { "version" }
            require(manifest.getInt("schemaVersion") == currentSchema) { "schema" }
            require(manifest.optString("dataArea", "CHAT") == dataArea) { "data area" }
            val originalHash = manifest.getString("manifestSha256"); manifest.remove("manifestSha256"); require(originalHash == sha256(manifest.toString().toByteArray())) { "manifest hash" }
            val listed = manifest.getJSONArray("entries"); require(listed.length() + 1 == all.size) { "entry count" }
            for (i in 0 until listed.length()) { val item = listed.getJSONObject(i); val entry = zip.getEntry(item.getString("path")) ?: error("missing entry"); require(sha256(zip.getInputStream(entry)) == item.getString("sha256") && entry.size == item.getLong("bytes")) { "entry hash" } }
            val counts = manifest.getJSONObject("tableCounts").keys().asSequence().associateWith { manifest.getJSONObject("tableCounts").getLong(it) }
            require(!manifest.toString().contains(Regex("(?i)(api[ _-]?key|authorization|credential|token)"))) { "secret metadata" }
            val assetBytes = (0 until listed.length()).asSequence().map { listed.getJSONObject(it) }.filter { it.getString("path").startsWith("assets/") }.sumOf { it.getLong("bytes") }
            LocalBackupPreflight(LocalBackupFormat.FORMAT, LocalBackupFormat.VERSION, currentSchema, counts, assetBytes, emptyList(), emptyList(), emptyList(), sha256(file))
        }
    }
    private fun safePackageFailure(failure: Throwable): String = when (failure.message) {
        "data area" -> "备份属于另一区域，请在对应区域恢复"
        "zip bomb" -> "未压缩内容超过 32 GiB 上限"
        "unsafe zip" -> "包路径或条目不安全"
        "manifest hash", "entry hash", "entry count" -> "包结构或哈希不一致"
        "schema", "version" -> "包版本或 Schema 不匹配"
        else -> "包结构不受支持"
    }
    private fun extractVerified(zipFile: File, root: File) { ZipFile(zipFile).use { zip -> zip.entries().toList().filter { it.name != LocalBackupFormat.MANIFEST_ENTRY }.forEach { entry -> val target = File(root, entry.name); require(safeChild(root, target)); target.parentFile?.mkdirs(); zip.getInputStream(entry).use { input -> FileOutputStream(target).use(input::copyTo) } } } }
    private fun replaceFile(source: File, target: File) { target.parentFile?.mkdirs(); val old = File(target.parentFile, ".${target.name}.p5d-old"); if (target.exists()) target.renameTo(old); if (!source.renameTo(target)) source.copyTo(target, overwrite = true); old.delete() }
    private fun replaceAssets(root: File) { allowedRoots().forEach { key -> val destination = File(files, key); destination.deleteRecursively(); val source = File(root, "assets/$key"); if (source.exists()) source.copyRecursively(destination, overwrite = true) } }
    private fun validateDatabaseArea(file: File) {
        android.database.sqlite.SQLiteDatabase.openDatabase(file.path,null,android.database.sqlite.SQLiteDatabase.OPEN_READONLY).use { db ->
            val identity=db.rawQuery("PRAGMA application_id",null).use { it.moveToFirst();it.getInt(0) }
            val expected=if(dataArea=="WORK") com.nanzhufeng.ai.data.local.ConversationAreaIdentity.WORK else com.nanzhufeng.ai.data.local.ConversationAreaIdentity.CHAT
            require(identity==expected || (identity==0 && dataArea=="CHAT")) { "备份数据库所属区域不一致，未替换本机数据。" }
            if(identity==0) {
                val workRoots=db.rawQuery("SELECT (SELECT COUNT(*) FROM projects)+(SELECT COUNT(*) FROM conversations WHERE surface='WORK' OR projectId IS NOT NULL)",null).use { it.moveToFirst();it.getLong(0) }
                require(workRoots==0L || !context.getDatabasePath("nanfeng-ai-work.db").exists()) { "此旧备份包含工作区记录，不能覆盖已有独立工作区。" }
            }
        }
    }

    private fun restoreCheckpoint(checkpoint: File) { replaceFile(File(checkpoint, "nanfeng-ai.snapshot"), dbFile); replaceAssets(checkpoint) }
    private fun tableCounts(file: File): Map<String, Long> { if (!file.isFile || file.length() == 0L) return emptyMap(); val sql = android.database.sqlite.SQLiteDatabase.openDatabase(file.path, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY); return try { sql.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT IN ('android_metadata','room_master_table') ORDER BY name", null).use { c -> buildMap { while (c.moveToNext()) { val name = c.getString(0); sql.rawQuery("SELECT COUNT(*) FROM `" + name.replace("`", "``") + "`", null).use { n -> n.moveToFirst(); put(name, n.getLong(0)) } } } } } finally { sql.close() } }
    private fun schemaVersion(file: File): Int = android.database.sqlite.SQLiteDatabase.openDatabase(file.path, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY).use { it.version }
    private fun sqliteHealthy(file: File): Boolean = runCatching { val sql = android.database.sqlite.SQLiteDatabase.openDatabase(file.path, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY); try { sql.rawQuery("PRAGMA integrity_check", null).use { it.moveToFirst() && it.getString(0) == "ok" } } finally { sql.close() } }.getOrDefault(false)
    private fun allowedRoots() = listOf(
        "attachments/v1", "markdown-import-assets/v1", "json-knowledge-import-assets/v1",
        "pdf-text-import-assets/v1", "web-text-snapshots/v1", "chatgpt-export-import-assets/v1",
        "claude-export-import-assets/v1", "nanfeng-knowledge-export-import-assets/v1", "p6k-zip-import/v1",
    )
    private fun safeEntry(name: String) = name == LocalBackupFormat.MANIFEST_ENTRY || (name.matches(Regex("[A-Za-z0-9._/-]+")) && !name.startsWith('/') && !name.contains(".."))
    private fun safeChild(root: File, child: File) = child.canonicalFile.path.startsWith(root.canonicalFile.path + File.separator) && !child.isDirectory
    private fun sha256(file: File) = FileInputStream(file).use(::sha256)
    private fun sha256(input: java.io.InputStream): String { val digest = MessageDigest.getInstance("SHA-256"); val buffer = ByteArray(8192); while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }; return digest.digest().joinToString("") { "%02x".format(it) } }
    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun copyBounded(input: java.io.InputStream, output: java.io.OutputStream, limit: Long) { val buffer = ByteArray(8192); var total = 0L; while (true) { val n = input.read(buffer); if (n < 0) break; total += n; require(total <= limit) { "too large" }; output.write(buffer, 0, n) } }
    private val currentSchema get() = database.openHelper.writableDatabase.version
    private companion object { const val DB_NAME = "nanfeng-ai.db"; const val STATE_PREFS = "p5d_local_backup"; const val PREF_FINGERPRINT = "preflight_fingerprint"; const val PREF_OPERATION = "operation"; const val PREF_CHECKPOINT = "checkpoint"; val EXCLUDED = listOf("provider-secret-material", "route-preferences", "diagnostics", "exports", "registry", "signing-secrets") }
}

package com.nanzhufeng.ai.data

import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import com.nanzhufeng.ai.data.local.NanfengAiDatabase
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * Owns the location of user content only. Credentials and app preferences remain in Android
 * private storage so moving conversations never weakens account protection.
 */
enum class AndroidDataStorageLocation(val label: String) {
    INTERNAL("应用内部存储"),
    EXTERNAL("设备存储");
}

data class AndroidDataStorageLocationState(
    val location: AndroidDataStorageLocation,
    val absolutePath: String,
    val externalAvailable: Boolean,
)

sealed interface AndroidDataStorageMoveResult {
    data class RestartRequired(val state: AndroidDataStorageLocationState) : AndroidDataStorageMoveResult
    data class Rejected(val reason: String) : AndroidDataStorageMoveResult
    data class Failed(val reason: String) : AndroidDataStorageMoveResult
}

class AndroidDataStorageLocationManager(
    private val owner: AndroidDataStorageLocationOwner,
    private val closeDatabase: () -> Unit,
) {
    fun state(): AndroidDataStorageLocationState = owner.state()
    fun moveTo(target: AndroidDataStorageLocation): AndroidDataStorageMoveResult = owner.moveTo(target, closeDatabase)
}

/** Bootstrap settings stay in the base Context; all product data is read through [storageContext]. */
class AndroidDataStorageLocationOwner(private val base: Context) {
    private val app = base.applicationContext
    private val preferences = app.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun state(): AndroidDataStorageLocationState {
        val location = runCatching { AndroidDataStorageLocation.valueOf(preferences.getString(KEY_LOCATION, null) ?: "") }
            .getOrDefault(AndroidDataStorageLocation.INTERNAL)
            .takeIf { it != AndroidDataStorageLocation.EXTERNAL || externalRootOrNull() != null }
            ?: AndroidDataStorageLocation.INTERNAL
        return AndroidDataStorageLocationState(
            location = location,
            absolutePath = rootFor(location).canonicalPath,
            externalAvailable = externalRootOrNull() != null,
        )
    }

    fun storageContext(): Context = AndroidDataStorageContext(app, this)

    fun rootFor(location: AndroidDataStorageLocation = state().location): File = when (location) {
        AndroidDataStorageLocation.INTERNAL -> app.filesDir
        AndroidDataStorageLocation.EXTERNAL -> externalRootOrNull() ?: app.filesDir
    }

    fun databaseFile(name: String): File = when (state().location) {
        AndroidDataStorageLocation.INTERNAL -> app.getDatabasePath(name)
        AndroidDataStorageLocation.EXTERNAL -> File(rootFor(AndroidDataStorageLocation.EXTERNAL), "databases/$name")
    }

    /**
     * Copies first, verifies the copied tree and SQLite file, then commits the bootstrap pointer.
     * The old root is intentionally retained, so a failed restart can be switched back safely.
     */
    fun moveTo(target: AndroidDataStorageLocation, closeDatabase: () -> Unit): AndroidDataStorageMoveResult {
        val current = state()
        if (current.location == target) return AndroidDataStorageMoveResult.Rejected("已在当前数据存储位置。")
        if (target == AndroidDataStorageLocation.EXTERNAL && externalRootOrNull() == null) {
            return AndroidDataStorageMoveResult.Rejected("设备存储暂不可用，请插入并挂载存储设备后重试。")
        }
        val sourceRoot = rootFor(current.location)
        val sourceDatabaseDirectory = databaseDirectoryFor(current.location)
        val targetRoot = rootFor(target)
        val targetDatabaseDirectory = databaseDirectoryFor(target)
        val stagingRoot = File(app.cacheDir, "nanfeng-ai-move-stage")
        return try {
            closeDatabase()
            deleteRecursively(stagingRoot)
            copyFilesTree(sourceRoot, sourceDatabaseDirectory, File(stagingRoot, "files"))
            copyDatabaseFiles(sourceDatabaseDirectory, stagingRoot)
            verifyFilesTree(sourceRoot, sourceDatabaseDirectory, File(stagingRoot, "files"))
            verifyDatabase(File(stagingRoot, "databases/$DATABASE_NAME"))

            replaceDirectory(targetRoot, File(stagingRoot, "files"))
            replaceDatabaseFiles(targetDatabaseDirectory, File(stagingRoot, "databases"))
            require(preferences.edit().putString(KEY_LOCATION, target.name).commit()) { "保存新的数据路径失败。" }
            AndroidDataStorageMoveResult.RestartRequired(state())
        } catch (error: Exception) {
            deleteRecursively(stagingRoot)
            AndroidDataStorageMoveResult.Failed("迁移没有生效：${error.message?.take(80) ?: "本机文件校验失败"}")
        }
    }

    private fun databaseDirectoryFor(location: AndroidDataStorageLocation): File = when (location) {
        AndroidDataStorageLocation.INTERNAL -> requireNotNull(app.getDatabasePath(DATABASE_NAME).parentFile)
        AndroidDataStorageLocation.EXTERNAL -> File(rootFor(location), "databases")
    }

    private fun copyDatabaseFiles(sourceDirectory: File, stagingRoot: File) {
        val targetDirectory = File(stagingRoot, "databases").also(File::mkdirs)
        listOf(DATABASE_NAME, "$DATABASE_NAME-wal", "$DATABASE_NAME-shm").forEach { name ->
            val source = File(sourceDirectory, name)
            if (source.isFile) copyFile(source, File(targetDirectory, name))
        }
    }

    private fun verifyDatabase(database: File) {
        require(database.isFile && database.length() > 0L) { "数据库副本不存在。" }
        SQLiteDatabase.openDatabase(database.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { opened ->
            opened.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                require(cursor.moveToFirst() && cursor.getString(0) == "ok") { "数据库完整性校验失败。" }
            }
        }
    }

    private fun copyTree(source: File, target: File) {
        if (!source.exists()) return
        if (source.isDirectory) {
            require(target.mkdirs() || target.isDirectory) { "无法创建数据目录。" }
            source.listFiles()?.forEach { child -> copyTree(child, File(target, child.name)) }
        } else copyFile(source, target)
    }

    private fun copyFilesTree(sourceRoot: File, databaseDirectory: File, target: File) {
        require(target.mkdirs() || target.isDirectory) { "无法创建数据目录。" }
        val databaseRoot = databaseDirectory.canonicalFile
        sourceRoot.listFiles()?.forEach { child ->
            if (child.canonicalFile != databaseRoot) copyTree(child, File(target, child.name))
        }
    }

    private fun copyFile(source: File, target: File) {
        target.parentFile?.mkdirs()
        FileInputStream(source).channel.use { input ->
            FileOutputStream(target).channel.use { output ->
                var position = 0L
                while (position < input.size()) position += input.transferTo(position, input.size() - position, output)
                output.force(true)
            }
        }
        require(source.length() == target.length()) { "文件大小不一致。" }
    }

    private fun replaceDirectory(target: File, staged: File) {
        require(staged.isDirectory) { "迁移文件副本不存在。" }
        deleteRecursively(target)
        copyTree(staged, target)
        verifyTree(staged, target)
    }

    private fun replaceDatabaseFiles(targetDirectory: File, stagedDirectory: File) {
        require(stagedDirectory.isDirectory) { "数据库副本不存在。" }
        require(targetDirectory.mkdirs() || targetDirectory.isDirectory) { "无法创建数据库目录。" }
        listOf(DATABASE_NAME, "$DATABASE_NAME-wal", "$DATABASE_NAME-shm").forEach { name ->
            val staged = File(stagedDirectory, name)
            val target = File(targetDirectory, name)
            if (staged.isFile) copyFile(staged, target) else target.delete()
        }
        verifyDatabase(File(targetDirectory, DATABASE_NAME))
    }

    private fun verifyTree(source: File, target: File) {
        if (!source.exists()) return
        require(target.exists() && source.isDirectory == target.isDirectory) { "数据目录校验失败。" }
        if (source.isDirectory) {
            val sourceChildren = source.listFiles()?.sortedBy { it.name }.orEmpty()
            val targetChildren = target.listFiles()?.associateBy { it.name }.orEmpty()
            require(sourceChildren.size == targetChildren.size) { "数据文件数量不一致。" }
            sourceChildren.forEach { child -> verifyTree(child, requireNotNull(targetChildren[child.name])) }
        } else {
            require(source.length() == target.length() && sha256(source).contentEquals(sha256(target))) { "数据文件校验失败。" }
        }
    }

    private fun verifyFilesTree(sourceRoot: File, databaseDirectory: File, target: File) {
        val databaseRoot = databaseDirectory.canonicalFile
        val sourceChildren = sourceRoot.listFiles()?.filter { it.canonicalFile != databaseRoot }?.sortedBy { it.name }.orEmpty()
        val targetChildren = target.listFiles()?.associateBy { it.name }.orEmpty()
        require(sourceChildren.size == targetChildren.size) { "数据文件数量不一致。" }
        sourceChildren.forEach { child -> verifyTree(child, requireNotNull(targetChildren[child.name])) }
    }

    private fun sha256(file: File): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest()
    }

    private fun externalRootOrNull(): File? = app.getExternalFilesDir(EXTERNAL_DIRECTORY)?.takeIf { it.exists() || it.mkdirs() }

    private fun deleteRecursively(file: File) {
        if (!file.exists()) return
        file.listFiles()?.forEach(::deleteRecursively)
        require(file.delete()) { "无法清理迁移临时目录。" }
    }

    private companion object {
        const val PREFERENCES = "nanfeng-ai-data-location-v1"
        const val KEY_LOCATION = "location"
        const val EXTERNAL_DIRECTORY = "nanfeng-ai-data"
        const val DATABASE_NAME = "nanfeng-ai.db"
    }
}

/** Makes existing data owners resolve files and Room through the chosen data root. */
private class AndroidDataStorageContext(
    base: Context,
    private val locationOwner: AndroidDataStorageLocationOwner,
) : ContextWrapper(base) {
    override fun getApplicationContext(): Context = this
    override fun getFilesDir(): File = locationOwner.rootFor()
    override fun getDatabasePath(name: String): File = locationOwner.databaseFile(name)

    override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?): SQLiteDatabase =
        openStorageDatabase(name, mode, factory, null)

    override fun openOrCreateDatabase(
        name: String,
        mode: Int,
        factory: SQLiteDatabase.CursorFactory?,
        errorHandler: DatabaseErrorHandler?,
    ): SQLiteDatabase = openStorageDatabase(name, mode, factory, errorHandler)

    override fun deleteDatabase(name: String): Boolean {
        if (locationOwner.state().location == AndroidDataStorageLocation.INTERNAL) return super.deleteDatabase(name)
        val database = getDatabasePath(name)
        var deleted = false
        listOf(database, File("${database.path}-wal"), File("${database.path}-shm")).forEach { file ->
            if (file.exists() && file.delete()) deleted = true
        }
        return deleted
    }

    private fun openStorageDatabase(
        name: String,
        mode: Int,
        factory: SQLiteDatabase.CursorFactory?,
        errorHandler: DatabaseErrorHandler?,
    ): SQLiteDatabase {
        if (locationOwner.state().location == AndroidDataStorageLocation.INTERNAL) {
            return if (errorHandler == null) super.openOrCreateDatabase(name, mode, factory)
            else super.openOrCreateDatabase(name, mode, factory, errorHandler)
        }
        val file = getDatabasePath(name)
        require(file.parentFile?.mkdirs() != false) { "无法创建数据库目录。" }
        var flags = SQLiteDatabase.CREATE_IF_NECESSARY
        if (mode and Context.MODE_ENABLE_WRITE_AHEAD_LOGGING != 0) flags = flags or SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING
        return if (errorHandler == null) SQLiteDatabase.openDatabase(file.path, factory, flags)
        else SQLiteDatabase.openDatabase(file.path, factory, flags, errorHandler)
    }
}

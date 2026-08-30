package com.nanzhufeng.ai.data

import android.content.Context
import com.nanzhufeng.ai.data.local.NanfengAiDatabase
import com.nanzhufeng.ai.data.local.PrivateAttachmentAssetEntity
import com.nanzhufeng.ai.domain.ImportedZipCleanupResult
import com.nanzhufeng.ai.domain.ImportedZipCleanupStatus
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.DigestInputStream
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipFile

/**
 * Turns ZIP-backed attachment references into ordinary managed attachments before removing the
 * source package. Mapping remains an intake-only ownership aid; no normal attachment is allowed to
 * depend on the package after this owner reports completion.
 */
internal class AndroidImportedZipPackageCleanup(
    context: Context,
    private val database: NanfengAiDatabase,
    private val fileDeleter: (File) -> Boolean = { it.delete() },
) {
    private val filesRoot = context.applicationContext.filesDir.canonicalFile
    private val archiveRoot = safeRoot(ARCHIVE_ROOT) ?: error("ZIP archive root unavailable")
    private val managedAttachmentRoot = safeRoot(MANAGED_ATTACHMENT_ROOT) ?: error("attachment root unavailable")
    private val pendingRoot = safeRoot(PENDING_ROOT) ?: error("ZIP cleanup quarantine unavailable")
    private val db get() = database.openHelper.writableDatabase

    fun status(): ImportedZipCleanupStatus {
        val archives = originalArchives()
        val imported = db.query(
            "SELECT COUNT(*), COALESCE(SUM(a.byteCount), 0) " +
                "FROM private_attachment_assets a INNER JOIN p6k_zip_asset_link_provenance p ON p.attachmentId=a.attachmentId",
        ).use { cursor ->
            cursor.moveToFirst()
            cursor.getLong(0) to cursor.getLong(1)
        }
        val sourceDependentAttachments = db.query(
            "SELECT COUNT(DISTINCT c.attachmentId) FROM p6k_zip_asset_candidates c " +
                "LEFT JOIN private_attachment_assets a ON a.attachmentId=c.attachmentId " +
                "WHERE c.attachmentId IS NOT NULL " +
                "AND (a.attachmentId IS NULL OR a.storageKey NOT LIKE 'attachments/v1/%')",
        ).use { cursor ->
            cursor.moveToFirst()
            cursor.getLong(0)
        }
        return ImportedZipCleanupStatus(
            originalPackageCount = archives.size.toLong(),
            importedAttachmentCount = imported.first,
            importedAttachmentByteCount = imported.second,
            sourceDependentAttachmentCount = sourceDependentAttachments,
            blockedPackageCount = archives.count { isBlocked(it.nameWithoutExtension) }.toLong(),
            pendingDeletionCount = pendingFiles().size.toLong(),
        )
    }

    fun cleanup(): ImportedZipCleanupResult {
        if (pendingRoot.exists() && deleteTree(pendingRoot) > 0) {
            return ImportedZipCleanupResult.Partial(
                deletedPackageCount = 0,
                remainingPackageCount = originalArchives().size.toLong(),
                reason = "上次已隔离的导入包仍有文件待清理，请释放存储占用后重试。",
            )
        }
        val archives = originalArchives()
        val dependencies = linkedCandidates()
            .filter { candidate -> candidate.assetStorageKey?.startsWith("$MANAGED_ATTACHMENT_ROOT/") != true }
            .groupBy(LinkedCandidate::attachmentId)
        if (archives.isEmpty()) {
            return if (dependencies.isEmpty()) {
                ImportedZipCleanupResult.Completed(0, 0)
            } else {
                ImportedZipCleanupResult.Partial(
                    deletedPackageCount = 0,
                    remainingPackageCount = 0,
                    reason = "仍有 ${dependencies.size} 个已归属附件缺少 ZIP 原始包，请重新导入对应原包后重试。",
                )
            }
        }
        val blocked = archives.filter { isBlocked(it.nameWithoutExtension) }
        if (blocked.isNotEmpty()) {
            return ImportedZipCleanupResult.Rejected("仍有 ${blocked.size} 个导入任务未完成，原始包已保留。")
        }

        var materialized = 0L
        val archivesByTask = archives.associateBy { it.nameWithoutExtension }
        dependencies.values.forEach { candidates ->
            try {
                if (materialize(candidates, archivesByTask)) materialized++
            } catch (_: Exception) {
                return ImportedZipCleanupResult.Partial(
                    deletedPackageCount = 0,
                    remainingPackageCount = archives.size.toLong(),
                    reason = "导入附件整理或完整性校验未完成，原始包已保留，可稍后重试。",
                )
            }
        }
        val remainingDependencies = sourceDependentAttachmentCount()
        if (remainingDependencies > 0) {
            return ImportedZipCleanupResult.Partial(
                deletedPackageCount = 0,
                remainingPackageCount = archives.size.toLong(),
                reason = "仍有 $remainingDependencies 个附件依赖原始包，未执行删除。",
            )
        }

        val quarantine = File(pendingRoot, UUID.randomUUID().toString()).canonicalFile
        if (!quarantine.path.startsWith(pendingRoot.path + File.separator) || !quarantine.mkdirs()) {
            return ImportedZipCleanupResult.Partial(0, archives.size.toLong(), "无法创建安全清理区，原始包已保留。")
        }
        val moved = mutableListOf<Pair<File, File>>()
        return try {
            archives.forEach { source ->
                val target = File(quarantine, source.name).canonicalFile
                require(target.path.startsWith(quarantine.path + File.separator))
                Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
                moved += source to target
            }
            database.runInTransaction {
                archives.map { it.nameWithoutExtension }.forEach { taskId ->
                    // These rows describe ZIP entries that were never imported into app data. Once
                    // the user removes the source package they are no longer actionable candidates.
                    db.execSQL("DELETE FROM p6k_zip_asset_candidates WHERE taskId=? AND attachmentId IS NULL", arrayOf(taskId))
                }
            }
            val failures = deleteTree(quarantine)
            if (failures == 0) {
                ImportedZipCleanupResult.Completed(archives.size.toLong(), materialized)
            } else {
                ImportedZipCleanupResult.Partial(
                    deletedPackageCount = archives.size.toLong(),
                    remainingPackageCount = 0,
                    reason = "原始包已从导入位置移除，但隔离区仍有文件待重新清理。",
                )
            }
        } catch (_: Exception) {
            moved.asReversed().forEach { (source, staged) ->
                if (staged.exists() && !source.exists()) runCatching {
                    source.parentFile?.mkdirs()
                    Files.move(staged.toPath(), source.toPath(), StandardCopyOption.ATOMIC_MOVE)
                }
            }
            ImportedZipCleanupResult.Partial(0, originalArchives().size.toLong(), "清理状态发生变化，原始包已保留，请重新确认。")
        }
    }

    /**
     * A linked candidate is the durable recovery evidence. Older interrupted migrations may have
     * committed the candidate/message ownership before the private asset catalog row, and a
     * deduplicated asset may still point at an older ZIP that is no longer retained. In both cases
     * any retained candidate with identical bytes is a valid recovery source.
     */
    private fun materialize(
        candidates: List<LinkedCandidate>,
        archivesByTask: Map<String, File>,
    ): Boolean {
        require(candidates.isNotEmpty())
        val attachmentId = candidates.first().attachmentId
        require(candidates.all { it.attachmentId == attachmentId })
        val existing = database.privateAttachmentAssetDao().findById(attachmentId)
        if (existing?.storageKey?.startsWith("$MANAGED_ATTACHMENT_ROOT/") == true) return false

        val expectedHash = existing?.sha256 ?: candidates.map(LinkedCandidate::sha256).distinct().single()
        val expectedBytes = existing?.byteCount ?: candidates.map(LinkedCandidate::byteCount).distinct().single()
        val expectedMime = existing?.mimeType ?: candidates.map(LinkedCandidate::mimeType).distinct().single()
        require(expectedHash.matches(SHA256) && expectedBytes >= 0)
        require(candidates.any { it.sha256 == expectedHash && it.byteCount == expectedBytes })

        val storedLocation = existing?.storageKey?.let(P6KZipArchiveAssetStorage::parse)
        val recoveryLocations = buildList {
            storedLocation?.let(::add)
            candidates.forEach { candidate ->
                if (candidate.sha256 == expectedHash && candidate.byteCount == expectedBytes) {
                    add(P6KZipArchiveAssetStorage.Location(candidate.taskId, candidate.entryName))
                }
            }
        }.distinct()
        val source = recoveryLocations.firstNotNullOfOrNull { location ->
            val archive = archivesByTask[location.taskId] ?: return@firstNotNullOfOrNull null
            runCatching {
                ZipFile(archive).use { zip ->
                    val entry = requireNotNull(zip.getEntry(location.entryName))
                    require(!entry.isDirectory && entry.name == location.entryName && entry.size == expectedBytes)
                    val temporary = copyVerifiedEntry(zip, entry, expectedBytes, expectedHash)
                    location to temporary
                }
            }.getOrNull()
        } ?: error("ZIP_RECOVERY_SOURCE_UNAVAILABLE")

        if (!managedAttachmentRoot.exists()) require(managedAttachmentRoot.mkdirs())
        val managedName = "$expectedHash${extensionFor(expectedMime)}"
        val managed = File(managedAttachmentRoot, managedName).canonicalFile
        require(managed.path.startsWith(managedAttachmentRoot.path + File.separator))
        val temporary = source.second
        try {
            if (managed.exists()) {
                require(managed.isFile && managed.length() == expectedBytes && managed.sha256() == expectedHash)
            } else {
                Files.move(temporary.toPath(), managed.toPath(), StandardCopyOption.ATOMIC_MOVE)
            }
        } finally {
            if (temporary.exists()) temporary.delete()
        }
        val managedStorageKey = "$MANAGED_ATTACHMENT_ROOT/$managedName"
        var changed = false
        database.runInTransaction {
            val dao = database.privateAttachmentAssetDao()
            val current = dao.findById(attachmentId)
            if (current == null) {
                val displayName = candidates.firstNotNullOfOrNull(LinkedCandidate::displayName)
                dao.insert(
                    PrivateAttachmentAssetEntity(
                        attachmentId = attachmentId,
                        storageKey = managedStorageKey,
                        mimeType = expectedMime,
                        displayName = displayName,
                        byteCount = expectedBytes,
                        sha256 = expectedHash,
                    ),
                )
                changed = true
            } else if (current.storageKey != managedStorageKey) {
                require(current.sha256 == expectedHash && current.byteCount == expectedBytes && current.mimeType == expectedMime)
                require(dao.materializeArchiveBackedAsset(attachmentId, current.storageKey, managedStorageKey) == 1)
                changed = true
            }
            require(dao.findById(attachmentId)?.storageKey == managedStorageKey)
        }
        return changed
    }

    private fun copyVerifiedEntry(
        zip: ZipFile,
        entry: java.util.zip.ZipEntry,
        expectedBytes: Long,
        expectedHash: String,
    ): File {
        if (!managedAttachmentRoot.exists()) require(managedAttachmentRoot.mkdirs())
        val temporary = File.createTempFile("zip-materialize-", ".part", managedAttachmentRoot)
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var copied = 0L
            zip.getInputStream(entry).use { input ->
                FileOutputStream(temporary).use { output ->
                    val digestOutput = DigestOutputStream(output, digest)
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        copied += count
                        require(copied <= expectedBytes)
                        digestOutput.write(buffer, 0, count)
                    }
                    digestOutput.flush()
                    output.fd.sync()
                }
            }
            require(copied == expectedBytes && digest.digest().toHex() == expectedHash)
            return temporary
        } catch (error: Throwable) {
            temporary.delete()
            throw error
        }
    }

    private fun sourceDependentAttachmentCount(): Long = db.query(
        "SELECT COUNT(DISTINCT c.attachmentId) FROM p6k_zip_asset_candidates c " +
            "LEFT JOIN private_attachment_assets a ON a.attachmentId=c.attachmentId " +
            "WHERE c.attachmentId IS NOT NULL " +
            "AND (a.attachmentId IS NULL OR a.storageKey NOT LIKE 'attachments/v1/%')",
    ).use { cursor -> cursor.moveToFirst(); cursor.getLong(0) }

    private fun linkedCandidates(): List<LinkedCandidate> = db.query(
        "SELECT c.taskId,c.entryName,c.sha256,c.byteCount,c.mimeType,c.attachmentId," +
            "COALESCE(k.displayName,a.displayName),a.storageKey " +
            "FROM p6k_zip_asset_candidates c " +
            "LEFT JOIN p6k_zip_asset_catalog k ON k.taskId=c.taskId AND k.entryName=c.entryName " +
            "LEFT JOIN private_attachment_assets a ON a.attachmentId=c.attachmentId " +
            "WHERE c.attachmentId IS NOT NULL ORDER BY c.attachmentId,c.taskId,c.entryName",
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(
                    LinkedCandidate(
                        taskId = cursor.getString(0),
                        entryName = cursor.getString(1),
                        sha256 = cursor.getString(2),
                        byteCount = cursor.getLong(3),
                        mimeType = cursor.getString(4),
                        attachmentId = cursor.getString(5),
                        displayName = cursor.takeUnless { it.isNull(6) }?.getString(6),
                        assetStorageKey = cursor.takeUnless { it.isNull(7) }?.getString(7),
                    ),
                )
            }
        }
    }

    private fun isBlocked(taskId: String): Boolean = db.query(
        "SELECT t.status, j.state FROM p6k_zip_import_tasks t " +
            "LEFT JOIN p6k_zip_asset_recovery_jobs j ON j.taskId=t.id WHERE t.id=?",
        arrayOf(taskId),
    ).use { cursor ->
        if (!cursor.moveToFirst()) return@use true
        val taskComplete = cursor.getString(0) == "COMPLETED"
        val recoveryComplete = cursor.isNull(1) || cursor.getString(1) == "COMPLETED"
        !taskComplete || !recoveryComplete
    }

    private fun originalArchives(): List<File> = archiveRoot.listFiles().orEmpty()
        .filter { it.isFile && !Files.isSymbolicLink(it.toPath()) && it.extension == "zip" && it.nameWithoutExtension.matches(TASK_ID) }
        .sortedBy(File::getName)

    private fun pendingFiles(): List<File> = pendingRoot.takeIf(File::exists)?.walkTopDown()?.filter(File::isFile)?.toList().orEmpty()
    private fun deleteTree(root: File): Int = root.walkBottomUp().count { it.exists() && !fileDeleter(it) }
    private fun safeRoot(relative: String): File? = File(filesRoot, relative).canonicalFile.takeIf { it.path.startsWith(filesRoot.path + File.separator) }
    private fun File.sha256(): String = FileInputStream(this).use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        DigestInputStream(input, digest).use { stream ->
            val buffer = ByteArray(64 * 1024)
            while (stream.read(buffer) >= 0) Unit
        }
        digest.digest().toHex()
    }
    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
    private fun extensionFor(mimeType: String): String = when (mimeType) {
        "image/jpeg" -> ".jpg"
        "image/png" -> ".png"
        "image/webp" -> ".webp"
        "video/mp4" -> ".mp4"
        "audio/mpeg" -> ".mp3"
        "audio/wav" -> ".wav"
        "audio/mp4" -> ".m4a"
        "application/pdf" -> ".pdf"
        "text/markdown" -> ".md"
        "application/json" -> ".json"
        "text/csv" -> ".csv"
        "text/plain" -> ".txt"
        else -> ".bin"
    }

    private companion object {
        const val ARCHIVE_ROOT = "p6k-zip-import/v1/archives"
        const val MANAGED_ATTACHMENT_ROOT = "attachments/v1"
        const val PENDING_ROOT = "p5c-pending-delete/zip-archives"
        val TASK_ID = Regex("[A-Za-z0-9-]{1,80}")
        val SHA256 = Regex("[a-f0-9]{64}")
    }

    private data class LinkedCandidate(
        val taskId: String,
        val entryName: String,
        val sha256: String,
        val byteCount: Long,
        val mimeType: String,
        val attachmentId: String,
        val displayName: String?,
        val assetStorageKey: String?,
    )
}

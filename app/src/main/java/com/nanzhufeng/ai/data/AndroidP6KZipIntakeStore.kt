package com.nanzhufeng.ai.data

import android.content.Context
import com.nanzhufeng.ai.domain.P6KChatGptZipCandidateMapper
import com.nanzhufeng.ai.domain.P6KZipCandidateFailure
import com.nanzhufeng.ai.domain.P6KZipImportTask
import com.nanzhufeng.ai.domain.P6KZipImportTaskRepository
import com.nanzhufeng.ai.domain.P6KProfilePersonalizationSettingsOwner
import com.nanzhufeng.ai.domain.P6KZipAssetRole
import com.nanzhufeng.ai.domain.P6KChatGptZipAssetMapper
import com.nanzhufeng.ai.domain.P6KZipAssetMappingResult
import com.nanzhufeng.ai.domain.P6KZipAssetMapping
import com.nanzhufeng.ai.domain.P6KZipAssetRecoveryFailureKind
import com.nanzhufeng.ai.domain.P6KZipAssetRecoveryJob
import com.nanzhufeng.ai.domain.P6KZipAssetRecoveryJobRepository
import com.nanzhufeng.ai.domain.P6KZipAssetRecoveryScheduler
import com.nanzhufeng.ai.domain.P6KZipAssetRecoveryState
import com.nanzhufeng.ai.domain.P6KZipMappedAssetLinkOwner
import com.nanzhufeng.ai.domain.P6KZipManualAssetLinkOwner
import com.nanzhufeng.ai.domain.P6KZipManualAssetLinkResult
import com.nanzhufeng.ai.domain.P6KZipManualLinkTarget
import com.nanzhufeng.ai.domain.P6KZipTaskId
import com.nanzhufeng.ai.domain.P6KZipTaskStatus
import com.nanzhufeng.ai.domain.ManageP6KChatGptZipImportUseCase
import com.nanzhufeng.ai.domain.P6K_ZIP_MAX_ARCHIVE_BYTES
import com.nanzhufeng.ai.domain.ThirdPartyZipInventoryPolicy
import com.nanzhufeng.ai.domain.ThirdPartyZipInventoryResult
import com.nanzhufeng.ai.domain.ThirdPartyZipProvider
import com.nanzhufeng.ai.domain.AttachmentImportRequest
import com.nanzhufeng.ai.domain.AttachmentImportResult
import com.nanzhufeng.ai.domain.PrivateAttachmentStore
import com.nanzhufeng.ai.domain.AttachmentId
import com.nanzhufeng.ai.domain.AttachmentReference
import com.nanzhufeng.ai.data.local.RoomConversationRepository
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.util.Properties
import java.util.zip.ZipFile

/**
 * K1's Android envelope owner: picker handles are discarded after one private copy.
 * Room persists only recovery-safe task/candidate facts; neither this store nor the mapper can commit a Conversation.
 */
interface P6KZipImportUiStore {
    fun stage(provider: ThirdPartyZipProvider, displayName: String, mimeType: String, input: InputStream): P6KZipImportTask
    fun list(): List<P6KZipImportTask>
    fun recoveryJobs(): List<P6KZipAssetRecoveryJob>
    fun retryAssetRecovery(taskId: String)
    fun cancel(id: String): Boolean
    fun manualLinkTargets(taskId: String): List<P6KZipManualLinkTarget>
    fun linkUnmappedAsset(taskId: String, entryName: String, conversationId: String, messageId: String): P6KZipImportTask
}

class AndroidP6KZipIntakeStore(
    context: Context,
    private val tasks: P6KZipImportTaskRepository,
    private val imports: ManageP6KChatGptZipImportUseCase,
    private val profileSettings: P6KProfilePersonalizationSettingsOwner,
    private val manualAssetLinks: P6KZipManualAssetLinkOwner,
    private val mappedAssetLinks: P6KZipMappedAssetLinkOwner,
    private val privateAttachments: PrivateAttachmentStore,
    private val conversations: RoomConversationRepository,
    private val clock: Clock = Clock.systemUTC(),
    private val mapper: P6KChatGptZipCandidateMapper = P6KChatGptZipCandidateMapper(),
    private val assetMapper: P6KChatGptZipAssetMapper = P6KChatGptZipAssetMapper(),
    private val assetRecoveryJobs: P6KZipAssetRecoveryJobRepository? = null,
    private val assetRecoveryScheduler: P6KZipAssetRecoveryScheduler? = null,
) : P6KZipImportUiStore {
    private val root = File(context.filesDir, "p6k-zip-import/v1").also { it.mkdirs() }
    private val archives = File(root, "archives").also { it.mkdirs() }
    private val legacyTasks = File(root, "tasks").also { it.mkdirs() }
    private val legacyEntries = File(root, "entries").also { it.mkdirs() }

    override fun stage(provider: ThirdPartyZipProvider, displayName: String, mimeType: String, input: InputStream): P6KZipImportTask {
        migrateLegacyJournal()
        val id = P6KZipTaskId.new(); val now = clock.instant(); val safeName = safeName(displayName)
        // Android document providers may label a ZIP as generic binary.  Filename is only a routing
        // hint: the copied bytes still must pass the strict ZIP central-directory inventory below.
        if (!displayName.endsWith(".zip", true) || (mimeType.isNotBlank() && mimeType !in ZIP_MIME_TYPES && mimeType != "application/octet-stream")) return tasks.save(P6KZipImportTask(id, provider, safeName, 0, "", P6KZipTaskStatus.FAILED, P6KZipCandidateFailure.NOT_ZIP, createdAt = now, updatedAt = now))
        val temporary = File(archives, ".${id.value}.part"); val digest = MessageDigest.getInstance("SHA-256"); var bytes = 0L
        try {
            input.use { source -> temporary.outputStream().use { target ->
                val buffer = ByteArray(8 * 1024)
                while (true) { val count = source.read(buffer); if (count < 0) break; bytes += count; if (bytes > P6K_ZIP_MAX_ARCHIVE_BYTES) throw LimitExceeded; digest.update(buffer, 0, count); target.write(buffer, 0, count) }
            } }
            val archive = File(archives, "${id.value}.zip"); if (!temporary.renameTo(archive)) error("private archive rename failed")
            val hash = digest.digest().hex()
            val base = P6KZipImportTask(id, provider, safeName, bytes, hash, P6KZipTaskStatus.PARSING, createdAt = now, updatedAt = clock.instant())
            return when (val inventory = ThirdPartyZipInventoryPolicy.inspect(archive)) {
                is ThirdPartyZipInventoryResult.Rejected -> {
                    archive.delete(); tasks.save(base.copy(status = P6KZipTaskStatus.FAILED, failure = P6KZipCandidateFailure.INVENTORY_REJECTED, updatedAt = clock.instant()))
                }
                is ThirdPartyZipInventoryResult.InventoriedUnsupported -> when (val mapped = mapper.map(provider, archive, inventory.entries)) {
                    is com.nanzhufeng.ai.domain.P6KChatGptZipMappingResult.Waiting -> tasks.save(base.copy(status = P6KZipTaskStatus.WAITING_FORMAT_EVIDENCE, failure = mapped.failure, updatedAt = clock.instant()))
                    is com.nanzhufeng.ai.domain.P6KChatGptZipMappingResult.Rejected -> tasks.save(base.copy(status = P6KZipTaskStatus.FAILED, failure = mapped.failure, updatedAt = clock.instant()))
                    is com.nanzhufeng.ai.domain.P6KChatGptZipMappingResult.Mapped -> {
                        val staged = tasks.save(base.copy(status = P6KZipTaskStatus.AWAITING_CONFIRMATION, formatVersion = mapped.formatVersion, items = mapped.items, assets = mapped.assets, profile = mapped.profile, updatedAt = clock.instant()))
                        mapped.profile.personalization?.let { profileSettings.commit(staged, it, clock.instant()) }
                        val imported = imports.importAll(staged.id)
                        if (assetRecoveryJobs != null && assetRecoveryScheduler != null) {
                            val job = assetRecoveryJobs.find(imported.id) ?: P6KZipAssetRecoveryJob(
                                taskId = imported.id,
                                state = P6KZipAssetRecoveryState.PENDING,
                                updatedAtMs = clock.millis(),
                            )
                            assetRecoveryJobs.save(job)
                            assetRecoveryScheduler.enqueue(imported.id)
                            imported
                        } else {
                            reconcileMappedAssets(imported)
                        }
                    }
                }
            }
        } catch (_: LimitExceeded) { temporary.delete(); return tasks.save(P6KZipImportTask(id, provider, safeName, bytes, "", P6KZipTaskStatus.FAILED, P6KZipCandidateFailure.TOO_LARGE, createdAt = now, updatedAt = clock.instant())) }
        catch (_: Exception) { temporary.delete(); return tasks.save(P6KZipImportTask(id, provider, safeName, bytes, "", P6KZipTaskStatus.FAILED, P6KZipCandidateFailure.PRIVATE_COPY_FAILED, createdAt = now, updatedAt = clock.instant())) }
    }

    override fun list(): List<P6KZipImportTask> {
        migrateLegacyJournal()
        return tasks.list()
    }
    override fun recoveryJobs(): List<P6KZipAssetRecoveryJob> = assetRecoveryJobs?.list().orEmpty()
    override fun retryAssetRecovery(taskId: String) {
        val id = P6KZipTaskId(taskId)
        val repository = assetRecoveryJobs ?: return
        val current = repository.find(id) ?: return
        repository.save(current.copy(state = P6KZipAssetRecoveryState.PENDING, lastFailureKind = null, lastFailureAtMs = null, updatedAtMs = clock.millis()))
        assetRecoveryScheduler?.enqueue(id)
    }
    fun recordAssetRecoveryInterruption(taskId: String) {
        val repository = assetRecoveryJobs ?: return
        val id = P6KZipTaskId(taskId)
        val current = repository.find(id) ?: return
        repository.save(current.copy(
            state = P6KZipAssetRecoveryState.PARTIAL,
            lastFailureKind = P6KZipAssetRecoveryFailureKind.INTERRUPTED,
            lastFailureAtMs = clock.millis(),
            updatedAtMs = clock.millis(),
        ))
    }

    /** Worker-only owner. UI reads the persisted job and never invokes this method. */
    fun runAssetRecovery(taskId: String): P6KZipAssetRecoveryJob? {
        val repository = assetRecoveryJobs ?: return null
        val id = P6KZipTaskId(taskId)
        var task = tasks.find(id) ?: return null
        var job = repository.find(id) ?: repository.save(P6KZipAssetRecoveryJob(id, P6KZipAssetRecoveryState.PENDING, updatedAtMs = clock.millis()))
        if (assetMarker(taskId).isFile) {
            val linked = task.assets.count { it.attachmentId != null }
            job = repository.save(job.copy(
                state = P6KZipAssetRecoveryState.COMPLETED,
                totalOccurrences = linked,
                linkedOccurrences = linked,
                processedConversations = job.totalConversations,
                uniqueAssets = linked,
                failedConversations = 0,
                lastFailureKind = null,
                lastFailureAtMs = null,
                updatedAtMs = clock.millis(),
            ))
            assetMarker(taskId).delete()
            legacyAssetMarker(taskId).delete()
            return job
        }
        val archive = File(archives, "$taskId.zip")
        if (!archive.isFile) return repository.save(job.failed(P6KZipAssetRecoveryFailureKind.MISSING_PRIVATE_ARCHIVE))
        job = repository.save(job.copy(
            state = P6KZipAssetRecoveryState.INDEXING,
            failedConversations = 0,
            lastFailureKind = null,
            lastFailureAtMs = null,
            updatedAtMs = clock.millis(),
        ))
        val mapping = try {
            ZipFile(archive).use { zip ->
                val mapped = (assetMapper.map(zip, task.assets) as? P6KZipAssetMappingResult.Mapped)?.value
                    ?: return repository.save(job.failed(P6KZipAssetRecoveryFailureKind.MAPPING_REJECTED))
                verifyMappedAssetHashes(zip, mapped)
            }
        } catch (_: Exception) {
            return repository.save(job.failed(P6KZipAssetRecoveryFailureKind.INDEX_REJECTED))
        }
        val verifiedByEntry = mapping.assets.mapValues { it.value.candidate }
        task = tasks.save(task.copy(
            assets = task.assets.map { verifiedByEntry[it.entryName] ?: it },
            updatedAt = clock.instant(),
        ))
        val occurrenceKeys = mapping.conversations.flatMap { conversation ->
            conversation.currentPath.flatMap { message ->
                message.entryNames.map { entryName -> Triple(conversation.sourceConversationId, message.sourceMessageId, entryName) }
            }
        }.toSet()
        val missingEntryNames = occurrenceKeys.map(Triple<String, String, String>::third)
            .filterNot(mapping.assets::containsKey).toSet()
        val recoveryConversations = mapping.conversations.filter { conversation ->
            conversation.currentPath.any { message -> message.entryNames.any(mapping.assets::containsKey) }
        }
        job = repository.save(job.copy(
            state = P6KZipAssetRecoveryState.MAPPING,
            totalOccurrences = occurrenceKeys.size,
            totalConversations = recoveryConversations.size,
            uniqueAssets = mapping.assets.size,
            missingEntries = missingEntryNames.size,
            unattributedCandidates = (task.assets.size - mapping.assets.size).coerceAtLeast(0),
            sourceReferenceRecords = mapping.conversations.sumOf { conversation ->
                conversation.currentPath.sumOf { it.sourceReferenceRecords }
            },
            fallbackNamedAssets = mapping.fallbackNamedEntries.size,
            updatedAtMs = clock.millis(),
        ))
        val prepared = mapping.assets.mapValues { (entryName, mapped) ->
            val candidate = mapped.candidate
            AttachmentReference(
                reference = P6KZipArchiveAssetStorage.key(task.id.value, entryName),
                mimeType = candidate.mimeType,
                displayName = mapped.displayName,
                id = AttachmentId.new(),
                byteCount = candidate.byteCount,
                sha256 = candidate.sha256,
            )
        }
        job = repository.save(job.copy(state = P6KZipAssetRecoveryState.LINKING, updatedAtMs = clock.millis()))
        val summary = mappedAssetLinks.reconcileResumable(task, mapping, prepared, job, clock.instant())
        job = repository.find(id) ?: job
        if (summary.failedConversationCount > 0) return job
        val refreshedTask = tasks.find(id) ?: task
        val linked = refreshedTask.assets.count { it.attachmentId != null }
        job = repository.save(job.copy(
            state = P6KZipAssetRecoveryState.COMPLETED,
            linkedOccurrences = linked,
            processedConversations = recoveryConversations.size,
            failedConversations = 0,
            lastFailureKind = null,
            lastFailureAtMs = null,
            updatedAtMs = clock.millis(),
        ))
        legacyAssetMarker(taskId).delete()
        return job
    }
    /** Only messages in this ZIP task's already committed conversations can be chosen.  No body or source ID leaves this store. */
    override fun manualLinkTargets(taskId: String): List<P6KZipManualLinkTarget> {
        val task = tasks.find(P6KZipTaskId(taskId)) ?: return emptyList()
        return task.items.mapNotNull { it.conversationId }.distinct().flatMap { conversationId ->
            val snapshot = conversations.findById(conversationId) ?: return@flatMap emptyList()
            snapshot.nodes.sortedWith(compareBy<com.nanzhufeng.ai.domain.MessageNode> { it.createdAt }.thenBy { it.siblingPosition }.thenBy { it.id.value }).mapIndexed { ordinal, node ->
                P6KZipManualLinkTarget(conversationId, node.id, snapshot.conversation.title, ordinal + 1, node.role, node.createdAt)
            }
        }
    }
    /** The user has already selected one candidate and one target message.  This performs no picker
     * work and never reads another ZIP entry. */
    override fun linkUnmappedAsset(taskId: String, entryName: String, conversationId: String, messageId: String): P6KZipImportTask {
        val id = P6KZipTaskId(taskId); val task = tasks.find(id) ?: return failed(taskId, entryName)
        val asset = task.assets.firstOrNull { it.entryName == entryName } ?: return task
        if (asset.role in setOf(P6KZipAssetRole.SOURCE_MAPPED, P6KZipAssetRole.MANUAL_LINKED)) return task
        val target = manualLinkTargets(taskId).firstOrNull { it.conversationId.value == conversationId && it.messageId.value == messageId } ?: return failed(taskId, entryName)
        val archive = File(archives, "$taskId.zip")
        return try {
            ZipFile(archive).use { zip ->
                val entry = zip.getEntry(asset.entryName) ?: return failed(taskId, entryName)
                if (entry.isDirectory || entry.name.replace('\\', '/') != asset.entryName || entry.size != asset.byteCount) return failed(taskId, entryName)
                val verifiedAsset = asset.copy(sha256 = verifiedSha256(zip, asset))
                val verifiedTask = tasks.save(task.copy(
                    assets = task.assets.map { if (it.entryName == entryName) verifiedAsset else it },
                    updatedAt = clock.instant(),
                ))
                val imported = zip.getInputStream(entry).use { source -> privateAttachments.import(AttachmentImportRequest(source, asset.mimeType, null)) }
                val attachment = (imported as? AttachmentImportResult.Imported)?.attachment ?: return failed(taskId, entryName)
                when (manualAssetLinks.link(verifiedTask, verifiedAsset, target, attachment, clock.instant())) {
                    P6KZipManualAssetLinkResult.Linked, P6KZipManualAssetLinkResult.Replayed -> requireNotNull(tasks.find(id))
                    P6KZipManualAssetLinkResult.Conflict, P6KZipManualAssetLinkResult.Failed -> failed(taskId, entryName)
                }
            }
        } catch (_: Exception) { failed(taskId, entryName) }
    }
    /**
     * Keep the recovery journal and private archive if any owner cannot revoke its part of the
     * batch.  Otherwise a failed conversation revoke would orphan imported conversations after
     * Settings had already removed the only retry/delete entry point.
     */
    override fun cancel(id: String): Boolean {
        migrateLegacyJournal()
        val taskId = P6KZipTaskId(id); val task = tasks.find(taskId)
        if (task != null) {
            assetRecoveryScheduler?.cancel(taskId)
            if (!imports.deleteBatch(taskId)) return false
            if (!manualAssetLinks.revokeBatch(task, clock.instant())) return false
            if (!profileSettings.revokeBatch(task, clock.instant())) return false
            if (!File(archives, "$id.zip").let { !it.exists() || it.delete() }) return false
            if (!File(legacyTasks, "$id.properties").let { !it.exists() || it.delete() }) return false
            if (!File(legacyEntries, "$id.tsv").let { !it.exists() || it.delete() }) return false
            if (!assetMarker(id).let { !it.exists() || it.delete() }) return false
            if (!legacyAssetMarker(id).let { !it.exists() || it.delete() }) return false
            tasks.delete(taskId)
            return true
        }
        val archiveDeleted = File(archives, "$id.zip").let { !it.exists() || it.delete() }
        val journalDeleted = File(legacyTasks, "$id.properties").let { !it.exists() || it.delete() } &&
            File(legacyEntries, "$id.tsv").let { !it.exists() || it.delete() } &&
            assetMarker(id).let { !it.exists() || it.delete() } &&
            legacyAssetMarker(id).let { !it.exists() || it.delete() }
        return archiveDeleted && journalDeleted
    }
    private fun failed(taskId: String, entryName: String): P6KZipImportTask {
        val id = P6KZipTaskId(taskId); val task = tasks.find(id) ?: return P6KZipImportTask(id, ThirdPartyZipProvider.CHATGPT, "selected.zip", 0, "", P6KZipTaskStatus.FAILED, createdAt = clock.instant(), updatedAt = clock.instant())
        return tasks.save(task.copy(assets = task.assets.map { asset -> if (asset.entryName == entryName && asset.role !in setOf(P6KZipAssetRole.SOURCE_MAPPED, P6KZipAssetRole.MANUAL_LINKED)) asset.copy(role = P6KZipAssetRole.MANUAL_LINK_FAILED, failure = "MANUAL_LINK_FAILED") else asset }, updatedAt = clock.instant()))
    }

    /** Existing completed imports are upgraded once from the retained official ZIP. */
    private fun reconcileMappedAssets(task: P6KZipImportTask): P6KZipImportTask {
        if (task.provider != ThirdPartyZipProvider.CHATGPT || task.status !in setOf(P6KZipTaskStatus.COMPLETED, P6KZipTaskStatus.PARTIALLY_COMPLETED)) return task
        if (assetMarker(task.id.value).isFile) return task
        val archive = File(archives, "${task.id.value}.zip")
        if (!archive.isFile) return task
        val mapping = try {
            ZipFile(archive).use { zip ->
                val mapped = (assetMapper.map(zip, task.assets) as? P6KZipAssetMappingResult.Mapped)?.value
                    ?: return task
                verifyMappedAssetHashes(zip, mapped)
            }
        } catch (_: Exception) { return task }
        val verifiedByEntry = mapping.assets.mapValues { it.value.candidate }
        val verifiedTask = tasks.save(task.copy(
            assets = task.assets.map { verifiedByEntry[it.entryName] ?: it },
            updatedAt = clock.instant(),
        ))
        val prepared = mapping.assets.mapValues { (entryName, mapped) ->
            val candidate = mapped.candidate
            AttachmentReference(
                reference = P6KZipArchiveAssetStorage.key(verifiedTask.id.value, entryName),
                mimeType = candidate.mimeType,
                displayName = mapped.displayName,
                id = AttachmentId.new(),
                byteCount = candidate.byteCount,
                sha256 = candidate.sha256,
            )
        }
        val summary = mappedAssetLinks.reconcile(verifiedTask, mapping, prepared, clock.instant())
        val refreshed = tasks.find(verifiedTask.id) ?: return verifiedTask
        val byEntry = refreshed.assets.associateBy { it.entryName }
        if (summary.failedConversationCount == 0 && mapping.assets.isNotEmpty() && mapping.assets.keys.all { byEntry[it]?.attachmentId != null }) {
            runCatching {
                assetMarker(task.id.value).outputStream().use { it.write("mapped-v2\n".toByteArray()) }
                legacyAssetMarker(task.id.value).delete()
            }
        }
        return refreshed
    }

    /** v1 could be written for an empty mapping and permanently suppress a real retry. */
    private fun assetMarker(taskId: String) = File(root, "$taskId.assets-v2.done")
    private fun legacyAssetMarker(taskId: String) = File(root, "$taskId.assets-v1.done")

    /** One-time K0 properties-to-Room bridge. It keeps the private archive and never fabricates candidates. */
    private fun migrateLegacyJournal() {
        legacyTasks.listFiles()?.filter { it.extension == "properties" }?.forEach { file -> runCatching {
            val properties = Properties().apply { file.inputStream().use(::load) }; val id = P6KZipTaskId(properties.getProperty("id"))
            if (tasks.find(id) == null) {
                val at = Instant.ofEpochMilli(file.lastModified().takeIf { it > 0 } ?: clock.millis())
                val status = properties.getProperty("status")?.let { runCatching { P6KZipTaskStatus.valueOf(it) }.getOrNull() } ?: P6KZipTaskStatus.FAILED
                val failure = properties.getProperty("failure")?.let { runCatching { P6KZipCandidateFailure.valueOf(it) }.getOrNull() } ?: P6KZipCandidateFailure.UNKNOWN_MANIFEST_SCHEMA
                tasks.save(P6KZipImportTask(id, ThirdPartyZipProvider.valueOf(properties.getProperty("provider")), properties.getProperty("displayName").let(::safeName), properties.getProperty("byteCount").toLong(), properties.getProperty("sha256"), status, failure, createdAt = at, updatedAt = at))
            }
            file.delete(); File(legacyEntries, "${id.value}.tsv").delete()
        } }
    }
    private fun safeName(value: String) = value.substringAfterLast('/').substringAfterLast('\\').take(120).ifBlank { "selected.zip" }
    private fun P6KZipAssetRecoveryJob.failed(kind: P6KZipAssetRecoveryFailureKind): P6KZipAssetRecoveryJob = copy(
        state = P6KZipAssetRecoveryState.FAILED,
        lastFailureKind = kind,
        lastFailureAtMs = clock.millis(),
        updatedAtMs = clock.millis(),
    )
    private fun verifyMappedAssetHashes(zip: ZipFile, mapping: P6KZipAssetMapping): P6KZipAssetMapping = mapping.copy(
        assets = mapping.assets.mapValues { (_, mapped) ->
            mapped.copy(candidate = mapped.candidate.copy(sha256 = verifiedSha256(zip, mapped.candidate)))
        },
    )
    private fun verifiedSha256(zip: ZipFile, candidate: com.nanzhufeng.ai.domain.P6KZipAssetCandidate): String {
        val entry = requireNotNull(zip.getEntry(candidate.entryName))
        require(!entry.isDirectory && entry.name.replace('\\', '/') == candidate.entryName)
        require(entry.size == candidate.byteCount)
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        zip.getInputStream(entry).use { source ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = source.read(buffer)
                if (count < 0) break
                total += count
                require(total <= candidate.byteCount)
                digest.update(buffer, 0, count)
            }
        }
        require(total == candidate.byteCount)
        val actual = digest.digest().hex()
        require(candidate.sha256.isBlank() || candidate.sha256 == actual)
        return actual
    }
    private data object LimitExceeded : Throwable()
    private companion object { val ZIP_MIME_TYPES = setOf("application/zip", "application/x-zip-compressed") }
}
private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }

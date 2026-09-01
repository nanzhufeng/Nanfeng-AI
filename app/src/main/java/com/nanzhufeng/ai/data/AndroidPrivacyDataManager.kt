package com.nanzhufeng.ai.data

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import com.nanzhufeng.ai.data.local.NanfengAiDatabase
import com.nanzhufeng.ai.data.local.RoomPrivateAttachmentRepository
import com.nanzhufeng.ai.domain.AttachmentId
import com.nanzhufeng.ai.domain.PrivateAttachmentCleanupResult
import com.nanzhufeng.ai.domain.PrivacyAggregate
import com.nanzhufeng.ai.domain.PrivacyDataManager
import com.nanzhufeng.ai.domain.PrivacyDeleteScope
import com.nanzhufeng.ai.domain.PrivacyDeletionPreview
import com.nanzhufeng.ai.domain.PrivacyDeletionRequest
import com.nanzhufeng.ai.domain.PrivacyDeletionResult
import com.nanzhufeng.ai.domain.PrivacyInventory
import com.nanzhufeng.ai.domain.PrivacyTaskAdapter
import com.nanzhufeng.ai.domain.PrivacyTaskDeletionCandidate
import com.nanzhufeng.ai.domain.PrivacyTaskDeletionPolicy
import com.nanzhufeng.ai.domain.ImportedZipCleanupResult
import com.nanzhufeng.ai.domain.SecurityDiagnosticAllowlist
import com.nanzhufeng.ai.domain.SecurityDiagnosticArtifact
import com.nanzhufeng.ai.domain.SecurityDiagnosticResult
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID

private val LOCAL_BUSINESS_PREFERENCE_FILES = setOf(
    "assistant_experience_settings_v1",
    "appearance_settings_v1",
    "chat_routing_policy_v1",
    "conversation-style-overrides-v1",
    "conversation-web-search-overrides-v1",
    "conversation_app_entry",
    "conversation_read_markers_v1",
    "direct_chat_call_audit_v1",
    "history_knowledge_auto_curation_runs_v1",
    "history_knowledge_auto_curation_v1",
    "local-audio-preview-position-v1",
    "local-pdf-preview-position-v1",
    "local-search-history-v1",
    "local-video-preview-position-v1",
    "model-health-v1",
    "model_service_settings_v1",
    "nanfeng_ai_google_account",
    "nanfeng_ai_selected_conversation_sync",
    "notification_reminder_settings_v1",
    "p5a_ui",
    "p5d_local_backup",
    "p6g-model-selection-v1",
    "p6k_import_identity",
    "p7e_restore_receipts_v1",
    "privacy_inventory_cache_v1",
    "provider_credentials_v1",
)

/** P5-C Android owner. It only queries aggregate SQL and never selects a user-content column. */
class AndroidPrivacyDataManager(
    private val context: Context,
    private val database: NanfengAiDatabase,
    private val appVersion: String,
    /** Test seam for a deterministic app-private quarantine cleanup failure; production uses File.delete. */
    private val fileDeleter: (File) -> Boolean = { it.delete() },
) : PrivacyDataManager {
    private val filesRoot = context.applicationContext.filesDir.canonicalFile
    private val inventoryCache = context.applicationContext.getSharedPreferences("privacy_inventory_cache_v1", Context.MODE_PRIVATE)
    private val db get() = database.openHelper.writableDatabase
    private val importedZipCleanup = AndroidImportedZipPackageCleanup(context, database)

    override fun cachedInventory(): PrivacyInventory {
        val encoded = inventoryCache.getString("aggregates", null) ?: return PrivacyInventory.EmptySnapshot
        val aggregates = runCatching {
            encoded.split('|').filter(String::isNotBlank).map { entry ->
                val (key, count, bytes) = entry.split(':', limit = 3)
                PrivacyAggregate(key, count.toLong(), bytes.toLong())
            }
        }.getOrNull() ?: return PrivacyInventory.EmptySnapshot
        return PrivacyInventory(
            aggregates = aggregates,
            credentialReferencePresent = inventoryCache.getBoolean("credentialReferencePresent", false),
            internetPermissionPresent = inventoryCache.getBoolean("internetPermissionPresent", false),
            importedZipCleanup = com.nanzhufeng.ai.domain.ImportedZipCleanupStatus(
                originalPackageCount = inventoryCache.getLong("zipOriginalPackageCount", 0L),
                importedAttachmentCount = inventoryCache.getLong("zipImportedAttachmentCount", 0L),
                importedAttachmentByteCount = inventoryCache.getLong("zipImportedAttachmentByteCount", 0L),
                sourceDependentAttachmentCount = inventoryCache.getLong("zipSourceDependentAttachmentCount", 0L),
                blockedPackageCount = inventoryCache.getLong("zipBlockedPackageCount", 0L),
                pendingDeletionCount = inventoryCache.getLong("zipPendingDeletionCount", 0L),
            ),
        )
    }

    override fun inventory(): PrivacyInventory {
        cleanupOrphanedAttachmentFilesSilently()
        return PrivacyInventory(
            aggregates = inventoryAggregates(),
            credentialReferencePresent = context.getSharedPreferences("provider_credentials_v1", Context.MODE_PRIVATE).contains("OPENROUTER"),
            internetPermissionPresent = context.packageManager.checkPermission(android.Manifest.permission.INTERNET, context.packageName) == PackageManager.PERMISSION_GRANTED,
            importedZipCleanup = importedZipCleanup.status(),
        ).also(::cacheInventory)
    }

    override fun preview(scope: PrivacyDeleteScope, selectedTaskIds: Set<String>): PrivacyDeletionPreview {
        val candidates = if (scope == PrivacyDeleteScope.TEMPORARY_FAILED_TASK_ASSETS) taskDeletionCandidates() else emptyList()
        val selected = candidates.filter { it.selectionId in selectedTaskIds }
        val aggregates = when (scope) {
            PrivacyDeleteScope.TEMPORARY_FAILED_TASK_ASSETS -> selected.groupBy { it.adapter }.map { (adapter, rows) ->
                PrivacyAggregate("selected_${adapter.wireValue}_tasks", rows.size.toLong(), rows.sumOf { it.privateAssetByteCount })
            }
            PrivacyDeleteScope.ORPHANED_ATTACHMENT_FILES -> listOf(orphanedAttachmentAggregate())
            PrivacyDeleteScope.OFFLINE_EVAL_RUNS -> listOf(PrivacyAggregate("offline_eval_runs", count("offline_eval_runs")), fileAggregate("offline_eval_reports", "exports/offline-eval/v1"))
            PrivacyDeleteScope.KNOWLEDGE_MEMORY_TRASH -> listOf(PrivacyAggregate("deleted_knowledge", countWhere("knowledge_items", "status='DELETED'")), PrivacyAggregate("deleted_memory", countWhere("memories", "status='DELETED'")))
            PrivacyDeleteScope.ALL_LOCAL_BUSINESS_DATA -> inventoryAggregates() + knownRoots().map { fileAggregate("files_${it.replace('/', '_')}", it) }
        }.filter { it.count > 0 || it.byteCount > 0 }
        val stable = buildString {
            append(scope.wireValue)
            aggregates.sortedBy { it.key }.forEach { append('|').append(it.key).append(':').append(it.count).append(':').append(it.byteCount) }
            selected.sortedWith(compareBy<PrivacyTaskDeletionCandidate> { it.adapter.wireValue }.thenBy { it.selectionId }).forEach {
                append('|').append(it.adapter.wireValue).append(':').append(it.selectionId).append(':').append(it.status).append(':').append(it.privateAssetCount).append(':').append(it.privateAssetByteCount)
            }
        }
        return PrivacyDeletionPreview(scope, aggregates, sha256(stable.toByteArray()), candidates)
    }

    override fun delete(request: PrivacyDeletionRequest): PrivacyDeletionResult {
        if (request.scope.requiresPhrase && request.confirmationPhrase.trim() != FULL_DELETE_PHRASE) return PrivacyDeletionResult.Rejected("需要输入完整确认语。")
        if (request.scope == PrivacyDeleteScope.TEMPORARY_FAILED_TASK_ASSETS && request.selectedTaskIds.isEmpty()) return PrivacyDeletionResult.Rejected("请先明确选择至少一项可删除任务。")
        val current = preview(request.scope, request.selectedTaskIds)
        if (current.fingerprint != request.previewFingerprint) {
            if (request.scope == PrivacyDeleteScope.TEMPORARY_FAILED_TASK_ASSETS && taskDeletionCandidates().none { it.selectionId in request.selectedTaskIds }) return PrivacyDeletionResult.Completed(emptyList())
            return PrivacyDeletionResult.Rejected("数据已变化、状态不再安全或存在正式引用，请重新预览后确认。")
        }
        if (request.scope == PrivacyDeleteScope.TEMPORARY_FAILED_TASK_ASSETS) return deleteSelectedTaskAssets(current.taskCandidates.filter { it.selectionId in request.selectedTaskIds })
        if (request.scope == PrivacyDeleteScope.ORPHANED_ATTACHMENT_FILES) return deleteOrphanedAttachmentFiles(current)
        val pending = File(filesRoot, "p5c-pending-delete/${UUID.randomUUID()}")
        var moved = emptyList<Pair<File, File>>()
        return try {
            pending.mkdirs()
            moved = stageFiles(request.scope, pending)
            database.runInTransaction { deleteRoomFacts(request.scope) }
            if (request.scope == PrivacyDeleteScope.ALL_LOCAL_BUSINESS_DATA) clearBusinessPreferences()
            val failures = deleteStaged(pending)
            if (failures == 0) PrivacyDeletionResult.Completed(current.aggregates)
            else PrivacyDeletionResult.Partial(current.aggregates, failures)
        } catch (_: Exception) {
            restoreStaged(moved)
            PrivacyDeletionResult.Partial(emptyList(), 1)
        }
    }

    /** This is intentionally user-triggered only; no Activity or process lifecycle invokes it. */
    override fun retryFailedTaskDeletion(): PrivacyDeletionResult {
        val root = safeRoot(PENDING_TASK_ROOT) ?: return PrivacyDeletionResult.Rejected("删除隔离区不可用；未执行重试。")
        if (!root.exists()) return PrivacyDeletionResult.Completed(emptyList())
        val failures = deleteStaged(root)
        return if (failures == 0) PrivacyDeletionResult.Completed(emptyList()) else PrivacyDeletionResult.Partial(emptyList(), failures)
    }

    override fun cleanupImportedZipPackages(): ImportedZipCleanupResult = importedZipCleanup.cleanup()

    override fun exportSecurityDiagnostic(destination: Uri): SecurityDiagnosticResult {
        return try {
        val inventory = inventory()
        val errorCounts = safeErrorCounts()
        val payload = diagnosticPayloadJson(inventory, errorCounts)
        val json = "{\"manifest\":{\"format\":\"${SecurityDiagnosticAllowlist.FORMAT}\",\"version\":${SecurityDiagnosticAllowlist.VERSION},\"payloadSha256\":\"${sha256(payload.toByteArray())}\"},\"diagnostic\":$payload}"
        if (!SecurityDiagnosticAllowlist.accepts(json)) return SecurityDiagnosticResult.Rejected("诊断字段命中高敏或非白名单形式，已拒绝导出。")
        val stagingRoot = File(filesRoot, "p5c-diagnostics/v1").also { it.mkdirs() }
        val name = "security-diagnostic-${System.currentTimeMillis()}.json"
        val part = File(stagingRoot, ".${name}.part")
        val final = File(stagingRoot, name)
        FileOutputStream(part).use { output -> output.write(json.toByteArray()); output.fd.sync() }
        Files.move(part.toPath(), final.toPath(), StandardCopyOption.ATOMIC_MOVE)
        val bytes = final.readBytes(); val hash = sha256(bytes)
        context.contentResolver.openFileDescriptor(destination, "w")?.use { descriptor ->
            FileOutputStream(descriptor.fileDescriptor).use { output -> output.write(bytes); output.fd.sync() }
        } ?: return SecurityDiagnosticResult.Failed("系统位置不可写入。")
        val readBack = context.contentResolver.openInputStream(destination)?.use { it.readBytes() }
            ?: return SecurityDiagnosticResult.Failed("无法回读系统位置。")
        if (sha256(readBack) != hash) return SecurityDiagnosticResult.Failed("回读校验失败；未将该文件报告为成功。")
        SecurityDiagnosticResult.Completed(SecurityDiagnosticArtifact(name, hash, bytes.size.toLong()))
        } catch (_: Exception) { SecurityDiagnosticResult.Failed("诊断写入失败；可重新选择位置后重试。") }
    }

    private fun inventoryAggregates(): List<PrivacyAggregate> = listOf(
        // These two facts deliberately use the same ALL-scope search owners as the drill-down
        // rows below.  Raw Room table counts would include records that the user cannot see in
        // 全部／正文 and made the two surfaces look contradictory.
        PrivacyAggregate("search_text", database.conversationDao().visibleTextSearchResultCount()),
        PrivacyAggregate("search_attachments", database.conversationDao().visibleConversationAttachmentSearchResultCount() + glmOcrSearchDocumentCount()),
        PrivacyAggregate("capture_drafts", count("capture_drafts"), textBytes("capture_drafts", "text")),
        PrivacyAggregate("conversations", count("conversations"), textBytes("conversations", "title")),
        PrivacyAggregate("messages", count("message_nodes"), textBytes("message_content_blocks", "textContent", "displayName", "toolSafeSummary")),
        PrivacyAggregate("conversation_drafts", count("conversation_drafts"), textBytes("conversation_drafts", "text")),
        PrivacyAggregate("invocations", count("invocation_records"), textBytes("invocation_records", "providerId", "modelId", "status", "errorCode")),
        PrivacyAggregate("projects", count("projects"), textBytes("projects", "title", "description") + textBytes("project_instruction_revisions", "content")),
        PrivacyAggregate("memory", count("memories"), textBytes("memories", "title", "body", "sourceSummary") + textBytes("memory_revisions", "title", "body", "sourceSummary")),
        PrivacyAggregate("knowledge", count("knowledge_items"), textBytes("knowledge_items", "title", "body") + textBytes("knowledge_revisions", "title", "body")),
        PrivacyAggregate("knowledge_relations", count("knowledge_relationships")), PrivacyAggregate("markdown_tasks", count("markdown_import_tasks")),
        PrivacyAggregate("json_tasks", count("json_knowledge_import_tasks")), PrivacyAggregate("pdf_tasks", count("pdf_text_import_tasks")),
        PrivacyAggregate("web_tasks", count("web_text_snapshot_tasks")),
        PrivacyAggregate("glm_ocr_tasks", count("glm_ocr_tasks")),
        PrivacyAggregate("offline_eval_runs", count("offline_eval_runs"), textBytes("offline_eval_assertions", "fact", "detail") + textBytes("offline_eval_human_scores", "note")),
        // Import facts are shown separately from the total conversation count.  They are aggregate
        // provenance/task facts only: no title, body, filename, source id, or archive size enters
        // the privacy overview.
        PrivacyAggregate("chatgpt_json_import_batches", count("chatgpt_export_import_tasks")),
        PrivacyAggregate("chatgpt_json_imported_conversations", count("chatgpt_import_provenance")),
        PrivacyAggregate("claude_json_import_batches", count("claude_export_import_tasks")),
        PrivacyAggregate("claude_json_imported_conversations", count("claude_import_provenance")),
        PrivacyAggregate("zip_import_batches", count("p6k_zip_import_tasks")),
        PrivacyAggregate("zip_imported_conversations", count("p6k_zip_import_provenance")),
        PrivacyAggregate("zip_pending_media", countWhere("p6k_zip_asset_candidates", "attachmentId IS NULL")),
        PrivacyAggregate("zip_imported_profile_fields", sum("p6k_zip_profile_candidates", "mappedFieldCount")),
        attachmentAggregate("attachment_images", "a.mimeType LIKE 'image/%'"),
        attachmentAggregate("attachment_videos", "a.mimeType LIKE 'video/%'"),
        attachmentAggregate("attachment_audio", "a.mimeType LIKE 'audio/%'"),
        attachmentAggregate("attachment_files", "a.mimeType NOT LIKE 'image/%' AND a.mimeType NOT LIKE 'video/%' AND a.mimeType NOT LIKE 'audio/%'"),
        attachmentAggregate("zip_imported_attachments", "a.attachmentId IN (SELECT DISTINCT attachmentId FROM p6k_zip_asset_occurrence_receipt)"),
        attachmentAggregate("glm_ocr_attachments", "a.attachmentId IN (SELECT sourceAttachmentId FROM glm_ocr_tasks UNION SELECT resultAttachmentId FROM glm_ocr_tasks WHERE resultAttachmentId IS NOT NULL)"),
        fileAggregate("import_source_assets", "markdown-import-assets/v1") + fileAggregate("import_source_assets", "json-knowledge-import-assets/v1") +
            fileAggregate("import_source_assets", "pdf-text-import-assets/v1") + fileAggregate("import_source_assets", "web-text-snapshots/v1") +
            fileAggregate("import_source_assets", "p6k-zip-import/v1"),
    )

    /** Mirrors [GlmOcrTaskOwner.searchDocuments] for the blank-query 全部 catalogue. */
    private fun glmOcrSearchDocumentCount(): Long = db.query(
        """
        SELECT COUNT(*) FROM (
            SELECT taskId, sourceAttachmentId AS attachmentId FROM glm_ocr_tasks
            UNION ALL
            SELECT taskId, resultAttachmentId AS attachmentId FROM glm_ocr_tasks WHERE resultAttachmentId IS NOT NULL
        ) task_assets
        JOIN private_attachment_assets assets ON assets.attachmentId = task_assets.attachmentId
        """.trimIndent(),
    ).use { cursor -> cursor.moveToFirst(); cursor.getLong(0) }

    private operator fun PrivacyAggregate.plus(other: PrivacyAggregate) = PrivacyAggregate(
        if (key == other.key) key else "$key+${other.key}",
        count + other.count,
        byteCount + other.byteCount,
    )

    private data class TaskSpec(
        val adapter: PrivacyTaskAdapter,
        val taskTable: String,
        val itemTable: String,
        val assetRoot: String,
        val storageKeyPattern: Regex,
        val extraChildTable: String? = null,
    )

    private val taskSpecs = listOf(
        TaskSpec(PrivacyTaskAdapter.MARKDOWN, "markdown_import_tasks", "markdown_import_items", "markdown-import-assets/v1", Regex("markdown-import-assets/v1/[A-Za-z0-9-]+/[A-Za-z0-9._-]+")),
        TaskSpec(PrivacyTaskAdapter.JSON, "json_knowledge_import_tasks", "json_knowledge_import_items", "json-knowledge-import-assets/v1", Regex("json-knowledge-import-assets/v1/[A-Za-z0-9-]+/[A-Za-z0-9._-]+")),
        TaskSpec(PrivacyTaskAdapter.PDF, "pdf_text_import_tasks", "pdf_text_import_items", "pdf-text-import-assets/v1", Regex("pdf-text-import-assets/v1/[A-Za-z0-9-]+/[A-Za-z0-9._-]+"), "pdf_text_import_pages"),
        TaskSpec(PrivacyTaskAdapter.WEB, "web_text_snapshot_tasks", "web_text_snapshot_items", "web-text-snapshots/v1", Regex("web-text-snapshots/v1/[A-Za-z0-9-]+/snapshot\\.html")),
    )

    /** Only terminal failed/cancelled tasks with an app-private, non-symlink asset boundary are selectable. */
    private fun taskDeletionCandidates(): List<PrivacyTaskDeletionCandidate> = taskSpecs.flatMap { spec ->
        db.query("SELECT id, status, storageKey FROM ${spec.taskTable} WHERE status IN ('FAILED','CANCELLED') ORDER BY id ASC").use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0) ?: continue
                    val status = cursor.getString(1) ?: continue
                    val storageKey = cursor.takeIf { !it.isNull(2) }?.getString(2)
                    val metrics = taskAssetMetrics(spec, id, storageKey)
                    if (!PrivacyTaskDeletionPolicy.canSelect(status, hasActiveKnowledgeReference(spec, id), hasActiveExportReference(storageKey), metrics != null)) continue
                    requireNotNull(metrics)
                    add(PrivacyTaskDeletionCandidate(id, spec.adapter, safeTaskSummary(id), status, metrics.first, metrics.second, status == "FAILED"))
                }
            }
        }
    }.sortedWith(compareBy<PrivacyTaskDeletionCandidate> { it.adapter.wireValue }.thenBy { it.selectionId })

    private fun taskAssetMetrics(spec: TaskSpec, taskId: String, storageKey: String?): Pair<Long, Long>? {
        if (!taskId.matches(TASK_ID_PATTERN)) return null
        if (storageKey != null && (!storageKey.matches(spec.storageKeyPattern) || !storageKey.startsWith("${spec.assetRoot}/$taskId/"))) return null
        val root = safeTaskRoot(spec, taskId) ?: return null
        if (!root.exists()) return 0L to 0L
        if (Files.isSymbolicLink(root.toPath()) || root.walkTopDown().any { Files.isSymbolicLink(it.toPath()) }) return null
        val files = root.walkTopDown().filter { it.isFile }.toList()
        return files.size.toLong() to files.sumOf { it.length() }
    }

    private fun hasActiveKnowledgeReference(spec: TaskSpec, taskId: String): Boolean = db.query(
        "SELECT 1 FROM ${spec.itemTable} i INNER JOIN knowledge_items k ON i.knowledgeId=k.id WHERE i.taskId=? AND k.status='ACTIVE' LIMIT 1",
        arrayOf(taskId),
    ).use { it.moveToFirst() }

    /** Task assets never share the export namespace. Any unrecognised key is conservatively treated as an active export reference. */
    private fun hasActiveExportReference(storageKey: String?) = storageKey?.startsWith("exports/") == true

    private fun deleteSelectedTaskAssets(candidates: List<PrivacyTaskDeletionCandidate>): PrivacyDeletionResult {
        if (candidates.isEmpty()) return PrivacyDeletionResult.Rejected("所选任务已不存在或不再满足安全删除条件。")
        val pending = safeRoot("$PENDING_TASK_ROOT/${UUID.randomUUID()}") ?: return PrivacyDeletionResult.Rejected("删除隔离区不可用；未执行删除。")
        val moved = mutableListOf<Pair<File, File>>()
        return try {
            if (!pending.mkdirs()) throw IllegalStateException("cannot create quarantine")
            candidates.sortedWith(compareBy<PrivacyTaskDeletionCandidate> { it.adapter.wireValue }.thenBy { it.selectionId }).forEach { candidate ->
                val spec = taskSpecs.first { it.adapter == candidate.adapter }
                // Re-check immediately before moving. Any status/reference/storage change cancels the whole request.
                if (taskDeletionCandidates().none { it.adapter == candidate.adapter && it.selectionId == candidate.selectionId }) throw IllegalStateException("stale task")
                val source = safeTaskRoot(spec, candidate.selectionId) ?: throw IllegalStateException("unsafe root")
                if (source.exists()) {
                    if (Files.isSymbolicLink(source.toPath()) || source.walkTopDown().any { Files.isSymbolicLink(it.toPath()) }) throw IllegalStateException("symlink")
                    val target = File(pending, "${spec.adapter.wireValue}/${candidate.selectionId}").canonicalFile
                    if (!target.path.startsWith(pending.path + File.separator)) throw IllegalStateException("quarantine escape")
                    target.parentFile?.mkdirs()
                    Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
                    moved += source to target
                }
            }
            database.runInTransaction {
                candidates.forEach { candidate ->
                    val spec = taskSpecs.first { it.adapter == candidate.adapter }
                    if (taskDeletionCandidates().none { it.adapter == candidate.adapter && it.selectionId == candidate.selectionId }) throw IllegalStateException("stale task before Room delete")
                    db.execSQL("DELETE FROM ${spec.itemTable} WHERE taskId=?", arrayOf(candidate.selectionId))
                    spec.extraChildTable?.let { db.execSQL("DELETE FROM $it WHERE taskId=?", arrayOf(candidate.selectionId)) }
                    db.execSQL("DELETE FROM ${spec.taskTable} WHERE id=?", arrayOf(candidate.selectionId))
                }
            }
            val failures = deleteStaged(pending)
            val deleted = candidates.groupBy { it.adapter }.map { (adapter, rows) -> PrivacyAggregate("selected_${adapter.wireValue}_tasks", rows.size.toLong(), rows.sumOf { it.privateAssetByteCount }) }
            if (failures == 0) PrivacyDeletionResult.Completed(deleted) else PrivacyDeletionResult.Partial(deleted, failures)
        } catch (_: Exception) {
            restoreStaged(moved)
            PrivacyDeletionResult.Partial(emptyList(), 1)
        }
    }

    private fun safeTaskRoot(spec: TaskSpec, taskId: String): File? =
        taskId.takeIf { it.matches(TASK_ID_PATTERN) }?.let { safeRoot("${spec.assetRoot}/$it") }
    private fun safeTaskSummary(taskId: String) = sha256(taskId.toByteArray()).take(12)
    private fun failedTaskAggregates() = listOf("markdown_import_tasks", "json_knowledge_import_tasks", "pdf_text_import_tasks", "web_text_snapshot_tasks").map { PrivacyAggregate("failed_$it", countWhere(it, "status IN ('FAILED','CANCELLED')")) }
    private fun count(table: String) = db.query("SELECT COUNT(*) FROM $table").use { cursor -> cursor.moveToFirst(); cursor.getLong(0) }
    private fun countWhere(table: String, condition: String) = db.query("SELECT COUNT(*) FROM $table WHERE $condition").use { cursor -> cursor.moveToFirst(); cursor.getLong(0) }
    private fun sum(table: String, column: String) = db.query("SELECT COALESCE(SUM($column), 0) FROM $table").use { cursor -> cursor.moveToFirst(); cursor.getLong(0) }
    private fun textBytes(table: String, vararg columns: String): Long {
        if (columns.isEmpty()) return 0
        val expression = columns.joinToString(" + ") { "LENGTH(CAST(COALESCE($it, '') AS BLOB))" }
        return db.query("SELECT COALESCE(SUM($expression), 0) FROM $table").use { cursor -> cursor.moveToFirst(); cursor.getLong(0) }
    }
    /** The privacy page reports bytes that are physically present now, never stale catalog
     * byteCount metadata. Archive-backed entries remain searchable while their package exists,
     * but the package bytes are counted once under import_source_assets instead of once per ZIP
     * entry (which previously inflated usage to the logical uncompressed total). */
    private fun attachmentAggregate(key: String, condition: String): PrivacyAggregate = db.query(
        "SELECT a.storageKey FROM private_attachment_assets a WHERE ($condition) AND ($ACTIVE_ATTACHMENT_REFERENCE_SQL) ORDER BY a.storageKey ASC",
    ).use { cursor ->
        var count = 0L
        var bytes = 0L
        while (cursor.moveToNext()) {
            when (val storage = actualAttachmentStorage(cursor.getString(0))) {
                null -> Unit
                is ActualAttachmentStorage.Managed -> {
                    count += 1L
                    bytes += storage.byteCount
                }
                ActualAttachmentStorage.ArchiveBacked -> count += 1L
            }
        }
        PrivacyAggregate(key, count, bytes)
    }

    /** Files left behind by an interrupted or older delete stay visible as real occupied bytes,
     * but no longer inflate the image/video/audio rows that drill into live search results. */
    private fun orphanedAttachmentAggregate(): PrivacyAggregate {
        val candidates = orphanedManagedAttachments()
        return PrivacyAggregate(
            key = "orphaned_attachment_files",
            count = candidates.size.toLong(),
            byteCount = candidates.sumOf { it.second },
        )
    }

    private fun orphanedManagedAttachments(): List<Pair<String, Long>> = db.query(
        "SELECT a.attachmentId,a.storageKey FROM private_attachment_assets a " +
            "WHERE a.storageKey LIKE 'attachments/v1/%' AND NOT ($ACTIVE_ATTACHMENT_REFERENCE_SQL) ORDER BY a.attachmentId ASC",
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                val id = cursor.getString(0)
                val storage = actualAttachmentStorage(cursor.getString(1)) as? ActualAttachmentStorage.Managed ?: continue
                add(id to storage.byteCount)
            }
        }
    }

    /** These files have no current owner by definition.  Clean them before exposing storage
     * facts, and retry on a later refresh if an I/O failure leaves one behind. */
    private fun cleanupOrphanedAttachmentFilesSilently() {
        val aggregate = orphanedAttachmentAggregate()
        if (aggregate.count == 0L) return
        runCatching {
            deleteOrphanedAttachmentFiles(
                PrivacyDeletionPreview(
                    scope = PrivacyDeleteScope.ORPHANED_ATTACHMENT_FILES,
                    aggregates = listOf(aggregate),
                    fingerprint = "automatic-orphan-cleanup",
                ),
            )
        }
    }

    private fun cacheInventory(inventory: PrivacyInventory) {
        val zip = inventory.importedZipCleanup
        inventoryCache.edit()
            .putString("aggregates", inventory.aggregates.joinToString("|") { "${it.key}:${it.count}:${it.byteCount}" })
            .putBoolean("credentialReferencePresent", inventory.credentialReferencePresent)
            .putBoolean("internetPermissionPresent", inventory.internetPermissionPresent)
            .putLong("zipOriginalPackageCount", zip.originalPackageCount)
            .putLong("zipImportedAttachmentCount", zip.importedAttachmentCount)
            .putLong("zipImportedAttachmentByteCount", zip.importedAttachmentByteCount)
            .putLong("zipSourceDependentAttachmentCount", zip.sourceDependentAttachmentCount)
            .putLong("zipBlockedPackageCount", zip.blockedPackageCount)
            .putLong("zipPendingDeletionCount", zip.pendingDeletionCount)
            // inventory() already runs on Dispatchers.IO. Persist before it returns so a process
            // stop immediately after leaving this screen cannot reopen without its last snapshot.
            .commit()
    }

    private fun deleteOrphanedAttachmentFiles(preview: PrivacyDeletionPreview): PrivacyDeletionResult {
        val expected = preview.aggregates.singleOrNull { it.key == "orphaned_attachment_files" }
            ?: return PrivacyDeletionResult.Rejected("待清理文件状态已变化，请重新预览。")
        val candidates = orphanedManagedAttachments()
        if (candidates.size.toLong() != expected.count || candidates.sumOf { it.second } != expected.byteCount) {
            return PrivacyDeletionResult.Rejected("待清理文件状态已变化，请重新预览。")
        }
        val assets = RoomPrivateAttachmentRepository(database)
        val store = AndroidPrivateAttachmentStore(context)
        var deletedCount = 0L
        var deletedBytes = 0L
        var failures = 0
        candidates.forEach { (attachmentId, byteCount) ->
            try {
                database.runInTransaction {
                    // ZIP rows are derivative ownership receipts. With no live owner they must not
                    // keep a physically orphaned private copy alive forever.
                    db.execSQL("DELETE FROM p6k_zip_asset_occurrence_receipt WHERE attachmentId=?", arrayOf(attachmentId))
                    db.execSQL("DELETE FROM p6k_zip_asset_link_receipts WHERE attachmentId=?", arrayOf(attachmentId))
                    db.execSQL("DELETE FROM p6k_zip_asset_link_provenance WHERE attachmentId=?", arrayOf(attachmentId))
                    db.execSQL("DELETE FROM p6k_zip_asset_catalog WHERE attachmentId=?", arrayOf(attachmentId))
                    db.execSQL("DELETE FROM p6k_zip_asset_candidates WHERE attachmentId=?", arrayOf(attachmentId))
                }
                when (assets.deleteIfUnreferenced(AttachmentId(attachmentId), store::deletePrivateCopy)) {
                    PrivateAttachmentCleanupResult.Deleted, PrivateAttachmentCleanupResult.Missing -> {
                        deletedCount++
                        deletedBytes += byteCount
                    }
                    is PrivateAttachmentCleanupResult.Retained, PrivateAttachmentCleanupResult.DeleteFailed -> failures++
                }
            } catch (_: Exception) {
                failures++
            }
        }
        val deleted = listOf(PrivacyAggregate("orphaned_attachment_files", deletedCount, deletedBytes))
        return if (failures == 0) PrivacyDeletionResult.Completed(deleted) else PrivacyDeletionResult.Partial(deleted, failures)
    }

    private sealed interface ActualAttachmentStorage {
        data class Managed(val byteCount: Long) : ActualAttachmentStorage
        data object ArchiveBacked : ActualAttachmentStorage
    }

    private fun actualAttachmentStorage(storageKey: String): ActualAttachmentStorage? {
        if (storageKey.startsWith("attachments/v1/")) {
            val root = safeRoot("attachments/v1") ?: return null
            val file = File(filesRoot, storageKey).canonicalFile
            if (!file.path.startsWith(root.path + File.separator) || !file.isFile || Files.isSymbolicLink(file.toPath())) return null
            return ActualAttachmentStorage.Managed(file.length())
        }
        val archive = P6KZipArchiveAssetStorage.parse(storageKey) ?: return null
        val file = safeRoot("p6k-zip-import/v1/archives/${archive.taskId}.zip") ?: return null
        return if (file.isFile && !Files.isSymbolicLink(file.toPath())) ActualAttachmentStorage.ArchiveBacked else null
    }
    private fun fileAggregate(key: String, relative: String): PrivacyAggregate {
        val root = safeRoot(relative) ?: return PrivacyAggregate(key, 0, 0)
        val files = root.walkTopDown().filter { it.isFile && !Files.isSymbolicLink(it.toPath()) }.toList()
        return PrivacyAggregate(key, files.size.toLong(), files.sumOf { it.length() })
    }

    private fun stageFiles(scope: PrivacyDeleteScope, pending: File): List<Pair<File, File>> {
        val roots = when (scope) {
            PrivacyDeleteScope.TEMPORARY_FAILED_TASK_ASSETS -> failedAssetRoots()
            PrivacyDeleteScope.ORPHANED_ATTACHMENT_FILES -> emptyList()
            PrivacyDeleteScope.OFFLINE_EVAL_RUNS -> listOf("exports/offline-eval/v1")
            PrivacyDeleteScope.KNOWLEDGE_MEMORY_TRASH -> emptyList()
            PrivacyDeleteScope.ALL_LOCAL_BUSINESS_DATA -> knownRoots()
        }
        return roots.distinct().mapNotNull { relative ->
            val source = safeRoot(relative) ?: return@mapNotNull null
            if (!source.exists() || Files.isSymbolicLink(source.toPath())) return@mapNotNull null
            val target = File(pending, relative.replace('/', '_'))
            target.parentFile?.mkdirs(); Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
            source to target
        }
    }
    private fun failedAssetRoots(): List<String> = listOf(
        "markdown_import_tasks" to "markdown-import-assets/v1", "json_knowledge_import_tasks" to "json-knowledge-import-assets/v1",
        "pdf_text_import_tasks" to "pdf-text-import-assets/v1", "web_text_snapshot_tasks" to "web-text-snapshots/v1",
    ).flatMap { (table, root) -> db.query("SELECT id FROM $table WHERE status IN ('FAILED','CANCELLED')").use { cursor -> buildList { while (cursor.moveToNext()) add("$root/${cursor.getString(0)}") } } }
    private fun deleteRoomFacts(scope: PrivacyDeleteScope) {
        when (scope) {
            PrivacyDeleteScope.TEMPORARY_FAILED_TASK_ASSETS -> listOf("markdown_import", "json_knowledge_import", "pdf_text_import", "web_text_snapshot").forEach { prefix -> db.execSQL("DELETE FROM ${prefix}_items WHERE taskId IN (SELECT id FROM ${prefix}_tasks WHERE status IN ('FAILED','CANCELLED'))"); if (prefix == "pdf_text_import") db.execSQL("DELETE FROM pdf_text_import_pages WHERE taskId IN (SELECT id FROM pdf_text_import_tasks WHERE status IN ('FAILED','CANCELLED'))"); db.execSQL("DELETE FROM ${prefix}_tasks WHERE status IN ('FAILED','CANCELLED')") }
            PrivacyDeleteScope.ORPHANED_ATTACHMENT_FILES -> Unit
            PrivacyDeleteScope.OFFLINE_EVAL_RUNS -> listOf("offline_eval_human_scores", "offline_eval_assertions", "offline_eval_case_results", "offline_eval_runs").forEach { db.execSQL("DELETE FROM $it") }
            PrivacyDeleteScope.KNOWLEDGE_MEMORY_TRASH -> { db.execSQL("DELETE FROM knowledge_revisions WHERE knowledgeId IN (SELECT id FROM knowledge_items WHERE status='DELETED')"); db.execSQL("DELETE FROM knowledge_items WHERE status='DELETED'"); db.execSQL("DELETE FROM memory_revisions WHERE memoryId IN (SELECT id FROM memories WHERE status='DELETED')"); db.execSQL("DELETE FROM memory_intents WHERE memoryId IN (SELECT id FROM memories WHERE status='DELETED')"); db.execSQL("DELETE FROM memories WHERE status='DELETED'") }
            PrivacyDeleteScope.ALL_LOCAL_BUSINESS_DATA -> database.clearAllTables()
        }
    }
    private fun clearBusinessPreferences() {
        LOCAL_BUSINESS_PREFERENCE_FILES.forEach { file ->
            check(context.getSharedPreferences(file, Context.MODE_PRIVATE).edit().clear().commit()) {
                "LOCAL_BUSINESS_PREFERENCE_CLEAR_FAILED:$file"
            }
        }
    }
    private fun deleteStaged(pending: File): Int = pending.walkBottomUp().count { file -> file.exists() && !fileDeleter(file) }
    private fun restoreStaged(moved: List<Pair<File, File>>) = moved.asReversed().forEach { (source, staged) -> if (staged.exists() && !source.exists()) runCatching { source.parentFile?.mkdirs(); Files.move(staged.toPath(), source.toPath(), StandardCopyOption.ATOMIC_MOVE) } }
    private fun safeRoot(relative: String): File? = File(filesRoot, relative).canonicalFile.takeIf { it.path.startsWith(filesRoot.path + File.separator) }
    private fun knownRoots() = listOf(
        "attachments/v1", "markdown-import-assets/v1", "json-knowledge-import-assets/v1", "pdf-text-import-assets/v1", "web-text-snapshots/v1", "p6k-zip-import/v1",
        "exports", "model-registry", "p2m-real-service-tokens", "p2m-real-service-evidence",
        // Bounded diagnostics/metadata are local business data too.  They contain no bodies or
        // credentials, but a full local-data clear must not leave their titles or model choices.
        "context-selection-audit-v1.json", "model-profile-directory-v1.json",
    )
    private fun safeErrorCounts(): Map<String, Long> = listOf("markdown_import_tasks", "json_knowledge_import_tasks", "pdf_text_import_tasks", "web_text_snapshot_tasks").flatMap { table -> db.query("SELECT failure, COUNT(*) FROM $table WHERE failure IS NOT NULL GROUP BY failure").use { cursor -> buildList { while (cursor.moveToNext()) SecurityDiagnosticAllowlist.errorCode(cursor.getString(0))?.let { add(it to cursor.getLong(1)) } } } }.groupingBy { it.first }.fold(0L) { total, item -> total + item.second }
    private fun diagnosticPayloadJson(inventory: PrivacyInventory, errors: Map<String, Long>): String = buildString {
        append("{\"app\":{\"version\":\"").append(appVersion).append("\",\"schema\":17,\"egress\":\"DISABLED\"}")
        append(",\"device\":{\"api\":").append(Build.VERSION.SDK_INT).append(",\"screenWidthDp\":").append(context.resources.configuration.screenWidthDp).append(",\"fontScaleMilli\":").append((context.resources.configuration.fontScale * 1000).toInt()).append("}")
        append(",\"capabilities\":{\"internetPermission\":").append(inventory.internetPermissionPresent).append(",\"webAdapterUserConfirmation\":true,\"providerEgress\":false}")
        append(",\"counts\":{"); inventory.aggregates.sortedBy { it.key }.joinTo(this, ",") { "\"${it.key}\":${it.count}" }; append('}')
        append(",\"errors\":{"); errors.toSortedMap().entries.joinTo(this, ",") { "\"${it.key}\":${it.value}" }; append("}}")
    }
    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private companion object {
        const val FULL_DELETE_PHRASE = "删除全部本地业务数据"
        const val PENDING_TASK_ROOT = "p5c-pending-delete/tasks"
        val TASK_ID_PATTERN = Regex("[A-Za-z0-9-]+")
        val ACTIVE_ATTACHMENT_REFERENCE_SQL = """
            EXISTS(SELECT 1 FROM conversation_draft_attachments r WHERE r.attachmentId=a.attachmentId)
            OR EXISTS(SELECT 1 FROM capture_draft_attachments r WHERE r.attachmentId=a.attachmentId)
            OR EXISTS(SELECT 1 FROM knowledge_attachments r WHERE r.attachmentId=a.attachmentId)
            OR EXISTS(SELECT 1 FROM message_content_blocks r WHERE r.attachmentId=a.attachmentId)
            OR EXISTS(SELECT 1 FROM p6k_zip_asset_occurrence_receipt r INNER JOIN p6k_zip_import_tasks t ON t.id=r.taskId WHERE r.attachmentId=a.attachmentId AND t.status NOT IN ('COMPLETED','FAILED','CANCELLED'))
            OR EXISTS(SELECT 1 FROM p6k_zip_asset_link_provenance r INNER JOIN p6k_zip_import_tasks t ON t.id=r.taskId WHERE r.attachmentId=a.attachmentId AND t.status NOT IN ('COMPLETED','FAILED','CANCELLED'))
            OR EXISTS(SELECT 1 FROM temporary_conversation_attachments r WHERE r.attachmentId=a.attachmentId)
            OR EXISTS(SELECT 1 FROM resumable_attachment_uploads r WHERE r.attachmentId=a.attachmentId AND r.status IN ('PENDING','UPLOADING','FAILED','UNKNOWN'))
            OR EXISTS(SELECT 1 FROM glm_ocr_tasks r WHERE r.sourceAttachmentId=a.attachmentId OR r.resultAttachmentId=a.attachmentId)
        """.trimIndent()
    }
}

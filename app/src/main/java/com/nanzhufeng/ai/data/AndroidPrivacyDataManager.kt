package com.nanzhufeng.ai.data

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import com.nanzhufeng.ai.data.local.NanfengAiDatabase
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

/** P5-C Android owner. It only queries aggregate SQL and never selects a user-content column. */
class AndroidPrivacyDataManager(
    private val context: Context,
    private val database: NanfengAiDatabase,
    private val appVersion: String,
    /** Test seam for a deterministic app-private quarantine cleanup failure; production uses File.delete. */
    private val fileDeleter: (File) -> Boolean = { it.delete() },
) : PrivacyDataManager {
    private val filesRoot = context.applicationContext.filesDir.canonicalFile
    private val db get() = database.openHelper.writableDatabase

    override fun inventory(): PrivacyInventory = PrivacyInventory(
        aggregates = inventoryAggregates(),
        credentialReferencePresent = context.getSharedPreferences("provider_credentials_v1", Context.MODE_PRIVATE).contains("OPENROUTER"),
        internetPermissionPresent = context.packageManager.checkPermission(android.Manifest.permission.INTERNET, context.packageName) == PackageManager.PERMISSION_GRANTED,
    )

    override fun preview(scope: PrivacyDeleteScope, selectedTaskIds: Set<String>): PrivacyDeletionPreview {
        val candidates = if (scope == PrivacyDeleteScope.TEMPORARY_FAILED_TASK_ASSETS) taskDeletionCandidates() else emptyList()
        val selected = candidates.filter { it.selectionId in selectedTaskIds }
        val aggregates = when (scope) {
            PrivacyDeleteScope.TEMPORARY_FAILED_TASK_ASSETS -> selected.groupBy { it.adapter }.map { (adapter, rows) ->
                PrivacyAggregate("selected_${adapter.wireValue}_tasks", rows.size.toLong(), rows.sumOf { it.privateAssetByteCount })
            }
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
        PrivacyAggregate("capture_drafts", count("capture_drafts")), PrivacyAggregate("conversations", count("conversations")),
        PrivacyAggregate("messages", count("message_nodes")), PrivacyAggregate("conversation_drafts", count("conversation_drafts")),
        PrivacyAggregate("invocations", count("invocation_records")), PrivacyAggregate("projects", count("projects")),
        PrivacyAggregate("memory", count("memories")), PrivacyAggregate("knowledge", count("knowledge_items")),
        PrivacyAggregate("knowledge_relations", count("knowledge_relationships")), PrivacyAggregate("markdown_tasks", count("markdown_import_tasks")),
        PrivacyAggregate("json_tasks", count("json_knowledge_import_tasks")), PrivacyAggregate("pdf_tasks", count("pdf_text_import_tasks")),
        PrivacyAggregate("web_tasks", count("web_text_snapshot_tasks")), PrivacyAggregate("offline_eval_runs", count("offline_eval_runs")),
        fileAggregate("private_assets", "attachments/v1") + fileAggregate("markdown_assets", "markdown-import-assets/v1") +
            fileAggregate("json_assets", "json-knowledge-import-assets/v1") + fileAggregate("pdf_assets", "pdf-text-import-assets/v1") + fileAggregate("web_assets", "web-text-snapshots/v1"),
    )

    private operator fun PrivacyAggregate.plus(other: PrivacyAggregate) = PrivacyAggregate("$key+${other.key}", count + other.count, byteCount + other.byteCount)

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
    private fun fileAggregate(key: String, relative: String): PrivacyAggregate {
        val root = safeRoot(relative) ?: return PrivacyAggregate(key, 0, 0)
        val files = root.walkTopDown().filter { it.isFile && !Files.isSymbolicLink(it.toPath()) }.toList()
        return PrivacyAggregate(key, files.size.toLong(), files.sumOf { it.length() })
    }

    private fun stageFiles(scope: PrivacyDeleteScope, pending: File): List<Pair<File, File>> {
        val roots = when (scope) {
            PrivacyDeleteScope.TEMPORARY_FAILED_TASK_ASSETS -> failedAssetRoots()
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
            PrivacyDeleteScope.OFFLINE_EVAL_RUNS -> listOf("offline_eval_human_scores", "offline_eval_assertions", "offline_eval_case_results", "offline_eval_runs").forEach { db.execSQL("DELETE FROM $it") }
            PrivacyDeleteScope.KNOWLEDGE_MEMORY_TRASH -> { db.execSQL("DELETE FROM knowledge_revisions WHERE knowledgeId IN (SELECT id FROM knowledge_items WHERE status='DELETED')"); db.execSQL("DELETE FROM knowledge_items WHERE status='DELETED'"); db.execSQL("DELETE FROM memory_revisions WHERE memoryId IN (SELECT id FROM memories WHERE status='DELETED')"); db.execSQL("DELETE FROM memory_intents WHERE memoryId IN (SELECT id FROM memories WHERE status='DELETED')"); db.execSQL("DELETE FROM memories WHERE status='DELETED'") }
            PrivacyDeleteScope.ALL_LOCAL_BUSINESS_DATA -> database.clearAllTables()
        }
    }
    private fun clearBusinessPreferences() {
        listOf(
            "model_service_settings_v1", "provider_credentials_v1", "p5a_ui",
            "direct_chat_call_audit_v1", "model-health-v1",
        ).forEach { context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit() }
    }
    private fun deleteStaged(pending: File): Int = pending.walkBottomUp().count { file -> file.exists() && !fileDeleter(file) }
    private fun restoreStaged(moved: List<Pair<File, File>>) = moved.asReversed().forEach { (source, staged) -> if (staged.exists() && !source.exists()) runCatching { source.parentFile?.mkdirs(); Files.move(staged.toPath(), source.toPath(), StandardCopyOption.ATOMIC_MOVE) } }
    private fun safeRoot(relative: String): File? = File(filesRoot, relative).canonicalFile.takeIf { it.path.startsWith(filesRoot.path + File.separator) }
    private fun knownRoots() = listOf(
        "attachments/v1", "markdown-import-assets/v1", "json-knowledge-import-assets/v1", "pdf-text-import-assets/v1", "web-text-snapshots/v1",
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
    }
}

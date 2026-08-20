package com.nanzhufeng.ai.domain

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Instant
import java.util.UUID

/** P4-H is deliberately the only file Adapter. It parses Markdown as inert text, never instructions. */
const val MARKDOWN_ADAPTER_VERSION = 1
const val MARKDOWN_TASK_MAX_BYTES = 512 * 1024L
const val MARKDOWN_ITEM_MAX_COUNT = 100
const val MARKDOWN_ITEM_MAX_BODY_CODEPOINTS = 12_000
const val MARKDOWN_MULTI_ITEM_SEPARATOR = "<!-- nanfeng-ai:knowledge -->"

@JvmInline value class ImportTaskId(val value: String) { companion object { fun new() = ImportTaskId(UUID.randomUUID().toString()) } }
@JvmInline value class ImportItemId(val value: String) { companion object { fun new() = ImportItemId(UUID.randomUUID().toString()) } }

enum class MarkdownImportTaskStatus { SELECTED, PRIVATE_COPIED, PARSING, AWAITING_CONFIRMATION, PARTIALLY_COMPLETED, COMPLETED, FAILED, CANCELLED }
enum class MarkdownImportItemStatus { PENDING_CONFIRMATION, CONFIRMED, SKIPPED, FAILED }
enum class MarkdownImportFailure { NOT_MARKDOWN, TOO_LARGE, INVALID_UTF8, EMPTY_DOCUMENT, MALFORMED_DOCUMENT, TOO_MANY_ITEMS, ITEM_TOO_LARGE, HIGH_SENSITIVITY, PRIVATE_COPY_FAILED, MISSING_PRIVATE_ASSET, INTERRUPTED, KNOWLEDGE_REJECTED }

data class MarkdownImportAsset(
    val storageKey: String,
    val mimeType: String,
    val displayName: String,
    val byteCount: Long,
    val sha256: String,
    val adapterVersion: Int = MARKDOWN_ADAPTER_VERSION,
)

data class MarkdownImportItem(
    val id: ImportItemId,
    val taskId: ImportTaskId,
    val ordinal: Int,
    val title: String,
    val body: String,
    val tags: Set<String>,
    val status: MarkdownImportItemStatus = MarkdownImportItemStatus.PENDING_CONFIRMATION,
    val failure: MarkdownImportFailure? = null,
    val knowledgeId: KnowledgeItemId? = null,
)

data class MarkdownImportTask(
    val id: ImportTaskId,
    val status: MarkdownImportTaskStatus,
    val asset: MarkdownImportAsset?,
    val failure: MarkdownImportFailure? = null,
    val retryCount: Int = 0,
    val createdAt: Instant,
    val updatedAt: Instant,
    val items: List<MarkdownImportItem> = emptyList(),
) {
    val completedCount get() = items.count { it.status == MarkdownImportItemStatus.CONFIRMED }
    val failedCount get() = items.count { it.status == MarkdownImportItemStatus.FAILED }
}

data class MarkdownPrivateCopyRequest(val taskId: ImportTaskId, val displayName: String, val mimeType: String, val bytes: ByteArray)
sealed interface MarkdownPrivateCopyResult { data class Copied(val asset: MarkdownImportAsset) : MarkdownPrivateCopyResult; data class Failed(val reason: MarkdownImportFailure) : MarkdownPrivateCopyResult }
interface MarkdownPrivateAssetStore { fun copy(request: MarkdownPrivateCopyRequest): MarkdownPrivateCopyResult; fun read(storageKey: String): ByteArray? }
interface MarkdownImportTaskRepository {
    fun save(task: MarkdownImportTask): MarkdownImportTask
    fun find(id: ImportTaskId): MarkdownImportTask?
    fun list(): List<MarkdownImportTask>
}

data class MarkdownExportManifest(val format: String = "nanfeng-ai.markdown-export", val version: Int = 1, val exportedAt: Instant, val itemCount: Int, val fidelity: String = "title, body, tags, scope; excludes URIs, paths, keys, prompts, provider payloads, attachment bytes, relationships and deleted items")
data class MarkdownExportResult(val fileName: String, val byteCount: Long, val sha256: String, val manifest: MarkdownExportManifest)
interface MarkdownKnowledgeExportStore { fun write(markdown: String, manifest: MarkdownExportManifest): MarkdownExportResult? }

/** Exports only explicit ACTIVE formal Knowledge through the same inert Markdown grammar. */
class ExportMarkdownKnowledgeUseCase(private val repository: KnowledgeManagementRepository, private val store: MarkdownKnowledgeExportStore, private val clock: Clock) {
    fun execute(ids: Set<KnowledgeItemId>): MarkdownExportResult? {
        if (ids.isEmpty()) return null
        val selected = repository.listSnapshots().filter { it.item.id in ids && it.lifecycle.status == KnowledgeStatus.ACTIVE }
        if (selected.size != ids.size) return null
        val document = selected.sortedBy { it.item.id.value }.joinToString("\n\n$MARKDOWN_MULTI_ITEM_SEPARATOR\n\n") { snapshot ->
            buildString { append("---\n"); append("tags: ").append(snapshot.lifecycle.tags.joinToString(", ")).append("\n"); append("scope: ").append(snapshot.lifecycle.scope.name).append("\n---\n# ").append(snapshot.item.title).append("\n\n").append(snapshot.item.body).append('\n') }
        }
        return store.write(document, MarkdownExportManifest(exportedAt = clock.instant(), itemCount = selected.size))
    }
}

sealed interface MarkdownParseResult { data class Parsed(val items: List<MarkdownParsedItem>) : MarkdownParseResult; data class Rejected(val reason: MarkdownImportFailure) : MarkdownParseResult }
data class MarkdownParsedItem(val title: String, val body: String, val tags: Set<String>)

class MarkdownKnowledgeAdapter(private val knowledgeDomain: KnowledgeDomain) {
    fun parse(displayName: String, mimeType: String, bytes: ByteArray): MarkdownParseResult {
        if (!isMarkdown(displayName, mimeType)) return MarkdownParseResult.Rejected(MarkdownImportFailure.NOT_MARKDOWN)
        if (bytes.size.toLong() > MARKDOWN_TASK_MAX_BYTES) return MarkdownParseResult.Rejected(MarkdownImportFailure.TOO_LARGE)
        val raw = runCatching { StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString() }.getOrElse { return MarkdownParseResult.Rejected(MarkdownImportFailure.INVALID_UTF8) }
        val normalized = raw.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n').trim()
        if (normalized.isBlank()) return MarkdownParseResult.Rejected(MarkdownImportFailure.EMPTY_DOCUMENT)
        val segments = splitOutsideCodeFence(normalized)
        if (segments.size > MARKDOWN_ITEM_MAX_COUNT) return MarkdownParseResult.Rejected(MarkdownImportFailure.TOO_MANY_ITEMS)
        val parsed = segments.mapIndexed { index, segment -> parseItem(segment.trim(), index + 1) }
        return parsed.filterIsInstance<MarkdownParseResult.Rejected>().firstOrNull()
            ?: MarkdownParseResult.Parsed(parsed.filterIsInstance<MarkdownParseResult.Parsed>().flatMap { it.items })
    }

    private fun parseItem(segment: String, ordinal: Int): MarkdownParseResult {
        if (segment.isBlank()) return MarkdownParseResult.Rejected(MarkdownImportFailure.MALFORMED_DOCUMENT)
        var lines = segment.lines()
        val tags = linkedSetOf<String>()
        if (lines.firstOrNull()?.trim() == "---") {
            val end = lines.drop(1).indexOfFirst { it.trim() == "---" }
            if (end < 0) return MarkdownParseResult.Rejected(MarkdownImportFailure.MALFORMED_DOCUMENT)
            lines.subList(1, end + 1).firstOrNull { it.startsWith("tags:", ignoreCase = true) }?.substringAfter(':')?.split(',')?.map(String::trim)?.filter(String::isNotEmpty)?.let(tags::addAll)
            lines = lines.drop(end + 2)
        }
        val heading = lines.indexOfFirst { it.matches(Regex("^#{1,6}\\s+.+$")) }
        val title = if (heading >= 0) lines[heading].replaceFirst(Regex("^#{1,6}\\s+"), "").trim() else "导入 Markdown $ordinal"
        val body = lines.filterIndexed { line, _ -> line != heading }.joinToString("\n").trim()
        if (body.isBlank()) return MarkdownParseResult.Rejected(MarkdownImportFailure.MALFORMED_DOCUMENT)
        return runCatching {
            knowledgeDomain.normalizedTitle(title); knowledgeDomain.normalizedBody(body); knowledgeDomain.normalizedTags(tags)
            MarkdownParseResult.Parsed(listOf(MarkdownParsedItem(title, body, knowledgeDomain.normalizedTags(tags))))
        }.getOrElse { MarkdownParseResult.Rejected(if (MemoryDomain.sensitiveRejection("$title\n$body") != null) MarkdownImportFailure.HIGH_SENSITIVITY else MarkdownImportFailure.ITEM_TOO_LARGE) }
    }

    private fun isMarkdown(displayName: String, mimeType: String): Boolean =
        displayName.lowercase().matches(Regex(".+\\.(md|markdown)( \\(\\d+\\))?")) && mimeType.lowercase() in setOf("text/markdown", "text/plain", "application/octet-stream")

    /** A separator-shaped line inside ``` or ~~~ stays literal Markdown body, never a record boundary. */
    private fun splitOutsideCodeFence(text: String): List<String> {
        val results = mutableListOf<String>(); val current = StringBuilder(); var fence: String? = null
        text.lines().forEach { line ->
            val trimmed = line.trimStart(); val marker = when { trimmed.startsWith("```") -> "```"; trimmed.startsWith("~~~") -> "~~~"; else -> null }
            if (marker != null) fence = if (fence == marker) null else fence ?: marker
            if (fence == null && line.trim() == MARKDOWN_MULTI_ITEM_SEPARATOR) { results += current.toString(); current.clear() } else current.append(line).append('\n')
        }
        results += current.toString(); return results
    }
}

/** The only P4-H write path: task → private asset → inert parse → per-item confirmation → KnowledgeDomain. */
class ManageMarkdownImportUseCase(
    private val tasks: MarkdownImportTaskRepository,
    private val assets: MarkdownPrivateAssetStore,
    private val adapter: MarkdownKnowledgeAdapter,
    private val knowledge: ManageKnowledgeUseCase,
    private val clock: Clock,
) {
    fun select(displayName: String, mimeType: String, bytes: ByteArray): MarkdownImportTask {
        val now = clock.instant(); val id = ImportTaskId.new()
        val selected = tasks.save(MarkdownImportTask(id, MarkdownImportTaskStatus.SELECTED, null, createdAt = now, updatedAt = now))
        return when (val copied = assets.copy(MarkdownPrivateCopyRequest(id, displayName, mimeType, bytes))) {
            is MarkdownPrivateCopyResult.Failed -> tasks.save(selected.copy(status = MarkdownImportTaskStatus.FAILED, failure = copied.reason, updatedAt = clock.instant()))
            is MarkdownPrivateCopyResult.Copied -> parseAndStage(selected.copy(status = MarkdownImportTaskStatus.PRIVATE_COPIED, asset = copied.asset, updatedAt = clock.instant()))
        }
    }
    fun retry(id: ImportTaskId): MarkdownImportTask = tasks.find(id)?.let { task ->
        val asset = task.asset ?: return@let task
        val bytes = assets.read(asset.storageKey) ?: return@let tasks.save(task.copy(status = MarkdownImportTaskStatus.FAILED, failure = MarkdownImportFailure.MISSING_PRIVATE_ASSET, retryCount = task.retryCount + 1, updatedAt = clock.instant()))
        parseAndStage(task.copy(status = MarkdownImportTaskStatus.PRIVATE_COPIED, failure = null, retryCount = task.retryCount + 1, updatedAt = clock.instant()), bytes)
    } ?: throw IllegalArgumentException("missing import task")
    fun skip(taskId: ImportTaskId, itemId: ImportItemId): MarkdownImportTask = updateItem(taskId, itemId) { it.copy(status = MarkdownImportItemStatus.SKIPPED, failure = null) }
    fun cancel(taskId: ImportTaskId): MarkdownImportTask = tasks.find(taskId)?.let { task ->
        if (task.status in setOf(MarkdownImportTaskStatus.COMPLETED, MarkdownImportTaskStatus.CANCELLED)) task
        else tasks.save(task.copy(status = MarkdownImportTaskStatus.CANCELLED, updatedAt = clock.instant()))
    } ?: throw IllegalArgumentException("missing import task")
    fun confirm(taskId: ImportTaskId, itemId: ImportItemId, title: String, body: String, tags: Set<String>, scope: KnowledgeScope, projectId: ProjectId?): MarkdownImportTask {
        val task = tasks.find(taskId) ?: throw IllegalArgumentException("missing import task"); val item = task.items.firstOrNull { it.id == itemId } ?: throw IllegalArgumentException("missing import item")
        if (task.status == MarkdownImportTaskStatus.CANCELLED || item.status != MarkdownImportItemStatus.PENDING_CONFIRMATION) return task
        val result = knowledge.execute(KnowledgeIntent(KnowledgeIntentId.new(), KnowledgeIntentAction.CREATE_MARKDOWN_IMPORT, KnowledgeItemId.new(), title, body, tags, scope, projectId, "markdown:${taskId.value}:${itemId.value}"))
        return updateItem(taskId, itemId) { current -> when (result) {
            is KnowledgeMutationResult.Applied -> current.copy(status = MarkdownImportItemStatus.CONFIRMED, knowledgeId = result.snapshot.item.id)
            is KnowledgeMutationResult.Replayed -> current.copy(status = MarkdownImportItemStatus.CONFIRMED, knowledgeId = result.snapshot.item.id)
            is KnowledgeMutationResult.Rejected -> current.copy(status = MarkdownImportItemStatus.FAILED, failure = if (result.code == KnowledgeRejectionCode.HIGH_SENSITIVITY) MarkdownImportFailure.HIGH_SENSITIVITY else MarkdownImportFailure.KNOWLEDGE_REJECTED)
        } }
    }
    fun list(): List<MarkdownImportTask> = tasks.list()
    fun recoverInterrupted(): Int = tasks.list().filter { it.status == MarkdownImportTaskStatus.PARSING }.count { task ->
        tasks.save(task.copy(status = MarkdownImportTaskStatus.FAILED, failure = MarkdownImportFailure.INTERRUPTED, updatedAt = clock.instant()))
        true
    }
    private fun parseAndStage(task: MarkdownImportTask, source: ByteArray? = null): MarkdownImportTask {
        val staged = tasks.save(task.copy(status = MarkdownImportTaskStatus.PARSING, updatedAt = clock.instant()))
        val bytes = source ?: staged.asset?.let { assets.read(it.storageKey) } ?: return tasks.save(staged.copy(status = MarkdownImportTaskStatus.FAILED, failure = MarkdownImportFailure.MISSING_PRIVATE_ASSET, updatedAt = clock.instant()))
        return when (val result = adapter.parse(requireNotNull(staged.asset).displayName, staged.asset.mimeType, bytes)) {
            is MarkdownParseResult.Rejected -> tasks.save(staged.copy(status = MarkdownImportTaskStatus.FAILED, failure = result.reason, updatedAt = clock.instant()))
            is MarkdownParseResult.Parsed -> tasks.save(staged.copy(status = MarkdownImportTaskStatus.AWAITING_CONFIRMATION, items = result.items.mapIndexed { i, parsed -> MarkdownImportItem(ImportItemId.new(), staged.id, i, parsed.title, parsed.body, parsed.tags) }, updatedAt = clock.instant()))
        }
    }
    private fun updateItem(taskId: ImportTaskId, itemId: ImportItemId, transform: (MarkdownImportItem) -> MarkdownImportItem): MarkdownImportTask {
        val task = tasks.find(taskId) ?: throw IllegalArgumentException("missing import task")
        val next = task.copy(items = task.items.map { if (it.id == itemId) transform(it) else it }, updatedAt = clock.instant())
        val status = when {
            next.items.all { it.status in setOf(MarkdownImportItemStatus.CONFIRMED, MarkdownImportItemStatus.SKIPPED) } -> MarkdownImportTaskStatus.COMPLETED
            next.items.any { it.status in setOf(MarkdownImportItemStatus.CONFIRMED, MarkdownImportItemStatus.SKIPPED, MarkdownImportItemStatus.FAILED) } -> MarkdownImportTaskStatus.PARTIALLY_COMPLETED
            else -> MarkdownImportTaskStatus.AWAITING_CONFIRMATION
        }
        return tasks.save(next.copy(status = status))
    }
}

package com.nanzhufeng.ai.domain

import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** P4-N is a dedicated, text-layer-only PDF adapter. PDF bytes and text are inert, never instructions. */
const val PDF_TEXT_ADAPTER_VERSION = 1
const val PDF_TEXT_MAX_BYTES = 5L * 1024 * 1024
const val PDF_TEXT_MAX_PAGES = 80
const val PDF_TEXT_MAX_OBJECT_MARKERS = 5_000
const val PDF_TEXT_MAX_STREAM_MARKERS = 500
const val PDF_TEXT_MAX_PAGE_CODE_POINTS = 12_000
const val PDF_TEXT_MAX_TOTAL_CODE_POINTS = 120_000

@JvmInline value class PdfTextImportTaskId(val value: String) { companion object { fun new() = PdfTextImportTaskId(UUID.randomUUID().toString()) } }
@JvmInline value class PdfTextImportItemId(val value: String) { companion object { fun new() = PdfTextImportItemId(UUID.randomUUID().toString()) } }
enum class PdfTextImportTaskStatus { SELECTED, PRIVATE_COPIED, PREFLIGHT, EXTRACTING, AWAITING_CONFIRMATION, PARTIALLY_COMPLETED, COMPLETED, FAILED, CANCELLED }
enum class PdfTextImportItemStatus { PENDING_CONFIRMATION, CONFIRMED, SKIPPED, FAILED }
enum class PdfTextImportFailure { NOT_PDF, TOO_LARGE, TOO_MANY_OBJECTS, TOO_MANY_STREAMS, MALFORMED, ENCRYPTED, EXTRACTION_NOT_PERMITTED, TOO_MANY_PAGES, EMPTY_DOCUMENT, NO_TEXT_LAYER, PAGE_TEXT_TOO_LONG, TOTAL_TEXT_TOO_LONG, HIGH_SENSITIVITY, PRIVATE_COPY_FAILED, MISSING_PRIVATE_ASSET, INTERRUPTED, KNOWLEDGE_REJECTED }

data class PdfTextImportAsset(val storageKey: String, val mimeType: String, val displayName: String, val byteCount: Long, val sourceSha256: String, val adapterVersion: Int = PDF_TEXT_ADAPTER_VERSION)
data class PdfTextPageArtifact(val pageNumber: Int, val textSha256: String, val codePointCount: Int, val extractionVersion: Int = PDF_TEXT_ADAPTER_VERSION)
data class PdfTextImportItem(val id: PdfTextImportItemId, val taskId: PdfTextImportTaskId, val ordinal: Int, val pageNumber: Int, val title: String, val body: String, val tags: Set<String> = emptySet(), val candidateSha256: String, val status: PdfTextImportItemStatus = PdfTextImportItemStatus.PENDING_CONFIRMATION, val failure: PdfTextImportFailure? = null, val knowledgeId: KnowledgeItemId? = null)
data class PdfTextImportTask(val id: PdfTextImportTaskId, val status: PdfTextImportTaskStatus, val asset: PdfTextImportAsset?, val pageCount: Int = 0, val extractedPageCount: Int = 0, val extractionSha256: String? = null, val failure: PdfTextImportFailure? = null, val retryCount: Int = 0, val createdAt: Instant, val updatedAt: Instant, val pages: List<PdfTextPageArtifact> = emptyList(), val items: List<PdfTextImportItem> = emptyList())
data class PdfTextPrivateCopyRequest(val taskId: PdfTextImportTaskId, val displayName: String, val mimeType: String, val bytes: ByteArray)
sealed interface PdfTextPrivateCopyResult { data class Copied(val asset: PdfTextImportAsset) : PdfTextPrivateCopyResult; data class Failed(val reason: PdfTextImportFailure) : PdfTextPrivateCopyResult }
interface PdfTextPrivateAssetStore { fun copy(request: PdfTextPrivateCopyRequest): PdfTextPrivateCopyResult; fun read(storageKey: String): ByteArray? }
interface PdfTextImportTaskRepository { fun save(task: PdfTextImportTask): PdfTextImportTask; fun find(id: PdfTextImportTaskId): PdfTextImportTask?; fun list(): List<PdfTextImportTask> }
sealed interface PdfTextParseResult { data class Parsed(val pageCount: Int, val pages: List<PdfTextPageArtifact>, val items: List<PdfTextImportItem>, val extractionSha256: String) : PdfTextParseResult; data class Rejected(val reason: PdfTextImportFailure) : PdfTextParseResult }

class PdfTextKnowledgeAdapter(private val knowledgeDomain: KnowledgeDomain) {
    fun parse(taskId: PdfTextImportTaskId, displayName: String, mimeType: String, bytes: ByteArray, cancellation: TaskCancellation = NoTaskCancellation, onPageProgress: (completed: Int, total: Int) -> Unit = { _, _ -> }): PdfTextParseResult = try {
        cancellation.throwIfCancelled()
        if (mimeType.lowercase() != "application/pdf" || !displayName.lowercase().matches(Regex(".+\\.pdf( \\(\\d+\\))?"))) reject(PdfTextImportFailure.NOT_PDF)
        if (bytes.size.toLong() > PDF_TEXT_MAX_BYTES) reject(PdfTextImportFailure.TOO_LARGE)
        if (!bytes.decodeToString(0, minOf(bytes.size, 8), throwOnInvalidSequence = false).startsWith("%PDF-")) reject(PdfTextImportFailure.NOT_PDF)
        val compact = bytes.toString(Charsets.ISO_8859_1)
        if (Regex("(?m)^[0-9]+\\s+[0-9]+\\s+obj\\b").findAll(compact).count() > PDF_TEXT_MAX_OBJECT_MARKERS) reject(PdfTextImportFailure.TOO_MANY_OBJECTS)
        if (Regex("(?i)\\b(stream|/filter)\\b").findAll(compact).count() > PDF_TEXT_MAX_STREAM_MARKERS) reject(PdfTextImportFailure.TOO_MANY_STREAMS)
        PDDocument.load(bytes, "", null, null, MemoryUsageSetting.setupMixed(2L * 1024 * 1024)).use { document ->
            if (document.isEncrypted) reject(PdfTextImportFailure.ENCRYPTED)
            if (!document.currentAccessPermission.canExtractContent()) reject(PdfTextImportFailure.EXTRACTION_NOT_PERMITTED)
            val count = document.numberOfPages
            if (count == 0) reject(PdfTextImportFailure.EMPTY_DOCUMENT)
            if (count > PDF_TEXT_MAX_PAGES) reject(PdfTextImportFailure.TOO_MANY_PAGES)
            val pages = mutableListOf<PdfTextPageArtifact>(); val items = mutableListOf<PdfTextImportItem>(); var total = 0
            for (index in 1..count) {
                cancellation.throwIfCancelled()
                val stripper = PDFTextStripper().apply { startPage = index; endPage = index; sortByPosition = false }
                val text = stripper.getText(document).replace("\r\n", "\n").replace('\r', '\n').trim()
                cancellation.throwIfCancelled()
                val cps = text.codePointCount(0, text.length)
                if (cps > PDF_TEXT_MAX_PAGE_CODE_POINTS) reject(PdfTextImportFailure.PAGE_TEXT_TOO_LONG)
                total += cps; if (total > PDF_TEXT_MAX_TOTAL_CODE_POINTS) reject(PdfTextImportFailure.TOTAL_TEXT_TOO_LONG)
                if (MemoryDomain.sensitiveRejection(text) != null) reject(PdfTextImportFailure.HIGH_SENSITIVITY)
                val pageHash = text.sha256(); pages += PdfTextPageArtifact(index, pageHash, cps)
                if (text.isNotBlank()) {
                    val title = text.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(120)?.ifBlank { "PDF 第 $index 页" } ?: "PDF 第 $index 页"
                    items += PdfTextImportItem(PdfTextImportItemId.new(), taskId, items.size, index, title, text, candidateSha256 = ("$index\\n$text").sha256())
                }
                onPageProgress(index, count)
            }
            if (items.isEmpty()) reject(PdfTextImportFailure.NO_TEXT_LAYER)
            PdfTextParseResult.Parsed(count, pages, items, pages.joinToString("|") { "${it.pageNumber}:${it.textSha256}" }.sha256())
        }
    } catch (e: LocalTaskCancelledException) { PdfTextParseResult.Rejected(PdfTextImportFailure.INTERRUPTED) } catch (e: PdfReject) { PdfTextParseResult.Rejected(e.failure) } catch (_: Exception) { PdfTextParseResult.Rejected(PdfTextImportFailure.MALFORMED) }
    private fun reject(reason: PdfTextImportFailure): Nothing = throw PdfReject(reason)
}
private class PdfReject(val failure: PdfTextImportFailure) : RuntimeException()
private fun String.sha256(): String = MessageDigest.getInstance("SHA-256").digest(toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

class ManagePdfTextKnowledgeImportUseCase(private val tasks: PdfTextImportTaskRepository, private val assets: PdfTextPrivateAssetStore, private val adapter: PdfTextKnowledgeAdapter, private val knowledge: ManageKnowledgeUseCase, private val clock: Clock) {
    private val cancellations = ConcurrentHashMap<PdfTextImportTaskId, LocalTaskCancellation>()
    fun select(displayName: String, mimeType: String, bytes: ByteArray): PdfTextImportTask { val now = clock.instant(); val selected = tasks.save(PdfTextImportTask(PdfTextImportTaskId.new(), PdfTextImportTaskStatus.SELECTED, null, createdAt = now, updatedAt = now)); return when (val copy = assets.copy(PdfTextPrivateCopyRequest(selected.id, displayName, mimeType, bytes))) { is PdfTextPrivateCopyResult.Failed -> tasks.save(selected.copy(status = PdfTextImportTaskStatus.FAILED, failure = copy.reason, updatedAt = clock.instant())); is PdfTextPrivateCopyResult.Copied -> stage(selected.copy(status = PdfTextImportTaskStatus.PRIVATE_COPIED, asset = copy.asset, updatedAt = clock.instant())) } }
    fun retry(id: PdfTextImportTaskId): PdfTextImportTask { val task = tasks.find(id) ?: throw IllegalArgumentException("missing pdf task"); val bytes = task.asset?.let { assets.read(it.storageKey) } ?: return tasks.save(task.copy(status = PdfTextImportTaskStatus.FAILED, failure = PdfTextImportFailure.MISSING_PRIVATE_ASSET, retryCount = task.retryCount + 1, updatedAt = clock.instant())); return stage(task.copy(status = PdfTextImportTaskStatus.PRIVATE_COPIED, failure = null, retryCount = task.retryCount + 1, updatedAt = clock.instant()), bytes) }
    fun list() = tasks.list()
    fun recoverInterrupted(): Int = tasks.list().filter { it.status in setOf(PdfTextImportTaskStatus.PREFLIGHT, PdfTextImportTaskStatus.EXTRACTING) }.count { task ->
        tasks.save(task.copy(status = PdfTextImportTaskStatus.FAILED, failure = PdfTextImportFailure.INTERRUPTED, updatedAt = clock.instant()))
        true
    }
    fun cancel(id: PdfTextImportTaskId) = tasks.find(id)?.let { task ->
        cancellations[id]?.cancel()
        if (task.status in setOf(PdfTextImportTaskStatus.COMPLETED, PdfTextImportTaskStatus.CANCELLED)) task
        else tasks.save(task.copy(status = PdfTextImportTaskStatus.CANCELLED, updatedAt = clock.instant()))
    } ?: throw IllegalArgumentException("missing pdf task")
    fun skip(taskId: PdfTextImportTaskId, itemId: PdfTextImportItemId) = update(taskId, itemId) { it.copy(status = PdfTextImportItemStatus.SKIPPED, failure = null) }
    fun confirm(taskId: PdfTextImportTaskId, itemId: PdfTextImportItemId, title: String, body: String, tags: Set<String>): PdfTextImportTask { val task = tasks.find(taskId) ?: throw IllegalArgumentException("missing pdf task"); val item = task.items.firstOrNull { it.id == itemId } ?: throw IllegalArgumentException("missing pdf item"); if (task.status == PdfTextImportTaskStatus.CANCELLED || item.status != PdfTextImportItemStatus.PENDING_CONFIRMATION) return task; val result = knowledge.execute(KnowledgeIntent(KnowledgeIntentId.new(), KnowledgeIntentAction.CREATE_PDF_TEXT_IMPORT, KnowledgeItemId.new(), title, body, tags, KnowledgeScope.GLOBAL, null, "pdf:${taskId.value}:${item.candidateSha256}")); return update(taskId, itemId) { when (result) { is KnowledgeMutationResult.Applied -> it.copy(status = PdfTextImportItemStatus.CONFIRMED, knowledgeId = result.snapshot.item.id); is KnowledgeMutationResult.Replayed -> it.copy(status = PdfTextImportItemStatus.CONFIRMED, knowledgeId = result.snapshot.item.id); is KnowledgeMutationResult.Rejected -> it.copy(status = PdfTextImportItemStatus.FAILED, failure = if (result.code == KnowledgeRejectionCode.HIGH_SENSITIVITY) PdfTextImportFailure.HIGH_SENSITIVITY else PdfTextImportFailure.KNOWLEDGE_REJECTED) } } }
    private fun stage(task: PdfTextImportTask, supplied: ByteArray? = null): PdfTextImportTask {
        val cancellation = LocalTaskCancellation().also { cancellations[task.id] = it }
        val parsing = saveUnlessCancelled(task.copy(status = PdfTextImportTaskStatus.PREFLIGHT, updatedAt = clock.instant()))
        if (parsing.status == PdfTextImportTaskStatus.CANCELLED) return parsing
        val bytes = supplied ?: parsing.asset?.let { assets.read(it.storageKey) }
            ?: return saveUnlessCancelled(parsing.copy(status = PdfTextImportTaskStatus.FAILED, failure = PdfTextImportFailure.MISSING_PRIVATE_ASSET, updatedAt = clock.instant()))
        val extracting = saveUnlessCancelled(parsing.copy(status = PdfTextImportTaskStatus.EXTRACTING, updatedAt = clock.instant()))
        if (extracting.status == PdfTextImportTaskStatus.CANCELLED) return extracting
        return when (val result = adapter.parse(extracting.id, requireNotNull(extracting.asset).displayName, extracting.asset.mimeType, bytes, cancellation) { completed, total ->
            saveUnlessCancelled(extracting.copy(pageCount = total, extractedPageCount = completed, updatedAt = clock.instant()))
        }) {
            is PdfTextParseResult.Rejected -> saveUnlessCancelled(extracting.copy(status = PdfTextImportTaskStatus.FAILED, failure = result.reason, updatedAt = clock.instant()))
            is PdfTextParseResult.Parsed -> saveUnlessCancelled(extracting.copy(status = PdfTextImportTaskStatus.AWAITING_CONFIRMATION, pageCount = result.pageCount, extractedPageCount = result.pages.size, extractionSha256 = result.extractionSha256, pages = result.pages, items = result.items, updatedAt = clock.instant()))
        }
    }
    private fun saveUnlessCancelled(next: PdfTextImportTask): PdfTextImportTask = tasks.find(next.id)?.takeIf { it.status == PdfTextImportTaskStatus.CANCELLED } ?: tasks.save(next)
    private fun update(taskId: PdfTextImportTaskId, itemId: PdfTextImportItemId, transform: (PdfTextImportItem) -> PdfTextImportItem): PdfTextImportTask { val current = tasks.find(taskId) ?: throw IllegalArgumentException("missing pdf task"); val next = current.copy(items = current.items.map { if (it.id == itemId) transform(it) else it }, updatedAt = clock.instant()); val status = when { next.items.all { it.status in setOf(PdfTextImportItemStatus.CONFIRMED, PdfTextImportItemStatus.SKIPPED) } -> PdfTextImportTaskStatus.COMPLETED; next.items.any { it.status in setOf(PdfTextImportItemStatus.CONFIRMED, PdfTextImportItemStatus.SKIPPED, PdfTextImportItemStatus.FAILED) } -> PdfTextImportTaskStatus.PARTIALLY_COMPLETED; else -> PdfTextImportTaskStatus.AWAITING_CONFIRMATION }; return tasks.save(next.copy(status = status)) }
}

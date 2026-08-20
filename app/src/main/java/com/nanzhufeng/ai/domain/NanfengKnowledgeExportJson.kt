package com.nanzhufeng.ai.domain

import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

/**
 * P6-J consumes only the public JSON export shape documented by the static knowledge-base
 * source.  It deliberately has no file, database, URI, credential, or provider dependency.
 */
const val NANFENG_KNOWLEDGE_EXPORT_ADAPTER_ID = "nanfeng-knowledge-export-json"
const val NANFENG_KNOWLEDGE_EXPORT_ADAPTER_VERSION = 1
const val NANFENG_KNOWLEDGE_EXPORT_MAX_BYTES = 32L * 1024 * 1024
const val NANFENG_KNOWLEDGE_EXPORT_MAX_RECORDS = 200
const val NANFENG_KNOWLEDGE_EXPORT_MAX_MESSAGES = 2_000
const val NANFENG_KNOWLEDGE_EXPORT_MAX_DEPTH = 24
const val NANFENG_KNOWLEDGE_EXPORT_MAX_TEXT_CODE_POINTS = 120_000

enum class NanfengKnowledgeExportParseFailure {
    TOO_LARGE, INVALID_UTF8, MALFORMED_JSON, DUPLICATE_KEY, TOO_DEEP, STRING_TOO_LONG,
    ROOT_NOT_ARRAY, EMPTY_EXPORT, TOO_MANY_RECORDS, INVALID_RECORD, INVALID_TIME,
    NOT_CONVERSATION_RECORD, TOO_MANY_MESSAGES, UNSUPPORTED_ROLE, EMPTY_CONTENT,
    PRIVATE_COPY_FAILED, MISSING_PRIVATE_ASSET, INTERRUPTED, CONFLICT_REIMPORT,
}

data class NanfengKnowledgeExportMessage(
    val sourceId: String,
    val parentSourceId: String?,
    val siblingPosition: Int,
    val role: MessageRole,
    val text: String,
    val createdAt: Instant,
)

data class NanfengKnowledgeExportCandidate(
    val sourceConversationId: String,
    val title: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val messages: List<NanfengKnowledgeExportMessage>,
    val contentHash: String,
)

data class NanfengKnowledgeExportParseItem(
    val ordinal: Int,
    val candidate: NanfengKnowledgeExportCandidate? = null,
    val failure: NanfengKnowledgeExportParseFailure? = null,
)

sealed interface NanfengKnowledgeExportParseResult {
    data class Parsed(val items: List<NanfengKnowledgeExportParseItem>) : NanfengKnowledgeExportParseResult
    data class Rejected(val failure: NanfengKnowledgeExportParseFailure) : NanfengKnowledgeExportParseResult
}

/** P6-J owns a distinct, recoverable task queue; the selected system grant never crosses this boundary. */
@JvmInline value class NanfengKnowledgeImportTaskId(val value: String) { companion object { fun new() = NanfengKnowledgeImportTaskId(UUID.randomUUID().toString()) } }
@JvmInline value class NanfengKnowledgeImportItemId(val value: String) { companion object { fun new() = NanfengKnowledgeImportItemId(UUID.randomUUID().toString()) } }
enum class NanfengKnowledgeImportTaskStatus { SELECTED, PRIVATE_COPIED, PARSING, AWAITING_CONFIRMATION, PARTIALLY_COMPLETED, COMPLETED, FAILED, CANCELLED }
enum class NanfengKnowledgeImportItemStatus { PENDING_CONFIRMATION, CONFIRMED, SKIPPED, FAILED }

data class NanfengKnowledgeImportAsset(val storageKey: String, val mimeType: String, val displayName: String, val byteCount: Long, val packageHash: String)
data class NanfengKnowledgeImportItem(val id: NanfengKnowledgeImportItemId, val taskId: NanfengKnowledgeImportTaskId, val ordinal: Int, val candidate: NanfengKnowledgeExportCandidate?, val status: NanfengKnowledgeImportItemStatus = if (candidate == null) NanfengKnowledgeImportItemStatus.FAILED else NanfengKnowledgeImportItemStatus.PENDING_CONFIRMATION, val failure: NanfengKnowledgeExportParseFailure? = null, val conversationId: ConversationId? = null)
data class NanfengKnowledgeImportTask(val id: NanfengKnowledgeImportTaskId, val status: NanfengKnowledgeImportTaskStatus, val asset: NanfengKnowledgeImportAsset?, val failure: NanfengKnowledgeExportParseFailure? = null, val retryCount: Int = 0, val createdAt: Instant, val updatedAt: Instant, val items: List<NanfengKnowledgeImportItem> = emptyList())
data class NanfengKnowledgePrivateCopyRequest(val taskId: NanfengKnowledgeImportTaskId, val displayName: String, val mimeType: String, val bytes: ByteArray)
sealed interface NanfengKnowledgePrivateCopyResult { data class Copied(val asset: NanfengKnowledgeImportAsset) : NanfengKnowledgePrivateCopyResult; data class Failed(val failure: NanfengKnowledgeExportParseFailure = NanfengKnowledgeExportParseFailure.PRIVATE_COPY_FAILED) : NanfengKnowledgePrivateCopyResult }
interface NanfengKnowledgePrivateAssetStore { fun copy(request: NanfengKnowledgePrivateCopyRequest): NanfengKnowledgePrivateCopyResult; fun read(storageKey: String): ByteArray? }
interface NanfengKnowledgeImportTaskRepository { fun save(task: NanfengKnowledgeImportTask): NanfengKnowledgeImportTask; fun find(id: NanfengKnowledgeImportTaskId): NanfengKnowledgeImportTask?; fun list(): List<NanfengKnowledgeImportTask> }
sealed interface NanfengKnowledgeImportCommitResult { data class Created(val conversationId: ConversationId) : NanfengKnowledgeImportCommitResult; data class Replayed(val conversationId: ConversationId) : NanfengKnowledgeImportCommitResult; data object ConflictReimport : NanfengKnowledgeImportCommitResult; data object Failed : NanfengKnowledgeImportCommitResult }
interface NanfengKnowledgeConversationCommitStore { fun commit(candidate: NanfengKnowledgeExportCandidate, packageHash: String, importedAt: Instant): NanfengKnowledgeImportCommitResult }

class NanfengKnowledgeExportJsonAdapter {
    fun parse(bytes: ByteArray): NanfengKnowledgeExportParseResult = try {
        if (bytes.size.toLong() !in 1..NANFENG_KNOWLEDGE_EXPORT_MAX_BYTES) {
            reject(if (bytes.isEmpty()) NanfengKnowledgeExportParseFailure.EMPTY_EXPORT else NanfengKnowledgeExportParseFailure.TOO_LARGE)
        }
        val root = StrictJsonDocument.parseUtf8(bytes, NANFENG_KNOWLEDGE_EXPORT_MAX_DEPTH, NANFENG_KNOWLEDGE_EXPORT_MAX_TEXT_CODE_POINTS)
            as? StrictJsonValue.Arr ?: reject(NanfengKnowledgeExportParseFailure.ROOT_NOT_ARRAY)
        if (root.values.isEmpty()) reject(NanfengKnowledgeExportParseFailure.EMPTY_EXPORT)
        if (root.values.size > NANFENG_KNOWLEDGE_EXPORT_MAX_RECORDS) reject(NanfengKnowledgeExportParseFailure.TOO_MANY_RECORDS)
        NanfengKnowledgeExportParseResult.Parsed(root.values.mapIndexed { ordinal, value ->
            try {
                NanfengKnowledgeExportParseItem(ordinal, candidate = parseRecord(value as? StrictJsonValue.Obj ?: reject(NanfengKnowledgeExportParseFailure.INVALID_RECORD)))
            } catch (rejected: NanfengKnowledgeExportRejected) {
                NanfengKnowledgeExportParseItem(ordinal, failure = rejected.failure)
            }
        })
    } catch (rejected: StrictJsonRejected) {
        NanfengKnowledgeExportParseResult.Rejected(rejected.failure.asNanfengKnowledgeFailure())
    } catch (rejected: NanfengKnowledgeExportRejected) {
        NanfengKnowledgeExportParseResult.Rejected(rejected.failure)
    }

    private fun parseRecord(record: StrictJsonValue.Obj): NanfengKnowledgeExportCandidate {
        val recordId = (record.fields["id"] as? StrictJsonValue.Num)?.lexical?.toLongOrNull()?.takeIf { it > 0 }
            ?: reject(NanfengKnowledgeExportParseFailure.INVALID_RECORD)
        val title = record.string("title").trim().takeIf { it.isNotEmpty() && it.length <= 120 && it.none(Char::isISOControl) }
            ?: reject(NanfengKnowledgeExportParseFailure.INVALID_RECORD)
        val createdAt = record.timestamp("createdAt")
        val updatedAt = record.timestamp("updatedAt")
        val sourceText = record.string("sourceText")
        val source = StrictJsonDocument.parseUtf8(sourceText.toByteArray(Charsets.UTF_8), NANFENG_KNOWLEDGE_EXPORT_MAX_DEPTH, NANFENG_KNOWLEDGE_EXPORT_MAX_TEXT_CODE_POINTS)
            as? StrictJsonValue.Obj ?: reject(NanfengKnowledgeExportParseFailure.NOT_CONVERSATION_RECORD)
        val rawMessages = (source.fields["chat_messages"] as? StrictJsonValue.Arr)?.values
            ?: reject(NanfengKnowledgeExportParseFailure.NOT_CONVERSATION_RECORD)
        if (rawMessages.isEmpty()) reject(NanfengKnowledgeExportParseFailure.EMPTY_CONTENT)
        if (rawMessages.size > NANFENG_KNOWLEDGE_EXPORT_MAX_MESSAGES) reject(NanfengKnowledgeExportParseFailure.TOO_MANY_MESSAGES)
        val sourceConversationId = "knowledge-record-$recordId"
        val visibleMessages = rawMessages.map { value ->
            val raw = value as? StrictJsonValue.Obj ?: reject(NanfengKnowledgeExportParseFailure.INVALID_RECORD)
            val role = when (raw.string("sender").lowercase()) {
                "human", "user" -> MessageRole.USER
                "assistant" -> MessageRole.ASSISTANT
                "tool" -> return@map null
                else -> reject(NanfengKnowledgeExportParseFailure.UNSUPPORTED_ROLE)
            }
            role to raw.visibleText() to (raw.optionalTimestamp("created_at") ?: createdAt)
        }
        val messages = visibleMessages.filterNotNull().mapIndexed { ordinal, (roleAndText, time) ->
            val (role, text) = roleAndText
            NanfengKnowledgeExportMessage(
                sourceId = "$sourceConversationId-message-$ordinal",
                parentSourceId = if (ordinal == 0) null else "$sourceConversationId-message-${ordinal - 1}",
                siblingPosition = 0,
                role = role,
                text = text,
                createdAt = time,
            )
        }
        if (messages.isEmpty()) reject(NanfengKnowledgeExportParseFailure.EMPTY_CONTENT)
        val contentHash = "$sourceConversationId|${messages.joinToString("|") { "${it.sourceId}:${it.role}:${it.createdAt}:${it.text}" }}".sha256()
        return NanfengKnowledgeExportCandidate(sourceConversationId, title, createdAt, updatedAt, messages, contentHash)
    }

    private fun StrictJsonValue.Obj.string(key: String): String =
        (fields[key] as? StrictJsonValue.Str)?.value ?: reject(NanfengKnowledgeExportParseFailure.INVALID_RECORD)

    private fun StrictJsonValue.Obj.timestamp(key: String): Instant = try {
        Instant.parse(string(key))
    } catch (_: Exception) { reject(NanfengKnowledgeExportParseFailure.INVALID_TIME) }

    private fun StrictJsonValue.Obj.optionalTimestamp(key: String): Instant? = when (val value = fields[key]) {
        null, StrictJsonValue.Null -> null
        is StrictJsonValue.Str -> try { Instant.parse(value.value) } catch (_: Exception) { reject(NanfengKnowledgeExportParseFailure.INVALID_TIME) }
        else -> reject(NanfengKnowledgeExportParseFailure.INVALID_TIME)
    }

    private fun StrictJsonValue.Obj.visibleText(): String {
        val blocks = (fields["content"] as? StrictJsonValue.Arr)?.values.orEmpty()
        val visible = blocks.mapNotNull { value ->
            val block = value as? StrictJsonValue.Obj ?: reject(NanfengKnowledgeExportParseFailure.INVALID_RECORD)
            val type = (block.fields["type"] as? StrictJsonValue.Str)?.value
            if (type != null && type != "text") null else (block.fields["text"] as? StrictJsonValue.Str)?.value
        }.joinToString("\n\n").trim()
        val fallback = (fields["text"] as? StrictJsonValue.Str)?.value.orEmpty().trim()
        return (visible.ifEmpty { fallback }).takeIf(String::isNotEmpty) ?: reject(NanfengKnowledgeExportParseFailure.EMPTY_CONTENT)
    }

    private fun reject(failure: NanfengKnowledgeExportParseFailure): Nothing = throw NanfengKnowledgeExportRejected(failure)
}

private class NanfengKnowledgeExportRejected(val failure: NanfengKnowledgeExportParseFailure) : RuntimeException()
private fun StrictJsonFailure.asNanfengKnowledgeFailure() = when (this) {
    StrictJsonFailure.INVALID_UTF8 -> NanfengKnowledgeExportParseFailure.INVALID_UTF8
    StrictJsonFailure.MALFORMED -> NanfengKnowledgeExportParseFailure.MALFORMED_JSON
    StrictJsonFailure.DUPLICATE_KEY -> NanfengKnowledgeExportParseFailure.DUPLICATE_KEY
    StrictJsonFailure.TOO_DEEP -> NanfengKnowledgeExportParseFailure.TOO_DEEP
    StrictJsonFailure.STRING_TOO_LONG -> NanfengKnowledgeExportParseFailure.STRING_TOO_LONG
}
private fun String.sha256() = MessageDigest.getInstance("SHA-256").digest(toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

/** Coordinates only an already-private JSON copy and the adapter-owned atomic commit port. */
class ManageNanfengKnowledgeExportImportUseCase(
    private val tasks: NanfengKnowledgeImportTaskRepository,
    private val assets: NanfengKnowledgePrivateAssetStore,
    private val adapter: NanfengKnowledgeExportJsonAdapter,
    private val commits: NanfengKnowledgeConversationCommitStore,
    private val clock: java.time.Clock,
) {
    fun select(displayName: String, mimeType: String, bytes: ByteArray): NanfengKnowledgeImportTask {
        val now = clock.instant()
        val selected = tasks.save(NanfengKnowledgeImportTask(NanfengKnowledgeImportTaskId.new(), NanfengKnowledgeImportTaskStatus.SELECTED, null, createdAt = now, updatedAt = now))
        return when (val copied = assets.copy(NanfengKnowledgePrivateCopyRequest(selected.id, displayName, mimeType, bytes))) {
            is NanfengKnowledgePrivateCopyResult.Failed -> tasks.save(selected.copy(status = NanfengKnowledgeImportTaskStatus.FAILED, failure = copied.failure, updatedAt = clock.instant()))
            is NanfengKnowledgePrivateCopyResult.Copied -> stage(selected.copy(status = NanfengKnowledgeImportTaskStatus.PRIVATE_COPIED, asset = copied.asset, updatedAt = clock.instant()))
        }
    }

    fun list(): List<NanfengKnowledgeImportTask> = tasks.list()
    fun retry(id: NanfengKnowledgeImportTaskId): NanfengKnowledgeImportTask {
        val task = requireNotNull(tasks.find(id))
        val bytes = task.asset?.let { assets.read(it.storageKey) }
            ?: return tasks.save(task.copy(status = NanfengKnowledgeImportTaskStatus.FAILED, failure = NanfengKnowledgeExportParseFailure.MISSING_PRIVATE_ASSET, retryCount = task.retryCount + 1, updatedAt = clock.instant()))
        return stage(task.copy(status = NanfengKnowledgeImportTaskStatus.PRIVATE_COPIED, failure = null, retryCount = task.retryCount + 1, updatedAt = clock.instant()), bytes)
    }
    fun recoverInterrupted(): Int = tasks.list().filter { it.status == NanfengKnowledgeImportTaskStatus.PARSING }.count { task ->
        tasks.save(task.copy(status = NanfengKnowledgeImportTaskStatus.FAILED, failure = NanfengKnowledgeExportParseFailure.INTERRUPTED, updatedAt = clock.instant())); true
    }
    fun cancel(id: NanfengKnowledgeImportTaskId): NanfengKnowledgeImportTask {
        val task = requireNotNull(tasks.find(id))
        return if (task.status in setOf(NanfengKnowledgeImportTaskStatus.COMPLETED, NanfengKnowledgeImportTaskStatus.CANCELLED)) task else tasks.save(task.copy(status = NanfengKnowledgeImportTaskStatus.CANCELLED, updatedAt = clock.instant()))
    }
    fun skip(taskId: NanfengKnowledgeImportTaskId, itemId: NanfengKnowledgeImportItemId) = update(taskId, itemId) { item -> if (item.status == NanfengKnowledgeImportItemStatus.PENDING_CONFIRMATION) item.copy(status = NanfengKnowledgeImportItemStatus.SKIPPED) else item }
    fun confirm(taskId: NanfengKnowledgeImportTaskId, itemId: NanfengKnowledgeImportItemId): NanfengKnowledgeImportTask {
        val task = requireNotNull(tasks.find(taskId)); val item = task.items.firstOrNull { it.id == itemId } ?: error("missing P6-J import item")
        if (task.status == NanfengKnowledgeImportTaskStatus.CANCELLED || item.status != NanfengKnowledgeImportItemStatus.PENDING_CONFIRMATION) return task
        return update(taskId, itemId) { current -> when (val result = commits.commit(requireNotNull(item.candidate), requireNotNull(task.asset).packageHash, clock.instant())) {
            is NanfengKnowledgeImportCommitResult.Created -> current.copy(status = NanfengKnowledgeImportItemStatus.CONFIRMED, conversationId = result.conversationId)
            is NanfengKnowledgeImportCommitResult.Replayed -> current.copy(status = NanfengKnowledgeImportItemStatus.CONFIRMED, conversationId = result.conversationId)
            NanfengKnowledgeImportCommitResult.ConflictReimport -> current.copy(status = NanfengKnowledgeImportItemStatus.FAILED, failure = NanfengKnowledgeExportParseFailure.CONFLICT_REIMPORT)
            NanfengKnowledgeImportCommitResult.Failed -> current.copy(status = NanfengKnowledgeImportItemStatus.FAILED, failure = NanfengKnowledgeExportParseFailure.INVALID_RECORD)
        } }
    }
    private fun stage(task: NanfengKnowledgeImportTask, supplied: ByteArray? = null): NanfengKnowledgeImportTask {
        val parsing = tasks.save(task.copy(status = NanfengKnowledgeImportTaskStatus.PARSING, updatedAt = clock.instant()))
        val bytes = supplied ?: parsing.asset?.let { assets.read(it.storageKey) }
            ?: return tasks.save(parsing.copy(status = NanfengKnowledgeImportTaskStatus.FAILED, failure = NanfengKnowledgeExportParseFailure.MISSING_PRIVATE_ASSET, updatedAt = clock.instant()))
        return when (val parsed = adapter.parse(bytes)) {
            is NanfengKnowledgeExportParseResult.Rejected -> tasks.save(parsing.copy(status = NanfengKnowledgeImportTaskStatus.FAILED, failure = parsed.failure, updatedAt = clock.instant()))
            is NanfengKnowledgeExportParseResult.Parsed -> commitSelectedCandidates(tasks.save(parsing.copy(status = NanfengKnowledgeImportTaskStatus.AWAITING_CONFIRMATION, items = parsed.items.map { NanfengKnowledgeImportItem(NanfengKnowledgeImportItemId.new(), parsing.id, it.ordinal, it.candidate, failure = it.failure) }, updatedAt = clock.instant())))
        }
    }
    /** Picker selection is the import command: only valid records commit, each atomically. */
    private fun commitSelectedCandidates(task: NanfengKnowledgeImportTask): NanfengKnowledgeImportTask = task.items
        .filter { it.status == NanfengKnowledgeImportItemStatus.PENDING_CONFIRMATION && it.candidate != null }
        .fold(task) { _, item -> confirm(task.id, item.id) }
    private fun update(taskId: NanfengKnowledgeImportTaskId, itemId: NanfengKnowledgeImportItemId, mutate: (NanfengKnowledgeImportItem) -> NanfengKnowledgeImportItem): NanfengKnowledgeImportTask {
        val task = requireNotNull(tasks.find(taskId)); val next = task.copy(items = task.items.map { if (it.id == itemId) mutate(it) else it }, updatedAt = clock.instant())
        val status = when { next.items.all { it.status in setOf(NanfengKnowledgeImportItemStatus.CONFIRMED, NanfengKnowledgeImportItemStatus.SKIPPED) } -> NanfengKnowledgeImportTaskStatus.COMPLETED; next.items.any { it.status != NanfengKnowledgeImportItemStatus.PENDING_CONFIRMATION } -> NanfengKnowledgeImportTaskStatus.PARTIALLY_COMPLETED; else -> NanfengKnowledgeImportTaskStatus.AWAITING_CONFIRMATION }
        return tasks.save(next.copy(status = status))
    }
}

package com.nanzhufeng.ai.domain

import java.security.MessageDigest
import java.time.Instant

/**
 * P6-I's first, deliberately small boundary: a pure parser for a user-selected Claude export.
 * It has no file, database, credential, provider, or UI dependency.  Import task ownership and
 * persistence are separate follow-up increments once this input contract is stable.
 */
const val CLAUDE_EXPORT_ADAPTER_ID = "claude-export-json"
const val CLAUDE_EXPORT_ADAPTER_VERSION = 2
// The verified official ZIP's conversations.json is 38.4 MiB.  Keep a bounded
// allowance above that evidence; this remains a strict, in-memory parser limit.
const val CLAUDE_EXPORT_MAX_BYTES = 64L * 1024 * 1024
const val CLAUDE_EXPORT_MAX_CONVERSATIONS = 1_000
const val CLAUDE_EXPORT_MAX_MESSAGES = 2_000
const val CLAUDE_EXPORT_MAX_DEPTH = 24
const val CLAUDE_EXPORT_MAX_TEXT_CODE_POINTS = 120_000

enum class ClaudeExportParseFailure {
    TOO_LARGE,
    INVALID_UTF8,
    MALFORMED_JSON,
    DUPLICATE_KEY,
    TOO_DEEP,
    STRING_TOO_LONG,
    ROOT_NOT_ARRAY,
    EMPTY_EXPORT,
    TOO_MANY_CONVERSATIONS,
    INVALID_CONVERSATION,
    INVALID_TIME,
    TOO_MANY_MESSAGES,
    DUPLICATE_SOURCE_ID,
    INVALID_TREE,
    UNSUPPORTED_ROLE,
    UNSUPPORTED_CONTENT,
    EMPTY_CONTENT,
    PRIVATE_COPY_FAILED,
    MISSING_PRIVATE_ASSET,
    INTERRUPTED,
    CONFLICT_REIMPORT,
}

@JvmInline value class ClaudeImportTaskId(val value: String) { companion object { fun new() = ClaudeImportTaskId(java.util.UUID.randomUUID().toString()) } }
@JvmInline value class ClaudeImportItemId(val value: String) { companion object { fun new() = ClaudeImportItemId(java.util.UUID.randomUUID().toString()) } }
enum class ClaudeImportTaskStatus { SELECTED, PRIVATE_COPIED, PARSING, AWAITING_CONFIRMATION, PARTIALLY_COMPLETED, COMPLETED, FAILED, CANCELLED }
enum class ClaudeImportItemStatus { PENDING_CONFIRMATION, CONFIRMED, SKIPPED, FAILED }

data class ClaudeImportAsset(val storageKey: String, val mimeType: String, val displayName: String, val byteCount: Long, val packageHash: String)
data class ClaudeImportItem(val id: ClaudeImportItemId, val taskId: ClaudeImportTaskId, val ordinal: Int, val candidate: ClaudeExportCandidate?, val status: ClaudeImportItemStatus = if (candidate == null) ClaudeImportItemStatus.FAILED else ClaudeImportItemStatus.PENDING_CONFIRMATION, val failure: ClaudeExportParseFailure? = null, val conversationId: ConversationId? = null)
data class ClaudeImportTask(val id: ClaudeImportTaskId, val status: ClaudeImportTaskStatus, val asset: ClaudeImportAsset?, val failure: ClaudeExportParseFailure? = null, val retryCount: Int = 0, val createdAt: Instant, val updatedAt: Instant, val items: List<ClaudeImportItem> = emptyList())
data class ClaudePrivateCopyRequest(val taskId: ClaudeImportTaskId, val displayName: String, val mimeType: String, val bytes: ByteArray)
sealed interface ClaudePrivateCopyResult { data class Copied(val asset: ClaudeImportAsset) : ClaudePrivateCopyResult; data class Failed(val failure: ClaudeExportParseFailure = ClaudeExportParseFailure.PRIVATE_COPY_FAILED) : ClaudePrivateCopyResult }
interface ClaudePrivateAssetStore { fun copy(request: ClaudePrivateCopyRequest): ClaudePrivateCopyResult; fun read(storageKey: String): ByteArray? }
interface ClaudeImportTaskRepository { fun save(task: ClaudeImportTask): ClaudeImportTask; fun find(id: ClaudeImportTaskId): ClaudeImportTask?; fun list(): List<ClaudeImportTask> }
sealed interface ClaudeImportCommitResult { data class Created(val conversationId: ConversationId) : ClaudeImportCommitResult; data class Replayed(val conversationId: ConversationId) : ClaudeImportCommitResult; data object ConflictReimport : ClaudeImportCommitResult; data object Failed : ClaudeImportCommitResult }
interface ClaudeConversationCommitStore { fun commit(candidate: ClaudeExportCandidate, packageHash: String, importedAt: Instant): ClaudeImportCommitResult }

data class ClaudeExportMessage(
    val sourceId: String,
    val parentSourceId: String?,
    val siblingPosition: Int,
    val role: MessageRole,
    val text: String,
    val createdAt: Instant,
)

data class ClaudeExportCandidate(
    val sourceConversationId: String,
    val title: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val messages: List<ClaudeExportMessage>,
    val contentHash: String,
)

data class ClaudeExportParseItem(
    val ordinal: Int,
    val candidate: ClaudeExportCandidate? = null,
    val failure: ClaudeExportParseFailure? = null,
)

sealed interface ClaudeExportParseResult {
    data class Parsed(val items: List<ClaudeExportParseItem>) : ClaudeExportParseResult
    data class Rejected(val failure: ClaudeExportParseFailure) : ClaudeExportParseResult
}

class ClaudeExportJsonAdapter {
    fun parse(bytes: ByteArray): ClaudeExportParseResult = try {
        if (bytes.size.toLong() !in 1..CLAUDE_EXPORT_MAX_BYTES) {
            reject(if (bytes.isEmpty()) ClaudeExportParseFailure.EMPTY_EXPORT else ClaudeExportParseFailure.TOO_LARGE)
        }
        val root = StrictJsonDocument.parseUtf8(bytes, CLAUDE_EXPORT_MAX_DEPTH, CLAUDE_EXPORT_MAX_TEXT_CODE_POINTS)
            as? StrictJsonValue.Arr ?: reject(ClaudeExportParseFailure.ROOT_NOT_ARRAY)
        if (root.values.isEmpty()) reject(ClaudeExportParseFailure.EMPTY_EXPORT)
        if (root.values.size > CLAUDE_EXPORT_MAX_CONVERSATIONS) reject(ClaudeExportParseFailure.TOO_MANY_CONVERSATIONS)
        ClaudeExportParseResult.Parsed(root.values.mapIndexed { ordinal, value ->
            try {
                ClaudeExportParseItem(ordinal, candidate = parseConversation(value as? StrictJsonValue.Obj ?: reject(ClaudeExportParseFailure.INVALID_CONVERSATION)))
            } catch (rejected: ClaudeExportRejected) {
                ClaudeExportParseItem(ordinal, failure = rejected.failure)
            }
        })
    } catch (rejected: StrictJsonRejected) {
        ClaudeExportParseResult.Rejected(rejected.failure.asClaudeExportFailure())
    } catch (rejected: ClaudeExportRejected) {
        ClaudeExportParseResult.Rejected(rejected.failure)
    }

    private fun parseConversation(obj: StrictJsonValue.Obj): ClaudeExportCandidate {
        val id = obj.requiredString("uuid").safeUuid()
        val title = obj.nullableSafeTitle("name")
        val createdAt = obj.isoTimestamp("created_at")
        val updatedAt = obj.isoTimestamp("updated_at")
        val rawMessages = (obj.fields["chat_messages"] as? StrictJsonValue.Arr)?.values
            ?: reject(ClaudeExportParseFailure.INVALID_CONVERSATION)
        if (rawMessages.isEmpty()) reject(ClaudeExportParseFailure.EMPTY_CONTENT)
        if (rawMessages.size > CLAUDE_EXPORT_MAX_MESSAGES) reject(ClaudeExportParseFailure.TOO_MANY_MESSAGES)

        val messages = rawMessages.map { value -> parseMessage(value as? StrictJsonValue.Obj ?: reject(ClaudeExportParseFailure.INVALID_CONVERSATION)) }
        if (messages.map(ClaudeRawMessage::sourceId).distinct().size != messages.size) reject(ClaudeExportParseFailure.DUPLICATE_SOURCE_ID)
        val byId = messages.associateBy(ClaudeRawMessage::sourceId)
        // The verified ZIP records the first in-export message with an opaque predecessor outside
        // the conversation's local array. Treat only that missing local parent as its root.
        val roots = messages.filter { it.parentSourceId == null || it.parentSourceId !in byId }
        if (roots.size != 1) reject(ClaudeExportParseFailure.INVALID_TREE)

        val children = messages.groupBy { it.parentSourceId?.takeIf(byId::containsKey) }
        val visited = linkedSetOf<String>()
        fun visit(sourceId: String) {
            if (!visited.add(sourceId)) reject(ClaudeExportParseFailure.INVALID_TREE)
            children[sourceId].orEmpty().forEach { visit(it.sourceId) }
        }
        visit(roots.single().sourceId)
        if (visited.size != messages.size) reject(ClaudeExportParseFailure.INVALID_TREE)

        val siblingPositions = mutableMapOf<String?, Int>()
        val imported = messages.map { message ->
            val parent = message.parentSourceId?.takeIf(byId::containsKey)
            val position = siblingPositions.getOrDefault(parent, 0)
            siblingPositions[parent] = position + 1
            ClaudeExportMessage(message.sourceId, parent, position, message.role, message.text, message.createdAt)
        }
        return ClaudeExportCandidate(id, title, createdAt, updatedAt, imported, "$id|${imported.joinToString("|") { "${it.sourceId}:${it.parentSourceId}:${it.role}:${it.createdAt}:${it.text}" }}".sha256())
    }

    private data class ClaudeRawMessage(
        val sourceId: String,
        val parentSourceId: String?,
        val role: MessageRole,
        val text: String,
        val createdAt: Instant,
    )

    private fun parseMessage(obj: StrictJsonValue.Obj): ClaudeRawMessage {
        val sourceId = obj.requiredString("uuid").safeUuid()
        val parent = obj.nullableString("parent_message_uuid")?.safeUuid()
        val role = when (obj.requiredString("sender")) {
            "human" -> MessageRole.USER
            "assistant" -> MessageRole.ASSISTANT
            "tool" -> MessageRole.TOOL
            else -> reject(ClaudeExportParseFailure.UNSUPPORTED_ROLE)
        }
        val text = obj.textContent()
        val createdAt = obj.isoTimestamp("created_at")
        return ClaudeRawMessage(sourceId, parent, role, text, createdAt)
    }

    private fun StrictJsonValue.Obj.requiredString(key: String): String =
        (fields[key] as? StrictJsonValue.Str)?.value ?: reject(ClaudeExportParseFailure.INVALID_CONVERSATION)

    private fun StrictJsonValue.Obj.nullableString(key: String): String? = when (val value = fields[key]) {
        null, StrictJsonValue.Null -> null
        is StrictJsonValue.Str -> value.value
        else -> reject(ClaudeExportParseFailure.INVALID_CONVERSATION)
    }

    private fun StrictJsonValue.Obj.nullableSafeTitle(key: String): String = when (val value = fields[key]) {
        null, StrictJsonValue.Null -> "未命名 Claude 对话"
        is StrictJsonValue.Str -> value.value.trim().let { title ->
            when {
                title.isEmpty() -> "未命名 Claude 对话"
                title.length <= 120 && title.none(Char::isISOControl) -> title
                else -> reject(ClaudeExportParseFailure.INVALID_CONVERSATION)
            }
        }
        else -> reject(ClaudeExportParseFailure.INVALID_CONVERSATION)
    }

    private fun StrictJsonValue.Obj.isoTimestamp(key: String): Instant = try {
        Instant.parse(requiredString(key))
    } catch (_: Exception) {
        reject(ClaudeExportParseFailure.INVALID_TIME)
    }

    private fun StrictJsonValue.Obj.textContent(): String {
        val source = (fields["text"] as? StrictJsonValue.Str) ?: (fields["content"] as? StrictJsonValue.Str)
            ?: reject(ClaudeExportParseFailure.UNSUPPORTED_CONTENT)
        val text = source.value
        return text.trim().takeIf { it.isNotEmpty() } ?: reject(ClaudeExportParseFailure.EMPTY_CONTENT)
    }

    private fun String.safeUuid(): String = takeIf {
        matches(Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
    } ?: reject(ClaudeExportParseFailure.INVALID_CONVERSATION)

    private fun reject(failure: ClaudeExportParseFailure): Nothing = throw ClaudeExportRejected(failure)
}

private class ClaudeExportRejected(val failure: ClaudeExportParseFailure) : RuntimeException()

/**
 * The task state machine has only opaque private-asset and atomic-commit ports.  Platform storage
 * owns those ports later; this type deliberately cannot receive an Android URI, a path, or a key.
 */
class ManageClaudeExportImportUseCase(
    private val tasks: ClaudeImportTaskRepository,
    private val assets: ClaudePrivateAssetStore,
    private val adapter: ClaudeExportJsonAdapter,
    private val commits: ClaudeConversationCommitStore,
    private val clock: java.time.Clock,
) {
    fun select(displayName: String, mimeType: String, bytes: ByteArray): ClaudeImportTask {
        val now = clock.instant()
        val selected = tasks.save(ClaudeImportTask(ClaudeImportTaskId.new(), ClaudeImportTaskStatus.SELECTED, null, createdAt = now, updatedAt = now))
        return when (val copied = assets.copy(ClaudePrivateCopyRequest(selected.id, displayName, mimeType, bytes))) {
            is ClaudePrivateCopyResult.Failed -> tasks.save(selected.copy(status = ClaudeImportTaskStatus.FAILED, failure = copied.failure, updatedAt = clock.instant()))
            is ClaudePrivateCopyResult.Copied -> stage(selected.copy(status = ClaudeImportTaskStatus.PRIVATE_COPIED, asset = copied.asset, updatedAt = clock.instant()))
        }
    }

    fun list(): List<ClaudeImportTask> = tasks.list()

    fun retry(id: ClaudeImportTaskId): ClaudeImportTask {
        val task = requireNotNull(tasks.find(id))
        val bytes = task.asset?.let { assets.read(it.storageKey) }
            ?: return tasks.save(task.copy(status = ClaudeImportTaskStatus.FAILED, failure = ClaudeExportParseFailure.MISSING_PRIVATE_ASSET, retryCount = task.retryCount + 1, updatedAt = clock.instant()))
        return stage(task.copy(status = ClaudeImportTaskStatus.PRIVATE_COPIED, failure = null, retryCount = task.retryCount + 1, updatedAt = clock.instant()), bytes)
    }

    fun recoverInterrupted(): Int = tasks.list().filter { it.status == ClaudeImportTaskStatus.PARSING }.count { task ->
        tasks.save(task.copy(status = ClaudeImportTaskStatus.FAILED, failure = ClaudeExportParseFailure.INTERRUPTED, updatedAt = clock.instant()))
        true
    }

    fun cancel(id: ClaudeImportTaskId): ClaudeImportTask {
        val task = requireNotNull(tasks.find(id))
        return if (task.status in setOf(ClaudeImportTaskStatus.COMPLETED, ClaudeImportTaskStatus.CANCELLED)) task
        else tasks.save(task.copy(status = ClaudeImportTaskStatus.CANCELLED, updatedAt = clock.instant()))
    }

    fun skip(taskId: ClaudeImportTaskId, itemId: ClaudeImportItemId): ClaudeImportTask = update(taskId, itemId) { item ->
        if (item.status == ClaudeImportItemStatus.PENDING_CONFIRMATION) item.copy(status = ClaudeImportItemStatus.SKIPPED) else item
    }

    fun confirm(taskId: ClaudeImportTaskId, itemId: ClaudeImportItemId): ClaudeImportTask {
        val task = requireNotNull(tasks.find(taskId))
        val item = task.items.firstOrNull { it.id == itemId } ?: error("missing Claude import item")
        if (task.status == ClaudeImportTaskStatus.CANCELLED || item.status != ClaudeImportItemStatus.PENDING_CONFIRMATION) return task
        val outcome = commits.commit(requireNotNull(item.candidate), requireNotNull(task.asset).packageHash, clock.instant())
        return update(taskId, itemId) { current -> when (outcome) {
            is ClaudeImportCommitResult.Created -> current.copy(status = ClaudeImportItemStatus.CONFIRMED, conversationId = outcome.conversationId)
            is ClaudeImportCommitResult.Replayed -> current.copy(status = ClaudeImportItemStatus.CONFIRMED, conversationId = outcome.conversationId)
            ClaudeImportCommitResult.ConflictReimport -> current.copy(status = ClaudeImportItemStatus.FAILED, failure = ClaudeExportParseFailure.CONFLICT_REIMPORT)
            ClaudeImportCommitResult.Failed -> current.copy(status = ClaudeImportItemStatus.FAILED, failure = ClaudeExportParseFailure.INVALID_CONVERSATION)
        } }
    }

    private fun stage(task: ClaudeImportTask, supplied: ByteArray? = null): ClaudeImportTask {
        val parsing = tasks.save(task.copy(status = ClaudeImportTaskStatus.PARSING, updatedAt = clock.instant()))
        val bytes = supplied ?: parsing.asset?.let { assets.read(it.storageKey) }
            ?: return tasks.save(parsing.copy(status = ClaudeImportTaskStatus.FAILED, failure = ClaudeExportParseFailure.MISSING_PRIVATE_ASSET, updatedAt = clock.instant()))
        return when (val parsed = adapter.parse(bytes)) {
            is ClaudeExportParseResult.Rejected -> tasks.save(parsing.copy(status = ClaudeImportTaskStatus.FAILED, failure = parsed.failure, updatedAt = clock.instant()))
            is ClaudeExportParseResult.Parsed -> commitSelectedCandidates(tasks.save(parsing.copy(status = ClaudeImportTaskStatus.AWAITING_CONFIRMATION, items = parsed.items.map { item ->
                ClaudeImportItem(ClaudeImportItemId.new(), parsing.id, item.ordinal, item.candidate, failure = item.failure)
            }, updatedAt = clock.instant())))
        }
    }

    /** Picker selection is the import command: valid candidates commit independently. */
    private fun commitSelectedCandidates(task: ClaudeImportTask): ClaudeImportTask = task.items
        .filter { it.status == ClaudeImportItemStatus.PENDING_CONFIRMATION && it.candidate != null }
        .fold(task) { _, item -> confirm(task.id, item.id) }

    private fun update(taskId: ClaudeImportTaskId, itemId: ClaudeImportItemId, mutate: (ClaudeImportItem) -> ClaudeImportItem): ClaudeImportTask {
        val task = requireNotNull(tasks.find(taskId))
        val next = task.copy(items = task.items.map { if (it.id == itemId) mutate(it) else it }, updatedAt = clock.instant())
        val status = when {
            next.items.all { it.status in setOf(ClaudeImportItemStatus.CONFIRMED, ClaudeImportItemStatus.SKIPPED) } -> ClaudeImportTaskStatus.COMPLETED
            next.items.any { it.status != ClaudeImportItemStatus.PENDING_CONFIRMATION } -> ClaudeImportTaskStatus.PARTIALLY_COMPLETED
            else -> ClaudeImportTaskStatus.AWAITING_CONFIRMATION
        }
        return tasks.save(next.copy(status = status))
    }
}

private fun StrictJsonFailure.asClaudeExportFailure() = when (this) {
    StrictJsonFailure.INVALID_UTF8 -> ClaudeExportParseFailure.INVALID_UTF8
    StrictJsonFailure.MALFORMED -> ClaudeExportParseFailure.MALFORMED_JSON
    StrictJsonFailure.DUPLICATE_KEY -> ClaudeExportParseFailure.DUPLICATE_KEY
    StrictJsonFailure.TOO_DEEP -> ClaudeExportParseFailure.TOO_DEEP
    StrictJsonFailure.STRING_TOO_LONG -> ClaudeExportParseFailure.STRING_TOO_LONG
}

private fun String.sha256() = MessageDigest.getInstance("SHA-256")
    .digest(toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(it) }

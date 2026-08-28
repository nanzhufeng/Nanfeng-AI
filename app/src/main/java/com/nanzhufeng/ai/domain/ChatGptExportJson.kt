package com.nanzhufeng.ai.domain

import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.util.UUID

/** P6-H's source-specific, local-only adapter. ChatGPT export values are never executable. */
const val CHATGPT_EXPORT_ADAPTER_ID = "chatgpt-export-json"
const val CHATGPT_EXPORT_ADAPTER_VERSION = 2
const val CHATGPT_EXPORT_MAX_BYTES = 32L * 1024 * 1024
const val CHATGPT_EXPORT_MAX_CONVERSATIONS = 200
const val CHATGPT_EXPORT_MAX_MAPPING_NODES = 2_000
const val CHATGPT_EXPORT_MAX_DEPTH = 24
const val CHATGPT_EXPORT_MAX_TEXT_CODE_POINTS = 120_000

@JvmInline value class ChatGptImportTaskId(val value: String) { companion object { fun new() = ChatGptImportTaskId(UUID.randomUUID().toString()) } }
@JvmInline value class ChatGptImportItemId(val value: String) { companion object { fun new() = ChatGptImportItemId(UUID.randomUUID().toString()) } }
enum class ChatGptImportTaskStatus { SELECTED, PRIVATE_COPIED, PARSING, AWAITING_CONFIRMATION, PARTIALLY_COMPLETED, COMPLETED, FAILED, CANCELLED }
enum class ChatGptImportItemStatus { PENDING_CONFIRMATION, CONFIRMED, SKIPPED, FAILED }
enum class ChatGptImportFailure { NOT_JSON, TOO_LARGE, INVALID_UTF8, MALFORMED_JSON, DUPLICATE_KEY, TOO_DEEP, STRING_TOO_LONG, ROOT_NOT_ARRAY, EMPTY_EXPORT, TOO_MANY_CONVERSATIONS, INVALID_CONVERSATION, INVALID_TIME, TOO_MANY_NODES, DUPLICATE_SOURCE_ID, INVALID_TREE, UNSUPPORTED_ROLE, UNSUPPORTED_CONTENT, EMPTY_CONTENT, PRIVATE_COPY_FAILED, MISSING_PRIVATE_ASSET, INTERRUPTED, CONFLICT_REIMPORT }

data class ChatGptImportAsset(val storageKey: String, val mimeType: String, val displayName: String, val byteCount: Long, val packageHash: String)
data class ChatGptImportMessage(val sourceId: String, val parentSourceId: String?, val siblingPosition: Int, val role: MessageRole, val text: String, val createdAt: Instant, val importedModel: String?)
data class ChatGptImportCandidate(val sourceConversationId: String, val title: String, val createdAt: Instant, val updatedAt: Instant, val messages: List<ChatGptImportMessage>, val contentHash: String)

/** Stable semantic identity for one imported source message.  It deliberately excludes the
 * transient ZIP entry and keeps content-based incremental import from mistaking a changed reply
 * for a package replay. */
fun ChatGptImportMessage.semanticContentHash(): String = listOf(
    sourceId, parentSourceId.orEmpty(), role.name, text,
    createdAt.toEpochMilli().toString(), importedModel.orEmpty(),
).joinToString("\u001f").sha256()

/** Package-level dedup is based on source conversation identity plus every rendered source
 * message, not on export timestamps, titles, filenames, or package hashes. */
fun chatGptImportContentHash(sourceConversationId: String, messages: List<ChatGptImportMessage>): String =
    (sourceConversationId + "\u001e" + messages.joinToString("\u001e") { it.semanticContentHash() }).sha256()
data class ChatGptImportItem(val id: ChatGptImportItemId, val taskId: ChatGptImportTaskId, val ordinal: Int, val candidate: ChatGptImportCandidate?, val status: ChatGptImportItemStatus = ChatGptImportItemStatus.PENDING_CONFIRMATION, val failure: ChatGptImportFailure? = null, val conversationId: ConversationId? = null)
data class ChatGptImportTask(val id: ChatGptImportTaskId, val status: ChatGptImportTaskStatus, val asset: ChatGptImportAsset?, val failure: ChatGptImportFailure? = null, val retryCount: Int = 0, val createdAt: Instant, val updatedAt: Instant, val items: List<ChatGptImportItem> = emptyList())
data class ChatGptPrivateCopyRequest(val taskId: ChatGptImportTaskId, val displayName: String, val mimeType: String, val bytes: ByteArray)
sealed interface ChatGptPrivateCopyResult { data class Copied(val asset: ChatGptImportAsset) : ChatGptPrivateCopyResult; data class Failed(val failure: ChatGptImportFailure) : ChatGptPrivateCopyResult }
interface ChatGptPrivateAssetStore { fun copy(request: ChatGptPrivateCopyRequest): ChatGptPrivateCopyResult; fun read(storageKey: String): ByteArray? }
interface ChatGptImportTaskRepository { fun save(task: ChatGptImportTask): ChatGptImportTask; fun find(id: ChatGptImportTaskId): ChatGptImportTask?; fun list(): List<ChatGptImportTask> }

sealed interface ChatGptParseResult { data class Parsed(val items: List<ChatGptImportItem>) : ChatGptParseResult; data class Rejected(val failure: ChatGptImportFailure) : ChatGptParseResult }

class ChatGptExportJsonAdapter {
    fun parse(bytes: ByteArray): ChatGptParseResult = try {
        if (bytes.size.toLong() !in 1..CHATGPT_EXPORT_MAX_BYTES) reject(if (bytes.isEmpty()) ChatGptImportFailure.EMPTY_EXPORT else ChatGptImportFailure.TOO_LARGE)
        val root = StrictJsonDocument.parseUtf8(bytes, CHATGPT_EXPORT_MAX_DEPTH, CHATGPT_EXPORT_MAX_TEXT_CODE_POINTS) as? StrictJsonValue.Arr ?: reject(ChatGptImportFailure.ROOT_NOT_ARRAY)
        if (root.values.isEmpty()) reject(ChatGptImportFailure.EMPTY_EXPORT); if (root.values.size > CHATGPT_EXPORT_MAX_CONVERSATIONS) reject(ChatGptImportFailure.TOO_MANY_CONVERSATIONS)
        // A syntactically valid export is a package: one unsafe conversation must remain a visible
        // item-level failure and never erase independently confirmable neighbours.
        ChatGptParseResult.Parsed(root.values.mapIndexed { index, value ->
            try { parseConversation(index, value as? StrictJsonValue.Obj ?: reject(ChatGptImportFailure.INVALID_CONVERSATION)) }
            catch (e: ChatGptRejected) { ChatGptImportItem(ChatGptImportItemId.new(), ChatGptImportTaskId("unassigned"), index, null, ChatGptImportItemStatus.FAILED, e.failure) }
        })
    } catch (e: StrictJsonRejected) { ChatGptParseResult.Rejected(e.failure.asChatGptFailure()) } catch (e: ChatGptRejected) { ChatGptParseResult.Rejected(e.failure) }

    private fun parseConversation(index: Int, obj: StrictJsonValue.Obj): ChatGptImportItem {
        val id = obj.requiredString("id").safeId(); val title = obj.requiredString("title").trim().takeIf(String::isNotEmpty) ?: "无标题对话"
        val created = obj.timestamp("create_time"); val updated = obj.timestamp("update_time")
        val mapping = obj.requiredObj("mapping"); if (mapping.fields.size !in 1..CHATGPT_EXPORT_MAX_MAPPING_NODES) reject(ChatGptImportFailure.TOO_MANY_NODES)
        val nodes = mapping.fields.map { (sourceId, value) -> sourceId.safeId(); sourceId to parseNode(sourceId, value as? StrictJsonValue.Obj ?: reject(ChatGptImportFailure.INVALID_CONVERSATION)) }.toMap()
        // 2026 ZIP evidence omits redundant `children` while retaining a complete parent tree.
        // Reconstruct only from in-document parents; an explicit children array remains validated.
        val normalized = nodes.mapValues { (source, node) -> node.copy(children = node.children ?: nodes.filterValues { it.parent == source }.keys.toList()) }
        val roots = normalized.filterValues { it.parent == null }.keys; if (roots.size != 1) reject(ChatGptImportFailure.INVALID_TREE)
        normalized.forEach { (source, node) -> val children = requireNotNull(node.children); if (node.parent != null && node.parent !in normalized) reject(ChatGptImportFailure.INVALID_TREE); if (children.any { it !in normalized } || children.distinct().size != children.size) reject(ChatGptImportFailure.INVALID_TREE); if (children.any { normalized[it]?.parent != source }) reject(ChatGptImportFailure.INVALID_TREE) }
        val ordered = traverse(roots.single(), normalized)
        val rawMessages = ordered.mapIndexed { position, source -> source to nodeToMessage(source, normalized.getValue(source), position, created) }
        val importableSourceIds = rawMessages.mapNotNull { (source, message) -> message?.let { source } }.toSet()
        fun nearestImportableParent(sourceId: String?): String? {
            var current = sourceId; val seen = mutableSetOf<String>()
            while (current != null && seen.add(current)) {
                if (current in importableSourceIds) return current
                current = normalized[current]?.parent
            }
            return null
        }
        // Provider trees may contain structural nodes with no role/text.  They are not rendered
        // messages, so their descendants must attach to the nearest renderable ancestor rather
        // than becoming accidental second roots at the Room MessageTree boundary.
        val messages = rawMessages.mapNotNull { (_, message) -> message?.copy(parentSourceId = nearestImportableParent(message.parentSourceId)) }
        val candidate = ChatGptImportCandidate(id, title.take(120), created, updated, messages, chatGptImportContentHash(id, messages))
        if (candidate.messages.isEmpty()) reject(ChatGptImportFailure.EMPTY_CONTENT)
        return ChatGptImportItem(ChatGptImportItemId.new(), ChatGptImportTaskId("unassigned"), index, candidate)
    }
    private data class Node(val parent: String?, val children: List<String>?, val role: String?, val parts: List<StrictJsonValue>?, val createdAt: Instant?, val model: String?)
    private fun parseNode(sourceId: String, obj: StrictJsonValue.Obj): Node { val parent = obj.optionalString("parent")?.safeId(); val children = when (val raw = obj.fields["children"]) { null -> null; is StrictJsonValue.Arr -> raw.values.map { (it as? StrictJsonValue.Str)?.value?.safeId() ?: reject(ChatGptImportFailure.INVALID_TREE) }; else -> reject(ChatGptImportFailure.INVALID_TREE) }; val message = obj.fields["message"] as? StrictJsonValue.Obj ?: return Node(parent, children, null, null, null, null); val role = ((message.fields["author"] as? StrictJsonValue.Obj)?.fields?.get("role") as? StrictJsonValue.Str)?.value; val parts = ((message.fields["content"] as? StrictJsonValue.Obj)?.fields?.get("parts") as? StrictJsonValue.Arr)?.values; val time = (message.fields["create_time"] as? StrictJsonValue.Num)?.lexical?.toDoubleOrNull()?.let { Instant.ofEpochMilli((it * 1000).toLong()) }; val model = ((message.fields["metadata"] as? StrictJsonValue.Obj)?.fields?.get("model_slug") as? StrictJsonValue.Str)?.value?.take(120); return Node(parent, children, role, parts, time, model) }
    private fun traverse(root: String, nodes: Map<String, Node>): List<String> { val seen = linkedSetOf<String>(); fun visit(id: String) { if (!seen.add(id)) reject(ChatGptImportFailure.INVALID_TREE); requireNotNull(nodes.getValue(id).children).forEach(::visit) }; visit(root); if (seen.size != nodes.size) reject(ChatGptImportFailure.INVALID_TREE); return seen.toList() }
    private fun nodeToMessage(source: String, node: Node, position: Int, fallbackTime: Instant): ChatGptImportMessage? {
        val role = when (node.role) {
            "user" -> MessageRole.USER; "assistant" -> MessageRole.ASSISTANT; "tool" -> MessageRole.TOOL
            null -> return null; else -> reject(ChatGptImportFailure.UNSUPPORTED_ROLE)
        }
        // Official exports interleave ordinary text with multimodal objects and private
        // thought/reasoning records.  Keep only explicit string parts; a non-text node is skipped
        // locally rather than causing its whole conversation to disappear or exposing hidden data.
        val text = node.parts.orEmpty().filterIsInstance<StrictJsonValue.Str>().joinToString("\n") { it.value }.trim()
        if (text.isEmpty()) return null
        return ChatGptImportMessage(source, node.parent, position, role, text, node.createdAt ?: fallbackTime, node.model)
    }
    private fun StrictJsonValue.Obj.requiredString(key: String) = (fields[key] as? StrictJsonValue.Str)?.value ?: reject(ChatGptImportFailure.INVALID_CONVERSATION)
    private fun StrictJsonValue.Obj.optionalString(key: String) = when (val value = fields[key]) { null, StrictJsonValue.Null -> null; is StrictJsonValue.Str -> value.value; else -> reject(ChatGptImportFailure.INVALID_CONVERSATION) }
    private fun StrictJsonValue.Obj.requiredObj(key: String) = fields[key] as? StrictJsonValue.Obj ?: reject(ChatGptImportFailure.INVALID_CONVERSATION)
    private fun StrictJsonValue.Obj.timestamp(key: String): Instant = ((fields[key] as? StrictJsonValue.Num)?.lexical ?: (fields[key] as? StrictJsonValue.Str)?.value)?.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }?.let { Instant.ofEpochMilli((it * 1000).toLong()) } ?: reject(ChatGptImportFailure.INVALID_TIME)
    private fun String.safeId(): String = takeIf { matches(Regex("[A-Za-z0-9._:-]{1,200}")) } ?: reject(ChatGptImportFailure.INVALID_CONVERSATION)
    private fun reject(failure: ChatGptImportFailure): Nothing = throw ChatGptRejected(failure)
}

/**
 * The use case owns recovery and confirmation semantics.  Its commit port is deliberately
 * separate from ConversationRepository so Android/Desktop can make the conversation, receipt,
 * and provenance rows a single transaction without giving an adapter a DAO or database handle.
 */
data class ChatGptImportedProvenance(val conversationId: ConversationId, val sourceConversationId: String, val packageHash: String, val contentHash: String, val importedAt: Instant, val adapterId: String = CHATGPT_EXPORT_ADAPTER_ID, val adapterVersion: Int = CHATGPT_EXPORT_ADAPTER_VERSION)
sealed interface ChatGptImportCommitResult { data class Created(val conversationId: ConversationId) : ChatGptImportCommitResult; data class Replayed(val conversationId: ConversationId) : ChatGptImportCommitResult; data object ConflictReimport : ChatGptImportCommitResult; data class Failed(val safeReason: String) : ChatGptImportCommitResult }
interface ChatGptConversationCommitStore { fun commit(candidate: ChatGptImportCandidate, packageHash: String, importedAt: Instant): ChatGptImportCommitResult }

class ManageChatGptExportImportUseCase(
    private val tasks: ChatGptImportTaskRepository,
    private val assets: ChatGptPrivateAssetStore,
    private val adapter: ChatGptExportJsonAdapter,
    private val commits: ChatGptConversationCommitStore,
    private val clock: Clock,
) {
    fun select(displayName: String, mimeType: String, bytes: ByteArray): ChatGptImportTask {
        val now = clock.instant(); val task = tasks.save(ChatGptImportTask(ChatGptImportTaskId.new(), ChatGptImportTaskStatus.SELECTED, null, createdAt = now, updatedAt = now))
        return when (val copied = assets.copy(ChatGptPrivateCopyRequest(task.id, displayName, mimeType, bytes))) {
            is ChatGptPrivateCopyResult.Failed -> tasks.save(task.copy(status = ChatGptImportTaskStatus.FAILED, failure = copied.failure, updatedAt = clock.instant()))
            is ChatGptPrivateCopyResult.Copied -> stage(task.copy(status = ChatGptImportTaskStatus.PRIVATE_COPIED, asset = copied.asset, updatedAt = clock.instant()))
        }
    }
    fun retry(id: ChatGptImportTaskId): ChatGptImportTask { val task = requireNotNull(tasks.find(id)); val bytes = task.asset?.let { assets.read(it.storageKey) } ?: return tasks.save(task.copy(status = ChatGptImportTaskStatus.FAILED, failure = ChatGptImportFailure.MISSING_PRIVATE_ASSET, retryCount = task.retryCount + 1, updatedAt = clock.instant())); return stage(task.copy(status = ChatGptImportTaskStatus.PRIVATE_COPIED, failure = null, retryCount = task.retryCount + 1, updatedAt = clock.instant()), bytes) }
    fun list(): List<ChatGptImportTask> = tasks.list()
    fun recoverInterrupted(): Int = tasks.list().filter { it.status == ChatGptImportTaskStatus.PARSING }.count { task -> tasks.save(task.copy(status = ChatGptImportTaskStatus.FAILED, failure = ChatGptImportFailure.INTERRUPTED, updatedAt = clock.instant())); true }
    fun cancel(id: ChatGptImportTaskId): ChatGptImportTask { val task = requireNotNull(tasks.find(id)); return if (task.status in setOf(ChatGptImportTaskStatus.COMPLETED, ChatGptImportTaskStatus.CANCELLED)) task else tasks.save(task.copy(status = ChatGptImportTaskStatus.CANCELLED, updatedAt = clock.instant())) }
    fun skip(taskId: ChatGptImportTaskId, itemId: ChatGptImportItemId): ChatGptImportTask = update(taskId, itemId) { it.copy(status = ChatGptImportItemStatus.SKIPPED, failure = null) }
    fun confirm(taskId: ChatGptImportTaskId, itemId: ChatGptImportItemId): ChatGptImportTask {
        val task = requireNotNull(tasks.find(taskId)); val item = task.items.firstOrNull { it.id == itemId } ?: error("missing ChatGPT item")
        if (task.status == ChatGptImportTaskStatus.CANCELLED || item.status != ChatGptImportItemStatus.PENDING_CONFIRMATION) return task
        val candidate = requireNotNull(item.candidate); val outcome = commits.commit(candidate, requireNotNull(task.asset).packageHash, clock.instant())
        return update(taskId, itemId) { when (outcome) { is ChatGptImportCommitResult.Created -> it.copy(status = ChatGptImportItemStatus.CONFIRMED, conversationId = outcome.conversationId); is ChatGptImportCommitResult.Replayed -> it.copy(status = ChatGptImportItemStatus.CONFIRMED, conversationId = outcome.conversationId); ChatGptImportCommitResult.ConflictReimport -> it.copy(status = ChatGptImportItemStatus.FAILED, failure = ChatGptImportFailure.CONFLICT_REIMPORT); is ChatGptImportCommitResult.Failed -> it.copy(status = ChatGptImportItemStatus.FAILED, failure = ChatGptImportFailure.INVALID_CONVERSATION) } }
    }
    private fun stage(task: ChatGptImportTask, supplied: ByteArray? = null): ChatGptImportTask { val parsing = tasks.save(task.copy(status = ChatGptImportTaskStatus.PARSING, updatedAt = clock.instant())); val bytes = supplied ?: parsing.asset?.let { assets.read(it.storageKey) } ?: return tasks.save(parsing.copy(status = ChatGptImportTaskStatus.FAILED, failure = ChatGptImportFailure.MISSING_PRIVATE_ASSET, updatedAt = clock.instant())); return when (val result = adapter.parse(bytes)) { is ChatGptParseResult.Rejected -> tasks.save(parsing.copy(status = ChatGptImportTaskStatus.FAILED, failure = result.failure, updatedAt = clock.instant())); is ChatGptParseResult.Parsed -> commitSelectedCandidates(tasks.save(parsing.copy(status = ChatGptImportTaskStatus.AWAITING_CONFIRMATION, items = result.items.map { it.copy(taskId = parsing.id) }, updatedAt = clock.instant()))) } }
    /** Picker selection is the import command: each valid candidate keeps its own atomic receipt. */
    private fun commitSelectedCandidates(task: ChatGptImportTask): ChatGptImportTask = task.items
        .filter { it.status == ChatGptImportItemStatus.PENDING_CONFIRMATION && it.candidate != null }
        .fold(task) { _, item -> confirm(task.id, item.id) }
    private fun update(taskId: ChatGptImportTaskId, itemId: ChatGptImportItemId, mutate: (ChatGptImportItem) -> ChatGptImportItem): ChatGptImportTask { val task = requireNotNull(tasks.find(taskId)); val next = task.copy(items = task.items.map { if (it.id == itemId) mutate(it) else it }, updatedAt = clock.instant()); val status = when { next.items.all { it.status in setOf(ChatGptImportItemStatus.CONFIRMED, ChatGptImportItemStatus.SKIPPED) } -> ChatGptImportTaskStatus.COMPLETED; next.items.any { it.status != ChatGptImportItemStatus.PENDING_CONFIRMATION } -> ChatGptImportTaskStatus.PARTIALLY_COMPLETED; else -> ChatGptImportTaskStatus.AWAITING_CONFIRMATION }; return tasks.save(next.copy(status = status)) }
}

private class ChatGptRejected(val failure: ChatGptImportFailure) : RuntimeException()
private fun StrictJsonFailure.asChatGptFailure() = when (this) { StrictJsonFailure.INVALID_UTF8 -> ChatGptImportFailure.INVALID_UTF8; StrictJsonFailure.DUPLICATE_KEY -> ChatGptImportFailure.DUPLICATE_KEY; StrictJsonFailure.TOO_DEEP -> ChatGptImportFailure.TOO_DEEP; StrictJsonFailure.STRING_TOO_LONG -> ChatGptImportFailure.STRING_TOO_LONG; StrictJsonFailure.MALFORMED -> ChatGptImportFailure.MALFORMED_JSON }
private fun String.sha256() = MessageDigest.getInstance("SHA-256").digest(toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

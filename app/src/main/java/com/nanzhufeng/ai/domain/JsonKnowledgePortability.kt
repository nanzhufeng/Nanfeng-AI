package com.nanzhufeng.ai.domain

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Instant
import java.util.UUID

/** P4-L is a deliberately separate, strict JSON adapter. JSON strings are inert data, never instructions. */
const val JSON_KNOWLEDGE_FORMAT = "nfai.knowledge.json"
const val JSON_KNOWLEDGE_SCHEMA_VERSION = 1
const val JSON_KNOWLEDGE_MAX_BYTES = 768 * 1024L
const val JSON_KNOWLEDGE_MAX_ITEMS = 100
const val JSON_KNOWLEDGE_MAX_DEPTH = 24
const val JSON_KNOWLEDGE_MAX_STRING = 12_000

@JvmInline value class JsonKnowledgeTaskId(val value: String) { companion object { fun new() = JsonKnowledgeTaskId(UUID.randomUUID().toString()) } }
@JvmInline value class JsonKnowledgeImportItemId(val value: String) { companion object { fun new() = JsonKnowledgeImportItemId(UUID.randomUUID().toString()) } }
enum class JsonKnowledgeTaskStatus { SELECTED, PRIVATE_COPIED, PARSING, AWAITING_CONFIRMATION, PARTIALLY_COMPLETED, COMPLETED, FAILED, CANCELLED }
enum class JsonKnowledgeItemStatus { PENDING_CONFIRMATION, CONFIRMED, SKIPPED, FAILED }
enum class JsonKnowledgeFailure { NOT_JSON, TOO_LARGE, INVALID_UTF8, MALFORMED_JSON, DUPLICATE_KEY, TOO_DEEP, UNKNOWN_FORMAT, UNKNOWN_VERSION, UNKNOWN_FIELD, TYPE_MISMATCH, EMPTY_ITEMS, TOO_MANY_ITEMS, ITEM_INVALID, STRING_TOO_LONG, UNSAFE_METADATA, HIGH_SENSITIVITY, PRIVATE_COPY_FAILED, MISSING_PRIVATE_ASSET, INTERRUPTED, KNOWLEDGE_REJECTED }

data class JsonKnowledgeAsset(val storageKey: String, val mimeType: String, val displayName: String, val byteCount: Long, val sha256: String, val adapterVersion: Int = JSON_KNOWLEDGE_SCHEMA_VERSION)
data class JsonKnowledgeImportItem(val id: JsonKnowledgeImportItemId, val taskId: JsonKnowledgeTaskId, val ordinal: Int, val sourceStableId: String, val title: String, val body: String, val tags: Set<String>, val sourceSummary: String, val requestedScope: KnowledgeScope, val status: JsonKnowledgeItemStatus = JsonKnowledgeItemStatus.PENDING_CONFIRMATION, val failure: JsonKnowledgeFailure? = null, val knowledgeId: KnowledgeItemId? = null)
data class JsonKnowledgeTask(val id: JsonKnowledgeTaskId, val status: JsonKnowledgeTaskStatus, val asset: JsonKnowledgeAsset?, val failure: JsonKnowledgeFailure? = null, val retryCount: Int = 0, val createdAt: Instant, val updatedAt: Instant, val items: List<JsonKnowledgeImportItem> = emptyList()) { val completedCount get() = items.count { it.status == JsonKnowledgeItemStatus.CONFIRMED }; val failedCount get() = items.count { it.status == JsonKnowledgeItemStatus.FAILED } }
data class JsonKnowledgePrivateCopyRequest(val taskId: JsonKnowledgeTaskId, val displayName: String, val mimeType: String, val bytes: ByteArray)
sealed interface JsonKnowledgePrivateCopyResult { data class Copied(val asset: JsonKnowledgeAsset) : JsonKnowledgePrivateCopyResult; data class Failed(val reason: JsonKnowledgeFailure) : JsonKnowledgePrivateCopyResult }
interface JsonKnowledgePrivateAssetStore { fun copy(request: JsonKnowledgePrivateCopyRequest): JsonKnowledgePrivateCopyResult; fun read(storageKey: String): ByteArray? }
interface JsonKnowledgeTaskRepository { fun save(task: JsonKnowledgeTask): JsonKnowledgeTask; fun find(id: JsonKnowledgeTaskId): JsonKnowledgeTask?; fun list(): List<JsonKnowledgeTask> }
data class JsonKnowledgeExportManifest(val format: String = JSON_KNOWLEDGE_FORMAT, val schemaVersion: Int = JSON_KNOWLEDGE_SCHEMA_VERSION, val exportedAt: Instant, val itemCount: Int, val fidelity: String = "title,body,tags,scope,source safe metadata; imported IDs are remapped; excludes URI,path,key,prompt,provider payload,attachments,relationships,memory,context and temporary selections")
data class JsonKnowledgeExportResult(val fileName: String, val byteCount: Long, val sha256: String, val manifest: JsonKnowledgeExportManifest)
interface JsonKnowledgeExportStore { fun write(document: String, manifest: JsonKnowledgeExportManifest): JsonKnowledgeExportResult? }

private sealed interface StrictJson { data class Obj(val fields: LinkedHashMap<String, StrictJson>) : StrictJson; data class Arr(val values: List<StrictJson>) : StrictJson; data class Str(val value: String) : StrictJson; data object Null : StrictJson; data class Bool(val value: Boolean) : StrictJson }
private class JsonReject(val failure: JsonKnowledgeFailure) : RuntimeException()

/** Small strict parser: duplicate keys, numbers (including NaN/Infinity), excessive depth and unknown shapes cannot slip through. */
private class StrictJsonParser(private val source: String) {
    private var p = 0
    fun parse(): StrictJson { val value = value(0); ws(); if (p != source.length) fail(JsonKnowledgeFailure.MALFORMED_JSON); return value }
    private fun value(depth: Int): StrictJson { if (depth > JSON_KNOWLEDGE_MAX_DEPTH) fail(JsonKnowledgeFailure.TOO_DEEP); ws(); return when (peek()) { '{' -> obj(depth + 1); '[' -> arr(depth + 1); '"' -> StrictJson.Str(string()); 't' -> literal("true", StrictJson.Bool(true)); 'f' -> literal("false", StrictJson.Bool(false)); 'n' -> literal("null", StrictJson.Null); else -> fail(JsonKnowledgeFailure.MALFORMED_JSON) } }
    private fun obj(depth: Int): StrictJson.Obj { take('{'); ws(); val fields = linkedMapOf<String, StrictJson>(); if (consume('}')) return StrictJson.Obj(fields); while (true) { ws(); if (peek() != '"') fail(JsonKnowledgeFailure.MALFORMED_JSON); val key = string(); if (key.length > 120 || fields.containsKey(key)) fail(JsonKnowledgeFailure.DUPLICATE_KEY); fields[key] = valueAfterColon(depth); ws(); if (consume('}')) return StrictJson.Obj(fields); take(',') } }
    private fun valueAfterColon(depth: Int): StrictJson { ws(); take(':'); return value(depth) }
    private fun arr(depth: Int): StrictJson.Arr { take('['); ws(); val out = mutableListOf<StrictJson>(); if (consume(']')) return StrictJson.Arr(out); while (true) { out += value(depth); ws(); if (consume(']')) return StrictJson.Arr(out); take(',') } }
    private fun string(): String { take('"'); val out = StringBuilder(); while (p < source.length) { val c = source[p++]; when (c) { '"' -> return out.toString().also { if (it.codePointCount(0, it.length) > JSON_KNOWLEDGE_MAX_STRING) fail(JsonKnowledgeFailure.STRING_TOO_LONG) }; '\\' -> { val e = source.getOrNull(p++) ?: fail(JsonKnowledgeFailure.MALFORMED_JSON); out.append(when (e) { '"','\\','/' -> e; 'b' -> '\b'; 'f' -> '\u000c'; 'n' -> '\n'; 'r' -> '\r'; 't' -> '\t'; 'u' -> source.substring(p, (p + 4).also { if (it > source.length) fail(JsonKnowledgeFailure.MALFORMED_JSON) }).also { p += 4 }.toIntOrNull(16)?.toChar() ?: fail(JsonKnowledgeFailure.MALFORMED_JSON); else -> fail(JsonKnowledgeFailure.MALFORMED_JSON) }) }; else -> if (c.code < 0x20) fail(JsonKnowledgeFailure.MALFORMED_JSON) else out.append(c) } }; fail(JsonKnowledgeFailure.MALFORMED_JSON) }
    private fun literal(text: String, result: StrictJson): StrictJson { if (!source.startsWith(text, p)) fail(JsonKnowledgeFailure.MALFORMED_JSON); p += text.length; return result }
    private fun ws() { while (source.getOrNull(p)?.isWhitespace() == true) p++ }
    private fun take(c: Char) { if (!consume(c)) fail(JsonKnowledgeFailure.MALFORMED_JSON) }; private fun consume(c: Char) = if (peek() == c) { p++; true } else false; private fun peek(): Char? = source.getOrNull(p); private fun fail(reason: JsonKnowledgeFailure): Nothing = throw JsonReject(reason)
}

sealed interface JsonKnowledgeParseResult { data class Parsed(val items: List<JsonKnowledgeParsedItem>) : JsonKnowledgeParseResult; data class Rejected(val reason: JsonKnowledgeFailure) : JsonKnowledgeParseResult }
data class JsonKnowledgeParsedItem(val sourceStableId: String, val title: String, val body: String, val tags: Set<String>, val sourceSummary: String, val requestedScope: KnowledgeScope)

class JsonKnowledgeAdapter(private val knowledgeDomain: KnowledgeDomain) {
    fun parse(displayName: String, mimeType: String, bytes: ByteArray): JsonKnowledgeParseResult = try {
        if (!displayName.lowercase().matches(Regex(".+\\.json( \\(\\d+\\))?")) || mimeType.lowercase() !in setOf("application/json", "text/json", "application/octet-stream")) reject(JsonKnowledgeFailure.NOT_JSON)
        if (bytes.size.toLong() > JSON_KNOWLEDGE_MAX_BYTES) reject(JsonKnowledgeFailure.TOO_LARGE)
        val text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        val root = StrictJsonParser(text.removePrefix("\uFEFF")).parse() as? StrictJson.Obj ?: reject(JsonKnowledgeFailure.TYPE_MISMATCH)
        root.keysExactly(setOf("format", "schemaVersion", "exportedAt", "items"))
        if (root.str("format") != JSON_KNOWLEDGE_FORMAT) reject(JsonKnowledgeFailure.UNKNOWN_FORMAT)
        if (root.numberlessInt("schemaVersion") != JSON_KNOWLEDGE_SCHEMA_VERSION) reject(JsonKnowledgeFailure.UNKNOWN_VERSION)
        runCatching { Instant.parse(root.str("exportedAt")) }.getOrElse { reject(JsonKnowledgeFailure.TYPE_MISMATCH) }
        val rawItems = root.arr("items").values; if (rawItems.isEmpty()) reject(JsonKnowledgeFailure.EMPTY_ITEMS); if (rawItems.size > JSON_KNOWLEDGE_MAX_ITEMS) reject(JsonKnowledgeFailure.TOO_MANY_ITEMS)
        JsonKnowledgeParseResult.Parsed(rawItems.map { value -> item(value as? StrictJson.Obj ?: reject(JsonKnowledgeFailure.TYPE_MISMATCH)) })
    } catch (e: JsonReject) { JsonKnowledgeParseResult.Rejected(e.failure) } catch (_: Exception) { JsonKnowledgeParseResult.Rejected(JsonKnowledgeFailure.MALFORMED_JSON) }
    private fun item(o: StrictJson.Obj): JsonKnowledgeParsedItem {
        o.keysExactly(setOf("id", "title", "body", "tags", "scope", "projectRef", "revision", "hash", "source"))
        val id = o.str("id"); if (!id.matches(Regex("[A-Za-z0-9._:-]{1,160}"))) reject(JsonKnowledgeFailure.UNSAFE_METADATA)
        val title = o.str("title"); val body = o.str("body"); val tags = o.arr("tags").values.map { (it as? StrictJson.Str)?.value ?: reject(JsonKnowledgeFailure.TYPE_MISMATCH) }.toSet()
        val scope = when (o.str("scope")) { "GLOBAL" -> KnowledgeScope.GLOBAL; "PROJECT" -> KnowledgeScope.PROJECT; else -> reject(JsonKnowledgeFailure.ITEM_INVALID) }
        val projectRef = o["projectRef"]; if (scope == KnowledgeScope.PROJECT && projectRef !is StrictJson.Str) reject(JsonKnowledgeFailure.TYPE_MISMATCH)
        if (projectRef is StrictJson.Str && (!projectRef.value.matches(Regex("[A-Za-z0-9._:-]{1,160}")) || projectRef.value.contains("/"))) reject(JsonKnowledgeFailure.UNSAFE_METADATA)
        if (o.str("revision").toIntOrNull()?.let { it > 0 } != true || !o.str("hash").matches(Regex("[a-f0-9]{64}"))) reject(JsonKnowledgeFailure.ITEM_INVALID)
        val source = o.obj("source"); source.keysExactly(setOf("kind", "summary")); val summary = source.str("summary")
        if (summary.contains(Regex("(?i)(https?://|content:|file:|authorization|bearer|api[_ -]?key|provider[_ -]?payload)"))) reject(JsonKnowledgeFailure.UNSAFE_METADATA)
        if (MemoryDomain.sensitiveRejection("$title\n$body") != null) reject(JsonKnowledgeFailure.HIGH_SENSITIVITY)
        return try { knowledgeDomain.normalizedTitle(title); knowledgeDomain.normalizedBody(body); JsonKnowledgeParsedItem(id, title, body, knowledgeDomain.normalizedTags(tags), "${source.str("kind").take(40)}: ${summary.take(160)}", scope) } catch (_: Exception) { reject(JsonKnowledgeFailure.ITEM_INVALID) }
    }
    private fun reject(reason: JsonKnowledgeFailure): Nothing = throw JsonReject(reason)
}
private fun StrictJson.Obj.obj(key: String) = this[key] as? StrictJson.Obj ?: throw JsonReject(JsonKnowledgeFailure.TYPE_MISMATCH)
private fun StrictJson.Obj.arr(key: String) = this[key] as? StrictJson.Arr ?: throw JsonReject(JsonKnowledgeFailure.TYPE_MISMATCH)
private fun StrictJson.Obj.str(key: String) = (this[key] as? StrictJson.Str)?.value ?: throw JsonReject(JsonKnowledgeFailure.TYPE_MISMATCH)
private fun StrictJson.Obj.numberlessInt(key: String): Int = str(key).toIntOrNull() ?: throw JsonReject(JsonKnowledgeFailure.TYPE_MISMATCH)
private operator fun StrictJson.Obj.get(key: String) = fields[key]
private fun StrictJson.Obj.keysExactly(expected: Set<String>) { if (fields.keys != expected) throw JsonReject(JsonKnowledgeFailure.UNKNOWN_FIELD) }

class ManageJsonKnowledgeImportUseCase(private val tasks: JsonKnowledgeTaskRepository, private val assets: JsonKnowledgePrivateAssetStore, private val adapter: JsonKnowledgeAdapter, private val knowledge: ManageKnowledgeUseCase, private val clock: Clock) {
    fun select(displayName: String, mimeType: String, bytes: ByteArray): JsonKnowledgeTask { val now = clock.instant(); val selected = tasks.save(JsonKnowledgeTask(JsonKnowledgeTaskId.new(), JsonKnowledgeTaskStatus.SELECTED, null, createdAt = now, updatedAt = now)); return when (val copy = assets.copy(JsonKnowledgePrivateCopyRequest(selected.id, displayName, mimeType, bytes))) { is JsonKnowledgePrivateCopyResult.Failed -> tasks.save(selected.copy(status = JsonKnowledgeTaskStatus.FAILED, failure = copy.reason, updatedAt = clock.instant())); is JsonKnowledgePrivateCopyResult.Copied -> stage(selected.copy(status = JsonKnowledgeTaskStatus.PRIVATE_COPIED, asset = copy.asset, updatedAt = clock.instant())) } }
    fun retry(id: JsonKnowledgeTaskId): JsonKnowledgeTask { val task = tasks.find(id) ?: throw IllegalArgumentException("missing json task"); val bytes = task.asset?.let { assets.read(it.storageKey) } ?: return tasks.save(task.copy(status = JsonKnowledgeTaskStatus.FAILED, failure = JsonKnowledgeFailure.MISSING_PRIVATE_ASSET, retryCount = task.retryCount + 1, updatedAt = clock.instant())); return stage(task.copy(status = JsonKnowledgeTaskStatus.PRIVATE_COPIED, failure = null, retryCount = task.retryCount + 1, updatedAt = clock.instant()), bytes) }
    fun list() = tasks.list()
    fun recoverInterrupted(): Int = tasks.list().filter { it.status == JsonKnowledgeTaskStatus.PARSING }.count { task ->
        tasks.save(task.copy(status = JsonKnowledgeTaskStatus.FAILED, failure = JsonKnowledgeFailure.INTERRUPTED, updatedAt = clock.instant()))
        true
    }
    fun cancel(id: JsonKnowledgeTaskId) = tasks.find(id)?.let { task ->
        if (task.status in setOf(JsonKnowledgeTaskStatus.COMPLETED, JsonKnowledgeTaskStatus.CANCELLED)) task
        else tasks.save(task.copy(status = JsonKnowledgeTaskStatus.CANCELLED, updatedAt = clock.instant()))
    } ?: throw IllegalArgumentException("missing json task")
    fun skip(taskId: JsonKnowledgeTaskId, itemId: JsonKnowledgeImportItemId) = update(taskId, itemId) { it.copy(status = JsonKnowledgeItemStatus.SKIPPED, failure = null) }
    /** Imported IDs are never reused; PROJECT requests deterministically degrade to GLOBAL until an explicit user mapping exists. */
    fun confirm(taskId: JsonKnowledgeTaskId, itemId: JsonKnowledgeImportItemId, title: String, body: String, tags: Set<String>): JsonKnowledgeTask { val task = tasks.find(taskId) ?: throw IllegalArgumentException("missing json task"); val item = task.items.firstOrNull { it.id == itemId } ?: throw IllegalArgumentException("missing json item"); if (task.status == JsonKnowledgeTaskStatus.CANCELLED || item.status != JsonKnowledgeItemStatus.PENDING_CONFIRMATION) return task; val result = knowledge.execute(KnowledgeIntent(KnowledgeIntentId.new(), KnowledgeIntentAction.CREATE_JSON_IMPORT, KnowledgeItemId.new(), title, body, tags, KnowledgeScope.GLOBAL, null, "json:${taskId.value}:${item.sourceStableId}")); return update(taskId, itemId) { when (result) { is KnowledgeMutationResult.Applied -> it.copy(status = JsonKnowledgeItemStatus.CONFIRMED, knowledgeId = result.snapshot.item.id); is KnowledgeMutationResult.Replayed -> it.copy(status = JsonKnowledgeItemStatus.CONFIRMED, knowledgeId = result.snapshot.item.id); is KnowledgeMutationResult.Rejected -> it.copy(status = JsonKnowledgeItemStatus.FAILED, failure = if (result.code == KnowledgeRejectionCode.HIGH_SENSITIVITY) JsonKnowledgeFailure.HIGH_SENSITIVITY else JsonKnowledgeFailure.KNOWLEDGE_REJECTED) } } }
    private fun stage(task: JsonKnowledgeTask, supplied: ByteArray? = null): JsonKnowledgeTask { val parsing = tasks.save(task.copy(status = JsonKnowledgeTaskStatus.PARSING, updatedAt = clock.instant())); val bytes = supplied ?: parsing.asset?.let { assets.read(it.storageKey) } ?: return tasks.save(parsing.copy(status = JsonKnowledgeTaskStatus.FAILED, failure = JsonKnowledgeFailure.MISSING_PRIVATE_ASSET, updatedAt = clock.instant())); return when (val result = adapter.parse(requireNotNull(parsing.asset).displayName, parsing.asset.mimeType, bytes)) { is JsonKnowledgeParseResult.Rejected -> tasks.save(parsing.copy(status = JsonKnowledgeTaskStatus.FAILED, failure = result.reason, updatedAt = clock.instant())); is JsonKnowledgeParseResult.Parsed -> tasks.save(parsing.copy(status = JsonKnowledgeTaskStatus.AWAITING_CONFIRMATION, items = result.items.mapIndexed { i, x -> JsonKnowledgeImportItem(JsonKnowledgeImportItemId.new(), parsing.id, i, x.sourceStableId, x.title, x.body, x.tags, x.sourceSummary, x.requestedScope) }, updatedAt = clock.instant())) } }
    private fun update(taskId: JsonKnowledgeTaskId, itemId: JsonKnowledgeImportItemId, transform: (JsonKnowledgeImportItem) -> JsonKnowledgeImportItem): JsonKnowledgeTask { val current = tasks.find(taskId) ?: throw IllegalArgumentException("missing json task"); val next = current.copy(items = current.items.map { if (it.id == itemId) transform(it) else it }, updatedAt = clock.instant()); val status = when { next.items.all { it.status in setOf(JsonKnowledgeItemStatus.CONFIRMED, JsonKnowledgeItemStatus.SKIPPED) } -> JsonKnowledgeTaskStatus.COMPLETED; next.items.any { it.status in setOf(JsonKnowledgeItemStatus.CONFIRMED, JsonKnowledgeItemStatus.SKIPPED, JsonKnowledgeItemStatus.FAILED) } -> JsonKnowledgeTaskStatus.PARTIALLY_COMPLETED; else -> JsonKnowledgeTaskStatus.AWAITING_CONFIRMATION }; return tasks.save(next.copy(status = status)) }
}

class ExportJsonKnowledgeUseCase(private val repository: KnowledgeManagementRepository, private val store: JsonKnowledgeExportStore, private val clock: Clock) {
    fun execute(ids: Set<KnowledgeItemId>): JsonKnowledgeExportResult? { if (ids.isEmpty()) return null; val selected = repository.listSnapshots().filter { it.item.id in ids && it.lifecycle.status == KnowledgeStatus.ACTIVE }; if (selected.size != ids.size) return null; val manifest = JsonKnowledgeExportManifest(exportedAt = clock.instant(), itemCount = selected.size); val items = selected.sortedBy { it.item.id.value }.joinToString(",") { s -> val scope = s.lifecycle.scope.name; val project = s.lifecycle.projectId?.value?.let { "\"projectRef\":\"${escape(it)}\"," } ?: "\"projectRef\":null,"; "{\"id\":\"${escape(s.item.id.value)}\",\"title\":\"${escape(s.item.title)}\",\"body\":\"${escape(s.item.body)}\",\"tags\":[${s.lifecycle.tags.sorted().joinToString(",") { "\"${escape(it)}\"" }}],\"scope\":\"$scope\",$project\"revision\":\"${s.revisions.size}\",\"hash\":\"${s.lifecycle.contentHash}\",\"source\":{\"kind\":\"LOCAL_KNOWLEDGE\",\"summary\":\"confirmed local knowledge\"}}" }; val doc = "{\"format\":\"$JSON_KNOWLEDGE_FORMAT\",\"schemaVersion\":\"$JSON_KNOWLEDGE_SCHEMA_VERSION\",\"exportedAt\":\"${manifest.exportedAt}\",\"items\":[$items]}"; return store.write(doc, manifest) }
}
private fun escape(value: String) = value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")

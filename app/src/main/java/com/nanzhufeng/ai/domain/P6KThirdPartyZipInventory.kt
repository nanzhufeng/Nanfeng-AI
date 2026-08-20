package com.nanzhufeng.ai.domain

import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import java.util.zip.ZipFile

/** K0 only inventories an already private ZIP. It never extracts an entry or parses provider data. */
const val P6K_ZIP_MAX_ARCHIVE_BYTES = 2L * 1024 * 1024 * 1024
const val P6K_ZIP_MAX_ENTRIES = 5_000
// Verified ChatGPT export media includes a 246,982,191-byte entry.  The cap is
// deliberately just above that evidence; entries are still streamed, never extracted.
const val P6K_ZIP_MAX_ENTRY_BYTES = 256L * 1024 * 1024
const val P6K_ZIP_MAX_TOTAL_UNCOMPRESSED_BYTES = 4L * 1024 * 1024 * 1024
const val P6K_ZIP_MAX_COMPRESSION_RATIO = 100L

enum class ThirdPartyZipProvider { CHATGPT, CLAUDE }
enum class ThirdPartyZipInventoryFailure { NOT_ZIP, TOO_LARGE, UNREADABLE_ZIP, UNSAFE_PATH, DUPLICATE_ENTRY, TOO_MANY_ENTRIES, ENTRY_TOO_LARGE, TOTAL_TOO_LARGE, COMPRESSION_BOMB, UNKNOWN_MANIFEST_SCHEMA }
data class ThirdPartyZipEntryMetadata(val name: String, val uncompressedBytes: Long, val compressedBytes: Long, val mimeType: String)
sealed interface ThirdPartyZipInventoryResult {
    data class InventoriedUnsupported(val entries: List<ThirdPartyZipEntryMetadata>) : ThirdPartyZipInventoryResult
    data class Rejected(val failure: ThirdPartyZipInventoryFailure) : ThirdPartyZipInventoryResult
}

object ThirdPartyZipInventoryPolicy {
    fun inspect(privateZip: File): ThirdPartyZipInventoryResult {
        if (!privateZip.name.endsWith(".zip", ignoreCase = true) || !privateZip.isFile || privateZip.length() !in 1..P6K_ZIP_MAX_ARCHIVE_BYTES) return ThirdPartyZipInventoryResult.Rejected(if (privateZip.length() > P6K_ZIP_MAX_ARCHIVE_BYTES) ThirdPartyZipInventoryFailure.TOO_LARGE else ThirdPartyZipInventoryFailure.NOT_ZIP)
        return try {
            ZipFile(privateZip).use { zip ->
                val seen = linkedSetOf<String>(); var total = 0L; val entries = mutableListOf<ThirdPartyZipEntryMetadata>()
                val iterator = zip.entries()
                while (iterator.hasMoreElements()) {
                    if (entries.size >= P6K_ZIP_MAX_ENTRIES) return ThirdPartyZipInventoryResult.Rejected(ThirdPartyZipInventoryFailure.TOO_MANY_ENTRIES)
                    val entry = iterator.nextElement(); if (entry.isDirectory) continue
                    val normalized = entry.name.replace('\\', '/')
                    if (normalized.isBlank() || normalized.startsWith('/') || Regex("^[A-Za-z]:/").containsMatchIn(normalized) || normalized.split('/').any { it == ".." || it.isBlank() }) return ThirdPartyZipInventoryResult.Rejected(ThirdPartyZipInventoryFailure.UNSAFE_PATH)
                    if (!seen.add(normalized.lowercase())) return ThirdPartyZipInventoryResult.Rejected(ThirdPartyZipInventoryFailure.DUPLICATE_ENTRY)
                    val size = entry.size; val compressed = entry.compressedSize
                    if (size !in 0..P6K_ZIP_MAX_ENTRY_BYTES) return ThirdPartyZipInventoryResult.Rejected(ThirdPartyZipInventoryFailure.ENTRY_TOO_LARGE)
                    if (compressed < 0 || (size > 0 && compressed == 0L) || (compressed > 0 && size / compressed > P6K_ZIP_MAX_COMPRESSION_RATIO)) return ThirdPartyZipInventoryResult.Rejected(ThirdPartyZipInventoryFailure.COMPRESSION_BOMB)
                    total = try { Math.addExact(total, size) } catch (_: ArithmeticException) { return ThirdPartyZipInventoryResult.Rejected(ThirdPartyZipInventoryFailure.TOTAL_TOO_LARGE) }
                    if (total > P6K_ZIP_MAX_TOTAL_UNCOMPRESSED_BYTES) return ThirdPartyZipInventoryResult.Rejected(ThirdPartyZipInventoryFailure.TOTAL_TOO_LARGE)
                    entries += ThirdPartyZipEntryMetadata(normalized, size, compressed, inferMime(normalized))
                }
                // Provider version/manifest schemas are deliberately unregistered in K0.
                ThirdPartyZipInventoryResult.InventoriedUnsupported(entries)
            }
        } catch (_: Exception) { ThirdPartyZipInventoryResult.Rejected(ThirdPartyZipInventoryFailure.UNREADABLE_ZIP) }
    }

    private fun inferMime(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "json" -> "application/json"; "pdf" -> "application/pdf"; "png" -> "image/png"; "jpg", "jpeg" -> "image/jpeg"; "webp" -> "image/webp"; "mp4" -> "video/mp4"; "mp3" -> "audio/mpeg"; "wav" -> "audio/wav"; "txt", "md" -> "text/plain"; else -> "application/octet-stream"
    }
}

/**
 * K1 registers one deliberately narrow official OpenAI envelope: a root
 * conversations.json whose bytes still pass the pre-existing P6-H contract.
 * Other ZIP entries never acquire an ownership guess from a pathname.
 */
const val P6K_CHATGPT_ROOT_CONVERSATIONS_ENTRY = "conversations.json"
const val P6K_CHATGPT_ZIP_FORMAT_VERSION = "openai-chatgpt-conversations-json/v1"
const val P6K_CHATGPT_NUMBERED_ZIP_FORMAT_VERSION = "openai-chatgpt-numbered-conversations-json/v1"
const val P6K_CLAUDE_ZIP_FORMAT_VERSION = "anthropic-claude-conversations-json/v1"
const val P6K_CHATGPT_ZIP_MAX_CONVERSATIONS = 1_000

enum class P6KZipTaskStatus { WAITING_FORMAT_EVIDENCE, PARSING, AWAITING_CONFIRMATION, PARTIALLY_COMPLETED, COMPLETED, FAILED, CANCELLED }
enum class P6KZipItemStatus { PENDING_CONFIRMATION, CONFIRMED, SKIPPED, FAILED }
enum class P6KZipCandidateFailure { UNKNOWN_MANIFEST_SCHEMA, MISSING_CONVERSATIONS_JSON, CHATGPT_CONVERSATIONS_NOT_STRICT, CLAUDE_CONVERSATIONS_NOT_STRICT, MISSING_PRIVATE_ARCHIVE, INTERRUPTED, NOT_ZIP, TOO_LARGE, PRIVATE_COPY_FAILED, INVENTORY_REJECTED, CONFLICT_REIMPORT, COMMIT_FAILED }
/** A ZIP asset remains rejected until the user explicitly associates this exact candidate with one imported message. */
enum class P6KZipAssetRole { UNMAPPED_REJECTED, MANUAL_LINKED, MANUAL_LINK_FAILED }

@JvmInline value class P6KZipTaskId(val value: String) { companion object { fun new() = P6KZipTaskId(UUID.randomUUID().toString()) } }
@JvmInline value class P6KZipItemId(val value: String) { companion object { fun new() = P6KZipItemId(UUID.randomUUID().toString()) } }
data class P6KZipAssetCandidate(
    val entryName: String,
    val sha256: String,
    val byteCount: Long,
    val mimeType: String,
    val role: P6KZipAssetRole = P6KZipAssetRole.UNMAPPED_REJECTED,
    val sourceConversationId: String? = null,
    val sourceMessageId: String? = null,
    val linkedConversationId: String? = null,
    val linkedMessageId: String? = null,
    val attachmentId: String? = null,
    val failure: String? = null,
)

/** Settings receives no message body: only a user-pickable imported-message target. */
data class P6KZipManualLinkTarget(
    val conversationId: ConversationId,
    val messageId: MessageNodeId,
    val conversationTitle: String,
    val messageOrdinal: Int,
    val role: MessageRole,
    val createdAt: Instant,
)

sealed interface P6KZipManualAssetLinkResult { data object Linked : P6KZipManualAssetLinkResult; data object Replayed : P6KZipManualAssetLinkResult; data object Conflict : P6KZipManualAssetLinkResult; data object Failed : P6KZipManualAssetLinkResult }
interface P6KZipManualAssetLinkOwner {
    fun link(task: P6KZipImportTask, asset: P6KZipAssetCandidate, target: P6KZipManualLinkTarget, attachment: AttachmentReference, at: Instant): P6KZipManualAssetLinkResult
    fun revokeBatch(task: P6KZipImportTask, at: Instant): Boolean
}
/** Recovery-safe profile task status only; values belong to the native settings owner, never this queue. */
data class P6KImportedProfileCandidate(
    val status: String = "NO_SAFE_PROFILE_FIELDS",
    val mappedFieldCount: Int = 0,
    /** Present only while the strict ZIP mapper hands the safe value to the native settings owner.
     * The P6-K task journal persists status/count only. */
    val personalization: ThirdPartyProfilePersonalization? = null,
)
data class P6KZipImportItem(val id: P6KZipItemId, val ordinal: Int, val candidate: ChatGptImportCandidate?, val status: P6KZipItemStatus = P6KZipItemStatus.PENDING_CONFIRMATION, val failure: String? = null, val conversationId: ConversationId? = null)
data class P6KZipImportTask(
    val id: P6KZipTaskId,
    val provider: ThirdPartyZipProvider,
    val displayName: String,
    val byteCount: Long,
    val packageHash: String,
    val status: P6KZipTaskStatus,
    val failure: P6KZipCandidateFailure? = null,
    val formatVersion: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val items: List<P6KZipImportItem> = emptyList(),
    val assets: List<P6KZipAssetCandidate> = emptyList(),
    val profile: P6KImportedProfileCandidate = P6KImportedProfileCandidate(),
)

interface P6KZipImportTaskRepository {
    fun save(task: P6KZipImportTask): P6KZipImportTask
    fun find(id: P6KZipTaskId): P6KZipImportTask?
    fun list(): List<P6KZipImportTask>
    fun delete(id: P6KZipTaskId)
}

sealed interface P6KZipCommitResult { data class Created(val conversationId: ConversationId) : P6KZipCommitResult; data class Replayed(val conversationId: ConversationId) : P6KZipCommitResult; data object ConflictReimport : P6KZipCommitResult; data object Failed : P6KZipCommitResult }
interface P6KZipImportCommitStore {
    fun confirm(task: P6KZipImportTask, item: P6KZipImportItem, importedAt: Instant): P6KZipCommitResult
    fun skip(task: P6KZipImportTask, item: P6KZipImportItem, at: Instant): Boolean
    fun deleteBatch(task: P6KZipImportTask, at: Instant): Boolean
}

/** K6's only profile/settings write path.  It owns canonical values, receipt/provenance,
 * idempotency/conflict handling and batch revoke; ZIP adapters only supply the bounded IR. */
sealed interface P6KProfileCommitResult { data object Committed : P6KProfileCommitResult; data object Replayed : P6KProfileCommitResult; data object Conflict : P6KProfileCommitResult; data object Failed : P6KProfileCommitResult }
interface P6KProfilePersonalizationSettingsOwner {
    fun commit(task: P6KZipImportTask, value: ThirdPartyProfilePersonalization, at: Instant): P6KProfileCommitResult
    fun revokeBatch(task: P6KZipImportTask, at: Instant): Boolean
    fun read(): ThirdPartyProfilePersonalization?
}

/** K2 owns explicit per-item decisions only; no candidate can reach Conversation before this call. */
class ManageP6KChatGptZipImportUseCase(
    private val tasks: P6KZipImportTaskRepository,
    private val commits: P6KZipImportCommitStore,
    private val clock: java.time.Clock,
) {
    /** User's explicit ZIP selection is the import command; this only runs the already strict, local transaction per item. */
    fun importAll(taskId: P6KZipTaskId): P6KZipImportTask {
        var task = requireNotNull(tasks.find(taskId))
        if (task.provider !in setOf(ThirdPartyZipProvider.CHATGPT, ThirdPartyZipProvider.CLAUDE) || task.status !in setOf(P6KZipTaskStatus.AWAITING_CONFIRMATION, P6KZipTaskStatus.PARTIALLY_COMPLETED)) return task
        task.items.filter { it.status == P6KZipItemStatus.PENDING_CONFIRMATION && it.candidate != null }.forEach { item ->
            commits.confirm(task, item, clock.instant())
            task = requireNotNull(tasks.find(taskId))
        }
        return task
    }
    fun confirm(taskId: P6KZipTaskId, itemId: P6KZipItemId): P6KZipImportTask {
        val task = requireNotNull(tasks.find(taskId)); val item = task.items.firstOrNull { it.id == itemId } ?: error("missing P6-K ZIP item")
        if (task.provider != ThirdPartyZipProvider.CHATGPT || task.status !in setOf(P6KZipTaskStatus.AWAITING_CONFIRMATION, P6KZipTaskStatus.PARTIALLY_COMPLETED) || item.status != P6KZipItemStatus.PENDING_CONFIRMATION || item.candidate == null) return task
        commits.confirm(task, item, clock.instant())
        return requireNotNull(tasks.find(taskId))
    }
    fun skip(taskId: P6KZipTaskId, itemId: P6KZipItemId): P6KZipImportTask {
        val task = requireNotNull(tasks.find(taskId)); val item = task.items.firstOrNull { it.id == itemId } ?: error("missing P6-K ZIP item")
        if (task.provider != ThirdPartyZipProvider.CHATGPT || task.status !in setOf(P6KZipTaskStatus.AWAITING_CONFIRMATION, P6KZipTaskStatus.PARTIALLY_COMPLETED) || item.status != P6KZipItemStatus.PENDING_CONFIRMATION) return task
        commits.skip(task, item, clock.instant())
        return requireNotNull(tasks.find(taskId))
    }
    fun deleteBatch(taskId: P6KZipTaskId): Boolean {
        val task = requireNotNull(tasks.find(taskId))
        return commits.deleteBatch(task, clock.instant())
    }
}

sealed interface P6KChatGptZipMappingResult {
    data class Mapped(val formatVersion: String, val items: List<P6KZipImportItem>, val assets: List<P6KZipAssetCandidate>, val profile: P6KImportedProfileCandidate = P6KImportedProfileCandidate()) : P6KChatGptZipMappingResult
    data class Waiting(val failure: P6KZipCandidateFailure) : P6KChatGptZipMappingResult
    data class Rejected(val failure: P6KZipCandidateFailure) : P6KChatGptZipMappingResult
}

/** Streaming-only candidate mapper: it never extracts an entry, writes business data, or parses profile data. */
class P6KChatGptZipCandidateMapper(
    private val chatGptAdapter: ChatGptExportJsonAdapter = ChatGptExportJsonAdapter(),
    private val claudeAdapter: ClaudeExportJsonAdapter = ClaudeExportJsonAdapter(),
) {
    fun map(provider: ThirdPartyZipProvider, archive: File, inventory: List<ThirdPartyZipEntryMetadata>): P6KChatGptZipMappingResult {
        return when (provider) {
            ThirdPartyZipProvider.CHATGPT -> mapChatGpt(archive, inventory)
            ThirdPartyZipProvider.CLAUDE -> mapClaude(archive, inventory)
        }
    }

    private fun mapChatGpt(archive: File, inventory: List<ThirdPartyZipEntryMetadata>): P6KChatGptZipMappingResult {
        val jsonEntries = inventory.filter { it.name == P6K_CHATGPT_ROOT_CONVERSATIONS_ENTRY || P6K_CHATGPT_NUMBERED_CONVERSATIONS.matches(it.name) }
        if (jsonEntries.isEmpty()) return P6KChatGptZipMappingResult.Waiting(P6KZipCandidateFailure.MISSING_CONVERSATIONS_JSON)
        return try {
            ZipFile(archive).use { zip ->
                val items = jsonEntries.flatMap { entry ->
                    when (val parsed = chatGptAdapter.parse(zip.getInputStream(requireNotNull(zip.getEntry(entry.name))).use { it.readBounded(CHATGPT_EXPORT_MAX_BYTES) })) {
                        is ChatGptParseResult.Rejected -> return P6KChatGptZipMappingResult.Rejected(P6KZipCandidateFailure.CHATGPT_CONVERSATIONS_NOT_STRICT)
                        is ChatGptParseResult.Parsed -> parsed.items
                    }
                }.mapIndexed { ordinal, item -> P6KZipImportItem(P6KZipItemId.new(), ordinal, item.candidate, if (item.candidate == null) P6KZipItemStatus.FAILED else P6KZipItemStatus.PENDING_CONFIRMATION, item.failure?.name) }
                if (items.none { it.candidate != null } || items.size > P6K_CHATGPT_ZIP_MAX_CONVERSATIONS || items.mapNotNull { it.candidate?.sourceConversationId }.distinct().size != items.count { it.candidate != null }) return P6KChatGptZipMappingResult.Rejected(P6KZipCandidateFailure.CHATGPT_CONVERSATIONS_NOT_STRICT)
                val jsonEntryNames = jsonEntries.mapTo(mutableSetOf()) { it.name }
                val assets = inventory.filter { it.name !in jsonEntryNames && it.mimeType != "application/json" }.map { entry ->
                    val hash = zip.getInputStream(requireNotNull(zip.getEntry(entry.name))).use { it.sha256Bounded(entry.uncompressedBytes) }
                    P6KZipAssetCandidate(entry.name, hash, entry.uncompressedBytes, entry.mimeType)
                }
                P6KChatGptZipMappingResult.Mapped(if (jsonEntries.singleOrNull()?.name == P6K_CHATGPT_ROOT_CONVERSATIONS_ENTRY) P6K_CHATGPT_ZIP_FORMAT_VERSION else P6K_CHATGPT_NUMBERED_ZIP_FORMAT_VERSION, items, assets, mapProfile(zip))
            }
        } catch (_: Exception) { P6KChatGptZipMappingResult.Rejected(P6KZipCandidateFailure.CHATGPT_CONVERSATIONS_NOT_STRICT) }
    }

    private fun mapClaude(archive: File, inventory: List<ThirdPartyZipEntryMetadata>): P6KChatGptZipMappingResult {
        val entry = inventory.singleOrNull { it.name == P6K_CHATGPT_ROOT_CONVERSATIONS_ENTRY }
            ?: return P6KChatGptZipMappingResult.Waiting(P6KZipCandidateFailure.MISSING_CONVERSATIONS_JSON)
        return try {
            ZipFile(archive).use { zip ->
                val parsed = claudeAdapter.parse(zip.getInputStream(requireNotNull(zip.getEntry(entry.name))).use { it.readBounded(CLAUDE_EXPORT_MAX_BYTES) })
                val items = when (parsed) {
                    is ClaudeExportParseResult.Rejected -> return P6KChatGptZipMappingResult.Rejected(P6KZipCandidateFailure.CLAUDE_CONVERSATIONS_NOT_STRICT)
                    is ClaudeExportParseResult.Parsed -> parsed.items.map { item ->
                        val candidate = item.candidate?.let { it.toP6KCandidate() }
                        P6KZipImportItem(P6KZipItemId.new(), item.ordinal, candidate, if (candidate == null) P6KZipItemStatus.FAILED else P6KZipItemStatus.PENDING_CONFIRMATION, item.failure?.name)
                    }
                }
                if (items.none { it.candidate != null }) P6KChatGptZipMappingResult.Rejected(P6KZipCandidateFailure.CLAUDE_CONVERSATIONS_NOT_STRICT)
                else P6KChatGptZipMappingResult.Mapped(P6K_CLAUDE_ZIP_FORMAT_VERSION, items, emptyList(), mapProfile(zip))
            }
        } catch (_: Exception) { P6KChatGptZipMappingResult.Rejected(P6KZipCandidateFailure.CLAUDE_CONVERSATIONS_NOT_STRICT) }
    }

    private fun mapProfile(zip: ZipFile): P6KImportedProfileCandidate {
        // Only an explicit, separately versioned sidecar is eligible.  Unknown provider JSON is
        // not a profile schema and cannot block otherwise valid conversation candidates.
        val entry = zip.getEntry("profile-personalization-v1.json") ?: return P6KImportedProfileCandidate()
        return when (val result = zip.getInputStream(entry).use { ThirdPartyProfilePersonalizationSchema.parse(it.readBounded(16L * 1024L)) }) {
            is ThirdPartyProfilePersonalizationParseResult.Mapped -> P6KImportedProfileCandidate("MAPPED_PENDING_OWNER_COMMIT", result.value.mappedFieldCount, result.value)
            ThirdPartyProfilePersonalizationParseResult.NoSafeFields -> P6KImportedProfileCandidate()
            ThirdPartyProfilePersonalizationParseResult.Rejected -> P6KImportedProfileCandidate("REJECTED_UNSAFE_PROFILE_SCHEMA", 0)
        }
    }
}

private val P6K_CHATGPT_NUMBERED_CONVERSATIONS = Regex("^conversations(?:[-_]\\d+)?\\.json$")
private fun java.io.InputStream.readBounded(limit: Long): ByteArray {
    val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8 * 1024); var total = 0L
    while (true) { val count = read(buffer); if (count < 0) break; total += count; require(total <= limit); output.write(buffer, 0, count) }
    return output.toByteArray()
}
private fun java.io.InputStream.sha256Bounded(limit: Long): String {
    val digest = MessageDigest.getInstance("SHA-256"); val buffer = ByteArray(8 * 1024); var total = 0L
    while (true) { val count = read(buffer); if (count < 0) break; total += count; require(total <= limit); digest.update(buffer, 0, count) }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
private fun ByteArray.sha256() = MessageDigest.getInstance("SHA-256").digest(this).joinToString("") { "%02x".format(it) }
private fun ClaudeExportCandidate.toP6KCandidate() = ChatGptImportCandidate(
    sourceConversationId, title, createdAt, updatedAt,
    messages.map { ChatGptImportMessage(it.sourceId, it.parentSourceId, it.siblingPosition, it.role, it.text, it.createdAt, null) }, contentHash,
)

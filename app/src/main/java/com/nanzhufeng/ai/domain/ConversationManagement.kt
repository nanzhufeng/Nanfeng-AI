package com.nanzhufeng.ai.domain

import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.util.Locale
import java.util.UUID

@JvmInline
value class ConversationManagementIntentId(val value: String) {
    companion object { fun new() = ConversationManagementIntentId(UUID.randomUUID().toString()) }
}

enum class ConversationManagementAction { RENAME, PIN, UNPIN, FAVORITE, UNFAVORITE, ARCHIVE, UNARCHIVE, ASSIGN_PROJECT, REMOVE_PROJECT, SOFT_DELETE, RESTORE_DELETED }
enum class ConversationListScope { ACTIVE, FAVORITES, ARCHIVED, DELETED, ALL }

/** One owner for the full-screen local search ranges.  These are content kinds, not routes. */
enum class ConversationSearchCategory(val label: String) {
    ALL("全部"),
    TEXT("正文"),
    IMAGE("图片"),
    VIDEO("视频"),
    AUDIO("音频"),
    FILE("文件"),
}

/** Shared category owner for search indexing and every exact attachment-to-search route. */
fun conversationAttachmentSearchCategory(mimeType: String): ConversationSearchCategory = when {
    mimeType in CONVERSATION_ALLOWED_IMAGE_MIME_TYPES -> ConversationSearchCategory.IMAGE
    mimeType in CONVERSATION_ALLOWED_VIDEO_MIME_TYPES -> ConversationSearchCategory.VIDEO
    mimeType in CONVERSATION_ALLOWED_AUDIO_MIME_TYPES -> ConversationSearchCategory.AUDIO
    else -> ConversationSearchCategory.FILE
}

data class ConversationManagementIntent(
    val id: ConversationManagementIntentId,
    val conversationId: ConversationId,
    val action: ConversationManagementAction,
    val expectedRevision: Long,
    val title: String? = null,
    val projectId: String? = null,
)

sealed interface ConversationManagementResult {
    data class Applied(val snapshot: ConversationSnapshot) : ConversationManagementResult
    data class Replayed(val snapshot: ConversationSnapshot) : ConversationManagementResult
    data class Rejected(val reason: String) : ConversationManagementResult
}

/** P3-E's sole owner of title, pin/archive semantics and list ordering. */
class ConversationManagementDomain(private val clock: Clock) {
    fun apply(snapshot: ConversationSnapshot, intent: ConversationManagementIntent): ConversationSnapshot {
        require(snapshot.conversation.id == intent.conversationId) { "管理 intent 与会话不匹配。" }
        val before = snapshot.conversation
        require(before.revision == intent.expectedRevision) { "REVISION_CONFLICT：expected=${intent.expectedRevision}，actual=${before.revision}" }
        val now = clock.instant()
        val next = when (intent.action) {
            ConversationManagementAction.RENAME -> before.copy(
                title = normalizeTitle(requireNotNull(intent.title)),
                titleRevision = Math.addExact(before.titleRevision ?: 0L, 1L),
                autoTitlePending = false,
            )
            ConversationManagementAction.PIN -> {
                require(before.archivedAt == null) { "ARCHIVED_CONVERSATION_CANNOT_PIN" }
                before.copy(pinnedAt = before.pinnedAt ?: now)
            }
            ConversationManagementAction.UNPIN -> before.copy(pinnedAt = null)
            ConversationManagementAction.FAVORITE -> {
                require(before.archivedAt == null && before.deletedAt == null) { "INACTIVE_CONVERSATION_CANNOT_FAVORITE" }
                before.copy(favoritedAt = before.favoritedAt ?: now)
            }
            ConversationManagementAction.UNFAVORITE -> before.copy(favoritedAt = null)
            ConversationManagementAction.ARCHIVE -> before.copy(archivedAt = before.archivedAt ?: now, pinnedAt = null, favoritedAt = null)
            ConversationManagementAction.UNARCHIVE -> before.copy(archivedAt = null, pinnedAt = null)
            ConversationManagementAction.ASSIGN_PROJECT -> before.copy(projectId = requireNotNull(intent.projectId))
            ConversationManagementAction.REMOVE_PROJECT -> before.copy(projectId = null)
            ConversationManagementAction.SOFT_DELETE -> before.copy(deletedAt = before.deletedAt ?: now, archivedAt = now, pinnedAt = null, favoritedAt = null)
            ConversationManagementAction.RESTORE_DELETED -> before.copy(deletedAt = null, archivedAt = null, pinnedAt = null)
        }
        // Exact repeated intent is handled before this point. A fresh, semantically idempotent
        // action must not churn ordering or create a false update.
        return snapshot.copy(conversation = if (next == before) before else next.copy(updatedAt = now, revision = before.revision + 1))
    }

    fun listOrder(scope: ConversationListScope): Comparator<Conversation> = Comparator { left, right ->
        when (scope) {
            ConversationListScope.ACTIVE -> compareValuesBy(left, right,
                { if (it.pinnedAt == null) 1 else 0 },
                { it.updatedAt },
                { it.id.value },
            ).let { if (it != 0) it else 0 }.let { result ->
                // `updatedAt` needs descending order while the stable ID remains ascending.
                if (left.pinnedAt == null && right.pinnedAt != null) 1
                else if (left.pinnedAt != null && right.pinnedAt == null) -1
                else when {
                    left.updatedAt != right.updatedAt -> right.updatedAt.compareTo(left.updatedAt)
                    else -> left.id.value.compareTo(right.id.value)
                }
            }
            ConversationListScope.FAVORITES, ConversationListScope.ARCHIVED, ConversationListScope.DELETED, ConversationListScope.ALL -> when {
                left.updatedAt != right.updatedAt -> right.updatedAt.compareTo(left.updatedAt)
                else -> left.id.value.compareTo(right.id.value)
            }
        }
    }

    fun normalizeTitle(raw: String): String {
        val value = raw.trim()
        require(value.isNotEmpty()) { "会话标题不能为空或全为空白。" }
        require(value.codePointCount(0, value.length) <= MAX_TITLE_CODE_POINTS) { "会话标题不能超过 $MAX_TITLE_CODE_POINTS 个 Unicode 字符。" }
        require(value.none { it.isISOControl() }) { "会话标题不能包含控制字符。" }
        return value
    }

    companion object { const val MAX_TITLE_CODE_POINTS = 120 }
}

data class ConversationSearchHit(
    val conversationId: ConversationId,
    val messageNodeId: MessageNodeId?,
    val title: String,
    val snippet: String,
    val titleMatch: Boolean,
    /** The local message or indexed-text timestamp shown beside the searchable body. */
    val timestampEpochMs: Long = 0L,
    /** Exact UTF-8 bytes of the local searchable text, never an attachment or provider payload. */
    val byteCount: Long = 0L,
    /** Content-free import label; it never changes searchable text. */
    val importSource: ConversationImportSource? = null,
)

enum class ConversationImportSource(val searchLabel: String) {
    CHATGPT_JSON("从 ChatGPT JSON 导入"),
    CLAUDE_JSON("从 Claude JSON 导入"),
    CHATGPT_ZIP("从 ChatGPT ZIP 导入"),
}

/**
 * A safe attachment hit. It deliberately carries only the conversation/message location and
 * already-safe attachment reference; opening it first returns to the owning conversation.
 */
data class ConversationAttachmentSearchHit(
    val conversationId: ConversationId,
    val messageNodeId: MessageNodeId,
    val title: String,
    val attachment: ConversationAttachmentReference,
    /** Message-owned local timestamp; used only for month grouping in the search catalogue. */
    val timestampEpochMs: Long,
    /** Safe visible context when the keyword matched the attachment-owning message text. */
    val matchSnippet: String? = null,
)

/** A safe, local-only search row. It deliberately has no storage key, URI, path or provider fact. */
data class LocalSearchIndexRecord(
    val conversationId: ConversationId,
    val messageNodeId: MessageNodeId?,
    val title: String,
    val snippet: String,
    val contentKind: String,
    val timestampEpochMs: Long,
    val titleMatch: Boolean,
    val byteCount: Long,
)

/** Read-only projection: it deliberately receives only repository snapshots. */
class ConversationSearchProjection(private val management: ConversationManagementDomain) {
    /** Opening a search range browses the same local conversation catalogue that keyword search filters. */
    fun browse(snapshots: List<ConversationSnapshot>, scope: ConversationListScope): List<ConversationSearchHit> = snapshots.asSequence()
        .filter { snapshotMatchesScope(it, scope) }
        .sortedWith { left, right -> management.listOrder(scope).compare(left.conversation, right.conversation) }
        .map { snapshot ->
            val latestText = MessageTree(snapshot.conversation, snapshot.nodes).contextPath()
                .asReversed()
                .asSequence()
                .filter { it.role == MessageRole.USER || it.role == MessageRole.ASSISTANT }
                .flatMap { node -> node.content.filterIsInstance<ContentBlock.Text>().asReversed().asSequence().map { node to it.text } }
                .firstOrNull()
            val latestBody = latestText?.second?.trim().orEmpty()
            ConversationSearchHit(
                conversationId = snapshot.conversation.id,
                messageNodeId = null,
                title = snapshot.conversation.title,
                snippet = latestBody.take(120).ifBlank { "本地对话" },
                titleMatch = true,
                timestampEpochMs = latestText?.first?.createdAt?.toEpochMilli() ?: snapshot.conversation.updatedAt.toEpochMilli(),
                byteCount = latestBody.toByteArray(Charsets.UTF_8).size.toLong(),
            )
        }
        .take(MAX_RESULTS)
        .toList()

    fun search(snapshots: List<ConversationSnapshot>, query: String, scope: ConversationListScope): List<ConversationSearchHit> {
        val normalized = query.trim().lowercase(Locale.ROOT)
        if (normalized.isEmpty()) return emptyList()
        val conversationsById = snapshots.associate { it.conversation.id to it.conversation }
        return snapshots.asSequence()
            .filter { snapshotMatchesScope(it, scope) }
            .flatMap { snapshot -> hitsFor(snapshot, normalized).asSequence() }
            .sortedWith(compareByDescending<ConversationSearchHit> { it.titleMatch }
                .thenComparator { a, b -> management.listOrder(scope).compare(
                    conversationsById.getValue(a.conversationId),
                    conversationsById.getValue(b.conversationId),
                ) }
                .thenBy { it.messageNodeId?.value ?: "" })
            .take(MAX_RESULTS)
            .toList()
    }

    private fun hitsFor(snapshot: ConversationSnapshot, normalized: String): List<ConversationSearchHit> {
        val titleMatch = snapshot.conversation.title.lowercase(Locale.ROOT).contains(normalized)
        val titleHit = if (titleMatch) listOf(
            ConversationSearchHit(
                conversationId = snapshot.conversation.id,
                messageNodeId = null,
                title = snapshot.conversation.title,
                snippet = snapshot.conversation.title,
                titleMatch = true,
                timestampEpochMs = snapshot.conversation.updatedAt.toEpochMilli(),
                byteCount = snapshot.conversation.title.toByteArray(Charsets.UTF_8).size.toLong(),
            ),
        ) else emptyList()
        val messageHits = MessageTree(snapshot.conversation, snapshot.nodes).contextPath()
            .asSequence().filter { it.role == MessageRole.USER || it.role == MessageRole.ASSISTANT }
            .mapNotNull { node -> node.content.filterIsInstance<ContentBlock.Text>().joinToString("\n") { it.text }
                .takeIf { it.lowercase(Locale.ROOT).contains(normalized) }
                ?.let { text ->
                    ConversationSearchHit(
                        conversationId = snapshot.conversation.id,
                        messageNodeId = node.id,
                        title = snapshot.conversation.title,
                        snippet = snippet(text, normalized),
                        titleMatch = false,
                        timestampEpochMs = node.createdAt.toEpochMilli(),
                        byteCount = text.toByteArray(Charsets.UTF_8).size.toLong(),
                    )
                } }
            .toList()
        return titleHit + messageHits
    }

    private fun snippet(text: String, query: String): String {
        val index = text.lowercase(Locale.ROOT).indexOf(query).coerceAtLeast(0)
        val start = (index - 36).coerceAtLeast(0)
        return text.substring(start, (index + query.length + 84).coerceAtMost(text.length)).replace('\n', ' ')
    }
    private fun snapshotMatchesScope(snapshot: ConversationSnapshot, scope: ConversationListScope) = when (scope) {
        ConversationListScope.ACTIVE -> snapshot.conversation.archivedAt == null
        ConversationListScope.FAVORITES -> snapshot.conversation.archivedAt == null && snapshot.conversation.deletedAt == null && snapshot.conversation.favoritedAt != null
        ConversationListScope.ARCHIVED -> snapshot.conversation.archivedAt != null
        ConversationListScope.DELETED -> snapshot.conversation.deletedAt != null
        ConversationListScope.ALL -> true
    }
    companion object { const val MAX_RESULTS = 50 }
}

class ManageConversationUseCase(private val domain: ConversationManagementDomain, private val repository: ConversationManagementRepository) {
    fun execute(intent: ConversationManagementIntent): ConversationManagementResult = repository.applyManagement(intent, intent.fingerprint()) { snapshot -> domain.apply(snapshot, intent) }
}

class SearchConversationsUseCase(private val repository: ConversationSearchRepository, private val projection: ConversationSearchProjection) {
    fun browse(scope: ConversationListScope): List<ConversationSearchHit> {
        val hits = (repository as? OptimizedConversationSearchRepository)?.browseConversationHits(scope)
            ?: projection.browse(repository.snapshotsForSearch(), scope)
        val sourceReader = repository as? ImportedConversationProvenanceReader
        val importSources = sourceReader?.importSources(hits.map(ConversationSearchHit::conversationId)).orEmpty()
        return hits.map { hit -> hit.copy(importSource = importSources[hit.conversationId]) }
    }

    fun execute(query: String, scope: ConversationListScope): List<ConversationSearchHit> {
        val normalized = query.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)
        if (normalized.isBlank()) return emptyList()
        val indexed = (repository as? LocalSearchIndexRepository)?.searchLocalIndex(normalized, scope)
        val indexedHits = indexed.orEmpty().sortedWith(
            compareByDescending<LocalSearchIndexRecord> { it.titleMatch }
                .thenByDescending { it.timestampEpochMs }
                .thenBy { it.conversationId.value }
                .thenBy { it.messageNodeId?.value.orEmpty() },
        ).take(50).map {
            ConversationSearchHit(
                conversationId = it.conversationId,
                messageNodeId = it.messageNodeId,
                title = it.title,
                snippet = it.snippet,
                titleMatch = it.titleMatch,
                timestampEpochMs = it.timestampEpochMs,
                byteCount = it.byteCount,
            )
        }
        // Older app versions may leave a non-empty but partial acceleration index. Merge the
        // current persisted path so a valid hit never disappears merely because another row
        // still exists in that index.
        val currentPathHits = (repository as? OptimizedConversationSearchRepository)?.searchCurrentPathHits(query, scope)
            ?: projection.search(repository.snapshotsForSearch(), query, scope)
        val sourceReader = repository as? ImportedConversationProvenanceReader
        val hits = (indexedHits + currentPathHits)
            .distinctBy { "${it.conversationId.value}:${it.messageNodeId?.value.orEmpty()}" }
            .take(50)
        val importSources = sourceReader?.importSources(hits.map(ConversationSearchHit::conversationId)).orEmpty()
        return hits.map { hit -> hit.copy(importSource = importSources[hit.conversationId]) }
    }
}

/** Search may match a message outside the selected branch of an imported ChatGPT tree. */
fun conversationSearchLeafForMessage(snapshot: ConversationSnapshot, messageId: MessageNodeId): MessageNodeId? {
    val byId = snapshot.nodes.associateBy(MessageNode::id)
    if (messageId !in byId) return null
    val parentIds = snapshot.nodes.mapNotNull(MessageNode::parentMessageId).toSet()
    fun containsMessage(leaf: MessageNode): Boolean {
        var current: MessageNode? = leaf
        while (current != null) {
            if (current.id == messageId) return true
            current = current.parentMessageId?.let(byId::get)
        }
        return false
    }
    return snapshot.nodes.asSequence()
        .filter { it.id !in parentIds && containsMessage(it) }
        .maxWithOrNull(compareBy<MessageNode> { it.createdAt }.thenBy { it.id.value })
        ?.id
}

/**
 * Local-only attachment projection for the same six-range search state as conversation text.
 * It walks the visible branch of each local snapshot and never exposes a path, URI or bytes.
 */
class SearchConversationAttachmentsUseCase(private val repository: ConversationSearchRepository) {
    /** Category tabs are browsable immediately; keywords only narrow this complete local catalogue. */
    fun browse(
        category: ConversationSearchCategory,
        scope: ConversationListScope,
    ): List<ConversationAttachmentSearchHit> = (repository as? OptimizedConversationSearchRepository)
        ?.browseAttachmentHits(category, scope)
        ?: repository.snapshotsForSearch().asSequence()
        .filter { snapshot -> snapshotMatchesScope(snapshot, scope) }
        .flatMap { snapshot ->
            MessageTree(snapshot.conversation, snapshot.nodes).contextPath().asSequence()
                .flatMap { node -> node.content.filterIsInstance<ContentBlock.Attachment>().asSequence().map { node to it.attachment } }
                .filter { (_, attachment) -> category == ConversationSearchCategory.ALL || conversationAttachmentSearchCategory(attachment.mimeType) == category }
                .map { (node, attachment) -> ConversationAttachmentSearchHit(snapshot.conversation.id, node.id, snapshot.conversation.title, attachment, node.createdAt.toEpochMilli()) }
        }
        .distinctBy { "${it.conversationId.value}:${it.messageNodeId.value}:${it.attachment.id.value}" }
        .toList()

    fun execute(
        query: String,
        category: ConversationSearchCategory,
        scope: ConversationListScope,
    ): List<ConversationAttachmentSearchHit> {
        if (category == ConversationSearchCategory.TEXT) return emptyList()
        val normalized = query.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)
        if (normalized.isBlank()) return emptyList()
        (repository as? OptimizedConversationSearchRepository)?.let { optimized ->
            return optimized.searchAttachmentHits(normalized, category, scope)
        }
        return repository.snapshotsForSearch().asSequence()
            .filter { snapshot -> snapshotMatchesScope(snapshot, scope) }
            .flatMap { snapshot ->
                MessageTree(snapshot.conversation, snapshot.nodes).contextPath().asSequence()
                    .flatMap { node ->
                        val messageText = node.content.filterIsInstance<ContentBlock.Text>().joinToString("\n") { it.text }
                        node.content.filterIsInstance<ContentBlock.Attachment>().asSequence().map { Triple(node, it.attachment, messageText) }
                    }
                    .filter { (_, attachment, _) -> category == ConversationSearchCategory.ALL || conversationAttachmentSearchCategory(attachment.mimeType) == category }
                    .filter { (_, attachment, messageText) ->
                        snapshot.conversation.title.lowercase(Locale.ROOT).contains(normalized) ||
                            attachment.displayName.orEmpty().lowercase(Locale.ROOT).contains(normalized) ||
                            messageText.lowercase(Locale.ROOT).contains(normalized)
                    }
                    .map { (node, attachment, messageText) ->
                        ConversationAttachmentSearchHit(
                            conversationId = snapshot.conversation.id,
                            messageNodeId = node.id,
                            title = snapshot.conversation.title,
                            attachment = attachment,
                            timestampEpochMs = node.createdAt.toEpochMilli(),
                            matchSnippet = messageText.takeIf { it.lowercase(Locale.ROOT).contains(normalized) }
                                ?.let { snippet(it, normalized) },
                        )
                    }
            }
            .distinctBy { "${it.conversationId.value}:${it.messageNodeId.value}:${it.attachment.id.value}" }
            .toList()
    }

    private fun snippet(text: String, query: String): String {
        val index = text.lowercase(Locale.ROOT).indexOf(query).coerceAtLeast(0)
        val start = (index - 36).coerceAtLeast(0)
        return text.substring(start, (index + query.length + 84).coerceAtMost(text.length)).replace('\n', ' ')
    }

    private fun snapshotMatchesScope(snapshot: ConversationSnapshot, scope: ConversationListScope) = when (scope) {
        ConversationListScope.ACTIVE -> snapshot.conversation.archivedAt == null
        ConversationListScope.FAVORITES -> snapshot.conversation.archivedAt == null && snapshot.conversation.deletedAt == null && snapshot.conversation.favoritedAt != null
        ConversationListScope.ARCHIVED -> snapshot.conversation.archivedAt != null
        ConversationListScope.DELETED -> snapshot.conversation.deletedAt != null
        ConversationListScope.ALL -> true
    }

}

private fun ConversationManagementIntent.fingerprint(): String = MessageDigest.getInstance("SHA-256")
    .digest("${conversationId.value}|${action.name}|${expectedRevision}|${title ?: ""}|${projectId ?: ""}".toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(it) }

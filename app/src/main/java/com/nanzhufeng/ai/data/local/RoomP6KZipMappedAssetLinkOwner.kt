package com.nanzhufeng.ai.data.local

import com.nanzhufeng.ai.domain.AttachmentId
import com.nanzhufeng.ai.domain.AttachmentReference
import com.nanzhufeng.ai.domain.ContentBlock
import com.nanzhufeng.ai.domain.ConversationAttachmentReference
import com.nanzhufeng.ai.domain.ConversationId
import com.nanzhufeng.ai.domain.ConversationSnapshot
import com.nanzhufeng.ai.domain.MessageNode
import com.nanzhufeng.ai.domain.MessageNodeId
import com.nanzhufeng.ai.domain.P6KZipAssetMapping
import com.nanzhufeng.ai.domain.P6KZipAssetRecoveryFailureKind
import com.nanzhufeng.ai.domain.P6KZipAssetRecoveryJob
import com.nanzhufeng.ai.domain.P6KZipAssetRecoveryState
import com.nanzhufeng.ai.domain.P6KZipAssetRole
import com.nanzhufeng.ai.domain.P6KZipImportTask
import com.nanzhufeng.ai.domain.P6KZipMappedAssetLinkOwner
import com.nanzhufeng.ai.domain.P6KZipMappedAssetLinkSummary
import com.nanzhufeng.ai.domain.toConversationReference
import java.time.Instant
import java.util.concurrent.Callable

/**
 * Atomic source-message restoration. It reuses the normal Message Tree and private attachment
 * catalog, so imported content has the same renderer, preview, search and locate behavior as a
 * native attachment. Provider IDs remain only in the ZIP provenance tables.
 */
class RoomP6KZipMappedAssetLinkOwner(
    private val database: NanfengAiDatabase,
    private val conversations: RoomConversationRepository,
    private val onConversationFailure: (Throwable) -> Unit = {},
    private val beforeConversationCommit: (conversationNumber: Int, sourceConversationId: String) -> Unit = { _, _ -> },
) : P6KZipMappedAssetLinkOwner {
    override fun reconcile(
        task: P6KZipImportTask,
        mapping: P6KZipAssetMapping,
        privateAssets: Map<String, AttachmentReference>,
        at: Instant,
    ): P6KZipMappedAssetLinkSummary = reconcileInternal(task, mapping, privateAssets, null, at)

    override fun reconcileResumable(
        task: P6KZipImportTask,
        mapping: P6KZipAssetMapping,
        privateAssets: Map<String, AttachmentReference>,
        job: P6KZipAssetRecoveryJob,
        at: Instant,
    ): P6KZipMappedAssetLinkSummary = reconcileInternal(task, mapping, privateAssets, job, at)

    private fun reconcileInternal(
        task: P6KZipImportTask,
        mapping: P6KZipAssetMapping,
        privateAssets: Map<String, AttachmentReference>,
        initialJob: P6KZipAssetRecoveryJob?,
        at: Instant,
    ): P6KZipMappedAssetLinkSummary {
        var linked = 0
        var createdMessages = 0
        var failedConversations = 0
        var checkpoint = initialJob
        val recoveryConversations = mapping.conversations
            .filter { conversation -> conversation.currentPath.any { message -> message.entryNames.any(mapping.assets::containsKey) } }
        val processedBeforeRun = initialJob?.processedConversations ?: 0
        for ((offset, sourceConversation) in recoveryConversations.drop(processedBeforeRun).withIndex()) {
                val outcome = runCatching {
                    beforeConversationCommit(processedBeforeRun + offset + 1, sourceConversation.sourceConversationId)
                    database.runInTransaction(Callable {
                        val zipDao = database.p6kZipImportTaskDao()
                        val provenance = zipDao.provenanceForSource(sourceConversation.sourceConversationId).singleOrNull()
                            ?: error("ZIP_RECOVERY_PROVENANCE_MISSING")
                        val conversationId = ConversationId(provenance.conversationId)
                        val snapshot = conversations.findById(conversationId) ?: error("ZIP_RECOVERY_CONVERSATION_MISSING")
                        val storedCandidates = zipDao.assets(task.id.value).associateBy { it.entryName }
                        val attachmentDao = database.privateAttachmentAssetDao()
                        val effectiveByEntry = linkedMapOf<String, ConversationAttachmentReference>()

                        sourceConversation.currentPath.flatMap { it.entryNames }.distinct().forEach { entryName ->
                            val prepared = privateAssets[entryName] ?: return@forEach
                            val mapped = mapping.assets[entryName] ?: return@forEach
                            val stored = storedCandidates[entryName] ?: return@forEach
                            if (stored.sha256 != prepared.sha256 || stored.byteCount != prepared.byteCount) return@forEach
                            val existing = attachmentDao.findBySha256(requireNotNull(prepared.sha256))
                            val effective = existing?.toReference() ?: prepared.also { asset ->
                                attachmentDao.insert(
                                    PrivateAttachmentAssetEntity(
                                        attachmentId = asset.id.value,
                                        storageKey = asset.reference,
                                        mimeType = asset.mimeType,
                                        displayName = asset.displayName,
                                        byteCount = requireNotNull(asset.byteCount),
                                        sha256 = requireNotNull(asset.sha256),
                                    ),
                                )
                            }
                            effectiveByEntry[entryName] = effective.toConversationReference().copy(
                                mimeType = mapped.candidate.mimeType,
                                displayName = mapped.displayName,
                            )
                        }

                        val localBySource = zipDao.messageProvenanceForConversation(conversationId.value)
                            .associate { it.sourceMessageId to MessageNodeId(it.messageId) }
                            .toMutableMap()
                        storedCandidates.values.filter { it.sourceConversationId == sourceConversation.sourceConversationId && it.linkedMessageId != null }
                            .forEach { candidate -> candidate.sourceMessageId?.let { localBySource.putIfAbsent(it, MessageNodeId(requireNotNull(candidate.linkedMessageId))) } }

                        val nodes = snapshot.nodes.associateBy(MessageNode::id).toMutableMap()
                        var previousLocalId: MessageNodeId? = null
                        var newlyCreated = 0
                        sourceConversation.currentPath.forEach { sourceMessage ->
                            val availableEntries = sourceMessage.entryNames.filter(effectiveByEntry::containsKey)
                            var localId = localBySource[sourceMessage.sourceMessageId]
                            if (localId == null && availableEntries.isNotEmpty()) {
                                localId = MessageNodeId.new()
                                localBySource[sourceMessage.sourceMessageId] = localId
                            }
                            if (localId == null) return@forEach
                            val existing = nodes[localId]
                            val attachmentBlocks = availableEntries.map { ContentBlock.Attachment(requireNotNull(effectiveByEntry[it])) }
                            val content = if (existing == null) {
                                attachmentBlocks
                            } else {
                                existing.content + attachmentBlocks.filter { block ->
                                    existing.content.filterIsInstance<ContentBlock.Attachment>()
                                        .none {
                                            it.attachment.id == block.attachment.id ||
                                                it.attachment.sha256 == block.attachment.sha256
                                        }
                                }
                            }
                            if (content.isEmpty()) return@forEach
                            val parent = previousLocalId
                            val position = if (existing != null && existing.parentMessageId == parent) existing.siblingPosition else nextSiblingPosition(nodes.values, parent, localId)
                            val next = existing?.copy(parentMessageId = parent, siblingPosition = position, content = content)
                                ?: MessageNode(localId, conversationId, parent, position, sourceMessage.role, content, sourceMessage.createdAt).also { newlyCreated += 1 }
                            nodes[localId] = next
                            previousLocalId = localId
                        }
                        val leaf = selectLeaf(nodes.values, previousLocalId ?: error("ZIP_RECOVERY_PATH_MISSING"), snapshot.conversation.currentLeafMessageId)
                        val nextSnapshot = ConversationSnapshot(
                            snapshot.conversation.copy(currentLeafMessageId = leaf, revision = snapshot.conversation.revision + 1),
                            nodes.values.toList(),
                            snapshot.draft,
                        )
                        conversations.restoreMappedImportPathInExistingTransaction(nextSnapshot)

                        var newlyLinked = 0
                        sourceConversation.currentPath.forEach { sourceMessage ->
                            val localId = localBySource[sourceMessage.sourceMessageId] ?: return@forEach
                            sourceMessage.entryNames.distinct().forEach { entryName ->
                                val reference = effectiveByEntry[entryName] ?: return@forEach
                                val mapped = mapping.assets[entryName] ?: return@forEach
                                val stored = zipDao.asset(task.id.value, entryName) ?: return@forEach
                                val prior = zipDao.assetLinkReceipt(task.id.value, entryName)
                                if (prior == null) {
                                    zipDao.insertAssetLinkReceipt(
                                        P6KZipAssetLinkReceiptEntity(
                                            task.id.value,
                                            entryName,
                                            stored.sha256,
                                            reference.id.value,
                                            conversationId.value,
                                            localId.value,
                                            at.toEpochMilli(),
                                        ),
                                    )
                                    newlyLinked += 1
                                }
                                zipDao.upsertAssets(
                                    listOf(
                                        stored.copy(
                                            mimeType = mapped.candidate.mimeType,
                                            role = P6KZipAssetRole.SOURCE_MAPPED.name,
                                            sourceConversationId = sourceConversation.sourceConversationId,
                                            sourceMessageId = sourceMessage.sourceMessageId,
                                            linkedConversationId = conversationId.value,
                                            linkedMessageId = localId.value,
                                            attachmentId = reference.id.value,
                                            failure = null,
                                        ),
                                    ),
                                )
                            }
                        }
                        checkpoint?.let { current ->
                            val next = current.copy(
                                state = P6KZipAssetRecoveryState.LINKING,
                                linkedOccurrences = current.linkedOccurrences + newlyLinked,
                                processedConversations = current.processedConversations + 1,
                                failedConversations = 0,
                                lastFailureKind = null,
                                lastFailureAtMs = null,
                                updatedAtMs = at.toEpochMilli(),
                            )
                            zipDao.upsertAssetRecoveryJob(next.toEntity())
                            checkpoint = next
                        }
                        Outcome(newlyLinked, newlyCreated)
                    })
                }.getOrElse { error ->
                    failedConversations += 1
                    onConversationFailure(error)
                    checkpoint?.let { current ->
                        val failed = current.copy(
                            state = P6KZipAssetRecoveryState.PARTIAL,
                            failedConversations = current.failedConversations + 1,
                            lastFailureKind = P6KZipAssetRecoveryFailureKind.LINKING_FAILED,
                            lastFailureAtMs = at.toEpochMilli(),
                            updatedAtMs = at.toEpochMilli(),
                        )
                        database.p6kZipImportTaskDao().upsertAssetRecoveryJob(failed.toEntity())
                        checkpoint = failed
                    }
                    Outcome()
                }
                linked += outcome.linked
                createdMessages += outcome.createdMessages
                if (failedConversations > 0 && checkpoint != null) break
            }
        checkpoint?.takeIf { current ->
            failedConversations == 0 && current.processedConversations >= current.totalConversations
        }?.let { current ->
            val completed = current.copy(
                state = P6KZipAssetRecoveryState.COMPLETED,
                failedConversations = 0,
                lastFailureKind = null,
                lastFailureAtMs = null,
                updatedAtMs = at.toEpochMilli(),
            )
            database.p6kZipImportTaskDao().upsertAssetRecoveryJob(completed.toEntity())
        }
        val unresolved = database.p6kZipImportTaskDao().assets(task.id.value).count { it.attachmentId == null }
        return P6KZipMappedAssetLinkSummary(linked, createdMessages, unresolved, failedConversations)
    }

    private fun nextSiblingPosition(nodes: Collection<MessageNode>, parent: MessageNodeId?, current: MessageNodeId): Int =
        (nodes.asSequence().filter { it.id != current && it.parentMessageId == parent }.maxOfOrNull { it.siblingPosition } ?: -1) + 1

    /** The official current node can end in a non-renderable structural record. In that case the
     * last renderable source message may still own children from another preserved branch, so it
     * cannot itself become Conversation.currentLeafMessageId. Prefer the existing selected leaf
     * when it descends from the restored official path; otherwise choose a deterministic leaf
     * below that path rather than producing an invalid MessageTree. */
    private fun selectLeaf(nodes: Collection<MessageNode>, preferredAncestor: MessageNodeId, current: MessageNodeId?): MessageNodeId {
        val byId = nodes.associateBy(MessageNode::id)
        val parentIds = nodes.mapNotNull(MessageNode::parentMessageId).toSet()
        val leaves = nodes.filter { it.id !in parentIds }
        fun descendsFrom(node: MessageNode, ancestor: MessageNodeId): Boolean {
            var cursor: MessageNode? = node
            val seen = mutableSetOf<MessageNodeId>()
            while (cursor != null && seen.add(cursor.id)) {
                if (cursor.id == ancestor) return true
                cursor = cursor.parentMessageId?.let(byId::get)
            }
            return false
        }
        val preferredLeaves = leaves.filter { descendsFrom(it, preferredAncestor) }
        current?.let { selected -> preferredLeaves.firstOrNull { it.id == selected }?.let { return it.id } }
        return requireNotNull(
            preferredLeaves.maxWithOrNull(compareBy<MessageNode> { it.createdAt }.thenBy { it.id.value })
                ?: current?.let(byId::get)?.takeIf { it.id !in parentIds }
                ?: leaves.maxWithOrNull(compareBy<MessageNode> { it.createdAt }.thenBy { it.id.value }),
        ).id
    }

    private data class Outcome(val linked: Int = 0, val createdMessages: Int = 0)
}

private fun PrivateAttachmentAssetEntity.toReference() = AttachmentReference(
    reference = storageKey,
    mimeType = mimeType,
    displayName = displayName,
    id = AttachmentId(attachmentId),
    byteCount = byteCount,
    sha256 = sha256,
)

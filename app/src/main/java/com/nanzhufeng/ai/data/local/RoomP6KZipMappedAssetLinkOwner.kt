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
    private val identityLedger: RoomP6KImportIdentityLedger = RoomP6KImportIdentityLedger(database),
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
                        if (processedBeforeRun == 0 && offset == 0) {
                            // A newer mapping index is a complete ownership rebuild. Remove the
                            // previous index rows atomically with the first rebuilt conversation;
                            // attachment bytes remain protected by their normal message links.
                            zipDao.deleteAssetOccurrenceReceiptsForTask(task.id.value)
                            zipDao.deleteAssetOccurrencesForTask(task.id.value)
                            zipDao.deleteAssetCatalogForTask(task.id.value)
                        }
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
                            when (identityLedger.sourceAssetDecision(task, entryName, requireNotNull(prepared.sha256), requireNotNull(prepared.byteCount))) {
                                P6KAssetIdentityDecision.New -> if (existing != null) {
                                    identityLedger.mutateBatchReceipt(task, at) { receipt ->
                                        receipt.copy(reusedAssetBytes = receipt.reusedAssetBytes + requireNotNull(prepared.byteCount))
                                    }
                                }
                                P6KAssetIdentityDecision.Existing -> Unit
                                P6KAssetIdentityDecision.Conflict -> error("P6K_ASSET_IDENTITY_CONFLICT")
                            }
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
                            zipDao.upsertAssetCatalog(
                                P6KZipAssetCatalogEntity(
                                    taskId = task.id.value,
                                    entryName = entryName,
                                    sha256 = stored.sha256,
                                    byteCount = stored.byteCount,
                                    mimeType = mapped.candidate.mimeType,
                                    displayName = mapped.displayName,
                                    attachmentId = effective.id.value,
                                    missingInArchive = false,
                                    verificationState = "VERIFIED",
                                ),
                            )
                        }

                        val nodes = snapshot.nodes.associateBy(MessageNode::id).toMutableMap()
                        val desiredRoleBySource = sourceConversation.currentPath.associate { it.sourceMessageId to it.role }
                        val localBySource = zipDao.messageProvenanceForConversation(conversationId.value)
                            .associate { it.sourceMessageId to MessageNodeId(it.messageId) }
                            .toMutableMap()
                        storedCandidates.values.filter { it.sourceConversationId == sourceConversation.sourceConversationId && it.linkedMessageId != null }
                            .forEach { candidate -> candidate.sourceMessageId?.let { sourceMessageId ->
                                val linkedId = MessageNodeId(requireNotNull(candidate.linkedMessageId))
                                val linked = nodes[linkedId]
                                val desiredRole = desiredRoleBySource[sourceMessageId]
                                if (linked != null && desiredRole != null &&
                                    (linked.role == desiredRole || linked.content.all { it is ContentBlock.Attachment })
                                ) localBySource.putIfAbsent(sourceMessageId, linkedId)
                            } }

                        var previousLocalId: MessageNodeId? = null
                        var newlyCreated = 0
                        sourceConversation.currentPath.forEach { sourceMessage ->
                            val availableEntries = sourceMessage.entryNames.filter(effectiveByEntry::containsKey).filter { entryName ->
                                val stored = storedCandidates[entryName] ?: return@filter false
                                when (identityLedger.occurrenceDecision(
                                    task,
                                    sourceConversation.sourceConversationId,
                                    sourceMessage.sourceMessageId,
                                    entryName,
                                    stored.sha256,
                                    stored.byteCount,
                                )) {
                                    P6KOccurrenceIdentityDecision.New -> true
                                    P6KOccurrenceIdentityDecision.Existing,
                                    P6KOccurrenceIdentityDecision.UserDeleted -> false
                                    P6KOccurrenceIdentityDecision.Conflict -> error("P6K_ASSET_IDENTITY_CONFLICT")
                                }
                            }
                            var localId = localBySource[sourceMessage.sourceMessageId]
                            val linkedNode = localId?.let(nodes::get)
                            if (linkedNode != null && linkedNode.role != sourceMessage.role &&
                                linkedNode.content.any { it !is ContentBlock.Attachment }
                            ) localId = null
                            if (localId == null && availableEntries.isNotEmpty()) {
                                localId = MessageNodeId.new()
                                localBySource[sourceMessage.sourceMessageId] = localId
                            }
                            if (localId == null) return@forEach
                            availableEntries.forEach entryLoop@ { entryName ->
                                val staleId = storedCandidates[entryName]?.linkedMessageId?.let(::MessageNodeId)
                                if (staleId == null || staleId == localId) return@entryLoop
                                val stale = nodes[staleId] ?: return@entryLoop
                                val reference = requireNotNull(effectiveByEntry[entryName])
                                val pruned = stale.content.filterNot { block ->
                                    block is ContentBlock.Attachment &&
                                        (block.attachment.id == reference.id || block.attachment.sha256 == reference.sha256)
                                }
                                if (pruned.isNotEmpty()) nodes[staleId] = stale.copy(content = pruned)
                            }
                            val existing = nodes[localId]
                            val reclassifyAttachmentOnly = existing != null && existing.role != sourceMessage.role &&
                                existing.content.all { it is ContentBlock.Attachment }
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
                            val next = existing?.copy(
                                parentMessageId = parent,
                                siblingPosition = position,
                                role = if (reclassifyAttachmentOnly) sourceMessage.role else existing.role,
                                content = content,
                                createdAt = if (reclassifyAttachmentOnly) sourceMessage.createdAt else existing.createdAt,
                            )
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
                                when (identityLedger.occurrenceDecision(
                                    task,
                                    sourceConversation.sourceConversationId,
                                    sourceMessage.sourceMessageId,
                                    entryName,
                                    stored.sha256,
                                    stored.byteCount,
                                )) {
                                    P6KOccurrenceIdentityDecision.UserDeleted,
                                    P6KOccurrenceIdentityDecision.Existing -> return@forEach
                                    P6KOccurrenceIdentityDecision.Conflict -> error("P6K_ASSET_IDENTITY_CONFLICT")
                                    P6KOccurrenceIdentityDecision.New -> Unit
                                }
                                identityLedger.registerOccurrence(
                                    task,
                                    sourceConversation.sourceConversationId,
                                    sourceMessage.sourceMessageId,
                                    entryName,
                                    stored.sha256,
                                    stored.byteCount,
                                    conversationId,
                                    localId,
                                    reference.id.value,
                                    at,
                                )
                                zipDao.insertAssetOccurrence(
                                    P6KZipAssetOccurrenceEntity(
                                        task.id.value,
                                        entryName,
                                        sourceConversation.sourceConversationId,
                                        sourceMessage.sourceMessageId,
                                    ),
                                )
                                val prior = zipDao.assetOccurrenceReceipt(
                                    task.id.value,
                                    entryName,
                                    sourceConversation.sourceConversationId,
                                    sourceMessage.sourceMessageId,
                                )
                                if (prior == null) {
                                    zipDao.insertAssetOccurrenceReceipt(
                                        P6KZipAssetOccurrenceReceiptEntity(
                                            task.id.value,
                                            entryName,
                                            sourceConversation.sourceConversationId,
                                            sourceMessage.sourceMessageId,
                                            conversationId.value,
                                            localId.value,
                                            reference.id.value,
                                            at.toEpochMilli(),
                                        ),
                                    )
                                    newlyLinked += 1
                                    identityLedger.mutateBatchReceipt(task, at) {
                                        it.copy(importedNewAttachments = it.importedNewAttachments + 1)
                                    }
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
                            val receiptCount = zipDao.assetOccurrenceReceiptCount(task.id.value)
                            val next = current.copy(
                                state = P6KZipAssetRecoveryState.LINKING,
                                linkedOccurrences = receiptCount,
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
                    identityLedger.mutateBatchReceipt(task, at) { receipt ->
                        if (error.message == "P6K_ASSET_IDENTITY_CONFLICT") {
                            receipt.copy(identityConflicts = receipt.identityConflicts + 1)
                        } else {
                            receipt.copy(failed = receipt.failed + 1)
                        }
                    }
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
        checkpoint?.let { current ->
            val dao = database.p6kZipImportTaskDao()
            val occurrenceCount = dao.assetOccurrenceCount(task.id.value)
            val receiptCount = dao.assetOccurrenceReceiptCount(task.id.value)
            val complete = failedConversations == 0 &&
                current.processedConversations >= current.totalConversations &&
                occurrenceCount > 0 && receiptCount == occurrenceCount
            val terminal = current.copy(
                state = if (complete) P6KZipAssetRecoveryState.COMPLETED else P6KZipAssetRecoveryState.PARTIAL,
                totalOccurrences = occurrenceCount,
                linkedOccurrences = receiptCount,
                failedConversations = if (complete) 0 else current.failedConversations,
                lastFailureKind = if (complete) null else current.lastFailureKind,
                lastFailureAtMs = if (complete) null else current.lastFailureAtMs,
                updatedAtMs = at.toEpochMilli(),
            )
            dao.upsertAssetRecoveryJob(terminal.toEntity())
            identityLedger.finishAssetRecovery(task, complete, at)
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

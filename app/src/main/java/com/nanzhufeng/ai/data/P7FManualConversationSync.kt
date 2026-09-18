package com.nanzhufeng.ai.data

import android.util.Log
import com.nanzhufeng.ai.data.local.ManualConversationSyncStateEntity
import com.nanzhufeng.ai.data.local.CloudConversationPresentationEntity
import com.nanzhufeng.ai.data.local.NanfengAiDatabase
import com.nanzhufeng.ai.data.local.CloudResponseModelUsageEntity
import com.nanzhufeng.ai.data.local.toDomain
import com.nanzhufeng.ai.domain.ContentBlock
import com.nanzhufeng.ai.domain.ConversationId
import com.nanzhufeng.ai.domain.ConversationRepository
import com.nanzhufeng.ai.domain.ConversationSnapshot
import com.nanzhufeng.ai.domain.CloudResponseModelUsage
import com.nanzhufeng.ai.domain.ConversationCostSource
import com.nanzhufeng.ai.domain.MessageNode
import com.nanzhufeng.ai.domain.MessageNodeId
import com.nanzhufeng.ai.domain.NfaiSyncPreparedSnapshot
import com.nanzhufeng.ai.domain.NfaiSyncRecord
import com.nanzhufeng.ai.domain.NfaiSyncResult
import com.nanzhufeng.ai.domain.NfaiSyncV1Gateway
import com.nanzhufeng.ai.domain.P7BAccountStateMachine
import com.nanzhufeng.ai.domain.P7BDirectionFact
import com.nanzhufeng.ai.domain.P7BSyncState
import com.nanzhufeng.ai.domain.P7CCloudResult
import com.nanzhufeng.ai.domain.P7CRemoteEnvelope
import com.nanzhufeng.ai.domain.P7CSupabaseEnvelopeGateway
import java.security.MessageDigest
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

data class P7FConversationSyncPreview(
    val conversationId: String,
    val title: String,
    val eligible: Boolean,
    val reason: String?,
)

/** A decrypted-title-free projection: listing cloud documents must not expose their content. */
data class P7FCloudConversationDocument(
    val documentId: String,
    val remoteRevision: Long,
)

/**
 * A tiny account-owned cloud document is the sole cross-device source for the
 * cloud-list pin grouping.  It contains IDs only: no title, message, local
 * pin, or device-specific order is allowed in this projection.
 */
private data class P7FCloudListPresentation(
    val remoteRevision: Long,
    val pinnedConversationIds: Set<String>,
)

private const val CLOUD_LIST_PRESENTATION_DOCUMENT_ID = "cloud-conversation-list-v1"
private const val CLOUD_LIST_PRESENTATION_TYPE = "CLOUD_CONVERSATION_LIST_V1"

/**
 * A response can have a local attribution and a portable record received from another device.
 * The provider's completed settlement is the accounting fact: it must never be replaced by a
 * local estimate (or an amount still unknown) merely because the local attribution is richer.
 */
internal fun portableUsageForCloud(
    localAttribution: CloudResponseModelUsage?,
    syncedUsage: CloudResponseModelUsage?,
): CloudResponseModelUsage? {
    fun CloudResponseModelUsage.isProviderSettlement() =
        costSource == ConversationCostSource.PROVIDER_RESPONSE && cost.totalMicros != null

    return listOfNotNull(localAttribution, syncedUsage)
        .firstOrNull(CloudResponseModelUsage::isProviderSettlement)
        ?: localAttribution
        ?: syncedUsage
}

sealed interface P7FManualConversationSyncResult {
    data class Synced(val title: String, val syncedAtEpochMs: Long) : P7FManualConversationSyncResult
    data class Rejected(val message: String) : P7FManualConversationSyncResult
}

sealed interface P7FManualConversationCancelResult {
    data class Cancelled(val title: String) : P7FManualConversationCancelResult
    data class Rejected(val message: String) : P7FManualConversationCancelResult
}

sealed interface P7FCloudConversationRestoreResult {
    data class Restored(val title: String) : P7FCloudConversationRestoreResult
    /** A newer cloud title was applied without replacing a usable local message tree. */
    data class Updated(val title: String) : P7FCloudConversationRestoreResult
    data class AlreadyPresent(val title: String) : P7FCloudConversationRestoreResult
    data class Rejected(val message: String) : P7FCloudConversationRestoreResult
}

sealed interface P7FCloudConversationBatchRestoreResult {
    data class Restored(
        val restoredCount: Int,
        val updatedCount: Int,
        val latestTitle: String,
        val skippedLegacyCount: Int = 0,
        val alreadyPresentCount: Int = 0,
    ) : P7FCloudConversationBatchRestoreResult
    /**
     * A malformed or temporarily unavailable remote document must never make the successfully
     * restored siblings look like a failed batch.  The rejected documents are left in cloud and
     * the local repository is not changed for them.
     */
    data class PartiallyRestored(
        val restoredCount: Int,
        val updatedCount: Int,
        val alreadyPresentCount: Int,
        val rejectedCount: Int,
        /** A content-free, user-actionable summary of the rejected restore stages. */
        val rejectedSummary: String,
        val latestTitle: String,
        val skippedLegacyCount: Int = 0,
    ) : P7FCloudConversationBatchRestoreResult
    data class Empty(val ignoredCount: Int, val skippedLegacyCount: Int = 0) : P7FCloudConversationBatchRestoreResult
    data class Rejected(val message: String) : P7FCloudConversationBatchRestoreResult
}

/**
 * A prior partial import can leave a durable conversation shell with no tree.
 * Completing that shell is a merge: all local-only presentation and draft
 * state survives; locally retained nodes stay untouched and verified remote
 * nodes missing locally are appended.  A locally usable leaf is never
 * overwritten.
 */
internal fun mergeRemoteIntoEmptyLocalConversation(
    existing: ConversationSnapshot,
    remote: ConversationSnapshot,
): ConversationSnapshot {
    if (existing.conversation.currentLeafMessageId != null) return existing
    val existingNodeIds = existing.nodes.mapTo(mutableSetOf()) { it.id }
    return remote.copy(
        conversation = remote.conversation.copy(
            projectId = existing.conversation.projectId,
            settings = existing.conversation.settings,
            archivedAt = existing.conversation.archivedAt,
            pinnedAt = existing.conversation.pinnedAt,
            favoritedAt = existing.conversation.favoritedAt,
            deletedAt = existing.conversation.deletedAt,
            revision = maxOf(existing.conversation.revision, remote.conversation.revision),
            autoTitlePending = existing.conversation.autoTitlePending,
            surface = existing.conversation.surface,
        ),
        nodes = existing.nodes + remote.nodes.filter { it.id !in existingNodeIds },
        draft = existing.draft,
    )
}

/**
 * A cloud read is also the cross-device update path for an already-restored conversation.
 *
 * Replacing a populated local tree would discard device-local drafts, attachments and branches,
 * but ignoring it entirely makes a desktop rename or newly synced turn permanently invisible on
 * Android. The portable record and every message both carry a monotonic revision, letting the
 * receiver merge the latest verified cloud snapshot. The cloud document is the ordered,
 * complete source during a read: even an older client that reused a semantic revision must not
 * leave a stale title or reply on the receiving device. Local-only attachment/runtime blocks
 * remain attached to their matching message but never enter the cloud record.
 */
internal sealed interface P7FExistingConversationMerge {
    data class Applied(val snapshot: ConversationSnapshot) : P7FExistingConversationMerge
    data object Unchanged : P7FExistingConversationMerge
}

internal fun mergeNewerRemoteConversation(
    existing: ConversationSnapshot,
    remote: ConversationSnapshot,
): P7FExistingConversationMerge {
    val remoteTitleIsNewer = incomingTitleIsNewer(existing, remote)
    val createdAt = mergeConversationCreationTime(existing, remote)
    if (existing.conversation.currentLeafMessageId == null) {
        val merged = mergeRemoteIntoEmptyLocalConversation(existing, remote)
        return P7FExistingConversationMerge.Applied(if (remoteTitleIsNewer) merged else merged.copy(
            conversation = merged.conversation.copy(title = existing.conversation.title, titleRevision = existing.conversation.titleRevision)))
    }
    if (remote.conversation.revision == existing.conversation.revision) {
        val localById = existing.nodes.associateBy { it.id }
        val samePortableTree = remote.nodes.size == existing.nodes.size && remote.nodes.all { remoteNode ->
            val localNode = localById[remoteNode.id] ?: return@all false
            localNode.parentMessageId == remoteNode.parentMessageId &&
                localNode.siblingPosition == remoteNode.siblingPosition &&
                localNode.role == remoteNode.role &&
                localNode.revision == remoteNode.revision &&
                localNode.content.filterIsInstance<ContentBlock.Text>() == remoteNode.content.filterIsInstance<ContentBlock.Text>()
        }
        if (existing.conversation.title == remote.conversation.title &&
            existing.conversation.titleRevision == remote.conversation.titleRevision &&
            existing.conversation.currentLeafMessageId == remote.conversation.currentLeafMessageId &&
            existing.conversation.createdAt == createdAt &&
            samePortableTree
        ) return P7FExistingConversationMerge.Unchanged
    }
    val mergedNodes = existing.nodes.toMutableList()
    val localIndexById = existing.nodes.mapIndexed { index, node -> node.id to index }.toMap()
    val remoteIds = remote.nodes.mapTo(linkedSetOf()) { it.id }
    remote.nodes.forEach { remoteNode ->
        val localIndex = localIndexById[remoteNode.id]
        if (localIndex == null) {
            mergedNodes += remoteNode
            return@forEach
        }
        val localNode = mergedNodes[localIndex]
        val remoteText = remoteNode.content.filterIsInstance<ContentBlock.Text>()
        // A verified cloud read is authoritative for all portable fields.  Do
        // not stop on a stale client reusing a message revision: replace its
        // text with the cloud text and retain only device-local blocks.
        val localOnlyBlocks = localNode.content.filterNot { it is ContentBlock.Text }
        mergedNodes[localIndex] = remoteNode.copy(content = remoteText + localOnlyBlocks)
    }
    // Absence is not a deletion: legacy/partial exports can omit completed
    // replies. There is no per-message tombstone in this wire version.
    val remoteLeaf = requireNotNull(remote.conversation.currentLeafMessageId) {
        "Verified cloud conversation has no current leaf."
    }
    return P7FExistingConversationMerge.Applied(existing.copy(
        conversation = existing.conversation.copy(
            createdAt = createdAt,
            title = if (remoteTitleIsNewer) remote.conversation.title else existing.conversation.title,
            titleRevision = if (remoteTitleIsNewer) remote.conversation.titleRevision else existing.conversation.titleRevision,
            updatedAt = maxOf(existing.conversation.updatedAt, remote.conversation.updatedAt),
            revision = maxOf(remote.conversation.revision, existing.conversation.revision),
            surface = remote.conversation.surface,
            currentLeafMessageId = completeSyncLeaf(remoteLeaf, existing.conversation.currentLeafMessageId, mergedNodes),
            archivedAt = remote.conversation.archivedAt,
            pinnedAt = remote.conversation.pinnedAt,
            favoritedAt = remote.conversation.favoritedAt,
        ),
        nodes = mergedNodes,
    ))
}

/** Identical ordering metadata cannot establish which different title is newer. */
private class TitleVersionConflict : IllegalArgumentException("标题版本冲突：缺少先后依据或双方同时改名；已保留本机标题，未覆盖。")

private fun incomingTitleIsNewer(local: ConversationSnapshot, remote: ConversationSnapshot): Boolean {
    val localTitleRevision = local.conversation.titleRevision
    val remoteTitleRevision = remote.conversation.titleRevision
    if (localTitleRevision != null || remoteTitleRevision != null) {
        val order = (remoteTitleRevision ?: 0L).compareTo(localTitleRevision ?: 0L)
        if (order == 0 && local.conversation.title != remote.conversation.title) throw TitleVersionConflict()
        return order >= 0
    }
    val timeOrder = remote.conversation.updatedAt.compareTo(local.conversation.updatedAt)
    val revisionOrder = remote.conversation.revision.compareTo(local.conversation.revision)
    if (local.conversation.title != remote.conversation.title) {
        throw TitleVersionConflict()
    }
    return timeOrder > 0 || (timeOrder == 0 && revisionOrder >= 0)
}

/**
 * A local upload must never erase turns that arrived on another device after
 * this device's last receipt.  Keep the explicit local title/leaf as the
 * latest user action, but union independently created message IDs.  For the
 * same message ID, the larger immutable message revision wins; an equal
 * revision keeps the local source instead of inventing a rewrite.
 */
private fun completeSyncLeaf(preferred: MessageNodeId?, alternate: MessageNodeId?, nodes: List<MessageNode>): MessageNodeId? {
    val byId = nodes.associateBy { it.id }
    if (preferred == null || preferred !in byId) return alternate
    val seen = mutableSetOf<MessageNodeId>()
    var cursor = alternate
    while (cursor != null && seen.add(cursor)) {
        if (cursor == preferred) return alternate
        cursor = byId[cursor]?.parentMessageId
    }
    return preferred
}

/** A proven first-send correction survives stale activity timestamps in either sync direction. */
private fun mergeConversationCreationTime(local: ConversationSnapshot, remote: ConversationSnapshot): java.time.Instant {
    val candidate = maxOf(local.conversation.createdAt, remote.conversation.createdAt)
    val firstMessage = (local.nodes.asSequence() + remote.nodes.asSequence()).minOfOrNull { it.createdAt }
    return if (candidate == firstMessage) candidate else local.conversation.createdAt
}

internal fun mergeRemoteAdditionsForLocalCommit(
    local: ConversationSnapshot,
    remote: ConversationSnapshot,
): ConversationSnapshot {
    require(local.conversation.id == remote.conversation.id) { "云端对话标识不匹配。" }
    val remoteIsNewer = remote.conversation.updatedAt > local.conversation.updatedAt
    val sameUpdatedAt = remote.conversation.updatedAt == local.conversation.updatedAt
    val remoteTitleIsNewer = incomingTitleIsNewer(local, remote)
    val merged = local.nodes.toMutableList()
    val localIndexById = local.nodes.mapIndexed { index, node -> node.id to index }.toMap()
    remote.nodes.forEach { remoteNode ->
        val localIndex = localIndexById[remoteNode.id]
        if (localIndex == null) {
            merged += remoteNode
        } else if (remoteIsNewer || (sameUpdatedAt && remoteNode.revision.revision > merged[localIndex].revision.revision)) {
            val localOnlyBlocks = merged[localIndex].content.filterNot { it is ContentBlock.Text }
            merged[localIndex] = remoteNode.copy(
                content = remoteNode.content.filterIsInstance<ContentBlock.Text>() + localOnlyBlocks,
            )
        }
    }
    val mergedIds = merged.mapTo(linkedSetOf()) { it.id }
    val localLeaf = local.conversation.currentLeafMessageId?.takeIf { it in mergedIds }
    val remoteLeaf = remote.conversation.currentLeafMessageId?.takeIf { it in mergedIds }
    return local.copy(
        conversation = local.conversation.copy(
            createdAt = mergeConversationCreationTime(local, remote),
            title = if (remoteTitleIsNewer) remote.conversation.title else local.conversation.title,
            titleRevision = if (remoteTitleIsNewer) remote.conversation.titleRevision else local.conversation.titleRevision,
            revision = maxOf(local.conversation.revision, remote.conversation.revision),
            currentLeafMessageId = if (remoteIsNewer) completeSyncLeaf(remoteLeaf, localLeaf, merged) else completeSyncLeaf(localLeaf, remoteLeaf, merged),
            updatedAt = maxOf(local.conversation.updatedAt, remote.conversation.updatedAt),
        ),
        nodes = merged.sortedWith(compareBy({ it.createdAt }, { it.id.value })),
    )
}

/**
 * Projects a completed, connected conversation tree onto the portable text-only boundary.
 * Attachment bytes and tool/runtime blocks remain exclusively local; copying a node keeps the
 * local preview and attachment owner untouched.  A node with no portable text, and descendants
 * that would become orphaned through it, are omitted rather than being rewritten or re-parented.
 */
internal fun portableCompletedTextNodes(nodes: List<MessageNode>): List<MessageNode> {
    val pending = nodes.sortedWith(compareBy({ it.createdAt }, { it.id.value })).toMutableList()
    val portableParentBySourceId = linkedMapOf<MessageNodeId, MessageNodeId?>()
    val portable = mutableListOf<MessageNode>()
    while (pending.isNotEmpty()) {
        var progressed = false
        val iterator = pending.iterator()
        while (iterator.hasNext()) {
            val node = iterator.next()
            val sourceParent = node.parentMessageId
            if (sourceParent != null && sourceParent !in portableParentBySourceId) continue
            iterator.remove()
            progressed = true
            if (node.deliveryState != com.nanzhufeng.ai.domain.MessageDeliveryState.COMPLETE) continue
            val portableParent = sourceParent?.let(portableParentBySourceId::get)
            val textOnlyContent = node.content.filterIsInstance<ContentBlock.Text>()
            if (textOnlyContent.isEmpty()) {
                // The omitted attachment/tool node is structural only.  Its
                // completed text descendants stay connected to the nearest
                // portable parent instead of disappearing from cloud sync.
                portableParentBySourceId[node.id] = portableParent
                continue
            }
            portable += node.copy(parentMessageId = portableParent, content = textOnlyContent)
            portableParentBySourceId[node.id] = node.id
        }
        if (!progressed) break
    }
    return portable
}

/** Pure aggregation keeps a bad remote document isolated from its valid siblings. */
internal fun summarizeRemoteConversationRestores(
    results: List<P7FCloudConversationRestoreResult>,
): P7FCloudConversationBatchRestoreResult {
    var restoredCount = 0
    var updatedCount = 0
    var alreadyPresentCount = 0
    var rejectedCount = 0
    var skippedLegacyCount = 0
    val rejectedMessages = mutableListOf<String>()
    var latestTitle: String? = null
    results.forEach { result ->
        when (result) {
            is P7FCloudConversationRestoreResult.Restored -> {
                restoredCount += 1
                latestTitle = result.title
            }
            is P7FCloudConversationRestoreResult.Updated -> {
                updatedCount += 1
                latestTitle = result.title
            }
            is P7FCloudConversationRestoreResult.AlreadyPresent -> {
                alreadyPresentCount += 1
                latestTitle = result.title
            }
            is P7FCloudConversationRestoreResult.Rejected -> {
                rejectedCount += 1
                rejectedMessages += result.message
            }
        }
    }
    val title = latestTitle ?: "云端会话"
    val rejectedSummary = rejectedMessages
        .groupingBy { it }
        .eachCount()
        .entries
        .sortedBy { it.key }
        .take(2)
        .joinToString("；") { (message, count) -> if (count == 1) message else "$message ×$count" }
    return when {
        rejectedCount == 0 && (restoredCount > 0 || updatedCount > 0) -> P7FCloudConversationBatchRestoreResult.Restored(restoredCount, updatedCount, title, skippedLegacyCount, alreadyPresentCount)
        rejectedCount == 0 -> P7FCloudConversationBatchRestoreResult.Empty(alreadyPresentCount, skippedLegacyCount)
        restoredCount > 0 || updatedCount > 0 || alreadyPresentCount > 0 -> P7FCloudConversationBatchRestoreResult.PartiallyRestored(
            restoredCount = restoredCount,
            updatedCount = updatedCount,
            alreadyPresentCount = alreadyPresentCount,
            rejectedCount = rejectedCount,
            rejectedSummary = rejectedSummary,
            latestTitle = title,
            skippedLegacyCount = skippedLegacyCount,
        )
        else -> P7FCloudConversationBatchRestoreResult.Rejected(
            "云端有 $rejectedCount 个对话未能恢复：$rejectedSummary",
        )
    }
}

sealed interface P7FLegacyConversationMigrationResult {
    data class Migrated(val count: Int) : P7FLegacyConversationMigrationResult
    data class Rejected(val message: String) : P7FLegacyConversationMigrationResult
}

/**
 * The only production owner allowed to upload a conversation. It has no scheduler and accepts
 * exactly one explicit conversation ID per call. Other conversations and every other domain are
 * unreachable from this owner.
 */
class P7FManualConversationSyncOwner(
    private val conversations: ConversationRepository,
    private val database: NanfengAiDatabase,
    private val accounts: P7BAccountStateMachine,
    private val accountOwner: P7FGoogleAccountOwner,
    private val now: () -> Long = System::currentTimeMillis,
) {
    fun onAuthenticated(session: P7FCloudSession) {
        val accountRef = P7BAccountStateMachine.accountRef(session.userId)
        val current = accounts.metadata(accountRef)
        accounts.authenticateDirect(
            intentId = "google-auth-${session.userId.sha256().take(24)}-${current?.revision ?: 0}",
            expectedRevision = current?.revision,
            opaqueId = session.userId,
        )
    }

    fun onSignedOut(session: P7FCloudSession?) {
        session ?: return
        val accountRef = P7BAccountStateMachine.accountRef(session.userId)
        val current = accounts.metadata(accountRef) ?: return
        if (current.state !in setOf(P7BSyncState.SIGNED_OUT, P7BSyncState.SIGNED_OUT_KEEP_LOCAL)) {
            accounts.signOutKeepLocal(
                intentId = "google-signout-${UUID.randomUUID()}",
                expectedRevision = current.revision,
                accountRef = accountRef,
            )
        }
    }

    fun preview(conversationId: ConversationId): P7FConversationSyncPreview {
        val snapshot = conversations.findById(conversationId)
            ?: return P7FConversationSyncPreview(conversationId.value, "对话", false, "该对话已不存在。")
        val reason = eligibilityFailure(snapshot)
        return P7FConversationSyncPreview(conversationId.value, snapshot.conversation.title, reason == null, reason)
    }

    /**
     * Reads only authenticated, preflighted document headers. No conversation text is decrypted
     * while listing, and callers receive no mutable remote envelope.
     */
    fun listRemoteConversationDocuments(): List<P7FCloudConversationDocument> {
        if (!accountOwner.configured) return emptyList()
        val session = accountOwner.cachedSession() ?: return emptyList()
        return accountOwner.listCloudDocuments().mapNotNull { envelope ->
            val preflight = (NfaiSyncV1Gateway.preflight(envelope) as? NfaiSyncResult.Preflighted)?.value
                ?: return@mapNotNull null
            preflight.takeIf { it.appId == "com.nanzhufeng.ai" && it.documentId.startsWith("conversation-") }
                ?.let { P7FCloudConversationDocument(it.documentId, it.revision) }
        // The server list is ordered by the shared cloud update order.  Do not
        // replace it with a device-local document-id sort.
        }.distinctBy { it.documentId }.also {
            require(session.userId == accountOwner.cachedSession()?.userId) { "登录状态已变化。" }
        }
    }

    /**
     * Restores exactly one encrypted text conversation. A pre-existing local ID is never
     * overwritten, and unrelated safe-settings/reminder records are deliberately ignored.
     */
    fun restoreRemoteConversation(documentId: String): P7FCloudConversationRestoreResult = runCatching {
        restoreRemoteConversationInternal(documentId)
    }.getOrElse { error ->
        // No envelope, title, message, account, or token is emitted.  The
        // stage and exception class are enough to distinguish a true parser or
        // Room failure from a cloud read when diagnosing a user-visible batch.
        Log.w("NanfengCloudSync", "restore stage=unclassified document=$documentId cause=${error.javaClass.simpleName}")
        P7FCloudConversationRestoreResult.Rejected(if (error is TitleVersionConflict) error.message!! else "云端对话恢复失败，未改动本机数据。")
    }

    /**
     * Cloud reading is deliberately not a two-step picker.  The explicit read action imports
     * every eligible conversation into the ordinary local conversation repository, leaving
     * existing local IDs untouched and returning the newest restored title for navigation.
     */
    fun restoreAllRemoteConversations(): P7FCloudConversationBatchRestoreResult {
        val session = accountOwner.cachedSession()
            ?: return P7FCloudConversationBatchRestoreResult.Rejected("请先登录 Google 账号。")
        val accountRef = P7BAccountStateMachine.accountRef(session.userId)
        val before = database.manualConversationSyncStateDao().listForAccount(accountRef)
        // The list RPC already returns complete envelopes. Reuse this snapshot
        // rather than issuing one more network read for every conversation.
        val documents = runCatching { accountOwner.listCloudDocuments() }
            .getOrElse { error -> return P7FCloudConversationBatchRestoreResult.Rejected(cloudListReadFailureMessage(error)) }
        val result = summarizeRemoteConversationRestores(
            documents.mapNotNull { envelope ->
                if (session.userId != accountOwner.cachedSession()?.userId)
                    return P7FCloudConversationBatchRestoreResult.Rejected("登录状态已变化。")
                restoreListedDocument(session, envelope)
            },
        )
        // Only a complete, identifiable inventory can remove old selections.
        // Failed/retired entries make absence ambiguous; do not prune then.
        val headers = documents.map { (NfaiSyncV1Gateway.preflight(it) as? NfaiSyncResult.Preflighted)?.value }
        if (headers.all { it != null && it.appId == "com.nanzhufeng.ai" && (it.documentId == CLOUD_LIST_PRESENTATION_DOCUMENT_ID || it.documentId.matches(Regex("conversation-[a-f0-9]{40}"))) } && session.userId == accountOwner.cachedSession()?.userId) {
            val present = headers.mapNotNull { it?.documentId }.toSet()
            database.runInTransaction {
                val dao = database.manualConversationSyncStateDao()
                before.filter { it.documentId !in present }.forEach { previous ->
                    if (dao.find(accountRef, previous.conversationId) == previous) {
                        dao.delete(accountRef, previous.conversationId)
                        database.cloudConversationPresentationDao().delete(accountRef, previous.conversationId)
                    }
                }
            }
        }
        return result
    }

    private fun restoreListedDocument(session: P7FCloudSession, envelope: String): P7FCloudConversationRestoreResult? {
        val header = (NfaiSyncV1Gateway.preflight(envelope) as? NfaiSyncResult.Preflighted)?.value
            ?: return P7FCloudConversationRestoreResult.Rejected("云端封包格式无法读取，本机内容未改动。")
        val remote = try {
            migrateLegacyDocument(session, P7CRemoteEnvelope(header.documentId, header.revision, header.payloadHash, envelope))
        } catch (error: Exception) {
            if (error is java.util.concurrent.CancellationException) throw error
            return P7FCloudConversationRestoreResult.Rejected(error.message ?: "旧记录格式更新未完成，本机内容已保留。")
        }
        if (header.documentId == CLOUD_LIST_PRESENTATION_DOCUMENT_ID) {
            return try {
                val presentation = readCloudListPresentation(session, remote)
                if (presentation != null) replaceLocalCloudPinnedPresentation(session, presentation.pinnedConversationIds)
                null
            } catch (error: Exception) {
                if (error is java.util.concurrent.CancellationException) throw error
                P7FCloudConversationRestoreResult.Rejected("云端置顶状态未读入，已保留原状态。")
            }
        }
        if (!header.documentId.startsWith("conversation-")) return null
        return try { restoreRemoteConversationInternal(header.documentId, remote, session.userId) }
        catch (error: Exception) {
            if (error is java.util.concurrent.CancellationException) throw error
            P7FCloudConversationRestoreResult.Rejected(if (error is TitleVersionConflict) error.message!! else "云端对话恢复失败，本机内容已保留。")
        }
    }

    private class RestoreRejected(val outcome: P7FCloudConversationRestoreResult.Rejected) : RuntimeException()

    private fun restoreRemoteConversationInternal(documentId: String, listedRemote: P7CRemoteEnvelope? = null, expectedUserId: String? = null): P7FCloudConversationRestoreResult {
        if (!documentId.matches(Regex("conversation-[a-f0-9]{40}")))
            return P7FCloudConversationRestoreResult.Rejected("云端对话标识无效。")
        val session = accountOwner.cachedSession() ?: return P7FCloudConversationRestoreResult.Rejected("请先登录 Google 账号。")
        val configured = P7CAndroidCloudGateway.availability() as? com.nanzhufeng.ai.domain.P7CServiceAvailability.Configured
            ?: return P7FCloudConversationRestoreResult.Rejected("云端服务尚未配置。")
        if (expectedUserId != null && expectedUserId != session.userId) return P7FCloudConversationRestoreResult.Rejected("登录状态已变化。")
        // Finish network I/O before the local transaction; bind the result to its account.
        val remote = listedRemote ?: when (val read = P7CSupabaseEnvelopeGateway(configured.config, accountOwner.authenticatedTransport(session.userId)).read(documentId, 0)) {
            is P7CCloudResult.Value -> read.value
            else -> return P7FCloudConversationRestoreResult.Rejected("云端对话无法读取。")
        }
        return try {
            database.runInTransaction(java.util.concurrent.Callable {
                if (accountOwner.cachedSession()?.userId != session.userId)
                    throw RestoreRejected(P7FCloudConversationRestoreResult.Rejected("登录状态已变化，未应用云端内容。"))
                val result = restoreRemoteConversationUnchecked(documentId, remote, session)
                if (result is P7FCloudConversationRestoreResult.Rejected) throw RestoreRejected(result)
                result
            })
        } catch (rejected: RestoreRejected) { rejected.outcome }
    }

    private fun restoreRemoteConversationUnchecked(documentId: String, remote: P7CRemoteEnvelope, session: P7FCloudSession): P7FCloudConversationRestoreResult {
        val openResult = openCloudEnvelope(session, remote)
        val opened = openResult as? NfaiSyncResult.Opened ?: run {
            val code = (openResult as? NfaiSyncResult.Rejected)?.code ?: "ENVELOPE_REJECTED"
            Log.w("NanfengCloudSync", "restore stage=envelope code=$code")
            val message = when (code) {
                "RECOVERY_MATERIAL_MISMATCH" -> "旧加密记录需要在原设备更新后同步一次"
                "LEGACY_DEVICE_REQUIRED" -> "旧加密记录需要在原设备更新后同步一次"
                "REVISION_ROLLBACK" -> "云端对话版本不一致"
                else -> "云端封包格式无法读取"
            }
            return P7FCloudConversationRestoreResult.Rejected("$message，本机内容未改动。")
        }
        val decoded = runCatching { P7FConversationSyncWireFormat.decodeWithModelUsage(opened.value.snapshot) }
            .getOrElse { error ->
                Log.w("NanfengCloudSync", "restore stage=decode document=$documentId cause=${error.javaClass.simpleName}")
                return P7FCloudConversationRestoreResult.Rejected("云端对话结构无法兼容，本机内容未改动。")
            }
        val restored = decoded.snapshot
        if ("conversation-${restored.conversation.id.value.sha256().take(40)}" != documentId)
            return P7FCloudConversationRestoreResult.Rejected("云端对话身份不一致，本机内容未改动。")
        // The receipt must describe the verified remote payload, not a second
        // serialization of the local tree.  Desktop history may contain failed
        // branches which Android faithfully keeps for diagnostics but excludes
        // from its next upload.  Re-serializing that tree here used to throw
        // after save(), leaving visible local content without a cloud receipt.
        val remoteConversationRecord = opened.value.snapshot.records.singleOrNull {
            it.kind == "conversation" && it.id == restored.conversation.id.value
        } ?: return P7FCloudConversationRestoreResult.Rejected("云端对话内容无效，本机内容未改动。")
        val remoteContentHash = (remoteConversationRecord.contentJson + "|" + remoteConversationRecord.revision).sha256()
        val existing = runCatching { conversations.findById(restored.conversation.id) }
            .getOrElse { error ->
                Log.w("NanfengCloudSync", "restore stage=local-read document=$documentId cause=${error.javaClass.simpleName}")
                return P7FCloudConversationRestoreResult.Rejected("本机对话状态无法读取，未改动本机数据。")
            }
        val merge = existing?.let { mergeNewerRemoteConversation(it, restored) }
        val local = when {
            existing == null -> runCatching { conversations.save(restored) }
            merge is P7FExistingConversationMerge.Applied -> runCatching { conversations.saveVerifiedCloudMerge(merge.snapshot) }
            else -> Result.success(existing)
        }
            .getOrElse { error ->
                Log.w("NanfengCloudSync", "restore stage=local-save document=$documentId cause=${error.javaClass.simpleName}")
                val reason = when (error) {
                    is android.database.sqlite.SQLiteConstraintException -> "消息关联或顺序约束冲突"
                    is android.database.sqlite.SQLiteFullException -> "本机存储空间不足"
                    is IllegalArgumentException -> "消息树或消息归属校验未通过"
                    else -> "本机存储写入异常"
                }
                return P7FCloudConversationRestoreResult.Rejected("本机对话保存未完成：$reason；未写入同步回执。")
            }
        runCatching {
            persistCloudResponseModelUsage(local.conversation.id, decoded.modelUsageByAssistantMessage.values)
        }.getOrElse { error ->
            Log.w("NanfengCloudSync", "restore stage=model-usage-save document=$documentId cause=${error.javaClass.simpleName}")
            return P7FCloudConversationRestoreResult.Rejected("云端回答模型信息未能保存，本机对话已保留。")
        }
        // A restored cloud conversation becomes an ordinary local conversation
        // immediately.  Persisting its receipt is what lets subsequent mobile
        // edits and replies enter the same periodic/manual sync path as a
        // conversation first created on this device.
        val accountRef = P7BAccountStateMachine.accountRef(session.userId)
        runCatching {
            database.manualConversationSyncStateDao().save(
                ManualConversationSyncStateEntity(
                accountRef = accountRef,
                conversationId = local.conversation.id.value,
                documentId = documentId,
                remoteRevision = remote.revision,
                payloadHash = remote.payloadHash,
                localContentHash = remoteContentHash,
                lastSyncedAtEpochMs = now(),
                ),
            )
        }.getOrElse { error ->
            Log.w("NanfengCloudSync", "restore stage=receipt-save document=$documentId cause=${error.javaClass.simpleName}")
            return P7FCloudConversationRestoreResult.Rejected("同步回执未写入；本机对话已保留。")
        }
        return when {
            existing == null -> P7FCloudConversationRestoreResult.Restored(local.conversation.title)
            merge is P7FExistingConversationMerge.Applied -> P7FCloudConversationRestoreResult.Updated(local.conversation.title)
            else -> P7FCloudConversationRestoreResult.AlreadyPresent(local.conversation.title)
        }
    }

    fun sync(conversationId: ConversationId): P7FManualConversationSyncResult =
        runCatching { syncInternal(conversationId) }
            .getOrElse { P7FManualConversationSyncResult.Rejected("同步失败，请稍后重试。") }

    /**
     * An already-selected legacy document has prior explicit sync authorization.  When the user
     * explicitly reads the cloud list, upgrade only those matched source conversations first so
     * the list never asks them to wait for an unspecified "original device" action.  Missing
     * local sources and unrelated cloud documents are deliberately left untouched.
     */
    fun migratePreviouslySelectedLegacyConversations(): P7FLegacyConversationMigrationResult {
        val session = accountOwner.cachedSession() ?: return P7FLegacyConversationMigrationResult.Rejected("请先登录 Google 账号。")
        val accountRef = P7BAccountStateMachine.accountRef(session.userId)
        val configured = P7CAndroidCloudGateway.availability() as? com.nanzhufeng.ai.domain.P7CServiceAvailability.Configured
            ?: return P7FLegacyConversationMigrationResult.Rejected("云端服务尚未配置。")
        val gateway = P7CSupabaseEnvelopeGateway(configured.config, accountOwner.authenticatedTransport(session.userId))
        var migrated = 0
        for (receipt in database.manualConversationSyncStateDao().listForAccount(accountRef)) {
            val conversationId = ConversationId(receipt.conversationId)
            if (conversations.findById(conversationId) == null) continue
            val remote = when (val read = gateway.read(receipt.documentId, 0)) {
                is P7CCloudResult.Value -> read.value
                is P7CCloudResult.Rejected -> return P7FLegacyConversationMigrationResult.Rejected("已同步对话的云端状态无法读取，本机内容未改动。")
                P7CCloudResult.Disabled -> return P7FLegacyConversationMigrationResult.Rejected("云端服务尚未配置。")
            }
            if (!isDirectEnvelope(remote.canonicalEnvelope)) continue
            when (val result = sync(conversationId)) {
                is P7FManualConversationSyncResult.Synced -> migrated += 1
                is P7FManualConversationSyncResult.Rejected -> return P7FLegacyConversationMigrationResult.Rejected(result.message)
            }
        }
        return P7FLegacyConversationMigrationResult.Migrated(migrated)
    }

    /**
     * Removes the cloud copy only.  The source conversation and every local message stay in
     * the ordinary repository, so a later cloud read cannot make the user's local history vanish.
     */
    fun cancelSync(conversationId: ConversationId): P7FManualConversationCancelResult = runCatching {
        if (!accountOwner.configured) return P7FManualConversationCancelResult.Rejected("尚未配置 Google 登录与云端服务。")
        val session = accountOwner.cachedSession()
            ?: return P7FManualConversationCancelResult.Rejected("请先登录 Google 账号。")
        val conversation = conversations.findById(conversationId)
            ?: return P7FManualConversationCancelResult.Rejected("该对话已不存在。")
        val accountRef = P7BAccountStateMachine.accountRef(session.userId)
        val receipt = database.manualConversationSyncStateDao().find(accountRef, conversationId.value)
            ?: return P7FManualConversationCancelResult.Rejected("该对话尚未同步到云端。")
        val configured = P7CAndroidCloudGateway.availability() as? com.nanzhufeng.ai.domain.P7CServiceAvailability.Configured
            ?: return P7FManualConversationCancelResult.Rejected("云端服务尚未配置。")
        val gateway = P7CSupabaseEnvelopeGateway(configured.config, accountOwner.authenticatedTransport(session.userId))
        when (gateway.delete(receipt.documentId, receipt.remoteRevision)) {
            is P7CCloudResult.Value -> {
                // A missing remote document is already equivalent to cancellation.  Remove only
                // the receipt; the local conversation is deliberately never deleted here.
                database.manualConversationSyncStateDao().delete(accountRef, conversationId.value)
                database.cloudConversationPresentationDao().delete(accountRef, conversationId.value)
                P7FManualConversationCancelResult.Cancelled(conversation.conversation.title)
            }
            P7CCloudResult.Disabled -> P7FManualConversationCancelResult.Rejected("云端服务尚未配置。")
            is P7CCloudResult.Rejected -> P7FManualConversationCancelResult.Rejected("云端版本已变化，未取消同步。")
        }
    }.getOrElse { P7FManualConversationCancelResult.Rejected("取消同步失败，本机对话保持不变。") }

    private fun syncInternal(conversationId: ConversationId, requireExistingSelection: Boolean = false): P7FManualConversationSyncResult {
        conversations.repairLegacyCreationTimes()
        if (!accountOwner.configured) return P7FManualConversationSyncResult.Rejected("尚未配置 Google 登录与云端服务。")
        val session = accountOwner.cachedSession()
            ?: return P7FManualConversationSyncResult.Rejected("请先登录 Google 账号。")
        val snapshot = conversations.findById(conversationId)
            ?: return P7FManualConversationSyncResult.Rejected("该对话已不存在。")
        eligibilityFailure(snapshot)?.let { return P7FManualConversationSyncResult.Rejected(it) }

        onAuthenticated(session)
        val accountRef = P7BAccountStateMachine.accountRef(session.userId)
        val documentId = documentId(conversationId.value)
        val gateway = P7CSupabaseEnvelopeGateway(
            config = (P7CAndroidCloudGateway.availability() as com.nanzhufeng.ai.domain.P7CServiceAvailability.Configured).config,
            transport = accountOwner.authenticatedTransport(session.userId),
        )
        val remote = when (val read = gateway.read(documentId, 0)) {
            is P7CCloudResult.Value -> read.value
            is P7CCloudResult.Rejected -> if (read.code == "REMOTE_MISSING") null else {
                return P7FManualConversationSyncResult.Rejected("无法读取该对话的云端状态。")
            }
            P7CCloudResult.Disabled -> return P7FManualConversationSyncResult.Rejected("云端服务尚未配置。")
        }
        val ledger = database.manualConversationSyncStateDao().find(accountRef, conversationId.value)
        if (requireExistingSelection && ledger == null) {
            return P7FManualConversationSyncResult.Rejected("该对话已停止云端同步，未重新上传。")
        }
        if (remote == null && ledger != null) {
            database.runInTransaction {
                database.manualConversationSyncStateDao().delete(accountRef, conversationId.value)
                database.cloudConversationPresentationDao().delete(accountRef, conversationId.value)
            }
            return P7FManualConversationSyncResult.Rejected("该对话已从云端删除，已停止续同步；本机内容保留。")
        }
        val remoteIsDirect = remote?.let { isDirectEnvelope(it.canonicalEnvelope) } == true
        val remoteDecoded = remote?.let { envelope ->
            val opened = openCloudEnvelope(session, envelope) as? NfaiSyncResult.Opened
                ?: return P7FManualConversationSyncResult.Rejected("云端记录无法完整读取；旧加密记录请在原设备更新后同步，本机和云端内容均保留。")
            runCatching { P7FConversationSyncWireFormat.decodeWithModelUsage(opened.value.snapshot) }
                .getOrElse { return P7FManualConversationSyncResult.Rejected("云端对话结构无法兼容。") }
        }
        val remoteSnapshot = remoteDecoded?.snapshot
        // An explicit local sync is the user's newest completed snapshot.  The
        // cloud revision above is only the optimistic-commit base; a stale
        // receipt must not turn this into a permanent conflict.  Conversely,
        // an explicit cloud read installs that verified cloud snapshot.

        var metadata = accounts.metadata(accountRef) ?: return P7FManualConversationSyncResult.Rejected("账号状态尚未准备，请重新登录。")
        if (metadata.state == P7BSyncState.AUTHENTICATED_NEEDS_RECOVERY_CONFIRMATION) return P7FManualConversationSyncResult.Rejected("账号同步准备未完成，请重新登录后重试。")
        if (metadata.state == P7BSyncState.DIRECTION_REQUIRED) {
            val direction = if (remote == null) P7BDirectionFact.LOCAL_PRESENT_EMPTY_REMOTE else P7BDirectionFact.LOCAL_PRESENT_REMOTE_MATCHED
            accounts.chooseDirection("direction-${UUID.randomUUID()}", metadata.revision, accountRef, direction)
            metadata = accounts.metadata(accountRef)!!
        }
        if (metadata.state != P7BSyncState.READY) {
            return P7FManualConversationSyncResult.Rejected(syncStateActionMessage(metadata))
        }

        val expectedRevision = remote?.revision ?: 0L
        val uploadSnapshot = remoteSnapshot?.let { remoteValue ->
            runCatching { mergeRemoteAdditionsForLocalCommit(snapshot, remoteValue) }
                .getOrElse { return P7FManualConversationSyncResult.Rejected(if (it is TitleVersionConflict) it.message!! else "云端对话无法与本机完整合并。") }
        } ?: snapshot
        // The remote tree has already passed the encrypted-envelope and wire
        // checks. Keep its additions locally before attempting our next
        // optimistic write, so a later network failure cannot make a valid
        // phone turn disappear from this device. Model/cost facts are stored
        // at the same point and are therefore available to the outgoing
        // envelope rather than being silently dropped during the union.
        if (uploadSnapshot != snapshot) {
            runCatching { conversations.saveVerifiedCloudMerge(uploadSnapshot) }
                .getOrElse { return P7FManualConversationSyncResult.Rejected("云端新增内容保存未完成。") }
        }
        remoteDecoded?.modelUsageByAssistantMessage?.values?.let { usages ->
            runCatching { persistCloudResponseModelUsage(uploadSnapshot.conversation.id, usages) }
                .getOrElse { return P7FManualConversationSyncResult.Rejected("云端回答模型信息未能保存。") }
        }
        val (prepared, localContentHash) = preparedSnapshot(uploadSnapshot, documentId, expectedRevision + 1)
        if (remote != null &&
            remoteIsDirect &&
            ledger?.remoteRevision == remote.revision &&
            ledger.payloadHash == remote.payloadHash &&
            ledger.localContentHash == localContentHash
        ) {
            val checkedAt = now()
            database.manualConversationSyncStateDao().save(ledger.copy(lastSyncedAtEpochMs = checkedAt))
            return P7FManualConversationSyncResult.Synced(snapshot.conversation.title, checkedAt)
        }
        val sealed = NfaiSyncV1Gateway.sealDirect(prepared) as? NfaiSyncResult.Sealed
            ?: return P7FManualConversationSyncResult.Rejected("该对话无法准备同步内容。")
        val preflight = NfaiSyncV1Gateway.preflight(sealed.canonicalEnvelope) as? NfaiSyncResult.Preflighted
            ?: return P7FManualConversationSyncResult.Rejected("同步内容完整性校验失败。")
        val committed = gateway.commit(expectedRevision, sealed.canonicalEnvelope) as? P7CCloudResult.Value
            ?: return P7FManualConversationSyncResult.Rejected("该对话未能写入云端。")
        val readBack = gateway.read(documentId, committed.value.revision) as? P7CCloudResult.Value
            ?: return P7FManualConversationSyncResult.Rejected("云端回读校验失败。")
        if (readBack.value.payloadHash != preflight.value.payloadHash || readBack.value.revision != preflight.value.revision) {
            return P7FManualConversationSyncResult.Rejected("云端回读与本机密文不一致。")
        }
        val syncedAt = now()
        database.manualConversationSyncStateDao().save(
            ManualConversationSyncStateEntity(accountRef, conversationId.value, documentId, readBack.value.revision, readBack.value.payloadHash, localContentHash, syncedAt),
        )
        return P7FManualConversationSyncResult.Synced(uploadSnapshot.conversation.title, syncedAt)
    }

    /** Periodic work can only revisit conversations that already have a successful manual receipt. */
    fun syncPreviouslySelected(): List<P7FManualConversationSyncResult> {
        val session = accountOwner.cachedSession() ?: return emptyList()
        val accountRef = P7BAccountStateMachine.accountRef(session.userId)
        return database.manualConversationSyncStateDao().listForAccount(accountRef).map { state ->
            val local = conversations.findById(ConversationId(state.conversationId))
            if (local == null || local.conversation.deletedAt != null) {
                cancelDeletedConversationCloudCopy(accountRef, state)
            } else {
                runCatching { syncInternal(ConversationId(state.conversationId), requireExistingSelection = true) }
                    .getOrElse { P7FManualConversationSyncResult.Rejected("云端续同步未完成。") }
            }
        }
    }

    /** A normal local deletion must not leave an orphan that a later cloud read revives. */
    private fun cancelDeletedConversationCloudCopy(
        accountRef: String,
        receipt: ManualConversationSyncStateEntity,
    ): P7FManualConversationSyncResult {
        val session = accountOwner.cachedSession() ?: return P7FManualConversationSyncResult.Rejected("请先登录 Google 账号。")
        if (P7BAccountStateMachine.accountRef(session.userId) != accountRef)
            return P7FManualConversationSyncResult.Rejected("登录状态已变化。")
        val configured = P7CAndroidCloudGateway.availability() as? com.nanzhufeng.ai.domain.P7CServiceAvailability.Configured
            ?: return P7FManualConversationSyncResult.Rejected("云端服务尚未配置。")
        val gateway = P7CSupabaseEnvelopeGateway(configured.config, accountOwner.authenticatedTransport(session.userId))
        // Delete the confirmed current version, not a stale receipt forever.
        // A missing document is already the requested deletion outcome.
        val remote = when (val read = gateway.read(receipt.documentId, 0)) {
            is P7CCloudResult.Value -> read.value
            is P7CCloudResult.Rejected -> if (read.code == "REMOTE_MISSING") null else
                return P7FManualConversationSyncResult.Rejected("已删除对话的云端状态无法核对。")
            P7CCloudResult.Disabled -> return P7FManualConversationSyncResult.Rejected("云端服务尚未配置。")
        }
        val deletion = remote?.let { gateway.delete(receipt.documentId, it.revision) } ?: P7CCloudResult.Value(true)
        return when (deletion) {
            is P7CCloudResult.Value -> {
                database.manualConversationSyncStateDao().delete(accountRef, receipt.conversationId)
                database.cloudConversationPresentationDao().delete(accountRef, receipt.conversationId)
                P7FManualConversationSyncResult.Synced("已删除对话", now())
            }
            P7CCloudResult.Disabled -> P7FManualConversationSyncResult.Rejected("云端服务尚未配置。")
            is P7CCloudResult.Rejected -> P7FManualConversationSyncResult.Rejected("已删除对话的云端副本未能清理。")
        }
    }

    fun latestSync(session: P7FCloudSession?): ManualConversationSyncStateEntity? = session?.let {
        database.manualConversationSyncStateDao().listForAccount(P7BAccountStateMachine.accountRef(it.userId)).firstOrNull()
    }

    fun syncedConversationIds(session: P7FCloudSession?): Set<String> = session
        ?.let { database.manualConversationSyncStateDao().listForAccount(P7BAccountStateMachine.accountRef(it.userId)) }
        ?.mapTo(linkedSetOf()) { it.conversationId }
        .orEmpty()

    /** Receipt-owned cloud rows bypass the local active/archive filter without changing it. */
    fun cloudConversationProjection(session: P7FCloudSession?): List<com.nanzhufeng.ai.domain.Conversation> = session
        ?.let { value ->
            conversations.repairLegacyCreationTimes()
            database.manualConversationSyncStateDao()
                .listForAccount(P7BAccountStateMachine.accountRef(value.userId))
                .mapNotNull { receipt -> conversations.findById(ConversationId(receipt.conversationId))?.conversation }
        }
        .orEmpty()

    /** Cloud-list pinning is account-isolated presentation state, not conversation state. */
    fun cloudPinnedConversationIds(session: P7FCloudSession?): Set<String> = session
        ?.let { database.cloudConversationPresentationDao().pinnedConversationIds(P7BAccountStateMachine.accountRef(it.userId)) }
        ?.toSet()
        .orEmpty()

    /**
     * Updates the account-owned cloud presentation, then mirrors its verified
     * IDs locally for offline rendering.  It never writes Conversation.pinned.
     */
    fun setCloudConversationPinned(session: P7FCloudSession?, conversationId: ConversationId, pinned: Boolean): Boolean {
        val value = session ?: return false
        val accountRef = P7BAccountStateMachine.accountRef(value.userId)
        if (database.manualConversationSyncStateDao().find(accountRef, conversationId.value) == null) return false
        return runCatching {
            val current = readCloudListPresentation(value)
            val updated = current?.pinnedConversationIds.orEmpty().toMutableSet().apply {
                if (pinned) add(conversationId.value) else remove(conversationId.value)
            }
            val expectedRevision = current?.remoteRevision ?: 0L
            val gateway = directCloudGateway()
            val content = JSONObject()
                .put("type", CLOUD_LIST_PRESENTATION_TYPE)
                .put("pinnedConversationIds", JSONArray(updated.sorted()))
            val prepared = NfaiSyncPreparedSnapshot(
                appId = "com.nanzhufeng.ai",
                documentId = CLOUD_LIST_PRESENTATION_DOCUMENT_ID,
                revision = expectedRevision + 1,
                records = listOf(
                    NfaiSyncRecord(
                        kind = "safe_settings",
                        id = CLOUD_LIST_PRESENTATION_DOCUMENT_ID,
                        revision = expectedRevision + 1,
                        classification = "NORMAL",
                        contentJson = content.toString(),
                    ),
                ),
            )
            val sealed = NfaiSyncV1Gateway.sealDirect(prepared) as? NfaiSyncResult.Sealed ?: return false
            val committed = gateway.commit(expectedRevision, sealed.canonicalEnvelope) as? P7CCloudResult.Value ?: return false
            val readBack = readCloudListPresentation(value) ?: return false
            if (readBack.remoteRevision != committed.value.revision || readBack.pinnedConversationIds != updated) return false
            replaceLocalCloudPinnedPresentation(value, readBack.pinnedConversationIds)
            true
        }.getOrElse { error ->
            Log.w("NanfengCloudSync", "cloud-list-presentation stage=write cause=${error.javaClass.simpleName}")
            false
        }
    }

    private fun migrateLegacyDocument(session: P7FCloudSession, remote: P7CRemoteEnvelope): P7CRemoteEnvelope {
        if (isDirectEnvelope(remote.canonicalEnvelope)) return remote
        val opened = openCloudEnvelope(session, remote) as? NfaiSyncResult.Opened
            ?: error("旧加密记录需要在原设备更新后同步一次，本机内容已保留。")
        val revision = Math.addExact(remote.revision, 1)
        val sealed = NfaiSyncV1Gateway.sealDirect(opened.value.snapshot.copy(revision = revision)) as? NfaiSyncResult.Sealed
            ?: error("旧记录格式更新未完成。")
        val header = (NfaiSyncV1Gateway.preflight(sealed.canonicalEnvelope) as NfaiSyncResult.Preflighted).value
        require(accountOwner.cachedSession()?.userId == session.userId) { "登录状态已变化。" }
        val gateway = directCloudGateway(session.userId)
        gateway.commit(remote.revision, sealed.canonicalEnvelope)
        val readBack = (gateway.read(remote.documentId, revision) as? P7CCloudResult.Value)?.value
            ?: error("旧记录格式更新尚未确认，请再次读取核对。")
        require(readBack.revision == revision && readBack.payloadHash == header.payloadHash &&
            NfaiSyncV1Gateway.openDirect(readBack.canonicalEnvelope, "com.nanzhufeng.ai", remote.documentId, revision) is NfaiSyncResult.Opened) { "旧记录格式更新回读未通过。" }
        return readBack
    }

    private fun openCloudEnvelope(session: P7FCloudSession, remote: P7CRemoteEnvelope): NfaiSyncResult {
        if (isDirectEnvelope(remote.canonicalEnvelope)) return NfaiSyncV1Gateway.openDirect(remote.canonicalEnvelope, "com.nanzhufeng.ai", remote.documentId, remote.revision)
        // Read-only compatibility: retained private material may recover old ciphertext.
        // Never delete old material or replace an unreadable cloud record from a stale local copy.
        return runCatching {
            accountOwner.withWrappingMaterial(session.userId) { material ->
                NfaiSyncV1Gateway.openWithAccountWrappingMaterial(remote.canonicalEnvelope, material, "com.nanzhufeng.ai", remote.documentId, remote.revision)
            }
        }.getOrNull()?.takeIf { it is NfaiSyncResult.Opened } ?: NfaiSyncResult.Rejected("LEGACY_DEVICE_REQUIRED")
    }

    private fun directCloudGateway(expectedUserId: String = accountOwner.cachedSession()?.userId ?: error("SIGNED_OUT")): P7CSupabaseEnvelopeGateway {
        val configured = P7CAndroidCloudGateway.availability() as? com.nanzhufeng.ai.domain.P7CServiceAvailability.Configured
            ?: error("云端服务尚未配置。")
        return P7CSupabaseEnvelopeGateway(configured.config, accountOwner.authenticatedTransport(expectedUserId))
    }

    /** Reads IDs only; titles, bodies and local pin state never enter this document. */
    private fun readCloudListPresentation(session: P7FCloudSession, listedRemote: P7CRemoteEnvelope? = null): P7FCloudListPresentation? {
        val remote = listedRemote ?: when (val result = directCloudGateway().read(CLOUD_LIST_PRESENTATION_DOCUMENT_ID, 0)) {
            is P7CCloudResult.Value -> result.value
            is P7CCloudResult.Rejected -> if (result.code == "REMOTE_MISSING") return null else error("云端列表状态无法读取。")
            P7CCloudResult.Disabled -> error("云端服务尚未配置。")
        }
        val opened = openCloudEnvelope(session, remote) as? NfaiSyncResult.Opened ?: error("云端列表状态校验失败。")
        val record = opened.value.snapshot.records.singleOrNull {
            it.kind == "safe_settings" && it.id == CLOUD_LIST_PRESENTATION_DOCUMENT_ID
        } ?: error("云端列表状态无效。")
        val content = JSONObject(record.contentJson)
        require(content.length() == 2 && content.getString("type") == CLOUD_LIST_PRESENTATION_TYPE)
        val values = content.getJSONArray("pinnedConversationIds")
        require(values.length() <= 10_000)
        val pinned = buildSet {
            repeat(values.length()) {
                val id = values.getString(it)
                require(id.matches(Regex("[A-Za-z0-9._-]{2,128}")))
                add(id)
            }
        }
        require(session.userId == accountOwner.cachedSession()?.userId) { "登录状态已变化。" }
        return P7FCloudListPresentation(remote.revision, pinned)
    }

    private fun replaceLocalCloudPinnedPresentation(session: P7FCloudSession, ids: Set<String>) {
        val accountRef = P7BAccountStateMachine.accountRef(session.userId)
        database.runInTransaction {
            val dao = database.cloudConversationPresentationDao()
            dao.clearForAccount(accountRef)
            ids.sorted().forEach { conversationId ->
                dao.save(CloudConversationPresentationEntity(accountRef, conversationId, cloudPinned = true))
            }
        }
    }

    /** Never expose an implementation-state catchall to the account screen. */
    private fun syncStateActionMessage(metadata: com.nanzhufeng.ai.domain.P7BAccountMetadata): String = when (metadata.state) {
        P7BSyncState.AUTHENTICATED_NEEDS_RECOVERY_CONFIRMATION -> "账号状态正在更新，请重新登录后重试。"
        P7BSyncState.DIRECTION_REQUIRED -> "正在确认本机与云端的首次同步状态，请再次同步该对话。"
        P7BSyncState.SYNCING -> "该账号正在同步，请稍候再试。"
        P7BSyncState.CONFLICT -> "云端已有不同版本，请先读取云端列表并处理冲突。"
        P7BSyncState.FAILED -> when (metadata.lastError) {
            "KEY_MATERIAL_UNAVAILABLE" -> "账号状态正在更新，请重新登录后重试。"
            else -> "账号同步准备未完成，请重新登录后重试。"
        }
        P7BSyncState.SIGNED_OUT, P7BSyncState.SIGNED_OUT_KEEP_LOCAL -> "请先登录 Google 账号。"
        P7BSyncState.READY -> "同步状态已就绪，请重试。"
    }

    private fun eligibilityFailure(snapshot: com.nanzhufeng.ai.domain.ConversationSnapshot): String? = when {
        snapshot.conversation.deletedAt != null -> "已删除的对话不会同步。"
        snapshot.draft.text.isNotBlank() || snapshot.draft.attachments.isNotEmpty() -> "请先发送或清空未完成草稿。"
        else -> null
    }

    private fun isDirectEnvelope(envelope: String): Boolean = runCatching {
        JSONObject(envelope).optString("format") == "nfai.sync.direct"
    }.getOrDefault(false)

    private fun preparedSnapshot(
        snapshot: com.nanzhufeng.ai.domain.ConversationSnapshot,
        documentId: String,
        revision: Long,
    ): Pair<NfaiSyncPreparedSnapshot, String> {
        val conversation = snapshot.conversation
        // Match Desktop's portable boundary: a provider timeout/cancellation
        // must not block all prior completed turns, and it must never be
        // relabeled as complete in the cloud copy.  The local source snapshot
        // remains untouched for retry and diagnostics.
        val retainedNodes = portableCompletedTextNodes(snapshot.nodes)
        require(retainedNodes.isNotEmpty()) { "没有可同步的已完成消息。" }
        val retainedIds = retainedNodes.mapTo(linkedSetOf()) { it.id.value }
        val currentLeafId = snapshot.conversation.currentLeafMessageId
            ?.takeIf { retainedIds.contains(it.value) }
            ?: retainedNodes.last().id
        val attributionsByMessage = database.assistantResponseModelAttributionDao()
            .forMessages(retainedNodes.map { it.id.value })
            .groupBy { it.assistantMessageId }
        val cloudUsageByMessage = database.cloudResponseModelUsageDao()
            .forMessages(retainedNodes.map { it.id.value })
            .groupBy { it.assistantMessageId }
        val nodes = JSONArray().also { array ->
            retainedNodes.forEach { node ->
                val encoded = JSONObject()
                    .put("id", node.id.value)
                    .put("parentMessageId", node.parentMessageId?.value ?: JSONObject.NULL)
                    .put("siblingPosition", node.siblingPosition)
                    .put("role", node.role.name)
                    .put("createdAtEpochMs", node.createdAt.toEpochMilli())
                    .put("deliveryState", com.nanzhufeng.ai.domain.MessageDeliveryState.COMPLETE.name)
                    .put("revision", node.revision.revision)
                    .put("revisesMessageId", node.revision.revisesMessageId?.value ?: JSONObject.NULL)
                    .put("text", JSONArray().also { texts -> node.content.filterIsInstance<ContentBlock.Text>().forEach { texts.put(it.text) } })
                // Model and settled-cost facts are history metadata, not a
                // provider configuration.  Keep them without routes, keys or
                // endpoint details so the restored answer has its original
                // model/amount rather than a new local estimate.
                val localAttribution = attributionsByMessage[node.id.value]?.lastOrNull()?.let { attribution ->
                    CloudResponseModelUsage(
                        assistantMessageId = node.id,
                        modelId = attribution.modelId,
                        modelDisplayName = attribution.modelDisplayName,
                        usage = com.nanzhufeng.ai.domain.ProviderUsage(attribution.inputTokens, attribution.outputTokens, attribution.totalTokens, attribution.cachedInputTokens, attribution.reasoningTokens),
                        cost = com.nanzhufeng.ai.domain.ProviderCost(attribution.costPriceVersion, attribution.costCurrencyCode, attribution.costTotalMicros),
                        costSource = attribution.costSource?.let(com.nanzhufeng.ai.domain.ConversationCostSource::valueOf),
                    )
                }
                val portableUsage = portableUsageForCloud(
                    localAttribution,
                    cloudUsageByMessage[node.id.value]?.lastOrNull()?.toDomain(),
                )
                portableUsage?.let { usage ->
                    encoded.put("modelUsage", JSONObject()
                        .put("modelId", usage.modelId)
                        .put("modelDisplayName", usage.modelDisplayName)
                        .put("inputTokens", usage.usage.inputTokens ?: JSONObject.NULL)
                        .put("outputTokens", usage.usage.outputTokens ?: JSONObject.NULL)
                        .put("totalTokens", usage.usage.totalTokens ?: JSONObject.NULL)
                        .put("cachedInputTokens", usage.usage.cachedInputTokens ?: JSONObject.NULL)
                        .put("reasoningTokens", usage.usage.reasoningTokens ?: JSONObject.NULL)
                        .put("costPriceVersion", usage.cost.priceVersion ?: JSONObject.NULL)
                        .put("costCurrencyCode", usage.cost.currencyCode ?: JSONObject.NULL)
                        .put("costTotalMicros", usage.cost.totalMicros ?: JSONObject.NULL)
                        .put("costSource", usage.costSource?.name ?: JSONObject.NULL))
                }
                array.put(encoded)
            }
        }
        val content = JSONObject()
            .put("title", conversation.title)
            .apply { conversation.titleRevision?.let { put("titleRevision", it) } }
            .put("currentLeafMessageId", currentLeafId.value)
            .put("createdAtEpochMs", conversation.createdAt.toEpochMilli())
            .put("updatedAtEpochMs", conversation.updatedAt.toEpochMilli())
            .put("surface", conversation.surface.name)
            .put("archivedAtEpochMs", conversation.archivedAt?.toEpochMilli() ?: JSONObject.NULL)
            .put("pinnedAtEpochMs", conversation.pinnedAt?.toEpochMilli() ?: JSONObject.NULL)
            .put("favoritedAtEpochMs", conversation.favoritedAt?.toEpochMilli() ?: JSONObject.NULL)
            .put("nodes", nodes)
        val semanticRevision = maxOf(conversation.revision, snapshot.nodes.maxOfOrNull { it.revision.revision.toLong() } ?: 1L)
        val prepared = NfaiSyncPreparedSnapshot(
            appId = "com.nanzhufeng.ai",
            documentId = documentId,
            revision = revision,
            records = listOf(NfaiSyncRecord("conversation", conversation.id.value, semanticRevision, "NORMAL", content.toString())),
        )
        return prepared to (content.toString() + "|" + semanticRevision).sha256()
    }

    private fun documentId(conversationId: String) = "conversation-${conversationId.sha256().take(40)}"

    private fun persistCloudResponseModelUsage(
        conversationId: ConversationId,
        usages: Collection<CloudResponseModelUsage>,
    ) = com.nanzhufeng.ai.data.local.RoomCloudResponseModelUsageStore(database)
        .recordVerified(conversationId, usages)

    private fun CloudResponseModelUsage.toEntity(conversationId: ConversationId) = CloudResponseModelUsageEntity(
        conversationId = conversationId.value,
        assistantMessageId = assistantMessageId.value,
        modelId = modelId,
        modelDisplayName = modelDisplayName,
        inputTokens = usage.inputTokens,
        outputTokens = usage.outputTokens,
        totalTokens = usage.totalTokens,
        cachedInputTokens = usage.cachedInputTokens,
        reasoningTokens = usage.reasoningTokens,
        costPriceVersion = cost.priceVersion,
        costCurrencyCode = cost.currencyCode,
        costTotalMicros = cost.totalMicros,
        costSource = costSource?.name,
    )
    private fun String.sha256() = MessageDigest.getInstance("SHA-256").digest(toByteArray()).joinToString("") { "%02x".format(it) }
}

internal fun cloudListReadFailureMessage(error: Throwable): String {
    if (error is kotlinx.coroutines.CancellationException) throw error
    return when (error.message) {
        "REMOTE_401", "REMOTE_403", "SIGNED_OUT" -> "登录已失效，请重新登录后再读取。"
        "REMOTE_404" -> "云端列表服务暂不可用，请稍后重试。"
        "REMOTE_429" -> "读取太频繁，请稍后重试。"
        else -> when (error) {
            is org.json.JSONException -> "云端列表响应格式无法读取，本机内容已保留。"
            is java.io.IOException -> "网络连接未完成，请检查网络后重试。"
            else -> "云端列表读取失败，本机内容已保留，请稍后重试。"
        }
    }
}

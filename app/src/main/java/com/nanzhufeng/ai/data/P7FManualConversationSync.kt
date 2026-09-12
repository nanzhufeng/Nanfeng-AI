package com.nanzhufeng.ai.data

import com.nanzhufeng.ai.data.local.ManualConversationSyncStateEntity
import com.nanzhufeng.ai.data.local.NanfengAiDatabase
import com.nanzhufeng.ai.domain.ContentBlock
import com.nanzhufeng.ai.domain.ConversationId
import com.nanzhufeng.ai.domain.ConversationRepository
import com.nanzhufeng.ai.domain.NfaiSyncPreparedSnapshot
import com.nanzhufeng.ai.domain.NfaiSyncRecord
import com.nanzhufeng.ai.domain.NfaiSyncResult
import com.nanzhufeng.ai.domain.NfaiSyncV1Gateway
import com.nanzhufeng.ai.domain.P7BAccountStateMachine
import com.nanzhufeng.ai.domain.P7BDirectionFact
import com.nanzhufeng.ai.domain.P7BSyncState
import com.nanzhufeng.ai.domain.P7CCloudResult
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

sealed interface P7FManualConversationSyncResult {
    data class Synced(val title: String, val syncedAtEpochMs: Long) : P7FManualConversationSyncResult
    data class Rejected(val message: String) : P7FManualConversationSyncResult
    data object Conflict : P7FManualConversationSyncResult
}

sealed interface P7FCloudConversationRestoreResult {
    data class Restored(val title: String) : P7FCloudConversationRestoreResult
    data class AlreadyPresent(val title: String) : P7FCloudConversationRestoreResult
    data class Rejected(val message: String) : P7FCloudConversationRestoreResult
}

sealed interface P7FRecoveryCodeRotationResult {
    data class Changed(val conversationCount: Int) : P7FRecoveryCodeRotationResult
    data class Rejected(val message: String) : P7FRecoveryCodeRotationResult
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
        accounts.authenticateVerifiedOpaqueId(
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

    fun prepareRecoveryProtection(recoveryCode: CharArray, recoveryCodeSaved: Boolean): Result<Unit> = runCatching {
        require(recoveryCodeSaved) { "请先确认已保存恢复码。" }
        require(recoveryCode.size >= 12) { "恢复码至少 12 个字符。" }
        val session = accountOwner.cachedSession() ?: error("请先登录 Google 账号。")
        onAuthenticated(session)
        if (!accountOwner.recoveryReady(session.userId)) accountOwner.prepareRecoveryMaterial(session.userId, recoveryCode)
        val accountRef = P7BAccountStateMachine.accountRef(session.userId)
        val metadata = accounts.metadata(accountRef) ?: error("账号密钥尚未准备。")
        if (metadata.state == P7BSyncState.AUTHENTICATED_NEEDS_RECOVERY_CONFIRMATION) {
            accounts.confirmRecoverySaved("recovery-${UUID.randomUUID()}", metadata.revision, accountRef)
        }
    }

    /**
     * Re-encrypts every locally selected cloud document with pending material and only promotes
     * that material after every remote commit has been read back. The recovery code is never
     * stored; retry state contains only Keystore-encrypted derived material.
     */
    fun changeRecoveryCode(recoveryCode: CharArray): P7FRecoveryCodeRotationResult = runCatching {
        rotateRecoveryCodeInternal(recoveryCode)
    }.getOrElse { P7FRecoveryCodeRotationResult.Rejected("更换恢复码失败，请稍后重试。") }

    private fun rotateRecoveryCodeInternal(recoveryCode: CharArray): P7FRecoveryCodeRotationResult {
        if (recoveryCode.size !in 12..128 || recoveryCode.any(Char::isISOControl)) {
            return P7FRecoveryCodeRotationResult.Rejected("恢复码需要 12 至 128 个字符，且不能包含控制字符。")
        }
        if (!accountOwner.configured) return P7FRecoveryCodeRotationResult.Rejected("尚未配置 Google 登录与云端服务。")
        val session = accountOwner.cachedSession() ?: return P7FRecoveryCodeRotationResult.Rejected("请先登录 Google 账号。")
        if (!accountOwner.recoveryReady(session.userId)) return P7FRecoveryCodeRotationResult.Rejected("请先完成恢复保护。")
        onAuthenticated(session)
        val accountRef = P7BAccountStateMachine.accountRef(session.userId)
        val metadata = accounts.metadata(accountRef) ?: return P7FRecoveryCodeRotationResult.Rejected("账号密钥尚未准备。")
        if (metadata.state != P7BSyncState.READY) return P7FRecoveryCodeRotationResult.Rejected("账号同步状态需要先处理。")
        val states = database.manualConversationSyncStateDao().listForAccount(accountRef)
        val configured = P7CAndroidCloudGateway.availability() as? com.nanzhufeng.ai.domain.P7CServiceAvailability.Configured
            ?: return P7FRecoveryCodeRotationResult.Rejected("云端服务尚未配置。")
        val gateway = P7CSupabaseEnvelopeGateway(configured.config, accountOwner.authenticatedTransport())
        accountOwner.preparePendingRecoveryMaterial(session.userId, recoveryCode)

        states.forEach { ledger ->
            val snapshot = conversations.findById(ConversationId(ledger.conversationId))
                ?: return P7FRecoveryCodeRotationResult.Rejected("新恢复码已暂存；存在无法读取的已同步对话，请使用同一新码重试。")
            eligibilityFailure(snapshot)?.let {
                return P7FRecoveryCodeRotationResult.Rejected("新恢复码已暂存；存在当前不能同步的对话，请使用同一新码重试。")
            }
            val remote = (gateway.read(ledger.documentId, 0) as? P7CCloudResult.Value)?.value
                ?: return P7FRecoveryCodeRotationResult.Rejected("新恢复码已暂存；无法读取云端对话，请使用同一新码重试。")
            if (remote.revision != ledger.remoteRevision || remote.payloadHash != ledger.payloadHash) {
                return P7FRecoveryCodeRotationResult.Rejected("新恢复码已暂存；云端对话已变化，请使用同一新码重试。")
            }
            val (prepared, localHash) = preparedSnapshot(snapshot, ledger.documentId, remote.revision + 1)
            val sealed = accountOwner.withPendingWrappingMaterial(session.userId) { material ->
                accounts.withReadyDataKey(accountRef) { dataKey ->
                    NfaiSyncV1Gateway.sealWithAccountWrappingMaterial(prepared, dataKey, material)
                }
            } as? NfaiSyncResult.Sealed
                ?: return P7FRecoveryCodeRotationResult.Rejected("新恢复码已暂存；无法重新加密已同步对话，请使用同一新码重试。")
            val preflight = NfaiSyncV1Gateway.preflight(sealed.canonicalEnvelope) as? NfaiSyncResult.Preflighted
                ?: return P7FRecoveryCodeRotationResult.Rejected("新恢复码校验失败。")
            val committed = gateway.commit(remote.revision, sealed.canonicalEnvelope) as? P7CCloudResult.Value
                ?: return P7FRecoveryCodeRotationResult.Rejected("云端未接受新的恢复保护。")
            val readBack = gateway.read(ledger.documentId, committed.value.revision) as? P7CCloudResult.Value
                ?: return P7FRecoveryCodeRotationResult.Rejected("新恢复码已暂存；云端回读失败，请使用同一新码重试。")
            if (readBack.value.revision != preflight.value.revision || readBack.value.payloadHash != preflight.value.payloadHash) {
                return P7FRecoveryCodeRotationResult.Rejected("新恢复码已暂存；云端回读校验失败，请使用同一新码重试。")
            }
            database.manualConversationSyncStateDao().save(
                ledger.copy(
                    remoteRevision = readBack.value.revision,
                    payloadHash = readBack.value.payloadHash,
                    localContentHash = localHash,
                    lastSyncedAtEpochMs = now(),
                ),
            )
        }
        accountOwner.promotePendingRecoveryMaterial(session.userId)
        return P7FRecoveryCodeRotationResult.Changed(states.size)
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
        }.distinctBy { it.documentId }.sortedBy { it.documentId }.also {
            require(session.userId == accountOwner.cachedSession()?.userId) { "登录状态已变化。" }
        }
    }

    /**
     * Restores exactly one encrypted text conversation. A pre-existing local ID is never
     * overwritten, and unrelated safe-settings/reminder records are deliberately ignored.
     */
    fun restoreRemoteConversation(documentId: String): P7FCloudConversationRestoreResult = runCatching {
        restoreRemoteConversationInternal(documentId)
    }.getOrElse { P7FCloudConversationRestoreResult.Rejected("云端对话恢复失败，未改动本机数据。") }

    private fun restoreRemoteConversationInternal(documentId: String): P7FCloudConversationRestoreResult {
        if (!documentId.matches(Regex("conversation-[a-f0-9]{40}"))) {
            return P7FCloudConversationRestoreResult.Rejected("云端对话标识无效。")
        }
        if (!accountOwner.configured) return P7FCloudConversationRestoreResult.Rejected("尚未配置 Google 登录与云端服务。")
        val session = accountOwner.cachedSession() ?: return P7FCloudConversationRestoreResult.Rejected("请先登录 Google 账号。")
        if (!accountOwner.recoveryReady(session.userId)) return P7FCloudConversationRestoreResult.Rejected("请先用恢复码完成恢复保护。")
        val configured = P7CAndroidCloudGateway.availability() as? com.nanzhufeng.ai.domain.P7CServiceAvailability.Configured
            ?: return P7FCloudConversationRestoreResult.Rejected("云端服务尚未配置。")
        val remote = when (val read = P7CSupabaseEnvelopeGateway(configured.config, accountOwner.authenticatedTransport()).read(documentId, 0)) {
            is P7CCloudResult.Value -> read.value
            is P7CCloudResult.Rejected -> return P7FCloudConversationRestoreResult.Rejected("云端对话无法读取。")
            P7CCloudResult.Disabled -> return P7FCloudConversationRestoreResult.Rejected("云端服务尚未配置。")
        }
        val opened = accountOwner.withWrappingMaterial(session.userId) { material ->
            NfaiSyncV1Gateway.openWithAccountWrappingMaterial(
                remote.canonicalEnvelope, material, "com.nanzhufeng.ai", documentId, remote.revision,
            )
        } as? NfaiSyncResult.Opened
            ?: return P7FCloudConversationRestoreResult.Rejected("恢复保护与云端密文不匹配。")
        val restored = P7FConversationSyncWireFormat.decode(opened.value.snapshot)
        val existing = conversations.findById(restored.conversation.id)
        if (existing != null) return P7FCloudConversationRestoreResult.AlreadyPresent(existing.conversation.title)
        conversations.save(restored)
        return P7FCloudConversationRestoreResult.Restored(restored.conversation.title)
    }

    fun sync(conversationId: ConversationId): P7FManualConversationSyncResult =
        runCatching { syncInternal(conversationId) }
            .getOrElse { P7FManualConversationSyncResult.Rejected("同步失败，请稍后重试。") }

    private fun syncInternal(conversationId: ConversationId): P7FManualConversationSyncResult {
        if (!accountOwner.configured) return P7FManualConversationSyncResult.Rejected("尚未配置 Google 登录与云端服务。")
        val session = accountOwner.cachedSession()
            ?: return P7FManualConversationSyncResult.Rejected("请先登录 Google 账号。")
        if (!accountOwner.recoveryReady(session.userId)) return P7FManualConversationSyncResult.Rejected("请先在账号页完成恢复保护。")
        val snapshot = conversations.findById(conversationId)
            ?: return P7FManualConversationSyncResult.Rejected("该对话已不存在。")
        eligibilityFailure(snapshot)?.let { return P7FManualConversationSyncResult.Rejected(it) }

        onAuthenticated(session)
        val accountRef = P7BAccountStateMachine.accountRef(session.userId)
        val documentId = documentId(conversationId.value)
        val gateway = P7CSupabaseEnvelopeGateway(
            config = (P7CAndroidCloudGateway.availability() as com.nanzhufeng.ai.domain.P7CServiceAvailability.Configured).config,
            transport = accountOwner.authenticatedTransport(),
        )
        val remote = when (val read = gateway.read(documentId, 0)) {
            is P7CCloudResult.Value -> read.value
            is P7CCloudResult.Rejected -> if (read.code == "REMOTE_MISSING") null else {
                return P7FManualConversationSyncResult.Rejected("无法读取该对话的云端状态。")
            }
            P7CCloudResult.Disabled -> return P7FManualConversationSyncResult.Rejected("云端服务尚未配置。")
        }
        val ledger = database.manualConversationSyncStateDao().find(accountRef, conversationId.value)
        if (remote != null && (ledger == null || ledger.remoteRevision != remote.revision || ledger.payloadHash != remote.payloadHash)) {
            return P7FManualConversationSyncResult.Conflict
        }

        var metadata = accounts.metadata(accountRef) ?: return P7FManualConversationSyncResult.Rejected("账号密钥尚未准备。")
        if (metadata.state == P7BSyncState.AUTHENTICATED_NEEDS_RECOVERY_CONFIRMATION) return P7FManualConversationSyncResult.Rejected("请先在账号页完成恢复保护。")
        if (metadata.state == P7BSyncState.DIRECTION_REQUIRED) {
            val direction = if (remote == null) P7BDirectionFact.LOCAL_PRESENT_EMPTY_REMOTE else P7BDirectionFact.LOCAL_PRESENT_REMOTE_MATCHED
            accounts.chooseDirection("direction-${UUID.randomUUID()}", metadata.revision, accountRef, direction)
            metadata = accounts.metadata(accountRef)!!
        }
        if (metadata.state != P7BSyncState.READY) {
            return P7FManualConversationSyncResult.Rejected("账号同步状态需要先处理。")
        }

        val expectedRevision = remote?.revision ?: 0L
        val (prepared, localContentHash) = preparedSnapshot(snapshot, documentId, expectedRevision + 1)
        if (remote != null && ledger?.localContentHash == localContentHash) {
            val checkedAt = now()
            database.manualConversationSyncStateDao().save(ledger.copy(lastSyncedAtEpochMs = checkedAt))
            return P7FManualConversationSyncResult.Synced(snapshot.conversation.title, checkedAt)
        }
        val sealed = accountOwner.withWrappingMaterial(session.userId) { material ->
            accounts.withReadyDataKey(accountRef) { dataKey ->
                NfaiSyncV1Gateway.sealWithAccountWrappingMaterial(prepared, dataKey, material)
            }
        } as? NfaiSyncResult.Sealed
            ?: return P7FManualConversationSyncResult.Rejected("该对话无法安全加密。")
        val preflight = NfaiSyncV1Gateway.preflight(sealed.canonicalEnvelope) as? NfaiSyncResult.Preflighted
            ?: return P7FManualConversationSyncResult.Rejected("加密完整性校验失败。")
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
        return P7FManualConversationSyncResult.Synced(snapshot.conversation.title, syncedAt)
    }

    /** Periodic work can only revisit conversations that already have a successful manual receipt. */
    fun syncPreviouslySelected(): List<P7FManualConversationSyncResult> {
        val session = accountOwner.cachedSession() ?: return emptyList()
        if (!accountOwner.recoveryReady(session.userId)) return emptyList()
        val accountRef = P7BAccountStateMachine.accountRef(session.userId)
        return database.manualConversationSyncStateDao().listForAccount(accountRef).map { state ->
            sync(ConversationId(state.conversationId))
        }
    }

    fun latestSync(session: P7FCloudSession?): ManualConversationSyncStateEntity? = session?.let {
        database.manualConversationSyncStateDao().listForAccount(P7BAccountStateMachine.accountRef(it.userId)).firstOrNull()
    }

    private fun eligibilityFailure(snapshot: com.nanzhufeng.ai.domain.ConversationSnapshot): String? = when {
        snapshot.conversation.deletedAt != null -> "已删除的对话不会同步。"
        snapshot.draft.text.isNotBlank() || snapshot.draft.attachments.isNotEmpty() -> "请先发送或清空未完成草稿。"
        snapshot.nodes.any { node -> node.content.any { it !is ContentBlock.Text } } -> "该对话含附件或工具结果，当前不会部分上传。"
        else -> null
    }

    private fun preparedSnapshot(
        snapshot: com.nanzhufeng.ai.domain.ConversationSnapshot,
        documentId: String,
        revision: Long,
    ): Pair<NfaiSyncPreparedSnapshot, String> {
        val conversation = snapshot.conversation
        val nodes = JSONArray().also { array ->
            snapshot.nodes.sortedWith(compareBy({ it.createdAt }, { it.id.value })).forEach { node ->
                array.put(JSONObject()
                    .put("id", node.id.value)
                    .put("parentMessageId", node.parentMessageId?.value ?: JSONObject.NULL)
                    .put("siblingPosition", node.siblingPosition)
                    .put("role", node.role.name)
                    .put("createdAtEpochMs", node.createdAt.toEpochMilli())
                    .put("deliveryState", node.deliveryState.name)
                    .put("revision", node.revision.revision)
                    .put("revisesMessageId", node.revision.revisesMessageId?.value ?: JSONObject.NULL)
                    .put("text", JSONArray().also { texts -> node.content.filterIsInstance<ContentBlock.Text>().forEach { texts.put(it.text) } }))
            }
        }
        val content = JSONObject()
            .put("title", conversation.title)
            .put("currentLeafMessageId", conversation.currentLeafMessageId?.value ?: JSONObject.NULL)
            .put("createdAtEpochMs", conversation.createdAt.toEpochMilli())
            .put("updatedAtEpochMs", conversation.updatedAt.toEpochMilli())
            .put("surface", conversation.surface.name)
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
    private fun String.sha256() = MessageDigest.getInstance("SHA-256").digest(toByteArray()).joinToString("") { "%02x".format(it) }
}

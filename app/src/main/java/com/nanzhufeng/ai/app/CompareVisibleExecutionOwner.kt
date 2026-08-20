package com.nanzhufeng.ai.app

import com.nanzhufeng.ai.ai.OpenRouterCompareBatchDispatchAdapter
import com.nanzhufeng.ai.data.AndroidOpenRouterComparePlatformAdapter
import com.nanzhufeng.ai.data.local.RoomCompareBranchExecutionPorts
import com.nanzhufeng.ai.domain.CanonicalContextSnapshotId
import com.nanzhufeng.ai.domain.CanonicalContextSnapshotRef
import com.nanzhufeng.ai.domain.CompareConversationSessionIntent
import com.nanzhufeng.ai.domain.CompareConversationSessionIntentId
import com.nanzhufeng.ai.domain.CompareConversationSessionOwner
import com.nanzhufeng.ai.domain.CompareConversationSessionStore
import com.nanzhufeng.ai.domain.CompareConversationSessionResult
import com.nanzhufeng.ai.domain.CompareExecutionApplicationBlocker
import com.nanzhufeng.ai.domain.CompareExecutionApplicationOwner
import com.nanzhufeng.ai.domain.CompareExecutionApplicationRequest
import com.nanzhufeng.ai.domain.CompareExecutionApplicationResult
import com.nanzhufeng.ai.domain.CompareExecutionGrantedPlan
import com.nanzhufeng.ai.domain.CompareExecutionSummaryConfirmation
import com.nanzhufeng.ai.domain.CompareMvpLogicalModels
import com.nanzhufeng.ai.domain.ConversationDraft
import com.nanzhufeng.ai.domain.ConversationDraftSubmissionResult
import com.nanzhufeng.ai.domain.ConversationId
import com.nanzhufeng.ai.domain.ConversationRepository
import com.nanzhufeng.ai.domain.ModelDeploymentSelection
import com.nanzhufeng.ai.domain.MultiProviderModelRegistry
import com.nanzhufeng.ai.domain.OrchestrationRequestId
import com.nanzhufeng.ai.domain.ProviderCredentialStore
import com.nanzhufeng.ai.domain.ProviderTransportEphemeralTextInput
import com.nanzhufeng.ai.domain.SubmitConversationDraftUseCase
import java.security.MessageDigest
import java.time.Clock

/**
 * The only Android-visible Compare flow owner. It keeps the ordinary composer submit path
 * untouched: this owner is entered exclusively by the explicit Compare menu item. It asks the
 * existing MM-O4 execution guard for a scoped one-shot grant, then commits the selected local
 * draft as its user parent before handing the same grant to O4-G. The explicit action is the
 * user command; this layer never creates a second product confirmation screen.
 */
class CompareVisibleExecutionOwner(
    private val conversations: ConversationRepository,
    private val submitDraft: SubmitConversationDraftUseCase,
    private val registry: MultiProviderModelRegistry,
    private val compare: CompareExecutionApplicationOwner,
    private val sessionOwner: CompareConversationSessionOwner,
    private val sessionStore: CompareConversationSessionStore,
    private val credentials: ProviderCredentialStore,
    private val branchPorts: RoomCompareBranchExecutionPorts,
    private val clock: Clock,
) {
    private data class Pending(
        val conversationId: ConversationId,
        val draftFingerprint: String,
        val attachmentCount: Int,
        val context: CanonicalContextSnapshotRef,
    )

    private val pending = mutableMapOf<String, Pending>()

    fun request(conversationId: ConversationId, draft: ConversationDraft): CompareExecutionApplicationResult {
        val snapshot = conversations.findById(conversationId)
            ?: return CompareExecutionApplicationResult.Blocked(CompareExecutionApplicationBlocker.REGISTRY_REJECTED)
        val targets = targets()
            ?: return CompareExecutionApplicationResult.Blocked(CompareExecutionApplicationBlocker.REGISTRY_REJECTED)
        val context = canonicalContext(snapshot.conversation.id, snapshot.conversation.revision, draft.text)
        val result = compare.requestConfirmation(
            CompareExecutionApplicationRequest(
                requestId = OrchestrationRequestId("compare-visible:${snapshot.conversation.id.value}:${snapshot.conversation.revision}:${sha256(draft.text).take(24)}"),
                context = context,
                targets = targets,
                text = ProviderTransportEphemeralTextInput(draft.text),
                outputTokenLimit = OUTPUT_TOKEN_LIMIT,
                attachmentCount = draft.attachments.size,
            ),
        )
        if (result is CompareExecutionApplicationResult.Confirmation) {
            pending[result.value.id] = Pending(conversationId, sha256(draft.text), draft.attachments.size, context)
        }
        return result
    }

    /** The explicit Compare action is itself the product command; no second UI acknowledgement. */
    fun execute(conversationId: ConversationId, draft: ConversationDraft): CompareVisibleExecutionResult = when (val requested = request(conversationId, draft)) {
        is CompareExecutionApplicationResult.Blocked -> CompareVisibleExecutionResult.Blocked(requested.blocker)
        is CompareExecutionApplicationResult.Granted -> CompareVisibleExecutionResult.Blocked(CompareExecutionApplicationBlocker.CONFIRMATION_NOT_ACKNOWLEDGED)
        is CompareExecutionApplicationResult.Confirmation -> when (val acknowledged = setAcknowledgement(requested.value.id, true)) {
            is CompareExecutionApplicationResult.Confirmation -> confirm(acknowledged.value.id)
            is CompareExecutionApplicationResult.Blocked -> CompareVisibleExecutionResult.Blocked(acknowledged.blocker)
            is CompareExecutionApplicationResult.Granted -> confirm(requested.value.id)
        }
    }

    fun setAcknowledgement(id: String, checked: Boolean): CompareExecutionApplicationResult =
        compare.setAcknowledgement(id, checked)

    fun cancel(id: String) {
        pending.remove(id)
        compare.cancel(id)
    }

    fun expire(id: String): CompareExecutionApplicationResult {
        pending.remove(id)
        return compare.cancel(id)
    }

    /** Confirm -> local user parent -> two-branch session -> one batch O4-G hand-off. */
    fun confirm(id: String): CompareVisibleExecutionResult {
        val granted = compare.confirm(id) as? CompareExecutionApplicationResult.Granted
            ?: return CompareVisibleExecutionResult.Blocked(CompareExecutionApplicationBlocker.CONFIRMATION_NOT_ACKNOWLEDGED)
        val pendingRequest = pending.remove(id)
            ?: return cancelAndBlock(id, CompareExecutionApplicationBlocker.CONFIRMATION_UNKNOWN)
        val current = conversations.findById(pendingRequest.conversationId)
            ?: return cancelAndBlock(id, CompareExecutionApplicationBlocker.DISPATCH_STORE_REJECTED)
        if (sha256(current.draft.text) != pendingRequest.draftFingerprint || current.draft.attachments.size != pendingRequest.attachmentCount) {
            return cancelAndBlock(id, CompareExecutionApplicationBlocker.CONFIRMATION_SCOPE_CHANGED)
        }
        if (current.draft.attachments.isNotEmpty()) return cancelAndBlock(id, CompareExecutionApplicationBlocker.ATTACHMENTS_NOT_SUPPORTED)
        val submitted = submitDraft.execute(pendingRequest.conversationId)
        val userSnapshot = (submitted as? ConversationDraftSubmissionResult.Submitted)?.snapshot
            ?: return cancelAndBlock(id, CompareExecutionApplicationBlocker.DISPATCH_STORE_REJECTED)
        val parent = userSnapshot.conversation.currentLeafMessageId
            ?: return cancelAndBlock(id, CompareExecutionApplicationBlocker.DISPATCH_SESSION_SCOPE_MISMATCH)
        val plan = sessionOwner.plan(
            CompareConversationSessionIntent(
                id = CompareConversationSessionIntentId("compare-visible:${granted.plan.confirmationId}"),
                grantedPlan = granted.plan,
                conversationId = pendingRequest.conversationId,
                parentUserMessageId = parent,
                canonicalContext = pendingRequest.context,
            ),
            userSnapshot,
        )
        val session = (plan as? CompareConversationSessionResult.Planned)?.plan
            ?: return cancelAndBlock(id, CompareExecutionApplicationBlocker.DISPATCH_SESSION_SCOPE_MISMATCH)
        if (sessionStore.persist(session) !is com.nanzhufeng.ai.domain.CompareSessionStoreResult.Stored) {
            return cancelAndBlock(id, CompareExecutionApplicationBlocker.DISPATCH_STORE_REJECTED)
        }
        val platform = AndroidOpenRouterComparePlatformAdapter.createForConfirmedCompare(granted.plan, credentials, clock)
            ?: return cancelAndBlock(id, CompareExecutionApplicationBlocker.REGISTRY_REJECTED)
        val dispatch = compare.dispatch(
            confirmationId = id,
            sessionId = session.sessionId,
            store = sessionStore,
            port = OpenRouterCompareBatchDispatchAdapter(sessionStore, platform, platform, branchPorts, branchPorts, clock),
        )
        return when (dispatch) {
            is com.nanzhufeng.ai.domain.CompareDispatchApplicationResult.Accepted -> CompareVisibleExecutionResult.Accepted(dispatch.sessionId.value)
            is com.nanzhufeng.ai.domain.CompareDispatchApplicationResult.Blocked -> CompareVisibleExecutionResult.Blocked(dispatch.blocker)
        }
    }

    private fun cancelAndBlock(id: String, blocker: CompareExecutionApplicationBlocker): CompareVisibleExecutionResult {
        compare.cancel(id)
        return CompareVisibleExecutionResult.Blocked(blocker)
    }

    private fun targets(): List<ModelDeploymentSelection>? {
        val snapshot = registry.currentSnapshot() ?: return null
        return CompareMvpLogicalModels.defaultPair.map { logical ->
            val deployment = snapshot.deployments.singleOrNull { it.logicalModelId == logical.id } ?: return null
            ModelDeploymentSelection(logical.id, deployment.id, deployment.provider)
        }
    }

    private fun canonicalContext(conversationId: ConversationId, revision: Long, text: String): CanonicalContextSnapshotRef {
        val hash = sha256("conversation=${conversationId.value}\nrevision=$revision\ntext=${sha256(text)}")
        return CanonicalContextSnapshotRef(CanonicalContextSnapshotId("compare-visible:${conversationId.value}:$revision"), hash, revision)
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    companion object { const val OUTPUT_TOKEN_LIMIT = 512L }
}

sealed interface CompareVisibleExecutionResult {
    data class Accepted(val sessionId: String) : CompareVisibleExecutionResult
    data class Blocked(val blocker: CompareExecutionApplicationBlocker) : CompareVisibleExecutionResult
}

package com.nanzhufeng.ai.ai

import com.nanzhufeng.ai.domain.AppendConversationMessageUseCase
import com.nanzhufeng.ai.domain.AppendMessageRequest
import com.nanzhufeng.ai.domain.AutoRoutingFacts
import com.nanzhufeng.ai.domain.AutoRoutingTaskClassifier
import com.nanzhufeng.ai.domain.CapabilityAwareAutoModelRouter
import com.nanzhufeng.ai.domain.LoadChatRoutingPolicyUseCase
import com.nanzhufeng.ai.domain.ComposerModelRoutingCatalog
import com.nanzhufeng.ai.domain.ContentBlock
import com.nanzhufeng.ai.domain.CONVERSATION_ATTACHMENT_MAX_BYTES
import com.nanzhufeng.ai.domain.ConversationDraftSubmissionResult
import com.nanzhufeng.ai.domain.ConversationId
import com.nanzhufeng.ai.domain.ConversationRepository
import com.nanzhufeng.ai.domain.DirectChatCallAuditRecord
import com.nanzhufeng.ai.domain.DirectChatCallAuditStore
import com.nanzhufeng.ai.domain.ErrorBodyRedactor
import com.nanzhufeng.ai.domain.ProviderDiagnosticErrorClass
import com.nanzhufeng.ai.domain.ProviderDiagnosticRecord
import com.nanzhufeng.ai.domain.ProviderDiagnosticStore
import com.nanzhufeng.ai.domain.classifyProviderFailure
import com.nanzhufeng.ai.domain.AiRuntimeEventId
import com.nanzhufeng.ai.domain.AiRuntimeSecurityMetadata
import com.nanzhufeng.ai.domain.ApplyConversationRuntimeEventUseCase
import com.nanzhufeng.ai.domain.ConversationRuntimePersistenceResult
import com.nanzhufeng.ai.domain.ConversationRuntimeState
import com.nanzhufeng.ai.domain.RuntimeCompleted
import com.nanzhufeng.ai.domain.RuntimeContentDelta
import com.nanzhufeng.ai.domain.RuntimeFailed
import com.nanzhufeng.ai.domain.SubmitConversationDraftAndStartProviderRuntimeUseCase
import com.nanzhufeng.ai.domain.ProviderRuntimeDraftSubmissionResult
import com.nanzhufeng.ai.domain.StartProviderRuntimeForExistingUserUseCase
import com.nanzhufeng.ai.domain.ProviderRuntimeRetryResult
import com.nanzhufeng.ai.domain.LoadModelServiceConfigurationUseCase
import com.nanzhufeng.ai.domain.LocalContextBroker
import com.nanzhufeng.ai.domain.MessageRole
import com.nanzhufeng.ai.domain.MessageDeliveryState
import com.nanzhufeng.ai.domain.ModelPresetId
import com.nanzhufeng.ai.domain.ModelRegistryResolution
import com.nanzhufeng.ai.domain.AiTaskError
import com.nanzhufeng.ai.domain.NanfengModelServiceCatalog
import com.nanzhufeng.ai.domain.P6GModelSelectionOwner
import com.nanzhufeng.ai.domain.ProviderCredentialStore
import com.nanzhufeng.ai.domain.ProviderId
import com.nanzhufeng.ai.domain.SubmitConversationDraftUseCase
import com.nanzhufeng.ai.domain.VersionedModelRegistry
import com.nanzhufeng.ai.domain.VerifyOpenRouterRegistryResult
import com.nanzhufeng.ai.domain.VerifyOpenRouterRegistryUseCase
import com.nanzhufeng.ai.domain.PrivateAttachmentRepository
import com.nanzhufeng.ai.domain.PrivateAttachmentStore
import com.nanzhufeng.ai.domain.AttachmentReadResult
import com.nanzhufeng.ai.domain.NormalChatSendAttempt
import com.nanzhufeng.ai.domain.NormalChatSendAttemptId
import com.nanzhufeng.ai.domain.NormalChatSendAttemptStatus
import com.nanzhufeng.ai.domain.NormalChatSendAttemptStore
import com.nanzhufeng.ai.domain.AssistantResponseModelAttribution
import com.nanzhufeng.ai.domain.AssistantResponseModelAttributionStore
import com.nanzhufeng.ai.domain.ModelResolver
import com.nanzhufeng.ai.domain.ModelHealthReporter
import com.nanzhufeng.ai.domain.ResolvedModelResult
import com.nanzhufeng.ai.domain.ContextBudget
import com.nanzhufeng.ai.domain.ContextSelectionAuditRecord
import com.nanzhufeng.ai.domain.ContextSelectionAuditStore
import com.nanzhufeng.ai.domain.ContextSelectionSource
import com.nanzhufeng.ai.domain.ContextRetrievalAudit
import com.nanzhufeng.ai.domain.LocalContextBroker.AssemblyStatus
import com.nanzhufeng.ai.domain.ModelProfileRefresher
import com.nanzhufeng.ai.domain.ModelProfileRefreshResult
import com.nanzhufeng.ai.domain.AssistantExperienceSettings
import com.nanzhufeng.ai.domain.ConversationCostEstimator
import com.nanzhufeng.ai.domain.ConversationCostSource
import com.nanzhufeng.ai.domain.ProviderCost
import com.nanzhufeng.ai.domain.ProviderUsage
import com.nanzhufeng.ai.domain.MemorySummaryDraft
import com.nanzhufeng.ai.domain.MemorySummaryPolicy
import com.nanzhufeng.ai.domain.MemoryMutationResult
import com.nanzhufeng.ai.domain.ConversationTitleRefiner
import com.nanzhufeng.ai.domain.ConversationTitleRefinementResult
import com.nanzhufeng.ai.domain.ConversationAutoTitle
import com.nanzhufeng.ai.domain.openingTitleSource
import com.nanzhufeng.ai.domain.withRequiredOpeningAddress
import com.nanzhufeng.ai.domain.withoutLeakedReasoningTailBeforeOpeningAddress
import java.time.Clock
import java.util.concurrent.ConcurrentHashMap

/** Ordinary composer send. The chosen logical slot determines the concrete provider/model. */
class NormalChatOpenRouterExecutor(
    private val configuration: LoadModelServiceConfigurationUseCase,
    private val conversations: ConversationRepository,
    private val registry: VersionedModelRegistry,
    private val credentials: ProviderCredentialStore,
    private val selection: P6GModelSelectionOwner,
    private val submitDraft: SubmitConversationDraftUseCase,
    private val appendMessage: AppendConversationMessageUseCase,
    private val contextBroker: LocalContextBroker,
    private val transport: ProviderChatTransport,
    private val audit: DirectChatCallAuditStore,
    private val diagnostics: ProviderDiagnosticStore,
    private val sendAttempts: NormalChatSendAttemptStore,
    private val responseModelAttributions: AssistantResponseModelAttributionStore,
    private val adapters: ChatProviderAdapters,
    private val modelResolver: ModelResolver,
    private val modelHealthReporter: ModelHealthReporter,
    private val contextSelectionAudits: ContextSelectionAuditStore,
    private val modelProfileRefresher: ModelProfileRefresher = ModelProfileRefresher { _, _, _ -> ModelProfileRefreshResult.SKIPPED },
    private val clock: Clock,
    private val verifyOpenRouterRegistry: VerifyOpenRouterRegistryUseCase,
    private val loadRoutingPolicy: LoadChatRoutingPolicyUseCase,
    private val attachmentRepository: PrivateAttachmentRepository,
    private val attachmentStore: PrivateAttachmentStore,
    private val submitDraftAndStartProviderRuntime: SubmitConversationDraftAndStartProviderRuntimeUseCase,
    private val startProviderRuntimeForExistingUser: StartProviderRuntimeForExistingUserUseCase,
    private val applyRuntimeEvent: ApplyConversationRuntimeEventUseCase,
    private val runtimeRepository: com.nanzhufeng.ai.domain.ConversationRuntimeRepository,
    private val attachmentBridge: ChatAttachmentBridge = PassthroughChatAttachmentBridge,
    private val loadAssistantExperienceSettings: () -> AssistantExperienceSettings = { AssistantExperienceSettings() },
    private val resolveConversationWebSearchEnabled: (ConversationId, Boolean) -> Boolean = { _, globalEnabled -> globalEnabled },
    private val saveMemorySummary: (ConversationId, MemorySummaryDraft) -> MemoryMutationResult = { _, _ ->
        MemoryMutationResult.Rejected(com.nanzhufeng.ai.domain.MemoryRejectionCode.INVALID_ACTION)
    },
    private val onConversationCompleted: (ConversationId) -> Unit = {},
    private val conversationTitleRefiner: ConversationTitleRefiner,
) {
    private val activeCalls = ConcurrentHashMap<ConversationId, ProviderChatCancellation>()
    private val cancellationRequested = ConcurrentHashMap.newKeySet<ConversationId>()

    sealed interface Result {
        data object Sent : Result
        /** The user-visible answer is durable; only an optional local supplement was not retained. */
        data class SentWithNotice(val notice: CompletionNotice) : Result
        data class Blocked(val code: Code) : Result
        data class Failed(val code: Code) : Result
    }

    enum class CompletionNotice { REASONING_NOT_SAVED, TOOL_CALL_NOT_EXECUTED }

    enum class Code {
        SERVICE_DISABLED, CREDENTIAL_MISSING, REGISTRY_UNVERIFIED, MODEL_UNAVAILABLE,
        ATTACHMENTS_UNSUPPORTED, ATTACHMENT_MODEL_UNSUPPORTED, ATTACHMENT_BRIDGE_UNAVAILABLE, CONTEXT_LIMIT, DRAFT_UNAVAILABLE, AUTHENTICATION, BALANCE, RATE_LIMIT,
        TIMEOUT, NETWORK, SERVICE, MODEL_NOT_FOUND, STREAM_REQUIRED, INVALID_REQUEST, RESPONSE_FORMAT, TOOL_CALL_UNSUPPORTED,
        LOCAL_RESPONSE_PERSISTENCE, LOCAL_ACCOUNTING_PERSISTENCE, LOCAL_ATTEMPT_PERSISTENCE,
        RECOVERY_UNAVAILABLE, RECOVERY_MODEL_CHANGED,
    }

    /**
     * Content-free recovery projection.  The user has already authorized the original message;
     * retry never silently changes provider, model, or idempotency key.
     */
    data class Recovery(
        val status: NormalChatSendAttemptStatus,
        val providerId: ProviderId,
        val modelId: String,
        val canRetry: Boolean,
        val duplicateChargePossible: Boolean,
        val failureReason: String,
    )

    /** Called only by the visible stop action; disconnects the active socket before state cleanup. */
    fun cancelActive(conversationId: ConversationId): Boolean {
        // A foreground service can receive Stop immediately after it starts, before the executor
        // has built its socket. Keep that intent and apply it as soon as the call is registered.
        cancellationRequested += conversationId
        return activeCalls.remove(conversationId)?.let { call -> call.cancel(); true } ?: false
    }

    fun recoveryForConversation(conversationId: ConversationId): Recovery? =
        sendAttempts.findLatestForConversation(conversationId)?.let { attempt ->
            if (attempt.safeErrorCode == "USER_MARKED_FAILED") return@let null
            when (attempt.status) {
                NormalChatSendAttemptStatus.UNKNOWN, NormalChatSendAttemptStatus.FAILED -> Recovery(
                    status = attempt.status,
                    providerId = attempt.providerId,
                    modelId = attempt.modelId,
                    canRetry = true,
                    // The direct Provider APIs are not treated as proving server-side de-duplication.
                    duplicateChargePossible = true,
                    failureReason = recoveryFailureReason(attempt.status, attempt.safeErrorCode),
                )
                else -> null
            }
        }

    /** Explicit user action only. It retries the original direct request. */
    fun retryLatestAttempt(conversationId: ConversationId): Result {
        cancellationRequested -= conversationId
        val attempt = sendAttempts.findLatestForConversation(conversationId) ?: return Result.Blocked(Code.RECOVERY_UNAVAILABLE)
        val resumableStatuses = setOf(NormalChatSendAttemptStatus.UNKNOWN, NormalChatSendAttemptStatus.FAILED)
        if (attempt.status !in resumableStatuses) {
            return Result.Blocked(Code.RECOVERY_UNAVAILABLE)
        }
        val snapshot = conversations.findById(conversationId) ?: return Result.Blocked(Code.RECOVERY_UNAVAILABLE)
        val user = snapshot.nodes.firstOrNull { it.id == attempt.messageId && it.role == MessageRole.USER }
            ?: return Result.Blocked(Code.RECOVERY_UNAVAILABLE)
        val text = user.content.filterIsInstance<ContentBlock.Text>().joinToString("") { it.text }
        val attachments = prepareAttachments(user.content.filterIsInstance<ContentBlock.Attachment>())
            ?: return Result.Blocked(Code.ATTACHMENTS_UNSUPPORTED)
        val preset = NanfengModelServiceCatalog.presets.asSequence().map { it.id }
            .firstOrNull { presetId ->
                NanfengModelServiceCatalog.providerFor(presetId) == attempt.providerId &&
                    (modelResolver.resolve(presetId) as? ResolvedModelResult.Resolved)?.model?.modelId == attempt.modelId
            } ?: return Result.Blocked(Code.RECOVERY_MODEL_CHANGED)
        // A recovered request keeps the original attempt/key, but owns a fresh persisted
        // assistant placeholder so the retry is visible before its first provider chunk.
        val restarted = when (val result = startProviderRuntimeForExistingUser.execute(conversationId, user.id)) {
            is ProviderRuntimeRetryResult.Started -> result
            is ProviderRuntimeRetryResult.Rejected -> return Result.Failed(Code.LOCAL_RESPONSE_PERSISTENCE)
        }
        val cancellation = ProviderChatCancellation().also { call ->
            activeCalls[conversationId] = call
            if (cancellationRequested.remove(conversationId)) call.cancel()
        }
        val resumedRuntime = ActiveProviderRuntime(restarted.runtime)
        val retried = try {
            requestOne(
                preset = preset,
                conversationId = conversationId,
                userMessageId = user.id,
                snapshot = restarted.snapshot,
                userMessage = text,
                attachments = attachments,
                choice = ComposerModelRoutingCatalog.auto,
                runtime = resumedRuntime,
                onStreamProgress = {},
                cancellation = cancellation,
                existingAttempt = attempt,
            )
        } finally {
            activeCalls.remove(conversationId, cancellation)
        }
        return when (retried) {
            is OneResult.Reply -> {
                if (!recordResponseAttribution(resumedRuntime.state.messageId, retried)) return Result.Failed(Code.LOCAL_ACCOUNTING_PERSISTENCE)
                contextSelectionAudits.bindAnswer(retried.attempt.attemptId, resumedRuntime.state.messageId)
                completedResult(listOf(retried)).also { maybeRefineOpeningTitle(conversationId); onConversationCompleted(conversationId) }
            }
            is OneResult.Blocked -> Result.Blocked(retried.code).also { resumedRuntime.fail(retried.code.name) }
            is OneResult.Failed -> Result.Failed(retried.code).also { resumedRuntime.fail(retried.code.name) }
            OneResult.Cancelled -> Result.Sent.also { resumedRuntime.cancel() }
        }
    }

    /** Explicitly abandons an uncertain request. It never deletes the durable audit fact. */
    fun markLatestAttemptFailed(conversationId: ConversationId): Boolean =
        sendAttempts.findLatestForConversation(conversationId)?.let { attempt ->
            sendAttempts.markFailed(attempt.attemptId, clock.instant(), "USER_MARKED_FAILED") != null
        } ?: false

    /** Invoked immediately after the user message and cleared draft have committed locally. */
    fun execute(
        conversationId: ConversationId,
        onLocalSubmission: () -> Unit = {},
        onStreamProgress: () -> Unit = {},
    ): Result {
        cancellationRequested -= conversationId
        val selectedChoice = choiceForConversation(conversationId)
        var providerRuntime: ConversationRuntimeState? = null
        val submitted = if (selectedChoice.isCompare) {
            submitDraft.execute(conversationId)
        } else when (val started = submitDraftAndStartProviderRuntime.execute(conversationId)) {
            is ProviderRuntimeDraftSubmissionResult.Started -> {
                providerRuntime = started.runtime
                ConversationDraftSubmissionResult.Submitted(started.submittedSnapshot)
            }
            is ProviderRuntimeDraftSubmissionResult.Rejected -> return Result.Blocked(Code.DRAFT_UNAVAILABLE)
        }
        if (submitted !is ConversationDraftSubmissionResult.Submitted) return Result.Blocked(Code.DRAFT_UNAVAILABLE)
        val runtime = if (selectedChoice.isCompare) null else providerRuntime?.let(::ActiveProviderRuntime)
        if (!selectedChoice.isCompare && runtime == null) return Result.Failed(Code.LOCAL_RESPONSE_PERSISTENCE)
        val latest = submitted.snapshot.nodes.lastOrNull { it.role == MessageRole.USER }
            ?: return Result.Blocked(Code.DRAFT_UNAVAILABLE)
        val userMessage = latest.content.filterIsInstance<ContentBlock.Text>().joinToString("") { it.text }
        if (userMessage.isBlank() && latest.content.none { it is ContentBlock.Attachment }) return Result.Blocked(Code.DRAFT_UNAVAILABLE)
        val hasMedia = latest.content.any { it is ContentBlock.Attachment }

        val selectedId = selection.readConversationOverride(conversationId).modelId
            ?: selection.readGlobalDefault().modelId
        val choice = ComposerModelRoutingCatalog.choice(selectedId)
        val routingPolicy = loadRoutingPolicy.execute()
        val automatic = routingPolicy.autoRoutingEnabled && (selectedId == null || choice == ComposerModelRoutingCatalog.auto)
        // Persisted selections must never become an implicit fallback after a product model is
        // removed. Stop before loading a credential or preparing an external request.
        if (!automatic && ComposerModelRoutingCatalog.isRetired(selectedId)) {
            return Result.Blocked(Code.MODEL_NOT_FOUND).also { runtime?.fail(Code.MODEL_NOT_FOUND.name) }
        }
        val attachmentPayloads = when {
            !hasMedia -> emptyList()
            else -> prepareAttachments(latest.content.filterIsInstance<ContentBlock.Attachment>())
                ?: return Result.Blocked(Code.ATTACHMENTS_UNSUPPORTED).also { runtime?.fail(Code.ATTACHMENTS_UNSUPPORTED.name) }
        }
        val autoFacts = AutoRoutingFacts(
            hasAttachment = hasMedia,
            requiresImage = attachmentPayloads.any { it.kind == ChatAttachmentKind.IMAGE },
            requiresPdf = attachmentPayloads.any { it.kind == ChatAttachmentKind.PDF },
            requiresVideo = attachmentPayloads.any { it.kind == ChatAttachmentKind.VIDEO },
            requiresAudio = attachmentPayloads.any { it.kind == ChatAttachmentKind.AUDIO },
            requiresFile = attachmentPayloads.any { it.kind == ChatAttachmentKind.FILE },
            requiresComplexReasoning = AutoRoutingTaskClassifier.requiresComplexReasoning(userMessage),
        )
        val autoPreset = CapabilityAwareAutoModelRouter(modelResolver).resolve(autoFacts) { candidate ->
            val candidateProvider = NanfengModelServiceCatalog.providerFor(candidate)
            configuration.execute(candidateProvider)?.settings?.enabled == true &&
                credentials.hasCredential(candidateProvider)
        }
        // Ordinary chat has one explicit egress attempt.  A failed request is surfaced with its
        // selected model and never silently replayed to another model or Provider.
        val presets = if (automatic) listOf(autoPreset) else choice.routes
        // Public catalog verification is content-free and no longer a manual prerequisite for
        // sending.  The actual model request is still refused if the exact logical mapping is
        // unavailable; a user never gets silently downgraded to a nearby model.
        val openRouterPresets = presets.filter {
            NanfengModelServiceCatalog.providerFor(it) == ProviderId.OPENROUTER
        }
        // K3 and Grok are explicit, fast-changing OpenRouter selections. A stored snapshot can
        // still resolve an ID that the public catalog has since withdrawn or changed. Verify
        // their exact mapping before any user material or Key is read; never approximate a
        // similarly named model when the provider catalog no longer confirms it.
        val requiresFreshOpenRouterVerification = openRouterPresets.any {
            it in setOf(ModelPresetId.KIMI_K3, ModelPresetId.GROK_4_1_FAST)
        }
        // Other OpenRouter selections retain the existing cold-start behaviour: refresh only
        // when the stored directory cannot resolve their exact logical preset.
        if (requiresFreshOpenRouterVerification || openRouterPresets.any {
                registry.resolve(ProviderId.OPENROUTER, it) !is ModelRegistryResolution.Resolved
            }
        ) {
            val verified = verifyOpenRouterRegistry.execute()
            if (requiresFreshOpenRouterVerification) {
                // Exact explicit routes do not use an old snapshot or cold-start fallback. A
                // successful refresh that lacks the selected ID means no request may be made;
                // a refresh failure is likewise not permission to egress user material blindly.
                if (verified !is VerifyOpenRouterRegistryResult.Verified) {
                    return Result.Blocked(Code.REGISTRY_UNVERIFIED).also { runtime?.fail(Code.REGISTRY_UNVERIFIED.name) }
                }
                if (openRouterPresets.any { registry.resolve(ProviderId.OPENROUTER, it) !is ModelRegistryResolution.Resolved }) {
                    return Result.Blocked(Code.MODEL_NOT_FOUND).also { runtime?.fail(Code.MODEL_NOT_FOUND.name) }
                }
            }
        }
        val cancellation = runtime?.let { active ->
            ProviderChatCancellation().also { call ->
                activeCalls[active.state.conversationId] = call
                if (cancellationRequested.remove(active.state.conversationId)) call.cancel()
            }
        }
        onLocalSubmission()
        val replies = mutableListOf<Pair<ModelPresetId, OneResult.Reply>>()
        var lastBlocked: Code? = null
        var lastFailed: Code? = null
        for (preset in presets) {
            val request = requestOne(preset, conversationId, latest.id, submitted.snapshot, userMessage, attachmentPayloads, choice, runtime, onStreamProgress, cancellation)
            if (cancellation != null) activeCalls.remove(conversationId, cancellation)
            when (request) {
                is OneResult.Reply -> {
                    // The streaming path creates its assistant node before the HTTP response
                    // finishes. Enrich the same message-owned attribution after final usage is
                    // known, rather than manufacturing a second accounting record.
                    if (runtime != null && !recordResponseAttribution(runtime.state.messageId, request)) {
                        return Result.Failed(Code.LOCAL_ACCOUNTING_PERSISTENCE)
                    }
                    runtime?.let { active -> contextSelectionAudits.bindAnswer(request.attempt.attemptId, active.state.messageId) }
                    replies += preset to request
                }
                is OneResult.Blocked -> {
                    lastBlocked = request.code
                    if (!automatic) return Result.Blocked(request.code).also { runtime?.fail(request.code.name) }
                }
                is OneResult.Failed -> {
                    lastFailed = request.code
                    if (!automatic) return Result.Failed(request.code).also { runtime?.fail(request.code.name) }
                }
                OneResult.Cancelled -> return Result.Sent
            }
            if (!choice.isCompare && replies.isNotEmpty()) break
        }
        if (replies.isEmpty()) return (lastBlocked?.let(Result::Blocked) ?: Result.Failed(lastFailed ?: Code.SERVICE)).also { runtime?.fail((lastBlocked ?: lastFailed ?: Code.SERVICE).name) }
        if (choice.isCompare && replies.size != choice.routes.size) return lastBlocked?.let(Result::Blocked) ?: Result.Failed(lastFailed ?: Code.SERVICE)
        if (runtime != null) return completedResult(replies.map { it.second }).also {
            maybeRefineOpeningTitle(conversationId)
            onConversationCompleted(conversationId)
        }
        val openingPrefix = loadAssistantExperienceSettings().firstReplyAddressPrefix(
            userMessage = userMessage,
            isFirstAssistantReply = submitted.snapshot.nodes.none { node ->
                node.role == MessageRole.ASSISTANT && node.deliveryState == MessageDeliveryState.COMPLETE
            },
        )
        val renderedBlocks = if (choice.isCompare) {
            buildList {
                openingPrefix?.let { add(ContentBlock.Text(it)) }
                replies.forEachIndexed { index, (preset, reply) ->
                    if (index > 0) add(ContentBlock.Text("\n"))
                    add(ContentBlock.Text("【${NanfengModelServiceCatalog.preset(preset).displayName}】"))
                    reply.reasoning?.let { add(ContentBlock.Reasoning(it)) }
                    reply.toolCalls.forEach { add(ContentBlock.ProviderToolCall(it.id, it.name, it.argumentsJson)) }
                    add(ContentBlock.Text(reply.text))
                }
            }
        } else replies.single().second.let { reply ->
            buildList {
                reply.reasoning?.let { add(ContentBlock.Reasoning(it)) }
                reply.toolCalls.forEach { add(ContentBlock.ProviderToolCall(it.id, it.name, it.argumentsJson)) }
                add(ContentBlock.Text(reply.text.withRequiredOpeningAddress(openingPrefix)))
            }
        }
        val messageId = com.nanzhufeng.ai.domain.MessageNodeId.new()
        return when (appendMessage.execute(submitted.snapshot, AppendMessageRequest(MessageRole.ASSISTANT, renderedBlocks, messageId = messageId))) {
            is com.nanzhufeng.ai.domain.ConversationMutationResult.Saved -> {
                if (!replies.all { (_, reply) -> recordResponseAttribution(messageId, reply) }) return Result.Failed(Code.LOCAL_ACCOUNTING_PERSISTENCE)
                completedResult(replies.map { it.second }).also {
                replies.forEach { (_, reply) -> contextSelectionAudits.bindAnswer(reply.attempt.attemptId, messageId) }
                maybeRefineOpeningTitle(conversationId)
                onConversationCompleted(conversationId)
            }
            }
            is com.nanzhufeng.ai.domain.ConversationMutationResult.Rejected -> Result.Failed(Code.LOCAL_RESPONSE_PERSISTENCE)
        }
    }

    private fun completedResult(replies: List<OneResult.Reply>): Result =
        replies.firstOrNull { it.completionNotice != null }?.completionNotice?.let(Result::SentWithNotice) ?: Result.Sent

    private fun choiceForConversation(conversationId: ConversationId) =
        ComposerModelRoutingCatalog.choice(selection.readConversationOverride(conversationId).modelId)

    /** K3 starts from a clean current-path context, then keeps only its own exact protocol suffix. */
    private fun kimiK3ContinuationMessages(
        snapshot: com.nanzhufeng.ai.domain.ConversationSnapshot,
        selected: List<LocalContextBroker.Message>,
        systemFact: String,
    ): List<ChatHistoryMessage> {
        val nodes = snapshot.nodes.associateBy { it.id }
        val currentPath = selected.filter(LocalContextBroker.Message::isCurrentPathMessage)
        val attribution = responseModelAttributions.forMessages(currentPath.mapNotNull(LocalContextBroker.Message::messageId))
        fun LocalContextBroker.Message.assistantModelIds(): List<String> =
            messageId?.let { attribution[it] }.orEmpty().map { it.modelId }
        val lastForeignAssistant = currentPath.indexOfLast { message ->
            message.role == MessageRole.ASSISTANT && message.assistantModelIds().any { it != KIMI_K3_MODEL_ID }
        }
        val firstK3Assistant = currentPath.withIndex().firstOrNull { (index, message) ->
            index > lastForeignAssistant && message.role == MessageRole.ASSISTANT && KIMI_K3_MODEL_ID in message.assistantModelIds()
        }?.index ?: -1
        val suffixStart = if (firstK3Assistant >= 0) (firstK3Assistant - 1).coerceAtLeast(lastForeignAssistant + 1) else currentPath.lastIndex
        val allowedCurrentIds = currentPath.drop(suffixStart.coerceAtLeast(0)).mapNotNull(LocalContextBroker.Message::messageId).toSet()
        return listOf(ChatHistoryMessage("system", systemFact)) + selected.mapNotNull { message ->
            if (message.isCurrentPathMessage && message.messageId !in allowedCurrentIds) return@mapNotNull null
            val node = message.messageId?.let(nodes::get)
            ChatHistoryMessage(
                role = message.role.name.lowercase(),
                content = message.text,
                reasoningContent = node?.content?.filterIsInstance<ContentBlock.Reasoning>()?.joinToString("") { it.text },
                toolCalls = node?.content?.filterIsInstance<ContentBlock.ProviderToolCall>()?.map {
                    ChatToolCall(it.callId, it.toolName, it.argumentsJson)
                }.orEmpty(),
            )
        }
    }

    private sealed interface OneResult {
        data class Reply(
            val text: String,
            val reasoning: String? = null,
            val toolCalls: List<ChatToolCall> = emptyList(),
            val completionNotice: CompletionNotice? = null,
            val attempt: NormalChatSendAttempt,
            val providerId: ProviderId,
            val receiverProviderId: ProviderId,
            val modelId: String,
            val modelDisplayName: String,
            val usage: ProviderUsage,
            val cost: ProviderCost,
            val costSource: ConversationCostSource?,
        ) : OneResult
        data class Blocked(val code: Code) : OneResult
        data class Failed(val code: Code) : OneResult
        data object Cancelled : OneResult
    }

    private fun requestOne(preset: ModelPresetId, conversationId: ConversationId, userMessageId: com.nanzhufeng.ai.domain.MessageNodeId, snapshot: com.nanzhufeng.ai.domain.ConversationSnapshot, userMessage: String, attachments: List<ChatAttachment>, choice: com.nanzhufeng.ai.domain.ComposerModelChoice, runtime: ActiveProviderRuntime?, onStreamProgress: () -> Unit, cancellation: ProviderChatCancellation? = null, existingAttempt: NormalChatSendAttempt? = null): OneResult {
        // Old persisted routes remain recognizable for attribution and a precise UI explanation,
        // but recovery must never turn an unavailable historical model into a new egress.
        if (NanfengModelServiceCatalog.preset(preset).usage != com.nanzhufeng.ai.domain.ModelPresetUsage.CHAT) {
            return OneResult.Blocked(Code.MODEL_NOT_FOUND)
        }
        val providerId = NanfengModelServiceCatalog.providerFor(preset)
        val resolved = modelResolver.resolve(preset)
        var resolvedModel = (resolved as? ResolvedModelResult.Resolved)?.model
            ?: return OneResult.Blocked(Code.MODEL_UNAVAILABLE)
        if (!resolvedModel.capabilities.supportsText) return OneResult.Blocked(Code.MODEL_UNAVAILABLE)
        val adapter = adapters.adapter(providerId) ?: return OneResult.Blocked(Code.MODEL_UNAVAILABLE)
        val experience = loadAssistantExperienceSettings()
        val webSearchEnabled = resolveConversationWebSearchEnabled(conversationId, experience.webSearchEnabled)
        val analysisMode = EvidenceFirstAnalysisPolicy.modeFor(userMessage, attachments)
        val requestedOptions = ChatRequestOptions.Standard
        // Historical DeepSeek-to-Qwen attempts used a model ID that Qwen Responses does not
        // accept. Do not replay the known-invalid egress merely because it was persisted.
        if (existingAttempt?.providerId == ProviderId.DEEPSEEK &&
            existingAttempt.egressProviderId == ProviderId.QWEN
        ) return OneResult.Blocked(Code.RECOVERY_MODEL_CHANGED)
        val automaticOptions = AutomaticWebSearchPolicy.requestOptions(
            providerId = providerId,
            current = requestedOptions,
            enabled = webSearchEnabled,
            userMessage = userMessage,
            attachments = attachments,
            modelId = resolvedModel.modelId,
        )
        val requestOptions = automaticOptions
        val fulfilledAnalysisMode = analysisMode.copy(liveEvidence = requestOptions.liveWebSearch)
        val executionProviderId = adapter.executionProviderId(requestOptions)
        val config = configuration.execute(executionProviderId) ?: return OneResult.Blocked(Code.SERVICE_DISABLED)
        if (!config.settings.enabled) return OneResult.Blocked(Code.SERVICE_DISABLED)
        if (!credentials.hasCredential(executionProviderId)) return OneResult.Blocked(Code.CREDENTIAL_MISSING)
        // Refresh only the directory whose model ID is being resolved. Hosted DeepSeek search
        // routes use a Qwen receiver but must not rewrite the DeepSeek model profile.
        if (executionProviderId == providerId) {
            modelProfileRefresher.refreshIfStale(providerId, preset, config.provider.fixedEndpoint)
            resolvedModel = (modelResolver.resolve(preset) as? ResolvedModelResult.Resolved)?.model
                ?: return OneResult.Blocked(Code.MODEL_UNAVAILABLE)
            if (!resolvedModel.capabilities.supportsText) return OneResult.Blocked(Code.MODEL_UNAVAILABLE)
        }
        val modelId = when (providerId) {
            ProviderId.OPENROUTER, ProviderId.QWEN, ProviderId.DEEPSEEK, ProviderId.ZHIPU -> resolvedModel.modelId
            ProviderId.MOCK -> return OneResult.Blocked(Code.MODEL_UNAVAILABLE)
        }
        // Streaming creates the current assistant placeholder before the request is assembled.
        // Only a completed earlier assistant message means this conversation has already had its
        // opening reply; failed/partial placeholders must not suppress the first real greeting.
        val isFirstAssistantReply = snapshot.nodes.none { node ->
            node.role == MessageRole.ASSISTANT && node.deliveryState == MessageDeliveryState.COMPLETE
        }
        val openingAddressPrefix = experience.firstReplyAddressPrefix(userMessage, isFirstAssistantReply)
        // A model that does not stream still keeps its ordinary-chat capability: use its
        // Adapter's non-SSE decoder instead of treating streaming as a product requirement.
        val explicitMemoryCommand = MemorySummaryPolicy.isExplicitSaveCommand(userMessage) && experience.memoryEnabled
        // The whole answer must be available before parsing a user-authorized summary; streaming
        // chunks are never treated as a durable memory candidate.
        val stream = !explicitMemoryCommand && adapter.supportsStreaming(resolvedModel, requestOptions)
        val bridged = when (val result = attachmentBridge.resolve(
            conversationId = conversationId,
            targetModel = resolvedModel,
            userRequest = userMessage,
            attachments = attachments,
            forceTextProjection = requestOptions.liveWebSearch,
        )) {
            is ChatAttachmentBridgeResult.Ready -> result
            is ChatAttachmentBridgeResult.Failed -> return OneResult.Blocked(Code.ATTACHMENT_BRIDGE_UNAVAILABLE)
        }
        val providerAttachments = bridged.providerAttachments
        val attachmentFact = attachmentReferenceInstruction(attachments) + if (bridged.receivers.isEmpty()) "" else
            "\n其中当前模型不能原生读取的内容已由${bridged.receivers.joinToString("、")}转为文本；请基于转换结果回答，不要声称当前模型直接看到了原始二进制文件。"
        val systemFact = listOfNotNull(
            systemFactForRequest(requestOptions, experience, fulfilledAnalysisMode, userMessage, isFirstAssistantReply),
            attachmentFact.takeIf { attachments.isNotEmpty() },
        ).joinToString("\n\n")
        val budget = ContextBudget.forModel(resolvedModel)
        val bridgeContextTokens = com.nanzhufeng.ai.domain.ModelTokenEstimators.estimate(
            resolvedModel.tokenizerId,
            bridged.contextText,
        )
        val attachmentInputTokens = providerAttachments.sumOf { attachment -> attachment.estimatedInputTokens(resolvedModel.attachmentInputTokenEstimate) } + bridgeContextTokens
        val context = contextBroker.assemble(
            snapshot, userMessage, budget, attachmentInputTokens,
            fixedInstructionTokens = com.nanzhufeng.ai.domain.ModelTokenEstimators.estimate(
                resolvedModel.tokenizerId,
                systemFact,
            ),
            policy = LocalContextBroker.RetrievalPolicy(
                includeRelevantMemory = experience.memoryEnabled,
                includeRelevantKnowledge = experience.historyLibraryEnabled,
            ),
        )
        if (context.status == AssemblyStatus.INPUT_TOO_LARGE) return OneResult.Blocked(Code.CONTEXT_LIMIT)
        val protocolMessages = if (modelId == KIMI_K3_MODEL_ID) {
            kimiK3ContinuationMessages(snapshot, context.messages, systemFact)
        } else {
            listOf(ChatHistoryMessage("system", systemFact)) + context.messages.map { ChatHistoryMessage(it.role.name.lowercase(), it.text) }
        } + bridged.contextText.takeIf(String::isNotBlank)?.let { listOf(ChatHistoryMessage("user", it)) }.orEmpty()
        val contextMessages = protocolMessages.map { it.role to it.content }
        val prepared = adapter.prepareContinuation(resolvedModel, protocolMessages, providerAttachments, stream, requestOptions)
        if (prepared !is ChatAdapterPrepareResult.Ready) return OneResult.Blocked(Code.ATTACHMENT_MODEL_UNSUPPORTED)
        val now = clock.instant()
        val attempt = existingAttempt ?: runCatching {
            val attemptId = NormalChatSendAttemptId.new()
            sendAttempts.create(
                NormalChatSendAttempt(
                    attemptId, userMessageId, conversationId,
                    providerId, modelId, "normal-chat-${attemptId.value}", NormalChatSendAttemptStatus.PENDING, now, now,
                    egressProviderId = executionProviderId,
                ),
            )
        }.getOrElse { return OneResult.Failed(Code.LOCAL_ATTEMPT_PERSISTENCE) }
        if (attempt.providerId != providerId || attempt.modelId != modelId ||
            (attempt.egressProviderId != null && attempt.egressProviderId != executionProviderId)
        ) return OneResult.Blocked(Code.RECOVERY_MODEL_CHANGED)
        val retryableStates = setOf(NormalChatSendAttemptStatus.UNKNOWN, NormalChatSendAttemptStatus.FAILED)
        val expectedStart = if (existingAttempt == null) setOf(NormalChatSendAttemptStatus.PENDING) else retryableStates
        if (sendAttempts.transition(attempt.attemptId, expectedStart, NormalChatSendAttemptStatus.SENDING, clock.instant()) == null) {
            return OneResult.Failed(Code.LOCAL_ATTEMPT_PERSISTENCE)
        }
        val credential = credentials.loadCredential(executionProviderId) ?: run {
            sendAttempts.transition(attempt.attemptId, setOf(NormalChatSendAttemptStatus.SENDING), NormalChatSendAttemptStatus.FAILED, clock.instant(), "CREDENTIAL_MISSING")
            return OneResult.Blocked(Code.CREDENTIAL_MISSING)
        }
        contextSelectionAudits.append(
            ContextSelectionAuditRecord(
                createdAt = clock.instant(), conversationId = conversationId.value, attemptId = attempt.attemptId, providerId = executionProviderId, modelId = modelId, tokenizerId = resolvedModel.tokenizerId,
                budget = context.effectiveBudget,
                selectedSources = contextAuditSources(context, experience) + bridged.receivers.map { receiver ->
                    ContextSelectionSource("附件解析", "attachment-bridge", receiver, 0)
                },
                indexStatus = context.status,
                participationAuditAvailable = true,
                retrievalAudit = context.investmentDecisionContext.takeIf { it }?.let {
                    ContextRetrievalAudit(
                        topic = "投资决策",
                        memorySearched = experience.memoryEnabled,
                        selectedMemoryCount = context.selectedMemoryCount,
                        knowledgeSearched = experience.historyLibraryEnabled,
                        selectedKnowledgeCount = context.selectedKnowledgeCount,
                    )
                },
            ),
        )
        val requestedAt = clock.instant()
        val endpoint = "${config.provider.fixedEndpoint}${adapter.endpointPath(requestOptions)}"
        val outcome = try {
            transport.execute(
                ProviderChatRequest(
                    endpoint, prepared.jsonBody,
                    expectsStream = stream,
                    body = prepared.body,
                    onAccepted = {
                        sendAttempts.transition(attempt.attemptId, setOf(NormalChatSendAttemptStatus.SENDING), NormalChatSendAttemptStatus.ACCEPTED, clock.instant())
                    },
                    onTextDelta = {
                        sendAttempts.transition(attempt.attemptId, setOf(NormalChatSendAttemptStatus.ACCEPTED, NormalChatSendAttemptStatus.SENDING), NormalChatSendAttemptStatus.STREAMING, clock.instant())
                        runtime?.append(it)
                        onStreamProgress()
                    },
                    // OpenRouter executes the only declared server tool itself. No arbitrary
                    // client tool becomes allowed: this request body contains only web_search.
                    streamEventDecoder = { event ->
                        adapter.decodeStreamingEvent(event, requestOptions)?.let { decoded ->
                            if (requestOptions.liveWebSearch && decoded.toolCallEncountered) decoded.copy(toolCallEncountered = false) else decoded
                        }
                    },
                    idempotencyKey = attempt.idempotencyKey,
                    readTimeoutMillis = adapter.readTimeoutMillis(resolvedModel, providerAttachments, stream, requestOptions),
                    streamTextMode = adapter.streamTextMode(requestOptions),
                    maxStreamDurationMillis = adapter.maxStreamDurationMillis(resolvedModel, providerAttachments, requestOptions),
                    maxResponseBytes = ProviderResponseByteBudget.forMaxOutputTokens(resolvedModel.maxOutputTokens),
                    cancellation = cancellation,
                ),
                credential,
            )
        } finally {
            credential.fill('\u0000')
        }
        return when (outcome) {
            is ProviderChatOutcome.HttpResponse -> if (outcome.statusCode !in 200..299) {
                sendAttempts.transition(attempt.attemptId, setOf(NormalChatSendAttemptStatus.SENDING), NormalChatSendAttemptStatus.FAILED, clock.instant(), "HTTP_${outcome.statusCode}")
                val redacted = ErrorBodyRedactor.redact(outcome.responseBody)
                val classification = adapter.classifyHttpFailure(outcome.statusCode, outcome.responseBody)
                modelHealthReporter.recordFailure(preset, classification)
                diagnostics.append(
                    ProviderDiagnosticRecord(
                        createdAt = clock.instant(), conversationId = conversationId.value, providerId = executionProviderId,
                        endpointHost = ProviderDiagnosticRecord.endpointHost(endpoint), apiModelId = modelId,
                        httpStatus = outcome.statusCode, errorClass = classification,
                        redactedBody = redacted.takeIf { it.isNotBlank() },
                        requestShape = requestShape(contextMessages, providerAttachments, requestOptions),
                        latencyMs = java.time.Duration.between(requestedAt, clock.instant()).toMillis().coerceAtLeast(0),
                    ),
                )
                audit.append(auditRecord(executionProviderId, endpoint, modelId, preset, choice, requestedAt, null, null, "HTTP_${outcome.statusCode}"))
                OneResult.Failed(httpCode(outcome.statusCode, classification))
            } else {
                val decoded = adapter.decodeNonStreaming(outcome.responseBody)
                val reply = (decoded as? ChatAdapterDecodedResult.Text)
                val toolOnly = decoded as? ChatAdapterDecodedResult.ToolCalls
                // A model may emit explanatory text alongside tool calls.  No ordinary-chat
                // tool is registered or approved here, so treating that mixed response as a
                // completed answer would falsely claim the required action has happened.
                val toolCallEncountered = !requestOptions.liveWebSearch && (toolOnly != null || (reply?.toolCallEncountered == true && modelId != KIMI_K3_MODEL_ID))
                audit.append(auditRecord(executionProviderId, endpoint, modelId, preset, choice, requestedAt, reply?.inputTokens ?: toolOnly?.inputTokens, reply?.outputTokens ?: toolOnly?.outputTokens, when { toolCallEncountered -> "TOOL_CALL_UNSUPPORTED"; reply != null && requestOptions.liveWebSearch -> "WEB_SEARCH_ENABLED_SUCCEEDED"; reply != null -> "SUCCEEDED"; else -> "RESPONSE_FORMAT" }))
                if (toolCallEncountered) {
                    sendAttempts.transition(attempt.attemptId, setOf(NormalChatSendAttemptStatus.ACCEPTED, NormalChatSendAttemptStatus.STREAMING), NormalChatSendAttemptStatus.FAILED, clock.instant(), "TOOL_CALL_UNSUPPORTED")
                    OneResult.Failed(Code.TOOL_CALL_UNSUPPORTED)
                } else if (reply == null) {
                    sendAttempts.transition(
                        attempt.attemptId,
                        setOf(NormalChatSendAttemptStatus.ACCEPTED, NormalChatSendAttemptStatus.STREAMING),
                        NormalChatSendAttemptStatus.FAILED,
                        clock.instant(),
                        "RESPONSE_FORMAT",
                    )
                    recordResponseFormatDiagnostic(conversationId, executionProviderId, endpoint, modelId, contextMessages, providerAttachments, requestOptions, requestedAt, outcome.statusCode)
                    OneResult.Failed(Code.RESPONSE_FORMAT)
                } else {
                    modelHealthReporter.recordSuccess(preset)
                    val responseText = if (explicitMemoryCommand) {
                        completedMemoryCommandReply(conversationId, reply.text)
                    } else appendProviderWebSources(reply.text, reply.webSources)
                    val visibleReply = responseText
                        .withoutLeakedReasoningTailBeforeOpeningAddress(reply.reasoning, openingAddressPrefix)
                        .withRequiredOpeningAddress(openingAddressPrefix)
                    runtime?.append(visibleReply)
                    if (runtime?.persistenceRejected == true) return OneResult.Failed(Code.LOCAL_RESPONSE_PERSISTENCE)
                    runtime?.complete(visibleReply)
                    if (runtime?.persistenceRejected == true) OneResult.Failed(Code.LOCAL_RESPONSE_PERSISTENCE) else {
                        val reasoningRetained = runtime?.retainProviderContinuation(reply.reasoning, if (modelId == KIMI_K3_MODEL_ID) reply.toolCalls else emptyList()) ?: true
                        sendAttempts.transition(attempt.attemptId, setOf(NormalChatSendAttemptStatus.ACCEPTED, NormalChatSendAttemptStatus.STREAMING), NormalChatSendAttemptStatus.COMPLETED, clock.instant())
                        val usage = ProviderUsage(
                            inputTokens = reply.inputTokens,
                            outputTokens = reply.outputTokens,
                            cachedInputTokens = reply.cachedInputTokens,
                            reasoningTokens = reply.reasoningTokens,
                        )
                        val (cost, source) = resolvedConversationCost(executionProviderId, modelId, usage, reply.reportedCostUsdMicros)
                        val k3Tools = if (modelId == KIMI_K3_MODEL_ID) reply.toolCalls else emptyList()
                        val notice = when {
                            !reasoningRetained -> CompletionNotice.REASONING_NOT_SAVED
                            k3Tools.isNotEmpty() -> CompletionNotice.TOOL_CALL_NOT_EXECUTED
                            else -> null
                        }
                        OneResult.Reply(visibleReply, reply.reasoning, k3Tools, notice, attempt, providerId, executionProviderId, modelId, resolvedModel.displayName, usage, cost, source)
                    }
                }
            }
            is ProviderChatOutcome.StreamedResponse -> {
                val reply = outcome.text.cleanReply()
                val incompleteResponsesStream = outcome.finishReason in setOf("INCOMPLETE", "FAILED", "MISSING_COMPLETION")
                audit.append(auditRecord(executionProviderId, endpoint, modelId, preset, choice, requestedAt, outcome.inputTokens, outcome.outputTokens, when { incompleteResponsesStream -> "RESPONSE_INCOMPLETE"; outcome.toolCallEncountered && modelId != KIMI_K3_MODEL_ID -> "TOOL_CALL_UNSUPPORTED"; reply == null -> "RESPONSE_FORMAT"; requestOptions.liveWebSearch -> "WEB_SEARCH_ENABLED_STREAM_SUCCEEDED"; else -> "STREAM_SUCCEEDED" }))
                if (runtime?.persistenceRejected == true) {
                    OneResult.Failed(Code.LOCAL_RESPONSE_PERSISTENCE)
                } else if (incompleteResponsesStream) {
                    sendAttempts.transition(
                        attempt.attemptId,
                        setOf(NormalChatSendAttemptStatus.ACCEPTED, NormalChatSendAttemptStatus.STREAMING),
                        NormalChatSendAttemptStatus.FAILED,
                        clock.instant(),
                        "RESPONSE_INCOMPLETE",
                    )
                    runtime?.fail(Code.RESPONSE_FORMAT.name)
                    recordResponseFormatDiagnostic(conversationId, executionProviderId, endpoint, modelId, contextMessages, providerAttachments, requestOptions, requestedAt, outcome.statusCode)
                    OneResult.Failed(Code.RESPONSE_FORMAT)
                } else if (outcome.toolCallEncountered && modelId != KIMI_K3_MODEL_ID) {
                    sendAttempts.transition(attempt.attemptId, setOf(NormalChatSendAttemptStatus.ACCEPTED, NormalChatSendAttemptStatus.STREAMING), NormalChatSendAttemptStatus.FAILED, clock.instant(), "TOOL_CALL_UNSUPPORTED")
                    OneResult.Failed(Code.TOOL_CALL_UNSUPPORTED)
                } else if (reply == null) {
                    sendAttempts.transition(
                        attempt.attemptId,
                        setOf(NormalChatSendAttemptStatus.ACCEPTED, NormalChatSendAttemptStatus.STREAMING),
                        NormalChatSendAttemptStatus.FAILED,
                        clock.instant(),
                        "RESPONSE_FORMAT",
                    )
                    runtime?.fail(Code.RESPONSE_FORMAT.name)
                    recordResponseFormatDiagnostic(conversationId, executionProviderId, endpoint, modelId, contextMessages, providerAttachments, requestOptions, requestedAt, outcome.statusCode)
                    OneResult.Failed(Code.RESPONSE_FORMAT)
                } else {
                    modelHealthReporter.recordSuccess(preset)
                    val visibleReply = appendProviderWebSources(reply, outcome.webSources)
                        .withoutLeakedReasoningTailBeforeOpeningAddress(outcome.reasoning, openingAddressPrefix)
                        .withRequiredOpeningAddress(openingAddressPrefix)
                    runtime?.complete(visibleReply)
                    if (runtime?.persistenceRejected == true) return OneResult.Failed(Code.LOCAL_RESPONSE_PERSISTENCE)
                    val reasoningRetained = runtime?.retainProviderContinuation(outcome.reasoning, if (modelId == KIMI_K3_MODEL_ID) outcome.toolCalls else emptyList()) ?: true
                    sendAttempts.transition(attempt.attemptId, setOf(NormalChatSendAttemptStatus.ACCEPTED, NormalChatSendAttemptStatus.STREAMING), NormalChatSendAttemptStatus.COMPLETED, clock.instant())
                    val usage = ProviderUsage(
                        inputTokens = outcome.inputTokens,
                        outputTokens = outcome.outputTokens,
                        cachedInputTokens = outcome.cachedInputTokens,
                        reasoningTokens = outcome.reasoningTokens,
                    )
                    val (cost, source) = resolvedConversationCost(executionProviderId, modelId, usage, outcome.reportedCostUsdMicros)
                    val k3Tools = if (modelId == KIMI_K3_MODEL_ID) outcome.toolCalls else emptyList()
                    val notice = when {
                        !reasoningRetained -> CompletionNotice.REASONING_NOT_SAVED
                        k3Tools.isNotEmpty() -> CompletionNotice.TOOL_CALL_NOT_EXECUTED
                        else -> null
                    }
                    OneResult.Reply(visibleReply, outcome.reasoning, k3Tools, notice, attempt, providerId, executionProviderId, modelId, resolvedModel.displayName, usage, cost, source)
                }
            }
            ProviderChatOutcome.TimedOut -> OneResult.Failed(Code.TIMEOUT).also {
                sendAttempts.transition(attempt.attemptId, setOf(NormalChatSendAttemptStatus.SENDING, NormalChatSendAttemptStatus.ACCEPTED, NormalChatSendAttemptStatus.STREAMING), NormalChatSendAttemptStatus.UNKNOWN, clock.instant(), "TIMEOUT")
                recordTransportDiagnostic(conversationId, executionProviderId, endpoint, modelId, contextMessages, providerAttachments, requestOptions, requestedAt, ProviderDiagnosticErrorClass.TIMEOUT)
                audit.append(auditRecord(executionProviderId, endpoint, modelId, preset, choice, requestedAt, null, null, "TIMEOUT"))
            }
            is ProviderChatOutcome.NetworkFailure -> OneResult.Failed(Code.NETWORK).also {
                sendAttempts.transition(attempt.attemptId, setOf(NormalChatSendAttemptStatus.SENDING, NormalChatSendAttemptStatus.ACCEPTED, NormalChatSendAttemptStatus.STREAMING), NormalChatSendAttemptStatus.UNKNOWN, clock.instant(), "NETWORK")
                recordTransportDiagnostic(
                    conversationId, executionProviderId, endpoint, modelId, contextMessages,
                    providerAttachments, requestOptions, requestedAt, ProviderDiagnosticErrorClass.NETWORK,
                    safeDetail = "TRANSPORT_${outcome.kind.name}",
                )
                audit.append(auditRecord(executionProviderId, endpoint, modelId, preset, choice, requestedAt, null, null, "NETWORK"))
            }
            ProviderChatOutcome.ResponseTooLarge -> OneResult.Failed(Code.RESPONSE_FORMAT).also {
                sendAttempts.transition(attempt.attemptId, setOf(NormalChatSendAttemptStatus.SENDING, NormalChatSendAttemptStatus.ACCEPTED, NormalChatSendAttemptStatus.STREAMING), NormalChatSendAttemptStatus.UNKNOWN, clock.instant(), "RESPONSE_TOO_LARGE")
                recordTransportDiagnostic(conversationId, executionProviderId, endpoint, modelId, contextMessages, providerAttachments, requestOptions, requestedAt, ProviderDiagnosticErrorClass.RESPONSE_TOO_LARGE)
                audit.append(auditRecord(executionProviderId, endpoint, modelId, preset, choice, requestedAt, null, null, "RESPONSE_TOO_LARGE"))
            }
            ProviderChatOutcome.Cancelled -> OneResult.Cancelled.also {
                sendAttempts.transition(attempt.attemptId, setOf(NormalChatSendAttemptStatus.SENDING, NormalChatSendAttemptStatus.ACCEPTED, NormalChatSendAttemptStatus.STREAMING), NormalChatSendAttemptStatus.CANCELLED, clock.instant(), "USER_CANCELLED")
                audit.append(auditRecord(executionProviderId, endpoint, modelId, preset, choice, requestedAt, null, null, "CANCELLED"))
            }
        }
    }

    private fun recordResponseAttribution(
        assistantMessageId: com.nanzhufeng.ai.domain.MessageNodeId,
        reply: OneResult.Reply,
    ): Boolean = recordResponseAttribution(
        assistantMessageId, reply.attempt, reply.providerId, reply.receiverProviderId, reply.modelId, reply.modelDisplayName,
        reply.usage, reply.cost, reply.costSource,
    )

    private fun recordResponseAttribution(
        assistantMessageId: com.nanzhufeng.ai.domain.MessageNodeId,
        attempt: NormalChatSendAttempt,
        providerId: ProviderId,
        receiverProviderId: ProviderId,
        modelId: String,
        modelDisplayName: String,
        usage: ProviderUsage = ProviderUsage(),
        cost: ProviderCost = ProviderCost(),
        costSource: ConversationCostSource? = null,
    ): Boolean = runCatching {
        responseModelAttributions.record(
            AssistantResponseModelAttribution(
                assistantMessageId = assistantMessageId,
                attemptId = attempt.attemptId,
                providerId = providerId,
                receiverProviderId = receiverProviderId,
                modelId = modelId,
                modelDisplayName = modelDisplayName,
                recordedAt = clock.instant(),
                usage = usage,
                cost = cost,
                costSource = costSource,
            ),
        )
    }.isSuccess

    /** OpenRouter's response amount settles the request; the calibrated table is fallback only. */
    private fun resolvedConversationCost(
        receiverProviderId: ProviderId,
        modelId: String,
        usage: ProviderUsage,
        reportedCostUsdMicros: Long?,
    ): Pair<ProviderCost, ConversationCostSource?> {
        if (receiverProviderId == ProviderId.OPENROUTER && reportedCostUsdMicros != null) {
            return ProviderCost("openrouter-provider-response", "USD", reportedCostUsdMicros) to ConversationCostSource.PROVIDER_RESPONSE
        }
        return ConversationCostEstimator.estimate(modelId, usage, clock.instant())?.let { it to ConversationCostSource.LOCAL_ESTIMATE }
            ?: (ProviderCost() to null)
    }

    private fun recordTransportDiagnostic(
        conversationId: ConversationId, providerId: ProviderId, endpoint: String, modelId: String, messages: List<Pair<String, String>>,
        attachments: List<ChatAttachment>, options: ChatRequestOptions, requestedAt: java.time.Instant,
        errorClass: ProviderDiagnosticErrorClass,
        safeDetail: String? = null,
    ) {
        diagnostics.append(
            ProviderDiagnosticRecord(
                createdAt = clock.instant(), conversationId = conversationId.value, providerId = providerId,
                endpointHost = ProviderDiagnosticRecord.endpointHost(endpoint), apiModelId = modelId,
                httpStatus = null, errorClass = errorClass, redactedBody = safeDetail,
                requestShape = requestShape(messages, attachments, options),
                latencyMs = java.time.Duration.between(requestedAt, clock.instant()).toMillis().coerceAtLeast(0),
            ),
        )
    }

    /**
     * Keep a safe fact when a successful HTTP exchange has no displayable final answer.  The
     * transport body is deliberately not retained: it can contain the user's material or an
     * upstream tool payload.  This distinguishes protocol drift from network/auth failures.
     */
    private fun recordResponseFormatDiagnostic(
        conversationId: ConversationId,
        providerId: ProviderId,
        endpoint: String,
        modelId: String,
        messages: List<Pair<String, String>>,
        attachments: List<ChatAttachment>,
        options: ChatRequestOptions,
        requestedAt: java.time.Instant,
        httpStatus: Int,
    ) {
        diagnostics.append(
            ProviderDiagnosticRecord(
                createdAt = clock.instant(), conversationId = conversationId.value, providerId = providerId,
                endpointHost = ProviderDiagnosticRecord.endpointHost(endpoint), apiModelId = modelId,
                httpStatus = httpStatus, errorClass = ProviderDiagnosticErrorClass.RESPONSE_FORMAT,
                redactedBody = null, requestShape = requestShape(messages, attachments, options),
                latencyMs = java.time.Duration.between(requestedAt, clock.instant()).toMillis().coerceAtLeast(0),
            ),
        )
    }

    private fun requestShape(messages: List<Pair<String, String>>, attachments: List<ChatAttachment>, options: ChatRequestOptions): String =
        "fields=model,messages,stream${if (options.liveWebSearch) ",webSearch" else ""};messageCount=${messages.size};attachmentCount=${attachments.size};attachmentBytes=${attachments.sumOf { it.byteCount }};webSearchRoute=${options.webSearchRoute.name}"

    /** The provider receives an explicit date fact; a model's training cut-off is never the current date. */
    private fun systemFactForRequest(
        options: ChatRequestOptions,
        experience: AssistantExperienceSettings,
        analysisMode: EvidenceFirstAnalysisPolicy.Mode,
        userMessage: String,
        isFirstAssistantReply: Boolean,
    ): String {
        val date = clock.instant().atZone(clock.zone).toLocalDate()
        val runtimeFact = if (options.liveWebSearch) {
            "系统事实：当前本机日期为 $date。本轮请求已由服务端启用${options.webSearchRoute.userLabel()}。请直接基于服务端提供的检索结果形成最终答复；不要输出、模拟或重复任何工具调用、工具参数、工具结果或 XML 工具标签。能取得公开来源时以 Markdown 链接标注；不得声称没有联网搜索能力。"
        } else {
            "系统事实：当前本机日期为 $date。本轮已连接模型服务，但没有附带可验证的实时网页检索结果。不得把训练数据截止时间说成当前日期，也不得编造已联网检索；若问题依赖实时资料，应明确说明本轮未取得实时来源。"
        }
        return listOfNotNull(
            runtimeFact,
            EvidenceFirstAnalysisPolicy.instruction(analysisMode),
            MemorySummaryPolicy.commandInstruction().takeIf { MemorySummaryPolicy.isExplicitSaveCommand(userMessage) },
            experience.modelInstruction(isFirstAssistantReply),
        ).joinToString("\n\n")
    }

    /** Metadata-only projection of each user-owned input sent with this request. */
    private fun contextAuditSources(
        context: LocalContextBroker.Package,
        experience: AssistantExperienceSettings,
    ): List<ContextSelectionSource> = buildList {
        addAll(context.selectedSources)
        if (context.includedCurrentPathMessageCount > 0) {
            add(ContextSelectionSource("当前对话路径", "current-conversation-path", "此前 ${context.includedCurrentPathMessageCount} 条消息", 0))
        }
        if (experience.personalizationEnabled) {
            val fields = buildList {
                if (experience.displayName.isNotBlank()) add("昵称")
                if (experience.occupation.isNotBlank()) add("职业／角色")
                if (experience.interests.isNotBlank()) add("关注方向")
            }
            if (fields.isNotEmpty()) add(ContextSelectionSource("个性化资料", "assistant-profile", fields.joinToString("、"), 0))
        }
        if (experience.customInstructions.isNotBlank()) {
            add(ContextSelectionSource("自定义指令", "assistant-custom-instructions", "已保存的自定义指令", 0))
        }
        if (experience.conversationStyle != com.nanzhufeng.ai.domain.ConversationStyle.DEFAULT) {
            add(ContextSelectionSource("对话风格", "assistant-conversation-style", experience.conversationStyle.name, 0))
        }
    }

    private fun completedMemoryCommandReply(conversationId: ConversationId, providerText: String): String {
        val draft = MemorySummaryPolicy.parseModelSummary(providerText)
            ?: return "这次没有生成可安全保存的记忆摘要，因此未写入。你可以更明确地说出需要长期记住的内容后再试一次。"
        return when (saveMemorySummary(conversationId, draft)) {
            is MemoryMutationResult.Applied, is MemoryMutationResult.Replayed -> "已更新记忆摘要：\n${draft.body}"
            is MemoryMutationResult.Duplicate -> "这条内容已在记忆摘要中，无需重复保存。"
            is MemoryMutationResult.Conflict -> "检测到相近但不同的已有记忆，为避免覆盖，尚未自动写入。"
            is MemoryMutationResult.Rejected -> "这条内容未通过本机记忆安全校验，因此没有保存。"
        }
    }

    private fun OfficialWebSearchRoute.userLabel(): String = when (this) {
        OfficialWebSearchRoute.OPENROUTER_SERVER_TOOL -> " OpenRouter 官方实时网页检索"
        OfficialWebSearchRoute.QWEN_CHAT_COMPLETIONS -> " 千问官方实时网页检索"
        OfficialWebSearchRoute.QWEN_RESPONSES -> " 千问官方 Responses 实时网页检索"
        OfficialWebSearchRoute.DEEPSEEK_RESPONSES -> " DeepSeek 官方 Responses 实时网页检索"
        OfficialWebSearchRoute.ZHIPU_CHAT_COMPLETIONS -> " 智谱官方实时网页检索"
        OfficialWebSearchRoute.NONE -> ""
    }

    private fun recoveryFailureReason(status: NormalChatSendAttemptStatus, safeErrorCode: String?): String = when (safeErrorCode) {
        "NETWORK" -> "网络连接已中断，未收到服务返回结果。"
        "TIMEOUT" -> "等待服务响应超时，本次未收到完整结果。"
        "PROCESS_INTERRUPTED" -> "应用或系统在生成期间中断，未能确认本次结果。"
        "RESPONSE_TOO_LARGE" -> "服务返回内容过大，未能完整接收。"
        "USER_CANCELLED" -> "你已停止本次生成。"
        else -> if (status == NormalChatSendAttemptStatus.UNKNOWN) "生成连接已中断，未能确认本次结果。" else "服务未完成本次生成。"
    }

    private inner class ActiveProviderRuntime(var state: ConversationRuntimeState) {
        private val security = AiRuntimeSecurityMetadata(source = "DIRECT_PROVIDER_SSE")
        var persistenceRejected: Boolean = false
            private set

        fun append(delta: String) {
            if (delta.isBlank() || state.isTerminal) return
            apply(RuntimeContentDelta(AiRuntimeEventId.new(), state.invocationId, state.conversationId, state.messageId, state.nextExpectedSequence, clock.instant(), delta, security))
        }

        fun complete(finalVisibleText: String) {
            if (state.isTerminal) return
            apply(
                RuntimeCompleted(
                    eventId = AiRuntimeEventId.new(),
                    invocationId = state.invocationId,
                    conversationId = state.conversationId,
                    messageId = state.messageId,
                    sequence = state.nextExpectedSequence,
                    emittedAt = clock.instant(),
                    security = security,
                    finalVisibleText = finalVisibleText,
                ),
            )
        }

        /**
         * Retain provider reasoning only after the visible reply has completed successfully.
         * Reasoning is an optional disclosure: a failure here must never roll back the reply,
         * automatic title, accounting, or the user's ability to submit a new question.
         */
        fun retainProviderContinuation(reasoning: String?, toolCalls: List<ChatToolCall>): Boolean {
            val clean = reasoning?.cleanReply()
            if (clean == null && toolCalls.isEmpty()) return true
            val snapshot = conversations.findById(state.conversationId) ?: run {
                return false
            }
            val target = snapshot.nodes.firstOrNull { it.id == state.messageId } ?: run {
                return false
            }
            val continuation = buildList {
                if (clean != null && target.content.none { it is ContentBlock.Reasoning }) add(ContentBlock.Reasoning(clean))
                if (target.content.none { it is ContentBlock.ProviderToolCall }) toolCalls.forEach {
                    add(ContentBlock.ProviderToolCall(it.id, it.name, it.argumentsJson))
                }
            }
            val revisedContent = continuation + target.content
            if (target.content == revisedContent) return true
            return runCatching {
                conversations.save(
                    snapshot.copy(
                        nodes = snapshot.nodes.map { node ->
                            if (node.id == state.messageId) node.copy(content = revisedContent)
                            else node
                        },
                    ),
                )
            }.isSuccess
        }

        fun fail(code: String) {
            if (state.isTerminal) return
            apply(RuntimeFailed(AiRuntimeEventId.new(), state.invocationId, state.conversationId, state.messageId, state.nextExpectedSequence, clock.instant(), code, security))
        }

        fun cancel() {
            if (state.isTerminal) return
            apply(com.nanzhufeng.ai.domain.RuntimeCancelled(AiRuntimeEventId.new(), state.invocationId, state.conversationId, state.messageId, state.nextExpectedSequence, clock.instant(), security))
        }

        private fun apply(event: com.nanzhufeng.ai.domain.AiRuntimeEvent) {
            when (val result = applyRuntimeEvent.execute(event)) {
                is ConversationRuntimePersistenceResult.Applied -> state = result.projection.state
                is ConversationRuntimePersistenceResult.Replayed -> state = result.projection.state
                is ConversationRuntimePersistenceResult.Rejected -> persistenceRejected = true
            }
        }
    }

    /**
     * The regular reply has already been stored before this secondary request begins.  Re-read
     * immediately before committing so a manual rename wins even if it happened during title work.
     */
    private fun maybeRefineOpeningTitle(conversationId: ConversationId) {
        val snapshot = conversations.findById(conversationId) ?: return
        if (!snapshot.conversation.autoTitlePending) return
        val source = snapshot.openingTitleSource() ?: return
        when (val result = conversationTitleRefiner.refine(conversationId, source)) {
            is ConversationTitleRefinementResult.Title -> completeAutomaticTitle(conversationId, result.value)
            is ConversationTitleRefinementResult.Failed -> Unit // Keep pending: later completed replies retry a transient service failure.
        }
    }

    private fun completeAutomaticTitle(conversationId: ConversationId, generated: String?) {
        val latest = conversations.findById(conversationId) ?: return
        if (!latest.conversation.autoTitlePending) return
        val conversation = latest.conversation.copy(
            title = generated ?: ConversationAutoTitle.NEW_CONVERSATION_TITLE,
            autoTitlePending = false,
            updatedAt = clock.instant(),
            revision = latest.conversation.revision + 1,
        )
        conversations.save(latest.copy(conversation = conversation))
    }

    private fun auditRecord(provider: ProviderId, endpoint: String, modelId: String, preset: ModelPresetId, choice: com.nanzhufeng.ai.domain.ComposerModelChoice, requestedAt: java.time.Instant, inputTokens: Long?, outputTokens: Long?, status: String) =
        DirectChatCallAuditRecord(
            provider, endpoint, modelId, NanfengModelServiceCatalog.preset(preset).displayName,
            when {
                modelId == "qwen3.8-max" -> "low"
                modelId == "glm-5.3-flash" -> "max"
                choice.slot == com.nanzhufeng.ai.domain.ComposerModelSlot.DEEP -> "high"
                choice.slot == com.nanzhufeng.ai.domain.ComposerModelSlot.COMPARE -> "review"
                choice.slot == com.nanzhufeng.ai.domain.ComposerModelSlot.MULTIMODAL -> "multimodal"
                choice.slot == com.nanzhufeng.ai.domain.ComposerModelSlot.AUTO -> "auto"
                else -> "standard"
            },
            requestedAt, inputTokens, outputTokens, status,
        )

    private fun prepareAttachments(blocks: List<ContentBlock.Attachment>): List<ChatAttachment>? {
        if (blocks.isEmpty() || blocks.size > 4) return null
        // Only attachments still referenced by the exact submitted draft can reach this point.
        // Provider adapters serialize complete PDF `file_data` and video `video_url` parts; this
        // executor never substitutes a rendered first page or a preview cover.
        return blocks.mapNotNull { block ->
            val asset = attachmentRepository.findById(block.attachment.id) ?: return null
            val kind = when {
                asset.mimeType.startsWith("image/") -> ChatAttachmentKind.IMAGE
                asset.mimeType == "application/pdf" -> ChatAttachmentKind.PDF
                asset.mimeType.startsWith("video/") -> ChatAttachmentKind.VIDEO
                asset.mimeType.startsWith("audio/") -> ChatAttachmentKind.AUDIO
                else -> ChatAttachmentKind.FILE
            }
            (attachmentStore.openVerified(asset) as? com.nanzhufeng.ai.domain.AttachmentOpenResult.Opened)
                ?.takeIf { it.byteCount <= CONVERSATION_ATTACHMENT_MAX_BYTES }
                ?.let { ChatAttachment(kind, asset.mimeType, asset.displayName ?: defaultFileName(kind), it.byteCount, it.open) }
        }.takeIf { it.size == blocks.size }
    }

    private fun defaultFileName(kind: ChatAttachmentKind) = when (kind) {
        ChatAttachmentKind.IMAGE -> "image"
        ChatAttachmentKind.PDF -> "document.pdf"
        ChatAttachmentKind.VIDEO -> "video.mp4"
        ChatAttachmentKind.AUDIO -> "audio"
        ChatAttachmentKind.FILE -> "document"
    }


    private fun httpCode(status: Int, diagnostic: ProviderDiagnosticErrorClass): Code = when (diagnostic) {
        ProviderDiagnosticErrorClass.MODEL_NOT_FOUND -> Code.MODEL_NOT_FOUND
        ProviderDiagnosticErrorClass.STREAM_REQUIRED -> Code.STREAM_REQUIRED
        ProviderDiagnosticErrorClass.INVALID_REQUEST -> Code.INVALID_REQUEST
        else -> when (OpenRouterErrorMapper.fromHttpStatus(status)) {
        AiTaskError.ProviderAuthenticationFailed -> Code.AUTHENTICATION
        AiTaskError.ProviderBalanceInsufficient -> Code.BALANCE
        AiTaskError.ProviderRateLimited -> Code.RATE_LIMIT
        AiTaskError.ProviderTimedOut -> Code.TIMEOUT
        else -> Code.SERVICE
        }
    }

}

/** Stream completion must preserve the same full reply contract as non-SSE decoding. */
private fun String.cleanReply(): String? = replace(Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]"), "").trim().takeIf { it.isNotBlank() }

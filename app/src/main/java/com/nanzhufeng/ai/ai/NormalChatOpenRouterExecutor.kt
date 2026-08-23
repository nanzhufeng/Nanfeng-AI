package com.nanzhufeng.ai.ai

import com.nanzhufeng.ai.domain.AppendConversationMessageUseCase
import com.nanzhufeng.ai.domain.AppendMessageRequest
import com.nanzhufeng.ai.domain.ContentBlock
import com.nanzhufeng.ai.domain.ConversationDraftSubmissionResult
import com.nanzhufeng.ai.domain.ConversationId
import com.nanzhufeng.ai.domain.LoadModelServiceConfigurationUseCase
import com.nanzhufeng.ai.domain.MessageRole
import com.nanzhufeng.ai.domain.ModelRegistryResolution
import com.nanzhufeng.ai.domain.AiTaskError
import com.nanzhufeng.ai.domain.ProviderCredentialStore
import com.nanzhufeng.ai.domain.ProviderId
import com.nanzhufeng.ai.domain.SubmitConversationDraftUseCase
import com.nanzhufeng.ai.domain.VersionedModelRegistry
import java.time.Clock
import java.util.UUID

/**
 * Production owner for one explicitly-confirmed normal-chat text request.
 *
 * It sends only the current draft text, never attachments or history.  Credential bytes and the
 * provider response are process-memory-only; neither is logged nor stored in the invocation
 * ledger.  The user message is committed locally before the network call so an interrupted
 * request is visible and can be retried deliberately.
 */
class NormalChatOpenRouterExecutor(
    private val configuration: LoadModelServiceConfigurationUseCase,
    private val registry: VersionedModelRegistry,
    private val credentials: ProviderCredentialStore,
    private val submitDraft: SubmitConversationDraftUseCase,
    private val appendMessage: AppendConversationMessageUseCase,
    private val transport: OpenRouterInferenceTransport,
    private val clock: Clock,
) {
    sealed interface Result {
        data object Sent : Result
        data class Blocked(val code: Code) : Result
        data class Failed(val code: Code) : Result
    }

    enum class Code {
        SERVICE_DISABLED, CREDENTIAL_MISSING, REGISTRY_UNVERIFIED, MODEL_UNAVAILABLE,
        ATTACHMENTS_UNSUPPORTED, DRAFT_UNAVAILABLE, AUTHENTICATION, BALANCE, RATE_LIMIT,
        TIMEOUT, NETWORK, SERVICE, RESPONSE_FORMAT, LOCAL_SAVE,
    }

    fun execute(conversationId: ConversationId): Result {
        val config = configuration.execute() ?: return Result.Blocked(Code.SERVICE_DISABLED)
        if (!config.settings.enabled) return Result.Blocked(Code.SERVICE_DISABLED)
        if (!credentials.hasCredential(ProviderId.OPENROUTER)) return Result.Blocked(Code.CREDENTIAL_MISSING)
        val model = when (val resolved = registry.resolve(ProviderId.OPENROUTER, config.settings.presetId)) {
            is ModelRegistryResolution.Resolved -> resolved.model
            is ModelRegistryResolution.Rejected -> return Result.Blocked(
                if (resolved.error == AiTaskError.ModelRegistrySnapshotUnverified) Code.REGISTRY_UNVERIFIED else Code.MODEL_UNAVAILABLE,
            )
        }
        if (!model.capabilities.supportsText) return Result.Blocked(Code.MODEL_UNAVAILABLE)

        val submitted = submitDraft.execute(conversationId)
        if (submitted !is ConversationDraftSubmissionResult.Submitted) return Result.Blocked(Code.DRAFT_UNAVAILABLE)
        val userMessage = submitted.snapshot.nodes.lastOrNull()?.content
            ?.filterIsInstance<ContentBlock.Text>()?.joinToString("") { it.text }.orEmpty()
        if (userMessage.isBlank()) return Result.Blocked(Code.DRAFT_UNAVAILABLE)
        if (submitted.snapshot.nodes.lastOrNull()?.content?.any { it is ContentBlock.Attachment } == true) {
            return Result.Blocked(Code.ATTACHMENTS_UNSUPPORTED)
        }

        val credential = credentials.loadCredential(ProviderId.OPENROUTER) ?: return Result.Blocked(Code.CREDENTIAL_MISSING)
        val request = OpenRouterTransportRequest(
            jsonBody = normalChatRequest(model.id, userMessage),
            idempotencyKey = UUID.randomUUID().toString(),
        )
        val outcome = try {
            transport.execute(request, credential, OpenRouterCancellationSignal { false })
        } finally {
            credential.fill('\u0000')
        }
        val text = when (outcome) {
            is OpenRouterTransportOutcome.HttpResponse -> {
                if (outcome.statusCode !in 200..299) return Result.Failed(httpCode(outcome.statusCode))
                val decoded = OpenRouterJsonCodec().decodeResponse(outcome.responseBody) as? OpenRouterAdapterDecodeResult.Decoded
                    ?: return Result.Failed(Code.RESPONSE_FORMAT)
                decoded.response.structuredContent.cleanReply() ?: return Result.Failed(Code.RESPONSE_FORMAT)
            }
            OpenRouterTransportOutcome.Cancelled -> return Result.Failed(Code.SERVICE)
            OpenRouterTransportOutcome.TimedOut -> return Result.Failed(Code.TIMEOUT)
            OpenRouterTransportOutcome.NetworkFailure -> return Result.Failed(Code.NETWORK)
            OpenRouterTransportOutcome.ResponseTooLarge -> return Result.Failed(Code.RESPONSE_FORMAT)
        }
        return when (appendMessage.execute(submitted.snapshot, AppendMessageRequest(MessageRole.ASSISTANT, listOf(ContentBlock.Text(text))))) {
            is com.nanzhufeng.ai.domain.ConversationMutationResult.Saved -> Result.Sent
            is com.nanzhufeng.ai.domain.ConversationMutationResult.Rejected -> Result.Failed(Code.LOCAL_SAVE)
        }
    }

    private fun normalChatRequest(modelId: String, text: String): String = buildString {
        append("{\"model\":\"")
        append(modelId.jsonEscaped())
        append("\",\"messages\":[{\"role\":\"user\",\"content\":\"")
        append(text.jsonEscaped())
        append("\"}],\"stream\":false}")
    }

    private fun httpCode(status: Int): Code = when (OpenRouterErrorMapper.fromHttpStatus(status)) {
        AiTaskError.ProviderAuthenticationFailed -> Code.AUTHENTICATION
        AiTaskError.ProviderBalanceInsufficient -> Code.BALANCE
        AiTaskError.ProviderRateLimited -> Code.RATE_LIMIT
        AiTaskError.ProviderTimedOut -> Code.TIMEOUT
        else -> Code.SERVICE
    }
}

private fun String.cleanReply(): String? = replace(Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]"), "")
    .trim().take(12_000).takeIf { it.isNotBlank() }

private fun String.jsonEscaped(): String = buildString {
    for (char in this@jsonEscaped) when (char) {
        '"' -> append("\\\"")
        '\\' -> append("\\\\")
        '\n' -> append("\\n")
        '\r' -> append("\\r")
        '\t' -> append("\\t")
        else -> if (char.code < 0x20) append("\\u%04x".format(char.code)) else append(char)
    }
}

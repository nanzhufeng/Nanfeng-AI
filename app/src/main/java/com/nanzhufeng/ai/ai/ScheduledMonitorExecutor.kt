package com.nanzhufeng.ai.ai

import com.nanzhufeng.ai.domain.LoadModelServiceConfigurationUseCase
import com.nanzhufeng.ai.domain.ModelPresetId
import com.nanzhufeng.ai.domain.ModelResolver
import com.nanzhufeng.ai.domain.NanfengModelServiceCatalog
import com.nanzhufeng.ai.domain.ProviderCredentialStore
import com.nanzhufeng.ai.domain.ProviderId
import com.nanzhufeng.ai.domain.ResolvedModelResult
import com.nanzhufeng.ai.domain.ScheduledMonitorRepository
import com.nanzhufeng.ai.domain.ScheduledMonitorRun
import com.nanzhufeng.ai.domain.ScheduledMonitorRunId
import com.nanzhufeng.ai.domain.ScheduledMonitorRunStatus
import com.nanzhufeng.ai.domain.ScheduledMonitorStatus
import com.nanzhufeng.ai.domain.ScheduledMonitorTask
import com.nanzhufeng.ai.domain.ScheduledMonitorTaskId
import java.time.Clock

/** The only background egress path for a user-created monitoring task. */
class ScheduledMonitorExecutor(
    private val tasks: ScheduledMonitorRepository,
    private val configuration: LoadModelServiceConfigurationUseCase,
    private val credentials: ProviderCredentialStore,
    private val modelResolver: ModelResolver,
    private val transport: ProviderChatTransport,
    private val clock: Clock,
) {
    sealed interface Result {
        data class Completed(val task: ScheduledMonitorTask) : Result
        data class Failed(val task: ScheduledMonitorTask?, val safeCode: String) : Result
        data object Cancelled : Result
    }

    fun execute(taskId: ScheduledMonitorTaskId): Result {
        val task = tasks.find(taskId) ?: return Result.Cancelled
        if (task.status != ScheduledMonitorStatus.ACTIVE) return Result.Cancelled
        val now = clock.instant()
        val running = ScheduledMonitorRun(ScheduledMonitorRunId.new(), task.id, task.nextRunAt, now, status = ScheduledMonitorRunStatus.RUNNING)
        if (!tasks.startRun(running)) return Result.Failed(task, "LOCAL_SAVE")

        fun fail(code: String): Result {
            val completedAt = clock.instant()
            val updated = task.copy(
                nextRunAt = completedAt.plusMillis(task.cadence.intervalMillis),
                lastRunAt = completedAt,
                lastSafeErrorCode = code,
                updatedAt = completedAt,
            )
            tasks.finishRun(updated, running.copy(completedAt = completedAt, status = ScheduledMonitorRunStatus.FAILED, safeErrorCode = code))
            return Result.Failed(updated, code)
        }

        val resolved = modelResolver.resolve(task.modelPresetId) as? ResolvedModelResult.Resolved ?: return fail("MODEL_UNAVAILABLE")
        val model = resolved.model
        if (!model.capabilities.supportsText) return fail("MODEL_UNAVAILABLE")
        val providerId = NanfengModelServiceCatalog.providerFor(task.modelPresetId)
        val adapter = ChatProviderAdapters().adapter(providerId) ?: return fail("MODEL_UNAVAILABLE")
        val options = monitoringWebSearchOptions(providerId)
        // A recurring news monitor must never silently run as an offline model call. Every deep
        // provider here owns its verified native route; DeepSeek stays on its own /responses.
        if (!options.liveWebSearch) return fail("WEB_SEARCH_UNAVAILABLE")
        val executionProvider = adapter.executionProviderId(options)
        val config = configuration.execute(executionProvider) ?: return fail("SERVICE_DISABLED")
        if (!config.settings.enabled) return fail("SERVICE_DISABLED")
        if (!credentials.hasCredential(executionProvider)) return fail("CREDENTIAL_MISSING")
        val prepared = adapter.prepare(
            model = model,
            messages = listOf(
                "system" to monitoringSystemInstruction(now.toString()),
                "user" to "计划名称：${task.title}\n\n监控要求：${task.instruction}",
            ),
            attachments = emptyList(),
            stream = false,
            options = options,
        ) as? ChatAdapterPrepareResult.Ready ?: return fail("REQUEST_UNSUPPORTED")
        val credential = credentials.loadCredential(executionProvider) ?: return fail("CREDENTIAL_MISSING")
        val endpoint = "${config.provider.fixedEndpoint}${adapter.endpointPath(options)}"
        val outcome = try {
            transport.execute(
                ProviderChatRequest(
                    endpoint = endpoint,
                    jsonBody = prepared.jsonBody,
                    body = prepared.body,
                    expectsStream = false,
                    idempotencyKey = "scheduled-monitor-${running.id.value}",
                    readTimeoutMillis = adapter.readTimeoutMillis(model, emptyList(), false),
                    maxResponseBytes = ProviderResponseByteBudget.forMaxOutputTokens(model.maxOutputTokens),
                ),
                credential,
            )
        } finally {
            credential.fill('\u0000')
        }
        val reply = when (outcome) {
            is ProviderChatOutcome.HttpResponse -> {
                if (outcome.statusCode !in 200..299) return fail("HTTP_${outcome.statusCode}")
                adapter.decodeNonStreaming(outcome.responseBody) as? ChatAdapterDecodedResult.Text
                    ?: return fail("RESPONSE_FORMAT")
            }
            is ProviderChatOutcome.StreamedResponse -> return fail("UNEXPECTED_STREAM")
            ProviderChatOutcome.TimedOut -> return fail("TIMEOUT")
            ProviderChatOutcome.NetworkFailure -> return fail("NETWORK")
            ProviderChatOutcome.ResponseTooLarge -> return fail("RESPONSE_TOO_LARGE")
            ProviderChatOutcome.Cancelled -> return fail("CANCELLED")
        }
        val visibleReply = appendProviderWebSources(reply.text, reply.webSources)
        val completedAt = clock.instant()
        val updated = task.copy(
            nextRunAt = completedAt.plusMillis(task.cadence.intervalMillis),
            lastRunAt = completedAt,
            latestResult = visibleReply,
            lastProviderId = executionProvider,
            lastModelId = model.modelId,
            lastInputTokens = reply.inputTokens,
            lastOutputTokens = reply.outputTokens,
            lastSafeErrorCode = null,
            updatedAt = completedAt,
        )
        val saved = tasks.finishRun(
            updated,
            running.copy(
                completedAt = completedAt,
                status = ScheduledMonitorRunStatus.SUCCEEDED,
                result = visibleReply,
                providerId = executionProvider,
                modelId = model.modelId,
                inputTokens = reply.inputTokens,
                outputTokens = reply.outputTokens,
            ),
        )
        return if (saved) Result.Completed(updated) else Result.Failed(updated, "LOCAL_SAVE")
    }

    private fun monitoringWebSearchOptions(providerId: ProviderId) = when (providerId) {
        ProviderId.OPENROUTER -> ChatRequestOptions(OfficialWebSearchRoute.OPENROUTER_SERVER_TOOL)
        ProviderId.QWEN -> ChatRequestOptions(OfficialWebSearchRoute.QWEN_RESPONSES)
        ProviderId.DEEPSEEK -> ChatRequestOptions(OfficialWebSearchRoute.DEEPSEEK_RESPONSES)
        ProviderId.MOCK -> ChatRequestOptions.Standard
    }

    private fun monitoringSystemInstruction(now: String) = """
        系统事实：当前本机时间为 $now。本次是用户明确创建的周期新闻监控，已启用服务商官方网页检索。
        只检索与任务要求直接相关的公开最新资料；优先一手来源。输出中文简报，先写本次有无重要变化，随后列出要点和可核验 Markdown 来源链接。无法核验时必须说明，不得编造新闻、价格、公告或来源。
    """.trimIndent()
}

package com.nanzhufeng.ai.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nanzhufeng.ai.domain.AiTaskError
import com.nanzhufeng.ai.domain.AiTaskRunResult
import com.nanzhufeng.ai.domain.LoadModelServiceConfigurationUseCase
import com.nanzhufeng.ai.domain.ModelPresetId
import com.nanzhufeng.ai.domain.ModelServiceConfiguration
import com.nanzhufeng.ai.domain.ProviderId
import com.nanzhufeng.ai.domain.SaveModelServiceConfigurationResult
import com.nanzhufeng.ai.domain.SaveModelServiceConfigurationUseCase
import com.nanzhufeng.ai.domain.LoadRegistryVerificationStatusUseCase
import com.nanzhufeng.ai.domain.RegistryVerificationDisplayStatus
import com.nanzhufeng.ai.domain.RegistryVerificationStatusView
import com.nanzhufeng.ai.domain.VerifyOpenRouterRegistryResult
import com.nanzhufeng.ai.domain.VerifyOpenRouterRegistryUseCase
import com.nanzhufeng.ai.domain.DirectChatCallAuditStore
import com.nanzhufeng.ai.domain.DirectChatCallAuditSummary
import com.nanzhufeng.ai.domain.ProviderDiagnosticRecord
import com.nanzhufeng.ai.domain.ProviderDiagnosticStore
import com.nanzhufeng.ai.domain.ContextSelectionAuditRecord
import com.nanzhufeng.ai.domain.ContextSelectionAuditStore
import com.nanzhufeng.ai.domain.ChatRoutingPolicy
import com.nanzhufeng.ai.domain.LoadChatRoutingPolicyUseCase
import com.nanzhufeng.ai.domain.SaveChatRoutingPolicyUseCase
import com.nanzhufeng.ai.ai.LoadRealServiceAcceptanceUiStatusUseCase
import com.nanzhufeng.ai.ai.RealServiceAcceptanceUiStatus
import com.nanzhufeng.ai.ai.P2MRealServiceExecutor
import com.nanzhufeng.ai.ai.P2MRealServiceExecutionResult
import com.nanzhufeng.ai.ai.P2MRealServiceReadiness
import com.nanzhufeng.ai.ai.P2MRealServiceReadinessUseCase
import com.nanzhufeng.ai.ai.P2MRealServiceRunSummary
import com.nanzhufeng.ai.ai.ProviderConnectionProbe
import com.nanzhufeng.ai.ai.ProviderConnectionProbeResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ModelSettingsUiState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val verifyingRegistry: Boolean = false,
    val probingConnection: Boolean = false,
    val dialogVisible: Boolean = false,
    val realServiceConfirmationVisible: Boolean = false,
    val realServiceConfirmationChecked: Boolean = false,
    val realServiceExecuting: Boolean = false,
    val realServiceReadiness: P2MRealServiceReadiness = P2MRealServiceReadiness.Blocked("正在检查本机配置。"),
    val realServiceSummary: P2MRealServiceRunSummary? = null,
    val configuration: ModelServiceConfiguration? = null,
    /** One local configuration per transport boundary; OpenRouter backs OpenAI/Claude/Gemini. */
    val providerConfigurations: Map<ProviderId, ModelServiceConfiguration> = emptyMap(),
    val callAuditSummary: DirectChatCallAuditSummary = DirectChatCallAuditSummary(),
    /** Redacted failures only; this is intentionally separate from the normal audit ledger. */
    val recentDiagnostics: List<ProviderDiagnosticRecord> = emptyList(),
    val recentContextSelections: List<ContextSelectionAuditRecord> = emptyList(),
    val routingPolicy: ChatRoutingPolicy = ChatRoutingPolicy(),
    /** Ephemeral screen-only value, cleared when this dialog closes or saves. */
    val revealedCredential: String? = null,
    val notice: String? = null,
    val error: CaptureUiError? = null,
    val registryStatus: RegistryVerificationStatusView = RegistryVerificationStatusView(RegistryVerificationDisplayStatus.NOT_VERIFIED),
    val realServiceAcceptance: RealServiceAcceptanceUiStatus = RealServiceAcceptanceUiStatus(
        com.nanzhufeng.ai.ai.RealServiceAcceptanceUiState.PREPARATION_INCOMPLETE,
    ),
)

class ModelSettingsViewModel(
    private val loadConfiguration: LoadModelServiceConfigurationUseCase,
    private val saveConfiguration: SaveModelServiceConfigurationUseCase,
    private val loadRegistryStatus: LoadRegistryVerificationStatusUseCase,
    private val verifyOpenRouterRegistry: VerifyOpenRouterRegistryUseCase,
    private val loadRealServiceAcceptanceStatus: LoadRealServiceAcceptanceUiStatusUseCase,
    private val realServiceReadiness: P2MRealServiceReadinessUseCase,
    private val realServiceExecutor: P2MRealServiceExecutor,
    private val directChatCallAudit: DirectChatCallAuditStore,
    private val providerDiagnostics: ProviderDiagnosticStore,
    private val contextSelectionAudits: ContextSelectionAuditStore,
    private val loadRoutingPolicy: LoadChatRoutingPolicyUseCase,
    private val saveRoutingPolicy: SaveChatRoutingPolicyUseCase,
    private val providerConnectionProbe: ProviderConnectionProbe,
) : ViewModel() {
    var state by mutableStateOf(ModelSettingsUiState())
        private set

    init {
        refresh()
    }

    fun showDialog() {
        state = state.copy(dialogVisible = true, revealedCredential = null, notice = null, error = null)
        refresh()
    }

    fun dismissDialog() {
        if (!state.saving && !state.verifyingRegistry && !state.realServiceExecuting) state = state.copy(dialogVisible = false, revealedCredential = null, error = null)
    }

    fun revealStoredCredential(providerId: ProviderId) {
        if (state.saving || state.providerConfigurations[providerId]?.credentialState != com.nanzhufeng.ai.domain.CredentialState.STORED) return
        viewModelScope.launch {
            val revealed = withContext(Dispatchers.IO) {
                loadConfiguration.revealStoredCredential(providerId)?.let { chars ->
                    try { chars.concatToString() } finally { chars.fill('\u0000') }
                }
            }
            state = state.copy(revealedCredential = revealed)
        }
    }

    fun showRealServiceConfirmation() {
        if (state.realServiceExecuting) return
        viewModelScope.launch {
            val readiness = withContext(Dispatchers.IO) { realServiceReadiness.execute() }
            state = when (readiness) {
                is P2MRealServiceReadiness.Ready -> state.copy(
                    realServiceConfirmationVisible = true,
                    realServiceConfirmationChecked = false,
                    realServiceReadiness = readiness,
                    notice = null,
                    error = null,
                )
                is P2MRealServiceReadiness.Blocked -> state.copy(
                    realServiceReadiness = readiness,
                    error = CaptureUiError("一次真实验收不可发送", readiness.reason),
                )
            }
        }
    }

    fun setRealServiceConfirmationChecked(checked: Boolean) {
        if (!state.realServiceExecuting) state = state.copy(realServiceConfirmationChecked = checked)
    }

    fun dismissRealServiceConfirmation() {
        if (!state.realServiceExecuting) state = state.copy(realServiceConfirmationVisible = false, realServiceConfirmationChecked = false)
    }

    fun confirmRealServiceOnce() {
        val ready = state.realServiceReadiness as? P2MRealServiceReadiness.Ready ?: return
        if (!state.realServiceConfirmationVisible || !state.realServiceConfirmationChecked || state.realServiceExecuting) return
        state = state.copy(realServiceExecuting = true, notice = null, error = null)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { realServiceExecutor.execute(ready.spec.fingerprint()) }
            val summary = withContext(Dispatchers.IO) { realServiceExecutor.readPersistedSummary() }
            state = when (result) {
                is P2MRealServiceExecutionResult.Completed -> {
                    val failure = result.taskResult as? AiTaskRunResult.Failure
                    val noProviderAttempt = failure?.invocation?.taskRun?.attempts?.isEmpty() == true
                    state.copy(
                        realServiceExecuting = false,
                        realServiceConfirmationVisible = false,
                        realServiceConfirmationChecked = false,
                        realServiceSummary = summary,
                        notice = if (failure == null) "一次真实服务请求已结束；安全运行元数据已保存到本机。" else null,
                        error = failure?.let {
                            CaptureUiError(
                                if (noProviderAttempt) "一次真实验收未发送" else "一次真实服务请求已结束",
                                "安全错误分类：${it.error.javaClass.simpleName}。${if (noProviderAttempt) "未创建 Provider Attempt。" else "不会自动重试。"}",
                            )
                        },
                    )
                }
                is P2MRealServiceExecutionResult.Blocked -> state.copy(
                    realServiceExecuting = false,
                    realServiceConfirmationVisible = false,
                    realServiceConfirmationChecked = false,
                    realServiceSummary = summary,
                    error = CaptureUiError("一次真实验收未发送", result.reason),
                )
                is P2MRealServiceExecutionResult.NotReady -> state.copy(
                    realServiceExecuting = false,
                    realServiceConfirmationVisible = false,
                    realServiceConfirmationChecked = false,
                    realServiceSummary = summary,
                    error = CaptureUiError("一次真实验收已停止", result.reason),
                )
            }
        }
    }

    fun verifyRegistry() {
        if (state.verifyingRegistry || state.saving) return
        state = state.copy(verifyingRegistry = true, notice = null, error = null)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { verifyOpenRouterRegistry.execute() }
            state = when (result) {
                is VerifyOpenRouterRegistryResult.Verified -> state.copy(
                    verifyingRegistry = false,
                    registryStatus = RegistryVerificationStatusView(
                        RegistryVerificationDisplayStatus.VERIFIED, result.snapshot.sourceUrl,
                        result.snapshot.lastVerifiedAt, result.snapshot.catalogVersion,
                    ),
                    notice = "公开模型目录已核验；未读取 API Key，也没有发送文字、图片或 Prompt。",
                )
                is VerifyOpenRouterRegistryResult.StableFallback -> state.copy(
                    verifyingRegistry = false,
                    registryStatus = RegistryVerificationStatusView(
                        RegistryVerificationDisplayStatus.STABLE_FALLBACK, result.snapshot.sourceUrl,
                        result.snapshot.lastVerifiedAt, result.snapshot.catalogVersion, result.failure,
                    ),
                    notice = "本次目录核验失败，继续使用上一稳定快照；没有发起推理请求。",
                )
                is VerifyOpenRouterRegistryResult.Unavailable -> state.copy(
                    verifyingRegistry = false,
                    registryStatus = RegistryVerificationStatusView(
                        RegistryVerificationDisplayStatus.NOT_VERIFIED, failure = result.failure,
                    ),
                    error = CaptureUiError("公开目录尚未核验", "请检查网络后重试；当前不能选择真实模型，也没有发送任何内容。"),
                )
            }
        }
    }

    /** User-initiated fixed "hi" probe; no conversation text or attachment is used. */
    fun testConnection(providerId: ProviderId) {
        if (state.probingConnection || state.saving || state.verifyingRegistry) return
        state = state.copy(probingConnection = true, notice = null, error = null)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { providerConnectionProbe.execute(providerId) }
            val diagnostics = withContext(Dispatchers.IO) { providerDiagnostics.recent(8) }
            state = when (result) {
                is ProviderConnectionProbeResult.Connected -> state.copy(
                    probingConnection = false, recentDiagnostics = diagnostics,
                    notice = "连接成功：${result.endpointHost} · ${result.apiModelId} · ${result.latencyMs} ms。",
                )
                is ProviderConnectionProbeResult.Failed -> state.copy(
                    probingConnection = false, recentDiagnostics = diagnostics,
                    error = CaptureUiError(
                        "连接测试失败：${result.errorClass.connectionTestLabel()}",
                        "${result.httpStatus?.let { "HTTP $it；" } ?: "未建立连接；"}${result.errorClass.connectionTestNextStep()} 可到“运行诊断”查看本机脱敏记录。",
                    ),
                )
                is ProviderConnectionProbeResult.Blocked -> state.copy(
                    probingConnection = false, recentDiagnostics = diagnostics,
                    error = CaptureUiError("连接测试未发送", result.reason),
                )
            }
        }
    }

    fun save(providerId: ProviderId, enabled: Boolean, presetId: ModelPresetId, replacementCredential: String?) {
        if (state.saving) return
        state = state.copy(saving = true, notice = null, error = null)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                saveConfiguration.execute(providerId, enabled, presetId, replacementCredential)
            }
            state = when (result) {
                is SaveModelServiceConfigurationResult.Saved -> state.copy(
                    saving = false,
                    dialogVisible = false,
                    revealedCredential = null,
                    configuration = if (providerId == ProviderId.OPENROUTER) result.configuration else state.configuration,
                    providerConfigurations = state.providerConfigurations + (providerId to result.configuration),
                    notice = "模型服务设置已安全保存在本机。",
                )
                is SaveModelServiceConfigurationResult.Rejected -> state.copy(
                    saving = false,
                    error = result.error.toModelSettingsUiError(),
                )
            }
        }
    }

    fun saveRoutingPolicy(policy: ChatRoutingPolicy) {
        if (state.saving) return
        state = state.copy(saving = true, notice = null, error = null)
        viewModelScope.launch {
            val saved = runCatching { withContext(Dispatchers.IO) { saveRoutingPolicy.execute(policy) } }
            state = saved.fold(
                onSuccess = { state.copy(saving = false, routingPolicy = it, notice = "自动路由与兜底策略已保存在本机。") },
                onFailure = { state.copy(saving = false, error = CaptureUiError("路由设置保存失败", "已有策略保持不变，请稍后重试。")) },
            )
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            val dialogVisible = state.dialogVisible
            val providerConfigurations = withContext(Dispatchers.IO) {
                listOf(ProviderId.OPENROUTER, ProviderId.QWEN, ProviderId.DEEPSEEK)
                    .mapNotNull { provider -> loadConfiguration.execute(provider)?.let { provider to it } }
                    .toMap()
            }
            val configuration = providerConfigurations[ProviderId.OPENROUTER]
            val registryStatus = withContext(Dispatchers.IO) { loadRegistryStatus.execute() }
            val acceptance = withContext(Dispatchers.IO) { loadRealServiceAcceptanceStatus.execute() }
            val readiness = withContext(Dispatchers.IO) { realServiceReadiness.execute() }
            val callAuditSummary = withContext(Dispatchers.IO) { directChatCallAudit.summary() }
            val recentDiagnostics = withContext(Dispatchers.IO) { providerDiagnostics.recent(8) }
            val recentContextSelections = withContext(Dispatchers.IO) { contextSelectionAudits.recent(50) }
            val routingPolicy = withContext(Dispatchers.IO) { loadRoutingPolicy.execute() }
            withContext(Dispatchers.IO) { realServiceExecutor.ensureSuccessfulTranscriptBinding() }
            val summary = withContext(Dispatchers.IO) { realServiceExecutor.readPersistedSummary() }
            state = ModelSettingsUiState(
                loading = false, dialogVisible = dialogVisible, configuration = configuration, providerConfigurations = providerConfigurations, callAuditSummary = callAuditSummary, registryStatus = registryStatus,
                recentDiagnostics = recentDiagnostics,
                recentContextSelections = recentContextSelections,
                realServiceAcceptance = acceptance, realServiceReadiness = readiness, realServiceSummary = summary,
                routingPolicy = routingPolicy,
            )
        }
    }

    class Factory(
        private val loadConfiguration: LoadModelServiceConfigurationUseCase,
        private val saveConfiguration: SaveModelServiceConfigurationUseCase,
        private val loadRegistryStatus: LoadRegistryVerificationStatusUseCase,
        private val verifyOpenRouterRegistry: VerifyOpenRouterRegistryUseCase,
        private val loadRealServiceAcceptanceStatus: LoadRealServiceAcceptanceUiStatusUseCase,
        private val realServiceReadiness: P2MRealServiceReadinessUseCase,
        private val realServiceExecutor: P2MRealServiceExecutor,
        private val directChatCallAudit: DirectChatCallAuditStore,
        private val providerDiagnostics: ProviderDiagnosticStore,
        private val contextSelectionAudits: ContextSelectionAuditStore,
        private val loadRoutingPolicy: LoadChatRoutingPolicyUseCase,
        private val saveRoutingPolicy: SaveChatRoutingPolicyUseCase,
        private val providerConnectionProbe: ProviderConnectionProbe,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ModelSettingsViewModel::class.java))
            return ModelSettingsViewModel(
                loadConfiguration, saveConfiguration, loadRegistryStatus, verifyOpenRouterRegistry,
                loadRealServiceAcceptanceStatus, realServiceReadiness, realServiceExecutor, directChatCallAudit, providerDiagnostics, contextSelectionAudits,
                loadRoutingPolicy, saveRoutingPolicy, providerConnectionProbe,
            ) as T
        }
    }
}

private fun com.nanzhufeng.ai.domain.ProviderDiagnosticErrorClass.connectionTestLabel(): String = when (this) {
    com.nanzhufeng.ai.domain.ProviderDiagnosticErrorClass.AUTHENTICATION -> "API Key 无效"
    com.nanzhufeng.ai.domain.ProviderDiagnosticErrorClass.BALANCE -> "额度不足"
    com.nanzhufeng.ai.domain.ProviderDiagnosticErrorClass.RATE_LIMIT -> "请求过于频繁"
    com.nanzhufeng.ai.domain.ProviderDiagnosticErrorClass.MODEL_NOT_FOUND -> "模型不可用"
    com.nanzhufeng.ai.domain.ProviderDiagnosticErrorClass.INVALID_REQUEST,
    com.nanzhufeng.ai.domain.ProviderDiagnosticErrorClass.STREAM_REQUIRED -> "服务参数不兼容"
    com.nanzhufeng.ai.domain.ProviderDiagnosticErrorClass.TIMEOUT -> "连接超时"
    com.nanzhufeng.ai.domain.ProviderDiagnosticErrorClass.NETWORK -> "网络连接失败"
    com.nanzhufeng.ai.domain.ProviderDiagnosticErrorClass.RESPONSE_TOO_LARGE,
    com.nanzhufeng.ai.domain.ProviderDiagnosticErrorClass.RESPONSE_FORMAT -> "服务返回异常"
    com.nanzhufeng.ai.domain.ProviderDiagnosticErrorClass.SERVER -> "服务暂时不可用"
    com.nanzhufeng.ai.domain.ProviderDiagnosticErrorClass.UNKNOWN -> "原因未识别"
}

private fun com.nanzhufeng.ai.domain.ProviderDiagnosticErrorClass.connectionTestNextStep(): String = when (this) {
    com.nanzhufeng.ai.domain.ProviderDiagnosticErrorClass.AUTHENTICATION -> "请检查并重新保存 API Key。"
    com.nanzhufeng.ai.domain.ProviderDiagnosticErrorClass.BALANCE -> "请检查服务商额度。"
    com.nanzhufeng.ai.domain.ProviderDiagnosticErrorClass.RATE_LIMIT -> "请稍后再试。"
    com.nanzhufeng.ai.domain.ProviderDiagnosticErrorClass.MODEL_NOT_FOUND -> "请更换模型后保存。"
    com.nanzhufeng.ai.domain.ProviderDiagnosticErrorClass.INVALID_REQUEST,
    com.nanzhufeng.ai.domain.ProviderDiagnosticErrorClass.STREAM_REQUIRED -> "请更换模型或服务商后重试。"
    com.nanzhufeng.ai.domain.ProviderDiagnosticErrorClass.TIMEOUT,
    com.nanzhufeng.ai.domain.ProviderDiagnosticErrorClass.NETWORK -> "请检查网络后重试。"
    com.nanzhufeng.ai.domain.ProviderDiagnosticErrorClass.RESPONSE_TOO_LARGE,
    com.nanzhufeng.ai.domain.ProviderDiagnosticErrorClass.RESPONSE_FORMAT,
    com.nanzhufeng.ai.domain.ProviderDiagnosticErrorClass.SERVER,
    com.nanzhufeng.ai.domain.ProviderDiagnosticErrorClass.UNKNOWN -> "请稍后重试或更换服务商。"
}

private fun AiTaskError.toModelSettingsUiError(): CaptureUiError = when (this) {
    AiTaskError.ProviderCredentialMissing -> CaptureUiError(
        "启用前需要 API Key",
        "请输入本机 OpenRouter API Key；当前没有发起任何网络请求。",
    )
    AiTaskError.ProviderCredentialInvalid -> CaptureUiError(
        "API Key 格式不完整",
        "请检查是否误粘贴空白、控制字符或过短内容。",
    )
    AiTaskError.ProviderCredentialStorageFailed -> CaptureUiError(
        "API Key 无法安全保存",
        "请确认设备安全存储可用后重试；Key 未写入 Room 或日志。",
    )
    AiTaskError.PersistenceConflict -> CaptureUiError(
        "模型设置保存失败",
        "请稍后重试；已有设置保持不变。",
    )
    else -> CaptureUiError("模型设置无效", "请重新选择预设后重试。")
}

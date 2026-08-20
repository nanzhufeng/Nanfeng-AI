package com.nanzhufeng.ai.ai

import com.nanzhufeng.ai.domain.CaptureDraft
import com.nanzhufeng.ai.domain.CaptureDraftId
import com.nanzhufeng.ai.domain.CaptureSourceType
import com.nanzhufeng.ai.domain.ContentBlock
import com.nanzhufeng.ai.domain.Conversation
import com.nanzhufeng.ai.domain.ConversationDraft
import com.nanzhufeng.ai.domain.ConversationId
import com.nanzhufeng.ai.domain.ConversationRepository
import com.nanzhufeng.ai.domain.ConversationSnapshot
import com.nanzhufeng.ai.domain.CredentialPresence
import com.nanzhufeng.ai.domain.AiTask
import com.nanzhufeng.ai.domain.AiTaskId
import com.nanzhufeng.ai.domain.AiTaskRunResult
import com.nanzhufeng.ai.domain.CostDisclosure
import com.nanzhufeng.ai.domain.EgressConsent
import com.nanzhufeng.ai.domain.GeneratedCandidateRepository
import com.nanzhufeng.ai.domain.HarnessProfile
import com.nanzhufeng.ai.domain.InvocationRepository
import com.nanzhufeng.ai.domain.InvocationRecord
import com.nanzhufeng.ai.domain.InvocationStatus
import com.nanzhufeng.ai.domain.LoadModelServiceConfigurationUseCase
import com.nanzhufeng.ai.domain.ModelCapabilities
import com.nanzhufeng.ai.domain.ModelDescriptor
import com.nanzhufeng.ai.domain.ModelPricing
import com.nanzhufeng.ai.domain.ModelRegistryResolution
import com.nanzhufeng.ai.domain.ModelServiceConfiguration
import com.nanzhufeng.ai.domain.MessageInvocationReference
import com.nanzhufeng.ai.domain.MessageNode
import com.nanzhufeng.ai.domain.MessageNodeId
import com.nanzhufeng.ai.domain.MessageRole
import com.nanzhufeng.ai.domain.MessageDeliveryState
import com.nanzhufeng.ai.domain.CredentialState
import com.nanzhufeng.ai.domain.PersistGeneratedCandidateUseCase
import com.nanzhufeng.ai.domain.ProviderCredentialStore
import com.nanzhufeng.ai.domain.ModelRegistrySnapshot
import com.nanzhufeng.ai.domain.ProviderId
import com.nanzhufeng.ai.domain.RegistryVerificationStatus
import com.nanzhufeng.ai.domain.RunAiTaskUseCase
import com.nanzhufeng.ai.domain.SourceEvidence
import com.nanzhufeng.ai.domain.TaskRunStatus
import com.nanzhufeng.ai.domain.VersionedModelRegistry
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * P2-L is deliberately an offline preparation contract. It has no transport, credential loader,
 * Authorization value, request JSON, or provider response type. A future, separately authorized
 * task may consume its token only after it wires a real call through P2-K's existing gates.
 */
object RealServiceAcceptanceContract {
    const val ID = "nanfeng-ai-real-service-acceptance"
    const val VERSION = 1
    const val CREDENTIAL_HANDLE = "openrouter-keystore-v1"
    const val DEFAULT_MAX_INPUT_TOKENS = 256
    const val DEFAULT_MAX_OUTPUT_TOKENS = 180
    const val DEFAULT_COST_CAP_MICROS = 10_000L
    const val DEFAULT_CURRENCY = "USD"
    const val DEFAULT_CONNECT_TIMEOUT_MS = 8_000
    const val DEFAULT_READ_TIMEOUT_MS = 30_000
    const val DEFAULT_RETRY_COUNT = 1

    val requiredEvidence = listOf(
        "用户一次性授权记录（仅绑定 RunSpec 指纹）",
        "已验证 Registry Snapshot ID 与 SHA-256",
        "合成夹具 ID、SHA-256、MIME、尺寸",
        "安全 Ledger / Candidate / Knowledge 状态截图或导出元数据",
        "最终 Token、费用或明确未知语义",
        "目标设备与正式签名包验证记录",
    )
}

enum class RealServiceFixtureKind { TEXT, IMAGE }

data class RealServiceFixtureDescriptor(
    val id: String,
    val kind: RealServiceFixtureKind,
    val mimeType: String,
    val byteCount: Int,
    val sha256: String,
    val widthPx: Int? = null,
    val heightPx: Int? = null,
) {
    init {
        require(id.isNotBlank() && sha256.matches(Regex("[0-9a-f]{64}")))
        require(byteCount > 0)
        require((widthPx == null) == (heightPx == null))
        require(widthPx == null || widthPx > 0 && heightPx!! > 0)
    }
}

/** Repository-auditable, synthetic fixtures only. Neither value is sourced from a user draft. */
object P2LSyntheticFixtures {
    private const val TEXT = "将这条非敏感演示事项整理为标题和两条后续步骤：周三复核本地学习笔记，并安排下次复盘。"
    // Deliberately simple program-owned SVG: blue rectangle, green circle, no embedded metadata.
    private const val IMAGE_SVG = """<svg xmlns="http://www.w3.org/2000/svg" width="96" height="64" viewBox="0 0 96 64"><rect width="96" height="64" fill="#eef5ff"/><rect x="12" y="14" width="36" height="28" rx="4" fill="#4a90e2"/><circle cx="70" cy="32" r="14" fill="#48a868"/></svg>"""

    val text: RealServiceFixtureDescriptor = descriptor("p2l-text-organize-v1", RealServiceFixtureKind.TEXT, "text/plain; charset=utf-8", TEXT.toByteArray())
    val image: RealServiceFixtureDescriptor = descriptor("p2l-image-shapes-v1", RealServiceFixtureKind.IMAGE, "image/svg+xml", IMAGE_SVG.toByteArray(), 96, 64)

    fun textContent(id: String): String? = if (id == text.id) TEXT else null
    fun imageBytes(id: String): ByteArray? = if (id == image.id) IMAGE_SVG.toByteArray() else null
    fun all(): List<RealServiceFixtureDescriptor> = listOf(text, image)

    private fun descriptor(
        id: String,
        kind: RealServiceFixtureKind,
        mime: String,
        bytes: ByteArray,
        width: Int? = null,
        height: Int? = null,
    ) = RealServiceFixtureDescriptor(id, kind, mime, bytes.size, sha256(bytes), width, height)
}

/**
 * A separately versioned P2-M fixture.  It intentionally does not replace the P2-L v1 fixture:
 * the latter remains part of the already-recorded v1 RunSpec and must stay reproducible.
 */
object P2MSyntheticFixtures {
    private const val TEXT_V2 = "将这条非敏感演示事项整理为标题和两条后续步骤：周五核对本地学习笔记，并预约下周复盘。"

    val textV2: RealServiceFixtureDescriptor = RealServiceFixtureDescriptor(
        id = "p2m-text-organize-v2",
        kind = RealServiceFixtureKind.TEXT,
        mimeType = "text/plain; charset=utf-8",
        byteCount = TEXT_V2.toByteArray().size,
        sha256 = sha256(TEXT_V2.toByteArray()),
    )

    fun textContent(id: String): String? = if (id == textV2.id) TEXT_V2 else null
    fun all(): List<RealServiceFixtureDescriptor> = listOf(textV2)
}

enum class ExpectedLedgerState { ONE_SAFE_ATTEMPT_AFTER_AUTHORIZATION, BLOCKED_WITHOUT_ATTEMPT_BEFORE_AUTHORIZATION }
enum class ExpectedCandidateState { PENDING_REVIEW_ON_SUCCESS, NONE_ON_FAILURE_OR_BLOCK }
enum class ExpectedKnowledgeState { NO_WRITE_UNTIL_USER_REVIEW }

data class RealServiceExpectedState(
    val ledger: ExpectedLedgerState = ExpectedLedgerState.ONE_SAFE_ATTEMPT_AFTER_AUTHORIZATION,
    val candidate: ExpectedCandidateState = ExpectedCandidateState.PENDING_REVIEW_ON_SUCCESS,
    val knowledge: ExpectedKnowledgeState = ExpectedKnowledgeState.NO_WRITE_UNTIL_USER_REVIEW,
)

data class RealServiceRunSpec(
    val contractId: String = RealServiceAcceptanceContract.ID,
    val contractVersion: Int = RealServiceAcceptanceContract.VERSION,
    val runId: String,
    val providerId: ProviderId,
    val modelId: String,
    val registrySnapshotId: String,
    val registryCatalogSha256: String,
    val fixture: RealServiceFixtureDescriptor,
    val maxInputTokens: Int,
    val maxOutputTokens: Int,
    val costCapMicros: Long,
    val currencyCode: String,
    /** A hash only; this is not a key, prompt, image body, or user identity. */
    val consentFingerprint: String,
    val credentialHandle: String,
    val connectTimeoutMs: Int,
    val readTimeoutMs: Int,
    val retryCount: Int,
    val expectedState: RealServiceExpectedState,
    val evidenceChecklist: List<String> = RealServiceAcceptanceContract.requiredEvidence,
) {
    init {
        require(contractId == RealServiceAcceptanceContract.ID && contractVersion == RealServiceAcceptanceContract.VERSION)
        require(runId.matches(Regex("[a-z0-9-]{8,80}")))
        require(providerId == ProviderId.OPENROUTER && modelId.isNotBlank() && registrySnapshotId.isNotBlank())
        require(registryCatalogSha256.matches(Regex("[0-9a-f]{64}")))
        require(maxInputTokens > 0 && maxOutputTokens > 0 && costCapMicros >= 0)
        require(currencyCode.matches(Regex("[A-Z]{3}")))
        require(consentFingerprint.matches(Regex("[0-9a-f]{64}")))
        require(credentialHandle == RealServiceAcceptanceContract.CREDENTIAL_HANDLE)
        require(connectTimeoutMs in 1..RealServiceAcceptanceContract.DEFAULT_CONNECT_TIMEOUT_MS)
        require(readTimeoutMs in 1..RealServiceAcceptanceContract.DEFAULT_READ_TIMEOUT_MS)
        require(retryCount in 0..RealServiceAcceptanceContract.DEFAULT_RETRY_COUNT)
        require(evidenceChecklist.isNotEmpty())
    }

    fun fingerprint(): String = sha256(
        listOf(contractId, contractVersion, runId, providerId, modelId, registrySnapshotId, registryCatalogSha256,
            fixture.id, fixture.sha256, fixture.mimeType, fixture.byteCount, fixture.widthPx, fixture.heightPx,
            maxInputTokens, maxOutputTokens, costCapMicros, currencyCode, credentialHandle, connectTimeoutMs,
            readTimeoutMs, retryCount, expectedState.ledger, expectedState.candidate, expectedState.knowledge)
            .joinToString("|").toByteArray(),
    )
}

data class RealServiceAcceptanceConsent(
    val runSpecFingerprint: String,
    val consentFingerprint: String,
    val acceptedAt: Instant,
    val explicitSingleUseAccepted: Boolean,
)

fun interface CredentialPresenceProbe {
    /** Must expose only existence; implementations must not return or decrypt credential bytes. */
    fun presence(providerId: ProviderId): CredentialPresence
}

enum class RealServiceDryRunIssue {
    EGRESS_POLICY_NOT_DISABLED,
    REGISTRY_MISSING_OR_STALE,
    MODEL_UNAVAILABLE_OR_CAPABILITY_MISMATCH,
    FIXTURE_TAMPERED_OR_UNSUPPORTED,
    CONTENT_UNSAFE_OR_OVERSIZE,
    CONTEXT_LIMIT_EXCEEDED,
    BUDGET_CAP_EXCEEDED,
    CREDENTIAL_NOT_PRESENT,
    CONSENT_FINGERPRINT_MISMATCH,
}

data class RealServiceDryRunCheck(val name: String, val passed: Boolean, val detail: String)

enum class RealServiceCostEstimateState { KNOWN_WITHIN_CAP, UNKNOWN_NOT_ZERO, KNOWN_OVER_CAP }

data class RealServiceDryRunReport(
    val passed: Boolean,
    val runSpecFingerprint: String,
    val fixtureId: String,
    val fixtureSha256: String,
    val fixtureMimeType: String,
    val fixtureDimensions: String?,
    val costEstimateState: RealServiceCostEstimateState,
    val estimatedCostMicros: Long?,
    val issues: List<RealServiceDryRunIssue>,
    val checks: List<RealServiceDryRunCheck>,
    val authorizationConstructed: Boolean = false,
    val networkRequestsConstructed: Int = 0,
    val credentialBytesRead: Boolean = false,
)

/** Pure offline preflight. It cannot receive a transport or a credential loader by construction. */
class RealServiceDryRunPreflight(
    private val registry: VersionedModelRegistry,
    private val credentialPresence: CredentialPresenceProbe,
    private val egressPolicy: OpenRouterEgressPolicy,
    private val sanitizer: OpenRouterRequestSanitizer = OpenRouterRequestSanitizer(),
    private val clock: Clock,
) {
    fun execute(spec: RealServiceRunSpec, consent: RealServiceAcceptanceConsent): RealServiceDryRunReport {
        val checks = mutableListOf<RealServiceDryRunCheck>()
        val issues = mutableListOf<RealServiceDryRunIssue>()
        fun check(name: String, passed: Boolean, detail: String, issue: RealServiceDryRunIssue) {
            checks += RealServiceDryRunCheck(name, passed, detail)
            if (!passed) issues += issue
        }

        check("生产出站保持关闭", egressPolicy is OpenRouterEgressPolicy.Disabled, "DryRun 不会创建 Authorization 或网络请求。", RealServiceDryRunIssue.EGRESS_POLICY_NOT_DISABLED)
        val knownFixture = (P2LSyntheticFixtures.all() + P2MSyntheticFixtures.all()).firstOrNull { it.id == spec.fixture.id }
        val fixtureMatches = knownFixture != null && knownFixture == spec.fixture
        check("合成夹具完整性", fixtureMatches, "仅记录夹具 ID、SHA-256、MIME 和尺寸。", RealServiceDryRunIssue.FIXTURE_TAMPERED_OR_UNSUPPORTED)

        val snapshot = registry.currentSnapshot(ProviderId.OPENROUTER)
        val snapshotMatches = snapshot != null && snapshot.verificationStatus == RegistryVerificationStatus.VERIFIED &&
            snapshot.id.value == spec.registrySnapshotId && snapshot.catalogSha256 == spec.registryCatalogSha256
        check("Registry Snapshot", snapshotMatches, "验证 Snapshot ID 和 SHA-256，不读取目录原文。", RealServiceDryRunIssue.REGISTRY_MISSING_OR_STALE)
        val model = snapshot?.models?.firstOrNull { it.id == spec.modelId }
        val capabilityMatches = model != null && model.capabilities.supportsText && model.capabilities.supportsStructuredOutput &&
            (spec.fixture.kind != RealServiceFixtureKind.IMAGE || model.capabilities.supportsVision)
        check("模型能力", capabilityMatches, "要求文本、结构化输出；图片另要求视觉能力。", RealServiceDryRunIssue.MODEL_UNAVAILABLE_OR_CAPABILITY_MISMATCH)

        val contentSafe = when (spec.fixture.kind) {
            RealServiceFixtureKind.TEXT -> (P2LSyntheticFixtures.textContent(spec.fixture.id)
                ?: P2MSyntheticFixtures.textContent(spec.fixture.id))?.let { text ->
                val draft = CaptureDraft(
                    id = CaptureDraftId("p2l-dryrun-${spec.fixture.id}"), text = text, attachments = emptyList(),
                    sourceEvidence = listOf(SourceEvidence(CaptureSourceType.MANUAL_TEXT, clock.instant(), contributedFields = setOf("text"))),
                    createdAt = clock.instant(),
                )
                sanitizer.sanitize(draft) == text && text.toByteArray().size <= spec.maxInputTokens
            } == true
            // P2-K deliberately has no image encoding; image fixture integrity is covered, execution remains future work.
            RealServiceFixtureKind.IMAGE -> false
        }
        check("内容清洗与尺寸", contentSafe, "文本不输出正文；图片在 P2-K 图片编码前保持不可执行。", RealServiceDryRunIssue.CONTENT_UNSAFE_OR_OVERSIZE)
        val contextFits = model?.contextWindowTokens?.let { it >= spec.maxInputTokens + spec.maxOutputTokens } == true
        check("最大输入与输出", contextFits, "以 RunSpec 上限核验模型上下文，不依赖实时 Provider。", RealServiceDryRunIssue.CONTEXT_LIMIT_EXCEEDED)

        val estimate = estimateCostMicros(snapshot, spec)
        val costState = when {
            estimate == null -> RealServiceCostEstimateState.UNKNOWN_NOT_ZERO
            estimate > spec.costCapMicros -> RealServiceCostEstimateState.KNOWN_OVER_CAP
            else -> RealServiceCostEstimateState.KNOWN_WITHIN_CAP
        }
        check("费用硬上限", costState != RealServiceCostEstimateState.KNOWN_OVER_CAP,
            if (estimate == null) "目录价格未知；预计费用保持未知，不写作 0。" else "最大估算仅与硬上限比较。",
            RealServiceDryRunIssue.BUDGET_CAP_EXCEEDED)
        check("Credential 存在性", credentialPresence.presence(ProviderId.OPENROUTER) == CredentialPresence.PRESENT,
            "只检查存在性接口；不读取或解密 Key。", RealServiceDryRunIssue.CREDENTIAL_NOT_PRESENT)
        val consentMatches = consent.explicitSingleUseAccepted && consent.runSpecFingerprint == spec.fingerprint() &&
            consent.consentFingerprint == spec.consentFingerprint
        check("一次性 Consent 指纹", consentMatches, "Consent 只绑定当前 RunSpec 哈希。", RealServiceDryRunIssue.CONSENT_FINGERPRINT_MISMATCH)

        return RealServiceDryRunReport(
            passed = issues.isEmpty(), runSpecFingerprint = spec.fingerprint(), fixtureId = spec.fixture.id,
            fixtureSha256 = spec.fixture.sha256, fixtureMimeType = spec.fixture.mimeType,
            fixtureDimensions = spec.fixture.widthPx?.let { "${it}x${spec.fixture.heightPx}" },
            costEstimateState = costState, estimatedCostMicros = estimate, issues = issues, checks = checks,
        )
    }

    private fun estimateCostMicros(snapshot: ModelRegistrySnapshot?, spec: RealServiceRunSpec): Long? {
        val model = snapshot?.models?.firstOrNull { it.id == spec.modelId } ?: return null
        val pricing = model.pricing
        if (pricing.currencyCode != spec.currencyCode || pricing.inputMicrosPerToken == null || pricing.outputMicrosPerToken == null) return null
        return runCatching {
            Math.addExact(
                Math.multiplyExact(pricing.inputMicrosPerToken, spec.maxInputTokens.toLong()),
                Math.multiplyExact(pricing.outputMicrosPerToken, spec.maxOutputTokens.toLong()),
            )
        }.getOrNull()
    }
}

enum class RealServiceTokenState { ISSUED, CONSUMED }

data class RealServiceAcceptanceToken(
    val nonce: String,
    val runSpecFingerprint: String,
    val expiresAt: Instant,
    val state: RealServiceTokenState,
)

sealed interface RealServiceTokenConsumeResult {
    data object Consumed : RealServiceTokenConsumeResult
    data object Missing : RealServiceTokenConsumeResult
    data object SpecMismatch : RealServiceTokenConsumeResult
    data object Expired : RealServiceTokenConsumeResult
    data object AlreadyConsumed : RealServiceTokenConsumeResult
}

interface RealServiceAcceptanceTokenStore {
    fun issue(token: RealServiceAcceptanceToken): Boolean
    fun consume(nonce: String, expectedSpecFingerprint: String, now: Instant): RealServiceTokenConsumeResult
    fun find(nonce: String): RealServiceAcceptanceToken?
}

class InMemoryRealServiceAcceptanceTokenStore : RealServiceAcceptanceTokenStore {
    private val tokens = linkedMapOf<String, RealServiceAcceptanceToken>()
    @Synchronized override fun issue(token: RealServiceAcceptanceToken): Boolean = if (tokens.containsKey(token.nonce)) false else {
        tokens[token.nonce] = token; true
    }
    @Synchronized override fun consume(nonce: String, expectedSpecFingerprint: String, now: Instant): RealServiceTokenConsumeResult =
        consumeStored(tokens[nonce], nonce, expectedSpecFingerprint, now) { tokens[nonce] = it; true }
    override fun find(nonce: String): RealServiceAcceptanceToken? = synchronized(this) { tokens[nonce] }
}

/** App-private file store so a recreated Activity/process cannot reuse a consumed nonce. */
class FileRealServiceAcceptanceTokenStore(private val root: File) : RealServiceAcceptanceTokenStore {
    init { require(root.exists() || root.mkdirs()) { "无法创建验收令牌目录。" } }
    @Synchronized override fun issue(token: RealServiceAcceptanceToken): Boolean {
        val file = fileFor(token.nonce)
        if (file.exists()) return false
        return writeAtomically(file, token)
    }
    @Synchronized override fun consume(nonce: String, expectedSpecFingerprint: String, now: Instant): RealServiceTokenConsumeResult {
        val current = find(nonce)
        return consumeStored(current, nonce, expectedSpecFingerprint, now) { writeAtomically(fileFor(nonce), it) }
    }
    @Synchronized override fun find(nonce: String): RealServiceAcceptanceToken? = runCatching {
        val values = fileFor(nonce).takeIf(File::isFile)?.readLines()?.mapNotNull { line ->
            line.substringBefore('=', missingDelimiterValue = "").takeIf(String::isNotEmpty)?.let { key -> key to line.substringAfter('=') }
        }?.toMap() ?: return null
        RealServiceAcceptanceToken(values.getValue("nonce"), values.getValue("spec"), Instant.parse(values.getValue("expires")),
            RealServiceTokenState.valueOf(values.getValue("state")))
    }.getOrNull()

    private fun fileFor(nonce: String): File {
        require(nonce.matches(Regex("[0-9a-f-]{36}")))
        return File(root, "p2l-$nonce.token")
    }
    private fun writeAtomically(file: File, token: RealServiceAcceptanceToken): Boolean = runCatching {
        val temporary = File(file.parentFile, "${file.name}.tmp")
        FileOutputStream(temporary).use { output ->
            output.write("nonce=${token.nonce}\nspec=${token.runSpecFingerprint}\nexpires=${token.expiresAt}\nstate=${token.state.name}\n".toByteArray())
            output.fd.sync()
        }
        if (!temporary.renameTo(file)) {
            temporary.delete()
            return false
        }
        true
    }.getOrDefault(false)
}

private fun consumeStored(
    token: RealServiceAcceptanceToken?, nonce: String, expectedSpecFingerprint: String, now: Instant,
    write: (RealServiceAcceptanceToken) -> Boolean,
): RealServiceTokenConsumeResult = when {
    token == null -> RealServiceTokenConsumeResult.Missing
    token.nonce != nonce || token.runSpecFingerprint != expectedSpecFingerprint -> RealServiceTokenConsumeResult.SpecMismatch
    token.expiresAt <= now -> RealServiceTokenConsumeResult.Expired
    token.state == RealServiceTokenState.CONSUMED -> RealServiceTokenConsumeResult.AlreadyConsumed
    write(token.copy(state = RealServiceTokenState.CONSUMED)) -> RealServiceTokenConsumeResult.Consumed
    else -> RealServiceTokenConsumeResult.Missing
}

class RealServiceAcceptanceTokenService(
    private val store: RealServiceAcceptanceTokenStore,
    private val clock: Clock,
) {
    fun issue(
        spec: RealServiceRunSpec,
        consent: RealServiceAcceptanceConsent,
        expiresAt: Instant,
        nonce: String = UUID.randomUUID().toString(),
    ): RealServiceAcceptanceToken? {
        if (!consent.explicitSingleUseAccepted || consent.runSpecFingerprint != spec.fingerprint() ||
            consent.consentFingerprint != spec.consentFingerprint || expiresAt <= clock.instant()) return null
        if (!nonce.matches(Regex("[0-9a-f-]{36}"))) return null
        val token = RealServiceAcceptanceToken(nonce, spec.fingerprint(), expiresAt, RealServiceTokenState.ISSUED)
        return token.takeIf(store::issue)
    }
    fun consume(spec: RealServiceRunSpec, nonce: String): RealServiceTokenConsumeResult =
        store.consume(nonce, spec.fingerprint(), clock.instant())
}

enum class RealServiceAcceptanceUiState { PREPARATION_INCOMPLETE, OFFLINE_PREFLIGHT_PASSED, WAITING_USER_AUTHORIZATION }

data class RealServiceAcceptanceUiStatus(
    val state: RealServiceAcceptanceUiState,
    val fixtureIds: List<String> = P2LSyntheticFixtures.all().map(RealServiceFixtureDescriptor::id),
    val costCapMicros: Long = RealServiceAcceptanceContract.DEFAULT_COST_CAP_MICROS,
    val currencyCode: String = RealServiceAcceptanceContract.DEFAULT_CURRENCY,
)

/** This reads Registry metadata only. It deliberately does not ask the credential store anything. */
class LoadRealServiceAcceptanceUiStatusUseCase(private val registry: VersionedModelRegistry) {
    fun execute(): RealServiceAcceptanceUiStatus = when (val snapshot = registry.currentSnapshot(ProviderId.OPENROUTER)) {
        null -> RealServiceAcceptanceUiStatus(RealServiceAcceptanceUiState.PREPARATION_INCOMPLETE)
        else -> if (snapshot.verificationStatus == RegistryVerificationStatus.VERIFIED && snapshot.catalogSha256 != null)
            RealServiceAcceptanceUiStatus(RealServiceAcceptanceUiState.WAITING_USER_AUTHORIZATION)
        else RealServiceAcceptanceUiStatus(RealServiceAcceptanceUiState.PREPARATION_INCOMPLETE)
    }
}

/** One user-visible confirmation may bind this synthetic text fixture to the saved preset. */
object P2MOpenRouterTextAcceptance {
    /** v1 facts remain readable; this value must never issue a new token again. */
    const val LEGACY_RUN_ID = "p2m-openrouter-text-v1"
    const val LEGACY_TASK_ID = "p2m-openrouter-text-task-v1"

    const val RUN_ID = "p2m-openrouter-text-v2"
    const val TASK_ID = "p2m-openrouter-text-task-v2"
    private const val SINGLE_USE_NONCE = "1ae933d3-9c4f-4fe0-8b8c-5ec7d50b742e"

    fun spec(configuration: ModelServiceConfiguration, registry: VersionedModelRegistry): RealServiceRunSpec? {
        if (configuration.provider.id != ProviderId.OPENROUTER || !configuration.settings.enabled ||
            configuration.credentialState != CredentialState.STORED) return null
        val resolved = registry.resolve(ProviderId.OPENROUTER, configuration.settings.presetId)
            as? ModelRegistryResolution.Resolved ?: return null
        val snapshot = resolved.snapshot
        val model = resolved.model
        if (snapshot.verificationStatus != RegistryVerificationStatus.VERIFIED || snapshot.catalogSha256 == null ||
            !model.capabilities.supportsText || !model.capabilities.supportsStructuredOutput) return null
        return RealServiceRunSpec(
        runId = RUN_ID,
        providerId = ProviderId.OPENROUTER,
        modelId = model.id,
        registrySnapshotId = snapshot.id.value,
        registryCatalogSha256 = snapshot.catalogSha256,
        fixture = P2MSyntheticFixtures.textV2,
        maxInputTokens = 256,
        maxOutputTokens = 180,
        costCapMicros = 10_000L,
        currencyCode = model.pricing.currencyCode ?: "USD",
        consentFingerprint = consentFingerprint(model.id),
        credentialHandle = RealServiceAcceptanceContract.CREDENTIAL_HANDLE,
        connectTimeoutMs = 8_000,
        readTimeoutMs = 30_000,
        retryCount = 0,
        expectedState = RealServiceExpectedState(),
        )
    }

    fun consent(spec: RealServiceRunSpec, acceptedAt: Instant): RealServiceAcceptanceConsent =
        RealServiceAcceptanceConsent(spec.fingerprint(), spec.consentFingerprint, acceptedAt, explicitSingleUseAccepted = true)

    fun nonce(): String = SINGLE_USE_NONCE

    /** Accept old persisted evidence when reading/binding it, but never use it for new issuance. */
    fun isKnownTaskId(taskId: String): Boolean = taskId in setOf(LEGACY_RUN_ID, LEGACY_TASK_ID, RUN_ID, TASK_ID)

    private fun consentFingerprint(modelId: String): String = sha256(
        "2026-08-15|P2-M-v2|single|OpenRouter|$modelId|text-only|USD<=0.01".toByteArray(),
    )
}

sealed interface P2MRealServiceReadiness {
    data class Ready(val configuration: ModelServiceConfiguration, val spec: RealServiceRunSpec, val model: ModelDescriptor) : P2MRealServiceReadiness
    data class Blocked(val reason: String) : P2MRealServiceReadiness
}

/** Reads only saved configuration and credential presence; it cannot load a Key or create egress. */
class P2MRealServiceReadinessUseCase(
    private val loadConfiguration: LoadModelServiceConfigurationUseCase,
    private val registry: VersionedModelRegistry,
) {
    fun execute(): P2MRealServiceReadiness {
        val configuration = loadConfiguration.execute() ?: return P2MRealServiceReadiness.Blocked("当前没有可用的本机模型配置。")
        if (!configuration.settings.enabled) return P2MRealServiceReadiness.Blocked("当前服务未启用，不能发送。")
        if (configuration.credentialState != CredentialState.STORED) return P2MRealServiceReadiness.Blocked("本机凭据不存在，不能发送。")
        val resolved = registry.resolve(ProviderId.OPENROUTER, configuration.settings.presetId)
            as? ModelRegistryResolution.Resolved ?: return P2MRealServiceReadiness.Blocked("当前预设没有已验证的可用模型。")
        val spec = P2MOpenRouterTextAcceptance.spec(configuration, registry)
            ?: return P2MRealServiceReadiness.Blocked("当前模型不满足一次文本验收的安全条件。")
        return P2MRealServiceReadiness.Ready(configuration, spec, resolved.model)
    }
}

data class P2MRealServiceRunSummary(
    val providerLabel: String,
    val presetLabel: String,
    val modelLabel: String,
    val terminalLabel: String,
    val durationMillis: Long?,
    val inputTokens: Long?,
    val outputTokens: Long?,
    val costMicros: Long?,
    val currencyCode: String?,
    /** Content-free local outcome classification; it never contains a provider response. */
    val errorCode: String?,
)

/**
 * The successful P2-M invocation gets one deterministic, app-local transcript owner.  It keeps
 * only a safe acceptance summary; the provider response remains in its existing review store and
 * is never copied into the transcript, evidence, or a runtime event.
 */
sealed interface P2MTranscriptBindingResult {
    data class Bound(
        val conversationId: ConversationId,
        val assistantMessageId: MessageNodeId,
        val replayed: Boolean,
    ) : P2MTranscriptBindingResult
    data class Ignored(val reason: String) : P2MTranscriptBindingResult
    data class Rejected(val reason: String) : P2MTranscriptBindingResult
}

/** Local-only projection owner. It has no credential, transport, or nonce capability. */
class P2MTranscriptOwner(
    private val conversations: ConversationRepository,
) {
    fun bindSuccessfulInvocation(invocation: InvocationRecord): P2MTranscriptBindingResult {
        if (!P2MOpenRouterTextAcceptance.isKnownTaskId(invocation.taskId.value) ||
            invocation.status != InvocationStatus.SUCCEEDED ||
            invocation.taskRun.status != TaskRunStatus.SUCCEEDED
        ) return P2MTranscriptBindingResult.Ignored("只绑定成功的 P2-M TaskRun。")

        val conversationId = ConversationId("p2m:${invocation.id.value}:conversation")
        val userMessageId = MessageNodeId("p2m:${invocation.id.value}:user")
        val assistantMessageId = MessageNodeId("p2m:${invocation.id.value}:assistant")
        val existing = conversations.findById(conversationId)
        if (existing != null) {
            val assistant = existing.nodes.singleOrNull { it.id == assistantMessageId }
            return if (assistant?.role == MessageRole.ASSISTANT && assistant.invocation?.invocationId == invocation.id) {
                P2MTranscriptBindingResult.Bound(conversationId, assistantMessageId, replayed = true)
            } else P2MTranscriptBindingResult.Rejected("既有 P2-M transcript owner 不一致。")
        }

        val startedAt = invocation.taskRun.startedAt
        val completedAt = invocation.taskRun.completedAt
        val user = MessageNode(
            id = userMessageId, conversationId = conversationId, parentMessageId = null, siblingPosition = 0,
            role = MessageRole.USER, content = listOf(ContentBlock.Text("一次受控文本验收")),
            createdAt = startedAt,
        )
        val assistant = MessageNode(
            id = assistantMessageId, conversationId = conversationId, parentMessageId = userMessageId, siblingPosition = 0,
            role = MessageRole.ASSISTANT, content = listOf(ContentBlock.Text("一次受控文本验收已完成。")),
            createdAt = completedAt, deliveryState = MessageDeliveryState.COMPLETE,
            invocation = MessageInvocationReference(invocation.id),
        )
        val snapshot = ConversationSnapshot(
            conversation = Conversation(
                id = conversationId,
                title = "一次真实文本验收",
                currentLeafMessageId = assistantMessageId,
                createdAt = startedAt,
                updatedAt = completedAt,
            ),
            nodes = listOf(user, assistant),
            draft = ConversationDraft(updatedAt = completedAt),
        )
        return runCatching {
            conversations.save(snapshot)
            P2MTranscriptBindingResult.Bound(conversationId, assistantMessageId, replayed = false)
        }.getOrElse { P2MTranscriptBindingResult.Rejected("P2-M transcript owner 未保存。") }
    }
}

sealed interface P2MRealServiceExecutionResult {
    data class NotReady(val reason: String) : P2MRealServiceExecutionResult
    data class Blocked(val report: RealServiceDryRunReport, val reason: String) : P2MRealServiceExecutionResult
    data class Completed(
        val report: RealServiceDryRunReport,
        val nonce: String,
        val taskResult: AiTaskRunResult,
        val transcriptBinding: P2MTranscriptBindingResult,
    ) : P2MRealServiceExecutionResult
}

/**
 * A narrow orchestration seam, not a general egress switch. DryRun stays disabled; only after
 * it passes does this consume the exact persisted nonce and create an adapter for one attempt.
 */
class P2MRealServiceExecutor(
    private val readiness: P2MRealServiceReadinessUseCase,
    private val registry: VersionedModelRegistry,
    private val credentialStore: ProviderCredentialStore,
    private val invocationRepository: InvocationRepository,
    private val persistCandidate: PersistGeneratedCandidateUseCase,
    private val transcriptOwner: P2MTranscriptOwner,
    private val tokenService: RealServiceAcceptanceTokenService,
    private val transport: OpenRouterInferenceTransport,
    private val clock: Clock,
    private val evidenceDirectory: File? = null,
    private val realTextExecutionBridge: P2MRealTextExecutionBridge? = null,
) {
    fun execute(expectedRunSpecFingerprint: String): P2MRealServiceExecutionResult {
        val ready = readiness.execute()
        val readyRun = ready as? P2MRealServiceReadiness.Ready
            ?: return P2MRealServiceExecutionResult.NotReady((ready as P2MRealServiceReadiness.Blocked).reason)
        val spec = readyRun.spec
        if (spec.fingerprint() != expectedRunSpecFingerprint) {
            return P2MRealServiceExecutionResult.NotReady("确认内容已变化，请重新打开确认页。")
        }
        val consent = P2MOpenRouterTextAcceptance.consent(spec, clock.instant())
        // The bridge is an additive, default-fail-closed observation of this existing confirmation.
        // It is intentionally ignored so it cannot alter P2-M's visible gate or its legacy result.
        realTextExecutionBridge?.coordinate(readyRun, P2MExistingVisibleConfirmation(spec.fingerprint(), consent.acceptedAt))
        val report = RealServiceDryRunPreflight(
            registry = registry,
            credentialPresence = CredentialPresenceProbe(credentialStore::credentialPresence),
            egressPolicy = OpenRouterEgressPolicy.Disabled,
            clock = clock,
        ).execute(spec, consent)
        val taskAndDraft = taskAndDraft(spec)
        if (!report.passed) {
            val blocked = RunAiTaskUseCase(
                OpenRouterInferenceAdapter(registry, credentialStore, transport, OpenRouterEgressPolicy.Disabled, clock = clock),
                invocationRepository,
                clock,
            ).execute(taskAndDraft.first, taskAndDraft.second)
            val status = when (blocked) {
                is AiTaskRunResult.Success -> blocked.invocation.status
                is AiTaskRunResult.Failure -> blocked.invocation.status
            }
            return finish(P2MRealServiceExecutionResult.Blocked(
                report,
                "DryRun 未通过；安全 Ledger=$status，没有创建 Provider Attempt。",
            ), spec)
        }

        val nonce = P2MOpenRouterTextAcceptance.nonce()
        val issued = tokenService.issue(spec, consent, clock.instant().plusSeconds(300), nonce)
            ?: return finish(P2MRealServiceExecutionResult.Blocked(report, "该 RunSpec 的一次性 nonce 已发行或已消费；未创建 Provider Attempt。"), spec)
        if (tokenService.consume(spec, issued.nonce) != RealServiceTokenConsumeResult.Consumed) {
            return finish(P2MRealServiceExecutionResult.Blocked(report, "一次性 nonce 未能原子消费；未创建 Provider Attempt。"), spec)
        }

        val task = taskAndDraft.first
        val draft = taskAndDraft.second
        val result = RunAiTaskUseCase(
            OpenRouterInferenceAdapter(
                registry = registry,
                credentialStore = credentialStore,
                transport = transport,
                egressPolicy = OpenRouterEgressPolicy.ExactSingleUseRun(spec.fingerprint()),
                clock = clock,
                maxAttempts = spec.retryCount + 1,
                credentialValidator = ::isP2MNonPlaceholderCredential,
            ),
            invocationRepository,
            clock,
        ).execute(task, draft)
        val binding = if (result is AiTaskRunResult.Success) {
            persistCandidate.execute(result.candidate, result.invocation, task, draft)
            transcriptOwner.bindSuccessfulInvocation(result.invocation)
        } else P2MTranscriptBindingResult.Ignored("非成功终态不创建 transcript owner。")
        return finish(P2MRealServiceExecutionResult.Completed(report, issued.nonce, result, binding), spec)
    }

    /** Restart-safe repair for a previously completed local ledger; it cannot create egress. */
    fun ensureSuccessfulTranscriptBinding(): P2MTranscriptBindingResult? {
        val invocation = invocationRepository.listNewestFirst().firstOrNull {
            P2MOpenRouterTextAcceptance.isKnownTaskId(it.taskId.value)
        }
            ?: return null
        return transcriptOwner.bindSuccessfulInvocation(invocation)
    }

    fun readPersistedSummary(): P2MRealServiceRunSummary? {
        val invocation = invocationRepository.listNewestFirst().firstOrNull {
            P2MOpenRouterTextAcceptance.isKnownTaskId(it.taskId.value)
        } ?: return null
        val duration = (invocation.taskRun.completedAt.toEpochMilli() - invocation.taskRun.startedAt.toEpochMilli()).takeIf { it > 0L }
        return P2MRealServiceRunSummary(
            providerLabel = "OpenRouter", presetLabel = "已保存预设", modelLabel = invocation.modelId,
            terminalLabel = invocation.status.name, durationMillis = duration,
            inputTokens = invocation.usage.inputTokens, outputTokens = invocation.usage.outputTokens,
            costMicros = invocation.cost.totalMicros, currencyCode = invocation.cost.currencyCode,
            errorCode = invocation.error?.javaClass?.simpleName,
        )
    }

    private fun isP2MNonPlaceholderCredential(value: CharArray): Boolean {
        fun containsAscii(marker: String): Boolean = (0..value.size - marker.length).any { start ->
            marker.indices.all { offset -> value[start + offset].lowercaseChar() == marker[offset] }
        }
        return value.size >= 16 && listOf("test-key", "not-real", "placeholder", "replace-me").none(::containsAscii)
    }

    private fun finish(result: P2MRealServiceExecutionResult, spec: RealServiceRunSpec): P2MRealServiceExecutionResult {
        val directory = evidenceDirectory ?: return result
        runCatching {
            require(directory.exists() || directory.mkdirs())
            val report = when (result) {
                is P2MRealServiceExecutionResult.NotReady -> return result
                is P2MRealServiceExecutionResult.Blocked -> result.report
                is P2MRealServiceExecutionResult.Completed -> result.report
            }
            val terminal = when (result) {
                is P2MRealServiceExecutionResult.NotReady -> return result
                is P2MRealServiceExecutionResult.Blocked -> listOf("terminal=BLOCKED", "reason=${result.reason}")
                is P2MRealServiceExecutionResult.Completed -> {
                    val invocation = when (val taskResult = result.taskResult) {
                        is AiTaskRunResult.Success -> taskResult.invocation
                        is AiTaskRunResult.Failure -> taskResult.invocation
                    }
                    listOf(
                        "terminal=${invocation.status}",
                        "errorCode=${invocation.error?.javaClass?.simpleName ?: "none"}",
                        "attempts=${invocation.taskRun.attempts.size}",
                        "inputTokens=${invocation.usage.inputTokens ?: "unknown"}",
                        "outputTokens=${invocation.usage.outputTokens ?: "unknown"}",
                        "costMicros=${invocation.cost.totalMicros ?: "unknown"}",
                        "currency=${invocation.cost.currencyCode ?: "unknown"}",
                        "durationMillis=${invocation.taskRun.completedAt.toEpochMilli() - invocation.taskRun.startedAt.toEpochMilli()}",
                        "transcriptOwner=${when (result.transcriptBinding) {
                            is P2MTranscriptBindingResult.Bound -> "BOUND"
                            is P2MTranscriptBindingResult.Ignored -> "NOT_ELIGIBLE"
                            is P2MTranscriptBindingResult.Rejected -> "NOT_SAVED"
                        }}",
                        "nonce=${result.nonce}",
                    )
                }
            }
            val text = buildList {
                add("contract=${spec.contractId}:v${spec.contractVersion}")
                add("runSpecFingerprint=${spec.fingerprint()}")
                add("provider=${spec.providerId}")
                add("model=${spec.modelId}")
                add("registrySnapshot=${spec.registrySnapshotId}")
                add("catalogSha256=${spec.registryCatalogSha256}")
                add("fixture=${spec.fixture.id}:${spec.fixture.sha256}")
                add("dryRunPassed=${report.passed}")
                add("dryRunCostState=${report.costEstimateState}")
                add("dryRunEstimatedMicros=${report.estimatedCostMicros ?: "unknown"}")
                add("dryRunIssues=${report.issues.joinToString(",")}")
                add("authorizationConstructed=${report.authorizationConstructed}")
                add("networkRequestsConstructed=${report.networkRequestsConstructed}")
                add("credentialBytesReadDuringDryRun=${report.credentialBytesRead}")
                addAll(terminal)
            }.joinToString("\n", postfix = "\n")
            val file = File(directory, "p2m-real-text-evidence.txt")
            val temporary = File(directory, "${file.name}.tmp")
            FileOutputStream(temporary).use { output -> output.write(text.toByteArray()); output.fd.sync() }
            if (!temporary.renameTo(file)) temporary.delete()
        }
        return result
    }

    private fun taskAndDraft(spec: RealServiceRunSpec): Pair<AiTask, CaptureDraft> {
        val now = clock.instant()
        val model = registry.currentSnapshot(ProviderId.OPENROUTER)?.models?.firstOrNull { it.id == spec.modelId }
            ?: ModelDescriptor(
                id = spec.modelId,
                displayName = spec.modelId,
                capabilities = ModelCapabilities(true, false, false, true),
                pricing = ModelPricing(null, "USD", null, null),
            )
        val draft = CaptureDraft(
            id = CaptureDraftId("p2m-text-fixture-draft-v2"),
            text = requireNotNull(P2MSyntheticFixtures.textContent(spec.fixture.id)),
            attachments = emptyList(),
            sourceEvidence = listOf(SourceEvidence(CaptureSourceType.MANUAL_TEXT, now, contributedFields = setOf("text"))),
            createdAt = now,
        )
        val disclosure = CostDisclosure(now, model.pricing.priceVersion, model.pricing.currencyCode, null)
        return AiTask(
            id = AiTaskId(P2MOpenRouterTextAcceptance.TASK_ID), draftId = draft.id, providerId = ProviderId.OPENROUTER,
            model = model, harness = HarnessProfile("p2m-text-organize", 2),
            consent = EgressConsent(now, ProviderId.OPENROUTER, model.id, draft.egressFingerprint(), disclosure), createdAt = now,
        ) to draft
    }
}

private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
    .joinToString("") { "%02x".format(it) }

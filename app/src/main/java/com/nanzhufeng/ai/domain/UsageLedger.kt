package com.nanzhufeng.ai.domain

import java.time.Instant
import java.util.UUID

/**
 * P0 local-only Usage Ledger protocol. It records quantitative, secret-free facts only; it has
 * no transport, credential, prompt, response, attachment or UI capability.
 */
const val USAGE_LEDGER_PROTOCOL = "usage-ledger-v1"

@JvmInline
value class UsageLedgerEntryId(val value: String) {
    companion object { fun new() = UsageLedgerEntryId(UUID.randomUUID().toString()) }
}

enum class UsageFactGrade { ESTIMATED, PROVIDER_REPORTED, RECONCILED }

enum class UsageLedgerKind {
    STREAM_PENDING,
    FINAL_MEASURED,
    RECONCILIATION_ADJUSTMENT,
    BUDGET_RESERVATION,
    BUDGET_RELEASE,
}

/** The origin is metadata, not a URI, device path, account identity, or synchronization command. */
enum class UsageLedgerSource { ANDROID_LOCAL, DESKTOP_LOCAL, CROSS_PLATFORM_IMPORT }

/**
 * An immutable fact. Monetary values are integer micros in [currencyCode]; nullable quantities
 * mean the source did not report that dimension. [reconciliationFingerprint] is a SHA-256 link,
 * never a request/response payload.
 */
data class UsageLedgerEntry(
    val entryId: UsageLedgerEntryId,
    val replayToken: String,
    val executionId: ConversationRealTextExecutionId,
    val conversationId: ConversationId,
    val branchLeafMessageId: MessageNodeId,
    val invocationId: InvocationId,
    val attemptId: ProviderAttemptId,
    val kind: UsageLedgerKind,
    val factGrade: UsageFactGrade,
    val requestedModelId: String,
    val actualModelId: String?,
    val inputTokens: Long?,
    val outputTokens: Long?,
    val cachedInputTokens: Long?,
    val chargeMicros: Long?,
    val budgetMicros: Long?,
    val adjustmentMicros: Long?,
    val currencyCode: String?,
    val reconciliationFingerprint: String?,
    val reconcilesEntryId: UsageLedgerEntryId?,
    val source: UsageLedgerSource,
    val occurredAt: Instant,
) {
    init {
        require(replayToken.matches(Regex("[A-Za-z0-9._:-]{1,160}"))) { "账本重放标识不合法。" }
        require(requestedModelId.matches(Regex("[A-Za-z0-9._:/-]{1,200}"))) { "请求模型标识不合法。" }
        require(actualModelId == null || actualModelId.matches(Regex("[A-Za-z0-9._:/-]{1,200}"))) { "实际模型标识不合法。" }
        require(currencyCode == null || currencyCode.matches(Regex("[A-Z]{3}"))) { "币种必须是 ISO 大写三字码。" }
        require(reconciliationFingerprint == null || reconciliationFingerprint.matches(Regex("[0-9a-f]{64}"))) { "对账指纹必须是小写 SHA-256。" }
        require(listOfNotNull(inputTokens, outputTokens, cachedInputTokens, chargeMicros, budgetMicros).all { it >= 0 }) {
            "计量与预算数量不能为负。"
        }
        require(currencyCode != null || listOfNotNull(chargeMicros, budgetMicros, adjustmentMicros).isEmpty()) { "金额事实必须有币种。" }
        when (kind) {
            UsageLedgerKind.STREAM_PENDING -> {
                require(factGrade == UsageFactGrade.ESTIMATED && reconciliationFingerprint != null && reconcilesEntryId == null) {
                    "流式 pending 必须是带对账指纹的估算事实。"
                }
            }
            UsageLedgerKind.RECONCILIATION_ADJUSTMENT -> {
                require(factGrade == UsageFactGrade.RECONCILED && reconciliationFingerprint != null && reconcilesEntryId != null && reconcilesEntryId != entryId) {
                    "对账调整必须链接另一条 pending 事实。"
                }
            }
            UsageLedgerKind.FINAL_MEASURED -> require(factGrade != UsageFactGrade.ESTIMATED) { "最终计量不能标为估算。" }
            UsageLedgerKind.BUDGET_RESERVATION, UsageLedgerKind.BUDGET_RELEASE -> require(budgetMicros != null) { "预算事实必须包含预算数量。" }
        }
    }
}

sealed interface UsageLedgerAppendResult {
    data class Appended(val entry: UsageLedgerEntry) : UsageLedgerAppendResult
    data class Replayed(val entry: UsageLedgerEntry) : UsageLedgerAppendResult
    data class Conflict(val reason: String) : UsageLedgerAppendResult
}

/** Derived locally from immutable entries; it is deliberately not a mutable persisted summary. */
data class UsageLedgerReadModel(
    val entries: List<UsageLedgerEntry>,
    val pendingStreamEntries: List<UsageLedgerEntry>,
    val requestedModelIds: Set<String>,
    val actualModelIds: Set<String>,
    val inputTokens: Long,
    val outputTokens: Long,
    val cachedInputTokens: Long,
    val chargeMicros: Long,
    val reservedBudgetMicros: Long,
    val releasedBudgetMicros: Long,
    val reconciliationAdjustmentMicros: Long,
)

object UsageLedgerReadModelFactory {
    fun create(entries: List<UsageLedgerEntry>): UsageLedgerReadModel {
        val ordered = entries.sortedWith(compareBy<UsageLedgerEntry> { it.occurredAt }.thenBy { it.entryId.value })
        val reconciled = ordered.mapNotNull { it.reconcilesEntryId }.toSet()
        val measured = ordered.filter { it.kind != UsageLedgerKind.STREAM_PENDING }
        return UsageLedgerReadModel(
            entries = ordered,
            pendingStreamEntries = ordered.filter { it.kind == UsageLedgerKind.STREAM_PENDING && it.entryId !in reconciled },
            requestedModelIds = ordered.map { it.requestedModelId }.toSet(),
            actualModelIds = ordered.mapNotNull { it.actualModelId }.toSet(),
            inputTokens = measured.sumOf { it.inputTokens ?: 0L },
            outputTokens = measured.sumOf { it.outputTokens ?: 0L },
            cachedInputTokens = measured.sumOf { it.cachedInputTokens ?: 0L },
            chargeMicros = measured.sumOf { it.chargeMicros ?: 0L },
            reservedBudgetMicros = ordered.filter { it.kind == UsageLedgerKind.BUDGET_RESERVATION }.sumOf { it.budgetMicros ?: 0L },
            releasedBudgetMicros = ordered.filter { it.kind == UsageLedgerKind.BUDGET_RELEASE }.sumOf { it.budgetMicros ?: 0L },
            reconciliationAdjustmentMicros = ordered.filter { it.kind == UsageLedgerKind.RECONCILIATION_ADJUSTMENT }.sumOf { it.adjustmentMicros ?: 0L },
        )
    }
}

/** Append/read boundary only. No caller is registered in the production app in this P0 increment. */
interface UsageLedgerRepository {
    fun append(entry: UsageLedgerEntry): UsageLedgerAppendResult
    fun entriesForExecution(executionId: ConversationRealTextExecutionId): List<UsageLedgerEntry>
    fun readModelForExecution(executionId: ConversationRealTextExecutionId): UsageLedgerReadModel
}

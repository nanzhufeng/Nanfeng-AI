package com.nanzhufeng.ai.domain

import java.time.Instant

/** One physical request's content-free quantitative facts; never a prompt or response archive. */
data class ChatCallAccounting(
    val callId: String,
    val rootAttemptId: String?,
    val conversationId: String?,
    val role: String,
    val providerId: ProviderId,
    val requestedModelId: String,
    val actualModelId: String?,
    val modelDisplayName: String,
    val requestedAt: Instant,
    val finishedAt: Instant?,
    val status: String,
    val usage: ProviderUsage = ProviderUsage(),
    val cost: ProviderCost = ProviderCost(),
    val costSource: ConversationCostSource? = null,
    val contextWindowTokens: Long? = null,
    val contextSourceCount: Int? = null,
    val serviceId:String?=null,
    val serviceEngineId:String?=null,
) {
    init {
        require(callId.matches(Regex("[A-Za-z0-9._:-]{1,180}")))
        require(role in setOf("ANSWER","RETRIEVAL","BACKGROUND"))
        require(status.matches(Regex("[A-Z0-9_]{1,100}")))
        require(if(serviceId==null) requestedModelId.length in 1..200 && requestedModelId.none(Char::isISOControl)
            else serviceId==IndependentSearchServiceGrant.SERVICE_ID && serviceEngineId==IndependentSearchServiceGrant.ENGINE_ID && role=="RETRIEVAL" && providerId==ProviderId.ZHIPU && requestedModelId.isEmpty() && actualModelId==null)
        require(actualModelId == null || actualModelId.length in 1..200 && actualModelId.none(Char::isISOControl))
        require((cost.totalMicros == null) == (costSource == null))
        require(contextSourceCount == null || contextSourceCount >= 0)
        require(contextWindowTokens == null || contextWindowTokens > 0)
    }
}

/** Namespaces prevent an OpenRouter bare response ID from selecting a discounted Relay tariff. */
fun accountingPricingModelId(provider:ProviderId,requested:String,actual:String?):String =
    if(actual==null || actual==requested) requested else if(provider==ProviderId.OPENROUTER && "/" !in actual && "/" in requested) requested.substringBefore('/')+"/"+actual else actual

/** Read-only correction: the old Relay estimate's dollar-style numbers are nominal RMB. */
fun ProviderCost.withRelayNominalCurrency(): ProviderCost =
    if (currencyCode == "USD" && priceVersion?.startsWith("relaycore-verified-group-") == true)
        copy(currencyCode = "CNY") else this

fun combinedAccountingCost(calls: List<ChatCallAccounting>): Pair<ProviderCost,ConversationCostSource?> {
    val costs = calls.map { it.cost.withRelayNominalCurrency() }
    if (costs.isEmpty() || costs.any { it.totalMicros == null } || costs.map { it.currencyCode }.distinct().size != 1) return ProviderCost() to null
    val total = runCatching { costs.fold(0L) { sum,cost -> Math.addExact(sum,requireNotNull(cost.totalMicros)) } }.getOrNull() ?: return ProviderCost() to null
    val source = if (calls.any { it.costSource == ConversationCostSource.LOCAL_ESTIMATE }) ConversationCostSource.LOCAL_ESTIMATE else ConversationCostSource.PROVIDER_RESPONSE
    return ProviderCost("combined-call-accounting-v1",costs.first().currencyCode,total) to source
}

/** Ledger projection only; these rows are never written as additional assistant messages. */
fun AssistantResponseModelAttribution.physicalCostRows(): List<AssistantResponseModelAttribution> =
    if (accountingCalls.isEmpty()) listOf(this) else accountingCalls.map { call ->
        copy(attemptId=NormalChatSendAttemptId(call.callId),providerId=call.providerId,receiverProviderId=call.providerId,
            modelId=call.actualModelId ?: call.requestedModelId,modelDisplayName=call.modelDisplayName,recordedAt=call.requestedAt,
            usage=call.usage,cost=call.cost.withRelayNominalCurrency(),costSource=call.costSource,accountingCalls=emptyList(),
            physicalCallId=call.callId,physicalCallRole=call.role,physicalCallStatus=call.status,
            physicalActualModelId=call.actualModelId,physicalContextWindowTokens=call.contextWindowTokens,physicalContextSourceCount=call.contextSourceCount,
            modelDurationMillis=call.finishedAt?.let { java.time.Duration.between(call.requestedAt,it).toMillis().takeIf { value -> value>0 } })
    }

fun mergePhysicalCallCostRecords(answers:List<AssistantResponseModelAttribution>,calls:List<ChatCallAccounting>):List<AssistantResponseModelAttribution> {
    val rows=answers.flatMap { it.physicalCostRows() }.toMutableList()
    val seen=rows.mapNotNull { it.physicalCallId }.toMutableSet()
    calls.filter { it.role!="BACKGROUND" && seen.add(it.callId) }.forEach { call ->
        rows+=AssistantResponseModelAttribution(MessageNodeId("accounting:${call.callId}"),NormalChatSendAttemptId(call.callId),call.providerId,call.providerId,
            call.actualModelId ?: call.requestedModelId,call.modelDisplayName,call.requestedAt,usage=call.usage,cost=call.cost.withRelayNominalCurrency(),costSource=call.costSource,
            modelDurationMillis=call.finishedAt?.let { java.time.Duration.between(call.requestedAt,it).toMillis().takeIf { value -> value>0 } },
            physicalCallId=call.callId,physicalCallRole=call.role,physicalCallStatus=call.status,physicalActualModelId=call.actualModelId,
            physicalContextWindowTokens=call.contextWindowTokens,physicalContextSourceCount=call.contextSourceCount)
    }
    return rows.sortedByDescending { it.recordedAt }
}

/** User-facing fee rows; the physical call ledger remains immutable. */
fun mergeConversationCostRecords(answers: List<AssistantResponseModelAttribution>, calls: List<ChatCallAccounting>): List<AssistantResponseModelAttribution> {
    val facts = (answers.flatMap { it.accountingCalls } + calls).associateBy { it.callId }
    val rows = mergePhysicalCallCostRecords(answers, calls)
    // A root identity is mandatory. Never infer a question from timestamps or a conversation alone.
    val groups = rows.groupBy { row ->
        val call = facts[row.physicalCallId]
        if (call?.role == "RETRIEVAL" && !call.rootAttemptId.isNullOrBlank() && !call.conversationId.isNullOrBlank())
            listOf(call.conversationId, call.rootAttemptId, call.providerId.name, call.requestedModelId,
                call.modelDisplayName, call.serviceId, call.serviceEngineId)
        else if (row.physicalCallId != null) listOf("physical", row.physicalCallId)
        else listOf("legacy", row.assistantMessageId.value, row.attemptId.value)
    }
    return groups.values.map { members ->
        if (members.size == 1 || members.first().physicalCallRole != "RETRIEVAL") return@map members.first()
        val groupedCalls = members.map { requireNotNull(facts[it.physicalCallId]) }.distinctBy { it.callId }
        val (cost, source) = combinedAccountingCost(groupedCalls)
        fun sum(values: List<Long?>): Long? = if (values.any { it == null }) null else
            runCatching { values.fold(0L) { total, value -> Math.addExact(total, requireNotNull(value)) } }.getOrNull()
        members.first().copy(
            recordedAt = groupedCalls.minOf { it.requestedAt },
            accountingCalls = groupedCalls, cost = cost, costSource = source,
            usage = ProviderUsage(
                sum(groupedCalls.map { it.usage.inputTokens }), sum(groupedCalls.map { it.usage.outputTokens }),
                sum(groupedCalls.map { it.usage.totalTokens }), sum(groupedCalls.map { it.usage.cachedInputTokens }),
                sum(groupedCalls.map { it.usage.reasoningTokens }),
            ),
            modelDurationMillis = sum(members.map { it.modelDurationMillis })?.takeIf { it > 0 },
            physicalCallStatus = groupedCalls.map { it.status }.distinct().joinToString(" / "),
            physicalActualModelId = groupedCalls.map { it.actualModelId }.distinct().singleOrNull(),
            physicalContextWindowTokens = null, physicalContextSourceCount = null,
        )
    }.sortedByDescending { it.recordedAt }
}

/** All original amounts, including the known members of a partially unknown display group. */
fun AssistantResponseModelAttribution.ledgerCostFacts(): List<ProviderCost> =
    if (accountingCalls.isEmpty()) listOf(cost.withRelayNominalCurrency())
    else accountingCalls.distinctBy { it.callId }.map { it.cost.withRelayNominalCurrency() }

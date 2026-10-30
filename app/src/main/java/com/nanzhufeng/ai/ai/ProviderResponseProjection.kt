package com.nanzhufeng.ai.ai

/** Preserve received facts; this projection does not validate completion or authorize reuse. */
internal fun ProviderChatOutcome.StreamedResponse.decodedText() = ChatAdapterDecodedResult.Text(
    text = text,
    inputTokens = inputTokens,
    outputTokens = outputTokens,
    reasoning = reasoning,
    toolCalls = toolCalls,
    toolCallEncountered = toolCallEncountered,
    webSources = webSources,
    reportedProviderCost = reportedProviderCost,
    cachedInputTokens = cachedInputTokens,
    reasoningTokens = reasoningTokens,
    webSearchPerformed = webSearchPerformed,
    returnedModelId = returnedModelId,
)

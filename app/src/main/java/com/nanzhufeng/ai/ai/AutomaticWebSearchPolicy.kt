package com.nanzhufeng.ai.ai

import com.nanzhufeng.ai.domain.ProviderId

/**
 * Resolves the official provider route for the current conversation's explicit web-search state.
 * It never reads credentials or contacts a provider; the selected adapter remains the sole
 * network owner. Once the user enables search, every ordinary request asks the provider to
 * search; completion still records separately whether the provider returned usable evidence.
 */
internal object AutomaticWebSearchPolicy {
    @Suppress("UNUSED_PARAMETER")
    fun requestOptions(
        providerId: ProviderId,
        current: ChatRequestOptions,
        enabled: Boolean,
        userMessage: String,
        attachments: List<ChatAttachment>,
        modelId: String? = null,
    ): ChatRequestOptions {
        if (!enabled) return current.copy(webSearchRoute = OfficialWebSearchRoute.NONE,openRouterResponsesSearch=false)
        if(providerId==ProviderId.OPENROUTER && modelId=="meta/muse-wonder" && attachments.all {it.kind==ChatAttachmentKind.FILE})
            return current.copy(webSearchRoute=OfficialWebSearchRoute.OPENROUTER_SERVER_TOOL,openRouterResponsesSearch=true)
        if (attachments.isNotEmpty() && providerId in com.nanzhufeng.ai.domain.RelayCoreModels.providers)
            return current.copy(webSearchRoute = OfficialWebSearchRoute.RELAYCORE_COMPANION)
        if (current.liveWebSearch) return current
        return when (providerId) {
            ProviderId.OPENROUTER -> current.copy(webSearchRoute = OfficialWebSearchRoute.OPENROUTER_SERVER_TOOL)
            // Qwen Chat Completions does not expose search sources. Responses is the
            // verified text search route; Max explicitly enables low-effort thinking.
            ProviderId.QWEN -> current.copy(
                webSearchRoute = OfficialWebSearchRoute.QWEN_RESPONSES,
            )
            // Text files and readable PDF layers stay local before these text-only search routes.
            // Media without an approved text projection must first use the explicit transcription flow.
            // An attachment is not permission to
            // silently remove the user's explicit web-search requirement.
            ProviderId.DEEPSEEK -> current.copy(webSearchRoute = OfficialWebSearchRoute.DEEPSEEK_MESSAGES)
            ProviderId.ZHIPU -> current.copy(webSearchRoute = OfficialWebSearchRoute.ZHIPU_CHAT_COMPLETIONS)
            ProviderId.RELAYCORE_GPT -> current.copy(webSearchRoute = OfficialWebSearchRoute.RELAYCORE_GPT_RESPONSES)
            ProviderId.RELAYCORE_CLAUDE -> current.copy(webSearchRoute = if(com.nanzhufeng.ai.domain.RelayCoreModels.hasNativeClaudeSearch(modelId)) OfficialWebSearchRoute.RELAYCORE_CLAUDE_MESSAGES else OfficialWebSearchRoute.RELAYCORE_COMPANION)
            ProviderId.RELAYCORE_GEMINI -> current.copy(webSearchRoute = OfficialWebSearchRoute.RELAYCORE_COMPANION)
            ProviderId.MOCK -> current
        }
    }
}

/** An explicit search request requires provider-owned execution evidence. */
internal object WebSearchGroundingPolicy {
    const val ENABLED_INSTRUCTION = "本轮已启用实时网页搜索。以联网开关为准，必须实际执行搜索，不按问题关键词判断是否联网。若本请求已附服务端实际搜索资料则据此作答；否则在回答前调用已提供的搜索工具。只查询与问题或图片相关的最小公开关键词，不搜索私人信息。引用实际返回来源；不得把模型记忆或自行编写的网址当作搜索结果。工具失败或实际结果为空时如实说明。"
    fun missingExecutionEvidence(options: ChatRequestOptions, sources: List<ProviderWebSource>, performed: Boolean): Boolean =
        options.liveWebSearch && !performed && sources.none { ProviderWebSource.isValidPublicHttpUrl(it.url) }

    fun completedAuditStatus(options: ChatRequestOptions, sources: List<ProviderWebSource>, streamed: Boolean, performed: Boolean = false): String = when {
        !options.liveWebSearch -> if (streamed) "STREAM_SUCCEEDED" else "SUCCEEDED"
        sources.any { ProviderWebSource.isValidPublicHttpUrl(it.url) } ->
            if (streamed) "WEB_SEARCH_STREAM_SUCCEEDED_WITH_SOURCES" else "WEB_SEARCH_SUCCEEDED_WITH_SOURCES"
        performed -> "WEB_SEARCH_SUCCEEDED"
        else -> "WEB_SEARCH_ANSWER_UNVERIFIED"
    }
}

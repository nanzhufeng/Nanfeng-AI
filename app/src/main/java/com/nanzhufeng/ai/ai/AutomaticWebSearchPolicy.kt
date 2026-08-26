package com.nanzhufeng.ai.ai

import com.nanzhufeng.ai.domain.ProviderId

/**
 * Decides locally whether an ordinary question needs current public information. It never reads
 * credentials or contacts a provider; the selected adapter remains the sole network owner.
 */
internal object AutomaticWebSearchPolicy {
    private val currentInformationRequest = Regex(
        "最新|最近|今天|今日|当前|实时|新闻|动态|价格|股价|汇率|行情|财报|业绩|政策|法规|发布|联网|网页|网络|网上|在线搜索|web\\s*search|internet\\s*search|latest|recent|today|current|real.?time|news|price|stock|exchange rate|earnings",
        RegexOption.IGNORE_CASE,
    )

    fun requestOptions(
        providerId: ProviderId,
        current: ChatRequestOptions,
        enabled: Boolean,
        userMessage: String,
        attachments: List<ChatAttachment>,
    ): ChatRequestOptions {
        if (!enabled) return ChatRequestOptions.Standard
        if (current.liveWebSearch || !currentInformationRequest.containsMatchIn(userMessage)) return current
        return when (providerId) {
            ProviderId.OPENROUTER -> ChatRequestOptions(OfficialWebSearchRoute.OPENROUTER_SERVER_TOOL)
            ProviderId.QWEN -> ChatRequestOptions(OfficialWebSearchRoute.QWEN_CHAT_COMPLETIONS)
            ProviderId.DEEPSEEK -> if (attachments.isEmpty()) {
                ChatRequestOptions(OfficialWebSearchRoute.DEEPSEEK_RESPONSES)
            } else {
                current
            }
            ProviderId.MOCK -> current
        }
    }
}

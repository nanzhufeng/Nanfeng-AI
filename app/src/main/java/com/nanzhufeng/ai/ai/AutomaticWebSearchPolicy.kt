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
    private val investmentDecisionRequest = Regex(
        "(?=.*(?:股票|基金|etf|a股|美股|沪深|中证|指数|宽基|股息))(?=.*(?:买入|卖出|选哪个|配置|仓位|估值|建仓|加仓|减仓|回撤))",
        RegexOption.IGNORE_CASE,
    )

    fun requestOptions(
        providerId: ProviderId,
        current: ChatRequestOptions,
        enabled: Boolean,
        userMessage: String,
        attachments: List<ChatAttachment>,
        modelId: String? = null,
    ): ChatRequestOptions {
        if (!enabled) return ChatRequestOptions.Standard
        val needsCurrentInformation = currentInformationRequest.containsMatchIn(userMessage) || investmentDecisionRequest.containsMatchIn(userMessage)
        if (current.liveWebSearch || !needsCurrentInformation) return current
        return when (providerId) {
            ProviderId.OPENROUTER -> ChatRequestOptions(OfficialWebSearchRoute.OPENROUTER_SERVER_TOOL)
            // Qwen3.8-Max Responses has repeatedly kept emitting search/tool progress without a
            // final answer until the five-minute hard deadline. Its official Chat Completions
            // search route is bounded to three minutes and already buffers provider tool frames.
            // Other text-only Qwen models retain Responses; attachments still require Chat.
            ProviderId.QWEN -> ChatRequestOptions(
                if (attachments.isEmpty() && modelId != QWEN_3_8_MAX_MODEL_ID) OfficialWebSearchRoute.QWEN_RESPONSES
                else OfficialWebSearchRoute.QWEN_CHAT_COMPLETIONS,
            )
            ProviderId.DEEPSEEK -> if (attachments.isEmpty()) {
                ChatRequestOptions(OfficialWebSearchRoute.DEEPSEEK_RESPONSES)
            } else {
                current
            }
            ProviderId.ZHIPU -> if (attachments.isEmpty()) {
                ChatRequestOptions(OfficialWebSearchRoute.ZHIPU_CHAT_COMPLETIONS)
            } else {
                current
            }
            ProviderId.MOCK -> current
        }
    }
}

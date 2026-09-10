package com.nanzhufeng.ai.ai

import com.nanzhufeng.ai.domain.ProviderId

/**
 * Resolves the official provider route for the current conversation's explicit web-search state.
 * It never reads credentials or contacts a provider; the selected adapter remains the sole
 * network owner. Once the user enables search, every ordinary request is grounded instead of
 * guessing from a small keyword list whether this particular prompt or attachment needs it.
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
        if (!enabled) return current.copy(webSearchRoute = OfficialWebSearchRoute.NONE)
        if (current.liveWebSearch) return current
        return when (providerId) {
            ProviderId.OPENROUTER -> current.copy(webSearchRoute = OfficialWebSearchRoute.OPENROUTER_SERVER_TOOL)
            // Qwen3.8-Max Responses has repeatedly kept emitting search/tool progress without a
            // final answer until the five-minute hard deadline. Its official Chat Completions
            // search route is bounded to three minutes and already buffers provider tool frames.
            // Other text-only Qwen models retain Responses; attachments still require Chat.
            ProviderId.QWEN -> current.copy(
                webSearchRoute = if (attachments.isEmpty() && modelId != QWEN_3_8_MAX_MODEL_ID) OfficialWebSearchRoute.QWEN_RESPONSES
                else OfficialWebSearchRoute.QWEN_CHAT_COMPLETIONS,
            )
            // The shared attachment bridge projects Markdown/OCR/PDF/media to text before these
            // text-only official search routes are serialized. An attachment is not permission to
            // silently remove the user's explicit web-search requirement.
            ProviderId.DEEPSEEK -> current.copy(webSearchRoute = OfficialWebSearchRoute.DEEPSEEK_RESPONSES)
            ProviderId.ZHIPU -> current.copy(webSearchRoute = OfficialWebSearchRoute.ZHIPU_CHAT_COMPLETIONS)
            ProviderId.MOCK -> current
        }
    }
}

/** A live-search answer is complete only when the provider returned public source metadata. */
internal object WebSearchGroundingPolicy {
    fun hasRequiredSources(options: ChatRequestOptions, sources: List<ProviderWebSource>): Boolean =
        !options.liveWebSearch || sources.any { ProviderWebSource.isValidPublicHttpUrl(it.url) }
}

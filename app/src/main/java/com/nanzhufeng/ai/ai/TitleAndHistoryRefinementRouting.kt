package com.nanzhufeng.ai.ai

import com.nanzhufeng.ai.domain.ModelPresetId
import com.nanzhufeng.ai.domain.ProviderId
import com.nanzhufeng.ai.domain.ComposerModelRoutingCatalog
import com.nanzhufeng.ai.domain.NanfengModelServiceCatalog

/**
 * History-library refinement retains its existing product-owned order.
 * Keep this separate from normal chat and Auto routing: changing those user-facing routes must
 * not silently change background calls, and vice versa.
 */
internal object TitleAndHistoryRefinementRouting {
    val candidates = listOf(
        Candidate(
            NanfengModelServiceCatalog.providerFor(ComposerModelRoutingCatalog.dailyDeepSeekFlash.routes.single()),
            ComposerModelRoutingCatalog.dailyDeepSeekFlash.routes.single(),
        ),
        Candidate(ProviderId.ZHIPU, ModelPresetId.GLM_5_3_FLASH),
        Candidate(ProviderId.QWEN, ModelPresetId.QWEN_3_6_FLASH),
    )

    data class Candidate(val providerId: ProviderId, val preset: ModelPresetId)
}

/** Automatic titles are short labels, independent of long-form history synthesis. */
internal object ConversationTitleRouting {
    val candidates = listOf(
        TitleAndHistoryRefinementRouting.Candidate(ProviderId.QWEN, ModelPresetId.QWEN_3_6_FLASH),
        TitleAndHistoryRefinementRouting.Candidate(ProviderId.DEEPSEEK, ModelPresetId.DEEPSEEK_V4_FLASH),
    )
    const val MAX_OUTPUT_TOKENS = 256L
    const val READ_TIMEOUT_MILLIS = 15_000
}

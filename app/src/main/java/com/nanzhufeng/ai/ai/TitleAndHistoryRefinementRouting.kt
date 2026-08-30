package com.nanzhufeng.ai.ai

import com.nanzhufeng.ai.domain.ModelPresetId
import com.nanzhufeng.ai.domain.ProviderId

/**
 * The two low-cost background text refiners deliberately share one product-owned order.
 * Keep this separate from normal chat and Auto routing: changing those user-facing routes must
 * not silently change title or history-library calls, and vice versa.
 */
internal object TitleAndHistoryRefinementRouting {
    val candidates = listOf(
        Candidate(ProviderId.DEEPSEEK, ModelPresetId.DEEPSEEK_V4_FLASH),
        Candidate(ProviderId.ZHIPU, ModelPresetId.GLM_5_3_FLASH),
        Candidate(ProviderId.QWEN, ModelPresetId.QWEN_3_6_FLASH),
    )

    data class Candidate(val providerId: ProviderId, val preset: ModelPresetId)
}

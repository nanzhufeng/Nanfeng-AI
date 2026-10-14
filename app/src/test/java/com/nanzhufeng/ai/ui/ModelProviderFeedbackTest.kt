package com.nanzhufeng.ai.ui

import com.nanzhufeng.ai.domain.ProviderId
import org.junit.Assert.*
import org.junit.Test

class ModelProviderFeedbackTest {
    @Test fun `save and connection feedback are isolated between all three Relay profiles`() {
        val state = ModelSettingsUiState(providerFeedback = mapOf(
            ProviderId.RELAYCORE_CLAUDE to ModelProviderFeedback("API Key 已安全保存在本机。"),
            ProviderId.RELAYCORE_GPT to ModelProviderFeedback(error = CaptureUiError("连接测试失败", "未建立连接")),
        ))
        assertEquals("API Key 已安全保存在本机。", state.feedbackFor(ProviderId.RELAYCORE_CLAUDE).notice)
        assertNull(state.feedbackFor(ProviderId.RELAYCORE_CLAUDE).error)
        assertNull(state.feedbackFor(ProviderId.RELAYCORE_GPT).notice)
        assertEquals("连接测试失败", state.feedbackFor(ProviderId.RELAYCORE_GPT).error?.title)
        assertNull(state.feedbackFor(ProviderId.RELAYCORE_GEMINI).notice)
        assertNull(state.feedbackFor(ProviderId.RELAYCORE_GEMINI).error)
        assertNull(state.feedbackFor(ProviderId.OPENROUTER).notice)
    }
}

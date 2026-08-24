package com.nanzhufeng.ai.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class P2MVisibleConfirmationContractsTest {
    private val ui = File("src/main/java/com/nanzhufeng/ai/ui/ModelSettingsUi.kt").readText()
    private val owner = File("src/main/java/com/nanzhufeng/ai/ui/ModelSettingsViewModel.kt").readText()
    private val app = File("src/main/java/com/nanzhufeng/ai/ui/NanfengAiApp.kt").readText()

    @Test fun `model settings expose real provider and routing state without a per-send confirmation`() {
        for (token in listOf("OpenAI、Claude 与 Gemini 共用 OpenRouter", "路由", "自动路由", "自动兜底", "质量升级", "允许跨服务商兜底", "核验模型目录")) {
            assertTrue("missing $token", ui.contains(token))
        }
        assertTrue(app.contains("onVerifyRegistry"))
        assertTrue(app.contains("onSaveRoutingPolicy"))
        assertFalse(ui.contains("确认发送给第三方服务"))
    }

    @Test fun `settings state and save path share the same persisted routing owner`() {
        assertTrue(owner.contains("loadRoutingPolicy.execute()"))
        assertTrue(owner.contains("saveRoutingPolicy.execute(policy)"))
        assertTrue(owner.contains("routingPolicy = routingPolicy"))
    }
}

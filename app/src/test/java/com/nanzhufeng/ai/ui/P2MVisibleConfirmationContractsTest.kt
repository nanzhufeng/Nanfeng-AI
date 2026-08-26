package com.nanzhufeng.ai.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class P2MVisibleConfirmationContractsTest {
    private val ui = File("src/main/java/com/nanzhufeng/ai/ui/ModelSettingsUi.kt").readText()
    private val owner = File("src/main/java/com/nanzhufeng/ai/ui/ModelSettingsViewModel.kt").readText()
    private val app = File("src/main/java/com/nanzhufeng/ai/ui/NanfengAiApp.kt").readText()

    @Test fun `model settings keep only provider configuration and the second page keeps operational records`() {
        for (token in listOf("OpenRouter、Qwen、DeepSeek", "费用与用量", "上下文记录", "运行诊断", "测试连接")) {
            assertTrue("missing $token", ui.contains(token))
        }
        for (token in listOf("质量升级", "跨模型复核", "保存路由策略", "核验模型目录")) {
            assertFalse("unexpected $token", ui.contains(token))
        }
        assertFalse(ui.contains("确认发送给第三方服务"))
    }

    @Test fun `quality escalation and cross model review are disabled by default`() {
        val policy = File("src/main/java/com/nanzhufeng/ai/domain/ChatRoutingPolicy.kt").readText()
        assertTrue(policy.contains("qualityEscalationEnabled: Boolean = false"))
        assertTrue(policy.contains("crossModelReviewPolicy: CrossModelReviewPolicy = CrossModelReviewPolicy.NEVER"))
    }
}

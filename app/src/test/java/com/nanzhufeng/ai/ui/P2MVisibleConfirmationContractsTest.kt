package com.nanzhufeng.ai.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class P2MVisibleConfirmationContractsTest {
    private val ui = File("src/main/java/com/nanzhufeng/ai/ui/ModelSettingsUi.kt").readText()
    private val owner = File("src/main/java/com/nanzhufeng/ai/ui/ModelSettingsViewModel.kt").readText()
    private val activity = File("src/main/java/com/nanzhufeng/ai/NanfengAiActivity.kt").readText()
    private val app = File("src/main/java/com/nanzhufeng/ai/ui/NanfengAiApp.kt").readText()

    @Test fun `normal model settings hide registry verification and one-time real-service actions`() {
        for (token in listOf("P2MRealServiceConfirmationDialog", "P2MRealServiceStatusCard", "公开模型目录", "真实服务状态", "核验公开模型目录", "确认一次真实文本请求")) {
            assertFalse(token, ui.contains(token))
            assertFalse(token, app.contains(token))
        }
        assertFalse(app.contains("onVerifyRegistry"))
        assertFalse(app.contains("onOpenRealServiceConfirmation"))
    }

    @Test fun `controlled owner remains outside the normal settings rendering path`() {
        assertTrue(owner.contains("realServiceReadiness.execute()"))
        assertTrue(owner.contains("realServiceExecutor.execute(ready.spec.fingerprint())"))
        assertTrue(owner.contains("realServiceConfirmationChecked = false"))
        assertTrue(owner.contains("未创建 Provider Attempt"))
        assertFalse(activity.contains("ACTION_P2M_REAL_SERVICE_ACCEPTANCE"))
        assertFalse(app.contains("showRealServiceConfirmation"))
        assertFalse(app.contains("verifyRegistry"))
    }
}

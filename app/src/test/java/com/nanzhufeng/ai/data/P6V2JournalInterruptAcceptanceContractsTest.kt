package com.nanzhufeng.ai.data

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class P6V2JournalInterruptAcceptanceContractsTest {
    @Test
    fun `journal interruption seam is limited to one disposable acceptance application`() {
        val gradle = File("build.gradle.kts").readText()
        val seam = File("src/main/java/com/nanzhufeng/ai/data/P6V2JournalInterruptAcceptance.kt").readText()
        val container = File("src/main/java/com/nanzhufeng/ai/app/AppContainer.kt").readText()
        val ui = File("src/main/java/com/nanzhufeng/ai/ui/NanfengAiApp.kt").readText()

        assertTrue(gradle.contains("create(\"p6V2JournalInterruptAcceptance\")"))
        assertTrue(gradle.contains("applicationIdSuffix = \".p6v2journalinterruptacceptance\""))
        assertTrue(gradle.contains("buildConfigField(\"boolean\", \"P6_V2_JOURNAL_INTERRUPT_ACCEPTANCE\", \"true\")"))
        assertTrue(seam.contains("BuildConfig.P6_V2_JOURNAL_INTERRUPT_ACCEPTANCE"))
        assertTrue(seam.contains("com.nanzhufeng.ai.p6v2journalinterruptacceptance"))
        assertTrue(seam.contains("promotedCount != 1 || marker.exists()"))
        assertTrue(seam.contains("output.fd.sync()"))
        assertTrue(seam.contains("Process.killProcess(Process.myPid())"))
        assertTrue(seam.contains("startup journal="))
        assertTrue(container.contains("p6V2JournalInterruptAcceptance::afterAttachmentPromotion"))
        assertTrue(container.contains("recordStartupAudit(workspaceExchangeV2AtomicRestoreStore)"))
        assertTrue(container.contains("Thread {"))
        assertFalse(ui.contains("P6V2JournalInterruptAcceptance"))
    }
}

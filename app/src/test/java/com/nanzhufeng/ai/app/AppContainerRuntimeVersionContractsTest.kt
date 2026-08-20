package com.nanzhufeng.ai.app

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppContainerRuntimeVersionContractsTest {
    @Test fun `runtime artifacts use the current build version instead of historical release labels`() {
        val source = File("src/main/java/com/nanzhufeng/ai/app/AppContainer.kt").readText()
        assertTrue(source.contains("import com.nanzhufeng.ai.BuildConfig"))
        for (call in listOf(
            "RunOfflineEvalUseCase(offlineEvalRepository, clock, BuildConfig.VERSION_NAME)",
            "AndroidPrivacyDataManager(context, database, BuildConfig.VERSION_NAME)",
            "AndroidLocalBackupRestoreManager(context, database, BuildConfig.VERSION_NAME)",
        )) {
            assertTrue("missing current version for $call", source.contains(call))
        }
        assertFalse(source.contains("\"0.3.0-p4m\""))
        assertFalse(source.contains("\"0.3.0-p5d\""))
    }
}

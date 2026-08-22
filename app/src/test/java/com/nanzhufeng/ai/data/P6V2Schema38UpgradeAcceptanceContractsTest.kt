package com.nanzhufeng.ai.data

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class P6V2Schema38UpgradeAcceptanceContractsTest {
    @Test fun `schema 38 upgrade audit stays in one disposable package and contains only counts`() {
        val gradle = File("build.gradle.kts").readText()
        val seam = File("src/main/java/com/nanzhufeng/ai/data/P6V2Schema38UpgradeAcceptance.kt").readText()
        val container = File("src/main/java/com/nanzhufeng/ai/app/AppContainer.kt").readText()
        val ui = File("src/main/java/com/nanzhufeng/ai/ui/NanfengAiApp.kt").readText()

        assertTrue(gradle.contains("create(\"p6V2Schema38UpgradeAcceptance\")"))
        assertTrue(gradle.contains("applicationIdSuffix = \".p6v2schema38upgradeacceptance\""))
        assertTrue(gradle.contains("buildConfigField(\"boolean\", \"P6_V2_SCHEMA38_UPGRADE_ACCEPTANCE\", \"true\")"))
        assertTrue(seam.contains("BuildConfig.P6_V2_SCHEMA38_UPGRADE_ACCEPTANCE"))
        assertTrue(seam.contains("com.nanzhufeng.ai.p6v2schema38upgradeacceptance"))
        assertTrue(seam.contains("startup schema=39 project="))
        assertTrue(seam.contains("v2receipt="))
        assertFalse(seam.contains("Uri"))
        assertFalse(seam.contains("path"))
        assertFalse(seam.contains("displayName"))
        assertTrue(container.contains("p6V2Schema38UpgradeAcceptance.recordStartupAudit()"))
        assertFalse(ui.contains("P6V2Schema38UpgradeAcceptance"))
    }
}

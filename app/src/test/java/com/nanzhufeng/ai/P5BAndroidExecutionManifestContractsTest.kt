package com.nanzhufeng.ai

import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class P5BAndroidExecutionManifestContractsTest {
    @Test fun `p5b has no app declared background component and p7d work remains code guarded`() {
        val project = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .first { File(it, "app/src/main/AndroidManifest.xml").isFile }
        val manifest = File(project, "app/src/main/AndroidManifest.xml").readText()
        // P7-D adds WorkManager only behind configured+verified+READY guards. Its library may merge
        // a system WakeLock permission, but the app still declares no receiver/service/boot trigger.
        listOf("<service", "<receiver", "BOOT_COMPLETED").forEach {
            assertFalse("App manifest must not declare $it", manifest.contains(it))
        }
        assertFalse("P7-D worker must not be declared as an exported app component", manifest.contains("P7DSyncWorker"))
    }
}

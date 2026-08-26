package com.nanzhufeng.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class P5BAndroidExecutionManifestContractsTest {
    @Test fun `only normal chat generation declares a private foreground service while p7d work remains code guarded`() {
        val project = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .first { File(it, "app/src/main/AndroidManifest.xml").isFile }
        val manifest = File(project, "app/src/main/AndroidManifest.xml").readText()
        // P7-D adds WorkManager only behind configured+verified+READY guards. The sole app service
        // is the user-initiated normal-chat foreground guard, never a boot or background trigger.
        assertFalse(manifest.contains("<receiver"))
        assertFalse(manifest.contains("BOOT_COMPLETED"))
        assertTrue(manifest.contains(".background.NormalChatGenerationForegroundService"))
        assertTrue(manifest.contains("android:foregroundServiceType=\"dataSync\""))
        assertTrue(manifest.contains("android:stopWithTask=\"false\""))
        assertFalse("P7-D worker must not be declared as an exported app component", manifest.contains("P7DSyncWorker"))
    }
}

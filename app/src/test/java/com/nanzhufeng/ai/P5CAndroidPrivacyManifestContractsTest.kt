package com.nanzhufeng.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class P5CAndroidPrivacyManifestContractsTest {
    private val manifest = File("src/main/AndroidManifest.xml").readText()

    @Test fun `manifest keeps only declared network notification and user initiated foreground service permissions`() {
        val permissions = Regex("<uses-permission android:name=\\\"([^\\\"]+)\\\"").findAll(manifest).map { it.groupValues[1] }.toList()
        assertEquals(listOf(
            "android.permission.INTERNET",
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.FOREGROUND_SERVICE",
            "android.permission.FOREGROUND_SERVICE_DATA_SYNC",
            // Held only by GenerationForegroundService during a short active sync interval.
            "android.permission.WAKE_LOCK",
        ), permissions)
        assertFalse(manifest.contains("MANAGE_EXTERNAL_STORAGE"))
        assertFalse(manifest.contains("READ_MEDIA"))
        assertFalse(manifest.contains("CAMERA"))
        assertFalse(manifest.contains("RECORD_AUDIO"))
    }

    @Test fun `manifest documents the current user initiated network boundary`() {
        assertTrue(manifest.contains("user-initiated model calls"))
        assertTrue(manifest.contains("credentials remain scoped to their owner"))
        assertFalse(manifest.contains("OpenRouterEgressPolicy.Disabled"))
    }
}

package com.nanzhufeng.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class P5CAndroidPrivacyManifestContractsTest {
    private val manifest = File("src/main/AndroidManifest.xml").readText()

    @Test fun `manifest keeps only scoped internet permission and no broad storage or sensitive hardware permission`() {
        val permissions = Regex("<uses-permission android:name=\\\"([^\\\"]+)\\\"").findAll(manifest).map { it.groupValues[1] }.toList()
        assertEquals(listOf("android.permission.INTERNET"), permissions)
        assertFalse(manifest.contains("MANAGE_EXTERNAL_STORAGE"))
        assertFalse(manifest.contains("READ_MEDIA"))
        assertFalse(manifest.contains("CAMERA"))
        assertFalse(manifest.contains("RECORD_AUDIO"))
        assertFalse(manifest.contains("POST_NOTIFICATIONS"))
    }

    @Test fun `manifest documents disabled provider boundary`() = assertTrue(manifest.contains("OpenRouterEgressPolicy.Disabled"))
}

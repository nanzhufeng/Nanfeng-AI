package com.nanzhufeng.ai.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Keeps the verified Qwen context and per-model output ceilings from silently drifting. */
class ModelProfileAssetContractsTest {
    @Test fun `Qwen visual profiles retain verified one million context and model specific output ceilings`() {
        val raw = File("src/main/assets/model_profiles.json").readText()
        val expected = mapOf("QWEN_3_7_PLUS" to 65_536, "QWEN_3_8_MAX" to 131_072, "QWEN_3_6_FLASH" to 65_536)
        expected.forEach { (preset, maxOutput) ->
            val profile = raw.substringAfter("\"presetId\": \"$preset\"").substringBefore("\n    }")
            assertTrue("missing $preset", profile.isNotBlank())
            assertEquals(1, Regex("\"contextWindowTokens\": 1000000").findAll(profile).count())
            assertEquals(1, Regex("\"maxOutputTokens\": $maxOutput").findAll(profile).count())
            assertTrue(profile.contains("\"video\": true"))
            assertFalse(profile.contains("\"audio\": true"))
        }
    }

    @Test fun `DeepSeek V4 Pro profile retains verified context output and tool capability`() {
        val raw = File("src/main/assets/model_profiles.json").readText()
        val profile = raw.substringAfter("\"presetId\": \"DEEPSEEK_V4_PRO\"").substringBefore("\n    }")

        assertTrue("missing DEEPSEEK_V4_PRO", profile.isNotBlank())
        assertTrue(profile.contains("\"contextWindowTokens\": 1000000"))
        assertTrue(profile.contains("\"maxOutputTokens\": 384000"))
        assertTrue(profile.contains("\"tools\": true"))
        assertFalse(profile.contains("\"image\": true"))
        assertFalse(profile.contains("\"pdf\": true"))
        assertFalse(profile.contains("\"video\": true"))
    }
}

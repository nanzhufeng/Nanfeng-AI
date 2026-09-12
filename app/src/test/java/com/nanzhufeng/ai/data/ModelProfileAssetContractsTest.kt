package com.nanzhufeng.ai.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Keeps the verified Qwen context and per-model output ceilings from silently drifting. */
class ModelProfileAssetContractsTest {
    @Test fun `Grok bundled profiles retain exact current OpenRouter IDs and verified modality boundaries`() {
        val raw = File("src/main/assets/model_profiles.json").readText()
        val high = raw.substringAfter("\"presetId\": \"GROK_4_6_HIGH\"").substringBefore("\n    }")

        assertFalse(raw.contains("\"presetId\": \"GROK_4_5\""))
        assertTrue(high.contains("\"modelId\": \"x-ai/grok-4.6\""))
        assertTrue(high.contains("\"contextWindowTokens\": 500000"))
        listOf(high).forEach { profile ->
            assertTrue(profile.contains("\"image\": true"))
            assertTrue(profile.contains("\"pdf\": true"))
            assertTrue(profile.contains("\"tools\": true"))
            assertTrue(profile.contains("\"reasoning\": true"))
            assertFalse(profile.contains("\"video\": true"))
            assertFalse(profile.contains("\"audio\": true"))
        }
    }

    @Test fun `Qwen profiles retain verified one million context and model specific capability boundaries`() {
        val raw = File("src/main/assets/model_profiles.json").readText()
        val expected = mapOf("QWEN_3_7_PLUS" to (65_536 to true), "QWEN_3_8_MAX" to (131_072 to false), "QWEN_3_6_FLASH" to (65_536 to true))
        expected.forEach { (preset, limits) ->
            val (maxOutput, supportsVideo) = limits
            val profile = raw.substringAfter("\"presetId\": \"$preset\"").substringBefore("\n    }")
            assertTrue("missing $preset", profile.isNotBlank())
            assertEquals(1, Regex("\"contextWindowTokens\": 1000000").findAll(profile).count())
            assertEquals(1, Regex("\"maxOutputTokens\": $maxOutput").findAll(profile).count())
            assertEquals(supportsVideo, profile.contains("\"video\": true"))
            assertFalse(profile.contains("\"audio\": true"))
            if (preset == "QWEN_3_8_MAX") assertTrue(profile.contains("\"reasoning\": true"))
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

    @Test fun `DeepSeek V4 point 1 Flash profile retains its official direct identity and text capabilities`() {
        val raw = File("src/main/assets/model_profiles.json").readText()
        val profile = raw.substringAfter("\"presetId\": \"DEEPSEEK_V4_FLASH\"").substringBefore("\n    }")

        assertTrue("missing DEEPSEEK_V4_FLASH", profile.isNotBlank())
        assertTrue(profile.contains("\"providerId\": \"DEEPSEEK\""))
        assertTrue(profile.contains("\"modelId\": \"deepseek-flash\""))
        assertTrue(profile.contains("\"contextWindowTokens\": 1000000"))
        assertTrue(profile.contains("\"maxOutputTokens\": 384000"))
        assertTrue(profile.contains("\"tools\": true"))
        assertTrue(profile.contains("\"reasoning\": true"))
        assertTrue(profile.contains("\"image\": true"))
        assertFalse(profile.contains("\"pdf\": true"))
        assertFalse(profile.contains("\"video\": true"))
    }
    @Test fun `Zhipu GLM Flash profile exposes only the capabilities this client can transmit`() {
        val raw = File("src/main/assets/model_profiles.json").readText()
        val profile = raw.substringAfter("\"presetId\": \"GLM_5_3_FLASH\"").substringBefore("\n    }")

        assertTrue("missing GLM_5_3_FLASH", profile.isNotBlank())
        assertTrue(profile.contains("\"providerId\": \"ZHIPU\""))
        assertTrue(profile.contains("\"modelId\": \"glm-5.3-flash\""))
        assertTrue(profile.contains("\"text\": true"))
        assertFalse(profile.contains("\"image\": true"))
        assertFalse(profile.contains("\"pdf\": true"))
        assertFalse(profile.contains("\"video\": true"))
        assertFalse(profile.contains("\"tools\": true"))
        assertTrue(profile.contains("\"reasoning\": true"))
        assertFalse(profile.contains("\"structuredOutput\": true"))
        assertTrue(profile.contains("\"contextWindowTokens\": 1000000"))
        assertTrue(profile.contains("\"maxOutputTokens\": 128000"))
    }

    @Test fun `Zhipu GLM flagship profile keeps the official identity and deep capabilities`() {
        val raw = File("src/main/assets/model_profiles.json").readText()
        val profile = raw.substringAfter("\"presetId\": \"GLM_5_3\"").substringBefore("\n    }")

        assertTrue("missing GLM_5_3", profile.isNotBlank())
        assertTrue(profile.contains("\"providerId\": \"ZHIPU\""))
        assertTrue(profile.contains("\"modelId\": \"glm-5.3\""))
        assertTrue(profile.contains("\"text\": true"))
        assertTrue(profile.contains("\"streaming\": true"))
        assertTrue(profile.contains("\"tools\": true"))
        assertTrue(profile.contains("\"reasoning\": true"))
        assertTrue(profile.contains("\"structuredOutput\": true"))
        assertFalse(profile.contains("\"image\": true"))
        assertFalse(profile.contains("\"pdf\": true"))
        assertFalse(profile.contains("\"video\": true"))
        assertTrue(profile.contains("\"contextWindowTokens\": 1000000"))
        assertTrue(profile.contains("\"maxOutputTokens\": 131072"))
    }
}

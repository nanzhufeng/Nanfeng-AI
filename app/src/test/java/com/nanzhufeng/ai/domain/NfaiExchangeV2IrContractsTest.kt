package com.nanzhufeng.ai.domain

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class NfaiExchangeV2IrContractsTest {
    @Test fun `shared v2 golden preserves owner fields without a storage side effect`() {
        val exchange = golden()
        assertEquals("aaeafcfbcfdf1d36ab4c8484e5d6d3537b6d4a60abc7216b3aa4fd702b1c0ef8", NfaiExchangeV2Ir.semanticHash(exchange))
        val verified = NfaiExchangeV2Ir.validate(exchange)
        assertEquals("aaeafcfbcfdf1d36ab4c8484e5d6d3537b6d4a60abc7216b3aa4fd702b1c0ef8", verified.semanticHash)
        assertEquals("FOREST", exchange.getJSONArray("projects").getJSONObject(0).getJSONObject("appearance").getString("color"))
        assertEquals(1, exchange.getJSONArray("projects").getJSONObject(0).getJSONArray("instructionHistory").length())
        assertEquals(1, exchange.getJSONArray("conversations").getJSONObject(0).getJSONObject("settings").getJSONArray("memorySources").length())
        assertEquals(1, exchange.getJSONArray("knowledge").getJSONObject(0).getJSONArray("history").length())
        assertEquals("Memory title", exchange.getJSONArray("memory").getJSONObject(0).getString("title"))
        assertEquals("PROJECT", exchange.getJSONArray("relations").getJSONObject(0).getString("scope"))
    }

    @Test fun `v2 rejects source locators and missing owner history instead of silently degrading`() {
        val unsafe = golden(); unsafe.getJSONArray("knowledge").getJSONObject(0).getJSONArray("sourceEvidence").getJSONObject(0).put("sourceReference", "content://forbidden")
        unsafe.getJSONObject("export").put("semanticHash", NfaiExchangeV2Ir.semanticHash(unsafe))
        assertTrue(runCatching { NfaiExchangeV2Ir.validate(unsafe) }.isFailure)
        val lossy = golden(); lossy.getJSONArray("projects").getJSONObject(0).remove("instructionHistory")
        lossy.getJSONObject("export").put("semanticHash", NfaiExchangeV2Ir.semanticHash(lossy))
        assertTrue(runCatching { NfaiExchangeV2Ir.validate(lossy) }.isFailure)
    }

    private fun golden(): JSONObject {
        val candidates = listOf(File("protocol/fixtures/nfai.exchange.v2.golden.json"), File("../protocol/fixtures/nfai.exchange.v2.golden.json"))
        return JSONObject(candidates.firstOrNull(File::isFile)?.readText() ?: error("找不到共享 v2 golden fixture。"))
    }
}

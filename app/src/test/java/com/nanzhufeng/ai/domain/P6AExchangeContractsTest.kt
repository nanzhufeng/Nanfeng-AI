package com.nanzhufeng.ai.domain

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class P6AExchangeContractsTest {
    @Test fun `golden semantic package exports and Android preflight preserves all safe domains`() {
        val root = sequenceOf(File("../protocol"), File("protocol"), File("../../protocol")).firstOrNull { it.isDirectory } ?: error("shared protocol fixture missing")
        val json = File(root, "fixtures/nfai.exchange.v1.golden.json").readText()
        val exchange = JSONObject(json)
        val asset = File(root, "fixtures/assets/golden-note.txt").readBytes()
        fun ids(key: String) = exchange.getJSONArray(key).let { array -> buildSet { repeat(array.length()) { add(array.getJSONObject(it).getString("id")) } } }
        val attachmentIds = buildSet {
            val conversations = exchange.getJSONArray("conversations")
            repeat(conversations.length()) { conversationIndex ->
                val messages = conversations.getJSONObject(conversationIndex).getJSONArray("messages")
                repeat(messages.length()) { messageIndex ->
                    val blocks = messages.getJSONObject(messageIndex).getJSONArray("blocks")
                    repeat(blocks.length()) { blockIndex ->
                        val block = blocks.getJSONObject(blockIndex)
                        if (block.getString("kind") == "ASSET_REF") add(block.getJSONObject("asset").getString("id"))
                    }
                }
            }
        }
        val selection = NfaiExchangeExportSelection(ids("projects"), ids("conversations"), ids("knowledge"), ids("memory"), ids("relations"), attachmentIds)
        val target = createTempFile("nfai-exchange", ".zip")
        try {
            val desktopPackage = File(root, "artifacts/nfai.exchange.v1.golden.nfai-exchange")
            require(desktopPackage.isFile) { "run protocol/scripts/run-golden.mjs before Android cross-implementation contracts" }
            val desktopPreflight = NfaiExchangeV1Gateway.preflight(desktopPackage) as NfaiExchangeResult.Preflighted
            assertEquals("ad41c1ee6aa64b9e2f218034dbefccc333c43fb923b874b12ff51ce5972d4031", desktopPreflight.value.semanticHash)
            val exported = NfaiExchangeV1Gateway.export(NfaiExchangePreparedSnapshot(selection, json, listOf(NfaiExchangeAsset("assets/3f3fa3e4842fd4d54be489e980058c1aaf1bd89951dd0dd521e29f0b81639969", asset))), target)
            assertTrue("export result=$exported", exported is NfaiExchangeResult.Exported)
            val preflight = NfaiExchangeV1Gateway.preflight(target) as NfaiExchangeResult.Preflighted
            assertEquals("ad41c1ee6aa64b9e2f218034dbefccc333c43fb923b874b12ff51ce5972d4031", preflight.value.semanticHash)
            assertEquals(1, preflight.value.projectCount); assertEquals(1, preflight.value.conversationCount); assertEquals(1, preflight.value.knowledgeCount)
            assertTrue(preflight.value.hasHighSensitiveData)
        } finally { target.delete() }
    }
}

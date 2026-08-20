package com.nanzhufeng.ai.domain

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class P7CCloudGatewayContractsTest {
    private fun envelope(): String {
        val root = sequenceOf(File("../protocol"), File("protocol"), File("../../protocol")).first { it.isDirectory }
        return JSONObject(File(root, "fixtures/nfai.sync.v1.golden.json").readText()).getJSONObject("envelope").toString()
    }

    @Test fun `missing private configuration stays disabled without a transport call`() {
        assertTrue(P7CServiceConfiguration.resolve(null, "", "") is P7CServiceAvailability.Disabled)
        assertEquals(P7CCloudResult.Disabled, P7CDisabledCloudGateway.commit(0, envelope()))
    }

    @Test fun `configured gateway submits only a strict P7-A envelope and checks receipt`() {
        val calls = mutableListOf<Pair<String, JSONObject>>()
        val transport = object : P7CAuthenticatedRpcTransport {
            override fun call(function: String, body: String): String {
                val request = JSONObject(body); calls += function to request
                return "{\"revision\":7,\"payload_hash\":\"${request.getJSONObject("p_envelope").getString("payloadHash")}\"}"
            }
        }
        val availability = P7CServiceConfiguration.resolve("https://example.invalid", "public-key", "web-client") as P7CServiceAvailability.Configured
        val result = P7CSupabaseEnvelopeGateway(availability.config, transport).commit(6, envelope())
        assertTrue(result is P7CCloudResult.Value)
        assertEquals("nanfeng_sync_commit_document", calls.single().first)
        assertEquals("com.nanzhufeng.ai", calls.single().second.getString("p_app_id"))
        assertEquals(6, calls.single().second.getLong("p_expected_revision"))
    }
}

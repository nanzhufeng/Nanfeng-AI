package com.nanzhufeng.ai.domain

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class P7ASyncContractsTest {
    private fun fixture(): JSONObject {
        val root = sequenceOf(File("../protocol"), File("protocol"), File("../../protocol")).first { it.isDirectory }
        return JSONObject(File(root, "fixtures/nfai.sync.v1.golden.json").readText())
    }
    private fun b64(value: String) = android.util.Base64.decode(value, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP)
    private fun snapshot(fixture: JSONObject): NfaiSyncPreparedSnapshot {
        val payload = fixture.getJSONObject("payload"); val records = payload.getJSONArray("records")
        return NfaiSyncPreparedSnapshot(payload.getString("appId"), payload.getString("documentId"), payload.getLong("revision"), buildList {
            repeat(records.length()) { index -> val record = records.getJSONObject(index); add(NfaiSyncRecord(record.getString("kind"), record.getString("id"), record.getLong("revision"), record.getString("classification"), record.getJSONObject("content").toString())) }
        })
    }

    @Test fun `Android strict seal and open match shared Desktop golden`() {
        val fixture = fixture(); val material = fixture.getJSONObject("material")
        val sealed = NfaiSyncV1Gateway.sealKnownAnswer(snapshot(fixture), fixture.getString("recoveryCode").toCharArray(), NfaiSyncKnownAnswerMaterial(b64(material.getString("dataKey")), b64(material.getString("salt")), b64(material.getString("wrappingNonce")), b64(material.getString("payloadNonce")))) as NfaiSyncResult.Sealed
        val goldenEnvelope = fixture.getJSONObject("envelope"); val sealedEnvelope = JSONObject(sealed.canonicalEnvelope)
        assertEquals(goldenEnvelope.getString("payloadHash"), sealedEnvelope.getString("payloadHash"))
        assertEquals(goldenEnvelope.getJSONObject("wrappedDataKey").getString("ciphertext"), sealedEnvelope.getJSONObject("wrappedDataKey").getString("ciphertext"))
        assertEquals(goldenEnvelope.getJSONObject("payload").getString("ciphertext"), sealedEnvelope.getJSONObject("payload").getString("ciphertext"))
        val opened = NfaiSyncV1Gateway.open(sealed.canonicalEnvelope, fixture.getString("recoveryCode").toCharArray(), "com.nanzhufeng.ai", "sync-fixture-v1", 7)
        assertTrue(opened is NfaiSyncResult.Opened)
        assertEquals(2, (opened as NfaiSyncResult.Opened).value.snapshot.records.size)
    }

    @Test fun `wrong code tamper unknown duplicate truncation binding rollback and sensitive content fail closed`() {
        val fixture = fixture(); val envelope = fixture.getJSONObject("envelope").toString(); val code = fixture.getString("recoveryCode").toCharArray()
        fun rejected(value: NfaiSyncResult) = assertTrue(value is NfaiSyncResult.Rejected)
        rejected(NfaiSyncV1Gateway.open(envelope, "wrong recovery code".toCharArray(), "com.nanzhufeng.ai", "sync-fixture-v1", 1))
        rejected(NfaiSyncV1Gateway.open(envelope.replace("d10aa455", "e10aa455"), code, "com.nanzhufeng.ai", "sync-fixture-v1", 1))
        rejected(NfaiSyncV1Gateway.open(envelope.replace("VtQy", "WtQy"), code, "com.nanzhufeng.ai", "sync-fixture-v1", 1))
        rejected(NfaiSyncV1Gateway.open(envelope.dropLast(8), code, "com.nanzhufeng.ai", "sync-fixture-v1", 1))
        rejected(NfaiSyncV1Gateway.open(envelope, code, "other.app", "sync-fixture-v1", 1))
        rejected(NfaiSyncV1Gateway.open(envelope, code, "com.nanzhufeng.ai", "other-document", 1))
        assertEquals("REVISION_ROLLBACK", (NfaiSyncV1Gateway.open(envelope, code, "com.nanzhufeng.ai", "sync-fixture-v1", 8) as NfaiSyncResult.Rejected).code)
        rejected(NfaiSyncV1Gateway.preflight(envelope.dropLast(1) + ",\"unknown\":1}"))
        rejected(NfaiSyncV1Gateway.preflight(envelope.dropLast(1) + ",\"appId\":\"duplicate\"}"))
        rejected(NfaiSyncV1Gateway.preflight("x".repeat(2 * 1024 * 1024 + 1)))
        val bad = NfaiSyncPreparedSnapshot("com.nanzhufeng.ai", "safe-doc", 1, listOf(NfaiSyncRecord("knowledge", "knowledge-safe", 1, "HIGH_SENSITIVE", "{\"title\":\"classified\"}")))
        rejected(NfaiSyncV1Gateway.seal(bad, code, ByteArray(32)))
        val unclassifiedSecret = NfaiSyncPreparedSnapshot("com.nanzhufeng.ai", "safe-doc", 1, listOf(NfaiSyncRecord("knowledge", "knowledge-safe", 1, "NORMAL", "{\"apiKey\":\"forbidden\"}")))
        rejected(NfaiSyncV1Gateway.seal(unclassifiedSecret, code, ByteArray(32)))
    }

    @Test fun `one time recovery setup can seal later without persisting the recovery code`() {
        val fixture = fixture()
        val code = "saved-once-recovery-code".toCharArray()
        val wrapping = NfaiSyncV1Gateway.createAccountWrappingMaterial(code)
        val dataKey = ByteArray(32) { (it + 1).toByte() }
        try {
            val sealed = NfaiSyncV1Gateway.sealWithAccountWrappingMaterial(snapshot(fixture), dataKey, wrapping) as NfaiSyncResult.Sealed
            val opened = NfaiSyncV1Gateway.open(sealed.canonicalEnvelope, code, "com.nanzhufeng.ai", "sync-fixture-v1", 7)
            assertTrue(opened is NfaiSyncResult.Opened)
        } finally {
            code.fill('\u0000')
            wrapping.wrappingKey.fill(0)
            wrapping.salt.fill(0)
            dataKey.fill(0)
        }
    }

    @Test fun `stored wrapping material opens its envelope without reusing the recovery code`() {
        val fixture = fixture()
        val code = "changed-recovery-code".toCharArray()
        val wrapping = NfaiSyncV1Gateway.createAccountWrappingMaterial(code)
        val wrong = NfaiSyncV1Gateway.createAccountWrappingMaterial("different-recovery-code".toCharArray())
        val dataKey = ByteArray(32) { (it + 11).toByte() }
        try {
            val sealed = NfaiSyncV1Gateway.sealWithAccountWrappingMaterial(snapshot(fixture), dataKey, wrapping) as NfaiSyncResult.Sealed
            assertTrue(NfaiSyncV1Gateway.openWithAccountWrappingMaterial(sealed.canonicalEnvelope, wrapping, "com.nanzhufeng.ai", "sync-fixture-v1", 7) is NfaiSyncResult.Opened)
            assertTrue(NfaiSyncV1Gateway.openWithAccountWrappingMaterial(sealed.canonicalEnvelope, wrong, "com.nanzhufeng.ai", "sync-fixture-v1", 7) is NfaiSyncResult.Rejected)
        } finally {
            code.fill('\u0000')
            wrapping.wrappingKey.fill(0); wrapping.salt.fill(0)
            wrong.wrappingKey.fill(0); wrong.salt.fill(0)
            dataKey.fill(0)
        }
    }
}

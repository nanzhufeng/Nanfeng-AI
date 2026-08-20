package com.nanzhufeng.ai.domain

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class P7ESemanticSnapshotContractsTest {
    @Test fun `shared Android Desktop semantic fixture is stable P7-A records and round trips without P5-D or P6 formats`() {
        val fixtureRoot = sequenceOf(File("../protocol"), File("protocol"), File("../../protocol")).first { it.isDirectory }
        val fixture = JSONObject(File(fixtureRoot, "fixtures/nfai.sync.semantic.v1.golden.json").readText())
        val prepared = P7ESemanticSnapshotMapper.toPreparedSnapshot(P7ESemanticSnapshot(
            fixture.getString("appId"), fixture.getString("documentId"), fixture.getLong("revision"),
            buildList { val records = fixture.getJSONArray("records"); repeat(records.length()) { index ->
                val record = records.getJSONObject(index)
                add(P7ESemanticState(record.getString("kind"), record.getString("id"), record.getLong("revision"), record.getString("classification"), record.getJSONObject("content").getJSONObject("value").toString()))
            } },
        ))
        assertEquals(listOf("knowledge", "project", "safe_settings"), prepared.records.map { it.kind })
        assertTrue(prepared.records.all { it.contentJson.contains(NFAI_SYNC_SEMANTIC_RECORD_FORMAT_V1) })
        assertEquals(prepared, P7ESemanticSnapshotMapper.toPreparedSnapshot(P7ESemanticSnapshotMapper.fromOpened(prepared)))
    }

    @Test fun `semantic mapping rejects raw persistence and sensitive fields as one failure`() {
        fun rejected(value: String) = runCatching {
            P7ESemanticSnapshotMapper.toPreparedSnapshot(P7ESemanticSnapshot("com.nanzhufeng.ai", "doc", 1, listOf(P7ESemanticState("knowledge", "knowledge-a", 1, valueJson = value))))
        }.isFailure
        assertTrue(rejected("{\"storageKey\":\"attachments/v1/a\"}"))
        assertTrue(rejected("{\"body\":\"api key: forbidden\"}"))
    }
}

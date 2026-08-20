package com.nanzhufeng.ai.data

import com.nanzhufeng.ai.data.local.NanfengAiDatabase
import com.nanzhufeng.ai.domain.P7ESemanticSnapshotMapper
import com.nanzhufeng.ai.domain.P7ESemanticSnapshotSource

/** Keeps AppContainer's P7-E source typed: DAO semantic mapping is the only production snapshot path. */
class AndroidP7ESemanticSnapshotSourceAdapter(
    private val database: NanfengAiDatabase,
    private val appId: String = "com.nanzhufeng.ai",
) : P7ESemanticSnapshotSource {
    override fun snapshot(documentId: String, revision: Long) = P7ESemanticSnapshotMapper.toPreparedSnapshot(
        AndroidP7ESemanticSnapshotSource(database, appId).snapshot(documentId, revision),
    )
}

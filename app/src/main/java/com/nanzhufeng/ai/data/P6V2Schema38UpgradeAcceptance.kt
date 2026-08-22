package com.nanzhufeng.ai.data

import android.content.Context
import android.util.Log
import com.nanzhufeng.ai.BuildConfig
import com.nanzhufeng.ai.data.local.NanfengAiDatabase

/**
 * Disposable migration acceptance audit. It reads only aggregate counts after Room has opened a
 * schema-38 database with the production migration chain; it has no UI, mutation, locator or
 * content data and is inert outside this exact separately installed package.
 */
internal class P6V2Schema38UpgradeAcceptance(
    context: Context,
    private val database: NanfengAiDatabase,
) {
    private val app = context.applicationContext

    fun recordStartupAudit() {
        if (!enabled()) return
        Log.i(
            TAG,
            "startup schema=39 project=${database.projectDao().listAllForP7ESemanticSnapshot().size} " +
                "conversation=${database.conversationDao().listAllForP7ESemanticSnapshot().size} " +
                "draft=${database.conversationDao().listAllForP7ESemanticSnapshot().count { database.conversationDao().draftFor(it.id) != null }} " +
                "knowledge=${database.knowledgeDao().listKnowledgeIncludingHidden().size} " +
                "memory=${database.memoryDao().listMemories(null, null).size} " +
                "relation=${database.knowledgeRelationshipDao().all().size} " +
                "attachment=${database.privateAttachmentAssetDao().assetCount()} " +
                "v2receipt=${database.workspaceExchangeV2RestoreDao().receiptCount()} " +
                "v2provenance=${database.workspaceExchangeV2RestoreDao().provenanceCount()} " +
                "v2settings=${database.workspaceExchangeV2RestoreDao().settingsCount()}",
        )
    }

    private fun enabled() = BuildConfig.P6_V2_SCHEMA38_UPGRADE_ACCEPTANCE &&
        app.packageName == "com.nanzhufeng.ai.p6v2schema38upgradeacceptance"

    private companion object {
        const val TAG = "P6V2Schema38UpgradeAcceptance"
    }
}

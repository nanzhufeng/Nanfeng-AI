package com.nanzhufeng.ai.data

import android.content.Context
import android.os.Process
import android.util.Log
import com.nanzhufeng.ai.BuildConfig
import com.nanzhufeng.ai.data.local.NanfengAiDatabase
import java.io.File
import java.io.FileOutputStream

/**
 * Acceptance-only interruption seam. It is inert outside the exact disposable application ID,
 * records no package/body/path data, and adds no UI entry or production recovery behavior.
 */
internal class P6V2JournalInterruptAcceptance(
    context: Context,
    private val database: NanfengAiDatabase,
) {
    private val app = context.applicationContext
    private val marker = File(app.filesDir, ".p6-v2-journal-interrupt-once")

    fun afterAttachmentPromotion(promotedCount: Int) {
        if (!enabled() || promotedCount != 1 || marker.exists()) return
        FileOutputStream(marker).use { output ->
            output.write(byteArrayOf(1))
            output.fd.sync()
        }
        Log.i(TAG, "stage=ATTACHMENT_PROMOTED_FIRST marker=durable action=kill-process")
        Process.killProcess(Process.myPid())
    }

    fun recordStartupAudit(store: AndroidWorkspaceExchangeV2AtomicRestoreStore) {
        if (!enabled()) return
        val state = store.journalInterruptAcceptanceAudit()
        Log.i(
            TAG,
            "startup journal=${state.journalCount} owners=${state.ownerCount} attachments=${state.attachmentCount} " +
                "receipt=${state.receiptCount} provenance=${state.provenanceCount} settings=${state.settingsCount}",
        )
    }

    private fun enabled() = BuildConfig.P6_V2_JOURNAL_INTERRUPT_ACCEPTANCE &&
        app.packageName == "com.nanzhufeng.ai.p6v2journalinterruptacceptance"

    private companion object {
        const val TAG = "P6V2JournalAcceptance"
    }
}

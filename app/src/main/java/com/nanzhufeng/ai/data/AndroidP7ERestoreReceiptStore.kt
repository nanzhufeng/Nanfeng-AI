package com.nanzhufeng.ai.data

import android.content.Context
import com.nanzhufeng.ai.domain.P7ERestoreReceipt
import com.nanzhufeng.ai.domain.P7ERestoreReceiptStore

/** Durable, secret-free P7-E receipt metadata. Envelope, recovery material and business text never enter it. */
class AndroidP7ERestoreReceiptStore(context: Context) : P7ERestoreReceiptStore {
    private val prefs = context.applicationContext.getSharedPreferences("p7e_restore_receipts_v1", Context.MODE_PRIVATE)

    override fun receipt(intentId: String): P7ERestoreReceipt? {
        val wire = prefs.getString(intentId, null) ?: return null
        val parts = wire.split('|')
        if (parts.size != 5) return null
        return parts[2].toLongOrNull()?.let { revision ->
            P7ERestoreReceipt(intentId, parts[0], parts[1], revision, parts[3].ifEmpty { null }, parts[4])
        }
    }

    override fun save(receipt: P7ERestoreReceipt) {
        // All fields are opaque identifiers, a revision, a SHA-256 or a stable outcome code.
        prefs.edit().putString(receipt.intentId, listOf(receipt.accountRef, receipt.documentId, receipt.remoteRevision, receipt.payloadHash.orEmpty(), receipt.outcome).joinToString("|")).commit()
    }
}

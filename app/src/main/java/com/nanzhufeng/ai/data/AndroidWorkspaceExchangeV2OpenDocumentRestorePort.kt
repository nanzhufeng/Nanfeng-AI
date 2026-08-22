package com.nanzhufeng.ai.data

import android.content.Context
import android.net.Uri
import com.nanzhufeng.ai.domain.NFAI_EXCHANGE_V2_MAX_PACKAGE_BYTES
import com.nanzhufeng.ai.domain.WorkspaceExchangeV2AtomicRestoreOwner
import com.nanzhufeng.ai.domain.WorkspaceExchangeV2AtomicRestoreResult
import com.nanzhufeng.ai.domain.WorkspaceExchangeV2OpenDocumentRestorePort
import com.nanzhufeng.ai.domain.WorkspaceExchangeV2OpenDocumentRestoreResult
import com.nanzhufeng.ai.domain.WorkspaceExchangeV2RestoreRequest
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest

/**
 * Consumes one transient OpenDocument URI after an explicit user selection. This adapter neither
 * inspects ZIP/JSON nor stores a URI, name, path, or package body: it reads one bounded byte array
 * and forwards it directly to the atomic restore owner.
 */
class AndroidWorkspaceExchangeV2OpenDocumentRestorePort(
    context: Context,
    private val owner: WorkspaceExchangeV2AtomicRestoreOwner,
) : WorkspaceExchangeV2OpenDocumentRestorePort {
    private val app = context.applicationContext

    override fun restoreSelectedDocument(document: Uri): WorkspaceExchangeV2OpenDocumentRestoreResult {
        val packageBytes = runCatching {
            app.contentResolver.openInputStream(document)?.use(::readSelectedBytes) ?: error("无法读取所选文档")
        }.getOrNull() ?: return WorkspaceExchangeV2OpenDocumentRestoreResult.PackageRejected

        // A deterministic opaque ID lets only this exact byte package reclaim its own interrupted
        // journal after the user explicitly selects it again; URI and display name never take part.
        val intentId = "v2-open-document-${sha256(packageBytes)}"
        return when (val result = owner.restore(WorkspaceExchangeV2RestoreRequest(intentId, packageBytes))) {
            is WorkspaceExchangeV2AtomicRestoreResult.Restored -> result.toUiResult(replayed = false)
            is WorkspaceExchangeV2AtomicRestoreResult.Replayed -> result.toUiResult(replayed = true)
            is WorkspaceExchangeV2AtomicRestoreResult.Rejected -> when (result.code) {
                "LOCAL_TRUTH_PRESENT" -> WorkspaceExchangeV2OpenDocumentRestoreResult.LocalTruthPresent
                "INTENT_CONFLICT" -> WorkspaceExchangeV2OpenDocumentRestoreResult.IntentConflict
                "RECOVERY_REQUIRED" -> WorkspaceExchangeV2OpenDocumentRestoreResult.RecoveryRequired
                else -> WorkspaceExchangeV2OpenDocumentRestoreResult.PackageRejected
            }
            WorkspaceExchangeV2AtomicRestoreResult.FailedRecoverably -> WorkspaceExchangeV2OpenDocumentRestoreResult.FailedRecoverably
        }
    }

    /** Returns null rather than ever sending oversized bytes into the strict reader. */
    private fun readSelectedBytes(input: InputStream): ByteArray? {
        val limit = NFAI_EXCHANGE_V2_MAX_PACKAGE_BYTES
        val output = ByteArrayOutputStream(64 * 1024)
        val buffer = ByteArray(8 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (output.size().toLong() + count > limit) return null
            output.write(buffer, 0, count)
        }
        return output.toByteArray().takeIf { it.isNotEmpty() }
    }

    private fun WorkspaceExchangeV2AtomicRestoreResult.Restored.toUiResult(replayed: Boolean) =
        WorkspaceExchangeV2OpenDocumentRestoreResult.Restored(
            semanticHash = receipt.semanticHash,
            objectCount = receipt.rootCounts.values.sum(),
            attachmentCount = receipt.assetCount,
            replayed = replayed,
        )

    private fun WorkspaceExchangeV2AtomicRestoreResult.Replayed.toUiResult(replayed: Boolean) =
        WorkspaceExchangeV2OpenDocumentRestoreResult.Restored(
            semanticHash = receipt.semanticHash,
            objectCount = receipt.rootCounts.values.sum(),
            attachmentCount = receipt.assetCount,
            replayed = replayed,
        )

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }
}

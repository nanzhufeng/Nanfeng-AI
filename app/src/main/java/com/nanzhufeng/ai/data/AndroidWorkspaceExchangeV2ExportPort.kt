package com.nanzhufeng.ai.data

import android.content.Context
import android.net.Uri
import com.nanzhufeng.ai.domain.NfaiExchangeSafeSettings
import com.nanzhufeng.ai.domain.NfaiExchangeV2PackageOutput
import com.nanzhufeng.ai.domain.NfaiExchangeV2PackageOutputPort
import com.nanzhufeng.ai.domain.NfaiExchangeV2PackageWrite
import com.nanzhufeng.ai.domain.NfaiExchangeV2PackageWriter
import com.nanzhufeng.ai.domain.WorkspaceExchangeV2ExportPort
import com.nanzhufeng.ai.domain.WorkspaceExchangeV2ExportResult
import com.nanzhufeng.ai.domain.WorkspaceExchangeV2ScopePlanner
import com.nanzhufeng.ai.domain.WorkspaceExchangeV2ScopePreparation
import com.nanzhufeng.ai.domain.WorkspaceExchangeV2ScopeSummary
import java.security.MessageDigest

/**
 * The v2 Android SAF boundary.  The package is completely serialized and preflighted in memory
 * by the writer before this adapter opens the selected document.  A write or readback failure
 * attempts to remove the newly-created document and never returns a success receipt.
 */
class AndroidWorkspaceExchangeV2ExportPort(
    context: Context,
    private val planner: WorkspaceExchangeV2ScopePlanner,
    private val writer: NfaiExchangeV2PackageWriter,
) : WorkspaceExchangeV2ExportPort {
    private val app = context.applicationContext

    override fun prepareCompleteWorkspace(): WorkspaceExchangeV2ScopePreparation = planner.prepareCompleteWorkspace()

    override fun export(scope: WorkspaceExchangeV2ScopeSummary, destination: Uri): WorkspaceExchangeV2ExportResult {
        val output = SafOutput(destination)
        return when (val result = writer.write(scope.selection, NfaiExchangeSafeSettings("zh-CN", "SYSTEM"), output)) {
            is NfaiExchangeV2PackageWrite.Rejected -> WorkspaceExchangeV2ExportResult.Rejected(result.reason)
            is NfaiExchangeV2PackageWrite.Failed -> WorkspaceExchangeV2ExportResult.Failed(result.reason)
            is NfaiExchangeV2PackageWrite.Written -> runCatching {
                val readbackHash = app.contentResolver.openInputStream(destination)?.use(::sha256)
                    ?: error("无法回读所选位置。")
                require(readbackHash == result.receipt.packageHash) { "所选位置回读哈希不一致。" }
                WorkspaceExchangeV2ExportResult.Exported(
                    packageHash = result.receipt.packageHash,
                    semanticHash = result.receipt.semanticHash,
                    objectCount = scope.objectCount,
                    attachmentCount = result.receipt.assetCount,
                )
            }.getOrElse {
                output.removeIncomplete()
                WorkspaceExchangeV2ExportResult.Failed("完整工作区导出未完成，已请求移除未完成文件。")
            }
        }
    }

    private inner class SafOutput(private val destination: Uri) : NfaiExchangeV2PackageOutputPort {
        override fun writeOnce(packageBytes: ByteArray): NfaiExchangeV2PackageOutput = runCatching {
            app.contentResolver.openOutputStream(destination, "w")?.use { stream ->
                stream.write(packageBytes)
                stream.flush()
            } ?: error("无法写入所选位置。")
            NfaiExchangeV2PackageOutput.Written
        }.getOrElse {
            removeIncomplete()
            NfaiExchangeV2PackageOutput.Failed("无法写入所选位置，已请求移除未完成文件。")
        }

        fun removeIncomplete() {
            runCatching { app.contentResolver.delete(destination, null, null) }
        }
    }

    private fun sha256(input: java.io.InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

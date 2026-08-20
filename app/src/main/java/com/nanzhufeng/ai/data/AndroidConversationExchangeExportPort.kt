package com.nanzhufeng.ai.data

import android.content.Context
import android.net.Uri
import com.nanzhufeng.ai.domain.ConversationExchangeExportPort
import com.nanzhufeng.ai.domain.ConversationExchangeExportResult
import com.nanzhufeng.ai.domain.ConversationExchangePreparation
import com.nanzhufeng.ai.domain.ConversationId
import com.nanzhufeng.ai.domain.ExportConversationExchangeUseCase
import com.nanzhufeng.ai.domain.NfaiExchangeResult
import com.nanzhufeng.ai.domain.NfaiExchangeV1Gateway
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/**
 * Android's only P6-A conversation-exchange output adapter. It creates a private staging file,
 * invokes the protocol gateway, writes exactly once through the user-selected SAF Uri, then
 * reads that Uri back before reporting success. It never imports, mutates Room, or reads Key/HTTP.
 */
class AndroidConversationExchangeExportPort(
    context: Context,
    private val prepare: ExportConversationExchangeUseCase,
) : ConversationExchangeExportPort {
    private val app = context.applicationContext

    override fun export(conversationId: ConversationId, destination: Uri): ConversationExchangeExportResult = when (val prepared = prepare.prepare(conversationId)) {
        is ConversationExchangePreparation.Rejected -> ConversationExchangeExportResult.Rejected(prepared.reason)
        is ConversationExchangePreparation.Prepared -> runCatching {
            val stage = File(app.filesDir, "p6a-conversation-exchange/${UUID.randomUUID()}").also { it.mkdirs() }
            try {
                val packageFile = File(stage, "conversation.nfai-exchange")
                val result = NfaiExchangeV1Gateway.export(prepared.snapshot, packageFile)
                val exported = result as? NfaiExchangeResult.Exported
                    ?: return ConversationExchangeExportResult.Failed("本机交换包生成或严格回读失败。")
                app.contentResolver.openOutputStream(destination, "w")?.use { output ->
                    packageFile.inputStream().use { input -> input.copyTo(output) }
                    output.flush()
                } ?: return ConversationExchangeExportResult.Failed("无法写入所选位置。")
                val destinationHash = app.contentResolver.openInputStream(destination)?.use(::sha256)
                    ?: return ConversationExchangeExportResult.Failed("无法回读所选位置。")
                if (destinationHash != exported.sha256) return ConversationExchangeExportResult.Failed("所选位置回读哈希不一致。")
                val preflight = NfaiExchangeV1Gateway.preflight(packageFile) as? NfaiExchangeResult.Preflighted
                    ?: return ConversationExchangeExportResult.Failed("本机交换包预检未通过。")
                ConversationExchangeExportResult.Exported(exported.sha256, exported.byteCount, preflight.value.semanticHash)
            } finally {
                stage.deleteRecursively()
            }
        }.getOrElse { ConversationExchangeExportResult.Failed("跨端交换导出失败：${it.javaClass.simpleName}") }
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

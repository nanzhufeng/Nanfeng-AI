package com.nanzhufeng.ai.data

import android.content.Context
import com.nanzhufeng.ai.domain.*
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.json.JSONArray
import org.json.JSONObject

/** P3-E package store: app-private, atomic, and validated from the final ZIP before success. */
class AndroidConversationExportStore internal constructor(private val root: File) : ConversationExportStore {
    constructor(context: Context) : this(File(context.applicationContext.filesDir, EXPORT_ROOT))

    @Synchronized override fun write(payload: ConversationExportPayload, exportedAt: Instant, cancelled: () -> Boolean): ConversationExportResult {
        if (cancelled()) return ConversationExportResult.Cancelled
        if (!root.exists() && !root.mkdirs() || !root.isDirectory) return ConversationExportResult.Failed(ConversationExportFailure.OUTPUT_UNAVAILABLE)
        val body = payload.json().toByteArray(Charsets.UTF_8)
        val manifest = ConversationExportManifest(
            exportId = ConversationExportMapper.newExportId(), exportedAt = exportedAt, conversationId = payload.conversation.id,
            scope = payload.scope, excludedFields = ConversationExportMapper.excludedFields,
            files = listOf(ConversationExportFileEntry(PAYLOAD, body.size.toLong(), body.hash())),
        )
        val target = File(root, "conversation-${exportedAt.toEpochMilli()}-${manifest.exportId.substringAfter(':')}.nfai")
        val temporary = runCatching { File.createTempFile("conversation-", ".part", root) }.getOrElse { return ConversationExportResult.Failed(ConversationExportFailure.WRITE_FAILED) }
        try {
            if (cancelled()) return ConversationExportResult.Cancelled
            FileOutputStream(temporary).use { raw -> ZipOutputStream(raw).use { zip ->
                zip.entry(MANIFEST, manifest.json().toByteArray(Charsets.UTF_8)); if (cancelled()) return ConversationExportResult.Cancelled
                zip.entry(PAYLOAD, body); zip.finish(); raw.fd.sync()
            } }
            if (cancelled()) return ConversationExportResult.Cancelled
            if (target.exists()) return ConversationExportResult.Failed(ConversationExportFailure.WRITE_FAILED)
            java.nio.file.Files.move(temporary.toPath(), target.toPath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE)
            return verify(target).also { if (it !is ConversationExportResult.Success) target.delete() }
        } catch (_: Exception) { return ConversationExportResult.Failed(ConversationExportFailure.WRITE_FAILED) }
        finally { if (temporary.exists()) temporary.delete() }
    }

    override fun latestVerified(): ConversationExportResult? = root.listFiles { file -> file.isFile && file.name.endsWith(".nfai") }
        ?.sortedWith(compareByDescending<File> { it.lastModified() }.thenByDescending { it.name })?.asSequence()?.map(::verify)
        ?.firstOrNull { it is ConversationExportResult.Success } ?: if (root.exists()) null else null

    internal fun verify(file: File): ConversationExportResult = try {
        ZipFile(file).use { zip ->
            if (zip.entries().asSequence().map { it.name }.toSet() != setOf(MANIFEST, PAYLOAD)) return ConversationExportResult.Failed(ConversationExportFailure.INVALID_PACKAGE)
            val manifest = zip.read(MANIFEST)?.decodeToString()?.manifest() ?: return ConversationExportResult.Failed(ConversationExportFailure.READ_BACK_FAILED)
            val payloadBytes = zip.read(PAYLOAD) ?: return ConversationExportResult.Failed(ConversationExportFailure.READ_BACK_FAILED)
            if (manifest.format != CONVERSATION_EXPORT_FORMAT || manifest.protocolVersion != CONVERSATION_EXPORT_PROTOCOL_VERSION || manifest.scope != "current-path-only" || manifest.files.singleOrNull()?.path != PAYLOAD) return ConversationExportResult.Failed(ConversationExportFailure.INVALID_PACKAGE)
            val entry = manifest.files.single()
            if (entry.byteCount != payloadBytes.size.toLong() || entry.sha256 != payloadBytes.hash()) return ConversationExportResult.Failed(ConversationExportFailure.INTEGRITY_MISMATCH)
            val payload = payloadBytes.decodeToString().payload()
            if (payload.schema != CONVERSATION_EXPORT_PAYLOAD_SCHEMA || payload.scope != manifest.scope || payload.conversation.id != manifest.conversationId) return ConversationExportResult.Failed(ConversationExportFailure.INVALID_PACKAGE)
            ConversationExportResult.Success(VerifiedConversationExport(manifest, payload, file.name, "$EXPORT_ROOT/${file.name}", file.length(), file.hash()))
        }
    } catch (_: Exception) { ConversationExportResult.Failed(ConversationExportFailure.READ_BACK_FAILED) }

    private fun ZipOutputStream.entry(name: String, bytes: ByteArray) { putNextEntry(ZipEntry(name)); write(bytes); closeEntry() }
    private fun ZipFile.read(name: String) = getEntry(name)?.let { getInputStream(it).use { stream -> stream.readBytes() } }
    private fun File.hash() = FileInputStream(this).use { input -> MessageDigest.getInstance("SHA-256").let { digest -> DigestInputStream(input, digest).use { stream -> while (stream.read(ByteArray(DEFAULT_BUFFER_SIZE)) >= 0) Unit }; digest.digest().hex() } }
    private fun ByteArray.hash() = MessageDigest.getInstance("SHA-256").digest(this).hex()
    private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }
    private companion object { const val EXPORT_ROOT = "exports/conversations/v1"; const val MANIFEST = "manifest.json"; const val PAYLOAD = "conversation.json" }
}

private fun ConversationExportManifest.json() = JSONObject().apply { put("format", format); put("protocolVersion", protocolVersion); put("exportId", exportId); put("exportedAt", exportedAt.toString()); put("conversationId", conversationId); put("scope", scope); put("excludedFields", JSONArray(excludedFields)); put("files", JSONArray(files.map { JSONObject().apply { put("path", it.path); put("byteCount", it.byteCount); put("sha256", it.sha256) } })) }.toString()
private fun ConversationExportPayload.json() = JSONObject().apply { put("schema", schema); put("schemaVersion", schemaVersion); put("scope", scope); put("conversation", conversation.json()); put("messages", JSONArray(messages.map { it.json() })) }.toString()
private fun ConversationExportConversation.json() = JSONObject().apply { put("id", id); put("title", title); put("createdAt", createdAt.toString()); put("updatedAt", updatedAt.toString()); put("archivedAt", archivedAt?.toString()); put("pinnedAt", pinnedAt?.toString()); put("currentLeafMessageId", currentLeafMessageId); put("schemaVersion", schemaVersion) }
private fun ConversationExportMessage.json() = JSONObject().apply { put("id", id); put("parentMessageId", parentMessageId); put("siblingPosition", siblingPosition); put("role", role); put("deliveryState", deliveryState); put("createdAt", createdAt.toString()); put("revision", revision); put("revisesMessageId", revisesMessageId); put("invocationId", invocationId); put("schemaVersion", schemaVersion); put("content", JSONArray(content.map { it.json() })) }
private fun ConversationExportBlock.json() = JSONObject().apply { put("kind", kind); put("text", text); put("schemaVersion", schemaVersion); put("attachment", attachment?.let { JSONObject().apply { put("id", it.id); put("mimeType", it.mimeType); put("byteCount", it.byteCount); put("sha256", it.sha256) } }) }
private fun String.manifest(): ConversationExportManifest { val v = JSONObject(this); return ConversationExportManifest(v.getString("format"), v.getInt("protocolVersion"), v.getString("exportId"), Instant.parse(v.getString("exportedAt")), v.getString("conversationId"), v.getString("scope"), v.getJSONArray("excludedFields").strings(), v.getJSONArray("files").objects { ConversationExportFileEntry(it.getString("path"), it.getLong("byteCount"), it.getString("sha256")) }) }
private fun String.payload(): ConversationExportPayload { val v = JSONObject(this); val c = v.getJSONObject("conversation"); val conversation = ConversationExportConversation(c.getString("id"), c.getString("title"), Instant.parse(c.getString("createdAt")), Instant.parse(c.getString("updatedAt")), c.optString("archivedAt").takeIf { c.has("archivedAt") && it.isNotEmpty() }?.let(Instant::parse), c.optString("pinnedAt").takeIf { c.has("pinnedAt") && it.isNotEmpty() }?.let(Instant::parse), c.optString("currentLeafMessageId").takeIf { c.has("currentLeafMessageId") && it.isNotEmpty() }, c.getInt("schemaVersion")); return ConversationExportPayload(v.getString("schema"), v.getInt("schemaVersion"), v.getString("scope"), conversation, v.getJSONArray("messages").objects { m -> ConversationExportMessage(m.getString("id"), m.optString("parentMessageId").takeIf { m.has("parentMessageId") && it.isNotEmpty() }, m.getInt("siblingPosition"), m.getString("role"), m.getString("deliveryState"), Instant.parse(m.getString("createdAt")), m.getInt("revision"), m.optString("revisesMessageId").takeIf { m.has("revisesMessageId") && it.isNotEmpty() }, m.optString("invocationId").takeIf { m.has("invocationId") && it.isNotEmpty() }, m.getInt("schemaVersion"), m.getJSONArray("content").objects { b -> ConversationExportBlock(b.getString("kind"), b.optString("text").takeIf { b.has("text") && it.isNotEmpty() }, b.optJSONObject("attachment")?.let { a -> ConversationExportAttachment(a.getString("id"), a.getString("mimeType"), a.getLong("byteCount"), a.getString("sha256")) }, b.getInt("schemaVersion")) }) }) }
private fun <T> JSONArray.objects(map: (JSONObject) -> T) = List(length()) { map(getJSONObject(it)) }
private fun JSONArray.strings() = List(length()) { getString(it) }

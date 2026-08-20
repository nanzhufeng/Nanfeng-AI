package com.nanzhufeng.ai.data

import android.content.Context
import com.nanzhufeng.ai.domain.KNOWLEDGE_EXPORT_ATTACHMENT_POLICY
import com.nanzhufeng.ai.domain.KNOWLEDGE_EXPORT_FORMAT
import com.nanzhufeng.ai.domain.KNOWLEDGE_EXPORT_PAYLOAD_SCHEMA
import com.nanzhufeng.ai.domain.KNOWLEDGE_EXPORT_PROTOCOL_VERSION
import com.nanzhufeng.ai.domain.KnowledgeExportAttachment
import com.nanzhufeng.ai.domain.KnowledgeExportFailure
import com.nanzhufeng.ai.domain.KnowledgeExportFileEntry
import com.nanzhufeng.ai.domain.KnowledgeExportItem
import com.nanzhufeng.ai.domain.KnowledgeExportManifest
import com.nanzhufeng.ai.domain.KnowledgeExportPayload
import com.nanzhufeng.ai.domain.KnowledgeExportResult
import com.nanzhufeng.ai.domain.KnowledgeExportSource
import com.nanzhufeng.ai.domain.KnowledgeExportStore
import com.nanzhufeng.ai.domain.KnowledgeExportMapper
import com.nanzhufeng.ai.domain.VerifiedKnowledgeExport
import com.nanzhufeng.ai.domain.CaptureSourceType
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

/** App-private, package-only file storage. No user-selected or shared system directory is used. */
class AndroidKnowledgeExportStore internal constructor(private val root: File) : KnowledgeExportStore {
    constructor(context: Context) : this(File(context.applicationContext.filesDir, EXPORT_ROOT))

    @Synchronized
    override fun write(payload: KnowledgeExportPayload, exportedAt: Instant, cancelled: () -> Boolean): KnowledgeExportResult {
        if (cancelled()) return KnowledgeExportResult.Cancelled
        if (!root.exists() && !root.mkdirs()) return KnowledgeExportResult.Failed(KnowledgeExportFailure.OUTPUT_UNAVAILABLE)
        if (!root.isDirectory) return KnowledgeExportResult.Failed(KnowledgeExportFailure.OUTPUT_UNAVAILABLE)

        val payloadBytes = payload.toJson().toByteArray(Charsets.UTF_8)
        val payloadEntry = KnowledgeExportFileEntry(PAYLOAD_ENTRY, payloadBytes.size.toLong(), payloadBytes.sha256())
        val manifest = KnowledgeExportManifest(
            exportId = KnowledgeExportMapper.newExportId(),
            exportedAt = exportedAt,
            knowledgeCount = payload.items.size,
            knowledgeSchemaVersions = payload.items.map { it.schemaVersion }.distinct().sorted(),
            excludedFields = KnowledgeExportMapper.excludedFields,
            files = listOf(payloadEntry),
        )
        val fileName = "knowledge-${exportedAt.toEpochMilli()}-${manifest.exportId.substringAfter(':')}.nfai"
        val target = File(root, fileName)
        val temporary = runCatching { File.createTempFile("knowledge-", ".part", root) }.getOrElse {
            return KnowledgeExportResult.Failed(KnowledgeExportFailure.WRITE_FAILED)
        }
        try {
            if (cancelled()) return KnowledgeExportResult.Cancelled
            FileOutputStream(temporary).use { raw ->
                ZipOutputStream(raw).use { zip ->
                    zip.writeEntry(MANIFEST_ENTRY, manifest.toJson().toByteArray(Charsets.UTF_8))
                    if (cancelled()) return KnowledgeExportResult.Cancelled
                    zip.writeEntry(PAYLOAD_ENTRY, payloadBytes)
                    zip.finish()
                    raw.fd.sync()
                }
            }
            if (cancelled()) return KnowledgeExportResult.Cancelled
            // A collision always receives a new export identity; existing packages are never replaced.
            if (target.exists()) return KnowledgeExportResult.Failed(KnowledgeExportFailure.WRITE_FAILED)
            java.nio.file.Files.move(temporary.toPath(), target.toPath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE)
            val verified = verify(target)
            if (verified !is KnowledgeExportResult.Success) target.delete()
            return verified
        } catch (_: Exception) {
            return KnowledgeExportResult.Failed(KnowledgeExportFailure.WRITE_FAILED)
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    override fun latestVerified(): KnowledgeExportResult? {
        val packages = root.listFiles { file -> file.isFile && file.name.endsWith(PACKAGE_EXTENSION) }
            ?.sortedWith(compareByDescending<File> { it.lastModified() }.thenByDescending { it.name })
            .orEmpty()
        if (packages.isEmpty()) return null
        return packages.asSequence().map(::verify).firstOrNull { it is KnowledgeExportResult.Success }
            ?: KnowledgeExportResult.Failed(KnowledgeExportFailure.READ_BACK_FAILED)
    }

    internal fun verify(file: File): KnowledgeExportResult {
        if (!file.isFile) return KnowledgeExportResult.Failed(KnowledgeExportFailure.READ_BACK_FAILED)
        return try {
            ZipFile(file).use { zip ->
                val names = zip.entries().asSequence().map { it.name }.toSet()
                if (names != setOf(MANIFEST_ENTRY, PAYLOAD_ENTRY)) return KnowledgeExportResult.Failed(KnowledgeExportFailure.INVALID_PACKAGE)
                val manifestBytes = zip.readEntry(MANIFEST_ENTRY) ?: return KnowledgeExportResult.Failed(KnowledgeExportFailure.READ_BACK_FAILED)
                val payloadBytes = zip.readEntry(PAYLOAD_ENTRY) ?: return KnowledgeExportResult.Failed(KnowledgeExportFailure.READ_BACK_FAILED)
                val manifest = manifestBytes.decodeToString().toManifest()
                if (manifest.format != KNOWLEDGE_EXPORT_FORMAT || manifest.protocolVersion != KNOWLEDGE_EXPORT_PROTOCOL_VERSION ||
                    manifest.files.singleOrNull()?.path != PAYLOAD_ENTRY || manifest.attachmentPolicy != KNOWLEDGE_EXPORT_ATTACHMENT_POLICY ||
                    manifest.knowledgeCount < 0
                ) return KnowledgeExportResult.Failed(KnowledgeExportFailure.INVALID_PACKAGE)
                val entry = manifest.files.single()
                if (entry.byteCount != payloadBytes.size.toLong() || entry.sha256 != payloadBytes.sha256()) {
                    return KnowledgeExportResult.Failed(KnowledgeExportFailure.INTEGRITY_MISMATCH)
                }
                val payload = payloadBytes.decodeToString().toPayload()
                if (payload.schema != KNOWLEDGE_EXPORT_PAYLOAD_SCHEMA || payload.items.size != manifest.knowledgeCount) {
                    return KnowledgeExportResult.Failed(KnowledgeExportFailure.INVALID_PACKAGE)
                }
                KnowledgeExportResult.Success(VerifiedKnowledgeExport(
                    manifest = manifest,
                    payload = payload,
                    fileName = file.name,
                    relativeLocation = "$EXPORT_ROOT/${file.name}",
                    byteCount = file.length(),
                    sha256 = file.sha256(),
                ))
            }
        } catch (_: Exception) {
            KnowledgeExportResult.Failed(KnowledgeExportFailure.READ_BACK_FAILED)
        }
    }

    private fun ZipOutputStream.writeEntry(name: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(bytes)
        closeEntry()
    }

    private fun ZipFile.readEntry(name: String): ByteArray? = getEntry(name)?.let { entry ->
        getInputStream(entry).use { it.readBytes() }
    }

    private fun File.sha256(): String = FileInputStream(this).use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        DigestInputStream(input, digest).use { stream ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (stream.read(buffer) >= 0) Unit
        }
        digest.digest().toHex()
    }

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256").digest(this).toHex()
    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private companion object {
        const val EXPORT_ROOT = "exports/knowledge/v1"
        const val PACKAGE_EXTENSION = ".nfai"
        const val MANIFEST_ENTRY = "manifest.json"
        const val PAYLOAD_ENTRY = "knowledge.json"
    }
}

private fun KnowledgeExportManifest.toJson(): String = JSONObject().apply {
    put("format", format)
    put("protocolVersion", protocolVersion)
    put("exportId", exportId)
    put("exportedAt", exportedAt.toString())
    put("knowledgeCount", knowledgeCount)
    put("knowledgeSchemaVersions", JSONArray(knowledgeSchemaVersions))
    put("attachmentPolicy", attachmentPolicy)
    put("excludedFields", JSONArray(excludedFields))
    put("files", JSONArray(files.map { entry -> JSONObject().apply {
        put("path", entry.path); put("byteCount", entry.byteCount); put("sha256", entry.sha256)
    } }))
}.toString()

private fun KnowledgeExportPayload.toJson(): String = JSONObject().apply {
    put("schema", schema)
    put("schemaVersion", schemaVersion)
    put("knowledge", JSONArray(items.map { item -> JSONObject().apply {
        put("id", item.id); put("schemaVersion", item.schemaVersion); put("createdAt", item.createdAt.toString())
        put("title", item.title); put("body", item.body)
        put("sources", JSONArray(item.sources.map { source -> JSONObject().apply {
            put("sourceType", source.sourceType.name); put("receivedAt", source.receivedAt.toString())
            put("sourceReference", source.sourceReference); put("contributedFields", JSONArray(source.contributedFields))
        } }))
        put("provenance", JSONObject().apply {
            put("candidateId", item.candidateId); put("invocationId", item.invocationId); put("providerId", item.providerId)
            put("modelId", item.modelId); put("harnessVersion", item.harnessVersion)
        })
        put("attachments", JSONArray(item.attachments.map { attachment -> JSONObject().apply {
            put("id", attachment.id); put("mimeType", attachment.mimeType); put("byteCount", attachment.byteCount); put("sha256", attachment.sha256)
        } }))
    } }))
}.toString()

private fun String.toManifest(): KnowledgeExportManifest {
    val value = JSONObject(this)
    val files = value.getJSONArray("files").mapObjects { entry -> KnowledgeExportFileEntry(entry.getString("path"), entry.getLong("byteCount"), entry.getString("sha256")) }
    return KnowledgeExportManifest(
        format = value.getString("format"), protocolVersion = value.getInt("protocolVersion"), exportId = value.getString("exportId"),
        exportedAt = Instant.parse(value.getString("exportedAt")), knowledgeCount = value.getInt("knowledgeCount"),
        knowledgeSchemaVersions = value.getJSONArray("knowledgeSchemaVersions").mapValues { it as Int },
        attachmentPolicy = value.getString("attachmentPolicy"), excludedFields = value.getJSONArray("excludedFields").mapValues { it as String }, files = files,
    )
}

private fun String.toPayload(): KnowledgeExportPayload {
    val value = JSONObject(this)
    return KnowledgeExportPayload(
        schema = value.getString("schema"), schemaVersion = value.getInt("schemaVersion"),
        items = value.getJSONArray("knowledge").mapObjects { item ->
            val provenance = item.getJSONObject("provenance")
            KnowledgeExportItem(
                id = item.getString("id"), schemaVersion = item.getInt("schemaVersion"), createdAt = Instant.parse(item.getString("createdAt")),
                title = item.getString("title"), body = item.getString("body"),
                sources = item.getJSONArray("sources").mapObjects { source -> KnowledgeExportSource(
                    CaptureSourceType.valueOf(source.getString("sourceType")), Instant.parse(source.getString("receivedAt")),
                    source.optString("sourceReference").takeIf { source.has("sourceReference") && it.isNotBlank() },
                    source.getJSONArray("contributedFields").mapValues { it as String },
                ) },
                candidateId = provenance.getString("candidateId"), invocationId = provenance.getString("invocationId"),
                providerId = provenance.getString("providerId"), modelId = provenance.getString("modelId"), harnessVersion = provenance.getInt("harnessVersion"),
                attachments = item.getJSONArray("attachments").mapObjects { attachment -> KnowledgeExportAttachment(
                    attachment.getString("id"), attachment.getString("mimeType"), attachment.getLong("byteCount"), attachment.getString("sha256"),
                ) },
            )
        },
    )
}

private fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> = List(length()) { transform(getJSONObject(it)) }
private fun <T> JSONArray.mapValues(transform: (Any) -> T): List<T> = List(length()) { transform(get(it)) }

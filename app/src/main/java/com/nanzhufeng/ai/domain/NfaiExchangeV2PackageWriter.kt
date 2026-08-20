package com.nanzhufeng.ai.domain

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * The Android half of the v2 package boundary.  It is deliberately not a SAF adapter: callers
 * must provide a single all-or-nothing output port after the read-only owner mapping has passed.
 * No Room, UI, picker, private staging file, import, or persistent receipt exists in this layer.
 */
data class NfaiExchangeV2PackageReceipt(
    val packageHash: String,
    val semanticHash: String,
    val origin: String,
    val sensitivity: String,
    val rootCounts: Map<String, Int>,
    val assetCount: Int,
    val assetBytes: Long,
    val ownerFieldHashes: Map<String, String>,
)

sealed interface NfaiExchangeV2PackageWrite {
    data class Written(val receipt: NfaiExchangeV2PackageReceipt) : NfaiExchangeV2PackageWrite
    data class Rejected(val reason: String) : NfaiExchangeV2PackageWrite
    data class Failed(val reason: String) : NfaiExchangeV2PackageWrite
}

/** A future adapter must publish [packageBytes] atomically, or return [Failed]. */
interface NfaiExchangeV2PackageOutputPort {
    fun writeOnce(packageBytes: ByteArray): NfaiExchangeV2PackageOutput
}

sealed interface NfaiExchangeV2PackageOutput {
    data object Written : NfaiExchangeV2PackageOutput
    data class Failed(val reason: String) : NfaiExchangeV2PackageOutput
}

/**
 * Strict serializer for the already-validated owner -> v2 IR path.  The only owner reads after
 * mapping are the explicit, ledger-referenced attachment bytes; an output port is never called
 * until the complete ZIP has been preflighted in memory.
 */
class NfaiExchangeV2PackageWriter(
    private val mapper: NfaiExchangeV2OwnerMapper,
    private val source: NfaiExchangeWorkspaceSource,
) {
    fun write(
        selection: NfaiExchangeWorkspaceSelection,
        settings: NfaiExchangeSafeSettings,
        output: NfaiExchangeV2PackageOutputPort,
    ): NfaiExchangeV2PackageWrite {
        val mapped = mapper.map(selection, settings)
        if (mapped is WorkspaceExchangeV2Mapping.Rejected) return NfaiExchangeV2PackageWrite.Rejected(mapped.reason)
        val exchange = JSONObject((mapped as WorkspaceExchangeV2Mapping.Prepared).exchangeJson)
        val prepared = runCatching {
            val assets = readLedgerAssets(exchange)
            val packageBytes = packageBytes(exchange, assets)
            packageBytes to preflight(packageBytes)
        }.getOrElse { return NfaiExchangeV2PackageWrite.Rejected(it.message ?: "完整 v2 交换包预检失败。") }
        return runCatching {
            val (packageBytes, receipt) = prepared
            when (val written = output.writeOnce(packageBytes)) {
                NfaiExchangeV2PackageOutput.Written -> NfaiExchangeV2PackageWrite.Written(receipt)
                is NfaiExchangeV2PackageOutput.Failed -> NfaiExchangeV2PackageWrite.Failed(written.reason)
            }
        }.getOrElse { NfaiExchangeV2PackageWrite.Failed(it.message ?: "v2 package 输出失败。") }
    }

    private fun readLedgerAssets(exchange: JSONObject): Map<String, ByteArray> {
        val ledger = attachmentLedger(exchange)
        return ledger.values.associate { asset ->
            val owner = source.attachment(AttachmentId(asset.id)) ?: error("附件 owner 不可读取。")
            require(owner.isReadyPrivateCopy() && owner.id.value == asset.id && owner.mimeType == asset.mimeType &&
                owner.displayName == asset.displayName && owner.byteCount == asset.byteCount && owner.sha256 == asset.sha256) {
                "附件 owner 与 v2 IR 账本不一致。"
            }
            val bytes = (source.readPrivateAttachment(owner) as? AttachmentReadResult.Content)?.bytes
                ?: error("附件私有内容不可读取。")
            require(bytes.size.toLong() == asset.byteCount && hash(bytes) == asset.sha256) { "附件内容摘要不一致。" }
            asset.entry to bytes
        }
    }

    private fun packageBytes(exchange: JSONObject, assets: Map<String, ByteArray>): ByteArray {
        NfaiExchangeV2Ir.validate(exchange)
        val ledger = attachmentLedger(exchange)
        require(assets.keys == ledger.values.map { it.entry }.toSet()) { "v2 package asset 账本不完整。" }
        ledger.values.forEach { asset ->
            val bytes = assets[asset.entry] ?: error("v2 package asset 缺失。")
            require(bytes.size.toLong() == asset.byteCount && hash(bytes) == asset.sha256) { "v2 package asset hash 不符。" }
        }
        val entries = sortedMapOf<String, ByteArray>()
        entries["exchange.json"] = canonical(exchange).toByteArray(Charsets.UTF_8)
        entries.putAll(assets)
        val manifest = JSONObject().apply {
            put("format", PACKAGE_FORMAT); put("packageVersion", 2); put("exchangeVersion", 2)
            put("export", exchange.getJSONObject("export"))
            put("files", JSONArray(entries.map { (path, bytes) -> JSONObject().apply {
                put("path", path); put("byteCount", bytes.size); put("sha256", hash(bytes))
            } }))
        }
        return ByteArrayOutputStream().use { raw ->
            ZipOutputStream(raw).use { zip ->
                zip.entry("manifest.json", canonical(manifest).toByteArray(Charsets.UTF_8))
                entries.forEach { (path, bytes) -> zip.entry(path, bytes) }
            }
            raw.toByteArray()
        }
    }

    /** Pure reader for locally serialized bytes; it has no output or owner side effect. */
    private fun preflight(packageBytes: ByteArray): NfaiExchangeV2PackageReceipt {
        require(packageBytes.size in 1..MAX_PACKAGE_BYTES.toInt()) { "v2 package 为空或超过 128 MiB 限制。" }
        val entries = readZip(packageBytes)
        val manifest = JSONObject(entries["manifest.json"]?.toString(Charsets.UTF_8) ?: error("v2 缺少 manifest。"))
        manifest.requireExact("format", "packageVersion", "exchangeVersion", "export", "files")
        require(manifest.optString("format") == PACKAGE_FORMAT && manifest.optInt("packageVersion") == 2 && manifest.optInt("exchangeVersion") == 2) { "v2 manifest 版本不支持。" }
        val files = manifest.getJSONArray("files")
        require(files.length() + 1 == entries.size) { "v2 package 包含未知或未列 entry。" }
        val listed = mutableSetOf<String>()
        repeat(files.length()) { index ->
            val file = files.getJSONObject(index); file.requireExact("path", "byteCount", "sha256")
            val path = file.getString("path"); val bytes = entries[path] ?: error("v2 manifest entry 不存在。")
            require(path != "manifest.json" && listed.add(path) && file.getLong("byteCount") == bytes.size.toLong() && file.getString("sha256") == hash(bytes)) { "v2 manifest hash、大小或清单不符。" }
        }
        require(listed == entries.keys - "manifest.json") { "v2 manifest entry 集合不一致。" }
        val exchange = JSONObject(entries["exchange.json"]?.toString(Charsets.UTF_8) ?: error("v2 缺少 exchange.json。"))
        val verified = NfaiExchangeV2Ir.validate(exchange)
        require(canonical(manifest.getJSONObject("export")) == canonical(exchange.getJSONObject("export"))) { "v2 manifest export 与 exchange 不一致。" }
        val ledger = attachmentLedger(exchange)
        val expectedAssets = ledger.values.map { it.entry }.toSet()
        require(entries.keys.filter { it.startsWith("assets/") }.toSet() == expectedAssets && entries.keys.all { it == "manifest.json" || it == "exchange.json" || it.startsWith("assets/") }) { "v2 asset 账本与 package entry 不一致。" }
        ledger.values.forEach { asset ->
            val bytes = entries[asset.entry] ?: error("v2 attachment asset 不存在。")
            require(bytes.size.toLong() == asset.byteCount && hash(bytes) == asset.sha256) { "v2 attachment asset hash 或大小不符。" }
        }
        val ownerHashes = sortedMapOf<String, String>()
        listOf("project" to "projects", "conversation" to "conversations", "knowledge" to "knowledge", "memory" to "memory", "relation" to "relations").forEach { (kind, group) ->
            exchange.getJSONArray(group).objects().forEach { owner -> ownerHashes["$kind/${owner.getString("id")}"] = hash(canonical(owner).toByteArray(Charsets.UTF_8)) }
        }
        ownerHashes["settings/root"] = hash(canonical(exchange.getJSONObject("settings")).toByteArray(Charsets.UTF_8))
        ledger.values.forEach { asset -> ownerHashes["asset/${asset.sha256}"] = asset.sha256 }
        return NfaiExchangeV2PackageReceipt(
            packageHash = hash(packageBytes), semanticHash = verified.semanticHash,
            origin = exchange.getJSONObject("export").getJSONObject("origin").getString("platform"),
            sensitivity = exchange.getJSONObject("export").getString("sensitivity"),
            rootCounts = listOf("projects", "conversations", "knowledge", "memory", "relations").associateWith { exchange.getJSONArray(it).length() },
            assetCount = expectedAssets.size, assetBytes = expectedAssets.sumOf { entries.getValue(it).size.toLong() }, ownerFieldHashes = ownerHashes,
        )
    }

    private fun attachmentLedger(exchange: JSONObject): Map<String, Asset> {
        val byId = sortedMapOf<String, Asset>()
        fun record(value: JSONObject) {
            value.requireExact("id", "entry", "mimeType", "displayName", "byteCount", "sha256", "classification")
            val asset = Asset(value.getString("id"), value.getString("entry"), value.getString("mimeType"), value.getString("displayName"), value.getLong("byteCount"), value.getString("sha256"), value.getString("classification"))
            require(asset.id.matches(STABLE_ID) && asset.entry == "assets/${asset.sha256}" && asset.sha256.matches(SHA256) && asset.byteCount > 0 && asset.mimeType in CONVERSATION_ALLOWED_MIME_TYPES && asset.displayName.isNotBlank() && !looksLikeLocator(asset.displayName) && asset.classification in setOf("NORMAL", "HIGH_SENSITIVE")) { "v2 attachment metadata 无效。" }
            val previous = byId.putIfAbsent(asset.id, asset)
            require(previous == null || previous == asset) { "同一 v2 attachment ID 的元数据不一致。" }
        }
        exchange.getJSONArray("conversations").objects().forEach { conversation ->
            conversation.getJSONArray("messages").objects().forEach { message ->
                message.getJSONArray("blocks").objects().filter { it.optString("kind") == "ASSET_REF" }.forEach { block -> record(block.getJSONObject("asset")) }
            }
        }
        exchange.getJSONArray("knowledge").objects().forEach { knowledge -> knowledge.getJSONArray("attachments").objects().forEach(::record) }
        return byId
    }

    private fun readZip(bytes: ByteArray): Map<String, ByteArray> {
        val entries = linkedMapOf<String, ByteArray>(); var total = 0L
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(entry.isSafeFile() && entries.size < MAX_ENTRIES) { "v2 ZIP 包含不安全或过多 entry。" }
                require(entries.put(entry.name, zip.readBounded(MAX_PACKAGE_BYTES, total)) == null) { "v2 ZIP 包含重复 entry。" }
                total += entries.getValue(entry.name).size; require(total <= MAX_PACKAGE_BYTES) { "v2 ZIP 解压总量超过限制。" }
                zip.closeEntry()
            }
        }
        require(entries.isNotEmpty()) { "v2 ZIP 包无效。" }
        return entries
    }

    private data class Asset(val id: String, val entry: String, val mimeType: String, val displayName: String, val byteCount: Long, val sha256: String, val classification: String)

    private fun ZipOutputStream.entry(path: String, bytes: ByteArray) { putNextEntry(ZipEntry(path).apply { time = 0L }); write(bytes); closeEntry() }
    private fun ZipEntry.isSafeFile() = !isDirectory && name.isNotBlank() && !name.startsWith('/') && !name.contains("..") && !name.contains('\\')
    private fun ZipInputStream.readBounded(max: Long, alreadyRead: Long): ByteArray { val out = ByteArrayOutputStream(); val buffer = ByteArray(8192); while (true) { val read = read(buffer); if (read < 0) break; require(alreadyRead + out.size() + read <= max) { "v2 ZIP entry 超限。" }; out.write(buffer, 0, read) }; return out.toByteArray() }
    private fun JSONArray.objects(): List<JSONObject> = List(length()) { index -> get(index) as? JSONObject ?: error("v2 JSON 数组必须是对象数组。") }
    private fun JSONObject.requireExact(vararg expected: String) { require(keys().asSequence().toSet() == expected.toSet()) { "v2 字段缺失或未知。" } }
    private fun canonical(value: Any?): String = when (value) { null, JSONObject.NULL -> "null"; is String -> quote(value); is Boolean, is Int, is Long -> value.toString(); is Number -> require(value.toDouble().isFinite() && value.toLong().toDouble() == value.toDouble()) { "仅允许安全整数。" }.let { value.toLong().toString() }; is JSONObject -> value.keys().asSequence().toList().sorted().joinToString(",", "{", "}") { "${quote(it)}:${canonical(value.get(it))}" }; is JSONArray -> (0 until value.length()).joinToString(",", "[", "]") { canonical(value.get(it)) }; else -> error("不支持的 JSON 值。") }
    private fun quote(value: String) = buildString { append('"'); value.forEach { char -> when (char) { '"' -> append("\\\""); '\\' -> append("\\\\"); '\b' -> append("\\b"); '\u000C' -> append("\\f"); '\n' -> append("\\n"); '\r' -> append("\\r"); '\t' -> append("\\t"); else -> if (char.code < 0x20) append("\\u%04x".format(char.code)) else append(char) } }; append('"') }
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private companion object {
        const val PACKAGE_FORMAT = "nfai.exchange.package"
        const val MAX_ENTRIES = 100_000
        const val MAX_PACKAGE_BYTES = 128L * 1024L * 1024L
        val STABLE_ID = Regex("[a-z0-9][a-z0-9_-]{1,63}")
        val SHA256 = Regex("[a-f0-9]{64}")
        fun looksLikeLocator(value: String) = value.contains(Regex("(?i)(://|^/|^~[/\\\\]|^[a-z]:[/\\\\]|content:|file:|\\\\\\\\)")) || value.contains('/') || value.contains('\\')
    }
}
